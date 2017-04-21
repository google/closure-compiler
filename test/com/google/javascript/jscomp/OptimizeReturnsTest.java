/*
 * Copyright 2009 The Closure Compiler Authors.
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
 * Tests OptimizeReturns
 * @author johnlenz@google.com (John Lenz)
 */
public final class OptimizeReturnsTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new OptimizeReturns(compiler);
  }

  private static final String EXTERNAL_SYMBOLS =
    "var extern;extern.externalMethod";

  public OptimizeReturnsTest() {
    super(EXTERNAL_SYMBOLS);
  }

  @Override
  protected int getNumRepetitions() {
    // run pass once.
    return 1;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    disableTypeCheck();
  }

  /**
   * Combine source strings using '\n' as the separator.
   */
  private static String newlineJoin(String ... parts) {
    return LINE_JOINER.join(parts);
  }

  public void testNoRewriteUsedResult1() throws Exception {
    String source = newlineJoin(
        "function a(){return 1}",
        "var x = a()");
    testSame(source);
  }

  public void testNoRewriteUsedResult2() throws Exception {
    String source = newlineJoin(
        "var a = function(){return 1}",
        "a(); var b = a()");
    testSame(source);
  }

  public void testRewriteUnusedResult1() throws Exception {
    String source = newlineJoin(
        "function a(){return 1}",
        "a()");
    String expected = newlineJoin(
        "function a(){return}",
        "a()");
    test(source, expected);
  }

  public void testRewriteUnusedResult2() throws Exception {
    String source = newlineJoin(
        "var a; a = function(){return 1}",
        "a()");
    String expected = newlineJoin(
        "var a; a = function(){return}",
        "a()");
    test(source, expected);
  }

  public void testRewriteUnusedResult3() throws Exception {
    String source = newlineJoin(
        "var a = function(){return 1}",
        "a()");
    String expected = newlineJoin(
        "var a = function(){return}",
        "a()");
    test(source, expected);
  }

  public void testRewriteUnusedResult4a() throws Exception {
    String source = newlineJoin(
        "var a = function(){return a()}",
        "a()");
    testSame(source);
  }

  public void testRewriteUnusedResult4b() throws Exception {
    String source = newlineJoin(
        "var a = function b(){return b()}",
        "a()");
    testSame(source);
  }

  public void testRewriteUnusedResult4c() throws Exception {
    String source = newlineJoin(
        "function a(){return a()}",
        "a()");
    testSame(source);
  }

  public void testRewriteUnusedResult5() throws Exception {
    String source = newlineJoin(
        "function a(){}",
        "a.prototype.foo = function(args) {return args};",
        "var o = new a;",
        "o.foo()");
    String expected = newlineJoin(
        "function a(){}",
        "a.prototype.foo = function(args) {return};",
        "var o = new a;",
        "o.foo()");
    test(source, expected);
  }

  public void testRewriteUnusedResult6() throws Exception {
    String source = newlineJoin(
        "function a(){return (g = 1)}",
        "a()");
    String expected = newlineJoin(
        "function a(){g = 1;return}",
        "a()");
    test(source, expected);
  }

  public void testRewriteUnusedResult7a() throws Exception {
    String source = newlineJoin(
        "function a() { return 1 }",
        "function b() { return a() }",
        "function c() { return b() }",
        "c();");

    String expected = newlineJoin(
        "function a() { return 1 }",
        "function b() { return a() }",
        "function c() { b(); return }",
        "c();");
    test(source, expected);
  }

  public void testRewriteUnusedResult7b() throws Exception {
    String source = newlineJoin(
        "c();",
        "function c() { return b() }",
        "function b() { return a() }",
        "function a() { return 1 }");

    // Iteration 1.
    String expected = newlineJoin(
        "c();",
        "function c() { b(); return }",
        "function b() { return a() }",
        "function a() { return 1 }");
    test(source, expected);

    // Iteration 2.
    source = expected;
    expected = newlineJoin(
        "c();",
        "function c() { b(); return }",
        "function b() { a(); return }",
        "function a() { return 1 }");
    test(source, expected);

    // Iteration 3.
    source = expected;
    expected = newlineJoin(
        "c();",
        "function c() { b(); return }",
        "function b() { a(); return }",
        "function a() { return }");
    test(source, expected);
  }

  public void testRewriteUnusedResult8() throws Exception {
    String source = newlineJoin(
        "function a() { return c() }",
        "function b() { return a() }",
        "function c() { return b() }",
        "c();");
    testSame(source);
  }

  public void testNoRewriteObjLit1() throws Exception {
    String source = newlineJoin(
        "var a = {b:function(){return 1;}}",
        "for(c in a) (a[c])();",
        "a.b()");
    testSame(source);
  }

  public void testNoRewriteObjLit2() throws Exception {
    String source = newlineJoin(
        "var a = {b:function fn(){return 1;}}",
        "for(c in a) (a[c])();",
        "a.b()");
    testSame(source);
  }

  public void testNoRewriteArrLit() throws Exception {
    String source = newlineJoin(
        "var a = [function(){return 1;}]",
        "(a[0])();");
    testSame(source);
  }

  public void testPrototypeMethod1() throws Exception {
    String source = newlineJoin(
        "function c(){}",
        "c.prototype.a = function(){return 1}",
        "var x = new c;",
        "x.a()");
    String result = newlineJoin(
        "function c(){}",
        "c.prototype.a = function(){return}",
        "var x = new c;",
        "x.a()");
    test(source, result);
  }

  public void testPrototypeMethod2() throws Exception {
    String source = newlineJoin(
        "function c(){}",
        "c.prototype.a = function(){return 1}",
        "goog.reflect.object({a: 'v'})",
        "var x = new c;",
        "x.a()");
    testSame(source);
  }

  public void testPrototypeMethod3() throws Exception {
    String source = newlineJoin(
        "function c(){}",
        "c.prototype.a = function(){return 1}",
        "var x = new c;",
        "for(var key in goog.reflect.object({a: 'v'})){ x[key](); }",
        "x.a()");
    testSame(source);
  }

  public void testPrototypeMethod4() throws Exception {
    String source = newlineJoin(
        "function c(){}",
        "c.prototype.a = function(){return 1}",
        "var x = new c;",
        "for(var key in goog.reflect.object({a: 'v'})){ x[key](); }");
    testSame(source);
  }

  public void testCallOrApply() throws Exception {
    // TODO(johnlenz): Add support for .call and .apply
    testSame("function a() {return 1}; a.call(new foo);");

    testSame("function a() {return 1}; a.apply(new foo);");
  }

  public void testRewriteUseSiteRemoval() throws Exception {
    String source = newlineJoin(
        "function a() { return {\"_id\" : 1} }",
        "a();");
    String expected = newlineJoin(
        "function a() { return }",
        "a();");
    test(source, expected);
  }
}
