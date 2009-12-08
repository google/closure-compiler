/*
 * Copyright 2009 Google Inc.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Compiler options
*
 */
public class CompilerOptions implements Serializable, Cloneable {
  private static final long serialVersionUID = 7L;

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
   */
  public boolean ideMode;

  /**
   * Configures the compiler to skip as many passes as possible.
   */
  boolean skipAllPasses;

  /**
   * If true, name anonymous functions only. All others passes will be skipped.
   */
  boolean nameAnonymousFunctionsOnly;

  /**
   * Configures the compiler to run expensive sanity checks after
   * every pass. Only intended for internal development.
   */
  DevMode devMode;

  //--------------------------------
  // Checks
  //--------------------------------

  /** Checks that all symbols are defined */
  public boolean checkSymbols;

  /**
   * Checks that all variables with the @noshadow attribute are
   * never shadowed.
   */
  public CheckLevel checkShadowVars;

  /** Checks for suspicious variable definitions and undefined variables */
  public CheckLevel aggressiveVarCheck;

  /** Checks function arity */
  public CheckLevel checkFunctions;

  /** Checks method arity */
  public CheckLevel checkMethods;

  /** Makes sure no duplicate messages */
  public boolean checkDuplicateMessages;

  /** Allows old deprecated js message style */
  public boolean allowLegacyJsMessages;

  /**
   * Whether we should throw an exception in case if the message absent from a
   * bundle.
   */
  public boolean strictMessageReplacement;

  /** Checks for suspicious statements that have no effect */
  public boolean checkSuspiciousCode;

  /** Checks for invalid control structures */
  public boolean checkControlStructures;

  /** Checks for non-extern properties that are read but never set. */
  public CheckLevel checkUndefinedProperties;

  /**
   * Checks for non-extern properties that are written but never read.
   * This check occurs after the first constant-based dead code removal pass,
   * but before the main optimization loop.  This is noisy if you are
   * including libraries with methods that you don't use, so it is off by
   * default.
   */
  public boolean checkUnusedPropertiesEarly;

  /** Checks types on expressions */
  public boolean checkTypes;

  /** Tightens types based on a global analysis. */
  public boolean tightenTypes;

  // TODO(user): A temporary flag to prevent the type inference from
  // running in the global scope because it takes too long to finish.
  /** Runs a flow sensitive type inference in the global scope */
  public boolean inferTypesInGlobalScope;

  /** Checks for inexistant property calls */
  public boolean checkTypedPropertyCalls;

  /**
   * Flags a warning if a property is missing the @override annotation, but it
   * overrides a base class property.
   */
  public CheckLevel reportMissingOverride;

  /** Flags a warning for every node whose type could not be determined. */
  public CheckLevel reportUnknownTypes;

  /** Checks for missing goog.require() calls **/
  public CheckLevel checkRequires;

  /** Checks for missing goog.provides() calls **/
  public CheckLevel checkProvides;

  /**
   * Checks the integrity of references to qualified global names.
   * (e.g. "a.b")
   */
  public CheckLevel checkGlobalNamesLevel;

  /** Sets the check level for bad Closure require calls. */
  public CheckLevel brokenClosureRequiresLevel;

  /**
   * Checks for certain uses of the {@code this} keyword that are considered
   * unsafe because they are likely to reference the global {@code this}
   * object unintentionally.
   */
  public CheckLevel checkGlobalThisLevel;

  /**
   * Checks that certain string literals only appear in strings used as
   * goog.getCssName arguments.
   */
  public CheckLevel checkMissingGetCssNameLevel;

  /**
   * Regex of string literals that may only appear in goog.getCssName arguments.
   */
  public String checkMissingGetCssNameBlacklist;

  /** Checks that the synctactic restrictions of ES5 strict mode are met. */
  public boolean checkEs5Strict;

  /** Checks that the synctactic restrictions of Caja are met. */
  public boolean checkCaja;

  //--------------------------------
  // Optimizations
  //--------------------------------

  /** Folds constants (e.g. (2 + 3) to 5) */
  public boolean foldConstants;

  public boolean removeConstantExpressions;

