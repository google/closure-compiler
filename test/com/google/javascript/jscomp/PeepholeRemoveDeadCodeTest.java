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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for PeepholeRemoveDeadCodeTest in isolation. Tests for the interaction of multiple peephole
 * passes are in PeepholeIntegrationTest.
 */
@RunWith(JUnit4.class)
public final class PeepholeRemoveDeadCodeTest extends CompilerTestCase {

  private static final String MATH =
      lines(
          "/** @const */ var Math = {};",
          "/** @nosideeffects */ Math.random = function(){};",
          "/** @nosideeffects */ Math.sin = function(){};");

  public PeepholeRemoveDeadCodeTest() {
    super(MATH);
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        PeepholeOptimizationsPass peepholePass =
            new PeepholeOptimizationsPass(compiler, getName(), new PeepholeRemoveDeadCode());
        peepholePass.process(externs, root);
      }
    };
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableNormalize();
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    enableComputeSideEffects();
  }

  @Override
  protected int getNumRepetitions() {
    return 2;
  }

  private void foldSame(String js) {
    testSame(js);
  }

  private void fold(String js, String expected) {
    test(js, expected);
  }

  @Test
  public void testRemoveNoOpLabelledStatement() {
    fold("a: break a;", "");
    fold("a: { break a; }", "");

    foldSame("a: { break a; console.log('unreachable'); }");
    foldSame("a: { break a; var x = 1; } x = 2;");

    foldSame("b: { var x = 1; } x = 2;");
    foldSame("a: b: { var x = 1; } x = 2;");
  }

  @Test
  public void testFoldBlock() {
    fold("{{foo()}}", "foo()");
    fold("{foo();{}}", "foo()");
    fold("{{foo()}{}}", "foo()");
    fold("{{foo()}{bar()}}", "foo();bar()");
    fold("{if(false)foo(); {bar()}}", "bar()");
    fold("{if(false)if(false)if(false)foo(); {bar()}}", "bar()");

    fold("{'hi'}", "");
    fold("{x==3}", "");
    fold("{`hello ${foo}`}", "");
    fold("{ (function(){x++}) }", "");
    foldSame("function f(){return;}");
    fold("function f(){return 3;}", "function f(){return 3}");
    foldSame("function f(){if(x)return; x=3; return; }");
    fold("{x=3;;;y=2;;;}", "x=3;y=2");

    // Cases to test for empty block.
    fold("while(x()){x}", "while(x());");
    fold("while(x()){x()}", "while(x())x()");
    fold("for(x=0;x<100;x++){x}", "for(x=0;x<100;x++);");
    fold("for(x in y){x}", "for(x in y);");
    fold("for (x of y) {x}", "for(x of y);");
    foldSame("for (let x = 1; x <10; x++ ) {}");
    foldSame("for (var x = 1; x <10; x++ ) {}");
  }

  @Test
  public void testFoldBlockWithDeclaration_notNormalized() {
    disableNormalize();
    disableComputeSideEffects();

    foldSame("{let x}");
    foldSame("function f() {let x}");
    foldSame("{const x = 1}");
    foldSame("{x = 2; y = 4; let z;}");
    fold("{'hi'; let x;}", "{let x}");
    fold("{x = 4; {let y}}", "x = 4; {let y}");
    foldSame("{class C {}} {class C {}}");
    fold("{label: var x}", "label: var x");
    // `{label: let x}` is a syntax error
    foldSame("{label: var x; let y;}");
  }

  @Test
  public void testFoldBlockWithDeclaration_normalized() {
    fold("{let x}", "let x");
    foldSame("function f() {let x}");
    fold("{const x = 1}", "const x = 1;");
    fold("{x = 2; y = 4; let z;}", "x = 2; y = 4; let z;");
    fold("{'hi'; let x;}", "let x;");
    fold("{x = 4; {let y}}", "x = 4; let y;");
    fold("{class C {}} {class C {}}", "class C {} class C$jscomp$1 {}");
    fold("{label: var x}", "label: var x");
    // `{label: let x}` is a syntax error
    fold("{label: var x; let y;}", "label: var x; let y;");
  }

  /** Try to remove spurious blocks with multiple children */
  @Test
  public void testFoldBlocksWithManyChildren() {
    fold("function f() { if (false) {} }", "function f(){}");
    fold("function f() { { if (false) {} if (true) {} {} } }", "function f(){}");
    fold(
        "{var x; var y; var z; class Foo { constructor() { var a; { var b; } } } }",
        "var x;var y;var z;class Foo { constructor() { var a;var b} }");
    fold("{var x; var y; var z; { { var a; { var b; } } } }", "var x;var y;var z; var a;var b");
  }

  @Test
  public void testIf() {
    fold("if (1){ x=1; } else { x = 2;}", "x=1");
    fold("if (false){ x = 1; } else { x = 2; }", "x=2");
    fold("if (undefined){ x = 1; } else { x = 2; }", "x=2");
    fold("if (null){ x = 1; } else { x = 2; }", "x=2");
    fold("if (void 0){ x = 1; } else { x = 2; }", "x=2");
    fold("if (void foo()){ x = 1; } else { x = 2; }", "foo();x=2");
    fold("if (false){ x = 1; } else if (true) { x = 3; } else { x = 2; }", "x=3");
    fold("if (x){ x = 1; } else if (false) { x = 3; }", "if(x)x=1");
  }

  @Test
  public void testHook() {
    fold("true ? a() : b()", "a()");
    fold("false ? a() : b()", "b()");

    fold("a() ? b() : true", "a() && b()");
    fold("a() ? true : b()", "a() || b()");

    fold("(a = true) ? b() : c()", "a = true, b()");
    fold("(a = false) ? b() : c()", "a = false, c()");
    fold("do {f()} while((a = true) ? b() : c())", "do {f()} while((a = true) , b())");
    fold("do {f()} while((a = false) ? b() : c())", "do {f()} while((a = false) , c())");

    fold("var x = (true) ? 1 : 0", "var x=1");
    fold("var y = (true) ? ((false) ? 12 : (cond ? 1 : 2)) : 13", "var y=cond?1:2");

    foldSame("var z=x?void 0:y()");
    foldSame("z=x?void 0:y()");
    foldSame("z*=x?void 0:y()");

    foldSame("var z=x?y():void 0");
    foldSame("(w?x:void 0).y=z");
    foldSame("(w?x:void 0).y+=z");

    fold("y = (x ? void 0 : void 0)", "y = void 0");
    fold("y = (x ? f() : f())", "y = f()");
    fold("(function(){}) ? function(){} : function(){}", "");
  }

  @Test
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
    fold("b=1;if(foo,b)x=b;", "b=1;x=b;");
    foldSame("b=1;if(foo=1,b)x=b;");
  }

  @Test
  public void testConstantConditionWithSideEffect2() {
    fold("(b=true)?x=1:x=2;", "b=true,x=1");
    fold("(b=false)?x=1:x=2;", "b=false,x=2");
    fold("if (b=/ab/) x=1;", "b=/ab/;x=1");
    fold("var b;b=/ab/;(b)?x=1:x=2;", "var b;b=/ab/;x=1");
    foldSame("var b;b=f();(b)?x=1:x=2;");
    fold("var b=/ab/;(b)?x=1:x=2;", "var b=/ab/;x=1");
    foldSame("var b=f();(b)?x=1:x=2;");
  }

  @Test
  public void testConstantConditionWithSideEffect_coalesce() {
    fold("b = null; b ?? (x = 1)", "b = null; void 0 ?? (x = 1)");
    fold("b = undefined; b ?? (x = 1)", "b = undefined; void 0 ?? (x = 1)");
    fold("b = (fn(), null); b ?? (x = 1)", "b = (fn(), null); void 0 ?? (x = 1)");

    fold("b = 34; b ?? (x = 1)", "b = 34; 0 ?? (x = 1)");
    fold("b = 'test'; b ?? (x = 1)", "b = 'test'; 0 ?? (x = 1)");
    fold("b = []; b ?? (x = 1)", "b = []; 0 ?? (x = 1)");
    fold("b = (fn(), 0); b ?? (x = 1)", " b= (fn(), 0); 0 ?? (x = 1)");

    foldSame("b = fn(); b ?? (x = 1)");
  }

  @Test
  public void testVarLifting() {
    fold("if(true)var a", "var a");
    fold("if(false)var a", "var a");

    // More var lifting tests in PeepholeIntegrationTests
  }

  @Test
  public void testLetConstLifting() {
    fold("if(true) {const x = 1}", "const x = 1;");
    fold("if(false) {const x = 1}", "");
    fold("if(true) {let x}", "let x;");
    fold("if(false) {let x}", "");
  }

  @Test
  public void testFoldUselessFor() {
    fold("for(;false;) { foo() }", "");
    fold("for(;void 0;) { foo() }", "");
    fold("for(;undefined;) { foo() }", "");
    fold("for(;true;) foo() ", "for(;;) foo() ");
    foldSame("for(;;) foo()");
    fold("for(;false;) { var a = 0; }", "var a");
    fold("for(;false;) { const a = 0; }", "");
    fold("for(;false;) { let a = 0; }", "");

    // Make sure it plays nice with minimizing
    fold("for(;false;) { foo(); continue }", "");

    fold("l1:for(;false;) {  }", "");
  }

  @Test
  public void testFoldUselessDo() {
    fold("do { foo() } while(false);", "foo()");
    fold("do { foo() } while(void 0);", "foo()");
    fold("do { foo() } while(undefined);", "foo()");
    foldSame("do { foo() } while(true);");
    fold("do { var a = 0; } while(false);", "var a=0");

    fold("do { var a = 0; } while(!{a:foo()});", "var a=0;foo()");

    // Can't fold with break or continues.
    foldSame("do { foo(); continue; } while(0)");
    foldSame("do { try { foo() } catch (e) { break; } } while (0);");
    foldSame("do { foo(); break; } while(0)");
    fold("do { for (;;) {foo(); continue;} } while(0)", "for (;;) {foo(); continue;}");
    foldSame("l1: do { for (;;) { foo() } } while(0)");
    fold("do { switch (1) { default: foo(); break} } while(0)", "foo();");
    fold(
        "do { switch (1) { default: foo(); continue} } while(0)",
        "do { foo(); continue } while(0)");

    fold("l1: { do { x = 1; break l1; } while (0); x = 2; }", "l1: { x = 1; break l1; x = 2;}");

    fold("do { x = 1; } while (x = 0);", "x = 1; x = 0;");
    fold(
        "let x = 1; (function() { do { let x = 2; } while (x = 10, false); })();",
        "let x = 1; (function() { let x$jscomp$1 = 2; x = 10 })();");
  }

  @Test
  public void testFoldEmptyDo() {
    fold("do { } while(true);", "for (;;);");
  }

  @Test
  public void testMinimizeLoop_withConstantCondition_vanillaFor() {
    fold("for(;true;) foo()", "for(;;) foo()");
    fold("for(;0;) foo()", "");
    fold("for(;0.0;) foo()", "");
    fold("for(;NaN;) foo()", "");
    fold("for(;null;) foo()", "");
    fold("for(;undefined;) foo()", "");
    fold("for(;'';) foo()", "");
  }

  @Test
  public void testMinimizeLoop_withConstantCondition_doWhile() {
    fold("do { foo(); } while (true)", "do { foo(); } while (true);");
    fold("do { foo(); } while (0)", "foo();");
    fold("do { foo(); } while (0.0)", "foo();");
    fold("do { foo(); } while (NaN)", "foo();");
    fold("do { foo(); } while (null)", "foo();");
    fold("do { foo(); } while (undefined)", "foo();");
    fold("do { foo(); } while ('')", "foo();");
  }

  @Test
  public void testFoldConstantCommaExpressions() {
    fold("if (true, false) {foo()}", "");
    fold("if (false, true) {foo()}", "foo()");
    fold("true, foo()", "foo()");
    fold("true, foo?.()", "foo?.()");
    fold("(1 + 2 + ''), foo()", "foo()");
    fold("(1 + 2 + ''), foo?.()", "foo?.()");
  }

  @Test
  public void testRemoveUselessOps1() {
    foldSame("(function () { f(); })();");
  }

  @Test
  public void testCallSideEffectsPreserved() {
    // Functions calls known to be free of side effects are removed.
    fold("Math.random()", "");
    fold("Math?.random()", "");
    fold("Math.random(f() + g())", "f(),g();");
    fold("Math?.random(f() + g())", "f(),g();");
    fold("Math.random(f(),g(),h())", "f(),g(),h();");
    fold("Math?.random(f(),g(),h())", "f(),g(),h();");

    // Calls to functions with unknown side-effects are preserved.
    foldSame("f();");
    foldSame("f?.();");
    foldSame("(function () { f(); })();");
  }

  @Test
  public void testRemoveUselessOps2() {
    // There are four place where expression results are discarded:
    //  - a top-level expression EXPR_RESULT
    //  - the LHS of a COMMA
    //  - the FOR init expression
    //  - the FOR increment expression

    // We know that this function has no side effects because of the
    // PureFunctionIdentifier.
    fold("(function () {})();", "");

    // Uncalled function expressions are removed
    fold("(function () {});", "");
    fold("(function f() {});", "");
    fold("(function* f() {})", "");
    // ... including any code they contain.
    fold("(function () {foo();});", "");

    // Useless operators are removed.
    fold("+f()", "f()");
    fold("+f?.()", "f?.()");
    fold("a=(+f(),g())", "a=(f(),g())");
    fold("a=(+f?.(),g())", "a=(f?.(),g())");
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

  @Test
  public void testOptimizeSwitch() {
    fold("switch(a){}", "");
    fold("switch(foo()){}", "foo()");
    fold("switch(a){default:}", "");
    fold("switch(a){default:break;}", "");
    fold("switch(a){default:var b;break;}", "var b");
    fold("switch(a){case 1: default:}", "");
    fold("switch(a){default: case 1:}", "");
    fold("switch(a){default: break; case 1:break;}", "");
    fold("switch(a){default: var b; break; case 1: var c; break;}", "var c; var b;");
    fold("var x=1; switch(x) { case 1: var y; }", "var y; var x=1;");

    // Can't remove cases if a default exists and is not the last case.
    foldSame("function f() {switch(a){default: return; case 1: break;}}");
    foldSame("function f() {switch(1){default: return; case 1: break;}}"); // foldable
    foldSame("function f() {switch(a){case 1: foo();}}");
    foldSame("function f() {switch(a){case 3: case 2: case 1: foo();}}");

    fold("function f() {switch(a){case 2: case 1: default: foo();}}", "function f() { foo(); }");
    fold("switch(a){case 1: default:break; case 2: foo()}", "switch(a){case 2: foo()}");
    foldSame("switch(a){case 1: goo(); default:break; case 2: foo()}");

    // TODO(johnlenz): merge the useless "case 2"
    foldSame("switch(a){case 1: goo(); case 2:break; case 3: foo()}");

    // Can't remove unused code with a "var" in it.
    fold("switch(1){case 2: var x=0;}", "var x;");
    fold(
        "switch ('repeated') {\n"
            + "case 'repeated':\n"
            + "  foo();\n"
            + "  break;\n"
            + "case 'repeated':\n"
            + "  var x=0;\n"
            + "  break;\n"
            + "}",
        "var x; foo();");

    // Can't remove cases if something useful is done.
    foldSame("switch(a){case 1: var c =2; break;}");
    foldSame("function f() {switch(a){case 1: return;}}");
    foldSame("x:switch(a){case 1: break x;}");

    fold(
        "switch ('foo') {\n"
            + "case 'foo':\n"
            + "  foo();\n"
            + "  break;\n"
            + "case 'bar':\n"
            + "  bar();\n"
            + "  break;\n"
            + "}",
        "foo();");
    fold(
        "switch ('noMatch') {\n"
            + "case 'foo':\n"
            + "  foo();\n"
            + "  break;\n"
            + "case 'bar':\n"
            + "  bar();\n"
            + "  break;\n"
            + "}",
        "");
    fold(
        lines(
            "switch ('fallThru') {",
            "case 'fallThru':",
            "  if (foo(123) > 0) {",
            "    foobar(1);",
            "    break;",
            "  }",
            "  foobar(2);",
            "case 'bar':",
            "  bar();",
            "}"),
        lines(
            "switch ('fallThru') {",
            "case 'fallThru':",
            "  if (foo(123) > 0) {",
            "    foobar(1);",
            "    break;",
            "  }",
            "  foobar(2);",
            "  bar();",
            "}"));
    fold(
        lines(
            "switch ('fallThru') {",
            "case 'fallThru':",
            "  foo();",
            "case 'bar':",
            "  bar();",
            "}"),
        lines("foo();", "bar();"));
    fold(
        lines(
            "switch ('hasDefaultCase') {",
            "  case 'foo':",
            "    foo();",
            "    break;",
            "  default:",
            "    bar();",
            "    break;",
            "}"),
        "bar();");
    fold(
        "switch ('repeated') {\n"
            + "case 'repeated':\n"
            + "  foo();\n"
            + "  break;\n"
            + "case 'repeated':\n"
            + "  bar();\n"
            + "  break;\n"
            + "}",
        "foo();");
    fold(
        "switch ('foo') {\n"
            + "case 'bar':\n"
            + "  bar();\n"
            + "  break;\n"
            + "case notConstant:\n"
            + "  foobar();\n"
            + "  break;\n"
            + "case 'foo':\n"
            + "  foo();\n"
            + "  break;\n"
            + "}",
        "switch ('foo') {\n"
            + "case notConstant:\n"
            + "  foobar();\n"
            + "  break;\n"
            + "case 'foo':\n"
            + "  foo();\n"
            + "  break;\n"
            + "}");
    fold(
        "switch (1) {\n"
            + "case 1:\n"
            + "  foo();\n"
            + "  break;\n"
            + "case 2:\n"
            + "  bar();\n"
            + "  break;\n"
            + "}",
        "foo();");
    fold(
        "switch (1) {\n"
            + "case 1.1:\n"
            + "  foo();\n"
            + "  break;\n"
            + "case 2:\n"
            + "  bar();\n"
            + "  break;\n"
            + "}",
        "");
    fold(
        "switch (0) {\n"
            + "case NaN:\n"
            + "  foobar();\n"
            + "  break;\n"
            + "case -0.0:\n"
            + "  foo();\n"
            + "  break;\n"
            + "case 2:\n"
            + "  bar();\n"
            + "  break;\n"
            + "}",
        "foo();");
    foldSame("switch ('\\v') {\n" + "case '\\u000B':\n" + "  foo();\n" + "}");
    fold(lines("switch ('empty') {", "case 'empty':", "case 'foo':", "  foo();", "}"), "foo()");

    fold(
        lines(
            "let x;", //
            "switch (use(x)) {",
            "  default: {let y;}",
            "}"),
        lines(
            "let x;", //
            "use(x);", "let y;"));

    fold(
        lines(
            "let x;", //
            "switch (use?.(x)) {",
            "  default: {let y;}",
            "}"),
        lines(
            "let x;", //
            "use?.(x);",
            "let y;"));

    fold(
        lines(
            "let x;", //
            "switch (use(x)) {",
            "  default: let y;",
            "}"),
        lines(
            "let x;", //
            "use(x);", //
            "let y;"));
  }

  @Test
  public void testOptimizeSwitchBug11536863() {
    fold(
        "outer: {"
            + "  switch (2) {\n"
            + "    case 2:\n"
            + "      f();\n"
            + "      break outer;\n"
            + "  }"
            + "}",
        "outer: {f(); break outer;}");
  }

  // `a[b]` could trigger a getter or setter, and have side effects. However, we always assume it
  // does not (even though it's unsound) because the code size cost of assuming all GETELEM nodes
  // have side effects is unacceptable.
  @Test
  public void testUnusedGetElemRemoved() {
    fold("a[b]", "");
    fold("a?.[b]", "");
  }

  @Test
  public void testOptimizeSwitch2() {
    fold(
        "outer: switch (2) {\n" + "  case 2:\n" + "    f();\n" + "    break outer;\n" + "}",
        "outer: {f(); break outer;}");
  }

  @Test
  public void testOptimizeSwitch3() {
    fold(
        lines(
            "switch (1) {",
            "  case 1:",
            "  case 2:",
            "  case 3: {",
            "    break;",
            "  }",
            "  case 4:",
            "  case 5:",
            "  case 6:",
            "  default:",
            "    fail('Should not get here');",
            "    break;",
            "}"),
        "");
  }

  @Test
  public void testOptimizeSwitchWithLabellessBreak() {
    fold(
        lines(
            "function f() {",
            "  switch('x') {",
            "    case 'x': var x = 1; break;",
            "    case 'y': break;",
            "  }",
            "}"),
        "function f() { var x = 1; }");

    // TODO(moz): Convert this to an if statement for better optimization
    foldSame(
        lines(
            "function f() {",
            "  switch(x) {",
            "    case 'y': break;",
            "    default: var x = 1;",
            "  }",
            "}"));

    fold(
        lines(
            "var exit;",
            "switch ('a') {",
            "  case 'a':",
            "    break;",
            "  default:",
            "    exit = 21;",
            "    break;",
            "}",
            "switch(exit) {",
            "  case 21: throw 'x';",
            "  default : console.log('good');",
            "}"),
        lines(
            "var exit;",
            "switch(exit) {",
            "  case 21: throw 'x';",
            "  default : console.log('good');",
            "}"));

    fold(
        lines(
            "let x = 1;", //
            "switch('x') {",
            "  case 'x': let x = 2; break;",
            "}"),
        lines(
            "let x = 1;", //
            "let x$jscomp$1 = 2"));
  }

  @Test
  public void testOptimizeSwitchWithLabelledBreak() {
    fold(
        lines(
            "function f() {",
            "  label:",
            "  switch('x') {",
            "    case 'x': break label;",
            "    case 'y': throw f;",
            "  }",
            "}"),
        "function f() { }");

    fold(
        lines(
            "function f() {",
            "  label:",
            "  switch('x') {",
            "    case 'x': break label;",
            "    default: throw f;",
            "  }",
            "}"),
        "function f() { }");
  }

  @Test
  public void testOptimizeSwitchWithReturn() {
    fold(
        lines(
            "function f() {",
            "  switch('x') {",
            "    case 'x': return 1;",
            "    case 'y': return 2;",
            "  }",
            "}"),
        "function f() { return 1; }");

    fold(
        lines(
            "function f() {",
            "  let x = 1;",
            "  switch('x') {",
            "    case 'x': { let x = 2; } return 3;",
            "    case 'y': return 4;",
            "  }",
            "}"),
        lines(
            "function f() {", //
            "  let x = 1;",
            "  let x$jscomp$1 = 2;",
            "  return 3; ",
            "}"));
  }

  @Test
  public void testOptimizeSwitchWithThrow() {
    fold(
        lines(
            "function f() {",
            "  switch('x') {",
            "    case 'x': throw f;",
            "    case 'y': throw f;",
            "  }",
            "}"),
        "function f() { throw f; }");
  }

  @Test
  public void testOptimizeSwitchWithContinue() {
    fold(
        lines(
            "function f() {",
            "  for (;;) {",
            "    switch('x') {",
            "      case 'x': continue;",
            "      case 'y': continue;",
            "    }",
            "  }",
            "}"),
        "function f() { for (;;) { continue; } }");
  }

  @Test
  public void testOptimizeSwitchWithDefaultCaseWithFallthru() {
    foldSame(
        lines(
            "function f() {",
            "  switch(a) {",
            "    case 'x':",
            "    case foo():",
            "    default: return 3",
            "  }",
            "}"));
  }

  // GitHub issue #1722: https://github.com/google/closure-compiler/issues/1722
  @Test
  public void testOptimizeSwitchWithDefaultCase() {
    fold(
        lines(
            "function f() {",
            "  switch('x') {",
            "    case 'x': return 1;",
            "    case 'y': return 2;",
            "    default: return 3",
            " }",
            "}"),
        "function f() { return 1; }");

    fold(
        lines(
            "switch ('hasDefaultCase') {",
            "  case 'foo':",
            "    foo();",
            "    break;",
            "  default:",
            "    bar();",
            "    break;",
            "}"),
        "bar();");

    foldSame("switch (x) { default: if (a) { break; } bar(); }");

    // Potentially foldable
    foldSame(
        lines(
            "switch (x) {",
            "  case x:",
            "    foo();",
            "    break;",
            "  default:",
            "    if (a) { break; }",
            "    bar();",
            "}"));

    fold(
        lines(
            "switch ('hasDefaultCase') {",
            "  case 'foo':",
            "    foo();",
            "    break;",
            "  default:",
            "    if (true) { break; }",
            "    bar();",
            "}"),
        "");

    fold(
        lines(
            "switch ('hasDefaultCase') {",
            "  case 'foo':",
            "    foo();",
            "    break;",
            "  default:",
            "    if (a) { break; }",
            "    bar();",
            "}"),
        "switch ('hasDefaultCase') { default: if (a) { break; } bar(); }");

    fold(
        lines(
            "l: switch ('hasDefaultCase') {",
            "  case 'foo':",
            "    foo();",
            "    break;",
            "  default:",
            "    if (a) { break l; }",
            "    bar();",
            "    break;",
            "}"),
        "l:{ if (a) { break l; } bar(); }");

    fold(
        lines(
            "switch ('hasDefaultCase') {",
            "  case 'foo':",
            "    bar();",
            "    break;",
            "  default:",
            "    foo();",
            "    break;",
            "}"),
        "foo();");

    fold("switch (a()) { default: bar(); break;}", "a(); bar();");

    fold("switch (a?.()) { default: bar(); break;}", "a?.(); bar();");

    fold("switch (a()) { default: break; bar();}", "a();");

    fold(
        lines(
            "loop: ",
            "for (;;) {",
            "  switch (a()) {",
            "    default:",
            "      bar();",
            "      break loop;",
            "  }",
            "}"),
        "loop: for (;;) { a(); bar(); break loop; }");
  }

  @Test
  public void testRemoveNumber() {
    fold("3", "");
  }

  @Test
  public void testRemoveVarGet1() {
    fold("a", "");
  }

  @Test
  public void testRemoveVarGet2() {
    fold("var a = 1;a", "var a = 1");
  }

  @Test
  public void testRemoveUnusedGetProp() {
    fold("var a = {};a.b", "var a = {}");
  }

  @Test
  public void testRemoveUnusedOptChainGetProp() {
    fold("var a = {};a?.b", "var a = {}");
  }

  @Test
  public void testRemoveUnusedGetProp2() {
    fold("var a = {};a.b=1;a.b", "var a = {};a.b=1");
  }

  @Test
  public void testRemoveUnusedOptChainGetProp2() {
    fold("var a = {};a.b=1;a?.b", "var a = {};a.b=1");
  }

  @Test
  public void testRemovePrototypeGet1() {
    fold("var a = {};a.prototype.b", "var a = {}");
  }

  @Test
  public void testRemoveOptChainPrototypeGet1() {
    fold("var a = {};a?.prototype.b", "var a = {}");
  }

  @Test
  public void testRemovePrototypeGet2() {
    fold("var a = {};a.prototype.b = 1;a.prototype.b", "var a = {};a.prototype.b = 1");
  }

  @Test
  public void testRemoveOptChainPrototypeGet2() {
    fold("var a = {};a.prototype.b = 1;a?.prototype.b", "var a = {};a.prototype.b = 1");
  }

  @Test
  public void testNotRemovePrototypeGet2() {
    foldSame("var a = {};a.prototype.b = 1; let x = a.prototype.b");
  }

  @Test
  public void testNotRemoveOptChainPrototypeGet2() {
    foldSame("var a = {};a.prototype.b = 1; let x = a?.prototype.b");
  }

  @Test
  public void testRemoveAdd1() {
    fold("1 + 2", "");
  }

  @Test
  public void testNoRemoveVar1() {
    foldSame("var a = 1");
  }

  @Test
  public void testNoRemoveVar2() {
    foldSame("var a = 1, b = 2");
  }

  @Test
  public void testNoRemoveAssign1() {
    foldSame("a = 1");
  }

  @Test
  public void testNoRemoveAssign2() {
    foldSame("a = b = 1");
  }

  @Test
  public void testNoRemoveAssign3() {
    fold("1 + (a = 2)", "a = 2");
  }

  @Test
  public void testNoRemoveAssign4() {
    foldSame("x.a = 1");
  }

  @Test
  public void testNoRemoveAssign5() {
    foldSame("x.a = x.b = 1");
  }

  @Test
  public void testNoRemoveAssign6() {
    fold("1 + (x.a = 2)", "x.a = 2");
  }

  @Test
  public void testNoRemoveCall1() {
    foldSame("a()");
  }

  @Test
  public void testNoRemoveOptChainCall1() {
    foldSame("a?.()");
  }

  @Test
  public void testNoRemoveCall2() {
    fold("a()+b()", "a(),b()");
  }

  @Test
  public void testNoRemoveOptChainCall2() {
    fold("a?.()+b?.()", "a?.(),b?.()");
  }

  @Test
  public void testNoRemoveCall3() {
    foldSame("a() && b()");
  }

  @Test
  public void testNoRemoveOptChainCall3() {
    foldSame("a?.() && b?.()");
  }

  @Test
  public void testNoRemoveCall4() {
    foldSame("a() || b()");
  }

  @Test
  public void testNoRemoveOptChainCall4() {
    foldSame("a?.() || b?.()");
  }

  @Test
  public void testNoRemoveCall4NullishCoalesce() {
    foldSame("a() ?? b()");
  }

  @Test
  public void testNoRemoveOptChainCall4NullishCoalesce() {
    foldSame("a?.() ?? b?.()");
  }

  @Test
  public void testNoRemoveCall5NullishCoalesce() {
    fold("a() ?? 1", "a()");
  }

  @Test
  public void testNoRemoveOptChainCall5NullishCoalesce() {
    fold("a?.() ?? 1", "a?.()");
  }

  @Test
  public void testNoRemoveCall6NullishCoalesce() {
    foldSame("1 ?? a()");
  }

  @Test
  public void testNoRemoveCall5() {
    fold("a() || 1", "a()");
  }

  @Test
  public void testNoRemoveCall6() {
    foldSame("1 || a()");
  }

  @Test
  public void testNoRemoveThrow1() {
    foldSame("function f(){throw a()}");
  }

  @Test
  public void testNoRemoveThrow2() {
    foldSame("function f(){throw a}");
  }

  @Test
  public void testNoRemoveThrow3() {
    foldSame("function f(){throw 10}");
  }

  @Test
  public void testRedundantIfRemoved() {
    fold("if(x()) 1", "x()");
  }

  @Test
  public void testRemoveInControlStructure3() {
    fold("for(1;2;3) 4", "for(;;);");
  }

  @Test
  public void testHook1() {
    fold("1 ? 2 : 3", "");
  }

  @Test
  public void testHook2() {
    fold("x ? a() : 3", "x && a()");
  }

  @Test
  public void testHook3() {
    fold("x ? 2 : a()", "x || a()");
  }

  @Test
  public void testHook4() {
    foldSame("x ? a() : b()");
  }

  @Test
  public void testHook5() {
    fold("a() ? 1 : 2", "a()");
  }

  @Test
  public void testHook6() {
    fold("a() ? b() : 2", "a() && b()");
  }

  // TODO(johnlenz): Consider adding a post optimization pass to
  // convert OR into HOOK to save parentheses when the operator
  // precedents would require them.
  @Test
  public void testHook7() {
    fold("a() ? 1 : b()", "a() || b()");
  }

  @Test
  public void testHook8() {
    foldSame("a() ? b() : c()");
  }

  @Test
  public void testHook9() {
    fold("true ? a() : (function f() {})()", "a()");
    fold("false ? a() : (function f() {alert(x)})()", "(function f() {alert(x)})()");
  }

  @Test
  public void testHook10() {
    fold("((function () {}), true) ? a() : b()", "a()");
    fold("((function () {alert(x)})(), true) ? a() : b()", "(function(){alert(x)})(),a()");
  }

  @Test
  public void testShortCircuit1() {
    foldSame("1 && a()");
  }

  @Test
  public void testShortCircuit2NullishCoalesce() {
    fold("1 ?? a() ?? 2", "1 ?? a()");
  }

  @Test
  public void testShortCircuit3NullishCoalesce() {
    fold("a() ?? 1 ?? 2", "a()");
  }

  @Test
  public void testShortCircuit4NullishCoalesce() {
    foldSame("a() ?? 1 ?? b()");
  }

  @Test
  public void testShortCircuit2() {
    fold("1 && a() && 2", "1 && a()");
  }

  @Test
  public void testShortCircuit3() {
    fold("a() && 1 && 2", "a()");
  }

  @Test
  public void testShortCircuit4() {
    foldSame("a() && 1 && b()");
  }

  @Test
  public void testComplex1() {
    fold("1 && a() + b() + c()", "1 && (a(), b(), c())");
  }

  @Test
  public void testComplex2() {
    fold("1 && (a() ? b() : 1)", "1 && (a() && b())");
  }

  @Test
  public void testComplex3() {
    fold("1 && (a() ? b() : 1 + c())", "1 && (a() ? b() : c())");
  }

  @Test
  public void testComplex4() {
    fold("1 && (a() ? 1 : 1 + c())", "1 && (a() || c())");
  }

  @Test
  public void testComplex5() {
    // can't simplify LHS of short circuit statements with side effects
    foldSame("(a() ? 1 : 1 + c()) && foo()");
  }

  @Test
  public void testNoRemoveFunctionDeclaration1() {
    foldSame("function foo(){}");
  }

  @Test
  public void testNoRemoveFunctionDeclaration2() {
    foldSame("var foo = function (){}");
  }

  @Test
  public void testNoSimplifyFunctionArgs1() {
    foldSame("f(1 + 2, 3 + g())");
  }

  @Test
  public void testNoSimplifyFunctionArgs2() {
    foldSame("1 && f(1 + 2, 3 + g())");
  }

  @Test
  public void testNoSimplifyFunctionArgs3() {
    foldSame("1 && foo(a() ? b() : 1 + c())");
  }

  @Test
  public void testNoRemoveInherits1() {
    foldSame("var a = {}; this.b = {}; var goog = {}; goog.inherits(b, a)");
  }

  @Test
  public void testNoRemoveInherits2() {
    fold(
        "var a = {}; this.b = {}; var goog = {}; goog.inherits(b, a) + 1",
        "var a = {}; this.b = {}; var goog = {}; goog.inherits(b, a)");
  }

  @Test
  public void testNoRemoveInherits3() {
    foldSame("this.a = {}; var b = {}; b.inherits(a);");
  }

  @Test
  public void testNoRemoveInherits4() {
    fold("this.a = {}; var b = {}; b.inherits(a) + 1;", "this.a = {}; var b = {}; b.inherits(a)");
  }

  @Test
  public void testRemoveFromLabel1() {
    fold("LBL: void 0", "");
  }

  @Test
  public void testRemoveFromLabel2() {
    fold("LBL: foo() + 1 + bar()", "LBL: foo(),bar()");
  }

  @Test
  public void testCall() {
    foldSame("foo(0)");
    // We use a function with no side-effects, otherwise the entire invocation would be preserved.
    fold("Math.sin(0);", "");
    fold("1 + Math.sin(0);", "");
  }

  @Test
  public void testCall_containingSpread() {
    // We use a function with no side-effects, otherwise the entire invocation would be preserved.
    fold("Math.sin(...c)", "([...c])");
    fold("Math.sin(4, ...c, a)", "([...c])");
    fold("Math.sin(foo(), ...c, bar())", "(foo(), [...c], bar())");
    fold("Math.sin(...a, b, ...c)", "([...a], [...c])");
    fold("Math.sin(...b, ...c)", "([...b], [...c])");
  }

  @Test
  public void testOptChainCall_containingSpread() {
    // We use a function with no side-effects, otherwise the entire invocation would be preserved.
    fold("Math?.sin(...c)", "([...c])");
    fold("Math?.sin(4, ...c, a)", "([...c])");
    fold("Math?.sin(foo(), ...c, bar())", "(foo(), [...c], bar())");
    fold("Math?.sin(...a, b, ...c)", "([...a], [...c])");
    fold("Math?.sin(...b, ...c)", "([...b], [...c])");
  }

  @Test
  public void testNew() {
    foldSame("new foo(0)");
    // We use a function with no side-effects, otherwise the entire invocation would be preserved.
    fold("new Date;", "");
    fold("1 + new Date;", "");
  }

  @Test
  public void testNew_containingSpread() {
    // We use a function with no side-effects, otherwise the entire invocation would be preserved.
    fold("new Date(...c)", "([...c])");
    fold("new Date(4, ...c, a)", "([...c])");
    fold("new Date(foo(), ...c, bar())", "(foo(), [...c], bar())");
    fold("new Date(...a, b, ...c)", "([...a], [...c])");
    fold("new Date(...b, ...c)", "([...b], [...c])");
  }

  @Test
  public void testTaggedTemplateLit_simpleTemplate() {
    foldSame("foo`Simple`");
    // We use a function with no side-effects, otherwise the entire invocation would be preserved.
    fold("Math.sin`Simple`", "");
    fold("1 + Math.sin`Simple`", "");
  }

  @Test
  public void testTaggedTemplateLit_substitutingTemplate() {
    foldSame("foo`Complex ${butSafe}`");
    // We use a function with no side-effects, otherwise the entire invocation would be preserved.
    fold("Math.sin`Complex ${butSafe}`", "");
    fold("Math.sin`Complex ${andDangerous()}`", "andDangerous()");
  }

  @Test
  public void testFoldAssign() {
    fold("x=x", "");
    foldSame("x=xy");
    foldSame("x=x + 1");
    foldSame("x.a=x.a");
    fold("var y=(x=x)", "var y=x");
    fold("y=1 + (x=x)", "y=1 + x");
  }

  @Test
  public void testTryCatchFinally() {
    foldSame("try {foo()} catch (e) {bar()}");
    foldSame("try { try {foo()} catch (e) {bar()}} catch (x) {bar()}");
    fold("try {var x = 1} finally {}", "var x = 1;");
    foldSame("try {var x = 1} finally {x()}");
    fold("function f() { return; try{var x = 1}finally{} }", "function f() { return; var x = 1; }");
    fold("try {} finally {x()}", "x()");
    fold("try {} catch (e) { bar()} finally {x()}", "x()");
    fold("try {} catch (e) { bar()}", "");
    fold("try {} catch (e) { var a = 0; } finally {x()}", "var a; x()");
    fold("try {} catch (e) {}", "");
    fold("try {} finally {}", "");
    fold("try {} catch (e) {} finally {}", "");
    fold("L1:try {} catch (e) {} finally {}", "");
    fold("L2:L1:try {} catch (e) {} finally {}", "");
  }

  @Test
  public void testObjectLiteral() {
    fold("({})", "");
    fold("({a:1})", "");
    fold("({a:foo()})", "foo()");
    fold("({'a':foo()})", "foo()");
    // Object-spread may tigger getters.
    foldSame("({...a})");
    foldSame("({...foo()})");
  }

  @Test
  public void testArrayLiteral() {
    fold("([])", "");
    fold("([1])", "");
    fold("([a])", "");
    fold("([foo()])", "foo()");
  }

  @Test
  public void testArrayLiteral_containingSpread() {
    foldSame("([...c])");
    fold("([4, ...c, a])", "([...c])");
    fold("([foo(), ...c, bar()])", "(foo(), [...c], bar())");
    fold("([...a, b, ...c])", "([...a], [...c])");
    foldSame("([...b, ...c])"); // It would also be fine if the spreads were split apart.
  }

  @Test
  public void testAwait() {
    foldSame("async function f() { await something(); }");
    foldSame("async function f() { await some.thing(); }");
  }

  @Test
  public void testEmptyPatternInDeclarationRemoved() {
    fold("var [] = [];", "");
    fold("let [] = [];", "");
    fold("const [] = [];", "");
    fold("var {} = [];", "");
    fold("var [] = foo();", "foo()");
  }

  @Test
  public void testEmptyArrayPatternInAssignRemoved() {
    fold("({} = {});", "");
    fold("({} = foo());", "foo()");
    fold("[] = [];", "");
    fold("[] = foo();", "foo()");
  }

  @Test
  public void testEmptyPatternInParamsNotRemoved() {
    foldSame("function f([], a) {}");
    foldSame("function f({}, a) {}");
  }

  @Test
  public void testEmptyPatternInForOfLoopNotRemoved() {
    foldSame("for (let [] of foo()) {}");
    foldSame("for (const [] of foo()) {}");
    foldSame("for ([] of foo()) {}");
    foldSame("for ({} of foo()) {}");
  }

  @Test
  public void testEmptySlotInArrayPatternRemoved() {
    fold("[,,] = foo();", "foo()");
    fold("[a,b,,] = foo();", "[a,b] = foo();");
    fold("[a,[],b,[],[]] = foo();", "[a,[],b] = foo();");
    fold("[a,{},b,{},{}] = foo();", "[a,{},b] = foo();");
    fold("function f([,,,]) {}", "function f([]) {}");
    foldSame("[[], [], [], ...rest] = foo()");
  }

  @Test
  public void testEmptySlotInArrayPatternWithDefaultValueMaybeRemoved() {
    fold("[a,[] = 0] = [];", "[a] = [];");
    foldSame("[a,[] = foo()] = [];");
  }

  @Test
  public void testEmptyKeyInObjectPatternRemoved() {
    fold("const {f: {}} = {};", "");
    fold("const {f: []} = {};", "");
    fold("const {f: {}, g} = {};", "const {g} = {};");
    fold("const {f: [], g} = {};", "const {g} = {};");
    foldSame("const {[foo()]: {}} = {};");
  }

  @Test
  public void testEmptyKeyInObjectPatternWithDefaultValueMaybeRemoved() {
    fold("const {f: {} = 0} = {};", "");
    // In theory the following case could be reduced to `foo()`, but that gets more complicated to
    // implement for object patterns with multiple keys with side effects.
    // Instead the pass backs off for any default with a possible side effect
    foldSame("const {f: {} = foo()} = {};");
  }

  @Test
  public void testEmptyKeyInObjectPatternNotRemovedWithObjectRest() {
    foldSame("const {f: {}, ...g} = foo()");
    foldSame("const {f: [], ...g} = foo()");
  }

  @Test
  public void testUndefinedDefaultParameterRemoved() {
    fold(
        "function f(x=undefined,y) {  }", //
        "function f(x,y)             {  }");
    fold(
        "function f(x,y=undefined,z) {  }", //
        "function f(x,y          ,z) {  }");
    fold(
        "function f(x=undefined,y=undefined,z=undefined) {  }", //
        "function f(x,          y,          z)           {  }");
  }

  @Test
  public void testPureVoidDefaultParameterRemoved() {
    fold(
        "function f(x = void 0) {  }", //
        "function f(x         ) {  }");
    fold(
        "function f(x = void \"XD\") {  }", //
        "function f(x              ) {  }");
    fold(
        "function f(x = void f()) {  }", //
        "function f(x)            {  }");
  }

  @Test
  public void testNoDefaultParameterNotRemoved() {
    foldSame("function f(x,y) {  }");
    foldSame("function f(x) {  }");
    foldSame("function f() {  }");
  }

  @Test
  public void testEffectfulDefaultParameterNotRemoved() {
    foldSame("function f(x = void console.log(1)) {  }");
    foldSame("function f(x = void f()) { alert(x); }");
  }

  @Test
  public void testDestructuringUndefinedDefaultParameter() {
    fold(
        "function f({a=undefined,b=1,c}) {  }", //
        "function f({a          ,b=1,c}) {  }");
    fold(
        "function f({a={},b=0}=undefined) {  }", //
        "function f({a={},b=0}) {  }");
    fold(
        "function f({a=undefined,b=0}) {  }", //
        "function f({a,b=0}) {  }");
    fold(
        " function f({a: {b = undefined}}) {  }", //
        " function f({a: {b}}) {  }");
    foldSame("function f({a,b}) {  }");
    foldSame("function f({a=0, b=1}) {  }");
    foldSame("function f({a=0,b=0}={}) {  }");
    foldSame("function f({a={},b=0}={}) {  }");
  }

  @Test
  public void testUndefinedDefaultObjectPatterns() {
    fold(
        "const {a = undefined} = obj;", //
        "const {a} = obj;");
    fold(
        "const {a = void 0} = obj;", //
        "const {a} = obj;");
  }

  @Test
  public void testDoNotRemoveGetterOnlyAccess() {
    foldSame(
        lines(
            "var a = {", //
            "  get property() {}",
            "};",
            "a.property;"));

    foldSame(
        lines(
            "var a = {", //
            "  get property() {}",
            "};",
            "a?.property;"));

    foldSame(
        lines(
            "var a = {};", //
            "Object.defineProperty(a, 'property', {",
            "  get() {}",
            "});",
            "a.property;"));

    foldSame(
        lines(
            "var a = {};", //
            "Object.defineProperty(a, 'property', {",
            "  get() {}",
            "});",
            "a?.property;"));
  }

  @Test
  public void testDoNotRemoveNestedGetterOnlyAccess() {
    foldSame(
        lines(
            "var a = {", //
            "  b: { get property() {} }",
            "};",
            "a.b.property;"));
  }

  @Test
  public void testRemoveAfterNestedGetterOnlyAccess() {
    fold(
        lines(
            "var a = {", //
            "  b: { get property() {} }",
            "};",
            "a.b.property.d.e;"),
        lines(
            "var a = {", //
            "  b: { get property() {} }",
            "};",
            "a.b.property;"));
  }

  @Test
  public void testRetainSetterOnlyAccess() {
    foldSame(
        lines(
            "var a = {", //
            "  set property(v) {}",
            "};",
            "a.property;"));

    foldSame(
        lines(
            "var a = {", //
            "  set property(v) {}",
            "};",
            "a?.property;"));
  }

  @Test
  public void testDoNotRemoveGetterSetterAccess() {
    foldSame(
        lines(
            "var a = {", //
            "  get property() {},",
            "  set property(x) {}",
            "};",
            "a.property;"));
  }

  @Test
  public void testDoNotRemoveSetSetterToGetter() {
    foldSame(
        lines(
            "var a = {", //
            "  get property() {},",
            "  set property(x) {}",
            "};",
            "a.property = a.property;"));
  }

  @Test
  public void testDoNotRemoveAccessIfOtherPropertyIsGetter() {
    foldSame(
        lines(
            "var a = {", //
            "  get property() {}",
            "};",
            "var b = {",
            "  property: 0,",
            "};",
            // This pass should be conservative and not remove this since it sees a getter for
            // "property"
            "b.property;"));

    foldSame(
        lines(
            "var a = {};", //
            "Object.defineProperty(a, 'property', {",
            "  get() {}",
            "});",
            "var b = {",
            "  property: 0,",
            "};",
            "b.property;"));
  }

  @Test
  public void testFunctionCallReferencesGetterIsNotRemoved() {
    foldSame(
        lines(
            "var a = {", //
            "  get property() {}",
            "};",
            "function foo() { a.property; }",
            "foo();"));
  }

  @Test
  public void testFunctionCallReferencesSetterIsNotRemoved() {
    foldSame(
        lines(
            "var a = {", //
            "  set property(v) {}",
            "};",
            "function foo() { a.property = 0; }",
            "foo();"));
  }

  @Test
  public void testClassField() {
    fold(
        lines(
            "class C {", //
            "  f1 = (5,2);",
            "}"),
        lines(
            "class C {", //
            "  f1 = 2;",
            "}"));
  }

  @Test
  public void testThis() {
    fold(
        lines(
            "class C {", //
            "  constructor() {",
            "    this.f1 = (5,2);",
            "  }",
            "}"),
        lines(
            "class C {", //
            "  constructor() {",
            "    this.f1 = 2;",
            "  }",
            "}"));
  }

  @Test
  public void testClassStaticBlock() {
    fold(
        lines(
            "class C {", //
            "  static {",
            "  }",
            "}"),
        lines(
            "class C {", //
            "}"));

    foldSame(
        lines(
            "class C {", //
            "  static {",
            "    this.x = 0;",
            "  }",
            "}"));
  }

  @Test
  public void testRemoveUnreachableOptionalChainingCall() {
    fold("(null)?.();", "");
    fold("(void 0)?.();", "");
    fold("(undefined)?.();", "");
    fold("(void 0)?.(0)", "");
    fold("(void 0)?.(function f() {})", "");
    // arguments with unknown side effects are also removed
    fold("(void 0)?.(f(), g())", "");

    // void arguments with unknown side effects are preserved
    fold("(void f())?.();", "f();");
    fold("g((void f())?.());", "g(void f());");

    foldSame("(f(), null)?.()");
    foldSame("f?.()");
  }
}
