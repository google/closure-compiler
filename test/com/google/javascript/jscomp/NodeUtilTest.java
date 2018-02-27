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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.DiagnosticGroups.ES5_STRICT;
import static com.google.javascript.jscomp.testing.NodeSubject.assertNode;

import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.TernaryValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import junit.framework.TestCase;

/** Tests for NodeUtil */
public final class NodeUtilTest extends TestCase {

  private static Node parse(String js) {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);

    // To allow octal literals such as 0123 to be parsed.
    options.setStrictModeInput(false);
    options.setWarningLevel(ES5_STRICT, CheckLevel.OFF);

    Compiler compiler = new Compiler();
    compiler.initOptions(options);

    Node n = compiler.parseTestCode(js);
    assertThat(compiler.getErrors()).isEmpty();
    return n;
  }

  private static Node getNode(String js) {
    Node root = parse("var a=(" + js + ");");
    Node expr = root.getFirstChild();
    Node var = expr.getFirstChild();
    return var.getFirstChild();
  }

  private static Node getNode(Node root, Token token) {
    for (Node n : root.children()) {
      if (n.getToken() == token) {
        return n;
      }
      Node potentialMatch = getNode(n, token);
      if (potentialMatch != null) {
        return potentialMatch;
      }
    }
    return null;
  }

  private static Node getYieldNode(String js) {
    return checkNotNull(getYieldNode(parse(js)));
  }

  private static Node getYieldNode(Node root) {
    return getNode(root, Token.YIELD);
  }

  private static Node getAwaitNode(String js) {
    return checkNotNull(getAwaitNode(parse(js)));
  }

  private static Node getAwaitNode(Node root) {
    return getNode(root, Token.AWAIT);
  }

  public void testGetNodeByLineCol_1() {
    Node root = parse("var x = 1;");
    assertNull(NodeUtil.getNodeByLineCol(root, 1, 0));
    assertNode(NodeUtil.getNodeByLineCol(root, 1, 1)).hasType(Token.VAR);
    assertNode(NodeUtil.getNodeByLineCol(root, 1, 2)).hasType(Token.VAR);
    assertNode(NodeUtil.getNodeByLineCol(root, 1, 5)).hasType(Token.NAME);
    assertNode(NodeUtil.getNodeByLineCol(root, 1, 9)).hasType(Token.NUMBER);
    assertNode(NodeUtil.getNodeByLineCol(root, 1, 11)).hasType(Token.VAR);
  }

  public void testGetNodeByLineCol_2() {
    Node root = parse(Joiner.on("\n").join(
        "var x = {};",
        "x.prop = 123;"));
    assertNode(NodeUtil.getNodeByLineCol(root, 2, 1)).hasType(Token.NAME);
    assertNode(NodeUtil.getNodeByLineCol(root, 2, 2)).hasType(Token.NAME);
    assertNode(NodeUtil.getNodeByLineCol(root, 2, 3)).hasType(Token.STRING);
    assertNode(NodeUtil.getNodeByLineCol(root, 2, 8)).hasType(Token.ASSIGN);
    assertNode(NodeUtil.getNodeByLineCol(root, 2, 11)).hasType(Token.NUMBER);
    assertNode(NodeUtil.getNodeByLineCol(root, 2, 13)).hasType(Token.NUMBER);
    assertNode(NodeUtil.getNodeByLineCol(root, 2, 14)).hasType(Token.EXPR_RESULT);
  }

  public void testGetNodeByLineCol_preferLiterals() {
    Node root;

    root = parse("x-5;");
    assertNode(NodeUtil.getNodeByLineCol(root, 1, 2)).hasType(Token.NAME);
    assertNode(NodeUtil.getNodeByLineCol(root, 1, 3)).hasType(Token.NUMBER);

    root = parse(Joiner.on("\n").join(
        "function f(x) {",
        "  return x||null;",
        "}"));
    assertNode(NodeUtil.getNodeByLineCol(root, 2, 11)).hasType(Token.NAME);
    assertNode(NodeUtil.getNodeByLineCol(root, 2, 12)).hasType(Token.OR);
    assertNode(NodeUtil.getNodeByLineCol(root, 2, 13)).hasType(Token.NULL);
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

  public void testObjectLiteralIsLiteralValue() {
    assertTrue(isLiteralValue("{a: 20}"));
    assertTrue(isLiteralValue("{'a': 20}"));
    assertTrue(isLiteralValue("{a: function() {}}"));
    assertFalse(isLiteralValueExcludingFunctions("{a: function() {}}"));
    assertTrue(isLiteralValue("{a() {}}"));
    assertFalse(isLiteralValueExcludingFunctions("{a() {}}"));
    assertTrue(isLiteralValue("{'a'() {}}"));
    assertFalse(isLiteralValueExcludingFunctions("{'a'() {}}"));

    assertTrue(isLiteralValue("{['a']: 20}"));
    assertFalse(isLiteralValue("{[b]: 20}"));
    assertTrue(isLiteralValue("{['a']() {}}"));
    assertFalse(isLiteralValue("{[b]() {}}"));
    assertFalse(isLiteralValueExcludingFunctions("{['a']() {}}"));

    assertTrue(isLiteralValue("{ get a() { return 0; } }"));
    assertTrue(isLiteralValue("{ get 'a'() { return 0; } }"));
    assertTrue(isLiteralValue("{ get ['a']() { return 0; } }"));
    assertTrue(isLiteralValue("{ get 123() { return 0; } }"));
    assertTrue(isLiteralValue("{ get [123]() { return 0; } }"));
    assertFalse(isLiteralValue("{ get [b]() { return 0; } }"));
    assertTrue(isLiteralValue("{ set a(x) { } }"));
    assertTrue(isLiteralValue("{ set 'a'(x) { } }"));
    assertTrue(isLiteralValue("{ set ['a'](x) { } }"));
    assertTrue(isLiteralValue("{ set 123(x) { } }"));
    assertTrue(isLiteralValue("{ set [123](x) { } }"));
    assertFalse(isLiteralValue("{ set [b](x) { } }"));
  }

  private boolean isLiteralValueExcludingFunctions(String code) {
    return NodeUtil.isLiteralValue(getNode(code), /* includeFunctions */ false);
  }

  private boolean isLiteralValue(String code) {
    return NodeUtil.isLiteralValue(getNode(code), /* includeFunctions */ true);
  }

  private void assertLiteralAndImmutable(Node n) {
    assertTrue(NodeUtil.isLiteralValue(n, true));
    assertTrue(NodeUtil.isLiteralValue(n, false));
    assertTrue(NodeUtil.isImmutableValue(n));
  }

  private void assertLiteralButNotImmutable(Node n) {
    assertTrue(NodeUtil.isLiteralValue(n, true));
    assertTrue(NodeUtil.isLiteralValue(n, false));
    assertFalse(NodeUtil.isImmutableValue(n));
  }

  private void assertNotLiteral(Node n) {
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

    /* See https://github.com/google/closure-compiler/issues/1262 */
    assertEquals(
        "1.2323919403474454e+21", NodeUtil.getStringValue(getNode("1.2323919403474454e+21")));

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

    assertEquals("Hello", NodeUtil.getStringValue(getNode("`Hello`")));
    assertEquals("Hello foo", NodeUtil.getStringValue(getNode("`Hello ${'foo'}`")));
    assertEquals(null, NodeUtil.getStringValue(getNode("`Hello ${name}`")));
    assertEquals("4 bananas", NodeUtil.getStringValue(getNode("`${4} bananas`")));
    assertEquals("This is true.", NodeUtil.getStringValue(getNode("`This is ${true}.`")));
    assertEquals(null, NodeUtil.getStringValue(getNode("`${'hello'} ${name}`")));
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
    assertIsObjectLiteralKey(parseExpr("({})"), false);
    assertIsObjectLiteralKey(parseExpr("a"), false);
    assertIsObjectLiteralKey(parseExpr("'a'"), false);
    assertIsObjectLiteralKey(parseExpr("1"), false);
    assertIsObjectLiteralKey(parseExpr("({a: 1})").getFirstChild(), true);
    assertIsObjectLiteralKey(parseExpr("({1: 1})").getFirstChild(), true);
    assertIsObjectLiteralKey(parseExpr("({get a(){}})").getFirstChild(), true);
    assertIsObjectLiteralKey(parseExpr("({set a(b){}})").getFirstChild(), true);
  }

  private Node parseExpr(String js) {
    Node root = parse(js);
    return root.getFirstFirstChild();
  }

  private void assertIsObjectLiteralKey(Node node, boolean expected) {
    assertEquals(expected, NodeUtil.isObjectLitKey(node));
  }

  public void testGetFunctionName1() throws Exception {
    Node parent = parse("function name(){}");
    assertGetNameResult(parent.getFirstChild(), "name");
  }

  public void testGetFunctionName2() throws Exception {
    Node parent = parse("var name = function(){}")
        .getFirstFirstChild();

    assertGetNameResult(parent.getFirstChild(), "name");
  }

  public void testGetFunctionName3() throws Exception {
    Node parent = parse("qualified.name = function(){}")
        .getFirstFirstChild();

    assertGetNameResult(parent.getLastChild(), "qualified.name");
  }

  public void testGetFunctionName4() throws Exception {
    Node parent = parse("var name2 = function name1(){}")
        .getFirstFirstChild();

    assertGetNameResult(parent.getFirstChild(), "name2");
  }

  public void testGetFunctionName5() throws Exception {
    Node n = parse("qualified.name2 = function name1(){}");
    Node parent = n.getFirstFirstChild();

    assertGetNameResult(parent.getLastChild(), "qualified.name2");
  }

  public void testGetBestFunctionName1() throws Exception {
    Node parent = parse("function func(){}");

    assertEquals("func",
        NodeUtil.getNearestFunctionName(parent.getFirstChild()));
  }

  public void testGetBestFunctionName2() throws Exception {
    Node parent = parse("var obj = {memFunc(){}}")
        .getFirstFirstChild().getFirstFirstChild();

    assertEquals("memFunc",
        NodeUtil.getNearestFunctionName(parent.getLastChild()));
  }

  public void testConstKeywordNamespace() {
    Node decl = parse("const ns = {};").getFirstChild();
    checkState(decl.isConst(), decl);

    Node nameNode = decl.getFirstChild();
    checkState(nameNode.isName(), nameNode);

    assertThat(NodeUtil.isNamespaceDecl(nameNode)).isTrue();
  }

  private void assertGetNameResult(Node function, String name) {
    assertEquals(Token.FUNCTION, function.getToken());
    assertEquals(name, NodeUtil.getName(function));
  }

  public void testContainsFunctionDeclaration() {
    assertTrue(NodeUtil.containsFunction(getNode("function foo(){}")));
    assertTrue(NodeUtil.containsFunction(getNode("(b?function(){}:null)")));

    assertFalse(NodeUtil.containsFunction(getNode("(b?foo():null)")));
    assertFalse(NodeUtil.containsFunction(getNode("foo()")));
  }

  public void testIsFunctionDeclaration() {
    assertTrue(NodeUtil.isFunctionDeclaration(getFunctionNode("function foo(){}")));
    assertFalse(NodeUtil.isFunctionDeclaration(getFunctionNode("class C { constructor() {} }")));
    assertFalse(NodeUtil.isFunctionDeclaration(getFunctionNode("({ foo() {} })")));
    assertFalse(NodeUtil.isFunctionDeclaration(getFunctionNode("var x = function(){}")));
    assertTrue(NodeUtil.isFunctionDeclaration(getFunctionNode("export function f() {}")));
    assertFalse(NodeUtil.isFunctionDeclaration(getFunctionNode("export default function() {}")));
    assertTrue(
        NodeUtil.isFunctionDeclaration(getFunctionNode("export default function foo() {}")));
    assertFalse(
        NodeUtil.isFunctionDeclaration(getFunctionNode("export default (foo) => { alert(foo); }")));
  }

  public void testIsMethodDeclaration() {
    assertTrue(
        NodeUtil.isMethodDeclaration(getFunctionNode("class C { constructor() {} }")));
    assertTrue(NodeUtil.isMethodDeclaration(getFunctionNode("class C { a() {} }")));
    assertTrue(NodeUtil.isMethodDeclaration(getFunctionNode("class C { static a() {} }")));
    assertTrue(NodeUtil.isMethodDeclaration(getFunctionNode("({ set foo(v) {} })")));
    assertTrue(NodeUtil.isMethodDeclaration(getFunctionNode("({ get foo() {} })")));
    assertTrue(NodeUtil.isMethodDeclaration(getFunctionNode("({ [foo]() {} })")));
  }

  public void testIsClassDeclaration() {
    assertTrue(NodeUtil.isClassDeclaration(getClassNode("class Foo {}")));
    assertFalse(NodeUtil.isClassDeclaration(getClassNode("var Foo = class {}")));
    assertFalse(NodeUtil.isClassDeclaration(getClassNode("var Foo = class Foo{}")));
    assertTrue(NodeUtil.isClassDeclaration(getClassNode("export default class Foo {}")));
    assertTrue(NodeUtil.isClassDeclaration(getClassNode("export class Foo {}")));
    assertFalse(NodeUtil.isClassDeclaration(getClassNode("export default class {}")));
  }

  private void assertSideEffect(boolean se, String js) {
    Node n = parse(js);
    assertEquals(se, NodeUtil.mayHaveSideEffects(n.getFirstChild()));
  }

  private void assertSideEffect(boolean se, Node n) {
    assertEquals(se, NodeUtil.mayHaveSideEffects(n));
  }

  private void assertSideEffect(boolean se, String js, boolean globalRegExp) {
    Node n = parse(js);
    Compiler compiler = new Compiler();
    compiler.initCompilerOptionsIfTesting();
    compiler.setHasRegExpGlobalReferences(globalRegExp);
    assertEquals(se, NodeUtil.mayHaveSideEffects(n.getFirstChild(), compiler));
  }

  public void testMayHaveSideEffects() {
    assertSideEffect(false, "[1]");
    assertSideEffect(false, "[1, 2]");
    assertSideEffect(false, "[...[]]");
    assertSideEffect(false, "[...[1]]");
    assertSideEffect(true, "[...[i++]]");
    assertSideEffect(true, "[...f()]");
    assertSideEffect(false, "({...x})");
    assertSideEffect(false, "({...{}})");
    assertSideEffect(false, "({...{a:1}})");
    assertSideEffect(true, "({...{a:i++}})");
    assertSideEffect(true, "({...{a:f()}})");
    assertSideEffect(true, "({...f()})");
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
    assertSideEffect(true, "(class { [computedName()]() {} })");
    assertSideEffect(false, "(class { [computedName]() {} })");
    assertSideEffect(false, "(class Foo extends Bar { })");
    assertSideEffect(true, "(class extends foo() { })");

    assertSideEffect(false, "a");
    assertSideEffect(false, "a.b");
    assertSideEffect(false, "a.b.c");
    assertSideEffect(false, "[b, c [d, [e]]]");
    assertSideEffect(false, "({a: x, b: y, c: z})");
    assertSideEffect(false, "({a, b, c})");
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
    assertSideEffect(false, "[1, 1].foo;");

    assertSideEffect(true, "export var x = 0;");
    assertSideEffect(true, "export let x = 0;");
    assertSideEffect(true, "export const x = 0;");
    assertSideEffect(true, "export class X {};");
    assertSideEffect(true, "export function x() {};");
    assertSideEffect(true, "export {x};");
  }

  public void testComputedPropSideEffects() {
    assertSideEffect(false, "({[a]: x})");
    assertSideEffect(true, "({[a()]: x})");
    assertSideEffect(true, "({[a]: x()})");

    Node computedProp = parse("({[a]: x})")  // SCRIPT
        .getFirstChild()   // EXPR_RESULT
        .getFirstChild()   // OBJECT_LIT
        .getFirstChild();  // COMPUTED_PROP
    checkState(computedProp.isComputedProp(), computedProp);
    assertSideEffect(false, computedProp);

    computedProp = parse("({[a()]: x})")  // SCRIPT
        .getFirstChild()   // EXPR_RESULT
        .getFirstChild()   // OBJECT_LIT
        .getFirstChild();  // COMPUTED_PROP
    checkState(computedProp.isComputedProp(), computedProp);
    assertSideEffect(true, computedProp);

    computedProp = parse("({[a]: x()})")  // SCRIPT
        .getFirstChild()   // EXPR_RESULT
        .getFirstChild()   // OBJECT_LIT
        .getFirstChild();  // COMPUTED_PROP
    checkState(computedProp.isComputedProp(), computedProp);
    assertSideEffect(true, computedProp);
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
    assertContainsAnonFunc(true, "({x: function a(){}})");
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
    assertContainsAnonFunc(true, "export default function() {};");
    assertContainsAnonFunc(false, "export default function a() {};");
    assertContainsAnonFunc(false, "export function a() {};");
    assertContainsAnonFunc(false, "class C { a() {} }");
    assertContainsAnonFunc(false, "class C { static a() {} }");
    assertContainsAnonFunc(false, "x = { a() {} }");
  }

  private void assertContainsAnonFunc(boolean expected, String js) {
    Node funcParent = findParentOfFuncOrClassDescendant(parse(js), Token.FUNCTION);
    assertNotNull("Expected function node in parse tree of: " + js, funcParent);
    Node funcNode = getFuncOrClassChild(funcParent, Token.FUNCTION);
    assertEquals(expected, NodeUtil.isFunctionExpression(funcNode));
  }

  public void testIsClassExpression() {
    assertContainsAnonClass(true, "(class {})");
    assertContainsAnonClass(true, "[class Clazz {}]");
    assertContainsAnonClass(true, "({x: class Clazz {}})");
    assertContainsAnonClass(true, "x = class Clazz {};");
    assertContainsAnonClass(true, "var x = class Clazz {};");
    assertContainsAnonClass(true, "if (class Clazz {});");
    assertContainsAnonClass(true, "while (class Clazz {});");
    assertContainsAnonClass(true, "do; while (class Clazz {});");
    assertContainsAnonClass(true, "for (class Clazz {};;);");
    assertContainsAnonClass(true, "for (;class Clazz {};);");
    assertContainsAnonClass(true, "for (;;class Clazz {});");
    assertContainsAnonClass(true, "for (p in class Clazz {});");
    assertContainsAnonClass(true, "with (class Clazz {}) {}");
    assertContainsAnonClass(false, "class Clazz {}");
    assertContainsAnonClass(false, "if (x) class Clazz {};");
    assertContainsAnonClass(false, "if (x) { class Clazz {} }");
    assertContainsAnonClass(false, "if (x); else class Clazz {};");
    assertContainsAnonClass(false, "while (x) class Clazz {};");
    assertContainsAnonClass(false, "do class Clazz {} while (0);");
    assertContainsAnonClass(false, "for (;;) class Clazz {}");
    assertContainsAnonClass(false, "for (p in o) class Clazz {};");
    assertContainsAnonClass(false, "with (x) class Clazz {}");
    assertContainsAnonClass(true, "export default class {};");
    assertContainsAnonClass(false, "export default class Clazz {};");
    assertContainsAnonClass(false, "export class Clazz {};");
  }

  private void assertContainsAnonClass(boolean expected, String js) {
    Node classParent = findParentOfFuncOrClassDescendant(parse(js), Token.CLASS);
    assertNotNull("Expected class node in parse tree of: " + js, classParent);
    Node classNode = getFuncOrClassChild(classParent, Token.CLASS);
    assertEquals(expected, NodeUtil.isClassExpression(classNode));
  }

  private Node findParentOfFuncOrClassDescendant(Node n, Token token) {
    checkArgument(token.equals(Token.CLASS) || token.equals(Token.FUNCTION));
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      if (c.getToken().equals(token)) {
        return n;
      }
      Node result = findParentOfFuncOrClassDescendant(c, token);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  private Node getFuncOrClassChild(Node n, Token token) {
    checkArgument(token.equals(Token.CLASS) || token.equals(Token.FUNCTION));
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      if (c.getToken().equals(token)) {
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
    assertEquals(Token.FUNCTION, n.getToken());
    assertTrue(NodeUtil.referencesThis(n));
    assertTrue(NodeUtil.referencesThis(
        parse("b?this:null")));

    assertFalse(NodeUtil.referencesThis(
        parse("a")));
    n = parse("function foo(){}").getFirstChild();
    assertEquals(Token.FUNCTION, n.getToken());
    assertFalse(NodeUtil.referencesThis(n));
    assertFalse(NodeUtil.referencesThis(
        parse("(b?foo():null)")));

    assertTrue(NodeUtil.referencesThis(parse("()=>this")));
    assertTrue(NodeUtil.referencesThis(parse("() => { () => alert(this); }")));
  }

  public void testGetNodeTypeReferenceCount() {
    assertEquals(
        0,
        NodeUtil.getNodeTypeReferenceCount(
            parse("function foo(){}"), Token.THIS, Predicates.<Node>alwaysTrue()));
    assertEquals(
        1,
        NodeUtil.getNodeTypeReferenceCount(
            parse("this"), Token.THIS, Predicates.<Node>alwaysTrue()));
    assertEquals(
        2,
        NodeUtil.getNodeTypeReferenceCount(
            parse("this;function foo(){}(this)"), Token.THIS, Predicates.<Node>alwaysTrue()));
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

  public void testIsNonlocalModuleExportNameOnExports1() {
    Node root = parse("export {localName as exportName};");
    Node moduleBody = root.getFirstChild();
    Node exportNode = moduleBody.getFirstChild();
    Node exportSpecs = exportNode.getFirstChild();
    Node exportSpec = exportSpecs.getFirstChild();

    Node localName = exportSpec.getFirstChild();
    Node exportName = exportSpec.getSecondChild();

    assertFalse(NodeUtil.isNonlocalModuleExportName(localName));
    assertTrue(NodeUtil.isNonlocalModuleExportName(exportName));

  }

  public void testIsNonlocalModuleExportNameOnExports2() {
    Node root = parse("let bar; export {bar};");
    Node moduleBody = root.getFirstChild();
    Node exportNode = moduleBody.getSecondChild();
    Node exportSpecs = exportNode.getFirstChild();
    Node exportSpec = exportSpecs.getFirstChild();

    Node name = exportSpec.getFirstChild();

    // bar is defined locally, so isNonlocalModuleExportName is false.
    assertFalse(NodeUtil.isNonlocalModuleExportName(name));
  }

  public void testIsNonlocalModuleExportNameOnImports1() {
    Node root = parse("import {exportName as localName} from './foo.js';");
    Node moduleBody = root.getFirstChild();
    Node importNode = moduleBody.getFirstChild();
    Node importSpecs = importNode.getSecondChild();
    Node importSpec = importSpecs.getFirstChild();

    Node exportName = importSpec.getFirstChild();
    Node localName = importSpec.getSecondChild();

    assertTrue(NodeUtil.isNonlocalModuleExportName(exportName));
    assertFalse(NodeUtil.isNonlocalModuleExportName(localName));
  }

  public void testIsNonlocalModuleExportNameOnImports2() {
    Node root = parse("import {bar} from './foo.js';");
    Node moduleBody = root.getFirstChild();
    Node importNode = moduleBody.getFirstChild();
    Node importSpecs = importNode.getSecondChild();
    Node importSpec = importSpecs.getFirstChild();

    Node name = importSpec.getSecondChild();

    // bar is defined locally so isNonlocalModuleExportName is false
    assertFalse(NodeUtil.isNonlocalModuleExportName(name));
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
    // Test removing the block that contains the catch clause.
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

  public void testRemoveFromImport() {
    // Remove imported function
    Node actual = parse("import foo from './foo';");
    Node moduleBody = actual.getFirstChild();
    Node importNode = moduleBody.getFirstChild();
    Node functionFoo = importNode.getFirstChild();

    NodeUtil.removeChild(importNode, functionFoo);
    String expected = "import './foo';";
    String difference = parse(expected).checkTreeEquals(actual);
    if (difference != null) {
      fail("Nodes do not match:\n" + difference);
    }
  }

  public void testRemoveParamChild1() {
    // Remove traditional parameter
    Node actual = parse("function f(p1) {}");
    Node functionNode = actual.getFirstChild();
    Node paramList = functionNode.getFirstChild().getNext();
    Node p1 = paramList.getFirstChild();

    NodeUtil.removeChild(paramList, p1);
    String expected = "function f() {}";
    String difference = parse(expected).checkTreeEquals(actual);
    if (difference != null) {
      fail("Nodes do not match:\n" + difference);
    }
  }

  public void testRemoveParamChild2() {
    // Remove default parameter
    Node actual = parse("function f(p1 = 0, p2) {}");
    Node functionNode = actual.getFirstChild();
    Node paramList = functionNode.getFirstChild().getNext();
    Node p1 = paramList.getFirstChild();

    NodeUtil.removeChild(paramList, p1);
    String expected = "function f(p2) {}";
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

  public void testRemoveLetChild() {
    // Test removing the first child.
    Node actual = parse("let foo, goo, hoo");

    Node letNode = actual.getFirstChild();
    Node nameNode = letNode.getFirstChild();

    NodeUtil.removeChild(letNode, nameNode);
    String expected = "let goo, hoo";
    String difference = parse(expected).checkTreeEquals(actual);
    if (difference != null) {
      fail("Nodes do not match:\n" + difference);
    }

    // Test removing the second child.
    actual = parse("let foo, goo, hoo");

    letNode = actual.getFirstChild();
    nameNode = letNode.getSecondChild();

    NodeUtil.removeChild(letNode, nameNode);
    expected = "let foo, hoo";
    difference = parse(expected).checkTreeEquals(actual);
    if (difference != null) {
      fail("Nodes do not match:\n" + difference);
    }

    // Test removing the last child of several children.
    actual = parse("let foo, hoo");

    letNode = actual.getFirstChild();
    nameNode = letNode.getSecondChild();

    NodeUtil.removeChild(letNode, nameNode);
    expected = "let foo";
    difference = parse(expected).checkTreeEquals(actual);
    if (difference != null) {
      fail("Nodes do not match:\n" + difference);
    }

    // Test removing the last.
    actual = parse("let hoo");

    letNode = actual.getFirstChild();
    nameNode = letNode.getFirstChild();

    NodeUtil.removeChild(letNode, nameNode);
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

  public void testRemovePatternChild() {
    // remove variable declaration from object pattern
    Node actual = parse("var {a, b, c} = {a:1, b:2, c:3}");
    Node varNode = actual.getFirstChild();
    Node destructure = varNode.getFirstChild();
    Node pattern = destructure.getFirstChild();
    Node a = pattern.getFirstChild();
    Node b = a.getNext();
    Node c = b.getNext();

    NodeUtil.removeChild(pattern, a);
    String expected = "var {b, c} = {a:1, b:2, c:3};";
    String difference = parse(expected).checkTreeEquals(actual);
    assertNull("Nodes do not match:\n" + difference, difference);

    // Remove all entries in object pattern
    NodeUtil.removeChild(pattern, b);
    NodeUtil.removeChild(pattern, c);
    expected = "var { } = {a:1, b:2, c:3};";
    difference = parse(expected).checkTreeEquals(actual);
    assertNull("Nodes do not match:\n" + difference, difference);

    // remove variable declaration from array pattern
    actual = parse("var [a, b] = [1, 2]");
    varNode = actual.getFirstChild();
    destructure = varNode.getFirstChild();
    pattern = destructure.getFirstChild();
    a = pattern.getFirstChild();

    NodeUtil.removeChild(pattern, a);
    expected = "var [ , b] = [1, 2];";
    difference = parse(expected).checkTreeEquals(actual);
    assertNull("Nodes do not match:\n" + difference, difference);
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

  private static void replaceDeclChild(String js, int declarationChild, String expected) {
    Node actual = parse(js);
    Node declarationNode = actual.getFirstChild();
    Node nameNode = declarationNode.getChildAtIndex(declarationChild);

    NodeUtil.replaceDeclarationChild(nameNode, IR.block());
    String difference = parse(expected).checkTreeEquals(actual);
    assertNull("Nodes do not match:\n" + difference, difference);
  }

  public void testReplaceDeclarationName() {
    replaceDeclChild("var x;", 0, "{}");
    replaceDeclChild("var x, y;", 0, "{} var y;");
    replaceDeclChild("var x, y;", 1, "var x; {}");
    replaceDeclChild("let x, y, z;", 0, "{} let y, z;");
    replaceDeclChild("let x, y, z;", 1, "let x; {} let z;");
    replaceDeclChild("let x, y, z;", 2, "let x, y; {}");
    replaceDeclChild("const x = 1, y = 2, z = 3;", 1, "const x = 1; {} const z = 3;");
    replaceDeclChild("const x =1, y = 2, z = 3, w = 4;", 1, "const x = 1; {} const z = 3, w = 4;");
  }

  public void testTryMergeBlock1() {
    // Test removing the initializer.
    Node actual = parse("{{a();b();}}");

    Node parentBlock = actual.getFirstChild();
    Node childBlock = parentBlock.getFirstChild();

    assertTrue(NodeUtil.tryMergeBlock(childBlock, false));
    String expected = "{a();b();}";
    String difference = parse(expected).checkTreeEquals(actual);
    assertNull("Nodes do not match:\n" + difference, difference);
  }

  public void testTryMergeBlock2() {
    // Test removing the initializer.
    Node actual = parse("foo:{a();}");

    Node parentLabel = actual.getFirstChild();
    Node childBlock = parentLabel.getLastChild();

    assertFalse(NodeUtil.tryMergeBlock(childBlock, false));
  }

  public void testTryMergeBlock3() {
    // Test removing the initializer.
    String code = "foo:{a();boo()}";
    Node actual = parse("foo:{a();boo()}");

    Node parentLabel = actual.getFirstChild();
    Node childBlock = parentLabel.getLastChild();

    assertFalse(NodeUtil.tryMergeBlock(childBlock, false));
    String expected = code;
    String difference = parse(expected).checkTreeEquals(actual);
    assertNull("Nodes do not match:\n" + difference, difference);
  }

  public void testTryMergeBlock4() {
    Node actual = parse("{const module$exports$Foo=class{}}");
    String expected = "const module$exports$Foo=class{}";

    Node block = actual.getFirstChild();

    assertTrue(NodeUtil.tryMergeBlock(block, true));
    String difference = parse(expected).checkTreeEquals(actual);
    assertNull("Nodes do not match:\n" + difference, difference);
  }

  public void testCanMergeBlock1() {
    Node actual = parse("{a(); let x;}");

    Node block = actual.getFirstChild();

    assertFalse(NodeUtil.canMergeBlock(block));
  }

  public void testCanMergeBlock2() {
    Node actual = parse("{a(); f(); var x; {const y = 2;}}");

    Node parentBlock = actual.getFirstChild();
    Node childBlock = parentBlock.getLastChild();

    assertTrue(NodeUtil.canMergeBlock(parentBlock));
    assertFalse(NodeUtil.canMergeBlock(childBlock));
  }

  public void testGetSourceName() {
    Node n = new Node(Token.BLOCK);
    Node parent = new Node(Token.BLOCK, n);
    parent.setSourceFileForTesting("foo");

    assertEquals("foo", NodeUtil.getSourceName(n));
  }

  public void testLocalValue1() throws Exception {
    // Names are not known to be local.
    assertFalse(NodeUtil.evaluatesToLocalValue(getNode("x")));
    assertFalse(NodeUtil.evaluatesToLocalValue(getNode("x()")));
    assertFalse(NodeUtil.evaluatesToLocalValue(getNode("this")));
    assertFalse(NodeUtil.evaluatesToLocalValue(getNode("arguments")));

    // We can't know if new objects are local unless we know
    // that they don't alias themselves.
    // TODO(tdeegan): Revisit this.
    assertFalse(NodeUtil.evaluatesToLocalValue(getNode("new x()")));

    // property references are assumed to be non-local
    assertFalse(NodeUtil.evaluatesToLocalValue(getNode("(new x()).y")));
    assertFalse(NodeUtil.evaluatesToLocalValue(getNode("(new x())['y']")));

    // Primitive values are local
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("null")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("undefined")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("Infinity")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("NaN")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("1")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("'a'")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("true")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("false")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("[]")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("{}")));

    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("[x]")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("{'a':x}")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("{'a': {'b': 2}}")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("{'a': {'b': global}}")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("{get someGetter() { return 1; }}")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("{get someGetter() { return global; }}")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("{set someSetter(value) {}}")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("{[someComputedProperty]: {}}")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("{[someComputedProperty]: global}")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("{[someComputedProperty]: {'a':x}}")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("{[someComputedProperty]: {'a':1}}")));

    // increment/decrement results in primitive number, the previous value is
    // always coersed to a number (even in the post.
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("++x")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("--x")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("x++")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("x--")));

    // The left side of an only assign matters if it is an alias or mutable.
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("x=1")));
    assertFalse(NodeUtil.evaluatesToLocalValue(getNode("x=[]")));
    assertFalse(NodeUtil.evaluatesToLocalValue(getNode("x=y")));
    // The right hand side of assignment opts don't matter, as they force
    // a local result.
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("x+=y")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("x*=y")));
    // Comparisons always result in locals, as they force a local boolean
    // result.
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("x==y")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("x!=y")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("x>y")));
    // Only the right side of a comma matters
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("(1,2)")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("(x,1)")));
    assertFalse(NodeUtil.evaluatesToLocalValue(getNode("(x,y)")));

    // Both the operands of OR matter
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("1||2")));
    assertFalse(NodeUtil.evaluatesToLocalValue(getNode("x||1")));
    assertFalse(NodeUtil.evaluatesToLocalValue(getNode("x||y")));
    assertFalse(NodeUtil.evaluatesToLocalValue(getNode("1||y")));

    // Both the operands of AND matter
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("1&&2")));
    assertFalse(NodeUtil.evaluatesToLocalValue(getNode("x&&1")));
    assertFalse(NodeUtil.evaluatesToLocalValue(getNode("x&&y")));
    assertFalse(NodeUtil.evaluatesToLocalValue(getNode("1&&y")));

    // Only the results of HOOK matter
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("x?1:2")));
    assertFalse(NodeUtil.evaluatesToLocalValue(getNode("x?x:2")));
    assertFalse(NodeUtil.evaluatesToLocalValue(getNode("x?1:x")));
    assertFalse(NodeUtil.evaluatesToLocalValue(getNode("x?x:y")));

    // Results of ops are local values
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("!y")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("~y")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("y + 1")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("y + z")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("y * z")));

    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("'a' in x")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("typeof x")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("x instanceof y")));

    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("void x")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("void 0")));

    assertFalse(NodeUtil.evaluatesToLocalValue(getNode("{}.x")));

    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("{}.toString()")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("o.toString()")));

    assertFalse(NodeUtil.evaluatesToLocalValue(getNode("o.valueOf()")));

    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("delete a.b")));
  }

  public void testLocalValueTemplateLit() {
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("`hello`")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("`hello ${name}`")));
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("`${'name'}`")));
    assertTrue(NodeUtil.isLiteralValue(getNode("`${'name'}`"), false));
    assertTrue(NodeUtil.isImmutableValue(getNode("`${'name'}`")));
  }

  public void testLocalValueTaggedTemplateLit1() {
    Node n = getNode("tag`simple string`");
    assertFalse(NodeUtil.evaluatesToLocalValue(n));

    // Set 'tag' function as producing a local result
    Node.SideEffectFlags flags = new Node.SideEffectFlags();
    flags.clearAllFlags();
    n.setSideEffectFlags(flags);

    assertTrue(NodeUtil.evaluatesToLocalValue(n));
  }

  public void testLocalValueTaggedTemplateLit2() {
    // Here, replacement() may have side effects.
    Node n = getNode("tag`string with ${replacement()}`");
    assertFalse(NodeUtil.evaluatesToLocalValue(n));

    // Set 'tag' function as producing a local result
    Node.SideEffectFlags flags = new Node.SideEffectFlags();
    flags.clearAllFlags();
    n.setSideEffectFlags(flags);

    assertTrue(NodeUtil.evaluatesToLocalValue(n));
  }

  public void testLocalValueNewExpr() {
    Node newExpr = getNode("new x()");
    assertFalse(NodeUtil.evaluatesToLocalValue(newExpr));

    checkState(newExpr.isNew());
    Node.SideEffectFlags flags = new Node.SideEffectFlags();

    flags.clearAllFlags();
    newExpr.setSideEffectFlags(flags);

    assertTrue(NodeUtil.evaluatesToLocalValue(newExpr));

    flags.clearAllFlags();
    flags.setMutatesThis();
    newExpr.setSideEffectFlags(flags);

    assertTrue(NodeUtil.evaluatesToLocalValue(newExpr));

    flags.clearAllFlags();
    flags.setReturnsTainted();
    newExpr.setSideEffectFlags(flags);

    assertTrue(NodeUtil.evaluatesToLocalValue(newExpr));

    flags.clearAllFlags();
    flags.setThrows();
    newExpr.setSideEffectFlags(flags);

    assertFalse(NodeUtil.evaluatesToLocalValue(newExpr));

    flags.clearAllFlags();
    flags.setMutatesArguments();
    newExpr.setSideEffectFlags(flags);

    assertFalse(NodeUtil.evaluatesToLocalValue(newExpr));

    flags.clearAllFlags();
    flags.setMutatesGlobalState();
    newExpr.setSideEffectFlags(flags);

    assertFalse(NodeUtil.evaluatesToLocalValue(newExpr));
  }

  public void testLocalValueSpread() {
    // Array literals are always known local values.
    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("[...x]")));
    assertFalse(NodeUtil.isLiteralValue(getNode("[...x]"), false));
    assertFalse(NodeUtil.isImmutableValue(getNode("[...x]")));

    assertTrue(NodeUtil.evaluatesToLocalValue(getNode("{...x}")));
    assertFalse(NodeUtil.isLiteralValue(getNode("{...x}"), false));
    assertFalse(NodeUtil.isImmutableValue(getNode("{...x}")));
  }

  public void testLocalValueAwait() {
    Node expr;
    expr = getAwaitNode("async function f() { await someAsyncAction(); }");
    assertFalse(NodeUtil.evaluatesToLocalValue(expr));

    expr = getAwaitNode("async function f() { await {then:function() { return p }}; }");
    assertFalse(NodeUtil.evaluatesToLocalValue(expr));

    // it isn't clear why someone would want to wait on a non-thenable value...
    expr = getAwaitNode("async function f() { await 5; }");
    assertFalse(NodeUtil.evaluatesToLocalValue(expr));
  }

  public void testLocalValueYield() {
    Node expr;
    expr = getYieldNode("function *f() { yield; }");
    assertFalse(NodeUtil.evaluatesToLocalValue(expr));

    expr = getYieldNode("function *f() { yield 'something'; }");
    assertFalse(NodeUtil.evaluatesToLocalValue(expr));
  }

  public void testCallSideEffects() {
    Node callExpr = getNode("new x().method()");
    assertTrue(NodeUtil.functionCallHasSideEffects(callExpr));

    Node newExpr = callExpr.getFirstFirstChild();
    checkState(newExpr.isNew());
    Node.SideEffectFlags flags = new Node.SideEffectFlags();

    // No side effects, local result
    flags.clearAllFlags();
    newExpr.setSideEffectFlags(flags);
    flags.clearAllFlags();
    callExpr.setSideEffectFlags(flags);

    assertTrue(NodeUtil.evaluatesToLocalValue(callExpr));
    assertFalse(NodeUtil.functionCallHasSideEffects(callExpr));
    assertFalse(NodeUtil.mayHaveSideEffects(callExpr));

    // Modifies this, local result
    flags.clearAllFlags();
    newExpr.setSideEffectFlags(flags);
    flags.clearAllFlags();
    flags.setMutatesThis();
    callExpr.setSideEffectFlags(flags);

    assertTrue(NodeUtil.evaluatesToLocalValue(callExpr));
    assertFalse(NodeUtil.functionCallHasSideEffects(callExpr));
    assertFalse(NodeUtil.mayHaveSideEffects(callExpr));

    // Modifies this, non-local result
    flags.clearAllFlags();
    newExpr.setSideEffectFlags(flags);
    flags.clearAllFlags();
    flags.setMutatesThis();
    flags.setReturnsTainted();
    callExpr.setSideEffectFlags(flags);

    assertFalse(NodeUtil.evaluatesToLocalValue(callExpr));
    assertFalse(NodeUtil.functionCallHasSideEffects(callExpr));
    assertFalse(NodeUtil.mayHaveSideEffects(callExpr));

    // No modifications, non-local result
    flags.clearAllFlags();
    newExpr.setSideEffectFlags(flags);
    flags.clearAllFlags();
    flags.setReturnsTainted();
    callExpr.setSideEffectFlags(flags);

    assertFalse(NodeUtil.evaluatesToLocalValue(callExpr));
    assertFalse(NodeUtil.functionCallHasSideEffects(callExpr));
    assertFalse(NodeUtil.mayHaveSideEffects(callExpr));

    // The new modifies global state, no side-effect call, non-local result
    // This call could be removed, but not the new.
    flags.clearAllFlags();
    flags.setMutatesGlobalState();
    newExpr.setSideEffectFlags(flags);
    flags.clearAllFlags();
    callExpr.setSideEffectFlags(flags);

    assertTrue(NodeUtil.evaluatesToLocalValue(callExpr));
    assertFalse(NodeUtil.functionCallHasSideEffects(callExpr));
    assertTrue(NodeUtil.mayHaveSideEffects(callExpr));
  }

  public void testValidDefine() {
    assertTrue(getIsValidDefineValueResultFor("1"));
    assertTrue(getIsValidDefineValueResultFor("-3"));
    assertTrue(getIsValidDefineValueResultFor("true"));
    assertTrue(getIsValidDefineValueResultFor("false"));
    assertTrue(getIsValidDefineValueResultFor("'foo'"));

    assertFalse(getIsValidDefineValueResultFor("x"));
    assertFalse(getIsValidDefineValueResultFor("null"));
    assertFalse(getIsValidDefineValueResultFor("undefined"));
    assertFalse(getIsValidDefineValueResultFor("NaN"));

    assertTrue(getIsValidDefineValueResultFor("!true"));
    assertTrue(getIsValidDefineValueResultFor("-true"));
    assertTrue(getIsValidDefineValueResultFor("1 & 8"));
    assertTrue(getIsValidDefineValueResultFor("1 + 8"));
    assertTrue(getIsValidDefineValueResultFor("'a' + 'b'"));
    assertTrue(getIsValidDefineValueResultFor("true ? 'a' : 'b'"));

    assertFalse(getIsValidDefineValueResultFor("1 & foo"));
    assertFalse(getIsValidDefineValueResultFor("foo ? 'a' : 'b'"));
  }

  private boolean getIsValidDefineValueResultFor(String js) {
    Node script = parse("var test = " + js + ";");
    Node var = script.getFirstChild();
    Node name = var.getFirstChild();
    Node value = name.getFirstChild();

    ImmutableSet<String> defines = ImmutableSet.of();
    return NodeUtil.isValidDefineValue(value, defines);
  }

  public void testGetOctalNumberValue() {
    assertEquals(18.0, NodeUtil.getNumberValue(getNode("022")), 0.0);
  }

  @SuppressWarnings("JUnit3FloatingPointComparisonWithoutDelta")
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

  public void testIsNumericResult() {
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

  /**
   * When the left side is a destructuring pattern, generally it's not possible to identify the RHS
   * for a specific name on the LHS.
   */
  public void testGetRValueOfLValueInDestructuringPattern() {
    assertThat(NodeUtil.getRValueOfLValue(getNameNode(parse("var [x] = rhs;"), "x"))).isNull();
    assertThat(NodeUtil.getRValueOfLValue(getNameNode(parse("var [x, y] = rhs;"), "x"))).isNull();
    assertThat(NodeUtil.getRValueOfLValue(getNameNode(parse("var [y, x] = rhs;"), "x"))).isNull();
    assertThat(NodeUtil.getRValueOfLValue(getNameNode(parse("var {x: x} = rhs;"), "x"))).isNull();
    assertThat(NodeUtil.getRValueOfLValue(getNameNode(parse("var {y: x} = rhs;"), "x"))).isNull();

    Node ast = parse("var {x} = rhs;");
    Node x = ast.getFirstChild()   // VAR
                .getFirstChild()   // DESTRUCTURING_LHS
                .getFirstChild()   // OBJECT_PATTERN
                .getFirstChild();  //STRING_KEY
    checkState(x.isStringKey(), x);
    assertThat(NodeUtil.getRValueOfLValue(x)).isNull();
  }

  public void testGetRValueOfLValueDestructuringPattern() {
    assertNode(NodeUtil.getRValueOfLValue(getPattern(parse("var [x] = 'rhs';"))))
        .hasType(Token.STRING);
    assertNode(NodeUtil.getRValueOfLValue(getPattern(parse("var [x, y] = 'rhs';"))))
        .hasType(Token.STRING);
    assertNode(NodeUtil.getRValueOfLValue(getPattern(parse("var [y, x] = 'rhs';"))))
        .hasType(Token.STRING);
    assertNode(NodeUtil.getRValueOfLValue(getPattern(parse("var {x: x} = 'rhs';"))))
        .hasType(Token.STRING);
    assertNode(NodeUtil.getRValueOfLValue(getPattern(parse("var {y: x} = 'rhs';"))))
        .hasType(Token.STRING);
    assertNode(NodeUtil.getRValueOfLValue(getPattern(parse("var {x} = 'rhs';"))))
        .hasType(Token.STRING);
  }

  public void testGetRValueOfLValueDestructuringLhs() {
    assertNode(NodeUtil.getRValueOfLValue(getDestructuringLhs(parse("var [x] = 'rhs';"))))
        .hasType(Token.STRING);
    assertNode(NodeUtil.getRValueOfLValue(getDestructuringLhs(parse("var [x, y] = 'rhs';"))))
        .hasType(Token.STRING);
    assertNode(NodeUtil.getRValueOfLValue(getDestructuringLhs(parse("var [y, x] = 'rhs';"))))
        .hasType(Token.STRING);
    assertNode(NodeUtil.getRValueOfLValue(getDestructuringLhs(parse("var {x: x} = 'rhs';"))))
        .hasType(Token.STRING);
    assertNode(NodeUtil.getRValueOfLValue(getDestructuringLhs(parse("var {y: x} = 'rhs';"))))
        .hasType(Token.STRING);
    assertNode(NodeUtil.getRValueOfLValue(getDestructuringLhs(parse("var {x} = 'rhs';"))))
        .hasType(Token.STRING);
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
        parse("({x} = obj)").getFirstFirstChild().getFirstFirstChild().getFirstChild());
    assertLValueNamedX(
        parse("([x] = obj)").getFirstFirstChild().getFirstFirstChild());
    assertLValueNamedX(
        parse("function foo (...x) {}").getFirstChild().getSecondChild().getFirstFirstChild());
    assertLValueNamedX(
        parse("({[0]: x} = obj)").getFirstFirstChild().getFirstFirstChild().getSecondChild());
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

  public void testIsNestedObjectPattern() {
    Node root = parse("var {a, b} = {a:1, b:2}");
    Node destructuring = root.getFirstFirstChild();
    Node objPattern = destructuring.getFirstChild();
    assertFalse(NodeUtil.isNestedObjectPattern(objPattern));

    root = parse("var {a, b:{c}} = {a:{}, b:{c:5}};");
    destructuring = root.getFirstFirstChild();
    objPattern = destructuring.getFirstChild();
    assertTrue(NodeUtil.isNestedObjectPattern(objPattern));
  }

  public void testIsNestedArrayPattern() {
    Node root = parse("var [a, b] = [1, 2]");
    Node destructuring = root.getFirstFirstChild();
    Node arrayPattern = destructuring.getFirstChild();
    assertFalse(NodeUtil.isNestedArrayPattern(arrayPattern));
  }

  public void testLhsByDestructuring1() {
    Node root = parse("var [a, b] = obj;");
    Node destructLhs = root.getFirstFirstChild();
    checkArgument(destructLhs.isDestructuringLhs());
    Node destructPat = destructLhs.getFirstChild();
    checkArgument(destructPat.isArrayPattern());

    Node nameNodeA = destructPat.getFirstChild();
    Node nameNodeB = nameNodeA.getNext();
    checkState(nameNodeA.getString().equals("a"), nameNodeA);
    checkState(nameNodeB.getString().equals("b"), nameNodeB);

    Node nameNodeObj = destructPat.getNext();
    checkState(nameNodeObj.getString().equals("obj"), nameNodeObj);

    assertLhsByDestructuring(nameNodeA);
    assertLhsByDestructuring(nameNodeB);
    assertNotLhsByDestructuring(nameNodeObj);
  }

  public void testLhsByDestructuring1b() {
    Node root = parse("var {a: c, b: d} = obj;");
    Node destructLhs = root.getFirstFirstChild();
    checkArgument(destructLhs.isDestructuringLhs());
    Node destructPat = destructLhs.getFirstChild();
    checkArgument(destructPat.isObjectPattern());

    Node strKeyNodeA = destructPat.getFirstChild();
    Node strKeyNodeB = strKeyNodeA.getNext();
    Node nameNodeC = strKeyNodeA.getFirstChild();
    Node nameNodeD = strKeyNodeB.getFirstChild();
    checkState(strKeyNodeA.getString().equals("a"), strKeyNodeA);
    checkState(strKeyNodeB.getString().equals("b"), strKeyNodeB);
    checkState(nameNodeC.getString().equals("c"), nameNodeC);
    checkState(nameNodeD.getString().equals("d"), nameNodeD);

    Node nameNodeObj = destructPat.getNext();
    checkState(nameNodeObj.getString().equals("obj"), nameNodeObj);

    assertNotLhsByDestructuring(strKeyNodeA);
    assertNotLhsByDestructuring(strKeyNodeB);
    assertLhsByDestructuring(nameNodeC);
    assertLhsByDestructuring(nameNodeD);
    assertNotLhsByDestructuring(nameNodeObj);
  }

  public void testLhsByDestructuring1c() {
    Node root = parse("var {a, b} = obj;");
    Node destructLhs = root.getFirstFirstChild();
    checkArgument(destructLhs.isDestructuringLhs());
    Node destructPat = destructLhs.getFirstChild();
    checkArgument(destructPat.isObjectPattern());

    Node strKeyNodeA = destructPat.getFirstChild();
    Node strKeyNodeB = strKeyNodeA.getNext();
    Node nameNodeA = strKeyNodeA.getFirstChild();
    Node nameNodeB = strKeyNodeB.getFirstChild();
    checkState(strKeyNodeA.getString().equals("a"), strKeyNodeA);
    checkState(nameNodeA.getString().equals("a"), nameNodeA);
    checkState(strKeyNodeB.getString().equals("b"), strKeyNodeB);
    checkState(nameNodeB.getString().equals("b"), nameNodeB);

    Node nameNodeObj = destructPat.getNext();
    checkState(nameNodeObj.getString().equals("obj"), nameNodeObj);

    assertNotLhsByDestructuring(strKeyNodeA);
    assertNotLhsByDestructuring(strKeyNodeB);
    assertLhsByDestructuring(nameNodeA);
    assertLhsByDestructuring(nameNodeB);
    assertNotLhsByDestructuring(nameNodeObj);
  }

  public void testLhsByDestructuring1d() {
    Node root = parse("var {a = defaultValue} = obj;");
    Node destructLhs = root.getFirstFirstChild();
    checkState(destructLhs.isDestructuringLhs(), destructLhs);
    Node destructPat = destructLhs.getFirstChild();
    checkState(destructPat.isObjectPattern(), destructPat);

    Node strKeyNodeA = destructPat.getFirstChild();
    checkState(strKeyNodeA.isStringKey());
    checkState(strKeyNodeA.getString().equals("a"), strKeyNodeA);

    Node defaultNodeA = strKeyNodeA.getFirstChild();
    checkState(defaultNodeA.isDefaultValue(), defaultNodeA);
    Node nameNodeA = defaultNodeA.getFirstChild();
    checkState(nameNodeA.getString().equals("a"), nameNodeA);

    Node nameNodeDefault = defaultNodeA.getSecondChild();
    checkState(nameNodeDefault.getString().equals("defaultValue"), nameNodeDefault);
    Node nameNodeObj = destructPat.getNext();
    checkState(nameNodeObj.getString().equals("obj"), nameNodeObj);

    assertNotLhsByDestructuring(strKeyNodeA);
    assertNotLhsByDestructuring(defaultNodeA);
    assertLhsByDestructuring(nameNodeA);
    assertNotLhsByDestructuring(nameNodeDefault);
    assertNotLhsByDestructuring(nameNodeObj);
  }

  public void testLhsByDestructuring1e() {
    Node root = parse("var {a: b = defaultValue} = obj;");
    Node destructLhs = root.getFirstFirstChild();
    checkState(destructLhs.isDestructuringLhs(), destructLhs);
    Node destructPat = destructLhs.getFirstChild();
    checkState(destructPat.isObjectPattern(), destructPat);

    Node strKeyNodeA = destructPat.getFirstChild();
    checkState(strKeyNodeA.getString().equals("a"), strKeyNodeA);

    Node defaultNodeA = strKeyNodeA.getOnlyChild();
    checkState(defaultNodeA.isDefaultValue(), defaultNodeA);

    Node nameNodeB = defaultNodeA.getFirstChild();
    checkState(nameNodeB.getString().equals("b"), nameNodeB);

    Node nameNodeDefaultValue = defaultNodeA.getSecondChild();
    checkState(nameNodeDefaultValue.getString().equals("defaultValue"), nameNodeDefaultValue);
    Node nameNodeObj = destructPat.getNext();
    checkState(nameNodeObj.getString().equals("obj"), nameNodeObj);

    assertNotLhsByDestructuring(strKeyNodeA);
    assertLhsByDestructuring(nameNodeB);
    assertNotLhsByDestructuring(nameNodeDefaultValue);
    assertNotLhsByDestructuring(nameNodeObj);
  }

  public void testLhsByDestructuring1f() {
    Node root = parse("var [a  = defaultValue] = arr;");
    Node destructLhs = root.getFirstFirstChild();
    checkState(destructLhs.isDestructuringLhs(), destructLhs);
    Node destructPat = destructLhs.getFirstChild();
    checkState(destructPat.isArrayPattern(), destructPat);

    Node defaultNodeA = destructPat.getFirstChild();

    checkState(defaultNodeA.isDefaultValue(), defaultNodeA);

    Node nameNodeA = defaultNodeA.getFirstChild();
    checkState(nameNodeA.getString().equals("a"), nameNodeA);

    Node nameNodeDefault = defaultNodeA.getSecondChild();
    checkState(nameNodeDefault.getString().equals("defaultValue"), nameNodeDefault);
    Node nameNodeObj = destructPat.getNext();
    checkState(nameNodeObj.getString().equals("arr"), nameNodeObj);

    assertLhsByDestructuring(nameNodeA);
    assertNotLhsByDestructuring(nameNodeDefault);
    assertNotLhsByDestructuring(nameNodeObj);
  }

  public void testLhsByDestructuring2() {
    Node root = parse("var [a, [b, c]] = obj;");
    Node destructLhs = root.getFirstFirstChild();
    checkArgument(destructLhs.isDestructuringLhs());
    Node destructPat = destructLhs.getFirstChild();
    checkArgument(destructPat.isArrayPattern());

    Node nameNodeA = destructPat.getFirstChild();
    Node nameNodeB = nameNodeA.getNext().getFirstChild();
    Node nameNodeC = nameNodeB.getNext();
    checkState(nameNodeA.getString().equals("a"), nameNodeA);
    checkState(nameNodeB.getString().equals("b"), nameNodeB);
    checkState(nameNodeC.getString().equals("c"), nameNodeC);

    Node nameNodeObj = destructPat.getNext();
    checkState(nameNodeObj.getString().equals("obj"), nameNodeObj);

    assertLhsByDestructuring(nameNodeA);
    assertLhsByDestructuring(nameNodeB);
    assertLhsByDestructuring(nameNodeC);
    assertNotLhsByDestructuring(nameNodeObj);
  }

  public void testLhsByDestructuring2b() {
    Node root = parse("var {a: e, b: {c: f, d: g}} = obj;");
    Node destructLhs = root.getFirstFirstChild();
    checkArgument(destructLhs.isDestructuringLhs());
    Node destructPat = destructLhs.getFirstChild();
    checkArgument(destructPat.isObjectPattern());

    Node strKeyNodeA = destructPat.getFirstChild();
    Node strKeyNodeB = strKeyNodeA.getNext();
    Node strKeyNodeC = strKeyNodeB.getFirstFirstChild();
    Node strKeyNodeD = strKeyNodeC.getNext();
    Node nameNodeE = strKeyNodeA.getFirstChild();
    Node nameNodeF = strKeyNodeC.getFirstChild();
    Node nameNodeG = strKeyNodeD.getFirstChild();
    checkState(strKeyNodeA.getString().equals("a"), strKeyNodeA);
    checkState(strKeyNodeB.getString().equals("b"), strKeyNodeB);
    checkState(strKeyNodeC.getString().equals("c"), strKeyNodeC);
    checkState(strKeyNodeD.getString().equals("d"), strKeyNodeD);
    checkState(nameNodeE.getString().equals("e"), nameNodeE);
    checkState(nameNodeF.getString().equals("f"), nameNodeF);
    checkState(nameNodeG.getString().equals("g"), nameNodeG);

    Node nameNodeObj = destructPat.getNext();
    checkState(nameNodeObj.getString().equals("obj"), nameNodeObj);

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
    checkArgument(destructLhs.isDestructuringLhs());
    Node destructPat = destructLhs.getFirstChild();
    checkArgument(destructPat.isArrayPattern());

    Node nameNodeA = destructPat.getFirstChild();
    Node nameNodeB = nameNodeA.getNext();
    checkState(nameNodeA.getString().equals("a"), nameNodeA);
    checkState(nameNodeB.getString().equals("b"), nameNodeB);

    Node nameNodeC = destructLhs.getLastChild().getFirstChild();
    Node nameNodeD = nameNodeC.getNext();
    checkState(nameNodeC.getString().equals("c"), nameNodeC);
    checkState(nameNodeD.getString().equals("d"), nameNodeD);

    assertLhsByDestructuring(nameNodeA);
    assertLhsByDestructuring(nameNodeB);
    assertNotLhsByDestructuring(nameNodeC);
    assertNotLhsByDestructuring(nameNodeD);
  }

  public void testLhsByDestructuring3b() {
    Node root = parse("var {a: c, b: d} = {a: 1, b: 2};");
    Node destructLhs = root.getFirstFirstChild();
    checkArgument(destructLhs.isDestructuringLhs());
    Node destructPat = destructLhs.getFirstChild();
    checkArgument(destructPat.isObjectPattern());

    Node strKeyNodeA = destructPat.getFirstChild();
    Node strKeyNodeB = strKeyNodeA.getNext();
    Node nameNodeC = strKeyNodeA.getFirstChild();
    Node nameNodeD = strKeyNodeB.getFirstChild();
    checkState(strKeyNodeA.getString().equals("a"), strKeyNodeA);
    checkState(strKeyNodeB.getString().equals("b"), strKeyNodeB);
    checkState(nameNodeC.getString().equals("c"), nameNodeC);
    checkState(nameNodeD.getString().equals("d"), nameNodeD);

    assertNotLhsByDestructuring(strKeyNodeA);
    assertNotLhsByDestructuring(strKeyNodeB);
    assertLhsByDestructuring(nameNodeC);
    assertLhsByDestructuring(nameNodeD);
  }

  public void testLhsByDestructuring4() {
    Node root = parse("for ([a, b] of X){}");
    Node destructPat = root.getFirstFirstChild();
    checkArgument(destructPat.isArrayPattern());

    Node nameNodeA = destructPat.getFirstChild();
    Node nameNodeB = destructPat.getLastChild();
    checkState(nameNodeA.getString().equals("a"), nameNodeA);
    checkState(nameNodeB.getString().equals("b"), nameNodeB);

    assertLhsByDestructuring(nameNodeA);
    assertLhsByDestructuring(nameNodeB);
  }

  public void testLhsByDestructuring5() {
    Node root = parse("function fn([a, b] = [c, d]){}");
    Node destructPat = root.getFirstChild().getSecondChild()
        .getFirstFirstChild();
    checkArgument(destructPat.isArrayPattern());

    Node nameNodeA = destructPat.getFirstChild();
    Node nameNodeB = destructPat.getLastChild();
    Node nameNodeC = destructPat.getNext().getFirstChild();
    Node nameNodeD = destructPat.getNext().getLastChild();
    checkState(nameNodeA.getString().equals("a"), nameNodeA);
    checkState(nameNodeB.getString().equals("b"), nameNodeB);
    checkState(nameNodeC.getString().equals("c"), nameNodeC);
    checkState(nameNodeD.getString().equals("d"), nameNodeD);

    assertLhsByDestructuring(nameNodeA);
    assertLhsByDestructuring(nameNodeB);
    assertNotLhsByDestructuring(nameNodeC);
    assertNotLhsByDestructuring(nameNodeD);
  }

  public void testLhsByDestructuring6() {
    Node root = parse("for ([{a: b}] of c) {}");
    Node destructPat = root.getFirstFirstChild().getFirstChild();
    checkArgument(destructPat.isObjectPattern() && destructPat.getParent().isArrayPattern());

    Node strKeyNodeA = destructPat.getFirstChild();
    Node nameNodeB = strKeyNodeA.getFirstChild();
    Node nameNodeC = destructPat.getParent().getNext();
    checkState(strKeyNodeA.getString().equals("a"), strKeyNodeA);
    checkState(nameNodeB.getString().equals("b"), nameNodeB);
    checkState(nameNodeC.getString().equals("c"), nameNodeC);

    assertNotLhsByDestructuring(strKeyNodeA);
    assertLhsByDestructuring(nameNodeB);
    assertNotLhsByDestructuring(nameNodeC);
  }

  public void testLhsByDestructuring6b() {
    Node root = parse("for ([{a: b}] in c) {}");
    Node destructPat = root.getFirstFirstChild().getFirstChild();
    checkArgument(destructPat.isObjectPattern() && destructPat.getParent().isArrayPattern());

    Node strKeyNodeA = destructPat.getFirstChild();
    Node nameNodeB = strKeyNodeA.getFirstChild();
    Node nameNodeC = destructPat.getParent().getNext();
    checkState(strKeyNodeA.getString().equals("a"), strKeyNodeA);
    checkState(nameNodeB.getString().equals("b"), nameNodeB);
    checkState(nameNodeC.getString().equals("c"), nameNodeC);

    assertNotLhsByDestructuring(strKeyNodeA);
    assertLhsByDestructuring(nameNodeB);
    assertNotLhsByDestructuring(nameNodeC);
  }

  public void testLhsByDestructuring6c() {
    Node root = parse("for (var [{a: b}] = [{a: 1}];;) {}");
    Node destructArr = root.getFirstFirstChild().getFirstFirstChild();
    checkArgument(destructArr.isArrayPattern());
    Node destructPat = destructArr.getFirstChild();
    checkArgument(destructPat.isObjectPattern() && destructPat.getParent().isArrayPattern());

    Node strKeyNodeA = destructPat.getFirstChild();
    Node nameNodeB = strKeyNodeA.getFirstChild();
    checkState(strKeyNodeA.getString().equals("a"), strKeyNodeA);
    checkState(nameNodeB.getString().equals("b"), nameNodeB);

    assertNotLhsByDestructuring(strKeyNodeA);
    assertLhsByDestructuring(nameNodeB);
  }

  public void testLhsByDestructuring7() {
    Node root = parse("for ([a] of c) {}");
    Node destructPat = root.getFirstFirstChild();
    checkArgument(destructPat.isArrayPattern());

    Node nameNodeA = destructPat.getFirstChild();
    Node nameNodeC = destructPat.getNext();
    checkState(nameNodeA.getString().equals("a"), nameNodeA);
    checkState(nameNodeC.getString().equals("c"), nameNodeC);

    assertLhsByDestructuring(nameNodeA);
    assertNotLhsByDestructuring(nameNodeC);
  }

  public void testLhsByDestructuring7b() {
    Node root = parse("for ([a] in c) {}");
    Node destructPat = root.getFirstFirstChild();
    checkArgument(destructPat.isArrayPattern());

    Node nameNodeA = destructPat.getFirstChild();
    Node nameNodeC = destructPat.getNext();
    checkState(nameNodeA.getString().equals("a"), nameNodeA);
    checkState(nameNodeC.getString().equals("c"), nameNodeC);

    assertLhsByDestructuring(nameNodeA);
    assertNotLhsByDestructuring(nameNodeC);
  }

  public void testLhsByDestructuring7c() {
    Node root = parse("for (var [a] = [1];;) {}");
    Node destructLhs = root.getFirstFirstChild().getFirstChild();
    checkArgument(destructLhs.isDestructuringLhs());
    Node destructPat = destructLhs.getFirstChild();
    checkArgument(destructPat.isArrayPattern());

    Node nameNodeA = destructPat.getFirstChild();
    checkState(nameNodeA.getString().equals("a"), nameNodeA);

    assertLhsByDestructuring(nameNodeA);
  }

  public void testLhsByDestructuring7d() {
    Node root = parse("for (let [a] = [1];;) {}");
    Node destructLhs = root.getFirstFirstChild().getFirstChild();
    checkArgument(destructLhs.isDestructuringLhs());
    Node destructPat = destructLhs.getFirstChild();
    checkArgument(destructPat.isArrayPattern());

    Node nameNodeA = destructPat.getFirstChild();
    checkState(nameNodeA.getString().equals("a"), nameNodeA);

    assertLhsByDestructuring(nameNodeA);
  }

  public void testLhsByDestructuring7e() {
    Node root = parse("for (const [a] = [1];;) {}");
    Node destructLhs = root.getFirstFirstChild().getFirstChild();
    checkArgument(destructLhs.isDestructuringLhs());
    Node destructPat = destructLhs.getFirstChild();
    checkArgument(destructPat.isArrayPattern());

    Node nameNodeA = destructPat.getFirstChild();
    checkState(nameNodeA.getString().equals("a"), nameNodeA);

    assertLhsByDestructuring(nameNodeA);
  }

  public void testLhsByDestructuring8() {
    Node root = parse("var [...x] = obj;");
    Node destructLhs = root.getFirstFirstChild();
    checkArgument(destructLhs.isDestructuringLhs());
    Node destructPat = destructLhs.getFirstChild();
    checkArgument(destructPat.isArrayPattern());
    Node restNode = destructPat.getFirstChild();
    checkArgument(restNode.isRest());

    Node nameNodeA = restNode.getFirstChild();
    checkState(nameNodeA.getString().equals("x"), nameNodeA);

    assertLhsByDestructuring(nameNodeA);
  }

  public void testLhsByDestructuring8b() {
    Node root = parse("([...this.x] = obj);");
    Node assign = root.getFirstFirstChild();
    checkArgument(assign.isAssign());
    Node destructPat = assign.getFirstChild();
    checkArgument(destructPat.isArrayPattern());
    Node restNode = destructPat.getFirstChild();
    checkArgument(restNode.isRest());
    Node getProp = restNode.getFirstChild();
    checkArgument(getProp.isGetProp());

    assertLhsByDestructuring(getProp);
  }

  public void testLhsByDestructuring9() {
    Node root = parse("var {['a']:x} = obj;");
    Node destructLhs = root.getFirstFirstChild();
    checkArgument(destructLhs.isDestructuringLhs());
    Node destructPat = destructLhs.getFirstChild();
    checkArgument(destructPat.isObjectPattern());
    Node computedPropNode = destructPat.getFirstChild();
    checkArgument(computedPropNode.isComputedProp());

    Node nameNodeA = computedPropNode.getLastChild();
    checkState(nameNodeA.getString().equals("x"), nameNodeA);

    assertLhsByDestructuring(nameNodeA);
  }

  private static void assertLhsByDestructuring(Node n) {
    assertTrue(NodeUtil.isLhsByDestructuring(n));
  }

  private static void assertNotLhsByDestructuring(Node n) {
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

    classNode = getClassNode("/** @export */ var Foo = class Bar {}");
    assertTrue(NodeUtil.getBestJSDocInfo(classNode).isExport());
  }

  public void testGetBestJsDocInfoForMethods() {
    Node function = getFunctionNode("class C { /** @export */ foo() {} }");
    assertTrue(NodeUtil.getBestJSDocInfo(function).isExport());

    function = getFunctionNode("class C { /** @export */ [computedMethod]() {} }");
    assertTrue(NodeUtil.getBestJSDocInfo(function).isExport());
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

  public void testGetDeclaredTypeExpression1() {
    Node ast = parse("function f(/** string */ x) {}");
    Node x = getNameNode(ast, "x");
    JSTypeExpression typeExpr = NodeUtil.getDeclaredTypeExpression(x);
    assertThat(typeExpr.getRoot().getString()).isEqualTo("string");
  }

  public void testGetDeclaredTypeExpression2() {
    Node ast = parse("/** @param {string} x */ function f(x) {}");
    Node x = getNameNode(ast, "x");
    JSTypeExpression typeExpr = NodeUtil.getDeclaredTypeExpression(x);
    assertThat(typeExpr.getRoot().getString()).isEqualTo("string");
  }

  public void testGetDeclaredTypeExpression3() {
    Node ast = parse("/** @param {...number} x */ function f(...x) {}");
    Node x = getNameNode(ast, "x");
    JSTypeExpression typeExpr = NodeUtil.getDeclaredTypeExpression(x);
    assertNode(typeExpr.getRoot()).hasType(Token.ELLIPSIS);
    assertThat(typeExpr.getRoot().getFirstChild().getString()).isEqualTo("number");
  }

  public void testGetDeclaredTypeExpression4() {
    Node ast = parse("/** @param {number=} x */ function f(x = -1) {}");
    Node x = getNameNode(ast, "x");
    JSTypeExpression typeExpr = NodeUtil.getDeclaredTypeExpression(x);
    assertNode(typeExpr.getRoot()).hasType(Token.EQUALS);
    assertThat(typeExpr.getRoot().getFirstChild().getString()).isEqualTo("number");
  }

  public void testFindLhsNodesInNode() {
    assertThat(findLhsNodesInNode("var x;")).hasSize(1);
    assertThat(findLhsNodesInNode("var x, y;")).hasSize(2);
    assertThat(findLhsNodesInNode("var f = function(x, y, z) {};")).hasSize(1);
    assertThat(findLhsNodesInNode("var [x=a => a, y = b=>b+1] = arr;")).hasSize(2);
    assertThat(findLhsNodesInNode("var [x=a => a, y = b=>b+1, ...z] = arr;")).hasSize(3);
    assertThat(findLhsNodesInNode("var [ , , , y = b=>b+1, ...z] = arr;")).hasSize(2);
    assertThat(findLhsNodesInNode("var {x = a=>a, y = b=>b+1} = obj;")).hasSize(2);
    assertThat(findLhsNodesInNode("var {p1: x = a=>a, p2: y = b=>b+1} = obj;")).hasSize(2);
    assertThat(findLhsNodesInNode("var {[pname]: x = a=>a, [p2name]: y} = obj;")).hasSize(2);
    assertThat(findLhsNodesInNode("var {lhs1 = a, p2: [lhs2, lhs3 = b] = [notlhs]} = obj;"))
        .hasSize(3);
    assertThat(findLhsNodesInNode("[this.x] = rhs;")).hasSize(1);
    assertThat(findLhsNodesInNode("[this.x, y] = rhs;")).hasSize(2);
    assertThat(findLhsNodesInNode("[this.x, y, this.z] = rhs;")).hasSize(3);
    assertThat(findLhsNodesInNode("[y, this.z] = rhs;")).hasSize(2);
    assertThat(findLhsNodesInNode("[x[y]] = rhs;")).hasSize(1);
    assertThat(findLhsNodesInNode("[x.y.z] = rhs;")).hasSize(1);

    assertThat(findLhsNodesInNode("x += 1;")).hasSize(1);
    assertThat(findLhsNodesInNode("x.y += 1;")).hasSize(1);
    assertThat(findLhsNodesInNode("x -= 1;")).hasSize(1);
    assertThat(findLhsNodesInNode("x.y -= 1;")).hasSize(1);
    assertThat(findLhsNodesInNode("x *= 2;")).hasSize(1);
    assertThat(findLhsNodesInNode("x.y *= 2;")).hasSize(1);
  }

  public void testIsConstructor() {
    assertTrue(NodeUtil.isConstructor(getFunctionNode("/** @constructor */ function Foo() {}")));
    assertTrue(NodeUtil.isConstructor(getFunctionNode(
        "/** @constructor */ var Foo = function() {}")));
    assertTrue(NodeUtil.isConstructor(getFunctionNode(
        "var x = {}; /** @constructor */ x.Foo = function() {}")));
    assertTrue(NodeUtil.isConstructor(getFunctionNode("class Foo { constructor() {} }")));

    assertFalse(NodeUtil.isConstructor(getFunctionNode("function Foo() {}")));
    assertFalse(NodeUtil.isConstructor(getFunctionNode("var Foo = function() {}")));
    assertFalse(NodeUtil.isConstructor(getFunctionNode("var x = {}; x.Foo = function() {};")));
    assertFalse(NodeUtil.isConstructor(getFunctionNode("function constructor() {}")));
    assertFalse(NodeUtil.isConstructor(getFunctionNode("class Foo { bar() {} }")));
  }

  public void testIsGetterOrSetter() {
    Node fnNode = getFunctionNode("Object.defineProperty(this, 'bar', {get: function() {}});");
    assertTrue(NodeUtil.isGetterOrSetter(fnNode.getParent()));

    fnNode = getFunctionNode("Object.defineProperty(this, 'bar', {set: function() {}});");
    assertTrue(NodeUtil.isGetterOrSetter(fnNode.getParent()));

    fnNode = getFunctionNode("Object.defineProperties(this, {bar: {get: function() {}}});");
    assertTrue(NodeUtil.isGetterOrSetter(fnNode.getParent()));

    fnNode = getFunctionNode("Object.defineProperties(this, {bar: {set: function() {}}});");
    assertTrue(NodeUtil.isGetterOrSetter(fnNode.getParent()));

    fnNode = getFunctionNode("var x = {get bar() {}};");
    assertTrue(NodeUtil.isGetterOrSetter(fnNode.getParent()));

    fnNode = getFunctionNode("var x = {set bar(z) {}};");
    assertTrue(NodeUtil.isGetterOrSetter(fnNode.getParent()));
  }

  public void testIsObjectDefinePropertiesDefinition() {
    assertTrue(NodeUtil.isObjectDefinePropertiesDefinition(
        getCallNode("Object.defineProperties(this, {});")));
    assertTrue(NodeUtil.isObjectDefinePropertiesDefinition(
        getCallNode("Object.defineProperties(this, foo);")));

    assertFalse(NodeUtil.isObjectDefinePropertiesDefinition(
        getCallNode("Object.defineProperties(this, {}, foo);")));
    assertFalse(NodeUtil.isObjectDefinePropertiesDefinition(
        getCallNode("Object.defineProperties(this);")));
    assertFalse(NodeUtil.isObjectDefinePropertiesDefinition(
        getCallNode("Object.defineProperties();")));
  }

  public void testIsObjectDefinePropertyDefinition() {
    assertTrue(NodeUtil.isObjectDefinePropertyDefinition(
        getCallNode("Object.defineProperty(this, 'foo', {});")));
    assertTrue(NodeUtil.isObjectDefinePropertyDefinition(
        getCallNode("Object.defineProperty(this, 'foo', foo);")));

    assertFalse(NodeUtil.isObjectDefinePropertyDefinition(
        getCallNode("Object.defineProperty(this, {});")));
    assertFalse(NodeUtil.isObjectDefinePropertyDefinition(
        getCallNode("Object.defineProperty(this);")));
    assertFalse(NodeUtil.isObjectDefinePropertyDefinition(
        getCallNode("Object.defineProperty();")));
  }

  public void testGetAllVars1() {
    String fnString = "var h; function g(x, y) {var z; h = 2; {let a; const b = 1} let c}";
    Compiler compiler = new Compiler();
    compiler.setLifeCycleStage(LifeCycleStage.NORMALIZED);
    Es6SyntacticScopeCreator scopeCreator = new Es6SyntacticScopeCreator(compiler);

    Node ast = parse(fnString);
    Node functionNode = getFunctionNode(fnString);

    Scope globalScope = Scope.createGlobalScope(ast);
    Scope functionScope = scopeCreator.createScope(functionNode, globalScope);

    Map<String, Var> allVariables = new HashMap<>();
    List<Var> orderedVars = new ArrayList<>();
    NodeUtil.getAllVarsDeclaredInFunction(
        allVariables, orderedVars, compiler, scopeCreator, functionScope);
    Set<String> keySet = new HashSet<>(Arrays.asList("a", "b", "c", "z", "x", "y"));
    assertEquals(keySet, allVariables.keySet());
  }

  public void testGetAllVars2() {
    String fnString =
        "function g(x, y) "
            + "{var z; "
            + "{let a = (no1, no2) => { let no6, no7; }; "
            + "const b = 1} "
            + "let c} "
            + "function u(h) {let e}";

    Compiler compiler = new Compiler();
    compiler.setLifeCycleStage(LifeCycleStage.NORMALIZED);
    Es6SyntacticScopeCreator scopeCreator = new Es6SyntacticScopeCreator(compiler);

    Node ast = parse(fnString);
    Node functionNode = getFunctionNode(fnString);

    Scope globalScope = Scope.createGlobalScope(ast);
    Scope functionScope = scopeCreator.createScope(functionNode, globalScope);

    Map<String, Var> allVariables = new HashMap<>();
    List<Var> orderedVars = new ArrayList<>();
    NodeUtil.getAllVarsDeclaredInFunction(
        allVariables, orderedVars, compiler, scopeCreator, functionScope);
    Set<String> keySet = new HashSet<>(Arrays.asList("x", "y", "z", "a", "b", "c"));
    assertEquals(keySet, allVariables.keySet());
  }

  public void testIsVarArgs() {
    assertTrue(NodeUtil.doesFunctionReferenceOwnArgumentsObject(
        getNode("function() {return () => arguments}")));
    assertFalse(NodeUtil.doesFunctionReferenceOwnArgumentsObject(
        getNode("() => arguments")));
  }

  /**
   * When the left side is a destructuring pattern, generally it's not possible to identify the
   * RHS for a specific name on the LHS.
   */
  public void testIsExpressionResultUsed() {
    assertThat(NodeUtil.isExpressionResultUsed(getNameNodeFrom("for (x in y) z", "x"))).isTrue();
    assertThat(NodeUtil.isExpressionResultUsed(getNameNodeFrom("for (x in y) z", "y"))).isTrue();
    assertThat(NodeUtil.isExpressionResultUsed(getNameNodeFrom("for (x in y) z", "z"))).isFalse();

    assertThat(NodeUtil.isExpressionResultUsed(getNameNodeFrom("for (x of y) z", "x"))).isTrue();
    assertThat(NodeUtil.isExpressionResultUsed(getNameNodeFrom("for (x of y) z", "y"))).isTrue();
    assertThat(NodeUtil.isExpressionResultUsed(getNameNodeFrom("for (x of y) z", "z"))).isFalse();

    assertThat(NodeUtil.isExpressionResultUsed(getNameNodeFrom("for (x; y; z) a", "x"))).isFalse();
    assertThat(NodeUtil.isExpressionResultUsed(getNameNodeFrom("for (x; y; z) a", "y"))).isTrue();
    assertThat(NodeUtil.isExpressionResultUsed(getNameNodeFrom("for (x; y; z) a", "z"))).isFalse();

    assertTrue(NodeUtil.isExpressionResultUsed(getNameNodeFrom("function f() { return y }", "y")));
    assertFalse(NodeUtil.isExpressionResultUsed(getNameNodeFrom("function f() { y }", "y")));

    assertTrue(NodeUtil.isExpressionResultUsed(getNameNodeFrom("var x = ()=> y", "y")));
    assertFalse(NodeUtil.isExpressionResultUsed(getNameNodeFrom("var x = ()=>{ y }", "y")));

    assertTrue(NodeUtil.isExpressionResultUsed(getNameNodeFrom("({a: x = y} = z)", "y")));
    assertTrue(NodeUtil.isExpressionResultUsed(getNameNodeFrom("[x = y] = z", "y")));

    assertTrue(NodeUtil.isExpressionResultUsed(getNameNodeFrom("({x: y})", "y")));
    assertTrue(NodeUtil.isExpressionResultUsed(getNameNodeFrom("[y]", "y")));

    assertTrue(NodeUtil.isExpressionResultUsed(getNameNodeFrom("y()", "y")));
    assertTrue(NodeUtil.isExpressionResultUsed(getNameNodeFrom("y``", "y")));
  }

  public void testIsSimpleOperator() {
    assertTrue(NodeUtil.isSimpleOperator(parseExpr("!x")));
    assertTrue(NodeUtil.isSimpleOperator(parseExpr("5 + x")));
    assertTrue(NodeUtil.isSimpleOperator(parseExpr("typeof x")));
    assertTrue(NodeUtil.isSimpleOperator(parseExpr("x instanceof y")));
    // short curcuits aren't simple
    assertFalse(NodeUtil.isSimpleOperator(parseExpr("5 && x")));
    assertFalse(NodeUtil.isSimpleOperator(parseExpr("5 || x")));
    // side-effects aren't simple
    assertFalse(NodeUtil.isSimpleOperator(parseExpr("x = 5")));
    assertFalse(NodeUtil.isSimpleOperator(parseExpr("x++")));
    assertFalse(NodeUtil.isSimpleOperator(parseExpr("--y")));
    // prop access are simple
    assertTrue(NodeUtil.isSimpleOperator(parseExpr("y in x")));
    assertTrue(NodeUtil.isSimpleOperator(parseExpr("x.y")));
    assertTrue(NodeUtil.isSimpleOperator(parseExpr("x[y]")));
  }

  private Node getNameNodeFrom(String code, String name) {
    Node ast = parse(code);
    Node nameNode = getNameNode(ast, name);
    return nameNode;
  }

  private boolean executedOnceTestCase(String code) {
    Node nameNode = getNameNodeFrom(code, "x");
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

  private static void testFunctionName(String js, String expected) {
    assertEquals(
        expected,
        NodeUtil.getNearestFunctionName(getFunctionNode(js)));
  }

  private static Node getClassNode(String js) {
    Node root = parse(js);
    return getClassNode(root);
  }

  private static Node getClassNode(Node n) {
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

  /**
   * @param js JavaScript node to be passed to {@code NodeUtil.findLhsNodesInNode}. Must be either
   *     an EXPR_RESULT containing an assignment operation (e.g. =, +=, /=, etc)
   *     in which case the assignment node will be passed to
   *     {@code NodeUtil.findLhsNodesInNode}, or a VAR, LET, or CONST statement, in which case the
   *     declaration statement will be passed.
   */
  private static Iterable<Node> findLhsNodesInNode(String js) {
    Node root = parse(js);
    checkState(root.isScript(), root);
    root = root.getOnlyChild();
    if (root.isExprResult()) {
      root = root.getOnlyChild();
      checkState(NodeUtil.isAssignmentOp(root), root);
    }
    return NodeUtil.findLhsNodesInNode(root);
  }

  private static Node getCallNode(String js) {
    Node root = parse(js);
    return getCallNode(root);
  }

  private static Node getCallNode(Node n) {
    if (n.isCall()) {
      return n;
    }
    for (Node c : n.children()) {
      Node result = getCallNode(c);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  private static Node getFunctionNode(String js) {
    Node root = parse(js);
    return getFunctionNode(root);
  }

  private static Node getFunctionNode(Node n) {
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

  private static Node getNameNode(Node n, String name) {
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

  /** @return The first node in {@code tree} that is an array pattern or object pattern. */
  @Nullable
  private static Node getPattern(Node tree) {
    if (tree.isDestructuringPattern()) {
      return tree;
    }
    for (Node c : tree.children()) {
      Node result = getPattern(c);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  /** @return The first node in {@code tree} that is a DESTRUCTURING_LHS. */
  @Nullable
  private static Node getDestructuringLhs(Node tree) {
    if (tree.isDestructuringLhs()) {
      return tree;
    }
    for (Node c : tree.children()) {
      Node result = getDestructuringLhs(c);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  private static boolean isValidPropertyName(String s) {
    return NodeUtil.isValidPropertyName(FeatureSet.ES3, s);
  }

  private static boolean isValidQualifiedName(String s) {
    return NodeUtil.isValidQualifiedName(FeatureSet.ES3, s);
  }
}
