/*
 * Copyright 2009 The Closure Compiler Authors.
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
import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.CompilerOptions.LanguageMode.ECMASCRIPT_2017;
import static com.google.javascript.jscomp.FunctionArgumentInjector.findModifiedParameters;
import static com.google.javascript.jscomp.FunctionArgumentInjector.getFunctionCallParameterMap;
import static com.google.javascript.jscomp.FunctionArgumentInjector.inject;
import static com.google.javascript.jscomp.FunctionArgumentInjector.maybeAddTempsForCallArguments;
import static com.google.javascript.jscomp.NodeUtil.getFunctionBody;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.Node;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for the static methods in {@link FunctionArgumentInjector}.
 *
 * @author johnlenz@google.com (John Lenz)
 */
@RunWith(JUnit4.class)
public final class FunctionArgumentInjectorTest {

  private static final ImmutableSet<String> EMPTY_STRING_SET = ImmutableSet.of();

  @Test
  public void testInject0() {
    Compiler compiler = getCompiler();
    Node result =
        inject(
            compiler,
            getFunctionBody(parseFunction("function f(x) { alert(x); }")),
            null,
            ImmutableMap.of("x", parse("null").getFirstFirstChild()));
    assertNode(result).isEqualTo(getFunctionBody(parseFunction("function f(x) { alert(null); }")));
  }

  @Test
  public void testInject1() {
    Compiler compiler = getCompiler();
    Node result =
        inject(
            compiler,
            getFunctionBody(parseFunction("function f() { alert(this); }")),
            null,
            ImmutableMap.of("this", parse("null").getFirstFirstChild()));
    assertNode(result).isEqualTo(getFunctionBody(parseFunction("function f() { alert(null); }")));
  }

  // TODO(johnlenz): Add more unit tests for "inject"

  @Test
  public void testFindModifiedParameters0() {
    assertThat(findModifiedParameters(parseFunction("function f(a){ return a; }"))).isEmpty();
  }

  @Test
  public void testFindModifiedParameters1() {
    assertThat(findModifiedParameters(parseFunction("function f(a){ return a==0; }"))).isEmpty();
  }

  @Test
  public void testFindModifiedParameters2() {
    assertThat(findModifiedParameters(parseFunction("function f(a){ b=a }"))).isEmpty();
  }

  @Test
  public void testFindModifiedParameters3() {
    assertThat(findModifiedParameters(parseFunction("function f(a){ a=0 }"))).containsExactly("a");
  }

  @Test
  public void testFindModifiedParameters4() {
    assertThat(findModifiedParameters(parseFunction("function f(a,b){ a=0;b=0 }")))
        .containsExactly("a", "b");
  }

  @Test
  public void testFindModifiedParameters5() {
    assertThat(findModifiedParameters(parseFunction("function f(a,b){ a; if (a) b=0 }")))
        .containsExactly("b");
  }

  @Test
  public void testFindModifiedParameters6() {
    assertThat(findModifiedParameters(parseFunction("function f(a,b){ function f(){ a;b; } }")))
        .containsExactly("a", "b");
  }

  @Test
  public void testFindModifiedParameters7() {
    assertThat(findModifiedParameters(parseFunction("function f(a,b){ a; function f(){ b; } }")))
        .containsExactly("b");
  }

  @Test
  public void testFindModifiedParameters8() {
    assertThat(
            findModifiedParameters(
                parseFunction("function f(a,b){ a; function f(){ function g() { b; } } }")))
        .containsExactly("b");
  }

  @Test
  public void testFindModifiedParameters9() {
    assertThat(findModifiedParameters(parseFunction("function f(a,b){ (function(){ a;b; }) }")))
        .containsExactly("a", "b");
  }

  @Test
  public void testFindModifiedParameters10() {
    assertThat(findModifiedParameters(parseFunction("function f(a,b){ a; (function (){ b; }) }")))
        .containsExactly("b");
  }

  @Test
  public void testFindModifiedParameters11() {
    assertThat(
            findModifiedParameters(
                parseFunction("function f(a,b){ a; (function(){ (function () { b; }) }) }")))
        .containsExactly("b");
  }

