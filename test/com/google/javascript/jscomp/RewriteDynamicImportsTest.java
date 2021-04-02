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
import static com.google.javascript.jscomp.RewriteDynamicImports.UNABLE_TO_COMPUTE_RELATIVE_PATH;
import static com.google.javascript.jscomp.RewriteDynamicImports.DYNAMIC_IMPORT_ALIASING_REQUIRED;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.jscomp.type.ReverseAbstractInterpreter;
import com.google.javascript.jscomp.type.SemanticReverseAbstractInterpreter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RewriteDynamicImports} */
@RunWith(JUnit4.class)
public class RewriteDynamicImportsTest extends CompilerTestCase {
  private String dynamicImportAlias = "imprt_";
  private LanguageMode langage = null;
  private LanguageMode langageIn = null;

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
    if (langage != null) {
      options.setLanguage(langage);
    }
    if (langageIn != null) {
      options.setLanguageIn(langageIn);
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

      new RewriteDynamicImports(compiler, dynamicImportAlias).process(externs, root);
    };
  }

  @Test
  public void externalImportWithoutAlias() {
    this.dynamicImportAlias = null;
    testSame("import('./external.js')");
  }

  @Test
  public void externalImportWithAlias() {
    test(
        externs("/** @param {string} a @return {!Promise<?>} */ function imprt_(a) {}"),
        srcs("import('./external.js')"),
        expected("imprt_('./external.js')"));
  }

  @Test
  public void internalImportSameChunkWithoutAlias_unused() {
    this.dynamicImportAlias = null;
    JSModule actualChunk0 = new JSModule("chunk0");
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

    test(srcs(new JSModule[] {actualChunk0}), expectedSrcs);
  }

  @Test
  public void internalImportSameChunkWithoutAlias_used() {
    this.dynamicImportAlias = null;
    JSModule actualChunk0 = new JSModule("chunk0");
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

    test(srcs(new JSModule[] {actualChunk0}), expectedSrcs);
  }

  @Test
  public void internalImportSameChunkWithAlias() {
    JSModule actualChunk0 = new JSModule("chunk0");
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
        srcs(new JSModule[] {actualChunk0}),
        expectedSrcs);
  }

  @Test
  public void internalImportDifferentChunksWithoutAlias_unused() {
    this.dynamicImportAlias = null;
    JSModule actualChunk0 = new JSModule("chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));

    JSModule actualChunk1 = new JSModule("chunk1");
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

    test(srcs(new JSModule[] {actualChunk0, actualChunk1}), expectedSrcs);
  }

  @Test
  public void internalImportDifferentChunksWithAlias_unused() {
    allowExternsChanges();
    JSModule actualChunk0 = new JSModule("chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));

    JSModule actualChunk1 = new JSModule("chunk1");
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

    test(srcs(new JSModule[] {actualChunk0, actualChunk1}), expectedSrcs);
  }

  @Test
  public void internalImportDifferentChunksWithoutAlias_used() {
    disableAstValidation();
    this.dynamicImportAlias = null;
    JSModule actualChunk0 = new JSModule("chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));

    JSModule actualChunk1 = new JSModule("chunk1");
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

    test(srcs(new JSModule[] {actualChunk0, actualChunk1}), expectedSrcs);
  }

  @Test
  public void internalImportDifferentChunksWithAlias_used() {
    disableAstValidation();
    allowExternsChanges();
    JSModule actualChunk0 = new JSModule("chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));

    JSModule actualChunk1 = new JSModule("chunk1");
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

    test(srcs(new JSModule[] {actualChunk0, actualChunk1}), expectedSrcs);
  }

  @Test
  public void qualifiedNameAlias() {
    this.dynamicImportAlias = "ns.imprt_";
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
    test(
        externs("const ns = {}; /** @const */ ns.imprt_ = function(path) {};"),
        srcs("import('./other.js');"),
        expected("ns.imprt_('./other.js');"));
  }

  @Test
  public void internalImportPathError() {
    this.dynamicImportAlias = null;
    JSModule actualChunk0 = new JSModule("folder1/chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));

    JSModule actualChunk1 = new JSModule("../folder2/chunk1");
    actualChunk1.add(SourceFile.fromCode("i1.js", "import('./i0.js');"));

    testError(srcs(new JSModule[] {actualChunk0, actualChunk1}), UNABLE_TO_COMPUTE_RELATIVE_PATH);
  }

  @Test
  public void lowerLanguageWithoutAlias() {
    this.dynamicImportAlias = null;
    langage = LanguageMode.ECMASCRIPT_2015;
    langageIn = LanguageMode.ECMASCRIPT_NEXT;
    JSModule actualChunk0 = new JSModule("chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));

    JSModule actualChunk1 = new JSModule("chunk1");
    actualChunk1.add(SourceFile.fromCode("i1.js", "import('./i0.js');"));

    testWarning(srcs(new JSModule[] {actualChunk0, actualChunk1}), DYNAMIC_IMPORT_ALIASING_REQUIRED);
  }
}
