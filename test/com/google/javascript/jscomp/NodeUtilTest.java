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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.TernaryValue;

import junit.framework.TestCase;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Tests for NodeUtil
 */
public final class NodeUtilTest extends TestCase {

  private static Node parse(String js) {
    Compiler compiler = new Compiler();
    compiler.initCompilerOptionsIfTesting();
    compiler.getOptions().setLanguageIn(LanguageMode.ECMASCRIPT6);
    Node n = compiler.parseTestCode(js);
    assertEquals(0, compiler.getErrorCount());
    return n;
  }

  static Node getNode(String js) {
    Node root = parse("var a=(" + js + ");");
    Node expr = root.getFirstChild();
    Node var = expr.getFirstChild();
    return var.getFirstChild();
  }

  public void testIsLiteralOrConstValue() {
    assertLiteralAndImmutable(getNode("10"));
    assertLiteralAndImmutable(getNode("-10"));
    assertLiteralButNotImmutable(getNode("[10, 20]"));
    assertLiteralButNotImmutable(getNode("{'a': 20}"));
    assertLiteralButNotImmutable(getNode("[10, , 1.0, [undefined], 'a']"));
    assertLiteralButNotImmutable(getNode("/abc/"));
    assertLiteralAndImmutable(getNode("\"string\""));
    assertLiteralAndImmutable(getNode("'aaa'"));
    assertLiteralAndImmutable(getNode("null"));
    assertLiteralAndImmutable(getNode("undefined"));
    assertLiteralAndImmutable(getNode("void 0"));
    assertNotLiteral(getNode("abc"));
    assertNotLiteral(getNode("[10, foo(), 20]"));
    assertNotLiteral(getNode("foo()"));
    assertNotLiteral(getNode("c + d"));
    assertNotLiteral(getNode("{'a': foo()}"));
    assertNotLiteral(getNode("void foo()"));
  }

  public void assertLiteralAndImmutable(Node n) {
    assertTrue(NodeUtil.isLiteralValue(n, true));
    assertTrue(NodeUtil.isLiteralValue(n, false));
    assertTrue(NodeUtil.isImmutableValue(n));
  }

  public void assertLiteralButNotImmutable(Node n) {
    assertTrue(NodeUtil.isLiteralValue(n, true));
    assertTrue(NodeUtil.isLiteralValue(n, false));
    assertFalse(NodeUtil.isImmutableValue(n));
  }

  public void assertNotLiteral(Node n) {
    assertFalse(NodeUtil.isLiteralValue(n, true));
    assertFalse(NodeUtil.isLiteralValue(n, false));
    assertFalse(NodeUtil.isImmutableValue(n));
  }

  public void testGetBooleanValue() {
    assertPureBooleanTrue("true");
    assertPureBooleanTrue("10");
    assertPureBooleanTrue("'0'");
    assertPureBooleanTrue("/a/");
    assertPureBooleanTrue("{}");
    assertPureBooleanTrue("[]");
    assertPureBooleanFalse("false");
    assertPureBooleanFalse("null");
    assertPureBooleanFalse("0");
    assertPureBooleanFalse("''");
    assertPureBooleanFalse("undefined");
    assertPureBooleanFalse("void 0");
    assertPureBooleanUnknown("void foo()");
    assertPureBooleanUnknown("b");
    assertPureBooleanUnknown("-'0.0'");

    // Known but getBooleanValue return false for expressions with side-effects
    assertPureBooleanUnknown("{a:foo()}");
    assertPureBooleanUnknown("[foo()]");

    assertPureBooleanTrue("`definiteLength`");
    assertPureBooleanFalse("``");
    assertPureBooleanUnknown("`${indefinite}Length`");

    assertPureBooleanTrue("class Klass{}");
    assertPureBooleanTrue("new Date()");
  }

  private void assertPureBooleanTrue(String val) {
    assertEquals(TernaryValue.TRUE, NodeUtil.getPureBooleanValue(getNode(val)));
  }

  private void assertPureBooleanFalse(String val) {
    assertEquals(
        TernaryValue.FALSE, NodeUtil.getPureBooleanValue(getNode(val)));
  }

  private void assertPureBooleanUnknown(String val) {
    assertEquals(
        TernaryValue.UNKNOWN, NodeUtil.getPureBooleanValue(getNode(val)));
  }

  public void testGetExpressionBooleanValue() {
    assertImpureBooleanTrue("a=true");
    assertImpureBooleanFalse("a=false");

    assertImpureBooleanTrue("a=(false,true)");
    assertImpureBooleanFalse("a=(true,false)");

    assertImpureBooleanTrue("a=(false || true)");
    assertImpureBooleanFalse("a=(true && false)");

    assertImpureBooleanTrue("a=!(true && false)");

    assertImpureBooleanTrue("a,true");
    assertImpureBooleanFalse("a,false");

    assertImpureBooleanTrue("true||false");
    assertImpureBooleanFalse("false||false");

    assertImpureBooleanTrue("true&&true");
    assertImpureBooleanFalse("true&&false");

    assertImpureBooleanFalse("!true");
    assertImpureBooleanTrue("!false");
    assertImpureBooleanTrue("!''");

    // Assignment ops other than ASSIGN are unknown.
    assertImpureBooleanUnknown("a *= 2");

    // Complex expressions that contain anything other then "=", ",", or "!" are
    // unknown.
    assertImpureBooleanUnknown("2 + 2");

    assertImpureBooleanTrue("a=1");
    assertImpureBooleanTrue("a=/a/");
    assertImpureBooleanTrue("a={}");

    assertImpureBooleanTrue("true");
    assertImpureBooleanTrue("10");
    assertImpureBooleanTrue("'0'");
    assertImpureBooleanTrue("/a/");
    assertImpureBooleanTrue("{}");
    assertImpureBooleanTrue("[]");
    assertImpureBooleanFalse("false");
    assertImpureBooleanFalse("null");
    assertImpureBooleanFalse("0");
    assertImpureBooleanFalse("''");
    assertImpureBooleanFalse("undefined");
    assertImpureBooleanFalse("void 0");
    assertImpureBooleanFalse("void foo()");

    assertImpureBooleanTrue("a?true:true");
    assertImpureBooleanFalse("a?false:false");
    assertImpureBooleanUnknown("a?true:false");
    assertImpureBooleanUnknown("a?true:foo()");

    assertImpureBooleanUnknown("b");
    assertImpureBooleanUnknown("-'0.0'");

    assertImpureBooleanTrue("{a:foo()}");
    assertImpureBooleanTrue("[foo()]");
    assertImpureBooleanTrue("new Date()");
  }

  private void assertImpureBooleanTrue(String val) {
    assertEquals(TernaryValue.TRUE,
        NodeUtil.getImpureBooleanValue(getNode(val)));
  }

  private void assertImpureBooleanFalse(String val) {
    assertEquals(TernaryValue.FALSE,
        NodeUtil.getImpureBooleanValue(getNode(val)));
  }

  private void assertImpureBooleanUnknown(String val) {
    assertEquals(TernaryValue.UNKNOWN,
        NodeUtil.getImpureBooleanValue(getNode(val)));
  }

  public void testGetStringValue() {
    assertEquals("true", NodeUtil.getStringValue(getNode("true")));
    assertEquals("10", NodeUtil.getStringValue(getNode("10")));
    assertEquals("1", NodeUtil.getStringValue(getNode("1.0")));
    assertEquals("0", NodeUtil.getStringValue(getNode("'0'")));
    assertEquals(null, NodeUtil.getStringValue(getNode("/a/")));
    assertEquals("[object Object]", NodeUtil.getStringValue(getNode("{}")));
    assertThat(NodeUtil.getStringValue(getNode("[]"))).isEmpty();
    assertEquals("false", NodeUtil.getStringValue(getNode("false")));
    assertEquals("null", NodeUtil.getStringValue(getNode("null")));
    assertEquals("0", NodeUtil.getStringValue(getNode("0")));
    assertThat(NodeUtil.getStringValue(getNode("''"))).isEmpty();
    assertEquals("undefined", NodeUtil.getStringValue(getNode("undefined")));
    assertEquals("undefined", NodeUtil.getStringValue(getNode("void 0")));
    assertEquals("undefined", NodeUtil.getStringValue(getNode("void foo()")));

    assertEquals("NaN", NodeUtil.getStringValue(getNode("NaN")));
    assertEquals("Infinity", NodeUtil.getStringValue(getNode("Infinity")));
    assertEquals(null, NodeUtil.getStringValue(getNode("x")));
  }

  public void testGetArrayStringValue() {
    assertThat(NodeUtil.getStringValue(getNode("[]"))).isEmpty();
    assertThat(NodeUtil.getStringValue(getNode("['']"))).isEmpty();
    assertThat(NodeUtil.getStringValue(getNode("[null]"))).isEmpty();
    assertThat(NodeUtil.getStringValue(getNode("[undefined]"))).isEmpty();
    assertThat(NodeUtil.getStringValue(getNode("[void 0]"))).isEmpty();
    assertEquals("NaN", NodeUtil.getStringValue(getNode("[NaN]")));
    assertEquals(",", NodeUtil.getStringValue(getNode("[,'']")));
    assertEquals(",,", NodeUtil.getStringValue(getNode("[[''],[''],['']]")));
    assertEquals("1,2", NodeUtil.getStringValue(getNode("[[1.0],[2.0]]")));
    assertEquals(null, NodeUtil.getStringValue(getNode("[a]")));
    assertEquals(null, NodeUtil.getStringValue(getNode("[1,a]")));
  }

  public void testIsObjectLiteralKey1() throws Exception {
    testIsObjectLiteralKey(
      parseExpr("({})"), false);
    testIsObjectLiteralKey(
      parseExpr("a"), false);
    testIsObjectLiteralKey(
      parseExpr("'a'"), false);
    testIsObjectLiteralKey(
      parseExpr("1"), false);
    testIsObjectLiteralKey(
      parseExpr("({a: 1})").getFirstChild(), true);
    testIsObjectLiteralKey(
      parseExpr("({1: 1})").getFirstChild(), true);
    testIsObjectLiteralKey(
      parseExpr("({get a(){}})").getFirstChild(), true);
    testIsObjectLiteralKey(
      parseExpr("({set a(b){}})").getFirstChild(), true);
  }

