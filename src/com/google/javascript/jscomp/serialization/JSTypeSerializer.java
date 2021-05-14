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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.javascript.jscomp.serialization.TypePointers.isAxiomatic;
import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.joining;

import com.google.auto.value.AutoOneOf;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Streams;
import com.google.javascript.jscomp.InvalidatingTypes;
import com.google.javascript.jscomp.colors.ColorId;
import com.google.javascript.jscomp.serialization.JSTypeSerializer.SimplifiedType.Kind;
import com.google.javascript.rhino.ClosurePrimitive;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.EnumType;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.Nullable;

final class JSTypeSerializer {

  // Cache some commonly used types
  private final SimplifiedType unknownType;
  private final SimplifiedType topObjectType;
  private final SimplifiedType nullType;
  private final JSTypeRegistry registry;
  // JSTypes that map to the top object type. Necessary because it's difficult to identify these
  // types other than checking for equality with JSTypeRegistry methods.
  private final ImmutableSet<JSType> topObjectLikeTypes;

  private final InvalidatingTypes invalidatingTypes;
  private final StringPool.Builder stringPoolBuilder;
  private final JSTypeColorIdHasher hasher;
  private final SerializationOptions serializationMode;
  private final LinkedHashMap<SimplifiedType, SeenTypeRecord> seenSerializableTypes =
      new LinkedHashMap<>();
  private final Multimap<TypePointer, TypePointer> disambiguateEdges = LinkedHashMultimap.create();

  private State state = State.COLLECTING_TYPES;

  // JSTypeNatives that map to the top object type.
  private static final ImmutableSet<JSTypeNative> TOP_LIKE_OBJECT_IDS =
      ImmutableSet.of(
          JSTypeNative.OBJECT_TYPE,
          JSTypeNative.OBJECT_FUNCTION_TYPE,
          JSTypeNative.OBJECT_PROTOTYPE,
          JSTypeNative.FUNCTION_PROTOTYPE,
          JSTypeNative.FUNCTION_TYPE,
          JSTypeNative.FUNCTION_FUNCTION_TYPE);

  private enum State {
    COLLECTING_TYPES,
    GENERATING_POOL,
    FINISHED,
  }

  private JSTypeSerializer(
      JSTypeRegistry registry,
      InvalidatingTypes invalidatingTypes,
      StringPool.Builder stringPoolBuilder,
      SerializationOptions serializationMode) {
    this.unknownType = SimplifiedType.ofJSType(registry.getNativeType(JSTypeNative.UNKNOWN_TYPE));
    this.topObjectType = SimplifiedType.ofJSType(registry.getNativeType(JSTypeNative.OBJECT_TYPE));
    this.registry = registry;
    this.hasher = new JSTypeColorIdHasher(registry);
    this.topObjectLikeTypes =
        TOP_LIKE_OBJECT_IDS.stream().map(registry::getNativeType).collect(toImmutableSet());
    this.nullType = SimplifiedType.ofJSType(registry.getNativeType(JSTypeNative.NULL_TYPE));
    this.invalidatingTypes = invalidatingTypes;
    this.stringPoolBuilder = stringPoolBuilder;
    this.serializationMode = serializationMode;
  }

  public static JSTypeSerializer create(
      JSTypeRegistry registry,
      InvalidatingTypes invalidatingTypes,
      StringPool.Builder stringPoolBuilder,
      SerializationOptions serializationMode) {
    JSTypeSerializer serializer =
        new JSTypeSerializer(registry, invalidatingTypes, stringPoolBuilder, serializationMode);

    serializer.addPrimitiveTypePointers();
    serializer.checkValid();

    return serializer;
  }

  /** Returns a pointer to the given type. If it is not already serialized, serializes it too */
  TypePointer serializeType(JSType originalType) {
    checkValid();
    SimplifiedType type = simplifyTypeInternal(originalType);
    return serializeSimplifiedType(type);
  }

