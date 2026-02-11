/*
 * Copyright 2009 The Closure Compiler Authors.
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

import static org.junit.Assert.assertThrows;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @author johnlenz@google.com (John Lenz)
 */
@RunWith(JUnit4.class)
public final class MinimizeExitPointsTest extends CompilerTestCase {
  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    disableScriptFeatureValidation();
    enableNormalize();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new PeepholeOptimizationsPass(compiler, getName(), new MinimizeExitPoints());
  }

  void foldSame(String js) {
    testSame(js);
  }

  void fold(String js, String expected) {
    test(js, expected);
  }

  @Test
  public void testBreakOptimization() {
    fold("f:{if(true){a();break f;}else;b();}", "f:{if(true){a()}else{b()}}");
    fold("f:{if(false){a();break f;}else;b();break f;}", "f:{if(false){a()}else{b()}}");
    fold("f:{if(a()){b();break f;}else;c();}", "f:{if(a()){b();}else{c();}}");
    fold("f:{if(a()){b()}else{c();break f;}}", "f:{if(a()){b()}else{c();}}");
    fold("f:{if(a()){b();break f;}else;}", "f:{if(a()){b();}else;}");
    fold("f:{if(a()){break f;}else;}", "f:{if(a()){}else;}");

    fold("f:for(;a();)break f;", "f:for(;a();)break f");
    foldSame("f:for(x in a())break f");

    foldSame("f:{for(;a();)break;}");
    foldSame("f:{for(x in a())break}");

    fold("f:try{break f;}catch(e){break f;}", "f: { try{}catch(e){} }");
    fold(
        "f:try{if(a()){break f;}else{break f;} break f;}catch(e){}",
        "f:{ try{if(a()){}else{}}catch(e){} }");

    fold("f:g:break f", "f: g: {}");
    fold("f:g:{if(a()){break f;}else{break f;} break f;}", "f:g:{if(a()){}else{}}");
    fold("function f() { a: break a; }", "function f() { a: {} }");
    fold("function f() { a: { break a; } }", "function f() { a: {} }");
  }

  @Test
  public void testFunctionReturnOptimization1() {
    fold("function f(){return}", "function f(){}");
  }

  @Test
  public void testFunctionReturnOptimization2() {
    fold("function f(){if(a()){b();if(c())return;}}", "function f(){if(a()){b();if(c());}}");
    fold("function f(){if(x)return; x=3; return; }", "function f(){if(x); else x=3}");
    fold("function f(){if(true){a();return;}else;b();}", "function f(){if(true){a();}else{b();}}");
    fold(
        "function f(){if(false){a();return;}else;b();return;}",
        "function f(){if(false){a();}else{b();}}");
    fold("function f(){if(a()){b();return;}else;c();}", "function f(){if(a()){b();}else{c();}}");
    fold("function f(){if(a()){b()}else{c();return;}}", "function f(){if(a()){b()}else{c();}}");
    fold("function f(){if(a()){b();return;}else;}", "function f(){if(a()){b();}else;}");
    fold("function f(){if(a()){return;}else{return;} return;}", "function f(){if(a()){}else{}}");
    fold(
        "function f(){if(a()){return;}else{return;} b();}",
        "function f(){if(a()){}else{return;b()}}");
    fold(
        "function f(){ if (x) return; if (y) return; if (z) return; w(); }",
        """
        function f() {
          if (x) {} else { if (y) {} else { if (z) {} else w(); }}
        }
        """);

    fold("function f(){for(;a();)return;}", "function f(){for(;a();)return}");
    foldSame("function f(){for(x in a())return}");

    fold("function f(){for(;a();)break;}", "function f(){for(;a();)break}");
    foldSame("function f(){for(x in a())break}");

    fold(
        "function f(){try{return;}catch(e){throw 9;}finally{return}}",
        "function f(){try{}catch(e){throw 9;}finally{return}}");
    foldSame("function f(){try{throw 9;}finally{return;}}");

    fold("function f(){try{return;}catch(e){return;}}", "function f(){try{}catch(e){}}");
    fold(
        "function f(){try{if(a()){return;}else{return;} return;}catch(e){}}",
        "function f(){try{if(a()){}else{}}catch(e){}}");

    fold("function f(){g:return}", "function f(){g: {}}");
    fold(
        "function f(){g:if(a()){return;}else{return;} return;}",
        "function f(){g:{if(a()){}else{}}}");
    fold(
        """
        function f() {
          try {
            g: if (a()) {
              throw 9;
            }
            return;
          } finally {
            return;
          }
        }
        """,
        """
        function f() {
          try {
            g: {
              if (a()) {
                throw 9;
              }
            }
          } finally {
            return;
          }
        }
        """);
  }

  @Test
  public void testFunctionReturnScoped() {
    testSame(
        """
        function f(a) {
          if (a) {
            const aInner = Math.random();

            if (aInner < 0.5) {
                return aInner;
            }
          }

          return a;
        }
        """);
  }

  @Test
  public void testWhileContinueOptimization() {
    // Normalization should convert all WHILE loops to FOR loops, so just have a simple test
    // verifying that happens & we don't need explicit WHILE loop support.
    fold("while(true){if(x)continue; x=3; continue; }", "for(;true;)if(x);else x=3");
  }

  @Test
  public void testDoContinueOptimization() {
    fold("do{if(x)continue; x=3; continue; }while(true)", "do if(x); else x=3; while(true)");
    foldSame("do{a();continue;b()}while(true)");
    fold(
        "do{if(true){a();continue;}else;b();}while(true)",
        "do{if(true){a();}else{b();}}while(true)");
    fold(
        "do{if(false){a();continue;}else;b();continue;}while(true)",
        "do{if(false){a();}else{b();}}while(true)");
    fold("do{if(a()){b();continue;}else;c();}while(true)", "do{if(a()){b();}else{c()}}while(true)");
    fold(
        "do{if(a()){b();}else{c();continue;}}while(true)",
        "do{if(a()){b();}else{c();}}while(true)");
    fold("do{if(a()){b();continue;}else;}while(true)", "do{if(a()){b();}else;}while(true)");
    fold(
        "do{if(a()){continue;}else{continue;} continue;}while(true)",
        "do{if(a()){}else{}}while(true)");
    fold(
        "do{if(a()){continue;}else{continue;} b();}while(true)",
        "do{if(a()){}else{continue; b();}}while(true)");

    fold("do{for(;a();)continue;}while(true)", "do for(;a(););while(true)");
    fold("do{for(x in a())continue}while(true)", "do for(x in a());while(true)");

    fold("do{for(;a();)break;}while(true)", "do for(;a();)break;while(true)");
    foldSame("do for(x in a())break;while(true)");

    fold("do{try{continue;}catch(e){continue;}}while(true)", "do{try{}catch(e){}}while(true)");
    fold(
        "do{try{if(a()){continue;}else{continue;} continue;}catch(e){}}while(true)",
        "do{try{if(a()){}else{}}catch(e){}}while(true)");

    fold("do{g:continue}while(true)", "do{g: {}}while(true)");
    // This case could be improved.
    fold(
        "do{g:if(a()){continue;}else{continue;} continue;}while(true)",
        "do{g: { if(a());else; } }while(true)");

    fold("do { foo(); continue; } while(false)", "do { foo(); } while(false)");
    fold("do { foo(); break; } while(false)", "do { foo(); } while(false)");

    fold("do{break}while(!new Date());", "do{}while(!new Date());");

    foldSame("do { foo(); switch (x) { case 1: break; default: f()}; } while(false)");
  }

  @Test
  public void testForContinueOptimization() {
    fold("for(x in y){if(x)continue; x=3; continue; }", "for(x in y)if(x);else x=3");
    foldSame("for(x in y){a();continue;b()}");
    fold("for(x in y){if(true){a();continue;}else;b();}", "for(x in y){if(true)a();else b();}");
    fold(
        "for(x in y){if(false){a();continue;}else;b();continue;}",
        "for(x in y){if(false){a();}else{b()}}");
    fold("for(x in y){if(a()){b();continue;}else;c();}", "for(x in y){if(a()){b();}else{c();}}");
    fold("for(x in y){if(a()){b();}else{c();continue;}}", "for(x in y){if(a()){b();}else{c();}}");

    fold("for(x of y){if(x)continue; x=3; continue; }", "for(x of y)if(x);else x=3");
    foldSame("for(x of y){a();continue;b()}");
    fold("for(x of y){if(true){a();continue;}else;b();}", "for(x of y){if(true)a();else b();}");
    fold(
        "for(x of y){if(false){a();continue;}else;b();continue;}",
        "for(x of y){if(false){a();}else{b()}}");
    fold("for(x of y){if(a()){b();continue;}else;c();}", "for(x of y){if(a()){b();}else{c();}}");
    fold("for(x of y){if(a()){b();}else{c();continue;}}", "for(x of y){if(a()){b();}else{c();}}");

    fold(
        "async () => { for await (x of y){if(x)continue; x=3; continue; }}",
        "async () => { for await (x of y)if(x);else x=3 }");
    foldSame("async () => { for await (x of y){a();continue;b()}}");
    fold(
        "async () => { for await (x of y){if(true){a();continue;}else;b();}}",
        "async () => { for await (x of y){if(true)a();else b();}}");
    fold(
        "async () => { for await (x of y){if(false){a();continue;}else;b();continue;}}",
        "async () => { for await (x of y){if(false){a();}else{b()}}}");
    fold(
        "async () => { for await (x of y){if(a()){b();continue;}else;c();}}",
        "async () => { for await (x of y){if(a()){b();}else{c();}}}");
    fold(
        "async () => { for await (x of y){if(a()){b();}else{c();continue;}}}",
        "async () => { for await (x of y){if(a()){b();}else{c();}}}");

    fold("for(x=0;x<y;x++){if(a()){b();continue;}else;}", "x=0;for(;x<y;x++){if(a()){b();}else;}");
    fold(
        "for(x=0;x<y;x++){if(a()){continue;}else{continue;} continue;}",
        "x=0;for(;x<y;x++){if(a()){}else{}}");
    fold(
        "for(x=0;x<y;x++){if(a()){continue;}else{continue;} b();}",
        "x=0;for(;x<y;x++){if(a()){}else{continue; b();}}");

    fold("for(x=0;x<y;x++)for(;a();)continue;", "x=0;for(;x<y;x++)for(;a(););");
    fold("for(x=0;x<y;x++)for(x in a())continue", "x=0;for(;x<y;x++)for(x in a());");

    fold("for(x=0;x<y;x++)for(;a();)break;", "x=0;for(;x<y;x++)for(;a();)break");
    foldSame("x=0;for(;x<y;x++)for(x in a())break");

    fold(
        "for(x=0;x<y;x++){try{continue;}catch(e){continue;}}",
        "x=0;for(;x<y;x++){try{}catch(e){}}");
    fold(
        "for(x=0;x<y;x++){try{if(a()){continue;}else{continue;} continue;}catch(e){}}",
        "x=0;for(;x<y;x++){try{if(a()){}else{}}catch(e){}}");

    fold("for(x=0;x<y;x++){g:continue}", "x=0;for(;x<y;x++){ g: {} }");
    fold(
        "for(x=0;x<y;x++){g:if(a()){continue;}else{continue;} continue;}",
        "x=0;for(;x<y;x++){g:{if(a());else;}}");
  }

  @Test
  public void testCodeMotionDoesntBreakFunctionHoisting() {
    fold(
        "function f() { if (x) return; foo(); function foo() {} }",
        // Note: normalization moves all function declarations to the beginning of a block.
        // This test cases was originally added before requiring normalization.
        "function f() { function foo() {} if (x) {} else { foo(); } }");
  }

  @Test
  public void testDontRemoveBreakInTryFinally() {
    foldSame("function f() {b: {try{throw 9} finally {break b}} return 1;}");
  }

  /**
   * The 'break' prevents the 'b=false' from being evaluated. If we fold the do-while to
   * 'do;while(b=false)' the code will be incorrect.
   *
   * @see https://github.com/google/closure-compiler/issues/554
   */
  @Test
  public void testDontFoldBreakInDoWhileIfConditionHasSideEffects() {
    foldSame("var b=true;do{break}while(b=false);");
  }

  @Test
  public void testSwitchExitPoints1() {
    fold("switch (x) { case 1: f(); break; }", "switch (x) { case 1: f();        }");
    fold(
        "switch (x) { case 1: f(); break; case 2: g(); break; }",
        "switch (x) { case 1: f(); break; case 2: g();        }");
    fold(
        "switch (x) { case 1: if (x) { f(); break; } break; default: g(); break; }",
        "switch (x) { case 1: if (x) { f();        } break; default: g();        }");
  }

  @Test
  public void testThrowsExceptionIfAstNotNormalized() {
    disableNormalize();
    assertThrows(RuntimeException.class, () -> foldSame(""));
  }

  @Test
  public void testFoldBlockScopedVariables() {
    // When moving block-scoped variable declarations into inner blocks, first convert them to
    // "var" declarations to avoid breaking any references in inner functions.

    // For example, in the following test case, moving "let c = 3;" directly inside the else block
    // would break the function "g"'s reference to "c".
    fold(
        "function f() { function g() { return c; } if (x) {return;} let c = 3; }",
        "function f() { function g() { return c; } if (x){} else {var c = 3;} }");
    fold(
        "function f() { function g() { return c; } if (x) {return;} const c = 3; }",
        "function f() { function g() { return c; } if (x) {} else {var c = 3;} }");
    // Convert let and const even they're if not referenced by any functions.
    fold(
        "function f() { if (x) {return;} const c = 3; }",
        "function f() { if (x) {} else { var c = 3; } }");
    fold(
        "function f() { if (x) {return;} let a = 3; let b = () => a; }",
        "function f() { if (x) {} else { var a = 3; var b = () => { return a; }} }");
    fold(
        "function f() { if (x) { if (y) {return;} let c = 3; } }",
        "function f() { if (x) { if (y) {} else { var c = 3; } } }");
  }

  @Test
  public void testDontFoldBlockScopedVariablesInLoops() {
    // Don't move block-scoped declarations into inner blocks inside a loop, since converting
    // let/const declarations to vars in a loop can cause incorrect semantics.
    // See the following test case for an example.
    foldSame(
        """
        function f(param) {
          let arr = [];
          for (let x of param) {
            if (x < 0) continue;
            let y = x * 2;
            arr.push(() => {
              return y;
            }); // If y were a var, this would capture the wrong value.
          }
          return arr;
        }
        """);

    // Additional tests for different kinds of loops.
    foldSame("function f() { for (;true;) { if (true) {return;} let c = 3; } }");
    foldSame("function f() { do { if (true) {return;} let c = 3; } while (x); }");
    foldSame("function f() { for (;;) { if (true) { return; } let c = 3; } }");
    foldSame("function f(y) { for(x in []){ if(x) { return; } let c = 3; } }");
    foldSame("async function f(y) { for await (x in []){ if(x) { return; } let c = 3; } }");
  }
}
