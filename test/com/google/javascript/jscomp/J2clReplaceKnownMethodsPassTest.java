/*
 * Copyright 2016 The Closure Compiler Authors.
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

/**
 * Unit tests for {#link {@link J2clReplaceKnownMethodsPass}
 *
 * @author moz@google.com (Michael Zhou)
 */
public final class J2clReplaceKnownMethodsPassTest extends CompilerTestCase {

  private boolean useTypes = true;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    useTypes = true;
    enableTypeCheck();
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setJ2clPass(CompilerOptions.J2clPassMode.ON);
    return options;
  }

  @Override
  public CompilerPass getProcessor(final Compiler compiler) {
    return new J2clReplaceKnownMethodsPass(compiler, useTypes);
  }

  public void testReplaceWithCharAt() {
    foldStringTyped(
        "var /** number */ i; a.substring(i, i + 1)", "var /** number */ i; a.charAt(i)");
    foldStringTyped(
        "var /** number */ i; a.substring(i - 1, i)", "var /** number */ i; a.charAt(i - 1)");
    foldStringTyped(
        "var /** number */ i; ''.substring(i, i + 1)", "var /** number */ i; ''.charAt(i)");
    foldSameStringTyped("var /** number */ i; a.substring(i, 2 + 1)");
    foldSameStringTyped("a.substring(i, j + 1)");
    foldSameStringTyped("a.substring(i, i + 1)");
    foldSameStringTyped("a.substring(i, j, k)");
    foldSameStringTyped("a.substring()");
    foldSameStringTyped("a.substring(i)");

    foldStringTyped("var /** number */ i; a.slice(i, i + 1)", "var /** number */ i; a.charAt(i)");
    foldStringTyped("var /** number */ i; ''.slice(i, i + 1)", "var /** number */ i; ''.charAt(i)");
    foldSameStringTyped("a.slice(i, j + 1)");
    foldSameStringTyped("a.slice(i, i + 1)");
    foldSameStringTyped("a.slice(i, j, k)");
    foldSameStringTyped("a.slice()");
    foldSameStringTyped("a.slice(i)");

    foldSameStringTyped("var /** number */ i; substring(i, i + 1)");

    testSame("var /** ? */ a; var /** number */ i; a.substring(i, i + 1)");

    useTypes = false;
    foldSameStringTyped("''.substring(i, i + 1)");
    disableTypeCheck();
  }

  private void foldSameStringTyped(String js) {
    foldStringTyped(js, js);
  }

  private void foldStringTyped(String js, String expected) {
    test("var /** string */ a;" + js, "var /** string */ a;" + expected);
  }
}
