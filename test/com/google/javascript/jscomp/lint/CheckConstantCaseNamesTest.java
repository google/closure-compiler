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

  private void testWarning(String js) {
    testWarning(js, CheckConstantCaseNames.MISSING_CONST_PROPERTY);
  }

  @Test
  public void constantCaseNameDeclarations_requireExplicitConst() {
    testWarning("goog.module('m'); var ABC;");
    testWarning("goog.module('m'); var ABC = 0;");
    testWarning("goog.module('m'); let ABC = 0;");
    testWarning("goog.module('m'); var ABC_DEF_0 = 0;");
    testWarning("goog.module('m'); var ABC;");
    testWarning("goog.module('m'); var abc = 0, DEF = 1;");
    test(
        srcs("goog.module('m'); var ABC = 0, DEF = 1;"),
        warning(CheckConstantCaseNames.MISSING_CONST_PROPERTY),
        warning(CheckConstantCaseNames.MISSING_CONST_PROPERTY));
    testWarning("goog.module('m'); var {ABC} = obj;");
    testWarning("goog.module('m'); var {abc, DEF} = obj;");
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
