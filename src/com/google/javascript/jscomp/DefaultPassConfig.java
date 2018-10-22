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
import static com.google.javascript.jscomp.PassFactory.createEmptyPass;
import static com.google.javascript.jscomp.parsing.parser.FeatureSet.ES2018;
import static com.google.javascript.jscomp.parsing.parser.FeatureSet.ES5;
import static com.google.javascript.jscomp.parsing.parser.FeatureSet.ES6;
import static com.google.javascript.jscomp.parsing.parser.FeatureSet.ES8;
import static com.google.javascript.jscomp.parsing.parser.FeatureSet.ES8_MODULES;
import static com.google.javascript.jscomp.parsing.parser.FeatureSet.ES_NEXT;
import static com.google.javascript.jscomp.parsing.parser.FeatureSet.TYPESCRIPT;
import static com.google.javascript.jscomp.parsing.parser.FeatureSet.TYPE_CHECK_SUPPORTED;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.jscomp.CompilerOptions.ExtractPrototypeMemberDeclarationsMode;
import com.google.javascript.jscomp.CompilerOptions.Reach;
import com.google.javascript.jscomp.CoverageInstrumentationPass.CoverageReach;
import com.google.javascript.jscomp.CoverageInstrumentationPass.InstrumentOption;
import com.google.javascript.jscomp.ExtractPrototypeMemberDeclarations.Pattern;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.jscomp.PassFactory.HotSwapPassFactory;
import com.google.javascript.jscomp.ijs.ConvertToTypedInterface;
import com.google.javascript.jscomp.lint.CheckArrayWithGoogObject;
import com.google.javascript.jscomp.lint.CheckDuplicateCase;
import com.google.javascript.jscomp.lint.CheckEmptyStatements;
import com.google.javascript.jscomp.lint.CheckEnums;
import com.google.javascript.jscomp.lint.CheckEs6ModuleFileStructure;
import com.google.javascript.jscomp.lint.CheckEs6Modules;
import com.google.javascript.jscomp.lint.CheckInterfaces;
import com.google.javascript.jscomp.lint.CheckJSDocStyle;
import com.google.javascript.jscomp.lint.CheckMissingSemicolon;
import com.google.javascript.jscomp.lint.CheckNoMutatedEs6Exports;
import com.google.javascript.jscomp.lint.CheckNullabilityModifiers;
import com.google.javascript.jscomp.lint.CheckNullableReturn;
import com.google.javascript.jscomp.lint.CheckPrimitiveAsObject;
import com.google.javascript.jscomp.lint.CheckPrototypeProperties;
import com.google.javascript.jscomp.lint.CheckRequiresAndProvidesSorted;
import com.google.javascript.jscomp.lint.CheckUnusedLabels;
import com.google.javascript.jscomp.lint.CheckUselessBlocks;
import com.google.javascript.jscomp.parsing.ParserRunner;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

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
  private transient GlobalNamespace namespaceForChecks = null;

  /** A symbol table for registering references that get removed during preprocessing. */
  private final transient PreprocessorSymbolTable.CachedInstanceFactory
      preprocessorSymbolTableFactory;

  /**
   * Global state necessary for doing hotswap recompilation of files with references to
   * processed goog.modules.
   */
  private transient ClosureRewriteModule.GlobalRewriteState moduleRewriteState = null;

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
    preprocessorSymbolTableFactory = new PreprocessorSymbolTable.CachedInstanceFactory();
  }

  GlobalNamespace getGlobalNamespace() {
    return namespaceForChecks;
  }

  @Nullable
  PreprocessorSymbolTable getPreprocessorSymbolTable() {
    return preprocessorSymbolTableFactory.getInstanceOrNull();
  }

  void maybeInitializeModuleRewriteState() {
    if (options.allowsHotswapReplaceScript() && this.moduleRewriteState == null) {
      this.moduleRewriteState = new ClosureRewriteModule.GlobalRewriteState();
    }
  }

  @Override
  protected List<PassFactory> getTranspileOnlyPasses() {
    List<PassFactory> passes = new ArrayList<>();

    if (options.needsTranspilationFrom(TYPESCRIPT)) {
      passes.add(convertEs6TypedToEs6);
    }

    passes.add(checkVariableReferencesForTranspileOnly);
    passes.add(gatherModuleMetadataPass);

    if (options.getLanguageIn().toFeatureSet().has(FeatureSet.Feature.MODULES)) {
      passes.add(rewriteGoogJsImports);
      switch (options.getEs6ModuleTranspilation()) {
        case COMPILE:
          TranspilationPasses.addEs6ModulePass(passes, preprocessorSymbolTableFactory);
          break;
        case TO_COMMON_JS_LIKE_MODULES:
          TranspilationPasses.addEs6ModuleToCjsPass(passes);
          break;
        case RELATIVIZE_IMPORT_PATHS:
          TranspilationPasses.addEs6RewriteImportPathPass(passes);
          break;
        case NONE:
          // nothing
          break;
      }
    }

    passes.add(checkSuper);

    // It's important that the Dart super accessors pass run *before* es6ConvertSuper,
    // which is a "late" ES6 pass. This is enforced in the assertValidOrder method.
    if (options.dartPass && options.needsTranspilationFrom(ES6)) {
      passes.add(dartSuperAccessorsPass);
    }

    TranspilationPasses.addPreTypecheckTranspilationPasses(passes, options);

    TranspilationPasses.addPostCheckTranspilationPasses(passes, options);

    if (options.needsTranspilationFrom(ES6)) {
      if (options.rewritePolyfills) {
        TranspilationPasses.addRewritePolyfillPass(passes);
      }
    }

    passes.add(injectRuntimeLibraries);

    assertAllOneTimePasses(passes);
    assertValidOrderForChecks(passes);
    return passes;
  }

  @Override
  protected List<PassFactory> getWhitespaceOnlyPasses() {
    List<PassFactory> passes = new ArrayList<>();

    if (options.processCommonJSModules) {
      passes.add(rewriteCommonJsModules);
    } else if (options.getLanguageIn().toFeatureSet().has(FeatureSet.Feature.MODULES)) {
      passes.add(rewriteScriptsToEs6Modules);
    }

    if (options.wrapGoogModulesForWhitespaceOnly) {
      passes.add(whitespaceWrapGoogModules);
    }
    return passes;
  }

  private void addTypeCheckerPasses(List<PassFactory> checks, CompilerOptions options) {
    if (!options.allowsHotswapReplaceScript()) {
      checks.add(inlineTypeAliases);
    }
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

    if (options.shouldGenerateTypedExterns()) {
      checks.add(closureGoogScopeAliases);
      checks.add(closureRewriteClass);
      checks.add(generateIjs);
      checks.add(whitespaceWrapGoogModules);
      return checks;
    }

    checks.add(createEmptyPass("beforeStandardChecks"));

    if (options.processCommonJSModules) {
      checks.add(rewriteCommonJsModules);
    } else if (options.getLanguageIn().toFeatureSet().has(FeatureSet.Feature.MODULES)) {
      checks.add(rewriteScriptsToEs6Modules);
    }

    // Note: ChromePass can rewrite invalid @type annotations into valid ones, so should run before
    // JsDoc checks.
    if (options.isChromePassEnabled()) {
      checks.add(chromePass);
    }

    // Verify JsDoc annotations and check ES6 modules
    checks.add(checkJsDocAndEs6Modules);

    if (options.needsTranspilationFrom(TYPESCRIPT)) {
      checks.add(convertEs6TypedToEs6);
    }

    checks.add(gatherModuleMetadataPass);

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

    checks.add(checkVariableReferences);

    if (options.getLanguageIn().toFeatureSet().has(FeatureSet.Feature.MODULES)) {
      checks.add(rewriteGoogJsImports);
      TranspilationPasses.addEs6ModulePass(checks, preprocessorSymbolTableFactory);
    }

    checks.add(checkStrictMode);

    if (options.closurePass) {
      checks.add(closureCheckModule);
      checks.add(closureRewriteModule);
    }

    if (options.declaredGlobalExternsOnWindow) {
      checks.add(declaredGlobalExternsOnWindow);
    }

    checks.add(checkSuper);

    if (options.closurePass) {
      checks.add(closureGoogScopeAliases);
      checks.add(closureRewriteClass);
    }

    checks.add(checkSideEffects);

    if (options.enables(DiagnosticGroups.MISSING_PROVIDE)) {
      checks.add(checkProvides);
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

    // It's important that the PolymerPass run *after* the ClosurePrimitives and ChromePass rewrites
    // and *before* the suspicious code checks. This is enforced in the assertValidOrder method.
    if (options.polymerVersion != null) {
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
    if (options.dartPass && !options.getOutputFeatureSet().contains(ES6)) {
      checks.add(dartSuperAccessorsPass);
    }

    // Passes running before this point should expect to see language features up to ES_2017.
    checks.add(createEmptyPass(PassNames.BEFORE_ES_2017_TRANSPILATION));

    TranspilationPasses.addPreTypecheckTranspilationPasses(checks, options);

    if (options.rewritePolyfills && !options.checksOnly) {
      TranspilationPasses.addRewritePolyfillPass(checks);
    }

    checks.add(injectRuntimeLibraries);
    checks.add(createEmptyPass(PassNames.BEFORE_TYPE_CHECKING));

    addTypeCheckerPasses(checks, options);

    if (options.j2clPassMode.shouldAddJ2clPasses()) {
      checks.add(j2clSourceFileChecker);
    }

    if (!options.disables(DiagnosticGroups.CHECK_USELESS_CODE)
        || !options.disables(DiagnosticGroups.MISSING_RETURN)) {
      checks.add(checkControlFlow);
    }

    // CheckAccessControls only works if check types is on.
    if (options.isTypecheckingEnabled()
        && (!options.disables(DiagnosticGroups.ACCESS_CONTROLS)
            || options.enables(DiagnosticGroups.CONSTANT_PROPERTY))) {
      checks.add(checkAccessControls);
    }

    checks.add(checkConsts);

    // Analyzer checks must be run after typechecking.
    if (options.enables(DiagnosticGroups.ANALYZER_CHECKS) && options.isTypecheckingEnabled()) {
      checks.add(analyzerChecks);
    }

    if (options.checkGlobalNamesLevel.isOn()) {
      checks.add(checkGlobalNames);
    }

    if (!options.getConformanceConfigs().isEmpty()) {
      checks.add(checkConformance);
    }

    // Replace 'goog.getCssName' before processing defines but after the
    // other checks have been done.
    if (options.closurePass && !options.shouldPreserveGoogLibraryPrimitives()) {
      checks.add(closureReplaceGetCssName);
    }

    if (options.getTweakProcessing().isOn()) {
      checks.add(processTweaks);
    }

    if (options.instrumentationTemplate != null || options.recordFunctionInformation) {
      checks.add(computeFunctionNames);
    }

    if (options.checksOnly) {
      // Run process defines here so that warnings/errors from that pass are emitted as part of
      // checks.
      // TODO(rluble): Split process defines into two stages, one that performs only checks to be
      // run here, and the one that actually changes the AST that would run in the optimization
      // phase.
      checks.add(processDefines);
    }

    if (options.j2clPassMode.shouldAddJ2clPasses()) {
      checks.add(j2clChecksPass);
    }

    if (options.shouldRunTypeSummaryChecksLate()) {
      checks.add(generateIjs);
    }

    // When options.generateExportsAfterTypeChecking is true, run GenerateExports after
    // both type checkers, not just after NTI.
    if (options.generateExportsAfterTypeChecking && options.generateExports) {
      checks.add(generateExports);
    }

    checks.add(createEmptyPass(PassNames.AFTER_STANDARD_CHECKS));

    if (!options.checksOnly) {
      // At this point all checks have been done.
      // There's no need to complete transpilation if we're only running checks.
      TranspilationPasses.addPostCheckTranspilationPasses(checks, options);

      if (options.needsTranspilationFrom(ES6)) {
        // This pass used to be necessary to make the typechecker happy with class-side inheritance.
        // It's not necessary for typechecking now that class transpilation is post-typechecking,
        // but it turned out to be necessary to avoid CollapseProperties breaking static inheritance
        // TODO(b/116054203): try to only run this pass when property collapsing is enabled during
        // the optimizations. Even better, find a way to get rid of this pass completely.
        checks.add(convertStaticInheritance);
      }
    }

    assertAllOneTimePasses(checks);
    assertValidOrderForChecks(checks);

    return checks;
  }

  @Override
  protected List<PassFactory> getOptimizations() {
    List<PassFactory> passes = new ArrayList<>();

    if (options.skipNonTranspilationPasses) {
      return passes;
    }

    passes.add(removeWeakSources);

    passes.add(garbageCollectChecks);

    // i18n
    // If you want to customize the compiler to use a different i18n pass,
    // you can create a PassConfig that calls replacePassFactory
    // to replace this.
    if (options.replaceMessagesWithChromeI18n) {
      passes.add(replaceMessagesForChrome);
    } else if (options.messageBundle != null) {
      passes.add(replaceMessages);
    }

    // Defines in code always need to be processed.
    passes.add(processDefines);

    if (options.getTweakProcessing().shouldStrip()
        || !options.stripTypes.isEmpty()
        || !options.stripNameSuffixes.isEmpty()
        || !options.stripTypePrefixes.isEmpty()
        || !options.stripNamePrefixes.isEmpty()) {
      passes.add(stripCode);
    }

    passes.add(normalize);

    // Create extern exports after the normalize because externExports depends on unique names.
    if (options.isExternExportsEnabled() || options.externExportsPath != null) {
      passes.add(externExports);
    }

    // Gather property names in externs so they can be queried by the
    // optimizing passes.
    passes.add(gatherExternProperties);

    // Should be run before runtimeTypeCheck and instrumentForCoverage as they rewrite code that
    // this pass expects to see.
    if (options.j2clPassMode.shouldAddJ2clPasses()) {
      passes.add(j2clPass);
      passes.add(j2clUtilGetDefineRewriterPass);
    }

    if (options.instrumentForCoverage) {
      passes.add(instrumentForCodeCoverage);
    }

    if (options.runtimeTypeCheck) {
      passes.add(runtimeTypeCheck);
    }

    passes.add(createEmptyPass(PassNames.BEFORE_STANDARD_OPTIMIZATIONS));

    if (options.replaceIdGenerators) {
      passes.add(replaceIdGenerators);
    }

    // Optimizes references to the arguments variable.
    if (options.optimizeArgumentsArray) {
      passes.add(optimizeArgumentsArray);
    }

    // Abstract method removal works best on minimally modified code, and also
    // only needs to run once.
    if (options.closurePass && (options.removeAbstractMethods || options.removeClosureAsserts)) {
      passes.add(closureCodeRemoval);
    }

    if (options.removeJ2clAsserts) {
      passes.add(j2clAssertRemovalPass);
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
    if (options.shouldCollapseProperties()) {
      passes.add(aggressiveInlineAliases);
    }

    // Inline getters/setters in J2CL classes so that Object.defineProperties() calls (resulting
    // from desugaring) don't block class stripping.
    if (options.j2clPassMode.shouldAddJ2clPasses() && options.shouldCollapseProperties()) {
      // Relies on collapseProperties-triggered aggressive alias inlining.
      passes.add(j2clPropertyInlinerPass);
    }

    // Collapsing properties can undo constant inlining, so we do this before
    // the main optimization loop.
    if (options.shouldCollapseProperties()) {
      passes.add(collapseProperties);
    }

    if (options.inferConsts) {
      passes.add(inferConsts);
    }

    // TODO(mlourenco): Ideally this would be in getChecks() instead of getOptimizations(). But
    // for that it needs to understand constant properties as well. See b/31301233#10.
    // Needs to happen after inferConsts and collapseProperties. Detects whether invocations of
    // the method goog.string.Const.from are done with an argument which is a string literal.
    passes.add(checkConstParams);

    // Running RemoveUnusedCode before disambiguate properties allows disambiguate properties to be
    // more effective if code that would prevent disambiguation can be removed.
    // TODO(b/66971163): Rename options since we're not actually using smartNameRemoval here now.
    if (options.extraSmartNameRemoval && options.smartNameRemoval) {

      // These passes remove code that is dead because of define flags.
      // If the dead code is weakly typed, running these passes before property
      // disambiguation results in more code removal.
      // The passes are one-time on purpose. (The later runs are loopable.)
      if (options.foldConstants && (options.inlineVariables || options.inlineLocalVariables)) {
        passes.add(earlyInlineVariables);
        passes.add(earlyPeepholeOptimizations);
      }

      passes.add(removeUnusedCodeOnce);
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
    if (options.shouldDisambiguateProperties() && options.isTypecheckingEnabled()) {
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

    if (options.smartNameRemoval) {
      passes.addAll(getCodeRemovingPasses());
      // TODO(b/66971163): Remove this early loop or rename the option that enables it
      // to something more appropriate.
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

    // TODO(user): This forces a first crack at crossChunkCodeMotion
    // before devirtualization. Once certain functions are devirtualized,
    // it confuses crossChunkCodeMotion ability to recognized that
    // it is recursive.

    // TODO(user): This is meant for a temporary quick win.
    // In the future, we might want to improve our analysis in
    // CrossChunkCodeMotion so we don't need to do this.
    if (options.shouldRunCrossChunkCodeMotion()) {
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

    passes.add(createEmptyPass(PassNames.BEFORE_MAIN_OPTIMIZATIONS));

    // Because FlowSensitiveInlineVariables does not operate on the global scope due to compilation
    // time, we need to run it once before InlineFunctions so that we don't miss inlining
    // opportunities when a function will be inlined into the global scope.
    if (options.inlineVariables || options.inlineLocalVariables) {
      passes.add(flowSensitiveInlineVariables);
    }

    passes.addAll(getMainOptimizationLoop());
    passes.add(createEmptyPass(PassNames.AFTER_MAIN_OPTIMIZATIONS));

    passes.add(createEmptyPass("beforeModuleMotion"));

    if (options.shouldRunCrossChunkCodeMotion()) {
      passes.add(crossModuleCodeMotion);
    }

    if (options.shouldRunCrossChunkMethodMotion()) {
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
      if (shouldRunRemoveUnusedCode()) {
        passes.add(removeUnusedCodeOnce);
      }
    }

    if (options.collapseAnonymousFunctions) {
      passes.add(collapseAnonymousFunctions);
    }

    // Move functions before extracting prototype member declarations.
    if (options.moveFunctionDeclarations
        // renamePrefixNamescape relies on moveFunctionDeclarations
        // to preserve semantics.
        || options.renamePrefixNamespace != null) {
      passes.add(moveFunctionDeclarations);
    }

    if (options.anonymousFunctionNaming == AnonymousFunctionNamingPolicy.MAPPED) {
      passes.add(nameMappedAnonymousFunctions);
    }

    // The mapped name anonymous function pass makes use of information that
    // the extract prototype member declarations pass removes so the former
    // happens before the latter.
    if (options.extractPrototypeMemberDeclarations != ExtractPrototypeMemberDeclarationsMode.OFF) {
      passes.add(extractPrototypeMemberDeclarations);
    }

    if (options.shouldAmbiguateProperties()
        && options.propertyRenaming == PropertyRenamingPolicy.ALL_UNQUOTED
        && options.isTypecheckingEnabled()) {
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

    if (options.coalesceVariableNames) {
      // Passes after this point can no longer depend on normalized AST
      // assumptions because the code is marked as un-normalized
      passes.add(coalesceVariableNames);

      // coalesceVariables creates identity assignments and more redundant code
      // that can be removed, rerun the peephole optimizations to clean them
      // up.
      if (options.foldConstants) {
        passes.add(peepholeOptimizationsOnce);
      }
    }

    // Passes after this point can no longer depend on normalized AST assumptions.
    passes.add(markUnnormalized);

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
      // local variables ("x" -> "x$jscomp$1").
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

    if (options.anonymousFunctionNaming == AnonymousFunctionNamingPolicy.UNMAPPED) {
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
    passes.add(checkAstValidity);
    passes.add(varCheckValidity);

    // Raise to ES6, if allowed
    if (options.getOutputFeatureSet().contains(ES6)) {
      passes.add(optimizeToEs6);
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

    if (options.getInlineFunctionsLevel() != Reach.NONE) {
      passes.add(inlineFunctions);
    }

    if (options.shouldInlineProperties() && options.isTypecheckingEnabled()) {
      passes.add(inlineProperties);
    }

    if (options.removeUnusedVars || options.removeUnusedLocalVars) {
      if (options.deadAssignmentElimination) {
        passes.add(deadAssignmentsElimination);

        // The Polymer source is usually not included in the compilation, but it creates
        // getters/setters for many properties in compiled code. Dead property assignment
        // elimination is only safe when it knows about getters/setters. Therefore, we skip
        // it if the polymer pass is enabled.
        if (options.polymerVersion == null) {
          passes.add(deadPropertyAssignmentElimination);
        }
      }
    }

    if (options.optimizeCalls) {
      passes.add(optimizeCalls);
    }

    if (options.j2clPassMode.shouldAddJ2clPasses()) {
      passes.add(j2clConstantHoisterPass);
      passes.add(j2clClinitPass);
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

    if (shouldRunRemoveUnusedCode()) {
      passes.add(removeUnusedCode);
    }

    assertAllLoopablePasses(passes);
    return passes;
  }

  private boolean shouldRunRemoveUnusedCode() {
    return options.removeUnusedVars
        || options.removeUnusedLocalVars
        || options.removeUnusedPrototypeProperties
        || options.isRemoveUnusedClassProperties()
        || options.isRemoveUnusedConstructorProperties();
  }

  private final HotSwapPassFactory checkSideEffects =
      new HotSwapPassFactory("checkSideEffects") {
        @Override
        protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
          return new CheckSideEffects(
              compiler, options.checkSuspiciousCode, protectHiddenSideEffects);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES_NEXT;
        }
      };

  /** Removes the "protector" functions that were added by CheckSideEffects. */
  private final PassFactory stripSideEffectProtection =
      new PassFactory(PassNames.STRIP_SIDE_EFFECT_PROTECTION, true) {
        @Override
        protected CompilerPass create(final AbstractCompiler compiler) {
          return new CheckSideEffects.StripProtection(compiler);
        }

        @Override
        public FeatureSet featureSet() {
          return FeatureSet.latest();
        }
      };

  /** Checks for code that is probably wrong (such as stray expressions). */
  private final HotSwapPassFactory suspiciousCode =
      new HotSwapPassFactory("suspiciousCode") {
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

        @Override
        protected FeatureSet featureSet() {
          return ES_NEXT;
        }
      };

  /** Verify that all the passes are one-time passes. */
  private static void assertAllOneTimePasses(List<PassFactory> passes) {
    for (PassFactory pass : passes) {
      checkState(pass.isOneTimePass());
    }
  }

  /** Verify that all the passes are multi-run passes. */
  private static void assertAllLoopablePasses(List<PassFactory> passes) {
    for (PassFactory pass : passes) {
      checkState(!pass.isOneTimePass());
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
      checkState(pass1Index < pass2Index, msg);
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
        chromePass,
        checkJsDocAndEs6Modules,
        "The ChromePass must run before after JsDoc and Es6 module checking.");
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
      checkState(
          checks.contains(checkVariableReferences),
          "goog.scope processing requires variable checking");
    }
    assertPassOrder(
        checks,
        checkVariableReferences,
        closureGoogScopeAliases,
        "Variable checking must happen before goog.scope processing.");
  }

  /**
   * Certain optimizations need to run in a particular order. For example, OptimizeCalls must run
   * before RemoveSuperMethodsPass, because the former can invalidate assumptions in the latter.
   * This enforces those constraints.
   * @param optimizations The list of optimization passes
   */
  private void assertValidOrderForOptimizations(List<PassFactory> optimizations) {
    assertPassOrder(
        optimizations,
        processDefines,
        j2clUtilGetDefineRewriterPass,
        "J2CL define re-writing should be done after processDefines since it relies on "
            + "collectDefines which has side effects.");
  }

  /** Checks that all constructed classes are goog.require()d. */
  private final HotSwapPassFactory checkRequires =
      new HotSwapPassFactory("checkMissingAndExtraRequires") {
        @Override
        protected HotSwapCompilerPass create(AbstractCompiler compiler) {
          return new CheckMissingAndExtraRequires(
              compiler, CheckMissingAndExtraRequires.Mode.FULL_COMPILE);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES_NEXT;
        }
      };

  /** Makes sure @constructor is paired with goog.provides(). */
  private final HotSwapPassFactory checkProvides =
      new HotSwapPassFactory("checkProvides") {
        @Override
        protected HotSwapCompilerPass create(AbstractCompiler compiler) {
          return new CheckProvides(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES_NEXT;
        }
      };

  private static final DiagnosticType GENERATE_EXPORTS_ERROR =
      DiagnosticType.error(
          "JSC_GENERATE_EXPORTS_ERROR",
          "Exports can only be generated if export symbol/property functions are set.");

  /** Verifies JSDoc annotations are used properly and checks for ES6 modules. */
  private final HotSwapPassFactory checkJsDocAndEs6Modules =
      new HotSwapPassFactory("checkJsDocAndEs6Modules") {
        @Override
        protected HotSwapCompilerPass create(AbstractCompiler compiler) {
          ImmutableList.Builder<Callback> callbacks =
              ImmutableList.<Callback>builder()
                  .add(new CheckJSDoc(compiler))
                  .add(new Es6CheckModule(compiler));
          return combineChecks(compiler, callbacks.build());
        }

        @Override
        protected FeatureSet featureSet() {
          return FeatureSet.latest();
        }
      };

  /** Generates exports for @export annotations. */
  private final PassFactory generateExports =
      new PassFactory(PassNames.GENERATE_EXPORTS, true) {
        @Override
        protected CompilerPass create(final AbstractCompiler compiler) {
          CodingConvention convention = compiler.getCodingConvention();
          if (convention.getExportSymbolFunction() != null
              && convention.getExportPropertyFunction() != null) {
            final GenerateExports pass =
                new GenerateExports(
                    compiler,
                    options.exportLocalPropertyDefinitions,
                    convention.getExportSymbolFunction(),
                    convention.getExportPropertyFunction());
            return new CompilerPass() {
              @Override
              public void process(Node externs, Node root) {
                pass.process(externs, root);
                compiler.addExportedNames(pass.getExportedVariableNames());
              }
            };
          } else {
            return new ErrorPass(compiler, GENERATE_EXPORTS_ERROR);
          }
        }

        @Override
        protected FeatureSet featureSet() {
          return ES2018;
        }
      };

  private final PassFactory generateIjs =
      new PassFactory("generateIjs", true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new ConvertToTypedInterface(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  /** Generates exports for functions associated with JsUnit. */
  private final PassFactory exportTestFunctions =
      new PassFactory(PassNames.EXPORT_TEST_FUNCTIONS, true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          CodingConvention convention = compiler.getCodingConvention();
          if (convention.getExportSymbolFunction() != null) {
            return new ExportTestFunctions(
                compiler,
                convention.getExportSymbolFunction(),
                convention.getExportPropertyFunction());
          } else {
            return new ErrorPass(compiler, GENERATE_EXPORTS_ERROR);
          }
        }

        @Override
        protected FeatureSet featureSet() {
          return ES_NEXT;
        }
      };

  /** Raw exports processing pass. */
  private final PassFactory gatherRawExports =
      new PassFactory(PassNames.GATHER_RAW_EXPORTS, true) {
        @Override
        protected CompilerPass create(final AbstractCompiler compiler) {
          final GatherRawExports pass = new GatherRawExports(compiler);

          return new CompilerPass() {
            @Override
            public void process(Node externs, Node root) {
              pass.process(externs, root);
              compiler.addExportedNames(pass.getExportedVariableNames());
            }
          };
        }

        @Override
        public FeatureSet featureSet() {
          // Should be FeatureSet.latest() since it's a trivial pass, but must match "normalize"
          // TODO(johnlenz): Update this and normalize to latest()
          return ES8_MODULES;
        }
      };

  /** Closure pre-processing pass. */
  private final HotSwapPassFactory closurePrimitives =
      new HotSwapPassFactory("closurePrimitives") {
        @Override
        protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
          preprocessorSymbolTableFactory.maybeInitialize(compiler);
          final ProcessClosurePrimitives pass =
              new ProcessClosurePrimitives(
                  compiler,
                  preprocessorSymbolTableFactory.getInstanceOrNull(),
                  options.brokenClosureRequiresLevel,
                  options.shouldPreservesGoogProvidesAndRequires());

          return new HotSwapCompilerPass() {
            @Override
            public void process(Node externs, Node root) {
              pass.process(externs, root);
              compiler.addExportedNames(pass.getExportedVariableNames());
            }

            @Override
            public void hotSwapScript(Node scriptRoot, Node originalRoot) {
              pass.hotSwapScript(scriptRoot, originalRoot);
            }
          };
        }

        @Override
        protected FeatureSet featureSet() {
          return ES_NEXT;
        }
      };

  /** Process AngularJS-specific annotations. */
  private final HotSwapPassFactory angularPass =
      new HotSwapPassFactory(PassNames.ANGULAR_PASS) {
        @Override
        protected HotSwapCompilerPass create(AbstractCompiler compiler) {
          return new AngularPass(compiler);
        }

        @Override
        public FeatureSet featureSet() {
          return ES_NEXT;
        }
      };

  /**
   * The default i18n pass. A lot of the options are not configurable, because ReplaceMessages has a
   * lot of legacy logic.
   */
  private final PassFactory replaceMessages =
      new PassFactory(PassNames.REPLACE_MESSAGES, true) {
        @Override
        protected CompilerPass create(final AbstractCompiler compiler) {
          return new ReplaceMessages(
              compiler,
              options.messageBundle,
              /* warn about message dupes */
              true,
              /* allow messages with goog.getMsg */
              JsMessage.Style.CLOSURE,
              /* if we can't find a translation, don't worry about it. */
              false);
        }

        @Override
        public FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  private final PassFactory replaceMessagesForChrome =
      new PassFactory(PassNames.REPLACE_MESSAGES, true) {
        @Override
        protected CompilerPass create(final AbstractCompiler compiler) {
          return new ReplaceMessagesForChrome(
              compiler,
              new GoogleJsMessageIdGenerator(options.tcProjectId),
              /* warn about message dupes */
              true,
              /* allow messages with goog.getMsg */
              JsMessage.Style.CLOSURE);
        }

        @Override
        public FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  /** Applies aliases and inlines goog.scope. */
  private final HotSwapPassFactory closureGoogScopeAliases =
      new HotSwapPassFactory("closureGoogScopeAliases") {
        @Override
        protected HotSwapCompilerPass create(AbstractCompiler compiler) {
          preprocessorSymbolTableFactory.maybeInitialize(compiler);
          return new ScopedAliases(
              compiler,
              preprocessorSymbolTableFactory.getInstanceOrNull(),
              options.getAliasTransformationHandler());
        }

        @Override
        protected FeatureSet featureSet() {
          return ES_NEXT;
        }
      };

  private final PassFactory injectRuntimeLibraries =
      new PassFactory("InjectRuntimeLibraries", true) {
        @Override
        protected CompilerPass create(final AbstractCompiler compiler) {
          return new InjectRuntimeLibraries(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES2018;
        }
      };

  /** Desugars ES6_TYPED features into ES6 code. */
  final HotSwapPassFactory convertEs6TypedToEs6 =
      new HotSwapPassFactory("convertEs6Typed") {
        @Override
        protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
          return new Es6TypedToEs6Converter(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return TYPESCRIPT;
        }
      };

  private final PassFactory convertStaticInheritance =
      new PassFactory("Es6StaticInheritance", true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new Es6ToEs3ClassSideInheritance(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return TYPE_CHECK_SUPPORTED;
        }
      };

  private final PassFactory inlineTypeAliases =
      new PassFactory(PassNames.INLINE_TYPE_ALIASES, true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new InlineAliases(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES2018;
        }
      };

  /** Inlines type aliases if they are explicitly or effectively const. */
  private final PassFactory aggressiveInlineAliases =
      new PassFactory("aggressiveInlineAliases", true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new AggressiveInlineAliases(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  private final PassFactory removeWeakSources =
      new PassFactory("removeWeakSources", true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new RemoveWeakSources(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return FeatureSet.latest();
        }
      };

  private final PassFactory declaredGlobalExternsOnWindow =
      new PassFactory(PassNames.DECLARED_GLOBAL_EXTERNS_ON_WINDOW, true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new DeclaredGlobalExternsOnWindow(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES_NEXT;
        }
      };

  /** Rewrites goog.defineClass */
  private final HotSwapPassFactory closureRewriteClass =
      new HotSwapPassFactory(PassNames.CLOSURE_REWRITE_CLASS) {
        @Override
        protected HotSwapCompilerPass create(AbstractCompiler compiler) {
          return new ClosureRewriteClass(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES_NEXT;
        }
      };

  /** Checks of correct usage of goog.module */
  private final HotSwapPassFactory closureCheckModule =
      new HotSwapPassFactory("closureCheckModule") {
        @Override
        protected HotSwapCompilerPass create(AbstractCompiler compiler) {
          return new ClosureCheckModule(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES_NEXT;
        }
      };

  /** Rewrites goog.module */
  private final HotSwapPassFactory closureRewriteModule =
      new HotSwapPassFactory("closureRewriteModule") {
        @Override
        protected HotSwapCompilerPass create(AbstractCompiler compiler) {
          preprocessorSymbolTableFactory.maybeInitialize(compiler);
          maybeInitializeModuleRewriteState();
          return new ClosureRewriteModule(
              compiler, preprocessorSymbolTableFactory.getInstanceOrNull(), moduleRewriteState);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES_NEXT;
        }
      };

  /** Rewrite imports for Closure Library's goog.js file to global goog references. */
  private final HotSwapPassFactory rewriteGoogJsImports =
      new HotSwapPassFactory("rewriteGoogJsImports") {
        @Override
        protected HotSwapCompilerPass create(AbstractCompiler compiler) {
          return new RewriteGoogJsImports(compiler, RewriteGoogJsImports.Mode.LINT_AND_REWRITE);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES_NEXT;
        }
      };

  /** Checks that CSS class names are wrapped in goog.getCssName */
  private final PassFactory closureCheckGetCssName =
      new PassFactory("closureCheckGetCssName", true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new CheckMissingGetCssName(
              compiler,
              options.checkMissingGetCssNameLevel,
              options.checkMissingGetCssNameBlacklist);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES_NEXT;
        }
      };

  /**
   * Processes goog.getCssName. The cssRenamingMap is used to lookup replacement values for the
   * classnames. If null, the raw class names are inlined.
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
              ReplaceCssNames pass =
                  new ReplaceCssNames(compiler, newCssNames, options.cssRenamingWhitelist);
              pass.process(externs, jsRoot);
              compiler.setCssNames(newCssNames);
            }
          };
        }

        @Override
        protected FeatureSet featureSet() {
          return ES2018;
        }
      };

  /**
   * Creates synthetic blocks to prevent FoldConstants from moving code past markers in the source.
   */
  private final PassFactory createSyntheticBlocks =
      new PassFactory("createSyntheticBlocks", true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new CreateSyntheticBlocks(
              compiler, options.syntheticBlockStartMarker, options.syntheticBlockEndMarker);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES_NEXT;
        }
      };

  private final PassFactory earlyPeepholeOptimizations =
      new PassFactory("earlyPeepholeOptimizations", true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          List<AbstractPeepholeOptimization> peepholeOptimizations = new ArrayList<>();
          peepholeOptimizations.add(new PeepholeRemoveDeadCode());
          if (compiler.getOptions().j2clPassMode.shouldAddJ2clPasses()) {
            peepholeOptimizations.add(new J2clEqualitySameRewriterPass());
          }
          return new PeepholeOptimizationsPass(compiler, getName(), peepholeOptimizations);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
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

    @Override
    protected FeatureSet featureSet() {
      return ES8_MODULES;
    }
  };

  /** Various peephole optimizations. */
  private static CompilerPass createPeepholeOptimizationsPass(
      AbstractCompiler compiler, String passName) {
    final boolean late = false;
    final boolean useTypesForOptimization = compiler.getOptions().useTypesForLocalOptimization;
    List<AbstractPeepholeOptimization> optimizations = new ArrayList<>();
    optimizations.add(new MinimizeExitPoints());
    optimizations.add(new PeepholeMinimizeConditions(late));
    optimizations.add(new PeepholeSubstituteAlternateSyntax(late));
    optimizations.add(new PeepholeReplaceKnownMethods(late, useTypesForOptimization));
    optimizations.add(new PeepholeRemoveDeadCode());
    if (compiler.getOptions().j2clPassMode.shouldAddJ2clPasses()) {
      optimizations.add(new J2clEqualitySameRewriterPass());
      optimizations.add(new J2clStringValueOfRewriterPass());
    }
    optimizations.add(new PeepholeFoldConstants(late, useTypesForOptimization));
    optimizations.add(new PeepholeCollectPropertyAssignments());
    return new PeepholeOptimizationsPass(compiler, passName, optimizations);
  }

  /** Various peephole optimizations. */
  private final PassFactory peepholeOptimizations =
      new PassFactory(PassNames.PEEPHOLE_OPTIMIZATIONS, false /* oneTimePass */) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return createPeepholeOptimizationsPass(compiler, getName());
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  /** Various peephole optimizations. */
  private final PassFactory peepholeOptimizationsOnce =
      new PassFactory(PassNames.PEEPHOLE_OPTIMIZATIONS, true /* oneTimePass */) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return createPeepholeOptimizationsPass(compiler, getName());
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  /** Same as peepholeOptimizations but aggressively merges code together */
  private final PassFactory latePeepholeOptimizations =
      new PassFactory("latePeepholeOptimizations", true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          final boolean late = true;
          final boolean useTypesForOptimization = options.useTypesForLocalOptimization;
          return new PeepholeOptimizationsPass(
              compiler,
              getName(),
              new StatementFusion(options.aggressiveFusion),
              new PeepholeRemoveDeadCode(),
              new PeepholeMinimizeConditions(late),
              new PeepholeSubstituteAlternateSyntax(late),
              new PeepholeReplaceKnownMethods(late, useTypesForOptimization),
              new PeepholeFoldConstants(late, useTypesForOptimization),
              new PeepholeReorderConstantExpression());
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  /** Checks that all variables are defined. */
  private final HotSwapPassFactory checkVars =
      new HotSwapPassFactory(PassNames.CHECK_VARS) {
        @Override
        protected HotSwapCompilerPass create(AbstractCompiler compiler) {
          return new VarCheck(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES_NEXT;
        }
      };

  /** Infers constants. */
  private final PassFactory inferConsts =
      new PassFactory(PassNames.INFER_CONSTS, true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new InferConsts(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES_NEXT;
        }
      };

  /** Checks for RegExp references. */
  private final PassFactory checkRegExp =
      new PassFactory(PassNames.CHECK_REG_EXP, true) {
        @Override
        protected CompilerPass create(final AbstractCompiler compiler) {
          final CheckRegExp pass = new CheckRegExp(compiler);

          return new CompilerPass() {
            @Override
            public void process(Node externs, Node root) {
              pass.process(externs, root);
              compiler.setHasRegExpGlobalReferences(pass.isGlobalRegExpPropertiesUsed());
            }
          };
        }

        @Override
        protected FeatureSet featureSet() {
          return ES_NEXT;
        }
      };

  /** Checks that references to variables look reasonable. */
  private final HotSwapPassFactory checkVariableReferencesForTranspileOnly =
      new HotSwapPassFactory(PassNames.CHECK_VARIABLE_REFERENCES) {
        @Override
        protected HotSwapCompilerPass create(AbstractCompiler compiler) {
          return new VariableReferenceCheck(compiler, true);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  /** Checks that references to variables look reasonable. */
  private final HotSwapPassFactory checkVariableReferences =
      new HotSwapPassFactory(PassNames.CHECK_VARIABLE_REFERENCES) {
        @Override
        protected HotSwapCompilerPass create(AbstractCompiler compiler) {
          return new VariableReferenceCheck(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES_NEXT;
        }
      };

  /** Checks that references to variables look reasonable. */
  private final HotSwapPassFactory checkSuper =
      new HotSwapPassFactory("checkSuper") {
        @Override
        protected HotSwapCompilerPass create(AbstractCompiler compiler) {
          return new CheckSuper(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES_NEXT;
        }
      };

  /** Pre-process goog.testing.ObjectPropertyString. */
  private final PassFactory objectPropertyStringPreprocess =
      new PassFactory("ObjectPropertyStringPreprocess", true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new ObjectPropertyStringPreprocess(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES_NEXT;
        }
      };

  /** Creates a typed scope and adds types to the type registry. */
  final HotSwapPassFactory resolveTypes =
      new HotSwapPassFactory(PassNames.RESOLVE_TYPES) {
        @Override
        protected HotSwapCompilerPass create(AbstractCompiler compiler) {
          return new GlobalTypeResolver(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return TYPE_CHECK_SUPPORTED;
        }
      };

  /** Clears the typed scope when we're done. */
  private final PassFactory clearTypedScopePass =
      new PassFactory("clearTypedScopePass", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new ClearTypedScope();
    }

    @Override
    protected FeatureSet featureSet() {
      return FeatureSet.latest();
    }
  };

  /** Runs type inference. */
  final HotSwapPassFactory inferTypes =
      new HotSwapPassFactory(PassNames.INFER_TYPES) {
        @Override
        protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
          return new HotSwapCompilerPass() {
            @Override
            public void process(Node externs, Node root) {
              checkNotNull(topScope);
              checkNotNull(getTypedScopeCreator());

              makeTypeInference(compiler).process(externs, root);
            }

            @Override
            public void hotSwapScript(Node scriptRoot, Node originalRoot) {
              makeTypeInference(compiler).inferAllScopes(scriptRoot);
            }
          };
        }

        @Override
        protected FeatureSet featureSet() {
          return TYPE_CHECK_SUPPORTED;
        }
      };

  private final HotSwapPassFactory inferJsDocInfo =
      new HotSwapPassFactory("inferJsDocInfo") {
        @Override
        protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
          return new HotSwapCompilerPass() {
            @Override
            public void process(Node externs, Node root) {
              checkNotNull(topScope);
              checkNotNull(getTypedScopeCreator());

              makeInferJsDocInfo(compiler).process(externs, root);
            }

            @Override
            public void hotSwapScript(Node scriptRoot, Node originalRoot) {
              makeInferJsDocInfo(compiler).hotSwapScript(scriptRoot, originalRoot);
            }
          };
        }

        @Override
        protected FeatureSet featureSet() {
          return TYPE_CHECK_SUPPORTED;
        }
      };

  /** Checks type usage */
  private final HotSwapPassFactory checkTypes =
      new HotSwapPassFactory(PassNames.CHECK_TYPES) {
        @Override
        protected HotSwapCompilerPass create(final AbstractCompiler compiler) {
          return new HotSwapCompilerPass() {
            @Override
            public void process(Node externs, Node root) {
              checkNotNull(topScope);
              checkNotNull(getTypedScopeCreator());

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

        @Override
        protected FeatureSet featureSet() {
          return TYPE_CHECK_SUPPORTED;
        }
      };

  /**
   * Checks possible execution paths of the program for problems: missing return statements and dead
   * code.
   */
  private final HotSwapPassFactory checkControlFlow =
      new HotSwapPassFactory("checkControlFlow") {
        @Override
        protected HotSwapCompilerPass create(AbstractCompiler compiler) {
          List<Callback> callbacks = new ArrayList<>();
          if (!options.disables(DiagnosticGroups.CHECK_USELESS_CODE)) {
            callbacks.add(new CheckUnreachableCode(compiler));
          }
          if (!options.disables(DiagnosticGroups.MISSING_RETURN)) {
            callbacks.add(new CheckMissingReturn(compiler));
          }
          return combineChecks(compiler, callbacks);
        }

        @Override
        public FeatureSet featureSet() {
          return ES2018;
        }
      };

  /** Checks access controls. Depends on type-inference. */
  private final HotSwapPassFactory checkAccessControls =
      new HotSwapPassFactory("checkAccessControls") {
    @Override
    protected HotSwapCompilerPass create(AbstractCompiler compiler) {
      return new CheckAccessControls(
          compiler, options.enforceAccessControlCodingConventions);
    }

    @Override
    protected FeatureSet featureSet() {
      return TYPE_CHECK_SUPPORTED;
    }
  };

  private final HotSwapPassFactory lintChecks =
      new HotSwapPassFactory(PassNames.LINT_CHECKS) {
        @Override
        protected HotSwapCompilerPass create(AbstractCompiler compiler) {
          ImmutableList.Builder<Callback> callbacks =
              ImmutableList.<Callback>builder()
                  .add(new CheckEmptyStatements(compiler))
                  .add(new CheckEnums(compiler))
                  .add(new CheckEs6ModuleFileStructure(compiler))
                  .add(new CheckEs6Modules(compiler))
                  .add(new CheckNoMutatedEs6Exports(compiler))
                  .add(new CheckInterfaces(compiler))
                  .add(new CheckJSDocStyle(compiler))
                  .add(new CheckMissingSemicolon(compiler))
                  .add(new CheckNullabilityModifiers(compiler))
                  .add(new CheckPrimitiveAsObject(compiler))
                  .add(new CheckPrototypeProperties(compiler))
                  .add(new CheckUnusedLabels(compiler))
                  .add(new CheckUselessBlocks(compiler));
          return combineChecks(compiler, callbacks.build());
        }

        @Override
        protected FeatureSet featureSet() {
          return ES_NEXT;
        }
      };

  private final HotSwapPassFactory analyzerChecks =
      new HotSwapPassFactory(PassNames.ANALYZER_CHECKS) {
        @Override
        protected HotSwapCompilerPass create(AbstractCompiler compiler) {
          ImmutableList.Builder<Callback> callbacks = ImmutableList.builder();
          if (options.enables(DiagnosticGroups.ANALYZER_CHECKS_INTERNAL)) {
            callbacks
                .add(new CheckNullableReturn(compiler))
                .add(new CheckArrayWithGoogObject(compiler))
                .add(new ImplicitNullabilityCheck(compiler));
          }
          // These are grouped together for better execution efficiency.
          if (options.enables(DiagnosticGroups.UNUSED_PRIVATE_PROPERTY)) {
            callbacks.add(new CheckUnusedPrivateProperties(compiler));
          }
          if (options.enables(DiagnosticGroups.MISSING_CONST_PROPERTY)) {
            callbacks.add(new CheckConstPrivateProperties(compiler));
          }
          return combineChecks(compiler, callbacks.build());
        }

        @Override
        protected FeatureSet featureSet() {
          return TYPE_CHECK_SUPPORTED;
        }
      };

  private final HotSwapPassFactory checkRequiresAndProvidesSorted =
      new HotSwapPassFactory("checkRequiresAndProvidesSorted") {
        @Override
        protected HotSwapCompilerPass create(AbstractCompiler compiler) {
          return new CheckRequiresAndProvidesSorted(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES_NEXT;
        }
      };

  /** Executes the given callbacks with a {@link CombinedCompilerPass}. */
  private static HotSwapCompilerPass combineChecks(AbstractCompiler compiler,
      List<Callback> callbacks) {
    checkArgument(!callbacks.isEmpty());
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
      this.compiler.setTypeCheckingHasRun(true);
      if (topScope == null) {
        regenerateGlobalTypedScope(compiler, root.getParent());
      } else {
        compiler.getTypeRegistry().resolveTypes();
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

    @Override
    protected FeatureSet featureSet() {
      return TYPE_CHECK_SUPPORTED;
    }
  };

  /** Checks that the code is ES5 strict compliant. */
  private final PassFactory checkStrictMode =
      new PassFactory("checkStrictMode", true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new StrictModeCheck(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES_NEXT;
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

    @Override
    protected FeatureSet featureSet() {
      return TYPE_CHECK_SUPPORTED;
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
          new ProcessDefines(compiler, ImmutableMap.copyOf(replacements), !options.checksOnly)
              .injectNamespace(namespaceForChecks).process(externs, jsRoot);
        }
      };
    }

    @Override
    public FeatureSet featureSet() {
      return ES8_MODULES;
    }
  };

  /**
   * Strips code for smaller compiled code. This is useful for removing debug
   * statements to prevent leaking them publicly.
   */
  private final PassFactory stripCode = new PassFactory("stripCode", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
      return new CompilerPass() {
        @Override
        public void process(Node externs, Node jsRoot) {
          CompilerOptions options = compiler.getOptions();
          StripCode pass = new StripCode(compiler, options.stripTypes, options.stripNameSuffixes,
              options.stripTypePrefixes, options.stripNamePrefixes);
          if (options.getTweakProcessing().shouldStrip()) {
            pass.enableTweakStripping();
          }
          pass.process(externs, jsRoot);
        }
      };
    }

    @Override
    protected FeatureSet featureSet() {
      return ES8_MODULES;
    }
  };

  /** Release references to data that is only needed during checks. */
  final PassFactory garbageCollectChecks =
      new HotSwapPassFactory("garbageCollectChecks") {
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

    @Override
    public FeatureSet featureSet() {
      return FeatureSet.latest();
    }
  };

  /** Checks that all constants are not modified */
  private final PassFactory checkConsts =
      new PassFactory("checkConsts", true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new ConstCheck(compiler);
        }

        @Override
        public FeatureSet featureSet() {
          return ES2018;
        }
      };

  /** Checks that the arguments are constants */
  private final PassFactory checkConstParams =
      new PassFactory(PassNames.CHECK_CONST_PARAMS, true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new ConstParamCheck(compiler);
        }

        @Override
        public FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  /** Computes the names of functions for later analysis. */
  private final PassFactory computeFunctionNames =
      new PassFactory("computeFunctionNames", true) {
        @Override
        protected CompilerPass create(final AbstractCompiler compiler) {
          return new CompilerPass() {
            @Override
            public void process(Node externs, Node root) {
              CollectFunctionNames pass = new CollectFunctionNames(compiler);
              pass.process(externs, root);
              compiler.setFunctionNames(pass.getFunctionNames());
            }
          };
        }

        @Override
        public FeatureSet featureSet() {
          return ES2018;
        }
      };

  /** Inserts run-time type assertions for debugging. */
  private final PassFactory runtimeTypeCheck =
      new PassFactory(PassNames.RUNTIME_TYPE_CHECK, true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new RuntimeTypeCheck(compiler, options.runtimeTypeCheckLogFunction);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES5;
        }
      };

  /** Generates unique ids. */
  private final PassFactory replaceIdGenerators =
      new PassFactory(PassNames.REPLACE_ID_GENERATORS, true) {
        @Override
        protected CompilerPass create(final AbstractCompiler compiler) {
          return new CompilerPass() {
            @Override
            public void process(Node externs, Node root) {
              ReplaceIdGenerators pass =
                  new ReplaceIdGenerators(
                      compiler,
                      options.idGenerators,
                      options.generatePseudoNames,
                      options.idGeneratorsMapSerialized,
                      options.xidHashFunction);
              pass.process(externs, root);
              compiler.setIdGeneratorMap(pass.getSerializedIdMappings());
            }
          };
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
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
          compiler.setStringMap(pass.getStringMap());
        }
      };
    }

    @Override
    protected FeatureSet featureSet() {
      return ES5;
    }
  };

  /** Optimizes the "arguments" array. */
  private final PassFactory optimizeArgumentsArray =
      new PassFactory(PassNames.OPTIMIZE_ARGUMENTS_ARRAY, true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new OptimizeArgumentsArray(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
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

    @Override
    protected FeatureSet featureSet() {
      return ES8_MODULES;
    }
  };

  /** Special case optimizations for closure functions. */
  private final PassFactory closureOptimizePrimitives =
      new PassFactory("closureOptimizePrimitives", true) {
        @Override
        protected CompilerPass create(final AbstractCompiler compiler) {
          return new ClosureOptimizePrimitives(
              compiler,
              compiler.getOptions().propertyRenaming == PropertyRenamingPolicy.ALL_UNQUOTED,
              compiler.getOptions().getOutputFeatureSet().contains(ES6));
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
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
          options.renamePrefixNamespaceAssumeCrossChunkNames);
    }

    @Override
    protected FeatureSet featureSet() {
      return ES8_MODULES;
    }
  };

  /** Collapses names in the global scope. */
  private final PassFactory collapseProperties =
      new PassFactory(PassNames.COLLAPSE_PROPERTIES, true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new CollapseProperties(compiler, options.getPropertyCollapseLevel());
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  /** Rewrite properties as variables. */
  private final PassFactory collapseObjectLiterals =
      new PassFactory(PassNames.COLLAPSE_OBJECT_LITERALS, false) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new InlineObjectLiterals(compiler, compiler.getUniqueNameIdSupplier());
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  /** Disambiguate property names based on the coding convention. */
  private final PassFactory disambiguatePrivateProperties =
      new PassFactory(PassNames.DISAMBIGUATE_PRIVATE_PROPERTIES, true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new DisambiguatePrivateProperties(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  /** Disambiguate property names based on type information. */
  private final PassFactory disambiguateProperties =
      new PassFactory(PassNames.DISAMBIGUATE_PROPERTIES, true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new DisambiguateProperties(compiler, options.propertyInvalidationErrors);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES5;
        }
      };

  /** Rewrite instance methods as static methods, to make them easier to inline. */
  private final PassFactory devirtualizePrototypeMethods =
      new PassFactory(PassNames.DEVIRTUALIZE_PROTOTYPE_METHODS, true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new DevirtualizePrototypeMethods(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES5;
        }
      };

  /**
   * Optimizes unused function arguments, unused return values, and inlines constant parameters.
   * Also runs RemoveUnusedCode.
   */
  private final PassFactory optimizeCalls =
      new PassFactory(PassNames.OPTIMIZE_CALLS, false) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          OptimizeCalls passes = new OptimizeCalls(compiler);
          // Remove unused return values.
          passes.addPass(new OptimizeReturns(compiler));
          // Remove all parameters that are constants or unused.
          passes.addPass(new OptimizeParameters(compiler));
          return passes;
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
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

    @Override
    protected FeatureSet featureSet() {
      return ES8_MODULES;
    }
  };

  /** Look for function calls that have no side effects, and annotate them that way. */
  private final PassFactory markNoSideEffectCalls =
      new PassFactory(PassNames.MARK_NO_SIDE_EFFECT_CALLS, true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new MarkNoSideEffectCalls(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES5;
        }
      };

  /** Inlines variables heuristically. */
  private final PassFactory inlineVariables =
      new PassFactory(PassNames.INLINE_VARIABLES, false) {
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

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  /** Inlines variables that are marked as constants. */
  private final PassFactory inlineConstants =
      new PassFactory("inlineConstants", false) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new InlineVariables(compiler, InlineVariables.Mode.CONSTANTS_ONLY, true);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  /** Use data flow analysis to remove dead branches. */
  private final PassFactory removeUnreachableCode =
      new PassFactory(PassNames.REMOVE_UNREACHABLE_CODE, false) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new UnreachableCodeElimination(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8;
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

    @Override
    protected FeatureSet featureSet() {
      return ES8_MODULES;
    }
  };

  /** Inlines simple methods, like getters */
  private final PassFactory inlineSimpleMethods =
      new PassFactory("inlineSimpleMethods", false) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new InlineSimpleMethods(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  /** Kills dead assignments. */
  private final PassFactory deadAssignmentsElimination =
      new PassFactory(PassNames.DEAD_ASSIGNMENT_ELIMINATION, false) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new DeadAssignmentsElimination(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  /** Kills dead property assignments. */
  private final PassFactory deadPropertyAssignmentElimination =
      new PassFactory("deadPropertyAssignmentElimination", false) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new DeadPropertyAssignmentElimination(compiler);
        }

        @Override
        public FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  /** Inlines function calls. */
  private final PassFactory inlineFunctions =
      new PassFactory(PassNames.INLINE_FUNCTIONS, false) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new InlineFunctions(
              compiler,
              compiler.getUniqueNameIdSupplier(),
              options.getInlineFunctionsLevel(),
              options.assumeStrictThis() || options.expectStrictModeInput(),
              options.assumeClosuresOnlyCaptureReferences,
              options.maxFunctionSizeAfterInlining);
        }

        @Override
        public FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  /** Inlines constant properties. */
  private final PassFactory inlineProperties =
      new PassFactory(PassNames.INLINE_PROPERTIES, false) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new InlineProperties(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES5;
        }
      };

  private final PassFactory removeUnusedCodeOnce = getRemoveUnusedCode(true /* isOneTimePass */);
  private final PassFactory removeUnusedCode = getRemoveUnusedCode(false /* isOneTimePass */);

  private PassFactory getRemoveUnusedCode(boolean isOneTimePass) {
    /** Removes variables that are never used. */
    return new PassFactory(PassNames.REMOVE_UNUSED_CODE, isOneTimePass) {
      @Override
      protected CompilerPass create(AbstractCompiler compiler) {
        boolean preserveAnonymousFunctionNames =
            options.anonymousFunctionNaming != AnonymousFunctionNamingPolicy.OFF;
        return new RemoveUnusedCode.Builder(compiler)
            .removeLocalVars(options.removeUnusedLocalVars)
            .removeGlobals(options.removeUnusedVars)
            .preserveFunctionExpressionNames(preserveAnonymousFunctionNames)
            .removeUnusedPrototypeProperties(options.removeUnusedPrototypeProperties)
            .allowRemovalOfExternProperties(options.removeUnusedPrototypePropertiesInExterns)
            .removeUnusedThisProperties(options.isRemoveUnusedClassProperties())
            .removeUnusedObjectDefinePropertiesDefinitions(options.isRemoveUnusedClassProperties())
            .removeUnusedConstructorProperties(options.isRemoveUnusedConstructorProperties())
            .build();
      }

      @Override
      public FeatureSet featureSet() {
        return ES8_MODULES;
      }
    };

  }

  /** Move global symbols to a deeper common module */
  private final PassFactory crossModuleCodeMotion =
      new PassFactory(PassNames.CROSS_CHUNK_CODE_MOTION, false) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new CrossChunkCodeMotion(
              compiler,
              compiler.getModuleGraph(),
              options.parentChunkCanSeeSymbolsDeclaredInChildren);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  /** Move methods to a deeper common module */
  private final PassFactory crossModuleMethodMotion =
      new PassFactory(PassNames.CROSS_CHUNK_METHOD_MOTION, false) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new CrossChunkMethodMotion(
              compiler,
              compiler.getCrossModuleIdGenerator(),
              // Only move properties in externs if we're not treating
              // them as exports.
              options.removeUnusedPrototypePropertiesInExterns,
              options.crossChunkCodeMotionNoStubMethods);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  /** A data-flow based variable inliner. */
  private final PassFactory flowSensitiveInlineVariables =
      new PassFactory(PassNames.FLOW_SENSITIVE_INLINE_VARIABLES, true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new FlowSensitiveInlineVariables(compiler);
        }

        @Override
        public FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  /** Uses register-allocation algorithms to use fewer variables. */
  private final PassFactory coalesceVariableNames =
      new PassFactory(PassNames.COALESCE_VARIABLE_NAMES, true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new CoalesceVariableNames(compiler, options.generatePseudoNames);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  /** Some simple, local collapses (e.g., {@code var x; var y;} becomes {@code var x,y;}. */
  private final PassFactory exploitAssign =
      new PassFactory(PassNames.EXPLOIT_ASSIGN, true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new PeepholeOptimizationsPass(compiler, getName(), new ExploitAssigns());
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  /** Some simple, local collapses (e.g., {@code var x; var y;} becomes {@code var x,y;}. */
  private final PassFactory collapseVariableDeclarations =
      new PassFactory(PassNames.COLLAPSE_VARIABLE_DECLARATIONS, true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new CollapseVariableDeclarations(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  /** Extracts common sub-expressions. */
  private final PassFactory extractPrototypeMemberDeclarations =
      new PassFactory(PassNames.EXTRACT_PROTOTYPE_MEMBER_DECLARATIONS, true) {
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

          return new ExtractPrototypeMemberDeclarations(compiler, pattern);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8;
        }
      };

  /** Rewrites common function definitions to be more compact. */
  private final PassFactory rewriteFunctionExpressions =
      new PassFactory(PassNames.REWRITE_FUNCTION_EXPRESSIONS, true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new FunctionRewriter(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return FeatureSet.latest();
        }
      };

  /** Collapses functions to not use the VAR keyword. */
  private final PassFactory collapseAnonymousFunctions =
      new PassFactory(PassNames.COLLAPSE_ANONYMOUS_FUNCTIONS, true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new CollapseAnonymousFunctions(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  /** Moves function declarations to the top, to simulate actual hoisting. */
  private final PassFactory moveFunctionDeclarations =
      new PassFactory(PassNames.MOVE_FUNCTION_DECLARATIONS, true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new MoveFunctionDeclarations(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8;
        }
      };

  private final PassFactory nameUnmappedAnonymousFunctions =
      new PassFactory(PassNames.NAME_ANONYMOUS_FUNCTIONS, true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new NameAnonymousFunctions(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  private final PassFactory nameMappedAnonymousFunctions =
      new PassFactory(PassNames.NAME_ANONYMOUS_FUNCTIONS, true) {
        @Override
        protected CompilerPass create(final AbstractCompiler compiler) {
          return new CompilerPass() {
            @Override
            public void process(Node externs, Node root) {
              NameAnonymousFunctionsMapped naf =
                  new NameAnonymousFunctionsMapped(
                      compiler, options.inputAnonymousFunctionNamingMap);
              naf.process(externs, root);
              compiler.setAnonymousFunctionNameMap(naf.getFunctionMap());
            }
          };
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
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

    @Override
    protected FeatureSet featureSet() {
      return ES8_MODULES;
    }
  };

  /** Handling for the ObjectPropertyString primitive. */
  private final PassFactory objectPropertyStringPostprocess =
      new PassFactory("ObjectPropertyStringPostprocess", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new ObjectPropertyStringPostprocess(compiler);
    }

    @Override
    protected FeatureSet featureSet() {
      return ES5;
    }
  };

  /**
   * Renames properties so that the two properties that never appear on the same object get the same
   * name.
   */
  private final PassFactory ambiguateProperties =
      new PassFactory(PassNames.AMBIGUATE_PROPERTIES, true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new AmbiguateProperties(
              compiler,
              options.getPropertyReservedNamingFirstChars(),
              options.getPropertyReservedNamingNonFirstChars());
        }

        @Override
        protected FeatureSet featureSet() {
          return ES5;
        }
      };

  /** Mark the point at which the normalized AST assumptions no longer hold. */
  private final PassFactory markUnnormalized =
      new PassFactory("markUnnormalized", true) {
        @Override
        protected CompilerPass create(final AbstractCompiler compiler) {
          return new CompilerPass() {
            @Override
            public void process(Node externs, Node root) {
              compiler.setLifeCycleStage(LifeCycleStage.RAW);
            }
          };
        }

        @Override
        protected FeatureSet featureSet() {
          return FeatureSet.latest();
        }
      };

  private final PassFactory normalize =
      new PassFactory(PassNames.NORMALIZE, true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new Normalize(compiler, false);
        }

        @Override
        protected FeatureSet featureSet() {
          // TODO(johnlenz): Update this and gatherRawExports to latest()
          return ES8_MODULES;
        }
      };

  private final PassFactory externExports =
      new PassFactory(PassNames.EXTERN_EXPORTS, true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new ExternExportsPass(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES5;
        }
      };

  /** Denormalize the AST for code generation. */
  private final PassFactory denormalize = new PassFactory("denormalize", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new Denormalize(compiler);
    }

    @Override
    protected FeatureSet featureSet() {
      return ES8_MODULES;
    }
  };

  /** Inverting name normalization. */
  private final PassFactory invertContextualRenaming =
      new PassFactory("invertContextualRenaming", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return MakeDeclaredNamesUnique.getContextualRenameInverter(compiler);
    }

    @Override
    protected FeatureSet featureSet() {
      return ES8_MODULES;
    }
  };

  /** Renames properties. */
  private final PassFactory renameProperties =
      new PassFactory("renameProperties", true) {
        @Override
        protected CompilerPass create(final AbstractCompiler compiler) {
          checkState(options.propertyRenaming == PropertyRenamingPolicy.ALL_UNQUOTED);
          final VariableMap prevPropertyMap = options.inputPropertyMap;
          return new CompilerPass() {
            @Override
            public void process(Node externs, Node root) {
              RenameProperties rprop =
                  new RenameProperties(
                      compiler,
                      options.generatePseudoNames,
                      prevPropertyMap,
                      options.getPropertyReservedNamingFirstChars(),
                      options.getPropertyReservedNamingNonFirstChars(),
                      options.nameGenerator);
              rprop.process(externs, root);
              compiler.setPropertyMap(rprop.getPropertyMap());
            }
          };
        }
        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  /** Renames variables. */
  private final PassFactory renameVars = new PassFactory("renameVars", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
      final VariableMap prevVariableMap = options.inputVariableMap;
      return new CompilerPass() {
        @Override public void process(Node externs, Node root) {
          compiler.setVariableMap(runVariableRenaming(
              compiler, prevVariableMap, externs, root));
        }
      };
    }

    @Override
    protected FeatureSet featureSet() {
      return ES8_MODULES;
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
    reservedNames.addAll(compiler.getExportedNames());
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
  private final PassFactory renameLabels =
      new PassFactory("renameLabels", true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new RenameLabels(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  /** Convert bracket access to dot access */
  private final PassFactory convertToDottedProperties =
      new PassFactory(PassNames.CONVERT_TO_DOTTED_PROPERTIES, true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new ConvertToDottedProperties(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  private final PassFactory checkAstValidity =
      new PassFactory("checkAstValidity", true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new AstValidator(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return FeatureSet.latest();
        }
      };

  /** Checks that all variables are defined. */
  private final PassFactory varCheckValidity =
      new PassFactory("varCheckValidity", true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new VarCheck(compiler, true);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  /** Adds instrumentations according to an instrumentation template. */
  private final PassFactory instrumentFunctions =
      new PassFactory("instrumentFunctions", true) {
    @Override
    protected CompilerPass create(final AbstractCompiler compiler) {
      return new InstrumentFunctions(
          compiler, compiler.getFunctionNames(),
          options.instrumentationTemplate, options.appNameStr);
    }

    @Override
    protected FeatureSet featureSet() {
      return ES5;
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

        @Override
        protected FeatureSet featureSet() {
          return ES5;
        }
      };

  /** Extern property names gathering pass. */
  private final PassFactory gatherExternProperties =
      new PassFactory("gatherExternProperties", true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new GatherExternProperties(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
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

      @Override
      protected FeatureSet featureSet() {
        return ES5;
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
  static Map<String, Node> getAdditionalReplacements(CompilerOptions options) {
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
      new HotSwapPassFactory("polymerPass") {
        @Override
        protected HotSwapCompilerPass create(AbstractCompiler compiler) {
          return new PolymerPass(
              compiler,
              compiler.getOptions().polymerVersion,
              compiler.getOptions().polymerExportPolicy,
              compiler.getOptions().propertyRenaming == PropertyRenamingPolicy.ALL_UNQUOTED);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES_NEXT;
        }
      };

  private final PassFactory chromePass = new PassFactory("chromePass", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new ChromePass(compiler);
    }

    @Override
    protected FeatureSet featureSet() {
      return ES8_MODULES;
    }
  };

  /** Rewrites the super accessors calls to support Dart Dev Compiler output. */
  private final HotSwapPassFactory dartSuperAccessorsPass =
      new HotSwapPassFactory("dartSuperAccessorsPass") {
        @Override
        protected HotSwapCompilerPass create(AbstractCompiler compiler) {
          return new DartSuperAccessorsPass(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES_NEXT;
        }
      };

  /** Rewrites J2CL constructs to be more optimizable. */
  private final PassFactory j2clConstantHoisterPass =
      new PassFactory("j2clConstantHoisterPass", false) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new J2clConstantHoisterPass(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  /** Optimizes J2CL clinit methods. */
  private final PassFactory j2clClinitPass =
      new PassFactory("j2clClinitPass", false) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          List<Node> changedScopeNodes = compiler.getChangedScopeNodesForPass(getName());
          return new J2clClinitPrunerPass(compiler, changedScopeNodes);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  /** Rewrites J2CL constructs to be more optimizable. */
  private final PassFactory j2clPropertyInlinerPass =
      new PassFactory("j2clES6Pass", true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new J2clPropertyInlinerPass(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  /** Rewrites J2CL constructs to be more optimizable. */
  private final PassFactory j2clPass =
      new PassFactory("j2clPass", true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new J2clPass(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  /** Rewrites J2CL constructs to be more optimizable. */
  private final PassFactory j2clUtilGetDefineRewriterPass =
      new PassFactory("j2clUtilGetDefineRewriterPass", true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new J2clUtilGetDefineRewriterPass(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  private final PassFactory j2clAssertRemovalPass =
      new PassFactory("j2clAssertRemovalPass", true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new J2clAssertRemovalPass(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  private final PassFactory j2clSourceFileChecker =
      new PassFactory("j2clSourceFileChecker", true) {
        @Override
        protected CompilerPass create(final AbstractCompiler compiler) {
          return new J2clSourceFileChecker(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return FeatureSet.latest();
        }
      };

  private final PassFactory j2clChecksPass =
      new PassFactory("j2clChecksPass", true) {
        @Override
        protected CompilerPass create(final AbstractCompiler compiler) {
          return new J2clChecksPass(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES2018;
        }
      };

  private final PassFactory checkConformance =
      new PassFactory("checkConformance", true) {
        @Override
        protected CompilerPass create(final AbstractCompiler compiler) {
          return new CheckConformance(
              compiler, ImmutableList.copyOf(options.getConformanceConfigs()));
        }

        @Override
        protected FeatureSet featureSet() {
          return TYPE_CHECK_SUPPORTED;
        }
      };

  /** Optimizations that output ES6 features. */
  private final PassFactory optimizeToEs6 =
      new PassFactory("optimizeToEs6", true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new SubstituteEs6Syntax(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  /** Rewrites goog.module in whitespace only mode */
  private final HotSwapPassFactory whitespaceWrapGoogModules =
      new HotSwapPassFactory("whitespaceWrapGoogModules") {
        @Override
        protected HotSwapCompilerPass create(AbstractCompiler compiler) {
          return new WhitespaceWrapGoogModules(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES8_MODULES;
        }
      };

  private final PassFactory rewriteCommonJsModules =
      new PassFactory(PassNames.REWRITE_COMMON_JS_MODULES, true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new ProcessCommonJSModules(compiler);
        }

        @Override
        public FeatureSet featureSet() {
          return ES_NEXT;
        }
      };

  private final PassFactory rewriteScriptsToEs6Modules =
      new PassFactory(PassNames.REWRITE_SCRIPTS_TO_ES6_MODULES, true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new Es6RewriteScriptsToModules(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES_NEXT;
        }
      };

  private final PassFactory gatherModuleMetadataPass =
      new PassFactory(PassNames.GATHER_MODULE_METADATA, /* isOneTimePass= */ true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new GatherModuleMetadata(
              compiler, options.processCommonJSModules, options.moduleResolutionMode);
        }

        @Override
        protected FeatureSet featureSet() {
          return ES_NEXT;
        }
      };
}
