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
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.primitives.Chars;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.RestrictedApi;
import com.google.javascript.jscomp.annotations.LegacySetFeatureSetCaller;
import com.google.javascript.jscomp.base.Tri;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.jscomp.parsing.Config;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.resources.ResourceLoader;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.SourcePosition;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.nullness.Nullable;

/** Compiler options */
public class CompilerOptions implements Serializable {
  // The number of characters after which we insert a line break in the code
  static final int DEFAULT_LINE_LENGTH_THRESHOLD = 500;

  private static final char[] POLYMER_PROPERTY_RESERVED_FIRST_CHARS =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZ$".toCharArray();
  private static final char[] POLYMER_PROPERTY_RESERVED_NON_FIRST_CHARS = "_$".toCharArray();
  private static final char[] ANGULAR_PROPERTY_RESERVED_FIRST_CHARS = {'$'};

  public static ImmutableSet<Character> getAngularPropertyReservedFirstChars() {
    return ImmutableSet.copyOf(Chars.asList(ANGULAR_PROPERTY_RESERVED_FIRST_CHARS));
  }

  public boolean shouldRunCrossChunkCodeMotion() {
    return crossChunkCodeMotion;
  }

  public boolean shouldRunCrossChunkMethodMotion() {
    return crossChunkMethodMotion;
  }

  public String getSourceMapOutputPath() {
    return sourceMapOutputPath;
  }

  public boolean shouldGatherSourceMapInfo() {
    return shouldAlwaysGatherSourceMapInfo || !Strings.isNullOrEmpty(sourceMapOutputPath);
  }

  public void setAlwaysGatherSourceMapInfo(boolean shouldAlwaysGatherSourceMapInfo) {
    this.shouldAlwaysGatherSourceMapInfo = shouldAlwaysGatherSourceMapInfo;
  }

  /** A common enum for compiler passes that can run either globally or locally. */
  public enum Reach {
    ALL,
    LOCAL_ONLY,
    NONE;

    public boolean isOn() {
      return this != NONE;
    }

    public boolean includesGlobals() {
      return this == ALL;
    }
  }

  public enum PropertyCollapseLevel {
    ALL,
    NONE,
    MODULE_EXPORT
  }

  // TODO(nicksantos): All public properties of this class should be made
  // package-private, and have a public setter.

  /** Should the compiled output start with "'use strict';"? */
  private Optional<Boolean> emitUseStrict = Optional.absent();

  /** The JavaScript language version accepted. */
  private LanguageMode languageIn;

  /** The JavaScript features that are allowed to be in the output. */
  private Optional<FeatureSet> outputFeatureSet = Optional.absent();

  private ImmutableList<ExperimentalForceTranspile> experimentalForceTranspiles =
      ImmutableList.of();

  private Optional<Boolean> languageOutIsDefaultStrict = Optional.absent();

  /** The builtin set of externs to be used */
  private Environment environment;

  /**
   * Represents browser feature set year to use for compilation. It tells the JSCompiler to output
   * code that works on the releases of major browsers that were current as of January 1 of the
   * given year, without including transpilation or other workarounds for browsers older than that
   */
  private class BrowserFeaturesetYear implements Serializable {

    final int year;

    BrowserFeaturesetYear(int year) {
      checkState(
          year == 2012 || (year >= 2018 && year <= 2023),
          "Illegal browser_featureset_year=%s. We support values 2012, or 2018..2023 only",
          year);
      this.year = year;
    }

    void setDependentValuesFromYear() {
      if (year == 2023) {
        setOutputFeatureSet(FeatureSet.BROWSER_2023);
      } else if (year == 2022) {
        setOutputFeatureSet(FeatureSet.BROWSER_2022);
      } else if (year == 2021) {
        setOutputFeatureSet(FeatureSet.BROWSER_2021);
      } else if (year == 2020) {
        setOutputFeatureSet(FeatureSet.BROWSER_2020);
      } else if (year == 2019) {
        setLanguageOut(LanguageMode.ECMASCRIPT_2017);
      } else if (year == 2018) {
        setLanguageOut(LanguageMode.ECMASCRIPT_2016);
      } else if (year == 2012) {
        setLanguageOut(LanguageMode.ECMASCRIPT5_STRICT);
      }
      setDefineToNumberLiteral("goog.FEATURESET_YEAR", year);
    }
  }

  /** Represents browserFeaturesetYear to use for compilation */
  private @Nullable BrowserFeaturesetYear browserFeaturesetYear;

  public int getBrowserFeaturesetYear() {
    return this.browserFeaturesetYear != null ? this.browserFeaturesetYear.year : 0;
  }

  public void setBrowserFeaturesetYear(int year) {
    this.browserFeaturesetYear = new BrowserFeaturesetYear(year);
    browserFeaturesetYear.setDependentValuesFromYear();
  }

  /**
   * Instrument code for the purpose of collecting coverage data - restrict to coverage pass only,
   * and skip all other passes.
   */
  private boolean instrumentForCoverageOnly = false;

  public void setInstrumentForCoverageOnly(boolean instrumentForCoverageOnly) {
    this.instrumentForCoverageOnly = instrumentForCoverageOnly;
  }

  public boolean getInstrumentForCoverageOnly() {
    return instrumentForCoverageOnly;
  }

  private @Nullable Path typedAstOutputFile = null;

  /**
   * Sets file to output in-progress TypedAST format to. DO NOT USE!
   *
   * <p>The "TypedAST format" is currently a gzipped TypedAst proto but this is not stable.
   */
  public void setTypedAstOutputFile(@Nullable Path file) {
    this.typedAstOutputFile = file;
  }

  @Nullable Path getTypedAstOutputFile() {
    return this.typedAstOutputFile;
  }

  private boolean mergedPrecompiledLibraries = false;

  void setMergedPrecompiledLibraries(boolean mergedPrecompiledLibraries) {
    this.mergedPrecompiledLibraries = mergedPrecompiledLibraries;
  }

  boolean getMergedPrecompiledLibraries() {
    return this.mergedPrecompiledLibraries;
  }

  @Deprecated
  public void setSkipTranspilationAndCrash(boolean value) {}

  /**
   * Sets the input sourcemap files, indexed by the JS files they refer to.
   *
   * @param inputSourceMaps the collection of input sourcemap files
   */
  public void setInputSourceMaps(final ImmutableMap<String, SourceMapInput> inputSourceMaps) {
    this.inputSourceMaps = inputSourceMaps;
  }

  /**
   * Whether to infer consts. This should not be configurable by external clients. This is a
   * transitional flag for a new type of const analysis.
   *
   * <p>TODO(nicksantos): Remove this option.
   */
  boolean inferConsts = true;

  // TODO(tbreisacher): Remove this method after ctemplate issues are solved.
  public void setInferConst(boolean value) {
    inferConsts = value;
  }

  /**
   * Whether the compiler should assume that a function's "this" value never needs coercion (for
   * example in non-strict "null" or "undefined" will be coerced to the global "this" and primitives
   * to objects).
   */
  private boolean assumeStrictThis;

  private boolean preserveDetailedSourceInfo = false;
  private boolean preserveNonJSDocComments = false;
  private boolean continueAfterErrors = false;

  public enum IncrementalCheckMode {
    /** Normal mode */
    OFF,

    /**
     * The compiler should generate an output file that represents the type-only interface of the
     * code being compiled. This is useful for incremental type checking.
     */
    GENERATE_IJS,

    /**
     * The compiler should run the same checks as used during type-only interface generation, but
     * run them after typechecking to give better error messages. This only makes sense in
     * --checks_only mode.
     */
    RUN_IJS_CHECKS_LATE,
  }

  private IncrementalCheckMode incrementalCheckMode = IncrementalCheckMode.OFF;

  public void setIncrementalChecks(IncrementalCheckMode value) {
    incrementalCheckMode = value;
    switch (value) {
      case OFF:
      case RUN_IJS_CHECKS_LATE:
        break;
      case GENERATE_IJS:
        setPreserveTypeAnnotations(true);
        setOutputJs(OutputJs.NORMAL);
        break;
    }
  }

  public boolean shouldGenerateTypedExterns() {
    return incrementalCheckMode == IncrementalCheckMode.GENERATE_IJS;
  }

  public boolean shouldRunTypeSummaryChecksLate() {
    return incrementalCheckMode == IncrementalCheckMode.RUN_IJS_CHECKS_LATE;
  }

  private Config.JsDocParsing parseJsDocDocumentation = Config.JsDocParsing.TYPES_ONLY;

  private boolean printExterns;

  void setPrintExterns(boolean printExterns) {
    this.printExterns = printExterns;
  }

  boolean shouldPrintExterns() {
    return this.printExterns || incrementalCheckMode == IncrementalCheckMode.GENERATE_IJS;
  }

  /** Even if checkTypes is disabled, clients such as IDEs might want to still infer types. */
  boolean inferTypes;

  /**
   * Configures the compiler to skip as many passes as possible. If transpilation is requested, it
   * will be run, but all others passes will be skipped.
   */
  boolean skipNonTranspilationPasses;

  /**
   * Configures the compiler to run expensive validity checks after every pass. Only intended for
   * internal development.
   */
  DevMode devMode;

  /**
   * Configures the compiler to log a hash code of the AST after every pass. Only intended for
   * internal development.
   */
  private boolean checkDeterminism;

  // --------------------------------
  // Input Options
  // --------------------------------

  private DependencyOptions dependencyOptions = DependencyOptions.none();

  /** Returns localized replacement for MSG_* variables */
  public @Nullable MessageBundle messageBundle = null;

  /** Whether we should report an error if a message is absent from a bundle. */
  private boolean strictMessageReplacement;

  // --------------------------------
  // Checks
  // --------------------------------

  /** Checks that all symbols are defined */
  // TODO(tbreisacher): Remove this and deprecate the corresponding setter.
  public boolean checkSymbols;

  /** Checks for suspicious statements that have no effect */
  public boolean checkSuspiciousCode;

  /** Checks types on expressions */
  public boolean checkTypes;

  /** Deprecated. Please use setWarningLevel(DiagnosticGroups.GLOBAL_THIS, level) instead. */
  @Deprecated
  public void setCheckGlobalThisLevel(CheckLevel level) {}

  /**
   * A set of extra annotation names which are accepted and silently ignored when encountered in a
   * source file. Defaults to null which has the same effect as specifying an empty set.
   */
  @Nullable Set<String> extraAnnotationNames;

  // TODO(bradfordcsmith): Investigate how can we use multi-threads as default.
  int numParallelThreads = 1;

  /**
   * Sets the level of parallelism for compilation passes that can exploit multi-threading.
   *
   * <p>Some compiler passes may take advantage of multi-threading, for example, parsing inputs.
   * This sets the level of parallelism. The compiler will not start more than this number of
   * threads.
   *
   * @param parallelism up to this number of parallel threads may be created.
   */
  public void setNumParallelThreads(int parallelism) {
    numParallelThreads = parallelism;
  }

  // --------------------------------
  // Optimizations
  // --------------------------------

  /** Folds constants (e.g. (2 + 3) to 5) */
  public boolean foldConstants;

  /** Remove assignments to values that can not be referenced */
  public boolean deadAssignmentElimination;

  /** Inlines constants (symbols that are all CAPS) */
  public boolean inlineConstantVars;

  /** For projects that want to avoid the creation of giant functions after inlining. */
  int maxFunctionSizeAfterInlining;

  static final int UNLIMITED_FUN_SIZE_AFTER_INLINING = -1;

  /** More aggressive function inlining */
  boolean assumeClosuresOnlyCaptureReferences;

  /** Inlines properties */
  private boolean inlineProperties;

  /** Move code to a deeper chunk */
  private boolean crossChunkCodeMotion;

  /**
   * Don't generate stub functions when moving methods deeper.
   *
   * <p>Note, switching on this option may break existing code that depends on enumerating prototype
   * methods for mixin behavior, such as goog.object.extend, since the prototype assignments will be
   * removed from the parent chunk and moved to a later chunk.
   */
  boolean crossChunkCodeMotionNoStubMethods;

  /**
   * Whether when chunk B depends on chunk A and chunk B declares a symbol, this symbol can be seen
   * in A after B has been loaded. This is often true, but may not be true when loading code using
   * nested eval.
   */
  boolean parentChunkCanSeeSymbolsDeclaredInChildren;

  /** Merge two variables together as one. */
  public boolean coalesceVariableNames;

  /** Move methods to a deeper chunk */
  private boolean crossChunkMethodMotion;

  /** Inlines trivial getters */
  boolean inlineGetters;

  /** Inlines variables */
  public boolean inlineVariables;

  /** Inlines variables */
  boolean inlineLocalVariables;

  // TODO(user): This is temporary. Once flow sensitive inlining is stable
  // Remove this.
  public boolean flowSensitiveInlineVariables;

  /** Removes code associated with unused global names */
  public boolean smartNameRemoval;

  /** Removes code that will never execute */
  public boolean removeDeadCode;

  public enum ExtractPrototypeMemberDeclarationsMode {
    OFF,
    USE_GLOBAL_TEMP,
    USE_CHUNK_TEMP,
    USE_IIFE
  }

  /** Extracts common prototype member declarations */
  ExtractPrototypeMemberDeclarationsMode extractPrototypeMemberDeclarations;

  /** Removes unused member prototypes */
  public boolean removeUnusedPrototypeProperties;

  /** Removes unused member properties */
  public boolean removeUnusedClassProperties;

  /** Removes unused constructor properties */
  boolean removeUnusedConstructorProperties;

  /** Removes unused variables */
  public boolean removeUnusedVars;

  /** Removes unused variables in local scope. */
  public boolean removeUnusedLocalVars;

  /** Collapses multiple variable declarations into one */
  public boolean collapseVariableDeclarations;

  /** Collapses anonymous function declarations into named function declarations */
  public boolean collapseAnonymousFunctions;

  /** Aliases string literals to global instances, to reduce code size. */
  private AliasStringsMode aliasStringsMode;

  /** Print string usage as part of the compilation log. */
  boolean outputJsStringUsage;

  /** Converts quoted property accesses to dot syntax (a['b'] &rarr; a.b) */
  public boolean convertToDottedProperties;

