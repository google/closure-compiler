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

import static com.google.javascript.jscomp.lint.CheckJSDocStyle.EXTERNS_FILES_SHOULD_BE_ANNOTATED;
import static com.google.javascript.jscomp.lint.CheckJSDocStyle.INCORRECT_PARAM_NAME;
import static com.google.javascript.jscomp.lint.CheckJSDocStyle.MISSING_JSDOC;
import static com.google.javascript.jscomp.lint.CheckJSDocStyle.MISSING_PARAMETER_JSDOC;
import static com.google.javascript.jscomp.lint.CheckJSDocStyle.MIXED_PARAM_JSDOC_STYLES;
import static com.google.javascript.jscomp.lint.CheckJSDocStyle.MUST_BE_PRIVATE;
import static com.google.javascript.jscomp.lint.CheckJSDocStyle.OPTIONAL_PARAM_NOT_MARKED_OPTIONAL;
import static com.google.javascript.jscomp.lint.CheckJSDocStyle.OPTIONAL_TYPE_NOT_USING_OPTIONAL_NAME;
import static com.google.javascript.jscomp.lint.CheckJSDocStyle.WRONG_NUMBER_OF_PARAMS;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;

/**
 * Test case for {@link CheckJSDocStyle}.
 */
public final class CheckJSDocStyleTest extends CompilerTestCase {
  public CheckJSDocStyleTest() {
    super("/** @fileoverview\n * @externs\n */");
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
  }

  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    return new CheckJSDocStyle(compiler);
  }

  public void testMissingJsDoc() {
    testWarning("function f() {}", MISSING_JSDOC);
    testWarning("var f = function() {}", MISSING_JSDOC);
    testWarning("let f = function() {}", MISSING_JSDOC);
    testWarning("const f = function() {}", MISSING_JSDOC);
    testWarning("foo.bar = function() {}", MISSING_JSDOC);
    testWarning("Foo.prototype.bar = function() {}", MISSING_JSDOC);
    testWarning("class Foo { bar() {} }", MISSING_JSDOC);
    testWarning("var Foo = class { bar() {} };", MISSING_JSDOC);

    testSame("/** @return {string} */ function f() {}");
    testSame("/** @return {string} */ var f = function() {}");
    testSame("/** @return {string} */ let f = function() {}");
    testSame("/** @return {string} */ const f = function() {}");
    testSame("/** @return {string} */ foo.bar = function() {}");
    testSame("/** @return {string} */ Foo.prototype.bar = function() {}");
    testSame("class Foo { /** @return {string} */ bar() {} }");
    testSame("var Foo = class { /** @return {string} */ bar() {} };");
  }

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

  public void testMissingJsDoc_noWarningIfNotTopLevel() {
    testSame(inIIFE("function f() {}"));
    testSame(inIIFE("var f = function() {}"));
    testSame(inIIFE("let f = function() {}"));
    testSame(inIIFE("const f = function() {}"));
    testSame(inIIFE("foo.bar = function() {}"));
    testSame(inIIFE("class Foo { bar() {} }"));
    testSame(inIIFE("var Foo = class { bar() {} };"));

    testSame("myArray.forEach(function(elem) { alert(elem); });");

    testSame(LINE_JOINER.join(
        "Polymer({",
        "  is: 'example-elem',",
        "  someMethod: function() {},",
        "});"));

    testSame(LINE_JOINER.join(
        "Polymer({",
        "  is: 'example-elem',",
        "  someMethod() {},",
        "});"));
  }

  public void testMissingJsDoc_noWarningIfNotTopLevelAndNoParams() {
    testSame(LINE_JOINER.join(
        "describe('a karma test', function() {",
        "  /** @ngInject */",
        "  var helperFunction = function($compile, $rootScope) {};",
        "})"));
  }

  public void testMissingJsDoc_noWarningOnTestFunctions() {
    testSame("function testSomeFunctionality() {}");
    testSame("var testSomeFunctionality = function() {};");
    testSame("let testSomeFunctionality = function() {};");
    testSame("const testSomeFunctionality = function() {};");

    testSame("function setUp() {}");
    testSame("function tearDown() {}");
    testSame("var setUp = function() {};");
    testSame("var tearDown = function() {};");
  }

  private String inIIFE(String js) {
    return "(function() {\n" + js + "\n})()";
  }

  public void testMissingParam_noWarning() {
    testSame(LINE_JOINER.join(
        "/**",
        " * @param {string} x",
        " * @param {string} y",
        " */",
        "function f(x, y) {}"));

    testSame(LINE_JOINER.join(
        "/** @override */",
        "Foo.bar = function(x, y) {}"));

    testSame(LINE_JOINER.join(
        "/**",
        " * @param {string=} x",
        " */",
        "function f(x = 1) {}"));

    testSame(LINE_JOINER.join(
        "/**",
        " * @param {number=} x",
        " * @param {number=} y",
        " * @param {number=} z",
        " */",
        "function f(x = 1, y = 2, z = 3) {}"));

    testSame(LINE_JOINER.join(
        "/**",
        " * @param {...string} args",
        " */",
        "function f(...args) {}"));

    testSame(LINE_JOINER.join(
        "(function() {",
        "  myArray.forEach(function(elem) { alert(elem); });",
        "})();"));

    testSame(LINE_JOINER.join(
        "(function() {",
        "  myArray.forEach(elem => alert(elem));",
        "})();"));

    testSame("/** @type {function(number)} */ function f(x) {}");

    testSame("function f(/** string */ inlineArg) {}");
    testSame("/** @export */ function f(/** string */ inlineArg) {}");

    testSame("class Foo { constructor(/** string */ inlineArg) {} }");
    testSame("class Foo { method(/** string */ inlineArg) {} }");

    testSame("class Foo { /** @export */ constructor(/** string */ inlineArg) {} }");
    testSame("class Foo { /** @export */ method(/** string */ inlineArg) {} }");
  }

  public void testMissingParam() {
    testWarning(
        LINE_JOINER.join(
            "/**",
            " * @param {string} x",
            // No @param for y.
            " */",
            "function f(x, y) {}"),
        WRONG_NUMBER_OF_PARAMS);

    testWarning(
        LINE_JOINER.join(
            "/**",
            " * @param {string} x",
            " */",
            "function f(x = 1) {}"),
        OPTIONAL_PARAM_NOT_MARKED_OPTIONAL);

    testWarning(
        LINE_JOINER.join(
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

  public void testMissingParamWithDestructuringPattern() {
    testWarning(
        LINE_JOINER.join(
            "/**",
            " * @param {string} namedParam",
            " * @return {void}",
            " */",
            "function f(namedParam, {destructuring:pattern}) {",
            "}"),
        WRONG_NUMBER_OF_PARAMS);

    testWarning(
        LINE_JOINER.join(
            "/**",
            " * @param {string} namedParam",
            " * @return {void}",
            " */",
            "function f({destructuring:pattern}, namedParam) {",
            "}"),
        WRONG_NUMBER_OF_PARAMS);

    testWarning(
        LINE_JOINER.join(
            "/**",
            " * @param {string} namedParam",
            " * @return {void}",
            " */",
            "function f(namedParam, [pattern]) {",
            "}"),
        WRONG_NUMBER_OF_PARAMS);

    testWarning(
        LINE_JOINER.join(
            "/**",
            " * @param {string} namedParam",
            " * @return {void}",
            " */",
            "function f([pattern], namedParam) {",
            "}"),
        WRONG_NUMBER_OF_PARAMS);
  }

  public void testMissingParamWithDestructuringPatternWithDefault() {
    testWarning(
        LINE_JOINER.join(
            "/**",
            " * @param {string} namedParam",
            " * @return {void}",
            " */",
            "function f(namedParam, {destructuring:pattern} = defaultValue) {",
            "}"),
        WRONG_NUMBER_OF_PARAMS);

    testWarning(
        LINE_JOINER.join(
            "/**",
            " * @param {string} namedParam",
            " * @return {void}",
            " */",
            "function f(namedParam, [pattern] = defaultValue) {",
            "}"),
        WRONG_NUMBER_OF_PARAMS);
  }

  public void testParamWithNoTypeInfo() {
    testSame(
        LINE_JOINER.join(
            "/**",
            " * @param x A param with no type information.",
            " */",
            "function f(x) { }"));

  }

  public void testMissingPrivate() {
    testSame(
        LINE_JOINER.join(
            "/**",
            " * @return {number}",
            " */",
            "X.prototype.foo_ = function() { return 0; }"),
        MUST_BE_PRIVATE);

    testSame(
        LINE_JOINER.join(
            "/**",
            " * @return {number}",
            " * @private",
            " */",
            "X.prototype.foo_ = function() { return 0; }"));
  }

  public void testOptionalArgs() {
    testSame(
        LINE_JOINER.join(
            "/**",
            " * @param {number=} n",
            " */",
            "function f(n) {}"),
        OPTIONAL_TYPE_NOT_USING_OPTIONAL_NAME);

    testSame(
        LINE_JOINER.join(
            "/**",
            " * @param {number} opt_n",
            " */",
            "function f(opt_n) {}"),
        OPTIONAL_PARAM_NOT_MARKED_OPTIONAL);

    testSame(LINE_JOINER.join(
        "/**",
        " * @param {number=} opt_n",
        " */",
        "function f(opt_n) {}"));
  }

  public void testParamsOutOfOrder() {
    testWarning(
        LINE_JOINER.join(
            "/**",
            " * @param {?} second",
            " * @param {?} first",
            " */",
            "function f(first, second) {}"),
        INCORRECT_PARAM_NAME);
  }

  public void testMixedStyles() {
    testWarning(
        LINE_JOINER.join(
            "/**",
            " * @param {?} first",
            " * @param {string} second",
            " */",
            "function f(first, /** string */ second) {}"),
        MIXED_PARAM_JSDOC_STYLES);
  }

  public void testDestructuring() {
    testSame(
        LINE_JOINER.join(
            "/**",
            " * @param {{x: number, y: number}} point",
            " */",
            "function getDistanceFromZero({x, y}) {}"));

    testSame("function getDistanceFromZero(/** {x: number, y: number} */ {x, y}) {}");
  }

  public void testExternsAnnotation() {
    testSame(
        "function Example() {}",
        "",
        EXTERNS_FILES_SHOULD_BE_ANNOTATED);

    testSame(
        "/** @fileoverview Some super cool externs.\n * @externs\n */ function Example() {}",
        "",
        null);

    testSame(
        LINE_JOINER.join(
            "/** @fileoverview Some super cool externs.\n * @externs\n */",
            "/** @constructor */ function Example() {}",
            "/** @param {number} x */ function example2(x) {}"),
        "",
        null);

    test(
        new String[] {
            "/** @fileoverview Some externs.\n * @externs\n */ /** @const */ var example;",
            "/** @fileoverview Some more.\n * @externs\n */ /** @const */ var example2;",
        },
        new String[] {},
        null);
  }
}
