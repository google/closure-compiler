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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class PeepholeCollectPropertyAssignmentsTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new PeepholeOptimizationsPass(
        compiler, getName(), new PeepholeCollectPropertyAssignments());
  }

  @Test
  public void test36122565a() {
    testSame(
        lines(
            "var foo = { bar: g(), baz: 4 };",
            "foo.bar = 3;",
            "foo.baz = 3;",
            "console.log(foo.bar);",
            "console.log(foo.baz);"));

    test(
        lines(
            "var foo = { bar: g(), baz: 4 };",
            "foo.baz = 3;",
            "foo.bar = 3;",
            "console.log(foo.bar);",
            "console.log(foo.baz);"),
        lines(
            "var foo = { bar: g(), baz: 3 };",
            "foo.bar = 3;",
            "console.log(foo.bar);",
            "console.log(foo.baz);"));
  }

  @Test
  public void testArrayOptimization1() {
    test("var a = []; a[0] = 1; a[1] = 2; a[2] = 3;",
         "var a = [1, 2, 3];");
  }

  @Test
  public void testArrayOptimization2() {
    test("var a; a = []; a[0] = 1; a[1] = 2; a[2] = 3;",
         "var a; a = [1, 2, 3];");
  }

  @Test
  public void testArrayOptimization3() {
    testSame("var a; a.b = []; a.b[0] = 1; a.b[1] = 2; a.b[2] = 3;");
  }

  @Test
  public void testArrayOptimizationWithLet() {
    test("let a = []; a[0] = 1; a[1] = 2; a[2] = 3;", "let a = [1, 2, 3];");
  }

  @Test
  public void testArrayOptimizationWithConst() {
    test("const a = []; a[0] = 1; a[1] = 2; a[2] = 3;", "const a = [1, 2, 3];");
  }

  @Test
  public void testCompoundAssignment() {
    testSame("var x, a; a = []; a[0] *= x;");
  }

  @Test
  public void testNegativeArrayIndex1() {
    testSame("var a = []; a[-1] = 1;");
  }

  @Test
  public void testNegativeArrayIndex2() {
    testSame("var a; a = []; a[-1] = 1;");
  }

  @Test
  public void testFractionalArrayIndex1() {
    testSame("var a = []; a[0.5] = 1;");
  }

  @Test
  public void testFractionalArrayIndex2() {
    testSame("var a; a = []; a[0.5] = 1;");
  }

  @Test
  public void testArrayOptimizationOfPartiallyBuiltArray1() {
    test("var a = [1, 2]; a[2] = 3;",
         "var a = [1, 2, 3];");
  }

  @Test
  public void testArrayOptimizationOfPartiallyBuiltArray2() {
    test("var a; a = [1, 2]; a[2] = 3;",
         "var a; a = [1, 2, 3];");
  }

  @Test
  public void testArrayOptimizationWithAHole1() {
    test("var a = []; a[0] = 1; a[1] = 2; a[3] = 4;",
         "var a = [1, 2, , 4];");
  }

  @Test
  public void testArrayOptimizationWithAHole2() {
    test("var a; a = []; a[0] = 1; a[1] = 2; a[3] = 4;",
         "var a; a = [1, 2, , 4];");
  }

  @Test
  public void testEarlyUsage1() {
    testSame(
        "function c() {return sum(a)};"
        + "var a = [1,2,3];"
        + "a[4] = c();");
  }

  @Test
  public void testEarlyUsage2() {
    testSame(
        "function c() {return sum(a)};"
        + "var a; a = [1,2,3];"
        + "a[4] = c();");
  }

  @Test
  public void testArrayTooSparseOptimization1() {
    test("var a = []; a[0] = 1; a[1] = 2; a[100] = 4;",
         "var a = [1, 2]; a[100] = 4;");
  }

  @Test
  public void testArrayTooSparseOptimization2() {
    test("var a; a = []; a[0] = 1; a[1] = 2; a[100] = 4;",
         "var a; a = [1, 2]; a[100] = 4;");
  }

  @Test
  public void testArrayOutOfOrder() {
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

  @Test
  public void testMultipleNames1() {
    test("var b = []; b[0] = 2; var a = []; a[0] = 1;",
         "var b = [2]; var a = [1];");
  }

  @Test
  public void testMultipleNames2() {
    test("var b; b = []; b[0] = 2; var a = []; a[0] = 1;",
         "var b; b = [2]; var a = [1];");
  }

  @Test
  public void testArrayReassignedInValue1() {
    test("var a = []; a[0] = 1; a[1] = (a = []); a[3] = 4;",
         "var a = [1]; a[1] = (a = []); a[3] = 4;");
  }

  @Test
  public void testArrayReassignedInValue2() {
    test("var a; a = []; a[0] = 1; a[1] = (a = []); a[3] = 4;",
         "var a; a = [1]; a[1] = (a = []); a[3] = 4;");
  }

  @Test
  public void testArrayReassignedInSubsequentVar1() {
    testSame("var a = []; a[0] = a = []; a[1] = 2;");
  }

  @Test
  public void testArrayReassignedInSubsequentVar2() {
    testSame("var a; a = []; a[0] = a = []; a[1] = 2;");
  }

  @Test
  public void testForwardReference1() {
    test("var a; a = []; a[0] = 1; a[1] = a;",
         "var a; a = [1]; a[1] = a;");
  }

  @Test
  public void testForwardReference2() {
    test("var a; a = []; a[0] = 1; a[1] = a;",
         "var a; a = [1]; a[1] = a;");
  }

  @Test
  public void testObjectOptimization1() {
    test("var o = {}; o.x = 0; o['y'] = 1; o[2] = 2;",
         "var o = { x: 0, \"y\": 1, \"2\": 2 };");
  }

  @Test
  public void testObjectOptimization2() {
    test("var o; o = {}; o.x = 0; o['y'] = 1; o[2] = 2;",
         "var o; o = { x: 0, \"y\": 1, \"2\": 2 };");
  }

  @Test
  public void testObjectOptimizationWithLet() {
    test("let o = {}; o.x = 0; o['y'] = 1; o[2] = 2;", "let o = { x: 0, 'y': 1, '2': 2 };");
  }

  @Test
  public void testObjectOptimizationWithConst() {
    test("const o = {}; o.x = 0; o['y'] = 1; o[2] = 2;", "const o = { x: 0, 'y': 1, '2': 2 };");
  }

  @Test
  public void testObjectReassignedInValue1() {
    test("var o = {}; o.x = 1; o.y = (o = {}); o.z = 4;",
         "var o = {x:1}; o.y = (o = {}); o.z = 4;");
  }

  @Test
  public void testObjectReassignedInValue2() {
    test("var o; o = {}; o.x = 1; o.y = (o = {}); o.z = 4;",
         "var o; o = {x:1}; o.y = (o = {}); o.z = 4;");
  }

  @Test
  public void testObjectFunctionRollup1() {
    test("var o; o = {};" +
         "o.x = function() {};",
         "var o; o = {x:function () {}};");
  }

  @Test
  public void testObjectFunctionRollup2() {
    testSame(
         "var o; o = {};" +
         "o.x = (function() {return o})();");
  }

  @Test
  public void testObjectFunctionRollup3() {
    test("var o; o = {};" +
         "o.x = function() {return o};",
         "var o; o = {x:function () {return o}};");
  }

  @Test
  public void testObjectFunctionRollup4() {
    testSame(
        "function f() {return o};" +
        "var o; o = {};" +
        "o.x = f();");
  }

  @Test
  public void testObjectFunctionRollup5() {
    test("var o; o = {};" +
         "o.x = function() {return o};" +
         "o.y = [function() {return o}];" +
         "o.z = {a:function() {return o}};",

         "var o; o = {" +
         "x:function () {return o}, " +
         "y:[function () {return o}], " +
         "z:{a:function () {return o}}};");
  }

  @Test
  public void testObjectPropertyReassigned() {
    test("var a = {b:''};" +
        "a.b='c';",
        "var a={b:'c'};");
  }

  @Test
  public void testObjectPropertyReassigned2() {
    test("var a = {b:'', x:10};" +
        "a.b='c';",
        "var a={x:10, b:'c'};");
  }

  @Test
  public void testObjectPropertyReassigned3() {
    test("var a = {x:10};" +
        "a.b = 'c';",
        "var a = {x:10, b:'c'};");
  }

  @Test
  public void testObjectPropertyReassigned4() {
    testSame(
        "var a = {b:10};" +
        "var x = 1;" +
        "a.b = x+10;");
  }

  @Test
  public void testObjectComputedProp1() {
    testSame(
        lines(
            "var a = {['computed']: 10};",
            "var alsoComputed = 'someValue';",
            "a[alsoComputed] = 20;"));
  }

  @Test
  public void testObjectComputedProp2() {
    test(
        lines(
            "var a = {['computed']: 10};",
            "a.prop = 20;"),
        lines(
            "var a = {",
            "  ['computed']: 10,",
            "  prop: 20,",
            "};"));
  }

  @Test
  public void testObjectMemberFunction1() {
    test(
        lines(
            "var a = { member() {} };",
            "a.prop = 20;"),
        lines(
            "var a = {",
            "  member() {},",
            "  prop: 20,",
            "};"));
  }

  @Test
  public void testObjectMemberFunction2() {
    test(
        lines(
            "var a = { member() {} };",
            "a.member = 20;"),
        lines(
            "var a = {",
            "  member: 20,",
            "};"));
  }

  @Test
  public void testObjectGetter() {
    testSame(
        lines(
            "var a = { get x() {} };",
            "a.x = 20;"));
  }

  @Test
  public void testObjectSetter() {
    testSame(
        lines(
            "var a = { set x(value) {} };",
            "a.x = 20;"));
  }
}
