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

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import junit.framework.TestCase;

import java.util.Map.Entry;
import java.util.Random;

/**
 * @author zplin@google.com (Zhongpeng Lin)
 */
public class FuzzerTest extends TestCase{
  private FuzzingContext context;

  @Override
  public void setUp() {
    Random random = new Random(123);
    JsonObject config = TestConfig.getConfig();
    context = new FuzzingContext(random, config, true);
  }

  public void testGenerateArray() {
    ArrayFuzzer fuzzer =
        new ArrayFuzzer(context);
    int budget = 10;
    Node node = fuzzer.generate(budget);
    String code = ArrayFuzzer.getPrettyCode(node);
    assertTrue(code.startsWith("["));
    assertTrue(code.endsWith("]"));
    JsonObject config = fuzzer.getOwnConfig();
    assertTrue(
        "\nGenerated code: \n" + code,
        code.split(",").length
            < (int) (config.get("maxLength").getAsDouble()
                * (budget - 1)));
  }

  public void testGenerateNull() {
    SimpleFuzzer fuzzer = new SimpleFuzzer(Token.NULL, "null", Type.OBJECT);
    Node node = fuzzer.generate(30);
    assertEquals("null", SimpleFuzzer.getPrettyCode(node));
  }

  public void testGenerateBoolean() {
    BooleanFuzzer fuzzer = new BooleanFuzzer(context);
    Node node = fuzzer.generate(10);
    String code = BooleanFuzzer.getPrettyCode(node).trim();
    assertTrue(
        "\nGenerated code: \n" + code,
        code.equals("true") || code.equals("false"));
  }
  public void testGenerateNumeric() {
    NumericFuzzer fuzzer = new NumericFuzzer(context);
    Node node = fuzzer.generate(10);
    String code = NumericFuzzer.getPrettyCode(node);
    for (int i = 0; i < code.length(); i++) {
      assertTrue("\nGenerated code: \n" + code, code.charAt(i) >= '0');
      assertTrue("\nGenerated code: \n" + code, code.charAt(i) <= '9');
    }
  }

  public void testGenerateString() {
    StringFuzzer fuzzer = new StringFuzzer(context);
    Node node = fuzzer.generate(10);
    String code = StringFuzzer.getPrettyCode(node);
    assertTrue("\nGenerated code: \n" + code, code.startsWith("\""));
    assertTrue("\nGenerated code: \n" + code, code.endsWith("\""));
  }

  public void testGenerateRegex() {
    RegularExprFuzzer fuzzer = new RegularExprFuzzer(context);
    Node node = fuzzer.generate(10);
    String code = RegularExprFuzzer.getPrettyCode(node);
    assertTrue("\nGenerated code: \n" + code, code.startsWith("/"));
    assertNotSame('/', code.charAt(1));
  }

  public void testGenerateObjectLiteral() {
    ObjectFuzzer fuzzer = new ObjectFuzzer(context);
    Node node = fuzzer.generate(10);
    String code = ObjectFuzzer.getPrettyCode(node);
    assertTrue("\nGenerated code: \n" + code, code.startsWith("{"));
    assertTrue("\nGenerated code: \n" + code, code.endsWith("}"));
  }

  public void testGenerateLiteral() throws JsonParseException {
    int budget = 0;
    LiteralFuzzer literalFuzzer = new LiteralFuzzer(context);
    LiteralFuzzer spyFuzzer = spy(literalFuzzer);
    doThrow(new RuntimeException("Not enough budget for literal")).
    when(spyFuzzer).generate(budget);
    budget = 1;
    leaveOneSubtype(literalFuzzer.getOwnConfig(), "null");
    Node node = literalFuzzer.generate(budget);
    String code = AbstractFuzzer.getPrettyCode(node);
    assertEquals("null", code.trim());
}

  public void testPostfixExpressions() throws JsonParseException {
    String[] postfixes = {"++", "--"};
    String[] types = {"postInc", "postDec"};
    for (int i = 0; i < postfixes.length; i++) {
      setUp();
      UnaryExprFuzzer fuzzer = new UnaryExprFuzzer(context);
      leaveOneSubtype(fuzzer.getOwnConfig(), types[i]);
      Node node = fuzzer.generate(10);
      String code = UnaryExprFuzzer.getPrettyCode(node);
      assertTrue(code.endsWith(postfixes[i]));
    }
  }

