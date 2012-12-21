/*
 * Copyright 2010 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Beginnings of an optimization to specialize the initial module at the cost of
 * increasing code in later modules. This is still very experimental.
 *
 * High-level overview:
 *
 * This optimization replaces functions in the initial module with specialized
 * versions that are only safe in the initial module. The original, general,
 * versions of the functions are "fixed up" in later modules. This optimization
 * can shrink the initial module significantly but the fixup code in later
 * modules increases overall code size.
 *
 * Implementation approach:
 *
 * We take a ridiculously naive approach: remove the initial module
 * from the rest of the AST, optimize it with existing optimization passes
 * (recording which functions have been specialized), put it back in the AST,
 * and add fixups restoring the general versions of the functions in each module
 * that depends on the initial module.
 *
 * Since it is only safe to specialize functions that can be fixed up, we
 * don't allow specialization of local functions and functions that
 * are aliased.
 *
 * We currently run three optimizations on the isolated AST: InlineFunctions,
 * DevirtualizePrototypeMethods, and RemoveUnusedPrototypeProperties.
 *
 * These optimizations rely on a coarse-grained name-based analysis to
 * maintain safety properties and thus are likely to see some benefit when
 * applied in isolation.
 *
 * InlineFunctions is truly specializing -- it replaces functions with
 * versions that have calls to other functions inlined into them, while
 * RemoveUnusedPrototypeProperties is really just removing properties that
 * aren't used in the initial module and adding copies further down in the
 * module graph. It would probably be more elegant to give
 * CrossModuleMethodMotion permission to make copies of methods instead.
 *
 * There are additional passes that might benefit from being made
 * specialization-aware:
 *
 * - OptimizeParameters
 *
 * - Any pass that is too slow to run over the entire AST but might
 *      be acceptable on only the initial module:
 *  - RemoveUnusedNames
 *
 *  - Also, any pass that uses the results of PureFunctionIdentifier to
 *  determine when it is safe to remove code might benefit (e.g. the peephole
 *  passes), since PureFunctionIdentifier relies on SimpleDefinitionFinder,
 *  which would be more precise when running on only the initial module.
 *
 * @author dcc@google.com (Devin Coughlin)
 */
class SpecializeModule implements CompilerPass {
  private AbstractCompiler compiler;

  private Map<Node, Node> specializedInputRootsByOriginal;

  private Map<Node, OriginalFunctionInformation>
      functionInfoBySpecializedFunctionNode;

  private SpecializationState specializationState;

  private final PassFactory[] specializationPassFactories;

  public SpecializeModule(AbstractCompiler compiler,
      PassFactory ...specializationPassFactories) {
    this.compiler = compiler;
    this.specializationPassFactories = specializationPassFactories;
  }

  /**
   * Performs initial module specialization.
   *
   * The process is as follows:
   *
   * 1) Make a copy of each of the inputs in the initial root and put them
   * in a fake AST that looks like it is the whole program.
   *
   * 2) Run the specializing compiler passes over the fake initial module AST
   * until it reaches a fixed point, recording which functions are specialized
   * or removed.
   *
   * 3) Replace the original input roots with the specialized input roots
   *
   * 4) For each module that directly depends on the initial module, add
   * fixups for the specialized and removed functions. Right now we add
   * fixups for for every function that was specialized or removed -- we could
   * be smarter about this and for each dependent module only add the functions
   * that it needs.
   *
   * 5) Add dummy variables declaring the removed function to the end of
   * the now-specialized initial module. This is needed to keep
   * {@link VarCheck} from complaining.
   */
  @Override
  public void process(Node externs, Node root) {
    JSModuleGraph moduleGraph = compiler.getModuleGraph();

    // Can't perform optimization without a module graph!
    if (moduleGraph == null) {
      return;
    }

    JSModule module = moduleGraph.getRootModule();

    Node fakeModuleRoot = copyModuleInputs(module);

    SimpleDefinitionFinder defFinder = new SimpleDefinitionFinder(compiler);

    defFinder.process(externs, fakeModuleRoot);

    SimpleFunctionAliasAnalysis initialModuleFunctionAliasAnalysis =
        new SimpleFunctionAliasAnalysis();

    initialModuleFunctionAliasAnalysis.analyze(defFinder);

    specializationState =
        new SpecializationState(initialModuleFunctionAliasAnalysis);

    do {
      specializationState.resetHasChanged();

      for (SpecializationAwareCompilerPass pass : createSpecializingPasses()) {
        pass.enableSpecialization(specializationState);
        pass.process(externs, fakeModuleRoot);
      }
    } while(specializationState.hasChanged());

    // We must always add dummy variables before replacing the original module.
    addDummyVarDeclarationsToInitialModule(module);
    replaceOriginalModuleInputsWithSpecialized();
    addOriginalFunctionVersionsToDependentModules(module);
  }

