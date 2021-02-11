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

import static com.google.common.truth.Truth.assertThat;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link PeepholeSubstituteAlternateSyntax} in isolation. Tests for the interaction of
 * multiple peephole passes are in PeepholeIntegrationTest.
 */
@RunWith(JUnit4.class)
public final class PeepholeSubstituteAlternateSyntaxTest extends CompilerTestCase {

  // Externs for built-in constructors
  // Needed for testFoldLiteralObjectConstructors(),
  // testFoldLiteralArrayConstructors() and testFoldRegExp...()
  private static final String FOLD_CONSTANTS_TEST_EXTERNS =
      "var window = {};\n" +
      "var Object = function f(){};\n" +
      "var RegExp = function f(a){};\n" +
      "var Array = function f(a){};\n" +
      "window.foo = null;\n";

  private boolean late;
  private boolean retraverseOnChange;

  public PeepholeSubstituteAlternateSyntaxTest() {
    super(FOLD_CONSTANTS_TEST_EXTERNS);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    late = true;
    retraverseOnChange = false;
    disableNormalize();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    PeepholeOptimizationsPass peepholePass =
        new PeepholeOptimizationsPass(
            compiler, getName(), new PeepholeSubstituteAlternateSyntax(late));
    peepholePass.setRetraverseOnChange(retraverseOnChange);
    return peepholePass;
  }

  @Test
  public void testFoldRegExpConstructor() {
    enableNormalize();

    // Cannot fold all the way to a literal because there are too few arguments.
    test("x = new RegExp", "x = RegExp()");
    // Empty regexp should not fold to // since that is a line comment
    test("x = new RegExp(\"\")", "x = RegExp(\"\")");
    test("x = new RegExp(\"\", \"i\")", "x = RegExp(\"\",\"i\")");

    // Regexp starting with * should not fold to /* since that is the start of a comment
    test("x = new RegExp('*')", "x = RegExp('*')");
    test("x = new RegExp('*', 'i')", "x = RegExp('*', 'i')");

    // Bogus flags should not fold
    testSame("x = RegExp(\"foobar\", \"bogus\")",
         PeepholeSubstituteAlternateSyntax.INVALID_REGULAR_EXPRESSION_FLAGS);
    // Don't fold if the argument is not a string. See issue 1260.
    testSame("x = new RegExp(y)");
    // Can Fold
    test("x = new RegExp(\"foobar\")", "x = /foobar/");
    test("x = RegExp(\"foobar\")", "x = /foobar/");
    test("x = new RegExp(\"foobar\", \"i\")", "x = /foobar/i");
    // Make sure that escaping works
    test("x = new RegExp(\"\\\\.\", \"i\")", "x = /\\./i");
    test("x = new RegExp(\"/\", \"\")", "x = /\\//");
    test("x = new RegExp(\"[/]\", \"\")", "x = /[/]/");
    test("x = new RegExp(\"///\", \"\")", "x = /\\/\\/\\//");
    test("x = new RegExp(\"\\\\\\/\", \"\")", "x = /\\//");
    test("x = new RegExp(\"\\n\")", "x = /\\n/");
    test("x = new RegExp('\\\\\\r')", "x = /\\r/");

    // Shouldn't fold RegExp unnormalized because
    // we can't be sure that RegExp hasn't been redefined
    disableNormalize();

    testSame("x = new RegExp(\"foobar\")");
  }

  @Test
  public void testVersionSpecificRegExpQuirks() {
    enableNormalize();

    // Don't fold if the flags contain 'g'
    setAcceptedLanguage(LanguageMode.ECMASCRIPT3);
    test("x = new RegExp(\"foobar\", \"g\")", "x = RegExp(\"foobar\",\"g\")");
    test("x = new RegExp(\"foobar\", \"ig\")", "x = RegExp(\"foobar\",\"ig\")");
    // ... unless in ECMAScript 5 mode per section 7.8.5 of ECMAScript 5.
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    test("x = new RegExp(\"foobar\", \"ig\")", "x = /foobar/ig");
    // Don't fold things that crash older versions of Safari and that don't work
    // as regex literals on other old versions of Safari
    setAcceptedLanguage(LanguageMode.ECMASCRIPT3);
    test("x = new RegExp(\"\\u2028\")", "x = RegExp(\"\\u2028\")");
    test("x = new RegExp(\"\\\\\\\\u2028\")", "x = /\\\\u2028/");
    // Sunset Safari exclusions for ECMAScript 5 and later.
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    test("x = new RegExp(\"\\u2028\\u2029\")", "x = /\\u2028\\u2029/");
    test("x = new RegExp(\"\\\\u2028\")", "x = /\\u2028/");
    test("x = new RegExp(\"\\\\\\\\u2028\")", "x = /\\\\u2028/");
  }

