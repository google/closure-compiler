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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.regex.Pattern;

/**
 * Generates goog.exportSymbol for test functions, so they can be recognized
 * by the test runner, even if the code is compiled.
 */
public class ExportTestFunctions implements CompilerPass {

  private static final Pattern TEST_FUNCTIONS_NAME_PATTERN =
      Pattern.compile(
          "^(?:((\\w+\\.)+prototype\\.||window\\.)*"
              + "(setUpPage|setUp|shouldRunTests|tearDown|tearDownPage|test[\\w\\$]+))$");
  private static final String GOOG_TESTING_TEST_SUITE = "goog.testing.testSuite";

  private final AbstractCompiler compiler;
  private final String exportSymbolFunction;
  private final String exportPropertyFunction;

  /**
   * Creates a new export test functions compiler pass.
   * @param compiler
   * @param exportSymbolFunction The function name used to export symbols in JS.
   * @param exportPropertyFunction The function name used to export properties
   *     in JS.
   */
  ExportTestFunctions(AbstractCompiler compiler,
      String exportSymbolFunction, String exportPropertyFunction) {

    checkNotNull(compiler);
    this.compiler = compiler;
    this.exportSymbolFunction = exportSymbolFunction;
    this.exportPropertyFunction = exportPropertyFunction;
  }

  private class ExportTestFunctionsNodes extends NodeTraversal.AbstractShallowCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (parent == null) {
        return;
      }

