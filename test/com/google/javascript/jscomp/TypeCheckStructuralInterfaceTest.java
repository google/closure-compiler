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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link TypeCheck}. */
@RunWith(JUnit4.class)
public final class TypeCheckStructuralInterfaceTest extends TypeCheckTestCase {

  /**
   * although C1 does not declare to extend Interface1, obj2 : C1 still structurally matches obj1 :
   * Interface1 because of the structural interface matching (Interface1 is declared with @record
   * tag)
   */
  @Test
  public void testStructuralInterfaceMatching1() {
    newTest()
        .addExterns(
            "/** @record */",
            "function Interface1() {}",
            "/** @type {number} */",
            "Interface1.prototype.length;",
            "",
            "/** @constructor */",
            "function C1() {}",
            "/** @type {number} */",
            "C1.prototype.length;")
        .addSource(
            "/** @type{Interface1} */",
            "var obj1;",
            "/** @type{C1} */",
            "var obj2 = new C1();",
            "obj1 = obj2;")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching2() {
    newTest()
        .addExterns(
            "/** @record */",
            "function Interface1() {}",
            "/** @type {number} */",
            "Interface1.prototype.length;",
            "",
            "/** @constructor */",
            "function C1() {}",
            "/** @type {number} */",
            "C1.prototype.length;")
        .addSource(
            "/** @type{Interface1} */", //
            "var obj1;",
            "var obj2 = new C1();",
            "obj1 = obj2;")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching3() {
    newTest()
        .addExterns(
            "/** @record */", //
            "function I1() {}",
            "",
            "/** @record */",
            "function I2() {}")
        .addSource(
            "/** @type {I1} */", //
            "var i1;",
            "/** @type {I2} */",
            "var i2;",
            "i1 = i2;",
            "i2 = i1;")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching4_1() {
    newTest()
        .addExterns(
            "/** @record */", //
            "function I1() {}",
            "",
            "/** @record */",
            "function I2() {}")
        .addSource(
            "/** @type {I1} */", //
            "var i1;",
            "/** @type {I2} */",
            "var i2;",
            "i2 = i1;",
            "i1 = i2;")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching5_1() {
    newTest()
        .addExterns(
            "/** @record */",
            "function I1() {}",
            "",
            "/** @interface */",
            "function I3() {}",
            "/** @type {number} */",
            "I3.prototype.length;")
        .addSource(
            "/** @type {I1} */", //
            "var i1;",
            "/** @type {I3} */",
            "var i3;",
            "i1 = i3;")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching7_1() {
    newTest()
        .addExterns(
            "/** @record */",
            "function I1() {}",
            "",
            "/** @constructor */",
            "function C1() {}",
            "/** @type {number} */",
            "C1.prototype.length;")
        .addSource(
            "/** @type {I1} */",
            "var i1;" + "/** @type {C1} */",
            "var c1;",
            "i1 = c1;   // no warning")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching9() {
    newTest()
        .addExterns(
            "/** @constructor */",
            "function C1() {}",
            "/** @type {number} */",
            "C1.prototype.length;",
            "",
            "/** @constructor */",
            "function C2() {}",
            "/** @type {number} */",
            "C2.prototype.length;")
        .addSource(
            "/** @type {C1} */", //
            "var c1;" + "/** @type {C2} */",
            "var c2;",
            "c1 = c2;")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : (C2|null)",
                "required: (C1|null)"))
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching11_1() {
    newTest()
        .addExterns(
            "/** @interface */",
            "function I3() {}",
            "/** @type {number} */",
            "I3.prototype.length;",
            "",
            "/** ",
            " * @record",
            " * @extends I3",
            " */",
            "function I4() {}",
            "/** @type {boolean} */",
            "I4.prototype.prop;",
            "",
            "/** @constructor */",
            "function C4() {}",
            "/** @type {number} */",
            "C4.prototype.length;",
            "/** @type {boolean} */",
            "C4.prototype.prop;")
        .addSource(
            "/** @type {I4} */", //
            "var i4;" + "/** @type {C4} */",
            "var c4;",
            "i4 = c4;")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching13() {
    newTest()
        .addExterns(
            "/**",
            "   * @record",
            "   */",
            "  function I5() {}",
            "  /** @type {I5} */",
            "  I5.prototype.next;",
            "",
            "  /**",
            "   * @interface",
            "   */",
            "  function C5() {}",
            "  /** @type {C5} */",
            "  C5.prototype.next;")
        .addSource(
            "/** @type {I5} */", //
            "var i5;" + "/** @type {C5} */",
            "var c5;",
            "i5 = c5;")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching13_2() {
    newTest()
        .addExterns(
            "/**",
            "   * @record",
            "   */",
            "  function I5() {}",
            "  /** @type {I5} */",
            "  I5.prototype.next;",
            "",
            "  /**",
            "   * @record",
            "   */",
            "  function C5() {}",
            "  /** @type {C5} */",
            "  C5.prototype.next;")
        .addSource(
            "/** @type {I5} */", //
            "var i5;" + "/** @type {C5} */",
            "var c5;",
            "i5 = c5;")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching13_3() {
    newTest()
        .addExterns(
            "/**",
            "   * @interface",
            "   */",
            "  function I5() {}",
            "  /** @type {I5} */",
            "  I5.prototype.next;",
            "",
            "  /**",
            "   * @record",
            "   */",
            "  function C5() {}",
            "  /** @type {C5} */",
            "  C5.prototype.next;")
        .addSource(
            "/** @type {I5} */", //
            "var i5;" + "/** @type {C5} */",
            "var c5;",
            "i5 = c5;")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : (C5|null)",
                "required: (I5|null)"))
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching15() {
    newTest()
        .addExterns(
            "/** @record */",
            "function I5() {}",
            "/** @type {I5} */",
            "I5.prototype.next;",
            "",
            "/** @constructor */",
            "function C6() {}",
            "/** @type {C6} */",
            "C6.prototype.next;",
            "",
            "/** @constructor */",
            "function C5() {}",
            "/** @type {C6} */",
            "C5.prototype.next;")
        .addSource(
            "/** @type {I5} */", //
            "var i5;" + "/** @type {C5} */",
            "var c5;",
            "i5 = c5;")
        .includeDefaultExterns()
        .run();
  }

  /**
   * a very long structural chain, all property types from I5 and C5 are structurally the same, I5
   * is declared as @record so structural interface matching will be performed
   */
  private static final String EXTERNS_FOR_LONG_MATCHING_CHAIN_RECORD =
      lines(
          "/** @record */",
          "function I5() {}",
          "/** @type {I5} */",
          "I5.prototype.next;",
          "",
          "/** @constructor */",
          "function C6() {}",
          "/** @type {C6} */",
          "C6.prototype.next;",
          "",
          "/** @constructor */",
          "function C6_1() {}",
          "/** @type {C6} */",
          "C6_1.prototype.next;",
          "",
          "/** @constructor */",
          "function C6_2() {}",
          "/** @type {C6_1} */",
          "C6_2.prototype.next;",
          "",
          "/** @constructor */",
          "function C6_3() {}",
          "/** @type {C6_2} */",
          "C6_3.prototype.next;",
          "",
          "/** @constructor */",
          "function C6_4() {}",
          "/** @type {C6_3} */",
          "C6_4.prototype.next;",
          "",
          "/** @constructor */",
          "function C6_5() {}",
          "/** @type {C6_4} */",
          "C6_5.prototype.next;",
          "",
          "/** @constructor */",
          "function C5() {}",
          "/** @type {C6_5} */",
          "C5.prototype.next;");

  @Test
  public void testStructuralInterfaceMatching16_1() {
    // I5 and C5 shares the same type structure
    newTest()
        .addExterns(EXTERNS_FOR_LONG_MATCHING_CHAIN_RECORD)
        .addSource(
            "/** @type {I5} */", //
            "var i5;" + "/** @type {C5} */",
            "var c5;",
            "i5 = c5;")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching17_1() {
    // I5 and C5 shares the same type structure
    newTest()
        .addExterns(EXTERNS_FOR_LONG_MATCHING_CHAIN_RECORD)
        .addSource(
            "/** @type {C5} */",
            "var c5;",
            "/**",
            " * @param {I5} i5",
            " */",
            "function f(i5) {}",
            "",
            "f(c5);")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching18_1() {
    // I5 and C5 shares the same type structure
    newTest()
        .addExterns(EXTERNS_FOR_LONG_MATCHING_CHAIN_RECORD)
        .addSource(
            "/** @type {I5} */", //
            "var i5;" + "/** @type {C5} */",
            "var c5;",
            "i5.next = c5;")
        .includeDefaultExterns()
        .run();
  }

  /**
   * a very long non-structural chain, there is a slight difference between the property type
   * structural of I5 and that of C5: I5.next.next.next.next.next has type I5 while
   * C5.next.next.next.next.next has type number
   */
  private static final String EXTERNS_FOR_LONG_NONMATCHING_CHAIN =
      lines(
          "/** @record */",
          "function I5() {}",
          "/** @type {I5} */",
          "I5.prototype.next;",
          "",
          "/** @constructor */",
          "function C6() {}",
          "/** @type {number} */",
          "C6.prototype.next;",
          "",
          "/** @constructor */",
          "function C6_1() {}",
          "/** @type {C6} */",
          "C6_1.prototype.next;",
          "",
          "/** @constructor */",
          "function C6_2() {}",
          "/** @type {C6_1} */",
          "C6_2.prototype.next;",
          "",
          "/** @constructor */",
          "function C6_3() {}",
          "/** @type {C6_2} */",
          "C6_3.prototype.next;",
          "",
          "/** @constructor */",
          "function C6_4() {}",
          "/** @type {C6_3} */",
          "C6_4.prototype.next;",
          "",
          "/** @constructor */",
          "function C6_5() {}",
          "/** @type {C6_4} */",
          "C6_5.prototype.next;",
          "",
          "/** @interface */",
          "function C5() {}",
          "/** @type {C6_5} */",
          "C5.prototype.next;");

  @Test
  public void testStructuralInterfaceMatching19() {
    // the type structure of I5 and C5 are different
    newTest()
        .addExterns(EXTERNS_FOR_LONG_NONMATCHING_CHAIN)
        .addSource(
            "/** @type {I5} */", //
            "var i5;",
            "/** @type {C5} */",
            "var c5;",
            "i5 = c5;")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : (C5|null)",
                "required: (I5|null)"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching20() {
    // the type structure of I5 and C5 are different
    newTest()
        .addExterns(EXTERNS_FOR_LONG_NONMATCHING_CHAIN)
        .addSource(
            "/** @type {C5} */",
            "var c5;",
            "/**",
            " * @param {I5} i5",
            " */",
            "function f(i5) {}",
            "",
            "f(c5);")
        .addDiagnostic(
            lines(
                "actual parameter 1 of f does not match formal parameter",
                "found   : (C5|null)",
                "required: (I5|null)"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching21() {
    // the type structure of I5 and C5 are different
    newTest()
        .addExterns(EXTERNS_FOR_LONG_NONMATCHING_CHAIN)
        .addSource(
            "/** @type {I5} */", //
            "var i5;",
            "/** @type {C5} */",
            "var c5;",
            "i5.next = c5;")
        .addDiagnostic(
            lines(
                "assignment to property next of I5", //
                "found   : (C5|null)",
                "required: (I5|null)"))
        .includeDefaultExterns()
        .run();
  }

  /**
   * structural interface matching will also be able to structurally match ordinary function types
   * check if the return types of the ordinary function types match (should match, since declared
   * with @record)
   */
  @Test
  public void testStructuralInterfaceMatching22_1() {
    // I5 and C5 shares the same type structure
    newTest()
        .addExterns(
            EXTERNS_FOR_LONG_MATCHING_CHAIN_RECORD,
            "/** @record */",
            "function I7() {}",
            "/** @type{function(): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(): C5} */",
            "C7.prototype.getElement = function(){};")
        .addSource(
            "/** @type {I7} */", //
            "var i7;",
            "/** @type {C7} */",
            "var c7;",
            "",
            "i7 = c7;")
        .includeDefaultExterns()
        .run();
  }

  /**
   * structural interface matching will also be able to structurally match ordinary function types
   * check if the return types of the ordinary function types match (should not match)
   */
  @Test
  public void testStructuralInterfaceMatching23() {
    // the type structure of I5 and C5 are different
    newTest()
        .addExterns(
            EXTERNS_FOR_LONG_NONMATCHING_CHAIN,
            "/** @record */",
            "function I7() {}",
            "/** @type{function(): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(): C5} */",
            "C7.prototype.getElement = function(){};")
        .addSource(
            "/** @type {I7} */", //
            "var i7;",
            "/** @type {C7} */",
            "var c7;",
            "",
            "i7 = c7;")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : (C7|null)",
                "required: (I7|null)"))
        .includeDefaultExterns()
        .run();
  }

  /**
   * structural interface matching will also be able to structurally match ordinary function types
   * check if the parameter types of the ordinary function types match (should match, since declared
   * with @record)
   */
  @Test
  public void testStructuralInterfaceMatching24_1() {
    // I5 and C5 shares the same type structure
    newTest()
        .addExterns(
            EXTERNS_FOR_LONG_MATCHING_CHAIN_RECORD,
            "/** @record */",
            "function I7() {}",
            "/** @type{function(C5): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(I5): C5} */",
            "C7.prototype.getElement = function(){};")
        .addSource(
            "/** @type {I7} */", //
            "var i7;",
            "/** @type {C7} */",
            "var c7;",
            "",
            "i7 = c7;")
        .includeDefaultExterns()
        .run();
  }

  /**
   * structural interface matching will also be able to structurally match ordinary function types
   * check if the parameter types of the ordinary function types match (should match, since declared
   * with @record)
   */
  @Test
  public void testStructuralInterfaceMatching26_1() {
    // I5 and C5 shares the same type structure
    newTest()
        .addExterns(
            EXTERNS_FOR_LONG_MATCHING_CHAIN_RECORD,
            "/** @record */",
            "function I7() {}",
            "/** @type{function(C5, C5, I5): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(I5, C5): C5} */",
            "C7.prototype.getElement = function(){};")
        .addSource(
            "/** @type {I7} */", //
            "var i7;",
            "/** @type {C7} */",
            "var c7;",
            "",
            "i7 = c7;")
        .includeDefaultExterns()
        .run();
  }

  /**
   * structural interface matching will also be able to structurally match ordinary function types
   * check if the parameter types of the ordinary function types match (should match)
   */
  @Test
  public void testStructuralInterfaceMatching29_1() {
    // I5 and C5 shares the same type structure
    newTest()
        .addExterns(
            EXTERNS_FOR_LONG_MATCHING_CHAIN_RECORD,
            "/** @record */",
            "function I7() {}",
            "/** @type{function(C5, C5, I5=): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(I5, C5=, I5=): C5} */",
            "C7.prototype.getElement = function(){};")
        .addSource(
            "/** @type {I7} */", //
            "var i7;",
            "/** @type {C7} */",
            "var c7;",
            "",
            "i7 = c7;")
        .includeDefaultExterns()
        .run();
  }

  /** the "this" of I5 and C5 are covariants, so should match */
  @Test
  public void testStructuralInterfaceMatching30_1_1() {
    // I5 and C5 shares the same type structure
    newTest()
        .addExterns(
            EXTERNS_FOR_LONG_MATCHING_CHAIN_RECORD,
            "/** @record */",
            "function I7() {}",
            "/** @type{function(this:I5, C5, C5, I5=): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(this:C5, I5, C5=, I5=): C5} */",
            "C7.prototype.getElement = function(){};")
        .addSource(
            "/** @type {I7} */", //
            "var i7;",
            "/** @type {C7} */",
            "var c7;",
            "",
            "i7 = c7;")
        .includeDefaultExterns()
        .run();
  }

  /** the "this" of I5 and C5 are covariants, so should match */
  @Test
  public void testStructuralInterfaceMatching30_2_1() {
    // I5 and C5 shares the same type structure
    newTest()
        .addExterns(
            EXTERNS_FOR_LONG_MATCHING_CHAIN_RECORD,
            "/** @record */",
            "function I7() {}",
            "/** @type{function(this:C5, C5, C5, I5=): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(this:I5, I5, C5=, I5=): C5} */",
            "C7.prototype.getElement = function(){};")
        .addSource(
            "/** @type {I7} */", //
            "var i7;",
            "/** @type {C7} */",
            "var c7;",
            "",
            "i7 = c7;")
        .includeDefaultExterns()
        .run();
  }

  /** the "this" of I5 and C5 are covariants, so should match */
  @Test
  public void testStructuralInterfaceMatching30_3_1() {
    newTest()
        .addExterns(
            "/** @record */ function I5() {}",
            "/** @constructor @implements {I5} */ function C5() {}",
            "/** @record */",
            "function I7() {}",
            "/** @type{function(this:C5, C5, C5, I5=): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(this:I5, I5, C5=, I5=): C5} */",
            "C7.prototype.getElement = function(){};")
        .addSource(
            "/** @type {I7} */", //
            "var i7;",
            "/** @type {C7} */",
            "var c7;",
            "",
            "i7 = c7;")
        .includeDefaultExterns()
        .run();
  }

  /** I7 is declared with @record tag, so it will match */
  @Test
  public void testStructuralInterfaceMatching30_3_2() {
    newTest()
        .addExterns(
            "/** @interface */ function I5() {}",
            "/** @constructor @implements {I5} */ function C5() {}",
            "/** @record */",
            "function I7() {}",
            "/** @type{function(this:C5, C5, C5, I5=): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(this:I5, I5, C5=, I5=): C5} */",
            "C7.prototype.getElement = function(){};")
        .addSource(
            "/** @type {I7} */", //
            "var i7;",
            "/** @type {C7} */",
            "var c7;",
            "",
            "i7 = c7;")
        .includeDefaultExterns()
        .run();
  }

  /**
   * Although I7 is declared with @record tag, note that I5 is declared with @interface and C5 does
   * not extend I5, so it will not match
   */
  @Test
  public void testStructuralInterfaceMatching30_3_3() {
    newTest()
        .addExterns(
            "/** @interface */ function I5() {}",
            "/** @constructor */ function C5() {}",
            "/** @record */",
            "function I7() {}",
            "/** @type{function(this:C5, C5, C5, I5=): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(this:I5, I5, C5=, I5=): C5} */",
            "C7.prototype.getElement = function(){};")
        .addSource(
            "/** @type {I7} */", //
            "var i7;",
            "/** @type {C7} */",
            "var c7;",
            "",
            "i7 = c7;")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : (C7|null)",
                "required: (I7|null)"))
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching30_3_4() {
    // I5 and C5 shares the same type structure
    newTest()
        .addExterns(
            "/** @record */ function I5() {}",
            "/** @constructor */ function C5() {}",
            "/** @record */",
            "function I7() {}",
            "/** @type{function(this:C5, C5, C5, I5=): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(this:I5, I5, C5=, I5=): C5} */",
            "C7.prototype.getElement = function(){};")
        .addSource(
            "/** @type {I7} */", //
            "var i7;",
            "/** @type {C7} */",
            "var c7;",
            "",
            "i7 = c7;")
        .includeDefaultExterns()
        .run();
  }

  /** the "this" of I5 and C5 are covariants, so should match */
  @Test
  public void testStructuralInterfaceMatching30_4_1() {
    // I5 and C5 shares the same type structure
    newTest()
        .addExterns(
            "/** @record */ function I5() {}",
            "/** @constructor @implements {I5} */ function C5() {}",
            "/** @record */",
            "function I7() {}",
            "/** @type{function(this:I5, C5, C5, I5=): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(this:C5, I5, C5=, I5=): C5} */",
            "C7.prototype.getElement = function(){};")
        .addSource(
            "/** @type {I7} */", //
            "var i7;",
            "/** @type {C7} */",
            "var c7;",
            "",
            "i7 = c7;")
        .includeDefaultExterns()
        .run();
  }

  /**
   * although I7 is declared with @record tag I5 is declared with @interface tag, so no structural
   * interface matching
   */
  @Test
  public void testStructuralInterfaceMatching30_4_2() {
    newTest()
        .addExterns(
            "/** @interface */ function I5() {}",
            "/** @constructor */ function C5() {}",
            "/** @record */",
            "function I7() {}",
            "/** @type{function(this:I5, C5, C5, I5=): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(this:C5, I5, C5=, I5=): C5} */",
            "C7.prototype.getElement = function(){};")
        .addSource(
            "/** @type {I7} */", //
            "var i7;",
            "/** @type {C7} */",
            "var c7;",
            "",
            "i7 = c7;")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : (C7|null)",
                "required: (I7|null)"))
        .run();
  }

  /**
   * structural interface matching will also be able to structurally match ordinary function types
   * check if the this types of the ordinary function types match (should match)
   */
  @Test
  public void testStructuralInterfaceMatching31_1() {
    // I5 and C5 shares the same type structure
    newTest()
        .addExterns(
            EXTERNS_FOR_LONG_MATCHING_CHAIN_RECORD,
            "/** @record */",
            "function I7() {}",
            "/** @type{function(this:C5, C5, C5, I5=): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(this:I5, I5, C5=, I5=): C5} */",
            "C7.prototype.getElement = function(){};")
        .addSource(
            "/** @type {I7} */", //
            "var i7;",
            "/** @type {C7} */",
            "var c7;",
            "",
            "i7 = c7;")
        .includeDefaultExterns()
        .run();
  }

  /** test structural interface matching for record types */
  @Test
  public void testStructuralInterfaceMatching32_2() {
    // I5 and C5 shares the same type structure
    newTest()
        .addExterns(
            EXTERNS_FOR_LONG_MATCHING_CHAIN_RECORD,
            "/** @record */",
            "function I7() {}",
            "/** @type{function(this:C5, C5, C5, I5=): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(this:I5, I5, C5=, I5=): C5} */",
            "C7.prototype.getElement = function(){};")
        .addSource(
            "/** @type {{prop: I7, prop2: C7}}*/",
            "var r1;",
            "/** @type {{prop: C7, prop2: C7}} */",
            "var r2;",
            "r1 = r2;")
        .includeDefaultExterns()
        .run();
  }

  /** test structural interface matching for record types */
  @Test
  public void testStructuralInterfaceMatching33_3() {
    // I5 and C5 shares the same type structure
    newTest()
        .addExterns(
            EXTERNS_FOR_LONG_MATCHING_CHAIN_RECORD,
            "/** @record */",
            "function I7() {}",
            "/** @type{function(this:C5, C5, C5, I5=): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(this:I5, I5, C5=, I5=): C5} */",
            "C7.prototype.getElement = function(){};")
        .addSource(
            "/** @type {{prop: I7, prop2: C7}}*/",
            "var r1;",
            "/** @type {{prop: C7, prop2: C7, prop3: C7}} */",
            "var r2;",
            "r1 = r2;")
        .includeDefaultExterns()
        .run();
  }

  /**
   * test structural interface matching for a combination of ordinary function types and record
   * types
   */
  @Test
  public void testStructuralInterfaceMatching36_2() {
    // I5 and C5 shares the same type structure
    newTest()
        .addExterns(
            EXTERNS_FOR_LONG_MATCHING_CHAIN_RECORD,
            "/** @record */",
            "function I7() {}",
            "/** @type{function(this:C5, C5, C5, I5=): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(this:I5, I5, C5=, I5=): C5} */",
            "C7.prototype.getElement = function(){};")
        .addSource(
            "/** @type {{fun: function(C7):I7, prop: {prop: I7}}} */",
            " var com1;",
            "/** @type {{fun: function(I7):C7, prop: {prop: C7}}} */",
            "var com2;",
            "",
            "com1 = com2;")
        .includeDefaultExterns()
        .run();
  }

  /**
   * test structural interface matching for a combination of ordinary function types and record
   * types
   */
  @Test
  public void testStructuralInterfaceMatching36_3() {
    // I5 and C5 shares the same type structure
    newTest()
        .addExterns(
            EXTERNS_FOR_LONG_MATCHING_CHAIN_RECORD,
            "/** @record */",
            "function I7() {}",
            "/** @type{function(this:C5, C5, C5, I5=): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(this:I5, I5, C5=, I5=): C5} */",
            "C7.prototype.getElement = function(){};")
        .addSource(
            "/** @type {{fun: function(C7):I7, prop: {prop: I7}}} */",
            " var com1;",
            "/** @type {{fun: function(I7):C7, prop: {prop: C7}}} */",
            "var com2;",
            "",
            "com1 = com2;")
        .includeDefaultExterns()
        .run();
  }

  /**
   * test structural interface matching for a combination of ordinary function types and record
   * types here C7 does not structurally match I7
   */
  @Test
  public void testStructuralInterfaceMatching37() {
    // the type structure of I5 and C5 are different
    newTest()
        .addExterns(
            EXTERNS_FOR_LONG_NONMATCHING_CHAIN,
            "/** @record */",
            "function I7() {}",
            "/** @type{function(this:C5, C5, C5, I5=): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(this:I5, I5, C5=, I5=): C5} */",
            "C7.prototype.getElement = function(){};")
        .addSource(
            "/** @type {{fun: function(C7):I7, prop: {prop: I7}}} */",
            "var com1;",
            "/** @type {{fun: function(I7):C7, prop: {prop: C7}}} */",
            "var com2;",
            "",
            "com1 = com2;")
        .addDiagnostic(
            lines(
                "assignment",
                "found   : {\n  fun: function((I7|null)): (C7|null),\n  prop: {prop: (C7|null)}\n}",
                "required: {\n  fun: function((C7|null)): (I7|null),\n  prop: {prop: (I7|null)}\n}",
                "missing : []",
                "mismatch: [fun,prop]"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching_forObjectLiterals_39() {
    newTest()
        .addExterns(
            "/** @record */", //
            "function I2() {}",
            "/** @type {number} */",
            "I2.prototype.length;")
        .addSource(
            "/** @type {I2} */", //
            "var o1 = {length : 'test'};")
        .addDiagnostic(
            lines(
                "initializing variable",
                "found   : {length: string}",
                "required: (I2|null)",
                "missing : []",
                "mismatch: [length]"))
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching_forObjectLiterals_prototypeProp_matching() {
    newTest()
        .addExterns(
            "/** @record */", //
            "function I2() {}",
            "/** @type {number} */",
            "I2.prototype.length;")
        .addSource(
            "/** @type {I2} */", //
            "var o1 = {length : 123};")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching_forObjectLiterals_instanceProp_matching() {
    newTest()
        .addExterns(
            "/** @record */", //
            "function I2() {",
            "  /** @type {number} */",
            "  this.length;",
            "}")
        .addSource(
            "/** @type {!I2} */", //
            "var o1 = {length : 123};")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching_forObjectLiterals_prototypeProp_missing() {
    newTest()
        .addExterns(
            "/** @record */", //
            "function I2() {}",
            "/** @type {number} */",
            "I2.prototype.length;")
        .addSource(
            "/** @type {!I2} */", //
            "var o1 = {};")
        .addDiagnostic(
            lines(
                "initializing variable",
                "found   : {}",
                "required: I2",
                "missing : [length]",
                "mismatch: []"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching_forObjectLiterals_instanceProp_missing() {
    newTest()
        .addExterns(
            "/** @record */", //
            "function I2() {",
            "  /** @type {number} */",
            "  this.length;",
            "}")
        .addSource(
            "/** @type {!I2} */", //
            "var o1 = {};")
        .addDiagnostic(
            lines(
                "initializing variable",
                "found   : {}",
                "required: I2",
                "missing : [length]",
                "mismatch: []"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching_forObjectLiterals_prototypeProp_mismatch() {
    newTest()
        .addExterns(
            "/** @record */", //
            "function I2() {}",
            "/** @type {number} */",
            "I2.prototype.length;")
        .addSource(
            "/** @type {!I2} */", //
            "var o1 = {length: null};")
        .addDiagnostic(
            lines(
                "initializing variable",
                "found   : {length: null}",
                "required: I2",
                "missing : []",
                "mismatch: [length]"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching_forObjectLiterals_instanceProp_mismatch() {
    newTest()
        .addExterns(
            "/** @record */", //
            "function I2() {",
            "  /** @type {number} */",
            "  this.length;",
            "}")
        .addSource(
            "/** @type {!I2} */", //
            "var o1 = {length: null};")
        .addDiagnostic(
            lines(
                "initializing variable",
                "found   : {length: null}",
                "required: I2",
                "missing : []",
                "mismatch: [length]"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching_forObjectLiterals_41() {
    newTest()
        .addExterns(
            "/** @record */", //
            "function I2() {}",
            "/** @type {number} */",
            "I2.prototype.length;")
        .addSource(
            "/** @type {I2} */",
            "var o1 = {length : 123};",
            "/** @type {I2} */",
            "var i;",
            "i = o1;")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching41_forObjectLiterals_41_1() {
    newTest()
        .addExterns(
            "/** @record */", //
            "function I2() {}",
            "/** @type {number} */",
            "I2.prototype.length;")
        .addSource(
            "/** @type {I2} */",
            "var o1 = {length : 123};",
            "/** @type {I2} */",
            "var i;",
            "i = o1;")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching_forObjectLiterals_42() {
    newTest()
        .addExterns(
            "/** @record */", //
            "function I2() {}",
            "/** @type {number} */",
            "I2.prototype.length;")
        .addSource(
            "/** @type {{length: number}} */",
            "var o1 = {length : 123};",
            "/** @type {I2} */",
            "var i;",
            "i = o1;")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching_forObjectLiterals_43() {
    newTest()
        .addExterns(
            "/** @record */", //
            "function I2() {}",
            "/** @type {number} */",
            "I2.prototype.length;")
        .addSource(
            "var o1 = {length : 123};", //
            "/** @type {I2} */",
            "var i;",
            "i = o1;")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching44() {
    newTest()
        .addExterns(
            "/** @record */ function I() {}",
            "/** @type {!Function} */ I.prototype.removeEventListener;",
            "/** @type {!Function} */ I.prototype.addEventListener;",
            "/** @constructor */ function C() {}",
            "/** @type {!Function} */ C.prototype.addEventListener;")
        .addSource(
            "/** @param {C|I} x */", //
            "function f(x) { x.addEventListener(); }",
            "f(new C());")
        .includeDefaultExterns()
        .run();
  }

  /**
   * Currently, the structural interface matching does not support structural matching for template
   * types Using @template @interfaces requires @implements them explicitly.
   */
  @Test
  public void testStructuralInterfaceMatching45() {
    newTest()
        .addSource(
            "/**",
            " * @record",
            " * @template X",
            " */",
            "function I() {}",
            "/** @constructor */",
            "function C() {}",
            "var /** !I */ i = new C;")
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching46() {
    newTest()
        .addSource(
            "/** @interface */",
            "function I2() {}",
            "/**",
            " * @interface",
            " * @extends {I2}",
            " */",
            "function I3() {}",
            "/**",
            " * @record",
            " * @extends {I3}",
            " */",
            "function I4() {}",
            "/** @type {I4} */",
            "var i4;",
            "/** @type {I2} */",
            "var i2;",
            "i4 = i2;")
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching47() {
    newTest()
        .addExterns(
            "/** @interface */",
            "function I2() {}",
            "/**",
            " * @interface",
            " * @extends {I2}",
            " */",
            "function I3() {}",
            "/**",
            " * @record",
            " * @extends {I3}",
            " */",
            "function I4() {}")
        .addSource(
            "/** @type {I4} */", //
            "var i4;",
            "/** @type {I2} */",
            "var i2;",
            "i4 = i2;")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching48() {
    newTest()
        .addExterns("")
        .addSource(
            "/** @interface */",
            "function I2() {}",
            "/**",
            " * @record",
            " * @extends {I2}",
            " */",
            "function I3() {}",
            "/** @type {I3} */",
            "var i3;",
            "/** @type {I2} */",
            "var i2;",
            "i3 = i2;")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching49() {
    newTest()
        .addExterns(
            "/** @interface */",
            "function I2() {}",
            "/**",
            " * @record",
            " * @extends {I2}",
            " */",
            "function I3() {}")
        .addSource(
            "/** @type {I3} */", //
            "var i3;",
            "/** @type {I2} */",
            "var i2;",
            "i3 = i2;")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching49_2() {
    newTest()
        .addExterns(
            "/** @record */",
            "function I2() {}",
            "/**",
            " * @record",
            " * @extends {I2}",
            " */",
            "function I3() {}")
        .addSource(
            "/** @type {I3} */", //
            "var i3;",
            "/** @type {I2} */",
            "var i2;",
            "i3 = i2;")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching50() {
    newTest()
        .addExterns(
            "/** @interface */",
            "function I2() {}",
            "/**",
            " * @record",
            " * @extends {I2}",
            " */",
            "function I3() {}")
        .addSource(
            "/** @type {I3} */",
            "var i3;",
            "/** @type {{length : number}} */",
            "var r = {length: 123};",
            "i3 = r;")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testStructuralInterfaceMatching1_1() {
    newTest()
        .addExterns(
            "/** @interface */",
            "function Interface1() {}",
            "/** @type {number} */",
            "Interface1.prototype.length;",
            "",
            "/** @constructor */",
            "function C1() {}",
            "/** @type {number} */",
            "C1.prototype.length;")
        .addSource(
            "/** @type{Interface1} */",
            "var obj1;",
            "/** @type{C1} */",
            "var obj2 = new C1();",
            "obj1 = obj2;")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : (C1|null)",
                "required: (Interface1|null)"))
        .run();
  }

  /**
   * structural interface matching will also be able to structurally match ordinary function types
   * check if the return types of the ordinary function types match (should not match, since I7 is
   * declared with @interface)
   */
  @Test
  public void testStructuralInterfaceMatching22_2() {
    // I5 and C5 shares the same type structure
    newTest()
        .addExterns(
            EXTERNS_FOR_LONG_MATCHING_CHAIN_RECORD,
            "/** @interface */",
            "function I7() {}",
            "/** @type{function(): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(): C5} */",
            "C7.prototype.getElement = function(){};")
        .addSource(
            "/** @type {I7} */", //
            "var i7;",
            "/** @type {C7} */",
            "var c7;",
            "",
            "i7 = c7;")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : (C7|null)",
                "required: (I7|null)"))
        .includeDefaultExterns()
        .run();
  }

  /** declared with @interface, no structural interface matching */
  @Test
  public void testStructuralInterfaceMatching30_3() {
    // I5 and C5 shares the same type structure
    newTest()
        .addExterns(
            "/** @interface */ function I5() {}",
            "/** @constructor @implements {I5} */ function C5() {}",
            "/** @interface */",
            "function I7() {}",
            "/** @type{function(this:C5, C5, C5, I5=): I5} */",
            "I7.prototype.getElement = function(){};",
            "",
            "/** @constructor */",
            "function C7() {}",
            "/** @type{function(this:I5, I5, C5=, I5=): C5} */",
            "C7.prototype.getElement = function(){};")
        .addSource(
            "/** @type {I7} */", //
            "var i7;",
            "/** @type {C7} */",
            "var c7;",
            "",
            "i7 = c7;")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : (C7|null)",
                "required: (I7|null)"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testStructuralInterfaceWithOptionalProperty() {
    newTest()
        .addSource(
            "/** @record */ function Rec() {}",
            "/** @type {string} */ Rec.prototype.str;",
            "/** @type {(number|undefined)} */ Rec.prototype.opt_num;",
            "",
            "/** @constructor */ function Foo() {}",
            "Foo.prototype.str = 'foo';",
            "",
            "var /** !Rec */ x = new Foo;")
        .run();
  }

  @Test
  public void testStructuralInterfaceWithUnknownProperty() {
    newTest()
        .addSource(
            "/** @record */ function Rec() {}",
            "/** @type {string} */ Rec.prototype.str;",
            "/** @type {?} */ Rec.prototype.unknown;",
            "",
            "/** @constructor */ function Foo() {}",
            "Foo.prototype.str = 'foo';",
            "",
            "var /** !Rec */ x = new Foo;")
        .addDiagnostic(
            lines(
                "initializing variable",
                "found   : Foo",
                "required: Rec",
                "missing : [unknown]",
                "mismatch: []"))
        .run();
  }

  @Test
  public void testStructuralInterfaceWithOptionalUnknownProperty() {
    newTest()
        .addSource(
            "/** @record */ function Rec() {}",
            "/** @type {string} */ Rec.prototype.str;",
            "/** @type {?|undefined} */ Rec.prototype.opt_unknown;",
            "",
            "/** @constructor */ function Foo() {}",
            "Foo.prototype.str = 'foo';",
            "",
            "var /** !Rec */ x = new Foo;")
        .run();
  }

  @Test
  public void testStructuralInterfaceWithTopProperty() {
    newTest()
        .addSource(
            "/** @record */ function Rec() {}",
            "/** @type {string} */ Rec.prototype.str;",
            "/** @type {*} */ Rec.prototype.top;",
            "",
            "/** @constructor */ function Foo() {}",
            "Foo.prototype.str = 'foo';",
            "",
            "var /** !Rec */ x = new Foo;")
        .addDiagnostic(
            lines(
                "initializing variable",
                "found   : Foo",
                "required: Rec",
                "missing : [top]",
                "mismatch: []"))
        .run();
  }

  @Test
  public void testStructuralInterfaceCycleDoesntCrash() {
    newTest()
        .addSource(
            "/**  @record */ function Foo() {};",
            "/**  @return {MutableFoo} */ Foo.prototype.toMutable;",
            "/**  @record */ function MutableFoo() {};",
            "/**  @param {Foo} from */ MutableFoo.prototype.copyFrom;",
            "",
            "/**  @record */ function Bar() {};",
            "/**  @return {MutableBar} */ Bar.prototype.toMutable;",
            "/**  @record */ function MutableBar() {};",
            "/**  @param {Bar} from */ MutableBar.prototype.copyFrom;",
            "",
            "/** @constructor @implements {MutableBar} */ function MutableBarImpl() {};",
            "/** @override */ MutableBarImpl.prototype.copyFrom = function(from) {};",
            "/** @constructor  @implements {MutableFoo} */ function MutableFooImpl() {};",
            "/** @override */ MutableFooImpl.prototype.copyFrom = function(from) {};")
        .run();
  }

  @Test
  public void testStructuralInterfacesMatchOwnProperties1() {
    newTest()
        .addSource(
            "/** @record */ function WithProp() {}",
            "/** @type {number} */ WithProp.prototype.prop;",
            "",
            "/** @constructor */",
            "function Foo() {",
            "  /** @type {number} */ this.prop = 5;",
            "}",
            "var /** !WithProp */ wp = new Foo;")
        .run();
  }

  @Test
  public void testStructuralInterfacesMatchOwnProperties2() {
    newTest()
        .addSource(
            "/** @record */ function WithProp() {}",
            "/** @type {number} */ WithProp.prototype.prop;",
            "",
            "/** @constructor */",
            "function Foo() {",
            "  /** @type {number} */ this.oops = 5;",
            "}",
            "var /** !WithProp */ wp = new Foo;")
        .addDiagnostic(
            lines(
                "initializing variable",
                "found   : Foo",
                "required: WithProp",
                "missing : [prop]",
                "mismatch: []"))
        .run();
  }

  @Test
  public void testStructuralInterfacesMatchOwnProperties3() {
    newTest()
        .addSource(
            "/** @record */ function WithProp() {}",
            "/** @type {number} */ WithProp.prototype.prop;",
            "",
            "/** @constructor */",
            "function Foo() {",
            "  /** @type {string} */ this.prop = 'str';",
            "}",
            "var /** !WithProp */ wp = new Foo;")
        .addDiagnostic(
            lines(
                "initializing variable",
                "found   : Foo",
                "required: WithProp",
                "missing : []",
                "mismatch: [prop]"))
        .run();
  }

  @Test
  public void testStructuralInterfacesMatchFunctionNamespace1() {
    newTest()
        .addSource(
            "/** @record */ function WithProp() {}",
            "/** @type {number} */ WithProp.prototype.prop;",
            "",
            "var ns = function() {};",
            "/** @type {number} */ ns.prop;",
            "var /** !WithProp */ wp = ns;")
        .run();
  }

  @Test
  public void testStructuralInterfacesMatchFunctionNamespace2() {
    newTest()
        .addSource(
            "/** @record */ function WithProp() {}",
            "/** @type {number} */ WithProp.prototype.prop;",
            "",
            "var ns = function() {};",
            "/** @type {number} */ ns.oops;",
            "var /** !WithProp */ wp = ns;")
        .addDiagnostic(
            lines(
                "initializing variable",
                "found   : function(): undefined",
                "required: WithProp",
                "missing : [prop]",
                "mismatch: []"))
        .run();
  }

  @Test
  public void testStructuralInterfacesMatchFunctionNamespace3() {
    newTest()
        .addSource(
            "/** @record */ function WithProp() {}",
            "/** @type {number} */ WithProp.prototype.prop;",
            "",
            "var ns = function() {};",
            "/** @type {string} */ ns.prop;",
            "var /** !WithProp */ wp = ns;")
        .addDiagnostic(
            lines(
                "initializing variable",
                "found   : function(): undefined",
                "required: WithProp",
                "missing : []",
                "mismatch: [prop]"))
        .run();
  }

  @Test
  public void testRecursiveTemplatizedStructuralInterface() {
    newTest()
        .addSource(
            "/**",
            " * @record",
            " * @template T",
            " */",
            "var Rec = function() { };",
            "/** @type {!Rec<T>} */",
            "Rec.prototype.p;",
            "",
            "/**",
            " * @constructor @implements {Rec<U>}",
            " * @template U",
            " */",
            "var Foo = function() {};",
            "/** @override */",
            "Foo.prototype.p = new Foo;")
        .run();
  }
}