  @Test
  public void testFindModifiedParameters12() {
    assertThat(
            findModifiedParameters(parseFunction("function f(a){ { let a = 1; } }"))).isEmpty();
  }

  @Test
  public void testFindModifiedParameters13() {
    assertThat(
            findModifiedParameters(parseFunction("function f(a){ { const a = 1; } }"))).isEmpty();
  }

  @Test
  public void testFindModifiedParameters14() {
    assertThat(findModifiedParameters(parseFunction("function f(a){ for (a in []) {} }")))
        .containsExactly("a");
  }

  // Note: This is technically incorrect. The parameter a is shadowed, not modified. However, this
  // will just cause the function inliner to do a little bit of unnecessary work; it will not
  // result in incorrect output.
  @Test
  public void testFindModifiedParameters15() {
    assertThat(findModifiedParameters(parseFunction("function f(a){ for (const a in []) {} }")))
        .containsExactly("a");
  }

  @Test
  public void testFindModifiedParameters16() {
    assertThat(findModifiedParameters(parseFunction("function f(a){ for (a of []) {} }")))
        .containsExactly("a");
  }

  @Test
  public void testFindModifiedParameters17() {
    assertThat(findModifiedParameters(parseFunction("function f(a){ [a] = [2]; }")))
        .containsExactly("a");
  }

  @Test
  public void testFindModifiedParameters18() {
    assertThat(findModifiedParameters(parseFunction("function f(a){ var [a] = [2]; }")))
        .containsExactly("a");
  }

  @Test
  public void testMaybeAddTempsForCallArguments1() {
    // Parameters with side-effects must be executed
    // even if they aren't referenced.
    testNeededTemps(
        "function foo(a,b){}; foo(goo(),goo());",
        "foo",
        ImmutableSet.of("a", "b"));
  }

  @Test
  public void testMaybeAddTempsForCallArguments2() {
    // Unreferenced parameters without side-effects
    // can be ignored.
    testNeededTemps(
        "function foo(a,b){}; foo(1,2);",
        "foo",
        EMPTY_STRING_SET);
  }

  @Test
  public void testMaybeAddTempsForCallArguments3() {
    // Referenced parameters without side-effects
    // don't need temps.
    testNeededTemps(
        "function foo(a,b){a;b;}; foo(x,y);",
        "foo",
        EMPTY_STRING_SET);
  }

  @Test
  public void testMaybeAddTempsForCallArguments4() {
    // Parameters referenced after side-effect must
    // be assigned to temps.
    testNeededTemps(
        "function foo(a,b){a;goo();b;}; foo(x,y);",
        "foo",
        ImmutableSet.of("b"));
  }

  @Test
  public void testMaybeAddTempsForCallArguments5() {
    // Parameters referenced after out-of-scope side-effect must
    // be assigned to temps.
    testNeededTemps(
        "function foo(a,b){x = b; y = a;}; foo(x,y);",
        "foo",
        ImmutableSet.of("a"));
  }

  @Test
  public void testMaybeAddTempsForCallArguments6() {
    // Parameter referenced after a out-of-scope side-effect must
    // be assigned to a temp.
    testNeededTemps(
        "function foo(a){x++;a;}; foo(x);",
        "foo",
        ImmutableSet.of("a"));
  }

  @Test
  public void testMaybeAddTempsForCallArguments7() {
    // No temp needed after local side-effects.
    testNeededTemps(
        "function foo(a){var c; c = 0; a;}; foo(x);",
        "foo",
        EMPTY_STRING_SET);

    testNeededTemps(
        "function foo(a){let c; c = 0; a;}; foo(x);",
        "foo",
        EMPTY_STRING_SET);

    testNeededTemps(
        "function foo(a){const c = 0; a;}; foo(x);",
        "foo",
        EMPTY_STRING_SET);
  }

