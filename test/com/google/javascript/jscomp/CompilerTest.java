/*
 * Copyright 2010 The Closure Compiler Authors.
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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.debugging.sourcemap.FilePosition;
import com.google.debugging.sourcemap.SourceMapGeneratorV3;
import com.google.debugging.sourcemap.proto.Mapping.OriginalMapping;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;

/**
 * @author johnlenz@google.com (John Lenz)
 */

public class CompilerTest extends TestCase {

  // Verify the line and column information is maintained after a reset
  public void testCodeBuilderColumnAfterReset() {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    String js = "foo();\ngoo();";
    cb.append(js);
    assertEquals(js, cb.toString());
    assertEquals(1, cb.getLineIndex());
    assertEquals(6, cb.getColumnIndex());

    cb.reset();

    assertThat(cb.toString()).isEmpty();
    assertEquals(1, cb.getLineIndex());
    assertEquals(6, cb.getColumnIndex());
  }

  public void testCodeBuilderAppend() {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("foo();");
    assertEquals(0, cb.getLineIndex());
    assertEquals(6, cb.getColumnIndex());

    cb.append("goo();");

    assertEquals(0, cb.getLineIndex());
    assertEquals(12, cb.getColumnIndex());

    // newline reset the column index
    cb.append("blah();\ngoo();");

    assertEquals(1, cb.getLineIndex());
    assertEquals(6, cb.getColumnIndex());
  }

  public void testCyclicalDependencyInInputs() {
    List<SourceFile> inputs = Lists.newArrayList(
        SourceFile.fromCode(
            "gin", "goog.provide('gin'); goog.require('tonic'); var gin = {};"),
        SourceFile.fromCode("tonic",
            "goog.provide('tonic'); goog.require('gin'); var tonic = {};"),
        SourceFile.fromCode(
            "mix", "goog.require('gin'); goog.require('tonic');"));
    CompilerOptions options = new CompilerOptions();
    options.setIdeMode(true);
    options.setManageClosureDependencies(true);
    Compiler compiler = new Compiler();
    compiler.init(ImmutableList.<SourceFile>of(), inputs, options);
    compiler.parseInputs();
    assertEquals(compiler.externAndJsRoot, compiler.jsRoot.getParent());
    assertEquals(compiler.externAndJsRoot, compiler.externsRoot.getParent());
    assertNotNull(compiler.externAndJsRoot);

    Node jsRoot = compiler.jsRoot;
    assertEquals(3, jsRoot.getChildCount());
  }

  public void testLocalUndefined() throws Exception {
    // Some JavaScript libraries like to create a local instance of "undefined",
    // to ensure that other libraries don't try to overwrite it.
    //
    // Most of the time, this is OK, because normalization will rename
    // that variable to undefined$$1. But this won't happen if they don't
    // include the default externs.
    //
    // This test is just to make sure that the compiler doesn't crash.
    CompilerOptions options = new CompilerOptions();
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(
        options);
    Compiler compiler = new Compiler();
    SourceFile externs = SourceFile.fromCode("externs.js", "");
    SourceFile input = SourceFile.fromCode("input.js",
        "(function (undefined) { alert(undefined); })();");
    compiler.compile(externs, input, options);
  }

  public void testCommonJSProvidesAndRequire() throws Exception {
    List<SourceFile> inputs = Lists.newArrayList(
        SourceFile.fromCode("gin.js", "require('tonic')"),
        SourceFile.fromCode("tonic.js", ""),
        SourceFile.fromCode("mix.js", "require('gin'); require('tonic');"));
    List<String> entryPoints = Lists.newArrayList("module$mix");

    Compiler compiler = initCompilerForCommonJS(inputs, entryPoints);
    JSModuleGraph graph = compiler.getModuleGraph();
    assertEquals(3, graph.getModuleCount());
    List<CompilerInput> result = graph.manageDependencies(entryPoints,
        compiler.getInputsForTesting());
    assertEquals("[module$tonic]", result.get(0).getName());
    assertEquals("[module$gin]", result.get(1).getName());
    assertEquals("tonic.js", result.get(2).getName());
    assertEquals("gin.js", result.get(3).getName());
    assertEquals("mix.js", result.get(4).getName());
  }

