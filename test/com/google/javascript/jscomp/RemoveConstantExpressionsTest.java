/*
 * Copyright 2009 Google Inc.
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
 * Tests for {@link RemoveConstantExpressions}
 *
*
 */
public class RemoveConstantExpressionsTest extends CompilerTestCase {

  @Override
  protected RemoveConstantExpressions getProcessor(Compiler compiler) {
    return new RemoveConstantExpressions(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    // Pass reaches steady state after just 1 iteration
    return 1;
  }

  public void testRemoveNumber() {
    test("3", "");
  }

  public void testRemoveVarGet1() {
    test("a", "");
  }

  public void testRemoveVarGet2() {
    test("var a = 1;a", "var a = 1");
  }

  public void testRemoveNamespaceGet1() {
    test("var a = {};a.b", "var a = {}");
  }

  public void testRemoveNamespaceGet2() {
    test("var a = {};a.b=1;a.b", "var a = {};a.b=1");
  }

  public void testRemovePrototypeGet1() {
    test("var a = {};a.prototype.b", "var a = {}");
  }

  public void testRemovePrototypeGet2() {
    test("var a = {};a.prototype.b = 1;a.prototype.b",
         "var a = {};a.prototype.b = 1");
  }

  public void testRemoveAdd1() {
    test("1 + 2", "");
  }

  public void testNoRemoveVar1() {
    testSame("var a = 1");
  }

  public void testNoRemoveVar2() {
    testSame("var a = 1, b = 2");
  }

  public void testNoRemoveAssign1() {
    testSame("a = 1");
  }

  public void testNoRemoveAssign2() {
    testSame("a = b = 1");
  }

  public void testNoRemoveAssign3() {
    test("1 + (a = 2)", "a = 2");
  }

  public void testNoRemoveAssign4() {
    testSame("x.a = 1");
  }

  public void testNoRemoveAssign5() {
    testSame("x.a = x.b = 1");
  }

  public void testNoRemoveAssign6() {
    test("1 + (x.a = 2)", "x.a = 2");
  }

  public void testNoRemoveCall1() {
    testSame("a()");
  }

  public void testNoRemoveCall2() {
    test("a()+b()", "a();b()");
  }

  public void testNoRemoveCall3() {
    testSame("a() && b()");
  }

  public void testNoRemoveCall4() {
    testSame("a() || b()");
  }

  public void testNoRemoveCall5() {
    test("a() || 1", "a()");
  }

  public void testNoRemoveCall6() {
    testSame("1 || a()");
  }

  public void testNoRemoveThrow1() {
    testSame("function f(){throw a()}");
  }

  public void testNoRemoveThrow2() {
    testSame("function f(){throw a}");
  }

  public void testNoRemoveThrow3() {
    testSame("function f(){throw 10}");
  }

  public void testRemoveInControlStructure1() {
    test("if(2) 1", "if(2);");
  }

  public void testRemoveInControlStructure2() {
    test("while(2) 1", "while(2);");
  }

  public void testRemoveInControlStructure3() {
    test("for(1;2;3) 4", "for(1;2;3);");
  }

  public void testHook1() {
    test("1 ? 2 : 3", "");
  }

  public void testHook2() {
    test("1 ? a() : 3", "1 && a()");
  }

  public void testHook3() {
    test("1 ? 2 : a()", "1 || a()");
  }

  public void testHook4() {
    testSame("1 ? a() : b()");
  }

  public void testHook5() {
    test("a() ? 1 : 2", "a()");
  }

  public void testHook6() {
    test("a() ? b() : 2", "a() && b()");
  }

  public void testHook7() {
    test("a() ? 1 : b()", "a() || b()");
  }

  public void testHook8() {
    testSame("a() ? b() : c()");
  }

  public void testShortCircuit1() {
    testSame("1 && a()");
  }

  public void testShortCircuit2() {
    test("1 && a() && 2", "1 && a()");
  }

  public void testShortCircuit3() {
    test("a() && 1 && 2", "a()");
  }

  public void testComma1() {
    test("1, 2", "");
  }

  public void testComma2() {
    test("1, a()", "a()");
  }

  public void testComma3() {
    test("1, a(), b()", "a();b()");
  }

  public void testComma4() {
    test("a(), b()", "a();b()");
  }

  public void testComma5() {
    test("a(), b(), 1", "a();b()");
  }

  public void testComplex1() {
    test("1 && a() + b() + c()", "1 && (a(), b(), c())");
  }

  public void testComplex2() {
    test("1 && (a() ? b() : 1)", "1 && a() && b()");
  }

  public void testComplex3() {
    test("1 && (a() ? b() : 1 + c())", "1 && (a() ? b() : c())");
  }

  public void testComplex4() {
    test("1 && (a() ? 1 : 1 + c())", "1 && (a() || c())");
  }

  public void testComplex5() {
    // can't simplify lhs of short circuit statements with side effects
    testSame("(a() ? 1 : 1 + c()) && foo()");
  }

  public void testNoRemoveFunctionDeclaration1() {
    testSame("function foo(){}");
  }

  public void testNoRemoveFunctionDeclaration2() {
    testSame("var foo = function (){}");
  }

  public void testNoSimplifyFunctionArgs1() {
    testSame("f(1 + 2, 3 + g())");
  }

  public void testNoSimplifyFunctionArgs2() {
    testSame("1 && f(1 + 2, 3 + g())");
  }

  public void testNoSimplifyFunctionArgs3() {
    testSame("1 && foo(a() ? b() : 1 + c())");
  }

  public void testNoRemoveInherits1() {
    testSame("var a = {}; this.b = {}; var goog = {}; goog.inherits(b, a)");
  }

  public void testNoRemoveInherits2() {
    test("var a = {}; this.b = {}; var goog = {}; goog.inherits(b, a) + 1",
         "var a = {}; this.b = {}; var goog = {}; goog.inherits(b, a)");
  }

  public void testNoRemoveInherits3() {
    testSame("this.a = {}; var b = {}; b.inherits(a);");
  }

  public void testNoRemoveInherits4() {
    test("this.a = {}; var b = {}; b.inherits(a) + 1;",
         "this.a = {}; var b = {}; b.inherits(a)");
  }

  public void testRemoveFromLabel1() {
    test("LBL: void 0", "LBL: {}");
  }

  public void testRemoveFromLabel2() {
    test("LBL: foo() + 1 + bar()", "LBL: {foo();bar()}");
  }
}
