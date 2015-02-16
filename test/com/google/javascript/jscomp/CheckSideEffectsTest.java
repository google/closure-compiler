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

  public void testWarning(String js, String expected, DiagnosticType warning) {
    test(js, expected, null, warning);
  }

  public void testWarning(String js, DiagnosticType warning) {
    test(js, js, null, warning);
  }

  final DiagnosticType e = CheckSideEffects.USELESS_CODE_ERROR;
  final DiagnosticType ok = null; // no warning

  public void testUselessCode() {
    testWarning("function f(x) { if(x) return; }", ok);
    testWarning("function f(x) { if(x); }", "function f(x) { if(x); }", e);

    testWarning("if(x) x = y;", ok);
    testWarning("if(x) x == bar();", "if(x) JSCOMPILER_PRESERVE(x == bar());", e);

    testWarning("x = 3;", ok);
    testWarning("x == 3;", "JSCOMPILER_PRESERVE(x == 3);", e);

    testWarning("var x = 'test'", ok);
    testWarning("var x = 'test'\n'str'",
         "var x = 'test'\nJSCOMPILER_PRESERVE('str')", e);

    testWarning("", ok);
    testWarning("foo();;;;bar();;;;", ok);

    testWarning("var a, b; a = 5, b = 6", ok);
    testWarning("var a, b; a = 5, b == 6",
         "var a, b; a = 5, JSCOMPILER_PRESERVE(b == 6)", e);
    testWarning("var a, b; a = (5, 6)",
         "var a, b; a = (JSCOMPILER_PRESERVE(5), 6)", e);
    testWarning("var a, b; a = (bar(), 6, 7)",
         "var a, b; a = (bar(), JSCOMPILER_PRESERVE(6), 7)", e);
    testWarning("var a, b; a = (bar(), bar(), 7, 8)",
         "var a, b; a = (bar(), bar(), JSCOMPILER_PRESERVE(7), 8)", e);
    testWarning("var a, b; a = (b = 7, 6)", ok);
    testWarning("function x(){}\nfunction f(a, b){}\nf(1,(x(), 2));", ok);
    testWarning("function x(){}\nfunction f(a, b){}\nf(1,(2, 3));",
         "function x(){}\nfunction f(a, b){}\n" +
         "f(1,(JSCOMPILER_PRESERVE(2), 3));", e);
  }

  public void testUselessCodeInFor() {
    testWarning("for(var x = 0; x < 100; x++) { foo(x) }", ok);
    testWarning("for(; true; ) { bar() }", ok);
    testWarning("for(foo(); true; foo()) { bar() }", ok);
    testWarning("for(void 0; true; foo()) { bar() }",
         "for(JSCOMPILER_PRESERVE(void 0); true; foo()) { bar() }", e);
    testWarning("for(foo(); true; void 0) { bar() }",
         "for(foo(); true; JSCOMPILER_PRESERVE(void 0)) { bar() }", e);
    testWarning("for(foo(); true; (1, bar())) { bar() }",
         "for(foo(); true; (JSCOMPILER_PRESERVE(1), bar())) { bar() }", e);

    testWarning("for(foo in bar) { foo() }", ok);
    testWarning("for (i = 0; el = el.previousSibling; i++) {}", ok);
    testWarning("for (i = 0; el = el.previousSibling; i++);", ok);
  }

  public void testTypeAnnotations() {
    testWarning("x;", "JSCOMPILER_PRESERVE(x);", e);
    testWarning("a.b.c.d;", "JSCOMPILER_PRESERVE(a.b.c.d);", e);
    testWarning("/** @type Number */ a.b.c.d;", ok);
    testWarning("if (true) { /** @type Number */ a.b.c.d; }", ok);

    testWarning("function A() { this.foo; }",
         "function A() { JSCOMPILER_PRESERVE(this.foo); }", e);
    testWarning("function A() { /** @type Number */ this.foo; }", ok);
  }

  public void testJSDocComments() {
    testWarning("function A() { /** This is a JsDoc comment */ this.foo; }", ok);
    testWarning("function A() { /* This is a normal comment */ this.foo; }",
         "function A() { " +
         " /* This is a normal comment */ JSCOMPILER_PRESERVE(this.foo); }", e);
  }

  public void testIssue80() {
    testWarning("(0, eval)('alert');", ok);
    testWarning("(0, foo)('alert');", "(JSCOMPILER_PRESERVE(0), foo)('alert');", e);
  }

  public void testIsue504() {
    test("void f();", "JSCOMPILER_PRESERVE(void f());", null, e,
        "Suspicious code. The result of the 'void' operator is not being used.");
  }

  public void testExternFunctions() {
    String externs = "/** @return {boolean}\n * @nosideeffects */\n" +
        "function noSideEffectsExtern(){}\n" +
        "/** @return {boolean}\n * @nosideeffects */\n" +
        "var noSideEffectsExtern2 = function(){};\n" +
        "/** @return {boolean} */ function hasSideEffectsExtern(){}\n" +
        "/** @return {boolean} */ var hasSideEffectsExtern2 = function(){}\n";

    testSame(externs, "alert(noSideEffectsExtern());", ok);

    test(externs, "noSideEffectsExtern();",
        "JSCOMPILER_PRESERVE(noSideEffectsExtern());",
        null, e, "Suspicious code. The result of the extern function call " +
        "'noSideEffectsExtern' is not being used.");

    test(externs, "noSideEffectsExtern2();",
            "JSCOMPILER_PRESERVE(noSideEffectsExtern2());",
            null, e, "Suspicious code. The result of the extern function call " +
            "'noSideEffectsExtern2' is not being used.");

    testSame(externs, "hasSideEffectsExtern()", ok);

    testSame(externs, "hasSideEffectsExtern2()", ok);

    // Methods redefined in inner scopes should not trigger a warning
    testSame(externs, "(function() { function noSideEffectsExtern() {}; " +
             "noSideEffectsExtern(); })()", ok);
  }

  public void testExternPropertyFunctions() {
    String externs = "/** @const */ var foo = {};\n" +
        "/** @return {boolean}\n * @nosideeffects */\n" +
        "foo.noSideEffectsExtern = function(){}";

    test(externs, "alert(foo.noSideEffectsExtern());",
            "alert(foo.noSideEffectsExtern());", ok, null);

    test(externs, "foo.noSideEffectsExtern();",
        "JSCOMPILER_PRESERVE(foo.noSideEffectsExtern());",
        null, e, "Suspicious code. The result of the extern function call " +
        "'foo.noSideEffectsExtern' is not being used.");

    // Methods redefined in inner scopes should not trigger a warning
    testSame(externs, "(function() { var foo = {}; " +
            "foo.noSideEffectsExtern = function() {}; " +
            "noSideEffectsExtern(); })()", ok);
  }
}