  /** Reduces the size of common function expressions. */
  public boolean rewriteFunctionExpressions;

  /**
   * Remove unused function arguments, remove unused return values, and inlines constant parameters.
   */
  public boolean optimizeCalls;

  /** Removes trivial constructors where ES class implicit constructors are sufficient. */
  boolean optimizeESClassConstructors;

  /** Provide formal names for elements of arguments array. */
  public boolean optimizeArgumentsArray;

  /** Use type information to enable additional optimization opportunities. */
  boolean useTypesForLocalOptimization;

  boolean useSizeHeuristicToStopOptimizationLoop = true;

  /**
   * Do up to this many iterations of the optimization loop. Setting this field to some small
   * number, say 3 or 4, allows a large project to build faster, but sacrifice some code size.
   */
  int optimizationLoopMaxIterations;

  // --------------------------------
  // Renaming
  // --------------------------------

  /** Controls which variables get renamed. */
  public VariableRenamingPolicy variableRenaming;

  /** Controls which properties get renamed. */
  PropertyRenamingPolicy propertyRenaming;

  /** Controls if property renaming only compilation mode is needed. */
  private boolean propertyRenamingOnlyCompilationMode;

  /** Controls label renaming. */
  public boolean labelRenaming;

  /** Reserve property names on the global this object. */
  public boolean reserveRawExports;

  /**
   * Use a renaming heuristic with better stability across source changes. With this option each
   * symbol is more likely to receive the same name between builds. The cost may be a slight
   * increase in code size.
   */
  boolean preferStableNames;

  /** Generate pseudo names for variables and properties for debugging purposes. */
  public boolean generatePseudoNames;

  /** Specifies a prefix for all globals */
  public @Nullable String renamePrefix;

  /** Specifies the name of an object that will be used to store all non-extern globals. */
  public String renamePrefixNamespace;

  /**
   * Used by tests of the RescopeGlobalSymbols pass to avoid having declare 2 chunks in simple
   * cases.
   */
  boolean renamePrefixNamespaceAssumeCrossChunkNames = false;

  /** Useful for tests to avoid having to declare two chunks */
  @VisibleForTesting
  public void setRenamePrefixNamespaceAssumeCrossChunkNames(boolean assume) {
    renamePrefixNamespaceAssumeCrossChunkNames = assume;
  }

  private PropertyCollapseLevel collapsePropertiesLevel;

  /**
   * Flattens multi-level property names (e.g. a$b = x)
   *
   * @deprecated use getPropertyCollapseLevel
   */
  @Deprecated
  public boolean shouldCollapseProperties() {
    return collapsePropertiesLevel != PropertyCollapseLevel.NONE;
  }

  public PropertyCollapseLevel getPropertyCollapseLevel() {
    return collapsePropertiesLevel;
  }

  /** Split object literals into individual variables when possible. */
  boolean collapseObjectLiterals;

  public void setCollapseObjectLiterals(boolean enabled) {
    collapseObjectLiterals = enabled;
  }

  public boolean getCollapseObjectLiterals() {
    return collapseObjectLiterals;
  }

  /**
   * Devirtualize prototype method by rewriting them to be static calls that take the this pointer
   * as their first argument
   */
  public boolean devirtualizeMethods;

  /**
   * Use @nosideeffects annotations, function bodies and name graph to determine if calls have side
   * effects.
   */
  public boolean computeFunctionSideEffects;

  /** Rename properties to disambiguate between unrelated fields based on type information. */
  private boolean disambiguateProperties;

  /** Rename unrelated properties to the same name to reduce code size. */
  private boolean ambiguateProperties;

  /** Input sourcemap files, indexed by the JS files they refer to */
  ImmutableMap<String, SourceMapInput> inputSourceMaps;

  /**
   * Input variable renaming map.
   *
   * <p>During renaming, the compiler uses this map and the inputPropertyMap to try to preserve
   * renaming mappings from a previous compilation. The application is delta encoding: keeping the
   * diff between consecutive versions of one's code small. The compiler does NOT guarantee to
   * respect these maps; projects should not use these maps to prevent renaming or to select
   * particular names. Point questioners to this post:
   * http://closuretools.blogspot.com/2011/01/property-by-any-other-name-part-3.html
   */
  VariableMap inputVariableMap;

  /** Input property renaming map. */
  VariableMap inputPropertyMap;

  /** Whether to export test functions. */
  public boolean exportTestFunctions;

  /** Shared name generator */
  NameGenerator nameGenerator;

  public void setNameGenerator(NameGenerator nameGenerator) {
    this.nameGenerator = nameGenerator;
  }

  // --------------------------------
  // Special-purpose alterations
  // --------------------------------

  /** Replace UI strings with chrome.i18n.getMessage calls. Used by Chrome extensions/apps. */
  private boolean replaceMessagesWithChromeI18n;

  String tcProjectId;

  public void setReplaceMessagesWithChromeI18n(
      boolean replaceMessagesWithChromeI18n, String tcProjectId) {
    if (replaceMessagesWithChromeI18n
        && messageBundle != null
        && !(messageBundle instanceof EmptyMessageBundle)) {
      throw new RuntimeException(
          "When replacing messages with"
              + " chrome.i18n.getMessage, a message bundle should not be specified.");
    }

    this.replaceMessagesWithChromeI18n = replaceMessagesWithChromeI18n;
    this.tcProjectId = tcProjectId;
  }

  /**
   * Should we run the pass that does replacement of the chrome-specific `chrome.i18n.getMessage()`
   * translatable message definitions?
   *
   * <p>This form of l10n is incompatible with our standard `goog.getMsg()` messages.
   */
  public boolean shouldRunReplaceMessagesForChrome() {
    if (replaceMessagesWithChromeI18n) {
      checkState(
          messageBundle == null || messageBundle instanceof EmptyMessageBundle,
          "When replacing messages with chrome.i18n.getMessage, a message bundle should not be"
              + " specified.");
      checkState(
          !doLateLocalization, "Late localization is not supported for chrome.i18n.getMessage");
      return true;
    } else {
      return false;
    }
  }

  /** A CodingConvention to use during the compile. */
  private CodingConvention codingConvention;

  public @Nullable String syntheticBlockStartMarker;
  public @Nullable String syntheticBlockEndMarker;

  /** Compiling locale */
  public @Nullable String locale;

  /**
   * If true, then perform localization passes as late as possible.
   *
   * <p>At the moment this only affects when `ReplaceMessages` does the work of inserting the
   * localized form of declared messages from the message bundle.
   *
   * <p>TODO(johnlenz): Use this option to control late substitution of other locale-specific code.
   */
  private boolean doLateLocalization;

  /** Sets the special "COMPILED" value to true */
  public boolean markAsCompiled;

  /** Processes goog.provide() and goog.require() calls */
  public boolean closurePass;

  /** Do not strip goog.provide()/goog.require() calls from the code. */
  private boolean preserveClosurePrimitives;

  /** Processes AngularJS-specific annotations */
  boolean angularPass;

  /** If non-null, processes Polymer code */
  @Nullable Integer polymerVersion;

  /** How to handle exports/externs for Polymer properties and methods. */
  PolymerExportPolicy polymerExportPolicy;

  /** Processes cr.* functions */
  private boolean chromePass;

  /** Processes the output of J2CL */
  J2clPassMode j2clPassMode;

  boolean j2clMinifierEnabled = true;

  @Nullable String j2clMinifierPruningManifest = null;

  /** Remove goog.abstractMethod assignments and @abstract methods. */
  boolean removeAbstractMethods;

  /** Remove goog.asserts calls. */
  boolean removeClosureAsserts;

  /** Remove J2CL assert calls. */
  boolean removeJ2clAsserts = true;

  /** Gather CSS names (requires closurePass) */
  public boolean gatherCssNames;

  /** Names of types to strip */
  ImmutableSet<String> stripTypes;

  /** Name suffixes that determine which variables and properties to strip */
  ImmutableSet<String> stripNameSuffixes;

  /** Name prefixes that determine which variables and properties to strip */
  ImmutableSet<String> stripNamePrefixes;

  /** Custom passes */
  protected transient @Nullable Multimap<CustomPassExecutionTime, CompilerPass> customPasses;

  /** Replacements for @defines. Will be Boolean, Numbers, or Strings */
  private final LinkedHashMap<String, Object> defineReplacements;

  /** What kind of processing to do for goog.tweak functions. */
  private TweakProcessing tweakProcessing;

  /** Move top-level function declarations to the top */
  public boolean rewriteGlobalDeclarationsForTryCatchWrapping;

  boolean checksOnly;

  /** What type of JS file should be output by this compilation */
  public static enum OutputJs {
    // Don't output anything.
    NONE,
    // Output a "sentinel" file containing just a comment.
    SENTINEL,
    // Output the compiled JS.
    NORMAL,
  }

  OutputJs outputJs;

  public boolean generateExports;

  boolean exportLocalPropertyDefinitions;

  /** Map used in the renaming of CSS class names. */
  public @Nullable CssRenamingMap cssRenamingMap;

  /** Skiplist used in the renaming of CSS class names. */
  @Nullable Set<String> cssRenamingSkiplist;

  /** Replace id generators */
  boolean replaceIdGenerators = true; // true by default for legacy reasons.

  /** Id generators to replace. */
  ImmutableMap<String, RenamingMap> idGenerators;

  /** Hash function to use for xid generation. */
  Xid.HashFunction xidHashFunction;

  /** Hash function to use for generating chunk ID. */
  Xid.HashFunction chunkIdHashFunction;

  /**
   * A previous map of ids (serialized to a string by a previous compile). This will be used as a
   * hint during the ReplaceIdGenerators pass, which will attempt to reuse the same ids.
   */
  String idGeneratorsMapSerialized;

  /** Configuration strings */
  List<String> replaceStringsFunctionDescriptions;

  String replaceStringsPlaceholderToken;
  // A previous map of replacements to strings.
  VariableMap replaceStringsInputMap;

  /** List of properties that we report invalidation errors for. */
  private ImmutableSet<String> propertiesThatMustDisambiguate;

  /** Rewrite CommonJS modules so that they can be concatenated together. */
  private boolean processCommonJSModules = false;

  /** CommonJS module prefix. */
  List<String> moduleRoots = ImmutableList.of(ModuleLoader.DEFAULT_FILENAME_PREFIX);

  /** Inject polyfills */
  boolean rewritePolyfills = false;

  /** Isolates injected polyfills from the global scope. */
  private boolean isolatePolyfills = false;

  /** Runtime libraries to always inject. */
  List<String> forceLibraryInjection = ImmutableList.of();

  /** Runtime libraries to never inject. */
  boolean preventLibraryInjection = false;

  boolean assumeForwardDeclaredForMissingTypes = false;

  /**
   * A Set of goog.requires to be removed. If null, ALL of the unused goog.requires will be counted
   * as a candidate to be removed.
   */
  @Nullable ImmutableSet<String> unusedImportsToRemove;

  /**
   * If {@code true}, considers all missing types to be forward declared (useful for partial
   * compilation).
   */
  public void setAssumeForwardDeclaredForMissingTypes(
      boolean assumeForwardDeclaredForMissingTypes) {
    this.assumeForwardDeclaredForMissingTypes = assumeForwardDeclaredForMissingTypes;
  }

  // --------------------------------
  // Output options
  // --------------------------------

  /** Do not strip closure-style type annotations from code. */
  public boolean preserveTypeAnnotations;

  /**
   * To distinguish between gents and non-gents mode so that we can turn off checking the sanity of
   * the source location of comments, and also provide a different mode for comment printing between
   * those two.
   */
  public boolean gentsMode;

  /** Output in pretty indented format */
  private boolean prettyPrint;

  /** Line break the output a bit more aggressively */
  public boolean lineBreak;

  /** Prints a separator comment before each JS script */
  public boolean printInputDelimiter;

  /** The string to use as the separator for printInputDelimiter */
  public String inputDelimiter = "// Input %num%";

  /**
   * A directory into which human readable debug log files can be written.
   *
   * <p>{@code null} indicates that no such files should be written.
   */
  private @Nullable Path debugLogDirectory;

  /**
   * A comma separated list of strings used to filter which debug logs are written. If a filter is
   * specified, only log files with a path name that contains at least one of the filter Strings are
   * written.
   *
   * <p>{@code null} indicates that no such files should be written.
   */
  private String debugLogFilter;

  private boolean serializeExtraDebugInfo;

  /** Whether to write keyword properties as foo['class'] instead of foo.class; needed for IE8. */
  private boolean quoteKeywordProperties;

  boolean preferSingleQuotes;

  /**
   * Normally, when there are an equal number of single and double quotes in a string, the compiler
   * will use double quotes. Set this to true to prefer single quotes.
   */
  public void setPreferSingleQuotes(boolean enabled) {
    this.preferSingleQuotes = enabled;
  }

  boolean trustedStrings;

  /**
   * Some people want to put arbitrary user input into strings, which are then run through the
   * compiler. These scripts are then put into HTML. By default, we assume strings are untrusted. If
   * the compiler is run from the command-line, we assume that strings are trusted.
   */
  public void setTrustedStrings(boolean yes) {
    trustedStrings = yes;
  }

  // Should only be used when debugging compiler bugs.
  boolean printSourceAfterEachPass;

  // Used to narrow down the printed source when overall input size is large. If these are both
  // empty the entire source is printed.
  List<String> filesToPrintAfterEachPassRegexList = ImmutableList.of();
  List<String> chunksToPrintAfterEachPassRegexList = ImmutableList.of();
  List<String> qnameUsesToPrintAfterEachPassList = ImmutableList.of();

  public void setPrintSourceAfterEachPass(boolean printSource) {
    this.printSourceAfterEachPass = printSource;
  }

  public void setFilesToPrintAfterEachPassRegexList(List<String> filePathRegexList) {
    this.filesToPrintAfterEachPassRegexList = filePathRegexList;
  }

  public void setChunksToPrintAfterEachPassRegexList(List<String> chunkPathRegexList) {
    this.chunksToPrintAfterEachPassRegexList = chunkPathRegexList;
  }

