/*
 * Copyright 2014 The Closure Compiler Authors.
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

package com.google.javascript.refactoring;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.BlackHoleErrorManager;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.DependencyOptions;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.parsing.Config;
import com.google.javascript.rhino.Node;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Primary driver of a refactoring. This class collects the inputs, runs the refactoring over
 * the compiled input, and then collects the suggested fixes based on the refactoring.
 *
 * @author mknichel@google.com (Mark Knichel)
 */
public final class RefactoringDriver {

  private final Compiler compiler;
  private final Node rootNode;

  private RefactoringDriver(
      List<SourceFile> inputs,
      List<SourceFile> externs,
      CompilerOptions compilerOptions) {
    this.compiler = createCompiler(inputs, externs, compilerOptions);
    this.rootNode = this.compiler.getRoot();
  }

  /** Run a refactoring and return any suggested fixes as a result. */
  public List<SuggestedFix> drive(Scanner scanner, Pattern includeFilePattern) {
    JsFlumeCallback callback = new JsFlumeCallback(scanner, includeFilePattern);
    NodeTraversal.traverse(compiler, rootNode, callback);
    List<SuggestedFix> fixes = callback.getFixes();
    fixes.addAll(scanner.processAllMatches(callback.getMatches()));
    return fixes;
  }

  /** Run a refactoring and return any suggested fixes as a result. */
  public List<SuggestedFix> drive(Scanner scanner) {
    return drive(scanner, null);
  }

  public Compiler getCompiler() {
    return compiler;
  }

  private static Compiler createCompiler(
      List<SourceFile> inputs, List<SourceFile> externs, CompilerOptions compilerOptions) {
    Compiler compiler = new Compiler(new BlackHoleErrorManager());
    compiler.disableThreads();
    compiler.compile(externs, inputs, compilerOptions);
    return compiler;
  }

  // TODO(tbreisacher): Make this package-private by refactoring tests so they
  // don't need to call it directly.
  @VisibleForTesting
  public static CompilerOptions getCompilerOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setSummaryDetailLevel(0);

    options.setDependencyOptions(DependencyOptions.sortOnly());

    options.setChecksOnly(true);
    options.setContinueAfterErrors(true);
    options.setParseJsDocDocumentation(Config.JsDocParsing.INCLUDE_DESCRIPTIONS_NO_WHITESPACE);
    options.setCheckSuspiciousCode(true);
    options.setCheckSymbols(true);
    options.setCheckTypes(true);
    options.setBrokenClosureRequiresLevel(CheckLevel.OFF);
    // TODO(bangert): Remove this -- we want to rewrite code before closure syntax is removed.
    // Unfortunately, setClosurePass is required, or code doesn't type check.
    options.setClosurePass(true);
    options.setGenerateExports(true);
    options.setPreserveClosurePrimitives(true);

    options.setWarningLevel(DiagnosticGroups.STRICT_MISSING_REQUIRE, CheckLevel.WARNING);
    options.setWarningLevel(DiagnosticGroups.EXTRA_REQUIRE, CheckLevel.WARNING);
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);

    return options;
  }

  public static class Builder {
    private static final Function<String, SourceFile> TO_SOURCE_FILE_FN =
        file -> new SourceFile.Builder().buildFromFile(file);

    private final ImmutableList.Builder<SourceFile> inputs = ImmutableList.builder();
    private final ImmutableList.Builder<SourceFile> externs = ImmutableList.builder();
    private CompilerOptions compilerOptions = getCompilerOptions();

    public Builder() {}

    public Builder addExternsFromFile(String filename) {
      externs.add(SourceFile.fromFile(filename));
      return this;
    }

    public Builder addExternsFromFile(Iterable<String> externs) {
      this.externs.addAll(Lists.transform(ImmutableList.copyOf(externs), TO_SOURCE_FILE_FN));
      return this;
    }

    public Builder addExternsFromCode(String code) {
      externs.add(SourceFile.fromCode("externs", code));
      return this;
    }

    public Builder addExterns(Iterable<SourceFile> externs) {
      this.externs.addAll(externs);
      return this;
    }

    public Builder addInputsFromFile(String filename) {
      inputs.add(SourceFile.fromFile(filename));
      return this;
    }

    public Builder addInputsFromFile(Iterable<String> inputs) {
      this.inputs.addAll(Lists.transform(ImmutableList.copyOf(inputs), TO_SOURCE_FILE_FN));
      return this;
    }

    public Builder addInputsFromCode(String code) {
      return addInputsFromCode(code, "input");
    }

    public Builder addInputsFromCode(String code, String filename) {
      inputs.add(SourceFile.fromCode(filename, code));
      return this;
    }

    public Builder addInputs(Iterable<SourceFile> inputs) {
      this.inputs.addAll(inputs);
      return this;
    }

    public Builder withCompilerOptions(CompilerOptions compilerOptions) {
      this.compilerOptions = checkNotNull(compilerOptions);
      return this;
    }

    public RefactoringDriver build() {
      return new RefactoringDriver(inputs.build(), externs.build(), compilerOptions);
    }
  }
}
