/*
 * Copyright 2006 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.testing.TestExternsBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link TypeCheck}. */
@RunWith(JUnit4.class)
public final class TypeCheckBigIntTest extends TypeCheckTestCase {

  @Test
  public void testTypeofBigInt() {
    newTest()
        .addSource(
            "/**",
            " * @param {bigint|number} i",
            " * @return {boolean}",
            " */",
            "function foo(i) {",
            "  return typeof i === 'bigint';",
            "}")
        .run();
  }

  @Test
  public void testBigIntArgument() {
    newTest().addSource("BigInt(1)").run();
    newTest()
        .addSource("BigInt({})")
        .addDiagnostic(
            lines(
                "actual parameter 1 of BigInt does not match formal parameter",
                "found   : {}",
                "required: (bigint|number|string)"))
        .run();
  }

  @Test
  public void testBigIntOperators_increment() {
    newTest().addSource("const x = 1n; x++;").run();
    newTest().addSource("/** @type {!BigInt} */ var x; x++;").run();
    newTest().addSource("/** @type {bigint|number} */var x; x++;").run();
    newTest()
        .addSource("/** @type {bigint|string} */var x; x++;")
        .addDiagnostic(
            lines(
                "increment/decrement", //
                "found   : (bigint|string)",
                "required: (bigint|number)"))
        .run();
  }

  @Test
  public void testBigIntOperators_decrement() {
    newTest().addSource("const x = 1n; x--;").run();
    newTest().addSource("/** @type {!BigInt} */ var x; x--;").run();
    newTest().addSource("/** @type {bigint|number} */var x; x--;").run();
    newTest()
        .addSource("/** @type {bigint|string} */var x; x--;")
        .addDiagnostic(
            lines(
                "increment/decrement", //
                "found   : (bigint|string)",
                "required: (bigint|number)"))
        .run();
  }

  @Test
  public void testBigIntOperators_logicalNot() {
    newTest().addSource("const x = 1n; !x;").run();
    newTest().addSource("/** @type {!BigInt} */ var x; !x;").run();
    newTest().addSource("/** @type {bigint|string} */var x; !x;").run();
  }

  @Test
  public void testBigIntOperators_bitwiseNot() {
    newTest().addSource("const x = 1n; ~x;").run();
    newTest().addSource("/** @type {!BigInt} */ var x; ~x;").run();
    newTest().addSource("/** @type {?} */var x; ~x;").run();
    newTest().addSource("/** @type {bigint|number} */var x; ~x;").run();
    newTest()
        .addSource("/** @type {bigint|string} */var x; ~x;")
        .addDiagnostic(
            lines(
                "bitwise NOT", //
                "found   : (bigint|string)",
                "required: (bigint|number)"))
        .run();
  }

  @Test
  public void testBigIntValueOperators_unaryPlusIsForbidden() {
    newTest()
        .addSource("var x = 1n; +x;")
        .addDiagnostic("unary operator + cannot be applied to bigint")
        .run();
  }

  @Test
  public void testBigIntObjectOperators_unaryPlusIsForbidden() {
    newTest()
        .addSource("/** @type {!BigInt} */ var x; +x;")
        .addDiagnostic("unary operator + cannot be applied to BigInt")
        .run();
  }

  @Test
  public void testBigIntUnionOperators_unaryPlusIsForbidden() {
    newTest()
        .addSource("/** @type {bigint|number} */ var x; +x;")
        .addDiagnostic("unary operator + cannot be applied to (bigint|number)")
        .run();
  }

  @Test
  public void testBigIntEnumOperators_unaryPlusIsForbidden() {
    newTest()
        .addSource("/** @enum {bigint} */ const BIGINTS = {ONE: 1n, TWO: 2n}; +BIGINTS.ONE;")
        .addDiagnostic("unary operator + cannot be applied to BIGINTS<bigint>")
        .run();
  }

  @Test
  public void testBigIntOperators_unaryMinus() {
    newTest().addSource("const x = 1n; -x;").run();
    newTest().addSource("/** @type {!BigInt} */ var x; -x;").run();
    newTest().addSource("/** @type {?} */var x; -x;").run();
    newTest().addSource("/** @type {bigint|number} */var x; -x;").run();
    newTest()
        .addSource("/** @type {bigint|string} */var x; -x;")
        .addDiagnostic(
            lines(
                "unary minus operator", //
                "found   : (bigint|string)",
                "required: (bigint|number)"))
        .run();
  }