  /**
   * Returns a collection of new instances of specializing passes.
   */
  private Collection<SpecializationAwareCompilerPass>
      createSpecializingPasses() {

    Collection<SpecializationAwareCompilerPass> passes = Lists.newLinkedList();

    for (PassFactory passFactory : specializationPassFactories) {
      CompilerPass pass = passFactory.create(compiler);

      Preconditions.checkState(pass instanceof
          SpecializationAwareCompilerPass);

      passes.add((SpecializationAwareCompilerPass) pass);
    }

    return passes;
  }

  /**
   * Creates an AST that consists solely of copies of the input roots for the
   * passed in module.
   *
   * Also records a map in {@link #functionInfoBySpecializedFunctionNode}
   * of information about the original function keyed on the copies of the
   * functions to specialized.
   */
  private Node copyModuleInputs(JSModule module) {

    specializedInputRootsByOriginal = Maps.newLinkedHashMap();

    functionInfoBySpecializedFunctionNode = Maps.newLinkedHashMap();

    Node syntheticModuleJsRoot = IR.block();
    syntheticModuleJsRoot.setIsSyntheticBlock(true);

    for (CompilerInput input : module.getInputs()) {
      Node originalInputRoot = input.getAstRoot(compiler);

      Node copiedInputRoot = originalInputRoot.cloneTree();
      copiedInputRoot.copyInformationFromForTree(originalInputRoot);

      specializedInputRootsByOriginal.put(originalInputRoot,
          copiedInputRoot);

      matchTopLevelFunctions(originalInputRoot, copiedInputRoot);

      syntheticModuleJsRoot.addChildToBack(copiedInputRoot);
    }

    // The jsRoot needs a parent (in a normal compilation this would be the
    // node that contains jsRoot and the externs).
    Node syntheticExternsAndJsRoot = IR.block();
    syntheticExternsAndJsRoot.addChildToBack(syntheticModuleJsRoot);

    return syntheticModuleJsRoot;
  }

  /**
   * Records information about original functions and creates a map from
   * the specialized functions to this information.
   *
   * This information is only recorded for global functions since non-global
   * functions cannot be inlined.
   *
   * @param original An original input root.
   * @param toBeSpecialized A copy of the input root (the copy to be
   * specialized)
   */
  private void matchTopLevelFunctions(Node original, Node toBeSpecialized) {
    new NodeMatcher() {
      @Override
      public void reportMatch(Node original, Node specialized) {
        if (original.isFunction()) {
          OriginalFunctionInformation functionInfo =
              new OriginalFunctionInformation(original);

          functionInfoBySpecializedFunctionNode.put(specialized,
              functionInfo);
        }
      }

      @Override
      public boolean shouldTraverse(Node n1, Node n2) {
        return !n1.isFunction();
      }
    }.match(original, toBeSpecialized);
  }

  /**
   * Replaces the original input roots of the initial module with
   * their specialized versions.
   *
   * (Since {@link JsAst} holds a pointer to original inputs roots, we actually
   * replace the all the children of the root rather than swapping the
   * root pointers).
   */
  private void replaceOriginalModuleInputsWithSpecialized() {
    for (Node original : specializedInputRootsByOriginal.keySet()) {
      Node specialized = specializedInputRootsByOriginal.get(original);

      original.removeChildren();

      List<Node> specializedChildren = Lists.newLinkedList();

      while (specialized.getFirstChild() != null) {
        original.addChildToBack(specialized.removeFirstChild());
      }
    }
  }

