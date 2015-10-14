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
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Minimal binary that just runs the "lint" checks which can be run on a single file at a time.
 * This means some checks in the lintChecks DiagnosticGroup are skipped, since they depend on
 * type information.
 */
public class Linter {
  public static void main(String[] args) throws IOException {
    for (String filename : args) {
      lint(filename);
    }
  }

  private static void lint(String filename) throws IOException {
    lint(Paths.get(filename));
  }

  private static void lint(Path path) throws IOException {
    SourceFile file = SourceFile.fromFile(path.toString());
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT6_STRICT);
    options.setLanguageIn(LanguageMode.ECMASCRIPT5);
    options.setCodingConvention(new GoogleCodingConvention());
    options.setWarningLevel(DiagnosticGroups.MISSING_REQUIRE, CheckLevel.WARNING);
    options.setWarningLevel(DiagnosticGroups.EXTRA_REQUIRE, CheckLevel.WARNING);
    compiler.setPassConfig(new LintPassConfig(options));
    compiler.disableThreads();
    compiler.compile(ImmutableList.<SourceFile>of(), ImmutableList.of(file), options);
  }
}
