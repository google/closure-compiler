/*
 * Copyright 2008 The Closure Compiler Authors.
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
 * Verifies that valid candidates for inlining are inlined, but
 * that no dangerous inlining occurs.
 *
 * @author kushal@google.com (Kushal Dave)
 */
public class InlineVariablesTest extends CompilerTestCase {

  private boolean inlineAllStrings = false;
  private boolean inlineLocalsOnly = false;

  public InlineVariablesTest() {
    enableNormalize();
  }

  @Override
  public void setUp() {
    super.enableLineNumberCheck(true);
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new InlineVariables(
        compiler,
        (inlineLocalsOnly)
            ? InlineVariables.Mode.LOCALS_ONLY
            : InlineVariables.Mode.ALL,
        inlineAllStrings);
  }

  @Override
  public void tearDown() {
    inlineAllStrings = false;
    inlineLocalsOnly = false;
  }

  // Test respect for scopes and blocks

  public void testInlineGlobal() {
    test("var x = 1; var z = x;", "var z = 1;");
  }

  public void testNoInlineExportedName() {
    testSame("var _x = 1; var z = _x;");
  }

  public void testNoInlineExportedName2() {
    testSame("var f = function() {}; var _x = f;" +
             "var y = function() { _x(); }; var _y = f;");
  }

  public void testDoNotInlineIncrement() {
    testSame("var x = 1; x++;");
  }

  public void testDoNotInlineDecrement() {
    testSame("var x = 1; x--;");
  }

  public void testDoNotInlineIntoLhsOfAssign() {
    testSame("var x = 1; x += 3;");
  }

  public void testInlineIntoRhsOfAssign() {
    test("var x = 1; var y = x;", "var y = 1;");
  }

  public void testInlineInFunction() {
    test("function baz() { var x = 1; var z = x; }",
        "function baz() { var z = 1; }");
  }

  public void testInlineInFunction2() {
    test("function baz() { " +
            "var a = new obj();"+
            "result = a;" +
         "}",
         "function baz() { " +
            "result = new obj()" +
         "}");
  }

  public void testInlineInFunction3() {
    testSame(
        "function baz() { " +
           "var a = new obj();" +
           "(function(){a;})();" +
           "result = a;" +
        "}");
  }

  public void testInlineInFunction4() {
    testSame(
        "function baz() { " +
           "var a = new obj();" +
           "foo.result = a;" +
        "}");
  }

  public void testInlineInFunction5() {
    testSame(
        "function baz() { " +
           "var a = (foo = new obj());" +
           "foo.x();" +
           "result = a;" +
        "}");
  }

  public void testInlineAcrossModules() {
    // TODO(kushal): Make decision about overlap with CrossModuleCodeMotion
    test(createModules("var a = 2;", "var b = a;"),
        new String[] { "", "var b = 2;" });
  }

  public void testDoNotExitConditional1() {
    testSame("if (true) { var x = 1; } var z = x;");
  }

  public void testDoNotExitConditional2() {
    testSame("if (true) var x = 1; var z = x;");
  }


  public void testDoNotExitConditional3() {
    testSame("var x; if (true) x=1; var z = x;");
  }

  public void testDoNotExitLoop() {
    testSame("while (z) { var x = 3; } var y = x;");
  }

  public void testDoNotExitForLoop() {
    test("for (var i = 1; false; false) var z = i;",
         "for (;false;false) var z = 1;");
    testSame("for (; false; false) var i = 1; var z = i;");
    testSame("for (var i in {}); var z = i;");
  }

  public void testDoNotEnterSubscope() {
    testSame(
        "var x = function() {" +
        "  var self = this; " +
        "  return function() { var y = self; };" +
        "}");
    testSame(
        "var x = function() {" +
        "  var y = [1]; " +
        "  return function() { var z = y; };" +
        "}");
  }

  public void testDoNotExitTry() {
    testSame("try { var x = y; } catch (e) {} var z = y; ");
    testSame("try { throw e; var x = 1; } catch (e) {} var z = x; ");
  }

