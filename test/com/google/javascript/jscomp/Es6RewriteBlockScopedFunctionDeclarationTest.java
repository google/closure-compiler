/*
 * Copyright 2014 The Closure Compiler Authors.
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

/** Test case for {@link Es6RewriteBlockScopedFunctionDeclaration}. */
public final class Es6RewriteBlockScopedFunctionDeclarationTest extends TypeICompilerTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    enableRunTypeCheckAfterProcessing();
    this.mode = TypeInferenceMode.NEITHER;
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new Es6RewriteBlockScopedFunctionDeclaration(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testRewritesBlockScopedFunctionDeclaration() {
    test("{ function f(){} }", "{ let f = function(){}; }");
  }

  public void testHoistsFunctionToStartOfBlock() {
    test("{ console.log(f()); function f(){} }", "{ let f = function(){}; console.log(f()); }");
  }

  public void testBlockScopedGeneratorFunction() {
    test("{ function* f() {yield 1;} }", "{ let f = function*() { yield 1; }; }");
  }

  public void testBlockNestedInsideFunction() {
    test(
        lines(
            "function f() {",
            "  var x = 1;",
            "  if (a) {",
            "    x();",
            "    function x() { return x; }",
            "  }",
            "  return x;",
            "}"),
        lines(
            "function f() {",
            "  var x = 1;",
            "  if (a) {",
            "    let x = function() { return x; };",
            "    x();",
            "  }",
            "  return x;",
            "}"));
  }

  public void testFunctionInLoop() {
    test(
        lines(
            "for (var x of y) {",
            "  y();",
            "  function f() {",
            "    let z;",
            "  }",
            "}"),
        lines(
            "for (var x of y) {",
            "  let f = function() {",
            "    let z;",
            "  };",
            "  y();",
            "}"));
  }

  public void testDoesNotRewriteTopLevelDeclarations() {
    testSame("function f(){}");
  }

  public void testDoesNotRewriteFunctionScopedDeclarations() {
    testSame("function g() {function f(){}}");
  }
}
