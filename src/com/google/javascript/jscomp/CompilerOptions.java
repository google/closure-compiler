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

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Ascii;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.parsing.Config;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.SourcePosition;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compiler options
 * @author nicksantos@google.com (Nick Santos)
 */
public class CompilerOptions {
  // The number of characters after which we insert a line break in the code
  static final int DEFAULT_LINE_LENGTH_THRESHOLD = 500;

  /**
   * A common enum for compiler passes that can run either globally or locally.
   */
  public enum Reach {
    ALL,
    LOCAL_ONLY,
    NONE
  }

  // TODO(nicksantos): All public properties of this class should be made
  // package-private, and have a public setter.

  /**
   * Should the compiled output start with "'use strict';"?
   *
   * <p>Ignored for non-strict output language modes.
   */
  private boolean emitUseStrict = true;

  /**
   * The JavaScript language version accepted.
   */
  private LanguageMode languageIn;

  /**
   * The JavaScript language version that should be produced.
   */
  private LanguageMode languageOut;

  /**
   * The builtin set of externs to be used
   */
  private Environment environment;

  /**
   * If true, don't transpile ES6 to ES3.
   *  WARNING: Enabling this option will likely cause the compiler to crash
   *     or produce incorrect output.
   */
  boolean skipTranspilationAndCrash = false;

  /**
   * Allow disabling ES6 to ES3 transpilation.
   */
  public void setSkipTranspilationAndCrash(boolean value) {
    skipTranspilationAndCrash = value;
  }

  /**
   * Sets the input sourcemap files, indexed by the JS files they refer to.
   *
   * @param inputSourceMaps the collection of input sourcemap files
   */
  public void setInputSourceMaps(final ImmutableMap<String, SourceMapInput> inputSourceMaps) {
    this.inputSourceMaps = inputSourceMaps;
  }

  /**
   * Whether to infer consts. This should not be configurable by
   * external clients. This is a transitional flag for a new type
   * of const analysis.
   *
   * TODO(nicksantos): Remove this option.
   */
  boolean inferConsts = true;

  // TODO(tbreisacher): Remove this method after ctemplate issues are solved.
  public void setInferConst(boolean value) {
    inferConsts = value;
  }

  /**
   * Whether the compiler should assume that a function's "this" value
   * never needs coercion (for example in non-strict "null" or "undefined" will
   * be coerced to the global "this" and primitives to objects).
   */
  private boolean assumeStrictThis;

  private boolean allowHotswapReplaceScript = false;
  private boolean preserveDetailedSourceInfo = false;
  private boolean continueAfterErrors = false;

  public enum IncrementalCheckMode {
    /** Normal mode */
    OFF,

    /**
     * The compiler should generate an output file that represents the type-only interface
     * of the code being compiled.  This is useful for incremental type checking.
     */
    GENERATE_IJS,

    /**
     * The compiler should check type-only interface definitions generated above.
     */
    CHECK_IJS,
  }

  private IncrementalCheckMode incrementalCheckMode = IncrementalCheckMode.OFF;

  public void setIncrementalChecks(IncrementalCheckMode value) {
    incrementalCheckMode = value;
    switch (value) {
      case OFF:
        break;
      case GENERATE_IJS:
        setPreserveTypeAnnotations(true);
        setOutputJs(OutputJs.NORMAL);
        break;
      case CHECK_IJS:
        setChecksOnly(true);
        setOutputJs(OutputJs.SENTINEL);
        break;
    }
  }

  public boolean shouldGenerateTypedExterns() {
    return incrementalCheckMode == IncrementalCheckMode.GENERATE_IJS;
  }

  public boolean allowIjsInputs() {
    return incrementalCheckMode != IncrementalCheckMode.OFF;
  }

  public boolean allowUnfulfilledForwardDeclarations() {
    return incrementalCheckMode == IncrementalCheckMode.OFF;
  }

  private Config.JsDocParsing parseJsDocDocumentation = Config.JsDocParsing.TYPES_ONLY;

  /**
   * Even if checkTypes is disabled, clients such as IDEs might want to still infer types.
   */
  boolean inferTypes;

  private boolean useNewTypeInference;

  /**
   * Relevant only when {@link #useNewTypeInference} is true, where we normally disable OTI errors.
   * If you want both NTI and OTI errors in this case, set to true.
   * E.g. if using using a warnings guard to filter NTI or OTI warnings in new or legacy code,
   * respectively.
   * This will be removed when NTI entirely replaces OTI.
   */
  boolean reportOTIErrorsUnderNTI = false;

  /**
   * Configures the compiler to skip as many passes as possible.
   * If transpilation is requested, it will be run, but all others passes will be skipped.
   */
  boolean skipNonTranspilationPasses;

  /**
   * Configures the compiler to run expensive sanity checks after
   * every pass. Only intended for internal development.
   */
  DevMode devMode;

  /**
   * Configures the compiler to log a hash code of the AST after
   * every pass. Only intended for internal development.
   */
  private boolean checkDeterminism;

  //--------------------------------
  // Input Options
  //--------------------------------

  DependencyOptions dependencyOptions = new DependencyOptions();

  // TODO(tbreisacher): When this is false, report an error if there's a goog.provide
  // in an externs file.
  boolean allowGoogProvideInExterns() {
    return allowIjsInputs();
  }

  /** Returns localized replacement for MSG_* variables */
  public MessageBundle messageBundle = null;

  //--------------------------------
  // Checks
  //--------------------------------

  /** Checks that all symbols are defined */
  // TODO(tbreisacher): Remove this and deprecate the corresponding setter.
  public boolean checkSymbols;

  /** Checks for suspicious statements that have no effect */
  public boolean checkSuspiciousCode;

  /** Checks types on expressions */
  public boolean checkTypes;

  public CheckLevel checkGlobalNamesLevel;

  /**
   * Checks the integrity of references to qualified global names.
   * (e.g. "a.b")
   */
  public void setCheckGlobalNamesLevel(CheckLevel level) {
    checkGlobalNamesLevel = level;
  }

  @Deprecated
  public CheckLevel brokenClosureRequiresLevel;

  /**
   * Sets the check level for bad Closure require calls.
   * Do not use; this should always be an error.
   */
  @Deprecated
  public void setBrokenClosureRequiresLevel(CheckLevel level) {
    brokenClosureRequiresLevel = level;
  }

  public CheckLevel checkGlobalThisLevel;

  /**
   * Checks for certain uses of the {@code this} keyword that are considered
   * unsafe because they are likely to reference the global {@code this}
   * object unintentionally.
   *
   * If this is off, but collapseProperties is on, then the compiler will
   * usually ignore you and run this check anyways.
   */
  public void setCheckGlobalThisLevel(CheckLevel level) {
    this.checkGlobalThisLevel = level;
  }

  public CheckLevel checkMissingGetCssNameLevel;

  /**
   * Checks that certain string literals only appear in strings used as
   * goog.getCssName arguments.
   */
  public void setCheckMissingGetCssNameLevel(CheckLevel level) {
    this.checkMissingGetCssNameLevel = level;
  }

  /**
   * Regex of string literals that may only appear in goog.getCssName arguments.
   */
  public String checkMissingGetCssNameBlacklist;

  /**
   * A set of extra annotation names which are accepted and silently ignored
   * when encountered in a source file. Defaults to null which has the same
   * effect as specifying an empty set.
   */
  Set<String> extraAnnotationNames;

  /**
   * Policies to determine the disposal checking level.
   */
  public enum DisposalCheckingPolicy {
    /**
     * Don't check any disposal.
     */
    OFF,

    /**
     * Default/conservative disposal checking.
     */
    ON,

    /**
     * Aggressive disposal checking.
     */
    AGGRESSIVE,
  }

  /**
   * Check for patterns that are known to cause memory leaks.
   */
  DisposalCheckingPolicy checkEventfulObjectDisposalPolicy;

  public void setCheckEventfulObjectDisposalPolicy(DisposalCheckingPolicy policy) {
    this.checkEventfulObjectDisposalPolicy = policy;

    // The CheckEventfulObjectDisposal pass requires types so enable inferring types if
    // this pass is enabled.
    if (policy != DisposalCheckingPolicy.OFF) {
      this.inferTypes = true;
    }
  }
  public DisposalCheckingPolicy getCheckEventfulObjectDisposalPolicy() {
    return checkEventfulObjectDisposalPolicy;
  }

  /**
   * Used for projects that are not well maintained, but are still used.
   * Does not allow promoting warnings to errors, and disables some potentially
   * risky optimizations.
   */
  boolean legacyCodeCompile = false;

  public boolean getLegacyCodeCompile() {
    return this.legacyCodeCompile;
  }

  public void setLegacyCodeCompile(boolean legacy) {
    this.legacyCodeCompile = legacy;
  }

  //--------------------------------
  // Optimizations
  //--------------------------------

  /** Prefer commas over semicolons when doing statement fusion */
  boolean aggressiveFusion;

  /** Folds constants (e.g. (2 + 3) to 5) */
  public boolean foldConstants;

  /** Remove assignments to values that can not be referenced */
  public boolean deadAssignmentElimination;

  /** Inlines constants (symbols that are all CAPS) */
  public boolean inlineConstantVars;

  /** Inlines global functions */
  public boolean inlineFunctions;

  /**
   * For projects that want to avoid the creation of giant functions after
   * inlining.
   */
  int maxFunctionSizeAfterInlining;
  static final int UNLIMITED_FUN_SIZE_AFTER_INLINING = -1;

  /** Inlines functions defined in local scopes */
  public boolean inlineLocalFunctions;

  /** More aggressive function inlining */
  boolean assumeClosuresOnlyCaptureReferences;

  /** Inlines properties */
  private boolean inlineProperties;

  /** Move code to a deeper module */
  public boolean crossModuleCodeMotion;

  /**
   * Don't generate stub functions when moving methods deeper.
   *
   * Note, switching on this option may break existing code that depends on
   * enumerating prototype methods for mixin behavior, such as goog.mixin or
   * goog.object.extend, since the prototype assignments will be removed from
   * the parent module and moved to a later module.
   **/
  boolean crossModuleCodeMotionNoStubMethods;

  /**
   * Whether when module B depends on module A and module B declares a symbol,
   * this symbol can be seen in A after B has been loaded. This is often true,
   * but may not be true when loading code using nested eval.
   */
  boolean parentModuleCanSeeSymbolsDeclaredInChildren;

  /** Merge two variables together as one. */
  public boolean coalesceVariableNames;

  /** Move methods to a deeper module */
  public boolean crossModuleMethodMotion;

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

  /** Removes code associated with unused global names */
  boolean extraSmartNameRemoval;

  /** Removes code that will never execute */
  public boolean removeDeadCode;

  public enum ExtractPrototypeMemberDeclarationsMode {
    OFF,
    USE_GLOBAL_TEMP,
    USE_IIFE
  }

  /** Extracts common prototype member declarations */
  ExtractPrototypeMemberDeclarationsMode extractPrototypeMemberDeclarations;

  /** Removes unused member prototypes */
  public boolean removeUnusedPrototypeProperties;

  /** Tells AnalyzePrototypeProperties it can remove externed props. */
  public boolean removeUnusedPrototypePropertiesInExterns;

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

  /**
   * Collapses anonymous function declarations into named function
   * declarations
   */
  public boolean collapseAnonymousFunctions;

  /**
   * If set to a non-empty set, those strings literals will be aliased to a
   * single global instance per string, to avoid creating more objects than
   * necessary.
   */
  public Set<String> aliasableStrings;

