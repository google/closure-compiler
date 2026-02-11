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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.primitives.Chars;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.RestrictedApi;
import com.google.javascript.jscomp.annotations.LegacySetFeatureSetCaller;
import com.google.javascript.jscomp.base.Tri;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.jscomp.js.RuntimeJsLibManager.RuntimeLibraryMode;
import com.google.javascript.jscomp.parsing.Config;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Compiler options */
public class CompilerOptions {
  // The number of characters after which we insert a line break in the code
  static final int DEFAULT_LINE_LENGTH_THRESHOLD = 500;

  private static final ImmutableSet<Character> POLYMER_PROPERTY_RESERVED_FIRST_CHARS =
      ImmutableSet.copyOf(Chars.asList("ABCDEFGHIJKLMNOPQRSTUVWXYZ$".toCharArray()));
  private static final ImmutableSet<Character> POLYMER_PROPERTY_RESERVED_NON_FIRST_CHARS =
      ImmutableSet.of('_', '$');
  private static final ImmutableSet<Character> ANGULAR_PROPERTY_RESERVED_FIRST_CHARS =
      ImmutableSet.of('$');

  public static ImmutableSet<Character> getAngularPropertyReservedFirstChars() {
    return ANGULAR_PROPERTY_RESERVED_FIRST_CHARS;
  }