  public void testCommonJSMissingRequire() throws Exception {
    List<SourceFile> inputs = Lists.newArrayList(
        SourceFile.fromCode("gin.js", "require('missing')"));
    Compiler compiler = initCompilerForCommonJS(
        inputs, ImmutableList.of("module$gin"));

    assertEquals(1, compiler.getErrorManager().getErrorCount());
    String error = compiler.getErrorManager().getErrors()[0].toString();
    assertTrue(
        "Unexpected error: " + error,
        error.contains(
            "required entry point \"module$missing\" never provided"));
  }

  private String normalize(String path) {
    return path.replace("/", File.separator);
  }

  public void testInputSourceMaps() throws Exception {
    FilePosition originalSourcePosition = new FilePosition(17, 25);
    ImmutableMap<String, SourceMapInput> inputSourceMaps = ImmutableMap.of(
        normalize("generated_js/example.js"),
        sourcemap(
            normalize("generated_js/example.srcmap"),
            normalize("../original/source.html"),
            originalSourcePosition));
    String origSourceName = normalize("original/source.html");
    List<SourceFile> originalSources = Lists.newArrayList(
        SourceFile.fromCode(origSourceName, "<div ng-show='foo()'>"));

    CompilerOptions options = new CompilerOptions();
    options.inputSourceMaps = inputSourceMaps;
    Compiler compiler = new Compiler();
    compiler.setOriginalSourcesLoader(createFileLoader(originalSources));
    compiler.init(Lists.<SourceFile>newArrayList(),
        Lists.<SourceFile>newArrayList(), options);

    assertEquals(
        OriginalMapping.newBuilder()
            .setOriginalFile(origSourceName)
            .setLineNumber(18)
            .setColumnPosition(25)
            .build(),
        compiler.getSourceMapping(normalize("generated_js/example.js"), 3, 3));
    assertEquals("<div ng-show='foo()'>",
        compiler.getSourceLine(origSourceName, 1));
  }

  private SourceMapInput sourcemap(String sourceMapPath, String originalSource,
      FilePosition originalSourcePosition) throws Exception {
    SourceMapGeneratorV3 sourceMap = new SourceMapGeneratorV3();
    sourceMap.addMapping(originalSource, null, originalSourcePosition,
        new FilePosition(1, 1), new FilePosition(100, 1));
    StringBuilder output = new StringBuilder();
    sourceMap.appendTo(output, "unused.js");

    return new SourceMapInput(
        SourceFile.fromCode(sourceMapPath, output.toString()));
  }

  private Function<String, SourceFile> createFileLoader(
      final List<SourceFile> sourceFiles) {
    return new Function<String, SourceFile>() {
      @Override
      public SourceFile apply(String filename) {
        for (SourceFile file : sourceFiles) {
          if (file.getOriginalPath().equals(filename)) {
            return file;
          }
        }
        return null;
      }
    };
  }

  private Compiler initCompilerForCommonJS(
      List<SourceFile> inputs, List<String> entryPoints)
      throws Exception {
    CompilerOptions options = new CompilerOptions();
    options.setIdeMode(true);
    options.setManageClosureDependencies(entryPoints);
    options.setClosurePass(true);
    options.setProcessCommonJSModules(true);
    Compiler compiler = new Compiler();
    compiler.init(Lists.<SourceFile>newArrayList(), inputs, options);
    compiler.parseInputs();
    return compiler;
  }

  private static final List<SourceFile> EMPTY_EXTERNS = ImmutableList.of(
      SourceFile.fromCode("externs", ""));

  /**
   * Ensure that the printInputDelimiter option adds a "// Input #" comment
   * at the start of each "script" in the compiled output.
   */
  public void testInputDelimiters() throws Exception {
    Compiler compiler = new Compiler();
    CompilerOptions options = createNewFlagBasedOptions();
    options.setPrintInputDelimiter(true);

    String fileOverview = "/** @fileoverview Foo */";
    List<SourceFile> inputs = ImmutableList.of(
        SourceFile.fromCode("i1", ""),
        SourceFile.fromCode("i2", fileOverview));

    Result result = compiler.compile(EMPTY_EXTERNS, inputs, options);
    assertTrue(result.success);

    String outputSource = compiler.toSource();
    System.err.println("Output:\n[" + outputSource + "]");
    assertEquals("// Input 0\n// Input 1\n", outputSource);
  }