  public void testDoNotEnterCatch() {
    testSame("try { } catch (e) { var z = e; } ");
  }

  public void testDoNotEnterFinally() {
    testSame("try { throw e; var x = 1; } catch (e) {} " +
             "finally  { var z = x; } ");
  }

  public void testInsideIfConditional() {
    test("var a = foo(); if (a) { alert(3); }", "if (foo()) { alert(3); }");
    test("var a; a = foo(); if (a) { alert(3); }", "if (foo()) { alert(3); }");
  }

  public void testOnlyReadAtInitialization() {
    test("var a; a = foo();", "foo();");
    test("var a; if (a = foo()) { alert(3); }", "if (foo()) { alert(3); }");
    test("var a; switch (a = foo()) {}", "switch(foo()) {}");
    test("var a; function f(){ return a = foo(); }",
         "function f(){ return foo(); }");
    test("function f(){ var a; return a = foo(); }",
         "function f(){ return foo(); }");
    test("var a; with (a = foo()) { alert(3); }", "with (foo()) { alert(3); }");

    test("var a; b = (a = foo());", "b = foo();");
    test("var a; while(a = foo()) { alert(3); }",
         "while(foo()) { alert(3); }");
    test("var a; for(;a = foo();) { alert(3); }",
         "for(;foo();) { alert(3); }");
    test("var a; do {} while(a = foo()) { alert(3); }",
         "do {} while(foo()) { alert(3); }");
  }

  public void testImmutableWithSingleReferenceAfterInitialzation() {
    test("var a; a = 1;", "1;");
    test("var a; if (a = 1) { alert(3); }", "if (1) { alert(3); }");
    test("var a; switch (a = 1) {}", "switch(1) {}");
    test("var a; function f(){ return a = 1; }",
         "function f(){ return 1; }");
    test("function f(){ var a; return a = 1; }",
         "function f(){ return 1; }");
    test("var a; with (a = 1) { alert(3); }", "with (1) { alert(3); }");

    test("var a; b = (a = 1);", "b = 1;");
    test("var a; while(a = 1) { alert(3); }",
         "while(1) { alert(3); }");
    test("var a; for(;a = 1;) { alert(3); }",
         "for(;1;) { alert(3); }");
    test("var a; do {} while(a = 1) { alert(3); }",
         "do {} while(1) { alert(3); }");
  }

  public void testSingleReferenceAfterInitialzation() {
    test("var a; a = foo();a;", "foo();");
    testSame("var a; if (a = foo()) { alert(3); } a;");
    testSame("var a; switch (a = foo()) {} a;");
    testSame("var a; function f(){ return a = foo(); } a;");
    testSame("function f(){ var a; return a = foo(); a;}");
    testSame("var a; with (a = foo()) { alert(3); } a;");
    testSame("var a; b = (a = foo()); a;");
    testSame("var a; while(a = foo()) { alert(3); } a;");
    testSame("var a; for(;a = foo();) { alert(3); } a;");
    testSame("var a; do {} while(a = foo()) { alert(3); } a;");
  }

  public void testInsideIfBranch() {
    testSame("var a = foo(); if (1) { alert(a); }");
  }

  public void testInsideAndConditional() {
    test("var a = foo(); a && alert(3);", "foo() && alert(3);");
  }

  public void testInsideAndBranch() {
    testSame("var a = foo(); 1 && alert(a);");
  }

  public void testInsideOrBranch() {
    testSame("var a = foo(); 1 || alert(a);");
  }

  public void testInsideHookBranch() {
    testSame("var a = foo(); 1 ? alert(a) : alert(3)");
  }

  public void testInsideHookConditional() {
    test("var a = foo(); a ? alert(1) : alert(3)",
         "foo() ? alert(1) : alert(3)");
  }

  public void testInsideOrBranchInsideIfConditional() {
    testSame("var a = foo(); if (x || a) {}");
  }

  public void testInsideOrBranchInsideIfConditionalWithConstant() {
    // We don't inline non-immutable constants into branches.
    testSame("var a = [false]; if (x || a) {}");
  }

