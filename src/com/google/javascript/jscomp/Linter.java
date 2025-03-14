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

import static com.google.javascript.jscomp.parsing.Config.JsDocParsing.INCLUDE_ALL_COMMENTS;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.jscomp.parsing.Config.JsDocParsing;
import com.google.javascript.refactoring.ApplySuggestedFixes;
import com.google.javascript.refactoring.FixingErrorManager;
import com.google.javascript.refactoring.SuggestedFix;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Tool for running just the lint checks which can be run on a single file at a time.
 *
 * <p>Run from {@link LinterMain}
 */
public final class Linter {

  /**
   * Builder for a Linter that allows some customization.
   *
   * <p>Note: A builder is not designed to generate multiple Linters, just a single one. The
   * underlying CompilerOptions it builds is never copied defensively, so futher edits to this
   * builder will affect previously built Linters.
   */
  public static final class Builder {
    private final CompilerOptions options;

    private Builder() {
      options = new CompilerOptions();
      options.setLanguage(LanguageMode.ECMASCRIPT_NEXT);
      options.setParseJsDocDocumentation(JsDocParsing.INCLUDE_ALL_COMMENTS);
      options.setPreserveDetailedSourceInfo(true);

      // These are necessary to make sure that suggested fixes are printed correctly.
      options.setPrettyPrint(true);
      options.setPreserveTypeAnnotations(true);
      options.setPreferSingleQuotes(true);
      options.setEmitUseStrict(false);

      options.setParseJsDocDocumentation(INCLUDE_ALL_COMMENTS);
      options.setCodingConvention(new GoogleCodingConvention());

      // Even though we're not running the typechecker, enable the checkTypes DiagnosticGroup, since
      // it contains some warnings we do want to report, such as JSDoc parse warnings.
      options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.WARNING);

      options.setWarningLevel(DiagnosticGroups.JSDOC_MISSING_TYPE, CheckLevel.ERROR);
      options.setWarningLevel(DiagnosticGroups.MISPLACED_MSG_ANNOTATION, CheckLevel.WARNING);
      options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
      options.setWarningLevel(DiagnosticGroups.UNUSED_LOCAL_VARIABLE, CheckLevel.WARNING);
      options.setWarningLevel(DiagnosticGroups.EXTRA_REQUIRE, CheckLevel.ERROR);
      options.setWarningLevel(DiagnosticGroups.MISPLACED_SUPPRESS, CheckLevel.WARNING);
      options.setWarningLevel(DiagnosticGroups.TYPE_IMPORT_CODE_REFERENCES, CheckLevel.ERROR);
      options.setWarningLevel(DiagnosticGroups.MODULE_LOAD, CheckLevel.OFF);
      options.setWarningLevel(DiagnosticGroups.STRICT_MODULE_CHECKS, CheckLevel.WARNING);
      options.setSummaryDetailLevel(0);
    }

    @CanIgnoreReturnValue
    public Builder withModuleResolutionMode(ResolutionMode moduleResolutionMode) {
      options.setModuleResolutionMode(moduleResolutionMode);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder withBrowserResolverPrefixReplacements(
        ImmutableMap<String, String> replacements) {
      options.setBrowserResolverPrefixReplacements(replacements);
      return this;
    }

    public Linter build() {
      return new Linter(options);
    }
  }

  // Don't try to apply fixes anymore, after trying this many times.
  // This is to avoid the unlikely event of an infinite loop of fixes.
  static final int MAX_FIXES = 5;

  private final CompilerOptions options;

  private Linter(CompilerOptions options) {
    this.options = options;
  }

  static Builder builder() {
    return new Builder();
  }

  void lint(String filename) {
    lint(Path.of(filename), new Compiler(System.out));
  }

  void lint(Path path, Compiler compiler) {
    SourceFile file = SourceFile.fromFile(path.toString());
    compiler.setPassConfig(new LintPassConfig(options));
    compiler.disableThreads();
    SourceFile externs = SourceFile.fromCode("<Linter externs>", "");
    compiler.compile(ImmutableList.<SourceFile>of(externs), ImmutableList.of(file), options);
  }

  /**
   * Keep applying fixes to the given file until no more fixes can be found, or until fixes have
   * been applied {@code MAX_FIXES} times.
   */
  void fixRepeatedly(String filename) throws IOException {
    fixRepeatedly(filename, ImmutableSet.of());
  }

  /**
   * Keep applying fixes to the given file until no more fixes can be found, or until fixes have
   * been applied {@code MAX_FIXES} times.
   */
  void fixRepeatedly(String filename, ImmutableSet<DiagnosticType> unfixableErrors)
      throws IOException {
    for (int i = 0; i < MAX_FIXES; i++) {
      if (!fix(filename, unfixableErrors)) {
        break;
      }
    }
  }

  /** @return Whether any fixes were applied. */
  private boolean fix(String filename, ImmutableSet<DiagnosticType> unfixableErrors)
      throws IOException {
    Compiler compiler = new Compiler(System.out);
    FixingErrorManager errorManager = new FixingErrorManager(unfixableErrors);
    compiler.setErrorManager(errorManager);
    errorManager.setCompiler(compiler);

    lint(Path.of(filename), compiler);

    Collection<SuggestedFix> fixes = errorManager.getSureFixes();
    if (!fixes.isEmpty()) {
      ApplySuggestedFixes.applySuggestedFixesToFiles(fixes);
      return true;
    }
    return false;
  }
}