  public static ImmutableSet<Character> getPolymerPropertyReservedFirstChars() {
    return POLYMER_PROPERTY_RESERVED_FIRST_CHARS;
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
  enum BrowserFeaturesetYear {
    YEAR_2012(2012, LanguageMode.ECMASCRIPT5_STRICT.toFeatureSet()),
    YEAR_2018(2018, LanguageMode.ECMASCRIPT_2016.toFeatureSet()),
    YEAR_2019(2019, FeatureSet.BROWSER_2019),
    YEAR_2020(2020, FeatureSet.BROWSER_2020),
    YEAR_2021(2021, FeatureSet.BROWSER_2021),
    YEAR_2022(2022, FeatureSet.BROWSER_2022),
    YEAR_2023(2023, FeatureSet.BROWSER_2023),
    YEAR_2024(2024, FeatureSet.BROWSER_2024),
    YEAR_2025(2025, FeatureSet.BROWSER_2025),
    YEAR_2026(2026, FeatureSet.BROWSER_2026);

    private final int year;
    private final FeatureSet featureSet;
    private static final ImmutableMap<Integer, BrowserFeaturesetYear> YEAR_MAP =
        ImmutableMap.of(
            // go/keep-sorted start
            2012, YEAR_2012,
            2018, YEAR_2018,
            2019, YEAR_2019,
            2020, YEAR_2020,
            2021, YEAR_2021,
            2022, YEAR_2022,
            2023, YEAR_2023,
            2024, YEAR_2024,
            2025, YEAR_2025,
            2026, YEAR_2026
            // go/keep-sorted end
            );

    private BrowserFeaturesetYear(int year, FeatureSet featureSet) {
      this.year = year;
      this.featureSet = featureSet;
    }

    static BrowserFeaturesetYear from(int year) {
      checkState(
          YEAR_MAP.containsKey(year),
          "Illegal browser_featureset_year=%s. We support values 2012, or 2018..2026 only",
          year);
      return YEAR_MAP.get(year);
    }

    void setDependentValuesFromYear(CompilerOptions options) {
      options.setOutputFeatureSet(featureSet);
      // All values targeted by browser featureset year are default strict.
      options.languageOutIsDefaultStrict = Optional.of(true);
      options.setDefineToNumberLiteral("goog.FEATURESET_YEAR", year);
      options.setDefineToBooleanLiteral("$jscomp.ASSUME_ES5", year > 2012);
      options.setDefineToBooleanLiteral("$jscomp.ASSUME_ES6", year >= 2018);
      options.setDefineToBooleanLiteral("$jscomp.ASSUME_ES2020", year >= 2021);
    }

    FeatureSet getFeatureSet() {
      return featureSet;
    }

    int getYear() {
      return year;
    }

    /**
     * Returns the minimum browser featureset year required for the given feature, or null if the
     * feature is in a language level higher than that corresponding to the most recent
     * BrowserFeaturesetYear.
     */
    static @Nullable BrowserFeaturesetYear minimumRequiredFor(FeatureSet.Feature feature) {
      // Depends on YEAR_TO_FEATURE_SET being created with keys in increasing order
      for (int year : BrowserFeaturesetYear.YEAR_MAP.keySet()) {
        BrowserFeaturesetYear yearObject = BrowserFeaturesetYear.YEAR_MAP.get(year);
        if (yearObject.getFeatureSet().contains(feature)) {
          return yearObject;
        }
      }
      return null;
    }
  }

  /** Represents BrowserFeaturesetYear to use for compilation */
  private @Nullable BrowserFeaturesetYear browserFeaturesetYear;

  BrowserFeaturesetYear getBrowserFeaturesetYearObject() {
    return this.browserFeaturesetYear;
  }

  public void setBrowserFeaturesetYear(int year) {
    setBrowserFeaturesetYear(BrowserFeaturesetYear.from(year));
  }

  public void setBrowserFeaturesetYear(BrowserFeaturesetYear year) {
    this.browserFeaturesetYear = year;
    browserFeaturesetYear.setDependentValuesFromYear(this);
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

  public boolean getMergedPrecompiledLibraries() {
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

  ImmutableMap<String, SourceMapInput> getInputSourceMaps() {
    return this.inputSourceMaps;
  }

  /**
   * Whether to infer consts. This should not be configurable by external clients. This is a
   * transitional flag for a new type of const analysis.
   *
   * <p>TODO(nicksantos): Remove this option.
   */
  private boolean inferConsts = true;

  // TODO(tbreisacher): Remove this method after ctemplate issues are solved.
  public void setInferConst(boolean value) {
    inferConsts = value;
  }

  boolean shouldInferConsts() {
    return inferConsts;
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
  }

  private IncrementalCheckMode incrementalCheckMode = IncrementalCheckMode.OFF;

  public void setIncrementalChecks(IncrementalCheckMode value) {
    incrementalCheckMode = value;
    switch (value) {
      case OFF -> {}
      case GENERATE_IJS -> {
        setPreserveTypeAnnotations(true);
        setOutputJs(OutputJs.NORMAL);
      }
    }
  }

  public boolean shouldGenerateTypedExterns() {
    return incrementalCheckMode == IncrementalCheckMode.GENERATE_IJS;
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
  private boolean inferTypes;

  /**
   * Configures the compiler to skip as many passes as possible. If transpilation is requested, it
   * will be run, but all others passes will be skipped.
   */
  private boolean skipNonTranspilationPasses;

  /**
   * Configures the compiler to run expensive validity checks after every pass. Only intended for
   * internal development.
   */
  private DevMode devMode;

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
  private @Nullable MessageBundle messageBundle = null;

  /** Whether we should report an error if a message is absent from a bundle. */
  private boolean strictMessageReplacement;

  // --------------------------------
  // Checks
  // --------------------------------

  /** Checks that all symbols are defined */
  // TODO(tbreisacher): Remove this and deprecate the corresponding setter.
  private boolean checkSymbols;

  /** Checks for suspicious statements that have no effect */
  private boolean checkSuspiciousCode;

  /** Checks types on expressions */
  private boolean checkTypes;

  /** Deprecated. Please use setWarningLevel(DiagnosticGroups.GLOBAL_THIS, level) instead. */
  @Deprecated
  public void setCheckGlobalThisLevel(CheckLevel level) {}

  /**
   * A set of extra annotation names which are accepted and silently ignored when encountered in a
   * source file. Defaults to null which has the same effect as specifying an empty set.
   */
  private @Nullable ImmutableSet<String> extraAnnotationNames;

  // TODO(bradfordcsmith): Investigate how can we use multi-threads as default.
  private int numParallelThreads = 1;

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

  int getNumParallelThreads() {
    return numParallelThreads;
  }

  // --------------------------------
  // Optimizations
  // --------------------------------

  /** Folds constants (e.g. (2 + 3) to 5) */
  private boolean foldConstants;

  /** Remove assignments to values that can not be referenced */
  private boolean deadAssignmentElimination;

  /** Remove assignments to properties that cannot be referenced */
  private Tri deadPropertyAssignmentElimination;

  /** Inlines constants (symbols that are all CAPS) */
  private boolean inlineConstantVars;

  /** For projects that want to avoid the creation of giant functions after inlining. */
  private int maxFunctionSizeAfterInlining;

  static final int UNLIMITED_FUN_SIZE_AFTER_INLINING = -1;

  /** More aggressive function inlining */
  private boolean assumeClosuresOnlyCaptureReferences;

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
  private boolean crossChunkCodeMotionNoStubMethods;

  /**
   * Whether when chunk B depends on chunk A and chunk B declares a symbol, this symbol can be seen
   * in A after B has been loaded. This is often true, but may not be true when loading code using
   * nested eval.
   */
  private boolean parentChunkCanSeeSymbolsDeclaredInChildren;

  /** Merge two variables together as one. */
  private boolean coalesceVariableNames;

  /** Move methods to a deeper chunk */
  private boolean crossChunkMethodMotion;

  /** Inlines trivial getters */
  private boolean inlineGetters;

  /** Inlines variables */
  private boolean inlineVariables;

  /** Inlines variables */
  private boolean inlineLocalVariables;

  // TODO(user): This is temporary. Once flow sensitive inlining is stable
  // Remove this.
  private boolean flowSensitiveInlineVariables;

  /** Removes code associated with unused global names */
  private boolean smartNameRemoval;

  public enum ExtractPrototypeMemberDeclarationsMode {
    OFF,
    USE_GLOBAL_TEMP,
    USE_CHUNK_TEMP,
    USE_IIFE
  }

  /** Extracts common prototype member declarations */
  private ExtractPrototypeMemberDeclarationsMode extractPrototypeMemberDeclarations;

  /** Removes unused member prototypes */
  private boolean removeUnusedPrototypeProperties;

  /** Removes unused member properties */
  private boolean removeUnusedClassProperties;

  /** Removes unused variables */
  private boolean removeUnusedVars;

  /** Removes unused variables in local scope. */
  private boolean removeUnusedLocalVars;

  /** Collapses multiple variable declarations into one */
  private boolean collapseVariableDeclarations;

  /** Collapses anonymous function declarations into named function declarations */
  private boolean collapseAnonymousFunctions;

  /** Aliases string literals to global instances, to reduce code size. */
  private AliasStringsMode aliasStringsMode;

  /** Print string usage as part of the compilation log. */
  private boolean outputJsStringUsage;

  /** Converts quoted property accesses to dot syntax (a['b'] &rarr; a.b) */
  private boolean convertToDottedProperties;

  /** Reduces the size of common function expressions. */
  private boolean rewriteFunctionExpressions;

  /**
   * Remove unused function arguments, remove unused return values, and inlines constant parameters.
   */
  private boolean optimizeCalls;

  /** Removes trivial constructors where ES class implicit constructors are sufficient. */
  private boolean optimizeESClassConstructors;

  /** Use type information to enable additional optimization opportunities. */
  private boolean useTypesForLocalOptimization;

  private boolean useSizeHeuristicToStopOptimizationLoop = true;

  /**
   * Do up to this many iterations of the optimization loop. Setting this field to some small
   * number, say 3 or 4, allows a large project to build faster, but sacrifice some code size.
   */
  private int optimizationLoopMaxIterations;

  // --------------------------------
  // Renaming
  // --------------------------------

  /** Controls which variables get renamed. */
  private VariableRenamingPolicy variableRenaming;

  /** Controls which properties get renamed. */
  private PropertyRenamingPolicy propertyRenaming;

  /** Controls if property renaming only compilation mode is needed. */
  private boolean propertyRenamingOnlyCompilationMode;

  /** Controls label renaming. */
  private boolean labelRenaming;

  /** Reserve property names on the global this object. */
  private boolean reserveRawExports;

  /**
   * Use a renaming heuristic with better stability across source changes. With this option each
   * symbol is more likely to receive the same name between builds. The cost may be a slight
   * increase in code size.
   */
  private boolean preferStableNames;

  /** Generate pseudo names for variables and properties for debugging purposes. */
  private boolean generatePseudoNames;

  /** Specifies a prefix for all globals */
  private @Nullable String renamePrefix;

  /** Specifies the name of an object that will be used to store all non-extern globals. */
  private String renamePrefixNamespace;

  /**
   * Used by tests of the RescopeGlobalSymbols pass to avoid having declare 2 chunks in simple
   * cases.
   */
  private boolean renamePrefixNamespaceAssumeCrossChunkNames = false;

  /** Useful for tests to avoid having to declare two chunks */
  @VisibleForTesting
  public void setRenamePrefixNamespaceAssumeCrossChunkNames(boolean assume) {
    renamePrefixNamespaceAssumeCrossChunkNames = assume;
  }

  boolean assumeCrossChunkNamesForRenamePrefixNamespace() {
    return renamePrefixNamespaceAssumeCrossChunkNames;
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
  private boolean collapseObjectLiterals;

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
  private boolean devirtualizeMethods;

  /**
   * Use @nosideeffects annotations, function bodies and name graph to determine if calls have side
   * effects.
   */
  private boolean computeFunctionSideEffects;

  /** Rename properties to disambiguate between unrelated fields based on type information. */
  private boolean disambiguateProperties;

  /** Rename unrelated properties to the same name to reduce code size. */
  private boolean ambiguateProperties;

  /** Input sourcemap files, indexed by the JS files they refer to */
  private ImmutableMap<String, SourceMapInput> inputSourceMaps;

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
  private VariableMap inputVariableMap;

  /** Input property renaming map. */
  private VariableMap inputPropertyMap;

  /** Whether to export test functions. */
  private boolean exportTestFunctions;

  /** Shared name generator */
  private NameGenerator nameGenerator;

  public void setNameGenerator(NameGenerator nameGenerator) {
    this.nameGenerator = nameGenerator;
  }

  NameGenerator getNameGenerator() {
    return nameGenerator;
  }

  // --------------------------------
  // Special-purpose alterations
  // --------------------------------

  /** Replace UI strings with chrome.i18n.getMessage calls. Used by Chrome extensions/apps. */
  private boolean replaceMessagesWithChromeI18n;

  private String tcProjectId;

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

  String getTcProjectId() {
    return tcProjectId;
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

  private @Nullable String syntheticBlockStartMarker;
  private @Nullable String syntheticBlockEndMarker;

  /** Compiling locale */
  private @Nullable String locale;

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
  private boolean markAsCompiled;

  /** Processes goog.provide() and goog.require() calls */
  private boolean closurePass;

  /** Do not strip goog.provide()/goog.require() calls from the code. */
  private boolean preserveClosurePrimitives;

  /** Processes AngularJS-specific annotations */
  private boolean angularPass;

  /** Processes Polymer code */
  private boolean polymerPass;

  /** Processes cr.* functions */
  private boolean chromePass;

  /** Processes the output of J2CL */
  private J2clPassMode j2clPassMode;

  private boolean j2clMinifierEnabled = true;

  private @Nullable String j2clMinifierPruningManifest = null;

  /** Remove goog.abstractMethod assignments and @abstract methods. */
  private boolean removeAbstractMethods;

  /** Remove goog.asserts calls. */
  private boolean removeClosureAsserts;

  /** Remove J2CL assert calls. */
  private boolean removeJ2clAsserts = true;

  /** Gather CSS names (requires closurePass) */
  private boolean gatherCssNames;

  /** Names of types to strip */
  private ImmutableSet<String> stripTypes;

  /** Name suffixes that determine which variables and properties to strip */
  private ImmutableSet<String> stripNameSuffixes;

  /** Name prefixes that determine which variables and properties to strip */
  private ImmutableSet<String> stripNamePrefixes;

  /** Custom passes */
  private transient @Nullable SetMultimap<CustomPassExecutionTime, CompilerPass> customPasses;

  /** Replacements for @defines. Will be Boolean, Numbers, or Strings */
  private final LinkedHashMap<String, Object> defineReplacements;

  /** What kind of processing to do for goog.tweak functions. */
  private TweakProcessing tweakProcessing;

  /** Move top-level function declarations to the top */
  private boolean rewriteGlobalDeclarationsForTryCatchWrapping;

  private boolean checksOnly;

  /** What type of JS file should be output by this compilation */
  public static enum OutputJs {
    // Don't output anything.
    NONE,
    // Output a "sentinel" file containing just a comment.
    SENTINEL,
    // Output the compiled JS.
    NORMAL,
  }

  private OutputJs outputJs;

  private boolean generateExports;

  private boolean exportLocalPropertyDefinitions;

  /** Map used in the renaming of CSS class names. */
  private @Nullable CssRenamingMap cssRenamingMap;

  /** Skiplist used in the renaming of CSS class names. */
  private ImmutableSet<String> cssRenamingSkiplist;

  /** Replace id generators */
  private boolean replaceIdGenerators = true; // true by default for legacy reasons.

  /** Id generators to replace. */
  private ImmutableMap<String, RenamingMap> idGenerators;

  /** Hash function to use for xid generation. */
  private Xid.HashFunction xidHashFunction;

  /** Hash function to use for generating chunk ID. */
  private Xid.HashFunction chunkIdHashFunction;

  /**
   * A previous map of ids (serialized to a string by a previous compile). This will be used as a
   * hint during the ReplaceIdGenerators pass, which will attempt to reuse the same ids.
   */
  private String idGeneratorsMapSerialized;

  /** Configuration strings */
  private ImmutableList<String> replaceStringsFunctionDescriptions;

  private String replaceStringsPlaceholderToken;

  /** List of properties that we report invalidation errors for. */
  private ImmutableSet<String> propertiesThatMustDisambiguate;

  /** Whether to enforce that the @requireInlining annotation works. */
  private Tri validateRequireInliningAnnotation;

  /** Rewrite CommonJS modules so that they can be concatenated together. */
  private boolean processCommonJSModules = false;

  /** CommonJS module prefix. */
  private ImmutableList<String> moduleRoots =
      ImmutableList.of(ModuleLoader.DEFAULT_FILENAME_PREFIX);

  /** Inject polyfills */
  private boolean rewritePolyfills = false;

  /** Isolates injected polyfills from the global scope. */
  private boolean isolatePolyfills = false;

  /**
   * Configures polyfill injection of all polyfills newer than the given language mode, regardless
   * of whether they are actually referenced in the code being compiled.
   *
   * <p>How is this different from --inject_library? --inject_library takes a specific JS file
   * defined in the jscomp/js package, and injects that and all of its dependencies. This option
   * takes a language mode.
   *
   * <p>For example, the common use of `--inject_library` is passing `--inject_library=es6_runtime`.
   * es6_runtime.js is a file that contains both all the ES6 polyfills and also ES6 transpilation
   * utilities (library code referenced by transpiled code). So
   *
   * <ul>
   *   <li>es6_runtime includes transpilation utilities, not just polyfills.
   *       `--inject_polyfills_newer_than=ES5` will not add transpilation utilities, just the
   *       polyfills,
   *   <li>if someone forgot to add a new polyfill to es6_runtime.js, then `--inject_library` will
   *       not add it, but `--inject_polyfills_newer_than=ES5` will add it.
   * </ul>
   */
  private LanguageMode injectPolyfillsNewerThan = null;

  /**
   * Whether to emit the safe but slower/more verbose transpilation of class super, when the
   * compiler cannot statically determine whether the superclass is an ES6 class.
   */
  private Es6SubclassTranspilation es6SubclassTranspilation =
      Es6SubclassTranspilation.CONCISE_UNSAFE;

  /** Whether to instrument reentrant functions for AsyncContext. */
  private boolean instrumentAsyncContext = false;

  /** Runtime libraries to always inject. */
  private ImmutableList<String> forceLibraryInjection = ImmutableList.of();

  /** Runtime libraries to never inject. */
  private RuntimeLibraryMode runtimeLibraryMode = RuntimeLibraryMode.INJECT;

  private boolean assumeForwardDeclaredForMissingTypes = false;

  /**
   * A Set of goog.requires to be removed. If null, ALL of the unused goog.requires will be counted
   * as a candidate to be removed.
   */
  private @Nullable ImmutableSet<String> unusedImportsToRemove;

  /**
   * If {@code true}, considers all missing types to be forward declared (useful for partial
   * compilation).
   */
  public void setAssumeForwardDeclaredForMissingTypes(
      boolean assumeForwardDeclaredForMissingTypes) {
    this.assumeForwardDeclaredForMissingTypes = assumeForwardDeclaredForMissingTypes;
  }

  boolean assumeForwardDeclaredForMissingTypes() {
    return assumeForwardDeclaredForMissingTypes;
  }

  // --------------------------------
  // Output options
  // --------------------------------

  /** Do not strip closure-style type annotations from code. */
  private boolean preserveTypeAnnotations;

  /**
   * To distinguish between gents and non-gents mode so that we can turn off checking the sanity of
   * the source location of comments, and also provide a different mode for comment printing between
   * those two.
   */
  private boolean gentsMode;

  /** Output in pretty indented format */
  private boolean prettyPrint;

  /** Line break the output a bit more aggressively */
  private boolean lineBreak;

  /** Prints a separator comment before each JS script */
  private boolean printInputDelimiter;

  /** The string to use as the separator for printInputDelimiter */
  private String inputDelimiter = "// Input %num%";

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

  private boolean preferSingleQuotes;

  /**
   * Normally, when there are an equal number of single and double quotes in a string, the compiler
   * will use double quotes. Set this to true to prefer single quotes.
   */
  public void setPreferSingleQuotes(boolean enabled) {
    this.preferSingleQuotes = enabled;
  }

  boolean shouldPreferSingleQuotes() {
    return preferSingleQuotes;
  }

  private boolean trustedStrings;

  /**
   * Some people want to put arbitrary user input into strings, which are then run through the
   * compiler. These scripts are then put into HTML. By default, we assume strings are untrusted. If
   * the compiler is run from the command-line, we assume that strings are trusted.
   */
  public void setTrustedStrings(boolean yes) {
    trustedStrings = yes;
  }

  boolean assumeTrustedStrings() {
    return trustedStrings;
  }

  // Should only be used when debugging compiler bugs.
  private boolean printSourceAfterEachPass;

  // Used to narrow down the printed source when overall input size is large. If these are both
  // empty the entire source is printed.
  private List<String> filesToPrintAfterEachPassRegexList = ImmutableList.of();
  private List<String> chunksToPrintAfterEachPassRegexList = ImmutableList.of();
  private List<String> qnameUsesToPrintAfterEachPassList = ImmutableList.of();

  public void setPrintSourceAfterEachPass(boolean printSource) {
    this.printSourceAfterEachPass = printSource;
  }

  boolean shouldPrintSourceAfterEachPass() {
    return printSourceAfterEachPass;
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

  List<String> getFilesToPrintAfterEachPassRegexList() {
    return filesToPrintAfterEachPassRegexList;
  }

  List<String> getChunksToPrintAfterEachPassRegexList() {
    return chunksToPrintAfterEachPassRegexList;
  }

  List<String> getQnameUsesToPrintAfterEachPassList() {
    return qnameUsesToPrintAfterEachPassList;
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

  private ErrorFormat errorFormat;

  private ComposeWarningsGuard warningsGuard = new ComposeWarningsGuard();

  private int summaryDetailLevel = 1;

  private int lineLengthThreshold = DEFAULT_LINE_LENGTH_THRESHOLD;

  /**
   * Whether to use the original names of nodes in the code output. This option is only really
   * useful when using the compiler to print code meant to check in to source.
   */
  private boolean useOriginalNamesInOutput = false;

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
  private SourceMap.DetailLevel sourceMapDetailLevel = SourceMap.DetailLevel.ALL;

  /** The source map file format */
  private SourceMap.Format sourceMapFormat = SourceMap.Format.DEFAULT;

  /** Whether to parse inline source maps. */
  private boolean parseInlineSourceMaps = true;

  /**
   * Whether to apply input source maps to the output, i.e. map back to original inputs from input
   * files that have source maps applied to them.
   */
  private boolean applyInputSourceMaps = false;

  /**
   * Whether to resolve source mapping annotations. Cannot do this in an appengine or js environment
   * since we don't have access to the filesystem.
   */
  private boolean resolveSourceMapAnnotations = true;

  private ImmutableList<? extends SourceMap.LocationMapping> sourceMapLocationMappings =
      ImmutableList.of();

  /** Whether to include full file contents in the source map. */
  private boolean sourceMapIncludeSourcesContent = false;

  /** Charset to use when generating code. If null, then output ASCII. */
  private transient Charset outputCharset;

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
   * When {@code true}, the compiler assumes that all non-quoted properties are only referenced by
   * their non-quoted names (not reflectively), or otherwise are defined in the externs. This is
   * basically the set of assumptions that {@link PropertyRenamingPolicy.ALL_UNQUOTED} makes, but
   * has repercussions beyond just property renaming.
   *
   * <p>When {@code false}, the compiler does not attempt to do any property optimizations.
   *
   * <p>NOTE: lharker - I added this in 2025 specifically for @closureUnaware support. Previously
   * this was always implicitly true. I've tried to update various parts of the compiler to check
   * this field, rather than assuming property definitions are known, but it's quite likely there
   * are still some silent assumptions somewhere. So this is still experimental and needs more
   * validation on real code.
   */
  private boolean assumePropertiesAreStaticallyAnalyzable = true;

  public void setAssumePropertiesAreStaticallyAnalyzable(boolean x) {
    this.assumePropertiesAreStaticallyAnalyzable = x;
  }

  public boolean getAssumePropertiesAreStaticallyAnalyzable() {
    return assumePropertiesAreStaticallyAnalyzable;
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

  /** Handler for compiler warnings and errors. */
  private transient @Nullable ErrorHandler errorHandler;

  private InstrumentOption instrumentForCoverageOption;

  private String productionInstrumentationArrayName;

  /** List of conformance configs to use in CheckConformance. */
  private ImmutableList<ConformanceConfig> conformanceConfigs = ImmutableList.of();

  private ConformanceReportingMode conformanceReportingMode =
      ConformanceReportingMode.IGNORE_LIBRARY_LEVEL_BEHAVIOR_SPECIFIED_IN_CONFIG;

  public ConformanceReportingMode getConformanceReportingMode() {
    return conformanceReportingMode;
  }

  public void setConformanceReportingMode(ConformanceReportingMode mode) {
    this.conformanceReportingMode = mode;
  }

  /**
   * Whether to respect the library-level non-allowlisted conformance behavior specified in the
   * conformance config.
   */
  public static enum ConformanceReportingMode {
    /**
     * Used when running conformance checks at the binary level. It ignores the library-level
     * conformance behavior specified in the conformance config and always report the conformance
     * violations. For example, setting this allows the compiler to ignore the "RECORD_ONLY"
     * library-level behavior specified in the config and unconditionally report the non-allowlisted
     * violations when it is running at the binary level.
     */
    IGNORE_LIBRARY_LEVEL_BEHAVIOR_SPECIFIED_IN_CONFIG, // default
    /**
     * Used when running conformance checks at the library level. It respects the library-level
     * conformance behavior specified in the conformance config and only reports the conformance
     * violations according to the behavior. For example, setting this respects the "RECORD_ONLY"
     * library-level behavior specified in the Boq Web configs and only records the violations when
     * it is running at the library level, and also respects the "REPORT_AS_BUILD_ERROR" behavior
     * specified in the global conformance configs to report errors for go/jscp at the library
     * level.
     */
    RESPECT_LIBRARY_LEVEL_BEHAVIOR_SPECIFIED_IN_CONFIG
  }

  /**
   * Remove the first match of this regex from any paths when checking conformance allowlists.
   *
   * <p>You can use this to make absolute paths relative to the root of your source tree. This is
   * useful to work around CI and build systems that use absolute paths.
   */
  private Optional<Pattern> conformanceRemoveRegexFromPath =
      Optional.of(
          Pattern.compile("^((.*/)?google3/)?(/?(blaze|bazel)-out/[^/]+/(bin|genfiles)/)?"));

  public void setConformanceRemoveRegexFromPath(Optional<Pattern> pattern) {
    conformanceRemoveRegexFromPath = pattern;
  }

  public Optional<Pattern> getConformanceRemoveRegexFromPath() {
    return conformanceRemoveRegexFromPath;
  }

  /** For use in {@link CompilationLevel#WHITESPACE_ONLY} mode, when using goog.module. */
  private boolean wrapGoogModulesForWhitespaceOnly = true;

  public void setWrapGoogModulesForWhitespaceOnly(boolean enable) {
    this.wrapGoogModulesForWhitespaceOnly = enable;
  }

  boolean shouldWrapGoogModulesForWhitespaceOnly() {
    return this.wrapGoogModulesForWhitespaceOnly;
  }

  /** Print all configuration options to stderr after the compiler is initialized. */
  private boolean printConfig = false;

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
  private ResolutionMode moduleResolutionMode;

  /**
   * Map of prefix replacements for use when moduleResolutionMode is {@link
   * ResolutionMode#BROWSER_WITH_TRANSFORMED_PREFIXES}.
   */
  private ImmutableMap<String, String> browserResolverPrefixReplacements;

  private ModuleLoader.PathEscaper pathEscaper;

  /** Which entries to look for in package.json files when processing modules */
  private List<String> packageJsonEntryNames;

  /**
   * Should the compiler print its configuration options to stderr when they are initialized?
   *
   * <p>Default {@code false}.
   */
  public void setPrintConfig(boolean printConfig) {
    this.printConfig = printConfig;
  }

  boolean shouldPrintConfig() {
    return this.printConfig;
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

  private ChunkOutputType chunkOutputType;

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
    deadPropertyAssignmentElimination = Tri.UNKNOWN;
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
    validateRequireInliningAnnotation = Tri.UNKNOWN;

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
    polymerPass = false;
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
      if (value instanceof Boolean b) {
        map.put(name, NodeUtil.booleanNode(b.booleanValue()));
      } else if (value instanceof Number number) {
        map.put(name, NodeUtil.numberNode(number.doubleValue(), null));
      } else if (value instanceof String string) {
        map.put(name, IR.string(string));
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
    setSkipNonTranspilationPasses(true);
  }

  /** Whether the warnings guard in this Options object enables the given group of warnings. */
  boolean enables(DiagnosticGroup group) {
    return this.warningsGuard.mustRunChecks(group) == Tri.TRUE;
  }

  /** Whether the warnings guard in this Options object disables the given group of warnings. */
  boolean disables(DiagnosticGroup group) {
    return this.warningsGuard.mustRunChecks(group) == Tri.FALSE;
  }

  private ImmutableList<String> unknownDefinesToIgnore = ImmutableList.of();

  public void setUnknownDefinesToIgnore(ImmutableList<String> unknownDefinesToIgnore) {
    this.unknownDefinesToIgnore = unknownDefinesToIgnore;
  }

  public void addUnknownDefinesToIgnore(ImmutableList<String> unknownDefinesToIgnore) {
    this.unknownDefinesToIgnore =
        ImmutableList.<String>builder()
            .addAll(this.unknownDefinesToIgnore)
            .addAll(unknownDefinesToIgnore)
            .build();
  }

  ImmutableList<String> getUnknownDefinesToIgnore() {
    return this.unknownDefinesToIgnore;
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

  boolean shouldReplaceIdGenerators() {
    return replaceIdGenerators;
  }

  /** Sets the id generators to replace. */
  public void setIdGenerators(Set<String> idGenerators) {
    ImmutableMap.Builder<String, RenamingMap> builder = ImmutableMap.builder();
    for (String name : idGenerators) {
      builder.put(name, RenamingToken.INCONSISTENT);
    }
    this.idGenerators = builder.buildOrThrow();
  }

  /** Sets the id generators to replace. */
  public void setIdGenerators(Map<String, RenamingMap> idGenerators) {
    this.idGenerators = ImmutableMap.copyOf(idGenerators);
  }

  Map<String, RenamingMap> getIdGenerators() {
    return idGenerators;
  }

  /**
   * A previous map of ids (serialized to a string by a previous compile). This will be used as a
   * hint during the ReplaceIdGenerators pass, which will attempt to reuse the same ids.
   */
  public void setIdGeneratorsMap(String previousMappings) {
    this.idGeneratorsMapSerialized = previousMappings;
  }

  String getIdGeneratorsMapSerialized() {
    return idGeneratorsMapSerialized;
  }

  /** Sets the hash function to use for Xid */
  public void setXidHashFunction(Xid.HashFunction xidHashFunction) {
    this.xidHashFunction = xidHashFunction;
  }

  Xid.HashFunction getXidHashFunction() {
    return xidHashFunction;
  }

  /** Sets the hash function to use for chunk ID generation */
  public void setChunkIdHashFunction(Xid.HashFunction chunkIdHashFunction) {
    this.chunkIdHashFunction = chunkIdHashFunction;
  }

  Xid.HashFunction getChunkIdHashFunction() {
    return chunkIdHashFunction;
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

  int getMaxFunctionSizeAfterInlining() {
    return maxFunctionSizeAfterInlining;
  }

  public void setInlineVariables(boolean inlineVariables) {
    this.inlineVariables = inlineVariables;
  }

  public boolean shouldInlineVariables() {
    return inlineVariables;
  }

  /** Set the variable inlining policy for the compiler. */
  public void setInlineVariables(Reach reach) {
    switch (reach) {
      case ALL -> {
        this.inlineVariables = true;
        this.inlineLocalVariables = true;
      }
      case LOCAL_ONLY -> {
        this.inlineVariables = false;
        this.inlineLocalVariables = true;
      }
      case NONE -> {
        this.inlineVariables = false;
        this.inlineLocalVariables = false;
      }
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
      case ALL -> {
        this.removeUnusedVars = true;
        this.removeUnusedLocalVars = true;
      }
      case LOCAL_ONLY -> {
        this.removeUnusedVars = false;
        this.removeUnusedLocalVars = true;
      }
      case NONE -> {
        this.removeUnusedVars = false;
        this.removeUnusedLocalVars = false;
      }
    }
  }

  public boolean shouldRemoveUnusedVariables() {
    return removeUnusedVars;
  }

  public boolean shouldRemoveUnusedLocalVariables() {
    return removeUnusedLocalVars;
  }

  /** Sets the functions whose debug strings to replace. */
  public void setReplaceStringsConfiguration(
      String placeholderToken, List<String> functionDescriptors) {
    this.replaceStringsPlaceholderToken = placeholderToken;
    this.replaceStringsFunctionDescriptions = ImmutableList.copyOf(functionDescriptors);
  }

  public void setRemoveAbstractMethods(boolean remove) {
    this.removeAbstractMethods = remove;
  }

  boolean shouldRemoveAbstractMethods() {
    return removeAbstractMethods;
  }

  public void setRemoveClosureAsserts(boolean remove) {
    this.removeClosureAsserts = remove;
  }

  boolean shouldRemoveClosureAsserts() {
    return removeClosureAsserts;
  }

  public void setRemoveJ2clAsserts(boolean remove) {
    this.removeJ2clAsserts = remove;
  }

  boolean shouldRemoveJ2clAsserts() {
    return removeJ2clAsserts;
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

  boolean isChecksOnly() {
    return this.checksOnly;
  }

  public void setOutputJs(OutputJs outputJs) {
    this.outputJs = outputJs;
  }

  OutputJs getOutputJs() {
    return this.outputJs;
  }

  public void setGenerateExports(boolean generateExports) {
    this.generateExports = generateExports;
  }

  public boolean shouldGenerateExports() {
    return this.generateExports;
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

  boolean getAngularPass() {
    return angularPass;
  }

  public void setPolymerVersion(Integer polymerVersion) {
    checkArgument(
        polymerVersion == null || polymerVersion == 1 || polymerVersion == 2,
        "Invalid Polymer version: (%s)",
        polymerVersion);
    // Internally we model the polymerVersion as a boolean. if True, run the PolymerPass; otherwise
    // don't. The public API is an integer for historical reasons & in case we need to modify it
    // in the future.
    this.polymerPass = polymerVersion != null;
  }

  boolean isPolymerPassEnabled() {
    return polymerPass;
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

  J2clPassMode getJ2clPass() {
    return j2clPassMode;
  }

  public void setJ2clMinifierEnabled(boolean enabled) {
    this.j2clMinifierEnabled = enabled;
  }

  boolean isJ2clMinifierEnabled() {
    return j2clMinifierEnabled;
  }

  public void setJ2clMinifierPruningManifest(String j2clMinifierPruningManifest) {
    this.j2clMinifierPruningManifest = j2clMinifierPruningManifest;
  }

  String getJ2clMinifierPruningManifest() {
    return j2clMinifierPruningManifest;
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

  int getSummaryDetailLevel() {
    return summaryDetailLevel;
  }

  public void setExtraAnnotationNames(Iterable<String> extraAnnotationNames) {
    this.extraAnnotationNames = ImmutableSet.copyOf(extraAnnotationNames);
  }

  ImmutableSet<String> getExtraAnnotationNames() {
    return extraAnnotationNames;
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

  ErrorHandler getErrorHandler() {
    return errorHandler;
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

  /**
   * Ensures that functions labelled with `@requireInlining` JSDoc are, in fact, actually inlined by
   * the optimizer.
   *
   * <p>This may only be set when using advanced optimizations, as otherwise the compiler is unable
   * to do most inlining. The compiler will throw an exception during initialization if this is set
   * to true, but advanced optimizations are not being used.
   */
  public void setValidateRequiredInlinings(boolean validateRequireInliningAnnotation) {
    this.validateRequireInliningAnnotation = Tri.forBoolean(validateRequireInliningAnnotation);
  }

  Tri getShouldValidateRequiredInlinings() {
    return validateRequireInliningAnnotation;
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

  boolean getSkipNonTranspilationPasses() {
    return skipNonTranspilationPasses;
  }

  public void setDevMode(DevMode devMode) {
    this.devMode = devMode;
  }

  DevMode getDevMode() {
    return devMode;
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

  public MessageBundle getMessageBundle() {
    return messageBundle;
  }

  public void setCheckSymbols(boolean checkSymbols) {
    this.checkSymbols = checkSymbols;
  }

  public boolean getCheckSymbols() {
    return checkSymbols;
  }

  public void setCheckSuspiciousCode(boolean checkSuspiciousCode) {
    this.checkSuspiciousCode = checkSuspiciousCode;
  }

  public boolean getCheckSuspiciousCode() {
    return checkSuspiciousCode;
  }

  public void setCheckTypes(boolean checkTypes) {
    this.checkTypes = checkTypes;
  }

  public boolean getCheckTypes() {
    return checkTypes;
  }

  public void setFoldConstants(boolean foldConstants) {
    this.foldConstants = foldConstants;
  }

  public boolean shouldFoldConstants() {
    return foldConstants;
  }

  public void setDeadAssignmentElimination(boolean deadAssignmentElimination) {
    this.deadAssignmentElimination = deadAssignmentElimination;
  }

  public boolean shouldRunDeadAssignmentElimination() {
    return deadAssignmentElimination;
  }

  public void setDeadPropertyAssignmentElimination(boolean deadPropertyAssignmentElimination) {
    this.deadPropertyAssignmentElimination = Tri.forBoolean(deadPropertyAssignmentElimination);
  }

  public boolean shouldRunDeadPropertyAssignmentElimination() {
    // By default, run dead property assignment elimination when both
    //   1. regular dead assignment elimiantion is enabled.
    //   2. the polymer pass is disabled.
    // This is because Polymer relies heavily on getters and setters. Those are very incompatible
    // eliminating seemingly "dead" property assignments. See b/28912819.
    return deadPropertyAssignmentElimination.toBoolean(
        this.deadAssignmentElimination && !this.polymerPass);
  }

  public void setInlineConstantVars(boolean inlineConstantVars) {
    this.inlineConstantVars = inlineConstantVars;
  }

  public boolean shouldInlineConstantVars() {
    return inlineConstantVars;
  }

  public void setCrossChunkCodeMotion(boolean crossChunkCodeMotion) {
    this.crossChunkCodeMotion = crossChunkCodeMotion;
  }

  public void setCrossChunkCodeMotionNoStubMethods(boolean crossChunkCodeMotionNoStubMethods) {
    this.crossChunkCodeMotionNoStubMethods = crossChunkCodeMotionNoStubMethods;
  }

  boolean getCrossChunkCodeMotionNoStubMethods() {
    return crossChunkCodeMotionNoStubMethods;
  }

  public void setParentChunkCanSeeSymbolsDeclaredInChildren(
      boolean parentChunkCanSeeSymbolsDeclaredInChildren) {
    this.parentChunkCanSeeSymbolsDeclaredInChildren = parentChunkCanSeeSymbolsDeclaredInChildren;
  }

  boolean getParentChunkCanSeeSymbolsDeclaredInChildren() {
    return parentChunkCanSeeSymbolsDeclaredInChildren;
  }

  public void setCrossChunkMethodMotion(boolean crossChunkMethodMotion) {
    this.crossChunkMethodMotion = crossChunkMethodMotion;
  }

  boolean getCrossChunkMethodMotion() {
    return crossChunkMethodMotion;
  }

  public void setCoalesceVariableNames(boolean coalesceVariableNames) {
    this.coalesceVariableNames = coalesceVariableNames;
  }

  public boolean shouldCoalesceVariableNames() {
    return coalesceVariableNames;
  }

  public void setInlineLocalVariables(boolean inlineLocalVariables) {
    this.inlineLocalVariables = inlineLocalVariables;
  }

  boolean shouldInlineLocalVariables() {
    return inlineLocalVariables;
  }

  public void setFlowSensitiveInlineVariables(boolean enabled) {
    this.flowSensitiveInlineVariables = enabled;
  }

  boolean getFlowSensitiveInlineVariables() {
    return flowSensitiveInlineVariables;
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

  public boolean getSmartNameRemoval() {
    return smartNameRemoval;
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

  ExtractPrototypeMemberDeclarationsMode getExtractPrototypeMemberDeclarationsMode() {
    return extractPrototypeMemberDeclarations;
  }

  public void setRemoveUnusedPrototypeProperties(boolean enabled) {
    this.removeUnusedPrototypeProperties = enabled;
    // InlineSimpleMethods makes similar assumptions to
    // RemoveUnusedCode, so they are enabled together.
    this.inlineGetters = enabled;
  }

  public boolean shouldRemoveUnusedPrototypeProperties() {
    return removeUnusedPrototypeProperties;
  }

  boolean shouldInlineGetters() {
    return inlineGetters;
  }

  public void setCollapseVariableDeclarations(boolean enabled) {
    this.collapseVariableDeclarations = enabled;
  }

  public boolean shouldCollapseVariableDeclarations() {
    return collapseVariableDeclarations;
  }

  public void setCollapseAnonymousFunctions(boolean enabled) {
    this.collapseAnonymousFunctions = enabled;
  }

  public boolean shouldCollapseAnonymousFunctions() {
    return collapseAnonymousFunctions;
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

  boolean shouldOutputJsStringUsage() {
    return outputJsStringUsage;
  }

  public void setConvertToDottedProperties(boolean convertToDottedProperties) {
    this.convertToDottedProperties = convertToDottedProperties;
  }

  public boolean shouldConvertToDottedProperties() {
    return convertToDottedProperties;
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

  public boolean shouldRewriteFunctionExpressions() {
    return rewriteFunctionExpressions;
  }

  public void setOptimizeCalls(boolean optimizeCalls) {
    this.optimizeCalls = optimizeCalls;
  }

  public boolean shouldOptimizeCalls() {
    return optimizeCalls;
  }

  public boolean getOptimizeESClassConstructors() {
    return this.optimizeESClassConstructors;
  }

  public void setOptimizeESClassConstructors(boolean optimizeESClassConstructors) {
    this.optimizeESClassConstructors = optimizeESClassConstructors;
  }

  public void setVariableRenaming(VariableRenamingPolicy variableRenaming) {
    this.variableRenaming = variableRenaming;
  }

  public VariableRenamingPolicy getVariableRenaming() {
    return variableRenaming;
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

  public boolean shouldRenameLabels() {
    return labelRenaming;
  }

  public void setReserveRawExports(boolean reserveRawExports) {
    this.reserveRawExports = reserveRawExports;
  }

  boolean shouldReserveRawExports() {
    return reserveRawExports;
  }

  public void setPreferStableNames(boolean preferStableNames) {
    this.preferStableNames = preferStableNames;
  }

  boolean shouldPreferStableNames() {
    return this.preferStableNames;
  }

  public void setGeneratePseudoNames(boolean generatePseudoNames) {
    this.generatePseudoNames = generatePseudoNames;
  }

  public boolean shouldGeneratePseudoNames() {
    return this.generatePseudoNames;
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

  String getRenamePrefix() {
    return this.renamePrefix;
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

  PropertyCollapseLevel getCollapsePropertiesLevel() {
    return this.collapsePropertiesLevel;
  }

  public void setDevirtualizeMethods(boolean devirtualizeMethods) {
    this.devirtualizeMethods = devirtualizeMethods;
  }

  public boolean shouldDevirtualizeMethods() {
    return this.devirtualizeMethods;
  }

  public void setComputeFunctionSideEffects(boolean computeFunctionSideEffects) {
    this.computeFunctionSideEffects = computeFunctionSideEffects;
  }

  public boolean shouldComputeFunctionSideEffects() {
    return this.computeFunctionSideEffects;
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

  VariableMap getInputVariableMap() {
    return this.inputVariableMap;
  }

  public void setInputPropertyMap(VariableMap inputPropertyMap) {
    this.inputPropertyMap = inputPropertyMap;
  }

  VariableMap getInputPropertyMap() {
    return this.inputPropertyMap;
  }

  public void setExportTestFunctions(boolean exportTestFunctions) {
    this.exportTestFunctions = exportTestFunctions;
  }

  boolean shouldExportTestFunctions() {
    return this.exportTestFunctions;
  }

  public void setSyntheticBlockStartMarker(String syntheticBlockStartMarker) {
    this.syntheticBlockStartMarker = syntheticBlockStartMarker;
  }

  public void setSyntheticBlockEndMarker(String syntheticBlockEndMarker) {
    this.syntheticBlockEndMarker = syntheticBlockEndMarker;
  }

  String getSyntheticBlockStartMarker() {
    return this.syntheticBlockStartMarker;
  }

  String getSyntheticBlockEndMarker() {
    return this.syntheticBlockEndMarker;
  }

  public void setLocale(String locale) {
    this.locale = locale;
  }

  public String getLocale() {
    return this.locale;
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

  boolean shouldMarkAsCompiled() {
    return this.markAsCompiled;
  }

  public void setClosurePass(boolean closurePass) {
    this.closurePass = closurePass;
  }

  public boolean getClosurePass() {
    return this.closurePass;
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

  boolean shouldPreserveTypeAnnotations() {
    return this.preserveTypeAnnotations;
  }

  public void setGentsMode(boolean gentsMode) {
    this.gentsMode = gentsMode;
  }

  boolean getGentsMode() {
    return this.gentsMode;
  }

  public void setGatherCssNames(boolean gatherCssNames) {
    this.gatherCssNames = gatherCssNames;
  }

  boolean shouldGatherCssNames() {
    return this.gatherCssNames;
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

  ImmutableSet<String> getStripNameSuffixes() {
    return this.stripNameSuffixes;
  }

  /**
   * @deprecated StripCode is deprecated. Code should be designed to be removed by other means.
   */
  @Deprecated
  public void setStripNamePrefixes(Set<String> stripNamePrefixes) {
    this.stripNamePrefixes = ImmutableSet.copyOf(stripNamePrefixes);
  }

  ImmutableSet<String> getStripNamePrefixes() {
    return this.stripNamePrefixes;
  }

  public void addCustomPass(CustomPassExecutionTime time, CompilerPass customPass) {
    if (customPasses == null) {
      customPasses = LinkedHashMultimap.create();
    }
    customPasses.put(time, customPass);
  }

  ImmutableList<CompilerPass> getCustomPassesAt(CustomPassExecutionTime executionTime) {
    return this.customPasses == null
        ? ImmutableList.of()
        : ImmutableList.copyOf(this.customPasses.get(executionTime));
  }

  public void setDefineReplacements(Map<String, Object> defineReplacements) {
    this.defineReplacements.clear();
    this.defineReplacements.putAll(defineReplacements);
  }

  private @Nullable String enableZonesDefineName = null;

  public @Nullable String getEnableZonesDefineName() {
    return this.enableZonesDefineName;
  }

  public void setEnableZonesDefineName(@Nullable String enableZonesDefineName) {
    this.enableZonesDefineName = enableZonesDefineName;
  }

  private @Nullable Pattern zoneInputPattern = null;

  public @Nullable Pattern getZoneInputPattern() {
    return this.zoneInputPattern;
  }

  public void setZoneInputPattern(@Nullable Pattern zoneInputPattern) {
    this.zoneInputPattern = zoneInputPattern;
  }

  public void setRewriteGlobalDeclarationsForTryCatchWrapping(boolean rewrite) {
    this.rewriteGlobalDeclarationsForTryCatchWrapping = rewrite;
  }

  public boolean shouldRewriteGlobalDeclarationsForTryCatchWrapping() {
    return this.rewriteGlobalDeclarationsForTryCatchWrapping;
  }

  public void setCssRenamingMap(CssRenamingMap cssRenamingMap) {
    this.cssRenamingMap = cssRenamingMap;
  }

  public CssRenamingMap getCssRenamingMap() {
    return this.cssRenamingMap;
  }

  public void setCssRenamingSkiplist(Set<String> skiplist) {
    this.cssRenamingSkiplist = ImmutableSet.copyOf(skiplist);
  }

  ImmutableSet<String> getCssRenamingSkiplist() {
    return this.cssRenamingSkiplist;
  }

  public void setReplaceStringsFunctionDescriptions(
      List<String> replaceStringsFunctionDescriptions) {
    this.replaceStringsFunctionDescriptions =
        ImmutableList.copyOf(replaceStringsFunctionDescriptions);
  }

  ImmutableList<String> getReplaceStringsFunctionDescriptions() {
    return this.replaceStringsFunctionDescriptions;
  }

  public void setReplaceStringsPlaceholderToken(String replaceStringsPlaceholderToken) {
    this.replaceStringsPlaceholderToken = replaceStringsPlaceholderToken;
  }

  String getReplaceStringsPlaceholderToken() {
    return this.replaceStringsPlaceholderToken;
  }

  /**
   * How @closureUnaware code blocks should be handled.
   *
   * <p>PASS_THROUGH: they are entirely hidden from the compiler, as if they were an evaled string.
   *
   * <p>SIMPLE_OPTIMIZATIONS_AND_TRANSPILATION: This is *experimental* - we want to support some
   * minimal transpilation & safe optimizations. TODO: b/421971366 - implement this flag.
   */
  public enum ClosureUnawareMode {
    PASS_THROUGH,
    SIMPLE_OPTIMIZATIONS_AND_TRANSPILATION
  }

  private ClosureUnawareMode closureUnawareMode = ClosureUnawareMode.PASS_THROUGH;

  /**
   * Opts into experimental support for transpiling/optimizing @closureUnaware code blocks, rather
   * than just passing them through compilation unchanged.
   */
  public void setClosureUnawareMode(ClosureUnawareMode mode) {
    this.closureUnawareMode = mode;
  }

  ClosureUnawareMode getClosureUnawareMode() {
    return this.closureUnawareMode;
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

  boolean shouldAddLineBreak() {
    return this.lineBreak;
  }

  public void setPrintInputDelimiter(boolean printInputDelimiter) {
    this.printInputDelimiter = printInputDelimiter;
  }

  boolean shouldPrintInputDelimiter() {
    return this.printInputDelimiter;
  }

  public void setInputDelimiter(String inputDelimiter) {
    this.inputDelimiter = inputDelimiter;
  }

  public String getInputDelimiter() {
    return this.inputDelimiter;
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

  boolean getApplyInputSourceMaps() {
    return this.applyInputSourceMaps;
  }

  public void setResolveSourceMapAnnotations(boolean resolveSourceMapAnnotations) {
    this.resolveSourceMapAnnotations = resolveSourceMapAnnotations;
  }

  boolean getResolveSourceMapAnnotations() {
    return this.resolveSourceMapAnnotations;
  }

  public void setSourceMapIncludeSourcesContent(boolean sourceMapIncludeSourcesContent) {
    this.sourceMapIncludeSourcesContent = sourceMapIncludeSourcesContent;
  }

  boolean getSourceMapIncludeSourcesContent() {
    return this.sourceMapIncludeSourcesContent;
  }

  public void setParseInlineSourceMaps(boolean parseInlineSourceMaps) {
    this.parseInlineSourceMaps = parseInlineSourceMaps;
  }

  boolean getParseInlineSourceMaps() {
    return this.parseInlineSourceMaps;
  }

  public void setSourceMapDetailLevel(SourceMap.DetailLevel sourceMapDetailLevel) {
    this.sourceMapDetailLevel = sourceMapDetailLevel;
  }

  SourceMap.DetailLevel getSourceMapDetailLevel() {
    return this.sourceMapDetailLevel;
  }

  public void setSourceMapFormat(SourceMap.Format sourceMapFormat) {
    this.sourceMapFormat = sourceMapFormat;
  }

  SourceMap.Format getSourceMapFormat() {
    return this.sourceMapFormat;
  }

  public void setSourceMapLocationMappings(
      List<? extends SourceMap.LocationMapping> sourceMapLocationMappings) {
    this.sourceMapLocationMappings = ImmutableList.copyOf(sourceMapLocationMappings);
  }

  ImmutableList<? extends SourceMap.LocationMapping> getSourceMapLocationMappings() {
    return this.sourceMapLocationMappings;
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
    this.moduleRoots = ImmutableList.copyOf(moduleRoots);
  }

  ImmutableList<String> getModuleRoots() {
    return this.moduleRoots;
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

  public void setInjectPolyfillsNewerThan(LanguageMode injectPolyfillsNewerThan) {
    this.injectPolyfillsNewerThan = injectPolyfillsNewerThan;
  }

  LanguageMode getInjectPolyfillsNewerThan() {
    return this.injectPolyfillsNewerThan;
  }

  /** Sets whether to transpile async functions and generators for AsyncContext. */
  public void setInstrumentAsyncContext(boolean instrumentAsyncContext) {
    this.instrumentAsyncContext = instrumentAsyncContext;
    this.setDefineToBooleanLiteral("$jscomp.INSTRUMENT_ASYNC_CONTEXT", instrumentAsyncContext);
  }

  public boolean getInstrumentAsyncContext() {
    return this.instrumentAsyncContext;
  }

  /**
   * Configures the transpiled output for ES6 classes when targeting ES5.
   *
   * <p>This is needed for transpilation of ES6 classes that extend some unknown superclass, in case
   * that superclass is still a native ES6 class at runtime (not transpiled to ES5).
   *
   * <p>The default is {@link Es6SubclassTranspilation#CONCISE_UNSAFE}, which assumes that if a
   * superclass in an extends clause cannot be statically analyzed, it's safe to assume it is an ES5
   * class.
   *
   * <p>The "safe" behavior relies on {@code $jscomp.construct}, which is our wrapper for {@code
   * Reflect.construct}. This is correct, but has the disadvantage of pulling in more helper
   * utilities into the compiled output and of the transpiled output being slightly larger.
   *
   * <p>TODO: b/36789413 - always enable the "safe" transpilation.
   */
  public enum Es6SubclassTranspilation {
    CONCISE_UNSAFE,
    SAFE_REFLECT_CONSTRUCT
  }

  public void setEs6SubclassTranspilation(Es6SubclassTranspilation es6SubclassTranspilation) {
    this.es6SubclassTranspilation = es6SubclassTranspilation;
  }

  public Es6SubclassTranspilation getEs6SubclassTranspilation() {
    return this.es6SubclassTranspilation;
  }

  /** Sets list of libraries to always inject, even if not needed. */
  public void setForceLibraryInjection(Iterable<String> libraries) {
    this.forceLibraryInjection = ImmutableList.copyOf(libraries);
  }

  ImmutableList<String> getForceLibraryInjectionList() {
    return this.forceLibraryInjection;
  }

  /** Sets the set of libraries to never inject, even if required. */
  public void setPreventLibraryInjection(boolean preventLibraryInjection) {
    this.runtimeLibraryMode =
        preventLibraryInjection ? RuntimeLibraryMode.NO_OP : RuntimeLibraryMode.INJECT;
  }

  public void setRuntimeLibraryMode(RuntimeLibraryMode runtimeLibraryMode) {
    this.runtimeLibraryMode = runtimeLibraryMode;
  }

  RuntimeLibraryMode getRuntimeLibraryMode() {
    return this.runtimeLibraryMode;
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

  /**
   * Both enable and configure conformance checks, if non-null.
   *
   * @deprecated See go/binary-level-conformance-deprecated.
   */
  @Deprecated // See go/binary-level-conformance-deprecated.
  public void setConformanceConfig(ConformanceConfig conformanceConfig) {
    setConformanceConfigs(ImmutableList.of(conformanceConfig));
  }

  /**
   * Both enable and configure conformance checks, if non-null.
   *
   * @deprecated See go/binary-level-conformance-deprecated.
   */
  @Deprecated // See go/binary-level-conformance-deprecated.
  public void setConformanceConfigs(List<ConformanceConfig> configs) {
    this.conformanceConfigs = ImmutableList.copyOf(configs);
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

  boolean shouldUseSizeHeuristicToStopOptimizationLoop() {
    return this.useSizeHeuristicToStopOptimizationLoop;
  }

  public void setMaxOptimizationLoopIterations(int maxIterations) {
    this.optimizationLoopMaxIterations = maxIterations;
  }

  int getMaxOptimizationLoopIterations() {
    return this.optimizationLoopMaxIterations;
  }

  public ChunkOutputType getChunkOutputType() {
    return chunkOutputType;
  }

  public void setChunkOutputType(ChunkOutputType chunkOutputType) {
    this.chunkOutputType = chunkOutputType;
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
        .add("ambiguateProperties", ambiguateProperties)
        .add("angularPass", angularPass)
        .add("assumeClosuresOnlyCaptureReferences", assumeClosuresOnlyCaptureReferences)
        .add("assumeGettersArePure", assumeGettersArePure)
        .add("assumePropertiesAreStaticallyAnalyzable", assumePropertiesAreStaticallyAnalyzable)
        .add("assumeStrictThis", assumeStrictThis())
        .add("browserResolverPrefixReplacements", browserResolverPrefixReplacements)
        .add("checkDeterminism", getCheckDeterminism())
        .add("checkSuspiciousCode", checkSuspiciousCode)
        .add("checkSymbols", checkSymbols)
        .add("checkTypes", checkTypes)
        .add("checksOnly", checksOnly)
        .add("chunksToPrintAfterEachPassRegexList", chunksToPrintAfterEachPassRegexList)
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
        .add("conformanceReportingMode", conformanceReportingMode)
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
        .add("devMode", devMode)
        .add("devirtualizeMethods", devirtualizeMethods)
        .add("disambiguateProperties", disambiguateProperties)
        .add("emitUseStrict", emitUseStrict)
        .add("enableModuleRewriting", enableModuleRewriting)
        .add("environment", getEnvironment())
        .add("errorFormat", errorFormat)
        .add("errorHandler", errorHandler)
        .add("es6ModuleTranspilation", es6ModuleTranspilation)
        .add("es6SubclassTranspilation", es6SubclassTranspilation)
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
        .add("nameGenerator", nameGenerator)
        .add("numParallelThreads", numParallelThreads)
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
        .add("polymerPass", polymerPass)
        .add("preferSingleQuotes", preferSingleQuotes)
        .add("preferStableNames", preferStableNames)
        .add("preserveDetailedSourceInfo", preservesDetailedSourceInfo())
        .add("preserveGoogProvidesAndRequires", preserveClosurePrimitives)
        .add("preserveNonJSDocComments", getPreserveNonJSDocComments())
        .add("preserveTypeAnnotations", preserveTypeAnnotations)
        .add("prettyPrint", prettyPrint)
        .add("printConfig", printConfig)
        .add("printInputDelimiter", printInputDelimiter)
        .add("printSourceAfterEachPass", printSourceAfterEachPass)
        .add("processCommonJSModules", processCommonJSModules)
        .add("productionInstrumentationArrayName", productionInstrumentationArrayName)
        .add("propertiesThatMustDisambiguate", propertiesThatMustDisambiguate)
        .add("propertyRenaming", propertyRenaming)
        .add("propertyRenamingOnlyCompilationMode", propertyRenamingOnlyCompilationMode)
        .add("protectHiddenSideEffects", protectHiddenSideEffects)
        .add("qnameUsesToPrintAfterEachPassRegexList", qnameUsesToPrintAfterEachPassList)
        .add("quoteKeywordProperties", quoteKeywordProperties)
        .add("removeAbstractMethods", removeAbstractMethods)
        .add("removeClosureAsserts", removeClosureAsserts)
        .add("removeJ2clAsserts", removeJ2clAsserts)
        .add("removeUnusedClassProperties", removeUnusedClassProperties)
        .add("removeUnusedLocalVars", removeUnusedLocalVars)
        .add("removeUnusedPrototypeProperties", removeUnusedPrototypeProperties)
        .add("removeUnusedVars", removeUnusedVars)
        .add("renamePrefix", renamePrefix)
        .add("renamePrefixNamespace", renamePrefixNamespace)
        .add(
            "renamePrefixNamespaceAssumeCrossChunkNames",
            renamePrefixNamespaceAssumeCrossChunkNames)
        .add("replaceIdGenerators", replaceIdGenerators)
        .add("replaceMessagesWithChromeI18n", replaceMessagesWithChromeI18n)
        .add("replaceStringsFunctionDescriptions", replaceStringsFunctionDescriptions)
        .add("replaceStringsPlaceholderToken", replaceStringsPlaceholderToken)
        .add("reserveRawExports", reserveRawExports)
        .add("rewriteFunctionExpressions", rewriteFunctionExpressions)
        .add(
            "rewriteGlobalDeclarationsForTryCatchWrapping",
            rewriteGlobalDeclarationsForTryCatchWrapping)
        .add("rewriteModulesBeforeTypechecking", rewriteModulesBeforeTypechecking)
        .add("rewritePolyfills", rewritePolyfills)
        .add("runtimeLibraryMode", runtimeLibraryMode)
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
        .add("unusedImportsToRemove", unusedImportsToRemove)
        .add("useTypesForLocalOptimization", useTypesForLocalOptimization)
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
      return switch (value) {
        case "NONE" -> InstrumentOption.NONE;
        case "LINE" -> InstrumentOption.LINE_ONLY;
        case "BRANCH" -> InstrumentOption.BRANCH_ONLY;
        case "PRODUCTION" -> InstrumentOption.PRODUCTION;
        default -> null;
      };
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

    /** ECMAScript standard approved in 2022. */
    ECMASCRIPT_2022,

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
      return switch (this) {
        case ECMASCRIPT3, ECMASCRIPT5 -> false;
        default -> true;
      };
    }

    /** Returns a list of valid names used to select a `LanguageMode` on the command line. */
    public static List<String> validCommandLineNames() {
      List<String> names = new ArrayList<>();
      for (LanguageMode mode : EnumSet.allOf(LanguageMode.class)) {
        if (mode != UNSUPPORTED) {
          names.add(mode.name());
          if (mode.name().startsWith("ECMASCRIPT")) {
            names.add(mode.name().replaceFirst("ECMASCRIPT", "ES"));
          }
        }
      }
      names.add("ECMASCRIPT6");
      names.add("ES6");
      names.add("ECMASCRIPT6_STRICT");
      names.add("ES6_STRICT");
      return names;
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
      return switch (this) {
        case ECMASCRIPT3 -> FeatureSet.ES3;
        case ECMASCRIPT5, ECMASCRIPT5_STRICT -> FeatureSet.ES5;
        case ECMASCRIPT_2015 -> FeatureSet.ES2015_MODULES;
        case ECMASCRIPT_2016 -> FeatureSet.ES2016_MODULES;
        case ECMASCRIPT_2017 -> FeatureSet.ES2017_MODULES;
        case ECMASCRIPT_2018 -> FeatureSet.ES2018_MODULES;
        case ECMASCRIPT_2019 -> FeatureSet.ES2019_MODULES;
        case ECMASCRIPT_2020 -> FeatureSet.ES2020_MODULES;
        case ECMASCRIPT_2021 -> FeatureSet.ES2021_MODULES;
        case ECMASCRIPT_2022 -> FeatureSet.ES2022_MODULES;
        case ECMASCRIPT_NEXT -> FeatureSet.ES_NEXT;
        case NO_TRANSPILE, UNSTABLE -> FeatureSet.ES_UNSTABLE;
        case UNSUPPORTED -> FeatureSet.ES_UNSUPPORTED;
        case STABLE ->
            throw new UnsupportedOperationException(
                "STABLE has different feature sets for language in and out. "
                    + "Use STABLE_IN or STABLE_OUT.");
      };
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

  /** What segment of the compilation to run. */
  public static enum SegmentOfCompilationToRun {
    ENTIRE_COMPILATION, // all JSC compilation in a single action.
    CHECKS, // all checks
    OPTIMIZATIONS_FIRST_HALF, // first half of optimizations
    OPTIMIZATIONS_SECOND_HALF, // second half of optimizations
    OPTIMIZATIONS, // all optimizations
    OPTIMIZATIONS_AND_FINALIZATIONS, // all optimizations and finalizations in one action
    FINALIZATIONS, // all finalizations
  }

  /** A mode enum used to indicate the alias strings policy for the AliasStrings pass */
  public static enum AliasStringsMode {
    NONE, // Do not alias string literals.
    LARGE, // Alias all string literals with a length greater than 100 characters.
    ALL, // Alias all string literals where it may improve code size
    ALL_AGGRESSIVE // Alias all string regardless of code size
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

  public ImmutableSet<Character> getPropertyReservedNamingFirstChars() {
    if (polymerPass) {
      return POLYMER_PROPERTY_RESERVED_FIRST_CHARS;
    } else if (angularPass) {
      return ANGULAR_PROPERTY_RESERVED_FIRST_CHARS;
    }
    return ImmutableSet.of();
  }

  public ImmutableSet<Character> getPropertyReservedNamingNonFirstChars() {
    if (polymerPass) {
      return POLYMER_PROPERTY_RESERVED_NON_FIRST_CHARS;
    }
    return ImmutableSet.of();
  }

  boolean shouldOptimize() {
    return !skipNonTranspilationPasses
        && !checksOnly
        && !shouldGenerateTypedExterns()
        && !instrumentForCoverageOnly;
  }
}