  public void setQnameUsesToPrintAfterEachPassList(List<String> qnameRegexList) {
    this.qnameUsesToPrintAfterEachPassList = qnameRegexList;
  }

  private TracerMode tracer;

  public TracerMode getTracerMode() {
    return tracer;
  }

  // NOTE: Timing information will not be printed if compiler.disableThreads() is called!
  public void setTracerMode(TracerMode mode) {
    this.tracer = mode;
  }

  private Path tracerOutput;

  Path getTracerOutput() {
    return tracerOutput;
  }

  public void setTracerOutput(Path out) {
    tracerOutput = out;
  }

  private boolean colorizeErrorOutput;

  public ErrorFormat errorFormat;

  private ComposeWarningsGuard warningsGuard = new ComposeWarningsGuard();

  int summaryDetailLevel = 1;

  int lineLengthThreshold = DEFAULT_LINE_LENGTH_THRESHOLD;

  /**
   * Whether to use the original names of nodes in the code output. This option is only really
   * useful when using the compiler to print code meant to check in to source.
   */
  boolean useOriginalNamesInOutput = false;

  // --------------------------------
  // Special Output Options
  // --------------------------------

  /** The output path for the created externs file. */
  private @Nullable String externExportsPath;

  private final List<SortingErrorManager.ErrorReportGenerator> extraReportGenerators =
      new ArrayList<>();

  List<SortingErrorManager.ErrorReportGenerator> getExtraReportGenerators() {
    return extraReportGenerators;
  }

  void addReportGenerator(SortingErrorManager.ErrorReportGenerator generator) {
    extraReportGenerators.add(generator);
  }

  // --------------------------------
  // Debugging Options
  // --------------------------------

  /** The output path for the source map. */
  private String sourceMapOutputPath;

  /**
   * Should we gather information needed to generate a source map even when sourceMapOutputPath
   * indicates otherwise?
   *
   * <p>This is necessary in cases where we're doing a partial compilation, because the source map
   * output path may not be known until the final stage, but we must still gather information in the
   * early stages.
   */
  private boolean shouldAlwaysGatherSourceMapInfo = false;

  /** The detail level for the generated source map. */
  public SourceMap.DetailLevel sourceMapDetailLevel = SourceMap.DetailLevel.ALL;

  /** The source map file format */
  public SourceMap.Format sourceMapFormat = SourceMap.Format.DEFAULT;

  /** Whether to parse inline source maps. */
  boolean parseInlineSourceMaps = true;

  /**
   * Whether to apply input source maps to the output, i.e. map back to original inputs from input
   * files that have source maps applied to them.
   */
  boolean applyInputSourceMaps = false;

  /**
   * Whether to resolve source mapping annotations. Cannot do this in an appengine or js environment
   * since we don't have access to the filesystem.
   */
  boolean resolveSourceMapAnnotations = true;

  public List<? extends SourceMap.LocationMapping> sourceMapLocationMappings = ImmutableList.of();

  /** Whether to include full file contents in the source map. */
  boolean sourceMapIncludeSourcesContent = false;

  /** Charset to use when generating code. If null, then output ASCII. */
  transient Charset outputCharset;

  /** When set, assume that apparently side-effect free code is meaningful. */
  private boolean protectHiddenSideEffects;

  /** When enabled, assume that apparently side-effect free code is meaningful. */
  public void setProtectHiddenSideEffects(boolean enable) {
    this.protectHiddenSideEffects = enable;
  }

  /**
   * Whether or not the compiler should wrap apparently side-effect free code to prevent it from
   * being removed
   */
  public boolean shouldProtectHiddenSideEffects() {
    // TODO(lharker): would it make sense to make 'checksOnly' a tristate instead? either we're
    // running only checks for refactoring; running checks to generate a .typedast; or running
    // checks as part of a fully optimizing build?
    return protectHiddenSideEffects && (!checksOnly || getTypedAstOutputFile() != null);
  }

  /**
   * Ignore the possibility that getter invocations (gets) can have side-effects and that the
   * results of gets can be side-effected by local state mutations.
   *
   * <p>When {@code false}, it doesn't necessarily mean that all gets are considered side-effectful
   * or side-effected. Gets that can be proven to be pure may still be considered as such.
   *
   * <p>Recall that object-spread is capable of triggering getters. Since the syntax doesn't
   * explicitly specify a property, it is essentailly impossible to prove it has no side-effects
   * without this assumption.
   */
  private boolean assumeGettersArePure = true;

  public void setAssumeGettersArePure(boolean x) {
    this.assumeGettersArePure = x;
  }

  public boolean getAssumeGettersArePure() {
    return assumeGettersArePure;
  }

  /**
   * Assume that static (class-side) inheritance is not being used and that static methods will not
   * be referenced via `this` or through subclasses.
   *
   * <p>When {@code true}, the compiler is free to make unsafe (breaking) optimizations to code that
   * depends on static inheritance. These optimizations represent a substantial code-size reduction
   * for older projects and therefore cannot be unilaterally disabled. {@code true} was the
   * long-standing implicit assumption before static inheritance came about in ES2015.
   *
   * <p>Example of what may break if this flag is {@code true}:
   *
   * <pre>{@code
   * class Parent {
   *   static method() { }
   * }
   *
   * class Child extends Parent { }
   *
   * Child.method(); // `method` will not be defined.
   * }</pre>
   */
  private boolean assumeStaticInheritanceIsNotUsed = true;

  public void setAssumeStaticInheritanceIsNotUsed(boolean x) {
    this.assumeStaticInheritanceIsNotUsed = x;
  }

  public boolean getAssumeStaticInheritanceIsNotUsed() {
    return assumeStaticInheritanceIsNotUsed;
  }

  /** Data holder Alias Transformation information accumulated during a compile. */
  private transient AliasTransformationHandler aliasHandler;

  /** Handler for compiler warnings and errors. */
  transient @Nullable ErrorHandler errorHandler;

  private InstrumentOption instrumentForCoverageOption;

  private String productionInstrumentationArrayName;

  private static final ImmutableList<ConformanceConfig> GLOBAL_CONFORMANCE_CONFIGS =
      ImmutableList.of(ResourceLoader.loadGlobalConformance(CompilerOptions.class));

  /**
   * List of conformance configs to use in CheckConformance.
   *
   * <p>The first entry of this list is always the Global ConformanceConfig
   */
  private ImmutableList<ConformanceConfig> conformanceConfigs = GLOBAL_CONFORMANCE_CONFIGS;

  /**
   * Remove the first match of this regex from any paths when checking conformance whitelists.
   *
   * <p>You can use this to make absolute paths relative to the root of your source tree. This is
   * useful to work around CI and build systems that use absolute paths.
   */
  private Optional<Pattern> conformanceRemoveRegexFromPath =
      Optional.of(
          // The regex uses lookahead because we want to be able to identify generated files. For a
          // path like "blaze-out/directory/bin/some/file.js" we strip out the entire prefix,
          // resulting in a reported path of "some/file.js". For generated files, we only strip the
          // first two segments, leaving "genfiles/some/file.js".
          Pattern.compile("^((.*/)?google3/)?(/?(blaze|bazel)-out/[^/]+/(bin/|(?=genfiles/)))?"));

  public void setConformanceRemoveRegexFromPath(Optional<Pattern> pattern) {
    conformanceRemoveRegexFromPath = pattern;
  }

  public Optional<Pattern> getConformanceRemoveRegexFromPath() {
    return conformanceRemoveRegexFromPath;
  }

  /** For use in {@link CompilationLevel#WHITESPACE_ONLY} mode, when using goog.module. */
  boolean wrapGoogModulesForWhitespaceOnly = true;

  public void setWrapGoogModulesForWhitespaceOnly(boolean enable) {
    this.wrapGoogModulesForWhitespaceOnly = enable;
  }

  /** Print all configuration options to stderr after the compiler is initialized. */
  boolean printConfig = false;

  /** Are the input files written for strict mode? */
  private Optional<Boolean> isStrictModeInput = Optional.absent();

  // Store four unique states:
  //  - run module rewriting before typechecking
  //  - run module rewriting after typechecking
  //  - don't run module rewriting, but if it's reenabled, run it before typechecking
  //  - don't run module rewriting, but if it's reenabled, run it after typechecking
  private boolean rewriteModulesBeforeTypechecking = false;
  private boolean enableModuleRewriting = true;

  /** Whether to enable the bad module rewriting before typechecking that we want to get rid of */
  public void setBadRewriteModulesBeforeTypecheckingThatWeWantToGetRidOf(boolean b) {
    this.rewriteModulesBeforeTypechecking = b;
  }

  boolean shouldRewriteModulesBeforeTypechecking() {
    return this.enableModuleRewriting
        && (this.rewriteModulesBeforeTypechecking || this.processCommonJSModules);
  }

  /**
   * Experimental option to disable all Closure and ES module and goog.provide rewriting
   *
   * <p>Use at your own risk - disabling module rewriting is not fully tested yet.
   */
  public void setEnableModuleRewriting(boolean enable) {
    this.enableModuleRewriting = enable;
  }

  boolean shouldRewriteModulesAfterTypechecking() {
    return this.enableModuleRewriting && !this.rewriteModulesBeforeTypechecking;
  }

  boolean shouldRewriteModules() {
    return this.enableModuleRewriting;
  }

  /** Which algorithm to use for locating ES6 and CommonJS modules */
  ResolutionMode moduleResolutionMode;

  /**
   * Map of prefix replacements for use when moduleResolutionMode is {@link
   * ResolutionMode#BROWSER_WITH_TRANSFORMED_PREFIXES}.
   */
  private ImmutableMap<String, String> browserResolverPrefixReplacements;

  private ModuleLoader.PathEscaper pathEscaper;

  /** Which entries to look for in package.json files when processing modules */
  List<String> packageJsonEntryNames;

  /**
   * Should the compiler print its configuration options to stderr when they are initialized?
   *
   * <p>Default {@code false}.
   */
  public void setPrintConfig(boolean printConfig) {
    this.printConfig = printConfig;
  }

  private boolean allowDynamicImport = false;

  /** Whether to enable support for dynamic import expressions */
  public void setAllowDynamicImport(boolean value) {
    this.allowDynamicImport = value;
  }

  boolean shouldAllowDynamicImport() {
    return this.allowDynamicImport;
  }

  private @Nullable String dynamicImportAlias = null;

  /** Set the alias name for dynamic import expressions */
  public String getDynamicImportAlias() {
    return this.dynamicImportAlias;
  }

  /** Set the alias name for dynamic import expressions */
  public void setDynamicImportAlias(String value) {
    this.dynamicImportAlias = value;
  }

  boolean shouldAliasDynamicImport() {
    return this.dynamicImportAlias != null;
  }

  ChunkOutputType chunkOutputType;

  public List<String> parenthesizeFunctionsInChunks;

  /**
   * Initializes compiler options. All options are disabled by default.
   *
   * <p>Command-line frontends to the compiler should set these properties like a builder.
   */
  public CompilerOptions() {
    // Accepted language
    languageIn = LanguageMode.STABLE_IN;

    // Which environment to use
    environment = Environment.BROWSER;
    browserResolverPrefixReplacements = ImmutableMap.of();

    // Modules
    moduleResolutionMode = ResolutionMode.BROWSER;
    packageJsonEntryNames = ImmutableList.of("browser", "module", "main");
    pathEscaper = ModuleLoader.PathEscaper.ESCAPE;
    rewriteModulesBeforeTypechecking = false;
    enableModuleRewriting = true;

    // Checks
    skipNonTranspilationPasses = false;
    devMode = DevMode.OFF;
    checkDeterminism = false;
    checkSymbols = false;
    checkSuspiciousCode = false;
    checkTypes = false;
    computeFunctionSideEffects = false;
    extraAnnotationNames = null;

    // Optimizations
    foldConstants = false;
    coalesceVariableNames = false;
    deadAssignmentElimination = false;
    inlineConstantVars = false;
    inlineFunctionsLevel = Reach.NONE;
    maxFunctionSizeAfterInlining = UNLIMITED_FUN_SIZE_AFTER_INLINING;
    assumeStrictThis = false;
    assumeClosuresOnlyCaptureReferences = false;
    inlineProperties = false;
    crossChunkCodeMotion = false;
    parentChunkCanSeeSymbolsDeclaredInChildren = false;
    crossChunkMethodMotion = false;
    inlineGetters = false;
    inlineVariables = false;
    inlineLocalVariables = false;
    smartNameRemoval = false;
    removeDeadCode = false;
    extractPrototypeMemberDeclarations = ExtractPrototypeMemberDeclarationsMode.OFF;
    removeUnusedPrototypeProperties = false;
    removeUnusedClassProperties = false;
    removeUnusedVars = false;
    removeUnusedLocalVars = false;
    collapseVariableDeclarations = false;
    collapseAnonymousFunctions = false;
    aliasStringsMode = AliasStringsMode.NONE;
    outputJsStringUsage = false;
    convertToDottedProperties = false;
    rewriteFunctionExpressions = false;

    // Renaming
    variableRenaming = VariableRenamingPolicy.OFF;
    propertyRenaming = PropertyRenamingPolicy.OFF;
    propertyRenamingOnlyCompilationMode = false;
    labelRenaming = false;
    generatePseudoNames = false;
    preferStableNames = false;
    renamePrefix = null;
    collapsePropertiesLevel = PropertyCollapseLevel.NONE;
    collapseObjectLiterals = false;
    devirtualizeMethods = false;
    disambiguateProperties = false;
    ambiguateProperties = false;
    exportTestFunctions = false;
    nameGenerator = new DefaultNameGenerator();

    // Alterations

    syntheticBlockStartMarker = null;
    syntheticBlockEndMarker = null;
    locale = null;
    doLateLocalization = false;
    markAsCompiled = false;
    closurePass = false;
    preserveClosurePrimitives = false;
    angularPass = false;
    polymerVersion = null;
    polymerExportPolicy = PolymerExportPolicy.LEGACY;
    j2clPassMode = J2clPassMode.AUTO;
    j2clMinifierEnabled = true;
    removeAbstractMethods = false;
    removeClosureAsserts = false;
    stripTypes = ImmutableSet.of();
    stripNameSuffixes = ImmutableSet.of();
    stripNamePrefixes = ImmutableSet.of();
    customPasses = null;
    defineReplacements = new LinkedHashMap<>();
    tweakProcessing = TweakProcessing.OFF;
    rewriteGlobalDeclarationsForTryCatchWrapping = false;
    checksOnly = false;
    outputJs = OutputJs.NORMAL;
    generateExports = true;
    exportLocalPropertyDefinitions = true;
    cssRenamingMap = null;
    cssRenamingSkiplist = null;
    idGenerators = ImmutableMap.of();
    replaceStringsFunctionDescriptions = ImmutableList.of();
    replaceStringsPlaceholderToken = "";
    propertiesThatMustDisambiguate = ImmutableSet.of();
    inputSourceMaps = ImmutableMap.of();
    parenthesizeFunctionsInChunks = ImmutableList.of();

    instrumentForCoverageOption = InstrumentOption.NONE;
    productionInstrumentationArrayName = "";

    // Output
    preserveTypeAnnotations = false;
    gentsMode = false;
    printInputDelimiter = false;
    prettyPrint = false;
    lineBreak = false;
    tracer = TracerMode.OFF;
    colorizeErrorOutput = false;
    errorFormat = ErrorFormat.FULL;
    chunkOutputType = ChunkOutputType.GLOBAL_NAMESPACE;

    // Debugging
    aliasHandler = NULL_ALIAS_TRANSFORMATION_HANDLER;
    errorHandler = null;
    printSourceAfterEachPass = false;
    strictMessageReplacement = false;
  }

