/*
 * Copyright 2008 The Closure Compiler Authors.
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
import com.google.javascript.rhino.testing.BaseJSTypeTestCase;

import java.util.Arrays;

/**
 * This class is mostly used by passes testing the old type checker.
 * Passes that run after type checking and need type information use
 * the class TypeICompilerTestCase.
 */
abstract class CompilerTypeTestCase extends BaseJSTypeTestCase {

  static final String CLOSURE_DEFS =
      "var goog = {};" +
      "goog.inherits = function(x, y) {};" +
      "/** @type {!Function} */ goog.abstractMethod = function() {};" +
      "goog.isArray = function(x) {};" +
      "goog.isDef = function(x) {};" +
      "goog.isFunction = function(x) {};" +
      "goog.isNull = function(x) {};" +
      "goog.isString = function(x) {};" +
      "goog.isObject = function(x) {};" +
      "goog.isDefAndNotNull = function(x) {};" +
      "goog.array = {};" +
      // simplified ArrayLike definition
      "/**\n" +
      " * @typedef {Array|{length: number}}\n" +
      " */\n" +
      "goog.array.ArrayLike;" +
      "/**\n" +
      " * @param {Array.<T>|{length:number}} arr\n" +
      " * @param {function(this:S, T, number, goog.array.ArrayLike):boolean} f\n" +
      " * @param {S=} opt_obj\n" +
      " * @return {!Array.<T>}\n" +
      " * @template T,S\n" +
      " */" +
      // return empty array to satisfy return type
      "goog.array.filter = function(arr, f, opt_obj){ return []; };" +
      "goog.asserts = {};" +
      "/** @return {*} */ goog.asserts.assert = function(x) { return x; };";

  /** A default set of externs for testing. */
  static final String DEFAULT_EXTERNS =
      CompilerTestCase.DEFAULT_EXTERNS;

  protected Compiler compiler;

  protected CompilerOptions getOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT5);
    options.setWarningLevel(
        DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.WARNING);
    options.setWarningLevel(
        DiagnosticGroups.MISPLACED_TYPE_ANNOTATION, CheckLevel.WARNING);
    options.setWarningLevel(
        DiagnosticGroups.INVALID_CASTS, CheckLevel.WARNING);
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    options.setCodingConvention(getCodingConvention());
    return options;
  }

  protected CodingConvention getCodingConvention() {
    return new GoogleCodingConvention();
  }

  protected void checkReportedWarningsHelper(String[] expected) {
    JSError[] warnings = compiler.getWarnings();
    for (String element : expected) {
      if (element != null) {
        assertThat(warnings.length).named("Number of warnings").isGreaterThan(0);
        assertThat(warnings[0].description).isEqualTo(element);
        warnings =
            Arrays.asList(warnings)
                .subList(1, warnings.length)
                .toArray(new JSError[warnings.length - 1]);
      }
    }
    if (warnings.length > 0) {
      fail("unexpected warnings(s):\n" + LINE_JOINER.join(warnings));
    }
  }

  @Override
  protected void setUp() {
    compiler = new Compiler();
    compiler.initOptions(getOptions());
    registry = compiler.getTypeRegistry();
    initTypes();
  }
}
