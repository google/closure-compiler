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

import com.google.common.base.Optional;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import java.util.IdentityHashMap;

/**
 * Pass to convert JSType objects from TypeChecking that are attached to the AST into Color objects
 * whose sole use is to enable running optimizations and delete all other references to JSTypes.
 *
 * <p>This pass is also responsible for logging debug information that needs to know about both
 * JSType objects and their corresponding colors, and for telling the compiler to prepare for
 * reading runtime libraries from precompiled TypedASTs instead of source.
 */
public final class ConvertTypesToColors implements CompilerPass {
  private final AbstractCompiler compiler;
  private final SerializationOptions serializationOptions;

  public ConvertTypesToColors(
      AbstractCompiler compiler, SerializationOptions serializationOptions) {
    this.compiler = compiler;
    this.serializationOptions = serializationOptions;
  }

  private static class RemoveTypes extends AbstractPostOrderCallback {

    RemoveTypes() {}

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      n.setJSType(null);
      n.setJSTypeBeforeCast(null);
      n.setDeclaredTypeExpression(null);
      n.setTypedefTypeProp(null);

      JSDocInfo jsdoc = n.getJSDocInfo();
      if (jsdoc != null) {
        n.setJSDocInfo(JSDocSerializer.convertJSDocInfoForOptimizations(jsdoc));
      }
    }
  }

  private static class RemoveTypesAndApplyColors extends RemoveTypes {
    private final ColorPool.ShardView colorPoolShard;
    private final IdentityHashMap<JSType, Integer> typePointersByJstype;

    RemoveTypesAndApplyColors(
        ColorPool.ShardView colorPoolShard, IdentityHashMap<JSType, Integer> typePointersByJstype) {
      super();
      this.colorPoolShard = colorPoolShard;
      this.typePointersByJstype = typePointersByJstype;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      JSType oldType = n.getJSType();
      JSType oldTypeBeforeCast = n.getJSTypeBeforeCast();

      super.visit(t, n, parent);

      if (oldType != null) {
        Integer pointer = typePointersByJstype.get(oldType);
        if (pointer != null) {
          n.setColor(colorPoolShard.getColor(pointer));
        }
      }

      if (oldTypeBeforeCast != null) {
        // used by FunctionInjector and InlineVariables when inlining as a hint that a node has a
        // more specific color
        n.setColorFromTypeCast();
      }
    }
  }

  @Override
  public void process(Node externs, Node root) {
    if (compiler.getLifeCycleStage().hasColorAndSimplifiedJSDoc()) {
      // Pass is a no-op if we already have optimization colors or have finished the checks phase
      return;
    }
    // NOTE(lharker): this pass could probably safely run after normalization, except that the
    // LifeCycleStage enum assumes that normalization implies "colors + simplified JSDoc"
    checkState(
        !compiler.getLifeCycleStage().isNormalized(), "Not expected to run after normalization");

    Node externsAndJsRoot = root.getParent();

    if (!compiler.hasTypeCheckingRun()) {
      NodeTraversal.traverse(compiler, externsAndJsRoot, new RemoveTypes());
      compiler.clearJSTypeRegistry();
      this.compiler.initRuntimeLibraryTypedAsts(Optional.absent());
      return;
    }

    StringPool.Builder stringPoolBuilder = StringPool.builder();
    SerializeTypesToPointers serializeJstypes =
        SerializeTypesToPointers.create(compiler, stringPoolBuilder, this.serializationOptions);
    serializeJstypes.gatherTypesOnAst(externsAndJsRoot);

    TypePool typePool = serializeJstypes.getTypePool();
    StringPool stringPool = stringPoolBuilder.build();

    ColorPool.Builder colorPoolBuilder = ColorPool.builder();
    this.compiler.initRuntimeLibraryTypedAsts(Optional.of(colorPoolBuilder));

    RemoveTypesAndApplyColors callback =
        new RemoveTypesAndApplyColors(
            colorPoolBuilder.addShard(typePool, stringPool),
            serializeJstypes.getTypePointersByJstype());

    ColorPool colorPool = colorPoolBuilder.build();
    NodeTraversal.traverse(compiler, externsAndJsRoot, callback);

    compiler.clearJSTypeRegistry();
    compiler.setColorRegistry(colorPool.getRegistry());
  }
}
