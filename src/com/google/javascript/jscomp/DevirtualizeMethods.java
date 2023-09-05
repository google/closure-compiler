/*
 * Copyright 2009 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Predicates.alwaysTrue;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.OptimizeCalls.ReferenceMap;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.jspecify.nullness.Nullable;

/**
 * Rewrites prototype and static methods as global, free functions that take the receiver as their
 * first argument.
 *
 * <p>This transformation simplifies the call graph so smart name removal, cross module code motion
 * and other passes can do more.
 *
 * <p>To work effectively, this pass depends on {@link
 * com.google.javascript.jscomp.disambiguate.DisambiguateProperties} running first to do a lot of
 * heavy-lifting. It assumes that different methods will have unique names which in general isn't
 * true for source JavaScript.
 *
 * <p>This pass should only be used in production code if property and variable renaming are turned
 * on. Resulting code may also benefit from `--collapse_anonymous_functions` and
 * `--collapse_variable_declarations`
 *
 * <p>This pass only rewrites functions that are part of a type's prototype or are statics on a
 * ctor/interface function. A host of other preconditions must also be met. Functions that access
 * the "arguments" variable arguments object are not eligible for this optimization, for example.
 *
 * <p>For example:
 *
 * <pre>
 *     A.prototype.accumulate = function(value) {
 *       this.total += value; return this.total
 *     }
 *     var total = a.accumulate(2)
 * </pre>
 *
 * <p>will be rewritten as:
 *
 * <pre>
 *     var accumulate = function(self, value) {
 *       self.total += value; return self.total
 *     }
 *     var total = accumulate(a, 2)
 * </pre>
 *
 * <p>A similar transformation occurs for:
 *
 * <pre>
 *     /** @constructor *\/
 *     function A() { }
 *
 *     A.accumulate = function(value) {
 *       this.total += value; return this.total
 *     }
 *     var total = a.accumulate(2)
 * </pre>
 */
class DevirtualizeMethods implements OptimizeCalls.CallGraphCompilerPass {
  private final AbstractCompiler compiler;
  private ReferenceMap refMap;

  DevirtualizeMethods(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root, ReferenceMap refMap) {
    checkState(this.refMap == null, "`process` should only be called once.");

    this.refMap = refMap;

    for (Map.Entry<String, ArrayList<Node>> referenceGroup : refMap.getPropReferences()) {
      processReferenceList(referenceGroup.getKey(), referenceGroup.getValue());
    }
  }

  private void processReferenceList(String name, List<Node> sites) {
    ImmutableListMultimap<Node, Node> functionsBySite = ReferenceMap.getFunctionNodes(sites);
    if (functionsBySite.isEmpty()) {
      return; // We can't devirtualize without a definition.
    }

    // Use the first definition to ensure that all invocations are after the definition
    // (temporally). It's  possible that if class `A` is defined after class `B` there are calls to
    // `A::foo` before `B::foo` is defined.
    Node canonicalDefinitionSite = Iterables.get(functionsBySite.keySet(), 0);

    ImmutableList<Node> callSites =
        sites.stream()
            // If a site has no associated functions, it must be a call site.
            .filter((s) -> !functionsBySite.containsKey(s))
            .collect(toImmutableList());
    if (callSites.isEmpty()) {
      return; // No need to devirtualize without calls.
    }

    // Check that this method is safe for devirtualization. These are in (estimated) increasing
    // order of cost.
    if (!functionsBySite.keySet().stream().allMatch((s) -> isEligibleDefinitionSite(name, s))) {
      return;
    } else if (!functionsBySite.values().stream().allMatch(this::isEligibleDefinitionFunction)) {
      return;
    } else if (!callSites.stream()
        .allMatch((c) -> isEligibleCallSite(c, canonicalDefinitionSite))) {
      return;
    } else if (!allDefinitionsEquivalent(functionsBySite.values())) {
      // Remember that this is only valid because we already checked the scoping of the definitions.
      return;
    }

    String devirtualizedName = rewrittenMethodNameOf(name);
    JSDocInfo bestJSDoc = NodeUtil.getBestJSDocInfo(canonicalDefinitionSite);
    boolean isConstantName = bestJSDoc != null && bestJSDoc.isConstant();

    for (Node callSite : callSites) {
      rewriteCall(callSite, devirtualizedName, isConstantName);
    }
    // We only have to rewrite one definition. We've checked they're all identical so any of them
    // can replace the others. The un-rewritten ones will be dead-code eliminated.
    rewriteDefinition(canonicalDefinitionSite, devirtualizedName, isConstantName);
  }

