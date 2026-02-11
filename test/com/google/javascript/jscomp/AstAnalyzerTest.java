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
import static com.google.javascript.rhino.Token.ARRAY_PATTERN;
import static com.google.javascript.rhino.Token.ASSIGN;
import static com.google.javascript.rhino.Token.ASSIGN_ADD;
import static com.google.javascript.rhino.Token.ASSIGN_AND;
import static com.google.javascript.rhino.Token.ASSIGN_COALESCE;
import static com.google.javascript.rhino.Token.ASSIGN_OR;
import static com.google.javascript.rhino.Token.AWAIT;
import static com.google.javascript.rhino.Token.BIGINT;
import static com.google.javascript.rhino.Token.BITOR;
import static com.google.javascript.rhino.Token.BLOCK;
import static com.google.javascript.rhino.Token.CALL;
import static com.google.javascript.rhino.Token.CLASS;
import static com.google.javascript.rhino.Token.COALESCE;
import static com.google.javascript.rhino.Token.COMMA;
import static com.google.javascript.rhino.Token.COMPUTED_FIELD_DEF;
import static com.google.javascript.rhino.Token.COMPUTED_PROP;
import static com.google.javascript.rhino.Token.DEC;
import static com.google.javascript.rhino.Token.DEFAULT_VALUE;
import static com.google.javascript.rhino.Token.DELPROP;
import static com.google.javascript.rhino.Token.DESTRUCTURING_LHS;
import static com.google.javascript.rhino.Token.DYNAMIC_IMPORT;
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
import static com.google.javascript.rhino.Token.ITER_REST;
import static com.google.javascript.rhino.Token.ITER_SPREAD;
import static com.google.javascript.rhino.Token.MEMBER_FIELD_DEF;
import static com.google.javascript.rhino.Token.MEMBER_FUNCTION_DEF;
import static com.google.javascript.rhino.Token.NAME;
import static com.google.javascript.rhino.Token.NEW;
import static com.google.javascript.rhino.Token.NUMBER;
import static com.google.javascript.rhino.Token.OBJECTLIT;
import static com.google.javascript.rhino.Token.OBJECT_PATTERN;
import static com.google.javascript.rhino.Token.OBJECT_REST;
import static com.google.javascript.rhino.Token.OBJECT_SPREAD;
import static com.google.javascript.rhino.Token.OPTCHAIN_CALL;
import static com.google.javascript.rhino.Token.OPTCHAIN_GETELEM;
import static com.google.javascript.rhino.Token.OPTCHAIN_GETPROP;
import static com.google.javascript.rhino.Token.OR;
import static com.google.javascript.rhino.Token.REGEXP;
import static com.google.javascript.rhino.Token.SETTER_DEF;
import static com.google.javascript.rhino.Token.STRING_KEY;
import static com.google.javascript.rhino.Token.SUB;
import static com.google.javascript.rhino.Token.SUPER;
import static com.google.javascript.rhino.Token.TAGGED_TEMPLATELIT;
import static com.google.javascript.rhino.Token.THROW;
import static com.google.javascript.rhino.Token.VOID;
import static com.google.javascript.rhino.Token.WHILE;
import static com.google.javascript.rhino.Token.YIELD;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.javascript.jscomp.AccessorSummary.PropertyAccessKind;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import java.util.ArrayDeque;
import java.util.Optional;
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
public final class AstAnalyzerTest {

  private static final class AnalysisCase {
    boolean expect;
    String js;
    Token token;
    boolean globalRegExp;
    boolean assumeGettersArePure;
    boolean assumeBuiltinsPure = true;

    @CanIgnoreReturnValue
    AnalysisCase expect(boolean b) {
      this.expect = b;
      return this;
    }

    @CanIgnoreReturnValue
    AnalysisCase js(String s) {
      this.js = s;
      return this;
    }

    @CanIgnoreReturnValue
    AnalysisCase token(Token t) {
      this.token = t;
      return this;
    }

    @CanIgnoreReturnValue
    AnalysisCase globalRegExp(boolean b) {
      this.globalRegExp = b;
      return this;
    }

    @CanIgnoreReturnValue
    AnalysisCase assumeGettersArePure(boolean b) {
      this.assumeGettersArePure = b;
      return this;
    }

    @CanIgnoreReturnValue
    AnalysisCase assumeBuiltinsPure(boolean b) {
      this.assumeBuiltinsPure = b;
      return this;
    }

