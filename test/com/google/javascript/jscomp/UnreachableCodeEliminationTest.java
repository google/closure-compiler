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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test for {@link UnreachableCodeElimination}.
 *
 */
@RunWith(JUnit4.class)
public final class UnreachableCodeEliminationTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new UnreachableCodeElimination(compiler);
  }

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
    enableComputeSideEffects();
  }

  @Test
  public void testDontRemoveExport() {
    test(
        "export function foo() { return 1; alert(2); }",
        "export function foo() { return 1; }");
  }

  @Test
  public void testRemoveUnreachableCode() {
    // switch statement with stuff after "return"
    test("function foo(){switch(foo){case 1:x=1;return;break;" +
         "case 2:{x=2;return;break}default:}}",
         "function foo(){switch(foo){case 1:x=1;return;" +
         "case 2:{x=2}default:}}");

    // if/else statements with returns
    test("function bar(){if(foo)x=1;else if(bar){return;x=2}" +
         "else{x=3;return;x=4}return 5;x=5}",
         "function bar(){if(foo)x=1;else if(bar){return}" +
         "else{x=3;return}return 5}");

    // if statements without blocks
    test("function foo(){if(x==3)return;x=4;y++;while(y==4){return;x=3}}",
         "function foo(){if(x==3)return;x=4;y++;while(y==4){return}}");

    // for/do/while loops
    test("function baz(){for(i=0;i<n;i++){x=3;break;x=4}" +
         "do{x=2;break;x=4}while(x==4);" +
         "while(i<4){x=3;return;x=6}}",
         "function baz(){for(i=0;i<n;){x=3;break}" +
         "do{x=2;break}while(x==4);" +
         "while(i<4){x=3;return}}");

    // return statements on the same level as conditionals
    test("function foo(){if(x==3){return}return 5;while(y==4){x++;return;x=4}}",
         "function foo(){if(x==3){return}return 5}");

    // return statements on the same level as conditionals
    test("function foo(){return 3;for(;y==4;){x++;return;x=4}}",
         "function foo(){return 3}");

    // try/catch statements
    test("function foo(){try{x=3;return x+1;x=5}catch(e){x=4;return 5;x=5}}",
         "function foo(){try{x=3;return x+1}catch(e){x=4;return 5}}");

    // try/finally statements
    test("function foo(){try{x=3;return x+1;x=5}finally{x=4;return 5;x=5}}",
         "function foo(){try{x=3;return x+1}finally{x=4;return 5}}");

    // try/catch/finally statements
    test("function foo(){try{x=3;return x+1;x=5}catch(e){x=3;return;x=2}" +
         "finally{x=4;return 5;x=5}}",

         "function foo(){try{x=3;return x+1}catch(e){x=3;return}" +
         "finally{x=4;return 5}}");

    // test a combination of blocks
    test("function foo(){x=3;if(x==4){x=5;return;x=6}else{x=7}return 5;x=3}",
         "function foo(){x=3;if(x==4){x=5;return}else{x=7}return 5}");

    // test removing multiple statements
    test("function foo() { return 1; var x = 2; var y = 10; return 2;}",
         "function foo() { var y; var x; return 1}");

    test("function foo() { return 1; x = 2; y = 10; return 2;}",
         "function foo(){ return 1}");
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
    test("while(1) { break; do { print(1); break } while(1) }",
         "while(1) { break; do {} while(1) }");
  }

  @Test
  public void testRemoveUselessLiteralValueStatements() {
    test("true;", "");
    test("'hi';", "");
    test("if (x) 1;", "");
    test("while (x) 1;", "while (x);");
    test("do 1; while (x);", "do ; while (x);");
    test("for (;;) 1;", "for (;;);");
    test("switch(x){case 1:true;case 2:'hi';default:true}",
         "switch(x){case 1:case 2:default:}");
  }

  @Test
  public void testConditionalDeadCode() {
    test("function f() { if (1) return 5; else return 5; x = 1}",
        "function f() { if (1) return 5; else return 5; }");
  }

  @Test
  public void testSwitchCase() {
    test("function f() { switch(x) { default: return 5; foo()}}",
         "function f() { switch(x) { default: return 5;}}");
    testSame("function f() { switch(x) { default: return; case 1: foo(); bar()}}");
    test("function f() { switch(x) { default: return; case 1: return 5;bar()}}",
         "function f() { switch(x) { default: return; case 1: return 5;}}");
  }

  @Test
  public void testTryCatchFinally() {
    testSame("try {foo()} catch (e) {bar()}");
    testSame("try { try {foo()} catch (e) {bar()}} catch (x) {bar()}");
    test("try {var x = 1} catch (e) {e()}", "try {var x = 1} finally {}");
    test("try {var x = 1} catch (e) {e()} finally {x()}",
        " try {var x = 1}                 finally {x()}");
    test("try {var x = 1} catch (e) {e()} finally {}",
        " try {var x = 1} finally {}");
    testSame("try {var x = 1} finally {x()}");
    testSame("try {var x = 1} finally {}");
    test("function f() {return; try{var x = 1}catch(e){} }",
         "function f() {var x;}");
  }

  @Test
  public void testRemovalRequiresRedeclaration() {
    test("while(1) { break; var x = 1}", "var x; while(1) { break } ");
    test("while(1) { break; var x=1; var y=1}",
        "var y; var x; while(1) { break } ");
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
  public void testUselessUnconditionalReturn() {
    test("function foo() { return }", " function foo() { }");
    test("function foo() { return; return; x=1 }", "function foo() { }");
    test("function foo() { return; return; var x=1}", "function foo() {var x}");
    test("function foo() { return; function bar() {} }",
         "function foo() {         function bar() {} }" );
    testSame("function foo() { return 5 }");

    test("function f() {switch (a) { case 'a': return}}",
         "function f() {switch (a) { case 'a': }}");
    testSame("function f() {switch (a) { case 'a': case foo(): }}");
    testSame("function f() {switch (a) {" +
             " default: return; case 'a': alert(1)}}");
    testSame("function f() {switch (a) {" +
             " case 'a': return; default: alert(1)}}");
  }

  @Test
  public void testUselessUnconditionalContinue() {
    test("for(;1;) {continue}", " for(;1;) {}");
    test("for(;0;) {continue}", " for(;0;) {}");

    testSame("X: for(;1;) { for(;1;) { if (x()) {continue X} x = 1}}");
    test("for(;1;) { X: for(;1;) { if (x()) {continue X} }}",
         "for(;1;) { X: for(;1;) { if (x()) {}}}");

    test("do { continue } while(1);", "do {  } while(1);");
  }

  @Test
  public void testUselessUnconditionalBreak() {
    test("switch (a) { case 'a': break }", "switch (a) { case 'a': }");
    test("switch (a) { case 'a': break; case foo(): }",
         "switch (a) { case 'a':        case foo(): }");
    test("switch (a) { default: break; case 'a': }",
         "switch (a) { default:        case 'a': }");

    testSame("switch (a) { case 'a': alert(a); break; default: alert(a); }");
    testSame("switch (a) { default: alert(a); break; case 'a': alert(a); }");


    test("X: {switch (a) { case 'a': break X}}",
         "X: {switch (a) { case 'a': }}");

    testSame("X: {switch (a) { case 'a': if (a()) {break X}  a = 1}}");
    test("X: {switch (a) { case 'a': if (a()) {break X}}}",
         "X: {switch (a) { case 'a': if (a()) {}}}");

    test("X: {switch (a) { case 'a': if (a()) {break X}}}",
         "X: {switch (a) { case 'a': if (a()) {}}}");


    testSame("do { break } while(1);");
    testSame("for(;1;) { break }");
  }

  // These tests all require the analysis to go to a fixpoint in order to pass
  @Test
  public void testIteratedRemoval() {
    test("switch (a) { case 'a': break; case 'b': break; case 'c': break }",
        " switch (a) { case 'a': case 'b': case 'c': }");

    test("function foo() {" +
        "  switch (a) { case 'a':return; case 'b':return; case 'c':return }}",
        " function foo() { switch (a) { case 'a': case 'b': case 'c': }}");

    test("for (;;) {\n" +
        "   switch (a) {\n" +
        "   case 'a': continue;\n" +
        "   case 'b': continue;\n" +
        "   case 'c': continue;\n" +
        "   }\n" +
        " }",
        " for (;;) { switch (a) { case 'a': case 'b': case 'c': } }");

    test("function foo() { if (x) { return; } if (x) { return; } x; }",
        " function foo() {}");

    test("var x; \n" +
        " out: { \n" +
        "   try { break out; } catch (e) { break out; } \n" +
        "   x = undefined; \n" +
        " }",
        " var x; out: {}");
  }

  @Test
  public void testIssue311() {
    test("function a(b) {\n" +
         "  switch (b.v) {\n" +
         "    case 'SWITCH':\n" +
         "      if (b.i >= 0) {\n" +
         "        return b.o;\n" +
         "      } else {\n" +
         "        return;\n" +
         "      }\n" +
         "      break;\n" +
         "  }\n" +
         "}",
         "function a(b) {\n" +
         "  switch (b.v) {\n" +
         "    case 'SWITCH':\n" +
         "      if (b.i >= 0) {\n" +
         "        return b.o;\n" +
         "      } else {\n" +
         "      }\n" +
         "  }\n" +
         "}");
  }

  @Test
  public void testIssue4177428a() {
    testSame(
        "f = function() {\n" +
        "  var action;\n" +
        "  a: {\n" +
        "    var proto = null;\n" +
        "    try {\n" +
        "      proto = new Proto\n" +
        "    } finally {\n" +
        "      action = proto;\n" +
        "      break a\n" +  // Keep this...
        "    }\n" +
        "  }\n" +
        "  alert(action)\n" + // and this.
        "};");
  }

  @Test
  public void testIssue4177428b() {
    testSame(
        "f = function() {\n" +
        "  var action;\n" +
        "  a: {\n" +
        "    var proto = null;\n" +
        "    try {\n" +
        "    try {\n" +
        "      proto = new Proto\n" +
        "    } finally {\n" +
        "      action = proto;\n" +
        "      break a\n" +  // Keep this...
        "    }\n" +
        "    } finally {\n" +
        "    }\n" +
        "  }\n" +
        "  alert(action)\n" + // and this.
        "};");
  }

  @Test
  public void testIssue4177428c() {
    testSame(
        "f = function() {\n" +
        "  var action;\n" +
        "  a: {\n" +
        "    var proto = null;\n" +
        "    try {\n" +
        "    } finally {\n" +
        "    try {\n" +
        "      proto = new Proto\n" +
        "    } finally {\n" +
        "      action = proto;\n" +
        "      break a\n" +  // Keep this...
        "    }\n" +
        "    }\n" +
        "  }\n" +
        "  alert(action)\n" + // and this.
        "};");
  }

  @Test
  public void testIssue4177428_continue() {
    testSame(
        "f = function() {\n" +
        "  var action;\n" +
        "  a: do {\n" +
        "    var proto = null;\n" +
        "    try {\n" +
        "      proto = new Proto\n" +
        "    } finally {\n" +
        "      action = proto;\n" +
        "      continue a\n" +  // Keep this...
        "    }\n" +
        "  } while(false)\n" +
        "  alert(action)\n" + // and this.
        "};");
  }

  @Test
  public void testIssue4177428_return() {
    test(
        "f = function() {\n" +
        "  var action;\n" +
        "  a: {\n" +
        "    var proto = null;\n" +
        "    try {\n" +
        "      proto = new Proto\n" +
        "    } finally {\n" +
        "      action = proto;\n" +
        "      return\n" +  // Keep this...
        "    }\n" +
        "  }\n" +
        "  alert(action)\n" + // and remove this.
        "};",
        "f = function() {\n" +
        "  var action;\n" +
        "  a: {\n" +
        "    var proto = null;\n" +
        "    try {\n" +
        "      proto = new Proto\n" +
        "    } finally {\n" +
        "      action = proto;\n" +
        "      return\n" +
        "    }\n" +
        "  }\n" +
        "};"
        );
  }

  @Test
  public void testIssue4177428_multifinally() {
    testSame(
        "a: {\n" +
        " try {\n" +
        " try {\n" +
        " } finally {\n" +
        "   break a;\n" +
        " }\n" +
        " } finally {\n" +
        "   x = 1;\n" +
        " }\n" +
        "}");
  }

  @Test
  public void testIssue5215541_deadVarDeclar() {
    testSame("throw 1; var x");
    testSame("throw 1; function x() {}");
    testSame("throw 1; var x; var y;");
    test("throw 1; var x = foo", "var x; throw 1");
  }

  @Test
  public void testForInLoop() {
    testSame("for(var x in y) {}");
  }

  @Test
  public void testDontRemoveBreakInTryFinally() {
    testSame("function f() {b:try{throw 9} finally {break b} return 1;}");
  }

  @Test
  public void testDontRemoveBreakInTryFinallySwitch() {
    testSame("function f() {b:try{throw 9} finally { switch(x) {case 1: break b} } return 1; }");
  }

  @Test
  public void testIssue1001() {
    test("function f(x) { x.property = 3; } f({})",
         "function f(x) { x.property = 3; }");
    test("function f(x) { x.property = 3; } new f({})",
         "function f(x) { x.property = 3; }");
  }

  @Test
  public void testLetConstBlocks() {
    test("function f() {return 1; let a; }", "function f() {return 1;}");

    test("function f() {return 1; const a = 1; }", "function f() {return 1;}");

    test(
        "function f() { x = 1; {let g; return x} let y}",
        "function f() { x = 1; {let g; return x;}} ");
  }

  @Test
  public void testArrowFunctions() {
    test("f(x => {return x; j = 1})", "f(x => {return x;})");

    testSame("f( () => {return 1;})");
  }

  @Test
  public void testGenerators() {
    test(
        lines(
            "function* f() {", "  while(true) {", "    yield 1;", "  }", "  x = 1;", "}"),
        lines("function* f() {", "  while(true) {", "    yield 1;", "  }", "}"));

    testSame(lines("function* f() {", "  while(true) {", "    yield 1;", "  }", "}"));

    testSame(
        lines(
            "function* f() {",
            "  let i = 0;",
            "  while (true) {",
            "    if (i < 10) {",
            "      yield i;",
            "    } else {",
            "      break;",
            "    }",
            "  }",
            "  let x = 1;",
            "}"));
  }

  @Test
  public void testForOf() {
    test("for(x of i){ 1; }", "for(x of i) {}");

    testSame("for(x of i){}");
  }

  @Test
  public void testRemoveUselessTemplateStrings() {
    test("`hi`", "");

    testSame("`hello visitor # ${i++}`");
  }

  // TODO (simranarora) Make the pass handle ES6 Modules correctly.
  public void disabled_testRemoveFromImportStatement_ES6Modules() {
    // Error: Invalid attempt to remove: STRING ./foo 1 [length: 7] [source_file: testcode] from
    // IMPORT 1 [length: 24] [source_file: testcode]
    testSame("import foo from './foo'; foo('hello');");

    // Error: Invalid attempt to remove: STRING ./foo 1 [length: 7] [source_file: testcode] from
    // IMPORT 1 [length: 24] [source_file: testcode]
    test(
        "import foo from './foo';",
        "import './foo';");

    // Error: Invalid attempt to remove node: NAME x 1 [length: 1] [source_file: testcode] of
    // IMPORT_SPEC 1 [length: 1] [source_file: testcode]
    test(
        "import {x, y} from './foo'; x('hi');",
        "import {x} from './foo'; x('hi');");
  }

  @Test
  public void testLetConstBlocks_withES6Modules() {
    test(
        "export function f() {return 1; let a; } f();",
        "export function f() {return 1;}");

    test(
        "export function f() {return 1; const a = 1; }",
        "export function f() {return 1;}");

    test(
        "export function f() { x = 1; {let g; return x} let y}",
        "export function f() { x = 1; {let g; return x;}} ");
  }

  // Currently leaves an empty module.
  // SCRIPT
  //   MODULE_BODY
  // TODO(tbreisacher): Fix and enable.
  public void disabled_testLetConstBlocks_withES6Modules2() {
    test("export let x = 2;", "");
  }

  @Test
  public void testRemoveUnreachableCode_withES6Modules() {
    // Switch statements
    test(
        "export function foo() { switch (foo) { case 1:x = 1; return; break;"
            + "case 2:{ x = 2; return; break } default:}}",
        "export function foo() { switch (foo) { case 1:x = 1; return;"
            + "case 2:{ x = 2 } default:}}");

    // if/else statements with returns
    test(
        "export function bar(){if(foo)x=1;else if(bar){return;x=2}"
            + "else{x=3;return;x=4}return 5;x=5}",
        "export function bar(){if(foo)x=1;else if(bar){return}"
            + "else{x=3;return}return 5}");
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
            "class Foo {",
            "  [function() {",
            "    1; return 'x';",
            "  }()]() { return 1; }",
            "}"),
        lines(
            "class Foo {",
            "  [function() {",
            "    return 'x';",
            "  }()]() { return 1; }",
            "}"));
  }
}