  /**
   * @return Whether to attempt to remove unused class properties
   */
  public boolean isRemoveUnusedClassProperties() {
    return removeUnusedClassProperties;
  }

  /**
   * @param removeUnusedClassProperties Whether to attempt to remove unused class properties
   */
  public void setRemoveUnusedClassProperties(boolean removeUnusedClassProperties) {
    this.removeUnusedClassProperties = removeUnusedClassProperties;
  }

  /** Returns the map of define replacements. */
  public ImmutableMap<String, Node> getDefineReplacements() {
    ImmutableMap.Builder<String, Node> map = ImmutableMap.builder();
    for (Map.Entry<String, Object> entry : this.defineReplacements.entrySet()) {
      String name = entry.getKey();
      Object value = entry.getValue();
      if (value instanceof Boolean) {
        map.put(name, NodeUtil.booleanNode(((Boolean) value).booleanValue()));
      } else if (value instanceof Number) {
        map.put(name, NodeUtil.numberNode(((Number) value).doubleValue(), null));
      } else if (value instanceof String) {
        map.put(name, IR.string((String) value));
      } else {
        throw new IllegalStateException(String.valueOf(value));
      }
    }
    return map.buildOrThrow();
  }

  /** Sets the value of the {@code @define} variable in JS to a boolean literal. */
  public void setDefineToBooleanLiteral(String defineName, boolean value) {
    this.defineReplacements.put(defineName, value);
  }

  /** Sets the value of the {@code @define} variable in JS to a String literal. */
  public void setDefineToStringLiteral(String defineName, String value) {
    this.defineReplacements.put(defineName, value);
  }

  /** Sets the value of the {@code @define} variable in JS to a number literal. */
  public void setDefineToNumberLiteral(String defineName, int value) {
    this.defineReplacements.put(defineName, value);
  }

  /** Sets the value of the {@code @define} variable in JS to a number literal. */
  public void setDefineToDoubleLiteral(String defineName, double value) {
    this.defineReplacements.put(defineName, value);
  }

  /** Skip all possible passes, to make the compiler as fast as possible. */
  public void skipAllCompilerPasses() {
    skipNonTranspilationPasses = true;
  }

  /** Whether the warnings guard in this Options object enables the given group of warnings. */
  boolean enables(DiagnosticGroup group) {
    return this.warningsGuard.mustRunChecks(group) == Tri.TRUE;
  }

  /** Whether the warnings guard in this Options object disables the given group of warnings. */
  boolean disables(DiagnosticGroup group) {
    return this.warningsGuard.mustRunChecks(group) == Tri.FALSE;
  }

  /** Configure the given type of warning to the given level. */
  public void setWarningLevel(DiagnosticGroup type, CheckLevel level) {
    addWarningsGuard(new DiagnosticGroupWarningsGuard(type, level));
  }

  WarningsGuard getWarningsGuard() {
    return this.warningsGuard;
  }

  /** Reset the warnings guard. */
  public void resetWarningsGuard() {
    this.warningsGuard = new ComposeWarningsGuard();
  }

  /** Add a guard to the set of warnings guards. */
  public void addWarningsGuard(WarningsGuard guard) {
    this.warningsGuard.addGuard(guard);
  }

  /**
   * Sets the variable and property renaming policies for the compiler, in a way that clears
   * warnings about the renaming policy being uninitialized from flags.
   */
  public void setRenamingPolicy(
      VariableRenamingPolicy newVariablePolicy, PropertyRenamingPolicy newPropertyPolicy) {
    this.variableRenaming = newVariablePolicy;
    this.propertyRenaming = newPropertyPolicy;
  }

  /**
   * @param replaceIdGenerators the replaceIdGenerators to set
   */
  public void setReplaceIdGenerators(boolean replaceIdGenerators) {
    this.replaceIdGenerators = replaceIdGenerators;
  }

  /** Sets the id generators to replace. */
  public void setIdGenerators(Set<String> idGenerators) {
    RenamingMap gen = new UniqueRenamingToken();
    ImmutableMap.Builder<String, RenamingMap> builder = ImmutableMap.builder();
    for (String name : idGenerators) {
      builder.put(name, gen);
    }
    this.idGenerators = builder.buildOrThrow();
  }

  /** Sets the id generators to replace. */
  public void setIdGenerators(Map<String, RenamingMap> idGenerators) {
    this.idGenerators = ImmutableMap.copyOf(idGenerators);
  }

  /**
   * A previous map of ids (serialized to a string by a previous compile). This will be used as a
   * hint during the ReplaceIdGenerators pass, which will attempt to reuse the same ids.
   */
  public void setIdGeneratorsMap(String previousMappings) {
    this.idGeneratorsMapSerialized = previousMappings;
  }

  /** Sets the hash function to use for Xid */
  public void setXidHashFunction(Xid.HashFunction xidHashFunction) {
    this.xidHashFunction = xidHashFunction;
  }

  /** Sets the hash function to use for chunk ID generation */
  public void setChunkIdHashFunction(Xid.HashFunction chunkIdHashFunction) {
    this.chunkIdHashFunction = chunkIdHashFunction;
  }

  private Reach inlineFunctionsLevel;

  /** Set the function inlining policy for the compiler. */
  public void setInlineFunctions(Reach reach) {
    this.inlineFunctionsLevel = reach;
  }

  /** Get the function inlining policy for the compiler. */
  public Reach getInlineFunctionsLevel() {
    return this.inlineFunctionsLevel;
  }

  public void setMaxFunctionSizeAfterInlining(int funAstSize) {
    checkArgument(funAstSize > 0);
    this.maxFunctionSizeAfterInlining = funAstSize;
  }

  public void setInlineVariables(boolean inlineVariables) {
    this.inlineVariables = inlineVariables;
  }

  /** Set the variable inlining policy for the compiler. */
  public void setInlineVariables(Reach reach) {
    switch (reach) {
      case ALL:
        this.inlineVariables = true;
        this.inlineLocalVariables = true;
        break;
      case LOCAL_ONLY:
        this.inlineVariables = false;
        this.inlineLocalVariables = true;
        break;
      case NONE:
        this.inlineVariables = false;
        this.inlineLocalVariables = false;
        break;
    }
  }

  /** Set the function inlining policy for the compiler. */
  public void setInlineProperties(boolean enable) {
    inlineProperties = enable;
  }

  public boolean shouldInlineProperties() {
    return inlineProperties;
  }

  /** Set the variable removal policy for the compiler. */
  public void setRemoveUnusedVariables(Reach reach) {
    switch (reach) {
      case ALL:
        this.removeUnusedVars = true;
        this.removeUnusedLocalVars = true;
        break;
      case LOCAL_ONLY:
        this.removeUnusedVars = false;
        this.removeUnusedLocalVars = true;
        break;
      case NONE:
        this.removeUnusedVars = false;
        this.removeUnusedLocalVars = false;
        break;
    }
  }

  /** Sets the functions whose debug strings to replace. */
  public void setReplaceStringsConfiguration(
      String placeholderToken, List<String> functionDescriptors) {
    this.replaceStringsPlaceholderToken = placeholderToken;
    this.replaceStringsFunctionDescriptions = new ArrayList<>(functionDescriptors);
  }

  public void setRemoveAbstractMethods(boolean remove) {
    this.removeAbstractMethods = remove;
  }

  public void setRemoveClosureAsserts(boolean remove) {
    this.removeClosureAsserts = remove;
  }

  public void setRemoveJ2clAsserts(boolean remove) {
    this.removeJ2clAsserts = remove;
  }

  public void setColorizeErrorOutput(boolean colorizeErrorOutput) {
    this.colorizeErrorOutput = colorizeErrorOutput;
  }

  public boolean shouldColorizeErrorOutput() {
    return colorizeErrorOutput;
  }

  public void setChecksOnly(boolean checksOnly) {
    this.checksOnly = checksOnly;
  }

  public void setOutputJs(OutputJs outputJs) {
    this.outputJs = outputJs;
  }

  public void setGenerateExports(boolean generateExports) {
    this.generateExports = generateExports;
  }

  public void setExportLocalPropertyDefinitions(boolean export) {
    this.exportLocalPropertyDefinitions = export;
  }

  public boolean shouldExportLocalPropertyDefinitions() {
    return this.exportLocalPropertyDefinitions;
  }

  public void setAngularPass(boolean angularPass) {
    this.angularPass = angularPass;
  }

  public void setPolymerVersion(Integer polymerVersion) {
    checkArgument(
        polymerVersion == null || polymerVersion == 1 || polymerVersion == 2,
        "Invalid Polymer version: (%s)",
        polymerVersion);
    this.polymerVersion = polymerVersion;
  }

  public void setPolymerExportPolicy(PolymerExportPolicy polymerExportPolicy) {
    this.polymerExportPolicy = polymerExportPolicy;
  }

  public void setChromePass(boolean chromePass) {
    this.chromePass = chromePass;
  }

  public boolean isChromePassEnabled() {
    return chromePass;
  }

  public void setJ2clPass(J2clPassMode j2clPassMode) {
    this.j2clPassMode = j2clPassMode;
  }

  public void setJ2clMinifierEnabled(boolean enabled) {
    this.j2clMinifierEnabled = enabled;
  }

  public void setJ2clMinifierPruningManifest(String j2clMinifierPruningManifest) {
    this.j2clMinifierPruningManifest = j2clMinifierPruningManifest;
  }

  public void setCodingConvention(CodingConvention codingConvention) {
    this.codingConvention = codingConvention;
  }

  public CodingConvention getCodingConvention() {
    return codingConvention;
  }

  /** Sets the dependency management options. */
  public void setDependencyOptions(DependencyOptions dependencyOptions) {
    this.dependencyOptions = dependencyOptions;
  }

  public DependencyOptions getDependencyOptions() {
    return dependencyOptions;
  }

  /**
   * Controls how detailed the compilation summary is. Values: 0 (never print summary), 1 (print
   * summary only if there are errors or warnings), 2 (print summary if type checking is on), 3
   * (always print summary). The default level is 1
   */
  public void setSummaryDetailLevel(int summaryDetailLevel) {
    this.summaryDetailLevel = summaryDetailLevel;
  }

  public void setExtraAnnotationNames(Iterable<String> extraAnnotationNames) {
    this.extraAnnotationNames = ImmutableSet.copyOf(extraAnnotationNames);
  }

  /** Sets the output charset. */
  public void setOutputCharset(Charset charset) {
    this.outputCharset = charset;
  }

  /** Gets the output charset. */
  Charset getOutputCharset() {
    return outputCharset;
  }

  /** Sets how goog.tweak calls are processed. */
  public void setTweakProcessing(TweakProcessing tweakProcessing) {
    this.tweakProcessing = tweakProcessing;
  }

  public TweakProcessing getTweakProcessing() {
    return tweakProcessing;
  }

  /** Sets ECMAScript version to use. */
  public void setLanguage(LanguageMode language) {
    checkState(language != LanguageMode.NO_TRANSPILE);
    this.setLanguageIn(language);
    this.setLanguageOut(language);
  }

  /**
   * Sets ECMAScript version to use for the input. If you are not transpiling from one version to
   * another, use #setLanguage instead.
   */
  public void setLanguageIn(LanguageMode languageIn) {
    checkState(languageIn != LanguageMode.NO_TRANSPILE);
    this.languageIn = languageIn == LanguageMode.STABLE ? LanguageMode.STABLE_IN : languageIn;
  }

  public LanguageMode getLanguageIn() {
    return languageIn;
  }

  /**
   * Sets ECMAScript version to use for the output.
   *
   * <p>If you are not transpiling from one version to another, use #setLanguage instead.
   *
   * <p>If you you need something more fine grained (e.g. "ES2017 without modules") use
   * #setOutputFeatureSet.
   */
  public void setLanguageOut(LanguageMode languageOut) {
    if (languageOut == LanguageMode.NO_TRANSPILE) {
      languageOutIsDefaultStrict = Optional.absent();
      outputFeatureSet = Optional.absent();
    } else {
      languageOut = languageOut == LanguageMode.STABLE ? LanguageMode.STABLE_OUT : languageOut;
      languageOutIsDefaultStrict = Optional.of(languageOut.isDefaultStrict());
      setOutputFeatureSet(languageOut.toFeatureSet());
    }
  }

  /**
   * Sets the features that allowed to appear in the output. Any feature in the input that is not in
   * this output must be transpiled away.
   *
   * <p>Note: this is a package private API since not every FeatureSet value can be properly output
   * by the compiler without crashing. Both the `setBrowserFeaturesetYear` and `setLanguageOut` APIs
   * are supported alternatives.
   */
  void setOutputFeatureSet(FeatureSet featureSet) {
    this.outputFeatureSet = Optional.of(featureSet);
  }

