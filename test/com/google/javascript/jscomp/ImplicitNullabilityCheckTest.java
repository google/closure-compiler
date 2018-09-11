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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link ImplicitNullabilityCheck}. */
@RunWith(JUnit4.class)
public final class ImplicitNullabilityCheckTest extends CompilerTestCase {

  @Override
  protected CompilerOptions getOptions(CompilerOptions options) {
    options.setWarningLevel(DiagnosticGroups.ANALYZER_CHECKS, CheckLevel.WARNING);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new ImplicitNullabilityCheck(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableTypeCheck();
  }

  @Test
  public void testExplicitJsdocDoesntWarn() {
    noWarning("/** @type {boolean} */ var x;");
    noWarning("/** @type {symbol} */ var x;");
    noWarning("/** @type {null} */ var x;");
    noWarning("/** @type {!Object} */ var x;");
    noWarning("/** @type {?Object} */ var x;");
    noWarning("/** @type {function(new:Object)} */ function f(){}");
    noWarning("/** @type {function(this:Object)} */ function f(){}");
    noWarning("/** @typedef {!Object} */ var Obj; var /** Obj */ x;");

    // Test let and const
    noWarning("/** @type {boolean} */ let x;");
    noWarning("/** @type {!Object} */ let x;");

    noWarning("/** @type {!Object} */ const x = {};");
    noWarning("/** @type {boolean} */ const x = true;");
  }

  @Test
  public void testExplicitlyNullableUnion() {
    noWarning("/** @type {(Object|null)} */ var x;");
    noWarning("/** @type {(Object|number)?} */ var x;");
    noWarning("/** @type {?(Object|number)} */ var x;");
    noWarning("/** @type {(Object|?number)} */ var x;");
    warnImplicitlyNullable("/** @type {(Object|number)} */ var x;");

    noWarning("/** @type {(Object|null)} */ let x;");
    warnImplicitlyNullable("/** @type {(Object|number)} */ let x;");

    noWarning("/** @type {(Object|null)} */ const x = null;");
    warnImplicitlyNullable("/** @type {(Object|number)} */ const x = 3;;");
  }

  @Test
  public void testJsdocPositions() {
    warnImplicitlyNullable("/** @type {Object} */ var x;");
    warnImplicitlyNullable("var /** Object */ x;");
    warnImplicitlyNullable("/** @typedef {Object} */ var x;");
    warnImplicitlyNullable("/** @param {Object} x */ function f(x){}");
    warnImplicitlyNullable("/** @return {Object} */ function f(x){ return {}; }");

    warnImplicitlyNullable("/** @type {Object} */ let x;");
    warnImplicitlyNullable("/** @type {Object} */ const x = {};");
  }

  @Test
  public void testParameterizedObject() {
    warnImplicitlyNullable(lines(
        "/** @param {Object<string, string>=} opt_values */",
        "function getMsg(opt_values) {};"));
  }

  @Test
  public void testNullableTypedef() {
    // Arguable whether or not this deserves a warning
    warnImplicitlyNullable("/** @typedef {?number} */ var Num; var /** Num */ x;");
  }

  @Test
  public void testUnknownTypenameDoesntWarn() {
    test(
        externs(DEFAULT_EXTERNS),
        srcs("/** @type {gibberish} */ var x;"),
        warning(RhinoErrorReporter.UNRECOGNIZED_TYPE_ERROR));
  }

  @Test
  public void testThrowsDoesntWarn() {
    noWarning("/** @throws {Error} */ function f() {}");
    noWarning("/** @throws {TypeError}\n * @throws {SyntaxError} */ function f() {}");
  }

  @Test
  public void testUserDefinedClass() {
    warnImplicitlyNullable(lines(
        "/** @constructor */",
        "function Foo() {}",
        "/** @type {Foo} */ var x;"));

    warnImplicitlyNullable(lines(
        "function f() {",
        "  /** @constructor */",
        "  function Foo() {}",
        "  /** @type {Foo} */ var x;",
        "}"));
  }

  @Test
  public void testNamespacedTypeDoesntCrash() {
    warnImplicitlyNullable(lines(
        "/** @const */ var a = {};",
        "/** @const */ a.b = {};",
        "/** @constructor */ a.b.Foo = function() {};",
        "/** @type Array<!a.b.Foo> */ var foos = [];"));
  }

  private void warnImplicitlyNullable(String js) {
    test(
        externs(DEFAULT_EXTERNS),
        srcs(js),
        warning(ImplicitNullabilityCheck.IMPLICITLY_NULLABLE_JSDOC));
  }

  private void noWarning(String js) {
    testSame(externs(DEFAULT_EXTERNS), srcs(js));
  }
}