    @Override
    public String toString() {
      return String.format("%s node in `%s` -> %s", token, js, expect);
    }
  }

  private static AnalysisCase kase() {
    return new AnalysisCase();
  }

  /** Provides methods for parsing and accessing the compiler used for the parsing. */
  private static final class ParseHelper {
    private boolean useTypesForLocalOptimizations = false;
    private boolean hasGlobalRegexpReferences = true;
    private boolean assumeGettersArePure = true;
    private boolean assumeBuiltinsPure = true;
    private JSTypeRegistry typeRegistry;
    private final AccessorSummary accessorSummary =
        AccessorSummary.create(
            ImmutableMap.of(
                "getter", PropertyAccessKind.GETTER_ONLY, //
                "setter", PropertyAccessKind.SETTER_ONLY));

    private Compiler newCompiler() {
      CompilerOptions options = new CompilerOptions();

      // To allow octal literals such as 0123 to be parsed.
      options.setStrictModeInput(false);
      options.setWarningLevel(ES5_STRICT, CheckLevel.OFF);

      options.setLanguageIn(CompilerOptions.LanguageMode.UNSUPPORTED);

      Compiler compiler = new Compiler();
      compiler.initOptions(options);

      return compiler;
    }

    private Node parseInternal(String js) {
      Compiler compiler = newCompiler();
      Node n = compiler.parseTestCode(js);
      assertThat(compiler.getErrors()).isEmpty();
      this.typeRegistry = compiler.getTypeRegistry();
      return n;
    }

    /**
     * Parse a string of JavaScript and return the first node found with the given token in a
     * preorder DFS.
     */
    Node parseFirst(Token token, String js) {
      return findFirst(token, parseInternal(js)).get();
    }

    Node parseCase(AnalysisCase kase) {
      this.hasGlobalRegexpReferences = kase.globalRegExp;
      this.assumeGettersArePure = kase.assumeGettersArePure;
      this.assumeBuiltinsPure = kase.assumeBuiltinsPure;
      Node root = parseInternal(kase.js);
      if (kase.token == null) {
        return root.getFirstChild();
      } else {
        return findFirst(kase.token, root).get();
      }
    }

    AstAnalyzer getAstAnalyzer() {
      return new AstAnalyzer(
          AstAnalyzer.Options.builder()
              .setUseTypesForLocalOptimization(useTypesForLocalOptimizations)
              .setHasRegexpGlobalReferences(hasGlobalRegexpReferences)
              .setAssumeGettersArePure(assumeGettersArePure)
              .setAssumeKnownBuiltinsArePure(assumeBuiltinsPure)
              .build(),
          typeRegistry,
          accessorSummary);
    }
  }

  /** Does a preorder DFS, returning the first node found that has the given token. */
  private static Optional<Node> findFirst(Token token, Node root) {
    ArrayDeque<Node> stack = new ArrayDeque<>();
    stack.push(root);

    while (!stack.isEmpty()) {
      Node cur = stack.pop();
      if (cur.getToken() == token) {
        return Optional.of(cur);
      }

      for (Node child = cur.getLastChild(); child != null; child = child.getPrevious()) {
        stack.push(child);
      }
    }

    return Optional.empty();
  }

  @RunWith(Parameterized.class)
  public static final class MayEffectMutableStateTest {
    @Parameter public AnalysisCase kase;

