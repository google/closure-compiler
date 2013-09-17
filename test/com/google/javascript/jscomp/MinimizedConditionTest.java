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

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.MinimizedCondition.MinimizationStyle;
import com.google.javascript.rhino.Node;

import junit.framework.TestCase;

import java.util.List;

/**
 * Tests for {@link MinimizedCondition} in isolation.
 * Tests for the containing PeepholeMinimizeConditions pass are in
 * {@link PeepholeMinimizeConditionsTest}.
 *
 * @author blickly@google.com (Ben Lickly)
 */
public class MinimizedConditionTest extends TestCase {

  private static Node parseExpr(String code) {
    Compiler compiler = new Compiler();
    List<SourceFile> input =
        Lists.newArrayList(SourceFile.fromCode("code", code));
    List<SourceFile> externs = Lists.newArrayList();
    compiler.init(externs, input, new CompilerOptions());
    Node root = compiler.parseInputs();
    assertTrue("Unexpected parse error(s): " +
        Joiner.on("\n").join(compiler.getErrors()), root != null);
    Node externsRoot = root.getFirstChild();
    Node mainRoot = externsRoot.getNext();
    Node script = mainRoot.getFirstChild();
    Node exprResult = script.getFirstChild();
    return exprResult.getFirstChild();
  }

  private static void minCond(String input, String positive, String negative) {
    Node inputNode = parseExpr(input);
    MinimizedCondition result = MinimizedCondition.fromConditionNode(inputNode);
    Node positiveNode = parseExpr(positive);
    Node negativeNode = parseExpr(negative);
    // With counting the leading NOT node:
    Node positiveResult =
        result.getMinimized(MinimizationStyle.PREFER_UNNEGATED).getNode();
    // Without counting the leading NOT node:
    Node negativeResult =
        result.getMinimized(MinimizationStyle.ALLOW_LEADING_NOT).getNode();
    if (!positiveResult.isEquivalentTo(positiveNode)) {
      fail("Not equal:\n" + positiveResult.toStringTree()
          + "and:\n" + positiveNode.toStringTree());
    }
    if (!negativeResult.isEquivalentTo(negativeNode)) {
      fail("Not equal:\n" + negativeResult.toStringTree()
          + "and:\n" + negativeNode.toStringTree());
    }
  }

  public void testTryMinimizeCondSimple() {
    minCond("x", "x", "x");
    minCond("!x", "!x", "!x");
    minCond("!!x", "x", "x");
    minCond("!(x && y)", "!x || !y", "!(x && y)");
  }

  public void testMinimizeDemorganSimple() {
    minCond("!(x&&y)", "!x||!y", "!(x&&y)");
    minCond("!(x||y)", "!x&&!y", "!(x||y)");
    minCond("!x||!y", "!x||!y", "!(x&&y)");
    minCond("!x&&!y", "!x&&!y", "!(x||y)");
    minCond("!(x && y && z)", "!(x && y && z)", "!(x && y && z)");
    minCond("(!a||!b)&&c", "(!a||!b)&&c", "!(a&&b||!c)");
    minCond("(!a||!b)&&(c||d)", "!(a&&b||!c&&!d)", "!(a&&b||!c&&!d)");
  }

  public void testMinimizeBug8494751() {
    minCond(
        "x && (y===2 || !f()) && (y===3 || !h())",
        "x && !((y!==2 && f()) || (y!==3 && h()))",
        "!(!x || (y!==2 && f()) || (y!==3 && h()))");
  }

  public void testMinimizeComplementableOperator() {
    minCond(
        "0===c && (2===a || 1===a)",
        "0===c && (2===a || 1===a)",
        "!(0!==c || 2!==a && 1!==a)");
  }

  public void testMinimizeHook() {
    minCond("!(x ? y : z)", "(x ? !y : !z)",  "!(x ? y : z)");
  }

  public void testMinimizeComma() {
    minCond("!(inc(), test())", "inc(), !test()", "!(inc(), test())");
    minCond("!((x,y)&&z)", "(x,!y)||!z", "!((x,y)&&z)");
  }

}
