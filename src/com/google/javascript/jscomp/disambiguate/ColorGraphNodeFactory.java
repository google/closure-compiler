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


import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.ColorRegistry;
import com.google.javascript.jscomp.colors.NativeColorId;
import java.util.LinkedHashMap;
import javax.annotation.Nullable;

/** A factory and cache for {@link ColorGraphNode} instances. */
final class ColorGraphNodeFactory {

  private final LinkedHashMap<Color, ColorGraphNode> typeIndex;
  private final Color unknownColor;

  private ColorGraphNodeFactory(
      LinkedHashMap<Color, ColorGraphNode> initialTypeIndex, Color unknownColor) {
    this.typeIndex = initialTypeIndex;
    this.unknownColor = unknownColor;
  }

  static ColorGraphNodeFactory createFactory(ColorRegistry colorRegistry) {
    LinkedHashMap<Color, ColorGraphNode> typeIndex = new LinkedHashMap<>();
    Color unknownColor = colorRegistry.get(NativeColorId.UNKNOWN);
    ColorGraphNode unknownColorNode = ColorGraphNode.create(unknownColor, 0);
    typeIndex.put(unknownColor, unknownColorNode);
    return new ColorGraphNodeFactory(typeIndex, unknownColor);
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
      return this.unknownColor;
    }

    if (type.isPrimitive()) {
      return this.unknownColor;
    } else if (type.isUnion()) {
      return type.subtractNullOrVoid();
    } else {
      return type;
    }
  }
}
