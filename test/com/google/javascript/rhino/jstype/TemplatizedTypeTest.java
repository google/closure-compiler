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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;

import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.testing.BaseJSTypeTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TemplatizedTypeTest extends BaseJSTypeTestCase {

  /** Tests the behavior of variants type. */
  @Test
  public void testTemplatizedType() {
    TemplatizedType arrOfString = createTemplatizedType(
        ARRAY_TYPE, STRING_TYPE);
    assertTypeCanAssignToItself(arrOfString);
    assertThat(arrOfString.isSubtype(ARRAY_TYPE)).isTrue();
    assertThat(ARRAY_TYPE.isSubtypeOf(arrOfString)).isTrue();

    TemplatizedType arrOfNumber = createTemplatizedType(
        ARRAY_TYPE, NUMBER_TYPE);
    assertTypeCanAssignToItself(arrOfNumber);
    assertThat(arrOfNumber.isSubtype(ARRAY_TYPE)).isTrue();
    assertThat(ARRAY_TYPE.isSubtypeOf(arrOfNumber)).isTrue();

    assertThat(arrOfString.isEquivalentTo(createTemplatizedType(ARRAY_TYPE, STRING_TYPE))).isTrue();

    assertThat(arrOfString.isEquivalentTo(ARRAY_TYPE)).isFalse();
    assertThat(arrOfString.isEquivalentTo(ARRAY_TYPE)).isFalse();
    assertThat(arrOfString.isEquivalentTo(arrOfNumber)).isFalse();
    assertThat(arrOfNumber.isEquivalentTo(arrOfString)).isFalse();
  }

  @Test
  public void testEquality() {
    // Weird that we allow this as a type at all.
    TemplatizedType booleanOfString = createTemplatizedType(BOOLEAN_OBJECT_TYPE, STRING_TYPE);
    assertThat(booleanOfString.hashCode()).isEqualTo(BOOLEAN_OBJECT_TYPE.hashCode());
  }

  @Test
  public void testPrint1() {
    TemplatizedType arrOfString = createTemplatizedType(
        ARRAY_TYPE, STRING_TYPE);
    assertThat(arrOfString.toString()).isEqualTo("Array<string>");
  }

  @Test
  public void testPrint2() {
    TemplatizedType arrOfTemplateType = createTemplatizedType(
        ARRAY_TYPE, new TemplateType(registry, "T"));
    assertThat(arrOfTemplateType.toString()).isEqualTo("Array<T>");
  }

  @Test
  public void testPrint3() {
    TemplatizedType arrOfUnknown = createTemplatizedType(
        ARRAY_TYPE, UNKNOWN_TYPE);
    assertThat(arrOfUnknown.toString()).isEqualTo("Array<?>");
  }

  @Test
  public void testPrintingRawType() {
    ObjectType rawType = createCustomTemplatizedType("Foo");

    assertThat(rawType.toString()).isEqualTo("Foo");
  }

  @Test
  public void testDifferentRawTypes() {
    TemplatizedType arrOfNumber = createTemplatizedType(
        ARRAY_TYPE, NUMBER_TYPE);
    TemplatizedType objType = createTemplatizedType(
        OBJECT_TYPE, UNKNOWN_TYPE);
    assertThat(arrOfNumber.isSubtype(objType)).isTrue();
    assertThat(objType.isSubtype(arrOfNumber)).isFalse();
  }

  @Test
  public void testSubtypingAndEquivalenceAmongCustomTemplatizedTypes() {
    ObjectType rawType = createCustomTemplatizedType("Baz");

    JSType templatizedStringNumber =
        registry.createTemplatizedType(rawType, ImmutableList.of(STRING_TYPE, NUMBER_TYPE));
    JSType templatizedStringAll =
        registry.createTemplatizedType(rawType, ImmutableList.of(STRING_TYPE, ALL_TYPE));
    JSType templatizedStringUnknown =
        registry.createTemplatizedType(rawType, ImmutableList.of(STRING_TYPE, UNKNOWN_TYPE));
    JSType templatizedUnknownUnknown =
        registry.createTemplatizedType(rawType, ImmutableList.of(UNKNOWN_TYPE, UNKNOWN_TYPE));

    assertThat(templatizedStringNumber.isSubtypeOf(rawType)).isTrue();
    assertThat(templatizedStringAll.isSubtypeOf(rawType)).isTrue();
    assertThat(templatizedStringUnknown.isSubtypeOf(rawType)).isTrue();
    assertThat(templatizedUnknownUnknown.isSubtypeOf(rawType)).isTrue();

    assertTypeNotEquals(templatizedStringNumber, rawType);
    assertTypeNotEquals(templatizedStringAll, rawType);
    assertTypeNotEquals(templatizedStringUnknown, rawType);

    // TODO(b/110224889): This case should probably be `assertTypeNotEquals`.
    assertTypeEquals(templatizedUnknownUnknown, rawType);

    assertThat(rawType.isSubtypeOf(templatizedStringNumber)).isTrue();
    assertThat(rawType.isSubtypeOf(templatizedStringAll)).isTrue();
    assertThat(rawType.isSubtypeOf(templatizedStringUnknown)).isTrue();
    assertThat(rawType.isSubtypeOf(templatizedUnknownUnknown)).isTrue();

    assertThat(templatizedStringNumber.isSubtypeOf(templatizedStringAll)).isFalse();
    assertThat(templatizedStringAll.isSubtypeOf(templatizedStringNumber)).isFalse();

    assertThat(templatizedStringAll.isSubtypeOf(templatizedStringUnknown)).isTrue();
    assertThat(templatizedStringUnknown.isSubtypeOf(templatizedStringAll)).isTrue();
  }

  @Test
  public void testGetPropertyTypeOnTemplatizedType() {
    TemplateType templateT = registry.createTemplateType("T");
    FunctionType ctor = // function<T>(new:Foo<T>)
        registry.createConstructorType("Foo", null, null, null, ImmutableList.of(templateT), false);
    ObjectType rawType = ctor.getInstanceType(); // Foo<T> == Foo
    rawType.defineDeclaredProperty("property", templateT, null);

    JSType templatizedNumber = registry.createTemplatizedType(rawType, NUMBER_TYPE);
    assertType(templatizedNumber.toObjectType().getPropertyType("property")).isEqualTo(NUMBER_TYPE);
  }

  @Test
  public void testFindPropertyTypeOnTemplatizedType() {
    TemplateType templateT = registry.createTemplateType("T");
    FunctionType ctor = // function<T>(new:Foo<T>)
        registry.createConstructorType("Foo", null, null, null, ImmutableList.of(templateT), false);
    ObjectType rawType = ctor.getInstanceType(); // Foo<T> == Foo
    rawType.defineDeclaredProperty("property", templateT, null);

    JSType templatizedNumber = registry.createTemplatizedType(rawType, NUMBER_TYPE);
    assertType(templatizedNumber.findPropertyType("property")).isEqualTo(NUMBER_TYPE);
  }

  /** Returns an unspecialized type with the provided name and two type parameters. */
  private ObjectType createCustomTemplatizedType(String rawName) {
    FunctionType ctor = // function<T,U>(new:Foo<T,U>)
        registry.createConstructorType(
            rawName,
            null,
            null,
            null,
            ImmutableList.of(registry.createTemplateType("T"), registry.createTemplateType("U")),
            false);
    return ctor.getInstanceType(); // Foo<T, U> == Foo
  }

  /** Assert that a type can assign to itself. */
  private void assertTypeCanAssignToItself(JSType type) {
    assertThat(type.isSubtypeOf(type)).isTrue();
  }
}
