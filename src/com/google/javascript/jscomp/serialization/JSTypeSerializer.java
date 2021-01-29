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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Streams;
import com.google.javascript.jscomp.IdGenerator;
import com.google.javascript.jscomp.InvalidatingTypes;
import com.google.javascript.jscomp.serialization.TypePointer.ValueCase;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.EnumType;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.UnionType;
import com.google.javascript.rhino.serialization.SerializationOptions;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.function.Supplier;
import javax.annotation.Nullable;

final class JSTypeSerializer {

  // Cache the unknownPointer since it's commonly used
  private final TypePointer unknownPointer;
  private final InvalidatingTypes invalidatingTypes;
  private final IdGenerator idGenerator;
  private final SerializationOptions serializationMode;
  private State state = State.COLLECTING_TYPES;
  private final LinkedHashMap<JSType, SeenTypeRecord> seenSerializableTypes = new LinkedHashMap<>();
  private int currentPoolSize = 0;
  private final Multimap<TypePointer, TypePointer> disambiguateEdges = LinkedHashMultimap.create();
  private final LinkedHashSet<NativeType> invalidatingNatives = new LinkedHashSet<>();

  private enum State {
    COLLECTING_TYPES,
    GENERATING_POOL,
    FINISHED,
  }

  private JSTypeSerializer(
      TypePointer unknownPointer,
      InvalidatingTypes invalidatingTypes,
      IdGenerator idGenerator,
      SerializationOptions serializationMode) {
    this.unknownPointer = unknownPointer;
    this.invalidatingTypes = invalidatingTypes;
    this.idGenerator = idGenerator;
    this.serializationMode = serializationMode;
  }

  public static JSTypeSerializer create(
      JSTypeRegistry registry,
      InvalidatingTypes invalidatingTypes,
      SerializationOptions serializationMode) {
    IdGenerator idGenerator = new IdGenerator();

    JSTypeSerializer serializer =
        new JSTypeSerializer(
            TypePointer.newBuilder().setNativeType(NativeType.UNKNOWN_TYPE).build(),
            invalidatingTypes,
            idGenerator,
            serializationMode);

    serializer.serializeNativeTypes(registry);
    serializer.collectInvalidatingNatives(registry, invalidatingTypes);
    serializer.checkValid();

    return serializer;
  }

  /**
   * Adds all invalidating native types to the type pool.
   *
   * <p>Native types are not explicitly serialized, since the only bit of information that differs
   * between compilation units is their "invalidatingness".
   */
  private void collectInvalidatingNatives(
      JSTypeRegistry registry, InvalidatingTypes invalidatingTypes) {
    for (JSTypeNative jsTypeNative : JSTypeNative.values()) {
      // First check if this type is also native in the colors
      NativeType correspondingType = translateNativeType(jsTypeNative);
      if (correspondingType == null) {
        continue;
      }
      // Then check if it is invalidating
      if (invalidatingTypes.isInvalidating(registry.getNativeType(jsTypeNative))) {
        registerInvalidatingNative(correspondingType);
      }
    }
  }

