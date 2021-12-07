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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Streams.stream;
import static com.google.common.truth.Truth.assertThat;

import com.google.javascript.jscomp.AstValidator.TypeInfoValidation;
import com.google.javascript.jscomp.AstValidator.ViolationHandler;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.SimpleSourceFile;
import com.google.javascript.rhino.StaticSourceFile.SourceKind;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.JSTypeResolver;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** @author johnlenz@google.com (John Lenz) */
@RunWith(JUnit4.class)
public final class AstValidatorTest extends CompilerTestCase {

  private List<String> lastCheckViolationMessages;
  private AstValidator.TypeInfoValidation typeInfoValidationMode =
      AstValidator.TypeInfoValidation.JSTYPE;

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return createValidator(compiler);
  }

  @Override
  public CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.MODULE_LOAD, CheckLevel.OFF);
    return options;
  }

  private AstValidator createValidator(Compiler compiler) {
    lastCheckViolationMessages = new ArrayList<>();
    AstValidator astValidator =
        new AstValidator(
            compiler,
            new ViolationHandler() {
              @Override
              public void handleViolation(String message, Node n) {
                lastCheckViolationMessages.add(message);
              }
            },
            /* validateScriptFeatures= */ true);
    astValidator.setTypeValidationMode(typeInfoValidationMode);
    return astValidator;
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    disableAstValidation();
    disableNormalize();
    enableTypeCheck();
  }

  @Test
  public void testParenthesizedProperty() {
    // Since we're building the AST by hand, there won't be any types on it.
    typeInfoValidationMode = AstValidator.TypeInfoValidation.NONE;

    Node n = IR.string("a");
    n.setIsParenthesized(true);
    setTestSourceLocationForTree(n);

    expectValid(n, Check.EXPRESSION);

    n.setToken(Token.STRING_KEY); // A string key cannot be parenthesized
    // We have to put the STRING_KEY into an object and give it a child, so we have an
    // expression to validate that is valid other than the bad parenthesized property.
    Node objNode = IR.objectlit(n);
    n.addChildToFront(IR.number(0));
    objNode.srcrefTree(n);
    expectInvalid(objNode, Check.EXPRESSION, "non-expression is parenthesized");
  }

  @Test
  public void testValidGetPropAndGetElem() {
    valid(
        lines(
            "a.b;", //
            "a['b'];"));
  }

  @Test
  public void testClass() {
    valid(
        lines(
            "class C {",
            "  get m1() {return 1}",
            "  set m1(a) {}",
            "  m2(a) {}",
            "}",
            "",
            "/** @dict */",
            "class D {",
            "  ['m2']() {}",
            "  [m2]() {}",
            "}",
            ""));

    this.typeInfoValidationMode = TypeInfoValidation.NONE; // synthetic AST w/o types

    Node c = new Node(Token.CLASS, IR.name("C"), IR.empty());
    Node members = new Node(Token.CLASS_MEMBERS);
    c.addChildToBack(members);
    setTestSourceLocationForTree(c);
    expectValid(c, Check.STATEMENT);
    Node method1 =
        new Node(Token.MEMBER_FUNCTION_DEF, IR.function(IR.name(""), IR.paramList(), IR.block()));
    method1.srcrefTree(members);
    members.addChildToBack(method1);
    expectInvalid(c, Check.STATEMENT);

    members.detachChildren();

    // Invalid empty string
    Node method2 = Node.newString(Token.MEMBER_FUNCTION_DEF, "");
    method2.addChildToBack(IR.function(IR.name(""), IR.paramList(), IR.block()));
    method2.srcrefTree(members);
    members.addChildToBack(method2);

    expectInvalid(c, Check.STATEMENT);
  }

  @Test
  public void testClassField() {
    valid("class C {x}");
    valid("class C {x = 2}");
    valid("class C {x = 2;}");
    valid("class C {x; y;}");
    valid(
        lines(
            "class C {", //
            "  x",
            "  y",
            "}",
            ""));
  }

  @Test
  public void testClassFieldStatic() {
    valid("class C {static x}");
    valid("class C {static x = 2}");
    valid("class C {static x = 2;}");
    valid("class C {static x; static y;}");
    valid(
        lines(
            "class C {", //
            "  static x",
            "  static y",
            "}",
            ""));
  }

  @Test
  public void testClassComputedField() {
    valid("/** @dict */ class C { [x]; }");
    valid("/** @dict */ class C { ['x']=2; }");
    valid("/** @dict */ class C { 'x'=2; }");
    valid("/** @dict */ class C { 1=2; }");
    valid(
        lines(
            "/** @unrestricted */", //
            "class C {",
            "  [x]=2",
            "  static y = 4",
            "}",
            ""));
  }

  @Test
  public void testClassComputedFieldStatic() {
    valid("/** @dict */ class C { static [x]; }");
    valid("/** @dict */ class C { static ['x']=2; }");
    valid("/** @dict */ class C { static 'x'=2; }");
    valid("/** @dict */ class C { static 1=2; }");
    valid(
        lines(
            "/** @unrestricted */", //
            "class C {",
            "  static [x]=2",
            "  static y = 4",
            "}",
            ""));
  }

  @Test
  public void testFeatureValidation_classField() {
    testFeatureValidation(
        lines(
            "class C {", //
            "  x=2;",
            "}",
            ""),
        Feature.PUBLIC_CLASS_FIELDS);
  }

  @Test
  public void testFeatureValidation_classComputedField() {
    testFeatureValidation(
        lines(
            "/** @dict */", //
            "class C {", //
            "  [x]=2;",
            "}",
            ""),
        Feature.PUBLIC_CLASS_FIELDS);
  }

  @Test
  public void testFor() {
    valid("for(var a;;);");
    valid("for(let a;;);");
    valid("for(var a = 0;;);");
    valid("for(var a;b;c);");
    valid("for(var a;;c);");
  }

  @Test
  public void testForIn() {
    valid("for(var a in b);");
    valid("for(let a in b);");
    valid("for(const a in b);");
    valid("for(a in b);");
    valid("for(a in []);");
    valid("for(a in {});");

    // Test that initializers are banned (except for simple vars - see testQuestionableForIn)
    typeInfoValidationMode = AstValidator.TypeInfoValidation.NONE;
    expectInvalid(
        new Node(Token.FOR_IN, IR.constNode(IR.name("a"), IR.number(1)), IR.name("b")),
        Check.STATEMENT);
    expectInvalid(
        new Node(
            Token.FOR_IN,
            IR.var(new Node(Token.DESTRUCTURING_LHS, IR.objectPattern(), IR.objectlit())),
            IR.name("b")),
        Check.STATEMENT);
  }

  @Test
  public void testForOf() {
    valid("for(var a of b);");
    valid("for(let a of b);");
    valid("for(const a of b);");
    valid("for(a of b);");
    valid("for(a of []);");
    valid("for(a of /** @type {!Iterable<?>} */ ({}));");
    valid("for (const [] of b);");
    valid("for (const {} of b);");

    // Test that initializers are banned
    typeInfoValidationMode = AstValidator.TypeInfoValidation.NONE;
    expectInvalid(
        new Node(Token.FOR_OF, IR.var(IR.name("a"), IR.number(1)), IR.name("b")), Check.STATEMENT);
    expectInvalid(
        new Node(Token.FOR_OF, IR.constNode(IR.name("a"), IR.number(1)), IR.name("b")),
        Check.STATEMENT);
    expectInvalid(
        new Node(
            Token.FOR_OF,
            IR.var(new Node(Token.DESTRUCTURING_LHS, IR.objectPattern(), IR.objectlit())),
            IR.name("b")),
        Check.STATEMENT);
  }

  @Test
  public void testForAwaitOf() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_NEXT);
    valid("async () => { for await(var a of b); }");
    valid("async () => { for await(let a of b); }");
    valid("async () => { for await(const a of b); }");
    valid("async () => { for await(a of b); }");
    valid("async () => { for await(a of []); }");
    valid("async () => { for await(a of /** @type {!Iterable<?>} */ ({})); }");

    // Test that initializers are banned
    typeInfoValidationMode = AstValidator.TypeInfoValidation.NONE;
    expectInvalid(
        new Node(Token.FOR_AWAIT_OF, IR.var(IR.name("a"), IR.number(1)), IR.name("b")),
        Check.STATEMENT);
    expectInvalid(
        new Node(Token.FOR_AWAIT_OF, IR.constNode(IR.name("a"), IR.number(1)), IR.name("b")),
        Check.STATEMENT);
    expectInvalid(
        new Node(
            Token.FOR_AWAIT_OF,
            IR.var(new Node(Token.DESTRUCTURING_LHS, IR.objectPattern(), IR.objectlit())),
            IR.name("b")),
        Check.STATEMENT);
  }

  @Test
  public void testIncDec() {
    valid("x++");
    valid("x--");
    valid("++x");
    valid("--x");
    valid("const a = {b: 0}; a.b++");
    valid("a['b']++");
    valid("/** @type {number} */ (x)++;");

    typeInfoValidationMode = AstValidator.TypeInfoValidation.NONE;
    expectInvalid(new Node(Token.INC, IR.name("x"), IR.name("x")), Check.EXPRESSION);
    expectInvalid(new Node(Token.INC, IR.arrayPattern()), Check.EXPRESSION);
    expectInvalid(new Node(Token.INC, IR.objectPattern()), Check.EXPRESSION);
  }

  @Test
  public void testCompoundAssign() {
    valid("a += 1;");
    valid("a['b'] += 1;");
    valid("const a = {b: 0}; a.b += 1;");
    valid("const a = {b: '0'}; /** @type {?} */ (a.b) += 1;");

    typeInfoValidationMode = AstValidator.TypeInfoValidation.NONE;
    expectInvalid(new Node(Token.ASSIGN_ADD, IR.arrayPattern(), IR.number(0)), Check.EXPRESSION);
    expectInvalid(new Node(Token.ASSIGN_ADD, IR.objectPattern(), IR.number(0)), Check.EXPRESSION);
  }

  @Test
  public void testQuestionableForIn() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    setExpectParseWarningsInThisTest();
    valid("for(var a = 1 in b);");
  }

  @Test
  public void testDebugger() {
    valid("debugger;");
  }

  @Test
  public void testValidScript() {
    // Since we're building the AST by hand, there won't be any types on it.
    typeInfoValidationMode = AstValidator.TypeInfoValidation.NONE;

    Node n = new Node(Token.SCRIPT);
    expectInvalid(n, Check.SCRIPT);
    n.setInputId(new InputId("something_input"));
    n.setStaticSourceFile(new SimpleSourceFile("something", SourceKind.STRONG));
    expectValid(n, Check.SCRIPT);
    expectInvalid(n, Check.STATEMENT);
    expectInvalid(n, Check.EXPRESSION);
  }

  @Test
  public void testValidStatement1() {
    // Since we're building the AST by hand, there won't be any types on it.
    typeInfoValidationMode = AstValidator.TypeInfoValidation.NONE;

    Node n = new Node(Token.RETURN);
    setTestSourceLocationForTree(n);
    expectInvalid(n, Check.EXPRESSION);
    expectValid(n, Check.STATEMENT);
    expectInvalid(n, Check.SCRIPT);
  }

  @Test
  public void testValidExpression1() {
    // Since we're building the AST by hand, there won't be any types on it.
    typeInfoValidationMode = AstValidator.TypeInfoValidation.NONE;

    Node n = new Node(Token.ARRAYLIT, new Node(Token.EMPTY));
    setTestSourceLocationForTree(n);
    expectValid(n, Check.EXPRESSION);
    expectInvalid(n, Check.STATEMENT);
    expectInvalid(n, Check.SCRIPT);
  }

  @Test
  public void testValidExpression2() {
    // Since we're building the AST by hand, there won't be any types on it.
    typeInfoValidationMode = AstValidator.TypeInfoValidation.NONE;

    Node n = new Node(Token.NOT, new Node(Token.TRUE));
    setTestSourceLocationForTree(n);
    expectValid(n, Check.EXPRESSION);
    expectInvalid(n, Check.STATEMENT);
    expectInvalid(n, Check.SCRIPT);
  }

  @Test
  public void testValidConst() {
    valid("const x = r;");
    valid("const [x] = r;");
    valid("const {x} = {x: 1};");
    valid("const x = r, y = r;");
    valid("const {x} = {x: 1}, y = r;");
    valid("const x = r, {y} = {y: 1};");
  }

  @Test
  public void testInvalidConst() {
    Node n = new Node(Token.CONST);
    expectInvalid(n, Check.STATEMENT);

    n.addChildToBack(IR.name("x"));
    expectInvalid(n, Check.STATEMENT);

    n = new Node(Token.CONST);
    n.addChildToBack(new Node(Token.DESTRUCTURING_LHS));
    n.getFirstChild().addChildToBack(new Node(Token.OBJECT_PATTERN));

    expectInvalid(n, Check.STATEMENT);
  }

  @Test
  public void testInvalidConstLanguageLevel() {
    // Since we're building the AST by hand, there won't be any types on it.
    typeInfoValidationMode = AstValidator.TypeInfoValidation.NONE;

    Node n = IR.constNode(IR.name("x"), IR.number(3));
    setTestSourceLocationForTree(n);

    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    expectInvalid(n, Check.STATEMENT);

    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    expectValid(n, Check.STATEMENT);
  }

  @Test
  public void testInvalidLetLanguageLevel() {
    // Since we're building the AST by hand, there won't be any types on it.
    typeInfoValidationMode = AstValidator.TypeInfoValidation.NONE;

    Node n = IR.let(IR.name("x"), IR.number(3));
    setTestSourceLocationForTree(n);

    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    expectInvalid(n, Check.STATEMENT);

    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    expectValid(n, Check.STATEMENT);
  }

  @Test
  public void testNewTargetIsValidExpression() {
    // Since we're building the AST by hand, there won't be any types on it.
    typeInfoValidationMode = AstValidator.TypeInfoValidation.NONE;

    Node n = new Node(Token.NEW_TARGET);
    setTestSourceLocationForTree(n);
    expectValid(n, Check.EXPRESSION);
  }

  @Test
  public void testImportMetaIsValidExpression() {
    // Since we're building the AST by hand, there won't be any types on it.
    typeInfoValidationMode = AstValidator.TypeInfoValidation.NONE;

    Node n = new Node(Token.IMPORT_META);
    setTestSourceLocationForTree(n);
    expectValid(n, Check.EXPRESSION);
  }

  @Test
  public void testCastOnLeftSideOfAssign() {
    // Since we're building the AST by hand, there won't be any types on it.
    typeInfoValidationMode = AstValidator.TypeInfoValidation.NONE;

    JSDocInfo.Builder jsdoc = JSDocInfo.builder();
    jsdoc.recordType(new JSTypeExpression(IR.string("number"), "<AstValidatorTest>"));
    Node n =
        IR.exprResult(new Node(Token.ASSIGN, IR.cast(IR.name("x"), jsdoc.build()), IR.number(0)));
    setTestSourceLocationForTree(n);
    expectValid(n, Check.STATEMENT);
  }

  @Test
  public void testInvalidEmptyStatement() {
    Node n = new Node(Token.EMPTY, new Node(Token.TRUE));
    setTestSourceLocationForTree(n);
    expectInvalid(n, Check.STATEMENT);
    n.detachChildren();
    expectValid(n, Check.STATEMENT);
  }

  @Test
  public void testInvalidNumberStatement() {
    // Since we're building the AST by hand, there won't be any types on it.
    typeInfoValidationMode = AstValidator.TypeInfoValidation.NONE;

    Node n = IR.number(1);
    setTestSourceLocationForTree(n);
    expectInvalid(n, Check.STATEMENT);
    n = IR.exprResult(n);
    setTestSourceLocationForTree(n);
    expectValid(n, Check.STATEMENT);
  }

  @Test
  public void testInvalidBigIntStatement() {
    // Since we're building the AST by hand, there won't be any types on it.
    typeInfoValidationMode = AstValidator.TypeInfoValidation.NONE;

    Node n = IR.bigint(BigInteger.ONE);
    setTestSourceLocationForTree(n);
    expectInvalid(n, Check.STATEMENT);
    n = IR.exprResult(n);
    setTestSourceLocationForTree(n);
    expectValid(n, Check.STATEMENT);
  }

  @Test
  public void testValidRestParameter() {
    setLanguage(LanguageMode.ECMASCRIPT_2015, LanguageMode.ECMASCRIPT5);
    valid("function f(a,...rest){}");
    valid("function f(a,...[re,...st]){}");
  }

  @Test
  public void testDefaultParameter() {
    setLanguage(LanguageMode.ECMASCRIPT_2015, LanguageMode.ECMASCRIPT5);
    valid("function f(a, b = 0){}");
  }

  @Test
  public void testAwaitExpression() {
    // Since we're building the AST by hand, there won't be any types on it.
    typeInfoValidationMode = AstValidator.TypeInfoValidation.NONE;

    setLanguage(LanguageMode.ECMASCRIPT_NEXT, LanguageMode.ECMASCRIPT5);
    Node awaitNode = new Node(Token.AWAIT);
    awaitNode.addChildToBack(IR.number(1));
    Node parentFunction =
        IR.function(IR.name("foo"), IR.paramList(), IR.block(IR.returnNode(awaitNode)));
    setTestSourceLocationForTree(awaitNode);
    parentFunction.setIsAsyncFunction(true);
    expectValid(awaitNode, Check.EXPRESSION);
  }

  @Test
  public void testNoAwaitExpressionInDefaultParams() {
    // We're inserting our own Nodes below, and we won't be bothering to put valid type
    // information on them.
    typeInfoValidationMode = AstValidator.TypeInfoValidation.NONE;
    Node scriptNode =
        parseValidScript(
            lines(
                "async function outer(){",
                // `await` in a parameter default value is not allowed,
                // regardless of whether the function with the parameter is an
                // async function or enclosed in an async function.
                "  async function inner(a = replaceWithAwait) {",
                "  }",
                "}",
                ""));

    Node awaitNode = new Node(Token.AWAIT);
    awaitNode.addChildToBack(IR.number(1));
    Node nodeToReplace =
        stream(NodeUtil.preOrderIterable(scriptNode))
            .filter(node -> node.isName() && node.getString().equals("replaceWithAwait"))
            .findFirst()
            .get();
    nodeToReplace.replaceWith(awaitNode);
    expectInvalid(awaitNode, Check.EXPRESSION);
  }

  @Test
  public void testNoYieldExpressionInDefaultParams() {
    // We're inserting our own Nodes below, and we won't be bothering to put valid type
    // information on them.
    typeInfoValidationMode = AstValidator.TypeInfoValidation.NONE;
    Node scriptNode =
        parseValidScript(
            lines(
                "function *outer(){",
                // `yield` in a parameter default value is not allowed,
                // regardless of whether the function with the parameter is a generator
                // or is enclosed in a generator function.
                "  function *inner(a = replaceWithYield) {",
                "  }",
                "}",
                ""));

    Node yieldNode = new Node(Token.YIELD);
    yieldNode.addChildToBack(IR.number(1));
    Node nodeToReplace =
        stream(NodeUtil.preOrderIterable(scriptNode))
            .filter(node -> node.isName() && node.getString().equals("replaceWithYield"))
            .findFirst()
            .get();
    nodeToReplace.replaceWith(yieldNode);
    expectInvalid(yieldNode, Check.EXPRESSION);
  }

  @Test
  public void testAwaitExpressionNonAsyncFunction() {
    setLanguage(LanguageMode.ECMASCRIPT_NEXT, LanguageMode.ECMASCRIPT5);
    Node awaitNode = new Node(Token.AWAIT);
    awaitNode.addChildToBack(IR.number(1));
    Node parentFunction =
        IR.function(IR.name("foo"), IR.paramList(), IR.block(IR.returnNode(awaitNode)));
    parentFunction.setIsAsyncFunction(false);
    expectInvalid(awaitNode, Check.EXPRESSION);
  }

  @Test
  public void testYieldExpressionNonGeneratorFunction() {
    Node yieldNode = new Node(Token.YIELD);
    yieldNode.addChildToBack(IR.number(1));
    Node parentFunction =
        IR.function(IR.name("foo"), IR.paramList(), IR.block(IR.returnNode(yieldNode)));
    parentFunction.setIsGeneratorFunction(false);
    expectInvalid(yieldNode, Check.EXPRESSION);
  }

  @Test
  public void testAwaitExpressionNoFunction() {
    // Since we're building the AST by hand, there won't be any types on it.
    typeInfoValidationMode = AstValidator.TypeInfoValidation.NONE;

    setLanguage(LanguageMode.ECMASCRIPT_NEXT, LanguageMode.ECMASCRIPT5);
    Node n = new Node(Token.AWAIT);
    n.addChildToBack(IR.number(1));
    expectInvalid(n, Check.EXPRESSION);
  }

  @Test
  public void testYieldExpressionNoFunction() {
    // Since we're building the AST by hand, there won't be any types on it.
    typeInfoValidationMode = AstValidator.TypeInfoValidation.NONE;

    setLanguage(LanguageMode.ECMASCRIPT_NEXT, LanguageMode.ECMASCRIPT5);
    Node n = new Node(Token.YIELD);
    n.addChildToBack(IR.number(1));
    expectInvalid(n, Check.EXPRESSION);
  }

  @Test
  public void testInvalidArrayPattern0() {
    // Since we're building the AST by hand, there won't be any types on it.
    typeInfoValidationMode = AstValidator.TypeInfoValidation.NONE;

    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);

    // [...x = 1] = [];
    Node n =
        IR.assign(
            new Node(
                Token.ARRAY_PATTERN,
                new Node(
                    Token.ITER_REST, new Node(Token.DEFAULT_VALUE, IR.name("x"), IR.arraylit()))),
            IR.arraylit());
    expectInvalid(n, Check.EXPRESSION);
  }

  @Test
  public void testValidDestructuringAssignment0() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    valid("var [x] = obj;");
    valid("var [x = 1] = obj;");
    valid("var [...y] = obj;");
    valid("var [x, ...y] = obj;");

    valid("[x] = [];");
    valid("[x = 1] = [];");
    valid("[x.y] = [];");
    valid("[x.y = 1] = [];");
    valid("[x['y']] = [];");
    valid("[x['y'] = 1] = [];");
    valid("[x().y] = [];");
    valid("[x().y = 1] = [];");
    valid("[x()['y']] = [];");
    valid("[x()['y'] = 1] = [];");

    valid("([...y] = obj);");
    valid("([x, ...y] = obj);");
    valid("([...this.x] = obj);");
    valid("([...this['x']] = obj);");
    valid("([...x.y] = obj);");
    valid("([...x['y']] = obj);");
    valid("([...x().y] = obj);");
    valid("([...x()['y']] = obj);");
  }

  @Test
  public void testValidDestructuringAssignment1() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    valid("var {a:b} = {a: 1};");
    valid("({a:b} = {a: 1});");
    valid("({a:b.c} = {a: 1});");
    valid("({a:b().c} = {a: 1});");
    valid("({a:b['c']} = {a: 1});");
    valid("({a:b()['c']} = {a: 1});");
    valid("({a:b.c = 1} = {a: 1});");
    valid("({a:b().c = 1} = {a: 1});");
    valid("({a:b['c'] = 1} = {a: 1});");
    valid("({a:b()['c'] = 1} = {a: 1});");
  }

  @Test
  public void testValidDestructuringAssignment2() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    valid("var {['a']:b} = obj;");
    valid("({['a']:b} = obj);");
    valid("({['a']:this.b} = obj);");
    valid("({['a']:b.c} = obj);");
    valid("({['a']:b.c = 1} = obj);");
    valid("({['a']:b().c} = obj);");
    valid("({['a']:b().c = 1} = obj);");
    valid("({['a']:b['c']} = obj);");
    valid("({['a']:b['c'] = 1} = obj);");
    valid("({['a']:b()['c']} = obj);");
    valid("({['a']:b()['c'] = 1} = obj);");
  }

  @Test
  public void testObjectRestAssignment() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_NEXT);
    valid("var {a, ...rest} = {a: 1, b: 2};");
    valid("({a:b, ...rest} = {a: 1, b: 2});");
    valid("({a:b.c, ...rest} = {a: 1, b: 2});");
    valid("({a:b().c, ...rest} = {a: 1, b: 2});");
    valid("({a:b['c'], ...rest} = {a: 1, b: 2});");
    valid("({a:b()['c'], ...rest} = {a: 1, b: 2});");
    valid("({a:b.c = 1, ...rest} = {a: 1, b: 2});");
    valid("({a:b().c = 1, ...rest} = {a: 1, b: 2});");
    valid("({a:b['c'] = 1, ...rest} = {a: 1, b: 2});");
    valid("({a:b()['c'] = 1, ...rest} = {a: 1, b: 2});");
  }

  @Test
  public void testInvalidObjectRestForLanguageLevel() {
    // Since we're building the AST by hand, there won't be any types on it.
    typeInfoValidationMode = AstValidator.TypeInfoValidation.NONE;

    Node n = IR.assign(IR.objectPattern(IR.objectRest(IR.name("x"))), IR.objectlit());
    setTestSourceLocationForTree(n);

    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    expectInvalid(n, Check.EXPRESSION);

    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2018);
    expectValid(n, Check.EXPRESSION);
  }

  @Test
  public void testInvalidArrayRestForLanguageLevel() {
    // Since we're building the AST by hand, there won't be any types on it.
    typeInfoValidationMode = AstValidator.TypeInfoValidation.NONE;

    Node n = IR.assign(IR.arrayPattern(IR.iterRest(IR.name("x"))), IR.arraylit());
    setTestSourceLocationForTree(n);

    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    expectInvalid(n, Check.EXPRESSION);

    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    expectValid(n, Check.EXPRESSION);
  }

  @Test
  public void testInvalidDestructuringDeclaration() {
    // Since we're building the AST by hand, there won't be any types on it.
    typeInfoValidationMode = AstValidator.TypeInfoValidation.NONE;

    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);

    // missing DESTRUCTURING_LHS
    //     Node n = IR.var(IR.objectPattern());
    //     expectInvalid(n, Check.STATEMENT);

    //     n = IR.let(IR.objectPattern());
    //     expectInvalid(n, Check.STATEMENT);

    //     n = new Node(Token.CONST, IR.objectPattern());
    //     expectInvalid(n, Check.STATEMENT);

    // missing a right-hand side
    Node n = IR.var(new Node(Token.DESTRUCTURING_LHS, IR.objectPattern()));
    expectInvalid(n, Check.STATEMENT);

    n = IR.let(new Node(Token.DESTRUCTURING_LHS, IR.objectPattern()));
    expectInvalid(n, Check.STATEMENT);

    n = new Node(Token.CONST, new Node(Token.DESTRUCTURING_LHS, IR.objectPattern()));
    expectInvalid(n, Check.STATEMENT);
  }

  @Test
  public void testInvalidDestructuringAssignment() {
    // Since we're building the AST by hand, there won't be any types on it.
    typeInfoValidationMode = AstValidator.TypeInfoValidation.NONE;

    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);

    Node n =
        IR.assign(new Node(Token.OBJECT_PATTERN, new Node(Token.ARRAY_PATTERN)), IR.objectlit());
    expectInvalid(n, Check.EXPRESSION);

    n =
        IR.assign(
            new Node(Token.ARRAY_PATTERN, IR.computedProp(IR.string("x"), IR.number(1))),
            IR.objectlit());
    expectInvalid(n, Check.EXPRESSION);

    Node stringkey = IR.stringKey("x");
    stringkey.addChildToFront(IR.computedProp(IR.string("x"), IR.number(1)));
    n = IR.assign(new Node(Token.OBJECT_PATTERN, stringkey), IR.objectlit());
    expectInvalid(n, Check.EXPRESSION);
  }

  @Test
  public void testGetter() {
    valid(
        lines(
            "class C {", //
            "  get m1() {return 1}",
            "}",
            "",
            "/** @dict */",
            "class D {", //
            "  get ['m2']() {return 2}",
            "}",
            ""));

    // Since we're modifying the AST by hand below, there won't be any types on it.
    typeInfoValidationMode = AstValidator.TypeInfoValidation.NONE;

    Node c = new Node(Token.CLASS, IR.name("C"), IR.empty());
    Node members = new Node(Token.CLASS_MEMBERS);
    c.addChildToBack(members);
    setTestSourceLocationForTree(c);
    expectValid(c, Check.STATEMENT);

    // Invalid getter with parameters
    Node getter1 = Node.newString(Token.GETTER_DEF, "prop");
    getter1.addChildToBack(IR.function(IR.name(""), IR.paramList(IR.name("foo")), IR.block()));
    getter1.srcrefTree(members);
    members.addChildToBack(getter1);
    expectInvalid(c, Check.STATEMENT);

    members.detachChildren();

    // Invalid getter with function name
    Node getter2 = Node.newString(Token.GETTER_DEF, "prop");
    getter2.addChildToBack(IR.function(IR.name("foo"), IR.paramList(), IR.block()));
    getter2.srcrefTree(members);
    members.addChildToBack(getter2);
    expectInvalid(c, Check.STATEMENT);

    members.detachChildren();

    // Invalid computed property style getter with parameters
    Node getter3 =
        new Node(
            Token.COMPUTED_PROP,
            IR.string("prop"),
            IR.function(IR.name(""), IR.paramList(IR.name("foo")), IR.block()));
    getter3.putBooleanProp(Node.COMPUTED_PROP_GETTER, true);
    getter3.srcrefTree(members);
    members.addChildToBack(getter3);
    expectInvalid(c, Check.STATEMENT);

    members.detachChildren();

    // Invalid computed property style getter with function name
    Node getter4 =
        new Node(
            Token.COMPUTED_PROP,
            IR.string("prop"),
            IR.function(IR.name("foo"), IR.paramList(), IR.block()));
    getter4.putBooleanProp(Node.COMPUTED_PROP_GETTER, true);
    getter4.srcrefTree(members);
    members.addChildToBack(getter4);
    expectInvalid(c, Check.STATEMENT);
  }

  @Test
  public void testSuperInvalidAtScriptLevel() {
    Node scriptNode = IR.script();
    Node superNode = IR.superNode();
    Node superStatementNode = IR.exprResult(IR.getprop(superNode, "prop"));
    scriptNode.addChildToBack(superStatementNode);

    expectInvalid(superStatementNode, Check.STATEMENT);
  }

  @Test
  public void superInvalidWithOptChainCall() {
    Node superNode = IR.superNode();
    Node optChainCallNode = IR.startOptChainCall(superNode);

    expectInvalid(optChainCallNode, Check.STATEMENT);
  }

  @Test
  public void superInvalidWithOptionalGetProp() {
    Node superNode = IR.superNode();
    Node optChainGetPropNode = IR.startOptChainGetprop(superNode, "prop");

    expectInvalid(optChainGetPropNode, Check.STATEMENT);
  }

  @Test
  public void superInvalidWithOptionalGetElem() {
    Node superNode = IR.superNode();
    Node exprNode = IR.name("expr");
    Node optChainGetElemNode = IR.startOptChainGetelem(superNode, exprNode);

    expectInvalid(optChainGetElemNode, Check.STATEMENT);
  }

  @Test
  public void optChainGetPropInvalidWithNoStartOfChain() {
    Node innerGetProp = IR.continueOptChainGetprop(IR.name("expr"), "prop1");
    Node outterGetProp = IR.continueOptChainGetprop(innerGetProp, "prop2");

    expectInvalid(outterGetProp, Check.STATEMENT);
  }

  @Test
  public void optChainGetElemInvalidWithNoStartOfChain() {
    Node innerGetElem = IR.continueOptChainGetelem(IR.name("expr"), IR.name("prop1"));
    Node outterGetElem = IR.continueOptChainGetelem(innerGetElem, IR.name("prop2"));

    expectInvalid(outterGetElem, Check.STATEMENT);
  }

  @Test
  public void optChainCallInvalidWithNoStartOfChain() {
    Node innerCall = IR.continueOptChainCall(IR.name("f"), IR.name("arg1"));
    Node outterCall = IR.continueOptChainCall(innerCall, IR.name("arg2"));

    expectInvalid(outterCall, Check.STATEMENT);
  }

  @Test
  public void testSuperInvalidInNonMemberFunction() {
    invalid(
        lines(
            "function nonMethod() {", //
            "  return super.toString();",
            "}",
            ""));
  }

  @Test
  public void testSuperPropReferenceIsValidInClassWithoutExtendsClause() {
    valid(
        lines(
            "class C {", //
            "  method() {",
            "    super.prop;",
            "    super['prop'];",
            "  }",
            "}",
            ""));
  }

  @Test
  public void testSuperPropReferenceIsValidInObjectLiteralMethod() {
    valid(
        lines(
            "const myObj = {", //
            "  method() {",
            "    super.prop;",
            "    super['prop'];",
            "  }",
            "};",
            ""));
  }

  @Test
  public void testSuperInvalidAsAnExpression() {
    Node scriptNode =
        parseValidScript(
            lines(
                "class D {}", //
                "class C extends D {",
                "  method() {",
                "    return replaceWithSuper;",
                "  }",
                "}",
                ""));

    Node nodeToReplace =
        stream(NodeUtil.preOrderIterable(scriptNode))
            .filter(node -> node.isName() && node.getString().equals("replaceWithSuper"))
            .findFirst()
            .get();
    nodeToReplace.replaceWith(IR.superNode());

    expectInvalid(scriptNode, Check.SCRIPT);
  }

  @Test
  public void testSuperInvalidIfTypeInfoIsMissing() {
    Node scriptNode =
        parseValidScript(
            lines(
                "class C {", //
                "  method() {",
                "    return super.toString();",
                "  }",
                "}",
                ""));

    Node superNode =
        stream(NodeUtil.preOrderIterable(scriptNode)).filter(Node::isSuper).findFirst().get();
    // Erase the type information to make the super node invalid
    superNode.setJSType(null);

    expectInvalid(scriptNode, Check.SCRIPT);
  }

  @Test
  public void testValidSuperConstructorCall() {
    valid(
        lines(
            "class D {}", //
            "class C extends D {",
            "  constructor() {",
            "    super();",
            "  }",
            "}",
            ""));
  }

  @Test
  public void testInvalidSuperConstructorCallInNonConstructor() {
    invalid(
        lines(
            "class D {}", //
            "class C extends D {",
            "  nonConstructor() {",
            "    super();",
            "  }",
            "}",
            ""));
  }

  @Test
  public void testSuperConstructorCallInvalidInClassWithoutExtendsClause() {
    invalid(
        lines(
            "class C {", //
            "  constructor() {",
            "    super();",
            "  }",
            "}",
            ""));
  }

  @Test
  public void testSuperConstructorCallInvalidInObjectLiteralMethod() {
    invalid(
        lines(
            "const myObj = {", //
            "  constructor() {",
            "    return super();",
            "  }",
            "}",
            ""));
  }

  @Test
  public void testSetter() {
    valid(
        lines(
            "class C {", //
            "  set m1(value) {}",
            "}",
            "",
            "/** @dict */",
            "class D {", //
            "  set ['m2'](value) {}",
            "}",
            ""));

    // Since we're modifying the AST by hand below, there won't be types on some nodes that need
    // them.
    typeInfoValidationMode = AstValidator.TypeInfoValidation.NONE;

    Node c = new Node(Token.CLASS, IR.name("C"), IR.empty());
    Node members = new Node(Token.CLASS_MEMBERS);
    c.addChildToBack(members);
    setTestSourceLocationForTree(c);
    expectValid(c, Check.STATEMENT);

    // Invalid setter with no parameters
    Node setter1 = Node.newString(Token.SETTER_DEF, "prop");
    setter1.addChildToBack(IR.function(IR.name(""), IR.paramList(), IR.block()));
    setter1.srcrefTree(members);
    members.addChildToBack(setter1);
    expectInvalid(c, Check.STATEMENT);

    members.detachChildren();

    // Invalid setter with function name
    Node setter2 = Node.newString(Token.SETTER_DEF, "prop");
    setter2.addChildToBack(IR.function(IR.name("foo"), IR.paramList(IR.name("value")), IR.block()));
    setter2.srcrefTree(members);
    members.addChildToBack(setter2);
    expectInvalid(c, Check.STATEMENT);

    members.detachChildren();

    // Invalid computed property style setter with parameters
    Node setter3 =
        new Node(
            Token.COMPUTED_PROP,
            IR.string("prop"),
            IR.function(IR.name(""), IR.paramList(), IR.block()));
    setter3.putBooleanProp(Node.COMPUTED_PROP_SETTER, true);
    setter3.srcrefTree(members);
    members.addChildToBack(setter3);
    expectInvalid(c, Check.STATEMENT);

    members.detachChildren();

    // Invalid computed property style setter with function name
    Node setter4 =
        new Node(
            Token.COMPUTED_PROP,
            IR.string("prop"),
            IR.function(IR.name("foo"), IR.paramList(IR.name("value")), IR.block()));
    setter4.putBooleanProp(Node.COMPUTED_PROP_SETTER, true);
    setter4.srcrefTree(members);
    members.addChildToBack(setter4);
    expectInvalid(c, Check.STATEMENT);
  }

  /** Tests checking that AstValidator validates one particular Feature in the AST. */
  @Test
  public void testFeatureValidation_getter() {
    testFeatureValidation("var obj = {get f() {}};", Feature.GETTER);
  }

  @Test
  public void testFeatureValidation_setter() {
    testFeatureValidation("var obj = {set f(x) {}};", Feature.SETTER);
  }

  @Test
  public void testFeatureValidation_classGetterSetter() {
    testFeatureValidation("class C { get f() {} }", Feature.CLASS_GETTER_SETTER);
    testFeatureValidation("class C { set f(x) {} }", Feature.CLASS_GETTER_SETTER);
  }

  @Test
  public void testFeatureValidation_classExtends() {
    testFeatureValidation("class B {} class C extends B {}", Feature.CLASS_EXTENDS);
  }

  @Test
  public void testFeatureValidation_arrowFunctions() {
    testFeatureValidation("var arrow = () => 3", Feature.ARROW_FUNCTIONS);
    testFeatureValidation("var asyncArrow = async () => 3", Feature.ARROW_FUNCTIONS);
  }

  @Test
  public void testFeatureValidation_blockScopedFunctionDeclaration() {
    testFeatureValidation("{ function f() {} }", Feature.BLOCK_SCOPED_FUNCTION_DECLARATION);
    testFeatureValidation(
        "function f() { if (true) { function g() {} } }",
        Feature.BLOCK_SCOPED_FUNCTION_DECLARATION);
    valid("function f() {}");
  }

  @Test
  public void testFeatureValidation_classes() {
    testFeatureValidation("class C {}", Feature.CLASSES);
    testFeatureValidation("var C = class {}", Feature.CLASSES);
  }

  @Test
  public void testFeatureValidation_computedProperties() {
    testFeatureValidation("var obj = { ['foo' + 3]: 4};", Feature.COMPUTED_PROPERTIES);
    testFeatureValidation("var { ['foo' + 3]: x} = obj;", Feature.COMPUTED_PROPERTIES);
    testFeatureValidation("/** @dict */ class C { ['foobar']() {} }", Feature.COMPUTED_PROPERTIES);
  }

  @Test
  public void testFeatureValidation_defaultParameters() {
    testFeatureValidation("function f(a = 1) {}", Feature.DEFAULT_PARAMETERS);
    testFeatureValidation("((a = 3) => a)", Feature.DEFAULT_PARAMETERS);
  }

  @Test
  public void testFeatureValidation_arrayDestructuring() {
    testFeatureValidation("var x, [a, b] = arr;", Feature.ARRAY_DESTRUCTURING);
    testFeatureValidation("x = 0, [a, b] = obj;", Feature.ARRAY_DESTRUCTURING);
    testFeatureValidation("for ([a, b] of c) {}", Feature.ARRAY_DESTRUCTURING);
    testFeatureValidation("function f([a, b]) {}", Feature.ARRAY_DESTRUCTURING);
  }

  @Test
  public void testFeatureValidation_objectDestructuring() {
    testFeatureValidation("var x, {a, b} = {a: 1, b: 2};", Feature.OBJECT_DESTRUCTURING);
    testFeatureValidation("(x = 0, {a, b} = {a: 1, b: 2});", Feature.OBJECT_DESTRUCTURING);
    testFeatureValidation(
        lines(
            "/** @type {!Array<{a: string, b:string}>} */",
            "const c = [];",
            "for ({a, b} of c) {}",
            ""),
        Feature.OBJECT_DESTRUCTURING);
    testFeatureValidation(
        lines(
            "/** @param {{a: string, b:string}} p1 */", //
            "function f({a, b}) {}",
            ""),
        Feature.OBJECT_DESTRUCTURING);
  }

  @Test
  public void testFeatureValidation_extendedObjectLiterals() {
    testFeatureValidation("var obj = { x };", Feature.EXTENDED_OBJECT_LITERALS);
  }

  @Test
  public void testFeatureValidation_forOf() {
    testFeatureValidation("for (const a of b) {}", Feature.FOR_OF);
  }

  @Test
  public void testFeatureValidation_forAwaitOf() {
    testFeatureValidation("async () => { for await (const a of b) {} }", Feature.FOR_AWAIT_OF);
  }

  @Test
  public void testFeatureValidation_generatorFunctions() {
    testFeatureValidation("const f = function *() {}", Feature.GENERATORS);
    testFeatureValidation("function *f() {}", Feature.GENERATORS);
    testFeatureValidation("class C { *f() {} }", Feature.GENERATORS);
  }

  @Test
  public void testFeatureValidation_memberDeclarations() {
    testFeatureValidation("class C { f() {} }", Feature.MEMBER_DECLARATIONS);
    testFeatureValidation("var obj = { f() {} };", Feature.MEMBER_DECLARATIONS);
  }

  @Test
  public void testFeatureValidation_newTarget() {
    testFeatureValidation("function f() { new.target }", Feature.NEW_TARGET);
  }

  @Test
  public void testFeatureValidation_restParameters() {
    testFeatureValidation("function f(...rest) {}", Feature.REST_PARAMETERS);
  }

  @Test
  public void testFeatureValidation_spreadExpressions() {
    testFeatureValidation("f(...arr);", Feature.SPREAD_EXPRESSIONS);
    testFeatureValidation("var arr = [...something];", Feature.SPREAD_EXPRESSIONS);
    testFeatureValidation("var obj = {...something};", Feature.OBJECT_LITERALS_WITH_SPREAD);
  }

  @Test
  public void testFeatureValidation_super() {
    testFeatureValidation("const obj = { f() { super.toString(); } };", Feature.SUPER);
  }

  @Test
  public void testFeatureValidation_templateLiterals() {
    testFeatureValidation("`foo ${3} bar `", Feature.TEMPLATE_LITERALS);
    testFeatureValidation("tag`foo ${3} bar`", Feature.TEMPLATE_LITERALS);
  }

  @Test
  public void testFeatureValidation_taggedTemplateLiteralWithInvalidEscapes() {
    // Tagged template literals are allowed to contain invalid escape sequences,
    // so these should not cause compiler errors or warnings.
    testFeatureValidation("foo`\\unicode`", Feature.TEMPLATE_LITERALS);
    testFeatureValidation("foo`\\xray ${3} \\u{42`", Feature.TEMPLATE_LITERALS);
  }

  @Test
  public void testFeatureValidation_modules() {
    // Modules need to be set up better than we're doing here to avoid type check throwing an
    // exception
    disableTypeCheck();
    this.typeInfoValidationMode = TypeInfoValidation.NONE;
    testFeatureValidation("export {x};", Feature.MODULES);
    testFeatureValidation("import {x} from './foo.js';", Feature.MODULES);
  }

  @Test
  public void testFeatureValidation_exponentOp() {
    testFeatureValidation("2 ** 3", Feature.EXPONENT_OP);
    testFeatureValidation("x **= 3;", Feature.EXPONENT_OP);
  }

  @Test
  public void testFeatureValidation_nullishCoalesceOp() {
    testFeatureValidation("x ?? y", Feature.NULL_COALESCE_OP);
    testFeatureValidation("x ?? y ?? z", Feature.NULL_COALESCE_OP);
  }

  @Test
  public void testFeatureValidation_logicalAssignmentOp() {
    // TODO (user): re-enable TypeInfoValidation and TypeCheck
    typeInfoValidationMode = AstValidator.TypeInfoValidation.NONE;
    disableTypeCheck();
    testFeatureValidation("x ||= y", Feature.LOGICAL_ASSIGNMENT);
    testFeatureValidation("x &&= y", Feature.LOGICAL_ASSIGNMENT);
    testFeatureValidation("x ??= y", Feature.LOGICAL_ASSIGNMENT);
  }

  @Test
  public void testFeatureValidation_optChain() {
    testFeatureValidation("x?.y", Feature.OPTIONAL_CHAINING);
    testFeatureValidation("x?.()", Feature.OPTIONAL_CHAINING);
    testFeatureValidation("x?.[1]", Feature.OPTIONAL_CHAINING);
  }

  @Test
  public void testFeatureValidation_asyncFunctions() {
    testFeatureValidation("const f = async function() {}", Feature.ASYNC_FUNCTIONS);
    testFeatureValidation("async function f() {}", Feature.ASYNC_FUNCTIONS);
    testFeatureValidation("class C { async f() {} }", Feature.ASYNC_FUNCTIONS);
    testFeatureValidation("(async () => {})", Feature.ASYNC_FUNCTIONS);
  }

  @Test
  public void testFeatureValidation_asyncGeneratorFunctions() {
    testFeatureValidation("const f = async function *() {}", Feature.ASYNC_GENERATORS);
    testFeatureValidation("async function *f() {}", Feature.ASYNC_GENERATORS);
    testFeatureValidation("class C { async *f() {} }", Feature.ASYNC_GENERATORS);
  }

  @Test
  public void testFeatureValidation_objectLiteralsWithSpread() {
    testFeatureValidation("var obj = {...something};", Feature.OBJECT_LITERALS_WITH_SPREAD);
  }

  @Test
  public void testValidFeatureInScript() {
    // Since we're building the AST by hand, there won't be any types on it.
    typeInfoValidationMode = AstValidator.TypeInfoValidation.NONE;

    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);

    Node n = new Node(Token.SCRIPT);
    n.setInputId(new InputId("something_input"));
    n.setStaticSourceFile(new SimpleSourceFile("something", SourceKind.STRONG));
    expectValid(n, Check.SCRIPT);

    n.addChildToFront(IR.let(IR.name("a"), IR.number(3)));
    n.srcrefTree(n);
    expectInvalid(n, Check.SCRIPT);

    n.putProp(Node.FEATURE_SET, FeatureSet.BARE_MINIMUM.with(Feature.LET_DECLARATIONS));
    expectValid(n, Check.SCRIPT);
  }

  @Test
  @SuppressWarnings("MustBeClosedChecker")
  public void testUnresolvedTypesAreBanned() {
    Compiler compiler = createCompiler();
    JSTypeRegistry registry = compiler.getTypeRegistry();
    JSTypeResolver.Closer closer = registry.getResolver().openForDefinition();

    JSType unresolvedFunction =
        registry.createFunctionType(registry.getNativeType(JSTypeNative.VOID_TYPE));
    assertThat(unresolvedFunction.isResolved()).isFalse();

    Node foo = IR.name("foo").setJSType(unresolvedFunction);
    Node expr = IR.exprResult(foo);
    setTestSourceLocationForTree(expr);

    expectInvalid(expr, Check.STATEMENT);

    closer.close();

    expectValid(expr, Check.STATEMENT);
  }

  @Test
  public void testValidatesColorInfoOnExpression() {
    this.typeInfoValidationMode = TypeInfoValidation.COLOR;

    Node foo = IR.string("foo");
    Node expr = IR.exprResult(foo);
    Node script = IR.script(expr);
    setTestSourceLocationForTree(script);

    expectInvalid(expr, Check.STATEMENT);

    Compiler compiler = createCompiler();

    JSTypeRegistry registry = compiler.getTypeRegistry();
    foo.setJSType(registry.getNativeType(JSTypeNative.STRING_TYPE));
    expectInvalid(expr, Check.STATEMENT);

    foo.setJSType(null);
    foo.setColor(StandardColors.STRING);

    expectValid(expr, Check.STATEMENT);
  }

  @Test
  public void testSwitchStatement() {
    // Since we're building the AST by hand, there won't be any types on it.
    typeInfoValidationMode = AstValidator.TypeInfoValidation.NONE;

    String switchStatement =
        lines(
            "function foo(x) {",
            "  switch(x) {",
            "    case 1: ",
            "      var y = 5;",
            "    case 2: ",
            "      var y = 5;",
            "  }",
            "}");

    valid(switchStatement);

    Node rootScriptNode = parseValidScript(switchStatement);
    Node blockNode = IR.block();

    Node firstCaseNode =
        stream(NodeUtil.preOrderIterable(rootScriptNode)).filter(Node::isCase).findFirst().get();

    firstCaseNode.addChildToFront(blockNode);
    // A CASE node is only allowed to have 2 children, 1 expression and 1 block.
    expectInvalid(rootScriptNode, Check.SCRIPT);
  }

  private void valid(String code) {
    testSame(code);
    assertThat(lastCheckViolationMessages).isEmpty();
  }

  /**
   * Parse and validate code for a script that should be valid.
   *
   * @return the SCRIPT Node created by the parsing.
   */
  private Node parseValidScript(String scriptCode) {
    testSame(scriptCode);
    assertThat(lastCheckViolationMessages).isEmpty();
    return getLastCompiler().getJsRoot().getFirstChild();
  }

  private void invalid(String code) {
    testSame(code);
    assertThat(lastCheckViolationMessages).isNotEmpty();
  }

  private enum Check {
    SCRIPT,
    STATEMENT,
    EXPRESSION
  }

  /**
   * Perform validation check.
   *
   * @param n tree of nodes ot check
   * @param level level at which the check starts
   * @return list of violations
   */
  private List<String> doCheck(Node n, Check level) {
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
    return lastCheckViolationMessages;
  }

  private void expectInvalid(Node n, Check level) {
    assertThat(doCheck(n, level)).isNotEmpty();
  }

  private void expectInvalid(Node n, Check level, String... validationMessages) {
    assertThat(doCheck(n, level)).containsExactlyElementsIn(validationMessages);
  }

  private void expectValid(Node n, Check level) {
    assertThat(doCheck(n, level)).isEmpty();
  }

  /**
   * Add source location information to a tree of nodes.
   *
   * <p>All non-ROOT nodes are expected to have at minimum a property indicating their original
   * source file. Make sure this is true for all of the nodes in this tree to avoid property
   * validation violations.
   */
  private void setTestSourceLocationForTree(Node n) {
    checkState(!n.isRoot(), "ROOT nodes don't get source files");
    n.setSourceFileForTesting("testcode");
    // make sure all child nodes get the same source file.
    n.srcrefTree(n);
  }

  /**
   * Tests that AstValidator checks for the given feature in the AST
   *
   * <p>This will raise an error if a) the AST parsed from {@code code} lacks {@code feature}, or b)
   * AstValidator does not validate {@code feature}'s presence in the AST.
   */
  private void testFeatureValidation(String code, Feature feature) {
    valid(code);
    Node script = getLastCompiler().getJsRoot().getFirstChild();

    // Remove `feature` from the SCRIPT node's feature set, checking that it was originally present,
    // and then validate that AstValidator errors because it expects `feature` to be present.
    FeatureSet currentFeatures = NodeUtil.getFeatureSetOfScript(script);
    assertThat(currentFeatures.contains(feature)).isTrue();

    script.putProp(Node.FEATURE_SET, currentFeatures.without(feature));
    expectInvalid(script, Check.SCRIPT);
  }
}
