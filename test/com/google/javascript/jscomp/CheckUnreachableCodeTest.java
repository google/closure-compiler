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

/**
 * Tests for {@link CheckUnreachableCode}.
 *
 */
public final class CheckUnreachableCodeTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CombinedCompilerPass(compiler,
        new CheckUnreachableCode(compiler));
  }

  public void testCorrectSimple() {
    testSame("var x");
    testSame("var x = 1");
    testSame("var x = 1; x = 2;");
    testSame("if (x) { var x = 1 }");
    testSame("if (x) { var x = 1 } else { var y = 2 }");
    testSame("while(x) {}");
  }

  public void testIncorrectSimple() {
    assertUnreachable("function f() { return; x=1; }");
    assertUnreachable("function f() { return; x=1; x=1; }");
    assertUnreachable("function f() { return; var x = 1; }");
  }

  public void testCorrectIfReturns() {
    testSame("function f() { if (x) { return } }");
    testSame("function f() { if (x) { return } return }");
    testSame("function f() { if (x) { if (y) { return } } else { return }}");
    testSame("function f()" +
        "{ if (x) { if (y) { return } return } else { return }}");
  }

  public void testInCorrectIfReturns() {
    assertUnreachable(
        "function f() { if (x) { return } else { return } return }");
  }

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

  public void testInCorrectSwitchReturn() {
    assertUnreachable("function f() {" +
        "switch(x) { default: return; case 1: return; } return }");
    assertUnreachable("function f() {" +
        "switch(x) { default: return; return; case 1: return; } }");
  }

  public void testCorrectLoopBreaksAndContinues() {
    testSame("while(1) { foo(); break }");
    testSame("while(1) { foo(); continue }");
    testSame("for(;;) { foo(); break }");
    testSame("for(;;) { foo(); continue }");
    testSame("for(;;) { if (x) { break } }");
    testSame("for(;;) { if (x) { continue } }");
    testSame("do { foo(); continue} while(1)");
  }

  public void testInCorrectLoopBreaksAndContinues() {
    assertUnreachable("while(1) { foo(); break; bar()}");
    assertUnreachable("while(1) { foo(); continue; bar() }");
    assertUnreachable("for(;;) { foo(); break; bar() }");
    assertUnreachable("for(;;) { foo(); continue; bar() }");
    assertUnreachable("for(;;) { if (x) { break; bar() } }");
    assertUnreachable("for(;;) { if (x) { continue; bar() } }");
    assertUnreachable("do { foo(); continue; bar()} while(1)");
  }

  public void testUncheckedWhileInDo() {
    assertUnreachable("do { foo(); break} while(1)");
  }

  public void testUncheckedConditionInFor() {
    assertUnreachable("for(var x = 0; x < 100; x++) { break };");
  }

  public void testFunctionDeclaration() {
    // functions are not in our CFG.
    testSame("function f() { return; function ff() { }}");
  }

  public void testVarDeclaration() {
    assertUnreachable("function f() { return; var x = 1 }");
    // I think the user should fix this as well.
    assertUnreachable("function f() { return; var x }");
  }

  public void testReachableTryCatchFinally() {
    testSame("try { } finally {  }");
    testSame("try { foo(); } finally { bar() } ");
    testSame("try { foo() } finally { bar() }");
    testSame("try { foo(); } catch (e) {e()} finally { bar() }");
    testSame("try { foo() } catch (e) {e()} finally { bar() }");
    testSame("try { foo() } catch (e) { throw e; } finally { bar() }");
  }

  public void testUnreachableCatch() {
    assertUnreachable("try { var x = 0 } catch (e) { }");
    assertUnreachable("try { } catch (e) { throw e; }");
  }

  public void testSpuriousBreak() {
    testSame("switch (x) { default: throw x; break; }");
  }

  public void testInstanceOfThrowsException() {
    testSame("function f() {try { if (value instanceof type) return true; } " +
             "catch (e) { }}");
  }

  public void testFalseCondition() {
    assertUnreachable("if(false) { }");
    assertUnreachable("if(0) { }");
  }

  public void testUnreachableLoop() {
    assertUnreachable("while(false) {}");
  }

  public void testInfiniteLoop() {
    testSame("while (true) { foo(); break; }");

    // TODO(user): Have a infinite loop warning instead.
    assertUnreachable("while(true) {} foo()");
  }

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

  public void testES6FeaturesInIfExpression() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
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

  public void testES6FeaturesInTryCatch() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    assertUnreachable("try { let x = 1; } catch(e) {}");
    assertUnreachable("try { const x = 1; } catch(e) {}");
    assertUnreachable("try {()=>42;} catch(e) {}");
    assertUnreachable("try {function *gen(){};} catch(e) {}");
    // Assumed tagged template may throw exception.
    testSame("try {tagged`temp`;} catch(e) {}");

    assertUnreachable("try { var obj = {a(){}};} catch(e) {}");
    testSame("try { var obj = {a(){}}; obj.a();} catch(e) {}");
  }

  public void testCorrectForOfBreakAndContinues() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    testSame("for(x of [1, 2, 3]) {foo();}");
    testSame("for(x of [1, 2, 3]) {foo(); break;}");
    testSame("for(x of [1, 2, 3]) {foo(); continue;}");
  }

  public void testInCorrectForOfBreakAndContinues() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    assertUnreachable("for(x of [1, 2, 3]) {foo(); break; bar();}");
    assertUnreachable("for(x of [1, 2, 3]) {foo(); continue; bar();}");

    assertUnreachable("for(x of [1, 2, 3]) {if(x) {break; bar();}}");
    assertUnreachable("for(x of [1, 2, 3]) {if(x) {continue; bar();}}");
  }

  public void testForLoopsEs6() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    assertUnreachable("for(;;) {if(x) {continue; bar();}}");
    assertUnreachable("for(x in y) {if(x) {continue; bar();}}");
  }

  public void testReturnsInShorthandFunctionOfObjLit() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    testSame(LINE_JOINER.join(
        "var obj = {",
        "  f() { ",
        "    switch(x) { ",
        "      default: return; ",
        "      case 1: x++; ",
        "    }",
        "  }",
        "}"));
    assertUnreachable(LINE_JOINER.join(
        "var obj = {f() {",
        "  switch(x) { ",
        "    default: return; ",
        "    case 1: return; ",
        "  }",
        "  return; ",
        "}}"));
    testSame("var obj = {f() { if(x) {return;} else {return; }}}");
    assertUnreachable(LINE_JOINER.join(
        "var obj = {f() { ",
        "  if(x) {",
        "    return;",
        "  } else {",
        "    return;",
        "  }",
        "  return; ",
        "}}"));
  }

  public void testObjLit() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    assertUnreachable("var a = {c(){if(true){return;}x = 1;}};");
  }

  public void testClass() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    testSame("class C{func(){}}");
    assertUnreachable("class C{func(){if (true){return;} else {return;}}}");
    assertUnreachable("class C{func(){if (true){return;} x = 1;}}");
    testSame("var C = class{func(){}}");
    testSame("let C = class{func(){}}");
    testSame("var C; C = class{func(){}}");
    testSame("let C; C = class{func(){}}");
    assertUnreachable("var C = class{func(){if (true){return;} x = 1;}}");
  }

  private void assertUnreachable(String js) {
    test(js, js, null, CheckUnreachableCode.UNREACHABLE_CODE);
  }
}