  /** Returns a pointer to the given type. If it is not already serialized, serializes it too */
  TypePointer serializeType(JSType type) {
    if (type.isNamedType()) {
      return serializeType(type.toMaybeNamedType().getReferencedType());
    }

    if (type.isEnumElementType()) {
      // replace with the corresponding primitive
      return serializeType(type.toMaybeEnumElementType().getPrimitiveType());
    }

    if (type.isTemplateType()) {
      // template types are not serialized because optimizations don't seem to care about them.
      // serialize as the UNKNOWN_TYPE because bounded generics are unsupported
      return unknownPointer;
    }

    if (type.isTemplatizedType()) {
      return serializeType(type.toMaybeTemplatizedType().getReferencedType());
    }

    if (type.isUnionType()) {
      return serializeUnionType(type.toMaybeUnionType());
    }

    if (type.isFunctionType() && type.toMaybeFunctionType().getCanonicalRepresentation() != null) {
      return serializeType(type.toMaybeFunctionType().getCanonicalRepresentation());
    }

    if (type.isNoResolvedType()
        || type.isAllType()
        || type.isCheckedUnknownType()
        || type.isUnknownType()
        || type.isNoType()
        || type.isNoObjectType()) {
      // Merge all the various top/bottom-like/unknown types into a single unknown type.
      return unknownPointer;
    }

    if (type.toObjectType() != null) {
      TypePointer serialized =
          typeToPointer(
              type,
              () ->
                  TypeProto.newBuilder()
                      .setObject(serializeObjectType(type.toObjectType()))
                      .build());
      addSupertypeEdges(type.toMaybeObjectType(), serialized);
      return serialized;
    }

    if (type.isBoxableScalar() || type.isNullType() || type.isVoidType()) {
      return typeToPointer(
          type,
          () -> {
            throw new IllegalStateException("Primitive types must already have been registered");
          });
    }

    throw new IllegalStateException("Unsupported type " + type);
  }

  private void addSupertypeEdges(ObjectType subtype, TypePointer serializedSubtype) {
    for (TypePointer ancestor : ownAncestorInterfacesOf(subtype)) {
      addDisambiguationEdge(serializedSubtype, ancestor);
    }
    if (subtype.getImplicitPrototype() != null) {
      addDisambiguationEdge(serializedSubtype, serializeType(subtype.getImplicitPrototype()));
    }
  }

  private TypePointer serializeUnionType(UnionType type) {
    ImmutableSet<TypePointer> serializedAlternates =
        type.getAlternates().stream().map(this::serializeType).collect(toImmutableSet());
    if (serializedAlternates.size() == 1) {
      return Iterables.getOnlyElement(serializedAlternates);
    }
    return typeToPointer(type, () -> serializeUnionType(serializedAlternates));
  }

  private static TypeProto serializeUnionType(ImmutableSet<TypePointer> serializedAlternates) {
    return TypeProto.newBuilder()
        .setUnion(UnionTypeProto.newBuilder().addAllUnionMember(serializedAlternates).build())
        .build();
  }

  private static ObjectTypeProto.DebugInfo getDebugInfo(ObjectType type) {
    ObjectTypeProto.DebugInfo defaultDebugInfo = defaultDebugInfo(type);
    if (type.isInstanceType()) {
      return instanceDebugInfo(type, defaultDebugInfo);
    } else if (type.isEnumType()) {
      return enumDebugInfo(type.toMaybeEnumType(), defaultDebugInfo);
    } else if (type.isFunctionType()) {
      return functionDebugInfo(type.toMaybeFunctionType(), defaultDebugInfo);
    }
    return defaultDebugInfo;
  }

  private static ObjectTypeProto.DebugInfo defaultDebugInfo(ObjectType type) {
    ObjectTypeProto.DebugInfo.Builder builder = ObjectTypeProto.DebugInfo.newBuilder();
    Node ownerNode = type.getOwnerFunction() != null ? type.getOwnerFunction().getSource() : null;
    if (ownerNode != null) {
      builder.setFilename(ownerNode.getSourceFileName());
    }
    String className = type.getReferenceName();
    if (className != null) {
      builder.setClassName(className);
    }
    return builder.build();
  }

