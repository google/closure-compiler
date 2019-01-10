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

/**
 * Tests for {@link PeepholeMinimizeConditions} in isolation. Tests for the interaction of multiple
 * peephole passes are in PeepholeIntegrationTest.
 */
@RunWith(JUnit4.class)
public final class PeepholeMinimizeConditionsTest extends CompilerTestCase {

  private boolean late = true;

  public PeepholeMinimizeConditionsTest() {
    super(DEFAULT_EXTERNS);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    late = true;
    disableTypeCheck();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    PeepholeOptimizationsPass peepholePass =
        new PeepholeOptimizationsPass(
            compiler, getName(), new PeepholeMinimizeConditions(late));
    peepholePass.setRetraverseOnChange(false);
    return peepholePass;
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  private void foldSame(String js) {
    testSame(js);
  }

  private void fold(String js, String expected) {
    test(js, expected);
  }

  /** Check that removing blocks with 1 child works */
  @Test
  public void testFoldOneChildBlocks() {
    late = false;
    fold("function f(){if(x)a();x=3}",
        "function f(){x&&a();x=3}");
    fold("function f(){if(x){a()}x=3}",
        "function f(){x&&a();x=3}");
    fold("function f(){if(x){return 3}}",
        "function f(){if(x)return 3}");
    fold("function f(){if(x){a()}}",
        "function f(){x&&a()}");
    fold("function f(){if(x){throw 1}}", "function f(){if(x)throw 1;}");

    // Try it out with functions
    fold("function f(){if(x){foo()}}", "function f(){x&&foo()}");
    fold("function f(){if(x){foo()}else{bar()}}",
         "function f(){x?foo():bar()}");

    // Try it out with properties and methods
    fold("function f(){if(x){a.b=1}}", "function f(){if(x)a.b=1}");
    fold("function f(){if(x){a.b*=1}}", "function f(){x&&(a.b*=1)}");
    fold("function f(){if(x){a.b+=1}}", "function f(){x&&(a.b+=1)}");
    fold("function f(){if(x){++a.b}}", "function f(){x&&++a.b}");
    fold("function f(){if(x){a.foo()}}", "function f(){x&&a.foo()}");

    // Try it out with throw/catch/finally [which should not change]
    foldSame("function f(){try{foo()}catch(e){bar(e)}finally{baz()}}");

    // Try it out with switch statements
    foldSame("function f(){switch(x){case 1:break}}");

    // Do while loops stay in a block if that's where they started
    foldSame("function f(){if(e1){do foo();while(e2)}else foo2()}");
    // Test an obscure case with do and while
    fold("if(x){do{foo()}while(y)}else bar()",
         "if(x){do foo();while(y)}else bar()");

    // Play with nested IFs
    fold("function f(){if(x){if(y)foo()}}",
         "function f(){x && (y && foo())}");
    fold("function f(){if(x){if(y)foo();else bar()}}",
         "function f(){x&&(y?foo():bar())}");
    fold("function f(){if(x){if(y)foo()}else bar()}",
         "function f(){x?y&&foo():bar()}");
    fold("function f(){if(x){if(y)foo();else bar()}else{baz()}}",
         "function f(){x?y?foo():bar():baz()}");

    fold("if(e1){while(e2){if(e3){foo()}}}else{bar()}",
         "if(e1)while(e2)e3&&foo();else bar()");

    fold("if(e1){with(e2){if(e3){foo()}}}else{bar()}",
         "if(e1)with(e2)e3&&foo();else bar()");

    fold("if(a||b){if(c||d){var x;}}", "if(a||b)if(c||d)var x");
    fold("if(x){ if(y){var x;}else{var z;} }",
         "if(x)if(y)var x;else var z");

    // NOTE - technically we can remove the blocks since both the parent
    // and child have elses. But we don't since it causes ambiguities in
    // some cases where not all descendent ifs having elses
    fold("if(x){ if(y){var x;}else{var z;} }else{var w}",
         "if(x)if(y)var x;else var z;else var w");
    fold("if (x) {var x;}else { if (y) { var y;} }",
         "if(x)var x;else if(y)var y");

    // Here's some of the ambiguous cases
    fold("if(a){if(b){f1();f2();}else if(c){f3();}}else {if(d){f4();}}",
         "if(a)if(b){f1();f2()}else c&&f3();else d&&f4()");

    fold("function f(){foo()}", "function f(){foo()}");
    fold("switch(x){case y: foo()}", "switch(x){case y:foo()}");
    fold("try{foo()}catch(ex){bar()}finally{baz()}",
         "try{foo()}catch(ex){bar()}finally{baz()}");
  }

  /** Try to minimize returns */
  @Test
  public void testFoldReturns() {
    fold("function f(){if(x)return 1;else return 2}",
         "function f(){return x?1:2}");
    fold("function f(){if(x)return 1;return 2}",
         "function f(){return x?1:2}");
    fold("function f(){if(x)return;return 2}",
         "function f(){return x?void 0:2}");
    fold("function f(){if(x)return 1+x;else return 2-x}",
         "function f(){return x?1+x:2-x}");
    fold("function f(){if(x)return 1+x;return 2-x}",
         "function f(){return x?1+x:2-x}");
    fold("function f(){if(x)return y += 1;else return y += 2}",
         "function f(){return x?(y+=1):(y+=2)}");

    fold("function f(){if(x)return;else return 2-x}",
         "function f(){if(x);else return 2-x}");
    fold("function f(){if(x)return;return 2-x}",
         "function f(){return x?void 0:2-x}");
    fold("function f(){if(x)return x;else return}",
         "function f(){if(x)return x;{}}");
    fold("function f(){if(x)return x;return}",
         "function f(){if(x)return x}");

    foldSame("function f(){for(var x in y) { return x.y; } return k}");
  }

  @Test
  public void testCombineIfs1() {
    fold("function f() {if (x) return 1; if (y) return 1}",
         "function f() {if (x||y) return 1;}");
    fold("function f() {if (x) return 1; if (y) foo(); else return 1}",
         "function f() {if ((!x)&&y) foo(); else return 1;}");
  }

  @Test
  public void testCombineIfs2() {
    // combinable but not yet done
    foldSame("function f() {if (x) throw 1; if (y) throw 1}");
    // Can't combine, side-effect
    fold("function f(){ if (x) g(); if (y) g() }",
         "function f(){ x&&g(); y&&g() }");
    // Can't combine, side-effect
    fold("function f(){ if (x) y = 0; if (y) y = 0; }",
         "function f(){ x&&(y = 0); y&&(y = 0); }");
  }

  @Test
  public void testCombineIfs3() {
    foldSame("function f() {if (x) return 1; if (y) {g();f()}}");
  }

  /** Try to minimize assignments */
  @Test
  public void testFoldAssignments() {
    fold("function f(){if(x)y=3;else y=4;}", "function f(){y=x?3:4}");
    fold("function f(){if(x)y=1+a;else y=2+a;}", "function f(){y=x?1+a:2+a}");

    // and operation assignments
    fold("function f(){if(x)y+=1;else y+=2;}", "function f(){y+=x?1:2}");
    fold("function f(){if(x)y-=1;else y-=2;}", "function f(){y-=x?1:2}");
    fold("function f(){if(x)y%=1;else y%=2;}", "function f(){y%=x?1:2}");
    fold("function f(){if(x)y|=1;else y|=2;}", "function f(){y|=x?1:2}");

    // Don't fold if the 2 ops don't match.
    foldSame("function f(){x ? y-=1 : y+=2}");

    // Don't fold if the 2 LHS don't match.
    foldSame("function f(){x ? y-=1 : z-=1}");

    // Don't fold if there are potential effects.
    foldSame("function f(){x ? y().a=3 : y().a=4}");
  }

  @Test
  public void testRemoveDuplicateStatements() {
    enableNormalize();
    fold("if (a) { x = 1; x++ } else { x = 2; x++ }",
         "x=(a) ? 1 : 2; x++");
    fold("if (a) { x = 1; x++; y += 1; z = pi; }" +
         " else  { x = 2; x++; y += 1; z = pi; }",
         "x=(a) ? 1 : 2; x++; y += 1; z = pi;");
    fold("function z() {" +
         "if (a) { foo(); return !0 } else { goo(); return !0 }" +
         "}",
         "function z() {(a) ? foo() : goo(); return !0}");
    fold("function z() {if (a) { foo(); x = true; return true " +
         "} else { goo(); x = true; return true }}",
         "function z() {(a) ? foo() : goo(); x = true; return true}");

    fold("function z() {" +
         "  if (a) { bar(); foo(); return true }" +
         "    else { bar(); goo(); return true }" +
         "}",
         "function z() {" +
         "  if (a) { bar(); foo(); }" +
         "    else { bar(); goo(); }" +
         "  return true;" +
         "}");
  }

  @Test
  public void testDontRemoveDuplicateStatementsWithoutNormalization() {
    // In the following test case, we can't remove the duplicate "alert(x);" lines since each "x"
    // refers to a different variable.
    // We only try removing duplicate statements if the AST is normalized and names are unique.
    testSame("if (Math.random() < 0.5) { const x = 3; alert(x); } else { const x = 5; alert(x); }");
  }

  @Test
  public void testNotCond() {
    fold("function f(){if(!x)foo()}", "function f(){x||foo()}");
    fold("function f(){if(!x)b=1}", "function f(){x||(b=1)}");
    fold("if(!x)z=1;else if(y)z=2", "if(x){y&&(z=2);}else{z=1;}");
    fold("if(x)y&&(z=2);else z=1;", "x ? y&&(z=2) : z=1");
    fold("function f(){if(!(x=1))a.b=1}",
         "function f(){(x=1)||(a.b=1)}");
  }

  @Test
  public void testAndParenthesesCount() {
    fold("function f(){if(x||y)a.foo()}", "function f(){(x||y)&&a.foo()}");
    fold("function f(){if(x.a)x.a=0}",
         "function f(){x.a&&(x.a=0)}");
    foldSame("function f(){if(x()||y()){x()||y()}}");
  }

  @Test
  public void testFoldLogicalOpStringCompare() {
    // side-effects
    // There is two way to parse two &&'s and both are correct.
    fold("if (foo() && false) z()", "(foo(), 0) && z()");
  }

  @Test
  public void testFoldNot() {
    fold("while(!(x==y)){a=b;}" , "while(x!=y){a=b;}");
    fold("while(!(x!=y)){a=b;}" , "while(x==y){a=b;}");
    fold("while(!(x===y)){a=b;}", "while(x!==y){a=b;}");
    fold("while(!(x!==y)){a=b;}", "while(x===y){a=b;}");
    // Because !(x<NaN) != x>=NaN don't fold < and > cases.
    foldSame("while(!(x>y)){a=b;}");
    foldSame("while(!(x>=y)){a=b;}");
    foldSame("while(!(x<y)){a=b;}");
    foldSame("while(!(x<=y)){a=b;}");
    foldSame("while(!(x<=NaN)){a=b;}");

    // NOT forces a boolean context
    fold("x = !(y() && true)", "x = !y()");
    // This will be further optimized by PeepholeFoldConstants.
    fold("x = !true", "x = !1");
  }

  @Test
  public void testMinimizeExprCondition() {
    fold("(x ? true : false) && y()", "x&&y()");
    fold("(x ? false : true) && y()", "(!x)&&y()");
    fold("(x ? true : y) && y()", "(x || y)&&y()");
    fold("(x ? y : false) && y()", "(x && y)&&y()");
    fold("(x && true) && y()", "x && y()");
    fold("(x && false) && y()", "0&&y()");
    fold("(x || true) && y()", "1&&y()");
    fold("(x || false) && y()", "x&&y()");
  }

  @Test
  public void testMinimizeWhileCondition() {
    // This test uses constant folding logic, so is only here for completeness.
    fold("while(!!true) foo()", "while(1) foo()");
    // These test tryMinimizeCondition
    fold("while(!!x) foo()", "while(x) foo()");
    fold("while(!(!x&&!y)) foo()", "while(x||y) foo()");
    fold("while(x||!!y) foo()", "while(x||y) foo()");
    fold("while(!(!!x&&y)) foo()", "while(!x||!y) foo()");
    fold("while(!(!x&&y)) foo()", "while(x||!y) foo()");
    fold("while(!(x||!y)) foo()", "while(!x&&y) foo()");
    fold("while(!(x||y)) foo()", "while(!x&&!y) foo()");
    fold("while(!(!x||y-z)) foo()", "while(x&&!(y-z)) foo()");
    fold("while(!(!(x/y)||z+w)) foo()", "while(x/y&&!(z+w)) foo()");
    foldSame("while(!(x+y||z)) foo()");
    foldSame("while(!(x&&y*z)) foo()");
    fold("while(!(!!x&&y)) foo()", "while(!x||!y) foo()");
    fold("while(x&&!0) foo()", "while(x) foo()");
    fold("while(x||!1) foo()", "while(x) foo()");
    fold("while(!((x,y)&&z)) foo()", "while((x,!y)||!z) foo()");
  }

  @Test
  public void testMinimizeDemorganRemoveLeadingNot() {
    fold("if(!(!a||!b)&&c) foo()", "((a&&b)&&c)&&foo()");
    fold("if(!(x&&y)) foo()", "x&&y||foo()");
    fold("if(!(x||y)) foo()", "(x||y)||foo()");
  }

  @Test
  public void testMinimizeDemorgan1() {
    fold("if(!a&&!b)foo()", "(a||b)||foo()");
  }

  @Test
  public void testMinimizeDemorgan2() {
    // Make sure trees with cloned functions are marked as changed
    fold("(!(a&&!((function(){})())))||foo()", "!a||(function(){})()||foo()");
  }

  @Test
  public void testMinimizeDemorgan2b() {
    // Make sure unchanged trees with functions are not marked as changed
    foldSame("!a||(function(){})()||foo()");
  }

  @Test
  public void testMinimizeDemorgan3() {
    fold("if((!a||!b)&&(c||d)) foo()", "(a&&b||!c&&!d)||foo()");
  }

  @Test
  public void testMinimizeDemorgan5() {
    fold("if((!a||!b)&&c) foo()", "(a&&b||!c)||foo()");
  }

  @Test
  public void testMinimizeDemorgan11() {
    fold("if (x && (y===2 || !f()) && (y===3 || !h())) foo()",
         "(!x || y!==2 && f() || y!==3 && h()) || foo()");
  }

  @Test
  public void testMinimizeDemorgan20a() {
    fold("if (0===c && (2===a || 1===a)) f(); else g()",
         "if (0!==c || 2!==a && 1!==a) g(); else f()");
  }

  @Test
  public void testMinimizeDemorgan20b() {
    fold("if (0!==c || 2!==a && 1!==a) g(); else f()",
         "(0!==c || 2!==a && 1!==a) ? g() : f()");
  }

  @Test
  public void testPreserveIf() {
    foldSame("if(!a&&!b)for(;f(););");
  }

  @Test
  public void testNoSwapWithDanglingElse() {
    foldSame("if(!x) {for(;;)foo(); for(;;)bar()} else if(y) for(;;) f()");
    foldSame("if(!a&&!b) {for(;;)foo(); for(;;)bar()} else if(y) for(;;) f()");
  }

  @Test
  public void testMinimizeHook() {
    fold("x ? x : y", "x || y");
    // We assume GETPROPs don't have side effects.
    fold("x.y ? x.y : x.z", "x.y || x.z");
    // This can be folded if x() does not have side effects.
    foldSame("x() ? x() : y()");

    fold("!x ? foo() : bar()",
         "x ? bar() : foo()");
    fold("while(!(x ? y : z)) foo();",
         "while(x ? !y : !z) foo();");
    fold("(x ? !y : !z) ? foo() : bar()",
         "(x ? y : z) ? bar() : foo()");
  }

  @Test
  public void testMinimizeComma() {
    fold("while(!(inc(), test())) foo();",
         "while(inc(), !test()) foo();");
    fold("(inc(), !test()) ? foo() : bar()",
         "(inc(), test()) ? bar() : foo()");
  }

  @Test
  public void testMinimizeExprResult() {
    fold("!x||!y", "x&&y");
    fold("if(!(x&&!y)) foo()", "(!x||y)&&foo()");
    fold("if(!x||y) foo()", "(!x||y)&&foo()");
    fold("(!x||y)&&foo()", "x&&!y||!foo()");
  }

  @Test
  public void testMinimizeDemorgan21() {
    fold("if (0===c && (2===a || 1===a)) f()",
         "(0!==c || 2!==a && 1!==a) || f()");
  }

  @Test
  public void testMinimizeAndOr1() {
    fold("if ((!a || !b) && (d || e)) f()", "(a&&b || !d&&!e) || f()");
  }

  @Test
  public void testMinimizeForCondition() {
    // This test uses constant folding logic, so is only here for completeness.
    // These could be simplified to "for(;;) ..."
    fold("for(;!!true;) foo()", "for(;1;) foo()");
    // Verify function deletion tracking.
    fold("if(!!true||function(){}) {}", "if(1) {}");
    // Don't bother with FOR inits as there are normalized out.
    fold("for(!!true;;) foo()", "for(!0;;) foo()");

    // These test tryMinimizeCondition
    fold("for(;!!x;) foo()", "for(;x;) foo()");

    foldSame("for(a in b) foo()");
    foldSame("for(a in {}) foo()");
    foldSame("for(a in []) foo()");
    fold("for(a in !!true) foo()", "for(a in !0) foo()");

    foldSame("for(a of b) foo()");
    foldSame("for(a of {}) foo()");
    foldSame("for(a of []) foo()");
    fold("for(a of !!true) foo()", "for(a of !0) foo()");
  }

  @Test
  public void testMinimizeCondition_example1() {
    // Based on a real failing code sample.
    fold("if(!!(f() > 20)) {foo();foo()}", "if(f() > 20){foo();foo()}");
  }

  @Test
  public void testFoldLoopBreakLate() {
    late = true;
    fold("for(;;) if (a) break", "for(;!a;);");
    foldSame("for(;;) if (a) { f(); break }");
    fold("for(;;) if (a) break; else f()", "for(;!a;) { { f(); } }");
    fold("for(;a;) if (b) break", "for(;a && !b;);");
    fold("for(;a;) { if (b) break; if (c) break; }",
         "for(;(a && !b);) if (c) break;");
    fold("for(;(a && !b);) if (c) break;", "for(;(a && !b) && !c;);");
    fold("for(;;) { if (foo) { break; var x; } } x;",
        "var x; for(;!foo;) {} x;");

    // 'while' is normalized to 'for'
    enableNormalize();
    fold("while(true) if (a) break", "for(;1&&!a;);");
    disableNormalize();
  }

  @Test
  public void testFoldLoopBreakEarly() {
    late = false;
    foldSame("for(;;) if (a) break");
    foldSame("for(;;) if (a) { f(); break }");
    foldSame("for(;;) if (a) break; else f()");
    foldSame("for(;a;) if (b) break");
    foldSame("for(;a;) { if (b) break; if (c) break; }");

    foldSame("while(1) if (a) break");
    enableNormalize();
    foldSame("while(1) if (a) break");
    disableNormalize();
  }

  @Test
  public void testFoldConditionalVarDeclaration() {
    fold("if(x) var y=1;else y=2", "var y=x?1:2");
    fold("if(x) y=1;else var y=2", "var y=x?1:2");

    foldSame("if(x) var y = 1; z = 2");
    foldSame("if(x||y) y = 1; var z = 2");

    foldSame("if(x) { var y = 1; print(y)} else y = 2 ");
    foldSame("if(x) var y = 1; else {y = 2; print(y)}");
  }

  @Test
  public void testFoldIfWithLowerOperatorsInside() {
    fold("if (x + (y=5)) z && (w,z);",
         "x + (y=5) && (z && (w,z))");
    fold("if (!(x+(y=5))) z && (w,z);",
         "x + (y=5) || z && (w,z)");
    fold("if (x + (y=5)) if (z && (w,z)) for(;;) foo();",
         "if (x + (y=5) && (z && (w,z))) for(;;) foo();");
  }

  @Test
  public void testSubsituteReturn() {

    fold("function f() { while(x) { return }}",
         "function f() { while(x) { break }}");

    foldSame("function f() { while(x) { return 5 } }");

    foldSame("function f() { a: { return 5 } }");

    fold("function f() { while(x) { return 5}  return 5}",
         "function f() { while(x) { break }    return 5}");

    fold("function f() { while(x) { return x}  return x}",
         "function f() { while(x) { break }    return x}");

    fold("function f() { while(x) { if (y) { return }}}",
         "function f() { while(x) { if (y) { break  }}}");

    fold("function f() { while(x) { if (y) { return }} return}",
         "function f() { while(x) { if (y) { break  }}}");

    fold("function f() { while(x) { if (y) { return 5 }} return 5}",
         "function f() { while(x) { if (y) { break    }} return 5}");

    // It doesn't matter if x is changed between them. We are still returning
    // x at whatever x value current holds. The whole x = 1 is skipped.
    fold("function f() { while(x) { if (y) { return x } x = 1} return x}",
         "function f() { while(x) { if (y) { break    } x = 1} return x}");

    // RemoveUnreachableCode would take care of the useless breaks.
    fold("function f() { while(x) { if (y) { return x } return x} return x}",
         "function f() { while(x) { if (y) {} break }return x}");

    // A break here only breaks out of the inner loop.
    foldSame("function f() { while(x) { while (y) { return } } }");

    foldSame("function f() { while(1) { return 7}  return 5}");


    foldSame("function f() {" +
             "  try { while(x) {return f()}} catch (e) { } return f()}");

    foldSame("function f() {" +
             "  try { while(x) {return f()}} finally {alert(1)} return f()}");


    // Both returns has the same handler
    fold("function f() {" +
         "  try { while(x) { return f() } return f() } catch (e) { } }",
         "function f() {" +
         "  try { while(x) { break } return f() } catch (e) { } }");

    // We can't fold this because it'll change the order of when foo is called.
    foldSame("function f() {" +
             "  try { while(x) { return foo() } } finally { alert(1) } "  +
             "  return foo()}");

    // This is fine, we have no side effect in the return value.
    fold("function f() {" +
         "  try { while(x) { return 1 } } finally { alert(1) } return 1}",
         "function f() {" +
         "  try { while(x) { break    } } finally { alert(1) } return 1}"
         );

    foldSame("function f() { try{ return a } finally { a = 2 } return a; }");

    fold(
      "function f() { switch(a){ case 1: return a; default: g();} return a;}",
      "function f() { switch(a){ case 1: break; default: g();} return a; }");
  }

  @Test
  public void testSubsituteBreakForThrow() {

    foldSame("function f() { while(x) { throw Error }}");

    fold("function f() { while(x) { throw Error } throw Error }",
         "function f() { while(x) { break } throw Error}");
    foldSame("function f() { while(x) { throw Error(1) } throw Error(2)}");
    foldSame("function f() { while(x) { throw Error(1) } return Error(2)}");

    foldSame("function f() { while(x) { throw 5 } }");

    foldSame("function f() { a: { throw 5 } }");

    fold("function f() { while(x) { throw 5}  throw 5}",
         "function f() { while(x) { break }   throw 5}");

    fold("function f() { while(x) { throw x}  throw x}",
         "function f() { while(x) { break }   throw x}");

    foldSame("function f() { while(x) { if (y) { throw Error }}}");

    fold("function f() { while(x) { if (y) { throw Error }} throw Error}",
         "function f() { while(x) { if (y) { break }} throw Error}");

    fold("function f() { while(x) { if (y) { throw 5 }} throw 5}",
         "function f() { while(x) { if (y) { break    }} throw 5}");

    // It doesn't matter if x is changed between them. We are still throwing
    // x at whatever x value current holds. The whole x = 1 is skipped.
    fold("function f() { while(x) { if (y) { throw x } x = 1} throw x}",
         "function f() { while(x) { if (y) { break    } x = 1} throw x}");

    // RemoveUnreachableCode would take care of the useless breaks.
    fold("function f() { while(x) { if (y) { throw x } throw x} throw x}",
         "function f() { while(x) { if (y) {} break }throw x}");

    // A break here only breaks out of the inner loop.
    foldSame("function f() { while(x) { while (y) { throw Error } } }");

    foldSame("function f() { while(1) { throw 7}  throw 5}");


    foldSame("function f() {" +
             "  try { while(x) {throw f()}} catch (e) { } throw f()}");

    foldSame("function f() {" +
             "  try { while(x) {throw f()}} finally {alert(1)} throw f()}");


    // Both throws has the same handler
    fold("function f() {" +
         "  try { while(x) { throw f() } throw f() } catch (e) { } }",
         "function f() {" +
         "  try { while(x) { break } throw f() } catch (e) { } }");

    // We can't fold this because it'll change the order of when foo is called.
    foldSame("function f() {" +
             "  try { while(x) { throw foo() } } finally { alert(1) } "  +
             "  throw foo()}");

    // This is fine, we have no side effect in the throw value.
    fold("function f() {" +
         "  try { while(x) { throw 1 } } finally { alert(1) } throw 1}",
         "function f() {" +
         "  try { while(x) { break    } } finally { alert(1) } throw 1}"
         );

    foldSame("function f() { try{ throw a } finally { a = 2 } throw a; }");

    fold(
      "function f() { switch(a){ case 1: throw a; default: g();} throw a;}",
      "function f() { switch(a){ case 1: break; default: g();} throw a; }");
  }

  @Test
  public void testRemoveDuplicateReturn() {
    fold("function f() { return; }",
         "function f(){}");
    foldSame("function f() { return a; }");
    fold("function f() { if (x) { return a } return a; }",
         "function f() { if (x) {} return a; }");
    foldSame(
      "function f() { try { if (x) { return a } } catch(e) {} return a; }");
    foldSame(
      "function f() { try { if (x) {} } catch(e) {} return 1; }");

    // finally clauses may have side effects
    foldSame(
      "function f() { try { if (x) { return a } } finally { a++ } return a; }");
    // but they don't matter if the result doesn't have side effects and can't
    // be affect by side-effects.
    fold("function f() { try { if (x) { return 1 } } finally {} return 1; }",
         "function f() { try { if (x) {} } finally {} return 1; }");

    fold("function f() { switch(a){ case 1: return a; } return a; }",
         "function f() { switch(a){ case 1: } return a; }");

    fold("function f() { switch(a){ " +
         "  case 1: return a; case 2: return a; } return a; }",
         "function f() { switch(a){ " +
         "  case 1: break; case 2: } return a; }");
  }

  @Test
  public void testRemoveDuplicateThrow() {
    foldSame("function f() { throw a; }");
    fold("function f() { if (x) { throw a } throw a; }",
         "function f() { if (x) {} throw a; }");
    foldSame(
      "function f() { try { if (x) {throw a} } catch(e) {} throw a; }");
    foldSame(
      "function f() { try { if (x) {throw 1} } catch(e) {f()} throw 1; }");
    foldSame(
      "function f() { try { if (x) {throw 1} } catch(e) {f()} throw 1; }");
    foldSame(
      "function f() { try { if (x) {throw 1} } catch(e) {throw 1}}");
    fold(
      "function f() { try { if (x) {throw 1} } catch(e) {throw 1} throw 1; }",
      "function f() { try { if (x) {throw 1} } catch(e) {} throw 1; }");

    // finally clauses may have side effects
    foldSame(
      "function f() { try { if (x) { throw a } } finally { a++ } throw a; }");
    // but they don't matter if the result doesn't have side effects and can't
    // be affect by side-effects.
    fold("function f() { try { if (x) { throw 1 } } finally {} throw 1; }",
         "function f() { try { if (x) {} } finally {} throw 1; }");

    fold("function f() { switch(a){ case 1: throw a; } throw a; }",
         "function f() { switch(a){ case 1: } throw a; }");

    fold("function f() { switch(a){ " +
             "case 1: throw a; case 2: throw a; } throw a; }",
         "function f() { switch(a){ case 1: break; case 2: } throw a; }");
  }

  @Test
  public void testNestedIfCombine() {
    fold("if(x)if(y){while(1){}}", "if(x&&y){while(1){}}");
    fold("if(x||z)if(y){while(1){}}", "if((x||z)&&y){while(1){}}");
    fold("if(x)if(y||z){while(1){}}", "if((x)&&(y||z)){while(1){}}");
    foldSame("if(x||z)if(y||z){while(1){}}");
    fold("if(x)if(y){if(z){while(1){}}}", "if(x&&(y&&z)){while(1){}}");
  }

  @Test
  public void testIssue291() {
    fold("if (true) { f.onchange(); }", "if (1) f.onchange();");
    foldSame("if (f) { f.onchange(); }");
    foldSame("if (f) { f.bar(); } else { f.onchange(); }");
    fold("if (f) { f.bonchange(); }", "f && f.bonchange();");
    foldSame("if (f) { f['x'](); }");
  }

  @Test
  public void testObjectLiteral() {
    test("({})", "1");
    test("({a:1})", "1");
    testSame("({a:foo()})");
    testSame("({'a':foo()})");
  }

  @Test
  public void testArrayLiteral() {
    test("([])", "1");
    test("([1])", "1");
    test("([a])", "1");
    testSame("([foo()])");
  }

  @Test
  public void testRemoveElseCause() {
    test("function f() {" +
         " if(x) return 1;" +
         " else if(x) return 2;" +
         " else if(x) return 3 }",
         "function f() {" +
         " if(x) return 1;" +
         "{ if(x) return 2;" +
         "{ if(x) return 3 } } }");
  }

  @Test
  public void testRemoveElseCause1() {
    test("function f() { if (x) throw 1; else f() }",
         "function f() { if (x) throw 1; { f() } }");
  }

  @Test
  public void testRemoveElseCause2() {
    test("function f() { if (x) return 1; else f() }",
         "function f() { if (x) return 1; { f() } }");
    test("function f() { if (x) return; else f() }",
         "function f() { if (x) {} else { f() } }");
    // This case is handled by minimize exit points.
    testSame("function f() { if (x) return; f() }");
  }

  @Test
  public void testRemoveElseCause3() {
    testSame("function f() { a:{if (x) break a; else f() } }");
    testSame("function f() { if (x) { a:{ break a } } else f() }");
    testSame("function f() { if (x) a:{ break a } else f() }");
  }

  @Test
  public void testRemoveElseCause4() {
    testSame("function f() { if (x) { if (y) { return 1; } } else f() }");
  }

  @Test
  public void testIssue925() {
    test(
        "if (x[--y] === 1) {\n" +
        "    x[y] = 0;\n" +
        "} else {\n" +
        "    x[y] = 1;\n" +
        "}",
        "(x[--y] === 1) ? x[y] = 0 : x[y] = 1;");

    test(
        "if (x[--y]) {\n" +
        "    a = 0;\n" +
        "} else {\n" +
        "    a = 1;\n" +
        "}",
        "a = (x[--y]) ? 0 : 1;");

    test("if (x++) { x += 2 } else { x += 3 }",
         "x++ ? x += 2 : x += 3");

    test("if (x++) { x = x + 2 } else { x = x + 3 }",
        "x = x++ ? x + 2 : x + 3");
  }

  @Test
  public void testCoercionSubstitution_disabled() {
    enableTypeCheck();
    testSame("var x = {}; if (x != null) throw 'a';");
    testSame("var x = {}; var y = x != null;");

    testSame("var x = 1; if (x != 0) throw 'a';");
    testSame("var x = 1; var y = x != 0;");
  }

  @Test
  public void testCoercionSubstitution_booleanResult0() {
    enableTypeCheck();
    testSame("var x = {}; var y = x != null;");
  }

  @Test
  public void testCoercionSubstitution_booleanResult1() {
    enableTypeCheck();
    testSame("var x = {}; var y = x == null;");
    testSame("var x = {}; var y = x !== null;");
    testSame("var x = undefined; var y = x !== null;");
    testSame("var x = {}; var y = x === null;");
    testSame("var x = undefined; var y = x === null;");

    testSame("var x = 1; var y = x != 0;");
    testSame("var x = 1; var y = x == 0;");
    testSame("var x = 1; var y = x !== 0;");
    testSame("var x = 1; var y = x === 0;");
  }

  @Test
  public void testCoercionSubstitution_if() {
    enableTypeCheck();
    test("var x = {};\nif (x != null) throw 'a';\n", "var x={}; if (x!=null) throw 'a'");
    testSame("var x = {};\nif (x == null) throw 'a';\n");
    testSame("var x = {};\nif (x !== null) throw 'a';\n");
    testSame("var x = {};\nif (x === null) throw 'a';\n");
    testSame("var x = {};\nif (null != x) throw 'a';\n");
    testSame("var x = {};\nif (null == x) throw 'a';\n");
    testSame("var x = {};\nif (null !== x) throw 'a';\n");
    testSame("var x = {};\nif (null === x) throw 'a';\n");

    testSame("var x = 1;\nif (x != 0) throw 'a';\n");
    testSame("var x = 1;\nif (x != 0) throw 'a';\n");
    testSame("var x = 1;\nif (x == 0) throw 'a';\n");
    testSame("var x = 1;\nif (x !== 0) throw 'a';\n");
    testSame("var x = 1;\nif (x === 0) throw 'a';\n");
    testSame("var x = 1;\nif (0 != x) throw 'a';\n");
    testSame("var x = 1;\nif (0 == x) throw 'a';\n");
    testSame("var x = 1;\nif (0 !== x) throw 'a';\n");
    testSame("var x = 1;\nif (0 === x) throw 'a';\n");
    testSame("var x = NaN;\nif (0 === x) throw 'a';\n");
    testSame("var x = NaN;\nif (x === 0) throw 'a';\n");
  }

  @Test
  public void testCoercionSubstitution_expression() {
    enableTypeCheck();
    testSame("var x = {}; x != null && alert('b');");
    testSame("var x = 1; x != 0 && alert('b');");
  }

  @Test
  public void testCoercionSubstitution_hook() {
    enableTypeCheck();
    testSame(
        lines(
            "var x = {};",
            "var y = x != null ? 1 : 2;"));
    testSame(
        lines(
            "var x = 1;",
            "var y = x != 0 ? 1 : 2;"));
  }

  @Test
  public void testCoercionSubstitution_not() {
    enableTypeCheck();
    test(
        "var x = {};\nvar y = !(x != null) ? 1 : 2;\n",
        "var x = {};\nvar y = (x == null) ? 1 : 2;\n");
    test("var x = 1;\nvar y = !(x != 0) ? 1 : 2;\n", "var x = 1;\nvar y = x == 0 ? 1 : 2;\n");
  }

  @Test
  public void testCoercionSubstitution_while() {
    enableTypeCheck();
    testSame("var x = {}; while (x != null) throw 'a';");
    testSame("var x = 1; while (x != 0) throw 'a';");
  }

  @Test
  public void testCoercionSubstitution_unknownType() {
    enableTypeCheck();
    testSame("var x = /** @type {?} */ ({});\nif (x != null) throw 'a';\n");
    testSame("var x = /** @type {?} */ (1);\nif (x != 0) throw 'a';\n");
  }

  @Test
  public void testCoercionSubstitution_allType() {
    enableTypeCheck();
    testSame("var x = /** @type {*} */ ({});\nif (x != null) throw 'a';\n");
    testSame("var x = /** @type {*} */ (1);\nif (x != 0) throw 'a';\n");
  }

  @Test
  public void testCoercionSubstitution_primitivesVsNull() {
    enableTypeCheck();
    testSame("var x = 0;\nif (x != null) throw 'a';\n");
    testSame("var x = '';\nif (x != null) throw 'a';\n");
    testSame("var x = false;\nif (x != null) throw 'a';\n");
  }

  @Test
  public void testCoercionSubstitution_nonNumberVsZero() {
    enableTypeCheck();
    testSame("var x = {};\nif (x != 0) throw 'a';\n");
    testSame("var x = '';\nif (x != 0) throw 'a';\n");
    testSame("var x = false;\nif (x != 0) throw 'a';\n");
  }

  @Test
  public void testCoercionSubstitution_boxedNumberVsZero() {
    enableTypeCheck();
    testSame("var x = new Number(0);\nif (x != 0) throw 'a';\n");
  }

  @Test
  public void testCoercionSubstitution_boxedPrimitives() {
    enableTypeCheck();
    testSame("var x = new Number(); if (x != null) throw 'a';");
    testSame("var x = new String(); if (x != null) throw 'a';");
    testSame("var x = new Boolean();\nif (x != null) throw 'a';");
  }

  @Test
  public void testMinimizeIfWithNewTargetCondition() {
    // Related to https://github.com/google/closure-compiler/issues/3097
    test(
        lines(
            "function x() {",
            "  if (new.target) {",
            "    return 1;",
            "  } else {",
            "    return 2;",
            "  }",
            "}"),
        lines("function x() {", "  return new.target ? 1 : 2;", "}"));
  }
}