      if (parent.isScript() || parent.isModuleBody()) {
        if (NodeUtil.isFunctionDeclaration(n)) {
          // Check for a test function statement.
          String functionName = NodeUtil.getName(n);
          if (isTestFunction(functionName)) {
            exportTestFunctionAsSymbol(functionName, n);
          }
        } else if (isNameDeclaredFunction(n)) {
          // Check for a test function expression.
          Node functionNode = n.getFirstFirstChild();
          String functionName = NodeUtil.getName(functionNode);
          if (isTestFunction(functionName)) {
            exportTestFunctionAsSymbol(functionName, n);
          }
        } else if (isNameDeclaredClass(n)) {
          Node classNode = n.getFirstFirstChild();
          String className = NodeUtil.getName(classNode);
          exportClass(classNode, className, n);
        } else if (n.isClass()) {
          exportClass(n);
        }
      } else if (NodeUtil.isExprAssign(parent)) {
        // Check for a test method assignment.
        Node grandparent = parent.getParent();
        if (grandparent != null && (grandparent.isScript() || grandparent.isModuleBody())) {
          //                                    NAME/(GETPROP -> ... -> NAME)
          // SCRIPT -> EXPR_RESULT -> ASSIGN ->
          //                                    FUNCTION/CLASS
          Node firstChild = n.getFirstChild();
          Node lastChild = n.getLastChild();
          String nodeName = firstChild.getQualifiedName();

          if (lastChild.isFunction()) {
            if (isTestFunction(nodeName)) {
              if (n.getFirstChild().isName()) {
                exportTestFunctionAsSymbol(nodeName, parent);
              } else {
                exportTestFunctionAsProperty(firstChild, n);
              }
            }
          } else if (lastChild.isClass()) {
            exportClass(lastChild, nodeName, parent);
          }
        }
      } else if (isTestSuiteArgument(n, t)) {
        for (Node c = n.getFirstChild(); c != null; ) {
          final Node next = c.getNext();
          if (c.isStringKey() && !c.isQuotedString()) {
            c.setQuotedString();
            compiler.reportChangeToEnclosingScope(c);
          } else if (c.isMemberFunctionDef()) {
            rewriteMemberDefInObjLit(c, n);
          }
          c = next;
        }
      }
    }

    private void exportClass(Node classNode) {
      String className = NodeUtil.getName(classNode);
      exportClass(classNode, className, classNode);
    }

    private void exportClass(Node classNode, String className, Node addAfter) {
      Node classMembers = classNode.getLastChild();
      for (Node maybeMemberFunctionDef = classMembers.getFirstChild();
          maybeMemberFunctionDef != null;
          maybeMemberFunctionDef = maybeMemberFunctionDef.getNext()) {
        if (maybeMemberFunctionDef.isMemberFunctionDef()) {
          String methodName = maybeMemberFunctionDef.getString();
          if (isTestFunction(methodName)) {
            String functionRef = className + ".prototype." + methodName;
            String classRef = className + ".prototype";

            Node exportCallTarget =
                NodeUtil.newQName(
                    compiler, exportPropertyFunction, maybeMemberFunctionDef, methodName);
            Node call = IR.call(exportCallTarget);
            if (exportCallTarget.isName()) {
              call.putBooleanProp(Node.FREE_CALL, true);
            }

            call.addChildToBack(
                NodeUtil.newQName(compiler, classRef, maybeMemberFunctionDef, classRef));
            call.addChildToBack(IR.string(methodName));
            call.addChildToBack(
                NodeUtil.newQName(compiler, functionRef, maybeMemberFunctionDef, functionRef));

            Node expression = IR.exprResult(call).srcrefTreeIfMissing(maybeMemberFunctionDef);

            expression.insertAfter(addAfter);
            compiler.reportChangeToEnclosingScope(expression);
            addAfter = expression;
          }
        }
      }
    }

    /** Converts a member function into a quoted string key to avoid property renaming */
    private void rewriteMemberDefInObjLit(Node memberDef, Node objLit) {
      String name = memberDef.getString();
      Node stringKey = IR.stringKey(name, memberDef.removeFirstChild());
      memberDef.replaceWith(stringKey);
      stringKey.setQuotedString();
      stringKey.setJSDocInfo(memberDef.getJSDocInfo());
      compiler.reportChangeToEnclosingScope(objLit);
    }

    /**
     * Get the node that corresponds to an expression declared with var, let or const.
     * This has the AST structure VAR/LET/CONST -> NAME -> NODE
     * @param node
     */
    private Node getNameDeclaredGrandchild(Node node) {
      if (!NodeUtil.isNameDeclaration(node)) {
        return null;
      }
      return node.getFirstFirstChild();
    }

    /**
     * Whether node corresponds to a function expression declared with var, let
     * or const which is of the form:
     * <pre>
     * var/let/const functionName = function() {
     *   // Implementation
     * };
     * </pre>
     * This has the AST structure VAR/LET/CONST -> NAME -> FUNCTION
     * @param node
     */
    private boolean isNameDeclaredFunction(Node node) {
      Node grandchild = getNameDeclaredGrandchild(node);
      return grandchild != null && grandchild.isFunction();
    }

    /**
     * Whether node corresponds to a class declared with var, let or const which
     * is of the form:
     * <pre>
     * var/let/const className = class {
     *   // Implementation
     * };
     * </pre>
     * This has the AST structure VAR/LET/CONST -> NAME -> CLASS
     * @param node
     */
    private boolean isNameDeclaredClass(Node node) {
      Node grandchild = getNameDeclaredGrandchild(node);
      return grandchild != null && grandchild.isClass();
    }
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, new ExportTestFunctionsNodes());
  }

  // Adds exportSymbol(testFunctionName, testFunction);
  private void exportTestFunctionAsSymbol(String testFunctionName, Node node) {

    Node exportCallTarget = NodeUtil.newQName(compiler,
        exportSymbolFunction, node, testFunctionName);
    Node call = IR.call(exportCallTarget);
    if (exportCallTarget.isName()) {
      call.putBooleanProp(Node.FREE_CALL, true);
    }
    call.addChildToBack(IR.string(testFunctionName));
    call.addChildToBack(NodeUtil.newQName(compiler,
        testFunctionName, node, testFunctionName));

    Node expression = IR.exprResult(call).srcrefTreeIfMissing(node);

    expression.insertAfter(node);
    compiler.reportChangeToEnclosingScope(expression);
  }

  // Adds exportProperty() of the test function name on the prototype object
  private void exportTestFunctionAsProperty(Node fullyQualifiedFunctionName, Node node) {
    checkState(fullyQualifiedFunctionName.isGetProp(), fullyQualifiedFunctionName);

    String testFunctionName =
        NodeUtil.getPrototypePropertyName(node.getFirstChild());
    if (node.getFirstChild().getQualifiedName().startsWith("window.")) {
      testFunctionName = node.getFirstChild().getQualifiedName().substring("window.".length());
    }

    Node exportCall =
        IR.call(
            NodeUtil.newQName(this.compiler, this.exportPropertyFunction),
            fullyQualifiedFunctionName.getOnlyChild().cloneTree(),
            IR.string(testFunctionName),
            fullyQualifiedFunctionName.cloneTree());
    exportCall.putBooleanProp(Node.FREE_CALL, exportCall.getFirstChild().isName());

    Node export = IR.exprResult(exportCall).srcrefTree(node);

    export.insertAfter(node.getParent());
    compiler.reportChangeToEnclosingScope(export);
  }

  /**
   * Whether this is an object literal containing test methods passed to goog.testing.testSuite
   *
   * @param t We only need the scope from this NodeTraversal but want to lazily create the scope.
   */
  private static boolean isTestSuiteArgument(Node n, NodeTraversal t) {
    return n.isObjectLit()
        && n.getParent().isCall()
        && n.isSecondChildOf(n.getParent())
        && isGoogTestingTestSuite(t, n.getPrevious());
  }

  /**
   * Whether this node is a reference to goog.testing.testSuite, either through a goog.require or by
   * its fully qualified name.
   *
   * <p>If this becomes more broadly useful consider branching it into its own class and making more
   * robust to handle forwardDeclares, destructuring requires, etc.
   */
  private static boolean isGoogTestingTestSuite(NodeTraversal t, Node qname) {
    qname = NodeUtil.getCallTargetResolvingIndirectCalls(qname.getParent());
    if (!qname.isQualifiedName()) {
      return false;
    }
    Node root = NodeUtil.getRootOfQualifiedName(qname);
    String rootName = root.getString();
    Var rootVar = t.getScope().getSlot(rootName);
    if (rootVar == null || rootVar.isGlobal()) {
      return qname.matchesQualifiedName(GOOG_TESTING_TEST_SUITE);
    } else if (rootVar.getScope().isModuleScope()) {
      Node originalValue = rootVar.getInitialValue();
      return originalValue != null
          && originalValue.isCall()
          && originalValue.getFirstChild().matchesQualifiedName("goog.require")
          && originalValue.hasTwoChildren()
          && originalValue.getSecondChild().getString().equals(GOOG_TESTING_TEST_SUITE);
    }
    return false;
  }

  /**
   * Whether a function is recognized as a test function. We follow the JsUnit
   * convention for naming (functions should start with "test"), and we also
   * check if it has no parameters declared.
   *
   * @param functionName The name of the function
   * @return {@code true} if the function is recognized as a test function.
   */
  public static boolean isTestFunction(String functionName) {
    return functionName != null
        && TEST_FUNCTIONS_NAME_PATTERN.matcher(functionName).matches();
  }
}