  /** Returns a pointer to the given type. If it is not already serialized, serializes it too */
  private TypePointer serializeSimplifiedType(SimplifiedType type) {
    checkValid();
    SeenTypeRecord existing = this.seenSerializableTypes.get(type);
    if (existing != null) {
      return existing.pointer;
    }

    checkState(State.COLLECTING_TYPES == this.state || State.GENERATING_POOL == this.state);

    TypePointer pointer =
        TypePointer.newBuilder().setPoolOffset(this.seenSerializableTypes.size()).build();
    SeenTypeRecord record = new SeenTypeRecord(pointer);
    this.seenSerializableTypes.put(type, record);
    // Serialize after the pointer is in the pool in case serialization requires a pool lookup.
    record.type = typeToProto(type, record.pointer);

    checkValid();
    return record.pointer;
  }

  private static String getDebugDescription(SimplifiedType type) {
    switch (type.getKind()) {
      case SINGLE:
        return type.single().toString();
      case UNION:
        return "("
            + type.union().stream().map(JSTypeSerializer::getDebugDescription).collect(joining(","))
            + ")";
    }
    throw new AssertionError();
  }

  /** Returns the canonical form of a given type. */
  private SimplifiedType simplifyTypeInternal(JSType type) {
    if (type.isEnumElementType()) {
      // replace with the corresponding primitive
      return simplifyTypeInternal(type.toMaybeEnumElementType().getPrimitiveType());
    }

    if (type.isTemplateType()) {
      // template types are not serialized because optimizations don't seem to care about them.
      // serialize as the UNKNOWN_TYPE because bounded generics are unsupported
      return unknownType;
    }

    if (type.isTemplatizedType()) {
      return simplifyTypeInternal(type.toMaybeTemplatizedType().getReferencedType());
    }

    if (type.isUnionType()) {
      return SimplifiedType.ofUnion(
          type.toMaybeUnionType().getAlternates().stream()
              .map(this::simplifyTypeInternal)
              .collect(toImmutableSet()));
    }

    if (type.isFunctionType() && type.toMaybeFunctionType().getCanonicalRepresentation() != null) {
      return simplifyTypeInternal(type.toMaybeFunctionType().getCanonicalRepresentation());
    }

    if (type.isNoResolvedType()
        || type.isAllType()
        || type.isCheckedUnknownType()
        || type.isUnknownType()
        || type.isNoType()
        || type.isNoObjectType()) {
      // Merge all the various top/bottom-like/unknown types into a single unknown type.
      return unknownType;
    }

    if (type.toObjectType() != null) {
      // Smoosh top-like objects into a single type.
      // The isNativeObjectType() check is only for performance reasons. It avoids computing
      // equals/hashCode for every object type as most types are not native.
      if (type.toObjectType().isNativeObjectType() && topObjectLikeTypes.contains(type)) {
        return topObjectType;
      }
      return SimplifiedType.ofJSType(type);
    }

    if (type.isVoidType() || type.isNullType()) {
      // Canonicalize the void type to the null type
      return nullType;
    }

    if (type.isBoxableScalar()) {
      return SimplifiedType.ofJSType(type);
    }

    throw new IllegalStateException("Unsupported type " + type);
  }

  /**
   * Constructs a {@link TypeProto} representation of the given union or object
   *
   * <p>Only call this method after checking the cache in {@link #seenSerializableTypes}, as this
   * method will unilaterally create a new object.
   */
  private TypeProto typeToProto(SimplifiedType type, TypePointer pointer) {
    switch (type.getKind()) {
      case UNION:
        UnionTypeProto.Builder union = UnionTypeProto.newBuilder();
        type.union().stream()
            .map(this::serializeSimplifiedType)
            .forEachOrdered(union::addUnionMember);
        return TypeProto.newBuilder().setUnion(union).build();
      case SINGLE:
        // Primitive types should have been added to the "seenSerializableTypes" map in
        // "addPrimitiveTypePointers", as they do not have corresponding TypeProtos.
        ObjectType objectType =
            checkNotNull(
                type.single().toMaybeObjectType(), "Unexpected non-object type %s", type.single());
        addSupertypeEdges(objectType, pointer);
        return TypeProto.newBuilder().setObject(serializeObjectType(objectType)).build();
    }

    throw new AssertionError();
  }

