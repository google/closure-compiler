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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link CheckUnreachableCode}.
 *
 */
@RunWith(JUnit4.class)
public final class CheckUnreachableCodeTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CombinedCompilerPass(compiler,
        new CheckUnreachableCode(compiler));
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2018);
  }

  @Test
  public void testCorrectSimple() {
    testSame("var x");
    testSame("var x = 1");
    testSame("var x = 1; x = 2;");
    testSame("if (x) { var x = 1 }");
    testSame("if (x) { var x = 1 } else { var y = 2 }");
    testSame("while(x) {}");
  }

  @Test
  public void testIncorrectSimple() {
    assertUnreachable("function f() { return; x=1; }");
    assertUnreachable("function f() { return; x=1; x=1; }");
    assertUnreachable("function f() { return; var x = 1; }");
  }

  @Test
  public void testCorrectIfReturns() {
    testSame("function f() { if (x) { return } }");
    testSame("function f() { if (x) { return } return }");
    testSame("function f() { if (x) { if (y) { return } } else { return }}");
    testSame("function f()" +
        "{ if (x) { if (y) { return } return } else { return }}");
  }

  @Test
  public void testInCorrectIfReturns() {
    assertUnreachable(
        "function f() { if (x) { return } else { return } return }");
  }

  @Test
  public void testCorrectSwitchReturn() {
    testSame("function f() { switch(x) { default: return; case 1: x++; }}");
    testSame("function f() {" +
        "switch(x) { default: return; case 1: x++; } return }");
    testSame("function f() {" +
        "switch(x) { default: return; case 1: return; }}");
    testSame("function f() {" +
        "switch(x) { case 1: return; } return }");
    testSame("function f() {" +
        "switch(x) { case 1: case 2: return; } return }");
    testSame("function f() {" +
        "switch(x) { case 1: return; case 2: return; } return }");
    testSame("function f() {" +
        "switch(x) { case 1 : return; case 2: return; } return }");
  }

  @Test
  public void testInCorrectSwitchReturn() {
    assertUnreachable("function f() {" +
        "switch(x) { default: return; case 1: return; } return }");
    assertUnreachable("function f() {" +
        "switch(x) { default: return; return; case 1: return; } }");
  }

  @Test
  public void testCorrectLoopBreaksAndContinues() {
    testSame("while(1) { foo(); break }");
    testSame("while(1) { foo(); continue }");
    testSame("for(;;) { foo(); break }");
    testSame("for(;;) { foo(); continue }");
    testSame("for(;;) { if (x) { break } }");
    testSame("for(;;) { if (x) { continue } }");
    testSame("do { foo(); continue} while(1)");
  }

  @Test
  public void testInCorrectLoopBreaksAndContinues() {
    assertUnreachable("while(1) { foo(); break; bar()}");
    assertUnreachable("while(1) { foo(); continue; bar() }");
    assertUnreachable("for(;;) { foo(); break; bar() }");
    assertUnreachable("for(;;) { foo(); continue; bar() }");
    assertUnreachable("for(;;) { if (x) { break; bar() } }");
    assertUnreachable("for(;;) { if (x) { continue; bar() } }");
    assertUnreachable("do { foo(); continue; bar()} while(1)");
  }

  @Test
  public void testUncheckedWhileInDo() {
    assertUnreachable("do { foo(); break} while(1)");
  }

  @Test
  public void testUncheckedConditionInFor() {
    assertUnreachable("for(var x = 0; x < 100; x++) { break };");
  }

  @Test
  public void testFunctionDeclaration() {
    // functions are not in our CFG.
    testSame("function f() { return; function ff() { }}");
  }

  @Test
  public void testVarDeclaration() {
    assertUnreachable("function f() { return; var x = 1 }");
    // I think the user should fix this as well.
    assertUnreachable("function f() { return; var x }");
  }

  @Test
  public void testReachableTryCatchFinally() {
    testSame("try { } finally {  }");
    testSame("try { foo(); } finally { bar() } ");
    testSame("try { foo() } finally { bar() }");
    testSame("try { foo(); } catch (e) {e()} finally { bar() }");
    testSame("try { foo() } catch (e) {e()} finally { bar() }");
    testSame("try { foo() } catch (e) { throw e; } finally { bar() }");
  }

  @Test
  public void testUnreachableCatch() {
    assertUnreachable("try { var x = 0 } catch (e) { }");
    assertUnreachable("try { } catch (e) { throw e; }");
  }

  @Test
  public void testSpuriousBreak() {
    testSame("switch (x) { default: throw x; break; }");
  }

  @Test
  public void testInstanceOfThrowsException() {
    testSame("function f() {try { if (value instanceof type) return true; } " +
             "catch (e) { }}");
  }

  @Test
  public void testFalseCondition() {
    assertUnreachable("if(false) { }");
    assertUnreachable("if(0) { }");
  }

  @Test
  public void testUnreachableLoop() {
    assertUnreachable("while(false) {}");
  }

  @Test
  public void testInfiniteLoop() {
    testSame("while (true) { foo(); break; }");

    // TODO(user): Have a infinite loop warning instead.
    assertUnreachable("while(true) {} foo()");
  }

  @Test
  public void testSuppression() {
    assertUnreachable("if(false) { }");

    testSame(
        "/** @fileoverview\n" +
        " * @suppress {uselessCode}\n" +
        " */\n" +
        "if(false) { }");

    testSame(
        "/** @fileoverview\n" +
        " * @suppress {uselessCode}\n" +
        " */\n" +
        "function f() { if(false) { } }");

    testSame(
        "/**\n" +
        " * @suppress {uselessCode}\n" +
        " */\n" +
        "function f() { if(false) { } }");

    assertUnreachable(
        "/**\n" +
        " * @suppress {uselessCode}\n" +
        " */\n" +
        "function f() { if(false) { } }\n" +
        "function g() { if(false) { } }\n");

    testSame(
        "/**\n" +
        " * @suppress {uselessCode}\n" +
        " */\n" +
        "function f() {\n" +
        "  function g() { if(false) { } }\n" +
        "  if(false) { } }\n");

    assertUnreachable(
        "function f() {\n" +
        "  /**\n" +
        "   * @suppress {uselessCode}\n" +
        "   */\n" +
        "  function g() { if(false) { } }\n" +
        "  if(false) { } }\n");

    testSame(
        "function f() {\n" +
        "  /**\n" +
        "   * @suppress {uselessCode}\n" +
        "   */\n" +
        "  function g() { if(false) { } }\n" +
        "}\n");
  }

  @Test
  public void testES6FeaturesInIfExpression() {
    // class X{} always eval to true by toBoolean();
    assertUnreachable("if(!class {}) x = 1;");
    assertUnreachable("if(!class A{}) x = 1;");

    // Template string with substitution and tagged template will be evaluated
    // to UNKNOWN. Template string with definite length (without substitution)
    // will be evaled like a normal string.
    assertUnreachable("if(!`tempLit`) {x = 1;}");
    assertUnreachable("if(``) {x = 1;}");
    testSame("if(`temp${sub}Lit`) {x = 1;} else {x = 2;}");
    testSame("if(`${sub}`) {x = 1;} else {x = 2;}");
    testSame("if(tagged`tempLit`) {x = 1;} else {x = 2;}");

    // Functions are truthy.
    assertUnreachable("if(()=>true) {x = 1;} else {x = 2;}");
  }

  @Test
  public void testES6FeaturesInTryCatch() {
    assertUnreachable("try { let x = 1; } catch(e) {}");
    assertUnreachable("try { const x = 1; } catch(e) {}");
    assertUnreachable("try {()=>42;} catch(e) {}");
    assertUnreachable("try {function *gen(){};} catch(e) {}");
    // Assumed tagged template may throw exception.
    testSame("try {tagged`temp`;} catch(e) {}");

    assertUnreachable("try { var obj = {a(){}};} catch(e) {}");
    testSame("try { var obj = {a(){}}; obj.a();} catch(e) {}");
  }

  @Test
  public void testCorrectForOfBreakAndContinues() {
    testSame("for(x of [1, 2, 3]) {foo();}");
    testSame("for(x of [1, 2, 3]) {foo(); break;}");
    testSame("for(x of [1, 2, 3]) {foo(); continue;}");
  }

  @Test
  public void testInCorrectForOfBreakAndContinues() {
    assertUnreachable("for(x of [1, 2, 3]) {foo(); break; bar();}");
    assertUnreachable("for(x of [1, 2, 3]) {foo(); continue; bar();}");

    assertUnreachable("for(x of [1, 2, 3]) {if(x) {break; bar();}}");
    assertUnreachable("for(x of [1, 2, 3]) {if(x) {continue; bar();}}");
  }

  @Test
  public void testForLoopsEs6() {
    assertUnreachable("for(;;) {if(x) {continue; bar();}}");
    assertUnreachable("for(x in y) {if(x) {continue; bar();}}");
  }

  @Test
  public void testReturnsInShorthandFunctionOfObjLit() {
    testSame(lines(
        "var obj = {",
        "  f() { ",
        "    switch(x) { ",
        "      default: return; ",
        "      case 1: x++; ",
        "    }",
        "  }",
        "}"));
    assertUnreachable(lines(
        "var obj = {f() {",
        "  switch(x) { ",
        "    default: return; ",
        "    case 1: return; ",
        "  }",
        "  return; ",
        "}}"));
    testSame("var obj = {f() { if(x) {return;} else {return; }}}");
    assertUnreachable(lines(
        "var obj = {f() { ",
        "  if(x) {",
        "    return;",
        "  } else {",
        "    return;",
        "  }",
        "  return; ",
        "}}"));
  }

  @Test
  public void testObjLit() {
    assertUnreachable("var a = {c(){if(true){return;}x = 1;}};");
  }

  @Test
  public void testClass() {
    testSame("class C{func(){}}");
    assertUnreachable("class C{func(){if (true){return;} else {return;}}}");
    assertUnreachable("class C{func(){if (true){return;} x = 1;}}");
    testSame("var C = class{func(){}}");
    testSame("let C = class{func(){}}");
    testSame("var C; C = class{func(){}}");
    testSame("let C; C = class{func(){}}");
    assertUnreachable("var C = class{func(){if (true){return;} x = 1;}}");
  }

  @Test
  public void testUnderClass() {
    testSame("class C {} alert(1);");
    testSame("class D {} class C extends D {} alert(1)");
    testSame("class D{} alert(1); class C extends D {}");
  }

  @Test
  public void testFunction() {
    testSame("function f() {} alert(1);");
  }

  @Test
  public void testSubclass() {
    testSame(
        lines(
            "class D {foo() {if (true) {return;}}}",
            "class C extends D {foo() {super.foo();}}"));
  }

  @Test
  public void testArrowFunction() {
    testSame("() => 3");
    testSame("e => e + 1");
    testSame("listen('click', e => onclick(e), true);");
    testSame("listen('click', e => { onclick(e); }, true);");
    assertUnreachable("listen('click', e => { return 0; onclick(e); }, true);");
    assertUnreachable("() => {return 3; x = 1;}");
    assertUnreachable("() => { if (false) x = 1;}");
    testSame("var f = array.filter(g => {if (g % 3) x = 1;});");
    assertUnreachable("var f = array.filter(g => {if (false) g = 1;});");
  }

  @Test
  public void testGenerators() {
    testSame(
        lines(
            "function* f() {",
            "  var i = 0;",
            "  while(true)",
            "    yield i++;",
            "}"));

    assertUnreachable(
        lines(
            "function* f() {",
            "  var i = 0;",
            "  while(true) {",
            "    yield i++;",
            "  }",
            "  i = 1;",
            "}"));

    testSame(
        lines(
            "function* f() {",
            "  var i = 0;",
            "  while(true) {",
            "    yield i;",
            "    i++;",
            "  }",
            "}"));

    testSame(
        lines(
            "function *f() {",
            "  try {",
            "    yield;",
            "  } catch (e) {",
            "    alert(e);",
            "  }",
            "}"));

    testSame(
        lines(
            "function *f() {",
            "  try {",
            "    yield 1;",
            "  } catch (e) {",
            "    alert(e);",
            "  }",
            "}"));
  }

  @Test
  public void testAwaitCanCauseError() {
    testSame("async function f(/** !Promise<?> */ p) { try { await p; } catch (e) {} }");
    // Note: for simplicity, we assume that `await <expr>` may always throw an exception, even if
    // <expr> is a literal.
    testSame("async function f() { try { await 3; } catch (e) {} }");
  }

  @Test
  public void testForAwait() {
    testSame("async function f(iter) { for await (const item of iter) { item; } }");
    assertUnreachable(
        lines(
            "async function f(iter) {",
            "  for await (const item of iter) { if (false) { 3; } }",
            "}"));
  }

  private void assertUnreachable(String js) {
    test(js, js, warning(CheckUnreachableCode.UNREACHABLE_CODE));
  }
}
