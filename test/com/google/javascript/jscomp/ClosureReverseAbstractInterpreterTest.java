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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;

import com.google.javascript.jscomp.type.ClosureReverseAbstractInterpreter;
import com.google.javascript.jscomp.type.FlowScope;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Outcome;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import org.jspecify.nullness.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ClosureReverseAbstractInterpreterTest extends CompilerTypeTestCase {

  @Test
  public void testGoogIsObjectOnNull() {
    testClosureFunction("goog.isObject", null, getNativeObjectType(), null);
  }

  @Test
  public void testGoogIsObject1() {
    testClosureFunction(
        "goog.isObject",
        getNativeAllType(),
        getNativeNoObjectType(),
        getNativeAllType());
  }

  @Test
  public void testGoogIsObject2a() {
    testClosureFunction(
        "goog.isObject",
        createUnionType(getNativeObjectType(), getNativeNumberStringBooleanType()),
        getNativeObjectType(),
        getNativeNumberStringBooleanType());
  }

  @Test
  public void testGoogIsObject2b() {
    testClosureFunction(
        "goog.isObject",
        createUnionType(getNativeObjectType(), getNativeValueTypes()),
        getNativeObjectType(),
        getNativeValueTypes());
  }

  @Test
  public void testGoogIsObject3a() {
    testClosureFunction(
        "goog.isObject",
        createUnionType(
            getNativeObjectType(),
            getNativeNumberStringBooleanType(),
            getNativeNullType(),
            getNativeVoidType()),
        getNativeObjectType(),
        createUnionType(
            getNativeNumberStringBooleanType(), getNativeNullType(), getNativeVoidType()));
  }

  @Test
  public void testGoogIsObject3b() {
    testClosureFunction(
        "goog.isObject",
        createUnionType(
            getNativeObjectType(), getNativeValueTypes(), getNativeNullType(), getNativeVoidType()),
        getNativeObjectType(),
        createUnionType(getNativeValueTypes(), getNativeNullType(), getNativeVoidType()));
  }

  @Test
  public void testGoogIsObject4() {
    testClosureFunction(
        "goog.isObject",
        getNativeUnknownType(),
        getNativeNoObjectType(), // ? Should this be CHECKED_UNKNOWN?
        getNativeCheckedUnknownType());
  }

  private void testClosureFunction(
      String function, @Nullable JSType type, JSType trueType, @Nullable JSType falseType) {
    // function(a) where a : type
    Node n = compiler.parseTestCode("var a; " + function + "(a)");
    Node call = n.getLastChild().getLastChild();
    Node name = call.getLastChild();

    Node root = IR.root(IR.root(), IR.root(n));
    TypedScope scope = new TypedScopeCreator(compiler).createScope(root, null);
    FlowScope flowScope = LinkedFlowScope.createEntryLattice(compiler, scope);

    assertThat(call.getToken()).isEqualTo(Token.CALL);
    assertThat(name.getToken()).isEqualTo(Token.NAME);

    flowScope = flowScope.inferSlotType("a", type);
    ClosureReverseAbstractInterpreter rai = new ClosureReverseAbstractInterpreter(registry);

    // trueScope
    assertType(
            rai.getPreciserScopeKnowingConditionOutcome(call, flowScope, Outcome.TRUE)
                .getSlot("a")
                .getType())
        .isEqualTo(trueType);

    // falseScope
    JSType aType =
        rai.getPreciserScopeKnowingConditionOutcome(call, flowScope, Outcome.FALSE)
            .getSlot("a")
            .getType();
    if (falseType == null) {
      assertThat(aType).isNull();
    } else {
      assertType(aType).isEqualTo(falseType);
    }
  }
}