    // Always include the index. If two cases have the same name, only one will be executed.
    @Parameters(name = "#{index} {0}")
    public static ImmutableList<AnalysisCase> cases() {
      return ImmutableList.of(
          kase().js("i++").token(INC).expect(true),
          kase().js("[b, [a, i++]]").token(ARRAYLIT).expect(true),
          kase().js("i=3").token(ASSIGN).expect(true),
          kase().js("[0, i=3]").token(ARRAYLIT).expect(true),
          kase().js("b()").token(CALL).expect(true),
          kase().js("b?.()").token(OPTCHAIN_CALL).expect(true),
          kase().js("void b()").token(VOID).expect(true),
          kase().js("[1, b()]").token(ARRAYLIT).expect(true),
          kase().js("b.b=4").token(ASSIGN).expect(true),
          kase().js("b.b--").token(DEC).expect(true),
          kase().js("i--").token(DEC).expect(true),
          kase().js("a[0][i=4]").token(GETELEM).expect(true),
          kase().js("a?.[0][i=4]").token(OPTCHAIN_GETELEM).expect(true),
          kase().js("a += 3").token(ASSIGN_ADD).expect(true),
          kase().js("a ||= b").token(ASSIGN_OR).expect(true),
          kase().js("a &&= b").token(ASSIGN_AND).expect(true),
          kase().js("a ??= b").token(ASSIGN_COALESCE).expect(true),
          kase().js("a, b, z += 4").token(COMMA).expect(true),
          kase().js("a ? c : d++").token(HOOK).expect(true),
          kase().js("a ?? b++").token(COALESCE).expect(true),
          kase().js("a + c++").token(ADD).expect(true),
          kase().js("a + c - d()").token(SUB).expect(true),
          kase().js("function foo() {}").token(FUNCTION).expect(true),
          kase().js("while(true);").token(WHILE).expect(true),
          kase().js("if(true){a()}").token(IF).expect(true),
          kase().js("if(true){a}").token(IF).expect(false),
          kase().js("(function() { })").token(FUNCTION).expect(true),
          kase().js("(function() { i++ })").token(FUNCTION).expect(true),
          kase().js("[function a(){}]").token(ARRAYLIT).expect(true),
          kase().js("a").token(NAME).expect(false),
          kase().js("[b, c [d, [e]]]").token(ARRAYLIT).expect(true),
          kase().js("({a: x, b: y, c: z})").token(OBJECTLIT).expect(true),
          // Note: RegExp objects are not immutable, for instance, the exec
          // method maintains state for "global" searches.
          kase().js("/abc/gi").token(REGEXP).expect(true),
          kase().js("'a'").token(Token.STRINGLIT).expect(false),
          kase().js("0").token(NUMBER).expect(false),
          kase().js("1n").token(BIGINT).expect(false),
          kase().js("a + c").token(ADD).expect(false),
          kase().js("'c' + a[0]").token(ADD).expect(false),
          kase().js("a[0][1]").token(GETELEM).expect(false),
          kase().js("a?.[0][1]").token(OPTCHAIN_GETELEM).expect(false),
          kase().js("'a' + c").token(ADD).expect(false),
          kase().js("'a' + a.name").token(ADD).expect(false),
          kase().js("1, 2, 3").token(COMMA).expect(false),
          kase().js("a, b, 3").token(COMMA).expect(false),
          kase().js("(function(a, b) {  })").token(FUNCTION).expect(true),
          kase().js("a ? c : d").token(HOOK).expect(false),
          kase().js("a ?? b").token(COALESCE).expect(false),
          kase().js("'1' + navigator.userAgent").token(ADD).expect(false),
          kase().js("new RegExp('foobar', 'i')").token(NEW).expect(true),
          kase().js("new RegExp(SomethingWacky(), 'i')").token(NEW).expect(true),
          kase().js("new Array()").token(NEW).expect(true),
          kase().js("new Array").token(NEW).expect(true),
          kase().js("new Array(4)").token(NEW).expect(true),
          kase().js("new Array('a', 'b', 'c')").token(NEW).expect(true),
          kase().js("new SomeClassINeverHeardOf()").token(NEW).expect(true),

          // Getters and setters - object rest and spread
          kase().js("({...x});").token(OBJECT_SPREAD).assumeGettersArePure(false).expect(true),
          kase()
              .js("const {...x} = y;")
              .token(OBJECT_REST)
              .assumeGettersArePure(false)
              .expect(true),
          kase().js("({...x});").token(OBJECT_SPREAD).assumeGettersArePure(true).expect(false),
          kase()
              .js("const {...x} = y;")
              .token(OBJECT_REST)
              .assumeGettersArePure(true)
              .expect(false),
          kase().js("({...f().x});").token(OBJECT_SPREAD).assumeGettersArePure(true).expect(true),
          kase().js("({...f().x} = y);").token(OBJECT_REST).assumeGettersArePure(true).expect(true),

          // the presence of `a` affects what gets put into `x`
          kase().js("({a, ...x} = y);").token(STRING_KEY).assumeGettersArePure(true).expect(true),

          // Getters and setters
          kase().js("x.getter;").token(GETPROP).expect(true),
          kase().js("x?.getter;").token(OPTCHAIN_GETPROP).expect(true),
          // Overapproximation to avoid inspecting the parent.
          kase().js("x.setter;").token(GETPROP).expect(true),
          kase().js("x?.setter;").token(OPTCHAIN_GETPROP).expect(true),
          kase().js("x.normal;").token(GETPROP).expect(false),
          kase().js("x?.normal;").token(OPTCHAIN_GETPROP).expect(false),
          kase().js("const {getter} = x;").token(STRING_KEY).expect(true),
          // Overapproximation to avoid inspecting the parent.
          kase().js("const {setter} = x;").token(STRING_KEY).expect(false),
          kase().js("const {normal} = x;").token(STRING_KEY).expect(false),
          kase().js("x.getter = 0;").token(GETPROP).expect(true),
          kase().js("x.setter = 0;").token(GETPROP).expect(true),
          kase().js("x.normal = 0;").token(GETPROP).expect(false),

          // Default values delegates to children.
          kase().js("({x = 0} = y);").token(DEFAULT_VALUE).expect(false),
          kase().js("([x = 0] = y);").token(DEFAULT_VALUE).expect(false),
          kase().js("function f(x = 0) { };").token(DEFAULT_VALUE).expect(false),

          // Dynamic import can mutate global state
          kase().js("import('./module.js')").token(DYNAMIC_IMPORT).expect(true));
    }

