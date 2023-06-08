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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.javascript.jscomp.serialization.TypePointers.isAxiomatic;
import static java.util.Comparator.naturalOrder;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.javascript.jscomp.InvalidatingTypes;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.ColorId;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.rhino.ClosurePrimitive;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.UnionType;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.function.Predicate;
import org.jspecify.nullness.Nullable;

/**
 * Takes {@link JSType}s produced by JSCompiler's typechecker and deduplicates and serializes them
 * into the TypedAST colors format.
 *
 * <p>The deduplication phase is called "reconciliation". It's necessary because the the TypedAST
 * colors format is simpler than the {@link JSType} format, and so there is a many-to-one
 * relationship between {@link JSType}s and TypedAST colors.
 */
final class JSTypeReconserializer {

  private final JSTypeRegistry registry;
  private final SerializationOptions serializationMode;
  private final InvalidatingTypes invalidatingTypes;
  private final StringPool.Builder stringPoolBuilder;
  private final JSTypeColorIdHasher hasher;
  private final Predicate<String> shouldPropagatePropertyName;

  // Cache some commonly used types.
  private final SeenTypeRecord unknownRecord;
  private final SeenTypeRecord topObjectRecord;

  private final IdentityHashMap<JSType, SeenTypeRecord> typeToRecordCache = new IdentityHashMap<>();
  private final LinkedHashMap<ColorId, SeenTypeRecord> seenTypeRecords = new LinkedHashMap<>();
  private final SetMultimap<Integer, Integer> disambiguateEdges = LinkedHashMultimap.create();

  private State state = State.COLLECTING_TYPES;

  // This is a one-way mapping because some JSTypes go to the same Color.
  private static final ImmutableMap<JSTypeNative, Color> JSTYPE_NATIVE_TO_AXIOMATIC_COLOR_MAP =
      ImmutableMap.<JSTypeNative, Color>builder()
          // Merge all the various top/bottom-like/unknown types into a single unknown type.
          .put(JSTypeNative.ALL_TYPE, StandardColors.UNKNOWN)
          .put(JSTypeNative.CHECKED_UNKNOWN_TYPE, StandardColors.UNKNOWN)
          .put(JSTypeNative.NO_OBJECT_TYPE, StandardColors.UNKNOWN)
          .put(JSTypeNative.NO_TYPE, StandardColors.UNKNOWN)
          .put(JSTypeNative.UNKNOWN_TYPE, StandardColors.UNKNOWN)
          // Map all the primitives in the obvious way.
          .put(JSTypeNative.BIGINT_TYPE, StandardColors.BIGINT)
          .put(JSTypeNative.BOOLEAN_TYPE, StandardColors.BOOLEAN)
          .put(JSTypeNative.NULL_TYPE, StandardColors.NULL_OR_VOID)
          .put(JSTypeNative.NUMBER_TYPE, StandardColors.NUMBER)
          .put(JSTypeNative.STRING_TYPE, StandardColors.STRING)
          .put(JSTypeNative.SYMBOL_TYPE, StandardColors.SYMBOL)
          .put(JSTypeNative.VOID_TYPE, StandardColors.NULL_OR_VOID)
          // Smoosh top-like objects into a single type.
          .put(JSTypeNative.FUNCTION_FUNCTION_TYPE, StandardColors.TOP_OBJECT)
          .put(JSTypeNative.FUNCTION_PROTOTYPE, StandardColors.TOP_OBJECT)
          .put(JSTypeNative.FUNCTION_INSTANCE_PROTOTYPE, StandardColors.TOP_OBJECT)
          .put(JSTypeNative.FUNCTION_TYPE, StandardColors.TOP_OBJECT)
          .put(JSTypeNative.OBJECT_FUNCTION_TYPE, StandardColors.TOP_OBJECT)
          .put(JSTypeNative.OBJECT_PROTOTYPE, StandardColors.TOP_OBJECT)
          .put(JSTypeNative.OBJECT_TYPE, StandardColors.TOP_OBJECT)
          .buildOrThrow();

