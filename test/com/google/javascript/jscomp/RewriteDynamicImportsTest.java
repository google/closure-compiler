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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.ConvertChunksToESModules.DYNAMIC_IMPORT_CALLBACK_FN;
import static com.google.javascript.jscomp.ConvertChunksToESModules.UNABLE_TO_COMPUTE_RELATIVE_PATH;
import static com.google.javascript.jscomp.RewriteDynamicImports.DYNAMIC_IMPORT_ALIASING_REQUIRED;
import static com.google.javascript.jscomp.RewriteDynamicImports.DYNAMIC_IMPORT_INVALID_ALIAS;
import static com.google.javascript.jscomp.deps.ModuleLoader.LOAD_WARNING;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.ChunkOutputType;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.jscomp.modules.ModuleMapCreator;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.jscomp.type.ReverseAbstractInterpreter;
import com.google.javascript.jscomp.type.SemanticReverseAbstractInterpreter;
import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RewriteDynamicImports} */
@RunWith(JUnit4.class)
public class RewriteDynamicImportsTest extends CompilerTestCase {
  private String dynamicImportAlias = "imprt_";
  private LanguageMode language = null;
  private LanguageMode languageIn = null;
  private ChunkOutputType chunkOutputType = ChunkOutputType.GLOBAL_NAMESPACE;

  public RewriteDynamicImportsTest() {
    super(new TestExternsBuilder().addPromise().build());
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableCreateModuleMap();
    enableTypeInfoValidation();
    disableScriptFeatureValidation();
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setPrettyPrint(true);
    if (language != null) {
      options.setLanguage(language);
    }
    if (languageIn != null) {
      options.setLanguageIn(languageIn);
    }
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return (externs, root) -> {
      // NOTE: we cannot just use enableTypeCheck(), because we need to be able
      // to retrieve globalTypedScope for use by Es6RewriteModules.
      ReverseAbstractInterpreter rai =
          new SemanticReverseAbstractInterpreter(compiler.getTypeRegistry());
      TypedScope globalTypedScope =
          checkNotNull(
              new TypeCheck(compiler, rai, compiler.getTypeRegistry())
                  .processForTesting(externs, root));
      compiler.setTypeCheckingHasRun(true);
      // We need to make sure modules are rewritten, because RewriteDynamicImports
      // expects to be able to see the module variables.
      new Es6RewriteModules(
              compiler,
              compiler.getModuleMetadataMap(),
              compiler.getModuleMap(),
              /* preprocessorSymbolTable= */ null,
              globalTypedScope)
          .process(externs, root);

      new RewriteDynamicImports(compiler, dynamicImportAlias, chunkOutputType)
          .process(externs, root);
    };
  }

  @Test
  public void externalImportWithoutAlias() {
    this.dynamicImportAlias = null;
    ignoreWarnings(LOAD_WARNING);
    testSame("import('./external.js')");
  }

  @Test
  public void externalImportWithAlias() {
    ignoreWarnings(LOAD_WARNING);
    test(
        externs("/** @param {string} a @return {!Promise<?>} */ function imprt_(a) {}"),
        srcs("import('./external.js')"),
        expected("imprt_('./external.js')"));
  }