  /**
   * Make sure that non-standard JSDoc annotation is not a hard error
   * unless it is specified.
   */
  public void testBug2176967Default() {
    final String badJsDoc = "/** @XYZ */\n var x";
    Compiler compiler = new Compiler();

    CompilerOptions options = createNewFlagBasedOptions();

    // Default is warning.
    compiler.compile(SourceFile.fromCode("extern.js", ""),
        SourceFile.fromCode("test.js", badJsDoc), options);
    assertEquals(1, compiler.getWarningCount());
    assertEquals(0, compiler.getErrorCount());
  }

  /**
   * Make sure that non-standard JSDoc annotation is not a hard error nor
   * warning when it is off.
   */
  public void testBug2176967Off() {
    final String badJsDoc = "/** @XYZ */\n var x";
    Compiler compiler = new Compiler();

    CompilerOptions options = createNewFlagBasedOptions();

    options.setWarningLevel(
        DiagnosticGroups.NON_STANDARD_JSDOC, CheckLevel.OFF);
    compiler.compile(SourceFile.fromCode("extern.js", ""),
        SourceFile.fromCode("test.js", badJsDoc), options);
    assertEquals(0, compiler.getWarningCount());
    assertEquals(0, compiler.getErrorCount());
  }

  /**
   * Make sure that non-standard JSDoc annotation is not a hard error nor
   * warning when it is off.
   */
  public void testCoverage() {
    final String original =
        "var name = 1;\n" +
        "function f() {\n" +
        " var name2 = 2;\n" +
        "}\n" +
        "window['f'] = f;\n";
    final String expected =
        "var JSCompiler_lcov_fileNames=JSCompiler_lcov_fileNames||[];" +
        "var JSCompiler_lcov_instrumentedLines=" +
            "JSCompiler_lcov_instrumentedLines||[];" +
        "var JSCompiler_lcov_executedLines=JSCompiler_lcov_executedLines||[];" +
        "var JSCompiler_lcov_data_test_js=[];" +
        "JSCompiler_lcov_executedLines.push(JSCompiler_lcov_data_test_js);" +
        "JSCompiler_lcov_instrumentedLines.push(\"04\");" +
        "JSCompiler_lcov_fileNames.push(\"test.js\");" +
        "var name=1;" +
        "function f(){" +
        "JSCompiler_lcov_data_test_js[2]=true;" +
        "var name2=2" +
        "}" +
        "window[\"f\"]=f;";

    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setInstrumentForCoverage(true);

    compiler.compile(
        SourceFile.fromCode("extern.js", "var window;"),
        SourceFile.fromCode("test.js", original), options);
    assertEquals(0, compiler.getWarningCount());
    assertEquals(0, compiler.getErrorCount());
    String outputSource = compiler.toSource();
    assertEquals(expected, outputSource);
  }

  /**
   * Make sure the non-standard JSDoc diagnostic group gives out an error
   * when it is set to check level error.
   */
  public void testBug2176967Error() {
    final String badJsDoc = "/** @XYZ */\n var x";
    Compiler compiler = new Compiler();

    CompilerOptions options = createNewFlagBasedOptions();

    options.setWarningLevel(
        DiagnosticGroups.NON_STANDARD_JSDOC, CheckLevel.ERROR);
    compiler.compile(SourceFile.fromCode("extern.js", ""),
        SourceFile.fromCode("test.js", badJsDoc), options);
    assertEquals(0, compiler.getWarningCount());
    assertEquals(1, compiler.getErrorCount());
  }

  public void testNormalInputs() {
    CompilerOptions options = new CompilerOptions();
    Compiler compiler = new Compiler();
    List<SourceFile> inputs = ImmutableList.of(
        SourceFile.fromCode("in1", ""),
        SourceFile.fromCode("in2", ""));
    compiler.compile(EMPTY_EXTERNS, inputs, options);

    assertTrue(compiler.getInput(new InputId("externs")).isExtern());
    assertFalse(compiler.getInput(new InputId("in1")).isExtern());
    assertFalse(compiler.getInput(new InputId("in2")).isExtern());
  }

