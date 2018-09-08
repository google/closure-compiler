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
import com.google.javascript.jscomp.Es6SyntacticScopeCreator.RedeclarationHandler;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import junit.framework.TestCase;

public class AstFactoryTest extends TestCase {
  private static final Joiner LINE_JOINER = Joiner.on('\n');

  private Compiler compiler;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
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

  private Node parseAndAddTypes(String source) {
    return parseAndAddTypes("", source);
  }

  private Node parseAndAddTypes(String externs, String source) {
    // parse the test code
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    compiler.init(
        ImmutableList.of(SourceFile.fromCode("externs", externs)),
        ImmutableList.of(SourceFile.fromCode("source", source)),
        options);
    compiler.parseInputs();

    // then add types to the AST
    TypedScopeCreator typedScopeCreator = new TypedScopeCreator(compiler);
    TypedScope topScope = typedScopeCreator.createScope(compiler.getRoot(), /* parent= */ null);
    TypeInferencePass typeInferencePass =
        new TypeInferencePass(
            compiler, compiler.getReverseAbstractInterpreter(), topScope, typedScopeCreator);
    typeInferencePass.process(compiler.getExternsRoot(), compiler.getJsRoot());
    return compiler.getJsRoot();
  }

  private AstFactory createTestAstFactory() {
    return AstFactory.createFactoryWithTypes(getRegistry());
  }

  private Scope getScope(Node root) {
    // Normal passes use Es6SyntacticScopeCreator, so that's what we use here.
    RedeclarationHandler redeclarationHandler =
        (Scope s, String name, Node n, CompilerInput input) -> {};
    Es6SyntacticScopeCreator scopeCreator =
        new Es6SyntacticScopeCreator(compiler, redeclarationHandler);
    return scopeCreator.createScope(root, null);
  }

  public void testStringLiteral() {
    AstFactory astFactory = createTestAstFactory();

    Node stringLiteral = astFactory.createString("hello");
    assertNode(stringLiteral).hasType(Token.STRING);
    assertThat(stringLiteral.getString()).isEqualTo("hello");
    assertType(stringLiteral.getJSType()).isString();
  }

  public void testNumberLiteral() {
    AstFactory astFactory = createTestAstFactory();

    Node numberLiteral = astFactory.createNumber(2112D);
    assertNode(numberLiteral).hasType(Token.NUMBER);
    assertThat(numberLiteral.getDouble()).isEqualTo(2112D);
    assertType(numberLiteral.getJSType()).isNumber();
  }

  public void testBooleanLiteral() {
    AstFactory astFactory = createTestAstFactory();

    Node trueNode = astFactory.createBoolean(true);
    assertNode(trueNode).hasType(Token.TRUE);
    assertType(trueNode.getJSType()).isBoolean();

    Node falseNode = astFactory.createBoolean(false);
    assertNode(falseNode).hasType(Token.FALSE);
    assertType(falseNode.getJSType()).isBoolean();
  }

  public void testCreateArgumentsReference() {
    // Make sure the compiler's type registry includes the standard externs definition for
    // Arguments.
    parseAndAddTypes(new TestExternsBuilder().addArguments().build(), "");

    AstFactory astFactory = createTestAstFactory();

    Node argumentsNode = astFactory.createArgumentsReference();
    assertNode(argumentsNode).matchesQualifiedName("arguments");
    assertType(argumentsNode.getJSType()).isEqualTo(getRegistry().getGlobalType("Arguments"));
  }

  public void testCreateNameWithJSType() {
    AstFactory astFactory = createTestAstFactory();

    Node x = astFactory.createName("x", getNativeType(JSTypeNative.STRING_TYPE));
    assertNode(x).hasType(Token.NAME);
    assertThat(x.getString()).isEqualTo("x");
    assertType(x.getJSType()).isString();
  }

  public void testCreateNameWithNativeType() {
    AstFactory astFactory = createTestAstFactory();

    Node x = astFactory.createName("x", JSTypeNative.STRING_TYPE);
    assertNode(x).hasType(Token.NAME);
    assertThat(x.getString()).isEqualTo("x");
    assertType(x.getJSType()).isString();
  }

  public void testCreateNameFromScope() {
    AstFactory astFactory = createTestAstFactory();

    Node root = parseAndAddTypes("/** @type {string} */ const X = 'hi';");
    Scope scope = getScope(root);

    Node x = astFactory.createName(scope, "X");
    assertNode(x).hasType(Token.NAME);
    assertThat(x.getString()).isEqualTo("X");
    assertType(x.getJSType()).isString();
  }

  public void testCreateThisReference() {
    AstFactory astFactory = createTestAstFactory();

    Node x = astFactory.createThis(getNativeType(JSTypeNative.STRING_TYPE));
    assertNode(x).hasType(Token.THIS);
    assertType(x.getJSType()).isString();
  }

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

  public void testCreateStringKey() {
    AstFactory astFactory = createTestAstFactory();

    Node numberNode = astFactory.createNumber(2112D);
    Node stringKeyNode = astFactory.createStringKey("key", numberNode);

    assertNode(stringKeyNode).hasType(Token.STRING_KEY);
    assertThat(stringKeyNode.getString()).isEqualTo("key");
    assertThat(stringKeyNode.children()).containsExactly(numberNode);
    assertType(stringKeyNode.getJSType()).isNumber();
  }

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

  public void testCreateComma() {
    AstFactory astFactory = createTestAstFactory();

    Node stringNode = astFactory.createString("hi");
    Node numberNode = astFactory.createNumber(2112D);
    Node commaNode = astFactory.createComma(stringNode, numberNode);

    assertNode(commaNode).hasType(Token.COMMA);
    assertThat(commaNode.children()).containsExactly(stringNode, numberNode).inOrder();
    assertType(commaNode.getJSType()).isNumber();
  }

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

  public void testCreateIn() {
    AstFactory astFactory = createTestAstFactory();

    Node prop = astFactory.createString("prop");
    Node obj = IR.name("obj"); // TODO(bradfordcsmith): This should have a type on it.
    Node n = astFactory.createIn(prop, obj);
    assertNode(n).hasType(Token.IN);
    assertType(n.getJSType()).isBoolean();
    assertThat(n.children()).containsExactly(prop, obj).inOrder();
  }

  public void testCreateAnd() {
    AstFactory astFactory = createTestAstFactory();

    Node numberLiteral = astFactory.createNumber(2112D);
    Node stringLiteral = astFactory.createString("hello");
    Node andNode = astFactory.createAnd(numberLiteral, stringLiteral);

    assertNode(andNode).hasType(Token.AND);
    assertThat(andNode.children()).containsExactly(numberLiteral, stringLiteral).inOrder();
    assertType(andNode.getJSType()).toStringIsEqualTo("(number|string)");
  }

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

  public void testCreateOr() {
    AstFactory astFactory = createTestAstFactory();

    Node numberLiteral = astFactory.createNumber(2112D);
    Node stringLiteral = astFactory.createString("hello");
    Node andNode = astFactory.createOr(numberLiteral, stringLiteral);

    assertNode(andNode).hasType(Token.OR);
    assertThat(andNode.children()).containsExactly(numberLiteral, stringLiteral).inOrder();
    assertType(andNode.getJSType()).toStringIsEqualTo("(number|string)");
  }

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
}