  public void testCrossFunctionsAsLeftLeaves() {
    // Ensures getNext() understands how to walk past a function leaf
    test(
        new String[] { "var x = function() {};", "",
            "function cow() {} var z = x;"},
        new String[] { "", "", "function cow() {} var z = function() {};" });
    test(
        new String[] { "var x = function() {};", "",
            "var cow = function() {}; var z = x;"},
        new String[] { "", "",
            "var cow = function() {}; var z = function() {};" });
    testSame(
        new String[] { "var x = a;", "",
            "(function() { a++; })(); var z = x;"});
    test(
        new String[] { "var x = a;", "",
            "function cow() { a++; }; cow(); var z = x;"},
        new String[] { "var x = a;", "",
            ";(function cow(){ a++; })(); var z = x;"});
    testSame(
        new String[] { "var x = a;", "",
            "cow(); var z = x; function cow() { a++; };"});
  }

  // Test movement of constant values

  public void testDoCrossFunction() {
    // We know foo() does not affect x because we require that x is only
    // referenced twice.
    test("var x = 1; foo(); var z = x;", "foo(); var z = 1;");
  }

  public void testDoNotCrossReferencingFunction() {
    testSame(
        "var f = function() { var z = x; };" +
        "var x = 1;" +
        "f();" +
        "var z = x;" +
        "f();");
  }


  // Test tricky declarations and references

  public void testChainedAssignment() {
    test("var a = 2, b = 2; var c = b;", "var a = 2; var c = 2;");
    test("var a = 2, b = 2; var c = a;", "var b = 2; var c = 2;");
    test("var a = b = 2; var f = 3; var c = a;", "var f = 3; var c = b = 2;");
    testSame("var a = b = 2; var c = b;");
  }

  public void testForIn() {
    testSame("for (var i in j) { var c = i; }");
    testSame("var i = 0; for (i in j) ;");
    testSame("var i = 0; for (i in j) { var c = i; }");
    testSame("i = 0; for (var i in j) { var c = i; }");
    testSame("var j = {'key':'value'}; for (var i in j) {print(i)};");
  }

  // Test movement of values that have (may) side effects

  public void testDoCrossNewVariables() {
    test("var x = foo(); var z = x;", "var z = foo();");
  }

  public void testDoNotCrossFunctionCalls() {
    testSame("var x = foo(); bar(); var z = x;");
  }


  // Test movement of values that are complex but lack side effects

  public void testDoNotCrossAssignment() {
    testSame("var x = {}; var y = x.a; x.a = 1; var z = y;");
    testSame("var a = this.id; foo(this.id = 3, a);");
  }

  public void testDoNotCrossDelete() {
    testSame("var x = {}; var y = x.a; delete x.a; var z = y;");
  }

  public void testDoNotCrossAssignmentPlus() {
    testSame("var a = b; b += 2; var c = a;");
  }

  public void testDoNotCrossIncrement() {
    testSame("var a = b.c; b.c++; var d = a;");
  }

  public void testDoNotCrossConstructor() {
    testSame("var a = b; new Foo(); var c = a;");
  }

  public void testDoCrossVar() {
    // Assumes we do not rely on undefined variables (not technically correct!)
    test("var a = b; var b = 3; alert(a)", "alert(3);");
  }

  public void testOverlappingInlines() {
    String source =
        "a = function(el, x, opt_y) { " +
        "  var cur = bar(el); " +
        "  opt_y = x.y; " +
        "  x = x.x; " +
        "  var dx = x - cur.x; " +
        "  var dy = opt_y - cur.y;" +
        "  foo(el, el.offsetLeft + dx, el.offsetTop + dy); " +
        "};";
    String expected =
      "a = function(el, x, opt_y) { " +
      "  var cur = bar(el); " +
      "  opt_y = x.y; " +
      "  x = x.x; " +
      "  foo(el, el.offsetLeft + (x - cur.x)," +
      "      el.offsetTop + (opt_y - cur.y)); " +
      "};";

    test(source, expected);
  }

