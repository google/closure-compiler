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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;

/** A set of multiple {@link Color}s. */
@AutoValue
@Immutable
public abstract class UnionColor implements Color {

  public static UnionColor create(ImmutableSet<Color> alternates) {
    checkArgument(
        alternates.size() > 1,
        "UnionColor alternates should have more than one element, found %s",
        alternates);
    // Flatten other UnionColors
    ImmutableSet.Builder<Color> flatAlternates = ImmutableSet.builder();
    for (Color alternate : alternates) {
      if (alternate.isUnion()) {
        flatAlternates.addAll(alternate.getAlternates());
      } else {
        flatAlternates.add(alternate);
      }
    }
    return new AutoValue_UnionColor(flatAlternates.build());
  }

  @Override
  public boolean isPrimitive() {
    return false;
  }

  @Override
  public boolean isUnion() {
    return true;
  }

  @Override
  public boolean isObject() {
    return false;
  }

  @Override
  public abstract ImmutableCollection<Color> getAlternates();

  @Override
  public boolean isInvalidating() {
    // Union colors are invalidating if any alternates are invalidating, with the exception of
    // null/undefined.
    for (Color alt : getAlternates()) {
      if (alt.equals(PrimitiveColor.NULL_OR_VOID)) {
        continue;
      }
      if (alt.isInvalidating()) {
        return true;
      }
    }
    return false;
  }

  public Color removeNullAndUndefined() {
    ImmutableSet<Color> nonNullElements =
        this.getAlternates().stream()
            .filter(color -> !PrimitiveColor.NULL_OR_VOID.equals(color))
            .collect(toImmutableSet());
    return nonNullElements.size() > 1
        ? UnionColor.create(nonNullElements)
        : nonNullElements.iterator().next();
  }
}