  private enum State {
    COLLECTING_TYPES,
    GENERATING_POOL,
    FINISHED,
  }

  private JSTypeReconserializer(
      JSTypeRegistry registry,
      InvalidatingTypes invalidatingTypes,
      StringPool.Builder stringPoolBuilder,
      Predicate<String> shouldPropagatePropertyName,
      SerializationOptions serializationMode) {
    this.registry = registry;
    this.hasher = new JSTypeColorIdHasher(registry);
    this.invalidatingTypes = invalidatingTypes;
    this.stringPoolBuilder = stringPoolBuilder;
    this.shouldPropagatePropertyName = shouldPropagatePropertyName;
    this.serializationMode = serializationMode;

    this.seedCachesWithAxiomaticTypes();

    this.unknownRecord = this.seenTypeRecords.get(StandardColors.UNKNOWN.getId());
    this.topObjectRecord = this.seenTypeRecords.get(StandardColors.TOP_OBJECT.getId());
  }

  /**
   * Initializes a JSTypeReconserializer
   *
   * @param shouldPropagatePropertyName decide whether a property present on some ObjectType should
   *     actually be serialized. Used to avoid serializing properties that won't impact
   *     optimizations (because they aren't present in the AST)
   */
  public static JSTypeReconserializer create(
      JSTypeRegistry registry,
      InvalidatingTypes invalidatingTypes,
      StringPool.Builder stringPoolBuilder,
      Predicate<String> shouldPropagatePropertyName,
      SerializationOptions serializationMode) {
    JSTypeReconserializer serializer =
        new JSTypeReconserializer(
            registry,
            invalidatingTypes,
            stringPoolBuilder,
            shouldPropagatePropertyName,
            serializationMode);
    serializer.checkValidLinearTime();
    return serializer;
  }

  /** Returns a pointer to the given type. If it is not already serialized, serializes it too */
  int serializeType(JSType type) {
    SeenTypeRecord record = recordType(type);
    return record.pointer;
  }

  private SeenTypeRecord recordType(JSType type) {
    final JSType forwardedType;
    if (type.isNamedType()) {
      forwardedType = type.toMaybeNamedType().getReferencedType();
    } else if (type.isEnumElementType()) {
      forwardedType = type.toMaybeEnumElementType().getPrimitiveType();
    } else if (type.isTemplatizedType()) {
      forwardedType = type.toMaybeTemplatizedType().getReferencedType();
    } else if (type.isFunctionType()
        && type.toMaybeFunctionType().getCanonicalRepresentation() != null) {
      forwardedType = type.toMaybeFunctionType().getCanonicalRepresentation();
    } else {
      forwardedType = null;
    }
    if (forwardedType != null) {
      return recordType(forwardedType);
    }

    if (type.isUnknownType() || type.isNoResolvedType() || type.isTemplateType()) {
      // template types are not serialized because optimizations don't seem to care about them.
      // serialize as the UNKNOWN_TYPE because bounded generics are unsupported
      return this.unknownRecord;
    }
    if (type.isFunctionType()
        && !type.toMaybeFunctionType().hasInstanceType()
        && type.toMaybeFunctionType().getClosurePrimitive() == null) {
      // Distinguishing different function types does not matter for optimizations unless they
      // are a constructor/interface or have a @closurePrimitive tag associated. Optimization colors
      // don't track function parameter/return/template types, and function literals are all
      // invalidating types during property disambiguation.
      return this.topObjectRecord;
    }

    SeenTypeRecord jstypeRecord = this.typeToRecordCache.get(type);
    if (jstypeRecord != null) {
      return jstypeRecord;
    }

    if (type.isUnionType()) {
      return this.recordUnionType(type.toMaybeUnionType());
    } else if (type.isObjectType()) {
      return this.recordObjectType(type.toMaybeObjectType());
    }

    throw new AssertionError(type);
  }

