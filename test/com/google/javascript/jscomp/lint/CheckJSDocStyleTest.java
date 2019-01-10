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
package com.google.javascript.jscomp.lint;

import static com.google.javascript.jscomp.lint.CheckJSDocStyle.CLASS_DISALLOWED_JSDOC;
import static com.google.javascript.jscomp.lint.CheckJSDocStyle.EXTERNS_FILES_SHOULD_BE_ANNOTATED;
import static com.google.javascript.jscomp.lint.CheckJSDocStyle.INCORRECT_PARAM_NAME;
import static com.google.javascript.jscomp.lint.CheckJSDocStyle.MISSING_JSDOC;
import static com.google.javascript.jscomp.lint.CheckJSDocStyle.MISSING_PARAMETER_JSDOC;
import static com.google.javascript.jscomp.lint.CheckJSDocStyle.MISSING_RETURN_JSDOC;
import static com.google.javascript.jscomp.lint.CheckJSDocStyle.MIXED_PARAM_JSDOC_STYLES;
import static com.google.javascript.jscomp.lint.CheckJSDocStyle.MUST_BE_PRIVATE;
import static com.google.javascript.jscomp.lint.CheckJSDocStyle.MUST_HAVE_TRAILING_UNDERSCORE;
import static com.google.javascript.jscomp.lint.CheckJSDocStyle.OPTIONAL_PARAM_NOT_MARKED_OPTIONAL;
import static com.google.javascript.jscomp.lint.CheckJSDocStyle.PREFER_BACKTICKS_TO_AT_SIGN_CODE;
import static com.google.javascript.jscomp.lint.CheckJSDocStyle.WRONG_NUMBER_OF_PARAMS;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.ClosureCodingConvention;
import com.google.javascript.jscomp.CodingConvention;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.GoogleCodingConvention;
import com.google.javascript.jscomp.parsing.Config;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test case for {@link CheckJSDocStyle}. */
@RunWith(JUnit4.class)
public final class CheckJSDocStyleTest extends CompilerTestCase {
  public CheckJSDocStyleTest() {
    super("/** @fileoverview\n * @externs\n */");
  }