  @RestrictedApi(
      explanation = "This API can request unsupported transpilation modes that crash the compiler",
      link = "go/setOutputFeatureSetDeprecation",
      allowlistAnnotations = {LegacySetFeatureSetCaller.class})
  public void legacySetOutputFeatureSet(FeatureSet featureSet) {
    setOutputFeatureSet(featureSet);
  }

  /** Gets the set of features that can appear in the output. */
  public FeatureSet getOutputFeatureSet() {
    if (outputFeatureSet.isPresent()) {
      return outputFeatureSet.get();
    }

    // Backwards compatibility for those that predate language out.
    return languageIn.toFeatureSet();
  }

  /** Options to force transpile specific features for performance experiments. */
  public enum ExperimentalForceTranspile {
    /**
     * Causes let/const to always be removed from the output featureset if present previously.
     *
     * <pre>{@code
     * For targets that set `options.setForceLetConstTranspilation(true)`:
     * - if they already set <= ES5 output, no change
     * - if they set >= ES6 output, then { force transpile let/const + classes + rewrite ESModules +
     * isolatePolyfills + rewritePolyfills}
     *
     * }</pre>
     */
    LET_CONST,

    /**
     * Causes classes to always be removed from the output featureset if present previously.
     *
     * <pre>{@code
     * For targets that set `options.setForceClassTranspilation(true)`:
     * - if they already set <= ES5 output, no change
     * - if they set >= ES6 output, then { force transpile classes + rewrite ESModules +
     * isolatePolyfills + rewritePolyfills}
     *
     * }</pre>
     */
    CLASS,

    /** Transpile all features down to ES5 except ASYNC AWAIT */
    ALL_EXCEPT_ASYNC_AWAIT
  };

  public void setExperimentalForceTranspiles(
      ExperimentalForceTranspile... experimentalForceTranspile) {
    if (experimentalForceTranspile.length > 1) {
      checkState(
          !experimentalForceTranspiles.contains(ExperimentalForceTranspile.ALL_EXCEPT_ASYNC_AWAIT));
    }
    experimentalForceTranspiles = ImmutableList.copyOf(experimentalForceTranspile);
  }

  public ImmutableList<ExperimentalForceTranspile> getExperimentalForceTranspiles() {
    return experimentalForceTranspiles;
  }

  public boolean needsTranspilationFrom(FeatureSet languageLevel) {
    // TODO(johnplaisted): This isn't really accurate. This should instead be the *parsed* language,
    // not the *input* language.
    return getLanguageIn().toFeatureSet().contains(languageLevel)
        && !getOutputFeatureSet().contains(languageLevel);
  }

  public boolean needsTranspilationOf(FeatureSet.Feature feature) {
    return getLanguageIn().toFeatureSet().has(feature) && !getOutputFeatureSet().has(feature);
  }

  /** Set which set of builtin externs to use. */
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  public Environment getEnvironment() {
    return environment;
  }

  public void setAliasTransformationHandler(AliasTransformationHandler changes) {
    this.aliasHandler = changes;
  }

  public AliasTransformationHandler getAliasTransformationHandler() {
    return this.aliasHandler;
  }

  /**
   * Set a custom handler for warnings and errors.
   *
   * <p>This is mostly used for piping the warnings and errors to a file behind the scenes.
   *
   * <p>If you want to filter warnings and errors, you should use a WarningsGuard.
   *
   * <p>If you want to change how warnings and errors are reported to the user, you should set a
   * ErrorManager on the Compiler. An ErrorManager is intended to summarize the errors for a single
   * compile job.
   */
  public void setErrorHandler(ErrorHandler handler) {
    this.errorHandler = handler;
  }

  /** If true, enables type inference. If checkTypes is enabled, this flag has no effect. */
  public void setInferTypes(boolean enable) {
    inferTypes = enable;
  }

  /**
   * Gets the inferTypes flag. Note that if checkTypes is enabled, this flag is ignored when
   * configuring the compiler.
   */
  public boolean getInferTypes() {
    return inferTypes;
  }

  /**
   * @deprecated This is a no-op.
   */
  @Deprecated
  public void setNewTypeInference(boolean enable) {}

  /**
   * Bypass check preventing output at a language mode that includes async functions when zone.js is
   * detected as present. Since this check catches difficult-to-debug issues at build time (see
   * https://github.com/angular/angular/issues/31730), setting this option is not recommended.
   */
  private boolean allowZoneJsWithAsyncFunctionsInOutput;

  public void setAllowZoneJsWithAsyncFunctionsInOutput(boolean enable) {
    this.allowZoneJsWithAsyncFunctionsInOutput = enable;
  }

  boolean allowsZoneJsWithAsyncFunctionsInOutput() {
    return this.checksOnly || this.allowZoneJsWithAsyncFunctionsInOutput;
  }

  /**
   * @return true if either typechecker is ON.
   */
  public boolean isTypecheckingEnabled() {
    return this.checkTypes;
  }

  /**
   * @return Whether assumeStrictThis is set.
   */
  public boolean assumeStrictThis() {
    return assumeStrictThis;
  }

  /** If true, enables enables additional optimizations. */
  public void setAssumeStrictThis(boolean enable) {
    this.assumeStrictThis = enable;
  }

  /**
   * @return Whether assumeClosuresOnlyCaptureReferences is set.
   */
  public boolean assumeClosuresOnlyCaptureReferences() {
    return assumeClosuresOnlyCaptureReferences;
  }

  /**
   * Whether to assume closures capture only what they reference. This allows more aggressive
   * function inlining.
   */
  public void setAssumeClosuresOnlyCaptureReferences(boolean enable) {
    this.assumeClosuresOnlyCaptureReferences = enable;
  }

  /** Sets the list of properties that we report property invalidation errors for. */
  public void setPropertiesThatMustDisambiguate(Set<String> names) {
    this.propertiesThatMustDisambiguate = ImmutableSet.copyOf(names);
  }

  public ImmutableSet<String> getPropertiesThatMustDisambiguate() {
    return this.propertiesThatMustDisambiguate;
  }

  public void setPreserveDetailedSourceInfo(boolean preserveDetailedSourceInfo) {
    this.preserveDetailedSourceInfo = preserveDetailedSourceInfo;
  }

  boolean preservesDetailedSourceInfo() {
    return preserveDetailedSourceInfo;
  }

  public void setPreserveNonJSDocComments(boolean preserveNonJSDocComments) {
    this.preserveNonJSDocComments = preserveNonJSDocComments;
  }

  boolean getPreserveNonJSDocComments() {
    return preserveNonJSDocComments;
  }

  public void setContinueAfterErrors(boolean continueAfterErrors) {
    this.continueAfterErrors = continueAfterErrors;
  }

  boolean canContinueAfterErrors() {
    return continueAfterErrors;
  }

  /**
   * Enables or disables the parsing of JSDoc documentation, and optionally also the preservation of
   * all whitespace and formatting within a JSDoc comment. By default, whitespace is collapsed for
   * all comments except {@literal @license} and {@literal @preserve} blocks,
   */
  public void setParseJsDocDocumentation(Config.JsDocParsing parseJsDocDocumentation) {
    this.parseJsDocDocumentation = parseJsDocDocumentation;
  }

  /**
   * Checks JSDoc documentation will be parsed.
   *
   * @return True when JSDoc documentation will be parsed, false if not.
   */
  public Config.JsDocParsing isParseJsDocDocumentation() {
    return this.parseJsDocDocumentation;
  }

  /**
   * Skip all passes (other than transpilation, if requested). Don't inject any runtime libraries
   * (unless explicitly requested) or do any checks/optimizations (this is useful for per-file
   * transpilation).
   */
  public void setSkipNonTranspilationPasses(boolean skipNonTranspilationPasses) {
    this.skipNonTranspilationPasses = skipNonTranspilationPasses;
  }

  public void setDevMode(DevMode devMode) {
    this.devMode = devMode;
  }

  public void setCheckDeterminism(boolean checkDeterminism) {
    this.checkDeterminism = checkDeterminism;
  }

  public boolean getCheckDeterminism() {
    return checkDeterminism;
  }

  public void setMessageBundle(MessageBundle messageBundle) {
    this.messageBundle = messageBundle;
  }

  public void setCheckSymbols(boolean checkSymbols) {
    this.checkSymbols = checkSymbols;
  }

  public void setCheckSuspiciousCode(boolean checkSuspiciousCode) {
    this.checkSuspiciousCode = checkSuspiciousCode;
  }

  public void setCheckTypes(boolean checkTypes) {
    this.checkTypes = checkTypes;
  }

  public void setFoldConstants(boolean foldConstants) {
    this.foldConstants = foldConstants;
  }

  public void setDeadAssignmentElimination(boolean deadAssignmentElimination) {
    this.deadAssignmentElimination = deadAssignmentElimination;
  }

  public void setInlineConstantVars(boolean inlineConstantVars) {
    this.inlineConstantVars = inlineConstantVars;
  }

  public void setCrossChunkCodeMotion(boolean crossChunkCodeMotion) {
    this.crossChunkCodeMotion = crossChunkCodeMotion;
  }

  public void setCrossChunkCodeMotionNoStubMethods(boolean crossChunkCodeMotionNoStubMethods) {
    this.crossChunkCodeMotionNoStubMethods = crossChunkCodeMotionNoStubMethods;
  }

  public void setParentChunkCanSeeSymbolsDeclaredInChildren(
      boolean parentChunkCanSeeSymbolsDeclaredInChildren) {
    this.parentChunkCanSeeSymbolsDeclaredInChildren = parentChunkCanSeeSymbolsDeclaredInChildren;
  }

  public void setCrossChunkMethodMotion(boolean crossChunkMethodMotion) {
    this.crossChunkMethodMotion = crossChunkMethodMotion;
  }

  public void setCoalesceVariableNames(boolean coalesceVariableNames) {
    this.coalesceVariableNames = coalesceVariableNames;
  }

  public void setInlineLocalVariables(boolean inlineLocalVariables) {
    this.inlineLocalVariables = inlineLocalVariables;
  }

  public void setFlowSensitiveInlineVariables(boolean enabled) {
    this.flowSensitiveInlineVariables = enabled;
  }

  public void setSmartNameRemoval(boolean smartNameRemoval) {
    // TODO(bradfordcsmith): Remove the smart name removal option.
    this.smartNameRemoval = smartNameRemoval;
    if (smartNameRemoval) {
      // To get the effect this option used to have we need to enable these options.
      // Don't disable them here if they were set explicitly, though.
      this.removeUnusedVars = true;
      this.removeUnusedPrototypeProperties = true;
    }
  }

  public void setRemoveDeadCode(boolean removeDeadCode) {
    this.removeDeadCode = removeDeadCode;
  }

  public void setExtractPrototypeMemberDeclarations(boolean enabled) {
    this.extractPrototypeMemberDeclarations =
        enabled
            ? ExtractPrototypeMemberDeclarationsMode.USE_GLOBAL_TEMP
            : ExtractPrototypeMemberDeclarationsMode.OFF;
  }

  // USE_IIFE is currently unused. Consider removing support for it and
  // deleting this setter.
  public void setExtractPrototypeMemberDeclarations(ExtractPrototypeMemberDeclarationsMode mode) {
    this.extractPrototypeMemberDeclarations = mode;
  }

  public void setRemoveUnusedPrototypeProperties(boolean enabled) {
    this.removeUnusedPrototypeProperties = enabled;
    // InlineSimpleMethods makes similar assumptions to
    // RemoveUnusedCode, so they are enabled together.
    this.inlineGetters = enabled;
  }

  public void setCollapseVariableDeclarations(boolean enabled) {
    this.collapseVariableDeclarations = enabled;
  }

  public void setCollapseAnonymousFunctions(boolean enabled) {
    this.collapseAnonymousFunctions = enabled;
  }

  public void setAliasStringsMode(AliasStringsMode aliasStringsMode) {
    this.aliasStringsMode = aliasStringsMode;
  }

  public AliasStringsMode getAliasStringsMode() {
    return this.aliasStringsMode;
  }

  public void setOutputJsStringUsage(boolean outputJsStringUsage) {
    this.outputJsStringUsage = outputJsStringUsage;
  }

  public void setConvertToDottedProperties(boolean convertToDottedProperties) {
    this.convertToDottedProperties = convertToDottedProperties;
  }

  public void setUseTypesForLocalOptimization(boolean useTypesForLocalOptimization) {
    this.useTypesForLocalOptimization = useTypesForLocalOptimization;
  }

  public boolean shouldUseTypesForLocalOptimization() {
    return this.useTypesForLocalOptimization;
  }

  @Deprecated
  public void setUseTypesForOptimization(boolean useTypesForOptimization) {
    if (useTypesForOptimization) {
      this.disambiguateProperties = true;
      this.ambiguateProperties = true;
      this.inlineProperties = true;
      this.useTypesForLocalOptimization = true;
    }
  }

  /** Whether any type-based optimizations are enabled */
  boolean requiresTypesForOptimization() {
    // Add new type-based optimization options to this list, or types may be erased from the AST
    // before the optimziations run.
    return this.disambiguateProperties
        || this.ambiguateProperties
        || this.inlineProperties
        || this.useTypesForLocalOptimization;
  }

  public void setRewriteFunctionExpressions(boolean rewriteFunctionExpressions) {
    this.rewriteFunctionExpressions = rewriteFunctionExpressions;
  }

  public void setOptimizeCalls(boolean optimizeCalls) {
    this.optimizeCalls = optimizeCalls;
  }

  public boolean getOptimizeESClassConstructors() {
    return this.optimizeESClassConstructors;
  }

  public void setOptimizeESClassConstructors(boolean optimizeESClassConstructors) {
    this.optimizeESClassConstructors = optimizeESClassConstructors;
  }

  public void setOptimizeArgumentsArray(boolean optimizeArgumentsArray) {
    this.optimizeArgumentsArray = optimizeArgumentsArray;
  }

  public void setVariableRenaming(VariableRenamingPolicy variableRenaming) {
    this.variableRenaming = variableRenaming;
  }

