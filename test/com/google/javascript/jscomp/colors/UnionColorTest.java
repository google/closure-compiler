/*
 * Copyright 2020 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp.colors;

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.testing.ColorSubject.assertThat;
import static com.google.javascript.rhino.testing.Asserts.assertThrows;

import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class UnionColorTest {
  @Test
  public void isPrimitiveReturnsFalse() {
    UnionColor numberOrString =
        UnionColor.create(ImmutableSet.of(PrimitiveColor.STRING, PrimitiveColor.NUMBER));

    assertThat(numberOrString.isPrimitive()).isFalse();
  }

  @Test
  public void isUnionHandlesReturnsTrue() {
    UnionColor union =
        UnionColor.create(ImmutableSet.of(PrimitiveColor.NUMBER, PrimitiveColor.STRING));

    assertThat(union).isUnion();
  }

  @Test
  public void getAlternatesReturnsAlternatesList() {
    UnionColor union =
        UnionColor.create(ImmutableSet.of(PrimitiveColor.NUMBER, PrimitiveColor.STRING));

    assertThat(union).hasAlternates(PrimitiveColor.NUMBER, PrimitiveColor.STRING);
  }

  @Test
  public void alternatesMayContainOtherUnion() {
    UnionColor union =
        UnionColor.create(ImmutableSet.of(PrimitiveColor.NUMBER, PrimitiveColor.STRING));
    UnionColor newUnion = UnionColor.create(ImmutableSet.of(union, PrimitiveColor.BIGINT));

    assertThat(newUnion)
        .hasAlternates(PrimitiveColor.STRING, PrimitiveColor.BIGINT, PrimitiveColor.NUMBER);
  }

  @Test
  public void alternatesAreDeduplicatedFromOtherUnion() {
    UnionColor union =
        UnionColor.create(
            ImmutableSet.of(
                PrimitiveColor.NUMBER,
                UnionColor.create(ImmutableSet.of(PrimitiveColor.NUMBER, PrimitiveColor.STRING))));

    assertThat(union).hasAlternates(PrimitiveColor.NUMBER, PrimitiveColor.STRING);
  }

  @Test
  public void createMethodRequiresTwoOrMoreAlternates() {
    assertThrows(IllegalArgumentException.class, () -> UnionColor.create(ImmutableSet.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> UnionColor.create(ImmutableSet.of(PrimitiveColor.NUMBER)));
  }

  @Test
  public void unknownTypeIsNotSpecialCased() {
    // In some "check" type systems, the top type + another type is equivalent to just the
    // top type. Colors don't replicate those semantics. Whatever serialized type structure the
    // colors are created from has already dealt with this.
    UnionColor union =
        UnionColor.create(ImmutableSet.of(PrimitiveColor.UNKNOWN, PrimitiveColor.STRING));

    assertThat(union).isUnion();
    assertThat(union.getAlternates()).hasSize(2);
  }
}