  public boolean deadAssignmentElimination;

  /** Inlines constants (symbols that are all CAPS) */
  public boolean inlineConstantVars;

  /** Inlines short functions */
  public boolean inlineFunctions;

  /** Enhanced function inlining */
  public boolean decomposeExpressions;

  /** Enhanced function inlining */
  public boolean inlineAnonymousFunctionExpressions;

  /** Enhanced function inlining */
  public boolean inlineLocalFunctions;

  /** Move code to a deeper module */
  public boolean crossModuleCodeMotion;

  /** Merge two variables together as one. */
  public boolean coalesceVariableNames;

  /** Move methds to a deeper module */
  public boolean crossModuleMethodMotion;

  /** Inlines trivial getters */
  public boolean inlineGetters;

  /** Inlines variables */
  public boolean inlineVariables;

  /** Inlines variables */
  public boolean inlineLocalVariables;

  // TODO(user): This is temporary. Once flow sensitive inlining is stable
  // Remove this.
  public boolean flowSensitiveInlineVariables;

  /** Removes code associated with unused global names */
  public boolean smartNameRemoval;

  /** Removes code that will never execute */
  public boolean removeDeadCode;

  /** Checks for unreachable code */
  public CheckLevel checkUnreachableCode;

  /** Checks for missing return statements */
  public CheckLevel checkMissingReturn;

  /** Extracts common prototype member declarations */
  public boolean extractPrototypeMemberDeclarations;

  /** Removes functions that have no body */
  public boolean removeEmptyFunctions;

  /** Removes unused member prototypes */
  public boolean removeUnusedPrototypeProperties;

  /** Tells AnalyzePrototypeProperties it can remove externed props. */
  public boolean removeUnusedPrototypePropertiesInExterns;

  /** Removes unused variables */
  public boolean removeUnusedVars;

  /** Removes unused variables in global scope. */
  public boolean removeUnusedVarsInGlobalScope;

  /** Adds variable aliases for externals to reduce code size */
  public boolean aliasExternals;

  /**
   * If set to a non-empty string, then during an alias externals pass only
   * externals with these names will be considered for aliasing.
   */
  public String aliasableGlobals;

  /**
   * Additional globals that can not be aliased since they may be undefined or
   * can cause errors.  Comma separated list of symbols.  e.g. "foo,bar"
   */
  public String unaliasableGlobals;

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

  /** Converts quoted property accesses to dot syntax (a['b'] -> a.b) */
  public boolean convertToDottedProperties;

  /** Reduces the size of common function expressions. */
  public boolean rewriteFunctionExpressions;

  /**
   * Remove unused and constant parameters.
   */
  public boolean optimizeParameters;

  /**
   * Provide formal names for elements of arguments array.
   */
  public boolean optimizeArgumentsArray;

  /** Chains calls to functions that return this. */
  boolean chainCalls;

  //--------------------------------
  // Renaming
  //--------------------------------

  /** Controls which variables get renamed. */
  public VariableRenamingPolicy variableRenaming;

  /** Controls which properties get renamed. */
  public PropertyRenamingPolicy propertyRenaming;

  /** Controls label renaming. */
  public boolean labelRenaming;

  /**
   * Generate pseudo names for variables and properties for debugging purposes.
   */
  public boolean generatePseudoNames;

  /** Specifies a prefix for all globals */
  public String renamePrefix;

  /** Aliases true, false, and null to variables with shorter names. */
  public boolean aliasKeywords;

  /** Flattens multi-level property names (e.g. a$b = x) */
  public boolean collapseProperties;

  /** Flattens multi-level property names on extern types (e.g. String$f = x) */
  boolean collapsePropertiesOnExternTypes;

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
  public String debugFunctionSideEffectsPath;

  /**
   * Rename properties to disambiguate between unrelated fields based on
   * type information.
   */
  public boolean disambiguateProperties;

  /** Rename unrelated properties to the same name to reduce code size. */
  public boolean ambiguateProperties;

  /** Give anonymous functions names for easier debugging */
  public AnonymousFunctionNamingPolicy anonymousFunctionNaming;