  @Test
  public void testBigIntOperators_binaryOperationWithSelf() {
    newTest().addSource("const x = 1n; const y = 1n; x * y").run();
  }

  @Test
  public void testBigIntOperators_assignOpWithSelf() {
    newTest().addSource("const x = 1n; const y = 1n; x *= y").run();
  }

  @Test
  public void testBigIntOperators_binaryBitwiseOperationWithSelf() {
    newTest().addSource("const x = 1n; const y = 1n; x | y").run();
  }

  @Test
  public void testBigIntOperators_additionWithString() {
    newTest().addSource("const x = 1n; const y = 'str'; x + y").run();
  }

  @Test
  public void testBigIntOperators_assignAddWithString() {
    newTest().addSource("const x = 1n; const y = 'str'; x += y").run();
  }

  @Test
  public void testBigIntOperators_binaryOperationWithString() {
    newTest()
        .addSource("const x = 1n; const y = 'str'; x * y")
        .addDiagnostic("operator * cannot be applied to bigint and string")
        .run();
  }

  @Test
  public void testBigIntOperators_assignOpWithString() {
    newTest()
        .addSource("const x = 1n; const y = 'str'; x *= y")
        .addDiagnostic("operator *= cannot be applied to bigint and string")
        .run();
  }

  @Test
  public void testBigIntOperators_binaryOperationWithUnknown() {
    newTest()
        .addSource("var x = 1n; /** @type {?} */var y; x * y")
        .addDiagnostic("operator * cannot be applied to bigint and ?")
        .run();
  }

  @Test
  public void testBigIntOperators_assignOpWithUnknown() {
    newTest()
        .addSource("var x = 1n; /** @type {?} */var y; x *= y")
        .addDiagnostic("operator *= cannot be applied to bigint and ?")
        .run();
  }

  @Test
  public void testBigIntOperators_binaryOperationWithNumber() {
    newTest()
        .addSource("const x = 1n; const y = 1; x * y")
        .addDiagnostic("operator * cannot be applied to bigint and number")
        .run();
  }

  @Test
  public void testBigIntOperators_assignOpWithNumber() {
    newTest()
        .addSource("const x = 1n; const y = 1; x *= y")
        .addDiagnostic("operator *= cannot be applied to bigint and number")
        .run();
  }

  @Test
  public void testBigIntOperators_binaryBitwiseOperationWithNumber() {
    newTest()
        .addSource("const x = 1n; const y = 1; x | y")
        .addDiagnostic("operator | cannot be applied to bigint and number")
        .run();
  }

  @Test
  public void testBigIntLeftShift() {
    newTest().addSource("1n << 2n").run();
    newTest().addSource("let x = 1n; x <<= 2n").run();
  }

  @Test
  public void testBigIntRightShift() {
    newTest().addSource("2n >> 1n").run();
    newTest().addSource("let x = 2n; x >>= 1n").run();
  }

  @Test
  public void testBigIntOperators_unsignedRightShift() {
    newTest()
        .addSource("const x = 1n; x >>> x;")
        .addDiagnostic("operator >>> cannot be applied to bigint and bigint")
        .run();
  }

  @Test
  public void testBigIntOperators_assignUnsignedRightShift() {
    newTest()
        .addSource("let x = 1n; x >>>= x;")
        .addDiagnostic("operator >>>= cannot be applied to bigint and bigint")
        .run();
  }

  @Test
  public void testBigIntOrNumberOperators_binaryOperationWithSelf() {
    newTest().addSource("/** @type {bigint|number} */ var x; x * x;").run();
  }

  @Test
  public void testBigIntOrNumberOperators_assignOpWithSelf() {
    newTest().addSource("/** @type {bigint|number} */ var x; x *= x;").run();
  }

  @Test
  public void testBigIntOrNumberOperators_binaryBitwiseOperationWithSelf() {
    newTest().addSource("/** @type {bigint|number} */ var x; x | x;").run();
  }

  @Test
  public void testBigIntOrNumberOperators_binaryOperationWithBigInt() {
    newTest()
        .addSource("var x = 1n; /** @type {bigint|number} */ var y; x * y;")
        .addDiagnostic("operator * cannot be applied to bigint and (bigint|number)")
        .run();
  }

