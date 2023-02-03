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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.base.JSCompStrings.lines;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.IncrementalCheckMode;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.CrossChunkMethodMotion;
import com.google.javascript.jscomp.DependencyOptions;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.JSChunk;
import com.google.javascript.jscomp.ModuleIdentifier;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.VariableRenamingPolicy;
import com.google.javascript.jscomp.WarningLevel;
import com.google.javascript.jscomp.testing.JSCompCorrespondences;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile.SourceKind;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that run the optimizer over individual library TypedAST shards */
@RunWith(JUnit4.class)
public final class TypedAstIntegrationTest extends IntegrationTestCase {

  private ArrayList<Path> shards;
  private ArrayList<SourceFile> externFiles;
  private ArrayList<SourceFile> sourceFiles;

  @Override
  @Before
  public void setUp() {
    super.setUp();
    this.shards = new ArrayList<>();
    this.externFiles = new ArrayList<>();
    this.sourceFiles = new ArrayList<>();
  }

  @Test
  public void simpleAlertCall() throws IOException {
    precompileLibrary(extern(new TestExternsBuilder().addAlert().build()), code("alert(10);"));

    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setDependencyOptions(DependencyOptions.none());

    Compiler compiler = compileTypedAstShards(options);

    Node expectedRoot = parseExpectedCode("alert(10);");
    assertNode(compiler.getRoot().getSecondChild())
        .usingSerializer(compiler::toSource)
        .isEqualTo(expectedRoot);
  }

  @Test
  public void alertCallWithCrossLibraryVarReference() throws IOException {
    SourceFile lib1 = code("const lib1Global = 10;");
    precompileLibrary(lib1);
    precompileLibrary(
        extern(new TestExternsBuilder().addAlert().build()),
        typeSummary(lib1),
        code("alert(lib1Global);"));

    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setDependencyOptions(DependencyOptions.none());

    Compiler compiler = compileTypedAstShards(options);

    Node expectedRoot = parseExpectedCode("", "alert(10);");
    assertNode(compiler.getRoot().getSecondChild())
        .usingSerializer(compiler::toSource)
        .isEqualTo(expectedRoot);
  }

  @Test
  public void disambiguatesGoogScopeAcrossLibraries() throws IOException {

    SourceFile lib1 = code("goog.scope(function () { var x = 3; });");
    SourceFile lib2 = code("goog.scope(function () { var x = 4; });");
    SourceFile externs = extern(new TestExternsBuilder().addClosureExterns().build());

    precompileLibrary(externs);
    precompileLibrary(typeSummary(externs), lib1);
    precompileLibrary(typeSummary(externs), lib2);

    CompilerOptions options = new CompilerOptions();
    options.setClosurePass(true);

    Compiler compiler = compileTypedAstShards(options);
    Node expectedRoot =
        parseExpectedCode(
            "var $jscomp$scope$1954846972$0$x=3;", "var $jscomp$scope$1954846973$0$x=4");
    assertNode(compiler.getRoot().getSecondChild())
        .usingSerializer(compiler::toSource)
        .isEqualTo(expectedRoot);
  }

  @Test
  public void disambiguatesAndDeletesMethodsAcrossLibraries() throws IOException {
    SourceFile lib1 = code("class Lib1 { m() { return 'lib1'; } n() { return 'delete me'; } }");
    SourceFile lib2 = code("class Lib2 { m() { return 'delete me'; } n() { return 'lib2'; } }");
    precompileLibrary(lib1);
    precompileLibrary(lib2);
    precompileLibrary(
        extern(new TestExternsBuilder().addAlert().build()),
        typeSummary(lib1),
        typeSummary(lib2),
        code("alert(new Lib1().m()); alert(new Lib2().n());"));

    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setDependencyOptions(DependencyOptions.none());
    options.setDisambiguateProperties(true);

    Compiler compiler = compileTypedAstShards(options);

    Node expectedRoot = parseExpectedCode("", "", "alert('lib1'); alert('lib2')");
    assertNode(compiler.getRoot().getSecondChild())
        .usingSerializer(compiler::toSource)
        .isEqualTo(expectedRoot);
  }

