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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.jscomp.CompilerOptions.ExtractPrototypeMemberDeclarationsMode;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.CoverageInstrumentationPass.CoverageReach;
import com.google.javascript.jscomp.ExtractPrototypeMemberDeclarations.Pattern;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.jscomp.lint.CheckEnums;
import com.google.javascript.jscomp.lint.CheckInterfaces;
import com.google.javascript.jscomp.lint.CheckNullableReturn;
import com.google.javascript.jscomp.lint.CheckPrototypeProperties;
import com.google.javascript.jscomp.parsing.ParserRunner;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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

  static final DiagnosticType CANNOT_USE_PROTOTYPE_AND_VAR =
      DiagnosticType.error("JSC_CANNOT_USE_PROTOTYPE_AND_VAR",
          "Rename prototypes and inline variables cannot be used together.");

  static final DiagnosticType CANNOT_USE_EXPORT_LOCALS_AND_EXTERN_PROP_REMOVAL =
      DiagnosticType.error("JSC_CANNOT_USE_EXPORT_LOCALS_AND_EXTERN_PROP_REMOVAL",
          "remove_unused_prototype_properties_in_externs " +
          "and export_local_property_definitions cannot be used together.");

  // Miscellaneous errors.
  static final DiagnosticType REPORT_PATH_IO_ERROR =
      DiagnosticType.error("JSC_REPORT_PATH_IO_ERROR",
          "Error writing compiler report to {0}");

  private static final DiagnosticType NAME_REF_GRAPH_FILE_ERROR =
      DiagnosticType.error("JSC_NAME_REF_GRAPH_FILE_ERROR",
          "Error \"{1}\" writing name reference graph to \"{0}\".");

  private static final DiagnosticType NAME_REF_REPORT_FILE_ERROR =
      DiagnosticType.error("JSC_NAME_REF_REPORT_FILE_ERROR",
          "Error \"{1}\" writing name reference report to \"{0}\".");

  private static final java.util.regex.Pattern GLOBAL_SYMBOL_NAMESPACE_PATTERN =
    java.util.regex.Pattern.compile("^[a-zA-Z0-9$_]+$");

  /**
   * A global namespace to share across checking passes.
   */
  private GlobalNamespace namespaceForChecks = null;

  /**
   * A symbol table for registering references that get removed during
   * preprocessing.
   */
  private PreprocessorSymbolTable preprocessorSymbolTable = null;

  /** Names exported by goog.exportSymbol. */
  private Set<String> exportedNames = null;

  /** Shared name generator that remembers character encoding bias */
  private NameGenerator nameGenerator = null;
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

  /**
   * Whether to protect "hidden" side-effects.
   * @see CheckSideEffects
   */
  private final boolean protectHiddenSideEffects;

  public DefaultPassConfig(CompilerOptions options) {
    super(options);

    // The current approach to protecting "hidden" side-effects is to
    // wrap them in a function call that is stripped later, this shouldn't
    // be done in IDE mode where AST changes may be unexpected.
    protectHiddenSideEffects = options != null &&
        options.protectHiddenSideEffects && !options.ideMode;
  }

  @Override
  protected State getIntermediateState() {
    return new State(
        cssNames == null ? null : Maps.newHashMap(cssNames),
        exportedNames == null ? null :
            Collections.unmodifiableSet(exportedNames),
        crossModuleIdGenerator, variableMap, propertyMap,
        anonymousFunctionNameMap, stringMap, functionNames, idGeneratorMap);
  }

  @Override
  protected void setIntermediateState(State state) {
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

  GlobalNamespace getGlobalNamespace() {
    return namespaceForChecks;
  }

  PreprocessorSymbolTable getPreprocessorSymbolTable() {
    return preprocessorSymbolTable;
  }

  private NameGenerator getNameGenerator() {
    if (nameGenerator == null) {
      nameGenerator = new NameGenerator(new HashSet<String>(0), "", null);
    }
    return nameGenerator;
  }

  void maybeInitializePreprocessorSymbolTable(AbstractCompiler compiler) {
    if (options.ideMode) {
      Node root = compiler.getRoot();
      if (preprocessorSymbolTable == null ||
          preprocessorSymbolTable.getRootNode() != root) {
        preprocessorSymbolTable = new PreprocessorSymbolTable(root);
      }
    }
  }

  @Override
  protected List<PassFactory> getChecks() {
    List<PassFactory> checks = Lists.newArrayList();

    checks.add(createEmptyPass("beforeStandardChecks"));

    if (options.closurePass) {
      checks.add(closureRewriteModule);
    }

    if (options.lowerFromEs6() || options.aggressiveVarCheck.isOn()) {
      checks.add(checkVariableReferences);
    }

    if (options.getLanguageIn() == LanguageMode.ECMASCRIPT6_TYPED
        && options.getLanguageOut() != LanguageMode.ECMASCRIPT6_TYPED) {
      checks.add(convertDeclaredTypesToJSDoc);
    }

    if (options.lowerFromEs6()) {
      checks.add(es6RenameVariablesInParamLists);
      checks.add(es6SplitVariableDeclarations);
      checks.add(es6ConvertSuper);
      checks.add(convertEs6ToEs3);
      checks.add(rewriteLetConst);
      checks.add(rewriteGenerators);
      checks.add(markTranspilationDone);
    }

    if (options.raiseToEs6Typed()) {
      checks.add(convertToTypedES6);
    }

    if (options.transpileOnly) {
      return checks;
    }

    if (options.lowerFromEs6()) {
      checks.add(es6RuntimeLibrary);
    }

    checks.add(convertStaticInheritance);

    if (options.declaredGlobalExternsOnWindow) {
      checks.add(declaredGlobalExternsOnWindow);
    }

    if (options.closurePass) {
      checks.add(closureGoogScopeAliases);
      checks.add(closureRewriteClass);
    }

    if (options.jqueryPass) {
      checks.add(jqueryAliases);
    }

    if (options.angularPass) {
      checks.add(angularPass);
    }

    checks.add(checkSideEffects);

    if (options.checkSuspiciousCode ||
        options.enables(DiagnosticGroups.GLOBAL_THIS) ||
        options.enables(DiagnosticGroups.DEBUGGER_STATEMENT_PRESENT)) {
      checks.add(suspiciousCode);
    }

    if (options.enables(DiagnosticGroups.MISSING_REQUIRE)) {
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
      checks.add(closurePrimitives);
    }

    if (options.closurePass && options.checkMissingGetCssNameLevel.isOn()) {
      checks.add(closureCheckGetCssName);
    }

    if (options.syntheticBlockStartMarker != null) {
      // This pass must run before the first fold constants pass.
      checks.add(createSyntheticBlocks);
    }

    checks.add(checkVars);

    if (options.inferConsts) {
      checks.add(inferConsts);
    }

    if (options.computeFunctionSideEffects) {
      checks.add(checkRegExp);
    }

    // This pass should run before types are assigned.
    if (options.processObjectPropertyString) {
      checks.add(objectPropertyStringPreprocess);
    }

    checks.add(createEmptyPass("beforeTypeChecking"));

    if (options.useNewTypeInference) {
      checks.add(symbolTableForNewTypeInference);
      checks.add(newTypeInference);
    }

    if (options.checkTypes || options.inferTypes) {
      checks.add(resolveTypes);
      checks.add(inferTypes);
      if (options.checkTypes) {
        checks.add(checkTypes);
      } else {
        checks.add(inferJsDocInfo);
      }

      // We assume that only IDE-mode clients will try to query the
      // typed scope creator after the compile job.
      if (!options.ideMode && !options.saveDataStructures) {
        checks.add(clearTypedScopePass);
      }
    }

    if (!options.disables(DiagnosticGroups.CHECK_USELESS_CODE) ||
        options.checkMissingReturn.isOn()) {
      checks.add(checkControlFlow);
    }

    // CheckAccessControls only works if check types is on.
    if (options.checkTypes &&
        (!options.disables(DiagnosticGroups.ACCESS_CONTROLS)
         || options.enables(DiagnosticGroups.CONSTANT_PROPERTY))) {
      checks.add(checkAccessControls);
    }

    // Lint checks must be run after typechecking.
    if (options.enables(DiagnosticGroups.LINT_CHECKS)) {
      checks.add(lintChecks);
    }

    if (options.checkEventfulObjectDisposalPolicy !=
        CheckEventfulObjectDisposal.DisposalCheckingPolicy.OFF) {
      checks.add(checkEventfulObjectDisposal);
    }

    if (options.checkGlobalNamesLevel.isOn()) {
      checks.add(checkGlobalNames);
    }

    if (options.enables(DiagnosticGroups.ES5_STRICT)) {
      checks.add(checkStrictMode);
    }

    if (!options.getConformanceConfigs().isEmpty()) {
      checks.add(checkConformance);
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
    if (options.replaceMessagesWithChromeI18n) {
      checks.add(replaceMessagesForChrome);
    } else if (options.messageBundle != null) {
      checks.add(replaceMessages);
    }

    if (options.getTweakProcessing().isOn()) {
      checks.add(processTweaks);
    }

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

    checks.add(createEmptyPass("afterStandardChecks"));

    assertAllOneTimePasses(checks);
    return checks;
  }

  @Override
  protected List<PassFactory> getOptimizations() {
    List<PassFactory> passes = Lists.newArrayList();

    if (options.transpileOnly) {
      return passes;
    }

    // Gather property names in externs so they can be queried by the
    // optimising passes.
    passes.add(gatherExternProperties);

    passes.add(garbageCollectChecks);

    // TODO(nicksantos): The order of these passes makes no sense, and needs
    // to be re-arranged.

    if (options.instrumentForCoverage) {
      passes.add(instrumentForCodeCoverage);
    }

    if (options.runtimeTypeCheck) {
      passes.add(runtimeTypeCheck);
    }

    passes.add(createEmptyPass("beforeStandardOptimizations"));

    if (options.replaceIdGenerators) {
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

    // Property disambiguation should only run once and needs to be done
    // soon after type checking, both so that it can make use of type
    // information and so that other passes can take advantage of the renamed
    // properties.
    if (options.disambiguatePrivateProperties) {
      passes.add(disambiguatePrivateProperties);
    }

    // Collapsing properties can undo constant inlining, so we do this before
    // the main optimization loop.
    if (options.collapseProperties) {
      passes.add(collapseProperties);
    }

    if (options.inferConsts) {
      passes.add(inferConsts);
    }

    // Running this pass before disambiguate properties allow the removing
    // unused methods that share the same name as methods called from unused
    // code.
    if (options.extraSmartNameRemoval && options.smartNameRemoval) {

      // These passes remove code that is dead because of define flags.
      // If the dead code is weakly typed, running these passes before property
      // disambiguation results in more code removal.
      // The passes are one-time on purpose. (The later runs are loopable.)
      if (options.foldConstants &&
          (options.inlineVariables || options.inlineLocalVariables)) {
        passes.add(earlyInlineVariables);
        passes.add(earlyPeepholeOptimizations);
      }

      passes.add(smartNamePass);
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
    // TODO(johnlenz): make checkConsts namespace aware so it can be run
    // as during the checks phase.
    passes.add(checkConsts);

    // Detects whether invocations of the method goog.string.Const.from are done
    // with an argument which is a string literal.
    passes.add(checkConstParams);

    assertAllOneTimePasses(passes);

    if (options.smartNameRemoval || options.reportPath != null) {
      passes.addAll(getCodeRemovingPasses());
      passes.add(smartNamePass);
    }

    // This needs to come after the inline constants pass, which is run within
    // the code removing passes.
    if (options.closurePass) {
      passes.add(closureOptimizePrimitives);
    }

    // ReplaceStrings runs after CollapseProperties in order to simplify
    // pulling in values of constants defined in enums structures. It also runs
    // after disambiguate properties and smart name removal so that it can
    // correctly identify logging types and can replace references to string
    // expressions.
    if (!options.replaceStringsFunctionDescriptions.isEmpty()) {
      passes.add(replaceStrings);
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

    // Method devirtualization benefits from property disambiguation so
    // it should run after that pass but before passes that do
    // optimizations based on global names (like cross module code motion
    // and inline functions).  Smart Name Removal does better if run before
    // this pass.
    if (options.devirtualizePrototypeMethods) {
      passes.add(devirtualizePrototypeMethods);
    }

    if (options.customPasses != null) {
      passes.add(getCustomPasses(
          CustomPassExecutionTime.BEFORE_OPTIMIZATION_LOOP));
    }

    passes.add(createEmptyPass("beforeMainOptimizations"));

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
      if (options.removeUnusedVars || options.removeUnusedLocalVars) {
        passes.add(removeUnusedVars);
      }
    }

    // Running this pass again is required to have goog.events compile down to
    // nothing when compiled on its own.
    if (options.smartNameRemoval) {
      passes.add(smartNamePass2);
    }

    if (options.collapseAnonymousFunctions) {
      passes.add(collapseAnonymousFunctions);
    }

    // Move functions before extracting prototype member declarations.
    if (options.moveFunctionDeclarations ||
        // renamePrefixNamescape relies on moveFunctionDeclarations
        // to preserve semantics.
        options.renamePrefixNamespace != null) {
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
    if (options.extractPrototypeMemberDeclarations !=
            ExtractPrototypeMemberDeclarationsMode.OFF
        && (options.propertyRenaming != PropertyRenamingPolicy.HEURISTIC
            && options.propertyRenaming !=
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

    // Passes after this point can no longer depend on normalized AST
    // assumptions.
    passes.add(markUnnormalized);

    if (options.coalesceVariableNames) {
      passes.add(coalesceVariableNames);

      // coalesceVariables creates identity assignments and more redundant code
      // that can be removed, rerun the peephole optimizations to clean them
      // up.
      if (options.foldConstants) {
        passes.add(peepholeOptimizations);
      }
    }

    if (options.collapseVariableDeclarations) {
      passes.add(exploitAssign);
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

    if (options.foldConstants) {
      passes.add(latePeepholeOptimizations);
    }

    if (options.anonymousFunctionNaming ==
        AnonymousFunctionNamingPolicy.UNMAPPED) {
      passes.add(nameUnmappedAnonymousFunctions);
    }

    if (protectHiddenSideEffects) {
      passes.add(stripSideEffectProtection);
    }

    if (options.renamePrefixNamespace != null) {
      if (!GLOBAL_SYMBOL_NAMESPACE_PATTERN.matcher(
          options.renamePrefixNamespace).matches()) {
        throw new IllegalArgumentException(
            "Illegal character in renamePrefixNamespace name: "
            + options.renamePrefixNamespace);
      }
      passes.add(rescopeGlobalSymbols);
    }

    // Safety checks
    passes.add(sanityCheckAst);
    passes.add(sanityCheckVars);

    return passes;
  }

  /** Creates the passes for the main optimization loop. */
  private List<PassFactory> getMainOptimizationLoop() {
    List<PassFactory> passes = Lists.newArrayList();
    if (options.inlineGetters) {
      passes.add(inlineSimpleMethods);
    }

    passes.addAll(getCodeRemovingPasses());

    if (options.inlineFunctions || options.inlineLocalFunctions) {
      passes.add(inlineFunctions);
    }

    if (options.inlineProperties) {
      passes.add(inlineProperties);
    }

    boolean runOptimizeCalls = options.optimizeCalls
        || options.optimizeParameters
        || options.optimizeReturns;

    if (options.removeUnusedVars || options.removeUnusedLocalVars) {
      if (options.deadAssignmentElimination) {
        passes.add(deadAssignmentsElimination);
      }
      if (!runOptimizeCalls) {
        passes.add(removeUnusedVars);
      }
    }
    if (runOptimizeCalls) {
      passes.add(optimizeCallsAndRemoveUnusedVars);
    }
    assertAllLoopablePasses(passes);
    return passes;
  }

  /** Creates several passes aimed at removing code. */
  private List<PassFactory> getCodeRemovingPasses() {
    List<PassFactory> passes = Lists.newArrayList();
    if (options.collapseObjectLiterals && !isInliningForbidden()) {
      passes.add(collapseObjectLiterals);
    }

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

    if (options.removeUnusedClassProperties && !isInliningForbidden()) {
      passes.add(removeUnusedClassProperties);
    }

    assertAllLoopablePasses(passes);
    return passes;
  }

  /**
   * Checks for code that is probably wrong (such as stray expressions).
   */
  final HotSwapPassFactory checkSideEffects =
      new HotSwapPassFactory("checkSideEffects", true) {
    @Override
    protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
      return new CheckSideEffects(compiler,
          options.checkSuspiciousCode ? CheckLevel.WARNING : CheckLevel.OFF,
              protectHiddenSideEffects);
    }
  };

  /**
   * Removes the "protector" functions that were added by CheckSideEffects.
   */
  final PassFactory stripSideEffectProtection =
      new PassFactory("stripSideEffectProtection", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
      return new CheckSideEffects.StripProtection(compiler);
    }
  };

  /**
   * Checks for code that is probably wrong (such as stray expressions).
   */
  final HotSwapPassFactory suspiciousCode =
      new HotSwapPassFactory("suspiciousCode", true) {
    @Override
    protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
      List<Callback> sharedCallbacks = Lists.newArrayList();
      if (options.checkSuspiciousCode) {
        sharedCallbacks.add(new CheckSuspiciousCode());
      }

      if (options.enables(DiagnosticGroups.GLOBAL_THIS)) {
        sharedCallbacks.add(new CheckGlobalThis(compiler));
      }

      if (options.enables(DiagnosticGroups.DEBUGGER_STATEMENT_PRESENT)) {
        sharedCallbacks.add(new CheckDebuggerStatement(compiler));
      }

      return combineChecks(compiler, sharedCallbacks);
    }

  };

  /** Verify that all the passes are one-time passes. */
  private static void assertAllOneTimePasses(List<PassFactory> passes) {
    for (PassFactory pass : passes) {
      Preconditions.checkState(pass.isOneTimePass());
    }
  }

  /** Verify that all the passes are multi-run passes. */
  private static void assertAllLoopablePasses(List<PassFactory> passes) {
    for (PassFactory pass : passes) {
      Preconditions.checkState(!pass.isOneTimePass());
    }
  }

  /** Checks that all constructed classes are goog.require()d. */
  final HotSwapPassFactory checkRequires =
      new HotSwapPassFactory("checkRequires", true) {
    @Override
    protected HotSwapCompilerPass create(AbstractCompiler compiler) {
      return new CheckRequiresForConstructors(compiler);
    }
  };

  /** Makes sure @constructor is paired with goog.provides(). */
  final HotSwapPassFactory checkProvides =
      new HotSwapPassFactory("checkProvides", true) {
    @Override
    protected HotSwapCompilerPass create(AbstractCompiler compiler) {
      return new CheckProvides(compiler, options.checkProvides);
    }
  };

  private static final DiagnosticType GENERATE_EXPORTS_ERROR =
      DiagnosticType.error(
          "JSC_GENERATE_EXPORTS_ERROR",
          "Exports can only be generated if export symbol/property " +
          "functions are set.");

  /** Generates exports for @export annotations. */
  final PassFactory generateExports = new PassFactory("generateExports", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      if (options.removeUnusedPrototypePropertiesInExterns
          && options.exportLocalPropertyDefinitions) {
        return new ErrorPass(
            compiler, CANNOT_USE_EXPORT_LOCALS_AND_EXTERN_PROP_REMOVAL);
      }

      CodingConvention convention = compiler.getCodingConvention();
      if (convention.getExportSymbolFunction() != null &&
          convention.getExportPropertyFunction() != null) {
        return new GenerateExports(compiler,
            options.exportLocalPropertyDefinitions,
            convention.getExportSymbolFunction(),
            convention.getExportPropertyFunction());
      } else {
        return new ErrorPass(compiler, GENERATE_EXPORTS_ERROR);
      }
    }
  };

  /** Generates exports for functions associated with JsUnit. */
  final PassFactory exportTestFunctions =
      new PassFactory("exportTestFunctions", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      CodingConvention convention = compiler.getCodingConvention();
      if (convention.getExportSymbolFunction() != null) {
        return new ExportTestFunctions(compiler,
            convention.getExportSymbolFunction(),
            convention.getExportPropertyFunction());
      } else {
        return new ErrorPass(compiler, GENERATE_EXPORTS_ERROR);
      }
    }
  };

  /** Raw exports processing pass. */
  final PassFactory gatherRawExports =
      new PassFactory("gatherRawExports", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
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
  final HotSwapPassFactory closurePrimitives =
      new HotSwapPassFactory("closurePrimitives", true) {
    @Override
    protected HotSwapCompilerPass create(AbstractCompiler compiler) {
      maybeInitializePreprocessorSymbolTable(compiler);
      final ProcessClosurePrimitives pass = new ProcessClosurePrimitives(
          compiler,
          preprocessorSymbolTable,
          options.brokenClosureRequiresLevel,
          options.preserveGoogRequires);

      return new HotSwapCompilerPass() {
        @Override
        public void process(Node externs, Node root) {
          pass.process(externs, root);
          exportedNames = pass.getExportedVariableNames();
        }
        @Override
        public void hotSwapScript(Node scriptRoot, Node originalRoot) {
          pass.hotSwapScript(scriptRoot, originalRoot);
        }
      };
    }
  };

  /** Expand jQuery Primitives and Aliases pass. */
  final PassFactory jqueryAliases = new PassFactory("jqueryAliases", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new ExpandJqueryAliases(compiler);
    }
  };

  /** Process AngularJS-specific annotations. */
  final HotSwapPassFactory angularPass =
      new HotSwapPassFactory("angularPass", true) {
    @Override
    protected HotSwapCompilerPass create(AbstractCompiler compiler) {
      return new AngularPass(compiler);
    }
  };

  /**
   * The default i18n pass.
   * A lot of the options are not configurable, because ReplaceMessages
   * has a lot of legacy logic.
   */
  final PassFactory replaceMessages = new PassFactory("replaceMessages", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
      return new ReplaceMessages(compiler,
          options.messageBundle,
          /* warn about message dupes */
          true,
          /* allow messages with goog.getMsg */
          JsMessage.Style.CLOSURE,
          /* if we can't find a translation, don't worry about it. */
          false);
    }
  };

  final PassFactory replaceMessagesForChrome =
      new PassFactory("replaceMessages", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
      return new ReplaceMessagesForChrome(compiler,
          new GoogleJsMessageIdGenerator(options.tcProjectId),
          /* warn about message dupes */
          true,
          /* allow messages with goog.getMsg */
          JsMessage.Style.CLOSURE);
    }
  };

  /** Applies aliases and inlines goog.scope. */
  final HotSwapPassFactory closureGoogScopeAliases =
      new HotSwapPassFactory("closureGoogScopeAliases", true) {
    @Override
    protected HotSwapCompilerPass create(AbstractCompiler compiler) {
      maybeInitializePreprocessorSymbolTable(compiler);
      return new ScopedAliases(
          compiler,
          preprocessorSymbolTable,
          options.getAliasTransformationHandler());
    }
  };

  final PassFactory es6RuntimeLibrary =
      new PassFactory("Es6RuntimeLibrary", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
      return new InjectEs6RuntimeLibrary(compiler);
    }
  };

  final HotSwapPassFactory es6RenameVariablesInParamLists =
      new HotSwapPassFactory("Es6RenameVariablesInParamLists", true) {
    @Override
    protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
      return new Es6RenameVariablesInParamLists(compiler);
    }
  };

  final HotSwapPassFactory es6SplitVariableDeclarations =
      new HotSwapPassFactory("Es6SplitVariableDeclarations", true) {
    @Override
    protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
      return new Es6SplitVariableDeclarations(compiler);
    }
  };

  final HotSwapPassFactory es6ConvertSuper =
      new HotSwapPassFactory("es6ConvertSuper", true) {
    @Override
    protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
      return new Es6ConvertSuper(compiler);
    }
  };

  /**
   * Does the main ES6 to ES3 conversion.
   * There are a few other passes which run before or after this one,
   * to convert constructs which are not converted by this pass.
   */
  final HotSwapPassFactory convertEs6ToEs3 =
      new HotSwapPassFactory("convertEs6", true) {
    @Override
    protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
      return new Es6ToEs3Converter(compiler);
    }
  };

  final HotSwapPassFactory rewriteLetConst =
      new HotSwapPassFactory("Es6RewriteLetConst", true) {
    @Override
    protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
      return new Es6RewriteLetConst(compiler);
    }
  };

  final HotSwapPassFactory rewriteGenerators =
      new HotSwapPassFactory("rewriteGenerators", true) {
    @Override
    protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
      return new Es6RewriteGenerators(compiler);
    }
  };

  final PassFactory convertStaticInheritance =
      new PassFactory("Es6StaticInheritance", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new Es6ToEs3ClassSideInheritance(compiler);
    }
  };

  final PassFactory convertDeclaredTypesToJSDoc =
      new PassFactory("convertDeclaredTypesToJSDoc", true) {
    @Override
    CompilerPass create(AbstractCompiler compiler) {
      return new ConvertDeclaredTypesToJSDoc(compiler);
    }
  };

  final PassFactory convertToTypedES6 =
      new PassFactory("ConvertToTypedES6", true) {
    @Override
    CompilerPass create(AbstractCompiler compiler) {
      return new ConvertToTypedES6(compiler);
    }
  };

  final PassFactory markTranspilationDone = new PassFactory("setLanguageMode", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override
        public void process(Node externs, Node root) {
          compiler.setLanguageMode(options.getLanguageOut());
        }
      };
    }
  };

  /** Applies aliases and inlines goog.scope. */
  final PassFactory declaredGlobalExternsOnWindow =
      new PassFactory("declaredGlobalExternsOnWindow", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new DeclaredGlobalExternsOnWindow(compiler);
    }
  };

  /** Rewrites goog.defineClass */
  final HotSwapPassFactory closureRewriteClass =
      new HotSwapPassFactory("closureRewriteClass", true) {
    @Override
    protected HotSwapCompilerPass create(AbstractCompiler compiler) {
      return new ClosureRewriteClass(compiler);
    }
  };

  /** Rewrites goog.module */
  final HotSwapPassFactory closureRewriteModule =
      new HotSwapPassFactory("closureRewriteModule", true) {
    @Override
    protected HotSwapCompilerPass create(AbstractCompiler compiler) {
      return new ClosureRewriteModule(compiler);
    }
  };

  /** Checks that CSS class names are wrapped in goog.getCssName */
  final PassFactory closureCheckGetCssName =
      new PassFactory("closureCheckGetCssName", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new CheckMissingGetCssName(
          compiler, options.checkMissingGetCssNameLevel,
          options.checkMissingGetCssNameBlacklist);
    }
  };

  /**
   * Processes goog.getCssName.  The cssRenamingMap is used to lookup
   * replacement values for the classnames.  If null, the raw class names are
   * inlined.
   */
  final PassFactory closureReplaceGetCssName =
      new PassFactory("closureReplaceGetCssName", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override
        public void process(Node externs, Node jsRoot) {
          Map<String, Integer> newCssNames = null;
          if (options.gatherCssNames) {
            newCssNames = Maps.newHashMap();
          }
          ReplaceCssNames pass = new ReplaceCssNames(
              compiler,
              newCssNames,
              options.cssRenamingWhitelist);
          pass.process(externs, jsRoot);
          cssNames = newCssNames;
        }
      };
    }
  };

  /**
   * Creates synthetic blocks to prevent FoldConstants from moving code
   * past markers in the source.
   */
  final PassFactory createSyntheticBlocks =
      new PassFactory("createSyntheticBlocks", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new CreateSyntheticBlocks(compiler,
          options.syntheticBlockStartMarker,
          options.syntheticBlockEndMarker);
    }
  };

  final PassFactory earlyPeepholeOptimizations =
      new PassFactory("earlyPeepholeOptimizations", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new PeepholeOptimizationsPass(compiler,
          new PeepholeRemoveDeadCode());
    }
  };

  final PassFactory earlyInlineVariables =
      new PassFactory("earlyInlineVariables", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
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
  };

  /** Various peephole optimizations. */
  final PassFactory peepholeOptimizations =
      new PassFactory("peepholeOptimizations", false) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      final boolean late = false;
      return new PeepholeOptimizationsPass(compiler,
            new PeepholeMinimizeConditions(late),
            new PeepholeSubstituteAlternateSyntax(late),
            new PeepholeReplaceKnownMethods(late),
            new PeepholeRemoveDeadCode(),
            new PeepholeFoldConstants(late),
            new PeepholeCollectPropertyAssignments());
    }
  };

  /** Same as peepholeOptimizations but aggressively merges code together */
  final PassFactory latePeepholeOptimizations =
      new PassFactory("latePeepholeOptimizations", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      final boolean late = true;
      return new PeepholeOptimizationsPass(compiler,
            new StatementFusion(options.aggressiveFusion),
            new PeepholeRemoveDeadCode(),
            new PeepholeMinimizeConditions(late),
            new PeepholeSubstituteAlternateSyntax(late),
            new PeepholeReplaceKnownMethods(late),
            new PeepholeFoldConstants(late),
            new ReorderConstantExpression());
    }
  };

  /** Checks that all variables are defined. */
  final HotSwapPassFactory checkVars =
      new HotSwapPassFactory("checkVars", true) {
    @Override
    protected HotSwapCompilerPass create(AbstractCompiler compiler) {
      return new VarCheck(compiler);
    }
  };

  /** Infers constants. */
  final PassFactory inferConsts = new PassFactory("inferConsts", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new InferConsts(compiler);
    }
  };

  /** Checks for RegExp references. */
  final PassFactory checkRegExp =
      new PassFactory("checkRegExp", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
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

  /** Checks that references to variables look reasonable. */
  final HotSwapPassFactory checkVariableReferences =
      new HotSwapPassFactory("checkVariableReferences", true) {
    @Override
    protected HotSwapCompilerPass create(AbstractCompiler compiler) {
      return new VariableReferenceCheck(
          compiler, options.aggressiveVarCheck);
    }
  };

  /** Pre-process goog.testing.ObjectPropertyString. */
  final PassFactory objectPropertyStringPreprocess =
      new PassFactory("ObjectPropertyStringPreprocess", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new ObjectPropertyStringPreprocess(compiler);
    }
  };

  /** Creates a typed scope and adds types to the type registry. */
  final HotSwapPassFactory resolveTypes =
      new HotSwapPassFactory("resolveTypes", true) {
    @Override
    protected HotSwapCompilerPass create(AbstractCompiler compiler) {
      return new GlobalTypeResolver(compiler);
    }
  };

  /** Clears the typed scope when we're done. */
  final PassFactory clearTypedScopePass =
      new PassFactory("clearTypedScopePass", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new ClearTypedScope();
    }
  };

  /** Runs type inference. */
  final HotSwapPassFactory inferTypes =
      new HotSwapPassFactory("inferTypes", true) {
    @Override
    protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
      return new HotSwapCompilerPass() {
        @Override
        public void process(Node externs, Node root) {
          Preconditions.checkNotNull(topScope);
          Preconditions.checkNotNull(getTypedScopeCreator());

          makeTypeInference(compiler).process(externs, root);
        }
        @Override
        public void hotSwapScript(Node scriptRoot, Node originalRoot) {
          makeTypeInference(compiler).inferAllScopes(scriptRoot);
        }
      };
    }
  };

  final PassFactory symbolTableForNewTypeInference =
      new PassFactory("GlobalTypeInfo", true) {
        @Override
        protected CompilerPass create(final AbstractCompiler compiler) {
          return new GlobalTypeInfo(compiler);
        }
      };

  final PassFactory newTypeInference =
      new PassFactory("NewTypeInference", true) {
        @Override
        protected CompilerPass create(final AbstractCompiler compiler) {
          return new NewTypeInference(compiler, options.closurePass);
        }
      };

  final HotSwapPassFactory inferJsDocInfo =
      new HotSwapPassFactory("inferJsDocInfo", true) {
  @Override
  protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
    return new HotSwapCompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        Preconditions.checkNotNull(topScope);
        Preconditions.checkNotNull(getTypedScopeCreator());

        makeInferJsDocInfo(compiler).process(externs, root);
      }
      @Override
      public void hotSwapScript(Node scriptRoot, Node originalRoot) {
        makeInferJsDocInfo(compiler).hotSwapScript(scriptRoot, originalRoot);
      }
    };
  }
};

  /** Checks type usage */
  final HotSwapPassFactory checkTypes =
      new HotSwapPassFactory("checkTypes", true) {
    @Override
    protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
      return new HotSwapCompilerPass() {
        @Override
        public void process(Node externs, Node root) {
          Preconditions.checkNotNull(topScope);
          Preconditions.checkNotNull(getTypedScopeCreator());

          TypeCheck check = makeTypeCheck(compiler);
          check.process(externs, root);
          compiler.getErrorManager().setTypedPercent(check.getTypedPercent());
        }
        @Override
        public void hotSwapScript(Node scriptRoot, Node originalRoot) {
          makeTypeCheck(compiler).check(scriptRoot, false);
        }
      };
    }
  };

  /**
   * Checks possible execution paths of the program for problems: missing return
   * statements and dead code.
   */
  final HotSwapPassFactory checkControlFlow =
      new HotSwapPassFactory("checkControlFlow", true) {
    @Override
    protected HotSwapCompilerPass create(AbstractCompiler compiler) {
      List<Callback> callbacks = Lists.newArrayList();
      if (!options.disables(DiagnosticGroups.CHECK_USELESS_CODE)) {
        callbacks.add(new CheckUnreachableCode(compiler));
      }
      if (options.checkMissingReturn.isOn()) {
        callbacks.add(
            new CheckMissingReturn(compiler, options.checkMissingReturn));
      }
      return combineChecks(compiler, callbacks);
    }
  };

  /** Checks access controls. Depends on type-inference. */
  final HotSwapPassFactory checkAccessControls =
      new HotSwapPassFactory("checkAccessControls", true) {
    @Override
    protected HotSwapCompilerPass create(AbstractCompiler compiler) {
      return new CheckAccessControls(
          compiler, options.enforceAccessControlCodingConventions);
    }
  };

  final HotSwapPassFactory lintChecks =
      new HotSwapPassFactory("lintChecks", true) {
    @Override
    protected HotSwapCompilerPass create(AbstractCompiler compiler) {
      return combineChecks(compiler, ImmutableList.<Callback>of(
          new CheckEnums(compiler),
          new CheckInterfaces(compiler),
          new CheckNullableReturn(compiler),
          new CheckPrototypeProperties(compiler)));
    }
  };

  /** Executes the given callbacks with a {@link CombinedCompilerPass}. */
  private static HotSwapCompilerPass combineChecks(AbstractCompiler compiler,
      List<Callback> callbacks) {
    Preconditions.checkArgument(!callbacks.isEmpty());
    return new CombinedCompilerPass(compiler, callbacks);
  }

  /** A compiler pass that resolves types in the global scope. */
  class GlobalTypeResolver implements HotSwapCompilerPass {
    private final AbstractCompiler compiler;

    GlobalTypeResolver(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void process(Node externs, Node root) {
      if (topScope == null) {
        regenerateGlobalTypedScope(compiler, root.getParent());
      } else {
        compiler.getTypeRegistry().resolveTypesInScope(topScope);
      }
    }
    @Override
    public void hotSwapScript(Node scriptRoot, Node originalRoot) {
      patchGlobalTypedScope(compiler, scriptRoot);
    }
  }

  /** A compiler pass that clears the global scope. */
  class ClearTypedScope implements CompilerPass {
    @Override
    public void process(Node externs, Node root) {
      clearTypedScope();
    }
  }

  /** Checks global name usage. */
  final PassFactory checkGlobalNames =
      new PassFactory("checkGlobalNames", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override
        public void process(Node externs, Node jsRoot) {
          // Create a global namespace for analysis by check passes.
          // Note that this class does all heavy computation lazily,
          // so it's OK to create it here.
          namespaceForChecks = new GlobalNamespace(compiler, externs, jsRoot);
          new CheckGlobalNames(compiler, options.checkGlobalNamesLevel)
              .injectNamespace(namespaceForChecks).process(externs, jsRoot);
        }
      };
    }
  };

  /** Checks that the code is ES5 strict compliant. */
  final PassFactory checkStrictMode =
      new PassFactory("checkStrictMode", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new StrictModeCheck(compiler,
          !options.checkSymbols);  // don't check variables twice
    }
  };

  /** Process goog.tweak.getTweak() calls. */
  final PassFactory processTweaks = new PassFactory("processTweaks", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override
        public void process(Node externs, Node jsRoot) {
          new ProcessTweaks(compiler,
              options.getTweakProcessing().shouldStrip(),
              options.getTweakReplacements()).process(externs, jsRoot);
        }
      };
    }
  };

  /** Override @define-annotated constants. */
  final PassFactory processDefines = new PassFactory("processDefines", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override
        public void process(Node externs, Node jsRoot) {
          HashMap<String, Node> replacements = new HashMap<>();
          replacements.putAll(compiler.getDefaultDefineValues());
          replacements.putAll(getAdditionalReplacements(options));
          replacements.putAll(options.getDefineReplacements());
          new ProcessDefines(compiler, ImmutableMap.copyOf(replacements))
              .injectNamespace(namespaceForChecks).process(externs, jsRoot);
        }
      };
    }
  };

  /** Release references to data that is only needed during checks. */
  final PassFactory garbageCollectChecks =
      new HotSwapPassFactory("garbageCollectChecks", true) {
    @Override
    protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
      return new HotSwapCompilerPass() {
        @Override
        public void process(Node externs, Node jsRoot) {
          // Kill the global namespace so that it can be garbage collected
          // after all passes are through with it.
          namespaceForChecks = null;
        }

        @Override
        public void hotSwapScript(Node scriptRoot, Node originalRoot) {
          process(null, null);
        }
      };
    }
  };

  /** Checks that all constants are not modified */
  final PassFactory checkConsts = new PassFactory("checkConsts", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new ConstCheck(compiler);
    }
  };

  /** Checks that the arguments are constants */
  final PassFactory checkConstParams =
      new PassFactory("checkConstParams", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new ConstParamCheck(compiler);
    }
  };

  /** Check memory bloat patterns */
  final PassFactory checkEventfulObjectDisposal =
      new PassFactory("checkEventfulObjectDisposal", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new CheckEventfulObjectDisposal(compiler,
          options.checkEventfulObjectDisposalPolicy);
    }
  };

  /** Computes the names of functions for later analysis. */
  final PassFactory computeFunctionNames =
      new PassFactory("computeFunctionNames", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return ((functionNames = new FunctionNames(compiler)));
    }
  };

  /** Inserts run-time type assertions for debugging. */
  final PassFactory runtimeTypeCheck =
      new PassFactory("runtimeTypeCheck", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new RuntimeTypeCheck(compiler,
          options.runtimeTypeCheckLogFunction);
    }
  };

  /** Generates unique ids. */
  final PassFactory replaceIdGenerators =
      new PassFactory("replaceIdGenerators", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override public void process(Node externs, Node root) {
          ReplaceIdGenerators pass =
              new ReplaceIdGenerators(
                  compiler, options.idGenerators, options.generatePseudoNames,
                  options.idGeneratorsMapSerialized);
          pass.process(externs, root);
          idGeneratorMap = pass.getSerializedIdMappings();
        }
      };
    }
  };

  /** Replace strings. */
  final PassFactory replaceStrings = new PassFactory("replaceStrings", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override public void process(Node externs, Node root) {
          ReplaceStrings pass = new ReplaceStrings(
              compiler,
              options.replaceStringsPlaceholderToken,
              options.replaceStringsFunctionDescriptions,
              options.replaceStringsReservedStrings,
              options.replaceStringsInputMap);
          pass.process(externs, root);
          stringMap = pass.getStringMap();
        }
      };
    }
  };

  /** Optimizes the "arguments" array. */
  final PassFactory optimizeArgumentsArray =
      new PassFactory("optimizeArgumentsArray", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new OptimizeArgumentsArray(compiler);
    }
  };

  /** Remove variables set to goog.abstractMethod. */
  final PassFactory closureCodeRemoval =
      new PassFactory("closureCodeRemoval", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
      return new ClosureCodeRemoval(compiler, options.removeAbstractMethods,
          options.removeClosureAsserts);
    }
  };

  /** Special case optimizations for closure functions. */
  final PassFactory closureOptimizePrimitives =
      new PassFactory("closureOptimizePrimitives", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
      return new ClosureOptimizePrimitives(compiler);
    }
  };

  /** Puts global symbols into a single object. */
  final PassFactory rescopeGlobalSymbols =
      new PassFactory("rescopeGlobalSymbols", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new RescopeGlobalSymbols(
          compiler,
          options.renamePrefixNamespace,
          options.renamePrefixNamespaceAssumeCrossModuleNames);
    }
  };

  /** Collapses names in the global scope. */
  final PassFactory collapseProperties =
      new PassFactory("collapseProperties", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new CollapseProperties(compiler, !isInliningForbidden());
    }
  };

  /** Rewrite properties as variables. */
  final PassFactory collapseObjectLiterals =
      new PassFactory("collapseObjectLiterals", false) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new InlineObjectLiterals(
          compiler, compiler.getUniqueNameIdSupplier());
    }
  };

  /** Disambiguate property names based on the coding convention. */
  final PassFactory disambiguatePrivateProperties =
      new PassFactory("disambiguatePrivateProperties", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new DisambiguatePrivateProperties(compiler);
    }
  };

  /** Disambiguate property names based on type information. */
  final PassFactory disambiguateProperties =
      new PassFactory("disambiguateProperties", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return DisambiguateProperties.forJSTypeSystem(compiler,
          options.propertyInvalidationErrors);
    }
  };

  /**
   * Chain calls to functions that return this.
   */
  final PassFactory chainCalls = new PassFactory("chainCalls", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new ChainCalls(compiler);
    }
  };

  /**
   * Rewrite instance methods as static methods, to make them easier
   * to inline.
   */
  final PassFactory devirtualizePrototypeMethods =
      new PassFactory("devirtualizePrototypeMethods", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new DevirtualizePrototypeMethods(compiler);
    }
  };

  /**
   * Optimizes unused function arguments, unused return values, and inlines
   * constant parameters. Also runs RemoveUnusedVars.
   */
  final PassFactory optimizeCallsAndRemoveUnusedVars =
      new PassFactory("optimizeCalls_and_removeUnusedVars", false) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
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
        boolean removeOnlyLocals = options.removeUnusedLocalVars
            && !options.removeUnusedVars;
        boolean preserveAnonymousFunctionNames =
            options.anonymousFunctionNaming !=
            AnonymousFunctionNamingPolicy.OFF;
        passes.addPass(
            new RemoveUnusedVars(compiler, !removeOnlyLocals,
                preserveAnonymousFunctionNames, true));
      }
      return passes;
    }
  };

  /**
   * Look for function calls that are pure, and annotate them
   * that way.
   */
  final PassFactory markPureFunctions =
      new PassFactory("markPureFunctions", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new PureFunctionIdentifier.Driver(
          compiler, options.debugFunctionSideEffectsPath, false);
    }
  };

  /**
   * Look for function calls that have no side effects, and annotate them
   * that way.
   */
  final PassFactory markNoSideEffectCalls =
      new PassFactory("markNoSideEffectCalls", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new MarkNoSideEffectCalls(compiler);
    }
  };

  /** Inlines variables heuristically. */
  final PassFactory inlineVariables =
      new PassFactory("inlineVariables", false) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
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
  final PassFactory inlineConstants =
      new PassFactory("inlineConstants", false) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new InlineVariables(
          compiler, InlineVariables.Mode.CONSTANTS_ONLY, true);
    }
  };

  /**
   * Perform local control flow optimizations.
   */
  final PassFactory minimizeExitPoints =
      new PassFactory("minimizeExitPoints", false) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new MinimizeExitPoints(compiler);
    }
  };

  /**
   * Use data flow analysis to remove dead branches.
   */
  final PassFactory removeUnreachableCode =
      new PassFactory("removeUnreachableCode", false) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new UnreachableCodeElimination(compiler, true);
    }
  };

  /**
   * Remove prototype properties that do not appear to be used.
   */
  final PassFactory removeUnusedPrototypeProperties =
      new PassFactory("removeUnusedPrototypeProperties", false) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new RemoveUnusedPrototypeProperties(
          compiler, options.removeUnusedPrototypePropertiesInExterns,
          !options.removeUnusedVars);
    }
  };

  /**
   * Remove prototype properties that do not appear to be used.
   */
  final PassFactory removeUnusedClassProperties =
      new PassFactory("removeUnusedClassProperties", false) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new RemoveUnusedClassProperties(
          compiler, options.removeUnusedConstructorProperties);
    }
  };

  /**
   * Process smart name processing - removes unused classes and does referencing
   * starting with minimum set of names.
   */
  final PassFactory smartNamePass = new PassFactory("smartNamePass", true) {
    private boolean hasWrittenFile = false;

    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override
        public void process(Node externs, Node root) {
          NameAnalyzer na = new NameAnalyzer(compiler, false);
          na.process(externs, root);

          String reportPath = options.reportPath;
          if (reportPath != null) {
            try {
              if (hasWrittenFile) {
                Files.append(na.getHtmlReport(), new File(reportPath),
                    UTF_8);
              } else {
                Files.write(na.getHtmlReport(), new File(reportPath),
                    UTF_8);
                hasWrittenFile = true;
              }
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

  /**
   * Process smart name processing - removes unused classes and does referencing
   * starting with minimum set of names.
   */
  final PassFactory smartNamePass2 = new PassFactory("smartNamePass", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override
        public void process(Node externs, Node root) {
          NameAnalyzer na = new NameAnalyzer(compiler, false);
          na.process(externs, root);
          na.removeUnreferenced();
        }
      };
    }
  };

  /** Inlines simple methods, like getters */
  final PassFactory inlineSimpleMethods =
      new PassFactory("inlineSimpleMethods", false) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new InlineSimpleMethods(compiler);
    }
  };

  /** Kills dead assignments. */
  final PassFactory deadAssignmentsElimination =
      new PassFactory("deadAssignmentsElimination", false) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new DeadAssignmentsElimination(compiler);
    }
  };

  /** Inlines function calls. */
  final PassFactory inlineFunctions =
      new PassFactory("inlineFunctions", false) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      boolean enableBlockInlining = !isInliningForbidden();
      return new InlineFunctions(
          compiler,
          compiler.getUniqueNameIdSupplier(),
          options.inlineFunctions,
          options.inlineLocalFunctions,
          enableBlockInlining,
          options.assumeStrictThis()
              || options.getLanguageIn() == LanguageMode.ECMASCRIPT5_STRICT,
          options.assumeClosuresOnlyCaptureReferences,
          options.maxFunctionSizeAfterInlining);
    }
  };

  /** Inlines constant properties. */
  final PassFactory inlineProperties =
      new PassFactory("inlineProperties", false) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new InlineProperties(compiler);
    }
  };

  /** Removes variables that are never used. */
  final PassFactory removeUnusedVars =
      new PassFactory("removeUnusedVars", false) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
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
  final PassFactory crossModuleCodeMotion =
      new PassFactory(Compiler.CROSS_MODULE_CODE_MOTION_NAME, false) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new CrossModuleCodeMotion(
          compiler,
          compiler.getModuleGraph(),
          options.parentModuleCanSeeSymbolsDeclaredInChildren);
    }
  };

  /**
   * Move methods to a deeper common module
   */
  final PassFactory crossModuleMethodMotion =
      new PassFactory(Compiler.CROSS_MODULE_METHOD_MOTION_NAME, false) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new CrossModuleMethodMotion(
          compiler, crossModuleIdGenerator,
          // Only move properties in externs if we're not treating
          // them as exports.
          options.removeUnusedPrototypePropertiesInExterns);
    }
  };

  /** A data-flow based variable inliner. */
  final PassFactory flowSensitiveInlineVariables =
      new PassFactory("flowSensitiveInlineVariables", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new FlowSensitiveInlineVariables(compiler);
    }
  };

  /** Uses register-allocation algorithms to use fewer variables. */
  final PassFactory coalesceVariableNames =
      new PassFactory("coalesceVariableNames", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new CoalesceVariableNames(compiler, options.generatePseudoNames);
    }
  };

  /**
   * Some simple, local collapses (e.g., {@code var x; var y;} becomes
   * {@code var x,y;}.
   */
  final PassFactory exploitAssign = new PassFactory("exploitAssign", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new PeepholeOptimizationsPass(compiler,
          new ExploitAssigns());
    }
  };

  /**
   * Some simple, local collapses (e.g., {@code var x; var y;} becomes
   * {@code var x,y;}.
   */
  final PassFactory collapseVariableDeclarations =
      new PassFactory("collapseVariableDeclarations", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new CollapseVariableDeclarations(compiler);
    }
  };

  /**
   * Simple global collapses of variable declarations.
   */
  final PassFactory groupVariableDeclarations =
      new PassFactory("groupVariableDeclarations", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new GroupVariableDeclarations(compiler);
    }
  };

  /**
   * Extracts common sub-expressions.
   */
  final PassFactory extractPrototypeMemberDeclarations =
      new PassFactory("extractPrototypeMemberDeclarations", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      Pattern pattern;
      switch (options.extractPrototypeMemberDeclarations) {
        case USE_GLOBAL_TEMP:
          pattern = Pattern.USE_GLOBAL_TEMP;
          break;
        case USE_IIFE:
          pattern = Pattern.USE_IIFE;
          break;
        default:
          throw new IllegalStateException("unexpected");
      }

      return new ExtractPrototypeMemberDeclarations(
          compiler, pattern);
    }
  };

  /** Rewrites common function definitions to be more compact. */
  final PassFactory rewriteFunctionExpressions =
      new PassFactory("rewriteFunctionExpressions", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new FunctionRewriter(compiler);
    }
  };

  /** Collapses functions to not use the VAR keyword. */
  final PassFactory collapseAnonymousFunctions =
      new PassFactory("collapseAnonymousFunctions", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new CollapseAnonymousFunctions(compiler);
    }
  };

  /** Moves function declarations to the top, to simulate actual hoisting. */
  final PassFactory moveFunctionDeclarations =
      new PassFactory("moveFunctionDeclarations", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new MoveFunctionDeclarations(compiler);
    }
  };

  final PassFactory nameUnmappedAnonymousFunctions =
      new PassFactory("nameAnonymousFunctions", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new NameAnonymousFunctions(compiler);
    }
  };

  final PassFactory nameMappedAnonymousFunctions =
      new PassFactory("nameAnonymousFunctions", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override public void process(Node externs, Node root) {
          NameAnonymousFunctionsMapped naf =
              new NameAnonymousFunctionsMapped(
                  compiler, options.inputAnonymousFunctionNamingMap);
          naf.process(externs, root);
          anonymousFunctionNameMap = naf.getFunctionMap();
        }
      };
    }
  };

  /** Alias external symbols. */
  final PassFactory aliasExternals = new PassFactory("aliasExternals", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new AliasExternals(compiler, compiler.getModuleGraph(),
          options.unaliasableGlobals, options.aliasableGlobals);
    }
  };

  /**
   * Alias string literals with global variables, to avoid creating lots of
   * transient objects.
   */
  final PassFactory aliasStrings = new PassFactory("aliasStrings", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new AliasStrings(
          compiler,
          compiler.getModuleGraph(),
          options.aliasAllStrings ? null : options.aliasableStrings,
          options.aliasStringsBlacklist,
          options.outputJsStringUsage);
    }
  };

  /** Handling for the ObjectPropertyString primitive. */
  final PassFactory objectPropertyStringPostprocess =
      new PassFactory("ObjectPropertyStringPostprocess", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new ObjectPropertyStringPostprocess(compiler);
    }
  };

  /**
   * Renames properties so that the two properties that never appear on
   * the same object get the same name.
   */
  final PassFactory ambiguateProperties =
      new PassFactory("ambiguateProperties", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new AmbiguateProperties(
          compiler, options.anonymousFunctionNaming.getReservedCharacters());
    }
  };

  /**
   * Mark the point at which the normalized AST assumptions no longer hold.
   */
  final PassFactory markUnnormalized =
      new PassFactory("markUnnormalized", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override public void process(Node externs, Node root) {
          compiler.setLifeCycleStage(LifeCycleStage.RAW);
        }
      };
    }
  };

  /** Denormalize the AST for code generation. */
  final PassFactory denormalize = new PassFactory("denormalize", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new Denormalize(compiler);
    }
  };

  /** Inverting name normalization. */
  final PassFactory invertContextualRenaming =
      new PassFactory("invertContextualRenaming", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return MakeDeclaredNamesUnique.getContextualRenameInverter(compiler);
    }
  };

  /**
   * Renames properties.
   */
  final PassFactory renameProperties =
      new PassFactory("renameProperties", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
      final VariableMap prevPropertyMap = options.inputPropertyMap;
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
            compiler, options.generatePseudoNames,
            prevPropertyMap, reservedChars);
        rprop.process(externs, root);
        return rprop.getPropertyMap();

      default:
        throw new IllegalStateException(
            "Unrecognized property renaming policy");
    }
  }

  /** Renames variables. */
  final PassFactory renameVars = new PassFactory("renameVars", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
      final VariableMap prevVariableMap = options.inputVariableMap;
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
    Set<String> reservedNames = Sets.newHashSet();
    if (options.renamePrefixNamespace != null) {
      // don't use the prefix name as a global symbol.
      reservedNames.add(options.renamePrefixNamespace);
    }
    if (exportedNames != null) {
      reservedNames.addAll(exportedNames);
    }
    reservedNames.addAll(ParserRunner.getReservedVars());
    RenameVars rn = new RenameVars(
        compiler,
        options.renamePrefix,
        options.variableRenaming == VariableRenamingPolicy.LOCAL,
        preserveAnonymousFunctionNames,
        options.generatePseudoNames,
        options.shadowVariables,
        options.preferStableNames,
        prevVariableMap,
        reservedChars,
        reservedNames);
    rn.process(externs, root);
    return rn.getVariableMap();
  }

  /** Renames labels */
  final PassFactory renameLabels = new PassFactory("renameLabels", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new RenameLabels(compiler);
    }
  };

  /** Convert bracket access to dot access */
  final PassFactory convertToDottedProperties =
      new PassFactory("convertToDottedProperties", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new ConvertToDottedProperties(compiler);
    }
  };

  /** Checks that all variables are defined. */
  final PassFactory sanityCheckAst = new PassFactory("sanityCheckAst", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new AstValidator(compiler);
    }
  };

  /** Checks that all variables are defined. */
  final PassFactory sanityCheckVars = new PassFactory("sanityCheckVars", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new VarCheck(compiler, true);
    }
  };

  /** Adds instrumentations according to an instrumentation template. */
  final PassFactory instrumentFunctions =
      new PassFactory("instrumentFunctions", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
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

  final PassFactory instrumentForCodeCoverage =
      new PassFactory("instrumentForCodeCoverage", true) {
        @Override
        protected CompilerPass create(final AbstractCompiler compiler) {
          // TODO(johnlenz): make global instrumentation an option
          return new CoverageInstrumentationPass(
              compiler, CoverageReach.CONDITIONAL);
        }
      };

  /** Extern property names gathering pass. */
  final PassFactory gatherExternProperties =
      new PassFactory("gatherExternProperties", true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new GatherExternProperties(compiler);
        }
      };

  /**
   * Create a no-op pass that can only run once. Used to break up loops.
   */
  static PassFactory createEmptyPass(String name) {
    return new PassFactory(name, true) {
      @Override
      protected CompilerPass create(final AbstractCompiler compiler) {
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
      protected CompilerPass create(final AbstractCompiler compiler) {
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
      additionalReplacements.put(COMPILED_CONSTANT_NAME, IR.trueNode());
    }

    if (options.closurePass && options.locale != null) {
      additionalReplacements.put(CLOSURE_LOCALE_CONSTANT_NAME,
          IR.string(options.locale));
    }

    return additionalReplacements;
  }

  final PassFactory printNameReferenceGraph =
    new PassFactory("printNameReferenceGraph", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
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
                UTF_8);
          } catch (IOException e) {
            compiler.report(
                JSError.make(
                    NAME_REF_GRAPH_FILE_ERROR, e.getMessage(), graphFileName));
          }
        }
      };
    }
  };

  final PassFactory printNameReferenceReport =
      new PassFactory("printNameReferenceReport", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
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
                UTF_8);
          } catch (IOException e) {
            compiler.report(
                JSError.make(
                    NAME_REF_REPORT_FILE_ERROR,
                    e.getMessage(),
                    reportFileName));
          }
        }
      };
    }
  };

  /**
   * A pass-factory that is good for {@code HotSwapCompilerPass} passes.
   */
  abstract static class HotSwapPassFactory extends PassFactory {

    HotSwapPassFactory(String name, boolean isOneTimePass) {
      super(name, isOneTimePass);
    }

    @Override
    protected abstract HotSwapCompilerPass create(AbstractCompiler compiler);

    @Override
    HotSwapCompilerPass getHotSwapPass(AbstractCompiler compiler) {
      return this.create(compiler);
    }
  }

  private final PassFactory checkConformance =
      new PassFactory("checkConformance", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
      return new CheckConformance(
          compiler, ImmutableList.copyOf(options.getConformanceConfigs()));
    }
  };
}