  private boolean isPrototypeOrStaticMethodDefinition(Node node) {
    Node parent = node.getParent();
    Node grandparent = node.getGrandparent();
    if (parent == null || grandparent == null) {
      return false;
    }

    switch (node.getToken()) {
      case MEMBER_FUNCTION_DEF:
        if (NodeUtil.isEs6ConstructorMemberFunctionDef(node)) {
          return false; // Constructors aren't methods.
        }

        return true;

      case GETPROP:
        {
          // Case: `Foo.prototype.bar = function() { };
          if (!node.isFirstChildOf(parent)
              || !NodeUtil.isExprAssign(grandparent)
              || !parent.getLastChild().isFunction()) {
            return false;
          }

          if (NodeUtil.isPrototypeProperty(node)) {
            return true;
          }

          if (isDefinitelyCtorOrInterface(node.getFirstChild())) {
            return true;
          }

          return false;
        }

      case STRING_KEY:
        {
          // Case: `Foo.prototype = {
          //          bar: function() { },
          //        }`
          checkArgument(parent.isObjectLit(), parent);

          if (!parent.isSecondChildOf(grandparent)
              || !NodeUtil.isPrototypeAssignment(grandparent.getFirstChild())
              || !node.getFirstChild().isFunction()) {
            return false;
          }

          return true;
        }

      default:
        return false;
    }
  }

  private boolean isDefinitelyCtorOrInterface(Node receiver) {
    String qname = receiver.getQualifiedName();
    if (qname == null) {
      return false;
    }

    // It's safe to rely on the global scope because normalization has made all names unique. No
    // local will shadow a global name, and we're ok with missing some statics.
    Var var = refMap.getGlobalScope().getVar(qname);
    if (var == null) {
      return false;
    }

    if (var.isClass()) {
      return true;
    }

    JSDocInfo jsdoc = var.getJSDocInfo();
    if (jsdoc == null) {
      return false;
    } else if (jsdoc.isConstructorOrInterface()) {
      return true; // Case: `@constructor`, `@interface`, `@record`.
    }

    return false;
  }

  /**
   * Determines if a method definition site is eligible for rewrite as a global.
   *
   * <p>In order to be eligible for rewrite, the definition site must:
   *
   * <ul>
   *   <li>Not be exported
   *   <li>Be for a prototype method
   * </ul>
   */
  private boolean isEligibleDefinitionSite(String name, Node definitionSite) {
    switch (definitionSite.getToken()) {
      case GETPROP:
      case MEMBER_FUNCTION_DEF:
      case STRING_KEY:
      case MEMBER_FIELD_DEF:
        break;

      default:
        // No other node types are supported.
        throw new IllegalArgumentException(definitionSite.toString());
    }

    // Exporting a method prevents rewrite.
    CodingConvention codingConvention = compiler.getCodingConvention();
    if (codingConvention.isExported(name, /* local= */ false)) {
      return false;
    }

    if (!isPrototypeOrStaticMethodDefinition(definitionSite)) {
      return false;
    }

    return true;
  }

  /**
   * Determines if a method definition function is eligible for rewrite as a global function.
   *
   * <p>In order to be eligible for rewrite, the definition function must:
   *
   * <ul>
   *   <li>Be instantiated exactly once
   *   <li>Be the only possible implementation at a given site
   *   <li>Not refer to its `arguments`; no implicit varags
   *   <li>Not be an arrow function
   * </ul>
   */
  private boolean isEligibleDefinitionFunction(Node definitionFunction) {
    checkArgument(definitionFunction.isFunction(), definitionFunction);

    if (definitionFunction.isArrowFunction()) {
      return false;
    }

    for (Node ancestor = definitionFunction.getParent();
        ancestor != null;
        ancestor = ancestor.getParent()) {
      // The definition must be made exactly once. (i.e. not in a loop, conditional, or function)
      if (isScopingOrBranchingConstruct(ancestor)) {
        return false;
      }

      // TODO(nickreid): Support this so long as the definition doesn't reference the name.
      // We can't allow this in general because references to the local name:
      //  - won't be rewritten correctly
      //  - won't be the same across multiple definitions, even if they are node-wise identical.
      if (ancestor.isClass() && localNameIsDeclaredByClass(ancestor)) {
        return false;
      }
    }

    if (NodeUtil.has(definitionFunction, Node::isSuper, alwaysTrue())) {
      // TODO(b/120452418): Remove this when we have a rewrite for `super`. We punted initially due
      // to complexity.
      return false;
    }

    if (NodeUtil.doesFunctionReferenceOwnArgumentsObject(definitionFunction)) {
      // Functions that access "arguments" are not eligible since rewriting changes the structure of
      // the function params.
      return false;
    }

    return true;
  }