  private Node parseExpr(String js) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT5);
    compiler.initOptions(options);
    Node root = compiler.parseTestCode(js);
    return root.getFirstFirstChild();
  }

  private void testIsObjectLiteralKey(Node node, boolean expected) {
    assertEquals(expected, NodeUtil.isObjectLitKey(node));
  }

  public void testGetFunctionName1() throws Exception {
    Compiler compiler = new Compiler();
    Node parent = compiler.parseTestCode("function name(){}");

    testGetFunctionName(parent.getFirstChild(), "name");
  }

  public void testGetFunctionName2() throws Exception {
    Compiler compiler = new Compiler();
    Node parent = compiler.parseTestCode("var name = function(){}")
        .getFirstFirstChild();

    testGetFunctionName(parent.getFirstChild(), "name");
  }

  public void testGetFunctionName3() throws Exception {
    Compiler compiler = new Compiler();
    Node parent = compiler.parseTestCode("qualified.name = function(){}")
        .getFirstFirstChild();

    testGetFunctionName(parent.getLastChild(), "qualified.name");
  }

  public void testGetFunctionName4() throws Exception {
    Compiler compiler = new Compiler();
    Node parent = compiler.parseTestCode("var name2 = function name1(){}")
        .getFirstFirstChild();

    testGetFunctionName(parent.getFirstChild(), "name2");
  }

  public void testGetFunctionName5() throws Exception {
    Compiler compiler = new Compiler();
    Node n = compiler.parseTestCode("qualified.name2 = function name1(){}");
    Node parent = n.getFirstFirstChild();

    testGetFunctionName(parent.getLastChild(), "qualified.name2");
  }

  public void testGetBestFunctionName1() throws Exception {
    Compiler compiler = new Compiler();
    Node parent = compiler.parseTestCode("function func(){}");

    assertEquals("func",
        NodeUtil.getNearestFunctionName(parent.getFirstChild()));
  }

  public void testGetBestFunctionName2() throws Exception {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT6);
    compiler.initOptions(options);

    Node parent = compiler.parseTestCode("var obj = {memFunc(){}}")
        .getFirstFirstChild().getFirstFirstChild();

    assertEquals("memFunc",
        NodeUtil.getNearestFunctionName(parent.getLastChild()));
  }


  private void testGetFunctionName(Node function, String name) {
    assertEquals(Token.FUNCTION, function.getType());
    assertEquals(name, NodeUtil.getName(function));
  }

  public void testContainsFunctionDeclaration() {
    assertTrue(NodeUtil.containsFunction(
                   getNode("function foo(){}")));
    assertTrue(NodeUtil.containsFunction(
                   getNode("(b?function(){}:null)")));

    assertFalse(NodeUtil.containsFunction(
                   getNode("(b?foo():null)")));
    assertFalse(NodeUtil.containsFunction(
                    getNode("foo()")));
  }

  private void assertSideEffect(boolean se, String js) {
    Node n = parse(js);
    assertEquals(se, NodeUtil.mayHaveSideEffects(n.getFirstChild()));
  }

  private void assertSideEffect(boolean se, String js, boolean globalRegExp) {
    Node n = parse(js);
    Compiler compiler = new Compiler();
    compiler.setHasRegExpGlobalReferences(globalRegExp);
    assertEquals(se, NodeUtil.mayHaveSideEffects(n.getFirstChild(), compiler));
  }

  public void testMayHaveSideEffects() {
    assertSideEffect(true, "i++");
    assertSideEffect(true, "[b, [a, i++]]");
    assertSideEffect(true, "i=3");
    assertSideEffect(true, "[0, i=3]");
    assertSideEffect(true, "b()");
    assertSideEffect(true, "[1, b()]");
    assertSideEffect(true, "b.b=4");
    assertSideEffect(true, "b.b--");
    assertSideEffect(true, "i--");
    assertSideEffect(true, "a[0][i=4]");
    assertSideEffect(true, "a += 3");
    assertSideEffect(true, "a, b, z += 4");
    assertSideEffect(true, "a ? c : d++");
    assertSideEffect(true, "a + c++");
    assertSideEffect(true, "a + c - d()");
    assertSideEffect(true, "a + c - d()");

    assertSideEffect(true, "function foo() {}");
    assertSideEffect(true, "class Foo {}");
    assertSideEffect(true, "while(true);");
    assertSideEffect(true, "if(true){a()}");

    assertSideEffect(false, "if(true){a}");
    assertSideEffect(false, "(function() { })");
    assertSideEffect(false, "(function() { i++ })");
    assertSideEffect(false, "[function a(){}]");
    assertSideEffect(false, "(class { })");
    assertSideEffect(false, "(class { method() { i++ } })");
    assertSideEffect(false, "(class Foo extends Bar { })");
    assertSideEffect(true, "(class extends foo() { })");

    assertSideEffect(false, "a");
    assertSideEffect(false, "[b, c [d, [e]]]");
    assertSideEffect(false, "({a: x, b: y, c: z})");
    assertSideEffect(false, "({a, b, c})");
    assertSideEffect(false, "({[a]: x})");
    assertSideEffect(true, "({[a()]: x})");
    assertSideEffect(true, "({[a]: x()})");
    assertSideEffect(false, "/abc/gi");
    assertSideEffect(false, "'a'");
    assertSideEffect(false, "0");
    assertSideEffect(false, "a + c");
    assertSideEffect(false, "'c' + a[0]");
    assertSideEffect(false, "a[0][1]");
    assertSideEffect(false, "'a' + c");
    assertSideEffect(false, "'a' + a.name");
    assertSideEffect(false, "1, 2, 3");
    assertSideEffect(false, "a, b, 3");
    assertSideEffect(false, "(function(a, b) {  })");
    assertSideEffect(false, "a ? c : d");
    assertSideEffect(false, "'1' + navigator.userAgent");

    assertSideEffect(false, "`template`");
    assertSideEffect(false, "`template${name}`");
    assertSideEffect(false, "`${name}template`");
    assertSideEffect(true, "`${naming()}template`");
    assertSideEffect(true, "templateFunction`template`");
    assertSideEffect(true, "st = `${name}template`");
    assertSideEffect(true, "tempFunc = templateFunction`template`");


    assertSideEffect(false, "new RegExp('foobar', 'i')");
    assertSideEffect(true, "new RegExp(SomethingWacky(), 'i')");
    assertSideEffect(false, "new Array()");
    assertSideEffect(false, "new Array");
    assertSideEffect(false, "new Array(4)");
    assertSideEffect(false, "new Array('a', 'b', 'c')");
    assertSideEffect(true, "new SomeClassINeverHeardOf()");
    assertSideEffect(true, "new SomeClassINeverHeardOf()");

    assertSideEffect(false, "({}).foo = 4");
    assertSideEffect(false, "([]).foo = 4");
    assertSideEffect(false, "(function() {}).foo = 4");

    assertSideEffect(true, "this.foo = 4");
    assertSideEffect(true, "a.foo = 4");
    assertSideEffect(true, "(function() { return n; })().foo = 4");
    assertSideEffect(true, "([]).foo = bar()");

    assertSideEffect(false, "undefined");
    assertSideEffect(false, "void 0");
    assertSideEffect(true, "void foo()");
    assertSideEffect(false, "-Infinity");
    assertSideEffect(false, "Infinity");
    assertSideEffect(false, "NaN");

    assertSideEffect(false, "({}||[]).foo = 2;");
    assertSideEffect(false, "(true ? {} : []).foo = 2;");
    assertSideEffect(false, "({},[]).foo = 2;");

    assertSideEffect(true, "delete a.b");

    assertSideEffect(false, "Math.random();");
    assertSideEffect(true, "Math.random(seed);");
  }

  public void testObjectMethodSideEffects() {
    // "toString" and "valueOf" are assumed to be side-effect free
    assertSideEffect(false, "o.toString()");
    assertSideEffect(false, "o.valueOf()");

    // other methods depend on the extern definitions
    assertSideEffect(true, "o.watch()");
  }

  public void testRegExpSideEffect() {
    // A RegExp Object by itself doesn't have any side-effects
    assertSideEffect(false, "/abc/gi", true);
    assertSideEffect(false, "/abc/gi", false);

    // RegExp instance methods have global side-effects, so whether they are
    // considered side-effect free depends on whether the global properties
    // are referenced.
    assertSideEffect(true, "(/abc/gi).test('')", true);
    assertSideEffect(false, "(/abc/gi).test('')", false);
    assertSideEffect(true, "(/abc/gi).test(a)", true);
    assertSideEffect(false, "(/abc/gi).test(b)", false);

    assertSideEffect(true, "(/abc/gi).exec('')", true);
    assertSideEffect(false, "(/abc/gi).exec('')", false);

    // Some RegExp object method that may have side-effects.
    assertSideEffect(true, "(/abc/gi).foo('')", true);
    assertSideEffect(true, "(/abc/gi).foo('')", false);

    // Try the string RegExp ops.
    assertSideEffect(true, "''.match('a')", true);
    assertSideEffect(false, "''.match('a')", false);
    assertSideEffect(true, "''.match(/(a)/)", true);
    assertSideEffect(false, "''.match(/(a)/)", false);

    assertSideEffect(true, "''.replace('a')", true);
    assertSideEffect(false, "''.replace('a')", false);

    assertSideEffect(true, "''.search('a')", true);
    assertSideEffect(false, "''.search('a')", false);

    assertSideEffect(true, "''.split('a')", true);
    assertSideEffect(false, "''.split('a')", false);

    // Some non-RegExp string op that may have side-effects.
    assertSideEffect(true, "''.foo('a')", true);
    assertSideEffect(true, "''.foo('a')", false);

    // 'a' might be a RegExp object with the 'g' flag, in which case
    // the state might change by running any of the string ops.
    // Specifically, using these methods resets the "lastIndex" if used
    // in combination with a RegExp instance "exec" method.
    assertSideEffect(true, "''.match(a)", true);
    assertSideEffect(true, "''.match(a)", false);
  }

  public void testRegExpSideEffect2() {
    assertSideEffect(true, "'a'.replace(/a/, function (s) {alert(s)})", false);
    assertSideEffect(false, "'a'.replace(/a/, 'x')", false);
  }

  private void assertMutableState(boolean se, String js) {
    Node n = parse(js);
    assertEquals(se, NodeUtil.mayEffectMutableState(n.getFirstChild()));
  }

  public void testMayEffectMutableState() {
    assertMutableState(true, "i++");
    assertMutableState(true, "[b, [a, i++]]");
    assertMutableState(true, "i=3");
    assertMutableState(true, "[0, i=3]");
    assertMutableState(true, "b()");
    assertMutableState(true, "void b()");
    assertMutableState(true, "[1, b()]");
    assertMutableState(true, "b.b=4");
    assertMutableState(true, "b.b--");
    assertMutableState(true, "i--");
    assertMutableState(true, "a[0][i=4]");
    assertMutableState(true, "a += 3");
    assertMutableState(true, "a, b, z += 4");
    assertMutableState(true, "a ? c : d++");
    assertMutableState(true, "a + c++");
    assertMutableState(true, "a + c - d()");
    assertMutableState(true, "a + c - d()");

    assertMutableState(true, "function foo() {}");
    assertMutableState(true, "while(true);");
    assertMutableState(true, "if(true){a()}");

    assertMutableState(false, "if(true){a}");
    assertMutableState(true, "(function() { })");
    assertMutableState(true, "(function() { i++ })");
    assertMutableState(true, "[function a(){}]");

    assertMutableState(false, "a");
    assertMutableState(true, "[b, c [d, [e]]]");
    assertMutableState(true, "({a: x, b: y, c: z})");
    // Note: RegExp objects are not immutable, for instance, the exec
    // method maintains state for "global" searches.
    assertMutableState(true, "/abc/gi");
    assertMutableState(false, "'a'");
    assertMutableState(false, "0");
    assertMutableState(false, "a + c");
    assertMutableState(false, "'c' + a[0]");
    assertMutableState(false, "a[0][1]");
    assertMutableState(false, "'a' + c");
    assertMutableState(false, "'a' + a.name");
    assertMutableState(false, "1, 2, 3");
    assertMutableState(false, "a, b, 3");
    assertMutableState(true, "(function(a, b) {  })");
    assertMutableState(false, "a ? c : d");
    assertMutableState(false, "'1' + navigator.userAgent");

    assertMutableState(true, "new RegExp('foobar', 'i')");
    assertMutableState(true, "new RegExp(SomethingWacky(), 'i')");
    assertMutableState(true, "new Array()");
    assertMutableState(true, "new Array");
    assertMutableState(true, "new Array(4)");
    assertMutableState(true, "new Array('a', 'b', 'c')");
    assertMutableState(true, "new SomeClassINeverHeardOf()");
  }


  public void testIsFunctionExpression() {
    assertContainsAnonFunc(true, "(function(){})");
    assertContainsAnonFunc(true, "[function a(){}]");
    assertContainsAnonFunc(false, "{x: function a(){}}");
    assertContainsAnonFunc(true, "(function a(){})()");
    assertContainsAnonFunc(true, "x = function a(){};");
    assertContainsAnonFunc(true, "var x = function a(){};");
    assertContainsAnonFunc(true, "if (function a(){});");
    assertContainsAnonFunc(true, "while (function a(){});");
    assertContainsAnonFunc(true, "do; while (function a(){});");
    assertContainsAnonFunc(true, "for (function a(){};;);");
    assertContainsAnonFunc(true, "for (;function a(){};);");
    assertContainsAnonFunc(true, "for (;;function a(){});");
    assertContainsAnonFunc(true, "for (p in function a(){});");
    assertContainsAnonFunc(true, "with (function a(){}) {}");
    assertContainsAnonFunc(false, "function a(){}");
    assertContainsAnonFunc(false, "if (x) function a(){};");
    assertContainsAnonFunc(false, "if (x) { function a(){} }");
    assertContainsAnonFunc(false, "if (x); else function a(){};");
    assertContainsAnonFunc(false, "while (x) function a(){};");
    assertContainsAnonFunc(false, "do function a(){} while (0);");
    assertContainsAnonFunc(false, "for (;;) function a(){}");
    assertContainsAnonFunc(false, "for (p in o) function a(){};");
    assertContainsAnonFunc(false, "with (x) function a(){}");
  }

  private void assertContainsAnonFunc(boolean expected, String js) {
    Node funcParent = findParentOfFuncDescendant(parse(js));
    assertNotNull("Expected function node in parse tree of: " + js, funcParent);
    Node funcNode = getFuncChild(funcParent);
    assertEquals(expected, NodeUtil.isFunctionExpression(funcNode));
  }

  private Node findParentOfFuncDescendant(Node n) {
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      if (c.isFunction()) {
        return n;
      }
      Node result = findParentOfFuncDescendant(c);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  private Node getFuncChild(Node n) {
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      if (c.isFunction()) {
        return c;
      }
    }
    return null;
  }

  public void testContainsType() {
    assertTrue(NodeUtil.containsType(
        parse("this"), Token.THIS));
    assertTrue(NodeUtil.containsType(
        parse("function foo(){}(this)"), Token.THIS));
    assertTrue(NodeUtil.containsType(
        parse("b?this:null"), Token.THIS));

    assertFalse(NodeUtil.containsType(
        parse("a"), Token.THIS));
    assertFalse(NodeUtil.containsType(
        parse("function foo(){}"), Token.THIS));
    assertFalse(NodeUtil.containsType(
        parse("(b?foo():null)"), Token.THIS));
  }

  public void testReferencesThis() {
    assertTrue(NodeUtil.referencesThis(
        parse("this")));
    // Don't descend into functions (starts at the script node)
    assertFalse(NodeUtil.referencesThis(
        parse("function foo(){this}")));
    // But starting with a function properly check for 'this'
    Node n = parse("function foo(){this}").getFirstChild();
    assertEquals(n.getType(), Token.FUNCTION);
    assertTrue(NodeUtil.referencesThis(n));
    assertTrue(NodeUtil.referencesThis(
        parse("b?this:null")));

    assertFalse(NodeUtil.referencesThis(
        parse("a")));
    n = parse("function foo(){}").getFirstChild();
    assertEquals(n.getType(), Token.FUNCTION);
    assertFalse(NodeUtil.referencesThis(n));
    assertFalse(NodeUtil.referencesThis(
        parse("(b?foo():null)")));

    assertTrue(NodeUtil.referencesThis(parse("()=>this")));
    assertTrue(NodeUtil.referencesThis(parse("() => { () => alert(this); }")));
  }

  public void testGetNodeTypeReferenceCount() {
    assertEquals(0, NodeUtil.getNodeTypeReferenceCount(
        parse("function foo(){}"), Token.THIS,
            Predicates.<Node>alwaysTrue()));
    assertEquals(1, NodeUtil.getNodeTypeReferenceCount(
        parse("this"), Token.THIS,
            Predicates.<Node>alwaysTrue()));
    assertEquals(2, NodeUtil.getNodeTypeReferenceCount(
        parse("this;function foo(){}(this)"), Token.THIS,
            Predicates.<Node>alwaysTrue()));
  }

  public void testIsNameReferenceCount() {
    assertTrue(NodeUtil.isNameReferenced(
        parse("function foo(){}"), "foo"));
    assertTrue(NodeUtil.isNameReferenced(
        parse("var foo = function(){}"), "foo"));
    assertFalse(NodeUtil.isNameReferenced(
        parse("function foo(){}"), "undefined"));
    assertTrue(NodeUtil.isNameReferenced(
        parse("undefined"), "undefined"));
    assertTrue(NodeUtil.isNameReferenced(
        parse("undefined;function foo(){}(undefined)"), "undefined"));

    assertTrue(NodeUtil.isNameReferenced(
        parse("goo.foo"), "goo"));
    assertFalse(NodeUtil.isNameReferenced(
        parse("goo.foo"), "foo"));
  }


  public void testGetNameReferenceCount() {
    assertEquals(0, NodeUtil.getNameReferenceCount(
        parse("function foo(){}"), "undefined"));
    assertEquals(1, NodeUtil.getNameReferenceCount(
        parse("undefined"), "undefined"));
    assertEquals(2, NodeUtil.getNameReferenceCount(
        parse("undefined;function foo(){}(undefined)"), "undefined"));

    assertEquals(1, NodeUtil.getNameReferenceCount(
        parse("goo.foo"), "goo"));
    assertEquals(0, NodeUtil.getNameReferenceCount(
        parse("goo.foo"), "foo"));
    assertEquals(1, NodeUtil.getNameReferenceCount(
        parse("function foo(){}"), "foo"));
    assertEquals(1, NodeUtil.getNameReferenceCount(
        parse("var foo = function(){}"), "foo"));
  }

  public void testGetVarsDeclaredInBranch() {
    assertNodeNames(ImmutableSet.of("foo"),
        NodeUtil.getVarsDeclaredInBranch(
            parse("var foo;")));
    assertNodeNames(ImmutableSet.of("foo", "goo"),
        NodeUtil.getVarsDeclaredInBranch(
            parse("var foo,goo;")));
    assertNodeNames(ImmutableSet.<String>of(),
        NodeUtil.getVarsDeclaredInBranch(
            parse("foo();")));
    assertNodeNames(ImmutableSet.<String>of(),
        NodeUtil.getVarsDeclaredInBranch(
            parse("function f(){var foo;}")));
    assertNodeNames(ImmutableSet.of("goo"),
        NodeUtil.getVarsDeclaredInBranch(
            parse("var goo;function f(){var foo;}")));
  }

  private void assertNodeNames(Set<String> nodeNames, Collection<Node> nodes) {
    Set<String> actualNames = new HashSet<>();
    for (Node node : nodes) {
      actualNames.add(node.getString());
    }
    assertEquals(nodeNames, actualNames);
  }

  public void testIsControlStructureCodeBlock() {
    Node root = parse("if (x) foo(); else boo();");
    Node ifNode = root.getFirstChild();

    Node ifCondition = ifNode.getFirstChild();
    Node ifCase = ifNode.getSecondChild();
    Node elseCase = ifNode.getLastChild();

    assertFalse(NodeUtil.isControlStructureCodeBlock(ifNode, ifCondition));
    assertTrue(NodeUtil.isControlStructureCodeBlock(ifNode, ifCase));
    assertTrue(NodeUtil.isControlStructureCodeBlock(ifNode, elseCase));
  }

  public void testIsFunctionExpression1() {
    Node root = parse("(function foo() {})");
    Node statementNode = root.getFirstChild();
    assertTrue(statementNode.isExprResult());
    Node functionNode = statementNode.getFirstChild();
    assertTrue(functionNode.isFunction());
    assertTrue(NodeUtil.isFunctionExpression(functionNode));
  }

  public void testIsFunctionExpression2() {
    Node root = parse("function foo() {}");
    Node functionNode = root.getFirstChild();
    assertTrue(functionNode.isFunction());
    assertFalse(NodeUtil.isFunctionExpression(functionNode));
  }

  public void testRemoveChildBlock() {
    // Test removing the inner block.
    Node actual = parse("{{x()}}");

    Node outerBlockNode = actual.getFirstChild();
    Node innerBlockNode = outerBlockNode.getFirstChild();
    innerBlockNode.setIsSyntheticBlock(true);

    NodeUtil.removeChild(outerBlockNode, innerBlockNode);
    String expected = "{{}}";
    String difference = parse(expected).checkTreeEquals(actual);
    if (difference != null) {
      fail("Nodes do not match:\n" + difference);
    }
  }

  public void testRemoveTryChild1() {
    // Test removing the finally clause.
    Node actual = parse("try {foo()} catch(e) {} finally {}");

    Node tryNode = actual.getFirstChild();
    Node finallyBlock = tryNode.getLastChild();

    NodeUtil.removeChild(tryNode, finallyBlock);
    String expected = "try {foo()} catch(e) {}";
    String difference = parse(expected).checkTreeEquals(actual);
    if (difference != null) {
      fail("Nodes do not match:\n" + difference);
    }
  }

  public void testRemoveTryChild2() {
    // Test removing the try clause.
    Node actual = parse("try {foo()} catch(e) {} finally {}");

    Node tryNode = actual.getFirstChild();
    Node tryBlock = tryNode.getFirstChild();

    NodeUtil.removeChild(tryNode, tryBlock);
    String expected = "try {} catch(e) {} finally {}";
    String difference = parse(expected).checkTreeEquals(actual);
    if (difference != null) {
      fail("Nodes do not match:\n" + difference);
    }
  }

  public void testRemoveTryChild3() {
    // Test removing the catch clause.
    Node actual = parse("try {foo()} catch(e) {} finally {}");

    Node tryNode = actual.getFirstChild();
    Node catchBlocks = tryNode.getSecondChild();
    Node catchBlock = catchBlocks.getFirstChild();

    NodeUtil.removeChild(catchBlocks, catchBlock);
    String expected = "try {foo()} finally {}";
    String difference = parse(expected).checkTreeEquals(actual);
    if (difference != null) {
      fail("Nodes do not match:\n" + difference);
    }
  }

  public void testRemoveTryChild4() {
    // Test removing the catch clause without a finally.
    Node actual = parse("try {foo()} catch(e) {} finally {}");

    Node tryNode = actual.getFirstChild();
    Node catchBlocks = tryNode.getSecondChild();

    NodeUtil.removeChild(tryNode, catchBlocks);
    String expected = "try {foo()} finally {}";
    String difference = parse(expected).checkTreeEquals(actual);
    if (difference != null) {
      fail("Nodes do not match:\n" + difference);
    }
  }

  public void testRemoveTryChild5() {
    Node actual = parse("try {foo()} catch(e) {} finally {}");

    Node tryNode = actual.getFirstChild();
    Node catchBlocks = tryNode.getSecondChild();
    Node catchBlock = catchBlocks.getFirstChild();

    NodeUtil.removeChild(catchBlocks, catchBlock);
    String expected = "try {foo()} finally {}";
    String difference = parse(expected).checkTreeEquals(actual);
    if (difference != null) {
      fail("Nodes do not match:\n" + difference);
    }
  }

  public void testRemoveVarChild() {
    // Test removing the first child.
    Node actual = parse("var foo, goo, hoo");

    Node varNode = actual.getFirstChild();
    Node nameNode = varNode.getFirstChild();

    NodeUtil.removeChild(varNode, nameNode);
    String expected = "var goo, hoo";
    String difference = parse(expected).checkTreeEquals(actual);
    if (difference != null) {
      fail("Nodes do not match:\n" + difference);
    }


    // Test removing the second child.
    actual = parse("var foo, goo, hoo");

    varNode = actual.getFirstChild();
    nameNode = varNode.getSecondChild();

    NodeUtil.removeChild(varNode, nameNode);
    expected = "var foo, hoo";
    difference = parse(expected).checkTreeEquals(actual);
    if (difference != null) {
      fail("Nodes do not match:\n" + difference);
    }

    // Test removing the last child of several children.
    actual = parse("var foo, hoo");

    varNode = actual.getFirstChild();
    nameNode = varNode.getSecondChild();

    NodeUtil.removeChild(varNode, nameNode);
    expected = "var foo";
    difference = parse(expected).checkTreeEquals(actual);
    if (difference != null) {
      fail("Nodes do not match:\n" + difference);
    }

    // Test removing the last.
    actual = parse("var hoo");

    varNode = actual.getFirstChild();
    nameNode = varNode.getFirstChild();

    NodeUtil.removeChild(varNode, nameNode);
    expected = "";
    difference = parse(expected).checkTreeEquals(actual);
    if (difference != null) {
      fail("Nodes do not match:\n" + difference);
    }
  }

  public void testRemoveLabelChild1() {
    // Test removing the first child.
    Node actual = parse("foo: goo()");

    Node labelNode = actual.getFirstChild();
    Node callExpressNode = labelNode.getLastChild();

    NodeUtil.removeChild(labelNode, callExpressNode);
    String expected = "";
    String difference = parse(expected).checkTreeEquals(actual);
    if (difference != null) {
      fail("Nodes do not match:\n" + difference);
    }
  }

  public void testRemoveLabelChild2() {
    // Test removing the first child.
    Node actual = parse("achoo: foo: goo()");

    Node labelNode = actual.getFirstChild();
    Node callExpressNode = labelNode.getLastChild();

    NodeUtil.removeChild(labelNode, callExpressNode);
    String expected = "";
    String difference = parse(expected).checkTreeEquals(actual);
    if (difference != null) {
      fail("Nodes do not match:\n" + difference);
    }
  }

  public void testRemoveForChild() {
    // Test removing the initializer.
    Node actual = parse("for(var a=0;a<0;a++)foo()");

    Node forNode = actual.getFirstChild();
    Node child = forNode.getFirstChild();

    NodeUtil.removeChild(forNode, child);
    String expected = "for(;a<0;a++)foo()";
    String difference = parse(expected).checkTreeEquals(actual);
    assertNull("Nodes do not match:\n" + difference, difference);


    // Test removing the condition.
    actual = parse("for(var a=0;a<0;a++)foo()");

    forNode = actual.getFirstChild();
    child = forNode.getSecondChild();

    NodeUtil.removeChild(forNode, child);
    expected = "for(var a=0;;a++)foo()";
    difference = parse(expected).checkTreeEquals(actual);
    assertNull("Nodes do not match:\n" + difference, difference);


    // Test removing the increment.
    actual = parse("for(var a=0;a<0;a++)foo()");

    forNode = actual.getFirstChild();
    child = forNode.getSecondChild().getNext();

    NodeUtil.removeChild(forNode, child);
    expected = "for(var a=0;a<0;)foo()";
    difference = parse(expected).checkTreeEquals(actual);
    assertNull("Nodes do not match:\n" + difference, difference);


    // Test removing the body.
    actual = parse("for(var a=0;a<0;a++)foo()");

    forNode = actual.getFirstChild();
    child = forNode.getLastChild();

    NodeUtil.removeChild(forNode, child);
    expected = "for(var a=0;a<0;a++);";
    difference = parse(expected).checkTreeEquals(actual);
    assertNull("Nodes do not match:\n" + difference, difference);


    // Test removing the body.
    actual = parse("for(a in ack)foo();");

    forNode = actual.getFirstChild();
    child = forNode.getLastChild();

    NodeUtil.removeChild(forNode, child);
    expected = "for(a in ack);";
    difference = parse(expected).checkTreeEquals(actual);
    assertNull("Nodes do not match:\n" + difference, difference);
  }

  public void testMergeBlock1() {
    // Test removing the initializer.
    Node actual = parse("{{a();b();}}");

    Node parentBlock = actual.getFirstChild();
    Node childBlock = parentBlock.getFirstChild();

    assertTrue(NodeUtil.tryMergeBlock(childBlock));
    String expected = "{a();b();}";
    String difference = parse(expected).checkTreeEquals(actual);
    assertNull("Nodes do not match:\n" + difference, difference);
  }

  public void testMergeBlock2() {
    // Test removing the initializer.
    Node actual = parse("foo:{a();}");

    Node parentLabel = actual.getFirstChild();
    Node childBlock = parentLabel.getLastChild();

    assertFalse(NodeUtil.tryMergeBlock(childBlock));
  }

  public void testMergeBlock3() {
    // Test removing the initializer.
    String code = "foo:{a();boo()}";
    Node actual = parse("foo:{a();boo()}");

    Node parentLabel = actual.getFirstChild();
    Node childBlock = parentLabel.getLastChild();

    assertFalse(NodeUtil.tryMergeBlock(childBlock));
    String expected = code;
    String difference = parse(expected).checkTreeEquals(actual);
    assertNull("Nodes do not match:\n" + difference, difference);
  }

  public void testGetSourceName() {
    Node n = new Node(Token.BLOCK);
    Node parent = new Node(Token.BLOCK, n);
    parent.setSourceFileForTesting("foo");

    assertEquals("foo", NodeUtil.getSourceName(n));
  }

  public void testLocalValue1() throws Exception {
    // Names are not known to be local.
    assertFalse(testLocalValue("x"));
    assertFalse(testLocalValue("x()"));
    assertFalse(testLocalValue("this"));
    assertFalse(testLocalValue("arguments"));

    // We can't know if new objects are local unless we know
    // that they don't alias themselves.
    assertFalse(testLocalValue("new x()"));

    // property references are assume to be non-local
    assertFalse(testLocalValue("(new x()).y"));
    assertFalse(testLocalValue("(new x())['y']"));

    // Primitive values are local
    assertTrue(testLocalValue("null"));
    assertTrue(testLocalValue("undefined"));
    assertTrue(testLocalValue("Infinity"));
    assertTrue(testLocalValue("NaN"));
    assertTrue(testLocalValue("1"));
    assertTrue(testLocalValue("'a'"));
    assertTrue(testLocalValue("true"));
    assertTrue(testLocalValue("false"));
    assertTrue(testLocalValue("[]"));
    assertTrue(testLocalValue("{}"));

    // The contents of arrays and objects don't matter
    assertTrue(testLocalValue("[x]"));
    assertTrue(testLocalValue("{'a':x}"));

    // increment/decrement results in primitive number, the previous value is
    // always coersed to a number (even in the post.
    assertTrue(testLocalValue("++x"));
    assertTrue(testLocalValue("--x"));
    assertTrue(testLocalValue("x++"));
    assertTrue(testLocalValue("x--"));

    // The left side of an only assign matters if it is an alias or mutable.
    assertTrue(testLocalValue("x=1"));
    assertFalse(testLocalValue("x=[]"));
    assertFalse(testLocalValue("x=y"));
    // The right hand side of assignment opts don't matter, as they force
    // a local result.
    assertTrue(testLocalValue("x+=y"));
    assertTrue(testLocalValue("x*=y"));
    // Comparisons always result in locals, as they force a local boolean
    // result.
    assertTrue(testLocalValue("x==y"));
    assertTrue(testLocalValue("x!=y"));
    assertTrue(testLocalValue("x>y"));
    // Only the right side of a comma matters
    assertTrue(testLocalValue("(1,2)"));
    assertTrue(testLocalValue("(x,1)"));
    assertFalse(testLocalValue("(x,y)"));

    // Both the operands of OR matter
    assertTrue(testLocalValue("1||2"));
    assertFalse(testLocalValue("x||1"));
    assertFalse(testLocalValue("x||y"));
    assertFalse(testLocalValue("1||y"));

    // Both the operands of AND matter
    assertTrue(testLocalValue("1&&2"));
    assertFalse(testLocalValue("x&&1"));
    assertFalse(testLocalValue("x&&y"));
    assertFalse(testLocalValue("1&&y"));

    // Only the results of HOOK matter
    assertTrue(testLocalValue("x?1:2"));
    assertFalse(testLocalValue("x?x:2"));
    assertFalse(testLocalValue("x?1:x"));
    assertFalse(testLocalValue("x?x:y"));

    // Results of ops are local values
    assertTrue(testLocalValue("!y"));
    assertTrue(testLocalValue("~y"));
    assertTrue(testLocalValue("y + 1"));
    assertTrue(testLocalValue("y + z"));
    assertTrue(testLocalValue("y * z"));

    assertTrue(testLocalValue("'a' in x"));
    assertTrue(testLocalValue("typeof x"));
    assertTrue(testLocalValue("x instanceof y"));

    assertTrue(testLocalValue("void x"));
    assertTrue(testLocalValue("void 0"));

    assertFalse(testLocalValue("{}.x"));

    assertTrue(testLocalValue("{}.toString()"));
    assertTrue(testLocalValue("o.toString()"));

    assertFalse(testLocalValue("o.valueOf()"));

    assertTrue(testLocalValue("delete a.b"));
  }

  public void testLocalValue2() {
    Node newExpr = getNode("new x()");
    assertFalse(NodeUtil.evaluatesToLocalValue(newExpr));

    Preconditions.checkState(newExpr.isNew());
    Node.SideEffectFlags flags = new Node.SideEffectFlags();

    flags.clearAllFlags();
    newExpr.setSideEffectFlags(flags.valueOf());

    assertTrue(NodeUtil.evaluatesToLocalValue(newExpr));

    flags.clearAllFlags();
    flags.setMutatesThis();
    newExpr.setSideEffectFlags(flags.valueOf());

    assertTrue(NodeUtil.evaluatesToLocalValue(newExpr));

    flags.clearAllFlags();
    flags.setReturnsTainted();
    newExpr.setSideEffectFlags(flags.valueOf());

    assertTrue(NodeUtil.evaluatesToLocalValue(newExpr));

    flags.clearAllFlags();
    flags.setThrows();
    newExpr.setSideEffectFlags(flags.valueOf());

    assertFalse(NodeUtil.evaluatesToLocalValue(newExpr));

    flags.clearAllFlags();
    flags.setMutatesArguments();
    newExpr.setSideEffectFlags(flags.valueOf());

    assertFalse(NodeUtil.evaluatesToLocalValue(newExpr));

    flags.clearAllFlags();
    flags.setMutatesGlobalState();
    newExpr.setSideEffectFlags(flags.valueOf());

    assertFalse(NodeUtil.evaluatesToLocalValue(newExpr));
  }

  public void testCallSideEffects() {
    Node callExpr = getNode("new x().method()");
    assertTrue(NodeUtil.functionCallHasSideEffects(callExpr));

    Node newExpr = callExpr.getFirstFirstChild();
    Preconditions.checkState(newExpr.isNew());
    Node.SideEffectFlags flags = new Node.SideEffectFlags();

    // No side effects, local result
    flags.clearAllFlags();
    newExpr.setSideEffectFlags(flags.valueOf());
    flags.clearAllFlags();
    callExpr.setSideEffectFlags(flags.valueOf());

    assertTrue(NodeUtil.evaluatesToLocalValue(callExpr));
    assertFalse(NodeUtil.functionCallHasSideEffects(callExpr));
    assertFalse(NodeUtil.mayHaveSideEffects(callExpr));

    // Modifies this, local result
    flags.clearAllFlags();
    newExpr.setSideEffectFlags(flags.valueOf());
    flags.clearAllFlags();
    flags.setMutatesThis();
    callExpr.setSideEffectFlags(flags.valueOf());

    assertTrue(NodeUtil.evaluatesToLocalValue(callExpr));
    assertFalse(NodeUtil.functionCallHasSideEffects(callExpr));
    assertFalse(NodeUtil.mayHaveSideEffects(callExpr));

    // Modifies this, non-local result
    flags.clearAllFlags();
    newExpr.setSideEffectFlags(flags.valueOf());
    flags.clearAllFlags();
    flags.setMutatesThis();
    flags.setReturnsTainted();
    callExpr.setSideEffectFlags(flags.valueOf());

    assertFalse(NodeUtil.evaluatesToLocalValue(callExpr));
    assertFalse(NodeUtil.functionCallHasSideEffects(callExpr));
    assertFalse(NodeUtil.mayHaveSideEffects(callExpr));

    // No modifications, non-local result
    flags.clearAllFlags();
    newExpr.setSideEffectFlags(flags.valueOf());
    flags.clearAllFlags();
    flags.setReturnsTainted();
    callExpr.setSideEffectFlags(flags.valueOf());

    assertFalse(NodeUtil.evaluatesToLocalValue(callExpr));
    assertFalse(NodeUtil.functionCallHasSideEffects(callExpr));
    assertFalse(NodeUtil.mayHaveSideEffects(callExpr));

    // The new modifies global state, no side-effect call, non-local result
    // This call could be removed, but not the new.
    flags.clearAllFlags();
    flags.setMutatesGlobalState();
    newExpr.setSideEffectFlags(flags.valueOf());
    flags.clearAllFlags();
    callExpr.setSideEffectFlags(flags.valueOf());

    assertTrue(NodeUtil.evaluatesToLocalValue(callExpr));
    assertFalse(NodeUtil.functionCallHasSideEffects(callExpr));
    assertTrue(NodeUtil.mayHaveSideEffects(callExpr));
  }

  private boolean testLocalValue(String js) {
    return NodeUtil.evaluatesToLocalValue(getNode(js));
  }

  public void testValidDefine() {
    assertTrue(testValidDefineValue("1"));
    assertTrue(testValidDefineValue("-3"));
    assertTrue(testValidDefineValue("true"));
    assertTrue(testValidDefineValue("false"));
    assertTrue(testValidDefineValue("'foo'"));

    assertFalse(testValidDefineValue("x"));
    assertFalse(testValidDefineValue("null"));
    assertFalse(testValidDefineValue("undefined"));
    assertFalse(testValidDefineValue("NaN"));

    assertTrue(testValidDefineValue("!true"));
    assertTrue(testValidDefineValue("-true"));
    assertTrue(testValidDefineValue("1 & 8"));
    assertTrue(testValidDefineValue("1 + 8"));
    assertTrue(testValidDefineValue("'a' + 'b'"));

    assertFalse(testValidDefineValue("1 & foo"));
  }

  private boolean testValidDefineValue(String js) {
    Node script = parse("var test = " + js + ";");
    Node var = script.getFirstChild();
    Node name = var.getFirstChild();
    Node value = name.getFirstChild();

    ImmutableSet<String> defines = ImmutableSet.of();
    return NodeUtil.isValidDefineValue(value, defines);
  }

  public void testGetNumberValue() {
    // Strings
    assertEquals(1.0, NodeUtil.getNumberValue(getNode("'\\uFEFF1'")));
    assertEquals(0.0, NodeUtil.getNumberValue(getNode("''")));
    assertEquals(0.0, NodeUtil.getNumberValue(getNode("' '")));
    assertEquals(0.0, NodeUtil.getNumberValue(getNode("' \\t'")));
    assertEquals(0.0, NodeUtil.getNumberValue(getNode("'+0'")));
    assertEquals(-0.0, NodeUtil.getNumberValue(getNode("'-0'")));
    assertEquals(2.0, NodeUtil.getNumberValue(getNode("'+2'")));
    assertEquals(-1.6, NodeUtil.getNumberValue(getNode("'-1.6'")));
    assertEquals(16.0, NodeUtil.getNumberValue(getNode("'16'")));
    assertEquals(16.0, NodeUtil.getNumberValue(getNode("' 16 '")));
    assertEquals(16.0, NodeUtil.getNumberValue(getNode("' 16 '")));
    assertEquals(12300.0, NodeUtil.getNumberValue(getNode("'123e2'")));
    assertEquals(12300.0, NodeUtil.getNumberValue(getNode("'123E2'")));
    assertEquals(1.23, NodeUtil.getNumberValue(getNode("'123e-2'")));
    assertEquals(1.23, NodeUtil.getNumberValue(getNode("'123E-2'")));
    assertEquals(-1.23, NodeUtil.getNumberValue(getNode("'-123e-2'")));
    assertEquals(-1.23, NodeUtil.getNumberValue(getNode("'-123E-2'")));
    assertEquals(1.23, NodeUtil.getNumberValue(getNode("'+123e-2'")));
    assertEquals(1.23, NodeUtil.getNumberValue(getNode("'+123E-2'")));
    assertEquals(12300.0, NodeUtil.getNumberValue(getNode("'+123e+2'")));
    assertEquals(12300.0, NodeUtil.getNumberValue(getNode("'+123E+2'")));

    assertEquals(15.0, NodeUtil.getNumberValue(getNode("'0xf'")));
    assertEquals(15.0, NodeUtil.getNumberValue(getNode("'0xF'")));

    // Chrome and rhino behavior differently from FF and IE. FF and IE
    // consider a negative hex number to be invalid
    assertNull(NodeUtil.getNumberValue(getNode("'-0xf'")));
    assertNull(NodeUtil.getNumberValue(getNode("'-0xF'")));
    assertNull(NodeUtil.getNumberValue(getNode("'+0xf'")));
    assertNull(NodeUtil.getNumberValue(getNode("'+0xF'")));

    assertEquals(16.0, NodeUtil.getNumberValue(getNode("'0X10'")));
    assertEquals(Double.NaN, NodeUtil.getNumberValue(getNode("'0X10.8'")));
    assertEquals(77.0, NodeUtil.getNumberValue(getNode("'077'")));
    assertEquals(-77.0, NodeUtil.getNumberValue(getNode("'-077'")));
    assertEquals(-77.5, NodeUtil.getNumberValue(getNode("'-077.5'")));
    assertEquals(
        Double.NEGATIVE_INFINITY,
        NodeUtil.getNumberValue(getNode("'-Infinity'")));
    assertEquals(
        Double.POSITIVE_INFINITY,
        NodeUtil.getNumberValue(getNode("'Infinity'")));
    assertEquals(
        Double.POSITIVE_INFINITY,
        NodeUtil.getNumberValue(getNode("'+Infinity'")));
    // Firefox treats "infinity" as "Infinity", IE treats it as NaN
    assertNull(NodeUtil.getNumberValue(getNode("'-infinity'")));
    assertNull(NodeUtil.getNumberValue(getNode("'infinity'")));
    assertNull(NodeUtil.getNumberValue(getNode("'+infinity'")));

    assertEquals(Double.NaN, NodeUtil.getNumberValue(getNode("'NaN'")));
    assertEquals(
        Double.NaN, NodeUtil.getNumberValue(getNode("'some unknown string'")));
    assertEquals(Double.NaN, NodeUtil.getNumberValue(getNode("'123 blah'")));

    // Literals
    assertEquals(1.0, NodeUtil.getNumberValue(getNode("1")));
    // "-1" is parsed as a literal
    assertEquals(-1.0, NodeUtil.getNumberValue(getNode("-1")));
    // "+1" is parse as an op + literal
    assertNull(NodeUtil.getNumberValue(getNode("+1")));
    assertEquals(22.0, NodeUtil.getNumberValue(getNode("22")));
    assertEquals(18.0, NodeUtil.getNumberValue(getNode("022")));
    assertEquals(34.0, NodeUtil.getNumberValue(getNode("0x22")));

    assertEquals(
        1.0, NodeUtil.getNumberValue(getNode("true")));
    assertEquals(
        0.0, NodeUtil.getNumberValue(getNode("false")));
    assertEquals(
        0.0, NodeUtil.getNumberValue(getNode("null")));
    assertEquals(
        Double.NaN, NodeUtil.getNumberValue(getNode("void 0")));
    assertEquals(
        Double.NaN, NodeUtil.getNumberValue(getNode("void f")));
    // values with side-effects are ignored.
    assertNull(NodeUtil.getNumberValue(getNode("void f()")));
    assertEquals(
        Double.NaN, NodeUtil.getNumberValue(getNode("NaN")));
    assertEquals(
        Double.POSITIVE_INFINITY,
        NodeUtil.getNumberValue(getNode("Infinity")));
    assertEquals(
        Double.NEGATIVE_INFINITY,
        NodeUtil.getNumberValue(getNode("-Infinity")));

    // "infinity" is not a known name.
    assertNull(NodeUtil.getNumberValue(getNode("infinity")));
    assertNull(NodeUtil.getNumberValue(getNode("-infinity")));

    // getNumberValue only converts literals
    assertNull(NodeUtil.getNumberValue(getNode("x")));
    assertNull(NodeUtil.getNumberValue(getNode("x.y")));
    assertNull(NodeUtil.getNumberValue(getNode("1/2")));
    assertNull(NodeUtil.getNumberValue(getNode("1-2")));
    assertNull(NodeUtil.getNumberValue(getNode("+1")));
  }

  public void testIsNumbericResult() {
    assertTrue(NodeUtil.isNumericResult(getNode("1")));
    assertFalse(NodeUtil.isNumericResult(getNode("true")));
    assertTrue(NodeUtil.isNumericResult(getNode("+true")));
    assertTrue(NodeUtil.isNumericResult(getNode("+1")));
    assertTrue(NodeUtil.isNumericResult(getNode("-1")));
    assertTrue(NodeUtil.isNumericResult(getNode("-Infinity")));
    assertTrue(NodeUtil.isNumericResult(getNode("Infinity")));
    assertTrue(NodeUtil.isNumericResult(getNode("NaN")));
    assertFalse(NodeUtil.isNumericResult(getNode("undefined")));
    assertFalse(NodeUtil.isNumericResult(getNode("void 0")));

    assertTrue(NodeUtil.isNumericResult(getNode("a << b")));
    assertTrue(NodeUtil.isNumericResult(getNode("a >> b")));
    assertTrue(NodeUtil.isNumericResult(getNode("a >>> b")));

    assertFalse(NodeUtil.isNumericResult(getNode("a == b")));
    assertFalse(NodeUtil.isNumericResult(getNode("a != b")));
    assertFalse(NodeUtil.isNumericResult(getNode("a === b")));
    assertFalse(NodeUtil.isNumericResult(getNode("a !== b")));
    assertFalse(NodeUtil.isNumericResult(getNode("a < b")));
    assertFalse(NodeUtil.isNumericResult(getNode("a > b")));
    assertFalse(NodeUtil.isNumericResult(getNode("a <= b")));
    assertFalse(NodeUtil.isNumericResult(getNode("a >= b")));
    assertFalse(NodeUtil.isNumericResult(getNode("a in b")));
    assertFalse(NodeUtil.isNumericResult(getNode("a instanceof b")));

    assertFalse(NodeUtil.isNumericResult(getNode("'a'")));
    assertFalse(NodeUtil.isNumericResult(getNode("'a'+b")));
    assertFalse(NodeUtil.isNumericResult(getNode("a+'b'")));
    assertFalse(NodeUtil.isNumericResult(getNode("a+b")));
    assertFalse(NodeUtil.isNumericResult(getNode("a()")));
    assertFalse(NodeUtil.isNumericResult(getNode("''.a")));
    assertFalse(NodeUtil.isNumericResult(getNode("a.b")));
    assertFalse(NodeUtil.isNumericResult(getNode("a.b()")));
    assertFalse(NodeUtil.isNumericResult(getNode("a().b()")));
    assertFalse(NodeUtil.isNumericResult(getNode("new a()")));

    // Definitely not numeric
    assertFalse(NodeUtil.isNumericResult(getNode("([1,2])")));
    assertFalse(NodeUtil.isNumericResult(getNode("({a:1})")));

    // Recurse into the expression when necessary.
    assertTrue(NodeUtil.isNumericResult(getNode("1 && 2")));
    assertTrue(NodeUtil.isNumericResult(getNode("1 || 2")));
    assertTrue(NodeUtil.isNumericResult(getNode("a ? 2 : 3")));
    assertTrue(NodeUtil.isNumericResult(getNode("a,1")));
    assertTrue(NodeUtil.isNumericResult(getNode("a=1")));

    assertFalse(NodeUtil.isNumericResult(getNode("a += 1")));

    assertTrue(NodeUtil.isNumericResult(getNode("a -= 1")));
    assertTrue(NodeUtil.isNumericResult(getNode("a *= 1")));
    assertTrue(NodeUtil.isNumericResult(getNode("--a")));
    assertTrue(NodeUtil.isNumericResult(getNode("++a")));
    assertTrue(NodeUtil.isNumericResult(getNode("a++")));
    assertTrue(NodeUtil.isNumericResult(getNode("a--")));
  }

  public void testIsBooleanResult() {
    assertFalse(NodeUtil.isBooleanResult(getNode("1")));
    assertTrue(NodeUtil.isBooleanResult(getNode("true")));
    assertFalse(NodeUtil.isBooleanResult(getNode("+true")));
    assertFalse(NodeUtil.isBooleanResult(getNode("+1")));
    assertFalse(NodeUtil.isBooleanResult(getNode("-1")));
    assertFalse(NodeUtil.isBooleanResult(getNode("-Infinity")));
    assertFalse(NodeUtil.isBooleanResult(getNode("Infinity")));
    assertFalse(NodeUtil.isBooleanResult(getNode("NaN")));
    assertFalse(NodeUtil.isBooleanResult(getNode("undefined")));
    assertFalse(NodeUtil.isBooleanResult(getNode("void 0")));

    assertFalse(NodeUtil.isBooleanResult(getNode("a << b")));
    assertFalse(NodeUtil.isBooleanResult(getNode("a >> b")));
    assertFalse(NodeUtil.isBooleanResult(getNode("a >>> b")));

    assertTrue(NodeUtil.isBooleanResult(getNode("a == b")));
    assertTrue(NodeUtil.isBooleanResult(getNode("a != b")));
    assertTrue(NodeUtil.isBooleanResult(getNode("a === b")));
    assertTrue(NodeUtil.isBooleanResult(getNode("a !== b")));
    assertTrue(NodeUtil.isBooleanResult(getNode("a < b")));
    assertTrue(NodeUtil.isBooleanResult(getNode("a > b")));
    assertTrue(NodeUtil.isBooleanResult(getNode("a <= b")));
    assertTrue(NodeUtil.isBooleanResult(getNode("a >= b")));
    assertTrue(NodeUtil.isBooleanResult(getNode("a in b")));
    assertTrue(NodeUtil.isBooleanResult(getNode("a instanceof b")));

    assertFalse(NodeUtil.isBooleanResult(getNode("'a'")));
    assertFalse(NodeUtil.isBooleanResult(getNode("'a'+b")));
    assertFalse(NodeUtil.isBooleanResult(getNode("a+'b'")));
    assertFalse(NodeUtil.isBooleanResult(getNode("a+b")));
    assertFalse(NodeUtil.isBooleanResult(getNode("a()")));
    assertFalse(NodeUtil.isBooleanResult(getNode("''.a")));
    assertFalse(NodeUtil.isBooleanResult(getNode("a.b")));
    assertFalse(NodeUtil.isBooleanResult(getNode("a.b()")));
    assertFalse(NodeUtil.isBooleanResult(getNode("a().b()")));
    assertFalse(NodeUtil.isBooleanResult(getNode("new a()")));
    assertTrue(NodeUtil.isBooleanResult(getNode("delete a")));

    // Definitely not boolean
    assertFalse(NodeUtil.isBooleanResult(getNode("([true,false])")));
    assertFalse(NodeUtil.isBooleanResult(getNode("({a:true})")));

    // These are boolean
    assertTrue(NodeUtil.isBooleanResult(getNode("true && false")));
    assertTrue(NodeUtil.isBooleanResult(getNode("true || false")));
    assertTrue(NodeUtil.isBooleanResult(getNode("a ? true : false")));
    assertTrue(NodeUtil.isBooleanResult(getNode("a,true")));
    assertTrue(NodeUtil.isBooleanResult(getNode("a=true")));
    assertFalse(NodeUtil.isBooleanResult(getNode("a=1")));
  }

  public void testMayBeString() {
    assertFalse(NodeUtil.mayBeString(getNode("1")));
    assertFalse(NodeUtil.mayBeString(getNode("true")));
    assertFalse(NodeUtil.mayBeString(getNode("+true")));
    assertFalse(NodeUtil.mayBeString(getNode("+1")));
    assertFalse(NodeUtil.mayBeString(getNode("-1")));
    assertFalse(NodeUtil.mayBeString(getNode("-Infinity")));
    assertFalse(NodeUtil.mayBeString(getNode("Infinity")));
    assertFalse(NodeUtil.mayBeString(getNode("NaN")));
    assertFalse(NodeUtil.mayBeString(getNode("undefined")));
    assertFalse(NodeUtil.mayBeString(getNode("void 0")));
    assertFalse(NodeUtil.mayBeString(getNode("null")));

    assertFalse(NodeUtil.mayBeString(getNode("a << b")));
    assertFalse(NodeUtil.mayBeString(getNode("a >> b")));
    assertFalse(NodeUtil.mayBeString(getNode("a >>> b")));

    assertFalse(NodeUtil.mayBeString(getNode("a == b")));
    assertFalse(NodeUtil.mayBeString(getNode("a != b")));
    assertFalse(NodeUtil.mayBeString(getNode("a === b")));
    assertFalse(NodeUtil.mayBeString(getNode("a !== b")));
    assertFalse(NodeUtil.mayBeString(getNode("a < b")));
    assertFalse(NodeUtil.mayBeString(getNode("a > b")));
    assertFalse(NodeUtil.mayBeString(getNode("a <= b")));
    assertFalse(NodeUtil.mayBeString(getNode("a >= b")));
    assertFalse(NodeUtil.mayBeString(getNode("a in b")));
    assertFalse(NodeUtil.mayBeString(getNode("a instanceof b")));

    assertTrue(NodeUtil.mayBeString(getNode("'a'")));
    assertTrue(NodeUtil.mayBeString(getNode("'a'+b")));
    assertTrue(NodeUtil.mayBeString(getNode("a+'b'")));
    assertTrue(NodeUtil.mayBeString(getNode("a+b")));
    assertTrue(NodeUtil.mayBeString(getNode("a()")));
    assertTrue(NodeUtil.mayBeString(getNode("''.a")));
    assertTrue(NodeUtil.mayBeString(getNode("a.b")));
    assertTrue(NodeUtil.mayBeString(getNode("a.b()")));
    assertTrue(NodeUtil.mayBeString(getNode("a().b()")));
    assertTrue(NodeUtil.mayBeString(getNode("new a()")));

    // These can't be strings but they aren't handled yet.
    assertFalse(NodeUtil.mayBeString(getNode("1 && 2")));
    assertFalse(NodeUtil.mayBeString(getNode("1 || 2")));
    assertFalse(NodeUtil.mayBeString(getNode("1 ? 2 : 3")));
    assertFalse(NodeUtil.mayBeString(getNode("1,2")));
    assertFalse(NodeUtil.mayBeString(getNode("a=1")));
    assertFalse(NodeUtil.mayBeString(getNode("1+1")));
    assertFalse(NodeUtil.mayBeString(getNode("true+true")));
    assertFalse(NodeUtil.mayBeString(getNode("null+null")));
    assertFalse(NodeUtil.mayBeString(getNode("NaN+NaN")));

    // These are not strings but they aren't primitives either
    assertTrue(NodeUtil.mayBeString(getNode("([1,2])")));
    assertTrue(NodeUtil.mayBeString(getNode("({a:1})")));
    assertTrue(NodeUtil.mayBeString(getNode("({}+1)")));
    assertTrue(NodeUtil.mayBeString(getNode("(1+{})")));
    assertTrue(NodeUtil.mayBeString(getNode("([]+1)")));
    assertTrue(NodeUtil.mayBeString(getNode("(1+[])")));

    assertTrue(NodeUtil.mayBeString(getNode("a += 'x'")));
    assertTrue(NodeUtil.mayBeString(getNode("a += 1")));
  }

  public void test1() {
    assertFalse(NodeUtil.isStringResult(getNode("a+b")));
  }

  public void testIsStringResult() {
    assertFalse(NodeUtil.isStringResult(getNode("1")));
    assertFalse(NodeUtil.isStringResult(getNode("true")));
    assertFalse(NodeUtil.isStringResult(getNode("+true")));
    assertFalse(NodeUtil.isStringResult(getNode("+1")));
    assertFalse(NodeUtil.isStringResult(getNode("-1")));
    assertFalse(NodeUtil.isStringResult(getNode("-Infinity")));
    assertFalse(NodeUtil.isStringResult(getNode("Infinity")));
    assertFalse(NodeUtil.isStringResult(getNode("NaN")));
    assertFalse(NodeUtil.isStringResult(getNode("undefined")));
    assertFalse(NodeUtil.isStringResult(getNode("void 0")));
    assertFalse(NodeUtil.isStringResult(getNode("null")));

    assertFalse(NodeUtil.isStringResult(getNode("a << b")));
    assertFalse(NodeUtil.isStringResult(getNode("a >> b")));
    assertFalse(NodeUtil.isStringResult(getNode("a >>> b")));

    assertFalse(NodeUtil.isStringResult(getNode("a == b")));
    assertFalse(NodeUtil.isStringResult(getNode("a != b")));
    assertFalse(NodeUtil.isStringResult(getNode("a === b")));
    assertFalse(NodeUtil.isStringResult(getNode("a !== b")));
    assertFalse(NodeUtil.isStringResult(getNode("a < b")));
    assertFalse(NodeUtil.isStringResult(getNode("a > b")));
    assertFalse(NodeUtil.isStringResult(getNode("a <= b")));
    assertFalse(NodeUtil.isStringResult(getNode("a >= b")));
    assertFalse(NodeUtil.isStringResult(getNode("a in b")));
    assertFalse(NodeUtil.isStringResult(getNode("a instanceof b")));

    assertTrue(NodeUtil.isStringResult(getNode("'a'")));
    assertTrue(NodeUtil.isStringResult(getNode("'a'+b")));
    assertTrue(NodeUtil.isStringResult(getNode("a+'b'")));
    assertFalse(NodeUtil.isStringResult(getNode("a+b")));
    assertFalse(NodeUtil.isStringResult(getNode("a()")));
    assertFalse(NodeUtil.isStringResult(getNode("''.a")));
    assertFalse(NodeUtil.isStringResult(getNode("a.b")));
    assertFalse(NodeUtil.isStringResult(getNode("a.b()")));
    assertFalse(NodeUtil.isStringResult(getNode("a().b()")));
    assertFalse(NodeUtil.isStringResult(getNode("new a()")));

    // These can't be strings but they aren't handled yet.
    assertFalse(NodeUtil.isStringResult(getNode("1 && 2")));
    assertFalse(NodeUtil.isStringResult(getNode("1 || 2")));
    assertFalse(NodeUtil.isStringResult(getNode("1 ? 2 : 3")));
    assertFalse(NodeUtil.isStringResult(getNode("1,2")));
    assertFalse(NodeUtil.isStringResult(getNode("a=1")));
    assertFalse(NodeUtil.isStringResult(getNode("1+1")));
    assertFalse(NodeUtil.isStringResult(getNode("true+true")));
    assertFalse(NodeUtil.isStringResult(getNode("null+null")));
    assertFalse(NodeUtil.isStringResult(getNode("NaN+NaN")));

    // These are not strings but they aren't primitives either
    assertFalse(NodeUtil.isStringResult(getNode("([1,2])")));
    assertFalse(NodeUtil.isStringResult(getNode("({a:1})")));
    assertFalse(NodeUtil.isStringResult(getNode("({}+1)")));
    assertFalse(NodeUtil.isStringResult(getNode("(1+{})")));
    assertFalse(NodeUtil.isStringResult(getNode("([]+1)")));
    assertFalse(NodeUtil.isStringResult(getNode("(1+[])")));

    assertTrue(NodeUtil.isStringResult(getNode("a += 'x'")));
  }

  public void testIsObjectResult() {
    assertFalse(NodeUtil.isObjectResult(getNode("1")));
    assertFalse(NodeUtil.isObjectResult(getNode("true")));
    assertFalse(NodeUtil.isObjectResult(getNode("+true")));
    assertFalse(NodeUtil.isObjectResult(getNode("+1")));
    assertFalse(NodeUtil.isObjectResult(getNode("-1")));
    assertFalse(NodeUtil.isObjectResult(getNode("-Infinity")));
    assertFalse(NodeUtil.isObjectResult(getNode("Infinity")));
    assertFalse(NodeUtil.isObjectResult(getNode("NaN")));
    assertFalse(NodeUtil.isObjectResult(getNode("undefined")));
    assertFalse(NodeUtil.isObjectResult(getNode("void 0")));

    assertFalse(NodeUtil.isObjectResult(getNode("a << b")));
    assertFalse(NodeUtil.isObjectResult(getNode("a >> b")));
    assertFalse(NodeUtil.isObjectResult(getNode("a >>> b")));

    assertFalse(NodeUtil.isObjectResult(getNode("a == b")));
    assertFalse(NodeUtil.isObjectResult(getNode("a != b")));
    assertFalse(NodeUtil.isObjectResult(getNode("a === b")));
    assertFalse(NodeUtil.isObjectResult(getNode("a !== b")));
    assertFalse(NodeUtil.isObjectResult(getNode("a < b")));
    assertFalse(NodeUtil.isObjectResult(getNode("a > b")));
    assertFalse(NodeUtil.isObjectResult(getNode("a <= b")));
    assertFalse(NodeUtil.isObjectResult(getNode("a >= b")));
    assertFalse(NodeUtil.isObjectResult(getNode("a in b")));
    assertFalse(NodeUtil.isObjectResult(getNode("a instanceof b")));

    assertFalse(NodeUtil.isObjectResult(getNode("delete a")));
    assertFalse(NodeUtil.isObjectResult(getNode("'a'")));
    assertFalse(NodeUtil.isObjectResult(getNode("'a'+b")));
    assertFalse(NodeUtil.isObjectResult(getNode("a+'b'")));
    assertFalse(NodeUtil.isObjectResult(getNode("a+b")));
    assertFalse(NodeUtil.isObjectResult(getNode("{},true")));

    // "false" here means "unknown"
    assertFalse(NodeUtil.isObjectResult(getNode("a()")));
    assertFalse(NodeUtil.isObjectResult(getNode("''.a")));
    assertFalse(NodeUtil.isObjectResult(getNode("a.b")));
    assertFalse(NodeUtil.isObjectResult(getNode("a.b()")));
    assertFalse(NodeUtil.isObjectResult(getNode("a().b()")));
    assertFalse(NodeUtil.isObjectResult(getNode("a ? true : {}")));

    // These are objects but aren't handled yet.
    assertFalse(NodeUtil.isObjectResult(getNode("true && {}")));
    assertFalse(NodeUtil.isObjectResult(getNode("true || {}")));

    // Definitely objects
    assertTrue(NodeUtil.isObjectResult(getNode("new a.b()")));
    assertTrue(NodeUtil.isObjectResult(getNode("([true,false])")));
    assertTrue(NodeUtil.isObjectResult(getNode("({a:true})")));
    assertTrue(NodeUtil.isObjectResult(getNode("a={}")));
    assertTrue(NodeUtil.isObjectResult(getNode("[] && {}")));
    assertTrue(NodeUtil.isObjectResult(getNode("[] || {}")));
    assertTrue(NodeUtil.isObjectResult(getNode("a ? [] : {}")));
    assertTrue(NodeUtil.isObjectResult(getNode("{},[]")));
    assertTrue(NodeUtil.isObjectResult(getNode("/a/g")));
  }

  public void testValidNames() {
    assertTrue(isValidPropertyName("a"));
    assertTrue(isValidPropertyName("a3"));
    assertFalse(isValidPropertyName("3a"));
    assertFalse(isValidPropertyName("a."));
    assertFalse(isValidPropertyName(".a"));
    assertFalse(isValidPropertyName("a.b"));
    assertFalse(isValidPropertyName("true"));
    assertFalse(isValidPropertyName("a.true"));
    assertFalse(isValidPropertyName("a..b"));

    assertTrue(NodeUtil.isValidSimpleName("a"));
    assertTrue(NodeUtil.isValidSimpleName("a3"));
    assertFalse(NodeUtil.isValidSimpleName("3a"));
    assertFalse(NodeUtil.isValidSimpleName("a."));
    assertFalse(NodeUtil.isValidSimpleName(".a"));
    assertFalse(NodeUtil.isValidSimpleName("a.b"));
    assertFalse(NodeUtil.isValidSimpleName("true"));
    assertFalse(NodeUtil.isValidSimpleName("a.true"));
    assertFalse(NodeUtil.isValidSimpleName("a..b"));

    assertTrue(isValidQualifiedName("a"));
    assertTrue(isValidQualifiedName("a3"));
    assertFalse(isValidQualifiedName("3a"));
    assertFalse(isValidQualifiedName("a."));
    assertFalse(isValidQualifiedName(".a"));
    assertTrue(isValidQualifiedName("a.b"));
    assertFalse(isValidQualifiedName("true"));
    assertFalse(isValidQualifiedName("a.true"));
    assertFalse(isValidQualifiedName("a..b"));
  }

  public void testGetNearestFunctionName() {
    testFunctionName("(function() {})()", null);
    testFunctionName("function a() {}", "a");
    testFunctionName("(function a() {})", "a");
    testFunctionName("({a:function () {}})", "a");
    testFunctionName("({get a() {}})", "a");
    testFunctionName("({set a(b) {}})", "a");
    testFunctionName("({set a(b) {}})", "a");
    testFunctionName("({1:function () {}})", "1");
    testFunctionName("var a = function a() {}", "a");
    testFunctionName("var a;a = function a() {}", "a");
    testFunctionName("var o;o.a = function a() {}", "o.a");
    testFunctionName("this.a = function a() {}", "this.a");
  }

  public void testGetBestLValue() {
    assertEquals("x", getFunctionLValue("var x = function() {};"));
    assertEquals("x", getFunctionLValue("x = function() {};"));
    assertEquals("x", getFunctionLValue("function x() {};"));
    assertEquals("x", getFunctionLValue("var x = y ? z : function() {};"));
    assertEquals("x", getFunctionLValue("var x = y ? function() {} : z;"));
    assertEquals("x", getFunctionLValue("var x = y && function() {};"));
    assertEquals("x", getFunctionLValue("var x = y || function() {};"));
    assertEquals("x", getFunctionLValue("var x = (y, function() {});"));
  }

  public void testGetRValueOfLValue() {
    assertTrue(functionIsRValueOfAssign("x = function() {};"));
    assertTrue(functionIsRValueOfAssign("x += function() {};"));
    assertTrue(functionIsRValueOfAssign("x -= function() {};"));
    assertTrue(functionIsRValueOfAssign("x *= function() {};"));
    assertTrue(functionIsRValueOfAssign("x /= function() {};"));
    assertTrue(functionIsRValueOfAssign("x <<= function() {};"));
    assertTrue(functionIsRValueOfAssign("x >>= function() {};"));
    assertTrue(functionIsRValueOfAssign("x >>= function() {};"));
    assertTrue(functionIsRValueOfAssign("x >>>= function() {};"));
    assertFalse(functionIsRValueOfAssign("x = y ? x : function() {};"));
  }

  public void testIsNaN() {
    assertTrue(NodeUtil.isNaN(getNode("NaN")));
    assertFalse(NodeUtil.isNaN(getNode("Infinity")));
    assertFalse(NodeUtil.isNaN(getNode("x")));
    assertTrue(NodeUtil.isNaN(getNode("0/0")));
    assertFalse(NodeUtil.isNaN(getNode("1/0")));
    assertFalse(NodeUtil.isNaN(getNode("0/1")));
    assertFalse(NodeUtil.isNaN(IR.number(0.0)));
  }

  public void testIsExecutedExactlyOnce() {
    assertTrue(executedOnceTestCase("x;"));

    assertTrue(executedOnceTestCase("x && 1;"));
    assertFalse(executedOnceTestCase("1 && x;"));

    assertFalse(executedOnceTestCase("1 && (x && 1);"));

    assertTrue(executedOnceTestCase("x || 1;"));
    assertFalse(executedOnceTestCase("1 || x;"));

    assertFalse(executedOnceTestCase("1 && (x || 1);"));

    assertTrue(executedOnceTestCase("x ? 1 : 2;"));
    assertFalse(executedOnceTestCase("1 ? 1 : x;"));
    assertFalse(executedOnceTestCase("1 ? x : 2;"));

    assertFalse(executedOnceTestCase("1 && (x ? 1 : 2);"));

    assertTrue(executedOnceTestCase("if (x) {}"));
    assertFalse(executedOnceTestCase("if (true) {x;}"));
    assertFalse(executedOnceTestCase("if (true) {} else {x;}"));

    assertFalse(executedOnceTestCase("if (1) { if (x) {} }"));

    assertTrue(executedOnceTestCase("for(x;;){}"));
    assertFalse(executedOnceTestCase("for(;x;){}"));
    assertFalse(executedOnceTestCase("for(;;x){}"));
    assertFalse(executedOnceTestCase("for(;;){x;}"));

    assertFalse(executedOnceTestCase("if (1) { for(x;;){} }"));

    assertFalse(executedOnceTestCase("for(x in {}){}"));
    assertTrue(executedOnceTestCase("for({}.a in x){}"));
    assertFalse(executedOnceTestCase("for({}.a in {}){x}"));

    assertFalse(executedOnceTestCase("if (1) { for(x in {}){} }"));

    assertTrue(executedOnceTestCase("switch (x) {}"));
    assertFalse(executedOnceTestCase("switch (1) {case x:}"));
    assertFalse(executedOnceTestCase("switch (1) {case 1: x}"));
    assertFalse(executedOnceTestCase("switch (1) {default: x}"));

    assertFalse(executedOnceTestCase("if (1) { switch (x) {} }"));

    assertFalse(executedOnceTestCase("while (x) {}"));
    assertFalse(executedOnceTestCase("while (1) {x}"));

    assertFalse(executedOnceTestCase("do {} while (x)"));
    assertFalse(executedOnceTestCase("do {x} while (1)"));

    assertFalse(executedOnceTestCase("try {x} catch (e) {}"));
    assertFalse(executedOnceTestCase("try {} catch (e) {x}"));
    assertTrue(executedOnceTestCase("try {} finally {x}"));

    assertFalse(executedOnceTestCase("if (1) { try {} finally {x} }"));
  }

  private void assertLValueNamedX(Node n) {
    assertThat(n.getString()).isEqualTo("x");
    assertThat(NodeUtil.isLValue(n)).isTrue();
  }

  public void testIsLValue() {
    assertLValueNamedX(parse("var x;").getFirstFirstChild());
    assertLValueNamedX(parse("var w, x;").getFirstChild().getLastChild());
    assertLValueNamedX(
        parse("var [...x] = y;").getFirstFirstChild().getFirstFirstChild().getFirstChild());
    assertLValueNamedX(parse("var x = y;").getFirstFirstChild());
    assertLValueNamedX(parse("x++;").getFirstFirstChild().getFirstChild());
    assertLValueNamedX(
       NodeUtil.getFunctionParameters(parse("function f(x) {}").getFirstChild()).getFirstChild());

    Node x = NodeUtil.getFunctionParameters(parse("function f(x = 3) {}").getFirstChild())
        .getFirstChild()  // x = 3
        .getFirstChild();  // x
    assertLValueNamedX(x);

    assertLValueNamedX(
        parse("({x} = obj)").getFirstFirstChild().getFirstFirstChild());
    assertLValueNamedX(
        parse("([x] = obj)").getFirstFirstChild().getFirstFirstChild());
  }

  private void assertNotLValueNamedX(Node n) {
    assertThat(n.getString()).isEqualTo("x");
    assertThat(NodeUtil.isLValue(n)).isFalse();
  }

  public void testIsNotLValue() {
    assertNotLValueNamedX(parse("var a = x;").getFirstFirstChild().getFirstChild());

    Node x = parse("f(...x);")  // script
        .getFirstChild()  // expr result
        .getFirstChild()  // call
        .getLastChild()  // spread
        .getFirstChild();  // x
    assertNotLValueNamedX(x);

    x = parse("var a = [...x];")  // script
        .getFirstChild()  // var
        .getFirstChild()  // a
        .getFirstChild()  // array
        .getFirstChild()  // spread
        .getFirstChild();  // x
    assertNotLValueNamedX(x);
  }

  public void testLhsByDestructuring1() {
    Node root = parse("var [a, b] = obj;");
    Node destructLhs = root.getFirstFirstChild();
    Preconditions.checkArgument(destructLhs.isDestructuringLhs());
    Node destructPat = destructLhs.getFirstChild();
    Preconditions.checkArgument(destructPat.isArrayPattern());

    Node nameNodeA = destructPat.getFirstChild();
    Node nameNodeB = nameNodeA.getNext();
    Preconditions.checkState(nameNodeA.getString().equals("a"), nameNodeA);
    Preconditions.checkState(nameNodeB.getString().equals("b"), nameNodeB);

    Node nameNodeObj = destructPat.getNext();
    Preconditions.checkState(nameNodeObj.getString().equals("obj"), nameNodeObj);

    assertLhsByDestructuring(nameNodeA);
    assertLhsByDestructuring(nameNodeB);
    assertNotLhsByDestructuring(nameNodeObj);
  }

  public void testLhsByDestructuring1b() {
    Node root = parse("var {a: c, b: d} = obj;");
    Node destructLhs = root.getFirstFirstChild();
    Preconditions.checkArgument(destructLhs.isDestructuringLhs());
    Node destructPat = destructLhs.getFirstChild();
    Preconditions.checkArgument(destructPat.isObjectPattern());

    Node strKeyNodeA = destructPat.getFirstChild();
    Node strKeyNodeB = strKeyNodeA.getNext();
    Node nameNodeC = strKeyNodeA.getFirstChild();
    Node nameNodeD = strKeyNodeB.getFirstChild();
    Preconditions.checkState(strKeyNodeA.getString().equals("a"), strKeyNodeA);
    Preconditions.checkState(strKeyNodeB.getString().equals("b"), strKeyNodeB);
    Preconditions.checkState(nameNodeC.getString().equals("c"), nameNodeC);
    Preconditions.checkState(nameNodeD.getString().equals("d"), nameNodeD);

    Node nameNodeObj = destructPat.getNext();
    Preconditions.checkState(nameNodeObj.getString().equals("obj"), nameNodeObj);

    assertNotLhsByDestructuring(strKeyNodeA);
    assertNotLhsByDestructuring(strKeyNodeB);
    assertLhsByDestructuring(nameNodeC);
    assertLhsByDestructuring(nameNodeD);
    assertNotLhsByDestructuring(nameNodeObj);
  }

  public void testLhsByDestructuring1c() {
    Node root = parse("var {a, b} = obj;");
    Node destructLhs = root.getFirstFirstChild();
    Preconditions.checkArgument(destructLhs.isDestructuringLhs());
    Node destructPat = destructLhs.getFirstChild();
    Preconditions.checkArgument(destructPat.isObjectPattern());

    Node strKeyNodeA = destructPat.getFirstChild();
    Node strKeyNodeB = strKeyNodeA.getNext();
    Preconditions.checkState(strKeyNodeA.getString().equals("a"), strKeyNodeA);
    Preconditions.checkState(strKeyNodeB.getString().equals("b"), strKeyNodeB);

    Node nameNodeObj = destructPat.getNext();
    Preconditions.checkState(nameNodeObj.getString().equals("obj"), nameNodeObj);

    assertLhsByDestructuring(strKeyNodeA);
    assertLhsByDestructuring(strKeyNodeB);
    assertNotLhsByDestructuring(nameNodeObj);
  }

  public void testLhsByDestructuring2() {
    Node root = parse("var [a, [b, c]] = obj;");
    Node destructLhs = root.getFirstFirstChild();
    Preconditions.checkArgument(destructLhs.isDestructuringLhs());
    Node destructPat = destructLhs.getFirstChild();
    Preconditions.checkArgument(destructPat.isArrayPattern());

    Node nameNodeA = destructPat.getFirstChild();
    Node nameNodeB = nameNodeA.getNext().getFirstChild();
    Node nameNodeC = nameNodeB.getNext();
    Preconditions.checkState(nameNodeA.getString().equals("a"), nameNodeA);
    Preconditions.checkState(nameNodeB.getString().equals("b"), nameNodeB);
    Preconditions.checkState(nameNodeC.getString().equals("c"), nameNodeC);

    Node nameNodeObj = destructPat.getNext();
    Preconditions.checkState(nameNodeObj.getString().equals("obj"), nameNodeObj);

    assertLhsByDestructuring(nameNodeA);
    assertLhsByDestructuring(nameNodeB);
    assertLhsByDestructuring(nameNodeC);
    assertNotLhsByDestructuring(nameNodeObj);
  }

  public void testLhsByDestructuring2b() {
    Node root = parse("var {a: e, b: {c: f, d: g}} = obj;");
    Node destructLhs = root.getFirstFirstChild();
    Preconditions.checkArgument(destructLhs.isDestructuringLhs());
    Node destructPat = destructLhs.getFirstChild();
    Preconditions.checkArgument(destructPat.isObjectPattern());

    Node strKeyNodeA = destructPat.getFirstChild();
    Node strKeyNodeB = strKeyNodeA.getNext();
    Node strKeyNodeC = strKeyNodeB.getFirstFirstChild();
    Node strKeyNodeD = strKeyNodeC.getNext();
    Node nameNodeE = strKeyNodeA.getFirstChild();
    Node nameNodeF = strKeyNodeC.getFirstChild();
    Node nameNodeG = strKeyNodeD.getFirstChild();
    Preconditions.checkState(strKeyNodeA.getString().equals("a"), strKeyNodeA);
    Preconditions.checkState(strKeyNodeB.getString().equals("b"), strKeyNodeB);
    Preconditions.checkState(strKeyNodeC.getString().equals("c"), strKeyNodeC);
    Preconditions.checkState(strKeyNodeD.getString().equals("d"), strKeyNodeD);
    Preconditions.checkState(nameNodeE.getString().equals("e"), nameNodeE);
    Preconditions.checkState(nameNodeF.getString().equals("f"), nameNodeF);
    Preconditions.checkState(nameNodeG.getString().equals("g"), nameNodeG);

    Node nameNodeObj = destructPat.getNext();
    Preconditions.checkState(nameNodeObj.getString().equals("obj"), nameNodeObj);

    assertNotLhsByDestructuring(strKeyNodeA);
    assertNotLhsByDestructuring(strKeyNodeB);
    assertNotLhsByDestructuring(strKeyNodeC);
    assertNotLhsByDestructuring(strKeyNodeD);
    assertLhsByDestructuring(nameNodeE);
    assertLhsByDestructuring(nameNodeF);
    assertLhsByDestructuring(nameNodeG);
    assertNotLhsByDestructuring(nameNodeObj);
  }

  public void testLhsByDestructuring3() {
    Node root = parse("var [a, b] = [c, d];");
    Node destructLhs = root.getFirstFirstChild();
    Preconditions.checkArgument(destructLhs.isDestructuringLhs());
    Node destructPat = destructLhs.getFirstChild();
    Preconditions.checkArgument(destructPat.isArrayPattern());

    Node nameNodeA = destructPat.getFirstChild();
    Node nameNodeB = nameNodeA.getNext();
    Preconditions.checkState(nameNodeA.getString().equals("a"), nameNodeA);
    Preconditions.checkState(nameNodeB.getString().equals("b"), nameNodeB);

    Node nameNodeC = destructLhs.getLastChild().getFirstChild();
    Node nameNodeD = nameNodeC.getNext();
    Preconditions.checkState(nameNodeC.getString().equals("c"), nameNodeC);
    Preconditions.checkState(nameNodeD.getString().equals("d"), nameNodeD);

    assertLhsByDestructuring(nameNodeA);
    assertLhsByDestructuring(nameNodeB);
    assertNotLhsByDestructuring(nameNodeC);
    assertNotLhsByDestructuring(nameNodeD);
  }

  public void testLhsByDestructuring3b() {
    Node root = parse("var {a: c, b: d} = {a: 1, b: 2};");
    Node destructLhs = root.getFirstFirstChild();
    Preconditions.checkArgument(destructLhs.isDestructuringLhs());
    Node destructPat = destructLhs.getFirstChild();
    Preconditions.checkArgument(destructPat.isObjectPattern());

    Node strKeyNodeA = destructPat.getFirstChild();
    Node strKeyNodeB = strKeyNodeA.getNext();
    Node nameNodeC = strKeyNodeA.getFirstChild();
    Node nameNodeD = strKeyNodeB.getFirstChild();
    Preconditions.checkState(strKeyNodeA.getString().equals("a"), strKeyNodeA);
    Preconditions.checkState(strKeyNodeB.getString().equals("b"), strKeyNodeB);
    Preconditions.checkState(nameNodeC.getString().equals("c"), nameNodeC);
    Preconditions.checkState(nameNodeD.getString().equals("d"), nameNodeD);

    assertNotLhsByDestructuring(strKeyNodeA);
    assertNotLhsByDestructuring(strKeyNodeB);
    assertLhsByDestructuring(nameNodeC);
    assertLhsByDestructuring(nameNodeD);
  }

  public void testLhsByDestructuring4() {
    Node root = parse("for ([a, b] of X){}");
    Node destructPat = root.getFirstFirstChild();
    Preconditions.checkArgument(destructPat.isArrayPattern());

    Node nameNodeA = destructPat.getFirstChild();
    Node nameNodeB = destructPat.getLastChild();
    Preconditions.checkState(nameNodeA.getString().equals("a"), nameNodeA);
    Preconditions.checkState(nameNodeB.getString().equals("b"), nameNodeB);

    assertLhsByDestructuring(nameNodeA);
    assertLhsByDestructuring(nameNodeB);
  }

  public void testLhsByDestructuring5() {
    Node root = parse("function fn([a, b] = [c, d]){}");
    Node destructPat = root.getFirstChild().getSecondChild()
        .getFirstFirstChild();
    Preconditions.checkArgument(destructPat.isArrayPattern());

    Node nameNodeA = destructPat.getFirstChild();
    Node nameNodeB = destructPat.getLastChild();
    Node nameNodeC = destructPat.getNext().getFirstChild();
    Node nameNodeD = destructPat.getNext().getLastChild();
    Preconditions.checkState(nameNodeA.getString().equals("a"), nameNodeA);
    Preconditions.checkState(nameNodeB.getString().equals("b"), nameNodeB);
    Preconditions.checkState(nameNodeC.getString().equals("c"), nameNodeC);
    Preconditions.checkState(nameNodeD.getString().equals("d"), nameNodeD);

    assertLhsByDestructuring(nameNodeA);
    assertLhsByDestructuring(nameNodeB);
    assertNotLhsByDestructuring(nameNodeC);
    assertNotLhsByDestructuring(nameNodeD);
  }

  public void testLhsByDestructuring6() {
    Node root = parse("for ([{a: b}] of c) {}");
    Node destructPat = root.getFirstFirstChild().getFirstChild();
    Preconditions.checkArgument(destructPat.isObjectPattern()
        && destructPat.getParent().isArrayPattern());

    Node strKeyNodeA = destructPat.getFirstChild();
    Node nameNodeB = strKeyNodeA.getFirstChild();
    Node nameNodeC = destructPat.getParent().getNext();
    Preconditions.checkState(strKeyNodeA.getString().equals("a"), strKeyNodeA);
    Preconditions.checkState(nameNodeB.getString().equals("b"), nameNodeB);
    Preconditions.checkState(nameNodeC.getString().equals("c"), nameNodeC);

    assertNotLhsByDestructuring(strKeyNodeA);
    assertLhsByDestructuring(nameNodeB);
    assertNotLhsByDestructuring(nameNodeC);
  }

  public void testLhsByDestructuring6b() {
    Node root = parse("for ([{a: b}] in c) {}");
    Node destructPat = root.getFirstFirstChild().getFirstChild();
    Preconditions.checkArgument(destructPat.isObjectPattern()
        && destructPat.getParent().isArrayPattern());

    Node strKeyNodeA = destructPat.getFirstChild();
    Node nameNodeB = strKeyNodeA.getFirstChild();
    Node nameNodeC = destructPat.getParent().getNext();
    Preconditions.checkState(strKeyNodeA.getString().equals("a"), strKeyNodeA);
    Preconditions.checkState(nameNodeB.getString().equals("b"), nameNodeB);
    Preconditions.checkState(nameNodeC.getString().equals("c"), nameNodeC);

    assertNotLhsByDestructuring(strKeyNodeA);
    assertLhsByDestructuring(nameNodeB);
    assertNotLhsByDestructuring(nameNodeC);
  }

  public void testLhsByDestructuring6c() {
    Node root = parse("for (var [{a: b}] = [{a: 1}];;) {}");
    Node destructArr = root.getFirstFirstChild().getFirstFirstChild();
    Preconditions.checkArgument(destructArr.isArrayPattern());
    Node destructPat = destructArr.getFirstChild();
    Preconditions.checkArgument(destructPat.isObjectPattern()
        && destructPat.getParent().isArrayPattern());

    Node strKeyNodeA = destructPat.getFirstChild();
    Node nameNodeB = strKeyNodeA.getFirstChild();
    Preconditions.checkState(strKeyNodeA.getString().equals("a"), strKeyNodeA);
    Preconditions.checkState(nameNodeB.getString().equals("b"), nameNodeB);

    assertNotLhsByDestructuring(strKeyNodeA);
    assertLhsByDestructuring(nameNodeB);
  }

  public void testLhsByDestructuring7() {
    Node root = parse("for ([a] of c) {}");
    Node destructPat = root.getFirstFirstChild();
    Preconditions.checkArgument(destructPat.isArrayPattern());

    Node nameNodeA = destructPat.getFirstChild();
    Node nameNodeC = destructPat.getNext();
    Preconditions.checkState(nameNodeA.getString().equals("a"), nameNodeA);
    Preconditions.checkState(nameNodeC.getString().equals("c"), nameNodeC);

    assertLhsByDestructuring(nameNodeA);
    assertNotLhsByDestructuring(nameNodeC);
  }

  public void testLhsByDestructuring7b() {
    Node root = parse("for ([a] in c) {}");
    Node destructPat = root.getFirstFirstChild();
    Preconditions.checkArgument(destructPat.isArrayPattern());

    Node nameNodeA = destructPat.getFirstChild();
    Node nameNodeC = destructPat.getNext();
    Preconditions.checkState(nameNodeA.getString().equals("a"), nameNodeA);
    Preconditions.checkState(nameNodeC.getString().equals("c"), nameNodeC);

    assertLhsByDestructuring(nameNodeA);
    assertNotLhsByDestructuring(nameNodeC);
  }

  public void testLhsByDestructuring7c() {
    Node root = parse("for (var [a] = [1];;) {}");
    Node destructLhs = root.getFirstFirstChild().getFirstChild();
    Preconditions.checkArgument(destructLhs.isDestructuringLhs());
    Node destructPat = destructLhs.getFirstChild();
    Preconditions.checkArgument(destructPat.isArrayPattern());

    Node nameNodeA = destructPat.getFirstChild();
    Preconditions.checkState(nameNodeA.getString().equals("a"), nameNodeA);

    assertLhsByDestructuring(nameNodeA);
  }

  public void testLhsByDestructuring7d() {
    Node root = parse("for (let [a] = [1];;) {}");
    Node destructLhs = root.getFirstFirstChild().getFirstChild();
    Preconditions.checkArgument(destructLhs.isDestructuringLhs());
    Node destructPat = destructLhs.getFirstChild();
    Preconditions.checkArgument(destructPat.isArrayPattern());

    Node nameNodeA = destructPat.getFirstChild();
    Preconditions.checkState(nameNodeA.getString().equals("a"), nameNodeA);

    assertLhsByDestructuring(nameNodeA);
  }

  public void testLhsByDestructuring7e() {
    Node root = parse("for (const [a] = [1];;) {}");
    Node destructLhs = root.getFirstFirstChild().getFirstChild();
    Preconditions.checkArgument(destructLhs.isDestructuringLhs());
    Node destructPat = destructLhs.getFirstChild();
    Preconditions.checkArgument(destructPat.isArrayPattern());

    Node nameNodeA = destructPat.getFirstChild();
    Preconditions.checkState(nameNodeA.getString().equals("a"), nameNodeA);

    assertLhsByDestructuring(nameNodeA);
  }

  static void assertLhsByDestructuring(Node n) {
    assertTrue(NodeUtil.isLhsByDestructuring(n));
  }

  static void assertNotLhsByDestructuring(Node n) {
    assertFalse(NodeUtil.isLhsByDestructuring(n));
  }

  public void testNewQName1() {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setCodingConvention(new GoogleCodingConvention());
    compiler.init(ImmutableList.<SourceFile>of(), ImmutableList.<SourceFile>of(), options);
    Node actual = NodeUtil.newQName(compiler, "ns.prop");
    Node expected = IR.getprop(
        IR.name("ns"),
        IR.string("prop"));
    assertNodeTreesEqual(expected, actual);
  }

  public void testNewQualifiedNameNode2() {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setCodingConvention(new GoogleCodingConvention());
    compiler.init(ImmutableList.<SourceFile>of(), ImmutableList.<SourceFile>of(), options);
    Node actual = NodeUtil.newQName(compiler, "this.prop");
    Node expected = IR.getprop(
        IR.thisNode(),
        IR.string("prop"));
    assertNodeTreesEqual(expected, actual);
  }

  public void testGetBestJsDocInfoForClasses() {
    Node classNode = getClassNode("/** @export */ class Foo {}");
    assertTrue(NodeUtil.getBestJSDocInfo(classNode).isExport());

    classNode = getClassNode("/** @export */ var Foo = class {}");
    assertTrue(NodeUtil.getBestJSDocInfo(classNode).isExport());
  }

  public void testGetBestJsDocInfoExport() {
    Node classNode = getClassNode("/** @constructor */ export class Foo {}");
    assertTrue(NodeUtil.getBestJSDocInfo(classNode).isConstructor());

    Node function = getFunctionNode("/** @constructor */ export function Foo() {}");
    assertTrue(NodeUtil.getBestJSDocInfo(function).isConstructor());

    function = getFunctionNode("/** @constructor */ export var Foo = function() {}");
    assertTrue(NodeUtil.getBestJSDocInfo(function).isConstructor());

    function = getFunctionNode("/** @constructor */ export let Foo = function() {}");
    assertTrue(NodeUtil.getBestJSDocInfo(function).isConstructor());
  }

  public void testGetLhsNodesOfDeclaration() {
    assertThat(getLhsNodesOfDeclaration("var x;")).hasSize(1);
    assertThat(getLhsNodesOfDeclaration("var x, y;")).hasSize(2);
    assertThat(getLhsNodesOfDeclaration("var f = function(x, y, z) {};")).hasSize(1);
    assertThat(getLhsNodesOfDeclaration("var [x=a => a, y = b=>b+1] = arr;")).hasSize(2);
    assertThat(getLhsNodesOfDeclaration("var [x=a => a, y = b=>b+1, ...z] = arr;")).hasSize(3);
    assertThat(getLhsNodesOfDeclaration("var [ , , , y = b=>b+1, ...z] = arr;")).hasSize(2);
    assertThat(getLhsNodesOfDeclaration("var {x = a=>a, y = b=>b+1} = obj;")).hasSize(2);
    assertThat(getLhsNodesOfDeclaration("var {p1: x = a=>a, p2: y = b=>b+1} = obj;")).hasSize(2);
    assertThat(getLhsNodesOfDeclaration("var {[pname]: x = a=>a, [p2name]: y} = obj;")).hasSize(2);
    assertThat(getLhsNodesOfDeclaration("var {lhs1 = a, p2: [lhs2, lhs3 = b] = [notlhs]} = obj;"))
        .hasSize(3);
  }

  private boolean executedOnceTestCase(String code) {
    Node ast = parse(code);
    Node nameNode = getNameNode(ast, "x");
    return NodeUtil.isExecutedExactlyOnce(nameNode);
  }

  private String getFunctionLValue(String js) {
    Node lVal = NodeUtil.getBestLValue(getFunctionNode(js));
    return lVal == null ? null : lVal.getString();
  }

  private boolean functionIsRValueOfAssign(String js) {
    Node ast = parse(js);
    Node nameNode = getNameNode(ast, "x");
    Node funcNode = getFunctionNode(ast);
    assertNotNull("No function node to test", funcNode);
    return funcNode == NodeUtil.getRValueOfLValue(nameNode);
  }

  private void assertNodeTreesEqual(
      Node expected, Node actual) {
    String error = expected.checkTreeEquals(actual);
    assertNull(error, error);
  }

  static void testFunctionName(String js, String expected) {
    assertEquals(
        expected,
        NodeUtil.getNearestFunctionName(getFunctionNode(js)));
  }

  static Node getClassNode(String js) {
    Node root = parse(js);
    return getClassNode(root);
  }

  static Iterable<Node> getLhsNodesOfDeclaration(String js) {
    Node root = parse(js);
    return NodeUtil.getLhsNodesOfDeclaration(root.getFirstChild());
  }

  static Node getClassNode(Node n) {
    if (n.isClass()) {
      return n;
    }
    for (Node c : n.children()) {
      Node result = getClassNode(c);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  static Node getFunctionNode(String js) {
    Node root = parse(js);
    return getFunctionNode(root);
  }

  static Node getFunctionNode(Node n) {
    if (n.isFunction()) {
      return n;
    }
    for (Node c : n.children()) {
      Node result = getFunctionNode(c);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  static Node getNameNode(Node n, String name) {
    if (n.isName() && n.getString().equals(name)) {
      return n;
    }
    for (Node c : n.children()) {
      Node result = getNameNode(c, name);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  static boolean isValidPropertyName(String s) {
    return NodeUtil.isValidPropertyName(LanguageMode.ECMASCRIPT3, s);
  }

  static boolean isValidQualifiedName(String s) {
    return NodeUtil.isValidQualifiedName(LanguageMode.ECMASCRIPT3, s);
  }
}
