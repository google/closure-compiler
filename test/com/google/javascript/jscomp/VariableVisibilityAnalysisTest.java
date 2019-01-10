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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallbackInterface;
import com.google.javascript.jscomp.VariableVisibilityAnalysis.VariableVisibility;
import com.google.javascript.rhino.Node;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests of {@link VariableVisibilityAnalysis}.
 *
 * @author dcc@google.com (Devin Coughlin)
 */
@RunWith(JUnit4.class)
public final class VariableVisibilityAnalysisTest extends CompilerTestCase {
  private VariableVisibilityAnalysis lastAnalysis;

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    lastAnalysis = new VariableVisibilityAnalysis(compiler);

    return lastAnalysis;
  }

  @Test
  public void testCapturedVariables() {
    String source =
        "global:var global;\n" +
        "function Outer() {\n" +
        "  captured:var captured;\n" +
        "  notcaptured:var notCaptured;\n" +
        "  function Inner() {\n" +
        "    alert(captured);" +
        "   }\n" +
        "}\n";

    analyze(source);

    assertIsCapturedLocal("captured");
    assertIsUncapturedLocal("notcaptured");
  }

  @Test
  public void testGlobals() {
    String source =
      "global:var global;";

    analyze(source);

    assertIsGlobal("global");
  }

  @Test
  public void testParameters() {
    String source =
      "function A(a,b,c) {\n" +
      "}\n";

    analyze(source);

    assertIsParameter("a");
    assertIsParameter("b");
    assertIsParameter("c");
  }

  @Test
  public void testFunctions() {
    String source =
        "function global() {\n" +
        "  function inner() {\n" +
        "  }\n" +
        "  function innerCaptured() {\n" +
        "    (function(){innerCaptured()})()\n" +
        "  }\n" +
        "}\n";

    analyze(source);

    assertFunctionHasVisibility("global",
        VariableVisibility.GLOBAL);

    assertFunctionHasVisibility("inner",
        VariableVisibility.LOCAL);

    assertFunctionHasVisibility("innerCaptured",
        VariableVisibility.CAPTURED_LOCAL);
  }

  private void assertFunctionHasVisibility(String functionName,
      VariableVisibility visibility) {

    Node functionNode = searchForFunction(functionName);
    assertThat(functionNode).isNotNull();

    Node nameNode = functionNode.getFirstChild();
    assertThat(lastAnalysis.getVariableVisibility(nameNode)).isEqualTo(visibility);
  }

  private void assertLabeledVariableHasVisibility(String label,
      VariableVisibility visibility) {
    Node labeledVariable = searchLabel(label);

    checkState(labeledVariable.isVar());

    // VAR
    //   NAME
    Node nameNode = labeledVariable.getFirstChild();

    assertThat(lastAnalysis.getVariableVisibility(nameNode)).isEqualTo(visibility);
  }

  private void assertIsCapturedLocal(String label) {
    assertLabeledVariableHasVisibility(label,
        VariableVisibility.CAPTURED_LOCAL);
  }

  private void assertIsUncapturedLocal(String label) {
    assertLabeledVariableHasVisibility(label,
        VariableVisibility.LOCAL);
  }

  private void assertIsGlobal(String label) {
    assertLabeledVariableHasVisibility(label,
        VariableVisibility.GLOBAL);
  }

  private void assertIsParameter(String parameterName) {
    Node parameterNode = searchForParameter(parameterName);

    assertThat(parameterNode).isNotNull();

    assertThat(lastAnalysis.getVariableVisibility(parameterNode))
        .isEqualTo(VariableVisibility.PARAMETER);
  }

  private VariableVisibilityAnalysis analyze(String src) {
    testSame(src);

    return lastAnalysis;
  }

  /*
   * Finds a parameter NAME node with the given name in the source AST.
   *
   * Behavior is undefined if there are multiple parameters with
   * parameterName.
   */
  private Node searchForParameter(final String parameterName) {
    checkArgument(parameterName != null);

    final Node[] foundNode = new Node[1];

    AbstractPostOrderCallbackInterface findParameter =
        (NodeTraversal t, Node n, Node parent) -> {
          if (n.getParent().isParamList() && parameterName.equals(n.getString())) {
            foundNode[0] = n;
          }
        };

    NodeTraversal.traversePostOrder(getLastCompiler(), getLastCompiler().jsRoot, findParameter);

    return foundNode[0];
  }

  /*
   * Finds a function node with the given name in the source AST.
   *
   * Behavior is undefined if there are multiple functions with
   * parameterName.
   */
  private Node searchForFunction(final String functionName) {
    checkArgument(functionName != null);

    final Node[] foundNode = new Node[1];

    AbstractPostOrderCallbackInterface findFunction =
        (NodeTraversal t, Node n, Node parent) -> {
          if (n.isFunction() && functionName.equals(NodeUtil.getName(n))) {
            foundNode[0] = n;
          }
        };

    NodeTraversal.traversePostOrder(getLastCompiler(), getLastCompiler().jsRoot, findFunction);

    return foundNode[0];
  }

  // Shamelessly stolen from NameReferenceGraphConstructionTest
  private Node searchLabel(String label) {
    LabeledVariableSearcher s = new LabeledVariableSearcher(label);

    NodeTraversal.traverse(getLastCompiler(), getLastCompiler().jsRoot, s);
    assertWithMessage("Label " + label + " should be in the source code").that(s.found).isNotNull();

    return s.found;
  }

  /**
   * Quick traversal to find a given labeled variable in the AST.
   *
   * Finds the variable for foo in:
   * foo: var a = ...
   */
  private static class LabeledVariableSearcher extends AbstractPostOrderCallback {
    Node found = null;
    final String target;

    LabeledVariableSearcher(String target) {
      this.target = target;
    }
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isLabel() && target.equals(n.getFirstChild().getString())) {

        // LABEL
        //     VAR
        //       NAME

        found = n.getLastChild();
      }
    }
  }
}