  @Test
  public void testFoldRegExpConstructorStringCompare() {
    enableNormalize();
    test("x = new RegExp(\"\\n\", \"i\")", "x = /\\n/i");
  }

  @Test
  public void testContainsUnicodeEscape() {
    assertThat(PeepholeSubstituteAlternateSyntax.containsUnicodeEscape("")).isFalse();
    assertThat(PeepholeSubstituteAlternateSyntax.containsUnicodeEscape("foo")).isFalse();
    assertThat(PeepholeSubstituteAlternateSyntax.containsUnicodeEscape("\u2028")).isTrue();
    assertThat(PeepholeSubstituteAlternateSyntax.containsUnicodeEscape("\\u2028")).isTrue();
    assertThat(PeepholeSubstituteAlternateSyntax.containsUnicodeEscape("foo\\u2028")).isTrue();
    assertThat(PeepholeSubstituteAlternateSyntax.containsUnicodeEscape("foo\\\\u2028")).isFalse();
    assertThat(PeepholeSubstituteAlternateSyntax.containsUnicodeEscape("foo\\\\u2028bar\\u2028"))
        .isTrue();
  }

  @Test
  public void testFoldLiteralObjectConstructors() {
    enableNormalize();

    // Can fold when normalized
    test("x = new Object", "x = ({})");
    test("x = new Object()", "x = ({})");
    test("x = Object()", "x = ({})");

    disableNormalize();
    // Cannot fold above when not normalized
    testSame("x = new Object");
    testSame("x = new Object()");
    testSame("x = Object()");

    enableNormalize();

    // Cannot fold, the constructor being used is actually a local function
    testSame("x = " + "(function f(){function Object(){this.x=4};return new Object();})();");
  }

  @Test
  public void testFoldLiteralObjectConstructors_onWindow() {
    enableNormalize();

    // Can fold when normalized
    test("x = new window.Object", "x = ({})");
    test("x = new window.Object()", "x = ({})");

    // Mustn't fold optional chains
    test("x = window.Object()", "x = ({})");
    test("x = window.Object?.()", "x = Object?.()");

    disableNormalize();
    // Cannot fold above when not normalized
    testSame("x = new window.Object");
    testSame("x = new window.Object()");
    testSame("x = window.Object()");
    testSame("x = window?.Object()");

    enableNormalize();

    // Can fold, the window namespace ensures it's not a conflict with the local Object.
    test(
        "x = (function f(){function Object(){this.x=4};return new window.Object;})();",
        "x = (function f(){function Object(){this.x=4};return {};})();");
  }

