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

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Serializable;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CyclicSerializableLinkedHashSetTest {

  @Test
  public void testSize_afterConstruction() {
    assertThat(new CyclicSerializableLinkedHashSet<>()).isEmpty();
  }

  @Test
  public void testCanAdd_distinctElements() {
    // Given
    Set<String> underTest = new CyclicSerializableLinkedHashSet<>();

    // When
    underTest.add("a");
    underTest.add("b");
    underTest.add("c");

    // Then
    assertThat(underTest).containsExactly("a", "b", "c");
  }

  @Test
  public void testCanAdd_distinctElements_afterDeserialization() {
    // Given
    Set<String> underTest = new CyclicSerializableLinkedHashSet<>();
    underTest.add("a");
    underTest.add("b");

    underTest = serializeAndBack(underTest);

    // When
    underTest.add("c");

    // Then
    assertThat(underTest).containsExactly("a", "b", "c");
  }

  @Test
  public void testCannotAdd_equalElements() {
    // Given
    Set<String> underTest = new CyclicSerializableLinkedHashSet<>();

    // When
    underTest.add("a");
    underTest.add("a");
    underTest.add("a");

    // Then
    assertThat(underTest).containsExactly("a");
  }

  @Test
  public void testCannotAdd_equalElements_afterDeserialization() {
    // Given
    Set<String> underTest = new CyclicSerializableLinkedHashSet<>();
    underTest.add("a");
    underTest.add("a");

    underTest = serializeAndBack(underTest);

    // When
    underTest.add("a");

    // Then
    assertThat(underTest).containsExactly("a");
  }

  @Test
  public void testIterationOrder_isBasedOnInsertionOrder() {
    // Given
    Set<String> underTest = new CyclicSerializableLinkedHashSet<>();

    // When
    underTest.add("c");
    underTest.add("b");
    underTest.add("a");

    // Then
    assertThat(underTest).containsExactly("c", "b", "a").inOrder();
  }

  @Test
  public void testIterationOrder_isBasedOnInsertionOrder_afterDeserialization() {
    // Given
    Set<String> underTest = new CyclicSerializableLinkedHashSet<>();
    underTest.add("c");
    underTest.add("b");

    underTest = serializeAndBack(underTest);

    // When
    underTest.add("a");

    // Then
    assertThat(underTest).containsExactly("c", "b", "a").inOrder();
  }

  @Test
  public void testIterationOrder_isStable_afterAddingEqualElement() {
    // Given
    Set<String> underTest = new CyclicSerializableLinkedHashSet<>();

    // When
    underTest.add("a");
    underTest.add("b");
    underTest.add("c");
    underTest.add(new String("a")); // Make sure this is a distinct string.

    // Then
    assertThat(underTest).containsExactly("a", "b", "c").inOrder();
  }

  @Test
  public void testIterationOrder_isStable_afterAddingEqualElement_afterDeserialization() {
    // Given
    Set<String> underTest = new CyclicSerializableLinkedHashSet<>();
    underTest.add("a");
    underTest.add("b");
    underTest.add("c");

    underTest = serializeAndBack(underTest);

    // When
    underTest.add(new String("a")); // Make sure this is a distinct string.

    // Then
    assertThat(underTest).containsExactly("a", "b", "c").inOrder();
  }

  @Test
  public void testDeserialization_producesAnEqualButDistinctSet() {
    // Given
    Set<String> underTest = new CyclicSerializableLinkedHashSet<>();
    underTest.add("a");
    underTest.add("b");
    underTest.add("c");

    // When
    Set<String> result = serializeAndBack(underTest);

    // Then
    assertThat(result).isInstanceOf(underTest.getClass());
    assertThat(result).isNotSameAs(underTest);
    assertThat(result).isEqualTo(underTest);
  }

  // We test this specifically because this behaviour is what makes existing `HashSet`s unsafe for
  // use with object cycles + deserialization.
  @Test
  public void testDeserialization_doesNotCallHashcode_onElements() {
    // Given
    Set<HashCodeThrower> underTest = new CyclicSerializableLinkedHashSet<>();
    underTest.add(new HashCodeThrower());
    underTest.add(new HashCodeThrower());
    underTest.add(new HashCodeThrower());

    assertThat(underTest).hasSize(3);
    underTest.forEach(Object::hashCode); // Make sure this doesn't throw.

    // When & Then
    // The throwers would throw on deserialization if this test failed. We can't iterate over the
    // result because that would call `hashCode`.
    serializeAndBack(underTest);
  }

  /** Throws an error if {@link #hashCode()} is called on a deserialized instance. */
  private static final class HashCodeThrower implements Serializable {
    private final transient boolean wasConstructed;

    public HashCodeThrower() {
      wasConstructed = true;
    }

    @Override
    public int hashCode() {
      assertThat(wasConstructed).isTrue();
      return System.identityHashCode(this);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T serializeAndBack(T original) {
    try {
      PipedInputStream inputStream = new PipedInputStream();
      ObjectOutputStream objectOutputStream =
          new ObjectOutputStream(new PipedOutputStream(inputStream));
      ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);

      objectOutputStream.writeObject(original);
      return (T) objectInputStream.readObject();
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }
}
