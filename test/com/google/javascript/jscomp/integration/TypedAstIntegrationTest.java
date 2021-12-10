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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.IncrementalCheckMode;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.VariableRenamingPolicy;
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

    Compiler compiler = compileTypedAstShards(options);

    Node expectedRoot = parseExpectedCode("", "alert(10);");
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
    options.setDisambiguateProperties(true);

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
        extern("/** @fileoverview @suppress {externsValidation,checkVars} */ lateDefinedVar;"),
        code("/** @fileoverview @suppress {checkVars} */ lateDefinedVar;"));
    // and another, entirely separate library defines it.
    precompileLibrary(code("var lateDefinedVar; var normalVar;"));

    CompilerOptions options = new CompilerOptions();
    options.setVariableRenaming(VariableRenamingPolicy.ALL);
    options.setGeneratePseudoNames(true);

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

    Compiler compiler = compileTypedAstShards(options);

    // TODO(b/207693227): stop renaming x and y to a and b
    Node expectedRoot = parseExpectedCode("takeCoord({a: 1, b: 2});");
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

  // use over 'compileTypedAstShards' if you want to validate reported errors or warnings in your
  // @Test case.
  private Compiler compileTypedAstShardsWithoutErrorChecks(CompilerOptions options)
      throws IOException {
    Compiler compiler = new Compiler();
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

  private SourceFile code(String code) {
    SourceFile sourceFile =
        SourceFile.fromCode("input_" + (sourceFiles.size() + 1), code, SourceKind.STRONG);
    this.sourceFiles.add(sourceFile);
    return sourceFile;
  }

  private SourceFile extern(String code) {
    SourceFile sourceFile =
        SourceFile.fromCode("extern_" + (externFiles.size() + 1), code, SourceKind.EXTERN);
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
    options.setCheckTypes(true);
    options.setCheckSymbols(true);
    options.setTypedAstOutputFile(typedAstPath);

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
