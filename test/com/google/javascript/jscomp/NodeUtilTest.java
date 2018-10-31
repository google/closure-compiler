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
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.DiagnosticGroups.ES5_STRICT;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.TernaryValue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for NodeUtil */
@RunWith(JUnit4.class)
public final class NodeUtilTest {

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

  @Test
  public void testGetNodeByLineCol_1() {
    Node root = parse("var x = 1;");
    assertNode(NodeUtil.getNodeByLineCol(root, 1, 0)).isNull();
    assertNode(NodeUtil.getNodeByLineCol(root, 1, 1)).hasType(Token.VAR);
    assertNode(NodeUtil.getNodeByLineCol(root, 1, 2)).hasType(Token.VAR);
    assertNode(NodeUtil.getNodeByLineCol(root, 1, 5)).hasType(Token.NAME);
    assertNode(NodeUtil.getNodeByLineCol(root, 1, 9)).hasType(Token.NUMBER);
    assertNode(NodeUtil.getNodeByLineCol(root, 1, 11)).hasType(Token.VAR);
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testObjectLiteralIsLiteralValue() {
    assertThat(isLiteralValue("{a: 20}")).isTrue();
    assertThat(isLiteralValue("{'a': 20}")).isTrue();
    assertThat(isLiteralValue("{a: function() {}}")).isTrue();
    assertThat(isLiteralValueExcludingFunctions("{a: function() {}}")).isFalse();
    assertThat(isLiteralValue("{a() {}}")).isTrue();
    assertThat(isLiteralValueExcludingFunctions("{a() {}}")).isFalse();
    assertThat(isLiteralValue("{'a'() {}}")).isTrue();
    assertThat(isLiteralValueExcludingFunctions("{'a'() {}}")).isFalse();

    assertThat(isLiteralValue("{['a']: 20}")).isTrue();
    assertThat(isLiteralValue("{[b]: 20}")).isFalse();
    assertThat(isLiteralValue("{['a']() {}}")).isTrue();
    assertThat(isLiteralValue("{[b]() {}}")).isFalse();
    assertThat(isLiteralValueExcludingFunctions("{['a']() {}}")).isFalse();

    assertThat(isLiteralValue("{ get a() { return 0; } }")).isTrue();
    assertThat(isLiteralValue("{ get 'a'() { return 0; } }")).isTrue();
    assertThat(isLiteralValue("{ get ['a']() { return 0; } }")).isTrue();
    assertThat(isLiteralValue("{ get 123() { return 0; } }")).isTrue();
    assertThat(isLiteralValue("{ get [123]() { return 0; } }")).isTrue();
    assertThat(isLiteralValue("{ get [b]() { return 0; } }")).isFalse();
    assertThat(isLiteralValue("{ set a(x) { } }")).isTrue();
    assertThat(isLiteralValue("{ set 'a'(x) { } }")).isTrue();
    assertThat(isLiteralValue("{ set ['a'](x) { } }")).isTrue();
    assertThat(isLiteralValue("{ set 123(x) { } }")).isTrue();
    assertThat(isLiteralValue("{ set [123](x) { } }")).isTrue();
    assertThat(isLiteralValue("{ set [b](x) { } }")).isFalse();
  }

  private boolean isLiteralValueExcludingFunctions(String code) {
    return NodeUtil.isLiteralValue(getNode(code), /* includeFunctions */ false);
  }

  private boolean isLiteralValue(String code) {
    return NodeUtil.isLiteralValue(getNode(code), /* includeFunctions */ true);
  }

  private void assertLiteralAndImmutable(Node n) {
    assertThat(NodeUtil.isLiteralValue(n, true)).isTrue();
    assertThat(NodeUtil.isLiteralValue(n, false)).isTrue();
    assertThat(NodeUtil.isImmutableValue(n)).isTrue();
  }

  private void assertLiteralButNotImmutable(Node n) {
    assertThat(NodeUtil.isLiteralValue(n, true)).isTrue();
    assertThat(NodeUtil.isLiteralValue(n, false)).isTrue();
    assertThat(NodeUtil.isImmutableValue(n)).isFalse();
  }

  private void assertNotLiteral(Node n) {
    assertThat(NodeUtil.isLiteralValue(n, true)).isFalse();
    assertThat(NodeUtil.isLiteralValue(n, false)).isFalse();
    assertThat(NodeUtil.isImmutableValue(n)).isFalse();
  }

  @Test
  public void testIsStringLiteralStringLiteral() {
    assertThat(NodeUtil.isSomeCompileTimeConstStringValue(parseExpr("'b'"))).isTrue(); // String
  }

  @Test
  public void testIsStringLiteralTemplateNoSubst() {
    assertThat(NodeUtil.isSomeCompileTimeConstStringValue(parseExpr("`b`"))).isTrue(); // Template
  }

  @Test
  public void testIsStringLiteralTemplateWithSubstitution() {
    // Template with substitution
    assertThat(NodeUtil.isSomeCompileTimeConstStringValue(parseExpr("`foo${bar}`"))).isFalse();
  }

  @Test
  public void testIsStringLiteralVariable() {
    assertThat(NodeUtil.isSomeCompileTimeConstStringValue(parseExpr("b"))).isFalse(); // Variable
  }

  @Test
  public void testIsStringLiteralConcatLiterals() {
    assertThat(NodeUtil.isSomeCompileTimeConstStringValue(parseExpr("'b' + 'c'")))
        .isTrue(); // String concatenation
  }

  @Test
  public void testIsStringLiteralConcatLiteralVariable() {
    assertThat(NodeUtil.isSomeCompileTimeConstStringValue(parseExpr("b + 'c'"))).isFalse();
    assertThat(NodeUtil.isSomeCompileTimeConstStringValue(parseExpr("'b' + c"))).isFalse();
  }

  @Test
  public void testIsStringLiteralTernary() {
    assertThat(NodeUtil.isSomeCompileTimeConstStringValue(parseExpr("a ? 'b' : 'c'"))).isTrue();
    assertThat(NodeUtil.isSomeCompileTimeConstStringValue(parseExpr("a ? b : 'c'"))).isFalse();
    assertThat(NodeUtil.isSomeCompileTimeConstStringValue(parseExpr("a ? 'b' : c"))).isFalse();
    assertThat(NodeUtil.isSomeCompileTimeConstStringValue(parseExpr("a ? b : c"))).isFalse();
  }

  @Test
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
    assertThat(NodeUtil.getPureBooleanValue(getNode(val))).isEqualTo(TernaryValue.TRUE);
  }

  private void assertPureBooleanFalse(String val) {
    assertThat(NodeUtil.getPureBooleanValue(getNode(val))).isEqualTo(TernaryValue.FALSE);
  }

  private void assertPureBooleanUnknown(String val) {
    assertThat(NodeUtil.getPureBooleanValue(getNode(val))).isEqualTo(TernaryValue.UNKNOWN);
  }

