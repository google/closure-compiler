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
package com.google.javascript.jscomp;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

/**
 * Round-trip test for Typescript-style inline type syntax.  Each expression is parsed and then
 * printed, and we assert that the pretty-printed result is identical to the input.
 *
 * <p>See {@link Es6InlineTypesTest} for tests which start from closure-style JSDoc type declaration
 * syntax.
 */
public class CodePrinterEs6TypedTest extends CodePrinterTestBase {

  @Override
  public void setUp() {
    super.setUp();
    languageMode = LanguageMode.ECMASCRIPT6_TYPED;
  }

  void assertPrettyPrintSame(String js) {
    String parsed = parsePrint(js, newCompilerOptions(new CompilerOptionBuilder() {
      @Override void setOptions(CompilerOptions options) {
        options.setPrettyPrint(true);
        options.setPreferLineBreakAtEndOfFile(false);
        options.setPreferSingleQuotes(true);
      }
    }));
    parsed = parsed.trim(); // strip trailing line break.
    assertEquals(js, parsed);
  }

  public void testVariableDeclaration() {
    assertPrettyPrintSame("var foo: any = 'hello';");
    assertPrettyPrintSame("var foo: number = 'hello';");
    assertPrettyPrintSame("var foo: boolean = 'hello';");
    assertPrettyPrintSame("var foo: string = 'hello';");
    assertPrettyPrintSame("var foo: void = 'hello';");
    assertPrettyPrintSame("var foo: hello = 'hello';");
  }

  public void testFunctionParamDeclaration() {
    assertPrettyPrintSame("function foo(x: string) {\n}");
  }

  public void testFunctionParamDeclaration_defaultValue() {
    assertPrettyPrintSame("function foo(x: string = 'hello') {\n}");
  }

  public void testFunctionParamDeclaration_arrow() {
    assertPrettyPrintSame("(x: string) => 'hello' + x;");
  }

  public void testFunctionReturn() {
    assertPrettyPrintSame("function foo(): string {\n  return 'hello';\n}");
  }

  public void testFunctionReturn_arrow() {
    assertPrettyPrintSame("(): string => 'hello';");
  }

  public void testCompositeType() {
    assertPrettyPrintSame("var foo: mymod.ns.Type;");
  }

  public void testArrayType() {
    assertPrettyPrintSame("var foo: string[];");
  }

  public void testArrayType_qualifiedType() {
    assertPrettyPrintSame("var foo: mymod.ns.Type[];");
  }

  public void testParameterizedType() {
    assertPrettyPrintSame("var x: my.parameterized.Type<ns.A, ns.B>;");
  }
}
