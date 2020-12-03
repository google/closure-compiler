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
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.javascript.jscomp.serialization.NativeType.NUMBER_OBJECT_TYPE;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.ColorRegistry;
import com.google.javascript.jscomp.colors.DebugInfo;
import com.google.javascript.jscomp.colors.NativeColorId;
import com.google.javascript.jscomp.colors.SingletonColorFields;
import com.google.javascript.jscomp.serialization.TypePointer.TypeCase;
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
  private final ArrayList<Color> typeColors; // filled in as we go. initially all null
  // to avoid infinite recursion on types in cycles
  private final Set<Type> currentlyDeserializing = new LinkedHashSet<>();
  // keys are indices into the type pool and values are pointers to its supertypes
  private final Multimap<Integer, TypePointer> disambiguationEdges;
  private final TypePool typePool;
  private final ColorRegistry colorRegistry;

  /** Error emitted when the deserializer sees a serialized type it cannot support deserialize */
  public static final class InvalidSerializedFormatException extends RuntimeException {
    public InvalidSerializedFormatException(String msg) {
      super("Invalid serialized Type format: " + msg);
    }
  }

  private ColorDeserializer(
      TypePool typePool,
      Multimap<Integer, TypePointer> disambiguationEdges,
      ColorRegistry colorRegistry) {
    this.typePool = typePool;
    this.typeColors = new ArrayList<>();
    this.disambiguationEdges = disambiguationEdges;
    typeColors.addAll(Collections.nCopies(typePool.getTypeCount(), null));
    this.colorRegistry = colorRegistry;
  }

  public ColorRegistry getRegistry() {
    return this.colorRegistry;
  }

  public static ColorDeserializer buildFromTypePool(TypePool typePool) {
    Multimap<Integer, TypePointer> disambiguationEdges = LinkedHashMultimap.create();
    for (SubtypingEdge edge : typePool.getDisambiguationEdgesList()) {
      TypePointer subtype = edge.getSubtype();
      TypePointer supertype = edge.getSupertype();
      if (subtype.getTypeCase() != TypeCase.POOL_OFFSET
          || supertype.getTypeCase() != TypeCase.POOL_OFFSET) {
        throw new InvalidSerializedFormatException(
            "Subtyping only supported for pool offsets, found " + subtype + " , " + supertype);
      }
      disambiguationEdges.put(subtype.getPoolOffset(), supertype);
    }

    ColorRegistry colorRegistry =
        ColorRegistry.createWithInvalidatingNatives(
            typePool.getInvalidatingNativeList().stream()
                .map(ColorDeserializer::nativeTypeToColor)
                .collect(toImmutableSet()));
    return new ColorDeserializer(typePool, disambiguationEdges, colorRegistry);
  }

  /**
   * Given an index into the type pool, creating its corresponding color if not already
   * deserialized.
   */
  private void deserializeTypeFromPoolIfEmpty(int i) {
    if (typeColors.get(i) != null) {
      return;
    }
    Color color = deserializeType(i, typePool.getTypeList().get(i));
    typeColors.set(i, color);
  }

  /**
   * Safely deserializes a type after verifying it's not going to cause infinite recursion
   *
   * <p>Currently this always initializes a new {@link Color} and we assume there are no duplicate
   * types in the serialized type pool.
   */
  private Color deserializeType(int i, Type serialized) {
    if (currentlyDeserializing.contains(serialized)) {
      throw new InvalidSerializedFormatException("Cannot deserialize type in cycle " + serialized);
    }
    currentlyDeserializing.add(serialized);

    Color newColor = deserializeTypeAssumingSafe(i, serialized);

    currentlyDeserializing.remove(serialized);
    return newColor;
  }

  /** Creates a color from a Type without checking for any type cycles */
  private Color deserializeTypeAssumingSafe(int offset, Type serialized) {
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

  private Color createObjectColor(int offset, ObjectType serialized) {
    ImmutableList<Color> directSupertypes =
        this.disambiguationEdges.get(offset).stream()
            .map(this::pointerToColor)
            .collect(toImmutableList());
    TypeDebugInfo serializedDebugInfo = serialized.getDebugInfo();
    SingletonColorFields.Builder builder =
        SingletonColorFields.builder()
            .setId(serialized.getUuid())
            .setInvalidating(serialized.getIsInvalidating())
            .setPropertiesKeepOriginalName(serialized.getPropertiesKeepOriginalName())
            .setDisambiguationSupertypes(directSupertypes)
            .setDebugInfo(
                DebugInfo.builder()
                    .setFilename(serializedDebugInfo.getFilename())
                    .setClassName(serializedDebugInfo.getClassName())
                    .build());
    if (serialized.hasPrototype()) {
      builder.setPrototype(this.pointerToColor(serialized.getPrototype()));
    }
    if (serialized.hasInstanceType()) {
      builder.setInstanceColor(this.pointerToColor(serialized.getInstanceType()));
    }
    builder.setConstructor(serialized.getMarkedConstructor());
    return Color.createSingleton(builder.build());
  }

  private Color createUnionColor(UnionType serialized) {
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

  @SuppressWarnings("UnnecessaryDefaultInEnumSwitch") // needed for J2CL protos
  private static NativeColorId nativeTypeToColor(NativeType nativeType) {
    switch (nativeType) {
      case TOP_OBJECT:
        return NativeColorId.TOP_OBJECT;
      case UNKNOWN_TYPE:
        return NativeColorId.UNKNOWN;
        // Primitives

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
        // Boxed primitives

      case BIGINT_OBJECT_TYPE:
        return NativeColorId.BIGINT_OBJECT;
      case BOOLEAN_OBJECT_TYPE:
        return NativeColorId.BOOLEAN_OBJECT;
      case NUMBER_OBJECT_TYPE:
        return NativeColorId.NUMBER_OBJECT;
      case STRING_OBJECT_TYPE:
        return NativeColorId.STRING_OBJECT;
      case SYMBOL_OBJECT_TYPE:
        return NativeColorId.SYMBOL_OBJECT;
      default:
        // Switch cannot be exhaustive because Java protos add an additional "UNRECOGNIZED" field
        // while J2CL protos do not.
        throw new InvalidSerializedFormatException("unrecognized nativetype " + nativeType);
    }
  }

  public Color pointerToColor(TypePointer typePointer) {
    switch (typePointer.getTypeCase()) {
      case NATIVE_TYPE:
        return this.colorRegistry.get(nativeTypeToColor(typePointer.getNativeType()));
      case POOL_OFFSET:
        int poolOffset = typePointer.getPoolOffset();
        if (poolOffset < 0 || poolOffset >= this.typeColors.size()) {
          throw new InvalidSerializedFormatException(
              "Type pointer has out-of-bounds pool offset: "
                  + typePointer
                  + " for pool size "
                  + this.typeColors.size());
        }
        if (this.typeColors.get(typePointer.getPoolOffset()) == null) {
          this.deserializeTypeFromPoolIfEmpty(poolOffset);
        }
        return this.typeColors.get(poolOffset);
      case TYPE_NOT_SET:
        throw new InvalidSerializedFormatException("Cannot dereference TypePointer " + typePointer);
    }
    throw new AssertionError();
  }
}
