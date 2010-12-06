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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pass factories and meta-data for native JSCompiler passes.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
// TODO(nicksantos): This needs state for a variety of reasons. Some of it
// is to satisfy the existing API. Some of it is because passes really do
// need to share state in non-trivial ways. This should be audited and
// cleaned up.
public class DefaultPassConfig extends PassConfig {

  /* For the --mark-as-compiled pass */
  private static final String COMPILED_CONSTANT_NAME = "COMPILED";

  /* Constant name for Closure's locale */
  private static final String CLOSURE_LOCALE_CONSTANT_NAME = "goog.LOCALE";

  // Compiler errors when invalid combinations of passes are run.
  static final DiagnosticType TIGHTEN_TYPES_WITHOUT_TYPE_CHECK =
      DiagnosticType.error("JSC_TIGHTEN_TYPES_WITHOUT_TYPE_CHECK",
          "TightenTypes requires type checking. Please use --check_types.");

  static final DiagnosticType CANNOT_USE_PROTOTYPE_AND_VAR =
      DiagnosticType.error("JSC_CANNOT_USE_PROTOTYPE_AND_VAR",
          "Rename prototypes and inline variables cannot be used together");

  // Miscellaneous errors.
  static final DiagnosticType REPORT_PATH_IO_ERROR =
      DiagnosticType.error("JSC_REPORT_PATH_IO_ERROR",
          "Error writing compiler report to {0}");

  private static final DiagnosticType INPUT_MAP_PROP_PARSE =
      DiagnosticType.error("JSC_INPUT_MAP_PROP_PARSE",
          "Input property map parse error: {0}");

  private static final DiagnosticType INPUT_MAP_VAR_PARSE =
      DiagnosticType.error("JSC_INPUT_MAP_VAR_PARSE",
          "Input variable map parse error: {0}");

  private static final DiagnosticType NAME_REF_GRAPH_FILE_ERROR =
      DiagnosticType.error("JSC_NAME_REF_GRAPH_FILE_ERROR",
          "Error \"{1}\" writing name reference graph to \"{0}\".");

  private static final DiagnosticType NAME_REF_REPORT_FILE_ERROR =
      DiagnosticType.error("JSC_NAME_REF_REPORT_FILE_ERROR",
          "Error \"{1}\" writing name reference report to \"{0}\".");

  /**
   * A global namespace to share across checking passes.
   * TODO(nicksantos): This is a hack until I can get the namespace into
   * the symbol table.
   */
  private GlobalNamespace namespaceForChecks = null;

  /**
   * A type-tightener to share across optimization passes.
   */
  private TightenTypes tightenTypes = null;

  /** Names exported by goog.exportSymbol. */
  private Set<String> exportedNames = null;

  /**
   * Ids for cross-module method stubbing, so that each method has
   * a unique id.
   */
  private CrossModuleMethodMotion.IdGenerator crossModuleIdGenerator =
      new CrossModuleMethodMotion.IdGenerator();

  /**
   * Keys are arguments passed to getCssName() found during compilation; values
   * are the number of times the key appeared as an argument to getCssName().
   */
  private Map<String, Integer> cssNames = null;

  /** The variable renaming map */
  private VariableMap variableMap = null;

  /** The property renaming map */
  private VariableMap propertyMap = null;

  /** The naming map for anonymous functions */
  private VariableMap anonymousFunctionNameMap = null;

  /** Fully qualified function names and globally unique ids */
  private FunctionNames functionNames = null;

  /** String replacement map */
  private VariableMap stringMap = null;

  /** Id generator map */
  private String idGeneratorMap = null;

  public DefaultPassConfig(CompilerOptions options) {
    super(options);
  }

  @Override
  State getIntermediateState() {
    return new State(
        cssNames == null ? null : Maps.newHashMap(cssNames),
        exportedNames == null ? null :
            Collections.unmodifiableSet(exportedNames),
        crossModuleIdGenerator, variableMap, propertyMap,
        anonymousFunctionNameMap, stringMap, functionNames, idGeneratorMap);
  }

  @Override
  void setIntermediateState(State state) {
    this.cssNames = state.cssNames == null ? null :
        Maps.newHashMap(state.cssNames);
    this.exportedNames = state.exportedNames == null ? null :
        Sets.newHashSet(state.exportedNames);
    this.crossModuleIdGenerator = state.crossModuleIdGenerator;
    this.variableMap = state.variableMap;
    this.propertyMap = state.propertyMap;
    this.anonymousFunctionNameMap = state.anonymousFunctionNameMap;
    this.stringMap = state.stringMap;
    this.functionNames = state.functionNames;
    this.idGeneratorMap = state.idGeneratorMap;
  }

  @Override
  protected List<PassFactory> getChecks() {
    List<PassFactory> checks = Lists.newArrayList();

    if (options.closurePass) {
      checks.add(closureGoogScopeAliases);
    }

    if (options.nameAnonymousFunctionsOnly) {
      if (options.anonymousFunctionNaming ==
          AnonymousFunctionNamingPolicy.MAPPED) {
        checks.add(nameMappedAnonymousFunctions);
      } else if (options.anonymousFunctionNaming ==
          AnonymousFunctionNamingPolicy.UNMAPPED) {
        checks.add(nameUnmappedAnonymousFunctions);
      }
      return checks;
    }

    if (options.checkSuspiciousCode ||
        options.checkGlobalThisLevel.isOn()) {
      checks.add(suspiciousCode);
    }

    if (options.checkControlStructures)  {
      checks.add(checkControlStructures);
    }

    if (options.checkRequires.isOn()) {
      checks.add(checkRequires);
    }

    if (options.checkProvides.isOn()) {
      checks.add(checkProvides);
    }

    // The following passes are more like "preprocessor" passes.
    // It's important that they run before most checking passes.
    // Perhaps this method should be renamed?
    if (options.generateExports) {
      checks.add(generateExports);
    }

    if (options.exportTestFunctions) {
      checks.add(exportTestFunctions);
    }

    if (options.closurePass) {
      checks.add(closurePrimitives.makeOneTimePass());
    }

    if (options.closurePass && options.checkMissingGetCssNameLevel.isOn()) {
      checks.add(closureCheckGetCssName);
    }

    if (options.syntheticBlockStartMarker != null) {
      // This pass must run before the first fold constants pass.
      checks.add(createSyntheticBlocks);
    }

    checks.add(checkVars);
    if (options.computeFunctionSideEffects) {
      checks.add(checkRegExp);
    }

    if (options.checkShadowVars.isOn()) {
      checks.add(checkShadowVars);
    }

    if (options.aggressiveVarCheck.isOn()) {
      checks.add(checkVariableReferences);
    }

    // This pass should run before types are assigned.
    if (options.processObjectPropertyString) {
      checks.add(objectPropertyStringPreprocess);
    }

    // DiagnosticGroups override the plain checkTypes option.
    if (options.enables(DiagnosticGroups.CHECK_TYPES)) {
      options.checkTypes = true;
    } else if (options.disables(DiagnosticGroups.CHECK_TYPES)) {
      options.checkTypes = false;
    }

    if (options.checkTypes) {
      checks.add(resolveTypes.makeOneTimePass());
      checks.add(inferTypes.makeOneTimePass());
      checks.add(checkTypes.makeOneTimePass());
    }

    if (options.checkUnreachableCode.isOn() ||
        (options.checkTypes && options.checkMissingReturn.isOn())) {
      checks.add(checkControlFlow);
    }

    // CheckAccessControls only works if check types is on.
    if (options.enables(DiagnosticGroups.ACCESS_CONTROLS)
        && options.checkTypes) {
      checks.add(checkAccessControls);
    }

    if (options.checkGlobalNamesLevel.isOn()) {
      checks.add(checkGlobalNames);
    }

    if (options.checkUndefinedProperties.isOn() ||
        options.checkUnusedPropertiesEarly) {
      checks.add(checkSuspiciousProperties);
    }

    if (options.checkCaja || options.checkEs5Strict) {
      checks.add(checkStrictMode);
    }

    // Replace 'goog.getCssName' before processing defines but after the
    // other checks have been done.
    if (options.closurePass) {
      checks.add(closureReplaceGetCssName);
    }

    // i18n
    // If you want to customize the compiler to use a different i18n pass,
    // you can create a PassConfig that calls replacePassFactory
    // to replace this.
    checks.add(options.messageBundle != null ?
        replaceMessages : createEmptyPass("replaceMessages"));

    // Defines in code always need to be processed.
    checks.add(processDefines);

    if (options.instrumentationTemplate != null ||
        options.recordFunctionInformation) {
      checks.add(computeFunctionNames);
    }

    if (options.nameReferenceGraphPath != null &&
        !options.nameReferenceGraphPath.isEmpty()) {
      checks.add(printNameReferenceGraph);
    }

    if (options.nameReferenceReportPath != null &&
        !options.nameReferenceReportPath.isEmpty()) {
      checks.add(printNameReferenceReport);
    }

    assertAllOneTimePasses(checks);
    return checks;
  }