  public void testOverlappingInlineFunctions() {
    String source =
        "a = function() { " +
        "  var b = function(args) {var n;}; " +
        "  var c = function(args) {}; " +
        "  d(b,c); " +
        "};";
    String expected =
      "a = function() { " +
      "  d(function(args){var n;}, function(args){}); " +
      "};";

    test(source, expected);
  }

  public void testInlineIntoLoops() {
    test("var x = true; while (true) alert(x);",
         "while (true) alert(true);");
    test("var x = true; while (true) for (var i in {}) alert(x);",
         "while (true) for (var i in {}) alert(true);");
    testSame("var x = [true]; while (true) alert(x);");
  }

  public void testInlineIntoFunction() {
    test("var x = false; var f = function() { alert(x); };",
         "var f = function() { alert(false); };");
    testSame("var x = [false]; var f = function() { alert(x); };");
  }

  public void testNoInlineIntoNamedFunction() {
    testSame("f(); var x = false; function f() { alert(x); };");
  }

  public void testInlineIntoNestedNonHoistedNamedFunctions() {
    test("f(); var x = false; if (false) function f() { alert(x); };",
         "f(); if (false) function f() { alert(false); };");
  }

  public void testNoInlineIntoNestedNamedFunctions() {
    testSame("f(); var x = false; function f() { if (false) { alert(x); } };");
  }

  public void testNoInlineMutatedVariable() {
    testSame("var x = false; if (true) { var y = x; x = true; }");
  }

  public void testInlineImmutableMultipleTimes() {
    test("var x = null; var y = x, z = x;",
         "var y = null, z = null;");
    test("var x = 3; var y = x, z = x;",
         "var y = 3, z = 3;");
  }

  public void testNoInlineStringMultipleTimesIfNotWorthwhile() {
    testSame("var x = 'abcdefghijklmnopqrstuvwxyz'; var y = x, z = x;");
  }

  public void testInlineStringMultipleTimesWhenAliasingAllStrings() {
    inlineAllStrings = true;
    test("var x = 'abcdefghijklmnopqrstuvwxyz'; var y = x, z = x;",
         "var y = 'abcdefghijklmnopqrstuvwxyz', " +
         "    z = 'abcdefghijklmnopqrstuvwxyz';");
  }

  public void testNoInlineBackwards() {
    testSame("var y = x; var x = null;");
  }

  public void testNoInlineOutOfBranch() {
    testSame("if (true) var x = null; var y = x;");
  }

  public void testInterferingInlines() {
    test("var a = 3; var f = function() { var x = a; alert(x); };",
         "var f = function() { alert(3); };");
  }

  public void testInlineIntoTryCatch() {
    test("var a = true; " +
         "try { var b = a; } " +
         "catch (e) { var c = a + b; var d = true; } " +
         "finally { var f = a + b + c + d; }",
         "try { var b = true; } " +
         "catch (e) { var c = true + b; var d = true; } " +
         "finally { var f = true + b + c + d; }");
  }

  // Make sure that we still inline constants that are not provably
  // written before they're read.
  public void testInlineConstants() {
    test("function foo() { return XXX; } var XXX = true;",
         "function foo() { return true; }");
  }

  public void testInlineStringWhenWorthwhile() {
    test("var x = 'a'; foo(x, x, x);", "foo('a', 'a', 'a');");
  }

  public void testInlineConstantAlias() {
    test("var XXX = new Foo(); q(XXX); var YYY = XXX; bar(YYY)",
         "var XXX = new Foo(); q(XXX); bar(XXX)");
  }

  public void testInlineConstantAliasWithAnnotation() {
    test("/** @const */ var xxx = new Foo(); q(xxx); var YYY = xxx; bar(YYY)",
         "/** @const */ var xxx = new Foo(); q(xxx); bar(xxx)");
  }

  public void testInlineConstantAliasWithNonConstant() {
    test("var XXX = new Foo(); q(XXX); var y = XXX; bar(y); baz(y)",
         "var XXX = new Foo(); q(XXX); bar(XXX); baz(XXX)");
  }