  /**
   * A blacklist in the form of a regular expression to block strings that
   * contains certain words from being aliased.
   * If the value is the empty string, no words are blacklisted.
   */
  public String aliasStringsBlacklist;

  /**
   * Aliases all string literals to global instances, to avoid creating more
   * objects than necessary (if true, overrides any set of strings passed in
   * to aliasableStrings)
   */
  public boolean aliasAllStrings;

  /** Print string usage as part of the compilation log. */
  boolean outputJsStringUsage;

  /** Converts quoted property accesses to dot syntax (a['b'] &rarr; a.b) */
  public boolean convertToDottedProperties;

  /** Reduces the size of common function expressions. */
  public boolean rewriteFunctionExpressions;

  /**
   * Remove unused and constant parameters.
   */
  public boolean optimizeParameters;

  /**
   * Remove unused return values.
   */
  public boolean optimizeReturns;

  /**
   * Remove unused parameters from call sites.
   */
  public boolean optimizeCalls;

  /**
   * Provide formal names for elements of arguments array.
   */
  public boolean optimizeArgumentsArray;

  /** Chains calls to functions that return this. */
  boolean chainCalls;

  /** Use type information to enable additional optimization opportunities. */
  boolean useTypesForLocalOptimization;

  //--------------------------------
  // Renaming
  //--------------------------------

  /** Controls which variables get renamed. */
  public VariableRenamingPolicy variableRenaming;

  /** Controls which properties get renamed. */
  PropertyRenamingPolicy propertyRenaming;

  /** Controls label renaming. */
  public boolean labelRenaming;

  /** Reserve property names on the global this object. */
  public boolean reserveRawExports;

  /** Should shadow variable names in outer scope. */
  boolean shadowVariables;

  /**
   * Use a renaming heuristic with better stability across source
   * changes.  With this option each symbol is more likely to receive
   * the same name between builds.  The cost may be a slight increase
   * in code size.
   */
  boolean preferStableNames;

  /**
   * Generate pseudo names for variables and properties for debugging purposes.
   */
  public boolean generatePseudoNames;

  /** Specifies a prefix for all globals */
  public String renamePrefix;

  /**
   * Specifies the name of an object that will be used to store all non-extern
   * globals.
   */
  public String renamePrefixNamespace;

  /**
   * Used by tests of the RescopeGlobalSymbols pass to avoid having declare 2
   * modules in simple cases.
   */
  boolean renamePrefixNamespaceAssumeCrossModuleNames = false;

  void setRenamePrefixNamespaceAssumeCrossModuleNames(boolean assume) {
    renamePrefixNamespaceAssumeCrossModuleNames = assume;
  }

  /** Flattens multi-level property names (e.g. a$b = x) */
  public boolean collapseProperties;

  /** Split object literals into individual variables when possible. */
  boolean collapseObjectLiterals;

  public void setCollapseObjectLiterals(boolean enabled) {
    collapseObjectLiterals = enabled;
  }

  /**
   * Devirtualize prototype method by rewriting them to be static calls that
   * take the this pointer as their first argument
   */
  public boolean devirtualizePrototypeMethods;

  /**
   * Use @nosideeffects annotations, function bodies and name graph
   * to determine if calls have side effects.  Requires --check_types.
   */
  public boolean computeFunctionSideEffects;

  /**
   * Where to save debug report for compute function side effects.
   */
  String debugFunctionSideEffectsPath;

  /**
   * Rename private properties to disambiguate between unrelated fields based on
   * the coding convention.
   */
  boolean disambiguatePrivateProperties;

  /**
   * Rename properties to disambiguate between unrelated fields based on
   * type information.
   */
  private boolean disambiguateProperties;

  /** Rename unrelated properties to the same name to reduce code size. */
  private boolean ambiguateProperties;

  /** Input sourcemap files, indexed by the JS files they refer to */
  ImmutableMap<String, SourceMapInput> inputSourceMaps;

  /** Give anonymous functions names for easier debugging */
  public AnonymousFunctionNamingPolicy anonymousFunctionNaming;

  /** Input anonymous function renaming map. */
  VariableMap inputAnonymousFunctionNamingMap;

  /**
   * Input variable renaming map.
   * <p>During renaming, the compiler uses this map and the inputPropertyMap to
   * try to preserve renaming mappings from a previous compilation.
   * The application is delta encoding: keeping the diff between consecutive
   * versions of one's code small.
   * The compiler does NOT guarantee to respect these maps; projects should not
   * use these maps to prevent renaming or to select particular names.
   * Point questioners to this post:
   * http://closuretools.blogspot.com/2011/01/property-by-any-other-name-part-3.html
   */
  VariableMap inputVariableMap;

  /** Input property renaming map. */
  VariableMap inputPropertyMap;

  /** Whether to export test functions. */
  public boolean exportTestFunctions;

  /** Whether to declare globals declared in externs as properties on window */
  boolean declaredGlobalExternsOnWindow;

  /** Shared name generator */
  NameGenerator nameGenerator;

  public void setNameGenerator(NameGenerator nameGenerator) {
    this.nameGenerator = nameGenerator;
  }

  //--------------------------------
  // Special-purpose alterations
  //--------------------------------

  /**
   * Replace UI strings with chrome.i18n.getMessage calls.
   * Used by Chrome extensions/apps.
   */
  boolean replaceMessagesWithChromeI18n;
  String tcProjectId;

  public void setReplaceMessagesWithChromeI18n(
      boolean replaceMessagesWithChromeI18n,
      String tcProjectId) {
    if (replaceMessagesWithChromeI18n
        && messageBundle != null
        && !(messageBundle instanceof EmptyMessageBundle)) {
    throw new RuntimeException("When replacing messages with"
          + " chrome.i18n.getMessage, a message bundle should not be specified.");
    }

    this.replaceMessagesWithChromeI18n = replaceMessagesWithChromeI18n;
    this.tcProjectId = tcProjectId;
  }

  /** Inserts run-time type assertions for debugging. */
  boolean runtimeTypeCheck;

  /**
   * A JS function to be used for logging run-time type assertion
   * failures. It will be passed the warning as a string and the
   * faulty expression as arguments.
   */
  String runtimeTypeCheckLogFunction;

  /** A CodingConvention to use during the compile. */
  private CodingConvention codingConvention;

  public String syntheticBlockStartMarker;

  public String syntheticBlockEndMarker;

  /** Compiling locale */
  public String locale;

  /** Sets the special "COMPILED" value to true */
  public boolean markAsCompiled;

  /** Processes goog.provide() and goog.require() calls */
  public boolean closurePass;

  /** Do not strip goog.provide()/goog.require() calls from the code. */
  private boolean preserveGoogProvidesAndRequires;

  /** Processes jQuery aliases */
  public boolean jqueryPass;

  /** Processes AngularJS-specific annotations */
  boolean angularPass;

  /** Processes Polymer calls */
  boolean polymerPass;

  /** Processes cr.* functions */
  boolean chromePass;

  /** Processes the output of the Dart Dev Compiler */
  boolean dartPass;

  /** Processes the output of J2CL */
  J2clPassMode j2clPassMode;

  /** Remove goog.abstractMethod assignments and @abstract methods. */
  boolean removeAbstractMethods;

  /** Remove methods that only make a super call without changing the arguments. */
  boolean removeSuperMethods;

  /** Remove goog.asserts calls. */
  boolean removeClosureAsserts;

  /** Gather CSS names (requires closurePass) */
  public boolean gatherCssNames;

  /** Names of types to strip */
  public Set<String> stripTypes;

  /** Name suffixes that determine which variables and properties to strip */
  public Set<String> stripNameSuffixes;

  /** Name prefixes that determine which variables and properties to strip */
  public Set<String> stripNamePrefixes;

  /** Qualified type name prefixes that determine which types to strip */
  public Set<String> stripTypePrefixes;

  /** Custom passes */
  protected transient
      Multimap<CustomPassExecutionTime, CompilerPass> customPasses;

  /** Mark no side effect calls */
  public boolean markNoSideEffectCalls;

  /** Replacements for @defines. Will be Boolean, Numbers, or Strings */
  private Map<String, Object> defineReplacements;

  /** What kind of processing to do for goog.tweak functions. */
  private TweakProcessing tweakProcessing;

  /** Replacements for tweaks. Will be Boolean, Numbers, or Strings */
  private Map<String, Object> tweakReplacements;

  /** Move top-level function declarations to the top */
  public boolean moveFunctionDeclarations;

  /** Instrumentation template to use with #recordFunctionInformation */
  public Instrumentation instrumentationTemplate;

  String appNameStr;

  /**
   * App identifier string for use by the instrumentation template's
   * app_name_setter. @see #instrumentationTemplate
   */
  public void setAppNameStr(String appNameStr) {
    this.appNameStr = appNameStr;
  }

  /** Record function information */
  public boolean recordFunctionInformation;

  boolean checksOnly;

  static enum OutputJs {
    // Don't output anything.
    NONE,
    // Output a "sentinel" file containing just a comment.
    SENTINEL,
    // Output the compiled JS.
    NORMAL,
  }
  OutputJs outputJs;

  public boolean generateExports;

  // TODO(dimvar): generate-exports should always run after typechecking.
  // If it runs before, it adds a bunch of properties to Object, which masks
  // many type warnings. Cleanup all clients and remove this.
  boolean generateExportsAfterTypeChecking;

  boolean exportLocalPropertyDefinitions;

  /** Map used in the renaming of CSS class names. */
  public CssRenamingMap cssRenamingMap;

  /** Whitelist used in the renaming of CSS class names. */
  Set<String> cssRenamingWhitelist;

  /** Process instances of goog.testing.ObjectPropertyString. */
  boolean processObjectPropertyString;

  /** Replace id generators */
  boolean replaceIdGenerators = true;  // true by default for legacy reasons.

  /** Id generators to replace. */
  ImmutableMap<String, RenamingMap> idGenerators;

  /** Hash function to use for xid generation. */
  Xid.HashFunction xidHashFunction;

  /**
   * A previous map of ids (serialized to a string by a previous compile).
   * This will be used as a hint during the ReplaceIdGenerators pass, which
   * will attempt to reuse the same ids.
   */
  String idGeneratorsMapSerialized;

  /** Configuration strings */
  List<String> replaceStringsFunctionDescriptions;

  String replaceStringsPlaceholderToken;
  // A list of strings that should not be used as replacements
  Set<String> replaceStringsReservedStrings;
  // A previous map of replacements to strings.
  VariableMap replaceStringsInputMap;

  /** List of properties that we report invalidation errors for. */
  Map<String, CheckLevel> propertyInvalidationErrors;

  /** Transform AMD to CommonJS modules. */
  boolean transformAMDToCJSModules = false;

  /** Rewrite CommonJS modules so that they can be concatenated together. */
  boolean processCommonJSModules = false;

  /** CommonJS module prefix. */
  List<String> moduleRoots = ImmutableList.of(ModuleLoader.DEFAULT_FILENAME_PREFIX);

  /** Rewrite polyfills. */
  boolean rewritePolyfills = false;

  /** Runtime libraries to always inject. */
  List<String> forceLibraryInjection = ImmutableList.of();

  /** Runtime libraries to never inject. */
  boolean preventLibraryInjection = false;


  //--------------------------------
  // Output options
  //--------------------------------

  /** Do not strip closure-style type annotations from code. */
  public boolean preserveTypeAnnotations;

  /** Output in pretty indented format */
  public boolean prettyPrint;

  /** Line break the output a bit more aggressively */
  public boolean lineBreak;

