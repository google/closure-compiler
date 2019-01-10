/*
 * Copyright 2015 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.PassFactory.HotSwapPassFactory;
import com.google.javascript.jscomp.lint.CheckDuplicateCase;
import com.google.javascript.jscomp.lint.CheckEmptyStatements;
import com.google.javascript.jscomp.lint.CheckEnums;
import com.google.javascript.jscomp.lint.CheckInterfaces;
import com.google.javascript.jscomp.lint.CheckJSDocStyle;
import com.google.javascript.jscomp.lint.CheckMissingSemicolon;
import com.google.javascript.jscomp.lint.CheckNullabilityModifiers;
import com.google.javascript.jscomp.lint.CheckPrimitiveAsObject;
import com.google.javascript.jscomp.lint.CheckPrototypeProperties;
import com.google.javascript.jscomp.lint.CheckRequiresAndProvidesSorted;
import com.google.javascript.jscomp.lint.CheckUnusedLabels;
import com.google.javascript.jscomp.lint.CheckUselessBlocks;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import java.util.List;

/**
 * A PassConfig for the standalone linter, which runs on a single file at a time. This runs a
 * similar set of checks to what you would get when running the compiler with the lintChecks
 * DiagnosticGroup enabled, but some of the lint checks depend on type information, which is not
 * available when looking at a single file, so those are omitted here.
 */
class LintPassConfig extends PassConfig.PassConfigDelegate {
  LintPassConfig(CompilerOptions options) {
    super(new DefaultPassConfig(options));
  }

  @Override
  protected List<PassFactory> getChecks() {
    return ImmutableList.of(
        gatherModuleMetadataPass,
        earlyLintChecks,
        checkRequires,
        variableReferenceCheck,
        closureRewriteClass,
        lateLintChecks);
  }

  @Override
  protected List<PassFactory> getOptimizations() {
    return ImmutableList.of();
  }

  private final HotSwapPassFactory gatherModuleMetadataPass =
      new HotSwapPassFactory(PassNames.GATHER_MODULE_METADATA) {
        @Override
        protected HotSwapCompilerPass create(AbstractCompiler compiler) {
          return new GatherModuleMetadata(
              compiler,
              compiler.getOptions().getProcessCommonJSModules(),
              compiler.getOptions().getModuleResolutionMode());
        }

        @Override
        protected FeatureSet featureSet() {
          return FeatureSet.latest().withoutTypes();
        }
      };

  private final PassFactory earlyLintChecks =
      new PassFactory("earlyLintChecks", true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new CombinedCompilerPass(
              compiler,
              ImmutableList.of(
                  new CheckDuplicateCase(compiler),
                  new CheckEmptyStatements(compiler),
                  new CheckEnums(compiler),
                  new CheckJSDocStyle(compiler),
                  new CheckJSDoc(compiler),
                  new CheckMissingSemicolon(compiler),
                  new CheckSuper(compiler),
                  new CheckPrimitiveAsObject(compiler),
                  new ClosureCheckModule(compiler, compiler.getModuleMetadataMap()),
                  new CheckNullabilityModifiers(compiler),
                  new CheckRequiresAndProvidesSorted(compiler),
                  new CheckSideEffects(
                      compiler, /* report */ true, /* protectSideEffectFreeCode */ false),
                  new CheckUnusedLabels(compiler),
                  new CheckUselessBlocks(compiler)));
        }

        @Override
        protected FeatureSet featureSet() {
          return FeatureSet.latest().withoutTypes();
        }
      };

  private final PassFactory variableReferenceCheck =
      new PassFactory("variableReferenceCheck", true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new VariableReferenceCheck(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return FeatureSet.latest().withoutTypes();
        }
      };

  private final PassFactory checkRequires =
      new PassFactory("checkMissingAndExtraRequires", true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new CheckMissingAndExtraRequires(
              compiler, CheckMissingAndExtraRequires.Mode.SINGLE_FILE);
        }

        @Override
        protected FeatureSet featureSet() {
          return FeatureSet.latest().withoutTypes();
        }
      };

  private final PassFactory closureRewriteClass =
      new PassFactory(PassNames.CLOSURE_REWRITE_CLASS, true) {
        @Override
        protected HotSwapCompilerPass create(AbstractCompiler compiler) {
          return new ClosureRewriteClass(compiler);
        }

        @Override
        protected FeatureSet featureSet() {
          return FeatureSet.latest().withoutTypes();
        }
      };

  private final PassFactory lateLintChecks =
      new PassFactory("lateLintChecks", true) {
        @Override
        protected CompilerPass create(AbstractCompiler compiler) {
          return new CombinedCompilerPass(
              compiler,
              ImmutableList.of(
                  new CheckInterfaces(compiler), new CheckPrototypeProperties(compiler)));
        }

        @Override
        protected FeatureSet featureSet() {
          return FeatureSet.latest().withoutTypes();
        }
      };
}
