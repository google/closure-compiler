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

import com.google.javascript.jscomp.CheckLevel;

/**
 * Tests for {@link CheckMissingReturn}.
 *
 */
public class CheckMissingReturnTest extends CompilerTestCase {

  public CheckMissingReturnTest() {
    enableTypeCheck(CheckLevel.OFF);
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CombinedCompilerPass(compiler,
        new CheckMissingReturn(compiler, CheckLevel.ERROR));
  }

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

  public void testReturnNotMissing()  {
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
    testNotMissing("(number,void)", "var x;");
    testNotMissing("(number,undefined)", "var x;");
    testNotMissing("*", "var x;");

    // Test try catch finally.
    testNotMissing("try { return foo() } catch (e) { } finally { }");

    // Nested function.
    testNotMissing(
        "/** @return {number} */ function f() { return 1; }; return 1;");

    // Strange tests that come up when reviewing closure code.
    testNotMissing("try { return 12; } finally { return 62; }");
    testNotMissing("try { } finally { return 1; }");
    testNotMissing("switch(1) { default: return 1; }");
    testNotMissing("switch(g) { case 1: return 1; default: return 2; }");
  }

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
        "try {" +
        "   /** @return {number} */ function f() {" +
        "       try { return 1; }" +
        "       finally { }" +
        "   };" +
        "   return 1;" +
        "}" +
        "finally { }");
    testMissing(
        "try {" +
        "   /** @return {number} */ function f() {" +
        "       try { }" +
        "       finally { }" +
        "   };" +
        "   return 1;" +
        "}" +
        "finally { }");
    testMissing(
        "try {" +
        "   /** @return {number} */ function f() {" +
        "       try { return 1; }" +
        "       finally { }" +
        "   };" +
        "}" +
        "finally { }");
  }

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

  public void testMultiConditions() {
    testMissing("if (a) { } else { while (1) {return 1} }");
    testNotMissing("if (a) { return 1} else { while (1) {return 1} }");
  }

  public void testIssue779() {
    testNotMissing(
        "var a = f(); try { alert(); if (a > 0) return 1; }" +
        "finally { a = 5; } return 2;");
  }

  public void testConstructors() {
    testSame("/** @constructor */ function foo() {} ");

    final String constructorWithReturn = "/** @constructor \n" +
        " * @return {!foo} */ function foo() {" +
        " if (!(this instanceof foo)) { return new foo; } }";
    testSame(constructorWithReturn);
  }

  public void testClosureAsserts() {
    String closureDefs =
        "/** @const */ var goog = {};\n" +
        "goog.asserts = {};\n" +
        "goog.asserts.fail = function(x) {};";

    testNotMissing(closureDefs + "goog.asserts.fail('');");

    testNotMissing(closureDefs
        + "switch (x) { case 1: return 1; default: goog.asserts.fail(''); }");
  }

  public void testInfiniteLoops() {
    testNotMissing("while (true) { x = y; if (x === 0) { return 1; } }");
    testNotMissing("for (;true;) { x = y; if (x === 0) { return 1; } }");
    testNotMissing("for (;;) { x = y; if (x === 0) { return 1; } }");
  }

  private static String createFunction(String returnType, String body) {
    return "/** @return {" + returnType + "} */ function foo() {" + body + "}";
  }

  private void testMissing(String returnType, String body) {
    String js = createFunction(returnType, body);
    testError(js, CheckMissingReturn.MISSING_RETURN_STATEMENT);
  }

  private void testNotMissing(String returnType, String body) {
    testSame(createFunction(returnType, body));
  }

  /** Creates function with return type {number} */
  private void testNotMissing(String body) {
    testNotMissing("number", body);
  }

  /** Creates function with return type {number} */
  private void testMissing(String body) {
    testMissing("number", body);
  }
}
