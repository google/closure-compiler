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

import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.jscomp.DefinitionsRemover.Definition;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.ObjectType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Rewrites prototyped methods calls as static calls that take "this"
 * as their first argument.  This transformation simplifies the call
 * graph so smart name removal, cross module code motion and other
 * passes can do more.
 *
 * <p>This pass should only be used in production code if property
 * and variable renaming are turned on.  Resulting code may also
 * benefit from --collapse_anonymous_functions and
 * --collapse_variable_declarations
 *
 * <p>This pass only rewrites functions that are part of an objects
 * prototype.  Functions that access the "arguments" variable
 * arguments object are not eligible for this optimization.
 *
 * <p>For example:
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
 */
class DevirtualizePrototypeMethods implements CompilerPass {
  private final AbstractCompiler compiler;

  DevirtualizePrototypeMethods(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    DefinitionUseSiteFinder defFinder = new DefinitionUseSiteFinder(compiler);
    defFinder.process(externs, root);
    process(externs, root, defFinder);
  }

  public void process(
      Node externs, Node root, DefinitionUseSiteFinder definitions) {
    for (DefinitionSite defSite : definitions.getDefinitionSites()) {
      rewriteDefinitionIfEligible(defSite, definitions);
    }
  }

  /**
   * Determines if the name node acts as the function name in a call expression.
   */
  private static boolean isCall(UseSite site) {
    Node node = site.node;
    Node parent = node.getParent();
    if (parent == null) {
      return false;
    }
    return (parent.getFirstChild() == node) && parent.isCall();
  }

  /**
   * Determines if the current node is a function prototype definition.
   */
  private static boolean isPrototypeMethodDefinition(Node node) {
    Node parent = node.getParent();
    if (parent == null) {
      return false;
    }
    Node gramp = parent.getParent();
    if (gramp == null) {
      return false;
    }

    if (node.isGetProp()) {
      if (parent.getFirstChild() != node) {
        return false;
      }

      if (!NodeUtil.isExprAssign(gramp)) {
        return false;
      }

      Node functionNode = parent.getLastChild();
      if ((functionNode == null) || !functionNode.isFunction()) {
        return false;
      }

      Node nameNode = node.getFirstChild();
      return nameNode.isGetProp() &&
          nameNode.getLastChild().getString().equals("prototype");
    } else if (node.isStringKey()) {
      checkState(parent.isObjectLit());

      if (!gramp.isAssign()) {
        return false;
      }

      if (gramp.getLastChild() != parent) {
        return false;
      }

      Node greatGramp = gramp.getParent();
      if (greatGramp == null || !greatGramp.isExprResult()) {
        return false;
      }

      Node functionNode = node.getFirstChild();
      if ((functionNode == null) || !functionNode.isFunction()) {
        return false;
      }

      Node target = gramp.getFirstChild();
      return target.isGetProp() &&
          target.getLastChild().getString().equals("prototype");
    } else {
      return false;
    }
  }

  private static String getMethodName(Node node) {
    if (node.isGetProp()) {
      return node.getLastChild().getString();
    } else if (node.isStringKey()) {
      return node.getString();
    } else {
      throw new IllegalStateException("unexpected");
    }
  }

  /**
   * @return The new name for a rewritten method.
   */
  private static String getRewrittenMethodName(String originalMethodName) {
    return "JSCompiler_StaticMethods_" + originalMethodName;
  }

  /**
   * Rewrites method definition and call sites if the method is
   * defined in the global scope exactly once.
   *
   * Definition and use site information is provided by the
   * {@link DefinitionUseSiteFinder} passed in as an argument.
   *
   * @param defSite definition site to process.
   * @param defFinder structure that hold Node -> Definition and
   * Definition -> [UseSite] maps.
   */
  private void rewriteDefinitionIfEligible(DefinitionSite defSite,
                                           DefinitionUseSiteFinder defFinder) {
    if (defSite.inExterns ||
        !defSite.inGlobalScope ||
        !isEligibleDefinition(defFinder, defSite)) {
      return;
    }

    Node node = defSite.node;
    if (!isPrototypeMethodDefinition(node)) {
      return;
    }

    for (Node ancestor = node.getParent();
         ancestor != null;
         ancestor = ancestor.getParent()) {
      if (NodeUtil.isControlStructure(ancestor)) {
        return;
      }
    }

    // TODO(user) The code only works if there is a single definition associated with a property
    // name.
    // Whatever scheme we use should not break stable renaming.
    String newMethodName = getRewrittenMethodName(
        getMethodName(node));
    rewriteDefinition(node, newMethodName);
    rewriteCallSites(defFinder, defSite.definition, newMethodName);
  }

