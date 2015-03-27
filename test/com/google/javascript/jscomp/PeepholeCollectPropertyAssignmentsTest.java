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

public final class PeepholeCollectPropertyAssignmentsTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new PeepholeOptimizationsPass(
        compiler, new PeepholeCollectPropertyAssignments());
  }

  public final void testArrayOptimization1() {
    test("var a = []; a[0] = 1; a[1] = 2; a[2] = 3;",
         "var a = [1, 2, 3];");
  }

  public final void testArrayOptimization2() {
    test("var a; a = []; a[0] = 1; a[1] = 2; a[2] = 3;",
         "var a; a = [1, 2, 3];");
  }

  public final void testArrayOptimization3() {
    testSame("var a; a.b = []; a.b[0] = 1; a.b[1] = 2; a.b[2] = 3;");
  }

  public final void testCompoundAssignment() {
    testSame("var x, a; a = []; a[0] *= x;");
  }

  public final void testNegativeArrayIndex1() {
    testSame("var a = []; a[-1] = 1;");
  }

  public final void testNegativeArrayIndex2() {
    testSame("var a; a = []; a[-1] = 1;");
  }

  public final void testFractionalArrayIndex1() {
    testSame("var a = []; a[0.5] = 1;");
  }

  public final void testFractionalArrayIndex2() {
    testSame("var a; a = []; a[0.5] = 1;");
  }

  public final void testArrayOptimizationOfPartiallyBuiltArray1() {
    test("var a = [1, 2]; a[2] = 3;",
         "var a = [1, 2, 3];");
  }

  public final void testArrayOptimizationOfPartiallyBuiltArray2() {
    test("var a; a = [1, 2]; a[2] = 3;",
         "var a; a = [1, 2, 3];");
  }

  public final void testArrayOptimizationWithAHole1() {
    test("var a = []; a[0] = 1; a[1] = 2; a[3] = 4;",
         "var a = [1, 2, , 4];");
  }

  public final void testArrayOptimizationWithAHole2() {
    test("var a; a = []; a[0] = 1; a[1] = 2; a[3] = 4;",
         "var a; a = [1, 2, , 4];");
  }

  public final void testEarlyUsage1() {
    testSame(
        "function c() {return sum(a)};"
        + "var a = [1,2,3];"
        + "a[4] = c();");
  }

  public final void testEarlyUsage2() {
    testSame(
        "function c() {return sum(a)};"
        + "var a; a = [1,2,3];"
        + "a[4] = c();");
  }

  public final void testArrayTooSparseOptimization1() {
    test("var a = []; a[0] = 1; a[1] = 2; a[100] = 4;",
         "var a = [1, 2]; a[100] = 4;");
  }

  public final void testArrayTooSparseOptimization2() {
    test("var a; a = []; a[0] = 1; a[1] = 2; a[100] = 4;",
         "var a; a = [1, 2]; a[100] = 4;");
  }

  public final void testArrayOutOfOrder() {
    test("var a = []; a[1] = 1; a[0] = 0;",
         "var a = [0, 1];");
    test("var a; a = []; a[1] = 1; a[0] = 0;",
         "var a; a = [0, 1];");
    // We cannot change the order of side-effects.
    // The below should not be
    //   var x = 0; var a = [x++, x++]
    // since that would produce
    //   var a = [0, 1], x = 2;
    // instead of
    //   var a = [1, 0], x = 2;
    testSame("var x = 0; var a = []; a[1] = x++; a[0] = x++;");
    testSame("var x; x = 0; var a = []; a[1] = x++; a[0] = x++;");
  }

  public final void testMultipleNames1() {
    test("var b = []; b[0] = 2; var a = []; a[0] = 1;",
         "var b = [2]; var a = [1];");
  }

  public final void testMultipleNames2() {
    test("var b; b = []; b[0] = 2; var a = []; a[0] = 1;",
         "var b; b = [2]; var a = [1];");
  }


  public final void testArrayReassignedInValue1() {
    test("var a = []; a[0] = 1; a[1] = (a = []); a[3] = 4;",
         "var a = [1]; a[1] = (a = []); a[3] = 4;");
  }

  public final void testArrayReassignedInValue2() {
    test("var a; a = []; a[0] = 1; a[1] = (a = []); a[3] = 4;",
         "var a; a = [1]; a[1] = (a = []); a[3] = 4;");
  }

  public final void testArrayReassignedInSubsequentVar1() {
    testSame("var a = []; a[0] = a = []; a[1] = 2;");
  }

  public final void testArrayReassignedInSubsequentVar2() {
    testSame("var a; a = []; a[0] = a = []; a[1] = 2;");
  }

  public final void testForwardReference1() {
    test("var a; a = []; a[0] = 1; a[1] = a;",
         "var a; a = [1]; a[1] = a;");
  }

  public final void testForwardReference2() {
    test("var a; a = []; a[0] = 1; a[1] = a;",
         "var a; a = [1]; a[1] = a;");
  }

  public final void testObjectOptimization1() {
    test("var o = {}; o.x = 0; o['y'] = 1; o[2] = 2;",
         "var o = { x: 0, \"y\": 1, \"2\": 2 };");
  }

  public final void testObjectOptimization2() {
    test("var o; o = {}; o.x = 0; o['y'] = 1; o[2] = 2;",
         "var o; o = { x: 0, \"y\": 1, \"2\": 2 };");
  }

  public final void testObjectReassignedInValue1() {
    test("var o = {}; o.x = 1; o.y = (o = {}); o.z = 4;",
         "var o = {x:1}; o.y = (o = {}); o.z = 4;");
  }


  public final void testObjectReassignedInValue2() {
    test("var o; o = {}; o.x = 1; o.y = (o = {}); o.z = 4;",
         "var o; o = {x:1}; o.y = (o = {}); o.z = 4;");
  }

  public final void testObjectFunctionRollup1() {
    test("var o; o = {};" +
         "o.x = function() {};",
         "var o; o = {x:function () {}};");
  }

  public final void testObjectFunctionRollup2() {
    testSame(
         "var o; o = {};" +
         "o.x = (function() {return o})();");
  }

  public final void testObjectFunctionRollup3() {
    test("var o; o = {};" +
         "o.x = function() {return o};",
         "var o; o = {x:function () {return o}};");
  }

  public final void testObjectFunctionRollup4() {
    testSame(
        "function f() {return o};" +
        "var o; o = {};" +
        "o.x = f();");
  }

  public final void testObjectFunctionRollup5() {
    test("var o; o = {};" +
         "o.x = function() {return o};" +
         "o.y = [function() {return o}];" +
         "o.z = {a:function() {return o}};",

         "var o; o = {" +
         "x:function () {return o}, " +
         "y:[function () {return o}], " +
         "z:{a:function () {return o}}};");
  }

  public final void testObjectPropertyReassigned(){
    test("var a = {b:''};" +
        "a.b='c';",
        "var a={b:'c'};");
  }

  public final void testObjectPropertyReassigned2(){
    test("var a = {b:'', x:10};" +
        "a.b='c';",
        "var a={x:10, b:'c'};");
  }

  public final void testObjectPropertyReassigned3(){
    test("var a = {x:10};" +
        "a.b = 'c';",
        "var a = {x:10, b:'c'};");
  }

  public final void testObjectPropertyReassigned4(){
    testSame(
        "var a = {b:10};" +
        "var x = 1;" +
        "a.b = x+10;");
  }
}
