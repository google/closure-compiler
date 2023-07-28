/*
 * Copyright 2009 The Closure Compiler Authors.
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

package com.google.javascript.jscomp;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.MustBeClosed;
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import com.google.javascript.jscomp.ExpressionDecomposer.Workaround;
import com.google.javascript.jscomp.colors.ColorRegistry;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.diagnostic.LogFile;
import com.google.javascript.jscomp.modules.ModuleMap;
import com.google.javascript.jscomp.modules.ModuleMetadataMap;
import com.google.javascript.jscomp.parsing.Config;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.trees.Comment;
import com.google.javascript.jscomp.serialization.ColorPool;
import com.google.javascript.jscomp.type.ReverseAbstractInterpreter;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticScope;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.jspecify.nullness.Nullable;

/**
 * An abstract compiler, to help remove the circular dependency of passes on JSCompiler.
 *
 * <p>This is an abstract class, so that we can make the methods package-private.
 */
public abstract class AbstractCompiler implements SourceExcerptProvider, CompilerInputProvider {
  static final DiagnosticType READ_ERROR =
      DiagnosticType.error("JSC_READ_ERROR", "Cannot read file {0}: {1}");

  private int currentPassIndex = -1;

  /** Will be called before each pass runs. */
  @OverridingMethodsMustInvokeSuper
  void beforePass(String passName) {
    this.currentPassIndex++;
  }

  /** Will be called after each pass finishes. */
  @OverridingMethodsMustInvokeSuper
  void afterPass(String passName) {}

  private LifeCycleStage stage = LifeCycleStage.RAW;

  // TODO(nicksantos): Decide if all of these are really necessary.
  // Many of them are just accessors that should be passed to the
  // CompilerPass's constructor.

  abstract java.util.function.Supplier<Node> getTypedAstDeserializer(SourceFile file);

  /** Looks up an input (possibly an externs input) by input id. May return null. */
  @Override
  public abstract CompilerInput getInput(InputId inputId);

  /** Looks up a source file by name. May return null. */
  abstract @Nullable SourceFile getSourceFileByName(String sourceName);

  public abstract @Nullable Node getScriptNode(String filename);

  /** Gets the module graph. */
  abstract @Nullable JSChunkGraph getModuleGraph();

  /**
   * Gets the inputs in the order in which they are being processed. Only for use by {@code
   * AbstractCompilerRunner}.
   */
  abstract Iterable<CompilerInput> getInputsInOrder();

  /**
   * Gets the total number of inputs.
   *
   * <p>This can be useful as a guide for the initial allocated size for data structures.
   */
  abstract int getNumberOfInputs();

  //
  // Intermediate state and results produced and needed by particular passes.
  // TODO(rluble): move these into the general structure for keeping state between pass runs.
  //
  /** Adds exported names to keep track. */
  public abstract void addExportedNames(Set<String> exportedVariableNames);

  /** Gets the names that have been exported. */
  public abstract Set<String> getExportedNames();

  /** Sets the variable renaming map */
  public abstract void setVariableMap(VariableMap variableMap);

  /** Sets the property renaming map */
  public abstract void setPropertyMap(VariableMap propertyMap);

  /** Sets the string replacement map */
  public abstract void setStringMap(VariableMap stringMap);

  /** Sets the css names found during compilation. */
  public abstract void setCssNames(Set<String> newCssNames);

  /** Sets the mapping for instrumentation parameter encoding. */
  public abstract void setInstrumentationMapping(VariableMap instrumentationMapping);

  /** Sets the id generator for cross-module motion. */
  public abstract void setIdGeneratorMap(String serializedIdMappings);

  /** Gets whether any file needed to transpile any feature */
  public abstract boolean getTranspiledFiles();

  /** Gets the id generator for cross-module motion. */
  public abstract IdGenerator getCrossModuleIdGenerator();

  /** Sets the naming map for anonymous functions */
  public abstract void setAnonymousFunctionNameMap(VariableMap functionMap);
  //
  // End of intermediate state needed by passes.
  //

