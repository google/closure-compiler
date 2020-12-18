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

package com.google.javascript.rhino.serialization;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.serialization.NativeType;
import com.google.javascript.jscomp.serialization.SubtypingEdge;
import com.google.javascript.jscomp.serialization.TypePointer;
import com.google.javascript.jscomp.serialization.TypePool;
import com.google.javascript.jscomp.serialization.TypeProto;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
public final class TypePoolCreator<T> {

  private State state = State.COLLECTING_TYPES;
  private final LinkedHashMap<T, SeenTypeRecord> seenSerializableTypes = new LinkedHashMap<>();
  private final Multimap<TypePointer, TypePointer> disambiguateEdges = LinkedHashMultimap.create();
  private final LinkedHashSet<NativeType> invalidatingNatives = new LinkedHashSet<>();
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
        this.serializationOptions)) {
      return;
    }

    final int totalTypeCount = this.seenSerializableTypes.size();
    for (SeenTypeRecord seen : this.seenSerializableTypes.values()) {
      checkState(seen.pointer.getValueCase().equals(TypePointer.ValueCase.POOL_OFFSET));
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
   * calls to {@link #typeToPointer(Object, Supplier)}.
   *
   * <p>After generation, no new types can be added, so subsequent calls to {@link
   * #typeToPointer(Object, Supplier)} can only be used to retrieve pointers to existing types in
   * the type pool.
   */
  public TypePool generateTypePool() {
    checkState(this.state == State.COLLECTING_TYPES);
    checkValid();
    this.state = State.GENERATING_POOL;

    TypePool.Builder builder = TypePool.newBuilder();
    for (SeenTypeRecord seen : this.seenSerializableTypes.values()) {
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
  public TypePointer typeToPointer(T t, Supplier<TypeProto> serialize) {
    checkValid();

    SeenTypeRecord existing = this.seenSerializableTypes.get(t);
    if (existing != null) {
      return existing.pointer;
    }

    checkState(State.COLLECTING_TYPES == this.state || State.GENERATING_POOL == this.state);

    TypePointer.Builder pointer =
        TypePointer.newBuilder().setPoolOffset(this.seenSerializableTypes.size());
    if (!SerializationOptions.SKIP_DEBUG_INFO.equals(this.serializationOptions)) {
      pointer.setDebugInfo(TypePointer.DebugInfo.newBuilder().setDescription(t.toString()));
    }

    SeenTypeRecord record = new SeenTypeRecord(pointer.build());
    this.seenSerializableTypes.put(t, record);
    // Serialize after the pointer is in the pool in case serialization requires a pool lookup.
    record.type = serialize.get();

    checkValid();
    return record.pointer;
  }

  /**
   * Adds an edge from the given subtype to the given supertype if both are user-defined types and
   * not native.
   */
  public void addDisambiguationEdge(TypePointer subtype, TypePointer supertype) {
    checkState(this.state == State.COLLECTING_TYPES);

    if (subtype.getValueCase().equals(TypePointer.ValueCase.POOL_OFFSET)
        && supertype.getValueCase().equals(TypePointer.ValueCase.POOL_OFFSET)) {
      this.disambiguateEdges.put(subtype, supertype);
    }
  }

  public void registerInvalidatingNative(NativeType invalidatingType) {
    checkState(this.state == State.COLLECTING_TYPES);
    this.invalidatingNatives.add(invalidatingType);
  }

  private static final class SeenTypeRecord {
    final TypePointer pointer;
    TypeProto type;

    SeenTypeRecord(TypePointer pointer) {
      this.pointer = pointer;
    }
  }
}
