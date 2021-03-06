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

import static com.google.javascript.jscomp.RewriteDynamicImports.DYNAMIC_IMPORT_ALIAS_MISSING;
import static com.google.javascript.jscomp.RewriteDynamicImports.UNABLE_TO_COMPUTE_RELATIVE_PATH;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
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

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setLanguage(LanguageMode.ECMASCRIPT_2020, LanguageMode.ECMASCRIPT_2020);
    enableCreateModuleMap();
    enableTypeInfoValidation();
    disableScriptFeatureValidation();
    enableTranspile();
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2020);
    options.setPrettyPrint(true);

    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return (externs, root) -> {
      ReverseAbstractInterpreter rai =
          new SemanticReverseAbstractInterpreter(compiler.getTypeRegistry());
      compiler.setTypeCheckingHasRun(true);
      new TypeCheck(compiler, rai, compiler.getTypeRegistry())
          .processForTesting(externs, root);
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
    testExternChanges(
        "import('./external.js')",
        "/** @param {string} specifier @return {Promise<?>} */ function imprt_(specifier) {}");

    this.allowExternsChanges();
    test("import('./external.js')", "imprt_('./external.js')");
  }

  @Test
  public void internalImportSameChuckWithoutAlias_unused() {
    this.dynamicImportAlias = null;
    JSModule actualChunk0 = new JSModule("chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));
    actualChunk0.add(SourceFile.fromCode("i1.js", "import('./i0.js');"));

    Expected expectedSrcs = new Expected(ImmutableList.of(
        SourceFile.fromCode("i0.js", "const a = 1; export default a;"),
        SourceFile.fromCode("i1.js", "Promise.resolve();")
    ));

    test(
        srcs(new JSModule[] { actualChunk0 }),
        expectedSrcs);
  }

  @Test
  public void internalImportSameChuckWithoutAlias_used() {
    this.dynamicImportAlias = null;
    JSModule actualChunk0 = new JSModule("chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));
    actualChunk0.add(SourceFile.fromCode("i1.js", "const ns = import('./i0.js');"));

    Expected expectedSrcs = new Expected(ImmutableList.of(
        SourceFile.fromCode("i0.js", "const a = 1; export default a;"),
        SourceFile.fromCode("i1.js", "const ns = Promise.resolve(module$i0);")
    ));

    test(
        srcs(new JSModule[] { actualChunk0 }),
        expectedSrcs);
  }

  @Test
  public void internalImportSameChuckWithAlias() {
    JSModule actualChunk0 = new JSModule("chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));
    actualChunk0.add(SourceFile.fromCode("i1.js", "const ns = import('./i0.js');"));

    Expected expectedSrcs = new Expected(ImmutableList.of(
        SourceFile.fromCode("i0.js", "const a = 1; export default a;"),
        SourceFile.fromCode("i1.js", "const ns = Promise.resolve(module$i0);")
    ));

    test(
        srcs(new JSModule[] { actualChunk0 }),
        expectedSrcs);
  }

  @Test
  public void internalImportDifferentChucksWithoutAlias_unused() {
    this.dynamicImportAlias = null;
    JSModule actualChunk0 = new JSModule("chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));

    JSModule actualChunk1 = new JSModule("chunk1");
    actualChunk1.add(SourceFile.fromCode("i1.js", "import('./i0.js');"));

    Expected expectedSrcs = new Expected(ImmutableList.of(
        SourceFile.fromCode("i0.js", "const a = 1; export default a;"),
        SourceFile.fromCode("i1.js", "import('./chunk0.js');")
    ));

    test(
        srcs(new JSModule[] { actualChunk0, actualChunk1 }),
        expectedSrcs);
  }

  @Test
  public void internalImportDifferentChucksWithAlias_unused() {
    allowExternsChanges();
    JSModule actualChunk0 = new JSModule("chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));

    JSModule actualChunk1 = new JSModule("chunk1");
    actualChunk1.add(SourceFile.fromCode("i1.js", "import('./i0.js');"));

    Expected expectedSrcs = new Expected(ImmutableList.of(
        SourceFile.fromCode("i0.js", "const a = 1; export default a;"),
        SourceFile.fromCode("i1.js", "imprt_('./chunk0.js');")
    ));

    test(
        srcs(new JSModule[] { actualChunk0, actualChunk1 }),
        expectedSrcs);
  }

  @Test
  public void internalImportDifferentChucksWithoutAlias_used() {
    this.dynamicImportAlias = null;
    JSModule actualChunk0 = new JSModule("chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));

    JSModule actualChunk1 = new JSModule("chunk1");
    actualChunk1.add(SourceFile.fromCode("i1.js", "const nsPromise = import('./i0.js');"));

    Expected expectedSrcs = new Expected(ImmutableList.of(
        SourceFile.fromCode("i0.js", "const a = 1; export default a;"),
        SourceFile.fromCode("i1.js",
            "const nsPromise = import('./chunk0.js').then(function() { return module$i0; });")
    ));

    test(
        srcs(new JSModule[] { actualChunk0, actualChunk1 }),
        expectedSrcs);
  }

  @Test
  public void internalImportDifferentChucksWithAlias_used() {
    allowExternsChanges();
    JSModule actualChunk0 = new JSModule("chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));

    JSModule actualChunk1 = new JSModule("chunk1");
    actualChunk1.add(SourceFile.fromCode("i1.js", "const nsPromise = import('./i0.js');"));

    Expected expectedSrcs = new Expected(ImmutableList.of(
        SourceFile.fromCode("i0.js", "const a = 1; export default a;"),
        SourceFile.fromCode("i1.js",
            "const nsPromise = imprt_('./chunk0.js').then(function() { return module$i0; });")
    ));

    test(
        srcs(new JSModule[] { actualChunk0, actualChunk1 }),
        expectedSrcs);
  }

  @Test
  public void qualifiedNameAlias() {
    this.dynamicImportAlias = "ns.imprt_";
    test(
        "/** @const */ const ns = {}; /**@const */ ns.imprt_ = function(path) {}; import('./other.js');",
        "const ns = {};  /** @const */ ns.imprt_ = function(path) {}; ns.imprt_('./other.js');");
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
  public void qualifiedNameAliasError() {
    this.dynamicImportAlias = "ns.imprt_";
    testWarning(
        "const ns = {}; import('./other.js');",
        DYNAMIC_IMPORT_ALIAS_MISSING);
  }

  @Test
  public void internalImportPathError() {
    this.dynamicImportAlias = null;
    JSModule actualChunk0 = new JSModule("folder1/chunk0");
    actualChunk0.add(SourceFile.fromCode("i0.js", "const a = 1; export default a;"));

    JSModule actualChunk1 = new JSModule("../folder2/chunk1");
    actualChunk1.add(SourceFile.fromCode("i1.js", "import('./i0.js');"));

    testError(
        srcs(new JSModule[] { actualChunk0, actualChunk1 }),
        UNABLE_TO_COMPUTE_RELATIVE_PATH);
  }
}
