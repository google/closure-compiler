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

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.ReferenceCollectingCallback.ReferenceCollection;
import com.google.javascript.jscomp.TypeValidator.TypeMismatch;
import com.google.javascript.jscomp.parsing.Config;
import com.google.javascript.jscomp.parsing.parser.trees.Comment;
import com.google.javascript.jscomp.type.ReverseAbstractInterpreter;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.TypeIRegistry;
import com.google.javascript.rhino.jstype.JSTypeRegistry;

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
      "JSC_READ_ERROR", "Cannot read: {0}");

  /**
   * Will be called before each pass runs.
   */
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

  /**
   * Looks up a source file by name. May return null.
   */
  abstract SourceFile getSourceFileByName(String sourceName);

  /**
   * Gets the module graph. May return null if there aren't at least two
   * modules.
   */
  abstract JSModuleGraph getModuleGraph();

  /**
   * Gets the inputs in the order in which they are being processed.
   * Only for use by {@code AbstractCompilerRunner}.
   */
  abstract List<CompilerInput> getInputsInOrder();

  /**
   * Gets a central registry of type information from the compiled JS.
   */
  public abstract JSTypeRegistry getTypeRegistry();

  public abstract TypeIRegistry getTypeIRegistry();

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
   * Report an error or warning.
   */
  public abstract void report(JSError error);

  /**
   * Report an internal error.
   */
  abstract void throwInternalError(String msg, Exception cause);

  /**
   * Gets the current coding convention.
   */
  public abstract CodingConvention getCodingConvention();

  /**
   * Report code changes.
   *
   * Passes should call reportCodeChange when they alter the JS tree. This is
   * verified by CompilerTestCase. This allows us to optimize to a fixed point.
   */
  public abstract void reportCodeChange();

  /**
   * Logs a message under a central logger.
   */
  abstract void addToDebugLog(String message);

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
  abstract Node getNodeForCodeInsertion(JSModule module);

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
   * matching structural interface type. These are
   * recorded as TypeMismatchs only for convenience
   */
  abstract Iterable<TypeMismatch> getImplicitInterfaceUses();

  /**
   * Used only by the new type inference
   */
  abstract CompilerPass getSymbolTable();

  abstract void setSymbolTable(CompilerPass symbolTable);

  /**
   * Used by three passes that run in sequence (optimize-returns,
   * optimize-parameters, remove-unused-variables), to avoid having them
   * recompute it independently.
   */
  abstract SimpleDefinitionFinder getSimpleDefinitionFinder();

  abstract void setSimpleDefinitionFinder(SimpleDefinitionFinder defFinder);

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

  /** Let the PhaseOptimizer know which scope a pass is currently analyzing */
  abstract void setScope(Node n);

  /** Returns the root of the source tree, ignoring externs */
  abstract Node getJsRoot();

  /** True iff a function changed since the last time a pass was run */
  abstract boolean hasScopeChanged(Node n);

  /** Passes that do cross-scope modifications use this (eg, InlineVariables) */
  abstract void reportChangeToEnclosingScope(Node n);

  /**
   * Returns true if compiling in IDE mode.
   */
  abstract boolean isIdeMode();

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

  static enum LifeCycleStage {
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
  abstract Node getRoot();

  abstract CompilerOptions getOptions();

  /**
   * The language mode of the current root node. This will match the languageIn
   * field of the {@link CompilerOptions} before transpilation happens, and
   * match the languageOut field after transpilation.
   */
  abstract CompilerOptions.LanguageMode getLanguageMode();

  abstract void setLanguageMode(CompilerOptions.LanguageMode mode);

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
   * @param progress A precentage expressed as a double in the range [0, 1].
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

  abstract void addComments(String filename, List<Comment> comments);

  /**
   * Returns all the comments from the given file.
   */
  abstract List<Comment> getComments(String filename);

   /**
    * Stores a map of default @define values.  These values
    * can be overriden by values specifically set in the CompilerOptions.
    */
   abstract void setDefaultDefineValues(ImmutableMap<String, Node> values);

   /**
    * Gets a map of default @define values.  These values
    * can be overriden by values specifically set in the CompilerOptions.
    */
   abstract ImmutableMap<String, Node> getDefaultDefineValues();
}