  private ObjectTypeProto serializeObjectType(ObjectType type) {
    ObjectTypeProto.Builder objBuilder = ObjectTypeProto.newBuilder();
    if (type.isFunctionType()) {
      FunctionType fnType = type.toMaybeFunctionType();
      // Serialize prototypes and instance types for instantiable types. Even if these types never
      // appear on the AST, optimizations need to know that at runtime these types may be present.
      if (fnType.hasInstanceType() && fnType.getInstanceType() != null) {
        objBuilder
            .setPrototype(serializeType(fnType.getPrototype()))
            .setInstanceType(serializeType(fnType.getInstanceType()));
        if (fnType.isConstructor()) {
          objBuilder.setMarkedConstructor(true);
        }
      }
    }
    if (this.serializationMode.includeDebugInfo()) {
      ObjectTypeProto.DebugInfo debugInfo = getDebugInfo(type);
      if (!debugInfo.equals(ObjectTypeProto.DebugInfo.getDefaultInstance())) {
        objBuilder.setDebugInfo(debugInfo);
      }
    }
    return objBuilder
        .setIsInvalidating(invalidatingTypes.isInvalidating(type))
        // To support legacy code, property disambiguation never renames properties of enums
        // (e.g. 'A' in '/** @enum */ const E = {A: 0}`). In
        // theory this would be safe to remove if we clean up code depending on the lack of renaming
        .setPropertiesKeepOriginalName(type.isEnumType())
        // NOTE: We need a better format than sequential integers in order to have an id that
        // can be consistent across compilation units. For now, using a sequential integers for each
        // type depends on the invariant that we serialize each distinct type exactly once and from
        // a single compilation unit.
        .setUuid(Integer.toHexString(idGenerator.newId()))
        .build();
  }

  private static ObjectTypeProto.DebugInfo instanceDebugInfo(
      ObjectType type, ObjectTypeProto.DebugInfo defaultDebugInfo) {
    FunctionType constructor = type.getConstructor();
    String className = constructor.getReferenceName();
    ObjectTypeProto.DebugInfo.Builder builder =
        ObjectTypeProto.DebugInfo.newBuilder(defaultDebugInfo);
    if (className != null && !className.isEmpty()) {
      builder.setClassName(className);
    }
    if (builder.getFilename().isEmpty() && constructor.getSource() != null) {
      String filename = constructor.getSource().getSourceFileName();
      builder.setFilename(filename);
    }
    return builder.build();
  }

  private static ObjectTypeProto.DebugInfo enumDebugInfo(
      EnumType type, ObjectTypeProto.DebugInfo defaultDebugInfo) {
    ObjectTypeProto.DebugInfo.Builder builder =
        ObjectTypeProto.DebugInfo.newBuilder(defaultDebugInfo);
    if (type.getSource() != null) {
      builder.setFilename(type.getSource().getSourceFileName());
    }
    return builder.build();
  }

  private static ObjectTypeProto.DebugInfo functionDebugInfo(
      FunctionType type, ObjectTypeProto.DebugInfo defaultDebugInfo) {
    Node source = type.getSource();
    ObjectTypeProto.DebugInfo.Builder builder =
        ObjectTypeProto.DebugInfo.newBuilder(defaultDebugInfo);
    if (source != null) {
      String filename = source.getSourceFileName();
      if (filename != null) {
        builder.setFilename(filename);
      }
    }
    if (type.hasInstanceType() && type.getSource() != null) {
      // Render function types known to be type definitions as "(typeof Foo)". This includes types
      // defined like "/** @constructor */ function Foo() { }" but not to those defined like "@param
      // {function(new:Foo)}". Only the former will have a source node.
      builder.setClassName("(typeof " + builder.getClassName() + ")");
    }
    return builder.build();
  }

  /**
   * Returns the interfaces directly implemented and extended by {@code type}.
   *
   * <p>Some of these relationships represent type errors; however, the graph needs to contain those
   * edges for safe disambiguation. In particular, code generated from other languages (e.g TS)
   * might have more flexible subtyping rules.
   */
  private ImmutableList<TypePointer> ownAncestorInterfacesOf(ObjectType type) {
    FunctionType ctorType = type.getConstructor();
    if (ctorType == null) {
      return ImmutableList.of();
    }

    return Streams.concat(
            ctorType.getExtendedInterfaces().stream(),
            ctorType.getOwnImplementedInterfaces().stream())
        .map(this::serializeType)
        .collect(toImmutableList());
  }

