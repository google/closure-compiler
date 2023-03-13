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
import com.google.javascript.jscomp.SourceMapInput;
import com.google.javascript.jscomp.SourceMapResolver;
import com.google.javascript.jscomp.colors.ColorRegistry;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.WireFormat;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.nullness.Nullable;

/** Deserializes a list of TypedAst protos into the JSCompiler AST structure. */
@GwtIncompatible("protobuf.lite")
public final class TypedAstDeserializer {

  private final Mode mode;
  private final SourceFile syntheticExterns;
  private final Optional<ColorPool.Builder> colorPoolBuilder;

  private final Optional<ImmutableSet<SourceFile>> requiredInputFiles;
  private final LinkedHashMap<String, SourceFile> filePoolBuilder = new LinkedHashMap<>();
  private final ConcurrentMap<SourceFile, Supplier<Node>> typedAstFilesystem =
      new ConcurrentHashMap<>();
  private final ImmutableSet.Builder<String> externProperties = ImmutableSet.builder();
  private final ArrayList<ScriptNodeDeserializer> syntheticExternsDeserializers = new ArrayList<>();

  private TypedAstDeserializer(
      SourceFile syntheticExterns,
      Optional<ColorPool.Builder> existingColorPool,
      Optional<ImmutableSet<SourceFile>> requiredInputFiles,
      Mode mode,
      boolean includeTypeInformation) {
    this.syntheticExterns = syntheticExterns;
    this.mode = mode;
    if (includeTypeInformation) {
      this.colorPoolBuilder = Optional.of(existingColorPool.or(ColorPool.builder()));
    } else {
      this.colorPoolBuilder = Optional.absent();
    }
    this.requiredInputFiles = requiredInputFiles;
  }

  private enum Mode {
    RUNTIME_LIBRARY_ONLY,
    FULL_AST
  }

  /**
   * Transforms a given TypedAst delimited stream into a compiler AST
   *
   * @param requiredInputFiles All SourceFiles that should go into the typedAstFilesystem. If a
   *     TypedAst contains a file not in this set, that file will not be added to the
   *     typedAstFilesystem to avoid wasting memory.
   * @param includeTypeInformation Whether to deserialize the "Typed" half of a "TypedAst". If false
   *     ignores the TypePool, any TypePointers on AstNodes, and does not create a ColorRegistry.
   * @param resolveSourceMapAnnotations Whether to create and register a SourceMapInput for a given
   *     `//# sourceMappingURL` (stored in LazyAst) when deserializing.
   * @param parseInlineSourceMaps Whether a given `//# sourceMappingURL` should be registered as a
   *     Base64 encoded source map.
   */
  public static DeserializedAst deserializeFullAst(
      AbstractCompiler compiler,
      SourceFile syntheticExterns,
      ImmutableSet<SourceFile> requiredInputFiles,
      InputStream typedAstsStream,
      boolean includeTypeInformation,
      boolean resolveSourceMapAnnotations,
      boolean parseInlineSourceMaps) {
    ImmutableMap<String, SourceFile> sourceFilesByName =
        requiredInputFiles.stream()
            .collect(toImmutableMap(SourceFile::getName, Function.identity()));
    return deserialize(
        compiler,
        syntheticExterns,
        Optional.of(requiredInputFiles),
        sourceFilesByName,
        Optional.absent(),
        typedAstsStream,
        Mode.FULL_AST,
        includeTypeInformation,
        resolveSourceMapAnnotations,
        parseInlineSourceMaps);
  }

  /**
   * Transforms the special runtime library TypedAst
   *
   * @param colorPool a ColorPool.Builder holding the colors on the full AST. We want to merge these
   *     colors with the runtime library colors to allow injecting runtime libraries without
   *     re-typechecking them.
   */
  public static DeserializedAst deserializeRuntimeLibraries(
      AbstractCompiler compiler,
      SourceFile syntheticExterns,
      Optional<ColorPool.Builder> colorPool,
      InputStream typedAstsStream,
      boolean resolveSourceMapAnnotations,
      boolean parseInlineSourceMaps) {
    return deserialize(
        compiler,
        syntheticExterns,
        // we don't a priori know the SourceFiles corresponding to the runtime libraries like we
        // do for normal CompilerInputs
        Optional.absent(),
        ImmutableMap.of(),
        colorPool,
        typedAstsStream,
        Mode.RUNTIME_LIBRARY_ONLY,
        colorPool.isPresent(),
        resolveSourceMapAnnotations,
        parseInlineSourceMaps);
  }