  /** Prefer line breaks at end of file */
  public boolean preferLineBreakAtEndOfFile;

  /** Prints a separator comment before each JS script */
  public boolean printInputDelimiter;

  /** The string to use as the separator for printInputDelimiter */
  public String inputDelimiter = "// Input %num%";

  /** Whether to write keyword properties as foo['class'] instead of foo.class; needed for IE8. */
  boolean quoteKeywordProperties;

  boolean preferSingleQuotes;

  /**
   * Normally, when there are an equal number of single and double quotes
   * in a string, the compiler will use double quotes. Set this to true
   * to prefer single quotes.
   */
  public void setPreferSingleQuotes(boolean enabled) {
    this.preferSingleQuotes = enabled;
  }

  boolean trustedStrings;

  /**
   * Some people want to put arbitrary user input into strings, which are then
   * run through the compiler. These scripts are then put into HTML.
   * By default, we assume strings are untrusted. If the compiler is run
   * from the command-line, we assume that strings are trusted.
   */
  public void setTrustedStrings(boolean yes) {
    trustedStrings = yes;
  }

  // Should only be used when debugging compiler bugs.
  boolean printSourceAfterEachPass;
  // Used to narrow down the printed source when overall input size is large. If this is empty,
  // the entire source is printed.
  List<String> filesToPrintAfterEachPass = ImmutableList.of();

  public void setPrintSourceAfterEachPass(boolean printSource) {
    this.printSourceAfterEachPass = printSource;
  }

  public void setFilesToPrintAfterEachPass(List<String> filenames) {
    this.filesToPrintAfterEachPass = filenames;
  }

  String reportPath;

  /** Where to save a report of global name usage */
  public void setReportPath(String reportPath) {
    this.reportPath = reportPath;
  }

  private TracerMode tracer;

  public TracerMode getTracerMode() {
    return tracer;
  }