  @Override
  protected List<PassFactory> getOptimizations() {
    List<PassFactory> passes = Lists.newArrayList();

    // TODO(nicksantos): The order of these passes makes no sense, and needs
    // to be re-arranged.

    if (options.runtimeTypeCheck) {
      passes.add(runtimeTypeCheck);
    }

    passes.add(createEmptyPass("beforeStandardOptimizations"));

    if (!options.idGenerators.isEmpty()) {
      passes.add(replaceIdGenerators);
    }

    // Optimizes references to the arguments variable.
    if (options.optimizeArgumentsArray) {
      passes.add(optimizeArgumentsArray);
    }

    // Abstract method removal works best on minimally modified code, and also
    // only needs to run once.
    if (options.closurePass &&
        (options.removeAbstractMethods || options.removeClosureAsserts)) {
      passes.add(closureCodeRemoval);
    }

    // Collapsing properties can undo constant inlining, so we do this before
    // the main optimization loop.
    if (options.collapseProperties) {
      passes.add(collapseProperties);
    }

    // ReplaceStrings runs after CollapseProperties in order to simplify
    // pulling in values of constants defined in enums structures.
    if (!options.replaceStringsFunctionDescriptions.isEmpty()) {
      passes.add(replaceStrings);
    }

    // Tighten types based on actual usage.
    if (options.tightenTypes) {
      passes.add(tightenTypesBuilder);
    }

    // Property disambiguation should only run once and needs to be done
    // soon after type checking, both so that it can make use of type
    // information and so that other passes can take advantage of the renamed
    // properties.
    if (options.disambiguateProperties) {
      passes.add(disambiguateProperties);
    }

    if (options.computeFunctionSideEffects) {
      passes.add(markPureFunctions);
    } else if (options.markNoSideEffectCalls) {
      // TODO(user) The properties that this pass adds to CALL and NEW
      // AST nodes increase the AST's in-memory size.  Given that we are
      // already running close to our memory limits, we could run into
      // trouble if we end up using the @nosideeffects annotation a lot
      // or compute @nosideeffects annotations by looking at function
      // bodies.  It should be easy to propagate @nosideeffects
      // annotations as part of passes that depend on this property and
      // store the result outside the AST (which would allow garbage
      // collection once the pass is done).
      passes.add(markNoSideEffectCalls);
    }

    if (options.chainCalls) {
      passes.add(chainCalls);
    }

    // Constant checking must be done after property collapsing because
    // property collapsing can introduce new constants (e.g. enum values).
    if (options.inlineConstantVars) {
      passes.add(checkConsts);
    }

    // The Caja library adds properties to Object.prototype, which breaks
    // most for-in loops.  This adds a check to each loop that skips
    // any property matching /___$/.
    if (options.ignoreCajaProperties) {
      passes.add(ignoreCajaProperties);
    }

    assertAllOneTimePasses(passes);

    if (options.smartNameRemoval || options.reportPath != null) {
      passes.addAll(getCodeRemovingPasses());
      passes.add(smartNamePass);
    }

    // TODO(user): This forces a first crack at crossModuleCodeMotion
    // before devirtualization. Once certain functions are devirtualized,
    // it confuses crossModuleCodeMotion ability to recognized that
    // it is recursive.

    // TODO(user): This is meant for a temporary quick win.
    // In the future, we might want to improve our analysis in
    // CrossModuleCodeMotion so we don't need to do this.
    if (options.crossModuleCodeMotion) {
      passes.add(crossModuleCodeMotion);
    }

    // Method devirtualization benefits from property disambiguiation so
    // it should run after that pass but before passes that do
    // optimizations based on global names (like cross module code motion
    // and inline functions).  Smart Name Removal does better if run before
    // this pass.
    if (options.devirtualizePrototypeMethods) {
      passes.add(devirtualizePrototypeMethods);
    }

    // Running "optimizeCalls" after devirtualization is useful for removing
    // unneeded "this" values.
    if (options.optimizeCalls
        || options.optimizeParameters
        || options.optimizeReturns) {
      passes.add(optimizeCalls);
    }

    if (options.customPasses != null) {
      passes.add(getCustomPasses(
          CustomPassExecutionTime.BEFORE_OPTIMIZATION_LOOP));
    }

    passes.add(createEmptyPass("beforeMainOptimizations"));

    if (options.specializeInitialModule) {
      // When specializing the initial module, we want our fixups to be
      // as lean as possible, so we run the entire optimization loop to a
      // fixed point before specializing, then specialize, and then run the
      // main optimization loop again.

      passes.addAll(getMainOptimizationLoop());

      if (options.crossModuleCodeMotion) {
        passes.add(crossModuleCodeMotion);
      }

      if (options.crossModuleMethodMotion) {
        passes.add(crossModuleMethodMotion);
      }

      passes.add(specializeInitialModule.makeOneTimePass());
    }

    passes.addAll(getMainOptimizationLoop());

    passes.add(createEmptyPass("beforeModuleMotion"));

    if (options.crossModuleCodeMotion) {
      passes.add(crossModuleCodeMotion);
    }

    if (options.crossModuleMethodMotion) {
      passes.add(crossModuleMethodMotion);
    }

    passes.add(createEmptyPass("afterModuleMotion"));

    // Some optimizations belong outside the loop because running them more
    // than once would either have no benefit or be incorrect.
    if (options.customPasses != null) {
      passes.add(getCustomPasses(
          CustomPassExecutionTime.AFTER_OPTIMIZATION_LOOP));
    }

    if (options.flowSensitiveInlineVariables) {
      passes.add(flowSensitiveInlineVariables);

      // After inlining some of the variable uses, some variables are unused.
      // Re-run remove unused vars to clean it up.
      if (options.removeUnusedVars) {
        passes.add(removeUnusedVars);
      }
    }

    if (options.collapseAnonymousFunctions) {
      passes.add(collapseAnonymousFunctions);
    }

    // Move functions before extracting prototype member declarations.
    if (options.moveFunctionDeclarations) {
      passes.add(moveFunctionDeclarations);
    }

    if (options.anonymousFunctionNaming ==
        AnonymousFunctionNamingPolicy.MAPPED) {
      passes.add(nameMappedAnonymousFunctions);
    }

    // The mapped name anonymous function pass makes use of information that
    // the extract prototype member declarations pass removes so the former
    // happens before the latter.
    //
    // Extracting prototype properties screws up the heuristic renaming
    // policies, so never run it when those policies are requested.
    if (options.extractPrototypeMemberDeclarations &&
        (options.propertyRenaming != PropertyRenamingPolicy.HEURISTIC &&
         options.propertyRenaming !=
            PropertyRenamingPolicy.AGGRESSIVE_HEURISTIC)) {
      passes.add(extractPrototypeMemberDeclarations);
    }

    if (options.ambiguateProperties &&
        (options.propertyRenaming == PropertyRenamingPolicy.ALL_UNQUOTED)) {
      passes.add(ambiguateProperties);
    }

    if (options.propertyRenaming != PropertyRenamingPolicy.OFF) {
      passes.add(renameProperties);
    }

    // Reserve global names added to the "windows" object.
    if (options.reserveRawExports) {
      passes.add(gatherRawExports);
    }

    // This comes after property renaming because quoted property names must
    // not be renamed.
    if (options.convertToDottedProperties) {
      passes.add(convertToDottedProperties);
    }

    // Property renaming must happen before this pass runs since this
    // pass may convert dotted properties into quoted properties.  It
    // is beneficial to run before alias strings, alias keywords and
    // variable renaming.
    if (options.rewriteFunctionExpressions) {
      passes.add(rewriteFunctionExpressions);
    }

    // This comes after converting quoted property accesses to dotted property
    // accesses in order to avoid aliasing property names.
    if (!options.aliasableStrings.isEmpty() || options.aliasAllStrings) {
      passes.add(aliasStrings);
    }

    if (options.aliasExternals) {
      passes.add(aliasExternals);
    }

    if (options.aliasKeywords) {
      passes.add(aliasKeywords);
    }

    // Passes after this point can no longer depend on normalized AST
    // assumptions.
    passes.add(markUnnormalized);

    if (options.coalesceVariableNames) {
      passes.add(coalesceVariableNames);
    }

    if (options.collapseVariableDeclarations) {
      passes.add(collapseVariableDeclarations);
    }

    // This pass works best after collapseVariableDeclarations.
    passes.add(denormalize);

    if (options.instrumentationTemplate != null) {
      passes.add(instrumentFunctions);
    }

    if (options.variableRenaming != VariableRenamingPolicy.ALL) {
      // If we're leaving some (or all) variables with their old names,
      // then we need to undo any of the markers we added for distinguishing
      // local variables ("$$1").
      passes.add(invertContextualRenaming);
    }

    if (options.variableRenaming != VariableRenamingPolicy.OFF) {
      passes.add(renameVars);
    }

    if (options.groupVariableDeclarations) {
      passes.add(groupVariableDeclarations);
    }

    // This pass should run after names stop changing.
    if (options.processObjectPropertyString) {
      passes.add(objectPropertyStringPostprocess);
    }

    if (options.labelRenaming) {
      passes.add(renameLabels);
    }

    if (options.anonymousFunctionNaming ==
        AnonymousFunctionNamingPolicy.UNMAPPED) {
      passes.add(nameUnmappedAnonymousFunctions);
    }

    // Safety check
    if (options.checkSymbols) {
      passes.add(sanityCheckVars);
    }

    return passes;
  }

