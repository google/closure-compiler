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

/**
 * Unit tests for {@link ExploitAssigns}
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public class ExploitAssignsTest extends CompilerTestCase {

  public void testExprExploitationTypes() {
    test("a = true; b = true",
         "b = a = true");
    test("a = !0; b = !0",
         "b = a = !0");
    test("a = !1; b = !1",
         "b = a = !1");
    test("a = void 0; b = void 0",
         "b = a = void 0");
    test("a = -Infinity; b = -Infinity",
         "b = a = -Infinity");
  }

  public void testExprExploitationTypes2() {
    test("a = !0; b = !0",
         "b = a = !0");
  }

  public void testExprExploitation() {
    test("a = null; b = null; var c = b",
         "var c = b = a = null");
    test("a = null; b = null",
         "b = a = null");
    test("a = undefined; b = undefined",
         "b = a = undefined");
    test("a = 0; b = 0", "b=a=0");
    test("a = 'foo'; b = 'foo'",
         "b = a = \"foo\"");
    test("a = c; b = c", "b=a=c");

    testSame("a = 0; b = 1");
    testSame("a = \"foo\"; b = \"foox\"");

    test("a = null; a && b;", "(a = null)&&b");
    test("a = null; a || b;", "(a = null)||b");

    test("a = null; a ? b : c;", "(a = null) ? b : c");

    test("a = null; this.foo = null;",
         "this.foo = a = null");
    test("function f(){ a = null; return null; }",
         "function f(){return a = null}");

    test("a = true; if (a) { foo(); }",
         "if (a = true) { foo() }");
    test("a = true; if (a && a) { foo(); }",
         "if ((a = true) && a) { foo() }");
    test("a = false; if (a) { foo(); }",
         "if (a = false) { foo() }");

    test("a = !0; if (a) { foo(); }",
        "if (a = !0) { foo() }");
    test("a = !0; if (a && a) { foo(); }",
        "if ((a = !0) && a) { foo() }");
    test("a = !1; if (a) { foo(); }",
        "if (a = !1) { foo() }");

    testSame("a = this.foo; a();");
    test("a = b; b = a;",
         "b = a = b");
    testSame("a = b; a.c = a");
    test("this.foo = null; this.bar = null;",
         "this.bar = this.foo = null");
    test("this.foo = null; this.bar = null; this.baz = this.bar",
         "this.baz = this.bar = this.foo = null");
    test("this.foo = null; a = null;",
         "a = this.foo = null");
    test("this.foo = null; a = this.foo;",
         "a = this.foo = null");
    test("a.b.c=null; a=null;",
         "a = a.b.c = null");
    testSame("a = null; a.b.c = null");
    test("(a=b).c = null; this.b = null;",
         "this.b = (a=b).c = null");
    testSame("if(x) a = null; else b = a");
  }

  public void testNestedExprExploitation() {
    test("this.foo = null; this.bar = null; this.baz = null;",
         "this.baz = this.bar = this.foo = null");

    test("a = 3; this.foo = a; this.bar = a; this.baz = 3;",
         "this.baz = this.bar = this.foo = a = 3");
    test("a = 3; this.foo = a; this.bar = this.foo; this.baz = a;",
         "this.baz = this.bar = this.foo = a = 3");
    test("a = 3; this.foo = a; this.bar = 3; this.baz = this.foo;",
         "this.baz = this.bar = this.foo = a = 3");
    test("a = 3; this.foo = a; a = 3; this.bar = 3; " +
         "a = 3; this.baz = this.foo;",
         "this.baz = a = this.bar = a = this.foo = a = 3");

    test("a = 4; this.foo = a; a = 3; this.bar = 3; " +
         "a = 3; this.baz = this.foo;",
         "this.foo = a = 4; a = this.bar = a = 3; this.baz = this.foo");
    test("a = 3; this.foo = a; a = 4; this.bar = 3; " +
         "a = 3; this.baz = this.foo;",
         "this.foo = a = 3; a = 4; a = this.bar = 3; this.baz = this.foo");
    test("a = 3; this.foo = a; a = 3; this.bar = 3; " +
         "a = 4; this.baz = this.foo;",
         "this.bar = a = this.foo = a = 3; a = 4; this.baz = this.foo");
  }

  public void testBug1840071() {
    // Some external properties are implemented as setters. Let's
    // make sure that we don't collapse them inappropriately.
    test("a.b = a.x; if (a.x) {}", "if (a.b = a.x) {}");
    testSame("a.b = a.x; if (a.b) {}");
    test("a.b = a.c = a.x; if (a.x) {}", "if (a.b = a.c = a.x) {}");
    testSame("a.b = a.c = a.x; if (a.c) {}");
    testSame("a.b = a.c = a.x; if (a.b) {}");
  }

  public void testBug2072343() {
    testSame("a = a.x;a = a.x");
    testSame("a = a.x;b = a.x");
    test("b = a.x;a = a.x", "a = b = a.x");
    testSame("a.x = a;a = a.x");
    testSame("a.b = a.b.x;a.b = a.b.x");
    testSame("a.y = a.y.x;b = a.y;c = a.y.x");
    test("a = a.x;b = a;c = a.x", "b = a = a.x;c = a.x");
    test("b = a.x;a = b;c = a.x", "a = b = a.x;c = a.x");
 }

  public void testBadCollapseIntoCall() {
    // Can't collapse this, because if we did, 'foo' would be called
    // in the wrong 'this' context.
    testSame("this.foo = function() {}; this.foo();");
  }

  public void testBadCollapse() {
    testSame("this.$e$ = []; this.$b$ = null;");
  }

  public void testIssue1017() {
    testSame("x = x.parentNode.parentNode; x = x.parentNode.parentNode;");
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new PeepholeOptimizationsPass(compiler,new ExploitAssigns());
  }
}