  /** Sets whether the typechecking passes have run. */
  abstract void setTypeCheckingHasRun(boolean hasRun);

  /** Returns whether the typechecking passes have run */
  public abstract boolean hasTypeCheckingRun();

  /** Whether the AST has been annotated with optimization colors. */
  public abstract boolean hasOptimizationColors();

  /**
   * Returns `true` when type checking has run, but the type registry has been cleared.
   *
   * <p>See also `clearJSTypeRegistry()`.
   */
  public abstract boolean isTypeRegistryCleared();

  /** Gets a central registry of type information from the compiled JS. */
  public abstract JSTypeRegistry getTypeRegistry();

  public abstract void clearJSTypeRegistry();

  /** Gets a central registry of colors from deserialized JS types. */
  public abstract ColorRegistry getColorRegistry();

  /** Sets the color registry */
  public abstract void setColorRegistry(ColorRegistry registry);

  abstract void forwardDeclareType(String typeName);

  /** Gets a memoized scope creator with type information. */
  abstract ScopeCreator getTypedScopeCreator();

  abstract void clearTypedScopeCreator();

  /** Gets the top scope. */
  public abstract @Nullable TypedScope getTopScope();

  /** Sets the top scope. */
  abstract void setTopScope(@Nullable TypedScope x);

  /**
   * Returns a scope containing only externs and synthetic code or other code in the first script.
   *
   * <p>Intended for transpilation passes to look up types when synthesizing new code.
   */
  abstract StaticScope getTranspilationNamespace();

  /** Report an error or warning. */
  public abstract void report(JSError error);

  /** Report an internal error. */
  abstract void throwInternalError(String msg, Throwable cause);

  /** Gets the current coding convention. */
  public abstract CodingConvention getCodingConvention();

  /**
   * Passes that make modifications in a scope that is different than the Compiler.currentScope use
   * this (eg, InlineVariables and many others)
   */
  public abstract void reportChangeToEnclosingScope(Node n);

  /**
   * Mark modifications in a scope that is different than the Compiler.currentScope use this (eg,
   * InlineVariables and many others)
   */
  public abstract void reportChangeToChangeScope(Node changeScopeRoot);

  /**
   * Mark a specific function node as known to be deleted. Is part of having accurate change
   * tracking which is necessary to streamline optimizations.
   */
  abstract void reportFunctionDeleted(Node node);

  /**
   * Gets a suitable SCRIPT node to serve as a parent for code insertion. If {@code module} contains
   * any inputs, the returned node will be the SCRIPT node corresponding to its first input. If
   * {@code module} is empty, on the other hand, then the returned node will be the first SCRIPT
   * node in a non-empty module that {@code module} depends on (the deepest one possible).
   *
   * @param module A module. If null, will return the first SCRIPT node of all modules.
   * @return A SCRIPT node (never null).
   */
  abstract Node getNodeForCodeInsertion(@Nullable JSChunk module);

  abstract TypeValidator getTypeValidator();

  /** Gets the central registry of type violations. */
  public abstract Iterable<TypeMismatch> getTypeMismatches();

  abstract void setExternExports(String externExports);

  /** Parses code for injecting, and associate it with a given source file. */
  public abstract Node parseSyntheticCode(String filename, String code);

  /** Parses code for testing. */
  @VisibleForTesting
  abstract Node parseTestCode(String code);

  /** Parses code for testing. */
  @VisibleForTesting
  abstract Node parseTestCode(ImmutableList<String> code);

  /** Prints a node to source code. */
  public abstract String toSource();

  /** Prints a node to source code. */
  public abstract String toSource(Node root);

  /**
   * Whether to prefer using {@link JSFileRegexParser} when calculating {@link DependencyInfo} as
   * opposed to triggering an AST-based parse.
   *
   * <p>This is primarily for performance reasons. The two should produce the same results except in
   * some edge cases.
   */
  abstract boolean preferRegexParser();

