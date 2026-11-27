/*
 * Copyright 2026 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.lint.CheckGoogFunctionsConstant.USE_GOOG_FUNCTIONS_CONSTANT_SHORTCUT;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.DiagnosticGroups;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test case for {@link CheckGoogFunctionsConstant}. */
@RunWith(JUnit4.class)
public final class CheckGoogFunctionsConstantTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckGoogFunctionsConstant(compiler);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    return options;
  }

  @Test
  public void testNoWarning() {
    testSame("var f = goog.functions.constant(1);");
    testSame("var f = goog.functions.constant(x);");
    testSame("var f = goog.functions.constant('true');");
    testSame("var f = goog.functions.constant();");
    testSame("var f = goog.functions.TRUE;");
    testSame("var f = other.constant(true);");
  }

  @Test
  public void testTrue() {
    testWarning(
        "var f = goog.functions.constant(true);",
        USE_GOOG_FUNCTIONS_CONSTANT_SHORTCUT,
        "Use goog.functions.TRUE instead of goog.functions.constant(true).");
  }

  @Test
  public void testFalse() {
    testWarning(
        "var f = goog.functions.constant(false);",
        USE_GOOG_FUNCTIONS_CONSTANT_SHORTCUT,
        "Use goog.functions.FALSE instead of goog.functions.constant(false).");
  }

  @Test
  public void testNull() {
    testWarning(
        "var f = goog.functions.constant(null);",
        USE_GOOG_FUNCTIONS_CONSTANT_SHORTCUT,
        "Use goog.functions.NULL instead of goog.functions.constant(null).");
  }
}
