/*
 * Copyright 2018 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.DiagnosticGroups;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link CheckNullabilityModifiers}. */
@RunWith(JUnit4.class)
public final class CheckNullabilityModifiersTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckNullabilityModifiers(compiler);
  }

  @Override
  protected CompilerOptions getOptions(CompilerOptions options) {
    super.getOptions(options);
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    return options;
  }

  @Test
  public void testPrimitiveType() {
    checkRedundantWarning("/** @type {!boolean} */ var x;");
    checkRedundantWarning("/** @type {!number} */ var x;");
    checkRedundantWarning("/** @type {!string} */ var x;");
    checkRedundantWarning("/** @type {!symbol} */ var x;");
    checkRedundantWarning("/** @type {!undefined} */ var x;");
    checkRedundantWarning("/** @type {!void} */ var x;");

    checkNoWarning("/** @type {?boolean} */ var x;");
    checkNoWarning("/** @type {boolean} */ var x;");
  }

  @Test
  public void testReferenceType() {
    checkMissingWarning("/** @type {Object} */ var x;");
    checkMissingWarning("/** @type {Function} */ var x;");
    checkMissingWarning("/** @type {Symbol} */ var x;");

    checkNoWarning("/** @type {?Object} */ var x;");
    checkNoWarning("/** @type {!Object} */ var x;");
  }

  @Test
  public void testRecordType() {
    checkRedundantWarning("/** @type {!{foo: string}} */ var x;");
    checkRedundantWarning("/** @type {{foo: !string}} */ var x;");

    checkMissingWarning("/** @type {{foo: Object}} */ var x;");

    checkNoWarning("/** @type {?{foo: string}} */ var x;");
    checkNoWarning("/** @type {{foo: string}} */ var x;");
    checkNoWarning("/** @type {{foo: ?string}} */ var x;");
    checkNoWarning("/** @type {{foo: !Object}} */ var x;");
    checkNoWarning("/** @type {{foo: ?Object}} */ var x;");
  }

  @Test
  public void testFunctionType() {
    checkRedundantWarning("/** @type {!function()} */ function f(){}");
    checkRedundantWarning("/** @type {function(!string)} */ function f(x){}");
    checkRedundantWarning("/** @type {function(): !string} */ function f(x){}");

    checkMissingWarning("/** @type {function(Object)} */ function f(x){}");
    checkMissingWarning("/** @type {function(): Object} */ function f(x){}");

    checkNoWarning("/** @type {function()} */ function f(){}");
    checkNoWarning("/** @type {function(?string): ?string} */ function f(x){}");
    checkNoWarning("/** @type {function(string): string} */ function f(x){}");
    checkNoWarning("/** @type {function(!Object): ?Object} */ function f(x){}");
    checkNoWarning("/** @type {function(new:Object)} */ function f(){}");
    checkNoWarning("/** @type {function(this:Object)} */ function f(){}");
  }

  @Test
  public void testUnionType() {
    checkRedundantWarning("/** @type {!Object|!string} */ var x;");

    checkMissingWarning("/** @type {Object|string} */ var x;");

    checkNoWarning("/** @type {?Object|string} */ var x;");
    checkNoWarning("/** @type {!Object|string} */ var x;");
  }

  @Test
  public void testEnumType() {
    checkRedundantWarning("/** @enum {!boolean} */ var x;");
    checkRedundantWarning("/** @enum {!number} */ var x;");
    checkRedundantWarning("/** @enum {!string} */ var x;");
    checkRedundantWarning("/** @enum {!symbol} */ var x;");

    checkNoWarning("/** @enum {?string} */ var x;");
    checkNoWarning("/** @enum {string} */ var x;");
    checkNoWarning("/** @enum {Object} */ var x;");
    checkNoWarning("/** @enum {?Object} */ var x;");
    checkNoWarning("/** @enum {!Object} */ var x;");
  }

  @Test
  public void testTemplateDefinitionType() {
    checkNoWarning("/** @param {T} x @template T */", "function f(x){}");
    checkNoWarning("/** @param {S} x @return {T} @template S,T */", "function f(x){}");
    checkNoWarning(
        lines(
            "/** @constructor @template T */ function Foo(){}",
            "/** @param {T} x */ Foo.prototype.bar = function(x){};"));
  }

  @Test
  public void testTemplateInstantiationType() {
    checkRedundantWarning("/** @type {!Array<!string>} */ var x;");

    checkMissingWarning("/** @type {Array<string>} */ var x;");
    checkMissingWarning("/** @type {!Array<Object>} */ var x;");

    checkNoWarning("/** @type {!Array<?string>} */ var x;");
    checkNoWarning("/** @type {!Array<string>} */ var x;");
    checkNoWarning("/** @type {!Array<?Object>} */ var x;");
    checkNoWarning("/** @type {!Array<!Object>} */ var x;");
  }

  @Test
  public void testParamType() {
    checkRedundantWarning("/** @param {!string} x */ function f(x){}");

    checkMissingWarning("/** @param {Object} x */ function f(x){}");

    checkNoWarning("/** @param {?string} x */ function f(x){}");
    checkNoWarning("/** @param {string} x */ function f(x){}");
    checkNoWarning("/** @param {?Object} x */ function f(x){}");
    checkNoWarning("/** @param {!Object} x */ function f(x){}");
  }

  @Test
  public void testParamMissingType() {
    checkNoWarning("/** @param x */ function f(x){}");
  }

  @Test
  public void testReturnType() {
    checkRedundantWarning("/** @return {!string} */ function f(){}");

    checkMissingWarning("/** @return {Object} */ function f(){}");

    checkNoWarning("/** @return {?string} */ function f(){}");
    checkNoWarning("/** @return {string} */ function f(){}");
    checkNoWarning("/** @return {?Object} */ function f(){}");
    checkNoWarning("/** @return {!Object} */ function f(){}");
  }

  @Test
  public void testTypedefType() {
    checkRedundantWarning("/** @typedef {!string} */ var x;");

    checkMissingWarning("/** @typedef {Object} */ var x;");

    checkNoWarning("/** @typedef {?string} */ var x;");
    checkNoWarning("/** @typedef {string} */ var x;");
    checkNoWarning("/** @typedef {?Object} */ var x;");
    checkNoWarning("/** @typedef {!Object} */ var x;");
  }

  @Test
  public void testThisType() {
    checkRedundantWarning("/** @this {!string} */ function f(){}");

    checkNoWarning("/** @this {?string} */ function f(){}");
    checkNoWarning("/** @this {string} */ function f(){}");
    checkNoWarning("/** @this {Object} */ function f(){}");
    checkNoWarning("/** @this {?Object} */ function f(){}");
    checkNoWarning("/** @this {!Object} */ function f(){}");
  }

  @Test
  public void testBaseType() {
    checkNoWarning("/** @extends {Object} */ function f(){}");
    checkNoWarning("/** @implements {Object} */ function f(){}");
  }

  @Test
  public void testThrowsType() {
    // TODO(tjgq): The style guide forbids throwing anything other than Error subclasses, so an
    // @throws should never contain a primitive type. Should we suppress the warning in this case?
    checkRedundantWarning("/** @throws {!string} */ function f(){}");

    checkMissingWarning("/** @throws {Object} */ function f(){}");

    checkNoWarning("/** @throws {string} */ function f(){}");
    checkNoWarning("/** @throws {?string} */ function f(){}");
    checkNoWarning("/** @throws {?Object} */ function f(){}");
    checkNoWarning("/** @throws {!Object} */ function f(){}");
  }

  @Test
  public void testTypeOf() {
    checkNoWarning("/** @type {typeof Object} */ var x;");
  }

  @Test
  public void testEndPosition() {
    checkRedundantWarning("/** @type {string!} */ var x;");

    checkNoWarning("/** @type {string?} */ var x;");
    checkNoWarning("/** @type {Object!} */ var x;");
    checkNoWarning("/** @type {Object?} */ var x;");
  }

  @Test
  public void testMultipleFiles() {
    checkMissingWarning(
        "/** @param {T} x @return {T} @template T */ function f(x){}",
        "/** @param {T} x */ function g(x){}");
  }

  @Test
  public void testSetToNull() {
    checkNullMissingWarning("/** @type {Object} */ var x = null;");
    checkNullMissingWarning(
        "/** @constructor */ function C() {} /** @private {Object} */ C.prop = null;");
    checkNullMissingWarning(
        "/** @constructor */ function C() { /** @private {Object} */ this.foo = null; }");

    checkNoWarning("/** @type {?Object} */ var x = null;");
    checkNoWarning("/** @constructor */ function C() {} /** @private {?Symbol} */ C.prop = null;");
    checkNoWarning(
        "/** @constructor */ function C() { /** @private {?Object} */ this.foo = null; }");
  }

  private void checkNoWarning(String... js) {
    testSame(js);
  }

  private void checkMissingWarning(String... js) {
    testWarning(js, CheckNullabilityModifiers.MISSING_NULLABILITY_MODIFIER_JSDOC);
  }

  private void checkNullMissingWarning(String... js) {
    testWarning(js, CheckNullabilityModifiers.NULL_MISSING_NULLABILITY_MODIFIER_JSDOC);
  }

  private void checkRedundantWarning(String... js) {
    testWarning(js, CheckNullabilityModifiers.REDUNDANT_NULLABILITY_MODIFIER_JSDOC);
  }
}