  /**
   * Determines if a method call is eligible for rewrite as a global function.
   *
   * <p>In order to be eligible for rewrite, the call must:
   *
   * <ul>
   *   <li>Property is never accessed outside a function call context.
   * </ul>
   */
  private boolean isEligibleCallSite(Node access, Node definitionSite) {
    Node invocation = access.getParent();
    if (!NodeUtil.isInvocationTarget(access) || !invocation.isCall()) {
      // TODO(nickreid): Use the same definition of "a call" as
      // `OptimizeCalls::ReferenceMap::isCallTarget`.
      //
      // Accessing the property in any way besides CALL has issues:
      //   - tear-off: invocations can't be tracked
      //   - as constructor: unsupported rewrite
      //   - as tagged template string: unsupported rewrite
      //   - as optional chaining call: original invocation target may be null/undefined and
      // rewriting creates an unconditional call to a well-defined global function
      return false;
    }

    // We can't rewrite functions called in modules that do not depend on the defining module.
    // This is due to a subtle execution order change introduced by rewriting. Example:
    //
    //     `x.foo().bar()` => `JSCompiler_StaticMethods_bar(x.foo())`
    //
    // Note how `JSCompiler_StaticMethods_bar` will be resolved before `x.foo()` is executed. In
    // the case that `x.foo()` defines `JSCompiler_StaticMethods_bar` (e.g. by dynamically loading
    // the defining module) this change in ordering will cause a `ReferenceError`. No error would
    // be thrown by the original code because `bar` would be resolved later.
    //
    // We choose to use module ordering to avoid this issue because:
    //   - The other eligibility checks for devirtualization prevent any other dangerous cases
    //     that JSCompiler supports.
    //   - Rewriting all call-sites in a way that preserves exact ordering (e.g. using
    //     `ExpressionDecomposer`) has a significant code-size impact (circa 2018-11-19).

    JSChunkGraph chunkGraph = compiler.getChunkGraph();
    @Nullable JSChunk definitionModule = chunkForNode(definitionSite);
    @Nullable JSChunk callModule = chunkForNode(access);
    if (definitionModule == callModule) {
      // Do nothing.
    } else if (callModule == null) {
      return false;
    } else if (!chunkGraph.dependsOn(callModule, definitionModule)) {
      return false;
    }

    return true;
  }

  /** Given a set of method definitions, verify they are the same. */
  private boolean allDefinitionsEquivalent(Collection<Node> definitions) {
    if (definitions.isEmpty()) {
      return true;
    }

    Node definition = Iterables.get(definitions, 0);
    checkArgument(definition.isFunction(), definition);

    return definitions.stream().allMatch((d) -> compiler.areNodesEqualForInlining(d, definition));
  }

  /**
   * Rewrites object method call sites as calls to global functions that take "this" as their first
   * argument.
   *
   * <p>Before: o.foo(a, b, c)
   *
   * <p>After: foo(o, a, b, c)
   */
  private void rewriteCall(Node getprop, String newMethodName, boolean isConstantName) {
    checkArgument(getprop.isGetProp(), getprop);
    Node call = getprop.getParent();
    checkArgument(call.isCall(), call);
    Node receiver = getprop.getFirstChild();

    // This rewriting does not exactly preserve order of operations; the newly inserted static
    // method name will be resolved before `receiver` is evaluated. This is known to be safe due
    // to the eligibility checks earlier in the pass.
    //
    // We choose not to do a full-fidelity rewriting (e.g. using `ExpressionDecomposer`) because
    // doing so means extracting `receiver` into a new variable at each call-site. This  has a
    // significant code-size impact (circa 2018-11-19).

    receiver.detach();
    getprop.replaceWith(receiver);
    Node newReceiver = IR.name(newMethodName).copyTypeFrom(getprop).srcref(getprop);
    if (isConstantName) {
      newReceiver.putBooleanProp(Node.IS_CONSTANT_NAME, true);
    }
    call.addChildToFront(newReceiver);

    if (receiver.isSuper()) {
      // Case: `super.foo(a, b)` => `foo(this, a, b)`
      receiver.setToken(Token.THIS);
    }

    call.putBooleanProp(Node.FREE_CALL, true);
    compiler.reportChangeToEnclosingScope(call);
  }