  /**
   * Determines if a method definition is eligible for rewrite as a
   * global function.  In order to be eligible for rewrite, the
   * definition must:
   *
   * - Refer to a function that takes a fixed number of arguments.
   * - Function must not be exported.
   * - Function must be used at least once.
   * - Property is never accessed outside a function call context.
   * - The definition under consideration must be the only possible
   *   choice at each call site.
   * - Definition must happen in a module loaded before the first use.
   */
  private boolean isEligibleDefinition(DefinitionUseSiteFinder defFinder,
                                       DefinitionSite definitionSite) {

    Definition definition = definitionSite.definition;
    JSModule definitionModule = moduleForNode(definitionSite.node);

    // Only functions may be rewritten.
    // Functions that access "arguments" are not eligible since
    // rewrite changes the structure of this object.
    Node rValue = definition.getRValue();
    if (rValue == null
        || !rValue.isFunction()
        || NodeUtil.doesFunctionReferenceOwnArgumentsObject(rValue)) {
      return false;
    }

    Node lValue = definition.getLValue();
    if ((lValue == null)
        || !lValue.isGetProp()) {
      return false;
    }

    // Note: the definition for prototype defined with an object literal returns
    // a mock return LValue of the form "{}.prop".
    if (!lValue.isQualifiedName()
        && !lValue.getFirstChild().isObjectLit()) {
      return false;
    }

    // Exporting a method prevents rewrite.
    CodingConvention codingConvention = compiler.getCodingConvention();
    if (codingConvention.isExported(lValue.getLastChild().getString())) {
      return false;
    }

    Collection<UseSite> useSites = defFinder.getUseSites(definition);

    // Rewriting unused methods is not sound.
    if (useSites.isEmpty()) {
      return false;
    }

    JSModuleGraph moduleGraph = compiler.getModuleGraph();

    for (UseSite site : useSites) {
      // Accessing the property directly prevents rewrite.
      if (!isCall(site)) {
        return false;
      }

      Node nameNode = site.node;

      // Multiple definitions prevent rewrite.
      Collection<Definition> singleSiteDefinitions =
          defFinder.getDefinitionsReferencedAt(nameNode);
      if (!allDefinitionsEquivalent(singleSiteDefinitions)) {
        return false;
      }
      checkState(!singleSiteDefinitions.isEmpty());
      checkState(singleSiteDefinitions.contains(definition));

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
      @Nullable JSModule callModule = moduleForNode(nameNode);
      if (definitionModule == callModule) {
        // Do nothing.
      } else if (callModule == null) {
        return false;
      } else if (!moduleGraph.dependsOn(callModule, definitionModule)) {
        return false;
      }
    }

    return true;
  }

  /** Given a set of method definitions, verify they are the same. */
  boolean allDefinitionsEquivalent(Collection<Definition> definitions) {
    if (definitions.size() <= 1) {
      return true;
    }

    Definition first = null;
    for (Definition definition : definitions) {
      if (definition.getRValue() == null) {
        return false; // We can't tell if they're all the same.
      }

      if (first == null) {
        first = definition;
        continue;
      }

      if (!compiler.areNodesEqualForInlining(first.getRValue(), definition.getRValue())) {
        return false;
      }
    }
    return true;
  }

  /**
   * Rewrites object method call sites as calls to global functions
   * that take "this" as their first argument.
   *
   * Before:
   *   o.foo(a, b, c)
   *
   * After:
   *   foo(o, a, b, c)
   */
  private void rewriteCallSites(DefinitionUseSiteFinder defFinder,
                                Definition definition,
                                String newMethodName) {
    Collection<UseSite> useSites = defFinder.getUseSites(definition);
    for (UseSite site : useSites) {
      Node getprop = site.node;
      checkState(getprop.isGetProp());
      Node call = getprop.getParent();
      checkState(call.isCall());
      Node receiever = getprop.getFirstChild();

      // This rewriting does not exactly preserve order of operations; the newly inserted static
      // method name will be resolved before `receiver` is evaluated. This is known to be safe due
      // to the eligibility checks earlier in the pass.
      //
      // We choose not to do a full-fidelity rewriting (e.g. using `ExpressionDecomposer`) because
      // doing so means extracting `receiver` into a new variable at each call-site. This  has a
      // significant code-size impact (circa 2018-11-19).

      getprop.removeChild(receiever);
      call.replaceChild(getprop, receiever);
      call.addChildToFront(IR.name(newMethodName).srcref(getprop));
      call.putBooleanProp(Node.FREE_CALL, true);
      compiler.reportChangeToEnclosingScope(call);
    }
  }

