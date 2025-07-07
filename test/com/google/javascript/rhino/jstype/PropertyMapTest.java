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
 *   Bob Jervis
 *   Google Inc.
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.javascript.rhino.testing.TestErrorReporter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PropertyMapTest {
  Property.Key symbolKey = null;
  JSTypeRegistry registry = null;
  JSType numberType = null;

  @Before
  public void setUp() {
    this.registry = new JSTypeRegistry(new TestErrorReporter());
    this.symbolKey = new Property.SymbolKey(new KnownSymbolType(registry, "Symbol.foo"));
    this.numberType = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
  }

  @Test
  public void emptyMap_hasNoProperties() {
    PropertyMap empty = PropertyMap.immutableEmptyMap();

    assertThat(empty.getPropertiesCount()).isEqualTo(0);
    assertThat(empty.getOwnPropertyNames()).isEmpty();
    assertThat(empty.getOwnKnownSymbols()).isEmpty();
    assertThat(empty.getAllKeys())
        .isEqualTo(new PropertyMap.AllKeys(ImmutableSortedSet.of(), ImmutableSet.of()));
    assertThat(empty.getPrimaryParent()).isNull();
    assertThat(empty.getOwnProperty("x")).isNull();
    assertThat(empty.getOwnProperty(symbolKey)).isNull();
  }

  @Test
  public void getAllKeys_returnsBothSymbolAndStringKeys() {
    PropertyMap properties = new PropertyMap();

    properties.putProperty("x", new Property("x", numberType, /* inferred= */ false, null));
    properties.putProperty(
        symbolKey, new Property(symbolKey, numberType, /* inferred= */ false, null));

    assertThat(properties.getAllKeys().stringKeys()).containsExactly("x");
    assertThat(properties.getAllKeys().knownSymbolKeys()).containsExactly(symbolKey.symbol());
  }

  @Test
  public void getAllKeys_cachesStringKeyResult() {
    PropertyMap properties = new PropertyMap();

    properties.putProperty("x", new Property("x", numberType, /* inferred= */ false, null));

    var result1 = properties.getAllKeys().stringKeys();
    var result2 = properties.getAllKeys().stringKeys();
    assertThat(result1).isSameInstanceAs(result2);
  }

  @Test
  public void getAllKeys_cachesSymbolKeyResult() {
    PropertyMap properties = new PropertyMap();

    properties.putProperty(
        symbolKey, new Property(symbolKey, numberType, /* inferred= */ false, null));

    var result1 = properties.getAllKeys().knownSymbolKeys();
    var result2 = properties.getAllKeys().knownSymbolKeys();
    assertThat(result1).isSameInstanceAs(result2);
  }

  @Test
  public void getAllKeys_beforeAndAfterMutation_stringKey_invalidatesCache() {
    // PropertyMap caches the result of getAllKeys(), as it can be expensive to compute for
    // maps with many ancestors. Verify that recalculating getAllKeys() after a mutation returns the
    // correct result.
    PropertyMap properties = new PropertyMap();

    // Request getAllKeys() once, which will cache the empty results.
    assertThat(properties.getAllKeys().stringKeys()).isEmpty();

    // Invalidate the cache.
    properties.putProperty("x", new Property("x", numberType, /* inferred= */ false, null));

    // getAllKeys() should recalculate the result.
    assertThat(properties.getAllKeys().stringKeys()).containsExactly("x");
  }

  @Test
  public void getAllKeys_beforeAndAfterMutation_symbolKey_invalidatesCache() {
    // PropertyMap caches the result of getAllKeys(), as it can be expensive to compute for
    // maps with many ancestors. Verify that recalculating getAllKeys() after a mutation returns the
    // correct result.
    PropertyMap properties = new PropertyMap();

    // Request getAllKeys() once, which will cache the empty results.
    assertThat(properties.getAllKeys().knownSymbolKeys()).isEmpty();

    // Invalidate the cache.
    properties.putProperty(
        symbolKey, new Property(symbolKey, numberType, /* inferred= */ false, null));

    // getAllKeys() should recalculate the result.
    assertThat(properties.getAllKeys().knownSymbolKeys()).containsExactly(symbolKey.symbol());
  }

  @Test
  public void lookUpKeysFromParent() {
    ObjectType parentType =
        registry.createRecordType(ImmutableMap.of("x", numberType)).assertObjectType();
    ObjectType childType = registry.createObjectType("Child", parentType);
    PropertyMap child = new PropertyMap();
    child.setParentSource(childType);

    assertThat(child.getOwnProperty("x")).isNull();
    assertThat(child.findClosest("x").getValue().getType()).isEqualTo(numberType);
    assertThat(child.findClosest(new Property.StringKey("x")).getValue().getType())
        .isEqualTo(numberType);

    // TODO: b/358577041 - Add tests for symbol keys.
  }
}
