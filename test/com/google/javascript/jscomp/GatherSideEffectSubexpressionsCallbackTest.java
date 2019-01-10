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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.GatherSideEffectSubexpressionsCallback.GetReplacementSideEffectSubexpressions;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link GatherSideEffectSubexpressionsCallback}
 *
 */
@RunWith(JUnit4.class)
public final class GatherSideEffectSubexpressionsCallbackTest {
  @Test
  public void testAndOr() {
    Node andNode = getSideEffectsAndNode();
    checkKeepSimplifiedShortCircuitExpr(andNode,
                                        ImmutableList.of("foo&&(bar=0)"));
  }

  @Test
  public void testIllegalArgumentIfNotAndOr() {
    Node nameNode = Node.newString(Token.NAME, "foo");
    try {
      checkKeepSimplifiedShortCircuitExpr(nameNode,
                                          ImmutableList.<String>of());
      assertWithMessage("Expected exception").fail();
    } catch (IllegalArgumentException e) {
      // ignore
    }
  }

  @Test
  public void testIllegalArgumentIfNoSideEffectAndOr() {
    Node andNode = getNoSideEffectsAndNode();
    try {
      checkKeepSimplifiedShortCircuitExpr(andNode,
                                          ImmutableList.<String>of());
      assertWithMessage("Expected exception").fail();
    } catch (IllegalArgumentException e) {
      // ignore
    }
  }

  @Test
  public void testHook() {
    Node hook = getSideEffectsHookNode();

    checkKeepSimplifiedHookExpr(hook,
                                true,
                                true,
                                ImmutableList.of("foo?bar=0:baz=0"));
  }

  @Test
  public void testIllegalArgumentIfNotHook() {
    Node nameNode = Node.newString(Token.NAME, "foo");
    try {
      checkKeepSimplifiedHookExpr(nameNode,
                                  true,
                                  true,
                                  ImmutableList.<String>of());
      assertWithMessage("Expected exception").fail();
    } catch (IllegalArgumentException e) {
      // ignore
    }
  }

  @Test
  public void testIllegalArgumentIfNoSideEffectHook() {
    Node hookNode = getNoSideEffectsHookNode();
    try {
      checkKeepSimplifiedHookExpr(hookNode,
                                  true,
                                  true,
                                  ImmutableList.<String>of());
      assertWithMessage("Expected exception").fail();
    } catch (IllegalArgumentException e) {
      // ignore
    }
  }

  @Test
  public void testIllegalArgumentIfHookKeepNeitherBranch() {
    Node hookNode = getSideEffectsHookNode();
    try {
      checkKeepSimplifiedHookExpr(hookNode,
                                  false,
                                  false,
                                  ImmutableList.<String>of());
      assertWithMessage("Expected exception").fail();
    } catch (IllegalArgumentException e) {
      // ignore
    }
  }

  private Node getNoSideEffectsAndNode() {
    Node andNode = new Node(Token.AND);

    andNode.addChildToBack(Node.newString(Token.NAME, "foo"));
    andNode.addChildToBack(Node.newString(Token.NAME, "bar"));

    return andNode;
  }

  private Node getSideEffectsAndNode() {
    Node andNode = new Node(Token.AND);

    Node assign = new Node(Token.ASSIGN);
    assign.addChildToBack(Node.newString(Token.NAME, "bar"));
    assign.addChildToBack(Node.newNumber(0));

    andNode.addChildToBack(Node.newString(Token.NAME, "foo"));
    andNode.addChildToBack(assign);

    return andNode;
  }

  private Node getNoSideEffectsHookNode() {
    Node hookNode = new Node(Token.HOOK);

    hookNode.addChildToBack(Node.newString(Token.NAME, "foo"));
    hookNode.addChildToBack(Node.newString(Token.NAME, "bar"));
    hookNode.addChildToBack(Node.newString(Token.NAME, "baz"));

    return hookNode;
  }

  private Node getSideEffectsHookNode() {
    Node hookNode = new Node(Token.HOOK);

    Node assign1 = new Node(Token.ASSIGN);
    assign1.addChildToBack(Node.newString(Token.NAME, "bar"));
    assign1.addChildToBack(Node.newNumber(0));

    Node assign2 = new Node(Token.ASSIGN);
    assign2.addChildToBack(Node.newString(Token.NAME, "baz"));
    assign2.addChildToBack(Node.newNumber(0));

    hookNode.addChildToBack(Node.newString(Token.NAME, "foo"));
    hookNode.addChildToBack(assign1);
    hookNode.addChildToBack(assign2);

    return hookNode;
  }

  private void checkKeepSimplifiedShortCircuitExpr(Node root,
                                                   List<String> expected) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setEmitUseStrict(false);
    compiler.initOptions(options);
    List<Node> replacements = new ArrayList<>();
    GetReplacementSideEffectSubexpressions accumulator =
        new GetReplacementSideEffectSubexpressions(compiler, replacements);
    accumulator.keepSimplifiedShortCircuitExpression(root);

    List<String> actual = new ArrayList<>();
    for (Node replacement : replacements) {
      actual.add(compiler.toSource(replacement));
    }
    assertThat(actual).isEqualTo(expected);
  }

  private void checkKeepSimplifiedHookExpr(Node root,
                                           boolean thenHasSideEffects,
                                           boolean elseHasSideEffects,
                                           List<String> expected) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setEmitUseStrict(false);
    compiler.initOptions(options);
    List<Node> replacements = new ArrayList<>();
    GetReplacementSideEffectSubexpressions accumulator =
        new GetReplacementSideEffectSubexpressions(compiler, replacements);
    accumulator.keepSimplifiedHookExpression(root,
                                             thenHasSideEffects,
                                             elseHasSideEffects);
    List<String> actual = new ArrayList<>();
    for (Node replacement : replacements) {
      actual.add(compiler.toSource(replacement));
    }
    assertThat(actual).isEqualTo(expected);
  }
}
