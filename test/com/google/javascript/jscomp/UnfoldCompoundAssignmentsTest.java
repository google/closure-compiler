/*
 * Copyright 2010 The Closure Compiler Authors.
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

/**
 * @author elnatan@google.com (Elnatan Reisner)
 *
 */
public class UnfoldCompoundAssignmentsTest extends CompilerTestCase {
  public UnfoldCompoundAssignmentsTest() {
    enableNormalize();
  }

  /* (non-Javadoc)
   * @see CompilerTestCase#getProcessor(Compiler)
   */
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new UnfoldCompoundAssignments(compiler);
  }

  public void testIncrement() {
    test("x++;", "x = +x + 1;");
    test("var x = 0; ++x;", "var x = 0; x = +x + 1;");
  }

  public void testDecrement() {
    test("x--;", "x = x - 1;");
    test("var x = 0; --x;", "var x = 0; x = x - 1;");
  }

  public void testCompoundAssignment() {
    test("x <<= y;", "x = x << y;");
  }

  public void testPostfixInForLoop0() {
    test("for (x++;;) {}", "for (x = +x + 1;;) {}");
  }

  public void testPostfixInForLoop1() {
    try {
      testSame("for (;x++;) {}");
      fail("Should raise an exception");
    } catch (RuntimeException e) {
    }
  }

  public void testPostfixInForLoop2() {
    test("for (;;x++) {}", "for (;;x = +x + 1) {}");
  }

  public void testPrefixWithinLargerExpression() {
    test("--x + 7;", "(x = x - 1) + 7;");
  }

  public void testPostfixInComma() {
    test("z++, z==8;", "z = +z + 1, z==8;");
  }

  public void testPostfixUsedValue0() {
    try {
      testSame("z==8, z++;");
      fail("Should raise an exception");
    } catch (RuntimeException e) {
    }
  }

  public void testPostfixUsedValue1() {
    try {
      testSame("x-- + 7;");
      fail("Should raise an Exception");
    } catch (RuntimeException e) {
    }
  }

  public void testMultiple() {
    test("x++, 5; for (a.x++;0;x++) {}; x++;",
        "x = +x + 1, 5; for (a.x = +a.x + 1; 0; x = +x + 1) {}; x = +x + 1;");
  }

  public void testIncrementSideEffects() {
    try {
      // Expanding '++' causes f to be called twice.
      testSame("++a[f()];");
      fail("Should raise an exception");
    } catch (RuntimeException e) {
    }
  }

  public void testCompoundAssignmentSideEffects() {
    try {
      // Expanding causes f to be called twice.
      testSame("a[f()] *= 2;");
      fail("Should raise an exception");
    } catch (RuntimeException e) {
    }
  }
}