  private ImmutableMap<JSType, TypePointer> serializeNativeTypes(JSTypeRegistry registry) {
    ImmutableMap.Builder<JSType, TypePointer> nativeTypes = ImmutableMap.builder();
    for (JSTypeNative jsNativeType : JSTypeNative.values()) {
      NativeType serializedNativeType = translateNativeType(jsNativeType);
      if (serializedNativeType == null) {
        continue;
      }
      JSType jsType = registry.getNativeType(jsNativeType);
      TypePointer pointer = TypePointer.newBuilder().setNativeType(serializedNativeType).build();
      nativeTypes.put(jsType, pointer);
      registerPointerForType(jsType, pointer);
    }
    return nativeTypes.build();
  }

  /** Checks that this instance is in a valid state. */
  private void checkValid() {
    if (!this.serializationMode.runValidation()) {
      return;
    }

    final int totalTypeCount = currentPoolSize;
    for (SeenTypeRecord seen : this.seenSerializableTypes.values()) {
      if (!seen.pointer.getValueCase().equals(TypePointer.ValueCase.POOL_OFFSET)) {
        continue;
      }
      int offset = seen.pointer.getPoolOffset();
      checkState(offset >= 0);
      checkState(
          offset <= totalTypeCount,
          "Found invalid pointer %s, out of a total of %s user-defined types",
          offset,
          totalTypeCount);
    }
  }

  /**
   * Generates a "type-pool" representing all the types that this class has encountered through
   * calls to {@link #typeToPointer(JSType, Supplier)}.
   *
   * <p>After generation, no new types can be added, so subsequent calls to {@link
   * #typeToPointer(JSType, Supplier)} can only be used to retrieve pointers to existing types in
   * the type pool.
   */
  TypePool generateTypePool() {
    checkState(this.state == State.COLLECTING_TYPES);
    checkValid();
    this.state = State.GENERATING_POOL;

    TypePool.Builder builder = TypePool.newBuilder();
    for (SeenTypeRecord seen : this.seenSerializableTypes.values()) {
      if (seen.type == null) {
        continue;
      }
      builder.addType(seen.type);
    }
    for (TypePointer subtype : this.disambiguateEdges.keySet()) {
      for (TypePointer supertype : this.disambiguateEdges.get(subtype)) {
        builder.addDisambiguationEdges(
            SubtypingEdge.newBuilder().setSubtype(subtype).setSupertype(supertype));
      }
    }
    TypePool pool = builder.addAllInvalidatingNative(this.invalidatingNatives).build();

    this.state = State.FINISHED;
    checkValid();
    return pool;
  }

  /**
   * Returns the type-pointer for the given AST-type, adding it to the type-pool if not present.
   *
   * <p>The type-pointer is a reference that can be later used to look up the given type in the
   * type-pool. This function memoizes type-pointers and can be safely called multiple times for a
   * given type.
   *
   * <p>The given serializer will be called only once per type during type pool generation.
   */
  private TypePointer typeToPointer(JSType jsType, Supplier<TypeProto> serialize) {
    checkValid();

    SeenTypeRecord existing = this.seenSerializableTypes.get(jsType);
    if (existing != null) {
      return existing.pointer;
    }

    checkState(State.COLLECTING_TYPES == this.state || State.GENERATING_POOL == this.state);

    TypePointer.Builder pointer = TypePointer.newBuilder().setPoolOffset(currentPoolSize);
    if (!SerializationOptions.SKIP_DEBUG_INFO.equals(this.serializationMode)) {
      pointer.setDebugInfo(TypePointer.DebugInfo.newBuilder().setDescription(jsType.toString()));
    }

    SeenTypeRecord record = new SeenTypeRecord(pointer.build());
    this.seenSerializableTypes.put(jsType, record);
    currentPoolSize++;
    // Serialize after the pointer is in the pool in case serialization requires a pool lookup.
    record.type = serialize.get();

    checkValid();
    return record.pointer;
  }

