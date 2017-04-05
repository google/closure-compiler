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
import java.util.List;

/**
 * CompilerTestCase for passes that run in both ES6 and ES5 modes.
 *
 * @author moz@google.com (Michael Zhou)
 */
public abstract class Es6CompilerTestCase extends CompilerTestCase {

  protected boolean useNTI = false;

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

  // Temporary hack until we migrate to junit 4. We use this function to run a unit
  // test with both the old and the new type checker.
  @Override
  public void test(
      List<SourceFile> externs,
      String js,
      String expected,
      DiagnosticType error,
      DiagnosticType warning,
      String description) {
    super.test(externs, js, expected, error, warning, description);
    if (this.useNTI) {
      disableTypeCheck();
      enableNewTypeInference();
      super.test(externs, js, expected, error, warning, description);
      disableNewTypeInference();
    }
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
    setLanguage(LanguageMode.ECMASCRIPT_2015, LanguageMode.ECMASCRIPT5);
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
    test(js, expected, LanguageMode.ECMASCRIPT_2015);
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
    test(js, expected, LanguageMode.ECMASCRIPT_2015);
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
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
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
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
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
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
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
  public void testSameEs6(String externs, String js, DiagnosticType warning) {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
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
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    testSame(externs, js, diag, error);
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
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
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
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
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
    testError(js, error, LanguageMode.ECMASCRIPT_2015);
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
    testError(js, error, LanguageMode.ECMASCRIPT_2015);
    testError(js, error, LanguageMode.ECMASCRIPT5);
  }

  /**
   * Verifies that the compiler generates the given error for the given inputs,
   * under just ES6 modes.
   *
   * @param js Inputs
   * @param error Expected error
   */
  public void testErrorEs6(String[] js, DiagnosticType error) {
    testError(js, error, LanguageMode.ECMASCRIPT_2015);
  }

  public void testError(String[] js, DiagnosticType error, LanguageMode mode) {
    setAcceptedLanguage(mode);
    super.testError(js, error);
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
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
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
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
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    super.test(js, expected, null, warning);
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    super.test(js, expected, null, warning);
  }

  /**
   * Verifies that the compiler generates the given warning for the given input, under just ES6.
   */
  public void testWarningEs6(String js, DiagnosticType warning, String warningMessage) {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    super.testWarning(js, warning, warningMessage);
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
    testWarning(js, expected, warning, LanguageMode.ECMASCRIPT_2015);
  }

  @Override
  protected void testExternChanges(String extern, String input, String expectedExtern) {
    testExternChanges(extern, input, expectedExtern, LanguageMode.ECMASCRIPT_2015);
    testExternChanges(extern, input, expectedExtern, LanguageMode.ECMASCRIPT5);
  }

  protected void testExternChanges(String extern, String input,
      String expectedExtern, LanguageMode lang) {
    setAcceptedLanguage(lang);
    super.testExternChanges(extern, input, expectedExtern);
  }
}

