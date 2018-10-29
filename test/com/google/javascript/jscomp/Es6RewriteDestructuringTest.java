/*
 * Copyright 2015 The Closure Compiler Authors.
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
import static com.google.javascript.rhino.testing.TypeSubject.assertType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.Es6RewriteDestructuring.ObjectDestructuringRewriteMode;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class Es6RewriteDestructuringTest extends CompilerTestCase {

  private ObjectDestructuringRewriteMode destructuringRewriteMode =
      ObjectDestructuringRewriteMode.REWRITE_ALL_OBJECT_PATTERNS;
  private boolean useNoninjectingCompiler = true;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2018);
    enableTypeCheck();
    enableTypeInfoValidation();

    // there are a lot of 'property x never defined on ?' warnings caused by object destructuring
    ignoreWarnings(TypeCheck.POSSIBLE_INEXISTENT_PROPERTY);
  }

  @Override
  @After
  public void tearDown() throws Exception {
    super.tearDown();
    this.destructuringRewriteMode = ObjectDestructuringRewriteMode.REWRITE_ALL_OBJECT_PATTERNS;
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new Es6RewriteDestructuring.Builder(compiler)
        .setDestructuringRewriteMode(destructuringRewriteMode)
        .build();
  }

  @Test
  public void testObjectDestructuring() {
    test(
        "var {a: b, c: d} = foo();",
        lines(
            "var $jscomp$destructuring$var0 = foo();",
            "var b = $jscomp$destructuring$var0.a;",
            "var d = $jscomp$destructuring$var0.c;"));
    assertThat(((NoninjectingCompiler) getLastCompiler()).injected).isEmpty();

    test(
        "var {a,b} = foo();",
        lines(
            "var $jscomp$destructuring$var0 = foo();",
            "var a = $jscomp$destructuring$var0.a;",
            "var b = $jscomp$destructuring$var0.b;"));

    test(
        "let {a,b} = foo();",
        lines(
            "var $jscomp$destructuring$var0 = foo();",
            "let a = $jscomp$destructuring$var0.a;",
            "let b = $jscomp$destructuring$var0.b;"));

    test(
        "const {a,b} = foo();",
        lines(
            "/** @const */ var $jscomp$destructuring$var0 = foo();",
            "const a = $jscomp$destructuring$var0.a;",
            "const b = $jscomp$destructuring$var0.b;"));

    test(
        "var x; ({a: x} = foo());",
        lines(
            "var x;",
            "var $jscomp$destructuring$var0 = foo();",
            "x = $jscomp$destructuring$var0.a;"));
  }

  @Test
  public void testObjectDestructuringWithInitializer() {
    test(
        "var {a : b = 'default'} = foo();",
        lines(
            "var $jscomp$destructuring$var0 = foo();",
            "var b = ($jscomp$destructuring$var0.a === undefined) ?",
            "    'default' :",
            "    $jscomp$destructuring$var0.a"));

    test(
        "var {a = 'default'} = foo();",
        lines(
            "var $jscomp$destructuring$var0 = foo();",
            "var a = ($jscomp$destructuring$var0.a === undefined) ?",
            "    'default' :",
            "    $jscomp$destructuring$var0.a"));
  }

  @Test
  public void testObjectDestructuringNested() {
    test(
        "var {a: {b}} = foo();",
        lines(
            "var $jscomp$destructuring$var0 = foo();",
            "var $jscomp$destructuring$var1 = $jscomp$destructuring$var0.a;",
            "var b = $jscomp$destructuring$var1.b"));
  }

  @Test
  public void testObjectDestructuringComputedProps() {
    test(
        "var {[a]: b} = foo();",
        "var $jscomp$destructuring$var0 = foo(); var b = $jscomp$destructuring$var0[a];");

    test(
        "({[a]: b} = foo());",
        "var $jscomp$destructuring$var0 = foo(); b = $jscomp$destructuring$var0[a];");

    test(
        "var {[foo()]: x = 5} = {};",
        lines(
            "var $jscomp$destructuring$var0 = {};",
            "var $jscomp$destructuring$var1 = $jscomp$destructuring$var0[foo()];",
            "var x = $jscomp$destructuring$var1 === undefined ?",
            "    5 : $jscomp$destructuring$var1"));

    test(
        "function f({['KEY']: x}) {}",
        lines(
            "function f($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0",
            "  var x = $jscomp$destructuring$var1['KEY']",
            "}"));
  }

  // https://github.com/google/closure-compiler/issues/2189
  @Test
  public void testGithubIssue2189() {
    setExpectParseWarningsThisTest();
    test(
        lines(
            "/**",
            " * @param {string} a",
            " * @param {{b: ?<?>}} __1",
            " */",
            "function x(a, { b }) {}"),
        lines(
            "/**",
            " * @param {string} a",
            " * @param {{b: ?<?>}} __1",
            " */",
            "function x(a, $jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "  var b=$jscomp$destructuring$var1.b;",
            "}"));
  }

  @Test
  public void testObjectDestructuringStrangeProperties() {
    test(
        "var {5: b} = foo();",
        "var $jscomp$destructuring$var0 = foo(); var b = $jscomp$destructuring$var0['5']");

    test(
        "var {0.1: b} = foo();",
        lines(
            "var $jscomp$destructuring$var0 = foo();",
            "var b = $jscomp$destructuring$var0['0.1']"));

    test(
        "var {'str': b} = foo();",
        lines(
            "var $jscomp$destructuring$var0 = foo();",
            "var b = $jscomp$destructuring$var0['str']"));
  }

  @Test
  public void testObjectDestructuringFunction() {
    test(
        "function f({a: b}) {}",
        lines(
            "function f($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0",
            "  var b = $jscomp$destructuring$var1.a",
            "}"));

    test(
        "function f({a}) {}",
        lines(
            "function f($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0",
            "  var a = $jscomp$destructuring$var1.a",
            "}"));

    test(
        "function f({k: {subkey : a}}) {}",
        lines(
            "function f($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0",
            "  var $jscomp$destructuring$var2 = $jscomp$destructuring$var1.k;",
            "  var a = $jscomp$destructuring$var2.subkey;",
            "}"));

    test(
        "function f({k: [x, y, z]}) {}",
        lines(
            "function f($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0",
            "  var $jscomp$destructuring$var2 =",
            "      $jscomp.makeIterator($jscomp$destructuring$var1.k);",
            "  var x = $jscomp$destructuring$var2.next().value;",
            "  var y = $jscomp$destructuring$var2.next().value;",
            "  var z = $jscomp$destructuring$var2.next().value;",
            "}"));
    // TODO(b/77597706): inject this runtime library in Es6InjectRuntimeLibraries, so it will happen
    // before typechecking.
    assertThat(((NoninjectingCompiler) getLastCompiler()).injected)
        .containsExactly("es6/util/makeiterator");

    test(
        "function f({key: x = 5}) {}",
        lines(
            "function f($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0",
            "  var x = $jscomp$destructuring$var1.key === undefined ?",
            "      5 : $jscomp$destructuring$var1.key",
            "}"));

    test(
        "function f({[key]: x = 5}) {}",
        lines(
            "function f($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0",
            "  var $jscomp$destructuring$var2 = $jscomp$destructuring$var1[key]",
            "  var x = $jscomp$destructuring$var2 === undefined ?",
            "      5 : $jscomp$destructuring$var2",
            "}"));

    test(
        "function f({x = 5}) {}",
        lines(
            "function f($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0",
            "  var x = $jscomp$destructuring$var1.x === undefined ?",
            "      5 : $jscomp$destructuring$var1.x",
            "}"));
  }

  @Test
  public void testObjectDestructuringFunctionJsDoc() {
    test(
        "function f(/** {x: number, y: number} */ {x, y}) {}",
        lines(
            "function f(/** {x: number, y: number} */ $jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "  var x = $jscomp$destructuring$var1.x;",
            "  var y = $jscomp$destructuring$var1.y;",
            "}"));
  }

  @Test
  public void testDefaultParametersDestructuring() {
    test(
        "function f({a,b} = foo()) {}",
        lines(
            "function f($jscomp$destructuring$var0){",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0===undefined ?",
            "    foo() : $jscomp$destructuring$var0;",
            "  var a = $jscomp$destructuring$var1.a;",
            "  var b = $jscomp$destructuring$var1.b;",
            "}"));
  }

  @Test
  public void testArrayDestructuring() {
    test(
        "var [x,y] = z();",
        lines(
            "var $jscomp$destructuring$var0 = $jscomp.makeIterator(z());",
            "var x = $jscomp$destructuring$var0.next().value;",
            "var y = $jscomp$destructuring$var0.next().value;"));

    test(
        "var x,y;\n" + "[x,y] = z();",
        lines(
            "var x,y;",
            "var $jscomp$destructuring$var0 = $jscomp.makeIterator(z());",
            "x = $jscomp$destructuring$var0.next().value;",
            "y = $jscomp$destructuring$var0.next().value;"));

    test(
        "var [a,b] = c();" + "var [x,y] = z();",
        lines(
            "var $jscomp$destructuring$var0 = $jscomp.makeIterator(c());",
            "var a = $jscomp$destructuring$var0.next().value;",
            "var b = $jscomp$destructuring$var0.next().value;",
            "var $jscomp$destructuring$var1 = $jscomp.makeIterator(z());",
            "var x = $jscomp$destructuring$var1.next().value;",
            "var y = $jscomp$destructuring$var1.next().value;"));
  }

  @Test
  public void testArrayDestructuringDefaultValues() {
    test(
        "var a; [a=1] = b();",
        lines(
            "var a;",
            "var $jscomp$destructuring$var0 = $jscomp.makeIterator(b())",
            "var $jscomp$destructuring$var1 = $jscomp$destructuring$var0.next().value",
            "a = ($jscomp$destructuring$var1 === undefined) ?",
            "    1 :",
            "    $jscomp$destructuring$var1;"));

    test(
        "var [a=1] = b();",
        lines(
            "var $jscomp$destructuring$var0 = $jscomp.makeIterator(b())",
            "var $jscomp$destructuring$var1 = $jscomp$destructuring$var0.next().value",
            "var a = ($jscomp$destructuring$var1 === undefined) ?",
            "    1 :",
            "    $jscomp$destructuring$var1;"));

    test(
        "var [a, b=1, c] = d();",
        lines(
            "var $jscomp$destructuring$var0=$jscomp.makeIterator(d());",
            "var a = $jscomp$destructuring$var0.next().value;",
            "var $jscomp$destructuring$var1 = $jscomp$destructuring$var0.next().value;",
            "var b = ($jscomp$destructuring$var1 === undefined) ?",
            "    1 :",
            "    $jscomp$destructuring$var1;",
            "var c=$jscomp$destructuring$var0.next().value"));

    test(
        externs(MINIMAL_EXTERNS),
        srcs("var a; [[a] = ['b']] = [];"),
        expected(
            lines(
                "var a;",
                "var $jscomp$destructuring$var0 = $jscomp.makeIterator([]);",
                "var $jscomp$destructuring$var1 = $jscomp$destructuring$var0.next().value;",
                "var $jscomp$destructuring$var2 = $jscomp.makeIterator(",
                "    $jscomp$destructuring$var1 === undefined",
                "        ? ['b']",
                "        : $jscomp$destructuring$var1);",
                "a = $jscomp$destructuring$var2.next().value")));
  }

  @Test
  public void testArrayDestructuringParam() {
    test(
        "function f([x,y]) { use(x); use(y); }",
        lines(
            "function f($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp.makeIterator($jscomp$destructuring$var0);",
            "  var x = $jscomp$destructuring$var1.next().value;",
            "  var y = $jscomp$destructuring$var1.next().value;",
            "  use(x);",
            "  use(y);",
            "}"));

    test(
        "function f([x, , y]) { use(x); use(y); }",
        lines(
            "function f($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp.makeIterator($jscomp$destructuring$var0);",
            "  var x = $jscomp$destructuring$var1.next().value;",
            "  $jscomp$destructuring$var1.next();",
            "  var y = $jscomp$destructuring$var1.next().value;",
            "  use(x);",
            "  use(y);",
            "}"));
  }

  @Test
  public void testArrayDestructuringRest() {
    test(
        "let [one, ...others] = f();",
        lines(
            "var $jscomp$destructuring$var0 = $jscomp.makeIterator(f());",
            "let one = $jscomp$destructuring$var0.next().value;",
            "let others = $jscomp.arrayFromIterator($jscomp$destructuring$var0);"));
    assertThat(((NoninjectingCompiler) getLastCompiler()).injected)
        .containsExactly("es6/util/arrayfromiterator", "es6/util/makeiterator");

    test(
        "function f([first, ...rest]) {}",
        lines(
            "function f($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp.makeIterator($jscomp$destructuring$var0);",
            "  var first = $jscomp$destructuring$var1.next().value;",
            "  var rest = $jscomp.arrayFromIterator($jscomp$destructuring$var1);",
            "}"));
  }

  @Test
  public void testRestParamDestructuring() {
    test(
        "function f(first, ...[re, st, ...{length: num_left}]) {}",
        lines(
            "function f(first, ...$jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp.makeIterator($jscomp$destructuring$var0);",
            "  var re = $jscomp$destructuring$var1.next().value;",
            "  var st = $jscomp$destructuring$var1.next().value;",
            "  var $jscomp$destructuring$var2 = "
                + "$jscomp.arrayFromIterator($jscomp$destructuring$var1);",
            "  var num_left = $jscomp$destructuring$var2.length;",
            "}"));
  }

  @Test
  public void testArrayDestructuringMixedRest() {
    test(
        externs(MINIMAL_EXTERNS),
        srcs("let [first, ...[re, st, ...{length: num_left}]] = f();"),
        expected(
            lines(
                "var $jscomp$destructuring$var0 = $jscomp.makeIterator(f());",
                "let first = $jscomp$destructuring$var0.next().value;",
                "var $jscomp$destructuring$var1 = ",
                "    $jscomp.makeIterator(",
                "        $jscomp.arrayFromIterator($jscomp$destructuring$var0));",
                "let re = $jscomp$destructuring$var1.next().value;",
                "let st = $jscomp$destructuring$var1.next().value;",
                "var $jscomp$destructuring$var2 = ",
                "    $jscomp.arrayFromIterator($jscomp$destructuring$var1);",
                "let num_left = $jscomp$destructuring$var2.length;")));
  }

  @Test
  public void testArrayDestructuringArguments() {
    test(
    "function f() { var [x, y] = arguments; }",
    lines(
        "function f() {",
        "  var $jscomp$destructuring$var0 = $jscomp.makeIterator(arguments);",
        "  var x = $jscomp$destructuring$var0.next().value;",
        "  var y = $jscomp$destructuring$var0.next().value;",
        "}"));
  }

  @Test
  public void testMixedDestructuring() {
    test(
        "var [a,{b,c}] = foo();",
        lines(
            "var $jscomp$destructuring$var0 = $jscomp.makeIterator(foo());",
            "var a = $jscomp$destructuring$var0.next().value;",
            "var $jscomp$destructuring$var1 = $jscomp$destructuring$var0.next().value;",
            "var b = $jscomp$destructuring$var1.b;",
            "var c = $jscomp$destructuring$var1.c"));

    test(
        "var {a,b:[c,d]} = foo();",
        lines(
            "var $jscomp$destructuring$var0 = foo();",
            "var a = $jscomp$destructuring$var0.a;",
            "var $jscomp$destructuring$var1 = $jscomp.makeIterator($jscomp$destructuring$var0.b);",
            "var c = $jscomp$destructuring$var1.next().value;",
            "var d = $jscomp$destructuring$var1.next().value"));
  }

  @Test
  public void testDestructuringForOf() {
    test(
        "for ({x} of y) { console.log(x); }",
        lines(
            "for (var $jscomp$destructuring$var0 of y) {",
            "   var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "   x = $jscomp$destructuring$var1.x;",
            "   console.log(x);",
            "}"));
  }

  @Test
  public void testDestructuringForOfWithShadowing() {
    test(
        externs(MINIMAL_EXTERNS),
        srcs("for (const [value] of []) { const value = 0; }"),
        expected(
            lines(
                "for (const $jscomp$destructuring$var0 of []) {",
                "  var $jscomp$destructuring$var1 =",
                "      $jscomp.makeIterator($jscomp$destructuring$var0);",
                "  const value = $jscomp$destructuring$var1.next().value;",
                "  {",
                "    const value = 0;",
                "  }",
                "}")));
  }

  @Test
  public void testDestructuringForInWithShadowing() {
    test(
        "for (const [value] in {}) { const value = 0; }",
        lines(
            "for (const $jscomp$destructuring$var0 in {}) {",
            "  var $jscomp$destructuring$var1 = $jscomp.makeIterator($jscomp$destructuring$var0);",
            "  const value = $jscomp$destructuring$var1.next().value;",
            "  {",
            "    const value = 0;",
            "  }",
            "}"));
  }

  @Test
  public void testDefaultValueInObjectPattern() {
    test(
        "function f({x = a()}, y = b()) {}",
        lines(
            "function f($jscomp$destructuring$var0, y) {",
            "var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "var x = $jscomp$destructuring$var1.x === undefined",
            "       ? a() : $jscomp$destructuring$var1.x;",
            "y = y === undefined ? b() : y",
            "}"));
  }

  @Test
  public void testDefaultParameters() {
    enableTypeCheck();

    test(
        "function f(/** ? */ zero, /** ?= */ one = 1, /** ?= */ two = 2) {}; f(1); f(1,2,3);",
        lines(
            "function f(/** ? */ zero, /** ?= */ one, /** ?= */ two) {",
            "  one = (one === undefined) ? 1 : one;",
            "  two = (two === undefined) ? 2 : two;",
            "};",
            "f(1); f(1,2,3);"));

    test(
        srcs("function f(/** ? */ zero, /** ?= */ one = 1, /** ?= */ two = 2) {}; f();"),
        expected(
            lines(
                "function f(/** ? */ zero, /** ?= */ one, /** ?= */ two) {",
                "  one = (one === undefined) ? 1 : one;",
                "  two = (two === undefined) ? 2 : two;",
                "}; f();")),
        warning(TypeCheck.WRONG_ARGUMENT_COUNT));
  }

  @Test
  public void testDefaultAndRestParameters() {
    test(
        "function f(zero, one = 1, ...two) {}",
        lines(
            "function f(zero, one, ...two) {",
            "  one = (one === undefined) ? 1 : one;",
            "}"));

    test(
        "function f(/** number= */ x = 5) {}",
        lines(
            "function f(/** number= */ x) {",
            "  x = (x === undefined) ? 5 : x;",
            "}"));
  }

  @Test
  public void testDefaultUndefinedParameters() {
    enableTypeCheck();

    test("function f(zero, one=undefined) {}", "function f(zero, one) {}");

    test("function f(zero, one=void 42) {}", "function f(zero, one) {}");

    test("function f(zero, one=void(42)) {}", "function f(zero, one) {}");

    test("function f(zero, one=void '\\x42') {}", "function f(zero, one) {}");

    test(
        "function f(zero, one='undefined') {}",
        "function f(zero, one) {   one = (one === undefined) ? 'undefined' : one; }");

    test(
        "function f(zero, one=void g()) {}",
        "function f(zero, one) {   one = (one === undefined) ? void g() : one; }");
  }

  @Test
  public void testCatch() {
    test(
        "try {} catch ({message}) {}",
        lines(
            "try {} catch($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "  let message = $jscomp$destructuring$var1.message",
            "}"));
  }

  @Test
  public void testTypeCheck() {
    enableTypeCheck();

    test(
        "/** @param {{x: number}} obj */ function f({x}) {}",
        lines(
            "/** @param {{x: number}} obj */",
            "function f(obj) {",
            "  var $jscomp$destructuring$var0 = obj;",
            "  var x = $jscomp$destructuring$var0.x;",
            "}"));

    test(
        srcs(lines("/** @param {{x: number}} obj */", "function f({x}) {}", "f({ x: 'str'});")),
        warning(TypeValidator.TYPE_MISMATCH_WARNING));

    test(
        lines(
            "/** @param {{x: number}} obj */",
            "var f = function({x}) {}"),
        lines(
            "/** @param {{x: number}} obj */",
            "var f = function(obj) {",
            "  var $jscomp$destructuring$var0 = obj;",
            "  var x = $jscomp$destructuring$var0.x;",
            "}"));

    test(
        lines(
            "/** @param {{x: number}} obj */",
            "f = function({x}) {}"),
        lines(
            "/** @param {{x: number}} obj */",
            "f = function(obj) {",
            "  var $jscomp$destructuring$var0 = obj;",
            "  var x = $jscomp$destructuring$var0.x;",
            "}"));

    test(
        lines(
            "/** @param {{x: number}} obj */",
            "ns.f = function({x}) {}"),
        lines(
            "/** @param {{x: number}} obj */",
            "ns.f = function(obj) {",
            "  var $jscomp$destructuring$var0 = obj;",
            "  var x = $jscomp$destructuring$var0.x;",
            "}"));

    test(
        "ns.f = function({x} = {x: 0}) {};",
        lines(
            "ns.f = function($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 =",
            "      $jscomp$destructuring$var0 === undefined ? {x:0} : $jscomp$destructuring$var0;",
            "  var x = $jscomp$destructuring$var1.x",
            "};"));

    test(
        lines(
            "/** @param {{x: number}=} obj */",
            "ns.f = function({x} = {x: 0}) {};"),
        lines(
            "/** @param {{x: number}=} obj */",
            "ns.f = function(obj) {",
            "  var $jscomp$destructuring$var0 = obj===undefined ? {x:0} : obj;",
            "  var x = $jscomp$destructuring$var0.x",
            "};"));
  }

  @Test
  public void testDestructuringPatternInExterns() {
    enableTypeCheck();
    allowExternsChanges();

    testSame(
        externs(
            lines(
                MINIMAL_EXTERNS,
                "/** @constructor */",
                "function Foo() {}",
                "",
                "Foo.prototype.bar = function({a}) {};")),
        srcs("(new Foo).bar({b: 0});"));
    // TODO(sdh): figure out what's going on here
  }

  @Test
  public void testTypeCheck_inlineAnnotations() {
    enableTypeCheck();

    test(
        "function f(/** {x: number} */ {x}) {}",
        lines(
            "function f(/** {x: number} */ $jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "  var x = $jscomp$destructuring$var1.x;",
            "}"));

    test(
        srcs(lines("function f(/** {x: number} */ {x}) {}", "f({ x: 'str'});")),
        warning(TypeValidator.TYPE_MISMATCH_WARNING));
  }

  @Test
  public void testDestructuringArrayNotInExprResult() {
    test(
        externs(MINIMAL_EXTERNS),
        srcs(lines("var x, a, b;", "x = ([a,b] = [1,2])")),
        expected(
            lines(
                "var x,a,b;",
                "x = (()=>{",
                "   let $jscomp$destructuring$var0 = [1,2];",
                "   var $jscomp$destructuring$var1 =",
                "       $jscomp.makeIterator($jscomp$destructuring$var0);",
                "   a = $jscomp$destructuring$var1.next().value;",
                "   b = $jscomp$destructuring$var1.next().value;",
                "   return $jscomp$destructuring$var0;",
                "})();")));

    test(
        externs(MINIMAL_EXTERNS),
        srcs(
            lines(
                "var foo = function () {", "var x, a, b;", "x = ([a,b] = [1,2]);", "}", "foo();")),
        expected(
            lines(
                "var foo = function () {",
                " var x, a, b;",
                " x = (()=>{",
                "   let $jscomp$destructuring$var0 = [1,2];",
                "   var $jscomp$destructuring$var1 =",
                "       $jscomp.makeIterator($jscomp$destructuring$var0);",
                "   a = $jscomp$destructuring$var1.next().value;",
                "   b = $jscomp$destructuring$var1.next().value;",
                "   return $jscomp$destructuring$var0;",
                " })();",
                "}",
                "foo();")));

    test(
        externs(MINIMAL_EXTERNS),
        srcs(
            lines(
                "var prefix;",
                "for (;;[, prefix] = /** @type {!Array<string>} */ (/\\.?([^.]+)$/.exec(prefix))){",
                "}")),
        expected(
            lines(
                "var prefix;",
                "for (;;(() => {",
                "   let $jscomp$destructuring$var0 =",
                "       /** @type {!Array<string>} */ (/\\.?([^.]+)$/.exec(prefix));",
                "   var $jscomp$destructuring$var1 = ",
                "       $jscomp.makeIterator($jscomp$destructuring$var0);",
                "   $jscomp$destructuring$var1.next();",
                "   prefix = $jscomp$destructuring$var1.next().value;",
                "   return $jscomp$destructuring$var0;",
                " })()){",
                "}")));

    test(
        externs(MINIMAL_EXTERNS),
        srcs(
            lines(
                "var prefix;",
                "for (;;[, prefix] = /** @type {!Array<string>} */ (/\\.?([^.]+)$/.exec(prefix))){",
                "   console.log(prefix);",
                "}")),
        expected(
            lines(
                "var prefix;",
                "for (;;(() => {",
                "   let $jscomp$destructuring$var0 =",
                "       /** @type {!Array<string>} */ (/\\.?([^.]+)$/.exec(prefix));",
                "   var $jscomp$destructuring$var1 = ",
                "$jscomp.makeIterator($jscomp$destructuring$var0);",
                "   $jscomp$destructuring$var1.next();",
                "   prefix = $jscomp$destructuring$var1.next().value;",
                "   return $jscomp$destructuring$var0;",
                " })()){",
                " console.log(prefix);",
                "}")));

    test(
        externs(MINIMAL_EXTERNS),
        srcs(lines("for (var x = 1; x < 3; [x,] = [3,4]){", "   console.log(x);", "}")),
        expected(
            lines(
                "for (var x = 1; x < 3; (()=>{",
                "   let $jscomp$destructuring$var0 = [3,4]",
                "   var $jscomp$destructuring$var1 = ",
                "       $jscomp.makeIterator($jscomp$destructuring$var0);",
                "   x = $jscomp$destructuring$var1.next().value;",
                "   return $jscomp$destructuring$var0;",
                " })()){",
                "console.log(x);",
                "}")));
  }

  @Test
  public void testDestructuringObjectNotInExprResult() {
    test(
        "var x = ({a: b, c: d} = foo());",
        lines(
            "var x = (()=>{",
            "   let $jscomp$destructuring$var0 = foo();",
            "   var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "   b = $jscomp$destructuring$var1.a;",
            "   d = $jscomp$destructuring$var1.c;",
            "   return $jscomp$destructuring$var0;",
            "})();"));

    test(
        "var x = ({a: b, c: d} = foo());",
        lines(
            "var x = (()=>{",
            "   let $jscomp$destructuring$var0 = foo();",
            "   var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "   b = $jscomp$destructuring$var1.a;",
            "   d = $jscomp$destructuring$var1.c;",
            "   return $jscomp$destructuring$var0;",
            "})();"));

    test(
        "var x; var y = ({a: x} = foo());",
        lines(
            "var x;",
            "var y = (()=>{",
            "   let $jscomp$destructuring$var0 = foo();",
            "   var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "   x = $jscomp$destructuring$var1.a;",
            "   return $jscomp$destructuring$var0;",
            "})();"));

    test(
        "var x; var y = (() => {return {a,b} = foo();})();",
        lines(
            "var x;",
            "var y = (()=>{",
            "   return (()=>{",
            "       let $jscomp$destructuring$var0 = foo();",
            "       var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "       a = $jscomp$destructuring$var1.a;",
            "       b = $jscomp$destructuring$var1.b;",
            "       return $jscomp$destructuring$var0;",
            "   })();",
            "})();"));
  }

  @Test
  public void testNestedDestructuring() {
    test(
        externs(MINIMAL_EXTERNS),
        srcs("var [[x]] = [[1]];"),
        expected(
            lines(
                "var $jscomp$destructuring$var0 = $jscomp.makeIterator([[1]]);",
                "var $jscomp$destructuring$var1 = ",
                "$jscomp.makeIterator($jscomp$destructuring$var0.next().value);",
                "var x = $jscomp$destructuring$var1.next().value;")));

    test(
        externs(MINIMAL_EXTERNS),
        srcs("var [[x,y],[z]] = [[1,2],[3]];"),
        expected(
            lines(
                "var $jscomp$destructuring$var0 = $jscomp.makeIterator([[1,2],[3]]);",
                "var $jscomp$destructuring$var1 = ",
                "$jscomp.makeIterator($jscomp$destructuring$var0.next().value);",
                "var x = $jscomp$destructuring$var1.next().value;",
                "var y = $jscomp$destructuring$var1.next().value;",
                "var $jscomp$destructuring$var2 = ",
                "$jscomp.makeIterator($jscomp$destructuring$var0.next().value);",
                "var z = $jscomp$destructuring$var2.next().value;")));

    test(
        externs(MINIMAL_EXTERNS),
        srcs("var [[x,y],z] = [[1,2],3];"),
        expected(
            lines(
                "var $jscomp$destructuring$var0 = $jscomp.makeIterator([[1,2],3]);",
                "var $jscomp$destructuring$var1 = ",
                "$jscomp.makeIterator($jscomp$destructuring$var0.next().value);",
                "var x = $jscomp$destructuring$var1.next().value;",
                "var y = $jscomp$destructuring$var1.next().value;",
                "var z = $jscomp$destructuring$var0.next().value;")));
  }

  @Test
  public void testTryCatch() {
    test(
        lines("var x = 1;", "try {", "  throw [];", "} catch ([x]) {}"),
        lines(
            "var x = 1;",
            "try {",
            "  throw [];",
            "} catch ($jscomp$destructuring$var0) {",
            "   var $jscomp$destructuring$var1 = $jscomp.makeIterator($jscomp$destructuring$var0);",
            "   let x = $jscomp$destructuring$var1.next().value;",
            "}"));

    test(
        lines("var x = 1;", "try {", "  throw [[]];", "} catch ([[x]]) {}"),
        lines(
            "var x = 1;",
            "try {",
            "  throw [[]];",
            "} catch ($jscomp$destructuring$var0) {",
            "   var $jscomp$destructuring$var1 = $jscomp.makeIterator($jscomp$destructuring$var0);",
            "   var $jscomp$destructuring$var2 = ",
            "$jscomp.makeIterator($jscomp$destructuring$var1.next().value);",
            "   let x = $jscomp$destructuring$var2.next().value;",
            "}"));
  }

  @Test
  public void testObjectPatternWithRestDecl() {
    test(
        "var {a: b, c: d, ...rest} = foo();",
        lines(
            "var $jscomp$destructuring$var0 = foo();",
            "var $jscomp$destructuring$var1 = Object.assign({}, $jscomp$destructuring$var0);",
            "var b = $jscomp$destructuring$var0.a;",
            "var d = $jscomp$destructuring$var0.c;",
            "var rest = (delete $jscomp$destructuring$var1.a,",
            "            delete $jscomp$destructuring$var1.c,",
            "            $jscomp$destructuring$var1);"));

    test(
        "const {a: b, c: d, ...rest} = foo();",
        lines(
            "/** @const */ var $jscomp$destructuring$var0 = foo();",
            "var $jscomp$destructuring$var1 = Object.assign({}, $jscomp$destructuring$var0);",
            "const b = $jscomp$destructuring$var0.a;",
            "const d = $jscomp$destructuring$var0.c;",
            "const rest = (delete $jscomp$destructuring$var1.a,",
            "              delete $jscomp$destructuring$var1.c,",
            "              $jscomp$destructuring$var1);"));

    test(
        "let {a: b, c: d, ...rest} = foo();",
        lines(
            "var $jscomp$destructuring$var0 = foo();",
            "var $jscomp$destructuring$var1 = Object.assign({}, $jscomp$destructuring$var0);",
            "let b = $jscomp$destructuring$var0.a;",
            "let d = $jscomp$destructuring$var0.c;",
            "let rest = (delete $jscomp$destructuring$var1.a,",
            "            delete $jscomp$destructuring$var1.c,",
            "            $jscomp$destructuring$var1);"));

    test(
        "var pre = foo(); var {a: b, c: d, ...rest} = foo();",
        lines(
            "var pre = foo();",
            "var $jscomp$destructuring$var0 = foo();",
            "var $jscomp$destructuring$var1 = Object.assign({}, $jscomp$destructuring$var0);",
            "var b = $jscomp$destructuring$var0.a;",
            "var d = $jscomp$destructuring$var0.c;",
            "var rest = (delete $jscomp$destructuring$var1.a,",
            "            delete $jscomp$destructuring$var1.c,",
            "            $jscomp$destructuring$var1);"));

    test(
        "var {a: b, c: d, ...rest} = foo(); var post = foo();",
        lines(
            "var $jscomp$destructuring$var0 = foo();",
            "var $jscomp$destructuring$var1 = Object.assign({}, $jscomp$destructuring$var0);",
            "var b = $jscomp$destructuring$var0.a;",
            "var d = $jscomp$destructuring$var0.c;",
            "var rest = (delete $jscomp$destructuring$var1.a,",
            "            delete $jscomp$destructuring$var1.c,",
            "            $jscomp$destructuring$var1);",
            "var post = foo();"));

    test(
        "var pre = foo(); var {a: b, c: d, ...rest} = foo(); var post = foo();",
        lines(
            "var pre = foo();",
            "var $jscomp$destructuring$var0 = foo();",
            "var $jscomp$destructuring$var1 = Object.assign({}, $jscomp$destructuring$var0);",
            "var b = $jscomp$destructuring$var0.a;",
            "var d = $jscomp$destructuring$var0.c;",
            "var rest = (delete $jscomp$destructuring$var1.a,",
            "            delete $jscomp$destructuring$var1.c,",
            "            $jscomp$destructuring$var1);",
            "var post = foo();"));

    test(
        "var {a: b1, c: d1, ...rest1} = foo(); var {a: b2, c: d2, ...rest2} = foo();",
        lines(
            "var $jscomp$destructuring$var0 = foo();",
            "var $jscomp$destructuring$var1 = Object.assign({}, $jscomp$destructuring$var0);",
            "var b1 = $jscomp$destructuring$var0.a;",
            "var d1 = $jscomp$destructuring$var0.c;",
            "var rest1 = (delete $jscomp$destructuring$var1.a,",
            "             delete $jscomp$destructuring$var1.c,",
            "             $jscomp$destructuring$var1);",
            "var $jscomp$destructuring$var2 = foo();",
            "var $jscomp$destructuring$var3 = Object.assign({}, $jscomp$destructuring$var2);",
            "var b2 = $jscomp$destructuring$var2.a;",
            "var d2 = $jscomp$destructuring$var2.c;",
            "var rest2 = (delete $jscomp$destructuring$var3.a,",
            "             delete $jscomp$destructuring$var3.c,",
            "             $jscomp$destructuring$var3);"));

    test(
        "var {...rest} = foo();",
        lines(
            "var $jscomp$destructuring$var0 = foo();",
            "var $jscomp$destructuring$var1 = Object.assign({}, $jscomp$destructuring$var0);",
            "var rest = ($jscomp$destructuring$var1);"));

    test(
        "const {...rest} = foo();",
        lines(
            "/** @const */ var $jscomp$destructuring$var0 = foo();",
            "var $jscomp$destructuring$var1 = Object.assign({}, $jscomp$destructuring$var0);",
            "const rest = ($jscomp$destructuring$var1);"));
  }

  @Test
  public void testObjectPatternWithRestAssignStatement() {
    test(
        "var b,d,rest; ({a: b, c: d, ...rest} = foo());",
        lines(
            "var b,d,rest;",
            "var $jscomp$destructuring$var0 = foo();",
            "var $jscomp$destructuring$var1 = Object.assign({}, $jscomp$destructuring$var0);",
            "b = $jscomp$destructuring$var0.a;",
            "d = $jscomp$destructuring$var0.c;",
            "rest = (delete $jscomp$destructuring$var1.a,",
            "            delete $jscomp$destructuring$var1.c,",
            "            $jscomp$destructuring$var1);"));

    test(
        "var b,d,rest,pre; pre = foo(), {a: b, c: d, ...rest} = foo();",
        lines(
            "var b,d,rest,pre;",
            "pre = foo(),",
            "      (() => {",
            "        let $jscomp$destructuring$var0 = foo();",
            "        var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "        var $jscomp$destructuring$var2=Object.assign({},$jscomp$destructuring$var1);",
            "        b = $jscomp$destructuring$var1.a;",
            "        d = $jscomp$destructuring$var1.c;",
            "        rest = (delete $jscomp$destructuring$var2.a,",
            "                delete $jscomp$destructuring$var2.c,",
            "                $jscomp$destructuring$var2);",
            "        return $jscomp$destructuring$var0",
            "      })();"));

    test(
        "var b,d,rest,post; ({a: b, c: d, ...rest} = foo()), post = foo();",
        lines(
            "var b,d,rest,post;",
            "(() => {",
            "  let $jscomp$destructuring$var0 = foo();",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "  var $jscomp$destructuring$var2 = Object.assign({},$jscomp$destructuring$var1);",
            "  b = $jscomp$destructuring$var1.a;",
            "  d = $jscomp$destructuring$var1.c;",
            "  rest = (delete $jscomp$destructuring$var2.a,",
            "          delete $jscomp$destructuring$var2.c,",
            "          $jscomp$destructuring$var2);",
            "  return $jscomp$destructuring$var0",
            "})(), post = foo();"));

    test(
        "var b,d,rest,pre,post; pre = foo(), {a: b, c: d, ...rest} = foo(), post = foo();",
        lines(
            "var b,d,rest,pre,post;",
            "pre = foo(),",
            "      (() => {",
            "        let $jscomp$destructuring$var0 = foo();",
            "        var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "        var $jscomp$destructuring$var2=Object.assign({},$jscomp$destructuring$var1);",
            "        b = $jscomp$destructuring$var1.a;",
            "        d = $jscomp$destructuring$var1.c;",
            "        rest = (delete $jscomp$destructuring$var2.a,",
            "                delete $jscomp$destructuring$var2.c,",
            "                $jscomp$destructuring$var2);",
            "        return $jscomp$destructuring$var0",
            "      })(),",
            "      post = foo();"));

    test(
        lines(
            "var b1,d1,rest1,b2,d2,rest2;",
            "({a: b1, c: d1, ...rest1} = foo(),",
            " {a: b2, c: d2, ...rest2} = foo());"),
        lines(
            "var b1,d1,rest1,b2,d2,rest2;",
            "      (() => {",
            "        let $jscomp$destructuring$var0 = foo();",
            "        var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "        var $jscomp$destructuring$var2=Object.assign({},$jscomp$destructuring$var1);",
            "        b1 = $jscomp$destructuring$var1.a;",
            "        d1 = $jscomp$destructuring$var1.c;",
            "        rest1 = (delete $jscomp$destructuring$var2.a,",
            "                 delete $jscomp$destructuring$var2.c,",
            "                 $jscomp$destructuring$var2);",
            "        return $jscomp$destructuring$var0",
            "      })(),",
            "      (() => {",
            "        let $jscomp$destructuring$var3 = foo();",
            "        var $jscomp$destructuring$var4 = $jscomp$destructuring$var3;",
            "        var $jscomp$destructuring$var5=Object.assign({},$jscomp$destructuring$var4);",
            "        b2 = $jscomp$destructuring$var4.a;",
            "        d2 = $jscomp$destructuring$var4.c;",
            "        rest2 = (delete $jscomp$destructuring$var5.a,",
            "                 delete $jscomp$destructuring$var5.c,",
            "                 $jscomp$destructuring$var5);",
            "        return $jscomp$destructuring$var3",
            "      })();"));
  }

  @Test
  public void testObjectPatternWithRestAssignExpr() {
    test(
        "var x,b,d,rest; x = ({a: b, c: d, ...rest} = foo());",
        lines(
            "var x,b,d,rest;",
            "x = (()=>{",
            "    let $jscomp$destructuring$var0 = foo();",
            "    var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "    var $jscomp$destructuring$var2 = Object.assign({}, $jscomp$destructuring$var1);",
            "    b = $jscomp$destructuring$var1.a;",
            "    d = $jscomp$destructuring$var1.c;",
            "    rest = (delete $jscomp$destructuring$var2.a,",
            "            delete $jscomp$destructuring$var2.c,",
            "            $jscomp$destructuring$var2);",
            "    return $jscomp$destructuring$var0",
            "})();"));

    test(
        "var x,b,d,rest; baz({a: b, c: d, ...rest} = foo());",
        lines(
            "var x,b,d,rest;",
            "baz((()=>{",
            "    let $jscomp$destructuring$var0 = foo();",
            "    var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "    var $jscomp$destructuring$var2 = Object.assign({}, $jscomp$destructuring$var1);",
            "    b = $jscomp$destructuring$var1.a;",
            "    d = $jscomp$destructuring$var1.c;",
            "    rest = (delete $jscomp$destructuring$var2.a,",
            "            delete $jscomp$destructuring$var2.c,",
            "            $jscomp$destructuring$var2);",
            "    return $jscomp$destructuring$var0;",
            "})());"));
  }

  @Test
  public void testObjectPatternWithRestForOf() {
    test(
        "for ({a: b, c: d, ...rest} of foo()) { console.log(rest.z); }",
        lines(
            "for (var $jscomp$destructuring$var0 of foo()) {",
            "    var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "    var $jscomp$destructuring$var2 = Object.assign({}, $jscomp$destructuring$var1);",
            "    b = $jscomp$destructuring$var1.a;",
            "    d = $jscomp$destructuring$var1.c;",
            "    rest = (delete $jscomp$destructuring$var2.a,",
            "            delete $jscomp$destructuring$var2.c,",
            "            $jscomp$destructuring$var2);",
            "    console.log(rest.z);",
            "}"));

    test(
        "for (var {a: b, c: d, ...rest} of foo()) { console.log(rest.z); }",
        lines(
            "for (var $jscomp$destructuring$var0 of foo()) {",
            "    var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "    var $jscomp$destructuring$var2 = Object.assign({}, $jscomp$destructuring$var1);",
            "    var b = $jscomp$destructuring$var1.a;",
            "    var d = $jscomp$destructuring$var1.c;",
            "    var rest = (delete $jscomp$destructuring$var2.a,",
            "            delete $jscomp$destructuring$var2.c,",
            "            $jscomp$destructuring$var2);",
            "    {",
            "      console.log(rest.z);",
            "    }",
            "}"));

    test(
        "for (let {a: b, c: d, ...rest} of foo()) { console.log(rest.z); }",
        lines(
            "for (let $jscomp$destructuring$var0 of foo()) {",
            "    var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "    var $jscomp$destructuring$var2 = Object.assign({}, $jscomp$destructuring$var1);",
            "    let b = $jscomp$destructuring$var1.a;",
            "    let d = $jscomp$destructuring$var1.c;",
            "    let rest = (delete $jscomp$destructuring$var2.a,",
            "            delete $jscomp$destructuring$var2.c,",
            "            $jscomp$destructuring$var2);",
            "    {",
            "      console.log(rest.z);",
            "    }",
            "}"));

    test(
        "for (const {a: b, c: d, ...rest} of foo()) { console.log(rest.z); }",
        lines(
            "for (const $jscomp$destructuring$var0 of foo()) {",
            "    /** @const */ var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "    var $jscomp$destructuring$var2 = Object.assign({}, $jscomp$destructuring$var1);",
            "    const b = $jscomp$destructuring$var1.a;",
            "    const d = $jscomp$destructuring$var1.c;",
            "    const rest = (delete $jscomp$destructuring$var2.a,",
            "            delete $jscomp$destructuring$var2.c,",
            "            $jscomp$destructuring$var2);",
            "    {",
            "      console.log(rest.z);",
            "    }",
            "}"));

    test(
        "for (var {a: b, [baz()]: d, ...rest} of foo()) { console.log(rest.z); }",
        lines(
            "for (var $jscomp$destructuring$var0 of foo()) {",
            "    var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "    var $jscomp$destructuring$var2 = Object.assign({}, $jscomp$destructuring$var1);",
            "    var b = $jscomp$destructuring$var1.a;",
            "    var $jscomp$destructuring$var3 = baz();",
            "    var d = $jscomp$destructuring$var1[$jscomp$destructuring$var3];",
            "    var rest = (delete $jscomp$destructuring$var2.a,",
            "            delete $jscomp$destructuring$var2[$jscomp$destructuring$var3],",
            "            $jscomp$destructuring$var2);",
            "    {",
            "      console.log(rest.z);",
            "    }",
            "}"));

    test(
        "for (var {a: b, [baz()]: d = 1, ...rest} of foo()) { console.log(rest.z); }",
        lines(
            "for (var $jscomp$destructuring$var0 of foo()) {",
            "    var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "    var $jscomp$destructuring$var2 = Object.assign({}, $jscomp$destructuring$var1);",
            "    var b = $jscomp$destructuring$var1.a;",
            "    var $jscomp$destructuring$var3 = baz();",
            "    var $jscomp$destructuring$var4 = ",
            "        $jscomp$destructuring$var1[$jscomp$destructuring$var3];",
            "    var d = $jscomp$destructuring$var4===undefined ? 1 : $jscomp$destructuring$var4;",
            "    var rest = (delete $jscomp$destructuring$var2.a,",
            "            delete $jscomp$destructuring$var2[$jscomp$destructuring$var3],",
            "            $jscomp$destructuring$var2);",
            "    {",
            "      console.log(rest.z);",
            "    }",
            "}"));
  }

  @Test
  public void testObjectPatternWithRestAndComputedPropertyName() {
    test(
        "var {a: b = 3, [bar()]: d, [baz()]: e, ...rest} = foo();",
        lines(
            "var $jscomp$destructuring$var0 = foo();",
            "var $jscomp$destructuring$var1 = Object.assign({},$jscomp$destructuring$var0);",
            "var b = $jscomp$destructuring$var0.a===undefined ? 3 : $jscomp$destructuring$var0.a;",
            "var $jscomp$destructuring$var2 = bar();",
            "var d = $jscomp$destructuring$var0[$jscomp$destructuring$var2];",
            "var $jscomp$destructuring$var3 = baz();",
            "var e = $jscomp$destructuring$var0[$jscomp$destructuring$var3];",
            "var rest = (delete $jscomp$destructuring$var1.a,",
            "            delete $jscomp$destructuring$var1[$jscomp$destructuring$var2],",
            "            delete $jscomp$destructuring$var1[$jscomp$destructuring$var3],",
            "            $jscomp$destructuring$var1);"));
  }

  @Test
  public void testObjectPatternWithRestAndDefaults() {
    test(
        "var {a = 3, ...rest} = foo();",
        lines(
            "var $jscomp$destructuring$var0 = foo();",
            "var $jscomp$destructuring$var1 = Object.assign({}, $jscomp$destructuring$var0);",
            "var a = $jscomp$destructuring$var0.a===undefined ? 3 : $jscomp$destructuring$var0.a;",
            "var rest = (delete $jscomp$destructuring$var1.a,",
            "            $jscomp$destructuring$var1);"));

    test(
        "var {[bar()]:a = 3, 'b c':b = 12, ...rest} = foo();",
        lines(
            "var $jscomp$destructuring$var0=foo();",
            "var $jscomp$destructuring$var1 = Object.assign({},$jscomp$destructuring$var0);",
            "var $jscomp$destructuring$var2 = bar();",
            "var $jscomp$destructuring$var3 =",
            "    $jscomp$destructuring$var0[$jscomp$destructuring$var2];",
            "var a = $jscomp$destructuring$var3===undefined ? 3 : $jscomp$destructuring$var3;",
            "var b = $jscomp$destructuring$var0[\"b c\"]===undefined",
            "    ? 12 : $jscomp$destructuring$var0[\"b c\"];",
            "var rest=(delete $jscomp$destructuring$var1[$jscomp$destructuring$var2],",
            "          delete $jscomp$destructuring$var1[\"b c\"],",
            "          $jscomp$destructuring$var1);"));
  }

  @Test
  public void testObjectPatternWithRestInCatch() {
    test(
        "try {} catch ({first, second, ...rest}) { console.log(rest.z); }",
        lines(
            "try {}",
            "catch ($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "  var $jscomp$destructuring$var2 = Object.assign({}, $jscomp$destructuring$var1);",
            "  let first = $jscomp$destructuring$var1.first;",
            "  let second = $jscomp$destructuring$var1.second;",
            "  let rest = (delete $jscomp$destructuring$var2.first, ",
            "              delete $jscomp$destructuring$var2.second, ",
            "              $jscomp$destructuring$var2);",
            "  console.log(rest.z);",
            "}"));
  }

  @Test
  public void testObjectPatternWithRestAssignReturn() {
    test(
        "function f() { return {x:a, ...rest} = foo(); }",
        lines(
            "function f() {",
            "  return (() => {",
            "    let $jscomp$destructuring$var0 = foo();",
            "    var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "    var $jscomp$destructuring$var2 = Object.assign({}, $jscomp$destructuring$var1);",
            "    a = $jscomp$destructuring$var1.x;",
            "    rest = (delete $jscomp$destructuring$var2.x,",
            "            $jscomp$destructuring$var2);",
            "    return $jscomp$destructuring$var0",
            "  })();",
            "}"));
  }

  @Test
  public void testObjectPatternWithRestParamList() {
    test(
        "function f({x = a(), ...rest}, y=b()) { console.log(y); }",
        lines(
            "function f($jscomp$destructuring$var0,y) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "  var $jscomp$destructuring$var2 = Object.assign({},$jscomp$destructuring$var1);",
            "  var x = $jscomp$destructuring$var1.x===undefined",
            "      ? a() : $jscomp$destructuring$var1.x;",
            "  var rest= (delete $jscomp$destructuring$var2.x,",
            "             $jscomp$destructuring$var2);",
            "  y = y===undefined ? b() : y;",
            "  console.log(y)",
            "}"));

    test(
        "function f({x = a(), ...rest}={}, y=b()) { console.log(y); }",
        lines(
            "function f($jscomp$destructuring$var0,y) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0===undefined",
            "      ? {} : $jscomp$destructuring$var0;",
            "  var $jscomp$destructuring$var2 = Object.assign({},$jscomp$destructuring$var1);",
            "  var x = $jscomp$destructuring$var1.x===undefined",
            "      ? a() : $jscomp$destructuring$var1.x;",
            "  var rest= (delete $jscomp$destructuring$var2.x,",
            "             $jscomp$destructuring$var2);",
            "  y = y===undefined ? b() : y;",
            "  console.log(y)",
            "}"));
  }

  @Test
  public void testObjectPatternWithRestArrowParamList() {
    test(
        "var f = ({x = a(), ...rest}, y=b()) => { console.log(y); };",
        lines(
            "var f = ($jscomp$destructuring$var0,y) => {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "  var $jscomp$destructuring$var2 = Object.assign({},$jscomp$destructuring$var1);",
            "  var x = $jscomp$destructuring$var1.x===undefined",
            "      ? a() : $jscomp$destructuring$var1.x;",
            "  var rest = (delete $jscomp$destructuring$var2.x,",
            "              $jscomp$destructuring$var2);",
            "  y = y===undefined ? b() : y;",
            "  console.log(y)",
            "}"));

    test(
        "var f = ({x = a(), ...rest}={}, y=b()) => { console.log(y); };",
        lines(
            "var f = ($jscomp$destructuring$var0,y) => {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0===undefined",
            "      ? {} : $jscomp$destructuring$var0;",
            "  var $jscomp$destructuring$var2 = Object.assign({},$jscomp$destructuring$var1);",
            "  var x = $jscomp$destructuring$var1.x===undefined",
            "      ? a() : $jscomp$destructuring$var1.x;",
            "  var rest= (delete $jscomp$destructuring$var2.x,",
            "             $jscomp$destructuring$var2);",
            "  y = y===undefined ? b() : y;",
            "  console.log(y)",
            "}"));
  }

  @Test
  public void testAllRewriteMode() {
    this.destructuringRewriteMode = ObjectDestructuringRewriteMode.REWRITE_ALL_OBJECT_PATTERNS;

    test(
        "var {a} = foo();",
        lines("var $jscomp$destructuring$var0 = foo();", "var a = $jscomp$destructuring$var0.a;"));

    test(
        "var {a} = foo(); var {...b} = bar();",
        lines(
            "var $jscomp$destructuring$var0 = foo();",
            "var a = $jscomp$destructuring$var0.a;",
            "var $jscomp$destructuring$var1 = bar();",
            "var $jscomp$destructuring$var2 = Object.assign({}, $jscomp$destructuring$var1);",
            "var b = $jscomp$destructuring$var2;"));

    test(
        "var {[foo0()]: {[foo1()]: a, ...r}, [foo2()]: { [foo3()]: b}, [foo4()]: c } = bar();",
        lines(
            "var $jscomp$destructuring$var0 = bar();",
            "var $jscomp$destructuring$var1 = $jscomp$destructuring$var0[foo0()];",
            "var $jscomp$destructuring$var2 = Object.assign({},$jscomp$destructuring$var1);",
            "var $jscomp$destructuring$var3 = foo1();",
            "var a = $jscomp$destructuring$var1[$jscomp$destructuring$var3];",
            "var r = (delete $jscomp$destructuring$var2[$jscomp$destructuring$var3],",
            "         $jscomp$destructuring$var2);",
            "var $jscomp$destructuring$var4 = $jscomp$destructuring$var0[foo2()];",
            "var b = $jscomp$destructuring$var4[foo3()];",
            "var c = $jscomp$destructuring$var0[foo4()]"));

    test(
        "var [a] = foo(); var [{b}] = foo(); var [{...c}] = foo();",
        lines(
            // var [a] = foo();
            "var $jscomp$destructuring$var0 = $jscomp.makeIterator(foo());",
            "var a = $jscomp$destructuring$var0.next().value;",
            // var [{b}] = foo();
            "var $jscomp$destructuring$var1 = $jscomp.makeIterator(foo());",
            "var $jscomp$destructuring$var2 = $jscomp$destructuring$var1.next().value;",
            "var b = $jscomp$destructuring$var2.b;",
            // var [{...c}] = foo();
            "var $jscomp$destructuring$var3 = $jscomp.makeIterator(foo());",
            "var $jscomp$destructuring$var4 = $jscomp$destructuring$var3.next().value;",
            "var $jscomp$destructuring$var5 = Object.assign({},$jscomp$destructuring$var4);",
            "var c = $jscomp$destructuring$var5"));
  }

  @Test
  public void testOnlyRestRewriteMode() {
    this.destructuringRewriteMode = ObjectDestructuringRewriteMode.REWRITE_OBJECT_REST;

    test("var {a} = foo();", "var {a} = foo();");

    test(
        "var {a} = foo(); var {...b} = bar();",
        lines(
            "var {a} = foo();",
            "var $jscomp$destructuring$var0 = bar();",
            "var $jscomp$destructuring$var1 = Object.assign({}, $jscomp$destructuring$var0);",
            "var b = $jscomp$destructuring$var1;"));

    // test that object patterns are rewritten if they have a rest property nested within
    test(
        "var {[foo0()]: {[foo1()]: a, ...r}, [foo2()]: { [foo3()]: b}, [foo4()]: c } = bar();",
        lines(
            "var $jscomp$destructuring$var0 = bar();",
            "var $jscomp$destructuring$var1 = $jscomp$destructuring$var0[foo0()];",
            "var $jscomp$destructuring$var2 = Object.assign({},$jscomp$destructuring$var1);",
            "var $jscomp$destructuring$var3 = foo1();",
            "var a = $jscomp$destructuring$var1[$jscomp$destructuring$var3];",
            "var r = (delete $jscomp$destructuring$var2[$jscomp$destructuring$var3],",
            "         $jscomp$destructuring$var2);",
            "var $jscomp$destructuring$var4 = $jscomp$destructuring$var0[foo2()];",
            "var b = $jscomp$destructuring$var4[foo3()];",
            "var c = $jscomp$destructuring$var0[foo4()]"));

    test(
        "var [a] = foo(); var [{b}] = foo(); var [{...c}] = foo();",
        lines(
            // var [a] = foo();
            "var [a] = foo();",
            // var [{b}] = foo();
            "var [{b}] = foo();",
            // var [{...c}] = foo();
            "var $jscomp$destructuring$var0 = $jscomp.makeIterator(foo());",
            "var $jscomp$destructuring$var1 = $jscomp$destructuring$var0.next().value;",
            "var $jscomp$destructuring$var2 = Object.assign({},$jscomp$destructuring$var1);",
            "var c = $jscomp$destructuring$var2"));
  }

  @Test
  public void testArrayDestructuring_getsCorrectTypes() {
    // inject libraries to get the correct $jscomp.makeIterator type
    useNoninjectingCompiler = false;
    ensureLibraryInjected("es6/util/makeiterator");
    disableCompareSyntheticCode();
    allowExternsChanges();

    test(
        externs(DEFAULT_EXTERNS),
        srcs(
            lines(
                "function takesIterable(/** !Iterable<number> */ iterable) {", //
                "  const [a] = iterable;",
                "}")),
        expected(
            lines(
                "function takesIterable(/** !Iterable<number> */ iterable) {", //
                "  var $jscomp$destructuring$var0 = $jscomp.makeIterator(iterable);",
                "  const a = $jscomp$destructuring$var0.next().value;",
                "}")));

    Compiler lastCompiler = getLastCompiler();
    JSTypeRegistry registry = lastCompiler.getTypeRegistry();

    // `$jscomp$destructuring$var0` is an Iterator<number>
    Node jscompDestructuringVar0 =
        getNodeMatchingQName(lastCompiler.getJsRoot(), "$jscomp$destructuring$var0");
    assertType(jscompDestructuringVar0.getJSType())
        .isEqualTo(
            registry.createTemplatizedType(
                registry.getNativeObjectType(JSTypeNative.ITERATOR_TYPE),
                registry.getNativeType(JSTypeNative.NUMBER_TYPE)));

    // `a` is a number
    Node aName = getNodeMatchingQName(lastCompiler.getJsRoot(), "a");
    assertType(aName.getJSType()).isEqualTo(registry.getNativeType(JSTypeNative.NUMBER_TYPE));

    // `$jscomp$destructuring$var0.next().value` is a number
    Node destructuringVarNextDotValue = aName.getOnlyChild();
    assertType(destructuringVarNextDotValue.getJSType())
        .isEqualTo(registry.getNativeType(JSTypeNative.NUMBER_TYPE));

    // `$jscomp$destructuring$var0.next()` is an IIterableResult<number>
    Node destructuringVarNextCall = destructuringVarNextDotValue.getFirstChild();
    assertType(destructuringVarNextCall.getJSType()).toStringIsEqualTo("IIterableResult<number>");
  }

  @Test
  public void testArrayDestructuringRest_getsCorrectTypes() {
    // inject libraries to get the correct $jscomp.arrayFromIterator type
    useNoninjectingCompiler = false;
    ensureLibraryInjected("es6/util/arrayfromiterator");
    ensureLibraryInjected("es6/util/makeiterator");
    disableCompareSyntheticCode();
    allowExternsChanges();

    test(
        externs(DEFAULT_EXTERNS),
        srcs(
            lines(
                "function takesIterable(/** !Iterable<number> */ iterable) {", //
                "  const [a, ...rest] = iterable;",
                "}")),
        expected(
            lines(
                "function takesIterable(/** !Iterable<number> */ iterable) {", //
                "  var $jscomp$destructuring$var0 = $jscomp.makeIterator(iterable);",
                "  const a = $jscomp$destructuring$var0.next().value;",
                "  const rest = $jscomp.arrayFromIterator($jscomp$destructuring$var0);",
                "}")));

    Compiler lastCompiler = getLastCompiler();
    JSTypeRegistry registry = lastCompiler.getTypeRegistry();

    Node jscompArrayFromIterator =
        getNodeMatchingQName(lastCompiler.getJsRoot(), "$jscomp.arrayFromIterator");
    assertType(jscompArrayFromIterator.getJSType())
        .toStringIsEqualTo("function(Iterator<number>): Array<number>");

    JSType arrayOfNumber =
        registry.createTemplatizedType(
            registry.getNativeObjectType(JSTypeNative.ARRAY_TYPE),
            registry.getNativeType(JSTypeNative.NUMBER_TYPE));

    Node jscompArrayFromIteratorCall = jscompArrayFromIterator.getParent();
    assertType(jscompArrayFromIteratorCall.getJSType()).isEqualTo(arrayOfNumber);

    // `rest` is Array<number>
    Node restName = getNodeMatchingQName(lastCompiler.getJsRoot(), "rest");
    assertType(restName.getJSType()).isEqualTo(arrayOfNumber);
  }

  @Test
  public void testObjectDestructuring_getsCorrectTypes() {
    test(
        lines(
            "const obj = {a: 3, b: 'string', c: null};", //
            "const {a} = obj;"),
        lines(
            "const obj = {a: 3, b: 'string', c: null};",
            "/** @const */ var $jscomp$destructuring$var0=obj;",
            "const a = $jscomp$destructuring$var0.a;"));

    JSTypeRegistry registry = getLastCompiler().getTypeRegistry();
    Node jsRoot = getLastCompiler().getJsRoot();

    Node aName = getNodeMatchingQName(jsRoot, "a");
    assertType(aName.getJSType()).isEqualTo(registry.getNativeType(JSTypeNative.NUMBER_TYPE));

    JSType objType = getNodeMatchingQName(jsRoot, "obj").getJSType();
    // `$jscomp$destructuring$var0` has the same type as `obj`
    Node jscompDestructuringVar0Name = getNodeMatchingQName(jsRoot, "$jscomp$destructuring$var0");
    assertType(jscompDestructuringVar0Name.getJSType()).isEqualTo(objType);
  }

  @Test
  public void testObjectDestructuringDefaultValue_getsCorrectTypes() {
    test(
        lines(
            "const obj = {a: 3, b: 'string', c: null};", //
            "const {a = 4} = obj;"),
        lines(
            "const obj = {a: 3, b: 'string', c: null};",
            "/** @const */ var $jscomp$destructuring$var0=obj;",
            "const a = $jscomp$destructuring$var0.a === undefined",
            "    ? 4: $jscomp$destructuring$var0.a;"));

    JSTypeRegistry registry = getLastCompiler().getTypeRegistry();
    Node jsRoot = getLastCompiler().getJsRoot();

    Node aName = getNodeMatchingQName(jsRoot, "a");
    assertType(aName.getJSType()).isEqualTo(registry.getNativeType(JSTypeNative.NUMBER_TYPE));

    JSType objType = getNodeMatchingQName(jsRoot, "obj").getJSType();

    // `$jscomp$destructuring$var0` has the same type as `obj`
    assertThat(
            getAllNodesMatchingQName(jsRoot, "$jscomp$destructuring$var0").stream()
                .map(node -> node.getJSType())
                .collect(Collectors.toSet()))
        .containsExactly(objType);
  }

  @Test
  public void testObjectDestructuringComputedPropWithDefault_getsCorrectTypes() {
    test(
        lines(
            "const /** !Object<string, number> */ obj = {['a']: 3};", //
            "const {['a']: a = 4} = obj;"),
        lines(
            "const /** !Object<string, number> */ obj = {['a']: 3};",
            "/** @const */ var $jscomp$destructuring$var0=obj;",
            "var $jscomp$destructuring$var1 = $jscomp$destructuring$var0['a'];",
            "const a = $jscomp$destructuring$var1 === undefined",
            "    ? 4: $jscomp$destructuring$var1;"));

    JSTypeRegistry registry = getLastCompiler().getTypeRegistry();
    Node jsRoot = getLastCompiler().getJsRoot();

    Node aName = getNodeMatchingQName(jsRoot, "a");
    assertType(aName.getJSType()).isEqualTo(registry.getNativeType(JSTypeNative.NUMBER_TYPE));

    // `$jscomp$destructuring$var0` has the same type as `obj`
    Node jscompDestructuringVar0Name = getNodeMatchingQName(jsRoot, "$jscomp$destructuring$var0");

    JSType objType = jscompDestructuringVar0Name.getOnlyChild().getJSType();
    assertType(objType).toStringIsEqualTo("Object<string,number>");
    assertType(jscompDestructuringVar0Name.getJSType()).isEqualTo(objType);

    // `$jscomp$destructuring$var1` is typed as `number` (this is probably less important!)
    Node jscompDestructuringVar1Name = getNodeMatchingQName(jsRoot, "$jscomp$destructuring$var0");
    assertType(jscompDestructuringVar1Name.getJSType()).isEqualTo(objType);
  }

  @Test
  public void testObjectDestructuringRest_getsCorrectTypes() {
    test(
        lines(
            "const obj = {a: 3, b: 'string', c: null};", //
            "const {...rest} = obj;"),
        lines(
            "const obj = {a: 3, b: 'string', c: null};",
            "/** @const */ var $jscomp$destructuring$var0=obj;",
            "var $jscomp$destructuring$var1 = Object.assign({}, $jscomp$destructuring$var0);",
            "const rest = $jscomp$destructuring$var1;"));

    JSTypeRegistry registry = getLastCompiler().getTypeRegistry();
    Node jsRoot = getLastCompiler().getJsRoot();

    JSType objType = getNodeMatchingQName(jsRoot, "obj").getJSType();

    // `$jscomp$destructuring$var0` has the same type as `obj`
    Node jscompDestructuringVar0Name = getNodeMatchingQName(jsRoot, "$jscomp$destructuring$var0");
    assertType(jscompDestructuringVar0Name.getJSType()).isEqualTo(objType);

    // `$jscomp$destructuring$var1` has the same type as `obj`
    Node jscompDestructuringVar1Name = getNodeMatchingQName(jsRoot, "$jscomp$destructuring$var1");
    assertType(jscompDestructuringVar1Name.getJSType()).isEqualTo(objType);

    // `rest` has the type `{a: number, b: string, c: null}`
    Node restName = getNodeMatchingQName(jsRoot, "rest");
    assertType(restName.getJSType())
        .isEqualTo(
            registry.createRecordType(
                ImmutableMap.of(
                    "a", registry.getNativeType(JSTypeNative.NUMBER_TYPE),
                    "b", registry.getNativeType(JSTypeNative.STRING_TYPE),
                    "c", registry.getNativeType(JSTypeNative.NULL_TYPE))));
    // TODO(lharker): should this be true? it's not because objType is a PrototypeObjectType, which
    // does not compare as structurally equal by default.
    assertType(restName.getJSType()).isNotEqualTo(objType);
  }

  /** Returns a list of all nodes in the given AST that matches the given qualified name */
  private ImmutableList<Node> getAllNodesMatchingQName(Node root, String qname) {
    ImmutableList.Builder<Node> builder = ImmutableList.builder();
    addAllNodesMatchingQNameHelper(root, qname, builder);
    return builder.build();
  }

  private void addAllNodesMatchingQNameHelper(
      Node root, String qname, ImmutableList.Builder<Node> nodesSoFar) {
    if (root.matchesQualifiedName(qname)) {
      nodesSoFar.add(root);
    }
    for (Node child : root.children()) {
      addAllNodesMatchingQNameHelper(child, qname, nodesSoFar);
    }
  }

  /** Returns the first node (preorder) in the given AST that matches the given qualified name */
  private Node getNodeMatchingQName(Node root, String qname) {
    if (root.matchesQualifiedName(qname)) {
      return root;
    }
    for (Node child : root.children()) {
      Node result = getNodeMatchingQName(child, qname);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  @Override
  protected Compiler createCompiler() {
    return useNoninjectingCompiler ? new NoninjectingCompiler() : new Compiler();
  }
}
