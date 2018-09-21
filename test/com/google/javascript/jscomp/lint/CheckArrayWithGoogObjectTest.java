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

import static com.google.javascript.jscomp.lint.CheckArrayWithGoogObject.ARRAY_PASSED_TO_GOOG_OBJECT;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.DiagnosticGroups;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test case for {@link CheckArrayWithGoogObject}.
 *
 */
@RunWith(JUnit4.class)
public final class CheckArrayWithGoogObjectTest extends CompilerTestCase {

  private static final String GOOG_OBJECT = lines(
      "var goog = {};",
      "goog.object = {};",
      "goog.object.forEach = function(obj, f, opt_this) {}");

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckArrayWithGoogObject(compiler);
  }

  @Override
  protected CompilerOptions getOptions(CompilerOptions options) {
    super.getOptions(options);
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    return options;
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableTranspile();
    enableTypeCheck();
  }

  @Test
  public void testGoogObjectForEach1() {
    testGoogObjectWarning(lines(
        GOOG_OBJECT,
        "var arr = [1, 2, 3];",
        "goog.object.forEach(arr, alert);"));
  }

  @Test
  public void testGoogObjectForEach2() {
    testGoogObjectWarning(lines(
        GOOG_OBJECT,
        "function f(/** Array<number>|number */ n) {",
        "  if (typeof n == 'number')",
        "    alert(n);",
        "  else",
        "    goog.object.forEach(n, alert);",
        "}"));
  }

  @Test
  public void testGoogObjectForEach3() {
    testGoogObjectWarning(lines(
        GOOG_OBJECT,
        "function f(/** !Array<number> */ arr) {",
        "  goog.object.forEach(arr, alert);",
        "}"));
  }

  @Test
  public void testGoogObjectForEach4() {
    testNoGoogObjectWarning(lines(
        GOOG_OBJECT,
        "function f(/** Object<string, number> */ obj) {",
        "  goog.object.forEach(obj, alert);",
        "}"));
  }

  private void testGoogObjectWarning(String js) {
    test(externs(DEFAULT_EXTERNS), srcs(js), warning(ARRAY_PASSED_TO_GOOG_OBJECT));
  }

  private void testNoGoogObjectWarning(String js) {
    testSame(externs(DEFAULT_EXTERNS), srcs(js));
  }
}
