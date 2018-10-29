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
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.CompilerTestCase.lines;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.debugging.sourcemap.FilePosition;
import com.google.debugging.sourcemap.SourceMapConsumerV3;
import com.google.debugging.sourcemap.SourceMapGeneratorV3;
import com.google.debugging.sourcemap.proto.Mapping.OriginalMapping;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile.SourceKind;
import com.google.javascript.rhino.Token;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** @author johnlenz@google.com (John Lenz) */

@RunWith(JUnit4.class)
public final class CompilerTest {

  // Verify the line and column information is maintained after a reset
  @Test
  public void testCodeBuilderColumnAfterReset() {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    String js = "foo();\ngoo();";
    cb.append(js);
    assertThat(cb.toString()).isEqualTo(js);
    assertThat(cb.getLineIndex()).isEqualTo(1);
    assertThat(cb.getColumnIndex()).isEqualTo(6);

    cb.reset();

    assertThat(cb.toString()).isEmpty();
    assertThat(cb.getLineIndex()).isEqualTo(1);
    assertThat(cb.getColumnIndex()).isEqualTo(6);
  }

  @Test
  public void testCodeBuilderAppend() {
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    cb.append("foo();");
    assertThat(cb.getLineIndex()).isEqualTo(0);
    assertThat(cb.getColumnIndex()).isEqualTo(6);

    cb.append("goo();");

    assertThat(cb.getLineIndex()).isEqualTo(0);
    assertThat(cb.getColumnIndex()).isEqualTo(12);

    // newline reset the column index
    cb.append("blah();\ngoo();");

    assertThat(cb.getLineIndex()).isEqualTo(1);
    assertThat(cb.getColumnIndex()).isEqualTo(6);
  }

