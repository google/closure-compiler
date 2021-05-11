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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.javascript.jscomp.serialization.TypePointers.trimOffset;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.ColorId;
import com.google.javascript.jscomp.colors.ColorRegistry;
import com.google.javascript.jscomp.colors.DebugInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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

  private ColorDeserializer(
      ImmutableList<Color> colorPool, ColorRegistry colorRegistry, TypePool typePool) {
    this.colorPool = colorPool;
    this.colorRegistry = colorRegistry;
    this.typePool = typePool;
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
    ImmutableMultimap.Builder<Integer, TypePointer> disambiguationEdges =
        ImmutableMultimap.builder();
    for (SubtypingEdge edge : typePool.getDisambiguationEdgesList()) {
      TypePointer subtype = edge.getSubtype();
      TypePointer supertype = edge.getSupertype();
      validatePointer(subtype, typePool);
      validatePointer(supertype, typePool);
      // Make the offset correspond to the actual list of type protos and exclude native types, as
      // native types are hardcoded in the ColorRegistry.
      disambiguationEdges.put(trimOffset(subtype), supertype);
    }

    ImmutableList<Color> colorPool =
        new ColorPoolBuilder(typePool, stringPool, disambiguationEdges.build()).build();

    return new ColorDeserializer(
        colorPool, ColorRegistry.create(gatherBoxColors(colorPool)), typePool);
  }

  private static LinkedHashMap<ColorId, Color> gatherBoxColors(ImmutableList<Color> colorPool) {
    ImmutableSet<ColorId> boxIds = ImmutableSet.copyOf(JSTypeColorIdHasher.BOX_TYPE_TO_ID.values());

    LinkedHashMap<ColorId, Color> boxIdToColor = new LinkedHashMap<>();
    for (Color c : colorPool) {
      ColorId id = c.getId();
      if (boxIds.contains(id)) {
        checkState(!boxIdToColor.containsKey(id), id);
        boxIdToColor.put(id, c);
      }
    }
    for (ColorId id : boxIds) {
      boxIdToColor.computeIfAbsent(id, (unused) -> Color.singleBuilder().setId(id).build());
    }

    return boxIdToColor;
  }

  /**
   * Contains necessary logic and mutable state to create a pool of {@link Color}s from a {@link
   * TypePool}.
   */
  private static final class ColorPoolBuilder {
    // the size of the color pool corersponds to the TypePool.getTypeList() size
    private final ArrayList<Color> colorPool; // filled in as we go. initially all null
    // to avoid infinite recursion on types in cycles
    private final Set<TypeProto> currentlyDeserializing = new LinkedHashSet<>();
    // keys are indices into the type proto list and values are pointers to its supertypes
    private final ImmutableMultimap<Integer, TypePointer> disambiguationEdges;
    private final TypePool typePool;
    private final StringPool stringPool;

    private ColorPoolBuilder(
        TypePool typePool,
        StringPool stringPool,
        ImmutableMultimap<Integer, TypePointer> disambiguationEdges) {
      this.typePool = typePool;
      this.stringPool = stringPool;
      this.colorPool = new ArrayList<>();
      this.disambiguationEdges = disambiguationEdges;
      this.colorPool.addAll(Collections.nCopies(typePool.getTypeCount(), null));
    }

    private ImmutableList<Color> build() {
      for (int i = 0; i < this.colorPool.size(); i++) {
        if (this.colorPool.get(i) == null) {
          this.deserializeTypeByOffset(i);
        }
      }

      return ImmutableList.copyOf(this.colorPool);
    }

    /**
     * Given an index into the type pool, creating its corresponding color if not already
     * deserialized.
     *
     * <p>Note: this index must correspond to the actual type proto list, so slots 0-N are /not/
     * reserved for the native types.
     */
    private void deserializeTypeByOffset(int i) {
      Color color = deserializeType(i, typePool.getTypeList().get(i));

      colorPool.set(i, color);
    }

    /**
     * Safely deserializes a type after verifying it's not going to cause infinite recursion
     *
     * <p>Currently this always initializes a new {@link Color} and we assume there are no duplicate
     * types in the serialized type pool.
     */
    private Color deserializeType(int i, TypeProto serialized) {
      if (currentlyDeserializing.contains(serialized)) {
        throw new MalformedTypedAstException("Cannot deserialize type in cycle " + serialized);
      }
      currentlyDeserializing.add(serialized);

      Color newColor = deserializeTypeAssumingSafe(i, serialized);

      currentlyDeserializing.remove(serialized);
      return newColor;
    }

    /** Creates a color from a TypeProto without checking for any type cycles */
    private Color deserializeTypeAssumingSafe(int offset, TypeProto serialized) {
      switch (serialized.getKindCase()) {
        case OBJECT:
          return createObjectColor(offset, serialized.getObject());
        case UNION:
          return createUnionColor(serialized.getUnion());
        case KIND_NOT_SET:
          throw new MalformedTypedAstException(
              "Expected all Types to have a Kind, found " + serialized);
      }
      throw new AssertionError();
    }

    private Color createObjectColor(int offset, ObjectTypeProto serialized) {
      ImmutableSet<Color> directSupertypes =
          this.disambiguationEdges.get(offset).stream()
              .map(this::pointerToColor)
              .collect(toImmutableSet());
      ObjectTypeProto.DebugInfo serializedDebugInfo = serialized.getDebugInfo();
      Color.Builder builder =
          Color.singleBuilder()
              .setId(ColorId.fromBytes(serialized.getUuid()))
              .setClosureAssert(serialized.getClosureAssert())
              .setInvalidating(serialized.getIsInvalidating())
              .setPropertiesKeepOriginalName(serialized.getPropertiesKeepOriginalName())
              .setDisambiguationSupertypes(directSupertypes)
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
      if (poolOffset < TypePointers.AXIOMATIC_COLOR_COUNT) {
        return TypePointers.OFFSET_TO_AXIOMATIC_COLOR.get(poolOffset);
      }
      int trimmedOffset = trimOffset(poolOffset);
      if (this.colorPool.get(trimmedOffset) == null) {
        this.deserializeTypeByOffset(trimmedOffset);
      }
      return this.colorPool.get(trimmedOffset);
    }
  }

  public Color pointerToColor(TypePointer typePointer) {
    validatePointer(typePointer, this.typePool);
    int poolOffset = typePointer.getPoolOffset();
    if (poolOffset < JSTypeSerializer.PRIMITIVE_POOL_SIZE) {
      return TypePointers.OFFSET_TO_AXIOMATIC_COLOR.get(poolOffset);
    }
    return this.colorPool.get(trimOffset(poolOffset));
  }

  /** Validates that the given typePointer is valid according to the given pool of types */
  private static void validatePointer(TypePointer typePointer, TypePool typePool) {
    int poolOffset = typePointer.getPoolOffset();
    // Account for the first N type pointer offsets being reserved for the primitive types.
    if (poolOffset < 0
        || poolOffset >= typePool.getTypeCount() + JSTypeSerializer.PRIMITIVE_POOL_SIZE) {
      throw new MalformedTypedAstException(
          "TypeProto pointer has out-of-bounds pool offset: "
              + typePointer
              + " for pool size "
              + typePool.getTypeCount());
    }
  }
}
