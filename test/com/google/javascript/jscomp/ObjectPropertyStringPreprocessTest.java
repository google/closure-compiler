/*
 * Copyright 2009 The Closure Compiler Authors.
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
 * Tests for {@link ObjectPropertyStringPreprocess}
 *
 */
public final class ObjectPropertyStringPreprocessTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new ObjectPropertyStringPreprocess(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Override
  protected void setUp() {
    super.allowExternsChanges(true);
  }

  public void testDeclaration() {
    test("goog.testing.ObjectPropertyString = function() {}",
         "JSCompiler_ObjectPropertyString = function() {}");
  }

  public void testFooBar() {
    test("new goog.testing.ObjectPropertyString(foo, 'bar')",
         "new JSCompiler_ObjectPropertyString(goog.global, foo.bar)");
  }

  public void testFooPrototypeBar() {
    test("new goog.testing.ObjectPropertyString(foo.prototype, 'bar')",
         "new JSCompiler_ObjectPropertyString(goog.global, " +
         "foo.prototype.bar)");
  }

  public void testInvalidNumArgumentsError() {
    testError("new goog.testing.ObjectPropertyString()",
        ObjectPropertyStringPreprocess.INVALID_NUM_ARGUMENTS_ERROR);
  }

  public void testQualifedNameExpectedError() {
    testError("new goog.testing.ObjectPropertyString(foo[a], 'bar')",
        ObjectPropertyStringPreprocess.QUALIFIED_NAME_EXPECTED_ERROR);
  }

  public void testStringLiteralExpectedError() {
    testError("new goog.testing.ObjectPropertyString(foo, bar)",
        ObjectPropertyStringPreprocess.STRING_LITERAL_EXPECTED_ERROR);
  }

  public void testTemplateStringError() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    testError("new goog.testing.ObjectPropertyString(foo, `bar`)",
        ObjectPropertyStringPreprocess.STRING_LITERAL_EXPECTED_ERROR);
    testError("new goog.testing.ObjectPropertyString(foo, `${A}bar`)",
        ObjectPropertyStringPreprocess.STRING_LITERAL_EXPECTED_ERROR);
    testError("new goog.testing.ObjectPropertyString(foo, `bar${A}`)",
        ObjectPropertyStringPreprocess.STRING_LITERAL_EXPECTED_ERROR);
  }

  public void testTaggedTemplateError() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    testError("new goog.testing.ObjectPropertyString(foo, tagged`bar`)",
        ObjectPropertyStringPreprocess.STRING_LITERAL_EXPECTED_ERROR);
    testError("new goog.testing.ObjectPropertyString(foo, tagged`${Foo}bar`)",
        ObjectPropertyStringPreprocess.STRING_LITERAL_EXPECTED_ERROR);
  }
}