  @Test
  public void testFoldLiteralArrayConstructors() {
    enableNormalize();

    // No arguments - can fold when normalized
    test("x = new Array", "x = []");
    test("x = new Array()", "x = []");
    test("x = Array()", "x = []");
    testSame("x = Array?.()"); // Mustn't fold optional chains

    // One argument - can be fold when normalized
    test("x = new Array(0)", "x = []");
    test("x = Array(0)", "x = []");
    test("x = new Array(\"a\")", "x = [\"a\"]");
    test("x = Array(\"a\")", "x = [\"a\"]");

    // One argument - cannot be fold when normalized
    test("x = new Array(7)", "x = Array(7)");
    testSame("x = Array(7)");
    test("x = new Array(y)", "x = Array(y)");
    testSame("x = Array(y)");
    test("x = new Array(foo())", "x = Array(foo())");
    testSame("x = Array(foo())");

    // More than one argument - can be fold when normalized
    test("x = new Array(1, 2, 3, 4)", "x = [1, 2, 3, 4]");
    test("x = Array(1, 2, 3, 4)", "x = [1, 2, 3, 4]");
    test("x = new Array('a', 1, 2, 'bc', 3, {}, 'abc')", "x = ['a', 1, 2, 'bc', 3, {}, 'abc']");
    test("x = Array('a', 1, 2, 'bc', 3, {}, 'abc')", "x = ['a', 1, 2, 'bc', 3, {}, 'abc']");
    test("x = new Array(Array(1, '2', 3, '4'))", "x = [[1, '2', 3, '4']]");
    test("x = Array(Array(1, '2', 3, '4'))", "x = [[1, '2', 3, '4']]");
    test(
        "x = new Array(Object(), Array(\"abc\", Object(), Array(Array())))",
        "x = [{}, [\"abc\", {}, [[]]]]");
    test(
        "x = new Array(Object(), Array(\"abc\", Object(), Array(Array())))",
        "x = [{}, [\"abc\", {}, [[]]]]");

    disableNormalize();
    // Cannot fold above when not normalized
    testSame("x = new Array");
    testSame("x = new Array()");
    testSame("x = Array()");

    testSame("x = new Array(0)");
    testSame("x = Array(0)");
    testSame("x = new Array(\"a\")");
    testSame("x = Array(\"a\")");
    testSame("x = new Array(7)");
    testSame("x = Array(7)");
    testSame("x = new Array(foo())");
    testSame("x = Array(foo())");

    testSame("x = new Array(1, 2, 3, 4)");
    testSame("x = Array(1, 2, 3, 4)");
    testSame("x = new Array('a', 1, 2, 'bc', 3, {}, 'abc')");
    testSame("x = Array('a', 1, 2, 'bc', 3, {}, 'abc')");
    testSame("x = new Array(Array(1, '2', 3, '4'))");
    testSame("x = Array(Array(1, '2', 3, '4'))");
    testSame("x = new Array(" + "Object(), Array(\"abc\", Object(), Array(Array())))");
    testSame("x = new Array(" + "Object(), Array(\"abc\", Object(), Array(Array())))");
  }

  @Test
  public void testRemoveWindowRefs() {
    enableNormalize();
    test("x = window.Object", "x = Object");
    test("x = window.Object.keys", "x = Object.keys");
    test("if (window.Object) {}", "if (Object) {}");
    test("x = window.Object", "x = Object");
    test("x = window.Array", "x = Array");
    test("x = window.Error", "x = Error");
    test("x = window.RegExp", "x = RegExp");
    test("x = window.Math", "x = Math");

    // Not currently handled by the pass but should be folded in the future.
    testSame("x = window.String");

    // Don't fold properties on the window.
    testSame("x = window.foo");

    disableNormalize();
    // Cannot fold when not normalized
    testSame("x = window.Object");
    testSame("x = window.Object.keys");

    enableNormalize();
    testSame(
        "var x = "
            + "(function f(){var window = {Object: function() {}};return new window.Object;})();");
  }

  /**
   * Tests that it's safe to fold access on window that is non-optional but is under an optional
   * chain in the AST.
   */
  @Test
  public void testRemoveWindowRef_childOfOptionalChain() {
    enableNormalize();
    // ref on window can be folded preserving the optional access.
    test("x = window.Object?.keys", "x = Object?.keys");
    test("x = window.Object?.(keys)", "x = Object?.(keys)");

    // Show that the above works only on `BUILTIN_EXTERNS` such as Object but not on regular prop
    // accesses
    testSame("x = window.prop?.keys");
    testSame("x = window.prop?.(keys)");
  }

  /**
   * There isn't an obvious reason to write `window?.Boolean()` or `window?.Error()`, but if one
   * did, presumably there's some reason to believe that `window` won't be there. We don't optimize
   * away these checks.
   */
  @Test
  public void testDontRemoveWindowRefs_optChain() {
    enableNormalize();
    // Don't fold the optional chain check on window
    testSame("x = window?.Object");
    testSame("if (window?.Object) {}");
    testSame("x = window?.Object");
    testSame("x = window?.Array");
    testSame("x = window?.Error");
    testSame("x = window?.RegExp");
    testSame("x = window?.Math");
    testSame("x = window?.String");

    // Don't fold properties on the window anyway (non-optional or optional).
    testSame("x = window.foo");
    testSame("x = window?.foo");

    disableNormalize();
    // Cannot fold when not normalized
    testSame("x = window?.Object");
    testSame("x = window.Object?.keys");
  }