  public void testCascadingInlines() {
    test("var XXX = 4; " +
         "function f() { var YYY = XXX; bar(YYY); baz(YYY); }",
         "function f() { bar(4); baz(4); }");
  }

  public void testNoInlineGetpropIntoCall() {
    test("var a = b; a();", "b();");
    test("var a = b.c; f(a);", "f(b.c);");
    testSame("var a = b.c; a();");
  }

  public void testInlineFunctionDeclaration() {
    test("var f = function () {}; var a = f;",
         "var a = function () {};");
    test("var f = function () {}; foo(); var a = f;",
         "foo(); var a = function () {};");
    test("var f = function () {}; foo(f);",
         "foo(function () {});");

    testSame("var f = function () {}; function g() {var a = f;}");
    testSame("var f = function () {}; function g() {h(f);}");
  }

  public void test2388531() {
    testSame("var f = function () {};" +
             "var g = function () {};" +
             "goog.inherits(f, g);");
    testSame("var f = function () {};" +
             "var g = function () {};" +
             "goog$inherits(f, g);");
  }

  public void testRecursiveFunction1() {
    testSame("var x = 0; (function x() { return x ? x() : 3; })();");
  }

  public void testRecursiveFunction2() {
    testSame("function y() { return y(); }");
  }

  public void testUnreferencedBleedingFunction() {
    testSame("var x = function y() {}");
  }

  public void testReferencedBleedingFunction() {
    testSame("var x = function y() { return y(); }");
  }

  public void testInlineAliases1() {
    test("var x = this.foo(); this.bar(); var y = x; this.baz(y);",
         "var x = this.foo(); this.bar(); this.baz(x);");
  }

  public void testInlineAliases1b() {
    test("var x = this.foo(); this.bar(); var y; y = x; this.baz(y);",
         "var x = this.foo(); this.bar(); x; this.baz(x);");
  }

  public void testInlineAliases1c() {
    test("var x; x = this.foo(); this.bar(); var y = x; this.baz(y);",
         "var x; x = this.foo(); this.bar(); this.baz(x);");
  }

  public void testInlineAliases1d() {
    test("var x; x = this.foo(); this.bar(); var y; y = x; this.baz(y);",
         "var x; x = this.foo(); this.bar(); x; this.baz(x);");
  }

  public void testInlineAliases2() {
    test("var x = this.foo(); this.bar(); " +
         "function f() { var y = x; this.baz(y); }",
         "var x = this.foo(); this.bar(); function f() { this.baz(x); }");
  }

  public void testInlineAliases2b() {
    test("var x = this.foo(); this.bar(); " +
         "function f() { var y; y = x; this.baz(y); }",
         "var x = this.foo(); this.bar(); function f() { this.baz(x); }");
  }

  public void testInlineAliases2c() {
    test("var x; x = this.foo(); this.bar(); " +
         "function f() { var y = x; this.baz(y); }",
         "var x; x = this.foo(); this.bar(); function f() { this.baz(x); }");
  }

  public void testInlineAliases2d() {
    test("var x; x = this.foo(); this.bar(); " +
         "function f() { var y; y = x; this.baz(y); }",
         "var x; x = this.foo(); this.bar(); function f() { this.baz(x); }");
  }

  public void testInlineAliasesInLoop() {
    test(
        "function f() { " +
        "  var x = extern();" +
        "  for (var i = 0; i < 5; i++) {" +
        "    (function() {" +
        "       var y = x; window.setTimeout(function() { extern(y); }, 0);" +
        "     })();" +
        "  }" +
        "}",
        "function f() { " +
        "  var x = extern();" +
        "  for (var i = 0; i < 5; i++) {" +
        "    (function() {" +
        "       window.setTimeout(function() { extern(x); }, 0);" +
        "     })();" +
        "  }" +
        "}");
  }