  @Test
  public void testMaybeAddTempsForCallArguments8() {
    // Temp needed for side-effects to object using local name.
    testNeededTemps(
        "function foo(a){var c = {}; c.goo=0; a;}; foo(x);",
        "foo",
        ImmutableSet.of("a"));
  }

  @Test
  public void testMaybeAddTempsForCallArguments9() {
    // Parameters referenced in a loop with side-effects must
    // be assigned to temps.
    testNeededTemps(
        "function foo(a,b){while(true){a;goo();b;}}; foo(x,y);",
        "foo",
        ImmutableSet.of("a", "b"));
  }

  @Test
  public void testMaybeAddTempsForCallArguments10() {
    // No temps for parameters referenced in a loop with no side-effects.
    testNeededTemps(
        "function foo(a,b){while(true){a;true;b;}}; foo(x,y);",
        "foo",
        EMPTY_STRING_SET);
  }

  @Test
  public void testMaybeAddTempsForCallArguments11() {
    // Parameters referenced in a loop with side-effects must
    // be assigned to temps.
    testNeededTemps(
        "function foo(a,b){do{a;b;}while(goo());}; foo(x,y);",
        "foo",
        ImmutableSet.of("a", "b"));
  }

  @Test
  public void testMaybeAddTempsForCallArguments12() {
    // Parameters referenced in a loop with side-effects must
    // be assigned to temps.
    testNeededTemps(
        "function foo(a,b){for(;;){a;b;goo();}}; foo(x,y);",
        "foo",
        ImmutableSet.of("a", "b"));
  }

  @Test
  public void testMaybeAddTempsForCallArguments13() {
    // Parameters referenced in a inner loop without side-effects must
    // be assigned to temps if the outer loop has side-effects.
    testNeededTemps(
        "function foo(a,b){for(;;){for(;;){a;b;}goo();}}; foo(x,y);",
        "foo",
        ImmutableSet.of("a", "b"));
  }

  @Test
  public void testMaybeAddTempsForCallArguments14() {
    // Parameters referenced in a loop must
    // be assigned to temps.
    testNeededTemps(
        "function foo(a,b){goo();for(;;){a;b;}}; foo(x,y);",
        "foo",
        ImmutableSet.of("a", "b"));
  }

  @Test
  public void testMaybeAddTempsForCallArguments20() {
    // A long string referenced more than once should have a temp.
    testNeededTemps(
        "function foo(a){a;a;}; foo(\"blah blah\");",
        "foo",
        ImmutableSet.of("a"));
  }

  @Test
  public void testMaybeAddTempsForCallArguments21() {
    // A short string referenced once should not have a temp.
    testNeededTemps(
        "function foo(a){a;a;}; foo(\"\");",
        "foo",
        EMPTY_STRING_SET);
  }

  @Test
  public void testMaybeAddTempsForCallArguments22() {
    // A object literal not referenced.
    testNeededTemps(
        "function foo(a){}; foo({x:1});",
        "foo",
        EMPTY_STRING_SET);
    // A object literal referenced after side-effect, should have a temp.
    testNeededTemps(
        "function foo(a){alert('foo');a;}; foo({x:1});",
        "foo",
        ImmutableSet.of("a"));
    // A object literal referenced after side-effect, should have a temp.
    testNeededTemps(
        "function foo(a,b){b;a;}; foo({x:1},alert('foo'));",
        "foo",
        ImmutableSet.of("a", "b"));
    // A object literal, referenced more than once, should have a temp.
    testNeededTemps(
        "function foo(a){a;a;}; foo({x:1});",
        "foo",
        ImmutableSet.of("a"));
  }

  @Test
  public void testMaybeAddTempsForCallArguments22b() {
    // A object literal not referenced.
    testNeededTemps(
        "function foo(a){a(this)}; foo.call(f(),g());",
        "foo",
        ImmutableSet.of("a", "this"));
  }

