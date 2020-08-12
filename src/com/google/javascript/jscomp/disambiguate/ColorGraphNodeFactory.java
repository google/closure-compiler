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
import com.google.javascript.jscomp.colors.PrimitiveColor;
import com.google.javascript.jscomp.colors.UnionColor;
import java.util.LinkedHashMap;
import javax.annotation.Nullable;

/** A factory and cache for {@link ColorGraphNode} instances. */
final class ColorGraphNodeFactory {

  private final LinkedHashMap<Color, ColorGraphNode> typeIndex;

  private ColorGraphNodeFactory(LinkedHashMap<Color, ColorGraphNode> initialTypeIndex) {
    this.typeIndex = initialTypeIndex;
  }

  static ColorGraphNodeFactory createFactory() {
    LinkedHashMap<Color, ColorGraphNode> typeIndex = new LinkedHashMap<>();
    ColorGraphNode unknownColorNode = ColorGraphNode.create(PrimitiveColor.UNKNOWN, 0);
    typeIndex.put(PrimitiveColor.UNKNOWN, unknownColorNode);
    return new ColorGraphNodeFactory(typeIndex);
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
      return PrimitiveColor.UNKNOWN;
    }

    if (type.isPrimitive()) {
      return PrimitiveColor.UNKNOWN;
    } else if (type.isUnion()) {
      return simplifyUnion((UnionColor) type);
    } else {
      return type;
    }
  }

  /**
   * Removes NULL_OR_VOID then recursively simplifies the union members. may create a new
   * UnionColor.
   */
  private Color simplifyUnion(UnionColor union) {
    // Remove NULL_OR_VOID then simplify components.
    ImmutableSet<Color> colors =
        union.getAlternates().stream()
            .filter(c -> !c.equals(PrimitiveColor.NULL_OR_VOID))
            .map(this::simplifyColor)
            .collect(toImmutableSet());
    switch (colors.size()) {
      case 0:
        throw new AssertionError();
      case 1:
        return colors.iterator().next();
      default:
        return UnionColor.create(colors);
    }
  }
}
