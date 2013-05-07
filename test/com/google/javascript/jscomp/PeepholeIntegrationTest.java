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

/**
 * Tests for the interaction between multiple peephole passes.
 */
public class PeepholeIntegrationTest extends CompilerTestCase {

  private boolean late = true;

  // TODO(user): Remove this when we no longer need to do string comparison.
  private PeepholeIntegrationTest(boolean compareAsTree) {
    super("", compareAsTree);
  }

  public PeepholeIntegrationTest() {
    super("");
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    this.late = false;
    enableLineNumberCheck(true);

    // TODO(nicksantos): Turn this on. There are some normalizations
    // that cause weirdness here.
    disableNormalize();
  }

  @Override
  public CompilerPass getProcessor(final Compiler compiler) {
    PeepholeOptimizationsPass peepholePass =
      new PeepholeOptimizationsPass(compiler,
        new PeepholeMinimizeConditions(late),
        new PeepholeSubstituteAlternateSyntax(late),
        new PeepholeRemoveDeadCode(),
        new PeepholeFoldConstants(late)
      );

    return peepholePass;
  }

  @Override
  protected int getNumRepetitions() {
    // Reduce this to 2 if we get better expression evaluators.
    return 4;
  }

  private void foldSame(String js) {
    testSame(js);
  }

  private void fold(String js, String expected) {
    test(js, expected);
  }

  // TODO(user): This is same as fold() except it uses string comparison. Any
  // test that needs tell us where a folding is constructing an invalid AST.
  private static void assertResultString(String js, String expected) {
    PeepholeIntegrationTest scTest = new PeepholeIntegrationTest(false);

    scTest.disableNormalize();

    scTest.test(js, expected);
  }

  public void testTrueFalse() {
    late = false;
    foldSame("x = true");
    foldSame("x = false");
    fold("x = !1", "x = false");
    fold("x = !0", "x = true");
    late = true;
    fold("x = true", "x = !0");
    fold("x = false", "x = !1");
    foldSame("x = !1");
    foldSame("x = !0");
  }

  /** Check that removing blocks with 1 child works */
  public void testFoldOneChildBlocksIntegration() {
     fold("function f(){switch(foo()){default:{break}}}",
          "function f(){foo()}");

     fold("function f(){switch(x){default:{break}}}",
          "function f(){}");

     fold("function f(){switch(x){default:x;case 1:return 2}}",
          "function f(){switch(x){default:case 1:return 2}}");

     // ensure that block folding does not break hook ifs
     fold("if(x){if(true){foo();foo()}else{bar();bar()}}",
          "if(x){foo();foo()}");

     fold("if(x){if(false){foo();foo()}else{bar();bar()}}",
          "if(x){bar();bar()}");

     // Cases where the then clause has no side effects.
     fold("if(x()){}", "x()");

     fold("if(x()){} else {x()}", "x()||x()");
     fold("if(x){}", ""); // Even the condition has no side effect.
     fold("if(a()){A()} else if (b()) {} else {C()}", "a()?A():b()||C()");

     fold("if(a()){} else if (b()) {} else {C()}",
          "a()||b()||C()");
     fold("if(a()){A()} else if (b()) {} else if (c()) {} else{D()}",
          "a()?A():b()||c()||D()");
     fold("if(a()){} else if (b()) {} else if (c()) {} else{D()}",
          "a()||b()||c()||D()");
     fold("if(a()){A()} else if (b()) {} else if (c()) {} else{}",
          "a()?A():b()||c()");

     // Verify that non-global scope works.
     fold("function foo(){if(x()){}}", "function foo(){x()}");

  }

  public void testFoldOneChildBlocksStringCompare() {
    // The expected parse tree has a BLOCK structure around the true branch.
    assertResultString("if(x){if(y){var x;}}else{var z;}",
        "if(x){if(y)var x}else var z");
  }

  /** Test a particularly hairy edge case. */
  public void testNecessaryDanglingElse() {
    // The extra block is added by CodeGenerator. The logic to avoid ambiguous
    // else clauses used to be in FoldConstants, so the test is here for
    // legacy reasons.
    assertResultString(
        "if(x)if(y){y();z()}else;else x()", "if(x){if(y){y();z()}}else x()");
  }