  private SeenTypeRecord recordUnionType(UnionType type) {
    checkNotNull(type);

    LinkedHashSet<SeenTypeRecord> altRecords = new LinkedHashSet<>();
    for (JSType altType : type.getAlternates()) {
      SeenTypeRecord alt = this.recordType(altType);
      if (alt.unionMembers == null) {
        altRecords.add(alt);
      } else {
        // Flatten out any nested unions. They are possible due to proxy-like types.
        altRecords.addAll(alt.unionMembers);
      }
    }

    // Some elements of the union may be equal as Colors
    if (altRecords.size() == 1) {
      return Iterables.getOnlyElement(altRecords);
    }

    ImmutableSet.Builder<ColorId> alternateIds = ImmutableSet.builder();
    for (SeenTypeRecord altRecord : altRecords) {
      alternateIds.add(altRecord.colorId);
    }
    ColorId unionId = ColorId.union(alternateIds.build());
    SeenTypeRecord record = this.getOrCreateRecord(unionId, type);

    if (record.unionMembers == null) {
      record.unionMembers = ImmutableSet.copyOf(altRecords);
    } else if (this.serializationMode.runValidation()) {
      checkState(
          altRecords.equals(record.unionMembers),
          "Unions with same ID must have same members: %s => %s == %s",
          unionId,
          altRecords.stream().map((r) -> r.colorId).collect(toImmutableSet()),
          record.unionMembers.stream().map((r) -> r.colorId).collect(toImmutableSet()));
    }

    return record;
  }

  private SeenTypeRecord recordObjectType(ObjectType type) {
    checkNotNull(type);

    ColorId id = this.hasher.hashObjectType(type);
    SeenTypeRecord record = this.getOrCreateRecord(id, type);
    this.addSupertypeEdges(type, record.pointer);

    if (type.isFunctionType()) {
      FunctionType fnType = type.toMaybeFunctionType();
      if (fnType.hasInstanceType() && fnType.getInstanceType() != null) {
        // We have to serialize these here ahead of time to avoid a ConcurrentModificationException
        // during reconciliation.
        this.serializeType(fnType.getInstanceType());
        this.serializeType(fnType.getPrototype());
      }
    }

    return record;
  }

  private void addSupertypeEdges(ObjectType subtype, Integer serializedSubtype) {
    this.disambiguateEdges.putAll(serializedSubtype, ownAncestorInterfacesOf(subtype));
    if (subtype.getImplicitPrototype() != null) {
      Integer supertype = this.serializeType(subtype.getImplicitPrototype());
      this.disambiguateEdges.put(serializedSubtype, supertype);
    }
  }

  private SeenTypeRecord getOrCreateRecord(ColorId id, JSType jstype) {
    checkNotNull(jstype);
    checkState(State.COLLECTING_TYPES == this.state || State.GENERATING_POOL == this.state);

    SeenTypeRecord record =
        this.seenTypeRecords.computeIfAbsent(
            id,
            (unused) -> {
              int pointer = this.seenTypeRecords.size();
              return new SeenTypeRecord(id, pointer);
            });
    this.typeToRecordCache.put(jstype, record);
    record.jstypes.add(jstype);

    return record;
  }

  private TypeProto reconcileUnionTypes(SeenTypeRecord seen) {
    return TypeProto.newBuilder()
        .setUnion(
            UnionTypeProto.newBuilder()
                .addAllUnionMember(
                    seen.unionMembers.stream()
                        .map((r) -> r.pointer)
                        .sorted()
                        .collect(toImmutableList()))
                .build())
        .build();
  }

