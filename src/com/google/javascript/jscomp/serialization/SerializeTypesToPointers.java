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
import static com.google.common.collect.ImmutableSortedSet.toImmutableSortedSet;
import static com.google.common.collect.Streams.stream;
import static com.google.javascript.jscomp.serialization.TypePointers.isAxiomatic;
import static com.google.javascript.jscomp.serialization.TypePointers.trimOffset;
import static java.util.Comparator.naturalOrder;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.InvalidatingTypes;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.TypeMismatch;
import com.google.javascript.jscomp.colors.ColorId;
import com.google.javascript.jscomp.diagnostic.LogFile;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import java.util.IdentityHashMap;

/** Grab a TypePointer for each JSType on the AST and log information about the pointers. */
final class SerializeTypesToPointers {

  private final AbstractCompiler compiler;
  private final JSTypeReconserializer jstypeReconserializer;
  private final IdentityHashMap<JSType, TypePointer> typePointersByJstype = new IdentityHashMap<>();
  private static final Gson GSON = new Gson();
  private TypePool typePool = null;

  private SerializeTypesToPointers(
      AbstractCompiler compiler, JSTypeReconserializer jstypeReconserializer) {
    this.compiler = compiler;
    this.jstypeReconserializer = jstypeReconserializer;
  }

  static SerializeTypesToPointers create(
      AbstractCompiler compiler,
      StringPool.Builder stringPoolBuilder,
      SerializationOptions serializationOptions) {
    InvalidatingTypes invalidatingTypes =
        new InvalidatingTypes.Builder(compiler.getTypeRegistry())
            .addAllTypeMismatches(compiler.getTypeMismatches())
            .build();
    JSTypeReconserializer jsTypeReconserializer =
        JSTypeReconserializer.create(
            compiler.getTypeRegistry(), invalidatingTypes, stringPoolBuilder, serializationOptions);
    return new SerializeTypesToPointers(compiler, jsTypeReconserializer);
  }

  void gatherTypesOnAst(Node root) {
    checkState(this.typePool == null, "Cannot call process() twice");
    NodeTraversal.traverse(this.compiler, root, new Callback());

    // these types are only used when debug logging is enabled, but we always serialize them as not
    // to have a different TypePool with and without debug logging.
    for (TypeMismatch mismatch : compiler.getTypeMismatches()) {
      jstypeReconserializer.serializeType(mismatch.getFound());
      jstypeReconserializer.serializeType(mismatch.getRequired());
    }

    this.typePool = jstypeReconserializer.generateTypePool();

    logSerializationDebugInfo(this.jstypeReconserializer, this.typePool);
  }

  private class Callback extends AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      JSType type = n.getJSType();
      if (type != null && !typePointersByJstype.containsKey(type)) {
        typePointersByJstype.put(type, jstypeReconserializer.serializeType(type));
      }
    }
  }

  IdentityHashMap<JSType, TypePointer> getTypePointersByJstype() {
    return typePointersByJstype;
  }

  TypePool getTypePool() {
    return typePool;
  }

  private void logSerializationDebugInfo(JSTypeReconserializer serializer, TypePool typePool) {
    // Log information about how the JSTypes correspond to the colors. This may be useful later on
    // in optimizations.
    try (LogFile log = this.compiler.createOrReopenLog(this.getClass(), "object_uuids.log")) {
      log.log(() -> GSON.toJson(serializer.getColorIdToJSTypeMapForDebugging().asMap()));
    }

    // Log type mismatches, which contribute to the definition of an "invalidating" type
    try (LogFile log = this.compiler.createOrReopenLog(this.getClass(), "mismatches.log")) {
      log.log(
          () -> GSON.toJson(logTypeMismatches(compiler.getTypeMismatches(), serializer, typePool)));
    }
  }

  /**
   * Serializes a type not necessarily attached to an AST node.
   *
   * <p>Not part of the main API for this callback. For use when serializing additional types for
   * debug logging.
   */
  private ImmutableSet<TypeMismatchJson> logTypeMismatches(
      Iterable<TypeMismatch> typeMismatches, JSTypeReconserializer serializer, TypePool typePool) {
    return stream(typeMismatches)
        .map(mismatch -> TypeMismatchJson.create(mismatch, serializer, typePool))
        .collect(toImmutableSortedSet(naturalOrder()));
  }

  private static final class TypeMismatchJson implements Comparable<TypeMismatchJson> {
    final String location;
    final String foundColorId;
    final String requiredColorId;

    TypeMismatchJson(TypeMismatch x, ColorId found, ColorId required) {
      this.location = x.getLocation().getLocation();
      this.foundColorId = found.toString();
      this.requiredColorId = required.toString();
    }

    static TypeMismatchJson create(
        TypeMismatch x, JSTypeReconserializer serializer, TypePool typePool) {
      TypePointer foundPointer = serializer.serializeType(x.getFound());
      TypePointer requiredPointer = serializer.serializeType(x.getRequired());

      return new TypeMismatchJson(
          x, typePointerToId(foundPointer, typePool), typePointerToId(requiredPointer, typePool));
    }

    /**
     * Returns the unique ID of this pointer if in the type pool.
     *
     * <p>The given type may not be in the type pool because the type pool was generated based on
     * all types reachable from the AST, while a TypeMismatch may contain a type in dead code no
     * longer reachable from the AST.
     */
    private static ColorId typePointerToId(TypePointer typePointer, TypePool typePool) {
      int poolOffset = typePointer.getPoolOffset();
      if (isAxiomatic(poolOffset)) {
        return TypePointers.OFFSET_TO_AXIOMATIC_COLOR.get(poolOffset).getId();
      }

      TypeProto typeProto = typePool.getTypeList().get(trimOffset(poolOffset));
      switch (typeProto.getKindCase()) {
        case UNION:
          return ColorId.union(
              typeProto.getUnion().getUnionMemberList().stream()
                  .map(pointer -> typePointerToId(pointer, typePool))
                  .collect(toImmutableSet()));
        case OBJECT:
          return ColorId.fromBytes(typeProto.getObject().getUuid());
        case KIND_NOT_SET:
          break;
      }
      throw new AssertionError("Unrecognized TypeProto " + typeProto);
    }

    @Override
    public int compareTo(TypeMismatchJson x) {
      return ComparisonChain.start()
          .compare(this.foundColorId, x.foundColorId)
          .compare(this.requiredColorId, x.requiredColorId)
          .compare(this.location, x.location)
          .result();
    }
  }
}
