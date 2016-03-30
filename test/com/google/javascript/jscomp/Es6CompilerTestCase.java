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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

/**
 * CompilerTestCase for passes that run in both ES6 and ES5 modes.
 *
 * @author moz@google.com (Michael Zhou)
 */
public abstract class Es6CompilerTestCase extends CompilerTestCase {

  protected Es6CompilerTestCase() {
    super();
  }

  protected Es6CompilerTestCase(String externs) {
    super(externs);
  }

  protected Es6CompilerTestCase(String externs, boolean compareAsTree) {
    super(externs, compareAsTree);
  }

  @Override
  protected void setUp() throws Exception {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output, under
   * both ES5 and ES6 modes.
   *
   * @param js Input
   * @param expected Expected JS output
   */
  @Override
  public void test(String js, String expected) {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    super.test(js, expected);
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    super.test(js, expected);
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output, under
   * a specific language mode.
   *
   * @param js Input
   * @param expected Expected JS output
   */
  public void test(String js, String expected, LanguageMode mode) {
    setAcceptedLanguage(mode);
    super.test(js, expected);
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
  }

  /**
   * Verifies that the compiler pass's JS outputs match the expected outputs, under
   * a specific language mode.
   *
   * @param js Inputs
   * @param expected Expected JS outputs
   */
  public void test(String[] js, String[] expected, LanguageMode mode) {
    setAcceptedLanguage(mode);
    super.test(js, expected);
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output, under
   * just ES6. Usually this implies that the input contains ES6 features.
   *
   * @param js Input
   * @param expected Expected JS output
   */
  public void testEs6(String js, String expected) {
    test(js, expected, LanguageMode.ECMASCRIPT6);
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
  }

  /**
   * Verifies that the compiler pass's JS outputs match the expected outputs, under
   * just ES6. Usually this implies that the inputs contain ES6 features.
   *
   * @param js Inputs
   * @param expected Expected JS outputs
   */
  public void testEs6(String[] js, String[] expected) {
    test(js, expected, LanguageMode.ECMASCRIPT6);
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output,
   * under both ES5 and ES6 modes.
   *
   * @param moduleInputs Module inputs
   * @param expected Expected JS outputs (one per module)
   */
  public void testModule(String[] moduleInputs, String[] expected) {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    super.test(createModuleStar(moduleInputs), expected, null);
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    super.test(createModuleStar(moduleInputs), expected, null);
  }

  /**
   * Verifies that the compiler pass's JS output is the same as its input, under
   * both ES5 and ES6 modes.
   *
   * @param js Input and output
   */
  @Override
  public void testSame(String js) {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    super.test(js, js);
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    super.test(js, js);
  }

  /**
   * Verifies that the compiler pass's JS output is the same as its input, under
   * just ES6. Usually this implies that the input contains ES6 features.
   *
   * @param js Input and output
   */
  public void testSameEs6(String js) {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    super.test(js, js);
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
  }

  /**
   * Verifies that the compiler pass's JS output is the same as its input
   * and (optionally) that an expected warning is issued, under both ES5 and ES6 modes.
   *
   * @param externs Externs input
   * @param js Input and output
   * @param warning Expected warning, or null if no warning is expected
   */
  @Override
  public void testSame(String externs, String js, DiagnosticType warning) {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    super.testSame(externs, js, warning);
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    super.testSame(externs, js, warning);
  }

  /**
   * Verifies that the compiler pass's JS output is the same as its input
   * and (optionally) that an expected warning is issued, under both ES5 and ES6 modes.
   *
   * @param externs Externs input
   * @param js Input and output
   * @param warning Expected warning, or null if no warning is expected
   */
  public void testSameEs6(String externs, String js, DiagnosticType warning) {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    super.testSame(externs, js, warning);
  }

  /**
   * Verifies that the compiler pass's JS output is the same as its input
   * and (optionally) that an expected warning is issued, under
   * just ES6. Usually this implies that the input contains ES6 features.
   *
   * @param externs Externs input
   * @param js Input and output
   * @param diag Expected error or warning, or null if none is expected
   * @param error true if diag is an error, false if it is a warning
   */
  public void testSameEs6(String externs, String js, DiagnosticType diag, boolean error) {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    testSame(externs, js, diag, error);
  }

  /**
   * Verifies that the compiler pass's JS output is the same as its input
   * and (optionally) that an expected warning is issued, under both ES5 and ES6 modes
   *
   * @param js Input and output
   * @param warning Expected warning, or null if no warning is expected
   * @param description The description of the expected warning,
   *      or null if no warning is expected or if the warning's description
   *      should not be examined
   */
  @Override
  public void testSameNoExterns(String js, DiagnosticType warning, String description) {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    super.testSameNoExterns(js, warning, description);
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    super.testSameNoExterns(js, warning, description);
  }

  /**
   * Verifies that the compiler pass's JS output is the same as its input
   * and (optionally) that an expected warning is issued, under only ES6 mode.
   * Usually this implies that the input contains ES6 features.
   *
   * @param js Input and output
   * @param warning Expected warning, or null if no warning is expected
   * @param description The description of the expected warning,
   *      or null if no warning is expected or if the warning's description
   *      should not be examined
   */
  public void testSameNoExternsEs6(String js, DiagnosticType warning, String description) {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    super.testSameNoExterns(js, warning, description);
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
  }

  /**
   * Verifies that the compiler generates the given error for the given input,
   * under both ES5 and ES6 modes.
   *
   * @param js Input
   * @param error Expected error
   */
  @Override
  public void testError(String js, DiagnosticType error) {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    super.testError(js, error);
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    super.testError(js, error);
  }

  /**
   * Verifies that the compiler generates the given error for the given inputs,
   * under both ES5 and ES6 modes.
   *
   * @param js Inputs
   * @param error Expected error
   */
  @Override
  public void testError(String[] js, DiagnosticType error) {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    super.testError(js, error);
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    super.testError(js, error);
  }

  /**
   * Verifies that the compiler generates the given errors for the given input,
   * under both ES5 and ES6 modes.
   *
   * @param js Input
   * @param es5Error Expected error in es5
   * @param es6Error Expected error in es6
   */
  public void testError(String js, DiagnosticType es5Error, DiagnosticType es6Error) {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    super.testError(js, es6Error);
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    super.testError(js, es5Error);
  }

  /**
   * Verifies that the compiler generates the given error for the given input,
   * under a specific language mode.
   *
   * @param js Input
   * @param error Expected error
   * @param mode Specific language mode
   */
  public void testError(String js, DiagnosticType error, LanguageMode mode) {
    setAcceptedLanguage(mode);
    assertNotNull("Must assert an error", error);
    test(js, null, error, null);
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
  }

  /**
   * Verifies that the compiler generates the given error for the given input,
   * under just ES6. Usually this implies that the input contains ES6 features.
   *
   * @param js Input
   * @param error Expected error
   */
  public void testErrorEs6(String js, DiagnosticType error) {
    testError(js, error, LanguageMode.ECMASCRIPT6);
  }

  /**
   * Verifies that the compiler generates the given warning for the given input,
   * under both ES5 and ES6 modes.
   *
   * @param js Input
   * @param warning Expected warning
   */
  @Override
  public void testWarning(String js, DiagnosticType warning) {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    super.testWarning(js, warning);
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    super.testWarning(js, warning);
  }

  /**
   * Verifies that the compiler generates the given warning for the given input,
   * under a specific language mode.
   *
   * @param js Input
   * @param warning Expected warning
   * @param mode Specific language mode
   */
  public void testWarning(String js, DiagnosticType warning, LanguageMode mode) {
    setAcceptedLanguage(mode);
    super.testWarning(js, warning);
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
  }

  /**
   * Verifies that the compiler generates the given warning for the given input,
   * under just ES6. Usually this implies that the input contains ES6 features.
   *
   * @param js Input
   * @param warning Expected warning
   */
  public void testWarningEs6(String js, DiagnosticType warning) {
    testWarning(js, warning, LanguageMode.ECMASCRIPT6);
  }

  /**
   * Verifies that the compiler generates expected output and the given warning
   * for the given input, under both ES5 and ES6 modes.
   *
   * @param js Input
   * @param expected Expected JS output
   * @param warning Expected warning
   */
  public void testWarning(String js, String expected, DiagnosticType warning) {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    super.test(js, expected, null, warning);
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    super.test(js, expected, null, warning);
  }

  /**
   * Verifies that the compiler generates expected output and the given warning
   * for the given input, under a specific language mode.
   *
   * @param js Input
   * @param expected Expected JS output
   * @param warning Expected warning
   * @param mode Specific language mode
   */
  private void testWarning(String js, String expected,
      DiagnosticType warning, LanguageMode mode) {
    setAcceptedLanguage(mode);
    super.test(js, expected, null, warning);
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
  }

  /**
   * Verifies that the compiler generates expected output and the given warning
   * for the given input, under just ES6. Usually this implies that the input
   * contains ES6 features.
   *
   * @param js Input
   * @param expected Expected JS output
   * @param warning Expected warning
   */
  public void testWarningEs6(String js, String expected, DiagnosticType warning) {
    testWarning(js, expected, warning, LanguageMode.ECMASCRIPT6);
  }

  @Override
  protected void testExternChanges(String extern, String input, String expectedExtern) {
    testExternChanges(extern, input, expectedExtern, LanguageMode.ECMASCRIPT6);
    testExternChanges(extern, input, expectedExtern, LanguageMode.ECMASCRIPT5);
  }

  protected void testExternChanges(String extern, String input,
      String expectedExtern, LanguageMode lang) {
    setAcceptedLanguage(lang);
    super.testExternChanges(extern, input, expectedExtern);
  }
}

