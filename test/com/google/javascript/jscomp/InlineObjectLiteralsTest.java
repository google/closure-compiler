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

import static com.google.javascript.jscomp.CompilerOptions.LanguageMode.ECMASCRIPT_NEXT;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Verifies that valid candidates for object literals are inlined as expected, and invalid
 * candidates are not touched.
 *
 */
@RunWith(JUnit4.class)
public final class InlineObjectLiteralsTest extends CompilerTestCase {

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableNormalize();
    setAcceptedLanguage(ECMASCRIPT_NEXT);
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new InlineObjectLiterals(
        compiler,
        compiler.getUniqueNameIdSupplier());
  }

  // Test object literal -> variable inlining

  @Test
  public void testObject0() {
    // Don't mess with global variables, that is the job of CollapseProperties.
    testSame("var a = {x:1}; f(a.x);");
  }

  @Test
  public void testObject1() {
    testLocal("var a = {x:x(), y:y()}; f(a.x, a.y);",
         "var JSCompiler_object_inline_x_0=x();" +
         "var JSCompiler_object_inline_y_1=y();" +
         "f(JSCompiler_object_inline_x_0, JSCompiler_object_inline_y_1);");
  }

  @Test
  public void testObject1a() {
    testLocal("var a; a = {x:x, y:y}; f(a.x, a.y);",
         "var JSCompiler_object_inline_x_0;" +
         "var JSCompiler_object_inline_y_1;" +
         "(JSCompiler_object_inline_x_0=x," +
         "JSCompiler_object_inline_y_1=y, true);" +
         "f(JSCompiler_object_inline_x_0, JSCompiler_object_inline_y_1);");
  }

  @Test
  public void testObject2() {
    testLocal("var a = {y:y}; a.x = z; f(a.x, a.y);",
         "var JSCompiler_object_inline_y_0 = y;" +
         "var JSCompiler_object_inline_x_1;" +
         "JSCompiler_object_inline_x_1=z;" +
         "f(JSCompiler_object_inline_x_1, JSCompiler_object_inline_y_0);");
  }

  @Test
  public void testObject3() {
    // Inlining the 'y' would cause the 'this' to be different in the
    // target function.
    testSameLocal("var a = {y:y,x:x}; a.y(); f(a.x);");
    testSameLocal("var a; a = {y:y,x:x}; a.y(); f(a.x);");
  }

  @Test
  public void testObject4() {
    // Object literal is escaped.
    testSameLocal("var a = {y:y}; a.x = z; f(a.x, a.y); g(a);");
    testSameLocal("var a; a = {y:y}; a.x = z; f(a.x, a.y); g(a);");
  }

  @Test
  public void testObject5() {
    testLocal("var a = {x:x, y:y}; var b = {a:a}; f(b.a.x, b.a.y);",
         "var a = {x:x, y:y};" +
         "var JSCompiler_object_inline_a_0=a;" +
         "f(JSCompiler_object_inline_a_0.x, JSCompiler_object_inline_a_0.y);");
  }

  @Test
  public void testObject6() {
    testLocal("for (var i = 0; i < 5; i++) { var a = {i:i,x:x}; f(a.i, a.x); }",
         "for (var i = 0; i < 5; i++) {" +
         "  var JSCompiler_object_inline_i_0=i;" +
         "  var JSCompiler_object_inline_x_1=x;" +
         "  f(JSCompiler_object_inline_i_0,JSCompiler_object_inline_x_1)" +
         "}");
    testLocal("if (c) { var a = {i:i,x:x}; f(a.i, a.x); }",
         "if (c) {" +
         "  var JSCompiler_object_inline_i_0=i;" +
         "  var JSCompiler_object_inline_x_1=x;" +
         "  f(JSCompiler_object_inline_i_0,JSCompiler_object_inline_x_1)" +
         "}");
  }

  @Test
  public void testObject7() {
    testLocal("var a = {x:x, y:f()}; g(a.x);",
      "var JSCompiler_object_inline_x_0=x;" +
         "var JSCompiler_object_inline_y_1=f();" +
         "g(JSCompiler_object_inline_x_0)");
  }

  @Test
  public void testObject8() {
    testSameLocal("var a = {x:x,y:y}; var b = {x:y}; f((c?a:b).x);");

    testLocal("var a; if(c) { a={x:x, y:y}; } else { a={x:y}; } f(a.x);",
         "var JSCompiler_object_inline_x_0;" +
         "var JSCompiler_object_inline_y_1;" +
         "if(c) JSCompiler_object_inline_x_0=x," +
         "      JSCompiler_object_inline_y_1=y," +
         "      true;" +
         "else JSCompiler_object_inline_x_0=y," +
         "     JSCompiler_object_inline_y_1=void 0," +
         "     true;" +
         "f(JSCompiler_object_inline_x_0)");
    testLocal("var a = {x:x,y:y}; var b = {x:y}; c ? f(a.x) : f(b.x);",
         "var JSCompiler_object_inline_x_0 = x; " +
         "var JSCompiler_object_inline_y_1 = y; " +
         "var JSCompiler_object_inline_x_2 = y; " +
         "c ? f(JSCompiler_object_inline_x_0):f(JSCompiler_object_inline_x_2)");
  }

  @Test
  public void testObject9() {
    // There is a call, so no inlining
    testSameLocal("function f(a,b) {" +
             "  var x = {a:a,b:b}; x.a(); return x.b;" +
             "}");

    testLocal("function f(a,b) {" +
         "  var x = {a:a,b:b}; g(x.a); x = {a:a,b:2}; return x.b;" +
         "}",
         "function f(a,b) {" +
         "  var JSCompiler_object_inline_a_0 = a;" +
         "  var JSCompiler_object_inline_b_1 = b;" +
         "  g(JSCompiler_object_inline_a_0);" +
         "  JSCompiler_object_inline_a_0 = a," +
         "  JSCompiler_object_inline_b_1=2," +
         "  true;" +
         "  return JSCompiler_object_inline_b_1" +
         "}");

    testLocal("function f(a,b) { " +
         "  var x = {a:a,b:b}; g(x.a); x.b = x.c = 2; return x.b; " +
         "}",
         "function f(a,b) { " +
         "  var JSCompiler_object_inline_a_0=a;" +
         "  var JSCompiler_object_inline_b_1=b; " +
         "  var JSCompiler_object_inline_c_2;" +
         "  g(JSCompiler_object_inline_a_0);" +
         "  JSCompiler_object_inline_b_1=JSCompiler_object_inline_c_2=2;" +
         "  return JSCompiler_object_inline_b_1" +
         "}");
  }

  @Test
  public void testObject10() {
    testLocal("var x; var b = f(); x = {a:a, b:b}; if(x.a) g(x.b);",
         "var JSCompiler_object_inline_a_0;" +
         "var JSCompiler_object_inline_b_1;" +
         "var b = f();" +
         "JSCompiler_object_inline_a_0=a,JSCompiler_object_inline_b_1=b,true;" +
         "if(JSCompiler_object_inline_a_0) g(JSCompiler_object_inline_b_1)");
    testSameLocal("var x = {}; var b = f(); x = {a:a, b:b}; if(x.a) g(x.b) + x.c");
    testLocal("var x; var b = f(); x = {a:a, b:b}; x.c = c; if(x.a) g(x.b) + x.c",
         "var JSCompiler_object_inline_a_0;" +
         "var JSCompiler_object_inline_b_1;" +
         "var JSCompiler_object_inline_c_2;" +
         "var b = f();" +
         "JSCompiler_object_inline_a_0 = a,JSCompiler_object_inline_b_1 = b, " +
         "  JSCompiler_object_inline_c_2=void 0,true;" +
         "JSCompiler_object_inline_c_2 = c;" +
         "if (JSCompiler_object_inline_a_0)" +
         "  g(JSCompiler_object_inline_b_1) + JSCompiler_object_inline_c_2;");
    testLocal("var x = {a:a}; if (b) x={b:b}; f(x.a||x.b);",
         "var JSCompiler_object_inline_a_0 = a;" +
         "var JSCompiler_object_inline_b_1;" +
         "if(b) JSCompiler_object_inline_b_1 = b," +
         "      JSCompiler_object_inline_a_0 = void 0," +
         "      true;" +
         "f(JSCompiler_object_inline_a_0 || JSCompiler_object_inline_b_1)");
    testLocal("var x; var y = 5; x = {a:a, b:b, c:c}; if (b) x={b:b}; f(x.a||x.b);",
         "var JSCompiler_object_inline_a_0;" +
         "var JSCompiler_object_inline_b_1;" +
         "var JSCompiler_object_inline_c_2;" +
         "var y=5;" +
         "JSCompiler_object_inline_a_0=a," +
         "JSCompiler_object_inline_b_1=b," +
         "JSCompiler_object_inline_c_2=c," +
         "true;" +
         "if (b) JSCompiler_object_inline_b_1=b," +
         "       JSCompiler_object_inline_a_0=void 0," +
         "       JSCompiler_object_inline_c_2=void 0," +
         "       true;" +
         "f(JSCompiler_object_inline_a_0||JSCompiler_object_inline_b_1)");
  }

  @Test
  public void testObject11() {
    testSameLocal("var x = {a:b}; (x = {a:a}).c = 5; f(x.a);");
    testSameLocal("var x = {a:a}; f(x[a]); g(x[a]);");
  }

  @Test
  public void testObject12() {
    testSameLocal("var a; a = {x:1, y:2}; f(a.x, a.y2);");
  }

  @Test
  public void testObject13() {
    testSameLocal("var x = {a:1, b:2}; x = {a:3, b:x.a};");
  }

  @Test
  public void testObject14() {
    testSameLocal("var x = {a:1}; if ('a' in x) { f(); }");
    testSameLocal("var x = {a:1}; for (var y in x) { f(y); }");
  }

  @Test
  public void testObject15() {
    testSameLocal("x = x || {}; f(x.a);");
  }

  @Test
  public void testObject16() {
    testLocal("function f(e) { bar(); x = {a: foo()}; var x; print(x.a); }",
         "function f(e) { " +
         "  var JSCompiler_object_inline_a_0;" +
         "  bar();" +
         "  JSCompiler_object_inline_a_0 = foo(), true;" +
         "  print(JSCompiler_object_inline_a_0);" +
         "}");
  }

  @Test
  public void testObject17() {
    // Note: Some day, with careful analysis, these two uses could be
    // disambiguated, and the second assignment could be inlined.
    testSameLocal(
      "var a = {a: function(){}};" +
      "a.a();" +
      "a = {a1: 100};" +
      "print(a.a1);");
  }

  @Test
  public void testObject18() {
    testSameLocal("var a,b; b=a={x:x, y:y}; f(b.x);");
  }

  @Test
  public void testObject19() {
    testSameLocal("var a,b; if(c) { b=a={x:x, y:y}; } else { b=a={x:y}; } f(b.x);");
  }

  @Test
  public void testObject20() {
    testSameLocal("var a,b; if(c) { b=a={x:x, y:y}; } else { b=a={x:y}; } f(a.x);");
  }

  @Test
  public void testObject21() {
    testSameLocal("var a,b; b=a={x:x, y:y};");
    testSameLocal("var a,b; if(c) { b=a={x:x, y:y}; }" +
             "else { b=a={x:y}; } f(a.x); f(b.x)");
    testSameLocal("var a, b; if(c) { if (a={x:x, y:y}) f(); } " +
             "else { b=a={x:y}; } f(a.x);");
    testSameLocal("var a,b; b = (a = {x:x, y:x});");
    testSameLocal("var a,b; a = {x:x, y:x}; b = a");
    testSameLocal("var a,b; a = {x:x, y:x}; b = x || a");
    testSameLocal("var a,b; a = {x:x, y:x}; b = y && a");
    testSameLocal("var a,b; a = {x:x, y:x}; b = y ? a : a");
    testSameLocal("var a,b; a = {x:x, y:x}; b = y , a");
    testSameLocal("b = x || (a = {x:1, y:2});");
  }

  @Test
  public void testObject22() {
    testLocal("while(1) { var a = {y:1}; if (b) a.x = 2; f(a.y, a.x);}",
      "for(;1;){" +
      " var JSCompiler_object_inline_y_0=1;" +
      " var JSCompiler_object_inline_x_1;" +
      " if(b) JSCompiler_object_inline_x_1=2;" +
      " f(JSCompiler_object_inline_y_0,JSCompiler_object_inline_x_1)" +
      "}");

    testSameLocal("var a; while (1) { f(a.x, a.y); a = {x:1, y:1};}");
  }

  @Test
  public void testObject23() {
    testLocal("function f() {\n" +
         "  var templateData = {\n" +
         "    linkIds: {\n" +
         "      CHROME: 'cl',\n" +
         "      DISMISS: 'd'\n" +
         "    }\n" +
         "  };\n" +
         "  var html = templateData.linkIds.CHROME \n" +
         "       + \":\" + templateData.linkIds.DISMISS;\n" +
         "}",
         "function f(){" +
         "var JSCompiler_object_inline_CHROME_1='cl';" +
         "var JSCompiler_object_inline_DISMISS_2='d';" +
         "var html=JSCompiler_object_inline_CHROME_1 +" +
         " ':' +JSCompiler_object_inline_DISMISS_2}");
  }

  @Test
  public void testObject24() {
    testLocal("function f() {\n" +
         "  var linkIds = {\n" +
         "      CHROME: 1,\n" +
         "  };\n" +
         "  var g = function () {var o = {a: linkIds};}\n" +
         "}",
         "function f(){var linkIds={CHROME:1};" +
         "var g=function(){var JSCompiler_object_inline_a_0=linkIds}}");
  }

  @Test
  public void testObject25() {
    testLocal("var a = {x:f(), y:g()}; a = {y:g(), x:f()}; f(a.x, a.y);",
         "var JSCompiler_object_inline_x_0=f();" +
         "var JSCompiler_object_inline_y_1=g();" +
         "JSCompiler_object_inline_y_1=g()," +
         "  JSCompiler_object_inline_x_0=f()," +
         "  true;" +
         "f(JSCompiler_object_inline_x_0,JSCompiler_object_inline_y_1)");
  }

  @Test
  public void testObject26() {
    testLocal("var a = {}; a.b = function() {}; new a.b.c",
         "var JSCompiler_object_inline_b_0;" +
         "JSCompiler_object_inline_b_0=function(){};" +
         "new JSCompiler_object_inline_b_0.c");
  }

  @Test
  public void testInlineObjectWithLet() {
    testLocal(
        "let a = {x:x(), y:y()}; f(a.x, a.y);",
        lines(
            "var JSCompiler_object_inline_x_0=x();",
            "var JSCompiler_object_inline_y_1=y();",
            "f(JSCompiler_object_inline_x_0, JSCompiler_object_inline_y_1);"));
  }

  @Test
  public void testInlineObjectWithConst() {
    testLocal(
        "const a = {x:x(), y:y()}; f(a.x, a.y);",
        "var JSCompiler_object_inline_x_0=x();"
            + "var JSCompiler_object_inline_y_1=y();"
            + "f(JSCompiler_object_inline_x_0, JSCompiler_object_inline_y_1);");
  }

  @Test
  public void testDontInlineLetInForLoopInit() {
    // Handling this case should be possible, but we don't currently have that logic in place.
    testSameLocal("var i; for(let a = {x:x(), y:y()}; i < 0; i++) { f(a.x, a.y); }");
  }

  @Test
  public void testDontInlineConstInForLoopInit() {
    testSameLocal("var i; for(const a = {x:x(), y:y()}; i < 0; i++) { f(a.x, a.y); }");
  }

  @Test
  public void testDoInlineObjectWithVarInForLoop() {
    // The Normalize pass actually moves "var a = {x: x(), y: y()}" out of the for loop initializer
    // before this pass runs, which is why this works.
    testLocal(
        "var i; for(var a = {x:x(), y:y()}; i < 0; i++) { f(a.x, a.y); }",
        "var i;"
            + "var JSCompiler_object_inline_x_0=x();"
            + "var JSCompiler_object_inline_y_1=y();"
            + "for(; i < 0; i++) {"
            + "f(JSCompiler_object_inline_x_0, JSCompiler_object_inline_y_1);"
            + "}");
  }

  @Test
  public void testBug545a() {
    testLocal("var a = {}", "");
  }

  @Test
  public void testBug545b() {
    testLocal("var a; a = {}", "true");
  }

  @Test
  public void testIssue724() {
    testSameLocal(
        "var getType; getType = {};" +
        "return functionToCheck && " +
        "   getType.toString.apply(functionToCheck) === " +
        "   '[object Function]';");
  }

  @Test
  public void testNoInlineDeletedProperties() {
    testSameLocal(
        "var foo = {bar:1};" +
        "delete foo.bar;" +
        "return foo.bar;");
  }

  @Test
  public void testProto() {
    testSameLocal(
        lines(
            "var protoObject = {",
            "  f: function() {",
            "    return 1;",
            "  }",
            "};",
            "var object = {",
            "  __proto__: protoObject,",
            "};",
            "g(object.f);"));

    testSame(
        externs(
            lines(
                "var protoObject = {",
                "  f: function() {",
                "    return 1;",
                "  }",
                "};",
                "var object = {",
                "  __proto__: protoObject,",
                "  g: false",
                "};",
                "g(object.g);")),
        srcs(
            lines(
                "var protoObject = {",
                "  f: function() {",
                "    return 1;",
                "  }",
                "};",
                "var JSCompiler_object_inline___proto___0=protoObject;",
                "var JSCompiler_object_inline_g_1=false;",
                "g(JSCompiler_object_inline_g_1)")));
  }

  @Test
  public void testSuper() {
    testSameLocal(
        lines(
            "var superObject = {",
            "  f() {",
            "    return 1;",
            "  }",
            "};",
            "var object = {",
            "  __proto__: superObject,",
            "  f() {",
            "    return super.f();",
            "  }",
            "}",
            "g(object.f());"));
  }

  @Test
  public void testShorthandFunctions() {
    testSameLocal(
        lines(
            "var object = {",
            "  items: [],",
            "  add(item) {",
            "    this.items.push(item);",
            "  },",
            "};",
            "object.add(1);"));

    testSameLocal(
        lines(
            "var object = {", "  one() {", "    return 1", "  },", "};", "object.one();"));
  }

  @Test
  public void testShorthandAssignments() {
    testLocal(
        lines(
            "var object = {",
            "  x,",
            "  y",
            "};",
            "f(object.x, object.y);"),
        lines(
            "var JSCompiler_object_inline_x_0=x;",
            "var JSCompiler_object_inline_y_1=y;",
            "f(JSCompiler_object_inline_x_0,JSCompiler_object_inline_y_1)"));

    testLocal(
        lines(
            "var object = {",
            "  x,",
            "};",
            "object.y = y",
            "f(object.x, object.y);"),
        lines(
            "var JSCompiler_object_inline_x_0=x;",
            "var JSCompiler_object_inline_y_1;",
            "var JSCompiler_object_inline_y_1=y;",
            "f(JSCompiler_object_inline_x_0,JSCompiler_object_inline_y_1)"));
  }

  @Test
  public void testComputedPropertyName() {
    testSameLocal(
        lines(
            "function addBar(name) {",
            "  return name + 'Bar'",
            "}",
            "var object = {",
            "  [addBar(\"foo\")]: 1",
            "};"));

    testSameLocal(
        lines(
            "var sym = Symbol('key');",
            "var object = {",
            "  [sym]: 1,",
            "  x: true",
            "}",
            "use(object[sym]);"));
  }

  @Test
  public void testQuotedKeyThatIsNotRead() {
    testLocal("var obj = {'a.b.c': 'd'};", "var JSCompiler_object_inline_string_key_0 = 'd';");
    testLocal("var obj = {'@': 5, '!': 4, 'foo': 3};",
        lines(
            "var JSCompiler_object_inline_string_key_0 = 5;",
            "var JSCompiler_object_inline_string_key_1 = 4;",
            "var JSCompiler_object_inline_foo_2 = 3;"
        ));

    testSameLocal("var obj = {}; obj['@'] = 3;");
  }

  @Test
  public void testQuotedKeyThatIsRead() {
    testSameLocal("var obj = {'a.b.c': 'd'}; use(obj['a.b.c']);");

    testSameLocal("var obj = {}; obj['a'] = 3; use(obj['a']);");
  }

  private static final String LOCAL_PREFIX = "function local(){";
  private static final String LOCAL_POSTFIX = "}";

  private void testLocal(String code, String result) {
    test(LOCAL_PREFIX + code + LOCAL_POSTFIX,
         LOCAL_PREFIX + result + LOCAL_POSTFIX);
  }

  private void testSameLocal(String code) {
    testSame(LOCAL_PREFIX + code + LOCAL_POSTFIX);
  }
}
