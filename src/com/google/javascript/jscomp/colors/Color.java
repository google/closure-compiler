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
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.auto.value.AutoOneOf;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.Immutable;
import java.util.List;
import java.util.function.Function;

/** A simplified version of a Closure or TS type for use by optimizations */
@AutoOneOf(Color.Kind.class)
@Immutable
public abstract class Color {

  // Colors are implemented so that internally, they are either a singleton or a set of other colors
  // In practice this is partially opaque to callers. Callers can access the elements of a color
  // that is a union, but otherwise the API does not distinguish between singletons and unions.
  enum Kind {
    SINGLETON,
    UNION
  }

  abstract Kind kind();

  abstract SingletonColorFields singleton();

  public abstract ImmutableCollection<Color> union();

  /**
   * Whether this corresponds to a single JavaScript primitive like number or symbol or to a union
   * of such primitives
   *
   * <p>Note that the boxed versions of primitives (String, Number, etc.) are /not/ considered
   * "primitive" by this method.
   */
  public final boolean isPrimitive() {
    switch (kind()) {
      case SINGLETON:
        return singleton().getNativeColorId() != null
            && singleton().getNativeColorId().isPrimitive();
      case UNION:
        return union().stream().allMatch(Color::isPrimitive);
    }
    throw new AssertionError();
  }

  public final boolean isUnion() {
    return kind().equals(Kind.UNION);
  }

  public final boolean isInvalidating() {
    switch (kind()) {
      case SINGLETON:
        return singleton().isInvalidating();
      case UNION:
        return union().stream().anyMatch(Color::isInvalidating);
    }
    throw new AssertionError();
  }

  public final boolean propertiesKeepOriginalName() {
    switch (kind()) {
      case SINGLETON:
        return singleton().getPropertiesKeepOriginalName();
      case UNION:
        return union().stream().anyMatch(Color::propertiesKeepOriginalName);
    }
    throw new AssertionError();
  }

  /** Whether this is exactly the given native color (and not a union containing that color */
  public final boolean is(NativeColorId color) {
    switch (kind()) {
      case SINGLETON:
        return color.equals(this.singleton().getNativeColorId());
      case UNION:
        return false;
    }
    throw new AssertionError();
  }

  /**
   * Whether this is a constructor (a type that TypeScript or Closure allows calling with 'new'
   *
   * <p>For unions, returns true if and only if all alternates in the union are constructors
   */
  public final boolean isConstructor() {
    switch (kind()) {
      case SINGLETON:
        return this.singleton().isConstructor();
      case UNION:
        return this.union().stream().allMatch(Color::isConstructor);
    }
    throw new AssertionError();
  }

  public final ImmutableSet<String> getId() {
    return collect(this, SingletonColorFields::getId);
  }

  public final ImmutableSet<DebugInfo> getDebugInfo() {
    return collect(this, SingletonColorFields::getDebugInfo);
  }

  // given `function Foo() {}` or `class Foo {}`, color of Foo.prototype. null otherwise.
  public final ImmutableSet<Color> getPrototype() {
    return collect(this, SingletonColorFields::getPrototype);
  }

  public final ImmutableSet<Color> getInstanceColor() {
    return collect(this, SingletonColorFields::getInstanceColor);
  }

  // List of other colors directly above this in the subtyping graph for the purposes of property
  // (dis)ambiguation.
  public final ImmutableList<Color> getDisambiguationSupertypes() {
    return collect(this, SingletonColorFields::getDisambiguationSupertypes).stream()
        .flatMap(List::stream)
        .collect(toImmutableList());
  }

  public final ImmutableSet<NativeColorId> getNativeColorIds() {
    return collect(this, SingletonColorFields::getNativeColorId);
  }

  // Abstracts the operation of collecting all fields into a single immutable set.
  private static <T> ImmutableSet<T> collect(
      Color color, Function<SingletonColorFields, T> accessor) {
    switch (color.kind()) {
      case SINGLETON:
        T singletonField = accessor.apply(color.singleton());
        return singletonField != null ? ImmutableSet.of(singletonField) : ImmutableSet.of();
      case UNION:
        ImmutableSet.Builder<T> allFields = ImmutableSet.builder();
        for (Color alt : color.union()) {
          T alternateField = accessor.apply(alt.singleton());
          if (alternateField != null) {
            allFields.add(alternateField);
          }
        }
        return allFields.build();
    }
    throw new AssertionError();
  }

  public static Color createSingleton(SingletonColorFields fields) {
    return AutoOneOf_Color.singleton(checkNotNull(fields));
  }

  public static Color createUnion(ImmutableSet<Color> alternates) {
    checkArgument(
        !alternates.isEmpty(), "Cannot create a union of zero elements, found %s", alternates);
    if (alternates.size() == 1) {
      return Iterables.getOnlyElement(alternates);
    }
    // Flatten nested unions
    ImmutableSet.Builder<Color> flatAlternates = ImmutableSet.builder();
    for (Color alternate : alternates) {
      switch (alternate.kind()) {
        case SINGLETON:
          flatAlternates.add(alternate);
          continue;
        case UNION:
          flatAlternates.addAll(alternate.union());
          continue;
      }
      throw new AssertionError();
    }
    return AutoOneOf_Color.union(flatAlternates.build());
  }

  public final Color subtractNullOrVoid() {
    // Forbid calling this on non-unions to avoid defining what NULL_OR_VOID.subtract(NULL_OR_VOID)
    // is.
    checkState(this.isUnion(), "Cannot remove null_or_void from non-unions");

    ImmutableSet<Color> alternates =
        union().stream()
            .filter(alt -> !alt.is(NativeColorId.NULL_OR_VOID))
            .collect(toImmutableSet());
    switch (alternates.size()) {
      case 0:
        throw new AssertionError(); // can never happen unless there are multiple NULL_OR_VOIDs
      case 1:
        return alternates.iterator().next();
      default:
        return Color.createUnion(alternates);
    }
  }
}
