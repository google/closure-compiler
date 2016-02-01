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
import com.google.javascript.jscomp.parsing.parser.util.format.SimpleFormat;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

import java.util.regex.Pattern;

/**
 * Generates goog.exportSymbol for test functions, so they can be recognized
 * by the test runner, even if the code is compiled.
 *
 */
public class ExportTestFunctions implements CompilerPass {

  private static final Pattern TEST_FUNCTIONS_NAME_PATTERN =
      Pattern.compile(
          "^(?:((\\w+\\.)+prototype\\.)*" +
          "(setUpPage|setUp|shouldRunTests|tearDown|tearDownPage|test[\\w\\$]+))$");

  private AbstractCompiler compiler;
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

    Preconditions.checkNotNull(compiler);
    this.compiler = compiler;
    this.exportSymbolFunction = exportSymbolFunction;
    this.exportPropertyFunction = exportPropertyFunction;
  }

  private class ExportTestFunctionsNodes extends
      NodeTraversal.AbstractShallowCallback {

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {

      if (parent == null) {
        return;
      }

      if (parent.isScript()) {
        if (NodeUtil.isFunctionDeclaration(n)) {
          // Check for a test function statement.
          String functionName = NodeUtil.getName(n);
          if (isTestFunction(functionName)) {
            exportTestFunctionAsSymbol(functionName, n, parent);
          }
        } else if (isNameDeclaredFunction(n)) {
          // Check for a test function expression.
          Node functionNode = n.getFirstFirstChild();
          String functionName = NodeUtil.getName(functionNode);
          if (isTestFunction(functionName)) {
            exportTestFunctionAsSymbol(functionName, n, parent);
          }
        }
      } else if (NodeUtil.isExprAssign(parent) &&
            !n.getLastChild().isAssign()) {
        // Check for a test method assignment.
        Node grandparent = parent.getParent();
        if (grandparent != null && grandparent.isScript()) {
          String functionName = n.getFirstChild().getQualifiedName();
          if (isTestFunction(functionName)) {
            exportTestFunctionAsProperty(functionName, parent, n, grandparent);
          }
        }
      } else if (n.isObjectLit()
          && isCallTargetQName(n.getParent(), "goog.testing.testSuite")) {
        for (Node c : n.children()) {
          if (c.isStringKey() && !c.isQuotedString()) {
            c.setQuotedString();
            compiler.reportCodeChange();
          } else if (c.isMemberFunctionDef()) {
            rewriteMemberDefInObjLit(c, n);
          }
        }
      }
    }

    private void rewriteMemberDefInObjLit(Node memberDef, Node objLit) {
      String name = memberDef.getString();
      Node stringKey = IR.stringKey(name, memberDef.getFirstChild().detachFromParent());
      objLit.replaceChild(memberDef, stringKey);
      stringKey.setQuotedString();
      compiler.reportCodeChange();
    }

    // TODO(johnlenz): move test suite declaration into the
    // coding convention class.
    private boolean isCallTargetQName(Node n, String qname) {
      return (n.isCall() && n.getFirstChild().matchesQualifiedName(qname));
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
      if (!NodeUtil.isNameDeclaration(node)) {
        return false;
      }
      Node grandchild = node.getFirstFirstChild();
      return grandchild != null && grandchild.isFunction();
    }
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, root, new ExportTestFunctionsNodes());
  }

  // Adds exportSymbol(testFunctionName, testFunction);
  private void exportTestFunctionAsSymbol(String testFunctionName, Node node,
      Node scriptNode) {

    Node exportCallTarget = NodeUtil.newQName(compiler,
        exportSymbolFunction, node, testFunctionName);
    Node call = IR.call(exportCallTarget);
    if (exportCallTarget.isName()) {
      call.putBooleanProp(Node.FREE_CALL, true);
    }
    call.addChildToBack(IR.string(testFunctionName));
    call.addChildToBack(NodeUtil.newQName(compiler,
        testFunctionName, node, testFunctionName));

    Node expression = IR.exprResult(call);

    scriptNode.addChildAfter(expression, node);
    compiler.reportCodeChange();
  }


  // Adds exportProperty() of the test function name on the prototype object
  private void exportTestFunctionAsProperty(String fullyQualifiedFunctionName,
      Node parent, Node node, Node scriptNode) {

    String testFunctionName =
        NodeUtil.getPrototypePropertyName(node.getFirstChild());
    String objectName = fullyQualifiedFunctionName.substring(0,
        fullyQualifiedFunctionName.lastIndexOf('.'));
    String exportCallStr = SimpleFormat.format("%s(%s, '%s', %s);",
        exportPropertyFunction, objectName, testFunctionName,
        fullyQualifiedFunctionName);

    Node exportCall = this.compiler.parseSyntheticCode(exportCallStr)
        .removeChildren();
    exportCall.useSourceInfoFromForTree(scriptNode);

    scriptNode.addChildAfter(exportCall, parent);
    compiler.reportCodeChange();
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
