/*
 * Copyright 2026 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link EvaluationOrder}. */
@RunWith(JUnit4.class)
public final class EvaluationOrderTest {

  private Compiler compiler;

  @Before
  public void setUp() {
    this.compiler = new Compiler();
  }

  @Test
  public void isConditionalChild_shortCircuitAndHook() {
    for (String op : new String[] {"&&", "||", "??", "&&=", "||=", "??="}) {
      Node n = parseExpr("a " + op + " b");
      assertThat(EvaluationOrder.isConditionalChild(n.getFirstChild())).isFalse();
      assertThat(EvaluationOrder.isConditionalChild(n.getSecondChild())).isTrue();
    }

    Node hook = parseExpr("a ? b : c");
    assertThat(EvaluationOrder.isConditionalChild(hook.getFirstChild())).isFalse();
    assertThat(EvaluationOrder.isConditionalChild(hook.getSecondChild())).isTrue();
    assertThat(EvaluationOrder.isConditionalChild(hook.getLastChild())).isTrue();
  }

  @Test
  public void isConditionalChild_optionalChains() {
    Node call = parseExpr("a?.(b, c)");
    assertThat(call.getToken()).isEqualTo(Token.OPTCHAIN_CALL);
    assertThat(EvaluationOrder.isConditionalChild(call.getFirstChild())).isFalse();
    assertThat(EvaluationOrder.isConditionalChild(call.getSecondChild())).isTrue();
    assertThat(EvaluationOrder.isConditionalChild(call.getLastChild())).isTrue();

    Node getElem = parseExpr("a?.[b]");
    assertThat(getElem.getToken()).isEqualTo(Token.OPTCHAIN_GETELEM);
    assertThat(EvaluationOrder.isConditionalChild(getElem.getFirstChild())).isFalse();
    assertThat(EvaluationOrder.isConditionalChild(getElem.getSecondChild())).isTrue();

    Node getProp = parseExpr("a?.b");
    assertThat(EvaluationOrder.isConditionalChild(getProp.getFirstChild())).isFalse();
  }

  @Test
  public void isConditionalChild_defaultValue() {
    Node defaultValue = findFirst(parse("[a = b] = arr;"), Token.DEFAULT_VALUE);
    assertThat(EvaluationOrder.isConditionalChild(defaultValue.getFirstChild())).isFalse();
    assertThat(EvaluationOrder.isConditionalChild(defaultValue.getSecondChild())).isTrue();

    Node paramDefault = findFirst(parse("function f(a = b) {}"), Token.DEFAULT_VALUE);
    assertThat(EvaluationOrder.isConditionalChild(paramDefault.getSecondChild())).isTrue();
  }

  @Test
  public void isConditionalChild_unconditionalChildren() {
    Node call = parseExpr("f(a, b)");
    for (Node child = call.getFirstChild(); child != null; child = child.getNext()) {
      assertThat(EvaluationOrder.isConditionalChild(child)).isFalse();
    }
    Node add = parseExpr("a + b");
    assertThat(EvaluationOrder.isConditionalChild(add.getSecondChild())).isFalse();

    // Statement-level control flow is intentionally out of scope.
    Node ifNode = parse("if (a) { b(); } else { c(); }").getFirstChild();
    assertThat(EvaluationOrder.isConditionalChild(ifNode.getSecondChild())).isFalse();
    assertThat(EvaluationOrder.isConditionalChild(ifNode.getLastChild())).isFalse();

    assertThat(EvaluationOrder.isConditionalChild(IR.name("detached"))).isFalse();
  }

