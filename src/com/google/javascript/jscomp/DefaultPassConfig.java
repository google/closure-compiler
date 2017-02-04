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

import static com.google.javascript.jscomp.PassFactory.createEmptyPass;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.jscomp.AbstractCompiler.MostRecentTypechecker;
import com.google.javascript.jscomp.CompilerOptions.ExtractPrototypeMemberDeclarationsMode;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.CoverageInstrumentationPass.CoverageReach;
import com.google.javascript.jscomp.CoverageInstrumentationPass.InstrumentOption;
import com.google.javascript.jscomp.ExtractPrototypeMemberDeclarations.Pattern;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.jscomp.PassFactory.HotSwapPassFactory;
import com.google.javascript.jscomp.lint.CheckArrayWithGoogObject;
import com.google.javascript.jscomp.lint.CheckDuplicateCase;
import com.google.javascript.jscomp.lint.CheckEmptyStatements;
import com.google.javascript.jscomp.lint.CheckEnums;
import com.google.javascript.jscomp.lint.CheckInterfaces;
import com.google.javascript.jscomp.lint.CheckJSDocStyle;
import com.google.javascript.jscomp.lint.CheckMissingSemicolon;
import com.google.javascript.jscomp.lint.CheckNullableReturn;
import com.google.javascript.jscomp.lint.CheckPrimitiveAsObject;
import com.google.javascript.jscomp.lint.CheckPrototypeProperties;
import com.google.javascript.jscomp.lint.CheckRequiresAndProvidesSorted;
import com.google.javascript.jscomp.lint.CheckUnusedLabels;
import com.google.javascript.jscomp.lint.CheckUselessBlocks;
import com.google.javascript.jscomp.parsing.ParserRunner;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
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
 *
 * NOTE(dimvar): this needs some non-trivial refactoring. The pass config should
 * use as little state as possible. The recommended way for a pass to leave
 * behind some state for a subsequent pass is through the compiler object.
 * Any other state remaining here should only be used when the pass config is
 * creating the list of checks and optimizations, not after passes have started
 * executing. For example, the field namespaceForChecks should be in Compiler.
 */
public final class DefaultPassConfig extends PassConfig {

  /* For the --mark-as-compiled pass */
  private static final String COMPILED_CONSTANT_NAME = "COMPILED";

  /* Constant name for Closure's locale */
  private static final String CLOSURE_LOCALE_CONSTANT_NAME = "goog.LOCALE";

  static final DiagnosticType CANNOT_USE_PROTOTYPE_AND_VAR =
      DiagnosticType.error("JSC_CANNOT_USE_PROTOTYPE_AND_VAR",
          "Rename prototypes and inline variables cannot be used together.");

  // Miscellaneous errors.
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

