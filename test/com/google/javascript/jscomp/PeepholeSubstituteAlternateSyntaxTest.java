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

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  private void foldSame(String js) {
    testSame(js);
  }

  private void fold(String js, String expected) {
    test(js, expected);
  }

  @Test
  public void testFoldRegExpConstructor() {
    enableNormalize();

    // Cannot fold all the way to a literal because there are too few arguments.
    fold("x = new RegExp",                    "x = RegExp()");
    // Empty regexp should not fold to // since that is a line comment in JS
    fold("x = new RegExp(\"\")",              "x = RegExp(\"\")");
    fold("x = new RegExp(\"\", \"i\")",       "x = RegExp(\"\",\"i\")");
    // Bogus flags should not fold
    testSame("x = RegExp(\"foobar\", \"bogus\")",
         PeepholeSubstituteAlternateSyntax.INVALID_REGULAR_EXPRESSION_FLAGS);
    // Don't fold if the argument is not a string. See issue 1260.
    foldSame("x = new RegExp(y)");
    // Can Fold
    fold("x = new RegExp(\"foobar\")",        "x = /foobar/");
    fold("x = RegExp(\"foobar\")",            "x = /foobar/");
    fold("x = new RegExp(\"foobar\", \"i\")", "x = /foobar/i");
    // Make sure that escaping works
    fold("x = new RegExp(\"\\\\.\", \"i\")",  "x = /\\./i");
    fold("x = new RegExp(\"/\", \"\")",       "x = /\\//");
    fold("x = new RegExp(\"[/]\", \"\")",     "x = /[/]/");
    fold("x = new RegExp(\"///\", \"\")",     "x = /\\/\\/\\//");
    fold("x = new RegExp(\"\\\\\\/\", \"\")", "x = /\\//");
    fold("x = new RegExp(\"\\n\")",           "x = /\\n/");
    fold("x = new RegExp('\\\\\\r')",         "x = /\\r/");

    // Shouldn't fold RegExp unnormalized because
    // we can't be sure that RegExp hasn't been redefined
    disableNormalize();

    foldSame("x = new RegExp(\"foobar\")");
  }

  @Test
  public void testVersionSpecificRegExpQuirks() {
    enableNormalize();

    // Don't fold if the flags contain 'g'
    setAcceptedLanguage(LanguageMode.ECMASCRIPT3);
    fold("x = new RegExp(\"foobar\", \"g\")",
         "x = RegExp(\"foobar\",\"g\")");
    fold("x = new RegExp(\"foobar\", \"ig\")",
         "x = RegExp(\"foobar\",\"ig\")");
    // ... unless in ECMAScript 5 mode per section 7.8.5 of ECMAScript 5.
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    fold("x = new RegExp(\"foobar\", \"ig\")",
         "x = /foobar/ig");
    // Don't fold things that crash older versions of Safari and that don't work
    // as regex literals on other old versions of Safari
    setAcceptedLanguage(LanguageMode.ECMASCRIPT3);
    fold("x = new RegExp(\"\\u2028\")", "x = RegExp(\"\\u2028\")");
    fold("x = new RegExp(\"\\\\\\\\u2028\")", "x = /\\\\u2028/");
    // Sunset Safari exclusions for ECMAScript 5 and later.
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    fold("x = new RegExp(\"\\u2028\\u2029\")", "x = /\\u2028\\u2029/");
    fold("x = new RegExp(\"\\\\u2028\")", "x = /\\u2028/");
    fold("x = new RegExp(\"\\\\\\\\u2028\")", "x = /\\\\u2028/");
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
    fold("x = new Object", "x = ({})");
    fold("x = new Object()", "x = ({})");
    fold("x = Object()", "x = ({})");

    disableNormalize();
    // Cannot fold above when not normalized
    foldSame("x = new Object");
    foldSame("x = new Object()");
    foldSame("x = Object()");

    enableNormalize();

    // Cannot fold, the constructor being used is actually a local function
    foldSame("x = " +
         "(function f(){function Object(){this.x=4};return new Object();})();");
  }

  @Test
  public void testFoldLiteralObjectConstructors_onWindow() {
    enableNormalize();

    // Can fold when normalized
    fold("x = new window.Object", "x = ({})");
    fold("x = new window.Object()", "x = ({})");
    fold("x = window.Object()", "x = ({})");

    disableNormalize();
    // Cannot fold above when not normalized
    foldSame("x = new window.Object");
    foldSame("x = new window.Object()");
    foldSame("x = window.Object()");

    enableNormalize();

    // Can fold, the window namespace ensures it's not a conflict with the local Object.
    fold("x = (function f(){function Object(){this.x=4};return new window.Object;})();",
        "x = (function f(){function Object(){this.x=4};return {};})();");
  }

  @Test
  public void testFoldLiteralArrayConstructors() {
    enableNormalize();

    // No arguments - can fold when normalized
    fold("x = new Array", "x = []");
    fold("x = new Array()", "x = []");
    fold("x = Array()", "x = []");

    // One argument - can be fold when normalized
    fold("x = new Array(0)", "x = []");
    fold("x = Array(0)", "x = []");
    fold("x = new Array(\"a\")", "x = [\"a\"]");
    fold("x = Array(\"a\")", "x = [\"a\"]");

    // One argument - cannot be fold when normalized
    fold("x = new Array(7)", "x = Array(7)");
    foldSame("x = Array(7)");
    fold("x = new Array(y)", "x = Array(y)");
    foldSame("x = Array(y)");
    fold("x = new Array(foo())", "x = Array(foo())");
    foldSame("x = Array(foo())");

    // More than one argument - can be fold when normalized
    fold("x = new Array(1, 2, 3, 4)", "x = [1, 2, 3, 4]");
    fold("x = Array(1, 2, 3, 4)", "x = [1, 2, 3, 4]");
    fold("x = new Array('a', 1, 2, 'bc', 3, {}, 'abc')",
         "x = ['a', 1, 2, 'bc', 3, {}, 'abc']");
    fold("x = Array('a', 1, 2, 'bc', 3, {}, 'abc')",
         "x = ['a', 1, 2, 'bc', 3, {}, 'abc']");
    fold("x = new Array(Array(1, '2', 3, '4'))", "x = [[1, '2', 3, '4']]");
    fold("x = Array(Array(1, '2', 3, '4'))", "x = [[1, '2', 3, '4']]");
    fold("x = new Array(Object(), Array(\"abc\", Object(), Array(Array())))",
         "x = [{}, [\"abc\", {}, [[]]]]");
    fold("x = new Array(Object(), Array(\"abc\", Object(), Array(Array())))",
         "x = [{}, [\"abc\", {}, [[]]]]");

    disableNormalize();
    // Cannot fold above when not normalized
    foldSame("x = new Array");
    foldSame("x = new Array()");
    foldSame("x = Array()");

    foldSame("x = new Array(0)");
    foldSame("x = Array(0)");
    foldSame("x = new Array(\"a\")");
    foldSame("x = Array(\"a\")");
    foldSame("x = new Array(7)");
    foldSame("x = Array(7)");
    foldSame("x = new Array(foo())");
    foldSame("x = Array(foo())");

    foldSame("x = new Array(1, 2, 3, 4)");
    foldSame("x = Array(1, 2, 3, 4)");
    foldSame("x = new Array('a', 1, 2, 'bc', 3, {}, 'abc')");
    foldSame("x = Array('a', 1, 2, 'bc', 3, {}, 'abc')");
    foldSame("x = new Array(Array(1, '2', 3, '4'))");
    foldSame("x = Array(Array(1, '2', 3, '4'))");
    foldSame("x = new Array(" +
        "Object(), Array(\"abc\", Object(), Array(Array())))");
    foldSame("x = new Array(" +
        "Object(), Array(\"abc\", Object(), Array(Array())))");
  }

  @Test
  public void testRemoveWindowRefs() {
    enableNormalize();
    fold("x = window.Object", "x = Object");
    fold("x = window.Object.keys", "x = Object.keys");
    fold("if (window.Object) {}", "if (Object) {}");
    fold("x = window.Object", "x = Object");
    fold("x = window.Array", "x = Array");
    fold("x = window.Error", "x = Error");
    fold("x = window.RegExp", "x = RegExp");
    fold("x = window.Math", "x = Math");

    // Not currently handled by the pass but should be folded in the future.
    foldSame("x = window.String");

    // Don't fold properties on the window.
    foldSame("x = window.foo");

    disableNormalize();
    foldSame("x = window.Object");
    foldSame("x = window.Object.keys");

    enableNormalize();
    foldSame("var x = "
        + "(function f(){var window = {Object: function() {}};return new window.Object;})();");
  }

  @Test
  public void testFoldStandardConstructors() {
    foldSame("new Foo('a')");
    foldSame("var x = new goog.Foo(1)");
    foldSame("var x = new String(1)");
    foldSame("var x = new Number(1)");
    foldSame("var x = new Boolean(1)");

    enableNormalize();

    fold("var x = new Object('a')", "var x = Object('a')");
    fold("var x = new RegExp('')", "var x = RegExp('')");
    fold("var x = new Error('20')", "var x = Error(\"20\")");
    fold("var x = new Array(20)", "var x = Array(20)");
  }

  @Test
  public void testFoldTrueFalse() {
    fold("x = true", "x = !0");
    fold("x = false", "x = !1");
  }

  @Test
  public void testFoldTrueFalseComparison() {
    fold("x == true", "x == 1");
    fold("x == false", "x == 0");
    fold("x != true", "x != 1");
    fold("x < true", "x < 1");
    fold("x <= true", "x <= 1");
    fold("x > true", "x > 1");
    fold("x >= true", "x >= 1");
  }

  @Test
  public void testFoldSubtractionAssignment() {
    fold("x -= 1", "--x");
    fold("x -= -1", "++x");
  }

  @Test
  public void testFoldReturnResult() {
    foldSame("function f(){return !1;}");
    foldSame("function f(){return null;}");
    fold("function f(){return void 0;}",
         "function f(){return}");
    foldSame("function f(){return void foo();}");
    fold("function f(){return undefined;}",
         "function f(){return}");
    fold("function f(){if(a()){return undefined;}}",
         "function f(){if(a()){return}}");
  }

  @Test
  public void testUndefined() {
    foldSame("var x = undefined");
    foldSame("function f(f) {var undefined=2;var x = undefined;}");
    enableNormalize();
    fold("var x = undefined", "var x=void 0");
    foldSame(
        "var undefined = 1;" +
        "function f() {var undefined=2;var x = undefined;}");
    foldSame("function f(undefined) {}");
    foldSame("try {} catch(undefined) {}");
    foldSame("for (undefined in {}) {}");
    foldSame("undefined++;");
    disableNormalize();
    foldSame("undefined += undefined;");
    enableNormalize();
    fold("undefined += undefined;", "undefined = void 0 + void 0;");
  }

  @Test
  public void testSplitCommaExpressions() {
    late = false;
    // Don't try to split in expressions.
    foldSame("while (foo(), !0) boo()");
    foldSame("var a = (foo(), !0);");
    foldSame("a = (foo(), !0);");

    // Don't try to split COMMA under LABELs.
    foldSame("a:a(),b()");

    fold("(x=2), foo()", "x=2; foo()");
    fold("foo(), boo();", "foo(); boo()");
    fold("(a(), b()), (c(), d());", "a(), b(); c(), d()");
    fold("a(); b(); (c(), d());", "a(); b(); c(); d();");
    fold("foo(), true", "foo();true");
    foldSame("foo();true");
    fold("function x(){foo(), !0}", "function x(){foo(); !0}");
    foldSame("function x(){foo(); !0}");
  }

  @Test
  public void testComma1() {
    late = false;
    fold("1, 2", "1; 2");
    late = true;
    foldSame("1, 2");
  }

  @Test
  public void testComma2() {
    late = false;
    test("1, a()", "1; a()");
    late = true;
    foldSame("1, a()");
  }

  @Test
  public void testComma3() {
    late = false;
    test("1, a(), b()", "1, a(); b()");
    late = true;
    foldSame("1, a(), b()");
  }

  @Test
  public void testComma4() {
    late = false;
    test("a(), b()", "a();b()");
    late = true;
    foldSame("a(), b()");
  }

  @Test
  public void testComma5() {
    late = false;
    test("a(), b(), 1", "a(), b(); 1");
    late = true;
    foldSame("a(), b(), 1");
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
    test("var a = String('hello')", "var a = '' + 'hello'");
    testSame("var a = String('hello', bar());");
    testSame("var a = String({valueOf: function() { return 1; }});");
  }

  @Test
  public void testSimpleFunctionCall2() {
    test("var a = Boolean(true)", "var a = !0");
    test("var a = Boolean(false)", "var a = !1");
    test("var a = Boolean(1)", "var a = !!1");
    test("var a = Boolean(x)", "var a = !!x");
    test("var a = Boolean({})", "var a = !!{}");
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
  public void testNoRotateInfiniteLoop() {
    test("1/x * (y/1 * (1/z))", "1/x * (y/1) * (1/z)");
    testSame("1/x * (y/1) * (1/z)");
  }
}