  public void testRebuildInputsFromModule() {
    List<JSModule> modules = ImmutableList.of(
        new JSModule("m1"), new JSModule("m2"));
    modules.get(0).add(SourceFile.fromCode("in1", ""));
    modules.get(1).add(SourceFile.fromCode("in2", ""));

    Compiler compiler = new Compiler();
    compiler.initModules(
        ImmutableList.<SourceFile>of(), modules, new CompilerOptions());

    modules.get(1).add(SourceFile.fromCode("in3", ""));
    assertNull(compiler.getInput(new InputId("in3")));
    compiler.rebuildInputsFromModules();
    assertNotNull(compiler.getInput(new InputId("in3")));
  }

  public void testMalformedFunctionInExterns() throws Exception {
    // Just verify that no exceptions are thrown (see bug 910619).
    new Compiler().compile(
        ImmutableList.of(SourceFile.fromCode("externs", "function f {}")),
        ImmutableList.of(SourceFile.fromCode("foo", "")),
        new CompilerOptions());
  }

  public void testGetSourceInfoInExterns() throws Exception {
    // Just verify that no exceptions are thrown (see bug 910619).
    Compiler compiler = new Compiler();
    compiler.compile(
        ImmutableList.of(SourceFile.fromCode("externs", "function f() {}\n")),
        ImmutableList.of(SourceFile.fromCode("foo", "function g() {}\n")),
        new CompilerOptions());
    assertEquals("function f() {}", compiler.getSourceLine("externs", 1));
    assertEquals("function g() {}", compiler.getSourceLine("foo", 1));
    assertEquals(null, compiler.getSourceLine("bar", 1));
  }

  public void testFileoverviewTwice() throws Exception {
    List<SourceFile> input = ImmutableList.of(
        SourceFile.fromCode("foo",
            "/** @fileoverview */ var x; /** @fileoverview */ var y;"));
    assertTrue(
        (new Compiler()).compile(
            EMPTY_EXTERNS, input, new CompilerOptions()).success);
  }

  // Make sure we correctly output license text.
  public void testLicenseDirectiveOutput() throws Exception {
    test("/** @license Your favorite license goes here */ var x;",
        "/*\n Your favorite license goes here */\n",
        null);
  }

  // Make sure we output license text even if followed by @fileoverview.
  public void testLicenseAndOverviewDirectiveWarning() throws Exception {
    List<SourceFile> input = ImmutableList.of(
      SourceFile.fromCode("foo",
         ("/** @license Your favorite license goes here */\n" +
        "/** \n" +
        "  * @fileoverview This is my favorite file! */\n" +
        "var x;")));
    assertTrue(
        (new Compiler()).compile(
            EMPTY_EXTERNS, input, new CompilerOptions()).success);
  }

  // Text for the opposite order - @fileoverview, then @license.
  public void testOverviewAndLicenseDirectiveOutput() throws Exception {
    test("/** @fileoverview This is my favorite file! */\n" +
        "/** @license Your favorite license goes here */\n" +
        "var x;",
        "/*\n Your favorite license goes here */\n",
        null);
  }

  // Test for sequence of @license and @fileoverview, and make sure
  // all the licenses get copied over.
  public void testLicenseOverviewLicense() throws Exception {
     test("/** @license Another license */\n" +
         "/** @fileoverview This is my favorite file! */\n" +
         "/** @license Your favorite license goes here */\n" +
         "var x;",
         "/*\n Your favorite license goes here  Another license */\n",
         null);
   }

  // Make sure things work even with @license and @fileoverview in the
  // same comment.
  public void testCombinedLicenseOverviewDirectiveOutput() throws Exception {
    test("/** @license Your favorite license goes here\n" +
        " * @fileoverview This is my favorite file! */\n" +
        "var x;",
        "/*\n Your favorite license goes here\n" +
        " @fileoverview This is my favorite file! */\n",
        null);
  }

  // Does the presence of @author change anything with the license?
  public void testCombinedLicenseAuthorDirectiveOutput() throws Exception {
    test("/** @license Your favorite license goes here\n" +
        " * @author Robert */\n" +
        "var x;",
        "/*\n Your favorite license goes here\n @author Robert */\n",
        null);
  }

