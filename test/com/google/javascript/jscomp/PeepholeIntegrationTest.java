/*
 * Copyright 2004 The Closure Compiler Authors.
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

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for the interaction between multiple peephole passes. */
@RunWith(JUnit4.class)
public class PeepholeIntegrationTest extends CompilerTestCase {

  private boolean late;
  private int numRepetitions;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    enableNormalize();
    enableComputeSideEffects();
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    late = false;
    numRepetitions = 2;
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new PeepholeOptimizationsPass(
        compiler,
        getName(),
        new PeepholeMinimizeConditions(late),
        new PeepholeSubstituteAlternateSyntax(late),
        new PeepholeRemoveDeadCode(),
        new PeepholeFoldConstants(late, false /* useTypes */),
        new PeepholeReplaceKnownMethods(late, /* useTypes= */ false),
        new MinimizeExitPoints());
  }

  @Override
  protected int getNumRepetitions() {
    return numRepetitions;
  }

  @Test
  public void testTrueFalse() {
    late = false;
    testSame("x = true");
    testSame("x = false");
    test("x = !1", "x = false");
    test("x = !0", "x = true");
    late = true;
    test("x = true", "x = !0");
    test("x = false", "x = !1");
    testSame("x = !1");
    testSame("x = !0");
  }

  @Test
  public void testFoldFollowing() {
    test("function f(){return; console.log(1);}", "function f(){}");
  }

  /** Check that removing blocks with 1 child works */
  @Test
  public void testFoldOneChildBlocksIntegration() {
    test("function f(){switch(foo()){default:{break}}}", "function f(){foo()}");

    test("function f(){switch(x){default:{break}}}", "function f(){}");

    test(
        "function f(){switch(x){default:x;case 1:return 2}}",
        "function f(){switch(x){default:case 1:return 2}}");

    // ensure that block folding does not break hook ifs
    test("if(x){if(true){foo();foo()}else{bar();bar()}}", "if(x){foo();foo()}");

    test("if(x){if(false){foo();foo()}else{bar();bar()}}", "if(x){bar();bar()}");

    // Cases where the then clause has no side effects.
    test("if(x()){}", "x()");

    test("if(x()){} else {x()}", "x()||x()");
    test("if(x){}", ""); // Even the condition has no side effect.
    test("if(a()){A()} else if (b()) {} else {C()}", "a()?A():b()||C()");

    test("if(a()){} else if (b()) {} else {C()}", "a() || (b() || C())");
    test(
        "if(a()){A()} else if (b()) {} else if (c()) {} else{D()}",
        "a() ? A() : b() || (c() || D())");
    test("if(a()){} else if (b()) {} else if (c()) {} else{D()}", "a() || (b() || (c() || D()))");
    test("if(a()){A()} else if (b()) {} else if (c()) {} else{}", "a()?A():b()||c()");

    // Verify that non-global scope works.
    test("function foo(){if(x()){}}", "function foo(){x()}");
    test("function foo(){if(x?.()){}}", "function foo(){x?.()}");
  }

  @Test
  public void testFoldOneChildBlocksStringCompare() {
    test("if (x) {if (y) { var x; } } else{ var z; }", "if (x) { if (y) var x } else var z");
  }

  /** Test a particularly hairy edge case. */
  @Test
  public void testNecessaryDanglingElse() {
    test("if (x) if (y){ y(); z() } else; else x()", "if (x) { if(y) { y(); z() } } else x()");
  }

  /** Try to minimize returns */
  @Test
  public void testFoldReturnsIntegration() {
    // if-then-else duplicate statement removal handles this case:
    test("function f(){if(x)return;else return}", "function f(){}");
  }

  /** Try to minimize returns */
  @Test
  public void testFoldReturnsIntegrationWithScoped() {
    late = true;
    disableComputeSideEffects();

    // if-then-else duplicate statement removal handles this case:
    testSame("function test(a) {if (a) {const a = Math.random();if(a) {return a;}} return a; }");
  }

  @Test
  public void testBug1059649() {
    // ensure that folding blocks with a single var node doesn't explode
    test("if(x){var y=3;}var z=5", "if(x)var y=3;var z=5");

    test("for(var i=0;i<10;i++){var y=3;}var z=5", "for(var i=0;i<10;i++)var y=3;var z=5");
    test("for(var i in x){var y=3;}var z=5", "for(var i in x)var y=3;var z=5");
    test("do{var y=3;}while(x);var z=5", "do var y=3;while(x);var z=5");
  }

  @Test
  public void testHookIfIntegration() {
    test("if (false){ x = 1; } else if (cond) { x = 2; } else { x = 3; }", "x=cond?2:3");

    test("x?void 0:y()", "x||y()");
    test("!x?void 0:y()", "x&&y()");
    test("x?y():void 0", "x&&y()");
  }

  @Test
  public void testRemoveDuplicateStatementsIntegration() {
    test(
        lines(
            "function z() {if (a) { return true }",
            "else if (b) { return true }",
            "else { return true }}"),
        "function z() {return true;}");

    test(
        lines(
            "function z() {if (a()) { return true }",
            "else if (b()) { return true }",
            "else { return true }}"),
        "function z() {a()||b();return true;}");
  }

  @Test
  public void testFoldLogicalOpIntegration() {
    test("if(x && true) z()", "x&&z()");
    test("if(x && false) z()", "");
    test("if(x || 3) z()", "z()");
    test("if(x || false) z()", "x&&z()");
    test("if(x==y && false) z()", "");
    test("if(y() || x || 3) z()", "y();z()");
  }

  @Test
  public void testFoldBitwiseOpStringCompareIntegration() {
    test("for (;-1 | 0;) {}", "for (;;);");
  }

  @Test
  public void testVarLiftingIntegration() {
    test("if(true);else var a;", "var a");
    test("if(false) foo();else var a;", "var a");
    test("if(true)var a;else;", "var a");
    test("if(false)var a;else;", "var a");
    test("if(false)var a,b;", "var b; var a");
    test("if(false){var a;var a;}", "var a");
    test("if(false)var a=function(){var b};", "var a");
    test("if(a)if(false)var a;else var b;", "var a;if(a)var b");
  }

  @Test
  public void testBug1438784() {
    test("for(var i=0;i<10;i++)if(x)x.y;", "for(var i=0;i<10;i++);");
  }

  @Test
  public void testFoldUselessForIntegration() {
    test("for(;!true;) { foo() }", "");
    test("for(;void 0;) { foo() }", "");
    test("for(;undefined;) { foo() }", "");
    test("for(;1;) foo()", "for(;;) foo()");
    test("for(;!void 0;) foo()", "for(;;) foo()");

    // Make sure proper empty nodes are inserted.
    test("if(foo())for(;false;){foo()}else bar()", "foo()||bar()");
    test("if(foo?.())for(;false;){foo?.()}else bar?.()", "foo?.()||bar?.()");
  }

  @Test
  public void testFoldUselessDoIntegration() {
    test("do { foo() } while(!true);", "foo()");
    test("do { foo() } while(void 0);", "foo()");
    test("do { foo() } while(undefined);", "foo()");
    test("do { foo() } while(!void 0);", "do { foo() } while(1);");

    // Make sure proper empty nodes are inserted.
    test("if(foo())do {foo()} while(false) else bar()", "foo()?foo():bar()");

    // Optional chaining version of these tests.
    test("do { foo?.() } while(!true);", "foo?.()");
    test("do { foo?.() } while(void 0);", "foo?.()");
    test("do { foo?.() } while(undefined);", "foo?.()");
    test("do { foo?.() } while(!void 0);", "do { foo?.() } while(1);");

    // Make sure proper empty nodes are inserted.
    test("if(foo?.())do {foo?.()} while(false) else bar?.()", "foo?.() ? foo?.() : bar?.()");
  }

  @Test
  public void testMinimizeExpr() {
    test("!!true", "");

    test("!!x()", "x()");
    test("!(!x()&&!y())", "x()||y()");
    test("x()||!!y()", "x()||y()");

    /* This is similar to the !!true case */
    test("!!x()&&y()", "x()&&y()");
    test("!!x?.()&&y?.()", "x?.()&&y?.()");
  }

  @Test
  public void testBug1509085() {
    numRepetitions = 1;
    late = true;
    // Such code can be replaced by using the simpler, equivalent optional chaining operator
    // `x?.()`.
    test("x ? x() : void 0", "x&&x();");
    testSame("y = x ? x() : void 0");

    testSame("x?.()");
  }

  @Test
  public void testBugIssue3() {
    testSame(
        lines(
            "function foo() {",
            "  if(sections.length != 1) children[i] = 0;",
            "  else var selectedid = children[i]",
            "}"));
  }

  @Test
  public void testBugIssue43() {
    testSame("function foo() {\n  if (a) { var b = 1; } else { a.b = 1; }\n}");
  }

  @Test
  public void testFoldNegativeBug() {
    test("for (;-3;){};", "for (;;);");
  }

  @Test
  public void testNoNormalizeLabeledExpr() {
    enableNormalize();
    testSame("var x; foo:{x = 3;}");
    testSame("var x; foo:x = 3;");
    disableNormalize();
  }

  @Test
  public void testShortCircuit1() {
    test("1 && a()", "a()");
  }

  @Test
  public void testShortCircuit2() {
    test("1 && a() && 2", "a()");
  }

  @Test
  public void testShortCircuit3() {
    test("a() && 1 && 2", "a()");
  }

  @Test
  public void testShortCircuit4() {
    test("a() && (1 && b())", "a() && b()");
    test("a() && 1 && b()", "a() && b()");
    test("(a() && 1) && b()", "a() && b()");

    test("a?.() && (1 && b?.())", "a?.() && b?.()");
    test("a?.() && 1 && b?.()", "a?.() && b?.()");
    test("(a?.() && 1) && b?.()", "a?.() && b?.()");
  }

  @Test
  public void testMinimizeExprCondition() {
    test("(x || true) && y()", "y()");
    test("(x || false) && y()", "x&&y()");
    test("(x && true) && y()", "x && y()");
    test("(x && false) && y()", "");
    test("a = x || false ? b : c", "a=x?b:c");
    test("do {x()} while((x && false) && y())", "x()");
    test("do {x?.()} while((x && false) && y?.())", "x?.()");
  }

  // A few miscellaneous cases where one of the peephole passes increases the
  // size, but it does it in such a way that a later pass can decrease it.
  // Test to make sure the overall change is a decrease, not an increase.
  @Test
  public void testMisc() {
    test("x = [foo()] && x", "x = (foo(),x)");
    test("x = foo() && false || bar()", "x = (foo(), bar())");
    test("if(foo() && false) z()", "foo()");
  }

  @Test
  public void testTrueFalseFolding() {
    late = true;
    test("x = true", "x = !0");
    test("x = false", "x = !1");
    test("x = !3", "x = !1");
    test("x = true && !0", "x = !0");
    test("x = !!!!!!!!!!!!3", "x = !0");
    test("if(!3){x()}", "");
    test("if(!!3){x()}", "x()");
  }

  @Test
  public void testCommaSplitingConstantCondition() {
    late = false;
    test("(b=0,b=1);if(b)x=b;", "b=0;b=1;x=b;");
    test("(b=0,b=1);if(b)x=b;", "b=0;b=1;x=b;");
  }

  @Test
  public void testAvoidCommaSplitting() {
    late = false;
    test("x(),y(),z()", "x();y();z()");
    test("x?.(),y?.(),z?.()", "x?.();y?.();z?.()");
    late = true;
    testSame("x(),y(),z()");
    testSame("x?.(),y?.(),z?.()");
  }

  @Test
  public void testObjectLiteral() {
    test("({})", "");
    test("({a:1})", "");
    test("({a:foo()})", "foo()");
    test("({'a':foo()})", "foo()");
    test("({a:foo?.()})", "foo?.()");
    test("({'a':foo?.()})", "foo?.()");
  }

  @Test
  public void testArrayLiteral() {
    test("([])", "");
    test("([1])", "");
    test("([a])", "");
    test("([foo()])", "foo()");
  }

  @Test
  public void testFoldIfs1() {
    test(
        "function f() {if (x) return 1; else if (y) return 1;}",
        "function f() {if (x||y) return 1;}");
    test(
        "function f() {if (x) return 1; else {if (y) return 1; else foo();}}",
        "function f() {if (x||y) return 1; foo();}");
  }

  @Test
  public void testFoldIfs2() {
    test("function f() {if (x) { a(); } else if (y) { a() }}", "function f() {x?a():y&&a();}");
  }

  @Test
  public void testFoldHook2() {
    test("function f(a) {if (!a) return a; else return a;}", "function f(a) {return a}");
  }

  @Test
  public void disable_testFoldHook1() {
    test("function f(a) {return (!a)?a:a;}", "function f(a) {return a}");
  }

  @Test
  public void testTemplateStringsKnownMethods() {
    test("x = `abcdef`.indexOf('b')", "x = 1");
    test("x = [`a`, `b`, `c`].join(``)", "x='abc'");
    test("x = `abcdef`.substr(0,2)", "x = 'ab'");
    test("x = `abcdef`.substring(0,2)", "x = 'ab'");
    test("x = `abcdef`.slice(0,2)", "x = 'ab'");
    test("x = `abcdef`.charAt(0)", "x = 'a'");
    test("x = `abcdef`.charCodeAt(0)", "x = 97");
    test("x = `abc`.toUpperCase()", "x = 'ABC'");
    test("x = `ABC`.toLowerCase()", "x = 'abc'");
    test("x = `\t\n\uFEFF\t asd foo bar \r\n`.trim()", "x = 'asd foo bar'");
    test("x = parseInt(`123`)", "x = 123");
    test("x = parseFloat(`1.23`)", "x = 1.23");
  }

  @Test
  public void testDontFoldKnownMethodsWithOptionalChaining() {
    // Known methods guarded by an optional chain are not folded
    test("x = `abcdef`.indexOf?.('b')", "x = \"abcdef\".indexOf?.(\"b\");");
    test("x = [`a`, `b`, `c`].join?.(``)", "x = [\"a\", \"b\", \"c\"].join?.(\"\")");
    test("x = `abcdef`.substr?.(0,2)", "x = \"abcdef\".substr?.(0,2)");
    test("x = `abcdef`.substring?.(0,2)", "x = \"abcdef\".substring?.(0,2)");
    test("x = `abcdef`.slice?.(0,2)", "x = \"abcdef\".slice?.(0,2)");
    test("x = `abcdef`.charAt?.(0)", "x = \"abcdef\".charAt?.(0)");
    test("x = `abcdef`.charCodeAt?.(0)", "x = \"abcdef\".charCodeAt?.(0)");
    test("x = `abc`.toUpperCase?.()", "x = \"abc\".toUpperCase?.()");
    test("x = `ABC`.toLowerCase?.()", "x = \"ABC\".toLowerCase?.()");
    test("x = parseInt?.(`123`)", "x = parseInt?.(\"123\")");
    test("x = parseFloat?.(`1.23`)", "x = parseFloat?.(\"1.23\")");
  }

  @Test
  public void testLabeledBlocks() {
    test(
        lines(
            "function b(m) {", //
            " return m;",
            " label: {",
            "   START('debug');",
            "   label2: {",
            "     alert('Shouldnt be here' + m);",
            "   }",
            "   END('debug');",
            "  }",
            "}"),
        lines(
            "function b(m) {", //
            "  return m;",
            "}"));
  }

  @Test
  public void testDoNotRemoveDeclarationOfUsedVariable() {
    test(
        lines(
            "var f = function() {", //
            "  return 1;",
            "  let b = 5;",
            "  do {",
            "    b--;",
            "  } while (b);",
            "  return 3;",
            "};"),
        lines(
            "var f = function() {", //
            "  return 1;",
            "};"));
  }

  @Test
  public void testDontRemoveExport() {
    test(
        lines(
            "function foo() {", //
            "  return 1;",
            "  alert(2);",
            "}",
            "export { foo as foo };"),
        lines(
            "function foo() {", //
            "  return 1;",
            "}",
            "export { foo as foo };"));
  }

  @Test
  public void testRemoveUnreachableCode1() {
    // switch statement with stuff after "return"
    test(
        lines(
            "function foo(){", //
            "  switch (foo) {",
            "    case 1:",
            "      x=1;",
            "      return;",
            "      break;",
            "    case 2: {",
            "      x=2;",
            "      return;",
            "      break;",
            "    }",
            "    default:",
            "  }",
            "}"),
        lines(
            "function foo() {", //
            "  switch (foo) {",
            "    case 1:",
            "      x=1;",
            "      break;",
            "    case 2:",
            "      x=2;",
            "  }",
            "}"));
  }

  @Test
  public void testRemoveUnreachableCode2() {
    // if/else statements with returns
    test(
        lines(
            "function bar(){", //
            "  if (foo)",
            "    x=1;",
            "  else if(bar) {",
            "    return;",
            "    x=2;",
            "  } else {",
            "    x=3;",
            "    return;",
            "    x=4;",
            "  }",
            "  return 5;",
            "  x=5;",
            "}"),
        lines(
            "function bar() {", //
            "  if (foo) {",
            "    x=1;",
            "    return 5;",
            "  }",
            "  bar || (x = 3);",
            "}"));

    // if statements without blocks
    // NOTE: This pass should never see while-loops, because normalization replaces them all with
    // for-loops.
    test(
        lines(
            "function foo() {", //
            "  if (x == 3) return;",
            "  x = 4;",
            "  y++;",
            "  for (; y == 4; ) {",
            "    return;",
            "    x = 3",
            "  }",
            "}"),
        lines(
            "function foo() {", //
            "  if (x != 3) {",
            "    x = 4;",
            "    y++;",
            "    for (; y == 4; ) {",
            "      break",
            "    }",
            "  }",
            "}"));

    // for/do/while loops
    test(
        lines(
            "function baz() {", //
            // Normalize always moves the for-loop initializer out of the loop.
            "  i = 0;",
            "  for (; i < n; i++) {",
            "    x = 3;",
            "    break;",
            "    x = 4",
            "  }",
            "  do {",
            "    x = 2;",
            "    break;",
            "    x = 4",
            "  } while (x == 4);",
            "  for (; i < 4; ) {",
            "    x = 3;",
            "    return;",
            "    x = 6",
            "  }",
            "}"),
        lines(
            "function baz() {", //
            "  i = 0;",
            "  for (; i < n; i++) {",
            "    x = 3;",
            "    break",
            "  }",
            "  do {",
            "    x = 2;",
            "    break",
            "  } while (x == 4);",
            "  for (; i < 4; ) {",
            "    x = 3;",
            "    break;",
            "  }",
            "}"));

    // return statements on the same level as conditionals
    test(
        lines(
            "function foo() {", //
            "  if (x == 3) {",
            "    return",
            "  }",
            "  return 5;",
            "  while (y == 4) {",
            "    x++;",
            "    return;",
            "    x = 4",
            "  }",
            "}"),
        lines(
            "function foo() {", //
            "  return x == 3 ? void 0 : 5;",
            "}"));

    // return statements on the same level as conditionals
    test(
        lines(
            "function foo() {", //
            "  return 3;",
            "  for (; y == 4;) {",
            "    x++;",
            "    return;",
            "    x = 4",
            "  }",
            "}"),
        lines(
            "function foo() {", //
            "  return 3",
            "}"));

    // try/catch statements
    test(
        lines(
            "function foo() {", //
            "  try {",
            "    x = 3;",
            "    return x + 1;",
            "    x = 5",
            "  } catch (e) {",
            "    x = 4;",
            "    return 5;",
            "    x = 5",
            "  }",
            "}"),
        lines(
            "function foo() {", //
            "  try {",
            "    x = 3;",
            "    return x + 1",
            "  } catch (e) {",
            "    x = 4;",
            "    return 5",
            "  }",
            "}"));

    // try/finally statements
    test(
        lines(
            "function foo() {", //
            "  try {",
            "    x = 3;",
            "    return x + 1;",
            "    x = 5",
            "  } finally {",
            "    x = 4;",
            "    return 5;",
            "    x = 5",
            "  }",
            "}"),
        lines(
            "function foo() {", //
            "  try {",
            "    x = 3;",
            "    return x + 1",
            "  } finally {",
            "    x = 4;",
            "    return 5",
            "  }",
            "}"));

    // try/catch/finally statements
    test(
        lines(
            "function foo() {", //
            "  try {",
            "    x = 3;",
            "    return x + 1;",
            "    x = 5",
            "  } catch (e) {",
            "    x = 3;",
            "    return;",
            "    x = 2",
            "  } finally {",
            "    x = 4;",
            "    return 5;",
            "    x = 5",
            "  }",
            "}"),
        lines(
            "function foo() {", //
            "  try {",
            "    x = 3;",
            "    return x + 1",
            "  } catch (e) {",
            "    x = 3;",
            "  } finally {",
            "    x = 4;",
            "    return 5",
            "  }",
            "}"));

    // test a combination of blocks
    test(
        lines(
            "function foo() {", //
            "  x = 3;",
            "  if (x == 4) {",
            "    x = 5;",
            "    return;",
            "    x = 6",
            "  } else {",
            "    x = 7",
            "  }",
            "  return 5;",
            "  x = 3",
            "}"),
        lines(
            "function foo() {", //
            "  x = 3;",
            "  if (x == 4) {",
            "    x = 5;",
            "  } else {",
            "    x = 7",
            "    return 5",
            "  }",
            "}"));

    // test removing multiple statements
    test(
        lines(
            "function foo() {", //
            "  return 1;",
            "  var x = 2;",
            "  var y = 10;",
            "  return 2;",
            "}"),
        lines(
            "function foo() {", //
            "  var y;",
            "  var x;",
            "  return 1",
            "}"));

    test(
        lines(
            "function foo() {", //
            "  return 1;",
            "  x = 2;",
            "  y = 10;",
            "  return 2;",
            "}"),
        lines(
            "function foo() {", //
            "  return 1",
            "}"));
  }

  @Test
  public void testRemoveUselessNameStatements() {
    test("a;", "");
    test("a.b;", "");
    test("a.b.MyClass.prototype.memberName;", "");
  }

  @Test
  public void testRemoveUselessStrings() {
    test("'a';", "");
  }

  @Test
  public void testNoRemoveUseStrict() {
    test("'use strict';", "'use strict'");
  }

  @Test
  public void testRemoveDo() {
    testSame("do { print(1); break } while(1)");
    // NOTE: This pass should never see while-loops, because normalization replaces them all with
    // for-loops.
    test(
        "for (; 1;) { break; do { print(1); break } while(1) }",
        "for (;  ;) { break;                                 }");
  }

  @Test
  public void testRemoveUselessLiteralValueStatements() {
    test("true;", "");
    test("'hi';", "");
    test("if (x) 1;", "");
    // NOTE: This pass should never see while-loops, because normalization replaces them all with
    // for-loops.
    test("for (; x;) 1;", "for (; x;);");
    test("do 1; while (x);", "for (;x;);");
    test("for (;;) 1;", "for (;;);");
    test(
        "switch(x){case 1:true;case 2:'hi';default:true}", //
        "");
  }

  @Test
  public void testConditionalDeadCode() {
    test("function f() { if (1) return 5; else return 5; x = 1}", "function f() { return 5;}");
  }

  @Test
  @Ignore(
      "TODO(b/301641291): this was originally supported by UnreachableCodeElimination, which was"
          + " removed in favor of peephole optimizations. Support this test case if found useful in"
          + " the real code.")
  public void testSwitchCase() {
    test("function f() { switch(x) { default: return 5; foo()}}", "function f() { return 5; }");
    testSame("function f() { switch(x) { default: return; case 1: foo(); bar()}}");
    test(
        "function f() { switch(x) { default: return; case 1: return 5;bar()}}",
        "function f() { switch(x) { default: return; case 1: return 5;}}");
  }

  @Test
  public void testTryCatchFinally1() {
    testSame("try {foo()} catch (e) {bar()}");
    testSame("try { try {foo()} catch (e) {bar()}} catch (x) {bar()}");
  }

  @Test
  public void testTryCatchFinally2() {
    testSame("try {var x = 1} catch (e) {e()}");
  }

  @Test
  public void testTryCatchFinally3() {
    testSame("try {var x = 1} catch (e) {e()} finally {x()}");
  }

  @Test
  public void testTryCatchFinally4() {
    testSame("try {var x = 1} catch (e) {e()} finally {}");
  }

  @Test
  public void testTryCatchFinally5() {
    testSame("try {var x = 1} finally {x()}");
    testSame("var x = 1");
  }

  @Test
  public void testTryCatchFinally6() {
    test("function f() {return; try{var x = 1}catch(e){} }", "function f() {var x;}");
  }

  @Test
  public void testRemovalRequiresRedeclaration() {
    // NOTE: This pass should never see while-loops, because normalization replaces them all with
    // for-loops.
    test(
        lines(
            "for (; 1;) {", //
            "  break;",
            "  var x = 1",
            "}"),
        lines(
            "var x;", //
            "for (;;) {",
            "  break;",
            "}"));
    test(
        lines(
            "for (; 1;) {", //
            "  break;",
            "  var x=1;",
            "  var y=1;",
            "}"),
        lines(
            "var y;", //
            "var x;",
            "for (;;) {",
            "  break;",
            "}"));
  }

  @Test
  public void testAssignPropertyOnCreatedObject() {
    testSame("this.foo = 3;");
    testSame("a.foo = 3;");
    testSame("bar().foo = 3;");
    testSame("({}).foo = bar();");
    testSame("(new X()).foo = 3;");

    test("({}).foo = 3;", "");
    test("(function() {}).prototype.toString = function(){};", "");
    test("(function() {}).prototype['toString'] = function(){};", "");
    test("(function() {}).prototype[f] = function(){};", "");
  }

  @Test
  public void testUselessUnconditionalReturn1() {
    test("function foo() { return }", "function foo() { }");
  }

  @Test
  public void testUselessUnconditionalReturn2() {
    test("function foo() { return; return; x=1 }", "function foo() { }");
  }

  @Test
  public void testUselessUnconditionalReturn3() {
    test("function foo() { return; return; var x=1}", "function foo() {var x}");
  }

  @Test
  public void testUselessUnconditionalReturn4() {
    test(
        "function foo() { return; function bar() {} }",
        "function foo() {         function bar() {} }");
  }

  @Test
  public void testUselessUnconditionalReturn5() {
    testSame("function foo() { return 5 }");
  }

  @Test
  public void testUselessUnconditionalReturn6() {
    test("function f() {switch (a) { case 'a': return}}", "function f() {}");
  }

  @Test
  @Ignore(
      "TODO(b/301641291): this was originally supported by UnreachableCodeElimination, which was"
          + " removed in favor of peephole optimizations. Support this test case if found useful in"
          + " the real code.")
  public void testUselessUnconditionalReturn7() {
    testSame("function f() {switch (a) { default: return; case 'a': alert(1)}}");
  }

  @Test
  @Ignore(
      "TODO(b/301641291): this was originally supported by UnreachableCodeElimination, which was"
          + " removed in favor of peephole optimizations. Support this test case if found useful in"
          + " the real code.")
  public void testUselessUnconditionalReturn8() {
    testSame("function f() {switch (a) { case 'a': return; default: alert(1)}}");
  }

  @Test
  public void testUselessUnconditionalReturn9() {
    testSame("function f() {switch (a) { case 'a': case foo(): }}");
  }

  @Test
  public void testUselessUnconditionalContinue() {
    test("for(;1;) {continue}", "for(;;) {}");
    test("for(;0;) {continue}", "");
  }

  @Test
  public void testUselessUnconditionalContinue2() {
    test(
        "X: for(;1;) { for(;1;) { if (x()) {continue X} x = 1}}",
        "X: for(; ;) { for(; ;) { if (x()) {continue X} x = 1}}");
  }

  @Test
  @Ignore(
      "TODO(b/301641291): this was originally supported by UnreachableCodeElimination, which was"
          + " removed in favor of peephole optimizations. Support this test case if found useful in"
          + " the real code.")
  public void testUselessUnconditionalContinue3() {
    test(
        "for(;1;) { X: for(;1;) { if (x()) {continue X} }}",
        "for(; ;) { X: for(; ;) { if (x()) {}}}");
  }

  @Test
  @Ignore(
      "TODO(b/301641291): this was originally supported by UnreachableCodeElimination, which was"
          + " removed in favor of peephole optimizations. Support this test case if found useful in"
          + " the real code.")
  public void testUselessUnconditionalContinue4() {
    test("do { continue } while(1);", "do {  } while(1);");
  }

  @Test
  @Ignore
  public void testUselessUnconditionalBreak1() {
    // TODO - b/335145701: `case 'a'` can not be eliminated as that would force "foo()" to be
    // always executed.
    // `case foo()` can only be eliminated if "foo()" can be determined to be side-effect
    // free.
    test(
        "switch (a) { case 'a': break; case foo(): }",
        "switch (a) { case 'a':        case foo(): }");
  }

  @Test
  public void testUselessUnconditionalBreak2() {
    test("switch (a) { case 'a': break }", "");
    test(
        "switch (a) { default: break; case 'a': }", //
        "");
  }

  @Test
  public void testUselessUnconditionalBreak3() {
    testSame("switch (a) { case 'a': alert(a); break; default: alert(a); }");
    testSame("switch (a) { default: alert(a); break; case 'a': alert(a); }");
  }

  @Test
  public void testUselessUnconditionalBreak4() {
    test("X: {switch (a) { case 'a': break X}}", "");

    test(
        "X: {switch (a) { case 'a': if (a()) {break X}  a = 1; }}", //
        "X: {switch (a) { case 'a': a() || (a = 1); }}");
  }

  @Test
  public void testUselessUnconditionalBreak5() {
    test(
        "X: {switch (a) { case 'a': if (a()) {break X}}}", //
        "X: {switch (a) { case 'a':     a()           }}");
  }

  @Test
  public void testUselessUnconditionalBreak6() {
    test(
        "X: {switch (a) { case 'a': if (a()) {break X}}}", //
        "X: {switch (a) { case 'a':     a();          }}");
  }

  @Test
  public void testUselessUnconditionalBreak7() {
    // There is no reason to keep these
    testSame("do { break } while(1);");
    test("for(;1;) { break }", "for(;;) { break; }");
  }

  @Test
  public void testIteratedRemoval1() {
    test("switch (a) { case 'a': break; case 'b': break; case 'c': break }", "");
  }

  @Test
  public void testIteratedRemoval2() {
    test(
        "function foo() { switch (a) { case 'a':return; case 'b':return; case 'c':return }}",
        "function foo() { }");
  }

  @Test
  public void testIteratedRemoval3() {
    test(
        "for (;;) {\n"
            + "   switch (a) {\n"
            + "   case 'a': continue;\n"
            + "   case 'b': continue;\n"
            + "   case 'c': continue;\n"
            + "   }\n"
            + " }",
        " for (;;) { }");
  }

  @Test
  public void testIteratedRemoval4() {
    test("function foo() { if (x) { return; } if (x) { return; }}", "function foo() {}");
  }

  @Test
  public void testIteratedRemoval5() {
    test(
        "var x; \n"
            + " out: { \n"
            + "   try { break out; } catch (e) { break out; } \n"
            + "   x = undefined; \n"
            + " }",
        "var x;");
  }

  @Test
  public void testIssue311() {
    test(
        lines(
            "function a(b) {",
            "  switch (b.v) {",
            "    case 'SWITCH':",
            "      if (b.i >= 0) {",
            "        return b.o;",
            "      } else {",
            "        return;",
            "      }",
            "      break;",
            "  }",
            "}"),
        lines(
            "function a(b) {",
            "  switch (b.v) {",
            "    case 'SWITCH':",
            "      if (b.i >= 0) {",
            "        return b.o;",
            "      }",
            "  }",
            "}"));
  }

  @Test
  public void testIssue4177428a() {
    testSame(
        lines(
            "f = function() {",
            "  var action;",
            "  a: {",
            "    var proto = null;",
            "    try {",
            "      proto = new Proto",
            "    } finally {",
            "      action = proto;",
            "      break a", // Keep this...
            "    }",
            "  }",
            "  alert(action)", // and this.
            "};"));
  }

  @Test
  public void testIssue4177428b() {
    test(
        lines(
            "f = function() {",
            "  var action;",
            "  a: {",
            "    var proto = null;",
            "    try {",
            "    try {",
            "      proto = new Proto",
            "    } finally {",
            "      action = proto;",
            "      break a", // Keep this...
            "    }",
            "    } finally {",
            "    }",
            "  }",
            "  alert(action)", // and this.
            "};"),
        lines(
            "f = function() {",
            "  var action;",
            "  a: {",
            "    var proto = null;",
            "    try {",
            "      proto = new Proto",
            "    } finally {",
            "      action = proto;",
            "      break a", // Keep this...
            "    }",
            "  }",
            "  alert(action)", // and this.
            "};"));
  }

  @Test
  public void testIssue4177428c() {
    test(
        lines(
            "f = function() {",
            "  var action;",
            "  a: {",
            "    var proto = null;",
            "    try {",
            "    } finally {",
            "    try {",
            "      proto = new Proto",
            "    } finally {",
            "      action = proto;",
            "      break a", // Keep this...
            "    }",
            "    }",
            "  }",
            "  alert(action)",
            // and this.
            "};"),
        lines(
            "f = function() {",
            "  var action;",
            "  a: {",
            "    var proto = null;",
            "    try {",
            "      proto = new Proto",
            "    } finally {",
            "      action = proto;",
            "      break a", // Keep this...
            "    }",
            "  }",
            "  alert(action)", // and this.
            "};"));
  }

  @Test
  public void testIssue4177428_continue() {
    test(
        lines(
            "f = function() {", //
            "  var action;",
            "  a: do {",
            "    var proto = null;",
            "    try {",
            "      proto = new Proto",
            "    } finally {",
            "      action = proto;",
            "      continue a",
            // Keep this...
            "    }",
            "  } while(false)",
            "  alert(action)",
            // and this.
            "};"),
        lines(
            "f = function() {", //
            "  var action;",
            "  a: do {",
            "    var proto = null;",
            "    try {",
            "      proto = new Proto",
            "    } finally {",
            "      action = proto;",
            "      continue a",
            // Keep this...
            "    }",
            "  } while(0)",
            "  alert(action)",
            // and this.
            "};"));
  }

  @Test
  public void testIssue4177428_multifinally() {
    test(
        lines(
            "a: {",
            " try {",
            "   try {",
            "   } finally {",
            "     break a;",
            "   }",
            " } finally {",
            "   x = 1;",
            " }",
            "}"),
        lines(
            "a: {", //
            "  x = 1;",
            "}"));
  }

  @Test
  public void testIssue5215541_deadVarDeclar() {
    test(
        "       throw 1; var x;", //
        "var x; throw 1;       ");
    testSame("throw 1; function x() {}");
    test(
        "throw 1; var x; var y;                ", //
        "                var y; var x; throw 1;");
    test(
        "       throw 1; var x = foo", //
        "var x; throw 1");
  }

  @Test
  public void testForInLoop() {
    testSame("var x; for(x in y) {}");
  }

  @Test
  public void testDontRemoveBreakInTryFinally() {
    testSame(
        lines(
            "function f() {", //
            "  b: {",
            "    try {",
            "      throw 9;",
            "    } finally {",
            "      break b;",
            "    }",
            "  }",
            "  return 1;",
            "}"));
  }

  @Test
  public void testDontRemoveBreakInTryFinallySwitch() {
    testSame(
        lines(
            "function f() {", //
            "  b: {",
            "    try {",
            "      throw 9;",
            "    } finally {",
            "      switch (x) {",
            "        case 1:",
            "          break b;",
            "      }",
            "    }",
            "  }",
            "  return 1;",
            "}"));
  }

  @Test
  // This was originally supported by UnreachableCodeElimination, which was removed in favor
  // of peephole optimizations. Support this test case if found useful in the real code.
  public void testIssue1001() {
    test(
        "function f(x) { x.property = 3; } f({})", //
        "function f(x) { x.property = 3; }");
    test(
        "function f(x) { x.property = 3; } new f({})", //
        "function f(x) { x.property = 3; }");
  }

  @Test
  public void testLetConstBlocks() {
    test(
        "function f() {return 1; let a; }", //
        "function f() {return 1;        }");

    test(
        "function f() { return 1; const a = 1; }", //
        "function f() { return 1;              }");

    test(
        "function f() { x = 1; { let g; return x; } let y;}",
        "function f() { x = 1;   let g; return x;         } ");
  }

  @Test
  public void testArrowFunctions() {
    test("f(x => {return x; j = 1})", "f(x => {return x;})");

    testSame("f( () => {return 1;})");
  }

  @Test
  public void testForOf() {
    test("for(x of i){ 1; }", "for(x of i) {}");

    testSame("for(x of i){}");
  }

  @Test
  public void testLetConstBlocks_inFunction_exportedFromEs6Module() {
    test(
        lines(
            "function f() {", //
            "  return 1;",
            "  let a;",
            "}",
            "export { f as f };"),
        lines(
            "function f() {", //
            "  return 1;",
            "}",
            "export { f as f };"));

    test(
        lines(
            "function f() {", //
            "  return 1;",
            "  const a = 1;",
            "}",
            "export { f as f };"),
        lines(
            "function f() {", //
            "  return 1;",
            "}",
            "export { f as f };"));

    test(
        lines(
            "function f() {", //
            "  x = 1;",
            "  {",
            "    let g;",
            "    return x",
            "  }",
            "  let y",
            "}",
            "export { f as f };"),
        lines(
            "function f() {", //
            "  x = 1;",
            "  let g;",
            "  return x;",
            "}",
            "export { f as f };"));
  }

  @Test
  public void testRemoveUnreachableCode_withES6Modules() {
    // Switch statements
    test(
        lines(
            "function foo() {",
            "  switch (foo) {",
            "    case 1:",
            "      x = 1;",
            "      return;",
            "      break;",
            "    case 2: {",
            "      x = 2;",
            "      return;",
            "      break;",
            "    }",
            "    default:",
            "  }",
            "}",
            "export { foo as foo };"),
        lines(
            "function foo() {",
            "  switch (foo) {",
            "    case 1:",
            "      x = 1;",
            "      break;",
            "    case 2:",
            "      x = 2;",
            "  }",
            "}",
            "export { foo as foo };"));

    // if/else statements with returns
    test(
        lines(
            "function bar() {",
            "  if (foo)",
            "    x=1;",
            "  else if(bar) {",
            "    return;",
            "    x=2;",
            "  } else {",
            "    x=3;",
            "    return;",
            "    x=4;",
            "  }",
            "  return 5;",
            "  x=5;",
            "}",
            "export { bar as bar };"),
        lines(
            "function bar() {", //
            "  if (foo) {",
            "    x=1;",
            "    return 5;",
            "  }",
            "  bar || (x = 3);",
            "}",
            "export { bar as bar };"));
  }

  @Test
  public void testComputedClassPropertyNotRemoved() {
    testSame("class Foo { ['x']() {} }");
  }

  @Test
  public void testClassExtendsNotRemoved() {
    testSame(
        lines(
            "function f() {}", //
            "class Foo extends f() {}"));
  }

  @Test
  public void testRemoveUnreachableCodeInComputedPropertIife() {
    test(
        lines(
            "class Foo {", //
            "  [function() {",
            "    1; return 'x';",
            "  }()]() { return 1; }",
            "}"),
        lines(
            "class Foo {", //
            "  [function() {",
            "    return 'x';",
            "  }()]() { return 1; }",
            "}"));
  }

  @Test
  public void testStaticBlockRemoved() {
    test("class Foo { static {} }", "class Foo {  }");
  }

  @Test
  public void testRemoveUnreachableCodeInStaticBlock1() {
    // TODO(b/240443227): Unreachable/Useless code isn't removed in static blocks
    test(
        lines(
            "class Foo {", //
            "  static {",
            "    switch (a) { case 'a': break }",
            "    try {var x = 1} catch (e) {e()}",
            "    true;",
            "    if (x) 1;",
            "  }",
            "}"),
        lines(
            "class Foo {", //
            "  static {",
            "    try {var x = 1} catch (e) {e()}",
            "  }",
            "}"));
  }
}
