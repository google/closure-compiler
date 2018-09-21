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

import static com.google.javascript.jscomp.lint.CheckNullableReturn.NULLABLE_RETURN_WITH_NAME;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.DiagnosticGroups;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test case for {@link CheckNullableReturn}.
 *
 */
@RunWith(JUnit4.class)
public final class CheckNullableReturnTest extends CompilerTestCase {
  private static final String EXTERNS =
      DEFAULT_EXTERNS + "/** @constructor */ function SomeType() {}";

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckNullableReturn(compiler);
  }

  @Override
  protected CompilerOptions getOptions(CompilerOptions options) {
    super.getOptions(options);
    options.setWarningLevel(DiagnosticGroups.ANALYZER_CHECKS, CheckLevel.WARNING);
    return options;
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableTypeCheck();
    enableTranspile();
  }

  @Test
  public void testSimpleWarning() {
    testError(lines(
        "/** @return {SomeType} */",
        "function f() {",
        "  return new SomeType();",
        "}"));
  }

  @Test
  public void testNullableReturn() {
    testBodyOk("return null;");
    testBodyOk("if (a) { return null; } return {};");
    testBodyOk("switch(1) { case 12: return null; } return {};");
    testBodyOk(
        "/** @return {number} */ function f() { return 42; }; return null;");
  }

  @Test
  public void testNotNullableReturn() {
    // Empty function body. Ignore this case. The remainder of the functions in
    // this test have non-empty bodies.
    testBodyOk("");

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

  @Test
  public void testFinallyStatements() {
    testBodyOk("try { return null; } finally { return {}; }");
    testBodyOk("try { } finally { return null; }");
    testBodyOk("try { return {}; } finally { return null; }");
    testBodyOk("try { return null; } finally { return {}; }");
    testBodyError("try { } catch (e) { return null; } finally { return {}; }");
  }

  @Test
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

  @Test
  public void testKnownWhileLoop() {
    testBodyError("while (1) return {}");
    testBodyError("while (1) { if (x) { return {}; } else { return {}; }}");
    testBodyError("while (0) {} return {}");

    // Not known.
    testBodyError("while(x) { return {}; }");
  }

  @Test
  public void testTwoBranches() {
    testError(lines(
        "/** @return {SomeType} */",
        "function f() {",
        "  if (foo) {",
        "    return new SomeType();",
        "  } else {",
        "    return new SomeType();",
        "  }",
        "}"));
    testError(lines(
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

  @Test
  public void testTryCatch() {
    testError(lines(
        "/** @return {SomeType} */",
        "function f() {",
        "  try {",
        "    return new SomeType();",
        "  } catch (e) {",
        "    return new SomeType();",
        "  }",
        "}"));
    testError(lines(
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

    testBodyOk(lines(
        "try {",
        "  if (a) throw '';",
        "} catch (e) {",
        "  return null;",
        "}",
        "return {}"));

    testBodyOk(lines(
        "try {",
        "  return bar();",
        "} catch (e) {",
        "} finally { return baz(); }"));
  }

  @Test
  public void testNoExplicitReturn() {
    testError(lines(
        "/** @return {SomeType} */",
        "function f() {",
        "  if (foo) {",
        "    return new SomeType();",
        "  }",
        "}"));
  }

  @Test
  public void testNoWarningIfCanReturnNull() {
    testOk(lines(
        "/** @return {SomeType} */",
        "function f() {",
        "  if (foo) {",
        "    return new SomeType();",
        "  } else {",
        "    return null;",
        "  }",
        "}"));
  }

  @Test
  public void testNoWarningOnEmptyFunction() {
    testOk(lines(
        "/** @return {SomeType} */",
        "function f() {}"));
    testOk(lines(
        "var obj = {",
        "  /** @return {SomeType} */\n",
        "  f() {}",
        "}"));
  }

  @Test
  public void testNoWarningOnXOrNull() {
    testOk(lines(
        "/**",
        " * @param {!Array.<!Object>} arr",
        " * @return {Object}",
        " */",
        "function f4(arr) {",
        "  return arr[0] || null;",
        "}"));
    testOk(lines(
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

  @Test
  public void testNonfunctionTypeDoesntCrash() {
    enableClosurePass();
    testNoWarning(
        DEFAULT_EXTERNS,
        "goog.forwardDeclare('FunType'); /** @type {!FunType} */ (function() { return; })");
  }

  private static String createFunction(String body) {
    return "/** @return {?Object} */ function foo() {" + body + "}";
  }

  private static String createShorthandFunctionInObjLit(String body) {
    return "var obj = {/** @return {?Object} */ foo() {" + body + "}}";
  }

  private void testOk(String js) {
    testSame(externs(EXTERNS), srcs(js));
  }

  private void testError(String js) {
    test(externs(EXTERNS), srcs(js), warning(NULLABLE_RETURN_WITH_NAME));
  }

  private void testBodyOk(String body) {
    testOk(createFunction(body));
    testOk(createShorthandFunctionInObjLit(body));
  }

  private void testBodyError(String body) {
    testError(createFunction(body));
    testError(createShorthandFunctionInObjLit(body));
  }
}