  private TypeProto reconcileObjectTypes(SeenTypeRecord seen) {
    LinkedHashSet<Integer> instancePointers = new LinkedHashSet<>();
    LinkedHashSet<Integer> prototypePointers = new LinkedHashSet<>();
    LinkedHashSet<Integer> ownProperties = new LinkedHashSet<>();
    boolean isClosureAssert = false;
    boolean isConstructor = false;
    boolean isInvalidating = false;
    boolean propertiesKeepOriginalName = false;

    for (JSType type : seen.jstypes) {
      ObjectType objType = checkNotNull(type.toMaybeObjectType(), type);

      if (objType.isFunctionType()) {
        FunctionType fnType = objType.toMaybeFunctionType();

        // Serialize prototypes and instance types for instantiable types. Even if these types never
        // appear on the AST, optimizations need to know that at runtime these types may be present.
        if (fnType.hasInstanceType() && fnType.getInstanceType() != null) {
          instancePointers.add(this.serializeType(fnType.getInstanceType()));
          prototypePointers.add(this.serializeType(fnType.getPrototype()));
          isConstructor |= fnType.isConstructor();
        }

        isClosureAssert |= isClosureAssert(fnType.getClosurePrimitive());
      }

      for (String ownProperty : objType.getOwnPropertyNames()) {
        // TODO(b/169899789): consider omitting common, well-known properties like "prototype" to
        // save space.
        if (shouldPropagatePropertyName.test(ownProperty)) {
          ownProperties.add(this.stringPoolBuilder.put(ownProperty));
        }
      }

      isInvalidating |= this.invalidatingTypes.isInvalidating(objType);

      /*
       * To support legacy code, property disambiguation never renames properties of enums (e.g. 'A'
       * in '/** @enum * / const E = {A: 0}`). In theory this would be safe to remove if we clean up
       * code depending on the lack of renaming
       */
      propertiesKeepOriginalName |= objType.isEnumType();
    }

    ObjectTypeProto objectProto =
        ObjectTypeProto.newBuilder()
            .addAllInstanceType(instancePointers)
            .addAllOwnProperty(ownProperties)
            .addAllPrototype(prototypePointers)
            .setClosureAssert(isClosureAssert)
            .setIsInvalidating(isInvalidating)
            .setMarkedConstructor(isConstructor)
            .setPropertiesKeepOriginalName(propertiesKeepOriginalName)
            .setUuid(seen.colorId.asByteString())
            .build();
    return TypeProto.newBuilder().setObject(objectProto).build();
  }

  /**
   * Returns the interfaces directly implemented and extended by {@code type}.
   *
   * <p>Some of these relationships represent type errors; however, the graph needs to contain those
   * edges for safe disambiguation. In particular, code generated from other languages (e.g TS)
   * might have more flexible subtyping rules.
   */
  private ImmutableList<Integer> ownAncestorInterfacesOf(ObjectType type) {
    FunctionType ctorType = type.getConstructor();
    if (ctorType == null) {
      return ImmutableList.of();
    }

    ImmutableList.Builder<Integer> ancestors = ImmutableList.builder();
    for (JSType ancestor :
        Iterables.concat(
            ctorType.getExtendedInterfaces(), ctorType.getOwnImplementedInterfaces())) {
      ancestors.add(this.serializeType(ancestor));
    }
    return ancestors.build();
  }

  /**
   * Inserts dummy pointers corresponding to all {@link PrimitiveType}s in the type pool.
   *
   * <p>These types will never correspond to an actual {@link TypeProto}. Instead, all normal {@link
   * Integer} offsets into the pool are offset by a number equivalent to the number of {@link
   * PrimitiveType} enum elements.
   */
  private void seedCachesWithAxiomaticTypes() {
    checkState(this.seenTypeRecords.isEmpty());

    // Load all the axiomatic records in the right order without any types.
    for (Color axiomatic : TypePointers.OFFSET_TO_AXIOMATIC_COLOR) {
      int index = this.seenTypeRecords.size();
      SeenTypeRecord record = new SeenTypeRecord(axiomatic.getId(), index);
      this.seenTypeRecords.put(axiomatic.getId(), record);
    }

    // Add JSTypes corresponding to axiomatic IDs.
    JSTYPE_NATIVE_TO_AXIOMATIC_COLOR_MAP.forEach(
        (jstypeNative, axiomatic) ->
            this.getOrCreateRecord(axiomatic.getId(), this.registry.getNativeType(jstypeNative)));

    checkState(this.seenTypeRecords.size() == TypePointers.OFFSET_TO_AXIOMATIC_COLOR.size());
  }

