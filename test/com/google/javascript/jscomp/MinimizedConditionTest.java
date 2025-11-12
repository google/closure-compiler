/*
 * Copyright 2013 The Closure Compiler Authors.
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
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.MinimizedCondition.MinimizationStyle;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link MinimizedCondition} in isolation. Tests for the containing
 * PeepholeMinimizeConditions pass are in {@link PeepholeMinimizeConditionsTest}.
 */
@RunWith(JUnit4.class)
public final class MinimizedConditionTest {

  private static Node parseExpr(String code) {
    Compiler compiler = new Compiler();
    ImmutableList<SourceFile> input = ImmutableList.of(SourceFile.fromCode("code", code));
    List<SourceFile> externs = new ArrayList<>();
    CompilerOptions options = new CompilerOptions();
    compiler.init(externs, input, options);
    Node root = compiler.parseInputs();
    assertWithMessage("Unexpected parse error(s): %s", Joiner.on("\n").join(compiler.getErrors()))
        .that(root)
        .isNotNull();
    Node externsRoot = root.getFirstChild();
    Node mainRoot = externsRoot.getNext();
    Node script = mainRoot.getFirstChild();
    Node exprResult = script.getFirstChild();
    return exprResult.getFirstChild();
  }

  private static Node cloneAttachedTree(Node n) {
    checkState(n.getParent().getFirstChild() == n);
    return n.getParent().cloneTree().getFirstChild();
  }

  /**
   * Tests minimization of input condition.
   *
   * @param input input code containing a condition
   * @param positive the representation expected to be produced when penalizing a leading NOT
   * @param negative the representation expected to be produced when not penalizing a leading NOT
   */
  private static void minCond(String input, String positive, String negative) {
    Node inputNode = parseExpr(input);
    MinimizedCondition result1 = MinimizedCondition.fromConditionNode(cloneAttachedTree(inputNode));
    MinimizedCondition result2 = MinimizedCondition.fromConditionNode(cloneAttachedTree(inputNode));
    Node positiveNode = parseExpr(positive);
    Node negativeNode = parseExpr(negative);
    // With counting the leading NOT node:
    Node positiveResult =
        result1.getMinimized(MinimizationStyle.PREFER_UNNEGATED).buildReplacement();
    // Without counting the leading NOT node:
    Node negativeResult =
        result2.getMinimized(MinimizationStyle.ALLOW_LEADING_NOT).buildReplacement();
    if (!positiveResult.isEquivalentTo(positiveNode)) {
      assertWithMessage(
              "Not equal:" + "\nExpected: %s\nBut was : %s\nExpected tree:\n%s\nActual tree:\n%s",
              positive,
              new Compiler().toSource(positiveResult),
              positiveNode.toStringTree(),
              positiveResult.toStringTree())
          .fail();
    }
    if (!negativeResult.isEquivalentTo(negativeNode)) {
      assertWithMessage(
              "Not equal:" + "\nExpected: %s\nBut was : %s\nExpected tree:\n%s\nActual tree:\n%s",
              negative,
              new Compiler().toSource(negativeResult),
              negativeNode.toStringTree(),
              negativeResult.toStringTree())
          .fail();
    }
  }

  @Test
  public void testTryMinimizeCondSimple() {
    minCond("x", "x", "x");
    minCond("!x", "!x", "!x");
    minCond("!!x", "x", "x");
    minCond("!(x && y)", "!x || !y", "!(x && y)");
  }

  @Test
  public void testMinimizeDemorganSimple() {
    minCond("!(x&&y)", "!x||!y", "!(x&&y)");
    minCond("!(x||y)", "!x&&!y", "!(x||y)");
    minCond("!x||!y", "!x||!y", "!(x&&y)");
    minCond("!x&&!y", "!x&&!y", "!(x||y)");
    minCond("!(x && y && z)", "!(x && y && z)", "!(x && y && z)");
    minCond("(!a||!b)&&c", "(!a||!b)&&c", "!(a&&b||!c)");
    minCond("(!a||!b)&&(c||d)", "!(a&&b||!c&&!d)", "!(a&&b||!c&&!d)");
  }

  @Test
  public void testMinimizeBug8494751() {
    minCond(
        "x && (y===2 || !f()) && (y===3 || !h())",
        // TODO(tbreisacher): The 'positive' option could be better:
        // "x && !((y!==2 && f()) || (y!==3 && h()))",
        "!(!x || (y!==2 && f()) || (y!==3 && h()))",
        "!(!x || (y!==2 && f()) || (y!==3 && h()))");

    minCond(
        "x && (y===2 || !f?.()) && (y===3 || !h?.())",
        "!(!x || (y!==2 && f?.()) || (y!==3 && h?.()))",
        "!(!x || (y!==2 && f?.()) || (y!==3 && h?.()))");
  }

  @Test
  public void testMinimizeComplementableOperator() {
    minCond("0===c && (2===a || 1===a)", "0===c && (2===a || 1===a)", "!(0!==c || 2!==a && 1!==a)");
  }

  @Test
  public void testMinimizeHook() {
    minCond("!(x ? y : z)", "(x ? !y : !z)", "!(x ? y : z)");
  }

  @Test
  public void testMinimizeComma() {
    minCond("!(inc(), test())", "inc(), !test()", "!(inc(), test())");
    minCond("!(inc?.(), test?.())", "inc?.(), !test?.()", "!(inc?.(), test?.())");
    minCond("!((x,y)&&z)", "(x,!y)||!z", "!((x,y)&&z)");
  }
}
