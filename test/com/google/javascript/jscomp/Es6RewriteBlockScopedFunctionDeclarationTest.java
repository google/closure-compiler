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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test case for {@link Es6RewriteBlockScopedFunctionDeclaration}. */
@RunWith(JUnit4.class)
public final class Es6RewriteBlockScopedFunctionDeclarationTest extends CompilerTestCase {

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    enableTypeCheck();
    enableTypeInfoValidation();
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

  @Test
  public void testRewritesBlockScopedFunctionDeclaration() {
    test("{ function f(){} }", "{ let f = function(){}; }");
  }

  @Test
  public void testHoistsFunctionToStartOfBlock() {
    test(
        lines(
            "", // preserve newlines
            "function use(x) {}",
            "{ use(f()); function f(){} }",
            ""),
        lines(
            "", // preserve newlines
            "function use(x) {}",
            "{ let f = function(){}; use(f()); }",
            ""));
  }

  @Test
  public void testBlockScopedGeneratorFunction() {
    test("{ function* f() {yield 1;} }", "{ let f = function*() { yield 1; }; }");
  }

  @Test
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

  @Test
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

  @Test
  public void testDoesNotRewriteTopLevelDeclarations() {
    testSame("function f(){}");
  }

  @Test
  public void testDoesNotRewriteFunctionScopedDeclarations() {
    testSame("function g() {function f(){}}");
  }
}
