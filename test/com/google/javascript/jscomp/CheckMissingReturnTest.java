/*
 * Copyright 2008 The Closure Compiler Authors.
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

/**
 * Tests for {@link CheckMissingReturn}.
 *
 */
@RunWith(JUnit4.class)
public final class CheckMissingReturnTest extends CompilerTestCase {

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableTypeCheck();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CombinedCompilerPass(compiler, new CheckMissingReturn(compiler));
  }

  @Test
  public void testMissingReturn() {
    // Requires control flow analysis.
    testMissing("if (a) { return 1; }");

    // Switch statement.
    testMissing("switch(1) { case 12: return 5; }");

    // Test try catch finally.
    testMissing("try { foo() } catch (e) { return 5; } finally { }");

    // Nested scope.
    testMissing("/** @return {number} */ function f() { var x; }; return 1;");
    testMissing("/** @return {number} */ function f() { return 1; };");
  }

  @Test
  public void testReturnNotMissing() {
    // Empty function body. Ignore this case. The remainder of the functions in
    // this test have non-empty bodies.
    testNotMissing("");

    // Simple cases.
    testSame("function f() { var x; }");
    testNotMissing("return 1;");

    // Returning void and undefined.
    testNotMissing("void", "var x;");
    testNotMissing("undefined", "var x;");

    // Returning a union that includes void or undefined.
    testNotMissing("number|undefined", "var x;");
    testNotMissing("number|void", "var x;");
    testNotMissing("*", "var x;");

    // Test try catch finally.
    testNotMissing("try { return foo() } catch (e) { } finally { }");
    testNotMissing("try {throw e;} catch (e) { return foo() } finally { }");
    testNotMissing("try {} catch (e) {} finally {return foo()};");

    // Nested function.
    testNotMissing(
        "/** @return {number} */ function f() { return 1; }; return 1;");

    // Strange tests that come up when reviewing closure code.
    testNotMissing("try { return 12; } finally { return 62; }");
    testNotMissing("try { } finally { return 1; }");
    testNotMissing("switch(1) { default: return 1; }");
    testNotMissing("switch(g) { case 1: return 1; default: return 2; }");
  }

  @Test
  public void testFinallyStatements() {
    // The control flow analysis (CFA) treats finally blocks somewhat strangely.
    // The CFA might indicate that a finally block implicitly returns. However,
    // if entry into the finally block is normally caused by an explicit return
    // statement, then a return statement isn't missing:
    //
    // try {
    //   return 1;
    // } finally {
    //   // CFA determines implicit return. However, return not missing
    //   // because of return statement in try block.
    // }
    //
    // Hence extra tests are warranted for various cases involving finally
    // blocks.

    // Simple finally case.
    testNotMissing("try { return 1; } finally { }");
    testNotMissing("try { } finally { return 1; }");
    testMissing("try { } finally { }");

    // Cycles in the CFG within the finally block were causing problems before.
    testNotMissing("try { return 1; } finally { while (true) { } }");
    testMissing("try { } finally { while (x) { } }");
    testMissing("try { } finally { while (x) { if (x) { break; } } }");
    testNotMissing(
        "try { return 2; } finally { while (x) { if (x) { break; } } }");

    // Test various cases with nested try statements.
    testMissing("try { } finally { try { } finally { } }");
    testNotMissing("try { } finally { try { return 1; } finally { } }");
    testNotMissing("try { return 1; } finally { try { } finally { } }");

    // Calling a function potentially causes control flow to transfer to finally
    // block. However, the function will not return in this case as the
    // exception will unwind the stack. Hence this function isn't missing a
    // return statement (i.e., the running program will not expect a return
    // value from the function if an exception is thrown).
    testNotMissing("try { g(); return 1; } finally { }");

    // Closures within try ... finally affect missing return statement analysis
    // because of the nested scopes. The following tests check for missing
    // return statements in the three possible configurations: both scopes
    // return; enclosed doesn't return; enclosing doesn't return.
    testNotMissing(
        "try {"
            + "   /** @return {number} */ function f() {"
            + "       try { return 1; }"
            + "       finally { }"
            + "   };"
            + "   return 1;"
            + "}"
            + "finally { }");
    testMissing(
        "try {"
            + "   /** @return {number} */ function f() {"
            + "       try { }"
            + "       finally { }"
            + "   };"
            + "   return 1;"
            + "}"
            + "finally { }");
    testMissing(
        "try {"
            + "   /** @return {number} */ function f() {"
            + "       try { return 1; }"
            + "       finally { }"
            + "   };"
            + "}"
            + "finally { }");
  }

  @Test
  public void testKnownConditions() {
    testNotMissing("if (true) return 1");
    testMissing("if (true) {} else {return 1}");

    testMissing("if (false) return 1");
    testNotMissing("if (false) {} else {return 1}");

    testNotMissing("if (1) return 1");
    testMissing("if (1) {} else {return 1}");

    testMissing("if (0) return 1");
    testNotMissing("if (0) {} else {return 1}");

    testNotMissing("if (3) return 1");
    testMissing("if (3) {} else {return 1}");
  }

  @Test
  public void testKnownWhileLoop() {
    testNotMissing("while (1) return 1");
    testNotMissing("while (1) { if (x) {return 1} else {return 1}}");
    testNotMissing("while (0) {} return 1");

    // TODO(user): The current algorithm will not detect this case. It is
    // still computable in most cases.
    testNotMissing("while (1) {} return 0");
    testMissing("while (false) return 1");

    // Not known.
    testMissing("while(x) { return 1 }");
  }

  @Test
  public void testMultiConditions() {
    testMissing("if (a) { } else { while (1) {return 1} }");
    testNotMissing("if (a) { return 1} else { while (1) {return 1} }");
  }

  @Test
  public void testIssue779() {
    testNotMissing(
        "var a = f(); try { alert(); if (a > 0) return 1; }" + "finally { a = 5; } return 2;");
  }

  @Test
  public void testConstructors() {
    testSame("/** @constructor */ function foo() {} ");

    final String constructorWithReturn = "/** @constructor \n" +
        " * @return {!foo} */ function foo() {" +
        " if (!(this instanceof foo)) { return new foo; } }";
    testSame(constructorWithReturn);
  }

  @Test
  public void testClosureAsserts() {
    String closureDefs =
        "/** @const */ var goog = {};\n"
            + "goog.asserts = {};\n"
            + "goog.asserts.fail = function(x) {};";

    testNotMissing(closureDefs + "goog.asserts.fail('');");

    testNotMissing(closureDefs
        + "switch (x) { case 1: return 1; default: goog.asserts.fail(''); }");
  }

  @Test
  public void testInfiniteLoops() {
    testNotMissing("while (true) { x = y; if (x === 0) { return 1; } }");
    testNotMissing("for (;true;) { x = y; if (x === 0) { return 1; } }");
    testNotMissing("for (;;) { x = y; if (x === 0) { return 1; } }");
  }

  private static String createFunction(String returnType, String body) {
    return "/** @return {" + returnType + "} */ function foo() {" + body + "}";
  }

  private static String createShorthandFunctionInObjLit(
      String returnType, String body) {
    return lines(
        "var obj = {",
        "  /** @return {" + returnType + "} */",
        "  foo() {", body, "}",
        "}");
  }

  private void testMissingInTraditionalFunction(String returnType, String body) {
    String js = createFunction(returnType, body);
    testWarning(js, CheckMissingReturn.MISSING_RETURN_STATEMENT);
  }

  private void testNotMissingInTraditionalFunction(String returnType, String body) {
    testSame(createFunction(returnType, body));
  }

  private void testMissingInShorthandFunction(String returnType, String body) {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    String js = createShorthandFunctionInObjLit(returnType, body);
    testWarning(js, CheckMissingReturn.MISSING_RETURN_STATEMENT);
  }

  private void testNotMissingInShorthandFunction(String returnType, String body) {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    testSame(createShorthandFunctionInObjLit(returnType, body));
  }

  private void testMissing(String returnType, String body) {
    testMissingInTraditionalFunction(returnType, body);
    testMissingInShorthandFunction(returnType, body);
  }

  /** Creates function with return type {number} */
  private void testMissing(String body) {
    testMissing("number", body);
  }

  private void testNotMissing(String returnType, String body) {
    testNotMissingInTraditionalFunction(returnType, body);
    testNotMissingInShorthandFunction(returnType, body);
  }

  /** Creates function with return type {number} */
  private void testNotMissing(String body) {
    testNotMissing("number", body);
  }

  @Test
  public void testArrowFunctions_noReturn() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    testNoWarning(lines("/** @return {undefined} */", "() => {}"));
  }

  @Test
  public void testArrowFunctions_expressionBody1() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    testSame(lines("/** @return {number} */", "() => 1"));
  }

  @Test
  public void testArrowFunctions_expressionBody2() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    testSame(lines("/** @return {number} */", "(a) => (a > 3) ? 1 : 0"));
  }

  @Test
  public void testArrowFunctions_block() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    testSame(
        lines(
            "/** @return {number} */", "(a) => { if (a > 3) { return 1; } else { return 0; }}"));
  }

  @Test
  public void testArrowFunctions_blockMissingReturn() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    testWarning(
        lines("/** @return {number} */", "(a) => { if (a > 3) { return 1; } else { } }"),
        CheckMissingReturn.MISSING_RETURN_STATEMENT);
  }

  @Test
  public void testArrowFunctions_objectLiteralExpression() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    testSame("(a) => ({foo: 1});");
  }

  @Test
  public void testGeneratorFunctionDoesntWarn() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    testNoWarning("function *gen() {}");

    testNoWarning(
        lines(
            "/** @return {!Generator<number>} */", // no yields is OK
            "function *gen() {}"));

    testNoWarning(
        lines(
            "/** @return {!Generator<number>} */", // one yield is OK
            "function *gen() {",
            " yield 1;",
            "}"));

    testNoWarning(
        lines(
            "/** @return {!Object} */", // Return type more vague than Generator is also OK
            "function *gen() {}"));
  }

  @Test
  public void testAsyncFunction_noJSDoc() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
    // Note: we add the alert because CheckMissingReturn never warns on functions with empty bodies.
    testNoWarning("async function foo() { alert(1); }");
  }

  @Test
  public void testAsyncFunction_returnsUndefined() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
    testNoWarning("/** @return {!Promise<undefined>} */ async function foo() { alert(1); }");
  }

  @Test
  public void testAsyncFunction_returnsUnknown() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
    testNoWarning("/** @return {!Promise<?>} */ async function foo() { alert(1); }");
  }

  @Test
  public void testAsyncFunction_returnsNumber() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
    testNoWarning("/** @return {!Promise<number>} */ async function foo() { return 1; }");

    testWarning(
        "/** @return {!Promise<string>} */ async function foo() { alert(1); }",
        CheckMissingReturn.MISSING_RETURN_STATEMENT);
  }
}