  // Make sure we concatenate licenses the same way.
  public void testMultipleLicenseDirectiveOutput() throws Exception {
    test("/** @license Your favorite license goes here */\n" +
        "/** @license Another license */\n" +
        "var x;",
        "/*\n Another license  Your favorite license goes here */\n" ,
        null);
  }

  // Same thing, two @licenses in the same comment.
  public void testTwoLicenseInSameComment() throws Exception {
    test("/** @license Your favorite license goes here \n" +
        "  * @license Another license */\n" +
        "var x;",
        "/*\n Your favorite license goes here \n" +
        " @license Another license */\n" ,
        null);
  }

  // Do we correctly handle the license if it's not at the top level, but
  // inside another declaration?
  public void testLicenseInTree() throws Exception {
    test("var a = function() {\n +" +
        "/** @license Your favorite license goes here */\n" +
        " 1;};\n",
        "/*\n Your favorite license goes here */\n" ,
        null);
  }

  public void testDefineNoOverriding() throws Exception {
    Map<String, Node> emptyMap = Maps.newHashMap();
    List<String> defines = Lists.newArrayList();
    assertDefineOverrides(emptyMap, defines);
  }

  public void testDefineOverriding1() throws Exception {
    List<String> defines =
        Lists.newArrayList(
            "COMPILED",
            "DEF_TRUE=true",
            "DEF_FALSE=false",
            "DEF_NUMBER=5.5",
            "DEF_STRING='bye'");
    Map<String, Node> expected = ImmutableMap.of(
        "COMPILED", new Node(Token.TRUE),
        "DEF_TRUE", new Node(Token.TRUE),
        "DEF_FALSE", new Node(Token.FALSE),
        "DEF_NUMBER", Node.newNumber(5.5),
        "DEF_STRING", Node.newString("bye"));
    assertDefineOverrides(expected, defines);
  }

  public void testDefineOverriding2() throws Exception {
    List<String> defines = Lists.newArrayList("DEF_STRING='='");
    Map<String, Node> expected = ImmutableMap.of(
        "DEF_STRING", Node.newString("="));
    assertDefineOverrides(expected, defines);
  }

  public void testDefineOverriding3() throws Exception {
    List<String> defines = Lists.newArrayList("a.DEBUG");
    Map<String, Node> expected = ImmutableMap.of(
        "a.DEBUG", new Node(Token.TRUE));
    assertDefineOverrides(expected, defines);
  }

  public void testBadDefineOverriding1() throws Exception {
    List<String> defines = Lists.newArrayList("DEF_STRING=");
    assertCreateDefinesThrowsException(defines);
  }

  public void testBadDefineOverriding2() throws Exception {
    List<String> defines = Lists.newArrayList("DEF_STRING='xyz");
    assertCreateDefinesThrowsException(defines);
  }

  public void testBadDefineOverriding3() throws Exception {
    List<String> defines = Lists.newArrayList("=true");
    assertCreateDefinesThrowsException(defines);
  }

  public void testBadDefineOverriding4() throws Exception {
    List<String> defines = Lists.newArrayList("DEF_STRING==");
    assertCreateDefinesThrowsException(defines);
  }

  public void testBadDefineOverriding5() throws Exception {
    List<String> defines = Lists.newArrayList("DEF_STRING='");
    assertCreateDefinesThrowsException(defines);
  }

  public void testBadDefineOverriding6() throws Exception {
    List<String> defines = Lists.newArrayList("DEF_STRING='''");
    assertCreateDefinesThrowsException(defines);
  }

  static void assertCreateDefinesThrowsException(List<String> defines) {
    try {
      CompilerOptions options = new CompilerOptions();
      AbstractCommandLineRunner.createDefineOrTweakReplacements(defines,
          options, false);
    } catch (RuntimeException e) {
      return;
    }

    fail();
  }

  static void assertDefineOverrides(Map<String, Node> expected,
      List<String> defines) {
    CompilerOptions options = new CompilerOptions();
    AbstractCommandLineRunner.createDefineOrTweakReplacements(defines, options,
        false);
    Map<String, Node> actual = options.getDefineReplacements();

    // equality of nodes compares by reference, so instead,
    // compare the maps manually using Node.checkTreeEqualsSilent
    assertThat(actual).hasSize(expected.size());
    for (Map.Entry<String, Node> entry : expected.entrySet()) {
      assertTrue(entry.getKey(), actual.containsKey(entry.getKey()));

      Node actualNode = actual.get(entry.getKey());
      assertTrue(entry.toString(),
          entry.getValue().isEquivalentTo(actualNode));
    }
  }

