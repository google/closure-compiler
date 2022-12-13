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

import static com.google.javascript.rhino.testing.TemplateTypeMapSubject.assertThat;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.testing.BaseJSTypeTestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TemplateTypeMapTest extends BaseJSTypeTestCase {

  private TemplateTypeMap emptyMap;

  @Before
  public void setUp() throws Exception {
    emptyMap = registry.getEmptyTemplateTypeMap();
  }

  @Test
  public void testCreateEmptyMap_throwsWhenDuplicateRequested() {
    assertThrows(Exception.class, () -> TemplateTypeMap.createEmpty(registry));
  }

  @Test
  public void testCopyExtend_createsNewMap() {
    // When
    TemplateTypeMap result =
        emptyMap.copyWithExtension(ImmutableList.of(key("A")), ImmutableList.of(NUMBER_TYPE));

    // Then
    assertThat(result).isNotSameInstanceAs(emptyMap);
  }

  @Test
  public void testCopyExtend_validatesCountOfKeysVsValues() {
    assertThrows(
        Exception.class,
        () -> emptyMap.copyWithExtension(ImmutableList.of(), ImmutableList.of(NUMBER_TYPE)));
  }

  @Test
  public void testCopyExtend_emptyMap() {
    // Given
    TemplateType keyT = key("T");
    TemplateType keyU = key("U");

    // When
    TemplateTypeMap result =
        emptyMap.copyWithExtension(ImmutableList.of(keyT, keyU), ImmutableList.of(NUMBER_TYPE));

    // Then
    assertThat(result)
        .hasKeysAndValues(ImmutableList.of(keyT, keyU), ImmutableList.of(NUMBER_TYPE));
  }

  @Test
  public void testCopyExtend_partialMap_fillsExitingUnfilledKeysWithUnknown() {
    // Given
    TemplateType keyA = key("A");
    TemplateType keyB = key("B");
    TemplateType keyC = key("C");
    TemplateTypeMap existing =
        createMap(ImmutableList.of(keyA, keyB, keyC), ImmutableList.of(NUMBER_TYPE));

    TemplateType keyX = key("X");
    TemplateType keyY = key("Y");
    TemplateType keyZ = key("Z");

    // When
    TemplateTypeMap result =
        existing.copyWithExtension(
            ImmutableList.of(keyX, keyY, keyZ), ImmutableList.of(STRING_TYPE));

    // Then
    assertThat(result)
        .hasKeysAndValues(
            ImmutableList.of(keyA, keyB, keyC, keyX, keyY, keyZ),
            ImmutableList.of(NUMBER_TYPE, UNKNOWN_TYPE, UNKNOWN_TYPE, STRING_TYPE));
  }

  @Test
  public void testCopyExtend_partialMap_emptyExtension_fillsExitingUnfilledKeysWithUnknown() {
    // Given
    TemplateType keyA = key("A");
    TemplateType keyB = key("B");
    TemplateType keyC = key("C");
    TemplateTypeMap existing =
        createMap(ImmutableList.of(keyA, keyB, keyC), ImmutableList.of(NUMBER_TYPE));
    // When
    TemplateTypeMap result = existing.copyWithExtension(ImmutableList.of(), ImmutableList.of());

    // Then
    assertThat(result)
        .hasKeysAndValues(
            ImmutableList.of(keyA, keyB, keyC),
            ImmutableList.of(NUMBER_TYPE, UNKNOWN_TYPE, UNKNOWN_TYPE));
  }

  @Test
  public void testCopyExtend_ifMapFilled_ifExtensionEmpty_returnsSelf() {
    // Given
    TemplateTypeMap existing = createMap(ImmutableList.of(key("A")), ImmutableList.of(NUMBER_TYPE));

    // When
    TemplateTypeMap result = emptyMap.copyWithExtension(ImmutableList.of(), ImmutableList.of());

    // Then
    assertThat(result).isNotSameInstanceAs(existing);
  }

  @Test
  public void testCopyFill_validatesValueCount_againstUnfilledKeys() {
    assertThrows(
        Exception.class, () -> emptyMap.copyFilledWithValues(ImmutableList.of(NUMBER_TYPE)));
  }

  @Test
  public void testCopyFill_ifNoSlotsEmpty_returnsSelf() {
    // Given
    TemplateType keyA = key("A");
    TemplateType keyB = key("B");
    TemplateTypeMap existing =
        createMap(ImmutableList.of(keyA, keyB), ImmutableList.of(NUMBER_TYPE, STRING_TYPE));

    // When
    TemplateTypeMap result = existing.copyFilledWithValues(ImmutableList.of());

    // Then
    assertThat(result).isSameInstanceAs(existing);
  }

  @Test
  public void testCopyFill_partialMap_fillsExtraKeysWithUnknown() {
    // Given
    TemplateType keyA = key("A");
    TemplateType keyB = key("B");
    TemplateType keyC = key("C");
    TemplateTypeMap existing =
        createMap(ImmutableList.of(keyA, keyB, keyC), ImmutableList.of(NUMBER_TYPE));

    // When
    TemplateTypeMap result = existing.copyFilledWithValues(ImmutableList.of(STRING_TYPE));

    // Then
    assertThat(result)
        .hasKeysAndValues(
            ImmutableList.of(keyA, keyB, keyC),
            ImmutableList.of(NUMBER_TYPE, STRING_TYPE, UNKNOWN_TYPE));
  }

  @Test
  public void testCopyWithout_canRemoveUnfilledKeys() {
    // Given
    TemplateType keyA = key("A");
    TemplateType keyB = key("B");
    TemplateType keyC = key("C");
    TemplateTypeMap existing =
        createMap(ImmutableList.of(keyA, keyB, keyC), ImmutableList.of(NUMBER_TYPE));

    // When
    TemplateTypeMap result = existing.copyWithoutKeys(ImmutableSet.of(keyB));

    // Then
    assertThat(result)
        .hasKeysAndValues(ImmutableList.of(keyA, keyC), ImmutableList.of(NUMBER_TYPE));
  }

  @Test
  public void testCopyWithout_retainFilledKeys() {
    // Given
    TemplateType keyA = key("A");
    TemplateType keyB = key("B");
    TemplateType keyC = key("C");
    TemplateTypeMap existing =
        createMap(ImmutableList.of(keyA, keyB, keyC), ImmutableList.of(NUMBER_TYPE));

    // When
    TemplateTypeMap result = existing.copyWithoutKeys(ImmutableSet.of(keyA));

    // Then
    assertThat(result)
        .hasKeysAndValues(ImmutableList.of(keyA, keyB, keyC), ImmutableList.of(NUMBER_TYPE));
  }

  @Test
  public void testCopyWithout_ifNothingRemoved_returnsSelf() {
    // Given
    TemplateType keyA = key("A");
    TemplateType keyB = key("B");
    TemplateType keyC = key("C");
    TemplateTypeMap existing =
        createMap(ImmutableList.of(keyA, keyB, keyC), ImmutableList.of(NUMBER_TYPE));

    // When
    TemplateTypeMap result = existing.copyWithoutKeys(ImmutableSet.of(keyA));

    // Then
    assertThat(result).isSameInstanceAs(existing);
  }

  @Test
  public void testGetLastTemplateTypeKeyByName_returnsLastKeyIfDuplicates() {
    TemplateType key1 = key("A");
    TemplateType key2 = key("A");

    TemplateTypeMap map = createMap(ImmutableList.of(key1, key2), ImmutableList.of(NUMBER_TYPE));

    TemplateType result = map.getLastTemplateTypeKeyByName("A");

    assertType(result).isSameInstanceAs(key2);
  }

  private TemplateTypeMap createMap(
      ImmutableList<TemplateType> keys, ImmutableList<JSType> values) {
    return emptyMap.copyWithExtension(keys, values);
  }

  private TemplateType key(String name) {
    return registry.createTemplateType(name);
  }
}
