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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.Node;
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
 * <pre><code>
 *
 * /** @ngInject * /
 * function Controller(dependency1, dependency2) {
 *   // do something
 * }
 *
 * </code></pre>
 *
 * <p>will be transformed into:
 * <pre><code>
 *
 * function Controller(dependency1, dependency2) {
 *   // do something
 * }
 * Controller.$inject = ['dependency1', 'dependency2'];
 *
 * </code></pre>
 *
 * <p> This pass also supports assignments of function expressions to variables
 * like:
 * <pre><code>
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
 * </code></pre>
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

  static final DiagnosticType INJECTED_FUNCTION_HAS_DESTRUCTURED_PARAM =
      DiagnosticType.error("JSC_INJECTED_FUNCTION_HAS_DESTRUCTURED_PARAM",
          "@ngInject cannot be used on functions containing "
          + "destructured parameter.");

  static final DiagnosticType INJECTED_FUNCTION_HAS_DEFAULT_VALUE =
      DiagnosticType.error("JSC_INJECTED_FUNCTION_HAS_DEFAULT_VALUE",
          "@ngInject cannot be used on functions containing default value.");

  static final DiagnosticType INJECTED_FUNCTION_ON_NON_QNAME =
      DiagnosticType.error("JSC_INJECTED_FUNCTION_ON_NON_QNAME",
          "@ngInject can only be used on qualified names.");

  @Override
  public void process(Node externs, Node root) {
    hotSwapScript(root, null);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    // Traverses AST looking for nodes annotated with @ngInject.
    NodeTraversal.traverse(compiler, scriptRoot, this);
    // iterates through annotated nodes adding $inject property to elements.
    for (NodeContext entry : injectables) {
      String name = entry.getName();
      Node fn = entry.getFunctionNode();
      List<Node> dependencies = createDependenciesList(fn);
      // skips entry if it does not have any dependencies.
      if (dependencies.isEmpty()) {
        continue;
      }
      Node dependenciesArray = IR.arraylit(dependencies);
      // creates `something.$inject = ['param1', 'param2']` node.
      Node statement =
          IR.exprResult(
              IR.assign(
                  IR.getelem(NodeUtil.newQName(compiler, name), IR.string(INJECT_PROPERTY_NAME)),
                  dependenciesArray));
      statement.srcrefTree(entry.getNode());
      statement.setOriginalName(name);
      // Set the visibility of the newly created property.
      JSDocInfo.Builder newPropertyDoc = JSDocInfo.builder();
      newPropertyDoc.recordVisibility(Visibility.PUBLIC);
      statement.getFirstChild().setJSDocInfo(newPropertyDoc.build());

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

      statement.insertAfter(insertionPoint);
      compiler.reportChangeToEnclosingScope(statement);
    }
  }

  /**
   * Given a FUNCTION node returns array of STRING nodes representing function
   * parameters.
   * @param n the FUNCTION node.
   * @return STRING nodes.
   */
  private List<Node> createDependenciesList(Node n) {
    checkArgument(n.isFunction());
    Node params = NodeUtil.getFunctionParameters(n);
    if (params != null) {
      return createStringsFromParamList(params);
    }
    return new ArrayList<>();
  }

  /**
   * Given a PARAM_LIST node creates an array of corresponding STRING nodes.
   * @param params PARAM_LIST node.
   * @return array of STRING nodes.
   */
  private List<Node> createStringsFromParamList(Node params) {
    Node param = params.getFirstChild();
    ArrayList<Node> names = new ArrayList<>();
    while (param != null) {
      if (param.isName()) {
        names.add(IR.string(param.getString()).srcref(param));
      } else if (param.isDestructuringPattern()) {
        compiler.report(JSError.make(param,
            INJECTED_FUNCTION_HAS_DESTRUCTURED_PARAM));
        return new ArrayList<>();
      } else if (param.isDefaultValue()) {
        compiler.report(JSError.make(param,
            INJECTED_FUNCTION_HAS_DEFAULT_VALUE));
        return new ArrayList<>();
      }
      param = param.getNext();
    }
    return names;
  }

  @Override
  public void visit(NodeTraversal unused, Node n, Node parent) {
    JSDocInfo docInfo = n.getJSDocInfo();
    if (docInfo != null && docInfo.isNgInject()) {
      addNode(n);
    }
  }

  /**
   * Add node to the list of injectables.
   *
   * @param n node to add.
   */
  private void addNode(Node n) {
    Node injectAfter = null;
    Node fn = null;
    String name = null;

    switch (n.getToken()) {
      // handles assignment cases like:
      // a = function() {}
      // a = b = c = function() {}
      case ASSIGN:
        if (!n.getFirstChild().isQualifiedName()) {
          compiler.report(JSError.make(n, INJECTED_FUNCTION_ON_NON_QNAME));
          return;
        }
        name = n.getFirstChild().getQualifiedName();
        // last node of chained assignment.
        fn = n;
        while (fn.isAssign()) {
          fn = fn.getLastChild();
        }
        injectAfter = n.getParent();
        break;

      // handles function case:
      // function fnName() {}
      case FUNCTION:
        name = NodeUtil.getName(n);
        fn = n;
        injectAfter = n;
        if (n.getParent().isAssign()
            && n.getParent().getJSDocInfo().isNgInject()) {
          // This is a function assigned into a symbol, e.g. a regular function
          // declaration in a goog.module or goog.scope.
          // Skip in this traversal, it is handled when visiting the assign.
          return;
        }
        break;

      // handles var declaration cases like:
      // var a = function() {}
      // var a = b = function() {}
      case VAR:
      case LET:
      case CONST:
        name = n.getFirstChild().getString();
        // looks for a function node.
        fn = getDeclarationRValue(n);
        injectAfter = n;
        break;

      // handles class method case:
      // class clName(){
      //   constructor(){}
      //   someMethod(){} <===
      // }
      case MEMBER_FUNCTION_DEF:
        Node parent = n.getParent();
        if (parent.isClassMembers()){
          Node classNode = parent.getParent();
          String midPart = n.isStaticMember() ? "." : ".prototype.";
          name = NodeUtil.getName(classNode) + midPart + n.getString();
          if (NodeUtil.isEs6ConstructorMemberFunctionDef(n)) {
            name = NodeUtil.getName(classNode);
          }
          fn = n.getFirstChild();
          if (classNode.getParent().isAssign() || classNode.getParent().isName()) {
            injectAfter = classNode.getGrandparent();
          } else {
            injectAfter = classNode;
          }
        }
        break;
      default:
        break;
    }

    if (fn == null || !fn.isFunction()) {
      compiler.report(JSError.make(n, INJECT_NON_FUNCTION_ERROR));
      return;
    }
    if (injectAfter.getParent().isExport()) {
      // handle `export class Foo {` or `export function(`
      injectAfter = injectAfter.getParent();
    }
    // report an error if the function declaration did not take place in the root of a statement
    // for example, `fn(/** @inject */ function(x) {});` is forbidden
    if (!NodeUtil.isStatementBlock(injectAfter.getParent())) {
      compiler.report(JSError.make(n, INJECT_IN_NON_GLOBAL_OR_BLOCK_ERROR));
      return;
    }
    // checks that name is present, which must always be the case unless the
    // compiler allowed a syntax error or a dangling anonymous function
    // expression.
    checkNotNull(name);
    // registers the node.
    injectables.add(new NodeContext(name, n, fn, injectAfter));
  }

  /**
   * Given a VAR node (variable declaration) returns the node of initial value.
   *
   * <pre><code>
   * var x;  // null
   * var y = "value"; // STRING "value" node
   * var z = x = y = function() {}; // FUNCTION node
   * <code></pre>
   * @param n VAR node.
   * @return the assigned initial value, or the rightmost rvalue of an assignment
   * chain, or null.
   */
  private static Node getDeclarationRValue(Node n) {
    checkNotNull(n);
    checkArgument(NodeUtil.isNameDeclaration(n));
    n = n.getFirstFirstChild();
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
