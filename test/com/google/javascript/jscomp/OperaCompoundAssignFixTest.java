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
 * Unit tests for {@link OperaCompoundAssignFix}
 *
 */
public class OperaCompoundAssignFixTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new OperaCompoundAssignFix(compiler);
  }

  public void testNoFix() {
    testSame("x = x");
    testSame("x = x = x");
    testSame("x = x = x(x)");
  }

  public void testFix() {
    test("       var a,b,x; x = a[x] = b[x]",
         "var c; var a,b,x; c = a[x] = b[x], x = c");
    test("       var a,b,x; x = a[1] = x.b",
         "var c; var a,b,x; c = a[1] = x.b, x = c");
  }

  public void testCombinedFix() {
    test("       var a,b,c, x; x = a[x] = b[x] = c[x]",
         "var d; var a,b,c, x; d = a[x] = b[x] = c[x], x = d");
    test("       var a,b,c, x; x = a[1] = b[1] = x[1]",
         "var d; var a,b,c, x; d = a[1] = b[1] = x[1], x = d");
  }

  public void testNestedFix1() {
    test("            var a,b,c,x,y;y= x = a[x] = b[y] = c[x];",
         "var e;var d;var a,b,c,x,y;d=(e = a[x] = b[y] = c[x], x=e), y=d;");
  }

  public void testNestedFix2() {
    test("            var a,b,c,x,y;y=a[x]= x=a[x]=b[y]=c[x];",
         "var e;var d;var a,b,c,x,y;d=a[x]=(e=a[x]=b[y]=c[x], x=e), y=d;");
  }

  public void testJqueryTest() {
    test("       z = bar[z] = bar[z] || [];",
         "var a; a = bar[z] = bar[z] || [], z=a");
  }

  public void testNoCrossingScope() {
    testSame("x = function(x) { return a[x] + b[x] }");
  }

  public void testForLoops() {
    test("       var a,b,x;for(x = a[x] = b[x];;)        {}",
         "var c; var a,b,x;for(c = a[x] = b[x], x = c;;) {}");
  }

  public void testForInLoops() {
    test("       var a,b,x;for(var j in  x = a[x] = b[x])         {}",
         "var c; var a,b,x;for(var j in (c = a[x] = b[x], x = c)) {}");
  }

  public void testUsedInCondition() {
    test("       var a,b,x;if(x = a[x] = b[x]) {}",
         "var c; var a,b,x;if((c = a[x] = b[x], x = c)) {}");
  }

  public void testUsedInExpression() {
    test("       var a,b,x; FOO( x = a[x] = b[x]);",
         "var c; var a,b,x; FOO((c = a[x] = b[x], x = c));");
  }

  public void testLocalScope() {
    test("function FOO() {       var a,b,x; x = a[x] = b[x]}",
         "function FOO() {var c; var a,b,x; c = a[x] = b[x], x = c}");
    test("function FOO() {       var a,b,x; x = a[1] = x.b}",
         "function FOO() {var c; var a,b,x; c = a[1] = x.b, x = c}");
  }

  public void testProperNames1() {
    test("var a,b,c,d,x;" +
         "function f() {" +
         "  function g() { return a }" +
         "  x = a[x] = b[x];" +
         "  return g();" +
         "}",

         "var a,b,c,d,x;" +
         "function f() {" +
         "  var e;" +
         "  function g() { return a }" +
         "  e = a[x] = b[x], x = e;" +
         "  return g();" +
         "}");
  }

  public void testProperNames2() {
    test("var a;",
         "function f() {" +
         " var b,x; x = a[x] = b[x];" +
         " return g();" +
         "}",

         "function f() {" +
         " var c;" +
         " var b,x; c = a[x] = b[x], x = c;" +
         " return g();" +
         "}", null, null);
  }

  public void testSaveShadowing() {
    // We could reuse any new temps in an inner scope.
    test("       var a,b,x; x = a[x] = b[x];" +
         "function FOO() {       var a,b,x; x = a[x] = b[x]}",

         "var c; var a,b,x; c = a[x] = b[x], x = c;" +
         "function FOO() {var c; var a,b,x; c = a[x] = b[x], x = c}");

  }
}