  public void setPropertyRenaming(PropertyRenamingPolicy propertyRenaming) {
    this.propertyRenaming = propertyRenaming;
  }

  public PropertyRenamingPolicy getPropertyRenaming() {
    return this.propertyRenaming;
  }

  public void setLabelRenaming(boolean labelRenaming) {
    this.labelRenaming = labelRenaming;
  }

  public void setReserveRawExports(boolean reserveRawExports) {
    this.reserveRawExports = reserveRawExports;
  }

  public void setPreferStableNames(boolean preferStableNames) {
    this.preferStableNames = preferStableNames;
  }

  public void setGeneratePseudoNames(boolean generatePseudoNames) {
    this.generatePseudoNames = generatePseudoNames;
  }

  public void setPropertyRenamingOnlyCompilationMode(boolean propertyRenamingOnlyCompilationMode) {
    this.propertyRenamingOnlyCompilationMode = propertyRenamingOnlyCompilationMode;
  }

  public boolean isPropertyRenamingOnlyCompilationMode() {
    return this.propertyRenamingOnlyCompilationMode;
  }

  public void setRenamePrefix(String renamePrefix) {
    this.renamePrefix = renamePrefix;
  }

  public String getRenamePrefixNamespace() {
    return this.renamePrefixNamespace;
  }

  public void setRenamePrefixNamespace(String renamePrefixNamespace) {
    this.renamePrefixNamespace = renamePrefixNamespace;
  }

  public void setCollapsePropertiesLevel(PropertyCollapseLevel level) {
    this.collapsePropertiesLevel = level;
  }

  @Deprecated
  public void setCollapseProperties(boolean fullyCollapse) {
    this.collapsePropertiesLevel =
        fullyCollapse ? PropertyCollapseLevel.ALL : PropertyCollapseLevel.NONE;
  }

  public void setDevirtualizeMethods(boolean devirtualizeMethods) {
    this.devirtualizeMethods = devirtualizeMethods;
  }

  public void setComputeFunctionSideEffects(boolean computeFunctionSideEffects) {
    this.computeFunctionSideEffects = computeFunctionSideEffects;
  }

  public void setDisambiguateProperties(boolean disambiguateProperties) {
    this.disambiguateProperties = disambiguateProperties;
  }

  public boolean shouldDisambiguateProperties() {
    return this.disambiguateProperties;
  }

  public void setAmbiguateProperties(boolean ambiguateProperties) {
    this.ambiguateProperties = ambiguateProperties;
  }

  public boolean shouldAmbiguateProperties() {
    return this.ambiguateProperties;
  }

  public void setInputVariableMap(VariableMap inputVariableMap) {
    this.inputVariableMap = inputVariableMap;
  }

  public void setInputPropertyMap(VariableMap inputPropertyMap) {
    this.inputPropertyMap = inputPropertyMap;
  }

  public void setExportTestFunctions(boolean exportTestFunctions) {
    this.exportTestFunctions = exportTestFunctions;
  }

  public void setSyntheticBlockStartMarker(String syntheticBlockStartMarker) {
    this.syntheticBlockStartMarker = syntheticBlockStartMarker;
  }

  public void setSyntheticBlockEndMarker(String syntheticBlockEndMarker) {
    this.syntheticBlockEndMarker = syntheticBlockEndMarker;
  }

  public void setLocale(String locale) {
    this.locale = locale;
  }

  public void setDoLateLocalization(boolean doLateLocalization) {
    this.doLateLocalization = doLateLocalization;
  }

  public boolean doLateLocalization() {
    return doLateLocalization;
  }

  /** Should we run any form of the `ReplaceMessages` pass? */
  public boolean shouldRunReplaceMessagesPass() {
    return !shouldRunReplaceMessagesForChrome() && messageBundle != null;
  }

  public void setMarkAsCompiled(boolean markAsCompiled) {
    this.markAsCompiled = markAsCompiled;
  }

  public void setClosurePass(boolean closurePass) {
    this.closurePass = closurePass;
  }

  /**
   * Preserve closure primitives.
   *
   * <p>For now, this only preserves goog.provide(), goog.require() and goog.module() calls.
   */
  public void setPreserveClosurePrimitives(boolean preserveClosurePrimitives) {
    this.preserveClosurePrimitives = preserveClosurePrimitives;
  }

  public boolean shouldPreservesGoogProvidesAndRequires() {
    return this.preserveClosurePrimitives;
  }

  public boolean shouldPreserveGoogModule() {
    return this.preserveClosurePrimitives;
  }

  /** Do not process goog. intrinsics, such as goog.getCssName(). */
  public boolean shouldPreserveGoogLibraryPrimitives() {
    return this.preserveClosurePrimitives;
  }

  public void setPreserveTypeAnnotations(boolean preserveTypeAnnotations) {
    this.preserveTypeAnnotations = preserveTypeAnnotations;
  }

  public void setGentsMode(boolean gentsMode) {
    this.gentsMode = gentsMode;
  }

  public void setGatherCssNames(boolean gatherCssNames) {
    this.gatherCssNames = gatherCssNames;
  }

  /**
   * @deprecated StripCode is deprecated. Code should be designed to be removed by other means.
   */
  @Deprecated
  public void setStripTypes(Set<String> stripTypes) {
    this.stripTypes = ImmutableSet.copyOf(stripTypes);
  }

  /**
   * @deprecated StripCode is deprecated. Code should be designed to be removed by other means.
   */
  @Deprecated
  public ImmutableSet<String> getStripTypes() {
    return this.stripTypes;
  }

  /**
   * @deprecated StripCode is deprecated. Code should be designed to be removed by other means.
   */
  @Deprecated
  public void setStripNameSuffixes(Set<String> stripNameSuffixes) {
    this.stripNameSuffixes = ImmutableSet.copyOf(stripNameSuffixes);
  }

  /**
   * @deprecated StripCode is deprecated. Code should be designed to be removed by other means.
   */
  @Deprecated
  public void setStripNamePrefixes(Set<String> stripNamePrefixes) {
    this.stripNamePrefixes = ImmutableSet.copyOf(stripNamePrefixes);
  }

  public void addCustomPass(CustomPassExecutionTime time, CompilerPass customPass) {
    if (customPasses == null) {
      customPasses = LinkedHashMultimap.create();
    }
    customPasses.put(time, customPass);
  }

  public void setDefineReplacements(Map<String, Object> defineReplacements) {
    this.defineReplacements.clear();
    this.defineReplacements.putAll(defineReplacements);
  }

  public void setRewriteGlobalDeclarationsForTryCatchWrapping(boolean rewrite) {
    this.rewriteGlobalDeclarationsForTryCatchWrapping = rewrite;
  }

  public void setCssRenamingMap(CssRenamingMap cssRenamingMap) {
    this.cssRenamingMap = cssRenamingMap;
  }

  public void setCssRenamingSkiplist(Set<String> skiplist) {
    this.cssRenamingSkiplist = skiplist;
  }

  public void setReplaceStringsFunctionDescriptions(
      List<String> replaceStringsFunctionDescriptions) {
    this.replaceStringsFunctionDescriptions = replaceStringsFunctionDescriptions;
  }

  public void setParenthesizeFunctionsInChunks(List<String> parenthesizeFunctionsInChunks) {
    this.parenthesizeFunctionsInChunks = parenthesizeFunctionsInChunks;
  }

  public void setReplaceStringsPlaceholderToken(String replaceStringsPlaceholderToken) {
    this.replaceStringsPlaceholderToken = replaceStringsPlaceholderToken;
  }

  public void setPrettyPrint(boolean prettyPrint) {
    this.prettyPrint = prettyPrint;
  }

  public boolean isPrettyPrint() {
    return this.prettyPrint;
  }

  public void setLineBreak(boolean lineBreak) {
    this.lineBreak = lineBreak;
  }

  public void setPrintInputDelimiter(boolean printInputDelimiter) {
    this.printInputDelimiter = printInputDelimiter;
  }

  public void setInputDelimiter(String inputDelimiter) {
    this.inputDelimiter = inputDelimiter;
  }

  public void setDebugLogDirectory(@Nullable Path dir) {
    this.debugLogDirectory = dir;
  }

  public @Nullable Path getDebugLogDirectory() {
    return debugLogDirectory;
  }

  public void setDebugLogFilter(String filter) {
    this.debugLogFilter = filter;
  }

  public String getDebugLogFilter() {
    return debugLogFilter;
  }

  boolean shouldSerializeExtraDebugInfo() {
    // NOTE: this is tied to debug logging (via checking getDebugLogDirectory()) for convenience,
    // and it's possible to split up the two options if this begins to trigger memory issues.
    return this.serializeExtraDebugInfo || getDebugLogDirectory() != null;
  }

  void setSerializeExtraDebugInfo(boolean serializeExtraDebugInfo) {
    this.serializeExtraDebugInfo = serializeExtraDebugInfo;
  }

  public void setQuoteKeywordProperties(boolean quoteKeywordProperties) {
    this.quoteKeywordProperties = quoteKeywordProperties;
  }

  public boolean shouldQuoteKeywordProperties() {
    // Never quote properties in .i.js files
    if (incrementalCheckMode == IncrementalCheckMode.GENERATE_IJS) {
      return false;
    }
    return this.quoteKeywordProperties || FeatureSet.ES3.contains(getOutputFeatureSet());
  }

  public void setErrorFormat(ErrorFormat errorFormat) {
    this.errorFormat = errorFormat;
  }

  public ErrorFormat getErrorFormat() {
    return this.errorFormat;
  }

  public void setWarningsGuard(ComposeWarningsGuard warningsGuard) {
    this.warningsGuard = warningsGuard;
  }

  public void setLineLengthThreshold(int lineLengthThreshold) {
    this.lineLengthThreshold = lineLengthThreshold;
  }

  public int getLineLengthThreshold() {
    return this.lineLengthThreshold;
  }

  public void setUseOriginalNamesInOutput(boolean useOriginalNamesInOutput) {
    this.useOriginalNamesInOutput = useOriginalNamesInOutput;
  }

  public boolean getUseOriginalNamesInOutput() {
    return this.useOriginalNamesInOutput;
  }

  public void setExternExportsPath(@Nullable String externExportsPath) {
    this.externExportsPath = externExportsPath;
  }

  public @Nullable String getExternExportsPath() {
    return this.externExportsPath;
  }

  public void setSourceMapOutputPath(String sourceMapOutputPath) {
    this.sourceMapOutputPath = sourceMapOutputPath;
  }

  public void setApplyInputSourceMaps(boolean applyInputSourceMaps) {
    this.applyInputSourceMaps = applyInputSourceMaps;
  }

  public void setResolveSourceMapAnnotations(boolean resolveSourceMapAnnotations) {
    this.resolveSourceMapAnnotations = resolveSourceMapAnnotations;
  }

  public void setSourceMapIncludeSourcesContent(boolean sourceMapIncludeSourcesContent) {
    this.sourceMapIncludeSourcesContent = sourceMapIncludeSourcesContent;
  }

  public void setParseInlineSourceMaps(boolean parseInlineSourceMaps) {
    this.parseInlineSourceMaps = parseInlineSourceMaps;
  }

  public void setSourceMapDetailLevel(SourceMap.DetailLevel sourceMapDetailLevel) {
    this.sourceMapDetailLevel = sourceMapDetailLevel;
  }

  public void setSourceMapFormat(SourceMap.Format sourceMapFormat) {
    this.sourceMapFormat = sourceMapFormat;
  }

  public void setSourceMapLocationMappings(
      List<? extends SourceMap.LocationMapping> sourceMapLocationMappings) {
    this.sourceMapLocationMappings = sourceMapLocationMappings;
  }

  /**
   * Rewrites CommonJS modules so that modules can be concatenated together, by renaming all globals
   * to avoid conflicting with other modules.
   */
  public void setProcessCommonJSModules(boolean processCommonJSModules) {
    this.processCommonJSModules = processCommonJSModules;
  }

  public boolean getProcessCommonJSModules() {
    return processCommonJSModules;
  }

  /** How ES modules should be transformed. */
  public enum Es6ModuleTranspilation {
    /** Do not touch any Es6 module feature. */
    NONE,

    /** Rewrite import paths to resolved, relative paths only. */
    RELATIVIZE_IMPORT_PATHS,

    /** Rewrite to common js like modules for bundling. */
    TO_COMMON_JS_LIKE_MODULES,

    /** Compile ES modules. */
    COMPILE
  }

  private Es6ModuleTranspilation es6ModuleTranspilation = Es6ModuleTranspilation.COMPILE;

  public void setEs6ModuleTranspilation(Es6ModuleTranspilation value) {
    es6ModuleTranspilation = value;
  }

  public Es6ModuleTranspilation getEs6ModuleTranspilation() {
    return es6ModuleTranspilation;
  }

  /** Sets a path prefix for CommonJS modules (maps to {@link #setModuleRoots(List)}). */
  public void setCommonJSModulePathPrefix(String commonJSModulePathPrefix) {
    setModuleRoots(ImmutableList.of(commonJSModulePathPrefix));
  }

  /** Sets the module roots. */
  public void setModuleRoots(List<String> moduleRoots) {
    this.moduleRoots = moduleRoots;
  }

  /** Sets whether to rewrite polyfills. */
  public void setRewritePolyfills(boolean rewritePolyfills) {
    this.rewritePolyfills = rewritePolyfills;
  }

  public boolean getRewritePolyfills() {
    return this.rewritePolyfills;
  }

  /** Sets whether to isolate polyfills from the global scope. */
  public void setIsolatePolyfills(boolean isolatePolyfills) {
    this.isolatePolyfills = isolatePolyfills;
    if (this.isolatePolyfills) {
      this.setDefineToBooleanLiteral("$jscomp.ISOLATE_POLYFILLS", isolatePolyfills);
    }
  }

  public boolean getIsolatePolyfills() {
    return this.isolatePolyfills;
  }

  /** Sets list of libraries to always inject, even if not needed. */
  public void setForceLibraryInjection(Iterable<String> libraries) {
    this.forceLibraryInjection = ImmutableList.copyOf(libraries);
  }

