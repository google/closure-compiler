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
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
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
 */
public final class ConvertTypesToColors implements CompilerPass {
  private final AbstractCompiler compiler;

  public ConvertTypesToColors(AbstractCompiler compiler) {
    this.compiler = compiler;
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
        SerializeTypesCallback.create(compiler, SerializationOptions.INCLUDE_DEBUG_INFO);
    NodeTraversal.traverse(compiler, externsAndJsRoot, serializeJstypes);

    // Step 2: Remove types and add colors
    TypePool typePool = serializeJstypes.generateTypePool();
    ColorDeserializer deserializer = ColorDeserializer.buildFromTypePool(typePool);
    NodeTraversal.traverse(
        compiler,
        externsAndJsRoot,
        new ColorAst(deserializer, serializeJstypes.getTypePointersByJstype()));
  }
}
