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
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ClosureReverseAbstractInterpreterTest extends CompilerTypeTestCase {

  @Test
  public void testGoogIsDef1() {
    testClosureFunction(
        "goog.isDef",
        createOptionalType(getNativeNumberType()),
        getNativeNumberType(),
        getNativeVoidType());
  }

  @Test
  public void testGoogIsDef2() {
    testClosureFunction(
        "goog.isDef",
        createNullableType(getNativeNumberType()),
        createNullableType(getNativeNumberType()),
        getNativeNoType());
  }

  @Test
  public void testGoogIsDef3() {
    testClosureFunction(
        "goog.isDef",
        getNativeAllType(),
        createUnionType(getNativeObjectNumberStringBooleanSymbolType(), getNativeNullType()),
        getNativeVoidType());
  }

  @Test
  public void testGoogIsDef4() {
    testClosureFunction(
        "goog.isDef",
        getNativeUnknownType(),
        getNativeUnknownType(), // TODO(johnlenz): should be getNativeCheckedUnknownType()
        getNativeUnknownType());
  }

  @Test
  public void testGoogIsNull1() {
    testClosureFunction(
        "goog.isNull",
        createOptionalType(getNativeNumberType()),
        getNativeNoType(),
        createOptionalType(getNativeNumberType()));
  }

  @Test
  public void testGoogIsNull2() {
    testClosureFunction(
        "goog.isNull",
        createNullableType(getNativeNumberType()),
        getNativeNullType(),
        getNativeNumberType());
  }

  @Test
  public void testGoogIsNull3() {
    testClosureFunction(
        "goog.isNull",
        getNativeAllType(),
        getNativeNullType(),
        createUnionType(getNativeObjectNumberStringBooleanSymbolType(), getNativeVoidType()));
  }

  @Test
  public void testGoogIsNull4() {
    testClosureFunction(
        "goog.isNull",
        getNativeUnknownType(),
        getNativeUnknownType(),
        getNativeUnknownType()); // TODO(johnlenz): this should be CHECK_UNKNOWN
  }

  @Test
  public void testGoogIsDefAndNotNull1() {
    testClosureFunction(
        "goog.isDefAndNotNull",
        createOptionalType(getNativeNumberType()),
        getNativeNumberType(),
        getNativeVoidType());
  }

  @Test
  public void testGoogIsDefAndNotNull2() {
    testClosureFunction(
        "goog.isDefAndNotNull",
        createNullableType(getNativeNumberType()),
        getNativeNumberType(),
        getNativeNullType());
  }

  @Test
  public void testGoogIsDefAndNotNull3() {
    testClosureFunction(
        "goog.isDefAndNotNull",
        createOptionalType(createNullableType(getNativeNumberType())),
        getNativeNumberType(),
        getNativeNullVoidType());
  }

  @Test
  public void testGoogIsDefAndNotNull4() {
    testClosureFunction(
        "goog.isDefAndNotNull",
        getNativeAllType(),
        getNativeObjectNumberStringBooleanSymbolType(),
        getNativeNullVoidType());
  }

  @Test
  public void testGoogIsDefAndNotNull5() {
    testClosureFunction(
        "goog.isDefAndNotNull",
        getNativeUnknownType(),
        getNativeUnknownType(), // TODO(johnlenz): this should be "CHECKED_UNKNOWN"
        getNativeUnknownType());
  }

  @Test
  public void testGoogIsString1() {
    testClosureFunction(
        "goog.isString",
        createNullableType(getNativeStringType()),
        getNativeStringType(),
        getNativeNullType());
  }

  @Test
  public void testGoogIsString2() {
    testClosureFunction(
        "goog.isString",
        createNullableType(getNativeNumberType()),
        createNullableType(getNativeNumberType()),
        createNullableType(getNativeNumberType()));
  }

  @Test
  public void testGoogIsBoolean1() {
    testClosureFunction(
        "goog.isBoolean",
        createNullableType(getNativeBooleanType()),
        getNativeBooleanType(),
        getNativeNullType());
  }

  @Test
  public void testGoogIsBoolean2() {
    testClosureFunction(
        "goog.isBoolean",
        createUnionType(getNativeBooleanType(), getNativeStringType(), getNativeNoObjectType()),
        getNativeBooleanType(),
        createUnionType(getNativeStringType(), getNativeNoObjectType()));
  }

  @Test
  public void testGoogIsBoolean3() {
    testClosureFunction(
        "goog.isBoolean",
        getNativeAllType(),
        getNativeBooleanType(),
        // TODO(johnlenz): this should be: {Object|number|string|null|void}
        getNativeAllType());
  }

  @Test
  public void testGoogIsBoolean4() {
    testClosureFunction(
        "goog.isBoolean",
        getNativeUnknownType(),
        getNativeBooleanType(),
        getNativeCheckedUnknownType());
  }

  @Test
  public void testGoogIsNumber() {
    testClosureFunction(
        "goog.isNumber",
        createNullableType(getNativeNumberType()),
        getNativeNumberType(),
        getNativeNullType());
  }

  @Test
  public void testGoogIsFunction() {
    testClosureFunction(
        "goog.isFunction",
        createNullableType(getNativeObjectConstructorType()),
        getNativeObjectConstructorType(),
        getNativeNullType());
  }

  @Test
  public void testGoogIsFunction2a() {
    testClosureFunction(
        "goog.isFunction",
        getNativeObjectNumberStringBooleanType(),
        getNativeU2UConstructorType(),
        getNativeObjectNumberStringBooleanType());
  }

  @Test
  public void testGoogIsFunction2b() {
    testClosureFunction(
        "goog.isFunction",
        getNativeObjectNumberStringBooleanSymbolType(),
        getNativeU2UConstructorType(),
        getNativeObjectNumberStringBooleanSymbolType());
  }

  @Test
  public void testGoogIsFunction3() {
    testClosureFunction(
        "goog.isFunction",
        createUnionType(getNativeU2UConstructorType(), getNativeNumberStringBooleanType()),
        getNativeU2UConstructorType(),
        getNativeNumberStringBooleanType());
  }

  @Test
  public void testGoogIsFunctionOnNull() {
    testClosureFunction("goog.isFunction", null, getNativeU2UConstructorType(), null);
  }

  @Test
  public void testGoogIsArray1() {
    testClosureFunction(
        "goog.isArray", getNativeObjectType(), getNativeArrayType(), getNativeObjectType());
  }

  @Test
  public void testGoogIsArray2() {
    testClosureFunction(
        "goog.isArray", getNativeAllType(), getNativeArrayType(), getNativeAllType());
  }

  @Test
  public void testGoogIsArray3() {
    testClosureFunction(
        "goog.isArray",
        getNativeUnknownType(),
        getNativeCheckedUnknownType(),
        getNativeCheckedUnknownType());
  }

  @Test
  public void testGoogIsArray4() {
    testClosureFunction(
        "goog.isArray",
        createUnionType(getNativeArrayType(), getNativeNullType()),
        getNativeArrayType(),
        getNativeNullType());
  }

  @Test
  public void testGoogIsArrayOnNull() {
    testClosureFunction("goog.isArray", null, getNativeArrayType(), null);
  }

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
        createUnionType(
            getNativeNumberStringBooleanSymbolType(), getNativeNullType(), getNativeVoidType()));
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
        createUnionType(getNativeObjectType(), getNativeNumberStringBooleanSymbolType()),
        getNativeObjectType(),
        getNativeNumberStringBooleanSymbolType());
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
            getNativeObjectType(),
            getNativeNumberStringBooleanSymbolType(),
            getNativeNullType(),
            getNativeVoidType()),
        getNativeObjectType(),
        createUnionType(
            getNativeNumberStringBooleanSymbolType(), getNativeNullType(), getNativeVoidType()));
  }

  @Test
  public void testGoogIsObject4() {
    testClosureFunction(
        "goog.isObject",
        getNativeUnknownType(),
        getNativeNoObjectType(), // ? Should this be CHECKED_UNKNOWN?
        getNativeCheckedUnknownType());
  }

  private void testClosureFunction(String function, JSType type,
      JSType trueType, JSType falseType) {
    // function(a) where a : type
    Node n = compiler.parseTestCode("var a; " + function + "(a)");
    Node call = n.getLastChild().getLastChild();
    Node name = call.getLastChild();

    TypedScope scope = (TypedScope) SyntacticScopeCreator.makeTyped(compiler).createScope(n, null);
    FlowScope flowScope = LinkedFlowScope.createEntryLattice(scope);

    assertThat(call.getToken()).isEqualTo(Token.CALL);
    assertThat(name.getToken()).isEqualTo(Token.NAME);

    flowScope = flowScope.inferSlotType("a", type);
    ClosureReverseAbstractInterpreter rai = new ClosureReverseAbstractInterpreter(registry);

    // trueScope
    assertType(
            rai.getPreciserScopeKnowingConditionOutcome(call, flowScope, true)
                .getSlot("a")
                .getType())
        .isStructurallyEqualTo(trueType);

    // falseScope
    JSType aType = rai.getPreciserScopeKnowingConditionOutcome(call, flowScope, false)
        .getSlot("a").getType();
    if (falseType == null) {
      assertThat(aType).isNull();
    } else {
      assertType(aType).isStructurallyEqualTo(falseType);
    }
  }
}
