/*
 * Copyright 2008 The Closure Compiler Authors.
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.type.FlowScope;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for LinkedFlowScope.
 *
 * @author nicksantos@google.com (Nick Santos)
 */

@RunWith(JUnit4.class)
public final class LinkedFlowScopeTest extends CompilerTypeTestCase {

  private final Node functionNode = new Node(Token.FUNCTION);
  private final Node rootNode = new Node(Token.ROOT, functionNode);
  private static final int LONG_CHAIN_LENGTH = 1050;

  private TypedScope globalScope;
  private TypedScope localScope;
  @SuppressWarnings("unused")
  private FlowScope globalEntry;
  private FlowScope localEntry;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    globalScope = TypedScope.createGlobalScope(rootNode);
    globalScope.declare("globalA", null, null, null);
    globalScope.declare("globalB", null, null, null);

    localScope = new TypedScope(globalScope, functionNode);
    localScope.declare("localA", null, null, null);
    localScope.declare("localB", null, null, null);

    globalEntry = LinkedFlowScope.createEntryLattice(globalScope);
    localEntry = LinkedFlowScope.createEntryLattice(localScope);
  }

  @Test
  public void testJoin1() {
    FlowScope childA = localEntry.inferSlotType("localB", getNativeNumberType());
    FlowScope childAB = childA.inferSlotType("localB", getNativeStringType());
    FlowScope childB = localEntry.inferSlotType("localB", getNativeBooleanType());

    assertTypeEquals(getNativeStringType(), childAB.getSlot("localB").getType());
    assertTypeEquals(getNativeBooleanType(), childB.getSlot("localB").getType());
    assertThat(childB.getSlot("localA").getType()).isNull();

    FlowScope joined = join(childB, childAB);
    assertTypeEquals(
        createUnionType(getNativeStringType(), getNativeBooleanType()),
        joined.getSlot("localB").getType());
    assertThat(joined.getSlot("localA").getType()).isNull();

    joined = join(childAB, childB);
    assertTypeEquals(
        createUnionType(getNativeStringType(), getNativeBooleanType()),
        joined.getSlot("localB").getType());
    assertThat(joined.getSlot("localA").getType()).isNull();

    assertWithMessage("Join should be symmetric")
        .that(join(childAB, childB))
        .isEqualTo(join(childB, childAB));
  }

  @Test
  public void testJoin2() {
    FlowScope childA = localEntry.inferSlotType("localA", getNativeStringType());
    FlowScope childB = localEntry.inferSlotType("globalB", getNativeBooleanType());

    assertTypeEquals(getNativeStringType(), childA.getSlot("localA").getType());
    assertTypeEquals(getNativeBooleanType(), childB.getSlot("globalB").getType());
    assertThat(childB.getSlot("localB").getType()).isNull();

    FlowScope joined = join(childB, childA);
    assertTypeEquals(getNativeStringType(), joined.getSlot("localA").getType());
    assertTypeEquals(getNativeBooleanType(), joined.getSlot("globalB").getType());

    joined = join(childA, childB);
    assertTypeEquals(getNativeStringType(), joined.getSlot("localA").getType());
    assertTypeEquals(getNativeBooleanType(), joined.getSlot("globalB").getType());

    assertWithMessage("Join should be symmetric")
        .that(join(childA, childB))
        .isEqualTo(join(childB, childA));
  }

  @Test
  public void testJoin3() {
    localScope.declare("localC", null, getNativeStringType(), null);
    localScope.declare("localD", null, getNativeStringType(), null);

    FlowScope childA = localEntry.inferSlotType("localC", getNativeNumberType());
    FlowScope childB = localEntry.inferSlotType("localD", getNativeBooleanType());

    FlowScope joined = join(childB, childA);
    assertTypeEquals(
        createUnionType(getNativeStringType(), getNativeNumberType()),
        joined.getSlot("localC").getType());
    assertTypeEquals(
        createUnionType(getNativeStringType(), getNativeBooleanType()),
        joined.getSlot("localD").getType());

    joined = join(childA, childB);
    assertTypeEquals(
        createUnionType(getNativeStringType(), getNativeNumberType()),
        joined.getSlot("localC").getType());
    assertTypeEquals(
        createUnionType(getNativeStringType(), getNativeBooleanType()),
        joined.getSlot("localD").getType());

    assertWithMessage("Join should be symmetric")
        .that(join(childA, childB))
        .isEqualTo(join(childB, childA));
  }

  /** Create a long chain of flow scopes. */
  @Test
  public void testLongChain() {
    FlowScope chainA = localEntry;
    FlowScope chainB = localEntry;
    for (int i = 0; i < LONG_CHAIN_LENGTH; i++) {
      localScope.declare("local" + i, null, null, null);
      chainA =
          chainA.inferSlotType(
              "local" + i, i % 2 == 0 ? getNativeNumberType() : getNativeBooleanType());
      chainB =
          chainB.inferSlotType(
              "local" + i, i % 3 == 0 ? getNativeStringType() : getNativeBooleanType());
    }

    FlowScope joined = join(chainA, chainB);
    for (int i = 0; i < LONG_CHAIN_LENGTH; i++) {
      assertTypeEquals(
          i % 2 == 0 ? getNativeNumberType() : getNativeBooleanType(),
          chainA.getSlot("local" + i).getType());
      assertTypeEquals(
          i % 3 == 0 ? getNativeStringType() : getNativeBooleanType(),
          chainB.getSlot("local" + i).getType());

      JSType joinedSlotType = joined.getSlot("local" + i).getType();
      if (i % 6 == 0) {
        assertTypeEquals(
            createUnionType(getNativeStringType(), getNativeNumberType()), joinedSlotType);
      } else if (i % 2 == 0) {
        assertTypeEquals(
            createUnionType(getNativeNumberType(), getNativeBooleanType()), joinedSlotType);
      } else if (i % 3 == 0) {
        assertTypeEquals(
            createUnionType(getNativeStringType(), getNativeBooleanType()), joinedSlotType);
      } else {
        assertTypeEquals(getNativeBooleanType(), joinedSlotType);
      }
    }

    assertScopesDiffer(chainA, chainB);
    assertScopesDiffer(chainA, joined);
    assertScopesDiffer(chainB, joined);
  }

  @Test
  public void testDiffer1() {
    FlowScope childA = localEntry.inferSlotType("localB", getNativeNumberType());
    FlowScope childAB = childA.inferSlotType("localB", getNativeStringType());
    FlowScope childABC = childAB.inferSlotType("localA", getNativeBooleanType());
    FlowScope childB = childAB.inferSlotType("localB", getNativeStringType());
    FlowScope childBC = childB.inferSlotType("localA", getNativeNoType());

    assertScopesSame(childAB, childB);
    assertScopesDiffer(childABC, childBC);

    assertScopesDiffer(childABC, childB);
    assertScopesDiffer(childAB, childBC);

    assertScopesDiffer(childA, childAB);
    assertScopesDiffer(childA, childABC);
    assertScopesDiffer(childA, childB);
    assertScopesDiffer(childA, childBC);
  }

  @Test
  public void testDiffer2() {
    FlowScope childA = localEntry.inferSlotType("localA", getNativeNumberType());
    FlowScope childB = localEntry.inferSlotType("localA", getNativeNoType());

    assertScopesDiffer(childA, childB);
  }

  private void assertScopesDiffer(FlowScope a, FlowScope b) {
    assertThat(a).isNotEqualTo(b);
    assertThat(b).isNotEqualTo(a);
  }

  private void assertScopesSame(FlowScope a, FlowScope b) {
    assertThat(b).isEqualTo(a);
    assertThat(a).isEqualTo(b);
  }

  @SuppressWarnings("unchecked")
  private FlowScope join(FlowScope a, FlowScope b) {
    return (new LinkedFlowScope.FlowScopeJoinOp()).apply(
        ImmutableList.of(a, b));
  }
}
