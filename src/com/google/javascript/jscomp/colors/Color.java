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

import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nullable;

/** A simplified version of a Closure or TS type for use by optimizations */
@AutoValue
public abstract class Color {

  public abstract ColorId getId();

  public abstract DebugInfo getDebugInfo();

  /** Given `function Foo() {}` or `class Foo {}`, color of Foo.prototype. null otherwise. */
  public abstract ImmutableSet<Color> getPrototypes();

  public abstract ImmutableSet<Color> getInstanceColors();

  public abstract boolean isInvalidating();

  public abstract boolean getPropertiesKeepOriginalName();

  public abstract boolean isConstructor();

  /**
   * Property names 'declared' on an object (as opposed to being conceptually inherited from some
   * supertype).
   */
  public abstract ImmutableSet<String> getOwnProperties();

  @Nullable
  public abstract ColorId getBoxId();

  /**
   * Whether this type is some Closure assertion function removable by Closure-specific
   * optimizations.
   */
  public abstract boolean isClosureAssert();

  public abstract ImmutableSet<Color> getUnionElements();

  public static Builder singleBuilder() {
    return new AutoValue_Color.Builder()
        .setClosureAssert(false)
        .setConstructor(false)
        .setDebugInfo(DebugInfo.EMPTY)
        .setInstanceColors(ImmutableSet.of())
        .setInvalidating(false)
        .setOwnProperties(ImmutableSet.of())
        .setPropertiesKeepOriginalName(false)
        .setPrototypes(ImmutableSet.of())
        .setUnionElements(ImmutableSet.of());
  }

  public static Color createUnion(Set<Color> elements) {
    switch (elements.size()) {
      case 0:
        throw new IllegalStateException();
      case 1:
        return Iterables.getOnlyElement(elements);
      default:
        break;
    }

    TreeSet<String> debugTypenames = new TreeSet<>();
    ImmutableSet.Builder<Color> instanceColors = ImmutableSet.builder();
    ImmutableSet.Builder<Color> prototypes = ImmutableSet.builder();
    ImmutableSet.Builder<Color> newElements = ImmutableSet.builder();
    ImmutableSet.Builder<ColorId> ids = ImmutableSet.builder();
    ImmutableSet.Builder<String> ownProperties = ImmutableSet.builder();
    boolean isClosureAssert = true;
    boolean isConstructor = true;
    boolean isInvalidating = false;
    boolean propertiesKeepOriginalName = false;

    for (Color element : elements) {
      if (element.isUnion()) {
        for (Color nestedElement : element.getUnionElements()) {
          newElements.add(nestedElement);
          ids.add(nestedElement.getId());
          debugTypenames.add(nestedElement.getDebugInfo().getCompositeTypename());
        }
      } else {
        newElements.add(element);
        ids.add(element.getId());
        debugTypenames.add(element.getDebugInfo().getCompositeTypename());
      }

      instanceColors.addAll(element.getInstanceColors());
      isClosureAssert &= element.isClosureAssert();
      isConstructor &= element.isConstructor();
      isInvalidating |= element.isInvalidating();
      ownProperties.addAll(element.getOwnProperties()); // Are these actually the "own props"?
      propertiesKeepOriginalName |= element.getPropertiesKeepOriginalName();
      prototypes.addAll(element.getPrototypes());
    }

    debugTypenames.remove("");
    DebugInfo debugInfo =
        debugTypenames.isEmpty()
            ? DebugInfo.EMPTY
            : DebugInfo.builder()
                .setCompositeTypename("(" + String.join("|", debugTypenames) + ")")
                .build();

    return new AutoValue_Color.Builder()
        .setClosureAssert(isClosureAssert)
        .setConstructor(isConstructor)
        .setDebugInfo(debugInfo)
        .setId(ColorId.union(ids.build()))
        .setInstanceColors(instanceColors.build())
        .setInvalidating(isInvalidating)
        .setOwnProperties(ownProperties.build())
        .setPropertiesKeepOriginalName(propertiesKeepOriginalName)
        .setPrototypes(prototypes.build())
        .setUnionElements(newElements.build())
        .buildUnion();
  }

  Color() {
    // No public constructor.
  }

  /**
   * Whether this corresponds to a single JavaScript primitive like number or symbol.
   *
   * <p>Note that the boxed versions of primitives (String, Number, etc.) are /not/ considered
   * "primitive" by this method.
   */
  public final boolean isPrimitive() {
    /**
     * Avoid the design headache about whether unions are primitives. The union *color* isn't
     * primitive, but the *value* held by a union reference may be.
     */
    checkState(!this.isUnion(), this);
    return StandardColors.PRIMITIVE_COLORS.containsKey(this.getId());
  }

  public final boolean isUnion() {
    // Single element sets are banned in the builder.
    return !this.getUnionElements().isEmpty();
  }

  @Memoized
  public Color subtractNullOrVoid() {
    /** Avoid defining NULL_OR_VOID.subtract(NULL_OR_VOID) */
    checkState(this.isUnion());

    if (!this.getUnionElements().contains(StandardColors.NULL_OR_VOID)) {
      return this;
    }

    LinkedHashSet<Color> elements = new LinkedHashSet<>(this.getUnionElements());
    elements.remove(StandardColors.NULL_OR_VOID);
    return createUnion(elements);
  }

  /**
   * Builder for a singleton color. Should be passed to {@link
   * Color#createSingleton(SingletonColorFields)} after building and before using
   */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setId(ColorId x);

    public abstract Builder setInvalidating(boolean x);

    public abstract Builder setPropertiesKeepOriginalName(boolean x);

    public abstract Builder setConstructor(boolean x);

    public abstract Builder setOwnProperties(ImmutableSet<String> x);

    public abstract Builder setDebugInfo(DebugInfo x);

    public abstract Builder setClosureAssert(boolean x);

    public abstract Builder setInstanceColors(ImmutableSet<Color> x);

    public abstract Builder setPrototypes(ImmutableSet<Color> x);

    abstract Builder setUnionElements(ImmutableSet<Color> x);

    abstract Builder setBoxId(@Nullable ColorId x);

    public Builder setPrototype(Color x) {
      return this.setPrototypes((x == null) ? ImmutableSet.of() : ImmutableSet.of(x));
    }

    public Builder setInstanceColor(Color x) {
      return this.setInstanceColors((x == null) ? ImmutableSet.of() : ImmutableSet.of(x));
    }

    abstract Color buildInternal();

    public final Color build() {
      Color result = this.buildInternal();
      checkState(result.getUnionElements().isEmpty(), result);
      checkState(result.getBoxId() == null, result);
      checkState(!StandardColors.AXIOMATIC_COLORS.containsKey(result.getId()), result);
      return result;
    }

    private final Color buildUnion() {
      Color result = this.buildInternal();
      checkState(result.getUnionElements().size() > 1, result);
      checkState(result.getBoxId() == null, result);
      return result;
    }

    final Color buildAxiomatic() {
      checkState(StandardColors.AXIOMATIC_COLORS == null, "StandardColors are all defined");

      Color result = this.buildInternal();
      checkState(result.getUnionElements().isEmpty(), result);
      checkState(!result.getDebugInfo().getCompositeTypename().isEmpty(), result);
      return result;
    }
  }
}
