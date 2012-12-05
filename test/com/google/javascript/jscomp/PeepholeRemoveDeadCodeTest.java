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

import com.google.javascript.rhino.Node;

/**
 * Tests for PeepholeRemoveDeadCodeTest in isolation. Tests for the interaction
 * of multiple peephole passes are in PeepholeIntegrationTest.
 */
public class PeepholeRemoveDeadCodeTest extends CompilerTestCase {

  private static final String MATH =
      "/** @const */ var Math = {};" +
      "/** @nosideeffects */ Math.random = function(){};" +
      "/** @nosideeffects */ Math.sin = function(){};";

  public PeepholeRemoveDeadCodeTest() {
    super(MATH);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    enableLineNumberCheck(true);
  }

  @Override
  public CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        SimpleDefinitionFinder definitionFinder =
            new SimpleDefinitionFinder(compiler);
        definitionFinder.process(externs, root);
        new PureFunctionIdentifier(compiler, definitionFinder)
            .process(externs, root);
        PeepholeOptimizationsPass peepholePass =
            new PeepholeOptimizationsPass(
                compiler, new PeepholeRemoveDeadCode());
        peepholePass.process(externs, root);
      }
    };
  }

  @Override
  protected int getNumRepetitions() {
    // Reduce this to 2 if we get better expression evaluators.
    return 2;
  }

  private void foldSame(String js) {
    testSame(js);
  }

  private void fold(String js, String expected) {
    test(js, expected);
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
    fold("function f(){return;}", "function f(){return;}");
    fold("function f(){return 3;}", "function f(){return 3}");
    fold("function f(){if(x)return; x=3; return; }",
         "function f(){if(x)return; x=3; return; }");
    fold("{x=3;;;y=2;;;}", "x=3;y=2");

    // Cases to test for empty block.
    fold("while(x()){x}", "while(x());");
    fold("while(x()){x()}", "while(x())x()");
    fold("for(x=0;x<100;x++){x}", "for(x=0;x<100;x++);");
    fold("for(x in y){x}", "for(x in y);");
  }

  /** Try to remove spurious blocks with multiple children */
  public void testFoldBlocksWithManyChildren() {
    fold("function f() { if (false) {} }", "function f(){}");
    fold("function f() { { if (false) {} if (true) {} {} } }",
         "function f(){}");
    fold("{var x; var y; var z; function f() { { var a; { var b; } } } }",
         "var x;var y;var z;function f(){var a;var b}");
  }

  public void testIf() {
    fold("if (1){ x=1; } else { x = 2;}", "x=1");
    fold("if (false){ x = 1; } else { x = 2; }", "x=2");
    fold("if (undefined){ x = 1; } else { x = 2; }", "x=2");
    fold("if (null){ x = 1; } else { x = 2; }", "x=2");
    fold("if (void 0){ x = 1; } else { x = 2; }", "x=2");
    fold("if (void foo()){ x = 1; } else { x = 2; }",
         "foo();x=2");
    fold("if (false){ x = 1; } else if (true) { x = 3; } else { x = 2; }",
         "x=3");
    fold("if (x){ x = 1; } else if (false) { x = 3; }",
         "if(x)x=1");
  }

  public void testHook() {
    fold("true ? a() : b()", "a()");
    fold("false ? a() : b()", "b()");

    fold("a() ? b() : true", "a() && b()");
    fold("a() ? true : b()", "a() || b()");

    fold("(a = true) ? b() : c()", "a = true, b()");
    fold("(a = false) ? b() : c()", "a = false, c()");
    fold("do {f()} while((a = true) ? b() : c())",
         "do {f()} while((a = true) , b())");
    fold("do {f()} while((a = false) ? b() : c())",
         "do {f()} while((a = false) , c())");

    fold("var x = (true) ? 1 : 0", "var x=1");
    fold("var y = (true) ? ((false) ? 12 : (cond ? 1 : 2)) : 13",
         "var y=cond?1:2");

    foldSame("var z=x?void 0:y()");
    foldSame("z=x?void 0:y()");
    foldSame("z*=x?void 0:y()");

    foldSame("var z=x?y():void 0");
    foldSame("(w?x:void 0).y=z");
    foldSame("(w?x:void 0).y+=z");

    fold("y = (x ? void 0 : void 0)", "y = void 0");
    fold("y = (x ? f() : f())", "y = f()");
  }

  public void testConstantConditionWithSideEffect1() {
    fold("if (b=true) x=1;", "b=true;x=1");
    fold("if (b=/ab/) x=1;", "b=/ab/;x=1");
    fold("if (b=/ab/){ x=1; } else { x=2; }", "b=/ab/;x=1");
    fold("var b;b=/ab/;if(b)x=1;", "var b;b=/ab/;x=1");
    foldSame("var b;b=f();if(b)x=1;");
    fold("var b=/ab/;if(b)x=1;", "var b=/ab/;x=1");
    foldSame("var b=f();if(b)x=1;");
    foldSame("b=b++;if(b)x=b;");
    fold("(b=0,b=1);if(b)x=b;", "b=0,b=1;if(b)x=b;");
    fold("b=1;if(foo,b)x=b;","b=1;x=b;");
    foldSame("b=1;if(foo=1,b)x=b;");
  }

  public void testConstantConditionWithSideEffect2() {
    fold("(b=true)?x=1:x=2;", "b=true,x=1");
    fold("(b=false)?x=1:x=2;", "b=false,x=2");
    fold("if (b=/ab/) x=1;", "b=/ab/;x=1");
    fold("var b;b=/ab/;(b)?x=1:x=2;", "var b;b=/ab/;x=1");
    foldSame("var b;b=f();(b)?x=1:x=2;");
    fold("var b=/ab/;(b)?x=1:x=2;", "var b=/ab/;x=1");
    foldSame("var b=f();(b)?x=1:x=2;");
  }

  public void testVarLifting() {
    fold("if(true)var a", "var a");
    fold("if(false)var a", "var a");

    // More var lifting tests in PeepholeIntegrationTests
  }

  public void testFoldUselessWhile() {
    fold("while(false) { foo() }", "");

    fold("while(void 0) { foo() }", "");
    fold("while(undefined) { foo() }", "");

    foldSame("while(true) foo()");

    fold("while(false) { var a = 0; }", "var a");

    // Make sure it plays nice with minimizing
    fold("while(false) { foo(); continue }", "");

    fold("while(0) { foo() }", "");
  }

  public void testFoldUselessFor() {
    fold("for(;false;) { foo() }", "");
    fold("for(;void 0;) { foo() }", "");
    fold("for(;undefined;) { foo() }", "");
    fold("for(;true;) foo() ", "for(;;) foo() ");
    foldSame("for(;;) foo()");
    fold("for(;false;) { var a = 0; }", "var a");

    // Make sure it plays nice with minimizing
    fold("for(;false;) { foo(); continue }", "");
  }

  public void testFoldUselessDo() {
    fold("do { foo() } while(false);", "foo()");
    fold("do { foo() } while(void 0);", "foo()");
    fold("do { foo() } while(undefined);", "foo()");
    fold("do { foo() } while(true);", "do { foo() } while(true);");
    fold("do { var a = 0; } while(false);", "var a=0");

    fold("do { var a = 0; } while(!{a:foo()});", "var a=0;foo()");

    // Can't fold with break or continues.
    foldSame("do { foo(); continue; } while(0)");
    foldSame("do { foo(); break; } while(0)");
  }

  public void testMinimizeWhileConstantCondition() {
    fold("while(true) foo()", "while(true) foo()");
    fold("while(0) foo()", "");
    fold("while(0.0) foo()", "");
    fold("while(NaN) foo()", "");
    fold("while(null) foo()", "");
    fold("while(undefined) foo()", "");
    fold("while('') foo()", "");
  }

  public void testFoldConstantCommaExpressions() {
    fold("if (true, false) {foo()}", "");
    fold("if (false, true) {foo()}", "foo()");
    fold("true, foo()", "foo()");
    fold("(1 + 2 + ''), foo()", "foo()");
  }


  public void testRemoveUselessOps() {
    // There are four place where expression results are discarded:
    //  - a top-level expression EXPR_RESULT
    //  - the LHS of a COMMA
    //  - the FOR init expression
    //  - the FOR increment expression

    // Known side-effect free functions calls are removed.
    fold("Math.random()", "");
    fold("Math.random(f() + g())", "f(),g();");
    fold("Math.random(f(),g(),h())", "f(),g(),h();");

    // Calls to functions with unknown side-effects are are left.
    foldSame("f();");
    foldSame("(function () { f(); })();");

    // We know that this function has no side effects because of the
    // PureFunctionIdentifier.
    fold("(function () {})();", "");

    // Uncalled function expressions are removed
    fold("(function () {});", "");
    fold("(function f() {});", "");
    // ... including any code they contain.
    fold("(function () {foo();});", "");

    // Useless operators are removed.
    fold("+f()", "f()");
    fold("a=(+f(),g())", "a=(f(),g())");
    fold("a=(true,g())", "a=g()");
    fold("f(),true", "f()");
    fold("f() + g()", "f(),g()");

    fold("for(;;+f()){}", "for(;;f()){}");
    fold("for(+f();;g()){}", "for(f();;g()){}");
    fold("for(;;Math.random(f(),g(),h())){}", "for(;;f(),g(),h()){}");

    // The optimization cascades into conditional expressions:
    fold("g() && +f()", "g() && f()");
    fold("g() || +f()", "g() || f()");
    fold("x ? g() : +f()", "x ? g() : f()");

    fold("+x()", "x()");
    fold("+x() * 2", "x()");
    fold("-(+x() * 2)", "x()");
    fold("2 -(+x() * 2)", "x()");
    fold("x().foo", "x()");
    foldSame("x().foo()");

    foldSame("x++");
    foldSame("++x");
    foldSame("x--");
    foldSame("--x");
    foldSame("x = 2");
    foldSame("x *= 2");

    // Sanity check, other expression are left alone.
    foldSame("function f() {}");
    foldSame("var x;");
  }

  public void testOptimizeSwitch() {
    fold("switch(a){}", "");
    fold("switch(foo()){}", "foo()");
    fold("switch(a){default:}", "");
    fold("switch(a){default:break;}", "");
    fold("switch(a){default:var b;break;}", "var b");
    fold("switch(a){case 1: default:}", "");
    fold("switch(a){default: case 1:}", "");
    fold("switch(a){default: break; case 1:break;}", "");
    fold("switch(a){default: var b; break; case 1: var c; break;}",
        "var c; var b;");

    // Can't remove cases if a default exists.
    foldSame("function f() {switch(a){default: return; case 1: break;}}");
    foldSame("function f() {switch(a){case 1: foo();}}");
    foldSame("function f() {switch(a){case 3: case 2: case 1: foo();}}");

    fold("function f() {switch(a){case 2: case 1: default: foo();}}",
         "function f() {switch(a){default: foo();}}");
    fold("switch(a){case 1: default:break; case 2: foo()}",
         "switch(a){case 2: foo()}");
    foldSame("switch(a){case 1: goo(); default:break; case 2: foo()}");

    // TODO(johnlenz): merge the useless "case 2"
    foldSame("switch(a){case 1: goo(); case 2:break; case 3: foo()}");

    // Can't remove unused code with a "var" in it.
    fold("switch(1){case 2: var x=0;}", "var x;");
    fold("switch ('repeated') {\n" +
        "case 'repeated':\n" +
        "  foo();\n" +
        "  break;\n" +
        "case 'repeated':\n" +
        "  var x=0;\n" +
        "  break;\n" +
        "}",
        "var x; {foo();}");

    // Can't remove cases if something useful is done.
    foldSame("switch(a){case 1: var c =2; break;}");
    foldSame("function f() {switch(a){case 1: return;}}");
    foldSame("x:switch(a){case 1: break x;}");

    fold("switch ('foo') {\n" +
        "case 'foo':\n" +
        "  foo();\n" +
        "  break;\n" +
        "case 'bar':\n" +
        "  bar();\n" +
        "  break;\n" +
        "}",
        "{foo();}");
    fold("switch ('noMatch') {\n" +
        "case 'foo':\n" +
        "  foo();\n" +
        "  break;\n" +
        "case 'bar':\n" +
        "  bar();\n" +
        "  break;\n" +
        "}",
        "");
    foldSame("switch ('fallThru') {\n" +
        "case 'fallThru':\n" +
        "  if (foo(123) > 0) {\n" +
        "    foobar(1);\n" +
        "    break;\n" +
        "  }\n" +
        "  foobar(2);\n" +
        "case 'bar':\n" +
        "  bar();\n" +
        "}");
    foldSame("switch ('fallThru') {\n" +
        "case 'fallThru':\n" +
        "  foo();\n" +
        "case 'bar':\n" +
        "  bar();\n" +
        "}");
    foldSame("switch ('hasDefaultCase') {\n" +
        "case 'foo':\n" +
        "  foo();\n" +
        "  break;\n" +
        "default:\n" +
        "  bar();\n" +
        "  break;\n" +
        "}");
    fold("switch ('repeated') {\n" +
        "case 'repeated':\n" +
        "  foo();\n" +
        "  break;\n" +
        "case 'repeated':\n" +
        "  bar();\n" +
        "  break;\n" +
        "}",
        "{foo();}");
    fold("switch ('foo') {\n" +
        "case 'bar':\n" +
        "  bar();\n" +
        "  break;\n" +
        "case notConstant:\n" +
        "  foobar();\n" +
        "  break;\n" +
        "case 'foo':\n" +
        "  foo();\n" +
        "  break;\n" +
        "}",
        "switch ('foo') {\n" +
        "case notConstant:\n" +
        "  foobar();\n" +
        "  break;\n" +
        "case 'foo':\n" +
        "  foo();\n" +
        "  break;\n" +
        "}");
    fold("switch (1) {\n" +
        "case 1:\n" +
        "  foo();\n" +
        "  break;\n" +
        "case 2:\n" +
        "  bar();\n" +
        "  break;\n" +
        "}",
        "{foo();}");
    fold("switch (1) {\n" +
        "case 1.1:\n" +
        "  foo();\n" +
        "  break;\n" +
        "case 2:\n" +
        "  bar();\n" +
        "  break;\n" +
        "}",
        "");
    foldSame("switch (0) {\n" +
        "case NaN:\n" +
        "  foobar();\n" +
        "  break;\n" +
        "case -0.0:\n" +
        "  foo();\n" +
        "  break;\n" +
        "case 2:\n" +
        "  bar();\n" +
        "  break;\n" +
        "}");
    foldSame("switch ('\\v') {\n" +
        "case '\\u000B':\n" +
        "  foo();\n" +
        "}");
    foldSame("switch ('empty') {\n" +
        "case 'empty':\n" +
        "case 'foo':\n" +
        "  foo();\n" +
        "}");
  }

  public void testRemoveNumber() {
    test("3", "");
  }

  public void testRemoveVarGet1() {
    test("a", "");
  }

  public void testRemoveVarGet2() {
    test("var a = 1;a", "var a = 1");
  }

  public void testRemoveNamespaceGet1() {
    test("var a = {};a.b", "var a = {}");
  }

  public void testRemoveNamespaceGet2() {
    test("var a = {};a.b=1;a.b", "var a = {};a.b=1");
  }

  public void testRemovePrototypeGet1() {
    test("var a = {};a.prototype.b", "var a = {}");
  }

  public void testRemovePrototypeGet2() {
    test("var a = {};a.prototype.b = 1;a.prototype.b",
         "var a = {};a.prototype.b = 1");
  }

  public void testRemoveAdd1() {
    test("1 + 2", "");
  }

  public void testNoRemoveVar1() {
    testSame("var a = 1");
  }

  public void testNoRemoveVar2() {
    testSame("var a = 1, b = 2");
  }

  public void testNoRemoveAssign1() {
    testSame("a = 1");
  }

  public void testNoRemoveAssign2() {
    testSame("a = b = 1");
  }

  public void testNoRemoveAssign3() {
    test("1 + (a = 2)", "a = 2");
  }

  public void testNoRemoveAssign4() {
    testSame("x.a = 1");
  }

  public void testNoRemoveAssign5() {
    testSame("x.a = x.b = 1");
  }

  public void testNoRemoveAssign6() {
    test("1 + (x.a = 2)", "x.a = 2");
  }

  public void testNoRemoveCall1() {
    testSame("a()");
  }

  public void testNoRemoveCall2() {
    test("a()+b()", "a(),b()");
  }

  public void testNoRemoveCall3() {
    testSame("a() && b()");
  }

  public void testNoRemoveCall4() {
    testSame("a() || b()");
  }

  public void testNoRemoveCall5() {
    test("a() || 1", "a()");
  }

  public void testNoRemoveCall6() {
    testSame("1 || a()");
  }

  public void testNoRemoveThrow1() {
    testSame("function f(){throw a()}");
  }

  public void testNoRemoveThrow2() {
    testSame("function f(){throw a}");
  }

  public void testNoRemoveThrow3() {
    testSame("function f(){throw 10}");
  }

  public void testRemoveInControlStructure1() {
    test("if(x()) 1", "x()");
  }

  public void testRemoveInControlStructure2() {
    test("while(2) 1", "while(2);");
  }

  public void testRemoveInControlStructure3() {
    test("for(1;2;3) 4", "for(;;);");
  }

  public void testHook1() {
    test("1 ? 2 : 3", "");
  }

  public void testHook2() {
    test("x ? a() : 3", "x && a()");
  }

  public void testHook3() {
    test("x ? 2 : a()", "x || a()");
  }

  public void testHook4() {
    testSame("x ? a() : b()");
  }

  public void testHook5() {
    test("a() ? 1 : 2", "a()");
  }

  public void testHook6() {
    test("a() ? b() : 2", "a() && b()");
  }

  // TODO(johnlenz): Consider adding a post optimization pass to
  // convert OR into HOOK to save parentheses when the operator
  // precedents would require them.
  public void testHook7() {
    test("a() ? 1 : b()", "a() || b()");
  }

  public void testHook8() {
    testSame("a() ? b() : c()");
  }

  public void testShortCircuit1() {
    testSame("1 && a()");
  }

  public void testShortCircuit2() {
    test("1 && a() && 2", "1 && a()");
  }

  public void testShortCircuit3() {
    test("a() && 1 && 2", "a()");
  }

  public void testShortCircuit4() {
    testSame("a() && 1 && b()");
  }

  public void testComplex1() {
    test("1 && a() + b() + c()", "1 && (a(), b(), c())");
  }

  public void testComplex2() {
    test("1 && (a() ? b() : 1)", "1 && a() && b()");
  }

  public void testComplex3() {
    test("1 && (a() ? b() : 1 + c())", "1 && (a() ? b() : c())");
  }

  public void testComplex4() {
    test("1 && (a() ? 1 : 1 + c())", "1 && (a() || c())");
  }

  public void testComplex5() {
    // can't simplify LHS of short circuit statements with side effects
    testSame("(a() ? 1 : 1 + c()) && foo()");
  }

  public void testNoRemoveFunctionDeclaration1() {
    testSame("function foo(){}");
  }

  public void testNoRemoveFunctionDeclaration2() {
    testSame("var foo = function (){}");
  }

  public void testNoSimplifyFunctionArgs1() {
    testSame("f(1 + 2, 3 + g())");
  }

  public void testNoSimplifyFunctionArgs2() {
    testSame("1 && f(1 + 2, 3 + g())");
  }

  public void testNoSimplifyFunctionArgs3() {
    testSame("1 && foo(a() ? b() : 1 + c())");
  }

  public void testNoRemoveInherits1() {
    testSame("var a = {}; this.b = {}; var goog = {}; goog.inherits(b, a)");
  }

  public void testNoRemoveInherits2() {
    test("var a = {}; this.b = {}; var goog = {}; goog.inherits(b, a) + 1",
         "var a = {}; this.b = {}; var goog = {}; goog.inherits(b, a)");
  }

  public void testNoRemoveInherits3() {
    testSame("this.a = {}; var b = {}; b.inherits(a);");
  }

  public void testNoRemoveInherits4() {
    test("this.a = {}; var b = {}; b.inherits(a) + 1;",
         "this.a = {}; var b = {}; b.inherits(a)");
  }

  public void testRemoveFromLabel1() {
    test("LBL: void 0", "LBL: {}");
  }

  public void testRemoveFromLabel2() {
    test("LBL: foo() + 1 + bar()", "LBL: foo(),bar()");
  }

  public void testCall1() {
    test("Math.sin(0);", "");
  }

  public void testCall2() {
    test("1 + Math.sin(0);", "");
  }

  public void testNew1() {
    test("new Date;", "");
  }

  public void testNew2() {
    test("1 + new Date;", "");
  }

  public void testFoldAssign() {
    test("x=x", "");
    testSame("x=xy");
    testSame("x=x + 1");
    testSame("x.a=x.a");
    test("var y=(x=x)", "var y=x");
    test("y=1 + (x=x)", "y=1 + x");
  }

  public void testTryCatchFinally() {
    testSame("try {foo()} catch (e) {bar()}");
    testSame("try { try {foo()} catch (e) {bar()}} catch (x) {bar()}");
    test("try {var x = 1} finally {}", "var x = 1;");
    testSame("try {var x = 1} finally {x()}");
    test("function f() { return; try{var x = 1}finally{} }",
        "function f() { return; var x = 1; }");
    test("try {} finally {x()}", "x()");
    test("try {} catch (e) { bar()} finally {x()}", "x()");
    test("try {} catch (e) { bar()}", "");
    test("try {} catch (e) { var a = 0; } finally {x()}", "var a; x()");
    test("try {} catch (e) {}", "");
    test("try {} finally {}", "");
    test("try {} catch (e) {} finally {}", "");
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
}
