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

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.DiagnosticGroups;

import java.io.IOException;

/**
 * Test case for {@link CheckForInOverArray}.
 *
 */
public final class CheckForInOverArrayTest extends CompilerTestCase {

  @Override
  public void setUp() throws IOException {
    enableTypeCheck();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckForInOverArray(compiler);
  }

  @Override
  protected CompilerOptions getOptions(CompilerOptions options) {
    super.getOptions(options);
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    return options;
  }

  public void testForInOverArray1() {
    testWarning(LINE_JOINER.join(
        "var arr = [1, 2, 3]; var b;",
        "for (var i in arr) {",
        "  b += arr[i];",
        "}"));
  }

  public void testForInOverArray2() {
    testPass(LINE_JOINER.join(
        "var arr = {prop: 1, prop: []}; var b;",
        "for (var i in arr) {",
        "  b += arr[i];",
        "}"));
  }

  /**
   * Only when we are sure it is for..in over
   * array will a warning be generated.
   */
  public void testForInOverArray3() {
    testWarning(LINE_JOINER.join(
        "var b;",
        "var arr = {prop: 1, prop: []};",
        "if (true) { arr = []; }",
        "for (var i in arr) {",
        "  b += arr[i];",
        "}"));
  }

  public void testForInOverArray4() {
    testWarning(LINE_JOINER.join(
        "var arr = Array(10); var b;",
        "for (var i in arr) {",
        "  b += arr[i];",
        "}"));
  }

  public void testForInOverArray5() {
    testWarning(LINE_JOINER.join(
        "var arr = new Array(10); var b;",
        "for (var i in arr) {",
        "  b += arr[i];",
        "}"));
  }

  public void testForInOverArray6() {
    testWarning(LINE_JOINER.join(
        "var b;",
        "var arr = [];",
        "for (var i in arr) {",
        "  b += arr[i];",
        "}"));
  }

  public void testForInOverArray7() {
    testPass(LINE_JOINER.join(
        "var b;",
        "var arr = {};",
        "for (var i in arr) {",
        "  b += arr[i];",
        "}"));
  }

  private void testPass(String js) {
    testSame("", js, null);
  }

  private void testWarning(String js) {
    testSame("", js, CheckForInOverArray.FOR_IN_OVER_ARRAY);
  }
}
