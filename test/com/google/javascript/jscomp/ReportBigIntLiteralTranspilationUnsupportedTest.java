/*
 * Copyright 2020 The Closure Compiler Authors.
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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ReportBigIntLiteralTranspilationUnsupported )}. */
@RunWith(JUnit4.class)
@SuppressWarnings("RhinoNodeGetFirstFirstChild")
public class ReportBigIntLiteralTranspilationUnsupportedTest extends CompilerTestCase {

  @Before
  public void enableTypeCheckBeforePass() {
    enableTypeCheck();
    enableTypeInfoValidation();
    replaceTypesWithColors();
    enableMultistageCompilation();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new ReportBigIntLiteralTranspilationUnsupported(compiler);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    return options;
  }

  @Test
  public void reportErrorWithBigIntLiteralTranspilation() {
    testError("1234n", ReportBigIntLiteralTranspilationUnsupported.BIGINT_TRANSPILATION);
  }

  @Test
  public void noErrorWithBigIntConstructorTranspilation() {
    // Do not report an error for use of the `BigInt()` method, just the literal form.
    testSame("BigInt(1234)");
  }
}