  /**
   * Caches a particular pointer with a particular type so that future {@link #typeToPointer} calls
   * will return the given {@code pointer}.
   *
   * <p>An error will be thrown if either the pointer refers to an out-of-bounds pool offset or the
   * given type is already recorded in the type pool.
   */
  private void registerPointerForType(JSType type, TypePointer pointer) {
    if (pointer.getValueCase().equals(ValueCase.POOL_OFFSET)) {
      checkState(
          pointer.getPoolOffset() >= 0 && pointer.getPoolOffset() < currentPoolSize,
          "Invalid type pointer %s",
          pointer);
    }

    // Avoid multiple map lookups by combining a) verifying that `type` isn'JSType already present
    // in our
    // map and b) putting (type, pointer) into the map.
    this.seenSerializableTypes.compute(
        type,
        (jsType, existingValue) -> {
          checkState(
              existingValue == null,
              "Cannot register duplicate pointer value %s for type %s",
              existingValue,
              jsType);
          return new SeenTypeRecord(pointer);
        });
  }

  /**
   * Adds an edge from the given subtype to the given supertype if both are user-defined types and
   * not native.
   */
  private void addDisambiguationEdge(TypePointer subtype, TypePointer supertype) {
    checkState(this.state == State.COLLECTING_TYPES);

    if (subtype.getValueCase().equals(TypePointer.ValueCase.POOL_OFFSET)
        && supertype.getValueCase().equals(TypePointer.ValueCase.POOL_OFFSET)) {
      this.disambiguateEdges.put(subtype, supertype);
    }
  }

  private void registerInvalidatingNative(NativeType invalidatingType) {
    checkState(this.state == State.COLLECTING_TYPES);
    this.invalidatingNatives.add(invalidatingType);
  }

  private static final class SeenTypeRecord {
    final TypePointer pointer;
    // If null, indicates that this SeenTypeRecord either corresponds to a native type pointer or
    // is an alias of a SeenTypeRecord with the same pool offset + a non-null TypeProto.
    @Nullable TypeProto type;

    SeenTypeRecord(TypePointer pointer) {
      this.pointer = pointer;
    }
  }

  /**
   * Maps between {@link JSTypeNative} and {@link NativeType}.
   *
   * <p>Not one-to-one or onto. Some Closure native types are not natively serialized and multiple
   * Closure native types correspond go the "UNKNOWN" serialized type.
   */
  private static NativeType translateNativeType(JSTypeNative nativeType) {
    switch (nativeType) {
      case BOOLEAN_TYPE:
        return NativeType.BOOLEAN_TYPE;
      case BIGINT_TYPE:
        return NativeType.BIGINT_TYPE;

      case NUMBER_TYPE:
        return NativeType.NUMBER_TYPE;
      case STRING_TYPE:
        return NativeType.STRING_TYPE;

      case SYMBOL_TYPE:
        return NativeType.SYMBOL_TYPE;


      case NULL_TYPE:
      case VOID_TYPE:
        return NativeType.NULL_OR_VOID_TYPE;

        // The optimizer doesn't distinguish between any of these types: they are all
        // invalidating objects.
      case OBJECT_TYPE:
      case OBJECT_FUNCTION_TYPE:
      case OBJECT_PROTOTYPE:
      case FUNCTION_PROTOTYPE:
      case FUNCTION_TYPE:
      case FUNCTION_FUNCTION_TYPE:
        return NativeType.TOP_OBJECT;

      case ALL_TYPE:
      case UNKNOWN_TYPE:
      case CHECKED_UNKNOWN_TYPE:
      case NO_TYPE:
      case NO_OBJECT_TYPE:
      case NO_RESOLVED_TYPE:
        return NativeType.UNKNOWN_TYPE;

      case BOOLEAN_OBJECT_TYPE:
        return NativeType.BOOLEAN_OBJECT_TYPE;
      case BIGINT_OBJECT_TYPE:
        return NativeType.BIGINT_OBJECT_TYPE;
      case NUMBER_OBJECT_TYPE:
        return NativeType.NUMBER_OBJECT_TYPE;
      case STRING_OBJECT_TYPE:
        return NativeType.STRING_OBJECT_TYPE;
      case SYMBOL_OBJECT_TYPE:
        return NativeType.SYMBOL_OBJECT_TYPE;

      default:
        return null;
    }
  }
}