  @Test
  public void internalImportSameChunkWithoutAlias_unused() {
    this.dynamicImportAlias = null;
    JSChunk actualChunk0 = new JSChunk("chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));
    actualChunk0.add(SourceFile.fromCode("i1.js", "import('./i0.js');"));

    Expected expectedSrcs =
        new Expected(
            ImmutableList.of(
                SourceFile.fromCode(
                    "i0.js",
                    lines(
                        "const a$$module$i0 = 1;",
                        "var $jscompDefaultExport$$module$i0 = a$$module$i0;",
                        "/** @const */ var module$i0 = {};",
                        "/** @const */ module$i0.default = $jscompDefaultExport$$module$i0;")),
                SourceFile.fromCode("i1.js", "Promise.resolve();")));

    test(srcs(new JSChunk[] {actualChunk0}), expectedSrcs);
  }

  @Test
  public void internalImportSameChunkWithoutAlias_used() {
    this.dynamicImportAlias = null;
    JSChunk actualChunk0 = new JSChunk("chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));
    actualChunk0.add(SourceFile.fromCode("i1.js", "const ns = import('./i0.js');"));

    Expected expectedSrcs =
        new Expected(
            ImmutableList.of(
                SourceFile.fromCode(
                    "i0.js",
                    lines(
                        "const a$$module$i0 = 1;",
                        "var $jscompDefaultExport$$module$i0 = a$$module$i0;",
                        "/** @const */ var module$i0 = {};",
                        "/** @const */ module$i0.default = $jscompDefaultExport$$module$i0;")),
                SourceFile.fromCode("i1.js", "const ns = Promise.resolve(module$i0);")));

    test(srcs(new JSChunk[] {actualChunk0}), expectedSrcs);
  }

  @Test
  public void internalImportSameChunkWithAlias() {
    JSChunk actualChunk0 = new JSChunk("chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));
    actualChunk0.add(SourceFile.fromCode("i1.js", "const ns = import('./i0.js');"));

    Expected expectedSrcs =
        new Expected(
            ImmutableList.of(
                SourceFile.fromCode(
                    "i0.js",
                    lines(
                        "const a$$module$i0 = 1;",
                        "var $jscompDefaultExport$$module$i0 = a$$module$i0;",
                        "/** @const */ var module$i0 = {};",
                        "/** @const */ module$i0.default = $jscompDefaultExport$$module$i0;")),
                SourceFile.fromCode("i1.js", "const ns = Promise.resolve(module$i0);")));

    test(
        externs(new TestExternsBuilder().addObject().addFunction().addPromise().build()),
        srcs(new JSChunk[] {actualChunk0}),
        expectedSrcs);
  }

  @Test
  public void internalImportDifferentChunksWithoutAlias_unused() {
    this.dynamicImportAlias = null;
    JSChunk actualChunk0 = new JSChunk("chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));

    JSChunk actualChunk1 = new JSChunk("chunk1");
    actualChunk1.add(SourceFile.fromCode("i1.js", "import('./i0.js');"));

    Expected expectedSrcs =
        new Expected(
            ImmutableList.of(
                SourceFile.fromCode(
                    "i0.js",
                    lines(
                        "const a$$module$i0 = 1;",
                        "var $jscompDefaultExport$$module$i0 = a$$module$i0;",
                        "/** @const */ var module$i0 = {};",
                        "/** @const */ module$i0.default = $jscompDefaultExport$$module$i0;")),
                SourceFile.fromCode("i1.js", "import('./chunk0.js');")));

    test(srcs(new JSChunk[] {actualChunk0, actualChunk1}), expectedSrcs);
  }

  @Test
  public void internalImportDifferentChunksWithAlias_unused() {
    JSChunk actualChunk0 = new JSChunk("chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));

    JSChunk actualChunk1 = new JSChunk("chunk1");
    actualChunk1.add(SourceFile.fromCode("i1.js", "import('./i0.js');"));

    Expected expectedSrcs =
        new Expected(
            ImmutableList.of(
                SourceFile.fromCode(
                    "i0.js",
                    lines(
                        "const a$$module$i0 = 1;",
                        "var $jscompDefaultExport$$module$i0 = a$$module$i0;",
                        "/** @const */ var module$i0 = {};",
                        "/** @const */ module$i0.default = $jscompDefaultExport$$module$i0;")),
                SourceFile.fromCode("i1.js", "imprt_('./chunk0.js');")));

    test(srcs(new JSChunk[] {actualChunk0, actualChunk1}), expectedSrcs);
  }

  @Test
  public void internalImportDifferentChunksWithoutAlias_used() {
    disableAstValidation();
    this.dynamicImportAlias = null;
    JSChunk actualChunk0 = new JSChunk("chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));

    JSChunk actualChunk1 = new JSChunk("chunk1");
    actualChunk1.add(SourceFile.fromCode("i1.js", "const nsPromise = import('./i0.js');"));

    Expected expectedSrcs =
        new Expected(
            ImmutableList.of(
                SourceFile.fromCode(
                    "i0.js",
                    lines(
                        "const a$$module$i0 = 1;",
                        "var $jscompDefaultExport$$module$i0 = a$$module$i0;",
                        "/** @const */ var module$i0 = {};",
                        "/** @const */ module$i0.default = $jscompDefaultExport$$module$i0;")),
                SourceFile.fromCode(
                    "i1.js",
                    lines(
                        "const nsPromise =", //
                        "    import('./chunk0.js').then(() => module$i0);"))));

    test(srcs(new JSChunk[] {actualChunk0, actualChunk1}), expectedSrcs);
  }

  @Test
  public void internalImportDifferentChunksWithAlias_used() {
    disableAstValidation();
    JSChunk actualChunk0 = new JSChunk("chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));

    JSChunk actualChunk1 = new JSChunk("chunk1");
    actualChunk1.add(SourceFile.fromCode("i1.js", "const nsPromise = import('./i0.js');"));

    Expected expectedSrcs =
        new Expected(
            ImmutableList.of(
                SourceFile.fromCode(
                    "i0.js",
                    lines(
                        "const a$$module$i0 = 1;",
                        "var $jscompDefaultExport$$module$i0 = a$$module$i0;",
                        "/** @const */ var module$i0 = {};",
                        "/** @const */ module$i0.default = $jscompDefaultExport$$module$i0;")),
                SourceFile.fromCode(
                    "i1.js", "const nsPromise = imprt_('./chunk0.js').then(() => module$i0);")));

    test(srcs(new JSChunk[] {actualChunk0, actualChunk1}), expectedSrcs);
  }

  @Test
  public void qualifiedNameAlias() {
    this.dynamicImportAlias = "ns.imprt_";
    ignoreWarnings(LOAD_WARNING);
    test(
        lines(
            "/** @const */", //
            "const ns = {};",
            "/** @const */",
            "ns.imprt_ = function(path) {};",
            "import('./other.js');"),
        lines(
            "/** @const */", //
            "const ns = {};",
            "/** @const */",
            "ns.imprt_ = function(path) {};",
            "ns.imprt_('./other.js');"));
  }

  @Test
  public void qualifiedNameAliasExtern() {
    this.dynamicImportAlias = "ns.imprt_";
    ignoreWarnings(LOAD_WARNING);
    test(
        externs("const ns = {}; /** @const */ ns.imprt_ = function(path) {};"),
        srcs("import('./other.js');"),
        expected("ns.imprt_('./other.js');"));
  }

  @Test
  public void internalImportPathError() {
    this.dynamicImportAlias = null;
    JSChunk actualChunk0 = new JSChunk("folder1/chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));

    JSChunk actualChunk1 = new JSChunk("../folder2/chunk1");
    actualChunk1.add(SourceFile.fromCode("i1.js", "import('./i0.js');"));

    testError(srcs(new JSChunk[] {actualChunk0, actualChunk1}), UNABLE_TO_COMPUTE_RELATIVE_PATH);
  }

  @Test
  public void lowerLanguageWithoutAlias() {
    this.dynamicImportAlias = null;
    language = LanguageMode.ECMASCRIPT_2015;
    languageIn = LanguageMode.ECMASCRIPT_NEXT;
    JSChunk actualChunk0 = new JSChunk("chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));

    JSChunk actualChunk1 = new JSChunk("chunk1");
    actualChunk1.add(SourceFile.fromCode("i1.js", "import('./i0.js');"));

    testWarning(srcs(new JSChunk[] {actualChunk0, actualChunk1}), DYNAMIC_IMPORT_ALIASING_REQUIRED);
  }

  @Test
  public void outputModulesExternInjected() {
    this.chunkOutputType = ChunkOutputType.ES_MODULES;
    this.dynamicImportAlias = null;
    JSChunk actualChunk0 = new JSChunk("chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));

    JSChunk actualChunk1 = new JSChunk("chunk1");
    actualChunk1.add(SourceFile.fromCode("i1.js", "const nsPromise = import('./i0.js');"));

    testExternChanges(
        srcs(actualChunk0, actualChunk1),
        expected("function " + DYNAMIC_IMPORT_CALLBACK_FN + "(importCallback) {}"));
  }

  @Test
  public void outputModulesInternalImportSameChunk_unused() {
    allowExternsChanges();
    this.chunkOutputType = ChunkOutputType.ES_MODULES;
    this.dynamicImportAlias = null;
    JSChunk actualChunk0 = new JSChunk("chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));
    actualChunk0.add(SourceFile.fromCode("i1.js", "import('./i0.js');"));

    Expected expectedSrcs =
        new Expected(
            ImmutableList.of(
                SourceFile.fromCode(
                    "i0.js",
                    lines(
                        "const a$$module$i0 = 1;",
                        "var $jscompDefaultExport$$module$i0 = a$$module$i0;",
                        "/** @const */ var module$i0 = {};",
                        "/** @const */ module$i0.default = $jscompDefaultExport$$module$i0;")),
                SourceFile.fromCode("i1.js", "Promise.resolve();")));

    test(srcs(new JSChunk[] {actualChunk0}), expectedSrcs);
  }

  @Test
  public void outputModulesInternalImportSameChunk_used() {
    allowExternsChanges();
    this.chunkOutputType = ChunkOutputType.ES_MODULES;
    this.dynamicImportAlias = null;
    JSChunk actualChunk0 = new JSChunk("chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));
    actualChunk0.add(SourceFile.fromCode("i1.js", "const ns = import('./i0.js');"));

    Expected expectedSrcs =
        new Expected(
            ImmutableList.of(
                SourceFile.fromCode(
                    "i0.js",
                    lines(
                        "const a$$module$i0 = 1;",
                        "var $jscompDefaultExport$$module$i0 = a$$module$i0;",
                        "/** @const */ var module$i0 = {};",
                        "/** @const */ module$i0.default = $jscompDefaultExport$$module$i0;")),
                SourceFile.fromCode("i1.js", "const ns = Promise.resolve(module$i0);")));

    test(srcs(new JSChunk[] {actualChunk0}), expectedSrcs);
  }

  @Test
  public void outputModulesInternalImportDifferentChunks_unused() {
    allowExternsChanges();
    this.chunkOutputType = ChunkOutputType.ES_MODULES;
    this.dynamicImportAlias = null;
    JSChunk actualChunk0 = new JSChunk("chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));

    JSChunk actualChunk1 = new JSChunk("chunk1");
    actualChunk1.add(SourceFile.fromCode("i1.js", "import('./i0.js');"));

    Expected expectedSrcs =
        new Expected(
            ImmutableList.of(
                SourceFile.fromCode(
                    "i0.js",
                    lines(
                        "const a$$module$i0 = 1;",
                        "var $jscompDefaultExport$$module$i0 = a$$module$i0;",
                        "/** @const */ var module$i0 = {};",
                        "/** @const */ module$i0.default = $jscompDefaultExport$$module$i0;")),
                SourceFile.fromCode("i1.js", "import('./chunk0.js');")));

    test(srcs(new JSChunk[] {actualChunk0, actualChunk1}), expectedSrcs);
  }

  @Test
  public void outputModulesInternalImportDifferentChunks_used() {
    allowExternsChanges();
    disableAstValidation();
    this.chunkOutputType = ChunkOutputType.ES_MODULES;
    this.dynamicImportAlias = null;
    JSChunk actualChunk0 = new JSChunk("chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));

    JSChunk actualChunk1 = new JSChunk("chunk1");
    actualChunk1.add(SourceFile.fromCode("i1.js", "const nsPromise = import('./i0.js');"));

    Expected expectedSrcs =
        new Expected(
            ImmutableList.of(
                SourceFile.fromCode(
                    "i0.js",
                    lines(
                        "const a$$module$i0 = 1;",
                        "var $jscompDefaultExport$$module$i0 = a$$module$i0;",
                        "/** @const */ var module$i0 = {};",
                        "/** @const */ module$i0.default = $jscompDefaultExport$$module$i0;")),
                SourceFile.fromCode(
                    "i1.js",
                    lines(
                        "const nsPromise =", //
                        "    import('./chunk0.js')",
                        "        .then(" + DYNAMIC_IMPORT_CALLBACK_FN + "(() => module$i0));"))));

    test(srcs(new JSChunk[] {actualChunk0, actualChunk1}), expectedSrcs);
  }

  @Test
  public void aliasExternInjectedSimple() {
    language = LanguageMode.ECMASCRIPT_2015;
    languageIn = LanguageMode.ECMASCRIPT_NEXT;
    this.dynamicImportAlias = "import_";
    JSChunk actualChunk0 = new JSChunk("chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));

    JSChunk actualChunk1 = new JSChunk("chunk1");
    actualChunk1.add(SourceFile.fromCode("i1.js", "const nsPromise = import('./i0.js');"));

    testExternChanges(
        externs(""), srcs(actualChunk0, actualChunk1), expected("function import_(specifier) {}"));
  }

  @Test
  public void aliasExternInjectedSimpleImport() {
    language = LanguageMode.ECMASCRIPT_2015;
    languageIn = LanguageMode.ECMASCRIPT_NEXT;
    this.dynamicImportAlias = "import";

    // "import" is not a valid JS identifier name and will not parse.
    // Instead, we have to manually check the AST for the expected name.
    Compiler compiler = createCompiler();
    CompilerOptions options = getOptions();
    compiler.init(
        ImmutableList.of(SourceFile.fromCode(GENERATED_EXTERNS_NAME, "")),
        ImmutableList.of(
            SourceFile.fromCode(
                GENERATED_SRC_NAME, "var external = 'url'; const nsPromise = import(external);")),
        options);
    compiler.parseInputs();
    new GatherModuleMetadata(compiler, /* processCommonJsModules= */ false, ResolutionMode.BROWSER)
        .process(compiler.getExternsRoot(), compiler.getJsRoot());
    new ModuleMapCreator(compiler, compiler.getModuleMetadataMap())
        .process(compiler.getExternsRoot(), compiler.getJsRoot());
    assertThat(compiler.getErrors()).isEmpty();
    Node externsAndJs = compiler.getRoot();
    Node externs = externsAndJs.getFirstChild();
    Node root = externsAndJs.getLastChild();
    compiler.beforePass(this.getName());
    getProcessor(compiler).process(externs, root);

    Node syntheticExterns = externs.getFirstChild();
    assertThat(syntheticExterns).isNotNull();
    assertThat(syntheticExterns.isScript()).isTrue();
    Node injectedAlias = syntheticExterns.getFirstChild();
    assertThat(injectedAlias).isNotNull();
    assertThat(injectedAlias.isFunction()).isTrue();
    assertThat(injectedAlias.getFirstChild().isName()).isTrue();
    assertThat(injectedAlias.getFirstChild().getString()).isEqualTo("import");
  }

  @Test
  public void aliasExternInjectedSimpleAlreadyDefined() {
    language = LanguageMode.ECMASCRIPT_2015;
    languageIn = LanguageMode.ECMASCRIPT_NEXT;
    this.dynamicImportAlias = "import_";
    JSChunk actualChunk0 = new JSChunk("chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));

    JSChunk actualChunk1 = new JSChunk("chunk1");
    actualChunk1.add(SourceFile.fromCode("i1.js", "const nsPromise = import('./i0.js');"));

    testExternChanges(
        externs("function import_() {}"),
        srcs(actualChunk0, actualChunk1),
        expected("function import_() {}"));
  }

  @Test
  public void aliasExternInjectedQualified() {
    language = LanguageMode.ECMASCRIPT_2015;
    languageIn = LanguageMode.ECMASCRIPT_NEXT;
    this.dynamicImportAlias = "foo.import";
    JSChunk actualChunk0 = new JSChunk("chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));

    JSChunk actualChunk1 = new JSChunk("chunk1");
    actualChunk1.add(SourceFile.fromCode("i1.js", "const nsPromise = import('./i0.js');"));

    testExternChanges(
        externs(""),
        srcs(actualChunk0, actualChunk1),
        expected("var foo = {}; foo.import = function(specifier) {};"));
  }

  @Test
  public void aliasExternInjectedQualifiedAlreadyDefinedComplete() {
    language = LanguageMode.ECMASCRIPT_2015;
    languageIn = LanguageMode.ECMASCRIPT_NEXT;
    this.dynamicImportAlias = "foo.import";
    JSChunk actualChunk0 = new JSChunk("chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));

    JSChunk actualChunk1 = new JSChunk("chunk1");
    actualChunk1.add(SourceFile.fromCode("i1.js", "const nsPromise = import('./i0.js');"));

    testExternChanges(
        externs("var foo = {}; foo.import = function(specifier) {};"),
        srcs(actualChunk0, actualChunk1),
        expected("var foo = {}; foo.import = function(specifier) {};"));
  }

  @Test
  public void aliasExternInjectedQualifiedAlreadyDefinedPartial() {
    language = LanguageMode.ECMASCRIPT_2015;
    languageIn = LanguageMode.ECMASCRIPT_NEXT;
    this.dynamicImportAlias = "foo.import";
    JSChunk actualChunk0 = new JSChunk("chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));

    JSChunk actualChunk1 = new JSChunk("chunk1");
    actualChunk1.add(SourceFile.fromCode("i1.js", "const nsPromise = import('./i0.js');"));

    testExternChanges(
        externs("var foo = {};"), srcs(actualChunk0, actualChunk1), expected("var foo = {};"));
  }

  @Test
  public void invalidAliasSimpleName() {
    this.dynamicImportAlias = "fo'oo";
    language = LanguageMode.ECMASCRIPT_2015;
    languageIn = LanguageMode.ECMASCRIPT_NEXT;
    JSChunk actualChunk0 = new JSChunk("chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));

    JSChunk actualChunk1 = new JSChunk("chunk1");
    actualChunk1.add(SourceFile.fromCode("i1.js", "import('./i0.js');"));

    testError(srcs(new JSChunk[] {actualChunk0, actualChunk1}), DYNAMIC_IMPORT_INVALID_ALIAS);
  }

  @Test
  public void invalidAliasQualifiedName() {
    this.dynamicImportAlias = "foo.bar['chunk']";
    language = LanguageMode.ECMASCRIPT_2015;
    languageIn = LanguageMode.ECMASCRIPT_NEXT;
    JSChunk actualChunk0 = new JSChunk("chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));

    JSChunk actualChunk1 = new JSChunk("chunk1");
    actualChunk1.add(SourceFile.fromCode("i1.js", "import('./i0.js');"));

    testError(srcs(new JSChunk[] {actualChunk0, actualChunk1}), DYNAMIC_IMPORT_INVALID_ALIAS);
  }
}
