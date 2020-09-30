/*
 * Copyright 2014 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.Es7RewriteExponentialOperator.TRANSPILE_EXPONENT_USING_BIGINT;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test cases for transpilation pass that replaces the exponential operator (`**`). */
@RunWith(JUnit4.class)
public final class Es7RewriteExponentialOperatorTest extends CompilerTestCase {

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2016);
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    enableTypeInfoValidation();
    enableTypeCheck();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new Es7RewriteExponentialOperator(compiler);
  }

  @Test
  public void testExponentiationOperator() {
    test(srcs("2 ** 2"), expected("Math.pow(2, 2)"));
  }

  @Test
  public void testExponentiationAssignmentOperator() {
    test(srcs("x **= 2;"), expected("x = Math.pow(x, 2)"));
  }

  /** @see <a href="https://github.com/google/closure-compiler/issues/2821">Issue 2821</a> */
  @Test
  public void testExponentialOperatorInIfCondition() {
    test(srcs("if (2 ** 3 > 0) { }"), expected("if (Math.pow(2, 3) > 0) { }"));
  }

  /** @see <a href="https://github.com/google/closure-compiler/issues/3684">Issue 3684</a> */
  @Test
  public void testExponentialOperatorWithBigIntCall() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2020);
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    // Avoid transpiling to Math.pow() when BigInt is involved.
    testError(srcs("let a = BigInt(2) ** BigInt(3);"), TRANSPILE_EXPONENT_USING_BIGINT);
  }

  /** @see <a href="https://github.com/google/closure-compiler/issues/3684">Issue 3684</a> */
  @Test
  public void testAssignExponentialOperatorWithBigIntCall() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2020);
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    // Avoid transpiling to Math.pow() when BigInt is involved.
    testError(srcs("let a = 3n; a **= BigInt(2);"), TRANSPILE_EXPONENT_USING_BIGINT);
  }

  /** @see <a href="https://github.com/google/closure-compiler/issues/3684">Issue 3684</a> */
  @Test
  public void testExponentialOperatorWithBigIntCall_noWarningWithoutTypeChecking() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2020);
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    disableTypeCheck();
    disableTypeInfoValidation();
    // Ideally, we would like to avoid transpiling to Math.pow() when BigInt is involved. However,
    // when type information is missing, the transpile succeeds and generates this runtime-erroneous
    // code for <ES2016.
    test(
        srcs("let a = BigInt(2) ** BigInt(3);"),
        expected("let a = Math.pow(BigInt(2), BigInt(3));"));
  }

  /** @see <a href="https://github.com/google/closure-compiler/issues/3684">Issue 3684</a> */
  @Test
  public void testExponentialOperatorWithBigIntLiteral() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2020);
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    // Avoid transpiling to Math.pow() when BigInt is involved.
    testError(srcs("let a = 2n ** 3n;"), TRANSPILE_EXPONENT_USING_BIGINT);
  }
}
