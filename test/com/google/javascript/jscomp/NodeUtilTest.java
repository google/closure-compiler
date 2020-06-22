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
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.DiagnosticGroups.ES5_STRICT;
import static com.google.javascript.jscomp.parsing.parser.testing.FeatureSetSubject.assertFS;
import static com.google.javascript.rhino.Token.AWAIT;
import static com.google.javascript.rhino.Token.CALL;
import static com.google.javascript.rhino.Token.CLASS;
import static com.google.javascript.rhino.Token.DESTRUCTURING_LHS;
import static com.google.javascript.rhino.Token.FOR_AWAIT_OF;
import static com.google.javascript.rhino.Token.FOR_OF;
import static com.google.javascript.rhino.Token.FUNCTION;
import static com.google.javascript.rhino.Token.GETTER_DEF;
import static com.google.javascript.rhino.Token.ITER_REST;
import static com.google.javascript.rhino.Token.ITER_SPREAD;
import static com.google.javascript.rhino.Token.MEMBER_FUNCTION_DEF;
import static com.google.javascript.rhino.Token.MODULE_BODY;
import static com.google.javascript.rhino.Token.OPTCHAIN_CALL;
import static com.google.javascript.rhino.Token.OPTCHAIN_GETELEM;
import static com.google.javascript.rhino.Token.OPTCHAIN_GETPROP;
import static com.google.javascript.rhino.Token.SCRIPT;
import static com.google.javascript.rhino.Token.SETTER_DEF;
import static com.google.javascript.rhino.Token.SUPER;
import static com.google.javascript.rhino.Token.YIELD;
import static com.google.javascript.rhino.testing.Asserts.assertThrows;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;
import static org.mockito.Mockito.verify;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.NodeUtil.GoogRequire;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.jscomp.parsing.parser.util.format.SimpleFormat;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * Tests for NodeUtil.
 *
 * <p>IMPORTANT: Do not put {@code {@literal @}Test} methods directly in this class, they must be
 * inside an inner class or they won't be executed, because we're using the {@code Enclosed} JUnit
 * runner.
 */
@RunWith(Enclosed.class)
@SuppressWarnings("RhinoNodeGetFirstFirstChild")
public final class NodeUtilTest {

  /** Provides methods for parsing and accessing the compiler used for the parsing. */
  private static class ParseHelper {
    private Compiler compiler = null;

    private Node parse(String js) {
      CompilerOptions options = new CompilerOptions();
      options.setLanguageIn(LanguageMode.UNSUPPORTED);

      // To allow octal literals such as 0123 to be parsed.
      options.setStrictModeInput(false);
      options.setWarningLevel(ES5_STRICT, CheckLevel.OFF);

      compiler = new Compiler();
      compiler.initOptions(options);
      Node n = compiler.parseTestCode(js);
      assertThat(compiler.getErrors()).isEmpty();
      return n;
    }

    private Node parseFirst(Token token, String js) {
      Node rootNode = this.parse(js);
      checkState(rootNode.isScript(), rootNode);
      return token.equals(SCRIPT) ? rootNode : getNode(rootNode, token);
    }
  }

  private static Node parse(String js) {
    return new ParseHelper().parse(js);
  }

  /**
   * Parses {@code js} into an AST and then returns the first Node in pre-traversal order with the
   * given token.
   *
   * <p>TODO(nickreid): Consider an overload that takes a `Predicate` rather than a `Token`.
   */
  private static Node parseFirst(Token token, String js) {
    return new ParseHelper().parseFirst(token, js);
  }

  /** Returns the parsed expression (e.g. returns a NAME given 'a') */
  private static Node parseExpr(String js) {
    Node script = parse("(" + js + ");"); // Parens force interpretation as an expression.
    return script
        .getFirstChild() // EXPR_RESULT
        .getFirstChild(); // expr
  }

  /**
   * Performs a DFS over {@code root} searching for a descendant matching {@code token}.
   *
   * <p>The search doesn't include {@code root}.
   *
   * @return the first matching node
   * @throws AssertionError if no matching node was found.
   */
  // TODO(nickreid): Consider an overload that takes a `Predicate` rather than a `Token`.
  private static Node getNode(Node root, Token token) {
    @Nullable Node result = getNodeOrNull(root, token);
    if (result == null) {
      throw new AssertionError("No " + token + " node found in:\n " + root.toStringTree());
    }
    return result;
  }

  /**
   * Performs a DFS below {@code root} searching for a descendant matching {@code token}.
   *
   * <p>The search doesn't include {@code root}.
   *
   * @return the first matching node, or {@code null} if none match.
   */
  // TODO(nickreid): Consider an overload that takes a `Predicate` rather than a `Token`.
  @Nullable
  private static Node getNodeOrNull(Node root, Token token) {
    for (Node n : root.children()) {
      if (n.getToken() == token) {
        return n;
      }
      Node potentialMatch = getNodeOrNull(n, token);
      if (potentialMatch != null) {
        return potentialMatch;
      }
    }
    return null;
  }

  @RunWith(JUnit4.class)
  public static final class IsDefinedValueTests {
    @Test
    public void testIsDefinedValue() {
      // while "null" is "defined" for the purposes of this method, it triggers the RHS.
      assertThat(NodeUtil.isDefinedValue(parseExpr("null ?? undefined"))).isFalse();
      assertThat(NodeUtil.isDefinedValue(parseExpr("null ?? null"))).isTrue();
      assertThat(NodeUtil.isDefinedValue(parseExpr("undefined ?? undefined"))).isFalse();
      assertThat(NodeUtil.isDefinedValue(parseExpr("undefined ?? null"))).isTrue();

      // could be true but the logic is not refined enough
      assertThat(NodeUtil.isDefinedValue(parseExpr("0 ?? undefined"))).isFalse();
    }

    @Test
    public void isDefinedValueOptionalChain() {
      assertThat(NodeUtil.isDefinedValue(parseExpr("x?.y"))).isFalse();
      assertThat(NodeUtil.isDefinedValue(parseExpr("x?.[y]"))).isFalse();
      assertThat(NodeUtil.isDefinedValue(parseExpr("x?.()"))).isFalse();
    }
  }

  /**
   * Test the forms of getting a boolean value for a node.
   *
   * <p>The 3 forms differ based on when they decide the value is unknown. See more description on
   * the method definitions.
   *
   * <dl>
   *   <dt>pure
   *   <dd>known only for side-effect free literal(ish) values
   *   <dt>literal
   *   <dd>known for literal(ish) values regardless of side-effects
   *   <dt>impure
   *   <dd>known for literal(ish) values and some simple expressions regardless of side-effects
   * </dl>
   */
  @RunWith(Parameterized.class)
  public static final class BooleanValueTests {

    /** Snippet of JS that will be parsed as an expression. */
    @Parameter(0)
    public String jsExpression;

    /** Expected result of NodeUtil.getBooleanValue() */
    @Parameter(1)
    public TernaryValue expectedResult;

    @Parameters(name = "getBooleanValue(\"{0}\") => {1}")
    public static Iterable<Object[]> cases() {
      return ImmutableList.copyOf(
          new Object[][] {
            // truly literal, side-effect free values are always known
            {"true", TernaryValue.TRUE},
            {"10", TernaryValue.TRUE},
            {"1n", TernaryValue.TRUE},
            {"'0'", TernaryValue.TRUE},
            {"/a/", TernaryValue.TRUE},
            {"{}", TernaryValue.TRUE},
            {"[]", TernaryValue.TRUE},
            {"false", TernaryValue.FALSE},
            {"null", TernaryValue.FALSE},
            {"0", TernaryValue.FALSE},
            {"0n", TernaryValue.FALSE},
            {"''", TernaryValue.FALSE},
            {"undefined", TernaryValue.FALSE},

            // literals that have side-effects aren't pure
            {"{a:foo()}", TernaryValue.TRUE},
            {"[foo()]", TernaryValue.TRUE},

            // not really literals, but we pretend they are for our purposes
            {"void 0", TernaryValue.FALSE},
            // side-effect keeps this one from being pure
            {"void foo()", TernaryValue.FALSE},
            {"!true", TernaryValue.FALSE},
            {"!false", TernaryValue.TRUE},
            {"!''", TernaryValue.TRUE},
            {"class Klass {}", TernaryValue.TRUE},
            {"new Date()", TernaryValue.TRUE},
            {"b", TernaryValue.UNKNOWN},
            {"-'0.0'", TernaryValue.UNKNOWN},

            // template literals
            {"``", TernaryValue.FALSE},
            {"`definiteLength`", TernaryValue.TRUE},
            {"`${some}str`", TernaryValue.UNKNOWN},

            // non-literal expressions
            {"a=true", TernaryValue.TRUE},
            {"a=false", TernaryValue.FALSE},
            {"a=(false,true)", TernaryValue.TRUE},
            {"a=(true,false)", TernaryValue.FALSE},
            {"a=(false || true)", TernaryValue.TRUE},
            {"a=(true && false)", TernaryValue.FALSE},
            {"a=!(true && false)", TernaryValue.TRUE},
            {"a,true", TernaryValue.TRUE},
            {"a,false", TernaryValue.FALSE},
            {"true||false", TernaryValue.TRUE},
            {"false||false", TernaryValue.FALSE},
            {"true&&true", TernaryValue.TRUE},
            {"true&&false", TernaryValue.FALSE},

            // Assignment ops other than ASSIGN are unknown.
            {"a *= 2", TernaryValue.UNKNOWN},

            // Complex expressions that contain anything other then "=", ",", or "!" are
            // unknown.
            {"2 + 2", TernaryValue.UNKNOWN},

            // assignment values are the RHS
            {"a=1", TernaryValue.TRUE},
            {"a=/a/", TernaryValue.TRUE},
            {"a={}", TernaryValue.TRUE},

            // hooks have impure boolean value if both cases have same impure boolean value
            {"a?true:true", TernaryValue.TRUE},
            {"a?false:false", TernaryValue.FALSE},
            {"a?true:false", TernaryValue.UNKNOWN},
            {"a?true:foo()", TernaryValue.UNKNOWN},

            // coalesce returns LHS if LHS is truthy or if LHS and RHS have same boolean value
            {"null??false", TernaryValue.FALSE}, // both false
            {"2??[]", TernaryValue.TRUE}, // both true
            {"{}??false", TernaryValue.TRUE}, // LHS is true
            {"undefined??[]", TernaryValue.UNKNOWN},
            {"foo()??true", TernaryValue.UNKNOWN},
          });
    }

    @Test
    public void getBooleanValue() {
      assertThat(NodeUtil.getBooleanValue(parseExpr(jsExpression))).isEqualTo(expectedResult);
    }
  }

  @RunWith(JUnit4.class)
  public static final class IsPropertyTestTests {

    @Test
    public void optionalChainGetPropIsPropertyTest() {
      Compiler compiler = new Compiler();
      Node getProp = parseExpr("x.y?.z");
      assertThat(NodeUtil.isPropertyTest(compiler, getProp.getFirstChild())).isTrue();
    }

    @Test
    public void optionalChainGetElemIsPropertyTest() {
      Compiler compiler = new Compiler();
      Node getElem = parseExpr("x.y?.[z]");
      assertThat(NodeUtil.isPropertyTest(compiler, getElem.getFirstChild())).isTrue();
    }

    @Test
    public void optionalChainCallIsPropertyTest() {
      Compiler compiler = new Compiler();
      Node call = parseExpr("x.y?.(z)");
      assertThat(NodeUtil.isPropertyTest(compiler, call.getFirstChild())).isTrue();
    }

    @Test
    public void optionalChainNonStartOfChainIsPropertyTest() {
      Compiler compiler = new Compiler();
      Node getProp = parseExpr("x.y?.z.foo.bar");
      assertThat(NodeUtil.isPropertyTest(compiler, getProp.getFirstChild())).isTrue();
    }
  }

  /**
   * A nested class to allow the `Enclosed` runner to run these tests since it doesn't run tests in
   * the outer class.
   *
   * <p>There's no particular organization to these tests since they were written in JUnit3. Many of
   * them could benefit from being broken out into their own classes.
   */
  @RunWith(JUnit4.class)
  public static final class AssortedTests {

    @Test
    public void testIsLiteralOrConstValue() {
      assertLiteralAndImmutable(parseExpr("10"));
      assertLiteralAndImmutable(parseExpr("-10"));
      assertLiteralButNotImmutable(parseExpr("[10, 20]"));
      assertLiteralButNotImmutable(parseExpr("{'a': 20}"));
      assertLiteralButNotImmutable(parseExpr("[10, , 1.0, [undefined], 'a']"));
      assertLiteralButNotImmutable(parseExpr("/abc/"));
      assertLiteralAndImmutable(parseExpr("\"string\""));
      assertLiteralAndImmutable(parseExpr("'aaa'"));
      assertLiteralAndImmutable(parseExpr("null"));
      assertLiteralAndImmutable(parseExpr("undefined"));
      assertLiteralAndImmutable(parseExpr("void 0"));
      assertNotLiteral(parseExpr("abc"));
      assertNotLiteral(parseExpr("[10, foo(), 20]"));
      assertNotLiteral(parseExpr("foo()"));
      assertNotLiteral(parseExpr("c + d"));
      assertNotLiteral(parseExpr("{'a': foo()}"));
      assertNotLiteral(parseExpr("void foo()"));
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
      return NodeUtil.isLiteralValue(parseExpr(code), /* includeFunctions */ false);
    }

