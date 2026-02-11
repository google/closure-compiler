/*
 * Copyright 2021 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.LocaleDataPasses.ProtectGoogLocale;
import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test which checks that replacer works correctly. */
@RunWith(JUnit4.class)
public final class LocaleDataPassesTest extends CompilerTestCase {

  /** Indicates which part of the replacement we're currently testing */
  enum TestMode {
    PROTECT_DATA,
    // Test replacement of the protected function call form with the final message values.
    REPLACE_PROTECTED_DATA
  }

  // Messages returned from fake bundle, keyed by `JsMessage.id`.
  private TestMode testMode = TestMode.PROTECT_DATA;
  private String locale = "en";

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    // locale prework and replacement always occurs after goog.provide and module rewriting
    // so enable this rewriting.
    enableRewriteClosureCode();
    enableRewriteClosureProvides();
    enableClosurePassForExpected();
    enableNormalize();

    testMode = TestMode.PROTECT_DATA;
    locale = "en";
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLocale(this.locale);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return switch (testMode) {
      case PROTECT_DATA ->
          new CompilerPass() {
            @Override
            public void process(Node externs, Node root) {
              final ProtectGoogLocale extract = new ProtectGoogLocale(compiler);
              extract.process(externs, root);
            }
          };
      case REPLACE_PROTECTED_DATA ->
          new CompilerPass() {
            @Override
            public void process(Node externs, Node root) {
              final ProtectGoogLocale extract = new ProtectGoogLocale(compiler);
              extract.process(externs, root);
              final LocaleDataPasses.LocaleSubstitutions subs =
                  new LocaleDataPasses.LocaleSubstitutions(
                      compiler, compiler.getOptions().getLocale());
              subs.process(externs, root);
            }
          };
    };
  }

  static class LocaleResult {
    LocaleResult(String locale, String result) {
      this.locale = locale;
      this.result = expected(result);
    }

    LocaleResult(String locale, Expected result) {
      this.locale = locale;
      this.result = result;
    }

    final String locale;
    final Expected result;
  }

  /**
   * The primary test method to use in this file.
   *
   * @param originalJs The original, input JS code
   * @param protectedJs What the code should look like after message definitions are is protected
   *     from mangling during optimizations, but before they are replaced with the localized message
   *     data.
   * @param allExpected What the code should look like after full replacement with localized
   *     messages has been done.
   */
  private void multiTest(Sources originalJs, Expected protectedJs, LocaleResult... allExpected) {
    // The PROTECT_DATA mode needs to add externs for the protection functions.
    allowExternsChanges();
    testMode = TestMode.PROTECT_DATA;
    test(originalJs, protectedJs);
    for (LocaleResult expected : allExpected) {
      testMode = TestMode.REPLACE_PROTECTED_DATA;
      this.locale = expected.locale;
      test(originalJs, expected.result);
    }
  }

  /**
   * The primary test method to use in this file.
   *
   * @param originalJs The original, input JS code
   * @param protectedJs What the code should look like after message definitions are is protected
   *     from mangling during optimizations, but before they are replaced with the localized message
   *     data.
   * @param allExpected What the code should look like after full replacement with localized
   *     messages has been done.
   */
  private void multiTest(String originalJs, String protectedJs, LocaleResult... allExpected) {
    multiTest(srcs(originalJs), expected(protectedJs), allExpected);
  }

  @Test
  public void testBaseJsGoogLocaleRef() {
    // We're confirming that there won't be any error reported for the use of `goog.LOCALE`.
    multiTest( //
        """
        /**
         * @fileoverview
         * @provideGoog // no @localeFile, but base.js has this special annotation
         */
        goog.provide('some.Obj');
        goog.LOCALE = 'en';
        console.log(goog.LOCALE);
        """,
        """
        /**
         * @fileoverview
         * @provideGoog // no @localeFile, but base.js has this special annotation
         */
        goog.provide('some.Obj');
        goog.LOCALE = __JSC_LOCALE__;
        console.log(__JSC_LOCALE__);
        """,
        new LocaleResult(
            "es_ES",
            """
            /**
             * @fileoverview
             * @provideGoog // no @localeFile, but base.js has this special annotation
             */
            goog.provide('some.Obj');
            goog.LOCALE = 'es_ES';
            console.log('es_ES');
            """));
  }
}
