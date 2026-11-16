/*
 * Copyright 2026 The Closure Compiler Authors.
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

import static com.google.common.truth.Truth.assertThat;

import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link BasicBlock}. */
@RunWith(JUnit4.class)
public final class BasicBlockTest {

  private Compiler compiler;

  @Before
  public void setUp() {
    this.compiler = new Compiler();
  }

  @Test
  public void isBlockBoundary_conditionalAndShortCircuitChildren() {
    Node andNode = parseExpr("a && b");
    assertThat(BasicBlock.isBlockBoundary(andNode.getFirstChild(), andNode)).isFalse();
    assertThat(BasicBlock.isBlockBoundary(andNode.getSecondChild(), andNode)).isTrue();

    Node orNode = parseExpr("a || b");
    assertThat(BasicBlock.isBlockBoundary(orNode.getFirstChild(), orNode)).isFalse();
    assertThat(BasicBlock.isBlockBoundary(orNode.getSecondChild(), orNode)).isTrue();

    Node coalesceNode = parseExpr("a ?? b");
    assertThat(BasicBlock.isBlockBoundary(coalesceNode.getFirstChild(), coalesceNode)).isFalse();
    assertThat(BasicBlock.isBlockBoundary(coalesceNode.getSecondChild(), coalesceNode)).isTrue();

    Node hookNode = parseExpr("a ? b : c");
    assertThat(BasicBlock.isBlockBoundary(hookNode.getFirstChild(), hookNode)).isFalse();
    assertThat(BasicBlock.isBlockBoundary(hookNode.getSecondChild(), hookNode)).isTrue();
    assertThat(BasicBlock.isBlockBoundary(hookNode.getLastChild(), hookNode)).isTrue();

    Node assignOrNode = parseExpr("a ||= b");
    assertThat(BasicBlock.isBlockBoundary(assignOrNode.getFirstChild(), assignOrNode)).isFalse();
    assertThat(BasicBlock.isBlockBoundary(assignOrNode.getSecondChild(), assignOrNode)).isTrue();

    Node assignAndNode = parseExpr("a &&= b");
    assertThat(BasicBlock.isBlockBoundary(assignAndNode.getFirstChild(), assignAndNode)).isFalse();
    assertThat(BasicBlock.isBlockBoundary(assignAndNode.getSecondChild(), assignAndNode)).isTrue();

    Node assignCoalesceNode = parseExpr("a ??= b");
    assertThat(BasicBlock.isBlockBoundary(assignCoalesceNode.getFirstChild(), assignCoalesceNode))
        .isFalse();
    assertThat(BasicBlock.isBlockBoundary(assignCoalesceNode.getSecondChild(), assignCoalesceNode))
        .isTrue();

    Node optChainCallNode = parseExpr("a?.(b)");
    assertThat(BasicBlock.isBlockBoundary(optChainCallNode.getFirstChild(), optChainCallNode))
        .isFalse();
    assertThat(BasicBlock.isBlockBoundary(optChainCallNode.getSecondChild(), optChainCallNode))
        .isTrue();
  }

  @Test
  public void isBlockBoundary_controlStatements() {
    Node ifNode = parse("if (a) { b(); } else { c(); }").getFirstChild();
    assertThat(BasicBlock.isBlockBoundary(ifNode.getFirstChild(), ifNode)).isFalse();
    assertThat(BasicBlock.isBlockBoundary(ifNode.getSecondChild(), ifNode)).isTrue();
    assertThat(BasicBlock.isBlockBoundary(ifNode.getLastChild(), ifNode)).isTrue();

    Node forNode = parse("for (let i = 0; i < 10; i++) { body(); }").getFirstChild();
    for (Node child = forNode.getFirstChild(); child != null; child = child.getNext()) {
      assertThat(BasicBlock.isBlockBoundary(child, forNode)).isTrue();
    }

    Node tryNode = parse("try { a(); } catch (e) { b(); } finally { c(); }").getFirstChild();
    for (Node child = tryNode.getFirstChild(); child != null; child = child.getNext()) {
      assertThat(BasicBlock.isBlockBoundary(child, tryNode)).isTrue();
    }

    Node switchNode = parse("switch (a) { case 1: b(); }").getFirstChild();
    Node switchCond = switchNode.getFirstChild();
    Node switchBody = switchNode.getSecondChild();
    Node caseNode = switchBody.getFirstChild();
    assertThat(BasicBlock.isBlockBoundary(switchCond, switchNode)).isFalse();
    assertThat(BasicBlock.isBlockBoundary(caseNode, switchBody)).isTrue();
    assertThat(BasicBlock.isBlockBoundary(caseNode.getFirstChild(), caseNode)).isFalse();

    Node classNode = parse("class C extends Base {}").getFirstChild();
    // All children of a class are conservatively treated as separate blocks.
    for (Node child = classNode.getFirstChild(); child != null; child = child.getNext()) {
      assertThat(BasicBlock.isBlockBoundary(child, classNode)).isTrue();
    }
  }

  @Test
  public void provablyExecutesBefore_blockHierarchy() {
    Node script = parse("if (a) { if (b) {} } else {}");
    Node ifNode = script.getFirstChild();
    Node thenBlockNode = ifNode.getSecondChild();
    Node nestedThenBlockNode = thenBlockNode.getFirstChild().getSecondChild();
    Node elseBlockNode = ifNode.getLastChild();

    BasicBlock rootBlock = new BasicBlock(/* parent= */ null, script);
    BasicBlock thenBlock = new BasicBlock(rootBlock, thenBlockNode);
    BasicBlock nestedThenBlock = new BasicBlock(thenBlock, nestedThenBlockNode);
    BasicBlock elseBlock = new BasicBlock(rootBlock, elseBlockNode);

    assertThat(rootBlock.provablyExecutesBefore(thenBlock)).isTrue();
    assertThat(rootBlock.provablyExecutesBefore(nestedThenBlock)).isTrue();
    assertThat(thenBlock.provablyExecutesBefore(nestedThenBlock)).isTrue();
    assertThat(thenBlock.provablyExecutesBefore(elseBlock)).isFalse();
    assertThat(nestedThenBlock.provablyExecutesBefore(thenBlock)).isFalse();
  }

  private Node parse(String js) {
    Node script = compiler.parseTestCode(js);
    assertThat(compiler.getErrors()).isEmpty();
    return script;
  }

  private Node parseExpr(String jsExpr) {
    return parse("(" + jsExpr + ");").getFirstChild().getFirstChild();
  }
}