  private CodingConvention codingConvention;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    codingConvention = new GoogleCodingConvention();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_NEXT);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckJSDocStyle(compiler);
  }

  @Override
  protected CompilerOptions getOptions(CompilerOptions options) {
    super.getOptions(options);
    options.setParseJsDocDocumentation(Config.JsDocParsing.INCLUDE_DESCRIPTIONS_NO_WHITESPACE);
    options.setWarningLevel(CheckJSDocStyle.ALL_DIAGNOSTICS, CheckLevel.WARNING);
    return options;
  }

  @Override
  protected CodingConvention getCodingConvention() {
    return codingConvention;
  }

  @Test
  public void testValidSuppress_onDeclaration() {
    testSame("/** @const */ var global = this;");
    testSame("/** @const */ goog.global = this;");
  }

  @Test
  public void testValidSuppress_withES6Modules01() {
    testSame("export /** @suppress {missingRequire} */ var x = new y.Z();");
  }

  @Test
  public void testValidSuppress_withES6Modules03() {
    testSame("export /** @const @suppress {duplicate} */ var google = {};");
  }

  @Test
  public void testExtraneousClassAnnotations() {
    testWarning(
        lines(
            "/**",
            " * @constructor",
            " */",
            "var X = class {};"),
        CLASS_DISALLOWED_JSDOC);

    testWarning(
        lines(
            "/**",
            " * @constructor",
            " */",
            "class X {};"),
        CLASS_DISALLOWED_JSDOC);

    // TODO(tbreisacher): Warn for @extends too. We need to distinguish between cases like this
    // which are totally redundant...
    testSame(
        lines(
            "/**",
            " * @extends {Y}",
            " */",
            "class X extends Y {};"));

    // ... and ones like this which are not.
    testSame(
        lines(
            "/**",
            " * @extends {Y<number>}",
            " */",
            "class X extends Y {};"));

    testSame(
        lines(
            "/**",
            " * @implements {Z}",
            " */",
            "class X extends Y {};"));

    testSame(
        lines(
            "/**",
            " * @interface",
            " * @extends {Y}",
            " */",
            "class X extends Y {};"));

    testSame(
        lines(
            "/**",
            " * @record",
            " * @extends {Y}",
            " */",
            "class X extends Y {};"));
  }

  @Test
  public void testInvalidExtraneousClassAnnotations_withES6Modules() {
    testWarning(
        lines(
            "export",
            "/**",
            " * @constructor",
            " */",
            "var X = class {};"),
        CLASS_DISALLOWED_JSDOC);
  }

  @Test
  public void testValidExtraneousClassAnnotations_withES6Modules() {
    testSame("export /** @extends {Y} */ class X extends Y {};");
  }

  @Test
  public void testNestedArrowFunctions() {
    testSame(
        lines(
            "/**",
            " * @param {Object} a",
            " * @return {function(Object): boolean}",
            " */",
            "var haskellStyleEquals = a => b => a == b;"));
  }

  @Test
  public void testNestedArrowFunctions_withES6Modules() {
    testSame(
        lines(
            "export",
            "/**",
            " * @param {Object} a",
            " * @return {function(Object): boolean}",
            " */",
            "var haskellStyleEquals = a => b => a == b;"));
  }

  @Test
  public void testGetterSetterMissingJsDoc() {
    testWarning("class Foo { get twentyone() { return 21; } }", MISSING_JSDOC);
    testWarning("class Foo { set someString(s) { this.someString_ = s; } }", MISSING_JSDOC);

    testSame("class Foo { /** @return {number} */ get twentyone() { return 21; } }");
    testSame("class Foo { /** @param {string} s */ set someString(s) { this.someString_ = s; } }");
  }

  @Test
  public void testGetterSetter_withES6Modules() {
    testSame("export class Foo { /** @return {number} */ get twentyone() { return 21; } }");
  }

  @Test
  public void testMissingJsDoc() {
    testWarning("function f() {}", MISSING_JSDOC);
    testWarning("var f = function() {}", MISSING_JSDOC);
    testWarning("let f = function() {}", MISSING_JSDOC);
    testWarning("const f = function() {}", MISSING_JSDOC);
    testWarning("foo.bar = function() {}", MISSING_JSDOC);
    testWarning("Foo.prototype.bar = function() {}", MISSING_JSDOC);
    testWarning("class Foo { bar() {} }", MISSING_JSDOC);
    testWarning("class Foo { constructor(x) {} }", MISSING_JSDOC);
    testWarning("var Foo = class { bar() {} };", MISSING_JSDOC);
    testWarning("if (COMPILED) { var f = function() {}; }", MISSING_JSDOC);
    testWarning("var f = async function() {};", MISSING_JSDOC);
    testWarning("async function f() {};", MISSING_JSDOC);
    testWarning("Polymer({ method() {} });", MISSING_JSDOC);
    testWarning("Polymer({ method: function() {} });", MISSING_JSDOC);

    testSame("/** @return {string} */ function f() {}");
    testSame("/** @return {string} */ var f = function() {}");
    testSame("/** @return {string} */ let f = function() {}");
    testSame("/** @return {string} */ const f = function() {}");
    testSame("/** @return {string} */ foo.bar = function() {}");
    testSame("/** @return {string} */ Foo.prototype.bar = function() {}");
    testSame("class Foo { /** @return {string} */ bar() {} }");
    testSame("class Foo { constructor(/** string */ x) {} }");
    testSame("var Foo = class { /** @return {string} */ bar() {} };");
    testSame("/** @param {string} s */ var f = async function(s) {};");
    testSame("/** @param {string} s */ async function f(s) {};");
    testSame("Polymer({ /** @return {null} */ method() {} });");
    testSame("Polymer({ /** @return {null} */ method: function() {} });");
  }

  @Test
  public void testMissingJsDoc_withES6Modules01() {
    testWarning("export function f() {}", MISSING_JSDOC);
  }

  @Test
  public void testMissingJsDoc_withES6Modules02() {
    testWarning("export var f = function() {}", MISSING_JSDOC);
  }

  @Test
  public void testMissingJsDoc_withES6Modules03() {
    testWarning("export let f = function() {}", MISSING_JSDOC);
  }

  @Test
  public void testMissingJsDoc_withES6Modules04() {
    testWarning("export const f = function() {}", MISSING_JSDOC);
  }

  @Test
  public void testMissingJsDoc_withES6Modules09() {
    testWarning("export var f = async function() {};", MISSING_JSDOC);
  }

  @Test
  public void testMissingJsDoc_noWarningIfInlineJsDocIsPresent() {
    testSame("function /** string */ f() {}");
    testSame("function f(/** string */ x) {}");
    testSame("var f = function(/** string */ x) {}");
    testSame("let f = function(/** string */ x) {}");
    testSame("const f = function(/** string */ x) {}");
    testSame("foo.bar = function(/** string */ x) {}");
    testSame("Foo.prototype.bar = function(/** string */ x) {}");
    testSame("class Foo { bar(/** string */ x) {} }");
    testSame("var Foo = class { bar(/** string */ x) {} };");
  }

  @Test
  public void testMissingJsDoc_noWarningIfInlineJsDocIsPresent_withES6Modules() {
    testSame("export function /** string */ f() {}");
  }

  @Test
  public void testMissingJsDoc_noWarningIfNotTopLevel() {
    testSame(inIIFE("function f() {}"));
    testSame(inIIFE("var f = function() {}"));
    testSame(inIIFE("let f = function() {}"));
    testSame(inIIFE("const f = function() {}"));
    testSame(inIIFE("foo.bar = function() {}"));
    testSame(inIIFE("class Foo { bar() {} }"));
    testSame(inIIFE("var Foo = class { bar() {} };"));

    testSame("myArray.forEach(function(elem) { alert(elem); });");

    testSame(lines(
        "Polymer({",
        "  is: 'example-elem',",
        "  /** @return {null} */",
        "  someMethod: function() {},",
        "});"));

    testSame(lines(
        "Polymer({",
        "  is: 'example-elem',",
        "  /** @return {null} */",
        "  someMethod() {},",
        "});"));
  }

  @Test
  public void testMissingJsDoc_noWarningIfNotTopLevelAndNoParams() {
    testSame(lines(
        "describe('a karma test', function() {",
        "  /** @ngInject */",
        "  var helperFunction = function($compile, $rootScope) {};",
        "})"));
  }

  @Test
  public void testMissingJsDoc_noWarningOnTestFunctions() {
    testSame("function testSomeFunctionality() {}");
    testSame("var testSomeFunctionality = function() {};");
    testSame("let testSomeFunctionality = function() {};");
    testSame("window.testSomeFunctionality = function() {};");
    testSame("const testSomeFunctionality = function() {};");

    testSame("function setUp() {}");
    testSame("function tearDown() {}");
    testSame("var setUp = function() {};");
    testSame("var tearDown = function() {};");
  }

  @Test
  public void testMissingJsDoc_noWarningOnTestFunctions_withES6Modules() {
    testSame("export function testSomeFunctionality() {}");
  }

  @Test
  public void testMissingJsDoc_noWarningOnEmptyConstructor() {
    testSame("class Foo { constructor() {} }");
  }

  @Test
  public void testMissingJsDoc_noWarningOnEmptyConstructor_withES6Modules() {
    testSame("export class Foo { constructor() {} }");
  }

  @Test
  public void testMissingJsDoc_googModule() {
    testWarning("goog.module('a.b.c'); function f() {}", MISSING_JSDOC);
    testWarning("goog.module('a.b.c'); var f = function() {};", MISSING_JSDOC);
  }

  @Test
  public void testMissingJsDoc_ES6Module01() {
    testWarning("export default abc; function f() {}", MISSING_JSDOC);
  }

  @Test
  public void testMissingJsDoc_ES6Module02() {
    testWarning("export default abc; var f = function() {};", MISSING_JSDOC);
  }

  @Test
  public void testMissingJsDoc_ES6Module03() {
    testWarning("export function f() {};", MISSING_JSDOC);
  }

  @Test
  public void testMissingJsDoc_ES6Module04() {
    testWarning("export default function () {}", MISSING_JSDOC);
  }

  @Test
  public void testMissingJsDoc_ES6Module05() {
    testWarning("export default (foo) => { alert(foo); }", MISSING_JSDOC);
  }

  @Test
  public void testMissingJsDoc_googModule_noWarning() {
    testSame("goog.module('a.b.c'); /** @type {function()} */ function f() {}");
    testSame("goog.module('a.b.c'); /** @type {function()} */ var f = function() {};");
  }

  @Test
  public void testMissingJsDoc_ES6Module_noWarning01() {
    testSame("export default abc; /** @type {function()} */ function f() {}");
  }

  @Test
  public void testMissingJsDoc_ES6Module_noWarning02() {
    testSame("export default abc; /** @type {function()} */ var f = function() {};");
  }

  private static String inIIFE(String js) {
    return "(function() {\n" + js + "\n})()";
  }

  @Test
  public void testMissingParam_noWarning() {
    testSame(lines(
        "/**",
        " * @param {string} x",
        " * @param {string} y",
        " */",
        "function f(x, y) {}"));

    testSame(lines(
        "/** @override */",
        "Foo.bar = function(x, y) {}"));

    testSame(lines(
        "/**",
        " * @param {string=} x",
        " */",
        "function f(x = 1) {}"));

    testSame(lines(
        "/**",
        " * @param {number=} x",
        " * @param {number=} y",
        " * @param {number=} z",
        " */",
        "function f(x = 1, y = 2, z = 3) {}"));

    testSame(lines(
        "/**",
        " * @param {...string} args",
        " */",
        "function f(...args) {}"));

    testSame(lines(
        "(function() {",
        "  myArray.forEach(function(elem) { alert(elem); });",
        "})();"));

    testSame(lines(
        "(function() {",
        "  myArray.forEach(elem => alert(elem));",
        "})();"));

    testSame("/** @type {function(number)} */ function f(x) {}");

    testSame("function f(/** string */ inlineArg) {}");
    testSame("/** @export */ function f(/** string */ inlineArg) {}");

    testSame("class Foo { constructor(/** string */ inlineArg) {} }");
    testSame("class Foo { method(/** string */ inlineArg) {} }");

    testSame("/** @export */ class Foo { constructor(/** string */ inlineArg) {} }");
    testSame("class Foo { /** @export */ method(/** string */ inlineArg) {} }");
  }

  @Test
  public void testMissingParam_noWarning_withES6Modules() {
    testSame("export class Foo { /** @export */ method(/** string */ inlineArg) {} }");
  }

  @Test
  public void testMissingParam() {
    testWarning(
        lines(
            "/**",
            " * @param {string} x",
            // No @param for y.
            " */",
            "function f(x, y) {}"),
        WRONG_NUMBER_OF_PARAMS);

    testWarning(
        lines(
            "/**",
            " * @param {string} x",
            " */",
            "function f(x = 1) {}"),
        OPTIONAL_PARAM_NOT_MARKED_OPTIONAL);

    testWarning(
        lines(
            "/**",
            " * @param {string} x",
            // No @param for y.
            " */",
            "function f(x, y = 1) {}"),
        WRONG_NUMBER_OF_PARAMS);

    testWarning("function f(/** string */ x, y) {}", MISSING_PARAMETER_JSDOC);
    testWarning("function f(x, /** string */ y) {}", MISSING_PARAMETER_JSDOC);
    testWarning("function /** string */ f(x) {}", MISSING_PARAMETER_JSDOC);

    testWarning(inIIFE("function f(/** string */ x, y) {}"), MISSING_PARAMETER_JSDOC);
    testWarning(inIIFE("function f(x, /** string */ y) {}"), MISSING_PARAMETER_JSDOC);
    testWarning(inIIFE("function /** string */ f(x) {}"), MISSING_PARAMETER_JSDOC);
  }

  @Test
  public void testMissingParam_withES6Modules01() {
    testWarning(
        lines(
            "export",
            "/**",
            " * @param {string} x",
            // No @param for y.
            " */",
            "function f(x, y) {}"),
        WRONG_NUMBER_OF_PARAMS);
  }

  @Test
  public void testMissingParam_withES6Modules02() {
    testWarning(
        "export /** @param {string} x */ function f(x = 1) {}",
        OPTIONAL_PARAM_NOT_MARKED_OPTIONAL);
  }

  @Test
  public void testMissingParam_withES6Modules03() {
    testWarning("export function f(/** string */ x, y) {}", MISSING_PARAMETER_JSDOC);
  }

  @Test
  public void testMissingParamWithDestructuringPattern() {
    testWarning(
        lines(
            "/**",
            " * @param {string} namedParam",
            " * @return {void}",
            " */",
            "function f(namedParam, {destructuring:pattern}) {",
            "}"),
        WRONG_NUMBER_OF_PARAMS);

    testWarning(
        lines(
            "/**",
            " * @param {string} namedParam",
            " * @return {void}",
            " */",
            "function f({destructuring:pattern}, namedParam) {",
            "}"),
        WRONG_NUMBER_OF_PARAMS);

    testWarning(
        lines(
            "/**",
            " * @param {string} namedParam",
            " * @return {void}",
            " */",
            "function f(namedParam, [pattern]) {",
            "}"),
        WRONG_NUMBER_OF_PARAMS);

    testWarning(
        lines(
            "/**",
            " * @param {string} namedParam",
            " * @return {void}",
            " */",
            "function f([pattern], namedParam) {",
            "}"),
        WRONG_NUMBER_OF_PARAMS);

    testWarning(
        lines(
            "/**",
            " * @param {{",
            " *   a: (string|undefined),",
            " *   b: (number|undefined),",
            " *   c: (boolean|undefined)",
            " * }} obj",
            " */",
            "function create({a = 'hello', b = 8, c = false} = {}) {}"),
        OPTIONAL_PARAM_NOT_MARKED_OPTIONAL);

    // Same as above except there's an '=' to indicate that it's optional.
    testSame(
        lines(
            "/**",
            " * @param {{",
            " *   a: (string|undefined),",
            " *   b: (number|undefined),",
            " *   c: (boolean|undefined)",
            " * }=} obj",
            " */",
            "function create({a = 'hello', b = 8, c = false} = {}) {}"));
  }

  @Test
  public void testInvalidMissingParamWithDestructuringPattern_withES6Modules01() {
    testWarning(
        lines(
            "export",
            "/**",
            " * @param {string} namedParam",
            " * @return {void}",
            " */",
            "function f(namedParam, {destructuring:pattern}) {",
            "}"),
        WRONG_NUMBER_OF_PARAMS);
  }

  @Test
  public void testInvalidMissingParamWithDestructuringPattern_withES6Modules02() {
    testWarning(
        lines(
            "export",
            "/**",
            " * @param {{",
            " *   a: (string|undefined),",
            " *   b: (number|undefined),",
            " *   c: (boolean|undefined)",
            " * }} obj",
            " */",
            "function create({a = 'hello', b = 8, c = false} = {}) {}"),
        OPTIONAL_PARAM_NOT_MARKED_OPTIONAL);
  }

  @Test
  public void testValidMissingParamWithDestructuringPattern_withES6Modules() {
    testSame(
        lines(
            "export",
            "/**",
            " * @param {{",
            " *   a: (string|undefined),",
            " *   b: (number|undefined),",
            " *   c: (boolean|undefined)",
            " * }=} obj",
            " */",
            "function create({a = 'hello', b = 8, c = false} = {}) {}"));
  }

  @Test
  public void testMissingParamWithDestructuringPatternWithDefault() {
    testWarning(
        lines(
            "/**",
            " * @param {string} namedParam",
            " * @return {void}",
            " */",
            "function f(namedParam, {destructuring:pattern} = defaultValue) {",
            "}"),
        WRONG_NUMBER_OF_PARAMS);

    testWarning(
        lines(
            "/**",
            " * @param {string} namedParam",
            " * @return {void}",
            " */",
            "function f(namedParam, [pattern] = defaultValue) {",
            "}"),
        WRONG_NUMBER_OF_PARAMS);
  }

  @Test
  public void testMissingParamWithDestructuringPatternWithDefault_withES6Modules() {
    testWarning(
        lines(
            "export",
            "/**",
            " * @param {string} namedParam",
            " * @return {void}",
            " */",
            "function f(namedParam, {destructuring:pattern} = defaultValue) {",
            "}"),
        WRONG_NUMBER_OF_PARAMS);
  }

  @Test
  public void testParamWithNoTypeInfo() {
    testSame(
        lines(
            "/**",
            " * @param x A param with no type information.",
            " */",
            "function f(x) { }"));
  }

  @Test
  public void testParamWithNoTypeInfo_withES6Modules() {
    testSame(
        lines(
            "export",
            "/**",
            " * @param x A param with no type information.",
            " */",
            "function f(x) { }"));
  }

  @Test
  public void testMissingPrivate_noWarningWithClosureConvention() {
    codingConvention = new ClosureCodingConvention();
    testSame(
        lines(
            "/**",
            " * @return {number}",
            " * @private",
            " */",
            "X.prototype.foo = function() { return 0; }"));
  }

  @Test
  public void testMissingPrivate() {
    testWarning(
        lines(
            "/** @return {number} */",
            "X.prototype.foo_ = function() { return 0; }"),
        MUST_BE_PRIVATE);

    testWarning(
        lines(
            "/** @type {?number} */",
            "X.prototype.foo_ = null;"),
        MUST_BE_PRIVATE);

    testWarning(
        lines(
            "/**",
            " * @return {number}",
            " * @private",
            " */",
            "X.prototype.foo = function() { return 0; }"),
        MUST_HAVE_TRAILING_UNDERSCORE);

    testWarning(
        lines(
            "/**",
            " * @type {number}",
            " * @private",
            " */",
            "X.prototype.foo = 0;"),
        MUST_HAVE_TRAILING_UNDERSCORE);

    testSame(
        lines(
            "/**",
            " * @return {number}",
            " * @private",
            " */",
            "X.prototype.foo_ = function() { return 0; }"));

    testSame(
        lines(
            "/**",
            " * @type {number}",
            " * @private",
            " */",
            "X.prototype.foo_ = 0;"));

    testSame(
        lines(
            "/** @type {number} */",
            "X.prototype['@some_special_property'] = 0;"));
  }

  @Test
  public void testMissingPrivate_class() {
    testWarning(
        lines(
            "class Example {",
            "  /** @return {number} */",
            "  foo_() { return 0; }",
            "}"),
        MUST_BE_PRIVATE);

    testWarning(
        lines(
            "class Example {",
            "  /** @return {number} */",
            "  get foo_() { return 0; }",
            "}"),
        MUST_BE_PRIVATE);

    testWarning(
        lines(
            "class Example {",
            "  /** @param {number} val */",
            "  set foo_(val) {}",
            "}"),
        MUST_BE_PRIVATE);

    testWarning(
        lines(
            "class Example {",
            "  /**",
            "   * @return {number}",
            "   * @private",
            "   */",
            "  foo() { return 0; }",
            "}"),
        MUST_HAVE_TRAILING_UNDERSCORE);

    testWarning(
        lines(
            "class Example {",
            "  /**",
            "   * @return {number}",
            "   * @private",
            "   */",
            "  get foo() { return 0; }",
            "}"),
        MUST_HAVE_TRAILING_UNDERSCORE);

    testWarning(
        lines(
            "class Example {",
            "  /**",
            "   * @param {number} val",
            "   * @private",
            "   */",
            "  set foo(val) { }",
            "}"),
        MUST_HAVE_TRAILING_UNDERSCORE);
  }

  @Test
  public void testMissingPrivate_class_withES6Modules01() {
    testWarning(
        "export class Example { /** @return {number} */ foo_() { return 0; } }",
        MUST_BE_PRIVATE);
  }

  @Test
  public void testMissingPrivate_class_withES6Modules02() {
    testWarning(
        lines(
            "export class Example {",
            "  /**",
            "   * @return {number}",
            "   * @private",
            "   */",
            "  foo() { return 0; }",
            "}"),
        MUST_HAVE_TRAILING_UNDERSCORE);
  }

  @Test
  public void testMissingPrivate_dontWarnOnObjectLiteral() {
    testSame(
        lines(
            "var obj = {",
            "  /** @return {number} */",
            "  foo_() { return 0; }",
            "}"));
  }

  @Test
  public void testMissingPrivate_dontWarnOnObjectLiteral_withES6Modules() {
    testSame("export var obj = { /** @return {number} */ foo_() { return 0; } }");
  }

  @Test
  public void testOptionalArgs() {
    testSame(
        lines(
            "/**",
            " * @param {number=} n",
            " */",
            "function f(n) {}"));

    testSame(
        lines(
            "/**",
            " * @param {number} opt_n",
            " */",
            "function f(opt_n) {}"),
        OPTIONAL_PARAM_NOT_MARKED_OPTIONAL);

    testSame(lines(
        "/**",
        " * @param {number=} opt_n",
        " */",
        "function f(opt_n) {}"));
  }

  @Test
  public void testValidOptionalArgs_withES6Modules() {
    testSame("export /** @param {number=} n */ function f(n) {}");
  }

  @Test
  public void testInvalidOptionalArgs_withES6Modules() {
    testSame(
        "export /** @param {number} opt_n */ function f(opt_n) {}",
        OPTIONAL_PARAM_NOT_MARKED_OPTIONAL);
  }

  @Test
  public void testParamsOutOfOrder() {
    testWarning(
        lines(
            "/**",
            " * @param {?} second",
            " * @param {?} first",
            " */",
            "function f(first, second) {}"),
        INCORRECT_PARAM_NAME);
  }

  @Test
  public void testParamsOutOfOrder_withES6Modules() {
    testWarning(
        lines(
            "export",
            "/**",
            " * @param {?} second",
            " * @param {?} first",
            " */",
            "function f(first, second) {}"),
        INCORRECT_PARAM_NAME);
  }

  @Test
  public void testMixedStyles() {
    testWarning(
        lines(
            "/**",
            " * @param {?} first",
            " * @param {string} second",
            " */",
            "function f(first, /** string */ second) {}"),
        MIXED_PARAM_JSDOC_STYLES);
  }

  @Test
  public void testMixedStyles_withES6Modules() {
    testWarning(
        lines(
            "export",
            "/**",
            " * @param {?} first",
            " * @param {string} second",
            " */",
            "function f(first, /** string */ second) {}"),
        MIXED_PARAM_JSDOC_STYLES);
  }

  @Test
  public void testDestructuring() {
    testSame(
        lines(
            "/**",
            " * @param {{x: number, y: number}} point",
            " */",
            "function getDistanceFromZero({x, y}) {}"));

    testSame("function getDistanceFromZero(/** {x: number, y: number} */ {x, y}) {}");
  }

  @Test
  public void testDestructuring_withES6Modules() {
    testSame("export function getDistanceFromZero(/** {x: number, y: number} */ {x, y}) {}");
  }

  @Test
  public void testMissingReturn_functionStatement_noWarning() {
    testSame("/** @param {number} x */ function f(x) {}");
    testSame("/** @constructor */ function f() {}");
    testSame("/** @param {number} x */ function f(x) { function bar() { return x; } }");
    testSame("/** @param {number} x */ function f(x) { return; }");
    testSame("/** @param {number} x\n * @return {number} */ function f(x) { return x; }");
    testSame("/** @param {number} x */ function /** number */ f(x) { return x; }");
    testSame("/** @inheritDoc */ function f(x) { return x; }");
    testSame("/** @override */ function f(x) { return x; }");
  }

  @Test
  public void testMissingReturn_functionStatement_noWarning_withES6Modules() {
    testSame("export /** @param {number} x */ function f(x) {}");
  }

  @Test
  public void testMissingReturn_assign_noWarning() {
    testSame("/** @param {number} x */ f = function(x) {}");
    testSame("/** @constructor */ f = function() {}");
    testSame("/** @param {number} x */ f = function(x) { function bar() { return x; } }");
    testSame("/** @param {number} x */ f = function(x) { return; }");
    testSame("/** @param {number} x\n * @return {number} */ f = function(x) { return x; }");
    testSame("/** @inheritDoc */ f = function(x) { return x; }");
    testSame("/** @override */ f = function(x) { return x; }");
  }

  @Test
  public void testMissingReturn_var_noWarning() {
    testSame("/** @param {number} x */ var f = function(x) {}");
    testSame("/** @constructor */ var f = function() {}");
    testSame("/** @param {number} x */ var f = function(x) { function bar() { return x; } }");
    testSame("/** @param {number} x */ var f = function(x) { return; }");
    testSame("/** @param {number} x\n * @return {number} */ var f = function(x) { return x; }");
    testSame("/** @const {function(number): number} */ var f = function(x) { return x; }");
    testSame("/** @inheritDoc */ var f = function(x) { return x; }");
    testSame("/** @override */ var f = function(x) { return x; }");
  }

  @Test
  public void testMissingReturn_constructor_noWarning() {
    testSame("/** @constructor */ var C = function() { return null; }");
  }

  @Test
  public void testMissingReturn_class_constructor_noWarning() {
    testSame("class C { /** @param {Array} x */ constructor(x) { return x; } }");
  }

  @Test
  public void testMissingReturn_var_noWarning_withES6Modules() {
    testSame("export /** @param {number} x */ var f = function(x) {}");
  }

  @Test
  public void testMissingReturn_functionStatement() {
    testWarning("/** @param {number} x */ function f(x) { return x; }", MISSING_RETURN_JSDOC);
    testWarning(
        lines(
            "/** @param {number} x */",
            "function f(x) {",
            "  /** @param {number} x */",
            "  function bar(x) {",
            "    return x;",
            "  }",
            "}"),
        MISSING_RETURN_JSDOC);
    testWarning(
        "/** @param {number} x */ function f(x) { if (true) { return x; } }", MISSING_RETURN_JSDOC);
    testWarning(
        "/** @param {number} x @constructor */ function f(x) { return x; }", MISSING_RETURN_JSDOC);
  }

  @Test
  public void testMissingReturn_functionStatement_withES6Modules() {
    testWarning(
        "export /** @param {number} x */ function f(x) { return x; }", MISSING_RETURN_JSDOC);
  }

  @Test
  public void testMissingReturn_assign() {
    testWarning("/** @param {number} x */ f = function(x) { return x; }", MISSING_RETURN_JSDOC);
    testWarning(
        lines(
            "/** @param {number} x */",
            "function f(x) {",
            "  /** @param {number} x */",
            "  bar = function(x) {",
            "    return x;",
            "  }",
            "}"),
        MISSING_RETURN_JSDOC);
    testWarning(
        "/** @param {number} x */ f = function(x) { if (true) { return x; } }",
        MISSING_RETURN_JSDOC);
    testWarning(
        "/** @param {number} x @constructor */ f = function(x) { return x; }",
        MISSING_RETURN_JSDOC);
  }

  @Test
  public void testMissingReturn_assign_withES6Modules() {
    testWarning(
        lines(
            "/** @param {number} x */",
            "export",
            "function f(x) {",
            "  /** @param {number} x */",
            "  bar = function(x) {",
            "    return x;",
            "  }",
            "}"),
        MISSING_RETURN_JSDOC);
  }

  @Test
  public void testMissingReturn_var() {
    testWarning("/** @param {number} x */ var f = function(x) { return x; }", MISSING_RETURN_JSDOC);
    testWarning(
        lines(
            "/** @param {number} x */",
            "function f(x) {",
            "  /** @param {number} x */",
            "  var bar = function(x) {",
            "    return x;",
            "  }",
            "}"),
        MISSING_RETURN_JSDOC);
    testWarning(
        "/** @param {number} x */ var f = function(x) { if (true) { return x; } }",
        MISSING_RETURN_JSDOC);
    testWarning(
        "/** @param {number} x @constructor */ var f = function(x) { return x; }",
        MISSING_RETURN_JSDOC);
  }

  @Test
  public void testMissingReturn_var_withES6Modules() {
    testWarning(
        "export /** @param {number} x */ var f = function(x) { return x; }", MISSING_RETURN_JSDOC);
  }

  @Test
  public void testExternsAnnotation() {
    test(
        externs("function Example() {}"),
        srcs(""),
        warning(EXTERNS_FILES_SHOULD_BE_ANNOTATED));

    testSame(
        externs(
            "/** @fileoverview Some super cool externs.\n * @externs\n */ function Example() {}"),
        srcs(""));

    testSame(
        externs(
            lines(
                "/** @fileoverview Some super cool externs.\n * @externs\n */",
                "/** @constructor */ function Example() {}",
                "/** @param {number} x */ function example2(x) {}")),
        srcs(""));

    test(
        new String[] {
            "/** @fileoverview Some externs.\n * @externs\n */ /** @const */ var example;",
            "/** @fileoverview Some more.\n * @externs\n */ /** @const */ var example2;",
        },
        new String[] {});
  }

  @Test
  public void testInvalidExternsAnnotation_withES6Modules() {
    test(
        externs("export function Example() {}"),
        srcs(""),
        warning(EXTERNS_FILES_SHOULD_BE_ANNOTATED));
  }

  @Test
  public void testValidExternsAnnotation_withES6Modules() {
    testSame(
        externs(
            lines(
                "export /** @fileoverview Some super cool externs.",
                " * @externs",
                " */",
                "function Example() {}")),
        srcs(""));
  }

  @Test
  public void testAtSignCodeDetectedWhenPresent() {
    testWarning(
        "/** blah blah {@code blah blah} blah blah */ function f() {}",
        PREFER_BACKTICKS_TO_AT_SIGN_CODE);
  }
}