  /** Creates the passes for the main optimization loop. */
  private List<PassFactory> getMainOptimizationLoop() {
    List<PassFactory> passes = Lists.newArrayList();
    if (options.inlineGetters) {
      passes.add(inlineGetters);
    }

    passes.addAll(getCodeRemovingPasses());

    if (options.inlineFunctions || options.inlineLocalFunctions) {
      passes.add(inlineFunctions);
    }

    if (options.removeUnusedVars || options.removeUnusedLocalVars) {
      if (options.deadAssignmentElimination) {
        passes.add(deadAssignmentsElimination);
      }
      passes.add(removeUnusedVars);
    }
    assertAllLoopablePasses(passes);
    return passes;
  }

  /** Creates several passes aimed at removing code. */
  private List<PassFactory> getCodeRemovingPasses() {
    List<PassFactory> passes = Lists.newArrayList();
    if (options.inlineVariables || options.inlineLocalVariables) {
      passes.add(inlineVariables);
    } else if (options.inlineConstantVars) {
      passes.add(inlineConstants);
    }

    if (options.foldConstants) {
      // These used to be one pass.
      passes.add(minimizeExitPoints);
      passes.add(peepholeOptimizations);
    }

    if (options.removeDeadCode) {
      passes.add(removeUnreachableCode);
    }

    if (options.removeUnusedPrototypeProperties) {
      passes.add(removeUnusedPrototypeProperties);
    }

    assertAllLoopablePasses(passes);
    return passes;
  }

  /**
   * Checks for code that is probably wrong (such as stray expressions).
   */
  // TODO(bolinfest): Write a CompilerPass for this.
  final PassFactory suspiciousCode =
      new PassFactory("suspiciousCode", true) {

    @Override
    protected CompilerPass createInternal(final AbstractCompiler compiler) {
      List<Callback> sharedCallbacks = Lists.newArrayList();
      if (options.checkSuspiciousCode) {
        sharedCallbacks.add(new CheckAccidentalSemicolon(CheckLevel.WARNING));
        sharedCallbacks.add(new CheckSideEffects(CheckLevel.WARNING));
      }

      CheckLevel checkGlobalThisLevel = options.checkGlobalThisLevel;
      if (checkGlobalThisLevel.isOn()) {
        sharedCallbacks.add(
            new CheckGlobalThis(compiler, checkGlobalThisLevel));
      }
      return combineChecks(compiler, sharedCallbacks);
    }

  };

