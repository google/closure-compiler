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
package com.google.javascript.jscomp.integration;

import static com.google.javascript.rhino.testing.Asserts.assertThrows;

import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.CompilerOptionsPreprocessor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Integration tests for compilation in {@link LanguageMode#ECMASCRIPT6_TYPED} mode
 *
 * <p>Only parsing and code printing of types is supported.
 */
@RunWith(JUnit4.class)
public final class Es6TypedIntegrationTest extends IntegrationTestCase {

  @Test
  public void forbidsTranspilingTsToJS() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);

    assertThrows(
        CompilerOptionsPreprocessor.InvalidOptionsException.class,
        () -> test(options, "var x: number = 12;\nalert(x);", "alert(12);"));
  }

  @Test
  public void forbidsParsingTypeSyntax() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT6_TYPED);

    assertThrows(
        CompilerOptionsPreprocessor.InvalidOptionsException.class,
        () -> test(options, "var x: number = 12;\nalert(x);", "alert(12);"));
  }

  @Override
  protected CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT6_TYPED);
    return options;
  }
}
