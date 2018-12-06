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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
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
      "/** @const */ var Math = {};" +
      "/** @nosideeffects */ Math.random = function(){};" +
      "/** @nosideeffects */ Math.sin = function(){};";

  public PeepholeRemoveDeadCodeTest() {
    super(MATH);
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        DefinitionUseSiteFinder definitionFinder = new DefinitionUseSiteFinder(compiler);
        definitionFinder.process(externs, root);
        new PureFunctionIdentifier(compiler, definitionFinder).process(externs, root);
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
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
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

    // Block with declarations
    foldSame("{let x}");
    foldSame("function f() {let x}");
    foldSame("{const x = 1}");
    foldSame("{x = 2; y = 4; let z;}");
    fold("{'hi'; let x;}", "{let x}");
    fold("{x = 4; {let y}}", "x = 4; {let y}");
    foldSame("{function f() {} } {function f() {}}");
    foldSame("{class C {}} {class C {}}");
    foldSame("{label: let x}");
    fold("{label: var x}", "label: var x");
    foldSame("{label: var x; let y;}");
  }

  /** Try to remove spurious blocks with multiple children */
  @Test
  public void testFoldBlocksWithManyChildren() {
    fold("function f() { if (false) {} }", "function f(){}");
    fold("function f() { { if (false) {} if (true) {} {} } }",
         "function f(){}");
    fold("{var x; var y; var z; function f() { { var a; { var b; } } } }",
         "{var x;var y;var z;function f(){var a;var b} }");
    fold("{var x; var y; var z; { { var a; { var b; } } } }",
        "var x;var y;var z; var a;var b");
  }

  @Test
  public void testIf() {
    fold("if (1){ x=1; } else { x = 2;}", "x=1");
    fold("if (1) {} else { function foo(){} }", "");
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

  @Test
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
    fold("b=1;if(foo,b)x=b;","b=1;x=b;");
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
  public void testVarLifting() {
    fold("if(true)var a", "var a");
    fold("if(false)var a", "var a");

    // More var lifting tests in PeepholeIntegrationTests
  }

  @Test
  public void testLetConstLifting() {
    fold("if(true) {const x = 1}", "{const x = 1}");
    fold("if(false) {const x = 1}", "");
    fold("if(true) {let x}", "{let x}");
    fold("if(false) {let x}", "");
  }

  @Test
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
    fold("do { while (1) {foo(); continue;} } while(0)", "while (1) {foo(); continue;}");
    foldSame("l1: do { while (1) { foo() } } while(0)");
    fold("do { switch (1) { default: foo(); break} } while(0)", "foo();");
    fold("do { switch (1) { default: foo(); continue} } while(0)",
        "do { foo(); continue } while(0)");

    fold("l1: { do { x = 1; break l1; } while (0); x = 2; }", "l1: { x = 1; break l1; x = 2;}");

    fold("do { x = 1; } while (x = 0);", "x = 1; x = 0;");
    fold("let x = 1; (function() { do { let x = 2; } while (x = 10, false); })();",
        "let x = 1; (function() { { let x = 2 } x = 10 })();");
  }

  @Test
  public void testFoldEmptyDo() {
    fold("do { } while(true);", "for (;;);");
  }

  @Test
  public void testMinimizeWhileConstantCondition() {
    foldSame("while(true) foo()");
    fold("while(0) foo()", "");
    fold("while(0.0) foo()", "");
    fold("while(NaN) foo()", "");
    fold("while(null) foo()", "");
    fold("while(undefined) foo()", "");
    fold("while('') foo()", "");
  }

  @Test
  public void testFoldConstantCommaExpressions() {
    fold("if (true, false) {foo()}", "");
    fold("if (false, true) {foo()}", "foo()");
    fold("true, foo()", "foo()");
    fold("(1 + 2 + ''), foo()", "foo()");
  }

  @Test
  public void testRemoveUselessOps1() {
    foldSame("(function () { f(); })();");
  }

  @Test
  public void testRemoveUselessOps2() {
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
    fold("(function* f() {})", "");
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
    fold("switch(a){default: var b; break; case 1: var c; break;}",
        "var c; var b;");
    fold("var x=1; switch(x) { case 1: var y; }",
        "var y; var x=1;");

    // Can't remove cases if a default exists and is not the last case.
    foldSame("function f() {switch(a){default: return; case 1: break;}}");
    foldSame("function f() {switch(1){default: return; case 1: break;}}"); // foldable
    foldSame("function f() {switch(a){case 1: foo();}}");
    foldSame("function f() {switch(a){case 3: case 2: case 1: foo();}}");

    fold("function f() {switch(a){case 2: case 1: default: foo();}}", "function f() { foo(); }");
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
        "var x; foo();");

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
        "foo();");
    fold("switch ('noMatch') {\n" +
        "case 'foo':\n" +
        "  foo();\n" +
        "  break;\n" +
        "case 'bar':\n" +
        "  bar();\n" +
        "  break;\n" +
        "}",
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
        lines(
            "foo();",
            "bar();"));
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
    fold("switch ('repeated') {\n" +
        "case 'repeated':\n" +
        "  foo();\n" +
        "  break;\n" +
        "case 'repeated':\n" +
        "  bar();\n" +
        "  break;\n" +
        "}",
        "foo();");
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
        "foo();");
    fold("switch (1) {\n" +
        "case 1.1:\n" +
        "  foo();\n" +
        "  break;\n" +
        "case 2:\n" +
        "  bar();\n" +
        "  break;\n" +
        "}",
        "");
    fold("switch (0) {\n" +
        "case NaN:\n" +
        "  foobar();\n" +
        "  break;\n" +
        "case -0.0:\n" +
        "  foo();\n" +
        "  break;\n" +
        "case 2:\n" +
        "  bar();\n" +
        "  break;\n" +
        "}",
        "foo();");
    foldSame("switch ('\\v') {\n" +
        "case '\\u000B':\n" +
        "  foo();\n" +
        "}");
    fold(
        lines(
            "switch ('empty') {",
            "case 'empty':",
            "case 'foo':",
            "  foo();",
            "}"),
        "foo()");

    fold(
        lines(
            "let x;",
            "switch (use(x)) {",
            "  default: {let x;}",
            "}"),
        lines(
            "let x;",
            "use(x);",
            "{let x}"));

    fold(
        lines(
            "let x;",
            "switch (use(x)) {",
            "  default: let x;",
            "}"),
        lines(
            "let x;",
            "use(x);",
            "{let x}"));
  }

  @Test
  public void testOptimizeSwitchBug11536863() {
    fold(
        "outer: {" +
        "  switch (2) {\n" +
        "    case 2:\n" +
        "      f();\n" +
        "      break outer;\n" +
        "  }" +
        "}",
        "outer: {f(); break outer;}");
  }

  @Test
  public void testOptimizeSwitch2() {
    fold(
        "outer: switch (2) {\n" +
        "  case 2:\n" +
        "    f();\n" +
        "    break outer;\n" +
        "}",
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
    test(
        lines(
            "function f() {",
            "  switch('x') {",
            "    case 'x': var x = 1; break;",
            "    case 'y': break;",
            "  }",
            "}"),
        "function f() { var x = 1; }");

    // TODO(moz): Convert this to an if statement for better optimization
    testSame(
        lines(
            "function f() {",
            "  switch(x) {",
            "    case 'y': break;",
            "    default: var x = 1;",
            "  }",
            "}"));

    test(
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

    test(
        lines(
            "let x = 1;",
            "switch('x') {",
            "  case 'x': let x = 2; break;",
            "}"
        ),
        lines(
            "let x = 1;",
            "{let x = 2}"
        ));
  }

  @Test
  public void testOptimizeSwitchWithLabelledBreak() {
    test(
        lines(
            "function f() {",
            "  label:",
            "  switch('x') {",
            "    case 'x': break label;",
            "    case 'y': throw f;",
            "  }",
            "}"),
        "function f() { }");

    test(
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
    test(
        lines(
            "function f() {",
            "  switch('x') {",
            "    case 'x': return 1;",
            "    case 'y': return 2;",
            "  }",
            "}"),
        "function f() { return 1; }");

    test(
        lines(
            "function f() {",
            "  let x = 1;",
            "  switch('x') {",
            "    case 'x': { let x = 2; } return 3;",
            "    case 'y': return 4;",
            "  }",
            "}"),
        lines(
            "function f() {",
            "  let x = 1;",
            "  { let x = 2; } return 3; ",
            "}"));
  }

  @Test
  public void testOptimizeSwitchWithThrow() {
    test(
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
    test(
        lines(
            "function f() {",
            "  while (true) {",
            "    switch('x') {",
            "      case 'x': continue;",
            "      case 'y': continue;",
            "    }",
            "  }",
            "}"),
        "function f() { while (true) { continue; } }");
  }

  // GitHub issue #1722: https://github.com/google/closure-compiler/issues/1722
  @Test
  public void testOptimizeSwitchWithDefaultCase() {
    test(
        lines(
            "function f() {",
            "  switch('x') {",
            "    case 'x': return 1;",
            "    case 'y': return 2;",
            "    default: return 3",
            " }",
            "}"),
        "function f() { return 1; }");

    test(
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

    testSame("switch (x) { default: if (a) { break; } bar(); }");

    // Potentially foldable
    testSame(
        lines(
            "switch (x) {",
            "  case x:",
            "    foo();",
            "    break;",
            "  default:",
            "    if (a) { break; }",
            "    bar();",
            "}"));

    test(
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

    test(
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

    test(
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

    test(
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

    test("switch (a()) { default: bar(); break;}", "a(); bar();");

    test("switch (a()) { default: break; bar();}", "a();");

    test(
        lines(
            "loop: ",
            "while (true) {",
            "  switch (a()) {",
            "    default:",
            "      bar();",
            "      break loop;",
            "  }",
            "}"),
        "loop: while (true) { a(); bar(); break loop; }");
  }

  @Test
  public void testRemoveNumber() {
    test("3", "");
  }

  @Test
  public void testRemoveVarGet1() {
    test("a", "");
  }

  @Test
  public void testRemoveVarGet2() {
    test("var a = 1;a", "var a = 1");
  }

  @Test
  public void testRemoveNamespaceGet1() {
    test("var a = {};a.b", "var a = {}");
  }

  @Test
  public void testRemoveNamespaceGet2() {
    test("var a = {};a.b=1;a.b", "var a = {};a.b=1");
  }

  @Test
  public void testRemovePrototypeGet1() {
    test("var a = {};a.prototype.b", "var a = {}");
  }

  @Test
  public void testRemovePrototypeGet2() {
    test("var a = {};a.prototype.b = 1;a.prototype.b",
         "var a = {};a.prototype.b = 1");
  }

  @Test
  public void testRemoveAdd1() {
    test("1 + 2", "");
  }

  @Test
  public void testNoRemoveVar1() {
    testSame("var a = 1");
  }

  @Test
  public void testNoRemoveVar2() {
    testSame("var a = 1, b = 2");
  }

  @Test
  public void testNoRemoveAssign1() {
    testSame("a = 1");
  }

  @Test
  public void testNoRemoveAssign2() {
    testSame("a = b = 1");
  }

  @Test
  public void testNoRemoveAssign3() {
    test("1 + (a = 2)", "a = 2");
  }

  @Test
  public void testNoRemoveAssign4() {
    testSame("x.a = 1");
  }

  @Test
  public void testNoRemoveAssign5() {
    testSame("x.a = x.b = 1");
  }

  @Test
  public void testNoRemoveAssign6() {
    test("1 + (x.a = 2)", "x.a = 2");
  }

  @Test
  public void testNoRemoveCall1() {
    testSame("a()");
  }

  @Test
  public void testNoRemoveCall2() {
    test("a()+b()", "a(),b()");
  }

  @Test
  public void testNoRemoveCall3() {
    testSame("a() && b()");
  }

  @Test
  public void testNoRemoveCall4() {
    testSame("a() || b()");
  }

  @Test
  public void testNoRemoveCall5() {
    test("a() || 1", "a()");
  }

  @Test
  public void testNoRemoveCall6() {
    testSame("1 || a()");
  }

  @Test
  public void testNoRemoveThrow1() {
    testSame("function f(){throw a()}");
  }

  @Test
  public void testNoRemoveThrow2() {
    testSame("function f(){throw a}");
  }

  @Test
  public void testNoRemoveThrow3() {
    testSame("function f(){throw 10}");
  }

  @Test
  public void testRemoveInControlStructure1() {
    test("if(x()) 1", "x()");
  }

  @Test
  public void testRemoveInControlStructure2() {
    test("while(2) 1", "while(2);");
  }

  @Test
  public void testRemoveInControlStructure3() {
    test("for(1;2;3) 4", "for(;;);");
  }

  @Test
  public void testHook1() {
    test("1 ? 2 : 3", "");
  }

  @Test
  public void testHook2() {
    test("x ? a() : 3", "x && a()");
  }

  @Test
  public void testHook3() {
    test("x ? 2 : a()", "x || a()");
  }

  @Test
  public void testHook4() {
    testSame("x ? a() : b()");
  }

  @Test
  public void testHook5() {
    test("a() ? 1 : 2", "a()");
  }

  @Test
  public void testHook6() {
    test("a() ? b() : 2", "a() && b()");
  }

  // TODO(johnlenz): Consider adding a post optimization pass to
  // convert OR into HOOK to save parentheses when the operator
  // precedents would require them.
  @Test
  public void testHook7() {
    test("a() ? 1 : b()", "a() || b()");
  }

  @Test
  public void testHook8() {
    testSame("a() ? b() : c()");
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
    testSame("1 && a()");
  }

  @Test
  public void testShortCircuit2() {
    test("1 && a() && 2", "1 && a()");
  }

  @Test
  public void testShortCircuit3() {
    test("a() && 1 && 2", "a()");
  }

  @Test
  public void testShortCircuit4() {
    testSame("a() && 1 && b()");
  }

  @Test
  public void testComplex1() {
    test("1 && a() + b() + c()", "1 && (a(), b(), c())");
  }

  @Test
  public void testComplex2() {
    test("1 && (a() ? b() : 1)", "1 && (a() && b())");
  }

  @Test
  public void testComplex3() {
    test("1 && (a() ? b() : 1 + c())", "1 && (a() ? b() : c())");
  }

  @Test
  public void testComplex4() {
    test("1 && (a() ? 1 : 1 + c())", "1 && (a() || c())");
  }

  @Test
  public void testComplex5() {
    // can't simplify LHS of short circuit statements with side effects
    testSame("(a() ? 1 : 1 + c()) && foo()");
  }

  @Test
  public void testNoRemoveFunctionDeclaration1() {
    testSame("function foo(){}");
  }

  @Test
  public void testNoRemoveFunctionDeclaration2() {
    testSame("var foo = function (){}");
  }

  @Test
  public void testNoSimplifyFunctionArgs1() {
    testSame("f(1 + 2, 3 + g())");
  }

  @Test
  public void testNoSimplifyFunctionArgs2() {
    testSame("1 && f(1 + 2, 3 + g())");
  }

  @Test
  public void testNoSimplifyFunctionArgs3() {
    testSame("1 && foo(a() ? b() : 1 + c())");
  }

  @Test
  public void testNoRemoveInherits1() {
    testSame("var a = {}; this.b = {}; var goog = {}; goog.inherits(b, a)");
  }

  @Test
  public void testNoRemoveInherits2() {
    test("var a = {}; this.b = {}; var goog = {}; goog.inherits(b, a) + 1",
         "var a = {}; this.b = {}; var goog = {}; goog.inherits(b, a)");
  }

  @Test
  public void testNoRemoveInherits3() {
    testSame("this.a = {}; var b = {}; b.inherits(a);");
  }

  @Test
  public void testNoRemoveInherits4() {
    test("this.a = {}; var b = {}; b.inherits(a) + 1;",
         "this.a = {}; var b = {}; b.inherits(a)");
  }

  @Test
  public void testRemoveFromLabel1() {
    test("LBL: void 0", "");
  }

  @Test
  public void testRemoveFromLabel2() {
    test("LBL: foo() + 1 + bar()", "LBL: foo(),bar()");
  }

  @Test
  public void testCall1() {
    test("Math.sin(0);", "");
  }

  @Test
  public void testCall2() {
    test("1 + Math.sin(0);", "");
  }

  @Test
  public void testNew1() {
    test("new Date;", "");
  }

  @Test
  public void testNew2() {
    test("1 + new Date;", "");
  }

  @Test
  public void testFoldAssign() {
    test("x=x", "");
    testSame("x=xy");
    testSame("x=x + 1");
    testSame("x.a=x.a");
    test("var y=(x=x)", "var y=x");
    test("y=1 + (x=x)", "y=1 + x");
  }

  @Test
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
  public void testAwait() {
    testSame("async function f() { await something(); }");
    testSame("async function f() { await some.thing(); }");
  }
}
