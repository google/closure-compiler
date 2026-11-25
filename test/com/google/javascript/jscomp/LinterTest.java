/*
 * Copyright 2026 The Closure Compiler Authors.
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.jscomp.lint.CheckExtraRequires;
import com.google.javascript.jscomp.lint.CheckMissingSemicolon;
import com.google.javascript.jscomp.lint.CheckVar;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link Linter} and {@link LintPassConfig}. */
@RunWith(JUnit4.class)
public final class LinterTest {
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void testLint_reportsSingleFileWarningsAndErrors() throws Exception {
    Path jsFile = tempFolder.newFile("sample.js").toPath();
    Files.writeString(
        jsFile,
        """
        goog.module('sample');
        const Unused = goog.require('other.Unused');
        /**
         * @param {number} x
         * @return {number}
         */
        function f(x) {
          var y = x + 1
          return y;
        }
        exports = {f};
        """,
        UTF_8);

    Compiler compiler = new Compiler();
    Linter linter =
        Linter.builder()
            .withModuleResolutionMode(ResolutionMode.BROWSER)
            .withBrowserResolverPrefixReplacements(ImmutableMap.of())
            .build();
    linter.lint(jsFile, compiler);

    assertThat(compiler.getErrors().stream().map(JSError::getType).collect(toImmutableList()))
        .containsExactly(CheckExtraRequires.EXTRA_REQUIRE_WARNING);
    assertThat(compiler.getWarnings().stream().map(JSError::getType).collect(toImmutableList()))
        .containsAtLeast(CheckMissingSemicolon.MISSING_SEMICOLON, CheckVar.VAR);
  }

  @Test
  public void testFixRepeatedly_appliesSuggestedFixesInPlace() throws Exception {
    Path jsFile = tempFolder.newFile("fixable.js").toPath();
    Files.writeString(
        jsFile,
        """
        goog.module('fixable');
        const Unused = goog.require('other.Unused');
        const x = 1
        exports = {x};
        """,
        UTF_8);

    Linter linter = Linter.builder().build();
    linter.fixRepeatedly(jsFile.toString(), ImmutableSet.of());

    String updated = Files.readString(jsFile, UTF_8);
    assertThat(updated).doesNotContain("other.Unused");
    assertThat(updated).contains("const x = 1;");
  }
}