    @Test
    public void mayEffectMutableState() {
      ParseHelper helper = new ParseHelper();
      Node node = helper.parseCase(kase);
      AstAnalyzer analyzer = helper.getAstAnalyzer();
      assertThat(analyzer.mayEffectMutableState(node)).isEqualTo(kase.expect);
    }
  }

  @RunWith(Parameterized.class)
  public static final class MayHaveSideEffects {

    @Parameter public AnalysisCase kase;

    @Test
    public void test() {
      ParseHelper helper = new ParseHelper();
      Node node = helper.parseCase(kase);
      assertThat(helper.getAstAnalyzer().mayHaveSideEffects(node)).isEqualTo(kase.expect);
    }

    // Always include the index. If two cases have the same name, only one will be executed.
    @Parameters(name = "#{index} {0}")
    public static ImmutableList<AnalysisCase> cases() {
      return ImmutableList.of(
          // Cases in need of differentiation.
          kase().expect(false).js("[1]"),
          kase().expect(false).js("[1, 2]"),
          kase().expect(false).js("[1, 2]").assumeBuiltinsPure(false),
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
          kase().expect(true).js("a ?? b++"),
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
          kase().expect(false).js("a").assumeBuiltinsPure(false),
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
          kase().expect(false).js("a ?? b"),
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
          kase().expect(true).js("new Array()").assumeBuiltinsPure(false),
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
          kase().expect(true).js("Math.random();").assumeBuiltinsPure(false),
          kase().expect(true).js("Math.random(seed);"),
          kase().expect(true).js("Math.random(seed);").assumeBuiltinsPure(false),
          kase().expect(false).js("Math.sin(1, 10);"),
          kase().expect(true).js("Math.sin(1, 10);").assumeBuiltinsPure(false),
          kase().expect(false).js("[1, 1].foo;"),
          kase().expect(true).js("export var x = 0;"),
          kase().expect(true).js("export let x = 0;"),
          kase().expect(true).js("export const x = 0;"),
          kase().expect(true).js("export class X {};"),
          kase().expect(true).js("export function x() {};"),
          kase().expect(true).js("export {x};"),

          // ARRAYLIT-ITER_SPREAD
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

          // CALL-ITER_SPREAD
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

          // NEW-ITER_SPREAD
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

          // OBJECT_SPREAD
          // These could all invoke getters.
          kase().expect(true).js("({...x})"),
          kase().expect(true).js("({...{}})"),
          kase().expect(true).js("({...{a:1}})"),
          kase().expect(true).js("({...{a:i++}})"),
          kase().expect(true).js("({...{a:f()}})"),
          kase().expect(true).js("({...f()})"),

          // OBJECT_REST
          // This could invoke getters.
          kase().expect(true).token(OBJECT_REST).js("({...x} = something)"),
          // the presence of `a` affects what goes into `x`
          kase().expect(true).token(STRING_KEY).js("({a, ...x} = something)"),

          // ITER_REST
          // We currently assume all iterable-rests are side-effectful.
          kase().expect(true).token(ITER_REST).js("([...x] = 'safe')"),
          kase().expect(false).token(ITER_REST).js("(function(...x) { })"),

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
          kase().expect(true).token(OPTCHAIN_GETPROP).js("x?.getter;"),
          // Overapproximation because to avoid inspecting the parent.
          kase().expect(true).token(GETPROP).js("x.setter;"),
          kase().expect(true).token(OPTCHAIN_GETPROP).js("x?.setter;"),
          kase().expect(false).token(GETPROP).js("x.normal;"),
          kase().expect(false).token(OPTCHAIN_GETPROP).js("x?.normal;"),
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

          // MEMBER_FIELD_DEF
          kase().expect(false).token(MEMBER_FIELD_DEF).js("class C { x=2; }"),
          kase().expect(false).token(MEMBER_FIELD_DEF).js("class C { x; }"),
          kase().expect(false).token(MEMBER_FIELD_DEF).js("class C { x }"),
          kase().expect(false).token(MEMBER_FIELD_DEF).js("class C { x \n y }"),
          kase().expect(false).token(MEMBER_FIELD_DEF).js("class C { static x=2; }"),
          kase().expect(false).token(MEMBER_FIELD_DEF).js("class C { static x; }"),
          kase().expect(false).token(MEMBER_FIELD_DEF).js("class C { static x }"),
          kase().expect(false).token(MEMBER_FIELD_DEF).js("class C { static x \n static y }"),
          kase().expect(false).token(MEMBER_FIELD_DEF).js("class C { x = alert(1); }"),
          kase().expect(true).token(MEMBER_FIELD_DEF).js("class C { static x = alert(1); }"),

          // COMPUTED_FIELD_DEF
          kase().expect(false).token(COMPUTED_FIELD_DEF).js("class C { [x]; }"),
          kase().expect(false).token(COMPUTED_FIELD_DEF).js("class C { ['x']=2; }"),
          kase().expect(false).token(COMPUTED_FIELD_DEF).js("class C { 'x'=2; }"),
          kase().expect(false).token(COMPUTED_FIELD_DEF).js("class C { 1=2; }"),
          kase().expect(false).token(COMPUTED_FIELD_DEF).js("class C { static [x]; }"),
          kase().expect(false).token(COMPUTED_FIELD_DEF).js("class C { static ['x']=2; }"),
          kase().expect(false).token(COMPUTED_FIELD_DEF).js("class C { static 'x'=2; }"),
          kase().expect(false).token(COMPUTED_FIELD_DEF).js("class C { static 1=2; }"),
          kase().expect(false).token(COMPUTED_FIELD_DEF).js("class C { ['x'] = alert(1); }"),
          kase().expect(true).token(COMPUTED_FIELD_DEF).js("class C { static ['x'] = alert(1); }"),
          kase().expect(true).token(COMPUTED_FIELD_DEF).js("class C { static [alert(1)] = 2; }"),

          // CLASS_STATIC_BLOCK
          kase().expect(false).token(BLOCK).js("class C { static {} }"),
          kase().expect(false).token(BLOCK).js("class C { static { [1]; } }"),
          kase().expect(true).token(BLOCK).js("class C { static { let x; } }"),
          kase().expect(true).token(BLOCK).js("class C { static { const x =1 ; } }"),
          kase().expect(true).token(BLOCK).js("class C { static { var x; } }"),
          kase().expect(true).token(BLOCK).js("class C { static { this.x = 1; } }"),
          kase().expect(true).token(BLOCK).js("class C { static { function f() { } } }"),
          kase().expect(false).token(BLOCK).js("class C { static { (function () {} )} }"),
          kase().expect(false).token(BLOCK).js("class C { static { ()=>{} } }"),

          // SUPER calls
          kase().expect(false).token(SUPER).js("super()"),
          kase().expect(false).token(SUPER).js("super.foo()"),

          // IObject methods
          // "toString" and "valueOf" are by default assumed to be side-effect free
          kase().expect(false).js("o.toString()"),
          kase().expect(false).js("o.valueOf()"),
          // "toString" and "valueOf" are not assumed to be side-effect free when
          // assumeKnownBuiltinsArePure is false.
          kase().expect(true).js("o.toString()").assumeBuiltinsPure(false),
          kase().expect(true).js("o.valueOf()").assumeBuiltinsPure(false),
          // other methods depend on the extern definitions
          kase().expect(true).js("o.watch()"),

          // A RegExp Object by itself doesn't have any side-effects
          kase().expect(false).js("/abc/gi").globalRegExp(true),
          kase().expect(false).js("/abc/gi").globalRegExp(false),

          // RegExp instance methods have global side-effects, so whether they are
          // considered side-effect free depends on whether the global properties
          // are referenced and whether we assume that the built-in methods are not overwritten
          // with impure variants.
          kase().expect(true).js("(/abc/gi).test('')").globalRegExp(true),
          kase().expect(false).js("(/abc/gi).test('')").globalRegExp(false),
          kase()
              .expect(true)
              .js("(/abc/gi).test('')")
              .globalRegExp(false)
              .assumeBuiltinsPure(false),
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
          kase().expect(false).js("'a'.replace(/a/, 'x')").globalRegExp(false),

          // Dynamic import changes global state
          kase().expect(true).token(DYNAMIC_IMPORT).js("import('./module.js')"));
    }
  }

