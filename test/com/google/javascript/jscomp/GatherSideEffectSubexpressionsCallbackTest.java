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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.GatherSideEffectSubexpressionsCallback.GetReplacementSideEffectSubexpressions;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import junit.framework.TestCase;

import java.util.List;

/**
 * Tests for {@link GatherSideEffectSubexpressionsCallback}
 *
 */
public class GatherSideEffectSubexpressionsCallbackTest extends TestCase {
  public void testAndOr() throws Exception {
    Node andNode = getSideEffectsAndNode();
    checkKeepSimplifiedShortCircuitExpr(andNode,
                                        ImmutableList.of("foo&&(bar=0)"));
  }

  public void testIllegalArgumentIfNotAndOr() throws Exception {
    Node nameNode = Node.newString(Token.NAME, "foo");
    try {
      checkKeepSimplifiedShortCircuitExpr(nameNode,
                                          ImmutableList.<String>of());
      fail("Expected exception");
    } catch (IllegalArgumentException e) {
      // ignore
    }
  }

  public void testIllegalArgumentIfNoSideEffectAndOr() throws Exception {
    Node andNode = getNoSideEffectsAndNode();
    try {
      checkKeepSimplifiedShortCircuitExpr(andNode,
                                          ImmutableList.<String>of());
      fail("Expected exception");
    } catch (IllegalArgumentException e) {
      // ignore
    }
  }

  public void testHook() throws Exception {
    Node hook = getSideEffectsHookNode();

    checkKeepSimplifiedHookExpr(hook,
                                true,
                                true,
                                ImmutableList.of("foo?bar=0:baz=0"));
  }

  public void testIllegalArgumentIfNotHook() throws Exception {
    Node nameNode = Node.newString(Token.NAME, "foo");
    try {
      checkKeepSimplifiedHookExpr(nameNode,
                                  true,
                                  true,
                                  ImmutableList.<String>of());
      fail("Expected exception");
    } catch (IllegalArgumentException e) {
      // ignore
    }
  }

  public void testIllegalArgumentIfNoSideEffectHook() throws Exception {
    Node hookNode = getNoSideEffectsHookNode();
    try {
      checkKeepSimplifiedHookExpr(hookNode,
                                  true,
                                  true,
                                  ImmutableList.<String>of());
      fail("Expected exception");
    } catch (IllegalArgumentException e) {
      // ignore
    }
  }

  public void testIllegalArgumentIfHookKeepNeitherBranch() throws Exception {
    Node hookNode = getSideEffectsHookNode();
    try {
      checkKeepSimplifiedHookExpr(hookNode,
                                  false,
                                  false,
                                  ImmutableList.<String>of());
      fail("Expected exception");
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
    List<Node> replacements = Lists.newArrayList();
    GetReplacementSideEffectSubexpressions accumulator =
        new GetReplacementSideEffectSubexpressions(compiler, replacements);
    accumulator.keepSimplifiedShortCircuitExpression(root);

    List<String> actual = Lists.newArrayList();
    for (Node replacement : replacements) {
      actual.add(compiler.toSource(replacement));
    }
    assertEquals(expected, actual);
  }

  private void checkKeepSimplifiedHookExpr(Node root,
                                           boolean thenHasSideEffects,
                                           boolean elseHasSideEffects,
                                           List<String> expected) {
    Compiler compiler = new Compiler();
    List<Node> replacements = Lists.newArrayList();
    GetReplacementSideEffectSubexpressions accumulator =
        new GetReplacementSideEffectSubexpressions(compiler, replacements);
    accumulator.keepSimplifiedHookExpression(root,
                                             thenHasSideEffects,
                                             elseHasSideEffects);
    List<String> actual = Lists.newArrayList();
    for (Node replacement : replacements) {
      actual.add(compiler.toSource(replacement));
    }
    assertEquals(expected, actual);
  }
}
