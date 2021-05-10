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
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;

/** Memoizes all native color instances */
public final class ColorRegistry {
  ImmutableMap<ColorId, Color> nativeColors;

  private ColorRegistry(ImmutableMap<ColorId, Color> nativeColors) {
    checkState(nativeColors.keySet().equals(REQUIRED_IDS));
    this.nativeColors = nativeColors;
  }

  public static ColorRegistry create(Map<ColorId, Color> nativeColors) {
    return new ColorRegistry(ImmutableMap.copyOf(nativeColors));
  }

  /**
   * Creates a ColorRegistry containing default implementations for all {@link ColorId}s.
   *
   * <p>Only for use in testing. In real compilations, certain native colors have fields that vary
   * from compilation-to-compilation (like whether the "Number" object is invalidating), so should
   * use {@link #create}} instead.
   */
  public static ColorRegistry createForTesting() {
    return new ColorRegistry(
        REQUIRED_IDS.stream()
            .map(ColorRegistry::createDefaultNativeColor)
            .collect(toImmutableMap(Color::getId, (x) -> x)));
  }

  public final Color get(ColorId id) {
    checkNotNull(id);
    return checkNotNull(this.nativeColors.get(id), "Missing Color for %s", id);
  }

  private static Color createDefaultNativeColor(ColorId id) {
    return Color.singleBuilder().setId(id).build();
  }

  private static final ImmutableSet<ColorId> REQUIRED_IDS = StandardColors.PRIMITIVE_BOX_IDS;
}
