/*
 * Copyright 2012 The Closure Compiler Authors.
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
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * Compiler pass for AngularJS-specific needs. Generates {@code $inject} \
 * properties for functions (class constructors, wrappers, etc) annotated with
 * @ngInject. Without this pass, AngularJS will not work properly if variable
 * renaming is enabled, because the function arguments will be renamed.
 * @see http://docs.angularjs.org/tutorial/step_05#a-note-on-minification
 *
 * <p>For example, the following code:</p>
 * <pre>{@code
 *
 * /** @ngInject * /
 * function Controller(dependency1, dependency2) {
 *   // do something
 * }
 *
 * }</pre>
 *
 * <p>will be transformed into:
 * <pre>{@code
 *
 * function Controller(dependency1, dependency2) {
 *   // do something
 * }
 * Controller.$inject = ['dependency1', 'dependency2'];
 *
 * }</pre>
 *
 * <p> This pass also supports assignments of function expressions to variables
 * like:
 * <pre>{@code
 *
 * /** @ngInject * /
 * var filter = function(a, b) {};
 *
 * var ns = {};
 * /** @ngInject * /
 * ns.method = function(a,b,c) {};
 *
 * /** @ngInject * /
 * var shorthand = ns.method2 = function(a,b,c,) {}
 *
 * }</pre>
 */