  @Test
  public void testMaybeAddTempsForCallArguments23() {
    // A array literal, not referenced.
    testNeededTemps(
        "function foo(a){}; foo([1,2]);",
        "foo",
        EMPTY_STRING_SET);
    // A array literal, referenced once after side-effect, should have a temp.
    testNeededTemps(
        "function foo(a){alert('foo');a;}; foo([1,2]);",
        "foo",
        ImmutableSet.of("a"));
    // A array literal, referenced more than once, should have a temp.
    testNeededTemps(
        "function foo(a){a;a;}; foo([1,2]);",
        "foo",
        ImmutableSet.of("a"));
  }

  @Test
  public void testMaybeAddTempsForCallArguments24() {
    // A regex literal, not referenced.
    testNeededTemps(
        "function foo(a){}; foo(/mac/);",
        "foo",
        EMPTY_STRING_SET);
    // A regex literal, referenced once after side-effect, should have a temp.
    testNeededTemps(
        "function foo(a){alert('foo');a;}; foo(/mac/);",
        "foo",
        ImmutableSet.of("a"));
    // A regex literal, referenced more than once, should have a temp.
    testNeededTemps(
        "function foo(a){a;a;}; foo(/mac/);",
        "foo",
        ImmutableSet.of("a"));
  }

  @Test
  public void testMaybeAddTempsForCallArguments25() {
    // A side-effect-less constructor, not referenced.
    testNeededTemps(
        "function foo(a){}; foo(new Date());",
        "foo",
        EMPTY_STRING_SET);
    // A side-effect-less constructor, referenced once after sideeffect, should have a temp.
    testNeededTemps(
        "function foo(a){alert('foo');a;}; foo(new Date());",
        "foo",
        ImmutableSet.of("a"));
    // A side-effect-less constructor, referenced more than once, should have
    // a temp.
    testNeededTemps(
        "function foo(a){a;a;}; foo(new Date());",
        "foo",
        ImmutableSet.of("a"));
  }

  @Test
  public void testMaybeAddTempsForCallArguments26() {
    // A constructor, not referenced.
    testNeededTemps(
        "function foo(a){}; foo(new Bar());",
        "foo",
        ImmutableSet.of("a"));
    // A constructor, referenced once after a sideeffect, should have a temp.
    testNeededTemps(
        "function foo(a){alert('foo');a;}; foo(new Bar());",
        "foo",
        ImmutableSet.of("a"));
    // A constructor, referenced more than once, should have a temp.
    testNeededTemps(
        "function foo(a){a;a;}; foo(new Bar());",
        "foo",
        ImmutableSet.of("a"));
  }

  @Test
  public void testMaybeAddTempsForCallArguments27() {
    // Ensure the correct parameter is given a temp, when there is
    // a this value in the call.
    testNeededTemps(
        "function foo(a,b,c){}; foo.call(this,1,goo(),2);",
        "foo",
        ImmutableSet.of("b"));
  }

  @Test
  public void testMaybeAddTempsForCallArguments28() {
    // true/false are don't need temps
    testNeededTemps(
        "function foo(a){a;a;}; foo(true);",
        "foo",
        EMPTY_STRING_SET);
  }

  @Test
  public void testMaybeAddTempsForCallArguments29() {
    // true/false are don't need temps
    testNeededTemps(
        "function foo(a){a;a;}; foo(false);",
        "foo",
        EMPTY_STRING_SET);
  }

  @Test
  public void testMaybeAddTempsForCallArguments30() {
    // true/false are don't need temps
    testNeededTemps(
        "function foo(a){a;a;}; foo(!0);",
        "foo",
        EMPTY_STRING_SET);
  }

  @Test
  public void testMaybeAddTempsForCallArguments31() {
    // true/false are don't need temps
    testNeededTemps(
        "function foo(a){a;a;}; foo(!1);",
        "foo",
        EMPTY_STRING_SET);
  }

  @Test
  public void testMaybeAddTempsForCallArguments32() {
    // void 0 doesn't need a temp
    testNeededTemps(
        "function foo(a){a;a;}; foo(void 0);",
        "foo",
        EMPTY_STRING_SET);
  }

  @Test
  public void testMaybeAddTempsForCallArguments33() {
    // doesn't need a temp
    testNeededTemps(
        "function foo(a){return a;}; foo(new X);",
        "foo",
        EMPTY_STRING_SET);
  }