  /** Serialized input variable renaming map. */
  public byte[] inputVariableMapSerialized;

  /** Serialized input property renaming map. */
  public byte[] inputPropertyMapSerialized;

  /** Whether to export test functions. */
  public boolean exportTestFunctions;

  //--------------------------------
  // Special-purpose alterations
  //--------------------------------

  /** A CodingConvention to use during the compile. */
  private CodingConvention codingConvention;

  /** Instrument code for the purpose of collecting coverage data. */
  public boolean instrumentForCoverage;

  /**
   * Instrument code for the purpose of collecting coverage data - restrict to
   * coverage pass only, and skip all other passes.
   */
  public boolean instrumentForCoverageOnly;

  public String syntheticBlockStartMarker;

  public String syntheticBlockEndMarker;

  /** Compiling locale */
  public String locale;

  /** Sets the special "COMPILED" value to true */
  public boolean markAsCompiled;

  /** Removes try...catch...finally blocks for easier debugging */
  public boolean removeTryCatchFinally;

  /** Processes goog.provide() and goog.require() calls */
  public boolean closurePass;

  /** Rewrite new Date(goog.now()) to new Date().  */
  boolean rewriteNewDateGoogNow;

  /** Remove goog.abstractMethod assignments. */
  boolean removeAbstractMethods;

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
  public transient
      Multimap<CustomPassExecutionTime, CompilerPass> customPasses;

  /** Mark no side effect calls */
  public boolean markNoSideEffectCalls;

  /** Replacements for @defines. Will be Boolean, Numbers, or Strings */
  private Map<String, Object> defineReplacements;

  /** Move top level function declarations to the top */
  public boolean moveFunctionDeclarations;

  /** Instrumentation template to use */
  public String instrumentationTemplate;

  /**
   * App identifier string for use by the instrumentation template's
   * app_name_setter
   */
  public String appNameStr;

  /** Record function information */
  public boolean recordFunctionInformation;

  public boolean generateExports;

  /** Map used in the renaming of CSS class names. */
  public CssRenamingMap cssRenamingMap;

  /** Process instances of goog.testing.ObjectPropertyString. */
  boolean processObjectPropertyString;

  /** Id generators to replace. */
  Set<String> idGenerators;

  //--------------------------------
  // Output options
  //--------------------------------

  /** Output in pretty indented format */
  public boolean prettyPrint;

  /** Line break the output a bit more aggressively */
  public boolean lineBreak;

  /** Prints a separator comment before each js script */
  public boolean printInputDelimiter;

  /** Where to save a report of global name usage */
  public String reportPath;

  public TracerMode tracer;

  private boolean colorizeErrorOutput;

  public ErrorFormat errorFormat;

  public String jsOutputFile;

  private transient ComposeWarningsGuard warningsGuard;

  //--------------------------------
  // Special Output Options
  //--------------------------------

  /** The output path for the created externs file. */
  String externExportsPath;

  /** Where to save a cross-reference report from the name reference graph */
  public String nameReferenceReportPath;

  /** Where to save the name reference graph */
  public String nameReferenceGraphPath;

  //--------------------------------
  // Debugging Options
  //--------------------------------

  /** The output path for the source map. */
  public String sourceMapOutputPath;

  /**
   * Charset to use when generating code.  If null, then output ASCII.
   */
  public Charset outputCharset;

