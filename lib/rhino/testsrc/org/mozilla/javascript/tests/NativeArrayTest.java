/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tests;

import org.junit.Test;
import org.junit.Before;

import static org.junit.Assert.assertThat;
import static org.hamcrest.core.Is.is;

import org.mozilla.javascript.NativeArray;

public class NativeArrayTest {
  private NativeArray array;

  @Before
  public void init() {
    array = new NativeArray(1);
  }

  @Test
  public void getIdsShouldIncludeBothIndexAndNormalProperties() {
    array.put(0, array, "index");
    array.put("a", array, "normal");

    assertThat(array.getIds(), is(new Object[]{0, "a"}));
  }

  @Test
  public void deleteShouldRemoveIndexProperties() {
    array.put(0, array, "a");
    array.delete(0);
    assertThat(array.has(0, array), is(false));
  }

  @Test
  public void deleteShouldRemoveNormalProperties() {
    array.put("p", array, "a");
    array.delete("p");
    assertThat(array.has("p", array), is(false));
  }

  @Test
  public void putShouldAddIndexProperties() {
    array.put(0, array, "a");
    assertThat(array.has(0, array), is(true));
  }

  @Test
  public void putShouldAddNormalProperties() {
    array.put("p", array, "a");
    assertThat(array.has("p", array), is(true));
  }

  @Test
  public void getShouldReturnIndexProperties() {
    array.put(0, array, "a");
    array.put("p", array, "b");
    assertThat((String) array.get(0, array), is("a"));
  }

  @Test
  public void getShouldReturnNormalProperties() {
    array.put("p", array, "a");
    assertThat((String) array.get("p", array), is("a"));
  }

  @Test
  public void hasShouldBeFalseForANewArray() {
    assertThat(new NativeArray(0).has(0, array), is(false));
  }

  @Test
  public void getIndexIdsShouldBeEmptyForEmptyArray() {
    assertThat(new NativeArray(0).getIndexIds(), is(new Integer[]{}));
  }

  @Test
  public void getIndexIdsShouldBeAZeroForSimpleSingletonArray() {
    array.put(0, array, "a");
    assertThat(array.getIndexIds(), is(new Integer[]{ 0 }));
  }

  @Test
  public void getIndexIdsShouldWorkWhenIndicesSetAsString() {
    array.put("0", array, "a");
    assertThat(array.getIndexIds(), is(new Integer[]{ 0 }));
  }

  @Test
  public void getIndexIdsShouldNotIncludeNegativeIds() {
    array.put(-1, array, "a");
    assertThat(array.getIndexIds(), is(new Integer[]{}));
  }

  @Test
  public void getIndexIdsShouldIncludeIdsLessThan2ToThe32() {
    int maxIndex = (int) (1L << 31) - 1;
    array.put(maxIndex, array, "a");
    assertThat(array.getIndexIds(), is(new Integer[]{ maxIndex }));
  }

  @Test
  public void getIndexIdsShouldNotIncludeIdsGreaterThanOrEqualTo2ToThe32() {
    array.put((1L<<31)+"", array, "a");
    assertThat(array.getIndexIds(), is(new Integer[]{}));
  }

  @Test
  public void getIndexIdsShouldNotReturnNonNumericIds() {
    array.put("x", array, "a");
    assertThat(array.getIndexIds(), is(new Integer[]{}));
  }

}
