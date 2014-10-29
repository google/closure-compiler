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
 * Tests for EnumElementTypes.
 * @author nicksantos@google.com (Nick Santos)
 */
public class EnumElementTypeTest extends BaseJSTypeTestCase {
  public void testSubtypeRelation() throws Exception {
    EnumElementType typeA = registry.createEnumType(
        "typeA", null, NUMBER_TYPE).getElementsType();
    EnumElementType typeB = registry.createEnumType(
        "typeB", null, NUMBER_TYPE).getElementsType();

    assertFalse(typeA.isSubtype(typeB));
    assertFalse(typeB.isSubtype(typeA));

    assertFalse(NUMBER_TYPE.isSubtype(typeB));
    assertFalse(NUMBER_TYPE.isSubtype(typeA));

    assertTrue(typeA.isSubtype(NUMBER_TYPE));
    assertTrue(typeB.isSubtype(NUMBER_TYPE));
  }

  public void testMeet() throws Exception {
    EnumElementType typeA = registry.createEnumType(
        "typeA", null, createUnionType(NUMBER_TYPE, STRING_TYPE))
        .getElementsType();

    JSType stringsOfA = typeA.getGreatestSubtype(STRING_TYPE);
    assertFalse(stringsOfA.isEmptyType());
    assertEquals("typeA<string>", stringsOfA.toString());
    assertTrue(stringsOfA.isSubtype(typeA));

    JSType numbersOfA = NUMBER_TYPE.getGreatestSubtype(typeA);
    assertFalse(numbersOfA.isEmptyType());
    assertEquals("typeA<number>", numbersOfA.toString());
    assertTrue(numbersOfA.isSubtype(typeA));
  }
}