  private static DeserializedAst deserialize(
      AbstractCompiler compiler,
      SourceFile syntheticExterns,
      Optional<ImmutableSet<SourceFile>> requiredInputFiles,
      ImmutableMap<String, SourceFile> scriptSourceFiles,
      Optional<ColorPool.Builder> colorPool,
      InputStream typedAstStream,
      Mode mode,
      boolean includeTypeInformation,
      boolean resolveSourceMapAnnotations,
      boolean parseInlineSourceMaps) {
    checkArgument(
        colorPool.isPresent() == (mode.equals(Mode.RUNTIME_LIBRARY_ONLY) && includeTypeInformation),
        "ColorPool.Builder required iff deserializing runtime libraries & including types");

    TypedAstDeserializer deserializer =
        new TypedAstDeserializer(
            syntheticExterns, colorPool, requiredInputFiles, mode, includeTypeInformation);
    deserializer.filePoolBuilder.put(syntheticExterns.getName(), syntheticExterns);
    deserializer.filePoolBuilder.putAll(scriptSourceFiles);

    if (!mode.equals(Mode.RUNTIME_LIBRARY_ONLY)) {
      // skip this step if deserializing the runtime libraries to avoid an infinite loop
      // runtime library initialization needs an unbuilt ColorPool.Builder
      compiler.initRuntimeLibraryTypedAsts(deserializer.colorPoolBuilder);
    }

    deserializeTypedAsts(
        typedAstStream, deserializer, compiler, resolveSourceMapAnnotations, parseInlineSourceMaps);

    deserializer.typedAstFilesystem.put(
        syntheticExterns,
        new SyntheticExternsDeserializer(
                syntheticExterns, deserializer.syntheticExternsDeserializers)
            ::deserialize);

    return deserializer.toDeserializedAst();
  }

  /**
   * Helper class that is a lazy deserializer for the synthetic externs script
   *
   * <p>The "checks" phase of JSCompiler may synthesize new extern nodes. This means that if we are
   * merging multiple TypedAST shards from different independent "checks" phases, each shard may
   * have its own synthetic externs script. This class concatenates each synthetic externs script
   * into one.
   *
   * <p>Note: while it's possible that other files appear in multiple TypedAST shards, we assume
   * that those files are identical, and arbitrarily choose one copy of each file. The synthetic
   * externs are the only case where we concatenate multiple versions of the same file rather than
   * just choosing one.
   */
  private static final class SyntheticExternsDeserializer {
    private final SourceFile syntheticExterns;
    private final ArrayList<ScriptNodeDeserializer> syntheticExternsDeserializers;

    SyntheticExternsDeserializer(
        SourceFile syntheticExterns,
        ArrayList<ScriptNodeDeserializer> syntheticExternsDeserializers) {
      this.syntheticExterns = syntheticExterns;
      this.syntheticExternsDeserializers = syntheticExternsDeserializers;
    }

    Node deserialize() {
      Node script = IR.script();
      script.setStaticSourceFile(syntheticExterns);
      for (ScriptNodeDeserializer d : this.syntheticExternsDeserializers) {
        script.addChildrenToBack(d.deserializeNew().removeChildren());
      }
      return script;
    }
  }

  private DeserializedAst toDeserializedAst() {
    Optional<ColorRegistry> registry =
        this.mode.equals(Mode.RUNTIME_LIBRARY_ONLY) || !this.colorPoolBuilder.isPresent()
            ? Optional.absent()
            : Optional.of(colorPoolBuilder.get().build().getRegistry());
    return DeserializedAst.create(typedAstFilesystem, registry, externProperties.build());
  }