  @RunWith(Parameterized.class)
  public static final class NodeTypeMayHaveSideEffects {
    @Parameter public AnalysisCase kase;

    // Always include the index. If two cases have the same name, only one will be executed.
    @Parameters(name = "#{index} {0}")
    public static ImmutableList<AnalysisCase> cases() {
      return ImmutableList.of(
          kase().js("x = y").token(ASSIGN).expect(true),
          kase().js("x += y").token(ASSIGN_ADD).expect(true),
          kase().js("delete x.y").token(DELPROP).expect(true),
          kase().js("x++").token(INC).expect(true),
          kase().js("x--").token(DEC).expect(true),
          kase().js("for (prop in obj) {}").token(FOR_IN).expect(true),
          kase().js("for (item of iterable) {}").token(FOR_OF).expect(true),
          // name declaration has a side effect when a value is actually assigned
          kase().js("var x = 1;").token(NAME).expect(true),
          kase().js("let x = 1;").token(NAME).expect(true),
          kase().js("const x = 1;").token(NAME).expect(true),
          // don't consider name declaration to have a side effect if there's no assignment
          kase().js("var x;").token(NAME).expect(false),
          kase().js("let x;").token(NAME).expect(false),

          // destructuring declarations and assignments are always considered side effectful even
          // when empty
          kase().js("var {x} = {};").token(DESTRUCTURING_LHS).expect(true),
          kase().js("var {} = {};").token(DESTRUCTURING_LHS).expect(true),
          kase().js("var [x] = [];").token(DESTRUCTURING_LHS).expect(true),
          kase().js("var {y: [x]} = {};").token(OBJECT_PATTERN).expect(false),
          kase().js("var {y: [x]} = {};").token(ARRAY_PATTERN).expect(false),
          kase().js("[x] = arr;").token(ASSIGN).expect(true),

          // NOTE: CALL and NEW nodes are delegated to functionCallHasSideEffects() and
          // constructorCallHasSideEffects(), respectively. The cases below are just a few
          // representative examples that are convenient to test here.
          //
          // in general function and constructor calls are assumed to have side effects
          kase().js("foo();").token(CALL).expect(true),
          kase().js("foo?.();").token(OPTCHAIN_CALL).expect(true),
          kase().js("new Foo();").token(NEW).expect(true),
          // Object() is known not to have side-effects, though
          kase().js("Object();").token(CALL).expect(false),
          kase().js("Object?.();").token(OPTCHAIN_CALL).expect(false),
          kase().js("new Object();").token(NEW).expect(false),
          // TAGGED_TEMPLATELIT is just a special syntax for a CALL.
          kase().js("foo`template`;").token(TAGGED_TEMPLATELIT).expect(true),

          // NOTE: ITER_REST and ITER_SPREAD are delegated to NodeUtil.iteratesImpureIterable()
          // Test cases here are just easy to test representative examples.
          kase().js("[...[1, 2, 3]]").token(ITER_SPREAD).expect(false),
          // unknown iterable, so assume side-effects
          kase().js("[...someIterable]").token(ITER_SPREAD).expect(true),
          // we just assume the rhs of an array pattern may have iteration side-effects
          // without looking too closely.
          kase().js("let [...rest] = [1, 2, 3];").token(ITER_REST).expect(true),
          // ITER_REST in parameter list does not trigger iteration at the function definition, so
          // it has no side effects.
          kase().js("function foo(...rest) {}").token(ITER_REST).expect(false),

          // defining a class or a function is not considered to be a side-effect
          kase().js("function foo() {}").token(FUNCTION).expect(false),
          kase().js("class Foo {}").token(CLASS).expect(false),

          // arithmetic, logic, and bitwise operations do not have side-effects
          kase().js("x + y").token(ADD).expect(false),
          kase().js("x || y").token(OR).expect(false),
          kase().js("x | y").token(BITOR).expect(false),
          kase().js("x ?? y").token(COALESCE).expect(false),

          // Getters and setters
          kase().js("({...x});").token(OBJECT_SPREAD).expect(true),
          kase().js("const {...x} = y;").token(OBJECT_REST).expect(true),
          kase().js("y.getter;").token(GETPROP).expect(true),
          kase().js("y?.getter;").token(OPTCHAIN_GETPROP).expect(true),
          kase().js("y.setter;").token(GETPROP).expect(true),
          kase().js("y?.setter;").token(OPTCHAIN_GETPROP).expect(true),
          kase().js("y.normal;").token(GETPROP).expect(false),
          kase().js("y?.normal;").token(OPTCHAIN_GETPROP).expect(false),
          kase().js("const {getter} = y;").token(STRING_KEY).expect(true),
          kase().js("const {setter} = y;").token(STRING_KEY).expect(false),
          kase().js("const {normal} = y;").token(STRING_KEY).expect(false),
          kase().js("y.getter = 0;").token(GETPROP).expect(true),
          kase().js("y.setter = 0;").token(GETPROP).expect(true),
          kase().js("y.normal = 0;").token(GETPROP).expect(false),

          // Dynamic import causes side effects
          kase().js("import('./module.js')").token(DYNAMIC_IMPORT).expect(true));
    }