  public void testNoInlineAliasesInLoop() {
    testSame(
        "function f() { " +
        "  for (var i = 0; i < 5; i++) {" +
        "    var x = extern();" +
        "    (function() {" +
        "       var y = x; window.setTimeout(function() { extern(y); }, 0);" +
        "     })();" +
        "  }" +
        "}");
  }

  public void testNoInlineAliases1() {
    testSame(
        "var x = this.foo(); this.bar(); var y = x; x = 3; this.baz(y);");
  }

  public void testNoInlineAliases1b() {
    testSame(
        "var x = this.foo(); this.bar(); var y; y = x; x = 3; this.baz(y);");
  }

  public void testNoInlineAliases2() {
    testSame(
        "var x = this.foo(); this.bar(); var y = x; y = 3; this.baz(y); ");
  }

  public void testNoInlineAliases2b() {
    testSame(
        "var x = this.foo(); this.bar(); var y; y = x; y = 3; this.baz(y); ");
  }

  public void testNoInlineAliases3() {
    testSame(
         "var x = this.foo(); this.bar(); " +
         "function f() { var y = x; g(); this.baz(y); } " +
         "function g() { x = 3; }");
  }

  public void testNoInlineAliases3b() {
    testSame(
         "var x = this.foo(); this.bar(); " +
         "function f() { var y; y = x; g(); this.baz(y); } " +
         "function g() { x = 3; }");
  }

  public void testNoInlineAliases4() {
    testSame(
         "var x = this.foo(); this.bar(); " +
         "function f() { var y = x; y = 3; this.baz(y); }");
  }

  public void testNoInlineAliases4b() {
    testSame(
         "var x = this.foo(); this.bar(); " +
         "function f() { var y; y = x; y = 3; this.baz(y); }");
  }

  public void testNoInlineAliases5() {
    testSame(
        "var x = this.foo(); this.bar(); var y = x; this.bing();" +
        "this.baz(y); x = 3;");
  }

  public void testNoInlineAliases5b() {
    testSame(
        "var x = this.foo(); this.bar(); var y; y = x; this.bing();" +
        "this.baz(y); x = 3;");
  }

  public void testNoInlineAliases6() {
    testSame(
        "var x = this.foo(); this.bar(); var y = x; this.bing();" +
        "this.baz(y); y = 3;");
  }

  public void testNoInlineAliases6b() {
    testSame(
        "var x = this.foo(); this.bar(); var y; y = x; this.bing();" +
        "this.baz(y); y = 3;");
  }

  public void testNoInlineAliases7() {
    testSame(
         "var x = this.foo(); this.bar(); " +
         "function f() { var y = x; this.bing(); this.baz(y); x = 3; }");
  }

  public void testNoInlineAliases7b() {
    testSame(
         "var x = this.foo(); this.bar(); " +
         "function f() { var y; y = x; this.bing(); this.baz(y); x = 3; }");
  }

  public void testNoInlineAliases8() {
    testSame(
         "var x = this.foo(); this.bar(); " +
         "function f() { var y = x; this.baz(y); y = 3; }");
  }

  public void testNoInlineAliases8b() {
    testSame(
         "var x = this.foo(); this.bar(); " +
         "function f() { var y; y = x; this.baz(y); y = 3; }");
  }

  public void testSideEffectOrder() {
    // z can not be changed by the call to y, so x can be inlined.
    String EXTERNS = "var z; function f(){}";
    test(EXTERNS,
         "var x = f(y.a, y); z = x;",
         "z = f(y.a, y);", null, null);
    // z.b can be changed by the call to y, so x can not be inlined.
    testSame(EXTERNS, "var x = f(y.a, y); z.b = x;", null, null);
  }

  public void testInlineParameterAlias1() {
    test(
      "function f(x) {" +
      "  var y = x;" +
      "  g();" +
      "  y;y;" +
      "}",
      "function f(x) {" +
      "  g();" +
      "  x;x;" +
      "}"
      );
  }

