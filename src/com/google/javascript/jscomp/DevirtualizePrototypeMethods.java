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

import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.DefinitionsRemover.Definition;
import com.google.javascript.rhino.FunctionTypeI;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.TypeI;

import java.util.Collection;

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
class DevirtualizePrototypeMethods
    implements OptimizeCalls.CallGraphCompilerPass, CompilerPass {
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

  @Override
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
      Preconditions.checkState(parent.isObjectLit());

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
    JSModule definitionModule = definitionSite.module;

    // Only functions may be rewritten.
    // Functions that access "arguments" are not eligible since
    // rewrite changes the structure of this object.
    Node rValue = definition.getRValue();
    if (rValue == null
        || !rValue.isFunction()
        || NodeUtil.isVarArgsFunction(rValue)) {
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
      Preconditions.checkState(!singleSiteDefinitions.isEmpty());
      Preconditions.checkState(singleSiteDefinitions.contains(definition));

      // Accessing the property in a module loaded before the
      // definition module prevents rewrite; accessing a variable
      // before definition results in a parse error.
      JSModule callModule = site.module;
      if ((definitionModule != callModule) &&
          ((callModule == null) ||
          !moduleGraph.dependsOn(callModule, definitionModule))) {
        return false;
      }
    }

    return true;
  }

  /**
   * Given a set of method definitions, verify they are the same.
   */
  boolean allDefinitionsEquivalent(
      Collection<Definition> definitions) {
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
      Node node = site.node;
      Node parent = node.getParent();

      Node objectNode = node.getFirstChild();
      node.removeChild(objectNode);
      parent.replaceChild(node, objectNode);
      parent.addChildToFront(IR.name(newMethodName).srcref(node));
      Preconditions.checkState(parent.isCall());
      parent.putBooleanProp(Node.FREE_CALL, true);
      compiler.reportCodeChange();
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
      Preconditions.checkState(parent.isAssign());
      functionNode = parent.getLastChild();
      Node expr = parent.getParent();
      Node block = expr.getParent();
      parent.removeChild(functionNode);
      newNameNode.addChildToFront(functionNode);
      block.replaceChild(expr, newVarNode);
    } else {
      Preconditions.checkState(parent.isObjectLit());
      functionNode = node.getFirstChild();
      Node assign = parent.getParent();
      Node expr = assign.getParent();
      Node block = expr.getParent();

      node.removeChild(functionNode);
      parent.removeChild(node);
      newNameNode.addChildToFront(functionNode);
      block.addChildAfter(newVarNode, expr);
    }

    // add extra argument
    String self = newMethodName + "$self";
    Node argList = functionNode.getSecondChild();
    argList.addChildToFront(IR.name(self)
        .useSourceInfoIfMissingFrom(functionNode));

    // rewrite body
    Node body = functionNode.getLastChild();
    replaceReferencesToThis(body, self);

    // fix type
    fixFunctionType(functionNode);

    compiler.reportCodeChange();
  }

  /**
   * Creates a new type based on the original function type by
   * adding the original this pointer type to the beginning of the
   * argument type list and replacing the this pointer type with bottom.
   */
  private void fixFunctionType(Node functionNode) {
    TypeI t = functionNode.getTypeI();
    if (t == null) {
      return;
    }
    FunctionTypeI ft = t.toMaybeFunctionType();
    if (ft != null) {
      functionNode.setTypeI(ft.convertMethodToFunction());
    }
  }

  /**
   * Replaces references to "this" with references to name.  Do not
   * traverse function boundaries.
   */
  private static void replaceReferencesToThis(Node node, String name) {
    if (node.isFunction()) {
      return;
    }

    for (Node child : node.children()) {
      if (child.isThis()) {
        Node newName = IR.name(name);
        newName.setTypeI(child.getTypeI());
        node.replaceChild(child, newName);
      } else {
        replaceReferencesToThis(child, name);
      }
    }
  }
}
