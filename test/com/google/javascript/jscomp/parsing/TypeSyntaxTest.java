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

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CodePrinter;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.testing.TestErrorManager;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Node.TypeDeclarationNode;

import junit.framework.TestCase;

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

  private void expectWarnings(String... warnings) {
    testErrorManager.expectWarnings(warnings);
  }

  public void testVariableDeclaration() {
    assertVarType("any", TypeDeclarationsIRFactory.anyType(),
        "var foo: any = 'hello';");
    assertVarType("number", TypeDeclarationsIRFactory.numberType(),
        "var foo: number = 'hello';");
    assertVarType("boolean", TypeDeclarationsIRFactory.booleanType(),
        "var foo: boolean = 'hello';");
    assertVarType("string", TypeDeclarationsIRFactory.stringType(),
        "var foo: string = 'hello';");
    assertVarType("void", TypeDeclarationsIRFactory.voidType(),
        "var foo: void = 'hello';");
    assertVarType("named type", TypeDeclarationsIRFactory.namedType("hello"),
        "var foo: hello = 'hello';");
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
    Node paramType = fn.getFirstChild().getNext().getFirstChild().getDeclaredTypeExpression();
    assertTreeEquals("string type", TypeDeclarationsIRFactory.stringType(), paramType);
  }

  public void testFunctionParamDeclaration_defaultValue() {
    Node fn = parse("function foo(x: string = 'hello') {\n}").getFirstChild();
    Node paramType = fn.getFirstChild().getNext().getFirstChild().getDeclaredTypeExpression();
    assertTreeEquals("string type", TypeDeclarationsIRFactory.stringType(), paramType);
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
    Node paramType = fn.getFirstChild().getNext().getFirstChild().getDeclaredTypeExpression();
    assertTreeEquals("string type", TypeDeclarationsIRFactory.stringType(), paramType);
  }

  public void testFunctionReturn() {
    Node fn = parse("function foo(): string {\n  return'hello';\n}").getFirstChild();
    Node fnType = fn.getDeclaredTypeExpression();
    assertTreeEquals("string type", TypeDeclarationsIRFactory.stringType(), fnType);
  }

  public void testFunctionReturn_arrow() {
    Node fn = parse("(): string => 'hello';").getFirstChild().getFirstChild();
    Node fnType = fn.getDeclaredTypeExpression();
    assertTreeEquals("string type", TypeDeclarationsIRFactory.stringType(), fnType);
  }

  public void testFunctionReturn_typeInDocAndSyntax() throws Exception {
    expectErrors("Parse error. Bad type syntax - "
        + "can only have JSDoc or inline type annotations, not both");
    parse("function /** string */ foo(): string { return 'hello'; }");
  }

  public void testFunctionReturn_typeInJsdocOnly() throws Exception {
    parse("function /** string */ foo() { return 'hello'; }",
        "function/** string */foo() {\n  return'hello';\n}");
  }

  public void testCompositeType() {
    Node varDecl = parse("var foo: mymod.ns.Type;").getFirstChild();
    Node type = varDecl.getFirstChild().getDeclaredTypeExpression();
    TypeDeclarationNode expected =
        TypeDeclarationsIRFactory.namedType(ImmutableList.of("mymod", "ns", "Type"));
    assertTreeEquals("mymod.ns.Type", expected, type);
  }

  public void testCompositeType_trailingDot() {
    expectErrors("Parse error. 'identifier' expected");
    parse("var foo: mymod.Type.;");
  }

  public void testArrayType() {
    TypeDeclarationNode arrayOfString =
        TypeDeclarationsIRFactory.arrayType(TypeDeclarationsIRFactory.stringType());
    assertVarType("string[]", arrayOfString, "var foo: string[];");
  }

  public void testArrayType_missingClose() {
    expectErrors("Parse error. ']' expected");
    parse("var foo: string[;");
  }

  public void testArrayType_qualifiedType() {
    TypeDeclarationNode arrayOfString =
        TypeDeclarationsIRFactory.arrayType(TypeDeclarationsIRFactory.namedType("mymod.ns.Type"));
    assertVarType("string[]", arrayOfString, "var foo: mymod.ns.Type[];");
  }

  private void assertVarType(String message, Node expectedType, String source) {
    Node varDecl = parse(source, source).getFirstChild();
    Node varType = varDecl.getFirstChild().getDeclaredTypeExpression();
    assertTreeEquals(message, expectedType, varType);
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
      assertEquals(expected, actual);
    }

    return script;
  }
}
