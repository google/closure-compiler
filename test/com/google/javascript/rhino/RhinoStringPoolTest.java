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

package com.google.javascript.rhino;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.RhinoStringPool.LazyInternedStringList;
import com.google.javascript.rhino.RhinoStringPool.WriteOnlyBitset;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class RhinoStringPoolTest {
  // note - RhinoStringPool intentionally uses static state, so these tests are not really order-
  // independent.

  @Test
  public void returnsDotEqualsString() {
    String newFoo = RhinoStringPool.addOrGet("foo");
    assertThat(newFoo).isEqualTo("foo");
  }

  @Test
  public void multipleCallsReturnSameInstance() {
    String[] foos = new String[100];
    for (int i = 0; i < 100; i++) {
      String newFoo = new String("foo"); // use new keyword to ensure different objects
      foos[i] = RhinoStringPool.addOrGet(newFoo);
    }
    for (int i = 1; i < 100; i++) {
      assertThat(foos[i]).isSameInstanceAs(foos[0]);
    }
  }

  @Test
  public void lazyInternedStringList_outOfBounds_throwsException() {
    LazyInternedStringList list = new LazyInternedStringList(ImmutableList.of("foo"));
    assertThrows(ArrayIndexOutOfBoundsException.class, () -> list.get(100));
  }

  @Test
  public void lazyInternedStringList_negativeIndex_throwsException() {
    LazyInternedStringList list = new LazyInternedStringList(ImmutableList.of());
    assertThrows(ArrayIndexOutOfBoundsException.class, () -> list.get(-1));
  }

  @Test
  public void lazyInternedStringList_getsEqualValuesAsInputList() {
    LazyInternedStringList list = new LazyInternedStringList(ImmutableList.of("foo", "bar", "baz"));
    assertThat(list.get(0)).isEqualTo("foo");
    assertThat(list.get(1)).isEqualTo("bar");
    assertThat(list.get(2)).isEqualTo("baz");
  }

  @Test
  public void lazyInternedStringList_getsSameInstanceAsRhinoStringPool() {
    // use new keyword to ensure we're passing unique String instances
    ImmutableList<String> strings =
        ImmutableList.of(new String("foo"), new String("bar"), new String("baz"));
    LazyInternedStringList list = new LazyInternedStringList(strings);
    assertThat(list.get(0)).isSameInstanceAs(RhinoStringPool.addOrGet("foo"));
    assertThat(list.get(1)).isSameInstanceAs(RhinoStringPool.addOrGet("bar"));
    assertThat(list.get(2)).isSameInstanceAs(RhinoStringPool.addOrGet("baz"));
  }

  @Test
  public void lazyInternedStringList_veryLargeListWorks() {
    ArrayList<String> strings = new ArrayList<>();
    for (int i = 0; i < 10000; i++) {
      strings.add(new String("foo" + i));
    }
    ImmutableList<String> immutableList = ImmutableList.copyOf(strings);
    LazyInternedStringList list = new LazyInternedStringList(immutableList);
    for (int i = 0; i < 10000; i++) {
      assertThat(list.get(i)).isSameInstanceAs(RhinoStringPool.addOrGet("foo" + i));
    }
  }

  @Test
  public void writeOnlyBitset_initializedToFalse() {
    WriteOnlyBitset bitset = new WriteOnlyBitset(5);
    for (int i = 0; i < 5; i++) {
      assertThat(bitset.get(i)).isFalse();
    }
  }

  @Test
  public void writeOnlyBitset_setToTrue_thenGetReturnsTrue() {
    WriteOnlyBitset bitset = new WriteOnlyBitset(2);
    bitset.set(0);
    assertThat(bitset.get(0)).isTrue();
    assertThat(bitset.get(1)).isFalse();
  }

  @Test
  public void writeOnlyBitset_wordBoundary_setToTrue_thenGetReturnsTrue() {
    WriteOnlyBitset bitset = new WriteOnlyBitset(33);
    bitset.set(32);
    assertThat(bitset.get(32)).isTrue();
  }

  @Test
  public void writeOnlyBitset_multipleSetsToTrue_allReturnTrue() {
    WriteOnlyBitset bitset = new WriteOnlyBitset(5);
    bitset.set(1);
    bitset.set(2);
    bitset.set(3);
    assertThat(bitset.get(0)).isFalse();
    assertThat(bitset.get(1)).isTrue();
    assertThat(bitset.get(2)).isTrue();
    assertThat(bitset.get(3)).isTrue();
    assertThat(bitset.get(4)).isFalse();
  }
}