  public void testPrefixExpressions() throws JsonParseException {
    String[] prefixes =
      {"void", "typeof", "+", "-", "~", "!", "++", "--", "delete"};
    String[] types = {"void", "typeof", "pos", "neg", "bitNot", "not", "inc",
        "dec", "delProp"};
    for (int i = 0; i < prefixes.length; i++) {
      setUp();
      UnaryExprFuzzer fuzzer = new UnaryExprFuzzer(context);
      leaveOneSubtype(fuzzer.getOwnConfig(), types[i]);
      Node node = fuzzer.generate(10);
      String code = UnaryExprFuzzer.getPrettyCode(node);
      assertTrue(code.startsWith(prefixes[i]));
    }
  }

  public void testNewExpression() throws JsonParseException {
    FunctionCallFuzzer fuzzer =
        new FunctionCallFuzzer(context);
    leaveOneSubtype(fuzzer.getOwnConfig(), "constructorCall");
    Node node = fuzzer.generate(10);
    String code = FunctionCallFuzzer.getPrettyCode(node);
    assertTrue(code.startsWith("new "));
  }

  public void testCallExpression() throws JsonParseException {
    FunctionCallFuzzer fuzzer =
        new FunctionCallFuzzer(context);
    leaveOneSubtype(fuzzer.getOwnConfig(), "normalCall");
    Node node = fuzzer.generate(10);
    String code = FunctionCallFuzzer.getPrettyCode(node);
    assertFalse(code.startsWith("new "));
  }

  public void testGenerateBinaryExpression() throws JsonParseException {
    int budget = 50;
    String[] operators = {"*", "/", "%", "+", "-", "<<", ">>", ">>>", "<", ">",
        "<=", ">=", "instanceof", "in", "==", "!=", "===", "!==", "&", "^",
        "|", "&&", "||", "=", "*=", "/=", "%=", "+=", "-=", "<<=", ">>=",
        ">>>=", "&=", "^=", "|="};
    String[] types = {"mul", "div", "mod", "add", "sub", "lsh", "rsh", "ursh",
        "lt", "gt", "le", "ge", "instanceof", "in", "eq", "ne", "sheq", "shne",
        "bitAnd", "bitXor", "bitOr", "and", "or", "assign", "assignMul",
        "assignDiv", "assignMod", "assignAdd", "assignSub", "assignLsh",
        "assignRsh", "assignUrsh", "assignBitAnd", "assignBitXor",
        "assignBitOr"};
    for (int i = 0; i < operators.length; i++) {
      context =
          new FuzzingContext(new Random(123), TestConfig.getConfig(), true);
      BinaryExprFuzzer fuzzer =
          new BinaryExprFuzzer(context);
      leaveOneSubtype(fuzzer.getOwnConfig(), types[i]);
      Node node = fuzzer.generate(budget);
      String code = BinaryExprFuzzer.getPrettyCode(node).trim();
      assertNotSame(-1, code.indexOf(" " + operators[i] + " "));
    }
  }

  public void testTrinaryExpression() {
    TernaryExprFuzzer fuzzer =
        new TernaryExprFuzzer(context);
    Node node = fuzzer.generate(4);
    String code = TernaryExprFuzzer.getPrettyCode(node);
    assertNotSame(-1, code.indexOf(" ? "));
    assertTrue(code.indexOf(" : ") > code.indexOf(" ? "));
  }

  public void testVariableStatement() {
    VarFuzzer fuzzer = new VarFuzzer(context);
    Node node = fuzzer.generate(10);
    String code = VarFuzzer.getPrettyCode(node);
    assertTrue(code.startsWith("var "));
  }

  public void testEmptyStatement() {
    SimpleFuzzer fuzzer = new SimpleFuzzer(Token.EMPTY, "empty", Type.UNDEFINED);
    Node emptyStmt = fuzzer.generate(10);
    assertEquals(Token.EMPTY, emptyStmt.getType());
  }

  public void testIfStatement() {
    IfFuzzer fuzzer = new IfFuzzer(context);
    Node ifStatement = fuzzer.generate(10);
    String code = IfFuzzer.getPrettyCode(ifStatement);
    assertTrue(code.startsWith("if ("));
  }

  public void testWhileStatement() {
    WhileFuzzer fuzzer =
        new WhileFuzzer(context);
    Node whileStatement = fuzzer.generate(10);
    String code = WhileFuzzer.getPrettyCode(whileStatement);
    assertTrue(code.startsWith("while ("));
  }

