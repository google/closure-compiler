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

public class PeepholeCollectPropertyAssignmentsTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new PeepholeOptimizationsPass(
        compiler, new PeepholeCollectPropertyAssignments());
  }

  public final void testArrayOptimization() {
    test("var a = []; a[0] = 1; a[1] = 2; a[2] = 3;",
         "var a = [1, 2, 3];");
  }

  public final void testNegativeArrayIndex() {
    testSame("var a = []; a[-1] = 1;");
  }

  public final void testFractionalArrayIndex() {
    testSame("var a = []; a[0.5] = 1;");
  }

  public final void testArrayOptimizationOfPartiallyBuiltArray() {
    test("var a = [1, 2]; a[2] = 3;",
         "var a = [1, 2, 3];");
  }

  public final void testArrayOptimizationWithAHole() {
    test("var a = []; a[0] = 1; a[1] = 2; a[3] = 4;",
         "var a = [1, 2, , 4];");
  }

  public final void testEarlyUsage() {
    testSame(
        "function c() {return sum(a)};"
        + "var a = [1,2,3];"
        + "a[4] = c();");
  }

  public final void testArrayTooSparseOptimization() {
    test("var a = []; a[0] = 1; a[1] = 2; a[100] = 4;",
         "var a = [1, 2]; a[100] = 4;");
  }

  public final void testArrayOutOfOrder() {
    test("var a = []; a[1] = 1; a[0] = 0;",
         "var a = [0, 1];");
    // We cannot change the order of side-effects.
    // The below should not be
    //   var x = 0; var a = [x++, x++]
    // since that would produce
    //   var a = [0, 1], x = 2;
    // instead of
    //   var a = [1, 0], x = 2;
    testSame("var x = 0; var a = []; a[1] = x++; a[0] = x++;");
  }

  public final void testMultipleNames() {
    test("var b = []; b[0] = 2; var a = []; a[0] = 1;",
         "var b = [2]; var a = [1];");
  }

  public final void testArrayReassignedInValue() {
    test("var a = []; a[0] = 1; a[1] = (a = []); a[3] = 4;",
         "var a = [1]; a[1] = (a = []); a[3] = 4;");
  }

  public final void testArrayReassignedInSubsequentVar() {
    testSame("var a = []; a[0] = a = []; a[1] = 2;");
  }

  public final void testForwardReference() {
    test("var a = []; a[0] = 1; a[1] = a;",
         "var a = [1]; a[1] = a;");
  }

  public final void testObjectOptimization() {
    test("var o = {}; o.x = 0; o['y'] = 1; o[2] = 2;",
         "var o = { x: 0, \"y\": 1, \"2\": 2 };");
  }

  public final void testObjectReassignedInValue() {
    test("var o = {}; o.x = 1; o.y = (o = {}); o.z = 4;",
         "var o = {x:1}; o.y = (o = {}); o.z = 4;");
  }

}