  public void testInlineParameterAlias2() {
    test(
      "function f(x) {" +
      "  var y; y = x;" +
      "  g();" +
      "  y;y;" +
      "}",
      "function f(x) {" +
      "  x;" +
      "  g();" +
      "  x;x;" +
      "}"
      );
  }

  public void testInlineFunctionAlias1a() {
    test(
      "function f(x) {}" +
      "var y = f;" +
      "g();" +
      "y();y();",
      "var y = function f(x) {};" +
      "g();" +
      "y();y();"
      );
  }

  public void testInlineFunctionAlias1b() {
    test(
      "function f(x) {};" +
      "f;var y = f;" +
      "g();" +
      "y();y();",
      "function f(x) {};" +
      "f;g();" +
      "f();f();"
      );
  }

  public void testInlineFunctionAlias2a() {
    test(
      "function f(x) {}" +
      "var y; y = f;" +
      "g();" +
      "y();y();",
      "var y; y = function f(x) {};" +
      "g();" +
      "y();y();"
      );
  }

  public void testInlineFunctionAlias2b() {
    test(
      "function f(x) {};" +
      "f; var y; y = f;" +
      "g();" +
      "y();y();",
      "function f(x) {};" +
      "f; f;" +
      "g();" +
      "f();f();"
      );
  }

  public void testInlineCatchAlias1() {
    test(
      "try {" +
      "} catch (e) {" +
      "  var y = e;" +
      "  g();" +
      "  y;y;" +
      "}",
      "try {" +
      "} catch (e) {" +
      "  g();" +
      "  e;e;" +
      "}"
      );
  }

  public void testInlineCatchAlias2() {
    test(
      "try {" +
      "} catch (e) {" +
      "  var y; y = e;" +
      "  g();" +
      "  y;y;" +
      "}",
      "try {" +
      "} catch (e) {" +
      "  e;" +
      "  g();" +
      "  e;e;" +
      "}"
      );
  }

  public void testLocalsOnly1() {
    inlineLocalsOnly = true;
    test(
        "var x=1; x; function f() {var x = 1; x;}",
        "var x=1; x; function f() {1;}");
  }

  public void testLocalsOnly2() {
    inlineLocalsOnly = true;
    test(
        "/** @const */\n" +
        "var X=1; X;\n" +
        "function f() {\n" +
        "  /** @const */\n" +
        "  var X = 1; X;\n" +
        "}",
        "var X=1; X; function f() {1;}");
  }

  public void testInlineUndefined1() {
    test("var x; x;",
         "void 0;");
  }

  public void testInlineUndefined2() {
    testSame("var x; x++;");
  }

  public void testInlineUndefined3() {
    testSame("var x; var x;");
  }

  public void testInlineUndefined4() {
    test("var x; x; x;",
         "void 0; void 0;");
  }

  public void testInlineUndefined5() {
    test("var x; for(x in a) {}",
         "var x; for(x in a) {}");
  }

  public void testIssue90() {
    test("var x; x && alert(1)",
         "void 0 && alert(1)");
  }

  public void testRenamePropertyFunction() {
    testSame("var JSCompiler_renameProperty; " +
             "JSCompiler_renameProperty('foo')");
  }

  public void testThisAlias() {
    test("function f() { var a = this; a.y(); a.z(); }",
         "function f() { this.y(); this.z(); }");
  }

  public void testThisEscapedAlias() {
    testSame(
        "function f() { var a = this; var g = function() { a.y(); }; a.z(); }");
  }

  public void testInlineNamedFunction() {
    test("function f() {} f();", "(function f(){})()");
  }

  public void testIssue378ModifiedArguments1() {
    testSame(
        "function g(callback) {\n" +
        "  var f = callback;\n" +
        "  arguments[0] = this;\n" +
        "  f.apply(this, arguments);\n" +
        "}");
  }

  public void testIssue378ModifiedArguments2() {
    testSame(
        "function g(callback) {\n" +
        "  /** @const */\n" +
        "  var f = callback;\n" +
        "  arguments[0] = this;\n" +
        "  f.apply(this, arguments);\n" +
        "}");
  }