  /** Checks that this instance is in a valid state. */
  private void checkValidLinearTime() {
    if (!this.serializationMode.runValidation()) {
      return;
    }

    final int totalTypeCount = this.seenTypeRecords.size();
    for (SeenTypeRecord seen : this.seenTypeRecords.values()) {
      int offset = seen.pointer;
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
    checkValidLinearTime();

    TypePool.Builder builder = TypePool.newBuilder();

    if (this.serializationMode.includeDebugInfo()) {
      TypePool.DebugInfo.Builder debugInfo = builder.getDebugInfoBuilder();
      this.invalidatingTypes
          .getMismatchLocations()
          .inverse() // Key by source ref to deduplicate the strings, which are pretty long.
          .asMap()
          .forEach(
              (location, types) ->
                  debugInfo
                      .addMismatchBuilder()
                      .setSourceRef(location.getLocation())
                      .addAllInvolvedColor(
                          types.stream()
                              .peek((t) -> checkState(!t.isUnionType(), t))
                              // Ensure all types are recorded before reconciliation.
                              .map(this::serializeType)
                              .distinct()
                              .sorted()
                              .collect(toImmutableList())));
    }

    this.state = State.GENERATING_POOL;

    for (SeenTypeRecord seen : this.seenTypeRecords.values()) {
      if (StandardColors.AXIOMATIC_COLORS.containsKey(seen.colorId)) {
        checkState(isAxiomatic(seen.pointer), "Missing .type for SeenTypeRecord %s", seen);
        continue;
      }
      builder.addType(
          (seen.unionMembers != null)
              ? this.reconcileUnionTypes(seen)
              : this.reconcileObjectTypes(seen));
    }

    for (Integer subtype : this.disambiguateEdges.keySet()) {
      for (Integer supertype : this.disambiguateEdges.get(subtype)) {
        builder.addDisambiguationEdges(
            SubtypingEdge.newBuilder().setSubtype(subtype).setSupertype(supertype));
      }
    }

    this.state = State.FINISHED;
    checkValidLinearTime();
    return builder.build();
  }

  /**
   * Returns a map from {@link ObjectTypeProto#getUuid()} to the originating {@link JSType}s.
   *
   * <p>Only intended to be used for debug logging.
   */
  ImmutableMultimap<String, JSType> getColorIdToJSTypeMapForDebugging() {
    // note: returns JSType values instead of String values, even though all that's needed for
    // debugging are the strings, to avoid the memory overhead of calculating all string
    // representations at once
    ImmutableMultimap.Builder<String, JSType> colorIdToTypes =
        ImmutableMultimap.<String, JSType>builder().orderKeysBy(naturalOrder());
    this.typeToRecordCache.forEach(
        (jstype, record) -> colorIdToTypes.put(record.colorId.toString(), jstype));
    return colorIdToTypes.build();
  }

  private static final class SeenTypeRecord {
    final ColorId colorId;
    final int pointer;

    /**
     * 2021-05-25: It's faster to build a list and reconcile duplicates than to deduplicate using a
     * set. The likely cause is that the caches head of these lists have low hit-rates for unions,
     * and that union reconciliation doesn't actually look at most entries in this list.
     */
    final ArrayList<JSType> jstypes = new ArrayList<>();

    @Nullable ImmutableSet<SeenTypeRecord> unionMembers = null;

    SeenTypeRecord(ColorId colorId, int pointer) {
      this.colorId = colorId;
      this.pointer = pointer;
    }
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