  /**
   * Adds dummy variable declarations for all the function declarations we've
   * removed to the end of the initial module.
   *
   * We do this to make {@link VarCheck} happy, since it requires variables to
   * be declared before they are used in the whole program AST and doesn't
   * like it when they are declared multiple times.
   *
   * TODO(dcc): Be smarter about whether we need a VAR here or not.
   */
  private void addDummyVarDeclarationsToInitialModule(JSModule module) {
    for (Node modifiedFunction :
      functionInfoBySpecializedFunctionNode.keySet()) {
     if (specializationState.getRemovedFunctions().contains(modifiedFunction)) {
       OriginalFunctionInformation originalInfo =
         functionInfoBySpecializedFunctionNode.get(modifiedFunction);

       if (originalInfo.name != null && originalInfo.originalWasDeclaration()) {
         Node block = specializationState.removedFunctionToBlock.get(
             modifiedFunction);

         // Declaring block might be null if no fix-up declarations is needed.
         // For example, InlineFunction can inline an anonymous function call or
         // anything with prototype property requires no dummy declaration
         // fix-ups afterward.
         if (block != null) {
           Node originalRoot = specializedInputRootsByOriginal.get(block);
           block.addChildrenToBack(originalInfo.generateDummyDeclaration());
         }
       }
     }
    }
  }

  /**
   * Adds a copy of the original versions of specialized/removed functions
   * to each of the dependents of module.
   *
   * Currently we add all of these functions to all dependents; it
   * would be more efficient to only add the functions that could be used.
   *
   * TODO(dcc): Only add fixup functions where needed.
   */
  private void addOriginalFunctionVersionsToDependentModules(JSModule module) {
    for (JSModule directDependent : getDirectDependents(module)) {
      CompilerInput firstInput = directDependent.getInputs().get(0);
      Node firstInputRootNode = firstInput.getAstRoot(compiler);

      // We don't iterate specializedFunctions directly because want to maintain
      // and specializedFunctions in source order, rather than
      // in the order that some optimization specialized the function.

      // So since we're adding to the front of the module each time, we
      // have to iterate in reverse source order.

      List<Node> possiblyModifiedFunctions =
        Lists.newArrayList(functionInfoBySpecializedFunctionNode.keySet());

      Collections.reverse(possiblyModifiedFunctions);

      for (Node modifiedFunction : possiblyModifiedFunctions) {
        boolean declarationWasSpecialized =
          specializationState.getSpecializedFunctions()
          .contains(modifiedFunction);

        boolean declarationWasRemoved =
            specializationState.getRemovedFunctions()
            .contains(modifiedFunction);

        if (declarationWasSpecialized || declarationWasRemoved) {
          OriginalFunctionInformation originalInfo =
               functionInfoBySpecializedFunctionNode.get(modifiedFunction);

           // Don't add unspecialized versions of anonymous functions
           if (originalInfo.name != null) {
             Node newDefinition =
               originalInfo.generateFixupDefinition();

             firstInputRootNode.addChildrenToFront(newDefinition);
           }
        }
      }
    }
  }

  /**
   * Returns a list of modules that directly depend on the given module.
   *
   * This probably deserves to be in JSModuleGraph.
   */
  public Collection<JSModule> getDirectDependents(JSModule module) {
    Set<JSModule> directDependents = Sets.newHashSet();

    for (JSModule possibleDependent :
          compiler.getModuleGraph().getAllModules()) {
      if (possibleDependent.getDependencies().contains(module)) {
        directDependents.add(possibleDependent);
      }
    }

    return directDependents;
  }

  /**
   * A simple abstract classes that takes two isomorphic ASTs and walks
   * each of them together, reporting matches to subclasses.
   *
   * This could probably be hardened and moved to NodeUtil
   */
  private abstract static class NodeMatcher {

    /**
     * Calls {@link #reportMatch(Node, Node)} for each pair of matching nodes
     * from the two ASTs.
     *
     * The two ASTs must be isomorphic. Currently no error checking is
     * performed to ensure that this is the case.
     */
    public void match(Node ast1, Node ast2) {
      // Just blunder ahead and assume that the two nodes actually match

      reportMatch(ast1, ast2);

      if (shouldTraverse(ast1, ast2)) {
        Node childOf1 = ast1.getFirstChild();
        Node childOf2 = ast2.getFirstChild();

        while (childOf1 != null) {
          match(childOf1, childOf2);
          childOf1 = childOf1.getNext();
          childOf2 = childOf2.getNext();
        }
      }

    }

    /**
     * Subclasses should override to add their own behavior when two nodes
     * are matched.
     * @param n1 A node from the AST passed as ast1 in
     * {@link #match(Node, Node)}.
     * @param n2 A node from the AST passed as ast1 in
     * {@link #match(Node, Node)}.
     */
    public abstract void reportMatch(Node n1, Node n2);

