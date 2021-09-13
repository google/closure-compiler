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
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.AstFactory.type;
import static com.google.javascript.jscomp.base.JSCompStrings.lines;
import static com.google.javascript.rhino.testing.Asserts.assertThrows;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.SyntacticScopeCreator.RedeclarationHandler;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.ColorId;
import com.google.javascript.jscomp.colors.ColorRegistry;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.jscomp.serialization.ConvertTypesToColors;
import com.google.javascript.jscomp.serialization.SerializationOptions;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticScope;
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
@SuppressWarnings("RhinoNodeGetFirstFirstChild")
public class AstFactoryTest {

  private Compiler compiler;

  @Before
  public void setUp() throws Exception {
    compiler = new Compiler();
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
    compiler.init(
        ImmutableList.of(SourceFile.fromCode("externs", externs)),
        ImmutableList.of(SourceFile.fromCode("source", source)),
        options);
    compiler.parseInputs();

    assertWithMessage("parse error").that(compiler.getErrors()).isEmpty();
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
    compiler.setTypeCheckingHasRun(true);
    return compiler.getJsRoot();
  }

  private Node parseAndAddColors(String source) {
    return parseAndAddColors("", source);
  }

  private Node parseAndAddColors(String externs, String source) {
    parseAndAddTypes(externs, source);
    new ConvertTypesToColors(compiler, SerializationOptions.INCLUDE_DEBUG_INFO)
        .process(compiler.getExternsRoot(), compiler.getJsRoot());
    return compiler.getJsRoot();
  }

  private AstFactory createTestAstFactory() {
    return AstFactory.createFactoryWithTypes(getRegistry());
  }

