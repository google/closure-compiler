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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for variable declaration collapsing.
 *
 */
@RunWith(JUnit4.class)
public final class CollapseVariableDeclarationsTest extends CompilerTestCase {

  @Test
  public void testCollapsing() {
    // Basic collapsing
    test("var a;var b;",
         "var a,b;");

    // With initial values
    test("var a = 1;var b = 1;",
         "var a=1,b=1;");

    // Already collapsed
    testSame("var a, b;");

    // Already collapsed with values
    testSame("var a = 1, b = 1;");

    // Some already collapsed
    test("var a;var b, c;var d;",
         "var a,b,c,d;");

    // Some already collapsed with values
    test("var a = 1;var b = 2, c = 3;var d = 4;",
         "var a=1,b=2,c=3,d=4;");

    test(
        "var x = 2; foo(x); x = 3; x = 1; var y = 2; var z = 4; x = 5",
        "var x = 2; foo(x); x = 3; x = 1; var y = 2, z = 4; x = 5");
  }

  @Test
  public void testIssue820() {
    // Don't redeclare function parameters, this is incompatible with
    // strict mode.
    testSame("function f(a){ var b=1; a=2; var c; }");
  }

  @Test
  public void testIfElseVarDeclarations() {
    testSame("if (x) var a = 1; else var b = 2;");
  }

  @Test
  public void testAggressiveRedeclarationInFor() {
    testSame("for(var x = 1; x = 2; x = 3) {x = 4}");
    testSame("for(var x = 1; y = 2; z = 3) {var a = 4}");
    testSame("var x; for(x = 1; x = 2; z = 3) {x = 4}");
  }

  @Test
  public void testIssue397() {
    test("var x; var y = 3; x = 5;",
         "var x, y = 3; x = 5;");

    testSame("var x; x = 5; var z = 7;");

    test("var x; var y = 3; x = 5; var z = 7;",
         "var x, y = 3; x = 5; var z = 7;");

    test("var a = 1; var x; var y = 3; x = 5;",
         "var a = 1, x, y = 3; x = 5;");
  }

  @Test
  public void testArgumentsAssignment() {
    testSame("function f() {arguments = 1;}");
  }

  // ES6 Tests
  @Test
  public void testCollapsingLetConst() {
    // Basic collapsing
    test("let a;let b;",
         "let a,b;");

    // With initial values
    test("const a = 1;const b = 1;",
         "const a=1,b=1;");

    // Already collapsed
    testSame("let a, b;");

    // Already collapsed with values
    testSame("let a = 1, b = 1;");

    // Some already collapsed
    test("let a;let b, c;let d;",
         "let a,b,c,d;");

    // Some already collapsed with values
    test("let a = 1;let b = 2, c = 3;let d = 4;",
         "let a=1,b=2,c=3,d=4;");

    // Different variable types
    testSame("let a = 1; const b = 2;");
  }

  @Test
  public void testIfElseVarDeclarationsLet() {
    testSame("if (x) { let a = 1; } else { let b = 2; }");
  }

  @Test
  public void testAggressiveRedeclarationOfLetInFor() {
    testSame("for(let x = 1; x = 2; x = 3) {x = 4}");
    testSame("for(let x = 1; y = 2; z = 3) {let a = 4}");
    testSame("let x; for(x = 1; x = 2; z = 3) {x = 4}");
  }

  @Test
  public void testRedeclarationLetInFunction() {
    test(
        "function f() { let x = 1; let y = 2; let z = 3; x + y + z; }",
        "function f() { let x = 1, y = 2, z = 3; x + y + z; } ");

    // recognize local scope version of x
    test(
        "var x = 1; function f() { let x = 1; let y = 2; x + y; }",
        "var x = 1; function f() { let x = 1, y = 2; x + y } ");

    // do not redeclare function parameters
    // incompatible with strict mode
    testSame("function f(x) { let y = 3; x = 4; x + y; }");
  }

  @Test
  public void testArrowFunction() {
    test("() => {let x = 1; let y = 2; x + y; }",
         "() => {let x = 1, y = 2; x + y; }");

    // do not redeclare function parameters
    // incompatible with strict mode
    testSame("(x) => {x = 4; let y = 2; x + y; }");
  }

  @Test
  public void testUncollapsableDeclarations() {
    testSame("let x = 1; var y = 2; const z = 3");

    testSame("let x = 1; var y = 2; let z = 3;");
  }

  @Test
  public void testMixedDeclarationTypes() {
    //lets, vars, const declarations consecutive

    test("let x = 1; let z = 3; var y = 2;",
         "let x = 1, z = 3; var y = 2;");

    test("let x = 1; let y = 2; var z = 3; var a = 4;",
         "let x = 1, y = 2; var z = 3, a = 4");
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CollapseVariableDeclarations(compiler);
  }
}