  @Test
  public void evaluatesLastChildFirst() {
    assertThat(
            EvaluationOrder.evaluatesLastChildFirst(
                firstOf("const [a] = rhs;", Token.DESTRUCTURING_LHS)))
        .isTrue();
    assertThat(
            EvaluationOrder.evaluatesLastChildFirst(
                firstOf("const {a} = rhs;", Token.DESTRUCTURING_LHS)))
        .isTrue();
    // No initializer.
    assertThat(
            EvaluationOrder.evaluatesLastChildFirst(
                firstOf("for (const [a] of arr) {}", Token.DESTRUCTURING_LHS)))
        .isFalse();

    assertThat(EvaluationOrder.evaluatesLastChildFirst(firstOf("[a] = rhs;", Token.ASSIGN)))
        .isTrue();
    assertThat(EvaluationOrder.evaluatesLastChildFirst(firstOf("({a} = rhs);", Token.ASSIGN)))
        .isTrue();
    assertThat(EvaluationOrder.evaluatesLastChildFirst(firstOf("a = rhs;", Token.ASSIGN)))
        .isFalse();
    assertThat(EvaluationOrder.evaluatesLastChildFirst(firstOf("o.p = rhs;", Token.ASSIGN)))
        .isFalse();

    assertThat(
            EvaluationOrder.evaluatesLastChildFirst(firstOf("[a = d] = arr;", Token.DEFAULT_VALUE)))
        .isTrue();
    assertThat(
            EvaluationOrder.evaluatesLastChildFirst(
                firstOf("[[a] = d] = arr;", Token.DEFAULT_VALUE)))
        .isTrue();
    assertThat(
            EvaluationOrder.evaluatesLastChildFirst(
                firstOf("function f(a = d) {}", Token.DEFAULT_VALUE)))
        .isTrue();
    // Property targets evaluate the target reference before the default value.
    assertThat(
            EvaluationOrder.evaluatesLastChildFirst(
                firstOf("[o.p = d] = arr;", Token.DEFAULT_VALUE)))
        .isFalse();
    assertThat(
            EvaluationOrder.evaluatesLastChildFirst(
                firstOf("[o[k] = d] = arr;", Token.DEFAULT_VALUE)))
        .isFalse();

    assertThat(EvaluationOrder.evaluatesLastChildFirst(parseExpr("f(a, b)"))).isFalse();
  }

  @Test
  public void isEvaluatedBeforeSibling() {
    Node call = parseExpr("f(a, b)");
    Node f = call.getFirstChild();
    Node a = f.getNext();
    Node b = a.getNext();
    assertThat(EvaluationOrder.isEvaluatedBeforeSibling(f, b)).isTrue();
    assertThat(EvaluationOrder.isEvaluatedBeforeSibling(a, b)).isTrue();
    assertThat(EvaluationOrder.isEvaluatedBeforeSibling(b, a)).isFalse();
    assertThat(EvaluationOrder.isEvaluatedBeforeSibling(a, a)).isFalse();

    Node assign = firstOf("[x] = rhs;", Token.ASSIGN);
    assertThat(
            EvaluationOrder.isEvaluatedBeforeSibling(assign.getLastChild(), assign.getFirstChild()))
        .isTrue();
    assertThat(
            EvaluationOrder.isEvaluatedBeforeSibling(assign.getFirstChild(), assign.getLastChild()))
        .isFalse();

    Node memberDefault = firstOf("[o[k] = d] = arr;", Token.DEFAULT_VALUE);
    assertThat(
            EvaluationOrder.isEvaluatedBeforeSibling(
                memberDefault.getFirstChild(), memberDefault.getLastChild()))
        .isTrue();

    assertThrows(
        IllegalArgumentException.class, () -> EvaluationOrder.isEvaluatedBeforeSibling(call, a));
  }

  @Test
  public void isExecutedAfterInit_sameNodeReturnsFalse() {
    Node script = parse("if (c) { init; }");
    ScopedNode init = checkNotNull(getScopedNameNodes(script).get("init"));

    assertThat(
            EvaluationOrder.checkExecutionAfter(init.node(), init.scope())
                .isExecutedAfterInit(init.node(), init.scope()))
        .isFalse();
  }