  public void setTracerMode(TracerMode mode) {
    this.tracer = mode;
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

  //--------------------------------
  // Special Output Options
  //--------------------------------

  /**
   * Whether the exports should be made available via {@link Result} after
   * compilation. This is implicitly true if {@link #externExportsPath} is set.
   */
  private boolean externExports;

  /** The output path for the created externs file. */
  String externExportsPath;

  //--------------------------------
  // Debugging Options
  //--------------------------------

  /** The output path for the source map. */
  public String sourceMapOutputPath;

  /** The detail level for the generated source map. */
  public SourceMap.DetailLevel sourceMapDetailLevel =
      SourceMap.DetailLevel.ALL;

  /** The source map file format */
  public SourceMap.Format sourceMapFormat =
      SourceMap.Format.DEFAULT;

  /**
   * Whether to parse inline source maps.
   */
  boolean parseInlineSourceMaps = true;

  /**
   * Whether to apply input source maps to the output, i.e. map back to original inputs from
   * input files that have source maps applied to them.
   */
  boolean applyInputSourceMaps = false;

  public List<SourceMap.LocationMapping> sourceMapLocationMappings =
      Collections.emptyList();

  /**
   * Whether to include full file contents in the source map.
   */
  boolean sourceMapIncludeSourcesContent = false;

  /**
   * Whether to return strings logged with AbstractCompiler#addToDebugLog
   * in the compiler's Result.
   */
  boolean useDebugLog;

  /**
   * Charset to use when generating code.  If null, then output ASCII.
   */
  Charset outputCharset;

  /**
   * Transitional option.
   */
  boolean enforceAccessControlCodingConventions;

  /**
   * When set, assume that apparently side-effect free code is meaningful.
   */
  private boolean protectHiddenSideEffects;

  /**
   * When enabled, assume that apparently side-effect free code is meaningful.
   */
  public void setProtectHiddenSideEffects(boolean enable) {
    this.protectHiddenSideEffects = enable;
  }

  /**
   * Whether or not the compiler should wrap apparently side-effect free code
   * to prevent it from being removed
   */
  public boolean shouldProtectHiddenSideEffects() {
    return protectHiddenSideEffects && !checksOnly && !allowHotswapReplaceScript;
  }

  /**
   * Data holder Alias Transformation information accumulated during a compile.
   */
  private transient AliasTransformationHandler aliasHandler;

  /**
   * Handler for compiler warnings and errors.
   */
  transient ErrorHandler errorHandler;

  /**
   * Instrument code for the purpose of collecting coverage data.
   */
  public boolean instrumentForCoverage;

  /** Instrument branch coverage data - valid only if instrumentForCoverage is True */
  public boolean instrumentBranchCoverage;

  String instrumentationTemplateFile;

  /** List of conformance configs to use in CheckConformance */
  private ImmutableList<ConformanceConfig> conformanceConfigs = ImmutableList.of();

  /**
   * For use in {@value CompilationLevel#WHITESPACE_ONLY} mode, when using goog.module.
   */
  boolean wrapGoogModulesForWhitespaceOnly = true;

  public void setWrapGoogModulesForWhitespaceOnly(boolean enable) {
    this.wrapGoogModulesForWhitespaceOnly = enable;
  }

  /**
   * Print all configuration options to stderr after the compiler is initialized.
   */
  boolean printConfig = false;

  /**
   * Are the input files written for strict mode?
   *
   * <p>Ignored for language modes that do not support strict mode.
   */
  private boolean isStrictModeInput = true;

  /** Which algorithm to use for locating ES6 and CommonJS modules */
  ModuleLoader.ResolutionMode moduleResolutionMode;

  /**
   * Should the compiler print its configuration options to stderr when they are initialized?
   *
   * <p>Default {@code false}.
   */
  public void setPrintConfig(boolean printConfig) {
    this.printConfig = printConfig;
  }

  /**
   * Initializes compiler options. All options are disabled by default.
   *
   * Command-line frontends to the compiler should set these properties
   * like a builder.
   */
  public CompilerOptions() {
    // Accepted language
    languageIn = LanguageMode.ECMASCRIPT3;
    languageOut = LanguageMode.NO_TRANSPILE;

    // Which environment to use
    environment = Environment.BROWSER;

    // Modules
    moduleResolutionMode = ModuleLoader.ResolutionMode.LEGACY;

    // Checks
    skipNonTranspilationPasses = false;
    devMode = DevMode.OFF;
    checkDeterminism = false;
    checkSymbols = false;
    checkSuspiciousCode = false;
    checkTypes = false;
    checkGlobalNamesLevel = CheckLevel.OFF;
    brokenClosureRequiresLevel = CheckLevel.ERROR;
    checkGlobalThisLevel = CheckLevel.OFF;
    checkMissingGetCssNameLevel = CheckLevel.OFF;
    checkMissingGetCssNameBlacklist = null;
    computeFunctionSideEffects = false;
    chainCalls = false;
    extraAnnotationNames = null;
    checkEventfulObjectDisposalPolicy = DisposalCheckingPolicy.OFF;

    // Optimizations
    foldConstants = false;
    coalesceVariableNames = false;
    deadAssignmentElimination = false;
    inlineConstantVars = false;
    inlineFunctions = false;
    maxFunctionSizeAfterInlining = UNLIMITED_FUN_SIZE_AFTER_INLINING;
    inlineLocalFunctions = false;
    assumeStrictThis = false;
    assumeClosuresOnlyCaptureReferences = false;
    inlineProperties = false;
    crossModuleCodeMotion = false;
    parentModuleCanSeeSymbolsDeclaredInChildren = false;
    crossModuleMethodMotion = false;
    inlineGetters = false;
    inlineVariables = false;
    inlineLocalVariables = false;
    smartNameRemoval = false;
    extraSmartNameRemoval = false;
    removeDeadCode = false;
    extractPrototypeMemberDeclarations =
        ExtractPrototypeMemberDeclarationsMode.OFF;
    removeUnusedPrototypeProperties = false;
    removeUnusedPrototypePropertiesInExterns = false;
    removeUnusedClassProperties = false;
    removeUnusedConstructorProperties = false;
    removeUnusedVars = false;
    removeUnusedLocalVars = false;
    collapseVariableDeclarations = false;
    collapseAnonymousFunctions = false;
    aliasableStrings = Collections.emptySet();
    aliasStringsBlacklist = "";
    aliasAllStrings = false;
    outputJsStringUsage = false;
    convertToDottedProperties = false;
    rewriteFunctionExpressions = false;
    optimizeParameters = false;
    optimizeReturns = false;

    // Renaming
    variableRenaming = VariableRenamingPolicy.OFF;
    propertyRenaming = PropertyRenamingPolicy.OFF;
    labelRenaming = false;
    generatePseudoNames = false;
    shadowVariables = false;
    preferStableNames = false;
    renamePrefix = null;
    collapseProperties = false;
    collapseObjectLiterals = false;
    devirtualizePrototypeMethods = false;
    disambiguateProperties = false;
    ambiguateProperties = false;
    anonymousFunctionNaming = AnonymousFunctionNamingPolicy.OFF;
    exportTestFunctions = false;
    declaredGlobalExternsOnWindow = true;
    nameGenerator = new DefaultNameGenerator();

    // Alterations
    runtimeTypeCheck = false;
    runtimeTypeCheckLogFunction = null;
    syntheticBlockStartMarker = null;
    syntheticBlockEndMarker = null;
    locale = null;
    markAsCompiled = false;
    closurePass = false;
    preserveGoogProvidesAndRequires = false;
    jqueryPass = false;
    angularPass = false;
    polymerPass = false;
    dartPass = false;
    j2clPassMode = J2clPassMode.OFF;
    removeAbstractMethods = false;
    removeSuperMethods = false;
    removeClosureAsserts = false;
    stripTypes = Collections.emptySet();
    stripNameSuffixes = Collections.emptySet();
    stripNamePrefixes = Collections.emptySet();
    stripTypePrefixes = Collections.emptySet();
    customPasses = null;
    markNoSideEffectCalls = false;
    defineReplacements = new HashMap<>();
    tweakProcessing = TweakProcessing.OFF;
    tweakReplacements = new HashMap<>();
    moveFunctionDeclarations = false;
    appNameStr = "";
    recordFunctionInformation = false;
    checksOnly = false;
    outputJs = OutputJs.NORMAL;
    generateExports = false;
    generateExportsAfterTypeChecking = true;
    exportLocalPropertyDefinitions = false;
    cssRenamingMap = null;
    cssRenamingWhitelist = null;
    processObjectPropertyString = false;
    idGenerators = ImmutableMap.of();
    replaceStringsFunctionDescriptions = Collections.emptyList();
    replaceStringsPlaceholderToken = "";
    replaceStringsReservedStrings = Collections.emptySet();
    propertyInvalidationErrors = new HashMap<>();
    inputSourceMaps = ImmutableMap.of();

    // Instrumentation
    instrumentationTemplate = null;  // instrument functions
    instrumentForCoverage = false;  // instrument lines
    instrumentBranchCoverage = false; // instrument branches
    instrumentationTemplateFile = "";

    // Output
    preserveTypeAnnotations = false;
    printInputDelimiter = false;
    prettyPrint = false;
    lineBreak = false;
    preferLineBreakAtEndOfFile = false;
    reportPath = null;
    tracer = TracerMode.OFF;
    colorizeErrorOutput = false;
    errorFormat = ErrorFormat.SINGLELINE;
    debugFunctionSideEffectsPath = null;
    externExports = false;

    // Debugging
    aliasHandler = NULL_ALIAS_TRANSFORMATION_HANDLER;
    errorHandler = null;
    printSourceAfterEachPass = false;
    useDebugLog = false;
  }

  /**
   * @return Whether to attempt to remove unused class properties
   */
  public boolean isRemoveUnusedClassProperties() {
    return removeUnusedClassProperties;
  }

  /**
   * @param removeUnusedClassProperties Whether to attempt to remove
   *      unused class properties
   */
  public void setRemoveUnusedClassProperties(boolean removeUnusedClassProperties) {
    this.removeUnusedClassProperties = removeUnusedClassProperties;
  }

  /**
   * @return Whether to attempt to remove unused constructor properties
   */
  public boolean isRemoveUnusedConstructorProperties() {
    return removeUnusedConstructorProperties;
  }

  /**
   * @param removeUnused Whether to attempt to remove
   *      unused constructor properties
   */
  public void setRemoveUnusedConstructorProperties(boolean removeUnused) {
    this.removeUnusedConstructorProperties = removeUnused;
  }

  /**
   * Returns the map of define replacements.
   */
  public Map<String, Node> getDefineReplacements() {
    return getReplacementsHelper(defineReplacements);
  }

  /**
   * Returns the map of tweak replacements.
   */
  public Map<String, Node> getTweakReplacements() {
    return getReplacementsHelper(tweakReplacements);
  }

  /**
   * Creates a map of String->Node from a map of String->Number/String/Boolean.
   */
  private static Map<String, Node> getReplacementsHelper(
      Map<String, Object> source) {
    ImmutableMap.Builder<String, Node> map = ImmutableMap.builder();
    for (Map.Entry<String, Object> entry : source.entrySet()) {
      String name = entry.getKey();
      Object value = entry.getValue();
      if (value instanceof Boolean) {
        map.put(name, NodeUtil.booleanNode(((Boolean) value).booleanValue()));
      } else if (value instanceof Integer) {
        map.put(name, IR.number(((Integer) value).intValue()));
      } else if (value instanceof Double) {
        map.put(name, IR.number(((Double) value).doubleValue()));
      } else {
        Preconditions.checkState(value instanceof String);
        map.put(name, IR.string((String) value));
      }
    }
    return map.build();
  }

  /**
   * Sets the value of the {@code @define} variable in JS
   * to a boolean literal.
   */
  public void setDefineToBooleanLiteral(String defineName, boolean value) {
    defineReplacements.put(defineName, value);
  }

  /**
   * Sets the value of the {@code @define} variable in JS to a
   * String literal.
   */
  public void setDefineToStringLiteral(String defineName, String value) {
    defineReplacements.put(defineName, value);
  }

  /**
   * Sets the value of the {@code @define} variable in JS to a
   * number literal.
   */
  public void setDefineToNumberLiteral(String defineName, int value) {
    defineReplacements.put(defineName, value);
  }

  /**
   * Sets the value of the {@code @define} variable in JS to a
   * number literal.
   */
  public void setDefineToDoubleLiteral(String defineName, double value) {
    defineReplacements.put(defineName, value);
  }

  /**
   * Sets the value of the tweak in JS
   * to a boolean literal.
   */
  public void setTweakToBooleanLiteral(String tweakId, boolean value) {
    tweakReplacements.put(tweakId, value);
  }

  /**
   * Sets the value of the tweak in JS to a
   * String literal.
   */
  public void setTweakToStringLiteral(String tweakId, String value) {
    tweakReplacements.put(tweakId, value);
  }

  /**
   * Sets the value of the tweak in JS to a
   * number literal.
   */
  public void setTweakToNumberLiteral(String tweakId, int value) {
    tweakReplacements.put(tweakId, value);
  }

  /**
   * Sets the value of the tweak in JS to a
   * number literal.
   */
  public void setTweakToDoubleLiteral(String tweakId, double value) {
    tweakReplacements.put(tweakId, value);
  }

  /**
   * Skip all possible passes, to make the compiler as fast as possible.
   */
  public void skipAllCompilerPasses() {
    skipNonTranspilationPasses = true;
  }

  /**
   * Whether the warnings guard in this Options object enables the given
   * group of warnings.
   */
  boolean enables(DiagnosticGroup type) {
    return this.warningsGuard.enables(type);
  }

  /**
   * Whether the warnings guard in this Options object disables the given
   * group of warnings.
   */
  boolean disables(DiagnosticGroup type) {
    return this.warningsGuard.disables(type);
  }

  /**
   * Configure the given type of warning to the given level.
   */
  public void setWarningLevel(DiagnosticGroup type, CheckLevel level) {
    addWarningsGuard(new DiagnosticGroupWarningsGuard(type, level));
  }

  WarningsGuard getWarningsGuard() {
    return this.warningsGuard;
  }

  /**
   * Reset the warnings guard.
   */
  public void resetWarningsGuard() {
    this.warningsGuard = new ComposeWarningsGuard();
  }

  /**
   * The emergency fail safe removes all strict and ERROR-escalating
   * warnings guards.
   */
  void useEmergencyFailSafe() {
    this.warningsGuard = this.warningsGuard.makeEmergencyFailSafeGuard();
  }

  void useNonStrictWarningsGuard() {
    this.warningsGuard = this.warningsGuard.makeNonStrict();
  }

  /**
   * Add a guard to the set of warnings guards.
   */
  public void addWarningsGuard(WarningsGuard guard) {
    this.warningsGuard.addGuard(guard);
  }

  /**
   * Sets the variable and property renaming policies for the compiler,
   * in a way that clears warnings about the renaming policy being
   * uninitialized from flags.
   */
  public void setRenamingPolicy(VariableRenamingPolicy newVariablePolicy,
      PropertyRenamingPolicy newPropertyPolicy) {
    this.variableRenaming = newVariablePolicy;
    this.propertyRenaming = newPropertyPolicy;
  }

  /** Should shadow outer scope variable name during renaming. */
  public void setShadowVariables(boolean shadow) {
    this.shadowVariables = shadow;
  }

  /**
   * If true, process goog.testing.ObjectPropertyString instances.
   */
  public void setProcessObjectPropertyString(boolean process) {
    processObjectPropertyString = process;
  }

  /**
   * @param replaceIdGenerators the replaceIdGenerators to set
   */
  public void setReplaceIdGenerators(boolean replaceIdGenerators) {
    this.replaceIdGenerators = replaceIdGenerators;
  }

  /**
   * Sets the id generators to replace.
   */
  public void setIdGenerators(Set<String> idGenerators) {
    RenamingMap gen = new UniqueRenamingToken();
    ImmutableMap.Builder<String, RenamingMap> builder = ImmutableMap.builder();
    for (String name : idGenerators) {
       builder.put(name, gen);
    }
    this.idGenerators = builder.build();
  }

  /**
   * Sets the hash function to use for Xid
   */
  public void setXidHashFunction(Xid.HashFunction xidHashFunction) {
    this.xidHashFunction = xidHashFunction;
  }

  /**
   * Sets the id generators to replace.
   */
  public void setIdGenerators(Map<String, RenamingMap> idGenerators) {
    this.idGenerators = ImmutableMap.copyOf(idGenerators);
  }

  /**
   * A previous map of ids (serialized to a string by a previous compile).
   * This will be used as a hint during the ReplaceIdGenerators pass, which
   * will attempt to reuse the same ids.
   */
  public void setIdGeneratorsMap(String previousMappings) {
    this.idGeneratorsMapSerialized = previousMappings;
  }

  /**
   * Set the function inlining policy for the compiler.
   */
  public void setInlineFunctions(Reach reach) {
    switch (reach) {
      case ALL:
        this.inlineFunctions = true;
        this.inlineLocalFunctions = true;
        break;
      case LOCAL_ONLY:
        this.inlineFunctions = false;
        this.inlineLocalFunctions = true;
        break;
      case NONE:
        this.inlineFunctions = false;
        this.inlineLocalFunctions = false;
        break;
      default:
        throw new IllegalStateException("unexpected");
    }
  }

  public void setMaxFunctionSizeAfterInlining(int funAstSize) {
    Preconditions.checkArgument(funAstSize > 0);
    this.maxFunctionSizeAfterInlining = funAstSize;
  }

  /**
   * Set the variable inlining policy for the compiler.
   */
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
      default:
        throw new IllegalStateException("unexpected");
    }
  }

  /**
   * Set the function inlining policy for the compiler.
   */
  public void setInlineProperties(boolean enable) {
    inlineProperties = enable;
  }

  boolean shouldInlineProperties() {
    return inlineProperties;
  }

  /**
   * Set the variable removal policy for the compiler.
   */
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
      default:
        throw new IllegalStateException("unexpected");
    }
  }

  /**
   * Sets the functions whose debug strings to replace.
   */
  public void setReplaceStringsConfiguration(
      String placeholderToken, List<String> functionDescriptors) {
    this.replaceStringsPlaceholderToken = placeholderToken;
    this.replaceStringsFunctionDescriptions =
         new ArrayList<>(functionDescriptors);
  }

  public void setRemoveAbstractMethods(boolean remove) {
    this.removeAbstractMethods = remove;
  }

  public void setRemoveSuperMethods(boolean remove) {
    this.removeSuperMethods = remove;
  }

  public void setRemoveClosureAsserts(boolean remove) {
    this.removeClosureAsserts = remove;
  }

  public void setColorizeErrorOutput(boolean colorizeErrorOutput) {
    this.colorizeErrorOutput = colorizeErrorOutput;
  }

  public boolean shouldColorizeErrorOutput() {
    return colorizeErrorOutput;
  }

  /**
   * If true, chain calls to functions that return this.
   */
  public void setChainCalls(boolean value) {
    this.chainCalls = value;
  }

  /**
   * Enable run-time type checking, which adds JS type assertions for debugging.
   *
   * @param logFunction A JS function to be used for logging run-time type
   *     assertion failures.
   */
  public void enableRuntimeTypeCheck(String logFunction) {
    this.runtimeTypeCheck = true;
    this.runtimeTypeCheckLogFunction = logFunction;
  }

  public void disableRuntimeTypeCheck() {
    this.runtimeTypeCheck = false;
  }

  public void setChecksOnly(boolean checksOnly) {
    this.checksOnly = checksOnly;
  }

  void setOutputJs(OutputJs outputJs) {
    this.outputJs = outputJs;
  }

  public void setGenerateExports(boolean generateExports) {
    this.generateExports = generateExports;
  }

  public void setExportLocalPropertyDefinitions(boolean export) {
    this.exportLocalPropertyDefinitions = export;
  }

  public void setAngularPass(boolean angularPass) {
    this.angularPass = angularPass;
  }

  public void setPolymerPass(boolean polymerPass) {
    this.polymerPass = polymerPass;
  }

  public void setDartPass(boolean dartPass) {
    this.dartPass = dartPass;
  }

  @Deprecated
  public void setJ2clPass(boolean flag) {
    setJ2clPass(flag ? J2clPassMode.ON : J2clPassMode.OFF);
  }

  public void setJ2clPass(J2clPassMode j2clPassMode) {
    this.j2clPassMode = j2clPassMode;
    if (j2clPassMode.isExplicitlyOn()) {
      setWarningLevel(DiagnosticGroup.forType(SourceFile.DUPLICATE_ZIP_CONTENTS), CheckLevel.OFF);
    }
  }

  public void setCodingConvention(CodingConvention codingConvention) {
    this.codingConvention = codingConvention;
  }

  public CodingConvention getCodingConvention() {
    return codingConvention;
  }

  /**
   * Sets dependency options. See the DependencyOptions class for more info.
   * This supersedes manageClosureDependencies.
   */
  public void setDependencyOptions(DependencyOptions options) {
    this.dependencyOptions = options;
  }

  public DependencyOptions getDependencyOptions() {
    return dependencyOptions;
  }

  /**
   * Sort inputs by their goog.provide/goog.require calls, and prune inputs
   * whose symbols are not required.
   */
  public void setManageClosureDependencies(boolean newVal) {
    dependencyOptions.setDependencySorting(
        newVal || dependencyOptions.shouldSortDependencies());
    dependencyOptions.setDependencyPruning(
        newVal || dependencyOptions.shouldPruneDependencies());
    dependencyOptions.setMoocherDropping(false);
  }

  /**
   * Sort inputs by their goog.provide/goog.require calls.
   *
   * @param entryPoints Entry points to the program. Must be goog.provide'd
   *     symbols. Any goog.provide'd symbols that are not a transitive
   *     dependency of the entry points will be deleted.
   *     Files without goog.provides, and their dependencies,
   *     will always be left in.
   */
  public void setManageClosureDependencies(List<String> entryPoints) {
    Preconditions.checkNotNull(entryPoints);
    setManageClosureDependencies(true);

    List<ModuleIdentifier> normalizedEntryPoints = new ArrayList<>();

    for (String entryPoint : entryPoints) {
      normalizedEntryPoints.add(ModuleIdentifier.forClosure(entryPoint));
    }

    dependencyOptions.setEntryPoints(normalizedEntryPoints);
  }

  /**
   * Controls how detailed the compilation summary is. Values:
   *  0 (never print summary), 1 (print summary only if there are
   * errors or warnings), 2 (print summary if type checking is on,
   * see --check_types), 3 (always print summary). The default level
   * is 1
   */
  public void setSummaryDetailLevel(int summaryDetailLevel) {
    this.summaryDetailLevel = summaryDetailLevel;
  }

  /**
   * @deprecated replaced by {@link #setExternExports}
   */
  @Deprecated
  public void enableExternExports(boolean enabled) {
    this.externExports = enabled;
  }

  public void setExtraAnnotationNames(Iterable<String> extraAnnotationNames) {
    this.extraAnnotationNames = ImmutableSet.copyOf(extraAnnotationNames);
  }

  public boolean isExternExportsEnabled() {
    return externExports;
  }

  /**
   * Sets the output charset.
   */
  public void setOutputCharset(Charset charsetName) {
    this.outputCharset = charsetName;
  }

  /**
   * Gets the output charset.
   */
  Charset getOutputCharset() {
    return outputCharset;
  }

  /**
   * Sets how goog.tweak calls are processed.
   */
  public void setTweakProcessing(TweakProcessing tweakProcessing) {
    this.tweakProcessing = tweakProcessing;
  }

  public TweakProcessing getTweakProcessing() {
    return tweakProcessing;
  }

  /**
   * Sets ECMAScript version to use.
   */
  public void setLanguage(LanguageMode language) {
    Preconditions.checkState(language != LanguageMode.NO_TRANSPILE);
    this.languageIn = language;
    this.languageOut = language;
  }

  /**
   * Sets ECMAScript version to use for the input. If you are not
   * transpiling from one version to another, use #setLanguage instead.
   */
  public void setLanguageIn(LanguageMode languageIn) {
    Preconditions.checkState(languageIn != LanguageMode.NO_TRANSPILE);
    this.languageIn = languageIn;
  }

  public LanguageMode getLanguageIn() {
    return languageIn;
  }

  /**
   * Sets ECMAScript version to use for the output. If you are not
   * transpiling from one version to another, use #setLanguage instead.
   */
  public void setLanguageOut(LanguageMode languageOut) {
    this.languageOut = languageOut;
    if (languageOut == LanguageMode.ECMASCRIPT3) {
      this.quoteKeywordProperties = true;
    }
  }

  public LanguageMode getLanguageOut() {
    if (languageOut == LanguageMode.NO_TRANSPILE) {
      return languageIn;
    }
    return languageOut;
  }

  /**
   * Set which set of builtin externs to use.
   */
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  public Environment getEnvironment() {
    return environment;
  }

  /**
   * @return whether we are currently transpiling from ES6 to a lower version.
   */
  boolean lowerFromEs6() {
    return languageOut != LanguageMode.NO_TRANSPILE
        && languageIn.isEs6OrHigher()
        && !languageOut.isEs6OrHigher();
  }

  /**
   * @return whether we are currently transpiling to ES6_TYPED
   */
  boolean raiseToEs6Typed() {
    return languageOut != LanguageMode.NO_TRANSPILE
        && !languageIn.isEs6OrHigher()
        && languageOut == LanguageMode.ECMASCRIPT6_TYPED;
  }

  public void setAliasTransformationHandler(
      AliasTransformationHandler changes) {
    this.aliasHandler = changes;
  }

  public AliasTransformationHandler getAliasTransformationHandler() {
    return this.aliasHandler;
  }

  /**
   * Set a custom handler for warnings and errors.
   *
   * This is mostly used for piping the warnings and errors to
   * a file behind the scenes.
   *
   * If you want to filter warnings and errors, you should use a WarningsGuard.
   *
   * If you want to change how warnings and errors are reported to the user,
   * you should set a ErrorManager on the Compiler. An ErrorManager is
   * intended to summarize the errors for a single compile job.
   */
  public void setErrorHandler(ErrorHandler handler) {
    this.errorHandler = handler;
  }

  /**
   * If true, enables type inference. If checkTypes is enabled, this flag has
   * no effect.
   */
  public void setInferTypes(boolean enable) {
    inferTypes = enable;
  }

  /**
   * Gets the inferTypes flag. Note that if checkTypes is enabled, this flag
   * is ignored when configuring the compiler.
   */
  public boolean getInferTypes() {
    return inferTypes;
  }

  public boolean getNewTypeInference() {
    return this.useNewTypeInference;
  }

  public void setNewTypeInference(boolean enable) {
    this.useNewTypeInference = enable;
  }

  // Not dead code; used by the open-source users of the compiler.
  public void setReportOTIErrorsUnderNTI(boolean enable) {
    this.reportOTIErrorsUnderNTI = enable;
  }

