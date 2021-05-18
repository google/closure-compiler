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

package com.google.javascript.jscomp.serialization;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.javascript.jscomp.serialization.TypePointers.isAxiomatic;
import static com.google.javascript.jscomp.serialization.TypePointers.trimOffset;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.ColorId;
import com.google.javascript.jscomp.colors.ColorRegistry;
import com.google.javascript.jscomp.colors.DebugInfo;
import com.google.javascript.jscomp.colors.StandardColors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Convert a {@link TypePool} (from a single compilation) into {@link Color}s.
 *
 * <p>Future work will be necessary to let this class convert multiple type-pools coming from
 * different libraries. For now it only handles a single type-pool.
 */
public final class ColorDeserializer {
  private final ImmutableList<Color> colorPool;
  private final ColorRegistry colorRegistry;
  private final TypePool typePool;

  private ColorDeserializer(Builder builder) {
    this.colorPool = ImmutableList.copyOf(builder.colorPool);
    this.colorRegistry = builder.registry.build();
    this.typePool = builder.typePool;
  }

  public ColorRegistry getRegistry() {
    return this.colorRegistry;
  }

  /**
   * Builds a pool of Colors and a {@link ColorRegistry} from the given pool of types.
   *
   * <p>This method does all of the deserialization work in advance so that {@link #getRegistry()}
   * and {@link #pointerToColor(TypePointer)} willexecute in constant time.
   */
  public static ColorDeserializer buildFromTypePool(TypePool typePool, StringPool stringPool) {
    return new Builder(typePool, stringPool).build();
  }

  /**
   * Contains necessary logic and mutable state to create a pool of {@link Color}s from a {@link
   * TypePool}.
   */
  private static final class Builder {
    // the size of the color pool corersponds to the TypePool.getTypeList() size
    private final ArrayList<Color> colorPool; // filled in as we go. initially all null
    // to avoid infinite recursion on types in cycles
    private final Set<TypeProto> currentlyDeserializing = new LinkedHashSet<>();
    // keys are indices into the type proto list and values are pointers to its supertypes
    private final TypePool typePool;
    private final StringPool stringPool;
    private final ColorRegistry.Builder registry = ColorRegistry.builder();

    private Builder(TypePool typePool, StringPool stringPool) {
      this.typePool = typePool;
      this.stringPool = stringPool;
      this.colorPool = new ArrayList<>();
      this.colorPool.addAll(Collections.nCopies(typePool.getTypeCount(), null));
    }

    private ColorDeserializer build() {
      for (int i = 0; i < this.colorPool.size(); i++) {
        if (this.colorPool.get(i) == null) {
          this.deserializeTypeByOffset(i);
        }
      }

      this.gatherBoxColors();
      this.recordDisambiguationSupertypeGraph();
      return new ColorDeserializer(this);
    }

    /**
     * Given an index into the type pool, creating its corresponding color if not already
     * deserialized.
     *
     * <p>Note: this index must correspond to the actual type proto list, so slots 0-N are /not/
     * reserved for the native types.
     */
    private void deserializeTypeByOffset(int i) {
      Color color = deserializeType(typePool.getTypeList().get(i));

      colorPool.set(i, color);
    }

    /**
     * Safely deserializes a type after verifying it's not going to cause infinite recursion
     *
     * <p>Currently this always initializes a new {@link Color} and we assume there are no duplicate
     * types in the serialized type pool.
     */
    private Color deserializeType(TypeProto serialized) {
      if (currentlyDeserializing.contains(serialized)) {
        throw new MalformedTypedAstException("Cannot deserialize type in cycle " + serialized);
      }
      currentlyDeserializing.add(serialized);

      Color newColor = deserializeTypeAssumingSafe(serialized);

      currentlyDeserializing.remove(serialized);
      return newColor;
    }

    /** Creates a color from a TypeProto without checking for any type cycles */
    private Color deserializeTypeAssumingSafe(TypeProto serialized) {
      switch (serialized.getKindCase()) {
        case OBJECT:
          return createObjectColor(serialized.getObject());
        case UNION:
          return createUnionColor(serialized.getUnion());
        case KIND_NOT_SET:
          throw new MalformedTypedAstException(
              "Expected all Types to have a Kind, found " + serialized);
      }
      throw new AssertionError();
    }

