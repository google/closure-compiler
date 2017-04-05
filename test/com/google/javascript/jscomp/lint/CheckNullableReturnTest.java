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

package com.google.javascript.jscomp.lint;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.TypeICompilerTestCase;

/**
 * Test case for {@link CheckNullableReturn}.
 *
 */
public final class CheckNullableReturnTest extends TypeICompilerTestCase {
  private static final String EXTERNS =
      DEFAULT_EXTERNS + "/** @constructor */ function SomeType() {}";

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckNullableReturn(compiler);
  }

  @Override
  protected CompilerOptions getOptions(CompilerOptions options) {
    super.getOptions(options);
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    return options;
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testSimpleWarning() {
    testError(LINE_JOINER.join(
        "/** @return {SomeType} */",
        "function f() {",
        "  return new SomeType();",
        "}"));
  }

  public void testNullableReturn() {
    testBodyOk("return null;");
    testBodyOk("if (a) { return null; } return {};");
    testBodyOk("switch(1) { case 12: return null; } return {};");
    testBodyOk(
        "/** @return {number} */ function f() { return 42; }; return null;");
  }

  public void testNotNullableReturn()  {
    // Empty function body. Ignore this case. The remainder of the functions in
    // this test have non-empty bodies.
    this.mode = TypeInferenceMode.OTI_ONLY;
    testBodyOk("");
    this.mode = TypeInferenceMode.BOTH;

    // Simple case.
    testBodyError("return {};");

    // This implementation of an abstract method should not be reported.
    testBodyOk("throw new Error('Not implemented');");

    // Nested function.
    testBodyError(
        "/** @return {number} */ function f() { return 1; }; return {};");

    testBodyError("switch(1) { default: return {}; } return null;");
    testBodyError("switch(g) { case 1: return {}; default: return {}; } return null;");
  }

  public void testFinallyStatements() {
    testBodyOk("try { return null; } finally { return {}; }");
    testBodyOk("try { } finally { return null; }");
    testBodyOk("try { return {}; } finally { return null; }");
    testBodyOk("try { return null; } finally { return {}; }");
    this.mode = TypeInferenceMode.OTI_ONLY;
    testBodyError("try { } catch (e) { return null; } finally { return {}; }");
  }

  public void testKnownConditions() {
    testBodyOk("if (true) return {}; return null;");
    testBodyOk("if (true) return null; else return {};");

    testBodyOk("if (false) return {}; return null;");
    testBodyOk("if (false) return null; else return {};");

    testBodyError("if (1) return {}; return {x: 42};");
    testBodyOk("if (1) { return null; } else { return {}; }");

    testBodyOk("if (0) return {}; return null;");
    testBodyOk("if (0) { return null; } else { return {}; }");

    testBodyOk("if (3) return null; else return {};");
  }

  public void testKnownWhileLoop() {
    testBodyError("while (1) return {}");
    testBodyError("while (1) { if (x) { return {}; } else { return {}; }}");
    testBodyError("while (0) {} return {}");

    // Not known.
    this.mode = TypeInferenceMode.OTI_ONLY;
    testBodyError("while(x) { return {}; }");
  }

  public void testTwoBranches() {
    testError(LINE_JOINER.join(
        "/** @return {SomeType} */",
        "function f() {",
        "  if (foo) {",
        "    return new SomeType();",
        "  } else {",
        "    return new SomeType();",
        "  }",
        "}"));
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    testError(LINE_JOINER.join(
        "var obj = {",
        "  /** @return {SomeType} */",
        "  f() {",
        "    if (foo) {",
        "      return new SomeType();",
        "    } else {",
        "      return new SomeType();",
        "    }",
        "  }",
        "}"));
  }

  public void testTryCatch() {
    testError(LINE_JOINER.join(
        "/** @return {SomeType} */",
        "function f() {",
        "  try {",
        "    return new SomeType();",
        "  } catch (e) {",
        "    return new SomeType();",
        "  }",
        "}"));
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    testError(LINE_JOINER.join(
        "var obj = {",
        "  /** @return {SomeType} */",
        "  f() {",
        "    try {",
        "      return new SomeType();",
        "    } catch (e) {",
        "      return new SomeType();",
        "    }",
        "  }",
        "}"));

    testBodyOk(LINE_JOINER.join(
        "try {",
        "  if (a) throw '';",
        "} catch (e) {",
        "  return null;",
        "}",
        "return {}"));

    testBodyOk(LINE_JOINER.join(
        "try {",
        "  return bar();",
        "} catch (e) {",
        "} finally { return baz(); }"));
  }

  public void testNoExplicitReturn() {
    this.mode = TypeInferenceMode.OTI_ONLY;
    testError(LINE_JOINER.join(
        "/** @return {SomeType} */",
        "function f() {",
        "  if (foo) {",
        "    return new SomeType();",
        "  }",
        "}"));
  }

  public void testNoWarningIfCanReturnNull() {
    testOk(LINE_JOINER.join(
        "/** @return {SomeType} */",
        "function f() {",
        "  if (foo) {",
        "    return new SomeType();",
        "  } else {",
        "    return null;",
        "  }",
        "}"));
  }

  public void testNoWarningOnEmptyFunction() {
    this.mode = TypeInferenceMode.OTI_ONLY;
    testOk(LINE_JOINER.join(
        "/** @return {SomeType} */",
        "function f() {}"));
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    testOk(LINE_JOINER.join(
        "var obj = {",
        "  /** @return {SomeType} */\n",
        "  f() {}",
        "}"));
  }

  public void testNoWarningOnXOrNull() {
    testOk(LINE_JOINER.join(
        "/**",
        " * @param {!Array.<!Object>} arr",
        " * @return {Object}",
        " */",
        "function f4(arr) {",
        "  return arr[0] || null;",
        "}"));
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    testOk(LINE_JOINER.join(
        "var obj = {",
        "  /**",
        "   * @param {!Array.<!Object>} arr",
        "   * @return {Object}",
        "   */",
        "  f4(arr) {",
        "    return arr[0] || null;",
        "  }",
        "}"));
  }

  public void testNonfunctionTypeDoesntCrash() {
    enableClosurePass();
    test(DEFAULT_EXTERNS,
        "goog.forwardDeclare('FunType'); /** @type {!FunType} */ (function() { return; })",
        (String) null,
        null,
        null);
  }

  private static String createFunction(String body) {
    return "/** @return {?Object} */ function foo() {" + body + "}";
  }

  private static String createShorthandFunctionInObjLit(String body) {
    return "var obj = {/** @return {?Object} */ foo() {" + body + "}}";
  }

  private void testOk(String js) {
    testSame(EXTERNS, js, null);
  }

  private void testError(String js) {
    testSame(EXTERNS, js, CheckNullableReturn.NULLABLE_RETURN_WITH_NAME);
  }

  private void testBodyOk(String body) {
    testOk(createFunction(body));
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    testOk(createShorthandFunctionInObjLit(body));
  }

  private void testBodyError(String body) {
    testError(createFunction(body));
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    testError(createShorthandFunctionInObjLit(body));
  }
}
