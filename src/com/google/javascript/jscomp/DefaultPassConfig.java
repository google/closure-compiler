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
import static com.google.javascript.jscomp.parsing.parser.FeatureSet.ES5;
import static com.google.javascript.jscomp.parsing.parser.FeatureSet.ES6;
import static com.google.javascript.jscomp.parsing.parser.FeatureSet.TYPESCRIPT;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.jscomp.CompilerOptions.ExtractPrototypeMemberDeclarationsMode;
import com.google.javascript.jscomp.CompilerOptions.InstrumentOption;
import com.google.javascript.jscomp.CompilerOptions.PropertyCollapseLevel;
import com.google.javascript.jscomp.CompilerOptions.Reach;
import com.google.javascript.jscomp.CoverageInstrumentationPass.CoverageReach;
import com.google.javascript.jscomp.ExtractPrototypeMemberDeclarations.Pattern;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.jscomp.ScopedAliases.InvalidModuleGetHandling;
import com.google.javascript.jscomp.disambiguate.DisambiguateProperties2;
import com.google.javascript.jscomp.ijs.ConvertToTypedInterface;
import com.google.javascript.jscomp.lint.CheckArrayWithGoogObject;
import com.google.javascript.jscomp.lint.CheckConstantCaseNames;
import com.google.javascript.jscomp.lint.CheckDuplicateCase;
import com.google.javascript.jscomp.lint.CheckEmptyStatements;
import com.google.javascript.jscomp.lint.CheckEnums;
import com.google.javascript.jscomp.lint.CheckEs6ModuleFileStructure;
import com.google.javascript.jscomp.lint.CheckEs6Modules;
import com.google.javascript.jscomp.lint.CheckExtraRequires;
import com.google.javascript.jscomp.lint.CheckInterfaces;
import com.google.javascript.jscomp.lint.CheckJSDocStyle;
import com.google.javascript.jscomp.lint.CheckMissingSemicolon;
import com.google.javascript.jscomp.lint.CheckNoMutatedEs6Exports;
import com.google.javascript.jscomp.lint.CheckNullabilityModifiers;
import com.google.javascript.jscomp.lint.CheckNullableReturn;
import com.google.javascript.jscomp.lint.CheckPrimitiveAsObject;
import com.google.javascript.jscomp.lint.CheckPrototypeProperties;
import com.google.javascript.jscomp.lint.CheckProvidesSorted;
import com.google.javascript.jscomp.lint.CheckRequiresSorted;
import com.google.javascript.jscomp.lint.CheckUnusedLabels;
import com.google.javascript.jscomp.lint.CheckUselessBlocks;
import com.google.javascript.jscomp.modules.ModuleMapCreator;
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
      preprocessorSymbolTableFactory = new PreprocessorSymbolTable.CachedInstanceFactory();

  /**
   * Global state necessary for doing hotswap recompilation of files with references to
   * processed goog.modules.
   */
  private transient ClosureRewriteModule.GlobalRewriteState moduleRewriteState = null;

  public DefaultPassConfig(CompilerOptions options) {
    super(options);
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
    passes.add(markUntranspilableFeaturesAsRemoved);

    passes.add(checkVariableReferencesForTranspileOnly);
    passes.add(gatherModuleMetadataPass);
    passes.add(createModuleMapPass);

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

    TranspilationPasses.addTranspilationRuntimeLibraries(passes, options);

    TranspilationPasses.addPostCheckTranspilationPasses(passes, options);

    if (options.needsTranspilationFrom(ES6)) {
      if (options.getRewritePolyfills()) {
        if (options.getIsolatePolyfills()) {
          throw new IllegalStateException(
              "Polyfill isolation cannot be used in transpileOnly mode");
        }
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

  private void addModuleRewritingPasses(List<PassFactory> checks, CompilerOptions options) {
    if (options.getLanguageIn().toFeatureSet().has(FeatureSet.Feature.MODULES)) {
      checks.add(rewriteGoogJsImports);
      TranspilationPasses.addEs6ModulePass(checks, preprocessorSymbolTableFactory);
    }

    if (options.closurePass) {
      checks.add(closureRewriteModule);
    }
    // TODO(b/141389184): include processClosureProvidesAndRequires here
  }

  @Override
  protected List<PassFactory> getChecks() {
    List<PassFactory> checks = new ArrayList<>();

    checks.add(syncCompilerFeatures);

    if (options.shouldGenerateTypedExterns()) {
      checks.add(addSyntheticScript);
      checks.add(closureGoogScopeAliasesForIjs);
      checks.add(closureRewriteClass);
      checks.add(generateIjs);
      checks.add(whitespaceWrapGoogModules);
      checks.add(removeSyntheticScript);
      return checks;
    }

    // Run this pass before any pass tries to inject new runtime libraries
    checks.add(addSyntheticScript);

    if (options.needsTranspilationFrom(TYPESCRIPT)) {
      checks.add(convertEs6TypedToEs6);
    }

    if (!options.checksOnly) {
      checks.add(markUntranspilableFeaturesAsRemoved);
    }

    checks.add(gatherGettersAndSetters);

    checks.add(createEmptyPass("beforeStandardChecks"));

    if (!options.processCommonJSModules
        && options.getLanguageIn().toFeatureSet().has(FeatureSet.Feature.MODULES)) {
      checks.add(rewriteScriptsToEs6Modules);
    }

    // Run these passes after promoting scripts to modules, but before rewriting any other modules.
    checks.add(gatherModuleMetadataPass);
    checks.add(createModuleMapPass);

    if (options.processCommonJSModules) {
      checks.add(rewriteCommonJsModules);
    }

    // Note: ChromePass can rewrite invalid @type annotations into valid ones, so should run before
    // JsDoc checks.
    if (options.isChromePassEnabled()) {
      checks.add(chromePass);
    }

    // Verify JsDoc annotations and check ES6 modules
    checks.add(checkJsDocAndEs6Modules);

    checks.add(checkTypeImportCodeReferences);

    if (options.enables(DiagnosticGroups.LINT_CHECKS)) {
      checks.add(lintChecks);
    }

    if (options.closurePass && options.enables(DiagnosticGroups.LINT_CHECKS)) {
      checks.add(checkRequiresAndProvidesSorted);
    }

    if (options.enables(DiagnosticGroups.EXTRA_REQUIRE)) {
      checks.add(extraRequires);
    }

    if (options.enables(DiagnosticGroups.MISSING_REQUIRE)
        || options.enables(DiagnosticGroups.STRICT_MISSING_REQUIRE)) {
      checks.add(missingAndExtraRequires);
    }

    if (options.enables(DiagnosticGroups.STRICTER_MISSING_REQUIRE)
        || options.enables(DiagnosticGroups.STRICTER_MISSING_REQUIRE_TYPE)) {
      checks.add(checkMissingRequires);
    }

    checks.add(checkVariableReferences);

    checks.add(declaredGlobalExternsOnWindow);

    if (!options.processCommonJSModules) {
      // TODO(ChadKillingsworth): move CommonJS module rewriting after VarCheck
      checks.add(checkVars);
    }

    if (options.closurePass) {
      checks.add(checkClosureImports);
    }

    checks.add(checkStrictMode);

    if (options.closurePass) {
      checks.add(closureCheckModule);
    }

    checks.add(checkSuper);

    if (options.closurePass) {
      checks.add(closureRewriteClass);
    }

    checks.add(checkSideEffects);

    if (options.enables(DiagnosticGroups.MISSING_PROVIDE)) {
      checks.add(checkProvides);
    }

    if (options.angularPass) {
      checks.add(angularPass);
    }

    if (options.closurePass) {
      checks.add(closureGoogScopeAliases);
    }

    // TODO(b/141389184): Move this after the Polymer pass
    if (options.shouldRewriteModulesBeforeTypechecking()) {
      addModuleRewritingPasses(checks, options);
    }

    if (options.closurePass) {
      checks.add(closurePrimitives);
      if (options.shouldRewriteModulesBeforeTypechecking()) {
        checks.add(closureProvidesRequires);
      }
    }

    // It's important that the PolymerPass run *after* the ClosurePrimitives and ChromePass rewrites
    // and *before* the suspicious code checks. This is enforced in the assertValidOrder method.
    if (options.polymerVersion != null) {
      checks.add(polymerPass);
    }

    if (options.closurePass && options.checkMissingGetCssNameLevel.isOn()) {
      checks.add(closureCheckGetCssName);
    }

    if (options.syntheticBlockStartMarker != null) {
      // This pass must run before the first fold constants pass.
      checks.add(createSyntheticBlocks);
    }

    if (options.processCommonJSModules) {
      // TODO(ChadKillingsworth): remove this branch.
      checks.add(checkVars);
    }

    if (options.inferConsts) {
      checks.add(inferConsts);
    }

    if (options.computeFunctionSideEffects) {
      checks.add(checkRegExp);
    }

    // It's important that the Dart super accessors pass run *before* es6ConvertSuper,
    // which is a "late" ES6 pass. This is enforced in the assertValidOrder method.
    if (options.dartPass && !options.getOutputFeatureSet().contains(ES6)) {
      checks.add(dartSuperAccessorsPass);
    }

    // Passes running before this point should expect to see language features up to ES_2017.
    checks.add(createEmptyPass(PassNames.BEFORE_PRE_TYPECHECK_TRANSPILATION));

    TranspilationPasses.addTranspilationRuntimeLibraries(checks, options);

    if ((options.rewritePolyfills || options.getIsolatePolyfills()) && !options.checksOnly) {
      TranspilationPasses.addRewritePolyfillPass(checks);
    }

    checks.add(injectRuntimeLibraries);
    checks.add(createEmptyPass(PassNames.BEFORE_TYPE_CHECKING));

    if (options.checkTypes || options.inferTypes) {
      checks.add(inferTypes);
      if (options.checkTypes) {
        checks.add(checkTypes);
      } else {
        checks.add(inferJsDocInfo);
      }
    }

    // We assume that only clients who are going to re-compile, or do in-depth static analysis,
    // will need the typed scope creator after the compile job.
    if (!options.preservesDetailedSourceInfo() && !options.allowsHotswapReplaceScript()) {
      checks.add(clearTypedScopeCreatorPass);
    }

    if (options.shouldRewriteModulesAfterTypechecking()) {
      addModuleRewritingPasses(checks, options);
      if (options.closurePass) {
        checks.add(closureProvidesRequires);
      }
    }

    // We assume that only clients who are going to re-compile, or do in-depth static analysis,
    // will need the typed scope creator after the compile job.
    if (!options.preservesDetailedSourceInfo() && !options.allowsHotswapReplaceScript()) {
      checks.add(clearTopTypedScopePass);
    }

    // CheckSuspiciousCode requires type information, so must run after the type checker.
    if (options.checkSuspiciousCode
        || options.enables(DiagnosticGroups.GLOBAL_THIS)
        || options.enables(DiagnosticGroups.DEBUGGER_STATEMENT_PRESENT)) {
      checks.add(suspiciousCode);
    }

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

    if (options.generateExports) {
      checks.add(generateExports);
    }

    checks.add(createEmptyPass(PassNames.AFTER_STANDARD_CHECKS));

    if (options.checksOnly) {
      checks.add(removeSyntheticScript);
    } else if (!options.checksOnly) {
      checks.add(mergeSyntheticScript);
      if (options.j2clPassMode.shouldAddJ2clPasses()) {
        checks.add(j2clPass);
      }
      // At this point all checks have been done.
      if (options.exportTestFunctions) {
        checks.add(exportTestFunctions);
      }

      // There's no need to complete transpilation if we're only running checks.
      TranspilationPasses.addPostCheckTranspilationPasses(checks, options);
    }

    assertAllOneTimePasses(checks);
    assertValidOrderForChecks(checks);

    checks.add(createEmptyPass(PassNames.BEFORE_SERIALIZATION));

    return checks;
  }

  @Override
  protected List<PassFactory> getOptimizations() {
    List<PassFactory> passes = new ArrayList<>();

    if (options.skipNonTranspilationPasses) {
      return passes;
    }

    passes.add(removeWeakSources);

    // TODO(b/124915436): Remove this pass completely after cleaning up the codebase.
    if (!options.allowsHotswapReplaceScript()) {
      passes.add(inlineTypeAliases);
    }

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

    if (options.j2clPassMode.shouldAddJ2clPasses()) {
      passes.add(j2clUtilGetDefineRewriterPass);
    }

    if (options.instrumentForCoverage) {
      if (options.instrumentBranchCoverage) {
        options.setInstrumentForCoverageOption(InstrumentOption.BRANCH_ONLY);
      } else {
        options.setInstrumentForCoverageOption(InstrumentOption.LINE_ONLY);
      }
    }

    if (options.getInstrumentForCoverageOption() != InstrumentOption.NONE) {
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

    assertAllOneTimePasses(passes);

    // Inline aliases so that following optimizations don't have to understand alias chains.
    if (options.getPropertyCollapseLevel() == PropertyCollapseLevel.ALL) {
      if (options.needsTranspilationFrom(ES6)) {
        // This helps AggressiveInlineAliases / CollapseProperties with static inheritance
        passes.add(convertStaticInheritance);
      }
      passes.add(aggressiveInlineAliases);
    }

    // Inline getters/setters in J2CL classes so that Object.defineProperties() calls (resulting
    // from desugaring) don't block class stripping.
    if (options.j2clPassMode.shouldAddJ2clPasses()
        && options.getPropertyCollapseLevel() == PropertyCollapseLevel.ALL) {
      // Relies on collapseProperties-triggered aggressive alias inlining.
      passes.add(j2clPropertyInlinerPass);
    }

    // Collapsing properties can undo constant inlining, so we do this before
    // the main optimization loop.
    if (options.getPropertyCollapseLevel() != PropertyCollapseLevel.NONE) {
      passes.add(collapseProperties);
    }

    if (options.inferConsts) {
      passes.add(inferConsts);
    }

    // Detects whether invocations of the method goog.string.Const.from are done with an argument
    // which is a string literal. Needs to happen after inferConsts and collapseProperties.
    // TODO(b/160616664): this should be in getChecks() instead of getOptimizations(). But
    // for that the pass needs to understand constant properties as well. See b/31301233#comment10
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

    // Property disambiguation should only run once and needs to be done
    // soon after type checking, both so that it can make use of type
    // information and so that other passes can take advantage of the renamed
    // properties.
    if (options.shouldDisambiguateProperties() && options.isTypecheckingEnabled()) {
      if (options.shouldUseGraphBasedDisambiguator()) {
        passes.add(disambiguateProperties2);
      } else {
        passes.add(disambiguateProperties);
      }
    }

    if (options.computeFunctionSideEffects) {
      passes.add(markPureFunctions);
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
    if (options.devirtualizeMethods) {
      passes.add(devirtualizeMethods);
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

    // Isolate injected polyfills from the global scope. Runs late in the optimization loop
    // to take advantage of property renaming & RemoveUnusedCode, as this pass will increase code
    // size by wrapping all potential polyfill usages.
    if (options.getIsolatePolyfills()) {
      passes.add(isolatePolyfills);
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

    passes.add(removeTypes);

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

    if (options.variableRenaming != VariableRenamingPolicy.ALL) {
      // If we're leaving some (or all) variables with their old names,
      // then we need to undo any of the markers we added for distinguishing
      // local variables ("x" -> "x$jscomp$1").
      passes.add(invertContextualRenaming);
    }

    if (options.variableRenaming != VariableRenamingPolicy.OFF) {
      passes.add(renameVars);
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

    // If side-effects were protected, remove the protection now.
    if (options.shouldProtectHiddenSideEffects()) {
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
        || options.rewritePolyfills;
  }

  /** Set feature set of compiler to only features used in the externs and sources */
  private final PassFactory syncCompilerFeatures =
      PassFactory.builder()
          .setName("syncCompilerFeatures")
          .setInternalFactory(SyncCompilerFeatures::new)
          // This pass just records which features actually appear in the input code.
          // It needs to work no matter what those features are.
          .setFeatureSet(FeatureSet.all())
          .build();

  private final PassFactory checkSideEffects =
      PassFactory.builderForHotSwap()
          .setName("checkSideEffects")
          .setInternalFactory(
              (compiler) ->
                  new CheckSideEffects(
                      compiler,
                      options.checkSuspiciousCode,
                      options.shouldProtectHiddenSideEffects()))
          .setFeatureSetForChecks()
          .build();

  /** Removes the "protector" functions that were added by CheckSideEffects. */
  private final PassFactory stripSideEffectProtection =
      PassFactory.builder()
          .setName(PassNames.STRIP_SIDE_EFFECT_PROTECTION)
          .setInternalFactory(CheckSideEffects.StripProtection::new)
          .setFeatureSet(FeatureSet.latest())
          .build();

  /** Checks for code that is probably wrong (such as stray expressions). */
  private final PassFactory suspiciousCode =
      PassFactory.builderForHotSwap()
          .setName("suspiciousCode")
          .setInternalFactory(
              (compiler) -> {
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
              })
          .setFeatureSetForChecks()
          .build();

  /** Verify that all the passes are one-time passes. */
  private static void assertAllOneTimePasses(List<PassFactory> passes) {
    for (PassFactory pass : passes) {
      checkState(!pass.isRunInFixedPointLoop());
    }
  }

  /** Verify that all the passes are multi-run passes. */
  private static void assertAllLoopablePasses(List<PassFactory> passes) {
    for (PassFactory pass : passes) {
      checkState(pass.isRunInFixedPointLoop());
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
   * Certain checks and rewriting passes need to run in a particular order. For example, the
   * PolymerPass will not work correctly unless it runs after the goog.provide() processing. This
   * enforces those constraints.
   *
   * @param checks The list of check passes
   */
  private void assertValidOrderForChecks(List<PassFactory> checks) {
    assertPassOrder(
        checks,
        declaredGlobalExternsOnWindow,
        checkVars,
        "declaredGlobalExternsOnWindow must happen before VarCheck, which adds synthetic externs");
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
    assertPassOrder(
        checks,
        addSyntheticScript,
        gatherModuleMetadataPass,
        "Cannot add a synthetic script node after module metadata creation.");
    assertPassOrder(
        checks,
        closureRewriteModule,
        removeSyntheticScript,
        "Synthetic script node should be removed only after module rewriting.");

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

    assertPassOrder(
        checks,
        gatherModuleMetadataPass,
        closureCheckModule,
        "Need to gather module metadata before checking closure modules.");

    assertPassOrder(
        checks,
        gatherModuleMetadataPass,
        createModuleMapPass,
        "Need to gather module metadata before scanning modules.");

    assertPassOrder(
        checks,
        createModuleMapPass,
        rewriteCommonJsModules,
        "Need to gather module information before rewriting CommonJS modules.");

    assertPassOrder(
        checks,
        rewriteScriptsToEs6Modules,
        gatherModuleMetadataPass,
        "Need to gather module information after rewriting scripts to modules.");

    assertPassOrder(
        checks,
        gatherModuleMetadataPass,
        checkMissingRequires,
        "Need to gather module information before checking for missing requires.");

    assertPassOrder(
        checks,
        j2clPass,
        TranspilationPasses.rewriteGenerators,
        "J2CL normalization should be done before generator re-writing.");
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

    assertPassOrder(
        optimizations,
        removeUnusedCode,
        isolatePolyfills,
        "Polyfill isolation should be done after RemovedUnusedCode. Otherwise unused polyfill"
            + " removal will not find any polyfill usages and will delete all polyfills.");
  }

  /** Checks that all goog.require()s are used. */
  private final PassFactory extraRequires =
      PassFactory.builderForHotSwap()
          .setName("checkExtraRequires")
          .setFeatureSetForChecks()
          .setInternalFactory(CheckExtraRequires::new)
          .build();

  /** Checks that all constructed classes are goog.require()d. */
  private final PassFactory missingAndExtraRequires =
      PassFactory.builderForHotSwap()
          .setName("checkMissingAndExtraRequires")
          .setFeatureSetForChecks()
          .setInternalFactory(CheckMissingAndExtraRequires::new)
          .build();

  private final PassFactory checkMissingRequires =
      PassFactory.builder()
          .setName("checkMissingRequires")
          .setInternalFactory(
              (compiler) -> new CheckMissingRequires(compiler, compiler.getModuleMetadataMap()))
          .setFeatureSetForChecks()
          .build();

  /** Makes sure @constructor is paired with goog.provides(). */
  private final PassFactory checkProvides =
      PassFactory.builderForHotSwap()
          .setName("checkProvides")
          .setFeatureSetForChecks()
          .setInternalFactory(CheckProvides::new)
          .build();

  private static final DiagnosticType GENERATE_EXPORTS_ERROR =
      DiagnosticType.error(
          "JSC_GENERATE_EXPORTS_ERROR",
          "Exports can only be generated if export symbol/property functions are set.");

  /** Verifies JSDoc annotations are used properly and checks for ES6 modules. */
  private final PassFactory checkJsDocAndEs6Modules =
      PassFactory.builderForHotSwap()
          .setName("checkJsDocAndEs6Modules")
          .setFeatureSetForChecks()
          .setInternalFactory(
              (compiler) ->
                  combineChecks(
                      compiler,
                      ImmutableList.of(new CheckJSDoc(compiler), new Es6CheckModule(compiler))))
          .build();

  /** Generates exports for @export annotations. */
  private final PassFactory generateExports =
      PassFactory.builder()
          .setName(PassNames.GENERATE_EXPORTS)
          .setInternalFactory(
              (compiler) -> {
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
              })
          .setFeatureSetForChecks()
          .build();

  private final PassFactory generateIjs =
      PassFactory.builder()
          .setName("generateIjs")
          .setInternalFactory(ConvertToTypedInterface::new)
          .setFeatureSetForChecks()
          .build();

  /** Generates exports for functions associated with JsUnit. */
  private final PassFactory exportTestFunctions =
      PassFactory.builder()
          .setName(PassNames.EXPORT_TEST_FUNCTIONS)
          .setInternalFactory(
              (compiler) -> {
                CodingConvention convention = compiler.getCodingConvention();
                if (convention.getExportSymbolFunction() != null) {
                  return new ExportTestFunctions(
                      compiler,
                      convention.getExportSymbolFunction(),
                      convention.getExportPropertyFunction());
                } else {
                  return new ErrorPass(compiler, GENERATE_EXPORTS_ERROR);
                }
              })
          .setFeatureSetForChecks()
          .build();

  /** Raw exports processing pass. */
  private final PassFactory gatherRawExports =
      PassFactory.builder()
          .setName(PassNames.GATHER_RAW_EXPORTS)
          .setInternalFactory(
              (compiler) -> {
                final GatherRawExports pass = new GatherRawExports(compiler);

                return new CompilerPass() {
                  @Override
                  public void process(Node externs, Node root) {
                    pass.process(externs, root);
                    compiler.addExportedNames(pass.getExportedVariableNames());
                  }
                };
              })
          .setFeatureSet(FeatureSet.latest())
          .build();

  /** Closure pre-processing pass. */
  private final PassFactory closurePrimitives =
      PassFactory.builderForHotSwap()
          .setName("closurePrimitives")
          .setInternalFactory(
              (compiler) -> {
                preprocessorSymbolTableFactory.maybeInitialize(compiler);
                final ProcessClosurePrimitives pass =
                    new ProcessClosurePrimitives(
                        compiler, preprocessorSymbolTableFactory.getInstanceOrNull());

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
              })
          .setFeatureSetForChecks()
          .build();

  /** Closure provide/require rewriting pass. */
  private final PassFactory closureProvidesRequires =
      PassFactory.builderForHotSwap()
          .setName("closureProvidesRequires")
          .setInternalFactory(
              (compiler) -> {
                preprocessorSymbolTableFactory.maybeInitialize(compiler);
                TypedScope globalTypedScope =
                    compiler.getOptions().allowsHotswapReplaceScript() ? null : this.getTopScope();
                return new ProcessClosureProvidesAndRequires(
                    compiler,
                    preprocessorSymbolTableFactory.getInstanceOrNull(),
                    options.brokenClosureRequiresLevel,
                    options.shouldPreservesGoogProvidesAndRequires(),
                    globalTypedScope);
              })
          .setFeatureSetForChecks()
          .build();

  /** Process AngularJS-specific annotations. */
  private final PassFactory angularPass =
      PassFactory.builderForHotSwap()
          .setName(PassNames.ANGULAR_PASS)
          .setInternalFactory(AngularPass::new)
          .setFeatureSetForChecks()
          .build();

  /**
   * The default i18n pass. A lot of the options are not configurable, because ReplaceMessages has a
   * lot of legacy logic.
   */
  private final PassFactory replaceMessages =
      PassFactory.builder()
          .setName(PassNames.REPLACE_MESSAGES)
          .setInternalFactory(
              (compiler) ->
                  new ReplaceMessages(
                      compiler,
                      options.messageBundle,
                      /* warn about message dupes */
                      true,
                      /* allow messages with goog.getMsg */
                      JsMessage.Style.CLOSURE,
                      /* if we can't find a translation, don't worry about it. */
                      false))
          .setFeatureSetForOptimizations()
          .build();

  private final PassFactory replaceMessagesForChrome =
      PassFactory.builder()
          .setName(PassNames.REPLACE_MESSAGES)
          .setInternalFactory(
              (compiler) ->
                  new ReplaceMessagesForChrome(
                      compiler,
                      new GoogleJsMessageIdGenerator(options.tcProjectId),
                      /* warn about message dupes */
                      true,
                      /* allow messages with goog.getMsg */
                      JsMessage.Style.CLOSURE))
          .setFeatureSetForOptimizations()
          .build();

  /** Applies aliases and inlines goog.scope. */
  private final PassFactory closureGoogScopeAliasesForIjs =
      PassFactory.builderForHotSwap()
          .setName("closureGoogScopeAliasesForIjs")
          .setInternalFactory((compiler) -> ScopedAliases.builder(compiler).build())
          .setFeatureSetForChecks()
          .build();

  /**
   * Applies aliases and inlines goog.scope, storing information about the transformations
   * performed.
   */
  private final PassFactory closureGoogScopeAliases =
      PassFactory.builderForHotSwap()
          .setName("closureGoogScopeAliases")
          .setInternalFactory(
              (compiler) -> {
                preprocessorSymbolTableFactory.maybeInitialize(compiler);
                return ScopedAliases.builder(compiler)
                    .setPreprocessorSymbolTable(preprocessorSymbolTableFactory.getInstanceOrNull())
                    .setAliasTransformationHandler(options.getAliasTransformationHandler())
                    .setModuleMetadataMap(compiler.getModuleMetadataMap())
                    .setInvalidModuleGetHandling(InvalidModuleGetHandling.DELETE)
                    .build();
              })
          .setFeatureSetForChecks()
          .build();

  private final PassFactory injectRuntimeLibraries =
      PassFactory.builder()
          .setName("InjectRuntimeLibraries")
          .setInternalFactory(InjectRuntimeLibraries::new)
          .setFeatureSetForChecks()
          .build();

  /** Desugars ES6_TYPED features into ES6 code. */
  final PassFactory convertEs6TypedToEs6 =
      PassFactory.builderForHotSwap()
          .setName("convertEs6Typed")
          .setInternalFactory(Es6TypedToEs6Converter::new)
          .setFeatureSet(TYPESCRIPT)
          .build();

  private final PassFactory markUntranspilableFeaturesAsRemoved =
      PassFactory.builder()
          .setName("markUntranspilableFeaturesAsRemoved")
          .setInternalFactory(
              (compiler) ->
                  new MarkUntranspilableFeaturesAsRemoved(compiler, options.getOutputFeatureSet()))
          .setFeatureSetForChecks()
          .build();

  private final PassFactory convertStaticInheritance =
      PassFactory.builder()
          .setName("Es6StaticInheritance")
          .setInternalFactory(Es6ToEs3ClassSideInheritance::new)
          .setFeatureSetForOptimizations()
          .build();

  private final PassFactory inlineTypeAliases =
      PassFactory.builder()
          .setName(PassNames.INLINE_TYPE_ALIASES)
          .setInternalFactory(InlineAliases::new)
          .setFeatureSetForOptimizations()
          .build();

  /** Inlines type aliases if they are explicitly or effectively const. */
  private final PassFactory aggressiveInlineAliases =
      PassFactory.builder()
          .setName("aggressiveInlineAliases")
          .setInternalFactory(AggressiveInlineAliases::new)
          .setFeatureSetForOptimizations()
          .build();

  private final PassFactory removeWeakSources =
      PassFactory.builder()
          .setName("removeWeakSources")
          .setInternalFactory(RemoveWeakSources::new)
          .setFeatureSet(FeatureSet.latest())
          .build();

  private final PassFactory declaredGlobalExternsOnWindow =
      PassFactory.builder()
          .setName(PassNames.DECLARED_GLOBAL_EXTERNS_ON_WINDOW)
          .setInternalFactory(DeclaredGlobalExternsOnWindow::new)
          .setFeatureSetForChecks()
          .build();

  private final PassFactory checkTypeImportCodeReferences =
      PassFactory.builder()
          .setName("checkTypeImportCodeReferences")
          .setInternalFactory(CheckTypeImportCodeReferences::new)
          .setFeatureSetForChecks()
          .build();

  /** Rewrites goog.defineClass */
  private final PassFactory closureRewriteClass =
      PassFactory.builderForHotSwap()
          .setName(PassNames.CLOSURE_REWRITE_CLASS)
          .setInternalFactory(ClosureRewriteClass::new)
          .setFeatureSetForChecks()
          .build();

  /** Checks of correct usage of goog.module */
  private final PassFactory closureCheckModule =
      PassFactory.builderForHotSwap()
          .setName("closureCheckModule")
          .setInternalFactory(
              (compiler) -> new ClosureCheckModule(compiler, compiler.getModuleMetadataMap()))
          .setFeatureSetForChecks()
          .build();

  /** Rewrites goog.module */
  private final PassFactory closureRewriteModule =
      PassFactory.builderForHotSwap()
          .setName("closureRewriteModule")
          .setInternalFactory(
              (compiler) -> {
                preprocessorSymbolTableFactory.maybeInitialize(compiler);
                maybeInitializeModuleRewriteState();
                TypedScope globalTypedScope =
                    compiler.getOptions().allowsHotswapReplaceScript() ? null : this.getTopScope();
                return new ClosureRewriteModule(
                    compiler,
                    preprocessorSymbolTableFactory.getInstanceOrNull(),
                    moduleRewriteState,
                    globalTypedScope);
              })
          .setFeatureSetForChecks()
          .build();

  /** Checks goog.require, goog.forwardDeclare, goog.requireType, and goog.module.get calls */
  private final PassFactory checkClosureImports =
      PassFactory.builderForHotSwap()
          .setName("checkGoogRequires")
          .setInternalFactory(
              (compiler) -> new CheckClosureImports(compiler, compiler.getModuleMetadataMap()))
          .setFeatureSetForChecks()
          .build();

  /** Rewrite imports for Closure Library's goog.js file to global goog references. */
  private final PassFactory rewriteGoogJsImports =
      PassFactory.builderForHotSwap()
          .setName("rewriteGoogJsImports")
          .setInternalFactory(
              (compiler) ->
                  new RewriteGoogJsImports(
                      compiler,
                      RewriteGoogJsImports.Mode.LINT_AND_REWRITE,
                      compiler.getModuleMap()))
          .setFeatureSetForChecks()
          .build();

  /** Checks that CSS class names are wrapped in goog.getCssName */
  private final PassFactory closureCheckGetCssName =
      PassFactory.builder()
          .setName("closureCheckGetCssName")
          .setInternalFactory(
              (compiler) ->
                  new CheckMissingGetCssName(
                      compiler,
                      options.checkMissingGetCssNameLevel,
                      options.checkMissingGetCssNameBlacklist))
          .setFeatureSetForChecks()
          .build();

  /**
   * Processes goog.getCssName. The cssRenamingMap is used to lookup replacement values for the
   * classnames. If null, the raw class names are inlined.
   */
  private final PassFactory closureReplaceGetCssName =
      PassFactory.builder()
          .setName("closureReplaceGetCssName")
          .setInternalFactory(
              (compiler) ->
                  new CompilerPass() {
                    @Override
                    public void process(Node externs, Node jsRoot) {
                      Map<String, Integer> newCssNames = null;
                      if (options.gatherCssNames) {
                        newCssNames = new HashMap<>();
                      }
                      ReplaceCssNames pass =
                          new ReplaceCssNames(compiler, newCssNames, options.cssRenamingSkiplist);
                      pass.process(externs, jsRoot);
                      compiler.setCssNames(newCssNames);
                    }
                  })
          .setFeatureSetForChecks()
          .build();

  /**
   * Creates synthetic blocks to prevent FoldConstants from moving code past markers in the source.
   */
  private final PassFactory createSyntheticBlocks =
      PassFactory.builder()
          .setName("createSyntheticBlocks")
          .setInternalFactory(
              (compiler) ->
                  new CreateSyntheticBlocks(
                      compiler, options.syntheticBlockStartMarker, options.syntheticBlockEndMarker))
          .setFeatureSetForChecks()
          .build();

  private final PassFactory earlyPeepholeOptimizations =
      PassFactory.builder()
          .setName("earlyPeepholeOptimizations")
          .setInternalFactory(
              (compiler) -> {
                boolean useTypesForOptimization =
                    compiler.getOptions().useTypesForLocalOptimization;
                List<AbstractPeepholeOptimization> peepholeOptimizations = new ArrayList<>();
                peepholeOptimizations.add(new PeepholeRemoveDeadCode());
                if (compiler.getOptions().j2clPassMode.shouldAddJ2clPasses()) {
                  peepholeOptimizations.add(
                      new J2clEqualitySameRewriterPass(useTypesForOptimization));
                }
                return new PeepholeOptimizationsPass(
                    compiler, "earlyPeepholeOptimizations", peepholeOptimizations);
              })
          .setFeatureSetForOptimizations()
          .build();

  private final PassFactory earlyInlineVariables =
      PassFactory.builder()
          .setName("earlyInlineVariables")
          .setInternalFactory(
              (compiler) -> {
                InlineVariables.Mode mode;
                if (options.inlineVariables) {
                  mode = InlineVariables.Mode.ALL;
                } else if (options.inlineLocalVariables) {
                  mode = InlineVariables.Mode.LOCALS_ONLY;
                } else {
                  throw new IllegalStateException("No variable inlining option set.");
                }
                return new InlineVariables(compiler, mode, true);
              })
          .setFeatureSetForOptimizations()
          .build();

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
      optimizations.add(new J2clEqualitySameRewriterPass(useTypesForOptimization));
      optimizations.add(new J2clStringValueOfRewriterPass());
    }
    optimizations.add(new PeepholeFoldConstants(late, useTypesForOptimization));
    optimizations.add(new PeepholeCollectPropertyAssignments());
    return new PeepholeOptimizationsPass(compiler, passName, optimizations);
  }

  /** Various peephole optimizations. */
  private final PassFactory peepholeOptimizations =
      PassFactory.builder()
          .setName(PassNames.PEEPHOLE_OPTIMIZATIONS)
          .setRunInFixedPointLoop(true)
          .setInternalFactory(
              (compiler) ->
                  createPeepholeOptimizationsPass(compiler, PassNames.PEEPHOLE_OPTIMIZATIONS))
          .setFeatureSetForOptimizations()
          .build();

  /** Various peephole optimizations. */
  private final PassFactory peepholeOptimizationsOnce =
      PassFactory.builder()
          .setName(PassNames.PEEPHOLE_OPTIMIZATIONS)
          .setInternalFactory(
              (compiler) ->
                  createPeepholeOptimizationsPass(compiler, PassNames.PEEPHOLE_OPTIMIZATIONS))
          .setFeatureSetForOptimizations()
          .build();

  /** Same as peepholeOptimizations but aggressively merges code together */
  private final PassFactory latePeepholeOptimizations =
      PassFactory.builder()
          .setName("latePeepholeOptimizations")
          .setInternalFactory(
              (compiler) -> {
                final boolean late = true;
                final boolean useTypesForOptimization = options.useTypesForLocalOptimization;
                return new PeepholeOptimizationsPass(
                    compiler,
                    "latePeepholeOptimizations",
                    new StatementFusion(),
                    new PeepholeRemoveDeadCode(),
                    new PeepholeMinimizeConditions(late),
                    new PeepholeSubstituteAlternateSyntax(late),
                    new PeepholeReplaceKnownMethods(late, useTypesForOptimization),
                    new PeepholeFoldConstants(late, useTypesForOptimization),
                    new PeepholeReorderConstantExpression());
              })
          .setFeatureSetForOptimizations()
          .build();

  /** Checks that all variables are defined. */
  private final PassFactory checkVars =
      PassFactory.builderForHotSwap()
          .setName(PassNames.CHECK_VARS)
          .setInternalFactory(VarCheck::new)
          .setFeatureSetForChecks()
          .build();

  /** Infers constants. */
  private final PassFactory inferConsts =
      PassFactory.builder()
          .setName(PassNames.INFER_CONSTS)
          .setInternalFactory(InferConsts::new)
          .setFeatureSetForChecks()
          .build();

  /** Checks for RegExp references. */
  private final PassFactory checkRegExp =
      PassFactory.builder()
          .setName(PassNames.CHECK_REG_EXP)
          .setInternalFactory(
              (compiler) -> {
                final CheckRegExp pass = new CheckRegExp(compiler);

                return new CompilerPass() {
                  @Override
                  public void process(Node externs, Node root) {
                    pass.process(externs, root);
                    compiler.setHasRegExpGlobalReferences(pass.isGlobalRegExpPropertiesUsed());
                  }
                };
              })
          .setFeatureSetForChecks()
          .build();

  /** Checks that references to variables look reasonable. */
  private final PassFactory checkVariableReferencesForTranspileOnly =
      PassFactory.builderForHotSwap()
          .setName(PassNames.CHECK_VARIABLE_REFERENCES)
          .setInternalFactory((compiler) -> new VariableReferenceCheck(compiler, true))
          .setFeatureSetForChecks()
          .build();

  /** Checks that references to variables look reasonable. */
  private final PassFactory checkVariableReferences =
      PassFactory.builderForHotSwap()
          .setName(PassNames.CHECK_VARIABLE_REFERENCES)
          .setInternalFactory(VariableReferenceCheck::new)
          .setFeatureSetForChecks()
          .build();

  private final PassFactory checkSuper =
      PassFactory.builderForHotSwap()
          .setName("checkSuper")
          .setInternalFactory(CheckSuper::new)
          .setFeatureSetForChecks()
          .build();

  /** Clears the typed scope creator and all local typed scopes. */
  private final PassFactory clearTypedScopeCreatorPass =
      PassFactory.builder()
          .setName("clearTypedScopeCreatorPass")
          .setInternalFactory((compiler) -> new ClearTypedScopeCreator())
          .setFeatureSetForChecks()
          .build();

  /** Clears the top typed scope when we're done with it. */
  private final PassFactory clearTopTypedScopePass =
      PassFactory.builder()
          .setName("clearTopTypedScopePass")
          .setInternalFactory((compiler) -> new ClearTopTypedScope())
          .setFeatureSetForChecks()
          .build();

  /** Runs type inference. */
  final PassFactory inferTypes =
      PassFactory.builderForHotSwap()
          .setName(PassNames.INFER_TYPES)
          .setInternalFactory(
              (compiler) ->
                  new HotSwapCompilerPass() {
                    @Override
                    public void process(Node unused, Node srcRoot) {
                      Node globalRoot = srcRoot.getParent();
                      compiler.setTypeCheckingHasRun(true);

                      DefaultPassConfig.this.topScope =
                          this.createInference().inferAllScopes(globalRoot);
                    }

                    @Override
                    public void hotSwapScript(Node scriptRoot, Node originalRoot) {
                      this.createInference()
                          .reuseTopScope(getTopScope())
                          .inferAllScopes(scriptRoot);
                    }

                    /** Create a type inference pass. */
                    private TypeInferencePass createInference() {
                      return new TypeInferencePass(
                          compiler,
                          compiler.getReverseAbstractInterpreter(),
                          getTypedScopeCreator(compiler));
                    }
                  })
          .setFeatureSetForChecks()
          .build();

  private final PassFactory inferJsDocInfo =
      PassFactory.builderForHotSwap()
          .setName("inferJsDocInfo")
          .setInternalFactory(
              (compiler) ->
                  new HotSwapCompilerPass() {
                    @Override
                    public void process(Node externs, Node root) {
                      checkNotNull(topScope);
                      checkNotNull(getTypedScopeCreator());

                      new InferJSDocInfo(compiler).process(externs, root);
                    }

                    @Override
                    public void hotSwapScript(Node scriptRoot, Node originalRoot) {
                      new InferJSDocInfo(compiler).hotSwapScript(scriptRoot, originalRoot);
                    }
                  })
          .setFeatureSetForChecks()
          .build();

  /** Checks type usage */
  private final PassFactory checkTypes =
      PassFactory.builderForHotSwap()
          .setName(PassNames.CHECK_TYPES)
          .setInternalFactory(
              (compiler) ->
                  new HotSwapCompilerPass() {
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
                  })
          .setFeatureSetForChecks()
          .build();

  /**
   * Checks possible execution paths of the program for problems: missing return statements and dead
   * code.
   */
  private final PassFactory checkControlFlow =
      PassFactory.builderForHotSwap()
          .setName("checkControlFlow")
          .setInternalFactory(
              (compiler) -> {
                List<Callback> callbacks = new ArrayList<>();
                if (!options.disables(DiagnosticGroups.CHECK_USELESS_CODE)) {
                  callbacks.add(new CheckUnreachableCode(compiler));
                }
                if (!options.disables(DiagnosticGroups.MISSING_RETURN)) {
                  callbacks.add(new CheckMissingReturn(compiler));
                }
                return combineChecks(compiler, callbacks);
              })
          .setFeatureSetForChecks()
          .build();

  /** Checks access controls. Depends on type-inference. */
  private final PassFactory checkAccessControls =
      PassFactory.builderForHotSwap()
          .setName("checkAccessControls")
          .setInternalFactory(
              (compiler) ->
                  new CheckAccessControls(compiler, options.enforceAccessControlCodingConventions))
          .setFeatureSetForChecks()
          .build();

  /**
   * Runs the single-file linter passes
   *
   * <p>These is NOT the configuration for the standalone Linter binary. New linter passes must also
   * be added to {@link LinterPassConfig} as well as this list.
   */
  private final PassFactory lintChecks =
      PassFactory.builderForHotSwap()
          .setName(PassNames.LINT_CHECKS)
          .setInternalFactory(
              (compiler) -> {
                ImmutableList.Builder<Callback> callbacks =
                    ImmutableList.<Callback>builder()
                        .add(new CheckConstantCaseNames(compiler))
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
              })
          .setFeatureSetForChecks()
          .build();

  private final PassFactory analyzerChecks =
      PassFactory.builderForHotSwap()
          .setName(PassNames.ANALYZER_CHECKS)
          .setInternalFactory(
              (compiler) -> {
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
              })
          .setFeatureSetForChecks()
          .build();

  private final PassFactory checkRequiresAndProvidesSorted =
      PassFactory.builderForHotSwap()
          .setName("checkRequiresAndProvidesSorted")
          .setInternalFactory(
              (compiler) ->
                  combineChecks(
                      compiler,
                      ImmutableList.of(
                          new CheckProvidesSorted(CheckProvidesSorted.Mode.COLLECT_AND_REPORT),
                          new CheckRequiresSorted(CheckRequiresSorted.Mode.COLLECT_AND_REPORT))))
          .setFeatureSetForChecks()
          .build();

  /** Executes the given callbacks with a {@link CombinedCompilerPass}. */
  private static HotSwapCompilerPass combineChecks(AbstractCompiler compiler,
      List<Callback> callbacks) {
    checkArgument(!callbacks.isEmpty());
    return new CombinedCompilerPass(compiler, callbacks);
  }

  /** A compiler pass that clears typed scope creator (and non-global scopes cached there) */
  class ClearTypedScopeCreator implements CompilerPass {
    @Override
    public void process(Node externs, Node root) {
      clearTypedScopeCreator();
    }
  }

  /** A compiler pass that clears the global scope. */
  class ClearTopTypedScope implements CompilerPass {
    @Override
    public void process(Node externs, Node root) {
      clearTopTypedScope();
    }
  }

  /** Checks global name usage. */
  private final PassFactory checkGlobalNames =
      PassFactory.builder()
          .setName("checkGlobalNames")
          .setInternalFactory(
              (compiler) ->
                  new CompilerPass() {
                    @Override
                    public void process(Node externs, Node jsRoot) {
                      // Create a global namespace for analysis by check passes.
                      // Note that this class does all heavy computation lazily,
                      // so it's OK to create it here.
                      namespaceForChecks = new GlobalNamespace(compiler, externs, jsRoot);
                      new CheckGlobalNames(compiler, options.checkGlobalNamesLevel)
                          .injectNamespace(namespaceForChecks)
                          .process(externs, jsRoot);
                    }
                  })
          .setFeatureSetForChecks()
          .build();

  /** Checks that the code is ES5 strict compliant. */
  private final PassFactory checkStrictMode =
      PassFactory.builder()
          .setName("checkStrictMode")
          .setInternalFactory(StrictModeCheck::new)
          .setFeatureSetForChecks()
          .build();

  /** Process goog.tweak.getTweak() calls. */
  private final PassFactory processTweaks =
      PassFactory.builder()
          .setName("processTweaks")
          .setInternalFactory(
              (compiler) ->
                  new CompilerPass() {
                    @Override
                    public void process(Node externs, Node jsRoot) {
                      new ProcessTweaks(
                              compiler,
                              options.getTweakProcessing().shouldStrip(),
                              options.getTweakReplacements())
                          .process(externs, jsRoot);
                    }
                  })
          .setFeatureSetForChecks()
          .build();

  /** Override @define-annotated constants. */
  private final PassFactory processDefines =
      PassFactory.builder()
          .setName("processDefines")
          .setInternalFactory(
              (compiler) ->
                  new ProcessDefines.Builder(compiler)
                      .putReplacements(compiler.getDefaultDefineValues())
                      .putReplacements(getAdditionalReplacements(options))
                      .putReplacements(options.getDefineReplacements())
                      .checksOnly(options.checksOnly)
                      .injectNamespace(() -> namespaceForChecks)
                      .build())
          .setFeatureSetForChecks()
          .build();

  /**
   * Strips code for smaller compiled code. This is useful for removing debug statements to prevent
   * leaking them publicly.
   */
  private final PassFactory stripCode =
      PassFactory.builder()
          .setName("stripCode")
          .setInternalFactory(
              (compiler) ->
                  new CompilerPass() {
                    @Override
                    public void process(Node externs, Node jsRoot) {
                      CompilerOptions options = compiler.getOptions();
                      StripCode pass =
                          new StripCode(
                              compiler,
                              options.stripTypes,
                              options.stripNameSuffixes,
                              options.stripTypePrefixes,
                              options.stripNamePrefixes);
                      if (options.getTweakProcessing().shouldStrip()) {
                        pass.enableTweakStripping();
                      }
                      pass.process(externs, jsRoot);
                    }
                  })
          .
          // TODO(johnlenz): StripCode may be fooled by some newer features, like destructuring,
          // an).build();
          setFeatureSetForOptimizations()
          .build();

  /** Release references to data that is only needed during checks. */
  final PassFactory garbageCollectChecks =
      PassFactory.builderForHotSwap()
          .setName("garbageCollectChecks")
          .setInternalFactory(
              (compiler) ->
                  new HotSwapCompilerPass() {
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
                  })
          .setFeatureSet(FeatureSet.latest())
          .build();

  /** Checks that all constants are not modified */
  private final PassFactory checkConsts =
      PassFactory.builder()
          .setName("checkConsts")
          .setInternalFactory(
              (compiler) -> new ConstCheck(compiler, compiler.getModuleMetadataMap()))
          .setFeatureSetForChecks()
          .build();

  /** Checks that the arguments are constants */
  private final PassFactory checkConstParams =
      PassFactory.builder()
          .setName(PassNames.CHECK_CONST_PARAMS)
          .setInternalFactory(ConstParamCheck::new)
          .setFeatureSetForChecks()
          .build();

  /** Inserts run-time type assertions for debugging. */
  private final PassFactory runtimeTypeCheck =
      PassFactory.builder()
          .setName(PassNames.RUNTIME_TYPE_CHECK)
          .setInternalFactory(
              (compiler) -> new RuntimeTypeCheck(compiler, options.runtimeTypeCheckLogFunction))
          // TODO(bradfordcsmith): Drop support for this pass.
          // It's never been updated to handle ES6+ code, because it isn't worth the effort.
          .setFeatureSet(ES5)
          .build();

  /** Generates unique ids. */
  private final PassFactory replaceIdGenerators =
      PassFactory.builder()
          .setName(PassNames.REPLACE_ID_GENERATORS)
          .setInternalFactory(
              (compiler) ->
                  new CompilerPass() {
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
                  })
          .setFeatureSetForChecks()
          .build();

  /** Replace strings. */
  private final PassFactory replaceStrings =
      PassFactory.builder()
          .setName("replaceStrings")
          .setInternalFactory(
              (compiler) ->
                  new CompilerPass() {
                    @Override
                    public void process(Node externs, Node root) {
                      ReplaceStrings pass =
                          new ReplaceStrings(
                              compiler,
                              options.replaceStringsPlaceholderToken,
                              options.replaceStringsFunctionDescriptions,
                              options.replaceStringsReservedStrings,
                              options.replaceStringsInputMap);
                      pass.process(externs, root);
                      compiler.setStringMap(pass.getStringMap());
                    }
                  })
          .setFeatureSetForOptimizations()
          .build();

  /** Optimizes the "arguments" array. */
  private final PassFactory optimizeArgumentsArray =
      PassFactory.builder()
          .setName(PassNames.OPTIMIZE_ARGUMENTS_ARRAY)
          .setInternalFactory(OptimizeArgumentsArray::new)
          .setFeatureSetForOptimizations()
          .build();

  /** Remove variables set to goog.abstractMethod. */
  private final PassFactory closureCodeRemoval =
      PassFactory.builder()
          .setName("closureCodeRemoval")
          .setInternalFactory(
              (compiler) ->
                  new ClosureCodeRemoval(
                      compiler, options.removeAbstractMethods, options.removeClosureAsserts))
          .setFeatureSetForOptimizations()
          .build();

  /** Special case optimizations for closure functions. */
  private final PassFactory closureOptimizePrimitives =
      PassFactory.builder()
          .setName("closureOptimizePrimitives")
          .setInternalFactory(
              (compiler) ->
                  new ClosureOptimizePrimitives(
                      compiler,
                      compiler.getOptions().propertyRenaming == PropertyRenamingPolicy.ALL_UNQUOTED,
                      compiler.getOptions().getOutputFeatureSet().contains(ES6)))
          .setFeatureSetForOptimizations()
          .build();

  /** Puts global symbols into a single object. */
  private final PassFactory rescopeGlobalSymbols =
      PassFactory.builder()
          .setName("rescopeGlobalSymbols")
          .setInternalFactory(
              (compiler) ->
                  new RescopeGlobalSymbols(
                      compiler,
                      options.renamePrefixNamespace,
                      options.renamePrefixNamespaceAssumeCrossChunkNames))
          .setFeatureSetForOptimizations()
          .build();

  /** Collapses names in the global scope. */
  private final PassFactory collapseProperties =
      PassFactory.builder()
          .setName(PassNames.COLLAPSE_PROPERTIES)
          .setInternalFactory(
              (compiler) -> new CollapseProperties(compiler, options.getPropertyCollapseLevel()))
          .setFeatureSetForOptimizations()
          .build();

  /** Rewrite properties as variables. */
  private final PassFactory collapseObjectLiterals =
      PassFactory.builder()
          .setName(PassNames.COLLAPSE_OBJECT_LITERALS)
          .setRunInFixedPointLoop(true)
          .setInternalFactory(
              (compiler) -> new InlineObjectLiterals(compiler, compiler.getUniqueNameIdSupplier()))
          .setFeatureSetForOptimizations()
          .build();

  /** Disambiguate property names based on type information. */
  private final PassFactory disambiguateProperties =
      PassFactory.builder()
          .setName(PassNames.DISAMBIGUATE_PROPERTIES)
          .setInternalFactory(
              (compiler) ->
                  new DisambiguateProperties(compiler, options.propertyInvalidationErrors))
          .setFeatureSetForOptimizations()
          .build();

  /** Disambiguate property names based on type information. */
  private final PassFactory disambiguateProperties2 =
      PassFactory.builder()
          .setName(PassNames.DISAMBIGUATE_PROPERTIES)
          .setInternalFactory(
              (compiler) ->
                  new DisambiguateProperties2(
                      compiler, ImmutableMap.copyOf(options.propertyInvalidationErrors)))
          .setFeatureSetForOptimizations()
          .build();

  /** Rewrite instance methods as static methods, to make them easier to inline. */
  private final PassFactory devirtualizeMethods =
      PassFactory.builder()
          .setName(PassNames.DEVIRTUALIZE_METHODS)
          .setInternalFactory(
              (compiler) ->
                  OptimizeCalls.builder()
                      .setCompiler(compiler)
                      .setConsiderExterns(false)
                      .addPass(new DevirtualizeMethods(compiler))
                      .build())
          .setFeatureSetForOptimizations()
          .build();

  /**
   * Optimizes unused function arguments, unused return values, and inlines constant parameters.
   * Also runs RemoveUnusedCode.
   */
  private final PassFactory optimizeCalls =
      PassFactory.builder()
          .setName(PassNames.OPTIMIZE_CALLS)
          .setRunInFixedPointLoop(true)
          .setInternalFactory(
              (compiler) ->
                  OptimizeCalls.builder()
                      .setCompiler(compiler)
                      .setConsiderExterns(false)
                      // Remove unused return values.
                      .addPass(new OptimizeReturns(compiler))
                      // Remove all parameters that are constants or unused.
                      .addPass(new OptimizeParameters(compiler))
                      .build())
          .setFeatureSetForOptimizations()
          .build();

  /** Look for function calls that are pure, and annotate them that way. */
  private final PassFactory markPureFunctions =
      PassFactory.builder()
          .setName("markPureFunctions")
          .setInternalFactory(PureFunctionIdentifier.Driver::new)
          .setFeatureSetForOptimizations()
          .build();

  /** Inlines variables heuristically. */
  private final PassFactory inlineVariables =
      PassFactory.builder()
          .setName(PassNames.INLINE_VARIABLES)
          .setRunInFixedPointLoop(true)
          .setInternalFactory(
              (compiler) -> {
                InlineVariables.Mode mode;
                if (options.inlineVariables) {
                  mode = InlineVariables.Mode.ALL;
                } else if (options.inlineLocalVariables) {
                  mode = InlineVariables.Mode.LOCALS_ONLY;
                } else {
                  throw new IllegalStateException("No variable inlining option set.");
                }
                return new InlineVariables(compiler, mode, true);
              })
          .setFeatureSetForOptimizations()
          .build();

  /** Inlines variables that are marked as constants. */
  private final PassFactory inlineConstants =
      PassFactory.builder()
          .setName("inlineConstants")
          .setRunInFixedPointLoop(true)
          .setInternalFactory(
              (compiler) ->
                  new InlineVariables(compiler, InlineVariables.Mode.CONSTANTS_ONLY, true))
          .setFeatureSetForOptimizations()
          .build();

  /** Use data flow analysis to remove dead branches. */
  private final PassFactory removeUnreachableCode =
      PassFactory.builder()
          .setName(PassNames.REMOVE_UNREACHABLE_CODE)
          .setRunInFixedPointLoop(true)
          .setInternalFactory(UnreachableCodeElimination::new)
          .setFeatureSetForOptimizations()
          .build();

  /** Inlines simple methods, like getters */
  private final PassFactory inlineSimpleMethods =
      PassFactory.builder()
          .setName("inlineSimpleMethods")
          .setRunInFixedPointLoop(true)
          .setInternalFactory(InlineSimpleMethods::new)
          .setFeatureSetForOptimizations()
          .build();

  /** Kills dead assignments. */
  private final PassFactory deadAssignmentsElimination =
      PassFactory.builder()
          .setName(PassNames.DEAD_ASSIGNMENT_ELIMINATION)
          .setRunInFixedPointLoop(true)
          .setInternalFactory(DeadAssignmentsElimination::new)
          .setFeatureSetForOptimizations()
          .build();

  /** Kills dead property assignments. */
  private final PassFactory deadPropertyAssignmentElimination =
      PassFactory.builder()
          .setName("deadPropertyAssignmentElimination")
          .setRunInFixedPointLoop(true)
          .setInternalFactory(DeadPropertyAssignmentElimination::new)
          .setFeatureSetForOptimizations()
          .build();

  /** Inlines function calls. */
  private final PassFactory inlineFunctions =
      PassFactory.builder()
          .setName(PassNames.INLINE_FUNCTIONS)
          .setRunInFixedPointLoop(true)
          .setInternalFactory(
              (compiler) ->
                  new InlineFunctions(
                      compiler,
                      compiler.getUniqueNameIdSupplier(),
                      options.getInlineFunctionsLevel(),
                      options.assumeStrictThis() || options.expectStrictModeInput(),
                      options.assumeClosuresOnlyCaptureReferences,
                      options.maxFunctionSizeAfterInlining))
          .setFeatureSetForOptimizations()
          .build();

  /** Inlines constant properties. */
  private final PassFactory inlineProperties =
      PassFactory.builder()
          .setName(PassNames.INLINE_PROPERTIES)
          .setRunInFixedPointLoop(true)
          .setInternalFactory(InlineProperties::new)
          .setFeatureSetForOptimizations()
          .build();

  /** Isolates injected polyfills & references from the global scope */
  private final PassFactory isolatePolyfills =
      PassFactory.builder()
          .setName("IsolatePolyfills")
          .setInternalFactory(IsolatePolyfills::new)
          .setFeatureSetForOptimizations()
          .build();

  private final PassFactory removeUnusedCode =
      PassFactory.builder()
          .setName(PassNames.REMOVE_UNUSED_CODE)
          .setRunInFixedPointLoop(true)
          .setInternalFactory(
              (compiler) -> {
                boolean preserveAnonymousFunctionNames =
                    options.anonymousFunctionNaming != AnonymousFunctionNamingPolicy.OFF;
                return new RemoveUnusedCode.Builder(compiler)
                    .removeLocalVars(options.removeUnusedLocalVars)
                    .removeGlobals(options.removeUnusedVars)
                    .preserveFunctionExpressionNames(preserveAnonymousFunctionNames)
                    .removeUnusedPrototypeProperties(options.removeUnusedPrototypeProperties)
                    .allowRemovalOfExternProperties(
                        options.removeUnusedPrototypePropertiesInExterns)
                    .removeUnusedThisProperties(options.isRemoveUnusedClassProperties())
                    .removeUnusedObjectDefinePropertiesDefinitions(
                        options.isRemoveUnusedClassProperties())
                    // If we are forcing injection of some library code, don't remove polyfills.
                    // Otherwise, we might end up removing polyfills the user specifically asked to
                    // include.
                    .removeUnusedPolyfills(options.forceLibraryInjection.isEmpty())
                    .assumeGettersArePure(options.getAssumeGettersArePure())
                    .build();
              })
          .setFeatureSetForOptimizations()
          .build();

  private final PassFactory removeUnusedCodeOnce =
      removeUnusedCode.toBuilder().setRunInFixedPointLoop(false).build();

  /** Move global symbols to a deeper common module */
  private final PassFactory crossModuleCodeMotion =
      PassFactory.builder()
          .setName(PassNames.CROSS_CHUNK_CODE_MOTION)
          .setRunInFixedPointLoop(true)
          .setInternalFactory(
              (compiler) ->
                  new CrossChunkCodeMotion(
                      compiler,
                      compiler.getModuleGraph(),
                      options.parentChunkCanSeeSymbolsDeclaredInChildren))
          .setFeatureSetForOptimizations()
          .build();

  /** Move methods to a deeper common module */
  private final PassFactory crossModuleMethodMotion =
      PassFactory.builder()
          .setName(PassNames.CROSS_CHUNK_METHOD_MOTION)
          .setRunInFixedPointLoop(true)
          .setInternalFactory(
              (compiler) ->
                  new CrossChunkMethodMotion(
                      compiler,
                      compiler.getCrossModuleIdGenerator(),
                      // Only move properties in externs if we're not treating
                      // them as exports.
                      options.removeUnusedPrototypePropertiesInExterns,
                      options.crossChunkCodeMotionNoStubMethods))
          .setFeatureSetForOptimizations()
          .build();

  /** A data-flow based variable inliner. */
  private final PassFactory flowSensitiveInlineVariables =
      PassFactory.builder()
          .setName(PassNames.FLOW_SENSITIVE_INLINE_VARIABLES)
          .setInternalFactory(FlowSensitiveInlineVariables::new)
          .setFeatureSetForOptimizations()
          .build();

  /** Uses register-allocation algorithms to use fewer variables. */
  private final PassFactory coalesceVariableNames =
      PassFactory.builder()
          .setName(PassNames.COALESCE_VARIABLE_NAMES)
          .setInternalFactory(
              (compiler) -> new CoalesceVariableNames(compiler, options.generatePseudoNames))
          .setFeatureSetForOptimizations()
          .build();

  /** Some simple, local collapses (e.g., {@code var x; var y;} becomes {@code var x,y;}. */
  private final PassFactory exploitAssign =
      PassFactory.builder()
          .setName(PassNames.EXPLOIT_ASSIGN)
          .setInternalFactory(
              (compiler) ->
                  new PeepholeOptimizationsPass(
                      compiler, PassNames.EXPLOIT_ASSIGN, new ExploitAssigns()))
          .setFeatureSetForOptimizations()
          .build();

  /** Some simple, local collapses (e.g., {@code var x; var y;} becomes {@code var x,y;}. */
  private final PassFactory collapseVariableDeclarations =
      PassFactory.builder()
          .setName(PassNames.COLLAPSE_VARIABLE_DECLARATIONS)
          .setInternalFactory(CollapseVariableDeclarations::new)
          .setFeatureSetForOptimizations()
          .build();

  /** Extracts common sub-expressions. */
  private final PassFactory extractPrototypeMemberDeclarations =
      PassFactory.builder()
          .setName(PassNames.EXTRACT_PROTOTYPE_MEMBER_DECLARATIONS)
          .setInternalFactory(
              (compiler) -> {
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
              })
          .setFeatureSetForOptimizations()
          .build();

  /** Rewrites common function definitions to be more compact. */
  private final PassFactory rewriteFunctionExpressions =
      PassFactory.builder()
          .setName(PassNames.REWRITE_FUNCTION_EXPRESSIONS)
          .setInternalFactory(FunctionRewriter::new)
          .setFeatureSetForOptimizations()
          .build();

  /** Collapses functions to not use the VAR keyword. */
  private final PassFactory collapseAnonymousFunctions =
      PassFactory.builder()
          .setName(PassNames.COLLAPSE_ANONYMOUS_FUNCTIONS)
          .setInternalFactory(CollapseAnonymousFunctions::new)
          .setFeatureSetForOptimizations()
          .build();

  /** Moves function declarations to the top, to simulate actual hoisting. */
  private final PassFactory moveFunctionDeclarations =
      PassFactory.builder()
          .setName(PassNames.MOVE_FUNCTION_DECLARATIONS)
          .setInternalFactory(MoveFunctionDeclarations::new)
          .setFeatureSetForOptimizations()
          .build();

  private final PassFactory nameUnmappedAnonymousFunctions =
      PassFactory.builder()
          .setName(PassNames.NAME_ANONYMOUS_FUNCTIONS)
          .setInternalFactory(NameAnonymousFunctions::new)
          .setFeatureSetForOptimizations()
          .build();

  private final PassFactory nameMappedAnonymousFunctions =
      PassFactory.builder()
          .setName(PassNames.NAME_ANONYMOUS_FUNCTIONS)
          .setInternalFactory(
              (compiler) ->
                  new CompilerPass() {
                    @Override
                    public void process(Node externs, Node root) {
                      NameAnonymousFunctionsMapped naf =
                          new NameAnonymousFunctionsMapped(
                              compiler, options.inputAnonymousFunctionNamingMap);
                      naf.process(externs, root);
                      compiler.setAnonymousFunctionNameMap(naf.getFunctionMap());
                    }
                  })
          .setFeatureSetForOptimizations()
          .build();

  /** Alias string literals with global variables, to avoid creating lots of transient objects. */
  private final PassFactory aliasStrings =
      PassFactory.builder()
          .setName("aliasStrings")
          .setInternalFactory(
              (compiler) ->
                  new AliasStrings(
                      compiler,
                      compiler.getModuleGraph(),
                      options.aliasAllStrings ? null : options.aliasableStrings,
                      options.aliasStringsBlacklist,
                      options.outputJsStringUsage))
          .setFeatureSetForOptimizations()
          .build();

  /**
   * Renames properties so that the two properties that never appear on the same object get the same
   * name.
   */
  private final PassFactory ambiguateProperties =
      PassFactory.builder()
          .setName(PassNames.AMBIGUATE_PROPERTIES)
          .setInternalFactory(
              (compiler) ->
                  new AmbiguateProperties(
                      compiler,
                      options.getPropertyReservedNamingFirstChars(),
                      options.getPropertyReservedNamingNonFirstChars()))
          .setFeatureSetForOptimizations()
          .build();

  /** Mark the point at which the normalized AST assumptions no longer hold. */
  private final PassFactory markUnnormalized =
      PassFactory.builder()
          .setName("markUnnormalized")
          .setInternalFactory(
              (compiler) ->
                  new CompilerPass() {
                    @Override
                    public void process(Node externs, Node root) {
                      compiler.setLifeCycleStage(LifeCycleStage.RAW);
                    }
                  })
          .setFeatureSetForOptimizations()
          .build();

  private final PassFactory normalize =
      PassFactory.builder()
          .setName(PassNames.NORMALIZE)
          .setInternalFactory((compiler) -> new Normalize(compiler, false))
          .setFeatureSetForOptimizations()
          .build();

  private final PassFactory externExports =
      PassFactory.builder()
          .setName(PassNames.EXTERN_EXPORTS)
          .setInternalFactory(ExternExportsPass::new)
          .setFeatureSetForOptimizations()
          .build();

  /** Denormalize the AST for code generation. */
  private final PassFactory denormalize =
      PassFactory.builder()
          .setName("denormalize")
          .setInternalFactory(Denormalize::new)
          .setFeatureSetForOptimizations()
          .build();

  /** Inverting name normalization. */
  private final PassFactory invertContextualRenaming =
      PassFactory.builder()
          .setName("invertContextualRenaming")
          .setInternalFactory(MakeDeclaredNamesUnique::getContextualRenameInverter)
          .setFeatureSetForOptimizations()
          .build();

  /** Renames properties. */
  private final PassFactory renameProperties =
      PassFactory.builder()
          .setName("renameProperties")
          .setInternalFactory(
              (compiler) -> {
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
              })
          .setFeatureSetForOptimizations()
          .build();

  /** Renames variables. */
  private final PassFactory renameVars =
      PassFactory.builder()
          .setName("renameVars")
          .setInternalFactory(
              (compiler) -> {
                final VariableMap prevVariableMap = options.inputVariableMap;
                return new CompilerPass() {
                  @Override
                  public void process(Node externs, Node root) {
                    compiler.setVariableMap(
                        runVariableRenaming(compiler, prevVariableMap, externs, root));
                  }
                };
              })
          .setFeatureSetForOptimizations()
          .build();

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
      PassFactory.builder()
          .setName("renameLabels")
          .setInternalFactory(RenameLabels::new)
          .setFeatureSetForOptimizations()
          .build();

  /** Convert bracket access to dot access */
  private final PassFactory convertToDottedProperties =
      PassFactory.builder()
          .setName(PassNames.CONVERT_TO_DOTTED_PROPERTIES)
          .setInternalFactory(ConvertToDottedProperties::new)
          .setFeatureSetForOptimizations()
          .build();

  private final PassFactory checkAstValidity =
      PassFactory.builder()
          .setName("checkAstValidity")
          .setInternalFactory(AstValidator::new)
          .setFeatureSetForChecks()
          .build();

  /** Checks that all variables are defined. */
  private final PassFactory varCheckValidity =
      PassFactory.builder()
          .setName("varCheckValidity")
          .setInternalFactory((compiler) -> new VarCheck(compiler, true))
          .setFeatureSetForChecks()
          .build();

  private final PassFactory instrumentForCodeCoverage =
      PassFactory.builder()
          .setName("instrumentForCodeCoverage")
          .setInternalFactory(
              (compiler) ->
                  // TODO(johnlenz): make global instrumentation an option
                  new CoverageInstrumentationPass(
                      compiler,
                      CoverageReach.CONDITIONAL,
                      options.getInstrumentForCoverageOption()))
          .setFeatureSetForOptimizations()
          .build();

  /** Extern property names gathering pass. */
  private final PassFactory gatherExternProperties =
      PassFactory.builder()
          .setName("gatherExternProperties")
          .setInternalFactory(GatherExternProperties::new)
          .setFeatureSetForChecks()
          .build();

  /**
   * Runs custom passes that are designated to run at a particular time.
   *
   * <p>TODO(nickreid): Deprecate this API
   */
  private PassFactory getCustomPasses(final CustomPassExecutionTime executionTime) {
    return PassFactory.builder()
        .setName("runCustomPasses")
        .setInternalFactory((compiler) -> runInSerial(options.customPasses.get(executionTime)))
        .setFeatureSetForOptimizations()
        .build();
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
  private final PassFactory polymerPass =
      PassFactory.builderForHotSwap()
          .setName("polymerPass")
          .setInternalFactory(
              (compiler) ->
                  new PolymerPass(
                      compiler,
                      compiler.getOptions().polymerVersion,
                      compiler.getOptions().polymerExportPolicy,
                      compiler.getOptions().propertyRenaming
                          == PropertyRenamingPolicy.ALL_UNQUOTED))
          .setFeatureSetForChecks()
          .build();

  private final PassFactory chromePass =
      PassFactory.builder()
          .setName("chromePass")
          .setInternalFactory(ChromePass::new)
          .setFeatureSetForChecks()
          .build();

  /** Rewrites the super accessors calls to support Dart Dev Compiler output. */
  private final PassFactory dartSuperAccessorsPass =
      PassFactory.builderForHotSwap()
          .setName("dartSuperAccessorsPass")
          .setInternalFactory(DartSuperAccessorsPass::new)
          .setFeatureSetForChecks()
          .build();

  /** Rewrites J2CL constructs to be more optimizable. */
  private final PassFactory j2clConstantHoisterPass =
      PassFactory.builder()
          .setName("j2clConstantHoisterPass")
          .setRunInFixedPointLoop(true)
          .setInternalFactory(J2clConstantHoisterPass::new)
          .setFeatureSetForOptimizations()
          .build();

  /** Optimizes J2CL clinit methods. */
  private final PassFactory j2clClinitPass =
      PassFactory.builder()
          .setName("j2clClinitPass")
          .setRunInFixedPointLoop(true)
          .setInternalFactory(
              (compiler) -> {
                List<Node> changedScopeNodes =
                    compiler.getChangedScopeNodesForPass("j2clClinitPass");
                return new J2clClinitPrunerPass(compiler, changedScopeNodes);
              })
          .setFeatureSetForOptimizations()
          .build();

  /** Rewrites J2CL constructs to be more optimizable. */
  private final PassFactory j2clPropertyInlinerPass =
      PassFactory.builder()
          .setName("j2clES6Pass")
          .setInternalFactory(J2clPropertyInlinerPass::new)
          .setFeatureSetForOptimizations()
          .build();

  /** Rewrites J2CL constructs to be more optimizable. */
  private final PassFactory j2clPass =
      PassFactory.builder()
          .setName("j2clPass")
          .setInternalFactory(J2clPass::new)
          .setFeatureSetForChecks()
          .build();

  /** Rewrites J2CL constructs to be more optimizable. */
  private final PassFactory j2clUtilGetDefineRewriterPass =
      PassFactory.builder()
          .setName("j2clUtilGetDefineRewriterPass")
          .setInternalFactory(J2clUtilGetDefineRewriterPass::new)
          .setFeatureSetForOptimizations()
          .build();

  private final PassFactory j2clAssertRemovalPass =
      PassFactory.builder()
          .setName("j2clAssertRemovalPass")
          .setInternalFactory(J2clAssertRemovalPass::new)
          .setFeatureSetForOptimizations()
          .build();

  private final PassFactory j2clSourceFileChecker =
      PassFactory.builder()
          .setName("j2clSourceFileChecker")
          .setInternalFactory(J2clSourceFileChecker::new)
          .setFeatureSetForChecks()
          .build();

  private final PassFactory j2clChecksPass =
      PassFactory.builder()
          .setName("j2clChecksPass")
          .setInternalFactory(J2clChecksPass::new)
          .setFeatureSetForChecks()
          .build();

  private final PassFactory checkConformance =
      PassFactory.builder()
          .setName(PassNames.CHECK_CONFORMANCE)
          .setInternalFactory(
              (compiler) ->
                  new CheckConformance(
                      compiler, ImmutableList.copyOf(options.getConformanceConfigs())))
          .setFeatureSetForChecks()
          .build();

  /** Remove types */
  private final PassFactory removeTypes =
      PassFactory.builder()
          .setName("removeTypes")
          .setInternalFactory(RemoveTypes::new)
          .setFeatureSetForOptimizations()
          .build();

  /** Optimizations that output ES6 features. */
  private final PassFactory optimizeToEs6 =
      PassFactory.builder()
          .setName("optimizeToEs6")
          .setInternalFactory(SubstituteEs6Syntax::new)
          .setFeatureSetForOptimizations()
          .build();

  /** Rewrites goog.module in whitespace only mode */
  private final PassFactory whitespaceWrapGoogModules =
      PassFactory.builderForHotSwap()
          .setName("whitespaceWrapGoogModules")
          .setInternalFactory(WhitespaceWrapGoogModules::new)
          .setFeatureSetForChecks()
          .build();

  private final PassFactory rewriteCommonJsModules =
      PassFactory.builder()
          .setName(PassNames.REWRITE_COMMON_JS_MODULES)
          .setInternalFactory(ProcessCommonJSModules::new)
          .setFeatureSetForChecks()
          .build();

  private final PassFactory rewriteScriptsToEs6Modules =
      PassFactory.builder()
          .setName(PassNames.REWRITE_SCRIPTS_TO_ES6_MODULES)
          .setInternalFactory(Es6RewriteScriptsToModules::new)
          .setFeatureSetForChecks()
          .build();

  private final PassFactory gatherModuleMetadataPass =
      PassFactory.builderForHotSwap()
          .setName(PassNames.GATHER_MODULE_METADATA)
          .setInternalFactory(
              (compiler) -> {
                // Force creation of the synthetic input so that we create metadata for it
                compiler.getSynthesizedExternsInput();
                return new GatherModuleMetadata(
                    compiler, options.processCommonJSModules, options.moduleResolutionMode);
              })
          .setFeatureSetForChecks()
          .build();

  private final PassFactory createModuleMapPass =
      PassFactory.builderForHotSwap()
          .setName(PassNames.CREATE_MODULE_MAP)
          .setInternalFactory(
              (compiler) -> new ModuleMapCreator(compiler, compiler.getModuleMetadataMap()))
          // does not look at AST
          .setFeatureSet(FeatureSet.all())
          .build();

  private final PassFactory gatherGettersAndSetters =
      PassFactory.builder()
          .setName(PassNames.GATHER_GETTERS_AND_SETTERS)
          .setInternalFactory(GatherGetterAndSetterProperties::new)
          .setFeatureSetForChecks()
          .build();

  // this pass is just adding script without looking at the AST so it should accept all features
  private final PassFactory addSyntheticScript =
      PassFactory.builder()
          .setName("ADD_SYNTHETIC_SCRIPT")
          .setFeatureSet(FeatureSet.all())
          .setInternalFactory(
              (compiler) -> (externs, js) -> compiler.initializeSyntheticCodeInput())
          .build();

  private final PassFactory removeSyntheticScript =
      PassFactory.builder()
          .setName("REMOVE_SYNTHETIC_SCRIPT")
          .setFeatureSet(FeatureSet.all())
          .setInternalFactory((compiler) -> (externs, js) -> compiler.removeSyntheticCodeInput())
          .build();

  private final PassFactory mergeSyntheticScript =
      PassFactory.builder()
          .setName("MERGE_SYNTHETIC_SCRIPT")
          .setFeatureSet(FeatureSet.all())
          .setInternalFactory((compiler) -> (externs, js) -> compiler.mergeSyntheticCodeInput())
          .build();
}
