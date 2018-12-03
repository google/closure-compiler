/*
 * Copyright 2018 The Closure Compiler Authors.
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests the hotswap functionality of {@link GatherModuleMetadata}. */

@RunWith(JUnit4.class)
public class GatherModuleMetadataReplaceScriptTest extends BaseReplaceScriptTestCase {
  @Test
  public void testAddScript() {
    CompilerOptions options = getOptions();

    String src = "goog.provide('Bar');";
    String newSrc = "goog.provide('Baz');";

    Compiler compiler =
        runAddScript(
            options,
            ImmutableList.of(src),
            /* expectedCompileErrors= */ 0,
            /* expectedCompileWarnings= */ 0,
            newSrc,
            /* flushResults= */ false);

    assertThat(compiler.getModuleMetadataMap().getModulesByGoogNamespace().keySet())
        .containsExactly("Bar", "Baz");
  }

  @Test
  public void testAddNestedModule() {
    CompilerOptions options = getOptions();

    String src = "";
    String newSrc =
        lines(
            "goog.loadModule(function(exports) {", //
            "  goog.module('new.module');",
            "  return exports;",
            "});");

    Compiler compiler =
        runAddScript(
            options,
            ImmutableList.of(src),
            /* expectedCompileErrors= */ 0,
            /* expectedCompileWarnings= */ 0,
            newSrc,
            /* flushResults= */ false);

    assertThat(compiler.getModuleMetadataMap().getModulesByGoogNamespace().keySet())
        .containsExactly("new.module");
  }

  @Test
  public void testRemoveNestedModule() {
    CompilerOptions options = getOptions();

    String src =
        lines(
            "goog.loadModule(function(exports) {", //
            "  goog.module('a.module');",
            "  return exports;",
            "});",
            "goog.loadModule(function(exports) {", //
            "  goog.module('b.module');",
            "  return exports;",
            "});");
    String newSrc =
        lines(
            "goog.loadModule(function(exports) {", //
            "  goog.module('a.module');",
            "  return exports;",
            "});");

    Compiler compiler =
        runReplaceScript(
            options,
            ImmutableList.of(src),
            /* expectedCompileErrors= */ 0,
            /* expectedCompileWarnings= */ 0,
            newSrc,
            /* newSourceInd= */ 0,
            /* flushResults= */ false);

    assertThat(compiler.getModuleMetadataMap().getModulesByGoogNamespace().keySet())
        .containsExactly("a.module");
  }

  @Test
  public void testChangeNamespace() {
    CompilerOptions options = getOptions();

    String src = "goog.provide('Bar');";
    String newSrc = "goog.provide('Baz');";

    Compiler compiler =
        runReplaceScript(
            options,
            ImmutableList.of(src),
            /* expectedCompileErrors= */ 0,
            /* expectedCompileWarnings= */ 0,
            newSrc,
            /* newSourceInd= */ 0,
            /* flushResults= */ false);

    assertThat(compiler.getModuleMetadataMap().getModulesByGoogNamespace().keySet())
        .containsExactly("Baz");
  }

  @Test
  public void testChangeModuleType() {
    CompilerOptions options = getOptions();

    String src = "goog.provide('Bar');";
    String newSrc = "goog.module('Bar');";

    Compiler compiler =
        runReplaceScript(
            options,
            ImmutableList.of(src),
            /* expectedCompileErrors= */ 0,
            /* expectedCompileWarnings= */ 0,
            newSrc,
            /* newSourceInd= */ 0,
            /* flushResults= */ false);

    assertThat(
            compiler
                .getModuleMetadataMap()
                .getModulesByGoogNamespace()
                .get("Bar")
                .isNonLegacyGoogModule())
        .isTrue();
  }
}
