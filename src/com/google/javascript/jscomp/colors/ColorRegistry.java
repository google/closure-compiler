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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import java.util.LinkedHashMap;

/**
 * Stores "out of band" information about the set of Colors in use by a compilation.
 *
 * <p>This includes any information that isn't specifically can't be set on a single Color object,
 */
public final class ColorRegistry {
  private final ImmutableMap<ColorId, Color> nativeColors;
  private final ImmutableSetMultimap<Color, Color> colorToDisambiguationSupertypeGraph;
  private final ImmutableSetMultimap<ColorId, String> mismatchLocations;

  private ColorRegistry(Builder builder) {
    this.nativeColors = ImmutableMap.copyOf(builder.nativeColors);
    this.colorToDisambiguationSupertypeGraph =
        ImmutableSetMultimap.copyOf(builder.colorToDisambiguationSupertypeGraph);
    this.mismatchLocations = ImmutableSetMultimap.copyOf(builder.mismatchLocations);

    checkState(this.nativeColors.keySet().equals(REQUIRED_IDS));
  }

  public final Color get(ColorId id) {
    checkNotNull(id);
    return checkNotNull(this.nativeColors.get(id), "Missing Color for %s", id);
  }

  /**
   * The colors directly above `x` in the subtyping graph for the purposes of property
   * (dis)ambiguation.
   */
  public final ImmutableSet<Color> getDisambiguationSupertypes(Color x) {
    return this.colorToDisambiguationSupertypeGraph.get(x);
  }

  /**
   * An index (mismatch sourceref string) => (involved color ids).
   *
   * <p>This index is only intended for debugging. It may be empty or incomplete during production
   * compilations.
   */
  public final ImmutableSetMultimap<ColorId, String> getMismatchLocationsForDebugging() {
    return this.mismatchLocations;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder */
  public static final class Builder {

    private final LinkedHashMap<ColorId, Color> nativeColors = new LinkedHashMap<>();
    private final SetMultimap<Color, Color> colorToDisambiguationSupertypeGraph =
        MultimapBuilder.linkedHashKeys().linkedHashSetValues().build();
    private final SetMultimap<ColorId, String> mismatchLocations =
        MultimapBuilder.linkedHashKeys().linkedHashSetValues().build();

    private Builder() {}

    public Builder setNativeColor(Color x) {
      checkState(REQUIRED_IDS.contains(x.getId()), x);
      this.nativeColors.put(x.getId(), x);
      return this;
    }

    public Builder addDisambiguationEdge(Color subtype, Color supertype) {
      this.colorToDisambiguationSupertypeGraph.put(subtype, supertype);
      return this;
    }

    public Builder addMismatchLocation(ColorId id, String location) {
      this.mismatchLocations.put(id, location);
      return this;
    }

    /**
     * Sets defaults for native Colors.
     *
     * <p>Only for use in testing. In real compilations, certain native colors have fields that vary
     * from compilation-to-compilation (like whether the "Number" object is invalidating), so should
     * use {@link #setNativeColor}} instead.
     */
    @VisibleForTesting
    public Builder setDefaultNativeColorsForTesting() {
      for (ColorId id : REQUIRED_IDS) {
        this.setNativeColor(Color.singleBuilder().setId(id).build());
      }
      return this;
    }

    public ColorRegistry build() {
      return new ColorRegistry(this);
    }
  }

  public static final ImmutableSet<ColorId> REQUIRED_IDS = StandardColors.STANDARD_OBJECT_IDS;
}
