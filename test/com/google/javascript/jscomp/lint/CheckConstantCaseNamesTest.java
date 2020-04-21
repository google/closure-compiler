/*
 * Copyright 2019 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.lint.CheckConstantCaseNames.MISSING_CONST_PROPERTY;
import static com.google.javascript.jscomp.lint.CheckConstantCaseNames.REASSIGNED_CONSTANT_CASE_NAME;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.DiagnosticGroups;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CheckConstantCaseNamesTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckConstantCaseNames(compiler);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    return options;
  }

  @Test
  public void emptyScriptIsOk() {
    testNoWarning("");
  }

  @Test
  public void emptyModuleIsOk() {
    testNoWarning("goog.module('mod');");
    testNoWarning("export {};");
  }

  @Test
  public void constantCaseNameDeclarations_requireExplicitConst() {
    testWarning("goog.module('m'); var ABC;", MISSING_CONST_PROPERTY);
    testWarning("goog.module('m'); var ABC = 0;", MISSING_CONST_PROPERTY);
    testWarning("goog.module('m'); let ABC = 0;", MISSING_CONST_PROPERTY);
    testWarning("goog.module('m'); var ABC_DEF_0 = 0;", MISSING_CONST_PROPERTY);
    testWarning("goog.module('m'); var abc = 0, DEF = 1;", MISSING_CONST_PROPERTY);
    test(
        srcs("goog.module('m'); var ABC = 0, DEF = 1;"),
        warning(MISSING_CONST_PROPERTY),
        warning(MISSING_CONST_PROPERTY));
    testWarning("goog.module('m'); var {ABC} = obj;", MISSING_CONST_PROPERTY);
    testWarning("goog.module('m'); var {abc, DEF} = obj;", MISSING_CONST_PROPERTY);
    testWarning("let ABC = 0; export {ABC};", MISSING_CONST_PROPERTY);
  }

  @Test
  public void constantCaseNameDeclarations_whenReassigned_requireCamelCase() {
    testWarning("goog.module('m'); var ABC = 0; ABC = 1;", REASSIGNED_CONSTANT_CASE_NAME);
    testWarning("goog.module('m'); let ABC = 0; ABC = 1;", REASSIGNED_CONSTANT_CASE_NAME);
    testWarning("goog.module('m'); let ABC = 0; [[ABC]] = [[1]];", REASSIGNED_CONSTANT_CASE_NAME);
  }

  @Test
  public void constantCaseNameDeclarations_whenReassignedInFunction_requireCamelCase() {
    testWarning(
        "goog.module('m'); var ABC = 0; exports = function() { ABC = 1; };",
        REASSIGNED_CONSTANT_CASE_NAME);
    testWarning(
        "goog.module('m'); let ABC = 0; exports = function() { ABC = 1; };",
        REASSIGNED_CONSTANT_CASE_NAME);
  }

  @Test
  public void constantCaseNameDeclarations_localShadowAreNotReassignments() {
    testWarning(
        // the local 'ABC' does not refer to the module-level 'ABC'.
        "goog.module('m'); let ABC = 0; if (cond) { let ABC = 0; ABC = 1; }",
        MISSING_CONST_PROPERTY);
    testWarning(
        // the local 'ABC' does not refer to the module-level 'ABC'.
        "goog.module('m'); let ABC = 0; exports = function(ABC) { ABC = 1; };",
        MISSING_CONST_PROPERTY);
  }

  @Test
  public void constantCaseNameDeclarations_usagesAreNotReassignments() {
    testWarning("goog.module('m'); let ABC = 0; alert(ABC);", MISSING_CONST_PROPERTY);
    testWarning("goog.module('m'); let ABC = 0; const {d = ABC} = {};", MISSING_CONST_PROPERTY);
    testWarning(
        "goog.module('m'); let ABC = 0; let d = 1; export {d as ABC};", MISSING_CONST_PROPERTY);
  }

  @Test
  public void constantCaseNameDeclarations_appearingInMultipleFiles_getDistinctErrors() {
    testWarning(
        srcs(
            "goog.module('m'); let ABC = 0;",
            "let ABC = 0; ABC = 1;" // not a reassignment of the module local.
            ),
        warning(MISSING_CONST_PROPERTY));
    test(
        srcs(
            // Recognize that only one out of the two 'ABC's is mutated.
            "goog.module('m1'); let ABC = 0;", "goog.module('m2'); let ABC = 0; ABC = 1;"),
        warning(MISSING_CONST_PROPERTY),
        warning(REASSIGNED_CONSTANT_CASE_NAME));
  }

  @Test
  public void constantCaseNameDeclarations_okIfAlreadyConst() {
    testSame("goog.module('m'); const ABC = 0;");
    testSame("goog.module('m'); const ABC = 0;");
    testSame("goog.module('m'); const ABC_DEF_0 = 0;");
    testSame("goog.module('m'); /** @const */ var ABC = 0;");
    testSame("goog.module('m'); /** @const */ let ABC = 0;");
    testSame("goog.module('m'); /** @const */ var ABC_DEF_0 = 0;");
    testSame("goog.module('m'); const {ABC} = obj;");
  }

  @Test
  public void nonConstantCaseNameDeclarations_dontRequireConst() {
    testSame("goog.module('m'); var abc = 0;");
    testSame("goog.module('m'); let abc = 0;");
    testSame("goog.module('m'); var AbcDef = 0;");
    testSame("goog.module('m'); var abc;");
  }

  @Test
  public void nonModuleLevelNamesDontCauseWarning() {
    testSame("var ABC = 0;");
    testSame("goog.module('m'); function fn() { var ABC = 0; }");
  }

  @Test
  public void constantCasePropertyDeclarations_dontRequireExplicitConst() {
    testSame("goog.module('m'); /** @private */ a.NAME = 'name';");
  }

  @Test
  public void constantCaseObjectLiteralKeys_dontRequireExplicitConst() {
    testSame("goog.module('m'); const Colors = {RED: 0, YELLOW: 1, GREEN: 2}");
    testSame("goog.module('m'); ns.Colors = {RED: 0, YELLOW: 1, GREEN: 2}");
  }
}