  @Test
  public void isExecutedAfterInit_sequentialStatements() {
    assertThat(executesAfterInitTestCase("init; ref;")).isTrue();
    assertThat(executesAfterInitTestCase("ref; init;")).isFalse();
    assertThat(executesAfterInitTestCase("if (c) { init; alert(1); use(ref); }")).isTrue();
    assertThat(executesAfterInitTestCase("if (c) { use(ref); alert(1); init; }")).isFalse();
    assertThat(executesAfterInitTestCase("if (c) { init; if (inner) { use(ref); } }")).isTrue();
    assertThat(executesAfterInitTestCase("if (c) { inner && init; alert(1); use(ref); }"))
        .isFalse();
    assertThat(executesAfterInitTestCase("if (c) { if (inner) { init; } use(ref); }")).isFalse();
  }

  @Test
  public void isExecutedAfterInit_shortCircuitAndTernaryExpressions() {
    assertThat(executesAfterInitTestCase("init && ref;")).isTrue();
    assertThat(executesAfterInitTestCase("init || ref;")).isTrue();
    assertThat(executesAfterInitTestCase("init ?? ref;")).isTrue();
    assertThat(executesAfterInitTestCase("c && (init.prop ?? (ref.prop = 1));")).isTrue();
    assertThat(executesAfterInitTestCase("(c && init) || ref;")).isFalse();

    assertThat(executesAfterInitTestCase("init ? ref : 1;")).isTrue();
    assertThat(executesAfterInitTestCase("init ? 1 : ref;")).isTrue();
    assertThat(executesAfterInitTestCase("c ? init : ref;")).isFalse();
    assertThat(executesAfterInitTestCase("c ? ref : init;")).isFalse();

    assertThat(executesAfterInitTestCase("init.x ||= ref.y;")).isTrue();
    assertThat(executesAfterInitTestCase("init.x &&= ref.y;")).isTrue();
    assertThat(executesAfterInitTestCase("init.x ??= ref.y;")).isTrue();
    assertThat(executesAfterInitTestCase("if (c) { a ||= init; use(ref); }")).isFalse();
    assertThat(executesAfterInitTestCase("if (c) { a &&= init; use(ref); }")).isFalse();
    assertThat(executesAfterInitTestCase("if (c) { a ??= init; use(ref); }")).isFalse();

    assertThat(executesAfterInitTestCase("init?.(ref);")).isTrue();
    assertThat(executesAfterInitTestCase("init?.[ref];")).isTrue();
    assertThat(executesAfterInitTestCase("if (c) { a?.(init); use(ref); }")).isFalse();
  }

  @Test
  public void isExecutedAfterInit_controlStructures() {
    assertThat(executesAfterInitTestCase("if (init) { use(ref); }")).isTrue();
    assertThat(executesAfterInitTestCase("if (init) {} else { use(ref); }")).isTrue();
    assertThat(executesAfterInitTestCase("if (c) { init; } else { use(ref); }")).isFalse();

    assertThat(executesAfterInitTestCase("switch (init) { case 1: use(ref); }")).isTrue();
    assertThat(executesAfterInitTestCase("switch (init) { case ref: break; }")).isTrue();
    assertThat(executesAfterInitTestCase("switch (x) { case 1: init; use(ref); }")).isTrue();
    assertThat(executesAfterInitTestCase("switch (x) { case init: use(ref); }")).isFalse();
    assertThat(executesAfterInitTestCase("switch (x) { case 1: case init: use(ref); }")).isFalse();
    assertThat(executesAfterInitTestCase("switch (x) { case init: case 2: use(ref); }")).isFalse();
    assertThat(executesAfterInitTestCase("switch (x) { case 1: init; case 2: use(ref); }"))
        .isFalse();

    assertThat(executesAfterInitTestCase("if (c) { init; label: { use(ref); } }")).isTrue();
    assertThat(executesAfterInitTestCase("if (c) { label: { init; use(ref); } }")).isTrue();
    assertThat(
            executesAfterInitTestCase(
                "if (c) { label: { if (skip) break label; init; } use(ref); }"))
        .isFalse();

    assertThat(executesAfterInitTestCase("try { init; use(ref); } catch (e) {}")).isTrue();
    assertThat(executesAfterInitTestCase("try { init; } catch (e) { use(ref); }")).isFalse();
    assertThat(executesAfterInitTestCase("try { init; } finally { use(ref); }")).isFalse();
  }