class AngularPass extends AbstractPostOrderCallback
    implements HotSwapCompilerPass {
  final AbstractCompiler compiler;

  /** Nodes annotated with @ngInject */
  private final List<NodeContext> injectables = new ArrayList<>();

  public AngularPass(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  public static final String INJECT_PROPERTY_NAME = "$inject";

  static final DiagnosticType INJECT_IN_NON_GLOBAL_OR_BLOCK_ERROR =
      DiagnosticType.error("JSC_INJECT_IN_NON_GLOBAL_OR_BLOCK_ERROR",
          "@ngInject only applies to functions defined in blocks or " +
          "global scope.");

  static final DiagnosticType INJECT_NON_FUNCTION_ERROR =
      DiagnosticType.error("JSC_INJECT_NON_FUNCTION_ERROR",
          "@ngInject can only be used when defining a function or " +
          "assigning a function expression.");

  static final DiagnosticType FUNCTION_NAME_ERROR =
      DiagnosticType.error("JSC_FUNCTION_NAME_ERROR",
          "Unable to determine target function name for @ngInject.");

  @Override
  public void process(Node externs, Node root) {
    hotSwapScript(root, null);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    // Traverses AST looking for nodes annotated with @ngInject.
    NodeTraversal.traverse(compiler, scriptRoot, this);
    boolean codeChanged = false;
    // iterates through annotated nodes adding $inject property to elements.
    for (NodeContext entry : injectables) {
      String name = entry.getName();
      Node fn = entry.getFunctionNode();
      List<Node> dependencies = createDependenciesList(fn);
      // skips entry if it does have any dependencies.
      if (dependencies.isEmpty()) {
        continue;
      }
      Node dependenciesArray = IR.arraylit(dependencies.toArray(
          new Node[dependencies.size()]));
      // creates `something.$inject = ['param1', 'param2']` node.
      Node statement = IR.exprResult(
          IR.assign(
              IR.getelem(
                  NodeUtil.newQName(compiler, name),
                  IR.string(INJECT_PROPERTY_NAME)),
              dependenciesArray
          )
      );
      NodeUtil.setDebugInformation(statement, entry.getNode(), name);

      // adds `something.$inject = [...]` node after the annotated node or the following
      // goog.inherits call.
      Node insertionPoint = entry.getTarget();
      Node next = insertionPoint.getNext();
      while (next != null
             && NodeUtil.isExprCall(next)
             && compiler.getCodingConvention().getClassesDefinedByCall(
                 next.getFirstChild()) != null) {
        insertionPoint = next;
        next = insertionPoint.getNext();
      }

      insertionPoint.getParent().addChildAfter(statement, insertionPoint);
      codeChanged = true;
    }
    if (codeChanged) {
      compiler.reportCodeChange();
    }
  }

  /**
   * Given a FUNCTION node returns array of STRING nodes representing function
   * parameters.
   * @param n the FUNCTION node.
   * @return STRING nodes.
   */
  private static List<Node> createDependenciesList(Node n) {
    Preconditions.checkArgument(n.isFunction());
    Node params = NodeUtil.getFunctionParameters(n);
    if (params != null) {
      return createStringsFromParamList(params);
    }
    return Lists.newArrayList();
  }

  /**
   * Given a PARAM_LIST node creates an array of corresponding STRING nodes.
   * @param params PARAM_LIST node.
   * @return array of STRING nodes.
   */
  private static List<Node> createStringsFromParamList(Node params) {
    Node param = params.getFirstChild();
    ArrayList<Node> names = Lists.newArrayList();
    while (param != null && param.isName()) {
      names.add(IR.string(param.getString()).srcref(param));
      param = param.getNext();
    }
    return names;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    JSDocInfo docInfo = n.getJSDocInfo();
    if (docInfo != null && docInfo.isNgInject()) {
      addNode(n, t);
    }
  }

  /**
   * Add node to the list of injectables.
   * @param n node to add.
   * @param t node traversal instance.
   */
  private void addNode(Node n, NodeTraversal t) {
    Node target = null;
    Node fn = null;
    String name = null;

    switch (n.getType()) {
      // handles assignment cases like:
      // a = function() {}
      // a = b = c = function() {}
      case Token.ASSIGN:
        name = n.getFirstChild().getQualifiedName();
        // last node of chained assignment.
        fn = n;
        while (fn.isAssign()) {
          fn = fn.getLastChild();
        }
        target = n.getParent();
        break;

      // handles function case:
      // function fnName() {}
      case Token.FUNCTION:
        name = NodeUtil.getFunctionName(n);
        fn = n;
        target = n;
        break;

      // handles var declaration cases like:
      // var a = function() {}
      // var a = b = function() {}
      case Token.VAR:
        name = n.getFirstChild().getString();
        // looks for a function node.
        fn = getDeclarationRValue(n);
        target = n;
        break;
    }
    // checks that it is a function declaration.
    if (fn == null || !fn.isFunction()) {
      compiler.report(t.makeError(n, INJECT_NON_FUNCTION_ERROR));
      return;
    }
    // checks that the declaration took place in a block or in a global scope.
    if (!target.getParent().isScript() && !target.getParent().isBlock()) {
      compiler.report(t.makeError(n, INJECT_IN_NON_GLOBAL_OR_BLOCK_ERROR));
      return;
    }
    // checks that name is present, which must always be the case unless the
    // compiler allowed a syntax error or a dangling anonymous function
    // expression.
    Preconditions.checkNotNull(name);
    // registers the node.
    injectables.add(new NodeContext(name, n, fn, target));
  }

  /**
   * Given a VAR node (variable declaration) returns the node of initial value.
   *
   * <pre>{@code
   * var x;  // null
   * var y = "value"; // STRING "value" node
   * var z = x = y = function() {}; // FUNCTION node
   * }</pre>
   * @param n VAR node.
   * @return the assigned initial value, or the rightmost rvalue of an assignment
   * chain, or null.
   */
  private static Node getDeclarationRValue(Node n) {
    Preconditions.checkNotNull(n);
    Preconditions.checkArgument(n.isVar());
    n = n.getFirstChild().getFirstChild();
    if (n == null) {
      return null;
    }
    while (n.isAssign()) {
      n = n.getLastChild();
    }
    return n;
  }

  static class NodeContext {
    /** Name of the function/object. */
    private final String name;
    /** Node jsDoc is attached to. */
    private final Node node;
    /** Function node */
    private final Node functionNode;
    /** Node after which to inject the new code */
    private final Node target;

    public NodeContext(String name, Node node, Node functionNode, Node target) {
      this.name = name;
      this.node = node;
      this.functionNode = functionNode;
      this.target = target;
    }

    /**
     * @return the name.
     */
    public String getName() {
      return name;
    }

    /**
     * @return the node.
     */
    public Node getNode() {
      return node;
    }

    /**
     * @return the context.
     */
    public Node getFunctionNode() {
      return functionNode;
    }

    /**
     * @return the context.
     */
    public Node getTarget() {
      return target;
    }
  }
}
