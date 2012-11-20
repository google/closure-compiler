/*
 * Copyright 2012 The Closure Compiler Authors.
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
 * Tests for CheckSuspiciousCode
 */
public class CheckSuspiciousCodeTest extends CompilerTestCase {
  public CheckSuspiciousCodeTest() {
    this.parseTypeInfo = true;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CombinedCompilerPass(compiler,
        new CheckSuspiciousCode());
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void test(String js, DiagnosticType error) {
    test(js, js, null, error);
  }

  public void testSuspiciousSemi() {
    final DiagnosticType e = CheckSuspiciousCode.SUSPICIOUS_SEMICOLON;
    final DiagnosticType ok = null;  //  code is 'ok', verify no warning

    test("if(x()) x = y;", ok);
    test("if(x()); x = y;", e);  // I've had this bug, damned ;
    test("if(x()){} x = y;", ok);

    test("if(x()) x = y; else y=z;", ok);
    test("if(x()); else y=z;", e);
    test("if(x()){} else y=z;", ok);
    test("if(x()) x = y; else;", e);
    test("if(x()) x = y; else {}", ok);

    test("while(x()) x = y;", ok);
    test("while(x()); x = y;", e);
    test("while(x()){} x = y;", ok);
    test("while(x()); {x = y}", e);
    test("while(x()){} {x = y}", ok);

    test("for(;;) x = y;", ok);
    test("for(;;); x = y;", e);
    test("for(;;){} x = y;", ok);
    test("for(x in y) x = y;", ok);
    test("for(x in y); x = y;", e);
    test("for(x in y){} x = y;", ok);
  }

  private void testReportNaN(String js) {
    testSame(js, CheckSuspiciousCode.SUSPICIOUS_COMPARISON_WITH_NAN);
  }

  public void testComparison1() {
    testReportNaN("x == NaN");
    testReportNaN("x != NaN");
    testReportNaN("x === NaN");
    testReportNaN("x !== NaN");
    testReportNaN("x < NaN");
    testReportNaN("x <= NaN");
    testReportNaN("x > NaN");
    testReportNaN("x >= NaN");
  }

  public void testComparison2() {
    testReportNaN("NaN == x");
    testReportNaN("NaN != x");
    testReportNaN("NaN === x");
    testReportNaN("NaN !== x");
    testReportNaN("NaN < x");
    testReportNaN("NaN <= x");
    testReportNaN("NaN > x");
    testReportNaN("NaN >= x");
  }

  public void testComparison3() {
    testReportNaN("x == 0/0");
    testReportNaN("x != 0/0");
    testReportNaN("x === 0/0");
    testReportNaN("x !== 0/0");
    testReportNaN("x < 0/0");
    testReportNaN("x <= 0/0");
    testReportNaN("x > 0/0");
    testReportNaN("x >= 0/0");
  }

  public void testComparison4() {
    testReportNaN("0/0 == x");
    testReportNaN("0/0 != x");
    testReportNaN("0/0 === x");
    testReportNaN("0/0 !== x");
    testReportNaN("0/0 < x");
    testReportNaN("0/0 <= x");
    testReportNaN("0/0 > x");
    testReportNaN("0/0 >= x");
  }
}
