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
import static com.google.javascript.jscomp.TypeValidator.TYPE_MISMATCH_WARNING;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

import java.util.Set;

public class Es6RewriteDestructuringTest extends CompilerTestCase {

  @Override
  public void setUp() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    disableTypeCheck();
    runTypeCheckAfterProcessing = true;
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
        LINE_JOINER.join(
            "var $jscomp$destructuring$var0 = foo();",
            "var b = $jscomp$destructuring$var0.a;",
            "var d = $jscomp$destructuring$var0.c;"));
    assertThat(getInjectedLibraries()).isEmpty();

    test(
        "var {a,b} = foo();",
        LINE_JOINER.join(
            "var $jscomp$destructuring$var0 = foo();",
            "var a = $jscomp$destructuring$var0.a;",
            "var b = $jscomp$destructuring$var0.b;"));

    test(
        "let {a,b} = foo();",
        LINE_JOINER.join(
            "var $jscomp$destructuring$var0 = foo();",
            "let a = $jscomp$destructuring$var0.a;",
            "let b = $jscomp$destructuring$var0.b;"));

    test(
        "const {a,b} = foo();",
        LINE_JOINER.join(
            "/** @const */ var $jscomp$destructuring$var0 = foo();",
            "const a = $jscomp$destructuring$var0.a;",
            "const b = $jscomp$destructuring$var0.b;"));

    test(
        "var x; ({a: x} = foo());",
        LINE_JOINER.join(
            "var x;",
            "var $jscomp$destructuring$var0 = foo();",
            "x = $jscomp$destructuring$var0.a;"));
  }

  public void testObjectDestructuringWithInitializer() {
    test(
        "var {a : b = 'default'} = foo();",
        LINE_JOINER.join(
            "var $jscomp$destructuring$var0 = foo();",
            "var b = ($jscomp$destructuring$var0.a === undefined) ?",
            "    'default' :",
            "    $jscomp$destructuring$var0.a"));

    test(
        "var {a = 'default'} = foo();",
        LINE_JOINER.join(
            "var $jscomp$destructuring$var0 = foo();",
            "var a = ($jscomp$destructuring$var0.a === undefined) ?",
            "    'default' :",
            "    $jscomp$destructuring$var0.a"));
  }

  public void testObjectDestructuringNested() {
    test(
        "var {a: {b}} = foo();",
        LINE_JOINER.join(
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
        LINE_JOINER.join(
            "var $jscomp$destructuring$var0 = {};",
            "var $jscomp$destructuring$var1 = $jscomp$destructuring$var0[foo()];",
            "var x = $jscomp$destructuring$var1 === undefined ?",
            "    5 : $jscomp$destructuring$var1"));

    test(
        "function f({['KEY']: x}) {}",
        LINE_JOINER.join(
            "function f($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0",
            "  var x = $jscomp$destructuring$var1['KEY']",
            "}"));
  }

  public void testObjectDestructuringStrangeProperties() {
    test(
        "var {5: b} = foo();",
        "var $jscomp$destructuring$var0 = foo(); var b = $jscomp$destructuring$var0['5']");

    test(
        "var {0.1: b} = foo();",
        LINE_JOINER.join(
            "var $jscomp$destructuring$var0 = foo();",
            "var b = $jscomp$destructuring$var0['0.1']"));

    test(
        "var {'str': b} = foo();",
        LINE_JOINER.join(
            "var $jscomp$destructuring$var0 = foo();",
            "var b = $jscomp$destructuring$var0['str']"));
  }

  public void testObjectDestructuringFunction() {
    test(
        "function f({a: b}) {}",
        LINE_JOINER.join(
            "function f($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0",
            "  var b = $jscomp$destructuring$var1.a",
            "}"));

    test(
        "function f({a}) {}",
        LINE_JOINER.join(
            "function f($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0",
            "  var a = $jscomp$destructuring$var1.a",
            "}"));

    test(
        "function f({k: {subkey : a}}) {}",
        LINE_JOINER.join(
            "function f($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0",
            "  var $jscomp$destructuring$var2 = $jscomp$destructuring$var1.k;",
            "  var a = $jscomp$destructuring$var2.subkey;",
            "}"));

    test(
        "function f({k: [x, y, z]}) {}",
        LINE_JOINER.join(
            "function f($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0",
            "  var $jscomp$destructuring$var2 =",
            "      $jscomp.makeIterator($jscomp$destructuring$var1.k);",
            "  var x = $jscomp$destructuring$var2.next().value;",
            "  var y = $jscomp$destructuring$var2.next().value;",
            "  var z = $jscomp$destructuring$var2.next().value;",
            "}"));
    assertThat(getInjectedLibraries()).containsExactly("es6_runtime");

    test(
        "function f({key: x = 5}) {}",
        LINE_JOINER.join(
            "function f($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0",
            "  var x = $jscomp$destructuring$var1.key === undefined ?",
            "      5 : $jscomp$destructuring$var1.key",
            "}"));

    test(
        "function f({[key]: x = 5}) {}",
        LINE_JOINER.join(
            "function f($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0",
            "  var $jscomp$destructuring$var2 = $jscomp$destructuring$var1[key]",
            "  var x = $jscomp$destructuring$var2 === undefined ?",
            "      5 : $jscomp$destructuring$var2",
            "}"));

    test(
        "function f({x = 5}) {}",
        LINE_JOINER.join(
            "function f($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0",
            "  var x = $jscomp$destructuring$var1.x === undefined ?",
            "      5 : $jscomp$destructuring$var1.x",
            "}"));
  }

  public void testObjectDestructuringFunctionJsDoc() {
    test(
        "function f(/** {x: number, y: number} */ {x, y}) {}",
        LINE_JOINER.join(
            "function f(/** {x: number, y: number} */ $jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "  var x = $jscomp$destructuring$var1.x;",
            "  var y = $jscomp$destructuring$var1.y;",
            "}"));
  }

  public void testDefaultParametersDestructuring() {
    test(
        "function f({a,b} = foo()) {}",
        LINE_JOINER.join(
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
        LINE_JOINER.join(
            "var $jscomp$destructuring$var0 = $jscomp.makeIterator(z());",
            "var x = $jscomp$destructuring$var0.next().value;",
            "var y = $jscomp$destructuring$var0.next().value;"));

    test(
        "var x,y;\n" + "[x,y] = z();",
        LINE_JOINER.join(
            "var x,y;",
            "var $jscomp$destructuring$var0 = $jscomp.makeIterator(z());",
            "x = $jscomp$destructuring$var0.next().value;",
            "y = $jscomp$destructuring$var0.next().value;"));

    test(
        "var [a,b] = c();" + "var [x,y] = z();",
        LINE_JOINER.join(
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
        LINE_JOINER.join(
            "var a;",
            "var $jscomp$destructuring$var0 = $jscomp.makeIterator(b())",
            "var $jscomp$destructuring$var1 = $jscomp$destructuring$var0.next().value",
            "a = ($jscomp$destructuring$var1 === undefined) ?",
            "    1 :",
            "    $jscomp$destructuring$var1;"));

    test(
        "var [a=1] = b();",
        LINE_JOINER.join(
            "var $jscomp$destructuring$var0 = $jscomp.makeIterator(b())",
            "var $jscomp$destructuring$var1 = $jscomp$destructuring$var0.next().value",
            "var a = ($jscomp$destructuring$var1 === undefined) ?",
            "    1 :",
            "    $jscomp$destructuring$var1;"));

    test(
        "var [a, b=1, c] = d();",
        LINE_JOINER.join(
            "var $jscomp$destructuring$var0=$jscomp.makeIterator(d());",
            "var a = $jscomp$destructuring$var0.next().value;",
            "var $jscomp$destructuring$var1 = $jscomp$destructuring$var0.next().value;",
            "var b = ($jscomp$destructuring$var1 === undefined) ?",
            "    1 :",
            "    $jscomp$destructuring$var1;",
            "var c=$jscomp$destructuring$var0.next().value"));

    test(
        "var a; [[a] = ['b']] = [];",
        LINE_JOINER.join(
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
        LINE_JOINER.join(
            "function f($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp.makeIterator($jscomp$destructuring$var0);",
            "  var x = $jscomp$destructuring$var1.next().value;",
            "  var y = $jscomp$destructuring$var1.next().value;",
            "  use(x);",
            "  use(y);",
            "}"));

    test(
        "function f([x, , y]) { use(x); use(y); }",
        LINE_JOINER.join(
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
        LINE_JOINER.join(
            "var $jscomp$destructuring$var0 = $jscomp.makeIterator(f());",
            "let one = $jscomp$destructuring$var0.next().value;",
            "let others = $jscomp.arrayFromIterator($jscomp$destructuring$var0);"));
    assertThat(getInjectedLibraries()).containsExactly("es6_runtime");

    test(
        "function f([first, ...rest]) {}",
        LINE_JOINER.join(
            "function f($jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp.makeIterator($jscomp$destructuring$var0);",
            "  var first = $jscomp$destructuring$var1.next().value;",
            "  var rest = $jscomp.arrayFromIterator($jscomp$destructuring$var1);",
            "}"));
  }

  public void testArrayDestructuringArguments() {
    test(
    "function f() { var [x, y] = arguments; }",
    LINE_JOINER.join(
        "function f() {",
        "  var $jscomp$destructuring$var0 = $jscomp.makeIterator(arguments);",
        "  var x = $jscomp$destructuring$var0.next().value;",
        "  var y = $jscomp$destructuring$var0.next().value;",
        "}"));
  }

  public void testMixedDestructuring() {
    test(
        "var [a,{b,c}] = foo();",
        LINE_JOINER.join(
            "var $jscomp$destructuring$var0 = $jscomp.makeIterator(foo());",
            "var a = $jscomp$destructuring$var0.next().value;",
            "var $jscomp$destructuring$var1 = $jscomp$destructuring$var0.next().value;",
            "var b = $jscomp$destructuring$var1.b;",
            "var c = $jscomp$destructuring$var1.c"));

    test(
        "var {a,b:[c,d]} = foo();",
        LINE_JOINER.join(
            "var $jscomp$destructuring$var0 = foo();",
            "var a = $jscomp$destructuring$var0.a;",
            "var $jscomp$destructuring$var1 = $jscomp.makeIterator($jscomp$destructuring$var0.b);",
            "var c = $jscomp$destructuring$var1.next().value;",
            "var d = $jscomp$destructuring$var1.next().value"));
  }

  public void testDestructuringForOf() {
    test(
        "for ({x} of y) { console.log(x); }",
        LINE_JOINER.join(
            "for (var $jscomp$destructuring$var0 of y) {",
            "   var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "   x = $jscomp$destructuring$var1.x;",
            "   console.log(x);",
            "}"));
  }

  public void testDefaultValueInObjectPattern() {
    test(
        "function f({x = a()}, y = b()) {}",
        LINE_JOINER.join(
            "function f($jscomp$destructuring$var0, y) {",
            "var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "var x = $jscomp$destructuring$var1.x === undefined",
            "       ? a() : $jscomp$destructuring$var1.x;",
            "y = y === undefined ? b() : y",
            "}"));
  }

  public void testDefaultParameters() {
    enableTypeCheck();
    test(
        "function f(zero, one = 1, two = 2) {}; f(1); f(1,2,3);",
        LINE_JOINER.join(
            "function f(zero, one, two) {",
            "  one = (one === undefined) ? 1 : one;",
            "  two = (two === undefined) ? 2 : two;",
            "};",
            "f(1); f(1,2,3);"));

    test(
        "function f(zero, one = 1, two = 2) {}; f();",
        LINE_JOINER.join(
            "function f(zero, one, two) {",
            "  one = (one === undefined) ? 1 : one;",
            "  two = (two === undefined) ? 2 : two;",
            "}; f();"),
        null,
        TypeCheck.WRONG_ARGUMENT_COUNT);
  }

  public void testDefaultAndRestParameters() {
    test(
        "function f(zero, one = 1, ...two) {}",
        LINE_JOINER.join(
            "function f(zero, one, ...two) {",
            "  one = (one === undefined) ? 1 : one;",
            "}"));

    test(
        "function f(/** number= */ x = 5) {}",
        LINE_JOINER.join(
            "function f(/** number= */ x) {",
            "  x = (x === undefined) ? 5 : x;",
            "}"));
  }

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

  public void testTypeCheck() {
    enableTypeCheck();

    test(
        "/** @param {{x: number}} obj */ function f({x}) {}",
        LINE_JOINER.join(
            "/** @param {{x: number}} obj */",
            "function f(obj) {",
            "  var $jscomp$destructuring$var0 = obj;",
            "  var x = $jscomp$destructuring$var0.x;",
            "}"));

    testWarning(
        LINE_JOINER.join(
            "/** @param {{x: number}} obj */",
            "function f({x}) {}",
          "f({ x: 'str'});"),
        TYPE_MISMATCH_WARNING);

    test(
        LINE_JOINER.join(
            "/** @param {{x: number}} obj */",
            "var f = function({x}) {}"),
        LINE_JOINER.join(
            "/** @param {{x: number}} obj */",
            "var f = function(obj) {",
            "  var $jscomp$destructuring$var0 = obj;",
            "  var x = $jscomp$destructuring$var0.x;",
            "}"));

    test(
        LINE_JOINER.join(
            "/** @param {{x: number}} obj */",
            "f = function({x}) {}"),
        LINE_JOINER.join(
            "/** @param {{x: number}} obj */",
            "f = function(obj) {",
            "  var $jscomp$destructuring$var0 = obj;",
            "  var x = $jscomp$destructuring$var0.x;",
            "}"));

    test(
        LINE_JOINER.join(
            "/** @param {{x: number}} obj */",
            "ns.f = function({x}) {}"),
        LINE_JOINER.join(
            "/** @param {{x: number}} obj */",
            "ns.f = function(obj) {",
            "  var $jscomp$destructuring$var0 = obj;",
            "  var x = $jscomp$destructuring$var0.x;",
            "}"));
  }

  public void testTypeCheck_inlineAnnotations() {
    enableTypeCheck();

    test(
        "function f(/** {x: number} */ {x}) {}",
        LINE_JOINER.join(
            "function f(/** {x: number} */ $jscomp$destructuring$var0) {",
            "  var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;",
            "  var x = $jscomp$destructuring$var1.x;",
            "}"));

    testWarning(
        LINE_JOINER.join(
            "function f(/** {x: number} */ {x}) {}",
            "f({ x: 'str'});"),
        TYPE_MISMATCH_WARNING);
  }

  @Override
  protected Compiler createCompiler() {
    return new NoninjectingCompiler();
  }

  protected Set<String> getInjectedLibraries() {
    return ((NoninjectingCompiler) getLastCompiler()).injected;
  }
}