  public void testDoWhileStatement() {
    DoWhileFuzzer fuzzer =
        new DoWhileFuzzer(context);
    Node doStatement = fuzzer.generate(10);
    String code = DoWhileFuzzer.getPrettyCode(doStatement);
    assertTrue(code.startsWith("do {"));
    assertTrue(code.trim().endsWith(");"));

  }

  public void testForStatement() {
    ForFuzzer fuzzer = new ForFuzzer(context);
    Node forStatement = fuzzer.generate(10);
    String code = ForFuzzer.getPrettyCode(forStatement);
    assertTrue(code.startsWith("for ("));
  }

  public void testForInStatement() {
    ForInFuzzer fuzzer =
        new ForInFuzzer(context);
    Node forInStatement = fuzzer.generate(10);
    String code = ForInFuzzer.getPrettyCode(forInStatement);
    assertTrue("\nGenerated code: \n" + code, code.startsWith("for ("));
    assertTrue("\nGenerated code: \n" + code, code.contains(" in "));
  }

  public void testSwitchStatement() {
    SwitchFuzzer fuzzer =
        new SwitchFuzzer(context);
    Node switchStmt = fuzzer.generate(20);
    String code = SwitchFuzzer.getPrettyCode(switchStmt);
    assertTrue("\nGenerated code: \n" + code, code.startsWith("switch("));
  }

  public void testThrowStatement() {
    ThrowFuzzer fuzzer =
        new ThrowFuzzer(context);
    Node throwStatement = fuzzer.generate(10);
    String code = ThrowFuzzer.getPrettyCode(throwStatement);
    assertTrue("\nGenerated code: \n" + code, code.startsWith("throw"));
  }

  public void testTryStatement() {
    TryFuzzer fuzzer = new TryFuzzer(context);
    Node tryStatement = fuzzer.generate(20);
    String code = TryFuzzer.getPrettyCode(tryStatement);
    assertTrue("\nGenerated code: \n" + code, code.startsWith("try {"));
  }

  public void testFunctionDeclaration() {
    FunctionFuzzer fuzzer =
        new FunctionFuzzer(context, false);
    Node functionDecl = fuzzer.generate(20);
    String code = FunctionFuzzer.getPrettyCode(functionDecl);
    assertTrue("\nGenerated code: \n" + code, code.startsWith("function "));
  }

  public void testBreakStatement() throws JsonParseException {
    BreakFuzzer fuzzer = new BreakFuzzer(context);
    fuzzer.getOwnConfig().addProperty("toLabel", 1);
    Scope scope = context.scopeManager.localScope();
    scope.otherLabels.add("testLabel");
    Node breakStmt = fuzzer.generate(10);
    String code = BreakFuzzer.getPrettyCode(breakStmt);
    assertEquals("break testLabel;", code.trim());
  }

  public void testContinueStatement() throws JsonParseException {
    ContinueFuzzer fuzzer = new ContinueFuzzer(context);
    fuzzer.getOwnConfig().addProperty("toLabel", 1);
    Scope scope = context.scopeManager.localScope();
    scope.loopLabels.add("testLabel");
    Node continueStmt = fuzzer.generate(10);
    String code = ContinueFuzzer.getPrettyCode(continueStmt);
    assertEquals("continue testLabel;", code.trim());
  }

  public void testDeterministicProgramGenerating() {
    ScriptFuzzer fuzzer = new ScriptFuzzer(context);
    Node nodes = fuzzer.generate(100);
    String code1 = ScriptFuzzer.getPrettyCode(nodes);

    setUp();
    context =
        new FuzzingContext(new Random(123), TestConfig.getConfig(), true);
    fuzzer = new ScriptFuzzer(context);
    nodes = fuzzer.generate(100);
    String code2 = ScriptFuzzer.getPrettyCode(nodes);

    assertEquals(code1, code2);
  }
  private static void leaveOneSubtype(JsonObject typeConfig, String subtypeName)
      throws JsonParseException {
    JsonObject weightConfig = typeConfig.get("weights").getAsJsonObject();

    for (Entry<String, JsonElement> entry : weightConfig.entrySet()) {
      String name = entry.getKey();
      if (name.equals(subtypeName)) {
        weightConfig.addProperty(name, 1);
      } else {
        weightConfig.addProperty(name, 0);
      }
    }
  }
}