  static Result test(String js, String expected, DiagnosticType error) {
    Compiler compiler = new Compiler();
    CompilerOptions options = createNewFlagBasedOptions();
    List<SourceFile> inputs = ImmutableList.of(
        SourceFile.fromCode("testcode", js));
    Result result = compiler.compile(EMPTY_EXTERNS, inputs, options);

    if (error == null) {
      assertTrue(Joiner.on(",").join(result.errors), result.success);
      String outputSource = compiler.toSource();
      assertEquals(expected, outputSource);
    } else {
      assertThat(result.errors).hasLength(1);
      assertEquals(error, result.errors[0].getType());
    }
    return result;
  }

  public void testConsecutiveSemicolons() {
    Compiler compiler = new Compiler();
    String js = "if(a);";
    Node n = compiler.parseTestCode(js);
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    compiler.toSource(cb, 0, n);
    assertEquals(js, cb.toString());
  }

  public void testWarningsFiltering() {
    // Warnings and errors are left alone when no filtering is used
    assertTrue(hasOutput(
        null,
        "foo/bar.js",
        CheckLevel.WARNING));
    assertTrue(hasOutput(
        null,
        "foo/bar.js",
        CheckLevel.ERROR));

    // Warnings (but not errors) get filtered out
    assertFalse(hasOutput(
        "baz",
        "foo/bar.js",
        CheckLevel.WARNING));
    assertTrue(hasOutput(
        "foo",
        "foo/bar.js",
        CheckLevel.WARNING));
    assertTrue(hasOutput(
        "baz",
        "foo/bar.js",
        CheckLevel.ERROR));
    assertTrue(hasOutput(
        "foo",
        "foo/bar.js",
        CheckLevel.ERROR));
  }

  public void testExportSymbolReservesNamesForRenameVars() {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setClosurePass(true);
    options.setVariableRenaming(VariableRenamingPolicy.ALL);

    String js = "var goog, x; goog.exportSymbol('a', x);";
    List<SourceFile> inputs = ImmutableList.of(
        SourceFile.fromCode("testcode", js));
    Result result = compiler.compile(EMPTY_EXTERNS, inputs, options);

    assertTrue(result.success);
    assertEquals("var b;var c;b.exportSymbol(\"a\",c);", compiler.toSource());
  }

  public void testGenerateExportsReservesNames() {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setClosurePass(true);
    options.setVariableRenaming(VariableRenamingPolicy.ALL);
    options.setGenerateExports(true);

    String js = "var goog; /** @export */ var a={};";
    List<SourceFile> inputs = ImmutableList.of(
        SourceFile.fromCode("testcode", js));
    Result result = compiler.compile(EMPTY_EXTERNS, inputs, options);

    assertTrue(result.success);
    assertEquals("var b;var c={};b.exportSymbol(\"a\",c);",
        compiler.toSource());
  }

  private static final DiagnosticType TEST_ERROR =
      DiagnosticType.error("TEST_ERROR", "Test error");

  /**
   * Simple error manager that tracks whether anything was reported/output.
   */
  private static class TestErrorManager implements ErrorManager {
    private boolean output = false;

    @Override public void report(CheckLevel level, JSError error) {
      output = true;
    }

    // Methods we don't care about
    @Override public void generateReport() {}
    @Override public int getErrorCount() { return 0; }
    @Override public int getWarningCount() { return 0; }
    @Override public JSError[] getErrors() { return null; }
    @Override public JSError[] getWarnings() { return null; }
    @Override public void setTypedPercent(double typedPercent) {}
    @Override public double getTypedPercent() { return 0.0; }
  }

