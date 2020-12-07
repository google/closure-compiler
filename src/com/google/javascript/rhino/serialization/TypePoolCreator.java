package com.google.javascript.rhino.serialization;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.serialization.NativeType;
import com.google.javascript.jscomp.serialization.SubtypingEdge;
import com.google.javascript.jscomp.serialization.Type;
import com.google.javascript.jscomp.serialization.TypePointer;
import com.google.javascript.jscomp.serialization.TypePointer.TypeCase;
import com.google.javascript.jscomp.serialization.TypePool;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Class that aids in building a pool of serialized types
 *
 * <p>This class's purpose is to build the pool of all types and construct pointers into that pool.
 *
 * <p>To add a new type to the pool or get a pointer to a type already in the pool, call {@link
 * #typeToPointer(Object, Supplier)}. Once all types have been registered call {@link
 * #generateTypePool()}.
 */
public class TypePoolCreator<T> {
  private Map<SerializableType<T>, Integer> seenSerializableTypes = new HashMap<>();
  private List<SerializableType<T>> toSerialize = new ArrayList<>();
  private final Multimap<TypePointer, TypePointer> disambiguateEdges = LinkedHashMultimap.create();
  private final Set<NativeType> invalidatingNatives = new LinkedHashSet<>();
  private State state = State.COLLECTING_TYPES;
  private final SerializationOptions serializationOptions;

  enum State {
    COLLECTING_TYPES,
    GENERATING_POOL,
    FINISHED,
  }

  private TypePoolCreator(SerializationOptions serializationOptions) {
    this.serializationOptions = serializationOptions;
  }

  /** Creates a new TypePoolCreator */
  public static <T> TypePoolCreator<T> create(SerializationOptions serializationOptions) {
    TypePoolCreator<T> serializer = new TypePoolCreator<>(serializationOptions);
    serializer.checkValid();
    return serializer;
  }

  /** Checks that this instance is in a valid state. */
  private void checkValid() {
    if (!SerializationOptions.INCLUDE_DEBUG_INFO_AND_EXPENSIVE_VALIDITY_CHECKS.equals(
        serializationOptions)) {
      return;
    }
    final int totalTypeCount = seenSerializableTypes.size();
    for (Integer pointer : seenSerializableTypes.values()) {
      checkState(pointer >= 0);
      checkState(
          pointer <= totalTypeCount,
          "Found invalid pointer %s, out of a total of %s user-defined types",
          pointer,
          totalTypeCount);
    }
    switch (state) {
      case COLLECTING_TYPES:
        checkState(seenSerializableTypes.size() == toSerialize.size());
        for (SerializableType<T> astType : toSerialize) {
          checkState(seenSerializableTypes.containsKey(astType));
        }
        for (SerializableType<T> astType : seenSerializableTypes.keySet()) {
          checkState(
              toSerialize.contains(astType),
              "Type %s not present in toSerialize, whose contents are: %s",
              astType,
              toSerialize);
          int serializeOrder = toSerialize.indexOf(astType);
          int seenPointer = seenSerializableTypes.get(astType);
          checkState(
              serializeOrder == seenPointer,
              "For type %s, serializeOrder (%s) != pointer (%s)",
              astType,
              serializeOrder,
              seenPointer);
        }
        break;
      case GENERATING_POOL:
        for (SerializableType<T> astType : toSerialize) {
          checkState(seenSerializableTypes.containsKey(astType));
        }
        break;
      case FINISHED:
        checkState(toSerialize.isEmpty());
        break;
    }
  }

  /**
   * Generates a "type-pool" representing all the types that this class has encountered through
   * calls to {@link #typeToPointer(Object, Supplier)}. After generation, no new types can be added,
   * so subsequent calls to {@link #typeToPointer(Object, Supplier)} can only be used to retrieve
   * pointers to existing types in the type pool.
   */
  public TypePool generateTypePool() {
    checkState(state == State.COLLECTING_TYPES);
    checkValid();
    state = State.GENERATING_POOL;
    TypePool.Builder builder = TypePool.newBuilder();
    for (int i = 0; !toSerialize.isEmpty(); i++) {
      SerializableType<T> astType = toSerialize.remove(0);
      checkState(seenSerializableTypes.get(astType) == i);
      Type serializedType = astType.serializeToConcrete();
      builder.addType(serializedType);
    }
    for (TypePointer subtype : disambiguateEdges.keySet()) {
      for (TypePointer supertype : disambiguateEdges.get(subtype)) {
        builder.addDisambiguationEdges(
            SubtypingEdge.newBuilder().setSubtype(subtype).setSupertype(supertype));
      }
    }
    TypePool pool = builder.addAllInvalidatingNative(this.invalidatingNatives).build();
    state = State.FINISHED;
    checkValid();
    seenSerializableTypes = ImmutableMap.copyOf(seenSerializableTypes);
    toSerialize = ImmutableList.of();
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
  public TypePointer typeToPointer(T t, Supplier<Type> serialize) {
    checkValid();
    SerializableType<T> idType = SerializableType.create(t, serialize);
    final int poolOffset;
    if (seenSerializableTypes.containsKey(idType)) {
      poolOffset = seenSerializableTypes.get(idType);
    } else {
      checkState(State.COLLECTING_TYPES == state || State.GENERATING_POOL == state);
      poolOffset = seenSerializableTypes.size();
      checkState(null == seenSerializableTypes.put(idType, poolOffset));
      toSerialize.add(idType);
      checkValid();
    }

    String descriptionForDebug = "";
    if (!SerializationOptions.SKIP_DEBUG_INFO.equals(serializationOptions)) {
      descriptionForDebug = t.toString();
    }

    return TypePointer.newBuilder()
        .setPoolOffset(poolOffset)
        .setDescriptionForDebug(descriptionForDebug)
        .build();
  }

  /**
   * Adds an edge from the given subtype to the given supertype if both are user-defined types and
   * not native.
   */
  public void addDisambiguationEdge(TypePointer subtype, TypePointer supertype) {
    if (subtype.getTypeCase().equals(TypeCase.POOL_OFFSET)
        && supertype.getTypeCase().equals(TypeCase.POOL_OFFSET)) {
      this.disambiguateEdges.put(subtype, supertype);
    }
  }

  public void registerInvalidatingNative(NativeType invalidatingType) {
    this.invalidatingNatives.add(invalidatingType);
  }
}
