/*
 * Copyright 2018 The Closure Compiler Authors.
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
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.SyntacticScopeCreator.RedeclarationHandler;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AstFactoryTest {
  private static final Joiner LINE_JOINER = Joiner.on('\n');

  private Compiler compiler;

  @Before
  public void setUp() throws Exception {
    compiler = new Compiler();
  }

  private static String lines(String... lines) {
    return LINE_JOINER.join(lines);
  }

  private JSTypeRegistry getRegistry() {
    return compiler.getTypeRegistry();
  }

  private JSType getNativeType(JSTypeNative nativeType) {
    return getRegistry().getNativeType(nativeType);
  }

  private Node parseWithoutTypes(String source) {
    return parseWithoutTypes("", source);
  }

  private Node parseWithoutTypes(String externs, String source) {
    // parse the test code
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    compiler.init(
        ImmutableList.of(SourceFile.fromCode("externs", externs)),
        ImmutableList.of(SourceFile.fromCode("source", source)),
        options);
    compiler.parseInputs();

    return compiler.getJsRoot();
  }

  private Node parseAndAddTypes(String source) {
    return parseAndAddTypes("", source);
  }

  private Node parseAndAddTypes(String externs, String source) {
    parseWithoutTypes(externs, source);

    // then add types to the AST & do type checks
    // TODO(bradfordcsmith): Fail if there are type checking errors.
    TypeCheck typeCheck =
        new TypeCheck(
            compiler, compiler.getReverseAbstractInterpreter(), compiler.getTypeRegistry());
    typeCheck.processForTesting(compiler.getExternsRoot(), compiler.getJsRoot());
    return compiler.getJsRoot();
  }

  private AstFactory createTestAstFactory() {
    return AstFactory.createFactoryWithTypes(getRegistry());
  }

  private AstFactory createTestAstFactoryWithoutTypes() {
    return AstFactory.createFactoryWithoutTypes();
  }

  private Scope getScope(Node root) {
    // Normal passes use SyntacticScopeCreator, so that's what we use here.
    RedeclarationHandler redeclarationHandler =
        (Scope s, String name, Node n, CompilerInput input) -> {};
    SyntacticScopeCreator scopeCreator = new SyntacticScopeCreator(compiler, redeclarationHandler);
    return scopeCreator.createScope(root, null);
  }

  @Test
  public void testStringLiteral() {
    AstFactory astFactory = createTestAstFactory();

    Node stringLiteral = astFactory.createString("hello");
    assertNode(stringLiteral).hasType(Token.STRING);
    assertThat(stringLiteral.getString()).isEqualTo("hello");
    assertType(stringLiteral.getJSType()).isString();
  }

  @Test
  public void testNumberLiteral() {
    AstFactory astFactory = createTestAstFactory();

    Node numberLiteral = astFactory.createNumber(2112D);
    assertNode(numberLiteral).hasType(Token.NUMBER);
    assertThat(numberLiteral.getDouble()).isEqualTo(2112D);
    assertType(numberLiteral.getJSType()).isNumber();
  }

  @Test
  public void testBooleanLiteral() {
    AstFactory astFactory = createTestAstFactory();

    Node trueNode = astFactory.createBoolean(true);
    assertNode(trueNode).hasType(Token.TRUE);
    assertType(trueNode.getJSType()).isBoolean();

    Node falseNode = astFactory.createBoolean(false);
    assertNode(falseNode).hasType(Token.FALSE);
    assertType(falseNode.getJSType()).isBoolean();
  }

  @Test
  public void testCreateArgumentsReference() {
    // Make sure the compiler's type registry includes the standard externs definition for
    // Arguments.
    parseAndAddTypes(new TestExternsBuilder().addArguments().build(), "");

    AstFactory astFactory = createTestAstFactory();

    Node argumentsNode = astFactory.createArgumentsReference();
    assertNode(argumentsNode).matchesName("arguments");
    assertType(argumentsNode.getJSType()).isEqualTo(getRegistry().getGlobalType("Arguments"));
  }

  @Test
  public void testCreateNameWithJSType() {
    AstFactory astFactory = createTestAstFactory();

    Node x = astFactory.createName("x", getNativeType(JSTypeNative.STRING_TYPE));
    assertNode(x).hasType(Token.NAME);
    assertThat(x.getString()).isEqualTo("x");
    assertType(x.getJSType()).isString();
  }

  @Test
  public void testCreateNameWithNativeType() {
    AstFactory astFactory = createTestAstFactory();

    Node x = astFactory.createName("x", JSTypeNative.STRING_TYPE);
    assertNode(x).hasType(Token.NAME);
    assertThat(x.getString()).isEqualTo("x");
    assertType(x.getJSType()).isString();
  }

  @Test
  public void testCreateNameFromScope() {
    AstFactory astFactory = createTestAstFactory();

    Node root = parseAndAddTypes("/** @type {string} */ const X = 'hi';");
    Scope scope = getScope(root);

    Node x = astFactory.createName(scope, "X");
    assertNode(x).hasType(Token.NAME);
    assertThat(x.getString()).isEqualTo("X");
    assertType(x.getJSType()).isString();
  }

  @Test
  public void testCreateThisReference() {
    AstFactory astFactory = createTestAstFactory();

    Node x = astFactory.createThis(getNativeType(JSTypeNative.STRING_TYPE));
    assertNode(x).hasType(Token.THIS);
    assertType(x.getJSType()).isString();
  }

  @Test
  public void testCreateSuperReference() {
    AstFactory astFactory = createTestAstFactory();

    Node x = astFactory.createSuper(getNativeType(JSTypeNative.STRING_TYPE));
    assertNode(x).hasType(Token.SUPER);
    assertType(x.getJSType()).isString();
  }

  @Test
  public void createThisForFunction() {
    AstFactory astFactory = createTestAstFactory();

    Node root =
        parseAndAddTypes(
            lines(
                "class C {", //
                "  method() {",
                "    this;", // created `this` should match this one
                "  }",
                "}",
                ""));

    Node methodFunction =
        root.getFirstChild() // script
            .getFirstChild() // class
            .getLastChild() // class members
            .getFirstChild() // class method member function def
            .getOnlyChild(); // method function

    Node existingThis =
        methodFunction
            .getLastChild() // method function body
            .getFirstChild() // expr_result
            .getOnlyChild(); // this

    Node newThis = astFactory.createThisForFunction(methodFunction);
    assertNode(newThis).isEqualTo(existingThis);
    assertNode(newThis).hasJSTypeThat().isEqualTo(existingThis.getJSTypeRequired());
  }

  @Test
  public void createSuperForFunction() {
    AstFactory astFactory = createTestAstFactory();

    Node root =
        parseAndAddTypes(
            lines(
                "class A {}",
                "class B extends A {", //
                "  method() {",
                "    super.method();", // created `super` should match this one
                "  }",
                "}",
                ""));

    Node a =
        root.getFirstChild() // script
            .getFirstChild(); // class A

    Node methodFunction =
        root.getFirstChild() // script
            .getSecondChild() // class B
            .getLastChild() // class members
            .getFirstChild() // class method member function def
            .getOnlyChild(); // method function

    Node existingSuper =
        methodFunction
            .getLastChild() // method function body
            .getFirstChild() // expr_result
            .getOnlyChild() // call "method"
            .getFirstFirstChild(); // super

    Node newSuper = astFactory.createSuperForFunction(methodFunction);
    assertNode(newSuper).isEqualTo(existingSuper);
    assertNode(newSuper)
        .hasJSTypeThat()
        .isEqualTo(a.getJSType().assertFunctionType().getInstanceType());
  }

  @Test
  public void createThisForFunctionWithCast() {
    AstFactory astFactory = createTestAstFactory();

    // When the function literal is inside of a cast, it can end up with a non-Function type.
    // Confirm that AstFactory correctly retrieves the type from before the cast
    Node root = parseAndAddTypes("const funcAsUnknown = /** @type ? */ (function() { this; });");

    Node functionNode =
        root.getFirstChild() // script
            .getFirstChild() // const declaration
            .getOnlyChild() // funcAsUnknown name
            .getOnlyChild() // cast node
            .getOnlyChild(); // function node

    Node existingThis =
        functionNode
            .getLastChild() // method function body
            .getFirstChild() // expr_result
            .getOnlyChild(); // this

    Node newThis = astFactory.createThisForFunction(functionNode);
    assertNode(newThis).isEqualTo(existingThis);
    assertNode(newThis).hasJSTypeThat().isEqualTo(existingThis.getJSTypeRequired());
  }

  @Test
  public void createThisAliasReferenceForFunction() {
    AstFactory astFactory = createTestAstFactory();

    Node root =
        parseAndAddTypes(
            lines(
                "class C {", //
                "  method() {}",
                "}",
                ""));

    Node classNode =
        root.getFirstChild() // script
            .getFirstChild(); // class node
    ObjectType instanceType = classNode.getJSTypeRequired().assertFunctionType().getInstanceType();

    Node methodFunction =
        classNode
            .getLastChild() // class members
            .getFirstChild() // member method definition
            .getOnlyChild(); // method function

    Node thisAlias = astFactory.createThisAliasReferenceForFunction("thisAlias", methodFunction);
    assertNode(thisAlias).hasType(Token.NAME);
    assertThat(thisAlias.getString()).isEqualTo("thisAlias");
    assertNode(thisAlias).hasJSTypeThat().isEqualTo(instanceType);
  }

  @Test
  public void createThisAliasReferenceForFunctionWithoutTypes() {
    AstFactory astFactory = createTestAstFactoryWithoutTypes();

    Node root =
        parseWithoutTypes(
            lines(
                "class C {", //
                "  method() {}",
                "}",
                ""));

    Node methodFunction =
        root.getFirstChild() // script
            .getFirstChild() // class
            .getLastChild() // class members
            .getFirstChild() // member method def
            .getOnlyChild(); // member function

    Node thisAlias = astFactory.createThisAliasReferenceForFunction("thisAlias", methodFunction);
    assertNode(thisAlias).hasType(Token.NAME);
    assertThat(thisAlias.getString()).isEqualTo("thisAlias");
    assertThat(thisAlias.getJSType()).isNull();
  }

  @Test
  public void testCreateGetpropJscompGlobal() {
    AstFactory astFactory = createTestAstFactory();

    // TODO(bradfordcsmith): We shouldn't need this special case.
    Node jscompNode = astFactory.createName("$jscomp", JSTypeNative.UNKNOWN_TYPE);
    Node jscompDotGlobal = astFactory.createGetProp(jscompNode, "global");

    assertNode(jscompDotGlobal).hasType(Token.GETPROP);
    Node firstChild = jscompDotGlobal.getFirstChild();
    assertThat(firstChild).isEqualTo(jscompNode);
    Node secondChild = firstChild.getNext();
    assertNode(secondChild).hasType(Token.STRING);
    assertThat(secondChild.getString()).isEqualTo("global");
    assertThat(secondChild.getNext()).isNull(); // only 2 children

    assertType(jscompDotGlobal.getJSType()).isEqualTo(getNativeType(JSTypeNative.GLOBAL_THIS));
  }

  @Test
  public void testCreateGetpropForObjectToString() {
    // It's convenient to use Object.toString for testing, since it's a native type we can just
    // look up without having to parse code.
    AstFactory astFactory = createTestAstFactory();

    ObjectType nativeObjectType = getNativeType(JSTypeNative.OBJECT_TYPE).toObjectType();
    Node obj = astFactory.createName("obj", nativeObjectType);
    Node objDotToString = astFactory.createGetProp(obj, "toString");

    assertNode(objDotToString).hasType(Token.GETPROP);
    Node firstChild = objDotToString.getFirstChild();
    assertThat(firstChild).isEqualTo(obj);
    Node secondChild = firstChild.getNext();
    assertNode(secondChild).hasType(Token.STRING);
    assertThat(secondChild.getString()).isEqualTo("toString");
    assertThat(secondChild.getNext()).isNull(); // only 2 children

    assertType(objDotToString.getJSType()).isEqualTo(nativeObjectType.getPropertyType("toString"));
  }

  @Test
  public void testCreateGetpropForTemplatizedType() {
    AstFactory astFactory = createTestAstFactory();

    // get the Bar<number> type
    Node root =
        parseAndAddTypes(
            lines(
                "/** @interface @template T */ function Bar() {} ",
                "/** @type {T} */ Bar.prototype.property;",
                "var /** !Bar<number> */ b;"));
    Node bName = root.getFirstChild().getLastChild().getOnlyChild();
    assertNode(bName).matchesName("b");
    JSType barOfNumber = bName.getJSType();

    Node barName = astFactory.createName("bar", barOfNumber);
    assertType(barName.getJSType()).toStringIsEqualTo("Bar<number>");

    Node propertyAccess = astFactory.createGetProp(barName, "property");
    assertNode(propertyAccess).hasToken(Token.GETPROP);
    // Verify that the property is typed as `number` instead of `?` or `T`
    assertType(propertyAccess.getJSType()).isEqualTo(getNativeType(JSTypeNative.NUMBER_TYPE));
  }

  @Test
  public void testCreateStringKey() {
    AstFactory astFactory = createTestAstFactory();

    Node numberNode = astFactory.createNumber(2112D);
    Node stringKeyNode = astFactory.createStringKey("key", numberNode);

    assertNode(stringKeyNode).hasType(Token.STRING_KEY);
    assertThat(stringKeyNode.getString()).isEqualTo("key");
    assertThat(stringKeyNode.children()).containsExactly(numberNode);
    assertType(stringKeyNode.getJSType()).isNumber();
  }

  @Test
  public void testCreateComputedProperty() {
    AstFactory astFactory = createTestAstFactory();

    Node numberNode = astFactory.createNumber(2112D);
    Node stringLiteral = astFactory.createString("string literal key");
    Node computedPropertyNode = astFactory.createComputedProperty(stringLiteral, numberNode);

    assertNode(computedPropertyNode).hasType(Token.COMPUTED_PROP);
    assertThat(computedPropertyNode.children())
        .containsExactly(stringLiteral, numberNode)
        .inOrder();
    assertType(computedPropertyNode.getJSType()).isNumber();
  }

  @Test
  public void testCreateGetElem() {
    AstFactory astFactory = createTestAstFactory();

    Node objectName = astFactory.createName("obj", getNativeType(JSTypeNative.OBJECT_TYPE));
    Node stringLiteral = astFactory.createString("string literal key");
    Node getElemNode = astFactory.createGetElem(objectName, stringLiteral);

    assertNode(getElemNode).hasType(Token.GETELEM);
    assertThat(getElemNode.children()).containsExactly(objectName, stringLiteral).inOrder();
    // TODO(bradfordcsmith): When receiver is an Array<T> or an Object<K, V>, use the template type
    // here.
    assertType(getElemNode.getJSType()).isUnknown();
  }

  @Test
  public void testCreateComma() {
    AstFactory astFactory = createTestAstFactory();

    Node stringNode = astFactory.createString("hi");
    Node numberNode = astFactory.createNumber(2112D);
    Node commaNode = astFactory.createComma(stringNode, numberNode);

    assertNode(commaNode).hasType(Token.COMMA);
    assertThat(commaNode.children()).containsExactly(stringNode, numberNode).inOrder();
    assertType(commaNode.getJSType()).isNumber();
  }

  @Test
  public void testCreateCommas() {
    AstFactory astFactory = createTestAstFactory();

    Node stringNode = astFactory.createString("hi");
    Node numberNode = astFactory.createNumber(2112D);
    Node trueNode = astFactory.createBoolean(true);
    Node falseNode = astFactory.createBoolean(false);
    // "hi", 2112, true, false
    Node stringNumberTrueFalse =
        astFactory.createCommas(stringNode, numberNode, trueNode, falseNode);

    // ("hi", 2112, true), false
    assertNode(stringNumberTrueFalse).hasType(Token.COMMA);
    Node stringNumberTrue = stringNumberTrueFalse.getFirstChild();
    assertThat(stringNumberTrueFalse.children())
        .containsExactly(stringNumberTrue, falseNode)
        .inOrder();
    assertType(stringNumberTrueFalse.getJSType()).isBoolean();

    // ("hi", 2112), true
    Node stringNumber = stringNumberTrue.getFirstChild();
    assertThat(stringNumberTrue.children()).containsExactly(stringNumber, trueNode).inOrder();
    assertType(stringNumberTrue.getJSType()).isBoolean();

    // "hi", 2112
    assertThat(stringNumber.children()).containsExactly(stringNode, numberNode);
    assertType(stringNumber.getJSType()).isNumber();
  }

  @Test
  public void testCreateIn() {
    AstFactory astFactory = createTestAstFactory();

    Node prop = astFactory.createString("prop");
    Node obj = IR.name("obj"); // TODO(bradfordcsmith): This should have a type on it.
    Node n = astFactory.createIn(prop, obj);
    assertNode(n).hasType(Token.IN);
    assertType(n.getJSType()).isBoolean();
    assertThat(n.children()).containsExactly(prop, obj).inOrder();
  }

  @Test
  public void testCreateAnd() {
    AstFactory astFactory = createTestAstFactory();

    Node numberLiteral = astFactory.createNumber(2112D);
    Node stringLiteral = astFactory.createString("hello");
    Node andNode = astFactory.createAnd(numberLiteral, stringLiteral);

    assertNode(andNode).hasType(Token.AND);
    assertThat(andNode.children()).containsExactly(numberLiteral, stringLiteral).inOrder();
    assertType(andNode.getJSType()).toStringIsEqualTo("(number|string)");
  }

  @Test
  public void testCreateAndWithAlwaysFalsyLhs() {
    AstFactory astFactory = createTestAstFactory();

    Node nullNode = astFactory.createNull();
    Node stringLiteral = astFactory.createString("hello");
    Node andNode = astFactory.createAnd(nullNode, stringLiteral);

    assertNode(andNode).hasType(Token.AND);
    assertThat(andNode.children()).containsExactly(nullNode, stringLiteral).inOrder();
    // NULL_TYPE doesn't contain any truthy values, so its type is the only possibility
    assertType(andNode.getJSType()).toStringIsEqualTo("null");
  }

  @Test
  public void testCreateAndWithAlwaysTruthyLhs() {
    AstFactory astFactory = createTestAstFactory();

    Node nonNullObject = astFactory.createName("nonNullObject", JSTypeNative.OBJECT_TYPE);
    Node stringLiteral = astFactory.createString("hello");
    Node andNode = astFactory.createAnd(nonNullObject, stringLiteral);

    assertNode(andNode).hasType(Token.AND);
    assertThat(andNode.children()).containsExactly(nonNullObject, stringLiteral).inOrder();
    // OBJECT_TYPE doesn't contain any falsy values, so the RHS type is the only possibility
    assertType(andNode.getJSType()).toStringIsEqualTo("string");
  }

  @Test
  public void testCreateOr() {
    AstFactory astFactory = createTestAstFactory();

    Node numberLiteral = astFactory.createNumber(2112D);
    Node stringLiteral = astFactory.createString("hello");
    Node andNode = astFactory.createOr(numberLiteral, stringLiteral);

    assertNode(andNode).hasType(Token.OR);
    assertThat(andNode.children()).containsExactly(numberLiteral, stringLiteral).inOrder();
    assertType(andNode.getJSType()).toStringIsEqualTo("(number|string)");
  }

  @Test
  public void testCreateOrWithAlwaysFalsyLhs() {
    AstFactory astFactory = createTestAstFactory();

    Node nullLiteral = astFactory.createNull();
    Node stringLiteral = astFactory.createString("hello");
    Node andNode = astFactory.createOr(nullLiteral, stringLiteral);

    assertNode(andNode).hasType(Token.OR);
    assertThat(andNode.children()).containsExactly(nullLiteral, stringLiteral).inOrder();
    // NULL_TYPE doesn't contain any truthy values, so the RHS type is the only possibility
    assertType(andNode.getJSType()).toStringIsEqualTo("string");
  }

  @Test
  public void testCreateOrWithAlwaysTruthyLhs() {
    AstFactory astFactory = createTestAstFactory();

    Node nonNullObject = astFactory.createName("nonNullObject", JSTypeNative.OBJECT_TYPE);
    Node stringLiteral = astFactory.createString("hello");
    Node andNode = astFactory.createOr(nonNullObject, stringLiteral);

    assertNode(andNode).hasType(Token.OR);
    assertThat(andNode.children()).containsExactly(nonNullObject, stringLiteral).inOrder();
    // OBJECT_TYPE doesn't contain any falsy values, so the RHS won't be evaluated
    assertType(andNode.getJSType()).toStringIsEqualTo("Object");
  }

  @Test
  public void testCreateFreeCall() {
    AstFactory astFactory = createTestAstFactory();

    Node root =
        parseAndAddTypes(
            lines(
                "/**",
                " * @param {string} arg1",
                " * @param {number} arg2",
                " * @return {string}",
                " */",
                "function foo() { return arg1; }",
                ""));
    Scope scope = getScope(root);

    // foo("hi", 2112)
    Node callee = astFactory.createName(scope, "foo");
    Node arg1 = astFactory.createString("hi");
    Node arg2 = astFactory.createNumber(2112D);
    Node callNode = astFactory.createCall(callee, arg1, arg2);

    assertNode(callNode).hasType(Token.CALL);
    assertThat(callNode.getBooleanProp(Node.FREE_CALL)).isTrue();
    assertThat(callNode.children()).containsExactly(callee, arg1, arg2).inOrder();
    assertType(callNode.getJSType()).isString();
  }

  @Test
  public void testCreateMethodCall() {
    AstFactory astFactory = createTestAstFactory();

    Node root =
        parseAndAddTypes(
            lines(
                "class Foo {",
                "  /**",
                "   * @param {string} arg1",
                "   * @param {number} arg2",
                "   * @return {string}",
                "   */",
                "  method(arg1, arg2) { return arg1; }",
                "}",
                "const foo = new Foo();"));
    Scope scope = getScope(root);

    // foo.method("hi", 2112)
    Node callee = astFactory.createQName(scope, "foo.method");
    Node arg1 = astFactory.createString("hi");
    Node arg2 = astFactory.createNumber(2112D);
    Node callNode = astFactory.createCall(callee, arg1, arg2);

    assertNode(callNode).hasType(Token.CALL);
    assertThat(callNode.getBooleanProp(Node.FREE_CALL)).isFalse();
    assertThat(callNode.children()).containsExactly(callee, arg1, arg2).inOrder();
    assertType(callNode.getJSType()).isString();
  }

  @Test
  public void testCreateStaticMethodCall() {
    // NOTE: This method is testing both createCall() and createQName()
    AstFactory astFactory = createTestAstFactory();

    Node root =
        parseAndAddTypes(
            lines(
                "class Foo {",
                "  /**",
                "   * @param {string} arg1",
                "   * @param {number} arg2",
                "   * @return {string}",
                "   */",
                "  static method(arg1, arg2) { return arg1; }",
                "}"));
    Scope scope = getScope(root);

    // Foo.method("hi", 2112)
    Node callee = astFactory.createQName(scope, "Foo.method");
    Node arg1 = astFactory.createString("hi");
    Node arg2 = astFactory.createNumber(2112D);
    Node callNode = astFactory.createCall(callee, arg1, arg2);

    assertNode(callNode).hasType(Token.CALL);
    assertThat(callNode.getBooleanProp(Node.FREE_CALL)).isFalse();
    assertThat(callNode.children()).containsExactly(callee, arg1, arg2).inOrder();
    assertType(callNode.getJSType()).isString();
  }

  @Test
  public void testCreateStaticMethodCallDotCall() {
    // NOTE: This method is testing both createCall() and createQName()
    AstFactory astFactory = createTestAstFactory();

    Node root =
        parseAndAddTypes(
            lines(
                "class Foo {",
                "  /**",
                "   * @param {string} arg1",
                "   * @param {number} arg2",
                "   * @return {string}",
                "   */",
                "  static method(arg1, arg2) { return arg1; }",
                "}"));
    Scope scope = getScope(root);

    // Foo.method.call(null, "hi", 2112)
    Node callee = astFactory.createQName(scope, "Foo.method.call");
    Node nullNode = astFactory.createNull();
    Node arg1 = astFactory.createString("hi");
    Node arg2 = astFactory.createNumber(2112D);
    Node callNode = astFactory.createCall(callee, nullNode, arg1, arg2);

    assertNode(callNode).hasType(Token.CALL);
    assertThat(callNode.getBooleanProp(Node.FREE_CALL)).isFalse();
    assertThat(callNode.children()).containsExactly(callee, nullNode, arg1, arg2).inOrder();
    assertType(callNode.getJSType()).isString();
  }

  @Test
  public void testCreateConstructorCall() {
    AstFactory astFactory = createTestAstFactory();

    Node root =
        parseAndAddTypes(
            lines(
                "class A {}", //
                "class B extends A {}"));

    Node classBNode =
        root.getFirstChild() // script node
            .getSecondChild();
    Node classBExtendsNode = classBNode.getSecondChild();
    FunctionType classBType = classBNode.getJSType().toMaybeFunctionType();
    ObjectType classBInstanceType = classBType.getInstanceType();

    // simulate creating a call to super() intended to go in a constructor for B
    Node callee = IR.superNode().setJSType(classBExtendsNode.getJSType());
    Node arg1 = astFactory.createString("hi");
    Node arg2 = astFactory.createNumber(2112D);
    Node callNode = astFactory.createConstructorCall(classBType, callee, arg1, arg2);

    assertNode(callNode).hasType(Token.CALL);
    assertThat(callNode.getBooleanProp(Node.FREE_CALL)).isTrue();
    assertThat(callNode.children()).containsExactly(callee, arg1, arg2).inOrder();
    assertType(callNode.getJSType()).isEqualTo(classBInstanceType);
  }

  @Test
  public void createObjectGetPrototypeOfCallWithoutTypes() {
    AstFactory astFactory = createTestAstFactoryWithoutTypes();

    Node nameNode = astFactory.createName("name", (JSType) null);
    Node callObjectDotGetPrototypeOfOnName = astFactory.createObjectGetPrototypeOfCall(nameNode);
    // expect
    // `Object.getPrototypeOf(name)`
    assertNode(callObjectDotGetPrototypeOfOnName).hasType(Token.CALL);
    assertThat(callObjectDotGetPrototypeOfOnName.getJSType()).isNull();

    Node callee = callObjectDotGetPrototypeOfOnName.getFirstChild();
    assertNode(callee).matchesQualifiedName("Object.getPrototypeOf");
    Node firstArg = callee.getNext();
    assertThat(firstArg).isEqualTo(nameNode);
  }

  @Test
  public void createObjectGetPrototypeOfCallOnInstance() {
    AstFactory astFactory = createTestAstFactory();

    // NOTE: the "WithoutTypes" version of this test checks that the method creates
    // `Object.getPrototypeOf(someThing)`. This test case is about making sure we get the
    // right types applied to the call
    Node root =
        parseAndAddTypes(
            lines(
                "class A {}", //
                "A.prototype;", // convenient way to grab class prototype
                "const a = new A();",
                ""));

    Scope scope = getScope(root);

    // `class A {}`
    Node classANode =
        root.getFirstChild() // script
            .getFirstChild();

    // `A.prototype`
    Node classADotPrototype =
        classANode
            .getNext() // expr_result
            .getOnlyChild();

    Node aNameNode = astFactory.createName(scope, "a");
    Node callObjectDotGetPrototypeOf = astFactory.createObjectGetPrototypeOfCall(aNameNode);
    assertNode(callObjectDotGetPrototypeOf)
        .hasJSTypeThat()
        .isEqualTo(classADotPrototype.getJSTypeRequired());
  }

  @Test
  public void createObjectGetPrototypeOfCallOnClass() {
    AstFactory astFactory = createTestAstFactory();

    // NOTE: the "WithoutTypes" version of this test checks that the method creates
    // `Object.getPrototypeOf(someThing)`. This test case is about making sure we get the
    // right types applied to the call
    Node root =
        parseAndAddTypes(
            lines(
                "class A {}", //
                "class B extends A {}",
                ""));

    Scope scope = getScope(root);

    // `class A {}`
    Node classANode =
        root.getFirstChild() // script
            .getFirstChild();

    Node classBNameNode = astFactory.createName(scope, "B");
    Node callObjectDotGetPrototypeOf = astFactory.createObjectGetPrototypeOfCall(classBNameNode);
    assertNode(callObjectDotGetPrototypeOf)
        .hasJSTypeThat()
        .isEqualTo(classANode.getJSTypeRequired());
  }

  @Test
  public void testCreateEmptyFunction() {
    AstFactory astFactory = createTestAstFactory();

    // just a quick way to get a valid function type
    Node root = parseAndAddTypes("function foo() {}");
    JSType functionType =
        root.getFirstChild() // script
            .getFirstChild() // function
            .getJSType();

    Node emptyFunction = astFactory.createEmptyFunction(functionType);
    assertNode(emptyFunction).hasToken(Token.FUNCTION);
    assertType(emptyFunction.getJSType()).isEqualTo(functionType);
  }

  @Test
  public void testCreateFunction() {
    AstFactory astFactory = createTestAstFactory();

    // just a quick way to get a valid function type
    Node root = parseAndAddTypes("function foo() {}");
    JSType functionType =
        root.getFirstChild() // script
            .getFirstChild() // function
            .getJSType();

    Node paramList = IR.paramList();
    Node body = IR.block();

    Node functionNode = astFactory.createFunction("bar", paramList, body, functionType);
    assertNode(functionNode).hasToken(Token.FUNCTION);
    assertType(functionNode.getJSType()).isEqualTo(functionType);
    Node functionNameNode = functionNode.getFirstChild();
    assertNode(functionNameNode).isName("bar");
    assertThat(functionNode.children())
        .containsExactly(functionNameNode, paramList, body)
        .inOrder();
  }

  @Test
  public void testCreateMemberFunctionDef() {
    AstFactory astFactory = createTestAstFactory();

    // just a quick way to get a valid function type
    Node root = parseAndAddTypes("function foo() {}");
    JSType functionType =
        root.getFirstChild() // script
            .getFirstChild() // function
            .getJSType();

    Node paramList = IR.paramList();
    Node body = IR.block();
    Node functionNode = astFactory.createFunction("", paramList, body, functionType);

    Node memberFunctionDef = astFactory.createMemberFunctionDef("bar", functionNode);
    assertNode(memberFunctionDef).hasToken(Token.MEMBER_FUNCTION_DEF);
    assertThat(memberFunctionDef.getString()).isEqualTo("bar");
    assertType(memberFunctionDef.getJSType()).isEqualTo(functionType);
  }

  @Test
  public void testCreateZeroArgFunction() {
    AstFactory astFactory = createTestAstFactory();

    // just a quick way to get a valid function type
    Node root = parseAndAddTypes("/** @return {number} */ function foo() {}");
    JSType functionType =
        root.getFirstChild() // script
            .getFirstChild() // function
            .getJSType();

    Node body = IR.block();
    JSType returnType = getNativeType(JSTypeNative.NUMBER_TYPE);
    Node functionNode = astFactory.createZeroArgFunction("bar", body, returnType);

    assertType(functionNode.getJSType()).isEqualTo(functionType);
    assertNode(functionNode).hasToken(Token.FUNCTION);
  }

  @Test
  public void createZeroArgGeneratorFunction() {
    AstFactory astFactory = createTestAstFactory();

    // just a quick way to get a valid generator function type
    Node root = parseAndAddTypes("/** @return {number} */ function *foo() { return 1; }");
    JSType functionType =
        root.getFirstChild() // script
            .getFirstChild() // function
            .getJSType();

    Node body = IR.block();
    JSType returnType = getNativeType(JSTypeNative.NUMBER_TYPE);
    Node functionNode = astFactory.createZeroArgGeneratorFunction("bar", body, returnType);

    assertType(functionNode.getJSType()).isEqualTo(functionType);
    assertNode(functionNode).hasToken(Token.FUNCTION);
  }

  @Test
  public void testCreateZeroArgFunctionForExpression() {
    AstFactory astFactory = createTestAstFactory();

    // quick way to get a function to contain the new arrow function and another arrow function
    // to compare types with
    Node root =
        parseAndAddTypes(
            lines(
                "",
                "class C {",
                "  /** @return {number} */",
                "  foo() {",
                // TODO(b/118435472): compiler should be able to infer the return type
                "    /**",
                "     * @return {number}",
                "     */",
                "    const orig = () => 1;", // new arrow function exactly like this one
                "  }",
                "}",
                ""));

    Node existingArrowFunctionNode =
        root.getFirstChild() // script
            .getFirstChild() // class
            .getLastChild() // class members
            .getFirstChild() // foo member function def
            .getFirstChild() // foo function node
            .getLastChild() // foo function body
            .getFirstChild() // const
            .getOnlyChild() // orig name node
            .getOnlyChild();

    Node expression = astFactory.createNumber(1);
    Node newArrowFunctionNode = astFactory.createZeroArgArrowFunctionForExpression(expression);

    assertNode(newArrowFunctionNode).isEqualTo(existingArrowFunctionNode);
    assertNode(newArrowFunctionNode)
        .hasJSTypeThat()
        .isEqualTo(existingArrowFunctionNode.getJSTypeRequired());
  }

  @Test
  public void testCreateEmptyObjectLit() {
    AstFactory astFactory = createTestAstFactory();

    // just a quick way to get a valid object literal type
    Node root = parseAndAddTypes("({})");
    JSType objectLitType =
        root.getFirstChild() // script
            .getFirstChild() // expression result
            .getOnlyChild() // object literal
            .getJSType();

    Node objectLit = astFactory.createEmptyObjectLit();

    assertType(objectLit.getJSType()).toStringIsEqualTo("{}");
    assertThat(objectLit.getJSType()).isInstanceOf(objectLitType.getClass());
    assertNode(objectLit).hasToken(Token.OBJECTLIT);
    assertNode(objectLit).hasChildren(false);
  }

  @Test
  public void testCreateDelProp() {
    AstFactory astFactory = createTestAstFactory();

    Node getprop = IR.getprop(IR.name("obj"), IR.string("prop"));

    Node delprop = astFactory.createDelProp(getprop);
    assertNode(delprop).hasToken(Token.DELPROP);
    assertType(delprop.getJSType()).isEqualTo(getNativeType(JSTypeNative.BOOLEAN_TYPE));
    assertNode(delprop).hasChildren(true);
  }

  @Test
  public void testCreateSheq() {
    AstFactory astFactory = createTestAstFactory();

    Node left = IR.string("left");
    Node right = IR.number(0);

    Node sheq = astFactory.createSheq(left, right);
    assertNode(sheq).hasToken(Token.SHEQ);
    assertType(sheq.getJSType()).isEqualTo(getNativeType(JSTypeNative.BOOLEAN_TYPE));
  }

  @Test
  public void testCreateHook() {
    AstFactory astFactory = createTestAstFactory();
    JSType stringType = getNativeType(JSTypeNative.STRING_TYPE);
    JSType numberType = getNativeType(JSTypeNative.NUMBER_TYPE);

    Node condition = IR.falseNode();
    Node left = IR.name("left").setJSType(stringType);
    Node right = IR.number(0).setJSType(numberType);

    Node hook = astFactory.createHook(condition, left, right);
    assertNode(hook).hasToken(Token.HOOK);
    assertType(hook.getJSType()).isEqualTo(getRegistry().createUnionType(stringType, numberType));
  }

  @Test
  public void testCreateArraylit() {
    // Given
    AstFactory astFactory = createTestAstFactory();
    JSType numberType = getNativeType(JSTypeNative.NUMBER_TYPE);

    Node first = IR.number(0).setJSType(numberType);
    Node second = IR.number(1).setJSType(numberType);
    Node third = IR.number(2).setJSType(numberType);

    Node expected =
        parseAndAddTypes("[0, 1, 2]")
            .getFirstChild() // Script
            .getFirstChild() // Expression
            .getFirstChild(); // Array

    // When
    Node array = astFactory.createArraylit(first, second, third);

    // Then
    assertNode(array).isEquivalentTo(expected);
    assertType(array.getJSType()).isEqualTo(expected.getJSType());
  }

  @Test
  public void testCreateNewNode() {
    // Given
    AstFactory astFactory = createTestAstFactory();
    JSType numberType = getNativeType(JSTypeNative.NUMBER_TYPE);

    Node first = IR.number(0).setJSType(numberType);
    Node second = IR.number(1).setJSType(numberType);

    Node classNode =
        parseAndAddTypes(
                lines(
                    "class Example { constructor(arg0, arg1) {} }", //
                    "new Example(0, 1);"))
            .getFirstChild() // Script
            .getFirstChild(); // class

    Node expected =
        classNode
            .getNext() // ExpressionResult
            .getFirstChild(); // NewExpression

    // When
    Node newExpr =
        astFactory.createNewNode(
            astFactory.createName("Example", classNode.getJSType()), first, second);

    // Then
    assertNode(newExpr).isEquivalentTo(expected);
    assertType(newExpr.getJSType()).isEqualTo(expected.getJSType());
  }
}