    @Test
    public void test() {
      ParseHelper helper = new ParseHelper();
      Node node = helper.parseCase(kase);
      assertThat(helper.getAstAnalyzer().nodeTypeMayHaveSideEffects(node)).isEqualTo(kase.expect);
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

      // Cannot determine this evaluates to a local value (even though it does in practice).
      assertThat(NodeUtil.evaluatesToLocalValue(newXDotMethodCall)).isFalse();
      assertThat(astAnalyzer.functionCallHasSideEffects(newXDotMethodCall)).isFalse();
      assertThat(astAnalyzer.mayHaveSideEffects(newXDotMethodCall)).isFalse();

      // Modifies this, local result
      flags.clearAllFlags();
      newExpr.setSideEffectFlags(flags);
      flags.clearAllFlags();
      flags.setMutatesThis();
      newXDotMethodCall.setSideEffectFlags(flags);

      assertThat(NodeUtil.evaluatesToLocalValue(newXDotMethodCall)).isFalse();
      assertThat(astAnalyzer.functionCallHasSideEffects(newXDotMethodCall)).isFalse();
      assertThat(astAnalyzer.mayHaveSideEffects(newXDotMethodCall)).isFalse();

      // Modifies this, non-local result
      flags.clearAllFlags();
      newExpr.setSideEffectFlags(flags);
      flags.clearAllFlags();
      flags.setMutatesThis();
      newXDotMethodCall.setSideEffectFlags(flags);

      assertThat(NodeUtil.evaluatesToLocalValue(newXDotMethodCall)).isFalse();
      assertThat(astAnalyzer.functionCallHasSideEffects(newXDotMethodCall)).isFalse();
      assertThat(astAnalyzer.mayHaveSideEffects(newXDotMethodCall)).isFalse();

      // No modifications, non-local result
      flags.clearAllFlags();
      newExpr.setSideEffectFlags(flags);
      flags.clearAllFlags();
      newXDotMethodCall.setSideEffectFlags(flags);

      assertThat(NodeUtil.evaluatesToLocalValue(newXDotMethodCall)).isFalse();
      assertThat(astAnalyzer.functionCallHasSideEffects(newXDotMethodCall)).isFalse();
      assertThat(astAnalyzer.mayHaveSideEffects(newXDotMethodCall)).isFalse();

      // The new modifies global state, no side-effect call
      // This call could be removed, but not the new.
      flags.clearAllFlags();
      flags.setMutatesGlobalState();
      newExpr.setSideEffectFlags(flags);
      flags.clearAllFlags();
      newXDotMethodCall.setSideEffectFlags(flags);

      // This does evaluate to a local value but NodeUtil does not know that
      assertThat(NodeUtil.evaluatesToLocalValue(newXDotMethodCall)).isFalse();
      assertThat(astAnalyzer.functionCallHasSideEffects(newXDotMethodCall)).isFalse();
      assertThat(astAnalyzer.mayHaveSideEffects(newXDotMethodCall)).isTrue();
    }

