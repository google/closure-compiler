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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Arrays.stream;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.ColorId;
import com.google.javascript.jscomp.colors.ColorRegistry;
import com.google.javascript.jscomp.colors.DebugInfo;
import com.google.javascript.jscomp.colors.NativeColorId;
import com.google.javascript.jscomp.colors.SingletonColorFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

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

  /** Error emitted when the deserializer sees a serialized type it cannot support deserialize */
  public static final class InvalidSerializedFormatException extends RuntimeException {
    public InvalidSerializedFormatException(String msg) {
      super("Invalid serialized TypeProto format: " + msg);
    }
  }

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
      disambiguationEdges.put(
          subtype.getPoolOffset() - JSTypeSerializer.PRIMITIVE_POOL_SIZE, supertype);
    }

    ColorRegistry.Builder colorRegistry = ColorRegistry.builder();
    ImmutableMap<NativeColorId, Color> colorIdToColor = colorRegistry.getNativePrimitives();

    ImmutableMap<PrimitiveType, Color> nativeTypeToColor =
        stream(PrimitiveType.values())
            .filter(primitive -> !primitive.equals(PrimitiveType.UNRECOGNIZED))
            .collect(
                toImmutableMap(
                    Function.identity(),
                    (nativeType) -> colorIdToColor.get(primitiveToColorId(nativeType))));

    ColorPoolBuilder colorPoolBuilder =
        new ColorPoolBuilder(typePool, stringPool, disambiguationEdges.build(), nativeTypeToColor);

    ImmutableMap<NativeColorId, Color> nativeObjectColors =
        gatherNativeObjects(typePool.getNativeObjectTable(), colorPoolBuilder);
    return new ColorDeserializer(
        colorPoolBuilder.build(),
        colorRegistry.withNativeObjectColors(nativeObjectColors).build(),
        typePool);
  }

  private static ImmutableMap<NativeColorId, Color> gatherNativeObjects(
      NativeObjectTable nativeObjectTable, ColorPoolBuilder colorPoolBuilder) {
    return ImmutableMap.<NativeColorId, Color>builder()
        .put(
            NativeColorId.BIGINT_OBJECT,
            colorPoolBuilder.pointerToColor(nativeObjectTable.getBigintObject()))
        .put(
            NativeColorId.BOOLEAN_OBJECT,
            colorPoolBuilder.pointerToColor(nativeObjectTable.getBooleanObject()))
        .put(
            NativeColorId.NUMBER_OBJECT,
            colorPoolBuilder.pointerToColor(nativeObjectTable.getNumberObject()))
        .put(
            NativeColorId.STRING_OBJECT,
            colorPoolBuilder.pointerToColor(nativeObjectTable.getStringObject()))
        .put(
            NativeColorId.SYMBOL_OBJECT,
            colorPoolBuilder.pointerToColor(nativeObjectTable.getSymbolObject()))
        .build();
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
    private final ImmutableMap<PrimitiveType, Color> nativeToColor;
    private final Wtf8.Decoder wtf8Decoder;

    private ColorPoolBuilder(
        TypePool typePool,
        StringPool stringPool,
        ImmutableMultimap<Integer, TypePointer> disambiguationEdges,
        ImmutableMap<PrimitiveType, Color> nativeToColor) {
      this.typePool = typePool;
      this.stringPool = stringPool;
      this.colorPool = new ArrayList<>();
      this.disambiguationEdges = disambiguationEdges;
      this.colorPool.addAll(Collections.nCopies(typePool.getTypeCount(), null));
      this.nativeToColor = nativeToColor;
      this.wtf8Decoder = Wtf8.decoder(stringPool.getMaxLength());
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
        throw new InvalidSerializedFormatException(
            "Cannot deserialize type in cycle " + serialized);
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
          throw new InvalidSerializedFormatException(
              "Expected all Types to have a Kind, found " + serialized);
      }
      throw new AssertionError();
    }

    private Color createObjectColor(int offset, ObjectTypeProto serialized) {
      ImmutableList<Color> directSupertypes =
          this.disambiguationEdges.get(offset).stream()
              .map(this::pointerToColor)
              .collect(toImmutableList());
      ObjectTypeProto.DebugInfo serializedDebugInfo = serialized.getDebugInfo();
      SingletonColorFields.Builder builder =
          SingletonColorFields.builder()
              .setId(ColorId.fromAscii(serialized.getUuid()))
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
                      .map(stringOffset -> this.stringPool.getStringsList().get(stringOffset))
                      .map(this.wtf8Decoder::decode)
                      .collect(toImmutableSet()));
      if (serialized.hasPrototype()) {
        builder.setPrototype(this.pointerToColor(serialized.getPrototype()));
      }
      if (serialized.hasInstanceType()) {
        builder.setInstanceColor(this.pointerToColor(serialized.getInstanceType()));
      }
      return Color.createSingleton(builder.build());
    }

    private Color createUnionColor(UnionTypeProto serialized) {
      if (serialized.getUnionMemberCount() <= 1) {
        throw new InvalidSerializedFormatException(
            "Unions must have >= 2 elements, found " + serialized);
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
      if (poolOffset < JSTypeSerializer.PRIMITIVE_POOL_SIZE) {
        return this.nativeToColor.get(PrimitiveType.forNumber(poolOffset));
      }
      int adjustedOffset = poolOffset - JSTypeSerializer.PRIMITIVE_POOL_SIZE;
      if (this.colorPool.get(adjustedOffset) == null) {
        this.deserializeTypeByOffset(adjustedOffset);
      }
      return this.colorPool.get(adjustedOffset);
    }
  }

  private static NativeColorId primitiveToColorId(PrimitiveType primitive) {
    switch (primitive) {
        // Type-system primitives
      case TOP_OBJECT:
        return NativeColorId.TOP_OBJECT;
      case UNKNOWN_TYPE:
        return NativeColorId.UNKNOWN;

        // JavaScript language primitives
      case BIGINT_TYPE:
        return NativeColorId.BIGINT;
      case BOOLEAN_TYPE:
        return NativeColorId.BOOLEAN;
      case NULL_OR_VOID_TYPE:
        return NativeColorId.NULL_OR_VOID;
      case NUMBER_TYPE:
        return NativeColorId.NUMBER;
      case STRING_TYPE:
        return NativeColorId.STRING;
      case SYMBOL_TYPE:
        return NativeColorId.SYMBOL;

      case UNRECOGNIZED:
        throw new InvalidSerializedFormatException("Unrecognized PrimitiveType " + primitive);
    }
    throw new AssertionError();
  }

  public Color pointerToColor(TypePointer typePointer) {
    validatePointer(typePointer, this.typePool);
    int poolOffset = typePointer.getPoolOffset();
    if (poolOffset < JSTypeSerializer.PRIMITIVE_POOL_SIZE) {
      return this.colorRegistry.get(primitiveToColorId(PrimitiveType.forNumber(poolOffset)));
    }
    int adjustedOffset = poolOffset - JSTypeSerializer.PRIMITIVE_POOL_SIZE;
    return this.colorPool.get(adjustedOffset);
  }

  /** Validates that the given typePointer is valid according to the given pool of types */
  private static void validatePointer(TypePointer typePointer, TypePool typePool) {
    int poolOffset = typePointer.getPoolOffset();
    // Account for the first N type pointer offsets being reserved for the primitive types.
    if (poolOffset < 0
        || poolOffset >= typePool.getTypeCount() + JSTypeSerializer.PRIMITIVE_POOL_SIZE) {
      throw new InvalidSerializedFormatException(
          "TypeProto pointer has out-of-bounds pool offset: "
              + typePointer
              + " for pool size "
              + typePool.getTypeCount());
    }
  }
}
