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

/** Unit tests for {@link ConvertToTypedInterface}. */
public final class ConvertToTypedInterfaceTest extends Es6CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new ConvertToTypedInterface(compiler);
  }

  public void testInferAnnotatedTypeFromTypeInference() {
    enableTypeCheck();

    test("/** @const */ var x = 5;", "/** @const {number} */ var x;");

    test(
        "/** @constructor */ function Foo() { /** @const */ this.x = 5; }",
        "/** @constructor */ function Foo() {} \n /** @const {number} */ Foo.prototype.x;");
  }

  public void testSimpleConstJsdocPropagation() {
    test("/** @const */ var x = 5;", "/** @const {number} */ var x;");
    test("/** @const */ var x = true;", "/** @const {boolean} */ var x;");
    test("/** @const */ var x = 'str';", "/** @const {string} */ var x;");
    test("/** @const */ var x = null;", "/** @const {null} */ var x;");
    test("/** @const */ var x = void 0;", "/** @const {void} */ var x;");

    test(
        "/** @constructor */ function Foo() { /** @const */ this.x = 5; }",
        "/** @constructor */ function Foo() {} \n /** @const {number} */ Foo.prototype.x;");

    testError(
        "/** @const */ var x = cond ? true : 5;",
        ConvertToTypedInterface.CONSTANT_WITHOUT_EXPLICIT_TYPE);
  }

  public void testThisPropertiesInConstructors() {
    test(
        "/** @constructor */ function Foo() { /** @const {number} */ this.x; }",
        "/** @constructor */ function Foo() {} \n /** @const {number} */ Foo.prototype.x;");

    test(
        "/** @constructor */ function Foo() { this.x; }",
        "/** @constructor */ function Foo() {} \n /** @type {*} */ Foo.prototype.x;");

    test(
        "/** @constructor */ function Foo() { /** @type {?number} */ this.x = null; this.x = 5; }",
        "/** @constructor */ function Foo() {} \n /** @type {?number} */ Foo.prototype.x;");

    testError(
        "/** @constructor */ function Foo() { /** @const */ this.x = cond ? true : 5; }",
        ConvertToTypedInterface.CONSTANT_WITHOUT_EXPLICIT_TYPE);
  }

  public void testConstJsdocPropagationForNames() {
    test(
        "/** @type {!Array<string>} */ var x = []; /** @const */ var y = x;",
        "/** @type {!Array<string>} */ var x; /** @const {!Array<string>} */ var y;");

    test(
        LINE_JOINER.join(
            "/** @constructor */",
            "function Foo(/** number */ x) {",
            "  /** @const */ this.x = x;",
            "}"),
        LINE_JOINER.join(
            "/** @constructor */ function Foo(/** number */ x) {}",
            "/** @const {number} */ Foo.prototype.x;"));

    test(
        LINE_JOINER.join(
            "/** @constructor @param {!Array<string>} arr */",
            "function Foo(arr) {",
            "  /** @const */ this.arr = arr;",
            "}"),
        LINE_JOINER.join(
          "/** @constructor @param {!Array<string>} arr */ function Foo(arr) {}",
          "/** @const {!Array<string>} */ Foo.prototype.arr;"));

    testEs6(
        LINE_JOINER.join(
            "class Foo {",
            "  constructor(/** number */ x) {",
            "    /** @const */ this.x = x;",
            "  }",
            "}"),
        LINE_JOINER.join(
            "class Foo {",
            "  constructor(/** number */ x) {}",
            "}",
            "/** @const {number} */ Foo.prototype.x;"));

    testEs6(
        LINE_JOINER.join(
            "class Foo {",
            "  /** @param {number} x */",
            "  constructor(x) {",
            "    /** @const */ this.x = x;",
            "  }",
            "}"),
        LINE_JOINER.join(
            "class Foo {",
            "  /** @param {number} x */",
            "  constructor(x) {}",
            "}",
            "/** @const {number} */ Foo.prototype.x;"));
  }

  public void testConstWithDeclaredTypes() {
    test("/** @const @type {number} */ var n = compute();", "/** @const @type {number} */ var n;");
    test("/** @const {number} */ var n = compute();", "/** @const @type {number} */ var n;");
    test("/** @const @return {void} */ var f = compute();", "/** @const @return {void} */ var f;");
    test("/** @const @this {Array} */ var f = compute();", "/** @const @this {Array} x */ var f;");

    test(
        "/** @const @param {number} x */ var f = compute();",
        "/** @const @param {number} x */ var f;");

    test(
        "/** @const @constructor x */ var Foo = createConstructor();",
        "/** @const @constructor x */ var Foo;");
  }

  public void testRemoveUselessStatements() {
    test("34", "");
    test("'str'", "");
    test("({x:4})", "");
    test("debugger;", "");
    test("throw 'error';", "");
    test("label: debugger;", "");
  }

  public void testRemoveUnnecessaryBodies() {
    test("function f(x,y) { /** @type {number} */ z = x + y; return z; }", "function f(x,y) {}");

    test(
        "/** @return {number} */ function f(/** number */ x, /** number */ y) { return x + y; }",
        "/** @return {number} */ function f(/** number */ x, /** number */ y) {}");

    testEs6(
        "class Foo { method(/** string */ s) { return s.split(','); } }",
        "class Foo { method(/** string */ s) {} }");
  }

  public void testRemoveCalls() {
    test("alert('hello'); window.clearTimeout();", "");

    testSame("goog.provide('a.b.c');");

    testSame("goog.provide('a.b.c'); goog.require('x.y.z');");
  }

  public void testEnums() {
    test(
        "/** @const @enum {number} */ var E = { A: 1, B: 2, C: 3};",
        "/** @const @enum {number} */ var E = { A: 0, B: 0, C: 0};");

    test(
        "/** @enum {number} */ var E = { A: foo(), B: bar(), C: baz()};",
        "/** @enum {number} */ var E = { A: 0, B: 0, C: 0};");

    // NOTE: This pattern typechecks when found in externs, but not for code.
    // Since the goal of this pass is intended to be used as externs, this is acceptable.
    test(
        "/** @enum {string} */ var E = { A: 'hello', B: 'world'};",
        "/** @enum {string} */ var E = { A: 0, B: 0};");

    test(
        "/** @enum {Object} */ var E = { A: {b: 'c'}, D: {e: 'f'} };",
        "/** @enum {Object} */ var E = { A: 0, D: 0};");

    test(
        "var x = 7; /** @enum {number} */ var E = { A: x };",
        "/** @type {*} */ var x; /** @enum {number} */ var E = { A: 0 };");
  }

  public void testTryCatch() {
    test(
        "try { /** @type {number} */ var n = foo(); } catch (e) { console.log(e); }",
        "{ /** @type {number} */ var n; }");
  }

  public void testConstructorBodyWithThisDeclaration() {
    test(
        "/** @constructor */ function Foo() { /** @type {number} */ this.num = 5;}",
        "/** @constructor */ function Foo() {} /** @type {number} */ Foo.prototype.num;");

    test(
        "/** @constructor */ function Foo(b) { if (b) { /** @type {number} */ this.num = 5; } }",
        "/** @constructor */ function Foo(b) {} /** @type {number} */ Foo.prototype.num;");

    testEs6(
        "/** @constructor */ let Foo = function() { /** @type {number} */ this.num = 5;}",
        "/** @constructor */ let Foo = function() {}; /** @type {number} */ Foo.prototype.num;");

    testEs6(
        "class Foo { constructor() { /** @type {number} */ this.num = 5;} }",
        "class Foo { constructor() {} } /** @type {number} */ Foo.prototype.num;");
  }

  public void testConstructorBodyWithoutThisDeclaration() {
    test(
        "/** @constructor */ function Foo(o) { o.num = 8; var x = 'str'; }",
        "/** @constructor */ function Foo(o) {}");
  }

  public void testIIFE() {
    test("(function(){ /** @type {number} */ var n = 99; })();", "");
  }

  public void testConstants() {
    test("/** @const {number} */ var x = 5;", "/** @const {number} */ var x;");
  }

  public void testDefines() {
    // NOTE: This is another pattern that is only allowed in externs.
    test("/** @define {number} */ var x = 5;", "/** @define {number} */ var x;");
  }

  public void testIfs() {
    test(
        "if (true) { var /** number */ x = 5; }",
        "{/** @type {number} */ var x;}");

    test(
        "if (true) { var /** number */ x = 5; } else { var /** string */ y = 'str'; }",
        "{/** @type {number} */ var x;} {/** @type {string} */ var  y; }");

    test(
        "if (true) { if (false) { var /** number */ x = 5; } }",
        "{{/** @type {number} */ var x;}}");

    test(
        "if (true) {} else { if (false) {} else { var /** number */ x = 5; } }",
        "{}{{}{/** @type {number} */ var x;}}");
  }

  public void testLoops() {
    test("while (true) { foo(); break; }", "{}");

    test("for (var i = 0; i < 10; i++) { var field = 88; }",
        "/** @type {*} */ var i; {/** @type {*} */ var field;}");

    test(
        "while (i++ < 10) { var /** number */ field = i; }",
        "{ /** @type {number } */ var field; }");

    test(
        "do { var /** number */ field = i; } while (i++ < 10);",
        "{ /** @type {number } */ var field; }");

    test(
        "for (var /** number */ i = 0; i < 10; i++) { var /** number */ field = i; }",
        "/** @type {number} */ var i; { /** @type {number } */ var field; }");

    test(
        "for (i = 0; i < 10; i++) { var /** number */ field = i; }",
        "{ /** @type {number } */ var field; }");

    test(
        "for (var i = 0; i < 10; i++) { var /** number */ field = i; }",
        "/** @type {*} */ var i; { /** @type {number } */ var field; }");
  }

  public void testNamespaces() {
    testSame("/** @const */ var ns = {}; /** @return {number} */ ns.fun = function(x,y,z) {}");

    testSame("/** @const */ var ns = {}; ns.fun = function(x,y,z) {}");
  }

  public void testRemoveIgnoredProperties() {
    test(
        "/** @const */ var ns = {}; /** @return {number} */ ns['fun'] = function(x,y,z) {}",
        "/** @const */ var ns = {};");

    test(
        "/** @constructor */ function Foo() {} Foo.prototype['fun'] = function(x,y,z) {}",
        "/** @constructor */ function Foo() {}");
  }

  public void testRemoveRepeatedProperties() {
    test(
        "/** @const */ var ns = {}; /** @type {number} */ ns.x = 5; ns.x = 7;",
        "/** @const */ var ns = {}; /** @type {number} */ ns.x;");

    test(
        "/** @const */ var ns = {}; ns.x = 5; ns.x = 7;",
        "/** @const */ var ns = {}; /** @type {*} */ ns.x;");

    testEs6(
        "const ns = {}; /** @type {number} */ ns.x = 5; ns.x = 7;",
        "const ns = {}; /** @type {number} */ ns.x;");
  }

  public void testRemoveRepeatedDeclarations() {
    test("/** @type {number} */ var x = 4; var x = 7;", "/** @type {number} */ var x;");

    test("/** @type {number} */ var x = 4; x = 7;", "/** @type {number} */ var x;");

    test("var x = 4; var x = 7;", "/** @type {*} */ var x;");

    test("var x = 4; x = 7;", "/** @type {*} */ var x;");
  }
}