  /**
   * Rewrites method definitions as global functions that take "this" as their first argument.
   *
   * <p>Before: a.prototype.b = function(a, b, c) {...}
   *
   * <p>After: var b = function(self, a, b, c) {...}
   */
  private void rewriteDefinition(
      Node definitionSite, String newMethodName, boolean isConstantName) {
    final Node function;
    final Node subtreeToRemove;
    final Node nameSource;

    switch (definitionSite.getToken()) {
      case GETPROP:
        function = definitionSite.getParent().getLastChild();
        nameSource = definitionSite;
        subtreeToRemove = NodeUtil.getEnclosingStatement(definitionSite);
        break;

      case STRING_KEY:
      case MEMBER_FUNCTION_DEF:
        function = definitionSite.getLastChild();
        nameSource = definitionSite;
        subtreeToRemove = definitionSite;
        break;

      default:
        throw new IllegalArgumentException(definitionSite.toString());
    }

    // Define a new variable after the original declaration.
    Node statement = NodeUtil.getEnclosingStatement(definitionSite);
    Node newNameNode = IR.name(newMethodName).srcrefIfMissing(nameSource);
    Node newVarNode = IR.var(newNameNode).srcrefIfMissing(nameSource);
    newVarNode.insertBefore(statement);
    if (isConstantName) {
      newNameNode.putBooleanProp(Node.IS_CONSTANT_NAME, true);
    }

    // Copy the JSDocInfo, if any, from the original declaration
    JSDocInfo originalJSDoc = NodeUtil.getBestJSDocInfo(definitionSite);
    newVarNode.setJSDocInfo(originalJSDoc);

    // Attach the function to the new variable.
    function.detach();
    newNameNode.addChildToFront(function);

    // Create the `this` param.
    String selfName = newMethodName + "$self";
    Node paramList = function.getSecondChild();
    paramList.addChildToFront(IR.name(selfName).srcrefIfMissing(function));
    compiler.reportChangeToEnclosingScope(paramList);

    // Eliminate `this`.
    replaceReferencesToThis(function.getSecondChild(), selfName); // In default param values.
    replaceReferencesToThis(function.getLastChild(), selfName); // In function body.

    // Clean up dangling AST.
    NodeUtil.deleteNode(subtreeToRemove, compiler);
    compiler.reportChangeToEnclosingScope(newVarNode);
  }

  /** Replaces references to "this" with references to name. Do not traverse function boundaries. */
  private void replaceReferencesToThis(Node node, String name) {
    if (node.isFunction() && !node.isArrowFunction()) {
      // Functions (besides arrows) create a new binding for `this`.
      return;
    }

    for (Node child = node.getFirstChild(); child != null; ) {
      final Node next = child.getNext();
      if (child.isThis()) {
        Node newName = IR.name(name).srcref(child).copyTypeFrom(child);
        child.replaceWith(newName);
        compiler.reportChangeToEnclosingScope(newName);
      } else {
        replaceReferencesToThis(child, name);
      }
      child = next;
    }
  }

  private @Nullable JSChunk chunkForNode(Node node) {
    Node script = NodeUtil.getEnclosingScript(node);
    CompilerInput input = compiler.getInput(script.getInputId());
    return input.getChunk();
  }

  private static String rewrittenMethodNameOf(String originalMethodName) {
    return "JSCompiler_StaticMethods_" + originalMethodName;
  }

  /**
   * Returns {@code true} iff a node may change the variable bindings of its subtree or cause that
   * subtree to be executed not exactly once.
   *
   * <p>This method does not include CLASS because CLASS does not always create a new binding and it
   * is important for the success of this optimization to consider class methods.
   *
   * @see {@link #localNameIsDeclaredByClass()}
   */
  private static boolean isScopingOrBranchingConstruct(Node node) {
    return NodeUtil.isControlStructure(node) // Branching.
        || node.isAnd() // Branching.
        || node.isOr() // Branching.
        || node.isFunction() // Branching & scoping.
        || node.isBlock(); // Scoping.
  }

  /**
   * Returns {@code true} iff a CLASS subtree declares a name local to the class body.
   *
   * <p>Example:
   *
   * <pre>{@code
   * const Foo = class Bar {
   *   qux() { return Bar; }
   * }
   * }</pre>
   */
  private static boolean localNameIsDeclaredByClass(Node clazz) {
    checkArgument(clazz.isClass(), clazz);

    if (clazz.getFirstChild().isEmpty()) {
      return false; // There must be a name.
    } else if (NodeUtil.isStatement(clazz)) {
      return false; // The name must be local.
    }

    return true;
  }
}