  @Test
  public void testFoldStandardConstructors() {
    testSame("new Foo('a')");
    testSame("var x = new goog.Foo(1)");
    testSame("var x = new String(1)");
    testSame("var x = new Number(1)");
    testSame("var x = new Boolean(1)");

    enableNormalize();

    test("var x = new Object('a')", "var x = Object('a')");
    test("var x = new RegExp('')", "var x = RegExp('')");
    test("var x = new Error('20')", "var x = Error(\"20\")");
    test("var x = new Array(20)", "var x = Array(20)");
  }

  @Test
  public void testFoldTrueFalse() {
    test("x = true", "x = !0");
    test("x = false", "x = !1");
  }

  @Test
  public void testFoldTrueFalseComparison() {
    test("x == true", "x == 1");
    test("x == false", "x == 0");
    test("x != true", "x != 1");
    test("x < true", "x < 1");
    test("x <= true", "x <= 1");
    test("x > true", "x > 1");
    test("x >= true", "x >= 1");
  }

  @Test
  public void testFoldSubtractionAssignment() {
    test("x -= 1", "--x");
    test("x -= -1", "++x");
  }

  @Test
  public void testFoldReturnResult() {
    testSame("function f(){return !1;}");
    testSame("function f(){return null;}");
    test("function f(){return void 0;}", "function f(){return}");
    testSame("function f(){return void foo();}");
    test("function f(){return undefined;}", "function f(){return}");
    test("function f(){if(a()){return undefined;}}", "function f(){if(a()){return}}");
  }

  @Test
  public void testUndefined() {
    testSame("var x = undefined");
    testSame("function f(f) {var undefined=2;var x = undefined;}");
    enableNormalize();
    test("var x = undefined", "var x=void 0");
    testSame("var undefined = 1;" + "function f() {var undefined=2;var x = undefined;}");
    testSame("function f(undefined) {}");
    testSame("try {} catch(undefined) {}");
    testSame("for (undefined in {}) {}");
    testSame("undefined++;");
    disableNormalize();
    testSame("undefined += undefined;");
    enableNormalize();
    test("undefined += undefined;", "undefined = void 0 + void 0;");
  }

  @Test
  public void testSplitCommaExpressions() {
    late = false;
    // Don't try to split in expressions.
    testSame("while (foo(), !0) boo()");
    testSame("var a = (foo(), !0);");
    testSame("a = (foo(), !0);");

    // Don't try to split COMMA under LABELs.
    testSame("a:a(),b()");

    test("(x=2), foo()", "x=2; foo()");
    test("foo(), boo();", "foo(); boo()");
    test("(a(), b()), (c(), d());", "a(), b(); c(), d()");
    test("a(); b(); (c(), d());", "a(); b(); c(); d();");
    test("foo(), true", "foo();true");
    testSame("foo();true");
    test("function x(){foo(), !0}", "function x(){foo(); !0}");
    testSame("function x(){foo(); !0}");
  }

  @Test
  public void testComma1() {
    late = false;
    test("1, 2", "1; 2");
    late = true;
    testSame("1, 2");
  }

  @Test
  public void testComma2() {
    late = false;
    test("1, a()", "1; a()");
    test("1, a?.()", "1; a?.()");

    late = true;
    testSame("1, a()");
    testSame("1, a?.()");
  }

  @Test
  public void testComma3() {
    late = false;
    test("1, a(), b()", "1, a(); b()");
    test("1, a?.(), b?.()", "1, a?.(); b?.()");

    late = true;
    testSame("1, a(), b()");
    testSame("1, a?.(), b?.()");
  }

  @Test
  public void testComma4() {
    late = false;
    test("a(), b()", "a();b()");
    test("a?.(), b?.()", "a?.();b?.()");

    late = true;
    testSame("a(), b()");
    testSame("a?.(), b?.()");
  }