  private void addSupertypeEdges(ObjectType subtype, TypePointer serializedSubtype) {
    this.disambiguateEdges.putAll(serializedSubtype, ownAncestorInterfacesOf(subtype));
    if (subtype.getImplicitPrototype() != null) {
      TypePointer supertype = serializeType(subtype.getImplicitPrototype());
      this.disambiguateEdges.put(serializedSubtype, supertype);
    }
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
      objBuilder.setClosureAssert(isClosureAssert(fnType.getClosurePrimitive()));
    }
    if (this.serializationMode.includeDebugInfo()) {
      ObjectTypeProto.DebugInfo debugInfo = getDebugInfo(type);
      if (!debugInfo.equals(ObjectTypeProto.DebugInfo.getDefaultInstance())) {
        objBuilder.setDebugInfo(debugInfo);
      }
    }
    for (String ownProperty : type.getOwnPropertyNames()) {
      // TODO(b/169899789): consider omitting common, well-known properties like "prototype" to save
      // space.
      objBuilder.addOwnProperty(this.stringPoolBuilder.put(ownProperty));
    }
    return objBuilder
        .setIsInvalidating(invalidatingTypes.isInvalidating(type))
        // To support legacy code, property disambiguation never renames properties of enums
        // (e.g. 'A' in '/** @enum */ const E = {A: 0}`). In
        // theory this would be safe to remove if we clean up code depending on the lack of renaming
        .setPropertiesKeepOriginalName(type.isEnumType())
        .setUuid(this.hasher.hashObjectType(type).asByteString())
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

  /**
   * Inserts dummy pointers corresponding to all {@link PrimitiveType}s in the type pool.
   *
   * <p>These types will never correspond to an actual {@link TypeProto}. Instead, all normal {@link
   * TypePointer} offsets into the pool are offset by a number equivalent to the number of {@link
   * PrimitiveType} enum elements.
   */
  private void addPrimitiveTypePointers() {
    for (PrimitiveType primitive : PrimitiveType.values()) {
      if (primitive.equals(PrimitiveType.UNRECOGNIZED)) {
        continue;
      }
      checkState(
          primitive.getNumber() == seenSerializableTypes.size(),
          "Expected all PrimitiveTypes to be added in order; %s added at index %s.",
          primitive,
          seenSerializableTypes.size());
      TypePointer pointer = TypePointer.newBuilder().setPoolOffset(primitive.getNumber()).build();
      SeenTypeRecord record = new SeenTypeRecord(pointer);
      JSTypeNative jsTypeNative = canonicalizePrimitive(primitive);
      SimplifiedType simplified =
          SimplifiedType.ofJSType(this.registry.getNativeType(jsTypeNative));
      this.seenSerializableTypes.put(simplified, record);
    }
    checkState(this.seenSerializableTypes.size() == TypePointers.AXIOMATIC_COLOR_COUNT);
  }