  @Test
  public void isExecutedAfterInit_destructuringAndDefaultValues() {
    assertThat(executesAfterInitTestCase("let {a: x = ref} = init;")).isTrue();
    assertThat(executesAfterInitTestCase("({a: x = ref} = init);")).isTrue();

    assertThat(executesAfterInitTestCase("let {[init]: x} = {a: ref};")).isFalse();
    assertThat(executesAfterInitTestCase("({[init]: x} = {a: ref});")).isFalse();
    assertThat(executesAfterInitTestCase("let [{[init]: x} = {a: ref}] = arr;")).isFalse();

    // A property target is evaluated before its (conditional) default value.
    assertThat(executesAfterInitTestCase("[o[init] = ref] = arr;")).isTrue();
    assertThat(executesAfterInitTestCase("[o[ref] = init] = arr;")).isFalse();
    // A name target is written after its default value is evaluated.
    assertThat(executesAfterInitTestCase("[init = ref] = arr;")).isFalse();
  }

  @Test
  public void isExecutedAfterInit_hoistScopesAndClasses() {
    assertThat(executesAfterInitTestCase("if (c) { init; function f() { return ref; } }"))
        .isFalse();
    assertThat(executesAfterInitTestCase("if (c) { init; const f = () => ref; }")).isFalse();
    assertThat(executesAfterInitTestCase("if (c) { init; class C { static { use(ref); } } }"))
        .isFalse();
    assertThat(executesAfterInitTestCase("if (c) { init; class C { x = ref; } }")).isFalse();
    assertThat(executesAfterInitTestCase("if (c) { class C { static x = init; [ref]() {} } }"))
        .isFalse();
    assertThat(executesAfterInitTestCase("if (c) { class C { x = init; static y = ref; } }"))
        .isFalse();
  }

  private record ScopedNode(Node node, Scope scope) {}

  private boolean executesAfterInitTestCase(String js) {
    Node script = parse(js);
    ImmutableMap<String, ScopedNode> nodes = getScopedNameNodes(script);
    ScopedNode init = checkNotNull(nodes.get("init"), "No 'init' node in: %s", js);
    ScopedNode ref = checkNotNull(nodes.get("ref"), "No 'ref' node in: %s", js);
    return EvaluationOrder.checkExecutionAfter(init.node(), init.scope())
        .isExecutedAfterInit(ref.node(), ref.scope());
  }

  private Node parse(String js) {
    Node script = compiler.parseTestCode(js);
    assertThat(compiler.getErrors()).isEmpty();
    return script;
  }

  private Node parseExpr(String jsExpr) {
    return parse("(" + jsExpr + ");").getFirstChild().getFirstChild();
  }

  private Node firstOf(String js, Token token) {
    return findFirst(parse(js), token);
  }

  private static Node findFirst(Node root, Token token) {
    Node found = findFirstOrNull(root, token);
    return checkNotNull(found, "No %s in %s", token, root);
  }

  private static @Nullable Node findFirstOrNull(Node root, Token token) {
    if (root.getToken() == token) {
      return root;
    }
    for (Node child = root.getFirstChild(); child != null; child = child.getNext()) {
      Node found = findFirstOrNull(child, token);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  private ImmutableMap<String, ScopedNode> getScopedNameNodes(Node script) {
    Map<String, ScopedNode> found = new LinkedHashMap<>();
    NodeTraversal.builder()
        .setCompiler(compiler)
        .setCallback((NodeTraversal t, Node n, Node parent) -> {
          if (n.isName()) {
            found.putIfAbsent(n.getString(), new ScopedNode(n, t.getScope()));
          }
        })
        .traverse(script);
    return ImmutableMap.copyOf(found);
  }
}