    @Test
    public void testStringMethodCallSideEffects_noTypesForLocalOptimizations() {
      ParseHelper helper = new ParseHelper();

      Node xDotReplaceCall = helper.parseFirst(CALL, "x.replace(/xyz/g, '');");
      AstAnalyzer astAnalyzer = helper.getAstAnalyzer();
      assertThat(astAnalyzer.functionCallHasSideEffects(xDotReplaceCall)).isTrue();

      helper.hasGlobalRegexpReferences = false;
      astAnalyzer = helper.getAstAnalyzer();
      assertThat(astAnalyzer.functionCallHasSideEffects(xDotReplaceCall)).isTrue();

      Node xNode = xDotReplaceCall.getFirstFirstChild();
      xNode.setJSType(helper.typeRegistry.getNativeType(JSTypeNative.STRING_TYPE));
      assertThat(astAnalyzer.functionCallHasSideEffects(xDotReplaceCall)).isTrue();
    }

    @Test
    public void testTypeBasedStringMethodCallSideEffects_useTypesForLocalOptimziations() {
      ParseHelper helper = new ParseHelper();
      helper.useTypesForLocalOptimizations = true;
      helper.hasGlobalRegexpReferences = false;

      Node xDotReplaceCall = helper.parseFirst(CALL, "x.replace(/xyz/g, '');");
      Node xDotReplaceCallStringType = xDotReplaceCall.cloneTree();
      Node xDotReplaceCallStringColor = xDotReplaceCall.cloneTree();

      JSType stringType = helper.typeRegistry.getNativeType(JSTypeNative.STRING_TYPE);
      xDotReplaceCallStringType.getFirstFirstChild().setJSType(stringType);
      xDotReplaceCallStringColor.getFirstFirstChild().setColor(StandardColors.STRING);
      AstAnalyzer astAnalyzer = helper.getAstAnalyzer();

      assertThat(astAnalyzer.functionCallHasSideEffects(xDotReplaceCall)).isTrue();
      assertThat(astAnalyzer.functionCallHasSideEffects(xDotReplaceCallStringType)).isFalse();
      assertThat(astAnalyzer.functionCallHasSideEffects(xDotReplaceCallStringColor)).isFalse();
    }
  }

