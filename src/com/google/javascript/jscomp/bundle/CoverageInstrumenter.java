/*
 * Copyright 2017 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.bundle;

import com.google.errorprone.annotations.Immutable;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.InstrumentOption;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.VariableRenamingPolicy;
import java.util.Optional;

/** A source transformer for instrumenting code for coverage data collection. */
@Immutable
public class CoverageInstrumenter extends CompilerBasedTransformer {

  public CoverageInstrumenter(CompilerBasedTransformer.CompilerSupplier compilerSupplier) {
    super(compilerSupplier);
  }
  /**
   * Supply options for coverage.
   */
  public static class CompilerSupplier extends CompilerBasedTransformer.CompilerSupplier {
    @Override
    protected void setOptions(CompilerOptions options) {
      options.setCoalesceVariableNames(false);
      options.setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT5);
      options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT_NEXT);
      options.setStrictModeInput(false);
      // Setting the path to any non-null value will trigger source map generation.
      // CompilerBasedTransformer attachs the sourcemap to the result.
      options.setSourceMapOutputPath("/dev/null");
      options.setVariableRenaming(VariableRenamingPolicy.OFF);
      options.setInstrumentForCoverageOption(InstrumentOption.LINE_ONLY);
      options.setInstrumentForCoverageOnly(true);
    }

    @Override
    public boolean transformed(Result result) {
      return true;
    }
  }

  @Override
  public Optional<String> getRuntime() {
    return Optional.empty();
  }

  @Override
  public String getTranformationName() {
    return "Coverage Instrumentation";
  }

  public static CoverageInstrumenter.CompilerSupplier compilerSupplier() {
    return new CoverageInstrumenter.CompilerSupplier();
  }

  public static final CoverageInstrumenter INSTRUMENTER =
      new CoverageInstrumenter(CoverageInstrumenter.compilerSupplier());
}
