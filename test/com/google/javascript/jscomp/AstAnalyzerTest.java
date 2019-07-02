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
    private Compiler compiler = null;

    private Node parse(String js) {
      CompilerOptions options = new CompilerOptions();
      options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);

      // To allow octal literals such as 0123 to be parsed.
      options.setStrictModeInput(false);
      options.setWarningLevel(ES5_STRICT, CheckLevel.OFF);

      compiler = new Compiler();
      compiler.initOptions(options);
      Node n = compiler.parseTestCode(js);
      assertThat(compiler.getErrors()).isEmpty();
      return n;
    }

    /**
     * Tell the compiler to behave as if it has (or has not) seen any references to the RegExp
     * global properties, which are modified when matches are performed.
     *
     * @param hasRegExpGlobalReferences
     */
    void setHasRegExpGlobalReferences(boolean hasRegExpGlobalReferences) {
      if (compiler == null) {
        throw new RuntimeException("must call parse() first");
      }
      compiler.setHasRegExpGlobalReferences(hasRegExpGlobalReferences);
    }

    ParseHelper regiserFakeGetterAndSetter() {
      if (compiler == null) {
        throw new RuntimeException("must call parse() first");
      }
      compiler.setAccessorSummary(
          AccessorSummary.create(
              ImmutableMap.of(
                  "getter", PropertyAccessKind.GETTER_ONLY, //
                  "setter", PropertyAccessKind.SETTER_ONLY)));
      return this;
    }

    /**
     * Parse a string of JavaScript and return the first node found with the given token in a
     * preorder DFS.
     */
    private Node parseFirst(Token token, String js) {
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
    private Node parseExpr(String js) {
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

    @Parameters(name = "{1} node in ({0}) -> {2}")
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
          });
    }

    @Test
    public void mayEffectMutableState() {
      ParseHelper helper = new ParseHelper();
      Node node = helper.parseFirst(token, jsExpression);
      helper.regiserFakeGetterAndSetter();
      AstAnalyzer analyzer = helper.getAstAnalyzer();
      assertThat(analyzer.mayEffectMutableState(node)).isEqualTo(expectedResult);
    }
  }

  // TODO(bradfordcsmith): It would be nice to implemenent this with Parameterized.
  // These test cases were copied from NodeUtilTest with minimal changes.
  @RunWith(JUnit4.class)
  public static final class MayHaveSideEffects {
    private void assertSideEffect(boolean se, String js) {
      ParseHelper helper = new ParseHelper();

      Node n = helper.parse(js);
      helper.regiserFakeGetterAndSetter();
      assertThat(helper.getAstAnalyzer().mayHaveSideEffects(n.getFirstChild())).isEqualTo(se);
    }

    private void assertNodeHasSideEffect(boolean se, Token token, String js) {
      ParseHelper helper = new ParseHelper();

      Node node = helper.parseFirst(token, js);
      helper.regiserFakeGetterAndSetter();
      assertThat(helper.getAstAnalyzer().mayHaveSideEffects(node)).isEqualTo(se);
    }

    private void assertSideEffect(boolean se, String js, boolean globalRegExp) {
      ParseHelper helper = new ParseHelper();

      Node n = helper.parse(js);
      helper.setHasRegExpGlobalReferences(globalRegExp);
      helper.regiserFakeGetterAndSetter();
      assertThat(helper.getAstAnalyzer().mayHaveSideEffects(n.getFirstChild())).isEqualTo(se);
    }

    @Test
    public void testMayHaveSideEffects_undifferentiatedCases() {
      assertSideEffect(false, "[1]");
      assertSideEffect(false, "[1, 2]");
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
    public void testMayHaveSideEffects_iterableSpread() {
      // ARRAYLIT-SPREAD
      assertSideEffect(false, "[...[]]");
      assertSideEffect(false, "[...[1]]");
      assertSideEffect(true, "[...[i++]]");
      assertSideEffect(false, "[...'string']");
      assertSideEffect(false, "[...`templatelit`]");
      assertSideEffect(false, "[...`templatelit ${safe}`]");
      assertSideEffect(true, "[...`templatelit ${unsafe()}`]");
      assertSideEffect(true, "[...f()]");
      assertSideEffect(true, "[...5]");
      assertSideEffect(true, "[...null]");
      assertSideEffect(true, "[...true]");

      // CALL-SPREAD
      assertSideEffect(false, "Math.sin(...[])");
      assertSideEffect(false, "Math.sin(...[1])");
      assertSideEffect(true, "Math.sin(...[i++])");
      assertSideEffect(false, "Math.sin(...'string')");
      assertSideEffect(false, "Math.sin(...`templatelit`)");
      assertSideEffect(false, "Math.sin(...`templatelit ${safe}`)");
      assertSideEffect(true, "Math.sin(...`templatelit ${unsafe()}`)");
      assertSideEffect(true, "Math.sin(...f())");
      assertSideEffect(true, "Math.sin(...5)");
      assertSideEffect(true, "Math.sin(...null)");
      assertSideEffect(true, "Math.sin(...true)");

      // NEW-SPREAD
      assertSideEffect(false, "new Object(...[])");
      assertSideEffect(false, "new Object(...[1])");
      assertSideEffect(true, "new Object(...[i++])");
      assertSideEffect(false, "new Object(...'string')");
      assertSideEffect(false, "new Object(...`templatelit`)");
      assertSideEffect(false, "new Object(...`templatelit ${safe}`)");
      assertSideEffect(true, "new Object(...`templatelit ${unsafe()}`)");
      assertSideEffect(true, "new Object(...f())");
      assertSideEffect(true, "new Object(...5)");
      assertSideEffect(true, "new Object(...null)");
      assertSideEffect(true, "new Object(...true)");
    }

    @Test
    public void testMayHaveSideEffects_objectSpread() {
      // OBJECT-SPREAD
      // These could all invoke getters.
      assertSideEffect(true, "({...x})");
      assertSideEffect(true, "({...{}})");
      assertSideEffect(true, "({...{a:1}})");
      assertSideEffect(true, "({...{a:i++}})");
      assertSideEffect(true, "({...{a:f()}})");
      assertSideEffect(true, "({...f()})");
    }

    @Test
    public void testMayHaveSideEffects_rest() {
      // REST

      // This could all invoke getters.
      assertNodeHasSideEffect(true, REST, "({...x} = something)");
      // We currently assume all iterable-rests are side-effectful.
      assertNodeHasSideEffect(true, REST, "([...x] = 'safe')");
      assertNodeHasSideEffect(false, REST, "(function(...x) { })");
    }

    @Test
    public void testMayHaveSideEffects_contextSwitch() {
      assertNodeHasSideEffect(true, AWAIT, "async function f() { await 0; }");
      assertNodeHasSideEffect(true, FOR_AWAIT_OF, "(async()=>{ for await (let x of []) {} })");
      assertNodeHasSideEffect(true, THROW, "function f() { throw 'something'; }");
      assertNodeHasSideEffect(true, YIELD, "function* f() { yield 'something'; }");
      assertNodeHasSideEffect(true, YIELD, "function* f() { yield* 'something'; }");
    }

    @Test
    public void testMayHaveSideEffects_enhancedForLoop() {
      // These edge cases are actually side-effect free. We include them to confirm we just give up
      // on enhanced for loops.
      assertSideEffect(true, "for (const x in []) { }");
      assertSideEffect(true, "for (const x of []) { }");
    }

    @Test
    public void testMayHaveSideEffects_computedProp() {
      assertNodeHasSideEffect(false, COMPUTED_PROP, "({[a]: x})");
      assertNodeHasSideEffect(true, COMPUTED_PROP, "({[a()]: x})");
      assertNodeHasSideEffect(true, COMPUTED_PROP, "({[a]: x()})");

      // computed property getters and setters are modeled as COMPUTED_PROP with an
      // annotation to indicate getter or setter.
      assertNodeHasSideEffect(false, COMPUTED_PROP, "({ get [a]() {} })");
      assertNodeHasSideEffect(true, COMPUTED_PROP, "({ get [a()]() {} })");

      assertNodeHasSideEffect(false, COMPUTED_PROP, "({ set [a](x) {} })");
      assertNodeHasSideEffect(true, COMPUTED_PROP, "({ set [a()](x) {} })");
    }

    @Test
    public void testMayHaveSideEffects_classComputedProp() {
      assertNodeHasSideEffect(false, COMPUTED_PROP, "class C { [a]() {} }");
      assertNodeHasSideEffect(true, COMPUTED_PROP, "class C { [a()]() {} }");

      // computed property getters and setters are modeled as COMPUTED_PROP with an
      // annotation to indicate getter or setter.
      assertNodeHasSideEffect(false, COMPUTED_PROP, "class C { get [a]() {} }");
      assertNodeHasSideEffect(true, COMPUTED_PROP, "class C { get [a()]() {} }");

      assertNodeHasSideEffect(false, COMPUTED_PROP, "class C { set [a](x) {} }");
      assertNodeHasSideEffect(true, COMPUTED_PROP, "class C { set [a()](x) {} }");
    }

    @Test
    public void testMayHaveSideEffects_getterDef() {
      assertNodeHasSideEffect(false, GETTER_DEF, "({ get a() {} })");
      assertNodeHasSideEffect(false, GETTER_DEF, "class C { get a() {} }");
    }

    @Test
    public void testMayHaveSideEffects_getterUse() {
      assertNodeHasSideEffect(true, GETPROP, "x.getter;");
      // Overapproximation because to avoid inspecting the parent.
      assertNodeHasSideEffect(true, GETPROP, "x.setter;");
      assertNodeHasSideEffect(false, GETPROP, "x.normal;");
      assertNodeHasSideEffect(true, STRING_KEY, "({getter} = foo());");
      assertNodeHasSideEffect(false, STRING_KEY, "({setter} = foo());");
      assertNodeHasSideEffect(false, STRING_KEY, "({normal} = foo());");
    }

    @Test
    public void testMayHaveSideEffects_setterDef() {
      assertNodeHasSideEffect(false, SETTER_DEF, "({ set a(x) {} })");
      assertNodeHasSideEffect(false, SETTER_DEF, "class C { set a(x) {} }");
    }

    @Test
    public void testMayHaveSideEffects_setterUse() {
      // Overapproximation because to avoid inspecting the parent.
      assertNodeHasSideEffect(true, GETPROP, "x.getter = 0;");
      assertNodeHasSideEffect(true, GETPROP, "x.setter = 0;");
      assertNodeHasSideEffect(false, GETPROP, "x.normal = 0;");
    }

    @Test
    public void testMayHaveSideEffects_method() {
      assertNodeHasSideEffect(false, MEMBER_FUNCTION_DEF, "({ a(x) {} })");
      assertNodeHasSideEffect(false, MEMBER_FUNCTION_DEF, "class C { a(x) {} }");
    }

    @Test
    public void testMayHaveSideEffects_objectMethod() {
      // "toString" and "valueOf" are assumed to be side-effect free
      assertSideEffect(false, "o.toString()");
      assertSideEffect(false, "o.valueOf()");

      // other methods depend on the extern definitions
      assertSideEffect(true, "o.watch()");
    }

    @Test
    public void testMayHaveSideEffects_regExp() {
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

      assertSideEffect(true, "'a'.replace(/a/, function (s) {alert(s)})", false);
      assertSideEffect(false, "'a'.replace(/a/, 'x')", false);
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

    @Parameters(name = "{1} node in \"{0}\" side-effects: {2}")
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
      ParseHelper helper = new ParseHelper();

      Node n = helper.parseFirst(token, js);
      helper.regiserFakeGetterAndSetter();
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

    @Parameters(name = "{0}")
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
