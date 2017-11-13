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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

public class Es6RewriteDestructuringTest extends TypeICompilerTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    this.mode = TypeInferenceMode.NEITHER;
    enableRunTypeCheckAfterProcessing();
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
    return new Es6RewriteDestructuring(compiler);
  }

  public void testObjectDestructuring() {
    test(
        "var {a: b, c: d} = foo();",
        lines(
            "var $jscomp$destructuring$var0 = foo();",
            "var b = $jscomp$destructuring$var0.a;",
            "var d = $jscomp$destructuring$var0.c;"));
    assertThat(getLastCompiler().injected).isEmpty();

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

  public void testObjectDestructuringNested() {
    test(
        "var {a: {b}} = foo();",
        lines(
            "var $jscomp$destructuring$var0 = foo();",
            "var $jscomp$destructuring$var1 = $jscomp$destructuring$var0.a;",
            "var b = $jscomp$destructuring$var1.b"));
  }

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
    assertThat(getLastCompiler().injected).containsExactly("es6/util/makeiterator");

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
        "var a; [[a] = ['b']] = [];",
        lines(
            "var a;",
            "var $jscomp$destructuring$var0 = $jscomp.makeIterator([]);",
            "var $jscomp$destructuring$var1 = $jscomp$destructuring$var0.next().value;",
            "var $jscomp$destructuring$var2 = $jscomp.makeIterator(",
            "    $jscomp$destructuring$var1 === undefined",
            "        ? ['b']",
            "        : $jscomp$destructuring$var1);",
            "a = $jscomp$destructuring$var2.next().value"));
  }

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

  public void testArrayDestructuringRest() {
    test(
        "let [one, ...others] = f();",
        lines(
            "var $jscomp$destructuring$var0 = $jscomp.makeIterator(f());",
            "let one = $jscomp$destructuring$var0.next().value;",
            "let others = $jscomp.arrayFromIterator($jscomp$destructuring$var0);"));
    assertThat(getLastCompiler().injected)
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

  public void testArrayDestructuringMixedRest() {
    test(
        "let [first, ...[re, st, ...{length: num_left}]] = f();",
        lines(
            "var $jscomp$destructuring$var0 = $jscomp.makeIterator(f());",
            "let first = $jscomp$destructuring$var0.next().value;",
            "var $jscomp$destructuring$var1 = "
                + "$jscomp.makeIterator("
                + "$jscomp.arrayFromIterator($jscomp$destructuring$var0));",
            "let re = $jscomp$destructuring$var1.next().value;",
            "let st = $jscomp$destructuring$var1.next().value;",
            "var $jscomp$destructuring$var2 = "
                + "$jscomp.arrayFromIterator($jscomp$destructuring$var1);",
            "let num_left = $jscomp$destructuring$var2.length;"));
  }

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

  public void testDefaultParameters() {
    this.mode = TypeInferenceMode.BOTH;
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
        warningOtiNti(TypeCheck.WRONG_ARGUMENT_COUNT, NewTypeInference.WRONG_ARGUMENT_COUNT));
  }

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

  public void testDefaultUndefinedParameters() {
    this.mode = TypeInferenceMode.BOTH;

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

  public void testCatch() {
    test(
        "try {} catch ({message}) {}",
        lines(
            "try {} catch($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "  let message = $jscomp$destructuring$var1.message",
            "}"));
  }

  public void testTypeCheck() {
    this.mode = TypeInferenceMode.BOTH;

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
        warningOtiNti(TypeValidator.TYPE_MISMATCH_WARNING, NewTypeInference.INVALID_ARGUMENT_TYPE));

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

  public void testDestructuringPatternInExterns() {
    this.mode = TypeInferenceMode.BOTH;
    allowExternsChanges();

    testSame(
        externs(
            lines(
                MINIMAL_EXTERNS,
                "/** @constructor */",
                "function Foo() {}",
                "",
                "Foo.prototype.bar = function({a}) {};")),
        srcs("(new Foo).bar({b: 0});"),
        warningOtiNti(TypeCheck.POSSIBLE_INEXISTENT_PROPERTY, null));
    // TODO(sdh): figure out what's going on here
  }

  public void testTypeCheck_inlineAnnotations() {
    this.mode = TypeInferenceMode.BOTH;

    test(
        "function f(/** {x: number} */ {x}) {}",
        lines(
            "function f(/** {x: number} */ $jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "  var x = $jscomp$destructuring$var1.x;",
            "}"));

    test(
        srcs(lines("function f(/** {x: number} */ {x}) {}", "f({ x: 'str'});")),
        warningOtiNti(TypeValidator.TYPE_MISMATCH_WARNING, NewTypeInference.INVALID_ARGUMENT_TYPE));
  }

  public void testDestructuringArrayNotInExprResult() {
    test(
        lines("var x, a, b;", "x = ([a,b] = [1,2])"),
        lines(
            "var x,a,b;",
            "x = (()=>{",
            "   let $jscomp$destructuring$var0 = [1,2];",
            "   var $jscomp$destructuring$var1 = $jscomp.makeIterator($jscomp$destructuring$var0);",
            "   a = $jscomp$destructuring$var1.next().value;",
            "   b = $jscomp$destructuring$var1.next().value;",
            "   return $jscomp$destructuring$var0;",
            "})();"));

    test(
        lines(
            "var foo = function () {", "var x, a, b;", "x = ([a,b] = [1,2]);", "}", "foo();"),
        lines(
            "var foo = function () {",
            " var x, a, b;",
            " x = (()=>{",
            "   let $jscomp$destructuring$var0 = [1,2];",
            "   var $jscomp$destructuring$var1 = $jscomp.makeIterator($jscomp$destructuring$var0);",
            "   a = $jscomp$destructuring$var1.next().value;",
            "   b = $jscomp$destructuring$var1.next().value;",
            "   return $jscomp$destructuring$var0;",
            " })();",
            "}",
            "foo();"));

    test(
        lines("var prefix;", "for (;;[, prefix] = /\\.?([^.]+)$/.exec(prefix)){", "}"),
        lines(
            "var prefix;",
            "for (;;(() => {",
            "   let $jscomp$destructuring$var0 = /\\.?([^.]+)$/.exec(prefix)",
            "   var $jscomp$destructuring$var1 = ",
            "$jscomp.makeIterator($jscomp$destructuring$var0);",
            "   $jscomp$destructuring$var1.next();",
            "   prefix = $jscomp$destructuring$var1.next().value;",
            "   return $jscomp$destructuring$var0;",
            " })()){",
            "}"));

    test(
        lines(
            "var prefix;",
            "for (;;[, prefix] = /\\.?([^.]+)$/.exec(prefix)){",
            "   console.log(prefix);",
            "}"),
        lines(
            "var prefix;",
            "for (;;(() => {",
            "   let $jscomp$destructuring$var0 = /\\.?([^.]+)$/.exec(prefix)",
            "   var $jscomp$destructuring$var1 = ",
            "$jscomp.makeIterator($jscomp$destructuring$var0);",
            "   $jscomp$destructuring$var1.next();",
            "   prefix = $jscomp$destructuring$var1.next().value;",
            "   return $jscomp$destructuring$var0;",
            " })()){",
            " console.log(prefix);",
            "}"));

    test(
        lines("for (var x = 1; x < 3; [x,] = [3,4]){", "   console.log(x);", "}"),
        lines(
            "for (var x = 1; x < 3; (()=>{",
            "   let $jscomp$destructuring$var0 = [3,4]",
            "   var $jscomp$destructuring$var1 = $jscomp.makeIterator($jscomp$destructuring$var0);",
            "   x = $jscomp$destructuring$var1.next().value;",
            "   return $jscomp$destructuring$var0;",
            " })()){",
            "console.log(x);",
            "}"));
  }

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

  public void testNestedDestructuring() {
    test(
        "var [[x]] = [[1]];",
        lines(
            "var $jscomp$destructuring$var0 = $jscomp.makeIterator([[1]]);",
            "var $jscomp$destructuring$var1 = ",
            "$jscomp.makeIterator($jscomp$destructuring$var0.next().value);",
            "var x = $jscomp$destructuring$var1.next().value;"));

    test(
        "var [[x,y],[z]] = [[1,2],[3]];",
        lines(
            "var $jscomp$destructuring$var0 = $jscomp.makeIterator([[1,2],[3]]);",
            "var $jscomp$destructuring$var1 = ",
            "$jscomp.makeIterator($jscomp$destructuring$var0.next().value);",
            "var x = $jscomp$destructuring$var1.next().value;",
            "var y = $jscomp$destructuring$var1.next().value;",
            "var $jscomp$destructuring$var2 = ",
            "$jscomp.makeIterator($jscomp$destructuring$var0.next().value);",
            "var z = $jscomp$destructuring$var2.next().value;"));

    test(
        "var [[x,y],z] = [[1,2],3];",
        lines(
            "var $jscomp$destructuring$var0 = $jscomp.makeIterator([[1,2],3]);",
            "var $jscomp$destructuring$var1 = ",
            "$jscomp.makeIterator($jscomp$destructuring$var0.next().value);",
            "var x = $jscomp$destructuring$var1.next().value;",
            "var y = $jscomp$destructuring$var1.next().value;",
            "var z = $jscomp$destructuring$var0.next().value;"));
  }

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

  @Override
  protected Compiler createCompiler() {
    return new NoninjectingCompiler();
  }

  @Override
  protected NoninjectingCompiler getLastCompiler() {
    return (NoninjectingCompiler) super.getLastCompiler();
  }
}
