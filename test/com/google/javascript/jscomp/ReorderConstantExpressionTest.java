/*
 * Copyright 2011 The Closure Compiler Authors.
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
 * Unit test for {@link ReorderConstantExpression}
 *
 */
public class ReorderConstantExpressionTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new PeepholeOptimizationsPass(compiler,
        new ReorderConstantExpression());
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    super.enableLineNumberCheck(true);
    disableTypeCheck();
  }

  public void testSymmetricOperations() throws Exception {
    set1Tests("==");
    set2Tests("==");
    set3Tests("==");

    set1Tests("!=");
    set2Tests("!=");
    set3Tests("!=");

    set1Tests("===");
    set2Tests("===");
    set3Tests("===");

    set1Tests("!==");
    set2Tests("!==");
    set3Tests("!==");

    set1Tests("*");
    set2Tests("*");
    set3Tests("*");
  }

  public void testRelationalOperations() throws Exception {
    set1Tests(">", "<");
    set3Tests(">");
    set1Tests("<", ">");
    set3Tests("<");

    set1Tests(">=", "<=");
    set3Tests(">=");
    set1Tests("<=", ">=");
    set3Tests("<=");
  }

  private void set1Tests(String op) throws Exception {
    set1Tests(op, op);
  }

  /**
   * This set has a mutable on the right and an Immutable on the left.
   * Applies for relational and symmetric operations.
   */
  private void set1Tests(String op1, String op2) throws Exception {
    test("a " + op1 + " 0", "0 " + op2 + " a");
    test("a " + op1 + " '0'", "'0' " + op2 + " a");
    test("a " + op1 + " ''", "'' " + op2 + " a");
    test("a " + op1 + " -1.0", "-1.0 " + op2 + " a");

    test("function f(a){a " + op1 + " 0}",
         "function f(a){0 " + op2 + " a}");
    test("f() " + op1 + " 0", "0 " + op2 + " f()");
    test("(a + b) " + op1 + " 0", "0 " + op2 + " (a + b)");
    test("(a + 1) " + op1 + " 0", "0 " + op2 + " (a + 1)");

    test("x++ " + op1 + " 0", "0 " + op2 + " x++");
    test("x = 0; function f(){x++; return x}; f() " + op1 + " 0",
         "x = 0; function f(){x++; return x}; 0 " + op2 + " f()");
  }

  /**
   * This set has a mutable on the right and an Immutable on the left.
   * Applies only for symmetric operations.
   */
  private void set2Tests(String op) throws Exception {
    test("a " + op + " NaN", "NaN " + op + " a");
    test("a " + op + " Infinity", "Infinity " + op + " a");

    testSame("NaN " + op + " a");
    testSame("Infinity " + op + " a");
  }

  /**
   * This set has an the immutable on the left already, or both non-immutable.
   */
  private void set3Tests(String op) throws Exception {
    testSame("0 " + op + " a");
    testSame("'0' " + op + " a");
    testSame("'' " + op + " a");
    testSame("-1.0 " + op + " a");
    testSame("-1.0 " + op + " a");

    testSame("0 " + op + " 1");

    testSame("a " + op + " b");
  }

  public void testReorderConstantDoesntAddParens() {
    testSame("a % b * 4");
    testSame("a * b * 4");
  }
}
