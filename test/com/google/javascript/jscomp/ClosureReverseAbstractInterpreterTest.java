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

import com.google.javascript.jscomp.type.ClosureReverseAbstractInterpreter;
import com.google.javascript.jscomp.type.FlowScope;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.testing.Asserts;

public final class ClosureReverseAbstractInterpreterTest extends
    CompilerTypeTestCase {

  public void testGoogIsDef1() throws Exception {
    testClosureFunction("goog.isDef",
        createOptionalType(NUMBER_TYPE),
        NUMBER_TYPE,
        VOID_TYPE);
  }

  public void testGoogIsDef2() throws Exception {
    testClosureFunction("goog.isDef",
        createNullableType(NUMBER_TYPE),
        createNullableType(NUMBER_TYPE),
        NO_TYPE);
  }

  public void testGoogIsDef3() throws Exception {
    testClosureFunction("goog.isDef",
        ALL_TYPE,
        createUnionType(OBJECT_NUMBER_STRING_BOOLEAN,NULL_TYPE),
        VOID_TYPE);
  }

  public void testGoogIsDef4() throws Exception {
    testClosureFunction("goog.isDef",
        UNKNOWN_TYPE,
        UNKNOWN_TYPE,  // TODO(johnlenz): should be CHECKED_UNKNOWN_TYPE
        UNKNOWN_TYPE);
  }

  public void testGoogIsNull1() throws Exception {
    testClosureFunction("goog.isNull",
        createOptionalType(NUMBER_TYPE),
        NO_TYPE,
        createOptionalType(NUMBER_TYPE));
  }

  public void testGoogIsNull2() throws Exception {
    testClosureFunction("goog.isNull",
        createNullableType(NUMBER_TYPE),
        NULL_TYPE,
        NUMBER_TYPE);
  }

  public void testGoogIsNull3() throws Exception {
    testClosureFunction("goog.isNull",
        ALL_TYPE,
        NULL_TYPE,
        createUnionType(OBJECT_NUMBER_STRING_BOOLEAN, VOID_TYPE));
  }

  public void testGoogIsNull4() throws Exception {
    testClosureFunction("goog.isNull",
        UNKNOWN_TYPE,
        UNKNOWN_TYPE,
        UNKNOWN_TYPE); // TODO(johnlenz): this should be CHECK_UNKNOWN
  }

  public void testGoogIsDefAndNotNull1() throws Exception {
    testClosureFunction("goog.isDefAndNotNull",
        createOptionalType(NUMBER_TYPE),
        NUMBER_TYPE,
        VOID_TYPE);
  }

  public void testGoogIsDefAndNotNull2() throws Exception {
    testClosureFunction("goog.isDefAndNotNull",
        createNullableType(NUMBER_TYPE),
        NUMBER_TYPE,
        NULL_TYPE);
  }

  public void testGoogIsDefAndNotNull3() throws Exception {
    testClosureFunction("goog.isDefAndNotNull",
        createOptionalType(createNullableType(NUMBER_TYPE)),
        NUMBER_TYPE,
        NULL_VOID);
  }

  public void testGoogIsDefAndNotNull4() throws Exception {
    testClosureFunction("goog.isDefAndNotNull",
        ALL_TYPE,
        OBJECT_NUMBER_STRING_BOOLEAN,
        NULL_VOID);
  }

  public void testGoogIsDefAndNotNull5() throws Exception {
    testClosureFunction("goog.isDefAndNotNull",
        UNKNOWN_TYPE,
        UNKNOWN_TYPE,  // TODO(johnlenz): this should be "CHECKED_UNKNOWN"
        UNKNOWN_TYPE);
  }

  public void testGoogIsString1() throws Exception {
    testClosureFunction("goog.isString",
        createNullableType(STRING_TYPE),
        STRING_TYPE,
        NULL_TYPE);
  }

  public void testGoogIsString2() throws Exception {
    testClosureFunction("goog.isString",
        createNullableType(NUMBER_TYPE),
        createNullableType(NUMBER_TYPE),
        createNullableType(NUMBER_TYPE));
  }

  public void testGoogIsBoolean1() throws Exception {
    testClosureFunction("goog.isBoolean",
        createNullableType(BOOLEAN_TYPE),
        BOOLEAN_TYPE,
        NULL_TYPE);
  }

  public void testGoogIsBoolean2() throws Exception {
    testClosureFunction("goog.isBoolean",
        createUnionType(BOOLEAN_TYPE, STRING_TYPE, NO_OBJECT_TYPE),
        BOOLEAN_TYPE,
        createUnionType(STRING_TYPE, NO_OBJECT_TYPE));
  }

  public void testGoogIsBoolean3() throws Exception {
    testClosureFunction("goog.isBoolean",
        ALL_TYPE,
        BOOLEAN_TYPE,
        ALL_TYPE); // TODO(johnlenz): this should be:
                   //   {Object|number|string|null|void}
  }

  public void testGoogIsBoolean4() throws Exception {
    testClosureFunction("goog.isBoolean",
        UNKNOWN_TYPE,
        BOOLEAN_TYPE,
        CHECKED_UNKNOWN_TYPE);
  }

  public void testGoogIsNumber() throws Exception {
    testClosureFunction("goog.isNumber",
        createNullableType(NUMBER_TYPE),
        NUMBER_TYPE,
        NULL_TYPE);
  }

  public void testGoogIsFunction() throws Exception {
    testClosureFunction("goog.isFunction",
        createNullableType(OBJECT_FUNCTION_TYPE),
        OBJECT_FUNCTION_TYPE,
        NULL_TYPE);
  }

  public void testGoogIsFunction2() throws Exception {
    testClosureFunction("goog.isFunction",
        OBJECT_NUMBER_STRING_BOOLEAN,
        U2U_CONSTRUCTOR_TYPE,
        OBJECT_NUMBER_STRING_BOOLEAN);
  }

  public void testGoogIsFunction3() throws Exception {
    testClosureFunction("goog.isFunction",
        createUnionType(U2U_CONSTRUCTOR_TYPE, NUMBER_STRING_BOOLEAN),
        U2U_CONSTRUCTOR_TYPE,
        NUMBER_STRING_BOOLEAN);
  }

  public void testGoogIsFunctionOnNull() throws Exception {
    testClosureFunction("goog.isFunction",
        null,
        U2U_CONSTRUCTOR_TYPE,
        null);
  }

  public void testGoogIsArray1() throws Exception {
    testClosureFunction("goog.isArray",
        OBJECT_TYPE,
        ARRAY_TYPE,
        OBJECT_TYPE);
  }

  public void testGoogIsArray2() throws Exception {
    testClosureFunction("goog.isArray",
        ALL_TYPE,
        ARRAY_TYPE,
        ALL_TYPE);
  }

  public void testGoogIsArray3() throws Exception {
    testClosureFunction("goog.isArray",
        UNKNOWN_TYPE,
        CHECKED_UNKNOWN_TYPE,
        CHECKED_UNKNOWN_TYPE);
  }

  public void testGoogIsArray4() throws Exception {
    testClosureFunction("goog.isArray",
        createUnionType(ARRAY_TYPE, NULL_TYPE),
        ARRAY_TYPE,
        NULL_TYPE);
  }

  public void testGoogIsArrayOnNull() throws Exception {
    testClosureFunction("goog.isArray",
        null,
        ARRAY_TYPE,
        null);
  }

  public void testGoogIsObjectOnNull() throws Exception {
    testClosureFunction("goog.isObject",
        null,
        OBJECT_TYPE,
        null);
  }

  public void testGoogIsObject1() throws Exception {
    testClosureFunction("goog.isObject",
        ALL_TYPE,
        NO_OBJECT_TYPE,
        createUnionType(NUMBER_STRING_BOOLEAN, NULL_TYPE, VOID_TYPE));
  }

  public void testGoogIsObject2() throws Exception {
    testClosureFunction("goog.isObject",
          createUnionType(OBJECT_TYPE, NUMBER_STRING_BOOLEAN),
          OBJECT_TYPE,
          NUMBER_STRING_BOOLEAN);
  }

  public void testGoogIsObject3() throws Exception {
    testClosureFunction("goog.isObject",
          createUnionType(
              OBJECT_TYPE, NUMBER_STRING_BOOLEAN, NULL_TYPE, VOID_TYPE),
          OBJECT_TYPE,
          createUnionType(NUMBER_STRING_BOOLEAN, NULL_TYPE, VOID_TYPE));
  }

  public void testGoogIsObject4() throws Exception {
    testClosureFunction("goog.isObject",
        UNKNOWN_TYPE,
        NO_OBJECT_TYPE,  // ? Should this be CHECKED_UNKNOWN?
        CHECKED_UNKNOWN_TYPE);
  }

  private void testClosureFunction(String function, JSType type,
      JSType trueType, JSType falseType) {
    // function(a) where a : type
    Node n = compiler.parseTestCode("var a; " + function + "(a)");
    Node call = n.getLastChild().getLastChild();
    Node name = call.getLastChild();

    TypedScope scope = SyntacticScopeCreator.makeTyped(compiler).createScope(n, null);
    FlowScope flowScope = LinkedFlowScope.createEntryLattice(scope);

    assertEquals(Token.CALL, call.getType());
    assertEquals(Token.NAME, name.getType());

    flowScope.inferSlotType("a", type);
    ClosureReverseAbstractInterpreter rai =
        new ClosureReverseAbstractInterpreter(registry);

    // trueScope
    Asserts.assertTypeEquals(
        trueType,
        rai.getPreciserScopeKnowingConditionOutcome(call, flowScope, true)
        .getSlot("a").getType());

    // falseScope
    Asserts.assertTypeEquals(
        falseType,
        rai.getPreciserScopeKnowingConditionOutcome(call, flowScope, false)
        .getSlot("a").getType());
  }
}
