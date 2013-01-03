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
 *   John Lenz
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

public class ParameterizedTypeTest extends BaseJSTypeTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected ParameterizedType createParameterizedType(
      ObjectType objectType, JSType parameterType) {
    return registry.createParameterizedType(objectType, parameterType);
  }

  /**
   * Assert that a type can assign to itself.
   */
  private void assertTypeCanAssignToItself(JSType type) {
    assertTrue(type.isSubtype(type));
  }

  /**
   * Tests the behavior of variants type.
   */
  @SuppressWarnings("checked")
  public void testParameterizedType() throws Exception {
    ParameterizedType arrOfString = createParameterizedType(
        ARRAY_TYPE, STRING_TYPE);
    assertTypeCanAssignToItself(arrOfString);
    assertTrue(arrOfString.isSubtype(ARRAY_TYPE));
    assertTrue(ARRAY_TYPE.isSubtype(arrOfString));

    ParameterizedType arrOfNumber = createParameterizedType(
        ARRAY_TYPE, NUMBER_TYPE);
    assertTypeCanAssignToItself(arrOfNumber);
    assertTrue(arrOfNumber.isSubtype(ARRAY_TYPE));
    assertTrue(ARRAY_TYPE.isSubtype(arrOfNumber));

    assertTrue(arrOfString.isEquivalentTo(createParameterizedType(
        ARRAY_TYPE, STRING_TYPE)));

    assertFalse(arrOfString.isEquivalentTo(ARRAY_TYPE));
    assertFalse(arrOfString.isEquivalentTo(ARRAY_TYPE));
    assertFalse(arrOfString.isEquivalentTo(arrOfNumber));
    assertFalse(arrOfNumber.isEquivalentTo(arrOfString));
  }

  public void testPrint1() throws Exception {
    ParameterizedType arrOfString = createParameterizedType(
        ARRAY_TYPE, STRING_TYPE);
    assertEquals("Array.<string>", arrOfString.toString());
  }

  public void testPrint2() throws Exception {
    ParameterizedType arrOfTemplateType = createParameterizedType(
        ARRAY_TYPE, new TemplateType(registry, "T"));
    assertEquals("Array.<T>", arrOfTemplateType.toString());
  }

  public void testPrint3() throws Exception {
    ParameterizedType arrOfUnknown = createParameterizedType(
        ARRAY_TYPE, UNKNOWN_TYPE);
    assertEquals("Array.<?>", arrOfUnknown.toString());
  }

  public void testDifferentRawTypes() throws Exception {
    ParameterizedType arrOfNumber = createParameterizedType(
        ARRAY_TYPE, NUMBER_TYPE);
    ParameterizedType objType = createParameterizedType(
        OBJECT_TYPE, UNKNOWN_TYPE);
    assertTrue(arrOfNumber.isSubtype(objType));
    assertFalse(objType.isSubtype(arrOfNumber));
  }
}
