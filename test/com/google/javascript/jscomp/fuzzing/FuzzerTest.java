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
package com.google.javascript.jscomp.fuzzing;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import junit.framework.TestCase;

import java.util.Random;

/**
 * @author zplin@google.com (Zhongpeng Lin)
 */
public class FuzzerTest extends TestCase{

  public void testGenerateArray() {
    ControlledRandom random = new ControlledRandom();
    int arraySize = 9;
    random.addOverride(1, arraySize);
    Fuzzer fuzzer = new Fuzzer(random);
    Node node = fuzzer.generateArrayLiteral(10);
    String code = Fuzzer.getPrettyCode(node);
    assertTrue(code.startsWith("["));
    assertTrue(code.endsWith("]"));
    assertEquals(arraySize, code.split(",").length);
  }

  public void testGenerateNull() {
    ControlledRandom random = new ControlledRandom();
    Fuzzer fuzzer = new Fuzzer(random);
    Node node = fuzzer.generateNullLiteral(30);
    assertEquals("null", Fuzzer.getPrettyCode(node));
  }

  public void testGenerateTrue() {
    ControlledRandom random = new ControlledRandom();
    random.addOverride(1, 1);
    Fuzzer fuzzer = new Fuzzer(random);
    Node node = fuzzer.generateBooleanLiteral(10);
    assertEquals("true", Fuzzer.getPrettyCode(node));
  }

  public void testGenerateFalse() {
    ControlledRandom random = new ControlledRandom();
    random.addOverride(1, 0);
    Fuzzer fuzzer = new Fuzzer(random);
    Node node = fuzzer.generateBooleanLiteral(10);
    assertEquals("false", Fuzzer.getPrettyCode(node));
  }

  public void testGenerateNumeric() {
    ControlledRandom random = new ControlledRandom();
    Fuzzer fuzzer = new Fuzzer(random);
    Node node = fuzzer.generateNumericLiteral(10);
    String code = Fuzzer.getPrettyCode(node);
    for (int i = 0; i < code.length(); i++) {
      assertTrue(code.charAt(i) >= '0');
      assertTrue(code.charAt(i) <= '9');
    }
  }

  public void testGenerateString() {
    ControlledRandom random = new ControlledRandom();
    Fuzzer fuzzer = new Fuzzer(random);
    Node node = fuzzer.generateStringLiteral(10);
    String code = Fuzzer.getPrettyCode(node);
    assertTrue(code.startsWith("\""));
    assertTrue(code.endsWith("\""));
  }

  public void testGenerateRegex() {
    ControlledRandom random = new ControlledRandom();
    Fuzzer fuzzer = new Fuzzer(random);
    Node node = fuzzer.generateRegularExpressionLiteral(10);
    String code = Fuzzer.getPrettyCode(node);
    assertTrue(code.startsWith("/"));
    assertNotSame('/', code.charAt(1));
  }

  public void testGenerateObjectLiteral() {
    ControlledRandom random = new ControlledRandom();
    Fuzzer fuzzer = new Fuzzer(random);
    Node node = fuzzer.generateObjectLiteral(10);
    String code = Fuzzer.getPrettyCode(node);
    assertTrue(code.startsWith("{"));
    assertTrue(code.endsWith("}"));
  }

  public void testPostfixExpressions() {
    int[] overriddenValues = {9, 10};
    String[] postfixes = {"++", "--"};
    for (int i = 0; i < postfixes.length; i++) {
      ControlledRandom random = new ControlledRandom();
      random.addOverride(1, overriddenValues[i]);
      Fuzzer fuzzer = new Fuzzer(random);
      Node node = fuzzer.generateUnaryExpression(10);
      String code = Fuzzer.getPrettyCode(node);
      assertTrue(code.endsWith(postfixes[i]));
    }
  }

  public void testPrefixExpressions() {
    int[] overriddenValues = {0, 1, 2, 3, 4, 5, 6, 7, 8};
    String[] prefixes = {"void", "typeof", "+", "-", "~", "!", "++", "--", "delete"};
    for (int i = 0; i < prefixes.length; i++) {
      ControlledRandom random = new ControlledRandom();
      random.addOverride(1, overriddenValues[i]);
      Fuzzer fuzzer = new Fuzzer(random);
      Node node = fuzzer.generateUnaryExpression(10);
      String code = Fuzzer.getPrettyCode(node).trim();
      assertTrue(code.startsWith(prefixes[i]));
    }
  }

  public void testNewExpression() {
    ControlledRandom random = new ControlledRandom();
    Fuzzer fuzzer = new Fuzzer(random);
    Node node = fuzzer.generateFunctionCall(10, true);
    String code = Fuzzer.getPrettyCode(node);
    assertTrue(code.startsWith("new "));
  }

