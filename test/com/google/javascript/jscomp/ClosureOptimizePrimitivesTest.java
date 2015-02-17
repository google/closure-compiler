/*
 * Copyright 2011 The Closure Compiler Authors.
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
 * Tests for {@link ClosureOptimizePrimitives}.
 *
 * @author agrieve@google.com (Andrew Grieve)
 */
public class ClosureOptimizePrimitivesTest extends CompilerTestCase {

  @Override public CompilerPass getProcessor(final Compiler compiler) {
    return new ClosureOptimizePrimitives(compiler);
  }

  public void testObjectCreateNonConstKey() {
    testSame("goog.object.create('a',1,2,3,foo,bar);");
  }

  public void testObjectCreateOddParams() {
    testSame("goog.object.create('a',1,2);");
  }

  public void testObjectCreate1() {
    test("var a = goog.object.create()", "var a = {}");
  }

  public void testObjectCreate2() {
    test("var a = goog$object$create('b',goog$object$create('c','d'))",
         "var a = {'b':{'c':'d'}};");
  }

  public void testObjectCreate3() {
    test("var a = goog.object.create(1,2)", "var a = {1:2}");
  }

  public void testObjectCreate4() {
    test("alert(goog.object.create(1,2).toString())",
         "alert({1:2}.toString())");
  }

  public void testObjectCreate5() {
    test("goog.object.create('a',2).toString()", "({'a':2}).toString()");
  }

  public void testObjectCreateSetNonConstKey() {
    testSame("goog.object.createSet('a',1,2,3,foo,bar);");
  }

  public void testObjectCreateSet1() {
    test("var a = goog.object.createSet()", "var a = {}");
  }

  public void testObjectCreateSet2() {
    test("var a = goog.object.createSet(1,2)", "var a = {1:true,2:true}");
  }

  public void testObjectCreateSet3() {
    test("alert(goog.object.createSet(1).toString())",
         "alert({1:true}.toString())");
  }

  public void testObjectCreateSet4() {
    test("goog.object.createSet('a').toString()", "({'a':true}).toString()");
  }
}