  public void testIssue378EscapedArguments1() {
    testSame(
        "function g(callback) {\n" +
        "  var f = callback;\n" +
        "  h(arguments,this);\n" +
        "  f.apply(this, arguments);\n" +
        "}\n" +
        "function h(a,b) {\n" +
        "  a[0] = b;" +
        "}");
  }

  public void testIssue378EscapedArguments2() {
    testSame(
        "function g(callback) {\n" +
        "  /** @const */\n" +
        "  var f = callback;\n" +
        "  h(arguments,this);\n" +
        "  f.apply(this);\n" +
        "}\n" +
        "function h(a,b) {\n" +
        "  a[0] = b;" +
        "}");
  }

  public void testIssue378EscapedArguments3() {
    test(
        "function g(callback) {\n" +
        "  var f = callback;\n" +
        "  f.apply(this, arguments);\n" +
        "}\n",
        "function g(callback) {\n" +
        "  callback.apply(this, arguments);\n" +
        "}\n");
  }

  public void testIssue378EscapedArguments4() {
    testSame(
        "function g(callback) {\n" +
        "  var f = callback;\n" +
        "  h(arguments[0],this);\n" +
        "  f.apply(this, arguments);\n" +
        "}\n" +
        "function h(a,b) {\n" +
        "  a[0] = b;" +
        "}");
  }

  public void testIssue378ArgumentsRead1() {
    test(
        "function g(callback) {\n" +
        "  var f = callback;\n" +
        "  var g = arguments[0];\n" +
        "  f.apply(this, arguments);\n" +
        "}",
        "function g(callback) {\n" +
        "  var g = arguments[0];\n" +
        "  callback.apply(this, arguments);\n" +
        "}");
  }

  public void testIssue378ArgumentsRead2() {
    test(
        "function g(callback) {\n" +
        "  var f = callback;\n" +
        "  h(arguments[0],this);\n" +
        "  f.apply(this, arguments[0]);\n" +
        "}\n" +
        "function h(a,b) {\n" +
        "  a[0] = b;" +
        "}",
        "function g(callback) {\n" +
        "  h(arguments[0],this);\n" +
        "  callback.apply(this, arguments[0]);\n" +
        "}\n" +
        "function h(a,b) {\n" +
        "  a[0] = b;" +
        "}");
  }

  public void testArgumentsModifiedInOuterFunction() {
    test(
      "function g(callback) {\n" +
      "  var f = callback;\n" +
      "  arguments[0] = this;\n" +
      "  f.apply(this, arguments);\n" +
      "  function inner(callback) {" +
      "    var x = callback;\n" +
      "    x.apply(this);\n" +
      "  }" +
      "}",
      "function g(callback) {\n" +
      "  var f = callback;\n" +
      "  arguments[0] = this;\n" +
      "  f.apply(this, arguments);\n" +
      "  function inner(callback) {" +
      "    callback.apply(this);\n" +
      "  }" +
      "}");
  }

  public void testArgumentsModifiedInInnerFunction() {
    test(
      "function g(callback) {\n" +
      "  var f = callback;\n" +
      "  f.apply(this, arguments);\n" +
      "  function inner(callback) {" +
      "    var x = callback;\n" +
      "    arguments[0] = this;\n" +
      "    x.apply(this);\n" +
      "  }" +
      "}",
      "function g(callback) {\n" +
      "  callback.apply(this, arguments);\n" +
      "  function inner(callback) {" +
      "    var x = callback;\n" +
      "    arguments[0] = this;\n" +
      "    x.apply(this);\n" +
      "  }" +
      "}");
  }

  public void testNoInlineRedeclaredExterns() {
    String externs = "var test = 1;";
    String code = "/** @suppress {duplicate} */ var test = 2;alert(test);";
    test(externs, code, code, null, null);
  }

  public void testBug6598844() {
    testSame(
        "function F() { this.a = 0; }" +
        "F.prototype.inc = function() { this.a++; return 10; };" +
        "F.prototype.bar = function() { var val = inc(); this.a += val; };");
  }
}
