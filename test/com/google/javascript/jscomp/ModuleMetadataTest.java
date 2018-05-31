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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.ModuleMetadata.Module;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.rhino.Node;


public final class ModuleMetadataTest extends CompilerTestCase {
  ModuleMetadata metadata;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    // ECMASCRIPT5 to trigger module processing after parsing.
    setLanguage(LanguageMode.ECMASCRIPT_2015, LanguageMode.ECMASCRIPT5);
    enableRunTypeCheckAfterProcessing();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    metadata =
        new ModuleMetadata(compiler, /* processCommonJsModules */ true, ResolutionMode.BROWSER);
    return (Node externs, Node root) -> metadata.process(externs, root);
  }

  public void testGoogProvide() {
    testSame("goog.provide('my.provide');");
    assertThat(metadata.getModulesByGoogNamespace().keySet()).containsExactly("my.provide");
    assertThat(metadata.getModulesByPath().keySet()).contains("testcode");
    Module m = metadata.getModulesByGoogNamespace().get("my.provide");
    assertThat(m.getGoogNamespaces()).containsExactly("my.provide");
    assertThat(m.isGoogProvide()).isTrue();
  }

  public void testMultipleGoogProvide() {
    testSame("goog.provide('my.first.provide'); goog.provide('my.second.provide');");
    assertThat(metadata.getModulesByGoogNamespace().keySet())
        .containsExactly("my.first.provide", "my.second.provide");
    assertThat(metadata.getModulesByPath().keySet()).contains("testcode");
    Module m = metadata.getModulesByGoogNamespace().get("my.first.provide");
    assertThat(m.getGoogNamespaces()).containsExactly("my.first.provide", "my.second.provide");
    assertThat(m.isGoogProvide()).isTrue();

    m = metadata.getModulesByGoogNamespace().get("my.second.provide");
    assertThat(m.getGoogNamespaces()).containsExactly("my.first.provide", "my.second.provide");
    assertThat(m.isGoogProvide()).isTrue();

    m = metadata.getModulesByPath().get("testcode");
    assertThat(m.getGoogNamespaces()).containsExactly("my.first.provide", "my.second.provide");
    assertThat(m.isGoogProvide()).isTrue();
  }

  public void testGoogModule() {
    testSame("goog.module('my.module');");
    assertThat(metadata.getModulesByGoogNamespace().keySet()).containsExactly("my.module");
    Module m = metadata.getModulesByGoogNamespace().get("my.module");
    assertThat(m.getGoogNamespaces()).containsExactly("my.module");
    assertThat(m.isGoogProvide()).isFalse();
    assertThat(m.isGoogModule()).isTrue();
    assertThat(m.isNonLegacyGoogModule()).isTrue();
    assertThat(m.isLegacyGoogModule()).isFalse();
  }

  public void testGoogModuleWithDefaultExport() {
    // exports = 0; on its own is CommonJS!
    testSame("goog.module('my.module'); exports = 0;");
    assertThat(metadata.getModulesByGoogNamespace().keySet()).containsExactly("my.module");
    Module m = metadata.getModulesByGoogNamespace().get("my.module");
    assertThat(m.getGoogNamespaces()).containsExactly("my.module");
    assertThat(m.isGoogProvide()).isFalse();
    assertThat(m.isGoogModule()).isTrue();
    assertThat(m.isNonLegacyGoogModule()).isTrue();
    assertThat(m.isLegacyGoogModule()).isFalse();
  }

  public void testLegacyGoogModule() {
    testSame("goog.module('my.module'); goog.module.declareLegacyNamespace();");
    assertThat(metadata.getModulesByGoogNamespace().keySet()).containsExactly("my.module");
    Module m = metadata.getModulesByGoogNamespace().get("my.module");
    assertThat(m.getGoogNamespaces()).containsExactly("my.module");
    assertThat(m.isGoogProvide()).isFalse();
    assertThat(m.isGoogModule()).isTrue();
    assertThat(m.isNonLegacyGoogModule()).isFalse();
    assertThat(m.isLegacyGoogModule()).isTrue();
  }

  public void testLoadModule() {
    testSame("goog.loadModule(function() { goog.module('my.module'); });");
    assertThat(metadata.getModulesByGoogNamespace().keySet()).containsExactly("my.module");

    Module m = metadata.getModulesByGoogNamespace().get("my.module");
    assertThat(m.getGoogNamespaces()).containsExactly("my.module");
    assertThat(m.isNonLegacyGoogModule()).isTrue();
    assertThat(m.getPath()).isNull();

    m = metadata.getModulesByPath().get("testcode");
    assertThat(m.getGoogNamespaces()).isEmpty();
    assertThat(m.isScript()).isTrue();
  }

  public void testEs6Module() {
    testSame("export var x;");
    assertThat(metadata.getModulesByGoogNamespace().keySet()).isEmpty();
    assertThat(metadata.getModulesByPath().keySet()).contains("testcode");
    Module m = metadata.getModulesByPath().get("testcode");
    assertThat(m.getGoogNamespaces()).isEmpty();
    assertThat(m.isEs6Module()).isTrue();
  }

  public void testEs6ModuleDeclareNamespace() {
    testSame("export var x; goog.module.declareNamespace('my.module');");
    assertThat(metadata.getModulesByGoogNamespace().keySet()).containsExactly("my.module");
    Module m = metadata.getModulesByGoogNamespace().get("my.module");
    assertThat(m.getGoogNamespaces()).containsExactly("my.module");
    assertThat(m.isEs6Module()).isTrue();
    assertThat(m.isGoogModule()).isFalse();
  }

  public void testCommonJsModule() {
    testSame("exports = 0;");
    assertThat(metadata.getModulesByGoogNamespace()).isEmpty();
    Module m = metadata.getModulesByPath().get("testcode");
    assertThat(m.isCommonJs()).isTrue();
  }

  public void testDuplicateProvides() {
    testError(
        new String[] {"goog.provide('duplciated');", "goog.provide('duplciated');"},
        ClosureRewriteModule.DUPLICATE_NAMESPACE);
  }

  public void testDuplicateProvideAndGoogModule() {
    testError(
        new String[] {"goog.provide('duplciated');", "goog.module('duplciated');"},
        ClosureRewriteModule.DUPLICATE_NAMESPACE);
    testError(
        new String[] {"goog.module('duplciated');", "goog.provide('duplciated');"},
        ClosureRewriteModule.DUPLICATE_MODULE);
  }

  public void testDuplicateProvideAndEs6Module() {
    testError(
        new String[] {
          "goog.provide('duplciated');", "export {}; goog.module.declareNamespace('duplciated');"
        },
        ClosureRewriteModule.DUPLICATE_NAMESPACE);
    testError(
        new String[] {
          "export {}; goog.module.declareNamespace('duplciated');", "goog.provide('duplciated');"
        },
        ClosureRewriteModule.DUPLICATE_MODULE);
  }

  public void testDuplicateGoogModules() {
    testError(
        new String[] {"goog.module('duplciated');", "goog.module('duplciated');"},
        ClosureRewriteModule.DUPLICATE_MODULE);
  }

  public void testDuplicateGoogAndEs6Module() {
    testError(
        new String[] {
            "goog.module('duplciated');", "export {}; goog.module.declareNamespace('duplciated');"
        },
        ClosureRewriteModule.DUPLICATE_MODULE);
    testError(
        new String[] {
            "export {}; goog.module.declareNamespace('duplciated');", "goog.module('duplciated');"
        },
        ClosureRewriteModule.DUPLICATE_MODULE);
  }

  public void testDuplicatEs6Modules() {
    testError(
        new String[] {
          "export {}; goog.module.declareNamespace('duplciated');",
          "export {}; goog.module.declareNamespace('duplciated');"
        },
        ClosureRewriteModule.DUPLICATE_MODULE);
    testError(
        new String[] {
          "export {}; goog.module.declareNamespace('duplciated');",
          "export {}; goog.module.declareNamespace('duplciated');"
        },
        ClosureRewriteModule.DUPLICATE_MODULE);
  }
}