  @Test
  public void testComma5() {
    late = false;
    test("a(), b(), 1", "a(), b(); 1");
    test("a?.(), b?.(), 1", "a?.(), b?.(); 1");

    late = true;
    testSame("a(), b(), 1");
    testSame("a?.(), b?.(), 1");
  }

  @Test
  public void testStringArraySplitting() {
    testSame("var x=['1','2','3','4']");
    testSame("var x=['1','2','3','4','5']");
    test("var x=['1','2','3','4','5','6']",
         "var x='123456'.split('')");
    test("var x=['1','2','3','4','5','00']",
         "var x='1 2 3 4 5 00'.split(' ')");
    test("var x=['1','2','3','4','5','6','7']",
        "var x='1234567'.split('')");
    test("var x=['1','2','3','4','5','6','00']",
         "var x='1 2 3 4 5 6 00'.split(' ')");
    test("var x=[' ,',',',',',',',',',',']",
         "var x=' ,;,;,;,;,;,'.split(';')");
    test("var x=[',,',' ',',',',',',',',']",
         "var x=',,; ;,;,;,;,'.split(';')");
    test("var x=['a,',' ',',',',',',',',']",
         "var x='a,; ;,;,;,;,'.split(';')");

    // all possible delimiters used, leave it alone
    testSame("var x=[',', ' ', ';', '{', '}']");
  }

  @Test
  public void testTemplateStringToString() {
    test("`abcde`", "'abcde'");
    test("`ab cd ef`", "'ab cd ef'");
    testSame("`hello ${name}`");
    testSame("tag `hello ${name}`");
    testSame("tag `hello`");
    test("`hello ${'foo'}`", "'hello foo'");
    test("`${2} bananas`", "'2 bananas'");
    test("`This is ${true}`", "'This is true'");
  }

  @Test
  public void testBindToCall1() {
    test("(goog.bind(f))()", "f()");
    test("(goog.bind(f,a))()", "f.call(a)");
    test("(goog.bind(f,a,b))()", "f.call(a,b)");

    test("(goog.bind(f))(a)", "f(a)");
    test("(goog.bind(f,a))(b)", "f.call(a,b)");
    test("(goog.bind(f,a,b))(c)", "f.call(a,b,c)");

    test("(goog.partial(f))()", "f()");
    test("(goog.partial(f,a))()", "f(a)");
    test("(goog.partial(f,a,b))()", "f(a,b)");

    test("(goog.partial(f))(a)", "f(a)");
    test("(goog.partial(f,a))(b)", "f(a,b)");
    test("(goog.partial(f,a,b))(c)", "f(a,b,c)");

    test("((function(){}).bind())()", "((function(){}))()");
    test("((function(){}).bind(a))()", "((function(){})).call(a)");
    test("((function(){}).bind(a,b))()", "((function(){})).call(a,b)");

    test("((function(){}).bind())(a)", "((function(){}))(a)");
    test("((function(){}).bind(a))(b)", "((function(){})).call(a,b)");
    test("((function(){}).bind(a,b))(c)", "((function(){})).call(a,b,c)");

    // Without using type information we don't know "f" is a function.
    testSame("(f.bind())()");
    testSame("(f.bind(a))()");
    testSame("(f.bind())(a)");
    testSame("(f.bind(a))(b)");

    // Don't rewrite if the bind isn't the immediate call target
    testSame("(goog.bind(f)).call(g)");
  }

  @Test
  public void testBindToCall2() {
    test("(goog$bind(f))()", "f()");
    test("(goog$bind(f,a))()", "f.call(a)");
    test("(goog$bind(f,a,b))()", "f.call(a,b)");

    test("(goog$bind(f))(a)", "f(a)");
    test("(goog$bind(f,a))(b)", "f.call(a,b)");
    test("(goog$bind(f,a,b))(c)", "f.call(a,b,c)");

    test("(goog$partial(f))()", "f()");
    test("(goog$partial(f,a))()", "f(a)");
    test("(goog$partial(f,a,b))()", "f(a,b)");

    test("(goog$partial(f))(a)", "f(a)");
    test("(goog$partial(f,a))(b)", "f(a,b)");
    test("(goog$partial(f,a,b))(c)", "f(a,b,c)");
    // Don't rewrite if the bind isn't the immediate call target
    testSame("(goog$bind(f)).call(g)");
  }