  public void testCallExpression() {
    ControlledRandom random = new ControlledRandom();
    Fuzzer fuzzer = new Fuzzer(random);
    Node node = fuzzer.generateFunctionCall(10, false);
    String code = Fuzzer.getPrettyCode(node);
    assertFalse(code.startsWith("new "));
  }

  public void testTrinaryExpression() {
    Random random = new Random();
    Fuzzer fuzzer = new Fuzzer(random);
    Node node = fuzzer.generateTernaryExpression(4);
    String code = Fuzzer.getPrettyCode(node);
    assertNotSame(-1, code.indexOf(" ? "));
    assertTrue(code.indexOf(" : ") > code.indexOf(" ? "));
  }

  public void testVariableStatement() {
    Random random = new Random();
    Fuzzer fuzzer = new Fuzzer(random);
    Node node = fuzzer.generateVariableStatement(10);
    String code = Fuzzer.getPrettyCode(node);
    assertTrue(code.startsWith("var "));
  }

  public void testEmptyStatement() {
    Random random = new Random();
    Fuzzer fuzzer = new Fuzzer(random);
    Node emptyStmt = fuzzer.generateEmptyStatement(10);
    assertEquals(Token.EMPTY, emptyStmt.getType());
  }

  public void testIfStatement() {
    Random random = new Random();
    Fuzzer fuzzer = new Fuzzer(random);
    Node ifStatement = fuzzer.generateIfStatement(10);
    String code = Fuzzer.getPrettyCode(ifStatement);
    assertTrue(code.startsWith("if ("));
  }

  public void testWhileStatement() {
    ControlledRandom random = new ControlledRandom();
    random.addOverride(1, 1);
    Fuzzer fuzzer = new Fuzzer(random);
    Node whileStatement = fuzzer.generateWhile(10);
    String code = Fuzzer.getPrettyCode(whileStatement);
    assertTrue(code.startsWith("while ("));
  }

  public void testDoWhileStatement() {
    ControlledRandom random = new ControlledRandom();
    random.addOverride(1, 0);
    Fuzzer fuzzer = new Fuzzer(random);
    Node doStatement = fuzzer.generateDoWhile(10);
    String code = Fuzzer.getPrettyCode(doStatement);
    assertTrue(code.startsWith("do {"));
    assertTrue(code.trim().endsWith(");"));

  }

  public void testForStatement() {
    ControlledRandom random = new ControlledRandom();
    random.addOverride(1, 0);
    Fuzzer fuzzer = new Fuzzer(random);
    Node forStatement = fuzzer.generateFor(10);
    String code = Fuzzer.getPrettyCode(forStatement);
    assertTrue(code.startsWith("for ("));
  }

  public void testSwitchStatement() {
    ControlledRandom random = new ControlledRandom();
    Fuzzer fuzzer = new Fuzzer(random);
    Node switchStmt = fuzzer.generateSwitch(20);
    String code = Fuzzer.getPrettyCode(switchStmt);
    assertTrue(code.startsWith("switch("));
  }

  public void testThrowStatement() {
    ControlledRandom random = new ControlledRandom();
    Fuzzer fuzzer = new Fuzzer(random);
    Node throwStatement = fuzzer.generateThrow(10);
    String code = Fuzzer.getPrettyCode(throwStatement);
    assertTrue(code.startsWith("throw"));
  }

  public void testTryStatement() {
    ControlledRandom random = new ControlledRandom();
    Fuzzer fuzzer = new Fuzzer(random);
    Node tryStatement = fuzzer.generateTry(20);
    String code = Fuzzer.getPrettyCode(tryStatement);
    assertTrue(code.startsWith("try {"));
  }

  public void testFunctionDeclaration() {
    ControlledRandom random = new ControlledRandom();
    Fuzzer fuzzer = new Fuzzer(random);
    Node functionDecl = fuzzer.generateFunctionDeclaration(20);
    String code = Fuzzer.getPrettyCode(functionDecl);
    assertTrue(code.startsWith("function "));
  }

  public void testBreakStatement() {
    ControlledRandom random = new ControlledRandom();
    random.addOverride(1, 0);
    Fuzzer fuzzer = new Fuzzer(random);
    fuzzer.currentLabels.push("testLabel");
    Node breakStmt = fuzzer.generateBreak(10);
    String code = Fuzzer.getPrettyCode(breakStmt);
    assertEquals("break testLabel;", code.trim());
  }

  public void testContinueStatement() {
    ControlledRandom random = new ControlledRandom();
    random.addOverride(1, 0);
    Fuzzer fuzzer = new Fuzzer(random);
    fuzzer.currentLabels.push("testLabel");
    Node breakStmt = fuzzer.generateContinue(10);
    String code = Fuzzer.getPrettyCode(breakStmt);
    assertEquals("continue testLabel;", code.trim());
  }

}
