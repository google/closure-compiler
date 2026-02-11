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
    // TODO: b/473813461 - refactor to make sure this class doesn't accidentally skip copying
    // any options.
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
    shadowOptions.setAssumeGettersArePure(false);
    shadowOptions.setCodingConvention(CodingConventions.getDefault());
    shadowOptions.setRemoveClosureAsserts(false);
    shadowOptions.setAssumePropertiesAreStaticallyAnalyzable(false);
  }

  private void copyOutputOptions() {
    shadowOptions.setPrettyPrint(original.isPrettyPrint());
    shadowOptions.setGeneratePseudoNames(original.shouldGeneratePseudoNames());
    shadowOptions.setErrorHandler(original.getErrorHandler());
    shadowOptions.setErrorFormat(original.getErrorFormat());
  }

  private void copyDebugOptions() {
    shadowOptions.setTracerMode(original.getTracerMode());
    shadowOptions.setDevMode(original.getDevMode());

    shadowOptions.setPrintSourceAfterEachPass(original.shouldPrintSourceAfterEachPass());
    shadowOptions.setFilesToPrintAfterEachPassRegexList(
        original.getFilesToPrintAfterEachPassRegexList());
    shadowOptions.setPrintInputDelimiter(original.shouldPrintInputDelimiter());

    shadowOptions.setInputDelimiter(original.getInputDelimiter());

    shadowOptions.setDebugLogFilter(original.getDebugLogFilter());
    Path debugLogDirectory = original.getDebugLogDirectory();
    if (debugLogDirectory != null) {
      shadowOptions.setDebugLogDirectory(
          debugLogDirectory.resolve("./TranspileAndOptimizeClosureUnaware"));
    }
  }
}