  /** Sets the set of libraries to never inject, even if required. */
  public void setPreventLibraryInjection(boolean preventLibraryInjection) {
    this.preventLibraryInjection = preventLibraryInjection;
  }

  public void setUnusedImportsToRemove(@Nullable ImmutableSet<String> unusedImportsToRemove) {
    this.unusedImportsToRemove = unusedImportsToRemove;
  }

  public @Nullable ImmutableSet<String> getUnusedImportsToRemove() {
    return this.unusedImportsToRemove;
  }

  public void setInstrumentForCoverageOption(InstrumentOption instrumentForCoverageOption) {
    this.instrumentForCoverageOption = checkNotNull(instrumentForCoverageOption);
  }

  public InstrumentOption getInstrumentForCoverageOption() {
    return this.instrumentForCoverageOption;
  }

  /**
   * Sets the name for the global array which is used by PRODUCTION instrumentation. The array is
   * declared during the instrumentation pass with the name provided through this setter.
   */
  public void setProductionInstrumentationArrayName(String productionInstrumentationArrayName) {
    this.productionInstrumentationArrayName = checkNotNull(productionInstrumentationArrayName);
  }

  public String getProductionInstrumentationArrayName() {
    return this.productionInstrumentationArrayName;
  }

  public final ImmutableList<ConformanceConfig> getConformanceConfigs() {
    return conformanceConfigs;
  }

  /** Both enable and configure conformance checks, if non-null. */
  @GwtIncompatible("Conformance")
  public void setConformanceConfig(ConformanceConfig conformanceConfig) {
    setConformanceConfigs(ImmutableList.of(conformanceConfig));
  }

  /** Both enable and configure conformance checks, if non-null. */
  @GwtIncompatible("Conformance")
  public void setConformanceConfigs(List<ConformanceConfig> configs) {
    this.conformanceConfigs =
        ImmutableList.<ConformanceConfig>builder()
            .add(ResourceLoader.loadGlobalConformance(CompilerOptions.class))
            .addAll(configs)
            .build();
  }

  public void clearConformanceConfigs() {
    this.conformanceConfigs = ImmutableList.of();
  }

  /** Whether the output should contain a 'use strict' directive. */
  public boolean shouldEmitUseStrict() {
    // Fall back to the language in's strictness if there is no output language explicitly set
    // for backwards compatibility.
    return this.emitUseStrict.or(languageOutIsDefaultStrict).or(languageIn.isDefaultStrict());
  }

  @CanIgnoreReturnValue
  public CompilerOptions setEmitUseStrict(boolean emitUseStrict) {
    this.emitUseStrict = Optional.of(emitUseStrict);
    return this;
  }

  public ResolutionMode getModuleResolutionMode() {
    return this.moduleResolutionMode;
  }

  public void setModuleResolutionMode(ResolutionMode moduleResolutionMode) {
    this.moduleResolutionMode = moduleResolutionMode;
  }

  public ImmutableMap<String, String> getBrowserResolverPrefixReplacements() {
    return this.browserResolverPrefixReplacements;
  }

  public void setBrowserResolverPrefixReplacements(
      ImmutableMap<String, String> browserResolverPrefixReplacements) {
    this.browserResolverPrefixReplacements = browserResolverPrefixReplacements;
  }

  public void setPathEscaper(ModuleLoader.PathEscaper pathEscaper) {
    this.pathEscaper = pathEscaper;
  }

  public ModuleLoader.PathEscaper getPathEscaper() {
    return pathEscaper;
  }

  public List<String> getPackageJsonEntryNames() {
    return this.packageJsonEntryNames;
  }

  public void setPackageJsonEntryNames(List<String> names) {
    this.packageJsonEntryNames = names;
  }

  public void setUseSizeHeuristicToStopOptimizationLoop(boolean mayStopEarly) {
    this.useSizeHeuristicToStopOptimizationLoop = mayStopEarly;
  }

  public void setMaxOptimizationLoopIterations(int maxIterations) {
    this.optimizationLoopMaxIterations = maxIterations;
  }

  public ChunkOutputType getChunkOutputType() {
    return chunkOutputType;
  }

  public void setChunkOutputType(ChunkOutputType chunkOutputType) {
    this.chunkOutputType = chunkOutputType;
  }

  /** Serializes compiler options to a stream. */
  @GwtIncompatible("ObjectOutputStream")
  public void serialize(OutputStream objectOutputStream) throws IOException {
    new ObjectOutputStream(objectOutputStream).writeObject(this);
  }

  /** Deserializes compiler options from a stream. */
  @GwtIncompatible("ObjectInputStream")
  public static CompilerOptions deserialize(InputStream objectInputStream)
      throws IOException, ClassNotFoundException {
    return (CompilerOptions) new ObjectInputStream(objectInputStream).readObject();
  }

  public void setStrictMessageReplacement(boolean strictMessageReplacement) {
    this.strictMessageReplacement = strictMessageReplacement;
  }

  public boolean getStrictMessageReplacement() {
    return this.strictMessageReplacement;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .omitNullValues()
        .add("aliasStringsMode", getAliasStringsMode())
        .add("aliasHandler", getAliasTransformationHandler())
        .add("ambiguateProperties", ambiguateProperties)
        .add("angularPass", angularPass)
        .add("assumeClosuresOnlyCaptureReferences", assumeClosuresOnlyCaptureReferences)
        .add("assumeGettersArePure", assumeGettersArePure)
        .add("assumeStrictThis", assumeStrictThis())
        .add("browserResolverPrefixReplacements", browserResolverPrefixReplacements)
        .add("checkDeterminism", getCheckDeterminism())
        .add("checksOnly", checksOnly)
        .add("checkSuspiciousCode", checkSuspiciousCode)
        .add("checkSymbols", checkSymbols)
        .add("checkTypes", checkTypes)
        .add("closurePass", closurePass)
        .add("coalesceVariableNames", coalesceVariableNames)
        .add("codingConvention", getCodingConvention())
        .add("collapseAnonymousFunctions", collapseAnonymousFunctions)
        .add("collapseObjectLiterals", collapseObjectLiterals)
        .add("collapseProperties", collapsePropertiesLevel)
        .add("collapseVariableDeclarations", collapseVariableDeclarations)
        .add("colorizeErrorOutput", shouldColorizeErrorOutput())
        .add("computeFunctionSideEffects", computeFunctionSideEffects)
        .add("conformanceConfigs", getConformanceConfigs())
        .add("conformanceRemoveRegexFromPath", conformanceRemoveRegexFromPath)
        .add("continueAfterErrors", canContinueAfterErrors())
        .add("convertToDottedProperties", convertToDottedProperties)
        .add("crossChunkCodeMotion", crossChunkCodeMotion)
        .add("crossChunkCodeMotionNoStubMethods", crossChunkCodeMotionNoStubMethods)
        .add("crossChunkMethodMotion", crossChunkMethodMotion)
        .add("cssRenamingMap", cssRenamingMap)
        .add("cssRenamingSkiplist", cssRenamingSkiplist)
        .add("customPasses", customPasses)
        .add("deadAssignmentElimination", deadAssignmentElimination)
        .add("debugLogDirectory", debugLogDirectory)
        .add("defineReplacements", getDefineReplacements())
        .add("dependencyOptions", getDependencyOptions())
        .add("devirtualizeMethods", devirtualizeMethods)
        .add("devMode", devMode)
        .add("disambiguateProperties", disambiguateProperties)
        .add("enableModuleRewriting", enableModuleRewriting)
        .add("environment", getEnvironment())
        .add("errorFormat", errorFormat)
        .add("errorHandler", errorHandler)
        .add("es6ModuleTranspilation", es6ModuleTranspilation)
        .add("exportLocalPropertyDefinitions", exportLocalPropertyDefinitions)
        .add("exportTestFunctions", exportTestFunctions)
        .add("externExportsPath", externExportsPath)
        .add("extraAnnotationNames", extraAnnotationNames)
        .add("extractPrototypeMemberDeclarations", extractPrototypeMemberDeclarations)
        .add("filesToPrintAfterEachPassRegexList", filesToPrintAfterEachPassRegexList)
        .add("flowSensitiveInlineVariables", flowSensitiveInlineVariables)
        .add("foldConstants", foldConstants)
        .add("forceLibraryInjection", forceLibraryInjection)
        .add("gatherCssNames", gatherCssNames)
        .add("generateExports", generateExports)
        .add("generatePseudoNames", generatePseudoNames)
        .add("generateTypedExterns", shouldGenerateTypedExterns())
        .add("idGenerators", idGenerators)
        .add("idGeneratorsMapSerialized", idGeneratorsMapSerialized)
        .add("incrementalCheckMode", incrementalCheckMode)
        .add("inferConsts", inferConsts)
        .add("inferTypes", inferTypes)
        .add("inlineConstantVars", inlineConstantVars)
        .add("inlineFunctionsLevel", inlineFunctionsLevel)
        .add("inlineGetters", inlineGetters)
        .add("inlineLocalVariables", inlineLocalVariables)
        .add("inlineProperties", inlineProperties)
        .add("inlineVariables", inlineVariables)
        .add("inputDelimiter", inputDelimiter)
        .add("inputPropertyMap", inputPropertyMap)
        .add("inputSourceMaps", inputSourceMaps)
        .add("inputVariableMap", inputVariableMap)
        .add("instrumentForCoverageOnly", instrumentForCoverageOnly)
        .add("instrumentForCoverageOption", instrumentForCoverageOption.toString())
        .add("productionInstrumentationArrayName", productionInstrumentationArrayName)
        .add("isolatePolyfills", isolatePolyfills)
        .add("j2clMinifierEnabled", j2clMinifierEnabled)
        .add("j2clMinifierPruningManifest", j2clMinifierPruningManifest)
        .add("j2clPassMode", j2clPassMode)
        .add("labelRenaming", labelRenaming)
        .add("languageIn", getLanguageIn())
        .add("languageOutIsDefaultStrict", languageOutIsDefaultStrict)
        .add("lineBreak", lineBreak)
        .add("lineLengthThreshold", lineLengthThreshold)
        .add("locale", locale)
        .add("markAsCompiled", markAsCompiled)
        .add("maxFunctionSizeAfterInlining", maxFunctionSizeAfterInlining)
        .add("messageBundle", messageBundle)
        .add("moduleRoots", moduleRoots)
        .add("chunksToPrintAfterEachPassRegexList", chunksToPrintAfterEachPassRegexList)
        .add("qnameUsesToPrintAfterEachPassRegexList", qnameUsesToPrintAfterEachPassList)
        .add(
            "rewriteGlobalDeclarationsForTryCatchWrapping",
            rewriteGlobalDeclarationsForTryCatchWrapping)
        .add("nameGenerator", nameGenerator)
        .add("optimizeArgumentsArray", optimizeArgumentsArray)
        .add("optimizeCalls", optimizeCalls)
        .add("optimizeESClassConstructors", optimizeESClassConstructors)
        .add("outputCharset", outputCharset)
        .add("outputFeatureSet", outputFeatureSet)
        .add("outputJs", outputJs)
        .add("outputJsStringUsage", outputJsStringUsage)
        .add(
            "parentChunkCanSeeSymbolsDeclaredInChildren",
            parentChunkCanSeeSymbolsDeclaredInChildren)
        .add("parseJsDocDocumentation", isParseJsDocDocumentation())
        .add("pathEscaper", pathEscaper)
        .add("polymerVersion", polymerVersion)
        .add("polymerExportPolicy", polymerExportPolicy)
        .add("preferSingleQuotes", preferSingleQuotes)
        .add("preferStableNames", preferStableNames)
        .add("preserveDetailedSourceInfo", preservesDetailedSourceInfo())
        .add("preserveNonJSDocComments", getPreserveNonJSDocComments())
        .add("preserveGoogProvidesAndRequires", preserveClosurePrimitives)
        .add("preserveTypeAnnotations", preserveTypeAnnotations)
        .add("prettyPrint", prettyPrint)
        .add("preventLibraryInjection", preventLibraryInjection)
        .add("printConfig", printConfig)
        .add("printInputDelimiter", printInputDelimiter)
        .add("printSourceAfterEachPass", printSourceAfterEachPass)
        .add("processCommonJSModules", processCommonJSModules)
        .add("propertiesThatMustDisambiguate", propertiesThatMustDisambiguate)
        .add("propertyRenaming", propertyRenaming)
        .add("propertyRenamingOnlyCompilationMode", propertyRenamingOnlyCompilationMode)
        .add("protectHiddenSideEffects", protectHiddenSideEffects)
        .add("quoteKeywordProperties", quoteKeywordProperties)
        .add("removeAbstractMethods", removeAbstractMethods)
        .add("removeClosureAsserts", removeClosureAsserts)
        .add("removeJ2clAsserts", removeJ2clAsserts)
        .add("removeDeadCode", removeDeadCode)
        .add("removeUnusedClassProperties", removeUnusedClassProperties)
        .add("removeUnusedConstructorProperties", removeUnusedConstructorProperties)
        .add("removeUnusedLocalVars", removeUnusedLocalVars)
        .add("removeUnusedPrototypeProperties", removeUnusedPrototypeProperties)
        .add("removeUnusedVars", removeUnusedVars)
        .add(
            "renamePrefixNamespaceAssumeCrossChunkNames",
            renamePrefixNamespaceAssumeCrossChunkNames)
        .add("renamePrefixNamespace", renamePrefixNamespace)
        .add("renamePrefix", renamePrefix)
        .add("replaceIdGenerators", replaceIdGenerators)
        .add("replaceMessagesWithChromeI18n", replaceMessagesWithChromeI18n)
        .add("replaceStringsFunctionDescriptions", replaceStringsFunctionDescriptions)
        .add("replaceStringsPlaceholderToken", replaceStringsPlaceholderToken)
        .add("reserveRawExports", reserveRawExports)
        .add("rewriteFunctionExpressions", rewriteFunctionExpressions)
        .add("rewritePolyfills", rewritePolyfills)
        .add("rewriteModulesBeforeTypechecking", rewriteModulesBeforeTypechecking)
        .add("skipNonTranspilationPasses", skipNonTranspilationPasses)
        .add("smartNameRemoval", smartNameRemoval)
        .add("sourceMapDetailLevel", sourceMapDetailLevel)
        .add("sourceMapFormat", sourceMapFormat)
        .add("sourceMapLocationMappings", sourceMapLocationMappings)
        .add("sourceMapOutputPath", sourceMapOutputPath)
        .add("strictMessageReplacement", strictMessageReplacement)
        .add("stripNamePrefixes", stripNamePrefixes)
        .add("stripNameSuffixes", stripNameSuffixes)
        .add("stripTypes", stripTypes)
        .add("summaryDetailLevel", summaryDetailLevel)
        .add("syntheticBlockEndMarker", syntheticBlockEndMarker)
        .add("syntheticBlockStartMarker", syntheticBlockStartMarker)
        .add("tcProjectId", tcProjectId)
        .add("tracer", tracer)
        .add("trustedStrings", trustedStrings)
        .add("tweakProcessing", getTweakProcessing())
        .add("emitUseStrict", emitUseStrict)
        .add("useTypesForLocalOptimization", useTypesForLocalOptimization)
        .add("unusedImportsToRemove", unusedImportsToRemove)
        .add("variableRenaming", variableRenaming)
        .add("warningsGuard", getWarningsGuard())
        .add("wrapGoogModulesForWhitespaceOnly", wrapGoogModulesForWhitespaceOnly)
        .toString();
  }

