/*
 * Copyright 2004 The Closure Compiler Authors.
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
 * Ensures that the InlineVariables pass in constants-only mode
 * is functionally equivalent to the old InlineVariablesConstants pass.
 */
public class InlineVariablesConstantsTest extends CompilerTestCase {

  private boolean inlineAllStrings = false;

  public InlineVariablesConstantsTest() {
    enableNormalize();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new InlineVariables(
        compiler, InlineVariables.Mode.CONSTANTS_ONLY, inlineAllStrings);
  }

  @Override
  public void tearDown() {
    inlineAllStrings = false;
  }

  public void testInlineVariablesConstants() {
    test("var ABC=2; var x = ABC;", "var x=2");
    test("var AA = 'aa'; AA;", "'aa'");
    test("var A_A=10; A_A + A_A;", "10+10");
    test("var AA=1", "");
    test("var AA; AA=1", "1");
    test("var AA; if (false) AA=1; AA;", "if (false) 1; 1;");
    testSame("var AA; if (false) AA=1; else AA=2; AA;");

    test("var AA;(function () {AA=1})()",
         "(function () {1})()");

    // Make sure that nothing explodes if there are undeclared variables.
    testSame("var x = AA;");

    // Don't inline if it will make the output larger.
    testSame("var AA = '1234567890'; foo(AA); foo(AA); foo(AA);");

    test("var AA = '123456789012345';AA;",
         "'123456789012345'");
  }

  public void testNoInlineArraysOrRegexps() {
    testSame("var AA = [10,20]; AA[0]");
    testSame("var AA = [10,20]; AA.push(1); AA[0]");
    testSame("var AA = /x/; AA.test('1')");
    testSame("/** @const */ var aa = /x/; aa.test('1')");
  }

  public void testInlineVariablesConstantsJsDocStyle() {
    test("/** @const */var abc=2; var x = abc;", "var x=2");
    test("/** @const */var aa = 'aa'; aa;", "'aa'");
    test("/** @const */var a_a=10; a_a + a_a;", "10+10");
    test("/** @const */var aa=1;", "");
    test("/** @const */var aa; aa=1;", "1");
    test("/** @const */var aa;(function () {aa=1})()", "(function () {1})()");
    test("/** @const */var aa;(function () {aa=1})(); var z=aa",
         "(function () {1})(); var z=1");
    testSame("/** @const */var aa;(function () {var y; aa=y})(); var z=aa");

    // Don't inline if it will make the output larger.
    testSame("/** @const */var aa = '1234567890'; foo(aa); foo(aa); foo(aa);");

    test("/** @const */var aa = '123456789012345';aa;",
         "'123456789012345'");
  }

  public void testInlineConditionallyDefinedConstant1() {
    // Note that inlining conditionally defined constants can change the
    // runtime behavior of code (e.g. when y is true and x is false in the
    // example below). We inline them anyway because if the code author didn't
    // want one inlined, he/she could define it as a non-const variable instead.
    test("if (x) var ABC = 2; if (y) f(ABC);",
         "if (x); if (y) f(2);");
  }

  public void testInlineConditionallyDefinedConstant2() {
    test("if (x); else var ABC = 2; if (y) f(ABC);",
         "if (x); else; if (y) f(2);");
  }

  public void testInlineConditionallyDefinedConstant3() {
    test("if (x) { var ABC = 2; } if (y) { f(ABC); }",
         "if (x) {} if (y) { f(2); }");
  }

  public void testInlineDefinedConstant() {
    test(
        "/**\n" +
        " * @define {string}\n" +
        " */\n" +
        "var aa = '1234567890';\n" +
        "foo(aa); foo(aa); foo(aa);",
        "foo('1234567890');foo('1234567890');foo('1234567890')");

    test(
        "/**\n" +
        " * @define {string}\n" +
        " */\n" +
        "var ABC = '1234567890';\n" +
        "foo(ABC); foo(ABC); foo(ABC);",
        "foo('1234567890');foo('1234567890');foo('1234567890')");
  }

  public void testInlineVariablesConstantsWithInlineAllStringsOn() {
    inlineAllStrings = true;
    test("var AA = '1234567890'; foo(AA); foo(AA); foo(AA);",
         "foo('1234567890'); foo('1234567890'); foo('1234567890')");
  }

  public void testNoInlineWithoutConstDeclaration() {
    testSame("var abc = 2; var x = abc;");
  }

// TODO(nicksantos): enable this again once we allow constant aliasing.
//  public void testInlineConstantAlias() {
//    test("var XXX = new Foo(); var YYY = XXX; bar(YYY)",
//         "var XXX = new Foo(); bar(XXX)");
//  }

  public void testNoInlineAliases() {
    testSame("var XXX = new Foo(); var yyy = XXX; bar(yyy)");
    testSame("var xxx = new Foo(); var YYY = xxx; bar(YYY)");
  }
}