  /**
   * Rewrites method definitions as global functions that take "this"
   * as their first argument.
   *
   * Before:
   *   a.prototype.b = function(a, b, c) {...}
   *
   * After:
   *   var b = function(self, a, b, c) {...}
   */
  private void rewriteDefinition(Node node, String newMethodName) {
    boolean isObjLitDefKey = node.isStringKey();

    Node parent = node.getParent();

    Node refNode = isObjLitDefKey ? node : parent.getFirstChild();
    Node newNameNode = IR.name(newMethodName).useSourceInfoIfMissingFrom(refNode);
    Node newVarNode = IR.var(newNameNode).useSourceInfoIfMissingFrom(refNode);

    Node functionNode;
    if (!isObjLitDefKey) {
      checkState(parent.isAssign());
      functionNode = parent.getLastChild();
      Node expr = parent.getParent();
      Node block = expr.getParent();
      parent.removeChild(functionNode);
      newNameNode.addChildToFront(functionNode);
      block.replaceChild(expr, newVarNode);
    } else {
      checkState(parent.isObjectLit());
      functionNode = node.getFirstChild();
      Node assign = parent.getParent();
      Node expr = assign.getParent();
      Node block = expr.getParent();

      node.removeChild(functionNode);
      parent.removeChild(node);
      newNameNode.addChildToFront(functionNode);
      block.addChildAfter(newVarNode, expr);
    }
    compiler.reportChangeToEnclosingScope(newVarNode);

    // add extra argument
    String self = newMethodName + "$self";
    Node argList = functionNode.getSecondChild();
    argList.addChildToFront(IR.name(self)
        .useSourceInfoIfMissingFrom(functionNode));
    compiler.reportChangeToEnclosingScope(argList);

    // rewrite body
    Node body = functionNode.getLastChild();
    if (replaceReferencesToThis(body, self)) {
      compiler.reportChangeToEnclosingScope(body);
    }

    // fix type
    fixFunctionType(functionNode);
  }

  /**
   * Creates a new type based on the original function type by
   * adding the original this pointer type to the beginning of the
   * argument type list and replacing the this pointer type with bottom.
   */
  private void fixFunctionType(Node functionNode) {
    JSType t = functionNode.getJSType();
    if (t == null) {
      return;
    }
    FunctionType ft = t.toMaybeFunctionType();
    if (ft != null) {
      functionNode.setJSType(convertMethodToFunction(ft));
    }
  }

  private JSType convertMethodToFunction(FunctionType method) {
    List<JSType> paramTypes = new ArrayList<>();
    paramTypes.add(method.getTypeOfThis());
    for (Node param : method.getParameters()) {
      paramTypes.add(param.getJSType());
    }
    ObjectType unknown = compiler.getTypeRegistry().getNativeObjectType(JSTypeNative.UNKNOWN_TYPE);
    return compiler.getTypeRegistry().createFunctionTypeWithInstanceType(
        unknown, method.getReturnType(), paramTypes);
  }

  /**
   * Replaces references to "this" with references to name.  Do not
   * traverse function boundaries.
   */
  private static boolean replaceReferencesToThis(Node node, String name) {
    if (node.isFunction()) {
      return false;
    }

    boolean changed = false;
    for (Node child : node.children()) {
      if (child.isThis()) {
        Node newName = IR.name(name);
        newName.setJSType(child.getJSType());
        node.replaceChild(child, newName);
        changed = true;
      } else {
        changed |= replaceReferencesToThis(child, name);
      }
    }

    return changed;
  }

  @Nullable
  private JSModule moduleForNode(Node node) {
    Node script = NodeUtil.getEnclosingScript(node);
    CompilerInput input = compiler.getInput(script.getInputId());
    return input.getModule();
  }
}