/**
   * @return Whether assumeStrictThis is set.
   */
  public boolean assumeStrictThis() {
    return assumeStrictThis;
  }

  /**
   * If true, enables enables additional optimizations.
   */
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
   * Whether to assume closures capture only what they reference. This allows
   * more aggressive function inlining.
   */
  public void setAssumeClosuresOnlyCaptureReferences(boolean enable) {
    this.assumeClosuresOnlyCaptureReferences = enable;
  }

  /**
   * Sets the list of properties that we report property invalidation errors
   * for.
   */
  public void setPropertyInvalidationErrors(
      Map<String, CheckLevel> propertyInvalidationErrors) {
    this.propertyInvalidationErrors =
         ImmutableMap.copyOf(propertyInvalidationErrors);
  }

  /**
   * Configures the compiler for use as an IDE backend.  In this mode:
   * <ul>
   *  <li>No optimization passes will run.</li>
   *  <li>The last time custom passes are invoked is
   *      {@link CustomPassExecutionTime#BEFORE_OPTIMIZATIONS}</li>
   *  <li>The compiler will always try to process all inputs fully, even
   *      if it encounters errors.</li>
   *  <li>The compiler may record more information than is strictly
   *      needed for codegen.</li>
   * </ul>
   *
   * @deprecated Some "IDE" clients will need some of these options but not
   * others. Consider calling setChecksOnly, setAllowRecompilation, etc,
   * explicitly, instead of calling this method which does a variety of
   * different things.
   */
  @Deprecated
  public void setIdeMode(boolean ideMode) {
    setChecksOnly(ideMode);
    setContinueAfterErrors(ideMode);
    setAllowHotswapReplaceScript(ideMode);
    setPreserveDetailedSourceInfo(ideMode);
    setParseJsDocDocumentation(
        ideMode
            ? Config.JsDocParsing.INCLUDE_DESCRIPTIONS_NO_WHITESPACE
            : Config.JsDocParsing.TYPES_ONLY);
  }

  public void setAllowHotswapReplaceScript(boolean allowRecompilation) {
    this.allowHotswapReplaceScript = allowRecompilation;
  }

  boolean allowsHotswapReplaceScript() {
    return allowHotswapReplaceScript;
  }

  public void setPreserveDetailedSourceInfo(boolean preserveDetailedSourceInfo) {
    this.preserveDetailedSourceInfo = preserveDetailedSourceInfo;
  }

  boolean preservesDetailedSourceInfo() {
    return preserveDetailedSourceInfo;
  }

  public void setContinueAfterErrors(boolean continueAfterErrors) {
    this.continueAfterErrors = continueAfterErrors;
  }

  boolean canContinueAfterErrors() {
    return continueAfterErrors;
  }


  @Deprecated
  public void setParseJsDocDocumentation(boolean parseJsDocDocumentation) {
    setParseJsDocDocumentation(
        parseJsDocDocumentation
            ? Config.JsDocParsing.INCLUDE_DESCRIPTIONS_NO_WHITESPACE
            : Config.JsDocParsing.TYPES_ONLY);
  }

  /**
   * Enables or disables the parsing of JSDoc documentation, and optionally also
   * the preservation of all whitespace and formatting within a JSDoc comment.
   * By default, whitespace is collapsed for all comments except {@literal @license} and
   * {@literal @preserve} blocks,
   *
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
   * Skip all passes (other than transpilation, if requested). Don't inject any
   * runtime libraries (unless explicitly requested) or do any checks/optimizations
   * (this is useful for per-file transpilation).
   */
  public void setSkipNonTranspilationPasses(boolean skipNonTranspilationPasses) {
    this.skipNonTranspilationPasses = skipNonTranspilationPasses;
  }

  public void setDevMode(DevMode devMode) {
    this.devMode = devMode;
  }

  public void setCheckDeterminism(boolean checkDeterminism) {
    this.checkDeterminism = checkDeterminism;
    if (checkDeterminism) {
      this.useDebugLog = true;
    }
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

  public void setCheckMissingGetCssNameBlacklist(String blackList) {
    this.checkMissingGetCssNameBlacklist = blackList;
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

  public void setInlineFunctions(boolean inlineFunctions) {
    this.inlineFunctions = inlineFunctions;
  }

  public void setInlineLocalFunctions(boolean inlineLocalFunctions) {
    this.inlineLocalFunctions = inlineLocalFunctions;
  }

  public void setCrossModuleCodeMotion(boolean crossModuleCodeMotion) {
    this.crossModuleCodeMotion = crossModuleCodeMotion;
  }

  public void setCrossModuleCodeMotionNoStubMethods(boolean
      crossModuleCodeMotionNoStubMethods) {
    this.crossModuleCodeMotionNoStubMethods = crossModuleCodeMotionNoStubMethods;
  }

  public void setParentModuleCanSeeSymbolsDeclaredInChildren(
      boolean parentModuleCanSeeSymbolsDeclaredInChildren) {
    this.parentModuleCanSeeSymbolsDeclaredInChildren =
        parentModuleCanSeeSymbolsDeclaredInChildren;
  }

  public void setCoalesceVariableNames(boolean coalesceVariableNames) {
    this.coalesceVariableNames = coalesceVariableNames;
  }

  public void setCrossModuleMethodMotion(boolean crossModuleMethodMotion) {
    this.crossModuleMethodMotion = crossModuleMethodMotion;
  }

  public void setInlineVariables(boolean inlineVariables) {
    this.inlineVariables = inlineVariables;
  }

  public void setInlineLocalVariables(boolean inlineLocalVariables) {
    this.inlineLocalVariables = inlineLocalVariables;
  }

  public void setFlowSensitiveInlineVariables(boolean enabled) {
    this.flowSensitiveInlineVariables = enabled;
  }

  public void setSmartNameRemoval(boolean smartNameRemoval) {
    this.smartNameRemoval = smartNameRemoval;
  }

  public void setExtraSmartNameRemoval(boolean smartNameRemoval) {
    this.extraSmartNameRemoval = smartNameRemoval;
  }

  public void setRemoveDeadCode(boolean removeDeadCode) {
    this.removeDeadCode = removeDeadCode;
  }

  public void setExtractPrototypeMemberDeclarations(boolean enabled) {
    this.extractPrototypeMemberDeclarations =
        enabled ? ExtractPrototypeMemberDeclarationsMode.USE_GLOBAL_TEMP
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
    // RemoveUnusedPrototypeProperties, so they are enabled together.
    this.inlineGetters = enabled;
  }

  public void setRemoveUnusedPrototypePropertiesInExterns(boolean enabled) {
    this.removeUnusedPrototypePropertiesInExterns = enabled;
  }

  public void setCollapseVariableDeclarations(boolean enabled) {
    this.collapseVariableDeclarations = enabled;
  }

  public void setCollapseAnonymousFunctions(boolean enabled) {
    this.collapseAnonymousFunctions = enabled;
  }

  public void setAliasableStrings(Set<String> aliasableStrings) {
    this.aliasableStrings = aliasableStrings;
  }

  public void setAliasStringsBlacklist(String aliasStringsBlacklist) {
    this.aliasStringsBlacklist = aliasStringsBlacklist;
  }

  public void setAliasAllStrings(boolean aliasAllStrings) {
    this.aliasAllStrings = aliasAllStrings;
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

  @Deprecated
  public void setUseTypesForOptimization(boolean useTypesForOptimization) {
    if (useTypesForOptimization) {
      this.disambiguateProperties = useTypesForOptimization;
      this.ambiguateProperties = useTypesForOptimization;
      this.inlineProperties = useTypesForOptimization;
      this.useTypesForLocalOptimization = useTypesForOptimization;
    }
  }

  public void setRewriteFunctionExpressions(boolean rewriteFunctionExpressions) {
    this.rewriteFunctionExpressions = rewriteFunctionExpressions;
  }

  public void setOptimizeParameters(boolean optimizeParameters) {
    this.optimizeParameters = optimizeParameters;
  }

  public void setOptimizeReturns(boolean optimizeReturns) {
    this.optimizeReturns = optimizeReturns;
  }

  public void setOptimizeCalls(boolean optimizeCalls) {
    this.optimizeCalls = optimizeCalls;
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

  public void setRenamePrefix(String renamePrefix) {
    this.renamePrefix = renamePrefix;
  }

  public String getRenamePrefixNamespace() {
    return this.renamePrefixNamespace;
  }

  public void setRenamePrefixNamespace(String renamePrefixNamespace) {
    this.renamePrefixNamespace = renamePrefixNamespace;
  }

  public void setCollapseProperties(boolean collapseProperties) {
    this.collapseProperties = collapseProperties;
  }

  public void setDevirtualizePrototypeMethods(boolean devirtualizePrototypeMethods) {
    this.devirtualizePrototypeMethods = devirtualizePrototypeMethods;
  }

  public void setComputeFunctionSideEffects(boolean computeFunctionSideEffects) {
    this.computeFunctionSideEffects = computeFunctionSideEffects;
  }

  public void setDebugFunctionSideEffectsPath(String debugFunctionSideEffectsPath) {
    this.debugFunctionSideEffectsPath = debugFunctionSideEffectsPath;
  }

  /**
   * @return Whether disambiguate private properties is enabled.
   */
  public boolean isDisambiguatePrivateProperties() {
    return disambiguatePrivateProperties;
  }

  /**
   * @param value Whether to enable private property disambiguation based on
   * the coding convention.
   */
  public void setDisambiguatePrivateProperties(boolean value) {
    this.disambiguatePrivateProperties = value;
  }

  public void setDisambiguateProperties(boolean disambiguateProperties) {
    this.disambiguateProperties = disambiguateProperties;
  }

  boolean shouldDisambiguateProperties() {
    return this.disambiguateProperties;
  }

  public void setAmbiguateProperties(boolean ambiguateProperties) {
    this.ambiguateProperties = ambiguateProperties;
  }

  boolean shouldAmbiguateProperties() {
    return this.ambiguateProperties;
  }

  public void setAnonymousFunctionNaming(
      AnonymousFunctionNamingPolicy anonymousFunctionNaming) {
    this.anonymousFunctionNaming = anonymousFunctionNaming;
  }

  public void setInputAnonymousFunctionNamingMap(VariableMap inputMap) {
    this.inputAnonymousFunctionNamingMap = inputMap;
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

  public void setRuntimeTypeCheck(boolean runtimeTypeCheck) {
    this.runtimeTypeCheck = runtimeTypeCheck;
  }

  public void setRuntimeTypeCheckLogFunction(String runtimeTypeCheckLogFunction) {
    this.runtimeTypeCheckLogFunction = runtimeTypeCheckLogFunction;
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

  public void setMarkAsCompiled(boolean markAsCompiled) {
    this.markAsCompiled = markAsCompiled;
  }

  public void setClosurePass(boolean closurePass) {
    this.closurePass = closurePass;
  }

  public void setPreserveGoogProvidesAndRequires(boolean preserveGoogProvidesAndRequires) {
    this.preserveGoogProvidesAndRequires = preserveGoogProvidesAndRequires;
  }

  public boolean shouldPreservesGoogProvidesAndRequires() {
    return this.preserveGoogProvidesAndRequires || this.shouldGenerateTypedExterns();
  }

  public void setPreserveTypeAnnotations(boolean preserveTypeAnnotations) {
    this.preserveTypeAnnotations = preserveTypeAnnotations;
  }

  public void setGatherCssNames(boolean gatherCssNames) {
    this.gatherCssNames = gatherCssNames;
  }

  public void setStripTypes(Set<String> stripTypes) {
    this.stripTypes = stripTypes;
  }

  public void setStripNameSuffixes(Set<String> stripNameSuffixes) {
    this.stripNameSuffixes = stripNameSuffixes;
  }

  public void setStripNamePrefixes(Set<String> stripNamePrefixes) {
    this.stripNamePrefixes = stripNamePrefixes;
  }

  public void setStripTypePrefixes(Set<String> stripTypePrefixes) {
    this.stripTypePrefixes = stripTypePrefixes;
  }

  public void addCustomPass(CustomPassExecutionTime time, CompilerPass customPass) {
    if (customPasses == null) {
      customPasses = LinkedHashMultimap.<CustomPassExecutionTime, CompilerPass>create();
    }
    customPasses.put(time, customPass);
  }

  public void setMarkNoSideEffectCalls(boolean markNoSideEffectCalls) {
    this.markNoSideEffectCalls = markNoSideEffectCalls;
  }

  public void setDefineReplacements(Map<String, Object> defineReplacements) {
    this.defineReplacements = defineReplacements;
  }

  public void setTweakReplacements(Map<String, Object> tweakReplacements) {
    this.tweakReplacements = tweakReplacements;
  }

  public void setMoveFunctionDeclarations(boolean moveFunctionDeclarations) {
    this.moveFunctionDeclarations = moveFunctionDeclarations;
  }

  public void setInstrumentationTemplate(Instrumentation instrumentationTemplate) {
    this.instrumentationTemplate = instrumentationTemplate;
  }

  public void setInstrumentationTemplateFile(String filename){
    this.instrumentationTemplateFile = filename;
  }

  public void setRecordFunctionInformation(boolean recordFunctionInformation) {
    this.recordFunctionInformation = recordFunctionInformation;
  }

  public void setCssRenamingMap(CssRenamingMap cssRenamingMap) {
    this.cssRenamingMap = cssRenamingMap;
  }

  public void setCssRenamingWhitelist(Set<String> whitelist) {
    this.cssRenamingWhitelist = whitelist;
  }

  public void setReplaceStringsFunctionDescriptions(
      List<String> replaceStringsFunctionDescriptions) {
    this.replaceStringsFunctionDescriptions =
        replaceStringsFunctionDescriptions;
  }

  public void setReplaceStringsPlaceholderToken(
      String replaceStringsPlaceholderToken) {
    this.replaceStringsPlaceholderToken =
        replaceStringsPlaceholderToken;
  }

  public void setReplaceStringsReservedStrings(
      Set<String> replaceStringsReservedStrings) {
    this.replaceStringsReservedStrings =
        replaceStringsReservedStrings;
  }

  public void setReplaceStringsInputMap(VariableMap serializedMap) {
    this.replaceStringsInputMap = serializedMap;
  }

  public void setPrettyPrint(boolean prettyPrint) {
    this.prettyPrint = prettyPrint;
  }

  public void setLineBreak(boolean lineBreak) {
    this.lineBreak = lineBreak;
  }

  public boolean getPreferLineBreakAtEndOfFile() {
    return this.preferLineBreakAtEndOfFile;
  }

  public void setPreferLineBreakAtEndOfFile(boolean lineBreakAtEnd) {
    this.preferLineBreakAtEndOfFile = lineBreakAtEnd;
  }

  public void setPrintInputDelimiter(boolean printInputDelimiter) {
    this.printInputDelimiter = printInputDelimiter;
  }

  public void setInputDelimiter(String inputDelimiter) {
    this.inputDelimiter = inputDelimiter;
  }

  public void setQuoteKeywordProperties(boolean quoteKeywordProperties) {
    this.quoteKeywordProperties = quoteKeywordProperties;
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

  public void setExternExports(boolean externExports) {
    this.externExports = externExports;
  }

  public void setExternExportsPath(String externExportsPath) {
    this.externExportsPath = externExportsPath;
  }

  public void setSourceMapOutputPath(String sourceMapOutputPath) {
    this.sourceMapOutputPath = sourceMapOutputPath;
  }

  public void setApplyInputSourceMaps(boolean applyInputSourceMaps) {
    this.applyInputSourceMaps = applyInputSourceMaps;
  }

  public void setSourceMapIncludeSourcesContent(boolean sourceMapIncludeSourcesContent) {
    this.sourceMapIncludeSourcesContent = sourceMapIncludeSourcesContent;
  }

  public void setSourceMapDetailLevel(SourceMap.DetailLevel sourceMapDetailLevel) {
    this.sourceMapDetailLevel = sourceMapDetailLevel;
  }

  public void setSourceMapFormat(SourceMap.Format sourceMapFormat) {
    this.sourceMapFormat = sourceMapFormat;
  }

  public void setSourceMapLocationMappings(
      List<SourceMap.LocationMapping> sourceMapLocationMappings) {
    this.sourceMapLocationMappings = sourceMapLocationMappings;
  }

  /**
   * Activates transformation of AMD to CommonJS modules.
   */
  public void setTransformAMDToCJSModules(boolean transformAMDToCJSModules) {
    this.transformAMDToCJSModules = transformAMDToCJSModules;
  }

  /**
   * Rewrites CommonJS modules so that modules can be concatenated together,
   * by renaming all globals to avoid conflicting with other modules.
   */
  public void setProcessCommonJSModules(boolean processCommonJSModules) {
    this.processCommonJSModules = processCommonJSModules;
  }

  /**
   * Sets a path prefix for CommonJS modules (maps to {@link #setModuleRoots(List)}).
   */
  public void setCommonJSModulePathPrefix(String commonJSModulePathPrefix) {
    setModuleRoots(ImmutableList.of(commonJSModulePathPrefix));
  }

  /**
   * Sets the module roots.
   */
  public void setModuleRoots(List<String> moduleRoots) {
    this.moduleRoots = moduleRoots;
  }

  /**
   * Sets whether to rewrite polyfills.
   */
  public void setRewritePolyfills(boolean rewritePolyfills) {
    this.rewritePolyfills = rewritePolyfills;
  }

  public boolean getRewritePolyfills() {
    return this.rewritePolyfills;
  }

  /**
   * Sets list of libraries to always inject, even if not needed.
   */
  public void setForceLibraryInjection(Iterable<String> libraries) {
    this.forceLibraryInjection = ImmutableList.copyOf(libraries);
  }

  /**
   * Sets the set of libraries to never inject, even if required.
   */
  public void setPreventLibraryInjection(boolean preventLibraryInjection) {
    this.preventLibraryInjection = preventLibraryInjection;
  }

  /**
   * Set whether or not code should be modified to provide coverage
   * information.
   */
  public void setInstrumentForCoverage(boolean instrumentForCoverage) {
    this.instrumentForCoverage = instrumentForCoverage;
  }

  /** Set whether to instrument to collect branch coverage */
  public void setInstrumentBranchCoverage(boolean instrumentBranchCoverage) {
    if (instrumentForCoverage || !instrumentBranchCoverage) {
      this.instrumentBranchCoverage = instrumentBranchCoverage;
    } else {
      throw new RuntimeException("The option instrumentForCoverage must be set to true for "
          + "instrumentBranchCoverage to be set to true.");
    }
  }

  public List<ConformanceConfig> getConformanceConfigs() {
    return conformanceConfigs;
  }

  /**
   * Both enable and configure conformance checks, if non-null.
   */
  @GwtIncompatible("Conformance")
  public void setConformanceConfig(ConformanceConfig conformanceConfig) {
    this.conformanceConfigs = ImmutableList.of(conformanceConfig);
  }

  /**
   * Both enable and configure conformance checks, if non-null.
   */
  @GwtIncompatible("Conformance")
  public void setConformanceConfigs(List<ConformanceConfig> configs) {
    this.conformanceConfigs = ImmutableList.copyOf(configs);
  }

  public boolean isEmitUseStrict() {
    return emitUseStrict;
  }

  public CompilerOptions setEmitUseStrict(boolean emitUseStrict) {
    this.emitUseStrict = emitUseStrict;
    return this;
  }

  public ModuleLoader.ResolutionMode getModuleResolutionMode() {
    return this.moduleResolutionMode;
  }

  public void setModuleResolutionMode(ModuleLoader.ResolutionMode mode) {
    this.moduleResolutionMode = mode;
  }

  @Override
  public String toString() {
    String strValue =
        MoreObjects.toStringHelper(this)
            .omitNullValues()
            .add("aggressiveFusion", aggressiveFusion)
            .add("aliasableStrings", aliasableStrings)
            .add("aliasAllStrings", aliasAllStrings)
            .add("aliasHandler", getAliasTransformationHandler())
            .add("aliasStringsBlacklist", aliasStringsBlacklist)
            .add("allowHotswapReplaceScript", allowsHotswapReplaceScript())
            .add("ambiguateProperties", ambiguateProperties)
            .add("angularPass", angularPass)
            .add("anonymousFunctionNaming", anonymousFunctionNaming)
            .add("appNameStr", appNameStr)
            .add("assumeClosuresOnlyCaptureReferences", assumeClosuresOnlyCaptureReferences)
            .add("assumeStrictThis", assumeStrictThis())
            .add("brokenClosureRequiresLevel", brokenClosureRequiresLevel)
            .add("chainCalls", chainCalls)
            .add("checkDeterminism", getCheckDeterminism())
            .add("checkEventfulObjectDisposalPolicy", checkEventfulObjectDisposalPolicy)
            .add("checkGlobalNamesLevel", checkGlobalNamesLevel)
            .add("checkGlobalThisLevel", checkGlobalThisLevel)
            .add("checkMissingGetCssNameBlacklist", checkMissingGetCssNameBlacklist)
            .add("checkMissingGetCssNameLevel", checkMissingGetCssNameLevel)
            .add("checksOnly", checksOnly)
            .add("checkSuspiciousCode", checkSuspiciousCode)
            .add("checkSymbols", checkSymbols)
            .add("checkTypes", checkTypes)
            .add("closurePass", closurePass)
            .add("coalesceVariableNames", coalesceVariableNames)
            .add("codingConvention", getCodingConvention())
            .add("collapseAnonymousFunctions", collapseAnonymousFunctions)
            .add("collapseObjectLiterals", collapseObjectLiterals)
            .add("collapseProperties", collapseProperties)
            .add("collapseVariableDeclarations", collapseVariableDeclarations)
            .add("colorizeErrorOutput", shouldColorizeErrorOutput())
            .add("computeFunctionSideEffects", computeFunctionSideEffects)
            .add("conformanceConfigs", getConformanceConfigs())
            .add("continueAfterErrors", canContinueAfterErrors())
            .add("convertToDottedProperties", convertToDottedProperties)
            .add("crossModuleCodeMotion", crossModuleCodeMotion)
            .add("crossModuleCodeMotionNoStubMethods", crossModuleCodeMotionNoStubMethods)
            .add("crossModuleMethodMotion", crossModuleMethodMotion)
            .add("cssRenamingMap", cssRenamingMap)
            .add("cssRenamingWhitelist", cssRenamingWhitelist)
            .add("customPasses", customPasses)
            .add("dartPass", dartPass)
            .add("deadAssignmentElimination", deadAssignmentElimination)
            .add("debugFunctionSideEffectsPath", debugFunctionSideEffectsPath)
            .add("declaredGlobalExternsOnWindow", declaredGlobalExternsOnWindow)
            .add("defineReplacements", getDefineReplacements())
            .add("dependencyOptions", dependencyOptions)
            .add("devirtualizePrototypeMethods", devirtualizePrototypeMethods)
            .add("devMode", devMode)
            .add("disambiguatePrivateProperties", disambiguatePrivateProperties)
            .add("disambiguateProperties", disambiguateProperties)
            .add("enforceAccessControlCodingConventions", enforceAccessControlCodingConventions)
            .add("environment", getEnvironment())
            .add("errorFormat", errorFormat)
            .add("errorHandler", errorHandler)
            .add("exportLocalPropertyDefinitions", exportLocalPropertyDefinitions)
            .add("exportTestFunctions", exportTestFunctions)
            .add("externExports", isExternExportsEnabled())
            .add("externExportsPath", externExportsPath)
            .add("extraAnnotationNames", extraAnnotationNames)
            .add("extractPrototypeMemberDeclarations", extractPrototypeMemberDeclarations)
            .add("extraSmartNameRemoval", extraSmartNameRemoval)
            .add("flowSensitiveInlineVariables", flowSensitiveInlineVariables)
            .add("foldConstants", foldConstants)
            .add("forceLibraryInjection", forceLibraryInjection)
            .add("gatherCssNames", gatherCssNames)
            .add("generateExportsAfterTypeChecking", generateExportsAfterTypeChecking)
            .add("generateExports", generateExports)
            .add("generatePseudoNames", generatePseudoNames)
            .add("generateTypedExterns", shouldGenerateTypedExterns())
            .add("idGenerators", idGenerators)
            .add("idGeneratorsMapSerialized", idGeneratorsMapSerialized)
            .add("inferConsts", inferConsts)
            .add("inferTypes", inferTypes)
            .add("inlineConstantVars", inlineConstantVars)
            .add("inlineFunctions", inlineFunctions)
            .add("inlineGetters", inlineGetters)
            .add("inlineLocalFunctions", inlineLocalFunctions)
            .add("inlineLocalVariables", inlineLocalVariables)
            .add("inlineProperties", inlineProperties)
            .add("inlineVariables", inlineVariables)
            .add("inputAnonymousFunctionNamingMap", inputAnonymousFunctionNamingMap)
            .add("inputDelimiter", inputDelimiter)
            .add("inputPropertyMap", inputPropertyMap)
            .add("inputSourceMaps", inputSourceMaps)
            .add("inputVariableMap", inputVariableMap)
            .add("instrumentationTemplateFile", instrumentationTemplateFile)
            .add("instrumentationTemplate", instrumentationTemplate)
            .add("instrumentForCoverage", instrumentForCoverage)
            .add("instrumentBranchCoverage", instrumentBranchCoverage)
            .add("j2clPassMode", j2clPassMode)
            .add("jqueryPass", jqueryPass)
            .add("labelRenaming", labelRenaming)
            .add("languageIn", getLanguageIn())
            .add("languageOut", getLanguageOut())
            .add("legacyCodeCompile", legacyCodeCompile)
            .add("lineBreak", lineBreak)
            .add("lineLengthThreshold", lineLengthThreshold)
            .add("locale", locale)
            .add("markAsCompiled", markAsCompiled)
            .add("markNoSideEffectCalls", markNoSideEffectCalls)
            .add("maxFunctionSizeAfterInlining", maxFunctionSizeAfterInlining)
            .add("messageBundle", messageBundle)
            .add("moduleRoots", moduleRoots)
            .add("moveFunctionDeclarations", moveFunctionDeclarations)
            .add("nameGenerator", nameGenerator)
            .add("optimizeArgumentsArray", optimizeArgumentsArray)
            .add("optimizeCalls", optimizeCalls)
            .add("optimizeParameters", optimizeParameters)
            .add("optimizeReturns", optimizeReturns)
            .add("outputCharset", outputCharset)
            .add("outputJs", outputJs)
            .add("outputJsStringUsage", outputJsStringUsage)
            .add(
                "parentModuleCanSeeSymbolsDeclaredInChildren",
                parentModuleCanSeeSymbolsDeclaredInChildren)
            .add("parseJsDocDocumentation", isParseJsDocDocumentation())
            .add("polymerPass", polymerPass)
            .add("preferLineBreakAtEndOfFile", preferLineBreakAtEndOfFile)
            .add("preferSingleQuotes", preferSingleQuotes)
            .add("preferStableNames", preferStableNames)
            .add("preserveDetailedSourceInfo", preservesDetailedSourceInfo())
            .add("preserveGoogProvidesAndRequires", preserveGoogProvidesAndRequires)
            .add("preserveTypeAnnotations", preserveTypeAnnotations)
            .add("prettyPrint", prettyPrint)
            .add("preventLibraryInjection", preventLibraryInjection)
            .add("printConfig", printConfig)
            .add("printInputDelimiter", printInputDelimiter)
            .add("printSourceAfterEachPass", printSourceAfterEachPass)
            .add("processCommonJSModules", processCommonJSModules)
            .add("processObjectPropertyString", processObjectPropertyString)
            .add("propertyInvalidationErrors", propertyInvalidationErrors)
            .add("propertyRenaming", propertyRenaming)
            .add("protectHiddenSideEffects", protectHiddenSideEffects)
            .add("quoteKeywordProperties", quoteKeywordProperties)
            .add("recordFunctionInformation", recordFunctionInformation)
            .add("removeAbstractMethods", removeAbstractMethods)
            .add("removeSuperMethods", removeSuperMethods)
            .add("removeClosureAsserts", removeClosureAsserts)
            .add("removeDeadCode", removeDeadCode)
            .add("removeUnusedClassProperties", removeUnusedClassProperties)
            .add("removeUnusedConstructorProperties", removeUnusedConstructorProperties)
            .add("removeUnusedLocalVars", removeUnusedLocalVars)
            .add(
                "removeUnusedPrototypePropertiesInExterns",
                removeUnusedPrototypePropertiesInExterns)
            .add("removeUnusedPrototypeProperties", removeUnusedPrototypeProperties)
            .add("removeUnusedVars", removeUnusedVars)
            .add(
                "renamePrefixNamespaceAssumeCrossModuleNames",
                renamePrefixNamespaceAssumeCrossModuleNames)
            .add("renamePrefixNamespace", renamePrefixNamespace)
            .add("renamePrefix", renamePrefix)
            .add("replaceIdGenerators", replaceIdGenerators)
            .add("replaceMessagesWithChromeI18n", replaceMessagesWithChromeI18n)
            .add("replaceStringsFunctionDescriptions", replaceStringsFunctionDescriptions)
            .add("replaceStringsInputMap", replaceStringsInputMap)
            .add("replaceStringsPlaceholderToken", replaceStringsPlaceholderToken)
            .add("replaceStringsReservedStrings", replaceStringsReservedStrings)
            .add("reportOTIErrorsUnderNTI", reportOTIErrorsUnderNTI)
            .add("reportPath", reportPath)
            .add("reserveRawExports", reserveRawExports)
            .add("rewriteFunctionExpressions", rewriteFunctionExpressions)
            .add("rewritePolyfills", rewritePolyfills)
            .add("runtimeTypeCheckLogFunction", runtimeTypeCheckLogFunction)
            .add("runtimeTypeCheck", runtimeTypeCheck)
            .add("shadowVariables", shadowVariables)
            .add("skipNonTranspilationPasses", skipNonTranspilationPasses)
            .add("skipTranspilationAndCrash", skipTranspilationAndCrash)
            .add("smartNameRemoval", smartNameRemoval)
            .add("sourceMapDetailLevel", sourceMapDetailLevel)
            .add("sourceMapFormat", sourceMapFormat)
            .add("sourceMapLocationMappings", sourceMapLocationMappings)
            .add("sourceMapOutputPath", sourceMapOutputPath)
            .add("stripNamePrefixes", stripNamePrefixes)
            .add("stripNameSuffixes", stripNameSuffixes)
            .add("stripTypePrefixes", stripTypePrefixes)
            .add("stripTypes", stripTypes)
            .add("summaryDetailLevel", summaryDetailLevel)
            .add("syntheticBlockEndMarker", syntheticBlockEndMarker)
            .add("syntheticBlockStartMarker", syntheticBlockStartMarker)
            .add("tcProjectId", tcProjectId)
            .add("tracer", tracer)
            .add("transformAMDToCJSModules", transformAMDToCJSModules)
            .add("trustedStrings", trustedStrings)
            .add("tweakProcessing", getTweakProcessing())
            .add("tweakReplacements", getTweakReplacements())
            .add("useDebugLog", useDebugLog)
            .add("useNewTypeInference", getNewTypeInference())
            .add("emitUseStrict", emitUseStrict)
            .add("useTypesForLocalOptimization", useTypesForLocalOptimization)
            .add("variableRenaming", variableRenaming)
            .add("warningsGuard", getWarningsGuard())
            .add("wrapGoogModulesForWhitespaceOnly", wrapGoogModulesForWhitespaceOnly)
            .toString();

    return strValue;
  }

  //////////////////////////////////////////////////////////////////////////////
  // Enums

  /**
   * A language mode applies to the whole compilation job.
   * As a result, the compiler does not support mixed strict and non-strict in
   * the same compilation job. Therefore, the 'use strict' directive is ignored
   * when the language mode is not strict.
   */
  public static enum LanguageMode {
    /**
     * 90's JavaScript
     */
    ECMASCRIPT3,

    /**
     * Traditional JavaScript
     */
    ECMASCRIPT5,

    /**
     * Nitpicky, traditional JavaScript
     */
    ECMASCRIPT5_STRICT,

    /**
     * Shiny new JavaScript
     * @deprecated Use ECMASCRIPT_2015 with {@code isStrictModeInput == false}.
     */
    @Deprecated
    ECMASCRIPT6,

    /**
     * Nitpicky, shiny new JavaScript
     * @deprecated Use ECMASCRIPT_2015 with {@code isStrictModeInput == true}.
     */
    @Deprecated
    ECMASCRIPT6_STRICT,

    /** ECMAScript standard approved in 2015. */
    ECMASCRIPT_2015,

    /**
     * A superset of ES6 which adds Typescript-style type declarations. Always strict.
     */
    ECMASCRIPT6_TYPED,

    /**
     * @deprecated Use ECMASCRIPT_2016.
     */
    @Deprecated
    ECMASCRIPT7,

    /**
     * ECMAScript standard approved in 2016.
     * Adds the exponent operator (**).
     */
    ECMASCRIPT_2016,

    /** @deprecated Use {@code ECMASCRIPT_NEXT} */
    @Deprecated
    ECMASCRIPT8,

    /**
     * ECMAScript latest draft standard.
     */
    ECMASCRIPT_NEXT,

    /**
     * For languageOut only. The same language mode as the input.
     */
    NO_TRANSPILE;

    /** Whether this is ECMAScript 5 or higher. */
    public boolean isEs5OrHigher() {
      Preconditions.checkState(this != NO_TRANSPILE);
      return this != LanguageMode.ECMASCRIPT3;
    }

    /** Whether this is ECMAScript 6 or higher. */
    public boolean isEs6OrHigher() {
      Preconditions.checkState(this != NO_TRANSPILE);
      switch (this) {
        case ECMASCRIPT3:
        case ECMASCRIPT5:
        case ECMASCRIPT5_STRICT:
          return false;
        default:
          return true;
      }
    }

    public static LanguageMode fromString(String value) {
      if (value == null) {
        return null;
      }
      // Trim spaces, disregard case, and allow abbreviation of ECMASCRIPT for convenience.
      String canonicalizedName = Ascii.toUpperCase(value.trim()).replaceFirst("^ES", "ECMASCRIPT");
      try {
        return LanguageMode.valueOf(canonicalizedName);
      } catch (IllegalArgumentException e) {
        return null; // unknown name.
      }
    }
  }

  /** When to do the extra sanity checks */
  static enum DevMode {
    /**
     * Don't do any extra sanity checks.
     */
    OFF,

    /**
     * After the initial parse
     */
    START,

    /**
     * At the start and at the end of all optimizations.
     */
    START_AND_END,

    /**
     * After every pass
     */
    EVERY_PASS
  }

  /** How much tracing we want to do */
  public static enum TracerMode {
    ALL, // Collect all timing and size metrics. Very slow.
    RAW_SIZE, // Collect all timing and size metrics, except gzipped size. Slow.
    AST_SIZE, // For size data, don't serialize the AST, just count the number of nodes.
    TIMING_ONLY, // Collect timing metrics only.
    OFF;  // Collect no timing and size metrics.

    boolean isOn() {
      return this != OFF;
    }
  }

  /** Option for the ProcessTweaks pass */
  public static enum TweakProcessing {
    OFF,  // Do not run the ProcessTweaks pass.
    CHECK, // Run the pass, but do not strip out the calls.
    STRIP;  // Strip out all calls to goog.tweak.*.

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

  /**
   * A Role Specific Interface for JS Compiler that represents a data holder
   * object which is used to store goog.scope alias code changes to code made
   * during a compile. There is no guarantee that individual alias changes are
   * invoked in the order they occur during compilation, so implementations
   * should not assume any relevance to the order changes arrive.
   * <p>
   * Calls to the mutators are expected to resolve very quickly, so
   * implementations should not perform expensive operations in the mutator
   * methods.
   *
   * @author tylerg@google.com (Tyler Goodwin)
   */
  public interface AliasTransformationHandler {

    /**
     * Builds an AliasTransformation implementation and returns it to the
     * caller.
     * <p>
     * Callers are allowed to request multiple AliasTransformation instances for
     * the same file, though it is expected that the first and last char values
     * for multiple instances will not overlap.
     * <p>
     * This method is expected to have a side-effect of storing off the created
     * AliasTransformation, which guarantees that invokers of this interface
     * cannot leak AliasTransformation to this implementation that the
     * implementor did not create
     *
     * @param sourceFile the source file the aliases re contained in.
     * @param position the region of the source file associated with the
     *        goog.scope call. The item of the SourcePosition is the returned
     *        AliasTransformation
     */
    public AliasTransformation logAliasTransformation(
        String sourceFile, SourcePosition<AliasTransformation> position);
  }

  /**
   * A Role Specific Interface for the JS Compiler to report aliases used to
   * change the code during a compile.
   * <p>
   * While aliases defined by goog.scope are expected to by only 1 per file, and
   * the only top-level structure in the file, this is not enforced.
   */
  public interface AliasTransformation {

    /**
     * Adds an alias definition to the AliasTransformation instance.
     * <p>
     * Last definition for a given alias is kept if an alias is inserted
     * multiple times (since this is generally the behavior in JavaScript code).
     *
     * @param alias the name of the alias.
     * @param definition the definition of the alias.
     */
    void addAlias(String alias, String definition);
  }

  /**
   * A Null implementation of the CodeChanges interface which performs all
   * operations as a No-Op
   */
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
      public void addAlias(String alias, String definition) {
      }
    }
  }

  /**
   * An environment specifies the built-in externs that are loaded for a given
   * compilation.
   */
  public static enum Environment {
    /**
     * Hand crafted externs that have traditionally been the default externs.
     */
    BROWSER,

    /**
     * Only language externs are loaded.
     */
    CUSTOM
  }

  /**
   * Whether standard input or standard output should be an array of
   * JSON encoded files
   */
  static enum JsonStreamMode {
    /**
     * stdin/out are both single files.
     */
    NONE,

    /**
     * stdin is a json stream.
     */
    IN,

    /**
     * stdout is a json stream.
     */
    OUT,

    /**
     * stdin and stdout are both json streams.
     */
    BOTH
  }

  /** How compiler should prune files based on the provide-require dependency graph */
  public static enum DependencyMode {
    /**
     * All files will be included in the compilation
     */
    NONE,

    /**
     * Files must be discoverable from specified entry points. Files
     * which do not goog.provide a namespace and and are not either
     * an ES6 or CommonJS module will be automatically treated as entry points.
     * Module files will be included only if referenced from an entry point.
     */
    LOOSE,

    /**
     * Files must be discoverable from specified entry points. Files which
     * do not goog.provide a namespace and are neither
     * an ES6 or CommonJS module will be dropped. Module files will be included
     * only if referenced from an entry point.
     */
    STRICT
  }

  /**
   * A mode enum used to indicate whether J2clPass should be enabled, disabled, or enabled
   * automatically if there is any J2cl source file (i.e. in the AUTO mode).
   */
  public static enum J2clPassMode {
    /** J2clPass is disabled. */
    FALSE,
    /** J2clPass is enabled. */
    TRUE,
    /** J2clPass is disabled. */
    OFF,
    /** J2clPass is enabled. */
    ON,
    /** It auto-detects whether there are J2cl generated file. If yes, execute J2clPass. */
    AUTO;

    boolean shouldAddJ2clPasses() {
      return this == TRUE || this == ON || this == AUTO;
    }

    boolean isExplicitlyOn() {
      return this == TRUE || this == ON;
    }
  }

  public boolean isStrictModeInput() {
    return isStrictModeInput;
  }

  public CompilerOptions setStrictModeInput(boolean isStrictModeInput) {
    this.isStrictModeInput = isStrictModeInput;
    return this;
  }
}