  @Test
  public void testBindToCall3() {
    // TODO(johnlenz): The code generator wraps free calls with (0,...) to
    // prevent leaking "this", but the parser doesn't unfold it, making a
    // AST comparison fail.  For now do a string comparison to validate the
    // correct code is in fact generated.
    // The FREE call wrapping should be moved out of the code generator
    // and into a denormalizing pass.
    disableCompareAsTree();
    retraverseOnChange = true;
    late = false;

    test("(goog.bind(f.m))()", "(0,f.m)()");
    test("(goog.bind(f.m,a))()", "f.m.call(a)");

    test("(goog.bind(f.m))(a)", "(0,f.m)(a)");
    test("(goog.bind(f.m,a))(b)", "f.m.call(a,b)");

    test("(goog.partial(f.m))()", "(0,f.m)()");
    test("(goog.partial(f.m,a))()", "(0,f.m)(a)");

    test("(goog.partial(f.m))(a)", "(0,f.m)(a)");
    test("(goog.partial(f.m,a))(b)", "(0,f.m)(a,b)");

    // Without using type information we don't know "f" is a function.
    testSame("f.m.bind()()");
    testSame("f.m.bind(a)()");
    testSame("f.m.bind()(a)");
    testSame("f.m.bind(a)(b)");

    // Don't rewrite if the bind isn't the immediate call target
    testSame("goog.bind(f.m).call(g)");
  }

  @Test
  public void testSimpleFunctionCall1() {
    test("var a = String(23)", "var a = '' + 23");
    // Don't fold the existence check to preserve behavior
    testSame("var a = String?.(23)");

    test("var a = String('hello')", "var a = '' + 'hello'");
    // Don't fold the existence check to preserve behavior
    testSame("var a = String?.('hello')");

    testSame("var a = String('hello', bar());");
    testSame("var a = String({valueOf: function() { return 1; }});");
  }

  @Test
  public void testSimpleFunctionCall2() {
    test("var a = Boolean(true)", "var a = !0");
    // Don't fold the existence check to preserve behavior
    test("var a = Boolean?.(true)", "var a = Boolean?.(!0)");

    test("var a = Boolean(false)", "var a = !1");
    // Don't fold the existence check to preserve behavior
    test("var a = Boolean?.(false)", "var a = Boolean?.(!1)");

    test("var a = Boolean(1)", "var a = !!1");
    // Don't fold the existence check to preserve behavior
    testSame("var a = Boolean?.(1)");

    test("var a = Boolean(x)", "var a = !!x");
    // Don't fold the existence check to preserve behavior
    testSame("var a = Boolean?.(x)");

    test("var a = Boolean({})", "var a = !!{}");
    // Don't fold the existence check to preserve behavior
    testSame("var a = Boolean?.({})");

    testSame("var a = Boolean()");
    testSame("var a = Boolean(!0, !1);");
  }

  @Test
  public void testRotateAssociativeOperators() {
    test("a || (b || c); a * (b * c); a | (b | c)",
        "(a || b) || c; (a * b) * c; (a | b) | c");
    testSame("a % (b % c); a / (b / c); a - (b - c);");
    test("a * (b % c);", "b % c * a");
    test("a * b * (c / d)", "c / d * b * a");
    test("(a + b) * (c % d)", "c % d * (a + b)");
    testSame("(a / b) * (c % d)");
    testSame("(c = 5) * (c % d)");
    test("(a + b) * c * (d % e)", "d % e * c * (a + b)");
    test("!a * c * (d % e)", "d % e * c * !a");
  }

  @Test
  public void nullishCoalesce() {
    test("a ?? (b ?? c);", "(a ?? b) ?? c");
  }

  @Test
  public void testNoRotateInfiniteLoop() {
    test("1/x * (y/1 * (1/z))", "1/x * (y/1) * (1/z)");
    testSame("1/x * (y/1) * (1/z)");
  }
}
