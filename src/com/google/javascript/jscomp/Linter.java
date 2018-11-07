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

import static com.google.javascript.jscomp.parsing.Config.JsDocParsing.INCLUDE_DESCRIPTIONS_WITH_WHITESPACE;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.parsing.Config.JsDocParsing;
import com.google.javascript.refactoring.ApplySuggestedFixes;
import com.google.javascript.refactoring.FixingErrorManager;
import com.google.javascript.refactoring.SuggestedFix;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

/**
 * Tool for running just the lint checks which can be run on a single file at a time.
 *
 * <p>Run from {@link LinterMain}
 */
@GwtIncompatible("Unnecessary")
public final class Linter {
  // Don't try to apply fixes anymore, after trying this many times.
  // This is to avoid the unlikely event of an infinite loop of fixes.
  static final int MAX_FIXES = 5;

  private Linter() {}

  static void lint(String filename) throws IOException {
    lint(Paths.get(filename), new Compiler(System.out));
  }

  static void lint(Path path, Compiler compiler) throws IOException {
    SourceFile file = SourceFile.fromFile(path.toString());
    CompilerOptions options = new CompilerOptions();
    options.setLanguage(LanguageMode.ECMASCRIPT_NEXT);
    options.setParseJsDocDocumentation(JsDocParsing.INCLUDE_DESCRIPTIONS_WITH_WHITESPACE);
    options.setPreserveDetailedSourceInfo(true);

    // These are necessary to make sure that suggested fixes are printed correctly.
    options.setPrettyPrint(true);
    options.setPreserveTypeAnnotations(true);
    options.setPreferSingleQuotes(true);
    options.setEmitUseStrict(false);

    options.setParseJsDocDocumentation(INCLUDE_DESCRIPTIONS_WITH_WHITESPACE);
    options.setCodingConvention(new GoogleCodingConvention());

    // Even though we're not running the typechecker, enable the checkTypes DiagnosticGroup, since
    // it contains some warnings we do want to report, such as JSDoc parse warnings.
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.WARNING);

    options.setWarningLevel(DiagnosticGroups.JSDOC_MISSING_TYPE, CheckLevel.ERROR);
    options.setWarningLevel(DiagnosticGroups.MISPLACED_MSG_ANNOTATION, CheckLevel.WARNING);
    options.setWarningLevel(DiagnosticGroups.UNNECESSARY_ESCAPE, CheckLevel.WARNING);
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    options.setWarningLevel(DiagnosticGroups.UNUSED_LOCAL_VARIABLE, CheckLevel.WARNING);
    options.setWarningLevel(DiagnosticGroups.UNUSED_PRIVATE_PROPERTY, CheckLevel.WARNING);
    options.setWarningLevel(DiagnosticGroups.STRICT_MISSING_REQUIRE, CheckLevel.ERROR);
    options.setWarningLevel(DiagnosticGroups.EXTRA_REQUIRE, CheckLevel.ERROR);
    options.setWarningLevel(DiagnosticGroups.USE_OF_GOOG_BASE, CheckLevel.WARNING);
    options.setWarningLevel(DiagnosticGroups.MISPLACED_SUPPRESS, CheckLevel.WARNING);
    options.setSummaryDetailLevel(0);
    compiler.setPassConfig(new LintPassConfig(options));
    compiler.disableThreads();
    SourceFile externs = SourceFile.fromCode("<Linter externs>", "");
    compiler.compile(ImmutableList.<SourceFile>of(externs), ImmutableList.of(file), options);
  }

  /**
   * Keep applying fixes to the given file until no more fixes can be found, or until fixes have
   * been applied {@code MAX_FIXES} times.
   */
  static void fixRepeatedly(String filename) throws IOException {
    fixRepeatedly(filename, ImmutableSet.of());
  }

  /**
   * Keep applying fixes to the given file until no more fixes can be found, or until fixes have
   * been applied {@code MAX_FIXES} times.
   */
  static void fixRepeatedly(String filename, ImmutableSet<DiagnosticType> unfixableErrors)
      throws IOException {
    for (int i = 0; i < MAX_FIXES; i++) {
      if (!fix(filename, unfixableErrors)) {
        break;
      }
    }
  }


  /** @return Whether any fixes were applied. */
  private static boolean fix(String filename, ImmutableSet<DiagnosticType> unfixableErrors)
      throws IOException {
    Compiler compiler = new Compiler(System.out);
    FixingErrorManager errorManager = new FixingErrorManager(unfixableErrors);
    compiler.setErrorManager(errorManager);
    errorManager.setCompiler(compiler);

    lint(Paths.get(filename), compiler);

    Collection<SuggestedFix> fixes = errorManager.getAllFixes();
    if (!fixes.isEmpty()) {
      ApplySuggestedFixes.applySuggestedFixesToFiles(fixes);
      return true;
    }
    return false;
  }
}
