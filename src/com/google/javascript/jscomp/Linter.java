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

import static com.google.common.collect.ObjectArrays.concat;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.refactoring.ApplySuggestedFixes;
import com.google.javascript.refactoring.ErrorToFixMapper;
import com.google.javascript.refactoring.SuggestedFix;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal binary that just runs the "lint" checks which can be run on a single file at a time.
 * This means some checks in the lintChecks DiagnosticGroup are skipped, since they depend on
 * type information.
 */
public class Linter {
  public static void main(String[] args) throws IOException {
    for (String filename : args) {
      // TODO(tbreisacher): Add a command line flag that causes this
      // to call fix() instead of lint().
      lint(filename);
    }
  }


  static void lint(String filename) throws IOException {
    lint(Paths.get(filename), false);
  }

  static void fix(String filename) throws IOException {
    lint(Paths.get(filename), true);
  }

  private static void lint(Path path, boolean fix) throws IOException {
    SourceFile file = SourceFile.fromFile(path.toString());
    Compiler compiler = new Compiler(System.out);
    CompilerOptions options = new CompilerOptions();
    options.setLanguage(LanguageMode.ECMASCRIPT6_STRICT);

    // For a full compile, this would cause a crash, as the method name implies. But the passes
    // in LintPassConfig can all handle untranspiled ES6.
    options.setSkipTranspilationAndCrash(true);

    options.setIdeMode(true);
    options.setCodingConvention(new GoogleCodingConvention());

    // Even though we're not running the typechecker, enable the checkTypes DiagnosticGroup, since
    // it contains some warnings we do want to report, such as JSDoc parse warnings.
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.WARNING);

    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    options.setWarningLevel(DiagnosticGroups.MISSING_REQUIRE, CheckLevel.WARNING);
    options.setWarningLevel(DiagnosticGroups.EXTRA_REQUIRE, CheckLevel.WARNING);
    compiler.setPassConfig(new LintPassConfig(options));
    compiler.disableThreads();
    SourceFile externs = SourceFile.fromCode("<Linter externs>", "");
    compiler.compile(ImmutableList.<SourceFile>of(externs), ImmutableList.of(file), options);
    if (fix) {
      List<SuggestedFix> fixes = new ArrayList<>();
      for (JSError warning : concat(compiler.getErrors(), compiler.getWarnings(), JSError.class)) {
        SuggestedFix suggestedFix = ErrorToFixMapper.getFixForJsError(warning, compiler);
        if (suggestedFix != null) {
          fixes.add(suggestedFix);
        }
      }
      ApplySuggestedFixes.applySuggestedFixesToFiles(fixes);
    }
  }
}