  /** Checks that this instance is in a valid state. */
  private void checkValid() {
    if (!this.serializationMode.runValidation()) {
      return;
    }

    final int totalTypeCount = this.seenSerializableTypes.size();
    for (SeenTypeRecord seen : this.seenSerializableTypes.values()) {
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
   * calls to {@link #serializeType(JSType)}.
   *
   * <p>After generation, no new types can be added, so subsequent calls to {@link
   * #serializeType(JSType)} can only be used to retrieve pointers to existing types in the type
   * pool.
   */
  TypePool generateTypePool() {
    checkState(this.state == State.COLLECTING_TYPES);
    checkValid();
    this.state = State.GENERATING_POOL;

    TypePool.Builder builder = TypePool.newBuilder();
    for (SeenTypeRecord seen : this.seenSerializableTypes.values()) {
      if (seen.type == null) {
        // seen.type is if and only this is a native type without a TypeProto representation.
        checkState(isAxiomatic(seen.pointer), "Missing .type for SeenTypeRecord %s", seen);
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
    TypePool pool = builder.build();

    this.state = State.FINISHED;
    checkValid();
    return pool;
  }

  /**
   * Returns a map from {@link ObjectTypeProto#getUuid()} to the originating {@link JSType}.
   *
   * <p>Excludes unions and primitive types as they do not have their own UUIDs
   *
   * <p>Only intended to be used for debug logging.
   */
  ImmutableMultimap<String, String> getColorIdToJSTypeMapForDebugging() {
    ImmutableMultimap.Builder<String, String> uuidToType = ImmutableMultimap.builder();
    uuidToType.orderKeysBy(naturalOrder()).orderValuesBy(naturalOrder());
    this.seenSerializableTypes.forEach(
        (s, r) -> {
          TypeProto p = r.type;
          if (s.getKind().equals(Kind.SINGLE) && p != null && p.hasObject()) {
            uuidToType.put(
                ColorId.fromBytes(p.getObject().getUuid()).toString(), s.single().toString());
          }
        });
    return uuidToType.build();
  }

  private static final class SeenTypeRecord {
    final TypePointer pointer;
    // If null, indicates that this SeenTypeRecord represents a native type pointer with no
    // corresponding TypeProto.
    @Nullable TypeProto type;

    SeenTypeRecord(TypePointer pointer) {
      this.pointer = pointer;
    }
  }

  /**
   * Wraps a "simplified" JSType to ensure that we never accidentally look up or serialize a
   * non-simplified type.
   */
  @AutoOneOf(SimplifiedType.Kind.class)
  abstract static class SimplifiedType {
    public enum Kind {
      SINGLE,
      UNION
    }

    abstract Kind getKind();

    abstract JSType single();

    abstract ImmutableSet<SimplifiedType> union();

    private static SimplifiedType ofJSType(JSType t) {
      checkArgument(!t.isUnionType(), "Unions must be simplified to Set<JSType> but found %s", t);
      return AutoOneOf_JSTypeSerializer_SimplifiedType.single(t);
    }

    private static SimplifiedType ofUnion(Set<SimplifiedType> dirtyAlts) {
      if (dirtyAlts.size() == 1) {
        return Iterables.getOnlyElement(dirtyAlts);
      }

      LinkedHashSet<SimplifiedType> cleanAlts = new LinkedHashSet<>();
      for (SimplifiedType s : dirtyAlts) {
        switch (s.getKind()) {
          case SINGLE:
            cleanAlts.add(s);
            break;
          case UNION:
            cleanAlts.addAll(s.union());
            break;
        }
      }

      if (cleanAlts.size() == 1) {
        // Cleaning may collapse some members of the union
        return Iterables.getOnlyElement(cleanAlts);
      }

      return AutoOneOf_JSTypeSerializer_SimplifiedType.union(ImmutableSet.copyOf(cleanAlts));
    }
  }

  /**
   * Maps between {@link JSTypeNative} and {@link NativeType}.
   *
   * <p>In practice, there are multiple {@link JSTypeNative}s that may correspond to the same {@link
   * NativeType}. The {@link #simplifyTypeInternal(JSType)} is responsible for doing this
   * simplification.
   */
  private static JSTypeNative canonicalizePrimitive(PrimitiveType primitive) {
    switch (primitive) {
      case BOOLEAN_TYPE:
        return JSTypeNative.BOOLEAN_TYPE;
      case BIGINT_TYPE:
        return JSTypeNative.BIGINT_TYPE;

      case NUMBER_TYPE:
        return JSTypeNative.NUMBER_TYPE;
      case STRING_TYPE:
        return JSTypeNative.STRING_TYPE;

      case SYMBOL_TYPE:
        return JSTypeNative.SYMBOL_TYPE;

      case NULL_OR_VOID_TYPE:
        return JSTypeNative.NULL_TYPE;

        // The optimizer doesn't distinguish between any of these types: they are all
        // invalidating objects.
      case TOP_OBJECT:
        return JSTypeNative.OBJECT_TYPE;

      case UNKNOWN_TYPE:
        return JSTypeNative.UNKNOWN_TYPE;

      case UNRECOGNIZED:
        throw new AssertionError();
    }
    throw new AssertionError();
  }

  /**
   * Returns whether this is some assertion call that should be removed by optimizations when
   * --remove_closure_asserts is enabled.
   */
  private static boolean isClosureAssert(@Nullable ClosurePrimitive primitive) {
    if (primitive == null) {
      return false;
    }

    switch (primitive) {
      case ASSERTS_TRUTHY:
      case ASSERTS_MATCHES_RETURN:
        return true;

      case ASSERTS_FAIL: // technically an assertion function, but not removed by ClosureCodeRemoval
        return false;
    }
    throw new AssertionError();
  }
}
