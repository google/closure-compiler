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

import static com.google.common.collect.ImmutableSortedSet.toImmutableSortedSet;
import static com.google.common.collect.Streams.stream;
import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.TypeMismatch;
import com.google.javascript.jscomp.diagnostic.LogFile;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.serialization.SerializationOptions;
import java.util.IdentityHashMap;

/**
 * Pass to convert JSType objects from TypeChecking that are attached to the AST into Color objects
 * whose sole use is to enable running optimizations.
 *
 * <p>Eventually, we anticipate this pass to run at the beginning of optimizations, and leave a
 * representation of the types as needed for optimizations on the AST.
 *
 * <p>This pass is also responsible for logging debug information that needs to know about both
 * JSType objects and their corresponding colors.
 */
public final class ConvertTypesToColors implements CompilerPass {
  private final AbstractCompiler compiler;
  private final SerializationOptions serializationOptions;
  private static final Gson GSON = new Gson();

  public ConvertTypesToColors(
      AbstractCompiler compiler, SerializationOptions serializationOptions) {
    this.compiler = compiler;
    this.serializationOptions = serializationOptions;
  }

  private static class ColorAst extends AbstractPostOrderCallback {
    private final ColorDeserializer deserializer;
    private final IdentityHashMap<JSType, TypePointer> typePointersByJstype;

    ColorAst(
        ColorDeserializer deserializer, IdentityHashMap<JSType, TypePointer> typePointersByJstype) {
      this.deserializer = deserializer;
      this.typePointersByJstype = typePointersByJstype;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      JSType oldType = n.getJSType();
      if (oldType != null && typePointersByJstype.containsKey(oldType)) {
        n.setColor(deserializer.pointerToColor(typePointersByJstype.get(oldType)));
      }
      if (n.getJSTypeBeforeCast() != null) {
        // used by FunctionInjector and InlineVariables when inlining as a hint that a node has a
        // more specific color
        n.setColorFromTypeCast();
      }
    }
  }

  @Override
  public void process(Node externs, Node root) {
    // Step 1: Serialize types
    Node externsAndJsRoot = root.getParent();
    SerializeTypesCallback serializeJstypes =
        SerializeTypesCallback.create(compiler, this.serializationOptions);
    JSTypeSerializer serializer = serializeJstypes.getSerializer();
    NodeTraversal.traverse(compiler, externsAndJsRoot, serializeJstypes);
    for (TypeMismatch mismatch : compiler.getTypeMismatches()) {
      serializer.serializeType(mismatch.getFound());
      serializer.serializeType(mismatch.getRequired());
    }

    // Step 2: Remove types and add colors
    TypePool typePool = serializeJstypes.generateTypePool();
    StringPool stringPool = serializeJstypes.generateStringPool();
    ColorDeserializer deserializer = ColorDeserializer.buildFromTypePool(typePool, stringPool);
    NodeTraversal.traverse(
        compiler,
        externsAndJsRoot,
        new ColorAst(deserializer, serializeJstypes.getTypePointersByJstype()));

    compiler.setColorRegistry(deserializer.getRegistry());

    // Log information about how the JSTypes correspond to the colors. This may be useful later on
    // in optimizations.
    try (LogFile log = this.compiler.createOrReopenLog(this.getClass(), "object_uuids.log")) {
      log.log(() -> GSON.toJson(serializer.getObjectUuidMapForDebugging()));
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
      Iterable<TypeMismatch> typeMismatches, JSTypeSerializer serializer, TypePool typePool) {
    return stream(typeMismatches)
        .map(mismatch -> TypeMismatchJson.create(mismatch, serializer, typePool))
        .collect(toImmutableSortedSet(naturalOrder()));
  }

  private static final class TypeMismatchJson implements Comparable<TypeMismatchJson> {
    final String found;
    final String required;
    final String location;
    final String foundUuid;
    final String requiredUuid;

    TypeMismatchJson(TypeMismatch x, String foundUuid, String requiredUuid) {
      this.found = x.getFound().toString();
      this.required = x.getRequired().toString();
      this.location = x.getLocation().getLocation();
      this.foundUuid = foundUuid;
      this.requiredUuid = requiredUuid;
    }

    static TypeMismatchJson create(TypeMismatch x, JSTypeSerializer serializer, TypePool typePool) {
      TypePointer foundPointer = serializer.serializeType(x.getFound());
      TypePointer requiredPointer = serializer.serializeType(x.getRequired());

      return new TypeMismatchJson(
          x, typePointerToId(foundPointer, typePool), typePointerToId(requiredPointer, typePool));
    }

    /**
     * Returns the unique ID of this pointer if in the type pool, or a debugging string otherwise
     *
     * <p>The given type may not be in the type pool because the type pool was generated based on
     * all types reachable from the AST, while a TypeMismatch may contain a type in dead code no
     * longer reachable from the AST.
     */
    private static String typePointerToId(TypePointer typePointer, TypePool typePool) {
      int poolOffset = typePointer.getPoolOffset();
      if (poolOffset < JSTypeSerializer.PRIMITIVE_POOL_SIZE) {
        // TODO(b/169090854): standardize the PrimitiveType UUIDs between here and ColorRegistry
        return "<native type>: " + PrimitiveType.forNumber(poolOffset);
      }

      int adjustedOffset = typePointer.getPoolOffset() - JSTypeSerializer.PRIMITIVE_POOL_SIZE;

      TypeProto typeProto = typePool.getTypeList().get(adjustedOffset);
      switch (typeProto.getKindCase()) {
        case UNION:
          return typeProto.getUnion().getUnionMemberList().stream()
              .map(pointer -> typePointerToId(pointer, typePool))
              .distinct()
              .collect(joining(","));
        case OBJECT:
          return typeProto.getObject().getUuid();
        case KIND_NOT_SET:
          break;
      }
      throw new AssertionError("Unrecognized TypeProto " + typeProto);
    }

    @Override
    public int compareTo(TypeMismatchJson x) {
      return ComparisonChain.start()
          .compare(this.found, x.found)
          .compare(this.required, x.required)
          .compare(this.location, x.location)
          .result();
    }
  }
}