  /** Gets a default error reporter for injecting into Rhino. */
  abstract ErrorReporter getDefaultErrorReporter();

  /** Get an interpreter for type analysis. */
  public abstract ReverseAbstractInterpreter getReverseAbstractInterpreter();

  /** Returns the current life-cycle stage of the AST we're working on. */
  public LifeCycleStage getLifeCycleStage() {
    return stage;
  }

  private static final String FILL_FILE_SUFFIX = "$fillFile";

  /** Empty modules get an empty "fill" file, so that we can move code into an empty module. */
  static String createFillFileName(String moduleName) {
    return moduleName + FILL_FILE_SUFFIX;
  }

  /** Returns whether a file name was created by {@link createFillFileName}. */
  public static boolean isFillFileName(String fileName) {
    return fileName.endsWith(FILL_FILE_SUFFIX);
  }

  /**
   * Deserialize runtime libraries from a TypedAST packaged as a JAR resource and reconcile their
   * Colors with the current inputs.
   *
   * <p>This method must be called anywhere that Colors are reconciled for application to the AST.
   * Otherwise Color information won't be consistent. `colorPoolBuilder` must be the same builder as
   * used for the other inputs, and the caller retains ownership.
   *
   * @param colorPoolBuilder if present, includes inferred optimization colors on the deserialized
   *     ASTs. If absent, does not include colors.
   */
  public void initRuntimeLibraryTypedAsts(Optional<ColorPool.Builder> colorPoolBuilder) {
    throw new UnsupportedOperationException(
        "Implementation in Compiler.java is not J2CL compatible.");
  }

  /**
   * Generates unique String Ids when requested via a compiler instance.
   *
   * <p>This supplier provides Ids that are deterministic and unique across all input files given to
   * the compiler. The generated ID format is: uniqueId = "fileHashCode$counterForThisFile"
   */
  abstract UniqueIdSupplier getUniqueIdSupplier();

  /**
   * Generates unique ids.
   *
   * @deprecated because the generated names during transpilation are not unique across all input
   *     files. Use the new supplier by calling {@code getUniqueIdSupplier()}.
   */
  @Deprecated
  abstract Supplier<String> getUniqueNameIdSupplier();

  /**
   * @return Whether any errors have been encountered that should stop the compilation process.
   */
  abstract boolean hasHaltingErrors();

  /** Register a listener for code change events. */
  abstract void addChangeHandler(CodeChangeHandler handler);

  /** Remove a listener for code change events. */
  abstract void removeChangeHandler(CodeChangeHandler handler);

  /** Register a provider for some type of index. */
  abstract void addIndexProvider(IndexProvider<?> indexProvider);

  /**
   * Returns, from a provider, the desired index of type T, otherwise null if no provider is
   * registered for the given type.
   */
  abstract <T> T getIndex(Class<T> type);

  /** A monotonically increasing value to identify a change */
  abstract int getChangeStamp();

  /**
   * An accumulation of changed scope nodes since the last time the given pass was run. A returned
   * empty list means no scope nodes have changed since the last run and a returned null means this
   * is the first time the pass has run.
   */
  abstract List<Node> getChangedScopeNodesForPass(String passName);

  /** Called to indicate that the current change stamp has been used */
  abstract void incrementChangeStamp();

  /** Returns the root of the source tree, ignoring externs */
  abstract Node getJsRoot();

  /** True iff a function changed since the last time a pass was run */
  abstract boolean hasScopeChanged(Node n);

  /**
   * Represents the different contexts for which the compiler could have distinct configurations.
   */
  static enum ConfigContext {
    /** Normal JavaScript. */
    DEFAULT,

    /** Externs files. */
    EXTERNS
  }

  /** Returns the parser configuration for the specified context. */
  abstract Config getParserConfig(ConfigContext context);

  /** Gets the error manager. */
  public abstract ErrorManager getErrorManager();

