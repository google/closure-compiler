/*
 * Copyright 2021 The Closure Compiler Authors.
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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.javascript.jscomp.base.JSCompObjects.identical;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.GwtIncompatible;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerInput;
import com.google.javascript.jscomp.JsAst;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.colors.ColorRegistry;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import java.util.LinkedHashMap;
import javax.annotation.Nullable;

/**
 * Class that deserializes a TypedAst proto into the JSCompiler AST structure.
 *
 * <p>NOTE: This is a work in progress, and incomplete.
 */
@GwtIncompatible("protobuf.lite")
public final class TypedAstDeserializer {

  private final TypedAst typedAst;
  private final ColorPool.ShardView colorPoolShard;
  private final StringPool stringPool;
  private final LinkedHashMap<InputId, CompilerInput> inputsById = new LinkedHashMap<>();
  // indices into this list correspond to AstNode::getSourceFile() - 1, as '0' is reserved for an
  // unset file and ImmutableLists don't accept null.
  private final ImmutableList<SourceFile> sourceFiles;

  private TypedAstDeserializer(
      TypedAst typedAst,
      StringPool stringPool,
      ColorPool.ShardView colorPoolShard,
      ImmutableList<SourceFile> sourceFiles) {
    this.typedAst = typedAst;
    this.colorPoolShard = colorPoolShard;
    this.stringPool = stringPool;
    this.sourceFiles = sourceFiles;
  }

  /** Transforms a given TypedAst object into a compiler AST (represented as a IR.root node) */
  public static DeserializedAst deserialize(AbstractCompiler compiler, TypedAst typedAst) {
    StringPool stringPool = StringPool.fromProto(typedAst.getStringPool());

    ColorPool.Builder colorPoolBuilder = ColorPool.builder();
    compiler.initRuntimeLibraryTypedAsts(colorPoolBuilder);

    ImmutableList<SourceFile> sourceFiles =
        typedAst.getSourceFilePool().getSourceFileList().stream()
            .map(SourceFile::fromProto)
            .collect(toImmutableList());
    TypedAstDeserializer deserializer =
        new TypedAstDeserializer(
            typedAst,
            stringPool,
            colorPoolBuilder.addShard(typedAst.getTypePool(), stringPool),
            sourceFiles);
    ColorPool colorPool = colorPoolBuilder.build();
    Node root = deserializer.deserializeToRoot();

    ImmutableSet<String> externProperties =
        typedAst.hasExternsSummary()
            ? typedAst.getExternsSummary().getPropNamePtrList().stream()
                .map(stringPool::get)
                .collect(toImmutableSet())
            : null;

    return DeserializedAst.create(
        root,
        colorPool.getRegistry(),
        ImmutableMap.copyOf(deserializer.inputsById),
        externProperties);
  }

  /** The result of deserializing a given TypedAst object */
  @AutoValue
  public abstract static class DeserializedAst {
    public abstract Node getRoot();

    public abstract ColorRegistry getColorRegistry();

    public abstract ImmutableMap<InputId, CompilerInput> getInputsById();

    @Nullable
    public abstract ImmutableSet<String> getExternProperties();

    private static DeserializedAst create(
        Node root,
        ColorRegistry colorRegistry,
        ImmutableMap<InputId, CompilerInput> inputsById,
        ImmutableSet<String> externProperties) {
      return new AutoValue_TypedAstDeserializer_DeserializedAst(
          root, colorRegistry, inputsById, externProperties);
    }
  }

  private Node deserializeToRoot() {

    Node externRoot = IR.root();
    Node codeRoot = IR.root();
    for (LazyAst ast : typedAst.getExternAstList()) {
      externRoot.addChildToBack(deserializeScriptNode(ast, ast.getSourceFile()));
    }
    for (LazyAst ast : typedAst.getCodeAstList()) {
      codeRoot.addChildToBack(deserializeScriptNode(ast, ast.getSourceFile()));
    }
    return IR.root(externRoot, codeRoot);
  }

  private Node deserializeScriptNode(LazyAst fileProto, int sourceFilePointer) {
    SourceFile file = this.sourceFiles.get(sourceFilePointer - 1);
    ScriptNodeDeserializer deserializer =
        new ScriptNodeDeserializer(
            fileProto, this.stringPool, this.colorPoolShard, this.sourceFiles);
    JsAst ast = new JsAst(file, deserializer::deserializeNew);
    Node node = ast.getAstRoot(null); // TODO(b/186431141) Don't allow passing null here.
    checkState(identical(node.getStaticSourceFile(), file));

    this.inputsById.put(ast.getInputId(), new CompilerInput(ast));
    return node;
  }
}
