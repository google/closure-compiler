/*
 * Copyright 2015 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.parsing;

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.testing.NodeSubject.assertNode;
import static com.google.javascript.rhino.TypeDeclarationsIR.anyType;
import static com.google.javascript.rhino.TypeDeclarationsIR.arrayType;
import static com.google.javascript.rhino.TypeDeclarationsIR.booleanType;
import static com.google.javascript.rhino.TypeDeclarationsIR.namedType;
import static com.google.javascript.rhino.TypeDeclarationsIR.numberType;
import static com.google.javascript.rhino.TypeDeclarationsIR.parameterizedType;
import static com.google.javascript.rhino.TypeDeclarationsIR.stringType;
import static com.google.javascript.rhino.TypeDeclarationsIR.voidType;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CodePrinter;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.testing.TestErrorManager;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Node.TypeDeclarationNode;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TypeDeclarationsIR;

import junit.framework.TestCase;

/**
 * Tests the AST generated when parsing code that includes type declarations
 * in the syntax.
 * <p>
 * (It tests both parsing from source to a parse tree, and conversion from a
 * parse tree to an AST in {@link IR} and {@link TypeDeclarationsIR}.)
 *
 * @author martinprobst@google.com (Martin Probst)
 */
public final class TypeSyntaxTest extends TestCase {