  @RunWith(Parameterized.class)
  public static class BuiltInFunctionWithoutSideEffects {

    @Parameter(0)
    public String functionName;

    @Parameters(name = "#{index} {0}")
    public static final ImmutableList<Object[]> cases() {
      return ImmutableList.copyOf(
          new Object[][] {
            {"Object"},
            {"Array"},
            {"String"},
            {"Number"},
            {"BigInt"},
            {"Boolean"},
            {"RegExp"},
            {"Error"}
          });
    }

    @Test
    public void test_assumingPureBuiltins() {
      ParseHelper parseHelper = new ParseHelper();
      parseHelper.assumeBuiltinsPure = true;
      Node func = parseHelper.parseFirst(CALL, String.format("%s(1);", functionName));
      assertThat(parseHelper.getAstAnalyzer().functionCallHasSideEffects(func)).isFalse();
    }

    @Test
    public void test_noAssumePureBuiltins() {
      ParseHelper parseHelper = new ParseHelper();
      parseHelper.assumeBuiltinsPure = false;
      Node func = parseHelper.parseFirst(CALL, String.format("%s(1);", functionName));
      assertThat(parseHelper.getAstAnalyzer().functionCallHasSideEffects(func)).isTrue();
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
    public static final ImmutableList<Object[]> cases() {
      return ImmutableList.copyOf(
          new Object[][] {
            {"Array"}, {"Date"}, {"Error"}, {"Object"}, {"RegExp"}, {"XMLHttpRequest"}
          });
    }

    @Test
    public void noSideEffectsForKnownConstructor() {
      ParseHelper parseHelper = new ParseHelper();
      Node newNode = parseHelper.parseFirst(NEW, String.format("new %s();", constructorName));
      AstAnalyzer astAnalyzer = parseHelper.getAstAnalyzer();
      // we know nothing about the class being instantiated, so assume side effects occur.
      assertThat(astAnalyzer.constructorCallHasSideEffects(newNode)).isFalse();
    }
  }
}
