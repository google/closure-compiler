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
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test for {@link UnreachableCodeElimination}. */
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

    enableNormalize(); // this pass requires normalization
    enableComputeSideEffects();
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
  public void testRemoveUnreachableCode() {
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
            "      return;",
            "    case 2: {",
            "      x=2;",
            "    }",
            "    default:",
            "  }",
            "}"));

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
            "  if (foo)",
            "    x=1;",
            "  else if(bar) {",
            "    return;",
            "  } else {",
            "    x=3;",
            "    return;",
            "  }",
            "  return 5;",
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
            "  if (x == 3) return;",
            "  x = 4;",
            "  y++;",
            "  for (; y == 4; ) {",
            "    return",
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
            "  for (; i < n;) {",
            "    x = 3;",
            "    break",
            "  }",
            "  do {",
            "    x = 2;",
            "    break",
            "  } while (x == 4);",
            "  for (; i < 4; ) {",
            "    x = 3;",
            "    return",
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
            "  if (x == 3) {",
            "    return",
            "  }",
            "  return 5",
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
            "    return",
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
            "    return",
            "  } else {",
            "    x = 7",
            "  }",
            "  return 5",
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
        "for (; 1;) { break;                                 }");
  }

  @Test
  public void testRemoveUselessLiteralValueStatements() {
    test("true;", "");
    test("'hi';", "");
    test("if (x) 1;", "");
    // NOTE: This pass should never see while-loops, because normalization replaces them all with
    // for-loops.
    test("for (; x;) 1;", "for (; x;);");
    test("do 1; while (x);", "do ; while (x);");
    test("for (;;) 1;", "for (;;);");
    test("switch(x){case 1:true;case 2:'hi';default:true}", "switch(x){case 1:case 2:default:}");
  }

  @Test
  public void testConditionalDeadCode() {
    test(
        "function f() { if (1) return 5; else return 5; x = 1}",
        "function f() { if (1) return 5; else return 5; }");
  }

  @Test
  public void testSwitchCase() {
    test(
        "function f() { switch(x) { default: return 5; foo()}}",
        "function f() { switch(x) { default: return 5;}}");
    testSame("function f() { switch(x) { default: return; case 1: foo(); bar()}}");
    test(
        "function f() { switch(x) { default: return; case 1: return 5;bar()}}",
        "function f() { switch(x) { default: return; case 1: return 5;}}");
  }

  @Test
  public void testTryCatchFinally() {
    testSame("try {foo()} catch (e) {bar()}");
    testSame("try { try {foo()} catch (e) {bar()}} catch (x) {bar()}");
    test("try {var x = 1} catch (e) {e()}", "try {var x = 1} finally {}");
    test(
        "try {var x = 1} catch (e) {e()} finally {x()}",
        " try {var x = 1}                 finally {x()}");
    test("try {var x = 1} catch (e) {e()} finally {}", " try {var x = 1} finally {}");
    testSame("try {var x = 1} finally {x()}");
    testSame("try {var x = 1} finally {}");
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
            "for (; 1;) {",
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
            "for (; 1;) {",
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
  public void testUselessUnconditionalReturn() {
    test("function foo() { return }", " function foo() { }");
    test("function foo() { return; return; x=1 }", "function foo() { }");
    test("function foo() { return; return; var x=1}", "function foo() {var x}");
    test(
        "function foo() { return; function bar() {} }",
        "function foo() {         function bar() {} }");
    testSame("function foo() { return 5 }");

    test(
        "function f() {switch (a) { case 'a': return}}", "function f() {switch (a) { case 'a': }}");
    testSame("function f() {switch (a) { case 'a': case foo(): }}");
    testSame("function f() {switch (a) {" + " default: return; case 'a': alert(1)}}");
    testSame("function f() {switch (a) {" + " case 'a': return; default: alert(1)}}");
  }

  @Test
  public void testUselessUnconditionalContinue() {
    test("for(;1;) {continue}", " for(;1;) {}");
    test("for(;0;) {continue}", " for(;0;) {}");

    testSame("X: for(;1;) { for(;1;) { if (x()) {continue X} x = 1}}");
    test(
        "for(;1;) { X: for(;1;) { if (x()) {continue X} }}",
        "for(;1;) { X: for(;1;) { if (x()) {}}}");

    test("do { continue } while(1);", "do {  } while(1);");
  }

  @Test
  public void testUselessUnconditionalBreak() {
    test("switch (a) { case 'a': break }", "switch (a) { case 'a': }");
    test(
        "switch (a) { case 'a': break; case foo(): }",
        "switch (a) { case 'a':        case foo(): }");
    test("switch (a) { default: break; case 'a': }", "switch (a) { default:        case 'a': }");

    testSame("switch (a) { case 'a': alert(a); break; default: alert(a); }");
    testSame("switch (a) { default: alert(a); break; case 'a': alert(a); }");

    test("X: {switch (a) { case 'a': break X}}", "X: {switch (a) { case 'a': }}");

    testSame("X: {switch (a) { case 'a': if (a()) {break X}  a = 1}}");
    test(
        "X: {switch (a) { case 'a': if (a()) {break X}}}",
        "X: {switch (a) { case 'a': if (a()) {}}}");

    test(
        "X: {switch (a) { case 'a': if (a()) {break X}}}",
        "X: {switch (a) { case 'a': if (a()) {}}}");

    testSame("do { break } while(1);");
    testSame("for(;1;) { break }");
  }

  // These tests all require the analysis to go to a fixpoint in order to pass
  @Test
  public void testIteratedRemoval() {
    test(
        "switch (a) { case 'a': break; case 'b': break; case 'c': break }",
        " switch (a) { case 'a': case 'b': case 'c': }");

    test(
        "function foo() {" + "  switch (a) { case 'a':return; case 'b':return; case 'c':return }}",
        " function foo() { switch (a) { case 'a': case 'b': case 'c': }}");

    test(
        "for (;;) {\n"
            + "   switch (a) {\n"
            + "   case 'a': continue;\n"
            + "   case 'b': continue;\n"
            + "   case 'c': continue;\n"
            + "   }\n"
            + " }",
        " for (;;) { switch (a) { case 'a': case 'b': case 'c': } }");

    test("function foo() { if (x) { return; } if (x) { return; } x; }", " function foo() {}");

    test(
        "var x; \n"
            + " out: { \n"
            + "   try { break out; } catch (e) { break out; } \n"
            + "   x = undefined; \n"
            + " }",
        " var x; out: {}");
  }

  @Test
  public void testIssue311() {
    test(
        "function a(b) {\n"
            + "  switch (b.v) {\n"
            + "    case 'SWITCH':\n"
            + "      if (b.i >= 0) {\n"
            + "        return b.o;\n"
            + "      } else {\n"
            + "        return;\n"
            + "      }\n"
            + "      break;\n"
            + "  }\n"
            + "}",
        "function a(b) {\n"
            + "  switch (b.v) {\n"
            + "    case 'SWITCH':\n"
            + "      if (b.i >= 0) {\n"
            + "        return b.o;\n"
            + "      } else {\n"
            + "      }\n"
            + "  }\n"
            + "}");
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
    testSame(
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
            "      break a",
            // Keep this...
            "    }",
            "    } finally {",
            "    }",
            "  }",
            "  alert(action)",
            // and this.
            "};"));
  }

  @Test
  public void testIssue4177428c() {
    testSame(
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
            "};"));
  }

  @Test
  public void testIssue4177428_continue() {
    testSame(
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
            "};"));
  }

  @Test
  public void testIssue4177428_return() {
    test(
        lines(
            "f = function() {", //
            "  var action;",
            "  a: {",
            "    var proto = null;",
            "    try {",
            "      proto = new Proto",
            "    } finally {",
            "      action = proto;",
            "      return;",
            // Keep this...
            "    }",
            "  }",
            "  alert(action)",
            // and remove this.
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
            "      return;",
            "    }",
            "  }",
            "};"));
  }

  @Test
  public void testIssue4177428_multifinally() {
    testSame(
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
        "function f() { x = 1; { let g; return x; } let y}",
        "function f() { x = 1; { let g; return x; }      } ");
  }

  @Test
  public void testArrowFunctions() {
    test("f(x => {return x; j = 1})", "f(x => {return x;})");

    testSame("f( () => {return 1;})");
  }

  @Test
  public void testGenerators() {
    // TODO(b/129557644): Make this test more broad.
    test(
        lines(
            "function* f() {", //
            "  for (;;) {",
            "    yield 1;",
            "  }",
            "  x = 1;",
            "}"),
        lines(
            "function* f() {", //
            "  for (;;) {",
            "    yield 1;",
            "  }",
            "}"));

    testSame(
        lines(
            "function* f() {", //
            "  for (;;) {",
            "    yield 1;",
            "  }",
            "}"));

    testSame(
        lines(
            "function* f() {",
            "  let i = 0;",
            "  for (;;) {",
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
  @Test
  @Ignore
  public void testRemoveFromImportStatement_ES6Modules() {
    // Error: Invalid attempt to remove: STRING ./foo 1 [length: 7] [source_file: testcode] from
    // IMPORT 1 [length: 24] [source_file: testcode]
    testSame("import foo from './foo'; foo('hello');");

    // Error: Invalid attempt to remove: STRING ./foo 1 [length: 7] [source_file: testcode] from
    // IMPORT 1 [length: 24] [source_file: testcode]
    test("import foo from './foo';", "import './foo';");

    // Error: Invalid attempt to remove node: NAME x 1 [length: 1] [source_file: testcode] of
    // IMPORT_SPEC 1 [length: 1] [source_file: testcode]
    test("import {x, y} from './foo'; x('hi');", "import {x} from './foo'; x('hi');");
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
            "  {",
            "    let g;",
            "    return x;",
            "  }",
            "}",
            "export { f as f };"));
  }

  // Currently leaves an empty module.
  // SCRIPT
  //   MODULE_BODY
  // TODO(tbreisacher): Fix and enable.
  @Test
  @Ignore
  public void testLetConstBlocks_asEs6ModuleExport() {
    test("export let x = 2;", "");
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
            "      return;",
            "    case 2: {",
            "      x = 2;",
            "    }",
            "    default:",
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
            "function bar() {",
            "  if (foo)",
            "    x = 1;",
            "  else if (bar) {",
            "    return;",
            "  } else {",
            "    x=3;",
            "    return;",
            "  }",
            "  return 5;",
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
  public void testStaticBlockNotRemoved() {
    testSame("class Foo { static {} }");
  }

  @Test
  public void testRemoveUnreachableCodeInStaticBlock1() {
    // TODO(b/240443227): Unreachable/Useless code isn't removed in static blocks
    testSame(
        lines(
            "class Foo {", //
            "  static {",
            "    switch (a) { case 'a': break }",
            "    try {var x = 1} catch (e) {e()}",
            "    true;",
            "    if (x) 1;",
            "  }",
            "}"));
  }
}