  private TestErrorManager testErrorManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    testErrorManager = new TestErrorManager();
  }

  private void expectErrors(String... errors) {
    testErrorManager.expectErrors(errors);
  }

  private void testNotEs6TypedFullError(String source, String error) {
    expectErrors(error);
    parse(source, LanguageMode.ECMASCRIPT6);
    expectErrors(error);
    parse(source, LanguageMode.ECMASCRIPT6_STRICT);
  }

  private void testNotEs6Typed(String source, String... features) {
    for (int i = 0; i < features.length; i++) {
      features[i] =
          "type syntax is only supported in ES6 typed mode: "
              + features[i]
              + ". Use --language_in=ECMASCRIPT6_TYPED to enable ES6 typed features.";
    }
    expectErrors(features);
    parse(source, LanguageMode.ECMASCRIPT6);
    expectErrors(features);
    parse(source, LanguageMode.ECMASCRIPT6_STRICT);
  }

  public void testVariableDeclaration() {
    assertVarType("any", anyType(), "var foo: any = 'hello';");
    assertVarType("number", numberType(), "var foo: number = 'hello';");
    assertVarType("boolean", booleanType(), "var foo: boolean = 'hello';");
    assertVarType("string", stringType(), "var foo: string = 'hello';");
    assertVarType("void", voidType(), "var foo: void = 'hello';");
    assertVarType("named type", namedType("hello"), "var foo: hello = 'hello';");
  }

  public void testVariableDeclaration_keyword() {
    expectErrors("Parse error. Unexpected token 'catch' in type expression");
    parse("var foo: catch;");
    expectErrors("Parse error. Unexpected token 'implements' in type expression");
    parse("var foo: implements;"); // strict mode keyword
  }

  public void testVariableDeclaration_errorIncomplete() {
    expectErrors("Parse error. Unexpected token '=' in type expression");
    parse("var foo: = 'hello';");
  }

  public void testTypeInDocAndSyntax() {
    expectErrors("Can only have JSDoc or inline type annotations, not both");
    parse("var /** string */ foo: string = 'hello';");
  }

  public void testTypedGetterSetterDeclaration() {
    Node n = parse("var x = {get a(): number {\n}};", LanguageMode.ECMASCRIPT6_TYPED);
    assertDeclaredType("number type", numberType(),
        n.getFirstFirstChild().getFirstFirstChild().getFirstChild());
    n = parse("var x = {set a(v: number) {\n}};", LanguageMode.ECMASCRIPT6_TYPED);
    assertDeclaredType("number type", numberType(),
        n.getFirstFirstChild().getFirstFirstChild().getFirstChild().getSecondChild()
            .getFirstChild());
  }

  public void testSetterDeclarationWithReturnType() {
    expectErrors("Parse error. setter should not have any returns");
    parse("var x = {set a(x): number {\n}};", LanguageMode.ECMASCRIPT6_TYPED);
  }

  public void testFunctionParamDeclaration() {
    Node fn = parse("function foo(x: string) {\n}").getFirstChild();
    Node param = fn.getSecondChild().getFirstChild();
    assertDeclaredType("string type", stringType(), param);
  }

  public void testFunctionParamDeclaration_defaultValue() {
    Node fn = parse("function foo(x: string = 'hello') {\n}").getFirstChild();
    Node param = fn.getSecondChild().getFirstChild();
    assertDeclaredType("string type", stringType(), param);
  }

  public void testFunctionParamDeclaration_destructuringArray() {
    parse("function foo([x]: string[]) {\n}");
  }

  public void testFunctionParamDeclaration_destructuringArrayInner() {
    // This syntax is not supported by TypeScript.
    expectErrors("Parse error. ']' expected");
    parse("function foo([x: string]) {}");
  }

  public void testFunctionParamDeclaration_destructuringObject() {
    parse("function foo({x}: any) {\n}");
  }

  public void disabled_testFunctionParamDeclaration_arrow() {
    Node fn = parse("(x: string) => 'hello' + x;").getFirstFirstChild();
    Node param = fn.getSecondChild().getFirstChild();
    assertDeclaredType("string type", stringType(), param);
  }

  public void testFunctionParamDeclaration_optionalParam() {
    parse("function foo(x?) {\n}");
  }

  public void testFunctionParamDeclaration_notEs6Typed() {
    testNotEs6Typed("function foo(x: string) {}", "type annotation");
    testNotEs6Typed("function foo(x?) {}", "optional parameter");
  }

  public void testFunctionReturn() {
    Node fn = parse("function foo(): string {\n  return 'hello';\n}").getFirstChild();
    assertDeclaredType("string type", stringType(), fn);
  }

  public void disabled_testFunctionReturn_arrow() {
    Node fn = parse("(): string => 'hello';").getFirstFirstChild();
    assertDeclaredType("string type", stringType(), fn);
  }

  public void testFunctionReturn_typeInDocAndSyntax() {
    expectErrors("Can only have JSDoc or inline type annotations, not both");
    parse("function /** string */ foo(): string { return 'hello'; }");
  }

  public void testFunctionReturn_typeInJsdocOnly() {
    parse("function /** string */ foo() { return 'hello'; }",
        "function/** string */ foo() {\n  return 'hello';\n}");
  }

  public void testCompositeType() {
    Node varDecl = parse("var foo: mymod.ns.Type;").getFirstChild();
    TypeDeclarationNode expected = namedType(ImmutableList.of("mymod", "ns", "Type"));
    assertDeclaredType("mymod.ns.Type", expected, varDecl.getFirstChild());
  }

  public void testCompositeType_trailingDot() {
    expectErrors("Parse error. 'identifier' expected");
    parse("var foo: mymod.Type.;");
  }

  public void testArrayType() {
    TypeDeclarationNode arrayOfString = arrayType(stringType());
    assertVarType("string[]", arrayOfString, "var foo: string[];");

    parse("var foo: string[][];");
  }

  public void testArrayType_empty() {
    expectErrors("Parse error. Unexpected token '[' in type expression");
    parse("var x: [];");
  }

  public void testArrayType_missingClose() {
    expectErrors("Parse error. ']' expected");
    parse("var foo: string[;");
  }

  public void testArrayType_qualifiedType() {
    TypeDeclarationNode arrayOfString = arrayType(namedType("mymod.ns.Type"));
    assertVarType("string[]", arrayOfString, "var foo: mymod.ns.Type[];");
  }

  public void testArrayType_trailingParameterizedType() {
    expectErrors("Parse error. Semi-colon expected");
    parse("var x: Foo[]<Bar>;");
  }

  public void testRecordType() {
    parse("var x: {p: string, q: string, r: string};");
    parse("var x: {p: string, q: string}[];");
    parse("var x: {p: string, q: string} | string;");
    parse("var x: (o: {p: string, q: string}) => r;");
  }

  public void testParameterizedType() {
    TypeDeclarationNode parameterizedType =
        parameterizedType(
            namedType("my.parameterized.Type"),
            ImmutableList.of(
                namedType("ns.A"),
                namedType("ns.B")));
    assertVarType("parameterized type 2 args", parameterizedType,
        "var x: my.parameterized.Type<ns.A, ns.B>;");

    parse("var x: Foo<Bar<Baz>>;");
    parse("var x: A<B<C<D>>>;");
  }

  public void testParameterizedType_empty() {
    expectErrors("Parse error. Unexpected token '>' in type expression");
    parse("var x: my.parameterized.Type<>;");
    expectErrors("Parse error. Unexpected token '>' in type expression");
    parse("var x: my.parameterized.Type<ns.A, >;");
  }

  public void testParameterizedType_noArgs() {
    expectErrors("Parse error. Unexpected token '>' in type expression");
    parse("var x: my.parameterized.Type<>;");
  }

  public void testParameterizedType_trailing1() {
    expectErrors("Parse error. '>' expected");
    parse("var x: my.parameterized.Type<ns.A;");
  }

  public void testParameterizedType_trailing2() {
    expectErrors("Parse error. Unexpected token ';' in type expression");
    parse("var x: my.parameterized.Type<ns.A,;");
  }

  public void testParameterizedArrayType() {
    parse("var x: Foo<Bar>[];");
  }

  public void testUnionType() {
    parse("var x: string | number[];");
    parse("var x: number[] | string;");
    parse("var x: Array<Foo> | number[];");
    parse("var x: (string | number)[];");

    Node ast = parse("var x: string | number[] | Array<Foo>;");
    TypeDeclarationNode union = (TypeDeclarationNode)
        (ast.getFirstFirstChild().getProp(Node.DECLARED_TYPE_EXPR));
    assertEquals(3, union.getChildCount());
  }

  public void testUnionType_empty() {
    expectErrors("Parse error. Unexpected token '|' in type expression");
    parse("var x: |;");
    expectErrors("Parse error. 'identifier' expected");
    parse("var x: number |;");
    expectErrors("Parse error. Unexpected token '|' in type expression");
    parse("var x: | number;");
  }

  public void testUnionType_trailingParameterizedType() {
    expectErrors("Parse error. Semi-colon expected");
    parse("var x: (Foo|Bar)<T>;");
  }

  public void testUnionType_notEs6Typed() {
    testNotEs6Typed("var x: string | number[] | Array<Foo>;", "type annotation");
  }

  public void testParenType_empty() {
    expectErrors("Parse error. Unexpected token ')' in type expression");
    parse("var x: ();");
  }

  public void testFunctionType() {
    parse("var n: (p1) => boolean;");
    parse("var n: (p1, p2) => boolean;");
    parse("var n: (p1: string) => boolean;");
    parse("var n: (p1: string, p2: number) => boolean;");
    parse("var n: () => () => number;");
    parse("var n: (p1: string) => {};");
    // parse("(number): () => number => number;");

    Node ast = parse("var n: (p1: string, p2: number) => boolean[];");
    TypeDeclarationNode function = (TypeDeclarationNode)
        (ast.getFirstFirstChild().getProp(Node.DECLARED_TYPE_EXPR));
    assertNode(function).hasType(Token.FUNCTION_TYPE);

    Node ast2 = parse("var n: (p1: string, p2: number) => boolean | number;");
    TypeDeclarationNode function2 = (TypeDeclarationNode)
        (ast2.getFirstFirstChild().getProp(Node.DECLARED_TYPE_EXPR));
    assertNode(function2).hasType(Token.FUNCTION_TYPE);

    Node ast3 = parse("var n: (p1: string, p2: number) => Array<Foo>;");
    TypeDeclarationNode function3 = (TypeDeclarationNode)
        (ast3.getFirstFirstChild().getProp(Node.DECLARED_TYPE_EXPR));
    assertNode(function3).hasType(Token.FUNCTION_TYPE);
  }

  public void testFunctionType_optionalParam() {
    parse("var n: (p1?) => boolean;");
    parse("var n: (p1?: string) => boolean;");
    parse("var n: (p1?: string, p2?) => boolean;");
  }

  public void testFunctionType_illegalParam() {
    expectErrors("Parse error. Unexpected token '...' in type expression");
    parse("var n : (...p1 p2) => number;");
    expectErrors("Parse error. ')' expected");
    parse("var n: (p1 = 5) => number;");
    expectErrors("Parse error. ')' expected");
    parse("var n: (p1 : p2 : p3) => number;");
    expectErrors("Parse error. ')' expected");
    parse("var n: (p1 : p2?) => number;");
    expectErrors("Parse error. ')' expected");
    parse("var n: (p1 : p2 = p3) => number;");
    expectErrors("Parse error. ')' expected");
    parse("var n: ({x, y}, z) => number;");
    expectErrors("Parse error. Unexpected token '[' in type expression");
    parse("var n: ([x, y], z) => number;");
  }

  public void testFunctionType_restParam() {
    parse("var n: (...p1) => boolean;");
    parse("var n: (...p1: number[]) => boolean;");
    parse("var n: (p0: number, ...p1) => boolean;");
    parse("var n: (p0?: number, ...p1) => boolean;");
  }

  public void testFunctionType_restParamNotArrayType() {
    expectErrors("Parse error. A rest parameter must be of an array type.");
    parse("var n: (...p1:number) => boolean;");
  }

  public void testFunctionType_restNotLastParam() {
    expectErrors("Parse error. A rest parameter must be last in a parameter list.");
    parse("var n: (...p0, p1) => boolean;");
    expectErrors("Parse error. A rest parameter must be last in a parameter list.");
    parse("var n: (...p0, ...p1) => boolean;");
  }

  public void testFunctionType_requiredParamAfterOptional() {
    expectErrors("Parse error. A required parameter cannot follow an optional parameter.");
    parse("var n: (p0?, p1) => boolean;");
  }

  public void testFunctionType_bothRestAndOptionalParam() {
    expectErrors("Parse error. Unexpected token '...' in type expression");
    parse("var n: (...p0?:number) => boolean;");
  }

  public void testFunctionType_incomplete() {
    expectErrors("Parse error. Unexpected token ';' in type expression");
    parse("var n: (p1:string) =>;");
    expectErrors("Parse error. Unexpected token '=>' in type expression");
    parse("var n: => boolean;");
  }

  public void testFunctionType_missingParens() {
    expectErrors("Parse error. Semi-colon expected");
    parse("var n: p1 => boolean;");
    expectErrors("Parse error. Semi-colon expected");
    parse("var n: p1:string => boolean;");
    expectErrors("Parse error. Semi-colon expected");
    parse("var n: p1:string, p2:number => boolean;");
  }

  public void testFunctionType_notEs6Typed() {
    testNotEs6Typed("var n: (p1:string) => boolean;", "type annotation");
    testNotEs6Typed("var n: (p1?) => boolean;", "type annotation", "optional parameter");
  }

  public void testInterface() {
    parse("interface I {\n}");
    parse("interface Foo extends Bar, Baz {\n}");
    parse("interface I {\n  foo: string;\n}");
    parse("interface I {\n  foo(p: boolean): string;\n}");
    parse("interface I {\n  foo<T>(p: boolean): string;\n}");
    parse("interface I {\n  *foo(p: boolean);\n}");

    expectErrors("Parse error. ',' expected");
    parse("interface I { foo(p: boolean): string {}}");
    expectErrors("Parse error. '}' expected");
    parse("if (true) { interface I {} }");

    parse("interface I {\n  (p: boolean): string;\n}");
    parse("interface I {\n  new (p: boolean): string;\n}");
    parse("interface I {\n  [foo: string]: number;\n}");
  }

  public void testInterface_notEs6Typed() {
    testNotEs6Typed("interface I { foo: string;}", "interface", "type annotation");
  }

  public void testInterface_disallowExpression() {
    expectErrors("Parse error. primary expression expected");
    parse("var i = interface {};");
  }

  public void testInterfaceMember() {
    Node ast = parse("interface I {\n  foo;\n}");
    Node classMembers = ast.getFirstChild().getLastChild();
    assertTreeEquals(
        "has foo variable",
        Node.newString(Token.MEMBER_VARIABLE_DEF, "foo"),
        classMembers.getFirstChild());
  }

  public void testEnum() {
    parse("enum E {\n  a,\n  b,\n  c\n}");

    expectErrors("Parse error. '}' expected");
    parse("if (true) { enum E {} }");
  }

  public void testEnum_notEs6Typed() {
    testNotEs6Typed("enum E {a, b, c}", "enum");
  }

  public void testMemberVariable() {
    Node ast = parse("class Foo {\n  foo;\n}");
    Node classMembers = ast.getFirstChild().getLastChild();
    assertTreeEquals("has foo variable", Node.newString(Token.MEMBER_VARIABLE_DEF, "foo"),
        classMembers.getFirstChild());
  }

  public void testMemberVariable_generator() {
    expectErrors("Parse error. Member variable cannot be prefixed by '*' (generator function)");
    parse("class X { *foo: number; }");
  }

  public void testComputedPropertyMemberVariable() {
    parse("class Foo {\n  ['foo'];\n}");
  }

  public void testMemberVariable_type() {
    Node classDecl = parse("class X {\n  m1: string;\n  m2: number;\n}").getFirstChild();
    Node members = classDecl.getChildAtIndex(2);
    Node memberVariable = members.getFirstChild();
    assertDeclaredType("string field type", stringType(), memberVariable);
  }

  public void testMemberVariable_notEs6Typed() {
    testNotEs6Typed("class Foo {\n  foo;\n}", "member variable in class");
    testNotEs6Typed("class Foo {\n  ['foo'];\n}", "member variable in class", "computed property");
  }

  public void testMethodType() {
    Node classDecl = parse(
        "class X {\n"
        + "  m(p: number): string {\n"
        + "    return p + x;\n"
        + "  }\n"
        + "}").getFirstChild();
    Node members = classDecl.getChildAtIndex(2);
    Node method = members.getFirstFirstChild();
    assertDeclaredType("string return type", stringType(), method);
  }

  public void testGenericInterface() {
    parse("interface Foo<T> {\n}");
    parse("interface J<F extends Array<I<number>>> {\n}");

    testNotEs6Typed("interface Foo<T> {\n}", "interface", "generics");
  }

  public void testGenericClass() {
    parse("class Foo<T> {\n}");
    parse("class Foo<U, V> {\n}");
    parse("class Foo<U extends () => boolean, V> {\n}");
    parse("var Foo = class<T> {\n};");

    testNotEs6Typed("class Foo<T> {}", "generics");
  }

  public void testGenericFunction() {
    parse("function foo<T>() {\n}");
    // parse("var x = <K, V>(p) => 3;");
    parse("class Foo {\n  f<T>() {\n  }\n}");
    parse("(function<T>() {\n})();");
    parse("function* foo<T>() {\n}");

    expectErrors("Parse error. Unexpected token '<' in type expression");
    parse("var n: (<T>p1) => boolean;");
    expectErrors("Parse error. '>' expected");
    parse("function foo<T() {\n}");
    expectErrors("Parse error. 'identifier' expected");
    parse("function foo<>() {\n}");

    // Typecasting, not supported yet.
    expectErrors("Parse error. primary expression expected");
    parse("var x = <T>((p:T) => 3);");

    testNotEs6Typed("function foo<T>() {}", "generics");
  }

  public void testImplements() {
    parse("class Foo implements Bar, Baz {\n}");
    parse("class Foo extends Bar implements Baz {\n}");

    testNotEs6Typed("class Foo implements Bar {\n}", "implements");
  }

  public void testTypeAlias() {
    parse("type Foo = number;");

    expectErrors("Parse error. Semi-colon expected");
    parse("if (true) { type Foo = number; }");

    testNotEs6Typed("type Foo = number;", "type alias");
  }

  public void testAmbientDeclaration() {
    parse("declare var x, y;");
    parse("declare var x;\ndeclare var y;");
    parse("declare let x;");
    parse("declare const x;");
    parse("declare function foo();");
    parse("declare class Foo {\n  constructor();\n  foo();\n}");
    parse("declare class Foo {\n  static *foo(bar: string);\n}");
    parse("declare class Foo {\n}\ndeclare class Bar {\n}");
    parse("declare enum Foo {\n}");
    parse("declare namespace foo {\n}");
    parse("declare namespace foo {\n  class A {\n  }\n  class B extends A {\n  }\n}");

    expectErrors("Parse error. Ambient variable declaration may not have initializer");
    parse("declare var x = 3;");
    expectErrors("Parse error. Ambient variable declaration may not have initializer");
    parse("declare const x = 3;");
    expectErrors("Parse error. Semi-colon expected");
    parse("declare var x declare var y;");
    expectErrors("Parse error. Semi-colon expected");
    parse("declare function foo() {}");
    expectErrors("Parse error. Semi-colon expected");
    parse("if (true) { declare var x; }");
    expectErrors("Parse error. Semi-colon expected");
    parse("declare class Foo {\n  constructor() {}\n};");

    testNotEs6Typed("declare var x;", "ambient declaration");
  }

  public void testExportDeclaration() {
    parse("export interface I {\n}\nexport class C implements I {\n}");
    parse("export declare class A {\n}\nexport declare class B extends A {\n}");

    expectErrors("Parse error. Semi-colon expected");
    parse("export var x export var y");
  }

  public void testTypeQuery() {
    parse("var x: typeof y;");
    parse("var x: typeof Foo.Bar.Baz;");
    parse("var x: typeof y | Bar;");

    expectErrors("Parse error. 'identifier' expected");
    parse("var x : typeof Foo.Bar.");
    testNotEs6Typed("var x: typeof y;", "type annotation");
  }

  public void testAccessibilityModifier() {
    parse("class Foo {\n  private constructor() {\n  }\n}");
    parse("class Foo {\n  protected static bar: number;\n}");
    parse("class Foo {\n  protected bar() {\n  }\n}");
    parse("class Foo {\n  private get() {\n  }\n}");
    parse("class Foo {\n  private set() {\n  }\n}");
    parse("class Foo {\n  private ['foo']() {\n  }\n}");
    parse("class Foo {\n  private [Symbol.iterator]() {\n  }\n}");
    parse("class Foo {\n  private ['foo'];\n}");

    // TODO(moz): Enable this
    //parse("class Foo {\n  constructor(public bar) {}}");

    expectErrors("Parse error. primary expression expected");
    parse("public var x;");
    expectErrors("Parse error. primary expression expected");
    parse("public function foo() {}");
    expectErrors("Parse error. Semi-colon expected");
    parse("class Foo { static private constructor() {}}");


    testNotEs6TypedFullError(
        "class Foo { private constructor() {} }",
        "Parse error. Accessibility modifier is only supported in ES6 typed mode");
    testNotEs6TypedFullError(
        "class Foo { protected bar; }",
        "Parse error. Accessibility modifier is only supported in ES6 typed mode");
    testNotEs6TypedFullError(
        "class Foo { protected bar() {} }",
        "Parse error. Accessibility modifier is only supported in ES6 typed mode");
    testNotEs6TypedFullError(
        "class Foo { private get() {} }",
        "Parse error. Accessibility modifier is only supported in ES6 typed mode");
    testNotEs6TypedFullError(
        "class Foo { private set() {} }",
        "Parse error. Accessibility modifier is only supported in ES6 typed mode");
    testNotEs6TypedFullError(
        "class Foo { private [Symbol.iterator]() {} }",
        "Parse error. Accessibility modifier is only supported in ES6 typed mode");
  }

  public void testOptionalProperty() {
    parse("interface I {\n  foo?: number;\n}");
    parse("interface I {\n  foo?(): number;\n}");
    parse("type I = {foo?: number};");
    parse("var x: {foo?: number};");

    expectErrors("Parse error. Semi-colon expected");
    parse("class C { foo?: number; }");
  }

  public void testParamNoInitializer() {
    expectErrors("Parse error. ',' expected");
    parse("declare function foo(bar = 3);");
    expectErrors("Parse error. ',' expected");
    parse("interface I { foo(bar = 3); }");
    expectErrors("Parse error. ',' expected");
    parse("declare class C { foo(bar = 3); }");
  }

  public void testParamDestructuring() {
    parse("declare function foo([bar]);");
    parse("declare function foo({bar:bar});");

    expectErrors("Parse error. ',' expected");
    parse("interface I { foo(bar = 3); }");
    expectErrors("Parse error. ',' expected");
    parse("declare class C { foo(bar = 3); }");

    expectErrors("Parse error. Unexpected token '[' in type expression");
    parse("var x: ([foo]) => number;");
    expectErrors("Parse error. Semi-colon expected");
    parse("var x: ({foo:foo}) => number;");
  }

  public void testIndexSignature() {
    parse("interface I {\n  [foo: number]: number;\n}");
    parse("var i: {[foo: number]: number;};");
    parse("class C {\n  [foo: number]: number;\n}");

    expectErrors("Parse error. ':' expected");
    parse("interface I { [foo: number]; }");
    expectErrors("Parse error. ':' expected");
    parse("interface I { [foo]: number; }");
    expectErrors("Parse error. Index signature parameter type must be 'string' or 'number'");
    parse("interface I {\n  [foo: any]: number;\n}");

    testNotEs6Typed("class C {\n  [foo: number]: number;\n}",
        "index signature", "type annotation", "type annotation");
  }

  public void testCallSignature() {
    parse("interface I {\n  (foo: number): number;\n}");
    parse("interface I {\n  <T>(foo: number): number;\n}");
    parse("var i: {(foo: number): number;};");

    testNotEs6Typed("interface I { (foo); }", "interface", "call signature");
  }

  public void testConstructSignature() {
    parse("interface I {\n  new (foo: number): number;\n}");
    parse("interface I {\n  new <T>(foo: number): number;\n}");
    parse("var i: {new (foo: number): number;};");

    testNotEs6Typed("interface I { new (foo); }", "interface", "constructor signature");
  }

  public void testSpecializedSignature() {
    parse("declare function foo(bar: 'string');");
    parse("interface I {\n  foo(bar: 'string'): number;\n}");

    expectErrors("Parse error. Unexpected token 'string literal' in type expression");
    parse("var x: 'string'");
  }

  public void testNamespace() {
    parse("namespace foo {\n}");
    parse("namespace foo.bar.baz {\n}");

    parse("namespace foo {\n  interface I {\n  }\n}");
    parse("namespace foo {\n  class C {\n  }\n}");
    parse("namespace foo {\n  enum E {\n  }\n}");
    parse("namespace foo {\n  function f() {\n  }\n}");
    parse("namespace foo {\n  declare var foo\n}");
    parse("namespace foo {\n  namespace bar {\n  }\n}");
    parse("namespace foo {\n  interface I {\n  }\n  class C {\n  }\n}");
    parse("namespace foo {\n  type Foo = number;\n}");

    parse("namespace foo {\n  export interface I {\n  }\n}");
    parse("namespace foo {\n  export class C {\n  }\n}");
    parse("namespace foo {\n  export enum E {\n  }\n}");
    parse("namespace foo {\n  export function f() {\n  }\n}");
    parse("namespace foo {\n  export declare var foo\n}");
    parse("namespace foo {\n  export namespace bar {\n  }\n}");
    parse("namespace foo {\n  export type Foo = number;\n}");

    expectErrors("Parse error. Semi-colon expected");
    parse("namespace {}");
    expectErrors("Parse error. Semi-colon expected");
    parse("namespace 'foo' {}"); // External modules are not supported

    testNotEs6Typed("namespace foo {}", "namespace declaration");
  }

  public void testAmbientNameSpace() {
    parse("declare namespace foo {\n}");
    parse("declare namespace foo.bar.baz {\n}");

    parse("declare namespace foo {\n  interface I {\n  }\n}");
    parse("declare namespace foo {\n  class I {\n    bar();\n  }\n}");
    parse("declare namespace foo {\n  enum E {\n  }\n}");
    parse("declare namespace foo {\n  function f();\n}");
    parse("declare namespace foo {\n  var foo\n}");
    parse("declare namespace foo {\n  namespace bar {\n  }\n}");
    parse("declare namespace foo {\n  interface I {\n  }\n  class C {\n  }\n}");

    parse("declare namespace foo {\n  export interface I {\n  }\n}");
    parse("declare namespace foo {\n  export class I {\n    bar();\n  }\n}");
    parse("declare namespace foo {\n  export enum E {\n  }\n}");
    parse("declare namespace foo {\n  export function f();\n}");
    parse("declare namespace foo {\n  export var foo\n}");
    parse("declare namespace foo {\n  export namespace bar {\n  }\n}");
    parse("declare namespace foo {\n  export interface I {\n  }\n  export class C {\n  }\n}");

    expectErrors("Parse error. Semi-colon expected");
    parse("declare namespace foo { class C { bar() {} }}");
    expectErrors("Parse error. Ambient variable declaration may not have initializer");
    parse("declare namespace foo { var a = 3; }");
    expectErrors("Parse error. Ambient variable declaration may not have initializer");
    parse("declare namespace foo { export var a = 3; }");
    expectErrors("Parse error. Semi-colon expected");
    parse("declare namespace foo { function bar() {} }");
    expectErrors("Parse error. '}' expected");
    parse("declare namespace foo { type Foo = number; }");

    testNotEs6Typed("declare namespace foo {}", "ambient declaration", "namespace declaration");
  }

  private void assertVarType(String message, TypeDeclarationNode expectedType, String source) {
    Node varDecl = parse(source, source).getFirstChild();
    assertDeclaredType(message, expectedType, varDecl.getFirstChild());
  }

  private void assertDeclaredType(String message, TypeDeclarationNode expectedType, Node typed) {
    assertTreeEquals(message, expectedType, typed.getDeclaredTypeExpression());
  }

  private void assertTreeEquals(String message, Node expected, Node actual) {
    String treeDiff = expected.checkTreeEquals(actual);
    assertNull(message + ": " + treeDiff, treeDiff);
  }

  private Node parse(String source, String expected, LanguageMode languageIn) {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(languageIn);
    options.setLanguageOut(LanguageMode.ECMASCRIPT6_TYPED);
    options.setPreserveTypeAnnotations(true);
    options.setPrettyPrint(true);
    options.setLineLengthThreshold(80);
    options.setPreferSingleQuotes(true);

    Compiler compiler = new Compiler();
    compiler.setErrorManager(testErrorManager);
    compiler.initOptions(options);

    Node script = compiler.parse(SourceFile.fromCode("[test]", source));

    // Verifying that all warnings were seen
    assertTrue("Missing an error", testErrorManager.hasEncounteredAllErrors());
    assertTrue("Missing a warning", testErrorManager.hasEncounteredAllWarnings());

    if (script != null && testErrorManager.getErrorCount() == 0) {
      // if it can be parsed, it should round trip.
      String actual = new CodePrinter.Builder(script)
          .setCompilerOptions(options)
          .setTypeRegistry(compiler.getTypeIRegistry())
          .build() // does the actual printing.
          .trim();
      assertThat(actual).isEqualTo(expected);
    }

    return script;
  }

  private Node parse(String source, LanguageMode languageIn) {
    return parse(source, source, languageIn);
  }

  private Node parse(String source) {
    return parse(source, source, LanguageMode.ECMASCRIPT6_TYPED);
  }

  private Node parse(String source, String expected) {
    return parse(source, expected, LanguageMode.ECMASCRIPT6_TYPED);
  }
}
