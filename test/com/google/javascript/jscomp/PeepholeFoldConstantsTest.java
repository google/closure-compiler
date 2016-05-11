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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.Node;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tests for {@link PeepholeFoldConstants} in isolation. Tests for
 * the interaction of multiple peephole passes are in
 * {@link PeepholeIntegrationTest}.
 */

public final class PeepholeFoldConstantsTest extends CompilerTestCase {

  private boolean late;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    late = false;
  }

  @Override
  public CompilerPass getProcessor(final Compiler compiler) {
    CompilerPass peepholePass = new PeepholeOptimizationsPass(compiler,
          new PeepholeFoldConstants(late, compiler.getOptions().useTypesForOptimization));
    return peepholePass;
  }

  @Override
  protected CompilerOptions getOptions(CompilerOptions options) {
    options.useTypesForOptimization = true;
    return super.getOptions(options);
  }

  @Override
  protected int getNumRepetitions() {
    // Reduce this to 1 if we get better expression evaluators.
    return 2;
  }

  private void foldSame(String js) {
    testSame(js);
  }

  private void fold(String js, String expected) {
    test(js, expected);
  }

  private void fold(String js, String expected, DiagnosticType warning) {
    test(js, expected, null, warning);
  }

  public void testUndefinedComparison1() {
    fold("undefined == undefined", "true");
    fold("undefined == null", "true");
    fold("undefined == void 0", "true");

    fold("undefined == 0", "false");
    fold("undefined == 1", "false");
    fold("undefined == 'hi'", "false");
    fold("undefined == true", "false");
    fold("undefined == false", "false");

    fold("undefined === undefined", "true");
    fold("undefined === null", "false");
    fold("undefined === void 0", "true");

    foldSame("undefined == this");
    foldSame("undefined == x");

    fold("undefined != undefined", "false");
    fold("undefined != null", "false");
    fold("undefined != void 0", "false");

    fold("undefined != 0", "true");
    fold("undefined != 1", "true");
    fold("undefined != 'hi'", "true");
    fold("undefined != true", "true");
    fold("undefined != false", "true");

    fold("undefined !== undefined", "false");
    fold("undefined !== void 0", "false");
    fold("undefined !== null", "true");

    foldSame("undefined != this");
    foldSame("undefined != x");

    fold("undefined < undefined", "false");
    fold("undefined > undefined", "false");
    fold("undefined >= undefined", "false");
    fold("undefined <= undefined", "false");

    fold("0 < undefined", "false");
    fold("true > undefined", "false");
    fold("'hi' >= undefined", "false");
    fold("null <= undefined", "false");

    fold("undefined < 0", "false");
    fold("undefined > true", "false");
    fold("undefined >= 'hi'", "false");
    fold("undefined <= null", "false");

    fold("null == undefined", "true");
    fold("0 == undefined", "false");
    fold("1 == undefined", "false");
    fold("'hi' == undefined", "false");
    fold("true == undefined", "false");
    fold("false == undefined", "false");
    fold("null === undefined", "false");
    fold("void 0 === undefined", "true");

    fold("undefined == NaN", "false");
    fold("NaN == undefined", "false");
    fold("undefined == Infinity", "false");
    fold("Infinity == undefined", "false");
    fold("undefined == -Infinity", "false");
    fold("-Infinity == undefined", "false");
    fold("({}) == undefined", "false");
    fold("undefined == ({})", "false");
    fold("([]) == undefined", "false");
    fold("undefined == ([])", "false");
    fold("(/a/g) == undefined", "false");
    fold("undefined == (/a/g)", "false");
    fold("(function(){}) == undefined", "false");
    fold("undefined == (function(){})", "false");

    fold("undefined != NaN", "true");
    fold("NaN != undefined", "true");
    fold("undefined != Infinity", "true");
    fold("Infinity != undefined", "true");
    fold("undefined != -Infinity", "true");
    fold("-Infinity != undefined", "true");
    fold("({}) != undefined", "true");
    fold("undefined != ({})", "true");
    fold("([]) != undefined", "true");
    fold("undefined != ([])", "true");
    fold("(/a/g) != undefined", "true");
    fold("undefined != (/a/g)", "true");
    fold("(function(){}) != undefined", "true");
    fold("undefined != (function(){})", "true");

    foldSame("this == undefined");
    foldSame("x == undefined");
  }

  public void testUndefinedComparison2() {
    fold("\"123\" !== void 0", "true");
    fold("\"123\" === void 0", "false");

    fold("void 0 !== \"123\"", "true");
    fold("void 0 === \"123\"", "false");
  }

  public void testUndefinedComparison3() {
    fold("\"123\" !== undefined", "true");
    fold("\"123\" === undefined", "false");

    fold("undefined !== \"123\"", "true");
    fold("undefined === \"123\"", "false");
  }

  public void testUndefinedComparison4() {
    fold("1 !== void 0", "true");
    fold("1 === void 0", "false");

    fold("null !== void 0", "true");
    fold("null === void 0", "false");

    fold("undefined !== void 0", "false");
    fold("undefined === void 0", "true");
  }

  public void testNullComparison1() {
    fold("null == undefined", "true");
    fold("null == null", "true");
    fold("null == void 0", "true");

    fold("null == 0", "false");
    fold("null == 1", "false");
    fold("null == 'hi'", "false");
    fold("null == true", "false");
    fold("null == false", "false");

    fold("null === undefined", "false");
    fold("null === null", "true");
    fold("null === void 0", "false");
    foldSame("null === x");

    foldSame("null == this");
    foldSame("null == x");

    fold("null != undefined", "false");
    fold("null != null", "false");
    fold("null != void 0", "false");

    fold("null != 0", "true");
    fold("null != 1", "true");
    fold("null != 'hi'", "true");
    fold("null != true", "true");
    fold("null != false", "true");

    fold("null !== undefined", "true");
    fold("null !== void 0", "true");
    fold("null !== null", "false");

    foldSame("null != this");
    foldSame("null != x");

    fold("null < null", "false");
    fold("null > null", "false");
    fold("null >= null", "true");
    fold("null <= null", "true");

    fold("0 < null", "false");
    fold("0 > null", "false");
    fold("0 >= null", "true");
    fold("true > null", "true");
    fold("'hi' < null", "false");
    fold("'hi' >= null", "false");
    fold("null <= null", "true");

    fold("null < 0", "false");
    fold("null > true", "false");
    fold("null < 'hi'", "false");
    fold("null >= 'hi'", "false");
    fold("null <= null", "true");

    fold("null == null", "true");
    fold("0 == null", "false");
    fold("1 == null", "false");
    fold("'hi' == null", "false");
    fold("true == null", "false");
    fold("false == null", "false");
    fold("null === null", "true");
    fold("void 0 === null", "false");

    fold("null == NaN", "false");
    fold("NaN == null", "false");
    fold("null == Infinity", "false");
    fold("Infinity == null", "false");
    fold("null == -Infinity", "false");
    fold("-Infinity == null", "false");
    fold("({}) == null", "false");
    fold("null == ({})", "false");
    fold("([]) == null", "false");
    fold("null == ([])", "false");
    fold("(/a/g) == null", "false");
    fold("null == (/a/g)", "false");
    fold("(function(){}) == null", "false");
    fold("null == (function(){})", "false");

    fold("null != NaN", "true");
    fold("NaN != null", "true");
    fold("null != Infinity", "true");
    fold("Infinity != null", "true");
    fold("null != -Infinity", "true");
    fold("-Infinity != null", "true");
    fold("({}) != null", "true");
    fold("null != ({})", "true");
    fold("([]) != null", "true");
    fold("null != ([])", "true");
    fold("(/a/g) != null", "true");
    fold("null != (/a/g)", "true");
    fold("(function(){}) != null", "true");
    fold("null != (function(){})", "true");

    foldSame("({a:f()}) == null");
    foldSame("null == ({a:f()})");
    foldSame("([f()]) == null");
    foldSame("null == ([f()])");

    foldSame("this == null");
    foldSame("x == null");
  }

  public void testBooleanBooleanComparison() {
    foldSame("!x == !y");
    foldSame("!x < !y");
    foldSame("!x !== !y");

    foldSame("!x == !x"); // foldable
    foldSame("!x < !x"); // foldable
    foldSame("!x !== !x"); // foldable
  }

  public void testBooleanNumberComparison() {
    foldSame("!x == +y");
    foldSame("!x <= +y");
    fold("!x !== +y", "true");
  }

  public void testNumberBooleanComparison() {
    foldSame("+x == !y");
    foldSame("+x <= !y");
    fold("+x === !y", "false");
  }

  public void testBooleanStringComparison() {
    foldSame("!x == '' + y");
    foldSame("!x <= '' + y");
    fold("!x !== '' + y", "true");
  }

  public void testStringBooleanComparison() {
    foldSame("'' + x == !y");
    foldSame("'' + x <= !y");
    fold("'' + x === !y", "false");
  }

  public void testNumberNumberComparison() {
    fold("1 > 1", "false");
    fold("2 == 3", "false");
    fold("3.6 === 3.6", "true");
    foldSame("+x > +y");
    foldSame("+x == +y");
    foldSame("+x === +y");
    foldSame("+x == +x");
    foldSame("+x === +x");

    foldSame("+x > +x"); // foldable
  }

  public void testStringStringComparison() {
    fold("'a' < 'b'", "true");
    fold("'a' <= 'b'", "true");
    fold("'a' > 'b'", "false");
    fold("'a' >= 'b'", "false");
    fold("+'a' < +'b'", "false");
    foldSame("typeof a < 'a'");
    foldSame("'a' >= typeof a");
    fold("typeof a < typeof a", "false");
    fold("typeof a >= typeof a", "true");
    fold("typeof 3 > typeof 4", "false");
    fold("typeof function() {} < typeof function() {}", "false");
    fold("'a' == 'a'", "true");
    fold("'b' != 'a'", "true");
    foldSame("'undefined' == typeof a");
    foldSame("typeof a != 'number'");
    foldSame("'undefined' == typeof a");
    foldSame("'undefined' == typeof a");
    fold("typeof a == typeof a", "true");
    fold("'a' === 'a'", "true");
    fold("'b' !== 'a'", "true");
    fold("typeof a === typeof a", "true");
    fold("typeof a !== typeof a", "false");
    foldSame("'' + x <= '' + y");
    foldSame("'' + x != '' + y");
    foldSame("'' + x === '' + y");

    foldSame("'' + x <= '' + x"); // potentially foldable
    foldSame("'' + x != '' + x"); // potentially foldable
    foldSame("'' + x === '' + x"); // potentially foldable
  }

  public void testNumberStringComparison() {
    fold("1 < '2'", "true");
    fold("2 > '1'", "true");
    fold("123 > '34'", "true");
    fold("NaN >= 'NaN'", "false");
    fold("1 == '2'", "false");
    fold("1 != '1'", "false");
    fold("NaN == 'NaN'", "false");
    fold("1 === '1'", "false");
    fold("1 !== '1'", "true");
    foldSame("+x > '' + y");
    foldSame("+x == '' + y");
    fold("+x !== '' + y", "true");
  }

  public void testStringNumberComparison() {
    fold("'1' < 2", "true");
    fold("'2' > 1", "true");
    fold("'123' > 34", "true");
    fold("'NaN' < NaN", "false");
    fold("'1' == 2", "false");
    fold("'1' != 1", "false");
    fold("'NaN' == NaN", "false");
    fold("'1' === 1", "false");
    fold("'1' !== 1", "true");
    foldSame("'' + x < +y");
    foldSame("'' + x == +y");
    fold("'' + x === +y", "false");
  }

  public void testNaNComparison() {
    fold("NaN < NaN", "false");
    fold("NaN >= NaN", "false");
    fold("NaN == NaN", "false");
    fold("NaN === NaN", "false");

    fold("NaN < null", "false");
    fold("null >= NaN", "false");
    fold("NaN == null", "false");
    fold("null != NaN", "true");
    fold("null === NaN", "false");

    fold("NaN < undefined", "false");
    fold("undefined >= NaN", "false");
    fold("NaN == undefined", "false");
    fold("undefined != NaN", "true");
    fold("undefined === NaN", "false");

    foldSame("NaN < x");
    foldSame("x >= NaN");
    foldSame("NaN == x");
    foldSame("x != NaN");
    fold("NaN === x", "false");
    fold("x !== NaN", "true");
    foldSame("NaN == foo()");
  }

  public void testObjectComparison1() {
    fold("!new Date()", "false");
    fold("!!new Date()", "true");

    fold("new Date() == null", "false");
    fold("new Date() == undefined", "false");
    fold("new Date() != null", "true");
    fold("new Date() != undefined", "true");
    fold("null == new Date()", "false");
    fold("undefined == new Date()", "false");
    fold("null != new Date()", "true");
    fold("undefined != new Date()", "true");
  }

  public void testUnaryOps() {
    // These cases are handled by PeepholeRemoveDeadCode.
    foldSame("!foo()");
    foldSame("~foo()");
    foldSame("-foo()");

    // These cases are handled here.
    fold("a=!true", "a=false");
    fold("a=!10", "a=false");
    fold("a=!false", "a=true");
    fold("a=!foo()", "a=!foo()");
    fold("a=-0", "a=-0.0");
    fold("a=-(0)", "a=-0.0");
    fold("a=-Infinity", "a=-Infinity");
    fold("a=-NaN", "a=NaN");
    fold("a=-foo()", "a=-foo()");
    fold("a=~~0", "a=0");
    fold("a=~~10", "a=10");
    fold("a=~-7", "a=6");

    fold("a=+true", "a=1");
    fold("a=+10", "a=10");
    fold("a=+false", "a=0");
    foldSame("a=+foo()");
    foldSame("a=+f");
    fold("a=+(f?true:false)", "a=+(f?1:0)"); // TODO(johnlenz): foldable
    fold("a=+0", "a=0");
    fold("a=+Infinity", "a=Infinity");
    fold("a=+NaN", "a=NaN");
    fold("a=+-7", "a=-7");
    fold("a=+.5", "a=.5");

    fold("a=~0x100000000", "a=~0x100000000",
         PeepholeFoldConstants.BITWISE_OPERAND_OUT_OF_RANGE);
    fold("a=~-0x100000000", "a=~-0x100000000",
         PeepholeFoldConstants.BITWISE_OPERAND_OUT_OF_RANGE);
    testSame("a=~.5", PeepholeFoldConstants.FRACTIONAL_BITWISE_OPERAND);
  }

  public void testUnaryOpsStringCompare() {
    fold("a = -1", "a = -1");
    fold("a = ~0", "a = -1");
    fold("a = ~1", "a = -2");
    fold("a = ~101", "a = -102");
  }

  public void testFoldLogicalOp() {
    fold("x = true && x", "x = x");
    fold("x = [foo()] && x", "x = ([foo()],x)");

    fold("x = false && x", "x = false");
    fold("x = true || x", "x = true");
    fold("x = false || x", "x = x");
    fold("x = 0 && x", "x = 0");
    fold("x = 3 || x", "x = 3");
    fold("x = false || 0", "x = 0");

    // unfoldable, because the right-side may be the result
    fold("a = x && true", "a=x&&true");
    fold("a = x && false", "a=x&&false");
    fold("a = x || 3", "a=x||3");
    fold("a = x || false", "a=x||false");
    fold("a = b ? c : x || false", "a=b?c:x||false");
    fold("a = b ? x || false : c", "a=b?x||false:c");
    fold("a = b ? c : x && true", "a=b?c:x&&true");
    fold("a = b ? x && true : c", "a=b?x&&true:c");

    // folded, but not here.
    foldSame("a = x || false ? b : c");
    foldSame("a = x && true ? b : c");

    fold("x = foo() || true || bar()", "x = foo()||true");
    fold("x = foo() || true && bar()", "x = foo()||bar()");
    fold("x = foo() || false && bar()", "x = foo()||false");
    fold("x = foo() && false && bar()", "x = foo()&&false");
    fold("x = foo() && false || bar()", "x = (foo()&&false,bar())");
    // TODO(tbreisacher): Fix and re-enable.
    //fold("x = foo() || false || bar()", "x = foo()||bar()");
    //fold("x = foo() && true && bar()", "x = foo()&&bar()");

    fold("1 && b()", "b()");
    fold("a() && (1 && b())", "a() && b()");
    // TODO(johnlenz): Consider folding the following to:
    //   "(a(),1) && b();
    fold("(a() && 1) && b()", "(a() && 1) && b()");

    // Really not foldable, because it would change the type of the
    // expression if foo() returns something equivalent, but not
    // identical, to true. Cf. FoldConstants.tryFoldAndOr().
    foldSame("x = foo() && true || bar()");
    foldSame("foo() && true || bar()");
  }

  public void testFoldBitwiseOp() {
    fold("x = 1 & 1", "x = 1");
    fold("x = 1 & 2", "x = 0");
    fold("x = 3 & 1", "x = 1");
    fold("x = 3 & 3", "x = 3");

    fold("x = 1 | 1", "x = 1");
    fold("x = 1 | 2", "x = 3");
    fold("x = 3 | 1", "x = 3");
    fold("x = 3 | 3", "x = 3");

    fold("x = 1 ^ 1", "x = 0");
    fold("x = 1 ^ 2", "x = 3");
    fold("x = 3 ^ 1", "x = 2");
    fold("x = 3 ^ 3", "x = 0");

    fold("x = -1 & 0", "x = 0");
    fold("x = 0 & -1", "x = 0");
    fold("x = 1 & 4", "x = 0");
    fold("x = 2 & 3", "x = 2");

    // make sure we fold only when we are supposed to -- not when doing so would
    // lose information or when it is performed on nonsensical arguments.
    fold("x = 1 & 1.1", "x = 1");
    fold("x = 1.1 & 1", "x = 1");
    fold("x = 1 & 3000000000", "x = 0");
    fold("x = 3000000000 & 1", "x = 0");

    // Try some cases with | as well
    fold("x = 1 | 4", "x = 5");
    fold("x = 1 | 3", "x = 3");
    fold("x = 1 | 1.1", "x = 1");
    foldSame("x = 1 | 3E9");
    fold("x = 1 | 3000000001", "x = -1294967295");
    fold("x = 4294967295 | 0", "x = -1");
  }

  public void testFoldBitwiseOp2() {
    fold("x = y & 1 & 1", "x = y & 1");
    fold("x = y & 1 & 2", "x = y & 0");
    fold("x = y & 3 & 1", "x = y & 1");
    fold("x = 3 & y & 1", "x = y & 1");
    fold("x = y & 3 & 3", "x = y & 3");
    fold("x = 3 & y & 3", "x = y & 3");

    fold("x = y | 1 | 1", "x = y | 1");
    fold("x = y | 1 | 2", "x = y | 3");
    fold("x = y | 3 | 1", "x = y | 3");
    fold("x = 3 | y | 1", "x = y | 3");
    fold("x = y | 3 | 3", "x = y | 3");
    fold("x = 3 | y | 3", "x = y | 3");

    fold("x = y ^ 1 ^ 1", "x = y ^ 0");
    fold("x = y ^ 1 ^ 2", "x = y ^ 3");
    fold("x = y ^ 3 ^ 1", "x = y ^ 2");
    fold("x = 3 ^ y ^ 1", "x = y ^ 2");
    fold("x = y ^ 3 ^ 3", "x = y ^ 0");
    fold("x = 3 ^ y ^ 3", "x = y ^ 0");

    fold("x = Infinity | NaN", "x=0");
    fold("x = 12 | NaN", "x=12");
  }

  public void testFoldingMixTypesLate() {
    late = true;
    fold("x = x + '2'", "x+='2'");
    fold("x = +x + +'2'", "x = +x + 2");
    fold("x = x - '2'", "x-=2");
    fold("x = x ^ '2'", "x^=2");
    fold("x = '2' ^ x", "x^=2");
    fold("x = '2' & x", "x&=2");
    fold("x = '2' | x", "x|=2");

    fold("x = '2' | y", "x=2|y");
    fold("x = y | '2'", "x=y|2");
    fold("x = y | (a && '2')", "x=y|(a&&2)");
    fold("x = y | (a,'2')", "x=y|(a,2)");
    fold("x = y | (a?'1':'2')", "x=y|(a?1:2)");
    fold("x = y | ('x'?'1':'2')", "x=y|('x'?1:2)");
  }

  public void testFoldingMixTypesEarly() {
    late = false;
    foldSame("x = x + '2'");
    fold("x = +x + +'2'", "x = +x + 2");
    fold("x = x - '2'", "x = x - 2");
    fold("x = x ^ '2'", "x = x ^ 2");
    fold("x = '2' ^ x", "x = 2 ^ x");
    fold("x = '2' & x", "x = 2 & x");
    fold("x = '2' | x", "x = 2 | x");

    fold("x = '2' | y", "x=2|y");
    fold("x = y | '2'", "x=y|2");
    fold("x = y | (a && '2')", "x=y|(a&&2)");
    fold("x = y | (a,'2')", "x=y|(a,2)");
    fold("x = y | (a?'1':'2')", "x=y|(a?1:2)");
    fold("x = y | ('x'?'1':'2')", "x=y|('x'?1:2)");
  }

  public void testFoldingAdd1() {
    fold("x = null + true", "x=1");
    foldSame("x = a + true");
    fold("x = '' + {}", "x = '[object Object]'");
    fold("x = [] + {}", "x = '[object Object]'");
    fold("x = {} + []", "x = '[object Object]'");
    fold("x = {} + ''", "x = '[object Object]'");
  }

  public void testFoldingAdd2() {
    fold("x = false + []", "x='false'");
    fold("x = [] + true",  "x='true'");
    fold("NaN + []", "'NaN'");
  }

  public void testFoldBitwiseOpStringCompare() {
    fold("x = -1 | 0", "x = -1");
  }

  public void testFoldBitShifts() {
    fold("x = 1 << 0", "x = 1");
    fold("x = -1 << 0", "x = -1");
    fold("x = 1 << 1", "x = 2");
    fold("x = 3 << 1", "x = 6");
    fold("x = 1 << 8", "x = 256");

    fold("x = 1 >> 0", "x = 1");
    fold("x = -1 >> 0", "x = -1");
    fold("x = 1 >> 1", "x = 0");
    fold("x = 2 >> 1", "x = 1");
    fold("x = 5 >> 1", "x = 2");
    fold("x = 127 >> 3", "x = 15");
    fold("x = 3 >> 1", "x = 1");
    fold("x = 3 >> 2", "x = 0");
    fold("x = 10 >> 1", "x = 5");
    fold("x = 10 >> 2", "x = 2");
    fold("x = 10 >> 5", "x = 0");

    fold("x = 10 >>> 1", "x = 5");
    fold("x = 10 >>> 2", "x = 2");
    fold("x = 10 >>> 5", "x = 0");
    fold("x = -1 >>> 1", "x = 2147483647"); // 0x7fffffff
    fold("x = -1 >>> 0", "x = 4294967295"); // 0xffffffff
    fold("x = -2 >>> 0", "x = 4294967294"); // 0xfffffffe
    fold("x = 0x90000000 >>> 28", "x = 9");

    testSame("3000000000 << 1",
         PeepholeFoldConstants.BITWISE_OPERAND_OUT_OF_RANGE);
    testSame("1 << 32",
        PeepholeFoldConstants.SHIFT_AMOUNT_OUT_OF_BOUNDS);
    testSame("1 << -1",
        PeepholeFoldConstants.SHIFT_AMOUNT_OUT_OF_BOUNDS);
    testSame("3000000000 >> 1",
        PeepholeFoldConstants.BITWISE_OPERAND_OUT_OF_RANGE);
    testSame("0x90000000 >> 28",
        PeepholeFoldConstants.BITWISE_OPERAND_OUT_OF_RANGE);
    testSame("1 >> 32",
        PeepholeFoldConstants.SHIFT_AMOUNT_OUT_OF_BOUNDS);
    testSame("1.5 << 0",
        PeepholeFoldConstants.FRACTIONAL_BITWISE_OPERAND);
    testSame("1 << .5",
        PeepholeFoldConstants.FRACTIONAL_BITWISE_OPERAND);
    testSame("1.5 >>> 0",
        PeepholeFoldConstants.FRACTIONAL_BITWISE_OPERAND);
    testSame("1 >>> .5",
        PeepholeFoldConstants.FRACTIONAL_BITWISE_OPERAND);
    testSame("1.5 >> 0",
        PeepholeFoldConstants.FRACTIONAL_BITWISE_OPERAND);
    testSame("1 >> .5",
        PeepholeFoldConstants.FRACTIONAL_BITWISE_OPERAND);
  }

  public void testFoldBitShiftsStringCompare() {
    fold("x = -1 << 1", "x = -2");
    fold("x = -1 << 8", "x = -256");
    fold("x = -1 >> 1", "x = -1");
    fold("x = -2 >> 1", "x = -1");
    fold("x = -1 >> 0", "x = -1");
  }

  public void testStringAdd() {
    fold("x = 'a' + \"bc\"", "x = \"abc\"");
    fold("x = 'a' + 5", "x = \"a5\"");
    fold("x = 5 + 'a'", "x = \"5a\"");
    fold("x = 'a' + ''", "x = \"a\"");
    fold("x = \"a\" + foo()", "x = \"a\"+foo()");
    fold("x = foo() + 'a' + 'b'", "x = foo()+\"ab\"");
    fold("x = (foo() + 'a') + 'b'", "x = foo()+\"ab\"");  // believe it!
    fold("x = foo() + 'a' + 'b' + 'cd' + bar()", "x = foo()+\"abcd\"+bar()");
    fold("x = foo() + 2 + 'b'", "x = foo()+2+\"b\"");  // don't fold!
    fold("x = foo() + 'a' + 2", "x = foo()+\"a2\"");
    fold("x = '' + null", "x = \"null\"");
    fold("x = true + '' + false", "x = \"truefalse\"");
    fold("x = '' + []", "x = ''");      // cannot fold (but nice if we can)
  }

  public void testIssue821() {
    foldSame("var a =(Math.random()>0.5? '1' : 2 ) + 3 + 4;");
    foldSame("var a = ((Math.random() ? 0 : 1) ||" +
             "(Math.random()>0.5? '1' : 2 )) + 3 + 4;");
  }

  public void testFoldConstructor() {
    fold("x = this[new String('a')]", "x = this['a']");
    fold("x = ob[new String(12)]", "x = ob['12']");
    fold("x = ob[new String(false)]", "x = ob['false']");
    fold("x = ob[new String(null)]", "x = ob['null']");
    fold("x = 'a' + new String('b')", "x = 'ab'");
    fold("x = 'a' + new String(23)", "x = 'a23'");
    fold("x = 2 + new String(1)", "x = '21'");
    foldSame("x = ob[new String(a)]");
    foldSame("x = new String('a')");
    foldSame("x = (new String('a'))[3]");
  }

  public void testFoldArithmetic() {
    fold("x = 10 + 20", "x = 30");
    fold("x = 2 / 4", "x = 0.5");
    fold("x = 2.25 * 3", "x = 6.75");
    fold("z = x * y", "z = x * y");
    fold("x = y * 5", "x = y * 5");
    fold("x = 1 / 0", "x = 1 / 0");
    fold("x = 3 % 2", "x = 1");
    fold("x = 3 % -2", "x = 1");
    fold("x = -1 % 3", "x = -1");
    fold("x = 1 % 0", "x = 1 % 0");
  }

  public void testFoldArithmetic2() {
    foldSame("x = y + 10 + 20");
    foldSame("x = y / 2 / 4");
    fold("x = y * 2.25 * 3", "x = y * 6.75");
    fold("z = x * y", "z = x * y");
    fold("x = y * 5", "x = y * 5");
    fold("x = y + (z * 24 * 60 * 60 * 1000)", "x = y + z * 864E5");
  }

  public void testFoldArithmetic3() {
    fold("x = null * undefined", "x = NaN");
    fold("x = null * 1", "x = 0");
    fold("x = (null - 1) * 2", "x = -2");
    fold("x = (null + 1) * 2", "x = 2");
  }

  public void testFoldArithmeticInfinity() {
    fold("x=-Infinity-2", "x=-Infinity");
    fold("x=Infinity-2", "x=Infinity");
    fold("x=Infinity*5", "x=Infinity");
  }

  public void testFoldArithmeticStringComp() {
    fold("x = 10 - 20", "x = -10");
  }

  public void testFoldComparison() {
    fold("x = 0 == 0", "x = true");
    fold("x = 1 == 2", "x = false");
    fold("x = 'abc' == 'def'", "x = false");
    fold("x = 'abc' == 'abc'", "x = true");
    fold("x = \"\" == ''", "x = true");
    fold("x = foo() == bar()", "x = foo()==bar()");

    fold("x = 1 != 0", "x = true");
    fold("x = 'abc' != 'def'", "x = true");
    fold("x = 'a' != 'a'", "x = false");

    fold("x = 1 < 20", "x = true");
    fold("x = 3 < 3", "x = false");
    fold("x = 10 > 1.0", "x = true");
    fold("x = 10 > 10.25", "x = false");
    fold("x = y == y", "x = y==y"); // Maybe foldable given type information
    fold("x = y < y", "x = false");
    fold("x = y > y", "x = false");
    fold("x = 1 <= 1", "x = true");
    fold("x = 1 <= 0", "x = false");
    fold("x = 0 >= 0", "x = true");
    fold("x = -1 >= 9", "x = false");

    fold("x = true == true", "x = true");
    fold("x = false == false", "x = true");
    fold("x = false == null", "x = false");
    fold("x = false == true", "x = false");
    fold("x = true == null", "x = false");

    fold("0 == 0", "true");
    fold("1 == 2", "false");
    fold("'abc' == 'def'", "false");
    fold("'abc' == 'abc'", "true");
    fold("\"\" == ''", "true");
    foldSame("foo() == bar()");

    fold("1 != 0", "true");
    fold("'abc' != 'def'", "true");
    fold("'a' != 'a'", "false");

    fold("1 < 20", "true");
    fold("3 < 3", "false");
    fold("10 > 1.0", "true");
    fold("10 > 10.25", "false");
    foldSame("x == x");
    fold("x < x", "false");
    fold("x > x", "false");
    fold("1 <= 1", "true");
    fold("1 <= 0", "false");
    fold("0 >= 0", "true");
    fold("-1 >= 9", "false");

    fold("true == true", "true");
    fold("false == null", "false");
    fold("false == true", "false");
    fold("true == null", "false");
  }

  // ===, !== comparison tests
  public void testFoldComparison2() {
    fold("x = 0 === 0", "x = true");
    fold("x = 1 === 2", "x = false");
    fold("x = 'abc' === 'def'", "x = false");
    fold("x = 'abc' === 'abc'", "x = true");
    fold("x = \"\" === ''", "x = true");
    fold("x = foo() === bar()", "x = foo()===bar()");

    fold("x = 1 !== 0", "x = true");
    fold("x = 'abc' !== 'def'", "x = true");
    fold("x = 'a' !== 'a'", "x = false");

    fold("x = y === y", "x = y===y");

    fold("x = true === true", "x = true");
    fold("x = false === false", "x = true");
    fold("x = false === null", "x = false");
    fold("x = false === true", "x = false");
    fold("x = true === null", "x = false");

    fold("0 === 0", "true");
    fold("1 === 2", "false");
    fold("'abc' === 'def'", "false");
    fold("'abc' === 'abc'", "true");
    fold("\"\" === ''", "true");
    foldSame("foo() === bar()");

    fold("1 === '1'", "false");
    fold("1 === true", "false");
    fold("1 !== '1'", "true");
    fold("1 !== true", "true");

    fold("1 !== 0", "true");
    fold("'abc' !== 'def'", "true");
    fold("'a' !== 'a'", "false");

    foldSame("x === x");

    fold("true === true", "true");
    fold("false === null", "false");
    fold("false === true", "false");
    fold("true === null", "false");
  }

  public void testFoldComparison3() {
    fold("x = !1 == !0", "x = false");

    fold("x = !0 == !0", "x = true");
    fold("x = !1 == !1", "x = true");
    fold("x = !1 == null", "x = false");
    fold("x = !1 == !0", "x = false");
    fold("x = !0 == null", "x = false");

    fold("!0 == !0", "true");
    fold("!1 == null", "false");
    fold("!1 == !0", "false");
    fold("!0 == null", "false");

    fold("x = !0 === !0", "x = true");
    fold("x = !1 === !1", "x = true");
    fold("x = !1 === null", "x = false");
    fold("x = !1 === !0", "x = false");
    fold("x = !0 === null", "x = false");

    fold("!0 === !0", "true");
    fold("!1 === null", "false");
    fold("!1 === !0", "false");
    fold("!0 === null", "false");
  }

  public void testFoldComparison4() {
    foldSame("[] == false");  // true
    foldSame("[] == true");   // false
    foldSame("[0] == false"); // true
    foldSame("[0] == true");  // false
    foldSame("[1] == false"); // false
    foldSame("[1] == true");  // true
    foldSame("({}) == false");  // false
    foldSame("({}) == true");   // true
  }

  public void testFoldGetElem1() {
    fold("x = [,10][0]", "x = void 0");
    fold("x = [10, 20][0]", "x = 10");
    fold("x = [10, 20][1]", "x = 20");

    testSame("x = [10, 20][0.5]",
        PeepholeFoldConstants.INVALID_GETELEM_INDEX_ERROR);
    testSame("x = [10, 20][-1]",
        PeepholeFoldConstants.INDEX_OUT_OF_BOUNDS_ERROR);
    testSame("x = [10, 20][2]",
        PeepholeFoldConstants.INDEX_OUT_OF_BOUNDS_ERROR);

    foldSame("x = [foo(), 0][1]");
    fold("x = [0, foo()][1]", "x = foo()");
    foldSame("x = [0, foo()][0]");
    foldSame("for([1][0] in {});");
  }

  public void testFoldGetElem2() {
    fold("x = 'string'[5]", "x = 'g'");
    fold("x = 'string'[0]", "x = 's'");
    fold("x = 's'[0]", "x = 's'");
    foldSame("x = '\uD83D\uDCA9'[0]");

    testSame("x = 'string'[0.5]",
        PeepholeFoldConstants.INVALID_GETELEM_INDEX_ERROR);
    testSame("x = 'string'[-1]",
        PeepholeFoldConstants.INDEX_OUT_OF_BOUNDS_ERROR);
    testSame("x = 'string'[6]",
        PeepholeFoldConstants.INDEX_OUT_OF_BOUNDS_ERROR);
  }

  public void testFoldComplex() {
    fold("x = (3 / 1.0) + (1 * 2)", "x = 5");
    fold("x = (1 == 1.0) && foo() && true", "x = foo()&&true");
    fold("x = 'abc' + 5 + 10", "x = \"abc510\"");
  }

  public void testFoldLeft() {
    foldSame("(+x - 1) + 2"); // not yet
    fold("(+x + 1) + 2", "+x + 3");
  }

  public void testFoldArrayLength() {
    // Can fold
    fold("x = [].length", "x = 0");
    fold("x = [1,2,3].length", "x = 3");
    fold("x = [a,b].length", "x = 2");

    // Not handled yet
    fold("x = [,,1].length", "x = 3");

    // Cannot fold
    fold("x = [foo(), 0].length", "x = [foo(),0].length");
    fold("x = y.length", "x = y.length");
  }

  public void testFoldStringLength() {
    // Can fold basic strings.
    fold("x = ''.length", "x = 0");
    fold("x = '123'.length", "x = 3");

    // Test Unicode escapes are accounted for.
    fold("x = '123\u01dc'.length", "x = 4");
  }

  public void testFoldTypeof() {
    fold("x = typeof 1", "x = \"number\"");
    fold("x = typeof 'foo'", "x = \"string\"");
    fold("x = typeof true", "x = \"boolean\"");
    fold("x = typeof false", "x = \"boolean\"");
    fold("x = typeof null", "x = \"object\"");
    fold("x = typeof undefined", "x = \"undefined\"");
    fold("x = typeof void 0", "x = \"undefined\"");
    fold("x = typeof []", "x = \"object\"");
    fold("x = typeof [1]", "x = \"object\"");
    fold("x = typeof [1,[]]", "x = \"object\"");
    fold("x = typeof {}", "x = \"object\"");
    fold("x = typeof function() {}", "x = 'function'");

    foldSame("x = typeof[1,[foo()]]");
    foldSame("x = typeof{bathwater:baby()}");
  }

  public void testFoldInstanceOf() {
    // Non object types are never instances of anything.
    fold("64 instanceof Object", "false");
    fold("64 instanceof Number", "false");
    fold("'' instanceof Object", "false");
    fold("'' instanceof String", "false");
    fold("true instanceof Object", "false");
    fold("true instanceof Boolean", "false");
    fold("!0 instanceof Object", "false");
    fold("!0 instanceof Boolean", "false");
    fold("false instanceof Object", "false");
    fold("null instanceof Object", "false");
    fold("undefined instanceof Object", "false");
    fold("NaN instanceof Object", "false");
    fold("Infinity instanceof Object", "false");

    // Array and object literals are known to be objects.
    fold("[] instanceof Object", "true");
    fold("({}) instanceof Object", "true");

    // These cases is foldable, but no handled currently.
    foldSame("new Foo() instanceof Object");
    // These would require type information to fold.
    foldSame("[] instanceof Foo");
    foldSame("({}) instanceof Foo");

    fold("(function() {}) instanceof Object", "true");

    // An unknown value should never be folded.
    foldSame("x instanceof Foo");
  }

  public void testDivision() {
    // Make sure the 1/3 does not expand to 0.333333
    fold("print(1/3)", "print(1/3)");

    // Decimal form is preferable to fraction form when strings are the
    // same length.
    fold("print(1/2)", "print(0.5)");
  }

  public void testAssignOpsLate() {
    late = true;
    fold("x=x+y", "x+=y");
    foldSame("x=y+x");
    fold("x=x*y", "x*=y");
    fold("x=y*x", "x*=y");
    fold("x.y=x.y+z", "x.y+=z");
    foldSame("next().x = next().x + 1");

    fold("x=x-y", "x-=y");
    foldSame("x=y-x");
    fold("x=x|y", "x|=y");
    fold("x=y|x", "x|=y");
    fold("x=x*y", "x*=y");
    fold("x=y*x", "x*=y");
    fold("x.y=x.y+z", "x.y+=z");
    foldSame("next().x = next().x + 1");
    // This is OK, really.
    fold("({a:1}).a = ({a:1}).a + 1", "({a:1}).a = 2");
  }

 public void testAssignOpsEarly() {
    late = false;
    foldSame("x=x+y");
    foldSame("x=y+x");
    foldSame("x=x*y");
    foldSame("x=y*x");
    foldSame("x.y=x.y+z");
    foldSame("next().x = next().x + 1");

    foldSame("x=x-y");
    foldSame("x=y-x");
    foldSame("x=x|y");
    foldSame("x=y|x");
    foldSame("x=x*y");
    foldSame("x=y*x");
    foldSame("x.y=x.y+z");
    foldSame("next().x = next().x + 1");
    // This is OK, really.
    fold("({a:1}).a = ({a:1}).a + 1", "({a:1}).a = 2");
  }

  public void testFoldAdd1() {
    fold("x=false+1", "x=1");
    fold("x=true+1", "x=2");
    fold("x=1+false", "x=1");
    fold("x=1+true", "x=2");
  }

  public void testFoldLiteralNames() {
    fold("NaN == NaN", "false");
    fold("Infinity == Infinity", "true");
    fold("Infinity == NaN", "false");
    fold("undefined == NaN", "false");
    fold("undefined == Infinity", "false");

    fold("Infinity >= Infinity", "true");
    fold("NaN >= NaN", "false");
  }

  public void testFoldLiteralsTypeMismatches() {
    fold("true == true", "true");
    fold("true == false", "false");
    fold("true == null", "false");
    fold("false == null", "false");

    // relational operators convert its operands
    fold("null <= null", "true"); // 0 = 0
    fold("null >= null", "true");
    fold("null > null", "false");
    fold("null < null", "false");

    fold("false >= null", "true"); // 0 = 0
    fold("false <= null", "true");
    fold("false > null", "false");
    fold("false < null", "false");

    fold("true >= null", "true");  // 1 > 0
    fold("true <= null", "false");
    fold("true > null", "true");
    fold("true < null", "false");

    fold("true >= false", "true");  // 1 > 0
    fold("true <= false", "false");
    fold("true > false", "true");
    fold("true < false", "false");
  }

  public void testFoldLeftChildConcat() {
    foldSame("x +5 + \"1\"");
    fold("x+\"5\" + \"1\"", "x + \"51\"");
    // fold("\"a\"+(c+\"b\")", "\"a\"+c+\"b\"");
    fold("\"a\"+(\"b\"+c)", "\"ab\"+c");
  }

  public void testFoldLeftChildOp() {
    fold("x * Infinity * 2", "x * Infinity");
    foldSame("x - Infinity - 2"); // want "x-Infinity"
    foldSame("x - 1 + Infinity");
    foldSame("x - 2 + 1");
    foldSame("x - 2 + 3");
    foldSame("1 + x - 2 + 1");
    foldSame("1 + x - 2 + 3");
    foldSame("1 + x - 2 + 3 - 1");
    foldSame("f(x)-0");
    foldSame("x-0-0");
    foldSame("x+2-2+2");
    foldSame("x+2-2+2-2");
    foldSame("x-2+2");
    foldSame("x-2+2-2");
    foldSame("x-2+2-2+2");

    foldSame("1+x-0-NaN");
    foldSame("1+f(x)-0-NaN");
    foldSame("1+x-0+NaN");
    foldSame("1+f(x)-0+NaN");

    foldSame("1+x+NaN"); // unfoldable
    foldSame("x+2-2");   // unfoldable
    foldSame("x+2");  // nothing to do
    foldSame("x-2");  // nothing to do
  }

  public void testFoldSimpleArithmeticOp() {
    foldSame("x*NaN");
    foldSame("NaN/y");
    foldSame("f(x)-0");
    foldSame("f(x)*1");
    foldSame("1*f(x)");
    foldSame("0+a+b");
    foldSame("0-a-b");
    foldSame("a+b-0");
    foldSame("(1+x)*NaN");

    foldSame("(1+f(x))*NaN"); // don't fold side-effects
  }

  public void testFoldLiteralsAsNumbers() {
    fold("x/'12'", "x/12");
    fold("x/('12'+'6')", "x/126");
    fold("true*x", "1*x");
    fold("x/false", "x/0");  // should we add an error check? :)
  }

  public void testNotFoldBackToTrueFalse() {
    late = false;
    fold("!0", "true");
    fold("!1", "false");
    fold("!3", "false");

    late = true;
    foldSame("!0");
    foldSame("!1");
    fold("!3", "false");
    foldSame("false");
    foldSame("true");
  }

  public void testFoldBangConstants() {
    fold("1 + !0", "2");
    fold("1 + !1", "1");
    fold("'a ' + !1", "'a false'");
    fold("'a ' + !0", "'a true'");
  }

  public void testFoldMixed() {
    fold("''+[1]", "'1'");
    fold("false+[]", "\"false\"");
  }

  public void testFoldVoid() {
    foldSame("void 0");
    fold("void 1", "void 0");
    fold("void x", "void 0");
    fold("void x()", "void x()");
  }

  public void testObjectLiteral() {
    test("(!{})", "false");
    test("(!{a:1})", "false");
    testSame("(!{a:foo()})");
    testSame("(!{'a':foo()})");
  }

  public void testArrayLiteral() {
    test("(![])", "false");
    test("(![1])", "false");
    test("(![a])", "false");
    testSame("(![foo()])");
  }

  public void testIssue601() {
    testSame("'\\v' == 'v'");
    testSame("'v' == '\\v'");
    testSame("'\\u000B' == '\\v'");
  }

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

    // functions can't reference the object through 'this'.
    testSame("({a:function(){return this}}).a");
    testSame("({get a() {return this}}).a");
    testSame("({set a(b) {return this}}).a");

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
    test("var x = ({a:function(){return 1}}).a",
         "var x = function(){return 1}");

    test("var x = ({a:1}).a", "var x = 1");
    test("var x = ({a:1, a:2}).a", "var x = 2");
    test("var x = ({a:1, a:foo()}).a", "var x = foo()");
    test("var x = ({a:foo()}).a", "var x = foo()");

    test("function f() { return {a:1, b:2}.a; }",
         "function f() { return 1; }");

    // GETELEM is handled the same way.
    test("var x = ({'a':1})['a']", "var x = 1");
  }

  public void testFoldObjectLiteralRef2() {
    late = false;
    test("({a:x}).a += 1", "({a:x}).a = x + 1");
    late = true;
    testSame("({a:x}).a += 1");
  }

  public void testIEString() {
    testSame("!+'\\v1'");
  }

  public void testIssue522() {
    testSame("[][1] = 1;");
  }

  public void testTypeBasedFoldConstant() {
    enableTypeCheck();
    test("var /** number */ x; x + 1 + 1 + x",
         "var /** number */ x; x + 2 + x");

    test("var /** boolean */ x; x + 1 + 1 + x",
         "var /** boolean */ x; x + 2 + x");

    test("var /** null */ x; x + 1 + 1 + x",
         "var /** null */ x; 2");

    test("var /** undefined */ x; x + 1 + 1 + x",
         "var /** undefined */ x; NaN");

    test("var /** null */ x; var y = true > x;", "var /** null */ x; var y = true;");

    test("var /** null */ x; var y = null > x;", "var /** null */ x; var y = false;");

    testSame("var /** string */ x; x + 1 + 1 + x");
    disableTypeCheck();
  }

  public void foldDefineProperties1() {
    test("Object.defineProperties({}, {})", "{}");
    test("Object.defineProperties(a, {})", "a");
    testSame("Object.defineProperties(a, {anything:1})");
  }

  private static final List<String> LITERAL_OPERANDS =
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
          //"({})",
          // "[]"
          //"[0]",
          //"Object",
          //"(function() {})"
          );

  public void testInvertibleOperators() {
    Map<String, String> inverses = ImmutableMap.<String, String>builder()
        .put("==", "!=")
        .put("===", "!==")
        .put("<=", ">")
        .put("<", ">=")
        .put(">=", "<")
        .put(">", "<=")
        .put("!=", "==")
        .put("!==", "===")
        .build();
    Set<String> comparators = ImmutableSet.of("<=", "<", ">=", ">");
    Set<String> equalitors = ImmutableSet.of("==", "===");
    Set<String> uncomparables = ImmutableSet.of("undefined", "void 0");
    List<String> operators = ImmutableList.copyOf(inverses.values());
    for (int iOperandA = 0; iOperandA < LITERAL_OPERANDS.size(); iOperandA++) {
      for (int iOperandB = 0;
           iOperandB < LITERAL_OPERANDS.size();
           iOperandB++) {
        for (int iOp = 0; iOp < operators.size(); iOp++) {
          String a = LITERAL_OPERANDS.get(iOperandA);
          String b = LITERAL_OPERANDS.get(iOperandB);
          String op = operators.get(iOp);
          String inverse = inverses.get(op);

          // Test invertability.
          if (comparators.contains(op)) {
              if (uncomparables.contains(a) || uncomparables.contains(b)
                  || (a.equals("null") && NodeUtil.getStringNumberValue(b) == null)) {
                assertSameResults(join(a, op, b), "false");
                assertSameResults(join(a, inverse, b), "false");
              }
          } else if (a.equals(b) && equalitors.contains(op)) {
            if (a.equals("NaN") ||
                a.equals("Infinity") ||
                a.equals("-Infinity")) {
              fold(join(a, op, b), a.equals("NaN") ? "false" : "true");
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

  public void testCommutativeOperators() {
    late = true;
    List<String> operators =
        ImmutableList.of(
            "==",
            "!=",
            "===",
            "!==",
            "*",
            "|",
            "&",
            "^");
    for (int iOperandA = 0; iOperandA < LITERAL_OPERANDS.size(); iOperandA++) {
      for (int iOperandB = iOperandA;
           iOperandB < LITERAL_OPERANDS.size();
           iOperandB++) {
        for (int iOp = 0; iOp < operators.size(); iOp++) {
          String a = LITERAL_OPERANDS.get(iOperandA);
          String b = LITERAL_OPERANDS.get(iOperandB);
          String op = operators.get(iOp);

          // Test commutativity.
          assertSameResults(join(a, op, b), join(b, op, a));
        }
      }
    }
  }

  public void testConvertToNumberNegativeInf() {
    foldSame("var x = 3 * (r ? Infinity : -Infinity);");
  }

  private static String join(String operandA, String op, String operandB) {
    return operandA + " " + op + " " + operandB;
  }

  private void assertSameResults(String exprA, String exprB) {
    assertEquals(
        "Expressions did not fold the same\nexprA: " +
        exprA + "\nexprB: " + exprB,
        process(exprA), process(exprB));
  }

  private void assertNotSameResults(String exprA, String exprB) {
    assertFalse(
        "Expressions folded the same\nexprA: " +
        exprA + "\nexprB: " + exprB,
        process(exprA).equals(process(exprB)));
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
    assertNotNull("Unexpected parse error(s): " + Joiner.on("\n").join(compiler.getErrors())
        + "\nEXPR: " + js, root);
    Node externsRoot = root.getFirstChild();
    Node mainRoot = externsRoot.getNext();
    if (runProcessor) {
      getProcessor(compiler).process(externsRoot, mainRoot);
    }
    return compiler.toSource(mainRoot);
  }
}
