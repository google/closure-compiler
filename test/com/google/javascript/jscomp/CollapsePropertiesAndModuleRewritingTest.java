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

package com.google.javascript.jscomp;

import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.CompilerOptions.ChunkOutputType;
import com.google.javascript.jscomp.CompilerOptions.PropertyCollapseLevel;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.jscomp.modules.ModuleMapCreator;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for dynamic import handling that requires cooperation between ModuleRewriting and
 * CollapseProperties.
 */
@RunWith(JUnit4.class)
public class CollapsePropertiesAndModuleRewritingTest extends CompilerTestCase {
  private PropertyCollapseLevel collapseLevel = PropertyCollapseLevel.ALL;
  private ChunkOutputType chunkOutputType = ChunkOutputType.ES_MODULES;

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {
      final CompilerOptions options = compiler.getOptions();

      @Override
      public void process(Node externs, Node root) {
        PassListBuilder factories = new PassListBuilder(options);
        GatherModuleMetadata gatherModuleMetadata =
            new GatherModuleMetadata(compiler, false, options.getModuleResolutionMode());
        factories.maybeAdd(
            PassFactory.builder()
                .setName(PassNames.GATHER_MODULE_METADATA)
                .setRunInFixedPointLoop(true)
                .setInternalFactory((x) -> gatherModuleMetadata)
                .build());
        factories.maybeAdd(
            PassFactory.builder()
                .setName(PassNames.CREATE_MODULE_MAP)
                .setRunInFixedPointLoop(true)
                .setInternalFactory(
                    (x) -> new ModuleMapCreator(compiler, compiler.getModuleMetadataMap()))
                .build());
        TranspilationPasses.addEs6ModulePass(
            factories, new PreprocessorSymbolTable.CachedInstanceFactory());
        factories.maybeAdd(
            PassFactory.builder()
                .setName("REWRITE_DYNAMIC_IMPORT")
                .setInternalFactory(
                    (x) -> new RewriteDynamicImports(compiler, null, ChunkOutputType.ES_MODULES))
                .build());
        factories.maybeAdd(
            PassFactory.builder()
                .setName(PassNames.COLLAPSE_PROPERTIES)
                .setRunInFixedPointLoop(true)
                .setInternalFactory(
                    (x) ->
                        InlineAndCollapseProperties.builder(compiler)
                            .setPropertyCollapseLevel(collapseLevel)
                            .setChunkOutputType(chunkOutputType)
                            .setHaveModulesBeenRewritten(true)
                            .setModuleResolutionMode(options.getModuleResolutionMode())
                            .build())
                .build());

        for (PassFactory factory : factories.build()) {
          factory.create(compiler).process(externs, root);
        }
      }
    };
  }

  @Test
  public void testModuleDynamicImport() {
    allowExternsChanges();
    // TODO(bradfordcsmith): aggressive alias inlining prevents this example from working with
    // PropertyCollapseLevel.ALL
    collapseLevel = PropertyCollapseLevel.MODULE_EXPORT;

    JSChunk[] inputModules = new JSChunk[] {new JSChunk("entry"), new JSChunk("mod1")};
    inputModules[0].add(
        SourceFile.fromCode(
            "entry.js", "import('./mod1.js').then((ns) => console.log(ns.Foo.bar()));"));
    inputModules[1].add(
        SourceFile.fromCode(
            "mod1.js",
            lines(
                "export class Foo {", //
                "  static bar() { return 'bar'; }",
                "}")));
    inputModules[1].addDependency(inputModules[0]);

    JSChunk[] expectedModules = new JSChunk[] {new JSChunk("entry"), new JSChunk("mod1")};
    expectedModules[0].add(
        SourceFile.fromCode(
            "entry.js",
            lines(
                "import('./mod1.js')",
                "    .then(jscomp$DynamicImportCallback(() => module$mod1 ))",
                "    .then(ns => console.log(ns.Foo.bar()));")));
    expectedModules[1].add(
        SourceFile.fromCode(
            "mod1.js",
            lines(
                "class Foo$$module$mod1 {",
                "  static bar() { return 'bar'; }",
                "}",
                "/** @const */ var module$mod1={};",
                "/** @const */ module$mod1.Foo = Foo$$module$mod1;")));
    expectedModules[1].addDependency(expectedModules[0]);

    test(srcs(inputModules), expected(expectedModules));
  }

  @Test
  public void testModuleDynamicImportCommonJs() {
    enableProcessCommonJsModules();
    collapseLevel = PropertyCollapseLevel.MODULE_EXPORT;
    chunkOutputType = ChunkOutputType.GLOBAL_NAMESPACE;
    setModuleResolutionMode(ResolutionMode.WEBPACK);
    this.setWebpackModulesById(
        ImmutableMap.of(
            "1", "entry.js",
            "2", "mod1.js"));

    ArrayList<SourceFile> inputs = new ArrayList<>();
    inputs.add(
        SourceFile.fromCode(
            "entry.js",
            lines(
                "__webpack_require__.e(2).then(",
                "    function() { return __webpack_require__(2);})")));
    inputs.add(SourceFile.fromCode("mod1.js", "module.exports = 123;"));

    ArrayList<SourceFile> expected = new ArrayList<>();
    expected.add(
        SourceFile.fromCode(
            "entry.js",
            "__webpack_require__.e(2).then(function() { return module$mod1.default;})"));
    expected.add(
        SourceFile.fromCode(
            "mod1.js",
            "/** @const */ var module$mod1={}; /** @const */ module$mod1.default = 123;"));

    test(srcs(inputs), expected(expected));
  }
}
