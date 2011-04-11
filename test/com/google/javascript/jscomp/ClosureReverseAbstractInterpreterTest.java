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

import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

public class ClosureReverseAbstractInterpreterTest extends
    CompilerTypeTestCase {

  public void testGoogIsDef1() throws Exception {
    testClosureFunction("goog.isDef",
        createOptionalType(NUMBER_TYPE),
        NUMBER_TYPE,
        createOptionalType(NUMBER_TYPE));
  }

  public void testGoogIsDef2() throws Exception {
    testClosureFunction("goog.isDef",
        createNullableType(NUMBER_TYPE),
        createNullableType(NUMBER_TYPE),
        createNullableType(NUMBER_TYPE));
  }

  public void testGoogIsNull1() throws Exception {
    testClosureFunction("goog.isNull",
        createOptionalType(NUMBER_TYPE),
        NULL_TYPE,
        createOptionalType(NUMBER_TYPE));
  }

  public void testGoogIsNull2() throws Exception {
    testClosureFunction("goog.isNull",
        createNullableType(NUMBER_TYPE),
        NULL_TYPE,
        NUMBER_TYPE);
  }

  public void testGoogIsDefAndNotNull1() throws Exception {
    testClosureFunction("goog.isDefAndNotNull",
        createOptionalType(NUMBER_TYPE),
        NUMBER_TYPE,
        createOptionalType(NUMBER_TYPE));
  }

  public void testGoogIsDefAndNotNull2() throws Exception {
    testClosureFunction("goog.isDefAndNotNull",
        createNullableType(NUMBER_TYPE),
        NUMBER_TYPE,
        createNullableType(NUMBER_TYPE));
  }

  public void testGoogIsDefAndNotNull3() throws Exception {
    testClosureFunction("goog.isDefAndNotNull",
        createOptionalType(createNullableType(NUMBER_TYPE)),
        NUMBER_TYPE,
        createOptionalType(createNullableType(NUMBER_TYPE)));
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

  public void testGoogIsArray() throws Exception {
    testClosureFunction("goog.isArray",
        OBJECT_TYPE,
        ARRAY_TYPE,
        OBJECT_TYPE);
  }

  public void testGoogIsArrayOnNull() throws Exception {
    testClosureFunction("goog.isArray",
        null,
        ARRAY_TYPE,
        null);
  }

  public void testGoogIsFunctionOnNull() throws Exception {
    testClosureFunction("goog.isFunction",
        null,
        U2U_CONSTRUCTOR_TYPE,
        null);
  }

  public void testGoogIsObjectOnNull() throws Exception {
    testClosureFunction("goog.isObject",
        null,
        OBJECT_TYPE,
        null);
  }

  private void testClosureFunction(String function, JSType type,
      JSType trueType, JSType falseType) {
    // function(a) where a : type
    Node n = compiler.parseTestCode("var a; " + function + "(a)");
    Node call = n.getLastChild().getLastChild();
    Node name = call.getLastChild();

    Scope scope = new SyntacticScopeCreator(compiler).createScope(n, null);
    FlowScope flowScope = LinkedFlowScope.createEntryLattice(scope);

    assertEquals(Token.CALL, call.getType());
    assertEquals(Token.NAME, name.getType());

    GoogleCodingConvention convention = new GoogleCodingConvention();
    flowScope.inferSlotType("a", type);
    ClosureReverseAbstractInterpreter rai =
        new ClosureReverseAbstractInterpreter(convention, registry);

    // trueScope
    assertEquals(trueType,
        rai.getPreciserScopeKnowingConditionOutcome(call, flowScope, true)
        .getSlot("a").getType());

    // falseScope
    assertEquals(falseType,
        rai.getPreciserScopeKnowingConditionOutcome(call, flowScope, false)
        .getSlot("a").getType());
  }
}
