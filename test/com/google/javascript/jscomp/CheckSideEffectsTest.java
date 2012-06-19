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

import com.google.javascript.jscomp.CheckLevel;

public class CheckSideEffectsTest extends CompilerTestCase {
  public CheckSideEffectsTest() {
    this.parseTypeInfo = true;
    allowExternsChanges(true);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckSideEffects(compiler, CheckLevel.WARNING, true);
  }

  @Override
  public void test(String js, String expected, DiagnosticType warning) {
    test(js, expected, null, warning);
  }

  public void test(String js, DiagnosticType warning) {
    test(js, js, null, warning);
  }

  final DiagnosticType e = CheckSideEffects.USELESS_CODE_ERROR;
  final DiagnosticType ok = null; // no warning

  public void testUselessCode() {
    test("function f(x) { if(x) return; }", ok);
    test("function f(x) { if(x); }", "function f(x) { if(x); }", e);

    test("if(x) x = y;", ok);
    test("if(x) x == bar();", "if(x) JSCOMPILER_PRESERVE(x == bar());", e);

    test("x = 3;", ok);
    test("x == 3;", "JSCOMPILER_PRESERVE(x == 3);", e);

    test("var x = 'test'", ok);
    test("var x = 'test'\n'str'",
         "var x = 'test'\nJSCOMPILER_PRESERVE('str')", e);

    test("", ok);
    test("foo();;;;bar();;;;", ok);

    test("var a, b; a = 5, b = 6", ok);
    test("var a, b; a = 5, b == 6",
         "var a, b; a = 5, JSCOMPILER_PRESERVE(b == 6)", e);
    test("var a, b; a = (5, 6)",
         "var a, b; a = (JSCOMPILER_PRESERVE(5), 6)", e);
    test("var a, b; a = (bar(), 6, 7)",
         "var a, b; a = (bar(), JSCOMPILER_PRESERVE(6), 7)", e);
    test("var a, b; a = (bar(), bar(), 7, 8)",
         "var a, b; a = (bar(), bar(), JSCOMPILER_PRESERVE(7), 8)", e);
    test("var a, b; a = (b = 7, 6)", ok);
    test("function x(){}\nfunction f(a, b){}\nf(1,(x(), 2));", ok);
    test("function x(){}\nfunction f(a, b){}\nf(1,(2, 3));",
         "function x(){}\nfunction f(a, b){}\n" +
         "f(1,(JSCOMPILER_PRESERVE(2), 3));", e);
  }

  public void testUselessCodeInFor() {
    test("for(var x = 0; x < 100; x++) { foo(x) }", ok);
    test("for(; true; ) { bar() }", ok);
    test("for(foo(); true; foo()) { bar() }", ok);
    test("for(void 0; true; foo()) { bar() }",
         "for(JSCOMPILER_PRESERVE(void 0); true; foo()) { bar() }", e);
    test("for(foo(); true; void 0) { bar() }",
         "for(foo(); true; JSCOMPILER_PRESERVE(void 0)) { bar() }", e);
    test("for(foo(); true; (1, bar())) { bar() }",
         "for(foo(); true; (JSCOMPILER_PRESERVE(1), bar())) { bar() }", e);

    test("for(foo in bar) { foo() }", ok);
    test("for (i = 0; el = el.previousSibling; i++) {}", ok);
    test("for (i = 0; el = el.previousSibling; i++);", ok);
  }

  public void testTypeAnnotations() {
    test("x;", "JSCOMPILER_PRESERVE(x);", e);
    test("a.b.c.d;", "JSCOMPILER_PRESERVE(a.b.c.d);", e);
    test("/** @type Number */ a.b.c.d;", ok);
    test("if (true) { /** @type Number */ a.b.c.d; }", ok);

    test("function A() { this.foo; }",
         "function A() { JSCOMPILER_PRESERVE(this.foo); }", e);
    test("function A() { /** @type Number */ this.foo; }", ok);
  }

  public void testJSDocComments() {
    test("function A() { /** This is a JsDoc comment */ this.foo; }", ok);
    test("function A() { /* This is a normal comment */ this.foo; }",
         "function A() { " +
         " /* This is a normal comment */ JSCOMPILER_PRESERVE(this.foo); }", e);
  }

  public void testIssue80() {
    test("(0, eval)('alert');", ok);
    test("(0, foo)('alert');", "(JSCOMPILER_PRESERVE(0), foo)('alert');", e);
  }

  public void testIsue504() {
    test("void f();", "JSCOMPILER_PRESERVE(void f());", null, e,
        "Suspicious code. The result of the 'void' operator is not being used.");
  }
}
