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

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
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
}
