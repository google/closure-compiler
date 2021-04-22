package com.google.javascript.jscomp;

import com.google.javascript.jscomp.CompilerOptions.ChunkOutputType;
import com.google.javascript.jscomp.CompilerOptions.PropertyCollapseLevel;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.jscomp.modules.ModuleMapCreator;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class CollapsePropertiesAndModuleRewritingTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {
      final CompilerOptions options = compiler.getOptions();
      @Override
      public void process(Node externs, Node root) {
        List<PassFactory> factories = new ArrayList<>();
        GatherModuleMetadata gatherModuleMetadata =
            new GatherModuleMetadata(
                compiler, false, ResolutionMode.BROWSER);
        factories.add(
            PassFactory.builder()
                .setName(PassNames.GATHER_MODULE_METADATA)
                .setRunInFixedPointLoop(true)
                .setInternalFactory((x) -> gatherModuleMetadata)
                .setFeatureSetForChecks()
                .build());
        factories.add(
            PassFactory.builder()
                .setName(PassNames.CREATE_MODULE_MAP)
                .setRunInFixedPointLoop(true)
                .setInternalFactory(
                    (x) -> new ModuleMapCreator(compiler, compiler.getModuleMetadataMap()))
                .setFeatureSetForChecks()
                .build());
        TranspilationPasses.addEs6ModulePass(
            factories, new PreprocessorSymbolTable.CachedInstanceFactory());
        factories.add(
            PassFactory.builder()
                .setName("REWRITE_DYNAMIC_IMPORT")
                .setFeatureSetForChecks()
                .setInternalFactory(
                    (x) ->
                        new RewriteDynamicImports(
                            compiler,
                            null,
                            ChunkOutputType.ES_MODULES))
                .build());
        factories.add(
            PassFactory.builder()
                .setName(PassNames.COLLAPSE_PROPERTIES)
                .setRunInFixedPointLoop(true)
                .setInternalFactory(
                    (x) ->
                        new CollapseProperties(
                            compiler,
                            PropertyCollapseLevel.ALL,
                            ChunkOutputType.ES_MODULES,
                            true,
                            ResolutionMode.BROWSER))
                .setFeatureSetForChecks()
                .build());
        for (PassFactory factory : factories) {
          factory.create(compiler).process(externs, root);
        }
      }
    };
  }

  @Test
  public void testModuleDynamicImport() {
    allowExternsChanges();

    JSModule[] inputModules = new JSModule[] { new JSModule("entry"), new JSModule("mod1")};
    inputModules[0].add(
        SourceFile.fromCode(
            "entry.js",
            "import('./mod1.js').then((ns) => console.log(ns.Foo.bar()));"));
    inputModules[1].add(
        SourceFile.fromCode(
            "mod1.js",
            lines(
                "export class Foo {",
                "  static bar() { return 'bar'; }",
                "}")));
    inputModules[1].addDependency(inputModules[0]);

    JSModule[] expectedModules = new JSModule[] { new JSModule("entry"), new JSModule("mod1")};
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

    test(inputModules, expectedModules);
  }
}
