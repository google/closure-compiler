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
import static com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactory.anyType;
import static com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactory.arrayType;
import static com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactory.booleanType;
import static com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactory.namedType;
import static com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactory.numberType;
import static com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactory.parameterizedType;
import static com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactory.stringType;
import static com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactory.voidType;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CodePrinter;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.testing.TestErrorManager;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Node.TypeDeclarationNode;
import com.google.javascript.rhino.Token;

import junit.framework.TestCase;

/**
 * Tests the AST generated when parsing code that includes type declarations
 * in the syntax.
 * <p>
 * (It tests both parsing from source to a parse tree, and conversion from a
 * parse tree to an AST in {@link IRFactory}
 * and {@link TypeDeclarationsIRFactory}.)
 *
 * @author martinprobst@google.com (Martin Probst)
 */
public class TypeSyntaxTest extends TestCase {

  private TestErrorManager testErrorManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    testErrorManager = new TestErrorManager();
  }

  private void expectErrors(String... errors) {
    testErrorManager.expectErrors(errors);
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
    expectErrors("Parse error. Bad type syntax - "
        + "can only have JSDoc or inline type annotations, not both");
    parse("var /** string */ foo: string = 'hello';");
  }

  public void testFunctionParamDeclaration() {
    Node fn = parse("function foo(x: string) {\n}").getFirstChild();
    Node param = fn.getFirstChild().getNext().getFirstChild();
    assertDeclaredType("string type", stringType(), param);
  }

  public void testFunctionParamDeclaration_defaultValue() {
    Node fn = parse("function foo(x: string = 'hello') {\n}").getFirstChild();
    Node param = fn.getFirstChild().getNext().getFirstChild();
    assertDeclaredType("string type", stringType(), param);
  }

  public void testFunctionParamDeclaration_destructuringArray() {
    // TODO(martinprobst): implement.
    expectErrors("Parse error. ',' expected");
    parse("function foo([x]: string) {}");
  }

  public void testFunctionParamDeclaration_destructuringArrayInner() {
    // TODO(martinprobst): implement.
    expectErrors("Parse error. ']' expected");
    parse("function foo([x: string]) {}");
  }

  public void testFunctionParamDeclaration_destructuringObject() {
    // TODO(martinprobst): implement.
    expectErrors("Parse error. ',' expected");
    parse("function foo({x}: string) {}");
  }

  public void testFunctionParamDeclaration_arrow() {
    Node fn = parse("(x: string) => 'hello' + x;").getFirstChild().getFirstChild();
    Node param = fn.getFirstChild().getNext().getFirstChild();
    assertDeclaredType("string type", stringType(), param);
  }

  public void testFunctionReturn() {
    Node fn = parse("function foo(): string {\n  return 'hello';\n}").getFirstChild();
    assertDeclaredType("string type", stringType(), fn);
  }

  public void testFunctionReturn_arrow() {
    Node fn = parse("(): string => 'hello';").getFirstChild().getFirstChild();
    assertDeclaredType("string type", stringType(), fn);
  }

  public void testFunctionReturn_typeInDocAndSyntax() throws Exception {
    expectErrors("Parse error. Bad type syntax - "
        + "can only have JSDoc or inline type annotations, not both");
    parse("function /** string */ foo(): string { return 'hello'; }");
  }

  public void testFunctionReturn_typeInJsdocOnly() throws Exception {
    parse("function /** string */ foo() { return 'hello'; }",
        "function/** string */foo() {\n  return 'hello';\n}");
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
  }

  public void testArrayType_missingClose() {
    expectErrors("Parse error. ']' expected");
    parse("var foo: string[;");
  }

  public void testArrayType_qualifiedType() {
    TypeDeclarationNode arrayOfString = arrayType(namedType("mymod.ns.Type"));
    assertVarType("string[]", arrayOfString, "var foo: mymod.ns.Type[];");
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
  }

  public void testParameterizedType_empty() {
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

  public void testMemberVariable() throws Exception {
    // Just make sure it round trips, no types for the moment.
    Node ast = parse("class Foo {\n  foo;\n}");
    Node classMembers = ast.getFirstChild().getLastChild();
    assertTreeEquals("has foo variable", Node.newString(Token.MEMBER_VARIABLE_DEF, "foo"),
        classMembers.getFirstChild());
  }

  public void testMemberVariable_generator() throws Exception {
    expectErrors("Parse error. Member variable cannot be prefixed by '*' (generator function)");
    parse("class X { *foo: number; }");
  }

  public void testComputedPropertyMemberVariable() throws Exception {
    parse("class Foo {\n  ['foo'];\n}");
  }

  public void testMemberVariable_type() {
    Node classDecl = parse("class X {\n  m1: string;\n  m2: number;\n}").getFirstChild();
    Node members = classDecl.getChildAtIndex(2);
    Node memberVariable = members.getFirstChild();
    assertDeclaredType("string field type", stringType(), memberVariable);
  }

  public void testMethodType() {
    Node classDecl = parse(
        "class X {\n"
        + "  m(p: number): string {\n"
        + "    return p + x;\n"
        + "  }\n"
        + "}").getFirstChild();
    Node members = classDecl.getChildAtIndex(2);
    Node method = members.getFirstChild().getFirstChild();
    assertDeclaredType("string return type", stringType(), method);
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

  private Node parse(String source) {
    return parse(source, source);
  }

  private Node parse(String source, String expected) {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT6_TYPED);
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
          .setTypeRegistry(compiler.getTypeRegistry())
          .build()  // does the actual printing.
          .trim();
      assertThat(actual).isEqualTo(expected);
    }

    return script;
  }
}
