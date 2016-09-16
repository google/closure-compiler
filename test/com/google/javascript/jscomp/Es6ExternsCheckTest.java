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

import static com.google.javascript.jscomp.Es6ExternsCheck.MISSING_ES6_EXTERNS;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

/**
 * Test cases for Es6ExternsCheck
 */
public final class Es6ExternsCheckTest extends CompilerTestCase {

  private static final String EXTERNS_BASE = "";

  public Es6ExternsCheckTest() {
    super(EXTERNS_BASE);
    allowSourcelessWarnings();
  }

  @Override
  protected Compiler createCompiler() {
    return new NoninjectingCompiler();
  }

  @Override
  NoninjectingCompiler getLastCompiler() {
    return (NoninjectingCompiler) super.getLastCompiler();
  }

  @Override
  public void setUp() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    setLanguageOut(LanguageMode.ECMASCRIPT3);
    allowExternsChanges(false);
  }

  @Override
  public CompilerPass getProcessor(final Compiler compiler) {
    return new Es6ExternsCheck(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testEs6ObjectLiteral() {
    // Report the missing externs if the project includes files with ES6 syntax
    testError("var x = {a, b};", MISSING_ES6_EXTERNS);
  }

  public void testEs6ONoEs6() {
    allowExternsChanges(true);  // adding Symbol
    // Don't report the missing externs if the project does not includes files with ES6 syntax.
    testSame("var x = {};");
  }
}
