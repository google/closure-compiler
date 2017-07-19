/*
 * Copyright 2011 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.AstValidator.ViolationHandler;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.SimpleSourceFile;
import com.google.javascript.rhino.Token;

/**
 * @author johnlenz@google.com (John Lenz)
 */
public final class AstValidatorTest extends CompilerTestCase {

  private boolean lastCheckWasValid = true;

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return createValidator(compiler);
  }

  private AstValidator createValidator(Compiler compiler) {
    lastCheckWasValid = true;
    return new AstValidator(compiler, new ViolationHandler() {
      @Override
      public void handleViolation(String message, Node n) {
        lastCheckWasValid = false;
      }
    });
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    disableAstValidation();
    disableNormalize();
    disableLineNumberCheck();
  }

  public void testForIn() {
    valid("for(var a in b);");
    valid("for(a in b);");
    valid("for(a in []);");
    valid("for(a in {});");
  }

  public void testQuestionableForIn() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    setExpectParseWarningsThisTest();
    valid("for(var a = 1 in b);");
  }

  public void testDebugger() {
    valid("debugger;");
  }

  public void testValidScript() {
    Node n = new Node(Token.SCRIPT);
    expectInvalid(n, Check.SCRIPT);
    n.setInputId(new InputId("something_input"));
    n.setStaticSourceFile(new SimpleSourceFile("something", false));
    expectValid(n, Check.SCRIPT);
    expectInvalid(n, Check.STATEMENT);
    expectInvalid(n, Check.EXPRESSION);
  }

  public void testValidStatement1() {
    Node n = new Node(Token.RETURN);
    expectInvalid(n, Check.EXPRESSION);
    expectValid(n, Check.STATEMENT);
    expectInvalid(n, Check.SCRIPT);
  }

  public void testValidExpression1() {
    Node n = new Node(Token.ARRAYLIT, new Node(Token.EMPTY));
    expectValid(n, Check.EXPRESSION);
    expectInvalid(n, Check.STATEMENT);
    expectInvalid(n, Check.SCRIPT);
  }

  public void testValidExpression2() {
    Node n = new Node(Token.NOT, new Node(Token.TRUE));
    expectValid(n, Check.EXPRESSION);
    expectInvalid(n, Check.STATEMENT);
    expectInvalid(n, Check.SCRIPT);
  }

  public void testNewTargetIsValidExpression() {
    Node n = new Node(Token.NEW_TARGET);
    expectValid(n, Check.EXPRESSION);
  }

  public void testInvalidEmptyStatement() {
    Node n = new Node(Token.EMPTY, new Node(Token.TRUE));
    expectInvalid(n, Check.STATEMENT);
    n.detachChildren();
    expectValid(n, Check.STATEMENT);
  }

  public void testInvalidNumberStatement() {
    Node n = IR.number(1);
    expectInvalid(n, Check.STATEMENT);
    n = IR.exprResult(n);
    expectValid(n, Check.STATEMENT);
  }

  public void testValidRestParameter() {
    setLanguage(LanguageMode.ECMASCRIPT_2015, LanguageMode.ECMASCRIPT5);
    valid("function f(a,...rest){}");
    valid("function f(a,...[re,...st]){}");
  }

  public void testDefaultParameter() {
    setLanguage(LanguageMode.ECMASCRIPT_2015, LanguageMode.ECMASCRIPT5);
    valid("function f(a = 0, b){}");
  }

  public void testAwaitExpression() {
    setLanguage(LanguageMode.ECMASCRIPT_NEXT, LanguageMode.ECMASCRIPT5);
    Node awaitNode = new Node(Token.AWAIT);
    awaitNode.addChildToBack(IR.number(1));
    Node parentFunction =
        IR.function(IR.name("foo"), IR.paramList(), IR.block(IR.returnNode(awaitNode)));
    parentFunction.setIsAsyncFunction(true);
    expectValid(awaitNode, Check.EXPRESSION);
  }

  public void testAwaitExpressionNonAsyncFunction() {
    setLanguage(LanguageMode.ECMASCRIPT_NEXT, LanguageMode.ECMASCRIPT5);
    Node awaitNode = new Node(Token.AWAIT);
    awaitNode.addChildToBack(IR.number(1));
    Node parentFunction =
        IR.function(IR.name("foo"), IR.paramList(), IR.block(IR.returnNode(awaitNode)));
    parentFunction.setIsAsyncFunction(false);
    expectInvalid(awaitNode, Check.EXPRESSION);
  }

  public void testAwaitExpressionNoFunction() {
    setLanguage(LanguageMode.ECMASCRIPT_NEXT, LanguageMode.ECMASCRIPT5);
    Node n = new Node(Token.AWAIT);
    n.addChildToBack(IR.number(1));
    expectInvalid(n, Check.EXPRESSION);
  }

  public void testValidDestructuringAssignment() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    valid("var [x, ...y] = obj;");
    valid("([...this.x] = obj);");
    valid("var {a:b} = obj;");
    valid("({a:this.b} = obj);");
  }

  public void testInvalidDestructuringAssignment() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);

    Node n = IR.assign(
        new Node(Token.OBJECT_PATTERN, new Node(Token.ARRAY_PATTERN)), IR.objectlit());
    expectInvalid(n, Check.EXPRESSION);

    n = IR.assign(
        new Node(Token.ARRAY_PATTERN, IR.computedProp(IR.string("x"), IR.number(1))),
        IR.objectlit());
    expectInvalid(n, Check.EXPRESSION);

    Node stringkey = IR.stringKey("x");
    stringkey.addChildToFront(IR.computedProp(IR.string("x"), IR.number(1)));
    n = IR.assign(new Node(Token.OBJECT_PATTERN, stringkey), IR.objectlit());
    expectInvalid(n, Check.EXPRESSION);
  }

  private void valid(String code) {
    testSame(code);
    assertTrue(lastCheckWasValid);
  }

  private enum Check {
    SCRIPT,
    STATEMENT,
    EXPRESSION
  }

  private boolean doCheck(Node n, Check level) {
    AstValidator validator = createValidator(createCompiler());
    switch (level) {
      case SCRIPT:
        validator.validateScript(n);
        break;
      case STATEMENT:
        validator.validateStatement(n);
        break;
      case EXPRESSION:
        validator.validateExpression(n);
        break;
    }
    return lastCheckWasValid;
  }

  private void expectInvalid(Node n, Check level) {
    assertFalse(doCheck(n, level));
  }

  private void expectValid(Node n, Check level) {
    assertTrue(doCheck(n, level));
  }
}
