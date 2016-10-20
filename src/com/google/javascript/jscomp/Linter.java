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

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.refactoring.ApplySuggestedFixes;
import com.google.javascript.refactoring.FixingErrorManager;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

/**
 * Minimal binary that just runs the "lint" checks which can be run on a single file at a time.
 * This means some checks in the lintChecks DiagnosticGroup are skipped, since they depend on
 * type information.
 */
public class Linter {
  @Option(name = "--fix", usage = "Fix lint warnings automatically")
  private boolean fix = false;

  @Argument private List<String> files = new ArrayList<>();

  public static void main(String[] args) throws IOException, CmdLineException {
    new Linter().run(args);
  }

  private void run(String[] args) throws IOException, CmdLineException {
    CmdLineParser parser = new CmdLineParser(this);
    parser.parseArgument(args);

    for (String filename : files) {
      if (fix) {
        fix(filename);
      } else {
        lint(filename);
      }
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

    FixingErrorManager errorManager = null;
    if (fix) {
      errorManager = new FixingErrorManager();
      compiler.setErrorManager(errorManager);
      errorManager.setCompiler(compiler);
    }

    CompilerOptions options = new CompilerOptions();
    options.setLanguage(LanguageMode.ECMASCRIPT8);

    // For a full compile, this would cause a crash, as the method name implies. But the passes
    // in LintPassConfig can all handle untranspiled ES6.
    options.setSkipTranspilationAndCrash(true);

    options.setParseJsDocDocumentation(INCLUDE_DESCRIPTIONS_WITH_WHITESPACE);
    options.setCodingConvention(new GoogleCodingConvention());

    // Even though we're not running the typechecker, enable the checkTypes DiagnosticGroup, since
    // it contains some warnings we do want to report, such as JSDoc parse warnings.
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.WARNING);

    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    options.setWarningLevel(DiagnosticGroups.STRICT_MISSING_REQUIRE, CheckLevel.ERROR);
    options.setWarningLevel(DiagnosticGroups.EXTRA_REQUIRE, CheckLevel.ERROR);
    options.setWarningLevel(DiagnosticGroups.USE_OF_GOOG_BASE, CheckLevel.WARNING);
    options.setSummaryDetailLevel(0);
    compiler.setPassConfig(new LintPassConfig(options));
    compiler.disableThreads();
    SourceFile externs = SourceFile.fromCode("<Linter externs>", "");
    compiler.compile(ImmutableList.<SourceFile>of(externs), ImmutableList.of(file), options);
    if (fix) {
      ApplySuggestedFixes.applySuggestedFixesToFiles(errorManager.getAllFixes());
    }
  }
}
