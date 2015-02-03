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
 * Tests the conversion of closure-style type declarations in JSDoc
 * to inline type declarations, by running both syntaxes through the parser
 * and verifying the resulting AST is the same.
 */
public class Es6InlineTypesTest extends CompilerTestCase {

  @Override
  public void setUp() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    enableAstValidation(true);
    compareJsDoc = false;
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT6_TYPED);
    return options;
  }

  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    return new ConvertToTypedES6(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testVariableDeclaration() {
    test("/** @type {string} */ var print;", "var print: string;");
  }

  public void testVariableDeclarationWithoutDeclaredType() throws Exception {
    test("var print;", "var print;");
  }

  public void testFunctionReturnType() throws Exception {
    test("/** @return {boolean} */ function b(){}", "function b(): boolean {}");
  }

  public void testFunctionParameterTypes() throws Exception {
    test("/** @param {number} n @param {string} s */ function t(n,s){}",
        "function t(n: number, s: string) {}");
  }

  public void testFunctionInsideAssignment() throws Exception {
    test("/** @param {boolean} b @return {boolean} */ "
            + "var f = function(b){return !b};",
        "var f = function(b: boolean): boolean { return !b; };");
  }

  public void testNestedFunctions() throws Exception {
    test("/**@param {boolean} b*/ "
            + "var f = function(b){var t = function(l) {}; t();};",
            "var f = function(b: boolean) {"
            + "  var t = function(l) {"
            + "  };"
            + "  t();"
            + "};");
  }

  public void testUnknownType() throws Exception {
    test("/** @type {?} */ var n;", "var n: any;");
  }

  // TypeScript doesn't have a representation for the Undefined type,
  // so our transpilation is lossy here.
  public void testUndefinedType() throws Exception {
    test("/** @type {undefined} */ var n;", "var n;");
  }
}