    private Color createObjectColor(ObjectTypeProto serialized) {
      ObjectTypeProto.DebugInfo serializedDebugInfo = serialized.getDebugInfo();
      Color.Builder builder =
          Color.singleBuilder()
              .setId(ColorId.fromBytes(serialized.getUuid()))
              .setClosureAssert(serialized.getClosureAssert())
              .setInvalidating(serialized.getIsInvalidating())
              .setPropertiesKeepOriginalName(serialized.getPropertiesKeepOriginalName())
              .setDebugInfo(
                  DebugInfo.builder()
                      .setFilename(serializedDebugInfo.getFilename())
                      .setClassName(serializedDebugInfo.getClassName())
                      .build())
              .setConstructor(serialized.getMarkedConstructor())
              .setOwnProperties(
                  serialized.getOwnPropertyList().stream()
                      .map(this.stringPool::get)
                      .collect(toImmutableSet()));
      if (serialized.hasPrototype()) {
        builder.setPrototype(this.pointerToColor(serialized.getPrototype()));
      }
      if (serialized.hasInstanceType()) {
        builder.setInstanceColor(this.pointerToColor(serialized.getInstanceType()));
      }
      return builder.build();
    }

    private Color createUnionColor(UnionTypeProto serialized) {
      if (serialized.getUnionMemberCount() <= 1) {
        throw new MalformedTypedAstException("Unions must have >= 2 elements, found " + serialized);
      }
      ImmutableSet<Color> allAlternates =
          serialized.getUnionMemberList().stream()
              .map(this::pointerToColor)
              .collect(toImmutableSet());
      if (allAlternates.size() == 1) {
        return Iterables.getOnlyElement(allAlternates);
      } else {
        return Color.createUnion(allAlternates);
      }
    }

    private Color pointerToColor(TypePointer typePointer) {
      validatePointer(typePointer, this.typePool);
      int poolOffset = typePointer.getPoolOffset();
      if (isAxiomatic(poolOffset)) {
        return TypePointers.OFFSET_TO_AXIOMATIC_COLOR.get(poolOffset);
      }
      int trimmedOffset = trimOffset(poolOffset);
      if (this.colorPool.get(trimmedOffset) == null) {
        this.deserializeTypeByOffset(trimmedOffset);
      }
      return this.colorPool.get(trimmedOffset);
    }

    private void gatherBoxColors() {
      for (ColorId id : StandardColors.PRIMITIVE_BOX_IDS) {
        this.registry.setNativeColor(Color.singleBuilder().setId(id).build());
      }
      for (Color c : this.colorPool) {
        ColorId id = c.getId();
        if (StandardColors.PRIMITIVE_BOX_IDS.contains(id)) {
          this.registry.setNativeColor(c);
        }
      }
    }

    private void recordDisambiguationSupertypeGraph() {
      for (SubtypingEdge edge : this.typePool.getDisambiguationEdgesList()) {
        this.registry.addDisambiguationEdge(
            this.pointerToColor(edge.getSubtype()), this.pointerToColor(edge.getSupertype()));
      }
    }
  }

  public Color pointerToColor(TypePointer typePointer) {
    validatePointer(typePointer, this.typePool);
    int poolOffset = typePointer.getPoolOffset();
    if (isAxiomatic(poolOffset)) {
      return TypePointers.OFFSET_TO_AXIOMATIC_COLOR.get(poolOffset);
    }
    return this.colorPool.get(trimOffset(poolOffset));
  }

  /** Validates that the given typePointer is valid according to the given pool of types */
  private static void validatePointer(TypePointer typePointer, TypePool typePool) {
    int poolOffset = typePointer.getPoolOffset();
    // Account for the first N type pointer offsets being reserved for the primitive types.
    if (poolOffset < 0
        || poolOffset >= typePool.getTypeCount() + TypePointers.AXIOMATIC_COLOR_COUNT) {
      throw new MalformedTypedAstException(
          "TypeProto pointer has out-of-bounds pool offset: "
              + typePointer
              + " for pool size "
              + typePool.getTypeCount());
    }
  }
}