  /** Set the current life-cycle state. */
  void setLifeCycleStage(LifeCycleStage stage) {
    this.stage = stage;
  }

  /**
   * Are the nodes equal for the purpose of inlining? If type aware optimizations are on, type
   * equality is checked.
   */
  abstract boolean areNodesEqualForInlining(Node n1, Node n2);

  /**
   * Set if RegExp global properties are used.
   *
   * @param references Whether there are references to the RegExp global object properties.
   */
  abstract void setHasRegExpGlobalReferences(boolean references);

  /**
   * @return Whether the AST contains references to the RegExp global object properties.
   */
  abstract boolean hasRegExpGlobalReferences();

  /**
   * @return The error level the given error object will be reported at.
   */
  abstract CheckLevel getErrorLevel(JSError error);

  /** What point in optimizations we're in. For use by compiler passes */
  public static enum LifeCycleStage implements Serializable {
    RAW,

    // See constraints put on the AST by either running the ConvertTypesToColors.java pass /or/ by
    // having created the AST via serialization/TypedAstDeserializer.java
    // NORMALIZED implies this constraint
    COLORS_AND_SIMPLIFIED_JSDOC,

    // See constraints put on the tree by Normalize.java
    NORMALIZED,

    // The normalize pass has put constraints on the tree,
    // but variables and properties have been renamed so
    // coding conventions no longer apply.
    NORMALIZED_OBFUSCATED;

    public boolean isNormalized() {
      return this == NORMALIZED || this == NORMALIZED_OBFUSCATED;
    }

    public boolean isNormalizedUnobfuscated() {
      return this == NORMALIZED;
    }

    public boolean isNormalizedObfuscated() {
      return this == NORMALIZED_OBFUSCATED;
    }

    public boolean hasColorAndSimplifiedJSDoc() {
      return this != RAW;
    }
  }

  /**
   * Runs a given compiler-pass by calling its {@code process()} method.
   *
   * @param pass The pass to be run.
   */
  abstract void process(CompilerPass pass);

  /** Returns the root node of the AST, which includes both externs and source. */
  public abstract Node getRoot();

  abstract CompilerOptions getOptions();

  /**
   * Returns the set of language features currently allowed to exist in the AST
   *
   * <p>At the start of compilation, all features in the languageIn are allowed. At the end of
   * compilation, only features in the languageOut should be allowed.
   *
   * <p>Transpilation passes should mark the transpiled features as "not allowed" to prevent future
   * passes from accidentally introducing new uses of those features. For example, if transpiling
   * away ES2017 features, the {@link RewriteAsyncFunctions} pass will mark async functions as no
   * longer allowed.
   */
  abstract FeatureSet getAllowableFeatures();

  /**
   * Sets the feature set allowed in the AST. See {@link #getAllowableFeatures()}
   *
   * <p>In most cases, callers of this method should only be shrinking the allowable feature set and
   * not adding to it. One known exception is the ConvertChunksToESModules pass.
   */
  abstract void setAllowableFeatures(FeatureSet featureSet);

  /** Marks feature as no longer allowed in the AST. See {@link #getAllowableFeatures()} */
  abstract void markFeatureNotAllowed(FeatureSet.Feature feature);

  /** Marks features as no longer allowed in the AST. See {@link #getAllowableFeatures()} */
  abstract void markFeatureSetNotAllowed(FeatureSet featureSet);

  /**
   * @return a CompilerInput that can be modified to add additional extern definitions to the
   *     beginning of the externs AST
   */
  abstract CompilerInput getSynthesizedExternsInput();

  /** Gets the last pass name set by setProgress. */
  abstract String getLastPassName();

  static final String RUNTIME_LIB_DIR =
  "src/com/google/javascript/jscomp/js/";