  /** Try to minimize returns */
  public void testFoldReturnsIntegration() {
    // if-then-else duplicate statement removal handles this case:
    fold("function f(){if(x)return;else return}",
         "function f(){}");
  }

  public void testBug1059649() {
    // ensure that folding blocks with a single var node doesn't explode
    fold("if(x){var y=3;}var z=5", "if(x)var y=3;var z=5");

    // With normalization, we no longer have this case.
    foldSame("if(x){var y=3;}else{var y=4;}var z=5");
    fold("while(x){var y=3;}var z=5", "while(x)var y=3;var z=5");
    fold("for(var i=0;i<10;i++){var y=3;}var z=5",
         "for(var i=0;i<10;i++)var y=3;var z=5");
    fold("for(var i in x){var y=3;}var z=5",
         "for(var i in x)var y=3;var z=5");
    fold("do{var y=3;}while(x);var z=5", "do var y=3;while(x);var z=5");
  }

  public void testHookIfIntegration() {
    fold("if (false){ x = 1; } else if (cond) { x = 2; } else { x = 3; }",
         "x=cond?2:3");

    fold("x?void 0:y()", "x||y()");
    fold("!x?void 0:y()", "x&&y()");
    fold("x?y():void 0", "x&&y()");
  }

  public void testRemoveDuplicateStatementsIntegration() {
    fold("function z() {if (a) { return true }" +
         "else if (b) { return true }" +
         "else { return true }}",
         "function z() {return true;}");

    fold("function z() {if (a()) { return true }" +
         "else if (b()) { return true }" +
         "else { return true }}",
         "function z() {a()||b();return true;}");
  }

  public void testFoldLogicalOpIntegration() {
    test("if(x && true) z()", "x&&z()");
    test("if(x && false) z()", "");
    fold("if(x || 3) z()", "z()");
    fold("if(x || false) z()", "x&&z()");
    test("if(x==y && false) z()", "");
    // TODO(user): This can be further optimized.
    fold("if(y() || x || 3) z()", "(y()||1)&&z()");
  }

  public void testFoldBitwiseOpStringCompareIntegration() {
    assertResultString("while(-1 | 0){}", "while(1);");
  }

  public void testVarLiftingIntegration() {
    fold("if(true);else var a;", "var a");
    fold("if(false) foo();else var a;", "var a");
    fold("if(true)var a;else;", "var a");
    fold("if(false)var a;else;", "var a");
    fold("if(false)var a,b;", "var b; var a");
    fold("if(false){var a;var a;}", "var a");
    fold("if(false)var a=function(){var b};", "var a");
    fold("if(a)if(false)var a;else var b;", "var a;if(a)var b");
  }

  public void testBug1438784() throws Exception {
    fold("for(var i=0;i<10;i++)if(x)x.y;", "for(var i=0;i<10;i++);");
  }

  public void testFoldUselessWhileIntegration() {
    fold("while(!true) { foo() }", "");
    fold("while(!false) foo() ", "while(1) foo()");
    fold("while(!void 0) foo()", "while(1) foo()");

    // Make sure proper empty nodes are inserted.
    fold("if(foo())while(false){foo()}else bar()", "foo()||bar()");
  }

  public void testFoldUselessForIntegration() {
    fold("for(;!true;) { foo() }", "");
    fold("for(;void 0;) { foo() }", "");
    fold("for(;undefined;) { foo() }", "");
    fold("for(;1;) foo()", "for(;;) foo()");
    fold("for(;!void 0;) foo()", "for(;;) foo()");

    // Make sure proper empty nodes are inserted.
    fold("if(foo())for(;false;){foo()}else bar()", "foo()||bar()");
  }

  public void testFoldUselessDoIntegration() {
    test("do { foo() } while(!true);", "foo()");
    fold("do { foo() } while(void 0);", "foo()");
    fold("do { foo() } while(undefined);", "foo()");
    fold("do { foo() } while(!void 0);", "do { foo() } while(1);");

    // Make sure proper empty nodes are inserted.
    test("if(foo())do {foo()} while(false) else bar()", "foo()?foo():bar()");
  }

