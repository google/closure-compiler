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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.javascript.jscomp.base.JSCompObjects.identical;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.colors.ColorRegistry;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.ExtensionRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/** Deserializes a list of TypedAst protos into the JSCompiler AST structure. */
@GwtIncompatible("protobuf.lite")
public final class TypedAstDeserializer {

  private final Mode mode;
  private final SourceFile syntheticExterns;
  private final Optional<ColorPool.Builder> colorPoolBuilder;

  private final LinkedHashMap<String, SourceFile> filePoolBuilder = new LinkedHashMap<>();
  private final LinkedHashMap<SourceFile, Supplier<Node>> typedAstFilesystem =
      new LinkedHashMap<>();
  private final ImmutableSet.Builder<String> externProperties = ImmutableSet.builder();
  private final ArrayList<ScriptNodeDeserializer> syntheticExternsDeserializers = new ArrayList<>();

  private TypedAstDeserializer(
      SourceFile syntheticExterns,
      Optional<ColorPool.Builder> existingColorPool,
      Mode mode,
      boolean includeTypeInformation) {
    this.syntheticExterns = syntheticExterns;
    this.mode = mode;
    if (includeTypeInformation) {
      this.colorPoolBuilder = Optional.of(existingColorPool.or(ColorPool.builder()));
    } else {
      this.colorPoolBuilder = Optional.absent();
    }
  }

  private enum Mode {
    RUNTIME_LIBRARY_ONLY,
    FULL_AST
  }

  /**
   * Transforms a given TypedAst.List stream into a compiler AST
   *
   * @param existingSourceFiles TypedAst nodes referencing a named SourceFile in this list will use
   *     the SourceFile object rather than creating a new SourceFile instance. AST references to
   *     SourceFiles not in this list will always create new SourceFile instances.
   * @param includeTypeInformation whether to deserialize the "Typed" half of a "TypedAst". If false
   *     ignores the TypePool, any TypePointers on AstNodes, and does not create a ColorRegistry
   */
  public static DeserializedAst deserializeFullAst(
      AbstractCompiler compiler,
      SourceFile syntheticExterns,
      ImmutableList<SourceFile> existingSourceFiles,
      InputStream typedAstsStream,
      boolean includeTypeInformation) {
    ImmutableMap<String, SourceFile> sourceFilesByName =
        existingSourceFiles.stream()
            .collect(toImmutableMap(SourceFile::getName, Function.identity()));
    return deserialize(
        compiler,
        syntheticExterns,
        sourceFilesByName,
        Optional.absent(),
        typedAstsStream,
        Mode.FULL_AST,
        includeTypeInformation);
  }

  /**
   * Transforms the special runtime library TypedAst
   *
   * @param colorPoolBuilder a ColorPool.Builder holding the colors on the full AST. We want to
   *     merge these colors with the runtime library colors to allow injecting runtime libraries
   *     without re-typechecking them.
   */
  public static DeserializedAst deserializeRuntimeLibraries(
      AbstractCompiler compiler,
      SourceFile syntheticExterns,
      Optional<ColorPool.Builder> colorPool,
      InputStream typedAstsStream) {
    return deserialize(
        compiler,
        syntheticExterns,
        ImmutableMap.of(),
        colorPool,
        typedAstsStream,
        Mode.RUNTIME_LIBRARY_ONLY,
        colorPool.isPresent());
  }

  private static DeserializedAst deserialize(
      AbstractCompiler compiler,
      SourceFile syntheticExterns,
      ImmutableMap<String, SourceFile> scriptSourceFiles,
      Optional<ColorPool.Builder> colorPool,
      InputStream typedAstStream,
      Mode mode,
      boolean includeTypeInformation) {
    checkArgument(
        colorPool.isPresent() == (mode.equals(Mode.RUNTIME_LIBRARY_ONLY) && includeTypeInformation),
        "ColorPool.Builder required iff deserializing runtime libraries & including types");
    List<TypedAst> typedAstProtos = deserializeTypedAsts(typedAstStream);

    TypedAstDeserializer deserializer =
        new TypedAstDeserializer(syntheticExterns, colorPool, mode, includeTypeInformation);
    deserializer.filePoolBuilder.put(syntheticExterns.getName(), syntheticExterns);
    deserializer.filePoolBuilder.putAll(scriptSourceFiles);

    if (!mode.equals(Mode.RUNTIME_LIBRARY_ONLY)) {
      // skip this step if deserializing the runtime libraries to avoid an infinite loop
      // runtime library initialization needs an unbuilt ColorPool.Builder
      compiler.initRuntimeLibraryTypedAsts(deserializer.colorPoolBuilder);
    }

    for (TypedAst typedAstProto : typedAstProtos) {
      deserializer.deserializeSingleTypedAst(typedAstProto);
    }

    deserializer.typedAstFilesystem.put(
        syntheticExterns,
        () -> {
          Node script = IR.script();
          script.setStaticSourceFile(syntheticExterns);
          for (ScriptNodeDeserializer d : deserializer.syntheticExternsDeserializers) {
            script.addChildrenToBack(d.deserializeNew().removeChildren());
          }
          return script;
        });

    return deserializer.toDeserializedAst();
  }

