/*
 * Copyright 2008 The Closure Compiler Authors.
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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link MoveFunctionDeclarations}
 *
 */
@RunWith(JUnit4.class)
public final class MoveFunctionDeclarationsTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new MoveFunctionDeclarations(compiler);
  }

  @Test
  public void testFunctionDeclarations() {
    test("a; function f(){} function g(){}", "var f = function(){}; var g = function(){}; a;");
  }

  @Test
  public void testFunctionDeclarationsInModule() {
    test(createModules("a; function f(){} function g(){}"),
         new String[] { "var f = function(){}; var g = function(){}; a" });
  }

  @Test
  public void testGeneratorDeclarations() {
    test(
        "a; function *f(){} function *g(){}", "var f = function* (){}; var g = function* (){}; a;");
  }

  @Test
  public void testFunctionDeclarationsInEs6Module() {
    // NOTE: Currently this pass always runs after module transpilation.
    // No current uses of this pass would benefit from ES6 module support:
    // a) RescopeGlobalSymbols, which relies on this pass, only cares about the global scope.
    // b) ES6 module output cannot be wrapped in a try/catch.
    testSame("a; function f(){} function g(){} export default 5;");
  }

  @Test
  public void testFunctionsExpression() {
    testSame("a; f = function(){}");
  }

  @Test
  public void testNoMoveDeepFunctionDeclarations() {
    setAcceptedLanguage(CompilerOptions.LanguageMode.ECMASCRIPT_2015);
    testSame("a; if (a) function f(){};");
    testSame("a; if (a) { function f(){} }");
  }
}