  @Test
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
    assertThat(NodeUtil.getImpureBooleanValue(getNode(val))).isEqualTo(TernaryValue.TRUE);
  }

  private void assertImpureBooleanFalse(String val) {
    assertThat(NodeUtil.getImpureBooleanValue(getNode(val))).isEqualTo(TernaryValue.FALSE);
  }

  private void assertImpureBooleanUnknown(String val) {
    assertThat(NodeUtil.getImpureBooleanValue(getNode(val))).isEqualTo(TernaryValue.UNKNOWN);
  }

  @Test
  public void testGetStringValue() {
    assertThat(NodeUtil.getStringValue(getNode("true"))).isEqualTo("true");
    assertThat(NodeUtil.getStringValue(getNode("10"))).isEqualTo("10");
    assertThat(NodeUtil.getStringValue(getNode("1.0"))).isEqualTo("1");

    /* See https://github.com/google/closure-compiler/issues/1262 */
    assertThat(NodeUtil.getStringValue(getNode("1.2323919403474454e+21")))
        .isEqualTo("1.2323919403474454e+21");

    assertThat(NodeUtil.getStringValue(getNode("'0'"))).isEqualTo("0");
    assertThat(NodeUtil.getStringValue(getNode("/a/"))).isNull();
    assertThat(NodeUtil.getStringValue(getNode("{}"))).isEqualTo("[object Object]");
    assertThat(NodeUtil.getStringValue(getNode("[]"))).isEmpty();
    assertThat(NodeUtil.getStringValue(getNode("false"))).isEqualTo("false");
    assertThat(NodeUtil.getStringValue(getNode("null"))).isEqualTo("null");
    assertThat(NodeUtil.getStringValue(getNode("0"))).isEqualTo("0");
    assertThat(NodeUtil.getStringValue(getNode("''"))).isEmpty();
    assertThat(NodeUtil.getStringValue(getNode("undefined"))).isEqualTo("undefined");
    assertThat(NodeUtil.getStringValue(getNode("void 0"))).isEqualTo("undefined");
    assertThat(NodeUtil.getStringValue(getNode("void foo()"))).isEqualTo("undefined");

    assertThat(NodeUtil.getStringValue(getNode("NaN"))).isEqualTo("NaN");
    assertThat(NodeUtil.getStringValue(getNode("Infinity"))).isEqualTo("Infinity");
    assertThat(NodeUtil.getStringValue(getNode("x"))).isNull();

    assertThat(NodeUtil.getStringValue(getNode("`Hello`"))).isEqualTo("Hello");
    assertThat(NodeUtil.getStringValue(getNode("`Hello ${'foo'}`"))).isEqualTo("Hello foo");
    assertThat(NodeUtil.getStringValue(getNode("`Hello ${name}`"))).isNull();
    assertThat(NodeUtil.getStringValue(getNode("`${4} bananas`"))).isEqualTo("4 bananas");
    assertThat(NodeUtil.getStringValue(getNode("`This is ${true}.`"))).isEqualTo("This is true.");
    assertThat(NodeUtil.getStringValue(getNode("`${'hello'} ${name}`"))).isNull();
  }

  @Test
  public void testGetArrayStringValue() {
    assertThat(NodeUtil.getStringValue(getNode("[]"))).isEmpty();
    assertThat(NodeUtil.getStringValue(getNode("['']"))).isEmpty();
    assertThat(NodeUtil.getStringValue(getNode("[null]"))).isEmpty();
    assertThat(NodeUtil.getStringValue(getNode("[undefined]"))).isEmpty();
    assertThat(NodeUtil.getStringValue(getNode("[void 0]"))).isEmpty();
    assertThat(NodeUtil.getStringValue(getNode("[NaN]"))).isEqualTo("NaN");
    assertThat(NodeUtil.getStringValue(getNode("[,'']"))).isEqualTo(",");
    assertThat(NodeUtil.getStringValue(getNode("[[''],[''],['']]"))).isEqualTo(",,");
    assertThat(NodeUtil.getStringValue(getNode("[[1.0],[2.0]]"))).isEqualTo("1,2");
    assertThat(NodeUtil.getStringValue(getNode("[a]"))).isNull();
    assertThat(NodeUtil.getStringValue(getNode("[1,a]"))).isNull();
  }

  @Test
  public void testIsObjectLiteralKey1() {
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
    assertThat(NodeUtil.isObjectLitKey(node)).isEqualTo(expected);
  }

  @Test
  public void testGetFunctionName1() {
    Node parent = parse("function name(){}");
    assertGetNameResult(parent.getFirstChild(), "name");
  }

  @Test
  public void testGetFunctionName2() {
    Node parent = parse("var name = function(){}")
        .getFirstFirstChild();

    assertGetNameResult(parent.getFirstChild(), "name");
  }

  @Test
  public void testGetFunctionName3() {
    Node parent = parse("qualified.name = function(){}")
        .getFirstFirstChild();

    assertGetNameResult(parent.getLastChild(), "qualified.name");
  }

  @Test
  public void testGetFunctionName4() {
    Node parent = parse("var name2 = function name1(){}")
        .getFirstFirstChild();

    assertGetNameResult(parent.getFirstChild(), "name2");
  }

  @Test
  public void testGetFunctionName5() {
    Node n = parse("qualified.name2 = function name1(){}");
    Node parent = n.getFirstFirstChild();

    assertGetNameResult(parent.getLastChild(), "qualified.name2");
  }

  @Test
  public void testGetBestFunctionName1() {
    Node parent = parse("function func(){}");

    assertThat(NodeUtil.getNearestFunctionName(parent.getFirstChild())).isEqualTo("func");
  }

  @Test
  public void testGetBestFunctionName2() {
    Node parent = parse("var obj = {memFunc(){}}")
        .getFirstFirstChild().getFirstFirstChild();

    assertThat(NodeUtil.getNearestFunctionName(parent.getLastChild())).isEqualTo("memFunc");
  }

  @Test
  public void testConstKeywordNamespace() {
    Node decl = parse("const ns = {};").getFirstChild();
    checkState(decl.isConst(), decl);

    Node nameNode = decl.getFirstChild();
    checkState(nameNode.isName(), nameNode);

    assertThat(NodeUtil.isNamespaceDecl(nameNode)).isTrue();
  }

  private void assertGetNameResult(Node function, String name) {
    assertNode(function).hasToken(Token.FUNCTION);
    assertThat(NodeUtil.getName(function)).isEqualTo(name);
  }

  @Test
  public void testContainsFunctionDeclaration() {
    assertThat(NodeUtil.containsFunction(getNode("function foo(){}"))).isTrue();
    assertThat(NodeUtil.containsFunction(getNode("(b?function(){}:null)"))).isTrue();

    assertThat(NodeUtil.containsFunction(getNode("(b?foo():null)"))).isFalse();
    assertThat(NodeUtil.containsFunction(getNode("foo()"))).isFalse();
  }

  @Test
  public void testIsFunctionDeclaration() {
    assertThat(NodeUtil.isFunctionDeclaration(getFunctionNode("function foo(){}"))).isTrue();
    assertThat(NodeUtil.isFunctionDeclaration(getFunctionNode("class C { constructor() {} }")))
        .isFalse();
    assertThat(NodeUtil.isFunctionDeclaration(getFunctionNode("({ foo() {} })"))).isFalse();
    assertThat(NodeUtil.isFunctionDeclaration(getFunctionNode("var x = function(){}"))).isFalse();
    assertThat(NodeUtil.isFunctionDeclaration(getFunctionNode("export function f() {}"))).isTrue();
    assertThat(NodeUtil.isFunctionDeclaration(getFunctionNode("export default function() {}")))
        .isFalse();
    assertThat(NodeUtil.isFunctionDeclaration(getFunctionNode("export default function foo() {}")))
        .isTrue();
    assertThat(
            NodeUtil.isFunctionDeclaration(
                getFunctionNode("export default (foo) => { alert(foo); }")))
        .isFalse();
  }

  @Test
  public void testIsMethodDeclaration() {
    assertThat(NodeUtil.isMethodDeclaration(getFunctionNode("class C { constructor() {} }")))
        .isTrue();
    assertThat(NodeUtil.isMethodDeclaration(getFunctionNode("class C { a() {} }"))).isTrue();
    assertThat(NodeUtil.isMethodDeclaration(getFunctionNode("class C { static a() {} }"))).isTrue();
    assertThat(NodeUtil.isMethodDeclaration(getFunctionNode("({ set foo(v) {} })"))).isTrue();
    assertThat(NodeUtil.isMethodDeclaration(getFunctionNode("({ get foo() {} })"))).isTrue();
    assertThat(NodeUtil.isMethodDeclaration(getFunctionNode("({ [foo]() {} })"))).isTrue();
  }

  @Test
  public void testIsClassDeclaration() {
    assertThat(NodeUtil.isClassDeclaration(getClassNode("class Foo {}"))).isTrue();
    assertThat(NodeUtil.isClassDeclaration(getClassNode("var Foo = class {}"))).isFalse();
    assertThat(NodeUtil.isClassDeclaration(getClassNode("var Foo = class Foo{}"))).isFalse();
    assertThat(NodeUtil.isClassDeclaration(getClassNode("export default class Foo {}"))).isTrue();
    assertThat(NodeUtil.isClassDeclaration(getClassNode("export class Foo {}"))).isTrue();
    assertThat(NodeUtil.isClassDeclaration(getClassNode("export default class {}"))).isFalse();
  }

  private void assertSideEffect(boolean se, String js) {
    Node n = parse(js);
    assertThat(NodeUtil.mayHaveSideEffects(n.getFirstChild())).isEqualTo(se);
  }

  private void assertSideEffect(boolean se, Node n) {
    assertThat(NodeUtil.mayHaveSideEffects(n)).isEqualTo(se);
  }

  private void assertSideEffect(boolean se, String js, boolean globalRegExp) {
    Node n = parse(js);
    Compiler compiler = new Compiler();
    compiler.initCompilerOptionsIfTesting();
    compiler.setHasRegExpGlobalReferences(globalRegExp);
    assertThat(NodeUtil.mayHaveSideEffects(n.getFirstChild(), compiler)).isEqualTo(se);
  }

  @Test
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

  @Test
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

  @Test
  public void testObjectMethodSideEffects() {
    // "toString" and "valueOf" are assumed to be side-effect free
    assertSideEffect(false, "o.toString()");
    assertSideEffect(false, "o.valueOf()");

    // other methods depend on the extern definitions
    assertSideEffect(true, "o.watch()");
  }

  @Test
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

  @Test
  public void testRegExpSideEffect2() {
    assertSideEffect(true, "'a'.replace(/a/, function (s) {alert(s)})", false);
    assertSideEffect(false, "'a'.replace(/a/, 'x')", false);
  }

  private void assertMutableState(boolean se, String js) {
    Node n = parse(js);
    assertThat(NodeUtil.mayEffectMutableState(n.getFirstChild())).isEqualTo(se);
  }

  @Test
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

  @Test
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
    assertWithMessage("Expected function node in parse tree of: %s", js)
        .that(funcParent)
        .isNotNull();

    Node funcNode = getFuncOrClassChild(funcParent, Token.FUNCTION);
    assertThat(NodeUtil.isFunctionExpression(funcNode)).isEqualTo(expected);
  }

  @Test
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
    assertWithMessage("Expected class node in parse tree of: %s", js).that(classParent).isNotNull();
    Node classNode = getFuncOrClassChild(classParent, Token.CLASS);
    assertThat(NodeUtil.isClassExpression(classNode)).isEqualTo(expected);
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

  @Test
  public void testContainsType() {
    assertThat(NodeUtil.containsType(parse("this"), Token.THIS)).isTrue();
    assertThat(NodeUtil.containsType(parse("function foo(){}(this)"), Token.THIS)).isTrue();
    assertThat(NodeUtil.containsType(parse("b?this:null"), Token.THIS)).isTrue();

    assertThat(NodeUtil.containsType(parse("a"), Token.THIS)).isFalse();
    assertThat(NodeUtil.containsType(parse("function foo(){}"), Token.THIS)).isFalse();
    assertThat(NodeUtil.containsType(parse("(b?foo():null)"), Token.THIS)).isFalse();
  }

  @Test
  public void testReferencesThis() {
    assertThat(NodeUtil.referencesThis(parse("this"))).isTrue();
    // Don't descend into functions (starts at the script node)
    assertThat(NodeUtil.referencesThis(parse("function foo(){this}"))).isFalse();
    // But starting with a function properly check for 'this'
    Node n = parse("function foo(){this}").getFirstChild();
    assertNode(n).hasToken(Token.FUNCTION);
    assertThat(NodeUtil.referencesThis(n)).isTrue();
    assertThat(NodeUtil.referencesThis(parse("b?this:null"))).isTrue();

    assertThat(NodeUtil.referencesThis(parse("a"))).isFalse();
    n = parse("function foo(){}").getFirstChild();
    assertNode(n).hasToken(Token.FUNCTION);
    assertThat(NodeUtil.referencesThis(n)).isFalse();
    assertThat(NodeUtil.referencesThis(parse("(b?foo():null)"))).isFalse();

    assertThat(NodeUtil.referencesThis(parse("()=>this"))).isTrue();
    assertThat(NodeUtil.referencesThis(parse("() => { () => alert(this); }"))).isTrue();
  }

  @Test
  public void testGetNodeTypeReferenceCount() {
    assertThat(
            NodeUtil.getNodeTypeReferenceCount(
                parse("function foo(){}"), Token.THIS, Predicates.<Node>alwaysTrue()))
        .isEqualTo(0);
    assertThat(
            NodeUtil.getNodeTypeReferenceCount(
                parse("this"), Token.THIS, Predicates.<Node>alwaysTrue()))
        .isEqualTo(1);
    assertThat(
            NodeUtil.getNodeTypeReferenceCount(
                parse("this;function foo(){}(this)"), Token.THIS, Predicates.<Node>alwaysTrue()))
        .isEqualTo(2);
  }

  @Test
  public void testIsNameReferenceCount() {
    assertThat(NodeUtil.isNameReferenced(parse("function foo(){}"), "foo")).isTrue();
    assertThat(NodeUtil.isNameReferenced(parse("var foo = function(){}"), "foo")).isTrue();
    assertThat(NodeUtil.isNameReferenced(parse("function foo(){}"), "undefined")).isFalse();
    assertThat(NodeUtil.isNameReferenced(parse("undefined"), "undefined")).isTrue();
    assertThat(
            NodeUtil.isNameReferenced(parse("undefined;function foo(){}(undefined)"), "undefined"))
        .isTrue();

    assertThat(NodeUtil.isNameReferenced(parse("goo.foo"), "goo")).isTrue();
    assertThat(NodeUtil.isNameReferenced(parse("goo.foo"), "foo")).isFalse();
  }

  @Test
  public void testGetNameReferenceCount() {
    assertThat(NodeUtil.getNameReferenceCount(parse("function foo(){}"), "undefined")).isEqualTo(0);
    assertThat(NodeUtil.getNameReferenceCount(parse("undefined"), "undefined")).isEqualTo(1);
    assertThat(
            NodeUtil.getNameReferenceCount(
                parse("undefined;function foo(){}(undefined)"), "undefined"))
        .isEqualTo(2);

    assertThat(NodeUtil.getNameReferenceCount(parse("goo.foo"), "goo")).isEqualTo(1);
    assertThat(NodeUtil.getNameReferenceCount(parse("goo.foo"), "foo")).isEqualTo(0);
    assertThat(NodeUtil.getNameReferenceCount(parse("function foo(){}"), "foo")).isEqualTo(1);
    assertThat(NodeUtil.getNameReferenceCount(parse("var foo = function(){}"), "foo")).isEqualTo(1);
  }

  @Test
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
    assertThat(actualNames).isEqualTo(nodeNames);
  }

  @Test
  public void testIsNonlocalModuleExportNameOnExports1() {
    Node root = parse("export {localName as exportName};");
    Node moduleBody = root.getFirstChild();
    Node exportNode = moduleBody.getFirstChild();
    Node exportSpecs = exportNode.getFirstChild();
    Node exportSpec = exportSpecs.getFirstChild();

    Node localName = exportSpec.getFirstChild();
    Node exportName = exportSpec.getSecondChild();

    assertThat(NodeUtil.isNonlocalModuleExportName(localName)).isFalse();
    assertThat(NodeUtil.isNonlocalModuleExportName(exportName)).isTrue();
  }

  @Test
  public void testIsNonlocalModuleExportNameOnExports2() {
    Node root = parse("let bar; export {bar};");
    Node moduleBody = root.getFirstChild();
    Node exportNode = moduleBody.getSecondChild();
    Node exportSpecs = exportNode.getFirstChild();
    Node exportSpec = exportSpecs.getFirstChild();

    Node name = exportSpec.getFirstChild();

    // bar is defined locally, so isNonlocalModuleExportName is false.
    assertThat(NodeUtil.isNonlocalModuleExportName(name)).isFalse();
  }

  @Test
  public void testIsNonlocalModuleExportNameOnImports1() {
    Node root = parse("import {exportName as localName} from './foo.js';");
    Node moduleBody = root.getFirstChild();
    Node importNode = moduleBody.getFirstChild();
    Node importSpecs = importNode.getSecondChild();
    Node importSpec = importSpecs.getFirstChild();

    Node exportName = importSpec.getFirstChild();
    Node localName = importSpec.getSecondChild();

    assertThat(NodeUtil.isNonlocalModuleExportName(exportName)).isTrue();
    assertThat(NodeUtil.isNonlocalModuleExportName(localName)).isFalse();
  }

  @Test
  public void testIsNonlocalModuleExportNameOnImports2() {
    Node root = parse("import {bar} from './foo.js';");
    Node moduleBody = root.getFirstChild();
    Node importNode = moduleBody.getFirstChild();
    Node importSpecs = importNode.getSecondChild();
    Node importSpec = importSpecs.getFirstChild();

    Node name = importSpec.getSecondChild();

    // bar is defined locally so isNonlocalModuleExportName is false
    assertThat(NodeUtil.isNonlocalModuleExportName(name)).isFalse();
  }

  @Test
  public void testIsControlStructureCodeBlock() {
    Node root = parse("if (x) foo(); else boo();");
    Node ifNode = root.getFirstChild();

    Node ifCondition = ifNode.getFirstChild();
    Node ifCase = ifNode.getSecondChild();
    Node elseCase = ifNode.getLastChild();

    assertThat(NodeUtil.isControlStructureCodeBlock(ifNode, ifCondition)).isFalse();
    assertThat(NodeUtil.isControlStructureCodeBlock(ifNode, ifCase)).isTrue();
    assertThat(NodeUtil.isControlStructureCodeBlock(ifNode, elseCase)).isTrue();
  }

  @Test
  public void testIsFunctionExpression1() {
    Node root = parse("(function foo() {})");
    Node statementNode = root.getFirstChild();
    assertThat(statementNode.isExprResult()).isTrue();
    Node functionNode = statementNode.getFirstChild();
    assertThat(functionNode.isFunction()).isTrue();
    assertThat(NodeUtil.isFunctionExpression(functionNode)).isTrue();
  }

  @Test
  public void testIsFunctionExpression2() {
    Node root = parse("function foo() {}");
    Node functionNode = root.getFirstChild();
    assertThat(functionNode.isFunction()).isTrue();
    assertThat(NodeUtil.isFunctionExpression(functionNode)).isFalse();
  }

  @Test
  public void testRemoveChildBlock() {
    // Test removing the inner block.
    Node actual = parse("{{x()}}");

    Node outerBlockNode = actual.getFirstChild();
    Node innerBlockNode = outerBlockNode.getFirstChild();

    NodeUtil.removeChild(outerBlockNode, innerBlockNode);
    String expected = "{{}}";
    assertNode(parse(expected)).isEqualTo(actual);
  }

  @Test
  public void testRemoveTryChild1() {
    // Test removing the finally clause.
    Node actual = parse("try {foo()} catch(e) {} finally {}");

    Node tryNode = actual.getFirstChild();
    Node finallyBlock = tryNode.getLastChild();

    NodeUtil.removeChild(tryNode, finallyBlock);
    String expected = "try {foo()} catch(e) {}";
    assertNode(parse(expected)).isEqualTo(actual);
  }

  @Test
  public void testRemoveTryChild2() {
    // Test removing the try clause.
    Node actual = parse("try {foo()} catch(e) {} finally {}");

    Node tryNode = actual.getFirstChild();
    Node tryBlock = tryNode.getFirstChild();

    NodeUtil.removeChild(tryNode, tryBlock);
    String expected = "try {} catch(e) {} finally {}";
    assertNode(parse(expected)).isEqualTo(actual);
  }

  @Test
  public void testRemoveTryChild3() {
    // Test removing the catch clause.
    Node actual = parse("try {foo()} catch(e) {} finally {}");

    Node tryNode = actual.getFirstChild();
    Node catchBlocks = tryNode.getSecondChild();
    Node catchBlock = catchBlocks.getFirstChild();

    NodeUtil.removeChild(catchBlocks, catchBlock);
    String expected = "try {foo()} finally {}";
    assertNode(parse(expected)).isEqualTo(actual);
  }

  @Test
  public void testRemoveTryChild4() {
    // Test removing the block that contains the catch clause.
    Node actual = parse("try {foo()} catch(e) {} finally {}");

    Node tryNode = actual.getFirstChild();
    Node catchBlocks = tryNode.getSecondChild();

    NodeUtil.removeChild(tryNode, catchBlocks);
    String expected = "try {foo()} finally {}";
    assertNode(parse(expected)).isEqualTo(actual);
  }

  @Test
  public void testRemoveFromImport() {
    // Remove imported function
    Node actual = parse("import foo from './foo';");
    Node moduleBody = actual.getFirstChild();
    Node importNode = moduleBody.getFirstChild();
    Node functionFoo = importNode.getFirstChild();

    NodeUtil.removeChild(importNode, functionFoo);
    String expected = "import './foo';";
    assertNode(parse(expected)).isEqualTo(actual);
  }

  @Test
  public void testRemoveParamChild1() {
    // Remove traditional parameter
    Node actual = parse("function f(p1) {}");
    Node functionNode = actual.getFirstChild();
    Node paramList = functionNode.getFirstChild().getNext();
    Node p1 = paramList.getFirstChild();

    NodeUtil.removeChild(paramList, p1);
    String expected = "function f() {}";
    assertNode(parse(expected)).isEqualTo(actual);
  }

  @Test
  public void testRemoveParamChild2() {
    // Remove default parameter
    Node actual = parse("function f(p1 = 0, p2) {}");
    Node functionNode = actual.getFirstChild();
    Node paramList = functionNode.getFirstChild().getNext();
    Node p1 = paramList.getFirstChild();

    NodeUtil.removeChild(paramList, p1);
    String expected = "function f(p2) {}";
    assertNode(parse(expected)).isEqualTo(actual);
  }

  @Test
  public void testRemoveVarChild() {
    // Test removing the first child.
    Node actual = parse("var foo, goo, hoo");

    Node varNode = actual.getFirstChild();
    Node nameNode = varNode.getFirstChild();

    NodeUtil.removeChild(varNode, nameNode);
    String expected = "var goo, hoo";
    assertNode(parse(expected)).isEqualTo(actual);

    // Test removing the second child.
    actual = parse("var foo, goo, hoo");

    varNode = actual.getFirstChild();
    nameNode = varNode.getSecondChild();

    NodeUtil.removeChild(varNode, nameNode);
    expected = "var foo, hoo";
    assertNode(parse(expected)).isEqualTo(actual);

    // Test removing the last child of several children.
    actual = parse("var foo, hoo");

    varNode = actual.getFirstChild();
    nameNode = varNode.getSecondChild();

    NodeUtil.removeChild(varNode, nameNode);
    expected = "var foo";
    assertNode(parse(expected)).isEqualTo(actual);

    // Test removing the last.
    actual = parse("var hoo");

    varNode = actual.getFirstChild();
    nameNode = varNode.getFirstChild();

    NodeUtil.removeChild(varNode, nameNode);
    expected = "";
    assertNode(parse(expected)).isEqualTo(actual);
  }

  @Test
  public void testRemoveLetChild() {
    // Test removing the first child.
    Node actual = parse("let foo, goo, hoo");

    Node letNode = actual.getFirstChild();
    Node nameNode = letNode.getFirstChild();

    NodeUtil.removeChild(letNode, nameNode);
    String expected = "let goo, hoo";
    assertNode(parse(expected)).isEqualTo(actual);

    // Test removing the second child.
    actual = parse("let foo, goo, hoo");

    letNode = actual.getFirstChild();
    nameNode = letNode.getSecondChild();

    NodeUtil.removeChild(letNode, nameNode);
    expected = "let foo, hoo";
    assertNode(parse(expected)).isEqualTo(actual);

    // Test removing the last child of several children.
    actual = parse("let foo, hoo");

    letNode = actual.getFirstChild();
    nameNode = letNode.getSecondChild();

    NodeUtil.removeChild(letNode, nameNode);
    expected = "let foo";
    assertNode(parse(expected)).isEqualTo(actual);

    // Test removing the last.
    actual = parse("let hoo");

    letNode = actual.getFirstChild();
    nameNode = letNode.getFirstChild();

    NodeUtil.removeChild(letNode, nameNode);
    expected = "";
    assertNode(parse(expected)).isEqualTo(actual);
  }

  @Test
  public void testRemoveLabelChild1() {
    // Test removing the first child.
    Node actual = parse("foo: goo()");

    Node labelNode = actual.getFirstChild();
    Node callExpressNode = labelNode.getLastChild();

    NodeUtil.removeChild(labelNode, callExpressNode);
    String expected = "";
    assertNode(parse(expected)).isEqualTo(actual);
  }

  @Test
  public void testRemoveLabelChild2() {
    // Test removing the first child.
    Node actual = parse("achoo: foo: goo()");

    Node labelNode = actual.getFirstChild();
    Node callExpressNode = labelNode.getLastChild();

    NodeUtil.removeChild(labelNode, callExpressNode);
    String expected = "";
    assertNode(parse(expected)).isEqualTo(actual);
  }

  @Test
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
    assertNode(parse(expected)).isEqualTo(actual);

    // Remove all entries in object pattern
    NodeUtil.removeChild(pattern, b);
    NodeUtil.removeChild(pattern, c);
    expected = "var { } = {a:1, b:2, c:3};";
    assertNode(parse(expected)).isEqualTo(actual);

    // remove variable declaration from array pattern
    actual = parse("var [a, b] = [1, 2]");
    varNode = actual.getFirstChild();
    destructure = varNode.getFirstChild();
    pattern = destructure.getFirstChild();
    a = pattern.getFirstChild();

    NodeUtil.removeChild(pattern, a);
    expected = "var [ , b] = [1, 2];";
    assertNode(parse(expected)).isEqualTo(actual);
  }

  @Test
  public void testRemoveForChild() {
    // Test removing the initializer.
    Node actual = parse("for(var a=0;a<0;a++)foo()");

    Node forNode = actual.getFirstChild();
    Node child = forNode.getFirstChild();

    NodeUtil.removeChild(forNode, child);
    String expected = "for(;a<0;a++)foo()";
    assertNode(parse(expected)).isEqualTo(actual);

    // Test removing the condition.
    actual = parse("for(var a=0;a<0;a++)foo()");

    forNode = actual.getFirstChild();
    child = forNode.getSecondChild();

    NodeUtil.removeChild(forNode, child);
    expected = "for(var a=0;;a++)foo()";
    assertNode(parse(expected)).isEqualTo(actual);

    // Test removing the increment.
    actual = parse("for(var a=0;a<0;a++)foo()");

    forNode = actual.getFirstChild();
    child = forNode.getSecondChild().getNext();

    NodeUtil.removeChild(forNode, child);
    expected = "for(var a=0;a<0;)foo()";
    assertNode(parse(expected)).isEqualTo(actual);

    // Test removing the body.
    actual = parse("for(var a=0;a<0;a++)foo()");

    forNode = actual.getFirstChild();
    child = forNode.getLastChild();

    NodeUtil.removeChild(forNode, child);
    expected = "for(var a=0;a<0;a++);";
    assertNode(parse(expected)).isEqualTo(actual);

    // Test removing the body.
    actual = parse("for(a in ack)foo();");

    forNode = actual.getFirstChild();
    child = forNode.getLastChild();

    NodeUtil.removeChild(forNode, child);
    expected = "for(a in ack);";
    assertNode(parse(expected)).isEqualTo(actual);
  }

  private static void replaceDeclChild(String js, int declarationChild, String expected) {
    Node actual = parse(js);
    Node declarationNode = actual.getFirstChild();
    Node nameNode = declarationNode.getChildAtIndex(declarationChild);

    NodeUtil.replaceDeclarationChild(nameNode, IR.block());
    assertNode(parse(expected)).isEqualTo(actual);
  }

  @Test
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

  @Test
  public void testTryMergeBlock1() {
    // Test removing the initializer.
    Node actual = parse("{{a();b();}}");

    Node parentBlock = actual.getFirstChild();
    Node childBlock = parentBlock.getFirstChild();

    assertThat(NodeUtil.tryMergeBlock(childBlock, false)).isTrue();
    String expected = "{a();b();}";
    assertNode(parse(expected)).isEqualTo(actual);
  }

  @Test
  public void testTryMergeBlock2() {
    // Test removing the initializer.
    Node actual = parse("foo:{a();}");

    Node parentLabel = actual.getFirstChild();
    Node childBlock = parentLabel.getLastChild();

    assertThat(NodeUtil.tryMergeBlock(childBlock, false)).isFalse();
  }

  @Test
  public void testTryMergeBlock3() {
    // Test removing the initializer.
    String code = "foo:{a();boo()}";
    Node actual = parse("foo:{a();boo()}");

    Node parentLabel = actual.getFirstChild();
    Node childBlock = parentLabel.getLastChild();

    assertThat(NodeUtil.tryMergeBlock(childBlock, false)).isFalse();
    String expected = code;
    assertNode(parse(expected)).isEqualTo(actual);
  }

  @Test
  public void testTryMergeBlock4() {
    Node actual = parse("{const module$exports$Foo=class{}}");
    String expected = "const module$exports$Foo=class{}";

    Node block = actual.getFirstChild();

    assertThat(NodeUtil.tryMergeBlock(block, true)).isTrue();
    assertNode(parse(expected)).isEqualTo(actual);
  }

  @Test
  public void testCanMergeBlock1() {
    Node actual = parse("{a(); let x;}");

    Node block = actual.getFirstChild();

    assertThat(NodeUtil.canMergeBlock(block)).isFalse();
  }

  @Test
  public void testCanMergeBlock2() {
    Node actual = parse("{a(); f(); var x; {const y = 2;}}");

    Node parentBlock = actual.getFirstChild();
    Node childBlock = parentBlock.getLastChild();

    assertThat(NodeUtil.canMergeBlock(parentBlock)).isTrue();
    assertThat(NodeUtil.canMergeBlock(childBlock)).isFalse();
  }

  @Test
  public void testGetSourceName() {
    Node n = new Node(Token.BLOCK);
    Node parent = new Node(Token.BLOCK, n);
    parent.setSourceFileForTesting("foo");

    assertThat(NodeUtil.getSourceName(n)).isEqualTo("foo");
  }

  @Test
  public void testLocalValue1() {
    // Names are not known to be local.
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("x"))).isFalse();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("x()"))).isFalse();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("this"))).isFalse();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("arguments"))).isFalse();

    // We can't know if new objects are local unless we know
    // that they don't alias themselves.
    // TODO(tdeegan): Revisit this.
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("new x()"))).isFalse();

    // property references are assumed to be non-local
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("(new x()).y"))).isFalse();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("(new x())['y']"))).isFalse();

    // Primitive values are local
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("null"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("undefined"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("Infinity"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("NaN"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("1"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("'a'"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("true"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("false"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("[]"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("{}"))).isTrue();

    assertThat(NodeUtil.evaluatesToLocalValue(getNode("[x]"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("{'a':x}"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("{'a': {'b': 2}}"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("{'a': {'b': global}}"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("{get someGetter() { return 1; }}")))
        .isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("{get someGetter() { return global; }}")))
        .isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("{set someSetter(value) {}}"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("{[someComputedProperty]: {}}"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("{[someComputedProperty]: global}")))
        .isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("{[someComputedProperty]: {'a':x}}")))
        .isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("{[someComputedProperty]: {'a':1}}")))
        .isTrue();

    // increment/decrement results in primitive number, the previous value is
    // always coersed to a number (even in the post.
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("++x"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("--x"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("x++"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("x--"))).isTrue();

    // The left side of an only assign matters if it is an alias or mutable.
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("x=1"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("x=[]"))).isFalse();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("x=y"))).isFalse();
    // The right hand side of assignment opts don't matter, as they force
    // a local result.
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("x+=y"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("x*=y"))).isTrue();
    // Comparisons always result in locals, as they force a local boolean
    // result.
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("x==y"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("x!=y"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("x>y"))).isTrue();
    // Only the right side of a comma matters
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("(1,2)"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("(x,1)"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("(x,y)"))).isFalse();

    // Both the operands of OR matter
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("1||2"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("x||1"))).isFalse();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("x||y"))).isFalse();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("1||y"))).isFalse();

    // Both the operands of AND matter
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("1&&2"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("x&&1"))).isFalse();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("x&&y"))).isFalse();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("1&&y"))).isFalse();

    // Only the results of HOOK matter
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("x?1:2"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("x?x:2"))).isFalse();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("x?1:x"))).isFalse();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("x?x:y"))).isFalse();

    // Results of ops are local values
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("!y"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("~y"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("y + 1"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("y + z"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("y * z"))).isTrue();

    assertThat(NodeUtil.evaluatesToLocalValue(getNode("'a' in x"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("typeof x"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("x instanceof y"))).isTrue();

    assertThat(NodeUtil.evaluatesToLocalValue(getNode("void x"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("void 0"))).isTrue();

    assertThat(NodeUtil.evaluatesToLocalValue(getNode("{}.x"))).isFalse();

    assertThat(NodeUtil.evaluatesToLocalValue(getNode("{}.toString()"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("o.toString()"))).isTrue();

    assertThat(NodeUtil.evaluatesToLocalValue(getNode("o.valueOf()"))).isFalse();

    assertThat(NodeUtil.evaluatesToLocalValue(getNode("delete a.b"))).isTrue();
  }

  @Test
  public void testLocalValueTemplateLit() {
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("`hello`"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("`hello ${name}`"))).isTrue();
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("`${'name'}`"))).isTrue();
    assertThat(NodeUtil.isLiteralValue(getNode("`${'name'}`"), false)).isTrue();
    assertThat(NodeUtil.isImmutableValue(getNode("`${'name'}`"))).isTrue();
  }

  @Test
  public void testLocalValueTaggedTemplateLit1() {
    Node n = getNode("tag`simple string`");
    assertThat(NodeUtil.evaluatesToLocalValue(n)).isFalse();

    // Set 'tag' function as producing a local result
    Node.SideEffectFlags flags = new Node.SideEffectFlags();
    flags.clearAllFlags();
    n.setSideEffectFlags(flags);

    assertThat(NodeUtil.evaluatesToLocalValue(n)).isTrue();
  }

  @Test
  public void testLocalValueTaggedTemplateLit2() {
    // Here, replacement() may have side effects.
    Node n = getNode("tag`string with ${replacement()}`");
    assertThat(NodeUtil.evaluatesToLocalValue(n)).isFalse();

    // Set 'tag' function as producing a local result
    Node.SideEffectFlags flags = new Node.SideEffectFlags();
    flags.clearAllFlags();
    n.setSideEffectFlags(flags);

    assertThat(NodeUtil.evaluatesToLocalValue(n)).isTrue();
  }

  @Test
  public void testLocalValueNewExpr() {
    Node newExpr = getNode("new x()");
    assertThat(NodeUtil.evaluatesToLocalValue(newExpr)).isFalse();

    checkState(newExpr.isNew());
    Node.SideEffectFlags flags = new Node.SideEffectFlags();

    flags.clearAllFlags();
    newExpr.setSideEffectFlags(flags);

    assertThat(NodeUtil.evaluatesToLocalValue(newExpr)).isTrue();

    flags.clearAllFlags();
    flags.setMutatesThis();
    newExpr.setSideEffectFlags(flags);

    assertThat(NodeUtil.evaluatesToLocalValue(newExpr)).isTrue();

    flags.clearAllFlags();
    flags.setReturnsTainted();
    newExpr.setSideEffectFlags(flags);

    assertThat(NodeUtil.evaluatesToLocalValue(newExpr)).isTrue();

    flags.clearAllFlags();
    flags.setThrows();
    newExpr.setSideEffectFlags(flags);

    assertThat(NodeUtil.evaluatesToLocalValue(newExpr)).isFalse();

    flags.clearAllFlags();
    flags.setMutatesArguments();
    newExpr.setSideEffectFlags(flags);

    assertThat(NodeUtil.evaluatesToLocalValue(newExpr)).isFalse();

    flags.clearAllFlags();
    flags.setMutatesGlobalState();
    newExpr.setSideEffectFlags(flags);

    assertThat(NodeUtil.evaluatesToLocalValue(newExpr)).isFalse();
  }

  @Test
  public void testLocalValueSpread() {
    // Array literals are always known local values.
    assertThat(NodeUtil.evaluatesToLocalValue(getNode("[...x]"))).isTrue();
    assertThat(NodeUtil.isLiteralValue(getNode("[...x]"), false)).isFalse();
    assertThat(NodeUtil.isImmutableValue(getNode("[...x]"))).isFalse();

    assertThat(NodeUtil.evaluatesToLocalValue(getNode("{...x}"))).isTrue();
    assertThat(NodeUtil.isLiteralValue(getNode("{...x}"), false)).isFalse();
    assertThat(NodeUtil.isImmutableValue(getNode("{...x}"))).isFalse();
  }

  @Test
  public void testLocalValueAwait() {
    Node expr;
    expr = getAwaitNode("async function f() { await someAsyncAction(); }");
    assertThat(NodeUtil.evaluatesToLocalValue(expr)).isFalse();

    expr = getAwaitNode("async function f() { await {then:function() { return p }}; }");
    assertThat(NodeUtil.evaluatesToLocalValue(expr)).isFalse();

    // it isn't clear why someone would want to wait on a non-thenable value...
    expr = getAwaitNode("async function f() { await 5; }");
    assertThat(NodeUtil.evaluatesToLocalValue(expr)).isFalse();
  }

  @Test
  public void testLocalValueYield() {
    Node expr;
    expr = getYieldNode("function *f() { yield; }");
    assertThat(NodeUtil.evaluatesToLocalValue(expr)).isFalse();

    expr = getYieldNode("function *f() { yield 'something'; }");
    assertThat(NodeUtil.evaluatesToLocalValue(expr)).isFalse();
  }

  @Test
  public void testCallSideEffects() {
    Node callExpr = getNode("new x().method()");
    assertThat(NodeUtil.functionCallHasSideEffects(callExpr)).isTrue();

    Node newExpr = callExpr.getFirstFirstChild();
    checkState(newExpr.isNew());
    Node.SideEffectFlags flags = new Node.SideEffectFlags();

    // No side effects, local result
    flags.clearAllFlags();
    newExpr.setSideEffectFlags(flags);
    flags.clearAllFlags();
    callExpr.setSideEffectFlags(flags);

    assertThat(NodeUtil.evaluatesToLocalValue(callExpr)).isTrue();
    assertThat(NodeUtil.functionCallHasSideEffects(callExpr)).isFalse();
    assertThat(NodeUtil.mayHaveSideEffects(callExpr)).isFalse();

    // Modifies this, local result
    flags.clearAllFlags();
    newExpr.setSideEffectFlags(flags);
    flags.clearAllFlags();
    flags.setMutatesThis();
    callExpr.setSideEffectFlags(flags);

    assertThat(NodeUtil.evaluatesToLocalValue(callExpr)).isTrue();
    assertThat(NodeUtil.functionCallHasSideEffects(callExpr)).isFalse();
    assertThat(NodeUtil.mayHaveSideEffects(callExpr)).isFalse();

    // Modifies this, non-local result
    flags.clearAllFlags();
    newExpr.setSideEffectFlags(flags);
    flags.clearAllFlags();
    flags.setMutatesThis();
    flags.setReturnsTainted();
    callExpr.setSideEffectFlags(flags);

    assertThat(NodeUtil.evaluatesToLocalValue(callExpr)).isFalse();
    assertThat(NodeUtil.functionCallHasSideEffects(callExpr)).isFalse();
    assertThat(NodeUtil.mayHaveSideEffects(callExpr)).isFalse();

    // No modifications, non-local result
    flags.clearAllFlags();
    newExpr.setSideEffectFlags(flags);
    flags.clearAllFlags();
    flags.setReturnsTainted();
    callExpr.setSideEffectFlags(flags);

    assertThat(NodeUtil.evaluatesToLocalValue(callExpr)).isFalse();
    assertThat(NodeUtil.functionCallHasSideEffects(callExpr)).isFalse();
    assertThat(NodeUtil.mayHaveSideEffects(callExpr)).isFalse();

    // The new modifies global state, no side-effect call, non-local result
    // This call could be removed, but not the new.
    flags.clearAllFlags();
    flags.setMutatesGlobalState();
    newExpr.setSideEffectFlags(flags);
    flags.clearAllFlags();
    callExpr.setSideEffectFlags(flags);

    assertThat(NodeUtil.evaluatesToLocalValue(callExpr)).isTrue();
    assertThat(NodeUtil.functionCallHasSideEffects(callExpr)).isFalse();
    assertThat(NodeUtil.mayHaveSideEffects(callExpr)).isTrue();
  }

  @Test
  public void testValidDefine() {
    assertThat(getIsValidDefineValueResultFor("1")).isTrue();
    assertThat(getIsValidDefineValueResultFor("-3")).isTrue();
    assertThat(getIsValidDefineValueResultFor("true")).isTrue();
    assertThat(getIsValidDefineValueResultFor("false")).isTrue();
    assertThat(getIsValidDefineValueResultFor("'foo'")).isTrue();

    assertThat(getIsValidDefineValueResultFor("x")).isFalse();
    assertThat(getIsValidDefineValueResultFor("null")).isFalse();
    assertThat(getIsValidDefineValueResultFor("undefined")).isFalse();
    assertThat(getIsValidDefineValueResultFor("NaN")).isFalse();

    assertThat(getIsValidDefineValueResultFor("!true")).isTrue();
    assertThat(getIsValidDefineValueResultFor("-true")).isTrue();
    assertThat(getIsValidDefineValueResultFor("1 & 8")).isTrue();
    assertThat(getIsValidDefineValueResultFor("1 + 8")).isTrue();
    assertThat(getIsValidDefineValueResultFor("'a' + 'b'")).isTrue();
    assertThat(getIsValidDefineValueResultFor("true ? 'a' : 'b'")).isTrue();

    assertThat(getIsValidDefineValueResultFor("1 & foo")).isFalse();
    assertThat(getIsValidDefineValueResultFor("foo ? 'a' : 'b'")).isFalse();
  }

  private boolean getIsValidDefineValueResultFor(String js) {
    Node script = parse("var test = " + js + ";");
    Node var = script.getFirstChild();
    Node name = var.getFirstChild();
    Node value = name.getFirstChild();

    ImmutableSet<String> defines = ImmutableSet.of();
    return NodeUtil.isValidDefineValue(value, defines);
  }

  @Test
  public void testGetOctalNumberValue() {
    assertThat(NodeUtil.getNumberValue(getNode("022"))).isEqualTo(18.0);
  }

  @SuppressWarnings("JUnit3FloatingPointComparisonWithoutDelta")
  @Test
  public void testGetNumberValue() {
    // Strings
    assertThat(NodeUtil.getNumberValue(getNode("'\\uFEFF1'"))).isEqualTo(1.0);
    assertThat(NodeUtil.getNumberValue(getNode("''"))).isEqualTo(0.0);
    assertThat(NodeUtil.getNumberValue(getNode("' '"))).isEqualTo(0.0);
    assertThat(NodeUtil.getNumberValue(getNode("' \\t'"))).isEqualTo(0.0);
    assertThat(NodeUtil.getNumberValue(getNode("'+0'"))).isEqualTo(0.0);
    assertThat(NodeUtil.getNumberValue(getNode("'-0'"))).isEqualTo(-0.0);
    assertThat(NodeUtil.getNumberValue(getNode("'+2'"))).isEqualTo(2.0);
    assertThat(NodeUtil.getNumberValue(getNode("'-1.6'"))).isEqualTo(-1.6);
    assertThat(NodeUtil.getNumberValue(getNode("'16'"))).isEqualTo(16.0);
    assertThat(NodeUtil.getNumberValue(getNode("' 16 '"))).isEqualTo(16.0);
    assertThat(NodeUtil.getNumberValue(getNode("' 16 '"))).isEqualTo(16.0);
    assertThat(NodeUtil.getNumberValue(getNode("'123e2'"))).isEqualTo(12300.0);
    assertThat(NodeUtil.getNumberValue(getNode("'123E2'"))).isEqualTo(12300.0);
    assertThat(NodeUtil.getNumberValue(getNode("'123e-2'"))).isEqualTo(1.23);
    assertThat(NodeUtil.getNumberValue(getNode("'123E-2'"))).isEqualTo(1.23);
    assertThat(NodeUtil.getNumberValue(getNode("'-123e-2'"))).isEqualTo(-1.23);
    assertThat(NodeUtil.getNumberValue(getNode("'-123E-2'"))).isEqualTo(-1.23);
    assertThat(NodeUtil.getNumberValue(getNode("'+123e-2'"))).isEqualTo(1.23);
    assertThat(NodeUtil.getNumberValue(getNode("'+123E-2'"))).isEqualTo(1.23);
    assertThat(NodeUtil.getNumberValue(getNode("'+123e+2'"))).isEqualTo(12300.0);
    assertThat(NodeUtil.getNumberValue(getNode("'+123E+2'"))).isEqualTo(12300.0);

    assertThat(NodeUtil.getNumberValue(getNode("'0xf'"))).isEqualTo(15.0);
    assertThat(NodeUtil.getNumberValue(getNode("'0xF'"))).isEqualTo(15.0);

    // Chrome and rhino behavior differently from FF and IE. FF and IE
    // consider a negative hex number to be invalid
    assertThat(NodeUtil.getNumberValue(getNode("'-0xf'"))).isNull();
    assertThat(NodeUtil.getNumberValue(getNode("'-0xF'"))).isNull();
    assertThat(NodeUtil.getNumberValue(getNode("'+0xf'"))).isNull();
    assertThat(NodeUtil.getNumberValue(getNode("'+0xF'"))).isNull();

    assertThat(NodeUtil.getNumberValue(getNode("'0X10'"))).isEqualTo(16.0);
    assertThat(NodeUtil.getNumberValue(getNode("'0X10.8'"))).isNaN();
    assertThat(NodeUtil.getNumberValue(getNode("'077'"))).isEqualTo(77.0);
    assertThat(NodeUtil.getNumberValue(getNode("'-077'"))).isEqualTo(-77.0);
    assertThat(NodeUtil.getNumberValue(getNode("'-077.5'"))).isEqualTo(-77.5);
    assertThat(NodeUtil.getNumberValue(getNode("'-Infinity'"))).isNegativeInfinity();
    assertThat(NodeUtil.getNumberValue(getNode("'Infinity'"))).isPositiveInfinity();
    assertThat(NodeUtil.getNumberValue(getNode("'+Infinity'"))).isPositiveInfinity();
    // Firefox treats "infinity" as "Infinity", IE treats it as NaN
    assertThat(NodeUtil.getNumberValue(getNode("'-infinity'"))).isNull();
    assertThat(NodeUtil.getNumberValue(getNode("'infinity'"))).isNull();
    assertThat(NodeUtil.getNumberValue(getNode("'+infinity'"))).isNull();

    assertThat(NodeUtil.getNumberValue(getNode("'NaN'"))).isNaN();
    assertThat(NodeUtil.getNumberValue(getNode("'some unknown string'"))).isNaN();
    assertThat(NodeUtil.getNumberValue(getNode("'123 blah'"))).isNaN();

    // Literals
    assertThat(NodeUtil.getNumberValue(getNode("1"))).isEqualTo(1.0);
    // "-1" is parsed as a literal
    assertThat(NodeUtil.getNumberValue(getNode("-1"))).isEqualTo(-1.0);
    // "+1" is parse as an op + literal
    assertThat(NodeUtil.getNumberValue(getNode("+1"))).isNull();
    assertThat(NodeUtil.getNumberValue(getNode("22"))).isEqualTo(22.0);
    assertThat(NodeUtil.getNumberValue(getNode("022"))).isEqualTo(18.0);
    assertThat(NodeUtil.getNumberValue(getNode("0x22"))).isEqualTo(34.0);

    assertThat(NodeUtil.getNumberValue(getNode("true"))).isEqualTo(1.0);
    assertThat(NodeUtil.getNumberValue(getNode("false"))).isEqualTo(0.0);
    assertThat(NodeUtil.getNumberValue(getNode("null"))).isEqualTo(0.0);
    assertThat(NodeUtil.getNumberValue(getNode("void 0"))).isNaN();
    assertThat(NodeUtil.getNumberValue(getNode("void f"))).isNaN();
    // values with side-effects are ignored.
    assertThat(NodeUtil.getNumberValue(getNode("void f()"))).isNull();
    assertThat(NodeUtil.getNumberValue(getNode("NaN"))).isNaN();
    assertThat(NodeUtil.getNumberValue(getNode("Infinity"))).isPositiveInfinity();
    assertThat(NodeUtil.getNumberValue(getNode("-Infinity"))).isNegativeInfinity();

    // "infinity" is not a known name.
    assertThat(NodeUtil.getNumberValue(getNode("infinity"))).isNull();
    assertThat(NodeUtil.getNumberValue(getNode("-infinity"))).isNull();

    // getNumberValue only converts literals
    assertThat(NodeUtil.getNumberValue(getNode("x"))).isNull();
    assertThat(NodeUtil.getNumberValue(getNode("x.y"))).isNull();
    assertThat(NodeUtil.getNumberValue(getNode("1/2"))).isNull();
    assertThat(NodeUtil.getNumberValue(getNode("1-2"))).isNull();
    assertThat(NodeUtil.getNumberValue(getNode("+1"))).isNull();
  }

  @Test
  public void testIsNumericResult() {
    assertThat(NodeUtil.isNumericResult(getNode("1"))).isTrue();
    assertThat(NodeUtil.isNumericResult(getNode("true"))).isFalse();
    assertThat(NodeUtil.isNumericResult(getNode("+true"))).isTrue();
    assertThat(NodeUtil.isNumericResult(getNode("+1"))).isTrue();
    assertThat(NodeUtil.isNumericResult(getNode("-1"))).isTrue();
    assertThat(NodeUtil.isNumericResult(getNode("-Infinity"))).isTrue();
    assertThat(NodeUtil.isNumericResult(getNode("Infinity"))).isTrue();
    assertThat(NodeUtil.isNumericResult(getNode("NaN"))).isTrue();
    assertThat(NodeUtil.isNumericResult(getNode("undefined"))).isFalse();
    assertThat(NodeUtil.isNumericResult(getNode("void 0"))).isFalse();

    assertThat(NodeUtil.isNumericResult(getNode("a << b"))).isTrue();
    assertThat(NodeUtil.isNumericResult(getNode("a >> b"))).isTrue();
    assertThat(NodeUtil.isNumericResult(getNode("a >>> b"))).isTrue();

    assertThat(NodeUtil.isNumericResult(getNode("a == b"))).isFalse();
    assertThat(NodeUtil.isNumericResult(getNode("a != b"))).isFalse();
    assertThat(NodeUtil.isNumericResult(getNode("a === b"))).isFalse();
    assertThat(NodeUtil.isNumericResult(getNode("a !== b"))).isFalse();
    assertThat(NodeUtil.isNumericResult(getNode("a < b"))).isFalse();
    assertThat(NodeUtil.isNumericResult(getNode("a > b"))).isFalse();
    assertThat(NodeUtil.isNumericResult(getNode("a <= b"))).isFalse();
    assertThat(NodeUtil.isNumericResult(getNode("a >= b"))).isFalse();
    assertThat(NodeUtil.isNumericResult(getNode("a in b"))).isFalse();
    assertThat(NodeUtil.isNumericResult(getNode("a instanceof b"))).isFalse();

    assertThat(NodeUtil.isNumericResult(getNode("'a'"))).isFalse();
    assertThat(NodeUtil.isNumericResult(getNode("'a'+b"))).isFalse();
    assertThat(NodeUtil.isNumericResult(getNode("a+'b'"))).isFalse();
    assertThat(NodeUtil.isNumericResult(getNode("a+b"))).isFalse();
    assertThat(NodeUtil.isNumericResult(getNode("a()"))).isFalse();
    assertThat(NodeUtil.isNumericResult(getNode("''.a"))).isFalse();
    assertThat(NodeUtil.isNumericResult(getNode("a.b"))).isFalse();
    assertThat(NodeUtil.isNumericResult(getNode("a.b()"))).isFalse();
    assertThat(NodeUtil.isNumericResult(getNode("a().b()"))).isFalse();
    assertThat(NodeUtil.isNumericResult(getNode("new a()"))).isFalse();

    // Definitely not numeric
    assertThat(NodeUtil.isNumericResult(getNode("([1,2])"))).isFalse();
    assertThat(NodeUtil.isNumericResult(getNode("({a:1})"))).isFalse();

    // Recurse into the expression when necessary.
    assertThat(NodeUtil.isNumericResult(getNode("1 && 2"))).isTrue();
    assertThat(NodeUtil.isNumericResult(getNode("1 || 2"))).isTrue();
    assertThat(NodeUtil.isNumericResult(getNode("a ? 2 : 3"))).isTrue();
    assertThat(NodeUtil.isNumericResult(getNode("a,1"))).isTrue();
    assertThat(NodeUtil.isNumericResult(getNode("a=1"))).isTrue();

    assertThat(NodeUtil.isNumericResult(getNode("a += 1"))).isFalse();

    assertThat(NodeUtil.isNumericResult(getNode("a -= 1"))).isTrue();
    assertThat(NodeUtil.isNumericResult(getNode("a *= 1"))).isTrue();
    assertThat(NodeUtil.isNumericResult(getNode("--a"))).isTrue();
    assertThat(NodeUtil.isNumericResult(getNode("++a"))).isTrue();
    assertThat(NodeUtil.isNumericResult(getNode("a++"))).isTrue();
    assertThat(NodeUtil.isNumericResult(getNode("a--"))).isTrue();
  }

  @Test
  public void testIsBooleanResult() {
    assertThat(NodeUtil.isBooleanResult(getNode("1"))).isFalse();
    assertThat(NodeUtil.isBooleanResult(getNode("true"))).isTrue();
    assertThat(NodeUtil.isBooleanResult(getNode("+true"))).isFalse();
    assertThat(NodeUtil.isBooleanResult(getNode("+1"))).isFalse();
    assertThat(NodeUtil.isBooleanResult(getNode("-1"))).isFalse();
    assertThat(NodeUtil.isBooleanResult(getNode("-Infinity"))).isFalse();
    assertThat(NodeUtil.isBooleanResult(getNode("Infinity"))).isFalse();
    assertThat(NodeUtil.isBooleanResult(getNode("NaN"))).isFalse();
    assertThat(NodeUtil.isBooleanResult(getNode("undefined"))).isFalse();
    assertThat(NodeUtil.isBooleanResult(getNode("void 0"))).isFalse();

    assertThat(NodeUtil.isBooleanResult(getNode("a << b"))).isFalse();
    assertThat(NodeUtil.isBooleanResult(getNode("a >> b"))).isFalse();
    assertThat(NodeUtil.isBooleanResult(getNode("a >>> b"))).isFalse();

    assertThat(NodeUtil.isBooleanResult(getNode("a == b"))).isTrue();
    assertThat(NodeUtil.isBooleanResult(getNode("a != b"))).isTrue();
    assertThat(NodeUtil.isBooleanResult(getNode("a === b"))).isTrue();
    assertThat(NodeUtil.isBooleanResult(getNode("a !== b"))).isTrue();
    assertThat(NodeUtil.isBooleanResult(getNode("a < b"))).isTrue();
    assertThat(NodeUtil.isBooleanResult(getNode("a > b"))).isTrue();
    assertThat(NodeUtil.isBooleanResult(getNode("a <= b"))).isTrue();
    assertThat(NodeUtil.isBooleanResult(getNode("a >= b"))).isTrue();
    assertThat(NodeUtil.isBooleanResult(getNode("a in b"))).isTrue();
    assertThat(NodeUtil.isBooleanResult(getNode("a instanceof b"))).isTrue();

    assertThat(NodeUtil.isBooleanResult(getNode("'a'"))).isFalse();
    assertThat(NodeUtil.isBooleanResult(getNode("'a'+b"))).isFalse();
    assertThat(NodeUtil.isBooleanResult(getNode("a+'b'"))).isFalse();
    assertThat(NodeUtil.isBooleanResult(getNode("a+b"))).isFalse();
    assertThat(NodeUtil.isBooleanResult(getNode("a()"))).isFalse();
    assertThat(NodeUtil.isBooleanResult(getNode("''.a"))).isFalse();
    assertThat(NodeUtil.isBooleanResult(getNode("a.b"))).isFalse();
    assertThat(NodeUtil.isBooleanResult(getNode("a.b()"))).isFalse();
    assertThat(NodeUtil.isBooleanResult(getNode("a().b()"))).isFalse();
    assertThat(NodeUtil.isBooleanResult(getNode("new a()"))).isFalse();
    assertThat(NodeUtil.isBooleanResult(getNode("delete a"))).isTrue();

    // Definitely not boolean
    assertThat(NodeUtil.isBooleanResult(getNode("([true,false])"))).isFalse();
    assertThat(NodeUtil.isBooleanResult(getNode("({a:true})"))).isFalse();

    // These are boolean
    assertThat(NodeUtil.isBooleanResult(getNode("true && false"))).isTrue();
    assertThat(NodeUtil.isBooleanResult(getNode("true || false"))).isTrue();
    assertThat(NodeUtil.isBooleanResult(getNode("a ? true : false"))).isTrue();
    assertThat(NodeUtil.isBooleanResult(getNode("a,true"))).isTrue();
    assertThat(NodeUtil.isBooleanResult(getNode("a=true"))).isTrue();
    assertThat(NodeUtil.isBooleanResult(getNode("a=1"))).isFalse();
  }

  @Test
  public void testMayBeString() {
    assertThat(NodeUtil.mayBeString(getNode("1"))).isFalse();
    assertThat(NodeUtil.mayBeString(getNode("true"))).isFalse();
    assertThat(NodeUtil.mayBeString(getNode("+true"))).isFalse();
    assertThat(NodeUtil.mayBeString(getNode("+1"))).isFalse();
    assertThat(NodeUtil.mayBeString(getNode("-1"))).isFalse();
    assertThat(NodeUtil.mayBeString(getNode("-Infinity"))).isFalse();
    assertThat(NodeUtil.mayBeString(getNode("Infinity"))).isFalse();
    assertThat(NodeUtil.mayBeString(getNode("NaN"))).isFalse();
    assertThat(NodeUtil.mayBeString(getNode("undefined"))).isFalse();
    assertThat(NodeUtil.mayBeString(getNode("void 0"))).isFalse();
    assertThat(NodeUtil.mayBeString(getNode("null"))).isFalse();

    assertThat(NodeUtil.mayBeString(getNode("a << b"))).isFalse();
    assertThat(NodeUtil.mayBeString(getNode("a >> b"))).isFalse();
    assertThat(NodeUtil.mayBeString(getNode("a >>> b"))).isFalse();

    assertThat(NodeUtil.mayBeString(getNode("a == b"))).isFalse();
    assertThat(NodeUtil.mayBeString(getNode("a != b"))).isFalse();
    assertThat(NodeUtil.mayBeString(getNode("a === b"))).isFalse();
    assertThat(NodeUtil.mayBeString(getNode("a !== b"))).isFalse();
    assertThat(NodeUtil.mayBeString(getNode("a < b"))).isFalse();
    assertThat(NodeUtil.mayBeString(getNode("a > b"))).isFalse();
    assertThat(NodeUtil.mayBeString(getNode("a <= b"))).isFalse();
    assertThat(NodeUtil.mayBeString(getNode("a >= b"))).isFalse();
    assertThat(NodeUtil.mayBeString(getNode("a in b"))).isFalse();
    assertThat(NodeUtil.mayBeString(getNode("a instanceof b"))).isFalse();

    assertThat(NodeUtil.mayBeString(getNode("'a'"))).isTrue();
    assertThat(NodeUtil.mayBeString(getNode("'a'+b"))).isTrue();
    assertThat(NodeUtil.mayBeString(getNode("a+'b'"))).isTrue();
    assertThat(NodeUtil.mayBeString(getNode("a+b"))).isTrue();
    assertThat(NodeUtil.mayBeString(getNode("a()"))).isTrue();
    assertThat(NodeUtil.mayBeString(getNode("''.a"))).isTrue();
    assertThat(NodeUtil.mayBeString(getNode("a.b"))).isTrue();
    assertThat(NodeUtil.mayBeString(getNode("a.b()"))).isTrue();
    assertThat(NodeUtil.mayBeString(getNode("a().b()"))).isTrue();
    assertThat(NodeUtil.mayBeString(getNode("new a()"))).isTrue();

    // These can't be strings but they aren't handled yet.
    assertThat(NodeUtil.mayBeString(getNode("1 && 2"))).isFalse();
    assertThat(NodeUtil.mayBeString(getNode("1 || 2"))).isFalse();
    assertThat(NodeUtil.mayBeString(getNode("1 ? 2 : 3"))).isFalse();
    assertThat(NodeUtil.mayBeString(getNode("1,2"))).isFalse();
    assertThat(NodeUtil.mayBeString(getNode("a=1"))).isFalse();
    assertThat(NodeUtil.mayBeString(getNode("1+1"))).isFalse();
    assertThat(NodeUtil.mayBeString(getNode("true+true"))).isFalse();
    assertThat(NodeUtil.mayBeString(getNode("null+null"))).isFalse();
    assertThat(NodeUtil.mayBeString(getNode("NaN+NaN"))).isFalse();

    // These are not strings but they aren't primitives either
    assertThat(NodeUtil.mayBeString(getNode("([1,2])"))).isTrue();
    assertThat(NodeUtil.mayBeString(getNode("({a:1})"))).isTrue();
    assertThat(NodeUtil.mayBeString(getNode("({}+1)"))).isTrue();
    assertThat(NodeUtil.mayBeString(getNode("(1+{})"))).isTrue();
    assertThat(NodeUtil.mayBeString(getNode("([]+1)"))).isTrue();
    assertThat(NodeUtil.mayBeString(getNode("(1+[])"))).isTrue();

    assertThat(NodeUtil.mayBeString(getNode("a += 'x'"))).isTrue();
    assertThat(NodeUtil.mayBeString(getNode("a += 1"))).isTrue();
  }

  @Test
  public void testIsStringResult() {
    assertThat(NodeUtil.isStringResult(getNode("1"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("true"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("+true"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("+1"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("-1"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("-Infinity"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("Infinity"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("NaN"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("undefined"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("void 0"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("null"))).isFalse();

    assertThat(NodeUtil.isStringResult(getNode("a << b"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("a >> b"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("a >>> b"))).isFalse();

    assertThat(NodeUtil.isStringResult(getNode("a == b"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("a != b"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("a === b"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("a !== b"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("a < b"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("a > b"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("a <= b"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("a >= b"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("a in b"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("a instanceof b"))).isFalse();

    assertThat(NodeUtil.isStringResult(getNode("'a'"))).isTrue();
    assertThat(NodeUtil.isStringResult(getNode("'a'+b"))).isTrue();
    assertThat(NodeUtil.isStringResult(getNode("a+'b'"))).isTrue();
    assertThat(NodeUtil.isStringResult(getNode("a+b"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("a()"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("''.a"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("a.b"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("a.b()"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("a().b()"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("new a()"))).isFalse();

    // These can't be strings but they aren't handled yet.
    assertThat(NodeUtil.isStringResult(getNode("1 && 2"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("1 || 2"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("1 ? 2 : 3"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("1,2"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("a=1"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("1+1"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("true+true"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("null+null"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("NaN+NaN"))).isFalse();

    // These are not strings but they aren't primitives either
    assertThat(NodeUtil.isStringResult(getNode("([1,2])"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("({a:1})"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("({}+1)"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("(1+{})"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("([]+1)"))).isFalse();
    assertThat(NodeUtil.isStringResult(getNode("(1+[])"))).isFalse();

    assertThat(NodeUtil.isStringResult(getNode("a += 'x'"))).isTrue();

    // Template literals
    assertThat(NodeUtil.isStringResult(getNode("`x`"))).isTrue();
    assertThat(NodeUtil.isStringResult(getNode("`a${b}c`"))).isTrue();
  }

  @Test
  public void testIsObjectResult() {
    assertThat(NodeUtil.isObjectResult(getNode("1"))).isFalse();
    assertThat(NodeUtil.isObjectResult(getNode("true"))).isFalse();
    assertThat(NodeUtil.isObjectResult(getNode("+true"))).isFalse();
    assertThat(NodeUtil.isObjectResult(getNode("+1"))).isFalse();
    assertThat(NodeUtil.isObjectResult(getNode("-1"))).isFalse();
    assertThat(NodeUtil.isObjectResult(getNode("-Infinity"))).isFalse();
    assertThat(NodeUtil.isObjectResult(getNode("Infinity"))).isFalse();
    assertThat(NodeUtil.isObjectResult(getNode("NaN"))).isFalse();
    assertThat(NodeUtil.isObjectResult(getNode("undefined"))).isFalse();
    assertThat(NodeUtil.isObjectResult(getNode("void 0"))).isFalse();

    assertThat(NodeUtil.isObjectResult(getNode("a << b"))).isFalse();
    assertThat(NodeUtil.isObjectResult(getNode("a >> b"))).isFalse();
    assertThat(NodeUtil.isObjectResult(getNode("a >>> b"))).isFalse();

    assertThat(NodeUtil.isObjectResult(getNode("a == b"))).isFalse();
    assertThat(NodeUtil.isObjectResult(getNode("a != b"))).isFalse();
    assertThat(NodeUtil.isObjectResult(getNode("a === b"))).isFalse();
    assertThat(NodeUtil.isObjectResult(getNode("a !== b"))).isFalse();
    assertThat(NodeUtil.isObjectResult(getNode("a < b"))).isFalse();
    assertThat(NodeUtil.isObjectResult(getNode("a > b"))).isFalse();
    assertThat(NodeUtil.isObjectResult(getNode("a <= b"))).isFalse();
    assertThat(NodeUtil.isObjectResult(getNode("a >= b"))).isFalse();
    assertThat(NodeUtil.isObjectResult(getNode("a in b"))).isFalse();
    assertThat(NodeUtil.isObjectResult(getNode("a instanceof b"))).isFalse();

    assertThat(NodeUtil.isObjectResult(getNode("delete a"))).isFalse();
    assertThat(NodeUtil.isObjectResult(getNode("'a'"))).isFalse();
    assertThat(NodeUtil.isObjectResult(getNode("'a'+b"))).isFalse();
    assertThat(NodeUtil.isObjectResult(getNode("a+'b'"))).isFalse();
    assertThat(NodeUtil.isObjectResult(getNode("a+b"))).isFalse();
    assertThat(NodeUtil.isObjectResult(getNode("{},true"))).isFalse();

    // "false" here means "unknown"
    assertThat(NodeUtil.isObjectResult(getNode("a()"))).isFalse();
    assertThat(NodeUtil.isObjectResult(getNode("''.a"))).isFalse();
    assertThat(NodeUtil.isObjectResult(getNode("a.b"))).isFalse();
    assertThat(NodeUtil.isObjectResult(getNode("a.b()"))).isFalse();
    assertThat(NodeUtil.isObjectResult(getNode("a().b()"))).isFalse();
    assertThat(NodeUtil.isObjectResult(getNode("a ? true : {}"))).isFalse();

    // These are objects but aren't handled yet.
    assertThat(NodeUtil.isObjectResult(getNode("true && {}"))).isFalse();
    assertThat(NodeUtil.isObjectResult(getNode("true || {}"))).isFalse();

    // Definitely objects
    assertThat(NodeUtil.isObjectResult(getNode("new a.b()"))).isTrue();
    assertThat(NodeUtil.isObjectResult(getNode("([true,false])"))).isTrue();
    assertThat(NodeUtil.isObjectResult(getNode("({a:true})"))).isTrue();
    assertThat(NodeUtil.isObjectResult(getNode("a={}"))).isTrue();
    assertThat(NodeUtil.isObjectResult(getNode("[] && {}"))).isTrue();
    assertThat(NodeUtil.isObjectResult(getNode("[] || {}"))).isTrue();
    assertThat(NodeUtil.isObjectResult(getNode("a ? [] : {}"))).isTrue();
    assertThat(NodeUtil.isObjectResult(getNode("{},[]"))).isTrue();
    assertThat(NodeUtil.isObjectResult(getNode("/a/g"))).isTrue();
  }

  @Test
  public void testValidNames() {
    assertThat(isValidPropertyName("a")).isTrue();
    assertThat(isValidPropertyName("a3")).isTrue();
    assertThat(isValidPropertyName("3a")).isFalse();
    assertThat(isValidPropertyName("a.")).isFalse();
    assertThat(isValidPropertyName(".a")).isFalse();
    assertThat(isValidPropertyName("a.b")).isFalse();
    assertThat(isValidPropertyName("true")).isFalse();
    assertThat(isValidPropertyName("a.true")).isFalse();
    assertThat(isValidPropertyName("a..b")).isFalse();

    assertThat(NodeUtil.isValidSimpleName("a")).isTrue();
    assertThat(NodeUtil.isValidSimpleName("a3")).isTrue();
    assertThat(NodeUtil.isValidSimpleName("3a")).isFalse();
    assertThat(NodeUtil.isValidSimpleName("a.")).isFalse();
    assertThat(NodeUtil.isValidSimpleName(".a")).isFalse();
    assertThat(NodeUtil.isValidSimpleName("a.b")).isFalse();
    assertThat(NodeUtil.isValidSimpleName("true")).isFalse();
    assertThat(NodeUtil.isValidSimpleName("a.true")).isFalse();
    assertThat(NodeUtil.isValidSimpleName("a..b")).isFalse();

    assertThat(isValidQualifiedName("a")).isTrue();
    assertThat(isValidQualifiedName("a3")).isTrue();
    assertThat(isValidQualifiedName("3a")).isFalse();
    assertThat(isValidQualifiedName("a.")).isFalse();
    assertThat(isValidQualifiedName(".a")).isFalse();
    assertThat(isValidQualifiedName("a.b")).isTrue();
    assertThat(isValidQualifiedName("true")).isFalse();
    assertThat(isValidQualifiedName("a.true")).isFalse();
    assertThat(isValidQualifiedName("a..b")).isFalse();
  }

  @Test
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

  @Test
  public void testGetBestLValue() {
    assertThat(getFunctionLValue("var x = function() {};")).isEqualTo("x");
    assertThat(getFunctionLValue("x = function() {};")).isEqualTo("x");
    assertThat(getFunctionLValue("function x() {};")).isEqualTo("x");
    assertThat(getFunctionLValue("var x = y ? z : function() {};")).isEqualTo("x");
    assertThat(getFunctionLValue("var x = y ? function() {} : z;")).isEqualTo("x");
    assertThat(getFunctionLValue("var x = y && function() {};")).isEqualTo("x");
    assertThat(getFunctionLValue("var x = y || function() {};")).isEqualTo("x");
    assertThat(getFunctionLValue("var x = (y, function() {});")).isEqualTo("x");
  }

  @Test
  public void testGetBestLValueName() {
    Function<String, String> getBestName =
        (js) -> NodeUtil.getBestLValueName(NodeUtil.getBestLValue(getFunctionNode(js)));
    assertThat(getBestName.apply("var x = function() {};")).isEqualTo("x");
    assertThat(getBestName.apply("x = function() {};")).isEqualTo("x");
    assertThat(getBestName.apply("function x() {};")).isEqualTo("x");
    assertThat(getBestName.apply("var x = y ? z : function() {};")).isEqualTo("x");
    assertThat(getBestName.apply("var x = y ? function() {} : z;")).isEqualTo("x");
    assertThat(getBestName.apply("var x = y && function() {};")).isEqualTo("x");
    assertThat(getBestName.apply("var x = y || function() {};")).isEqualTo("x");
    assertThat(getBestName.apply("var x = (y, function() {});")).isEqualTo("x");
    assertThat(getBestName.apply("C.prototype.d = function() {};")).isEqualTo("C.prototype.d");
    assertThat(getBestName.apply("class C { d() {} };")).isEqualTo("C.prototype.d");
    assertThat(getBestName.apply("C.d = function() {};")).isEqualTo("C.d");
    assertThat(getBestName.apply("class C { static d() {} };")).isEqualTo("C.d");
  }

  @Test
  public void testGetRValueOfLValue() {
    assertThat(functionIsRValueOfAssign("x = function() {};")).isTrue();
    assertThat(functionIsRValueOfAssign("x += function() {};")).isTrue();
    assertThat(functionIsRValueOfAssign("x -= function() {};")).isTrue();
    assertThat(functionIsRValueOfAssign("x *= function() {};")).isTrue();
    assertThat(functionIsRValueOfAssign("x /= function() {};")).isTrue();
    assertThat(functionIsRValueOfAssign("x <<= function() {};")).isTrue();
    assertThat(functionIsRValueOfAssign("x >>= function() {};")).isTrue();
    assertThat(functionIsRValueOfAssign("x >>= function() {};")).isTrue();
    assertThat(functionIsRValueOfAssign("x >>>= function() {};")).isTrue();
    assertThat(functionIsRValueOfAssign("x = y ? x : function() {};")).isFalse();
  }

  /**
   * When the left side is a destructuring pattern, generally it's not possible to identify the RHS
   * for a specific name on the LHS.
   */
  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testIsNaN() {
    assertThat(NodeUtil.isNaN(getNode("NaN"))).isTrue();
    assertThat(NodeUtil.isNaN(getNode("Infinity"))).isFalse();
    assertThat(NodeUtil.isNaN(getNode("x"))).isFalse();
    assertThat(NodeUtil.isNaN(getNode("0/0"))).isTrue();
    assertThat(NodeUtil.isNaN(getNode("1/0"))).isFalse();
    assertThat(NodeUtil.isNaN(getNode("0/1"))).isFalse();
    assertThat(NodeUtil.isNaN(IR.number(0.0))).isFalse();
  }

  @Test
  public void testIsExecutedExactlyOnce() {
    assertThat(executedOnceTestCase("x;")).isTrue();

    assertThat(executedOnceTestCase("x && 1;")).isTrue();
    assertThat(executedOnceTestCase("1 && x;")).isFalse();

    assertThat(executedOnceTestCase("1 && (x && 1);")).isFalse();

    assertThat(executedOnceTestCase("x || 1;")).isTrue();
    assertThat(executedOnceTestCase("1 || x;")).isFalse();

    assertThat(executedOnceTestCase("1 && (x || 1);")).isFalse();

    assertThat(executedOnceTestCase("x ? 1 : 2;")).isTrue();
    assertThat(executedOnceTestCase("1 ? 1 : x;")).isFalse();
    assertThat(executedOnceTestCase("1 ? x : 2;")).isFalse();

    assertThat(executedOnceTestCase("1 && (x ? 1 : 2);")).isFalse();

    assertThat(executedOnceTestCase("if (x) {}")).isTrue();
    assertThat(executedOnceTestCase("if (true) {x;}")).isFalse();
    assertThat(executedOnceTestCase("if (true) {} else {x;}")).isFalse();

    assertThat(executedOnceTestCase("if (1) { if (x) {} }")).isFalse();

    assertThat(executedOnceTestCase("for(x;;){}")).isTrue();
    assertThat(executedOnceTestCase("for(;x;){}")).isFalse();
    assertThat(executedOnceTestCase("for(;;x){}")).isFalse();
    assertThat(executedOnceTestCase("for(;;){x;}")).isFalse();

    assertThat(executedOnceTestCase("if (1) { for(x;;){} }")).isFalse();

    assertThat(executedOnceTestCase("for(x in {}){}")).isFalse();
    assertThat(executedOnceTestCase("for({}.a in x){}")).isTrue();
    assertThat(executedOnceTestCase("for({}.a in {}){x}")).isFalse();

    assertThat(executedOnceTestCase("if (1) { for(x in {}){} }")).isFalse();

    assertThat(executedOnceTestCase("switch (x) {}")).isTrue();
    assertThat(executedOnceTestCase("switch (1) {case x:}")).isFalse();
    assertThat(executedOnceTestCase("switch (1) {case 1: x}")).isFalse();
    assertThat(executedOnceTestCase("switch (1) {default: x}")).isFalse();

    assertThat(executedOnceTestCase("if (1) { switch (x) {} }")).isFalse();

    assertThat(executedOnceTestCase("while (x) {}")).isFalse();
    assertThat(executedOnceTestCase("while (1) {x}")).isFalse();

    assertThat(executedOnceTestCase("do {} while (x)")).isFalse();
    assertThat(executedOnceTestCase("do {x} while (1)")).isFalse();

    assertThat(executedOnceTestCase("try {x} catch (e) {}")).isFalse();
    assertThat(executedOnceTestCase("try {} catch (e) {x}")).isFalse();
    assertThat(executedOnceTestCase("try {} finally {x}")).isTrue();

    assertThat(executedOnceTestCase("if (1) { try {} finally {x} }")).isFalse();
  }

  private void assertLValueNamedX(Node n) {
    assertThat(n.getString()).isEqualTo("x");
    assertThat(NodeUtil.isLValue(n)).isTrue();
  }

  @Test
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

  @Test
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

  @Test
  public void testIsConstantDeclaration() {
    assertIsConstantDeclaration(false, parse("var x = 1;").getFirstFirstChild());
    assertIsConstantDeclaration(false, parse("let x = 1;").getFirstFirstChild());
    assertIsConstantDeclaration(true, parse("const x = 1;").getFirstFirstChild());

    assertIsConstantDeclaration(true, parse("/** @const */ var x = 1;").getFirstFirstChild());
    assertIsConstantDeclaration(true, parse("var /** @const */ x = 1;").getFirstFirstChild());
    assertIsConstantDeclaration(false, parse("var x, /** @const */ y = 1;").getFirstFirstChild());

    assertIsConstantDeclaration(true, getNameNodeFrom("const [a] = [];", "a"));
    assertIsConstantDeclaration(true, getNameNodeFrom("const [[[a]]] = [];", "a"));
    assertIsConstantDeclaration(true, getNameNodeFrom("const [a = 1] = [];", "a"));
    assertIsConstantDeclaration(true, getNameNodeFrom("const [...a] = [];", "a"));

    assertIsConstantDeclaration(true, getNameNodeFrom("const {a} = {};", "a"));
    assertIsConstantDeclaration(true, getNameNodeFrom("const {a = 1} = {};", "a"));
    assertIsConstantDeclaration(true, getNameNodeFrom("const {[3]: a} = {};", "a"));
    assertIsConstantDeclaration(true, getNameNodeFrom("const {a: [a]} = {};", "a"));

    // TODO(bradfordcsmith): Add test cases for other coding conventions.
  }

  private void assertIsConstantDeclaration(boolean isConstantDeclaration, Node node) {
    CodingConvention codingConvention = new ClosureCodingConvention();
    JSDocInfo jsDocInfo = NodeUtil.getBestJSDocInfo(node);
    assertWithMessage("Is %s a constant declaration?", node)
        .that(NodeUtil.isConstantDeclaration(codingConvention, jsDocInfo, node))
        .isEqualTo(isConstantDeclaration);
  }

  @Test
  public void testIsNestedObjectPattern() {
    Node root = parse("var {a, b} = {a:1, b:2}");
    Node destructuring = root.getFirstFirstChild();
    Node objPattern = destructuring.getFirstChild();
    assertThat(NodeUtil.isNestedObjectPattern(objPattern)).isFalse();

    root = parse("var {a, b:{c}} = {a:{}, b:{c:5}};");
    destructuring = root.getFirstFirstChild();
    objPattern = destructuring.getFirstChild();
    assertThat(NodeUtil.isNestedObjectPattern(objPattern)).isTrue();
  }

  @Test
  public void testIsNestedArrayPattern() {
    Node root = parse("var [a, b] = [1, 2]");
    Node destructuring = root.getFirstFirstChild();
    Node arrayPattern = destructuring.getFirstChild();
    assertThat(NodeUtil.isNestedArrayPattern(arrayPattern)).isFalse();
  }

  @Test
  public void testDestructuring1() {
    Node root = parse("var [a, b] = obj;");
    Node varNode = root.getFirstChild();
    Node destructLhs = varNode.getFirstChild();
    checkState(destructLhs.isDestructuringLhs(), destructLhs);
    Node destructPat = destructLhs.getFirstChild();
    checkState(destructPat.isArrayPattern(), destructPat);

    Node nameNodeA = destructPat.getFirstChild();
    Node nameNodeB = nameNodeA.getNext();
    checkState(nameNodeA.getString().equals("a"), nameNodeA);
    checkState(nameNodeB.getString().equals("b"), nameNodeB);

    Node nameNodeObj = destructPat.getNext();
    checkState(nameNodeObj.getString().equals("obj"), nameNodeObj);

    assertLhsByDestructuring(nameNodeA);
    assertLhsByDestructuring(nameNodeB);
    assertNotLhsByDestructuring(nameNodeObj);

    assertThat(NodeUtil.getRootTarget(destructPat)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(nameNodeB)).isSameAs(destructPat);

    assertThat(NodeUtil.getDeclaringParent(nameNodeA)).isSameAs(varNode);
    assertThat(NodeUtil.getDeclaringParent(nameNodeB)).isSameAs(varNode);
  }

  @Test
  public void testDestructuring1b() {
    Node root = parse("var {a: c, b: d} = obj;");
    Node varNode = root.getFirstChild();
    Node destructLhs = varNode.getFirstChild();
    checkState(destructLhs.isDestructuringLhs(), destructLhs);
    Node destructPat = destructLhs.getFirstChild();
    checkState(destructPat.isObjectPattern(), destructPat);

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

    assertThat(NodeUtil.getRootTarget(destructPat)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(nameNodeC)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(nameNodeD)).isSameAs(destructPat);

    assertThat(NodeUtil.getDeclaringParent(nameNodeC)).isSameAs(varNode);
    assertThat(NodeUtil.getDeclaringParent(nameNodeD)).isSameAs(varNode);
  }

  @Test
  public void testDestructuring1c() {
    Node root = parse("var {a, b} = obj;");
    Node varNode = root.getFirstChild();
    Node destructLhs = varNode.getFirstChild();
    checkState(destructLhs.isDestructuringLhs(), destructLhs);
    Node destructPat = destructLhs.getFirstChild();
    checkState(destructPat.isObjectPattern(), destructPat);

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

    assertThat(NodeUtil.getRootTarget(destructPat)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(nameNodeB)).isSameAs(destructPat);

    assertThat(NodeUtil.getDeclaringParent(nameNodeA)).isSameAs(varNode);
    assertThat(NodeUtil.getDeclaringParent(nameNodeB)).isSameAs(varNode);
  }

  @Test
  public void testDestructuring1d() {
    Node root = parse("var {a = defaultValue} = obj;");
    Node varNode = root.getFirstChild();
    Node destructLhs = varNode.getFirstChild();
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

    assertThat(NodeUtil.getRootTarget(destructPat)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameAs(destructPat);

    assertThat(NodeUtil.getDeclaringParent(nameNodeA)).isSameAs(varNode);
  }

  @Test
  public void testDestructuring1e() {
    Node root = parse("var {a: b = defaultValue} = obj;");
    Node varNode = root.getFirstChild();
    Node destructLhs = varNode.getFirstChild();
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

    assertThat(NodeUtil.getRootTarget(destructPat)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(nameNodeB)).isSameAs(destructPat);

    assertThat(NodeUtil.getDeclaringParent(nameNodeB)).isSameAs(varNode);
  }

  @Test
  public void testDestructuring1f() {
    Node root = parse("var [a  = defaultValue] = arr;");
    Node varNode = root.getFirstChild();
    Node destructLhs = varNode.getFirstChild();
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

    assertThat(NodeUtil.getRootTarget(destructPat)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameAs(destructPat);

    assertThat(NodeUtil.getDeclaringParent(nameNodeA)).isSameAs(varNode);
  }

  @Test
  public void testDestructuring2() {
    Node root = parse("var [a, [b, c]] = obj;");
    Node varNode = root.getFirstChild();
    Node destructLhs = varNode.getFirstChild();
    checkState(destructLhs.isDestructuringLhs(), destructLhs);
    Node destructPat = destructLhs.getFirstChild();
    checkState(destructPat.isArrayPattern(), destructPat);

    Node nameNodeA = destructPat.getFirstChild();
    Node innerPat = nameNodeA.getNext();
    Node nameNodeB = innerPat.getFirstChild();
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

    assertThat(NodeUtil.getRootTarget(destructPat)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(innerPat)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(nameNodeB)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(nameNodeC)).isSameAs(destructPat);

    assertThat(NodeUtil.getDeclaringParent(nameNodeA)).isSameAs(varNode);
    assertThat(NodeUtil.getDeclaringParent(nameNodeB)).isSameAs(varNode);
    assertThat(NodeUtil.getDeclaringParent(nameNodeC)).isSameAs(varNode);
  }

  @Test
  public void testDestructuring2b() {
    Node root = parse("var {a: e, b: {c: f, d: g}} = obj;");
    Node varNode = root.getFirstChild();
    Node destructLhs = varNode.getFirstChild();
    checkState(destructLhs.isDestructuringLhs(), destructLhs);
    Node destructPat = destructLhs.getFirstChild();
    checkState(destructPat.isObjectPattern(), destructPat);

    Node strKeyNodeA = destructPat.getFirstChild();
    Node strKeyNodeB = strKeyNodeA.getNext();
    Node innerPat = strKeyNodeB.getOnlyChild();
    Node strKeyNodeC = innerPat.getFirstChild();
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

    assertThat(NodeUtil.getRootTarget(destructPat)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(nameNodeE)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(innerPat)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(nameNodeF)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(nameNodeG)).isSameAs(destructPat);

    assertThat(NodeUtil.getDeclaringParent(nameNodeE)).isSameAs(varNode);
    assertThat(NodeUtil.getDeclaringParent(nameNodeF)).isSameAs(varNode);
    assertThat(NodeUtil.getDeclaringParent(nameNodeG)).isSameAs(varNode);
  }

  @Test
  public void testDestructuring3() {
    Node root = parse("var [a, b] = [c, d];");
    Node varNode = root.getFirstChild();
    Node destructLhs = varNode.getFirstChild();
    checkState(destructLhs.isDestructuringLhs(), destructLhs);
    Node destructPat = destructLhs.getFirstChild();
    checkState(destructPat.isArrayPattern(), destructPat);

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

    assertThat(NodeUtil.getRootTarget(destructPat)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(nameNodeB)).isSameAs(destructPat);

    assertThat(NodeUtil.getDeclaringParent(nameNodeA)).isSameAs(varNode);
    assertThat(NodeUtil.getDeclaringParent(nameNodeB)).isSameAs(varNode);
  }

  @Test
  public void testDestructuring3b() {
    Node root = parse("var {a: c, b: d} = {a: 1, b: 2};");
    Node varNode = root.getFirstChild();
    Node destructLhs = varNode.getFirstChild();
    checkState(destructLhs.isDestructuringLhs(), destructLhs);
    Node destructPat = destructLhs.getFirstChild();
    checkState(destructPat.isObjectPattern(), destructPat);

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

    assertThat(NodeUtil.getRootTarget(destructPat)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(nameNodeC)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(nameNodeD)).isSameAs(destructPat);

    assertThat(NodeUtil.getDeclaringParent(nameNodeC)).isSameAs(varNode);
    assertThat(NodeUtil.getDeclaringParent(nameNodeD)).isSameAs(varNode);
  }

  @Test
  public void testDestructuring4() {
    Node root = parse("for ([a, b] of X){}");
    Node destructPat = root.getFirstFirstChild();
    checkState(destructPat.isArrayPattern(), destructPat);

    Node nameNodeA = destructPat.getFirstChild();
    Node nameNodeB = destructPat.getLastChild();
    checkState(nameNodeA.getString().equals("a"), nameNodeA);
    checkState(nameNodeB.getString().equals("b"), nameNodeB);

    assertLhsByDestructuring(nameNodeA);
    assertLhsByDestructuring(nameNodeB);

    assertThat(NodeUtil.getRootTarget(destructPat)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(nameNodeB)).isSameAs(destructPat);
  }

  @Test
  public void testDestructuring5() {
    Node root = parse("function fn([a, b] = [c, d]){}");
    Node destructPat = root.getFirstChild().getSecondChild()
        .getFirstFirstChild();
    checkState(destructPat.isArrayPattern(), destructPat);

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

    assertThat(NodeUtil.getRootTarget(destructPat)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(nameNodeB)).isSameAs(destructPat);
  }

  @Test
  public void testRestParameter() {
    Node root = parse("function fn(a, b, ...rest){}");
    Node parameterList = root.getFirstChild().getSecondChild();

    Node nameNodeA = parameterList.getFirstChild();
    Node nameNodeB = nameNodeA.getNext();
    Node restNode = nameNodeB.getNext();
    checkState(restNode.isRest(), restNode);

    Node nameNodeRest = restNode.getFirstChild();

    checkState(nameNodeA.getString().equals("a"), nameNodeA);

    assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameAs(nameNodeA);
    assertThat(NodeUtil.getRootTarget(nameNodeB)).isSameAs(nameNodeB);
    assertThat(NodeUtil.getRootTarget(nameNodeRest)).isSameAs(nameNodeRest);

    assertThat(NodeUtil.getDeclaringParent(nameNodeA)).isSameAs(parameterList);
    assertThat(NodeUtil.getDeclaringParent(nameNodeB)).isSameAs(parameterList);
    assertThat(NodeUtil.getDeclaringParent(nameNodeRest)).isSameAs(parameterList);
  }

  @Test
  public void testDestructuring6() {
    Node root = parse("for ([{a: b}] of c) {}");
    Node forOfNode = root.getFirstChild();
    Node forOfTargetPat = forOfNode.getFirstChild();
    Node innerPat = forOfTargetPat.getFirstChild();
    checkState(innerPat.isObjectPattern(), innerPat);
    checkState(forOfTargetPat.isArrayPattern(), forOfTargetPat);

    Node strKeyNodeA = innerPat.getFirstChild();
    Node nameNodeB = strKeyNodeA.getFirstChild();
    Node nameNodeC = innerPat.getParent().getNext();
    checkState(strKeyNodeA.getString().equals("a"), strKeyNodeA);
    checkState(nameNodeB.getString().equals("b"), nameNodeB);
    checkState(nameNodeC.getString().equals("c"), nameNodeC);

    assertNotLhsByDestructuring(strKeyNodeA);
    assertLhsByDestructuring(nameNodeB);
    assertNotLhsByDestructuring(nameNodeC);

    assertThat(NodeUtil.getRootTarget(forOfTargetPat)).isSameAs(forOfTargetPat);
    assertThat(NodeUtil.getRootTarget(innerPat)).isSameAs(forOfTargetPat);
    assertThat(NodeUtil.getRootTarget(nameNodeB)).isSameAs(forOfTargetPat);
  }

  @Test
  public void testDestructuring6b() {
    Node root = parse("for ([{a: b}] in c) {}");
    Node forInNode = root.getFirstChild();
    Node forInTargetPat = forInNode.getFirstChild();
    Node innerPat = forInTargetPat.getFirstChild();
    checkState(innerPat.isObjectPattern(), innerPat);
    checkState(forInTargetPat.isArrayPattern(), forInTargetPat);

    Node strKeyNodeA = innerPat.getFirstChild();
    Node nameNodeB = strKeyNodeA.getFirstChild();
    Node nameNodeC = innerPat.getParent().getNext();
    checkState(strKeyNodeA.getString().equals("a"), strKeyNodeA);
    checkState(nameNodeB.getString().equals("b"), nameNodeB);
    checkState(nameNodeC.getString().equals("c"), nameNodeC);

    assertNotLhsByDestructuring(strKeyNodeA);
    assertLhsByDestructuring(nameNodeB);
    assertNotLhsByDestructuring(nameNodeC);

    assertThat(NodeUtil.getRootTarget(forInTargetPat)).isSameAs(forInTargetPat);
    assertThat(NodeUtil.getRootTarget(innerPat)).isSameAs(forInTargetPat);
    assertThat(NodeUtil.getRootTarget(nameNodeB)).isSameAs(forInTargetPat);
  }

  @Test
  public void testDestructuring6c() {
    Node root = parse("for (var [{a: b}] = [{a: 1}];;) {}");
    Node varNode = root.getFirstFirstChild();
    Node destructArr = varNode.getFirstFirstChild();
    checkState(destructArr.isArrayPattern(), destructArr);
    Node destructPat = destructArr.getFirstChild();
    checkState(destructPat.isObjectPattern(), destructPat);

    Node strKeyNodeA = destructPat.getFirstChild();
    Node nameNodeB = strKeyNodeA.getFirstChild();
    checkState(strKeyNodeA.getString().equals("a"), strKeyNodeA);
    checkState(nameNodeB.getString().equals("b"), nameNodeB);

    assertNotLhsByDestructuring(strKeyNodeA);
    assertLhsByDestructuring(nameNodeB);

    assertThat(NodeUtil.getRootTarget(destructArr)).isSameAs(destructArr);
    assertThat(NodeUtil.getRootTarget(destructPat)).isSameAs(destructArr);
    assertThat(NodeUtil.getRootTarget(nameNodeB)).isSameAs(destructArr);

    assertThat(NodeUtil.getDeclaringParent(nameNodeB)).isSameAs(varNode);
  }

  @Test
  public void testDestructuring7() {
    Node root = parse("for ([a] of c) {}");
    Node destructPat = root.getFirstFirstChild();
    checkState(destructPat.isArrayPattern(), destructPat);

    Node nameNodeA = destructPat.getFirstChild();
    Node nameNodeC = destructPat.getNext();
    checkState(nameNodeA.getString().equals("a"), nameNodeA);
    checkState(nameNodeC.getString().equals("c"), nameNodeC);

    assertLhsByDestructuring(nameNodeA);
    assertNotLhsByDestructuring(nameNodeC);

    assertThat(NodeUtil.getRootTarget(destructPat)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameAs(destructPat);
  }

  @Test
  public void testDestructuring7b() {
    Node root = parse("for ([a] in c) {}");
    Node destructPat = root.getFirstFirstChild();
    checkState(destructPat.isArrayPattern(), destructPat);

    Node nameNodeA = destructPat.getFirstChild();
    Node nameNodeC = destructPat.getNext();
    checkState(nameNodeA.getString().equals("a"), nameNodeA);
    checkState(nameNodeC.getString().equals("c"), nameNodeC);

    assertLhsByDestructuring(nameNodeA);
    assertNotLhsByDestructuring(nameNodeC);

    assertThat(NodeUtil.getRootTarget(destructPat)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameAs(destructPat);
  }

  @Test
  public void testDestructuring7c() {
    Node root = parse("for (var [a] = [1];;) {}");
    Node varNode = root.getFirstFirstChild();
    Node destructLhs = varNode.getFirstChild();
    checkState(destructLhs.isDestructuringLhs(), destructLhs);
    Node destructPat = destructLhs.getFirstChild();
    checkState(destructPat.isArrayPattern(), destructPat);

    Node nameNodeA = destructPat.getFirstChild();
    checkState(nameNodeA.getString().equals("a"), nameNodeA);

    assertLhsByDestructuring(nameNodeA);

    assertThat(NodeUtil.getRootTarget(destructPat)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameAs(destructPat);

    assertThat(NodeUtil.getDeclaringParent(nameNodeA)).isSameAs(varNode);
  }

  @Test
  public void testDestructuring7d() {
    Node root = parse("for (let [a] = [1];;) {}");
    Node letNode = root.getFirstFirstChild();
    Node destructLhs = letNode.getFirstChild();
    checkState(destructLhs.isDestructuringLhs(), destructLhs);
    Node destructPat = destructLhs.getFirstChild();
    checkState(destructPat.isArrayPattern(), destructPat);

    Node nameNodeA = destructPat.getFirstChild();
    checkState(nameNodeA.getString().equals("a"), nameNodeA);

    assertLhsByDestructuring(nameNodeA);

    assertThat(NodeUtil.getRootTarget(destructPat)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameAs(destructPat);

    assertThat(NodeUtil.getDeclaringParent(nameNodeA)).isSameAs(letNode);
  }

  @Test
  public void testDestructuring7e() {
    Node root = parse("for (const [a] = [1];;) {}");
    Node constNode = root.getFirstFirstChild();
    Node destructLhs = constNode.getFirstChild();
    checkState(destructLhs.isDestructuringLhs(), destructLhs);
    Node destructPat = destructLhs.getFirstChild();
    checkState(destructPat.isArrayPattern(), destructPat);

    Node nameNodeA = destructPat.getFirstChild();
    checkState(nameNodeA.getString().equals("a"), nameNodeA);

    assertLhsByDestructuring(nameNodeA);

    assertThat(NodeUtil.getRootTarget(destructPat)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameAs(destructPat);

    assertThat(NodeUtil.getDeclaringParent(nameNodeA)).isSameAs(constNode);
  }

  @Test
  public void testDestructuring8() {
    Node root = parse("var [...a] = obj;");
    Node varNode = root.getFirstChild();
    Node destructLhs = varNode.getFirstChild();
    checkState(destructLhs.isDestructuringLhs(), destructLhs);
    Node destructPat = destructLhs.getFirstChild();
    checkState(destructPat.isArrayPattern(), destructPat);
    Node restNode = destructPat.getFirstChild();
    checkState(restNode.isRest(), restNode);

    Node nameNodeA = restNode.getFirstChild();
    checkState(nameNodeA.getString().equals("a"), nameNodeA);

    assertLhsByDestructuring(nameNodeA);

    assertThat(NodeUtil.getRootTarget(destructPat)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameAs(destructPat);

    assertThat(NodeUtil.getDeclaringParent(nameNodeA)).isSameAs(varNode);
  }

  @Test
  public void testDestructuring8b() {
    Node root = parse("([...this.x] = obj);");
    Node assign = root.getFirstFirstChild();
    checkState(assign.isAssign(), assign);
    Node destructPat = assign.getFirstChild();
    checkState(destructPat.isArrayPattern(), destructPat);
    Node restNode = destructPat.getFirstChild();
    checkState(restNode.isRest(), restNode);
    Node getProp = restNode.getFirstChild();
    checkState(getProp.isGetProp(), getProp);

    assertLhsByDestructuring(getProp);

    assertThat(NodeUtil.getRootTarget(destructPat)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(getProp)).isSameAs(destructPat);
  }

  @Test
  public void testDestructuring9() {
    Node root = parse("var {['a']:a} = obj;");
    Node varNode = root.getFirstChild();
    Node destructLhs = varNode.getFirstChild();
    checkState(destructLhs.isDestructuringLhs(), destructLhs);
    Node destructPat = destructLhs.getFirstChild();
    checkState(destructPat.isObjectPattern(), destructPat);
    Node computedPropNode = destructPat.getFirstChild();
    checkState(computedPropNode.isComputedProp(), computedPropNode);

    Node nameNodeA = computedPropNode.getLastChild();
    checkState(nameNodeA.getString().equals("a"), nameNodeA);

    assertLhsByDestructuring(nameNodeA);

    assertThat(NodeUtil.getRootTarget(destructPat)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameAs(destructPat);

    assertThat(NodeUtil.getDeclaringParent(nameNodeA)).isSameAs(varNode);
  }

  @Test
  public void testDestructuringComputedPropertyWithDefault() {
    Node root = parse("var {['a']: a = 1} = obj;");
    Node varNode = root.getFirstChild();
    Node destructLhs = varNode.getFirstChild();
    checkState(destructLhs.isDestructuringLhs(), destructLhs);
    Node destructPat = destructLhs.getFirstChild();
    checkState(destructPat.isObjectPattern(), destructPat);
    Node computedPropNode = destructPat.getFirstChild();
    checkState(computedPropNode.isComputedProp(), computedPropNode);

    Node defaultValueNode = computedPropNode.getLastChild();
    Node nameNodeA = defaultValueNode.getFirstChild();
    checkState(nameNodeA.getString().equals("a"), nameNodeA);

    assertLhsByDestructuring(nameNodeA);

    assertThat(NodeUtil.getRootTarget(destructPat)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameAs(destructPat);

    assertThat(NodeUtil.getDeclaringParent(nameNodeA)).isSameAs(varNode);
  }

  @Test
  public void testDestructuringWithArrayPatternInCatch() {
    Node actual = parse("try {} catch([a]) {}");

    Node tryNode = actual.getFirstChild();
    Node catchBlock = tryNode.getSecondChild();
    Node catchNode = catchBlock.getFirstChild();
    assertNode(catchNode).hasType(Token.CATCH);

    Node arrayPattern = catchNode.getFirstChild();
    Node nameNodeA = arrayPattern.getOnlyChild();
    assertNode(nameNodeA).isName("a");

    assertLhsByDestructuring(nameNodeA);

    assertThat(NodeUtil.getRootTarget(arrayPattern)).isSameAs(arrayPattern);
    assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameAs(arrayPattern);

    assertThat(NodeUtil.getDeclaringParent(nameNodeA)).isSameAs(catchNode);
  }

  @Test
  public void testDestructuringObjectRest() {
    Node root = parse("var {...rest} = obj;");
    Node varNode = root.getFirstChild();
    Node destructLhs = varNode.getFirstChild();
    checkState(destructLhs.isDestructuringLhs(), destructLhs);
    Node destructPat = destructLhs.getFirstChild();
    checkState(destructPat.isObjectPattern(), destructPat);

    Node restNode = destructPat.getFirstChild();
    Node nameNodeRest = restNode.getFirstChild();
    checkState(nameNodeRest.getString().equals("rest"), nameNodeRest);

    Node nameNodeObj = destructPat.getNext();
    checkState(nameNodeObj.getString().equals("obj"), nameNodeObj);

    assertNotLhsByDestructuring(restNode);
    assertLhsByDestructuring(nameNodeRest);
    assertNotLhsByDestructuring(nameNodeObj);

    assertThat(NodeUtil.getRootTarget(destructPat)).isSameAs(destructPat);
    assertThat(NodeUtil.getRootTarget(nameNodeRest)).isSameAs(destructPat);

    assertThat(NodeUtil.getDeclaringParent(nameNodeRest)).isSameAs(varNode);
  }

  private static void assertLhsByDestructuring(Node n) {
    assertThat(NodeUtil.isLhsByDestructuring(n)).isTrue();
  }

  private static void assertNotLhsByDestructuring(Node n) {
    assertThat(NodeUtil.isLhsByDestructuring(n)).isFalse();
  }

  @Test
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

  @Test
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

  @Test
  public void testNewQNameDeclarationWithQualifiedName() {
    assertNode(createNewQNameDeclaration("ns.prop", IR.number(0), Token.VAR))
        .isEqualTo(
            IR.exprResult(IR.assign(IR.getprop(IR.name("ns"), IR.string("prop")), IR.number(0))));
  }

  @Test
  public void testNewQNameDeclarationWithVar() {
    assertNode(createNewQNameDeclaration("x", IR.number(0), Token.VAR))
        .isEqualTo(IR.var(IR.name("x"), IR.number(0)));
  }

  @Test
  public void testNewQNameDeclarationWithLet() {
    assertNode(createNewQNameDeclaration("x", IR.number(0), Token.LET))
        .isEqualTo(IR.let(IR.name("x"), IR.number(0)));
  }

  @Test
  public void testNewQNameDeclarationWithConst() {
    assertNode(createNewQNameDeclaration("x", IR.number(0), Token.CONST))
        .isEqualTo(IR.constNode(IR.name("x"), IR.number(0)));
  }

  private Node createNewQNameDeclaration(String name, Node value, Token type) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setCodingConvention(new GoogleCodingConvention());
    compiler.init(ImmutableList.<SourceFile>of(), ImmutableList.<SourceFile>of(), options);
    return NodeUtil.newQNameDeclaration(compiler, name, value, null, type);
  }

  @Test
  public void testGetBestJsDocInfoForClasses() {
    Node classNode = getClassNode("/** @export */ class Foo {}");
    assertThat(NodeUtil.getBestJSDocInfo(classNode).isExport()).isTrue();

    classNode = getClassNode("/** @export */ var Foo = class {}");
    assertThat(NodeUtil.getBestJSDocInfo(classNode).isExport()).isTrue();

    classNode = getClassNode("/** @export */ var Foo = class Bar {}");
    assertThat(NodeUtil.getBestJSDocInfo(classNode).isExport()).isTrue();
  }

  @Test
  public void testGetBestJsDocInfoForMethods() {
    Node function = getFunctionNode("class C { /** @export */ foo() {} }");
    assertThat(NodeUtil.getBestJSDocInfo(function).isExport()).isTrue();

    function = getFunctionNode("class C { /** @export */ [computedMethod]() {} }");
    assertThat(NodeUtil.getBestJSDocInfo(function).isExport()).isTrue();
  }

  @Test
  public void testGetBestJsDocInfoExport() {
    Node classNode = getClassNode("/** @constructor */ export class Foo {}");
    assertThat(NodeUtil.getBestJSDocInfo(classNode).isConstructor()).isTrue();

    Node function = getFunctionNode("/** @constructor */ export function Foo() {}");
    assertThat(NodeUtil.getBestJSDocInfo(function).isConstructor()).isTrue();

    function = getFunctionNode("/** @constructor */ export var Foo = function() {}");
    assertThat(NodeUtil.getBestJSDocInfo(function).isConstructor()).isTrue();

    function = getFunctionNode("/** @constructor */ export let Foo = function() {}");
    assertThat(NodeUtil.getBestJSDocInfo(function).isConstructor()).isTrue();
  }

  @Test
  public void testGetDeclaredTypeExpression1() {
    Node ast = parse("function f(/** string */ x) {}");
    Node x = getNameNode(ast, "x");
    JSTypeExpression typeExpr = NodeUtil.getDeclaredTypeExpression(x);
    assertThat(typeExpr.getRoot().getString()).isEqualTo("string");
  }

  @Test
  public void testGetDeclaredTypeExpression2() {
    Node ast = parse("/** @param {string} x */ function f(x) {}");
    Node x = getNameNode(ast, "x");
    JSTypeExpression typeExpr = NodeUtil.getDeclaredTypeExpression(x);
    assertThat(typeExpr.getRoot().getString()).isEqualTo("string");
  }

  @Test
  public void testGetDeclaredTypeExpression3() {
    Node ast = parse("/** @param {...number} x */ function f(...x) {}");
    Node x = getNameNode(ast, "x");
    JSTypeExpression typeExpr = NodeUtil.getDeclaredTypeExpression(x);
    assertNode(typeExpr.getRoot()).hasType(Token.ELLIPSIS);
    assertThat(typeExpr.getRoot().getFirstChild().getString()).isEqualTo("number");
  }

  @Test
  public void testGetDeclaredTypeExpression4() {
    Node ast = parse("/** @param {number=} x */ function f(x = -1) {}");
    Node x = getNameNode(ast, "x");
    JSTypeExpression typeExpr = NodeUtil.getDeclaredTypeExpression(x);
    assertNode(typeExpr.getRoot()).hasType(Token.EQUALS);
    assertThat(typeExpr.getRoot().getFirstChild().getString()).isEqualTo("number");
  }

  @Test
  public void testFindLhsNodesInNodeWithNameDeclaration() {
    assertThat(findLhsNodesInNode("var x;")).hasSize(1);
    assertThat(findLhsNodesInNode("var x, y;")).hasSize(2);
    assertThat(findLhsNodesInNode("var f = function(x, y, z) {};")).hasSize(1);
  }

  @Test
  public void testFindLhsNodesInNodeWithArrayPatternDeclaration() {
    assertThat(findLhsNodesInNode("var [x=a => a, y = b=>b+1] = arr;")).hasSize(2);
    assertThat(findLhsNodesInNode("var [x=a => a, y = b=>b+1, ...z] = arr;")).hasSize(3);
    assertThat(findLhsNodesInNode("var [ , , , y = b=>b+1, ...z] = arr;")).hasSize(2);
  }

  @Test
  public void testFindLhsNodesInNodeWithObjectPatternDeclaration() {
    assertThat(findLhsNodesInNode("var {x = a=>a, y = b=>b+1} = obj;")).hasSize(2);
    assertThat(findLhsNodesInNode("var {p1: x = a=>a, p2: y = b=>b+1} = obj;")).hasSize(2);
    assertThat(findLhsNodesInNode("var {[pname]: x = a=>a, [p2name]: y} = obj;")).hasSize(2);
    assertThat(findLhsNodesInNode("var {lhs1 = a, p2: [lhs2, lhs3 = b] = [notlhs]} = obj;"))
        .hasSize(3);
  }

  @Test
  public void testFindLhsNodesInNodeWithCastOnLhs() {
    Iterable<Node> lhsNodes = findLhsNodesInNode("/** @type {*} */ (a.b) = 3;");
    assertThat(lhsNodes).hasSize(1);
    Iterator<Node> nodeIterator = lhsNodes.iterator();
    assertNode(nodeIterator.next()).matchesQualifiedName("a.b");
  }

  @Test
  public void testFindLhsNodesInNodeWithArrayPatternAssign() {
    assertThat(findLhsNodesInNode("[this.x] = rhs;")).hasSize(1);
    assertThat(findLhsNodesInNode("[this.x, y] = rhs;")).hasSize(2);
    assertThat(findLhsNodesInNode("[this.x, y, this.z] = rhs;")).hasSize(3);
    assertThat(findLhsNodesInNode("[y, this.z] = rhs;")).hasSize(2);
    assertThat(findLhsNodesInNode("[x[y]] = rhs;")).hasSize(1);
    assertThat(findLhsNodesInNode("[x.y.z] = rhs;")).hasSize(1);
    assertThat(findLhsNodesInNode("[ /** @type {*} */ (x.y.z) ] = rhs;")).hasSize(1);
  }

  @Test
  public void testFindLhsNodesInNodeWithComplexAssign() {
    assertThat(findLhsNodesInNode("x += 1;")).hasSize(1);
    assertThat(findLhsNodesInNode("x.y += 1;")).hasSize(1);
    assertThat(findLhsNodesInNode("x -= 1;")).hasSize(1);
    assertThat(findLhsNodesInNode("x.y -= 1;")).hasSize(1);
    assertThat(findLhsNodesInNode("x *= 2;")).hasSize(1);
    assertThat(findLhsNodesInNode("x.y *= 2;")).hasSize(1);
  }

  @Test
  public void testFindLhsNodesInForOfWithDeclaration() {
    Iterable<Node> lhsNodes = findLhsNodesInNode("for (const {x, y} of iterable) {}");
    assertThat(lhsNodes).hasSize(2);
    Iterator<Node> nodeIterator = lhsNodes.iterator();
    assertNode(nodeIterator.next()).isName("x");
    assertNode(nodeIterator.next()).isName("y");
  }

  @Test
  public void testFindLhsNodesInForOfWithoutDeclaration() {
    Iterable<Node> lhsNodes = findLhsNodesInNode("for ({x, y: a.b} of iterable) {}");
    assertThat(lhsNodes).hasSize(2);
    Iterator<Node> nodeIterator = lhsNodes.iterator();
    assertNode(nodeIterator.next()).isName("x");
    assertNode(nodeIterator.next()).matchesQualifiedName("a.b");
  }

  @Test
  public void testFindLhsNodesInForInWithDeclaration() {
    Iterable<Node> lhsNodes = findLhsNodesInNode("for (const x in obj) {}");
    assertThat(lhsNodes).hasSize(1);
    Iterator<Node> nodeIterator = lhsNodes.iterator();
    assertNode(nodeIterator.next()).isName("x");
  }

  @Test
  public void testFindLhsNodesInForInWithoutDeclaration() {
    Iterable<Node> lhsNodes = findLhsNodesInNode("for (a.b in iterable) {}");
    assertThat(lhsNodes).hasSize(1);
    Iterator<Node> nodeIterator = lhsNodes.iterator();
    assertNode(nodeIterator.next()).matchesQualifiedName("a.b");
  }

  @Test
  public void testIsConstructor() {
    assertThat(NodeUtil.isConstructor(getFunctionNode("/** @constructor */ function Foo() {}")))
        .isTrue();
    assertThat(
            NodeUtil.isConstructor(getFunctionNode("/** @constructor */ var Foo = function() {}")))
        .isTrue();
    assertThat(
            NodeUtil.isConstructor(
                getFunctionNode("var x = {}; /** @constructor */ x.Foo = function() {}")))
        .isTrue();
    assertThat(NodeUtil.isConstructor(getFunctionNode("class Foo { constructor() {} }"))).isTrue();

    assertThat(NodeUtil.isConstructor(getFunctionNode("function Foo() {}"))).isFalse();
    assertThat(NodeUtil.isConstructor(getFunctionNode("var Foo = function() {}"))).isFalse();
    assertThat(NodeUtil.isConstructor(getFunctionNode("var x = {}; x.Foo = function() {};")))
        .isFalse();
    assertThat(NodeUtil.isConstructor(getFunctionNode("function constructor() {}"))).isFalse();
    assertThat(NodeUtil.isConstructor(getFunctionNode("class Foo { bar() {} }"))).isFalse();
  }

  @Test
  public void testIsGetterOrSetter() {
    Node fnNode = getFunctionNode("Object.defineProperty(this, 'bar', {get: function() {}});");
    assertThat(NodeUtil.isGetterOrSetter(fnNode.getParent())).isTrue();

    fnNode = getFunctionNode("Object.defineProperty(this, 'bar', {set: function() {}});");
    assertThat(NodeUtil.isGetterOrSetter(fnNode.getParent())).isTrue();

    fnNode = getFunctionNode("Object.defineProperties(this, {bar: {get: function() {}}});");
    assertThat(NodeUtil.isGetterOrSetter(fnNode.getParent())).isTrue();

    fnNode = getFunctionNode("Object.defineProperties(this, {bar: {set: function() {}}});");
    assertThat(NodeUtil.isGetterOrSetter(fnNode.getParent())).isTrue();

    fnNode = getFunctionNode("var x = {get bar() {}};");
    assertThat(NodeUtil.isGetterOrSetter(fnNode.getParent())).isTrue();

    fnNode = getFunctionNode("var x = {set bar(z) {}};");
    assertThat(NodeUtil.isGetterOrSetter(fnNode.getParent())).isTrue();
  }

  @Test
  public void testIsObjectDefinePropertiesDefinition() {
    assertThat(
            NodeUtil.isObjectDefinePropertiesDefinition(
                getCallNode("Object.defineProperties(this, {});")))
        .isTrue();
    assertThat(
            NodeUtil.isObjectDefinePropertiesDefinition(
                getCallNode("Object.defineProperties(this, foo);")))
        .isTrue();
    assertThat(
            NodeUtil.isObjectDefinePropertiesDefinition(
                getCallNode("$jscomp.global.Object.defineProperties(this, foo);")))
        .isTrue();
    assertThat(
            NodeUtil.isObjectDefinePropertiesDefinition(
                getCallNode("$jscomp$global.Object.defineProperties(this, foo);")))
        .isTrue();

    assertThat(
            NodeUtil.isObjectDefinePropertiesDefinition(
                getCallNode("Object.defineProperties(this, {}, foo);")))
        .isFalse();
    assertThat(
            NodeUtil.isObjectDefinePropertiesDefinition(
                getCallNode("Object.defineProperties(this);")))
        .isFalse();
    assertThat(
            NodeUtil.isObjectDefinePropertiesDefinition(getCallNode("Object.defineProperties();")))
        .isFalse();
  }

  @Test
  public void testIsObjectDefinePropertyDefinition() {
    assertThat(
            NodeUtil.isObjectDefinePropertyDefinition(
                getCallNode("Object.defineProperty(this, 'foo', {});")))
        .isTrue();
    assertThat(
            NodeUtil.isObjectDefinePropertyDefinition(
                getCallNode("Object.defineProperty(this, 'foo', foo);")))
        .isTrue();
    assertThat(
            NodeUtil.isObjectDefinePropertyDefinition(
                getCallNode("$jscomp.global.Object.defineProperty(this, 'foo', foo);")))
        .isTrue();
    assertThat(
            NodeUtil.isObjectDefinePropertyDefinition(
                getCallNode("$jscomp$global.Object.defineProperty(this, 'foo', foo);")))
        .isTrue();

    assertThat(
            NodeUtil.isObjectDefinePropertyDefinition(
                getCallNode("Object.defineProperty(this, {});")))
        .isFalse();
    assertThat(
            NodeUtil.isObjectDefinePropertyDefinition(getCallNode("Object.defineProperty(this);")))
        .isFalse();
    assertThat(NodeUtil.isObjectDefinePropertyDefinition(getCallNode("Object.defineProperty();")))
        .isFalse();
  }

  @Test
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

    assertThat(allVariables.keySet()).containsExactly("a", "b", "c", "z", "x", "y");
  }

  @Test
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

    assertThat(allVariables.keySet()).containsExactly("x", "y", "z", "a", "b", "c");
  }

  @Test
  public void testIsVarArgs() {
    assertThat(
            NodeUtil.doesFunctionReferenceOwnArgumentsObject(
                getNode("function() {return () => arguments}")))
        .isTrue();
    assertThat(NodeUtil.doesFunctionReferenceOwnArgumentsObject(getNode("() => arguments")))
        .isFalse();
  }

  /**
   * When the left side is a destructuring pattern, generally it's not possible to identify the RHS
   * for a specific name on the LHS.
   */
  @Test
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

    assertThat(NodeUtil.isExpressionResultUsed(getNameNodeFrom("function f() { return y }", "y")))
        .isTrue();
    assertThat(NodeUtil.isExpressionResultUsed(getNameNodeFrom("function f() { y }", "y")))
        .isFalse();

    assertThat(NodeUtil.isExpressionResultUsed(getNameNodeFrom("var x = ()=> y", "y"))).isTrue();
    assertThat(NodeUtil.isExpressionResultUsed(getNameNodeFrom("var x = ()=>{ y }", "y")))
        .isFalse();

    assertThat(NodeUtil.isExpressionResultUsed(getNameNodeFrom("({a: x = y} = z)", "y"))).isTrue();
    assertThat(NodeUtil.isExpressionResultUsed(getNameNodeFrom("[x = y] = z", "y"))).isTrue();

    assertThat(NodeUtil.isExpressionResultUsed(getNameNodeFrom("({x: y})", "y"))).isTrue();
    assertThat(NodeUtil.isExpressionResultUsed(getNameNodeFrom("[y]", "y"))).isTrue();

    assertThat(NodeUtil.isExpressionResultUsed(getNameNodeFrom("y()", "y"))).isTrue();
    assertThat(NodeUtil.isExpressionResultUsed(getNameNodeFrom("y``", "y"))).isTrue();
  }

  @Test
  public void testIsSimpleOperator() {
    assertThat(NodeUtil.isSimpleOperator(parseExpr("!x"))).isTrue();
    assertThat(NodeUtil.isSimpleOperator(parseExpr("5 + x"))).isTrue();
    assertThat(NodeUtil.isSimpleOperator(parseExpr("typeof x"))).isTrue();
    assertThat(NodeUtil.isSimpleOperator(parseExpr("x instanceof y"))).isTrue();
    // short curcuits aren't simple
    assertThat(NodeUtil.isSimpleOperator(parseExpr("5 && x"))).isFalse();
    assertThat(NodeUtil.isSimpleOperator(parseExpr("5 || x"))).isFalse();
    // side-effects aren't simple
    assertThat(NodeUtil.isSimpleOperator(parseExpr("x = 5"))).isFalse();
    assertThat(NodeUtil.isSimpleOperator(parseExpr("x++"))).isFalse();
    assertThat(NodeUtil.isSimpleOperator(parseExpr("--y"))).isFalse();
    // prop access are simple
    assertThat(NodeUtil.isSimpleOperator(parseExpr("y in x"))).isTrue();
    assertThat(NodeUtil.isSimpleOperator(parseExpr("x.y"))).isTrue();
    assertThat(NodeUtil.isSimpleOperator(parseExpr("x[y]"))).isTrue();
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
    assertWithMessage("No function node to test").that(funcNode).isNotNull();
    return funcNode == NodeUtil.getRValueOfLValue(nameNode);
  }

  private void assertNodeTreesEqual(
      Node expected, Node actual) {
    String error = expected.checkTreeEquals(actual);
    assertThat(error).isNull();
  }

  private static void testFunctionName(String js, String expected) {
    assertThat(NodeUtil.getNearestFunctionName(getFunctionNode(js))).isEqualTo(expected);
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
