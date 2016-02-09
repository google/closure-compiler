/*
 *
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Rhino code, released
 * May 6, 1999.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1997-1999
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Nick Santos
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU General Public License Version 2 or later (the "GPL"), in which
 * case the provisions of the GPL are applicable instead of those above. If
 * you wish to allow use of your version of this file only under the terms of
 * the GPL and not to allow others to use your version of this file under the
 * MPL, indicate your decision by deleting the provisions above and replacing
 * them with the notice and other provisions required by the GPL. If you do
 * not delete the provisions above, a recipient may use your version of this
 * file under either the MPL or the GPL.
 *
 * ***** END LICENSE BLOCK ***** */

package com.google.javascript.rhino.jstype;

import com.google.javascript.rhino.testing.BaseJSTypeTestCase;

/**
 * Test for {@link UnionTypeBuilder}.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public class UnionTypeBuilderTest extends BaseJSTypeTestCase {

  public void testAllType() {
    assertUnion("*", ALL_TYPE);
    assertUnion("*", NUMBER_TYPE, ALL_TYPE);
    assertUnion("*", ALL_TYPE, NUMBER_TYPE);
    assertUnion("*", ALL_TYPE, NUMBER_TYPE, NO_TYPE);
  }

  public void testEmptyUnion() {
    assertUnion("None");
    assertUnion("None", NO_TYPE, NO_TYPE);
  }

  public void testUnionTypes() {
    JSType union = registry.createUnionType(STRING_TYPE, OBJECT_TYPE);

    assertUnion("*", ALL_TYPE, union);
    assertUnion("(Object|string)", OBJECT_TYPE, union);
    assertUnion("(Object|string)", union, OBJECT_TYPE);
    assertUnion("(Object|number|string)", NUMBER_TYPE, union);
    assertUnion("(Object|number|string)", union, NUMBER_TYPE);
    assertUnion("(Object|boolean|number|string)", union,
        registry.createUnionType(NUMBER_TYPE, BOOLEAN_TYPE));
    assertUnion("(Object|boolean|number|string)",
        registry.createUnionType(NUMBER_TYPE, BOOLEAN_TYPE), union);
    assertUnion("(Object|string)", union, STRING_OBJECT_TYPE);
  }

  public void testUnknownTypes() {
    JSType unresolvedNameA1 =
        new NamedType(registry, "not.resolved.A", null, -1, -1);
    JSType unresolvedNameA2 =
        new NamedType(registry, "not.resolved.A", null, -1, -1);
    JSType unresolvedNameB =
        new NamedType(registry, "not.resolved.B", null, -1, -1);

    assertUnion("?", UNKNOWN_TYPE);
    assertUnion("?", UNKNOWN_TYPE, UNKNOWN_TYPE);
    assertUnion("(?|undefined)", UNKNOWN_TYPE, VOID_TYPE);
    assertUnion("(?|undefined)", VOID_TYPE, UNKNOWN_TYPE);
    assertUnion("(?|undefined)", VOID_TYPE, NUMBER_TYPE, UNKNOWN_TYPE);

    assertUnion("(*|undefined)", ALL_TYPE, VOID_TYPE, NULL_TYPE);

    // NOTE: "(?)" means there are multiple unknown types in the union.
    assertUnion("?", UNKNOWN_TYPE, unresolvedNameA1);
    assertUnion("not.resolved.A", unresolvedNameA1, unresolvedNameA2);
    assertUnion("(not.resolved.A|not.resolved.B)",
        unresolvedNameA1, unresolvedNameB);
    assertUnion("(Object|not.resolved.A)", unresolvedNameA1, OBJECT_TYPE);
  }

  public void testRemovalOfDupes() {
    JSType stringAndObject =
        registry.createUnionType(STRING_TYPE, OBJECT_TYPE);
    assertUnion("(Object|string)", stringAndObject, STRING_OBJECT_TYPE);
    assertUnion("(Object|string)", STRING_OBJECT_TYPE, stringAndObject);
  }

  public void testRemovalOfDupes2() {
    JSType union =
        registry.createUnionType(
            EVAL_ERROR_TYPE,
            createFunctionWithReturn(ERROR_TYPE),
            ERROR_TYPE,
            createFunctionWithReturn(EVAL_ERROR_TYPE));
    assertEquals("(Error|function (): Error)", union.toString());
  }

  public void testRemovalOfDupes3() {
    JSType union =
        registry.createUnionType(
            ERROR_TYPE,
            createFunctionWithReturn(EVAL_ERROR_TYPE),
            EVAL_ERROR_TYPE,
            createFunctionWithReturn(ERROR_TYPE));
    assertEquals("(Error|function (): Error)", union.toString());
  }

  public void assertUnion(String expected, JSType ... types) {
    UnionTypeBuilder builder = new UnionTypeBuilder(registry);
    for (JSType type : types) {
      builder.addAlternate(type);
    }
    assertEquals(expected, builder.build().toString());
  }

  public FunctionType createFunctionWithReturn(JSType type) {
    return new FunctionBuilder(registry)
        .withParamsNode(registry.createParameters())
        .withReturnType(type)
        .build();
  }
}