  private AstFactory createTestAstFactoryWithColors() {
    return AstFactory.createFactoryWithColors(
        // the built-in color registry is available only if we've run parseAndAddColors()
        compiler.hasOptimizationColors()
            ? compiler.getColorRegistry()
            : ColorRegistry.builder().setDefaultNativeColorsForTesting().build());
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
  public void testStringLiteral_jstypes() {
    AstFactory astFactory = createTestAstFactory();

    Node stringLiteral = astFactory.createString("hello");
    assertNode(stringLiteral).hasType(Token.STRINGLIT);
    assertThat(stringLiteral.getString()).isEqualTo("hello");
    assertType(stringLiteral.getJSType()).isString();
  }

  @Test
  public void testStringLiteral_colors() {
    AstFactory astFactory = createTestAstFactoryWithColors();

    Node stringLiteral = astFactory.createString("hello");
    assertNode(stringLiteral).hasType(Token.STRINGLIT);
    assertThat(stringLiteral.getString()).isEqualTo("hello");
    assertNode(stringLiteral).hasColorThat().isEqualTo(StandardColors.STRING);
  }

  @Test
  public void testNumberLiteral_jstypes() {
    AstFactory astFactory = createTestAstFactory();

    Node numberLiteral = astFactory.createNumber(2112D);
    assertNode(numberLiteral).hasType(Token.NUMBER);
    assertThat(numberLiteral.getDouble()).isEqualTo(2112D);
    assertType(numberLiteral.getJSType()).isNumber();
  }

  @Test
  public void testNumberLiteral_colors() {
    AstFactory astFactory = createTestAstFactoryWithColors();

    Node numberLiteral = astFactory.createNumber(2112D);
    assertNode(numberLiteral).hasType(Token.NUMBER);
    assertThat(numberLiteral.getDouble()).isEqualTo(2112D);
    assertNode(numberLiteral).hasColorThat().isEqualTo(StandardColors.NUMBER);
  }

  @Test
  public void testBooleanLiteral_jstypes() {
    AstFactory astFactory = createTestAstFactory();

    Node trueNode = astFactory.createBoolean(true);
    assertNode(trueNode).hasType(Token.TRUE);
    assertType(trueNode.getJSType()).isBoolean();

    Node falseNode = astFactory.createBoolean(false);
    assertNode(falseNode).hasType(Token.FALSE);
    assertType(falseNode.getJSType()).isBoolean();
  }

  @Test
  public void testBooleanLiteral_colors() {
    AstFactory astFactory = createTestAstFactoryWithColors();

    Node trueNode = astFactory.createBoolean(true);
    assertNode(trueNode).hasType(Token.TRUE);
    assertNode(trueNode).hasColorThat().isEqualTo(StandardColors.BOOLEAN);
  }

  @Test
  public void testVoidExpression_jstypes() {
    AstFactory astFactory = createTestAstFactory();

    Node voidNode = astFactory.createVoid(astFactory.createNumber(0));
    assertNode(voidNode).hasType(Token.VOID);
    assertNode(voidNode).hasJSTypeThat().isVoid();
  }

  @Test
  public void testVoidExpression_colors() {
    AstFactory astFactory = createTestAstFactoryWithColors();

    Node voidNode = astFactory.createVoid(astFactory.createNumber(0));
    assertNode(voidNode).hasType(Token.VOID);
    assertNode(voidNode).hasColorThat().isEqualTo(StandardColors.NULL_OR_VOID);
  }

  @Test
  public void testNotExpression_jstypes() {
    AstFactory astFactory = createTestAstFactory();

    Node notNode = astFactory.createNot(astFactory.createNumber(0));
    assertNode(notNode).hasType(Token.NOT);
    assertNode(notNode).hasJSTypeThat().isBoolean();
  }

  @Test
  public void testNotExpression_colors() {
    AstFactory astFactory = createTestAstFactoryWithColors();

    Node notNode = astFactory.createNot(astFactory.createNumber(0));
    assertNode(notNode).hasType(Token.NOT);
    assertNode(notNode).hasColorThat().isEqualTo(StandardColors.BOOLEAN);
  }

  @Test
  public void testCreateArgumentsReference_jstypes() {
    // Make sure the compiler's type registry includes the standard externs definition for
    // Arguments.
    parseAndAddTypes(new TestExternsBuilder().addArguments().build(), "");

    AstFactory astFactory = createTestAstFactory();

    Node argumentsNode = astFactory.createArgumentsReference();
    assertNode(argumentsNode).matchesName("arguments");
    assertType(argumentsNode.getJSType()).isEqualTo(getRegistry().getGlobalType("Arguments"));
  }

  @Test
  public void testCreateArgumentsReference_colors() {
    Node root =
        parseAndAddColors(
            new TestExternsBuilder().addArguments().build(), "function f() { arguments; }");

    AstFactory astFactory = createTestAstFactoryWithColors();

    Node block = NodeUtil.getFunctionBody(root.getFirstFirstChild());
    Node argumentsReferenceNode = block.getFirstFirstChild();
    Color argumentsReferenceColor = argumentsReferenceNode.getColor();

    Node argumentsNode = astFactory.createArgumentsReference();
    assertNode(argumentsNode).matchesName("arguments");
    assertNode(argumentsNode).hasColorThat().isEqualTo(argumentsReferenceColor);
  }

  @Test
  public void testCreateSingleVarNameDeclaration_jstypes() {
    AstFactory astFactory = createTestAstFactory();

    final Node valueNode = astFactory.createBoolean(true);
    Node constNode = astFactory.createSingleVarNameDeclaration("myTrue", valueNode);
    assertNode(constNode).isVar().hasOneChildThat().isName("myTrue");
    Node nameNode = constNode.getOnlyChild();
    assertNode(nameNode).hasOneChildThat().isEqualTo(valueNode);
    assertNode(nameNode).hasJSTypeThat().isEqualTo(valueNode.getJSType());
  }

  @Test
  public void testCreateSingleVarNameDeclaration_colors() {
    AstFactory astFactory = createTestAstFactoryWithColors();

    final Node valueNode = astFactory.createBoolean(true);
    Node constNode = astFactory.createSingleVarNameDeclaration("myTrue", valueNode);
    assertNode(constNode).isVar().hasOneChildThat().isName("myTrue");
    Node nameNode = constNode.getOnlyChild();
    assertNode(nameNode).hasOneChildThat().isEqualTo(valueNode);
    assertNode(nameNode).hasColorThat().isEqualTo(valueNode.getColor());
  }

  @Test
  public void testCreateSingleConstNameDeclaration_jstypes() {
    AstFactory astFactory = createTestAstFactory();

    final Node valueNode = astFactory.createBoolean(true);
    Node constNode = astFactory.createSingleConstNameDeclaration("myTrue", valueNode);
    assertNode(constNode).isConst().hasOneChildThat().isName("myTrue");
    Node nameNode = constNode.getOnlyChild();
    assertNode(nameNode).hasOneChildThat().isEqualTo(valueNode);
    assertNode(nameNode).hasJSTypeThat().isEqualTo(valueNode.getJSType());
  }

  @Test
  public void testCreateSingleConstNameDeclaration_colors() {
    AstFactory astFactory = createTestAstFactoryWithColors();

    final Node valueNode = astFactory.createBoolean(true);
    Node constNode = astFactory.createSingleConstNameDeclaration("myTrue", valueNode);
    assertNode(constNode).isConst().hasOneChildThat().isName("myTrue");
    Node nameNode = constNode.getOnlyChild();
    assertNode(nameNode).hasOneChildThat().isEqualTo(valueNode);
    assertNode(nameNode).hasColorThat().isEqualTo(valueNode.getColor());
  }

  @Test
  public void testCreateNameWithJSType() {
    AstFactory astFactory = createTestAstFactory();

    Node x = astFactory.createName("x", type(JSTypeNative.STRING_TYPE));
    assertNode(x).hasType(Token.NAME);
    assertThat(x.getString()).isEqualTo("x");
    assertType(x.getJSType()).isString();
  }

  @Test
  public void testCreateNameWithNativeType() {
    AstFactory astFactory = createTestAstFactory();

    Node x = astFactory.createName("x", type(JSTypeNative.STRING_TYPE));
    assertNode(x).hasType(Token.NAME);
    assertThat(x.getString()).isEqualTo("x");
    assertType(x.getJSType()).isString();
  }

  @Test
  public void testCreateNameWithColor() {
    AstFactory astFactory = createTestAstFactoryWithColors();

    Node x = astFactory.createName("x", type(JSTypeNative.STRING_TYPE, StandardColors.STRING));
    assertNode(x).hasType(Token.NAME);
    assertThat(x.getString()).isEqualTo("x");
    assertNode(x).hasColorThat().isEqualTo(StandardColors.STRING);
  }

  @Test
  public void testCreateNameFromScope_jstypes() {
    AstFactory astFactory = createTestAstFactory();

    Node root = parseAndAddTypes("/** @type {string} */ const X = 'hi';");
    Scope scope = getScope(root);

    Node x = astFactory.createName(scope, "X");
    assertNode(x).hasType(Token.NAME);
    assertThat(x.getString()).isEqualTo("X");
    assertType(x.getJSType()).isString();
  }

  @Test
  public void testCreateNameFromScope_colors() {
    AstFactory astFactory = createTestAstFactoryWithColors();

    Node root = parseAndAddColors("/** @type {string} */ const X = 'hi';");
    Scope scope = getScope(root);

    Node x = astFactory.createName(scope, "X");
    assertNode(x).hasType(Token.NAME);
    assertThat(x.getString()).isEqualTo("X");
    assertNode(x).hasColorThat().isEqualTo(StandardColors.STRING);
  }

  @Test
  public void testCreateNameFromScope_crashesIfMissingVariable() {
    AstFactory astFactory = createTestAstFactory();

    Node root = parseAndAddTypes("/** @type {string} */ const X = 'hi';");
    Scope scope = getScope(root);

    assertThrows(Exception.class, () -> astFactory.createName(scope, "missing"));
  }

  @Test
  public void testCreateThisReference() {
    AstFactory astFactory = createTestAstFactory();

    Node x = astFactory.createThis(type(getNativeType(JSTypeNative.STRING_TYPE)));
    assertNode(x).hasType(Token.THIS);
    assertType(x.getJSType()).isString();
  }

  @Test
  public void testCreateSuperReference() {
    AstFactory astFactory = createTestAstFactory();

    Node x = astFactory.createSuper(type(getNativeType(JSTypeNative.STRING_TYPE)));
    assertNode(x).hasType(Token.SUPER);
    assertType(x.getJSType()).isString();
  }

  @Test
  public void createThisForEs6ClassMember_jstypes() {
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
    Node memberDef =
        classNode
            .getLastChild() // class members
            .getFirstChild(); // member function def

    ObjectType instanceType = classNode.getJSTypeRequired().assertFunctionType().getInstanceType();

    Node thisAlias = astFactory.createThisForEs6ClassMember(memberDef);
    assertNode(thisAlias).hasType(Token.THIS);
    assertNode(thisAlias).hasJSTypeThat().isEqualTo(instanceType);
  }

  @Test
  public void createThisForEs6ClassMember_colors() {
    AstFactory astFactory = createTestAstFactoryWithColors();

    Node root =
        parseAndAddColors(
            lines(
                "class C {", //
                "  method() {}",
                "}",
                ""));

    Node classNode =
        root.getFirstChild() // script
            .getFirstChild(); // class node
    Node memberDef =
        classNode
            .getLastChild() // class members
            .getFirstChild(); // member function def

    Color instanceType = Color.createUnion(classNode.getColor().getInstanceColors());

    Node thisAlias = astFactory.createThisForEs6ClassMember(memberDef);
    assertNode(thisAlias).hasType(Token.THIS);
    assertNode(thisAlias).hasColorThat().isEqualTo(instanceType);
  }

  @Test
  public void createThisForEs6ClassStaticMember_jstypes() {
    AstFactory astFactory = createTestAstFactory();

    Node root =
        parseAndAddTypes(
            lines(
                "class C {", //
                "  static method() {}",
                "}",
                ""));

    Node classNode =
        root.getFirstChild() // script
            .getFirstChild(); // class node
    Node memberDef =
        classNode
            .getLastChild() // class members
            .getFirstChild(); // member function def

    Node thisAlias = astFactory.createThisForEs6ClassMember(memberDef);
    assertNode(thisAlias).hasType(Token.THIS);
    assertNode(thisAlias).hasJSTypeThat().isEqualTo(classNode.getJSType());
  }

  @Test
  public void createThisForEs6ClassStaticMember_colors() {
    AstFactory astFactory = createTestAstFactoryWithColors();

    Node root =
        parseAndAddColors(
            lines(
                "class C {", //
                "  static method() {}",
                "}",
                ""));

    Node classNode =
        root.getFirstChild() // script
            .getFirstChild(); // class node
    Node memberDef =
        classNode
            .getLastChild() // class members
            .getFirstChild(); // member function def

    Node thisAlias = astFactory.createThisForEs6ClassMember(memberDef);
    assertNode(thisAlias).hasType(Token.THIS);
    assertNode(thisAlias).hasColorThat().isEqualTo(classNode.getColor());
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

    Node thisAlias = astFactory.createThisAliasReferenceForEs6Class("thisAlias", classNode);
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

    Node classNode =
        root.getFirstChild() // script
            .getFirstChild(); // class

    Node thisAlias = astFactory.createThisAliasReferenceForEs6Class("thisAlias", classNode);
    assertNode(thisAlias).hasType(Token.NAME);
    assertThat(thisAlias.getString()).isEqualTo("thisAlias");
    assertThat(thisAlias.getJSType()).isNull();
  }

  @Test
  public void testCreateGetpropWithColorFromNode() {
    AstFactory astFactory = createTestAstFactoryWithColors();

    Node receiver = astFactory.createNameWithUnknownType("x");
    Node typeTemplate = astFactory.createNumber(0);

    Node getProp = astFactory.createGetProp(receiver, "y", type(typeTemplate));

    assertNode(getProp).hasToken(Token.GETPROP);
    assertNode(getProp).hasStringThat().isEqualTo("y");
    assertNode(getProp).hasFirstChildThat().isEqualTo(receiver);
    assertNode(getProp).hasColorThat().isEqualTo(StandardColors.NUMBER);
  }

  @Test
  public void testCreateStringKey_jstypes() {
    AstFactory astFactory = createTestAstFactory();

    Node numberNode = astFactory.createNumber(2112D);
    Node stringKeyNode = astFactory.createStringKey("key", numberNode);

    assertNode(stringKeyNode).hasType(Token.STRING_KEY);
    assertThat(stringKeyNode.getString()).isEqualTo("key");
    assertThat(childList(stringKeyNode)).containsExactly(numberNode);
    assertType(stringKeyNode.getJSType()).isNumber();
  }

  @Test
  public void testCreateStringKey_colors() {
    AstFactory astFactory = createTestAstFactoryWithColors();

    Node numberNode = astFactory.createNumber(2112D);
    Node stringKeyNode = astFactory.createStringKey("key", numberNode);

    assertNode(stringKeyNode).hasType(Token.STRING_KEY);
    assertThat(stringKeyNode.getString()).isEqualTo("key");
    assertThat(childList(stringKeyNode)).containsExactly(numberNode);
    assertNode(stringKeyNode).hasColorThat().isEqualTo(StandardColors.NUMBER);
  }

  @Test
  public void testCreateComputedProperty_jstypes() {
    AstFactory astFactory = createTestAstFactory();

    Node numberNode = astFactory.createNumber(2112D);
    Node stringLiteral = astFactory.createString("string literal key");
    Node computedPropertyNode = astFactory.createComputedProperty(stringLiteral, numberNode);

    assertNode(computedPropertyNode).hasType(Token.COMPUTED_PROP);
    assertThat(childList(computedPropertyNode))
        .containsExactly(stringLiteral, numberNode)
        .inOrder();
    assertType(computedPropertyNode.getJSType()).isNumber();
  }

  @Test
  public void testCreateComputedProperty_colors() {
    AstFactory astFactory = createTestAstFactoryWithColors();

    Node numberNode = astFactory.createNumber(2112D);
    Node stringLiteral = astFactory.createString("string literal key");
    Node computedPropertyNode = astFactory.createComputedProperty(stringLiteral, numberNode);

    assertNode(computedPropertyNode).hasType(Token.COMPUTED_PROP);
    assertThat(childList(computedPropertyNode))
        .containsExactly(stringLiteral, numberNode)
        .inOrder();
    assertNode(computedPropertyNode).hasColorThat().isEqualTo(StandardColors.NUMBER);
  }

  @Test
  public void testCreateGetterDef() {
    AstFactory astFactory = createTestAstFactory();

    Node valueNode = astFactory.createString("value");
    Node getterNode = astFactory.createGetterDef("name", valueNode);

    assertNode(getterNode) //
        .hasToken(Token.GETTER_DEF)
        .hasStringThat()
        .isEqualTo("name");
    assertNode(getterNode) //
        .hasOneChildThat()
        .isFunction()
        .hasXChildren(3);
    Node getterFunctionNode = getterNode.getOnlyChild();
    // The first child is an empty string representing that the function has no name
    assertNode(getterFunctionNode).hasFirstChildThat().isName("");
    // the second child is the parameter list, which should be empty.
    assertNode(getterFunctionNode) //
        .hasSecondChildThat()
        .isParamList()
        .hasNoChildren();
    Node getterFunctionBody = getterFunctionNode.getLastChild();
    assertNode(getterFunctionBody) //
        .hasOneChildThat()
        .hasToken(Token.RETURN)
        .hasOneChildThat()
        .isEqualTo(valueNode);
  }

  @Test
  public void testCreateGetElem_jstypes() {
    AstFactory astFactory = createTestAstFactory();

    Node objectName = astFactory.createName("obj", type(JSTypeNative.OBJECT_TYPE));
    Node stringLiteral = astFactory.createString("string literal key");
    Node getElemNode = astFactory.createGetElem(objectName, stringLiteral);

    assertNode(getElemNode).hasType(Token.GETELEM);
    assertThat(childList(getElemNode)).containsExactly(objectName, stringLiteral).inOrder();
    // TODO(bradfordcsmith): When receiver is an Array<T> or an Object<K, V>, use the template type
    // here.
    assertType(getElemNode.getJSType()).isUnknown();
  }

  @Test
  public void testCreateGetElem_colors() {
    AstFactory astFactory = createTestAstFactoryWithColors();

    Node objectName =
        astFactory.createName("obj", type(JSTypeNative.STRING_TYPE, StandardColors.STRING));
    Node stringLiteral = astFactory.createString("string literal key");
    Node getElemNode = astFactory.createGetElem(objectName, stringLiteral);

    assertNode(getElemNode).hasType(Token.GETELEM);
    assertThat(childList(getElemNode)).containsExactly(objectName, stringLiteral).inOrder();
    assertNode(getElemNode).hasColorThat().isEqualTo(StandardColors.UNKNOWN);
  }

  @Test
  public void testCreateComma_jstypes() {
    AstFactory astFactory = createTestAstFactory();

    Node stringNode = astFactory.createString("hi");
    Node numberNode = astFactory.createNumber(2112D);
    Node commaNode = astFactory.createComma(stringNode, numberNode);

    assertNode(commaNode).hasType(Token.COMMA);
    assertThat(childList(commaNode)).containsExactly(stringNode, numberNode).inOrder();
    assertType(commaNode.getJSType()).isNumber();
  }

  @Test
  public void testCreateComma_colors() {
    AstFactory astFactory = createTestAstFactoryWithColors();

    Node stringNode = astFactory.createString("hi");
    Node numberNode = astFactory.createNumber(2112D);
    Node commaNode = astFactory.createComma(stringNode, numberNode);

    assertNode(commaNode).hasType(Token.COMMA);
    assertThat(childList(commaNode)).containsExactly(stringNode, numberNode).inOrder();
    assertNode(commaNode).hasColorThat().isEqualTo(StandardColors.NUMBER);
  }

  @Test
  public void testCreateCommas_jstypes() {
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
    assertThat(childList(stringNumberTrueFalse))
        .containsExactly(stringNumberTrue, falseNode)
        .inOrder();
    assertType(stringNumberTrueFalse.getJSType()).isBoolean();

    // ("hi", 2112), true
    Node stringNumber = stringNumberTrue.getFirstChild();
    assertThat(childList(stringNumberTrue)).containsExactly(stringNumber, trueNode).inOrder();
    assertType(stringNumberTrue.getJSType()).isBoolean();

    // "hi", 2112
    assertThat(childList(stringNumber)).containsExactly(stringNode, numberNode);
    assertType(stringNumber.getJSType()).isNumber();
  }

  @Test
  public void testCreateIn_jstypes() {
    AstFactory astFactory = createTestAstFactory();

    Node prop = astFactory.createString("prop");
    Node obj = IR.name("obj"); // TODO(bradfordcsmith): This should have a type on it.
    Node n = astFactory.createIn(prop, obj);
    assertNode(n).hasType(Token.IN);
    assertType(n.getJSType()).isBoolean();
    assertThat(childList(n)).containsExactly(prop, obj).inOrder();
  }

  @Test
  public void testCreateIn_colors() {
    AstFactory astFactory = createTestAstFactoryWithColors();

    Node prop = astFactory.createString("prop");
    Node obj = IR.name("obj"); // TODO(bradfordcsmith): This should have a type on it.
    Node n = astFactory.createIn(prop, obj);
    assertNode(n).hasType(Token.IN);
    assertNode(n).hasColorThat().isEqualTo(StandardColors.BOOLEAN);
    assertThat(childList(n)).containsExactly(prop, obj).inOrder();
  }

  @Test
  public void testCreateAnd_jstypes() {
    AstFactory astFactory = createTestAstFactory();

    Node numberLiteral = astFactory.createNumber(2112D);
    Node stringLiteral = astFactory.createString("hello");
    Node andNode = astFactory.createAnd(numberLiteral, stringLiteral);

    assertNode(andNode).hasType(Token.AND);
    assertThat(childList(andNode)).containsExactly(numberLiteral, stringLiteral).inOrder();
    assertType(andNode.getJSType()).toStringIsEqualTo("(number|string)");
  }

  @Test
  public void testCreateAnd_colors() {
    AstFactory astFactory = createTestAstFactoryWithColors();

    Node numberLiteral = astFactory.createNumber(2112D);
    Node stringLiteral = astFactory.createString("hello");
    Node andNode = astFactory.createAnd(numberLiteral, stringLiteral);

    assertNode(andNode).hasType(Token.AND);
    assertThat(childList(andNode)).containsExactly(numberLiteral, stringLiteral).inOrder();
    assertNode(andNode).hasColorThat().hasAlternates(StandardColors.STRING, StandardColors.NUMBER);
  }

  @Test
  public void testCreateAndWithAlwaysFalsyLhs() {
    AstFactory astFactory = createTestAstFactory();

    Node nullNode = astFactory.createNull();
    Node stringLiteral = astFactory.createString("hello");
    Node andNode = astFactory.createAnd(nullNode, stringLiteral);

    assertNode(andNode).hasType(Token.AND);
    assertThat(childList(andNode)).containsExactly(nullNode, stringLiteral).inOrder();
    // NULL_TYPE doesn't contain any truthy values, so its type is the only possibility
    // but AstFactory is simpler than the type inferencer and does not realize this.
    assertType(andNode.getJSType()).toStringIsEqualTo("(null|string)");
  }

  @Test
  public void testCreateAndWithAlwaysTruthyLhs() {
    AstFactory astFactory = createTestAstFactory();

    Node nonNullObject = astFactory.createName("nonNullObject", type(JSTypeNative.OBJECT_TYPE));
    Node stringLiteral = astFactory.createString("hello");
    Node andNode = astFactory.createAnd(nonNullObject, stringLiteral);

    assertNode(andNode).hasType(Token.AND);
    assertThat(childList(andNode)).containsExactly(nonNullObject, stringLiteral).inOrder();
    // OBJECT_TYPE doesn't contain any falsy values, so the RHS type is the only possibility
    // but AstFactory is simpler than the type inferencer and does not realize this.
    assertType(andNode.getJSType()).toStringIsEqualTo("(Object|string)");
  }

  @Test
  public void testCreateOr() {
    AstFactory astFactory = createTestAstFactory();

    Node numberLiteral = astFactory.createNumber(2112D);
    Node stringLiteral = astFactory.createString("hello");
    Node andNode = astFactory.createOr(numberLiteral, stringLiteral);

    assertNode(andNode).hasType(Token.OR);
    assertThat(childList(andNode)).containsExactly(numberLiteral, stringLiteral).inOrder();
    assertType(andNode.getJSType()).toStringIsEqualTo("(number|string)");
  }

  @Test
  public void testCreateOrWithAlwaysFalsyLhs() {
    AstFactory astFactory = createTestAstFactory();

    Node nullLiteral = astFactory.createNull();
    Node stringLiteral = astFactory.createString("hello");
    Node andNode = astFactory.createOr(nullLiteral, stringLiteral);

    assertNode(andNode).hasType(Token.OR);
    assertThat(childList(andNode)).containsExactly(nullLiteral, stringLiteral).inOrder();
    // NULL_TYPE doesn't contain any truthy values, so the RHS type is the only possibility
    // but AstFactory is simpler than the type inferencer and does not realize this.
    assertType(andNode.getJSType()).toStringIsEqualTo("(null|string)");
  }

  @Test
  public void testCreateOrWithAlwaysTruthyLhs() {
    AstFactory astFactory = createTestAstFactory();

    Node nonNullObject = astFactory.createName("nonNullObject", type(JSTypeNative.OBJECT_TYPE));
    Node stringLiteral = astFactory.createString("hello");
    Node andNode = astFactory.createOr(nonNullObject, stringLiteral);

    assertNode(andNode).hasType(Token.OR);
    assertThat(childList(andNode)).containsExactly(nonNullObject, stringLiteral).inOrder();
    // OBJECT_TYPE doesn't contain any falsy values, so the RHS won't be evaluated
    // but AstFactory is simpler than the type inferencer and does not realize this.
    assertType(andNode.getJSType()).toStringIsEqualTo("(Object|string)");
  }

  @Test
  public void testCreateAdd_stringAndNumber_jstypes() {
    AstFactory astFactory = createTestAstFactory();

    Node zero = astFactory.createNumber(0);
    Node str = astFactory.createString("x");

    Node add = astFactory.createAdd(zero, str);
    assertNode(add).hasToken(Token.ADD);
    assertThat(childList(add)).containsExactly(zero, str);
    assertNode(add).hasJSTypeThat().isEqualTo(getNativeType(JSTypeNative.BIGINT_NUMBER_STRING));
  }

  @Test
  public void testCreateAdd_stringAndNumber_colors() {
    AstFactory astFactory = createTestAstFactoryWithColors();

    Node zero = astFactory.createNumber(0);
    Node str = astFactory.createString("x");

    Node add = astFactory.createAdd(zero, str);
    assertNode(add).hasToken(Token.ADD);
    assertThat(childList(add)).containsExactly(zero, str);
    assertNode(add)
        .hasColorThat()
        .hasAlternates(StandardColors.BIGINT, StandardColors.NUMBER, StandardColors.STRING);
  }

  @Test
  public void testCreateSub_jstypes() {
    AstFactory astFactory = createTestAstFactory();

    Node zero = astFactory.createNumber(0);
    Node one = astFactory.createNumber(1);

    Node sub = astFactory.createSub(zero, one);

    assertNode(sub).hasToken(Token.SUB);
    assertThat(childList(sub)).containsExactly(zero, one);
    assertNode(sub).hasJSTypeThat().isEqualTo(getNativeType(JSTypeNative.NUMBER_TYPE));
  }

  @Test
  public void testCreateSub_colors() {
    AstFactory astFactory = createTestAstFactoryWithColors();

    Node zero = astFactory.createNumber(0);
    Node one = astFactory.createNumber(1);

    Node sub = astFactory.createSub(zero, one);

    assertNode(sub).hasToken(Token.SUB);
    assertThat(childList(sub)).containsExactly(zero, one);
    assertNode(sub).hasColorThat().isEqualTo(StandardColors.NUMBER);
  }

  @Test
  public void testCreateLessThan_jstypes() {
    AstFactory astFactory = createTestAstFactory();

    Node zero = astFactory.createNumber(0);
    Node one = astFactory.createNumber(1);

    Node lt = astFactory.createLessThan(zero, one);

    assertNode(lt).hasToken(Token.LT);
    assertThat(childList(lt)).containsExactly(zero, one);
    assertNode(lt).hasJSTypeThat().isEqualTo(getNativeType(JSTypeNative.BOOLEAN_TYPE));
  }

  @Test
  public void testCreateLessThan_colors() {
    AstFactory astFactory = createTestAstFactoryWithColors();

    Node zero = astFactory.createNumber(0);
    Node one = astFactory.createNumber(1);

    Node lt = astFactory.createLessThan(zero, one);

    assertNode(lt).hasToken(Token.LT);
    assertThat(childList(lt)).containsExactly(zero, one);
    assertNode(lt).hasColorThat().isEqualTo(StandardColors.BOOLEAN);
  }

  @Test
  public void testCreateInc_prefix_jstypes() {
    AstFactory astFactory = createTestAstFactory();

    Node x = astFactory.createNameWithUnknownType("x");

    Node inc = astFactory.createInc(x, /* isPost= */ false);

    assertNode(inc).hasToken(Token.INC);
    assertThat(inc.getBooleanProp(Node.INCRDECR_PROP)).isFalse();
    assertThat(childList(inc)).containsExactly(x);
    assertNode(inc).hasJSTypeThat().isEqualTo(getNativeType(JSTypeNative.NUMBER_TYPE));
  }

  @Test
  public void testCreateInc_postfix_jstypes() {
    AstFactory astFactory = createTestAstFactory();

    Node x = astFactory.createNameWithUnknownType("x");

    Node inc = astFactory.createInc(x, /* isPost= */ true);

    assertNode(inc).hasToken(Token.INC);
    assertThat(inc.getBooleanProp(Node.INCRDECR_PROP)).isTrue();
    assertThat(childList(inc)).containsExactly(x);
    assertNode(inc).hasJSTypeThat().isEqualTo(getNativeType(JSTypeNative.NUMBER_TYPE));
  }

  @Test
  public void testCreateInc_prefix_colors() {
    AstFactory astFactory = createTestAstFactoryWithColors();

    Node x = astFactory.createNameWithUnknownType("x");

    Node inc = astFactory.createInc(x, /* isPost= */ false);

    assertNode(inc).hasToken(Token.INC);
    assertThat(inc.getBooleanProp(Node.INCRDECR_PROP)).isFalse();
    assertThat(childList(inc)).containsExactly(x);
    assertNode(inc).hasColorThat().isEqualTo(StandardColors.NUMBER);
  }

  @Test
  public void testCreateCallWithTypeFromNode() {
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
    Node callNode = astFactory.createCall(callee, type(astFactory.createString("tmp")), arg1, arg2);

    assertNode(callNode).hasType(Token.CALL);
    assertThat(callNode.getBooleanProp(Node.FREE_CALL)).isTrue();
    assertThat(childList(callNode)).containsExactly(callee, arg1, arg2).inOrder();
    assertType(callNode.getJSType()).isString();
  }

  @Test
  public void testCreateCallWithColorFromNode() {
    AstFactory astFactory = createTestAstFactoryWithColors();

    Node root =
        parseAndAddColors(
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
    Node callNode = astFactory.createCall(callee, type(astFactory.createString("tmp")), arg1, arg2);

    assertNode(callNode).hasType(Token.CALL);
    assertThat(callNode.getBooleanProp(Node.FREE_CALL)).isTrue();
    assertThat(childList(callNode)).containsExactly(callee, arg1, arg2).inOrder();
    assertNode(callNode).hasColorThat().isEqualTo(StandardColors.STRING);
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
    Node callNode = astFactory.createCall(callee, type(JSTypeNative.STRING_TYPE), arg1, arg2);

    assertNode(callNode).hasType(Token.CALL);
    assertThat(callNode.getBooleanProp(Node.FREE_CALL)).isTrue();
    assertThat(childList(callNode)).containsExactly(callee, arg1, arg2).inOrder();
    assertType(callNode.getJSType()).isString();
  }

  @Test
  public void testCreateMethodCall_throws() {
    AstFactory astFactory = createTestAstFactory();

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
    StaticScope scope = compiler.getTranspilationNamespace();

    // createQName only accepts globally qualified qnames. foo.method is a prototype method access.
    // foo.method("hi", 2112)
    assertThrows(Exception.class, () -> astFactory.createQName(scope, "foo.method"));
  }

  @Test
  public void testCreateStaticMethodCallDotCallThrows() {
    // NOTE: This method is testing both createCall() and createQName()
    AstFactory astFactory = createTestAstFactory();


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
    StaticScope scope = compiler.getTranspilationNamespace();

    // createQName only accepts globally qualified qnames. While Foo.method is a global qualified
    // name, its '.call' property is not.
    // Foo.method.call(null, "hi", 2112)
    assertThrows(Exception.class, () -> astFactory.createQName(scope, "Foo.method.call"));
  }

  @Test
  public void testCreateQNameWithUnknownTypeFromString_jstype() {
    AstFactory astFactory = createTestAstFactory();

    Node objDotInnerDotStr = astFactory.createQNameWithUnknownType("obj.inner.str");

    assertNode(objDotInnerDotStr).matchesQualifiedName("obj.inner.str");
    Node objDotInner = objDotInnerDotStr.getFirstChild();
    Node obj = objDotInner.getFirstChild();

    assertNode(obj).hasJSTypeThat().isUnknown();
    assertNode(objDotInner).hasJSTypeThat().isUnknown();
    assertNode(objDotInnerDotStr).hasJSTypeThat().isUnknown();
  }

  @Test
  public void testCreateQNameWithUnknownTypeFromString_colors() {
    AstFactory astFactory = createTestAstFactoryWithColors();

    Node objDotInnerDotStr = astFactory.createQNameWithUnknownType("obj.inner.str");

    assertNode(objDotInnerDotStr).matchesQualifiedName("obj.inner.str");
    Node objDotInner = objDotInnerDotStr.getFirstChild();
    Node obj = objDotInner.getFirstChild();

    assertNode(obj).hasColorThat().isEqualTo(StandardColors.UNKNOWN);
    assertNode(objDotInner).hasColorThat().isEqualTo(StandardColors.UNKNOWN);
    assertNode(objDotInnerDotStr).hasColorThat().isEqualTo(StandardColors.UNKNOWN);
  }

  @Test
  public void testCreateQNameFromString() {
    AstFactory astFactory = createTestAstFactory();

    parseAndAddTypes(
        lines(
            "", //
            "const obj = {",
            "  inner: {",
            "    str: 'hi',",
            "  }",
            "};",
            ""));
    StaticScope scope = compiler.getTranspilationNamespace();

    Node objDotInnerDotStr = astFactory.createQName(scope, "obj.inner.str");

    assertNode(objDotInnerDotStr).matchesQualifiedName("obj.inner.str");
    Node objDotInner = objDotInnerDotStr.getFirstChild();
    Node obj = objDotInner.getFirstChild();

    assertNode(obj).hasJSTypeThat().toStringIsEqualTo("{inner: {str: string}}");
    assertNode(objDotInner).hasJSTypeThat().toStringIsEqualTo("{str: string}");
    assertNode(objDotInnerDotStr).hasJSTypeThat().isString();
  }

  @Test
  public void testCreateQNameFromBaseNamePlusStringIterable() {
    AstFactory astFactory = createTestAstFactory();

    parseAndAddTypes(
        lines(
            "", //
            "const obj = {",
            "  inner: {",
            "    str: 'hi',",
            "  }",
            "};",
            ""));
    StaticScope scope = compiler.getTranspilationNamespace();

    Node objDotInnerDotStr = astFactory.createQName(scope, "obj", ImmutableList.of("inner", "str"));

    assertNode(objDotInnerDotStr).matchesQualifiedName("obj.inner.str");
    Node objDotInner = objDotInnerDotStr.getFirstChild();
    Node obj = objDotInner.getFirstChild();

    assertNode(obj).hasJSTypeThat().toStringIsEqualTo("{inner: {str: string}}");
    assertNode(objDotInner).hasJSTypeThat().toStringIsEqualTo("{str: string}");
    assertNode(objDotInnerDotStr).hasJSTypeThat().isString();
  }

  @Test
  public void testCreateQNameFromStringVarArgs() {
    AstFactory astFactory = createTestAstFactory();

    parseAndAddTypes(
        lines(
            "", //
            "const obj = {",
            "  inner: {",
            "    str: 'hi',",
            "  }",
            "};",
            ""));

    Node objDotInnerDotStr =
        astFactory.createQName(compiler.getTranspilationNamespace(), "obj", "inner", "str");

    assertNode(objDotInnerDotStr).matchesQualifiedName("obj.inner.str");
    Node objDotInner = objDotInnerDotStr.getFirstChild();
    Node obj = objDotInner.getFirstChild();

    assertNode(obj).hasJSTypeThat().toStringIsEqualTo("{inner: {str: string}}");
    assertNode(objDotInner).hasJSTypeThat().toStringIsEqualTo("{str: string}");
    assertNode(objDotInnerDotStr).hasJSTypeThat().isString();
  }

  @Test
  public void testCreateQNameFromSimpleStringAndTypedScope() {
    AstFactory astFactory = createTestAstFactory();

    TypedScope scope = TypedScope.createGlobalScope(IR.root());
    scope.declare("x", IR.name("x"), getNativeType(JSTypeNative.NUMBER_TYPE), null, true);

    Node name = astFactory.createQNameFromTypedScope(scope, "x");

    assertNode(name).hasStringThat().isEqualTo("x");
    assertNode(name).hasJSTypeThat().isNumber();
  }

  @Test
  public void testCreateQNameFromDottedStringAndTypedScope() {
    AstFactory astFactory = createTestAstFactory();

    TypedScope scope = TypedScope.createGlobalScope(IR.root());
    // Declare a global "x" with the type "{y: number}".
    ObjectType objectWithYProp = getRegistry().createAnonymousObjectType(null);
    objectWithYProp.defineDeclaredProperty("y", getNativeType(JSTypeNative.NUMBER_TYPE), null);
    scope.declare("x", IR.name("x"), objectWithYProp, null, true);

    Node name = astFactory.createQNameFromTypedScope(scope, "x.y");

    assertNode(name).matchesQualifiedName("x.y");
    assertNode(name).hasJSTypeThat().isNumber();
    assertNode(name.getFirstChild()).hasJSTypeThat().isObjectTypeWithProperty("y");
  }

  @Test
  public void testCreateQNameFromStringAndTypedScope_crashesGivenMissingName() {
    AstFactory astFactory = createTestAstFactory();

    TypedScope scope = TypedScope.createGlobalScope(IR.root());

    assertThrows(Exception.class, () -> astFactory.createQNameFromTypedScope(scope, "x"));
  }

  @Test
  public void testCreateQNameFromStringAndTypedScope_crashesGivenLocalScope() {
    AstFactory astFactory = createTestAstFactory();

    Node root = IR.root();
    Node block = IR.block();
    root.addChildToFront(IR.script(block));

    TypedScope globalScope = TypedScope.createGlobalScope(root);
    globalScope.declare("x", IR.name("x"), getNativeType(JSTypeNative.NUMBER_TYPE), null, true);
    TypedScope localScope = new TypedScope(globalScope, block);

    astFactory.createQNameFromTypedScope(globalScope, "x");
    assertThrows(
        IllegalArgumentException.class,
        () -> astFactory.createQNameFromTypedScope(localScope, "x"));
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
    Node callNode = astFactory.createConstructorCall(type(classBNode), callee, arg1, arg2);

    assertNode(callNode).hasType(Token.CALL);
    assertThat(callNode.getBooleanProp(Node.FREE_CALL)).isTrue();
    assertThat(childList(callNode)).containsExactly(callee, arg1, arg2).inOrder();
    assertType(callNode.getJSType()).isEqualTo(classBInstanceType);
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

    Node emptyFunction = astFactory.createEmptyFunction(type(functionType));
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

    Node functionNode = astFactory.createFunction("bar", paramList, body, type(functionType));
    assertNode(functionNode).hasToken(Token.FUNCTION);
    assertType(functionNode.getJSType()).isEqualTo(functionType);
    Node functionNameNode = functionNode.getFirstChild();
    assertNode(functionNameNode).isName("bar");
    assertThat(childList(functionNode))
        .containsExactly(functionNameNode, paramList, body)
        .inOrder();
  }

  @Test
  public void testCreateMemberFunctionDef_jstypes() {
    AstFactory astFactory = createTestAstFactory();

    // just a quick way to get a valid function type
    Node root = parseAndAddTypes("function foo() {}");
    JSType functionType =
        root.getFirstChild() // script
            .getFirstChild() // function
            .getJSType();

    Node paramList = IR.paramList();
    Node body = IR.block();
    Node functionNode = astFactory.createFunction("", paramList, body, type(functionType));

    Node memberFunctionDef = astFactory.createMemberFunctionDef("bar", functionNode);
    assertNode(memberFunctionDef).hasToken(Token.MEMBER_FUNCTION_DEF);
    assertThat(memberFunctionDef.getString()).isEqualTo("bar");
    assertType(memberFunctionDef.getJSType()).isEqualTo(functionType);
  }

  @Test
  public void testCreateMemberFunctionDef_colors() {
    AstFactory astFactory = createTestAstFactoryWithColors();

    // just a quick way to get a valid function type
    Node root = parseAndAddColors("function foo() {}");
    Color functionType =
        root.getFirstChild() // script
            .getFirstChild() // function
            .getColor();

    Node paramList = IR.paramList();
    Node body = IR.block();
    Node functionNode = IR.function(IR.name(""), paramList, body).setColor(functionType);

    Node memberFunctionDef = astFactory.createMemberFunctionDef("bar", functionNode);
    assertNode(memberFunctionDef).hasToken(Token.MEMBER_FUNCTION_DEF);
    assertThat(memberFunctionDef.getString()).isEqualTo("bar");
    assertNode(memberFunctionDef).hasColorThat().isEqualTo(functionType);
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
  public void testCreateAssignFromNodes_jstypes() {
    AstFactory astFactory = createTestAstFactory();

    Node lhs = astFactory.createName("x", type(JSTypeNative.STRING_TYPE));
    Node rhs = astFactory.createNumber(0);

    Node assign = astFactory.createAssign(lhs, rhs);
    assertNode(assign).hasToken(Token.ASSIGN);
    assertNode(assign).hasFirstChildThat().isEqualTo(lhs);
    assertNode(assign).hasSecondChildThat().isEqualTo(rhs);
    assertNode(assign).hasJSTypeThat().isNumber(); // take the rhs type, not lhs type
  }

  @Test
  public void testCreateAssignFromNodes_colors() {
    AstFactory astFactory = createTestAstFactoryWithColors();

    Node lhs = astFactory.createName("x", type(StandardColors.STRING));
    Node rhs = astFactory.createNumber(0);

    Node assign = astFactory.createAssign(lhs, rhs);
    assertNode(assign).hasToken(Token.ASSIGN);
    assertNode(assign).hasFirstChildThat().isEqualTo(lhs);
    assertNode(assign).hasSecondChildThat().isEqualTo(rhs);
    assertNode(assign).hasColorThat().isEqualTo(StandardColors.NUMBER); // rhs type, not lhs type
  }

  @Test
  public void testCreateObjectLit_empty() {
    AstFactory astFactory = createTestAstFactory();

    // just a quick way to get a valid object literal type
    Node root = parseAndAddTypes("({})");
    JSType objectLitType =
        root.getFirstChild() // script
            .getFirstChild() // expression result
            .getOnlyChild() // object literal
            .getJSType();

    Node objectLit = astFactory.createObjectLit();

    assertType(objectLit.getJSType()).toStringIsEqualTo("{}");
    assertThat(objectLit.getJSType()).isInstanceOf(objectLitType.getClass());
    assertNode(objectLit).hasToken(Token.OBJECTLIT);
    assertNode(objectLit).hasChildren(false);
  }

  @Test
  public void testCreateObjectLit_empty_withColor() {
    AstFactory astFactory = createTestAstFactoryWithColors();

    Color objColor = Color.singleBuilder().setId(ColorId.fromAscii("1")).build();

    Node objectLit = astFactory.createObjectLit(type(JSTypeNative.UNKNOWN_TYPE, objColor));

    assertNode(objectLit).hasColorThat().isEqualTo(objColor);
    assertNode(objectLit).hasToken(Token.OBJECTLIT);
    assertNode(objectLit).hasChildren(false);
  }

  @Test
  public void testCreateObjectLit_withElements() {
    AstFactory astFactory = createTestAstFactory();

    // just a quick way to get a valid object literal type
    Node root = parseAndAddTypes("({})");
    JSType objectLitType =
        root.getFirstChild() // script
            .getFirstChild() // expression result
            .getOnlyChild() // object literal
            .getJSType();

    Node spread = IR.objectSpread(IR.name("a"));
    Node stringKey = IR.stringKey("b", IR.number(0));

    Node objectLit = astFactory.createObjectLit(spread, stringKey);

    assertType(objectLit.getJSType()).toStringIsEqualTo("{}");
    assertThat(objectLit.getJSType()).isInstanceOf(objectLitType.getClass());
    assertNode(objectLit).hasToken(Token.OBJECTLIT);

    assertNode(objectLit.getFirstChild()).isSameInstanceAs(spread);
    assertNode(objectLit.getSecondChild()).isSameInstanceAs(stringKey);
  }

  @Test
  public void testCreateObjectLit_withElementsAndType() {
    AstFactory astFactory = createTestAstFactory();

    // Try creating an object literal that implements Thenable and type it as one.
    final JSType thenableType = getRegistry().getNativeType(JSTypeNative.THENABLE_TYPE);
    Node thenStringKey =
        IR.stringKey(
            "then",
            astFactory.createZeroArgArrowFunctionForExpression(astFactory.createString("hi")));

    Node objectLit = astFactory.createObjectLit(type(thenableType), thenStringKey);

    assertThat(objectLit.getJSType()).isEqualTo(thenableType);
    assertNode(objectLit).hasToken(Token.OBJECTLIT);
    assertNode(objectLit).hasOneChildThat().isSameInstanceAs(thenStringKey);
  }

  @Test
  public void testCreateObjectLit_empty_colors() {
    AstFactory astFactory = createTestAstFactoryWithColors();

    Node objectLit = astFactory.createObjectLit();

    assertNode(objectLit).hasColorThat().isEqualTo(StandardColors.TOP_OBJECT);
    assertNode(objectLit).hasToken(Token.OBJECTLIT);
    assertNode(objectLit).hasChildren(false);
  }

  @Test
  public void testCreateDelProp_jstypes() {
    AstFactory astFactory = createTestAstFactory();

    Node getprop = IR.getprop(IR.name("obj"), "prop");

    Node delprop = astFactory.createDelProp(getprop);
    assertNode(delprop).hasToken(Token.DELPROP);
    assertType(delprop.getJSType()).isEqualTo(getNativeType(JSTypeNative.BOOLEAN_TYPE));
    assertNode(delprop).hasChildren(true);
  }

  @Test
  public void testCreateDelProp_colors() {
    AstFactory astFactory = createTestAstFactoryWithColors();

    Node getprop = IR.getprop(IR.name("obj"), "prop");

    Node delprop = astFactory.createDelProp(getprop);
    assertNode(delprop).hasToken(Token.DELPROP);
    assertNode(delprop).hasColorThat().isEqualTo(StandardColors.BOOLEAN);
    assertNode(delprop).hasChildren(true);
  }

  @Test
  public void testCreateSheq_jstypes() {
    AstFactory astFactory = createTestAstFactory();

    Node left = IR.string("left");
    Node right = IR.number(0);

    Node sheq = astFactory.createSheq(left, right);
    assertNode(sheq).hasToken(Token.SHEQ);
    assertType(sheq.getJSType()).isEqualTo(getNativeType(JSTypeNative.BOOLEAN_TYPE));
  }

  @Test
  public void testCreateSheq_colors() {
    AstFactory astFactory = createTestAstFactoryWithColors();

    Node left = IR.string("left");
    Node right = IR.number(0);

    Node sheq = astFactory.createSheq(left, right);
    assertNode(sheq).hasToken(Token.SHEQ);
    assertNode(sheq).hasColorThat().isEqualTo(StandardColors.BOOLEAN);
  }

  @Test
  public void testCreateEq_jstypes() {
    AstFactory astFactory = createTestAstFactory();

    Node left = IR.string("left");
    Node right = IR.number(0);

    Node sheq = astFactory.createEq(left, right);
    assertNode(sheq).hasToken(Token.EQ);
    assertType(sheq.getJSType()).isEqualTo(getNativeType(JSTypeNative.BOOLEAN_TYPE));
  }

  @Test
  public void testCreateEq_colors() {
    AstFactory astFactory = createTestAstFactoryWithColors();

    Node left = IR.string("left");
    Node right = IR.number(0);

    Node eq = astFactory.createEq(left, right);
    assertNode(eq).hasToken(Token.EQ);
    assertNode(eq).hasColorThat().isEqualTo(StandardColors.BOOLEAN);
  }

  @Test
  public void testCreateNe_jstypes() {
    AstFactory astFactory = createTestAstFactory();

    Node left = IR.string("left");
    Node right = IR.number(0);

    Node ne = astFactory.createNe(left, right);
    assertNode(ne).hasToken(Token.NE);
    assertType(ne.getJSType()).isEqualTo(getNativeType(JSTypeNative.BOOLEAN_TYPE));
  }

  @Test
  public void testCreateNe_colors() {
    AstFactory astFactory = createTestAstFactoryWithColors();

    Node left = IR.string("left");
    Node right = IR.number(0);

    Node ne = astFactory.createNe(left, right);
    assertNode(ne).hasToken(Token.NE);
    assertNode(ne).hasColorThat().isEqualTo(StandardColors.BOOLEAN);
  }

  @Test
  public void testCreateHook_jstypes() {
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
  public void testCreateHook_colors() {
    AstFactory astFactory = createTestAstFactoryWithColors();

    Node condition = IR.falseNode();
    Node left = astFactory.createString("left");
    Node right = astFactory.createNumber(0);

    Node hook = astFactory.createHook(condition, left, right);
    assertNode(hook).hasToken(Token.HOOK);
    assertNode(hook).hasColorThat().hasAlternates(StandardColors.STRING, StandardColors.NUMBER);
  }

  @Test
  public void testCreateArraylit_jstypes() {
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
  public void testCreateArraylit_colors() {
    // Given
    AstFactory astFactory = createTestAstFactoryWithColors();

    Node first = astFactory.createNumber(0);
    Node second = astFactory.createNumber(1);
    Node third = astFactory.createNumber(2);

    Node expected =
        parseAndAddColors("[0, 1, 2]")
            .getFirstChild() // Script
            .getFirstChild() // Expression
            .getFirstChild(); // Array

    // When
    Node array = astFactory.createArraylit(first, second, third);

    // Then
    assertNode(array).isEquivalentTo(expected);
    assertThat(array.getColor()).isEqualTo(expected.getColor());
  }

  @Test
  public void testCreateNewNode_jstypes() {
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
        astFactory.createNewNode(astFactory.createName("Example", type(classNode)), first, second);

    // Then
    assertNode(newExpr).isEquivalentTo(expected);
    assertType(newExpr.getJSType()).isEqualTo(expected.getJSType());
  }

  @Test
  public void testCreateNewNode_colors() {
    // Given
    AstFactory astFactory = createTestAstFactoryWithColors();

    Node first = astFactory.createNumber(0);
    Node second = astFactory.createNumber(1);

    Node classNode =
        parseAndAddColors(
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
            astFactory.createName("Example", type(classNode.getColor())), first, second);

    // Then
    assertNode(newExpr).isEquivalentTo(expected);
    assertNode(newExpr).hasColorThat().isEqualTo(expected.getColor());
  }

  private static ImmutableList<Node> childList(Node parent) {
    ImmutableList.Builder<Node> list = ImmutableList.builder();
    for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
      list.add(child);
    }
    return list.build();
  }
}
