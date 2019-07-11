/*
 * Copyright 2019 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.DiagnosticGroups.ES5_STRICT;
import static com.google.javascript.rhino.Token.ADD;
import static com.google.javascript.rhino.Token.ARRAYLIT;
import static com.google.javascript.rhino.Token.ASSIGN;
import static com.google.javascript.rhino.Token.ASSIGN_ADD;
import static com.google.javascript.rhino.Token.AWAIT;
import static com.google.javascript.rhino.Token.BITOR;
import static com.google.javascript.rhino.Token.CALL;
import static com.google.javascript.rhino.Token.CLASS;
import static com.google.javascript.rhino.Token.COMMA;
import static com.google.javascript.rhino.Token.COMPUTED_PROP;
import static com.google.javascript.rhino.Token.DEC;
import static com.google.javascript.rhino.Token.DEFAULT_VALUE;
import static com.google.javascript.rhino.Token.DELPROP;
import static com.google.javascript.rhino.Token.FOR_AWAIT_OF;
import static com.google.javascript.rhino.Token.FOR_IN;
import static com.google.javascript.rhino.Token.FOR_OF;
import static com.google.javascript.rhino.Token.FUNCTION;
import static com.google.javascript.rhino.Token.GETELEM;
import static com.google.javascript.rhino.Token.GETPROP;
import static com.google.javascript.rhino.Token.GETTER_DEF;
import static com.google.javascript.rhino.Token.HOOK;
import static com.google.javascript.rhino.Token.IF;
import static com.google.javascript.rhino.Token.INC;
import static com.google.javascript.rhino.Token.MEMBER_FUNCTION_DEF;
import static com.google.javascript.rhino.Token.NAME;
import static com.google.javascript.rhino.Token.NEW;
import static com.google.javascript.rhino.Token.NUMBER;
import static com.google.javascript.rhino.Token.OBJECTLIT;
import static com.google.javascript.rhino.Token.OR;
import static com.google.javascript.rhino.Token.REGEXP;
import static com.google.javascript.rhino.Token.REST;
import static com.google.javascript.rhino.Token.SETTER_DEF;
import static com.google.javascript.rhino.Token.SPREAD;
import static com.google.javascript.rhino.Token.STRING;
import static com.google.javascript.rhino.Token.STRING_KEY;
import static com.google.javascript.rhino.Token.SUB;
import static com.google.javascript.rhino.Token.TAGGED_TEMPLATELIT;
import static com.google.javascript.rhino.Token.THROW;
import static com.google.javascript.rhino.Token.VOID;
import static com.google.javascript.rhino.Token.WHILE;
import static com.google.javascript.rhino.Token.YIELD;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.AccessorSummary.PropertyAccessKind;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.parsing.parser.util.format.SimpleFormat;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Unit tests for {@link AstAnalyzer}.
 *
 * <p>IMPORTANT: Do not put {@code {@literal @}Test} methods directly in this class, they must be
 * inside an inner class or they won't be executed, because we're using the {@code Enclosed} JUnit
 * runner.
 */
@RunWith(Enclosed.class)
public class AstAnalyzerTest {

  /** Provides methods for parsing and accessing the compiler used for the parsing. */
  private static class ParseHelper {
    private boolean hasRegExpGlobalReferences = false;
    private boolean hasFakeGetterAndSetter = false;
    private Compiler compiler = null;

    /**
     * Tell the compiler to behave as if it has (or has not) seen any references to the RegExp
     * global properties, which are modified when matches are performed.
     */
    ParseHelper setHasRegExpGlobalReferences(boolean hasRegExpGlobalReferences) {
      this.hasRegExpGlobalReferences = hasRegExpGlobalReferences;
      return this;
    }

    ParseHelper registerFakeGetterAndSetter() {
      this.hasFakeGetterAndSetter = true;
      return this;
    }

    private void createNewCompiler() {
      CompilerOptions options = new CompilerOptions();
      options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);

      // To allow octal literals such as 0123 to be parsed.
      options.setStrictModeInput(false);
      options.setWarningLevel(ES5_STRICT, CheckLevel.OFF);

      compiler = new Compiler();
      compiler.initOptions(options);

      compiler.setHasRegExpGlobalReferences(hasRegExpGlobalReferences);
      if (hasFakeGetterAndSetter) {
        compiler.setAccessorSummary(
            AccessorSummary.create(
                ImmutableMap.of(
                    "getter", PropertyAccessKind.GETTER_ONLY, //
                    "setter", PropertyAccessKind.SETTER_ONLY)));
      }
    }