  /**
   * The subdir js/ contains libraries of code that we inject at compile-time only if requested by
   * this function.
   *
   * <p>Notice that these libraries will almost always create global symbols.
   *
   * @param resourceName The name of the library. For example, if "base" is is specified, then we
   *     load js/base.js
   * @param force Inject the library even if compiler options say not to.
   * @return The last node of the most-recently-injected runtime library. If new code was injected,
   *     this will be the last expression node of the library. If the caller needs to add additional
   *     code, they should add it as the next sibling of this node. If no runtime libraries have
   *     been injected, then null is returned.
   */
  abstract Node ensureLibraryInjected(String resourceName, boolean force);

  /**
   * Sets the names of the properties defined in externs.
   *
   * @param externProperties The set of property names defined in externs.
   */
  abstract void setExternProperties(ImmutableSet<String> externProperties);

  /**
   * Gets the names of the properties defined in externs or null if GatherExternProperties pass was
   * not run yet.
   */
  public abstract ImmutableSet<String> getExternProperties();

  /**
   * Adds a {@link SourceMapInput} for the given {@code sourceFileName}, to be used for error
   * reporting and source map combining.
   */
  public abstract void addInputSourceMap(String name, SourceMapInput sourceMap);

  public abstract String getBase64SourceMapContents(String sourceFileName);

  abstract void addComments(String filename, List<Comment> comments);

  /**
   * Returns a summary an entry for every property name found in the AST with a getter and / or
   * setter defined.
   *
   * <p>Property names for which there are no getters or setters will not be in the map.
   */
  abstract AccessorSummary getAccessorSummary();

  /** Sets the summary of properties with getters and setters. */
  public abstract void setAccessorSummary(AccessorSummary summary);

  /** Returns all the comments from the given file. */
  abstract List<Comment> getComments(String filename);

  /** Gets the module loader. */
  abstract ModuleLoader getModuleLoader();

  /** Lookup the type of a module from its name. */
  abstract CompilerInput.ModuleType getModuleTypeByName(String moduleName);

  /** Set whether J2CL passes should run */
  abstract void setRunJ2clPasses(boolean runJ2clPasses);

  /** Whether J2CL passes should run */
  abstract boolean runJ2clPasses();

  /**
   * Returns a new AstFactory that will add type information to the nodes it creates if and only if
   * type checking has already happened and types have not been converted into colors.
   *
   * <p>Note that the AstFactory will /not/ add colors to the AST if types have been converted into
   * colors. The AstFactory does not understand colors, although color support could certainly be
   * added if it proves useful.
   */
  public final AstFactory createAstFactory() {
    return hasTypeCheckingRun()
        ? (hasOptimizationColors()
            ? AstFactory.createFactoryWithColors(stage, getColorRegistry())
            : AstFactory.createFactoryWithTypes(stage, getTypeRegistry()))
        : AstFactory.createFactoryWithoutTypes(stage);
  }

  /**
   * Returns a new AstFactory that will not add type information, regardless of whether type
   * checking has already happened.
   */
  public final AstFactory createAstFactoryWithoutTypes() {
    return AstFactory.createFactoryWithoutTypes(stage);
  }

  /**
   * Returns a new AstAnalyzer configured correctly to answer questions about Nodes in the AST
   * currently being compiled.
   */
  public AstAnalyzer getAstAnalyzer() {
    return new AstAnalyzer(this, getOptions().getAssumeGettersArePure());
  }

  public ExpressionDecomposer createDefaultExpressionDecomposer() {
    return createExpressionDecomposer(
        this.getUniqueNameIdSupplier(),
        ImmutableSet.of(),
        Scope.createGlobalScope(new Node(Token.SCRIPT)));
  }

  public ExpressionDecomposer createExpressionDecomposer(
      Supplier<String> uniqueNameIdSupplier,
      ImmutableSet<String> knownConstantFunctions,
      Scope scope) {
    // If the output is ES5, then it may end up running on IE11, so enable a workaround
    // for one of its bugs.
    final EnumSet<Workaround> enabledWorkarounds =
        FeatureSet.ES5.contains(getOptions().getOutputFeatureSet())
            ? EnumSet.of(Workaround.BROKEN_IE11_LOCATION_ASSIGN)
            : EnumSet.noneOf(Workaround.class);
    return new ExpressionDecomposer(
        this, uniqueNameIdSupplier, knownConstantFunctions, scope, enabledWorkarounds);
  }

