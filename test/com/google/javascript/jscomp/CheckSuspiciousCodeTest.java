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

import static com.google.javascript.jscomp.CheckSuspiciousCode.SUSPICIOUS_LEFT_OPERAND_OF_LOGICAL_OPERATOR;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for CheckSuspiciousCode */
@RunWith(JUnit4.class)
public final class CheckSuspiciousCodeTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CombinedCompilerPass(compiler, new CheckSuspiciousCode());
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableParseTypeInfo();
  }

  @Test
  public void suspiciousBreakingOutOfOptionalChain() {
    final DiagnosticType e = CheckSuspiciousCode.SUSPICIOUS_BREAKING_OUT_OF_OPTIONAL_CHAIN;

    testSame("a?.b.c");
    testWarning("(a?.b).c", e);

    testSame("a.b?.[c][d]");
    testWarning("(a.b?.[c])[d]", e);

    testSame("a.b?.()()");
    testWarning("(a.b?.())()", e);

    testSame("a(b?.c);");
    testSame("a[b?.c];");
  }

  @Test
  public void testSuspiciousSemi() {
    final DiagnosticType e = CheckSuspiciousCode.SUSPICIOUS_SEMICOLON;

    testSame("if(x()) x = y;");
    testWarning("if(x()); x = y;", e); // I've had this bug, damned ;
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

    testSame("async () => { var y = [1, 2, 3]; for await (x of y) console.log(x); }");
    testWarning("async () => { var y = [1, 2, 3]; for await (x of y); console.log(x); }", e);
    testSame("async () => { var y = [1, 2, 3]; for await (x of y){} console.log(x); }");
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

  @Test
  public void testForAwaitOf() {
    testSame("async () => { var y = [1, 2, 3]; for await (var x of y) console.log(x); }");
    testSame("async () => { var y = [1, 2, 3]; for await (var x of 'test') console.log(x); }");
    testSame("async () => { for await (var x of 123) console.log(x); }");
    testSame("async () => { for await (var x of false) console.log(x); }");
    testSame("async () => { for await (var x of true) console.log(x); }");
    testSame("async () => { for await (var x of undefined) console.log(x); }");
    testSame("async () => { for await (var x of NaN) console.log(x); }");
    testSame("async () => { for await (var x of Infinity) console.log(x); }");
    testSame("async () => { for await (var x of null) console.log(x); }");
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
        "/** @constructor */ function Foo() {}; var foo = new Foo();" + "!foo", "Foo");

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
    testReportInstanceOf("/** @constructor */ function Foo() {" + "!this", "Foo;" + "}");

    testSame("new String('') instanceof String");
    testSame("new Number(4) instanceof Number");
    testSame("var a = new Number(4); a instanceof Number");
    testSame("new Boolean(true) instanceof Boolean");
    testSame("new Object() instanceof Object");
    testSame("Object.prototype instanceof Object");
    testSame("Function instanceof Object");
    testSame("func() instanceof String");
    testSame("({}) instanceof Object");
    testSame("/** @constructor */ function Foo() {" + " var a = this instanceof Foo; }");

    testSame("(()=>42) instanceof Function");
    testSame("class Person{} Person instanceof Function");
    testSame(lines("class Person{}", "var peter = new Person();", "peter instanceof Person"));
    testSame("taggedTemplate`${tagged}Temp` instanceof Function");
  }

  private void testReportInstanceOf(String left, String right) {
    testWarning(
        left + " instanceof " + right, CheckSuspiciousCode.SUSPICIOUS_INSTANCEOF_LEFT_OPERAND);
  }

  @Test
  public void testCheckNegatedLeftOperandOfInOperator() {
    testSame("if (!(x in y)) {}");
    testSame("if (('' + !x) in y) {}");
    testWarning(
        "if (!x in y) {}", CheckSuspiciousCode.SUSPICIOUS_NEGATED_LEFT_OPERAND_OF_IN_OPERATOR);
  }

  @Test
  public void testSuspiciousLeftArgumentOfLogicalOperator() {
    testSame("if (x && y) {}");
    testWarning("if (false && y) {}", SUSPICIOUS_LEFT_OPERAND_OF_LOGICAL_OPERATOR);
    testWarning("if (true && y) {}", SUSPICIOUS_LEFT_OPERAND_OF_LOGICAL_OPERATOR);
    testWarning("if (false || y) {}", SUSPICIOUS_LEFT_OPERAND_OF_LOGICAL_OPERATOR);
    testWarning("if (true || y) {}", SUSPICIOUS_LEFT_OPERAND_OF_LOGICAL_OPERATOR);
    testWarning("if (0 || y) {}", SUSPICIOUS_LEFT_OPERAND_OF_LOGICAL_OPERATOR);
    testWarning("if (null && y) {}", SUSPICIOUS_LEFT_OPERAND_OF_LOGICAL_OPERATOR);
    testWarning("if (NaN || y) {}", SUSPICIOUS_LEFT_OPERAND_OF_LOGICAL_OPERATOR);
    testWarning("if ({} && y) {}", SUSPICIOUS_LEFT_OPERAND_OF_LOGICAL_OPERATOR);
    testWarning("if (void x && y) {}", SUSPICIOUS_LEFT_OPERAND_OF_LOGICAL_OPERATOR);

    testWarning("if (x || true || y) {}", SUSPICIOUS_LEFT_OPERAND_OF_LOGICAL_OPERATOR);
    testWarning("if (x && false || y) {}", SUSPICIOUS_LEFT_OPERAND_OF_LOGICAL_OPERATOR);
    testSame("if (x || false || y) {}");
    testSame("if (x && true || y) {}");
  }

  @Test
  public void testSuspiciousLeftArgumentOfLogicalOperator_propAccesses() {
    enableTypeCheck();
    // Cases that must not warn as LHS is not always falsy.
    testNoWarning("/** @type {null|{b:string}} */ let a; if (a?.b && true) {}");
    testNoWarning("/** @type {null|{b:string}} */ let a; if (a?.[b] && c) {}");
    testNoWarning("/** @type {{b: (null|string)}} */ let a; if (a?.b && true) {}");
    testNoWarning("/** @type {{b: (null|string)}} */ let a; if (a?.[b] && true) {}");

    // Cases that could warn but don't, as we assume that GETPROPs and GETELEMs type information is
    // likely wrong
    // normal prop access
    testNoWarning("/** @type {{b:null}} */ let a; if( a[b] && true) {}");
    testNoWarning("/** @type {{b:null}} */ let a; if( a.b && true) {}");
    // optChain prop access
    testNoWarning("/** @type {{b:null}} */ let a; if( a?.[b] && true) {}");
    testNoWarning("/** @type {{b:null}} */ let a; if( a?.b && true) {}");

    // normal call
    testWarning(
        "let a = {}; /** @return {null} */ a.b = function() {}; if (a.b() && c) {}",
        SUSPICIOUS_LEFT_OPERAND_OF_LOGICAL_OPERATOR);
    // optChain calls
    testWarning(
        "let a = {}; /** @return {null} */ a.b = function() {}; if (a?.b() && c) {}",
        SUSPICIOUS_LEFT_OPERAND_OF_LOGICAL_OPERATOR);
    testWarning(
        "/** @type {null} */ let a = null; if (a?.b() && c) {}",
        SUSPICIOUS_LEFT_OPERAND_OF_LOGICAL_OPERATOR);
  }

  @Test
  public void testSuspiciousLeftArgumentOfLogicalOperator_typeBased() {
    enableTypeCheck();
    String prefix =
        lines(
            "/** @return {!Object} */ function truthy() { return {}; }",
            "/** @return {null|undefined} */ function falsy() { return null; }",
            "/** @return {number} */ function number() { return 42; }",
            "/** @return {?} */ function unknown() { return 42; }",
            "/** @const */ var ns = {};",
            "/** @type {!Object<!Object>} */ ns.truthy = {};",
            "/** @type {null} */ ns.falsy = null;",
            "");

    testSame("var x = x || {};");
    testSame(prefix + "if (number() && y) {}");
    testSame(prefix + "if (unknown() && y) {}");
    testSame(prefix + "if (ns.truthy && y) {}");
    testSame(prefix + "if (ns.falsy && y) {}");
    testSame(prefix + "if (!ns.truthy && y) {}");
    testSame(prefix + "if (!ns.falsy && y) {}");

    testSame(prefix + "if (number() || y) {}");
    testSame(prefix + "if (unknown() || y) {}");
    testSame(prefix + "if (ns.truthy || y) {}");
    testSame(prefix + "if (ns.falsy || y) {}");
    testSame(prefix + "if (!ns.truthy || y) {}");
    testSame(prefix + "if (!ns.falsy || y) {}");

    testSame(prefix + "if (ns.falsy || ns.truthy || y) {}");
    testSame(prefix + "if (ns.falsy && ns.truthy || y) {}");
    testSame(prefix + "if (ns.truthy[ns.truthy] || y) {}");
    testSame(prefix + "if (ns.truthy[truthy()] || y) {}");
    testSame(prefix + "if ((truthy(), !ns.falsy) || y) {}");

    // It's always okay to second-guess a type-based always-truthy.
    testSame(prefix + "if (truthy() && y) {}");
    testSame(prefix + "if (!truthy() && y) {}");
    testSame(prefix + "if (truthy() || y) {}");
    testSame(prefix + "if (!truthy() || y) {}");

    testWarning(prefix + "if (falsy() && y) {}", SUSPICIOUS_LEFT_OPERAND_OF_LOGICAL_OPERATOR);
    testWarning(prefix + "if (!falsy() && y) {}", SUSPICIOUS_LEFT_OPERAND_OF_LOGICAL_OPERATOR);
    testWarning(prefix + "if (falsy() || y) {}", SUSPICIOUS_LEFT_OPERAND_OF_LOGICAL_OPERATOR);
    testWarning(prefix + "if (!falsy() || y) {}", SUSPICIOUS_LEFT_OPERAND_OF_LOGICAL_OPERATOR);

    testWarning(
        prefix + "if ((truthy(), !falsy()) || y) {}", SUSPICIOUS_LEFT_OPERAND_OF_LOGICAL_OPERATOR);
  }

  @Test
  public void testSuspiciousLeftArgumentOfLogicalOperator_guardedInitialization_noWarning() {
    // In this example, 'x' is provably falsy, but it's a common pattern to initialize a global
    // variable that may already exist before the script began.
    enableTypeCheck();
    testSame("var x = x || {};");
  }

  @Test
  public void testSuspiciousLeftArgumentOfLogicalOperator_truthyFunctionCalls_noWarning() {
    // There are commonplace examples where functions that are annotated to return truthy results
    // are actually lying, so we need to back off from warning about these.
    enableTypeCheck();
    String prefix = "/** @return {!Object} */ function truthy() { return {}; }\n";
    testSame(prefix + "if (truthy() && y) {}");
    testSame(prefix + "if (!truthy() && y) {}");
    testSame(prefix + "if (truthy() || y) {}");
    testSame(prefix + "if (!truthy() || y) {}");
  }

  @Test
  public void testSuspiciousLeftArgumentOfLogicalOperator_qualifiedName_noWarning() {
    // Type information on qualified names can be wrong in both directions (e.g. an extern might
    // assert that a name exists, but code must still verify to be sure).
    enableTypeCheck();
    String prefix =
        lines(
            "/** @const */ var ns = {};",
            "/** @type {!Object<!Object>} */ ns.truthy = {};",
            "/** @type {null} */ ns.falsy = null;",
            "");
    testSame(prefix + "if (ns.truthy && y) {}");
    testSame(prefix + "if (ns.falsy && y) {}");
    testSame(prefix + "if (!ns.truthy && y) {}");
    testSame(prefix + "if (!ns.falsy && y) {}");
    testSame(prefix + "if (ns.truthy || y) {}");
    testSame(prefix + "if (ns.falsy || y) {}");
    testSame(prefix + "if (!ns.truthy || y) {}");
    testSame(prefix + "if (!ns.falsy || y) {}");
  }

  @Test
  public void testSuspiciousLeftArgumentOfLogicalOperator_primitiveOrUnknownTypes_noWarning() {
    // Primitive types (like number) and unknown types can be either true or false, so don't warn.
    enableTypeCheck();
    String prefix =
        lines(
            "/** @return {number} */ function number() { return 42; }",
            "/** @return {?} */ function unknown() { return 42; }",
            "");
    testSame(prefix + "if (number() && y) {}");
    testSame(prefix + "if (unknown() && y) {}");
    testSame(prefix + "if (number() || y) {}");
    testSame(prefix + "if (unknown() || y) {}");
    testSame(prefix + "if (!number() && y) {}");
    testSame(prefix + "if (!unknown() && y) {}");
    testSame(prefix + "if (!number() || y) {}");
    testSame(prefix + "if (!unknown() || y) {}");
  }

  @Test
  public void testSuspiciousLeftArgumentOfLogicalOperator_deeperNesting() {
    enableTypeCheck();
    String prefix =
        lines(
            "/** @return {!Object} */ function truthy() { return {}; }",
            "function falsy() { return; }",
            "/** @const */ var ns = {};",
            "/** @type {!Object<!Object>} */ ns.truthy = {};",
            "/** @type {null} */ ns.falsy = null;",
            "");

    // When the above constructs (that don't warn) are combined more deeply, they still don't warn.
    testSame(prefix + "if (ns.falsy || ns.truthy || y) {}");
    testSame(prefix + "if (ns.falsy && ns.truthy || y) {}");
    testSame(prefix + "if (ns.truthy[ns.truthy] || y) {}");
    testSame(prefix + "if (ns.truthy[truthy()] || y) {}");

    // Comma operator only recurses into RHS, so the truthy() below is ignored.
    testSame(prefix + "if ((truthy(), !ns.falsy) || y) {}");

    // But if the RHS of the comma would normally warn, then we should warn on the comma, too.
    testWarning(
        prefix + "if ((truthy(), falsy()) || y) {}", SUSPICIOUS_LEFT_OPERAND_OF_LOGICAL_OPERATOR);
  }

  @Test
  public void testSuspiciousLeftArgumentOfLogicalOperator_falsyFunctionCalls_warn() {
    // Falsy function call results are less likely to be a false positive, since there are far
    // fewer lies about them in the wild.
    enableTypeCheck();
    String prefix =
        lines(
            "/** @return {!Object} */ function truthy() { return {}; }",
            "function falsy() { return; }",
            "");

    testWarning(prefix + "if (falsy() && y) {}", SUSPICIOUS_LEFT_OPERAND_OF_LOGICAL_OPERATOR);
    testWarning(prefix + "if (!falsy() && y) {}", SUSPICIOUS_LEFT_OPERAND_OF_LOGICAL_OPERATOR);
    testWarning(prefix + "if (falsy() || y) {}", SUSPICIOUS_LEFT_OPERAND_OF_LOGICAL_OPERATOR);
    testWarning(prefix + "if (!falsy() || y) {}", SUSPICIOUS_LEFT_OPERAND_OF_LOGICAL_OPERATOR);
  }
}
