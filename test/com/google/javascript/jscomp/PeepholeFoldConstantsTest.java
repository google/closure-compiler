/*
 * Copyright 2004 The Closure Compiler Authors.
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

import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link PeepholeFoldConstants} in isolation. Tests for the interaction of multiple
 * peephole passes are in {@link PeepholeIntegrationTest}.
 */
@RunWith(JUnit4.class)
public final class PeepholeFoldConstantsTest extends CompilerTestCase {

  public PeepholeFoldConstantsTest() {
    super(
        new TestExternsBuilder()
            .addArray()
            .addIterable()
            .addObject()
            .addUndefined()
            .addFunction()
            .addString()
            .addInfinity()
            .addNaN()
            .build());
  }

  private boolean late;
  private boolean useTypes = true;
  private int numRepetitions;
  private boolean assumeGettersPure = false;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    disableTypeCheck();
    late = false;
    useTypes = true;
    // Reduce this to 1 if we get better expression evaluators.
    numRepetitions = 2;
    enableNormalize();
    disableCompareJsDoc();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new PeepholeOptimizationsPass(
        compiler, getName(), new PeepholeFoldConstants(late, useTypes));
  }

  @Override
  protected int getNumRepetitions() {
    return numRepetitions;
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setAssumeGettersArePure(assumeGettersPure);
    return options;
  }

  @Test
  public void testUndefinedComparison1() {
    test("undefined == undefined", "true");
    test("undefined == null", "true");
    test("undefined == void 0", "true");

    test("undefined == 0", "false");
    test("undefined == 1", "false");
    test("undefined == 'hi'", "false");
    test("undefined == true", "false");
    test("undefined == false", "false");

    test("undefined === undefined", "true");
    test("undefined === null", "false");
    test("undefined === void 0", "true");

    testSame("undefined == this");
    testSame("undefined == x");

    test("undefined != undefined", "false");
    test("undefined != null", "false");
    test("undefined != void 0", "false");

    test("undefined != 0", "true");
    test("undefined != 1", "true");
    test("undefined != 'hi'", "true");
    test("undefined != true", "true");
    test("undefined != false", "true");

    test("undefined !== undefined", "false");
    test("undefined !== void 0", "false");
    test("undefined !== null", "true");

    testSame("undefined != this");
    testSame("undefined != x");

    test("undefined < undefined", "false");
    test("undefined > undefined", "false");
    test("undefined >= undefined", "false");
    test("undefined <= undefined", "false");

    test("0 < undefined", "false");
    test("true > undefined", "false");
    test("'hi' >= undefined", "false");
    test("null <= undefined", "false");

    test("undefined < 0", "false");
    test("undefined > true", "false");
    test("undefined >= 'hi'", "false");
    test("undefined <= null", "false");

    test("null == undefined", "true");
    test("0 == undefined", "false");
    test("1 == undefined", "false");
    test("'hi' == undefined", "false");
    test("true == undefined", "false");
    test("false == undefined", "false");
    test("null === undefined", "false");
    test("void 0 === undefined", "true");

    test("undefined == NaN", "false");
    test("NaN == undefined", "false");
    test("undefined == Infinity", "false");
    test("Infinity == undefined", "false");
    test("undefined == -Infinity", "false");
    test("-Infinity == undefined", "false");
    test("({}) == undefined", "false");
    test("undefined == ({})", "false");
    test("([]) == undefined", "false");
    test("undefined == ([])", "false");
    test("(/a/g) == undefined", "false");
    test("undefined == (/a/g)", "false");
    test("(function(){}) == undefined", "false");
    test("undefined == (function(){})", "false");

    test("undefined != NaN", "true");
    test("NaN != undefined", "true");
    test("undefined != Infinity", "true");
    test("Infinity != undefined", "true");
    test("undefined != -Infinity", "true");
    test("-Infinity != undefined", "true");
    test("({}) != undefined", "true");
    test("undefined != ({})", "true");
    test("([]) != undefined", "true");
    test("undefined != ([])", "true");
    test("(/a/g) != undefined", "true");
    test("undefined != (/a/g)", "true");
    test("(function(){}) != undefined", "true");
    test("undefined != (function(){})", "true");

    testSame("this == undefined");
    testSame("x == undefined");
  }

  @Test
  public void testUndefinedComparison2() {
    test("\"123\" !== void 0", "true");
    test("\"123\" === void 0", "false");

    test("void 0 !== \"123\"", "true");
    test("void 0 === \"123\"", "false");
  }

  @Test
  public void testUndefinedComparison3() {
    test("\"123\" !== undefined", "true");
    test("\"123\" === undefined", "false");

    test("undefined !== \"123\"", "true");
    test("undefined === \"123\"", "false");
  }

  @Test
  public void testUndefinedComparison4() {
    test("1 !== void 0", "true");
    test("1 === void 0", "false");

    test("null !== void 0", "true");
    test("null === void 0", "false");

    test("undefined !== void 0", "false");
    test("undefined === void 0", "true");
  }

  @Test
  public void testNullComparison1() {
    test("null == undefined", "true");
    test("null == null", "true");
    test("null == void 0", "true");

    test("null == 0", "false");
    test("null == 1", "false");
    test("null == 0n", "false");
    test("null == 1n", "false");
    test("null == 'hi'", "false");
    test("null == true", "false");
    test("null == false", "false");

    test("null === undefined", "false");
    test("null === null", "true");
    test("null === void 0", "false");
    testSame("null === x");

    testSame("null == this");
    testSame("null == x");

    test("null != undefined", "false");
    test("null != null", "false");
    test("null != void 0", "false");

    test("null != 0", "true");
    test("null != 1", "true");
    test("null != 0n", "true");
    test("null != 1n", "true");
    test("null != 'hi'", "true");
    test("null != true", "true");
    test("null != false", "true");

    test("null !== undefined", "true");
    test("null !== void 0", "true");
    test("null !== null", "false");

    testSame("null != this");
    testSame("null != x");

    test("null < null", "false");
    test("null > null", "false");
    test("null >= null", "true");
    test("null <= null", "true");

    test("0 < null", "false");
    test("0 > null", "false");
    test("0 >= null", "true");
    test("0n < null", "false");
    test("0n > null", "false");
    test("0n >= null", "true");
    test("true > null", "true");
    test("'hi' < null", "false");
    test("'hi' >= null", "false");
    test("null <= null", "true");

    test("null < 0", "false");
    test("null < 0n", "false");
    test("null > true", "false");
    test("null < 'hi'", "false");
    test("null >= 'hi'", "false");
    test("null <= null", "true");

    test("null == null", "true");
    test("0 == null", "false");
    test("1 == null", "false");
    test("'hi' == null", "false");
    test("true == null", "false");
    test("false == null", "false");
    test("null === null", "true");
    test("void 0 === null", "false");

    test("null == NaN", "false");
    test("NaN == null", "false");
    test("null == Infinity", "false");
    test("Infinity == null", "false");
    test("null == -Infinity", "false");
    test("-Infinity == null", "false");
    test("({}) == null", "false");
    test("null == ({})", "false");
    test("([]) == null", "false");
    test("null == ([])", "false");
    test("(/a/g) == null", "false");
    test("null == (/a/g)", "false");
    test("(function(){}) == null", "false");
    test("null == (function(){})", "false");

    test("null != NaN", "true");
    test("NaN != null", "true");
    test("null != Infinity", "true");
    test("Infinity != null", "true");
    test("null != -Infinity", "true");
    test("-Infinity != null", "true");
    test("({}) != null", "true");
    test("null != ({})", "true");
    test("([]) != null", "true");
    test("null != ([])", "true");
    test("(/a/g) != null", "true");
    test("null != (/a/g)", "true");
    test("(function(){}) != null", "true");
    test("null != (function(){})", "true");

    testSame("({a:f()}) == null");
    testSame("null == ({a:f()})");
    testSame("([f()]) == null");
    testSame("null == ([f()])");

    testSame("this == null");
    testSame("x == null");
  }

  @Test
  public void testBooleanBooleanComparison() {
    testSame("!x == !y");
    testSame("!x < !y");
    testSame("!x !== !y");

    testSame("!x == !x"); // foldable
    testSame("!x < !x"); // foldable
    testSame("!x !== !x"); // foldable
  }

  @Test
  public void testBooleanNumberComparison() {
    testSame("!x == +y");
    testSame("!x <= +y");
    test("!x !== +y", "true");
  }

  @Test
  public void testNumberBooleanComparison() {
    testSame("+x == !y");
    testSame("+x <= !y");
    test("+x === !y", "false");
  }

  @Test
  public void testBooleanStringComparison() {
    testSame("!x == '' + y");
    testSame("!x <= '' + y");
    test("!x !== '' + y", "true");
  }

  @Test
  public void testStringBooleanComparison() {
    testSame("'' + x == !y");
    testSame("'' + x <= !y");
    test("'' + x === !y", "false");
  }

  @Test
  public void testNumberNumberComparison() {
    test("1 > 1", "false");
    test("2 == 3", "false");
    test("3.6 === 3.6", "true");
    testSame("+x > +y");
    testSame("+x == +y");
    testSame("+x === +y");
    testSame("+x == +x");
    testSame("+x === +x");

    testSame("+x > +x"); // foldable
  }

  @Test
  public void testStringStringComparison() {
    test("'a' < 'b'", "true");
    test("'a' <= 'b'", "true");
    test("'a' > 'b'", "false");
    test("'a' >= 'b'", "false");
    test("+'a' < +'b'", "false");
    testSame("typeof a < 'a'");
    testSame("'a' >= typeof a");
    test("typeof a < typeof a", "false");
    test("typeof a >= typeof a", "true");
    test("typeof 3 > typeof 4", "false");
    test("typeof function() {} < typeof function() {}", "false");
    test("'a' == 'a'", "true");
    test("'b' != 'a'", "true");
    testSame("'undefined' == typeof a");
    testSame("typeof a != 'number'");
    testSame("'undefined' == typeof a");
    testSame("'undefined' == typeof a");
    test("typeof a == typeof a", "true");
    test("'a' === 'a'", "true");
    test("'b' !== 'a'", "true");
    test("typeof a === typeof a", "true");
    test("typeof a !== typeof a", "false");
    testSame("'' + x <= '' + y");
    testSame("'' + x != '' + y");
    testSame("'' + x === '' + y");

    testSame("'' + x <= '' + x"); // potentially foldable
    testSame("'' + x != '' + x"); // potentially foldable
    testSame("'' + x === '' + x"); // potentially foldable
  }

  @Test
  public void testNumberStringComparison() {
    test("1 < '2'", "true");
    test("2 > '1'", "true");
    test("123 > '34'", "true");
    test("NaN >= 'NaN'", "false");
    test("1 == '2'", "false");
    test("1 != '1'", "false");
    test("NaN == 'NaN'", "false");
    test("1 === '1'", "false");
    test("1 !== '1'", "true");
    testSame("+x > '' + y");
    testSame("+x == '' + y");
    test("+x !== '' + y", "true");
  }

  @Test
  public void testStringNumberComparison() {
    test("'1' < 2", "true");
    test("'2' > 1", "true");
    test("'123' > 34", "true");
    test("'NaN' < NaN", "false");
    test("'1' == 2", "false");
    test("'1' != 1", "false");
    test("'NaN' == NaN", "false");
    test("'1' === 1", "false");
    test("'1' !== 1", "true");
    testSame("'' + x < +y");
    testSame("'' + x == +y");
    test("'' + x === +y", "false");
  }

  @Test
  public void testBigIntNumberComparison() {
    test("1n < 2", "true");
    test("1n > 2", "false");
    test("1n == 1", "true");
    test("1n == 2", "false");

    // comparing with decimals is allowed
    test("1n < 1.1", "true");
    test("1n < 1.9", "true");
    test("1n < 0.9", "false");
    test("-1n < -1.1", "false");
    test("-1n < -1.9", "false");
    test("-1n < -0.9", "true");
    test("1n > 1.1", "false");
    test("1n > 0.9", "true");
    test("-1n > -1.1", "true");
    test("-1n > -0.9", "false");

    // Don't fold unsafely large numbers because there might be floating-point error
    final long maxSafeInt = 9007199254740991L;
    test("0n > " + maxSafeInt, "false");
    test("0n < " + maxSafeInt, "true");
    test("0n > " + -maxSafeInt, "true");
    test("0n < " + -maxSafeInt, "false");
    testSame("0n > " + (maxSafeInt + 1L));
    testSame("0n < " + (maxSafeInt + 1L));
    testSame("0n > " + -(maxSafeInt + 1L));
    testSame("0n < " + -(maxSafeInt + 1L));

    // comparing with Infinity is allowed
    test("1n < Infinity", "true");
    test("1n > Infinity", "false");
    test("1n < -Infinity", "false");
    test("1n > -Infinity", "true");

    // null is interpreted as 0 when comparing with bigint
    test("1n < null", "false");
    test("1n > null", "true");
  }

  @Test
  public void testBigIntStringComparison() {
    test("1n < '2'", "true");
    test("2n > '1'", "true");
    test("123n > '34'", "true");
    test("1n == '1'", "true");
    test("1n == '2'", "false");
    test("1n != '1'", "false");
    test("1n === '1'", "false");
    test("1n !== '1'", "true");
  }

  @Test
  public void testStringBigIntComparison() {
    test("'1' < 2n", "true");
    test("'2' > 1n", "true");
    test("'123' > 34n", "true");
    test("'1' == 1n", "true");
    test("'1' == 2n", "false");
    test("'1' != 1n", "false");
    test("'1' === 1n", "false");
    test("'1' !== 1n", "true");
  }

  @Test
  public void testNaNComparison() {
    test("NaN < 1", "false");
    test("NaN <= 1", "false");
    test("NaN > 1", "false");
    test("NaN >= 1", "false");
    test("NaN < 1n", "false");
    test("NaN <= 1n", "false");
    test("NaN > 1n", "false");
    test("NaN >= 1n", "false");

    test("NaN < NaN", "false");
    test("NaN >= NaN", "false");
    test("NaN == NaN", "false");
    test("NaN === NaN", "false");

    test("NaN < null", "false");
    test("null >= NaN", "false");
    test("NaN == null", "false");
    test("null != NaN", "true");
    test("null === NaN", "false");

    test("NaN < undefined", "false");
    test("undefined >= NaN", "false");
    test("NaN == undefined", "false");
    test("undefined != NaN", "true");
    test("undefined === NaN", "false");

    testSame("NaN < x");
    testSame("x >= NaN");
    testSame("NaN == x");
    testSame("x != NaN");
    test("NaN === x", "false");
    test("x !== NaN", "true");
    testSame("NaN == foo()");
  }

  @Test
  public void testObjectComparison1() {
    test("!new Date()", "false");
    test("!!new Date()", "true");

    test("new Date() == null", "false");
    test("new Date() == undefined", "false");
    test("new Date() != null", "true");
    test("new Date() != undefined", "true");
    test("null == new Date()", "false");
    test("undefined == new Date()", "false");
    test("null != new Date()", "true");
    test("undefined != new Date()", "true");
  }

  @Test
  public void testUnaryOps() {
    // Running on just changed code results in an exception on only the first invocation. Don't
    // repeat because it confuses the exception verification.
    numRepetitions = 1;

    // These cases are handled by PeepholeRemoveDeadCode.
    testSame("!foo()");
    testSame("~foo()");
    testSame("-foo()");

    // These cases are handled here.
    test("a=!true", "a=false");
    test("a=!10", "a=false");
    test("a=!false", "a=true");
    testSame("a=!foo()");
    test("a=-0", "a=-0.0");
    test("a=-(0)", "a=-0.0");
    testSame("a=-Infinity");
    test("a=-NaN", "a=NaN");
    testSame("a=-foo()");
    test("a=~~0", "a=0");
    test("a=~~10", "a=10");
    test("a=~-7", "a=6");

    test("a=+true", "a=1");
    test("a=+10", "a=10");
    test("a=+false", "a=0");
    testSame("a=+foo()");
    testSame("a=+f");
    test("a=+(f?true:false)", "a=+(f?1:0)"); // TODO(johnlenz): foldable
    test("a=+0", "a=0");
    test("a=+Infinity", "a=Infinity");
    test("a=+NaN", "a=NaN");
    test("a=+-7", "a=-7");
    test("a=+.5", "a=.5");

    test("a=~0xffffffff", "a=0");
    test("a=~~0xffffffff", "a=-1");
    testSame("a=~.5", PeepholeFoldConstants.FRACTIONAL_BITWISE_OPERAND);
  }

  @Test
  public void testUnaryOpsWithBigInt() {
    test("-(1n)", "-1n");
    test("- -1n", "1n");
    test("!1n", "false");
    test("~0n", "-1n");
  }

  @Test
  public void testUnaryOpsStringCompare() {
    testSame("a = -1");
    test("a = ~0", "a = -1");
    test("a = ~1", "a = -2");
    test("a = ~101", "a = -102");
  }

  @Test
  public void testFoldLogicalOp() {
    test("x = true && x", "x = x");
    test("x = [foo()] && x", "x = ([foo()],x)");

    test("x = false && x", "x = false");
    test("x = true || x", "x = true");
    test("x = false || x", "x = x");
    test("x = 0 && x", "x = 0");
    test("x = 3 || x", "x = 3");
    test("x = 0n && x", "x = 0n");
    test("x = 3n || x", "x = 3n");
    test("x = false || 0", "x = 0");

    // unfoldable, because the right-side may be the result
    test("a = x && true", "a=x && true");
    test("a = x && false", "a=x && false");
    test("a = x || 3", "a=x || 3");
    test("a = x || false", "a=x || false");
    test("a = b ? c : x || false", "a=b ? c:x || false");
    test("a = b ? x || false : c", "a=b ? x || false:c");
    test("a = b ? c : x && true", "a=b ? c:x && true");
    test("a = b ? x && true : c", "a=b ? x && true:c");

    // folded, but not here.
    testSame("a = x || false ? b : c");
    testSame("a = x && true ? b : c");

    test("x = foo() || true || bar()", "x = foo() || true");
    test("x = foo() || true && bar()", "x = foo() || bar()");
    test("x = foo() || false && bar()", "x = foo() || false");
    test("x = foo() && false && bar()", "x = foo() && false");
    test("x = foo() && false || bar()", "x = (foo() && false,bar())");
    test("x = foo() || false || bar()", "x = foo() || bar()");
    test("x = foo() && true && bar()", "x = foo() && bar()");
    test("x = foo() || true || bar()", "x = foo() || true");
    test("x = foo() && false && bar()", "x = foo() && false");
    test("x = foo() && 0 && bar()", "x = foo() && 0");
    test("x = foo() && 1 && bar()", "x = foo() && bar()");
    test("x = foo() || 0 || bar()", "x = foo() || bar()");
    test("x = foo() || 1 || bar()", "x = foo() || 1");
    test("x = foo() && 0n && bar()", "x = foo() && 0n");
    test("x = foo() && 1n && bar()", "x = foo() && bar()");
    test("x = foo() || 0n || bar()", "x = foo() || bar()");
    test("x = foo() || 1n || bar()", "x = foo() || 1n");
    testSame("x = foo() || bar() || baz()");
    testSame("x = foo() && bar() && baz()");

    test("0 || b()", "b()");
    test("1 && b()", "b()");
    test("a() && (1 && b())", "a() && b()");
    test("(a() && 1) && b()", "a() && b()");

    test("(x || '') || y;", "x || y");
    test("false || (x || '');", "x || ''");
    test("(x && 1) && y;", "x && y");
    test("true && (x && 1);", "x && 1");

    // Really not foldable, because it would change the type of the
    // expression if foo() returns something truthy but not true.
    // Cf. FoldConstants.tryFoldAndOr().
    // An example would be if foo() is 1 (truthy) and bar() is 0 (falsey):
    // (1 && true) || 0 == true
    // 1 || 0 == 1, but true =/= 1
    testSame("x = foo() && true || bar()");
    testSame("foo() && true || bar()");
  }

  @Test
  public void testFoldLogicalOp2() {
    test("x = function(){} && x", "x = x");
    test("x = true && function(){}", "x = function(){}");
    test("x = [(function(){alert(x)})()] && x", "x = ([(function(){alert(x)})()],x)");
  }

  @Test
  public void testFoldNullishCoalesce() {
    // fold if left is null/undefined
    test("null ?? 1", "1");
    test("undefined ?? false", "false");
    test("(a(), null) ?? 1", "(a(), null, 1)");

    test("x = [foo()] ?? x", "x = [foo()]");

    // short circuit on all non nullish LHS
    test("x = false ?? x", "x = false");
    test("x = true ?? x", "x = true");
    test("x = 0 ?? x", "x = 0");
    test("x = 3 ?? x", "x = 3");

    // unfoldable, because the right-side may be the result
    testSame("a = x ?? true");
    testSame("a = x ?? false");
    testSame("a = x ?? 3");
    testSame("a = b ? c : x ?? false");
    testSame("a = b ? x ?? false : c");

    // folded, but not here.
    testSame("a = x ?? false ? b : c");
    testSame("a = x ?? true ? b : c");

    testSame("x = foo() ?? true ?? bar()");
    ;
    test("x = foo() ?? (true && bar())", "x = foo() ?? bar()");
    testSame("x = (foo() || false) ?? bar()");

    test("a() ?? (1 ?? b())", "a() ?? 1");
    test("(a() ?? 1) ?? b()", "a() ?? 1 ?? b()");
  }

  @Test
  public void testFoldOptChain() {
    // can't fold when optional part may execute
    testSame("a = x?.y");
    testSame("a = x?.()");

    // fold args of optional call
    test("x = foo() ?. (true && bar())", "x = foo() ?.(bar())");
    test("a() ?. (1 ?? b())", "a() ?. (1)");

    test("({a})?.a.b.c.d()?.x.y.z", "a.b.c.d()?.x.y.z");

    // potential optimization
    testSame("x = undefined?.y"); // `x = void 0;`
  }

  @Test
  public void testFoldBitwiseOp() {
    test("x = 1 & 1", "x = 1");
    test("x = 1 & 2", "x = 0");
    test("x = 3 & 1", "x = 1");
    test("x = 3 & 3", "x = 3");

    test("x = 1 | 1", "x = 1");
    test("x = 1 | 2", "x = 3");
    test("x = 3 | 1", "x = 3");
    test("x = 3 | 3", "x = 3");

    test("x = 1 ^ 1", "x = 0");
    test("x = 1 ^ 2", "x = 3");
    test("x = 3 ^ 1", "x = 2");
    test("x = 3 ^ 3", "x = 0");

    test("x = -1 & 0", "x = 0");
    test("x = 0 & -1", "x = 0");
    test("x = 1 & 4", "x = 0");
    test("x = 2 & 3", "x = 2");

    // make sure we fold only when we are supposed to -- not when doing so would
    // lose information or when it is performed on nonsensical arguments.
    test("x = 1 & 1.1", "x = 1");
    test("x = 1.1 & 1", "x = 1");
    test("x = 1 & 3000000000", "x = 0");
    test("x = 3000000000 & 1", "x = 0");

    // Try some cases with | as well
    test("x = 1 | 4", "x = 5");
    test("x = 1 | 3", "x = 3");
    test("x = 1 | 1.1", "x = 1");
    testSame("x = 1 | 3E9");

    // these cases look strange because bitwise OR converts unsigned numbers to be signed
    test("x = 1 | 3000000001", "x = -1294967295");
    test("x = 4294967295 | 0", "x = -1");
  }

  @Test
  public void testFoldBitwiseOp2() {
    test("x = y & 1 & 1", "x = y & 1");
    test("x = y & 1 & 2", "x = y & 0");
    test("x = y & 3 & 1", "x = y & 1");
    test("x = 3 & y & 1", "x = y & 1");
    test("x = y & 3 & 3", "x = y & 3");
    test("x = 3 & y & 3", "x = y & 3");

    test("x = y | 1 | 1", "x = y | 1");
    test("x = y | 1 | 2", "x = y | 3");
    test("x = y | 3 | 1", "x = y | 3");
    test("x = 3 | y | 1", "x = y | 3");
    test("x = y | 3 | 3", "x = y | 3");
    test("x = 3 | y | 3", "x = y | 3");

    test("x = y ^ 1 ^ 1", "x = y ^ 0");
    test("x = y ^ 1 ^ 2", "x = y ^ 3");
    test("x = y ^ 3 ^ 1", "x = y ^ 2");
    test("x = 3 ^ y ^ 1", "x = y ^ 2");
    test("x = y ^ 3 ^ 3", "x = y ^ 0");
    test("x = 3 ^ y ^ 3", "x = y ^ 0");

    test("x = Infinity | NaN", "x=0");
    test("x = 12 | NaN", "x=12");
  }

  @Test
  public void testFoldBitwiseOpWithBigInt() {
    test("x = 1n & 1n", "x = 1n");
    test("x = 1n & 2n", "x = 0n");
    test("x = 3n & 1n", "x = 1n");
    test("x = 3n & 3n", "x = 3n");

    test("x = 1n | 1n", "x = 1n");
    test("x = 1n | 2n", "x = 3n");
    test("x = 1n | 3n", "x = 3n");
    test("x = 3n | 1n", "x = 3n");
    test("x = 3n | 3n", "x = 3n");
    test("x = 1n | 4n", "x = 5n");

    test("x = 1n ^ 1n", "x = 0n");
    test("x = 1n ^ 2n", "x = 3n");
    test("x = 3n ^ 1n", "x = 2n");
    test("x = 3n ^ 3n", "x = 0n");

    test("x = -1n & 0n", "x = 0n");
    test("x = 0n & -1n", "x = 0n");
    test("x = 1n & 4n", "x = 0n");
    test("x = 2n & 3n", "x = 2n");

    test("x = 1n & 3000000000n", "x = 0n");
    test("x = 3000000000n & 1n", "x = 0n");

    // bitwise OR does not affect the sign of a bigint
    test("x = 1n | 3000000001n", "x = 3000000001n");
    test("x = 4294967295n | 0n", "x = 4294967295n");

    test("x = y & 1n & 1n", "x = y & 1n");
    test("x = y & 1n & 2n", "x = y & 0n");
    test("x = y & 3n & 1n", "x = y & 1n");
    test("x = 3n & y & 1n", "x = y & 1n");
    test("x = y & 3n & 3n", "x = y & 3n");
    test("x = 3n & y & 3n", "x = y & 3n");

    test("x = y | 1n | 1n", "x = y | 1n");
    test("x = y | 1n | 2n", "x = y | 3n");
    test("x = y | 3n | 1n", "x = y | 3n");
    test("x = 3n | y | 1n", "x = y | 3n");
    test("x = y | 3n | 3n", "x = y | 3n");
    test("x = 3n | y | 3n", "x = y | 3n");

    test("x = y ^ 1n ^ 1n", "x = y ^ 0n");
    test("x = y ^ 1n ^ 2n", "x = y ^ 3n");
    test("x = y ^ 3n ^ 1n", "x = y ^ 2n");
    test("x = 3n ^ y ^ 1n", "x = y ^ 2n");
    test("x = y ^ 3n ^ 3n", "x = y ^ 0n");
    test("x = 3n ^ y ^ 3n", "x = y ^ 0n");
  }

  @Test
  public void testFoldingMixTypesLate() {
    late = true;
    disableNormalize();
    test("x = x + '2'", "x+='2'");
    test("x = +x + +'2'", "x = +x + 2");
    test("x = x - '2'", "x-=2");
    test("x = x ^ '2'", "x^=2");
    test("x = '2' ^ x", "x^=2");
    test("x = '2' & x", "x&=2");
    test("x = '2' | x", "x|=2");

    test("x = '2' | y", "x=2|y");
    test("x = y | '2'", "x=y|2");
    test("x = y | (a && '2')", "x=y|(a&&2)");
    test("x = y | (a,'2')", "x=y|(a,2)");
    test("x = y | (a?'1':'2')", "x=y|(a?1:2)");
    test("x = y | ('x'?'1':'2')", "x=y|('x'?1:2)");
  }

  @Test
  public void testFoldingMixTypesEarly() {
    late = false;
    testSame("x = x + '2'");
    test("x = +x + +'2'", "x = +x + 2");
    test("x = x - '2'", "x = x - 2");
    test("x = x ^ '2'", "x = x ^ 2");
    test("x = '2' ^ x", "x = 2 ^ x");
    test("x = '2' & x", "x = 2 & x");
    test("x = '2' | x", "x = 2 | x");

    test("x = '2' | y", "x=2|y");
    test("x = y | '2'", "x=y|2");
    test("x = y | (a && '2')", "x=y|(a&&2)");
    test("x = y | (a,'2')", "x=y|(a,2)");
    test("x = y | (a?'1':'2')", "x=y|(a?1:2)");
    test("x = y | ('x'?'1':'2')", "x=y|('x'?1:2)");
  }

  @Test
  public void testFoldingAdd1() {
    test("x = null + true", "x=1");
    testSame("x = a + true");
    test("x = '' + {}", "x = '[object Object]'");
    test("x = [] + {}", "x = '[object Object]'");
    test("x = {} + []", "x = '[object Object]'");
    test("x = {} + ''", "x = '[object Object]'");
  }

  @Test
  public void testFoldingAdd2() {
    test("x = false + []", "x='false'");
    test("x = [] + true", "x='true'");
    test("NaN + []", "'NaN'");
  }

  @Test
  public void testFoldBitwiseOpStringCompare() {
    test("x = -1 | 0", "x = -1");
  }

  @Test
  public void testFoldBitShifts() {
    // Running on just changed code results in an exception on only the first invocation. Don't
    // repeat because it confuses the exception verification.
    numRepetitions = 1;

    test("x = 1 << 0", "x = 1");
    test("x = -1 << 0", "x = -1");
    test("x = 1 << 1", "x = 2");
    test("x = 3 << 1", "x = 6");
    test("x = 1 << 8", "x = 256");

    test("x = 1 >> 0", "x = 1");
    test("x = -1 >> 0", "x = -1");
    test("x = 1 >> 1", "x = 0");
    test("x = 2 >> 1", "x = 1");
    test("x = 5 >> 1", "x = 2");
    test("x = 127 >> 3", "x = 15");
    test("x = 3 >> 1", "x = 1");
    test("x = 3 >> 2", "x = 0");
    test("x = 10 >> 1", "x = 5");
    test("x = 10 >> 2", "x = 2");
    test("x = 10 >> 5", "x = 0");

    test("x = 10 >>> 1", "x = 5");
    test("x = 10 >>> 2", "x = 2");
    test("x = 10 >>> 5", "x = 0");
    test("x = -1 >>> 1", "x = 2147483647"); // 0x7fffffff
    test("x = -1 >>> 0", "x = 4294967295"); // 0xffffffff
    test("x = -2 >>> 0", "x = 4294967294"); // 0xfffffffe
    test("x = 0x90000000 >>> 28", "x = 9");

    test("x = 0xffffffff << 0", "x = -1");
    test("x = 0xffffffff << 4", "x = -16");
    testSame("1 << 32");
    testSame("1 << -1");
    testSame("1 >> 32");
    testSame("1.5 << 0", PeepholeFoldConstants.FRACTIONAL_BITWISE_OPERAND);
    testSame("1 << .5", PeepholeFoldConstants.FRACTIONAL_BITWISE_OPERAND);
    testSame("1.5 >>> 0", PeepholeFoldConstants.FRACTIONAL_BITWISE_OPERAND);
    testSame("1 >>> .5", PeepholeFoldConstants.FRACTIONAL_BITWISE_OPERAND);
    testSame("1.5 >> 0", PeepholeFoldConstants.FRACTIONAL_BITWISE_OPERAND);
    testSame("1 >> .5", PeepholeFoldConstants.FRACTIONAL_BITWISE_OPERAND);
  }

  @Test
  public void testFoldBitShiftsStringCompare() {
    test("x = -1 << 1", "x = -2");
    test("x = -1 << 8", "x = -256");
    test("x = -1 >> 1", "x = -1");
    test("x = -2 >> 1", "x = -1");
    test("x = -1 >> 0", "x = -1");
  }

  @Test
  public void testStringAdd() {
    test("x = 'a' + 'bc'", "x = 'abc'");
    test("x = 'a' + 5", "x = 'a5'");
    test("x = 5 + 'a'", "x = '5a'");
    test("x = 'a' + 5n", "x = 'a5n'");
    test("x = 5n + 'a'", "x = '5na'");
    test("x = 'a' + ''", "x = 'a'");
    test("x = 'a' + foo()", "x = 'a'+foo()");
    test("x = foo() + 'a' + 'b'", "x = foo()+'ab'");
    test("x = (foo() + 'a') + 'b'", "x = foo()+'ab'"); // believe it!
    test("x = foo() + 'a' + 'b' + 'cd' + bar()", "x = foo()+'abcd'+bar()");
    test("x = foo() + 2 + 'b'", "x = foo()+2+\"b\""); // don't fold!
    test("x = foo() + 'a' + 2", "x = foo()+\"a2\"");
    test("x = '' + null", "x = 'null'");
    test("x = true + '' + false", "x = 'truefalse'");
    test("x = '' + []", "x = ''");
    test("x = foo() + 'a' + 1 + 1", "x = foo() + 'a11'");
    test("x = 1 + 1 + 'a'", "x = '2a'");
    test("x = 1 + 1 + 'a'", "x = '2a'");
    test("x = 'a' + (1 + 1)", "x = 'a2'");
    test("x = '_' + p1 + '_' + ('' + p2)", "x = '_' + p1 + '_' + p2");
    test("x = 'a' + ('_' + 1 + 1)", "x = 'a_11'");
    test("x = 'a' + ('_' + 1) + 1", "x = 'a_11'");
    test("x = 1 + (p1 + '_') + ('' + p2)", "x = 1 + (p1 + '_') + p2");
    test("x = 1 + p1 + '_' + ('' + p2)", "x = 1 + p1 + '_' + p2");
    test("x = 1 + 'a' + p1", "x = '1a' + p1");
    test("x = (p1 + (p2 + 'a')) + 'b'", "x = (p1 + (p2 + 'ab'))");
    test("'a' + ('b' + p1) + 1", "'ab' + p1 + 1");
    test("x = 'a' + ('b' + p1 + 'c')", "x = 'ab' + (p1 + 'c')");
    testSame("x = 'a' + (4 + p1 + 'a')");
    testSame("x = p1 / 3 + 4");
    testSame("foo() + 3 + 'a' + foo()");
    testSame("x = 'a' + ('b' + p1 + p2)");
    testSame("x = 1 + ('a' + p1)");
    testSame("x = p1 + '' + p2");
    testSame("x = 'a' + (1 + p1)");
    testSame("x = (p2 + 'a') + (1 + p1)");
    testSame("x = (p2 + 'a') + (1 + p1 + p2)");
    testSame("x = (p2 + 'a') + (1 + (p1 + p2))");
  }

  @Test
  public void testStringAdd_identity() {
    enableTypeCheck();
    replaceTypesWithColors();
    disableCompareJsDoc();
    foldStringTypes("x + ''", "x");
    foldStringTypes("'' + x", "x");
  }

  @Test
  public void testIssue821() {
    testSame("var a =(Math.random()>0.5? '1' : 2 ) + 3 + 4;");
    testSame("var a = ((Math.random() ? 0 : 1) || (Math.random()>0.5? '1' : 2 )) + 3 + 4;");
  }

  @Test
  public void testFoldConstructor() {
    test("x = this[new String('a')]", "x = this['a']");
    test("x = ob[new String(12)]", "x = ob['12']");
    test("x = ob[new String(false)]", "x = ob['false']");
    test("x = ob[new String(null)]", "x = ob['null']");
    test("x = 'a' + new String('b')", "x = 'ab'");
    test("x = 'a' + new String(23)", "x = 'a23'");
    test("x = 2 + new String(1)", "x = '21'");
    testSame("x = ob[new String(a)]");
    testSame("x = new String('a')");
    testSame("x = (new String('a'))[3]");
  }

  @Test
  public void testFoldArithmetic() {
    test("x = 10 + 20", "x = 30");
    test("x = 2 / 4", "x = 0.5");
    test("x = 2.25 & 3", "x = 2");
    testSame("z = x & y");
    testSame("x = y & 5");
    testSame("x = 1 / 0");
    test("x = 3 % 2", "x = 1");
    test("x = 3 % -2", "x = 1");
    test("x = -1 % 3", "x = -1");
    testSame("x = 1 % 0");
    test("x = 2 ** 3", "x = 8");
    test("x = 2 ** -3", "x = 0.125");
    testSame("x = 2 ** 55"); // backs off folding because 2 ** 55 is too large
    testSame("x = 3 ** -1"); // backs off because 3**-1 is shorter than 0.3333333333333333
  }

  @Test
  public void testFoldArithmetic2() {
    testSame("x = y + 10 + 20");
    testSame("x = y / 2 / 4");
    test("x = y & 2.25 & 3", "x = y & 2");
    testSame("x = y & 2.25 & z & 3");
    testSame("z = x &y");
    testSame("x = y & 5");
    test("x = y + (z & 24 & 60 & 60 & 1000)", "x = y + (z & 8)");
  }

  @Test
  public void testFoldArithmetic3() {
    test("x = null | undefined", "x = 0");
    test("x = null | 1", "x = 1");
    test("x = (null - 1)| 2", "x = -1");
    test("x = (null + 1) | 2", "x = 3");
    test("x = null ** 0", "x = 1");
    test("x = (-0) ** 3", "x = -0");
  }

  @Test
  public void testFoldArithmeticInfinity() {
    test("x=-Infinity-2", "x=-Infinity");
    test("x=Infinity-2", "x=Infinity");
    test("x=Infinity*5", "x=Infinity");
    test("x = Infinity ** 2", "x = Infinity");
    test("x = Infinity ** -2", "x = 0");
  }

  @Test
  public void testFoldArithmeticStringComp() {
    test("x = 10 - 20", "x = -10");
  }

  @Test
  public void testNoFoldArithmeticWithSideEffects() {
    // can't fold this to "x = y & 6.75" because you can't remove the "sideEffects()" call
    testSame("x = y & 2.25 & (sideEffects(), 3)");
  }

  @Test
  public void testFoldBigIntArithmetic() {
    test("x = 1n + 2n", "x = 3n");
    test("x = 1n - 2n", "x = -1n");
    test("x = 2n * 3n", "x = 6n");
    test("x = 6n / 2n", "x = 3n");
    test("x = 3n % 2n", "x = 1n");
    test("x = 2n ** 3n", "x = 8n");

    // The compiler is not designed to fold expressions with an exponent > 2147483647
    test("x = 1n ** 2147483647n", "x = 1n");
    testSame("x = 1n ** 2147483648n");

    test("x = y & 2n & 3n", "x = y & 2n");

    // TODO b/361826515: Optimize associative bigint operations
    testSame("x = y * 2n * z * 3n");
    testSame("x = y + 2n + z + 3n");
  }

  @Test
  public void testNoFoldBigIntArithmeticWithSideEffects() {
    // can't fold this to "x = y * 6.75" because you can't remove the "sideEffects()" call
    testSame("x = y * 2n * (sideEffects(), 3n)");
  }

  @Test
  public void testFoldComparison() {
    test("x = 0 == 0", "x = true");
    test("x = 1 == 2", "x = false");
    test("x = 0n == 0n", "x = true");
    test("x = 1n == 2n", "x = false");
    test("x = 'abc' == 'def'", "x = false");
    test("x = 'abc' == 'abc'", "x = true");
    test("x = \"\" == ''", "x = true");
    test("x = foo() == bar()", "x = foo()==bar()");

    test("x = 1 != 0", "x = true");
    test("x = 1 != 1", "x = false");
    test("x = 1n != 0n", "x = true");
    test("x = 1n != 1n", "x = false");
    test("x = 'abc' != 'def'", "x = true");
    test("x = 'a' != 'a'", "x = false");

    test("x = 1 < 20", "x = true");
    test("x = 3 < 3", "x = false");
    test("x = 10 > 1.0", "x = true");
    test("x = 10 > 10.25", "x = false");
    test("x = 1 <= 1", "x = true");
    test("x = 1 <= 0", "x = false");
    test("x = 0 >= 0", "x = true");
    test("x = -1 >= 9", "x = false");

    test("x = 1n < 20n", "x = true");
    test("x = 3n < 3n", "x = false");
    test("x = 10n > 1n", "x = true");
    test("x = 10n > 10n", "x = false");
    test("x = 1n <= 1n", "x = true");
    test("x = 1n <= 0n", "x = false");
    test("x = 0n >= 0n", "x = true");
    test("x = -1n >= 9n", "x = false");

    testSame("x = y == y");
    test("x = y < y", "x = false");
    test("x = y > y", "x = false");

    test("x = true == true", "x = true");
    test("x = false == false", "x = true");
    test("x = false == null", "x = false");
    test("x = false == true", "x = false");
    test("x = true == null", "x = false");

    test("0 == 0", "true");
    test("1 == 2", "false");
    test("0n == 0n", "true");
    test("1n == 2n", "false");
    test("'abc' == 'def'", "false");
    test("'abc' == 'abc'", "true");
    test("\"\" == ''", "true");
    testSame("foo() == bar()");

    test("1 != 0", "true");
    test("1 != 1", "false");
    test("1n != 0n", "true");
    test("1n != 1n", "false");
    test("'abc' != 'def'", "true");
    test("'a' != 'a'", "false");

    test("1 < 20", "true");
    test("3 < 3", "false");
    test("10 > 1.0", "true");
    test("10 > 10.25", "false");
    testSame("x == x");
    test("x < x", "false");
    test("x > x", "false");
    test("1 <= 1", "true");
    test("1 <= 0", "false");
    test("0 >= 0", "true");
    test("-1 >= 9", "false");

    test("1n < 20n", "true");
    test("3n < 3n", "false");
    test("10n > 1n", "true");
    test("10n > 10n", "false");
    test("1n <= 1n", "true");
    test("1n <= 0n", "false");
    test("0n >= 0n", "true");
    test("-1n >= 9n", "false");

    test("true == true", "true");
    test("false == null", "false");
    test("false == true", "false");
    test("true == null", "false");
  }

  // ===, !== comparison tests
  @Test
  public void testFoldComparison2() {
    test("x = 0 === 0", "x = true");
    test("x = 1 === 2", "x = false");
    test("x = 0n === 0n", "x = true");
    test("x = 1n === 2n", "x = false");
    test("x = 'abc' === 'def'", "x = false");
    test("x = 'abc' === 'abc'", "x = true");
    test("x = \"\" === ''", "x = true");
    test("x = foo() === bar()", "x = foo()===bar()");

    test("x = 1 !== 0", "x = true");
    test("x = 1 !== 1", "x = false");
    test("x = 1n !== 0n", "x = true");
    test("x = 1n !== 1n", "x = false");
    test("x = 'abc' !== 'def'", "x = true");
    test("x = 'a' !== 'a'", "x = false");

    test("x = y === y", "x = y===y");

    test("x = true === true", "x = true");
    test("x = false === false", "x = true");
    test("x = false === null", "x = false");
    test("x = false === true", "x = false");
    test("x = true === null", "x = false");

    test("0 === 0", "true");
    test("1 === 2", "false");
    test("0n === 0n", "true");
    test("1n === 2n", "false");
    test("'abc' === 'def'", "false");
    test("'abc' === 'abc'", "true");
    test("\"\" === ''", "true");
    testSame("foo() === bar()");

    test("1 === '1'", "false");
    test("1 === true", "false");
    test("1 !== '1'", "true");
    test("1 !== true", "true");

    test("1 !== 0", "true");
    test("'abc' !== 'def'", "true");
    test("'a' !== 'a'", "false");

    testSame("x === x");

    test("true === true", "true");
    test("false === null", "false");
    test("false === true", "false");
    test("true === null", "false");
  }

  @Test
  public void testFoldComparison3() {
    test("x = !1 == !0", "x = false");

    test("x = !0 == !0", "x = true");
    test("x = !1 == !1", "x = true");
    test("x = !1 == null", "x = false");
    test("x = !1 == !0", "x = false");
    test("x = !0 == null", "x = false");

    test("!0 == !0", "true");
    test("!1 == null", "false");
    test("!1 == !0", "false");
    test("!0 == null", "false");

    test("x = !0 === !0", "x = true");
    test("x = !1 === !1", "x = true");
    test("x = !1 === null", "x = false");
    test("x = !1 === !0", "x = false");
    test("x = !0 === null", "x = false");

    test("!0 === !0", "true");
    test("!1 === null", "false");
    test("!1 === !0", "false");
    test("!0 === null", "false");
  }

  @Test
  public void testFoldComparison4() {
    testSame("[] == false"); // true
    testSame("[] == true"); // false
    testSame("[0] == false"); // true
    testSame("[0] == true"); // false
    testSame("[1] == false"); // false
    testSame("[1] == true"); // true
    testSame("({}) == false"); // false
    testSame("({}) == true"); // true
  }

  @Test
  public void testFoldGetElem1() {
    // Running on just changed code results in an exception on only the first invocation. Don't
    // repeat because it confuses the exception verification.
    numRepetitions = 1;

    test("x = [,10][0]", "x = void 0");
    test("x = [10, 20][0]", "x = 10");
    test("x = [10, 20][1]", "x = 20");

    testSame("x = [10, 20][0.5]", PeepholeFoldConstants.INVALID_GETELEM_INDEX_ERROR);
    test("x = [10, 20][-1]", "x = void 0;");
    test("x = [10, 20][2]", "x = void 0;");

    testSame("x = [foo(), 0][1]");
    test("x = [0, foo()][1]", "x = foo()");
    testSame("x = [0, foo()][0]");
    testSame("for([1][0] in {});");
  }

  /** Optional versions of the above `testFoldGetElem1` tests */
  @Test
  public void testFoldOptChainGetElem1() {
    numRepetitions = 1;
    test("x = [,10]?.[0]", "x = void 0");
    test("x = [10, 20]?.[0]", "x = 10");
    test("x = [10, 20]?.[1]", "x = 20");

    testSame("x = [10, 20]?.[0.5]", PeepholeFoldConstants.INVALID_GETELEM_INDEX_ERROR);
    test("x = [10, 20]?.[-1]", "x = void 0;");
    test("x = [10, 20]?.[2]", "x = void 0;");

    testSame("x = [foo(), 0]?.[1]");
    test("x = [0, foo()]?.[1]", "x = foo()");
    testSame("x = [0, foo()]?.[0]");
  }

  @Test
  public void testFoldGetElem2() {
    // Running on just changed code results in an exception on only the first invocation. Don't
    // repeat because it confuses the exception verification.
    numRepetitions = 1;

    test("x = 'string'[5]", "x = 'g'");
    test("x = 'string'[0]", "x = 's'");
    test("x = 's'[0]", "x = 's'");
    testSame("x = '\uD83D\uDCA9'[0]");

    testSame("x = 'string'[0.5]", PeepholeFoldConstants.INVALID_GETELEM_INDEX_ERROR);
    test("x = 'string'[-1]", "x = void 0;");
    test("x = 'string'[6]", "x = void 0;");
  }

  /** Optional versions of the above `testFoldGetElem2` tests */
  @Test
  public void testFoldOptChainGetElem2() {
    // Running on just changed code results in an exception on only the first invocation. Don't
    // repeat because it confuses the exception verification.
    numRepetitions = 1;
    test("x = 'string'?.[5]", "x = 'g'");
    test("x = 'string'?.[0]", "x = 's'");
    test("x = 's'?.[0]", "x = 's'");
    testSame("x = '\uD83D\uDCA9'?.[0]");

    testSame("x = 'string'?.[0.5]", PeepholeFoldConstants.INVALID_GETELEM_INDEX_ERROR);
    test("x = 'string'?.[-1]", "x = void 0;");
    test("x = 'string'?.[6]", "x = void 0;");
  }

  @Test
  public void testFoldArrayLitSpreadGetElem() {
    numRepetitions = 1;
    test("x = [...[0]][0]", "x = 0;");
    test("x = [0, 1, ...[2, 3, 4]][3]", "x = 3;");
    test("x = [...[0, 1], 2, ...[3, 4]][3]", "x = 3;");
    test("x = [...[...[0, 1], 2, 3], 4][0]", "x = 0");
    test("x = [...[...[0, 1], 2, 3], 4][3]", "x = 3");
    test(srcs("x = [...[]][100]"), expected("x = void 0;"));
    test(srcs("x = [...[0]][100]"), expected("x = void 0;"));
  }

  /** Optional versions of the above `testFoldArrayLitSpreadGetElem` tests */
  @Test
  public void testFoldArrayLitSpreadOptChainGetElem() {
    numRepetitions = 1;
    test("x = [...[0]]?.[0]", "x = 0;");
    test("x = [0, 1, ...[2, 3, 4]]?.[3]", "x = 3;");
    test("x = [...[0, 1], 2, ...[3, 4]]?.[3]", "x = 3;");
    test("x = [...[...[0, 1], 2, 3], 4]?.[0]", "x = 0");
    test("x = [...[...[0, 1], 2, 3], 4]?.[3]", "x = 3");
    test(srcs("x = [...[]]?.[100]"), expected("x = void 0;"));
    test(srcs("x = [...[0]]?.[100]"), expected("x = void 0;"));
  }

  @Test
  public void testDontFoldNonLiteralSpreadGetElem() {
    testSame("x = [...iter][0];");
    testSame("x = [0, 1, ...iter][2];");
    //  `...iter` could have side effects, so don't replace `x` with `0`
    testSame("x = [0, 1, ...iter][0];");
  }

  @Test
  public void testFoldArraySpread() {
    numRepetitions = 1;
    test("x = [...[]]", "x = []");
    test("x = [0, ...[], 1]", "x = [0, 1]");
    test("x = [...[0, 1], 2, ...[3, 4]]", "x = [0, 1, 2, 3, 4]");
    test("x = [...[...[0], 1], 2]", "x = [0, 1, 2]");
    testSame("[...[x]] = arr");
    test("foo([...[...[0], 1], 2])", "foo([0, 1, 2])");
  }

  @Test
  public void testFoldArrayLitSpreadInArg() {
    test("foo(...[0], 1)", "foo(0, 1)");
    testSame("foo(...[,,,,\"foo\"], 1)");
    testSame("foo(...(false ? [0] : [1]))"); // other opts need to fold the ternery first
  }

  @Test
  public void testFoldObjectLitSpreadGetProp() {
    numRepetitions = 1;
    test("x = {...{a}}.a", "x = a;");
    test("x = {a, b, ...{c, d, e}}.d", "x = d;");
    test("x = {...{a, b}, c, ...{d, e}}.d", "x = d;");
    test("x = {...{...{a, b}, c, d}, e}.a", "x = a");
    test("x = {...{...{a, b}, c, d}, e}.d", "x = d");
  }

  @Test
  public void testDontFoldNonLiteralObjectSpreadGetProp_gettersImpure() {
    this.assumeGettersPure = false;

    testSame("x = {...obj}.a;");
    testSame("x = {a, ...obj, c}.a;");
    testSame("x = {a, ...obj, c}.c;");
  }

  @Test
  public void testDontFoldNonLiteralObjectSpreadGetProp_assumeGettersPure() {
    this.assumeGettersPure = true;

    testSame("x = {...obj}.a;");
    testSame("x = {a, ...obj, c}.a;");
    test("x = {a, ...obj, c}.c;", "x = c;"); // We assume object spread has no side-effects.
  }

  @Test
  public void testFoldObjectSpread() {
    numRepetitions = 1;
    test("x = {...{}}", "x = {}");
    test("x = {a, ...{}, b}", "x = {a, b}");
    test("x = {...{a, b}, c, ...{d, e}}", "x = {a, b, c, d, e}");
    test("x = {...{...{a}, b}, c}", "x = {a, b, c}");
    testSame("({...{x}} = obj)");
  }

  @Test
  public void testDontFoldMixedObjectAndArraySpread() {
    numRepetitions = 1;
    testSame("x = [...{}]");
    testSame("x = {...[]}");
    test("x = [a, ...[...{}]]", "x = [a, ...{}]");
    test("x = {a, ...{...[]}}", "x = {a, ...[]}");
  }

  @Test
  public void testFoldComplex() {
    test("x = (3 / 1.0) + (1 * 2)", "x = 5");
    test("x = (1 == 1.0) && foo() && true", "x = foo()&&true");
    test("x = 'abc' + 5 + 10", "x = \"abc510\"");
  }

  @Test
  public void testFoldLeft() {
    testSame("(+x - 1) + 2"); // not yet
    test("(+x & 1) & 2", "+x & 0");
  }

  @Test
  public void testFoldArrayLength() {
    // Can fold
    test("x = [].length", "x = 0");
    test("x = [1,2,3].length", "x = 3");
    test("x = [a,b].length", "x = 2");

    // Not handled yet
    test("x = [,,1].length", "x = 3");

    // Cannot fold
    test("x = [foo(), 0].length", "x = [foo(),0].length");
    testSame("x = y.length");
  }

  @Test
  public void testFoldStringLength() {
    // Can fold basic strings.
    test("x = ''.length", "x = 0");
    test("x = '123'.length", "x = 3");

    // Test Unicode escapes are accounted for.
    test("x = '123\u01dc'.length", "x = 4");
  }

  @Test
  public void testFoldTypeof() {
    test("x = typeof 1", "x = \"number\"");
    test("x = typeof 'foo'", "x = \"string\"");
    test("x = typeof true", "x = \"boolean\"");
    test("x = typeof false", "x = \"boolean\"");
    test("x = typeof null", "x = \"object\"");
    test("x = typeof undefined", "x = \"undefined\"");
    test("x = typeof void 0", "x = \"undefined\"");
    test("x = typeof []", "x = \"object\"");
    test("x = typeof [1]", "x = \"object\"");
    test("x = typeof [1,[]]", "x = \"object\"");
    test("x = typeof {}", "x = \"object\"");
    test("x = typeof function() {}", "x = 'function'");

    testSame("x = typeof[1,[foo()]]");
    testSame("x = typeof{bathwater:baby()}");
  }

  @Test
  public void testFoldInstanceOf() {
    // Non object types are never instances of anything.
    test("64 instanceof Object", "false");
    test("64 instanceof Number", "false");
    test("'' instanceof Object", "false");
    test("'' instanceof String", "false");
    test("true instanceof Object", "false");
    test("true instanceof Boolean", "false");
    test("!0 instanceof Object", "false");
    test("!0 instanceof Boolean", "false");
    test("false instanceof Object", "false");
    test("null instanceof Object", "false");
    test("undefined instanceof Object", "false");
    test("NaN instanceof Object", "false");
    test("Infinity instanceof Object", "false");

    // Array and object literals are known to be objects.
    test("[] instanceof Object", "true");
    test("({}) instanceof Object", "true");

    // These cases is foldable, but no handled currently.
    testSame("new Foo() instanceof Object");
    // These would require type information to fold.
    testSame("[] instanceof Foo");
    testSame("({}) instanceof Foo");

    test("(function() {}) instanceof Object", "true");

    // An unknown value should never be folded.
    testSame("x instanceof Foo");
  }

  @Test
  public void testDivision() {
    // Make sure the 1/3 does not expand to 0.333333
    testSame("print(1/3)");

    // Decimal form is preferable to fraction form when strings are the
    // same length.
    test("print(1/2)", "print(0.5)");
  }

  @Test
  public void testAssignOpsLate() {
    late = true;
    disableNormalize();
    test("x=x+y", "x+=y");
    testSame("x=y+x");
    test("x=x*y", "x*=y");
    test("x=y*x", "x*=y");
    test("x.y=x.y+z", "x.y+=z");
    testSame("next().x = next().x + 1");

    test("x=x-y", "x-=y");
    testSame("x=y-x");
    test("x=x|y", "x|=y");
    test("x=y|x", "x|=y");
    test("x=x|y|z", "x|=y|z");
    testSame("x=x&&y&&z");
    test("x=x*y", "x*=y");
    test("x=y*x", "x*=y");
    test("x=x**y", "x**=y");
    testSame("x=y**x");
    test("x.y=x.y+z", "x.y+=z");
    testSame("next().x = next().x + 1");
    // This is OK, really.
    test("({a:1}).a = ({a:1}).a + 1", "({a:1}).a = 2");
  }

  @Test
  public void testAssignOpsEarly() {
    late = false;
    testSame("x=x+y");
    testSame("x=y+x");
    testSame("x=x*y");
    testSame("x=y*x");
    testSame("x.y=x.y+z");
    testSame("next().x = next().x + 1");

    testSame("x=x-y");
    testSame("x=y-x");
    testSame("x=x|y");
    testSame("x=y|x");
    testSame("x=x*y");
    testSame("x=y*x");
    testSame("x=x**y");
    testSame("x=y**2");
    testSame("x.y=x.y+z");
    testSame("next().x = next().x + 1");
    // This is OK, really.
    test("({a:1}).a = ({a:1}).a + 1", "({a:1}).a = 2");
  }

  @Test
  public void testUnfoldAssignOpsLate() {
    late = true;
    disableNormalize();
    testSame("x+=y");
    testSame("x*=y");
    testSame("x.y+=z");
    testSame("x-=y");
    testSame("x|=y");
    testSame("x*=y");
    testSame("x**=y");
    testSame("x.y+=z");
  }

  @Test
  public void testUnfoldAssignOpsEarly() {
    late = false;
    test("x+=y", "x=x+y");
    test("x*=y", "x=x*y");
    test("x.y+=z", "x.y=x.y+z");
    test("x-=y", "x=x-y");
    test("x|=y", "x=x|y");
    test("x*=y", "x=x*y");
    test("x**=y", "x=x**y");
    test("x.y+=z", "x.y=x.y+z");
  }

  @Test
  public void testFoldAdd1() {
    test("x=false+1", "x=1");
    test("x=true+1", "x=2");
    test("x=1+false", "x=1");
    test("x=1+true", "x=2");
  }

  @Test
  public void testFoldLiteralNames() {
    test("NaN == NaN", "false");
    test("Infinity == Infinity", "true");
    test("Infinity == NaN", "false");
    test("undefined == NaN", "false");
    test("undefined == Infinity", "false");

    test("Infinity >= Infinity", "true");
    test("NaN >= NaN", "false");
  }

  @Test
  public void testFoldLiteralsTypeMismatches() {
    test("true == true", "true");
    test("true == false", "false");
    test("true == null", "false");
    test("false == null", "false");

    // relational operators convert its operands
    test("null <= null", "true"); // 0 = 0
    test("null >= null", "true");
    test("null > null", "false");
    test("null < null", "false");

    test("false >= null", "true"); // 0 = 0
    test("false <= null", "true");
    test("false > null", "false");
    test("false < null", "false");

    test("true >= null", "true"); // 1 > 0
    test("true <= null", "false");
    test("true > null", "true");
    test("true < null", "false");

    test("true >= false", "true"); // 1 > 0
    test("true <= false", "false");
    test("true > false", "true");
    test("true < false", "false");
  }

  @Test
  public void testFoldLeftChildConcat() {
    testSame("x +5 + \"1\"");
    test("x+\"5\" + \"1\"", "x + \"51\"");
    // test("\"a\"+(c+\"b\")", "\"a\"+c+\"b\"");
    test("\"a\"+(\"b\"+c)", "\"ab\"+c");
  }

  @Test
  public void testFoldLeftChildOp() {
    test("x & Infinity & 2", "x & 0");
    testSame("x - Infinity - 2"); // want "x-Infinity"
    testSame("x - 1 + Infinity");
    testSame("x - 2 + 1");
    testSame("x - 2 + 3");
    testSame("1 + x - 2 + 1");
    testSame("1 + x - 2 + 3");
    testSame("1 + x - 2 + 3 - 1");
    testSame("f(x)-0");
    test("x-0-0", "x-0");
    testSame("x+2-2+2");
    testSame("x+2-2+2-2");
    testSame("x-2+2");
    testSame("x-2+2-2");
    testSame("x-2+2-2+2");

    testSame("1+x-0-NaN");
    testSame("1+f(x)-0-NaN");
    testSame("1+x-0+NaN");
    testSame("1+f(x)-0+NaN");

    testSame("1+x+NaN"); // unfoldable
    testSame("x+2-2"); // unfoldable
    testSame("x+2"); // nothing to do
    testSame("x-2"); // nothing to do
  }

  @Test
  public void testFoldSimpleArithmeticOp() {
    testSame("x|NaN");
    testSame("NaN/y");
    testSame("f(x)-0");
    testSame("f(x)|1");
    testSame("1|f(x)");
    testSame("0+a+b");
    testSame("0-a-b");
    testSame("a+b-0");
    testSame("(1+x)|NaN");

    testSame("(1+f(x))|NaN"); // don't fold side-effects
  }

  @Test
  public void testFoldLiteralsAsNumbers() {
    test("x/'12'", "x/12");
    test("x/('12'+'6')", "x/126");
    test("true*x", "1*x");
    test("x/false", "x/0"); // should we add an error check? :)
  }

  @Test
  public void testNotFoldBackToTrueFalse() {
    late = false;
    test("!0", "true");
    test("!1", "false");
    test("!3", "false");

    late = true;
    disableNormalize();
    testSame("!0");
    testSame("!1");
    test("!3", "false");
    testSame("false");
    testSame("true");
  }

  @Test
  public void testFoldBangConstants() {
    test("1 + !0", "2");
    test("1 + !1", "1");
    test("'a ' + !1", "'a false'");
    test("'a ' + !0", "'a true'");
  }

  @Test
  public void testFoldMixed() {
    test("''+[1]", "'1'");
    test("false+[]", "\"false\"");
  }

  @Test
  public void testFoldVoid() {
    testSame("void 0");
    test("void 1", "void 0");
    test("void x", "void 0");
    testSame("void x()");
  }

  @Test
  public void testObjectLiteral() {
    test("(!{})", "false");
    test("(!{a:1})", "false");
    testSame("(!{a:foo()})");
    testSame("(!{'a':foo()})");
  }

  @Test
  public void testArrayLiteral() {
    test("(![])", "false");
    test("(![1])", "false");
    test("(![a])", "false");
    testSame("(![foo()])");
  }

  @Test
  public void testIssue601() {
    testSame("'\\v' == 'v'");
    testSame("'v' == '\\v'");
    testSame("'\\u000B' == '\\v'");
  }

  @Test
  public void testFoldObjectLiteralRef1() {
    // Leave extra side-effects in place
    testSame("var x = ({a:foo(),b:bar()}).a");
    testSame("var x = ({a:1,b:bar()}).a");
    testSame("function f() { return {b:foo(), a:2}.a; }");

    // on the LHS the object act as a temporary leave it in place.
    testSame("({a:x}).a = 1");
    test("({a:x}).a += 1", "({a:x}).a = x + 1");
    testSame("({a:x}).a ++");
    testSame("({a:x}).a --");

    // Getters should not be inlined.
    testSame("({get a() {return this}}).a");
    testSame("({get a() {return this}})?.a");

    // Except, if we can see that the getter function never references 'this'.
    test("({get a() {return 0}}).a", "(function() {return 0})()");
    test("({get a() {return 0}})?.a", "(function() {return 0})()");
    test("({get a() {return 0}})?.a.b", "(function() {return 0})().b");

    // It's okay to inline functions, as long as they're not immediately called.
    // (For tests where they are immediately called, see testFoldObjectLiteral_X)
    test("({a:function(){return this}}).a", "(function(){return this})");
    test("({a:function(){return this}})?.a", "(function(){return this})");

    // It's also okay to inline functions that are immediately called, so long as we know for
    // sure the function doesn't reference 'this'.
    test("({a:function(){return 0}}).a()", "(function(){return 0})()");
    test("({a:function(){return 0}})?.a()", "(function(){return 0})()");
    test("({a:function(){return 0}})?.a().b", "(function(){return 0})().b");

    // Don't inline setters.
    testSame("({set a(b) {return this}}).a");
    testSame("({set a(b) {this._a = b}}).a");

    // Don't inline if there are side-effects.
    testSame("({[foo()]: 1,   a: 0}).a");
    testSame("({['x']: foo(), a: 0}).a");
    testSame("({x: foo(),     a: 0}).a");

    // Leave unknown props alone, the might be on the prototype
    testSame("({}).a");

    // setters by themselves don't provide a definition
    testSame("({}).a");
    testSame("({set a(b) {}}).a");
    // sets don't hide other definitions.
    test("({a:1,set a(b) {}}).a", "1");

    // get is transformed to a call (gets don't have self referential names)
    test("({get a() {}}).a", "(function (){})()");
    // sets don't hide other definitions.
    test("({get a() {},set a(b) {}}).a", "(function (){})()");

    // a function remains a function not a call.
    test("var x = ({a:function(){return 1}}).a", "var x = function(){return 1}");

    test("var x = ({a:1}).a", "var x = 1");
    test("var x = ({a:1, a:2}).a", "var x = 2");
    test("var x = ({a:1, a:foo()}).a", "var x = foo()");
    test("var x = ({a:foo()}).a", "var x = foo()");

    test("function f() { return {a:1, b:2}.a; }", "function f() { return 1; }");

    // GETELEM is handled the same way.
    test("var x = ({'a':1})['a']", "var x = 1");

    // try folding string computed properties
    test("var a = {['a']:x}['a']", "var a = x");
    test("var a = {['a']:x}?.['a']", "var a = x");
    test("var a = {['a']:x}?.['a'].b", "var a = x.b");

    test("var a = { get ['a']() { return 1; }}['a']", "var a = function() { return 1; }();");
    test("var a = {'a': x, ['a']: y}['a']", "var a = y;");
    testSame("var a = {['foo']: x}.a;");
    // Note: it may be useful to fold symbols in the future.
    testSame("var y = Symbol(); var a = {[y]: 3}[y];");

    /*
     * We can fold member functions sometimes.
     *
     * <p>Even though they're different from fn expressions and arrow fns, extracting them only
     * causes programs that would have thrown errors to change behaviour.
     */
    test("var x = {a() { 1; }}.a;", "var x = function() { 1; };");
    // Notice `a` isn't invoked, so beahviour didn't change.
    test("var x = {a() { return this; }}.a;", "var x = function() { return this; };");
    // `super` is invisibly captures the object that declared the method so we can't fold.
    testSame("var x = {a() { return super.a; }}.a;");
    test("var x = {a: 1, a() { 2; }}.a;", "var x = function() { 2; };");
    test("var x = {a() {}, a: 1}.a;", "var x = 1;");
    testSame("var x = {a() {}}.b");
  }

  @Test
  public void testFoldObjectLiteralRef2() {
    late = false;
    test("({a:x}).a += 1", "({a:x}).a = x + 1");
    late = true;
    disableNormalize();
    testSame("({a:x}).a += 1");
  }

  // Regression test for https://github.com/google/closure-compiler/issues/2873
  // It would be incorrect to fold this to "x();" because the 'this' value inside the function
  // will be the global object, instead of the object {a:x} as it should be.
  @Test
  public void testFoldObjectLiteral_methodCall_nonLiteralFn() {
    testSame("({a:x}).a()");
    testSame("({a:x})?.a()");
    testSame("({a:x})?.a()?.b");
  }

  @Test
  public void testFoldObjectLiteral_freeMethodCall() {
    test("({a() { return 1; }}).a()", "(function() { return 1; })()");
    test("({a() { return 1; }})?.a()", "(function() { return 1; })()");

    // grandparent of optional chaining AST continues the chain
    test("({a() { return 1; }})?.a().b", "(function() { return 1; })().b");
    test("({a() { return 1; }})?.a().b.c?.d", "(function() { return 1; })().b.c?.d");
  }

  @Test
  public void testFoldObjectLiteral_freeArrowCall_usingEnclosingThis_late() {
    late = true;
    disableNormalize();
    test("({a: () => this }).a()", "(() => this)()");
    test("({a: () => this })?.a()", "(() => this)()");
  }

  @Test
  public void testFoldObjectLiteral_unfreeMethodCall_dueToThis() {
    testSame("({a() { return this; }}).a()");
    testSame("({a() { return this; }})?.a()");
  }

  @Test
  public void testFoldObjectLiteral_unfreeMethodCall_dueToSuper() {
    testSame("({a() { return super.toString(); }}).a()");
    testSame("({a() { return super.toString(); }})?.a()");
  }

  @Test
  public void testFoldObjectLiteral_paramToInvocation() {
    test("console.log({a: 1}.a)", "console.log(1)");
    test("console.log({a: 1}?.a)", "console.log(1)");
  }

  @Test
  public void testIEString() {
    testSame("!+'\\v1'");
  }

  @Test
  public void testIssue522() {
    testSame("[][1] = 1;");
  }

  @Test
  public void testTypeBasedFoldConstant() {
    enableTypeCheck();
    testSame("function f(/** number */ x) { x + 1 + 1 + x; }");

    testSame("function f(/** boolean */ x) { x + 1 + 1 + x; }");

    testSame("function f(/** null */ x) { var y = true > x; }");

    testSame("function f(/** null */ x) { var y = null > x; }");

    testSame("function f(/** string */ x) { x + 1 + 1 + x; }");

    useTypes = false;
    testSame("function f(/** number */ x) { x + 1 + 1 + x; }");
  }

  @Test
  public void testColorBasedFoldConstant() {
    enableTypeCheck();
    replaceTypesWithColors();
    disableCompareJsDoc();
    testSame("function f(/** number */ x) { x + 1 + 1 + x; }");

    testSame("function f(/** boolean */ x) { x + 1 + 1 + x; }");

    testSame("function f(/** null */ x) { var y = true > x; }");

    testSame("function f(/** null */ x) { var y = null > x; }");

    testSame("function f(/** string */ x) { x + 1 + 1 + x; }");

    useTypes = false;
    testSame("function f(/** number */ x) { x + 1 + 1 + x; }");
  }

  @Test
  public void foldDefineProperties() {
    test("Object.defineProperties({}, {})", "({})");
    test("Object.defineProperties(a, {})", "a");
    testSame("Object.defineProperties(a, {anything:1})");
  }

  @Test
  public void testES6Features() {
    test("var x = {[undefined != true] : 1};", "var x = {[true] : 1};");
    test("let x = false && y;", "let x = false;");
    test("const x = null == undefined", "const x = true");
    test("var [a, , b] = [false+1, true+1, ![]]", "var a; var b; [a, , b] = [1, 2, false]");
    test("var x = () =>  true || x;", "var x = () => { return true; }");
    test(
        "function foo(x = (1 !== void 0), y) {return x+y;}",
        "function foo(x = true, y) {return x+y;}");
    test(
        """
        class Foo {
          constructor() {this.x = null <= null;}
        }
        """,
        """
        class Foo {
          constructor() {this.x = true;}
        }
        """);
    test("function foo() {return `${false && y}`}", "function foo() {return `${false}`}");
  }

  @Test
  public void testES6Features_late() {
    late = true;
    disableNormalize();

    test("var [a, , b] = [false+1, true+1, ![]]", "var [a, , b] = [1, 2, false]");
    test("var x = () =>  true || x;", "var x = () => true;");
  }

  @Test
  public void testClassField() {
    test(
        """
        class Foo {
          x = null <= null;
        }
        """,
        """
        class Foo {
          x = true;
        }
        """);
  }

  private static final ImmutableList<String> LITERAL_OPERANDS =
      ImmutableList.of(
          "null",
          "undefined",
          "void 0",
          "true",
          "false",
          "!0",
          "!1",
          "0",
          "1",
          "''",
          "'123'",
          "'abc'",
          "'def'",
          "NaN",
          "Infinity",
          // TODO(nicksantos): Add more literals
          "-Infinity"
          // "({})",
          // "[]"
          // "[0]",
          // "Object",
          // "(function() {})"
          );

  @Test
  public void testInvertibleOperators() {
    ImmutableMap<String, String> inverses =
        ImmutableMap.<String, String>builder()
            .put("==", "!=")
            .put("===", "!==")
            .put("<=", ">")
            .put("<", ">=")
            .put(">=", "<")
            .put(">", "<=")
            .put("!=", "==")
            .put("!==", "===")
            .buildOrThrow();
    ImmutableSet<String> comparators = ImmutableSet.of("<=", "<", ">=", ">");
    ImmutableSet<String> equalitors = ImmutableSet.of("==", "===");
    ImmutableSet<String> uncomparables = ImmutableSet.of("undefined", "void 0");
    ImmutableList<String> operators = ImmutableList.copyOf(inverses.values());
    for (int iOperandA = 0; iOperandA < LITERAL_OPERANDS.size(); iOperandA++) {
      for (int iOperandB = 0; iOperandB < LITERAL_OPERANDS.size(); iOperandB++) {
        for (int iOp = 0; iOp < operators.size(); iOp++) {
          String a = LITERAL_OPERANDS.get(iOperandA);
          String b = LITERAL_OPERANDS.get(iOperandB);
          String op = operators.get(iOp);
          String inverse = inverses.get(op);

          // Test invertability.
          if (comparators.contains(op)) {
            if (uncomparables.contains(a)
                || uncomparables.contains(b)
                || (a.equals("null") && NodeUtil.getStringNumberValue(b) == null)) {
              assertSameResults(join(a, op, b), "false");
              assertSameResults(join(a, inverse, b), "false");
            }
          } else if (a.equals(b) && equalitors.contains(op)) {
            if (a.equals("NaN") || a.equals("Infinity") || a.equals("-Infinity")) {
              test(join(a, op, b), a.equals("NaN") ? "false" : "true");
            } else {
              assertSameResults(join(a, op, b), "true");
              assertSameResults(join(a, inverse, b), "false");
            }
          } else {
            assertNotSameResults(join(a, op, b), join(a, inverse, b));
          }
        }
      }
    }
  }

  @Test
  public void testCommutativeOperators() {
    late = true;
    disableNormalize();
    ImmutableList<String> operators =
        ImmutableList.of("==", "!=", "===", "!==", "*", "|", "&", "^");
    for (String a : LITERAL_OPERANDS) {
      for (String b : LITERAL_OPERANDS) {
        for (String op : operators) {
          // Test commutativity.
          assertSameResults(join(a, op, b), join(b, op, a));
        }
      }
    }
  }

  @Test
  public void testConvertToNumberNegativeInf() {
    testSame("var x = 3 & (r ? Infinity : -Infinity);");
  }

  @Test
  public void testAlgebraicIdentities() {
    enableTypeCheck();
    replaceTypesWithColors();
    disableCompareJsDoc();

    foldNumericTypes("x+0", "x");
    foldNumericTypes("0+x", "x");
    foldNumericTypes("x+0+0+x+x+0", "x+x+x");

    foldNumericTypes("x-0", "x");
    foldNumericTypes("x-0-0-0", "x");
    // 'x-0' is numeric even if x isn't
    test("var x='a'; x-0-0", "var x='a';x-0");
    foldNumericTypes("0-x", "-x");
    test("for (var i = 0; i < 5; i++) var x = 0 + i * 1", "var i = 0; for(; i < 5; i++) var x=i");

    foldNumericTypes("x*1", "x");
    foldNumericTypes("1*x", "x");
    // can't optimize these without a non-NaN prover
    testSame("x*0");
    testSame("0*x");
    testSame("0/x");

    foldNumericTypes("x/1", "x");
  }

  @Test
  public void testBigIntAlgebraicIdentities() {
    enableTypeCheck();
    replaceTypesWithColors();
    disableCompareJsDoc();

    foldBigIntTypes("x+0n", "x");
    foldBigIntTypes("0n+x", "x");
    foldBigIntTypes("x+0n+0n+x+x+0n", "x+x+x");

    foldBigIntTypes("x-0n", "x");
    foldBigIntTypes("0n-x", "-x");
    foldBigIntTypes("x-0n-0n-0n", "x");

    foldBigIntTypes("x*1n", "x");
    foldBigIntTypes("1n*x", "x");
    foldBigIntTypes("x*1n*1n*x*x*1n", "x*x*x");

    foldBigIntTypes("x/1n", "x");
    foldBigIntTypes("x/0n", "x/0n");

    test(
        "for (var i = 0n; i < 5n; i++) var x = 0n + i * 1n",
        "var i = 0n; for(; i < 5n; i++) var x=i");
    test(
        "for (var i = 0n; i % 2n === 0n; i++) var x = i % 2n",
        "var i = 0n; for (; i % 2n === 0n; i++) var x = i % 2n");

    test("(doSomething(),0n)*1n", "(doSomething(),0n)");
    test("1n*(doSomething(),0n)", "(doSomething(),0n)");
    ignoreWarnings(DiagnosticGroups.CHECK_TYPES);
    testSame("(0n,doSomething())*1n");
    testSame("1n*(0n,doSomething())");
  }

  @Test
  public void testAssociativeFoldConstantsWithVariables() {
    // MUL and ADD should not fold
    testSame("alert(x * 12 * 20);");
    testSame("alert(12 * x * 20);");
    testSame("alert(x + 12 + 20);");
    testSame("alert(12 + x +  20);");

    test("alert(x & 12 & 20);", "alert(x & 4);");
    test("alert(12 & x & 20);", "alert(x & 4);");
  }

  private void foldBigIntTypes(String js, String expected) {
    test(
        "function f(/** @type {bigint} */ x) { " + js + " }",
        "function f(/** @type {bigint} */ x) { " + expected + " }");
  }

  private void foldNumericTypes(String js, String expected) {
    test(
        "function f(/** @type {number} */ x) { " + js + " }",
        "function f(/** @type {number} */ x) { " + expected + " }");
  }

  private void foldStringTypes(String js, String expected) {
    test(
        "function f(/** @type {string} */ x) { " + js + " }",
        "function f(/** @type {string} */ x) { " + expected + " }");
  }

  private static String join(String operandA, String op, String operandB) {
    return operandA + " " + op + " " + operandB;
  }

  private void assertSameResults(String exprA, String exprB) {
    assertWithMessage("Expressions did not fold the same\nexprA: %s\nexprB: %s", exprA, exprB)
        .that(process(exprB))
        .isEqualTo(process(exprA));
  }

  private void assertNotSameResults(String exprA, String exprB) {
    assertWithMessage("Expressions folded the same\nexprA: %s\nexprB: %s", exprA, exprB)
        .that(process(exprA).equals(process(exprB)))
        .isFalse();
  }

  private String process(String js) {
    return printHelper(js, true);
  }

  private String printHelper(String js, boolean runProcessor) {
    Compiler compiler = createCompiler();
    CompilerOptions options = getOptions();
    compiler.init(
        ImmutableList.<SourceFile>of(),
        ImmutableList.of(SourceFile.fromCode("testcode", js)),
        options);
    Node root = compiler.parseInputs();
    compiler.setAccessorSummary(AccessorSummary.create(ImmutableMap.of()));
    assertWithMessage(
            "Unexpected parse error(s): %s\nEXPR: %s",
            Joiner.on("\n").join(compiler.getErrors()), js)
        .that(root)
        .isNotNull();
    Node externsRoot = root.getFirstChild();
    Node mainRoot = externsRoot.getNext();
    if (runProcessor) {
      getProcessor(compiler).process(externsRoot, mainRoot);
    }
    return compiler.toSource(mainRoot);
  }
}
