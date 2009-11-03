/*
 * Copyright 2007 Google Inc.
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

import com.google.javascript.jscomp.CheckLevel;

/**
 * Tests {@link CheckGlobalThis}.
 */
public class CheckGlobalThisTest extends CompilerTestCase {
  public CheckGlobalThisTest() {
    this.parseTypeInfo = true;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CombinedCompilerPass(
        compiler, new CheckGlobalThis(compiler, CheckLevel.ERROR));
  }

  private void testFailure(String js) {
    test(js, null, CheckGlobalThis.GLOBAL_THIS);
  }
  
  public void testGlobalThis1() throws Exception {
    testSame("var a = this;");
  }

  public void testGlobalThis2() {
    testFailure("this.foo = 5;");
  }

  public void testGlobalThis3() {
    testFailure("this[foo] = 5;");
  }

  public void testGlobalThis4() {
    testFailure("this['foo'] = 5;");
  }
  
  public void testGlobalThis5() {
    testFailure("(a = this).foo = 4;");
  }
  
  public void testGlobalThis6() {
    testSame("a = this;");
  }

  public void testStaticFunction1() {
    testSame("function a() { return this; }");
  }

  public void testStaticFunction2() {
    testFailure("function a() { this.complex = 5; }");
  }

  public void testStaticFunction3() {
    testSame("var a = function() { return this; }");
  }

  public void testStaticFunction4() {
    testFailure("var a = function() { this.foo.bar = 6; }");
  }

  public void testStaticFunction5() {
    testSame("function a() { return function() { return this; } }");
  }

  public void testStaticFunction6() {
    testFailure("function a() { return function() { this = 8; } }");
  }

  public void testStaticFunction7() {
    testFailure("var a = function() { return function() { this = 8; } }");
  }

  public void testConstructor1() {
    testSame("/** @constructor */function A() { this.m2 = 5; }");
  }

  public void testConstructor2() {
    testSame("/** @constructor */var A = function() { this.m2 = 5; }");
  }

  public void testConstructor3() {
    testSame("/** @constructor */a.A = function() { this.m2 = 5; }");
  }

  public void testThisJSDoc1() throws Exception {
    testSame("/** @this whatever */function h() { this.foo = 56; }");
  }

  public void testThisJSDoc2() throws Exception {
    testSame("/** @this whatever */var h = function() { this.foo = 56; }");
  }

  public void testThisJSDoc3() throws Exception {
    testSame("/** @this whatever */foo.bar = function() { this.foo = 56; }");
  }

  public void testThisJSDoc4() throws Exception {
    testSame("/** @this whatever */function() { this.foo = 56; }");
  }

  public void testThisJSDoc5() throws Exception {
    testSame("function a() { /** @this x */function() { this.foo = 56; } }");
  }

  public void testMethod1() {
    testSame("A.prototype.m1 = function() { this.m2 = 5; }");
  }

  public void testMethod2() {
    testSame("a.B.prototype.m1 = function() { this.m2 = 5; }");
  }

  public void testMethod3() {
    testSame("a.b.c.D.prototype.m1 = function() { this.m2 = 5; }");
  }

  public void testStaticMethod1() {
    testFailure("a.b = function() { this.m2 = 5; }");
  }

  public void testStaticMethod2() {
    testFailure("a.b = function() { return function() { this.m2 = 5; } }");
  }

  public void testStaticMethod3() {
    testFailure("a.b.c = function() { return function() { this.m2 = 5; } }");
  }

  public void testMethodInStaticFunction() {
    testSame("function f() { A.prototype.m1 = function() { this.m2 = 5; } }");
  }

  public void testStaticFunctionInMethod1() {
    testSame("A.prototype.m1 = function() { function me() { this.m2 = 5; } }");
  }

  public void testStaticFunctionInMethod2() {
    testSame("A.prototype.m1 = function() {" +
        "  function me() {" +
        "    function myself() {" +
        "      function andI() { this.m2 = 5; } } } }");
  }
}