  @Test
  public void testBigIntOrNumberOperators_assignOpWithBigInt() {
    newTest()
        .addSource("var x = 1n; /** @type {bigint|number} */ var y; x *= y;")
        .addDiagnostic("operator *= cannot be applied to bigint and (bigint|number)")
        .run();
  }

  @Test
  public void testBigIntOrNumberOperators_binaryBitwiseOperationWithBigInt() {
    newTest()
        .addSource("var x = 1n; /** @type {bigint|number} */ var y; x | y;")
        .addDiagnostic("operator | cannot be applied to bigint and (bigint|number)")
        .run();
  }

  @Test
  public void testBigIntOrNumberOperators_unsignedRightShift() {
    newTest()
        .addSource("/** @type {bigint|number} */ var x = 1n; x >>> x;")
        .addDiagnostic("operator >>> cannot be applied to (bigint|number) and (bigint|number)")
        .run();
  }

  @Test
  public void testBigIntOrNumberOperators_assignUnsignedRightShift() {
    newTest()
        .addSource("/** @type {bigint|number} */ var x = 1n; x >>>= x;")
        .addDiagnostic("operator >>>= cannot be applied to (bigint|number) and (bigint|number)")
        .run();
  }

  @Test
  public void testBigIntOrOtherOperators_binaryOperationWithBigInt() {
    newTest()
        .addSource("/** @type {bigint|string} */ var x; var y = 1n; x * y;")
        .addDiagnostic("operator * cannot be applied to (bigint|string) and bigint")
        .run();
  }

  @Test
  public void testBigIntOrOtherOperators_assignOpWithBigInt() {
    newTest()
        .addSource("/** @type {bigint|string} */ var x; var y = 1n; x *= y;")
        .addDiagnostic("operator *= cannot be applied to (bigint|string) and bigint")
        .run();
  }

  @Test
  public void testBigIntOrOtherOperators_binaryOperationWithBigIntOrNumber() {
    newTest()
        .addSource("/** @type {bigint|string} */ var x; /** @type {bigint|number} */ var y; x * y;")
        .addDiagnostic("operator * cannot be applied to (bigint|string) and (bigint|number)")
        .run();
  }

  @Test
  public void testBigIntOrOtherOperators_assignOpWithBigIntOrNumber() {
    newTest()
        .addSource(
            "/** @type {bigint|string} */ var x; /** @type {bigint|number} */ var y; x *= y;")
        .addDiagnostic("operator *= cannot be applied to (bigint|string) and (bigint|number)")
        .run();
  }

  @Test
  public void testBigIntEnumOperators_binaryOperationWithSelf() {
    newTest()
        .addSource(
            "/** @enum {bigint} */ const BIGINTS = {ONE: 1n, TWO: 2n}; BIGINTS.ONE * BIGINTS.TWO;")
        .run();
  }

  @Test
  public void testBigIntEnumOperators_assignOpWithSelf() {
    newTest()
        .addSource(
            "/** @enum {bigint} */ const BIGINTS = {ONE: 1n, TWO: 2n}; BIGINTS.ONE *= BIGINTS.TWO;")
        .run();
  }

  @Test
  public void testValidBigIntComparisons() {
    newTest().addSource("var x = 1n; var y = 2n; x < y").run();
    newTest().addSource("var x = 1n; /** @type {!BigInt} */ var y; x < y").run();
    newTest().addSource("var x = 1n; var y = 2; x < y").run();
    newTest().addSource("var x = 1n; /** @type {?} */ var y; x < y").run();
  }

  @Test
  public void testBigIntComparisonWithString() {
    newTest()
        .addSource("const x = 1n; const y = 'asdf'; x < y;")
        .addDiagnostic(
            lines(
                "right side of numeric comparison",
                "found   : string",
                "required: (bigint|number)"))
        .run();
  }

  @Test
  public void testValidBigIntObjectComparisons() {
    newTest().addSource("/** @type {!BigInt} */ var x; var y = 2n; x < y").run();
    newTest().addSource("/** @type {!BigInt} */ var x; /** @type {!BigInt} */ var y; x < y").run();
    newTest().addSource("/** @type {!BigInt} */ var x; var y = 2; x < y").run();
    newTest().addSource("/** @type {!BigInt} */ var x; /** @type {?} */ var y; x < y").run();
  }

