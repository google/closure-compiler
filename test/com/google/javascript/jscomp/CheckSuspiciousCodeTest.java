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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for CheckSuspiciousCode */
@RunWith(JUnit4.class)
public final class CheckSuspiciousCodeTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CombinedCompilerPass(compiler,
        new CheckSuspiciousCode());
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
    enableParseTypeInfo();
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Test
  public void testSuspiciousSemi() {
    final DiagnosticType e = CheckSuspiciousCode.SUSPICIOUS_SEMICOLON;

    testSame("if(x()) x = y;");
    testWarning("if(x()); x = y;", e);  // I've had this bug, damned ;
    testSame("if(x()){} x = y;");

    testSame("if(x()) x = y; else y=z;");
    testWarning("if(x()); else y=z;", e);
    testSame("if(x()){} else y=z;");
    testWarning("if(x()) x = y; else;", e);
    testSame("if(x()) x = y; else {}");

    testSame("while(x()) x = y;");
    testWarning("while(x()); x = y;", e);
    testSame("while(x()){} x = y;");
    testWarning("while(x()); {x = y}", e);
    testSame("while(x()){} {x = y}");

    testSame("for(;;) x = y;");
    testWarning("for(;;); x = y;", e);
    testSame("for(;;){} x = y;");
    testSame("for(x in y) x = y;");
    testWarning("for(x in y); x = y;", e);
    testSame("for(x in y){} x = y;");

    testSame("var y = [1, 2, 3]; for(x of y) console.log(x);");
    testWarning("var y = [1, 2, 3]; for(x of y); console.log(x);", e);
    testSame("var y = [1, 2, 3]; for(x of y){} console.log(x);");
  }

  @Test
  public void testSuspiciousIn() {
    testWarning("'foo' in 1", CheckSuspiciousCode.SUSPICIOUS_IN_OPERATOR);
    testWarning("'foo' in 'test'", CheckSuspiciousCode.SUSPICIOUS_IN_OPERATOR);
    testWarning("'foo' in NaN", CheckSuspiciousCode.SUSPICIOUS_IN_OPERATOR);
    testWarning("'foo' in undefined", CheckSuspiciousCode.SUSPICIOUS_IN_OPERATOR);
    testWarning("'foo' in Infinity", CheckSuspiciousCode.SUSPICIOUS_IN_OPERATOR);
    testWarning("'foo' in true", CheckSuspiciousCode.SUSPICIOUS_IN_OPERATOR);
    testWarning("'foo' in false", CheckSuspiciousCode.SUSPICIOUS_IN_OPERATOR);
    testWarning("'foo' in null", CheckSuspiciousCode.SUSPICIOUS_IN_OPERATOR);
    testWarning("'foo' in !Object", CheckSuspiciousCode.SUSPICIOUS_IN_OPERATOR);
    testSame("'foo' in Object");
    testSame("'foo' in {}");
  }

  @Test
  public void testForOf() {
    testSame("var y = [1, 2, 3]; for (var x of y) console.log(x);");
    testSame("var y = [1, 2, 3]; for (var x of 'test') console.log(x);");
    testSame("for (var x of 123) console.log(x);");
    testSame("for (var x of false) console.log(x);");
    testSame("for (var x of true) console.log(x);");
    testSame("for (var x of undefined) console.log(x);");
    testSame("for (var x of NaN) console.log(x);");
    testSame("for (var x of Infinity) console.log(x);");
    testSame("for (var x of null) console.log(x);");
  }

  private void testReportNaN(String js) {
    testWarning(js, CheckSuspiciousCode.SUSPICIOUS_COMPARISON_WITH_NAN);
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testComparison5() {
    testReportNaN("x == Number.NaN");
    testReportNaN("x != Number.NaN");
    testReportNaN("x === Number.NaN");
    testReportNaN("x !== Number.NaN");
    testReportNaN("x < Number.NaN");
    testReportNaN("x <= Number.NaN");
    testReportNaN("x > Number.NaN");
    testReportNaN("x >= Number.NaN");
  }

  @Test
  public void testComparison6() {
    testReportNaN("Number.NaN == x");
    testReportNaN("Number.NaN != x");
    testReportNaN("Number.NaN === x");
    testReportNaN("Number.NaN !== x");
    testReportNaN("Number.NaN < x");
    testReportNaN("Number.NaN <= x");
    testReportNaN("Number.NaN > x");
    testReportNaN("Number.NaN >= x");
  }

  @Test
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

    testReportInstanceOf("(4 + 5)", "Number");
    testReportInstanceOf("('a' + 'b')", "String");
    testReportInstanceOf("('a' + 5)", "String");

    // The following five are from "Wat — Destroy All Software Talks"
    testReportInstanceOf("([] + [])", "String");
    testReportInstanceOf("([] + {})", "String");
    testReportInstanceOf("({} + [])", "String");
    testReportInstanceOf("({c:1} + [])", "String");
    testReportInstanceOf("({} + {})", "Number"); // NaN

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

    testSame("new String('') instanceof String");
    testSame("new Number(4) instanceof Number");
    testSame("var a = new Number(4); a instanceof Number");
    testSame("new Boolean(true) instanceof Boolean");
    testSame("new Object() instanceof Object");
    testSame("Object.prototype instanceof Object");
    testSame("Function instanceof Object");
    testSame("func() instanceof String");
    testSame("({}) instanceof Object");
    testSame("/** @constructor */ function Foo() {"
        + " var a = this instanceof Foo; }");

    testSame("(()=>42) instanceof Function");
    testSame("class Person{} Person instanceof Function");
    testSame(lines(
        "class Person{}",
        "var peter = new Person();",
        "peter instanceof Person"));
    testSame("taggedTemplate`${tagged}Temp` instanceof Function");
  }

  private void testReportInstanceOf(String left, String right) {
    testWarning(left + " instanceof " + right,
        CheckSuspiciousCode.SUSPICIOUS_INSTANCEOF_LEFT_OPERAND);
  }

  @Test
  public void testCheckNegatedLeftOperandOfInOperator() {
    testSame("if (!(x in y)) {}");
    testSame("if (('' + !x) in y) {}");
    testWarning(
        "if (!x in y) {}", CheckSuspiciousCode.SUSPICIOUS_NEGATED_LEFT_OPERAND_OF_IN_OPERATOR);
  }
}
