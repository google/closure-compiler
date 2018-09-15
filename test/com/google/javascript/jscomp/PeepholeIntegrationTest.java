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
    this.late = false;
    this.numRepetitions = 2;
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    PeepholeOptimizationsPass peepholePass =
        new PeepholeOptimizationsPass(
            compiler,
            getName(),
            new PeepholeMinimizeConditions(late),
            new PeepholeSubstituteAlternateSyntax(late),
            new PeepholeRemoveDeadCode(),
            new PeepholeFoldConstants(late, false /* useTypes */),
            new PeepholeReplaceKnownMethods(late, false /* useTypes */));

    return peepholePass;
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

  /** Check that removing blocks with 1 child works */
  @Test
  public void testFoldOneChildBlocksIntegration() {
     test("function f(){switch(foo()){default:{break}}}",
          "function f(){foo()}");

     test("function f(){switch(x){default:{break}}}",
          "function f(){}");

     test("function f(){switch(x){default:x;case 1:return 2}}",
          "function f(){switch(x){default:case 1:return 2}}");

     // ensure that block folding does not break hook ifs
     test("if(x){if(true){foo();foo()}else{bar();bar()}}",
          "if(x){foo();foo()}");

     test("if(x){if(false){foo();foo()}else{bar();bar()}}",
          "if(x){bar();bar()}");

     // Cases where the then clause has no side effects.
     test("if(x()){}", "x()");

     test("if(x()){} else {x()}", "x()||x()");
     test("if(x){}", ""); // Even the condition has no side effect.
     test("if(a()){A()} else if (b()) {} else {C()}", "a()?A():b()||C()");

     test("if(a()){} else if (b()) {} else {C()}",
          "a() || (b() || C())");
     test("if(a()){A()} else if (b()) {} else if (c()) {} else{D()}",
          "a() ? A() : b() || (c() || D())");
     test("if(a()){} else if (b()) {} else if (c()) {} else{D()}",
          "a() || (b() || (c() || D()))");
     test("if(a()){A()} else if (b()) {} else if (c()) {} else{}",
          "a()?A():b()||c()");

     // Verify that non-global scope works.
     test("function foo(){if(x()){}}", "function foo(){x()}");

  }

  @Test
  public void testFoldOneChildBlocksStringCompare() {
    test("if (x) {if (y) { var x; } } else{ var z; }",
         "if (x) { if (y) var x } else var z");
  }

  /** Test a particularly hairy edge case. */
  @Test
  public void testNecessaryDanglingElse() {
    test("if (x) if (y){ y(); z() } else; else x()",
         "if (x) { if(y) { y(); z() } } else x()");
  }

  /** Try to minimize returns */
  @Test
  public void testFoldReturnsIntegration() {
    // if-then-else duplicate statement removal handles this case:
    test("function f(){if(x)return;else return}",
         "function f(){}");
  }

  @Test
  public void testBug1059649() {
    // ensure that folding blocks with a single var node doesn't explode
    test("if(x){var y=3;}var z=5", "if(x)var y=3;var z=5");

    // With normalization, we no longer have this case.
    testSame("if(x){var y=3;}else{var y=4;}var z=5");
    test("while(x){var y=3;}var z=5", "while(x)var y=3;var z=5");
    test("for(var i=0;i<10;i++){var y=3;}var z=5",
         "for(var i=0;i<10;i++)var y=3;var z=5");
    test("for(var i in x){var y=3;}var z=5",
         "for(var i in x)var y=3;var z=5");
    test("do{var y=3;}while(x);var z=5", "do var y=3;while(x);var z=5");
  }

  @Test
  public void testHookIfIntegration() {
    test("if (false){ x = 1; } else if (cond) { x = 2; } else { x = 3; }",
         "x=cond?2:3");

    test("x?void 0:y()", "x||y()");
    test("!x?void 0:y()", "x&&y()");
    test("x?y():void 0", "x&&y()");
  }

  @Test
  public void testRemoveDuplicateStatementsIntegration() {
    enableNormalize();
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
    test("while (-1 | 0) {}", "while (1);");
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
  public void testFoldUselessWhileIntegration() {
    test("while(!true) { foo() }", "");
    test("while(!false) foo() ", "while(1) foo()");
    test("while(!void 0) foo()", "while(1) foo()");

    // Make sure proper empty nodes are inserted.
    test("if(foo())while(false){foo()}else bar()", "foo()||bar()");
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
  }

  @Test
  public void testFoldUselessDoIntegration() {
    test("do { foo() } while(!true);", "foo()");
    test("do { foo() } while(void 0);", "foo()");
    test("do { foo() } while(undefined);", "foo()");
    test("do { foo() } while(!void 0);", "do { foo() } while(1);");

    // Make sure proper empty nodes are inserted.
    test("if(foo())do {foo()} while(false) else bar()", "foo()?foo():bar()");
  }

  @Test
  public void testMinimizeWhileConstantConditionIntegration() {
    test("while(!false) foo()", "while(1) foo()");
    test("while(202) foo()", "while(1) foo()");
    test("while(Infinity) foo()", "while(1) foo()");
    test("while('text') foo()", "while(1) foo()");
    test("while([]) foo()", "while(1) foo()");
    test("while({}) foo()", "while(1) foo()");
    test("while(/./) foo()", "while(1) foo()");
  }

  @Test
  public void testMinimizeExpr() {
    test("!!true", "");

    test("!!x()", "x()");
    test("!(!x()&&!y())", "x()||y()");
    test("x()||!!y()", "x()||y()");

    /* This is similar to the !!true case */
    test("!!x()&&y()", "x()&&y()");
  }

  @Test
  public void testBug1509085() {
    this.numRepetitions = 1;
    this.late = true;
    test("x ? x() : void 0", "x&&x();");
    testSame("y = x ? x() : void 0");
  }

  @Test
  public void testBugIssue3() {
    testSame(lines(
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
    test("while(-3){};", "while(1);");
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
  }

  @Test
  public void testMinimizeExprCondition() {
    test("(x || true) && y()", "y()");
    test("(x || false) && y()", "x&&y()");
    test("(x && true) && y()", "x && y()");
    test("(x && false) && y()", "");
    test("a = x || false ? b : c", "a=x?b:c");
    test("do {x()} while((x && false) && y())", "x()");
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
    late = true;
    testSame("x(),y(),z()");
  }

  @Test
  public void testObjectLiteral() {
    test("({})", "");
    test("({a:1})", "");
    test("({a:foo()})", "foo()");
    test("({'a':foo()})", "foo()");
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
    test("function f() {if (x) return 1; else if (y) return 1;}",
         "function f() {if (x||y) return 1;}");
    test("function f() {if (x) return 1; else {if (y) return 1; else foo();}}",
         "function f() {if (x||y) return 1; foo();}");
  }

  @Test
  public void testFoldIfs2() {
    test("function f() {if (x) { a(); } else if (y) { a() }}",
         "function f() {x?a():y&&a();}");
  }

  @Test
  public void testFoldHook2() {
    test("function f(a) {if (!a) return a; else return a;}",
         "function f(a) {return a}");
  }

  public void disable_testFoldHook1() {
    test("function f(a) {return (!a)?a:a;}",
         "function f(a) {return a}");
  }

  @Test
  public void testTemplateStringsKnownMethods() {
    enableNormalize();
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
}
