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

import com.google.javascript.jscomp.CodePrinter;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.testing.TestErrorManager;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Node.TypeDeclarationNode;
import com.google.javascript.rhino.testing.BaseJSTypeTestCase;

public class TypeSyntaxTest extends BaseJSTypeTestCase {

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
    Node varDecl = parse("var foo: string = 'hello';").getFirstChild();
    String treeDelta = TypeDeclarationsIRFactory.stringType()
        .checkTreeEquals(varDecl.getFirstChild().getDeclaredTypeExpression());
    assertNull(treeDelta);  }

  public void testVariableDeclaration_errorIncomplete() {
    expectErrors("Parse error. 'identifier' expected");
    parse("var foo: = 'hello';");
  }

  public void testTypeInDocAndSyntax() {
    expectErrors("Parse error. Bad type syntax - "
        + "can only have JSDoc or inline type annotations, not both");
    parse("var /** string */ foo: string = 'hello';");
  }

  public void testFunctionParamDeclaration() {
    Node fn = parse("function foo(x: string) {\n}").getFirstChild();
    TypeDeclarationNode paramType =
        fn.getFirstChild().getNext().getFirstChild().getDeclaredTypeExpression();
    String treeDelta = TypeDeclarationsIRFactory.stringType().checkTreeEquals(paramType);
    assertNull(treeDelta);
  }

  public void testFunctionParamDeclaration_defaultValue() {
    Node fn = parse("function foo(x: string = 'hello') {\n}").getFirstChild();
    TypeDeclarationNode paramType =
        fn.getFirstChild().getNext().getFirstChild().getDeclaredTypeExpression();
    String treeDelta = TypeDeclarationsIRFactory.stringType().checkTreeEquals(paramType);
    assertNull(treeDelta);
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
    TypeDeclarationNode paramType =
        fn.getFirstChild().getNext().getFirstChild().getDeclaredTypeExpression();
    String treeDelta = TypeDeclarationsIRFactory.stringType().checkTreeEquals(paramType);
    assertNull(treeDelta);
  }

  public void testFunctionReturn() {
    Node fn = parse("function foo(): string {\n  return'hello';\n}").getFirstChild();
    TypeDeclarationNode returnType = fn.getDeclaredTypeExpression();
    String treeDelta = TypeDeclarationsIRFactory.stringType().checkTreeEquals(returnType);
    assertNull(treeDelta);
  }

  public void testFunctionReturn_arrow() {
    Node fn = parse("(): string => 'hello';").getFirstChild().getFirstChild();
    TypeDeclarationNode returnType = fn.getDeclaredTypeExpression();
    String treeDelta = TypeDeclarationsIRFactory.stringType().checkTreeEquals(returnType);
    assertNull(treeDelta);  }

  public void testFunctionReturn_typeInDocAndSyntax() throws Exception {
    expectErrors("Parse error. Bad type syntax - "
        + "can only have JSDoc or inline type annotations, not both");
    parse("function /** string */ foo(): string { return 'hello'; }");
  }

  public void testFunctionReturn_typeInJsdocOnly() throws Exception {
    parse("function /** string */ foo() { return 'hello'; }",
        "function/** string */foo() {\n  return'hello';\n}");
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
