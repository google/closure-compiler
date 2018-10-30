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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.parsing.Config;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.trees.Comment;
import com.google.javascript.jscomp.type.ReverseAbstractInterpreter;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * An abstract compiler, to help remove the circular dependency of
 * passes on JSCompiler.
 *
 * This is an abstract class, so that we can make the methods package-private.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public abstract class AbstractCompiler implements SourceExcerptProvider {
  static final DiagnosticType READ_ERROR = DiagnosticType.error(
      "JSC_READ_ERROR", "Cannot read file {0}: {1}");

  protected Map<String, Object> annotationMap = new HashMap<>();

  /** Will be called before each pass runs. */
  abstract void beforePass(String passName);

  /**
   * Will be called after each pass finishes.
   */
  abstract void afterPass(String passName);

  private LifeCycleStage stage = LifeCycleStage.RAW;

  // TODO(nicksantos): Decide if all of these are really necessary.
  // Many of them are just accessors that should be passed to the
  // CompilerPass's constructor.

  /**
   * Looks up an input (possibly an externs input) by input id.
   * May return null.
   */
  public abstract CompilerInput getInput(InputId inputId);

  /** Looks up a source file by name. May return null. */
  @Nullable
  abstract SourceFile getSourceFileByName(String sourceName);

  @Nullable
  abstract Node getScriptNode(String filename);

  /** Gets the module graph. */
  @Nullable
  abstract JSModuleGraph getModuleGraph();

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

  /** Sets the fully qualified function name and globally unique id mapping. */
  public abstract void setFunctionNames(FunctionNames functionNames);

  /** Gets the fully qualified function name and globally unique id mapping. */
  public abstract FunctionNames getFunctionNames();

  /** Sets the css names found during compilation. */
  public abstract void setCssNames(Map<String, Integer> newCssNames);

  /** Sets the id generator for cross-module motion. */
  public abstract void setIdGeneratorMap(String serializedIdMappings);

  /** Gets the id generator for cross-module motion. */
  public abstract IdGenerator getCrossModuleIdGenerator();

  /** Sets the naming map for anonymous functions */
  public abstract void setAnonymousFunctionNameMap(VariableMap functionMap);
  //
  // End of intermediate state needed by passes.
  //

  /**
   * Sets the type-checking pass that ran most recently.
   */
  abstract void setTypeCheckingHasRun(boolean hasRun);

  /** Gets the type-checking pass that ran most recently. */
  abstract boolean hasTypeCheckingRun();

  /**
   * Gets a central registry of type information from the compiled JS.
   */
  public abstract JSTypeRegistry getTypeRegistry();

  public abstract void clearJSTypeRegistry();

  abstract void forwardDeclareType(String typeName);

  /**
   * Gets a memoized scope creator with type information. Only used by jsdev.
   */
  abstract ScopeCreator getTypedScopeCreator();

  /**
   * Gets the top scope.
   */
  public abstract TypedScope getTopScope();

  /**
   * Gets a memoized scope creator without type information, used by the checks and optimization
   * passes to avoid continuously recreating the entire scope.
   */
  abstract IncrementalScopeCreator getScopeCreator();

  /**
   * Stores a memoized scope creator without type information, used by the checks and optimization
   * passes to avoid continuously recreating the entire scope.
   */
  abstract void putScopeCreator(IncrementalScopeCreator creator);

  /**
   * Report an error or warning.
   */
  public abstract void report(JSError error);

  /**
   * Report an internal error.
   */
  abstract void throwInternalError(String msg, Throwable cause);

  /**
   * Gets the current coding convention.
   */
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
   * Sets the CssRenamingMap.
   */
  abstract void setCssRenamingMap(CssRenamingMap map);

  /**
   * Gets the CssRenamingMap.
   */
  abstract CssRenamingMap getCssRenamingMap();

  /**
   * Gets a suitable SCRIPT node to serve as a parent for code insertion. If
   * {@code module} contains any inputs, the returned node will be the SCRIPT
   * node corresponding to its first input. If {@code module} is empty, on the
   * other hand, then the returned node will be the first SCRIPT node in a
   * non-empty module that {@code module} depends on (the deepest one possible).
   *
   * @param module A module. If null, will return the first SCRIPT node of all
   *     modules.
   * @return A SCRIPT node (never null).
   */
  abstract Node getNodeForCodeInsertion(@Nullable JSModule module);

  /**
   * Only used by passes in the old type checker.
   */
  abstract TypeValidator getTypeValidator();

  /**
   * Gets the central registry of type violations.
   */
  abstract Iterable<TypeMismatch> getTypeMismatches();

  /**
   * Gets all types that are used implicitly as a
   * matching interface type. These are
   * recorded as TypeMismatchs only for convenience
   */
  abstract Iterable<TypeMismatch> getImplicitInterfaceUses();

  abstract void setExternExports(String externExports);

  /**
   * Parses code for injecting.
   */
  abstract Node parseSyntheticCode(String code);

  /**
   * Parses code for injecting, and associate it with a given source file.
   */
  abstract Node parseSyntheticCode(String filename, String code);

  /**
   * Parses code for testing.
   */
  @VisibleForTesting
  abstract Node parseTestCode(String code);

  /**
   * Prints a node to source code.
   */
  public abstract String toSource();

  /**
   * Prints a node to source code.
   */
  public abstract String toSource(Node root);

  /**
   * Gets a default error reporter for injecting into Rhino.
   */
  abstract ErrorReporter getDefaultErrorReporter();

  /**
   * Get an interpreter for type analysis.
   */
  public abstract ReverseAbstractInterpreter getReverseAbstractInterpreter();

  /**
   * @return The current life-cycle stage of the AST we're working on.
   */
  LifeCycleStage getLifeCycleStage() {
    return stage;
  }

  /**
   * Generates unique ids.
   */
  abstract Supplier<String> getUniqueNameIdSupplier();

  /**
   * @return Whether any errors have been encountered that
   *     should stop the compilation process.
   */
  abstract boolean hasHaltingErrors();

  /**
   * Register a listener for code change events.
   */
  abstract void addChangeHandler(CodeChangeHandler handler);

  /**
   * Remove a listener for code change events.
   */
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

  /**
   * An accumulation of deleted scope nodes since the last time the given pass was run. A returned
   * null or empty list means no scope nodes have been deleted since the last run or this is the
   * first time the pass has run.
   */
  abstract List<Node> getDeletedScopeNodesForPass(String passName);

  /** Called to indicate that the current change stamp has been used */
  abstract void incrementChangeStamp();

  /** Returns the root of the source tree, ignoring externs */
  abstract Node getJsRoot();

  /** True iff a function changed since the last time a pass was run */
  abstract boolean hasScopeChanged(Node n);

  /**
   * Represents the different contexts for which the compiler could have
   * distinct configurations.
   */
  static enum ConfigContext {
    /**
     * Normal JavaScript.
     */
    DEFAULT,

    /**
     * Externs files.
     */
    EXTERNS
  }

  /**
   * Returns the parser configuration for the specified context.
   */
  abstract Config getParserConfig(ConfigContext context);

  /**
   * Normalizes the types of AST nodes in the given tree, and
   * annotates any nodes to which the coding convention applies so that passes
   * can read the annotations instead of using the coding convention.
   */
  abstract void prepareAst(Node root);

  /**
   * Gets the error manager.
   */
  public abstract ErrorManager getErrorManager();

  /**
   * Set the current life-cycle state.
   */
  void setLifeCycleStage(LifeCycleStage stage) {
    this.stage = stage;
  }

  /**
   * Are the nodes equal for the purpose of inlining?
   * If type aware optimizations are on, type equality is checked.
   */
  abstract boolean areNodesEqualForInlining(Node n1, Node n2);

  /**
   * Set if RegExp global properties are used.
   * @param references Whether there are references to the RegExp global object
   *     properties.
   */
  abstract void setHasRegExpGlobalReferences(boolean references);

  /**
   * @return Whether the AST contains references to the RegExp global object
   *     properties.
   */
  abstract boolean hasRegExpGlobalReferences();

  /**
   * @return The error level the given error object will be reported at.
   */
  abstract CheckLevel getErrorLevel(JSError error);

  static enum LifeCycleStage implements Serializable {
    RAW,

    // See constraints put on the tree by Normalize.java
    NORMALIZED,

    // The normalize pass has put constraints on the tree,
    // but variables and properties have been renamed so
    // coding conventions no longer apply.
    NORMALIZED_OBFUSCATED;

    boolean isNormalized() {
      return this == NORMALIZED || this == NORMALIZED_OBFUSCATED;
    }

    boolean isNormalizedUnobfuscated() {
      return this == NORMALIZED;
    }

    boolean isNormalizedObfuscated() {
      return this == NORMALIZED_OBFUSCATED;
    }
  }

  /**
   * Runs a given compiler-pass by calling its {@code process()} method.
   * @param pass The pass to be run.
   */
  abstract void process(CompilerPass pass);

  /**
   * Returns the root node of the AST, which includes both externs and source.
   */
  public abstract Node getRoot();

  abstract CompilerOptions getOptions();

  abstract FeatureSet getFeatureSet();

  abstract void setFeatureSet(FeatureSet fs);

  // TODO(bashir) It would be good to extract a single dumb data object with
  // only getters and setters that keeps all global information we keep for a
  // compiler instance. Then move some of the functions of this class there.

  /**
   * Updates the list of references for variables in global scope.
   *
   * @param refMapPatch Maps each variable to all of its references; may contain
   *     references collected from the whole AST or only a SCRIPT sub-tree.
   * @param collectionRoot The root of sub-tree in which reference collection
   *     has been done. This should either be a SCRIPT node (if collection is
   *     done on a single file) or it is assumed that collection is on full AST.
   */
  abstract void updateGlobalVarReferences(Map<Var, ReferenceCollection>
      refMapPatch, Node collectionRoot);

  /**
   * This can be used to get the list of all references to all global variables
   * based on all previous calls to {@code updateGlobalVarReferences}.
   *
   * @return The reference collection map associated to global scope variable.
   */
  abstract GlobalVarReferenceMap getGlobalVarReferences();

  /**
   * @return a CompilerInput that can be modified to add additional extern
   * definitions to the beginning of the externs AST
   */
  abstract CompilerInput getSynthesizedExternsInput();

  /**
   * @return a CompilerInput that can be modified to add additional extern
   * definitions to the end of the externs AST
   */
  abstract CompilerInput getSynthesizedExternsInputAtEnd();

  /**
   * @return a number in [0,1] range indicating an approximate progress of the
   * last compile. Note this should only be used as a hint and no assumptions
   * should be made on accuracy, even a completed compile may choose not to set
   * this to 1.0 at the end.
   */
  public abstract double getProgress();

  /**
   * Gets the last pass name set by setProgress.
   */
  abstract String getLastPassName();

  /**
   * Sets the progress percentage as well as the name of the last pass that
   * ran (if available).
   * @param progress A percentage expressed as a double in the range [0, 1].
   *     Use -1 if you just want to set the last pass name.
   */
  abstract void setProgress(double progress, @Nullable String lastPassName);

  /**
   * The subdir js/ contains libraries of code that we inject
   * at compile-time only if requested by this function.
   *
   * Notice that these libraries will almost always create global symbols.
   *
   * @param resourceName The name of the library. For example, if "base" is
   *     is specified, then we load js/base.js
   * @param force Inject the library even if compiler options say not to.
   * @return The last node of the most-recently-injected runtime library.
   *     If new code was injected, this will be the last expression node of the
   *     library. If the caller needs to add additional code, they should add
   *     it as the next sibling of this node. If no runtime libraries have been
   *     injected, then null is returned.
   */
  abstract Node ensureLibraryInjected(String resourceName, boolean force);

  /**
   * Sets the names of the properties defined in externs.
   * @param externProperties The set of property names defined in externs.
   */
  abstract void setExternProperties(Set<String> externProperties);

  /**
   * Gets the names of the properties defined in externs or null if
   * GatherExternProperties pass was not run yet.
   */
  abstract Set<String> getExternProperties();

  /**
   * Adds a {@link SourceMapInput} for the given {@code sourceFileName}, to be used for error
   * reporting and source map combining.
   */
  public abstract void addInputSourceMap(String name, SourceMapInput sourceMap);

  abstract void addComments(String filename, List<Comment> comments);

  /**
   * Returns all the comments from the given file.
   */
  abstract List<Comment> getComments(String filename);

   /**
    * Stores a map of default @define values.  These values
    * can be overridden by values specifically set in the CompilerOptions.
    */
   abstract void setDefaultDefineValues(ImmutableMap<String, Node> values);

   /**
    * Gets a map of default @define values.  These values
    * can be overridden by values specifically set in the CompilerOptions.
    */
   abstract ImmutableMap<String, Node> getDefaultDefineValues();

  /**
   * Gets the module loader.
   */
  abstract ModuleLoader getModuleLoader();

  /** Lookup the type of a module from its name. */
  abstract CompilerInput.ModuleType getModuleTypeByName(String moduleName);

  /**
   * Sets an annotation for the given key.
   *
   * @param key the annotation key
   * @param object the object to store as the annotation
   */
  void setAnnotation(String key, Object object) {
    checkArgument(object != null, "The stored annotation value cannot be null.");
    Preconditions.checkArgument(
        !annotationMap.containsKey(key), "Cannot overwrite the existing annotation '%s'.", key);
    annotationMap.put(key, object);
  }

  /**
   * Gets the annotation for the given key.
   *
   * @param key the annotation key
   * @return the annotation object for the given key if it has been set, or null
   */
  @Nullable
  Object getAnnotation(String key) {
    return annotationMap.get(key);
  }

  /**
   * Returns a new AstFactory that will add type information to the nodes it creates if and only if
   * type type checking has already happened.
   */
  public AstFactory createAstFactory() {
    return hasTypeCheckingRun()
        ? AstFactory.createFactoryWithTypes(getTypeRegistry())
        : AstFactory.createFactoryWithoutTypes();
  }

  public abstract ModuleMetadataMap getModuleMetadataMap();

  public abstract void setModuleMetadataMap(ModuleMetadataMap moduleMetadataMap);
}