  private boolean hasOutput(
      String showWarningsOnlyFor,
      String path,
      CheckLevel level) {
    TestErrorManager errorManager = new TestErrorManager();
    Compiler compiler = new Compiler(errorManager);
    CompilerOptions options = createNewFlagBasedOptions();
    if (showWarningsOnlyFor != null) {
      options.addWarningsGuard(
          new ShowByPathWarningsGuard(showWarningsOnlyFor));
    }
    compiler.init(ImmutableList.<SourceFile>of(),
        ImmutableList.<SourceFile>of(), options);

    compiler.report(JSError.make(path, 1, 1, level, TEST_ERROR));

    return errorManager.output;
  }

  public void testIdeModeSkipsOptimizations() {
    Compiler compiler = new Compiler();
    CompilerOptions options = createNewFlagBasedOptions();
    options.setIdeMode(true);

    final boolean[] before = new boolean[1];
    final boolean[] after = new boolean[1];

    options.addCustomPass(CustomPassExecutionTime.BEFORE_OPTIMIZATIONS,
                     new CompilerPass() {
                       @Override public void process(Node externs, Node root) {
                         before[0] = true;
                       }
                     });

    options.addCustomPass(CustomPassExecutionTime.BEFORE_OPTIMIZATION_LOOP,
                     new CompilerPass() {
                       @Override public void process(Node externs, Node root) {
                         after[0] = true;
                       }
                     });

    String js = "var x = 1;";
    List<SourceFile> inputs = ImmutableList.of(
        SourceFile.fromCode("testcode", js));
    compiler.compile(EMPTY_EXTERNS, inputs, options);

    assertTrue(before[0]);  // should run these custom passes
    assertFalse(after[0]);  // but not these
  }

  public void testAdditionalReplacementsForClosure() {
    CompilerOptions options = createNewFlagBasedOptions();
    options.setLocale("it_IT");
    options.setClosurePass(true);

    Map<String, Node> replacements = DefaultPassConfig.getAdditionalReplacements(options);

    assertThat(replacements).hasSize(2);
    assertEquals("it_IT", replacements.get("goog.LOCALE").getString());
  }

  public void testInputSerialization() throws Exception {
    Compiler compiler = new Compiler();
    compiler.initCompilerOptionsIfTesting();
    CompilerInput input = new CompilerInput(SourceFile.fromCode(
          "tmp", "function foo() {}"));
    Node ast = input.getAstRoot(compiler);
    CompilerInput newInput = (CompilerInput) deserialize(serialize(input));
    assertTrue(ast.isEquivalentTo(newInput.getAstRoot(compiler)));
  }

  public void testTargetSpecificCompiles() throws Exception {
    String testExterns = ""
        + "var window;"
        + "/** @param {string} str */"
        + "function alert(str) {}";
    String testCode = ""
        + "/** @define {number} */"
        + "var mydef = 0;"
        + "if (mydef == 1) {"
        + "  alert('1');"
        + "} else if (mydef == 2) {"
        + "  alert('2');"
        + "} else if (mydef == 3) {"
        + "  alert('3');"
        + "} else { "
        + "  alert('4');"
        + "}";

    CompilerOptions options = createNewFlagBasedOptions();
    Compiler compiler = new Compiler();
    compiler.init(
        ImmutableList.of(SourceFile.fromCode("ext.js", testExterns)),
        ImmutableList.of(SourceFile.fromCode("in1.js", testCode)),
        options);
    compiler.parse();
    compiler.check();

    byte[] savedState = serialize(compiler.getState());
    for (int num = 1; num <= 3; ++num) {
      compiler.setState((Compiler.IntermediateState) deserialize(savedState));

      options.setDefineToNumberLiteral("mydef", num);
      compiler.processDefines();
      compiler.optimize();
      assertEquals("alert(\"" + num + "\");", compiler.toSource());
    }
  }

  public void testGetEmptyResult() {
    Result result = new Compiler().getResult();
    assertThat(result.errors).isEmpty();
  }

  private static CompilerOptions createNewFlagBasedOptions() {
    CompilerOptions opt = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(opt);
    return opt;
  }

  private static byte[] serialize(Object obj) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream out = new ObjectOutputStream(baos);
    out.writeObject(obj);
    out.close();
    return baos.toByteArray();
  }

  private static Object deserialize(byte[] bytes)
      throws IOException, ClassNotFoundException {
    ObjectInputStream in =
        new ObjectInputStream(new ByteArrayInputStream(bytes));
    Object obj = in.readObject();
    in.close();
    return obj;
  }
}
