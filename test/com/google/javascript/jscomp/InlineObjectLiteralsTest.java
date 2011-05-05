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
 * Verifies that valid candidates for object literals are inlined as
 * expected, and invalid candidates are not touched.
 *
 */
public class InlineObjectLiteralsTest extends CompilerTestCase {

  public InlineObjectLiteralsTest() {
    enableNormalize();
  }

  @Override
  public void setUp() {
    super.enableLineNumberCheck(true);
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new InlineObjectLiterals(
        compiler,
        compiler.getUniqueNameIdSupplier());
  }

  // Test object literal -> variable inlining
  public void testObject1() {
    test("var a = {x:x, y:y}; f(a.x, a.y);",
         "var JSCompiler_object_inline_y_1=y;" +
         "var JSCompiler_object_inline_x_0=x;" +
         "f(JSCompiler_object_inline_x_0, JSCompiler_object_inline_y_1);");
  }

  public void testObject2() {
    test("var a = {y:y}; a.x = z; f(a.x, a.y);",
         "var JSCompiler_object_inline_y_0 = y;" +
         "var JSCompiler_object_inline_x_1;" +
         "JSCompiler_object_inline_x_1=z;" +
         "f(JSCompiler_object_inline_x_1, JSCompiler_object_inline_y_0);");
  }

  public void testObject3() {
    // Inlining the 'y' would cause the 'this' to be different in the
    // target function.
    testSame("var a = {y:y,x:x}; a.y(); f(a.x);");
  }

  public void testObject4() {
    // Object literal is escaped.
    testSame("var a = {y:y}; a.x = z; f(a.x, a.y); g(a);");
  }

  public void testObject5() {
    test("var a = {x:x, y:y}; var b = {a:a}; f(b.a.x, b.a.y);",
         "var a = {x:x, y:y};" +
         "var JSCompiler_object_inline_a_0=a;" +
         "f(JSCompiler_object_inline_a_0.x, JSCompiler_object_inline_a_0.y);");
  }

  public void testObject6() {
    test("for (var i = 0; i < 5; i++) { var a = {i:i,x:x}; f(a.i, a.x); }",
         "for (var i = 0; i < 5; i++) {" +
         "  var JSCompiler_object_inline_x_1=x;" +
         "  var JSCompiler_object_inline_i_0=i;" +
         "  f(JSCompiler_object_inline_i_0,JSCompiler_object_inline_x_1)" +
         "}");
    test("if (c) { var a = {i:i,x:x}; f(a.i, a.x); }",
         "if (c) {" +
         "  var JSCompiler_object_inline_x_1=x;" +
         "  var JSCompiler_object_inline_i_0=i;" +
         "  f(JSCompiler_object_inline_i_0,JSCompiler_object_inline_x_1)" +
         "}");
  }

  public void testObject7() {
    test("var a = {x:x, y:f()}; g(a.x);",
         "var JSCompiler_object_inline_y_1=f();" +
         "var JSCompiler_object_inline_x_0=x;" +
         "g(JSCompiler_object_inline_x_0)");
  }

  public void testObject8() {
    testSame("var a = {x:x,y:y}; var b = {x:y}; f((c?a:b).x);");

    test("var a; if(c) { a={x:x, y:y}; } else { a={x:y}; } f(a.x);",
         "var JSCompiler_object_inline_y_1;" +
         "var JSCompiler_object_inline_x_0;" +
         "if(c) JSCompiler_object_inline_x_0=x," +
         "      JSCompiler_object_inline_y_1=y," +
         "      true;" +
         "else JSCompiler_object_inline_x_0=y," +
         "     JSCompiler_object_inline_y_1=void 0," +
         "     true;" +
         "f(JSCompiler_object_inline_x_0)");
    test("var a = {x:x,y:y}; var b = {x:y}; c ? f(a.x) : f(b.x);",
         "var JSCompiler_object_inline_y_1 = y; " +
         "var JSCompiler_object_inline_x_0 = x; " +
         "var JSCompiler_object_inline_x_2 = y; " +
         "c ? f(JSCompiler_object_inline_x_0):f(JSCompiler_object_inline_x_2)");
  }

  public void testObject9() {
    // There is a call, so no inlining
    testSame("function f(a,b) {" +
             "  var x = {a:a,b:b}; x.a(); return x.b;" +
             "}");

    test("function f(a,b) {" +
         "  var x = {a:a,b:b}; g(x.a); x = {a:a,b:2}; return x.b;" +
         "}",
         "function f(a,b) {" +
         "  var JSCompiler_object_inline_b_1 = b;" +
         "  var JSCompiler_object_inline_a_0 = a;" +
         "  g(JSCompiler_object_inline_a_0);" +
         "  JSCompiler_object_inline_a_0 = a," +
         "  JSCompiler_object_inline_b_1=2," +
         "  true;" +
         "  return JSCompiler_object_inline_b_1" +
         "}");

    test("function f(a,b) { " +
         "  var x = {a:a,b:b}; g(x.a); x.b = x.c = 2; return x.b; " +
         "}",
         "function f(a,b) { " +
         "  var JSCompiler_object_inline_b_1=b; " +
         "  var JSCompiler_object_inline_c_2;" +
         "  var JSCompiler_object_inline_a_0=a;" +
         "  g(JSCompiler_object_inline_a_0);" +
         "  JSCompiler_object_inline_b_1=JSCompiler_object_inline_c_2=2;" +
         "  return JSCompiler_object_inline_b_1" +
         "}");
  }

