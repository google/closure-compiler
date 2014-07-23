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

    testOk("if(x()) x = y;");
    test("if(x()); x = y;", e);  // I've had this bug, damned ;
    testOk("if(x()){} x = y;");

    testOk("if(x()) x = y; else y=z;");
    test("if(x()); else y=z;", e);
    testOk("if(x()){} else y=z;");
    test("if(x()) x = y; else;", e);
    testOk("if(x()) x = y; else {}");

    testOk("while(x()) x = y;");
    test("while(x()); x = y;", e);
    testOk("while(x()){} x = y;");
    test("while(x()); {x = y}", e);
    testOk("while(x()){} {x = y}");

    testOk("for(;;) x = y;");
    test("for(;;); x = y;", e);
    testOk("for(;;){} x = y;");
    testOk("for(x in y) x = y;");
    test("for(x in y); x = y;", e);
    testOk("for(x in y){} x = y;");
  }

  public void testSuspiciousIn() {
    testSame("'foo' in 1", CheckSuspiciousCode.SUSPICIOUS_IN_OPERATOR);
    testSame("'foo' in 'test'", CheckSuspiciousCode.SUSPICIOUS_IN_OPERATOR);
    testSame("'foo' in NaN", CheckSuspiciousCode.SUSPICIOUS_IN_OPERATOR);
    testSame("'foo' in undefined", CheckSuspiciousCode.SUSPICIOUS_IN_OPERATOR);
    testSame("'foo' in Infinity", CheckSuspiciousCode.SUSPICIOUS_IN_OPERATOR);
    testSame("'foo' in true", CheckSuspiciousCode.SUSPICIOUS_IN_OPERATOR);
    testSame("'foo' in false", CheckSuspiciousCode.SUSPICIOUS_IN_OPERATOR);
    testSame("'foo' in null", CheckSuspiciousCode.SUSPICIOUS_IN_OPERATOR);
    testSame("'foo' in !Object", CheckSuspiciousCode.SUSPICIOUS_IN_OPERATOR);
    testSame("'foo' in Object", null);
    testSame("'foo' in {}", null);
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

  public void testInstanceOf() {
    testReportInstanceOf("''", "String");
    testReportInstanceOf("4", "Number");
    testReportInstanceOf("-4", "Number");
    testReportInstanceOf("null", "Number");
    testReportInstanceOf("true", "Boolean");
    testReportInstanceOf("false", "Boolean");
    testReportInstanceOf("!true", "Boolean");
    testReportInstanceOf("undefined", "Number");
    testReportInstanceOf("Infinity", "Number");
    testReportInstanceOf("NaN", "Number");
    testReportInstanceOf(
        "/** @constructor */ function Foo() {}; var foo = new Foo();"
        + "!foo", "Foo");

    testReportInstanceOf("!''", "String");
    testReportInstanceOf("!4", "Number");
    testReportInstanceOf("!(new Boolean(true))", "Boolean");
    testReportInstanceOf("!(new Object())", "Object");
    testReportInstanceOf("!Object.prototype", "Object");
    testReportInstanceOf("!Function", "Object");
    testReportInstanceOf("!func()", "String");
    testReportInstanceOf("!({})", "Object");
    testReportInstanceOf("/** @constructor */ function Foo() {"
        + "!this", "Foo;"
        + "}");

    testOk("new String('') instanceof String");
    testOk("new Number(4) instanceof Number");
    testOk("new Boolean(true) instanceof Boolean");
    testOk("new Object() instanceof Object");
    testOk("Object.prototype instanceof Object");
    testOk("Function instanceof Object");
    testOk("func() instanceof String");
    testOk("({}) instanceof Object");
    testOk("/** @constructor */ function Foo() {"
        + " var a = this instanceof Foo; }");

    // TODO(apavlov): It would be nice to have this report, too.
    testOk("(4 + 5) instanceof Number");
  }

  private void testReportInstanceOf(String left, String right) {
    testSame(left + " instanceof " + right,
        CheckSuspiciousCode.SUSPICIOUS_INSTANCEOF_LEFT_OPERAND);
  }

  private void testOk(String js) {
    test(js, (DiagnosticType) null);
  }
}
