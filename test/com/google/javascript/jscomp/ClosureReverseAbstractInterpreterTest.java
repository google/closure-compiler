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

import com.google.javascript.jscomp.type.ClosureReverseAbstractInterpreter;
import com.google.javascript.jscomp.type.FlowScope;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.testing.Asserts;

public final class ClosureReverseAbstractInterpreterTest extends
    CompilerTypeTestCase {

  public void testGoogIsDef1() throws Exception {
    testClosureFunction(
        "goog.isDef",
        createOptionalType(getNativeNumberType()),
        getNativeNumberType(),
        getNativeVoidType());
  }

  public void testGoogIsDef2() throws Exception {
    testClosureFunction(
        "goog.isDef",
        createNullableType(getNativeNumberType()),
        createNullableType(getNativeNumberType()),
        getNativeNoType());
  }

  public void testGoogIsDef3() throws Exception {
    testClosureFunction(
        "goog.isDef",
        getNativeAllType(),
        createUnionType(getNativeObjectNumberStringBooleanSymbolType(), getNativeNullType()),
        getNativeVoidType());
  }

  public void testGoogIsDef4() throws Exception {
    testClosureFunction(
        "goog.isDef",
        getNativeUnknownType(),
        getNativeUnknownType(), // TODO(johnlenz): should be getNativeCheckedUnknownType()
        getNativeUnknownType());
  }

  public void testGoogIsNull1() throws Exception {
    testClosureFunction(
        "goog.isNull",
        createOptionalType(getNativeNumberType()),
        getNativeNoType(),
        createOptionalType(getNativeNumberType()));
  }

  public void testGoogIsNull2() throws Exception {
    testClosureFunction(
        "goog.isNull",
        createNullableType(getNativeNumberType()),
        getNativeNullType(),
        getNativeNumberType());
  }

  public void testGoogIsNull3() throws Exception {
    testClosureFunction(
        "goog.isNull",
        getNativeAllType(),
        getNativeNullType(),
        createUnionType(getNativeObjectNumberStringBooleanSymbolType(), getNativeVoidType()));
  }

  public void testGoogIsNull4() throws Exception {
    testClosureFunction(
        "goog.isNull",
        getNativeUnknownType(),
        getNativeUnknownType(),
        getNativeUnknownType()); // TODO(johnlenz): this should be CHECK_UNKNOWN
  }

  public void testGoogIsDefAndNotNull1() throws Exception {
    testClosureFunction(
        "goog.isDefAndNotNull",
        createOptionalType(getNativeNumberType()),
        getNativeNumberType(),
        getNativeVoidType());
  }

  public void testGoogIsDefAndNotNull2() throws Exception {
    testClosureFunction(
        "goog.isDefAndNotNull",
        createNullableType(getNativeNumberType()),
        getNativeNumberType(),
        getNativeNullType());
  }

  public void testGoogIsDefAndNotNull3() throws Exception {
    testClosureFunction(
        "goog.isDefAndNotNull",
        createOptionalType(createNullableType(getNativeNumberType())),
        getNativeNumberType(),
        getNativeNullVoidType());
  }

  public void testGoogIsDefAndNotNull4() throws Exception {
    testClosureFunction(
        "goog.isDefAndNotNull",
        getNativeAllType(),
        getNativeObjectNumberStringBooleanSymbolType(),
        getNativeNullVoidType());
  }

  public void testGoogIsDefAndNotNull5() throws Exception {
    testClosureFunction(
        "goog.isDefAndNotNull",
        getNativeUnknownType(),
        getNativeUnknownType(), // TODO(johnlenz): this should be "CHECKED_UNKNOWN"
        getNativeUnknownType());
  }

  public void testGoogIsString1() throws Exception {
    testClosureFunction(
        "goog.isString",
        createNullableType(getNativeStringType()),
        getNativeStringType(),
        getNativeNullType());
  }

  public void testGoogIsString2() throws Exception {
    testClosureFunction(
        "goog.isString",
        createNullableType(getNativeNumberType()),
        createNullableType(getNativeNumberType()),
        createNullableType(getNativeNumberType()));
  }

  public void testGoogIsBoolean1() throws Exception {
    testClosureFunction(
        "goog.isBoolean",
        createNullableType(getNativeBooleanType()),
        getNativeBooleanType(),
        getNativeNullType());
  }

  public void testGoogIsBoolean2() throws Exception {
    testClosureFunction(
        "goog.isBoolean",
        createUnionType(getNativeBooleanType(), getNativeStringType(), getNativeNoObjectType()),
        getNativeBooleanType(),
        createUnionType(getNativeStringType(), getNativeNoObjectType()));
  }

  public void testGoogIsBoolean3() throws Exception {
    testClosureFunction(
        "goog.isBoolean",
        getNativeAllType(),
        getNativeBooleanType(),
        // TODO(johnlenz): this should be: {Object|number|string|null|void}
        getNativeAllType());
  }

  public void testGoogIsBoolean4() throws Exception {
    testClosureFunction(
        "goog.isBoolean",
        getNativeUnknownType(),
        getNativeBooleanType(),
        getNativeCheckedUnknownType());
  }

  public void testGoogIsNumber() throws Exception {
    testClosureFunction(
        "goog.isNumber",
        createNullableType(getNativeNumberType()),
        getNativeNumberType(),
        getNativeNullType());
  }

  public void testGoogIsFunction() throws Exception {
    testClosureFunction(
        "goog.isFunction",
        createNullableType(getNativeObjectConstructorType()),
        getNativeObjectConstructorType(),
        getNativeNullType());
  }

  public void testGoogIsFunction2a() throws Exception {
    testClosureFunction(
        "goog.isFunction",
        getNativeObjectNumberStringBooleanType(),
        getNativeU2UConstructorType(),
        getNativeObjectNumberStringBooleanType());
  }

  public void testGoogIsFunction2b() throws Exception {
    testClosureFunction(
        "goog.isFunction",
        getNativeObjectNumberStringBooleanSymbolType(),
        getNativeU2UConstructorType(),
        getNativeObjectNumberStringBooleanSymbolType());
  }

  public void testGoogIsFunction3() throws Exception {
    testClosureFunction(
        "goog.isFunction",
        createUnionType(getNativeU2UConstructorType(), getNativeNumberStringBooleanType()),
        getNativeU2UConstructorType(),
        getNativeNumberStringBooleanType());
  }

  public void testGoogIsFunctionOnNull() throws Exception {
    testClosureFunction("goog.isFunction", null, getNativeU2UConstructorType(), null);
  }

  public void testGoogIsArray1() throws Exception {
    testClosureFunction(
        "goog.isArray", getNativeObjectType(), getNativeArrayType(), getNativeObjectType());
  }

  public void testGoogIsArray2() throws Exception {
    testClosureFunction(
        "goog.isArray", getNativeAllType(), getNativeArrayType(), getNativeAllType());
  }

  public void testGoogIsArray3() throws Exception {
    testClosureFunction(
        "goog.isArray",
        getNativeUnknownType(),
        getNativeCheckedUnknownType(),
        getNativeCheckedUnknownType());
  }

  public void testGoogIsArray4() throws Exception {
    testClosureFunction(
        "goog.isArray",
        createUnionType(getNativeArrayType(), getNativeNullType()),
        getNativeArrayType(),
        getNativeNullType());
  }

  public void testGoogIsArrayOnNull() throws Exception {
    testClosureFunction("goog.isArray", null, getNativeArrayType(), null);
  }

  public void testGoogIsObjectOnNull() throws Exception {
    testClosureFunction("goog.isObject", null, getNativeObjectType(), null);
  }

  public void testGoogIsObject1() throws Exception {
    testClosureFunction(
        "goog.isObject",
        getNativeAllType(),
        getNativeNoObjectType(),
        createUnionType(
            getNativeNumberStringBooleanSymbolType(), getNativeNullType(), getNativeVoidType()));
  }

  public void testGoogIsObject2a() throws Exception {
    testClosureFunction(
        "goog.isObject",
        createUnionType(getNativeObjectType(), getNativeNumberStringBooleanType()),
        getNativeObjectType(),
        getNativeNumberStringBooleanType());
  }

  public void testGoogIsObject2b() throws Exception {
    testClosureFunction(
        "goog.isObject",
        createUnionType(getNativeObjectType(), getNativeNumberStringBooleanSymbolType()),
        getNativeObjectType(),
        getNativeNumberStringBooleanSymbolType());
  }

  public void testGoogIsObject3a() throws Exception {
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

  public void testGoogIsObject3b() throws Exception {
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

  public void testGoogIsObject4() throws Exception {
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

    assertEquals(Token.CALL, call.getToken());
    assertEquals(Token.NAME, name.getToken());

    flowScope = flowScope.inferSlotType("a", type);
    ClosureReverseAbstractInterpreter rai = new ClosureReverseAbstractInterpreter(registry);

    // trueScope
    Asserts.assertTypeEquals(
        trueType,
        rai.getPreciserScopeKnowingConditionOutcome(call, flowScope, true).getSlot("a").getType());

    // falseScope
    JSType aType = rai.getPreciserScopeKnowingConditionOutcome(call, flowScope, false)
        .getSlot("a").getType();
    if (falseType == null) {
      assertThat(aType).isNull();
    } else {
      Asserts.assertTypeEquals(falseType, aType);
    }
  }
}