  public void testObject10() {
    test("var x; var b = f(); x = {a:a, b:b}; if(x.a) g(x.b);",
         "var JSCompiler_object_inline_b_1;" +
         "var JSCompiler_object_inline_a_0;" +
         "var b = f();" +
         "JSCompiler_object_inline_a_0=a,JSCompiler_object_inline_b_1=b,true;" +
         "if(JSCompiler_object_inline_a_0) g(JSCompiler_object_inline_b_1)");
    test("var x = {}; var b = f(); x = {a:a, b:b}; if(x.a) g(x.b) + x.c",
         "var JSCompiler_object_inline_b_1;" +
         "var JSCompiler_object_inline_c_2;" +
         "var JSCompiler_object_inline_a_0;" +
         "var b=f();" +
         "JSCompiler_object_inline_a_0=a,JSCompiler_object_inline_b_1=b," +
         "  JSCompiler_object_inline_c_2=void 0,true;" +
         "if(JSCompiler_object_inline_a_0) " +
         "  g(JSCompiler_object_inline_b_1) + JSCompiler_object_inline_c_2");
    test("var x; var b = f(); x = {a:a, b:b}; x.c = c; if(x.a) g(x.b) + x.c",
         "var JSCompiler_object_inline_b_1;" +
         "var JSCompiler_object_inline_c_2;" +
         "var JSCompiler_object_inline_a_0;" +
         "var b = f();" +
         "JSCompiler_object_inline_a_0 = a,JSCompiler_object_inline_b_1 = b, " +
         "  JSCompiler_object_inline_c_2=void 0,true;" +
         "JSCompiler_object_inline_c_2 = c;" +
         "if (JSCompiler_object_inline_a_0)" +
         "  g(JSCompiler_object_inline_b_1) + JSCompiler_object_inline_c_2;");
    test("var x = {a:a}; if (b) x={b:b}; f(x.a||x.b);",
         "var JSCompiler_object_inline_b_1;" +
         "var JSCompiler_object_inline_a_0 = a;" +
         "if(b) JSCompiler_object_inline_b_1 = b," +
         "      JSCompiler_object_inline_a_0 = void 0," +
         "      true;" +
         "f(JSCompiler_object_inline_a_0 || JSCompiler_object_inline_b_1)");
    test("var x; var y = 5; x = {a:a, b:b, c:c}; if (b) x={b:b}; f(x.a||x.b);",
         "var JSCompiler_object_inline_b_1;" +
         "var JSCompiler_object_inline_c_2;" +
         "var JSCompiler_object_inline_a_0;" +
         "var y=5;" +
         "JSCompiler_object_inline_a_0=a," +
         "JSCompiler_object_inline_b_1=b," +
         "JSCompiler_object_inline_c_2=c," +
         "true;" +
         "if (b) JSCompiler_object_inline_b_1=b," +
         "       JSCompiler_object_inline_c_2=void 0," +
         "       JSCompiler_object_inline_a_0=void 0," +
         "       true;" +
         "f(JSCompiler_object_inline_a_0||JSCompiler_object_inline_b_1)");
  }

  public void testObject11() {
    testSame("var x = {a:b}; (x = {a:a}).c = 5; f(x.a);");
    testSame("var x = {a:a}; f(x[a]); g(x[a]);");
  }

  public void testObject12() {
    test("var a; a = {x:1, y:2}; f(a.x, a.y2);",
         "var JSCompiler_object_inline_y2_2;" +
         "var JSCompiler_object_inline_y_1;" +
         "var JSCompiler_object_inline_x_0;" +
         "JSCompiler_object_inline_x_0=1," +
         "JSCompiler_object_inline_y_1=2," +
         "JSCompiler_object_inline_y2_2=void 0," +
         "true;" +
         "f(JSCompiler_object_inline_x_0, JSCompiler_object_inline_y2_2);");
  }

  public void testObject13() {
    testSame("var x = {a:1, b:2}; x = {a:3, b:x.a};");
  }

  public void testObject14() {
    testSame("var x = {a:1}; if ('a' in x) { f(); }");
    testSame("var x = {a:1}; for (var y in x) { f(y); }");
  }

  public void testObject15() {
    testSame("x = x || {}; f(x.a);");
  }

  public void testObject16() {
    test("function f(e) { bar(); x = {a: foo()}; var x; print(x.a); }",
         "function f(e) { " +
         "  var JSCompiler_object_inline_a_0;" +
         "  bar();" +
         "  JSCompiler_object_inline_a_0 = foo(), true;" +
         "  print(JSCompiler_object_inline_a_0);" +
         "}");
  }

  public void testObject17() {
    // Note: Some day, with careful analysis, these two uses could be
    // disambiguated, and the second assignment could be inlined.
    testSame(
      "var a = {a: function(){}};" +
      "a.a();" +
      "a = {a1: 100};" +
      "print(a.a1);");
  }
}
