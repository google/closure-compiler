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
    setTranspilationOptions();
    setSafeOptimizationAssumptions();
    copyOutputOptions();
    copyDebugOptions();
    return shadowOptions;
  }

  private void setTranspilationOptions() {
    // TODO: b/421971366 - look into adding an option to use Reflect.construct for subclass
    // transpilation, in the
    // case we can't prove it's not a subclass of an ES6 class (not being transpiled)
    shadowOptions.setRewritePolyfills(false); // for now, we ignore polyfills that may be needed.
    switch (original.getRuntimeLibraryMode()) {
      case NO_OP ->
          shadowOptions.setRuntimeLibraryMode(RuntimeJsLibManager.RuntimeLibraryMode.NO_OP);
      case RECORD_ONLY, RECORD_AND_VALIDATE_FIELDS, INJECT ->
          shadowOptions.setRuntimeLibraryMode(
              RuntimeJsLibManager.RuntimeLibraryMode.EXTERN_FIELD_NAMES);
      case EXTERN_FIELD_NAMES -> throw new AssertionError();
    }
    shadowOptions.setOutputFeatureSet(original.getOutputFeatureSet());
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
    shadowOptions.setGeneratePseudoNames(original.generatePseudoNames);
    shadowOptions.setErrorHandler(original.errorHandler);
    shadowOptions.setErrorFormat(original.errorFormat);

    // TODO: lharker - is there any use in propagating the VariableMap & PropertyMap?
  }

  private void copyDebugOptions() {
    shadowOptions.setTracerMode(original.getTracerMode());
    shadowOptions.setDevMode(original.devMode);

    shadowOptions.setPrintSourceAfterEachPass(original.printSourceAfterEachPass);
    shadowOptions.setFilesToPrintAfterEachPassRegexList(
        original.filesToPrintAfterEachPassRegexList);
    shadowOptions.setPrintInputDelimiter(original.printInputDelimiter);
    shadowOptions.setInputDelimiter(original.inputDelimiter);

    shadowOptions.setDebugLogFilter(original.getDebugLogFilter());
    Path debugLogDirectory = original.getDebugLogDirectory();
    if (debugLogDirectory != null) {
      shadowOptions.setDebugLogDirectory(
          debugLogDirectory.resolve("./TranspileAndOptimizeClosureUnaware"));
    }
  }
}
