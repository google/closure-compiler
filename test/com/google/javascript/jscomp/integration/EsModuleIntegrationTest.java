/*
 * Copyright 2021 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.integration;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.DiagnosticGroups;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for ES modules */
@RunWith(JUnit4.class)
public final class EsModuleIntegrationTest extends IntegrationTestCase {

  @Test
  public void testConvertChunksToModules_oneScript() {
    CompilerOptions options = new CompilerOptions();
    options.setChunkOutputType(CompilerOptions.ChunkOutputType.ES_MODULES);

    test(options, "console.log('test');", "console.log('test'); export {};");
  }

  @Test
  public void testConvertChunksToModules_twoScripts() {
    CompilerOptions options = new CompilerOptions();
    options.setChunkOutputType(CompilerOptions.ChunkOutputType.ES_MODULES);
    options.setWarningLevel(DiagnosticGroups.MODULE_LOAD, CheckLevel.OFF);

    test(
        options,
        new String[] {"console.log('one');", "console.log('two');"},
        new String[] {"console.log('one'); export {};", "import './m0.js'; console.log('two');"});
  }

  // Reproduction test for b/563543213 (https://github.com/google/closure-compiler/issues/4344):
  // Duplicate top-level `var` declarations in ES modules currently trigger a fatal
  // JSC_ILLEGAL_MODULE_RENAMING_CONFLICT when type checking is enabled.
  @Test
  public void testDuplicateVarInEsModule_basic() {
    CompilerOptions options = new CompilerOptions();
    options.setCheckTypes(true);
    options.setChunkOutputType(CompilerOptions.ChunkOutputType.ES_MODULES);

    test(
        options,
        """
        import.meta.url;
        var a = 1;
        var a = 2;
        use(a);
        """,
        DiagnosticGroups.forName("checkLevelOffDoNotUseDoNotUseDoNotUseDoNotUseDoNotUse"));
  }

  // Reproduction test for b/563543213 (https://github.com/google/closure-compiler/issues/4344):
  // Repeated `for (var i = ...)` loops in ES modules currently trigger a fatal
  // JSC_ILLEGAL_MODULE_RENAMING_CONFLICT when type checking is enabled.
  @Test
  public void testDuplicateVarInEsModule_forLoop() {
    CompilerOptions options = new CompilerOptions();
    options.setCheckTypes(true);
    options.setChunkOutputType(CompilerOptions.ChunkOutputType.ES_MODULES);

    test(
        options,
        """
        import.meta.url;
        for (var i = 0; i < 10; ++i) { use(i); }
        for (var i = 0; i < 10; ++i) { use(i); }
        """,
        DiagnosticGroups.forName("checkLevelOffDoNotUseDoNotUseDoNotUseDoNotUseDoNotUse"));
  }
}
