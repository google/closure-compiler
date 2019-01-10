/*
 * Copyright 2009 The Closure Compiler Authors.
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
import com.google.javascript.jscomp.Normalize.NormalizeStatements;
import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** @author johnlenz@google.com (John Lenz) */
@RunWith(JUnit4.class)
public final class DenormalizeTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new NormalizeAndDenormalizePass(compiler);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
  }

  @Override
  protected int getNumRepetitions() {
    // The normalize pass is only run once.
    return 1;
  }

  @Test
  public void testInlineVarKeyword1() {
    test(
        lines(
            "function f() {",
            "  var x;",
            "  function g() { x = 2; }",
            "  if (y) { x = -1; }",
            "  alert(x);",
            "}"),
        lines(
            "function f() {",
            "  function g() { x = 2; }",
            "  if (y) { var x = -1; }",
            "  alert(x);",
            "}"));
  }

  @Test
  public void testInlineVarKeyword2() {
    test(
        lines(
            "function f() {",
            "  var x;",
            "  function g() { x = 2; }",
            "  if (y) { x = -1; } else { x = 3; }",
            "  alert(x);",
            "}"),
        lines(
            "function f() {",
            "  function g() { x = 2; }",
            "  if (y) { var x = -1; } else { x = 3; }",
            "  alert(x);",
            "}"));
  }

  @Test
  public void testInlineVarKeywordArrowFunc1() {
    test(
        lines(
            "var f = () => {",
            "  var x;",
            "  var g = () => { x = 2; }",
            "  if (y) { x = -1; }",
            "  alert(x);",
            "}"),
        lines(
            "var f = () => {",
            "  var g = () => { x = 2; }",
            "  if (y) { var x = -1; }",
            "  alert(x);",
            "}"));
  }

  @Test
  public void testInlineVarKeywordArrowFunc2() {
    test(
        lines(
            "var f = () => {",
            "  var x;",
            "  var g = () => { x = 2; }",
            "  if (y) { x = -1; } else { x = 3; }",
            "  alert(x);",
            "}"),
        lines(
            "var f = () => {",
            "  var g = () => { x = 2; }",
            "  if (y) { var x = -1; } else { x = 3; }",
            "  alert(x);",
            "}"));
  }

  @Test
  public void testNotInlineConstLet() {
    testSame(
        lines(
            "let x;",
            "if (y) { x = -1; }"));

    testSame(
        lines(
            "const x = 1;",
            "if (y) { x = -1; }"));
  }

  @Test
  public void testFor() {
    // Verify assignments are moved into the FOR init node.
    test("a = 0; for(; a < 2 ; a++) foo()",
         "for(a = 0; a < 2 ; a++) foo();");
    // Verify vars are are moved into the FOR init node.
    test("var a = 0; for(; c < b ; c++) foo()",
         "for(var a = 0; c < b ; c++) foo()");

    // We don't handle labels yet.
    testSame("var a = 0; a:for(; c < b ; c++) foo()");
    testSame("var a = 0; a:b:for(; c < b ; c++) foo()");

    // Do not inline let or const
    testSame("let a = 0; for(; c < b ; c++) foo()");
    testSame("const a = 0; for(; c < b ; c++) foo()");

    // Verify FOR inside IFs.
    test("if(x){var a = 0; for(; c < b; c++) foo()}",
         "if(x){for(var a = 0; c < b; c++) foo()}");

    // Any other expression.
    test("init(); for(; a < 2 ; a++) foo()",
         "for(init(); a < 2 ; a++) foo();");

    // Other statements are left as is.
    test("function f(){ var a; for(; a < 2 ; a++) foo() }",
         "function f(){ for(var a; a < 2 ; a++) foo() }");
    testSame("function f(){ return; for(; a < 2 ; a++) foo() }");

    // Verify destructuring assignments are moved.
    test("[a, b] = [1, 2]; for (; a < 2; a = b++) foo();",
        "for ([a, b] = [1, 2]; a < 2; a = b++) foo();");

    test("var [a, b] = [1, 2]; for (; a < 2; a = b++) foo();",
        "for (var [a, b] = [1, 2]; a < 2; a = b++) foo();");
  }

  @Test
  public void testForIn() {
    test("var a; for(a in b) foo()", "for (var a in b) foo()");
    testSame("a = 0; for(a in b) foo()");
    testSame("var a = 0; for(a in b) foo()");

    // We don't handle labels yet.
    testSame("var a; a:for(a in b) foo()");
    testSame("var a; a:b:for(a in b) foo()");

    // Verify FOR inside IFs.
    test("if(x){var a; for(a in b) foo()}",
         "if(x){for(var a in b) foo()}");

    // Any other expression.
    testSame("init(); for(a in b) foo()");

    // Other statements are left as is.
    testSame("function f(){ return; for(a in b) foo() }");

    // We don't handle destructuring patterns yet.
    testSame("var a; var b; for ([a, b] in c) foo();");
  }

  @Test
  public void testForOf() {
    test("var a; for (a of b) foo()", "for (var a of b) foo()");
    testSame("a = 0; for (a of b) foo()");
    testSame("var a = 0; for (a of b) foo()");

    // We don't handle labels yet.
    testSame("var a; a: for (a of b) foo()");
    testSame("var a; a: b: for (a of b) foo()");

    // Verify FOR inside IFs.
    test("if (x) { var a; for (a of b) foo() }",
         "if (x) { for (var a of b) foo() }");

    // Any other expression.
    testSame("init(); for (a of b) foo()");

    // Other statements are left as is.
    testSame("function f() { return; for (a of b) foo() }");

    // We don't handle destructuring patterns yet.
    testSame("var a; var b; for ([a, b] of c) foo();");
  }

  @Test
  public void testInOperatorNotInsideFor() {
    // in operators shouldn't be moved into for loops.
    // Some JavaScript interpreters (such as the NetFront Access browser
    // embedded in the PlayStation 3) will not parse an in operator in
    // a for loop, even if it's protected by parentheses.

    // Make sure the in operator doesn't get moved into the for loop.
    testSame("function f(){ var a; var i=\"length\" in a;" +
        "for(; a < 2 ; a++) foo() }");
    // Same, but with parens around the operator.
    testSame("function f(){ var a; var i=(\"length\" in a);" +
        "for(; a < 2 ; a++) foo() }");
    // Make sure Normalize yanks the variable initializer out, and
    // Denormalize doesn't put it back.
    test("function f(){" +
         "var b,a=0; for (var i=(\"length\" in b);a<2; a++) foo()}",
         "function f(){var b; var a=0;var i=(\"length\" in b);" +
         "for (;a<2;a++) foo()}");
  }

  @Test
  public void testAssignShorthand() {
    test("x = x | 1;", "x |= 1;");
    test("x = x ^ 1;", "x ^= 1;");
    test("x = x & 1;", "x &= 1;");
    test("x = x << 1;", "x <<= 1;");
    test("x = x >> 1;", "x >>= 1;");
    test("x = x >>> 1;", "x >>>= 1;");
    test("x = x + 1;", "x += 1;");
    test("x = x - 1;", "x -= 1;");
    test("x = x * 1;", "x *= 1;");
    test("x = x / 1;", "x /= 1;");
    test("x = x % 1;", "x %= 1;");

    test("/** @suppress {const} */ x = x + 1;", "/** @suppress {const} */ x += 1;");
  }

  @Test
  public void testNoCrashOnEs6Features() {
    test(
        lines(
            "class C {",
            "  constructor() {",
            "    var x;",
            "    if (y) { x = -1; }",
            "  }",
            "}"),
        lines(
            "class C {",
            "  constructor() {",
            "    if (y) { var x = -1; }",
            "  }",
            "}"));

    test(
        lines(
            "var obj = {",
            "  method() {",
            "    var c; for (; c < b ; c++) foo()",
            "  },",
            "}"),
        lines(
            "var obj = {",
            "  method() {",
            "    for (var c; c < b ; c++) foo()",
            "  },",
            "}"));

    testSame(
        lines(
            "var obj = {",
            "  ['computed' + 'prop']: 42",
            "}"));

    // Denormalize does not revert shorthand object literals that were expanded in Normalize
    test(
        lines(
            "var obj = {",
            "  key",
            "}"),
        lines(
            "var obj = {",
            "  key: key",
            "}"));

    test(
        lines(
            "function tag(strings) {",
            "  var x;",
            "  if (y) { x = x + 1; }",
            "}",
            "tag`template`"),
        lines(
            "function tag(strings) {",
            "  var x;",
            "  if (y) { x += 1; }",
            "}",
            "tag`template`"));

    testSame(
        lines(
            "var x;",
            "var y;",
            "if (y) { [x, y] = [1, 2]; }"));
  }

  /**
   * Create a class to combine the Normalize and Denormalize passes.
   * This is needed because the enableNormalize() call on CompilerTestCase
   * causes normalization of the result *and* the expected string, and
   * we really don't want the compiler twisting the expected code around.
   */
  public static final class NormalizeAndDenormalizePass implements CompilerPass {
    Denormalize denormalizePass;
    NormalizeStatements normalizePass;
    AbstractCompiler compiler;

    public NormalizeAndDenormalizePass(AbstractCompiler compiler) {
      this.compiler = compiler;
      denormalizePass = new Denormalize(compiler);
      normalizePass = new NormalizeStatements(compiler, false);
    }

    @Override
    public void process(Node externs, Node root) {
      NodeTraversal.traverse(compiler, root, normalizePass);
      denormalizePass.process(externs, root);
    }
  }

}
