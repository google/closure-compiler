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

import com.google.javascript.jscomp.testing.JSChunkGraphBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link RewriteGlobalDeclarationsForTryCatchWrapping}
 */
@RunWith(JUnit4.class)
public final class RewriteGlobalDeclarationsForTryCatchWrappingTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new RewriteGlobalDeclarationsForTryCatchWrapping(compiler);
  }

  @Test
  public void testFunctionDeclarations() {
    test("a; function f(){} function g(){}", "var f = function(){}; var g = function(){}; a;");
  }

  @Test
  public void testFunctionDeclarationsInBlock() {
    testSame("a; if (1) { function f(){} function g(){} }");
  }

  @Test
  public void testClassDeclarations() {
    // classes don't hoist
    test("a; class B{} class C{}", "a; var B = class{}; var C = class{};");
  }

  @Test
  public void testClassDeclarationsInBlock() {
    testSame("a; if (1) { class B{} class C{} }");
  }

  @Test
  public void testFunctionDeclarationsInModule() {
    test(
        JSChunkGraphBuilder.forUnordered().addChunk("a; function f(){} function g(){}").build(),
        new String[] {"var f = function(){}; var g = function(){}; a"});
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
    testSame("a; if (a) function f(){};");
    testSame("a; if (a) { function f(){} }");
  }

  @Test
  public void testMigrateLetConstToVar1() {
    test("let a = 1;", "var a = 1");
    test("const a = 2;", "var a = 2");
  }

  @Test
  public void testMigrateLetConstToVar2() {
    testSame("{let a = 1;}");
    testSame("{const a = 2;}");
  }
}