  @Test
  public void testValidBigIntOrNumberComparisons() {
    newTest()
        .addSource("/** @type {bigint|number} */ var x; /** @type {bigint|number} */ var y; x < y;")
        .run();
    newTest().addSource("/** @type {bigint|number} */ var x; var y = 2; x < y;").run();
    newTest().addSource("/** @type {bigint|number} */ var x; var y = 2n; x < y;").run();
    newTest()
        .addSource("/** @type {bigint|number} */ var x; /** @type {!BigInt} */ var y; x < y;")
        .run();
    newTest().addSource("/** @type {bigint|number} */ var x; /** @type {?} */ var y; x < y").run();
  }

  @Test
  public void testBigIntOrNumberComparisonWithString() {
    newTest()
        .addSource("/** @type {bigint|number} */ var x; 'asdf' < x;")
        .addDiagnostic(
            lines(
                "left side of numeric comparison", //
                "found   : string",
                "required: (bigint|number)"))
        .run();
  }

  @Test
  public void testValidBigIntOrOtherComparisons() {
    newTest()
        .addSource("/** @type {bigint|string} */ var x; /** @type {bigint|string} */ var y; x < y;")
        .run();
    newTest().addSource("/** @type {bigint|string} */ var x; /** @type {?} */ var y; x < y").run();
  }

  @Test
  public void testBigIntOrOtherComparisonWithBigint() {
    newTest()
        .addSource("/** @type {bigint|string} */ var x; var y = 2n; x < y;")
        .addDiagnostic(
            lines(
                "left side of numeric comparison",
                "found   : (bigint|string)",
                "required: (bigint|number)"))
        .run();
  }

  @Test
  public void testBigIntOrOtherComparisonWithNumber() {
    newTest()
        .addSource("/** @type {bigint|string} */ var x; var y = 2; x < y;")
        .addDiagnostic(
            lines(
                "left side of numeric comparison",
                "found   : (bigint|string)",
                "required: (bigint|number)"))
        .run();
  }

  @Test
  public void testBigIntObjectIndex() {
    // As is, TypeCheck allows for objects to be indexed with bigint. An error could be reported as
    // is done with arrays, but for now we will avoid such restrictions.
    newTest()
        .addSource(
            "var obj = {};",
            "/** @type {bigint} */ var b;",
            "/** @type {bigint|number} */ var bn;",
            "obj[b] = 1;",
            "obj[bn] = 3;")
        .run();
  }

  @Test
  public void testBigIntArrayIndex() {
    // Even though the spec doesn't prohibit using bigint as an array index, we will report an error
    // to maintain consistency with TypeScript.
    newTest()
        .addSource("var arr = []; /** @type {bigint} */ var b; arr[b];")
        .addDiagnostic(
            lines(
                "restricted index type", //
                "found   : bigint",
                "required: number"))
        .run();
  }

  @Test
  public void testBigIntConstructorWithNew() {
    // BigInt object function type cannot be called with "new" keyword
    newTest()
        .addSource("new BigInt(1)")
        .addDiagnostic(
            "cannot instantiate non-constructor, found type: function(new:BigInt,"
                + " (bigint|number|string)): bigint")
        .run();
  }

  @Test
  public void testBigIntOrNumberArrayIndex() {
    // Even though the spec doesn't prohibit using bigint as an array index, we will report an error
    // to maintain consistency with TypeScript.
    newTest()
        .addSource("var arr = []; /** @type {bigint|number} */ var bn; arr[bn];")
        .addDiagnostic(
            lines(
                "restricted index type", //
                "found   : (bigint|number)",
                "required: number"))
        .run();
  }

  @Test
  public void testBigIntAsComputedPropForObjects() {
    newTest().addSource("/** @type {bigint} */ var x; ({[x]: 'value', 123n() {}});").run();
  }

  @Test
  public void testBigIntAsComputedPropForClasses() {
    newTest().addSource("/** @unrestricted */ class C { 123n() {} }").run();
    newTest().addSource("/** @dict */ class C { 123n() {} }").run();
  }

  @Test
  public void testBigIntAsComputedPropForStructClasses() {
    newTest()
        .addSource("class C { 123n() {} }")
        .addDiagnostic("Cannot do '[]' access on a struct")
        .run();
  }

  @Test
  public void testBigIntLiteralProperty() {
    newTest()
        .addExterns(new TestExternsBuilder().addBigInt().build())
        .addSource("(1n).toString()")
        .run();
  }
}
