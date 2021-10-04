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
import com.google.javascript.jscomp.lint.CheckConstPrivateProperties;
import com.google.javascript.jscomp.lint.CheckConstantCaseNames;
import com.google.javascript.jscomp.lint.CheckDefaultExportOfGoogModule;
import com.google.javascript.jscomp.lint.CheckDuplicateCase;
import com.google.javascript.jscomp.lint.CheckEmptyStatements;
import com.google.javascript.jscomp.lint.CheckEnums;
import com.google.javascript.jscomp.lint.CheckExtraRequires;
import com.google.javascript.jscomp.lint.CheckGoogModuleTypeScriptName;
import com.google.javascript.jscomp.lint.CheckInterfaces;
import com.google.javascript.jscomp.lint.CheckJSDocStyle;
import com.google.javascript.jscomp.lint.CheckMissingSemicolon;
import com.google.javascript.jscomp.lint.CheckNullabilityModifiers;
import com.google.javascript.jscomp.lint.CheckPrimitiveAsObject;
import com.google.javascript.jscomp.lint.CheckPrototypeProperties;
import com.google.javascript.jscomp.lint.CheckProvidesSorted;
import com.google.javascript.jscomp.lint.CheckRequiresSorted;
import com.google.javascript.jscomp.lint.CheckUnusedLabels;
import com.google.javascript.jscomp.lint.CheckUnusedPrivateProperties;
import com.google.javascript.jscomp.lint.CheckUselessBlocks;
import com.google.javascript.jscomp.lint.CheckVar;
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
        variableReferenceCheck,
        closureRewriteClass,
        lateLintChecks);
  }

  @Override
  protected List<PassFactory> getOptimizations() {
    return ImmutableList.of();
  }

  @Override
  protected List<PassFactory> getFinalizations() {
    return ImmutableList.of();
  }

  private final PassFactory gatherModuleMetadataPass =
      PassFactory.builder()
          .setName(PassNames.GATHER_MODULE_METADATA)
          .setInternalFactory(
              (compiler) ->
                  new GatherModuleMetadata(
                      compiler,
                      compiler.getOptions().getProcessCommonJSModules(),
                      compiler.getOptions().getModuleResolutionMode()))
          .setFeatureSet(FeatureSet.latest())
          .build();

  private final PassFactory earlyLintChecks =
      PassFactory.builder()
          .setName("earlyLintChecks")
          .setInternalFactory(
              (compiler) ->
                  new CombinedCompilerPass(
                      compiler,
                      ImmutableList.of(
                          new CheckConstPrivateProperties(compiler),
                          new CheckConstantCaseNames(compiler),
                          new CheckDefaultExportOfGoogModule(compiler),
                          new CheckDuplicateCase(compiler),
                          new CheckEmptyStatements(compiler),
                          new CheckEnums(compiler),
                          new CheckExtraRequires(compiler, options.getUnusedImportsToRemove()),
                          new CheckGoogModuleTypeScriptName(compiler),
                          new CheckJSDocStyle(compiler),
                          new CheckJSDoc(compiler),
                          new CheckMissingSemicolon(compiler),
                          new CheckSuper(compiler),
                          new CheckPrimitiveAsObject(compiler),
                          new ClosureCheckModule(compiler, compiler.getModuleMetadataMap()),
                          new CheckNullabilityModifiers(compiler),
                          new CheckProvidesSorted(CheckProvidesSorted.Mode.COLLECT_AND_REPORT),
                          new CheckRequiresSorted(CheckRequiresSorted.Mode.COLLECT_AND_REPORT),
                          new CheckSideEffects(
                              compiler, /* report */ true, /* protectSideEffectFreeCode */ false),
                          new CheckTypeImportCodeReferences(compiler),
                          new CheckUnusedLabels(compiler),
                          new CheckUnusedPrivateProperties(compiler),
                          new CheckUselessBlocks(compiler),
                          new CheckVar(compiler))))
          .setFeatureSet(FeatureSet.latest())
          .build();

  private final PassFactory variableReferenceCheck =
      PassFactory.builder()
          .setName("variableReferenceCheck")
          .setRunInFixedPointLoop(true)
          .setInternalFactory(VariableReferenceCheck::new)
          .setFeatureSet(FeatureSet.latest())
          .build();

  private final PassFactory closureRewriteClass =
      PassFactory.builder()
          .setName(PassNames.CLOSURE_REWRITE_CLASS)
          .setInternalFactory(ClosureRewriteClass::new)
          .setFeatureSet(FeatureSet.latest())
          .build();

  private final PassFactory lateLintChecks =
      PassFactory.builder()
          .setName("lateLintChecks")
          .setInternalFactory(
              (compiler) ->
                  new CombinedCompilerPass(
                      compiler,
                      ImmutableList.of(
                          new CheckInterfaces(compiler), new CheckPrototypeProperties(compiler))))
          .setFeatureSet(FeatureSet.latest())
          .build();
}