  @Test
  public void disambiguatesAndDeletesMethodsAcrossLibraries_withTranspilation() throws IOException {
    SourceFile lib1 = code("class Lib1 { m() { return 'lib1'; } n() { return 'delete me'; } }");
    SourceFile lib2 = code("class Lib2 { m() { return 'delete me'; } n() { return 'lib2'; } }");
    precompileLibrary(lib1);
    precompileLibrary(lib2);
    precompileLibrary(
        extern(new TestExternsBuilder().addAlert().build()),
        typeSummary(lib1),
        typeSummary(lib2),
        code("alert(new Lib1().m()); alert(new Lib2().n());"));

    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setDependencyOptions(DependencyOptions.none());
    options.setDisambiguateProperties(true);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);

    Compiler compiler = compileTypedAstShards(options);

    Node expectedRoot = parseExpectedCode("", "", "alert('lib1'); alert('lib2')");
    assertNode(compiler.getRoot().getSecondChild())
        .usingSerializer(compiler::toSource)
        .isEqualTo(expectedRoot);
  }

  @Test
  public void lateFulfilledGlobalVariableIsRenamed() throws IOException {
    SourceFile lib1 =
        code(
            lines(
                "function lib1() {",
                "  if (typeof lib2Var !== 'undefined') {",
                "    alert(lib2Var);",
                "  }",
                "}"));
    precompileLibrary(extern(new TestExternsBuilder().addAlert().build()), lib1);
    precompileLibrary(typeSummary(lib1), code("var lib2Var = 10; lib1();"));

    CompilerOptions options = new CompilerOptions();
    options.setVariableRenaming(VariableRenamingPolicy.ALL);
    options.setGeneratePseudoNames(true);

    Compiler compiler = compileTypedAstShards(options);

    String[] expected =
        new String[] {
          lines(
              "function $lib1$$() {",
              "  if (typeof $lib2Var$$ !== 'undefined') {",
              "    alert($lib2Var$$);",
              "  }",
              "}"),
          "var $lib2Var$$ = 10; $lib1$$();"
        };
    Node expectedRoot = parseExpectedCode(expected);
    assertNode(compiler.getRoot().getSecondChild())
        .usingSerializer(compiler::toSource)
        .isEqualTo(expectedRoot);
  }

  @Test
  public void lateFulfilledNameReferencedInExternsAndCode_notRenamed() throws IOException {
    // tests an edge case with the "RemoveUnnecessarySyntheticExterns" pass
    // it can't remove the synthetic externs definition of "lateDefinedVar" or the compiler will
    // crash after variable renaming.

    // both externs and code have bad references to the same lateDefinedVar
    precompileLibrary(
        extern(
            "/** @fileoverview @suppress {externsValidation,checkVars} */", //
            "lateDefinedVar;"),
        code(
            "/** @fileoverview @suppress {checkVars,uselessCode} */", //
            "lateDefinedVar;"));
    // and another, entirely separate library defines it.
    precompileLibrary(code("var lateDefinedVar; var normalVar;"));

    CompilerOptions options = new CompilerOptions();
    options.setVariableRenaming(VariableRenamingPolicy.ALL);
    options.setGeneratePseudoNames(true);
    options.setProtectHiddenSideEffects(true);

    Compiler compiler = compileTypedAstShards(options);

    Node expectedRoot =
        parseExpectedCode("lateDefinedVar;", "var lateDefinedVar; var $normalVar$$;");
    assertNode(compiler.getRoot().getSecondChild())
        .usingSerializer(compiler::toSource)
        .isEqualTo(expectedRoot);
  }

  @Test
  public void externJSDocPropertiesNotRenamed() throws IOException {
    precompileLibrary(
        extern(
            new TestExternsBuilder()
                .addExtra(
                    lines(
                        "/** @typedef {{x: number, y: number}} */",
                        "let Coord;",
                        "/** @param {!Coord} coord */",
                        "function takeCoord(coord) {}"))
                .build()),
        code("const coord = {x: 1, y: 2}; takeCoord(coord);"));

    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setDependencyOptions(DependencyOptions.none());

    Compiler compiler = compileTypedAstShards(options);

    Node expectedRoot = parseExpectedCode("takeCoord({x: 1, y: 2});");
    assertNode(compiler.getRoot().getSecondChild())
        .usingSerializer(compiler::toSource)
        .isEqualTo(expectedRoot);
  }

  @Test
  public void gatherExternProperties() throws IOException {
    precompileLibrary(
        extern(
            new TestExternsBuilder()
                .addExtra(
                    lines(
                        "/** @fileoverview @externs */ ", //
                        "var ns = {}; ",
                        "ns.x; "))
                .addConsole()
                .build()),
        code("ns.nonExternProperty = 2; console.log(ns.x); console.log(ns.nonExternProperty);"));

    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setDependencyOptions(DependencyOptions.none());

    Compiler compiler = compileTypedAstShards(options);

    Node expectedRoot = parseExpectedCode("ns.a = 2; console.log(ns.x);console.log(ns.a);");
    assertNode(compiler.getRoot().getSecondChild())
        .usingSerializer(compiler::toSource)
        .isEqualTo(expectedRoot);
  }

  @Test
  public void testDefineCheck() throws IOException {
    precompileLibrary(code(""));

    CompilerOptions options = new CompilerOptions();
    options.setDefineReplacements(ImmutableMap.of("FOOBAR", 1));

    Compiler compiler = compileTypedAstShardsWithoutErrorChecks(options);

    assertThat(compiler.getWarnings())
        .comparingElementsUsing(JSCompCorrespondences.OWNING_DIAGNOSTIC_GROUP)
        .containsExactly(DiagnosticGroups.UNKNOWN_DEFINES);
    assertThat(compiler.getErrors()).isEmpty();
  }

  @Test
  public void protectsHiddenSideEffects() throws IOException {
    precompileLibrary(
        extern("const foo = {}; foo.bar;"),
        code("/** @fileoverview @suppress {uselessCode} */ foo.bar;"));

    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setDependencyOptions(DependencyOptions.none());

    Compiler compiler = compileTypedAstShards(options);

    Node expectedRoot = parseExpectedCode("foo.bar");
    assertNode(compiler.getRoot().getSecondChild())
        .usingSerializer(compiler::toSource)
        .isEqualTo(expectedRoot);
  }

  @Test
  public void removesRegExpCallsIfSafe() throws IOException {
    precompileLibrary(
        extern(new TestExternsBuilder().addRegExp().build()), code("(/abc/gi).exec('')"));

    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setDependencyOptions(DependencyOptions.none());

    Compiler compiler = compileTypedAstShards(options);

    Node expectedRoot = parseExpectedCode("");
    assertNode(compiler.getRoot().getSecondChild())
        .usingSerializer(compiler::toSource)
        .isEqualTo(expectedRoot);
  }

  @Test
  public void removesRegExpCallsIfUnsafelyReferenced() throws IOException {
    precompileLibrary(
        extern(new TestExternsBuilder().addRegExp().addConsole().build()),
        code(
            "(/abc/gi).exec('');", //
            "console.log(RegExp.$1);"));

    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setDependencyOptions(DependencyOptions.none());

    Compiler compiler = compileTypedAstShards(options);

    Node expectedRoot = parseExpectedCode("(/abc/gi).exec(''); console.log(RegExp.$1);");
    assertNode(compiler.getRoot().getSecondChild())
        .usingSerializer(compiler::toSource)
        .isEqualTo(expectedRoot);
  }

  @Test
  public void runsJ2clOptimizations() throws IOException {
    SourceFile f =
        SourceFile.fromCode(
            "f.java.js",
            lines(
                "function InternalWidget(){}",
                "InternalWidget.$clinit = function () {",
                "  InternalWidget.$clinit = function() {};",
                "  InternalWidget.$clinit();",
                "};",
                "InternalWidget.$clinit();"));
    sourceFiles.add(f);
    precompileLibrary(f);

    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setDependencyOptions(DependencyOptions.none());

    Compiler compiler = compileTypedAstShards(options);

    Node expectedRoot = parseExpectedCode("");
    assertNode(compiler.getRoot().getSecondChild())
        .usingSerializer(compiler::toSource)
        .isEqualTo(expectedRoot);
  }

  @Test
  public void testAngularPass() throws IOException {
    precompileLibrary(
        extern(new TestExternsBuilder().build()),
        code(
            lines(
                "/** @ngInject */ function f() {} ",
                "/** @ngInject */ function g(a){} ",
                "/** @ngInject */ var b = function f(a, b, c) {} ")));

    CompilerOptions options = new CompilerOptions();

    Compiler compiler = compileTypedAstShards(options);

    Node expectedRoot =
        parseExpectedCode(
            lines(
                "function f() {} ",
                "function g(a) {} g['$inject']=['a'];",
                "var b = function f(a, b, c) {}; b['$inject']=['a', 'b', 'c']"));
    assertNode(compiler.getRoot().getSecondChild())
        .usingSerializer(compiler::toSource)
        .isEqualTo(expectedRoot);
  }

  @Test
  public void testCrossChunkMethodMotion() throws IOException {
    // run checks & serialize .typedasts
    SourceFile f1 =
        SourceFile.fromCode(
            "f1.js",
            lines(
                "/** @constructor */",
                "var Foo = function() {};",
                "Foo.prototype.bar = function() {};",
                "/** @type {!Foo} */",
                "var x = new Foo();"));
    SourceFile f2 = SourceFile.fromCode("f2.js", "x.bar();");
    precompileLibrary(f1);
    precompileLibrary(typeSummary(f1), f2);

    Compiler compiler = new Compiler();
    // create two chunks, where chunk 2 depends on chunk 1, and they contain f1 and f2
    CompilerOptions options = new CompilerOptions();
    options.setCrossChunkMethodMotion(true);
    compiler.initOptions(options);
    JSChunk chunk1 = new JSChunk("chunk1");
    chunk1.add(f1);
    JSChunk chunk2 = new JSChunk("chunk2");
    chunk2.add(f2);
    chunk2.addDependency(chunk1);

    // run compilation
    try (InputStream inputStream = toInputStream(this.shards)) {
      compiler.initModulesWithTypedAstFilesystem(
          ImmutableList.copyOf(this.externFiles),
          ImmutableList.of(chunk1, chunk2),
          options,
          inputStream);
    }
    compiler.parse();
    compiler.stage2Passes();
    compiler.stage3Passes();

    String[] expected =
        new String[] {
          CrossChunkMethodMotion.STUB_DECLARATIONS
              + "var Foo = function() {};"
              + "Foo.prototype.bar=JSCompiler_stubMethod(0); var x=new Foo;",
          "Foo.prototype.bar=JSCompiler_unstubMethod(0,function(){}); x.bar()",
        };
    Node expectedRoot = parseExpectedCode(expected);
    assertNode(compiler.getRoot().getSecondChild())
        .usingSerializer(compiler::toSource)
        .isEqualTo(expectedRoot);
  }

  @Test
  public void dependencyModePruningForGoogModules_banned() throws IOException {
    precompileLibrary(
        extern(new TestExternsBuilder().addClosureExterns().build()),
        code("goog.module('keepMe'); const x = 1;"), // input_1
        code("goog.module('entryPoint'); goog.require('keepMe');"), // input_2
        code("goog.module('dropMe'); const x = 3;")); // input_3

    CompilerOptions options = new CompilerOptions();
    options.setDependencyOptions(
        DependencyOptions.pruneForEntryPoints(
            ImmutableList.of(ModuleIdentifier.forClosure("entryPoint"))));

    // TODO(b/219588952): if we decide to support this, verify that JSCompiler no longer incorrectly
    // prunes the 'keepMe' module.
    // This might be fixable by just removing module rewriting from the 'checks' phase.
    Exception ex =
        assertThrows(IllegalArgumentException.class, () -> compileTypedAstShards(options));
    assertThat(ex).hasMessageThat().contains("mode=PRUNE");
  }

  // use over 'compileTypedAstShards' if you want to validate reported errors or warnings in your
  // @Test case.
  private Compiler compileTypedAstShardsWithoutErrorChecks(CompilerOptions options)
      throws IOException {
    Compiler compiler = new Compiler();
    compiler.initOptions(options);
    try (InputStream inputStream = toInputStream(this.shards)) {
      compiler.initWithTypedAstFilesystem(
          ImmutableList.copyOf(this.externFiles),
          ImmutableList.copyOf(this.sourceFiles),
          options,
          inputStream);
    }
    compiler.parse();
    compiler.stage2Passes();
    compiler.stage3Passes();

    return compiler;
  }

  private Compiler compileTypedAstShards(CompilerOptions options) throws IOException {
    Compiler compiler = compileTypedAstShardsWithoutErrorChecks(options);

    checkUnexpectedErrorsOrWarnings(compiler, 0);
    return compiler;
  }

  private SourceFile code(String... code) {
    SourceFile sourceFile =
        SourceFile.fromCode("input_" + (sourceFiles.size() + 1), lines(code), SourceKind.STRONG);
    this.sourceFiles.add(sourceFile);
    return sourceFile;
  }

  private SourceFile extern(String... code) {
    SourceFile sourceFile =
        SourceFile.fromCode("extern_" + (externFiles.size() + 1), lines(code), SourceKind.EXTERN);
    this.externFiles.add(sourceFile);
    return sourceFile;
  }

  /** Runs the type summary generator on the given source code, and returns a summary file */
  private SourceFile typeSummary(SourceFile original) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setIncrementalChecks(IncrementalCheckMode.GENERATE_IJS);

    compiler.init(ImmutableList.of(), ImmutableList.of(original), options);
    compiler.parse();
    checkUnexpectedErrorsOrWarnings(compiler, 0);

    compiler.stage1Passes();
    checkUnexpectedErrorsOrWarnings(compiler, 0);

    return SourceFile.fromCode(original.getName(), compiler.toSource());
  }

  private void precompileLibrary(SourceFile... files) throws IOException {
    Path typedAstPath = Files.createTempFile("", ".typedast");

    CompilerOptions options = new CompilerOptions();
    options.setChecksOnly(true);
    options.setAngularPass(true);
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
    options.setProtectHiddenSideEffects(true);
    options.setTypedAstOutputFile(typedAstPath);
    options.setClosurePass(true);

    ImmutableList.Builder<SourceFile> externs = ImmutableList.builder();
    ImmutableList.Builder<SourceFile> sources = ImmutableList.builder();
    for (SourceFile file : files) {
      if (file.isExtern()) {
        externs.add(file);
      } else {
        sources.add(file);
      }
    }

    Compiler compiler = new Compiler();
    compiler.init(externs.build(), sources.build(), options);
    compiler.parse();
    checkUnexpectedErrorsOrWarnings(compiler, 0);

    compiler.stage1Passes(); // serializes a TypedAST into typedAstPath
    checkUnexpectedErrorsOrWarnings(compiler, 0);

    this.shards.add(typedAstPath);
  }

  /** Converts the list of paths into an input stream sequentially reading all the given files */
  private static InputStream toInputStream(ArrayList<Path> typedAsts) {
    InputStream inputStream = null;
    for (Path typedAst : typedAsts) {
      FileInputStream inputShard;
      try {
        inputShard = new FileInputStream(typedAst.toFile());
      } catch (FileNotFoundException ex) {
        throw new AssertionError(ex);
      }
      if (inputStream == null) {
        inputStream = inputShard;
      } else {
        inputStream = new SequenceInputStream(inputStream, inputShard);
      }
    }
    return inputStream;
  }

  private Node parseExpectedCode(String... files) {
    // pass empty CompilerOptions; CompilerOptions only matters for CommonJS module parsing
    CompilerOptions options = new CompilerOptions();
    return super.parseExpectedCode(files, options);
  }
}
