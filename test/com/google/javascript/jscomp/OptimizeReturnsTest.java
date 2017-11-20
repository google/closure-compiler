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

  private static final String EXTERNAL_SYMBOLS = lines("var extern;", "extern.externalMethod");

  public OptimizeReturnsTest() {
    super(lines(DEFAULT_EXTERNS, EXTERNAL_SYMBOLS));
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableGatherExternProperties();
  }

  @Override
  protected int getNumRepetitions() {
    // run pass once.
    return 1;
  }

  public void testNoRewriteUsedResult1() throws Exception {
    String source = lines(
        "function a(){return 1}",
        "var x = a()");
    testSame(source);
  }

  public void testNoRewriteUsedResult2() throws Exception {
    String source = lines(
        "var a = function(){return 1}",
        "a(); var b = a()");
    testSame(source);
  }

  public void testNoRewriteUsedClassMethodResult1() throws Exception {
    String source = lines(
        "class C { method() {return 1} }",
        "var x = new C().method()");
    testSame(source);
  }

  public void testNoRewriteUnsedObjectMethodResult1() throws Exception {
    String source = lines(
        "var o = { method() {return 1} }",
        "o.method()");
    testSame(source);
  }

  public void testNoRewriteDestructuredArray1() throws Exception {
    String source = lines(
        "var x = function() { return 1; };",
        "[x] = []",
        "x()");
    testSame(source);
  }

  public void testNoRewriteDestructuredArray2() throws Exception {
    String source = lines(
        "var x = function() { return 1; };",
        "[x = function() {}] = []",
        "x()");
    testSame(source);
  }

  public void testNoRewriteDestructuredArray3() throws Exception {
    String source = lines(
        "class C { method() { return 1 }}",
        "[x.method] = []",
        "x()");
    testSame(source);
  }

  public void testNoRewriteDestructuredArray4() throws Exception {
    String source = lines(
        "class C { method() { return 1 }}",
        "[x.method] = []",
        "x()");
    testSame(source);
  }

  public void testNoRewriteDestructuredObject1() throws Exception {
    String source = lines(
        "var x = function() { return 1; };",
        "({a:x} = {})",
        "x()");
    testSame(source);
  }

  public void testNoRewriteDestructuredObject2() throws Exception {
    String source = lines(
        "var x = function() { return 1; };",
        "({a:x = function() {}} = {})",
        "x()");
    testSame(source);
  }

  public void testNoRewriteDestructuredObject3() throws Exception {
    String source = lines(
        "class C { method() { return 1 }}",
        "({a:x.method} = {})",
        "x()");
    testSame(source);
  }

  public void testNoRewriteDestructuredObject4() throws Exception {
    String source = lines(
        "class C { method() { return 1 }}",
        "({a:x.method = function() {}} = {})",
        "x()");
    testSame(source);
  }

  public void testNoRewriteTagged1() throws Exception {
    // TODO(johnlenz): support this. Unused return can be removed.
    String source = lines(
        "var f = function() { return 1; };",
        "f`tagged`");
    testSame(source);
  }

  public void testNoRewriteTagged2() throws Exception {
    // Tagged use prevents optimizations
    String source = lines(
        "var f = function() { return 1; };",
        "var x = f`tagged`");
    testSame(source);
  }


  public void testNoRewriteTagged3() throws Exception {
    // Tagged use is not ignored.
    String source = lines(
        "var f = function() { return 1; };",
        "var x = f`tagged`",
        "f()");
    testSame(source);
  }

  public void testNoRewriteConstructorProp() throws Exception {
    String source = lines(
        "class C { constructor() { return 1 } }",
        "x.constructor()");
    testSame(source);
  }

  public void testRewriteUnusedResult1() throws Exception {
    String source = lines(
        "function a(){return 1}",
        "a()");
    String expected = lines(
        "function a(){return}",
        "a()");
    test(source, expected);
  }

  public void testRewriteUnusedResult2() throws Exception {
    String source = lines(
        "var a; a = function(){return 1}",
        "a()");
    String expected = lines(
        "var a; a = function(){return}",
        "a()");
    test(source, expected);
  }

  public void testRewriteUnusedResult3() throws Exception {
    String source = lines(
        "var a = function(){return 1}",
        "a()");
    String expected = lines(
        "var a = function(){return}",
        "a()");
    test(source, expected);
  }

  public void testRewriteUnusedResult4a() throws Exception {
    String source = lines(
        "var a = function(){return a()}",
        "a()");
    testSame(source);
  }

  public void testRewriteUnusedResult4b() throws Exception {
    String source = lines(
        "var a = function b(){return b()}",
        "a()");
    testSame(source);
  }

  public void testRewriteUnusedResult4c() throws Exception {
    String source = lines(
        "function a(){return a()}",
        "a()");
    testSame(source);
  }

  public void testRewriteUnusedResult5() throws Exception {
    String source = lines(
        "function a(){}",
        "a.prototype.foo = function(args) {return args};",
        "var o = new a;",
        "o.foo()");
    String expected = lines(
        "function a(){}",
        "a.prototype.foo = function(args) {args;return};",
        "var o = new a;",
        "o.foo()");
    test(source, expected);
  }

  public void testRewriteUnusedResult6() throws Exception {
    String source = lines(
        "function a(){return (g = 1)}",
        "a()");
    String expected = lines(
        "function a(){g = 1;return}",
        "a()");
    test(source, expected);
  }

  public void testRewriteUnusedResult7a() throws Exception {
    String source = lines(
        "function a() { return 1 }",
        "function b() { return a() }",
        "function c() { return b() }",
        "c();");

    String expected = lines(
        "function a() { return 1 }",
        "function b() { return a() }",
        "function c() { b(); return }",
        "c();");
    test(source, expected);
  }

  public void testRewriteUnusedResult7b() throws Exception {
    String source = lines(
        "c();",
        "function c() { return b() }",
        "function b() { return a() }",
        "function a() { return 1 }");

    // Iteration 1.
    String expected = lines(
        "c();",
        "function c() { b(); return }",
        "function b() { return a() }",
        "function a() { return 1 }");
    test(source, expected);

    // Iteration 2.
    source = expected;
    expected = lines(
        "c();",
        "function c() { b(); return }",
        "function b() { a(); return }",
        "function a() { return 1 }");
    test(source, expected);

    // Iteration 3.
    source = expected;
    expected = lines(
        "c();",
        "function c() { b(); return }",
        "function b() { a(); return }",
        "function a() { return }");
    test(source, expected);
  }

  public void testRewriteUnusedResult8() throws Exception {
    String source = lines(
        "function a() { return c() }",
        "function b() { return a() }",
        "function c() { return b() }",
        "c();");
    testSame(source);
  }

  public void testRewriteUnusedResult9() throws Exception {
    // Proves that the deleted function scope is reported.
    String source = lines(
        "function a(){return function() {};}",
        "a()");
    String expected = lines(
        "function a(){(function() {}); return}",
        "a()");
    test(source, expected);
  }

  public void testRewriteUsedResult10() throws Exception {
    String source = lines(
        "class C { method() {return 1} }",
        "new C().method()");
    String expected = lines(
        "class C { method() {return} }",
        "new C().method()");
    test(source, expected);
  }

  public void testRewriteUnusedAsyncResult1() throws Exception {
    // Async function returns can be dropped if no-one waits on the returned
    // promise.
    String source = lines(
        "async function a(){return promise}",
        "a()");
    String expected = lines(
        "async function a(){promise; return}",
        "a()");
    test(source, expected);
  }

  public void testRewriteUnusedGeneratorResult1() throws Exception {
    // Generator function returns can be dropped if no-one uses the returned
    // iterator.
    String source = lines(
        "function *a(){return value}",
        "a()");
    String expected = lines(
        "function *a(){value; return}",
        "a()");
    test(source, expected);
  }

  public void testNoRewriteObjLit1() throws Exception {
    String source = lines(
        "var a = {b:function(){return 1;}}",
        "for(c in a) (a[c])();",
        "a.b()");
    testSame(source);
  }

  public void testNoRewriteObjLit2() throws Exception {
    String source = lines(
        "var a = {b:function fn(){return 1;}}",
        "for(c in a) (a[c])();",
        "a.b()");
    testSame(source);
  }

  public void testNoRewriteArrLit() throws Exception {
    String source = lines(
        "var a = [function(){return 1;}]",
        "(a[0])();");
    testSame(source);
  }

  public void testPrototypeMethod1() throws Exception {
    String source = lines(
        "function c(){}",
        "c.prototype.a = function(){return 1}",
        "var x = new c;",
        "x.a()");
    String result = lines(
        "function c(){}",
        "c.prototype.a = function(){return}",
        "var x = new c;",
        "x.a()");
    test(source, result);
  }

  public void testPrototypeMethod2() throws Exception {
    String source = lines(
        "function c(){}",
        "c.prototype.a = function(){return 1}",
        "goog.reflect.object({a: 'v'})",
        "var x = new c;",
        "x.a()");
    testSame(source);
  }

  public void testPrototypeMethod3() throws Exception {
    String source = lines(
        "function c(){}",
        "c.prototype.a = function(){return 1}",
        "var x = new c;",
        "for(var key in goog.reflect.object({a: 'v'})){ x[key](); }",
        "x.a()");
    testSame(source);
  }

  public void testPrototypeMethod4() throws Exception {
    String source = lines(
        "function c(){}",
        "c.prototype.a = function(){return 1}",
        "var x = new c;",
        "for(var key in goog.reflect.object({a: 'v'})){ x[key](); }");
    testSame(source);
  }

  public void testCallOrApply() throws Exception {
    // TODO(johnlenz): Add support for .apply
    test(
        "function a() {return 1}; a.call(new foo);",
        "function a() {return  }; a.call(new foo);");

    testSame("function a() {return 1}; a.apply(new foo);");
  }

  public void testRewriteUseSiteRemoval() throws Exception {
    String source = lines(
        "function a() { return {\"_id\" : 1} }",
        "a();");
    String expected = lines(
        "function a() { ({\"_id\" : 1}); return }",
        "a();");
    test(source, expected);
  }

  public void testUnknownDefinitionAllowRemoval() throws Exception {
    // TODO(johnlenz): allow this to be optimized.
    testSame(
        lines(
            "let x = functionFactory();",
            "x(1, 2);",
            "x = function(a,b) { return b; }"));
  }

  public void testRewriteUnusedResultWithSafeReference1() throws Exception {
    String source = lines(
        "function a(){return 1}",
        "typeof a",
        "a()");
    String expected = lines(
        "function a(){return}",
        "typeof a",
        "a()");
    test(source, expected);
  }

  public void testRewriteUnusedResultWithSafeReference2() throws Exception {
    String source = lines(
        "function a(){return 1}",
        "x instanceof a",
        "a instanceof x",
        "a()");
    String expected = lines(
        "function a(){return}",
        "x instanceof a",
        "a instanceof x",
        "a()");
    test(source, expected);
  }

  public void testRewriteUnusedResultWithSafeReference3() throws Exception {
    String source = lines(
        "function a(){return 1}",
        "x in a",
        "a in x",
        "a()");
    String expected = lines(
        "function a(){return}",
        "x in a",
        "a in x",
        "a()");
    test(source, expected);
  }

  public void testRewriteUnusedResultWithSafeReference4() throws Exception {
    String source = lines(
        "function a(){return 1}",
        "a.x",
        "a['x']",
        "a()");
    String expected = lines(
        "function a(){return}",
        "a.x",
        "a['x']",
        "a()");
    test(source, expected);
  }

  public void testRewriteUnusedResultWithSafeReference5() throws Exception {
    String source = lines(
        "function a(){return 1}",
        "for (x in a) {}",
        "for (x of a) {}",
        "a()");
    String expected = lines(
        "function a(){return}",
        "for (x in a) {}",
        "for (x of a) {}",
        "a()");
    test(source, expected);
  }

  public void testNoRewriteUnusedResultWithUnsafeReference1() throws Exception {
    // call to 'a.x' escapes 'a' as 'this'
    String source = lines(
        "function a(){return 1}",
        "a.x()",
        "a()");
    testSame(source);
  }

  public void testNoRewriteUnusedResultWithUnsafeReference2() throws Exception {
    // call to 'a.x' escapes 'a' as 'this'
    String source = lines(
        "function a(){return 1}",
        "a['x']()",
        "a()");
    testSame(source);
  }

  public void testNoRewriteUnusedResultWithUnsafeReference3() throws Exception {
    // call to 'a' is assigned an unknown value
    String source = lines(
        "function a(){return 1}",
        "for (a in x) {}",
        "a()");
    testSame(source);
  }

  public void testNoRewriteUnusedResultWithUnsafeReference4() throws Exception {
    // call to 'a' is assigned an unknown value
    // TODO(johnlenz): optimize this.
    String source = lines(
        "function a(){return 1}",
        "for (a of x) {}",
        "a()");
    testSame(source);
  }
}