  public void testMinimizeWhileConstantConditionIntegration() {
    fold("while(!false) foo()", "while(1) foo()");
    fold("while(202) foo()", "while(1) foo()");
    fold("while(Infinity) foo()", "while(1) foo()");
    fold("while('text') foo()", "while(1) foo()");
    fold("while([]) foo()", "while(1) foo()");
    fold("while({}) foo()", "while(1) foo()");
    fold("while(/./) foo()", "while(1) foo()");
  }

  public void testMinimizeExpr() {
    test("!!true", "");

    fold("!!x()", "x()");
    test("!(!x()&&!y())", "x()||y()");
    fold("x()||!!y()", "x()||y()");

    /* This is similar to the !!true case */
    fold("!!x()&&y()", "x()&&y()");
  }

  public void testBug1509085() {
    PeepholeIntegrationTest oneRepetitiontest = new PeepholeIntegrationTest() {
      @Override
      protected int getNumRepetitions() {
        return 1;
      }
    };

    oneRepetitiontest.test("x ? x() : void 0", "x&&x();");
    oneRepetitiontest.foldSame("y = x ? x() : void 0");
  }

  public void testBugIssue3() {
    foldSame("function foo() {" +
             "  if(sections.length != 1) children[i] = 0;" +
             "  else var selectedid = children[i]" +
             "}");
  }

  public void testBugIssue43() {
    foldSame("function foo() {" +
             "  if (a) { var b = 1; } else { a.b = 1; }" +
             "}");
  }

  public void testFoldNegativeBug() {
    fold("while(-3){};", "while(1);");
  }

  public void testNoNormalizeLabeledExpr() {
    enableNormalize(true);
    foldSame("var x; foo:{x = 3;}");
    foldSame("var x; foo:x = 3;");
  }

  public void testShortCircuit1() {
    test("1 && a()", "a()");
  }

  public void testShortCircuit2() {
    test("1 && a() && 2", "a()");
  }

  public void testShortCircuit3() {
    test("a() && 1 && 2", "a()");
  }

  public void testShortCircuit4() {
    test("a() && (1 && b())", "a() && b()");
    test("a() && 1 && b()", "a() && b()");
    test("(a() && 1) && b()", "a() && b()");
  }

  public void testMinimizeExprCondition() {
    fold("(x || true) && y()", "y()");
    fold("(x || false) && y()", "x&&y()");
    fold("(x && true) && y()", "x && y()");
    fold("(x && false) && y()", "");
    fold("a = x || false ? b : c", "a=x?b:c");
    fold("do {x()} while((x && false) && y())", "x()");
  }

  public void testTrueFalseFolding() {
    late = true;
    fold("x = true", "x = !0");
    fold("x = false", "x = !1");
    fold("x = !3", "x = !1");
    fold("x = true && !0", "x = !0");
    fold("x = !!!!!!!!!!!!3", "x = !0");
    fold("if(!3){x()}", "");
    fold("if(!!3){x()}", "x()");
  }

  public void testCommaSplitingConstantCondition() {
    late = false;
    fold("(b=0,b=1);if(b)x=b;", "b=0;b=1;x=b;");
    fold("(b=0,b=1);if(b)x=b;", "b=0;b=1;x=b;");
  }

  public void testAvoidCommaSplitting() {
    late = false;
    fold("x(),y(),z()", "x();y();z()");
    late = true;
    foldSame("x(),y(),z()");
  }

  public void testObjectLiteral() {
    test("({})", "");
    test("({a:1})", "");
    test("({a:foo()})", "foo()");
    test("({'a':foo()})", "foo()");
  }

  public void testArrayLiteral() {
    test("([])", "");
    test("([1])", "");
    test("([a])", "");
    test("([foo()])", "foo()");
  }

  public void testFoldIfs1() {
    fold("function f() {if (x) return 1; else if (y) return 1;}",
         "function f() {if (x||y) return 1;}");
    fold("function f() {if (x) return 1; else {if (y) return 1; else foo();}}",
         "function f() {if (x||y) return 1; foo();}");
  }

  public void testFoldIfs2() {
    fold("function f() {if (x) { a(); } else if (y) { a() }}",
         "function f() {x?a():y&&a();}");
  }

  public void testFoldHook2() {
    fold("function f(a) {if (!a) return a; else return a;}",
         "function f(a) {return a}");
  }

  public void disable_testFoldHook1() {
    fold("function f(a) {return (!a)?a:a;}",
         "function f(a) {return a}");
  }
}
