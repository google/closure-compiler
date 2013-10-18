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

//import static org.junit.Assert.*;

import com.google.javascript.rhino.Node;

import junit.framework.TestCase;

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

//  @Test
//  public void testGenerateLiteral() {
//    Random random = new Random(System.currentTimeMillis());
//    Fuzzer fuzzer = spy(new Fuzzer(random));
//    int budget = 0;
//    fuzzer.generateLiteral(budget);
//    verify(fuzzer, never()).generateNullLiteral(budget);
//    verify(fuzzer, never()).generateBooleanLiteral(budget);
//    verify(fuzzer, never()).generateNumericLiteral(budget);
//    verify(fuzzer, never()).generateStringLiteral(budget);
//    verify(fuzzer, never()).generateArrayLiteral(budget);
//    verify(fuzzer, never()).generateObjectLiteral(budget);
//    budget = 1;
//    fuzzer.generateLiteral(budget);
//    verify(fuzzer, never()).generateRegularExpressionLiteral(budget);
//  }

  public void testPostfixExpressions() {
    int[] overriddenValues = {0, 1};
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
    int[] overriddenValues = {2, 3, 4, 5, 6, 7, 8, 9, 10};
    String[] prefixes = {"delete ", "void", "typeof", "++", "--", "+", "-",
        "~", "!"};
    for (int i = 0; i < prefixes.length; i++) {
      ControlledRandom random = new ControlledRandom();
      random.addOverride(1, overriddenValues[i]);
      Fuzzer fuzzer = new Fuzzer(random);
      Node node = fuzzer.generateUnaryExpression(10);
      String code = Fuzzer.getPrettyCode(node);
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

  public void testBinaryExpressions() {
    String[] operators = {"*", "/", "%", "+", "-", "<<", ">>", ">>>", "<", ">",
        "<=", ">=", "instanceof", "in", "==", "!=", "===", "!==", "&", "^",
        "|", "&&", "||", "=", "*=", "/=", "%=", "+=", "-=", "<<=", ">>=",
        ">>>=", "&=", "^=", "|="};
    for (int i = 0; i < operators.length; i++) {
      ControlledRandom random = new ControlledRandom();
      random.addOverride(1, i);
      Fuzzer fuzzer = new Fuzzer(random);
      Node node = fuzzer.generateBinaryExpression(3);
      String code = Fuzzer.getPrettyCode(node);
      assertNotSame(-1, code.indexOf(" " + operators[i] + " "));
    }
  }

  public void testTrinaryExpression() {
    ControlledRandom random = new ControlledRandom();
    Fuzzer fuzzer = new Fuzzer(random);
    Node node = fuzzer.generateTernaryExpression(4);
    String code = Fuzzer.getPrettyCode(node);
    assertNotSame(-1, code.indexOf(" ? "));
    assertTrue(code.indexOf(" : ") > code.indexOf(" ? "));
  }

//  @Test
//  public void testExpression() {
//    Random random = new Random(System.currentTimeMillis());
//    Fuzzer fuzzer = spy(new Fuzzer(random));
//    int budget = 1;
//    fuzzer.generateExpression(budget);
//    verify(fuzzer).generateLiteral(budget);
//    verify(fuzzer, never()).generateCallableExpression(budget);
//    verify(fuzzer, never()).generateFunctionCall(budget);
//    verify(fuzzer, never()).generateUnaryExpression(budget);
//    budget = 2;
//    fuzzer.generateExpression(budget);
//    verify(fuzzer, never()).generateBinaryExpression(budget);
//    budget = 3;
//    verify(fuzzer, never()).generateTernaryExpression(budget);
//  }
}