  private void deserializeTypedAst(
      TypedAst typedAstProto,
      AbstractCompiler compiler,
      boolean resolveSourceMapAnnotations,
      boolean parseInlineSourceMaps) {
    ImmutableList<SourceFile> fileShard = toFileShard(typedAstProto);

    if (this.mode.equals(Mode.FULL_AST)) {
      // Exclude any TypedAST shards passed to the compiler that don't contain any actual
      // sources we care about. For example: if we're passed "--js=a.js --js=b.js", and see a
      // TypedAST only containing ["c.js", "synthetic:externs"], it's a drain on performance to
      // include it.
      boolean containsRequiredInput = false;
      for (LazyAst lazyAst :
          Iterables.concat(typedAstProto.getExternAstList(), typedAstProto.getCodeAstList())) {
        SourceFile file = fileShard.get(lazyAst.getSourceFile() - 1);
        if (requiredInputFiles.get().contains(file)) {
          containsRequiredInput = true;
          break;
        }
      }
      if (!containsRequiredInput) {
        return;
      }
    }

    // TODO(b/248351234): can we avoid some of this work if the shard only contains weak srcs?
    // one risk: could checks passes synthesize new externProperties even for weak srcs?
    StringPool stringShard = StringPool.fromProto(typedAstProto.getStringPool());
    Optional<ColorPool.ShardView> colorShard =
        colorPoolBuilder.transform(
            (builder) -> builder.addShard(typedAstProto.getTypePool(), stringShard));
    for (int x : typedAstProto.getExternsSummary().getPropNamePtrList()) {
      externProperties.add(stringShard.get(x));
    }

    for (LazyAst lazyAst :
        Iterables.concat(typedAstProto.getExternAstList(), typedAstProto.getCodeAstList())) {
      initLazyAstDeserializer(
          lazyAst,
          fileShard,
          colorShard,
          stringShard,
          compiler,
          resolveSourceMapAnnotations,
          parseInlineSourceMaps);
    }
  }

  private ImmutableList<SourceFile> toFileShard(TypedAst typedAstProto) {
    ImmutableList.Builder<SourceFile> fileShardBuilder = ImmutableList.builder();
    List<SourceFileProto> protos = typedAstProto.getSourceFilePool().getSourceFileList();
    for (int i = 0; i < protos.size(); i++) {
      SourceFileProto p = protos.get(i);

      SourceFile existingFile = filePoolBuilder.get(p.getFilename());
      if (existingFile != null) {
        // Merge the existing SourceFile with the information serialized in the TypedAST.
        existingFile.restoreCachedStateFrom(p);
        fileShardBuilder.add(existingFile);
      } else {
        SourceFile protoFile = SourceFile.fromProto(p);
        fileShardBuilder.add(protoFile);
      }
    }
    return fileShardBuilder.build();
  }

  private void initLazyAstDeserializer(
      LazyAst lazyAst,
      ImmutableList<SourceFile> fileShard,
      Optional<ColorPool.ShardView> colorShard,
      StringPool stringShard,
      AbstractCompiler compiler,
      boolean resolveSourceMapAnnotations,
      boolean parseInlineSourceMaps) {
    SourceFile file = fileShard.get(lazyAst.getSourceFile() - 1);

    if (requiredInputFiles.isPresent()
        && !requiredInputFiles.get().contains(file)
        // Synthetic externs bypass the normal dependency system and are always included in a
        // compilation.
        && !identical(syntheticExterns, file)) {
      return;
    }

    if (file.isWeak()) {
      typedAstFilesystem.computeIfAbsent(file, TypedAstDeserializer::createStubWeakScriptNode);
      return;
    }

    ScriptNodeDeserializer deserializer =
        new ScriptNodeDeserializer(lazyAst, stringShard, colorShard, fileShard);

    if (identical(syntheticExterns, file)) {
      syntheticExternsDeserializers.add(deserializer);
    } else {
      typedAstFilesystem.computeIfAbsent(file, (f) -> deserializer::deserializeNew);
    }

    addInputSourceMap(deserializer, compiler, resolveSourceMapAnnotations, parseInlineSourceMaps);
  }