    Node parse(String js) {
      createNewCompiler();

      Node n = compiler.parseTestCode(js);
      assertThat(compiler.getErrors()).isEmpty();
      return n;
    }

    /**
     * Parse a string of JavaScript and return the first node found with the given token in a
     * preorder DFS.
     */
    Node parseFirst(Token token, String js) {
      Node rootNode = this.parse(js);
      checkState(rootNode.isScript(), rootNode);
      Node firstNodeWithToken = getFirstNode(rootNode, token);
      if (firstNodeWithToken == null) {
        throw new AssertionError(SimpleFormat.format("No %s node found in:\n %s", token, js));
      } else {
        return firstNodeWithToken;
      }
    }

    /**
     * Returns the parsed expression (e.g. returns a NAME given 'a').
     *
     * <p>Presumes that the given JavaScript is an expression.
     */
    Node parseExpr(String js) {
      Node script = parse("(" + js + ");"); // Parens force interpretation as an expression.
      return script
          .getFirstChild() // EXPR_RESULT
          .getFirstChild(); // expr
    }

    private AstAnalyzer getAstAnalyzer() {
      return new AstAnalyzer(compiler, false);
    }
  }

  /**
   * Does a preorder DFS, returning the first node found that has the given token.
   *
   * @return the first matching node
   * @throws AssertionError if no matching node was found.
   */
  private static Node getFirstNode(Node root, Token token) {
    if (root.getToken() == token) {
      return root;
    } else {
      for (Node child = root.getFirstChild(); child != null; child = child.getNext()) {
        Node matchingNode = getFirstNode(child, token);
        if (matchingNode != null) {
          return matchingNode;
        }
      }
    }
    return null;
  }

  @RunWith(Parameterized.class)
  public static final class MayEffectMutableStateTest {
    @Parameter(0)
    public String jsExpression;

    @Parameter(1)
    public Token token;

    @Parameter(2)
    public Boolean expectedResult;

    // Always include the index. If two cases have the same name, only one will be executed.
    @Parameters(name = "#{index} {1} node in ({0}) -> {2}")
    public static Iterable<Object[]> cases() {
      return ImmutableList.copyOf(
          new Object[][] {
            {"i++", INC, true},
            {"[b, [a, i++]]", ARRAYLIT, true},
            {"i=3", ASSIGN, true},
            {"[0, i=3]", ARRAYLIT, true},
            {"b()", CALL, true},
            {"void b()", VOID, true},
            {"[1, b()]", ARRAYLIT, true},
            {"b.b=4", ASSIGN, true},
            {"b.b--", DEC, true},
            {"i--", DEC, true},
            {"a[0][i=4]", GETELEM, true},
            {"a += 3", ASSIGN_ADD, true},
            {"a, b, z += 4", COMMA, true},
            {"a ? c : d++", HOOK, true},
            {"a + c++", ADD, true},
            {"a + c - d()", SUB, true},
            {"function foo() {}", FUNCTION, true},
            {"while(true);", WHILE, true},
            {"if(true){a()}", IF, true},
            {"if(true){a}", IF, false},
            {"(function() { })", FUNCTION, true},
            {"(function() { i++ })", FUNCTION, true},
            {"[function a(){}]", ARRAYLIT, true},
            {"a", NAME, false},
            {"[b, c [d, [e]]]", ARRAYLIT, true},
            {"({a: x, b: y, c: z})", OBJECTLIT, true},
            // Note: RegExp objects are not immutable, for instance, the exec
            // method maintains state for "global" searches.
            {"/abc/gi", REGEXP, true},
            {"'a'", STRING, false},
            {"0", NUMBER, false},
            {"a + c", ADD, false},
            {"'c' + a[0]", ADD, false},
            {"a[0][1]", GETELEM, false},
            {"'a' + c", ADD, false},
            {"'a' + a.name", ADD, false},
            {"1, 2, 3", COMMA, false},
            {"a, b, 3", COMMA, false},
            {"(function(a, b) {  })", FUNCTION, true},
            {"a ? c : d", HOOK, false},
            {"'1' + navigator.userAgent", ADD, false},
            {"new RegExp('foobar', 'i')", NEW, true},
            {"new RegExp(SomethingWacky(), 'i')", NEW, true},
            {"new Array()", NEW, true},
            {"new Array", NEW, true},
            {"new Array(4)", NEW, true},
            {"new Array('a', 'b', 'c')", NEW, true},
            {"new SomeClassINeverHeardOf()", NEW, true},

            // Getters and setters.
            {"({...x});", SPREAD, true},
            {"const {...x} = y;", REST, true},
            {"x.getter;", GETPROP, true},
            // Overapproximation to avoid inspecting the parent.
            {"x.setter;", GETPROP, true},
            {"x.normal;", GETPROP, false},
            {"const {getter} = x;", STRING_KEY, true},
            // Overapproximation to avoid inspecting the parent.
            {"const {setter} = x;", STRING_KEY, false},
            {"const {normal} = x;", STRING_KEY, false},
            {"x.getter = 0;", GETPROP, true},
            {"x.setter = 0;", GETPROP, true},
            {"x.normal = 0;", GETPROP, false},

            // Default values delegates to children.
            {"({x = 0} = y);", DEFAULT_VALUE, false},
            {"([x = 0] = y);", DEFAULT_VALUE, false},
            {"function f(x = 0) { };", DEFAULT_VALUE, false},
          });
    }

    @Test
    public void mayEffectMutableState() {
      ParseHelper helper = new ParseHelper().registerFakeGetterAndSetter();
      Node node = helper.parseFirst(token, jsExpression);
      AstAnalyzer analyzer = helper.getAstAnalyzer();
      assertThat(analyzer.mayEffectMutableState(node)).isEqualTo(expectedResult);
    }
  }

  @RunWith(Parameterized.class)
  public static final class MayHaveSideEffects {

    @Parameter public SideEffectsCase kase;

    @Test
    public void test() {
      ParseHelper helper =
          new ParseHelper()
              .registerFakeGetterAndSetter()
              .setHasRegExpGlobalReferences(kase.globalRegExp);

      Node node;
      if (kase.token == null) {
        node = helper.parse(kase.js).getFirstChild();
      } else {
        node = helper.parseFirst(kase.token, kase.js);
      }

      assertThat(helper.getAstAnalyzer().mayHaveSideEffects(node)).isEqualTo(kase.expect);
    }

    private static final class SideEffectsCase {
      boolean expect;
      String js;
      Token token;
      boolean globalRegExp;

      SideEffectsCase expect(boolean b) {
        this.expect = b;
        return this;
      }

      SideEffectsCase js(String s) {
        this.js = s;
        return this;
      }

      SideEffectsCase token(Token t) {
        this.token = t;
        return this;
      }

      SideEffectsCase globalRegExp(boolean b) {
        this.globalRegExp = b;
        return this;
      }

      @Override
      public String toString() {
        return SimpleFormat.format("%s node in `%s` -> %s", token, js, expect);
      }
    }

    private static SideEffectsCase kase() {
      return new SideEffectsCase();
    }

    // Always include the index. If two cases have the same name, only one will be executed.
    @Parameters(name = "#{index} {0}")
    public static Iterable<SideEffectsCase> cases() {
      return ImmutableList.of(
          // Cases in need of differentiation.
          kase().expect(false).js("[1]"),
          kase().expect(false).js("[1, 2]"),
          kase().expect(true).js("i++"),
          kase().expect(true).js("[b, [a, i++]]"),
          kase().expect(true).js("i=3"),
          kase().expect(true).js("[0, i=3]"),
          kase().expect(true).js("b()"),
          kase().expect(true).js("[1, b()]"),
          kase().expect(true).js("b.b=4"),
          kase().expect(true).js("b.b--"),
          kase().expect(true).js("i--"),
          kase().expect(true).js("a[0][i=4]"),
          kase().expect(true).js("a += 3"),
          kase().expect(true).js("a, b, z += 4"),
          kase().expect(true).js("a ? c : d++"),
          kase().expect(true).js("a + c++"),
          kase().expect(true).js("a + c - d()"),
          kase().expect(true).js("a + c - d()"),
          kase().expect(true).js("function foo() {}"),
          kase().expect(true).js("class Foo {}"),
          kase().expect(true).js("while(true);"),
          kase().expect(true).js("if(true){a()}"),
          kase().expect(false).js("if(true){a}"),
          kase().expect(false).js("(function() { })"),
          kase().expect(false).js("(function() { i++ })"),
          kase().expect(false).js("[function a(){}]"),
          kase().expect(false).js("(class { })"),
          kase().expect(false).js("(class { method() { i++ } })"),
          kase().expect(true).js("(class { [computedName()]() {} })"),
          kase().expect(false).js("(class { [computedName]() {} })"),
          kase().expect(false).js("(class Foo extends Bar { })"),
          kase().expect(true).js("(class extends foo() { })"),
          kase().expect(false).js("a"),
          kase().expect(false).js("a.b"),
          kase().expect(false).js("a.b.c"),
          kase().expect(false).js("[b, c [d, [e]]]"),
          kase().expect(false).js("({a: x, b: y, c: z})"),
          kase().expect(false).js("({a, b, c})"),
          kase().expect(false).js("/abc/gi"),
          kase().expect(false).js("'a'"),
          kase().expect(false).js("0"),
          kase().expect(false).js("a + c"),
          kase().expect(false).js("'c' + a[0]"),
          kase().expect(false).js("a[0][1]"),
          kase().expect(false).js("'a' + c"),
          kase().expect(false).js("'a' + a.name"),
          kase().expect(false).js("1, 2, 3"),
          kase().expect(false).js("a, b, 3"),
          kase().expect(false).js("(function(a, b) {  })"),
          kase().expect(false).js("a ? c : d"),
          kase().expect(false).js("'1' + navigator.userAgent"),
          kase().expect(false).js("`template`"),
          kase().expect(false).js("`template${name}`"),
          kase().expect(false).js("`${name}template`"),
          kase().expect(true).js("`${naming()}template`"),
          kase().expect(true).js("templateFunction`template`"),
          kase().expect(true).js("st = `${name}template`"),
          kase().expect(true).js("tempFunc = templateFunction`template`"),
          kase().expect(false).js("new RegExp('foobar', 'i')"),
          kase().expect(true).js("new RegExp(SomethingWacky(), 'i')"),
          kase().expect(false).js("new Array()"),
          kase().expect(false).js("new Array"),
          kase().expect(false).js("new Array(4)"),
          kase().expect(false).js("new Array('a', 'b', 'c')"),
          kase().expect(true).js("new SomeClassINeverHeardOf()"),
          kase().expect(true).js("new SomeClassINeverHeardOf()"),
          kase().expect(false).js("({}).foo = 4"),
          kase().expect(false).js("([]).foo = 4"),
          kase().expect(false).js("(function() {}).foo = 4"),
          kase().expect(true).js("this.foo = 4"),
          kase().expect(true).js("a.foo = 4"),
          kase().expect(true).js("(function() { return n; })().foo = 4"),
          kase().expect(true).js("([]).foo = bar()"),
          kase().expect(false).js("undefined"),
          kase().expect(false).js("void 0"),
          kase().expect(true).js("void foo()"),
          kase().expect(false).js("-Infinity"),
          kase().expect(false).js("Infinity"),
          kase().expect(false).js("NaN"),
          kase().expect(false).js("({}||[]).foo = 2;"),
          kase().expect(false).js("(true ? {} : []).foo = 2;"),
          kase().expect(false).js("({},[]).foo = 2;"),
          kase().expect(true).js("delete a.b"),
          kase().expect(false).js("Math.random();"),
          kase().expect(true).js("Math.random(seed);"),
          kase().expect(false).js("[1, 1].foo;"),
          kase().expect(true).js("export var x = 0;"),
          kase().expect(true).js("export let x = 0;"),
          kase().expect(true).js("export const x = 0;"),
          kase().expect(true).js("export class X {};"),
          kase().expect(true).js("export function x() {};"),
          kase().expect(true).js("export {x};"),

          // ARRAYLIT-SPREAD
          kase().expect(false).js("[...[]]"),
          kase().expect(false).js("[...[1]]"),
          kase().expect(true).js("[...[i++]]"),
          kase().expect(false).js("[...'string']"),
          kase().expect(false).js("[...`templatelit`]"),
          kase().expect(false).js("[...`templatelit ${safe}`]"),
          kase().expect(true).js("[...`templatelit ${unsafe()}`]"),
          kase().expect(true).js("[...f()]"),
          kase().expect(true).js("[...5]"),
          kase().expect(true).js("[...null]"),
          kase().expect(true).js("[...true]"),

          // CALL-SPREAD
          kase().expect(false).js("Math.sin(...[])"),
          kase().expect(false).js("Math.sin(...[1])"),
          kase().expect(true).js("Math.sin(...[i++])"),
          kase().expect(false).js("Math.sin(...'string')"),
          kase().expect(false).js("Math.sin(...`templatelit`)"),
          kase().expect(false).js("Math.sin(...`templatelit ${safe}`)"),
          kase().expect(true).js("Math.sin(...`templatelit ${unsafe()}`)"),
          kase().expect(true).js("Math.sin(...f())"),
          kase().expect(true).js("Math.sin(...5)"),
          kase().expect(true).js("Math.sin(...null)"),
          kase().expect(true).js("Math.sin(...true)"),

          // NEW-SPREAD
          kase().expect(false).js("new Object(...[])"),
          kase().expect(false).js("new Object(...[1])"),
          kase().expect(true).js("new Object(...[i++])"),
          kase().expect(false).js("new Object(...'string')"),
          kase().expect(false).js("new Object(...`templatelit`)"),
          kase().expect(false).js("new Object(...`templatelit ${safe}`)"),
          kase().expect(true).js("new Object(...`templatelit ${unsafe()}`)"),
          kase().expect(true).js("new Object(...f())"),
          kase().expect(true).js("new Object(...5)"),
          kase().expect(true).js("new Object(...null)"),
          kase().expect(true).js("new Object(...true)"),

          // OBJECT-SPREAD
          // These could all invoke getters.
          kase().expect(true).js("({...x})"),
          kase().expect(true).js("({...{}})"),
          kase().expect(true).js("({...{a:1}})"),
          kase().expect(true).js("({...{a:i++}})"),
          kase().expect(true).js("({...{a:f()}})"),
          kase().expect(true).js("({...f()})"),

          // REST
          // This could invoke getters.
          kase().expect(true).token(REST).js("({...x} = something)"),
          // We currently assume all iterable-rests are side-effectful.
          kase().expect(true).token(REST).js("([...x] = 'safe')"),
          kase().expect(false).token(REST).js("(function(...x) { })"),

          // Context switch
          kase().expect(true).token(AWAIT).js("async function f() { await 0; }"),
          kase().expect(true).token(FOR_AWAIT_OF).js("(async()=>{ for await (let x of []) {} })"),
          kase().expect(true).token(THROW).js("function f() { throw 'something'; }"),
          kase().expect(true).token(YIELD).js("function* f() { yield 'something'; }"),
          kase().expect(true).token(YIELD).js("function* f() { yield* 'something'; }"),

          // Enhanced for loop
          // These edge cases are actually side-effect free. We include them to confirm we just give
          // up
          // on enhanced for loops.
          kase().expect(true).js("for (const x in []) { }"),
          kase().expect(true).js("for (const x of []) { }"),

          // COMPUTED_PROP - OBJECTLIT
          kase().expect(false).token(COMPUTED_PROP).js("({[a]: x})"),
          kase().expect(true).token(COMPUTED_PROP).js("({[a()]: x})"),
          kase().expect(true).token(COMPUTED_PROP).js("({[a]: x()})"),

          // computed property getters and setters are modeled as COMPUTED_PROP with an
          // annotation to indicate getter or setter.
          kase().expect(false).token(COMPUTED_PROP).js("({ get [a]() {} })"),
          kase().expect(true).token(COMPUTED_PROP).js("({ get [a()]() {} })"),
          kase().expect(false).token(COMPUTED_PROP).js("({ set [a](x) {} })"),
          kase().expect(true).token(COMPUTED_PROP).js("({ set [a()](x) {} })"),

          // COMPUTED_PROP - CLASS
          kase().expect(false).token(COMPUTED_PROP).js("class C { [a]() {} }"),
          kase().expect(true).token(COMPUTED_PROP).js("class C { [a()]() {} }"),

          // computed property getters and setters are modeled as COMPUTED_PROP with an
          // annotation to indicate getter or setter.
          kase().expect(false).token(COMPUTED_PROP).js("class C { get [a]() {} }"),
          kase().expect(true).token(COMPUTED_PROP).js("class C { get [a()]() {} }"),
          kase().expect(false).token(COMPUTED_PROP).js("class C { set [a](x) {} }"),
          kase().expect(true).token(COMPUTED_PROP).js("class C { set [a()](x) {} }"),

          // GETTER_DEF
          kase().expect(false).token(GETTER_DEF).js("({ get a() {} })"),
          kase().expect(false).token(GETTER_DEF).js("class C { get a() {} }"),

          // Getter use
          kase().expect(true).token(GETPROP).js("x.getter;"),
          // Overapproximation because to avoid inspecting the parent.
          kase().expect(true).token(GETPROP).js("x.setter;"),
          kase().expect(false).token(GETPROP).js("x.normal;"),
          kase().expect(true).token(STRING_KEY).js("({getter} = foo());"),
          kase().expect(false).token(STRING_KEY).js("({setter} = foo());"),
          kase().expect(false).token(STRING_KEY).js("({normal} = foo());"),

          // SETTER_DEF
          kase().expect(false).token(SETTER_DEF).js("({ set a(x) {} })"),
          kase().expect(false).token(SETTER_DEF).js("class C { set a(x) {} }"),

          // SETTER_USE
          // Overapproximation because to avoid inspecting the parent.
          kase().expect(true).token(GETPROP).js("x.getter = 0;"),
          kase().expect(true).token(GETPROP).js("x.setter = 0;"),
          kase().expect(false).token(GETPROP).js("x.normal = 0;"),

          // MEMBER_FUNCTION_DEF
          kase().expect(false).token(MEMBER_FUNCTION_DEF).js("({ a(x) {} })"),
          kase().expect(false).token(MEMBER_FUNCTION_DEF).js("class C { a(x) {} }"),

          // IObject methods
          // "toString" and "valueOf" are assumed to be side-effect free
          kase().expect(false).js("o.toString()"),
          kase().expect(false).js("o.valueOf()"),
          // other methods depend on the extern definitions
          kase().expect(true).js("o.watch()"),

          // A RegExp Object by itself doesn't have any side-effects
          kase().expect(false).js("/abc/gi").globalRegExp(true),
          kase().expect(false).js("/abc/gi").globalRegExp(false),

          // RegExp instance methods have global side-effects, so whether they are
          // considered side-effect free depends on whether the global properties
          // are referenced.
          kase().expect(true).js("(/abc/gi).test('')").globalRegExp(true),
          kase().expect(false).js("(/abc/gi).test('')").globalRegExp(false),
          kase().expect(true).js("(/abc/gi).test(a)").globalRegExp(true),
          kase().expect(false).js("(/abc/gi).test(b)").globalRegExp(false),
          kase().expect(true).js("(/abc/gi).exec('')").globalRegExp(true),
          kase().expect(false).js("(/abc/gi).exec('')").globalRegExp(false),

          // Some RegExp object method that may have side-effects.
          kase().expect(true).js("(/abc/gi).foo('')").globalRegExp(true),
          kase().expect(true).js("(/abc/gi).foo('')").globalRegExp(false),

          // Try the string RegExp ops.
          kase().expect(true).js("''.match('a')").globalRegExp(true),
          kase().expect(false).js("''.match('a')").globalRegExp(false),
          kase().expect(true).js("''.match(/(a)/)").globalRegExp(true),
          kase().expect(false).js("''.match(/(a)/)").globalRegExp(false),
          kase().expect(true).js("''.replace('a')").globalRegExp(true),
          kase().expect(false).js("''.replace('a')").globalRegExp(false),
          kase().expect(true).js("''.search('a')").globalRegExp(true),
          kase().expect(false).js("''.search('a')").globalRegExp(false),
          kase().expect(true).js("''.split('a')").globalRegExp(true),
          kase().expect(false).js("''.split('a')").globalRegExp(false),

          // Some non-RegExp string op that may have side-effects.
          kase().expect(true).js("''.foo('a')").globalRegExp(true),
          kase().expect(true).js("''.foo('a')").globalRegExp(false),

          // 'a' might be a RegExp object with the 'g' flag, in which case
          // the state might change by running any of the string ops.
          // Specifically, using these methods resets the "lastIndex" if used
          // in combination with a RegExp instance "exec" method.
          kase().expect(true).js("''.match(a)").globalRegExp(true),
          kase().expect(true).js("''.match(a)").globalRegExp(false),
          kase().expect(true).js("'a'.replace(/a/, function (s) {alert(s)})").globalRegExp(false),
          kase().expect(false).js("'a'.replace(/a/, 'x')").globalRegExp(false));
    }
  }

  @RunWith(Parameterized.class)
  public static final class NodeTypeMayHaveSideEffects {
    @Parameter(0)
    public String js;

    @Parameter(1)
    public Token token;

    @Parameter(2)
    public Boolean expectedResult;

    // Always include the index. If two cases have the same name, only one will be executed.
    @Parameters(name = "#{index} {1} node in \"{0}\" side-effects: {2}")
    public static Iterable<Object[]> cases() {
      return ImmutableList.copyOf(
          new Object[][] {
            {"x = y", ASSIGN, true},
            {"x += y", ASSIGN_ADD, true},
            {"delete x.y", DELPROP, true},
            {"x++", INC, true},
            {"x--", DEC, true},
            {"for (prop in obj) {}", FOR_IN, true},
            {"for (item of iterable) {}", FOR_OF, true},
            // name declaration has a side effect when a value is actually assigned
            {"var x = 1;", NAME, true},
            {"let x = 1;", NAME, true},
            {"const x = 1;", NAME, true},
            // don't consider name declaration to have a side effect if there's no assignment
            {"var x;", NAME, false},
            {"let x;", NAME, false},

            // NOTE: CALL and NEW nodes are delegated to functionCallHasSideEffects() and
            // constructorCallHasSideEffects(), respectively. The cases below are just a few
            // representative examples that are convenient to test here.
            //
            // in general function and constructor calls are assumed to have side effects
            {"foo();", CALL, true},
            {"new Foo();", NEW, true},
            // Object() is known not to have side-effects, though
            {"Object();", CALL, false},
            {"new Object();", NEW, false},
            // TAGGED_TEMPLATELIT is just a special syntax for a CALL.
            {"foo`template`;", TAGGED_TEMPLATELIT, true},

            // NOTE: REST and SPREAD are delegated to NodeUtil.iteratesImpureIterable()
            // Test cases here are just easy to test representative examples.
            {"[...[1, 2, 3]]", SPREAD, false},
            {"[...someIterable]", SPREAD, true}, // unknown, so assume side-effects
            // we just assume the rhs of an array pattern may have iteration side-effects
            // without looking too closely.
            {"let [...rest] = [1, 2, 3];", REST, true},
            // REST in parameter list does not trigger iteration at the function definition, so
            // it has no side effects.
            {"function foo(...rest) {}", REST, false},

            // defining a class or a function is not considered to be a side-effect
            {"function foo() {}", FUNCTION, false},
            {"class Foo {}", CLASS, false},

            // arithmetic, logic, and bitwise operations do not have side-effects
            {"x + y", ADD, false},
            {"x || y", OR, false},
            {"x | y", BITOR, false},

            // Getters and setters
            {"({...x});", SPREAD, true},
            {"const {...x} = y;", REST, true},
            {"y.getter;", GETPROP, true},
            {"y.setter;", GETPROP, true},
            {"y.normal;", GETPROP, false},
            {"const {getter} = y;", STRING_KEY, true},
            {"const {setter} = y;", STRING_KEY, false},
            {"const {normal} = y;", STRING_KEY, false},
            {"y.getter = 0;", GETPROP, true},
            {"y.setter = 0;", GETPROP, true},
            {"y.normal = 0;", GETPROP, false},
          });
    }

    @Test
    public void test() {
      ParseHelper helper = new ParseHelper().registerFakeGetterAndSetter();

      Node n = helper.parseFirst(token, js);
      AstAnalyzer astAnalyzer = helper.getAstAnalyzer();
      assertThat(astAnalyzer.nodeTypeMayHaveSideEffects(n)).isEqualTo(expectedResult);
    }
  }

  @RunWith(JUnit4.class)
  public static final class FunctionCallHasSideEffects {
    @Test
    public void testCallSideEffects() {
      ParseHelper helper = new ParseHelper();

      // Parens force interpretation as an expression.
      Node newXDotMethodCall = helper.parseFirst(CALL, "(new x().method());");
      AstAnalyzer astAnalyzer = helper.getAstAnalyzer();
      assertThat(astAnalyzer.functionCallHasSideEffects(newXDotMethodCall)).isTrue();

      Node newExpr = newXDotMethodCall.getFirstFirstChild();
      checkState(newExpr.isNew());
      Node.SideEffectFlags flags = new Node.SideEffectFlags();

      // No side effects, local result
      flags.clearAllFlags();
      newExpr.setSideEffectFlags(flags);
      flags.clearAllFlags();
      newXDotMethodCall.setSideEffectFlags(flags);

      assertThat(NodeUtil.evaluatesToLocalValue(newXDotMethodCall)).isTrue();
      assertThat(astAnalyzer.functionCallHasSideEffects(newXDotMethodCall)).isFalse();
      assertThat(astAnalyzer.mayHaveSideEffects(newXDotMethodCall)).isFalse();

      // Modifies this, local result
      flags.clearAllFlags();
      newExpr.setSideEffectFlags(flags);
      flags.clearAllFlags();
      flags.setMutatesThis();
      newXDotMethodCall.setSideEffectFlags(flags);

      assertThat(NodeUtil.evaluatesToLocalValue(newXDotMethodCall)).isTrue();
      assertThat(astAnalyzer.functionCallHasSideEffects(newXDotMethodCall)).isFalse();
      assertThat(astAnalyzer.mayHaveSideEffects(newXDotMethodCall)).isFalse();

      // Modifies this, non-local result
      flags.clearAllFlags();
      newExpr.setSideEffectFlags(flags);
      flags.clearAllFlags();
      flags.setMutatesThis();
      flags.setReturnsTainted();
      newXDotMethodCall.setSideEffectFlags(flags);

      assertThat(NodeUtil.evaluatesToLocalValue(newXDotMethodCall)).isFalse();
      assertThat(astAnalyzer.functionCallHasSideEffects(newXDotMethodCall)).isFalse();
      assertThat(astAnalyzer.mayHaveSideEffects(newXDotMethodCall)).isFalse();

      // No modifications, non-local result
      flags.clearAllFlags();
      newExpr.setSideEffectFlags(flags);
      flags.clearAllFlags();
      flags.setReturnsTainted();
      newXDotMethodCall.setSideEffectFlags(flags);

      assertThat(NodeUtil.evaluatesToLocalValue(newXDotMethodCall)).isFalse();
      assertThat(astAnalyzer.functionCallHasSideEffects(newXDotMethodCall)).isFalse();
      assertThat(astAnalyzer.mayHaveSideEffects(newXDotMethodCall)).isFalse();

      // The new modifies global state, no side-effect call, non-local result
      // This call could be removed, but not the new.
      flags.clearAllFlags();
      flags.setMutatesGlobalState();
      newExpr.setSideEffectFlags(flags);
      flags.clearAllFlags();
      newXDotMethodCall.setSideEffectFlags(flags);

      assertThat(NodeUtil.evaluatesToLocalValue(newXDotMethodCall)).isTrue();
      assertThat(astAnalyzer.functionCallHasSideEffects(newXDotMethodCall)).isFalse();
      assertThat(astAnalyzer.mayHaveSideEffects(newXDotMethodCall)).isTrue();
    }
  }

  @RunWith(JUnit4.class)
  public static class ConstructorCallHasSideEffects {
    @Test
    public void byDefaultAConstructorCallHasSideEffects() {
      ParseHelper parseHelper = new ParseHelper();
      Node newNode = parseHelper.parseFirst(NEW, "new SomeClass();");
      AstAnalyzer astAnalyzer = parseHelper.getAstAnalyzer();
      // we know nothing about the class being instantiated, so assume side effects occur.
      assertThat(astAnalyzer.constructorCallHasSideEffects(newNode)).isTrue();
    }

    @Test
    public void constructorCallMarkedAsNoSideEffectsHasNone() {
      ParseHelper parseHelper = new ParseHelper();
      Node newNode = parseHelper.parseFirst(NEW, "new SomeClass();");
      AstAnalyzer astAnalyzer = parseHelper.getAstAnalyzer();
      // simulate PureFunctionIdentifier marking the call as having no side effects.
      Node.SideEffectFlags flags = new Node.SideEffectFlags();
      flags.clearAllFlags();
      newNode.setSideEffectFlags(flags);

      assertThat(astAnalyzer.constructorCallHasSideEffects(newNode)).isFalse();
    }

    @Test
    public void modifyingALocalArgumentIsNotASideEffect() {
      ParseHelper parseHelper = new ParseHelper();
      // object literal is considered a local value. Modifying it isn't a side effect, since
      // nothing else looks at it.
      Node newNode = parseHelper.parseFirst(NEW, "new SomeClass({});");
      AstAnalyzer astAnalyzer = parseHelper.getAstAnalyzer();
      // simulate PureFunctionIdentifier marking the call as only modifying its arguments
      Node.SideEffectFlags flags = new Node.SideEffectFlags();
      flags.clearAllFlags();
      flags.setMutatesArguments();
      newNode.setSideEffectFlags(flags);

      assertThat(astAnalyzer.constructorCallHasSideEffects(newNode)).isFalse();
    }

    @Test
    public void modifyingANonLocalArgumentIsASideEffect() {
      ParseHelper parseHelper = new ParseHelper();
      // variable name is a non-local value. Modifying it is a side effect.
      Node newNode = parseHelper.parseFirst(NEW, "new SomeClass(x);");
      AstAnalyzer astAnalyzer = parseHelper.getAstAnalyzer();
      // simulate PureFunctionIdentifier marking the call as only modifying its arguments
      Node.SideEffectFlags flags = new Node.SideEffectFlags();
      flags.clearAllFlags();
      flags.setMutatesArguments();
      newNode.setSideEffectFlags(flags);

      assertThat(astAnalyzer.constructorCallHasSideEffects(newNode)).isTrue();
    }
  }

  @RunWith(Parameterized.class)
  public static class ConstructorsKnownToHaveNoSideEffects {
    @Parameter(0)
    public String constructorName;

    // Always include the index. If two cases have the same name, only one will be executed.
    @Parameters(name = "#{index} {0}")
    public static final Iterable<Object[]> cases() {
      return ImmutableList.copyOf(
          new Object[][] {
            {"Array"}, {"Date"}, {"Error"}, {"Object"}, {"RegExp"}, {"XMLHttpRequest"}
          });
    }

    @Test
    public void noSideEffectsForKnownConstructor() {
      ParseHelper parseHelper = new ParseHelper();
      Node newNode = parseHelper.parseFirst(NEW, SimpleFormat.format("new %s();", constructorName));
      AstAnalyzer astAnalyzer = parseHelper.getAstAnalyzer();
      // we know nothing about the class being instantiated, so assume side effects occur.
      assertThat(astAnalyzer.constructorCallHasSideEffects(newNode)).isFalse();
    }
  }
}
