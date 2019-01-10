/*
 * Copyright 2014 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.parsing;

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.javascript.jscomp.parsing.Config.LanguageMode;
import com.google.javascript.jscomp.parsing.Config.StrictMode;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.SimpleSourceFile;
import com.google.javascript.rhino.StaticSourceFile.SourceKind;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.testing.BaseJSTypeTestCase;
import com.google.javascript.rhino.testing.TestErrorReporter;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Ported from rhino/testsrc/org/mozilla/javascript/tests/AttachJsDocsTest.java */
@RunWith(JUnit4.class)
public final class AttachJsdocsTest extends BaseJSTypeTestCase {
  private Config.LanguageMode mode;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    mode = LanguageMode.ECMASCRIPT3;
  }

  @Test
  public void testOldJsdocAdd() {
    Node root = parse("1 + /** attach */ value;");
    Node plus = root.getFirstFirstChild();
    assertThat(plus.getLastChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocArrayLit() {
    Node root = parse("[1, /** attach */ 2]");
    Node lit = root.getFirstFirstChild();
    assertThat(lit.getSecondChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocAssign1() {
    Node root = parse("x = 1; /** attach */ y = 2;");
    Node assign = root.getLastChild().getFirstChild();
    assertThat(assign.getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocAssign2() {
    Node root = parse("x = 1; /** attach */y.p = 2;");
    Node assign = root.getLastChild().getFirstChild();
    assertThat(assign.getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocAssign3() {
    Node root =
        parse("/** @const */ var g = {}; /** @type {number} */ (g.foo) = 3;");
    Node assign = root.getLastChild().getFirstChild();
    assertThat(assign.getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocBlock1() {
    Node root = parse("if (x) { /** attach */ x; }");
    Node thenBlock = root.getFirstChild().getSecondChild();
    assertThat(thenBlock.getFirstFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocBlock2() {
    Node root = parse("if (x) { x; /** attach */ y; }");
    Node thenBlock = root.getFirstChild().getSecondChild();
    assertThat(thenBlock.getLastChild().getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocBreak() {
    Node root = parse("FOO: for (;;) { break /** don't attach */ FOO; }");
    Node forStm = root.getFirstChild().getLastChild();
    Node breakStm = forStm.getChildAtIndex(3).getFirstChild();
    assertNode(breakStm).hasToken(Token.BREAK);
    assertThat(breakStm.getJSDocInfo()).isNull();
    assertThat(breakStm.getFirstChild().getJSDocInfo()).isNull();
  }

  @Test
  @Ignore
  public void testOldJsdocCall1() {
    Node root = parse("foo/** don't attach */(1, 2);");
    Node call = root.getFirstFirstChild();
    assertThat(call.getSecondChild().getJSDocInfo()).isNull();
  }

  @Test
  public void testOldJsdocCall2() {
    Node root = parse("foo(/** attach */ 1, 2);");
    Node call = root.getFirstFirstChild();
    assertThat(call.getSecondChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocCall3() {
    // Incorrect attachment b/c the parser doesn't preserve comma positions.
    // TODO(dimvar): if this case comes up often, modify the parser to
    // remember comma positions for function decls and calls and fix the bug.
    Node root = parse("foo(1 /** attach to 2nd parameter */, 2);");
    Node call = root.getFirstFirstChild();
    assertThat(call.getChildAtIndex(2).getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocCall4() {
    Node root = parse("foo(1, 2 /** don't attach */);");
    Node call = root.getFirstFirstChild();
    assertThat(call.getChildAtIndex(2).getJSDocInfo()).isNull();
  }

  @Test
  public void testOldJsdocCall5() {
    Node root = parse("/** attach */ x(); function f() {}");
    assertThat(root.getFirstFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocCall6() {
    Node root = parse("(function f() { /** attach */ var x = 1; })();");
    Node func = root.getFirstFirstChild().getFirstChild();
    assertThat(func.isFunction()).isTrue();
    assertThat(func.getChildAtIndex(2).getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocCall7() {
    Node root = parse("/** attach */ obj.prop();");
    assertThat(root.getFirstFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocCall8() {
    Node root = parse("/** attach */ (obj).prop();");
    Node getProp = root.getFirstFirstChild().getFirstChild();
    assertThat(getProp.isGetProp()).isTrue();
    assertThat(getProp.getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  @Ignore
  public void testOldJsdocComma1() {
    Node root = parse("(/** attach */ x, y, z);");
    Node leftComma = root.getFirstFirstChild().getFirstChild();
    assertNode(leftComma).hasToken(Token.COMMA);
    assertThat(leftComma.getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  @Ignore
  public void testOldJsdocComma2() {
    Node root = parse("(x /** don't attach */, y, z);");
    Node leftComma = root.getFirstFirstChild().getFirstChild();
    assertThat(leftComma.getFirstChild().getJSDocInfo()).isNull();
    assertThat(leftComma.getLastChild().getJSDocInfo()).isNull();
  }

  @Test
  public void testOldJsdocComma3() {
    Node root = parse("(x, y, /** attach */ z);");
    Node rightComma = root.getFirstFirstChild();
    assertThat(rightComma.getLastChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocContinue() {
    Node root = parse("FOO: for (;;) { continue /** don't attach */ FOO; }");
    Node forStm = root.getFirstChild().getLastChild();
    Node cont = forStm.getChildAtIndex(3).getFirstChild();
    assertNode(cont).hasToken(Token.CONTINUE);
    assertThat(cont.getJSDocInfo()).isNull();
    assertThat(cont.getFirstChild().getJSDocInfo()).isNull();
  }

  @Test
  @Ignore
  public void testOldJsdocDoLoop1() {
    Node root = parse("do /** don't attach */ {} while (x);");
    Node doBlock = root.getFirstFirstChild();
    assertThat(doBlock.getJSDocInfo()).isNull();
  }

  @Test
  @Ignore
  public void testOldJsdocDoLoop2() {
    Node root = parse("do {} /** don't attach */ while (x);");
    Node whileExp = root.getFirstChild().getLastChild();
    assertThat(whileExp.getJSDocInfo()).isNull();
  }

  @Test
  public void testOldJsdocDot() {
    Node root = parse("/** attach */a.b;");
    assertThat(root.getFirstFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocDot2() {
    Node root = parse(
        "/** attach */\n" +
        "// test\n" +
        "a.b = {};");
    assertThat(root.getFirstFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  @Ignore
  public void testOldJsdocForInLoop1() {
    Node root = parse("for /** don't attach */ (var p in {}) {}");
    Node fil = root.getFirstChild();
    assertThat(fil.getJSDocInfo()).isNull();
    assertThat(fil.getFirstChild().getJSDocInfo()).isNull();
    assertThat(fil.getSecondChild().getJSDocInfo()).isNull();
    assertThat(fil.getChildAtIndex(2).getJSDocInfo()).isNull();
  }

  @Test
  public void testOldJsdocForInLoop2() {
    Node root = parse("for (/** attach */ var p in {}) {}");
    Node fil = root.getFirstChild();
    assertThat(fil.getJSDocInfo()).isNull();
    assertThat(fil.getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocForInLoop3() {
    Node root = parse("for (var p in /** attach */ {}) {}");
    Node fil = root.getFirstChild();
    assertThat(fil.getJSDocInfo()).isNull();
    assertThat(fil.getSecondChild().getJSDocInfo()).isNotNull();
  }

  @Test
  @Ignore
  public void testOldJsdocForInLoop4() {
    Node root = parse("for (var p in {}) /** don't attach */ {}");
    Node fil = root.getFirstChild();
    assertThat(fil.getJSDocInfo()).isNull();
    assertThat(fil.getChildAtIndex(2).getJSDocInfo()).isNull();
  }

  @Test
  @Ignore
  public void testOldJsdocForInLoop5() {
    Node root = parse("for (var p /** don't attach */ in {}) {}");
    Node fil = root.getFirstChild();
    assertThat(fil.getJSDocInfo()).isNull();
    assertThat(fil.getFirstChild().getJSDocInfo()).isNull();
    assertThat(fil.getSecondChild().getJSDocInfo()).isNull();
    assertThat(fil.getChildAtIndex(2).getJSDocInfo()).isNull();
  }

  @Test
  @Ignore
  public void testOldJsdocForInLoop6() {
    Node root = parse("for (var p in {} /** don't attach */) {}");
    Node fil = root.getFirstChild();
    assertThat(fil.getJSDocInfo()).isNull();
    assertThat(fil.getFirstChild().getJSDocInfo()).isNull();
    assertThat(fil.getSecondChild().getJSDocInfo()).isNull();
    assertThat(fil.getChildAtIndex(2).getJSDocInfo()).isNull();
  }

  @Test
  @Ignore
  public void testOldJsdocForLoop1() {
    Node root = parse("for /** don't attach */ (i = 0; i < 5; i++) {}");
    Node fl = root.getFirstChild();
    assertThat(fl.getJSDocInfo()).isNull();
    assertThat(fl.getFirstChild().getJSDocInfo()).isNull();
    assertThat(fl.getSecondChild().getJSDocInfo()).isNull();
    assertThat(fl.getChildAtIndex(2).getJSDocInfo()).isNull();
    assertThat(fl.getChildAtIndex(3).getJSDocInfo()).isNull();
  }

  @Test
  public void testOldJsdocForLoop2() {
    Node root = parse("for (/** attach */ i = 0; i < 5; i++) {}");
    Node fl = root.getFirstChild();
    assertThat(fl.getJSDocInfo()).isNull();
    assertThat(fl.getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  @Ignore
  public void testOldJsdocForLoop3() {
    Node root = parse("for (i /** don't attach */ = 0; i < 5; i++) {}");
    Node fl = root.getFirstChild();
    assertThat(fl.getJSDocInfo()).isNull();
    Node init = fl.getFirstChild();
    assertThat(init.getFirstChild().getJSDocInfo()).isNull();
    assertThat(init.getLastChild().getJSDocInfo()).isNull();
    assertThat(fl.getSecondChild().getJSDocInfo()).isNull();
  }

  @Test
  public void testOldJsdocForLoop4() {
    Node root = parse("for (i = /** attach */ 0; i < 5; i++) {}");
    Node fl = root.getFirstChild();
    Node init = fl.getFirstChild();
    assertThat(init.getFirstChild().getJSDocInfo()).isNull();
    assertThat(init.getLastChild().getJSDocInfo()).isNotNull();
  }

  @Test
  @Ignore
  public void testOldJsdocForLoop5() {
    Node root = parse("for (i = 0 /** don't attach */; i < 5; i++) {}");
    Node fl = root.getFirstChild();
    assertThat(fl.getJSDocInfo()).isNull();
    Node init = fl.getFirstChild();
    assertThat(init.getLastChild().getJSDocInfo()).isNull();
    assertThat(fl.getSecondChild().getJSDocInfo()).isNull();
  }

  @Test
  @Ignore
  public void testOldJsdocForLoop6() {
    Node root = parse("for (i = 0; /** attach */ i < 5; i++) {}");
    Node fl = root.getFirstChild();
    assertThat(fl.getJSDocInfo()).isNull();
    Node cond = fl.getSecondChild();
    assertThat(cond.getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocForLoop7() {
    Node root = parse("for (i = 0; i < /** attach */ 5; i++) {}");
    Node fl = root.getFirstChild();
    assertThat(fl.getJSDocInfo()).isNull();
    Node cond = fl.getSecondChild();
    assertThat(cond.getLastChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocForLoop8() {
    Node root = parse("for (i = 0; i < 5; /** attach */ i++) {}");
    Node fl = root.getFirstChild();
    assertThat(fl.getJSDocInfo()).isNull();
    assertThat(fl.getChildAtIndex(2).getJSDocInfo()).isNotNull();
  }

  @Test
  @Ignore
  public void testOldJsdocForLoop9() {
    Node root = parse("for (i = 0; i < 5; i++ /** don't attach */) {}");
    Node fl = root.getFirstChild();
    assertThat(fl.getJSDocInfo()).isNull();
    assertThat(fl.getChildAtIndex(2).getJSDocInfo()).isNull();
    assertThat(fl.getChildAtIndex(3).getJSDocInfo()).isNull();
  }

  @Test
  @Ignore
  public void testOldJsdocForLoop10() {
    Node root = parse("for (i = 0; i < 5; i++) /** don't attach */ {}");
    Node fl = root.getFirstChild();
    assertThat(fl.getJSDocInfo()).isNull();
    assertThat(fl.getChildAtIndex(3).getJSDocInfo()).isNull();
  }

  @Test
  public void testOldJsdocForLoop11() {
    Node root = parse("for (/** attach */ var i = 0; i < 5; i++) {}");
    Node fl = root.getFirstChild();
    assertThat(fl.getJSDocInfo()).isNull();
    assertThat(fl.getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  @Ignore
  public void testOldJsdocForLoop12() {
    Node root = parse("for (var i = 0 /** dont attach */; i < 5; i++) {}");
    Node fl = root.getFirstChild();
    assertThat(fl.getJSDocInfo()).isNull();
    assertThat(fl.getFirstChild().getJSDocInfo()).isNull();
    assertThat(fl.getSecondChild().getJSDocInfo()).isNull();
  }

  @Test
  public void testOldJsdocFun1() {
    Node root = parse("function f(/** string */ e) {}");
    Node fun = root.getFirstChild();
    Node params = fun.getSecondChild();
    assertThat(params.getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocFun2() {
    Node root = parse("(function() {/** don't attach */})()");
    Node call = root.getFirstFirstChild();
    assertThat(call.getFirstChild().getJSDocInfo()).isNull();
  }

  @Test
  public void testOldJsdocFun3() {
    Node root = parse("function /** string */ f (e) {}");
    Node fun = root.getFirstChild();
    assertThat(fun.getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocFun4() {
    Node root = parse("f = /** attach */ function(e) {};");
    Node assign = root.getFirstFirstChild();
    assertThat(assign.getLastChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocFun5() {
    Node root = parse("x = 1; /** attach */ function f(e) {}");
    assertThat(root.getLastChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocFun6() {
    Node root = parse("function f() { /** attach */ function Foo(){} }");
    Node innerFun = root.getFirstChild().getLastChild().getFirstChild();
    assertThat(innerFun.getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocFun7() {
    Node root = parse("(function f() { /** attach */function Foo(){} })();");
    Node outerFun = root.getFirstFirstChild().getFirstChild();
    assertThat(outerFun.getLastChild().getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocGetElem1() {
    Node root = parse("(/** attach */ {})['prop'];");
    Node getElem = root.getFirstFirstChild();
    assertThat(getElem.getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  @Ignore
  public void testOldJsdocGetElem2() {
    Node root = parse("({} /** don't attach */)['prop'];");
    Node getElem = root.getFirstFirstChild();
    assertThat(getElem.getFirstChild().getJSDocInfo()).isNull();
    assertThat(getElem.getLastChild().getJSDocInfo()).isNull();
  }

  @Test
  public void testOldJsdocGetElem3() {
    Node root = parse("({})[/** attach */ 'prop'];");
    Node getElem = root.getFirstFirstChild();
    assertThat(getElem.getLastChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocGetProp1() {
    Node root = parse("(/** attach */ {}).prop;");
    Node getProp = root.getFirstFirstChild();
    assertThat(getProp.getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocGetProp2() {
    Node root = parse("/** attach */ ({}).prop;");
    Node getProp = root.getFirstFirstChild();
    assertThat(getProp.getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocGetProp3() {
    Node root = parse("/** attach */ obj.prop;");
    Node getProp = root.getFirstFirstChild();
    assertThat(getProp.getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocGetter1() {
    mode = LanguageMode.ECMASCRIPT5;
    Node root = parse("({/** attach */ get foo() {}});");
    Node objlit = root.getFirstFirstChild();
    assertThat(objlit.getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocGetter2() {
    mode = LanguageMode.ECMASCRIPT5;
    Node root = parse("({/** attach */ get 1() {}});");
    Node objlit = root.getFirstFirstChild();
    assertThat(objlit.getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocGetter3() {
    mode = LanguageMode.ECMASCRIPT5;
    Node root = parse("({/** attach */ get 'foo'() {}});");
    Node objlit = root.getFirstFirstChild();
    assertThat(objlit.getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testJsdocHook1() {
     Node root = parse("/** attach */ (true) ? 1 : 2;");
     Node hook = root.getFirstFirstChild();
     assertThat(hook.getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  @Ignore
  public void testOldJsdocHook1() {
    Node root = parse("/** attach */ true ? 1 : 2;");
    Node hook = root.getFirstFirstChild();
    assertThat(hook.getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  @Ignore
  public void testOldJsdocHook2() {
    Node root = parse("true /** don't attach */ ? 1 : 2;");
    Node hook = root.getFirstFirstChild();
    assertThat(hook.getFirstChild().getJSDocInfo()).isNull();
    assertThat(hook.getSecondChild().getJSDocInfo()).isNull();
  }

  @Test
  public void testOldJsdocHook3() {
    Node root = parse("true ? /** attach */ 1 : 2;");
    Node hook = root.getFirstFirstChild();
    assertThat(hook.getSecondChild().getJSDocInfo()).isNotNull();
  }

  @Test
  @Ignore
  public void testOldJsdocHook4() {
    Node root = parse("true ? 1 /** don't attach */ : 2;");
    Node hook = root.getFirstFirstChild();
    assertThat(hook.getSecondChild().getJSDocInfo()).isNull();
    assertThat(hook.getChildAtIndex(2).getJSDocInfo()).isNull();
  }

  @Test
  public void testOldJsdocHook5() {
    Node root = parse("true ? 1 : /** attach */ 2;");
    Node hook = root.getFirstFirstChild();
    assertThat(hook.getChildAtIndex(2).getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocIf1() {
    Node root = parse("if (/** attach */ x) {}");
    assertThat(root.getFirstFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  @Ignore
  public void testOldJsdocIf2() {
    Node root = parse("if (x) /** don't attach */ {}");
    assertThat(root.getFirstChild().getSecondChild().getJSDocInfo()).isNull();
  }

  @Test
  @Ignore
  public void testOldJsdocIf3() {
    Node root = parse("if (x) {} else /** don't attach */ {}");
    assertThat(root.getFirstChild().getChildAtIndex(2).getJSDocInfo()).isNull();
  }

  @Test
  @Ignore
  public void testOldJsdocIf4() {
    Node root = parse("if (x) {} /** don't attach */ else {}");
    assertThat(root.getFirstChild().getChildAtIndex(2).getJSDocInfo()).isNull();
  }

  @Test
  @Ignore
  public void testOldJsdocLabeledStm1() {
    Node root = parse("/** attach */ FOO: if (x) {};");
    assertThat(root.getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  @Ignore
  public void testOldJsdocLabeledStm2() {
    Node root = parse("FOO: /** don't attach */ if (x) {};");
    assertThat(root.getFirstChild().getJSDocInfo()).isNull();
    assertThat(root.getFirstChild().getLastChild().getJSDocInfo()).isNull();
  }

  @Test
  public void testOldJsdocNew1() {
    Node root = parse("/** attach */ new Foo();");
    Node newexp = root.getFirstFirstChild();
    assertThat(newexp.getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocNew2() {
    Node root = parse("new /** don't attach */ Foo();");
    Node newexp = root.getFirstFirstChild();
    assertThat(newexp.getJSDocInfo()).isNull();
  }

  @Test
  @Ignore
  public void testOldJsdocObjLit1() {
    Node root = parse("({/** attach */ 1: 2});");
    Node objlit = root.getFirstFirstChild();
    assertThat(objlit.getFirstFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocObjLit2() {
    Node root = parse("({1: /** attach */ 2, 3: 4});");
    Node objlit = root.getFirstFirstChild();
    assertThat(objlit.getFirstChild().getLastChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocObjLit3() {
    Node root = parse("({'1': /** attach */ (foo())});");
    Node objlit = root.getFirstFirstChild();
    assertThat(objlit.getFirstChild().getLastChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocPostfix1() {
    Node root = parse("/** attach */ (x)++;");
    Node unary = root.getFirstFirstChild();
    assertThat(unary.getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocPostfix2() {
    Node root = parse("/** attach */ x++;");
    Node unary = root.getFirstFirstChild();
    assertThat(unary.getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocReturn1() {
    Node root = parse("function f(x) { return /** string */ x; }");
    Node ret = root.getFirstChild().getLastChild().getFirstChild();
    assertThat(ret.getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  @Ignore
  public void testOldJsdocReturn2() {
    Node root = parse("function f(x) { /** string */ return x; }");
    Node ret = root.getFirstChild().getLastChild().getFirstChild();
    assertThat(ret.getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocReturn3() {
    // The first comment should be attached to the parenthesis, and the
    // second comment shouldn't be attached to any local node.
    // There used to be a bug where the second comment would get attached.
    Node root = parse("function f(e) { return /** 1 */(g(1 /** 2 */)); }\n");
    Node ret = root.getFirstChild().getLastChild().getFirstChild();
    assertThat(ret.getFirstChild().getJSDocInfo()).isNotNull();
    assertThat(ret.getFirstChild().getLastChild().getJSDocInfo()).isNull();
  }

  @Test
  public void testOldJsdocSetter() {
    mode = LanguageMode.ECMASCRIPT5;
    Node root = parse("({/** attach */ set foo(x) {}});");
    Node objlit = root.getFirstFirstChild();
    assertThat(objlit.getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocScript1() {
    Node root = parse("{ 1; /** attach */ 2; }");
    Node block = root.getFirstChild();
    assertThat(block.getLastChild().getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocScript2() {
    Node root = parse("1; /** attach */ 2;");
    assertThat(root.getLastChild().getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocScript3() {
    Node root = parse("1;/** attach */ function f(){}");
    assertThat(root.getLastChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocSwitch1() {
    Node root = parse("switch /** attach */ (x) {}");
    Node sw = root.getFirstChild();
    assertThat(sw.getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  @Ignore
  public void testOldJsdocSwitch2() {
    Node root = parse("switch (x) { /** don't attach */ case 1: ; }");
    Node sw = root.getFirstChild();
    assertThat(sw.getSecondChild().getJSDocInfo()).isNull();
  }

  @Test
  public void testOldJsdocSwitch3() {
    Node root = parse("switch (x) { case /** attach */ 1: ; }");
    Node sw = root.getFirstChild();
    assertThat(sw.getSecondChild().getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocSwitch4() {
    Node root = parse("switch (x) { case 1: /** don't attach */ {}; }");
    Node sw = root.getFirstChild();
    assertThat(sw.getSecondChild().getLastChild().getJSDocInfo()).isNull();
  }

  @Test
  public void testOldJsdocSwitch5() {
    Node root = parse("switch (x) { default: /** don't attach */ {}; }");
    Node sw = root.getFirstChild();
    assertThat(sw.getSecondChild().getLastChild().getJSDocInfo()).isNull();
  }

  @Test
  public void testOldJsdocSwitch6() {
    Node root = parse("switch (x) { case 1: /** don't attach */ }");
    Node sw = root.getFirstChild();
    assertThat(sw.getSecondChild().getLastChild().getJSDocInfo()).isNull();
  }

  @Test
  public void testOldJsdocSwitch7() {
    Node root = parse(
        "switch (x) {" +
        "  case 1: " +
        "    /** attach */ y;" +
        "    /** attach */ z;" +
        "}");
    Node sw = root.getFirstChild();
    Node caseBody = sw.getSecondChild().getLastChild();
    assertThat(caseBody.getFirstFirstChild().getJSDocInfo()).isNotNull();
    assertThat(caseBody.getSecondChild().getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocThrow() {
    Node root = parse("throw /** attach */ new Foo();");
    assertThat(root.getFirstFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocTryCatch1() {
    Node root = parse("try {} catch (/** attach */ e) {}");
    Node catchNode = root.getFirstChild().getLastChild().getFirstChild();
    assertThat(catchNode.getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  @Ignore
  public void testOldJsdocTryCatch2() {
    Node root = parse("try {} /** don't attach */ catch (e) {}");
    Node catchNode = root.getFirstChild().getLastChild().getFirstChild();
    assertThat(catchNode.getJSDocInfo()).isNull();
    assertThat(catchNode.getFirstChild().getJSDocInfo()).isNull();
  }

  @Test
  public void testOldJsdocTryFinally() {
    Node root = parse("try {} finally { /** attach */ e; }");
    Node finallyBlock = root.getFirstChild().getLastChild();
    assertThat(finallyBlock.getFirstFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocUnary() {
    Node root = parse("!(/** attach */ x);");
    Node exp = root.getFirstFirstChild();
    assertThat(exp.getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocVar1() {
    Node root = parse("/** attach */ var a;");
    assertThat(root.getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocVar2() {
    Node root = parse("var a = /** attach */ (x);");
    Node var = root.getFirstChild();
    assertThat(var.getFirstFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocVar3() {
    Node root = parse("var a = (/** attach */ {});");
    Node var = root.getFirstChild();
    assertThat(var.getFirstFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocVar4() {
    Node root = parse("var /** number */ a = x;");
    Node var = root.getFirstChild();
    assertThat(var.getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocVar5() {
    Node root = parse("x = 1; /** attach */ var y = 5;");
    Node var = root.getLastChild();
    assertThat(var.getJSDocInfo()).isNotNull();
  }

  @Test
  @Ignore
  public void testOldJsdocWhile1() {
    Node root = parse("while (x) /** don't attach */ {}");
    Node wh = root.getFirstChild();
    assertThat(wh.getLastChild().getJSDocInfo()).isNull();
  }

  @Test
  public void testOldJsdocWhile2() {
    Node root = parse("while /** attach */ (x) {}");
    Node wh = root.getFirstChild();
    assertThat(wh.getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  @Ignore
  public void testOldJsdocWhile3() {
    Node root = parse("while (x /** don't attach */) {}");
    Node wh = root.getFirstChild();
    assertThat(wh.getFirstChild().getJSDocInfo()).isNull();
    assertThat(wh.getLastChild().getJSDocInfo()).isNull();
  }

  @Test
  public void testOldJsdocWith1() {
    Node root = parse("with (/** attach */ obj) {};");
    Node with = root.getFirstChild();
    assertThat(with.getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  @Ignore
  public void testOldJsdocWith2() {
    Node root = parse("with (obj) /** don't attach */ {};");
    Node with = root.getFirstChild();
    assertThat(with.getLastChild().getJSDocInfo()).isNull();
  }

  @Test
  @Ignore
  public void testOldJsdocWith3() {
    Node root = parse("with (obj /** don't attach */) {};");
    Node with = root.getFirstChild();
    assertThat(with.getFirstChild().getJSDocInfo()).isNull();
    assertThat(with.getLastChild().getJSDocInfo()).isNull();
  }

  @Test
  public void testOldJsdocWith4() {
    Node root = parse(
        "/** @suppress {with} */ with (context) {\n" +
        "  eval('[' + expr + ']');\n" +
        "}\n");
    assertThat(root.getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocManyComments1() {
    Node root = parse(
        "function /** number */ f(/** number */ x, /** number */ y) {\n" +
        "  return x + y;\n" +
        "}");
    Node fun = root.getFirstChild();
    assertThat(fun.getFirstChild().getJSDocInfo()).isNotNull();
    assertThat(fun.getSecondChild().getFirstChild().getJSDocInfo()).isNotNull();
    assertThat(fun.getSecondChild().getLastChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocManyComments2() {
    Node root = parse("var /** number */ x = 1; var /** string */ y = 2;");
    assertThat(root.getFirstFirstChild().getJSDocInfo()).isNotNull();
    assertThat(root.getLastChild().getFirstChild().getJSDocInfo()).isNotNull();
  }

  @Test
  public void testOldJsdocManyCommentsOnOneNode() {
    // When many jsdocs could attach to a node, we pick the last one.
    Node root = parse("var x; /** foo */ /** bar */ function f() {}");
    JSDocInfo info = root.getLastChild().getJSDocInfo();
    assertThat(info).isNotNull();
    assertThat(info.getOriginalCommentString()).isEqualTo("/** bar */");
  }

  @Test
  public void testInlineInExport() {
    mode = LanguageMode.ECMASCRIPT6;
    Node root = parse("export var /** number */ x;");
    Node moduleBody = root.getFirstChild();
    Node exportNode = moduleBody.getFirstChild();
    Node varNode = exportNode.getFirstChild();
    assertThat(varNode.getFirstChild().getJSDocInfo()).isNotNull();
  }

  private Node parse(String source, String... warnings) {
    TestErrorReporter testErrorReporter = new TestErrorReporter(null, warnings);
    Config config =
        ParserRunner.createConfig(
            mode,
            Config.JsDocParsing.INCLUDE_DESCRIPTIONS_NO_WHITESPACE,
            Config.RunMode.KEEP_GOING,
            null,
            true,
            StrictMode.SLOPPY);
    Node script =
        ParserRunner.parse(
                new SimpleSourceFile("input", SourceKind.STRONG), source, config, testErrorReporter)
            .ast;

    // verifying that all warnings were seen
    testErrorReporter.assertHasEncounteredAllErrors();
    testErrorReporter.assertHasEncounteredAllWarnings();

    return script;
  }
}