  private DeserializedAst toDeserializedAst() {
    Optional<ColorRegistry> registry =
        this.mode.equals(Mode.RUNTIME_LIBRARY_ONLY) || !this.colorPoolBuilder.isPresent()
            ? Optional.absent()
            : Optional.of(colorPoolBuilder.get().build().getRegistry());
    return DeserializedAst.create(
        ImmutableMap.copyOf(typedAstFilesystem),
        registry,
        externProperties.build());
  }

  private void deserializeSingleTypedAst(TypedAst typedAstProto) {
    ImmutableList.Builder<SourceFile> fileShardBuilder = ImmutableList.builder();
    StringPool stringShard = StringPool.fromProto(typedAstProto.getStringPool());
    Optional<ColorPool.ShardView> colorShard =
        colorPoolBuilder.transform(
            (builder) -> builder.addShard(typedAstProto.getTypePool(), stringShard));

    for (SourceFileProto p : typedAstProto.getSourceFilePool().getSourceFileList()) {
      fileShardBuilder.add(
          filePoolBuilder.computeIfAbsent(p.getFilename(), (n) -> SourceFile.fromProto(p)));
    }
    ImmutableList<SourceFile> fileShard = fileShardBuilder.build();

    for (int x : typedAstProto.getExternsSummary().getPropNamePtrList()) {
      externProperties.add(stringShard.get(x));
    }

    for (LazyAst lazyAst :
        Iterables.concat(typedAstProto.getExternAstList(), typedAstProto.getCodeAstList())) {
      initLazyAstDeserializer(lazyAst, fileShard, colorShard, stringShard);
    }
  }

  private void initLazyAstDeserializer(
      LazyAst lazyAst,
      ImmutableList<SourceFile> fileShard,
      Optional<ColorPool.ShardView> colorShard,
      StringPool stringShard) {
    SourceFile file = fileShard.get(lazyAst.getSourceFile() - 1);
    ScriptNodeDeserializer deserializer =
        new ScriptNodeDeserializer(lazyAst, stringShard, colorShard, fileShard);

    if (identical(syntheticExterns, file)) {
      syntheticExternsDeserializers.add(deserializer);
    } else {
      typedAstFilesystem.computeIfAbsent(file, (f) -> deserializer::deserializeNew);
    }
  }

  @GwtIncompatible("ObjectInputStream")
  private static List<TypedAst> deserializeTypedAsts(InputStream typedAstsStream) {
    try {
      CodedInputStream codedInput = CodedInputStream.newInstance(typedAstsStream);
      return TypedAst.List.parseFrom(codedInput, ExtensionRegistry.getEmptyRegistry())
          .getTypedAstsList();
    } catch (IOException ex) {
      throw new IllegalArgumentException("Cannot read from TypedAST input stream", ex);
    }
  }

  /** The result of deserializing a TypedAst.List */
  @AutoValue
  public abstract static class DeserializedAst {

    /**
     * Maps from SourceFile to a lazy deserializer of the SCRIPT node for that file
     *
     * <p>The supplier creates a new Node whenever called (but the results should be .equals)
     */
    public abstract ImmutableMap<SourceFile, Supplier<Node>> getFilesystem();

    /**
     * The built ColorRegistry.
     *
     * <p>Note that this is absent if either a) {@link
     * TypedAstDeserializer#deserializeRuntimeLibraries(AbstractCompiler, SourceFile,
     * ColorPool.Builder, InputStream)} was called, as this sort of deserialization does not build a
     * complete AST + colors, just a shard of it, or b) type information was not requested.
     */
    public abstract Optional<ColorRegistry> getColorRegistry();

    /**
     * Returns a list of all known extern properties, including properties that were present in type
     * annotations in source code but not serialized on the AST
     */
    @Nullable
    public abstract ImmutableSet<String> getExternProperties();

    private static DeserializedAst create(
        ImmutableMap<SourceFile, Supplier<Node>> filesystem,
        Optional<ColorRegistry> colorRegistry,
        ImmutableSet<String> externProperties) {
      return new AutoValue_TypedAstDeserializer_DeserializedAst(
          filesystem, colorRegistry, externProperties);
    }
  }
}
