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

import com.google.common.annotations.GwtIncompatible;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.InvalidatingTypes;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.TypeMismatch;
import com.google.javascript.jscomp.colors.ColorId;
import com.google.javascript.jscomp.diagnostic.LogFile;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import org.jspecify.nullness.Nullable;

/**
 * Grab an integer TypePool pointer for each JSType on the AST and log information about the
 * pointers.
 */
@GwtIncompatible
final class SerializeTypesToPointers {

  private final AbstractCompiler compiler;
  private final JSTypeReconserializer jstypeReconserializer;
  private final LinkedHashSet<String> propertiesReferencedInAst;
  private final IdentityHashMap<JSType, Integer> typePointersByJstype = new IdentityHashMap<>();
  private static final Gson GSON = new Gson();
  private @Nullable TypePool typePool = null;

  private SerializeTypesToPointers(
      AbstractCompiler compiler,
      JSTypeReconserializer jstypeReconserializer,
      LinkedHashSet<String> propertiesReferencedInAst) {
    this.compiler = compiler;
    this.jstypeReconserializer = jstypeReconserializer;
    this.propertiesReferencedInAst = propertiesReferencedInAst;
  }

  static SerializeTypesToPointers create(
      AbstractCompiler compiler,
      StringPool.Builder stringPoolBuilder,
      SerializationOptions serializationOptions) {
    InvalidatingTypes invalidatingTypes =
        new InvalidatingTypes.Builder(compiler.getTypeRegistry())
            .addAllTypeMismatches(compiler.getTypeMismatches())
            .build();

    // this set requires access to the externs and src ASTs, so can't be populated yet.
    LinkedHashSet<String> propertiesReferencedInAst = new LinkedHashSet<>();
    JSTypeReconserializer jsTypeReconserializer =
        JSTypeReconserializer.create(
            compiler.getTypeRegistry(),
            invalidatingTypes,
            stringPoolBuilder,
            propertiesReferencedInAst::contains,
            serializationOptions);
    return new SerializeTypesToPointers(compiler, jsTypeReconserializer, propertiesReferencedInAst);
  }

  void gatherTypesOnAst(Node root) {
    checkState(this.typePool == null, "Cannot call process() twice");
    NodeTraversal.traverse(this.compiler, root, new PropertySearchCallback());
    NodeTraversal.traverse(this.compiler, root, new TypeSearchCallback());

    // these types are only used when debug logging is enabled, but we always serialize them as not
    // to have a different TypePool with and without debug logging.
    for (TypeMismatch mismatch : compiler.getTypeMismatches()) {
      jstypeReconserializer.serializeType(mismatch.getFound());
      jstypeReconserializer.serializeType(mismatch.getRequired());
    }

    this.typePool = jstypeReconserializer.generateTypePool();

    logSerializationDebugInfo(this.jstypeReconserializer, this.typePool);
  }

  /**
   * Finds all unquoted property names referenced outside @typeSummary files
   *
   * <p>Only property names found in this traversal will be serialized onto a color's list of "own
   * properties". Properties referenced only inside `@typeSummary` files may be excluded because
   * they don't matter for optimization of this library's srcs.
   */
  private final class PropertySearchCallback implements NodeTraversal.Callback {
    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      return !n.isScript() || !NodeUtil.isFromTypeSummary(n);
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case GETPROP: // "name" from (someObject.name)
        case OPTCHAIN_GETPROP: // "name" from (someObject?.name)
          propertiesReferencedInAst.add(n.getString());
          break;
        case STRING_KEY: // "name" from obj = {name: 0}
        case MEMBER_FUNCTION_DEF: // "name" from class C { name() {} }
        case MEMBER_FIELD_DEF: // "name" from class C { name = 0; }
        case GETTER_DEF: // "name" from class C { get name() {} }
        case SETTER_DEF: // "name" from class C { set name(n) {} }
          if (!n.isQuotedStringKey()) {
            propertiesReferencedInAst.add(n.getString());
          }
          break;
        default:
      }
    }
  }

  private final class TypeSearchCallback implements NodeTraversal.Callback {
    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      return !n.isScript() || !NodeUtil.isFromTypeSummary(n);
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isRoot()) {
        // the ROOT node is given the 'global this' type for use during typechecking, but this is
        // not needed for optimizations. (The 'global this' type will still be serialized if
        // referenced in actual code though.)
        return;
      }
      JSType type = n.getJSType();
      if (type != null) {
        typePointersByJstype.computeIfAbsent(type, jstypeReconserializer::serializeType);
      }
    }
  }

  IdentityHashMap<JSType, Integer> getTypePointersByJstype() {
    return typePointersByJstype;
  }

  TypePool getTypePool() {
    return typePool;
  }

  private void logSerializationDebugInfo(JSTypeReconserializer serializer, TypePool typePool) {
    // Log information about how the JSTypes correspond to the colors. This may be useful later on
    // in optimizations.
    try (LogFile log = this.compiler.createOrReopenLog(this.getClass(), "object_uuids.log")) {
      if (log.isLogging()) {
        ImmutableMap<String, Collection<JSType>> allSerializedTypes =
            serializer.getColorIdToJSTypeMapForDebugging().asMap();
        // Stream json writing here rather than building up the entire json representation at once
        // because the latter used to cause OOMs.
        log.logJson(new StreamObjectUuidsJson(allSerializedTypes));
      }
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
  private ImmutableSortedSet<TypeMismatchJson> logTypeMismatches(
      Iterable<TypeMismatch> typeMismatches, JSTypeReconserializer serializer, TypePool typePool) {
    return stream(typeMismatches)
        .map(mismatch -> TypeMismatchJson.create(mismatch, serializer, typePool))
        .collect(toImmutableSortedSet(naturalOrder()));
  }

  /**
   * Writes a JSON object whose keys are color ids and values are arrays of JSType strings.
   *
   * <p>Example: `{0: ['*', '?', 'None'], 10f34lksdf: ['SomeProtoCtor']}`
   */
  private static class StreamObjectUuidsJson implements LogFile.StreamedJsonProducer {
    private final ImmutableMap<String, Collection<JSType>> allSerializedTypes;

    StreamObjectUuidsJson(ImmutableMap<String, Collection<JSType>> allSerializedTypes) {
      this.allSerializedTypes = allSerializedTypes;
    }

    @Override
    public void writeJson(JsonWriter jsonWriter) throws IOException {
      jsonWriter.beginObject();
      for (Map.Entry<String, Collection<JSType>> entry : allSerializedTypes.entrySet()) {
        jsonWriter.name(entry.getKey()); // color id
        jsonWriter.beginArray();
        // sort JSTypes by string representation
        ArrayList<String> jstypes = new ArrayList<>();
        for (JSType jstype : entry.getValue()) {
          jstypes.add(jstype.toString());
        }
        Collections.sort(jstypes);
        for (String typeName : jstypes) { // all corresponding JSTypes
          jsonWriter.value(typeName);
        }
        jsonWriter.endArray();
      }
      jsonWriter.endObject();
    }
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
      int foundPointer = serializer.serializeType(x.getFound());
      int requiredPointer = serializer.serializeType(x.getRequired());

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
    private static ColorId typePointerToId(int poolOffset, TypePool typePool) {
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
