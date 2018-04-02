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
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.TypeICompilerTestCase;

/** Unit tests for {@link CheckRedundantNullabilityModifier}. */
public final class CheckRedundantNullabilityModifierTest extends TypeICompilerTestCase {
  @Override
  protected CompilerOptions getOptions(CompilerOptions options) {
    options.setWarningLevel(DiagnosticGroups.ANALYZER_CHECKS, CheckLevel.WARNING);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckRedundantNullabilityModifier(compiler);
  }

  public void testPrimitiveType() {
    checkWarning("/** @type {!boolean} */ var x;");
    checkWarning("/** @type {!number} */ var x;");
    checkWarning("/** @type {!string} */ var x;");
    checkWarning("/** @type {!symbol} */ var x;");
    checkWarning("/** @type {!undefined} */ var x;");
    checkWarning("/** @type {!function()} */ function f(){}");

    checkNoWarning("/** @type {?boolean} */ var x;");
    checkNoWarning("/** @type {boolean} */ var x;");
  }

  public void testReferenceType() {
    checkNoWarning("/** @type {!Object} */ var x;");
    checkNoWarning("/** @type {!Function} */ var x;");
    checkNoWarning("/** @type {!Symbol} */ var x;");
  }

  public void testRecordLiteral() {
    checkWarning("/** @type {!{foo: string, bar: number}} */ var x;");

    checkNoWarning("/** @type {?{foo: string, bar: number}} */ var x;");
    checkNoWarning("/** @type {{foo: string, bar: number}} */ var x;");
  }

  public void testFunctionArgs() {
    checkWarning(
        "/** @type {function(!string)} */ function f(x){}");
    checkWarning(
        "/** @type {function(): !string} */ function f(x){ return ''; }");

    checkNoWarning("/** @type {function(?string): ?string} */ function f(x){ return ''; }");
    checkNoWarning("/** @type {function(string): string} */ function f(x){ return ''; }");
  }

  public void testUnionType() {
    checkWarning("/** @type {Object|!string} */ var x;");

    checkNoWarning("/** @type {Object|string} */ var x;");
  }

  public void testEnumType() {
    checkWarning("/** @enum {!string} */ var o = {foo: 'foo'};");

    checkNoWarning("/** @enum {string} */ var o = {foo: 'foo'};");
  }

  public void testTemplateType() {
    checkWarning("/** @type {Array<!string>} */ var x;");

    checkNoWarning("/** @type {Array<?string>} */ var x;");
    checkNoWarning("/** @type {Array<string>} */ var x;");
  }

  public void testParamType() {
    checkWarning("/** @param {!string} x */ function f(x){}");

    checkNoWarning("/** @param {string} x */ function f(x){}");
  }

  public void testParamMissingType() {
    checkNoWarning("/** @param x */ function f(x){}");
  }

  public void testReturnType() {
    checkWarning("/** @return {!string} */ function f(){ return ''; }");

    checkNoWarning("/** @return {string} */ function f(){ return ''; }");
  }

  public void testTypedefType() {
    checkWarning("/** @typedef {!string} */ var x;");
    checkWarning("/** @typedef {Array<!string>} */ var x;");

    checkNoWarning("/** @typedef {string} */ var x;");
    checkNoWarning("/** @typedef {Array<string>} */ var x;");
  }

  public void testThisType() {
    checkWarning("/** @this {!string} */ function f(){}");
    checkWarning("/** @this {!{foo: string}} */ function f(){}");

    checkNoWarning("/** @this {string} */ function f(){}");
    checkNoWarning("/** @this {{foo: string}} */ function f(){}");
  }

  public void testEndPosition() {
    checkWarning("/** @type {boolean!} */ var x;");
  }

  private void checkNoWarning(String js) {
    testSame(js);
  }

  private void checkWarning(String js) {
    testWarning(js, CheckRedundantNullabilityModifier.REDUNDANT_NULLABILITY_MODIFIER_JSDOC);
  }
}