    /**
     * Subclasses should override to determine whether matching should proceed
     * under a subtree.
     */
    public boolean shouldTraverse(Node node1, Node n2) {
      return true;
    }
  }

  /**
   * A class that stores information about the original version of a
   * function that will be/was specialized or removed.
   *
   * This class stores:
   * - how the function was defined
   * - a copy of the original function
   */
  private class OriginalFunctionInformation {
    private String name;

    /**
     *  a = function() {} if true;
     *  function a() {} otherwise
     */
    private boolean isAssignFunction;

    private boolean assignHasVar;

    private Node originalFunctionCopy;

    public OriginalFunctionInformation(Node originalFunction) {
      name = NodeUtil.getFunctionName(originalFunction);

      originalFunctionCopy = originalFunction.cloneTree();
      originalFunctionCopy.copyInformationFromForTree(originalFunction);

      Node originalParent = originalFunction.getParent();

      isAssignFunction = originalParent.isAssign() ||
          originalParent.isName();

      assignHasVar =
          isAssignFunction && originalParent.getParent().isVar();
    }

    private Node copiedOriginalFunction() {
      // Copy of a copy
      Node copy = originalFunctionCopy.cloneTree();
      copy.copyInformationFromForTree(originalFunctionCopy);

      return copy;
    }

    /**
     * Did the original function add its name to scope?
     * (If so, and specialization removes it, then we'll have to
     * add a VAR for it so VarCheck doesn't complain).
     */
    private boolean originalWasDeclaration() {
      return (!isAssignFunction) || (assignHasVar);
    }

    /**
     * Generates a definition of the original function that can be added as
     * a fixup in the modules that directly depend on the specialized module.
     *
     * <PRE>
     * The trick here is that even if the original function is declared as:
     *
     * function foo() {
     *   // stuff
     * }
     *
     * the fixup will have to be of the form
     *
     * foo = function() {
     *   // stuff
     * }
     * </PRE>
     *
     */
    private Node generateFixupDefinition() {
      Node functionCopy = copiedOriginalFunction();

      Node nameNode;

      if (isAssignFunction) {
        nameNode =
           NodeUtil.newQualifiedNameNode(
               compiler.getCodingConvention(), name, functionCopy, name);
      } else {
        // Grab the name node from the original function and make that
        // function anonymous.
        nameNode = functionCopy.getFirstChild();
        functionCopy.replaceChild(nameNode,
            NodeUtil.newName(compiler.getCodingConvention(), "", nameNode));
      }

      Node assignment = IR.assign(nameNode, functionCopy);
      assignment.copyInformationFrom(functionCopy);

      return NodeUtil.newExpr(assignment);
    }

    /**
     * Returns a new dummy var declaration for the function with no initial
     * value:
     *
     * var name;
     */
    private Node generateDummyDeclaration() {
      Node declaration = NodeUtil.newVarNode(name, null);
      declaration.copyInformationFromForTree(originalFunctionCopy);

      return declaration;
    }
  }

  /**
   * A class to hold state during SpecializeModule. An instance of this class
   * is passed to specialization-aware compiler passes -- they use it to
   * communicate with SpecializeModule.
   *
   * SpecializationAware optimizations are required to keep track of the
   * functions they remove and the functions that they modify so that the fixups
   * can be added. However, not all functions can be fixed up.
   *
   * Specialization-aware classes *must* call
   * {@link #reportSpecializedFunction} when a function is modified during
   * specialization and {@link #reportRemovedFunction} when one is removed.
   *
   * Also, when specializing, they must query {@link #canFixupFunction}
   * before modifying a function.
   *
   * This two-way communication, is the reason we don't use
   * {@link AstChangeProxy} to report code changes.
   */
  public static class SpecializationState {

    /**
     * The functions that the pass has specialized. These functions will
     * be fixed up in non-specialized modules to their more general versions.
     *
     * This field is also used to determine whether specialization is enabled.
     * If not null, specialization is enabled, otherwise it is disabled.
     */
    private Set<Node> specializedFunctions;

    /**
     * The functions that the pass has removed. These functions will be
     * redefined in non-specialized modules.
     */
    private Set<Node> removedFunctions;

    private Map<Node, Node> removedFunctionToBlock;

    private SimpleFunctionAliasAnalysis initialModuleAliasAnalysis;

    /** Will be true if any new functions have been removed or specialized since
     * {@link #resetHasChanged}.
     */
    private boolean hasChanged = false;