  @Test
  public void testCyclicalDependencyInInputs() {
    List<SourceFile> inputs = ImmutableList.of(
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
    assertThat(compiler.jsRoot.getParent()).isEqualTo(compiler.externAndJsRoot);
    assertThat(compiler.externsRoot.getParent()).isEqualTo(compiler.externAndJsRoot);
    assertThat(compiler.externAndJsRoot).isNotNull();

    Node jsRoot = compiler.jsRoot;
    assertThat(jsRoot.getChildCount()).isEqualTo(3);
  }

  @Test
  public void testPrintExterns() {
    List<SourceFile> externs =
        ImmutableList.of(SourceFile.fromCode("extern", "/** @externs */ function alert(x) {}"));
    CompilerOptions options = new CompilerOptions();
    options.setPreserveTypeAnnotations(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.setPrintExterns(true);
    Compiler compiler = new Compiler();
    compiler.init(externs, ImmutableList.<SourceFile>of(), options);
    compiler.parseInputs();
    assertThat(compiler.toSource()).isEqualTo("/** @externs */ function alert(x){};");
  }

  @Test
  public void testLocalUndefined() {
    // Some JavaScript libraries like to create a local instance of "undefined",
    // to ensure that other libraries don't try to overwrite it.
    //
    // Most of the time, this is OK, because normalization will rename
    // that variable to undefined$$1. But this won't happen if they don't
    // include the default externs.
    //
    // This test is just to make sure that the compiler doesn't crash.
    CompilerOptions options = new CompilerOptions();
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    Compiler compiler = new Compiler();
    SourceFile externs = SourceFile.fromCode("externs.js", "");
    SourceFile input = SourceFile.fromCode("input.js",
        "(function (undefined) { alert(undefined); })();");
    compiler.compile(externs, input, options);
  }

  private static String normalize(String path) {
    return path.replace(File.separator, "/");
  }

  @Test
  public void testInputSourceMaps() throws Exception {
    FilePosition originalSourcePosition = new FilePosition(17, 25);
    ImmutableMap<String, SourceMapInput> inputSourceMaps = ImmutableMap.of(
        normalize("generated_js/example.js"),
        sourcemap(
            normalize("generated_js/example.srcmap"),
            normalize("../original/source.html"),
            originalSourcePosition));
    String origSourceName = normalize("original/source.html");
    List<SourceFile> originalSources = ImmutableList.of(
        SourceFile.fromCode(origSourceName, "<div ng-show='foo()'>"));

    CompilerOptions options = new CompilerOptions();
    options.inputSourceMaps = inputSourceMaps;
    Compiler compiler = new Compiler();
    compiler.init(new ArrayList<SourceFile>(), originalSources, options);

    assertThat(compiler.getSourceMapping(normalize("generated_js/example.js"), 3, 3))
        .isEqualTo(
            OriginalMapping.newBuilder()
                .setOriginalFile(origSourceName)
                .setLineNumber(18)
                .setColumnPosition(25)
                .setIdentifier("testSymbolName")
                .build());
    assertThat(compiler.getSourceLine(origSourceName, 1)).isEqualTo("<div ng-show='foo()'>");
  }

  private SourceMapInput sourcemap(String sourceMapPath, String originalSource,
      FilePosition originalSourcePosition) throws Exception {
    SourceMapGeneratorV3 sourceMap = new SourceMapGeneratorV3();
    sourceMap.addMapping(originalSource, "testSymbolName", originalSourcePosition,
        new FilePosition(1, 1), new FilePosition(100, 1));
    StringBuilder output = new StringBuilder();
    sourceMap.appendTo(output, "unused.js");

    return new SourceMapInput(SourceFile.fromCode(sourceMapPath, output.toString()));
  }

  private static final String SOURCE_MAP_TEST_CODE =
      Joiner.on("\n")
          .join(
              "var X = (function () {",
              "    function X(input) {",
              "        this.y = input;",
              "    }",
              "    return X;",
              "}());",
              "console.log(new X(1));");

  private static final String SOURCE_MAP =
      "{\"version\":3,\"file\":\"foo.js\",\"sourceRoot\":\"\",\"sources\":[\"foo.ts\"],\"names\":[],\"mappings\":\"AAAA;IAGE,WAAY,KAAa;QACvB,IAAI,CAAC,CAAC,GAAG,KAAK,CAAC;IACjB,CAAC;IACH,QAAC;AAAD,CAAC,AAND,IAMC;AAED,OAAO,CAAC,GAAG,CAAC,IAAI,CAAC,CAAC,CAAC,CAAC,CAAC,CAAC\"}";
  private static final String BASE64_ENCODED_SOURCE_MAP =
      "data:application/json;base64,eyJ2ZXJzaW9uIjozLCJmaWxlIjoiZm9vLmpzIiwic291cmNlUm9vdCI6IiIsInNvdXJjZXMiOlsiZm9vLnRzIl0sIm5hbWVzIjpbXSwibWFwcGluZ3MiOiJBQUFBO0lBR0UsV0FBWSxLQUFhO1FBQ3ZCLElBQUksQ0FBQyxDQUFDLEdBQUcsS0FBSyxDQUFDO0lBQ2pCLENBQUM7SUFDSCxRQUFDO0FBQUQsQ0FBQyxBQU5ELElBTUM7QUFFRCxPQUFPLENBQUMsR0FBRyxDQUFDLElBQUksQ0FBQyxDQUFDLENBQUMsQ0FBQyxDQUFDLENBQUMifQ==";

  @Test
  public void testInputSourceMapInline() {
    Compiler compiler = new Compiler();
    compiler.initCompilerOptionsIfTesting();
    String code = SOURCE_MAP_TEST_CODE + "\n//# sourceMappingURL=" + BASE64_ENCODED_SOURCE_MAP;
    CompilerInput input = new CompilerInput(SourceFile.fromCode("tmp", code));
    input.getAstRoot(compiler);
    SourceMapInput inputSourceMap = compiler.inputSourceMaps.get("tmp");
    SourceMapConsumerV3 sourceMap = inputSourceMap.getSourceMap(null);
    assertThat(sourceMap.getOriginalSources()).containsExactly("foo.ts");
    assertThat(sourceMap.getOriginalSourcesContent()).isNull();
  }

  private static final String SOURCE_MAP_TEST_CONTENT =
      Joiner.on("\n")
          .join(
              "var A = (function () {",
              "    function A(input) {",
              "        this.a = input;",
              "    }",
              "    return A;",
              "}());",
              "console.log(new A(1));");

  // Similar to BASE64_ENCODED_SOURCE_MAP; contains encoded SOURCE_MAP but with
  // SOURCE_MAP_TEST_CONTENT as the only item of sourcesContent corresponding
  // to "../test/foo.ts" sources item.
  private static final String BASE64_ENCODED_SOURCE_MAP_WITH_CONTENT =
      "data:application/json;base64,eyJ2ZXJzaW9uIjozLCJmaWxlIjoiZm9vLmpzIiwic291cmNlUm9vdCI6IiIsInNvdXJjZXMiOlsiLi4vdGVzdC9mb28udHMiXSwic291cmNlc0NvbnRlbnQiOlsidmFyIEEgPSAoZnVuY3Rpb24gKCkge1xuICAgIGZ1bmN0aW9uIEEoaW5wdXQpIHtcbiAgICAgICAgdGhpcy5hID0gaW5wdXQ7XG4gICAgfVxuICAgIHJldHVybiBBO1xufSgpKTtcbmNvbnNvbGUubG9nKG5ldyBBKDEpKTsiXSwibmFtZXMiOltdLCJtYXBwaW5ncyI6IkFBQUE7SUFHRSxXQUFZLEtBQWE7UUFDdkIsSUFBSSxDQUFDLENBQUMsR0FBRyxLQUFLLENBQUM7SUFDakIsQ0FBQztJQUNILFFBQUM7QUFBRCxDQUFDLEFBTkQsSUFNQztBQUVELE9BQU8sQ0FBQyxHQUFHLENBQUMsSUFBSSxDQUFDLENBQUMsQ0FBQyxDQUFDLENBQUMsQ0FBQyJ9";

  @Test
  public void testInputSourceMapInlineContent() {
    Compiler compiler = new Compiler();
    compiler.initCompilerOptionsIfTesting();
    String code =
        SOURCE_MAP_TEST_CODE + "\n//# sourceMappingURL=" + BASE64_ENCODED_SOURCE_MAP_WITH_CONTENT;
    CompilerInput input = new CompilerInput(SourceFile.fromCode("tmp", code));
    input.getAstRoot(compiler);
    SourceMapInput inputSourceMap = compiler.inputSourceMaps.get("tmp");
    SourceMapConsumerV3 sourceMap = inputSourceMap.getSourceMap(null);
    assertThat(sourceMap.getOriginalSources()).containsExactly("../test/foo.ts");
    assertThat(sourceMap.getOriginalSourcesContent()).containsExactly(SOURCE_MAP_TEST_CONTENT);
  }

  @Test
  public void testResolveRelativeSourceMap() throws Exception {
    Compiler compiler = new Compiler();
    compiler.initCompilerOptionsIfTesting();
    File tempDir = Files.createTempDir();
    String code = SOURCE_MAP_TEST_CODE + "\n//# sourceMappingURL=foo.js.map";
    File jsFile = new File(tempDir, "foo.js");
    Files.asCharSink(jsFile, UTF_8).write(code);
    File sourceMapFile = new File(tempDir, "foo.js.map");
    Files.asCharSink(sourceMapFile, UTF_8).write(SOURCE_MAP);

    CompilerInput input = new CompilerInput(SourceFile.fromFile(jsFile.getAbsolutePath()));
    input.getAstRoot(compiler);
    // The source map is still
    assertThat(compiler.inputSourceMaps).hasSize(1);

    for (SourceMapInput inputSourceMap : compiler.inputSourceMaps.values()) {
      SourceMapConsumerV3 sourceMap = inputSourceMap.getSourceMap(null);
      assertThat(sourceMap.getOriginalSources()).containsExactly("foo.ts");
    }
  }

  // Make sure that the sourcemap resolution can find a sourcemap in a relative directory.
  @Test
  public void testResolveRelativeDirSourceMap() throws Exception {
    Compiler compiler = new Compiler();
    compiler.initCompilerOptionsIfTesting();
    File tempDir = Files.createTempDir();
    File relativedir = new File(tempDir, "/relativedir");
    relativedir.mkdir();
    String code = SOURCE_MAP_TEST_CODE + "\n//# sourceMappingURL=relativedir/foo.js.map";
    File jsFile = new File(tempDir, "foo.js");
    Files.asCharSink(jsFile, UTF_8).write(code);
    File sourceMapFile = new File(relativedir, "foo.js.map");
    Files.asCharSink(sourceMapFile, UTF_8).write(SOURCE_MAP);

    CompilerInput input = new CompilerInput(SourceFile.fromFile(jsFile.getAbsolutePath()));
    input.getAstRoot(compiler);
    // The source map is still
    assertThat(compiler.inputSourceMaps).hasSize(1);

    for (SourceMapInput inputSourceMap : compiler.inputSourceMaps.values()) {
      SourceMapConsumerV3 sourceMap = inputSourceMap.getSourceMap(null);
      assertThat(sourceMap.getOriginalSources()).containsExactly("foo.ts");
    }
  }

  @Test
  public void testMissingSourceMapFile() throws Exception {
    Compiler compiler = new Compiler();
    compiler.initCompilerOptionsIfTesting();
    File tempDir = Files.createTempDir();
    String code = SOURCE_MAP_TEST_CODE + "\n//# sourceMappingURL=foo-does-not-exist.js.map";
    File jsFile = new File(tempDir, "foo2.js");
    Files.asCharSink(jsFile, UTF_8).write(code);

    CompilerInput input = new CompilerInput(SourceFile.fromFile(jsFile.getAbsolutePath()));
    input.getAstRoot(compiler);
    assertThat(compiler.inputSourceMaps).hasSize(1);

    TestErrorManager errorManager = new TestErrorManager();
    for (SourceMapInput inputSourceMap : compiler.inputSourceMaps.values()) {
      SourceMapConsumerV3 sourceMap = inputSourceMap.getSourceMap(errorManager);
      assertThat(sourceMap).isNull();
    }

    // WARNING: Failed to resolve input sourcemap: foo-does-not-exist.js.map
    assertThat(errorManager.getWarningCount()).isEqualTo(1);
  }

  @Test
  public void testNoWarningMissingAbsoluteSourceMap() throws Exception {
    TestErrorManager errorManager = new TestErrorManager();
    Compiler compiler = new Compiler(errorManager);
    compiler.initCompilerOptionsIfTesting();
    File tempDir = Files.createTempDir();
    String code = SOURCE_MAP_TEST_CODE + "\n//# sourceMappingURL=/some/missing/path/foo.js.map";
    File jsFile = new File(tempDir, "foo.js");
    Files.asCharSink(jsFile, UTF_8).write(code);

    CompilerInput input = new CompilerInput(SourceFile.fromFile(jsFile.getAbsolutePath()));
    input.getAstRoot(compiler);
    // The source map is still
    assertThat(compiler.inputSourceMaps).isEmpty();

    // No warnings for unresolved absolute paths.
    assertThat(errorManager.getWarningCount()).isEqualTo(0);
  }

  @Test
  public void testApplyInputSourceMaps() throws Exception {
    FilePosition originalSourcePosition = new FilePosition(17, 25);
    ImmutableMap<String, SourceMapInput> inputSourceMaps = ImmutableMap.of(
        "input.js",
        sourcemap(
            "input.js.map",
            "input.ts",
            originalSourcePosition));

    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.sourceMapOutputPath = "fake/source_map_path.js.map";
    options.inputSourceMaps = inputSourceMaps;
    options.applyInputSourceMaps = true;
    Compiler compiler = new Compiler();
    compiler.compile(EMPTY_EXTERNS.get(0),
        SourceFile.fromCode("input.js", "// Unmapped line\nvar x = 1;\nalert(x);"), options);
    assertThat(compiler.toSource()).isEqualTo("var x=1;alert(x);");
    SourceMap sourceMap = compiler.getSourceMap();
    StringWriter out = new StringWriter();
    sourceMap.appendTo(out, "source.js.map");
    SourceMapConsumerV3 consumer = new SourceMapConsumerV3();
    consumer.parse(out.toString());
    // Column 5 contains the first actually mapped code ('x').
    OriginalMapping mapping = consumer.getMappingForLine(1, 5);
    assertThat(mapping.getOriginalFile()).isEqualTo("input.ts");
    // FilePosition above is 0-based, whereas OriginalMapping is 1-based, thus 18 & 26.
    assertThat(mapping.getLineNumber()).isEqualTo(18);
    assertThat(mapping.getColumnPosition()).isEqualTo(26);
    assertThat(mapping.getIdentifier()).isEqualTo("testSymbolName");
    assertThat(consumer.getOriginalSources()).containsExactly("input.js", "input.ts");
    assertThat(consumer.getOriginalSourcesContent()).isNull();
  }

  @Test
  public void testKeepInputSourceMapsSourcesContent() throws Exception {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.sourceMapOutputPath = "fake/source_map_path.js.map";
    options.applyInputSourceMaps = true;
    options.sourceMapIncludeSourcesContent = true;
    String code = SOURCE_MAP_TEST_CODE + "\n//# sourceMappingURL="
        + BASE64_ENCODED_SOURCE_MAP_WITH_CONTENT;
    Compiler compiler = new Compiler();
    SourceFile sourceFile = SourceFile.fromCode("temp/path/input.js", code);
    compiler.compile(EMPTY_EXTERNS.get(0), sourceFile, options);
    assertThat(compiler.toSource()).isEqualTo(
        "var X=function(){function X(input){this.y=input}return X}();console.log(new X(1));");
    SourceMap sourceMap = compiler.getSourceMap();
    StringWriter out = new StringWriter();
    sourceMap.appendTo(out, "source.js.map");
    SourceMapConsumerV3 consumer = new SourceMapConsumerV3();
    consumer.parse(out.toString());
    assertThat(consumer.getOriginalSources()).containsExactly("temp/test/foo.ts");
    assertThat(consumer.getOriginalSourcesContent()).containsExactly(SOURCE_MAP_TEST_CONTENT);
  }

  @Test
  public void testNoSourceMapIsGeneratedWithoutPath() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.applyInputSourceMaps = true;
    options.sourceMapIncludeSourcesContent = true;
    String code = SOURCE_MAP_TEST_CODE + "\n//# sourceMappingURL="
        + BASE64_ENCODED_SOURCE_MAP_WITH_CONTENT;
    Compiler compiler = new Compiler();
    SourceFile sourceFile = SourceFile.fromCode("input.js", code);
    compiler.compile(EMPTY_EXTERNS.get(0), sourceFile, options);
    assertThat(compiler.getSourceMap()).isNull();
  }

  private static final ImmutableList<SourceFile> EMPTY_EXTERNS =
      ImmutableList.of(SourceFile.fromCode("externs", ""));

  /**
   * Ensure that the printInputDelimiter option adds a "// Input #" comment at the start of each
   * "script" in the compiled output.
   */
  @Test
  public void testInputDelimiters() {
    Compiler compiler = new Compiler();
    CompilerOptions options = createNewFlagBasedOptions();
    options.setPrintInputDelimiter(true);

    String fileOverview = "/** @fileoverview Foo */";
    List<SourceFile> inputs = ImmutableList.of(
        SourceFile.fromCode("i1", ""),
        SourceFile.fromCode("i2", fileOverview));

    Result result = compiler.compile(EMPTY_EXTERNS, inputs, options);
    assertThat(result.success).isTrue();

    String outputSource = compiler.toSource();
    System.err.println("Output:\n[" + outputSource + "]");
    assertThat(outputSource).isEqualTo("// Input 0\n// Input 1\n");
  }

  /** Make sure that non-standard JSDoc annotation is not a hard error unless it is specified. */
  @Test
  public void testBug2176967Default() {
    final String badJsDoc = "/** @XYZ */\n var x";
    Compiler compiler = new Compiler();

    CompilerOptions options = createNewFlagBasedOptions();

    // Default is warning.
    compiler.compile(SourceFile.fromCode("extern.js", ""),
        SourceFile.fromCode("test.js", badJsDoc), options);
    assertThat(compiler.getWarningCount()).isEqualTo(1);
    assertThat(compiler.getErrorCount()).isEqualTo(0);
  }

  /**
   * Make sure that non-standard JSDoc annotation is not a hard error nor warning when it is off.
   */
  @Test
  public void testBug2176967Off() {
    final String badJsDoc = "/** @XYZ */\n var x";
    Compiler compiler = new Compiler();

    CompilerOptions options = createNewFlagBasedOptions();

    options.setWarningLevel(
        DiagnosticGroups.NON_STANDARD_JSDOC, CheckLevel.OFF);
    compiler.compile(SourceFile.fromCode("extern.js", ""),
        SourceFile.fromCode("test.js", badJsDoc), options);
    assertThat(compiler.getWarningCount()).isEqualTo(0);
    assertThat(compiler.getErrorCount()).isEqualTo(0);
  }

  /**
   * Make sure the non-standard JSDoc diagnostic group gives out an error when it is set to check
   * level error.
   */
  @Test
  public void testBug2176967Error() {
    final String badJsDoc = "/** @XYZ */\n var x";
    Compiler compiler = new Compiler();

    CompilerOptions options = createNewFlagBasedOptions();

    options.setWarningLevel(
        DiagnosticGroups.NON_STANDARD_JSDOC, CheckLevel.ERROR);
    compiler.compile(SourceFile.fromCode("extern.js", ""),
        SourceFile.fromCode("test.js", badJsDoc), options);
    assertThat(compiler.getWarningCount()).isEqualTo(0);
    assertThat(compiler.getErrorCount()).isEqualTo(1);
  }

  @Test
  public void testNormalInputs() {
    CompilerOptions options = new CompilerOptions();
    Compiler compiler = new Compiler();
    List<SourceFile> inputs = ImmutableList.of(
        SourceFile.fromCode("in1", ""),
        SourceFile.fromCode("in2", ""));
    compiler.compile(EMPTY_EXTERNS, inputs, options);

    assertThat(compiler.getInput(new InputId("externs")).isExtern()).isTrue();
    assertThat(compiler.getInput(new InputId("in1")).isExtern()).isFalse();
    assertThat(compiler.getInput(new InputId("in2")).isExtern()).isFalse();
  }

  @Test
  public void testRebuildInputsFromModule() {
    List<JSModule> modules = ImmutableList.of(
        new JSModule("m1"), new JSModule("m2"));
    modules.get(0).add(SourceFile.fromCode("in1", ""));
    modules.get(1).add(SourceFile.fromCode("in2", ""));

    Compiler compiler = new Compiler();
    compiler.initModules(
        ImmutableList.<SourceFile>of(), modules, new CompilerOptions());

    modules.get(1).add(SourceFile.fromCode("in3", ""));
    assertThat(compiler.getInput(new InputId("in3"))).isNull();
    compiler.rebuildInputsFromModules();
    assertThat(compiler.getInput(new InputId("in3"))).isNotNull();
  }

  @Test
  public void testMalformedFunctionInExterns() {
    // Just verify that no exceptions are thrown (see bug 910619).
    new Compiler().compile(
        ImmutableList.of(SourceFile.fromCode("externs", "function f {}")),
        ImmutableList.of(SourceFile.fromCode("foo", "")),
        new CompilerOptions());
  }

  @Test
  public void testGetSourceInfoInExterns() {
    // Just verify that no exceptions are thrown (see bug 910619).
    Compiler compiler = new Compiler();
    compiler.compile(
        ImmutableList.of(SourceFile.fromCode("externs", "function f() {}\n")),
        ImmutableList.of(SourceFile.fromCode("foo", "function g() {}\n")),
        new CompilerOptions());
    assertThat(compiler.getSourceLine("externs", 1)).isEqualTo("function f() {}");
    assertThat(compiler.getSourceLine("foo", 1)).isEqualTo("function g() {}");
    assertThat(compiler.getSourceLine("bar", 1)).isNull();
  }

  @Test
  public void testFileoverviewTwice() {
    List<SourceFile> input = ImmutableList.of(
        SourceFile.fromCode("foo",
            "/** @fileoverview */ var x; /** @fileoverview */ var y;"));
    assertThat(new Compiler().compile(EMPTY_EXTERNS, input, new CompilerOptions()).success)
        .isTrue();
  }

  // Make sure we correctly output license text.
  @Test
  public void testImportantCommentOutput() {
    test(
        "/*! Your favorite license goes here */ var x;",
        "/*\n Your favorite license goes here */\n",
        null);
  }

  // Make sure we output license text even if followed by @fileoverview.
  @Test
  public void testImportantCommentAndOverviewDirectiveWarning() {
    List<SourceFile> input =
        ImmutableList.of(
            SourceFile.fromCode(
                "foo",
                ("/*! Your favorite license goes here */\n"
                    + "/** \n"
                    + "  * @fileoverview This is my favorite file! */\n"
                    + "var x;")));
    assertThat(new Compiler().compile(EMPTY_EXTERNS, input, new CompilerOptions()).success)
        .isTrue();
  }

  // Text for the opposite order - @fileoverview, then @license.
  @Test
  public void testOverviewAndImportantCommentOutput() {
    test(
        "/** @fileoverview This is my favorite file! */\n"
            + "/*! Your favorite license goes here */\n"
            + "var x;",
        "/*\n Your favorite license goes here */\n",
        null);
  }

  // Test for sequence of @license and @fileoverview, and make sure
  // all the licenses get copied over.
  @Test
  public void testImportantCommentOverviewImportantComment() {
    test(
        "/*! Another license */\n"
            + "/** @fileoverview This is my favorite file! */\n"
            + "/*! Your favorite license goes here */\n"
            + "var x;",
        "/*\n Another license  Your favorite license goes here */\n",
        null);
   }

  // Make sure things work even with @license and @fileoverview in the
  // same comment.
  @Test
  public void testCombinedImportantCommentOverviewDirectiveOutput() {
    test(
        "/*! Your favorite license goes here\n"
            + " * @fileoverview This is my favorite file! */\n"
            + "var x;",
        "/*\n Your favorite license goes here\n" + " @fileoverview This is my favorite file! */\n",
        null);
  }

  // Does the presence of @author change anything with the license?
  @Test
  public void testCombinedImportantCommentAuthorDirectiveOutput() {
    test(
        "/*! Your favorite license goes here\n" + " * @author Robert */\n" + "var x;",
        "/*\n Your favorite license goes here\n @author Robert */\n",
        null);
  }

  // Make sure we concatenate licenses the same way.
  @Test
  public void testMultipleImportantCommentDirectiveOutput() {
    test(
        "/*! Your favorite license goes here */\n" + "/*! Another license */\n" + "var x;",
        "/*\n Your favorite license goes here  Another license */\n",
        null);
  }

  @Test
  public void testImportantCommentLicenseDirectiveOutput() {
    test(
        "/*! Your favorite license goes here */\n" + "/** @license Another license */\n" + "var x;",
        "/*\n Another license  Your favorite license goes here */\n",
        null);
  }

  @Test
  public void testLicenseImportantCommentDirectiveOutput() {
    test(
        "/** @license Your favorite license goes here */\n" + "/*! Another license */\n" + "var x;",
        "/*\n Your favorite license goes here  Another license */\n",
        null);
  }

  // Do we correctly handle the license if it's not at the top level, but
  // inside another declaration?
  @Test
  public void testImportantCommentInTree() {
    test(
        "var a = function() {\n +" + "/*! Your favorite license goes here */\n" + " 1;};\n",
        "/*\n Your favorite license goes here */\n",
        null);
  }

  @Test
  public void testMultipleUniqueImportantComments() {
    String js1 = "/*! One license here */\n" + "var x;";
    String js2 = "/*! Another license here */\n" + "var y;";
    String expected = "/*\n One license here */\n" + "/*\n Another license here */\n";

    Compiler compiler = new Compiler();
    CompilerOptions options = createNewFlagBasedOptions();
    List<SourceFile> inputs =
        ImmutableList.of(
            SourceFile.fromCode("testcode1", js1), SourceFile.fromCode("testcode2", js2));
    Result result = compiler.compile(EMPTY_EXTERNS, inputs, options);

    assertWithMessage(Joiner.on(",").join(result.errors)).that(result.success).isTrue();
    assertThat(compiler.toSource()).isEqualTo(expected);
  }

  @Test
  public void testMultipleIdenticalImportantComments() {
    String js1 = "/*! Identical license here */\n" + "var x;";
    String js2 = "/*! Identical license here */\n" + "var y;";
    String expected = "/*\n Identical license here */\n";

    Compiler compiler = new Compiler();
    CompilerOptions options = createNewFlagBasedOptions();
    List<SourceFile> inputs =
        ImmutableList.of(
            SourceFile.fromCode("testcode1", js1), SourceFile.fromCode("testcode2", js2));
    Result result = compiler.compile(EMPTY_EXTERNS, inputs, options);

    assertWithMessage(Joiner.on(",").join(result.errors)).that(result.success).isTrue();
    assertThat(compiler.toSource()).isEqualTo(expected);
  }

  // Make sure we correctly output license text.
  @Test
  public void testLicenseDirectiveOutput() {
    test(
        "/** @license Your favorite license goes here */ var x;",
        "/*\n Your favorite license goes here */\n",
        null);
  }

  // Make sure we output license text even if followed by @fileoverview.
  @Test
  public void testLicenseAndOverviewDirectiveWarning() {
    List<SourceFile> input =
        ImmutableList.of(
            SourceFile.fromCode(
                "foo",
                ("/** @license Your favorite license goes here */\n"
                    + "/** \n"
                    + "  * @fileoverview This is my favorite file! */\n"
                    + "var x;")));
    assertThat(new Compiler().compile(EMPTY_EXTERNS, input, new CompilerOptions()).success)
        .isTrue();
  }

  // Text for the opposite order - @fileoverview, then @license.
  @Test
  public void testOverviewAndLicenseDirectiveOutput() {
    test(
        "/** @fileoverview This is my favorite file! */\n"
            + "/** @license Your favorite license goes here */\n"
            + "var x;",
        "/*\n Your favorite license goes here */\n",
        null);
  }

  // Test for sequence of @license and @fileoverview, and make sure
  // all the licenses get copied over.
  @Test
  public void testLicenseOverviewLicense() {
    test(
        "/** @license Another license */\n"
            + "/** @fileoverview This is my favorite file! */\n"
            + "/** @license Your favorite license goes here */\n"
            + "var x;",
        "/*\n Your favorite license goes here  Another license */\n",
        null);
  }

  // Make sure things work even with @license and @fileoverview in the
  // same comment.
  @Test
  public void testCombinedLicenseOverviewDirectiveOutput() {
    test(
        "/** @license Your favorite license goes here\n"
            + " * @fileoverview This is my favorite file! */\n"
            + "var x;",
        "/*\n Your favorite license goes here\n" + " @fileoverview This is my favorite file! */\n",
        null);
  }

  // Does the presence of @author change anything with the license?
  @Test
  public void testCombinedLicenseAuthorDirectiveOutput() {
    test(
        "/** @license Your favorite license goes here\n" + " * @author Robert */\n" + "var x;",
        "/*\n Your favorite license goes here\n @author Robert */\n",
        null);
  }

  // Make sure we concatenate licenses the same way.
  @Test
  public void testMultipleLicenseDirectiveOutput() {
    test(
        lines(
            "/** @license Your favorite license goes here */",
            "/** @license Another license */",
            "var x;"),
        "/*\n Another license  Your favorite license goes here */\n" ,
        null);
  }

  // Same thing, two @licenses in the same comment.
  @Test
  public void testTwoLicenseInSameComment() {
    test(
        lines(
            "/** @license Your favorite license goes here ",
            "  * @license Another license */",
            "var x;"),
        "/*\n Your favorite license goes here \n @license Another license */\n",
        null);
  }

  // Do we correctly handle the license if it's not at the top level, but
  // inside another declaration?
  @Test
  public void testLicenseInTree() {
    test(
        lines(
            "var a = function() {",
            "+ /** @license Your favorite license goes here */",
            " 1;};"),
        "/*\n Your favorite license goes here */\n",
        null);
  }

  @Test
  public void testMultipleUniqueLicenses() {
    String js1 = "/** @license One license here */\n"
                 + "var x;";
    String js2 = "/** @license Another license here */\n"
                 + "var y;";
    String expected = "/*\n One license here */\n"
                      + "/*\n Another license here */\n";

    Compiler compiler = new Compiler();
    CompilerOptions options = createNewFlagBasedOptions();
    List<SourceFile> inputs = ImmutableList.of(
        SourceFile.fromCode("testcode1", js1),
        SourceFile.fromCode("testcode2", js2));
    Result result = compiler.compile(EMPTY_EXTERNS, inputs, options);

    assertWithMessage(Joiner.on(",").join(result.errors)).that(result.success).isTrue();
    assertThat(compiler.toSource()).isEqualTo(expected);
  }

  @Test
  public void testMultipleIdenticalLicenses() {
    String js1 = "/** @license Identical license here */\n"
                 + "var x;";
    String js2 = "/** @license Identical license here */\n"
                 + "var y;";
    String js3 = "/** @license Identical license here */\n"
                 + "var z;\n"
                 + "/** @license Identical license here */";
    String expected = "/*\n Identical license here */\n";

    Compiler compiler = new Compiler();
    CompilerOptions options = createNewFlagBasedOptions();
    List<SourceFile> inputs = ImmutableList.of(
        SourceFile.fromCode("testcode1", js1),
        SourceFile.fromCode("testcode2", js2),
        SourceFile.fromCode("bundled", js3));
    Result result = compiler.compile(EMPTY_EXTERNS, inputs, options);

    assertWithMessage(Joiner.on(",").join(result.errors)).that(result.success).isTrue();
    assertThat(compiler.toSource()).isEqualTo(expected);
  }

  @Test
  public void testIdenticalLicenseAndImportantComment() {
    String js1 = "/** @license Identical license here */\n" + "var x;";
    String js2 = "/*! Identical license here */\n" + "var y;";
    String expected = "/*\n Identical license here */\n";

    Compiler compiler = new Compiler();
    CompilerOptions options = createNewFlagBasedOptions();
    List<SourceFile> inputs =
        ImmutableList.of(
            SourceFile.fromCode("testcode1", js1), SourceFile.fromCode("testcode2", js2));
    Result result = compiler.compile(EMPTY_EXTERNS, inputs, options);

    assertWithMessage(Joiner.on(",").join(result.errors)).that(result.success).isTrue();
    assertThat(compiler.toSource()).isEqualTo(expected);
  }

  @Test
  public void testDefineNoOverriding() {
    Map<String, Node> emptyMap = new HashMap<>();
    List<String> defines = new ArrayList<>();
    assertDefineOverrides(emptyMap, defines);
  }

  @Test
  public void testDefineOverriding1() {
    List<String> defines =
        ImmutableList.of(
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

  @Test
  public void testDefineOverriding2() {
    List<String> defines = ImmutableList.of("DEF_STRING='='");
    Map<String, Node> expected = ImmutableMap.of(
        "DEF_STRING", Node.newString("="));
    assertDefineOverrides(expected, defines);
  }

  @Test
  public void testDefineOverriding3() {
    List<String> defines = ImmutableList.of("a.DEBUG");
    Map<String, Node> expected = ImmutableMap.of(
        "a.DEBUG", new Node(Token.TRUE));
    assertDefineOverrides(expected, defines);
  }

  @Test
  public void testBadDefineOverriding1() {
    List<String> defines = ImmutableList.of("DEF_STRING=");
    assertCreateDefinesThrowsException(defines);
  }

  @Test
  public void testBadDefineOverriding2() {
    List<String> defines = ImmutableList.of("=true");
    assertCreateDefinesThrowsException(defines);
  }

  @Test
  public void testBadDefineOverriding3() {
    List<String> defines = ImmutableList.of("DEF_STRING='''");
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

    assertWithMessage(defines + " didn't fail").fail();
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
      assertWithMessage(entry.getKey()).that(actual.containsKey(entry.getKey())).isTrue();

      Node actualNode = actual.get(entry.getKey());
      assertWithMessage(entry.toString())
          .that(entry.getValue().isEquivalentTo(actualNode))
          .isTrue();
    }
  }

  static Result test(String js, String expected, DiagnosticType error) {
    Compiler compiler = new Compiler();
    CompilerOptions options = createNewFlagBasedOptions();
    List<SourceFile> inputs = ImmutableList.of(
        SourceFile.fromCode("testcode", js));
    Result result = compiler.compile(EMPTY_EXTERNS, inputs, options);

    if (error == null) {
      assertWithMessage(Joiner.on(",").join(result.errors)).that(result.success).isTrue();
      String outputSource = compiler.toSource();
      assertThat(outputSource).isEqualTo(expected);
    } else {
      assertThat(result.errors).hasLength(1);
      assertThat(result.errors[0].getType()).isEqualTo(error);
    }
    return result;
  }

  @Test
  public void testConsecutiveSemicolons() {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setEmitUseStrict(false);
    compiler.initOptions(options);
    String js = "if(a);";
    Node n = compiler.parseTestCode(js);
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
    compiler.toSource(cb, 0, n);
    assertThat(cb.toString()).isEqualTo(js);
  }

  @Test
  public void testWarningsFiltering() {
    // Warnings and errors are left alone when no filtering is used
    assertThat(hasOutput(null, "foo/bar.js", CheckLevel.WARNING)).isTrue();
    assertThat(hasOutput(null, "foo/bar.js", CheckLevel.ERROR)).isTrue();

    // Warnings (but not errors) get filtered out
    assertThat(hasOutput("baz", "foo/bar.js", CheckLevel.WARNING)).isFalse();
    assertThat(hasOutput("foo", "foo/bar.js", CheckLevel.WARNING)).isTrue();
    assertThat(hasOutput("baz", "foo/bar.js", CheckLevel.ERROR)).isTrue();
    assertThat(hasOutput("foo", "foo/bar.js", CheckLevel.ERROR)).isTrue();
  }

  @Test
  public void testExportSymbolReservesNamesForRenameVars() {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setEmitUseStrict(false);
    options.setClosurePass(true);
    options.setVariableRenaming(VariableRenamingPolicy.ALL);

    String js = "var goog, x; goog.exportSymbol('a', x);";
    List<SourceFile> inputs = ImmutableList.of(
        SourceFile.fromCode("testcode", js));
    Result result = compiler.compile(EMPTY_EXTERNS, inputs, options);

    assertThat(result.success).isTrue();
    assertThat(compiler.toSource()).isEqualTo("var b;var c;b.exportSymbol(\"a\",c);");
  }

  @Test
  public void testGenerateExportsReservesNames() {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setEmitUseStrict(false);
    options.setClosurePass(true);
    options.setVariableRenaming(VariableRenamingPolicy.ALL);
    options.setGenerateExports(true);

    String js = "var goog; /** @export */ var a={};";
    List<SourceFile> inputs = ImmutableList.of(
        SourceFile.fromCode("testcode", js));
    Result result = compiler.compile(EMPTY_EXTERNS, inputs, options);

    assertThat(result.success).isTrue();
    assertThat(compiler.toSource()).isEqualTo("var b;var c={};b.exportSymbol(\"a\",c);");
  }

  private static final DiagnosticType TEST_ERROR =
      DiagnosticType.error("TEST_ERROR", "Test error");

  /**
   * Simple error manager that tracks whether anything was reported/output.
   */
  private static class TestErrorManager implements ErrorManager {
    private boolean output = false;
    private int warningCount = 0;
    private int errorCount = 0;

    @Override
    public void report(CheckLevel level, JSError error) {
      output = true;
      if (level == CheckLevel.WARNING) {
        warningCount++;
      }
      if (level == CheckLevel.ERROR) {
        errorCount++;
      }
    }

    // Methods we don't care about
    @Override public void generateReport() {}

    @Override
    public int getErrorCount() {
      return errorCount;
    }

    @Override
    public int getWarningCount() {
      return warningCount;
    }

    @Override
    public JSError[] getErrors() {
      return null;
    }

    @Override
    public JSError[] getWarnings() {
      return null;
    }

    @Override public void setTypedPercent(double typedPercent) {}

    @Override
    public double getTypedPercent() {
      return 0.0;
    }
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

  @Test
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

    assertThat(before[0]).isTrue(); // should run these custom passes
    assertThat(after[0]).isFalse(); // but not these
  }

  @Test
  public void testAdditionalReplacementsForClosure() {
    CompilerOptions options = createNewFlagBasedOptions();
    options.setLocale("it_IT");
    options.setClosurePass(true);

    Map<String, Node> replacements = DefaultPassConfig.getAdditionalReplacements(options);

    assertThat(replacements).hasSize(2);
    assertThat(replacements.get("goog.LOCALE").getString()).isEqualTo("it_IT");
  }

  @Test
  public void testInputSerialization() throws Exception {
    Compiler compiler = new Compiler();
    compiler.initCompilerOptionsIfTesting();
    CompilerInput input = new CompilerInput(SourceFile.fromCode(
          "tmp", "function foo() {}"));
    Node ast = input.getAstRoot(compiler);
    CompilerInput newInput = (CompilerInput) deserialize(compiler, serialize(input));
    assertThat(ast.isEquivalentTo(newInput.getAstRoot(compiler))).isTrue();
  }

  @Test
  public void testExternsDependencySorting() {
    List<SourceFile> inputs =
        ImmutableList.of(
            SourceFile.fromCode("leaf", "/** @fileoverview @typeSummary */ goog.require('beer');"),
            SourceFile.fromCode(
                "beer",
                "/** @fileoverview @typeSummary */ goog.provide('beer');\ngoog.require('hops');"),
            SourceFile.fromCode("hops", "/** @fileoverview @typeSummary */ goog.provide('hops');"));

    CompilerOptions options = createNewFlagBasedOptions();
    options.dependencyOptions.setDependencySorting(true);

    List<SourceFile> externs = ImmutableList.of();
    Compiler compiler = new Compiler();
    compiler.compile(externs, inputs, options);

    assertThat(compiler.externsRoot.getChildCount()).isEqualTo(3);
    assertExternIndex(compiler, 0, "hops");
    assertExternIndex(compiler, 1, "beer");
    assertExternIndex(compiler, 2, "leaf");
  }

  @Test
  public void testCheckSaveRestoreOptimize() throws Exception {
    Compiler compiler = new Compiler(new TestErrorManager());

    CompilerOptions options = new CompilerOptions();
    options.setAssumeForwardDeclaredForMissingTypes(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setCheckTypes(true);
    options.setStrictModeInput(true);
    options.setEmitUseStrict(true);
    options.setPreserveDetailedSourceInfo(true);
    options.setCheckTypes(true);

    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    compiler.init(
        Collections.singletonList(
            SourceFile.fromCode("externs.js",
                Joiner.on('\n').join(
                    "",
                    "var console = {};",
                    " console.log = function() {};"))),
        Collections.singletonList(
            SourceFile.fromCode("input.js",
                Joiner.on('\n').join(
                    "",
                    "function f() { return 2; }",
                    "console.log(f());"))),
        options);

    compiler.parse();
    compiler.check();

    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    compiler.saveState(byteArrayOutputStream);
    byteArrayOutputStream.close();

    compiler = new Compiler(new TestErrorManager());
    compiler.options = options;
    try (ByteArrayInputStream byteArrayInputStream =
        new ByteArrayInputStream(byteArrayOutputStream.toByteArray())) {
      compiler.restoreState(byteArrayInputStream);
    }

    compiler.performOptimizations();
    String source = compiler.toSource();
    assertThat(source).isEqualTo("'use strict';console.log(2);");
  }

  @Test
  public void testStrictnessWithNonStrictOutputLanguage() {
    Compiler compiler = new Compiler(new TestErrorManager());

    CompilerOptions options = new CompilerOptions();
    options.setAssumeForwardDeclaredForMissingTypes(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);

    compiler.init(
        ImmutableList.of(),
        Collections.singletonList(SourceFile.fromCode("input.js", "console.log(0);")),
        options);

    compiler.parse();
    String source = compiler.toSource();
    assertThat(source).isEqualTo("console.log(0);");
  }

  @Test
  public void testStrictnessWithStrictOutputLanguage() {
    Compiler compiler = new Compiler(new TestErrorManager());

    CompilerOptions options = new CompilerOptions();
    options.setAssumeForwardDeclaredForMissingTypes(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5_STRICT);

    compiler.init(
        ImmutableList.of(),
        Collections.singletonList(SourceFile.fromCode("input.js", "console.log(0);")),
        options);

    compiler.parse();
    String source = compiler.toSource();
    assertThat(source).isEqualTo("'use strict';console.log(0);");
  }

  @Test
  public void testStrictnessWithNonStrictInputLanguage() {
    Compiler compiler = new Compiler(new TestErrorManager());

    CompilerOptions options = new CompilerOptions();
    options.setAssumeForwardDeclaredForMissingTypes(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT5);

    compiler.init(
        ImmutableList.of(),
        Collections.singletonList(SourceFile.fromCode("input.js", "console.log(0);")),
        options);

    compiler.parse();
    String source = compiler.toSource();
    assertThat(source).isEqualTo("console.log(0);");
  }

  @Test
  public void testStrictnessWithStrictInputLanguage() {
    Compiler compiler = new Compiler(new TestErrorManager());

    CompilerOptions options = new CompilerOptions();
    options.setAssumeForwardDeclaredForMissingTypes(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT5_STRICT);

    compiler.init(
        ImmutableList.of(),
        Collections.singletonList(SourceFile.fromCode("input.js", "console.log(0);")),
        options);

    compiler.parse();

    String source = compiler.toSource();
    assertThat(source).isEqualTo("'use strict';console.log(0);");
  }

  @Test
  public void testStrictnessWithNonStrictInputLanguageAndNoTranspileOutput() {
    Compiler compiler = new Compiler(new TestErrorManager());

    CompilerOptions options = new CompilerOptions();
    options.setAssumeForwardDeclaredForMissingTypes(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT5);
    options.setLanguageOut(LanguageMode.NO_TRANSPILE);

    compiler.init(
        ImmutableList.of(),
        Collections.singletonList(SourceFile.fromCode("input.js", "console.log(0);")),
        options);

    compiler.parse();
    compiler.check();
    compiler.performOptimizations();

    String source = compiler.toSource();
    assertThat(source).isEqualTo("console.log(0);");
  }

  @Test
  public void testStrictnessWithStrictInputLanguageAndNoTranspileOutput() {
    Compiler compiler = new Compiler(new TestErrorManager());

    CompilerOptions options = new CompilerOptions();
    options.setAssumeForwardDeclaredForMissingTypes(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT5_STRICT);
    options.setLanguageOut(LanguageMode.NO_TRANSPILE);

    compiler.init(
        ImmutableList.of(),
        Collections.singletonList(SourceFile.fromCode("input.js", "console.log(0);")),
        options);

    compiler.parse();
    compiler.check();
    compiler.performOptimizations();

    String source = compiler.toSource();
    assertThat(source).isEqualTo("'use strict';console.log(0);");
  }

  @Test
  public void testExternsDependencyPruning() {
    List<SourceFile> inputs =
        ImmutableList.of(
            SourceFile.fromCode(
                "unused", "/** @fileoverview @typeSummary */ goog.provide('unused');"),
            SourceFile.fromCode(
                "moocher", "/** @fileoverview @typeSummary */ goog.require('something');"),
            SourceFile.fromCode(
                "something", "/** @fileoverview @typeSummary */ goog.provide('something');"));

    CompilerOptions options = createNewFlagBasedOptions();
    options.dependencyOptions.setDependencyPruning(true);

    List<SourceFile> externs = ImmutableList.of();
    Compiler compiler = new Compiler();
    compiler.compile(externs, inputs, options);

    assertThat(compiler.externsRoot.getChildCount()).isEqualTo(2);
    assertExternIndex(compiler, 0, "something");
    assertExternIndex(compiler, 1, "moocher");
  }

  private void assertExternIndex(Compiler compiler, int index, String name) {
    assertThat(compiler.externsRoot.getChildAtIndex(index))
        .isSameAs(compiler.getInput(new InputId(name)).getAstRoot(compiler));
  }

  @Test
  public void testEs6ModuleEntryPoint() throws Exception {
    List<SourceFile> inputs = ImmutableList.of(
        SourceFile.fromCode("/index.js", "import foo from './foo.js'; foo('hello');"),
        SourceFile.fromCode("/foo.js", "export default (foo) => { alert(foo); }"));

    List<ModuleIdentifier> entryPoints = ImmutableList.of(
        ModuleIdentifier.forFile("/index"));

    CompilerOptions options = createNewFlagBasedOptions();
    options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT5);
    options.dependencyOptions.setDependencyPruning(true);
    options.dependencyOptions.setDependencySorting(true);
    options.dependencyOptions.setEntryPoints(entryPoints);

    List<SourceFile> externs =
        AbstractCommandLineRunner.getBuiltinExterns(options.getEnvironment());

    Compiler compiler = new Compiler();
    compiler.compile(externs, inputs, options);

    Result result = compiler.getResult();
    assertThat(result.warnings).isEmpty();
    assertThat(result.errors).isEmpty();
  }

  // https://github.com/google/closure-compiler/issues/2692
  @Test
  public void testGoogNamespaceEntryPoint() throws Exception {
    List<SourceFile> inputs =
        ImmutableList.of(
            SourceFile.fromCode(
                "/index.js",
                "goog.provide('foobar'); const foo = require('./foo.js').default; foo('hello');"),
            SourceFile.fromCode("/foo.js", "export default (foo) => { alert(foo); }"));

    List<ModuleIdentifier> entryPoints =
        ImmutableList.of(ModuleIdentifier.forClosure("goog:foobar"));

    CompilerOptions options = createNewFlagBasedOptions();
    options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT5);
    options.dependencyOptions.setDependencyPruning(true);
    options.dependencyOptions.setDependencySorting(true);
    options.dependencyOptions.setEntryPoints(entryPoints);
    options.processCommonJSModules = true;

    List<SourceFile> externs =
        AbstractCommandLineRunner.getBuiltinExterns(options.getEnvironment());

    Compiler compiler = new Compiler();
    compiler.compile(externs, inputs, options);

    Result result = compiler.getResult();
    assertThat(result.warnings).isEmpty();
    assertThat(result.errors).isEmpty();
  }

  @Test
  public void testEs6ModulePathWithOddCharacters() throws Exception {
    // Note that this is not yet compatible with transpilation, since the generated goog.provide
    // statements are not valid identifiers.
    List<SourceFile> inputs =
        ImmutableList.of(
            SourceFile.fromCode("/index[0].js", "import foo from './foo.js'; foo('hello');"),
            SourceFile.fromCode("/foo.js", "export default (foo) => { alert(foo); }"));

    List<ModuleIdentifier> entryPoints = ImmutableList.of(ModuleIdentifier.forFile("/index[0].js"));

    CompilerOptions options = createNewFlagBasedOptions();
    options.setLanguage(CompilerOptions.LanguageMode.ECMASCRIPT_2017);
    options.dependencyOptions.setDependencyPruning(true);
    options.dependencyOptions.setDependencySorting(true);
    options.dependencyOptions.setEntryPoints(entryPoints);

    List<SourceFile> externs =
        AbstractCommandLineRunner.getBuiltinExterns(options.getEnvironment());

    Compiler compiler = new Compiler();
    compiler.compile(externs, inputs, options);

    Result result = compiler.getResult();
    assertThat(result.errors).isEmpty();
  }

  @Test
  public void testGetEmptyResult() {
    Result result = new Compiler().getResult();
    assertThat(result.errors).isEmpty();
  }

  @Test
  public void testAnnotation() {
    Compiler compiler = new Compiler();

    assertThat(compiler.getAnnotation(J2clSourceFileChecker.HAS_J2CL_ANNOTATION_KEY)).isNull();

    compiler.setAnnotation(J2clSourceFileChecker.HAS_J2CL_ANNOTATION_KEY, true);
    assertThat(compiler.getAnnotation(J2clSourceFileChecker.HAS_J2CL_ANNOTATION_KEY))
        .isEqualTo(Boolean.TRUE);
  }

  @Test
  public void testSetAnnotationTwice() {
    Compiler compiler = new Compiler();

    compiler.setAnnotation(J2clSourceFileChecker.HAS_J2CL_ANNOTATION_KEY, true);
    try {
      compiler.setAnnotation(J2clSourceFileChecker.HAS_J2CL_ANNOTATION_KEY, false);
      assertWithMessage("It didn't fail for overwriting existing annotation.").fail();
    } catch (IllegalArgumentException expected) {
      return;
    }
  }

  @Test
  public void testReportChangeNoScopeFails() {
    Compiler compiler = new Compiler();

    Node detachedNode = IR.var(IR.name("foo"));

    try {
      compiler.reportChangeToEnclosingScope(detachedNode);
      assertWithMessage("Reporting a change on a node with no scope should have failed.").fail();
    } catch (IllegalStateException e) {
      return;
    }
  }

  @Test
  public void testReportChangeWithScopeSucceeds() {
    Compiler compiler = new Compiler();

    Node attachedNode = IR.var(IR.name("foo"));
    IR.function(IR.name("bar"), IR.paramList(), IR.block(attachedNode));

    // Succeeds without throwing an exception.
    compiler.reportChangeToEnclosingScope(attachedNode);
  }

  /**
   * See TimelineTest.java for the many timeline behavior tests that don't make sense to duplicate
   * here.
   */
  @Test
  public void testGetChangesAndDeletions_baseline() {
    Compiler compiler = new Compiler();

    // In the initial state nothing has been marked changed or deleted.
    assertThat(compiler.getChangedScopeNodesForPass("FunctionInliner")).isNull();
    assertThat(compiler.getDeletedScopeNodesForPass("FunctionInliner")).isNull();
  }

  @Test
  public void testGetChangesAndDeletions_changeReportsVisible() {
    Compiler compiler = new Compiler();
    Node function1 = IR.function(IR.name("foo"), IR.paramList(), IR.block());
    Node function2 = IR.function(IR.name("foo"), IR.paramList(), IR.block());
    IR.root(IR.script(function1, function2));

    // Mark original baseline.
    compiler.getChangedScopeNodesForPass("FunctionInliner");
    compiler.getDeletedScopeNodesForPass("FunctionInliner");

    // Mark both functions changed.
    compiler.reportChangeToChangeScope(function1);
    compiler.reportChangeToChangeScope(function2);

    // Both function1 and function2 are seen as changed and nothing is seen as deleted.
    assertThat(compiler.getChangedScopeNodesForPass("FunctionInliner"))
        .containsExactly(function1, function2);
    assertThat(compiler.getDeletedScopeNodesForPass("FunctionInliner")).isEmpty();
  }

  @Test
  public void testGetChangesAndDeletions_deleteOverridesChange() {
    Compiler compiler = new Compiler();
    Node function1 = IR.function(IR.name("foo"), IR.paramList(), IR.block());
    Node function2 = IR.function(IR.name("foo"), IR.paramList(), IR.block());
    IR.root(IR.script(function1, function2));

    // Mark original baseline.
    compiler.getChangedScopeNodesForPass("FunctionInliner");
    compiler.getDeletedScopeNodesForPass("FunctionInliner");

    // Mark both functions changed, then delete function2 and mark it deleted.
    compiler.reportChangeToChangeScope(function1);
    compiler.reportChangeToChangeScope(function2);
    function2.detach();
    compiler.reportFunctionDeleted(function2);

    // Now function1 will be seen as changed and function2 will be seen as deleted, since delete
    // overrides change.
    assertThat(compiler.getChangedScopeNodesForPass("FunctionInliner")).containsExactly(function1);
    assertThat(compiler.getDeletedScopeNodesForPass("FunctionInliner")).containsExactly(function2);
  }

  @Test
  public void testGetChangesAndDeletions_changeDoesntOverrideDelete() {
    Compiler compiler = new Compiler();
    Node function1 = IR.function(IR.name("foo"), IR.paramList(), IR.block());
    Node function2 = IR.function(IR.name("foo"), IR.paramList(), IR.block());
    IR.root(IR.script(function1, function2));

    // Mark original baseline.
    compiler.getChangedScopeNodesForPass("FunctionInliner");
    compiler.getDeletedScopeNodesForPass("FunctionInliner");

    // Mark function1 changed and function2 deleted, then try to mark function2 changed.
    compiler.reportChangeToChangeScope(function1);
    function2.detach();
    compiler.reportFunctionDeleted(function2);
    compiler.reportChangeToChangeScope(function2);

    // Now function1 will be seen as changed and function2 will be seen as deleted, since change
    // does not override delete.
    assertThat(compiler.getChangedScopeNodesForPass("FunctionInliner")).containsExactly(function1);
    assertThat(compiler.getDeletedScopeNodesForPass("FunctionInliner")).containsExactly(function2);
  }

  @Test
  public void testGetChangesAndDeletions_onlySeesChangesSinceLastRequest() {
    Compiler compiler = new Compiler();
    Node function1 = IR.function(IR.name("foo"), IR.paramList(), IR.block());
    Node function2 = IR.function(IR.name("foo"), IR.paramList(), IR.block());
    IR.root(IR.script(function1, function2));

    // Mark original baseline.
    compiler.getChangedScopeNodesForPass("FunctionInliner");
    compiler.getDeletedScopeNodesForPass("FunctionInliner");

    // Mark function1 changed and function2 deleted.
    compiler.reportChangeToChangeScope(function1);
    function2.detach();
    compiler.reportFunctionDeleted(function2);

    // Verify their respective states are seen.
    assertThat(compiler.getChangedScopeNodesForPass("FunctionInliner")).containsExactly(function1);
    assertThat(compiler.getDeletedScopeNodesForPass("FunctionInliner")).containsExactly(function2);

    // Check states again. Should find nothing since nothing has changed since the last
    // 'FunctionInliner' request.
    assertThat(compiler.getChangedScopeNodesForPass("FunctionInliner")).isEmpty();
    assertThat(compiler.getDeletedScopeNodesForPass("FunctionInliner")).isEmpty();
  }

  @Test
  public void testAddIndexProvider_ThenGetIndex() {
    Compiler compiler = new Compiler();

    compiler.addIndexProvider(new IndexProvider<String>() {
      @Override
      public String get() {
        // Normally some shared index would be constructed/updated/returned here.
        return "String";
      }

      @Override
      public Class<String> getType() {
        return String.class;
      }});
    compiler.addIndexProvider(new IndexProvider<Double>() {
      @Override
      public Double get() {
        // Normally some shared index would be constructed/updated/returned here.
        return Double.MAX_VALUE;
      }

      @Override
      public Class<Double> getType() {
        return Double.class;
      }});

    // Have registered providers.
    assertThat(compiler.getIndex(String.class)).isEqualTo("String");
    assertThat(compiler.getIndex(Double.class)).isEqualTo(Double.MAX_VALUE);

    // Has no registered provider.
    assertThat(compiler.getIndex(Object.class)).isNull();
  }

  @Test
  public void testAddIndexProviderTwice_isException() {
    Compiler compiler = new Compiler();

    IndexProvider<String> stringIndexProvider =
        new IndexProvider<String>() {
          @Override
          public String get() {
            // Normally some shared index would be constructed/updated/returned here.
            return "String";
          }

          @Override
          public Class<String> getType() {
            return String.class;
          }
        };
    compiler.addIndexProvider(stringIndexProvider);

    try {
      compiler.addIndexProvider(stringIndexProvider);
      assertWithMessage("expected duplicate index addition to fail").fail();
    } catch (IllegalStateException e) {
      // expected
    }
  }

  private static CompilerOptions createNewFlagBasedOptions() {
    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    options.setEmitUseStrict(false);
    return options;
  }

  private static byte[] serialize(Object obj) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(baos)) {
      out.writeObject(obj);
    }
    return baos.toByteArray();
  }

  private static Object deserialize(final Compiler compiler, byte[] bytes)
      throws IOException, ClassNotFoundException {

    class CompilerObjectInputStream extends ObjectInputStream implements HasCompiler {
      public CompilerObjectInputStream(InputStream in) throws IOException {
        super(in);
      }

      @Override
      public AbstractCompiler getCompiler() {
        return compiler;
      }
    }

    ObjectInputStream in = new CompilerObjectInputStream(new ByteArrayInputStream(bytes));
    Object obj = in.readObject();
    in.close();
    return obj;
  }

  @Test
  public void testProperEs6ModuleOrdering() throws Exception {
    List<SourceFile> sources = new ArrayList<>();
    sources.add(
        SourceFile.fromCode(
            "/entry.js",
            lines(
                "import './b/b.js';",
                "import './b/a.js';",
                "import './important.js';",
                "import './a/b.js';",
                "import './a/a.js';")));
    sources.add(SourceFile.fromCode("/a/a.js", "window['D'] = true;"));
    sources.add(SourceFile.fromCode("/a/b.js", "window['C'] = true;"));
    sources.add(SourceFile.fromCode("/b/a.js", "window['B'] = true;"));
    sources.add(
        SourceFile.fromCode(
            "/b/b.js",
            lines(
                "import foo from './c.js';",
                "if (foo.settings.inUse) {",
                "  window['E'] = true;",
                "}",
                "window['A'] = true;")));
    sources.add(
        SourceFile.fromCode(
            "/b/c.js",
            lines(
                "window['BEFOREA'] = true;",
                "",
                "export default {",
                "  settings: {",
                "    inUse: Boolean(document.documentElement['attachShadow'])",
                "  }",
                "};")));
    sources.add(SourceFile.fromCode("/important.js", "window['E'] = false;"));

    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.dependencyOptions.setEntryPoints(
        ImmutableList.of(ModuleIdentifier.forFile("/entry.js")));
    options.dependencyOptions.setDependencySorting(true);
    options.dependencyOptions.setDependencyPruning(true);
    options.dependencyOptions.setMoocherDropping(true);
    List<SourceFile> externs =
        AbstractCommandLineRunner.getBuiltinExterns(options.getEnvironment());
    Compiler compiler = new Compiler();
    Result result = compiler.compile(externs, ImmutableList.copyOf(sources), options);
    assertThat(result.success).isTrue();

    List<String> orderedInputs = new ArrayList<>();
    for (CompilerInput input : compiler.getInputsInOrder()) {
      orderedInputs.add(input.getName());
    }

    assertThat(orderedInputs)
        .containsExactly(
            "/b/c.js", "/b/b.js", "/b/a.js", "/important.js", "/a/b.js", "/a/a.js", "/entry.js")
        .inOrder();
  }

  @Test
  public void testProperEs6ModuleOrderingWithExport() throws Exception {
    ImmutableList.Builder<SourceFile> sources = ImmutableList.builder();
    sources.add(
        SourceFile.fromCode(
            "/entry.js",
            lines(
                "import {A, B, C1} from './a.js';",
                "console.log(A)",
                "console.log(B)",
                "console.log(C1)")));
    sources.add(
        SourceFile.fromCode(
            "/a.js",
            lines(
                "export {B} from './b.js';",
                "export {C as C1} from './c.js';",
                "export const A = 'a';")));
    sources.add(SourceFile.fromCode("/b.js", "export const B = 'b';"));
    sources.add(SourceFile.fromCode("/c.js", "export const C = 'c';"));

    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.dependencyOptions.setEntryPoints(
        ImmutableList.of(ModuleIdentifier.forFile("/entry.js")));
    options.dependencyOptions.setDependencySorting(true);
    options.dependencyOptions.setDependencyPruning(true);
    options.dependencyOptions.setMoocherDropping(true);
    List<SourceFile> externs =
        AbstractCommandLineRunner.getBuiltinExterns(options.getEnvironment());
    Compiler compiler = new Compiler();
    Result result = compiler.compile(externs, sources.build(), options);
    assertThat(result.success).isTrue();

    List<String> orderedInputs = new ArrayList<>();
    for (CompilerInput input : compiler.getInputsInOrder()) {
      orderedInputs.add(input.getName());
    }

    assertThat(orderedInputs)
        .containsExactly(
            "/b.js", "/c.js", "/a.js", "/entry.js")
        .inOrder();
  }

  @Test
  public void testProperGoogBaseOrdering() throws Exception {
    List<SourceFile> sources = new ArrayList<>();
    sources.add(SourceFile.fromCode("test.js", "goog.setTestOnly()"));
    sources.add(SourceFile.fromCode("d.js", "goog.provide('d');"));
    sources.add(SourceFile.fromCode("c.js", "goog.provide('c');"));
    sources.add(SourceFile.fromCode("b.js", "goog.provide('b');"));
    sources.add(SourceFile.fromCode("a.js", "goog.provide('a');"));
    sources.add(
        SourceFile.fromCode(
            "base.js",
            lines(
                "/** @provideGoog */",
                "/** @const */ var goog = goog || {};",
                "var COMPILED = false;")));
    sources.add(
        SourceFile.fromCode(
            "entry.js",
            lines(
                "goog.require('a');",
                "goog.require('b');",
                "goog.require('c');",
                "goog.require('d');")));

    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.dependencyOptions.setEntryPoints(
        ImmutableList.of(ModuleIdentifier.forFile("entry.js")));
    options.dependencyOptions.setDependencySorting(true);
    options.dependencyOptions.setDependencyPruning(true);
    options.dependencyOptions.setMoocherDropping(false);
    List<SourceFile> externs =
        AbstractCommandLineRunner.getBuiltinExterns(options.getEnvironment());

    for (int iterationCount = 0; iterationCount < 10; iterationCount++) {
      java.util.Collections.shuffle(sources);
      Compiler compiler = new Compiler();
      Result result = compiler.compile(externs, ImmutableList.copyOf(sources), options);
      assertThat(result.success).isTrue();

      List<String> orderedInputs = new ArrayList<>();
      for (CompilerInput input : compiler.getInputsInOrder()) {
        orderedInputs.add(input.getName());
      }

      assertThat(orderedInputs)
          .containsExactly("base.js", "test.js", "a.js", "b.js", "c.js", "d.js", "entry.js");
      assertThat(orderedInputs.indexOf("base.js")).isLessThan(orderedInputs.indexOf("entry.js"));
      assertThat(orderedInputs.indexOf("base.js")).isLessThan(orderedInputs.indexOf("test.js"));
    }
  }

  @Test
  public void testDynamicImportOrdering() throws Exception {
    List<SourceFile> sources = new ArrayList<>();
    sources.add(SourceFile.fromCode("/entry.js", "__webpack_require__(2);"));
    sources.add(
        SourceFile.fromCode(
            "/a.js",
            lines(
                "console.log(module.id);",
                "__webpack_require__.e(0).then(function() { return __webpack_require__(3); });")));
    sources.add(SourceFile.fromCode("/b.js", "console.log(module.id); __webpack_require__(4);"));
    sources.add(SourceFile.fromCode("/c.js", "console.log(module.id);"));

    HashMap<String, String> webpackModulesById = new HashMap<>();
    webpackModulesById.put("1", "/entry.js");
    webpackModulesById.put("2", "/a.js");
    webpackModulesById.put("3", "/b.js");
    webpackModulesById.put("4", "/c.js");

    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.dependencyOptions.setEntryPoints(
        ImmutableList.of(ModuleIdentifier.forFile("/entry.js")));
    options.dependencyOptions.setDependencySorting(true);
    options.dependencyOptions.setDependencyPruning(true);
    options.dependencyOptions.setMoocherDropping(true);
    options.setProcessCommonJSModules(true);
    options.setModuleResolutionMode(ResolutionMode.WEBPACK);
    List<SourceFile> externs =
        AbstractCommandLineRunner.getBuiltinExterns(options.getEnvironment());
    Compiler compiler = new Compiler();
    compiler.initWebpackMap(ImmutableMap.copyOf(webpackModulesById));
    Result result = compiler.compile(externs, ImmutableList.copyOf(sources), options);
    assertThat(result.success).isTrue();

    List<String> orderedInputs = new ArrayList<>();
    for (CompilerInput input : compiler.getInputsInOrder()) {
      orderedInputs.add(input.getName());
    }

    assertThat(orderedInputs).containsExactly("/a.js", "/entry.js", "/c.js", "/b.js").inOrder();
  }

  @Test
  public void testDynamicImportOrdering2() throws Exception {
    List<SourceFile> sources = new ArrayList<>();
    sources.add(SourceFile.fromCode("/entry.js", "__webpack_require__(2);"));
    sources.add(
        SourceFile.fromCode(
            "/a.js",
            lines(
                "console.log(module.id);",
                "__webpack_require__.e(0).then(function() {",
                "  const foo = __webpack_require__(3);",
                "  console.log(foo);",
                "});")));
    sources.add(SourceFile.fromCode("/b.js", "console.log(module.id); module.exports = 'foo';"));

    HashMap<String, String> webpackModulesById = new HashMap<>();
    webpackModulesById.put("1", "/entry.js");
    webpackModulesById.put("2", "/a.js");
    webpackModulesById.put("3", "/b.js");

    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.dependencyOptions.setEntryPoints(
        ImmutableList.of(ModuleIdentifier.forFile("/entry.js")));
    options.dependencyOptions.setDependencySorting(true);
    options.dependencyOptions.setDependencyPruning(true);
    options.dependencyOptions.setMoocherDropping(true);
    options.setProcessCommonJSModules(true);
    options.setModuleResolutionMode(ResolutionMode.WEBPACK);
    List<SourceFile> externs =
        AbstractCommandLineRunner.getBuiltinExterns(options.getEnvironment());
    Compiler compiler = new Compiler();
    compiler.initWebpackMap(ImmutableMap.copyOf(webpackModulesById));
    Result result = compiler.compile(externs, ImmutableList.copyOf(sources), options);
    assertThat(result.success).isTrue();

    List<String> orderedInputs = new ArrayList<>();
    for (CompilerInput input : compiler.getInputsInOrder()) {
      orderedInputs.add(input.getName());
    }

    assertThat(orderedInputs).containsExactly("/a.js", "/entry.js", "/b.js").inOrder();
  }

  @Test
  public void testWeakSources() throws Exception {
    List<SourceFile> sources =
        ImmutableList.of(
            SourceFile.fromCode("weak1.js", "goog.provide('a');", SourceKind.WEAK),
            SourceFile.fromCode("strong1.js", "goog.provide('a.b');", SourceKind.STRONG),
            SourceFile.fromCode("weak2.js", "goog.provide('c');", SourceKind.WEAK),
            SourceFile.fromCode("strong2.js", "goog.provide('d');", SourceKind.STRONG));

    CompilerOptions options = new CompilerOptions();
    options.setEmitUseStrict(false);
    options.setClosurePass(true);

    Compiler compiler = new Compiler();

    compiler.init(ImmutableList.of(), sources, options);

    compiler.parse();
    compiler.check();
    compiler.performOptimizations();

    assertThat(compiler.getModuleGraph().getModuleCount()).isEqualTo(2);
    assertThat(Iterables.get(compiler.getModuleGraph().getAllModules(), 0).getName())
        .isEqualTo(JSModule.STRONG_MODULE_NAME);
    assertThat(Iterables.get(compiler.getModuleGraph().getAllModules(), 1).getName())
        .isEqualTo(JSModule.WEAK_MODULE_NAME);

    assertThat(compiler.toSource()).isEqualTo("var a={};a.b={};var d={};");
  }

  private void weakSourcesModulesHelper(boolean saveAndRestore) throws Exception {
    JSModule m1 = new JSModule("m1");
    m1.add(SourceFile.fromCode("weak1.js", "goog.provide('a');", SourceKind.WEAK));
    m1.add(SourceFile.fromCode("strong1.js", "goog.provide('a.b');", SourceKind.STRONG));
    JSModule m2 = new JSModule("m2");
    m2.add(SourceFile.fromCode("weak2.js", "goog.provide('c');", SourceKind.WEAK));
    m2.add(SourceFile.fromCode("strong2.js", "goog.provide('d');", SourceKind.STRONG));

    CompilerOptions options = new CompilerOptions();
    options.setEmitUseStrict(false);
    options.setClosurePass(true);

    Compiler compiler = new Compiler();

    compiler.initModules(ImmutableList.of(), ImmutableList.of(m1, m2), options);

    compiler.parse();
    compiler.check();

    if (saveAndRestore) {
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      compiler.saveState(byteArrayOutputStream);
      byteArrayOutputStream.close();
      try (ByteArrayInputStream byteArrayInputStream =
          new ByteArrayInputStream(byteArrayOutputStream.toByteArray())) {
        compiler.restoreState(byteArrayInputStream);
      }
    }

    compiler.performOptimizations();

    assertThat(compiler.getModuleGraph().getModuleCount()).isEqualTo(3);

    JSModule weakModule = compiler.getModuleGraph().getModuleByName("$weak$");
    assertThat(weakModule).isNotNull();

    assertThat(compiler.toSource(m1)).isEqualTo("var a={};a.b={};");
    assertThat(compiler.toSource(m2)).isEqualTo("var d={};");
    assertThat(compiler.toSource(weakModule)).isEmpty();
  }

  @Test
  public void testWeakSourcesModules() throws Exception {
    weakSourcesModulesHelper(/* saveAndRestore= */ false);
  }

  @Test
  public void testWeakSourcesSaveRestore() throws Exception {
    weakSourcesModulesHelper(/* saveAndRestore= */ true);
  }
}
