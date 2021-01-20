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

import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.InvalidatingTypes;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.serialization.SerializationOptions;
import com.google.javascript.rhino.serialization.TypePoolCreator;
import java.util.IdentityHashMap;

/** Grab a TypePointer for each JSType on the AST. */
final class SerializeTypesCallback extends AbstractPostOrderCallback {

  private final TypePoolCreator<JSType> typePoolCreator;
  private final JSTypeSerializer jstypeSerializer;
  private final IdentityHashMap<JSType, TypePointer> typePointersByJstype = new IdentityHashMap<>();

  private SerializeTypesCallback(
      TypePoolCreator<JSType> typePoolCreator, JSTypeSerializer jstypeSerializer) {
    this.typePoolCreator = typePoolCreator;
    this.jstypeSerializer = jstypeSerializer;
  }

  static SerializeTypesCallback create(
      AbstractCompiler compiler, SerializationOptions serializationOptions) {
    TypePoolCreator<JSType> typePoolCreator = TypePoolCreator.create(serializationOptions);
    InvalidatingTypes invalidatingTypes =
        new InvalidatingTypes.Builder(compiler.getTypeRegistry())
            .addAllTypeMismatches(compiler.getTypeMismatches())
            .build();
    JSTypeSerializer jsTypeSerializer =
        JSTypeSerializer.create(
            typePoolCreator, compiler.getTypeRegistry(), invalidatingTypes, serializationOptions);
    return new SerializeTypesCallback(typePoolCreator, jsTypeSerializer);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    JSType type = n.getJSType();
    if (type != null && !typePointersByJstype.containsKey(type)) {
      typePointersByJstype.put(type, jstypeSerializer.serializeType(type));
    }
  }

  IdentityHashMap<JSType, TypePointer> getTypePointersByJstype() {
    return typePointersByJstype;
  }

  TypePool generateTypePool() {
    return typePoolCreator.generateTypePool();
  }
}
