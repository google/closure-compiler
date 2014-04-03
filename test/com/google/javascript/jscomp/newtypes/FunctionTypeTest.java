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

import static com.google.javascript.jscomp.newtypes.FunctionType.TOP_FUNCTION;
import static com.google.javascript.jscomp.newtypes.JSType.BOTTOM;
import static com.google.javascript.jscomp.newtypes.JSType.NUMBER;
import static com.google.javascript.jscomp.newtypes.JSType.UNDEFINED;
import static com.google.javascript.jscomp.newtypes.NominalType.RawNominalType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.parsing.JsDocInfoParser;

import junit.framework.TestCase;

/**
 * Unit tests for FunctionType.
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public class FunctionTypeTest extends TestCase {
  private static final FunctionType fooConstructor = FunctionType.normalized(
      null, null, null, null,
      NominalType.fromRaw(RawNominalType.makeUnrestrictedClass("Foo", null)),
      null, null, false);

  private static FunctionType parse(String typestring) {
    JSTypeCreatorFromJSDoc parser = new JSTypeCreatorFromJSDoc();
    FunctionType result = parser.getTypeFromNode(
        JsDocInfoParser.parseTypeString(typestring), null, null, null)
        .getFunType();
    assertEquals(ImmutableSet.of(), parser.getWarnings());
    return result;
  }

  public void testUnknownsInFunctions() {
    assertTrue(parse("function (?)").isSubtypeOf(parse("function (string)")));
    assertTrue(parse("function (string)").isSubtypeOf(parse("function (?)")));
  }

  public void testFunctionJoinAndMeet() {
    FunctionType numToNum = parse("function (number): number");
    FunctionType optnumToNum = parse("function (number=): number");
    FunctionType optstrToNum = parse("function (string=): number");
    FunctionType numorstrToNum = parse("function ((number|string)): number");
    FunctionType anynumsToNum =
        FunctionType.makeJSType(null, null, NUMBER, NUMBER).getFunType();

    assertEquals(TOP_FUNCTION, FunctionType.join(TOP_FUNCTION, numToNum));
    assertEquals(numToNum, FunctionType.meet(TOP_FUNCTION, numToNum));
    assertEquals(numToNum, FunctionType.join(null, numToNum));
    assertEquals(null, FunctionType.meet(null, numToNum));
    // joins
    assertEquals(
        parse("function (number): (number|string)"),
        FunctionType.join(numToNum, parse("function (): string")));
    assertEquals(numToNum, FunctionType.join(optnumToNum, numorstrToNum));
    assertEquals(FunctionType.makeJSType(
        ImmutableList.of(BOTTOM), null, null, NUMBER).getFunType(),
        FunctionType.join(numToNum, parse("function (string): number")));
    assertEquals(FunctionType.makeJSType(
        null, ImmutableList.of(UNDEFINED), null, NUMBER).getFunType(),
        FunctionType.join(optnumToNum, optstrToNum));
    // meets
    assertEquals(
        parse("function (number, number=)"),
        FunctionType.meet(
            parse("function (number, number)"), parse("function (number)")));
    assertEquals(
        parse("function ((number|string)=): number"),
        FunctionType.meet(optnumToNum, optstrToNum));
    assertEquals(numorstrToNum,
        FunctionType.meet(
            numToNum, parse("function (string): number")));
    assertEquals(anynumsToNum,
        FunctionType.meet(
            anynumsToNum, parse("function (): number")));
  }

  public void testLooseFunctionSpecializeAndMeet() {
    // specialize
    assertEquals(
        parse("function((number|string))").withLoose(),
        parse("function(number)").withLoose().specialize(
            parse("function(string)")));
    assertEquals(
        parse("function(number)"),
        parse("function(number)").specialize(
            parse("function(string)").withLoose()));

    // meet (which defers to looseJoin)
    assertEquals(
        parse("function((number|string))").withLoose(),
        FunctionType.meet(
            parse("function(number)").withLoose(),
            parse("function(string)").withLoose()));
    assertEquals(
        parse("function((number|string))").withLoose(),
        FunctionType.meet(
            parse("function(number)"),
            parse("function(string)").withLoose()));
    assertEquals(
        parse("function((number|string))").withLoose(),
        FunctionType.meet(
            parse("function(number)").withLoose(),
            parse("function(string)")));
    assertEquals(
        parse("function ((number|string)): (number|string)").withLoose(),
        FunctionType.meet(
            parse("function (number): number").withLoose(),
            parse("function (string): string").withLoose()));
    assertEquals(
        parse("function((number|string)=, string=)").withLoose(),
        FunctionType.meet(
            parse("function(number, string)").withLoose(),
            parse("function(string=)").withLoose()));
    assertEquals(
        parse("function((number|string)=)").withLoose(),
        FunctionType.meet(
            parse("function(number, ...[string])"),
            parse("function(string=)").withLoose()));
    assertEquals(
        parse("function((number|string)=)").withLoose(),
        FunctionType.meet(
            parse("function(...[number])"),
            parse("function(string=)").withLoose()));

  }

  public void testToString() {
    assertEquals("function (new:Foo)", fooConstructor.toString());
  }

}
