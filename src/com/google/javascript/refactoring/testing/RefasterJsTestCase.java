/*
 * Copyright 2016 The Closure Compiler Authors.
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
package com.google.javascript.refactoring.testing;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.google.common.truth.Correspondence;
import com.google.javascript.jscomp.CommandLineRunner;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.refactoring.ApplySuggestedFixes;
import com.google.javascript.refactoring.RefactoringDriver;
import com.google.javascript.refactoring.RefasterJsScanner;
import com.google.javascript.refactoring.SuggestedFix;
import java.io.File;
import java.io.IOException;
import java.util.List;

/** RefasterJsTestCase */
@AutoValue
public abstract class RefasterJsTestCase {

  public static Builder builder() {
    return new AutoValue_RefasterJsTestCase.Builder();
  }

  /** Path of the resource containing the RefasterJs template to apply. */
  public abstract String getTemplatePath();

  /** Path of the resource containing the input JS file. */
  public abstract String getInputPath();

  /** Paths of the resources containing the expected output JS files. */
  public abstract ImmutableSet<String> getExpectedOutputPaths();

  /** Paths of the resources containing other JS files to include. */
  public abstract ImmutableSet<String> getAdditionalSourcePaths();

  /** Builder */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setTemplatePath(String x);

    public abstract Builder setInputPath(String x);

    public final Builder addExpectedOutputPath(String x) {
      this.expectedOutputPathsBuilder().add(x);
      return this;
    }

    public final Builder addAdditionalSourcePath(String x) {
      this.additionalSourcePathsBuilder().add(x);
      return this;
    }

    public final void test() {
      try {
        this.build().test();
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    }

    abstract ImmutableSet.Builder<String> expectedOutputPathsBuilder();

    abstract ImmutableSet.Builder<String> additionalSourcePathsBuilder();

    abstract RefasterJsTestCase build();
  }

  private void test() throws IOException {
    // Given
    RefasterJsScanner scanner = new RefasterJsScanner();
    scanner.loadRefasterJsTemplate(this.getTemplatePath());

    ImmutableList<String> expectedOutputs =
        this.getExpectedOutputPaths().stream()
            .map(RefasterJsTestCase::slurpFile)
            .collect(toImmutableList());

    RefactoringDriver.Builder driverBuilder =
        new RefactoringDriver.Builder()
            .addExterns(CommandLineRunner.getBuiltinExterns(CompilerOptions.Environment.BROWSER));
    for (String additionalSourcePath : this.getAdditionalSourcePaths()) {
      driverBuilder.addInputsFromFile(additionalSourcePath);
    }
    RefactoringDriver driver = driverBuilder.addInputsFromFile(this.getInputPath()).build();

    // When
    List<SuggestedFix> fixes = driver.drive(scanner);
    ImmutableList<String> newCode =
        ApplySuggestedFixes.applyAllSuggestedFixChoicesToCode(
                fixes, ImmutableMap.of(this.getInputPath(), slurpFile(this.getInputPath())))
            .stream()
            .map(m -> m.get(this.getInputPath()))
            .collect(toImmutableList());

    // Then
    assertThat(driver.getCompiler().getErrors()).isEmpty();
    assertThat(driver.getCompiler().getWarnings()).isEmpty();
    assertThat(newCode)
        .comparingElementsUsing(IGNORING_WHITESPACE_CORRESPONDENCE)
        .containsExactlyElementsIn(expectedOutputs);
  }

  private static String slurpFile(String originalFile) {
    try {
      return Files.asCharSource(new File(originalFile), UTF_8).read();
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private static final Correspondence<String, String> IGNORING_WHITESPACE_CORRESPONDENCE =
      Correspondence.transforming(
          RefasterJsTestCase::replaceTrailingWhitespace,
          RefasterJsTestCase::replaceTrailingWhitespace,
          "equals (except for whitespace)");

  private static String replaceTrailingWhitespace(String contents) {
    return contents.replaceAll("[ \t]*\n", "\n");
  }

  RefasterJsTestCase() {
    // Only subclassed by AutoValue.
  }
}
