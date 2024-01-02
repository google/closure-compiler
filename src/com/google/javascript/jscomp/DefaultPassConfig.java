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
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.PassFactory.createEmptyPass;
import static com.google.javascript.jscomp.parsing.parser.FeatureSet.ES2015;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.jscomp.CompilerOptions.AliasStringsMode;
import com.google.javascript.jscomp.CompilerOptions.ChunkOutputType;
import com.google.javascript.jscomp.CompilerOptions.ExtractPrototypeMemberDeclarationsMode;
import com.google.javascript.jscomp.CompilerOptions.InstrumentOption;
import com.google.javascript.jscomp.CompilerOptions.PropertyCollapseLevel;
import com.google.javascript.jscomp.CompilerOptions.Reach;
import com.google.javascript.jscomp.ExtractPrototypeMemberDeclarations.Pattern;
import com.google.javascript.jscomp.LocaleDataPasses.ProtectGoogLocale;
import com.google.javascript.jscomp.ScopedAliases.InvalidModuleGetHandling;
import com.google.javascript.jscomp.disambiguate.AmbiguateProperties;
import com.google.javascript.jscomp.disambiguate.DisambiguateProperties;
import com.google.javascript.jscomp.ijs.ConvertToTypedInterface;
import com.google.javascript.jscomp.instrumentation.CoverageInstrumentationPass;
import com.google.javascript.jscomp.instrumentation.CoverageInstrumentationPass.CoverageReach;
import com.google.javascript.jscomp.lint.CheckArrayWithGoogObject;
import com.google.javascript.jscomp.lint.CheckConstPrivateProperties;
import com.google.javascript.jscomp.lint.CheckConstantCaseNames;
import com.google.javascript.jscomp.lint.CheckDefaultExportOfGoogModule;
import com.google.javascript.jscomp.lint.CheckDuplicateCase;
import com.google.javascript.jscomp.lint.CheckEmptyStatements;
import com.google.javascript.jscomp.lint.CheckEnums;
import com.google.javascript.jscomp.lint.CheckEs6ModuleFileStructure;
import com.google.javascript.jscomp.lint.CheckEs6Modules;
import com.google.javascript.jscomp.lint.CheckExtraRequires;
import com.google.javascript.jscomp.lint.CheckGoogModuleTypeScriptName;
import com.google.javascript.jscomp.lint.CheckInterfaces;
import com.google.javascript.jscomp.lint.CheckJSDocStyle;
import com.google.javascript.jscomp.lint.CheckMissingSemicolon;
import com.google.javascript.jscomp.lint.CheckNestedNames;
import com.google.javascript.jscomp.lint.CheckNoMutatedEs6Exports;
import com.google.javascript.jscomp.lint.CheckNullabilityModifiers;
import com.google.javascript.jscomp.lint.CheckPrimitiveAsObject;
import com.google.javascript.jscomp.lint.CheckPrototypeProperties;
import com.google.javascript.jscomp.lint.CheckProvidesSorted;
import com.google.javascript.jscomp.lint.CheckRequiresSorted;
import com.google.javascript.jscomp.lint.CheckUnusedLabels;
import com.google.javascript.jscomp.lint.CheckUnusedPrivateProperties;
import com.google.javascript.jscomp.lint.CheckUselessBlocks;
import com.google.javascript.jscomp.lint.CheckVar;
import com.google.javascript.jscomp.modules.ModuleMapCreator;
import com.google.javascript.jscomp.parsing.ParserRunner;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.jscomp.serialization.ConvertTypesToColors;
import com.google.javascript.jscomp.serialization.SerializationOptions;
import com.google.javascript.jscomp.serialization.SerializeTypedAstPass;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.nullness.Nullable;

/**
 * Pass factories and meta-data for native JSCompiler passes.
 *
 * <p>NOTE(johnlenz): this needs some non-trivial refactoring. The pass config should use as little
 * state as possible. The recommended way for a pass to leave behind some state for a subsequent
 * pass is through the compiler object. Any other state remaining here should only be used when the
 * pass config is creating the list of checks and optimizations, not after passes have started
 * executing.
 *
 * <p>The general goal is for this class to be as minimal as possible. Option validation should
 * occur before this class configures the compiler, business logic should live here (passes should
 * not be inlined, etc).
 */
public final class DefaultPassConfig extends PassConfig {

  /* For the --mark-as-compiled pass */
  private static final String COMPILED_CONSTANT_NAME = "COMPILED";

  /* Constant name for Closure's locale */
  private static final String CLOSURE_LOCALE_CONSTANT_NAME = "goog.LOCALE";

  // Miscellaneous errors.
  private static final java.util.regex.Pattern GLOBAL_SYMBOL_NAMESPACE_PATTERN =
      java.util.regex.Pattern.compile("^[a-zA-Z0-9$_]+$");

  /** A symbol table for registering references that get removed during preprocessing. */
  private final transient PreprocessorSymbolTable.CachedInstanceFactory
      preprocessorSymbolTableFactory = new PreprocessorSymbolTable.CachedInstanceFactory();

  public DefaultPassConfig(CompilerOptions options) {
    super(options);
  }

  @Nullable PreprocessorSymbolTable getPreprocessorSymbolTable() {
    return preprocessorSymbolTableFactory.getInstanceOrNull();
  }

