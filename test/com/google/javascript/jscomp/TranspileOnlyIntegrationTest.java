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
package com.google.javascript.jscomp;

import static com.google.common.truth.Truth.assertThat;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.jspecify.nullness.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test cases for transpile-only mode.
 *
 * <p>This class actually tests several transpilation passes together.
 */
@RunWith(JUnit4.class)
public final class TranspileOnlyIntegrationTest {

  private @Nullable Compiler compiler = null;
  private @Nullable CompilerOptions options = null;

  @Before
  public void init() {
    compiler = new Compiler();
    options = new CompilerOptions();
    options.setLanguage(LanguageMode.ECMASCRIPT_NEXT);
    options.setSkipNonTranspilationPasses(true);
    options.setEmitUseStrict(false);
  }

  @Test
  public void esModuleNoTranspilationForSameLanguageLevel() {
    String js = "export default function fn(){};";
    test(js, js);
  }

  @Test
  public void esModuleTranspilationForDifferentLanguageLevel() {
    String js = "export default function fn() {}";
    String transpiled =
        "function fn$$module$in(){}var module$in={};module$in.default=fn$$module$in;";
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2020);
    test(js, transpiled);
  }

  private void test(String js, String transpiled) {
    compiler.compile(SourceFile.fromCode("ext.js", ""), SourceFile.fromCode("in.js", js), options);
    assertThat(compiler.getErrors()).isEmpty();
    assertThat(compiler.toSource()).isEqualTo(transpiled);
  }
}