    private boolean isLiteralValue(String code) {
      return NodeUtil.isLiteralValue(parseExpr(code), /* includeFunctions */ true);
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
    public void testGetStringValue() {
      assertThat(NodeUtil.getStringValue(parseExpr("true"))).isEqualTo("true");
      assertThat(NodeUtil.getStringValue(parseExpr("10"))).isEqualTo("10");
      assertThat(NodeUtil.getStringValue(parseExpr("1.0"))).isEqualTo("1");

      /* See https://github.com/google/closure-compiler/issues/1262 */
      assertThat(NodeUtil.getStringValue(parseExpr("1.2323919403474454e+21")))
          .isEqualTo("1.2323919403474454e+21");

      assertThat(NodeUtil.getStringValue(parseExpr("'0'"))).isEqualTo("0");
      assertThat(NodeUtil.getStringValue(parseExpr("/a/"))).isNull();
      assertThat(NodeUtil.getStringValue(parseExpr("{}"))).isEqualTo("[object Object]");
      assertThat(NodeUtil.getStringValue(parseExpr("[]"))).isEmpty();
      assertThat(NodeUtil.getStringValue(parseExpr("false"))).isEqualTo("false");
      assertThat(NodeUtil.getStringValue(parseExpr("null"))).isEqualTo("null");
      assertThat(NodeUtil.getStringValue(parseExpr("0"))).isEqualTo("0");
      assertThat(NodeUtil.getStringValue(parseExpr("1n"))).isEqualTo("1n");
      assertThat(NodeUtil.getStringValue(parseExpr("''"))).isEmpty();
      assertThat(NodeUtil.getStringValue(parseExpr("undefined"))).isEqualTo("undefined");
      assertThat(NodeUtil.getStringValue(parseExpr("void 0"))).isEqualTo("undefined");
      assertThat(NodeUtil.getStringValue(parseExpr("void foo()"))).isEqualTo("undefined");

      assertThat(NodeUtil.getStringValue(parseExpr("NaN"))).isEqualTo("NaN");
      assertThat(NodeUtil.getStringValue(parseExpr("Infinity"))).isEqualTo("Infinity");
      assertThat(NodeUtil.getStringValue(parseExpr("x"))).isNull();

      assertThat(NodeUtil.getStringValue(parseExpr("`Hello`"))).isEqualTo("Hello");
      assertThat(NodeUtil.getStringValue(parseExpr("`Hello ${'foo'}`"))).isEqualTo("Hello foo");
      assertThat(NodeUtil.getStringValue(parseExpr("`Hello ${name}`"))).isNull();
      assertThat(NodeUtil.getStringValue(parseExpr("`${4} bananas`"))).isEqualTo("4 bananas");
      assertThat(NodeUtil.getStringValue(parseExpr("`This is ${true}.`")))
          .isEqualTo("This is true.");
      assertThat(NodeUtil.getStringValue(parseExpr("`${'hello'} ${name}`"))).isNull();
    }

    @Test
    public void testGetArrayStringValue() {
      assertThat(NodeUtil.getStringValue(parseExpr("[]"))).isEmpty();
      assertThat(NodeUtil.getStringValue(parseExpr("['']"))).isEmpty();
      assertThat(NodeUtil.getStringValue(parseExpr("[null]"))).isEmpty();
      assertThat(NodeUtil.getStringValue(parseExpr("[undefined]"))).isEmpty();
      assertThat(NodeUtil.getStringValue(parseExpr("[void 0]"))).isEmpty();
      assertThat(NodeUtil.getStringValue(parseExpr("[NaN]"))).isEqualTo("NaN");
      assertThat(NodeUtil.getStringValue(parseExpr("[,'']"))).isEqualTo(",");
      assertThat(NodeUtil.getStringValue(parseExpr("[[''],[''],['']]"))).isEqualTo(",,");
      assertThat(NodeUtil.getStringValue(parseExpr("[[1.0],[2.0]]"))).isEqualTo("1,2");
      assertThat(NodeUtil.getStringValue(parseExpr("[a]"))).isNull();
      assertThat(NodeUtil.getStringValue(parseExpr("[1,a]"))).isNull();
    }

    @Test
    public void testMayBeObjectLitKey() {
      assertMayBeObjectLitKey(parseExpr("({})"), false);
      assertMayBeObjectLitKey(parseExpr("a"), false);
      assertMayBeObjectLitKey(parseExpr("'a'"), false);
      assertMayBeObjectLitKey(parseExpr("1"), false);
      assertMayBeObjectLitKey(parseExpr("1n"), false);
      assertMayBeObjectLitKey(parseExpr("({a: 1})").getFirstChild(), true);
      assertMayBeObjectLitKey(parseExpr("({1: 1})").getFirstChild(), true);
      assertMayBeObjectLitKey(parseExpr("({get a(){}})").getFirstChild(), true);
      assertMayBeObjectLitKey(parseExpr("({set a(b){}})").getFirstChild(), true);
      // returns false for computed properties (b/111621528)
      assertMayBeObjectLitKey(parseExpr("({['a']: 1})").getFirstChild(), false);
      // returns true for non-object-literal keys
      assertMayBeObjectLitKey(parseExpr("({a} = {})").getFirstFirstChild(), true);
      assertMayBeObjectLitKey(parseExpr("(class { a() {} })").getLastChild().getFirstChild(), true);
    }

    @Test
    public void testIsObjectLitKey() {
      assertIsObjectLitKey(parseExpr("({a: 1})").getFirstChild(), true);
      // returns false for computed properties (b/111621528)
      assertMayBeObjectLitKey(parseExpr("({['a']: 1})").getFirstChild(), false);
      // returns false for object patterns
      assertIsObjectLitKey(parseExpr("({a} = {})").getFirstFirstChild(), false);
      assertIsObjectLitKey(parseExpr("(class { a() {} })").getLastChild().getFirstChild(), false);
    }

    private void assertMayBeObjectLitKey(Node node, boolean expected) {
      assertThat(NodeUtil.mayBeObjectLitKey(node)).isEqualTo(expected);
    }

    private void assertIsObjectLitKey(Node node, boolean expected) {
      assertThat(NodeUtil.isObjectLitKey(node)).isEqualTo(expected);
    }

    @Test
    public void testGetFunctionName1() {
      Node parent = parse("function name(){}");
      assertGetNameResult(parent.getFirstChild(), "name");
    }

    @Test
    public void testGetFunctionName2() {
      Node parent = parse("var name = function(){}").getFirstFirstChild();

      assertGetNameResult(parent.getFirstChild(), "name");
    }

    @Test
    public void testGetFunctionName3() {
      Node parent = parse("qualified.name = function(){}").getFirstFirstChild();

      assertGetNameResult(parent.getLastChild(), "qualified.name");
    }

    @Test
    public void testGetFunctionName4() {
      Node parent = parse("var name2 = function name1(){}").getFirstFirstChild();

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
      Node parent = parse("var obj = {memFunc(){}}").getFirstFirstChild().getFirstFirstChild();

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
    public void testIsFunctionDeclaration() {
      assertThat(NodeUtil.isFunctionDeclaration(parseFirst(FUNCTION, "function foo(){}"))).isTrue();
      assertThat(
              NodeUtil.isFunctionDeclaration(parseFirst(FUNCTION, "class C { constructor() {} }")))
          .isFalse();
      assertThat(NodeUtil.isFunctionDeclaration(parseFirst(FUNCTION, "({ foo() {} })"))).isFalse();
      assertThat(NodeUtil.isFunctionDeclaration(parseFirst(FUNCTION, "var x = function(){}")))
          .isFalse();
      assertThat(NodeUtil.isFunctionDeclaration(parseFirst(FUNCTION, "export function f() {}")))
          .isTrue();
      assertThat(
              NodeUtil.isFunctionDeclaration(parseFirst(FUNCTION, "export default function() {}")))
          .isFalse();
      assertThat(
              NodeUtil.isFunctionDeclaration(
                  parseFirst(FUNCTION, "export default function foo() {}")))
          .isTrue();
      assertThat(
              NodeUtil.isFunctionDeclaration(
                  parseFirst(FUNCTION, "export default (foo) => { alert(foo); }")))
          .isFalse();
    }

    @Test
    public void testIsMethodDeclaration() {
      assertThat(NodeUtil.isMethodDeclaration(parseFirst(FUNCTION, "class C { constructor() {} }")))
          .isTrue();
      assertThat(NodeUtil.isMethodDeclaration(parseFirst(FUNCTION, "class C { a() {} }"))).isTrue();
      assertThat(NodeUtil.isMethodDeclaration(parseFirst(FUNCTION, "class C { static a() {} }")))
          .isTrue();
      assertThat(NodeUtil.isMethodDeclaration(parseFirst(FUNCTION, "({ set foo(v) {} })")))
          .isTrue();
      assertThat(NodeUtil.isMethodDeclaration(parseFirst(FUNCTION, "({ get foo() {} })"))).isTrue();
      assertThat(NodeUtil.isMethodDeclaration(parseFirst(FUNCTION, "({ [foo]() {} })"))).isTrue();
    }

    @Test
    public void testIsClassDeclaration() {
      assertThat(NodeUtil.isClassDeclaration(parseFirst(CLASS, "class Foo {}"))).isTrue();
      assertThat(NodeUtil.isClassDeclaration(parseFirst(CLASS, "var Foo = class {}"))).isFalse();
      assertThat(NodeUtil.isClassDeclaration(parseFirst(CLASS, "var Foo = class Foo{}"))).isFalse();
      assertThat(NodeUtil.isClassDeclaration(parseFirst(CLASS, "export default class Foo {}")))
          .isTrue();
      assertThat(NodeUtil.isClassDeclaration(parseFirst(CLASS, "export class Foo {}"))).isTrue();
      assertThat(NodeUtil.isClassDeclaration(parseFirst(CLASS, "export default class {}")))
          .isFalse();
    }

    @Test
    public void testIsFunctionExpression() {
      assertContainsAnonFunc(true, "(function(){})");
      assertContainsAnonFunc(true, "[function a(){}]");
      assertContainsAnonFunc(true, "({x: function a(){}})");
      assertContainsAnonFunc(true, "({[0]: function a() {}})");
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
      assertContainsAnonFunc(false, "({[0]() {}})");
      assertContainsAnonFunc(false, "({get [0]() {}})");
      assertContainsAnonFunc(false, "({set [0](x) {}})");
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
      assertWithMessage("Expected class node in parse tree of: %s", js)
          .that(classParent)
          .isNotNull();
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
              NodeUtil.isNameReferenced(
                  parse("undefined;function foo(){}(undefined)"), "undefined"))
          .isTrue();

      assertThat(NodeUtil.isNameReferenced(parse("goo.foo"), "goo")).isTrue();
      assertThat(NodeUtil.isNameReferenced(parse("goo.foo"), "foo")).isFalse();
    }

    @Test
    public void testGetNameReferenceCount() {
      assertThat(NodeUtil.getNameReferenceCount(parse("function foo(){}"), "undefined"))
          .isEqualTo(0);
      assertThat(NodeUtil.getNameReferenceCount(parse("undefined"), "undefined")).isEqualTo(1);
      assertThat(
              NodeUtil.getNameReferenceCount(
                  parse("undefined;function foo(){}(undefined)"), "undefined"))
          .isEqualTo(2);

      assertThat(NodeUtil.getNameReferenceCount(parse("goo.foo"), "goo")).isEqualTo(1);
      assertThat(NodeUtil.getNameReferenceCount(parse("goo.foo"), "foo")).isEqualTo(0);
      assertThat(NodeUtil.getNameReferenceCount(parse("function foo(){}"), "foo")).isEqualTo(1);
      assertThat(NodeUtil.getNameReferenceCount(parse("var foo = function(){}"), "foo"))
          .isEqualTo(1);
    }

    @Test
    public void testGetVarsDeclaredInBranch() {
      assertNodeNames(ImmutableSet.of("foo"), NodeUtil.getVarsDeclaredInBranch(parse("var foo;")));
      assertNodeNames(
          ImmutableSet.of("foo", "goo"), NodeUtil.getVarsDeclaredInBranch(parse("var foo,goo;")));
      assertNodeNames(ImmutableSet.<String>of(), NodeUtil.getVarsDeclaredInBranch(parse("foo();")));
      assertNodeNames(
          ImmutableSet.<String>of(),
          NodeUtil.getVarsDeclaredInBranch(parse("function f(){var foo;}")));
      assertNodeNames(
          ImmutableSet.of("goo"),
          NodeUtil.getVarsDeclaredInBranch(parse("var goo;function f(){var foo;}")));
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
    public void testIsNonlocalModuleExportNameOnExportFrom() {
      Node root = parse("export {bar as baz} from './foo.js';");
      Node moduleBody = root.getFirstChild();
      Node exportNode = moduleBody.getFirstChild();
      Node exportSpecs = exportNode.getFirstChild();
      Node exportSpec = exportSpecs.getFirstChild();

      Node bar = exportSpec.getFirstChild();
      Node baz = exportSpec.getSecondChild();

      // Neither identifier is defined locally.
      assertThat(NodeUtil.isNonlocalModuleExportName(bar)).isTrue();
      assertThat(NodeUtil.isNonlocalModuleExportName(baz)).isTrue();
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
      replaceDeclChild(
          "const x =1, y = 2, z = 3, w = 4;", 1, "const x = 1; {} const z = 3, w = 4;");
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
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("x"))).isFalse();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("x()"))).isFalse();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("this"))).isFalse();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("arguments"))).isFalse();

      // We can't know if new objects are local unless we know
      // that they don't alias themselves.
      // TODO(tdeegan): Revisit this.
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("new x()"))).isFalse();

      // property references are assumed to be non-local
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("(new x()).y"))).isFalse();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("(new x())['y']"))).isFalse();

      // Primitive values are local
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("null"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("undefined"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("Infinity"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("NaN"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("1"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("'a'"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("true"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("false"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("[]"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("{}"))).isTrue();

      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("[x]"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("{'a':x}"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("{'a': {'b': 2}}"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("{'a': {'b': global}}"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("{get someGetter() { return 1; }}")))
          .isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("{get someGetter() { return global; }}")))
          .isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("{set someSetter(value) {}}"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("{[someComputedProperty]: {}}")))
          .isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("{[someComputedProperty]: global}")))
          .isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("{[someComputedProperty]: {'a':x}}")))
          .isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("{[someComputedProperty]: {'a':1}}")))
          .isTrue();

      // increment/decrement results in primitive number, the previous value is
      // always coersed to a number (even in the post.
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("++x"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("--x"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("x++"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("x--"))).isTrue();

      // The left side of an only assign matters if it is an alias or mutable.
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("x=1"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("x=[]"))).isFalse();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("x=y"))).isFalse();
      // The right hand side of assignment opts don't matter, as they force
      // a local result.
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("x+=y"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("x*=y"))).isTrue();
      // Comparisons always result in locals, as they force a local boolean
      // result.
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("x==y"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("x!=y"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("x>y"))).isTrue();
      // Only the right side of a comma matters
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("(1,2)"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("(x,1)"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("(x,y)"))).isFalse();

      // Both the operands of OR matter
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("1||2"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("x||1"))).isFalse();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("x||y"))).isFalse();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("1||y"))).isFalse();

      // Both the operands of AND matter
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("1&&2"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("x&&1"))).isFalse();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("x&&y"))).isFalse();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("1&&y"))).isFalse();

      // Only the results of HOOK matter
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("x?1:2"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("x?x:2"))).isFalse();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("x?1:x"))).isFalse();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("x?x:y"))).isFalse();

      // Results of ops are local values
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("!y"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("~y"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("y + 1"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("y + z"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("y * z"))).isTrue();

      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("'a' in x"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("typeof x"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("x instanceof y"))).isTrue();

      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("void x"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("void 0"))).isTrue();

      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("{}.x"))).isFalse();

      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("{}.toString()"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("o.toString()"))).isTrue();

      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("o.valueOf()"))).isFalse();

      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("delete a.b"))).isTrue();

      assertThat(
              NodeUtil.evaluatesToLocalValue(
                  parseFirst(Token.NEW_TARGET, "function f() { new.target; }")))
          .isFalse();
    }

    @Test
    public void testLocalValueTemplateLit() {
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("`hello`"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("`hello ${name}`"))).isTrue();
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("`${'name'}`"))).isTrue();
      assertThat(NodeUtil.isLiteralValue(parseExpr("`${'name'}`"), false)).isTrue();
      assertThat(NodeUtil.isImmutableValue(parseExpr("`${'name'}`"))).isTrue();
    }

    @Test
    public void testLocalValueTaggedTemplateLit1() {
      Node n = parseExpr("tag`simple string`");
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
      Node n = parseExpr("tag`string with ${replacement()}`");
      assertThat(NodeUtil.evaluatesToLocalValue(n)).isFalse();

      // Set 'tag' function as producing a local result
      Node.SideEffectFlags flags = new Node.SideEffectFlags();
      flags.clearAllFlags();
      n.setSideEffectFlags(flags);

      assertThat(NodeUtil.evaluatesToLocalValue(n)).isTrue();
    }

    @Test
    public void testLocalValueNewExpr() {
      Node newExpr = parseExpr("new x()");
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
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("[...x]"))).isTrue();
      assertThat(NodeUtil.isLiteralValue(parseExpr("[...x]"), false)).isFalse();
      assertThat(NodeUtil.isImmutableValue(parseExpr("[...x]"))).isFalse();

      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("{...x}"))).isTrue();
      assertThat(NodeUtil.isLiteralValue(parseExpr("{...x}"), false)).isFalse();
      assertThat(NodeUtil.isImmutableValue(parseExpr("{...x}"))).isFalse();
    }

    @Test
    public void testLocalValueAwait() {
      Node expr;
      expr = parseFirst(AWAIT, "async function f() { await someAsyncAction(); }");
      assertThat(NodeUtil.evaluatesToLocalValue(expr)).isFalse();

      expr = parseFirst(AWAIT, "async function f() { await {then:function() { return p }}; }");
      assertThat(NodeUtil.evaluatesToLocalValue(expr)).isFalse();

      // it isn't clear why someone would want to wait on a non-thenable value...
      expr = parseFirst(AWAIT, "async function f() { await 5; }");
      assertThat(NodeUtil.evaluatesToLocalValue(expr)).isFalse();
    }

    @Test
    public void testLocalValueYield() {
      Node expr;
      expr = parseFirst(YIELD, "function *f() { yield; }");
      assertThat(NodeUtil.evaluatesToLocalValue(expr)).isFalse();

      expr = parseFirst(YIELD, "function *f() { yield 'something'; }");
      assertThat(NodeUtil.evaluatesToLocalValue(expr)).isFalse();
    }

    @Test
    public void localValueOptChainGetProp() {
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("x?.y"))).isFalse();
    }

    @Test
    public void localValueOptChainGetElem() {
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("x?.[y]"))).isFalse();
    }

    @Test
    public void localValueOptChainCall() {
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("x?.()"))).isFalse();
    }

    @Test
    public void localValueOptChainCall_toString() {
      // the toString() call is an optional call because it is part of an optional chain
      assertThat(NodeUtil.evaluatesToLocalValue(parseExpr("x?.toString()"))).isTrue();
    }

    @Test
    public void testGetOctalNumberValue() {
      assertThat(NodeUtil.getNumberValue(parseExpr("022"))).isEqualTo(18.0);
    }

    @SuppressWarnings("JUnit3FloatingPointComparisonWithoutDelta")
    @Test
    public void testGetNumberValue() {
      // Strings
      assertThat(NodeUtil.getNumberValue(parseExpr("'\\uFEFF1'"))).isEqualTo(1.0);
      assertThat(NodeUtil.getNumberValue(parseExpr("''"))).isEqualTo(0.0);
      assertThat(NodeUtil.getNumberValue(parseExpr("' '"))).isEqualTo(0.0);
      assertThat(NodeUtil.getNumberValue(parseExpr("' \\t'"))).isEqualTo(0.0);
      assertThat(NodeUtil.getNumberValue(parseExpr("'+0'"))).isEqualTo(0.0);
      assertThat(NodeUtil.getNumberValue(parseExpr("'-0'"))).isEqualTo(-0.0);
      assertThat(NodeUtil.getNumberValue(parseExpr("'+2'"))).isEqualTo(2.0);
      assertThat(NodeUtil.getNumberValue(parseExpr("'-1.6'"))).isEqualTo(-1.6);
      assertThat(NodeUtil.getNumberValue(parseExpr("'16'"))).isEqualTo(16.0);
      assertThat(NodeUtil.getNumberValue(parseExpr("' 16 '"))).isEqualTo(16.0);
      assertThat(NodeUtil.getNumberValue(parseExpr("' 16 '"))).isEqualTo(16.0);
      assertThat(NodeUtil.getNumberValue(parseExpr("'123e2'"))).isEqualTo(12300.0);
      assertThat(NodeUtil.getNumberValue(parseExpr("'123E2'"))).isEqualTo(12300.0);
      assertThat(NodeUtil.getNumberValue(parseExpr("'123e-2'"))).isEqualTo(1.23);
      assertThat(NodeUtil.getNumberValue(parseExpr("'123E-2'"))).isEqualTo(1.23);
      assertThat(NodeUtil.getNumberValue(parseExpr("'-123e-2'"))).isEqualTo(-1.23);
      assertThat(NodeUtil.getNumberValue(parseExpr("'-123E-2'"))).isEqualTo(-1.23);
      assertThat(NodeUtil.getNumberValue(parseExpr("'+123e-2'"))).isEqualTo(1.23);
      assertThat(NodeUtil.getNumberValue(parseExpr("'+123E-2'"))).isEqualTo(1.23);
      assertThat(NodeUtil.getNumberValue(parseExpr("'+123e+2'"))).isEqualTo(12300.0);
      assertThat(NodeUtil.getNumberValue(parseExpr("'+123E+2'"))).isEqualTo(12300.0);

      assertThat(NodeUtil.getNumberValue(parseExpr("'0xf'"))).isEqualTo(15.0);
      assertThat(NodeUtil.getNumberValue(parseExpr("'0xF'"))).isEqualTo(15.0);

      // Chrome and rhino behavior differently from FF and IE. FF and IE
      // consider a negative hex number to be invalid
      assertThat(NodeUtil.getNumberValue(parseExpr("'-0xf'"))).isNull();
      assertThat(NodeUtil.getNumberValue(parseExpr("'-0xF'"))).isNull();
      assertThat(NodeUtil.getNumberValue(parseExpr("'+0xf'"))).isNull();
      assertThat(NodeUtil.getNumberValue(parseExpr("'+0xF'"))).isNull();

      assertThat(NodeUtil.getNumberValue(parseExpr("'0X10'"))).isEqualTo(16.0);
      assertThat(NodeUtil.getNumberValue(parseExpr("'0X10.8'"))).isNaN();
      assertThat(NodeUtil.getNumberValue(parseExpr("'077'"))).isEqualTo(77.0);
      assertThat(NodeUtil.getNumberValue(parseExpr("'-077'"))).isEqualTo(-77.0);
      assertThat(NodeUtil.getNumberValue(parseExpr("'-077.5'"))).isEqualTo(-77.5);
      assertThat(NodeUtil.getNumberValue(parseExpr("'-Infinity'"))).isNegativeInfinity();
      assertThat(NodeUtil.getNumberValue(parseExpr("'Infinity'"))).isPositiveInfinity();
      assertThat(NodeUtil.getNumberValue(parseExpr("'+Infinity'"))).isPositiveInfinity();
      // Firefox treats "infinity" as "Infinity", IE treats it as NaN
      assertThat(NodeUtil.getNumberValue(parseExpr("'-infinity'"))).isNull();
      assertThat(NodeUtil.getNumberValue(parseExpr("'infinity'"))).isNull();
      assertThat(NodeUtil.getNumberValue(parseExpr("'+infinity'"))).isNull();

      assertThat(NodeUtil.getNumberValue(parseExpr("'NaN'"))).isNaN();
      assertThat(NodeUtil.getNumberValue(parseExpr("'some unknown string'"))).isNaN();
      assertThat(NodeUtil.getNumberValue(parseExpr("'123 blah'"))).isNaN();

      // Literals
      assertThat(NodeUtil.getNumberValue(parseExpr("1"))).isEqualTo(1.0);
      assertThat(NodeUtil.getNumberValue(parseExpr("1n"))).isEqualTo(null);
      // "-1" is parsed as a literal
      assertThat(NodeUtil.getNumberValue(parseExpr("-1"))).isEqualTo(-1.0);
      // "+1" is parse as an op + literal
      assertThat(NodeUtil.getNumberValue(parseExpr("+1"))).isNull();
      assertThat(NodeUtil.getNumberValue(parseExpr("22"))).isEqualTo(22.0);
      assertThat(NodeUtil.getNumberValue(parseExpr("022"))).isEqualTo(18.0);
      assertThat(NodeUtil.getNumberValue(parseExpr("0x22"))).isEqualTo(34.0);

      assertThat(NodeUtil.getNumberValue(parseExpr("true"))).isEqualTo(1.0);
      assertThat(NodeUtil.getNumberValue(parseExpr("false"))).isEqualTo(0.0);
      assertThat(NodeUtil.getNumberValue(parseExpr("null"))).isEqualTo(0.0);
      assertThat(NodeUtil.getNumberValue(parseExpr("void 0"))).isNaN();
      assertThat(NodeUtil.getNumberValue(parseExpr("void f"))).isNaN();
      // we pay no attention to possible side effects.
      assertThat(NodeUtil.getNumberValue(parseExpr("void f()"))).isNaN();
      assertThat(NodeUtil.getNumberValue(parseExpr("NaN"))).isNaN();
      assertThat(NodeUtil.getNumberValue(parseExpr("Infinity"))).isPositiveInfinity();
      assertThat(NodeUtil.getNumberValue(parseExpr("-Infinity"))).isNegativeInfinity();

      // "infinity" is not a known name.
      assertThat(NodeUtil.getNumberValue(parseExpr("infinity"))).isNull();
      assertThat(NodeUtil.getNumberValue(parseExpr("-infinity"))).isNull();

      // getNumberValue only converts literals
      assertThat(NodeUtil.getNumberValue(parseExpr("x"))).isNull();
      assertThat(NodeUtil.getNumberValue(parseExpr("x.y"))).isNull();
      assertThat(NodeUtil.getNumberValue(parseExpr("1/2"))).isNull();
      assertThat(NodeUtil.getNumberValue(parseExpr("1-2"))).isNull();
      assertThat(NodeUtil.getNumberValue(parseExpr("+1"))).isNull();
    }

    @Test
    public void testIsNumericResult() {
      assertThat(NodeUtil.isNumericResult(parseExpr("1"))).isTrue();
      assertThat(NodeUtil.isNumericResult(parseExpr("true"))).isFalse();
      assertThat(NodeUtil.isNumericResult(parseExpr("+true"))).isTrue();
      assertThat(NodeUtil.isNumericResult(parseExpr("+1"))).isTrue();
      assertThat(NodeUtil.isNumericResult(parseExpr("-1"))).isTrue();
      assertThat(NodeUtil.isNumericResult(parseExpr("-Infinity"))).isTrue();
      assertThat(NodeUtil.isNumericResult(parseExpr("Infinity"))).isTrue();
      assertThat(NodeUtil.isNumericResult(parseExpr("NaN"))).isTrue();
      assertThat(NodeUtil.isNumericResult(parseExpr("undefined"))).isFalse();
      assertThat(NodeUtil.isNumericResult(parseExpr("void 0"))).isFalse();

      assertThat(NodeUtil.isNumericResult(parseExpr("a << b"))).isTrue();
      assertThat(NodeUtil.isNumericResult(parseExpr("a >> b"))).isTrue();
      assertThat(NodeUtil.isNumericResult(parseExpr("a >>> b"))).isTrue();

      assertThat(NodeUtil.isNumericResult(parseExpr("a == b"))).isFalse();
      assertThat(NodeUtil.isNumericResult(parseExpr("a != b"))).isFalse();
      assertThat(NodeUtil.isNumericResult(parseExpr("a === b"))).isFalse();
      assertThat(NodeUtil.isNumericResult(parseExpr("a !== b"))).isFalse();
      assertThat(NodeUtil.isNumericResult(parseExpr("a < b"))).isFalse();
      assertThat(NodeUtil.isNumericResult(parseExpr("a > b"))).isFalse();
      assertThat(NodeUtil.isNumericResult(parseExpr("a <= b"))).isFalse();
      assertThat(NodeUtil.isNumericResult(parseExpr("a >= b"))).isFalse();
      assertThat(NodeUtil.isNumericResult(parseExpr("a in b"))).isFalse();
      assertThat(NodeUtil.isNumericResult(parseExpr("a instanceof b"))).isFalse();

      assertThat(NodeUtil.isNumericResult(parseExpr("'a'"))).isFalse();
      assertThat(NodeUtil.isNumericResult(parseExpr("'a'+b"))).isFalse();
      assertThat(NodeUtil.isNumericResult(parseExpr("a+'b'"))).isFalse();
      assertThat(NodeUtil.isNumericResult(parseExpr("a+b"))).isFalse();
      assertThat(NodeUtil.isNumericResult(parseExpr("a()"))).isFalse();
      assertThat(NodeUtil.isNumericResult(parseExpr("''.a"))).isFalse();
      assertThat(NodeUtil.isNumericResult(parseExpr("a.b"))).isFalse();
      assertThat(NodeUtil.isNumericResult(parseExpr("a.b()"))).isFalse();
      assertThat(NodeUtil.isNumericResult(parseExpr("a().b()"))).isFalse();
      assertThat(NodeUtil.isNumericResult(parseExpr("new a()"))).isFalse();

      // Definitely not numeric
      assertThat(NodeUtil.isNumericResult(parseExpr("([1,2])"))).isFalse();
      assertThat(NodeUtil.isNumericResult(parseExpr("({a:1})"))).isFalse();

      // Recurse into the expression when necessary.
      assertThat(NodeUtil.isNumericResult(parseExpr("1 && 2"))).isTrue();
      assertThat(NodeUtil.isNumericResult(parseExpr("1 || 2"))).isTrue();
      assertThat(NodeUtil.isNumericResult(parseExpr("a ? 2 : 3"))).isTrue();
      assertThat(NodeUtil.isNumericResult(parseExpr("a,1"))).isTrue();
      assertThat(NodeUtil.isNumericResult(parseExpr("a=1"))).isTrue();

      assertThat(NodeUtil.isNumericResult(parseExpr("a += 1"))).isFalse();

      assertThat(NodeUtil.isNumericResult(parseExpr("a -= 1"))).isTrue();
      assertThat(NodeUtil.isNumericResult(parseExpr("a *= 1"))).isTrue();
      assertThat(NodeUtil.isNumericResult(parseExpr("--a"))).isTrue();
      assertThat(NodeUtil.isNumericResult(parseExpr("++a"))).isTrue();
      assertThat(NodeUtil.isNumericResult(parseExpr("a++"))).isTrue();
      assertThat(NodeUtil.isNumericResult(parseExpr("a--"))).isTrue();
    }

    @Test
    public void testIsBooleanResult() {
      assertThat(NodeUtil.isBooleanResult(parseExpr("1"))).isFalse();
      assertThat(NodeUtil.isBooleanResult(parseExpr("true"))).isTrue();
      assertThat(NodeUtil.isBooleanResult(parseExpr("+true"))).isFalse();
      assertThat(NodeUtil.isBooleanResult(parseExpr("+1"))).isFalse();
      assertThat(NodeUtil.isBooleanResult(parseExpr("-1"))).isFalse();
      assertThat(NodeUtil.isBooleanResult(parseExpr("-Infinity"))).isFalse();
      assertThat(NodeUtil.isBooleanResult(parseExpr("Infinity"))).isFalse();
      assertThat(NodeUtil.isBooleanResult(parseExpr("NaN"))).isFalse();
      assertThat(NodeUtil.isBooleanResult(parseExpr("undefined"))).isFalse();
      assertThat(NodeUtil.isBooleanResult(parseExpr("void 0"))).isFalse();

      assertThat(NodeUtil.isBooleanResult(parseExpr("a << b"))).isFalse();
      assertThat(NodeUtil.isBooleanResult(parseExpr("a >> b"))).isFalse();
      assertThat(NodeUtil.isBooleanResult(parseExpr("a >>> b"))).isFalse();

      assertThat(NodeUtil.isBooleanResult(parseExpr("a == b"))).isTrue();
      assertThat(NodeUtil.isBooleanResult(parseExpr("a != b"))).isTrue();
      assertThat(NodeUtil.isBooleanResult(parseExpr("a === b"))).isTrue();
      assertThat(NodeUtil.isBooleanResult(parseExpr("a !== b"))).isTrue();
      assertThat(NodeUtil.isBooleanResult(parseExpr("a < b"))).isTrue();
      assertThat(NodeUtil.isBooleanResult(parseExpr("a > b"))).isTrue();
      assertThat(NodeUtil.isBooleanResult(parseExpr("a <= b"))).isTrue();
      assertThat(NodeUtil.isBooleanResult(parseExpr("a >= b"))).isTrue();
      assertThat(NodeUtil.isBooleanResult(parseExpr("a in b"))).isTrue();
      assertThat(NodeUtil.isBooleanResult(parseExpr("a instanceof b"))).isTrue();

      assertThat(NodeUtil.isBooleanResult(parseExpr("'a'"))).isFalse();
      assertThat(NodeUtil.isBooleanResult(parseExpr("'a'+b"))).isFalse();
      assertThat(NodeUtil.isBooleanResult(parseExpr("a+'b'"))).isFalse();
      assertThat(NodeUtil.isBooleanResult(parseExpr("a+b"))).isFalse();
      assertThat(NodeUtil.isBooleanResult(parseExpr("a()"))).isFalse();
      assertThat(NodeUtil.isBooleanResult(parseExpr("''.a"))).isFalse();
      assertThat(NodeUtil.isBooleanResult(parseExpr("a.b"))).isFalse();
      assertThat(NodeUtil.isBooleanResult(parseExpr("a.b()"))).isFalse();
      assertThat(NodeUtil.isBooleanResult(parseExpr("a().b()"))).isFalse();
      assertThat(NodeUtil.isBooleanResult(parseExpr("new a()"))).isFalse();
      assertThat(NodeUtil.isBooleanResult(parseExpr("delete a"))).isTrue();

      // Definitely not boolean
      assertThat(NodeUtil.isBooleanResult(parseExpr("([true,false])"))).isFalse();
      assertThat(NodeUtil.isBooleanResult(parseExpr("({a:true})"))).isFalse();

      // These are boolean
      assertThat(NodeUtil.isBooleanResult(parseExpr("true && false"))).isTrue();
      assertThat(NodeUtil.isBooleanResult(parseExpr("true || false"))).isTrue();
      assertThat(NodeUtil.isBooleanResult(parseExpr("a ? true : false"))).isTrue();
      assertThat(NodeUtil.isBooleanResult(parseExpr("a,true"))).isTrue();
      assertThat(NodeUtil.isBooleanResult(parseExpr("a=true"))).isTrue();
      assertThat(NodeUtil.isBooleanResult(parseExpr("a=1"))).isFalse();
    }

    @Test
    public void testMayBeString() {
      assertThat(NodeUtil.mayBeString(parseExpr("1"))).isFalse();
      assertThat(NodeUtil.mayBeString(parseExpr("1n"))).isFalse();
      assertThat(NodeUtil.mayBeString(parseExpr("true"))).isFalse();
      assertThat(NodeUtil.mayBeString(parseExpr("+true"))).isFalse();
      assertThat(NodeUtil.mayBeString(parseExpr("+1"))).isFalse();
      assertThat(NodeUtil.mayBeString(parseExpr("-1"))).isFalse();
      assertThat(NodeUtil.mayBeString(parseExpr("-Infinity"))).isFalse();
      assertThat(NodeUtil.mayBeString(parseExpr("Infinity"))).isFalse();
      assertThat(NodeUtil.mayBeString(parseExpr("NaN"))).isFalse();
      assertThat(NodeUtil.mayBeString(parseExpr("undefined"))).isFalse();
      assertThat(NodeUtil.mayBeString(parseExpr("void 0"))).isFalse();
      assertThat(NodeUtil.mayBeString(parseExpr("null"))).isFalse();

      assertThat(NodeUtil.mayBeString(parseExpr("a << b"))).isFalse();
      assertThat(NodeUtil.mayBeString(parseExpr("a >> b"))).isFalse();
      assertThat(NodeUtil.mayBeString(parseExpr("a >>> b"))).isFalse();

      assertThat(NodeUtil.mayBeString(parseExpr("a == b"))).isFalse();
      assertThat(NodeUtil.mayBeString(parseExpr("a != b"))).isFalse();
      assertThat(NodeUtil.mayBeString(parseExpr("a === b"))).isFalse();
      assertThat(NodeUtil.mayBeString(parseExpr("a !== b"))).isFalse();
      assertThat(NodeUtil.mayBeString(parseExpr("a < b"))).isFalse();
      assertThat(NodeUtil.mayBeString(parseExpr("a > b"))).isFalse();
      assertThat(NodeUtil.mayBeString(parseExpr("a <= b"))).isFalse();
      assertThat(NodeUtil.mayBeString(parseExpr("a >= b"))).isFalse();
      assertThat(NodeUtil.mayBeString(parseExpr("a in b"))).isFalse();
      assertThat(NodeUtil.mayBeString(parseExpr("a instanceof b"))).isFalse();

      assertThat(NodeUtil.mayBeString(parseExpr("'a'"))).isTrue();
      assertThat(NodeUtil.mayBeString(parseExpr("'a'+b"))).isTrue();
      assertThat(NodeUtil.mayBeString(parseExpr("a+'b'"))).isTrue();
      assertThat(NodeUtil.mayBeString(parseExpr("a+b"))).isTrue();
      assertThat(NodeUtil.mayBeString(parseExpr("a()"))).isTrue();
      assertThat(NodeUtil.mayBeString(parseExpr("''.a"))).isTrue();
      assertThat(NodeUtil.mayBeString(parseExpr("a.b"))).isTrue();
      assertThat(NodeUtil.mayBeString(parseExpr("a.b()"))).isTrue();
      assertThat(NodeUtil.mayBeString(parseExpr("a().b()"))).isTrue();
      assertThat(NodeUtil.mayBeString(parseExpr("new a()"))).isTrue();

      // These can't be strings but they aren't handled yet.
      assertThat(NodeUtil.mayBeString(parseExpr("1 && 2"))).isFalse();
      assertThat(NodeUtil.mayBeString(parseExpr("1 || 2"))).isFalse();
      assertThat(NodeUtil.mayBeString(parseExpr("1 ? 2 : 3"))).isFalse();
      assertThat(NodeUtil.mayBeString(parseExpr("1,2"))).isFalse();
      assertThat(NodeUtil.mayBeString(parseExpr("a=1"))).isFalse();
      assertThat(NodeUtil.mayBeString(parseExpr("1+1"))).isFalse();
      assertThat(NodeUtil.mayBeString(parseExpr("true+true"))).isFalse();
      assertThat(NodeUtil.mayBeString(parseExpr("null+null"))).isFalse();
      assertThat(NodeUtil.mayBeString(parseExpr("NaN+NaN"))).isFalse();

      // These are not strings but they aren't primitives either
      assertThat(NodeUtil.mayBeString(parseExpr("([1,2])"))).isTrue();
      assertThat(NodeUtil.mayBeString(parseExpr("({a:1})"))).isTrue();
      assertThat(NodeUtil.mayBeString(parseExpr("({}+1)"))).isTrue();
      assertThat(NodeUtil.mayBeString(parseExpr("(1+{})"))).isTrue();
      assertThat(NodeUtil.mayBeString(parseExpr("([]+1)"))).isTrue();
      assertThat(NodeUtil.mayBeString(parseExpr("(1+[])"))).isTrue();

      assertThat(NodeUtil.mayBeString(parseExpr("a += 'x'"))).isTrue();
      assertThat(NodeUtil.mayBeString(parseExpr("a += 1"))).isTrue();
    }

    @Test
    public void testIsStringResult() {
      assertThat(NodeUtil.isStringResult(parseExpr("1"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("true"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("+true"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("+1"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("-1"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("-Infinity"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("Infinity"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("NaN"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("undefined"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("void 0"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("null"))).isFalse();

      assertThat(NodeUtil.isStringResult(parseExpr("a << b"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("a >> b"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("a >>> b"))).isFalse();

      assertThat(NodeUtil.isStringResult(parseExpr("a == b"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("a != b"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("a === b"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("a !== b"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("a < b"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("a > b"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("a <= b"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("a >= b"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("a in b"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("a instanceof b"))).isFalse();

      assertThat(NodeUtil.isStringResult(parseExpr("'a'"))).isTrue();
      assertThat(NodeUtil.isStringResult(parseExpr("'a'+b"))).isTrue();
      assertThat(NodeUtil.isStringResult(parseExpr("a+'b'"))).isTrue();
      assertThat(NodeUtil.isStringResult(parseExpr("a+b"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("a()"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("''.a"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("a.b"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("a.b()"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("a().b()"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("new a()"))).isFalse();

      // These can't be strings but they aren't handled yet.
      assertThat(NodeUtil.isStringResult(parseExpr("1 && 2"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("1 || 2"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("1 ? 2 : 3"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("1,2"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("a=1"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("1+1"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("true+true"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("null+null"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("NaN+NaN"))).isFalse();

      // These are not strings but they aren't primitives either
      assertThat(NodeUtil.isStringResult(parseExpr("([1,2])"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("({a:1})"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("({}+1)"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("(1+{})"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("([]+1)"))).isFalse();
      assertThat(NodeUtil.isStringResult(parseExpr("(1+[])"))).isFalse();

      assertThat(NodeUtil.isStringResult(parseExpr("a += 'x'"))).isTrue();

      // Template literals
      assertThat(NodeUtil.isStringResult(parseExpr("`x`"))).isTrue();
      assertThat(NodeUtil.isStringResult(parseExpr("`a${b}c`"))).isTrue();
    }

    @Test
    public void testIsObjectResult() {
      assertThat(NodeUtil.isObjectResult(parseExpr("1"))).isFalse();
      assertThat(NodeUtil.isObjectResult(parseExpr("true"))).isFalse();
      assertThat(NodeUtil.isObjectResult(parseExpr("+true"))).isFalse();
      assertThat(NodeUtil.isObjectResult(parseExpr("+1"))).isFalse();
      assertThat(NodeUtil.isObjectResult(parseExpr("-1"))).isFalse();
      assertThat(NodeUtil.isObjectResult(parseExpr("-Infinity"))).isFalse();
      assertThat(NodeUtil.isObjectResult(parseExpr("Infinity"))).isFalse();
      assertThat(NodeUtil.isObjectResult(parseExpr("NaN"))).isFalse();
      assertThat(NodeUtil.isObjectResult(parseExpr("undefined"))).isFalse();
      assertThat(NodeUtil.isObjectResult(parseExpr("void 0"))).isFalse();

      assertThat(NodeUtil.isObjectResult(parseExpr("a << b"))).isFalse();
      assertThat(NodeUtil.isObjectResult(parseExpr("a >> b"))).isFalse();
      assertThat(NodeUtil.isObjectResult(parseExpr("a >>> b"))).isFalse();

      assertThat(NodeUtil.isObjectResult(parseExpr("a == b"))).isFalse();
      assertThat(NodeUtil.isObjectResult(parseExpr("a != b"))).isFalse();
      assertThat(NodeUtil.isObjectResult(parseExpr("a === b"))).isFalse();
      assertThat(NodeUtil.isObjectResult(parseExpr("a !== b"))).isFalse();
      assertThat(NodeUtil.isObjectResult(parseExpr("a < b"))).isFalse();
      assertThat(NodeUtil.isObjectResult(parseExpr("a > b"))).isFalse();
      assertThat(NodeUtil.isObjectResult(parseExpr("a <= b"))).isFalse();
      assertThat(NodeUtil.isObjectResult(parseExpr("a >= b"))).isFalse();
      assertThat(NodeUtil.isObjectResult(parseExpr("a in b"))).isFalse();
      assertThat(NodeUtil.isObjectResult(parseExpr("a instanceof b"))).isFalse();

      assertThat(NodeUtil.isObjectResult(parseExpr("delete a"))).isFalse();
      assertThat(NodeUtil.isObjectResult(parseExpr("'a'"))).isFalse();
      assertThat(NodeUtil.isObjectResult(parseExpr("'a'+b"))).isFalse();
      assertThat(NodeUtil.isObjectResult(parseExpr("a+'b'"))).isFalse();
      assertThat(NodeUtil.isObjectResult(parseExpr("a+b"))).isFalse();
      assertThat(NodeUtil.isObjectResult(parseExpr("{},true"))).isFalse();

      // "false" here means "unknown"
      assertThat(NodeUtil.isObjectResult(parseExpr("a()"))).isFalse();
      assertThat(NodeUtil.isObjectResult(parseExpr("''.a"))).isFalse();
      assertThat(NodeUtil.isObjectResult(parseExpr("a.b"))).isFalse();
      assertThat(NodeUtil.isObjectResult(parseExpr("a.b()"))).isFalse();
      assertThat(NodeUtil.isObjectResult(parseExpr("a().b()"))).isFalse();
      assertThat(NodeUtil.isObjectResult(parseExpr("a ? true : {}"))).isFalse();

      // These are objects but aren't handled yet.
      assertThat(NodeUtil.isObjectResult(parseExpr("true && {}"))).isFalse();
      assertThat(NodeUtil.isObjectResult(parseExpr("true || {}"))).isFalse();

      // Definitely objects
      assertThat(NodeUtil.isObjectResult(parseExpr("new a.b()"))).isTrue();
      assertThat(NodeUtil.isObjectResult(parseExpr("([true,false])"))).isTrue();
      assertThat(NodeUtil.isObjectResult(parseExpr("({a:true})"))).isTrue();
      assertThat(NodeUtil.isObjectResult(parseExpr("a={}"))).isTrue();
      assertThat(NodeUtil.isObjectResult(parseExpr("[] && {}"))).isTrue();
      assertThat(NodeUtil.isObjectResult(parseExpr("[] || {}"))).isTrue();
      assertThat(NodeUtil.isObjectResult(parseExpr("a ? [] : {}"))).isTrue();
      assertThat(NodeUtil.isObjectResult(parseExpr("{},[]"))).isTrue();
      assertThat(NodeUtil.isObjectResult(parseExpr("/a/g"))).isTrue();
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
          (js) -> NodeUtil.getBestLValueName(NodeUtil.getBestLValue(parseFirst(FUNCTION, js)));
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
     * When the left side is a destructuring pattern, generally it's not possible to identify the
     * RHS for a specific name on the LHS.
     */
    @Test
    public void testGetRValueOfLValueInDestructuringPattern() {
      assertThat(NodeUtil.getRValueOfLValue(getNameNode(parse("var [x] = rhs;"), "x"))).isNull();
      assertThat(NodeUtil.getRValueOfLValue(getNameNode(parse("var [x, y] = rhs;"), "x"))).isNull();
      assertThat(NodeUtil.getRValueOfLValue(getNameNode(parse("var [y, x] = rhs;"), "x"))).isNull();
      assertThat(NodeUtil.getRValueOfLValue(getNameNode(parse("var {x: x} = rhs;"), "x"))).isNull();
      assertThat(NodeUtil.getRValueOfLValue(getNameNode(parse("var {y: x} = rhs;"), "x"))).isNull();

      Node ast = parse("var {x} = rhs;");
      Node x =
          ast.getFirstChild() // VAR
              .getFirstChild() // DESTRUCTURING_LHS
              .getFirstChild() // OBJECT_PATTERN
              .getFirstChild(); // STRING_KEY
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
      assertNode(NodeUtil.getRValueOfLValue(parseFirst(DESTRUCTURING_LHS, "var [x] = 'rhs';")))
          .hasType(Token.STRING);
      assertNode(NodeUtil.getRValueOfLValue(parseFirst(DESTRUCTURING_LHS, "var [x, y] = 'rhs';")))
          .hasType(Token.STRING);
      assertNode(NodeUtil.getRValueOfLValue(parseFirst(DESTRUCTURING_LHS, "var [y, x] = 'rhs';")))
          .hasType(Token.STRING);
      assertNode(NodeUtil.getRValueOfLValue(parseFirst(DESTRUCTURING_LHS, "var {x: x} = 'rhs';")))
          .hasType(Token.STRING);
      assertNode(NodeUtil.getRValueOfLValue(parseFirst(DESTRUCTURING_LHS, "var {y: x} = 'rhs';")))
          .hasType(Token.STRING);
      assertNode(NodeUtil.getRValueOfLValue(parseFirst(DESTRUCTURING_LHS, "var {x} = 'rhs';")))
          .hasType(Token.STRING);
    }

    @Test
    public void testIsNaN() {
      assertThat(NodeUtil.isNaN(parseExpr("NaN"))).isTrue();
      assertThat(NodeUtil.isNaN(parseExpr("Infinity"))).isFalse();
      assertThat(NodeUtil.isNaN(parseExpr("x"))).isFalse();
      assertThat(NodeUtil.isNaN(parseExpr("0/0"))).isTrue();
      assertThat(NodeUtil.isNaN(parseExpr("1/0"))).isFalse();
      assertThat(NodeUtil.isNaN(parseExpr("0/1"))).isFalse();
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
          NodeUtil.getFunctionParameters(parse("function f(x) {}").getFirstChild())
              .getFirstChild());

      Node x =
          NodeUtil.getFunctionParameters(parse("function f(x = 3) {}").getFirstChild())
              .getFirstChild() // x = 3
              .getFirstChild(); // x
      assertLValueNamedX(x);

      assertLValueNamedX(
          parse("({x} = obj)").getFirstFirstChild().getFirstFirstChild().getFirstChild());
      assertLValueNamedX(parse("([x] = obj)").getFirstFirstChild().getFirstFirstChild());
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

      Node x =
          parse("f(...x);") // script
              .getFirstChild() // expr result
              .getFirstChild() // call
              .getLastChild() // spread
              .getFirstChild(); // x
      assertNotLValueNamedX(x);

      x =
          parse("var a = [...x];") // script
              .getFirstChild() // var
              .getFirstChild() // a
              .getFirstChild() // array
              .getFirstChild() // spread
              .getFirstChild(); // x
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
      assertIsConstantDeclaration(true, getNameNodeFrom("const {b: a} = {};", "a"));
      assertIsConstantDeclaration(true, getNameNodeFrom("const {[3]: a} = {};", "a"));
      assertIsConstantDeclaration(true, getNameNodeFrom("const {a: [a]} = {};", "a"));

      assertIsConstantDeclaration(false, getNameNodeFrom("var FOO = 1;", "FOO"));

      assertIsConstantDeclaration(true, constructInferredConstantDeclaration());
    }

    @Test
    public void testIsConstantDeclarations_FunctionClassLiterals() {
      assertIsConstantDeclaration(false, getNameNodeFrom("function Foo() {}", "Foo"));
      assertIsConstantDeclaration(false, getNameNodeFrom("class Foo {}", "Foo"));
    }

    @Test
    public void testIsConstantDeclaration_throwsOnNonDeclarationReferences() {
      assertThrows(
          IllegalStateException.class,
          () -> NodeUtil.isConstantDeclaration(null, getNameNodeFrom("const x = y;", "y")));

      assertThrows(
          IllegalStateException.class,
          () -> NodeUtil.isConstantDeclaration(null, getNameNodeFrom("x;", "x")));

      assertThrows(
          IllegalStateException.class,
          () -> NodeUtil.isConstantDeclaration(null, getNameNodeFrom("const ns = {y: x};", "x")));

      Node constAssignment = parse("/** @const */ x.y = a.b;");
      Node rhs = constAssignment.getFirstFirstChild().getSecondChild();

      assertThrows(
          IllegalArgumentException.class,
          () -> NodeUtil.isConstantDeclaration(NodeUtil.getBestJSDocInfo(rhs), rhs));
    }

    @Test
    public void testIsConstantDeclaration_qnames() {
      Node constAssignment = parse("/** @const */ x.y = a.b;");
      Node assign = constAssignment.getFirstFirstChild();
      assertIsConstantDeclaration(true, assign.getFirstChild());

      Node constByConventionAssignment = parse("x.Y = a.b;");
      assign = constByConventionAssignment.getFirstFirstChild();
      assertIsConstantDeclaration(false, assign.getFirstChild());

      Node nonConstAssignment = parse("x.y = a.b;");
      assign = nonConstAssignment.getFirstFirstChild();
      assertIsConstantDeclaration(false, assign.getFirstChild());

      Node expression = parse("/** @const */ x.y;"); // valid in externs
      assertIsConstantDeclaration(true, expression.getFirstFirstChild());
    }

    @Test
    public void testIsConstantDeclaration_keys() {
      assertIsConstantDeclaration(
          true, getStringKeyNodeFrom("const ns = {/** @const */ x: y};", "x"));
      assertIsConstantDeclaration(
          false, getStringKeyNodeFrom("/** @const */ const ns = {x: y};", "x"));
      assertIsConstantDeclaration(false, getStringKeyNodeFrom("const ns = {x: y};", "x"));
    }

    private void assertIsConstantDeclaration(boolean isConstantDeclaration, Node node) {

      JSDocInfo jsDocInfo = NodeUtil.getBestJSDocInfo(node);
      assertWithMessage("Is %s a constant declaration?", node)
          .that(NodeUtil.isConstantDeclaration(jsDocInfo, node))
          .isEqualTo(isConstantDeclaration);
    }

    /** Returns a NAME node from 'let foo = 1;` */
    private static Node constructInferredConstantDeclaration() {
      Node nameNode = IR.name("foo");
      nameNode.setInferredConstantVar(true);
      IR.script(IR.let(nameNode, IR.number(1))); // attach the expected context to the name.
      return nameNode;
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

      assertThat(NodeUtil.getRootTarget(destructPat)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(nameNodeB)).isSameInstanceAs(destructPat);

      assertThat(NodeUtil.getDeclaringParent(nameNodeA)).isSameInstanceAs(varNode);
      assertThat(NodeUtil.getDeclaringParent(nameNodeB)).isSameInstanceAs(varNode);
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

      assertThat(NodeUtil.getRootTarget(destructPat)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(nameNodeC)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(nameNodeD)).isSameInstanceAs(destructPat);

      assertThat(NodeUtil.getDeclaringParent(nameNodeC)).isSameInstanceAs(varNode);
      assertThat(NodeUtil.getDeclaringParent(nameNodeD)).isSameInstanceAs(varNode);
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

      assertThat(NodeUtil.getRootTarget(destructPat)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(nameNodeB)).isSameInstanceAs(destructPat);

      assertThat(NodeUtil.getDeclaringParent(nameNodeA)).isSameInstanceAs(varNode);
      assertThat(NodeUtil.getDeclaringParent(nameNodeB)).isSameInstanceAs(varNode);
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

      assertThat(NodeUtil.getRootTarget(destructPat)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameInstanceAs(destructPat);

      assertThat(NodeUtil.getDeclaringParent(nameNodeA)).isSameInstanceAs(varNode);
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

      assertThat(NodeUtil.getRootTarget(destructPat)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(nameNodeB)).isSameInstanceAs(destructPat);

      assertThat(NodeUtil.getDeclaringParent(nameNodeB)).isSameInstanceAs(varNode);
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

      assertThat(NodeUtil.getRootTarget(destructPat)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameInstanceAs(destructPat);

      assertThat(NodeUtil.getDeclaringParent(nameNodeA)).isSameInstanceAs(varNode);
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

      assertThat(NodeUtil.getRootTarget(destructPat)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(innerPat)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(nameNodeB)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(nameNodeC)).isSameInstanceAs(destructPat);

      assertThat(NodeUtil.getDeclaringParent(nameNodeA)).isSameInstanceAs(varNode);
      assertThat(NodeUtil.getDeclaringParent(nameNodeB)).isSameInstanceAs(varNode);
      assertThat(NodeUtil.getDeclaringParent(nameNodeC)).isSameInstanceAs(varNode);
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

      assertThat(NodeUtil.getRootTarget(destructPat)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(nameNodeE)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(innerPat)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(nameNodeF)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(nameNodeG)).isSameInstanceAs(destructPat);

      assertThat(NodeUtil.getDeclaringParent(nameNodeE)).isSameInstanceAs(varNode);
      assertThat(NodeUtil.getDeclaringParent(nameNodeF)).isSameInstanceAs(varNode);
      assertThat(NodeUtil.getDeclaringParent(nameNodeG)).isSameInstanceAs(varNode);
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

      assertThat(NodeUtil.getRootTarget(destructPat)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(nameNodeB)).isSameInstanceAs(destructPat);

      assertThat(NodeUtil.getDeclaringParent(nameNodeA)).isSameInstanceAs(varNode);
      assertThat(NodeUtil.getDeclaringParent(nameNodeB)).isSameInstanceAs(varNode);
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

      assertThat(NodeUtil.getRootTarget(destructPat)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(nameNodeC)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(nameNodeD)).isSameInstanceAs(destructPat);

      assertThat(NodeUtil.getDeclaringParent(nameNodeC)).isSameInstanceAs(varNode);
      assertThat(NodeUtil.getDeclaringParent(nameNodeD)).isSameInstanceAs(varNode);
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

      assertThat(NodeUtil.getRootTarget(destructPat)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(nameNodeB)).isSameInstanceAs(destructPat);
    }

    @Test
    public void testDestructuring5() {
      Node root = parse("function fn([a, b] = [c, d]){}");
      Node destructPat = root.getFirstChild().getSecondChild().getFirstFirstChild();
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

      assertThat(NodeUtil.getRootTarget(destructPat)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(nameNodeB)).isSameInstanceAs(destructPat);
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

      assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameInstanceAs(nameNodeA);
      assertThat(NodeUtil.getRootTarget(nameNodeB)).isSameInstanceAs(nameNodeB);
      assertThat(NodeUtil.getRootTarget(nameNodeRest)).isSameInstanceAs(nameNodeRest);

      assertThat(NodeUtil.getDeclaringParent(nameNodeA)).isSameInstanceAs(parameterList);
      assertThat(NodeUtil.getDeclaringParent(nameNodeB)).isSameInstanceAs(parameterList);
      assertThat(NodeUtil.getDeclaringParent(nameNodeRest)).isSameInstanceAs(parameterList);
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

      assertThat(NodeUtil.getRootTarget(forOfTargetPat)).isSameInstanceAs(forOfTargetPat);
      assertThat(NodeUtil.getRootTarget(innerPat)).isSameInstanceAs(forOfTargetPat);
      assertThat(NodeUtil.getRootTarget(nameNodeB)).isSameInstanceAs(forOfTargetPat);
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

      assertThat(NodeUtil.getRootTarget(forInTargetPat)).isSameInstanceAs(forInTargetPat);
      assertThat(NodeUtil.getRootTarget(innerPat)).isSameInstanceAs(forInTargetPat);
      assertThat(NodeUtil.getRootTarget(nameNodeB)).isSameInstanceAs(forInTargetPat);
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

      assertThat(NodeUtil.getRootTarget(destructArr)).isSameInstanceAs(destructArr);
      assertThat(NodeUtil.getRootTarget(destructPat)).isSameInstanceAs(destructArr);
      assertThat(NodeUtil.getRootTarget(nameNodeB)).isSameInstanceAs(destructArr);

      assertThat(NodeUtil.getDeclaringParent(nameNodeB)).isSameInstanceAs(varNode);
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

      assertThat(NodeUtil.getRootTarget(destructPat)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameInstanceAs(destructPat);
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

      assertThat(NodeUtil.getRootTarget(destructPat)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameInstanceAs(destructPat);
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

      assertThat(NodeUtil.getRootTarget(destructPat)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameInstanceAs(destructPat);

      assertThat(NodeUtil.getDeclaringParent(nameNodeA)).isSameInstanceAs(varNode);
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

      assertThat(NodeUtil.getRootTarget(destructPat)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameInstanceAs(destructPat);

      assertThat(NodeUtil.getDeclaringParent(nameNodeA)).isSameInstanceAs(letNode);
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

      assertThat(NodeUtil.getRootTarget(destructPat)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameInstanceAs(destructPat);

      assertThat(NodeUtil.getDeclaringParent(nameNodeA)).isSameInstanceAs(constNode);
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

      assertThat(NodeUtil.getRootTarget(destructPat)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameInstanceAs(destructPat);

      assertThat(NodeUtil.getDeclaringParent(nameNodeA)).isSameInstanceAs(varNode);
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

      assertThat(NodeUtil.getRootTarget(destructPat)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(getProp)).isSameInstanceAs(destructPat);
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

      assertThat(NodeUtil.getRootTarget(destructPat)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameInstanceAs(destructPat);

      assertThat(NodeUtil.getDeclaringParent(nameNodeA)).isSameInstanceAs(varNode);
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

      assertThat(NodeUtil.getRootTarget(destructPat)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameInstanceAs(destructPat);

      assertThat(NodeUtil.getDeclaringParent(nameNodeA)).isSameInstanceAs(varNode);
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

      assertThat(NodeUtil.getRootTarget(arrayPattern)).isSameInstanceAs(arrayPattern);
      assertThat(NodeUtil.getRootTarget(nameNodeA)).isSameInstanceAs(arrayPattern);

      assertThat(NodeUtil.getDeclaringParent(nameNodeA)).isSameInstanceAs(catchNode);
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

      assertThat(NodeUtil.getRootTarget(destructPat)).isSameInstanceAs(destructPat);
      assertThat(NodeUtil.getRootTarget(nameNodeRest)).isSameInstanceAs(destructPat);

      assertThat(NodeUtil.getDeclaringParent(nameNodeRest)).isSameInstanceAs(varNode);
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
      Node expected = IR.getprop(IR.name("ns"), IR.string("prop"));
      assertNode(actual).isEqualTo(expected);
    }

    @Test
    public void testNewQualifiedNameNode2() {
      Compiler compiler = new Compiler();
      CompilerOptions options = new CompilerOptions();
      options.setCodingConvention(new GoogleCodingConvention());
      compiler.init(ImmutableList.<SourceFile>of(), ImmutableList.<SourceFile>of(), options);
      Node actual = NodeUtil.newQName(compiler, "this.prop");
      Node expected = IR.getprop(IR.thisNode(), IR.string("prop"));
      assertNode(actual).isEqualTo(expected);
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
      Node classNode = parseFirst(CLASS, "/** @export */ class Foo {}");
      assertThat(NodeUtil.getBestJSDocInfo(classNode).isExport()).isTrue();

      classNode = parseFirst(CLASS, "/** @export */ var Foo = class {}");
      assertThat(NodeUtil.getBestJSDocInfo(classNode).isExport()).isTrue();

      classNode = parseFirst(CLASS, "/** @export */ var Foo = class Bar {}");
      assertThat(NodeUtil.getBestJSDocInfo(classNode).isExport()).isTrue();
    }

    @Test
    public void testGetBestJsDocInfoForMethods() {
      Node function = parseFirst(FUNCTION, "class C { /** @export */ foo() {} }");
      assertThat(NodeUtil.getBestJSDocInfo(function).isExport()).isTrue();

      function = parseFirst(FUNCTION, "class C { /** @export */ [computedMethod]() {} }");
      assertThat(NodeUtil.getBestJSDocInfo(function).isExport()).isTrue();
    }

    @Test
    public void testGetBestJsDocInfoExport() {
      Node classNode = parseFirst(CLASS, "/** @constructor */ export class Foo {}");
      assertThat(NodeUtil.getBestJSDocInfo(classNode).isConstructor()).isTrue();

      Node function = parseFirst(FUNCTION, "/** @constructor */ export function Foo() {}");
      assertThat(NodeUtil.getBestJSDocInfo(function).isConstructor()).isTrue();

      function = parseFirst(FUNCTION, "/** @constructor */ export var Foo = function() {}");
      assertThat(NodeUtil.getBestJSDocInfo(function).isConstructor()).isTrue();

      function = parseFirst(FUNCTION, "/** @constructor */ export let Foo = function() {}");
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
      assertNode(typeExpr.getRoot()).hasType(Token.ITER_REST);
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
    public void testGetGoogRequireInfo_returnsNullForShadowedRequires() {
      String src = "goog.module('a.b.c'); const Foo = goog.require('d.Foo'); { const Foo = 0; }";

      ParseHelper parser = new ParseHelper();
      Node first = parser.parse(src);
      // Build the three layers of scopes: global scope, module scope, and block scope.
      SyntacticScopeCreator scopeCreator = new SyntacticScopeCreator(parser.compiler);
      Scope globalScope = scopeCreator.createScope(first, null);
      Node module = getNode(first, Token.MODULE_BODY);
      Scope moduleScope = scopeCreator.createScope(module, globalScope);
      Node block = getNode(first, Token.BLOCK);
      Scope blockScope = scopeCreator.createScope(block, moduleScope);

      assertThat(NodeUtil.getGoogRequireInfo("Foo", moduleScope))
          .isEqualTo(GoogRequire.fromNamespace("d.Foo"));
      assertThat(NodeUtil.getGoogRequireInfo("Foo", blockScope)).isNull();
    }

    @Test
    public void testIsConstructor() {
      assertThat(
              NodeUtil.isConstructor(parseFirst(FUNCTION, "/** @constructor */ function Foo() {}")))
          .isTrue();
      assertThat(
              NodeUtil.isConstructor(
                  parseFirst(FUNCTION, "/** @constructor */ var Foo = function() {}")))
          .isTrue();
      assertThat(
              NodeUtil.isConstructor(
                  parseFirst(FUNCTION, "var x = {}; /** @constructor */ x.Foo = function() {}")))
          .isTrue();
      assertThat(NodeUtil.isConstructor(parseFirst(FUNCTION, "class Foo { constructor() {} }")))
          .isTrue();
      assertThat(
              NodeUtil.isConstructor(parseFirst(FUNCTION, "class Foo { static constructor() {} }")))
          .isFalse();
      assertThat(NodeUtil.isConstructor(parseFirst(FUNCTION, "let Foo = { constructor() {} }")))
          .isFalse();

      assertThat(NodeUtil.isConstructor(parseFirst(FUNCTION, "function Foo() {}"))).isFalse();
      assertThat(NodeUtil.isConstructor(parseFirst(FUNCTION, "var Foo = function() {}"))).isFalse();
      assertThat(NodeUtil.isConstructor(parseFirst(FUNCTION, "var x = {}; x.Foo = function() {};")))
          .isFalse();
      assertThat(NodeUtil.isConstructor(parseFirst(FUNCTION, "function constructor() {}")))
          .isFalse();
      assertThat(NodeUtil.isConstructor(parseFirst(FUNCTION, "class Foo { bar() {} }"))).isFalse();
    }

    @Test
    public void testGetEs6ClassConstructorMemberFunctionDef() {
      // distinguish between static and non-static method named "constructor"
      Node constructorMemberFunctionDef =
          NodeUtil.getEs6ClassConstructorMemberFunctionDef(
              parseFirst(
                  CLASS, "class Foo { method() {} constructor() {} static constructor() {} };"));
      assertNode(constructorMemberFunctionDef).isMemberFunctionDef("constructor");
      assertThat(constructorMemberFunctionDef.isStaticMember()).isFalse();

      // static method named "constructor" will not be returned as the constructor
      constructorMemberFunctionDef =
          NodeUtil.getEs6ClassConstructorMemberFunctionDef(
              parseFirst(CLASS, "class Foo { method() {} static constructor() {} }"));
      assertThat(constructorMemberFunctionDef).isNull();
    }

    @Test
    public void testIsGetterOrSetter() {
      Node fnNode =
          parseFirst(FUNCTION, "Object.defineProperty(this, 'bar', {get: function() {}});");
      assertThat(NodeUtil.isGetterOrSetter(fnNode.getParent())).isTrue();

      fnNode = parseFirst(FUNCTION, "Object.defineProperty(this, 'bar', {set: function() {}});");
      assertThat(NodeUtil.isGetterOrSetter(fnNode.getParent())).isTrue();

      fnNode = parseFirst(FUNCTION, "Object.defineProperties(this, {bar: {get: function() {}}});");
      assertThat(NodeUtil.isGetterOrSetter(fnNode.getParent())).isTrue();

      fnNode = parseFirst(FUNCTION, "Object.defineProperties(this, {bar: {set: function() {}}});");
      assertThat(NodeUtil.isGetterOrSetter(fnNode.getParent())).isTrue();

      fnNode = parseFirst(FUNCTION, "var x = {get bar() {}};");
      assertThat(NodeUtil.isGetterOrSetter(fnNode.getParent())).isTrue();

      fnNode = parseFirst(FUNCTION, "var x = {set bar(z) {}};");
      assertThat(NodeUtil.isGetterOrSetter(fnNode.getParent())).isTrue();
    }

    @Test
    public void testIsObjectDefinePropertiesDefinition() {
      assertThat(
              NodeUtil.isObjectDefinePropertiesDefinition(
                  parseFirst(CALL, "Object.defineProperties(this, {});")))
          .isTrue();
      assertThat(
              NodeUtil.isObjectDefinePropertiesDefinition(
                  parseFirst(CALL, "Object.defineProperties(this, foo);")))
          .isTrue();
      assertThat(
              NodeUtil.isObjectDefinePropertiesDefinition(
                  parseFirst(CALL, "$jscomp.global.Object.defineProperties(this, foo);")))
          .isTrue();
      assertThat(
              NodeUtil.isObjectDefinePropertiesDefinition(
                  parseFirst(CALL, "$jscomp$global.Object.defineProperties(this, foo);")))
          .isTrue();

      assertThat(
              NodeUtil.isObjectDefinePropertiesDefinition(
                  parseFirst(CALL, "Object.defineProperties(this, {}, foo);")))
          .isFalse();
      assertThat(
              NodeUtil.isObjectDefinePropertiesDefinition(
                  parseFirst(CALL, "Object.defineProperties(this);")))
          .isFalse();
      assertThat(
              NodeUtil.isObjectDefinePropertiesDefinition(
                  parseFirst(CALL, "Object.defineProperties();")))
          .isFalse();
    }

    @Test
    public void testIsObjectDefinePropertyDefinition() {
      assertThat(
              NodeUtil.isObjectDefinePropertyDefinition(
                  parseFirst(CALL, "Object.defineProperty(this, 'foo', {});")))
          .isTrue();
      assertThat(
              NodeUtil.isObjectDefinePropertyDefinition(
                  parseFirst(CALL, "Object.defineProperty(this, 'foo', foo);")))
          .isTrue();
      assertThat(
              NodeUtil.isObjectDefinePropertyDefinition(
                  parseFirst(CALL, "$jscomp.global.Object.defineProperty(this, 'foo', foo);")))
          .isTrue();
      assertThat(
              NodeUtil.isObjectDefinePropertyDefinition(
                  parseFirst(CALL, "$jscomp$global.Object.defineProperty(this, 'foo', foo);")))
          .isTrue();

      assertThat(
              NodeUtil.isObjectDefinePropertyDefinition(
                  parseFirst(CALL, "Object.defineProperty(this, {});")))
          .isFalse();
      assertThat(
              NodeUtil.isObjectDefinePropertyDefinition(
                  parseFirst(CALL, "Object.defineProperty(this);")))
          .isFalse();
      assertThat(
              NodeUtil.isObjectDefinePropertyDefinition(
                  parseFirst(CALL, "Object.defineProperty();")))
          .isFalse();
    }

    @Test
    public void testGetAllModuleVars() {
      String js =
          "goog.module('m'); var h =2; function g(x, y) {var z; {let a; const b = 1} let c}";
      Compiler compiler = new Compiler();

      compiler.setLifeCycleStage(LifeCycleStage.NORMALIZED);
      SyntacticScopeCreator scopeCreator = new SyntacticScopeCreator(compiler);
      Node ast = parse(js);
      Node moduleNode = parseFirst(MODULE_BODY, js);
      Scope globalScope = Scope.createGlobalScope(ast);
      Map<String, Var> allVariables = new LinkedHashMap<>();
      List<Var> orderedVars = new ArrayList<>();
      NodeUtil.getAllVarsDeclaredInModule(
          moduleNode, allVariables, orderedVars, compiler, scopeCreator, globalScope);
      assertThat(allVariables.keySet()).containsExactly("g", "h");
    }

    @Test
    public void testGetAllModuleVars2() {
      String js = "var glob = 3;";
      Compiler compiler = new Compiler();

      compiler.setLifeCycleStage(LifeCycleStage.NORMALIZED);
      SyntacticScopeCreator scopeCreator = new SyntacticScopeCreator(compiler);
      Node ast = parse(js);
      Scope globalScope = Scope.createGlobalScope(ast);
      Map<String, Var> allVariables = new LinkedHashMap<>();
      List<Var> orderedVars = new ArrayList<>();
      try {
        NodeUtil.getAllVarsDeclaredInModule(
            ast, allVariables, orderedVars, compiler, scopeCreator, globalScope);
        throw new RuntimeException("getAllVarsDeclaredInModule should throw an exception");
      } catch (IllegalStateException e) {
        assertThat(e.getMessage())
            .isEqualTo("getAllVarsDeclaredInModule expects a module body node");
      }
      assertThat(allVariables).isEmpty();
      assertThat(orderedVars).isEmpty();
    }

    @Test
    public void testGetAllVars1() {
      String fnString = "var h; function g(x, y) {var z; h = 2; {let a; const b = 1} let c}";
      Compiler compiler = new Compiler();
      compiler.setLifeCycleStage(LifeCycleStage.NORMALIZED);
      SyntacticScopeCreator scopeCreator = new SyntacticScopeCreator(compiler);

      Node ast = parse(fnString);
      Node functionNode = parseFirst(FUNCTION, fnString);

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
      SyntacticScopeCreator scopeCreator = new SyntacticScopeCreator(compiler);

      Node ast = parse(fnString);
      Node functionNode = parseFirst(FUNCTION, fnString);

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
                  parseExpr("function() {return () => arguments}")))
          .isTrue();
      assertThat(NodeUtil.doesFunctionReferenceOwnArgumentsObject(parseExpr("() => arguments")))
          .isFalse();
    }

    /**
     * When the left side is a destructuring pattern, generally it's not possible to identify the
     * RHS for a specific name on the LHS.
     */
    @Test
    public void testIsExpressionResultUsed() {
      assertThat(NodeUtil.isExpressionResultUsed(getNameNodeFrom("for (x in y) z", "x"))).isTrue();
      assertThat(NodeUtil.isExpressionResultUsed(getNameNodeFrom("for (x in y) z", "y"))).isTrue();
      assertThat(NodeUtil.isExpressionResultUsed(getNameNodeFrom("for (x in y) z", "z"))).isFalse();

      assertThat(NodeUtil.isExpressionResultUsed(getNameNodeFrom("for (x of y) z", "x"))).isTrue();
      assertThat(NodeUtil.isExpressionResultUsed(getNameNodeFrom("for (x of y) z", "y"))).isTrue();
      assertThat(NodeUtil.isExpressionResultUsed(getNameNodeFrom("for (x of y) z", "z"))).isFalse();

      assertThat(NodeUtil.isExpressionResultUsed(getNameNodeFrom("for (x; y; z) a", "x")))
          .isFalse();
      assertThat(NodeUtil.isExpressionResultUsed(getNameNodeFrom("for (x; y; z) a", "y"))).isTrue();
      assertThat(NodeUtil.isExpressionResultUsed(getNameNodeFrom("for (x; y; z) a", "z")))
          .isFalse();

      assertThat(NodeUtil.isExpressionResultUsed(getNameNodeFrom("function f() { return y }", "y")))
          .isTrue();
      assertThat(NodeUtil.isExpressionResultUsed(getNameNodeFrom("function f() { y }", "y")))
          .isFalse();

      assertThat(NodeUtil.isExpressionResultUsed(getNameNodeFrom("var x = ()=> y", "y"))).isTrue();
      assertThat(NodeUtil.isExpressionResultUsed(getNameNodeFrom("var x = ()=>{ y }", "y")))
          .isFalse();

      assertThat(NodeUtil.isExpressionResultUsed(getNameNodeFrom("({a: x = y} = z)", "y")))
          .isTrue();
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

    @Test
    public void testIsCallToString() {
      assertThat(NodeUtil.isCallTo(parseFirst(CALL, "foo()"), "foo")).isTrue();
      assertThat(NodeUtil.isCallTo(parseFirst(CALL, "foo.bar()"), "foo.bar")).isTrue();

      assertThat(NodeUtil.isCallTo(IR.name("foo"), "foo")).isFalse();
      assertThat(NodeUtil.isCallTo(IR.getprop(IR.name("foo"), IR.string("bar")), "foo.bar"))
          .isFalse();
      assertThat(NodeUtil.isCallTo(parseFirst(CALL, "foo.bar()"), "foo")).isFalse();
      assertThat(NodeUtil.isCallTo(parseFirst(CALL, "foo[0]()"), "foo")).isFalse();
    }

    @Test
    public void testIsCallToNode() {
      assertThat(NodeUtil.isCallTo(parseFirst(CALL, "foo()"), IR.name("foo"))).isTrue();
      assertThat(
              NodeUtil.isCallTo(
                  parseFirst(CALL, "foo.bar()"), IR.getprop(IR.name("foo"), IR.string("bar"))))
          .isTrue();

      assertThat(NodeUtil.isCallTo(IR.name("foo"), IR.name("foo"))).isFalse();
      assertThat(
              NodeUtil.isCallTo(
                  IR.getprop(IR.name("foo"), IR.string("bar")),
                  IR.getprop(IR.name("foo"), IR.string("bar"))))
          .isFalse();
      assertThat(NodeUtil.isCallTo(parseFirst(CALL, "foo.bar()"), IR.name("foo"))).isFalse();
      assertThat(NodeUtil.isCallTo(parseFirst(CALL, "foo[0]()"), IR.name("foo"))).isFalse();
    }

    @Test
    public void testIsBundledGoogModule() {
      Node googLoadModule =
          parse("goog.loadModule(function(exports) { goog.module('a.b'); return exports; });");

      Node googName = getNameNode(googLoadModule, "goog");
      Node callNode = googName.getGrandparent();

      assertNode(callNode).hasToken(Token.CALL);
      assertThat(NodeUtil.isBundledGoogModuleCall(callNode)).isTrue();
    }

    @Test
    public void testIsBundledGoogModule_onlyIfInScript() {
      Node callNode =
          IR.call(
              IR.getprop(IR.name("goog"), IR.string("loadModule")),
              IR.string("imaginary module text here"));

      assertThat(NodeUtil.isBundledGoogModuleCall(callNode)).isFalse();

      Node exprResult = IR.exprResult(callNode);
      assertThat(NodeUtil.isBundledGoogModuleCall(callNode)).isFalse();

      IR.script(exprResult); // Call this for its side effect of modifying `exprResult`.
      assertThat(NodeUtil.isBundledGoogModuleCall(callNode)).isTrue();
    }
  }

  @RunWith(JUnit4.class)
  public static class GetStartOfOptChainTests {

    @Test
    public void isStartOfChain() {
      // `expr?.prop`
      Node optChainGet = IR.startOptChainGetprop(IR.name("expr"), IR.string("prop"));

      assertThat(NodeUtil.getStartOfOptChain(optChainGet)).isEqualTo(optChainGet);
    }

    @Test
    public void shortChain() {
      // `expr?.prop1.prop2`
      Node innerGetProp = IR.startOptChainGetprop(IR.name("expr"), IR.string("pro1"));
      Node outterGetProp = IR.continueOptChainGetprop(innerGetProp, IR.string("prop2"));

      assertThat(NodeUtil.getStartOfOptChain(outterGetProp)).isEqualTo(innerGetProp);
    }

    @Test
    public void mixedChain() {
      // `expr().prop1?.prop2()[prop3]`
      Node call = IR.call(IR.name("expr"));
      Node getProp = IR.getprop(call, IR.string("prop1"));
      Node optGetProp = IR.startOptChainGetprop(getProp, IR.string("prop2"));
      Node optCall = IR.continueOptChainCall(optGetProp);
      Node optGetElem = IR.continueOptChainGetelem(optCall, IR.name("prop3"));

      assertThat(NodeUtil.getStartOfOptChain(optGetElem)).isEqualTo(optGetProp);
      assertThat(NodeUtil.getStartOfOptChain(optCall)).isEqualTo(optGetProp);
    }
  }

  @RunWith(JUnit4.class)
  public static class GetEndOfOptChainTests {

    @Test
    public void isEndOfChain() {
      // `expr?.prop`
      Node optChainGet = IR.startOptChainGetprop(IR.name("expr"), IR.string("prop"));

      assertThat(NodeUtil.getEndOfOptChain(optChainGet)).isEqualTo(optChainGet);
    }

    @Test
    public void isEndOfChain_call_innerChain() {
      Node optChainCall = parseExpr("a?.b?.(x?.y)");
      assertThat(optChainCall.isOptChainCall()).isTrue();
      assertThat(optChainCall.isOptionalChainStart()).isTrue();

      Node innerOptChain = optChainCall.getLastChild(); // `x?.y`
      assertThat(innerOptChain.isOptChainGetProp()).isTrue();
      assertThat(innerOptChain.isOptionalChainStart()).isTrue();
      assertThat(NodeUtil.isEndOfOptChain(innerOptChain)).isTrue();
    }

    @Test
    public void isEndOfChain_getProp_innerChain() {
      Node optChainGetProp = parseExpr("a?.b.x?.y");
      assertThat(optChainGetProp.isOptChainGetProp()).isTrue();
      assertThat(optChainGetProp.isOptionalChainStart()).isTrue();
      assertThat(NodeUtil.isEndOfOptChain(optChainGetProp)).isTrue();

      // Check that `a?.b.x` is not the start of optChain, but it ends the chain `a?.b`.
      Node innerOptChain = optChainGetProp.getFirstChild(); // `a?.b.x`
      assertThat(innerOptChain.isOptChainGetProp()).isTrue();
      assertThat(innerOptChain.isOptionalChainStart()).isFalse();
      assertThat(NodeUtil.isEndOfOptChain(innerOptChain)).isTrue();

      // Check that `a?.b` is the start of optChain but not the end
      Node innerMostOptChain = innerOptChain.getFirstChild(); // `a?.b`
      assertThat(innerMostOptChain.isOptChainGetProp()).isTrue();
      assertThat(innerMostOptChain.isOptionalChainStart()).isTrue();
      assertThat(NodeUtil.isEndOfOptChain(innerMostOptChain)).isFalse();
    }

    @Test
    public void isEndOfChain_call_innerChain2() {
      Node optChainCall = parseExpr("a?.b?.(c.x?.y)");
      assertThat(optChainCall.isOptChainCall()).isTrue();
      assertThat(optChainCall.isOptionalChainStart()).isTrue();

      // Check that `c.x?.y` is the start of a new chain and also its end
      Node innerOptChain = optChainCall.getLastChild();
      assertThat(innerOptChain.isOptChainGetProp()).isTrue();
      assertThat(innerOptChain.isOptionalChainStart()).isTrue();
      assertThat(NodeUtil.isEndOfOptChain(innerOptChain)).isTrue();
    }

    @Test
    public void isEndOfChain_callAndGetProp_innerChain() {
      Node optChainGetProp = parseExpr("a?.b(x?.y).c");
      assertThat(optChainGetProp.isOptChainGetProp()).isTrue();
      assertThat(optChainGetProp.isOptionalChainStart()).isFalse();

      // Check that `a?.b(x?.y)` is not the start or end of the chain
      Node optChainCall = optChainGetProp.getFirstChild();
      assertThat(optChainCall.isOptChainCall()).isTrue();
      assertThat(optChainCall.isOptionalChainStart()).isFalse();
      assertThat(NodeUtil.isEndOfOptChain(optChainCall)).isFalse();

      // Check that `x?.y` is the start and end of the chain
      Node innerOptChain = optChainCall.getLastChild();
      assertThat(innerOptChain.isOptChainGetProp()).isTrue();
      assertThat(innerOptChain.isOptionalChainStart()).isTrue();
      assertThat(NodeUtil.isEndOfOptChain(innerOptChain)).isTrue();
    }

    @Test
    public void isEndOfChain_callAndGetProp_innerChain_multipleInnerArgs() {
      Node optChainGetProp = parseExpr("a?.b(x?.y, c, d).e");
      assertThat(optChainGetProp.isOptChainGetProp()).isTrue();
      assertThat(optChainGetProp.isOptionalChainStart()).isFalse();

      // Check that `a?.b(x?.y, c, d)` is not the start or end of its chain
      Node optChainCall = optChainGetProp.getFirstChild();
      assertThat(optChainCall.isOptChainCall()).isTrue();
      assertThat(optChainCall.isOptionalChainStart()).isFalse();
      assertThat(NodeUtil.isEndOfOptChain(optChainCall)).isFalse();

      // Check that `x?.y` is the start and end of its chain
      Node innerOptChain = optChainCall.getSecondChild();
      assertThat(innerOptChain.isOptChainGetProp()).isTrue();
      assertThat(innerOptChain.isOptionalChainStart()).isTrue();
      assertThat(NodeUtil.isEndOfOptChain(innerOptChain)).isTrue();
    }

    @Test
    public void shortChain() {
      // `expr?.prop1.prop2`
      Node innerGetProp = IR.startOptChainGetprop(IR.name("expr"), IR.string("pro1"));
      Node outerGetProp = IR.continueOptChainGetprop(innerGetProp, IR.string("prop2"));

      assertThat(NodeUtil.getEndOfOptChain(innerGetProp)).isEqualTo(outerGetProp);
    }

    @Test
    public void twoChains() {
      // `expr()?.prop1.prop2()?.[prop3]`
      Node call = IR.call(IR.name("expr"));
      Node startOptGetProp = IR.startOptChainGetprop(call, IR.string("prop1"));
      Node optGetProp = IR.continueOptChainGetprop(startOptGetProp, IR.string("prop2"));
      Node optCall = IR.continueOptChainCall(optGetProp);
      IR.startOptChainGetelem(optCall, IR.name("prop3"));

      assertThat(NodeUtil.getEndOfOptChain(startOptGetProp)).isEqualTo(optCall);
    }

    @Test
    public void breakingOutOfOptChain() {
      // `(expr?.prop1.prop2).prop3`
      Node startOptGetProp = IR.startOptChainGetprop(IR.name("expr"), IR.string("prop1"));
      Node optGetProp = IR.continueOptChainGetprop(startOptGetProp, IR.string("prop2"));
      IR.getprop(optGetProp, IR.string("prop3"));

      assertThat(NodeUtil.getEndOfOptChain(startOptGetProp)).isEqualTo(optGetProp);
    }
  }

  @RunWith(JUnit4.class)
  public static class NodeTraversalTests {

    @Test
    public void testVisitPreOrder() {
      NodeUtil.Visitor visitor = Mockito.mock(NodeUtil.Visitor.class);
      NodeUtil.visitPreOrder(buildTestTree(), visitor);
      assertThat(getNodeStringsFromMockVisitor(visitor))
          .containsExactly("A", "B", "C", "D", "E", "F", "G")
          .inOrder();
    }

    @Test
    public void testVisitPostOrder() {
      NodeUtil.Visitor visitor = Mockito.mock(NodeUtil.Visitor.class);
      NodeUtil.visitPostOrder(buildTestTree(), visitor);
      assertThat(getNodeStringsFromMockVisitor(visitor))
          .containsExactly("C", "D", "B", "F", "G", "E", "A")
          .inOrder();
    }

    @Test
    public void testIteratePreOrder() {
      List<String> nodeNames =
          Streams.stream(NodeUtil.preOrderIterable(buildTestTree()))
              .map(n -> n.getString())
              .collect(Collectors.toList());

      assertThat(nodeNames).containsExactly("A", "B", "C", "D", "E", "F", "G").inOrder();
    }

    @Test
    public void testIteratePreOrderWithPredicate() {
      Predicate<Node> isNotE = n -> !n.getString().equals("E");
      List<String> nodeNames =
          Streams.stream(NodeUtil.preOrderIterable(buildTestTree(), isNotE))
              .map(n -> n.getString())
              .collect(Collectors.toList());

      assertThat(nodeNames).containsExactly("A", "B", "C", "D", "E").inOrder();
    }

    @Test
    public void addFeatureToScriptUpdatesCompilerFeatureSet() {
      Node scriptNode = parse("");
      Compiler compiler = new Compiler();
      compiler.setFeatureSet(FeatureSet.BARE_MINIMUM);
      NodeUtil.addFeatureToScript(scriptNode, Feature.MODULES, compiler);

      assertThat(NodeUtil.getFeatureSetOfScript(scriptNode))
          .isEqualTo(FeatureSet.BARE_MINIMUM.with(Feature.MODULES));
      assertFS(compiler.getFeatureSet()).equals(FeatureSet.BARE_MINIMUM.with(Feature.MODULES));
    }

    /**
     * Builds a node tree of string {@link com.google.javascript.rhino.Node} with string labels that
     * can be used to verify order in tests.
     *
     * <p>The resultant tree looks like this.
     *
     * <pre>
     *      A
     *    ↙   ↘
     *   B     E
     *  ↙ ↘    ↙ ↘
     * C   D  F   G
     * </pre>
     */
    private static Node buildTestTree() {
      Node a = Node.newString("A");

      Node b = Node.newString("B");
      b.addChildToBack(Node.newString("C"));
      b.addChildToBack(Node.newString("D"));
      a.addChildToBack(b);

      Node e = Node.newString("E");
      e.addChildToBack(Node.newString("F"));
      e.addChildToBack(Node.newString("G"));
      a.addChildToBack(e);

      return a;
    }

    private static ImmutableList<String> getNodeStringsFromMockVisitor(
        NodeUtil.Visitor mockVisitor) {
      ArgumentCaptor<Node> captor = ArgumentCaptor.forClass(Node.class);
      verify(mockVisitor, Mockito.atLeastOnce()).visit(captor.capture());
      return captor.getAllValues().stream()
          .map(n -> n.getString())
          .collect(ImmutableList.toImmutableList());
    }
  }

  @RunWith(Parameterized.class)
  public static final class GoogRequireInfoTest {

    @Parameters(name = "src={0}, name={1}, GoogRequire={2}")
    public static Iterable<Object[]> cases() {
      return ImmutableList.copyOf(
          new Object[][] {
            {
              "goog.module('a.b.c'); const {Bar} = goog.require('d.Foo');",
              "Bar",
              GoogRequire.fromNamespaceAndProperty("d.Foo", "Bar")
            },
            {"goog.module('a.b.c'); const {Bar} = goog.require('d.Foo');", "Foo", null},
            {
              "goog.module('a.b.c'); const {Bar: BarLocal} = goog.require('d.Foo');",
              "BarLocal",
              GoogRequire.fromNamespaceAndProperty("d.Foo", "Bar")
            },
            {
              "goog.module('a.b.c'); const {Bar: BarLocal} =" + " goog.require('d.Foo');",
              "Bar",
              null
            },
            {
              "goog.module('a.b.c'); const Foo = goog.require('d.Foo');",
              "Foo",
              GoogRequire.fromNamespace("d.Foo")
            },
            {
              "goog.module('a.b.c'); const dFoo = goog.require('d.Foo');",
              "dFoo",
              GoogRequire.fromNamespace("d.Foo")
            },
            {
              "goog.module('a.b.c'); const Foo = goog.requireType('d.Foo');",
              "Foo",
              GoogRequire.fromNamespace("d.Foo")
            },
            {
              "goog.module('a.b.c'); const {Bar} = goog.requireType('d.Foo');",
              "Bar",
              GoogRequire.fromNamespaceAndProperty("d.Foo", "Bar")
            },
            // Test that non-requires just return null.
            {"goog.module('a.b.c'); let Foo;", "Foo", null},
            {"goog.module('a.b.c'); let [Foo] = arr;", "Foo", null},
            {"goog.module('a.b.c'); let {Bar: {Foo}} = obj;", "Foo", null},
            {"goog.module('a.b.c'); const Foo = 0;", "Foo", null},
            {"const Foo = goog.require('d.Foo');", "Foo", null} // NB: Foo will be null here.
          });
    }

    @Parameter(0)
    public String src;

    @Parameter(1)
    public String name;

    @Parameter(2)
    public GoogRequire require;

    @Test
    public void test() {
      ParseHelper parser = new ParseHelper();
      Node first = parser.parse(src);
      SyntacticScopeCreator scopeCreator = new SyntacticScopeCreator(parser.compiler);
      Scope globalScope = scopeCreator.createScope(first, null);
      Node module = getNodeOrNull(first, Token.MODULE_BODY);
      Scope localScope =
          module != null ? scopeCreator.createScope(module, globalScope) : globalScope;
      assertThat(NodeUtil.getGoogRequireInfo(name, localScope)).isEqualTo(require);
    }
  }

  @RunWith(Parameterized.class)
  public static final class ReferencesReceiverTest {
    @Parameters(name = "\"{0}\"")
    public static Iterable<Object[]> cases() {
      ImmutableMap<String, Boolean> templateToDefinesOwnReceiver =
          ImmutableMap.<String, Boolean>builder()
              //
              .put("      (x = (%s)) => {}", false)
              .put("      (      ) => (%s)", false)
              .put("async (x = (%s)) => {}", false)
              .put("async (      ) => (%s)", false)
              .put("      function   f(        ) { (%s); }", true)
              .put("async function   f(        ) { (%s); }", true)
              .put("async function  *f(        ) { (%s); }", true)
              .put("      function  *f(        ) { (%s); }", true)
              .put("      function   f(x = (%s)) {       }", true)
              .put("      function  *f(x = (%s)) {       }", true)
              .put("async function   f(x = (%s)) {       }", true)
              .put("async function  *f(x = (%s)) {       }", true)
              .put("class F {        f(        ) { (%s); } }", true)
              .put("class F {       *f(        ) { (%s); } }", true)
              .put("class F { async  f(        ) { (%s); } }", true)
              .put("class F { async *f(        ) { (%s); } }", true)
              .put("class F {        f(x = (%s)) {       } }", true)
              .put("class F {       *f(x = (%s)) {       } }", true)
              .put("class F { async  f(x = (%s)) {       } }", true)
              .put("class F { async *f(x = (%s)) {       } }", true)
              .put("({               f(        ) { (%s); } })", true)
              .put("({              *f(        ) { (%s); } })", true)
              .put("({        async  f(        ) { (%s); } })", true)
              .put("({        async *f(        ) { (%s); } })", true)
              .put("({               f(x = (%s)) {       } })", true)
              .put("({              *f(x = (%s)) {       } })", true)
              .put("({        async  f(x = (%s)) {       } })", true)
              .put("({        async *f(x = (%s)) {       } })", true)
              //
              .build();

      ImmutableMap<String, Boolean> exprToUsesReceiver =
          ImmutableMap.<String, Boolean>builder()
              //
              .put("this", true)
              .put("1 || this", true)
              .put("{ x: this, }", true)
              .put("1", false)
              //
              .build();

      ImmutableList.Builder<Object[]> cases = ImmutableList.builder();
      templateToDefinesOwnReceiver.forEach(
          (outerTemplate, outerReceiver) -> {
            templateToDefinesOwnReceiver.forEach(
                (innerTemplate, innerReceiver) -> {
                  exprToUsesReceiver.forEach(
                      (expr, usesReceiver) -> {
                        String caseSrc =
                            SimpleFormat.format(
                                outerTemplate, SimpleFormat.format(innerTemplate, expr));
                        cases.add(
                            new Object[] {
                              caseSrc,
                              // refToEnclosing
                              !outerReceiver && !innerReceiver && usesReceiver,
                              // refToOuterFnOwn
                              outerReceiver && !innerReceiver && usesReceiver,
                            });
                      });
                });
          });

      /**
       * Add a few cases using `super` to check it behaves the same.
       *
       * <p>These aren't exhaustive becuase it's hard to construct valid strings that use `super`.
       */
      cases.add(
          new Object[][] {
            {"class F { f() { super.a() } }", false, true},
            {"class F { f() { () => super.a(); } }", false, true},
            {"() => class F { f() { super.a(); } }", false, false},
            {"({ f() { super.a() } })", false, true},
            {"({ f() { () => super.a(); } })", false, true},
            {"() => ({ f() { super.a(); } })", false, false},
          });

      return cases.build();
    }

    @Parameter(0)
    public String js;

    @Parameter(1)
    public boolean refToEnclosing;

    @Parameter(2)
    public boolean refToOuterFnOwn;

    @Test
    public void testReferencesEnclosingReceiver_ofScript() {
      Node node = parseFirst(SCRIPT, js);
      assertThat(NodeUtil.referencesEnclosingReceiver(node)).isEqualTo(this.refToEnclosing);
    }

    @Test
    public void testReferencesEnclosingReceiver_ofFn() {
      Node node = parseFirst(FUNCTION, js);
      assertThat(NodeUtil.referencesEnclosingReceiver(node)).isEqualTo(this.refToEnclosing);
    }

    @Test
    public void testReferencesOwnReceiver_ofFn() {
      Node node = parseFirst(FUNCTION, js);
      assertThat(NodeUtil.referencesOwnReceiver(node)).isEqualTo(this.refToOuterFnOwn);
    }
  }

  @RunWith(Parameterized.class)
  public static final class GetRValueOfLValueTest {

    @Parameters(name = "{0} in \"{1}\"")
    public static Iterable<Object[]> cases() {
      return ImmutableList.copyOf(
          new Object[][] {
            // CLASS_MEMBERS
            {MEMBER_FUNCTION_DEF, "constructor() { }"},
            {MEMBER_FUNCTION_DEF, "foo() { }"},
            {MEMBER_FUNCTION_DEF, "static foo() { }"},
            {GETTER_DEF, "get foo() { }"},
            {GETTER_DEF, "static get foo() { }"},
            {SETTER_DEF, "set foo(x) { }"},
            {SETTER_DEF, "static set foo(x) { }"},
          });
    }

    @Parameter(0)
    public Token token;

    @Parameter(1)
    public String member;

    @Test
    public void test() {
      Node node = parseFirst(token, "class F { " + member + " }");
      assertNode(NodeUtil.getRValueOfLValue(node)).hasType(Token.FUNCTION);
    }
  }

  @RunWith(Parameterized.class)
  public static final class IteratesImpureIterableTest {

    @Parameters(name = "{0} in \"{1}\"")
    public static Iterable<Object[]> cases() {
      return ImmutableList.copyOf(
          new Object[][] {
            // ITER_SPREAD < ARRAYLIT
            {ITER_SPREAD, "[...[]]", false},
            {ITER_SPREAD, "[...[danger()]]", false},
            {ITER_SPREAD, "[...'lit']", false},
            {ITER_SPREAD, "[...`template`]", false},
            {ITER_SPREAD, "[...`template ${sub}`]", false},
            {ITER_SPREAD, "[...`template ${danger()}`]", false},
            {ITER_SPREAD, "[...danger]", true},
            {ITER_SPREAD, "[...danger()]", true},
            {ITER_SPREAD, "[...5]", true},
            // ITER_SPREAD < CALL
            {ITER_SPREAD, "foo(...[])", false},
            {ITER_SPREAD, "foo(...[danger()])", false},
            {ITER_SPREAD, "foo(...'lit')", false},
            {ITER_SPREAD, "foo(...`template`)", false},
            {ITER_SPREAD, "foo(...`template ${safe}`)", false},
            {ITER_SPREAD, "foo(...`template ${danger()}`)", false},
            {ITER_SPREAD, "foo(...danger)", true},
            {ITER_SPREAD, "foo(...danger())", true},
            {ITER_SPREAD, "foo(...5)", true},
            // ITER_SPREAD < NEW
            {ITER_SPREAD, "new foo(...[])", false},
            {ITER_SPREAD, "new foo(...[danger()])", false},
            {ITER_SPREAD, "new foo(...'lit')", false},
            {ITER_SPREAD, "new foo(...`template`)", false},
            {ITER_SPREAD, "new foo(...`template ${safe}`)", false},
            {ITER_SPREAD, "new foo(...`template ${danger()}`)", false},
            {ITER_SPREAD, "new foo(...danger)", true},
            {ITER_SPREAD, "new foo(...danger())", true},
            {ITER_SPREAD, "new foo(...5)", true},
            // ITER_REST < ARRAY_PATTERN
            {ITER_REST, "const [...rest] = []", true},
            {ITER_REST, "const [...rest] = 'lit'", true},
            {ITER_REST, "const [...rest] = `template`", true},
            {ITER_REST, "const [...rest] = safe", true},
            {ITER_REST, "function f([...rest]) { }", true},
            {ITER_REST, "const [[...rest]] = safe", true},
            {ITER_REST, "const {key: [...rest]} = safe", true},
            // ITER_REST < PARAM_LIST
            {ITER_REST, "function f(...x) { }", false},
            {ITER_REST, "function f(a, ...x) { }", false},
            {ITER_REST, "async function f(...x) { }", false},
            {ITER_REST, "function* f(...x) { }", false},
            {ITER_REST, "async function* f(...x) { }", false},
            {ITER_REST, "((...x) => { })", false},
            // FOR_OF
            {FOR_OF, "for (let x of []) {}", false},
            {FOR_OF, "for (let x of [danger()]) {}", false},
            {FOR_OF, "for (let x of 'lit') {}", false},
            {FOR_OF, "for (let x of `template`) {}", false},
            {FOR_OF, "for (let x of `template ${safe}`) {}", false},
            {FOR_OF, "for (let x of `template ${danger()}`) {}", false},
            {FOR_OF, "for (let x of danger) {}", true},
            {FOR_OF, "for (let x of danger()) {}", true},
            {FOR_OF, "for (let x of 5) {}", true},
            // FOR_AWAIT_OF
            {FOR_AWAIT_OF, "(async()=>{ for await (let x of []) {} })", false},
            {FOR_AWAIT_OF, "(async()=>{ for await (let x of [danger()]) {} })", false},
            {FOR_AWAIT_OF, "(async()=>{ for await (let x of 'literal') {} })", false},
            {FOR_AWAIT_OF, "(async()=>{ for await (let x of `template`) {} })", false},
            {FOR_AWAIT_OF, "(async()=>{ for await (let x of `t ${safe}`) {} })", false},
            {FOR_AWAIT_OF, "(async()=>{ for await (let x of `t ${dn()}`) {} })", false},
            {FOR_AWAIT_OF, "(async()=>{ for await (let x of danger) {} })", true},
            {FOR_AWAIT_OF, "(async()=>{ for await (let x of danger()) {} })", true},
            {FOR_AWAIT_OF, "(async()=>{ for await (let x of 5) {} })", true},
            // YIELD*
            {YIELD, "function* f() { yield* []; }", false},
            {YIELD, "function* f() { yield* [danger()]; }", false},
            {YIELD, "function* f() { yield* 'lit'; }", false},
            {YIELD, "function* f() { yield* `template`; }", false},
            {YIELD, "function* f() { yield* `template ${sub}`; }", false},
            {YIELD, "function* f() { yield* `template ${danger()}`; }", false},
            {YIELD, "function* f() { yield* danger; }", true},
            {YIELD, "function* f() { yield* danger(); }", true},
            {YIELD, "function* f() { yield* 5; }", true},
            // YIELD
            {YIELD, "function* f() { yield danger(); }", false},
          });
    }

    private final Token token;
    private final String source;
    private final boolean expectation;

    public IteratesImpureIterableTest(Token token, String source, boolean expectation) {
      this.token = token;
      this.source = source;
      this.expectation = expectation;
    }

    @Test
    public void test() {
      Node node = parseFirst(token, source);
      assertThat(NodeUtil.iteratesImpureIterable(node)).isEqualTo(expectation);
    }
  }

  @RunWith(Parameterized.class)
  public static final class CanBeSideEffectedTest {

    @Parameters(name = "{0} in \"{1}\"")
    public static Iterable<Object[]> cases() {
      return ImmutableList.copyOf(
          new Object[][] {
            // TODO: Expand test cases for more node types.

            // SUPER
            {SUPER, "super()", false},
            {SUPER, "super.foo()", false},
            {CALL, "super()", true},
            {CALL, "super.foo()", true},

            // OPTCHAIN
            {OPTCHAIN_CALL, "x?.()", true},
            {OPTCHAIN_GETPROP, "x?.y", true},
            {OPTCHAIN_GETELEM, "x?.[y]", true},
          });
    }

    private final Token token;
    private final String source;
    private final boolean expectation;

    public CanBeSideEffectedTest(Token token, String source, boolean expectation) {
      this.token = token;
      this.source = source;
      this.expectation = expectation;
    }

    @Test
    public void test() {
      Node node = parseFirst(token, source);
      assertThat(NodeUtil.canBeSideEffected(node)).isEqualTo(expectation);
    }
  }

  private static Node getNameNodeFrom(String code, String name) {
    Node ast = parse(code);
    Node nameNode = getNameNode(ast, name);
    return nameNode;
  }

  private static Node getStringKeyNodeFrom(String code, String name) {
    Node ast = parse(code);
    Node stringKeyNode = getStringNode(ast, name, Token.STRING_KEY);
    return stringKeyNode;
  }

  private static boolean executedOnceTestCase(String code) {
    Node nameNode = getNameNodeFrom(code, "x");
    return NodeUtil.isExecutedExactlyOnce(nameNode);
  }

  private static String getFunctionLValue(String js) {
    Node lVal = NodeUtil.getBestLValue(parseFirst(FUNCTION, js));
    return lVal == null ? null : lVal.getString();
  }

  private static boolean functionIsRValueOfAssign(String js) {
    Node ast = parse(js);
    Node nameNode = getNameNode(ast, "x");
    Node funcNode = getNode(ast, FUNCTION);
    assertWithMessage("No function node to test").that(funcNode).isNotNull();
    return funcNode == NodeUtil.getRValueOfLValue(nameNode);
  }

  private static void testFunctionName(String js, String expected) {
    assertThat(NodeUtil.getNearestFunctionName(parseFirst(FUNCTION, js))).isEqualTo(expected);
  }

  /**
   * @param js JavaScript node to be passed to {@code NodeUtil.findLhsNodesInNode}. Must be either
   *     an EXPR_RESULT containing an assignment operation (e.g. =, +=, /=, etc) in which case the
   *     assignment node will be passed to {@code NodeUtil.findLhsNodesInNode}, or a VAR, LET, or
   *     CONST statement, in which case the declaration statement will be passed.
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

  private static Node getNameNode(Node n, String name) {
    return getStringNode(n, name, Token.NAME);
  }

  private static Node getStringNode(Node n, String name, Token nodeType) {
    if (nodeType.equals(n.getToken()) && n.getString().equals(name)) {
      return n;
    }
    for (Node c : n.children()) {
      Node result = getStringNode(c, name, nodeType);
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

  private static boolean isValidPropertyName(String s) {
    return NodeUtil.isValidPropertyName(FeatureSet.ES3, s);
  }

  private static boolean isValidQualifiedName(String s) {
    return NodeUtil.isValidQualifiedName(FeatureSet.ES3, s);
  }
}