  public abstract ModuleMetadataMap getModuleMetadataMap();

  public abstract void setModuleMetadataMap(ModuleMetadataMap moduleMetadataMap);

  public abstract ModuleMap getModuleMap();

  public abstract void setModuleMap(ModuleMap moduleMap);

  public final boolean isDebugLoggingEnabled() {
    return this.getOptions().getDebugLogDirectory() != null;
  }

  public final List<String> getDebugLogFilterList() {
    if (this.getOptions().getDebugLogFilter() == null) {
      return new ArrayList<>();
    }
    return Splitter.on(',').omitEmptyStrings().splitToList(this.getOptions().getDebugLogFilter());
  }

  /** Provides logging access to a file with the specified name. */
  @MustBeClosed
  public final LogFile createOrReopenLog(
      Class<?> owner, String firstNamePart, String... restNameParts) {
    if (!this.isDebugLoggingEnabled()) {
      return LogFile.createNoOp();
    }

    Path dir = getOptions().getDebugLogDirectory();
    Path relativeParts = Paths.get(firstNamePart, restNameParts);
    Path file = dir.resolve(owner.getSimpleName()).resolve(relativeParts);

    // If a filter list for log file names was provided, only create a log file if any
    // of the filter strings matches.
    List<String> filters = getDebugLogFilterList();
    if (filters.isEmpty()) {
      return LogFile.createOrReopen(file);
    }

    for (String filter : filters) {
      if (file.toString().contains(filter)) {
        return LogFile.createOrReopen(file);
      }
    }
    return LogFile.createNoOp();
  }

  /**
   * Provides logging access to a file with the specified name, differentiated by the index of the
   * current pass.
   *
   * <p>Indexing helps in separating logs from different pass loops. The filename pattern is
   * "[debug_log_directory]/[owner_name]/([name_part[i]]/){0,n-1}[pass_index]_[name_part[n]]".
   */
  @MustBeClosed
  public final LogFile createOrReopenIndexedLog(
      Class<?> owner, String firstNamePart, String... restNameParts) {
    checkState(this.currentPassIndex >= 0, this.currentPassIndex);

    String index = Strings.padStart(Integer.toString(this.currentPassIndex), 3, '0');
    int length = restNameParts.length;
    if (length == 0) {
      firstNamePart = index + "_" + firstNamePart;
    } else {
      restNameParts[length - 1] = index + "_" + restNameParts[length - 1];
    }

    return this.createOrReopenLog(owner, firstNamePart, restNameParts);
  }

  /** Returns the InputId of the synthetic code input (even if it is not initialized yet). */
  abstract InputId getSyntheticCodeInputId();

  /**
   * Adds a synthetic script to the front of the AST
   *
   * <p>Useful to allow inserting code into the global scope, earlier than any of the user-provided
   * code, in the case that the first user-provided input is a module.
   */
  abstract void initializeSyntheticCodeInput();

  /** Removes the script added by {@link #initializeSyntheticCodeInput} */
  abstract void removeSyntheticCodeInput();

  /**
   * Merges all code in the script added by {@link #initializeSyntheticCodeInput} into the first
   * non-synthetic script. Will crash if the first non-synthetic script is a module and module
   * rewriting has not occurred.
   */
  abstract void mergeSyntheticCodeInput();

  /**
   * Records the mapping of toggle names to ordinals, which is read from a bootstrap JS file by the
   * first (check) pass of {@link RewriteToggles}.
   */
  void setToggleOrdinalMapping(@Nullable ImmutableMap<String, Integer> mapping) {}

  /** Returns the recorded toggle-name-to-ordinal mapping. */
  @Nullable ImmutableMap<String, Integer> getToggleOrdinalMapping() {
    return null;
  }
}
