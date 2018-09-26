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

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.ModuleMetadataMap.ModuleMetadata;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


@RunWith(JUnit4.class)
public final class GatherModuleMetadataTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new GatherModuleMetadata(
        compiler, /* processCommonJsModules= */ true, ResolutionMode.BROWSER);
  }

  private ModuleMetadataMap metadataMap() {
    return getLastCompiler().getModuleMetadataMap();
  }

  @Test
  public void testGoogProvide() {
    testSame("goog.provide('my.provide');");
    assertThat(metadataMap().getModulesByGoogNamespace().keySet()).containsExactly("my.provide");
    assertThat(metadataMap().getModulesByPath().keySet()).contains("testcode");
    ModuleMetadata m = metadataMap().getModulesByGoogNamespace().get("my.provide");
    assertThat(m.googNamespaces()).containsExactly("my.provide");
    assertThat(m.isGoogProvide()).isTrue();
  }

  @Test
  public void testGoogProvideWithGoogDeclaredInOtherFile() {
    // Closure's base.js declare the global goog. It should be ignored when scanning the provide'd
    // file. Only local variables named goog should cause the pass to back off.
    testSame(new String[] {"var goog;", "goog.provide('my.provide');"});
    assertThat(metadataMap().getModulesByGoogNamespace().keySet()).containsExactly("my.provide");
    ModuleMetadata m = metadataMap().getModulesByGoogNamespace().get("my.provide");
    assertThat(m.googNamespaces()).containsExactly("my.provide");
    assertThat(m.isGoogProvide()).isTrue();
  }

  @Test
  public void testSameFileGoogIsIgnored() {
    testSame("var goog; goog.provide('my.provide');");
    assertThat(metadataMap().getModulesByGoogNamespace().keySet()).isEmpty();
    assertThat(metadataMap().getModulesByPath().get("testcode").usesClosure()).isFalse();
  }

  @Test
  public void testLocalGoogIsIgnored() {
    testSame("function bar(goog) { goog.provide('my.provide'); }");
    assertThat(metadataMap().getModulesByGoogNamespace().keySet()).isEmpty();
    assertThat(metadataMap().getModulesByPath().get("testcode").usesClosure()).isFalse();
  }

  @Test
  public void testMultipleGoogProvide() {
    testSame("goog.provide('my.first.provide'); goog.provide('my.second.provide');");
    assertThat(metadataMap().getModulesByGoogNamespace().keySet())
        .containsExactly("my.first.provide", "my.second.provide");
    assertThat(metadataMap().getModulesByPath().keySet()).contains("testcode");
    ModuleMetadata m = metadataMap().getModulesByGoogNamespace().get("my.first.provide");
    assertThat(m.googNamespaces()).containsExactly("my.first.provide", "my.second.provide");
    assertThat(m.isGoogProvide()).isTrue();

    m = metadataMap().getModulesByGoogNamespace().get("my.second.provide");
    assertThat(m.googNamespaces()).containsExactly("my.first.provide", "my.second.provide");
    assertThat(m.isGoogProvide()).isTrue();

    m = metadataMap().getModulesByPath().get("testcode");
    assertThat(m.googNamespaces()).containsExactly("my.first.provide", "my.second.provide");
    assertThat(m.isGoogProvide()).isTrue();
  }

  @Test
  public void testGoogModule() {
    testSame("goog.module('my.module');");
    assertThat(metadataMap().getModulesByGoogNamespace().keySet()).containsExactly("my.module");
    ModuleMetadata m = metadataMap().getModulesByGoogNamespace().get("my.module");
    assertThat(m.googNamespaces()).containsExactly("my.module");
    assertThat(m.isGoogProvide()).isFalse();
    assertThat(m.isGoogModule()).isTrue();
    assertThat(m.isNonLegacyGoogModule()).isTrue();
    assertThat(m.isLegacyGoogModule()).isFalse();
  }

  @Test
  public void testGoogModuleWithDefaultExport() {
    // exports = 0; on its own is CommonJS!
    testSame("goog.module('my.module'); exports = 0;");
    assertThat(metadataMap().getModulesByGoogNamespace().keySet()).containsExactly("my.module");
    ModuleMetadata m = metadataMap().getModulesByGoogNamespace().get("my.module");
    assertThat(m.googNamespaces()).containsExactly("my.module");
    assertThat(m.isGoogProvide()).isFalse();
    assertThat(m.isGoogModule()).isTrue();
    assertThat(m.isNonLegacyGoogModule()).isTrue();
    assertThat(m.isLegacyGoogModule()).isFalse();
  }

  @Test
  public void testLegacyGoogModule() {
    testSame("goog.module('my.module'); goog.module.declareLegacyNamespace();");
    assertThat(metadataMap().getModulesByGoogNamespace().keySet()).containsExactly("my.module");
    ModuleMetadata m = metadataMap().getModulesByGoogNamespace().get("my.module");
    assertThat(m.googNamespaces()).containsExactly("my.module");
    assertThat(m.isGoogProvide()).isFalse();
    assertThat(m.isGoogModule()).isTrue();
    assertThat(m.isNonLegacyGoogModule()).isFalse();
    assertThat(m.isLegacyGoogModule()).isTrue();
  }

  @Test
  public void testLoadModule() {
    testSame("goog.loadModule(function() { goog.module('my.module'); });");
    assertThat(metadataMap().getModulesByGoogNamespace().keySet()).containsExactly("my.module");

    ModuleMetadata m = metadataMap().getModulesByGoogNamespace().get("my.module");
    assertThat(m.googNamespaces()).containsExactly("my.module");
    assertThat(m.isNonLegacyGoogModule()).isTrue();
    assertThat(m.path()).isNull();

    m = metadataMap().getModulesByPath().get("testcode");
    assertThat(m.googNamespaces()).isEmpty();
    assertThat(m.isScript()).isTrue();
  }

  @Test
  public void testEs6Module() {
    testSame("export var x;");
    assertThat(metadataMap().getModulesByGoogNamespace().keySet()).isEmpty();
    assertThat(metadataMap().getModulesByPath().keySet()).contains("testcode");
    ModuleMetadata m = metadataMap().getModulesByPath().get("testcode");
    assertThat(m.googNamespaces()).isEmpty();
    assertThat(m.isEs6Module()).isTrue();
  }

  @Test
  public void testEs6ModuleDeclareModuleId() {
    testSame("export var x; goog.declareModuleId('my.module');");
    assertThat(metadataMap().getModulesByGoogNamespace().keySet()).containsExactly("my.module");
    ModuleMetadata m = metadataMap().getModulesByGoogNamespace().get("my.module");
    assertThat(m.googNamespaces()).containsExactly("my.module");
    assertThat(m.isEs6Module()).isTrue();
    assertThat(m.isGoogModule()).isFalse();
  }

  @Test
  public void testEs6ModuleDeclareNamespace() {
    testSame("export var x; goog.module.declareNamespace('my.module');");
    assertThat(metadataMap().getModulesByGoogNamespace().keySet()).containsExactly("my.module");
    ModuleMetadata m = metadataMap().getModulesByGoogNamespace().get("my.module");
    assertThat(m.googNamespaces()).containsExactly("my.module");
    assertThat(m.isEs6Module()).isTrue();
    assertThat(m.isGoogModule()).isFalse();
  }

  @Test
  public void testEs6ModuleDeclareNamespaceImportedGoog() {
    testSame(
        ImmutableList.of(
            SourceFile.fromCode("goog.js", ""),
            SourceFile.fromCode(
                "testcode", lines(
                    "import * as goog from './goog.js';",
                    "export var x;",
                    "goog.module.declareNamespace('my.module');"))));
    assertThat(metadataMap().getModulesByGoogNamespace().keySet()).containsExactly("my.module");
    ModuleMetadata m = metadataMap().getModulesByGoogNamespace().get("my.module");
    assertThat(m.googNamespaces()).containsExactly("my.module");
    assertThat(m.isEs6Module()).isTrue();
    assertThat(m.isGoogModule()).isFalse();
  }

  @Test
  public void testCommonJsModule() {
    testSame("exports = 0;");
    assertThat(metadataMap().getModulesByGoogNamespace()).isEmpty();
    ModuleMetadata m = metadataMap().getModulesByPath().get("testcode");
    assertThat(m.isCommonJs()).isTrue();
  }

  @Test
  public void testDuplicateProvides() {
    testError(
        new String[] {"goog.provide('duplciated');", "goog.provide('duplciated');"},
        ClosureRewriteModule.DUPLICATE_NAMESPACE);
  }

  @Test
  public void testDuplicateProvidesInSameFile() {
    testError(
        "goog.provide('duplciated');\ngoog.provide('duplciated');",
        ClosureRewriteModule.DUPLICATE_NAMESPACE);
  }

  @Test
  public void testDuplicateProvideAndGoogModule() {
    testError(
        new String[] {"goog.provide('duplciated');", "goog.module('duplciated');"},
        ClosureRewriteModule.DUPLICATE_NAMESPACE);
    testError(
        new String[] {"goog.module('duplciated');", "goog.provide('duplciated');"},
        ClosureRewriteModule.DUPLICATE_MODULE);
  }

  @Test
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

  @Test
  public void testDuplicateGoogModules() {
    testError(
        new String[] {"goog.module('duplciated');", "goog.module('duplciated');"},
        ClosureRewriteModule.DUPLICATE_MODULE);
  }

  @Test
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

  @Test
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

  @Test
  public void testUsesGlobalClosure() {
    testSame("goog.isArray(foo);");
    ModuleMetadata m = metadataMap().getModulesByPath().get("testcode");
    assertThat(m.usesClosure()).isTrue();
  }

  @Test
  public void testUsesGlobalClosureNoFunctionCall() {
    testSame("var b = goog.nullFunction;");
    ModuleMetadata m = metadataMap().getModulesByPath().get("testcode");
    assertThat(m.usesClosure()).isTrue();
  }

  @Test
  public void testLocalGoogIsNotClosure() {
    testSame("var goog; goog.isArray(foo);");
    ModuleMetadata m = metadataMap().getModulesByPath().get("testcode");
    assertThat(m.usesClosure()).isFalse();
  }

  @Test
  public void testImportedGoogIsClosure() {
    testSame("import * as goog from '/goog.js'; goog.isArray(foo);");
    ModuleMetadata m = metadataMap().getModulesByPath().get("testcode");
    assertThat(m.usesClosure()).isTrue();
  }

  @Test
  public void testRequireType() {
    testSame("goog.requireType('my.Type');");
    ModuleMetadata m = metadataMap().getModulesByPath().get("testcode");
    assertThat(m.requiredTypes()).containsExactly("my.Type");
  }

  @Test
  public void testRequiredClosureNamespaces() {
    testSame("goog.require('my.Type');");
    ModuleMetadata m = metadataMap().getModulesByPath().get("testcode");
    assertThat(m.requiredGoogNamespaces()).containsExactly("my.Type");
  }

  @Test
  public void testImport() {
    testSame("import '@spec!';");
    ModuleMetadata m = metadataMap().getModulesByPath().get("testcode");
    assertThat(m.es6ImportSpecifiers()).containsExactly("@spec!");
  }

  @Test
  public void testExport() {
    testSame("export { name } from '@spec!';");
    ModuleMetadata m = metadataMap().getModulesByPath().get("testcode");
    assertThat(m.es6ImportSpecifiers()).containsExactly("@spec!");
  }

  @Test
  public void testImportOrder() {
    testSame("import 'first'; export { name } from 'second'; import 'third';");
    ModuleMetadata m = metadataMap().getModulesByPath().get("testcode");
    assertThat(m.es6ImportSpecifiers()).containsExactly("first", "second", "third");
  }

  @Test
  public void testSetTestOnly() {
    testSame("goog.setTestOnly();");
    ModuleMetadata m = metadataMap().getModulesByPath().get("testcode");
    assertThat(m.isTestOnly()).isTrue();
  }

  @Test
  public void testSetTestOnlyWithStringArg() {
    testSame("goog.setTestOnly('string');");
    ModuleMetadata m = metadataMap().getModulesByPath().get("testcode");
    assertThat(m.isTestOnly()).isTrue();
  }

  @Test
  public void testSetTestOnlyWithExtraArg() {
    testError("goog.setTestOnly('string', 'string');", GatherModuleMetadata.INVALID_SET_TEST_ONLY);
    ModuleMetadata m = metadataMap().getModulesByPath().get("testcode");
    assertThat(m.isTestOnly()).isFalse();
  }

  @Test
  public void testSetTestOnlyWithInvalidArg() {
    testError("goog.setTestOnly(0);", GatherModuleMetadata.INVALID_SET_TEST_ONLY);
    ModuleMetadata m = metadataMap().getModulesByPath().get("testcode");
    assertThat(m.isTestOnly()).isFalse();
  }

  @Test
  public void testGatherFromExterns() {
    // js_lib will put data in externs for .i.js files.
    test(externs("export var x; goog.module.declareNamespace('my.module');"), srcs(""));
    assertThat(metadataMap().getModulesByGoogNamespace().keySet()).containsExactly("my.module");
    ModuleMetadata m = metadataMap().getModulesByGoogNamespace().get("my.module");
    assertThat(m.googNamespaces()).containsExactly("my.module");
    assertThat(m.isEs6Module()).isTrue();
    assertThat(m.isGoogModule()).isFalse();
  }
}