  /**
   * Global state necessary for doing hotswap recompilation of files with references to
   * processed goog.modules.
   */
  private ClosureRewriteModule.GlobalRewriteState moduleRewriteState = null;

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
    protectHiddenSideEffects = options != null && options.shouldProtectHiddenSideEffects();
  }

  @Override
  protected State getIntermediateState() {
    return new State(
        cssNames == null ? null : new HashMap<>(cssNames),
        exportedNames == null ? null :
            Collections.unmodifiableSet(exportedNames),
        crossModuleIdGenerator, variableMap, propertyMap,
        anonymousFunctionNameMap, stringMap, functionNames, idGeneratorMap);
  }

  GlobalNamespace getGlobalNamespace() {
    return namespaceForChecks;
  }

  PreprocessorSymbolTable getPreprocessorSymbolTable() {
    return preprocessorSymbolTable;
  }

  void maybeInitializePreprocessorSymbolTable(AbstractCompiler compiler) {
    if (options.preservesDetailedSourceInfo()) {
      Node root = compiler.getRoot();
      if (preprocessorSymbolTable == null ||
          preprocessorSymbolTable.getRootNode() != root) {
        preprocessorSymbolTable = new PreprocessorSymbolTable(root);
      }
    }
  }

  void maybeInitializeModuleRewriteState() {
    if (options.allowsHotswapReplaceScript() && this.moduleRewriteState == null) {
      this.moduleRewriteState = new ClosureRewriteModule.GlobalRewriteState();
    }
  }

  @Override
  protected List<PassFactory> getTranspileOnlyPasses() {
    List<PassFactory> passes = new ArrayList<>();

    if (options.getLanguageIn() == LanguageMode.ECMASCRIPT6_TYPED
        && options.getLanguageOut() != LanguageMode.ECMASCRIPT6_TYPED) {
      passes.add(convertEs6TypedToEs6);
    }

    passes.add(checkMissingSuper);
    passes.add(checkVariableReferencesForTranspileOnly);

    // It's important that the Dart super accessors pass run *before* es6ConvertSuper,
    // which is a "late" ES6 pass. This is enforced in the assertValidOrder method.
    if (options.dartPass && !options.getLanguageOut().isEs6OrHigher()) {
      passes.add(dartSuperAccessorsPass);
    }

    if (options.getLanguageIn().isEs6OrHigher() && !options.skipTranspilationAndCrash) {
      TranspilationPasses.addEs6EarlyPasses(passes);
      TranspilationPasses.addEs6LatePasses(passes);
      TranspilationPasses.addPostCheckPasses(passes);
      if (options.rewritePolyfills) {
        TranspilationPasses.addRewritePolyfillPass(passes);
      }
      passes.add(markTranspilationDone);
    }

    if (options.raiseToEs6Typed()) {
      passes.add(convertToTypedES6);
    }

    if (!options.forceLibraryInjection.isEmpty()) {
      passes.add(injectRuntimeLibraries);
    }

    assertAllOneTimePasses(passes);
    assertValidOrderForChecks(passes);
    return passes;
  }

  @Override
  protected List<PassFactory> getWhitespaceOnlyPasses() {
    List<PassFactory> passes = new ArrayList<>();
    if (options.wrapGoogModulesForWhitespaceOnly) {
      passes.add(whitespaceWrapGoogModules);
    }
    return passes;
  }

  private void addOldTypeCheckerPasses(
      List<PassFactory> checks, CompilerOptions options) {
    if (options.checkTypes || options.inferTypes) {
      checks.add(resolveTypes);
      checks.add(inferTypes);
      if (options.checkTypes) {
        checks.add(checkTypes);
      } else {
        checks.add(inferJsDocInfo);
      }

      // We assume that only clients who are going to re-compile, or do in-depth static analysis,
      // will need the typed scope creator after the compile job.
      if (!options.preservesDetailedSourceInfo() && !options.allowsHotswapReplaceScript()) {
        checks.add(clearTypedScopePass);
      }
    }
  }

  @Override
  protected List<PassFactory> getChecks() {
    List<PassFactory> checks = new ArrayList<>();

    checks.add(createEmptyPass("beforeStandardChecks"));

    // Verify JsDoc annotations
    checks.add(checkJsDoc);

    if (options.enables(DiagnosticGroups.LINT_CHECKS)) {
      checks.add(lintChecks);
    }

    if (options.closurePass && options.enables(DiagnosticGroups.LINT_CHECKS)) {
      checks.add(checkRequiresAndProvidesSorted);
    }

    if (options.enables(DiagnosticGroups.MISSING_REQUIRE)
        || options.enables(DiagnosticGroups.STRICT_MISSING_REQUIRE)
        || options.enables(DiagnosticGroups.EXTRA_REQUIRE)) {
      checks.add(checkRequires);
    }

    if (options.shouldGenerateTypedExterns()) {
      checks.add(closureGoogScopeAliases);
      checks.add(closureRewriteClass);
      checks.add(generateIjs);
      checks.add(whitespaceWrapGoogModules);
      return checks;
    }

    if (options.closurePass) {
      checks.add(closureCheckModule);
      checks.add(closureRewriteModule);
    }

    if (options.declaredGlobalExternsOnWindow) {
      checks.add(declaredGlobalExternsOnWindow);
    }

    if (options.getLanguageIn() == LanguageMode.ECMASCRIPT6_TYPED
            && options.getLanguageOut() != LanguageMode.ECMASCRIPT6_TYPED) {
      checks.add(convertEs6TypedToEs6);
    }

    checks.add(checkMissingSuper);
    checks.add(checkVariableReferences);

    if (options.closurePass) {
      checks.add(closureGoogScopeAliases);
      checks.add(closureRewriteClass);
    }

    checks.add(checkSideEffects);

    if (options.enables(DiagnosticGroups.MISSING_PROVIDE)) {
      checks.add(checkProvides);
    }

    if (options.jqueryPass) {
      checks.add(jqueryAliases);
    }

    if (options.angularPass) {
      checks.add(angularPass);
    }

    if (!options.generateExportsAfterTypeChecking && options.generateExports) {
      checks.add(generateExports);
    }

    if (options.exportTestFunctions) {
      checks.add(exportTestFunctions);
    }

    if (options.closurePass) {
      checks.add(closurePrimitives);
    }

    if (options.chromePass) {
      checks.add(chromePass);
    }

    // It's important that the PolymerPass run *after* the ClosurePrimitives and ChromePass rewrites
    // and *before* the suspicious code checks. This is enforced in the assertValidOrder method.
    if (options.polymerPass) {
      checks.add(polymerPass);
    }

    if (options.checkSuspiciousCode
        || options.enables(DiagnosticGroups.GLOBAL_THIS)
        || options.enables(DiagnosticGroups.DEBUGGER_STATEMENT_PRESENT)) {
      checks.add(suspiciousCode);
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

    // It's important that the Dart super accessors pass run *before* es6ConvertSuper,
    // which is a "late" ES6 pass. This is enforced in the assertValidOrder method.
    if (options.dartPass && !options.getLanguageOut().isEs6OrHigher()) {
      checks.add(dartSuperAccessorsPass);
    }

    if (options.getLanguageIn().isEs6OrHigher() && !options.skipTranspilationAndCrash) {
      checks.add(es6ExternsCheck);
      TranspilationPasses.addEs6EarlyPasses(checks);
    }

    if (options.getLanguageIn().isEs6OrHigher() && !options.skipTranspilationAndCrash) {
      TranspilationPasses.addEs6LatePasses(checks);
      if (options.rewritePolyfills) {
        TranspilationPasses.addRewritePolyfillPass(checks);
      }
      // TODO(bradfordcsmith): This marking is really about how variable scoping is handled during
      //     type checking. It should really be handled in a more direct fashion.
      checks.add(markTranspilationDone);
    }

    if (options.raiseToEs6Typed()) {
      checks.add(convertToTypedES6);
    }

    if (!options.forceLibraryInjection.isEmpty()) {
      checks.add(injectRuntimeLibraries);
    }

    if (!options.skipNonTranspilationPasses) {
      addNonTranspilationCheckPasses(checks);
    }

    if (options.getLanguageIn().isEs6OrHigher() && !options.skipTranspilationAndCrash) {
      TranspilationPasses.addPostCheckPasses(checks);
    }


    // NOTE(dimvar): Tried to move this into the optimizations, but had to back off
    // because the very first pass, normalization, rewrites the code in a way that
    // causes loss of type information.
    // So, I will convert the remaining optimizations to use TypeI and test that only
    // in unit tests, not full builds. Once all passes are converted, then
    // drop the OTI-after-NTI altogether.
    // In addition, I will probably have a local edit of the repo that retains both
    // types on Nodes, so I can test full builds on my machine. We can't check in such
    // a change because it would greatly increase memory usage.
    if (options.getNewTypeInference()) {
      addOldTypeCheckerPasses(checks, options);
    }

    // When options.generateExportsAfterTypeChecking is true, run GenerateExports after
    // both type checkers, not just after NTI.
    if (options.generateExportsAfterTypeChecking && options.generateExports) {
      checks.add(generateExports);
    }

    checks.add(createEmptyPass("afterStandardChecks"));

    assertAllOneTimePasses(checks);
    assertValidOrderForChecks(checks);

    return checks;
  }

  private void addNonTranspilationCheckPasses(List<PassFactory> checks) {
    checks.add(convertStaticInheritance);

    // End of ES6 transpilation passes.

    checks.add(createEmptyPass("beforeTypeChecking"));

    if (options.getNewTypeInference()) {
      checks.add(symbolTableForNewTypeInference);
      checks.add(newTypeInference);
    }

    if (options.j2clPassMode.equals(CompilerOptions.J2clPassMode.AUTO)) {
      checks.add(j2clSourceFileChecker);
    }

    if (!options.allowsHotswapReplaceScript()) {
      checks.add(inlineTypeAliases);
    }

    if (!options.getNewTypeInference()) {
      addOldTypeCheckerPasses(checks, options);
    }

    if (!options.disables(DiagnosticGroups.CHECK_USELESS_CODE) ||
        (!options.getNewTypeInference() && !options.disables(DiagnosticGroups.MISSING_RETURN))) {
      checks.add(checkControlFlow);
    }

    // CheckAccessControls only works if check types is on.
    if (options.checkTypes &&
        (!options.disables(DiagnosticGroups.ACCESS_CONTROLS)
         || options.enables(DiagnosticGroups.CONSTANT_PROPERTY))) {
      checks.add(checkAccessControls);
    }

    if (!options.getNewTypeInference()) {
      // NTI performs this check already
      checks.add(checkConsts);
    }

    // Analyzer checks must be run after typechecking.
    if (options.enables(DiagnosticGroups.ANALYZER_CHECKS)) {
      checks.add(analyzerChecks);
    }

    if (options.checkEventfulObjectDisposalPolicy != CompilerOptions.DisposalCheckingPolicy.OFF) {
      checks.add(checkEventfulObjectDisposal);
    }

    if (options.checkGlobalNamesLevel.isOn()) {
      checks.add(checkGlobalNames);
    }

    checks.add(checkStrictMode);

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

    if (options.j2clPassMode.shouldAddJ2clPasses()) {
      checks.add(j2clChecksPass);
    }
  }

  @Override
  protected List<PassFactory> getOptimizations() {
    List<PassFactory> passes = new ArrayList<>();

    if (options.skipNonTranspilationPasses) {
      return passes;
    }

    // Gather property names in externs so they can be queried by the
    // optimizing passes.
    passes.add(gatherExternProperties);

    passes.add(garbageCollectChecks);

    if (options.instrumentForCoverage) {
      passes.add(instrumentForCodeCoverage);
    }

    // TODO(dimvar): convert this pass to use NTI. Low priority since it's
    // mostly unused. Converting it shouldn't block switching to NTI.
    if (options.runtimeTypeCheck && !options.getNewTypeInference()) {
      passes.add(runtimeTypeCheck);
    }

    // Inlines functions that perform dynamic accesses to static properties of parameters that are
    // typed as {Function}. This turns a dynamic access to a static property of a class definition
    // into a fully qualified access and in so doing enables better dead code stripping.
    if (options.j2clPassMode.shouldAddJ2clPasses()) {
      passes.add(j2clPass);
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

    assertAllOneTimePasses(passes);

    // Inline aliases so that following optimizations don't have to understand alias chains.
    if (options.collapseProperties) {
      passes.add(aggressiveInlineAliases);
    }

    // Inline getters/setters in J2CL classes so that Object.defineProperties() calls (resulting
    // from desugaring) don't block class stripping.
    if (options.j2clPassMode.shouldAddJ2clPasses() && options.collapseProperties) {
      // Relies on collapseProperties-triggered aggressive alias inlining.
      passes.add(j2clPropertyInlinerPass);
    }

    // Collapsing properties can undo constant inlining, so we do this before
    // the main optimization loop.
    if (options.collapseProperties) {
      passes.add(collapseProperties);
    }

    if (options.inferConsts) {
      passes.add(inferConsts);
    }

    if (options.reportPath != null && options.smartNameRemoval) {
      passes.add(initNameAnalyzeReport);
    }

    // TODO(mlourenco): Ideally this would be in getChecks() instead of getOptimizations(). But
    // for that it needs to understand constant properties as well. See b/31301233#10.
    // Needs to happen after inferConsts and collapseProperties. Detects whether invocations of
    // the method goog.string.Const.from are done with an argument which is a string literal.
    passes.add(checkConstParams);

    // Running smart name removal before disambiguate properties allows disambiguate properties
    // to be more effective if code that would prevent disambiguation can be removed.
    if (options.extraSmartNameRemoval && options.smartNameRemoval) {

      // These passes remove code that is dead because of define flags.
      // If the dead code is weakly typed, running these passes before property
      // disambiguation results in more code removal.
      // The passes are one-time on purpose. (The later runs are loopable.)
      if (options.foldConstants && (options.inlineVariables || options.inlineLocalVariables)) {
        passes.add(earlyInlineVariables);
        passes.add(earlyPeepholeOptimizations);
      }

      passes.add(extraSmartNamePass);
    }

    // RewritePolyfills is overly generous in the polyfills it adds.  After type
    // checking and early smart name removal, we can use the new type information
    // to figure out which polyfilled prototype methods are actually called, and
    // which were "false alarms" (i.e. calling a method of the same name on a
    // user-provided class).  We also remove any polyfills added by code that
    // was smart-name-removed.  This is a one-time pass, since it does not work
    // after inlining - we do not attempt to aggressively remove polyfills used
    // by code that is only flow-sensitively dead.
    if (options.rewritePolyfills) {
      passes.add(removeUnusedPolyfills);
    }

    // Property disambiguation should only run once and needs to be done
    // soon after type checking, both so that it can make use of type
    // information and so that other passes can take advantage of the renamed
    // properties.
    if (options.shouldDisambiguateProperties()) {
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

    // Must run after ProcessClosurePrimitives, Es6ConvertSuper, and assertion removals, but
    // before OptimizeCalls (specifically, OptimizeParameters) and DevirtualizePrototypeMethods.
    if (options.removeSuperMethods) {
      passes.add(removeSuperMethodsPass);
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

    // Because FlowSensitiveInlineVariables does not operate on the global scope due to compilation
    // time, we need to run it once before InlineFunctions so that we don't miss inlining
    // opportunities when a function will be inlined into the global scope.
    if (options.inlineVariables || options.inlineLocalVariables) {
      passes.add(flowSensitiveInlineVariables);
    }

    passes.addAll(getMainOptimizationLoop());
    passes.add(createEmptyPass("afterMainOptimizations"));

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

    if (options.inlineVariables || options.inlineLocalVariables) {
      passes.add(flowSensitiveInlineVariables);

      // After inlining some of the variable uses, some variables are unused.
      // Re-run remove unused vars to clean it up.
      if (options.removeUnusedVars || options.removeUnusedLocalVars) {
        passes.add(lastRemoveUnusedVars());
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
    if (options.extractPrototypeMemberDeclarations != ExtractPrototypeMemberDeclarationsMode.OFF) {
      passes.add(extractPrototypeMemberDeclarations);
    }

    if (options.shouldAmbiguateProperties() &&
        (options.propertyRenaming == PropertyRenamingPolicy.ALL_UNQUOTED)) {
      passes.add(ambiguateProperties);
    }

    if (options.propertyRenaming == PropertyRenamingPolicy.ALL_UNQUOTED) {
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

    // Passes after this point can no longer depend on normalized AST
    // assumptions.
    passes.add(markUnnormalized);

    if (options.coalesceVariableNames) {
      passes.add(coalesceVariableNames);

      // coalesceVariables creates identity assignments and more redundant code
      // that can be removed, rerun the peephole optimizations to clean them
      // up.
      if (options.foldConstants) {
        passes.add(peepholeOptimizationsOnce);
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

    // Raise to ES6, if allowed
    if (options.getLanguageOut().isEs6OrHigher()) {
      passes.add(optimizeToEs6);
      passes.add(rewriteBindThis);
    }

    assertValidOrderForOptimizations(passes);
    return passes;
  }

  /** Creates the passes for the main optimization loop. */
  private List<PassFactory> getMainOptimizationLoop() {
    List<PassFactory> passes = new ArrayList<>();
    if (options.inlineGetters) {
      passes.add(inlineSimpleMethods);
    }

    passes.addAll(getCodeRemovingPasses());

    if (options.inlineFunctions || options.inlineLocalFunctions) {
      passes.add(inlineFunctions);
    }

    if (options.shouldInlineProperties()) {
      passes.add(inlineProperties);
    }

    boolean runOptimizeCalls = options.optimizeCalls
        || options.optimizeParameters
        || options.optimizeReturns;

    if (options.removeUnusedVars || options.removeUnusedLocalVars) {
      if (options.deadAssignmentElimination) {
        passes.add(deadAssignmentsElimination);

        // The Polymer source is usually not included in the compilation, but it creates
        // getters/setters for many properties in compiled code. Dead property assignment
        // elimination is only safe when it knows about getters/setters. Therefore, we skip
        // it if the polymer pass is enabled.
        if (!options.polymerPass) {
          passes.add(deadPropertyAssignmentElimination);
        }
      }
      if (!runOptimizeCalls) {
        passes.add(getRemoveUnusedVars("removeUnusedVars", false));
      }
    }

    if (runOptimizeCalls) {
      passes.add(optimizeCalls);
      // RemoveUnusedVars cleans up after optimizeCalls, so we run it here.
      // It has a special name because otherwise PhaseOptimizer would change its
      // position in the optimization loop.
      if (options.optimizeCalls) {
        passes.add(
            getRemoveUnusedVars("removeUnusedVars_afterOptimizeCalls", true));
      }
    }

    if (options.j2clPassMode.shouldAddJ2clPasses()) {
      passes.add(j2clOptBundlePass);
    }

    assertAllLoopablePasses(passes);
    return passes;
  }

  /** Creates several passes aimed at removing code. */
  private List<PassFactory> getCodeRemovingPasses() {
    List<PassFactory> passes = new ArrayList<>();
    if (options.collapseObjectLiterals) {
      passes.add(collapseObjectLiterals);
    }

    if (options.inlineVariables || options.inlineLocalVariables) {
      passes.add(inlineVariables);
    } else if (options.inlineConstantVars) {
      passes.add(inlineConstants);
    }

    if (options.foldConstants) {
      passes.add(peepholeOptimizations);
    }

    if (options.removeDeadCode) {
      passes.add(removeUnreachableCode);
    }

    if (options.removeUnusedPrototypeProperties) {
      passes.add(removeUnusedPrototypeProperties);
    }

    if (options.removeUnusedClassProperties) {
      passes.add(removeUnusedClassProperties);
    }

    assertAllLoopablePasses(passes);
    return passes;
  }

  /**
   * Checks for code that is probably wrong (such as stray expressions).
   */
  private final HotSwapPassFactory checkSideEffects =
      new HotSwapPassFactory("checkSideEffects", true) {
    @Override
    protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
      return new CheckSideEffects(
          compiler, options.checkSuspiciousCode, protectHiddenSideEffects);
    }
  };

  /**
   * Removes the "protector" functions that were added by CheckSideEffects.
   */
  private final PassFactory stripSideEffectProtection =
      new PassFactory("stripSideEffectProtection", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
      return new CheckSideEffects.StripProtection(compiler);
    }
  };

  /**
   * Checks for code that is probably wrong (such as stray expressions).
   */
  private final HotSwapPassFactory suspiciousCode =
      new HotSwapPassFactory("suspiciousCode", true) {
    @Override
    protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
      List<Callback> sharedCallbacks = new ArrayList<>();
      if (options.checkSuspiciousCode) {
        sharedCallbacks.add(new CheckSuspiciousCode());
        sharedCallbacks.add(new CheckDuplicateCase(compiler));
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

  /**
   * Checks that {@code pass1} comes before {@code pass2} in {@code passList}, if both are present.
   */
  private void assertPassOrder(
      List<PassFactory> passList, PassFactory pass1, PassFactory pass2, String msg) {
    int pass1Index = passList.indexOf(pass1);
    int pass2Index = passList.indexOf(pass2);
    if (pass1Index != -1 && pass2Index != -1) {
      Preconditions.checkState(pass1Index < pass2Index, msg);
    }
  }

  /**
   * Certain checks need to run in a particular order. For example, the PolymerPass
   * will not work correctly unless it runs after the goog.provide() processing.
   * This enforces those constraints.
   * @param checks The list of check passes
   */
  private void assertValidOrderForChecks(List<PassFactory> checks) {
    assertPassOrder(
        checks,
        closureRewriteModule,
        checkVariableReferences,
        "If checkVariableReferences runs before closureRewriteModule, it will produce invalid"
            + " warnings because it will think of module-scoped variables as global variables.");
    assertPassOrder(
        checks,
        closureRewriteModule,
        processDefines,
        "Must rewrite goog.module before processing @define's, so that @defines in modules work.");
    assertPassOrder(
        checks,
        closurePrimitives,
        polymerPass,
        "The Polymer pass must run after goog.provide processing.");
    assertPassOrder(
        checks,
        chromePass,
        polymerPass,
        "The Polymer pass must run after ChromePass processing.");
    assertPassOrder(
        checks,
        polymerPass,
        suspiciousCode,
        "The Polymer pass must run before suspiciousCode processing.");
    assertPassOrder(
        checks,
        dartSuperAccessorsPass,
        TranspilationPasses.es6ConvertSuper,
        "The Dart super accessors pass must run before ES6->ES3 super lowering.");

    if (checks.contains(closureGoogScopeAliases)) {
      Preconditions.checkState(
          checks.contains(checkVariableReferences),
          "goog.scope processing requires variable checking");
    }
    assertPassOrder(
        checks,
        checkVariableReferences,
        closureGoogScopeAliases,
        "Variable checking must happen before goog.scope processing.");

    assertPassOrder(
        checks,
        TranspilationPasses.es6ConvertSuper,
        removeSuperMethodsPass,
        "Super-call method removal must run after Es6 super rewriting, "
            + "because Es6 super calls are matched on their post-processed form.");

    assertPassOrder(
        checks,
        closurePrimitives,
        removeSuperMethodsPass,
        "Super-call method removal must run after Es6 super rewriting, "
            + "because Closure base calls are expected to be in post-processed form.");

    assertPassOrder(
        checks,
        closureCodeRemoval,
        removeSuperMethodsPass,
        "Super-call method removal must run after closure code removal, because "
            + "removing assertions may make more super calls eligible to be stripped.");
  }

  /**
   * Certain optimizations need to run in a particular order. For example, OptimizeCalls must run
   * before RemoveSuperMethodsPass, because the former can invalidate assumptions in the latter.
   * This enforces those constraints.
   * @param optimizations The list of optimization passes
   */
  private void assertValidOrderForOptimizations(List<PassFactory> optimizations) {
    assertPassOrder(optimizations, removeSuperMethodsPass, optimizeCalls,
        "RemoveSuperMethodsPass must run before OptimizeCalls.");

    assertPassOrder(optimizations, removeSuperMethodsPass, devirtualizePrototypeMethods,
        "RemoveSuperMethodsPass must run before DevirtualizePrototypeMethods.");
  }

  /** Checks that all constructed classes are goog.require()d. */
  private final HotSwapPassFactory checkRequires =
      new HotSwapPassFactory("checkRequires", true) {
    @Override
    protected HotSwapCompilerPass create(AbstractCompiler compiler) {
      return new CheckRequiresForConstructors(compiler,
          CheckRequiresForConstructors.Mode.FULL_COMPILE);
    }
  };

  /** Makes sure @constructor is paired with goog.provides(). */
  private final HotSwapPassFactory checkProvides =
      new HotSwapPassFactory("checkProvides", true) {
    @Override
    protected HotSwapCompilerPass create(AbstractCompiler compiler) {
      return new CheckProvides(compiler);
    }
  };

  private static final DiagnosticType GENERATE_EXPORTS_ERROR =
      DiagnosticType.error(
          "JSC_GENERATE_EXPORTS_ERROR",
          "Exports can only be generated if export symbol/property " +
          "functions are set.");

  /** Verifies JSDoc annotations are used properly. */
  private final HotSwapPassFactory checkJsDoc = new HotSwapPassFactory("checkJsDoc", true) {
    @Override
    protected HotSwapCompilerPass create(AbstractCompiler compiler) {
      return new CheckJSDoc(compiler);
    }
  };

  /** Generates exports for @export annotations. */
  private final PassFactory generateExports = new PassFactory("generateExports", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      CodingConvention convention = compiler.getCodingConvention();
      if (convention.getExportSymbolFunction() != null &&
          convention.getExportPropertyFunction() != null) {
        final GenerateExports pass = new GenerateExports(compiler,
            options.exportLocalPropertyDefinitions,
            convention.getExportSymbolFunction(),
            convention.getExportPropertyFunction());
        return new CompilerPass() {
          @Override
          public void process(Node externs, Node root) {
            pass.process(externs, root);
            if (exportedNames == null) {
              exportedNames = new HashSet<>();
            }

            exportedNames.addAll(pass.getExportedVariableNames());
          }
        };
      } else {
        return new ErrorPass(compiler, GENERATE_EXPORTS_ERROR);
      }
    }
  };

  private final PassFactory generateIjs = new PassFactory("generateIjs", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new ConvertToTypedInterface(compiler);
    }
  };

  /** Generates exports for functions associated with JsUnit. */
  private final PassFactory exportTestFunctions =
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
  private final PassFactory gatherRawExports =
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
            exportedNames = new HashSet<>();
          }
          exportedNames.addAll(pass.getExportedVariableNames());
        }
      };
    }
  };

  /** Closure pre-processing pass. */
  private final HotSwapPassFactory closurePrimitives =
      new HotSwapPassFactory("closurePrimitives", true) {
    @Override
    protected HotSwapCompilerPass create(AbstractCompiler compiler) {
      maybeInitializePreprocessorSymbolTable(compiler);
      final ProcessClosurePrimitives pass = new ProcessClosurePrimitives(
          compiler,
          preprocessorSymbolTable,
          options.brokenClosureRequiresLevel,
          options.shouldPreservesGoogProvidesAndRequires());

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
  private final PassFactory jqueryAliases = new PassFactory("jqueryAliases", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new ExpandJqueryAliases(compiler);
    }
  };

  /** Process AngularJS-specific annotations. */
  private final HotSwapPassFactory angularPass =
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
  private final PassFactory replaceMessages = new PassFactory("replaceMessages", true) {
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

  private final PassFactory replaceMessagesForChrome =
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
  private final HotSwapPassFactory closureGoogScopeAliases =
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

  private final PassFactory injectRuntimeLibraries =
      new PassFactory("InjectRuntimeLibraries", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
      return new InjectRuntimeLibraries(compiler);
    }
  };

  private final PassFactory es6ExternsCheck =
      new PassFactory("es6ExternsCheck", true) {
        @Override
        protected CompilerPass create(final AbstractCompiler compiler) {
          return new Es6ExternsCheck(compiler);
        }
      };

  /**
   * Desugars ES6_TYPED features into ES6 code.
   */
  final HotSwapPassFactory convertEs6TypedToEs6 =
      new HotSwapPassFactory("convertEs6Typed", true) {
    @Override
    protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
      return new Es6TypedToEs6Converter(compiler);
    }
  };

  private final PassFactory convertStaticInheritance =
      new PassFactory("Es6StaticInheritance", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new Es6ToEs3ClassSideInheritance(compiler);
    }
  };

  private final PassFactory inlineTypeAliases =
      new PassFactory("inlineTypeAliases", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new InlineAliases(compiler);
    }
  };

  /** Inlines type aliases if they are explicitly or effectively const. */
  private final PassFactory aggressiveInlineAliases =
      new PassFactory("aggressiveInlineAliases", false) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new AggressiveInlineAliases(compiler);
        }
      };

  private final PassFactory convertToTypedES6 =
      new PassFactory("ConvertToTypedES6", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new JsdocToEs6TypedConverter(compiler);
    }
  };

  private final PassFactory markTranspilationDone = new PassFactory("setLanguageMode", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override
        public void process(Node externs, Node root) {
          LanguageMode langOut = options.getLanguageOut();
          compiler.setLanguageMode(langOut.isEs6OrHigher() ? LanguageMode.ECMASCRIPT5 : langOut);
        }
      };
    }
  };

  /** Applies aliases and inlines goog.scope. */
  private final PassFactory declaredGlobalExternsOnWindow =
      new PassFactory("declaredGlobalExternsOnWindow", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new DeclaredGlobalExternsOnWindow(compiler);
    }
  };

  /** Rewrites goog.defineClass */
  private final HotSwapPassFactory closureRewriteClass =
      new HotSwapPassFactory("closureRewriteClass", true) {
    @Override
    protected HotSwapCompilerPass create(AbstractCompiler compiler) {
      return new ClosureRewriteClass(compiler);
    }
  };

  /** Checks of correct usage of goog.module */
  private final HotSwapPassFactory closureCheckModule =
      new HotSwapPassFactory("closureCheckModule", true) {
    @Override
    protected HotSwapCompilerPass create(AbstractCompiler compiler) {
      return new ClosureCheckModule(compiler);
    }
  };

  /** Rewrites goog.module */
  private final HotSwapPassFactory closureRewriteModule =
      new HotSwapPassFactory("closureRewriteModule", true) {
        @Override
        protected HotSwapCompilerPass create(AbstractCompiler compiler) {
          maybeInitializePreprocessorSymbolTable(compiler);
          maybeInitializeModuleRewriteState();
          return new ClosureRewriteModule(compiler, preprocessorSymbolTable, moduleRewriteState);
        }
      };

  /** Checks that CSS class names are wrapped in goog.getCssName */
  private final PassFactory closureCheckGetCssName =
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
  private final PassFactory closureReplaceGetCssName =
      new PassFactory("closureReplaceGetCssName", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override
        public void process(Node externs, Node jsRoot) {
          Map<String, Integer> newCssNames = null;
          if (options.gatherCssNames) {
            newCssNames = new HashMap<>();
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
  private final PassFactory createSyntheticBlocks =
      new PassFactory("createSyntheticBlocks", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new CreateSyntheticBlocks(compiler,
          options.syntheticBlockStartMarker,
          options.syntheticBlockEndMarker);
    }
  };

  private final PassFactory earlyPeepholeOptimizations =
      new PassFactory("earlyPeepholeOptimizations", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new PeepholeOptimizationsPass(compiler,
          new PeepholeRemoveDeadCode());
    }
  };

  private final PassFactory earlyInlineVariables =
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
  private static CompilerPass createPeepholeOptimizationsPass(AbstractCompiler compiler) {
    final boolean late = false;
    final boolean useTypesForOptimization = compiler.getOptions().useTypesForLocalOptimization;
    return new PeepholeOptimizationsPass(compiler,
          new MinimizeExitPoints(compiler),
          new PeepholeMinimizeConditions(late, useTypesForOptimization),
          new PeepholeSubstituteAlternateSyntax(late),
          new PeepholeReplaceKnownMethods(late, useTypesForOptimization),
          new PeepholeRemoveDeadCode(),
          new PeepholeFoldConstants(late, useTypesForOptimization),
          new PeepholeCollectPropertyAssignments());
  }

  /** Various peephole optimizations. */
  private final PassFactory peepholeOptimizations =
      new PassFactory("peepholeOptimizations", false /* oneTimePass */) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return createPeepholeOptimizationsPass(compiler);
    }
  };

  /** Various peephole optimizations. */
  private final PassFactory peepholeOptimizationsOnce =
      new PassFactory("peepholeOptimizations", true /* oneTimePass */) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return createPeepholeOptimizationsPass(compiler);
    }
  };

  /** Same as peepholeOptimizations but aggressively merges code together */
  private final PassFactory latePeepholeOptimizations =
      new PassFactory("latePeepholeOptimizations", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      final boolean late = true;
      final boolean useTypesForOptimization = options.useTypesForLocalOptimization;
      return new PeepholeOptimizationsPass(compiler,
            new StatementFusion(options.aggressiveFusion),
            new PeepholeRemoveDeadCode(),
            new PeepholeMinimizeConditions(late, useTypesForOptimization),
            new PeepholeSubstituteAlternateSyntax(late),
            new PeepholeReplaceKnownMethods(late, useTypesForOptimization),
            new PeepholeFoldConstants(late, useTypesForOptimization),
            new ReorderConstantExpression());
    }
  };

  /** Checks that all variables are defined. */
  private final HotSwapPassFactory checkVars =
      new HotSwapPassFactory("checkVars", true) {
    @Override
    protected HotSwapCompilerPass create(AbstractCompiler compiler) {
      return new VarCheck(compiler);
    }
  };

  /** Infers constants. */
  private final PassFactory inferConsts = new PassFactory("inferConsts", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new InferConsts(compiler);
    }
  };

  /** Checks for RegExp references. */
  private final PassFactory checkRegExp =
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
  private final HotSwapPassFactory checkVariableReferencesForTranspileOnly =
      new HotSwapPassFactory("checkVariableReferences", true) {
    @Override
    protected HotSwapCompilerPass create(AbstractCompiler compiler) {
      return new VariableReferenceCheck(compiler, true);
    }
  };

  /** Checks that references to variables look reasonable. */
  private final HotSwapPassFactory checkVariableReferences =
      new HotSwapPassFactory("checkVariableReferences", true) {
    @Override
    protected HotSwapCompilerPass create(AbstractCompiler compiler) {
      return new VariableReferenceCheck(compiler);
    }
  };

  /** Checks that references to variables look reasonable. */
  private final HotSwapPassFactory checkMissingSuper =
      new HotSwapPassFactory("checkMissingSuper", true) {
        @Override
        protected HotSwapCompilerPass create(AbstractCompiler compiler) {
          return new CheckMissingSuper(compiler);
        }
      };

  /** Pre-process goog.testing.ObjectPropertyString. */
  private final PassFactory objectPropertyStringPreprocess =
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
  private final PassFactory clearTypedScopePass =
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

  private final PassFactory symbolTableForNewTypeInference =
      new PassFactory("GlobalTypeInfo", true) {
        @Override
        protected CompilerPass create(final AbstractCompiler compiler) {
          return compiler.getSymbolTable();
        }
      };

  private final PassFactory newTypeInference =
      new PassFactory("NewTypeInference", true) {
        @Override
        protected CompilerPass create(final AbstractCompiler compiler) {
          return new NewTypeInference(compiler);
        }
      };

  private final HotSwapPassFactory inferJsDocInfo =
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
  private final HotSwapPassFactory checkTypes =
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
  private final HotSwapPassFactory checkControlFlow =
      new HotSwapPassFactory("checkControlFlow", true) {
    @Override
    protected HotSwapCompilerPass create(AbstractCompiler compiler) {
      List<Callback> callbacks = new ArrayList<>();
      if (!options.disables(DiagnosticGroups.CHECK_USELESS_CODE)) {
        callbacks.add(new CheckUnreachableCode(compiler));
      }
      if (!options.getNewTypeInference() && !options.disables(DiagnosticGroups.MISSING_RETURN)) {
        callbacks.add(
            new CheckMissingReturn(compiler));
      }
      return combineChecks(compiler, callbacks);
    }
  };

  /** Checks access controls. Depends on type-inference. */
  private final HotSwapPassFactory checkAccessControls =
      new HotSwapPassFactory("checkAccessControls", true) {
    @Override
    protected HotSwapCompilerPass create(AbstractCompiler compiler) {
      return new CheckAccessControls(
          compiler, options.enforceAccessControlCodingConventions);
    }
  };

  private final HotSwapPassFactory lintChecks =
      new HotSwapPassFactory("lintChecks", true) {
        @Override
        protected HotSwapCompilerPass create(AbstractCompiler compiler) {
          ImmutableList.Builder<Callback> callbacks =
              ImmutableList.<Callback>builder()
                  .add(new CheckEmptyStatements(compiler))
                  .add(new CheckEnums(compiler))
                  .add(new CheckInterfaces(compiler))
                  .add(new CheckJSDocStyle(compiler))
                  .add(new CheckMissingSemicolon(compiler))
                  .add(new CheckPrimitiveAsObject(compiler))
                  .add(new CheckPrototypeProperties(compiler))
                  .add(new CheckUnusedLabels(compiler))
                  .add(new CheckUselessBlocks(compiler));
          return combineChecks(compiler, callbacks.build());
        }
      };

  private final HotSwapPassFactory analyzerChecks =
      new HotSwapPassFactory("analyzerChecks", true) {
    @Override
    protected HotSwapCompilerPass create(AbstractCompiler compiler) {
      ImmutableList.Builder<Callback> callbacks = ImmutableList.<Callback>builder();
      if (options.enables(DiagnosticGroups.ANALYZER_CHECKS_INTERNAL)) {
        callbacks.add(new CheckNullableReturn(compiler))
          .add(new CheckArrayWithGoogObject(compiler))
          .add(new ImplicitNullabilityCheck(compiler));
      }
      // These are grouped together for better execution efficiency.
      if (options.enables(DiagnosticGroups.UNUSED_PRIVATE_PROPERTY)) {
        callbacks.add(new CheckUnusedPrivateProperties(compiler));
      }
      return combineChecks(compiler, callbacks.build());
    }
  };

  private final HotSwapPassFactory checkRequiresAndProvidesSorted =
      new HotSwapPassFactory("checkRequiresAndProvidesSorted", true) {
    @Override
    protected HotSwapCompilerPass create(AbstractCompiler compiler) {
      return new CheckRequiresAndProvidesSorted(compiler);
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
      // If NTI is enabled, erase the NTI types from the AST before adding the old types.
      if (this.compiler.getOptions().getNewTypeInference()) {
        NodeTraversal.traverseEs6(
            this.compiler, root,
            new NodeTraversal.AbstractPostOrderCallback(){
              @Override
              public void visit(NodeTraversal t, Node n, Node parent) {
                n.setTypeI(null);
              }
            });
      }

      this.compiler.setMostRecentTypechecker(MostRecentTypechecker.OTI);
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
  private final PassFactory checkGlobalNames =
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
  private final PassFactory checkStrictMode =
      new PassFactory("checkStrictMode", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new StrictModeCheck(compiler);
    }
  };

  /** Process goog.tweak.getTweak() calls. */
  private final PassFactory processTweaks = new PassFactory("processTweaks", true) {
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
  private final PassFactory processDefines = new PassFactory("processDefines", true) {
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
  private final PassFactory checkConsts = new PassFactory("checkConsts", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new ConstCheck(compiler);
    }
  };

  /** Checks that the arguments are constants */
  private final PassFactory checkConstParams =
      new PassFactory("checkConstParams", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new ConstParamCheck(compiler);
    }
  };

  /** Check memory bloat patterns */
  private final PassFactory checkEventfulObjectDisposal =
      new PassFactory("checkEventfulObjectDisposal", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new CheckEventfulObjectDisposal(compiler,
          options.checkEventfulObjectDisposalPolicy);
    }
  };

  /** Computes the names of functions for later analysis. */
  private final PassFactory computeFunctionNames =
      new PassFactory("computeFunctionNames", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return ((functionNames = new FunctionNames(compiler)));
    }
  };

  /** Inserts run-time type assertions for debugging. */
  private final PassFactory runtimeTypeCheck =
      new PassFactory("runtimeTypeCheck", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new RuntimeTypeCheck(compiler,
          options.runtimeTypeCheckLogFunction);
    }
  };

  /** Generates unique ids. */
  private final PassFactory replaceIdGenerators =
      new PassFactory("replaceIdGenerators", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override public void process(Node externs, Node root) {
          ReplaceIdGenerators pass =
              new ReplaceIdGenerators(
                  compiler, options.idGenerators, options.generatePseudoNames,
                  options.idGeneratorsMapSerialized, options.xidHashFunction);
          pass.process(externs, root);
          idGeneratorMap = pass.getSerializedIdMappings();
        }
      };
    }
  };

  /** Replace strings. */
  private final PassFactory replaceStrings = new PassFactory("replaceStrings", true) {
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
  private final PassFactory optimizeArgumentsArray =
      new PassFactory("optimizeArgumentsArray", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new OptimizeArgumentsArray(compiler);
    }
  };

  /** Remove variables set to goog.abstractMethod. */
  private final PassFactory closureCodeRemoval =
      new PassFactory("closureCodeRemoval", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
      return new ClosureCodeRemoval(compiler, options.removeAbstractMethods,
          options.removeClosureAsserts);
    }
  };

  /** Special case optimizations for closure functions. */
  private final PassFactory closureOptimizePrimitives =
      new PassFactory("closureOptimizePrimitives", true) {
        @Override
        protected CompilerPass create(final AbstractCompiler compiler) {
          return new ClosureOptimizePrimitives(
              compiler,
              compiler.getOptions().propertyRenaming == PropertyRenamingPolicy.ALL_UNQUOTED);
        }
      };

  /** Puts global symbols into a single object. */
  private final PassFactory rescopeGlobalSymbols =
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
  private final PassFactory collapseProperties =
      new PassFactory("collapseProperties", true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new CollapseProperties(compiler);
        }
      };

  /** Rewrite properties as variables. */
  private final PassFactory collapseObjectLiterals =
      new PassFactory("collapseObjectLiterals", false) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new InlineObjectLiterals(
          compiler, compiler.getUniqueNameIdSupplier());
    }
  };

  /** Disambiguate property names based on the coding convention. */
  private final PassFactory disambiguatePrivateProperties =
      new PassFactory("disambiguatePrivateProperties", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new DisambiguatePrivateProperties(compiler);
    }
  };

  /** Disambiguate property names based on type information. */
  private final PassFactory disambiguateProperties =
      new PassFactory("disambiguateProperties", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new DisambiguateProperties(
          compiler, options.propertyInvalidationErrors);
    }
  };

  /**
   * Chain calls to functions that return this.
   */
  private final PassFactory chainCalls = new PassFactory("chainCalls", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
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
    protected CompilerPass create(AbstractCompiler compiler) {
      return new DevirtualizePrototypeMethods(compiler);
    }
  };

  /**
   * Optimizes unused function arguments, unused return values, and inlines
   * constant parameters. Also runs RemoveUnusedVars.
   */
  private final PassFactory optimizeCalls =
      new PassFactory("optimizeCalls", false) {
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
    protected CompilerPass create(AbstractCompiler compiler) {
      return new PureFunctionIdentifier.Driver(
          compiler, options.debugFunctionSideEffectsPath);
    }
  };

  /**
   * Look for function calls that have no side effects, and annotate them
   * that way.
   */
  private final PassFactory markNoSideEffectCalls =
      new PassFactory("markNoSideEffectCalls", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new MarkNoSideEffectCalls(compiler);
    }
  };

  /** Inlines variables heuristically. */
  private final PassFactory inlineVariables =
      new PassFactory("inlineVariables", false) {
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

  /** Inlines variables that are marked as constants. */
  private final PassFactory inlineConstants =
      new PassFactory("inlineConstants", false) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new InlineVariables(
          compiler, InlineVariables.Mode.CONSTANTS_ONLY, true);
    }
  };

  /**
   * Use data flow analysis to remove dead branches.
   */
  private final PassFactory removeUnreachableCode =
      new PassFactory("removeUnreachableCode", false) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new UnreachableCodeElimination(compiler, true);
    }
  };

  /**
   * Use data flow analysis to remove dead branches.
   */
  private final PassFactory removeUnusedPolyfills =
      new PassFactory("removeUnusedPolyfills", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new RemoveUnusedPolyfills(compiler);
    }
  };

  /**
   * Remove prototype properties that do not appear to be used.
   */
  private final PassFactory removeUnusedPrototypeProperties =
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
  private final PassFactory removeUnusedClassProperties =
      new PassFactory("removeUnusedClassProperties", false) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new RemoveUnusedClassProperties(
          compiler, options.removeUnusedConstructorProperties);
    }
  };

  private final PassFactory initNameAnalyzeReport = new PassFactory("initNameAnalyzeReport", true) {
     @Override
     protected CompilerPass create(final AbstractCompiler compiler) {
       return new CompilerPass() {
         @Override
         public void process(Node externs, Node root) {
           NameAnalyzer.createEmptyReport(compiler, options.reportPath);
         }
       };
     }
  };

  /**
   * Process smart name processing - removes unused classes and does referencing
   * starting with minimum set of names.
   */
  private final PassFactory extraSmartNamePass = new PassFactory("smartNamePass", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
      return new NameAnalyzer(compiler, true, options.reportPath);
    }
  };

  private final PassFactory smartNamePass = new PassFactory("smartNamePass", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
      return new NameAnalyzer(compiler, true, options.reportPath);
    }
  };

  private final PassFactory smartNamePass2 = new PassFactory("smartNamePass", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
      return new NameAnalyzer(compiler, true, null);
    }
  };

  /** Inlines simple methods, like getters */
  private final PassFactory inlineSimpleMethods =
      new PassFactory("inlineSimpleMethods", false) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new InlineSimpleMethods(compiler);
    }
  };

  /** Kills dead assignments. */
  private final PassFactory deadAssignmentsElimination =
      new PassFactory("deadAssignmentsElimination", false) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new DeadAssignmentsElimination(compiler);
    }
  };

  /** Kills dead property assignments. */
  private final PassFactory deadPropertyAssignmentElimination =
      new PassFactory("deadPropertyAssignmentElimination", false) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new DeadPropertyAssignmentElimination(compiler);
        }
      };

  /** Inlines function calls. */
  private final PassFactory inlineFunctions =
      new PassFactory("inlineFunctions", false) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new InlineFunctions(
              compiler,
              compiler.getUniqueNameIdSupplier(),
              options.inlineFunctions,
              options.inlineLocalFunctions,
              true,
              options.assumeStrictThis()
                  || options.getLanguageIn() == LanguageMode.ECMASCRIPT5_STRICT,
              options.assumeClosuresOnlyCaptureReferences,
              options.maxFunctionSizeAfterInlining);
        }
      };

  /** Inlines constant properties. */
  private final PassFactory inlineProperties =
      new PassFactory("inlineProperties", false) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new InlineProperties(compiler);
    }
  };

  private PassFactory getRemoveUnusedVars(String name, final boolean modifyCallSites) {
    return getRemoveUnusedVars(name, modifyCallSites, false /* isOneTimePass */);
  }

  private PassFactory lastRemoveUnusedVars() {
    return getRemoveUnusedVars("removeUnusedVars", false, true /* isOneTimePass */);
  }

  private PassFactory getRemoveUnusedVars(
      String name, final boolean modifyCallSites, boolean isOneTimePass) {
    /** Removes variables that are never used. */
    return new PassFactory(name, isOneTimePass) {
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
            modifyCallSites);
      }
    };
  }

  /**
   * Move global symbols to a deeper common module
   */
  private final PassFactory crossModuleCodeMotion =
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
  private final PassFactory crossModuleMethodMotion =
      new PassFactory(Compiler.CROSS_MODULE_METHOD_MOTION_NAME, false) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new CrossModuleMethodMotion(
          compiler, crossModuleIdGenerator,
          // Only move properties in externs if we're not treating
          // them as exports.
          options.removeUnusedPrototypePropertiesInExterns,
          options.crossModuleCodeMotionNoStubMethods);
    }
  };

  /** A data-flow based variable inliner. */
  private final PassFactory flowSensitiveInlineVariables =
      new PassFactory("flowSensitiveInlineVariables", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new FlowSensitiveInlineVariables(compiler);
    }
  };

  /** Uses register-allocation algorithms to use fewer variables. */
  private final PassFactory coalesceVariableNames =
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
  private final PassFactory exploitAssign = new PassFactory("exploitAssign", true) {
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
  private final PassFactory collapseVariableDeclarations =
      new PassFactory("collapseVariableDeclarations", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new CollapseVariableDeclarations(compiler);
    }
  };

  /**
   * Extracts common sub-expressions.
   */
  private final PassFactory extractPrototypeMemberDeclarations =
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
  private final PassFactory rewriteFunctionExpressions =
      new PassFactory("rewriteFunctionExpressions", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new FunctionRewriter(compiler);
    }
  };

  /** Collapses functions to not use the VAR keyword. */
  private final PassFactory collapseAnonymousFunctions =
      new PassFactory("collapseAnonymousFunctions", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new CollapseAnonymousFunctions(compiler);
    }
  };

  /** Moves function declarations to the top, to simulate actual hoisting. */
  private final PassFactory moveFunctionDeclarations =
      new PassFactory("moveFunctionDeclarations", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new MoveFunctionDeclarations(compiler);
    }
  };

  private final PassFactory nameUnmappedAnonymousFunctions =
      new PassFactory("nameAnonymousFunctions", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new NameAnonymousFunctions(compiler);
    }
  };

  private final PassFactory nameMappedAnonymousFunctions =
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

  /**
   * Alias string literals with global variables, to avoid creating lots of
   * transient objects.
   */
  private final PassFactory aliasStrings = new PassFactory("aliasStrings", true) {
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
  private final PassFactory objectPropertyStringPostprocess =
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
  private final PassFactory ambiguateProperties =
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
  private final PassFactory markUnnormalized =
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
  private final PassFactory denormalize = new PassFactory("denormalize", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new Denormalize(compiler);
    }
  };

  /** Inverting name normalization. */
  private final PassFactory invertContextualRenaming =
      new PassFactory("invertContextualRenaming", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return MakeDeclaredNamesUnique.getContextualRenameInverter(compiler);
    }
  };

  /**
   * Renames properties.
   */
  private final PassFactory renameProperties =
      new PassFactory("renameProperties", true) {
        @Override
        protected CompilerPass create(final AbstractCompiler compiler) {
          Preconditions.checkState(options.propertyRenaming == PropertyRenamingPolicy.ALL_UNQUOTED);
          final VariableMap prevPropertyMap = options.inputPropertyMap;
          return new CompilerPass() {
            @Override
            public void process(Node externs, Node root) {
              char[] reservedChars = options.anonymousFunctionNaming.getReservedCharacters();
              RenameProperties rprop =
                  new RenameProperties(
                      compiler,
                      options.generatePseudoNames,
                      prevPropertyMap,
                      reservedChars,
                      options.nameGenerator);
              rprop.process(externs, root);
              propertyMap = rprop.getPropertyMap();
            }
          };
        }
      };

  /** Renames variables. */
  private final PassFactory renameVars = new PassFactory("renameVars", true) {
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
    Set<String> reservedNames = new HashSet<>();
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
        reservedNames,
        options.nameGenerator);
    rn.process(externs, root);
    return rn.getVariableMap();
  }

  /** Renames labels */
  private final PassFactory renameLabels = new PassFactory("renameLabels", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new RenameLabels(compiler);
    }
  };

  /** Convert bracket access to dot access */
  private final PassFactory convertToDottedProperties =
      new PassFactory("convertToDottedProperties", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new ConvertToDottedProperties(compiler);
    }
  };

  /** Checks that all variables are defined. */
  private final PassFactory sanityCheckAst = new PassFactory("sanityCheckAst", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new AstValidator(compiler);
    }
  };

  /** Checks that all variables are defined. */
  private final PassFactory sanityCheckVars = new PassFactory("sanityCheckVars", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new VarCheck(compiler, true);
    }
  };

  /** Adds instrumentations according to an instrumentation template. */
  private final PassFactory instrumentFunctions =
      new PassFactory("instrumentFunctions", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
      return new InstrumentFunctions(
          compiler, functionNames, options.instrumentationTemplate, options.appNameStr);
    }
  };

  private final PassFactory instrumentForCodeCoverage =
      new PassFactory("instrumentForCodeCoverage", true) {
        @Override
        protected CompilerPass create(final AbstractCompiler compiler) {
          // TODO(johnlenz): make global instrumentation an option
          if (options.instrumentBranchCoverage) {
            return new CoverageInstrumentationPass(
                compiler, CoverageReach.CONDITIONAL, InstrumentOption.BRANCH_ONLY);
          } else {
            return new CoverageInstrumentationPass(compiler, CoverageReach.CONDITIONAL);
          }
        }
      };

  /** Extern property names gathering pass. */
  private final PassFactory gatherExternProperties =
      new PassFactory("gatherExternProperties", true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new GatherExternProperties(compiler);
        }
      };

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
    Map<String, Node> additionalReplacements = new HashMap<>();

    if (options.markAsCompiled || options.closurePass) {
      additionalReplacements.put(COMPILED_CONSTANT_NAME, IR.trueNode());
    }

    if (options.closurePass && options.locale != null) {
      additionalReplacements.put(CLOSURE_LOCALE_CONSTANT_NAME,
          IR.string(options.locale));
    }

    return additionalReplacements;
  }

  /** Rewrites Polymer({}) */
  private final HotSwapPassFactory polymerPass =
      new HotSwapPassFactory("polymerPass", true) {
    @Override
    protected HotSwapCompilerPass create(AbstractCompiler compiler) {
      return new PolymerPass(compiler);
    }
  };

  private final PassFactory chromePass = new PassFactory("chromePass", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new ChromePass(compiler);
    }
  };

  /** Rewrites the super accessors calls to support Dart Dev Compiler output. */
  private final HotSwapPassFactory dartSuperAccessorsPass =
      new HotSwapPassFactory("dartSuperAccessorsPass", true) {
    @Override
    protected HotSwapCompilerPass create(AbstractCompiler compiler) {
      return new DartSuperAccessorsPass(compiler);
    }
  };

  /** Rewrites J2CL constructs to be more optimizable. */
  private final PassFactory j2clOptBundlePass =
      new PassFactory("j2clOptBundlePass", false) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          final J2clClinitPrunerPass j2clClinitPrunerPass = new J2clClinitPrunerPass(compiler);
          final J2clConstantHoisterPass j2clConstantHoisterPass =
              (new J2clConstantHoisterPass(compiler));
          final J2clEqualitySameRewriterPass j2clEqualitySameRewriterPass =
              (new J2clEqualitySameRewriterPass(compiler));
          return new CompilerPass() {

            @Override
            public void process(Node externs, Node root) {
              j2clClinitPrunerPass.process(externs, root);
              j2clConstantHoisterPass.process(externs, root);
              j2clEqualitySameRewriterPass.process(externs, root);
            }
          };
        }
      };

  /** Rewrites J2CL constructs to be more optimizable. */
  private final PassFactory j2clPropertyInlinerPass =
      new PassFactory("j2clES6Pass", true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new J2clPropertyInlinerPass(compiler);
        }
      };

  /** Rewrites J2CL constructs to be more optimizable. */
  private final PassFactory j2clPass =
      new PassFactory("j2clPass", true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new J2clPass(compiler);
        }
      };

  private final PassFactory j2clSourceFileChecker =
      new PassFactory("j2clSourceFileChecker", true) {
        @Override
        protected CompilerPass create(final AbstractCompiler compiler) {
          return new J2clSourceFileChecker(compiler);
        }
      };

  private final PassFactory j2clChecksPass =
      new PassFactory("j2clChecksPass", true) {
        @Override
        protected CompilerPass create(final AbstractCompiler compiler) {
          return new J2clChecksPass(compiler);
        }
      };

  private final PassFactory checkConformance =
      new PassFactory("checkConformance", true) {
        @Override
        protected CompilerPass create(final AbstractCompiler compiler) {
          return new CheckConformance(
              compiler, ImmutableList.copyOf(options.getConformanceConfigs()));
        }
      };

  /** Optimizations that output ES6 features. */
  private final PassFactory optimizeToEs6 = new PassFactory("optimizeToEs6", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new SubstituteEs6Syntax(compiler);
    }
  };

  private final PassFactory rewriteBindThis =
      new PassFactory("rewriteBindThis", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new RewriteBindThis(compiler);
    }
  };

  /** Rewrites goog.module in whitespace only mode */
  private final HotSwapPassFactory whitespaceWrapGoogModules =
      new HotSwapPassFactory("whitespaceWrapGoogModules", true) {
        @Override
        protected HotSwapCompilerPass create(AbstractCompiler compiler) {
          return new WhitespaceWrapGoogModules(compiler);
        }
      };

  private final PassFactory removeSuperMethodsPass =
      new PassFactory("removeSuperMethods", true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new RemoveSuperMethodsPass(compiler);
        }
      };
}
