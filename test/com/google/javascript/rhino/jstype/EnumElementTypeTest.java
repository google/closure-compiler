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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;

import com.google.javascript.rhino.testing.BaseJSTypeTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for EnumElementTypes.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
@RunWith(JUnit4.class)
public class EnumElementTypeTest extends BaseJSTypeTestCase {
  @Test
  public void testSubtypeRelation() {
    EnumElementType typeA = registry.createEnumType(
        "typeA", null, NUMBER_TYPE).getElementsType();
    EnumElementType typeB = registry.createEnumType(
        "typeB", null, NUMBER_TYPE).getElementsType();

    assertThat(typeA.isSubtype(typeB)).isFalse();
    assertThat(typeB.isSubtype(typeA)).isFalse();

    assertThat(NUMBER_TYPE.isSubtypeOf(typeB)).isFalse();
    assertThat(NUMBER_TYPE.isSubtypeOf(typeA)).isFalse();

    assertThat(typeA.isSubtype(NUMBER_TYPE)).isTrue();
    assertThat(typeB.isSubtype(NUMBER_TYPE)).isTrue();
  }

  @Test
  public void testGetGreatestSubtype() {
    EnumElementType typeA = registry.createEnumType(
        "typeA", null, createUnionType(NUMBER_TYPE, STRING_TYPE))
        .getElementsType();

    JSType stringsOfA = typeA.getGreatestSubtype(STRING_TYPE);
    assertThat(stringsOfA.isEmptyType()).isFalse();
    assertThat(stringsOfA.toString()).isEqualTo("typeA<string>");
    assertThat(stringsOfA.isSubtypeOf(typeA)).isTrue();

    JSType numbersOfA = NUMBER_TYPE.getGreatestSubtype(typeA);
    assertThat(numbersOfA.isEmptyType()).isFalse();
    assertThat(numbersOfA.toString()).isEqualTo("typeA<number>");
    assertThat(numbersOfA.isSubtypeOf(typeA)).isTrue();
  }

  @Test
  public void testGetGreatestSubtype_twoEnumElementTypes() {
    EnumElementType typeA = registry.createEnumType(
        "typeA", null, NUMBER_TYPE).getElementsType();
    EnumElementType typeB = registry.createEnumType(
        "typeB", null, NUMBER_TYPE).getElementsType();

    JSType greatestSubtype = EnumElementType.getGreatestSubtype(typeA, typeB);

    assertType(greatestSubtype).isSubtypeOf(typeA);
    assertType(greatestSubtype).isSubtypeOf(typeB);
    // This equality is because comparing unresolved type equality is just dependent on reference
    // name, due to 'NamedTypes' being difficult or impossible to correctly check equality on.
    assertType(greatestSubtype).isEqualTo(typeA);
    assertType(greatestSubtype).isNotEqualTo(typeB);

    assertType(typeA).isNotSubtypeOf(greatestSubtype);
    assertType(typeB).isNotSubtypeOf(greatestSubtype);
  }

  @Test
  public void testGetGreatestSubtype_twoEnumElementTypes_postResolution() {
    EnumElementType typeA = registry.createEnumType("typeA", null, NUMBER_TYPE).getElementsType();
    EnumElementType typeB = registry.createEnumType("typeB", null, NUMBER_TYPE).getElementsType();
    typeA.resolve(null);
    typeB.resolve(null);

    JSType greatestSubtype = EnumElementType.getGreatestSubtype(typeA, typeB);
    greatestSubtype.resolve(null);

    assertType(greatestSubtype).isSubtypeOf(typeA);
    assertType(greatestSubtype).isSubtypeOf(typeB);
    assertType(greatestSubtype).isNotEqualTo(typeA);
    assertType(greatestSubtype).isNotEqualTo(typeB);

    assertType(typeA).isNotSubtypeOf(greatestSubtype);
    assertType(typeB).isNotSubtypeOf(greatestSubtype);
  }

  @Test
  public void testEqualityOfEnumTypes_withSameReferenceName() {
    EnumType firstFoo = registry.createEnumType("Foo", null, NUMBER_TYPE);
    EnumType secondFoo = registry.createEnumType("Foo", null, NUMBER_TYPE);

    assertType(firstFoo).isNotEqualTo(secondFoo);
    // Pre-resolution type equality is intentionally loose because of NamedTypes, so treat these
    // types as potentially equal.
    assertType(firstFoo.getElementsType()).isEqualTo(secondFoo.getElementsType());

    firstFoo.resolve(registry.getErrorReporter());
    secondFoo.resolve(registry.getErrorReporter());

    assertType(firstFoo).isNotEqualTo(secondFoo);
    assertType(firstFoo.getElementsType()).isNotEqualTo(secondFoo.getElementsType());
  }
}
