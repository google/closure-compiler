/*
 * Copyright 2004 Google Inc.
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

import com.google.javascript.rhino.Node;

public class FoldConstantsTest extends CompilerTestCase {

  // TODO(user): Remove this when we no longer need to do string comparison.
  private FoldConstantsTest(boolean compareAsTree) {
    super("", compareAsTree);
  }

  public FoldConstantsTest() {
    super();
  }

  @Override
  public void setUp() {
    super.enableLineNumberCheck(true);
  }

  @Override
  public CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {
      public void process(Node externs, Node js) {
        NodeTraversal.traverse(compiler, js, new FoldConstants(compiler));
      }
    };
  }

  @Override
  protected int getNumRepetitions() {
    // Reduce this to 2 if we get better expression evaluators.
    return 2;
  }

  void foldSame(String js) {
    testSame(js);
  }

  void fold(String js, String expected) {
    test(js, expected);
  }

  void fold(String js, String expected, DiagnosticType warning) {
    test(js, expected, warning);
  }

  // TODO(user): This is same as fold() except it uses string comparison. Any
  // test that needs tell us where a folding is constructing an invalid AST.
  void assertResultString(String js, String expected) {
    FoldConstantsTest scTest = new FoldConstantsTest(false);
    scTest.test(js, expected);
  }

  public void testFoldBlock() {
    fold("{{foo()}}", "foo()");
    fold("{foo();{}}", "foo()");
    fold("{{foo()}{}}", "foo()");
    fold("{{foo()}{bar()}}", "foo();bar()");
    fold("{if(false)foo(); {bar()}}", "bar()");
    fold("{if(false)if(false)if(false)foo(); {bar()}}", "bar()");

    fold("{'hi'}", "");
    fold("{x==3}", "");
    fold("{ (function(){x++}) }", "");
    fold("function(){return;}", "function(){return;}");
    fold("function(){return 3;}", "function(){return 3}");
    fold("function(){if(x)return; x=3; return; }",
         "function(){if(x)return; x=3; return; }");
    fold("{x=3;;;y=2;;;}", "x=3;y=2");

    // Cases to test for empty block.
    fold("while(x()){x}", "while(x());");
    fold("while(x()){x()}", "while(x())x()");
    fold("for(x=0;x<100;x++){x}", "for(x=0;x<100;x++);");
    fold("for(x in y){x}", "for(x in y);");
  }

  /** Check that removing blocks with 1 child works */
  public void testFoldOneChildBlocks() {
    fold("function(){if(x)a();x=3}",
        "function(){x&&a();x=3}");
    fold("function(){if(x){a()}x=3}",
        "function(){x&&a();x=3}");
    fold("function(){if(x){return 3}}",
        "function(){if(x)return 3}");
    fold("function(){if(x){a()}}",
        "function(){x&&a()}");
    fold("function(){if(x){throw 1}}", "function(){if(x)throw 1;}");

    // Try it out with functions
    fold("function(){if(x){foo()}}", "function(){x&&foo()}");
    fold("function(){if(x){foo()}else{bar()}}",
         "function(){x?foo():bar()}");

    // Try it out with properties and methods
    fold("function(){if(x){a.b=1}}", "function(){if(x)a.b=1}");
    fold("function(){if(x){a.b*=1}}", "function(){if(x)a.b*=1}");
    fold("function(){if(x){a.b+=1}}", "function(){if(x)a.b+=1}");
    fold("function(){if(x){++a.b}}", "function(){x&&++a.b}");
    fold("function(){if(x){a.foo()}}", "function(){x&&a.foo()}");

    // Try it out with throw/catch/finally [which should not change]
    fold("function(){try{foo()}catch(e){bar(e)}finally{baz()}}",
         "function(){try{foo()}catch(e){bar(e)}finally{baz()}}");

    // Try it out with switch statements
    fold("function(){switch(x){case 1:break}}",
         "function(){switch(x){case 1:break}}");
    fold("function(){switch(x){default:{break}}}",
         "function(){switch(x){default:break}}");
    fold("function(){switch(x){default:{break}}}",
         "function(){switch(x){default:break}}");
    fold("function(){switch(x){default:x;case 1:return 2}}",
         "function(){switch(x){default:case 1:return 2}}");

    // Do while loops stay in a block if that's where they started
    fold("function(){if(e1){do foo();while(e2)}else foo2()}",
         "function(){if(e1){do foo();while(e2)}else foo2()}");
    // Test an obscure case with do and while
    fold("if(x){do{foo()}while(y)}else bar()",
         "if(x){do foo();while(y)}else bar()");


    // Play with nested IFs
    fold("function(){if(x){if(y)foo()}}",
         "function(){x&&y&&foo()}");
    fold("function(){if(x){if(y)foo();else bar()}}",
         "function(){if(x)y?foo():bar()}");
    fold("function(){if(x){if(y)foo()}else bar()}",
         "function(){if(x)y&&foo();else bar()}");
    fold("function(){if(x){if(y)foo();else bar()}else{baz()}}",
         "function(){if(x)y?foo():bar();else baz()}");

    fold("if(e1){while(e2){if(e3){foo()}}}else{bar()}",
         "if(e1)while(e2)e3&&foo();else bar()");

    fold("if(e1){with(e2){if(e3){foo()}}}else{bar()}",
         "if(e1)with(e2)e3&&foo();else bar()");

    fold("if(x){if(y){var x;}}", "if(x)if(y)var x");
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

    fold("function(){foo()}", "function(){foo()}");
    fold("switch(x){case y: foo()}", "switch(x){case y:foo()}");
    fold("try{foo()}catch(ex){bar()}finally{baz()}",
         "try{foo()}catch(ex){bar()}finally{baz()}");

    // ensure that block folding does not break hook ifs
    fold("if(x){if(true){foo();foo()}else{bar();bar()}}",
         "if(x){foo();foo()}");
    fold("if(x){if(false){foo();foo()}else{bar();bar()}}",
         "if(x){bar();bar()}");

    // Cases where the then clause has no side effects.
    fold("if(x()){}", "x()");
    fold("if(x()){} else {x()}", "x()||x()");
    fold("if(x){}", ""); // Even the condition has no side effect.
    fold("if(a()){A()} else if (b()) {} else {C()}",
         "if(a())A();else b()||C()");
    fold("if(a()){} else if (b()) {} else {C()}",
         "a()||b()||C()");
    fold("if(a()){A()} else if (b()) {} else if (c()) {} else{D()}",
         "if(a())A();else b()||c()||D()");
    fold("if(a()){} else if (b()) {} else if (c()) {} else{D()}",
         "a()||b()||c()||D()");
    fold("if(a()){A()} else if (b()) {} else if (c()) {} else{}",
         "if(a())A();else b()||c()");

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

  /** Try to remove spurious blocks with multiple children */
  public void testFoldBlocksWithManyChildren() {
    fold("function f() { if (false) {} }", "function f(){}");
    fold("function f() { { if (false) {} if (true) {} {} } }",
         "function f(){}");
    fold("{var x; var y; var z; function f() { { var a; { var b; } } } }",
         "var x;var y;var z;function f(){var a;var b}");
  }

  /** Try to minimize returns */
  public void testFoldReturns() {
    fold("function(){if(x)return 1;else return 2}",
         "function(){return x?1:2}");
    fold("function(){if(x)return 1+x;else return 2-x}",
         "function(){return x?1+x:2-x}");
    fold("function(){if(x)return y += 1;else return y += 2}",
         "function(){return x?(y+=1):(y+=2)}");

    // don't touch cases where either side doesn't return a value
    foldSame("function(){if(x)return;else return 2-x}");
    foldSame("function(){if(x)return x;else return}");

    // if-then-else duplicate statement removal handles this case:
    fold("function(){if(x)return;else return}",
         "function(){return}");
  }

  /** Try to minimize assignments */
  public void testFoldAssignments() {
    fold("function(){if(x)y=3;else y=4;}", "function(){y=x?3:4}");
    fold("function(){if(x)y=1+a;else y=2+a;}", "function(){y=x?1+a:2+a}");

    // and operation assignments
    fold("function(){if(x)y+=1;else y+=2;}", "function(){y+=x?1:2}");
    fold("function(){if(x)y-=1;else y-=2;}", "function(){y-=x?1:2}");
    fold("function(){if(x)y%=1;else y%=2;}", "function(){y%=x?1:2}");
    fold("function(){if(x)y|=1;else y|=2;}", "function(){y|=x?1:2}");

    // sanity check, don't fold if the 2 ops don't match
    foldSame("function(){if(x)y-=1;else y+=2}");

    // sanity check, don't fold if the 2 LHS don't match
    foldSame("function(){if(x)y-=1;else z-=1}");

    // sanity check, don't fold if there are potential effects
    foldSame("function(){if(x)y().a=3;else y().a=4}");
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

  public void testUndefinedComparison() {
    fold("if (0 == 0){ x = 1; } else { x = 2; }", "x=1");
    fold("if (undefined == undefined){ x = 1; } else { x = 2; }", "x=1");
    fold("if (undefined == null){ x = 1; } else { x = 2; }", "x=1");
    // fold("if (undefined == NaN){ x = 1; } else { x = 2; }", "x=2");
    fold("if (undefined == 0){ x = 1; } else { x = 2; }", "x=2");
    fold("if (undefined == 1){ x = 1; } else { x = 2; }", "x=2");
    fold("if (undefined == 'hi'){ x = 1; } else { x = 2; }", "x=2");
    fold("if (undefined == true){ x = 1; } else { x = 2; }", "x=2");
    fold("if (undefined == false){ x = 1; } else { x = 2; }", "x=2");
    fold("if (undefined === undefined){ x = 1; } else { x = 2; }", "x=1");
    fold("if (undefined === null){ x = 1; } else { x = 2; }", "x=2");
    fold("if (undefined === void 0){ x = 1; } else { x = 2; }", "x=1");
    // foldSame("if (undefined === void foo()){ x = 1; } else { x = 2; }");
    foldSame("x = (undefined == this) ? 1 : 2;");
    foldSame("x = (undefined == x) ? 1 : 2;");

    fold("if (undefined != undefined){ x = 1; } else { x = 2; }", "x=2");
    fold("if (undefined != null){ x = 1; } else { x = 2; }", "x=2");
    // fold("if (undefined != NaN){ x = 1; } else { x = 2; }", "x=1");
    fold("if (undefined != 0){ x = 1; } else { x = 2; }", "x=1");
    fold("if (undefined != 1){ x = 1; } else { x = 2; }", "x=1");
    fold("if (undefined != 'hi'){ x = 1; } else { x = 2; }", "x=1");
    fold("if (undefined != true){ x = 1; } else { x = 2; }", "x=1");
    fold("if (undefined != false){ x = 1; } else { x = 2; }", "x=1");
    fold("if (undefined !== undefined){ x = 1; } else { x = 2; }", "x=2");
    fold("if (undefined !== null){ x = 1; } else { x = 2; }", "x=1");
    foldSame("x = (undefined != this) ? 1 : 2;");
    foldSame("x = (undefined != x) ? 1 : 2;");

    fold("if (undefined < undefined){ x = 1; } else { x = 2; }", "x=2");
    fold("if (undefined > undefined){ x = 1; } else { x = 2; }", "x=2");
    fold("if (undefined >= undefined){ x = 1; } else { x = 2; }", "x=2");
    fold("if (undefined <= undefined){ x = 1; } else { x = 2; }", "x=2");

    fold("if (0 < undefined){ x = 1; } else { x = 2; }", "x=2");
    fold("if (true > undefined){ x = 1; } else { x = 2; }", "x=2");
    fold("if ('hi' >= undefined){ x = 1; } else { x = 2; }", "x=2");
    fold("if (null <= undefined){ x = 1; } else { x = 2; }", "x=2");

    fold("if (undefined < 0){ x = 1; } else { x = 2; }", "x=2");
    fold("if (undefined > true){ x = 1; } else { x = 2; }", "x=2");
    fold("if (undefined >= 'hi'){ x = 1; } else { x = 2; }", "x=2");
    fold("if (undefined <= null){ x = 1; } else { x = 2; }", "x=2");

    fold("if (null == undefined){ x = 1; } else { x = 2; }", "x=1");
    // fold("if (NaN == undefined){ x = 1; } else { x = 2; }", "x=2");
    fold("if (0 == undefined){ x = 1; } else { x = 2; }", "x=2");
    fold("if (1 == undefined){ x = 1; } else { x = 2; }", "x=2");
    fold("if ('hi' == undefined){ x = 1; } else { x = 2; }", "x=2");
    fold("if (true == undefined){ x = 1; } else { x = 2; }", "x=2");
    fold("if (false == undefined){ x = 1; } else { x = 2; }", "x=2");
    fold("if (null === undefined){ x = 1; } else { x = 2; }", "x=2");
    fold("if (void 0 === undefined){ x = 1; } else { x = 2; }", "x=1");
    // foldSame("if (void foo() === undefined){ x = 1; } else { x = 2; }");
    foldSame("x = (this == undefined) ? 1 : 2;");
    foldSame("x = (x == undefined) ? 1 : 2;");
  }

  public void testHookIf() {
    fold("if (1){ x=1; } else { x = 2;}", "x=1");
    fold("if (false){ x = 1; } else { x = 2; }", "x=2");
    fold("if (undefined){ x = 1; } else { x = 2; }", "x=2");
    fold("if (null){ x = 1; } else { x = 2; }", "x=2");
    fold("if (void 0){ x = 1; } else { x = 2; }", "x=2");
    // foldSame("if (void foo()){ x = 1; } else { x = 2; }");
    fold("if (false){ x = 1; } else if (true) { x = 3; } else { x = 2; }",
         "x=3");
    fold("if (false){ x = 1; } else if (cond) { x = 2; } else { x = 3; }",
         "x=cond?2:3");
    fold("var x = (true) ? 1 : 0", "var x=1");
    fold("var y = (true) ? ((false) ? 12 : (cond ? 1 : 2)) : 13",
         "var y=cond?1:2");
    fold("if (x){ x = 1; } else if (false) { x = 3; }", "if(x)x=1");
    fold("x?void 0:y()", "x||y()");
    fold("!x?void 0:y()", "x&&y()");
    foldSame("var z=x?void 0:y()");
    foldSame("z=x?void 0:y()");
    foldSame("z*=x?void 0:y()");
    fold("x?y():void 0", "x&&y()");
    foldSame("var z=x?y():void 0");
    foldSame("(w?x:void 0).y=z");
    foldSame("(w?x:void 0).y+=z");
  }

  public void testRemoveDuplicateStatements() {
    fold("if (a) { x = 1; x++ } else { x = 2; x++ }",
         "x=(a) ? 1 : 2; x++");
    fold("if (a) { x = 1; x++; y += 1; z = pi; }" +
         " else  { x = 2; x++; y += 1; z = pi; }",
         "x=(a) ? 1 : 2; x++; y += 1; z = pi;");
    fold("function z() {" +
         "if (a) { foo(); return true } else { goo(); return true }" +
         "}",
         "function z() {(a) ? foo() : goo(); return true}");
    fold("function z() {if (a) { foo(); x = true; return true " +
         "} else { goo(); x = true; return true }}",
         "function z() {(a) ? foo() : goo(); x = true; return true}");
    fold("function z() {if (a) { return true }" +
         "else if (b) { return true }" +
         "else { return true }}",
         "function z() {return true;}");
    fold("function z() {if (a()) { return true }" +
         "else if (b()) { return true }" +
         "else { return true }}",
         "function z() {if (!a()) { b() } return true;}");
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

  public void testNotCond() {
    fold("function(){if(!x)foo()}", "function(){x||foo()}");
    fold("function(){if(!x)b=1}", "function(){x||(b=1)}");
    fold("if(!x)z=1;else if(y)z=2", "if(x){if(y)z=2}else z=1");
    foldSame("function(){if(!(x=1))a.b=1}");
  }

  public void testAndParenthesesCount() {
    foldSame("function(){if(x||y)a.foo()}");
  }

  public void testUnaryOps() {
    fold("!foo()", "foo()");
    fold("~foo()", "foo()");
    fold("-foo()", "foo()");
    fold("a=!true", "a=false");
    fold("a=!10", "a=false");
    fold("a=!false", "a=true");
    fold("a=!foo()", "a=!foo()");
    fold("a=-0", "a=0");
    fold("a=-Infinity", "a=-Infinity");
    fold("a=-NaN", "a=NaN");
    fold("a=-foo()", "a=-foo()");
    fold("a=~~0", "a=0");
    fold("a=~~10", "a=10");
    fold("a=~-7", "a=6");
    fold("a=~0x100000000", "a=~0x100000000",
         FoldConstants.BITWISE_OPERAND_OUT_OF_RANGE);
    fold("a=~-0x100000000", "a=~-0x100000000",
         FoldConstants.BITWISE_OPERAND_OUT_OF_RANGE);
    fold("a=~.5", "~.5", FoldConstants.FRACTIONAL_BITWISE_OPERAND);
  }

  public void testUnaryOpsStringCompare() {
    // Negatives are folded into a single number node.
    assertResultString("a=-1", "a=-1");
    assertResultString("a=~0", "a=-1");
    assertResultString("a=~1", "a=-2");
    assertResultString("a=~101", "a=-102");
  }

  public void testFoldLogicalOp() {
    fold("x = true && x", "x = x");
    fold("x = false && x", "x = false");
    fold("x = true || x", "x = true");
    fold("x = false || x", "x = x");
    fold("x = 0 && x", "x = 0");
    fold("x = 3 || x", "x = 3");
    fold("x = false || 0", "x = 0");

    fold("if(x && true) z()", "x&&z()");
    fold("if(x && false) z()", "");
    fold("if(x || 3) z()", "z()");
    fold("if(x || false) z()", "x&&z()");
    fold("if(x==y && false) z()", "");

    // This would be foldable, but it isn't detected, because 'if' isn't
    // the parent of 'x || 3'. Cf. FoldConstants.tryFoldAndOr().
    fold("if(y() || x || 3) z()", "if(y()||x||1)z()");

    // surprisingly unfoldable
    fold("a = x && true", "a=x&&true");
    fold("a = x && false", "a=x&&false");
    fold("a = x || 3", "a=x||3");
    fold("a = x || false", "a=x||false");
    fold("a = b ? c : x || false", "a=b?c:x||false");
    fold("a = b ? x || false : c", "a=b?x||false:c");
    fold("a = b ? c : x && true", "a=b?c:x&&true");
    fold("a = b ? x && true : c", "a=b?x&&true:c");

    // foldable, analogous to if().
    fold("a = x || false ? b : c", "a=x?b:c");
    fold("a = x && true ? b : c", "a=x?b:c");

    // TODO(user): fold("foo()&&false&&z()", "foo()");
    fold("if(foo() || true) z()", "if(foo()||1)z()");

    fold("x = foo() || true || bar()", "x = foo()||true");
    fold("x = foo() || false || bar()", "x = foo()||bar()");
    fold("x = foo() || true && bar()", "x = foo()||bar()");
    fold("x = foo() || false && bar()", "x = foo()||false");
    fold("x = foo() && false && bar()", "x = foo()&&false");
    fold("x = foo() && true && bar()", "x = foo()&&bar()");
    fold("x = foo() && false || bar()", "x = foo()&&false||bar()");

    // Really not foldable, because it would change the type of the
    // expression if foo() returns something equivalent, but not
    // identical, to true. Cf. FoldConstants.tryFoldAndOr().
    fold("x = foo() && true || bar()", "x = foo()&&true||bar()");
    fold("foo() && true || bar()", "foo()&&1||bar()");
  }

  public void testFoldLogicalOpStringCompare() {
    // side-effects
    // There is two way to parse two &&'s and both are correct.
    assertResultString("if(foo() && false) z()", "foo()&&0&&z()");
  }

  public void testFoldBitwiseOp() {
    fold("x = 1 & 1", "x = 1");
    fold("x = 1 & 2", "x = 0");
    fold("x = 3 & 1", "x = 1");
    fold("x = 3 & 3", "x = 3");

    fold("x = 1 | 1", "x = 1");
    fold("x = 1 | 2", "x = 3");
    fold("x = 3 | 1", "x = 3");
    fold("x = 3 | 3", "x = 3");

    fold("x = -1 & 0", "x = 0");
    fold("x = 0 & -1", "x = 0");
    fold("x = 1 & 4", "x = 0");
    fold("x = 2 & 3", "x = 2");

    // make sure we fold only when we are supposed to -- not when doing so would
    // lose information or when it is performed on nonsensical arguments.
    fold("x = 1 & 1.1", "x = 1&1.1");
    fold("x = 1.1 & 1", "x = 1.1&1");
    fold("x = 1 & 3000000000", "x = 1&3000000000");
    fold("x = 3000000000 & 1", "x = 3000000000&1");

    // Try some cases with | as well
    fold("x = 1 | 4", "x = 5");
    fold("x = 1 | 3", "x = 3");
    fold("x = 1 | 1.1", "x = 1|1.1");
    fold("x = 1 | 3000000000", "x = 1|3000000000");
  }

  public void testFoldBitwiseOpStringCompare() {
    assertResultString("x = -1 | 0", "x=-1");
    assertResultString("-1 | 0", "1");
  }

  public void testFoldBitShifts() {
    fold("x = 1 << 0", "x = 1");
    fold("x = 1 << 1", "x = 2");
    fold("x = 3 << 1", "x = 6");
    fold("x = 1 << 8", "x = 256");

    fold("x = 1 >> 0", "x = 1");
    fold("x = 1 >> 1", "x = 0");
    fold("x = 2 >> 1", "x = 1");
    fold("x = 5 >> 1", "x = 2");
    fold("x = 127 >> 3", "x = 15");
    fold("x = 3 >> 1", "x = 1");
    fold("x = 3 >> 2", "x = 0");
    fold("x = 10 >> 1", "x = 5");
    fold("x = 10 >> 2", "x = 2");
    fold("x = 10 >> 5", "x = 0");

    fold("x = 10 >>> 1", "x = 5");
    fold("x = 10 >>> 2", "x = 2");
    fold("x = 10 >>> 5", "x = 0");
    fold("x = -1 >>> 1", "x = " + 0x7fffffff);

    fold("3000000000 << 1", "3000000000<<1",
         FoldConstants.BITWISE_OPERAND_OUT_OF_RANGE);
    fold("1 << 32", "1<<32",
         FoldConstants.SHIFT_AMOUNT_OUT_OF_BOUNDS);
    fold("1 << -1", "1<<32",
         FoldConstants.SHIFT_AMOUNT_OUT_OF_BOUNDS);
    fold("3000000000 >> 1", "3000000000>>1",
         FoldConstants.BITWISE_OPERAND_OUT_OF_RANGE);
    fold("1 >> 32", "1>>32",
         FoldConstants.SHIFT_AMOUNT_OUT_OF_BOUNDS);
    fold("1.5 << 0",  "1.5<<0",  FoldConstants.FRACTIONAL_BITWISE_OPERAND);
    fold("1 << .5",   "1.5<<0",  FoldConstants.FRACTIONAL_BITWISE_OPERAND);
    fold("1.5 >>> 0", "1.5>>>0", FoldConstants.FRACTIONAL_BITWISE_OPERAND);
    fold("1 >>> .5",  "1.5>>>0", FoldConstants.FRACTIONAL_BITWISE_OPERAND);
    fold("1.5 >> 0",  "1.5>>0",  FoldConstants.FRACTIONAL_BITWISE_OPERAND);
    fold("1 >> .5",   "1.5>>0",  FoldConstants.FRACTIONAL_BITWISE_OPERAND);
  }

  public void testFoldBitShiftsStringCompare() {
    // Negative numbers.
    assertResultString("x = -1 << 1", "x=-2");
    assertResultString("x = -1 << 8", "x=-256");
    assertResultString("x = -1 >> 1", "x=-1");
    assertResultString("x = -2 >> 1", "x=-1");
    assertResultString("x = -1 >> 0", "x=-1");
  }

  public void testStringAdd() {
    fold("x = 'a' + \"bc\"", "x = \"abc\"");
    fold("x = 'a' + 5", "x = \"a5\"");
    fold("x = 5 + 'a'", "x = \"5a\"");
    fold("x = 'a' + ''", "x = \"a\"");
    fold("x = \"a\" + foo()", "x = \"a\"+foo()");
    fold("x = foo() + 'a' + 'b'", "x = foo()+\"ab\"");
    fold("x = (foo() + 'a') + 'b'", "x = foo()+\"ab\"");  // believe it!
    fold("x = foo() + 'a' + 'b' + 'cd' + bar()", "x = foo()+\"abcd\"+bar()");
    fold("x = foo() + 2 + 'b'", "x = foo()+2+\"b\"");  // don't fold!
    fold("x = foo() + 'a' + 2", "x = foo()+\"a2\"");
    fold("x = '' + null", "x = \"null\"");
    fold("x = true + '' + false", "x = \"truefalse\"");
    fold("x = '' + []", "x = \"\"+[]");      // cannot fold (but nice if we can)
  }

  public void testStringIndexOf() {
    fold("x = 'abcdef'.indexOf('b')", "x = 1");
    fold("x = 'abcdefbe'.indexOf('b', 2)", "x = 6");
    fold("x = 'abcdef'.indexOf('bcd')", "x = 1");
    fold("x = 'abcdefsdfasdfbcdassd'.indexOf('bcd', 4)", "x = 13");

    fold("x = 'abcdef'.lastIndexOf('b')", "x = 1");
    fold("x = 'abcdefbe'.lastIndexOf('b')", "x = 6");
    fold("x = 'abcdefbe'.lastIndexOf('b', 5)", "x = 1");

    // Both elements must be string. Dont do anything if either one is not
    // string.
    fold("x = 'abc1def'.indexOf(1)", "x = 3");
    fold("x = 'abcNaNdef'.indexOf(NaN)", "x = 3");
    fold("x = 'abcundefineddef'.indexOf(undefined)", "x = 3");
    fold("x = 'abcnulldef'.indexOf(null)", "x = 3");
    fold("x = 'abctruedef'.indexOf(true)", "x = 3");


    // The following testcase fails with JSC_PARSE_ERROR. Hence omitted.
    // foldSame("x = 1.indexOf('bcd');");
    foldSame("x = NaN.indexOf('bcd')");
    foldSame("x = undefined.indexOf('bcd')");
    foldSame("x = null.indexOf('bcd')");
    foldSame("x = true.indexOf('bcd')");
    foldSame("x = false.indexOf('bcd')");

    //Avoid dealing with regex or other types.
    foldSame("x = 'abcdef'.indexOf(/b./)");
    foldSame("x = 'abcdef'.indexOf({a:2})");
    foldSame("x = 'abcdef'.indexOf([1,2])");
  }

  public void testStringJoinAdd() {
    fold("x = ['a', 'b', 'c'].join('')", "x = \"abc\"");
    fold("x = [].join(',')", "x = \"\"");
    fold("x = ['a'].join(',')", "x = \"a\"");
    fold("x = ['a', 'b', 'c'].join(',')", "x = \"a,b,c\"");
    fold("x = ['a', foo, 'b', 'c'].join(',')", "x = [\"a\",foo,\"b,c\"].join(\",\")");
    fold("x = [foo, 'a', 'b', 'c'].join(',')", "x = [foo,\"a,b,c\"].join(\",\")");
    fold("x = ['a', 'b', 'c', foo].join(',')", "x = [\"a,b,c\",foo].join(\",\")");

    // Works with numbers
    fold("x = ['a=', 5].join('')", "x = \"a=5\"");
    fold("x = ['a', '5'].join(7)", "x = \"a75\"");

    // Works on boolean
    fold("x = ['a=', false].join('')", "x = \"a=false\"");
    fold("x = ['a', '5'].join(true)", "x = \"atrue5\"");
    fold("x = ['a', '5'].join(false)", "x = \"afalse5\"");

    // Only optimize if it's a size win.
    fold("x = ['a', '5', 'c'].join('a very very very long chain')",
         "x = [\"a\",\"5\",\"c\"].join(\"a very very very long chain\")");

    // TODO(user): Its possible to fold this better.
    foldSame("x = ['', foo].join(',')");
    foldSame("x = ['', foo, ''].join(',')");

    fold("x = ['', '', foo, ''].join(',')", "x = [',', foo, ''].join(',')");
    fold("x = ['', '', foo, '', ''].join(',')",
         "x = [',', foo, ','].join(',')");

    fold("x = ['', '', foo, '', '', bar].join(',')",
         "x = [',', foo, ',', bar].join(',')");

    fold("x = [1,2,3].join('abcdef')",
         "x = '1abcdef2abcdef3'");
  }

  public void testStringJoinAdd_b1992789() {
    fold("x = ['a'].join('')", "x = \"a\"");
    fold("x = [foo()].join('')", "x = '' + foo()");
    fold("[foo()].join('')", "'' + foo()");
  }

  public void testFoldArithmetic() {
    fold("x = 10 + 20", "x = 30");
    fold("x = 2 / 4", "x = 0.5");
    fold("x = 2.25 * 3", "x = 6.75");
    fold("z = x * y", "z = x * y");
    fold("x = y * 5", "x = y * 5");
    fold("x = 1 / 0", "", FoldConstants.DIVIDE_BY_0_ERROR);
  }

  public void testFoldArithmeticStringComp() {
    // Negative Numbers.
    assertResultString("x = 10 - 20", "x=-10");
  }

  public void testFoldComparison() {
    fold("x = 0 == 0", "x = true");
    fold("x = 1 == 2", "x = false");
    fold("x = 'abc' == 'def'", "x = false");
    fold("x = 'abc' == 'abc'", "x = true");
    fold("x = \"\" == ''", "x = true");
    fold("x = foo() == bar()", "x = foo()==bar()");

    fold("x = 1 != 0", "x = true");
    fold("x = 'abc' != 'def'", "x = true");
    fold("x = 'a' != 'a'", "x = false");

    fold("x = 1 < 20", "x = true");
    fold("x = 3 < 3", "x = false");
    fold("x = 10 > 1.0", "x = true");
    fold("x = 10 > 10.25", "x = false");
    fold("x = y == y", "x = y==y");
    fold("x = y < y", "x = false");
    fold("x = y > y", "x = false");
    fold("x = 1 <= 1", "x = true");
    fold("x = 1 <= 0", "x = false");
    fold("x = 0 >= 0", "x = true");
    fold("x = -1 >= 9", "x = false");

    fold("x = true == true", "x = true");
    fold("x = true == true", "x = true");
    fold("x = false == null", "x = false");
    fold("x = false == true", "x = false");
    fold("x = true == null", "x = false");

    fold("0 == 0", "1");
    fold("1 == 2", "0");
    fold("'abc' == 'def'", "0");
    fold("'abc' == 'abc'", "1");
    fold("\"\" == ''", "1");
    fold("foo() == bar()", "foo()==bar()");

    fold("1 != 0", "1");
    fold("'abc' != 'def'", "1");
    fold("'a' != 'a'", "0");

    fold("1 < 20", "1");
    fold("3 < 3", "0");
    fold("10 > 1.0", "1");
    fold("10 > 10.25", "0");
    fold("x == x", "x==x");
    fold("x < x", "0");
    fold("x > x", "0");
    fold("1 <= 1", "1");
    fold("1 <= 0", "0");
    fold("0 >= 0", "1");
    fold("-1 >= 9", "0");

    fold("true == true", "1");
    fold("false == null", "0");
    fold("false == true", "0");
    fold("true == null", "0");
  }

  // ===, !== comparison tests
  public void testFoldComparison2() {
    fold("x = 0 === 0", "x = true");
    fold("x = 1 === 2", "x = false");
    fold("x = 'abc' === 'def'", "x = false");
    fold("x = 'abc' === 'abc'", "x = true");
    fold("x = \"\" === ''", "x = true");
    fold("x = foo() === bar()", "x = foo()===bar()");

    fold("x = 1 !== 0", "x = true");
    fold("x = 'abc' !== 'def'", "x = true");
    fold("x = 'a' !== 'a'", "x = false");

    fold("x = y === y", "x = y===y");

    fold("x = true === true", "x = true");
    fold("x = true === true", "x = true");
    fold("x = false === null", "x = false");
    fold("x = false === true", "x = false");
    fold("x = true === null", "x = false");

    fold("0 === 0", "1");
    fold("1 === 2", "0");
    fold("'abc' === 'def'", "0");
    fold("'abc' === 'abc'", "1");
    fold("\"\" === ''", "1");
    fold("foo() === bar()", "foo()===bar()");

    // TODO(johnlenz): It would be nice to handle these cases as well.
    foldSame("1 === '1'");
    foldSame("1 === true");
    foldSame("1 !== '1'");
    foldSame("1 !== true");

    fold("1 !== 0", "1");
    fold("'abc' !== 'def'", "1");
    fold("'a' !== 'a'", "0");

    fold("x === x", "x===x");

    fold("true === true", "1");
    fold("false === null", "0");
    fold("false === true", "0");
    fold("true === null", "0");
  }

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
  }

  public void testFoldGetElem() {
    fold("x = [10, 20][0]", "x = 10");
    fold("x = [10, 20][1]", "x = 20");
    fold("x = [10, 20][0.5]", "", FoldConstants.INVALID_GETELEM_INDEX_ERROR);
    fold("x = [10, 20][-1]",    "", FoldConstants.INDEX_OUT_OF_BOUNDS_ERROR);
    fold("x = [10, 20][2]",     "", FoldConstants.INDEX_OUT_OF_BOUNDS_ERROR);
  }

  public void testFoldComplex() {
    fold("x = (3 / 1.0) + (1 * 2)", "x = 5");
    fold("x = (1 == 1.0) && foo() && true", "x = foo()&&true");
    fold("x = 'abc' + 5 + 10", "x = \"abc510\"");
  }

  public void testFoldArrayLength() {
    // Can fold
    fold("x = [].length", "x = 0");
    fold("x = [1,2,3].length", "x = 3");
    fold("x = [a,b].length", "x = 2");

    // Cannot fold
    fold("x = [foo(), 0].length", "x = [foo(),0].length");
    fold("x = y.length", "x = y.length");
  }

  public void testFoldStringLength() {
    // Can fold basic strings.
    fold("x = ''.length", "x = 0");
    fold("x = '123'.length", "x = 3");

    // Test unicode escapes are accounted for.
    fold("x = '123\u01dc'.length", "x = 4");
  }

  public void testFoldRegExpConstructor() {
    // Cannot fold
    // Too few arguments
    fold("x = new RegExp",                    "x = RegExp()");
    // Empty regexp should not fold to // since that is a line comment in js
    fold("x = new RegExp(\"\")",              "x = RegExp(\"\")");
    fold("x = new RegExp(\"\", \"i\")",       "x = RegExp(\"\",\"i\")");
    // Bogus flags should not fold
    fold("x = new RegExp(\"foobar\", \"bogus\")",
         "x = RegExp(\"foobar\",\"bogus\")",
         FoldConstants.INVALID_REGULAR_EXPRESSION_FLAGS);
    // Don't fold if the flags contain 'g'
    fold("x = new RegExp(\"foobar\", \"g\")",
         "x = RegExp(\"foobar\",\"g\")");
    fold("x = new RegExp(\"foobar\", \"ig\")",
         "x = RegExp(\"foobar\",\"ig\")");

    // Can Fold
    fold("x = new RegExp(\"foobar\")",        "x = /foobar/");
    fold("x = RegExp(\"foobar\")",            "x = /foobar/");
    fold("x = new RegExp(\"foobar\", \"i\")", "x = /foobar/i");
    // Make sure that escaping works
    fold("x = new RegExp(\"\\\\.\", \"i\")",  "x = /\\./i");
    fold("x = new RegExp(\"/\", \"\")",       "x = /\\//");
    fold("x = new RegExp(\"///\", \"\")",     "x = /\\/\\/\\//");
    fold("x = new RegExp(\"\\\\\\/\", \"\")", "x = /\\//");
    // Don't fold things that crash older versions of Safari and that don't work
    // as regex literals on recent versions of Safari
    fold("x = new RegExp(\"\\u2028\")", "x = RegExp(\"\\u2028\")");
    fold("x = new RegExp(\"\\\\\\\\u2028\")", "x = /\\\\u2028/");

    // Don't fold really long regexp literals, because Opera 9.2's
    // regexp parser will explode.
    String longRegexp = "";
    for (int i = 0; i < 200; i++) longRegexp += "x";
    foldSame("x = RegExp(\"" + longRegexp + "\")");
  }

  public void testFoldRegExpConstructorStringCompare() {
    // Might have something to do with the internal representation of \n and how
    // it is used in node comparison.
    assertResultString("x=new RegExp(\"\\n\", \"i\")", "x=/\\n/i");
  }

  public void testFoldTypeof() {
    fold("x = typeof 1", "x = \"number\"");
    fold("x = typeof 'foo'", "x = \"string\"");
    fold("x = typeof true", "x = \"boolean\"");
    fold("x = typeof false", "x = \"boolean\"");
    fold("x = typeof null", "x = \"object\"");
    fold("x = typeof undefined", "x = \"undefined\"");
    fold("x = typeof []", "x = \"object\"");
    fold("x = typeof [1]", "x = \"object\"");
    fold("x = typeof [1,[]]", "x = \"object\"");
    fold("x = typeof {}", "x = \"object\"");

    foldSame("x = typeof[1,[foo()]]");
    foldSame("x = typeof{bathwater:baby()}");
  }

  public void testFoldLiteralConstructors() {
    // Can fold
    fold("x = new Array", "x = []");
    fold("x = new Array()", "x = []");
    fold("x = Array()", "x = []");
    fold("x = new Object", "x = ({})");
    fold("x = new Object()", "x = ({})");
    fold("x = Object()", "x = ({})");

    // Cannot fold, there are arguments
    fold("x = new Array(7)", "x = Array(7)");

    // Cannot fold, the constructor being used is actually a local function
    fold("x = " +
         "(function(){function Object(){this.x=4};return new Object();})();",
         "x = (function(){function Object(){this.x=4}return new Object})()");
  }

  public void testVarLifting() {
    fold("if(true)var a", "var a");
    fold("if(false)var a", "var a");
    fold("if(true);else var a;", "var a");
    fold("if(false) foo();else var a;", "var a");
    fold("if(true)var a;else;", "var a");
    fold("if(false)var a;else;", "var a");
    fold("if(false)var a,b;", "var b; var a");
    fold("if(false){var a;var a;}", "var a");
    fold("if(false)var a=function(){var b};", "var a");
    fold("if(a)if(false)var a;else var b;", "var a;if(a)var b");
  }

  public void testContainsUnicodeEscape() throws Exception {
    assertTrue(!FoldConstants.containsUnicodeEscape(""));
    assertTrue(!FoldConstants.containsUnicodeEscape("foo"));
    assertTrue( FoldConstants.containsUnicodeEscape("\u2028"));
    assertTrue( FoldConstants.containsUnicodeEscape("\\u2028"));
    assertTrue( FoldConstants.containsUnicodeEscape("foo\\u2028"));
    assertTrue(!FoldConstants.containsUnicodeEscape("foo\\\\u2028"));
    assertTrue( FoldConstants.containsUnicodeEscape("foo\\\\u2028bar\\u2028"));
  }

  public void testBug1438784() throws Exception {
    fold("for(var i=0;i<10;i++)if(x)x.y;", "for(var i=0;i<10;i++);");
  }


  public void testFoldUselessWhile() {
    fold("while(false) { foo() }", "");
    fold("while(!true) { foo() }", "");
    fold("while(void 0) { foo() }", "");
    fold("while(undefined) { foo() }", "");
    fold("while(!false) foo() ", "while(1) foo()");
    fold("while(true) foo() ", "while(1) foo() ");
    fold("while(!void 0) foo()", "while(1) foo()");
    fold("while(false) { var a = 0; }", "var a");

    // Make sure it plays nice with minimizing
    fold("while(false) { foo(); continue }", "");

    // Make sure proper empty nodes are inserted.
    fold("if(foo())while(false){foo()}else bar()", "foo()||bar()");
  }

  public void testFoldUselessFor() {
    fold("for(;false;) { foo() }", "");
    fold("for(;!true;) { foo() }", "");
    fold("for(;void 0;) { foo() }", "");
    fold("for(;undefined;) { foo() }", "");
    fold("for(;!false;) foo() ", "for(;;) foo()");
    fold("for(;true;) foo() ", "for(;;) foo() ");
    fold("for(;1;) foo()", "for(;;) foo()");
    foldSame("for(;;) foo()");
    fold("for(;!void 0;) foo()", "for(;;) foo()");
    fold("for(;false;) { var a = 0; }", "var a");

    // Make sure it plays nice with minimizing
    fold("for(;false;) { foo(); continue }", "");

    // Make sure proper empty nodes are inserted.
    fold("if(foo())for(;false;){foo()}else bar()", "foo()||bar()");
  }

  public void testFoldUselessDo() {
    fold("do { foo() } while(false);", "foo()");
    fold("do { foo() } while(!true);", "foo()");
    fold("do { foo() } while(void 0);", "foo()");
    fold("do { foo() } while(undefined);", "foo()");
    fold("do { foo() } while(!false);", "do { foo() } while(1);");
    fold("do { foo() } while(true);", "do { foo() } while(1);");
    fold("do { foo() } while(!void 0);", "do { foo() } while(1);");
    fold("do { var a = 0; } while(false);", "var a=0");

    // Can't fold with break or continues.
    foldSame("do { foo(); continue; } while(0)");
    foldSame("do { foo(); break; } while(0)");

    // Make sure proper empty nodes are inserted.
    fold("if(foo())do {foo()} while(false) else bar()", "foo()?foo():bar()");
  }

  public void testMinimizeCondition() {
    // This test uses constant folding logic, so is only here for completeness.
    fold("while(!!true) foo()", "while(1) foo()");
    // These test tryMinimizeCondition
    fold("while(!!x) foo()", "while(x) foo()");
    fold("while(!(!x&&!y)) foo()", "while(x||y) foo()");
    fold("while(x||!!y) foo()", "while(x||y) foo()");
    fold("while(!(!!x&&y)) foo()", "while(!(x&&y)) foo()");
  }

  public void testMinimizeCondition_example1() {
    // Based on a real failing code sample.
    fold("if(!!(f() > 20)) {foo();foo()}", "if(f() > 20){foo();foo()}");
  }

  public void testMinimizeWhileConstantCondition() {
    fold("while(true) foo()", "while(1) foo()");
    fold("while(!false) foo()", "while(1) foo()");
    fold("while(202) foo()", "while(1) foo()");
    fold("while(Infinity) foo()", "while(1) foo()");
    fold("while('text') foo()", "while(1) foo()");
    fold("while([]) foo()", "while(1) foo()");
    fold("while({}) foo()", "while(1) foo()");
    fold("while(/./) foo()", "while(1) foo()");
    fold("while(0) foo()", "");
    fold("while(0.0) foo()", "");
    fold("while(NaN) foo()", "");
    fold("while(null) foo()", "");
    fold("while(undefined) foo()", "");
    fold("while('') foo()", "");
  }

  public void testMinimizeExpr() {
    fold("!!true", "0");
    fold("!!x", "x");
    fold("!(!x&&!y)", "!x&&!y");
    fold("x||!!y", "x||y");
    fold("!(!!x&&y)", "x&&y");
  }

  public void testBug1509085() {
    new FoldConstantsTest() {
      @Override
      protected int getNumRepetitions() {
        return 1;
      }
    }.fold("x ? x() : void 0", "if(x) x();");
  }

  public void testFoldInstanceOf() {
    // Non object types are never instances of anything.
    fold("64 instanceof Object", "0");
    fold("64 instanceof Number", "0");
    fold("'' instanceof Object", "0");
    fold("'' instanceof String", "0");
    fold("true instanceof Object", "0");
    fold("true instanceof Boolean", "0");
    fold("false instanceof Object", "0");
    fold("null instanceof Object", "0");
    fold("undefined instanceof Object", "0");
    fold("NaN instanceof Object", "0");
    fold("Infinity instanceof Object", "0");

    // Array and object literals are known to be objects.
    fold("[] instanceof Object", "1");
    fold("({}) instanceof Object", "1");

    // These cases is foldable, but no handled currently.
    foldSame("new Foo() instanceof Object");
    // These would require type information to fold.
    foldSame("[] instanceof Foo");
    foldSame("({}) instanceof Foo");
  }

  public void testDivision() {
    // Make sure the 1/3 does not expand to 0.333333
    fold("print(1/3)", "print(1/3)");

    // Decimal form is preferable to fraction form when strings are the
    // same length.
    fold("print(1/2)", "print(0.5)");
  }

  public void testAssignOps() {
    fold("x=x+y", "x+=y");
    fold("x=x*y", "x*=y");
    fold("x.y=x.y+z", "x.y+=z");
    foldSame("next().x = next().x + 1");
  }

  public void testFoldConditionalVarDeclaration() {
    fold("if(x) var y=1;else y=2", "var y=x?1:2");
    fold("if(x) y=1;else var y=2", "var y=x?1:2");

    foldSame("if(x) var y = 1; z = 2");
    foldSame("if(x) y = 1; var z = 2");

    foldSame("if(x) { var y = 1; print(y)} else y = 2 ");
    foldSame("if(x) var y = 1; else {y = 2; print(y)}");
  }

  public void testFoldReturnResult() {
    foldSame("function f(){return false;}");
    foldSame("function f(){return null;}");
    fold("function f(){return void 0;}",
         "function f(){return}");
    foldSame("function f(){return void foo();}");
    fold("function f(){return undefined;}",
         "function f(){return}");
    fold("function(){if(a()){return undefined;}}",
         "function(){if(a()){return}}");
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

  public void testFoldConstantCommaExpressions() {
    fold("if (true, false) {foo()}", "");
    fold("if (false, true) {foo()}", "foo()");
    fold("true, foo()", "foo()");
    fold("(1 + 2 + ''), foo()", "foo()");
  }

  public void testSplitCommaExpressions() {
    // Don't try to split in expressions.
    foldSame("if (foo(), true) boo()");
    foldSame("var a = (foo(), true);");
    foldSame("a = (foo(), true);");

    fold("(x=2), foo()", "x=2; foo()");
    fold("foo(), boo();", "foo(); boo()");
    fold("(a(), b()), (c(), d());", "a(); b(); c(); d();");
    // TODO(johnlenz): interestingly we don't remove side-effect free expression
    // in a script block (as it is currently part of block folding), so "1;"
    // is left.
    fold("foo(), true", "foo();1");
    fold("function x(){foo(), true}", "function x(){foo();}");
  }

  public void testFoldStandardConstructors() {
    foldSame("new Foo('a')");
    foldSame("var x = new goog.Foo(1)");
    foldSame("var x = new String(1)");
    foldSame("var x = new Number(1)");
    foldSame("var x = new Boolean(1)");
    fold("var x = new Object('a')", "var x = Object('a')");
    fold("var x = new RegExp('')", "var x = RegExp('')");
    fold("var x = new Error('20')", "var x = Error(\"20\")");
    fold("var x = new Array('20')", "var x = Array(\"20\")");
  }
}