  //////////////////////////////////////////////////////////////////////////////
  // Enums

  /** An option to determine what level of code instrumentation is performed, if any */
  public enum InstrumentOption {
    NONE, // No coverage instrumentation is performed
    LINE_ONLY, // Collect coverage for every executable statement.
    BRANCH_ONLY, // Collect coverage for control-flow branches.
    PRODUCTION; // Collect coverage for functions where code is compiled for production.

    public static @Nullable InstrumentOption fromString(String value) {
      if (value == null) {
        return null;
      }
      switch (value) {
        case "NONE":
          return InstrumentOption.NONE;
        case "LINE":
          return InstrumentOption.LINE_ONLY;
        case "BRANCH":
          return InstrumentOption.BRANCH_ONLY;
        case "PRODUCTION":
          return InstrumentOption.PRODUCTION;
        default:
          return null;
      }
    }
  }

  /** Format for compiler output when multiple chunks are requested. */
  public enum ChunkOutputType {
    // Original format - each chunk is a simple script
    GLOBAL_NAMESPACE,
    // Each chunk is an ES module with dependencies and symbol access set up using import and export
    // statements.
    ES_MODULES;
  }

  /**
   * A language mode applies to the whole compilation job. As a result, the compiler does not
   * support mixed strict and non-strict in the same compilation job. Therefore, the 'use strict'
   * directive is ignored when the language mode is not strict.
   */
  public enum LanguageMode {
    /** 90's JavaScript */
    ECMASCRIPT3,

    /** Traditional JavaScript */
    ECMASCRIPT5,

    /** Nitpicky, traditional JavaScript */
    ECMASCRIPT5_STRICT,

    /** ECMAScript standard approved in 2015. */
    ECMASCRIPT_2015,

    /** ECMAScript standard approved in 2016. Adds the exponent operator (**). */
    ECMASCRIPT_2016,

    /** ECMAScript standard approved in 2017. Adds async/await and other syntax */
    ECMASCRIPT_2017,

    /** ECMAScript standard approved in 2018. Adds "..." in object literals/patterns. */
    ECMASCRIPT_2018,

    /** ECMAScript standard approved in 2019. Adds catch blocks with no error binding. */
    ECMASCRIPT_2019,

    /** ECMAScript standard approved in 2020. */
    ECMASCRIPT_2020,

    /** ECMAScript standard approved in 2021. */
    ECMASCRIPT_2021,

    /** ECMAScript features from the upcoming standard. */
    ECMASCRIPT_NEXT,

    /** Use stable features. */
    STABLE,

    /** For languageOut only. The same language mode as the input. */
    NO_TRANSPILE,

    /**
     * For testing only. Features that can be parsed/checked but cannot necessarily be understood by
     * the rest of the compiler yet.
     */
    UNSTABLE,

    /**
     * For testing only. Features that can be parsed but cannot necessarily be understood by the
     * rest of the compiler yet.
     */
    UNSUPPORTED;

    /** The default input language level of JSCompiler. */
    public static final LanguageMode STABLE_IN = ECMASCRIPT_NEXT;

    /** The default output language level of JSCompiler. */
    public static final LanguageMode STABLE_OUT = ECMASCRIPT5;

    /** Whether this language mode defaults to strict mode */
    boolean isDefaultStrict() {
      switch (this) {
        case ECMASCRIPT3:
        case ECMASCRIPT5:
          return false;
        default:
          return true;
      }
    }

    public static @Nullable LanguageMode fromString(String value) {
      if (value == null) {
        return null;
      }
      // Trim spaces, disregard case, and allow abbreviation of ECMASCRIPT for convenience.
      String canonicalizedName = Ascii.toUpperCase(value.trim()).replaceFirst("^ES", "ECMASCRIPT");

      if (canonicalizedName.equals("ECMASCRIPT6")
          || canonicalizedName.equals("ECMASCRIPT6_STRICT")) {
        return ECMASCRIPT_2015;
      }

      try {
        return LanguageMode.valueOf(canonicalizedName);
      } catch (IllegalArgumentException e) {
        return null; // unknown name.
      }
    }

    public FeatureSet toFeatureSet() {
      switch (this) {
        case ECMASCRIPT3:
          return FeatureSet.ES3;
        case ECMASCRIPT5:
        case ECMASCRIPT5_STRICT:
          return FeatureSet.ES5;
        case ECMASCRIPT_2015:
          return FeatureSet.ES2015_MODULES;
        case ECMASCRIPT_2016:
          return FeatureSet.ES2016_MODULES;
        case ECMASCRIPT_2017:
          return FeatureSet.ES2017_MODULES;
        case ECMASCRIPT_2018:
          return FeatureSet.ES2018_MODULES;
        case ECMASCRIPT_2019:
          return FeatureSet.ES2019_MODULES;
        case ECMASCRIPT_2020:
          return FeatureSet.ES2020_MODULES;
        case ECMASCRIPT_2021:
          return FeatureSet.ES2021_MODULES;
        case ECMASCRIPT_NEXT:
          return FeatureSet.ES_NEXT;
        case NO_TRANSPILE:
        case UNSTABLE:
          return FeatureSet.ES_UNSTABLE;
        case UNSUPPORTED:
          return FeatureSet.ES_UNSUPPORTED;
        case STABLE:
          throw new UnsupportedOperationException(
              "STABLE has different feature sets for language in and out. "
                  + "Use STABLE_IN or STABLE_OUT.");
      }
      throw new IllegalStateException();
    }
  }

  /** When to do the extra validity checks */
  public static enum DevMode {
    /** Don't do any extra checks. */
    OFF,

    /** After the initial parse */
    START,

    /** At the start and at the end of all optimizations. */
    START_AND_END,

    /** After every pass */
    EVERY_PASS
  }

  /** How much tracing we want to do */
  public static enum TracerMode {
    ALL, // Collect all timing and size metrics. Very slow.
    RAW_SIZE, // Collect all timing and size metrics, except gzipped size. Slow.
    AST_SIZE, // For size data, don't serialize the AST, just count the number of nodes.
    TIMING_ONLY, // Collect timing metrics only.
    OFF; // Collect no timing and size metrics.

    public boolean isOn() {
      return this != OFF;
    }
  }

  /** Option for the ProcessTweaks pass */
  public static enum TweakProcessing {
    OFF, // Do not run the ProcessTweaks pass.
    CHECK, // Run the pass, but do not strip out the calls.
    STRIP; // Strip out all calls to goog.tweak.*.

    public boolean isOn() {
      return this != OFF;
    }

    public boolean shouldStrip() {
      return this == STRIP;
    }
  }

  /** What kind of isolation is going to be used */
  public static enum IsolationMode {
    NONE, // output does not include additional isolation.
    IIFE; // The output should be wrapped in an IIFE to isolate global variables.
  }

  /** A mode enum used to indicate the alias strings policy for the AliasStrings pass */
  public static enum AliasStringsMode {
    NONE, // Do not alias string literals.
    LARGE, // Alias all string literals with a length greater than 100 characters.
    ALL // Alias all string literals.
  }

  /**
   * A Role Specific Interface for JS Compiler that represents a data holder object which is used to
   * store goog.scope alias code changes to code made during a compile. There is no guarantee that
   * individual alias changes are invoked in the order they occur during compilation, so
   * implementations should not assume any relevance to the order changes arrive.
   *
   * <p>Calls to the mutators are expected to resolve very quickly, so implementations should not
   * perform expensive operations in the mutator methods.
   */
  public interface AliasTransformationHandler {

    /**
     * Builds an AliasTransformation implementation and returns it to the caller.
     *
     * <p>Callers are allowed to request multiple AliasTransformation instances for the same file,
     * though it is expected that the first and last char values for multiple instances will not
     * overlap.
     *
     * <p>This method is expected to have a side-effect of storing off the created
     * AliasTransformation, which guarantees that invokers of this interface cannot leak
     * AliasTransformation to this implementation that the implementor did not create
     *
     * @param sourceFile the source file the aliases re contained in.
     * @param position the region of the source file associated with the goog.scope call. The item
     *     of the SourcePosition is the returned AliasTransformation
     */
    public AliasTransformation logAliasTransformation(
        String sourceFile, SourcePosition<AliasTransformation> position);
  }

  /**
   * A Role Specific Interface for the JS Compiler to report aliases used to change the code during
   * a compile.
   *
   * <p>While aliases defined by goog.scope are expected to by only 1 per file, and the only
   * top-level structure in the file, this is not enforced.
   */
  public interface AliasTransformation {

    /**
     * Adds an alias definition to the AliasTransformation instance.
     *
     * <p>Last definition for a given alias is kept if an alias is inserted multiple times (since
     * this is generally the behavior in JavaScript code).
     *
     * @param alias the name of the alias.
     * @param definition the definition of the alias.
     */
    void addAlias(String alias, String definition);
  }

  /** A Null implementation of the CodeChanges interface which performs all operations as a No-Op */
  static final AliasTransformationHandler NULL_ALIAS_TRANSFORMATION_HANDLER =
      new NullAliasTransformationHandler();

  private static class NullAliasTransformationHandler implements AliasTransformationHandler {
    private static final AliasTransformation NULL_ALIAS_TRANSFORMATION =
        new NullAliasTransformation();

    @Override
    public AliasTransformation logAliasTransformation(
        String sourceFile, SourcePosition<AliasTransformation> position) {
      position.setItem(NULL_ALIAS_TRANSFORMATION);
      return NULL_ALIAS_TRANSFORMATION;
    }

    private static class NullAliasTransformation implements AliasTransformation {
      @Override
      public void addAlias(String alias, String definition) {}
    }
  }

  /** An environment specifies the built-in externs that are loaded for a given compilation. */
  public static enum Environment {
    /** Hand crafted externs that have traditionally been the default externs. */
    BROWSER,

    /** Only language externs are loaded. */
    CUSTOM
  }

  /** Whether standard input or standard output should be an array of JSON encoded files */
  static enum JsonStreamMode {
    /** stdin/out are both single files. */
    NONE,

    /** stdin is a json stream. */
    IN,

    /** stdout is a json stream. */
    OUT,

    /** stdin and stdout are both json streams. */
    BOTH
  }

  /**
   * A mode enum used to indicate whether J2clPass should be enabled, disabled, or enabled
   * automatically if there is any J2cl source file (i.e. in the AUTO mode).
   */
  public static enum J2clPassMode {
    /** J2clPass is disabled. */
    OFF,
    /** It auto-detects whether there are J2cl generated file. If yes, execute J2clPass. */
    AUTO;

    boolean shouldAddJ2clPasses() {
      return this == AUTO;
    }
  }

  public boolean expectStrictModeInput() {
    return isStrictModeInput.or(getLanguageIn().isDefaultStrict());
  }

  @CanIgnoreReturnValue
  public CompilerOptions setStrictModeInput(boolean isStrictModeInput) {
    this.isStrictModeInput = Optional.of(isStrictModeInput);
    return this;
  }

  public char[] getPropertyReservedNamingFirstChars() {
    char[] reservedChars = null;
    if (polymerVersion != null && polymerVersion > 1) {
      if (reservedChars == null) {
        reservedChars = POLYMER_PROPERTY_RESERVED_FIRST_CHARS;
      } else {
        reservedChars = Chars.concat(reservedChars, POLYMER_PROPERTY_RESERVED_FIRST_CHARS);
      }
    } else if (angularPass) {
      if (reservedChars == null) {
        reservedChars = ANGULAR_PROPERTY_RESERVED_FIRST_CHARS;
      } else {
        reservedChars = Chars.concat(reservedChars, ANGULAR_PROPERTY_RESERVED_FIRST_CHARS);
      }
    }
    return reservedChars;
  }

  public char[] getPropertyReservedNamingNonFirstChars() {
    char[] reservedChars = null;
    if (polymerVersion != null && polymerVersion > 1) {
      if (reservedChars == null) {
        reservedChars = POLYMER_PROPERTY_RESERVED_NON_FIRST_CHARS;
      } else {
        reservedChars = Chars.concat(reservedChars, POLYMER_PROPERTY_RESERVED_NON_FIRST_CHARS);
      }
    }
    return reservedChars;
  }

  @GwtIncompatible("ObjectOutputStream")
  private void writeObject(ObjectOutputStream out) throws IOException {
    out.defaultWriteObject();
    out.writeObject(outputCharset == null ? null : outputCharset.name());
  }

  @GwtIncompatible("ObjectInputStream")
  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    String outputCharsetName = (String) in.readObject();
    if (outputCharsetName != null) {
      outputCharset = Charset.forName(outputCharsetName);
    }
  }

  boolean shouldOptimize() {
    return !skipNonTranspilationPasses
        && !checksOnly
        && !shouldGenerateTypedExterns()
        && !instrumentForCoverageOnly;
  }
}