  /**
   * While deserializing a TypedAST script, if it has a corresponding sourceMappingURL, create a
   * SourceMapInput and register it.
   *
   * @param resolveSourceMapAnnotations Whether to create and register a SourceMapInput for a given
   *     `//# sourceMappingURL` (stored in LazyAst) when deserializing.
   * @param parseInlineSourceMaps Whether a given `//# sourceMappingURL` should be registered as a
   *     Base64 encoded source map.
   */
  private static void addInputSourceMap(
      ScriptNodeDeserializer deserializer,
      AbstractCompiler compiler,
      boolean resolveSourceMapAnnotations,
      boolean parseInlineSourceMaps) {
    SourceFile sourceFile = deserializer.getSourceFile();
    String sourceMappingURL = deserializer.getSourceMappingURL(); // This is the encoded source map.

    if (sourceMappingURL != null && sourceMappingURL.length() > 0 && resolveSourceMapAnnotations) {
      // base64EncodedSourceMap adds "data:application/json;base64," prefix to the sourceMappingURL
      String base64EncodedSourceMap =
          SourceMapResolver.addBase64PrefixToEncodedSourceMap(sourceMappingURL);
      SourceFile sourceMapSourceFile =
          SourceMapResolver.extractSourceMap(
              sourceFile, base64EncodedSourceMap, parseInlineSourceMaps);
      if (sourceMapSourceFile != null) {
        compiler.addInputSourceMap(sourceFile.getName(), new SourceMapInput(sourceMapSourceFile));
      }
    }
  }

  /**
   * Adds a stub deserializer for weak files to the typed ast files system.
   *
   * <p>Weak files don't actually need to be deserialized. They're only needed for typechecking and
   * by definition a TypedAST has already been typechecked.
   */
  private static Supplier<Node> createStubWeakScriptNode(SourceFile file) {
    return () -> {
      Node stubScript = IR.script().setStaticSourceFile(file);
      stubScript.putProp(Node.FEATURE_SET, FeatureSet.BARE_MINIMUM);
      return stubScript;
    };
  }

  @GwtIncompatible("ObjectInputStream")
  private static void deserializeTypedAsts(
      InputStream typedAstsStream,
      TypedAstDeserializer deserializer,
      AbstractCompiler compiler,
      boolean resolveSourceMapAnnotations,
      boolean parseInlineSourceMaps) {
    try {
      CodedInputStream codedInput = CodedInputStream.newInstance(typedAstsStream);
      // The typedAstsStream is an encoded 'TypedAst.List' message:
      //  message TypedAst {
      //    // (other fields)
      //   message List {
      //     repeated TypedAst typed_asts = 1;
      //   }
      // }
      // We could use the Java proto API to create a TypedAst.List object from this stream. However,
      // in some compiler modes the TypedAst.List may contain thousands of TypedAst objects, and
      // pulling them all into memory at once is unnecessarily expensive. Instead we read a single
      // TypedAst object at a time from the stream.
      TypedAst.Builder typedAstBuilder = TypedAst.newBuilder();
      while (!codedInput.isAtEnd()) {
        int tag = codedInput.readTag();
        if (WireFormat.getTagFieldNumber(tag) != TypedAst.List.TYPED_ASTS_FIELD_NUMBER
            || WireFormat.getTagWireType(tag) != WireFormat.WIRETYPE_LENGTH_DELIMITED) {
          throw new InvalidProtocolBufferException(
              "Unexpected field number "
                  + WireFormat.getTagFieldNumber(tag)
                  + " or wire type "
                  + WireFormat.getTagWireType(tag));
        }
        codedInput.readMessage(typedAstBuilder, ExtensionRegistry.getEmptyRegistry());
        TypedAst typedAst = typedAstBuilder.build();
        typedAstBuilder.clear();
        deserializer.deserializeTypedAst(
            typedAst, compiler, resolveSourceMapAnnotations, parseInlineSourceMaps);
      }
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
    public abstract ConcurrentMap<SourceFile, Supplier<Node>> getFilesystem();

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
    public abstract @Nullable ImmutableSet<String> getExternProperties();

    private static DeserializedAst create(
        ConcurrentMap<SourceFile, Supplier<Node>> filesystem,
        Optional<ColorRegistry> colorRegistry,
        ImmutableSet<String> externProperties) {
      return new AutoValue_TypedAstDeserializer_DeserializedAst(
          filesystem, colorRegistry, externProperties);
    }
  }
}