  @Override
  protected PassListBuilder getTranspileOnlyPasses() {
    PassListBuilder passes = new PassListBuilder(options);

    // Certain errors in block-scoped variable declarations will prevent correct transpilation
    passes.maybeAdd(checkVariableReferences);
    passes.maybeAdd(checkVars);

    passes.maybeAdd(gatherModuleMetadataPass);
    passes.maybeAdd(createModuleMapPass);

    if (options.getLanguageIn().toFeatureSet().has(Feature.MODULES)) {
      passes.maybeAdd(rewriteGoogJsImports);
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

    passes.maybeAdd(checkSuper);

    TranspilationPasses.addTranspilationRuntimeLibraries(passes);

    if (options.needsTranspilationFrom(ES2015)) {
      if (options.getRewritePolyfills()) {
        if (options.getIsolatePolyfills()) {
          throw new IllegalStateException(
              "Polyfill isolation cannot be used in transpileOnly mode");
        }
        TranspilationPasses.addRewritePolyfillPass(passes);
      }
    }

    passes.maybeAdd(injectRuntimeLibraries);

    // NOTE: Any new transpiler passes added outside of
    // {@code addEarlyOptimizationTranspilationPasses} also need to update
    // {@code CompilerTestCase.transpileToEs5}
    TranspilationPasses.addEarlyOptimizationTranspilationPasses(passes, options);

    // Passes below this point may rely on normalization and must maintain normalization.
    passes.maybeAdd(normalize);
    TranspilationPasses.addPostNormalizationTranspilationPasses(passes, options);
    // The transpilation passes may rely on normalize making all variables unique,
    // but we're doing only transpilation, so we want to put back the original variable names
    // wherever we can to meet user expectations.
    //
    // Also, if we don't do this, we could end up breaking runtime behavior that depends on specific
    // variable names.
    //
    // The primary concern is function parameter names, because some frameworks, like Angular,
    // do runtime injection of function call arguments based on the function parameter names.
    passes.maybeAdd(invertContextualRenaming);

    passes.assertAllOneTimePasses();
    assertValidOrderForChecks(passes);
    return passes;
  }

  @Override
  protected PassListBuilder getWhitespaceOnlyPasses() {
    PassListBuilder passes = new PassListBuilder(options);

    if (options.getProcessCommonJSModules()) {
      passes.maybeAdd(rewriteCommonJsModules);
    } else if (options.getLanguageIn().toFeatureSet().has(Feature.MODULES)) {
      passes.maybeAdd(rewriteScriptsToEs6Modules);
    }

    if (options.wrapGoogModulesForWhitespaceOnly) {
      passes.maybeAdd(whitespaceWrapGoogModules);
    }
    return passes;
  }

  private void addModuleRewritingPasses(PassListBuilder checks, CompilerOptions options) {
    if (options.getLanguageIn().toFeatureSet().has(Feature.MODULES)) {
      checks.maybeAdd(rewriteGoogJsImports);
      TranspilationPasses.addEs6ModulePass(checks, preprocessorSymbolTableFactory);
    }

    if (options.closurePass) {
      checks.maybeAdd(closureRewriteModule);
    }
  }

  @Override
  protected PassListBuilder getChecks() {
    PassListBuilder checks = new PassListBuilder(options);

    checkState(
        !options.skipNonTranspilationPasses,
        "options.skipNonTranspilationPasses cannot be mixed with PassConfig::getChecks. Call"
            + " PassConfig::getTranspileOnlyPasses instead.");

    if (options.isPropertyRenamingOnlyCompilationMode()) {
      checks.maybeAdd(addSyntheticScript);
      checks.maybeAdd(gatherGettersAndSetters);
      checks.maybeAdd(gatherModuleMetadataPass);
      checks.maybeAdd(createModuleMapPass);
      checks.maybeAdd(declaredGlobalExternsOnWindow);
      checks.maybeAdd(closureRewriteClass);
      checks.maybeAdd(checkSideEffects);
      checks.maybeAdd(angularPass);
      checks.maybeAdd(closureGoogScopeAliases);
      addModuleRewritingPasses(checks, options);
      checks.maybeAdd(closurePrimitives);
      checks.maybeAdd(clearTypedScopeCreatorPass);
      checks.maybeAdd(clearTopTypedScopePass);
      checks.maybeAdd(generateExports);
      checks.maybeAdd(createEmptyPass(PassNames.AFTER_STANDARD_CHECKS));
      checks.maybeAdd(mergeSyntheticScript);
      checks.maybeAdd(gatherExternPropertiesCheck);
      checks.maybeAdd(createEmptyPass(PassNames.BEFORE_SERIALIZATION));
      return checks;
    }

    if (options.shouldGenerateTypedExterns()) {
      checks.maybeAdd(addSyntheticScript);
      checks.maybeAdd(closureGoogScopeAliasesForIjs);
      checks.maybeAdd(closureRewriteClass);
      checks.maybeAdd(generateIjs);
      if (options.wrapGoogModulesForWhitespaceOnly) {
        checks.maybeAdd(whitespaceWrapGoogModules);
      }
      checks.maybeAdd(removeSyntheticScript);
      return checks;
    }

    // Run this pass before any pass tries to inject new runtime libraries
    checks.maybeAdd(addSyntheticScript);

    checks.maybeAdd(gatherGettersAndSetters);

    if (options.getLanguageIn().toFeatureSet().contains(Feature.DYNAMIC_IMPORT)
        && !options.shouldAllowDynamicImport()) {
      checks.maybeAdd(forbidDynamicImportUsage);
    }

    checks.maybeAdd(createEmptyPass("beforeStandardChecks"));

    if (!options.getProcessCommonJSModules()
        && options.getLanguageIn().toFeatureSet().has(Feature.MODULES)) {
      checks.maybeAdd(rewriteScriptsToEs6Modules);
    }

    // Run these passes after promoting scripts to modules, but before rewriting any other modules.
    checks.maybeAdd(gatherModuleMetadataPass);
    checks.maybeAdd(createModuleMapPass);

    if (options.getProcessCommonJSModules()) {
      checks.maybeAdd(rewriteCommonJsModules);
    }

    // Note: ChromePass can rewrite invalid @type annotations into valid ones, so should run before
    // JsDoc checks.
    if (options.isChromePassEnabled()) {
      checks.maybeAdd(chromePass);
    }

    // Verify JsDoc annotations and check ES modules
    checks.maybeAdd(checkJsDocAndEs6Modules);

    checks.maybeAdd(checkTypeImportCodeReferences);

    if (options.enables(DiagnosticGroups.LINT_CHECKS)) {
      checks.maybeAdd(lintChecks);
    }

    if (options.closurePass && options.enables(DiagnosticGroups.LINT_CHECKS)) {
      checks.maybeAdd(checkRequiresAndProvidesSorted);
    }

    if (options.enables(DiagnosticGroups.EXTRA_REQUIRE)) {
      checks.maybeAdd(extraRequires);
    }

    if (options.enables(DiagnosticGroups.MISSING_REQUIRE)) {
      checks.maybeAdd(checkMissingRequires);
    }

    checks.maybeAdd(declaredGlobalExternsOnWindow);

    if (!options.getProcessCommonJSModules()) {
      // TODO(ChadKillingsworth): move CommonJS module rewriting after VarCheck
      checks.maybeAdd(checkVariableReferences);
      checks.maybeAdd(checkVars);
    }

    if (options.closurePass) {
      checks.maybeAdd(checkClosureImports);
    }

    checks.maybeAdd(checkStrictMode);

    if (options.closurePass) {
      checks.maybeAdd(closureCheckModule);
    }

    checks.maybeAdd(checkSuper);

    if (options.closurePass) {
      checks.maybeAdd(closureRewriteClass);
    }

    checks.maybeAdd(checkSideEffects);

    if (options.angularPass) {
      checks.maybeAdd(angularPass);
    }

    if (options.closurePass) {
      checks.maybeAdd(closureGoogScopeAliases);
    }

    // TODO(b/141389184): Move this after the Polymer pass
    if (options.shouldRewriteModulesBeforeTypechecking()) {
      addModuleRewritingPasses(checks, options);
    }

    if (options.closurePass) {
      checks.maybeAdd(closurePrimitives);
    }

    // It's important that the PolymerPass run *after* the ClosurePrimitives and ChromePass rewrites
    // and *before* the suspicious code checks. This is enforced in the assertValidOrder method.
    if (options.polymerVersion != null) {
      checks.maybeAdd(polymerPass);
    }

    if (options.syntheticBlockStartMarker != null) {
      // This pass must run before the first fold constants pass.
      checks.maybeAdd(createSyntheticBlocks);
    }

    if (options.getProcessCommonJSModules()) {
      // TODO(ChadKillingsworth): remove this branch.
      checks.maybeAdd(checkVariableReferences);
      checks.maybeAdd(checkVars);
    }

    if (options.inferConsts) {
      checks.maybeAdd(inferConsts);
    }

    if (options.computeFunctionSideEffects) {
      checks.maybeAdd(checkRegExp);
    }

    // Passes running before this point should expect to see language features up to ES_2017.
    checks.maybeAdd(createEmptyPass(PassNames.BEFORE_PRE_TYPECHECK_TRANSPILATION));

    checks.maybeAdd(createEmptyPass(PassNames.BEFORE_TYPE_CHECKING));

    if (options.checkTypes || options.inferTypes) {
      checks.maybeAdd(inferTypes);
      if (options.checkTypes) {
        checks.maybeAdd(checkTypes);
      } else {
        checks.maybeAdd(inferJsDocInfo);
      }
    }

    // Analyzer checks must be run after typechecking but before module rewriting.
    if (options.enables(DiagnosticGroups.ANALYZER_CHECKS) && options.isTypecheckingEnabled()) {
      checks.maybeAdd(analyzerChecks);
    }

    // We assume that only clients who are going to re-compile, or do in-depth static analysis,
    // will need the typed scope creator after the compile job.
    if (!options.preservesDetailedSourceInfo()) {
      checks.maybeAdd(clearTypedScopeCreatorPass);
    }

    if (options.shouldRewriteModulesAfterTypechecking()) {
      addModuleRewritingPasses(checks, options);
    }

    // Dynamic import rewriting must always occur after type checking
    if (options.shouldAllowDynamicImport()
        && options.getLanguageIn().toFeatureSet().has(Feature.DYNAMIC_IMPORT)) {
      checks.maybeAdd(rewriteDynamicImports);
    }

    // We assume that only clients who are going to re-compile, or do in-depth static analysis,
    // will need the typed scope creator after the compile job.
    if (!options.preservesDetailedSourceInfo()) {
      checks.maybeAdd(clearTopTypedScopePass);
    }

    // CheckSuspiciousCode requires type information, so must run after the type checker.
    if (options.checkSuspiciousCode
        || options.enables(DiagnosticGroups.GLOBAL_THIS)
        || options.enables(DiagnosticGroups.DEBUGGER_STATEMENT_PRESENT)) {
      checks.maybeAdd(suspiciousCode);
    }

    if (options.j2clPassMode.shouldAddJ2clPasses()) {
      checks.maybeAdd(j2clSourceFileChecker);
    }

    if (!options.disables(DiagnosticGroups.CHECK_USELESS_CODE)
        || !options.disables(DiagnosticGroups.MISSING_RETURN)) {
      checks.maybeAdd(checkControlFlow);
    }

    // CheckAccessControls only works if check types is on.
    if (options.isTypecheckingEnabled()
        && (!options.disables(DiagnosticGroups.ACCESS_CONTROLS)
            || options.enables(DiagnosticGroups.CONSTANT_PROPERTY))) {
      checks.maybeAdd(checkAccessControls);
    }

    checks.maybeAdd(checkConsts);

    if (!options.getConformanceConfigs().isEmpty()) {
      checks.maybeAdd(checkConformance);
    }

    if (options.getTweakProcessing().isOn()) {
      checks.maybeAdd(processTweaks);
    }

    checks.maybeAdd(processDefinesCheck);

    if (options.j2clPassMode.shouldAddJ2clPasses()) {
      checks.maybeAdd(j2clChecksPass);
    }

    if (options.shouldRunTypeSummaryChecksLate()) {
      checks.maybeAdd(generateIjs);
    }

    if (options.generateExports) {
      checks.maybeAdd(generateExports);
    }

    checks.maybeAdd(createEmptyPass(PassNames.AFTER_STANDARD_CHECKS));

    if (options.checksOnly) {
      checks.maybeAdd(removeSyntheticScript);
    } else if (!options.checksOnly) {
      checks.maybeAdd(mergeSyntheticScript);
      // At this point all checks have been done.
      if (options.exportTestFunctions) {
        checks.maybeAdd(exportTestFunctions);
      }
    }

    // Create extern exports after the normalize because externExports depends on unique names.
    if (options.getExternExportsPath() != null) {
      checks.maybeAdd(externExports);
    }

    if (!options.checksOnly) {
      checks.maybeAdd(removeWeakSources);
    }

    // Gather property names in externs so they can be queried by the optimizing passes.
    // See b/180424427 for why this runs in stage 1 and not stage 2.
    checks.maybeAdd(gatherExternPropertiesCheck);

    checks.assertAllOneTimePasses();
    assertValidOrderForChecks(checks);

    checks.maybeAdd(createEmptyPass(PassNames.BEFORE_SERIALIZATION));

    if (options.getTypedAstOutputFile() != null) {
      checks.maybeAdd(serializeTypedAst);
    }

    return checks;
  }

  @Override
  protected PassListBuilder getOptimizations() {
    PassListBuilder passes = new PassListBuilder(options);
    if (options.isPropertyRenamingOnlyCompilationMode()) {
      TranspilationPasses.addTranspilationRuntimeLibraries(passes);
      passes.maybeAdd(closureProvidesRequires);
      passes.maybeAdd(processDefinesOptimize);
      TranspilationPasses.addEarlyOptimizationTranspilationPasses(passes, options);
      passes.maybeAdd(normalize);
      TranspilationPasses.addPostNormalizationTranspilationPasses(passes, options);
      passes.maybeAdd(gatherExternPropertiesOptimize);
      passes.maybeAdd(createEmptyPass(PassNames.BEFORE_STANDARD_OPTIMIZATIONS));
      passes.maybeAdd(inlineAndCollapseProperties);
      passes.maybeAdd(closureOptimizePrimitives);
      return passes;
    }

    if (options.skipNonTranspilationPasses) {
      // Reaching this if-condition means the 'getChecks()' phase has been skipped in favor of
      // 'getTranspileOnlyPasses'.
      return passes;
    }

    if (options.getMergedPrecompiledLibraries()) {
      // Weak sources aren't removed at the library level
      passes.maybeAdd(removeWeakSources);

      // it would be safe to always recompute side effects even if not using precompiled libraries
      // (the else case) but it's unecessary so skip it to improve build times.
      passes.maybeAdd(checkRegExpForOptimizations);

      // This runs during getChecks(), so only needs to be run here if using precompiled .typedasts
      if (options.j2clPassMode.shouldAddJ2clPasses()) {
        passes.maybeAdd(j2clSourceFileChecker);
      }
    } else {
      addNonTypedAstNormalizationPasses(passes);
    }

    // Remove synthetic extern declarations of names that are now defined in source
    // This is expected to do nothing when in a monolithic build
    passes.maybeAdd(removeUnnecessarySyntheticExterns);

    if (options.j2clPassMode.shouldAddJ2clPasses()) {
      passes.maybeAdd(j2clPass);
    }

    TranspilationPasses.addTranspilationRuntimeLibraries(passes);

    if (options.rewritePolyfills || options.getIsolatePolyfills()) {
      TranspilationPasses.addRewritePolyfillPass(passes);
    }

    passes.maybeAdd(injectRuntimeLibraries);

    if (options.closurePass) {
      passes.maybeAdd(closureProvidesRequires);
    }

    if (options.shouldRunReplaceMessagesForChrome()) {
      passes.maybeAdd(replaceMessagesForChrome);
    } else if (options.shouldRunReplaceMessagesPass()) {
      if (options.doLateLocalization()) {
        // With late localization we protect the messages from mangling by optimizations now,
        // then actually replace them after optimizations.
        // The purpose of doing this is to separate localization from optimization so we can
        // optimize just once for all locales.
        passes.maybeAdd(getProtectMessagesPass());
      } else {
        // TODO(bradfordcsmith): At the moment we expect the optimized output may be slightly
        // smaller if you replace messages before optimizing, but if we can change that, it would
        // be good to drop this early replacement entirely.
        passes.maybeAdd(getFullReplaceMessagesPass());
      }
    }

    if (options.doLateLocalization()) {
      passes.maybeAdd(protectLocaleData);
    }

    // Replace 'goog.getCssName' before processing defines
    if (options.closurePass && !options.shouldPreserveGoogLibraryPrimitives()) {
      passes.maybeAdd(closureReplaceGetCssName);
    }

    // Defines in code always need to be processed.
    passes.maybeAdd(processDefinesOptimize);

    TranspilationPasses.addEarlyOptimizationTranspilationPasses(passes, options);

    passes.maybeAdd(normalize);

    TranspilationPasses.addPostNormalizationTranspilationPasses(passes, options);

    passes.maybeAdd(gatherGettersAndSetters);

    if (options.j2clPassMode.shouldAddJ2clPasses()) {
      passes.maybeAdd(j2clUtilGetDefineRewriterPass);
    }

    if (options.getInstrumentForCoverageOption() != InstrumentOption.NONE) {
      passes.maybeAdd(instrumentForCodeCoverage);
    }

    passes.maybeAdd(gatherExternPropertiesOptimize);

    passes.maybeAdd(createEmptyPass(PassNames.BEFORE_STANDARD_OPTIMIZATIONS));

    // Optimizes references to the arguments variable.
    if (options.optimizeArgumentsArray) {
      passes.maybeAdd(optimizeArgumentsArray);
    }

    // Abstract method removal works best on minimally modified code, and also
    // only needs to run once.
    if (options.closurePass && (options.removeAbstractMethods || options.removeClosureAsserts)) {
      passes.maybeAdd(closureCodeRemoval);
    }

    if (options.removeJ2clAsserts) {
      passes.maybeAdd(j2clAssertRemovalPass);
    }

    passes.maybeAdd(replaceToggles);
    passes.maybeAdd(inlineAndCollapseProperties);

    if (options.getTweakProcessing().shouldStrip()
        || !options.stripTypes.isEmpty()
        || !options.stripNameSuffixes.isEmpty()
        || !options.stripNamePrefixes.isEmpty()) {
      passes.maybeAdd(stripCode);
    }

    if (options.replaceIdGenerators) {
      passes.maybeAdd(replaceIdGenerators);
    }

    // Inline getters/setters in J2CL classes so that Object.defineProperties() calls (resulting
    // from desugaring) don't block class stripping.
    if (options.j2clPassMode.shouldAddJ2clPasses()
        && options.getPropertyCollapseLevel() == PropertyCollapseLevel.ALL) {
      // Relies on collapseProperties-triggered aggressive alias inlining.
      passes.maybeAdd(j2clPropertyInlinerPass);
    }

    if (options.inferConsts) {
      passes.maybeAdd(inferConsts);
    }

    // Detects whether invocations of the method goog.string.Const.from are done with an argument
    // which is a string literal. Needs to happen after inferConsts and collapseProperties.
    // TODO(b/160616664): this should be in getChecks() instead of getOptimizations(). But
    // for that the pass needs to understand constant properties as well. See b/31301233#comment10
    passes.maybeAdd(checkConstParams);

    // Running RemoveUnusedCode before disambiguate properties allows disambiguate properties to be
    // more effective if code that would prevent disambiguation can be removed.
    // TODO(b/66971163): Rename options since we're not actually using smartNameRemoval here now.
    if (options.smartNameRemoval) {

      // These passes remove code that is dead because of define flags.
      // If the dead code is weakly typed, running these passes before property
      // disambiguation results in more code removal.
      // The passes are one-time on purpose. (The later runs are loopable.)
      if (options.foldConstants && (options.inlineVariables || options.inlineLocalVariables)) {
        passes.maybeAdd(earlyInlineVariables);
        passes.maybeAdd(earlyPeepholeOptimizations);
      }

      passes.maybeAdd(removeUnusedCodeOnce);
    }

    // Property disambiguation should only run once and needs to be done
    // soon after type checking, both so that it can make use of type
    // information and so that other passes can take advantage of the renamed
    // properties.
    if (options.shouldDisambiguateProperties() && options.isTypecheckingEnabled()) {
      passes.maybeAdd(disambiguateProperties);
    }

    if (options.computeFunctionSideEffects) {
      passes.maybeAdd(markPureFunctions);
    }

    passes.assertAllOneTimePasses();

    if (options.smartNameRemoval) {
      // Place one-time marker passes around this loop to prevent the addition of a looping pass
      // above or below from accidentally becoming part of the loop.
      passes.maybeAdd(createEmptyPass(PassNames.BEFORE_EARLY_OPTIMIZATION_LOOP));
      passes.addAll(getEarlyOptimizationLoopPasses());
      // TODO(): Remove this early loop or rename the option that enables it
      // to something more appropriate.
      passes.maybeAdd(createEmptyPass(PassNames.AFTER_EARLY_OPTIMIZATION_LOOP));
    }

    // This needs to come after the inline constants pass, which is run within
    // the code removing passes.
    if (options.closurePass) {
      passes.maybeAdd(closureOptimizePrimitives);
    }

    // ReplaceStrings runs after CollapseProperties in order to simplify
    // pulling in values of constants defined in enums structures. It also runs
    // after disambiguate properties and smart name removal so that it can
    // correctly identify logging types and can replace references to string
    // expressions.
    if (!options.replaceStringsFunctionDescriptions.isEmpty()) {
      passes.maybeAdd(replaceStrings);
    }

    // TODO(user): This forces a first crack at crossChunkCodeMotion
    // before devirtualization. Once certain functions are devirtualized,
    // it confuses crossChunkCodeMotion ability to recognized that
    // it is recursive.

    // TODO(user): This is meant for a temporary quick win.
    // In the future, we might want to improve our analysis in
    // CrossChunkCodeMotion so we don't need to do this.
    if (options.shouldRunCrossChunkCodeMotion()) {
      passes.maybeAdd(crossModuleCodeMotion);
    }

    // Method devirtualization benefits from property disambiguation so
    // it should run after that pass but before passes that do
    // optimizations based on global names (like cross module code motion
    // and inline functions).  Smart Name Removal does better if run before
    // this pass.
    if (options.devirtualizeMethods) {
      passes.maybeAdd(devirtualizeMethods);
    }

    if (options.customPasses != null) {
      passes.maybeAdd(getCustomPasses(CustomPassExecutionTime.BEFORE_OPTIMIZATION_LOOP));
    }

    passes.maybeAdd(createEmptyPass(PassNames.BEFORE_MAIN_OPTIMIZATIONS));

    // Because FlowSensitiveInlineVariables does not operate on the global scope due to compilation
    // time, we need to run it once before InlineFunctions so that we don't miss inlining
    // opportunities when a function will be inlined into the global scope.
    if (options.inlineVariables || options.inlineLocalVariables) {
      passes.maybeAdd(flowSensitiveInlineVariables);
    }

    passes.addAll(getMainOptimizationLoop());
    passes.maybeAdd(createEmptyPass(PassNames.AFTER_MAIN_OPTIMIZATIONS));

    // Some optimizations belong outside the loop because running them more
    // than once would either have no benefit or be incorrect.
    if (options.customPasses != null) {
      passes.maybeAdd(getCustomPasses(CustomPassExecutionTime.AFTER_OPTIMIZATION_LOOP));
    }

    assertValidOrderForOptimizations(passes);
    return passes;
  }

  @Override
  protected PassListBuilder getFinalizations() {
    PassListBuilder passes = new PassListBuilder(options);

    if (options.isPropertyRenamingOnlyCompilationMode()) {
      if (options.rewriteGlobalDeclarationsForTryCatchWrapping
          || options.renamePrefixNamespace != null) {
        passes.maybeAdd(rewriteGlobalDeclarationsForTryCatchWrapping);
      }

      passes.maybeAdd(renameProperties);

      if (options.renamePrefixNamespace != null
          && options.chunkOutputType == ChunkOutputType.GLOBAL_NAMESPACE) {
        if (!GLOBAL_SYMBOL_NAMESPACE_PATTERN.matcher(options.renamePrefixNamespace).matches()) {
          throw new IllegalArgumentException(
              "Illegal character in renamePrefixNamespace name: " + options.renamePrefixNamespace);
        }
        passes.maybeAdd(rescopeGlobalSymbols);
      }

      return passes;
    }

    if (options.doLateLocalization()) {
      if (options.shouldRunReplaceMessagesPass()) {
        passes.maybeAdd(getReplaceProtectedMessagesPass());
      }
      passes.maybeAdd(substituteLocaleData);
      passes.maybeAdd(peepholeOptimizationsOnce);
    }

    if (options.inlineVariables || options.inlineLocalVariables) {
      passes.maybeAdd(flowSensitiveInlineVariables);

      // After inlining variable uses, some variables may be unused.
      // If we're doing late localization, the simple code removal pass runs added below will clean
      // those up. Otherwise, clean them up now.
      if (!options.doLateLocalization() && shouldRunRemoveUnusedCode()) {
        passes.maybeAdd(removeUnusedCodeOnce);
      }
    }

    if (options.doLateLocalization()) {
      passes.addAll(getPostL10nOptimizations());
    }

    passes.maybeAdd(createEmptyPass("beforeModuleMotion"));

    if (options.shouldRunCrossChunkCodeMotion()) {
      passes.maybeAdd(crossModuleCodeMotion);
    }

    if (options.shouldRunCrossChunkMethodMotion()) {
      passes.maybeAdd(crossModuleMethodMotion);
    }

    passes.maybeAdd(createEmptyPass("afterModuleMotion"));

    if (options.optimizeESClassConstructors && options.getOutputFeatureSet().contains(ES2015)) {
      passes.maybeAdd(optimizeConstructors);
    }

    // Isolate injected polyfills from the global scope. Runs late in the optimization loop
    // to take advantage of property renaming & RemoveUnusedCode, as this pass will increase code
    // size by wrapping all potential polyfill usages.
    if (options.getIsolatePolyfills()) {
      passes.maybeAdd(isolatePolyfills);
    }

    if (options.collapseAnonymousFunctions) {
      // TODO: b/197349249 - Maybe we should just move this pass after denormalization. It seems
      // weird to convert from function expression to function declaration while we're still
      // supposed to be in a normalized state. But it requires testing as perhaps some optimizations
      // in that range will get affected if we skip this rewriting.
      passes.maybeAdd(collapseAnonymousFunctions);
    }

    // Move functions before extracting prototype member declarations.
    if (options.rewriteGlobalDeclarationsForTryCatchWrapping
        // renamePrefixNamescape relies on rewriteGlobalDeclarationsForTryCatchWrapping
        // to preserve semantics.
        || options.renamePrefixNamespace != null) {
      passes.maybeAdd(rewriteGlobalDeclarationsForTryCatchWrapping);
    }

    // The mapped name anonymous function pass makes use of information that
    // the extract prototype member declarations pass removes so the former
    // happens before the latter.
    if (options.extractPrototypeMemberDeclarations != ExtractPrototypeMemberDeclarationsMode.OFF) {
      passes.maybeAdd(extractPrototypeMemberDeclarations);
    }

    if (options.shouldAmbiguateProperties()
        && options.propertyRenaming == PropertyRenamingPolicy.ALL_UNQUOTED
        && options.isTypecheckingEnabled()) {
      passes.maybeAdd(ambiguateProperties);
    }

    if (options.propertyRenaming == PropertyRenamingPolicy.ALL_UNQUOTED) {
      passes.maybeAdd(renameProperties);
    } else {
      passes.maybeAdd(removePropertyRenamingCalls);
    }

    // Reserve global names added to the "windows" object.
    if (options.reserveRawExports) {
      passes.maybeAdd(gatherRawExports);
    }

    // This comes after property renaming because quoted property names must
    // not be renamed.
    if (options.convertToDottedProperties) {
      passes.maybeAdd(convertToDottedProperties);
    }

    // Property renaming must happen before this pass runs since this
    // pass may convert dotted properties into quoted properties.  It
    // is beneficial to run before alias strings, alias keywords and
    // variable renaming.
    if (options.rewriteFunctionExpressions) {
      passes.maybeAdd(rewriteFunctionExpressions);
    }

    // This comes after converting quoted property accesses to dotted property
    // accesses in order to avoid aliasing property names.
    if (options.getAliasStringsMode() != AliasStringsMode.NONE) {
      passes.maybeAdd(aliasStrings);
    }

    if (options.coalesceVariableNames) {
      // Passes after this point can no longer depend on normalized AST
      // assumptions because the code is marked as un-normalized
      passes.maybeAdd(coalesceVariableNames);

      // coalesceVariables creates identity assignments and more redundant code
      // that can be removed, rerun the peephole optimizations to clean them
      // up.
      if (options.foldConstants) {
        passes.maybeAdd(peepholeOptimizationsOnce);
      }
    }

    // Passes after this point can no longer depend on normalized AST assumptions.
    passes.maybeAdd(markUnnormalized);

    if (options.collapseVariableDeclarations) {
      passes.maybeAdd(exploitAssign);
      passes.maybeAdd(collapseVariableDeclarations);
    }

    // This pass works best after collapseVariableDeclarations.
    passes.maybeAdd(denormalize);

    if (options.variableRenaming != VariableRenamingPolicy.ALL) {
      // If we're leaving some (or all) variables with their old names,
      // then we need to undo any of the markers we added for distinguishing
      // local variables ("x" -> "x$jscomp$1").
      passes.maybeAdd(invertContextualRenaming);
    }

    if (options.variableRenaming != VariableRenamingPolicy.OFF) {
      passes.maybeAdd(renameVars);
    }

    if (options.labelRenaming) {
      passes.maybeAdd(renameLabels);
    }

    if (options.foldConstants) {
      passes.maybeAdd(latePeepholeOptimizations);
    }

    // If side-effects were protected, remove the protection now.
    // Note that when using precompiled libraries we always run this pass regardless of the
    // 'shouldProtectHiddenSideEffects' option: the library compilation may have run
    // with side effect protection enabled even if the binary disables it, so we assume we may
    // always need to strip side effect protection.
    if (options.shouldProtectHiddenSideEffects() || options.getMergedPrecompiledLibraries()) {
      passes.maybeAdd(stripSideEffectProtection);
    }

    if (options.renamePrefixNamespace != null
        && options.chunkOutputType == ChunkOutputType.GLOBAL_NAMESPACE) {
      if (!GLOBAL_SYMBOL_NAMESPACE_PATTERN.matcher(options.renamePrefixNamespace).matches()) {
        throw new IllegalArgumentException(
            "Illegal character in renamePrefixNamespace name: " + options.renamePrefixNamespace);
      }
      passes.maybeAdd(rescopeGlobalSymbols);
    }

    // Raise to ES2015, if allowed
    if (options.getOutputFeatureSet().contains(ES2015)) {
      passes.maybeAdd(optimizeToEs6);
    }

    if (!options.parenthesizeFunctionsInChunks.isEmpty()) {
      passes.maybeAdd(parenthesizeFunctionsInChunks);
    }

    // Must run after all non-safety-check passes as the optimizations do not support modules.
    if (options.chunkOutputType == ChunkOutputType.ES_MODULES) {
      passes.maybeAdd(convertChunksToESModules);
    }

    // Safety checks.  These should always be the last passes.
    passes.maybeAdd(checkAstValidity);
    passes.maybeAdd(varCheckValidity);
    return passes;
  }

  private PassListBuilder getEarlyOptimizationLoopPasses() {
    PassListBuilder earlyLoopPasses = new PassListBuilder(options);

    if (options.inlineVariables || options.inlineLocalVariables) {
      earlyLoopPasses.maybeAdd(inlineVariables);
    } else if (options.inlineConstantVars) {
      earlyLoopPasses.maybeAdd(inlineConstants);
    }

    if (options.collapseObjectLiterals) {
      earlyLoopPasses.maybeAdd(collapseObjectLiterals);
    }

    if (shouldRunRemoveUnusedCode()) {
      earlyLoopPasses.maybeAdd(removeUnusedCode);
    }

    if (options.foldConstants) {
      earlyLoopPasses.maybeAdd(peepholeOptimizations);
    }

    if (options.removeDeadCode) {
      earlyLoopPasses.maybeAdd(removeUnreachableCode);
    }

    earlyLoopPasses.assertAllLoopablePasses();
    return earlyLoopPasses;
  }

  /**
   * Add optimization passes that need to run after late localization has been done.
   *
   * <p>Once we've replaced message references with string constants and `goog.LOCALE` with a
   * constant value, we need to re-run some optimizations, so they can throw away code for locales
   * that aren't relevant and perform constant folding on the message strings we've now inserted.
   */
  private PassListBuilder getPostL10nOptimizations() {
    // Localization replaced lots of function calls with constants to get statements like these.
    //
    // `goog.LOCALE = 'es-419';`
    // `const MSG_GREETING = 'Hola';`
    // `const MSG_GREETING_WITH_NAME = 'Hola, ' + person.getName();`
    //
    // Before calling this method we should also have run `flowSensitiveInlineVariables`
    // To make optimization opportunities for peepholeOptimizations.
    //
    // For example, `flowSensitiveInlineVariables` will change this
    // ```
    // var x = 'localized version of message';
    // x = x + '&nbsp';
    // ```
    // to this
    // ```
    // var x = 'localized version of message' + '&nbsp;';
    // ```
    // Which constant folding can then turn into this
    // ```
    // var x = 'localized version of message&nbsp;';
    // ```
    // Now we should have unblocked a lot of potential optimizations,
    // so do an optimization loop to perform those.
    // This loop is similar to the one created by getMainOptimizationLoop().
    // These should be in the same order as those, but only optimizations we expect to need
    // doing to clean up after localization are included.
    PassListBuilder loopPasses = new PassListBuilder(options);

    if (options.optimizeCalls) {
      loopPasses.maybeAdd(optimizeCalls);
    }

    if (options.j2clPassMode.shouldAddJ2clPasses()) {
      loopPasses.maybeAdd(j2clConstantHoisterPass);
      loopPasses.maybeAdd(j2clClinitPass);
    }

    // It is important that inlineVariables and peepholeOptimizations run after inlineFunctions,
    // because inlineFunctions relies on them to clean up patterns it introduces. This affects our
    // size-based loop-termination heuristic.
    if (options.getInlineFunctionsLevel() != Reach.NONE) {
      loopPasses.maybeAdd(inlineFunctions);
    }

    if (options.inlineVariables || options.inlineLocalVariables) {
      loopPasses.maybeAdd(inlineVariables);
    } else if (options.inlineConstantVars) {
      loopPasses.maybeAdd(inlineConstants);
    }

    if (shouldRunRemoveUnusedCode()) {
      loopPasses.maybeAdd(removeUnusedCode);
    }

    if (options.foldConstants) {
      loopPasses.maybeAdd(peepholeOptimizations);
    }

    if (options.removeDeadCode) {
      loopPasses.maybeAdd(removeUnreachableCode);
    }
    loopPasses.assertAllLoopablePasses();
    return loopPasses;
  }

  /** Creates the passes for the main optimization loop. */
  private PassListBuilder getMainOptimizationLoop() {
    PassListBuilder passes = new PassListBuilder(options);
    if (options.inlineGetters) {
      passes.maybeAdd(inlineSimpleMethods);
    }

    if (options.shouldInlineProperties() && options.isTypecheckingEnabled()) {
      passes.maybeAdd(inlineProperties);
    }

    // The Polymer source is usually not included in the compilation, but it creates
    // getters/setters for many properties in compiled code. Dead property property assignment
    // elimination is only safe when it knows about getters/setters. Therefore, we skip
    // it if the polymer pass is enabled.
    final boolean shouldRunDeadAssignmentElimination =
        (options.removeUnusedVars || options.removeUnusedLocalVars)
            && options.deadAssignmentElimination;
    final boolean shouldRunDeadPropertyAssignmentElimination =
        shouldRunDeadAssignmentElimination && options.polymerVersion == null;
    if (shouldRunDeadPropertyAssignmentElimination) {
      passes.maybeAdd(deadPropertyAssignmentElimination);
    }

    if (options.optimizeCalls) {
      passes.maybeAdd(optimizeCalls);
    }

    if (options.j2clPassMode.shouldAddJ2clPasses()) {
      passes.maybeAdd(j2clConstantHoisterPass);
      passes.maybeAdd(j2clClinitPass);
    }

    // It is important that inlineVariables and peepholeOptimizations run after inlineFunctions,
    // because inlineFunctions relies on them to clean up patterns it introduces. This affects our
    // size-based loop-termination heuristic.
    if (options.getInlineFunctionsLevel() != Reach.NONE) {
      passes.maybeAdd(inlineFunctions);
    }

    if (options.inlineVariables || options.inlineLocalVariables) {
      passes.maybeAdd(inlineVariables);
    } else if (options.inlineConstantVars) {
      passes.maybeAdd(inlineConstants);
    }

    if (shouldRunDeadAssignmentElimination) {
      passes.maybeAdd(deadAssignmentsElimination);
    }

    if (options.collapseObjectLiterals) {
      passes.maybeAdd(collapseObjectLiterals);
    }

    if (shouldRunRemoveUnusedCode()) {
      passes.maybeAdd(removeUnusedCode);
    }

    if (options.foldConstants) {
      passes.maybeAdd(peepholeOptimizations);
    }

    if (options.removeDeadCode) {
      passes.maybeAdd(removeUnreachableCode);
    }

    passes.assertAllLoopablePasses();
    return passes;
  }

  /**
   * For use in builds that run getOptimizations() on the result of an AST directly from
   * getChecks(), that has not gone through TypedAST serialization/deserialization.
   */
  private void addNonTypedAstNormalizationPasses(PassListBuilder passes) {
    passes.maybeAdd(removeCastNodes);
    passes.maybeAdd(typesToColors);
  }

  private boolean shouldRunRemoveUnusedCode() {
    return options.removeUnusedVars
        || options.removeUnusedLocalVars
        || options.removeUnusedPrototypeProperties
        || options.isRemoveUnusedClassProperties()
        || options.rewritePolyfills;
  }

  private final PassFactory checkSideEffects =
      PassFactory.builder()
          .setName("checkSideEffects")
          .setInternalFactory(
              (compiler) ->
                  new CheckSideEffects(
                      compiler,
                      options.checkSuspiciousCode,
                      options.shouldProtectHiddenSideEffects()))
          .build();

  /** Removes the "protector" functions that were added by CheckSideEffects. */
  private final PassFactory stripSideEffectProtection =
      PassFactory.builder()
          .setName(PassNames.STRIP_SIDE_EFFECT_PROTECTION)
          .setInternalFactory(CheckSideEffects.StripProtection::new)
          .build();

  /** Checks for code that is probably wrong (such as stray expressions). */
  private final PassFactory suspiciousCode =
      PassFactory.builder()
          .setName("suspiciousCode")
          .setInternalFactory(
              (compiler) -> {
                List<NodeTraversal.Callback> sharedCallbacks = new ArrayList<>();
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
          .build();

  /**
   * Certain checks and rewriting passes need to run in a particular order. For example, the
   * PolymerPass will not work correctly unless it runs after the goog.provide() processing. This
   * enforces those constraints.
   *
   * @param checks The list of check passes
   */
  private void assertValidOrderForChecks(PassListBuilder checks) {
    checks.assertPassOrder(
        declaredGlobalExternsOnWindow,
        checkVars,
        "declaredGlobalExternsOnWindow must happen before VarCheck, which adds synthetic externs");
    checks.assertPassOrder(
        chromePass,
        checkJsDocAndEs6Modules,
        "The ChromePass must run before after JsDoc and Es6 module checking.");
    checks.assertPassOrder(
        closureRewriteModule,
        processDefinesCheck,
        "Must rewrite goog.module before processing @define's, so that @defines in modules work.");
    checks.assertPassOrder(
        closurePrimitives, polymerPass, "The Polymer pass must run after goog.provide processing.");
    checks.assertPassOrder(
        chromePass, polymerPass, "The Polymer pass must run after ChromePass processing.");
    checks.assertPassOrder(
        polymerPass, suspiciousCode, "The Polymer pass must run before suspiciousCode processing.");
    checks.assertPassOrder(
        addSyntheticScript,
        gatherModuleMetadataPass,
        "Cannot add a synthetic script node after module metadata creation.");
    checks.assertPassOrder(
        closureRewriteModule,
        removeSyntheticScript,
        "Synthetic script node should be removed only after module rewriting.");

    if (checks.contains(closureGoogScopeAliases)) {
      checkState(
          checks.contains(checkVariableReferences),
          "goog.scope processing requires variable checking");
    }
    // TODO(lharker): add this back once fixing the ProcessCommonJSModules VarCheck branch
    // checks.assertPassOrder(
    //     checkVariableReferences,
    //     closureGoogScopeAliases,
    //     "Variable checking must happen before goog.scope processing.");

    checks.assertPassOrder(
        gatherModuleMetadataPass,
        closureCheckModule,
        "Need to gather module metadata before checking closure modules.");

    checks.assertPassOrder(
        gatherModuleMetadataPass,
        createModuleMapPass,
        "Need to gather module metadata before scanning modules.");

    checks.assertPassOrder(
        createModuleMapPass,
        rewriteCommonJsModules,
        "Need to gather module information before rewriting CommonJS modules.");

    checks.assertPassOrder(
        rewriteScriptsToEs6Modules,
        gatherModuleMetadataPass,
        "Need to gather module information after rewriting scripts to modules.");

    checks.assertPassOrder(
        gatherModuleMetadataPass,
        checkMissingRequires,
        "Need to gather module information before checking for missing requires.");
  }

  /**
   * Certain optimizations need to run in a particular order. For example, OptimizeCalls must run
   * before RemoveSuperMethodsPass, because the former can invalidate assumptions in the latter.
   * This enforces those constraints.
   *
   * @param optimizations The list of optimization passes
   */
  private void assertValidOrderForOptimizations(PassListBuilder optimizations) {
    optimizations.assertPassOrder(
        j2clPass,
        TranspilationPasses.rewriteGenerators,
        "J2CL normalization should be done before generator re-writing.");

    optimizations.assertPassOrder(
        TranspilationPasses.rewritePolyfills,
        processDefinesOptimize,
        "Polyfill injection must be done before processDefines as some polyfills reference "
            + "goog.defines.");
    optimizations.assertPassOrder(
        TranspilationPasses.injectTranspilationRuntimeLibraries,
        processDefinesOptimize,
        "Runtime library injection must be done before processDefines some runtime libraries "
            + "reference goog.defines.");

    optimizations.assertPassOrder(
        processDefinesOptimize,
        j2clUtilGetDefineRewriterPass,
        "J2CL define re-writing should be done after processDefines since it relies on "
            + "collectDefines which has side effects.");

    optimizations.assertPassOrder(
        removeUnusedCode,
        isolatePolyfills,
        "Polyfill isolation should be done after RemovedUnusedCode. Otherwise unused polyfill"
            + " removal will not find any polyfill usages and will delete all polyfills.");
  }

  /** Checks that all goog.require()s are used. */
  private final PassFactory extraRequires =
      PassFactory.builder()
          .setName("checkExtraRequires")
          .setInternalFactory(
              (compiler) -> new CheckExtraRequires(compiler, options.getUnusedImportsToRemove()))
          .build();

  private final PassFactory checkMissingRequires =
      PassFactory.builder()
          .setName("checkMissingRequires")
          .setInternalFactory(
              (compiler) -> new CheckMissingRequires(compiler, compiler.getModuleMetadataMap()))
          .build();

  private static final DiagnosticType GENERATE_EXPORTS_ERROR =
      DiagnosticType.error(
          "JSC_GENERATE_EXPORTS_ERROR",
          "Exports can only be generated if export symbol/property functions are set.");

  /** Verifies JSDoc annotations are used properly and checks for ES modules. */
  private final PassFactory checkJsDocAndEs6Modules =
      PassFactory.builder()
          .setName("checkJsDocAndEs6Modules")
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
              })
          .build();

  private final PassFactory generateIjs =
      PassFactory.builder()
          .setName("generateIjs")
          .setInternalFactory(ConvertToTypedInterface::new)
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
          .build();

  /** Closure pre-processing pass. */
  private final PassFactory closurePrimitives =
      PassFactory.builder()
          .setName("closurePrimitives")
          .setInternalFactory(
              (compiler) -> {
                preprocessorSymbolTableFactory.maybeInitialize(compiler);
                final ProcessClosurePrimitives pass = new ProcessClosurePrimitives(compiler);
                return (Node externs, Node root) -> {
                  pass.process(externs, root);
                  compiler.addExportedNames(pass.getExportedVariableNames());
                };
              })
          .build();

  /** Closure provide/require rewriting pass. */
  private final PassFactory closureProvidesRequires =
      PassFactory.builder()
          .setName("closureProvidesRequires")
          .setInternalFactory(
              (compiler) ->
                  new ProcessClosureProvidesAndRequires(
                      compiler, options.shouldPreservesGoogProvidesAndRequires()))
          .build();

  /** Process AngularJS-specific annotations. */
  private final PassFactory angularPass =
      PassFactory.builder()
          .setName(PassNames.ANGULAR_PASS)
          .setInternalFactory(AngularPass::new)
          .build();

  /**
   * Return the form of `replaceMessages` that does the replacement all at once, and which must run
   * before optimizations.
   */
  private PassFactory getFullReplaceMessagesPass() {
    return PassFactory.builder()
        .setName(PassNames.REPLACE_MESSAGES)
        .setInternalFactory(
            (compiler) ->
                new ReplaceMessages(
                        compiler,
                        options.messageBundle,
                        /* allow messages with goog.getMsg */
                        options.getStrictMessageReplacement())
                    .getFullReplacementPass())
        .build();
  }

  /**
   * Return the pass that protects messages from mangling, so they can be found and replaced after
   * optimizations.
   */
  private PassFactory getProtectMessagesPass() {
    return PassFactory.builder()
        .setName("protectMessages")
        .setInternalFactory(
            (compiler) ->
                new ReplaceMessages(
                        compiler,
                        options.messageBundle,
                        /* allow messages with goog.getMsg */
                        options.getStrictMessageReplacement())
                    .getMsgProtectionPass())
        .build();
  }

  /** Return the pass that identifies protected messages and completes their replacement. */
  private PassFactory getReplaceProtectedMessagesPass() {
    return PassFactory.builder()
        .setName("replaceProtectedMessages")
        .setInternalFactory(
            (compiler) ->
                new ReplaceMessages(
                        compiler,
                        options.messageBundle,
                        /* allow messages with goog.getMsg */
                        options.getStrictMessageReplacement())
                    .getReplacementCompletionPass())
        .build();
  }

  private final PassFactory replaceMessagesForChrome =
      PassFactory.builder()
          .setName(PassNames.REPLACE_MESSAGES)
          .setInternalFactory(
              (compiler) ->
                  new ReplaceMessagesForChrome(
                      compiler, new GoogleJsMessageIdGenerator(options.tcProjectId)
                      /* allow messages with goog.getMsg */
                      ))
          .build();

  /** Applies aliases and inlines goog.scope. */
  private final PassFactory closureGoogScopeAliasesForIjs =
      PassFactory.builder()
          .setName("closureGoogScopeAliasesForIjs")
          .setInternalFactory((compiler) -> ScopedAliases.builder(compiler).build())
          .build();

  /**
   * Applies aliases and inlines goog.scope, storing information about the transformations
   * performed.
   */
  private final PassFactory closureGoogScopeAliases =
      PassFactory.builder()
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
          .build();

  private final PassFactory injectRuntimeLibraries =
      PassFactory.builder()
          .setName("InjectRuntimeLibraries")
          .setInternalFactory(
              (compiler) ->
                  new InjectRuntimeLibraries(
                      compiler, ImmutableSet.copyOf(compiler.getOptions().forceLibraryInjection)))
          .build();

  private final PassFactory removeWeakSources =
      PassFactory.builder()
          .setName("removeWeakSources")
          .setInternalFactory(RemoveWeakSources::new)
          .build();

  private final PassFactory declaredGlobalExternsOnWindow =
      PassFactory.builder()
          .setName(PassNames.DECLARED_GLOBAL_EXTERNS_ON_WINDOW)
          .setInternalFactory(DeclaredGlobalExternsOnWindow::new)
          .build();

  private final PassFactory checkTypeImportCodeReferences =
      PassFactory.builder()
          .setName("checkTypeImportCodeReferences")
          .setInternalFactory(CheckTypeImportCodeReferences::new)
          .build();

  /** Rewrites goog.defineClass */
  private final PassFactory closureRewriteClass =
      PassFactory.builder()
          .setName(PassNames.CLOSURE_REWRITE_CLASS)
          .setInternalFactory(ClosureRewriteClass::new)
          .build();

  /** Checks of correct usage of goog.module */
  private final PassFactory closureCheckModule =
      PassFactory.builder()
          .setName("closureCheckModule")
          .setInternalFactory(
              (compiler) -> new ClosureCheckModule(compiler, compiler.getModuleMetadataMap()))
          .build();

  /** Rewrites goog.module */
  private final PassFactory closureRewriteModule =
      PassFactory.builder()
          .setName("closureRewriteModule")
          .setInternalFactory(
              (compiler) -> {
                preprocessorSymbolTableFactory.maybeInitialize(compiler);
                return new ClosureRewriteModule(
                    compiler,
                    preprocessorSymbolTableFactory.getInstanceOrNull(),
                    compiler.getTopScope());
              })
          .build();

  /** Checks goog.require, goog.forwardDeclare, goog.requireType, and goog.module.get calls */
  private final PassFactory checkClosureImports =
      PassFactory.builder()
          .setName("checkGoogRequires")
          .setInternalFactory(
              (compiler) -> new CheckClosureImports(compiler, compiler.getModuleMetadataMap()))
          .build();

  /** Rewrite imports for Closure Library's goog.js file to global goog references. */
  private final PassFactory rewriteGoogJsImports =
      PassFactory.builder()
          .setName("rewriteGoogJsImports")
          .setInternalFactory(
              (compiler) ->
                  new RewriteGoogJsImports(
                      compiler,
                      RewriteGoogJsImports.Mode.LINT_AND_REWRITE,
                      compiler.getModuleMap()))
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
                      Optional<ImmutableSet.Builder<String>> cssNames =
                          options.gatherCssNames
                              ? Optional.of(ImmutableSet.builder())
                              : Optional.empty();

                      ReplaceCssNames pass =
                          new ReplaceCssNames(
                              compiler,
                              options.cssRenamingMap,
                              cssName -> cssNames.ifPresent(builder -> builder.add(cssName)),
                              options.cssRenamingSkiplist);
                      pass.process(externs, jsRoot);

                      compiler.setCssNames(cssNames.isPresent() ? cssNames.get().build() : null);
                    }
                  })
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
                return new InlineVariables(compiler, mode);
              })
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
          .build();

  /** Various peephole optimizations. */
  private final PassFactory peepholeOptimizationsOnce =
      PassFactory.builder()
          .setName(PassNames.PEEPHOLE_OPTIMIZATIONS)
          .setInternalFactory(
              (compiler) ->
                  createPeepholeOptimizationsPass(compiler, PassNames.PEEPHOLE_OPTIMIZATIONS))
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
          .build();

  /** Checks that all variables are defined. */
  private final PassFactory checkVars =
      PassFactory.builder().setName(PassNames.CHECK_VARS).setInternalFactory(VarCheck::new).build();

  /** Infers constants. */
  private final PassFactory inferConsts =
      PassFactory.builder()
          .setName(PassNames.INFER_CONSTS)
          .setInternalFactory(InferConsts::new)
          .build();

  /** Checks for RegExp references. */
  private final PassFactory checkRegExp =
      PassFactory.builder()
          .setName(PassNames.CHECK_REG_EXP)
          .setInternalFactory(
              (compiler) -> {
                final CheckRegExp pass = new CheckRegExp(compiler, /* reportErrors= */ true);

                return new CompilerPass() {
                  @Override
                  public void process(Node externs, Node root) {
                    pass.process(externs, root);
                    compiler.setHasRegExpGlobalReferences(pass.isGlobalRegExpPropertiesUsed());
                  }
                };
              })
          .build();

  // This pass is redundant if we've already run checkRegExp during this compilation
  private final PassFactory checkRegExpForOptimizations =
      PassFactory.builder()
          .setName("checkRegExpForOptimizations")
          .setInternalFactory(
              (compiler) -> {
                final CheckRegExp regExpCheck =
                    new CheckRegExp(compiler, /* reportErrors= */ false);

                return new CompilerPass() {
                  @Override
                  public void process(Node externs, Node root) {
                    regExpCheck.process(externs, root);
                    compiler.setHasRegExpGlobalReferences(
                        regExpCheck.isGlobalRegExpPropertiesUsed());
                  }
                };
              })
          .build();

  /** Checks that references to variables look reasonable. */
  private final PassFactory checkVariableReferences =
      PassFactory.builder()
          .setName(PassNames.CHECK_VARIABLE_REFERENCES)
          .setInternalFactory(VariableReferenceCheck::new)
          .build();

  private final PassFactory checkSuper =
      PassFactory.builder().setName("checkSuper").setInternalFactory(CheckSuper::new).build();

  /** Clears the typed scope creator and all local typed scopes. */
  private final PassFactory clearTypedScopeCreatorPass =
      PassFactory.builder()
          .setName("clearTypedScopeCreatorPass")
          .setInternalFactory((compiler) -> (externs, root) -> compiler.clearTypedScopeCreator())
          .build();

  /** Clears the top typed scope when we're done with it. */
  private final PassFactory clearTopTypedScopePass =
      PassFactory.builder()
          .setName("clearTopTypedScopePass")
          .setInternalFactory(
              (compiler) ->
                  (externs, root) -> {
                    // clear these scopes which we don't need anymore so they can be garbage
                    // collected
                    compiler.setTopScope(null);
                    for (CompilerInput compilerInput : compiler.getInputsInOrder()) {
                      compilerInput.setTypedScope(null);
                    }
                  })
          .build();

  /** Runs type inference. */
  final PassFactory inferTypes =
      PassFactory.builder()
          .setName(PassNames.INFER_TYPES)
          .setInternalFactory(
              (compiler) ->
                  ((Node unused, Node srcRoot) -> {
                    Node globalRoot = srcRoot.getParent();

                    TypeInferencePass inferencePass =
                        new TypeInferencePass(
                            compiler,
                            compiler.getReverseAbstractInterpreter(),
                            (TypedScopeCreator) compiler.getTypedScopeCreator());
                    compiler.setTypeCheckingHasRun(true);
                    compiler.setTopScope(inferencePass.inferAllScopes(globalRoot));
                  }))
          .build();

  private final PassFactory inferJsDocInfo =
      PassFactory.builder()
          .setName("inferJsDocInfo")
          .setInternalFactory(InferJSDocInfo::new)
          .build();

  /** Checks type usage */
  private final PassFactory checkTypes =
      PassFactory.builder()
          .setName(PassNames.CHECK_TYPES)
          .setInternalFactory(
              (compiler) ->
                  (externs, root) -> {
                    TypeCheck check =
                        new TypeCheck(
                                compiler,
                                compiler.getReverseAbstractInterpreter(),
                                compiler.getTypeRegistry(),
                                compiler.getTopScope(),
                                (TypedScopeCreator) compiler.getTypedScopeCreator())
                            .reportUnknownTypes(
                                options.enables(
                                    DiagnosticGroup.forType(TypeCheck.UNKNOWN_EXPR_TYPE)))
                            .reportMissingProperties(
                                !options.disables(
                                    DiagnosticGroup.forType(TypeCheck.INEXISTENT_PROPERTY)));
                    check.process(externs, root);
                    compiler.getErrorManager().setTypedPercent(check.getTypedPercent());
                  })
          .build();

  /**
   * Checks possible execution paths of the program for problems: missing return statements and dead
   * code.
   */
  private final PassFactory checkControlFlow =
      PassFactory.builder()
          .setName("checkControlFlow")
          .setInternalFactory(
              (compiler) -> {
                List<NodeTraversal.Callback> callbacks = new ArrayList<>();
                if (!options.disables(DiagnosticGroups.CHECK_USELESS_CODE)) {
                  callbacks.add(new CheckUnreachableCode(compiler));
                }
                if (!options.disables(DiagnosticGroups.MISSING_RETURN)) {
                  callbacks.add(new CheckMissingReturn(compiler));
                }
                return combineChecks(compiler, callbacks);
              })
          .build();

  /** Checks access controls. Depends on type-inference. */
  private final PassFactory checkAccessControls =
      PassFactory.builder()
          .setName("checkAccessControls")
          .setInternalFactory(CheckAccessControls::new)
          .build();

  /**
   * Runs the single-file linter passes
   *
   * <p>These is NOT the configuration for the standalone Linter binary. New linter passes must also
   * be added to {@link LintPassConfig} as well as this list.
   */
  private final PassFactory lintChecks =
      PassFactory.builder()
          .setName(PassNames.LINT_CHECKS)
          .setInternalFactory(
              (compiler) -> {
                ImmutableList.Builder<NodeTraversal.Callback> callbacks =
                    ImmutableList.<NodeTraversal.Callback>builder()
                        .add(new CheckConstPrivateProperties(compiler))
                        .add(new CheckConstantCaseNames(compiler))
                        .add(new CheckDefaultExportOfGoogModule(compiler))
                        .add(new CheckEmptyStatements(compiler))
                        .add(new CheckEnums(compiler))
                        .add(new CheckEs6ModuleFileStructure(compiler))
                        .add(new CheckEs6Modules(compiler))
                        .add(new CheckNoMutatedEs6Exports(compiler))
                        .add(new CheckGoogModuleTypeScriptName(compiler))
                        .add(new CheckInterfaces(compiler))
                        .add(new CheckJSDocStyle(compiler))
                        .add(new CheckMissingSemicolon(compiler))
                        .add(new CheckNullabilityModifiers(compiler))
                        .add(new CheckPrimitiveAsObject(compiler))
                        .add(new CheckPrototypeProperties(compiler))
                        .add(new CheckUnusedPrivateProperties(compiler))
                        .add(new CheckUnusedLabels(compiler))
                        .add(new CheckUselessBlocks(compiler))
                        .add(new CheckVar(compiler));
                return combineChecks(compiler, callbacks.build());
              })
          .build();

  private final PassFactory analyzerChecks =
      PassFactory.builder()
          .setName(PassNames.ANALYZER_CHECKS)
          .setInternalFactory(
              (compiler) -> {
                ImmutableList<NodeTraversal.Callback> callbacks =
                    ImmutableList.of(
                        new CheckArrayWithGoogObject(compiler),
                        new ImplicitNullabilityCheck(compiler),
                        new CheckNestedNames(compiler));

                return combineChecks(compiler, callbacks);
              })
          .build();

  private final PassFactory checkRequiresAndProvidesSorted =
      PassFactory.builder()
          .setName("checkRequiresAndProvidesSorted")
          .setInternalFactory(
              (compiler) ->
                  combineChecks(
                      compiler,
                      ImmutableList.of(
                          new CheckProvidesSorted(CheckProvidesSorted.Mode.COLLECT_AND_REPORT),
                          new CheckRequiresSorted(CheckRequiresSorted.Mode.COLLECT_AND_REPORT))))
          .build();

  /** Executes the given callbacks with a {@link CombinedCompilerPass}. */
  private static CompilerPass combineChecks(
      AbstractCompiler compiler, List<NodeTraversal.Callback> callbacks) {
    checkArgument(!callbacks.isEmpty());
    return new CombinedCompilerPass(compiler, callbacks);
  }

  /** Checks that the code is ES5 strict compliant. */
  private final PassFactory checkStrictMode =
      PassFactory.builder()
          .setName("checkStrictMode")
          .setInternalFactory(
              (compiler) -> {
                CheckLevel defaultLevel =
                    options.expectStrictModeInput() ? CheckLevel.ERROR : CheckLevel.OFF;
                return new StrictModeCheck(compiler, defaultLevel);
              })
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
                      new ProcessTweaks(compiler, options.getTweakProcessing().shouldStrip())
                          .process(externs, jsRoot);
                    }
                  })
          .build();

  /** Check @define-annotated constants. */
  private final PassFactory processDefinesCheck = createProcessDefines(ProcessDefines.Mode.CHECK);

  /** Replace @define-annotated constants. */
  private final PassFactory processDefinesOptimize =
      createProcessDefines(ProcessDefines.Mode.OPTIMIZE);

  private PassFactory createProcessDefines(ProcessDefines.Mode mode) {
    return PassFactory.builder()
        .setName("processDefines_" + mode.name())
        .setInternalFactory(
            (compiler) ->
                new ProcessDefines.Builder(compiler)
                    .putReplacements(getAdditionalReplacements(options))
                    .putReplacements(options.getDefineReplacements())
                    .setMode(mode)
                    .setRecognizeClosureDefines(compiler.getOptions().closurePass)
                    .build())
        .build();
  }

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
                              options.stripNamePrefixes,
                              options.getTweakProcessing().shouldStrip());

                      pass.process(externs, jsRoot);
                    }
                  })
          // TODO(johnlenz): StripCode may be fooled by some newer features, like destructuring,
          // an).build();
          .build();

  /** Checks that all constants are not modified */
  private final PassFactory checkConsts =
      PassFactory.builder().setName("checkConsts").setInternalFactory(ConstCheck::new).build();

  /** Checks that the arguments are constants */
  private final PassFactory checkConstParams =
      PassFactory.builder()
          .setName(PassNames.CHECK_CONST_PARAMS)
          .setInternalFactory(ConstParamCheck::new)
          .build();

  /** Replaces goog.toggle calls with toggle lookups. */
  private final PassFactory replaceToggles =
      PassFactory.builder()
          .setName("replaceToggles")
          .setInternalFactory(ReplaceToggles::new)
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
                              options.replaceStringsFunctionDescriptions);
                      pass.process(externs, root);
                      compiler.setStringMap(pass.getStringMap());
                    }
                  })
          .build();

  /** Optimizes the "arguments" array. */
  private final PassFactory optimizeArgumentsArray =
      PassFactory.builder()
          .setName(PassNames.OPTIMIZE_ARGUMENTS_ARRAY)
          .setInternalFactory(OptimizeArgumentsArray::new)
          .build();

  /** Remove variables set to goog.abstractMethod. */
  private final PassFactory closureCodeRemoval =
      PassFactory.builder()
          .setName("closureCodeRemoval")
          .setInternalFactory(
              (compiler) ->
                  new ClosureCodeRemoval(
                      compiler, options.removeAbstractMethods, options.removeClosureAsserts))
          .build();

  /** Special case optimizations for closure functions. */
  private final PassFactory closureOptimizePrimitives =
      PassFactory.builder()
          .setName("closureOptimizePrimitives")
          .setInternalFactory(
              (compiler) ->
                  new ClosureOptimizePrimitives(
                      compiler,
                      compiler.getOptions().getOutputFeatureSet().contains(ES2015)))
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
          .build();

  /** Converts cross chunk references into ES Module import and export statements. */
  private final PassFactory convertChunksToESModules =
      PassFactory.builder()
          .setName("convertChunksToESModules")
          .setInternalFactory(ConvertChunksToESModules::new)
          .build();

  /**
   * Perform inlining of aliases and collapsing of qualified names to global variables in order to
   * improve later optimizations, such as RemoveUnusedCode.
   */
  private final PassFactory inlineAndCollapseProperties =
      PassFactory.builder()
          .setName("inlineAndCollapseProperties")
          .setInternalFactory(
              (compiler) ->
                  InlineAndCollapseProperties.builder(compiler)
                      .setPropertyCollapseLevel(options.getPropertyCollapseLevel())
                      .setChunkOutputType(options.getChunkOutputType())
                      .setHaveModulesBeenRewritten(options.getProcessCommonJSModules())
                      .setModuleResolutionMode(options.getModuleResolutionMode())
                      .setAssumeStaticInheritanceIsNotUsed(
                          options.getAssumeStaticInheritanceIsNotUsed())
                      .build())
          .build();

  /** Rewrite properties as variables. */
  private final PassFactory collapseObjectLiterals =
      PassFactory.builder()
          .setName(PassNames.COLLAPSE_OBJECT_LITERALS)
          .setRunInFixedPointLoop(true)
          .setInternalFactory(
              (compiler) -> new InlineObjectLiterals(compiler, compiler.getUniqueNameIdSupplier()))
          .build();

  /** Disambiguate property names based on type information. */
  private final PassFactory disambiguateProperties =
      PassFactory.builder()
          .setName(PassNames.DISAMBIGUATE_PROPERTIES)
          .setInternalFactory(
              (compiler) ->
                  new DisambiguateProperties(compiler, options.getPropertiesThatMustDisambiguate()))
          .build();

  /** Marks all functions for wrapping with () in the specified chunks. */
  private final PassFactory parenthesizeFunctionsInChunks =
      PassFactory.builder()
          .setName(PassNames.PARENTHESIZE_FUNCTIONS_IN_CHUNKS)
          .setInternalFactory(
              (compiler) ->
                  new ParenthesizeFunctionsInChunks(
                      compiler, new LinkedHashSet<>(options.parenthesizeFunctionsInChunks)))
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
          .build();

  /** Removes ECMAScript class constructors when an implicit constructor is sufficient. */
  private final PassFactory optimizeConstructors =
      PassFactory.builder()
          .setName("optimizeConstructors")
          .setRunInFixedPointLoop(false)
          .setInternalFactory(
              (compiler) ->
                  OptimizeCalls.builder()
                      .setCompiler(compiler)
                      .setConsiderExterns(false)
                      // Remove redundant constructor definitions.
                      .addPass(new OptimizeConstructors(compiler))
                      .build())
          .build();

  /** Look for function calls that are pure, and annotate them that way. */
  private final PassFactory markPureFunctions =
      PassFactory.builder()
          .setName("markPureFunctions")
          .setInternalFactory(PureFunctionIdentifier.Driver::new)
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
                return new InlineVariables(compiler, mode);
              })
          .build();

  /** Inlines variables that are marked as constants. */
  private final PassFactory inlineConstants =
      PassFactory.builder()
          .setName("inlineConstants")
          .setRunInFixedPointLoop(true)
          .setInternalFactory(
              (compiler) -> new InlineVariables(compiler, InlineVariables.Mode.CONSTANTS_ONLY))
          .build();

  /** Use data flow analysis to remove dead branches. */
  private final PassFactory removeUnreachableCode =
      PassFactory.builder()
          .setName(PassNames.REMOVE_UNREACHABLE_CODE)
          .setRunInFixedPointLoop(true)
          .setInternalFactory(UnreachableCodeElimination::new)
          .build();

  /** Inlines simple methods, like getters */
  private final PassFactory inlineSimpleMethods =
      PassFactory.builder()
          .setName("inlineSimpleMethods")
          .setRunInFixedPointLoop(true)
          .setInternalFactory(InlineSimpleMethods::new)
          .build();

  /** Kills dead assignments. */
  private final PassFactory deadAssignmentsElimination =
      PassFactory.builder()
          .setName(PassNames.DEAD_ASSIGNMENT_ELIMINATION)
          .setRunInFixedPointLoop(true)
          .setInternalFactory(DeadAssignmentsElimination::new)
          .build();

  /** Kills dead property assignments. */
  private final PassFactory deadPropertyAssignmentElimination =
      PassFactory.builder()
          .setName("deadPropertyAssignmentElimination")
          .setRunInFixedPointLoop(true)
          .setInternalFactory(DeadPropertyAssignmentElimination::new)
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
          .build();

  /** Inlines constant properties. */
  private final PassFactory inlineProperties =
      PassFactory.builder()
          .setName(PassNames.INLINE_PROPERTIES)
          .setRunInFixedPointLoop(true)
          .setInternalFactory(InlineProperties::new)
          .build();

  /** Isolates injected polyfills & references from the global scope */
  private final PassFactory isolatePolyfills =
      PassFactory.builder()
          .setName("IsolatePolyfills")
          .setInternalFactory(IsolatePolyfills::new)
          .build();

  private final PassFactory removeUnusedCode =
      PassFactory.builder()
          .setName(PassNames.REMOVE_UNUSED_CODE)
          .setRunInFixedPointLoop(true)
          .setInternalFactory(
              (compiler) ->
                  new RemoveUnusedCode.Builder(compiler)
                      .removeLocalVars(options.removeUnusedLocalVars)
                      .removeGlobals(options.removeUnusedVars)
                      .preserveFunctionExpressionNames(false)
                      .removeUnusedPrototypeProperties(options.removeUnusedPrototypeProperties)
                      .removeUnusedThisProperties(options.isRemoveUnusedClassProperties())
                      .removeUnusedObjectDefinePropertiesDefinitions(
                          options.isRemoveUnusedClassProperties())
                      // If we are forcing injection of some library code, don't remove polyfills.
                      // Otherwise, we might end up removing polyfills the user specifically asked
                      // to include.
                      .removeUnusedPolyfills(options.forceLibraryInjection.isEmpty())
                      .assumeGettersArePure(options.getAssumeGettersArePure())
                      .build())
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
                      compiler.getChunkGraph(),
                      options.parentChunkCanSeeSymbolsDeclaredInChildren))
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
                      /* canModifyExterns= */ false, // remove this
                      options.crossChunkCodeMotionNoStubMethods))
          .build();

  /** A data-flow based variable inliner. */
  private final PassFactory flowSensitiveInlineVariables =
      PassFactory.builder()
          .setName(PassNames.FLOW_SENSITIVE_INLINE_VARIABLES)
          .setInternalFactory(FlowSensitiveInlineVariables::new)
          .build();

  /** Uses register-allocation algorithms to use fewer variables. */
  private final PassFactory coalesceVariableNames =
      PassFactory.builder()
          .setName(PassNames.COALESCE_VARIABLE_NAMES)
          .setInternalFactory(
              (compiler) -> new CoalesceVariableNames(compiler, options.generatePseudoNames))
          .build();

  /** Collapses assignment expressions (e.g., {@code x = 3; y = x;} becomes {@code y = x = 3;}. */
  private final PassFactory exploitAssign =
      PassFactory.builder()
          .setName(PassNames.EXPLOIT_ASSIGN)
          .setInternalFactory(
              (compiler) ->
                  new PeepholeOptimizationsPass(
                      compiler, PassNames.EXPLOIT_ASSIGN, new ExploitAssigns()))
          .build();

  /** Collapses variable declarations (e.g., {@code var x; var y;} becomes {@code var x,y;}. */
  private final PassFactory collapseVariableDeclarations =
      PassFactory.builder()
          .setName(PassNames.COLLAPSE_VARIABLE_DECLARATIONS)
          .setInternalFactory(CollapseVariableDeclarations::new)
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
                  case USE_CHUNK_TEMP:
                    pattern = Pattern.USE_CHUNK_TEMP;
                    break;
                  case USE_IIFE:
                    pattern = Pattern.USE_IIFE;
                    break;
                  default:
                    throw new IllegalStateException("unexpected");
                }

                return new ExtractPrototypeMemberDeclarations(compiler, pattern);
              })
          .build();

  /** Rewrites common function definitions to be more compact. */
  private final PassFactory rewriteFunctionExpressions =
      PassFactory.builder()
          .setName(PassNames.REWRITE_FUNCTION_EXPRESSIONS)
          .setInternalFactory(FunctionRewriter::new)
          .build();

  /** Collapses functions to not use the VAR keyword. */
  private final PassFactory collapseAnonymousFunctions =
      PassFactory.builder()
          .setName(PassNames.COLLAPSE_ANONYMOUS_FUNCTIONS)
          .setInternalFactory(CollapseAnonymousFunctions::new)
          .build();

  /**
   * Moves function declarations to the top to simulate actual hoisting and rewrites block scope
   * declarations to 'var'.
   */
  private final PassFactory rewriteGlobalDeclarationsForTryCatchWrapping =
      PassFactory.builder()
          .setName("rewriteGlobalDeclarationsForTryCatchWrapping")
          .setInternalFactory(RewriteGlobalDeclarationsForTryCatchWrapping::new)
          .build();

  /** Alias string literals with global variables, to reduce code size. */
  private final PassFactory aliasStrings =
      PassFactory.builder()
          .setName("aliasStrings")
          .setInternalFactory(
              (compiler) ->
                  new AliasStrings(
                      compiler,
                      compiler.getChunkGraph(),
                      options.outputJsStringUsage,
                      options.getAliasStringsMode()))
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
                      options.getPropertyReservedNamingNonFirstChars(),
                      compiler.getExternProperties()))
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
          .build();

  private final PassFactory normalize =
      PassFactory.builder()
          .setName(PassNames.NORMALIZE)
          .setInternalFactory(Normalize::createNormalizeForOptimizations)
          .build();

  private final PassFactory externExports =
      PassFactory.builder()
          .setName(PassNames.EXTERN_EXPORTS)
          .setInternalFactory(ExternExportsPass::new)
          .build();

  /** Denormalize the AST for code generation. */
  private final PassFactory denormalize =
      PassFactory.builder()
          .setName("denormalize")
          .setInternalFactory(
              (compiler) -> new Denormalize(compiler, options.getOutputFeatureSet()))
          .build();

  /** Inverting name normalization. */
  private final PassFactory invertContextualRenaming =
      PassFactory.builder()
          .setName("invertContextualRenaming")
          .setInternalFactory(MakeDeclaredNamesUnique::getContextualRenameInverter)
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
          .build();

  /** Inlines calls to property renaming functions. */
  private final PassFactory removePropertyRenamingCalls =
      PassFactory.builder()
          .setName("removePropertyRenamingCalls")
          .setInternalFactory(RemovePropertyRenamingCalls::new)
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
          .build();

  private VariableMap runVariableRenaming(
      AbstractCompiler compiler, VariableMap prevVariableMap, Node externs, Node root) {
    char[] reservedChars = null;
    Set<String> reservedNames = new LinkedHashSet<>();
    if (options.renamePrefixNamespace != null) {
      // don't use the prefix name as a global symbol.
      reservedNames.add(options.renamePrefixNamespace);
    }
    reservedNames.addAll(compiler.getExportedNames());
    reservedNames.addAll(ParserRunner.getReservedVars());
    RenameVars rn =
        new RenameVars(
            compiler,
            options.renamePrefix,
            options.variableRenaming == VariableRenamingPolicy.LOCAL,
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
      PassFactory.builder().setName("renameLabels").setInternalFactory(RenameLabels::new).build();

  /** Convert bracket access to dot access */
  private final PassFactory convertToDottedProperties =
      PassFactory.builder()
          .setName(PassNames.CONVERT_TO_DOTTED_PROPERTIES)
          .setInternalFactory(ConvertToDottedProperties::new)
          .build();

  private final PassFactory checkAstValidity =
      PassFactory.builder()
          .setName("checkAstValidity")
          .setInternalFactory(AstValidator::new)
          .build();

  /** Checks that all variables are defined. */
  private final PassFactory varCheckValidity =
      PassFactory.builder()
          .setName("varCheckValidity")
          .setInternalFactory((compiler) -> new VarCheck(compiler, true))
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
                      options.getInstrumentForCoverageOption(),
                      options.getProductionInstrumentationArrayName()))
          .build();

  private final PassFactory gatherExternPropertiesCheck =
      createGatherExternProperties(GatherExternProperties.Mode.CHECK);

  private final PassFactory gatherExternPropertiesOptimize =
      createGatherExternProperties(GatherExternProperties.Mode.OPTIMIZE);

  /** Extern property names gathering pass. */
  private final PassFactory createGatherExternProperties(GatherExternProperties.Mode mode) {
    return PassFactory.builder()
        .setName("gatherExternProperties")
        .setInternalFactory((compiler) -> new GatherExternProperties(compiler, mode))
        .build();
  }

  /**
   * Runs custom passes that are designated to run at a particular time.
   *
   * <p>TODO(nickreid): Deprecate this API
   */
  private PassFactory getCustomPasses(final CustomPassExecutionTime executionTime) {
    return PassFactory.builder()
        .setName("runCustomPasses")
        .setInternalFactory((compiler) -> runInSerial(options.customPasses.get(executionTime)))
        .build();
  }

  /** Create a compiler pass that runs the given passes in serial. */
  private static CompilerPass runInSerial(final Collection<CompilerPass> passes) {
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        for (CompilerPass pass : passes) {
          pass.process(externs, root);
        }
      }
    };
  }

  @VisibleForTesting
  static Map<String, Node> getAdditionalReplacements(CompilerOptions options) {
    Map<String, Node> additionalReplacements = new LinkedHashMap<>();

    if (options.markAsCompiled || options.closurePass) {
      additionalReplacements.put(COMPILED_CONSTANT_NAME, IR.trueNode());
    }

    if (options.closurePass && options.locale != null && !options.doLateLocalization()) {
      additionalReplacements.put(CLOSURE_LOCALE_CONSTANT_NAME, IR.string(options.locale));
    }

    return additionalReplacements;
  }

  /** Rewrites Polymer({}) */
  private final PassFactory polymerPass =
      PassFactory.builder()
          .setName("polymerPass")
          .setInternalFactory(
              (compiler) ->
                  new PolymerPass(
                      compiler,
                      compiler.getOptions().polymerVersion,
                      compiler.getOptions().polymerExportPolicy,
                      compiler.getOptions().propertyRenaming
                          == PropertyRenamingPolicy.ALL_UNQUOTED))
          .build();

  private final PassFactory chromePass =
      PassFactory.builder().setName("chromePass").setInternalFactory(ChromePass::new).build();

  /** Rewrites J2CL constructs to be more optimizable. */
  private final PassFactory j2clConstantHoisterPass =
      PassFactory.builder()
          .setName("j2clConstantHoisterPass")
          .setRunInFixedPointLoop(true)
          .setInternalFactory(J2clConstantHoisterPass::new)
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
          .build();

  /** Rewrites J2CL constructs to be more optimizable. */
  private final PassFactory j2clPropertyInlinerPass =
      PassFactory.builder()
          .setName("j2clPropertyInlinerPass")
          .setInternalFactory(J2clPropertyInlinerPass::new)
          .build();

  /** Rewrites J2CL constructs to be more optimizable. */
  private final PassFactory j2clPass =
      PassFactory.builder().setName("j2clPass").setInternalFactory(J2clPass::new).build();

  /** Rewrites J2CL constructs to be more optimizable. */
  private final PassFactory j2clUtilGetDefineRewriterPass =
      PassFactory.builder()
          .setName("j2clUtilGetDefineRewriterPass")
          .setInternalFactory(J2clUtilGetDefineRewriterPass::new)
          .build();

  private final PassFactory j2clAssertRemovalPass =
      PassFactory.builder()
          .setName("j2clAssertRemovalPass")
          .setInternalFactory(J2clAssertRemovalPass::new)
          .build();

  private final PassFactory j2clSourceFileChecker =
      PassFactory.builder()
          .setName("j2clSourceFileChecker")
          .setInternalFactory(J2clSourceFileChecker::new)
          .build();

  private final PassFactory j2clChecksPass =
      PassFactory.builder()
          .setName("j2clChecksPass")
          .setInternalFactory(J2clChecksPass::new)
          .build();

  private final PassFactory checkConformance =
      PassFactory.builder()
          .setName(PassNames.CHECK_CONFORMANCE)
          .setInternalFactory(
              (compiler) -> new CheckConformance(compiler, options.getConformanceConfigs()))
          .build();

  private final PassFactory removeCastNodes =
      PassFactory.builder()
          .setName("removeCastNodes")
          .setInternalFactory(RemoveCastNodes::new)
          .build();

  /** Convert types from type-checking to optimization "colors" */
  private final PassFactory typesToColors =
      PassFactory.builder()
          .setName("typesToColors")
          .setInternalFactory(
              (compiler) ->
                  (externs, js) -> {
                    new ConvertTypesToColors(
                            compiler,
                            compiler.getOptions().shouldSerializeExtraDebugInfo()
                                ? SerializationOptions.INCLUDE_DEBUG_INFO
                                : SerializationOptions.SKIP_DEBUG_INFO)
                        .process(externs, js);

                    compiler.setLifeCycleStage(LifeCycleStage.COLORS_AND_SIMPLIFIED_JSDOC);
                  })
          .build();

  /** Emit a TypedAST into of the current compilation. */
  private final PassFactory serializeTypedAst =
      PassFactory.builder()
          .setName("serializeTypedAst")
          .setInternalFactory(
              (compiler) ->
                  SerializeTypedAstPass.createFromPath(
                      compiler,
                      options.getTypedAstOutputFile(),
                      compiler.getOptions().shouldSerializeExtraDebugInfo()
                          ? SerializationOptions.INCLUDE_DEBUG_INFO
                          : SerializationOptions.SKIP_DEBUG_INFO))
          .build();

  private final PassFactory removeUnnecessarySyntheticExterns =
      PassFactory.builder()
          .setName("removeUnnecessarySyntheticExterns")
          .setInternalFactory(RemoveUnnecessarySyntheticExterns::new)
          .build();

  /** Optimizations that output ES2015 features. */
  private final PassFactory optimizeToEs6 =
      PassFactory.builder()
          .setName("optimizeToEs6")
          .setInternalFactory(SubstituteEs6Syntax::new)
          .build();

  /** Rewrites goog.module in whitespace only mode */
  private final PassFactory whitespaceWrapGoogModules =
      PassFactory.builder()
          .setName("whitespaceWrapGoogModules")
          .setInternalFactory(WhitespaceWrapGoogModules::new)
          .build();

  private final PassFactory rewriteCommonJsModules =
      PassFactory.builder()
          .setName(PassNames.REWRITE_COMMON_JS_MODULES)
          .setInternalFactory(ProcessCommonJSModules::new)
          .build();

  private final PassFactory rewriteScriptsToEs6Modules =
      PassFactory.builder()
          .setName(PassNames.REWRITE_SCRIPTS_TO_ES6_MODULES)
          .setInternalFactory(Es6RewriteScriptsToModules::new)
          .build();

  private final PassFactory gatherModuleMetadataPass =
      PassFactory.builder()
          .setName(PassNames.GATHER_MODULE_METADATA)
          .setInternalFactory(
              (compiler) -> {
                // Force creation of the synthetic input so that we create metadata for it
                compiler.getSynthesizedExternsInput();
                return new GatherModuleMetadata(
                    compiler, options.getProcessCommonJSModules(), options.moduleResolutionMode);
              })
          .build();

  private final PassFactory createModuleMapPass =
      PassFactory.builder()
          .setName(PassNames.CREATE_MODULE_MAP)
          .setInternalFactory(
              (compiler) -> new ModuleMapCreator(compiler, compiler.getModuleMetadataMap()))
          // does not look at AST
          .build();

  private final PassFactory gatherGettersAndSetters =
      PassFactory.builder()
          .setName(PassNames.GATHER_GETTERS_AND_SETTERS)
          .setInternalFactory(GatherGetterAndSetterProperties::new)
          .build();

  // this pass is just adding script without looking at the AST so it should accept all features
  private final PassFactory addSyntheticScript =
      PassFactory.builder()
          .setName("ADD_SYNTHETIC_SCRIPT")
          .setInternalFactory(
              (compiler) -> (externs, js) -> compiler.initializeSyntheticCodeInput())
          .build();

  private final PassFactory removeSyntheticScript =
      PassFactory.builder()
          .setName("REMOVE_SYNTHETIC_SCRIPT")
          .setInternalFactory((compiler) -> (externs, js) -> compiler.removeSyntheticCodeInput())
          .build();

  private final PassFactory mergeSyntheticScript =
      PassFactory.builder()
          .setName("MERGE_SYNTHETIC_SCRIPT")
          .setInternalFactory((compiler) -> (externs, js) -> compiler.mergeSyntheticCodeInput())
          .build();

  /** Rewrites ES modules import paths to be browser compliant */
  private static final PassFactory forbidDynamicImportUsage =
      PassFactory.builder()
          .setName("FORBID_DYNAMIC_IMPORT")
          .setInternalFactory(ForbidDynamicImportUsage::new)
          .build();

  /** Rewrites Dynamic Import Expressions */
  private static final PassFactory rewriteDynamicImports =
      PassFactory.builder()
          .setName("REWRITE_DYNAMIC_IMPORT")
          .setInternalFactory(
              (compiler) ->
                  new RewriteDynamicImports(
                      compiler,
                      compiler.getOptions().getDynamicImportAlias(),
                      compiler.getOptions().getChunkOutputType()))
          .build();

  /** Replace locale values with stubs that can survive optimizations and be replaced afterwards. */
  private final PassFactory protectLocaleData =
      PassFactory.builder()
          .setName("protectLocaleData")
          .setInternalFactory(
              (compiler) ->
                  (externs, root) -> new ProtectGoogLocale(compiler).process(externs, root))
          .build();

  /** Replace locale data stubs with the values for a locale. */
  private final PassFactory substituteLocaleData =
      PassFactory.builder()
          .setName("SubstituteLocaleData")
          .setInternalFactory(
              (compiler) ->
                  new CompilerPass() {
                    @Override
                    public void process(Node externs, Node root) {
                      new LocaleDataPasses.LocaleSubstitutions(
                              compiler, compiler.getOptions().locale)
                          .process(externs, root);
                    }
                  })
          .build();
}