  /**
   * Initializes compiler options. All options are disabled by default.
   *
   * Command-line frontends to the compiler should set these properties
   * like a builder.
   */
  public CompilerOptions() {
    // Checks
    skipAllPasses = false;
    nameAnonymousFunctionsOnly = false;
    devMode = DevMode.OFF;
    checkSymbols = false;
    checkShadowVars = CheckLevel.OFF;
    aggressiveVarCheck = CheckLevel.OFF;
    checkFunctions = CheckLevel.OFF;
    checkMethods = CheckLevel.OFF;
    checkDuplicateMessages = false;
    allowLegacyJsMessages = false;
    strictMessageReplacement = false;
    checkSuspiciousCode = false;
    checkControlStructures = false;
    checkUndefinedProperties = CheckLevel.OFF;
    checkUnusedPropertiesEarly = false;
    checkTypes = false;
    tightenTypes = false;
    inferTypesInGlobalScope = false;
    checkTypedPropertyCalls = false;
    reportMissingOverride = CheckLevel.OFF;
    reportUnknownTypes = CheckLevel.OFF;
    checkRequires = CheckLevel.OFF;
    checkProvides = CheckLevel.OFF;
    checkGlobalNamesLevel = CheckLevel.OFF;
    brokenClosureRequiresLevel = CheckLevel.ERROR;
    checkGlobalThisLevel = CheckLevel.WARNING;
    checkUnreachableCode = CheckLevel.OFF;
    checkMissingReturn = CheckLevel.OFF;
    checkMissingGetCssNameLevel = CheckLevel.OFF;
    checkMissingGetCssNameBlacklist = null;
    checkEs5Strict = false;
    checkCaja = false;
    computeFunctionSideEffects = false;
    chainCalls = false;

    // Optimizations
    foldConstants = false;
    removeConstantExpressions = false;
    coalesceVariableNames = false;
    deadAssignmentElimination = false;
    inlineConstantVars = false;
    inlineFunctions = false;
    inlineLocalFunctions = false;
    crossModuleCodeMotion = false;
    crossModuleMethodMotion = false;
    inlineGetters = false;
    inlineVariables = false;
    inlineLocalVariables = false;
    smartNameRemoval = false;
    removeDeadCode = false;
    extractPrototypeMemberDeclarations = false;
    removeUnusedPrototypeProperties = false;
    removeUnusedPrototypePropertiesInExterns = false;
    removeUnusedVars = false;
    removeUnusedVarsInGlobalScope = true;
    aliasExternals = false;
    collapseVariableDeclarations = false;
    collapseAnonymousFunctions = false;
    aliasableStrings = Collections.emptySet();
    aliasStringsBlacklist = "";
    aliasAllStrings = false;
    outputJsStringUsage = false;
    convertToDottedProperties = false;
    rewriteFunctionExpressions = false;
    optimizeParameters = false;

    // Renaming
    variableRenaming = VariableRenamingPolicy.OFF;
    propertyRenaming = PropertyRenamingPolicy.OFF;
    labelRenaming = false;
    generatePseudoNames = false;
    renamePrefix = null;
    aliasKeywords = false;
    collapseProperties = false;
    collapsePropertiesOnExternTypes = false;
    devirtualizePrototypeMethods = false;
    disambiguateProperties = false;
    ambiguateProperties = false;
    anonymousFunctionNaming = AnonymousFunctionNamingPolicy.OFF;
    exportTestFunctions = false;

    // Alterations
    instrumentForCoverage = false;
    instrumentForCoverageOnly = false;
    syntheticBlockStartMarker = null;
    syntheticBlockEndMarker = null;
    locale = null;
    markAsCompiled = false;
    removeTryCatchFinally = false;
    closurePass = false;
    rewriteNewDateGoogNow = true;
    removeAbstractMethods = true;
    stripTypes = Collections.emptySet();
    stripNameSuffixes = Collections.emptySet();
    stripNamePrefixes = Collections.emptySet();
    stripTypePrefixes = Collections.emptySet();
    customPasses = null;
    markNoSideEffectCalls = false;
    defineReplacements = Maps.newHashMap();
    moveFunctionDeclarations = false;
    instrumentationTemplate = null;
    appNameStr = "";
    recordFunctionInformation = false;
    generateExports = false;
    cssRenamingMap = null;
    processObjectPropertyString = false;
    idGenerators = Collections.emptySet();

    // Output
    printInputDelimiter = false;
    prettyPrint = false;
    lineBreak = false;
    reportPath = null;
    tracer = TracerMode.OFF;
    colorizeErrorOutput = false;
    errorFormat = ErrorFormat.SINGLELINE;
    warningsGuard = null;
    debugFunctionSideEffectsPath = null;
    jsOutputFile = "";
    nameReferenceReportPath = null;
    nameReferenceGraphPath = null;
  }

