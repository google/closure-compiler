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

import com.google.javascript.jscomp.NodeIterators.FunctionlessLocalScope;
import com.google.javascript.jscomp.NodeIterators.LocalVarMotion;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for NodeIterators. */
@RunWith(JUnit4.class)
public final class NodeIteratorsTest {

  // In each test, we find the declaration of "X" in the local scope,
  // construct a list of all nodes where X is guaranteed to retain its
  // original value, and compare those nodes against an expected list of
  // tokens.

  @Test
  public void testBasic() {
    testVarMotionWithCode("var X = 3;", Token.VAR, Token.SCRIPT);
  }

  @Test
  public void testNamedFunction() {
    testVarMotionWithCode("var X = 3; function f() {}", Token.VAR, Token.SCRIPT);
  }

  @Test
  public void testNamedFunction2() {
    testVarMotionWithCode(
        "var X = 3; function f() {} var Y;", Token.VAR, Token.NAME, Token.VAR, Token.SCRIPT);
  }

  @Test
  public void testFunctionExpression() {
    testVarMotionWithCode(
        "var X = 3, Y = function() {}; 3;",
        Token.NAME,
        Token.VAR,
        Token.NUMBER,
        Token.EXPR_RESULT,
        Token.SCRIPT);
  }

  @Test
  public void testFunctionExpression2() {
    testVarMotionWithCode(
        "var X = 3; var Y = function() {}; 3;",
        Token.VAR,
        Token.NAME,
        Token.VAR,
        Token.NUMBER,
        Token.EXPR_RESULT,
        Token.SCRIPT);
  }

  @Test
  public void testHaltAtVarRef() {
    testVarMotionWithCode(
        "var X, Y = 3; var Z = X;", Token.NUMBER, Token.NAME, Token.VAR, Token.NAME);
  }

  @Test
  public void testHaltAtVarRef2() {
    testVarMotionWithCode(
        "var X, Y = 3; (function() {})(3, X);",
        Token.NUMBER,
        Token.NAME,
        Token.VAR,
        Token.NUMBER,
        Token.NAME);
  }

  @Test
  public void testHaltAtVarRef3() {
    testVarMotionWithCode("var X, Y = 3; X;", Token.NUMBER, Token.NAME, Token.VAR, Token.NAME);
  }

  @Test
  public void testHaltAtSideEffects() {
    testVarMotionWithCode(
        "var X, Y = 3; var Z = B(3);",
        Token.NUMBER,
        Token.NAME,
        Token.VAR,
        Token.NAME,
        Token.NUMBER);
  }

  @Test
  public void testHaltAtSideEffects2() {
    testVarMotionWithCode(
        "var A = 1, X = A, Y = 3; delete A;", Token.NUMBER, Token.NAME, Token.VAR, Token.NAME);
  }

  @Test
  public void testHaltAtSideEffects3() {
    testVarMotionWithCode(
        "var A = 1, X = A, Y = 3; A++;", Token.NUMBER, Token.NAME, Token.VAR, Token.NAME);
  }

  @Test
  public void testHaltAtSideEffects4() {
    testVarMotionWithCode(
        "var A = 1, X = A, Y = 3; A--;", Token.NUMBER, Token.NAME, Token.VAR, Token.NAME);
  }

  @Test
  public void testHaltAtSideEffects5() {
    testVarMotionWithCode(
        "var A = 1, X = A, Y = 3; A = 'a';",
        Token.NUMBER,
        Token.NAME,
        Token.VAR,
        Token.NAME,
        Token.STRINGLIT);
  }

  @Test
  public void testNoHaltReadWhenValueIsImmutable() {
    testVarMotionWithCode(
        "var X = 1, Y = 3; alert();", Token.NUMBER, Token.NAME, Token.VAR, Token.NAME);
  }

  @Test
  public void testHaltReadWhenValueHasSideEffects() {
    testVarMotionWithCode("var X = f(), Y = 3; alert();", Token.NUMBER, Token.NAME, Token.VAR);
  }

  @Test
  public void testCatchBlock() {
    testVarMotionWithCode(
        "var X = 1; try { 4; } catch (X) {}",
        Token.VAR,
        Token.NUMBER,
        Token.EXPR_RESULT,
        Token.BLOCK);
  }

  @Test
  public void testIfBranch() {
    testVarMotionWithCode("var X = foo(); if (X) {}", Token.VAR, Token.NAME);
  }

  @Test
  public void testLet() {
    testVarMotionWithCode("let X = foo(); if (X) {}", Token.LET, Token.NAME);
  }

  @Test
  public void testConst() {
    testVarMotionWithCode("const X = foo(); if (X) {}", Token.CONST, Token.NAME);
  }

  /**
   * Parses the given code, finds the variable X in the global scope, and runs the VarMotion
   * iterator. Asserts that the iteration order matches the tokens given.
   */
  private void testVarMotionWithCode(String code, Token... expectedTokens) {
    List<Token> expectedList = Arrays.asList(expectedTokens);
    testVarMotionWithCode(code, expectedList);
  }

  /**
   * @see #testVarMotionWithCode(String, Token ...)
   */
  private void testVarMotionWithCode(String code, List<Token> expectedTokens) {
    List<Node> ancestors = new ArrayList<>();

    // Add an empty node to the beginning of the code and start there.
    Compiler compiler = new Compiler();
    Node root = compiler.parseTestCode(";" + code);
    for (Node n = root; n != null; n = n.getFirstChild()) {
      ancestors.add(0, n);
    }

    FunctionlessLocalScope searchIt = new FunctionlessLocalScope(ancestors.toArray(new Node[0]));

    boolean found = false;
    while (searchIt.hasNext()) {
      Node n = searchIt.next();
      if (n.isName()
          && NodeUtil.isNameDeclaration(searchIt.currentParent())
          && n.getString().equals("X")) {
        found = true;
        break;
      }
    }

    assertWithMessage("Variable X not found! %s", root.toStringTree()).that(found).isTrue();

    List<Node> currentAncestors = searchIt.currentAncestors();
    assertThat(currentAncestors.size()).isAtLeast(3);
    Iterator<Node> moveIt =
        LocalVarMotion.forVar(
            compiler, currentAncestors.get(0), currentAncestors.get(1), currentAncestors.get(2));
    List<Token> actualTokens = new ArrayList<>();
    while (moveIt.hasNext()) {
      actualTokens.add(moveIt.next().getToken());
    }

    assertThat(actualTokens).isEqualTo(expectedTokens);
  }
}
