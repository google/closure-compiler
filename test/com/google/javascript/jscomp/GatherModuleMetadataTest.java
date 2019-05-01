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
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.jscomp.modules.ModuleMetadataMap;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleMetadata;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


@RunWith(JUnit4.class)
public final class GatherModuleMetadataTest extends CompilerTestCase {

  private boolean rewriteScriptsToModules;
  private ImmutableList<ModuleIdentifier> entryPoints;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    entryPoints = ImmutableList.of();
    rewriteScriptsToModules = false;
  }

  @Override
  protected CompilerOptions getOptions(CompilerOptions options) {
    options = super.getOptions(options);
    if (!entryPoints.isEmpty()) {
      options.setDependencyOptions(DependencyOptions.pruneForEntryPoints(entryPoints));
    }
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return (externs, root) -> {
      if (rewriteScriptsToModules) {
        new Es6RewriteScriptsToModules(compiler).process(externs, root);
      }
      new GatherModuleMetadata(
              compiler, /* processCommonJsModules= */ true, ResolutionMode.BROWSER)
          .process(externs, root);
    };
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
    testSame(
        lines(
            "goog.loadModule(function(exports) {", //
            "  goog.module('my.module');",
            "  return exports;",
            "});"));

    assertThat(metadataMap().getModulesByGoogNamespace().keySet())
        .containsExactly("my.module");

    ModuleMetadata m = metadataMap().getModulesByGoogNamespace().get("my.module");
    assertThat(m.googNamespaces()).containsExactly("my.module");
    assertThat(m.isNonLegacyGoogModule()).isTrue();
    assertThat(m.path()).isNull();

    m = metadataMap().getModulesByPath().get("testcode");
    assertThat(m.googNamespaces()).isEmpty();
    assertThat(m.isScript()).isTrue();
  }

  @Test
  public void testLoadModuleLegacyNamespace() {
    testSame(
        lines(
            "goog.loadModule(function(exports) {", //
            "  goog.module('my.module');",
            "  goog.module.declareLegacyNamespace();",
            "  return exports;",
            "});"));

    assertThat(metadataMap().getModulesByGoogNamespace().keySet())
        .containsExactly("my.module");

    ModuleMetadata m = metadataMap().getModulesByGoogNamespace().get("my.module");
    assertThat(m.googNamespaces()).containsExactly("my.module");
    assertThat(m.isLegacyGoogModule()).isTrue();
    assertThat(m.path()).isNull();

    m = metadataMap().getModulesByPath().get("testcode");
    assertThat(m.googNamespaces()).isEmpty();
    assertThat(m.isScript()).isTrue();
  }

  @Test
  public void testLoadModuleUseStrict() {
    testSame(
        lines(
            "goog.loadModule(function(exports) {", //
            "  'use strict';",
            "  goog.module('with.strict');",
            "  return exports;",
            "});"));

    assertThat(metadataMap().getModulesByGoogNamespace().keySet())
        .containsExactly("with.strict");

    ModuleMetadata m = metadataMap().getModulesByGoogNamespace().get("with.strict");
    assertThat(m.googNamespaces()).containsExactly("with.strict");
    assertThat(m.isNonLegacyGoogModule()).isTrue();
    assertThat(m.path()).isNull();

    m = metadataMap().getModulesByPath().get("testcode");
    assertThat(m.googNamespaces()).isEmpty();
    assertThat(m.isScript()).isTrue();
  }

  @Test
  public void testMultipleGoogModuleCallsInLoadModule() {
    testSame(
        lines(
            // Technically an error but this pass shouldn't report it.
            "goog.loadModule(function(exports) {",
            "  goog.module('multiple.calls.c0');",
            "  goog.module('multiple.calls.c1');",
            "  return exports;",
            "});"));

    assertThat(metadataMap().getModulesByGoogNamespace().keySet())
        .containsExactly("multiple.calls.c0", "multiple.calls.c1");

    ModuleMetadata m = metadataMap().getModulesByGoogNamespace().get("multiple.calls.c0");
    assertThat(m.googNamespaces()).containsExactly("multiple.calls.c0", "multiple.calls.c1");
    assertThat(metadataMap().getModulesByGoogNamespace().get("multiple.calls.c1"))
        .isSameInstanceAs(m);
    assertThat(m.isNonLegacyGoogModule()).isTrue();
    assertThat(m.path()).isNull();

    m = metadataMap().getModulesByPath().get("testcode");
    assertThat(m.googNamespaces()).isEmpty();
    assertThat(m.isScript()).isTrue();
  }

  @Test
  public void testMultipleGoogLoadModules() {
    testSame(
        lines(
            "goog.loadModule(function(exports) {",
            "  goog.module('multiple.calls.c0');",
            "  return exports;",
            "});",
            "",
            "goog.loadModule(function(exports) {",
            "  goog.module('multiple.calls.c1');",
            "  return exports;",
            "});"));

    assertThat(metadataMap().getModulesByGoogNamespace().keySet())
        .containsExactly("multiple.calls.c0", "multiple.calls.c1");

    ModuleMetadata m = metadataMap().getModulesByGoogNamespace().get("multiple.calls.c0");
    assertThat(m.googNamespaces()).containsExactly("multiple.calls.c0");
    assertThat(m.isNonLegacyGoogModule()).isTrue();
    assertThat(m.path()).isNull();

    m = metadataMap().getModulesByGoogNamespace().get("multiple.calls.c1");
    assertThat(m.googNamespaces()).containsExactly("multiple.calls.c1");
    assertThat(m.isNonLegacyGoogModule()).isTrue();
    assertThat(m.path()).isNull();

    m = metadataMap().getModulesByPath().get("testcode");
    assertThat(m.googNamespaces()).isEmpty();
    assertThat(m.isScript()).isTrue();
  }

  @Test
  public void testBundleGoogLoadModuleAndProvides() {
    testSame(
        lines(
            "goog.provide('some.provide');",
            "",
            "goog.provide('some.other.provide');",
            "",
            "goog.loadModule(function(exports) {",
            "  goog.module('multiple.calls.c0');",
            "  return exports;",
            "});",
            "",
            "goog.loadModule(function(exports) {",
            "  goog.module('multiple.calls.c1');",
            "  return exports;",
            "});"));

    assertThat(metadataMap().getModulesByGoogNamespace().keySet())
        .containsExactly(
            "some.provide", "some.other.provide", "multiple.calls.c0", "multiple.calls.c1");

    ModuleMetadata m = metadataMap().getModulesByGoogNamespace().get("multiple.calls.c0");
    assertThat(m.googNamespaces()).containsExactly("multiple.calls.c0");
    assertThat(m.isNonLegacyGoogModule()).isTrue();
    assertThat(m.path()).isNull();
    assertThat(m.usesClosure()).isTrue();

    m = metadataMap().getModulesByGoogNamespace().get("multiple.calls.c1");
    assertThat(m.googNamespaces()).containsExactly("multiple.calls.c1");
    assertThat(m.isNonLegacyGoogModule()).isTrue();
    assertThat(m.path()).isNull();
    assertThat(m.usesClosure()).isTrue();

    m = metadataMap().getModulesByPath().get("testcode");
    assertThat(m.googNamespaces()).containsExactly("some.provide", "some.other.provide");
    assertThat(m.isGoogProvide()).isTrue();
    assertThat(m.usesClosure()).isTrue();
  }

  @Test
  public void testBundleGoogLoadModuleAndProvidesWithGoogDefined() {
    testSame(
        lines(
            "/** @provideGoog */",
            "var goog = {};",
            "",
            "goog.provide('some.provide');",
            "",
            "goog.provide('some.other.provide');",
            "",
            "goog.loadModule(function(exports) {",
            "  goog.module('multiple.calls.c0');",
            "  return exports;",
            "});",
            "",
            "goog.loadModule(function(exports) {",
            "  goog.module('multiple.calls.c1');",
            "  return exports;",
            "});"));

    assertThat(metadataMap().getModulesByGoogNamespace().keySet())
        .containsExactly(
            "some.provide", "some.other.provide", "multiple.calls.c0", "multiple.calls.c1");

    ModuleMetadata m = metadataMap().getModulesByGoogNamespace().get("multiple.calls.c0");
    assertThat(m.googNamespaces()).containsExactly("multiple.calls.c0");
    assertThat(m.isNonLegacyGoogModule()).isTrue();
    assertThat(m.path()).isNull();
    assertThat(m.usesClosure()).isFalse();

    m = metadataMap().getModulesByGoogNamespace().get("multiple.calls.c1");
    assertThat(m.googNamespaces()).containsExactly("multiple.calls.c1");
    assertThat(m.isNonLegacyGoogModule()).isTrue();
    assertThat(m.path()).isNull();
    assertThat(m.usesClosure()).isFalse();

    m = metadataMap().getModulesByPath().get("testcode");
    assertThat(m.googNamespaces()).containsExactly("some.provide", "some.other.provide");
    assertThat(m.isGoogProvide()).isTrue();
    assertThat(m.usesClosure()).isFalse();
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
  public void testEs6ModuleDeclareModuleIdImportedGoog() {
    testSame(
        ImmutableList.of(
            SourceFile.fromCode("goog.js", ""),
            SourceFile.fromCode(
                "testcode",
                lines(
                    "import * as goog from './goog.js';",
                    "export var x;",
                    "goog.declareModuleId('my.module');"))));
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
          "goog.provide('duplciated');", "export {}; goog.declareModuleId('duplciated');"
        },
        ClosureRewriteModule.DUPLICATE_NAMESPACE);
    testError(
        new String[] {
          "export {}; goog.declareModuleId('duplciated');", "goog.provide('duplciated');"
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
          "goog.module('duplciated');", "export {}; goog.declareModuleId('duplciated');"
        },
        ClosureRewriteModule.DUPLICATE_MODULE);
    testError(
        new String[] {
          "export {}; goog.declareModuleId('duplciated');", "goog.module('duplciated');"
        },
        ClosureRewriteModule.DUPLICATE_MODULE);
  }

  @Test
  public void testDuplicatEs6Modules() {
    testError(
        new String[] {
          "export {}; goog.declareModuleId('duplciated');",
          "export {}; goog.declareModuleId('duplciated');"
        },
        ClosureRewriteModule.DUPLICATE_MODULE);
    testError(
        new String[] {
          "export {}; goog.declareModuleId('duplciated');",
          "export {}; goog.declareModuleId('duplciated');"
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
    testSame("function bar() { var goog; goog.isArray(foo); }");
    ModuleMetadata m = metadataMap().getModulesByPath().get("testcode");
    assertThat(m.usesClosure()).isFalse();
  }

  @Test
  public void testGoogInSameScriptGoogIsNotClosure() {
    testSame("/** @provideGoog */ var goog = {}; goog.isArray(foo);");
    ModuleMetadata m = metadataMap().getModulesByPath().get("testcode");
    assertThat(m.usesClosure()).isFalse();
  }

  @Test
  public void testGoogInOtherScriptGoogIsClosure() {
    testSame(srcs("/** @provideGoog */ var goog = {};", "goog.isArray(foo);"));
    ModuleMetadata m = metadataMap().getModulesByPath().get("input1");
    assertThat(m.usesClosure()).isTrue();
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
    test(externs("export var x; goog.declareModuleId('my.module');"), srcs(""));
    assertThat(metadataMap().getModulesByGoogNamespace().keySet()).containsExactly("my.module");
    ModuleMetadata m = metadataMap().getModulesByGoogNamespace().get("my.module");
    assertThat(m.googNamespaces()).containsExactly("my.module");
    assertThat(m.isEs6Module()).isTrue();
    assertThat(m.isGoogModule()).isFalse();
  }

  @Test
  public void testImportedScript() {
    test(
        srcs(
            SourceFile.fromCode("imported.js", "console.log('lol');"),
            SourceFile.fromCode("notimported.js", "console.log('lol');"),
            SourceFile.fromCode("module.js", "import './imported.js';")));

    assertThat(metadataMap().getModulesByPath().get("imported.js").moduleType())
        .isEqualTo(ModuleType.SCRIPT);
    assertThat(metadataMap().getModulesByPath().get("notimported.js").moduleType())
        .isEqualTo(ModuleType.SCRIPT);
  }

  @Test
  public void testImportedScriptWithScriptsToModules() {
    // Default dependency options should still mark imported files as ES modules.
    rewriteScriptsToModules = true;

    test(
        srcs(
            SourceFile.fromCode("imported.js", "console.log('lol');"),
            SourceFile.fromCode("notimported.js", "console.log('lol');"),
            SourceFile.fromCode("module.js", "import './imported.js';")));

    assertThat(metadataMap().getModulesByPath().get("imported.js").moduleType())
        .isEqualTo(ModuleType.ES6_MODULE);
    assertThat(metadataMap().getModulesByPath().get("notimported.js").moduleType())
        .isEqualTo(ModuleType.SCRIPT);
  }

  @Test
  public void testImportedScriptWithEntryPoint() {
    rewriteScriptsToModules = true;
    entryPoints = ImmutableList.of(ModuleIdentifier.forFile("module.js"));

    test(
        srcs(
            SourceFile.fromCode("imported.js", "console.log('lol');"),
            SourceFile.fromCode("notimported.js", "console.log('lol');"),
            SourceFile.fromCode("module.js", "import './imported.js';")));

    assertThat(metadataMap().getModulesByPath().get("imported.js").moduleType())
        .isEqualTo(ModuleType.ES6_MODULE);
    // Pruned
    assertThat(metadataMap().getModulesByPath().keySet()).doesNotContain("notimported.js");
  }
}