  /** Verify that all the passes are one-time passes. */
  private void assertAllOneTimePasses(List<PassFactory> passes) {
    for (PassFactory pass : passes) {
      Preconditions.checkState(pass.isOneTimePass());
    }
  }

  /** Verify that all the passes are multi-run passes. */
  private void assertAllLoopablePasses(List<PassFactory> passes) {
    for (PassFactory pass : passes) {
      Preconditions.checkState(!pass.isOneTimePass());
    }
  }

  /** Checks for validity of the control structures. */
  private final PassFactory checkControlStructures =
      new PassFactory("checkControlStructures", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new ControlStructureCheck(compiler);
    }
  };

  /** Checks that all constructed classes are goog.require()d. */
  private final PassFactory checkRequires =
      new PassFactory("checkRequires", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new CheckRequiresForConstructors(compiler, options.checkRequires);
    }
  };

  /** Makes sure @constructor is paired with goog.provides(). */
  private final PassFactory checkProvides =
      new PassFactory("checkProvides", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new CheckProvides(compiler, options.checkProvides);
    }
  };

  private static final DiagnosticType GENERATE_EXPORTS_ERROR =
      DiagnosticType.error(
          "JSC_GENERATE_EXPORTS_ERROR",
          "Exports can only be generated if export symbol/property " +
          "functions are set.");

  /** Generates exports for @export annotations. */
  private final PassFactory generateExports =
      new PassFactory("generateExports", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      CodingConvention convention = compiler.getCodingConvention();
      if (convention.getExportSymbolFunction() != null &&
          convention.getExportPropertyFunction() != null) {
        return new GenerateExports(compiler,
            convention.getExportSymbolFunction(),
            convention.getExportPropertyFunction());
      } else {
        return new ErrorPass(compiler, GENERATE_EXPORTS_ERROR);
      }
    }
  };

  /** Generates exports for functions associated with JSUnit. */
  private final PassFactory exportTestFunctions =
      new PassFactory("exportTestFunctions", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      CodingConvention convention = compiler.getCodingConvention();
      if (convention.getExportSymbolFunction() != null) {
        return new ExportTestFunctions(compiler,
            convention.getExportSymbolFunction());
      } else {
        return new ErrorPass(compiler, GENERATE_EXPORTS_ERROR);
      }
    }
  };

  /** Raw exports processing pass. */
  final PassFactory gatherRawExports =
      new PassFactory("gatherRawExports", false) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      final GatherRawExports pass = new GatherRawExports(
          compiler);

      return new CompilerPass() {
        @Override
        public void process(Node externs, Node root) {
          pass.process(externs, root);
          if (exportedNames == null) {
            exportedNames = Sets.newHashSet();
          }
          exportedNames.addAll(pass.getExportedVariableNames());
        }
      };
    }
  };

  /** Closure pre-processing pass. */
  @SuppressWarnings("deprecation")
  final PassFactory closurePrimitives =
      new PassFactory("processProvidesAndRequires", false) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      final ProcessClosurePrimitives pass = new ProcessClosurePrimitives(
          compiler,
          options.brokenClosureRequiresLevel,
          options.rewriteNewDateGoogNow);

      return new CompilerPass() {
        @Override
        public void process(Node externs, Node root) {
          pass.process(externs, root);
          exportedNames = pass.getExportedVariableNames();
        }
      };
    }
  };

  /**
   * The default i18n pass.
   * A lot of the options are not configurable, because ReplaceMessages
   * has a lot of legacy logic.
   */
  private final PassFactory replaceMessages =
      new PassFactory("replaceMessages", true) {
    @Override
    protected CompilerPass createInternal(final AbstractCompiler compiler) {
      return new ReplaceMessages(compiler,
          options.messageBundle,
          /* warn about message dupes */
          true,
          /* allow messages with goog.getMsg */
          JsMessage.Style.getFromParams(true, false),
          /* if we can't find a translation, don't worry about it. */
          false);
    }
  };

  /** Applies aliases and inlines goog.scope. */
  final PassFactory closureGoogScopeAliases =
      new PassFactory("processGoogScopeAliases", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new ScopedAliases(compiler);
    }
  };

  /** Checks that CSS class names are wrapped in goog.getCssName */
  private final PassFactory closureCheckGetCssName =
      new PassFactory("checkMissingGetCssName", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      String blacklist = options.checkMissingGetCssNameBlacklist;
      Preconditions.checkState(blacklist != null && !blacklist.isEmpty(),
          "Not checking use of goog.getCssName because of empty blacklist.");
      return new CheckMissingGetCssName(
          compiler, options.checkMissingGetCssNameLevel, blacklist);
    }
  };

  /**
   * Processes goog.getCssName.  The cssRenamingMap is used to lookup
   * replacement values for the classnames.  If null, the raw class names are
   * inlined.
   */
  private final PassFactory closureReplaceGetCssName =
      new PassFactory("renameCssNames", true) {
    @Override
    protected CompilerPass createInternal(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override
        public void process(Node externs, Node jsRoot) {
          Map<String, Integer> newCssNames = null;
          if (options.gatherCssNames) {
            newCssNames = Maps.newHashMap();
          }
          (new ReplaceCssNames(compiler, newCssNames)).process(
              externs, jsRoot);
          cssNames = newCssNames;
        }
      };
    }
  };

  /**
   * Creates synthetic blocks to prevent FoldConstants from moving code
   * past markers in the source.
   */
  private final PassFactory createSyntheticBlocks =
      new PassFactory("createSyntheticBlocks", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new CreateSyntheticBlocks(compiler,
          options.syntheticBlockStartMarker,
          options.syntheticBlockEndMarker);
    }
  };

  /** Various peephole optimizations. */
  private final PassFactory peepholeOptimizations =
      new PassFactory("peepholeOptimizations", false) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new PeepholeOptimizationsPass(compiler,
            new PeepholeSubstituteAlternateSyntax(),
            new PeepholeRemoveDeadCode(),
            new PeepholeFoldConstants());
    }
  };

  /** Checks that all variables are defined. */
  private final PassFactory checkVars =
      new PassFactory("checkVars", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new VarCheck(compiler);
    }
  };

  /** Checks for RegExp references. */
  private final PassFactory checkRegExp =
      new PassFactory("checkRegExp", true) {
    @Override
    protected CompilerPass createInternal(final AbstractCompiler compiler) {
      final CheckRegExp pass = new CheckRegExp(compiler);

      return new CompilerPass() {
        @Override
        public void process(Node externs, Node root) {
          pass.process(externs, root);
          compiler.setHasRegExpGlobalReferences(
              pass.isGlobalRegExpPropertiesUsed());
        }
      };
    }
  };

  /** Checks that no vars are illegally shadowed. */
  private final PassFactory checkShadowVars =
      new PassFactory("variableShadowDeclarationCheck", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new VariableShadowDeclarationCheck(
          compiler, options.checkShadowVars);
    }
  };

  /** Checks that references to variables look reasonable. */
  private final PassFactory checkVariableReferences =
      new PassFactory("checkVariableReferences", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new VariableReferenceCheck(
          compiler, options.aggressiveVarCheck);
    }
  };

  /** Pre-process goog.testing.ObjectPropertyString. */
  private final PassFactory objectPropertyStringPreprocess =
      new PassFactory("ObjectPropertyStringPreprocess", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new ObjectPropertyStringPreprocess(compiler);
    }
  };

  /** Creates a typed scope and adds types to the type registry. */
  final PassFactory resolveTypes =
      new PassFactory("resolveTypes", false) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new GlobalTypeResolver(compiler);
    }
  };

  /** Rusn type inference. */
  final PassFactory inferTypes =
      new PassFactory("inferTypes", false) {
    @Override
    protected CompilerPass createInternal(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override
        public void process(Node externs, Node root) {
          Preconditions.checkNotNull(topScope);
          Preconditions.checkNotNull(typedScopeCreator);

          makeTypeInference(compiler).process(externs, root);
        }
      };
    }
  };

  /** Checks type usage */
  private final PassFactory checkTypes =
      new PassFactory("checkTypes", false) {
    @Override
    protected CompilerPass createInternal(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override
        public void process(Node externs, Node root) {
          Preconditions.checkNotNull(topScope);
          Preconditions.checkNotNull(typedScopeCreator);

          TypeCheck check = makeTypeCheck(compiler);
          check.process(externs, root);
          compiler.getErrorManager().setTypedPercent(check.getTypedPercent());
        }
      };
    }
  };

  /**
   * Checks possible execution paths of the program for problems: missing return
   * statements and dead code.
   */
  private final PassFactory checkControlFlow =
      new PassFactory("checkControlFlow", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      List<Callback> callbacks = Lists.newArrayList();
      if (options.checkUnreachableCode.isOn()) {
        callbacks.add(
            new CheckUnreachableCode(compiler, options.checkUnreachableCode));
      }
      if (options.checkMissingReturn.isOn() && options.checkTypes) {
        callbacks.add(
            new CheckMissingReturn(compiler, options.checkMissingReturn));
      }
      return combineChecks(compiler, callbacks);
    }
  };

  /** Checks access controls. Depends on type-inference. */
  private final PassFactory checkAccessControls =
      new PassFactory("checkAccessControls", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new CheckAccessControls(compiler);
    }
  };

  /** Executes the given callbacks with a {@link CombinedCompilerPass}. */
  private static CompilerPass combineChecks(AbstractCompiler compiler,
      List<Callback> callbacks) {
    Preconditions.checkArgument(callbacks.size() > 0);
    Callback[] array = callbacks.toArray(new Callback[callbacks.size()]);
    return new CombinedCompilerPass(compiler, array);
  }

  /** A compiler pass that resolves types in the global scope. */
  private class GlobalTypeResolver implements CompilerPass {
    private final AbstractCompiler compiler;

    GlobalTypeResolver(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void process(Node externs, Node root) {
      if (topScope == null) {
        typedScopeCreator =
            new MemoizedScopeCreator(new TypedScopeCreator(compiler));
        topScope = typedScopeCreator.createScope(root.getParent(), null);
      } else {
        compiler.getTypeRegistry().resolveTypesInScope(topScope);
      }
    }
  }

  /** Checks global name usage. */
  private final PassFactory checkGlobalNames =
      new PassFactory("Check names", true) {
    @Override
    protected CompilerPass createInternal(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override
        public void process(Node externs, Node jsRoot) {
          // Create a global namespace for analysis by check passes.
          // Note that this class does all heavy computation lazily,
          // so it's OK to create it here.
          namespaceForChecks = new GlobalNamespace(compiler, jsRoot);
          new CheckGlobalNames(compiler, options.checkGlobalNamesLevel)
              .injectNamespace(namespaceForChecks).process(externs, jsRoot);
        }
      };
    }
  };

  /** Checks for properties that are not read or written */
  private final PassFactory checkSuspiciousProperties =
      new PassFactory("checkSuspiciousProperties", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new SuspiciousPropertiesCheck(
          compiler,
          options.checkUndefinedProperties,
          options.checkUnusedPropertiesEarly ?
              CheckLevel.WARNING : CheckLevel.OFF);
    }
  };

  /** Checks that the code is ES5 or Caja compliant. */
  private final PassFactory checkStrictMode =
      new PassFactory("checkStrictMode", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new StrictModeCheck(compiler,
          !options.checkSymbols,  // don't check variables twice
          !options.checkCaja);    // disable eval check if not Caja
    }
  };

  /** Override @define-annotated constants. */
  final PassFactory processDefines =
      new PassFactory("processDefines", true) {
    @Override
    protected CompilerPass createInternal(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override
        public void process(Node externs, Node jsRoot) {
          Map<String, Node> replacements = getAdditionalReplacements(options);
          replacements.putAll(options.getDefineReplacements());

          new ProcessDefines(compiler, replacements)
              .injectNamespace(namespaceForChecks).process(externs, jsRoot);

          // Kill the namespace in the other class
          // so that it can be garbage collected after all passes
          // are through with it.
          namespaceForChecks = null;
        }
      };
    }
  };

  /** Checks that all constants are not modified */
  private final PassFactory checkConsts =
      new PassFactory("checkConsts", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new ConstCheck(compiler);
    }
  };

  /** Computes the names of functions for later analysis. */
  private final PassFactory computeFunctionNames =
      new PassFactory("computeFunctionNames", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return ((functionNames = new FunctionNames(compiler)));
    }
  };

  /** Skips Caja-private properties in for-in loops */
  private final PassFactory ignoreCajaProperties =
      new PassFactory("ignoreCajaProperties", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new IgnoreCajaProperties(compiler);
    }
  };

  /** Inserts runtime type assertions for debugging. */
  private final PassFactory runtimeTypeCheck =
      new PassFactory("runtimeTypeCheck", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new RuntimeTypeCheck(compiler,
          options.runtimeTypeCheckLogFunction);
    }
  };

  /** Generates unique ids. */
  private final PassFactory replaceIdGenerators =
      new PassFactory("replaceIdGenerators", true) {
    @Override
    protected CompilerPass createInternal(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override public void process(Node externs, Node root) {
          ReplaceIdGenerators pass =
              new ReplaceIdGenerators(compiler, options.idGenerators);
          pass.process(externs, root);
          idGeneratorMap = pass.getIdGeneratorMap();
        }
      };
    }
  };

  /** Replace strings. */
  private final PassFactory replaceStrings =
      new PassFactory("replaceStrings", true) {
    @Override
    protected CompilerPass createInternal(final AbstractCompiler compiler) {
      VariableMap map = null;
      return new CompilerPass() {
        @Override public void process(Node externs, Node root) {
          ReplaceStrings pass = new ReplaceStrings(
              compiler,
              options.replaceStringsPlaceholderToken,
              options.replaceStringsFunctionDescriptions);
          pass.process(externs, root);
          stringMap = pass.getStringMap();
        }
      };
    }
  };

  /** Optimizes the "arguments" array. */
  private final PassFactory optimizeArgumentsArray =
      new PassFactory("optimizeArgumentsArray", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new OptimizeArgumentsArray(compiler);
    }
  };

  /** Remove variables set to goog.abstractMethod. */
  private final PassFactory closureCodeRemoval =
      new PassFactory("closureCodeRemoval", true) {
    @Override
    protected CompilerPass createInternal(final AbstractCompiler compiler) {
      return new ClosureCodeRemoval(compiler, options.removeAbstractMethods,
          options.removeClosureAsserts);
    }
  };

  /** Collapses names in the global scope. */
  private final PassFactory collapseProperties =
      new PassFactory("collapseProperties", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new CollapseProperties(
          compiler, options.collapsePropertiesOnExternTypes,
          !isInliningForbidden());
    }
  };

  /**
   * Try to infer the actual types, which may be narrower
   * than the declared types.
   */
  private final PassFactory tightenTypesBuilder =
      new PassFactory("tightenTypes", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      if (!options.checkTypes) {
        return new ErrorPass(compiler, TIGHTEN_TYPES_WITHOUT_TYPE_CHECK);
      }
      tightenTypes = new TightenTypes(compiler);
      return tightenTypes;
    }
  };

  /** Devirtualize property names based on type information. */
  private final PassFactory disambiguateProperties =
      new PassFactory("disambiguateProperties", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      if (tightenTypes == null) {
        return DisambiguateProperties.forJSTypeSystem(compiler);
      } else {
        return DisambiguateProperties.forConcreteTypeSystem(
            compiler, tightenTypes);
      }
    }
  };

  /**
   * Chain calls to functions that return this.
   */
  private final PassFactory chainCalls =
      new PassFactory("chainCalls", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new ChainCalls(compiler);
    }
  };

  /**
   * Rewrite instance methods as static methods, to make them easier
   * to inline.
   */
  private final PassFactory devirtualizePrototypeMethods =
      new PassFactory("devirtualizePrototypeMethods", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new DevirtualizePrototypeMethods(compiler);
    }
  };

  /**
   * Rewrite instance methods as static methods, to make them easier
   * to inline.
   */
  private final PassFactory optimizeCalls =
      new PassFactory("optimizeCalls", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      OptimizeCalls passes = new OptimizeCalls(compiler);
      if (options.optimizeReturns) {
        // Remove unused return values.
        passes.addPass(new OptimizeReturns(compiler));
      }

      if (options.optimizeParameters) {
        // Remove all parameters that are constants or unused.
        passes.addPass(new OptimizeParameters(compiler));
      }

      if (options.optimizeCalls) {
        passes.addPass(new RemoveUnusedVars(compiler, false, true, true));
      }
      return passes;
    }
  };

  /**
   * Look for function calls that are pure, and annotate them
   * that way.
   */
  private final PassFactory markPureFunctions =
      new PassFactory("markPureFunctions", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new PureFunctionIdentifier.Driver(
          compiler, options.debugFunctionSideEffectsPath, false);
    }
  };

  /**
   * Look for function calls that have no side effects, and annotate them
   * that way.
   */
  private final PassFactory markNoSideEffectCalls =
      new PassFactory("markNoSideEffectCalls", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new MarkNoSideEffectCalls(compiler);
    }
  };

  /** Inlines variables heuristically. */
  private final PassFactory inlineVariables =
      new PassFactory("inlineVariables", false) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      if (isInliningForbidden()) {
        // In old renaming schemes, inlining a variable can change whether
        // or not a property is renamed. This is bad, and those old renaming
        // schemes need to die.
        return new ErrorPass(compiler, CANNOT_USE_PROTOTYPE_AND_VAR);
      } else {
        InlineVariables.Mode mode;
        if (options.inlineVariables) {
          mode = InlineVariables.Mode.ALL;
        } else if (options.inlineLocalVariables) {
          mode = InlineVariables.Mode.LOCALS_ONLY;
        } else {
          throw new IllegalStateException("No variable inlining option set.");
        }

        return new InlineVariables(compiler, mode, true);
      }
    }
  };

  /** Inlines variables that are marked as constants. */
  private final PassFactory inlineConstants =
      new PassFactory("inlineConstants", false) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new InlineVariables(
          compiler, InlineVariables.Mode.CONSTANTS_ONLY, true);
    }
  };

  /**
   * Perform local control flow optimizations.
   */
  private final PassFactory minimizeExitPoints =
      new PassFactory("minimizeExitPoints", false) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new MinimizeExitPoints(compiler);
    }
  };

  /**
   * Use data flow analysis to remove dead branches.
   */
  private final PassFactory removeUnreachableCode =
      new PassFactory("removeUnreachableCode", false) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new UnreachableCodeElimination(compiler, true);
    }
  };

  /**
   * Remove prototype properties that do not appear to be used.
   */
  private final PassFactory removeUnusedPrototypeProperties =
      new PassFactory("removeUnusedPrototypeProperties", false) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new RemoveUnusedPrototypeProperties(
          compiler, options.removeUnusedPrototypePropertiesInExterns,
          !options.removeUnusedVars);
    }
  };

  /**
   * Process smart name processing - removes unused classes and does referencing
   * starting with minimum set of names.
   */
  private final PassFactory smartNamePass =
      new PassFactory("smartNamePass", true) {
    @Override
    protected CompilerPass createInternal(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override
        public void process(Node externs, Node root) {
          NameAnalyzer na = new NameAnalyzer(compiler, false);
          na.process(externs, root);

          String reportPath = options.reportPath;
          if (reportPath != null) {
            try {
              Files.write(na.getHtmlReport(), new File(reportPath),
                  Charsets.UTF_8);
            } catch (IOException e) {
              compiler.report(JSError.make(REPORT_PATH_IO_ERROR, reportPath));
            }
          }

          if (options.smartNameRemoval) {
            na.removeUnreferenced();
          }
        }
      };
    }
  };

  /** Inlines simple methods, like getters */
  private PassFactory inlineGetters =
      new PassFactory("inlineGetters", false) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new InlineGetters(compiler);
    }
  };

  /** Kills dead assignments. */
  private PassFactory deadAssignmentsElimination =
      new PassFactory("deadAssignmentsElimination", false) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new DeadAssignmentsElimination(compiler);
    }
  };

  /** Inlines function calls. */
  private PassFactory inlineFunctions =
      new PassFactory("inlineFunctions", false) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      boolean enableBlockInlining = !isInliningForbidden();
      return new InlineFunctions(
          compiler,
          compiler.getUniqueNameIdSupplier(),
          options.inlineFunctions,
          options.inlineLocalFunctions,
          enableBlockInlining);
    }
  };

  /** Removes variables that are never used. */
  private PassFactory removeUnusedVars =
      new PassFactory("removeUnusedVars", false) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      boolean removeOnlyLocals = options.removeUnusedLocalVars
          && !options.removeUnusedVars;
      boolean preserveAnonymousFunctionNames =
          options.anonymousFunctionNaming != AnonymousFunctionNamingPolicy.OFF;
      return new RemoveUnusedVars(
          compiler,
          !removeOnlyLocals,
          preserveAnonymousFunctionNames,
          false);
    }
  };

  /**
   * Move global symbols to a deeper common module
   */
  private PassFactory crossModuleCodeMotion =
      new PassFactory("crossModuleCodeMotion", false) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new CrossModuleCodeMotion(compiler, compiler.getModuleGraph());
    }
  };

  /**
   * Move methods to a deeper common module
   */
  private PassFactory crossModuleMethodMotion =
      new PassFactory("crossModuleMethodMotion", false) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new CrossModuleMethodMotion(
          compiler, crossModuleIdGenerator,
          // Only move properties in externs if we're not treating
          // them as exports.
          options.removeUnusedPrototypePropertiesInExterns);
    }
  };

  /**
   * Specialize the initial module at the cost of later modules
   */
  private PassFactory specializeInitialModule =
      new PassFactory("specializeInitialModule", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new SpecializeModule(compiler, devirtualizePrototypeMethods,
          inlineFunctions, removeUnusedPrototypeProperties);
    }
  };

  /** A data-flow based variable inliner. */
  private final PassFactory flowSensitiveInlineVariables =
      new PassFactory("flowSensitiveInlineVariables", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new FlowSensitiveInlineVariables(compiler);
    }
  };

  /** Uses register-allocation algorithms to use fewer variables. */
  private final PassFactory coalesceVariableNames =
      new PassFactory("coalesceVariableNames", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new CoalesceVariableNames(compiler, options.generatePseudoNames);
    }
  };

  /**
   * Some simple, local collapses (e.g., {@code var x; var y;} becomes
   * {@code var x,y;}.
   */
  private final PassFactory collapseVariableDeclarations =
      new PassFactory("collapseVariableDeclarations", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new CollapseVariableDeclarations(compiler);
    }
  };

  /**
   * Simple global collapses of variable declarations.
   */
  private final PassFactory groupVariableDeclarations =
      new PassFactory("groupVariableDeclarations", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new GroupVariableDeclarations(compiler);
    }
  };

  /**
   * Extracts common sub-expressions.
   */
  private final PassFactory extractPrototypeMemberDeclarations =
      new PassFactory("extractPrototypeMemberDeclarations", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new ExtractPrototypeMemberDeclarations(compiler);
    }
  };

  /** Rewrites common function definitions to be more compact. */
  private final PassFactory rewriteFunctionExpressions =
      new PassFactory("rewriteFunctionExpressions", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new FunctionRewriter(compiler);
    }
  };

  /** Collapses functions to not use the VAR keyword. */
  private final PassFactory collapseAnonymousFunctions =
      new PassFactory("collapseAnonymousFunctions", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new CollapseAnonymousFunctions(compiler);
    }
  };

  /** Moves function declarations to the top, to simulate actual hoisting. */
  private final PassFactory moveFunctionDeclarations =
      new PassFactory("moveFunctionDeclarations", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new MoveFunctionDeclarations(compiler);
    }
  };

  private final PassFactory nameUnmappedAnonymousFunctions =
      new PassFactory("nameAnonymousFunctions", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new NameAnonymousFunctions(compiler);
    }
  };

  private final PassFactory nameMappedAnonymousFunctions =
      new PassFactory("nameAnonymousFunctions", true) {
    @Override
    protected CompilerPass createInternal(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override public void process(Node externs, Node root) {
          NameAnonymousFunctionsMapped naf =
              new NameAnonymousFunctionsMapped(compiler);
          naf.process(externs, root);
          anonymousFunctionNameMap = naf.getFunctionMap();
        }
      };
    }
  };

  /** Alias external symbols. */
  private final PassFactory aliasExternals =
      new PassFactory("aliasExternals", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new AliasExternals(compiler, compiler.getModuleGraph(),
          options.unaliasableGlobals, options.aliasableGlobals);
    }
  };

  /**
   * Alias string literals with global variables, to avoid creating lots of
   * transient objects.
   */
  private final PassFactory aliasStrings =
      new PassFactory("aliasStrings", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new AliasStrings(
          compiler,
          compiler.getModuleGraph(),
          options.aliasAllStrings ? null : options.aliasableStrings,
          options.aliasStringsBlacklist,
          options.outputJsStringUsage);
    }
  };

  /** Aliases common keywords (true, false) */
  private final PassFactory aliasKeywords =
      new PassFactory("aliasKeywords", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new AliasKeywords(compiler);
    }
  };

  /** Handling for the ObjectPropertyString primitive. */
  private final PassFactory objectPropertyStringPostprocess =
      new PassFactory("ObjectPropertyStringPostprocess", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new ObjectPropertyStringPostprocess(compiler);
    }
  };

  /**
   * Renames properties so that the two properties that never appear on
   * the same object get the same name.
   */
  private final PassFactory ambiguateProperties =
      new PassFactory("ambiguateProperties", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new AmbiguateProperties(
          compiler, options.anonymousFunctionNaming.getReservedCharacters());
    }
  };

  /**
   * Mark the point at which the normalized AST assumptions no longer hold.
   */
  private final PassFactory markUnnormalized =
      new PassFactory("markUnnormalized", true) {
    @Override
    protected CompilerPass createInternal(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override public void process(Node externs, Node root) {
          compiler.setUnnormalized();
        }
      };
    }
  };

  /** Denormalize the AST for code generation. */
  private final PassFactory denormalize =
      new PassFactory("denormalize", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new Denormalize(compiler);
    }
  };

  /** Inverting name normalization. */
  private final PassFactory invertContextualRenaming =
      new PassFactory("invertNames", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return MakeDeclaredNamesUnique.getContextualRenameInverter(compiler);
    }
  };

  /**
   * Renames properties.
   */
  private final PassFactory renameProperties =
      new PassFactory("renameProperties", true) {
    @Override
    protected CompilerPass createInternal(final AbstractCompiler compiler) {
      VariableMap map = null;
      if (options.inputPropertyMapSerialized != null) {
        try {
          map = VariableMap.fromBytes(options.inputPropertyMapSerialized);
        } catch (ParseException e) {
          return new ErrorPass(compiler,
              JSError.make(INPUT_MAP_PROP_PARSE, e.getMessage()));
        }
      }

      final VariableMap prevPropertyMap = map;
      return new CompilerPass() {
        @Override public void process(Node externs, Node root) {
          propertyMap = runPropertyRenaming(
              compiler, prevPropertyMap, externs, root);
        }
      };
    }
  };

  private VariableMap runPropertyRenaming(
      AbstractCompiler compiler, VariableMap prevPropertyMap,
      Node externs, Node root) {
    char[] reservedChars =
        options.anonymousFunctionNaming.getReservedCharacters();
    switch (options.propertyRenaming) {
      case HEURISTIC:
        RenamePrototypes rproto = new RenamePrototypes(compiler, false,
            reservedChars, prevPropertyMap);
        rproto.process(externs, root);
        return rproto.getPropertyMap();

      case AGGRESSIVE_HEURISTIC:
        RenamePrototypes rproto2 = new RenamePrototypes(compiler, true,
            reservedChars, prevPropertyMap);
        rproto2.process(externs, root);
        return rproto2.getPropertyMap();

      case ALL_UNQUOTED:
        RenameProperties rprop = new RenameProperties(
            compiler, options.generatePseudoNames, prevPropertyMap,
            reservedChars);
        rprop.process(externs, root);
        return rprop.getPropertyMap();

      default:
        throw new IllegalStateException(
            "Unrecognized property renaming policy");
    }
  }

  /** Renames variables. */
  private final PassFactory renameVars =
      new PassFactory("renameVars", true) {
    @Override
    protected CompilerPass createInternal(final AbstractCompiler compiler) {
      VariableMap map = null;
      if (options.inputVariableMapSerialized != null) {
        try {
          map = VariableMap.fromBytes(options.inputVariableMapSerialized);
        } catch (ParseException e) {
          return new ErrorPass(compiler,
              JSError.make(INPUT_MAP_VAR_PARSE, e.getMessage()));
        }
      }

      final VariableMap prevVariableMap = map;
      return new CompilerPass() {
        @Override public void process(Node externs, Node root) {
          variableMap = runVariableRenaming(
              compiler, prevVariableMap, externs, root);
        }
      };
    }
  };

  private VariableMap runVariableRenaming(
      AbstractCompiler compiler, VariableMap prevVariableMap,
      Node externs, Node root) {
    char[] reservedChars =
        options.anonymousFunctionNaming.getReservedCharacters();
    boolean preserveAnonymousFunctionNames =
        options.anonymousFunctionNaming != AnonymousFunctionNamingPolicy.OFF;
    RenameVars rn = new RenameVars(
        compiler,
        options.renamePrefix,
        options.variableRenaming == VariableRenamingPolicy.LOCAL,
        preserveAnonymousFunctionNames,
        options.generatePseudoNames,
        prevVariableMap,
        reservedChars,
        exportedNames);
    rn.process(externs, root);
    return rn.getVariableMap();
  }

  /** Renames labels */
  private final PassFactory renameLabels =
      new PassFactory("renameLabels", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new RenameLabels(compiler);
    }
  };

  /** Convert bracket access to dot access */
  private final PassFactory convertToDottedProperties =
      new PassFactory("convertToDottedProperties", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new ConvertToDottedProperties(compiler);
    }
  };

  /** Checks that all variables are defined. */
  private final PassFactory sanityCheckVars =
      new PassFactory("sanityCheckVars", true) {
    @Override
    protected CompilerPass createInternal(AbstractCompiler compiler) {
      return new VarCheck(compiler, true);
    }
  };

  /** Adds instrumentations according to an instrumentation template. */
  private final PassFactory instrumentFunctions =
      new PassFactory("instrumentFunctions", true) {
    @Override
    protected CompilerPass createInternal(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override public void process(Node externs, Node root) {
          try {
            FileReader templateFile =
                new FileReader(options.instrumentationTemplate);
            (new InstrumentFunctions(
                compiler, functionNames,
                options.instrumentationTemplate,
                options.appNameStr,
                templateFile)).process(externs, root);
          } catch (IOException e) {
            compiler.report(
                JSError.make(AbstractCompiler.READ_ERROR,
                    options.instrumentationTemplate));
          }
        }
      };
    }
  };

  /**
   * Create a no-op pass that can only run once. Used to break up loops.
   */
  private static PassFactory createEmptyPass(String name) {
    return new PassFactory(name, true) {
      @Override
      protected CompilerPass createInternal(final AbstractCompiler compiler) {
        return runInSerial();
      }
    };
  }

  /**
   * Runs custom passes that are designated to run at a particular time.
   */
  private PassFactory getCustomPasses(
      final CustomPassExecutionTime executionTime) {
    return new PassFactory("runCustomPasses", true) {
      @Override
      protected CompilerPass createInternal(final AbstractCompiler compiler) {
        return runInSerial(options.customPasses.get(executionTime));
      }
    };
  }

  /**
   * All inlining is forbidden in heuristic renaming mode, because inlining
   * will ruin the invariants that it depends on.
   */
  private boolean isInliningForbidden() {
    return options.propertyRenaming == PropertyRenamingPolicy.HEURISTIC ||
        options.propertyRenaming ==
            PropertyRenamingPolicy.AGGRESSIVE_HEURISTIC;
  }

  /** Create a compiler pass that runs the given passes in serial. */
  private static CompilerPass runInSerial(final CompilerPass ... passes) {
    return runInSerial(Lists.newArrayList(passes));
  }

  /** Create a compiler pass that runs the given passes in serial. */
  private static CompilerPass runInSerial(
      final Collection<CompilerPass> passes) {
    return new CompilerPass() {
      @Override public void process(Node externs, Node root) {
        for (CompilerPass pass : passes) {
          pass.process(externs, root);
        }
      }
    };
  }

  @VisibleForTesting
  static Map<String, Node> getAdditionalReplacements(
      CompilerOptions options) {
    Map<String, Node> additionalReplacements = Maps.newHashMap();

    if (options.markAsCompiled || options.closurePass) {
      additionalReplacements.put(COMPILED_CONSTANT_NAME, new Node(Token.TRUE));
    }

    if (options.closurePass && options.locale != null) {
      additionalReplacements.put(CLOSURE_LOCALE_CONSTANT_NAME,
          Node.newString(options.locale));
    }

    return additionalReplacements;
  }

  private final PassFactory printNameReferenceGraph =
    new PassFactory("printNameReferenceGraph", true) {
    @Override
    protected CompilerPass createInternal(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override
        public void process(Node externs, Node jsRoot) {
          NameReferenceGraphConstruction gc =
              new NameReferenceGraphConstruction(compiler);
          gc.process(externs, jsRoot);
          String graphFileName = options.nameReferenceGraphPath;
          try {
            Files.write(DotFormatter.toDot(gc.getNameReferenceGraph()),
                new File(graphFileName),
                Charsets.UTF_8);
          } catch (IOException e) {
            compiler.report(
                JSError.make(
                    NAME_REF_GRAPH_FILE_ERROR, e.getMessage(), graphFileName));
          }
        }
      };
    }
  };

  private final PassFactory printNameReferenceReport =
      new PassFactory("printNameReferenceReport", true) {
    @Override
    protected CompilerPass createInternal(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override
        public void process(Node externs, Node jsRoot) {
          NameReferenceGraphConstruction gc =
              new NameReferenceGraphConstruction(compiler);
          String reportFileName = options.nameReferenceReportPath;
          try {
            NameReferenceGraphReport report =
                new NameReferenceGraphReport(gc.getNameReferenceGraph());
            Files.write(report.getHtmlReport(),
                new File(reportFileName),
                Charsets.UTF_8);
          } catch (IOException e) {
            compiler.report(
                JSError.make(
                    NAME_REF_REPORT_FILE_ERROR, e.getMessage(), reportFileName));
          }
        }
      };
    }
  };
}