    public SpecializationState(SimpleFunctionAliasAnalysis
        initialModuleAliasAnalysis) {

      this.initialModuleAliasAnalysis = initialModuleAliasAnalysis;

      specializedFunctions = Sets.newLinkedHashSet();
      removedFunctions = Sets.newLinkedHashSet();
      removedFunctionToBlock = Maps.newLinkedHashMap();
    }

    /**
     * Returns true if any new functions have been reported as removed or
     * specialized since {@link #resetHasChanged()} was last called.
     */
    private boolean hasChanged() {
      return hasChanged;
    }

    private void resetHasChanged() {
      hasChanged = false;
    }

    /**
     * Returns the functions specialized by this compiler pass.
     */
    public Set<Node> getSpecializedFunctions() {
      return specializedFunctions;
    }

    /**
     * Reports that a function has been specialized.
     *
     * @param functionNode A specialized AST node with type Token.FUNCTION
     */
    public void reportSpecializedFunction(Node functionNode) {
      if (specializedFunctions.add(functionNode)) {
        hasChanged = true;
      }
    }

    /**
     * Reports that the function containing the node has been specialized.
     */
    public void reportSpecializedFunctionContainingNode(Node node) {
      Node containingFunction = containingFunction(node);

      if (containingFunction != null) {
        reportSpecializedFunction(containingFunction);
      }
    }

    /**
     * The functions removed by this compiler pass.
     */
    public Set<Node> getRemovedFunctions() {
      return removedFunctions;
    }

    /**
     * Reports that a function has been removed.
     *
     * @param functionNode A removed AST node with type Token.FUNCTION
     * @param declaringBlock If the function declaration puts a variable in the
     *    scope, we need to have a VAR statement in the scope where the
     *    function is declared. Null if the function does not put a name
     *    in the scope.
     */
    public void reportRemovedFunction(Node functionNode, Node declaringBlock) {
      // Depends when we were notified, functionNode.getParent might or might
      // not be null. We are going to force the user to tell us the parent
      // instead.
      if (removedFunctions.add(functionNode)) {
        hasChanged = true;
        removedFunctionToBlock.put(functionNode, declaringBlock);
      }
    }

    /**
     * Returns true if the function can be fixed up (that is, if it can be
     * safely removed or specialized).
     *
     * <p>In order to be safely fixed up, a function must be:
     * <PRE>
     * - in the global scope
     * - not aliased in the initial module
     * - of one of the following forms:
     *    function f() {}
     *    var f = function() {}
     *    f = function(){}
     *    var ns = {}; ns.f = function() {}
     *    SomeClass.prototype.foo = function() {};
     * </PRE>
     *
     * <p>Anonymous functions cannot be safely fixed up, nor can functions
     * that have been aliased.
     *
     * <p>Some functions declared as object literals could be safely fixed up,
     * however we do not currently support this.
     */
    public boolean canFixupFunction(Node functionNode) {
      Preconditions.checkArgument(functionNode.isFunction());

      if (!nodeIsInGlobalScope(functionNode) ||
          initialModuleAliasAnalysis.isAliased(functionNode)) {
        return false;
      }

      if (NodeUtil.isStatement(functionNode)) {
        // function F() {}
        return true;
      }

      Node parent = functionNode.getParent();
      Node gramps = parent.getParent();

      if (parent.isName() && gramps.isVar()) {
        // var f = function() {}
        return true;
      }

      if (NodeUtil.isExprAssign(gramps)
          && parent.getChildAtIndex(1) == functionNode) {
        // f = function() {}
        // ns.f = function() {}
        return true;
      }

      return false;
    }

    /**
     * Returns true if the function containing n can be fixed up.
     * Also returns true if n is in the global scope -- since it is always safe
     * to specialize the global scope.
     */
    public boolean canFixupSpecializedFunctionContainingNode(Node n) {
      Node containingFunction = containingFunction(n);
      if (containingFunction != null) {
        return canFixupFunction(containingFunction);
      } else {
        // Always safe to specialize the global scope
        return true;
      }
    }

    /**
     * Returns true if a node is in the global scope; false otherwise.
     */
    private boolean nodeIsInGlobalScope(Node node) {
      return containingFunction(node) == null;
    }

    /**
     * Returns the function containing the node, or null if none exists.
     */
    private Node containingFunction(Node node) {
      for (Node ancestor : node.getAncestors()) {
        if (ancestor.isFunction()) {
          return ancestor;
        }
      }

      return null;
    }
  }
}