  /**
   * Returns the map of define replacements.
   */
  public Map<String, Node> getDefineReplacements() {
    Map<String, Node> map = Maps.newHashMap();
    for (Map.Entry<String, Object> entry : defineReplacements.entrySet()) {
      String name = entry.getKey();
      Object value = entry.getValue();
      if (value instanceof Boolean) {
        map.put(name, ((Boolean) value).booleanValue() ?
            new Node(Token.TRUE) : new Node(Token.FALSE));
      } else if (value instanceof Integer) {
        map.put(name, Node.newNumber(((Integer) value).intValue()));
      } else if (value instanceof Double) {
        map.put(name, Node.newNumber(((Double) value).doubleValue()));
      } else {
        Preconditions.checkState(value instanceof String);
        map.put(name, Node.newString((String) value));
      }
    }
    return map;
  }

  /**
   * Sets the value of the {@code @define} variable in JS
   * to a boolean literal.
   */
  public void setDefineToBooleanLiteral(String defineName, boolean value) {
    defineReplacements.put(defineName, new Boolean(value));
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
    defineReplacements.put(defineName, new Integer(value));
  }

  /**
   * Sets the value of the {@code @define} variable in JS to a
   * number literal.
   */
  public void setDefineToDoubleLiteral(String defineName, double value) {
    defineReplacements.put(defineName, new Double(value));
  }

  /**
   * Skip all possible passes, to make the compiler as fast as possible.
   */
  public void skipAllCompilerPasses() {
    skipAllPasses = true;
  }

  /**
   * Whether the warnings guard in this Options object enables the given
   * group of warnings.
   */
  boolean enables(DiagnosticGroup type) {
    return warningsGuard != null && warningsGuard.enables(type);
  }

  /**
   * Configure the given type of warning to the given level.
   */
  public void setWarningLevel(DiagnosticGroup type, CheckLevel level) {
    addWarningsGuard(new DiagnosticGroupWarningsGuard(type, level));
  }

  WarningsGuard getWarningsGuard() {
    return warningsGuard;
  }

  /**
   * Add a guard to the set of warnings guards.
   */
  public void addWarningsGuard(WarningsGuard guard) {
    if (warningsGuard == null) {
      warningsGuard = new ComposeWarningsGuard(guard);
    } else {
      warningsGuard.addGuard(guard);
    }
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

  /**
   * If true, flattens multi-level property names on extern types
   * (e.g. String$f = x). This should only be used with the typed version of
   * the externs files.
   */
  public void setCollapsePropertiesOnExternTypes(boolean collapse) {
    collapsePropertiesOnExternTypes = collapse;
  }

  /**
   * If true, process goog.testing.ObjectPropertyString instances.
   */
  public void setProcessObjectPropertyString(boolean process) {
    processObjectPropertyString = process;
  }

  /**
   * Sets the id generators to replace.
   */
  public void setIdGenerators(Set<String> idGenerators) {
    this.idGenerators = Sets.newHashSet(idGenerators);
  }

  public void setRewriteNewDateGoogNow(boolean rewrite) {
    this.rewriteNewDateGoogNow = rewrite;
  }

  public void setRemoveAbstractMethods(boolean remove) {
    this.removeAbstractMethods = remove;
  }

  /**
   * If true, name anonymous functions only. All other passes will be skipped.
   */
  public void setNameAnonymousFunctionsOnly(boolean value) {
    this.nameAnonymousFunctionsOnly = value;
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

  public void setCodingConvention(CodingConvention codingConvention) {
    this.codingConvention = codingConvention;
  }

  public CodingConvention getCodingConvention() {
    return codingConvention;
  }

  @Override
  public Object clone() throws CloneNotSupportedException {
    CompilerOptions clone = (CompilerOptions) super.clone();
    // TODO(bolinfest): Add relevant custom cloning.
    return clone;
  }

  //////////////////////////////////////////////////////////////////////////////
  // Enums

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

  public static enum TracerMode {
    ALL,  // Collect all timing and size metrics.
    FAST, // Collect all timing and size metrics, except gzipped size.
    OFF;  // Collect no timing and size metrics.

    boolean isOn() {
      return this != OFF;
    }
  }
}
