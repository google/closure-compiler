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

package com.google.javascript.jscomp.disambiguate;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.ColorRegistry;
import com.google.javascript.jscomp.colors.StandardColors;
import java.util.LinkedHashMap;
import org.jspecify.nullness.Nullable;

/** A factory and cache for {@link ColorGraphNode} instances. */
class ColorGraphNodeFactory {

  private final LinkedHashMap<Color, ColorGraphNode> typeIndex;
  private final ColorRegistry registry;

  protected ColorGraphNodeFactory(
      LinkedHashMap<Color, ColorGraphNode> initialTypeIndex, ColorRegistry registry) {
    this.typeIndex = initialTypeIndex;
    this.registry = registry;
  }

  static ColorGraphNodeFactory createFactory(ColorRegistry colorRegistry) {
    LinkedHashMap<Color, ColorGraphNode> typeIndex = new LinkedHashMap<>();
    ColorGraphNode unknownColorNode = ColorGraphNode.create(StandardColors.UNKNOWN, 0);
    typeIndex.put(StandardColors.UNKNOWN, unknownColorNode);
    return new ColorGraphNodeFactory(typeIndex, colorRegistry);
  }

  /**
   * Returns the {@link ColorGraphNode} known by this factory for {@code type}.
   *
   * <p>For a given {@code type} and factory, this method will always return the same result. The
   * results are cached.
   */
  public ColorGraphNode createNode(@Nullable Color color) {
    Color key = this.simplifyColor(color);
    return this.typeIndex.computeIfAbsent(key, this::newColorGraphNode);
  }

  public ImmutableSet<ColorGraphNode> getAllKnownTypes() {
    return ImmutableSet.copyOf(this.typeIndex.values());
  }

  private ColorGraphNode newColorGraphNode(Color key) {
    int id = this.typeIndex.size();
    return ColorGraphNode.create(key, id);
  }

  // Merges different colors with the same ambiguation-behavior into one
  private Color simplifyColor(@Nullable Color type) {
    if (type == null) {
      return StandardColors.UNKNOWN;
    }

    if (type.isUnion()) {
      // First remove null/void, then recursively simplify any primitive components
      type = type.subtractNullOrVoid();
      return type.isUnion()
          ? Color.createUnion(
              type.getUnionElements().stream().map(this::simplifyColor).collect(toImmutableSet()))
          : simplifyColor(type);
    } else if (type.getBoxId() != null) {
      return this.registry.get(type.getBoxId());
    } else if (type.equals(StandardColors.NULL_OR_VOID)) {
      return StandardColors.UNKNOWN;
    } else {
      return type;
    }
  }
}
