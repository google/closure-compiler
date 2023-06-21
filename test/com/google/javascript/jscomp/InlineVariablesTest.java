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

import com.google.javascript.jscomp.testing.JSChunkGraphBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Verifies that valid candidates for inlining are inlined, but that no dangerous inlining occurs.
 */
@RunWith(JUnit4.class)
public final class InlineVariablesTest extends CompilerTestCase {

  private boolean inlineLocalsOnly = false;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableNormalize();
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    disableCompareJsDoc();
    // NOTE: We are not enabling var checks here, so it is OK to use undeclared variables in
    // these tests. They will be treated as if they were externs.
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new InlineVariables(
        compiler, inlineLocalsOnly ? InlineVariables.Mode.LOCALS_ONLY : InlineVariables.Mode.ALL);
  }

  @Override
  @After
  public void tearDown() {
    inlineLocalsOnly = false;
  }

  @Test
  public void testArgAlias() {
    // same scope
    test(
        lines(
            "", //
            "function foo(arg) {",
            "  const argAlias = arg;",
            "  const argAliasAlias = argAlias",
            "  use(argAliasAlias);",
            "}",
            ""),
        lines(
            "", //
            "function foo(arg) {",
            "  use(arg);",
            "}",
            ""));
    // nested scope
    test(
        lines(
            "", //
            "function foo(arg) {",
            "  const argAlias = arg;",
            "  {",
            "    const argAliasAlias = argAlias",
            "    use(argAliasAlias);",
            "  }",
            "}",
            ""),
        lines(
            "", //
            "function foo(arg) {",
            "  {",
            "    use(arg);",
            "  }",
            "}",
            ""));
  }

  @Test
  public void testInlineExpressionUsingAlias() {
    test(
        lines(
            "", //
            "function foo(arg1, arg2) {",
            "  const arg1Alias = arg1;",
            "  const arg2Alias = arg2;",
            // Create an inner scope to force analysis of `product` before the alias
            // variables.
            "  {",
            "    const product = arg1Alias * arg2Alias;",
            // Use `product` twice so if it gets inlined, it will have to create new
            // references to the operands.
            // Either the expression should not be inlined, or some effort will have
            // to be made to avoid creating new references to `arg1Alias` and `arg2Alias`
            // before they are considered for inlining themselves.
            "    use(product);",
            "    use(product);",
            "  }",
            "}",
            ""),
        lines(
            "", //
            "function foo(arg1, arg2) {",
            "  {",
            "    const product = arg1 * arg2;",
            "    use(product);",
            "    use(product);",
            "  }",
            "}",
            ""));
  }

  @Test
  public void testPassDoesntProduceInvalidCode1() {
    testSame(
        lines(
            "function f(x = void 0) {",
            "  var z;",
            "  {",
            "    const y = {};",
            "    x && (y['x'] = x);",
            "    z = y;",
            "  }",
            "  return z;",
            "}"));
  }

  @Test
  public void testPassDoesntProduceInvalidCode2() {
    testSame(
        lines(
            "function f(x = void 0) {",
            "  {",
            "    var z;",
            "    const y = {};",
            "    x && (y['x'] = x);",
            "    z = y;",
            "  }",
            "  return z;",
            "}"));
  }

  @Test
  public void testPassDoesntProduceInvalidCode3() {
    test(
        lines(
            "function f(x = void 0) {",
            "  var z;",
            "  const y = {};",
            "  x && (y['x'] = x);",
            "  z = y;",
            "  {",
            "    return z;",
            "  }",
            "}"),
        lines(
            "function f(x = void 0) {",
            "",
            "  const y = {};",
            "  x && (y['x'] = x);",
            "  y;",
            "  {",
            "    return y;",
            "  }",
            "}"));
  }

  // Test respect for scopes and blocks

  @Test
  public void testInlineGlobal() {
    test("var x = 1; var z = x;", "var z = 1;");
  }

  @Test
  public void testNoInlineAnnotation() {
    testSame("/** @noinline */ var x = 1; use(x);");
    test(
        "/** @noinline */ var x = 1; use(x); var z = x; use(z);", //
        "/** @noinline */ var x = 1; use(x);            use(x);");
  }

  @Test
  public void testNoInlineExportedName() {
    testSame("var _x = 1; var z = _x;");
  }

  @Test
  public void testNoInlineExportedName2() {
    testSame(
        lines(
            "", //
            "var f = function() {};",
            "var _x = f;",
            "var y = function() { _x(); };",
            "var _y = f;",
            ""));
  }

  @Test
  public void testDontTreatLocalVariablesAsExportedByConvention() {
    // In the GoogleCodingConvention, globals starting with "_" are exported. (see the above two
    // tests). Verify that we don't accidentally apply the same convention to locals.
    test("function f() { var _x = 1; var z = _x; }", "function f() { var z = 1; }");
  }

  @Test
  public void testDoNotInlineIncrement() {
    testSame("var x = 1; x++;");
  }

  @Test
  public void testDoNotInlineDecrement() {
    testSame("var x = 1; x--;");
  }

  @Test
  public void testDoNotInlineIntoLhsOfAssign() {
    testSame("var x = 1; x += 3;");
  }

  @Test
  public void testInlineIntoRhsOfAssign() {
    test("var x = 1; var y = x;", "var y = 1;");
  }

  @Test
  public void testInlineInFunction1() {
    test(
        "function baz() { var x = 1; var z = x; }", //
        "function baz() { var z = 1;            }");
  }

  @Test
  public void testInlineInFunction2() {
    test(
        lines(
            "", //
            "function baz() {",
            "  var a = new obj();",
            "  result = a;",
            "}",
            ""),
        lines(
            "", //
            "function baz() {",
            "  result = new obj();",
            "}",
            ""));
  }

  @Test
  public void testInlineInFunction3() {
    testSame(
        lines(
            "", //
            "function baz() {",
            "  var a = new obj();",
            "  (function(){a;})();",
            "  result = a;",
            "}",
            ""));
  }

  @Test
  public void testInlineInFunction4() {
    testSame(
        lines(
            "", //
            "function baz() {",
            "  var a = new obj();",
            "  foo.result = a;",
            "}",
            ""));
  }

  @Test
  public void testInlineInFunction5() {
    testSame(
        lines(
            "", //
            "function baz() { ",
            "var a = (foo = new obj());",
            "foo.x();",
            "result = a;",
            "}"));
  }

  @Test
  public void testInlineInFunction6() {
    test(
        "function baz() { { var x = 1; var z = x; } }", //
        "function baz() { { var z = 1;            } }");
  }

  @Test
  public void testInlineInFunction7() {
    test(
        "function baz() { var x = 1; { var z = x; } }", //
        "function baz() {            { var z = 1; } }");
  }

  @Test
  public void testInlineIntoArrowFunction1() {
    test(
        "var x = 0; var f = () => x + 1;", //
        "           var f = () => 0 + 1;");
  }

  @Test
  public void testInlineIntoArrowFunction2() {
    test(
        "var x = 0; var f = () => { return x + 1; }", //
        "           var f = () => { return 0 + 1; }");
  }

  @Test
  public void testInlineAcrossModules() {
    // TODO(kushal): Make decision about overlap with CrossChunkCodeMotion
    test(
        srcs(
            JSChunkGraphBuilder.forUnordered()
                .addChunk("var a = 2;")
                .addChunk("var b = a;")
                .build()),
        expected("", "var b = 2;"));
  }

  @Test
  public void testDoNotExitConditional1() {
    test(
        "if (true) { var x = 1; } var z = x; use(z);", //
        "if (true) { var x = 1; }            use(x);");
  }

  @Test
  public void testDoNotExitConditional2() {
    test(
        "if (true) var x = 1; var z = x; use(z);", //
        "if (true) var x = 1;            use(x);");
  }

  @Test
  public void testDoNotExitConditional3() {
    test(
        "var x; if (true) x=1; var z = x; use(z);", //
        "var x; if (true) x=1;            use(x);");
  }

  @Test
  public void testDoNotExitLoop() {
    test(
        "while (z) { var x = 3; } var y = x; use(y);", //
        "while (z) { var x = 3; }            use(x);");
  }

  @Test
  public void testDoNotExitForLoop() {
    test(
        "for (var i = 1; false; false) var z = i;", //
        "for (         ; false; false) var z = 1;");
    testSame("for (; false; false) var i = 1; var z = i;");
    testSame("for (var i in {}); var z = i;");
  }

  @Test
  public void testInlineSubscopeAlias() {
    test(
        lines(
            "", //
            "var x = function() {",
            "  var self = this; ",
            "  return function() {",
            "    var y = self;",
            "    use(y);",
            "  }",
            "}"),
        lines(
            "", //
            "var x = function() {",
            "  var self = this; ",
            "  return function() {",
            "",
            "    use(self);",
            "  }",
            "}"));
    test(
        lines(
            "", //
            "var x = function() {",
            "  var y = [1]; ",
            "  return function() {",
            "    var z = y;",
            "    use(z);",
            "  };",
            "}"),
        lines(
            "", //
            "var x = function() {",
            "  var y = [1]; ",
            "  return function() {",
            "",
            "    use(y);",
            "  };",
            "}"));
    test(
        lines(
            "", //
            "var x = function() {",
            "  var y = 1;",
            "  return function() {",
            "    var z = y;",
            "    use(z);",
            "  };",
            "}"),
        lines(
            "", //
            "var x = function() {",
            "",
            "  return function() {",
            "",
            "    use(1);",
            "  };",
            "}"));
  }

  @Test
  public void testDoNotExitTry() {
    testSame("try { var x = y; } catch (e) {} var z = y; ");
    testSame("try { throw e; var x = 1; } catch (e) {} var z = x; ");
  }

  @Test
  public void testDoNotEnterCatch() {
    testSame("try { } catch (e) { var z = e; } ");
  }

  @Test
  public void testDoNotEnterFinally() {
    test(
        lines(
            "", //
            "try { throw e; var x = 1; } catch (e) {} ",
            "finally  { var z = x; use(z); } ",
            ""),
        lines(
            "", //
            "try { throw e; var x = 1; } catch (e) {} ",
            "finally  {            use(x); } ",
            ""));
  }

  @Test
  public void testInsideIfConditional() {
    test("var a = foo(); if (a) { alert(3); }", "if (foo()) { alert(3); }");
    test("var a; a = foo(); if (a) { alert(3); }", "if (foo()) { alert(3); }");
  }

  @Test
  public void testOnlyReadAtInitialization() {
    test("var a; a = foo();", "foo();");
    test("var a; if (a = foo()) { alert(3); }", "if (foo()) { alert(3); }");
    test("var a; switch (a = foo()) {}", "switch(foo()) {}");
    test(
        "var a; function f(){ return a = foo(); }", //
        "       function f(){ return     foo(); }");
    test(
        "function f(){ var a; return a = foo(); }", //
        "function f(){        return     foo(); }");
    test("var a; with (a = foo()) { alert(3); }", "with (foo()) { alert(3); }");

    test("var a; b = (a = foo());", "b = foo();");
    test(
        "var a; while(a = foo()) { alert(3); }", //
        "       while(    foo()) { alert(3); }");
    test(
        "var a; for(;a = foo();) { alert(3); }", //
        "       for(;    foo();) { alert(3); }");
    test(
        "var a; do {} while(a = foo()) { alert(3); }", //
        "       do {} while(    foo()) { alert(3); }");
  }

  @Test
  public void testImmutableWithSingleReferenceAfterInitialzation() {
    test("var a; a = 1;", "1;");
    test("var a; if (a = 1) { alert(3); }", "if (1) { alert(3); }");
    test("var a; switch (a = 1) {}", "switch(1) {}");
    test(
        "var a; function f(){ return a = 1; }", //
        "       function f(){ return 1; }");
    test(
        "function f(){ var a; return a = 1; }", //
        "function f(){        return     1; }");
    test("var a; with (a = 1) { alert(3); }", "with (1) { alert(3); }");

    test("var a; b = (a = 1);", "b = 1;");
    test(
        "var a; while(a = 1) { alert(3); }", //
        "       while(    1) { alert(3); }");
    test(
        "var a; for(;a = 1;) { alert(3); }", //
        "       for(;    1;) { alert(3); }");
    test(
        "var a; do {} while(a = 1) { alert(3); }", //
        "       do {} while(    1) { alert(3); }");
  }

  @Test
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

  @Test
  public void testInsideIfBranch() {
    testSame("var a = foo(); if (1) { alert(a); }");
  }

  @Test
  public void testInsideAndConditional() {
    test("var a = foo(); a && alert(3);", "foo() && alert(3);");
  }

  @Test
  public void testInsideAndBranch() {
    testSame("var a = foo(); 1 && alert(a);");
  }

  @Test
  public void testInsideOrBranch() {
    testSame("var a = foo(); 1 || alert(a);");
  }

  @Test
  public void testInsideHookBranch() {
    testSame("var a = foo(); 1 ? alert(a) : alert(3)");
  }

  @Test
  public void testInsideHookConditional() {
    test(
        "var a = foo(); a ? alert(1) : alert(3)", //
        "        foo()    ? alert(1) : alert(3)");
  }

  @Test
  public void testInsideOrBranchInsideIfConditional() {
    testSame("var a = foo(); if (x || a) {}");
  }

  @Test
  public void testInsideOrBranchInsideIfConditionalWithConstant() {
    // We don't inline non-immutable constants into branches.
    testSame("var a = [false]; if (x || a) {}");
  }

  @Test
  public void testCrossFunctionsAsLeftLeaves() {
    // Ensures getNext() understands how to walk past a function leaf
    test(
        srcs("var x = function() {};", "", "function cow() {} var z = x;"),
        expected("", "", "function cow() {} var z = function() {};"));
    test(
        srcs("var x = function() {};", "", "var cow = function() {}; var z = x;"),
        expected("", "", "var cow = function() {}; var z = function() {};"));
    test(
        "var x = a; (function() { a++; })(); var z = x; use(z);",
        "var x = a; (function() { a++; })();            use(x);");
    test(
        lines(
            "", //
            "var x = a;",
            "function cow(){ a++; }",
            "cow();",
            "var z = x;"),
        lines(
            "", //
            "var x = a;",
            "", // declaration removed
            "(function(){ a++; })();",
            "", // unused `z` removed
            ""));
    test(
        "var x = a; cow(); var z = x; z; function cow() { a++; };",
        "var x = a; cow();            x; function cow() { a++; };");
  }

  // Test movement of constant values

  @Test
  public void testDoCrossFunction() {
    test(
        "var x = 1; foo(); var z = x; use(z);", //
        "           foo();            use(1);");
  }

  @Test
  public void testDoNotCrossReferencingFunction() {
    test(
        lines(
            "", //
            "var f = function() { var z = x; };",
            "var x = 1;",
            "f();",
            "var z = x;",
            "use(z)",
            "f();",
            ""),
        lines(
            "", //
            "var f = function() { var z$jscomp$1 = x; };",
            "var x = 1;",
            "f();",
            "",
            "use(x)",
            "f();",
            ""));
  }

  // Test tricky declarations and references

  @Test
  public void testChainedAssignment() {
    test("var a = 2, b = 2; var c = b;", "var a = 2; var c = 2;");
    test("var a = 2, b = 2; var c = a;", "var b = 2; var c = 2;");
    test("var a = b = 2; var f = 3; var c = a;", "var f = 3; var c = b = 2;");
    testSame("var a = b = 2; var c = b;");
  }

  @Test
  public void testForIn() {
    testSame("for (var i in j) { var c = i; }");
    testSame("var i = 0; for (i in j) ;");
    testSame("var i = 0; for (i in j) { var c = i; }");
    testSame("i = 0; for (var i in j) { var c = i; }");
    testSame("var j = {'key':'value'}; for (var i in j) {print(i)};");
  }

  // Test movement of values that have (may) side effects

  @Test
  public void testDoCrossNewVariables() {
    test("var x = foo(); var z = x;", "var z = foo();");
  }

  @Test
  public void testAliasAfterFunctionCall() {
    test(
        "var x = foo(); bar(); var z = x; use(z);", //
        "var x = foo(); bar();            use(x);");
  }

  // Test movement of values that are complex but lack side effects

  @Test
  public void testDoNotCrossAssignment() {
    test(
        "var x = {}; var y = x.a; x.a = 1; var z = y; use(z);", //
        "var x = {}; var y = x.a; x.a = 1;            use(y);");
    testSame("var a = this.id; foo(this.id = 3, a);");
  }

  @Test
  public void testAliasAfterDelete() {
    test(
        "var x = {}; var y = x.a; delete x.a; var z = y; use(z);", //
        "var x = {}; var y = x.a; delete x.a;            use(y);");
  }

  @Test
  public void testInlineAliasButNotSnapshot() {
    test(
        "var b = 1; var a = b; b += 2; var c = a; use(c);", //
        "var b = 1; var a = b; b += 2;            use(a);");
  }

  @Test
  public void testAliasAfterIncrement() {
    test(
        "var a = b.c; b.c++; var d = a; use(d);", //
        "var a = b.c; b.c++;            use(a);");
  }

  @Test
  public void testConstructorBeforeAlias() {
    test(
        "var a = b; new Foo(); var c = a; use(c);", //
        "var a = b; new Foo();            use(a);");
  }

  @Test
  public void testDoNotInlineAliasAssignedBeforeAliasedVar() {
    // TODO(bradfordcsmith): This is consistent with past behavior, but it would be good to fix it.
    // Assumes we do not rely on undefined variables (not technically correct!)
    test(
        "var a = b; var b = 3; alert(a)", //
        "           var b = 3; alert(b);");
    test(
        lines(
            "", //
            "f();",
            "function f() {",
            "  var alias = original;",
            "  alert(alias)",
            "}",
            "var original = 3;",
            ""),
        lines(
            "", //
            "f();",
            "function f() {",
            "",
            "  alert(original)",
            "}",
            "var original = 3;",
            ""));
    test(
        lines(
            "", //
            "f();",
            "var original = 3;",
            "function f() {",
            "  var alias = original;",
            "  alert(alias)",
            "}",
            ""),
        lines(
            "", //
            "f();",
            "var original = 3;",
            "function f() {",
            "",
            "  alert(original)",
            "}",
            ""));
  }

  @Test
  public void testOverlappingInlines() {
    String source =
        lines(
            "",
            "a = function(el, x, opt_y) {",
            "  var cur = bar(el);",
            "  opt_y = x.y;",
            "  x = x.x;",
            "  var dx = x - cur.x;",
            "  var dy = opt_y - cur.y;",
            "  foo(",
            "      el,",
            "      el.offsetLeft + dx,",
            "      el.offsetTop + dy);",
            "};",
            "");
    String expected =
        lines(
            "", //
            "a = function(el, x, opt_y) {",
            "  var cur = bar(el);",
            "  opt_y = x.y;",
            "  x = x.x;",
            "  foo(",
            "      el,",
            "      el.offsetLeft + (x - cur.x),",
            "      el.offsetTop + (opt_y - cur.y)); ",
            "};");

    test(source, expected);
  }

  @Test
  public void testOverlappingInlineFunctions() {
    String source =
        lines(
            "", //
            "a = function() { ",
            "  var b = function(args) {var n;}; ",
            "  var c = function(args) {}; ",
            "  d(b,c); ",
            "};",
            "");
    String expected =
        lines(
            "", //
            "a = function() {",
            "  d(function(args){var n;}, function(args){});",
            "};",
            "");

    test(source, expected);
  }

  @Test
  public void testInlineIntoLoops() {
    test(
        "var x = true; while (true) alert(   x);", //
        "              while (true) alert(true);");
    test(
        "var x = true; while (true) for (var i in {}) alert(   x);",
        "              while (true) for (var i in {}) alert(true);");
    testSame("var x = [true]; while (true) alert(x);");
  }

  @Test
  public void testInlineIntoFunction() {
    test(
        "var x = false; var f = function() { alert(    x); };",
        "               var f = function() { alert(false); };");
    testSame("var x = [false]; var f = function() { alert(x); };");
  }

  @Test
  public void testNoInlineIntoNamedFunction() {
    testSame("f(); var x = false; function f() { alert(x); };");
  }

  @Test
  public void testInlineIntoNestedNonHoistedNamedFunctions() {
    test(
        "f(); var x = false; if (false) function f() { alert(    x); };",
        "f();                if (false) function f() { alert(false); };");
  }

  @Test
  public void testNoInlineIntoNestedNamedFunctions() {
    testSame("f(); var x = false; function f() { if (false) { alert(x); } };");
  }

  @Test
  public void testNoInlineMutatedVariable() {
    testSame("var x = false; if (true) { var y = x; x = true; }");
  }

  @Test
  public void testInlineImmutableMultipleTimes() {
    test(
        "var x = null; var y =    x, z =    x;", //
        "              var y = null, z = null;");
    test(
        "var x = 3; var y = x, z = x;", //
        "           var y = 3, z = 3;");
  }

  @Test
  public void testInlineStringMultipleTimesAllStrings() {
    test(
        lines(
            "", //
            "var x = 'abcdefghijklmnopqrstuvwxyz';",
            "var y = x, z = x;",
            ""),
        lines(
            "", //
            "var y = 'abcdefghijklmnopqrstuvwxyz', ",
            "    z = 'abcdefghijklmnopqrstuvwxyz';",
            ""));
  }

  @Test
  public void testNoInlineBackwards() {
    testSame("var y = x; var x = null;");
  }

  @Test
  public void testNoInlineOutOfBranch() {
    testSame("if (true) var x = null; var y = x;");
  }

  @Test
  public void testInterferingInlines() {
    test(
        "var a = 3; var f = function() { var x = a; alert(x); };",
        "           var f = function() {            alert(3); };");
  }

  @Test
  public void testInlineIntoTryCatch() {
    test(
        lines(
            "var a = true; ",
            "try { var b = a; } ",
            "catch (e) { var c = a + b; var d = true; } ",
            "finally { var f = a + b + c + d; }"),
        lines(
            "try { var b = true; } ",
            "catch (e) { var c = true + b; var d = true; } ",
            "finally { var f = true + b + c + d; }"));
  }

  // Make sure that we still inline constants that are not provably
  // written before they're read.
  @Test
  public void testInlineConstants() {
    test(
        "function foo() { return  XXX; } var XXX = true;", //
        "function foo() { return true; }                ");
  }

  @Test
  public void testInlineStringWhenWorthwhile() {
    test("var x = 'a'; foo(x, x, x);", "foo('a', 'a', 'a');");
  }

  @Test
  public void testInlineConstantAlias() {
    test(
        "var XXX = new Foo(); q(XXX); var YYY = XXX; bar(YYY)",
        "var XXX = new Foo(); q(XXX);                bar(XXX)");
  }

  @Test
  public void testInlineConstantAliasWithAnnotation() {
    test(
        "/** @const */ var xxx = new Foo(); q(xxx); var YYY = xxx; bar(YYY)",
        "/** @const */ var xxx = new Foo(); q(xxx);                bar(xxx)");
  }

  @Test
  public void testInlineConstantAliasWithNonConstant() {
    test(
        "var XXX = new Foo(); q(XXX); var y = XXX; bar(  y); baz(  y)",
        "var XXX = new Foo(); q(XXX);              bar(XXX); baz(XXX)");
  }

  @Test
  public void testCascadingInlines() {
    test(
        lines(
            "", //
            "var XXX = 4;",
            "function f() {",
            "  var YYY = XXX;",
            "  bar(YYY);",
            "  baz(YYY);",
            "}",
            ""),
        lines(
            "", //
            "function f() {",
            "  bar(4);",
            "  baz(4);",
            "}",
            ""));
  }

  @Test
  public void testNoInlineGetpropIntoCall() {
    test("var a = b; a();", "b();");
    test("var a = b.c; f(a);", "f(b.c);");
    testSame("var a = b.c; a();");
  }

  @Test
  public void testInlineFunctionDeclaration() {
    test(
        "var f = function () {}; var a =              f;", //
        "                        var a = function () {};");
    test(
        "var f = function () {}; foo(); var a =              f;", //
        "                        foo(); var a = function () {};");
    test(
        "var f = function () {}; foo(             f);", //
        "                        foo(function () {});");

    test(
        "var f = function () {}; function g() {var a = f; return a;}", //
        "var f = function () {}; function g() {           return f;}");
    testSame("var f = function () {}; function g() {h(f);}");
  }

  @Test
  public void test2388531() {
    testSame(
        lines(
            "", //
            "var f = function () {};",
            "var g = function () {};",
            "goog.inherits(f, g);",
            ""));
    testSame(
        lines(
            "", //
            "var f = function () {};",
            "var g = function () {};",
            "goog$inherits(f, g);",
            ""));
  }

  @Test
  public void testRecursiveFunction1() {
    testSame("var x = 0; (function x() { return x ? x() : 3; })();");
  }

  @Test
  public void testRecursiveFunction2() {
    testSame("function y() { return y(); }");
  }

  @Test
  public void testUnreferencedBleedingFunction() {
    testSame("var x = function y() {}");
  }

  @Test
  public void testReferencedBleedingFunction() {
    testSame("var x = function y() { return y(); }");
  }

  @Test
  public void testInlineAliases1() {
    test(
        "var x = this.foo(); this.bar(); var y = x; this.baz(y);",
        "var x = this.foo(); this.bar();            this.baz(x);");
  }

  @Test
  public void testInlineAliases1b() {
    test(
        "var x = this.foo(); this.bar(); var y; y = x; this.baz(y);",
        "var x = this.foo(); this.bar();            x; this.baz(x);");
  }

  @Test
  public void testInlineAliases1c() {
    test(
        "var x; x = this.foo(); this.bar(); var y = x; this.baz(y);",
        "var x; x = this.foo(); this.bar();            this.baz(x);");
  }

  @Test
  public void testInlineAliases1d() {
    test(
        "var x; x = this.foo(); this.bar(); var y; y = x; this.baz(y);",
        "var x; x = this.foo(); this.bar();            x; this.baz(x);");
  }

  @Test
  public void testInlineAliases2() {
    test(
        lines(
            "", //
            "var x = this.foo();",
            "this.bar();",
            "function f() {",
            "  var y = x;",
            "  this.baz(y);",
            "}",
            ""),
        lines(
            "", //
            "var x = this.foo();",
            "this.bar();",
            "function f() {",
            "  this.baz(x);",
            "}",
            ""));
  }

  @Test
  public void testInlineAliases2b() {
    test(
        lines(
            "",
            "var x = this.foo();",
            "this.bar(); ",
            "function f() {",
            "  var y;",
            "  y = x;",
            "  this.baz(y);",
            "}",
            ""),
        lines(
            "", //
            "var x = this.foo();",
            "this.bar();",
            "function f() {",
            "",
            "  x;",
            "  this.baz(x);",
            "}",
            ""));
  }

  @Test
  public void testInlineAliases2c() {
    test(
        lines(
            "", //
            "var x;",
            "x = this.foo();",
            "this.bar(); ",
            "function f() {",
            "  var y = x;",
            "  this.baz(y);",
            "}",
            ""),
        lines(
            "", //
            "var x;",
            "x = this.foo();",
            "this.bar();",
            "function f() {",
            "  this.baz(x);",
            "}",
            ""));
  }

  @Test
  public void testInlineAliases2d() {
    test(
        lines(
            "", //
            "var x;",
            "x = this.foo();",
            "this.bar();",
            "function f() {",
            "  var y;",
            "  y = x;",
            "  this.baz(y);",
            "}",
            ""),
        lines(
            "", //
            "var x;",
            "x = this.foo();",
            "this.bar();",
            "function f() {",
            "",
            "  x;",
            "  this.baz(x);",
            "}",
            ""));
  }

  @Test
  public void testInlineAliasesInLoop() {
    test(
        lines(
            "function f() { ",
            "  var x = extern();",
            "  for (var i = 0; i < 5; i++) {",
            "    (function() {",
            "       var y = x; window.setTimeout(function() { extern(y); }, 0);",
            "     })();",
            "  }",
            "}"),
        lines(
            "function f() { ",
            "  var x = extern();",
            "  for (var i = 0; i < 5; i++) {",
            "    (function() {",
            "       window.setTimeout(function() { extern(x); }, 0);",
            "     })();",
            "  }",
            "}"));
  }

  @Test
  public void testNoInlineAliasesInLoop() {
    testSame(
        lines(
            "function f() { ",
            "  for (var i = 0; i < 5; i++) {",
            "    var x = extern();",
            "    (function() {",
            "       var y = x; window.setTimeout(function() { extern(y); }, 0);",
            "     })();",
            "  }",
            "}"));
  }

  @Test
  public void testNoInlineAliases1() {
    testSame("var x = this.foo(); this.bar(); var y = x; x = 3; this.baz(y);");
  }

  @Test
  public void testNoInlineAliases1b() {
    testSame("var x = this.foo(); this.bar(); var y; y = x; x = 3; this.baz(y);");
  }

  @Test
  public void testNoInlineAliases2() {
    testSame("var x = this.foo(); this.bar(); var y = x; y = 3; this.baz(y); ");
  }

  @Test
  public void testNoInlineAliases2b() {
    testSame("var x = this.foo(); this.bar(); var y; y = x; y = 3; this.baz(y); ");
  }

  @Test
  public void testNoInlineAliases3() {
    testSame(
        lines(
            "var x = this.foo();",
            "this.bar();",
            "function f() {",
            "  var y = x;",
            "  g();",
            "  this.baz(y);",
            "}",
            "function g() {",
            "  x = 3;",
            "}"));
  }

  @Test
  public void testNoInlineAliases3b() {
    testSame(
        lines(
            "var x = this.foo();",
            "this.bar();",
            "",
            "function f() {",
            "var y;",
            "y = x;",
            "g();",
            "this.baz(y);",
            "} ",
            "function g() {",
            "x = 3;",
            "}"));
  }

  @Test
  public void testNoInlineAliases4() {
    testSame(
        lines(
            "", //
            "var x = this.foo();",
            "this.bar();",
            "function f() {",
            "  var y = x;",
            "  y = 3;",
            "  this.baz(y);",
            "}",
            ""));
  }

  @Test
  public void testNoInlineAliases4b() {
    testSame(
        lines(
            "", //
            "var x = this.foo();",
            "this.bar();",
            "function f() {",
            "  var y;",
            "  y = x;",
            "  y = 3;",
            "  this.baz(y);",
            "}",
            ""));
  }

  @Test
  public void testNoInlineAliases5() {
    testSame(
        lines(
            "", //
            "var x = this.foo();",
            "this.bar();",
            "var y = x;",
            "this.bing();",
            "this.baz(y);",
            "x = 3;",
            ""));
  }

  @Test
  public void testNoInlineAliases5b() {
    testSame(
        lines(
            "", //
            "var x = this.foo();",
            "this.bar();",
            "var y;",
            "y = x;",
            "this.bing();",
            "this.baz(y);",
            "x = 3;",
            ""));
  }

  @Test
  public void testNoInlineAliases6() {
    testSame(
        lines(
            "", //
            "var x = this.foo();",
            "this.bar();",
            "var y = x;",
            "this.bing();",
            "this.baz(y);",
            "y = 3;",
            ""));
  }

  @Test
  public void testNoInlineAliases6b() {
    testSame(
        lines(
            "", //
            "var x = this.foo();",
            "this.bar();",
            "var y;",
            "y = x;",
            "this.bing();",
            "this.baz(y);",
            "y = 3;",
            ""));
  }

  @Test
  public void testNoInlineAliases7() {
    testSame(
        lines(
            "", //
            "var x = this.foo();",
            "this.bar();",
            "function f() {",
            "  var y = x;",
            "  this.bing();",
            "  this.baz(y);",
            "  x = 3;",
            "}",
            ""));
  }

  @Test
  public void testNoInlineAliases7b() {
    testSame(
        lines(
            "", //
            "var x = this.foo();",
            "this.bar();",
            "function f() {",
            "  var y;",
            "  y = x;",
            "  this.bing();",
            "  this.baz(y);",
            "  x = 3;",
            "}"));
  }

  @Test
  public void testNoInlineAliases8() {
    testSame(
        lines(
            "", //
            "var x = this.foo();",
            "this.bar();",
            "function f() {",
            "  var y = x;",
            "  this.baz(y);",
            "  y = 3;",
            "}"));
  }

  @Test
  public void testNoInlineAliases8b() {
    testSame(
        lines(
            "", //
            "var x = this.foo();",
            "this.bar();",
            "function f() {",
            "  var y;",
            "  y = x;",
            "  this.baz(y);",
            "  y = 3;",
            "}"));
  }

  @Test
  public void testSideEffectOrder() {
    // z can not be changed by the call to y, so x can be inlined.
    String EXTERNS = "var z; function f(){}";
    test(
        externs(EXTERNS), //
        srcs("var x = f(y.a, y); z =          x"),
        expected("               z = f(y.a, y);"));
    // z.b can be changed by the call to y, so x can not be inlined.
    testSame(externs(EXTERNS), srcs("var x = f(y.a, y); z.b = x;"));
  }

  @Test
  public void testInlineParameterAlias1() {
    test(
        lines(
            "", //
            "function f(x) {",
            "  var y = x;",
            "  g();",
            "  y;y;",
            "}",
            ""),
        lines(
            "", //
            "function f(x) {",
            "  g();",
            "  x;x;",
            "}",
            ""));
  }

  @Test
  public void testInlineParameterAlias2() {
    test(
        lines(
            "", //
            "function f(x) {",
            "  var y; y = x;",
            "  g();",
            "  y;y;",
            "}",
            ""),
        lines(
            "", //
            "function f(x) {",
            "  x;",
            "  g();",
            "  x;x;",
            "}",
            ""));
  }

  @Test
  public void testInlineFunctionAlias1a() {
    test(
        lines(
            "", //
            "function f(x) {}",
            "var y = f;",
            "g();",
            "y();y();",
            ""),
        lines(
            "", //
            "var y = function(x) {};",
            "g();",
            "y();y();",
            ""));
  }

  @Test
  public void testInlineFunctionAlias1b() {
    test(
        lines(
            "", //
            "function f(x) {};",
            "f;var y = f;",
            "g();",
            "y();y();",
            ""),
        lines(
            "", //
            "function f(x) {};",
            "f;g();",
            "f();f();",
            ""));
  }

  @Test
  public void testInlineFunctionAlias2a() {
    test(
        lines(
            "", //
            "function f(x) {}",
            "var y; y = f;",
            "g();",
            "y();y();",
            ""),
        lines(
            "", //
            "var y; y = function(x) {};",
            "g();",
            "y();y();",
            ""));
  }

  @Test
  public void testInlineFunctionAlias2b() {
    test(
        lines(
            "", //
            "function f(x) {};",
            "f; var y; y = f;",
            "g();",
            "y();y();",
            ""),
        lines(
            "", //
            "function f(x) {};",
            "f; f;",
            "g();",
            "f();f();",
            ""));
  }

  @Test
  public void testInlineSwitchVar() {
    test(
        "var x = y; switch (x) {}", //
        "           switch (y) {}");
  }

  @Test
  public void testInlineSwitchLet() {
    test(
        "let x = y; switch (x) {}", //
        "           switch (y) {}");
  }

  // Successfully inlines 'values' and 'e'
  @Test
  public void testInlineIntoForLoop1() {
    test(
        lines(
            "function calculate_hashCode() {",
            "  var values = [1, 2, 3, 4, 5];",
            "  var hashCode = 1;",
            "  for (var $array = values, i = 0; i < $array.length; i++) {",
            "    var e = $array[i];",
            "    hashCode = 31 * hashCode + calculate_hashCode(e);",
            "  }",
            "  return hashCode;",
            "}"),
        lines(
            "function calculate_hashCode() {",
            "  var hashCode = 1;",
            "  var $array = [1, 2, 3, 4, 5];",
            "  var i = 0;",
            "  for (; i < $array.length; i++) {",
            "    hashCode = 31 * hashCode + calculate_hashCode($array[i]);",
            "  }",
            "  return hashCode;",
            "}"));
  }

  // Inlines 'e' but fails to inline 'values'
  // TODO(tbreisacher): Investigate and see if we can improve this.
  @Test
  public void testInlineIntoForLoop2() {
    test(
        lines(
            "function calculate_hashCode() {",
            "  let values = [1, 2, 3, 4, 5];",
            "  let hashCode = 1;",
            "  for (let $array = values, i = 0; i < $array.length; i++) {",
            "    let e = $array[i];",
            "    hashCode = 31 * hashCode + calculate_hashCode(e);",
            "  }",
            "  return hashCode;",
            "}"),
        lines(
            "function calculate_hashCode() {",
            "  let values = [1, 2, 3, 4, 5];",
            "  let hashCode = 1;",
            "  for (let $array = values, i = 0; i < $array.length; i++) {",
            "    hashCode = 31 * hashCode + calculate_hashCode($array[i]);",
            "  }",
            "  return hashCode;",
            "}"));
  }

  // This used to be inlined, but regressed when we switched to the ES6 scope creator.
  @Test
  public void testNoInlineCatchAliasVar1() {
    testSame(
        lines(
            "", //
            "try {",
            "} catch (e) {",
            "  var y = e;",
            "  g();",
            "  y;y;",
            "}"));
  }

  // This used to be inlined, but regressed when we switched to the ES6 scope creator.
  @Test
  public void testNoInlineCatchAliasVar2() {
    testSame(
        lines(
            "", //
            "try {",
            "} catch (e) {",
            "  var y; y = e;",
            "  g();",
            "  y;y;",
            "}"));
  }

  @Test
  public void testInlineCatchAliasLet1() {
    test(
        lines(
            "", //
            "try {",
            "} catch (e) {",
            "  let y = e;",
            "  g();",
            "  y;y;",
            "}"),
        lines(
            "", //
            "try {",
            "} catch (e) {",
            "  g();",
            "  e;e;",
            "}"));
  }

  @Test
  public void testInlineCatchAliasLet2() {
    test(
        lines(
            "", //
            "try {",
            "} catch (e) {",
            "  let y; y = e;",
            "  g();",
            "  y;y;",
            "}"),
        lines(
            "", //
            "try {",
            "} catch (e) {",
            "  e;",
            "  g();",
            "  e;e;",
            "}"));
  }

  @Test
  public void testInlineThis() {
    test(
        lines(
            "/** @constructor */",
            "function C() {}",
            "",
            "C.prototype.m = function() {",
            "  var self = this;",
            "  if (true) {",
            "    alert(self);",
            "  }",
            "};"),
        lines(
            "(/** @constructor */",
            "function() {}).prototype.m = function() {",
            "  if (true) {",
            "    alert(this);",
            "  }",
            "};"));
  }

  @Test
  public void testVarInBlock1() {
    test(
        "function f(x) { if (true) {var y = x; y; y;} }", //
        "function f(x) { if (true) {           x; x;} }");
  }

  @Test
  public void testVarInBlock2() {
    test(
        "function f(x) { switch (0) { case 0: { var y = x; y; y; } } }",
        "function f(x) { switch (0) { case 0: { x; x; } } }");
  }

  @Test
  public void testLocalsOnly1() {
    inlineLocalsOnly = true;
    test(
        "var x=1; x; function f() {var x = 1; x;}", //
        "var x=1; x; function f() {           1;}");
  }

  @Test
  public void testLocalsOnly2() {
    inlineLocalsOnly = true;
    test(
        lines(
            "/** @const */",
            "var X=1;",
            "X;",
            "function f() {",
            "  /** @const */",
            "  var X = 1; X;",
            "}"),
        lines(
            "", //
            "/** @const */",
            "var X=1;",
            "X;",
            "function f() {",
            "  1;",
            "}"));
  }

  @Test
  public void testInlineUndefined1() {
    test("var x; x;", "void 0;");
  }

  @Test
  public void testInlineUndefined2() {
    testSame("var x; x++;");
  }

  @Test
  public void testInlineUndefined3() {
    testSame("var x; var x;");
  }

  @Test
  public void testInlineUndefined4() {
    test("var x; x; x;", "void 0; void 0;");
  }

  @Test
  public void testInlineUndefined5() {
    testSame("var x; for(x in a) {}");
  }

  @Test
  public void testIssue90() {
    test(
        "var x;      x && alert(1)", //
        "       void 0 && alert(1)");
  }

  @Test
  public void testRenamePropertyFunction() {
    testSame(
        lines(
            "", //
            "var JSCompiler_renameProperty; ",
            "JSCompiler_renameProperty('foo')"));
  }

  @Test
  public void testThisAlias() {
    test(
        "function f() { var a = this;    a.y();    a.z(); }", //
        "function f() {               this.y(); this.z(); }");
  }

  @Test
  public void testThisEscapedAlias() {
    testSame("function f() { var a = this; var g = function() { a.y(); }; a.z(); }");
  }

  @Test
  public void testInlineNamedFunction() {
    test("function f() {} f();", "(function(){})()");
  }

  @Test
  public void testIssue378ModifiedArguments1() {
    testSame(
        lines(
            "function g(callback) {",
            "  var f = callback;",
            "  arguments[0] = this;",
            "  f.apply(this, arguments);",
            "}"));
  }

  @Test
  public void testIssue378ModifiedArguments2() {
    testSame(
        lines(
            "function g(callback) {",
            "  /** @const */",
            "  var f = callback;",
            "  arguments[0] = this;",
            "  f.apply(this, arguments);",
            "}"));
  }

  @Test
  public void testIssue378EscapedArguments1() {
    testSame(
        lines(
            "function g(callback) {",
            "  var f = callback;",
            "  h(arguments,this);",
            "  f.apply(this, arguments);",
            "}",
            "function h(a,b) {",
            "  a[0] = b;",
            "}"));
  }

  @Test
  public void testIssue378EscapedArguments2() {
    testSame(
        lines(
            "function g(callback) {",
            "  /** @const */",
            "  var f = callback;",
            "  h(arguments,this);",
            "  f.apply(this);",
            "}",
            "function h(a,b) {",
            "  a[0] = b;",
            "}"));
  }

  @Test
  public void testIssue378EscapedArguments3() {
    test(
        lines(
            "", //
            "function g(callback) {",
            "  var f = callback;",
            "  f.apply(this, arguments);",
            "}"),
        lines(
            "", //
            "function g(callback) {",
            "",
            "  callback.apply(this, arguments);",
            "}"));
  }

  @Test
  public void testIssue378EscapedArguments4() {
    test(
        lines(
            "function g(callback) {",
            "  var f = callback;",
            "  h(arguments[0],this);",
            "  f.apply(this, arguments);",
            "}",
            "function h(a,b) {",
            "  a[0] = b;",
            "}"),
        lines(
            "function g(callback) {",
            "",
            "  h(arguments[0],this);",
            "  callback.apply(this, arguments);",
            "}",
            "function h(a,b) {",
            "  a[0] = b;",
            "}"));
  }

  @Test
  public void testIssue378ArgumentsRead1() {
    test(
        lines(
            "function g(callback) {",
            "  var f = callback;",
            "  var g = arguments[0];",
            "  f.apply(this, arguments);",
            "}"),
        lines(
            "function g(callback) {",
            "",
            "  var g = arguments[0];",
            "  callback.apply(this, arguments);",
            "}"));
  }

  @Test
  public void testIssue378ArgumentsRead2() {
    test(
        lines(
            "function g(callback) {",
            "  var f = callback;",
            "  h(arguments[0],this);",
            "  f.apply(this, arguments[0]);",
            "}",
            "function h(a,b) {",
            "  a[0] = b;",
            "}"),
        lines(
            "function g(callback) {",
            "  h(arguments[0],this);",
            "  callback.apply(this, arguments[0]);",
            "}",
            "function h(a,b) {",
            "  a[0] = b;",
            "}"));
  }

  @Test
  public void testArgumentsModifiedInOuterFunction() {
    test(
        lines(
            "function g(callback) {",
            "  var f = callback;",
            "  arguments[0] = this;",
            "  f.apply(this, arguments);",
            "  function inner(callback) {",
            "    var x = callback;",
            "    x.apply(this);",
            "  }",
            "}"),
        lines(
            "function g(callback) {",
            "  var f = callback;",
            "  arguments[0] = this;",
            "  f.apply(this, arguments);",
            "  function inner(callback) {",
            "    callback.apply(this);",
            "  }",
            "}"));
  }

  @Test
  public void testArgumentsModifiedInInnerFunction() {
    test(
        lines(
            "function g(callback) {",
            "  var f = callback;",
            "  f.apply(this, arguments);",
            "  function inner(callback) {",
            "    var x = callback;",
            "    arguments[0] = this;",
            "    x.apply(this);",
            "  }",
            "}"),
        lines(
            "function g(callback) {",
            "  callback.apply(this, arguments);",
            "  function inner(callback) {",
            "    var x = callback;",
            "    arguments[0] = this;",
            "    x.apply(this);",
            "  }",
            "}"));
  }

  @Test
  public void testNoInlineRedeclaredExterns() {
    String externs = "var test = 1;";
    String code = "/** @suppress {duplicate} */ var test = 2;alert(test);";
    testSame(externs(externs), srcs(code));
  }

  @Test
  public void testBug6598844() {
    testSame(
        lines(
            "function F() { this.a = 0; }",
            "F.prototype.inc = function() { this.a++; return 10; };",
            "F.prototype.bar = function() { var x = this.inc(); this.a += x; };"));
  }

  @Test
  public void testExternalIssue1053() {
    testSame("var u; function f() { u = Random(); var x = u; f(); alert(x===u)}");
  }

  @Test
  public void testHoistedFunction1() {
    test("var x = 1; function f() { return x; }", "function f() { return 1; }");
  }

  @Test
  public void testHoistedFunction2() {
    testSame(
        lines(
            "var impl_0;",
            "b(a());",
            "function a() { impl_0 = {}; }",
            "function b() { window['f'] = impl_0; }"));
  }

  @Test
  public void testHoistedFunction3() {
    testSame(
        lines(
            "", //
            "var impl_0;",
            "b();",
            "impl_0 = 1;",
            "function b() { window['f'] = impl_0; }"));
  }

  @Test
  public void testHoistedFunction4() {
    test(
        lines(
            "", //
            "var impl_0;",
            "impl_0 = 1;",
            "b();",
            "function b() { window['f'] = impl_0; }"),
        lines(
            "", //
            "1;",
            "b();",
            "function b() { window['f'] = 1; }"));
  }

  @Test
  public void testHoistedFunction5() {
    testSame(
        lines(
            "a();",
            "var debug = 1;",
            "function b() { return debug; }",
            "function a() { return b(); }"));
  }

  @Test
  public void testHoistedFunction6() {
    test(
        lines(
            "var debug = 1;",
            "a();",
            "function b() { return debug; }",
            "function a() { return b(); }"),
        lines(
            "", //
            "a();",
            "function b() { return 1; }",
            "function a() { return b(); }"));
  }

  @Test
  public void testIssue354() {
    test(
        lines(
            "var enabled = true;",
            "function Widget() {}",
            "Widget.prototype = {",
            "  frob: function() {",
            "    search();",
            "  }",
            "};",
            "function search() {",
            "  if (enabled)",
            "    alert(1);",
            "  else",
            "    alert(2);",
            "}",
            "window.foo = new Widget();",
            "window.bar = search;"),
        lines(
            "function Widget() {}",
            "Widget.prototype = {",
            "  frob: function() {",
            "    search();",
            "  }",
            "};",
            "function search() {",
            "  if (true)",
            "    alert(1);",
            "  else",
            "    alert(2);",
            "}",
            "window.foo = new Widget();",
            "window.bar = search;"));
  }

  // Test respect for scopes and blocks
  @Test
  public void testIssue1177() {
    testSame("function x_64(){var x_7;for(;;);var x_68=x_7=x_7;}");
    testSame("function x_64(){var x_7;for(;;);var x_68=x_7=x_7++;}");
    testSame("function x_64(){var x_7;for(;;);var x_68=x_7=x_7*2;}");
  }

  // GitHub issue #1234: https://github.com/google/closure-compiler/issues/1234
  @Test
  public void testSwitchGithubIssue1234() {
    testSame(
        lines(
            "var x;",
            "switch ('a') {",
            "  case 'a':",
            "    break;",
            "  default:",
            "    x = 1;",
            "    break;",
            "}",
            "use(x);"));
  }

  @Test
  public void testLateGuardedAssign() {
    test(
        lines(
            "", //
            "let x;",
            "function f() {",
            "  x === void 0 && (x = 1);",
            "  const y = x;",
            "  use(y);",
            "}",
            ""),
        lines(
            "", //
            "let x;",
            "function f() {",
            "  x === void 0 && (x = 1);",
            "",
            "  use(x);",
            "}",
            ""));
  }

  @Test
  public void testLetConst() {
    test(
        lines(
            "", //
            "function f(x) {",
            "  if (true) {",
            "    let y = x; y; y;",
            "  }",
            "}"),
        lines(
            "", //
            "function f(x) {",
            "  if (true) {",
            "    x; x;",
            "  }",
            "}"));

    test(
        lines(
            "", //
            "function f(x) {",
            "  if (true) {",
            "    const y = x; y; y;",
            "    }",
            "  }"),
        lines(
            "", //
            "function f(x) {",
            "  if (true) {",
            "    x; x;",
            "  }",
            "}"));

    test(
        lines(
            "", //
            "function f(x) {",
            "  let y;",
            "  {",
            "    let y = x; y;",
            "  }",
            "}"),
        lines(
            "", //
            "function f(x) {",
            "  let y;",
            "  {",
            "    x;",
            "  }",
            "}"));

    test(
        lines(
            "function f(x) {",
            "  let y = x; y; const g = 2; ",
            "  {",
            "    const g = 3; let y = g; y;",
            "  }",
            "}"),
        lines(
            "", //
            "function f(x) {",
            "  x;",
            "  {3;}",
            "}"));
  }

  @Test
  public void testGenerators() {
    test(
        lines(
            "", //
            "function* f() {",
            "  let x = 1;",
            "  yield x;",
            "}"),
        lines(
            "", //
            "function* f() {",
            "  yield 1;",
            "}"));

    test(
        lines(
            "", //
            "function* f(x) {",
            "  let y = x++",
            "  yield y;",
            "}"),
        lines(
            "", //
            "function* f(x) {",
            "  yield x++;",
            "}"));
  }

  @Test
  public void testForOf() {
    testSame(" var i = 0; for(i of n) {}");

    testSame("for( var i of n) { var x = i; }");
  }

  @Test
  public void testTemplateStrings() {
    test(
        " var name = 'Foo'; `Hello ${name}`", //
        "                   `Hello ${'Foo'}`");

    test(
        "var name = 'Foo'; var foo = name; `Hello ${foo}`", //
        "                                  `Hello ${'Foo'}`");

    test(
        "var age = 3; `Age: ${age}`", //
        "             `Age: ${3}`");
  }

  @Test
  public void testTaggedTemplateLiterals() {
    test(
        lines(
            "var name = 'Foo';",
            "function myTag(strings, nameExp, numExp) {",
            "  var modStr;",
            "  if (numExp > 2) {",
            "    modStr = nameExp + 'Bar'",
            "  } else { ",
            "    modStr = nameExp + 'BarBar'",
            "  }",
            "}",
            "var output = myTag`My name is ${name} ${3}`;"),
        lines(
            "var output = function(strings, nameExp, numExp) {",
            "  var modStr;",
            "  if (numExp > 2) {",
            "    modStr = nameExp + 'Bar'",
            "  } else { ",
            "    modStr = nameExp + 'BarBar'",
            "  }",
            "}`My name is ${'Foo'} ${3}`;"));

    test(
        lines(
            "var name = 'Foo';",
            "function myTag(strings, nameExp, numExp) {",
            "  var modStr;",
            "  if (numExp > 2) {",
            "    modStr = nameExp + 'Bar'",
            "  } else { ",
            "    modStr = nameExp + 'BarBar'",
            "  }",
            "}",
            "var output = myTag`My name is ${name} ${3}`;",
            "output = myTag`My name is ${name} ${2}`;"),
        lines(
            "function myTag(strings, nameExp, numExp) {",
            "  var modStr;",
            "  if (numExp > 2) {",
            "    modStr = nameExp + 'Bar'",
            "  } else { ",
            "    modStr = nameExp + 'BarBar'",
            "  }",
            "}",
            "var output = myTag`My name is ${'Foo'} ${3}`;",
            "output = myTag`My name is ${'Foo'} ${2}`;"));
  }

  @Test
  public void testDestructuring() {
    test(
        lines(
            "", //
            "var [a, b, c] = [1, 2, 3]",
            "var x = a;",
            "x; x;"),
        lines(
            "", //
            "var [a, b, c] = [1, 2, 3]",
            "a; a;"));

    testSame("var x = 1; ({[0]: x} = {});");
  }

  @Test
  public void dontInlineConditionalDefaultValueAssignment() {
    testSame("let x; const {y = (x = 0)} = obj(); use(x);");
  }

  @Test
  public void testFunctionInlinedAcrossScript() {
    String[] srcs = {
      "function f() {}", //
      "use(f);"
    };

    String[] expected = {
      "", //
      "use(function() {});"
    };

    test(srcs(srcs), expected(expected));
  }

  @Test
  public void testFunctionVarInliningElidesFunctionName() {
    // When there's only one reference to a function name (e.g. in the rhs of an assignment), we
    // should elide that function name.
    test(
        "function notRecursive(x) { return 4 }; exports.y = notRecursive;",
        ";exports.y = function(x) { return 4; }");

    // In this case, the function is recursive, so there's more than one reference, we should keep
    // the name.
    testSame(
        "function factorial(x) { if (x == 1) return 1; return x + factorial(x - 1); };"
            + "exports.x = factorial;");
  }
}
