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

import com.google.common.collect.ImmutableMap;
import java.util.LinkedHashMap;

/** Memoizes all native color instances */
public final class ColorRegistry {
  ImmutableMap<NativeColorId, Color> nativeColors;

  private ColorRegistry(ImmutableMap<NativeColorId, Color> nativeColors) {
    this.nativeColors = nativeColors;
  }

  public static Builder builder() {
    // Prepopulate the builder with the typesystem primitive native colors.
    ImmutableMap.Builder<NativeColorId, Color> nativeColors = ImmutableMap.builder();
    for (NativeColorId id : NativeColorId.values()) {
      if (!id.isTypesystemPrimitive()) {
        continue;
      }
      nativeColors.put(id, createDefaultNativeColor(id));
    }

    return new Builder(nativeColors.build());
  }

  /**
   * Creates a ColorRegistry containing default implementations for all {@link NativeColorId}s.
   *
   * <p>Only for use in testing. In real compilations, certain native colors have fields that vary
   * from compilation-to-compilation (like whether the "Number" object is invalidating), so should
   * use the {@link #builder()}} instead.
   */
  public static ColorRegistry createForTesting() {
    return builder().withDefaultNativeObjectColors().build();
  }

  private static Color createDefaultNativeColor(NativeColorId nativeColorId) {
    SingletonColorFields fields =
        SingletonColorFields.builder()
            .setNativeColorId(nativeColorId)
            .setId("__nativeColor__" + nativeColorId.name())
            .setDebugInfo(DebugInfo.builder().setClassName(nativeColorId.name()).build())
            .setInvalidating(nativeColorId.alwaysInvalidating())
            .build();
    return Color.createSingleton(fields);
  }

  public final Color get(NativeColorId nativeColorId) {
    checkNotNull(nativeColorId);
    return checkNotNull(
        this.nativeColors.get(nativeColorId), "Missing Color for %s", nativeColorId);
  }

  /** Records the state for a partially-built ColorRegistry. Create with {@link #builder()} */
  public static final class Builder {
    private final ImmutableMap<NativeColorId, Color> nativeColors;
    private final LinkedHashMap<NativeColorId, Color> nativeObjectColors = new LinkedHashMap<>();

    // Prevent instantiation outside this class.
    private Builder(ImmutableMap<NativeColorId, Color> nativeColors) {
      this.nativeColors = nativeColors;
    }

    /**
     * Instantiate the registry with custom implementations of the given colors
     *
     * <p>May only be called on colors for which {@link NativeColorId::isTypesystemPrimitive} is
     * false.
     */
    public Builder withNativeObjectColors(ImmutableMap<NativeColorId, Color> nativeObjectColors) {
      for (NativeColorId id : nativeObjectColors.keySet()) {
        checkArgument(
            !id.isTypesystemPrimitive(),
            "Cannot configure non-default implemention for typesystem primitive %s",
            id);
      }
      this.nativeObjectColors.putAll(nativeObjectColors);
      return this;
    }

    /** Instantiates the registry with default implementations for all {@link NativeColorId}s. */
    private Builder withDefaultNativeObjectColors() {
      for (NativeColorId id : NativeColorId.values()) {
        if (id.isTypesystemPrimitive()) {
          continue;
        }
        this.nativeObjectColors.put(id, createDefaultNativeColor(id));
      }
      return this;
    }

    /**
     * Returns a copy of all the native type-system primitive colors.
     *
     * <p>Allows code to request default implementations of all the primitive colors, then use those
     * default implementations to build more complex, non-default implementations of native objects
     * like boxed scalars Number/String/etc.)
     */
    public ImmutableMap<NativeColorId, Color> getNativePrimitives() {
      return this.nativeColors;
    }

    public ColorRegistry build() {
      return new ColorRegistry(
          ImmutableMap.<NativeColorId, Color>builder()
              .putAll(this.nativeColors)
              .putAll(this.nativeObjectColors)
              .build());
    }
  }
}
