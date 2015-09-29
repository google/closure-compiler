/*
 * Copyright 2006 The Closure Compiler Authors.
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

/**
 * Tests for variable declaration collapsing.
 *
 */
public final class CollapseVariableDeclarationsTest extends CompilerTestCase {
  @Override
  public void setUp() {
    compareJsDoc = false;
  }

  public void testCollapsing() throws Exception {
    // Basic collapsing
    test("var a;var b;",
         "var a,b;");
    // With initial values
    test("var a = 1;var b = 1;",
         "var a=1,b=1;");
    // Already collapsed
    test("var a, b;",
         "var a,b;");
    // Already collapsed with values
    test("var a = 1, b = 1;",
         "var a=1,b=1;");
    // Some already collapsed
    test("var a;var b, c;var d;",
         "var a,b,c,d;");
    // Some already collapsed with values
    test("var a = 1;var b = 2, c = 3;var d = 4;",
         "var a=1,b=2,c=3,d=4;");
  }

  public void testIssue820() throws Exception {
    // Don't redeclare function parameters, this is incompatible with
    // strict mode.
    testSame("function f(a){ var b=1; a=2; var c; }");
  }

  public void testIfElseVarDeclarations() throws Exception {
    testSame("if (x) var a = 1; else var b = 2;");
  }

  public void testAggressiveRedeclaration() {
    test("var x = 2; foo(x);     x = 3; var y = 2;",
         "var x = 2; foo(x); var x = 3,     y = 2;");

    test("var x = 2; foo(x);     x = 3; x = 1; var y = 2;",
         "var x = 2; foo(x); var x = 3, x = 1,     y = 2;");

    test("var x = 2; foo(x);     x = 3; x = 1; var y = 2; var z = 4",
         "var x = 2; foo(x); var x = 3, x = 1,     y = 2,     z = 4");

    test("var x = 2; foo(x);     x = 3; x = 1; var y = 2; var z = 4; x = 5",
         "var x = 2; foo(x); var x = 3, x = 1,     y = 2,     z = 4, x = 5");
  }

  public void testAggressiveRedeclarationInFor() {
    testSame("for(var x = 1; x = 2; x = 3) {x = 4}");
    testSame("for(var x = 1; y = 2; z = 3) {var a = 4}");
    testSame("var x; for(x = 1; x = 2; z = 3) {x = 4}");
  }

  public void testIssue397() {
    test("var x; var y = 3; x = 5;",
         "var x, y = 3; x = 5;");
    testSame("var x; x = 5; var z = 7;");
    test("var x; var y = 3; x = 5; var z = 7;",
         "var x, y = 3; x = 5; var z = 7;");
    test("var a = 1; var x; var y = 3; x = 5;",
         "var a = 1, x, y = 3; x = 5;");
  }

  public void testArgumentsAssignment() {
    testSame("function f() {arguments = 1;}");
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CollapseVariableDeclarations(compiler);
  }
}
