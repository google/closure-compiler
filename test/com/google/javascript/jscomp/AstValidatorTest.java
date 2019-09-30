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

import static com.google.common.truth.Truth.assertThat;

import com.google.javascript.jscomp.AstValidator.ViolationHandler;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.SimpleSourceFile;
import com.google.javascript.rhino.StaticSourceFile.SourceKind;
import com.google.javascript.rhino.Token;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** @author johnlenz@google.com (John Lenz) */
@RunWith(JUnit4.class)
public final class AstValidatorTest extends CompilerTestCase {

  private boolean lastCheckWasValid = true;
  private boolean enableTypeInfoValidation = true;

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
    lastCheckWasValid = true;
    AstValidator astValidator =
        new AstValidator(
            compiler,
            new ViolationHandler() {
              @Override
              public void handleViolation(String message, Node n) {
                lastCheckWasValid = false;
              }
            },
            /* validateScriptFeatures= */ true);
    astValidator.setTypeValidationEnabled(enableTypeInfoValidation);
    return astValidator;
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    disableAstValidation();
    disableNormalize();
    disableLineNumberCheck();
    enableTypeCheck();
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

    Node c = new Node(Token.CLASS, IR.name("C"), IR.empty());
    Node members = new Node(Token.CLASS_MEMBERS);
    c.addChildToBack(members);
    expectValid(c, Check.STATEMENT);
    Node method1 = new Node(
        Token.MEMBER_FUNCTION_DEF, IR.function(IR.name(""), IR.paramList(), IR.block()));
    members.addChildToBack(method1);
    expectInvalid(c, Check.STATEMENT);

    members.detachChildren();

    // Invalid empty string
    Node method2 = Node.newString(Token.MEMBER_FUNCTION_DEF, "");
    method2.addChildToBack(IR.function(IR.name(""), IR.paramList(), IR.block()));
    members.addChildToBack(method2);

