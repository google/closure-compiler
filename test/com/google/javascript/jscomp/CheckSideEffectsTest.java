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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CheckSideEffectsTest extends CompilerTestCase {

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableParseTypeInfo();
    allowExternsChanges();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckSideEffects(compiler, true, true);
  }

  private final DiagnosticType e = CheckSideEffects.USELESS_CODE_ERROR;

  @Test
  public void testUselessCode() {
    testSame("function f(x) { if(x) return; }");
    testWarning("function f(x) { if(x); }", e);
    testSame("var f = x=>x");
    testWarning("var f = (x)=>{ if(x); }", e);

    testSame("if(x) x = y;");
    test("if(x) x == bar();", "if(x) JSCOMPILER_PRESERVE(x == bar());", warning(e));

    testSame("x = 3;");
    test("x == 3;", "JSCOMPILER_PRESERVE(x == 3);", warning(e));

    testSame("var x = 'test'");
    test(
        "var x = 'test'\n'Breakstr'",
        "var x = 'test'\nJSCOMPILER_PRESERVE('Breakstr')",
        warning(e));

    testSame("");
    testSame("foo();;;;bar();;;;");

    testSame("var a, b; a = 5, b = 6");
    test("var a, b; a = 5, b == 6", "var a, b; a = 5, JSCOMPILER_PRESERVE(b == 6)", warning(e));
    test("var a, b; a = (5, 6)", "var a, b; a = (JSCOMPILER_PRESERVE(5), 6)", warning(e));
    test("var a, b; a ||= (5, 6)", "var a, b; a ||= (JSCOMPILER_PRESERVE(5), 6)", warning(e));
    test(
        "var a, b; a = ('marker', 6)",
        "var a, b; a = (JSCOMPILER_PRESERVE('marker'), 6)",
        warning(e));
    test(
        "var a, b; a ||= ('marker', 6)",
        "var a, b; a ||= (JSCOMPILER_PRESERVE('marker'), 6)",
        warning(e));
    test(
        "var a, b; a = (bar(), 6, 7)",
        "var a, b; a = (bar(), JSCOMPILER_PRESERVE(6), 7)",
        warning(e));
    test(
        "var a, b; a = (bar(), bar(), 7, 8)",
        "var a, b; a = (bar(), bar(), JSCOMPILER_PRESERVE(7), 8)",
        warning(e));
    test(
        "var a, b; a ||= (bar(), bar(), 7, 8)",
        "var a, b; a ||= (bar(), bar(), JSCOMPILER_PRESERVE(7), 8)",
        warning(e));
    testSame("var a, b; a = (b = 7, 6)");
    testSame("var a, b; a ||= (b ||= 7, 6)");
    testSame(lines("function x(){}", "function f(a, b){}", "f(1,(x(), 2));"));
    test(
        lines("function x(){}", "function f(a, b){}", "f(1,(2, 3));"),
        lines("function x(){}", "function f(a, b){}", "f(1,(JSCOMPILER_PRESERVE(2), 3));"),
        warning(e));
  }

  @Test
  public void testUselessCodeTemplateStirngs() {

    test(
        "var x = `TemplateA`\n'TestB'",
        "var x = `TemplateA`\nJSCOMPILER_PRESERVE('TestB')",
        warning(e));
    test("`LoneTemplate`", "JSCOMPILER_PRESERVE(`LoneTemplate`)", warning(e));
    test(
        lines("var name = 'Bad';", "`${name}Template`;"),
        lines("var name = 'Bad';", "JSCOMPILER_PRESERVE(`${name}Template`)"),
        warning(e));
    test(
        lines("var name = 'BadTail';", "`Template${name}`;"),
        lines("var name = 'BadTail';", "JSCOMPILER_PRESERVE(`Template${name}`)"),
        warning(e));
    testSame(lines("var name = 'Good';", "var templateString = `${name}Template`;"));
    testSame("var templateString = `Template`;");
    testSame("tagged`Template`;");
    testSame("tagged`${name}Template`;");
  }

  @Test
  public void testUselessCodeDestructuring() {
    testSame(
        lines(
            "var obj = {",
            "  itm1: 1,",
            "  itm2: 2",
            "}",
            "var { itm1: de_item1, itm2: de_item2 } = obj;"));
    testSame(lines("var obj = {", "  itm1: 1,", "  itm2: 2", "}", "var { itm1, itm2 } = obj;"));
    testSame(lines("var arr = ['item1', 'item2', 'item3'];", "var [ itm1 = 1, itm2 = 2 ] = arr;"));
    testSame(
        lines("var arr = ['item1', 'item2', 'item3'];", "var [ itm1 = 1, itm2 = 2 ] = badArr;"));
    testSame(
        lines(
            "var arr = ['item1', 'item2', 'item3'];",
            "function f(){}",
            "var [ itm1 = f(), itm2 = 2 ] = badArr;"));
  }

  @Test
  public void testUselessCodeParameters() {
    testSame("function c(a, b = 1) {}; c(1);");
    testSame("function c(a, b = f()) {}; c(1);");
    testSame("function c(a, {b, c}) {}; c(1);");
    testSame("function c(a, {b, c}) {}; c(1, {b: 2, c: 3});");
  }

  @Test
  public void testUselessCodeArrowFunctions() {
    testWarning("var f = s => {key:s}", e);
    testWarning("var f = s => {key:s + 1}", e);
    testWarning("var f = s => {s}", e);
    testSame("var f = s => {s ||= 1}");
  }

  // just here to make sure import.meta doesn't break anything
  @Test
  public void testImportMeta() {
    testSame("var x = import.meta");
  }

  @Test
  public void testUselessCodeInFor() {
    testSame("for(var x = 0; x < 100; x++) { foo(x) }");
    testSame("for(; true; ) { bar() }");
    testSame("for(foo(); true; foo()) { bar() }");
    test(
        "for(i < 5; true; foo()) { bar() }",
        "for(JSCOMPILER_PRESERVE(i < 5); true; foo()) { bar() }",
        warning(e));
    test(
        "for(var i = foo(); i++; i < 5) { bar() }",
        "for(var i = foo(); i++; JSCOMPILER_PRESERVE(i < 5)) { bar() }",
        warning(e));
    test(
        "for(foo(); true; (1, bar())) { bar() }",
        "for(foo(); true; (JSCOMPILER_PRESERVE(1), bar())) { bar() }",
        warning(e));

    testSame("for(foo in bar) { foo() }");
    testSame("for (i = 0; el = el.previousSibling; i++) {}");
    testSame("for (i = 0; el = el.previousSibling; i++);");
  }

  @Test
  public void testUslessCodeInClassStaticBlock() {
    testSame(
        lines(
            "class C {", //
            "  static {",
            "    function f(x) { if(x) return; }",
            "  }",
            "}"));
    testWarning(
        lines(
            "class C {", //
            "  static {",
            "    function f(x) { if(x); }",
            "  }",
            "}"),
        e);
    testSame(
        lines(
            "class C {", //
            "  static {",
            "    var f = x=>x;",
            "  }",
            "}"));
    testWarning(
        lines(
            "class C {", //
            "  static {",
            "    var f = (x)=>{ if(x); }",
            "  }",
            "}"),
        e);
    testSame(
        lines(
            "class C {", //
            "  static {",
            "    x = 3;",
            "  }",
            "}"));
    test(
        lines(
            "class C {", //
            "  static {",
            "    x == 3;",
            "  }",
            "}"),
        lines(
            "class C {", //
            "  static {",
            "    JSCOMPILER_PRESERVE(x == 3);",
            "  }",
            "}"),
        warning(e));
    testSame(
        lines(
            "class C {", //
            "  static {",
            "     foo();;;;bar();;;;",
            "  }",
            "}"));
  }

  @Test
  public void testTypeAnnotations() {
    test("x;", "JSCOMPILER_PRESERVE(x);", warning(e));
    test("a.b.c.d;", "JSCOMPILER_PRESERVE(a.b.c.d);", warning(e));
    testSame("/** @type {Number} */ a.b.c.d;");
    testSame("if (true) { /** @type {Number} */ a.b.c.d; }");

    test(
        "function A() { this.foo; }",
        "function A() { JSCOMPILER_PRESERVE(this.foo); }",
        warning(e));
    testSame("function A() { /** @type {Number} */ this.foo; }");
  }

  @Test
  public void testJSDocComments() {
    testSame("function A() { /** This is a JsDoc comment */ this.foo; }");
    test(
        "function A() { /* This is a normal comment */ this.foo; }",
        "function A() { /* This is a normal comment */ JSCOMPILER_PRESERVE(this.foo); }",
        warning(e));
  }

  @Test
  public void testIndirectCallExpressions() {
    // indirect call to remove context from eval.
    testSame("(0, eval)('alert');");
    // indirect call to remove this context from module nested functions.
    testSame("(0, modPrefix.foo)('alert');");
    testSame("(0, modPrefix['foo'])('alert');");

    // tagged template literals are a form of call
    testSame("(0, modPrefix.foo)`alert ${x}`;");
    testSame("(0, modPrefix['foo'])`alert ${x}`;");

    // comma not in a call expression.
    test("x = (0, foo) + 1;", "x = (JSCOMPILER_PRESERVE(0), foo) + 1;", warning(e));
    // Plain function, no indirection needed to remove this
    testSame("(0, otherFn)(1);");
    // Other expression.
    test("(0, otherFn())(1);", "otherFn()(1);");
  }

  @Test
  public void testIssue504() {
    // See https://blickly.github.io/closure-compiler-issues/#504, though the `void` idiom in the
    // original issue is still relevant externally (see ESLint's ignoreVoid option for the
    // no-floating-promises rule), so it's no longer an error.
    test(
        "+f();",
        "JSCOMPILER_PRESERVE(+f());",
        warning(e)
            .withMessage("Suspicious code. The result of the 'pos' operator is not being used."));
  }

  @Test
  public void testExternFunctions() {
    String externs =
        lines(
            "/** @return {boolean}",
            "  * @nosideeffects */",
            "function noSideEffectsExtern(){}",
            "/** @return {boolean}",
            "  * @nosideeffects */",
            "var noSideEffectsExtern2 = function(){};",
            "/** @return {boolean} */ function hasSideEffectsExtern(){}",
            "/** @return {boolean} */ var hasSideEffectsExtern2 = function(){}");

    testSame(externs(externs), srcs("alert(noSideEffectsExtern());"));

    test(
        externs(externs),
        srcs("noSideEffectsExtern();"),
        expected("JSCOMPILER_PRESERVE(noSideEffectsExtern());"),
        warning(e)
            .withMessage(
                "Suspicious code. The result of the extern function call "
                    + "'noSideEffectsExtern' is not being used."));

    test(
        externs(externs),
        srcs("noSideEffectsExtern2();"),
        expected("JSCOMPILER_PRESERVE(noSideEffectsExtern2());"),
        warning(e)
            .withMessage(
                "Suspicious code. The result of the extern function call "
                    + "'noSideEffectsExtern2' is not being used."));

    testSame(externs(externs), srcs("hasSideEffectsExtern()"));

    testSame(externs(externs), srcs("hasSideEffectsExtern2()"));

    // Methods redefined in inner scopes should not trigger a warning
    testSame(
        externs(externs),
        srcs("(function() { function noSideEffectsExtern() {}; noSideEffectsExtern(); })()"));
  }

  @Test
  public void testExternPropertyFunctions() {
    String externs =
        lines(
            "/** @const */ var foo = {};",
            "/** @return {boolean}",
            "  * @nosideeffects */",
            "foo.noSideEffectsExtern = function(){}");

    testSame(externs(externs), srcs("alert(foo.noSideEffectsExtern());"));

    test(
        externs(externs),
        srcs("foo.noSideEffectsExtern();"),
        expected("JSCOMPILER_PRESERVE(foo.noSideEffectsExtern());"),
        warning(e)
            .withMessage(
                "Suspicious code. The result of the extern function call "
                    + "'foo.noSideEffectsExtern' is not being used."));

    // Methods redefined in inner scopes should not trigger a warning
    testSame(
        externs(externs),
        srcs(
            lines(
                "(function() {",
                "  var foo = {};",
                "  foo.noSideEffectsExtern = function() {};",
                "  noSideEffectsExtern();",
                " })()")));
  }

  @Test
  public void testIgnoresRuntimeLibRequireStatementsOnly() {
    testSame(srcs(SourceFile.fromCode(AbstractCompiler.RUNTIME_LIB_DIR, "'require base'")));

    testWarning(
        srcs(SourceFile.fromCode(AbstractCompiler.RUNTIME_LIB_DIR, "'other string lit'")),
        warning(CheckSideEffects.USELESS_CODE_ERROR));

    testWarning(
        srcs(
            SourceFile.fromCode(
                AbstractCompiler.RUNTIME_LIB_DIR, "function f() { 'require base' }")),
        warning(CheckSideEffects.USELESS_CODE_ERROR));

    testWarning(
        srcs(SourceFile.fromCode("other/file.js", "'require base'")),
        warning(CheckSideEffects.USELESS_CODE_ERROR));
  }
}
