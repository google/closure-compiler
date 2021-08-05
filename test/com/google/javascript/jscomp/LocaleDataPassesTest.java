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

import com.google.common.annotations.GwtIncompatible;
import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test which checks that replacer works correctly. */
@GwtIncompatible("Unnecessary")
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
  protected int getNumRepetitions() {
    // No longer valid on the second run.
    return 1;
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLocale(this.locale);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    switch (testMode) {
      case PROTECT_DATA:
        return new CompilerPass() {
          @Override
          public void process(Node externs, Node root) {
            final LocaleDataPasses.ExtractAndProtect extract =
                new LocaleDataPasses.ExtractAndProtect(compiler);
            extract.process(externs, root);
            compiler.setLocaleSubstitutionData(extract.getLocaleValuesDataMaps());
          }
        };
      case REPLACE_PROTECTED_DATA:
        return new CompilerPass() {
          @Override
          public void process(Node externs, Node root) {
            final LocaleDataPasses.ExtractAndProtect extract =
                new LocaleDataPasses.ExtractAndProtect(compiler);
            extract.process(externs, root);
            compiler.setLocaleSubstitutionData(extract.getLocaleValuesDataMaps());
            final LocaleDataPasses.LocaleSubstitutions subs =
                new LocaleDataPasses.LocaleSubstitutions(
                    compiler, compiler.getOptions().locale, compiler.getLocaleSubstitutionData());
            subs.process(externs, root);
          }
        };
    }
    throw new UnsupportedOperationException("unexpected testMode: " + testMode);
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

  /**
   * Test for errors that are detected before attempting to look up the messages in the bundle.
   *
   * @param originalJs The original, input JS code
   * @param diagnosticType expected error
   */
  private void multiTestProtectionError(
      String originalJs, DiagnosticType diagnosticType, String description) {
    // The PROTECT_DATA mode needs to add externs for the protection functions.
    allowExternsChanges();
    testMode = TestMode.PROTECT_DATA;
    testError(originalJs, diagnosticType, description);
  }

  @Test
  public void testMinimalSuccessProvide() {
    multiTest(
        lines(
            "/**",
            " * @fileoverview",
            " * @localeFile",
            " */",
            "goog.provide('i18n.Obj');",
            "",
            "/** @localeObject */",
            "i18n.Obj_en = {",
            "  val1: /** @localeValue */ 'en val1',",
            "  val2: /** @localeValue */ 'en val2',",
            "};",
            "/** @localeObject */",
            "i18n.Obj_af = {",
            "  val1: /** @localeValue */ 'af val1',",
            "  val2: /** @localeValue */ undefined,",
            "};",
            "/** @localeSelect */",
            "switch (goog.LOCALE) {",
            "  case 'af':",
            "    i18n.Obj = i18n.Obj_af;",
            "    break;",
            "  case 'en':",
            "    i18n.Obj = i18n.Obj_en",
            "    break;",
            "}",
            ""),
        lines(
            "/**",
            " * @fileoverview",
            " * @localeFile",
            " */",
            "goog.provide('i18n.Obj');",
            "",
            "/** @localeObject */",
            "i18n.Obj_en = {",
            "  val1: /** @localeValue */ 'en val1',",
            "  val2: /** @localeValue */ 'en val2',",
            "};",
            "/** @localeObject */",
            "i18n.Obj_af = {",
            "  val1: /** @localeValue */ 'af val1',",
            "  val2: /** @localeValue */ undefined,",
            "};",
            "",
            "i18n.Obj = {",
            "  val1: __JSC_LOCALE_VALUE__(0),",
            "  val2: __JSC_LOCALE_VALUE__(1),",
            "};",
            ""),
        new LocaleResult(
            "en",
            lines(
                "/**",
                " * @fileoverview",
                " * @localeFile",
                " */",
                "goog.provide('i18n.Obj');",
                "",
                "/** @localeObject */",
                "i18n.Obj_en = {",
                "  val1: /** @localeValue */ 'en val1',",
                "  val2: /** @localeValue */ 'en val2',",
                "};",
                "/** @localeObject */",
                "i18n.Obj_af = {",
                "  val1: /** @localeValue */ 'af val1',",
                "  val2: /** @localeValue */ undefined,",
                "};",
                "",
                "i18n.Obj = {",
                "  val1: /** @localeValue */ 'en val1',",
                "  val2: /** @localeValue */ 'en val2',",
                "};",
                "")),
        new LocaleResult(
            "af",
            lines(
                "/**",
                " * @fileoverview",
                " * @localeFile",
                " */",
                "goog.provide('i18n.Obj');",
                "",
                "/** @localeObject */",
                "i18n.Obj_en = {",
                "  val1: /** @localeValue */ 'en val1',",
                "  val2: /** @localeValue */ 'en val2',",
                "};",
                "/** @localeObject */",
                "i18n.Obj_af = {",
                "  val1: /** @localeValue */ 'af val1',",
                "  val2: /** @localeValue */ undefined,",
                "};",
                "",
                "i18n.Obj = {",
                "  val1: /** @localeValue */ 'af val1',",
                "  val2: /** @localeValue */ undefined,",
                "};",
                "")));
  }

  @Test
  public void testMinimalSuccessProvideExt() {
    multiTest(
        srcs(
            SourceFile.fromCode(
                "a.js",
                lines(
                    "/**",
                    " * @fileoverview",
                    " * @localeFile",
                    " */",
                    "goog.provide('i18n.Obj');",
                    "",
                    "/** @localeObject */",
                    "i18n.Obj_en = {",
                    "  val1: /** @localeValue */ 'en val1',",
                    "  val2: /** @localeValue */ 'en val2',",
                    "};",
                    "/** @localeObject */",
                    "i18n.Obj_af = {",
                    "  val1: /** @localeValue */ 'af val1',",
                    "  val2: /** @localeValue */ undefined,",
                    "};",
                    "/** @localeSelect */",
                    "switch (goog.LOCALE) {",
                    "  case 'af':",
                    "    i18n.Obj = i18n.Obj_af;",
                    "    break;",
                    "  case 'en':",
                    "    i18n.Obj = i18n.Obj_en;",
                    "    break;",
                    "}",
                    "")),
            SourceFile.fromCode(
                "aext.js",
                lines(
                    "/**",
                    " * @fileoverview",
                    " * @localeFile",
                    " */",
                    "goog.provide('i18n.ObjExt');",
                    "goog.require('i18n.Obj');",
                    "",
                    "/** @localeObject */",
                    "i18n.Obj_xx = {",
                    "  val1: /** @localeValue */ 'xx val1',",
                    "  val2: /** @localeValue */ 'xx val2',",
                    "};",
                    "/** @localeObject */",
                    "i18n.Obj_yy_ZZ = {",
                    "  val1: /** @localeValue */ 'yy val1',",
                    "  val2: /** @localeValue */ 'yy val2',",
                    "};",
                    "/** @localeSelect */",
                    "switch (goog.LOCALE) {",
                    "  case 'xx':",
                    "    i18n.Obj = i18n.Obj_xx;",
                    "    break;",
                    "  case 'yy':",
                    "    i18n.Obj = i18n.Obj_yy;",
                    "    break;",
                    "}"))),
        expected(
            lines(
                "/**",
                " * @fileoverview",
                " * @localeFile",
                " */",
                "goog.provide('i18n.Obj');",
                "",
                "/** @localeObject */",
                "i18n.Obj_en = {",
                "  val1: /** @localeValue */ 'en val1',",
                "  val2: /** @localeValue */ 'en val2',",
                "};",
                "/** @localeObject */",
                "i18n.Obj_af = {",
                "  val1: /** @localeValue */ 'af val1',",
                "  val2: /** @localeValue */ undefined,",
                "};",
                "",
                "i18n.Obj = {",
                "  val1: __JSC_LOCALE_VALUE__(0),",
                "  val2: __JSC_LOCALE_VALUE__(1),",
                "};"),
            lines(
                "/**",
                " * @fileoverview",
                " * @localeFile",
                " */",
                "/** @const */ i18n.ObjExt = {};",
                "/** @localeObject */",
                "i18n.Obj_xx = {",
                "  val1: /** @localeValue */ 'xx val1',",
                "  val2: /** @localeValue */ 'xx val2',",
                "};",
                "/** @localeObject */",
                "i18n.Obj_yy_ZZ = {",
                "  val1: /** @localeValue */ 'yy val1',",
                "  val2: /** @localeValue */ 'yy val2',",
                "};")),
        new LocaleResult(
            "en",
            expected(
                lines(
                    "/**",
                    " * @fileoverview",
                    " * @localeFile",
                    " */",
                    "goog.provide('i18n.Obj');",
                    "",
                    "/** @localeObject */",
                    "i18n.Obj_en = {",
                    "  val1: /** @localeValue */ 'en val1',",
                    "  val2: /** @localeValue */ 'en val2',",
                    "};",
                    "/** @localeObject */",
                    "i18n.Obj_af = {",
                    "  val1: /** @localeValue */ 'af val1',",
                    "  val2: /** @localeValue */ undefined,",
                    "};",
                    "",
                    "i18n.Obj = {",
                    "  val1: /** @localeValue */ 'en val1',",
                    "  val2: /** @localeValue */ 'en val2',",
                    "};"),
                lines(
                    "/**",
                    " * @fileoverview",
                    " * @localeFile",
                    " */",
                    "/** @const */ i18n.ObjExt = {};",
                    "/** @localeObject */",
                    "i18n.Obj_xx = {",
                    "  val1: /** @localeValue */ 'xx val1',",
                    "  val2: /** @localeValue */ 'xx val2',",
                    "};",
                    "/** @localeObject */",
                    "i18n.Obj_yy_ZZ = {",
                    "  val1: /** @localeValue */ 'yy val1',",
                    "  val2: /** @localeValue */ 'yy val2',",
                    "};"))) //
        ,
        new LocaleResult(
            "yy_ZZ",
            expected(
                lines(
                    "/**",
                    " * @fileoverview",
                    " * @localeFile",
                    " */",
                    "goog.provide('i18n.Obj');",
                    "",
                    "/** @localeObject */",
                    "i18n.Obj_en = {",
                    "  val1: /** @localeValue */ 'en val1',",
                    "  val2: /** @localeValue */ 'en val2',",
                    "};",
                    "/** @localeObject */",
                    "i18n.Obj_af = {",
                    "  val1: /** @localeValue */ 'af val1',",
                    "  val2: /** @localeValue */ undefined,",
                    "};",
                    "",
                    "i18n.Obj = {",
                    "  val1: /** @localeValue */ 'yy val1',",
                    "  val2: /** @localeValue */ 'yy val2',",
                    "};"),
                lines(
                    "/**",
                    " * @fileoverview",
                    " * @localeFile",
                    " */",
                    "/** @const */ i18n.ObjExt = {};",
                    "/** @localeObject */",
                    "i18n.Obj_xx = {",
                    "  val1: /** @localeValue */ 'xx val1',",
                    "  val2: /** @localeValue */ 'xx val2',",
                    "};",
                    "/** @localeObject */",
                    "i18n.Obj_yy_ZZ = {",
                    "  val1: /** @localeValue */ 'yy val1',",
                    "  val2: /** @localeValue */ 'yy val2',",
                    "};"))) //
        );
  }

  @Test
  public void testMinimalSuccessModule() {
    multiTest(
        lines(
            "/**",
            " * @fileoverview",
            " * @localeFile",
            " */",
            "goog.module('i18n.Obj');",
            "",
            "/** @localeObject */",
            "let Obj_en = {",
            "  val1: /** @localeValue */ 'en val1',",
            "  val2: /** @localeValue */ 'en val2',",
            "};",
            "/** @localeObject */",
            "let Obj_af = {",
            "  val1: /** @localeValue */ 'af val1',",
            "  val2: /** @localeValue */ 'af val2',",
            "};",
            "let Obj;",
            "/** @localeSelect */",
            "switch (goog.LOCALE) {",
            "  case 'af':",
            "    Obj = Obj_af;",
            "    break;",
            "  case 'en':",
            "    Obj = Obj_en;",
            "    break;",
            "  default:",
            "    Obj = Obj_en;",
            "    break;",
            "}",
            "exports.Obj_en = Obj_en",
            "exports.Obj_af = Obj_af",
            "exports.Obj = Obj;",
            ""),
        lines(
            "/**",
            " * @fileoverview",
            " * @localeFile",
            " */",
            "goog.module('i18n.Obj');",
            "",
            "/** @localeObject */",
            "let Obj_en = {",
            "  val1: /** @localeValue */ 'en val1',",
            "  val2: /** @localeValue */ 'en val2',",
            "};",
            "/** @localeObject */",
            "let Obj_af = {",
            "  val1: /** @localeValue */ 'af val1',",
            "  val2: /** @localeValue */ 'af val2',",
            "};",
            "let Obj;",
            "Obj = {",
            "  val1: __JSC_LOCALE_VALUE__(0),",
            "  val2: __JSC_LOCALE_VALUE__(1),",
            "};",
            "exports.Obj_en = Obj_en",
            "exports.Obj_af = Obj_af",
            "exports.Obj = Obj;",
            ""),
        new LocaleResult(
            "en",
            lines(
                "/**",
                " * @fileoverview",
                " * @localeFile",
                " */",
                "goog.module('i18n.Obj');",
                "",
                "/** @localeObject */",
                "let Obj_en = {",
                "  val1: /** @localeValue */ 'en val1',",
                "  val2: /** @localeValue */ 'en val2',",
                "};",
                "/** @localeObject */",
                "let Obj_af = {",
                "  val1: /** @localeValue */ 'af val1',",
                "  val2: /** @localeValue */ 'af val2',",
                "};",
                "let Obj;",
                "Obj = {",
                "  val1: /** @localeValue */ 'en val1',",
                "  val2: /** @localeValue */ 'en val2',",
                "};",
                "exports.Obj_en = Obj_en",
                "exports.Obj_af = Obj_af",
                "exports.Obj = Obj;",
                "")),
        new LocaleResult(
            "af",
            lines(
                "/**",
                " * @fileoverview",
                " * @localeFile",
                " */",
                "goog.module('i18n.Obj');",
                "",
                "/** @localeObject */",
                "let Obj_en = {",
                "  val1: /** @localeValue */ 'en val1',",
                "  val2: /** @localeValue */ 'en val2',",
                "};",
                "/** @localeObject */",
                "let Obj_af = {",
                "  val1: /** @localeValue */ 'af val1',",
                "  val2: /** @localeValue */ 'af val2',",
                "};",
                "let Obj;",
                "Obj = {",
                "  val1: /** @localeValue */ 'af val1',",
                "  val2: /** @localeValue */ 'af val2',",
                "};",
                "exports.Obj_en = Obj_en",
                "exports.Obj_af = Obj_af",
                "exports.Obj = Obj;",
                "")));
  }

  @Test
  public void testSuccessWithMultiModuleRenaming() {
    // force line break
    // force line break
    // force line break
    multiTest(
        srcs( // force line break
            lines(
                "/**",
                " * @fileoverview",
                " * @localeFile",
                " */",
                "goog.module('i18n.Obj');",
                "",
                "/** @localeObject */",
                "let Obj_en = {",
                "  val1: /** @localeValue */ 'en val1',",
                "  val2: /** @localeValue */ 'en val2',",
                "};",
                "/** @localeObject */",
                "let Obj_af = {",
                "  val1: /** @localeValue */ 'af val1',",
                "  val2: /** @localeValue */ 'af val2',",
                "};",
                "let Obj;",
                "/** @localeSelect */",
                "switch (goog.LOCALE) {",
                "  case 'af':",
                "    Obj = Obj_af;",
                "    break;",
                "  case 'en':",
                "    Obj = Obj_en;",
                "    break;",
                "  default:",
                "    Obj = Obj_en;",
                "    break;",
                "}",
                "exports.Obj_en = Obj_en",
                "exports.Obj_af = Obj_af",
                "exports.Obj = Obj;",
                ""),
            lines(
                "/**",
                " * @fileoverview",
                " * @localeFile",
                " */",
                "goog.module('i18n.Obj2');",
                "",
                "/** @localeObject */",
                "let Obj_en = {",
                "  val1: /** @localeValue */ 'en val1',",
                "  val2: /** @localeValue */ 'en val2',",
                "};",
                "/** @localeObject */",
                "let Obj_af = {",
                "  val1: /** @localeValue */ 'af val1',",
                "  val2: /** @localeValue */ 'af val2',",
                "};",
                "let Obj;",
                "/** @localeSelect */",
                "switch (goog.LOCALE) {",
                "  case 'af':",
                "    Obj = Obj_af;",
                "    break;",
                "  case 'en':",
                "    Obj = Obj_en;",
                "    break;",
                "  default:",
                "    Obj = Obj_en;",
                "    break;",
                "}",
                "exports.Obj_en = Obj_en",
                "exports.Obj_af = Obj_af",
                "exports.Obj = Obj;",
                "")),
        expected( // force line break
            lines(
                "/**",
                " * @fileoverview",
                " * @localeFile",
                " */",
                "goog.module('i18n.Obj');",
                "",
                "/** @localeObject */",
                "let Obj_en = {",
                "  val1: /** @localeValue */ 'en val1',",
                "  val2: /** @localeValue */ 'en val2',",
                "};",
                "/** @localeObject */",
                "let Obj_af = {",
                "  val1: /** @localeValue */ 'af val1',",
                "  val2: /** @localeValue */ 'af val2',",
                "};",
                "let Obj;",
                "Obj = {",
                "  val1: __JSC_LOCALE_VALUE__(0),",
                "  val2: __JSC_LOCALE_VALUE__(1),",
                "};",
                "exports.Obj_en = Obj_en",
                "exports.Obj_af = Obj_af",
                "exports.Obj = Obj;",
                ""),
            lines(
                "/**",
                " * @fileoverview",
                " * @localeFile",
                " */",
                "goog.module('i18n.Obj2');",
                "",
                "/** @localeObject */",
                "let Obj_en = {",
                "  val1: /** @localeValue */ 'en val1',",
                "  val2: /** @localeValue */ 'en val2',",
                "};",
                "/** @localeObject */",
                "let Obj_af = {",
                "  val1: /** @localeValue */ 'af val1',",
                "  val2: /** @localeValue */ 'af val2',",
                "};",
                "let Obj;",
                "Obj = {",
                "  val1: __JSC_LOCALE_VALUE__(2),",
                "  val2: __JSC_LOCALE_VALUE__(3),",
                "};",
                "exports.Obj_en = Obj_en",
                "exports.Obj_af = Obj_af",
                "exports.Obj = Obj;",
                "")),
        new LocaleResult(
            "af",
            expected( // force line break
                lines(
                    "/**",
                    " * @fileoverview",
                    " * @localeFile",
                    " */",
                    "goog.module('i18n.Obj');",
                    "",
                    "/** @localeObject */",
                    "let Obj_en = {",
                    "  val1: /** @localeValue */ 'en val1',",
                    "  val2: /** @localeValue */ 'en val2',",
                    "};",
                    "/** @localeObject */",
                    "let Obj_af = {",
                    "  val1: /** @localeValue */ 'af val1',",
                    "  val2: /** @localeValue */ 'af val2',",
                    "};",
                    "let Obj;",
                    "Obj = {",
                    "  val1: /** @localeValue */ 'af val1',",
                    "  val2: /** @localeValue */ 'af val2',",
                    "};",
                    "exports.Obj_en = Obj_en",
                    "exports.Obj_af = Obj_af",
                    "exports.Obj = Obj;",
                    ""),
                lines(
                    "/**",
                    " * @fileoverview",
                    " * @localeFile",
                    " */",
                    "goog.module('i18n.Obj2');",
                    "",
                    "/** @localeObject */",
                    "let Obj_en = {",
                    "  val1: /** @localeValue */ 'en val1',",
                    "  val2: /** @localeValue */ 'en val2',",
                    "};",
                    "/** @localeObject */",
                    "let Obj_af = {",
                    "  val1: /** @localeValue */ 'af val1',",
                    "  val2: /** @localeValue */ 'af val2',",
                    "};",
                    "let Obj;",
                    "Obj = {",
                    "  val1: /** @localeValue */ 'af val1',",
                    "  val2: /** @localeValue */ 'af val2',",
                    "};",
                    "exports.Obj_en = Obj_en",
                    "exports.Obj_af = Obj_af",
                    "exports.Obj = Obj;",
                    ""))));
  }

  @Test
  public void testMinimalSuccessFallbackToDefaultLocale() {
    multiTest(
        lines(
            "/**",
            " * @fileoverview",
            " * @localeFile",
            " */",
            "goog.provide('i18n.Obj');",
            "",
            "/** @localeObject */",
            "i18n.Obj_en = {",
            "  val1: /** @localeValue */ 'en val1',",
            "  val2: /** @localeValue */ 'en val2',",
            "};",
            "/** @localeObject */",
            "i18n.Obj_af = {",
            "  val1: /** @localeValue */ 'af val1',",
            "  val2: /** @localeValue */ 'af val2',",
            "};",
            "/** @localeSelect */",
            "switch (goog.LOCALE) {",
            "  case 'af':",
            "    i18n.Obj = i18n.Obj_af;",
            "    break;",
            "  case 'en':",
            "    i18n.Obj = i18n.Obj_en",
            "    break;",
            "}",
            ""),
        lines(
            "/**",
            " * @fileoverview",
            " * @localeFile",
            " */",
            "goog.provide('i18n.Obj');",
            "",
            "/** @localeObject */",
            "i18n.Obj_en = {",
            "  val1: /** @localeValue */ 'en val1',",
            "  val2: /** @localeValue */ 'en val2',",
            "};",
            "/** @localeObject */",
            "i18n.Obj_af = {",
            "  val1: /** @localeValue */ 'af val1',",
            "  val2: /** @localeValue */ 'af val2',",
            "};",
            "",
            "i18n.Obj = {",
            "  val1: __JSC_LOCALE_VALUE__(0),",
            "  val2: __JSC_LOCALE_VALUE__(1),",
            "};",
            ""),
        new LocaleResult(
            "unk_Loc_ale", // some locale without a definition
            lines(
                "/**",
                " * @fileoverview",
                " * @localeFile",
                " */",
                "goog.provide('i18n.Obj');",
                "",
                "/** @localeObject */",
                "i18n.Obj_en = {",
                "  val1: /** @localeValue */ 'en val1',",
                "  val2: /** @localeValue */ 'en val2',",
                "};",
                "/** @localeObject */",
                "i18n.Obj_af = {",
                "  val1: /** @localeValue */ 'af val1',",
                "  val2: /** @localeValue */ 'af val2',",
                "};",
                "",
                "i18n.Obj = {",
                "  val1: /** @localeValue */ 'en val1',",
                "  val2: /** @localeValue */ 'en val2',",
                "};",
                "")));
  }

  @Test
  public void testMinimalSuccessWithAlias() {
    multiTest(
        lines(
            "/**",
            " * @fileoverview",
            " * @localeFile",
            " */",
            "goog.provide('i18n.Obj');",
            "",
            "/** @localeObject */",
            "i18n.Obj_en = {",
            "  val1: /** @localeValue */ 'en val1',",
            "  val2: /** @localeValue */ 'en val2',",
            "};",
            "/** @localeObject */",
            "i18n.Obj_af = i18n.Obj_en;",
            "/** @localeSelect */",
            "switch (goog.LOCALE) {",
            "  case 'af':",
            "    i18n.Obj = i18n.Obj_af;",
            "    break;",
            "  case 'en':",
            "    i18n.Obj = i18n.Obj_en",
            "    break;",
            "}",
            ""),
        lines(
            "/**",
            " * @fileoverview",
            " * @localeFile",
            " */",
            "goog.provide('i18n.Obj');",
            "",
            "/** @localeObject */",
            "i18n.Obj_en = {",
            "  val1: /** @localeValue */ 'en val1',",
            "  val2: /** @localeValue */ 'en val2',",
            "};",
            "/** @localeObject */",
            "i18n.Obj_af = i18n.Obj_en",
            "",
            "i18n.Obj = {",
            "  val1: __JSC_LOCALE_VALUE__(0),",
            "  val2: __JSC_LOCALE_VALUE__(1),",
            "};",
            ""),
        new LocaleResult(
            "en",
            lines(
                "/**",
                " * @fileoverview",
                " * @localeFile",
                " */",
                "goog.provide('i18n.Obj');",
                "",
                "/** @localeObject */",
                "i18n.Obj_en = {",
                "  val1: /** @localeValue */ 'en val1',",
                "  val2: /** @localeValue */ 'en val2',",
                "};",
                "/** @localeObject */",
                "i18n.Obj_af = i18n.Obj_en;",
                "",
                "i18n.Obj = {",
                "  val1: /** @localeValue */ 'en val1',",
                "  val2: /** @localeValue */ 'en val2',",
                "};",
                "")),
        new LocaleResult(
            "af",
            lines(
                "/**",
                " * @fileoverview",
                " * @localeFile",
                " */",
                "goog.provide('i18n.Obj');",
                "",
                "/** @localeObject */",
                "i18n.Obj_en = {",
                "  val1: /** @localeValue */ 'en val1',",
                "  val2: /** @localeValue */ 'en val2',",
                "};",
                "/** @localeObject */",
                "i18n.Obj_af = i18n.Obj_en;",
                "",
                "i18n.Obj = {",
                "  val1: /** @localeValue */ 'en val1',",
                "  val2: /** @localeValue */ 'en val2',",
                "};",
                "")));
  }

  @Test
  public void testMinimalErrorMissingDefaultLocale() {
    multiTestProtectionError(
        lines(
            "/**",
            " * @fileoverview",
            " * @localeFile",
            " */",
            "goog.provide('i18n.Obj');",
            "",
            "/** @localeObject */",
            "i18n.Obj_af = {", // NOTE: 'en' is not defined in this @localFile
            "  val1: /** @localeValue */ 'af val1',",
            "};",
            "/** @localeSelect */",
            "switch (goog.LOCALE) {",
            "  case 'af':",
            "    i18n.Obj = i18n.Obj_af;",
            "    break;",
            "}",
            ""),
        LocaleDataPasses.LOCALE_FILE_MALFORMED,
        "Malformed locale data file. Missing default locale definition 'en'");
  }

  @Test
  public void testMinimalErrorMissingSelect() {
    multiTestProtectionError(
        lines(
            "/**",
            " * @fileoverview",
            " * @localeFile",
            " */",
            "goog.provide('i18n.Obj');",
            "",
            "/** @localeObject */",
            "i18n.Obj_en = {",
            "  val1: /** @localeValue */ 'af val1',",
            "};",
            ""),
        LocaleDataPasses.LOCALE_FILE_MALFORMED,
        "Malformed locale data file. Missing or misplaced @localeSelect");
  }

  @Test
  public void testMinimalErrorValuesMismatch() {
    multiTestProtectionError(
        lines(
            "/**",
            " * @fileoverview",
            " * @localeFile",
            " */",
            "goog.provide('i18n.Obj');",
            "",
            "/** @localeObject */",
            "i18n.Obj_en = {",
            "  val1: /** @localeValue */ 'en val1',",
            "};",
            "/** @localeObject */",
            "i18n.Obj_af = {",
            "  val1: { xxx: /** @localeValue */ 'af val2' },", // NOTE: mismatch here with Obj_en
            "};",
            "/** @localeSelect */",
            "switch (goog.LOCALE) {",
            "  case 'af':",
            "    i18n.Obj = i18n.Obj_af;",
            "    break;",
            "}",
            ""),
        LocaleDataPasses.LOCALE_FILE_MALFORMED,
        "Malformed locale data file. Expected @localeValue");
  }

  @Test
  public void testMinimalErrorValuesMismatch2() {
    multiTestProtectionError(
        lines(
            "/**",
            " * @fileoverview",
            " * @localeFile",
            " */",
            "goog.provide('i18n.Obj');",
            "",
            "/** @localeObject */",
            "i18n.Obj_en = {",
            "  val1: { inner: /** @localeValue */ 'en val1' },",
            "};",
            "/** @localeObject */",
            "i18n.Obj_af = {",
            "  val1: /** @localeValue */ { inner: 'af val2' },", // NOTE: mismatch here with Obj_en
            "};",
            "/** @localeSelect */",
            "switch (goog.LOCALE) {",
            "  case 'af':",
            "    i18n.Obj = i18n.Obj_af;",
            "    break;",
            "}",
            ""),
        LocaleDataPasses.LOCALE_FILE_MALFORMED,
        "Malformed locale data file. Mismatch between locales: unexpected @localeValue");
  }

  @Test
  public void testMinimalErrorValuesMismatch3() {
    multiTestProtectionError(
        lines(
            "/**",
            " * @fileoverview",
            " * @localeFile",
            " */",
            "goog.provide('i18n.Obj');",
            "",
            "/** @localeObject */",
            "i18n.Obj_en = {",
            "  val1: [ /** @localeValue */ 'en val1', [] ],",
            "};",
            "/** @localeObject */",
            "i18n.Obj_af = {",
            "  val1: [ /** @localeValue */ 'en val1' ],", // NOTE: mismatch here with Obj_en
            "};",
            "/** @localeSelect */",
            "switch (goog.LOCALE) {",
            "  case 'af':",
            "    i18n.Obj = i18n.Obj_af;",
            "    break;",
            "}",
            ""),
        LocaleDataPasses.LOCALE_FILE_MALFORMED,
        "Malformed locale data file. Missing or unexpected expressions. Expected 2 but found 1");
  }

  @Test
  public void testMinimalErrorStructureMismatch() {
    multiTestProtectionError(
        lines(
            "/**",
            " * @fileoverview",
            " * @localeFile",
            " */",
            "goog.provide('i18n.Obj');",
            "",
            "/** @localeObject */",
            "i18n.Obj_en = {",
            "  val1: { xxx: /** @localeValue */ 'en val1'} ,",
            "};",
            "/** @localeObject */",
            "i18n.Obj_af = {",
            "  val1: /** @localeValue */ 'af val2',", // NOTE: mismatch here with Obj_en
            "};",
            "/** @localeSelect */",
            "switch (goog.LOCALE) {",
            "  case 'af':",
            "    i18n.Obj = i18n.Obj_af;",
            "    break;",
            "}",
            ""),
        LocaleDataPasses.LOCALE_FILE_MALFORMED,
        "Malformed locale data file. Expected OBJECTLIT");
  }

  @Test
  public void testMinimalBadLocaleId() {
    multiTestProtectionError(
        lines(
            "/**",
            " * @fileoverview",
            " * @localeFile",
            " */",
            "goog.provide('i18n.Obj');",
            "",
            "/** @localeObject */",
            "i18n.Obj_en = {",
            "  val1: /** @localeValue */ 'en val1',",
            "};",
            "/** @localeObject */",
            "i18n.Obj_001 = {",
            "  val1: /** @localeValue */ '001 val1',",
            "};",
            "/** @localeSelect */",
            "switch (goog.LOCALE) {",
            "  case 'af':",
            "    i18n.Obj = i18n.Obj_af;",
            "    break;",
            "}",
            ""),
        LocaleDataPasses.LOCALE_FILE_MALFORMED,
        "Malformed locale data file. Unexpected locale id: 001");
  }

  @Test
  public void testMinimalDuplicateLocale() {
    multiTestProtectionError(
        lines(
            "/**",
            " * @fileoverview",
            " * @localeFile",
            " */",
            "goog.provide('i18n.Obj');",
            "",
            "/** @localeObject */",
            "i18n.Obj_en = {",
            "  val1: /** @localeValue */ 'en val1',",
            "};",
            "/** @localeObject */",
            "i18n.Obj_af = {",
            "  val1: /** @localeValue */ 'af val1',",
            "};",
            "/** @localeObject */",
            "i18n.Obj_af = {",
            "  val1: /** @localeValue */ 'af val1',",
            "};",
            "/** @localeSelect */",
            "switch (goog.LOCALE) {",
            "  case 'af':",
            "    i18n.Obj = i18n.Obj_af;",
            "    break;",
            "}",
            ""),
        LocaleDataPasses.LOCALE_FILE_MALFORMED,
        "Malformed locale data file. Duplicate locale definition: af");
  }

  @Test
  public void testMinimalDuplicateSwitch() {
    multiTestProtectionError(
        lines(
            "/**",
            " * @fileoverview",
            " * @localeFile",
            " */",
            "goog.provide('i18n.Obj');",
            "",
            "/** @localeObject */",
            "i18n.Obj_en = {",
            "  val1: /** @localeValue */ 'en val1',",
            "};",
            "/** @localeSelect */",
            "switch (goog.LOCALE) {",
            "  case 'en':",
            "    i18n.Obj = i18n.Obj_en;",
            "    break;",
            "}",
            "/** @localeSelect */",
            "switch (goog.LOCALE) {",
            "  case 'en':",
            "    i18n.Obj = i18n.Obj_en;",
            "    break;",
            "}",
            ""),
        LocaleDataPasses.LOCALE_FILE_MALFORMED,
        "Malformed locale data file. Duplicate switch");
  }

  @Test
  public void testMinimalMalformedSwitchCaseMissingBreak() {
    multiTestProtectionError(
        lines(
            "/**",
            " * @fileoverview",
            " * @localeFile",
            " */",
            "goog.provide('i18n.Obj');",
            "",
            "/** @localeObject */",
            "i18n.Obj_en = {",
            "  val1: /** @localeValue */ 'en val1',",
            "};",
            "/** @localeSelect */",
            "switch (goog.LOCALE) {",
            "  case 'en':",
            "    i18n.Obj = i18n.Obj_en;",
            "}",
            ""),
        LocaleDataPasses.LOCALE_FILE_MALFORMED,
        "Malformed locale data file. Missing break");
  }

  @Test
  public void testMinimalMalformedSwitchCaseExtraStatement() {
    multiTestProtectionError(
        lines(
            "/**",
            " * @fileoverview",
            " * @localeFile",
            " */",
            "goog.provide('i18n.Obj');",
            "",
            "/** @localeObject */",
            "i18n.Obj_en = {",
            "  val1: /** @localeValue */ 'en val1',",
            "};",
            "/** @localeSelect */",
            "switch (goog.LOCALE) {",
            "  case 'en':",
            "    i18n.Obj = i18n.Obj_en;",
            "    alert('haha');", // extra statement
            "    break;",
            "}",
            ""),
        LocaleDataPasses.LOCALE_FILE_MALFORMED,
        "Malformed locale data file. Unexpected statements");
  }

  @Test
  public void testMinimalMalformedSwitchCaseMissingAssignment() {
    multiTestProtectionError(
        lines(
            "/**",
            " * @fileoverview",
            " * @localeFile",
            " */",
            "goog.provide('i18n.Obj');",
            "",
            "/** @localeObject */",
            "i18n.Obj_en = {",
            "  val1: /** @localeValue */ 'en val1',",
            "};",
            "/** @localeSelect */",
            "switch (goog.LOCALE) {",
            "  case 'en':",
            "    alert('haha');", // no assignment
            "    break;",
            "}",
            ""),
        LocaleDataPasses.LOCALE_FILE_MALFORMED,
        "Malformed locale data file. Missing assignment");
  }

  @Test
  public void testMinimalMalformedSwitchCaseBadAssignmentTarget() {
    multiTestProtectionError(
        lines(
            "/**",
            " * @fileoverview",
            " * @localeFile",
            " */",
            "goog.provide('i18n.Obj');",
            "",
            "/** @localeObject */",
            "i18n.Obj_en = {",
            "  val1: /** @localeValue */ 'en val1',",
            "};",
            "/** @localeSelect */",
            "switch (goog.LOCALE) {",
            "  case 'en':",
            "    i18n['Obj'] = i18n.Obj_en;", // unexpected assignment target
            "    break;",
            "}",
            ""),
        LocaleDataPasses.LOCALE_FILE_MALFORMED,
        "Malformed locale data file. Unexpected assignment target");
  }

  @Test
  public void testMinimalInvalidObj() {
    multiTestProtectionError(
        lines(
            "/**",
            " * @fileoverview",
            " * @localeFile",
            " */",
            "goog.provide('i18n.Obj');",
            "",
            "/** @localeObject */",
            "i18n.Obj_en = {",
            "  val1: /** @localeValue */ 'en val1',",
            "};",
            "",
            "/** @localeObject */",
            "i18n.Obj_af = [{",
            "  val1: /** @localeValue */ 'en val1',",
            "}];",
            "",
            "/** @localeSelect */",
            "switch (goog.LOCALE) {",
            "  case 'en':",
            "    i18n.Obj = i18n.Obj_af;",
            "    break;",
            "}",
            ""),
        LocaleDataPasses.LOCALE_FILE_MALFORMED,
        "Malformed locale data file. Object literal or alias expected");
  }

  @Test
  public void testMinimalInvalidGoogLocaleRef() {
    multiTestProtectionError(
        lines(
            "/**",
            " * @fileoverview", // no @localeFile
            " */",
            "goog.provide('some.Obj');",
            "",
            "console.log(goog.LOCALE);",
            ""),
        LocaleDataPasses.UNEXPECTED_GOOG_LOCALE,
        "`goog.LOCALE` appears in a file lacking `@localeFile`.");
  }
}
