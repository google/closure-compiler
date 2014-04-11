/*
 * Copyright 2013 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.newtypes;

import static com.google.javascript.jscomp.newtypes.JSType.BOOLEAN;
import static com.google.javascript.jscomp.newtypes.JSType.BOTTOM;
import static com.google.javascript.jscomp.newtypes.JSType.FALSE_TYPE;
import static com.google.javascript.jscomp.newtypes.JSType.NULL;
import static com.google.javascript.jscomp.newtypes.JSType.NUMBER;
import static com.google.javascript.jscomp.newtypes.JSType.STRING;
import static com.google.javascript.jscomp.newtypes.JSType.TOP;
import static com.google.javascript.jscomp.newtypes.JSType.TRUE_TYPE;
import static com.google.javascript.jscomp.newtypes.JSType.UNDEFINED;
import static com.google.javascript.jscomp.newtypes.JSType.UNKNOWN;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.parsing.JsDocInfoParser;
import com.google.javascript.rhino.Node;

import junit.framework.TestCase;

/**
 * Unit tests for JSType/fromTypeExpression.
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public class JSTypeTest extends TestCase {
  private static final JSType fromNothingToUnknown =
      FunctionType.makeJSType(null, null, null, UNKNOWN);
  private static final JSType fromNothingToNumber =
      FunctionType.makeJSType(null, null, null, NUMBER);
  private static final JSType numorstr = JSType.join(NUMBER, STRING);

  private static JSType parse(String typestring) {
    Node typeAst = JsDocInfoParser.parseTypeString(typestring);
    assertTrue(typeAst != null);
    JSTypeCreatorFromJSDoc parser = new JSTypeCreatorFromJSDoc();
    JSType result = parser.getTypeFromNode(typeAst, null, null, null);
    assertEquals(ImmutableSet.of(), parser.getWarnings());
    return result;
  }

  private static void parseAndCompareTypes(String typestring, JSType expected) {
    assertEquals(expected, parse(typestring));
  }

  public void testSimpleTypes() {
    parseAndCompareTypes("boolean", BOOLEAN);
    parseAndCompareTypes("null", NULL);
    parseAndCompareTypes("number", NUMBER);
    parseAndCompareTypes("string", STRING);
    parseAndCompareTypes("undefined", UNDEFINED);
  }

  public void testUnion() {
    parseAndCompareTypes("(number|string)", numorstr);
    parseAndCompareTypes("(boolean|number|string)",
        JSType.join(BOOLEAN, numorstr));
  }

  public void testQmark() {
    parseAndCompareTypes("?}", UNKNOWN); // Right curly needed to avoid null ptr
    parseAndCompareTypes("?number", JSType.join(NUMBER, NULL));
  }

  public void testTop() {
    parseAndCompareTypes("*", TOP);
  }

  public void testParseRecordType() {
    parseAndCompareTypes("{x : number}",
        JSType.fromObjectType(ObjectType.fromProperties(
            ImmutableMap.of("x", NUMBER))));
    parseAndCompareTypes("{x : number, y : string}",
        JSType.fromObjectType(ObjectType.fromProperties(
              ImmutableMap.of("x", NUMBER, "y", STRING))));
  }

  public void testParseFunctionType() {
    parseAndCompareTypes("function ()", fromNothingToUnknown);
    parseAndCompareTypes("function (number, string): number",
        FunctionType.makeJSType(
            ImmutableList.of(NUMBER, STRING), null, null, NUMBER));
    parseAndCompareTypes("function (number, string=):number",
        FunctionType.makeJSType(
            ImmutableList.of(NUMBER),
            ImmutableList.of(JSType.join(STRING, UNDEFINED)),
            null, NUMBER));
    parseAndCompareTypes("function (string=): number",
        FunctionType.makeJSType(null,
            ImmutableList.of(JSType.join(STRING, UNDEFINED)), null, NUMBER));
    parseAndCompareTypes("function (string=, ...[number]): number",
        FunctionType.makeJSType(null,
            ImmutableList.of(JSType.join(STRING, UNDEFINED)), NUMBER, NUMBER));
    parseAndCompareTypes("function (string=, ...[string]): number",
        FunctionType.makeJSType(null,
            ImmutableList.of(JSType.join(STRING, UNDEFINED)), STRING, NUMBER));
  }

  public void testIsBoolean() {
    assertTrue(JSType.join(TRUE_TYPE, FALSE_TYPE).isBoolean());
    assertFalse(JSType.join(NUMBER,
            JSType.join(TRUE_TYPE, FALSE_TYPE)).isBoolean());
  }

  public void testJoin() {
    assertEquals(UNKNOWN, JSType.join(NUMBER, UNKNOWN));
    assertEquals(TOP, JSType.join(TOP, UNKNOWN));
    assertEquals(TOP, JSType.join(NUMBER, TOP));
  }

  public void testIsSubtypeOf() {
    assertTrue(UNKNOWN.isSubtypeOf(TOP));
    assertTrue(TOP.isSubtypeOf(UNKNOWN));
    assertTrue(NUMBER.isSubtypeOf(numorstr));
  }

  public void testToString() {
    assertEquals("boolean", BOOLEAN.toString());
    assertEquals("bottom", BOTTOM.toString());
    assertEquals("boolean|number", JSType.join(BOOLEAN, NUMBER).toString());
    assertEquals("top", TOP.toString());
    assertEquals("?", UNKNOWN.toString());
    assertEquals("function (): number", fromNothingToNumber.toString());
    assertEquals("function (): number|number",
        JSType.join(NUMBER, fromNothingToNumber).toString());
  }

  public void testMeet() {
    JSType union = JSType.join(fromNothingToUnknown, BOOLEAN);
    assertEquals(JSType.meet(union, BOOLEAN), BOOLEAN);
    assertEquals(
        JSType.meet(union, fromNothingToUnknown), fromNothingToUnknown);
  }

  public void testEquals() {
    assertFalse(fromNothingToUnknown.equals(fromNothingToNumber));
  }
}
