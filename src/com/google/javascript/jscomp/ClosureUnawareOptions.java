/*
 * Copyright 2025 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.js.RuntimeJsLibManager;
import java.nio.file.Path;

/**
 * Creates a partial copy of a {@link CompilerOptions} object for use in {@link
 * TranspileAndOptimizeClosureUnawareCode}.
 *
 * <p>During a regular compilation (i.e. "main compilation"), we have a special pass that will
 * instantiate a new Compiler object with a new CompilerOptions, created by this class. We then run
 * optimizations/finalizations in that pass. This is called a "nested compilation" or
 * "sub-compilation".
 *
 * <p>As a general principle:
 *
 * <ul>
 *   <li>always copy options that are related to code printing or debugging.
 *   <li>sometimes copy options related to transpilation: the nested compilation should try to match
 *       the main compilation's target language out, but we don't actually support automatic
 *       polyfill injection and we have some unique runtime library handling.
 *   <li>never copy options that are related to optimization. The purpose of {@code @closureUnaware}
 *       code blocks is to protect arbitrary 3P code from breaking under advanced JSCompiler
 *       optimizations. Instead, we explicitly set the relevant, safe optimization options a preset
 *       list of options, ignoring the main compilation's settings.
 *   <li>never copy options related to check passes or parsing: the nested compilation doesn't
 *       actually run the parser or any checks (besides VarCheck), so there's no need to explicitly
 *       enumerate and copy these.
 */
class ClosureUnawareOptions {
  private final CompilerOptions shadowOptions = new CompilerOptions();
  private final CompilerOptions original;

  static CompilerOptions convert(CompilerOptions original) {
    var options = new ClosureUnawareOptions(original);
    return options.toCompilerOptions();
  }

  private ClosureUnawareOptions(CompilerOptions original) {
    this.original = original;
  }

  private CompilerOptions toCompilerOptions() {
    setTranspilationOptions();
    setSafeOptimizationAssumptions();
    copyOutputOptions();
    copyDebugOptions();
    return shadowOptions;
  }

  private void setTranspilationOptions() {
    shadowOptions.setRewritePolyfills(false); // for now, we ignore polyfills that may be needed.
    switch (original.getRuntimeLibraryMode()) {
      case NO_OP ->
          shadowOptions.setRuntimeLibraryMode(RuntimeJsLibManager.RuntimeLibraryMode.NO_OP);
      case RECORD_ONLY, RECORD_AND_VALIDATE_FIELDS, INJECT ->
          shadowOptions.setRuntimeLibraryMode(
              RuntimeJsLibManager.RuntimeLibraryMode.EXTERN_FIELD_NAMES);
      case EXTERN_FIELD_NAMES -> throw new AssertionError();
    }
    shadowOptions.setSkipNonTranspilationPasses(original.getSkipNonTranspilationPasses());
    shadowOptions.setEs6SubclassTranspilation(
        CompilerOptions.Es6SubclassTranspilation.SAFE_REFLECT_CONSTRUCT);
    shadowOptions.setOutputFeatureSet(original.getOutputFeatureSet());
    shadowOptions.setInstrumentAsyncContext(original.getInstrumentAsyncContext());

    // To aid rollout of @closureUnaware transpilation, ignore warnings about untranspilable
    // features for now. (Any project using @closureUnaware today (i.e. early 2026) gets zero
    // transpilation, so rolling out with these suppressions is an incremental improvement.)

    // DiagnosticGroups.UNTRANSPILABLE_FEATURES: features JSCompiler will never transpile but
    // can pass through unchanged to the output, such as newer regex syntax.
    shadowOptions.setWarningLevel(DiagnosticGroups.UNTRANSPILABLE_FEATURES, CheckLevel.OFF);
  }

  private void setSafeOptimizationAssumptions() {
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(shadowOptions);
    shadowOptions.setRemoveUnusedVariables(CompilerOptions.Reach.ALL);

    shadowOptions.setComputeFunctionSideEffects(false); // unsafe for properties.
    shadowOptions.setCodingConvention(CodingConventions.getDefault());
    shadowOptions.setRemoveClosureAsserts(false);

    shadowOptions.setAssumeClosuresOnlyCaptureReferences(false);
    shadowOptions.setAssumeGettersArePure(false);
    shadowOptions.setAssumePropertiesAreStaticallyAnalyzable(false);
    shadowOptions.setAssumeStaticInheritanceIsNotUsed(false);
    shadowOptions.setAssumeStrictThis(false);

    if (original.getMaxFunctionSizeAfterInlining()
        != CompilerOptions.UNLIMITED_FUN_SIZE_AFTER_INLINING) {
      shadowOptions.setMaxFunctionSizeAfterInlining(original.getMaxFunctionSizeAfterInlining());
    }
    shadowOptions.setUseSizeHeuristicToStopOptimizationLoop(
        original.shouldUseSizeHeuristicToStopOptimizationLoop());
    shadowOptions.setMaxOptimizationLoopIterations(original.getMaxOptimizationLoopIterations());

    shadowOptions.setPreferStableNames(original.shouldPreferStableNames());
    shadowOptions.setInputPropertyMap(original.getInputPropertyMap());
    shadowOptions.setInputVariableMap(original.getInputVariableMap());
  }

  private void copyOutputOptions() {
    shadowOptions.setEmitUseStrict(original.shouldEmitUseStrict());
    shadowOptions.setOutputCharset(original.getOutputCharset());

    shadowOptions.setGeneratePseudoNames(original.shouldGeneratePseudoNames());
    shadowOptions.setLineBreak(original.shouldAddLineBreak());
    shadowOptions.setLineLengthThreshold(original.getLineLengthThreshold());
    shadowOptions.setPreferSingleQuotes(original.shouldPreferSingleQuotes());
    shadowOptions.setPrettyPrint(original.isPrettyPrint());

    shadowOptions.setErrorFormat(original.getErrorFormat());
    shadowOptions.setErrorHandler(original.getErrorHandler());
    shadowOptions.setColorizeErrorOutput(original.shouldColorizeErrorOutput());
  }

  private void copyDebugOptions() {
    shadowOptions.setTracerMode(original.getTracerMode());
    shadowOptions.setTracerOutput(original.getTracerOutput());
    shadowOptions.setDevMode(original.getDevMode());

    shadowOptions.setPrintSourceAfterEachPass(original.shouldPrintSourceAfterEachPass());
    shadowOptions.setFilesToPrintAfterEachPassRegexList(
        original.getFilesToPrintAfterEachPassRegexList());
    shadowOptions.setChunksToPrintAfterEachPassRegexList(
        original.getChunksToPrintAfterEachPassRegexList());
    shadowOptions.setPrintInputDelimiter(original.shouldPrintInputDelimiter());
    shadowOptions.setQnameUsesToPrintAfterEachPassList(
        original.getQnameUsesToPrintAfterEachPassList());

    shadowOptions.setInputDelimiter(original.getInputDelimiter());
    shadowOptions.setUseOriginalNamesInOutput(original.getUseOriginalNamesInOutput());

    shadowOptions.setDebugLogFilter(original.getDebugLogFilter());
    Path debugLogDirectory = original.getDebugLogDirectory();
    if (debugLogDirectory != null) {
      shadowOptions.setDebugLogDirectory(
          debugLogDirectory.resolve("./TranspileAndOptimizeClosureUnaware"));
    }
  }
}