    expectInvalid(c, Check.STATEMENT);
  }

  @Test
  public void testForIn() {
    valid("for(var a in b);");
    valid("for(let a in b);");
    valid("for(const a in b);");
    valid("for(a in b);");
    valid("for(a in []);");
    valid("for(a in {});");
  }

  @Test
  public void testForOf() {
    valid("for(var a of b);");
    valid("for(let a of b);");
    valid("for(const a of b);");
    valid("for(a of b);");
    valid("for(a of []);");
    valid("for(a of /** @type {!Iterable<?>} */ ({}));");
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
  }

  @Test
  public void testQuestionableForIn() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    setExpectParseWarningsThisTest();
    valid("for(var a = 1 in b);");
  }

  @Test
  public void testDebugger() {
    valid("debugger;");
  }

  @Test
  public void testValidScript() {
    // Since we're building the AST by hand, there won't be any types on it.
    enableTypeInfoValidation = false;

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
    enableTypeInfoValidation = false;

    Node n = new Node(Token.RETURN);
    expectInvalid(n, Check.EXPRESSION);
    expectValid(n, Check.STATEMENT);
    expectInvalid(n, Check.SCRIPT);
  }

  @Test
  public void testValidExpression1() {
    // Since we're building the AST by hand, there won't be any types on it.
    enableTypeInfoValidation = false;

    Node n = new Node(Token.ARRAYLIT, new Node(Token.EMPTY));
    expectValid(n, Check.EXPRESSION);
    expectInvalid(n, Check.STATEMENT);
    expectInvalid(n, Check.SCRIPT);
  }

  @Test
  public void testValidExpression2() {
    // Since we're building the AST by hand, there won't be any types on it.
    enableTypeInfoValidation = false;

    Node n = new Node(Token.NOT, new Node(Token.TRUE));
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
    enableTypeInfoValidation = false;

    Node n = IR.constNode(IR.name("x"), IR.number(3));

    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    expectInvalid(n, Check.STATEMENT);

    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    expectValid(n, Check.STATEMENT);
  }

  @Test
  public void testInvalidLetLanguageLevel() {
    // Since we're building the AST by hand, there won't be any types on it.
    enableTypeInfoValidation = false;

    Node n = IR.let(IR.name("x"), IR.number(3));

    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    expectInvalid(n, Check.STATEMENT);

    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    expectValid(n, Check.STATEMENT);
  }

  @Test
  public void testNewTargetIsValidExpression() {
    // Since we're building the AST by hand, there won't be any types on it.
    enableTypeInfoValidation = false;

    Node n = new Node(Token.NEW_TARGET);
    expectValid(n, Check.EXPRESSION);
  }

  @Test
  public void testImportMetaIsValidExpression() {
    // Since we're building the AST by hand, there won't be any types on it.
    enableTypeInfoValidation = false;

    setAcceptedLanguage(LanguageMode.UNSUPPORTED);
    Node n = new Node(Token.IMPORT_META);
    expectValid(n, Check.EXPRESSION);
  }

  @Test
  public void testCastOnLeftSideOfAssign() {
    // Since we're building the AST by hand, there won't be any types on it.
    enableTypeInfoValidation = false;

    JSDocInfoBuilder jsdoc = new JSDocInfoBuilder(false);
    jsdoc.recordType(new JSTypeExpression(IR.string("number"), "<AstValidatorTest>"));
    Node n = IR.exprResult(
        new Node(
            Token.ASSIGN,
            IR.cast(IR.name("x"), jsdoc.build()),
            IR.number(0)));
    expectValid(n, Check.STATEMENT);
  }

  @Test
  public void testInvalidEmptyStatement() {
    Node n = new Node(Token.EMPTY, new Node(Token.TRUE));
    expectInvalid(n, Check.STATEMENT);
    n.detachChildren();
    expectValid(n, Check.STATEMENT);
  }

  @Test
  public void testInvalidNumberStatement() {
    // Since we're building the AST by hand, there won't be any types on it.
    enableTypeInfoValidation = false;

    Node n = IR.number(1);
    expectInvalid(n, Check.STATEMENT);
    n = IR.exprResult(n);
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
    enableTypeInfoValidation = false;

    setLanguage(LanguageMode.ECMASCRIPT_NEXT, LanguageMode.ECMASCRIPT5);
    Node awaitNode = new Node(Token.AWAIT);
    awaitNode.addChildToBack(IR.number(1));
    Node parentFunction =
        IR.function(IR.name("foo"), IR.paramList(), IR.block(IR.returnNode(awaitNode)));
    parentFunction.setIsAsyncFunction(true);
    expectValid(awaitNode, Check.EXPRESSION);
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
  public void testAwaitExpressionNoFunction() {
    // Since we're building the AST by hand, there won't be any types on it.
    enableTypeInfoValidation = false;

    setLanguage(LanguageMode.ECMASCRIPT_NEXT, LanguageMode.ECMASCRIPT5);
    Node n = new Node(Token.AWAIT);
    n.addChildToBack(IR.number(1));
    expectInvalid(n, Check.EXPRESSION);
  }

  @Test
  public void testInvalidArrayPattern0() {
    // Since we're building the AST by hand, there won't be any types on it.
    enableTypeInfoValidation = false;

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
    enableTypeInfoValidation = false;

    Node n = IR.assign(IR.objectPattern(IR.objectRest(IR.name("x"))), IR.objectlit());

    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    expectInvalid(n, Check.EXPRESSION);

    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2018);
    expectValid(n, Check.EXPRESSION);
  }

  @Test
  public void testInvalidArrayRestForLanguageLevel() {
    // Since we're building the AST by hand, there won't be any types on it.
    enableTypeInfoValidation = false;

    Node n = IR.assign(IR.arrayPattern(IR.iterRest(IR.name("x"))), IR.arraylit());

    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    expectInvalid(n, Check.EXPRESSION);

    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    expectValid(n, Check.EXPRESSION);
  }

  @Test
  public void testInvalidDestructuringAssignment() {
    // Since we're building the AST by hand, there won't be any types on it.
    enableTypeInfoValidation = false;

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
    enableTypeInfoValidation = false;

    Node c = new Node(Token.CLASS, IR.name("C"), IR.empty());
    Node members = new Node(Token.CLASS_MEMBERS);
    c.addChildToBack(members);
    expectValid(c, Check.STATEMENT);

    // Invalid getter with parameters
    Node getter1 = Node.newString(Token.GETTER_DEF, "prop");
    getter1.addChildToBack(IR.function(IR.name(""), IR.paramList(IR.name("foo")), IR.block()));
    members.addChildToBack(getter1);
    expectInvalid(c, Check.STATEMENT);

    members.detachChildren();

    // Invalid getter with function name
    Node getter2 = Node.newString(Token.GETTER_DEF, "prop");
    getter2.addChildToBack(IR.function(IR.name("foo"), IR.paramList(), IR.block()));
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
    members.addChildToBack(getter4);
    expectInvalid(c, Check.STATEMENT);
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
    enableTypeInfoValidation = false;

    Node c = new Node(Token.CLASS, IR.name("C"), IR.empty());
    Node members = new Node(Token.CLASS_MEMBERS);
    c.addChildToBack(members);
    expectValid(c, Check.STATEMENT);

    // Invalid setter with no parameters
    Node setter1 = Node.newString(Token.SETTER_DEF, "prop");
    setter1.addChildToBack(IR.function(IR.name(""), IR.paramList(), IR.block()));
    members.addChildToBack(setter1);
    expectInvalid(c, Check.STATEMENT);

    members.detachChildren();

    // Invalid setter with function name
    Node setter2 = Node.newString(Token.SETTER_DEF, "prop");
    setter2.addChildToBack(IR.function(IR.name("foo"), IR.paramList(IR.name("value")), IR.block()));
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
    testFeatureValidation("class C { constructor() { super(); } }", Feature.SUPER);
    testFeatureValidation("class C { f() { super.toString(); } }", Feature.SUPER);
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
    testFeatureValidation("export {x};", Feature.MODULES);
    testFeatureValidation("import {x} from './foo.js';", Feature.MODULES);
  }

  @Test
  public void testFeatureValidation_exponentOp() {
    testFeatureValidation("2 ** 3", Feature.EXPONENT_OP);
    testFeatureValidation("x **= 3;", Feature.EXPONENT_OP);
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
    enableTypeInfoValidation = false;

    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);

    Node n = new Node(Token.SCRIPT);
    n.setInputId(new InputId("something_input"));
    n.setStaticSourceFile(new SimpleSourceFile("something", SourceKind.STRONG));
    expectValid(n, Check.SCRIPT);

    n.addChildToFront(IR.let(IR.name("a"), IR.number(3)));
    expectInvalid(n, Check.SCRIPT);

    n.putProp(Node.FEATURE_SET, FeatureSet.BARE_MINIMUM.with(Feature.LET_DECLARATIONS));
    expectValid(n, Check.SCRIPT);
  }

  private void valid(String code) {
    testSame(code);
    assertThat(lastCheckWasValid).isTrue();
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
    assertThat(doCheck(n, level)).isFalse();
  }

  private void expectValid(Node n, Check level) {
    assertThat(doCheck(n, level)).isTrue();
  }

  /**
   * Tests that AstValidator checks for the given feature in the AST
   *
   * <p>This will raise an error if a) the AST parsed from {@code code} lacks {@code feature}, or
   * b) AstValidator does not validate {@code feature}'s presence in the AST.
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
