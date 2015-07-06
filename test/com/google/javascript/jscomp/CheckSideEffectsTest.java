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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

public final class CheckSideEffectsTest extends CompilerTestCase {
  public CheckSideEffectsTest() {
    this.parseTypeInfo = true;
    allowExternsChanges(true);
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckSideEffects(compiler, CheckLevel.WARNING, true);
  }

  private final DiagnosticType e = CheckSideEffects.USELESS_CODE_ERROR;

  public void testUselessCode() {
    testSame("function f(x) { if(x) return; }");
    test("function f(x) { if(x); }", "function f(x) { if(x); }", null, e);

    testSame("if(x) x = y;");
    test("if(x) x == bar();", "if(x) JSCOMPILER_PRESERVE(x == bar());", null, e);

    testSame("x = 3;");
    test("x == 3;", "JSCOMPILER_PRESERVE(x == 3);", null, e);

    testSame("var x = 'test'");
    test("var x = 'test'\n'Breakstr'",
         "var x = 'test'\nJSCOMPILER_PRESERVE('Breakstr')", null, e);

    testSame("");
    testSame("foo();;;;bar();;;;");

    testSame("var a, b; a = 5, b = 6");
    test("var a, b; a = 5, b == 6",
         "var a, b; a = 5, JSCOMPILER_PRESERVE(b == 6)", null, e);
    test("var a, b; a = (5, 6)",
         "var a, b; a = (JSCOMPILER_PRESERVE(5), 6)", null, e);
    test("var a, b; a = (bar(), 6, 7)",
         "var a, b; a = (bar(), JSCOMPILER_PRESERVE(6), 7)", null, e);
    test("var a, b; a = (bar(), bar(), 7, 8)",
         "var a, b; a = (bar(), bar(), JSCOMPILER_PRESERVE(7), 8)", null, e);
    testSame("var a, b; a = (b = 7, 6)");
    testSame("function x(){}\nfunction f(a, b){}\nf(1,(x(), 2));");
    test("function x(){}\nfunction f(a, b){}\nf(1,(2, 3));",
         "function x(){}\nfunction f(a, b){}\n" +
         "f(1,(JSCOMPILER_PRESERVE(2), 3));", null, e);

    test("var x = `TemplateA`\n'TestB'",
        "var x = `TemplateA`\nJSCOMPILER_PRESERVE('TestB')", null, e);
    test("`LoneTemplate`", "JSCOMPILER_PRESERVE(`LoneTemplate`)", null, e);
    test(
        LINE_JOINER.join(
            "var name = 'Bad';",
            "`${name}Template`;"),
        LINE_JOINER.join(
            "var name = 'Bad';",
            "JSCOMPILER_PRESERVE(`${name}Template`)"), null, e);
    test(
        LINE_JOINER.join(
            "var name = 'BadTail';",
            "`Template${name}`;"),
        LINE_JOINER.join(
            "var name = 'BadTail';",
            "JSCOMPILER_PRESERVE(`Template${name}`)"), null, e);
    testSame(
        LINE_JOINER.join(
            "var name = 'Good';",
            "var templateString = `${name}Template`;"));
    testSame("var templateString = `Template`;");
    testSame("tagged`Template`;");
    testSame("tagged`${name}Template`;");
  }

  public void testUselessCodeInFor() {
    testSame("for(var x = 0; x < 100; x++) { foo(x) }");
    testSame("for(; true; ) { bar() }");
    testSame("for(foo(); true; foo()) { bar() }");
    test("for(void 0; true; foo()) { bar() }",
         "for(JSCOMPILER_PRESERVE(void 0); true; foo()) { bar() }", null, e);
    test("for(foo(); true; void 0) { bar() }",
         "for(foo(); true; JSCOMPILER_PRESERVE(void 0)) { bar() }", null, e);
    test("for(foo(); true; (1, bar())) { bar() }",
         "for(foo(); true; (JSCOMPILER_PRESERVE(1), bar())) { bar() }", null, e);

    testSame("for(foo in bar) { foo() }");
    testSame("for (i = 0; el = el.previousSibling; i++) {}");
    testSame("for (i = 0; el = el.previousSibling; i++);");
  }

  public void testTypeAnnotations() {
    test("x;", "JSCOMPILER_PRESERVE(x);", null, e);
    test("a.b.c.d;", "JSCOMPILER_PRESERVE(a.b.c.d);", null, e);
    testSame("/** @type {Number} */ a.b.c.d;");
    testSame("if (true) { /** @type {Number} */ a.b.c.d; }");

    test("function A() { this.foo; }",
         "function A() { JSCOMPILER_PRESERVE(this.foo); }", null, e);
    testSame("function A() { /** @type {Number} */ this.foo; }");
  }

  public void testJSDocComments() {
    testSame("function A() { /** This is a JsDoc comment */ this.foo; }");
    test("function A() { /* This is a normal comment */ this.foo; }",
         "function A() { " +
         " /* This is a normal comment */ JSCOMPILER_PRESERVE(this.foo); }", null, e);
  }

  public void testIssue80() {
    testSame("(0, eval)('alert');");
    test("(0, foo)('alert');", "(JSCOMPILER_PRESERVE(0), foo)('alert');", null, e);
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

    testSame(externs, "alert(noSideEffectsExtern());", null);

    test(externs, "noSideEffectsExtern();",
        "JSCOMPILER_PRESERVE(noSideEffectsExtern());",
        null, e, "Suspicious code. The result of the extern function call " +
        "'noSideEffectsExtern' is not being used.");

    test(externs, "noSideEffectsExtern2();",
            "JSCOMPILER_PRESERVE(noSideEffectsExtern2());",
            null, e, "Suspicious code. The result of the extern function call " +
            "'noSideEffectsExtern2' is not being used.");

    testSame(externs, "hasSideEffectsExtern()", null);

    testSame(externs, "hasSideEffectsExtern2()", null);

    // Methods redefined in inner scopes should not trigger a warning
    testSame(externs, "(function() { function noSideEffectsExtern() {}; " +
             "noSideEffectsExtern(); })()", null);
  }

  public void testExternPropertyFunctions() {
    String externs = "/** @const */ var foo = {};\n" +
        "/** @return {boolean}\n * @nosideeffects */\n" +
        "foo.noSideEffectsExtern = function(){}";

    testSame(externs, "alert(foo.noSideEffectsExtern());", null);

    test(externs, "foo.noSideEffectsExtern();",
        "JSCOMPILER_PRESERVE(foo.noSideEffectsExtern());",
        null, e, "Suspicious code. The result of the extern function call " +
        "'foo.noSideEffectsExtern' is not being used.");

    // Methods redefined in inner scopes should not trigger a warning
    testSame(externs, "(function() { var foo = {}; " +
            "foo.noSideEffectsExtern = function() {}; " +
            "noSideEffectsExtern(); })()", null);
  }
}
