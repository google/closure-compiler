/*
 * Copyright 2007 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.type.FlowScope;
import com.google.javascript.jscomp.type.ReverseAbstractInterpreter;
import com.google.javascript.jscomp.type.SemanticReverseAbstractInterpreter;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SemanticReverseAbstractInterpreterTest extends CompilerTypeTestCase {
{ new GoogleCodingConvention(); }  private ReverseAbstractInterpreter interpreter;
  private TypedScope functionScope;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    interpreter = new SemanticReverseAbstractInterpreter(registry);
  }

  private FlowScope[] newScope() {
    TypedScope globalScope = TypedScope.createGlobalScope(new Node(Token.ROOT));
    functionScope = new TypedScope(globalScope, new Node(Token.FUNCTION));
    return new FlowScope[] {LinkedFlowScope.createEntryLattice(functionScope)};
  }

  /** Tests reverse interpretation of a NAME expression. */
  @Test
  public void testNameCondition() {
    FlowScope[] blind = newScope();
    Node condition = createVar(blind, "a", createNullableType(getNativeStringType()));

    // true outcome.
    FlowScope informedTrue =
        interpreter.getPreciserScopeKnowingConditionOutcome(condition, blind[0], true);
    assertTypeEquals(getNativeStringType(), getVarType(informedTrue, "a"));

    // false outcome.
    FlowScope informedFalse =
        interpreter.getPreciserScopeKnowingConditionOutcome(condition, blind[0], false);
    assertTypeEquals(createNullableType(getNativeStringType()), getVarType(informedFalse, "a"));
  }

  /** Tests reverse interpretation of a NOT(NAME) expression. */
  @Test
  public void testNegatedNameCondition() {
    FlowScope[] blind = newScope();
    Node a = createVar(blind, "a", createNullableType(getNativeStringType()));
    Node condition = new Node(Token.NOT);
    condition.addChildToBack(a);

    // true outcome.
    FlowScope informedTrue =
        interpreter.getPreciserScopeKnowingConditionOutcome(condition, blind[0], true);
    assertTypeEquals(createNullableType(getNativeStringType()), getVarType(informedTrue, "a"));

    // false outcome.
    FlowScope informedFalse =
        interpreter.getPreciserScopeKnowingConditionOutcome(condition, blind[0], false);
    assertTypeEquals(getNativeStringType(), getVarType(informedFalse, "a"));
  }

  /** Tests reverse interpretation of a ASSIGN expression. */
  @SuppressWarnings("unchecked")
  @Test
  public void testAssignCondition1() {
    FlowScope[] blind = newScope();
    testBinop(
        blind,
        Token.ASSIGN,
        createVar(blind, "a", createNullableType(getNativeObjectType())),
        createVar(blind, "b", createNullableType(getNativeObjectType())),
        ImmutableSet.of(
            new TypedName("a", getNativeObjectType()), new TypedName("b", getNativeObjectType())),
        ImmutableSet.of(
            new TypedName("a", getNativeNullType()), new TypedName("b", getNativeNullType())));
  }

  /** Tests reverse interpretation of a SHEQ(NAME, NUMBER) expression. */
  @SuppressWarnings("unchecked")
  @Test
  public void testSheqCondition1() {
    FlowScope[] blind = newScope();
    testBinop(
        blind,
        Token.SHEQ,
        createVar(blind, "a", createUnionType(getNativeStringType(), getNativeNumberType())),
        createNumber(56),
        ImmutableSet.of(new TypedName("a", getNativeNumberType())),
        ImmutableSet.of(
            new TypedName("a", createUnionType(getNativeStringType(), getNativeNumberType()))));
  }

  /** Tests reverse interpretation of a SHEQ(NUMBER, NAME) expression. */
  @SuppressWarnings("unchecked")
  @Test
  public void testSheqCondition2() {
    FlowScope[] blind = newScope();
    testBinop(
        blind,
        Token.SHEQ,
        createNumber(56),
        createVar(blind, "a", createUnionType(getNativeStringType(), getNativeNumberType())),
        ImmutableSet.of(new TypedName("a", getNativeNumberType())),
        ImmutableSet.of(
            new TypedName("a", createUnionType(getNativeStringType(), getNativeNumberType()))));
  }

  /** Tests reverse interpretation of a SHEQ(NAME, NAME) expression. */
  @SuppressWarnings("unchecked")
  @Test
  public void testSheqCondition3() {
    FlowScope[] blind = newScope();
    testBinop(
        blind,
        Token.SHEQ,
        createVar(blind, "b", createUnionType(getNativeStringType(), getNativeBooleanType())),
        createVar(blind, "a", createUnionType(getNativeStringType(), getNativeNumberType())),
        ImmutableSet.of(
            new TypedName("a", getNativeStringType()), new TypedName("b", getNativeStringType())),
        ImmutableSet.of(
            new TypedName("a", createUnionType(getNativeStringType(), getNativeNumberType())),
            new TypedName("b", createUnionType(getNativeStringType(), getNativeBooleanType()))));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testSheqCondition4() {
    FlowScope[] blind = newScope();
    testBinop(
        blind,
        Token.SHEQ,
        createVar(blind, "a", createUnionType(getNativeStringType(), getNativeVoidType())),
        createVar(blind, "b", createUnionType(getNativeVoidType())),
        ImmutableSet.of(
            new TypedName("a", getNativeVoidType()), new TypedName("b", getNativeVoidType())),
        ImmutableSet.of(
            new TypedName("a", getNativeStringType()), new TypedName("b", getNativeVoidType())));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testSheqCondition5() {
    FlowScope[] blind = newScope();
    testBinop(
        blind,
        Token.SHEQ,
        createVar(blind, "a", createUnionType(getNativeNullType(), getNativeVoidType())),
        createVar(blind, "b", createUnionType(getNativeVoidType())),
        ImmutableSet.of(
            new TypedName("a", getNativeVoidType()), new TypedName("b", getNativeVoidType())),
        ImmutableSet.of(
            new TypedName("a", getNativeNullType()), new TypedName("b", getNativeVoidType())));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testSheqCondition6() {
    FlowScope[] blind = newScope();
    testBinop(
        blind,
        Token.SHEQ,
        createVar(blind, "a", createUnionType(getNativeStringType(), getNativeVoidType())),
        createVar(blind, "b", createUnionType(getNativeNumberType(), getNativeVoidType())),
        ImmutableSet.of(
            new TypedName("a", getNativeVoidType()), new TypedName("b", getNativeVoidType())),
        ImmutableSet.of(
            new TypedName("a", createUnionType(getNativeStringType(), getNativeVoidType())),
            new TypedName("b", createUnionType(getNativeNumberType(), getNativeVoidType()))));
  }

  /** Tests reverse interpretation of a SHNE(NAME, NUMBER) expression. */
  @SuppressWarnings("unchecked")
  @Test
  public void testShneCondition1() {
    FlowScope[] blind = newScope();
    testBinop(
        blind,
        Token.SHNE,
        createVar(blind, "a", createUnionType(getNativeStringType(), getNativeNumberType())),
        createNumber(56),
        ImmutableSet.of(
            new TypedName("a", createUnionType(getNativeStringType(), getNativeNumberType()))),
        ImmutableSet.of(new TypedName("a", getNativeNumberType())));
  }

  /** Tests reverse interpretation of a SHNE(NUMBER, NAME) expression. */
  @SuppressWarnings("unchecked")
  @Test
  public void testShneCondition2() {
    FlowScope[] blind = newScope();
    testBinop(
        blind,
        Token.SHNE,
        createNumber(56),
        createVar(blind, "a", createUnionType(getNativeStringType(), getNativeNumberType())),
        ImmutableSet.of(
            new TypedName("a", createUnionType(getNativeStringType(), getNativeNumberType()))),
        ImmutableSet.of(new TypedName("a", getNativeNumberType())));
  }

  /** Tests reverse interpretation of a SHNE(NAME, NAME) expression. */
  @SuppressWarnings("unchecked")
  @Test
  public void testShneCondition3() {
    FlowScope[] blind = newScope();
    testBinop(
        blind,
        Token.SHNE,
        createVar(blind, "b", createUnionType(getNativeStringType(), getNativeBooleanType())),
        createVar(blind, "a", createUnionType(getNativeStringType(), getNativeNumberType())),
        ImmutableSet.of(
            new TypedName("a", createUnionType(getNativeStringType(), getNativeNumberType())),
            new TypedName("b", createUnionType(getNativeStringType(), getNativeBooleanType()))),
        ImmutableSet.of(
            new TypedName("a", getNativeStringType()), new TypedName("b", getNativeStringType())));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testShneCondition4() {
    FlowScope[] blind = newScope();
    testBinop(
        blind,
        Token.SHNE,
        createVar(blind, "a", createUnionType(getNativeStringType(), getNativeVoidType())),
        createVar(blind, "b", createUnionType(getNativeVoidType())),
        ImmutableSet.of(
            new TypedName("a", getNativeStringType()), new TypedName("b", getNativeVoidType())),
        ImmutableSet.of(
            new TypedName("a", getNativeVoidType()), new TypedName("b", getNativeVoidType())));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testShneCondition5() {
    FlowScope[] blind = newScope();
    testBinop(
        blind,
        Token.SHNE,
        createVar(blind, "a", createUnionType(getNativeNullType(), getNativeVoidType())),
        createVar(blind, "b", createUnionType(getNativeNullType())),
        ImmutableSet.of(
            new TypedName("a", getNativeVoidType()), new TypedName("b", getNativeNullType())),
        ImmutableSet.of(
            new TypedName("a", getNativeNullType()), new TypedName("b", getNativeNullType())));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testShneCondition6() {
    FlowScope[] blind = newScope();
    testBinop(
        blind,
        Token.SHNE,
        createVar(blind, "a", createUnionType(getNativeStringType(), getNativeVoidType())),
        createVar(blind, "b", createUnionType(getNativeNumberType(), getNativeVoidType())),
        ImmutableSet.of(
            new TypedName("a", createUnionType(getNativeStringType(), getNativeVoidType())),
            new TypedName("b", createUnionType(getNativeNumberType(), getNativeVoidType()))),
        ImmutableSet.of(
            new TypedName("a", getNativeVoidType()), new TypedName("b", getNativeVoidType())));
  }

  /** Tests reverse interpretation of a EQ(NAME, NULL) expression. */
  @SuppressWarnings("unchecked")
  @Test
  public void testEqCondition1() {
    FlowScope[] blind = newScope();
    testBinop(
        blind,
        Token.EQ,
        createVar(blind, "a", createUnionType(getNativeBooleanType(), getNativeVoidType())),
        createNull(),
        ImmutableSet.of(new TypedName("a", getNativeVoidType())),
        ImmutableSet.of(new TypedName("a", getNativeBooleanType())));
  }

  /** Tests reverse interpretation of a NE(NULL, NAME) expression. */
  @SuppressWarnings("unchecked")
  @Test
  public void testEqCondition2() {
    FlowScope[] blind = newScope();
    testBinop(
        blind,
        Token.NE,
        createNull(),
        createVar(blind, "a", createUnionType(getNativeBooleanType(), getNativeVoidType())),
        ImmutableSet.of(new TypedName("a", getNativeBooleanType())),
        ImmutableSet.of(new TypedName("a", getNativeVoidType())));
  }

  /** Tests reverse interpretation of a EQ(NAME, NULL) expression. */
  @SuppressWarnings("unchecked")
  @Test
  public void testEqCondition3() {
    FlowScope[] blind = newScope();
    // (number,undefined,null)
    JSType nullableOptionalNumber =
        createUnionType(getNativeNullType(), getNativeVoidType(), getNativeNumberType());
    // (null,undefined)
    JSType nullUndefined = createUnionType(getNativeVoidType(), getNativeNullType());
    testBinop(
        blind,
        Token.EQ,
        createVar(blind, "a", nullableOptionalNumber),
        createNull(),
        ImmutableSet.of(new TypedName("a", nullUndefined)),
        ImmutableSet.of(new TypedName("a", getNativeNumberType())));
  }

  /** Tests reverse interpretation of two undefineds. */
  @SuppressWarnings("unchecked")
  @Test
  public void testEqCondition4() {
    FlowScope[] blind = newScope();
    testBinop(
        blind,
        Token.EQ,
        createVar(blind, "a", getNativeVoidType()),
        createVar(blind, "b", getNativeVoidType()),
        ImmutableSet.of(
            new TypedName("a", getNativeVoidType()), new TypedName("b", getNativeVoidType())),
        ImmutableSet.of(
            new TypedName("a", getNativeNoType()), new TypedName("b", getNativeNoType())));
  }

  /**
   * Tests reverse interpretation of a COMPARE(NAME, NUMBER) expression, where COMPARE can be LE,
   * LT, GE or GT.
   */
  @SuppressWarnings("unchecked")
  @Test
  public void testInequalitiesCondition1() {
    for (Token op : Arrays.asList(Token.LT, Token.GT, Token.LE, Token.GE)) {
      FlowScope[] blind = newScope();
      testBinop(
          blind,
          op,
          createVar(blind, "a", createUnionType(getNativeStringType(), getNativeVoidType())),
          createNumber(8),
          ImmutableSet.of(new TypedName("a", getNativeStringType())),
          ImmutableSet.of(
              new TypedName("a", createUnionType(getNativeStringType(), getNativeVoidType()))));
    }
  }

  /**
   * Tests reverse interpretation of a COMPARE(NAME, NAME) expression, where COMPARE can be LE, LT,
   * GE or GT.
   */
  @SuppressWarnings("unchecked")
  @Test
  public void testInequalitiesCondition2() {
    for (Token op : Arrays.asList(Token.LT, Token.GT, Token.LE, Token.GE)) {
      FlowScope[] blind = newScope();
      testBinop(
          blind,
          op,
          createVar(
              blind,
              "a",
              createUnionType(getNativeStringType(), getNativeNumberType(), getNativeVoidType())),
          createVar(blind, "b", createUnionType(getNativeNumberType(), getNativeNullType())),
          ImmutableSet.of(
              new TypedName("a", createUnionType(getNativeStringType(), getNativeNumberType())),
              new TypedName("b", createUnionType(getNativeNumberType(), getNativeNullType()))),
          ImmutableSet.of(
              new TypedName(
                  "a",
                  createUnionType(
                      getNativeStringType(), getNativeNumberType(), getNativeVoidType())),
              new TypedName("b", createUnionType(getNativeNumberType(), getNativeNullType()))));
    }
  }

  /**
   * Tests reverse interpretation of a COMPARE(NUMBER-untyped, NAME) expression, where COMPARE can
   * be LE, LT, GE or GT.
   */
  @SuppressWarnings("unchecked")
  @Test
  public void testInequalitiesCondition3() {
    for (Token op : Arrays.asList(Token.LT, Token.GT, Token.LE, Token.GE)) {
      FlowScope[] blind = newScope();
      testBinop(
          blind,
          op,
          createUntypedNumber(8),
          createVar(blind, "a", createUnionType(getNativeStringType(), getNativeVoidType())),
          ImmutableSet.of(new TypedName("a", getNativeStringType())),
          ImmutableSet.of(
              new TypedName("a", createUnionType(getNativeStringType(), getNativeVoidType()))));
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testAnd() {
    FlowScope[] blind = newScope();
    testBinop(
        blind,
        Token.AND,
        createVar(blind, "b", createUnionType(getNativeStringType(), getNativeNullType())),
        createVar(blind, "a", createUnionType(getNativeNumberType(), getNativeVoidType())),
        ImmutableSet.of(
            new TypedName("a", getNativeNumberType()), new TypedName("b", getNativeStringType())),
        ImmutableSet.of(
            new TypedName("a", createUnionType(getNativeNumberType(), getNativeVoidType())),
            new TypedName("b", createUnionType(getNativeStringType(), getNativeNullType()))));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testTypeof1() {
    FlowScope[] blind = newScope();
    testBinop(
        blind,
        Token.EQ,
        new Node(Token.TYPEOF, createVar(blind, "a", getNativeObjectType())),
        Node.newString("function"),
        ImmutableSet.of(new TypedName("a", getNativeU2UConstructorType())),
        ImmutableSet.of(new TypedName("a", getNativeObjectType())));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testTypeof2() {
    FlowScope[] blind = newScope();
    testBinop(
        blind,
        Token.EQ,
        new Node(Token.TYPEOF, createVar(blind, "a", getNativeAllType())),
        Node.newString("function"),
        ImmutableSet.of(new TypedName("a", getNativeU2UConstructorType())),
        ImmutableSet.of(new TypedName("a", getNativeAllType())));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testTypeof3() {
    FlowScope[] blind = newScope();
    testBinop(
        blind,
        Token.EQ,
        new Node(Token.TYPEOF, createVar(blind, "a", getNativeObjectNumberStringBooleanType())),
        Node.newString("function"),
        ImmutableSet.of(new TypedName("a", getNativeU2UConstructorType())),
        ImmutableSet.of(new TypedName("a", getNativeObjectNumberStringBooleanType())));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testTypeof4() {
    FlowScope[] blind = newScope();
    testBinop(
        blind,
        Token.EQ,
        new Node(
            Token.TYPEOF,
            createVar(
                blind,
                "a",
                createUnionType(
                    getNativeU2UConstructorType(), getNativeNumberStringBooleanType()))),
        Node.newString("function"),
        ImmutableSet.of(new TypedName("a", getNativeU2UConstructorType())),
        ImmutableSet.of(new TypedName("a", getNativeNumberStringBooleanType())));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testInstanceOf() {
    FlowScope[] blind = newScope();
    testBinop(
        blind,
        Token.INSTANCEOF,
        createVar(blind, "x", getNativeUnknownType()),
        createVar(blind, "s", getNativeStringObjectConstructorType()),
        ImmutableSet.of(
            new TypedName("x", getNativeStringObjectType()),
            new TypedName("s", getNativeStringObjectConstructorType())),
        ImmutableSet.of(new TypedName("s", getNativeStringObjectConstructorType())));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testInstanceOf2() {
    FlowScope[] blind = newScope();
    testBinop(
        blind,
        Token.INSTANCEOF,
        createVar(
            blind, "x", createUnionType(getNativeStringObjectType(), getNativeNumberObjectType())),
        createVar(blind, "s", getNativeStringObjectConstructorType()),
        ImmutableSet.of(
            new TypedName("x", getNativeStringObjectType()),
            new TypedName("s", getNativeStringObjectConstructorType())),
        ImmutableSet.of(
            new TypedName("x", getNativeNumberObjectType()),
            new TypedName("s", getNativeStringObjectConstructorType())));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testInstanceOf3() {
    FlowScope[] blind = newScope();
    testBinop(
        blind,
        Token.INSTANCEOF,
        createVar(blind, "x", getNativeObjectType()),
        createVar(blind, "s", getNativeStringObjectConstructorType()),
        ImmutableSet.of(
            new TypedName("x", getNativeStringObjectType()),
            new TypedName("s", getNativeStringObjectConstructorType())),
        ImmutableSet.of(
            new TypedName("x", getNativeObjectType()),
            new TypedName("s", getNativeStringObjectConstructorType())));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testInstanceOf4() {
    FlowScope[] blind = newScope();
    testBinop(
        blind,
        Token.INSTANCEOF,
        createVar(blind, "x", getNativeAllType()),
        createVar(blind, "s", getNativeStringObjectConstructorType()),
        ImmutableSet.of(
            new TypedName("x", getNativeStringObjectType()),
            new TypedName("s", getNativeStringObjectConstructorType())),
        ImmutableSet.of(new TypedName("s", getNativeStringObjectConstructorType())));
  }

  private void testBinop(
      FlowScope[] blind,
      Token binop,
      Node left,
      Node right,
      Collection<TypedName> trueOutcome,
      Collection<TypedName> falseOutcome) {
    Node condition = new Node(binop);
    condition.addChildToBack(left);
    condition.addChildToBack(right);

    // true outcome.
    FlowScope informedTrue =
        interpreter.getPreciserScopeKnowingConditionOutcome(condition, blind[0], true);
    for (TypedName p : trueOutcome) {
      assertTypeEquals(p.name, p.type, getVarType(informedTrue, p.name));
    }

    // false outcome.
    FlowScope informedFalse =
        interpreter.getPreciserScopeKnowingConditionOutcome(condition, blind[0], false);
    for (TypedName p : falseOutcome) {
      assertTypeEquals(p.type, getVarType(informedFalse, p.name));
    }
  }

  private Node createNull() {
    Node n = new Node(Token.NULL);
    n.setJSType(getNativeNullType());
    return n;
  }

  private Node createNumber(int n) {
    Node number = createUntypedNumber(n);
    number.setJSType(getNativeNumberType());
    return number;
  }

  private Node createUntypedNumber(int n) {
    return Node.newNumber(n);
  }

  private JSType getVarType(FlowScope scope, String name) {
    return scope.getSlot(name).getType();
  }

  private Node createVar(FlowScope[] scope, String name, JSType type) {
    Node n = Node.newString(Token.NAME, name);
    functionScope.declare(name, n, null, null);
    scope[0] = scope[0].inferSlotType(name, type);
    n.setJSType(type);
    return n;
  }

  private static class TypedName {
    private final String name;
    private final JSType type;

    private TypedName(String name, JSType type) {
      this.name = name;
      this.type = type;
    }
  }
}
