/*
 * Copyright 2020 The Closure Compiler Authors.
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.CompilerTestCase.lines;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.ParseException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link com.google.javascript.jscomp.ConformancePassConfig} */
@RunWith(JUnit4.class)
public class ConformanceIntegrationTest {

  @Test
  public void allowsValidCode() {
    Compiler compiler = runChecks(createCompilerOptions(), "const x = 0;");

    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(compiler.getErrors()).isEmpty();
  }

  @Test
  public void bansCallsToName() {
    Compiler compiler = runChecks(createCompilerOptions(), "bannedName('3');");

    assertThat(compiler.getErrors()).isEmpty();
    assertThat(compiler.getWarnings()).hasSize(1);
    assertThat(compiler.getWarnings().get(0).getType())
        .isEqualTo(CheckConformance.CONFORMANCE_VIOLATION);
  }

  @Test
  public void bansCallsToName_inEsModule() {
    CompilerOptions options = createCompilerOptions();
    options.setBadRewriteModulesBeforeTypecheckingThatWeWantToGetRidOf(false);

    Compiler compiler = runChecks(options, "let x = 1; bannedName('3'); export {};");

    assertThat(compiler.getErrors()).isEmpty();
    assertThat(compiler.getWarnings()).hasSize(1);
    assertThat(compiler.getWarnings().get(0).getType())
        .isEqualTo(CheckConformance.CONFORMANCE_VIOLATION);
  }

  private static final String DEFAULT_CONFORMANCE =
      lines(
          "requirement: {",
          "  type: BANNED_NAME",
          "  value: 'bannedName'",
          "   error_message: 'bannedName is not allowed'",
          "}",
          "");

  private static CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);
    ConformanceConfig.Builder builder = ConformanceConfig.newBuilder();
    try {
      TextFormat.merge(DEFAULT_CONFORMANCE, builder);
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
    options.setConformanceConfig(builder.build());
    return options;
  }

  private static Compiler runChecks(CompilerOptions options, String input) {
    Compiler compiler = new Compiler();

    compiler.setPassConfig(new ConformancePassConfig(new DefaultPassConfig(options)));
    compiler.init(
        ImmutableList.of(), ImmutableList.of(SourceFile.fromCode("test.js", input)), options);
    compiler.parseForCompilation();
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(compiler.getErrors()).isEmpty();

    compiler.check();

    return compiler;
  }
}