  @Test
  public void testMaybeAddTempsForCallArgumentsInLoops() {
    // A mutable parameter referenced in loop needs a
    // temporary.
    testNeededTemps(
        "function foo(a){for(;;)a;}; foo(new Bar());",
        "foo",
        ImmutableSet.of("a"));

    testNeededTemps(
        "function foo(a){while(true)a;}; foo(new Bar());",
        "foo",
        ImmutableSet.of("a"));

    testNeededTemps(
        "function foo(a){do{a;}while(true)}; foo(new Bar());",
        "foo",
        ImmutableSet.of("a"));
  }

  @Test
  public void testMaybeAddTempsForCallArgumentsRestParam1() {
    testNeededTemps("function foo(...args) {return args;} foo(1, 2);", "foo", EMPTY_STRING_SET);
  }

  @Test
  public void testMaybeAddTempsForCallArgumentsRestParam2() {
    testNeededTemps(
        "function foo(x, ...args) {return args;} foo(1, 2);", "foo", ImmutableSet.of("args"));
  }

  @Test
  public void testArgMapWithRestParam1() {
    assertArgMapHasKeys(
        "function foo(...args){return args;} foo(1, 2);", "foo", ImmutableSet.of("this", "args"));
  }

  private void assertArgMapHasKeys(String code, String fnName, ImmutableSet<String> expectedKeys) {
    Node n = parse(code);
    Node fn = findFunction(n, fnName);
    assertThat(fn).isNotNull();
    Node call = findCall(n, fnName);
    assertThat(call).isNotNull();
    ImmutableMap<String, Node> actualMap = getFunctionCallParameterMap(fn, call, getNameSupplier());
    assertThat(actualMap.keySet()).isEqualTo(expectedKeys);
  }

  private void testNeededTemps(String code, String fnName, ImmutableSet<String> expectedTemps) {
    Node n = parse(code);
    Node fn = findFunction(n, fnName);
    assertThat(fn).isNotNull();
    Node call = findCall(n, fnName);
    assertThat(call).isNotNull();
    ImmutableMap<String, Node> args =
        ImmutableMap.copyOf(getFunctionCallParameterMap(fn, call, getNameSupplier()));

    Set<String> actualTemps = new HashSet<>();
    maybeAddTempsForCallArguments(
        getCompiler(), fn, args, actualTemps, new ClosureCodingConvention());

    assertThat(actualTemps).isEqualTo(expectedTemps);
  }


  private static Supplier<String> getNameSupplier() {
    return new Supplier<String>() {
      int i = 0;
      @Override
      public String get() {
        return String.valueOf(i++);
      }
    };
  }

  private static Node findCall(Node n, String name) {
    if (n.isCall()) {
      Node callee;
      if (NodeUtil.isGet(n.getFirstChild())) {
        callee = n.getFirstFirstChild();
        Node prop = callee.getNext();
        // Only "call" is supported at this point.
        checkArgument(prop.isString() && prop.getString().equals("call"));
      } else {
        callee = n.getFirstChild();
      }

      if (callee.isName() && callee.getString().equals(name)) {
        return n;
      }
    }

    for (Node c : n.children()) {
      Node result = findCall(c, name);
      if (result != null) {
        return result;
      }
    }

    return null;
  }

  private static Node findFunction(Node n, String name) {
    if (n.isFunction()) {
      if (n.getFirstChild().getString().equals(name)) {
        return n;
      }
    }

    for (Node c : n.children()) {
      Node result = findFunction(c, name);
      if (result != null) {
        return result;
      }
    }

    return null;
  }

  private static Node parseFunction(String js) {
    return parse(js).getFirstChild();
  }

  private static Node parse(String js) {
    Compiler compiler = getCompiler();
    Node n = compiler.parseTestCode(js);
    assertThat(compiler.getErrors()).isEmpty();
    return n;
  }

  private static Compiler getCompiler() {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(ECMASCRIPT_2017);

    compiler.initOptions(options);
    return compiler;
  }
}
