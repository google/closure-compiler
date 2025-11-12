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

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.TypeValidator.TYPE_MISMATCH_WARNING;
import static com.google.javascript.jscomp.testing.JSCompCorrespondences.DIAGNOSTIC_EQUALITY;
import static com.google.javascript.jscomp.testing.JSErrorSubject.assertError;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;
import static org.junit.Assert.assertThrows;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.debugging.sourcemap.FilePosition;
import com.google.debugging.sourcemap.SourceMapConsumerV3;
import com.google.debugging.sourcemap.SourceMapGeneratorV3;
import com.google.debugging.sourcemap.proto.Mapping.OriginalMapping;
import com.google.debugging.sourcemap.proto.Mapping.OriginalMapping.Precision;
import com.google.javascript.jscomp.Compiler.ScriptNodeLicensesOnlyTracker;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.CompilerOptions.SegmentOfCompilationToRun;
import com.google.javascript.jscomp.base.Tri;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.jscomp.serialization.AstNode;
import com.google.javascript.jscomp.serialization.LazyAst;
import com.google.javascript.jscomp.serialization.NodeKind;
import com.google.javascript.jscomp.serialization.SourceFilePool;
import com.google.javascript.jscomp.serialization.StringPool;
import com.google.javascript.jscomp.serialization.TypedAst;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile;
import com.google.javascript.rhino.StaticSourceFile.SourceKind;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

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
    ImmutableList<SourceFile> inputs =
        ImmutableList.of(
            SourceFile.fromCode("gin", "goog.provide('gin'); goog.require('tonic'); var gin = {};"),
            SourceFile.fromCode(
                "tonic", "goog.provide('tonic'); goog.require('gin'); var tonic = {};"),
            SourceFile.fromCode("mix", "goog.require('gin'); goog.require('tonic');"));
    CompilerOptions options = new CompilerOptions();
    options.setChecksOnly(true);
    options.setContinueAfterErrors(true);
    options.setDependencyOptions(DependencyOptions.pruneLegacyForEntryPoints(ImmutableList.of()));
    Compiler compiler = new Compiler();
    compiler.init(ImmutableList.<SourceFile>of(), inputs, options);
    compiler.parseInputs();
    assertThat(compiler.getJsRoot().getParent()).isEqualTo(compiler.getRoot());
    assertThat(compiler.getExternsRoot().getParent()).isEqualTo(compiler.getRoot());
    assertThat(compiler.getRoot()).isNotNull();

    Node jsRoot = compiler.getJsRoot();
    assertThat(jsRoot.getChildCount()).isEqualTo(3);
  }

  @Test
  public void testPrintExterns() {
    ImmutableList<SourceFile> externs =
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
  public void testClosureUnawareCodeMarks() {
    ImmutableList<SourceFile> thirdPartyCode =
        ImmutableList.of(
            SourceFile.fromCode(
                "closure_unaware_code.js",
                "/** @fileoverview @closureUnaware */ function alert(x) {}"));
    CompilerOptions options = new CompilerOptions();
    options.setPreserveTypeAnnotations(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    Compiler compiler = new Compiler();
    compiler.init(ImmutableList.<SourceFile>of(), thirdPartyCode, options);
    compiler.parseInputs();
    assertThat(compiler.toSource()).isEqualTo("/** @closureUnaware */ function alert(x){};");
    assertThat(compiler.getJsRoot().getChildCount()).isEqualTo(1);
    StaticSourceFile staticSourceFile = compiler.getJsRoot().getFirstChild().getStaticSourceFile();
    assertThat(staticSourceFile.getName()).isEqualTo("closure_unaware_code.js");
    assertThat(staticSourceFile.isClosureUnawareCode()).isTrue();
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
    SourceFile input =
        SourceFile.fromCode("input.js", "(function (undefined) { alert(undefined); })();");
    compiler.compile(externs, input, options);
  }

  private static String normalize(String path) {
    return path.replace(File.separator, "/");
  }

  @Test
  public void testInputSourceMaps() throws Exception {
    FilePosition originalSourcePosition = new FilePosition(17, 25);
    ImmutableMap<String, SourceMapInput> inputSourceMaps =
        ImmutableMap.of(
            normalize("generated_js/example.js"),
            sourcemap(
                normalize("generated_js/example.srcmap"),
                normalize("../original/source.html"),
                originalSourcePosition));
    String origSourceName = normalize("original/source.html");
    ImmutableList<SourceFile> originalSources =
        ImmutableList.of(SourceFile.fromCode(origSourceName, "<div ng-show='foo()'>"));

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
                .setPrecision(Precision.APPROXIMATE_LINE)
                .build());
    assertThat(compiler.getSourceLine(origSourceName, 1)).isEqualTo("<div ng-show='foo()'>");
  }

  private SourceMapInput sourcemap(
      String sourceMapPath, String originalSource, FilePosition originalSourcePosition)
      throws Exception {
    SourceMapGeneratorV3 sourceMap = new SourceMapGeneratorV3();
    sourceMap.addMapping(
        originalSource,
        "testSymbolName",
        originalSourcePosition,
        new FilePosition(1, 1),
        new FilePosition(100, 1));
    StringBuilder output = new StringBuilder();
    sourceMap.appendTo(output, "unused.js");

    return new SourceMapInput(SourceFile.fromCode(sourceMapPath, output.toString()));
  }

  private static final String SOURCE_MAP_TEST_CODE =
      """
      var X = (function () {
          function X(input) {
              this.y = input;
          }
          return X;
      }());
      console.log(new X(1));
      """;

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
    assertThat(sourceMap.getOriginalNames()).isEmpty();
  }

  private static final String SOURCE_MAP_TEST_CONTENT =
      """
      var A = (function () {
          function A(input) {
              this.a = input;
          }
          return A;
      }());
      console.log(new A(1));\
      """;

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
    assertThat(sourceMap.getOriginalNames()).isEmpty();
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
      assertThat(sourceMap.getOriginalSourcesContent()).isNull();
      assertThat(sourceMap.getOriginalNames()).isEmpty();
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
      assertThat(sourceMap.getOriginalSourcesContent()).isNull();
      assertThat(sourceMap.getOriginalNames()).isEmpty();
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
    ImmutableMap<String, SourceMapInput> inputSourceMaps =
        ImmutableMap.of("input.js", sourcemap("input.js.map", "input.ts", originalSourcePosition));

    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.setSourceMapOutputPath("fake/source_map_path.js.map");
    options.inputSourceMaps = inputSourceMaps;
    options.applyInputSourceMaps = true;
    Compiler compiler = new Compiler();
    compiler.compile(
        EMPTY_EXTERNS.get(0),
        SourceFile.fromCode("input.js", "// Unmapped line\nvar x = 1;\nalert(x);"),
        options);
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
    assertThat(consumer.getOriginalSources()).containsExactly("input.ts");
    assertThat(consumer.getOriginalSourcesContent()).isNull();
    assertThat(consumer.getOriginalNames()).containsExactly("testSymbolName");
  }

  @Test
  public void testKeepInputSourceMapsSourcesContent() throws Exception {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.setSourceMapOutputPath("fake/source_map_path.js.map");
    options.applyInputSourceMaps = true;
    options.sourceMapIncludeSourcesContent = true;
    String code =
        SOURCE_MAP_TEST_CODE + "\n//# sourceMappingURL=" + BASE64_ENCODED_SOURCE_MAP_WITH_CONTENT;
    Compiler compiler = new Compiler();
    SourceFile sourceFile = SourceFile.fromCode("temp/path/input.js", code);
    compiler.compile(EMPTY_EXTERNS.get(0), sourceFile, options);
    assertThat(compiler.toSource())
        .isEqualTo(
            "var X=function(){function X(input){this.y=input}return X}();console.log(new X(1));");
    SourceMap sourceMap = compiler.getSourceMap();
    StringWriter out = new StringWriter();
    sourceMap.appendTo(out, "source.js.map");
    SourceMapConsumerV3 consumer = new SourceMapConsumerV3();
    consumer.parse(out.toString());
    assertThat(consumer.getOriginalSources()).containsExactly("temp/test/foo.ts");
    assertThat(consumer.getOriginalSourcesContent()).containsExactly(SOURCE_MAP_TEST_CONTENT);
    assertThat(consumer.getOriginalNames()).containsExactly("X", "input", "y", "console", "log");
  }

  @Test
  public void testNoSourceMapIsGeneratedWithoutPath() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT3);
    options.applyInputSourceMaps = true;
    options.sourceMapIncludeSourcesContent = true;
    String code =
        SOURCE_MAP_TEST_CODE + "\n//# sourceMappingURL=" + BASE64_ENCODED_SOURCE_MAP_WITH_CONTENT;
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
    ImmutableList<SourceFile> inputs =
        ImmutableList.of(SourceFile.fromCode("i1", ""), SourceFile.fromCode("i2", fileOverview));

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
    compiler.compile(
        SourceFile.fromCode("extern.js", ""), SourceFile.fromCode("test.js", badJsDoc), options);
    assertThat(compiler.getWarnings()).hasSize(1);
    assertThat(compiler.getErrors()).isEmpty();
  }

  /**
   * Make sure that non-standard JSDoc annotation is not a hard error nor warning when it is off.
   */
  @Test
  public void testBug2176967Off() {
    final String badJsDoc = "/** @XYZ */\n var x";
    Compiler compiler = new Compiler();

    CompilerOptions options = createNewFlagBasedOptions();

    options.setWarningLevel(DiagnosticGroups.NON_STANDARD_JSDOC, CheckLevel.OFF);
    compiler.compile(
        SourceFile.fromCode("extern.js", ""), SourceFile.fromCode("test.js", badJsDoc), options);
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(compiler.getErrors()).isEmpty();
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

    options.setWarningLevel(DiagnosticGroups.NON_STANDARD_JSDOC, CheckLevel.ERROR);
    compiler.compile(
        SourceFile.fromCode("extern.js", ""), SourceFile.fromCode("test.js", badJsDoc), options);
    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(compiler.getErrors()).hasSize(1);
  }

  @Test
  public void testArtificialFunctionValidation_defaultsToOnIfEnablesDiambiguateProperties() {
    Compiler compiler = new Compiler();
    CompilerOptions options = createNewFlagBasedOptions();
    options.setDisambiguateProperties(true);
    compiler.initOptions(options);

    assertThat(
            options
                .getWarningsGuard()
                .mustRunChecks(DiagnosticGroups.ARTIFICIAL_FUNCTION_PURITY_VALIDATION))
        .isEqualTo(Tri.TRUE);
  }

  @Test
  public void testArtificialFunctionValidation_canOverrideDefault() {
    Compiler compiler = new Compiler();
    CompilerOptions options = createNewFlagBasedOptions();
    options.setDisambiguateProperties(true);
    options.setWarningLevel(DiagnosticGroups.ARTIFICIAL_FUNCTION_PURITY_VALIDATION, CheckLevel.OFF);
    compiler.initOptions(options);

    assertThat(
            options
                .getWarningsGuard()
                .mustRunChecks(DiagnosticGroups.ARTIFICIAL_FUNCTION_PURITY_VALIDATION))
        .isEqualTo(Tri.FALSE);
  }

  @Test
  public void testArtificialFunctionValidation_defaultBehaviorWhenDisambiguateDisabled() {
    Compiler compiler = new Compiler();
    CompilerOptions options = createNewFlagBasedOptions();
    options.setDisambiguateProperties(false);
    compiler.initOptions(options);

    assertThat(
            options
                .getWarningsGuard()
                .mustRunChecks(DiagnosticGroups.ARTIFICIAL_FUNCTION_PURITY_VALIDATION))
        .isEqualTo(Tri.UNKNOWN);
  }

  @Test
  public void testNormalInputs() {
    CompilerOptions options = new CompilerOptions();
    Compiler compiler = new Compiler();
    ImmutableList<SourceFile> inputs =
        ImmutableList.of(SourceFile.fromCode("in1", ""), SourceFile.fromCode("in2", ""));
    compiler.compile(EMPTY_EXTERNS, inputs, options);

    assertThat(compiler.getInput(new InputId("externs")).isExtern()).isTrue();
    assertThat(compiler.getInput(new InputId("in1")).isExtern()).isFalse();
    assertThat(compiler.getInput(new InputId("in2")).isExtern()).isFalse();
  }

  @Test
  public void testRebuildInputsFromModule() {
    ImmutableList<JSChunk> chunks = ImmutableList.of(new JSChunk("m1"), new JSChunk("m2"));
    chunks.get(0).add(SourceFile.fromCode("in1", ""));
    chunks.get(1).add(SourceFile.fromCode("in2", ""));

    Compiler compiler = new Compiler();
    compiler.initChunks(ImmutableList.<SourceFile>of(), chunks, new CompilerOptions());

    chunks.get(1).add(SourceFile.fromCode("in3", ""));
    assertThat(compiler.getInput(new InputId("in3"))).isNull();
    compiler.rebuildInputsFromModules();
    assertThat(compiler.getInput(new InputId("in3"))).isNotNull();
  }

  @Test
  public void testMalformedFunctionInExterns() {
    // Just verify that no exceptions are thrown (see bug 910619).
    new Compiler()
        .compile(
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
    ImmutableList<SourceFile> input =
        ImmutableList.of(
            SourceFile.fromCode("foo", "/** @fileoverview */ var x; /** @fileoverview */ var y;"));
    assertThat(new Compiler().compile(EMPTY_EXTERNS, input, new CompilerOptions()).success)
        .isTrue();
  }

  // Make sure we correctly output license text.
  @Test
  public void testImportantCommentOutput() {
    test(
        "/*! Your favorite license goes here */ console.log(0);",
        "/*\n Your favorite license goes here */\nconsole.log(0);",
        null);
  }

  // Make sure we output license text even if followed by @fileoverview.
  @Test
  public void testImportantCommentAndOverviewDirectiveWarning() {
    ImmutableList<SourceFile> input =
        ImmutableList.of(
            SourceFile.fromCode(
                "foo",
                """
                /*! Your favorite license goes here */
                /**
                  * @fileoverview This is my favorite file! */
                var x;
                """));
    assertThat(new Compiler().compile(EMPTY_EXTERNS, input, new CompilerOptions()).success)
        .isTrue();
  }

  // Text for the opposite order - @fileoverview, then @license.
  @Test
  public void testOverviewAndImportantCommentOutput() {
    test(
        """
        /** @fileoverview This is my favorite file! */
        /*! Your favorite license goes here */
        console.log(0);
        """,
        "/*\n Your favorite license goes here */\nconsole.log(0);",
        null);
  }

  // Test for sequence of @license and @fileoverview, and make sure
  // all the licenses get copied over.
  @Test
  public void testImportantCommentOverviewImportantComment() {
    test(
        """
        /*! Another license */
        /** @fileoverview This is my favorite file! */
        /*! Your favorite license goes here */
        console.log(0);
        """,
        "/*\n Another license  Your favorite license goes here */\nconsole.log(0);",
        null);
  }

  // Make sure things work even with @license and @fileoverview in the
  // same comment.
  @Test
  public void testCombinedImportantCommentOverviewDirectiveOutput() {
    test(
        """
        /*! Your favorite license goes here
         * @fileoverview This is my favorite file! */
        console.log(0);
        """,
        """
        /*
         Your favorite license goes here
         @fileoverview This is my favorite file! */
        console.log(0);
        """,
        null);
  }

  // Does the presence of @author change anything with the license?
  @Test
  public void testCombinedImportantCommentAuthorDirectiveOutput() {
    test(
        """
        /*! Your favorite license goes here
         * @author Robert */
        console.log(0);
        """,
        "/*\n Your favorite license goes here\n @author Robert */\nconsole.log(0);",
        null);
  }

  // Make sure we concatenate licenses the same way.
  @Test
  public void testMultipleImportantCommentDirectiveOutput() {
    test(
        """
        /*! Your favorite license goes here */
        /*! Another license */
        console.log(0);
        """,
        "/*\n Your favorite license goes here  Another license */\nconsole.log(0);",
        null);
  }

  @Test
  public void testImportantCommentLicenseDirectiveOutput() {
    test(
        """
        /*! Your favorite license goes here */
        /** @license Another license */
        console.log(0);
        """,
        "/*\n Another license  Your favorite license goes here */\nconsole.log(0);",
        null);
  }

  @Test
  public void testLicenseImportantCommentDirectiveOutput() {
    test(
        """
        /** @license Your favorite license goes here */
        /*! Another license */
        console.log(0);
        """,
        "/*\n Your favorite license goes here  Another license */\nconsole.log(0);",
        null);
  }

  // Do we correctly handle the license if it's not at the top level, but
  // inside another declaration?
  @Test
  public void testImportantCommentInTree() {
    test(
        """
        var a = function() {
         +
        /*! Your favorite license goes here */
         1;};
        console.log(0);
        """,
        "/*\n Your favorite license goes here */\nconsole.log(0);",
        null);
  }

  @Test
  public void testMultipleUniqueImportantComments() {
    String js1 =
        """
        /*! One license here */
        console.log(0);
        """;
    String js2 =
        """
        /*! Another license here */
        console.log(1);
        """;
    String expected =
        """
        /*
         One license here */
        console.log(0);\
        /*
         Another license here */
        console.log(1);\
        """;

    Compiler compiler = new Compiler();
    CompilerOptions options = createNewFlagBasedOptions();
    ImmutableList<SourceFile> inputs =
        ImmutableList.of(
            SourceFile.fromCode("testcode1", js1), SourceFile.fromCode("testcode2", js2));
    Result result =
        compiler.compile(
            ImmutableList.of(
                SourceFile.fromCode("externs", new TestExternsBuilder().addConsole().build())),
            inputs,
            options);

    assertWithMessage(Joiner.on(",").join(result.errors)).that(result.success).isTrue();
    assertThat(compiler.toSource()).isEqualTo(expected);
  }

  @Test
  public void testMultipleIdenticalImportantComments() {
    String js1 =
        """
        /*! Identical license here */
        console.log(0);
        """;
    String js2 =
        """
        /*! Identical license here */
        console.log(1);
        """;
    String expected = "/*\n Identical license here */\nconsole.log(0);console.log(1);";

    Compiler compiler = new Compiler();
    CompilerOptions options = createNewFlagBasedOptions();
    ImmutableList<SourceFile> inputs =
        ImmutableList.of(
            SourceFile.fromCode("testcode1", js1), SourceFile.fromCode("testcode2", js2));
    Result result =
        compiler.compile(
            ImmutableList.of(
                SourceFile.fromCode("externs", new TestExternsBuilder().addConsole().build())),
            inputs,
            options);

    assertWithMessage(Joiner.on(",").join(result.errors)).that(result.success).isTrue();
    assertThat(compiler.toSource()).isEqualTo(expected);
  }

  // Make sure we correctly output license text.
  @Test
  public void testLicenseDirectiveOutput() {
    test(
        "/** @license Your favorite license goes here */ console.log(0);",
        "/*\n Your favorite license goes here */\nconsole.log(0);",
        null);
  }

  // Make sure we output license text even if followed by @fileoverview.
  @Test
  public void testLicenseAndOverviewDirectiveWarning() {
    ImmutableList<SourceFile> input =
        ImmutableList.of(
            SourceFile.fromCode(
                "foo",
                """
                /** @license Your favorite license goes here */
                /**\s
                  * @fileoverview This is my favorite file! */
                var x;
                """));
    assertThat(
            new Compiler()
                .compile(
                    ImmutableList.of(
                        SourceFile.fromCode(
                            "externs", new TestExternsBuilder().addConsole().build())),
                    input,
                    new CompilerOptions())
                .success)
        .isTrue();
  }

  // Text for the opposite order - @fileoverview, then @license.
  @Test
  public void testOverviewAndLicenseDirectiveOutput() {
    test(
        """
        /** @fileoverview This is my favorite file! */
        /** @license Your favorite license goes here */
        console.log(0);
        """,
        "/*\n Your favorite license goes here */\nconsole.log(0);",
        null);
  }

  // Test for sequence of @license and @fileoverview, and make sure
  // all the licenses get copied over.
  @Test
  public void testLicenseOverviewLicense() {
    test(
        """
        /** @license Another license */
        /** @fileoverview This is my favorite file! */
        /** @license Your favorite license goes here */
        console.log(0);
        """,
        "/*\n Your favorite license goes here  Another license */\nconsole.log(0);",
        null);
  }

  // Make sure things work even with @license and @fileoverview in the
  // same comment.
  @Test
  public void testCombinedLicenseOverviewDirectiveOutput() {
    test(
        """
        /** @license Your favorite license goes here
         * @fileoverview This is my favorite file! */
        console.log(0);
        """,
        """
        /*
         Your favorite license goes here
         @fileoverview This is my favorite file! */
        console.log(0);
        """,
        null);
  }

  // Does the presence of @author change anything with the license?
  @Test
  public void testCombinedLicenseAuthorDirectiveOutput() {
    test(
        """
        /** @license Your favorite license goes here
         * @author Robert */
        console.log(0);
        """,
        "/*\n Your favorite license goes here\n @author Robert */\nconsole.log(0);",
        null);
  }

  // Make sure we concatenate licenses the same way.
  @Test
  public void testMultipleLicenseDirectiveOutput() {
    test(
        """
        /** @license Your favorite license goes here */
        /** @license Another license */
        console.log(0);
        """,
        "/*\n Another license  Your favorite license goes here */\nconsole.log(0);",
        null);
  }

  // Same thing, two @licenses in the same comment.
  @Test
  public void testTwoLicenseInSameComment() {
    test(
        """
        /** @license Your favorite license goes here
          * @license Another license */
        console.log(0);
        """,
        """
        /*
         Your favorite license goes here
         @license Another license */
        console.log(0);
        """,
        null);
  }

  // Do we correctly handle the license if it's not at the top level, but
  // inside another declaration?
  @Test
  public void testLicenseInTree() {
    test(
        """
        var a = function() {
        /** @license Your favorite license goes here */
         console.log(0);};a();
        """,
        "/*\n Your favorite license goes here */\nconsole.log(0);",
        null);
  }

  @Test
  public void testMultipleUniqueLicenses() {
    String js1 =
        """
        /** @license One license here */
        console.log(0);
        """;
    String js2 =
        """
        /** @license Another license here */
        console.log(1);
        """;
    String expected =
        """
        /*
         One license here */
        console.log(0);/*
         Another license here */
        console.log(1);\
        """;

    Compiler compiler = new Compiler();
    CompilerOptions options = createNewFlagBasedOptions();
    ImmutableList<SourceFile> inputs =
        ImmutableList.of(
            SourceFile.fromCode("testcode1", js1), SourceFile.fromCode("testcode2", js2));
    Result result =
        compiler.compile(
            ImmutableList.of(
                SourceFile.fromCode("externs", new TestExternsBuilder().addConsole().build())),
            inputs,
            options);

    assertWithMessage(Joiner.on(",").join(result.errors)).that(result.success).isTrue();
    assertThat(compiler.toSource()).isEqualTo(expected);
  }

  @Test
  public void testMultipleIdenticalLicenses() {
    String js1 =
        """
        /** @license Identical license here */
        console.log(0);
        """;
    String js2 =
        """
        /** @license Identical license here */
        console.log(1);
        """;
    String js3 =
        """
        /** @license Identical license here */
        console.log(2);
        /** @license Identical license here */
        """;
    String expected =
        "/*\n Identical license here */\nconsole.log(0);console.log(1);console.log(2);";

    Compiler compiler = new Compiler();
    CompilerOptions options = createNewFlagBasedOptions();
    ImmutableList<SourceFile> inputs =
        ImmutableList.of(
            SourceFile.fromCode("testcode1", js1),
            SourceFile.fromCode("testcode2", js2),
            SourceFile.fromCode("bundled", js3));
    Result result =
        compiler.compile(
            ImmutableList.of(
                SourceFile.fromCode("externs", new TestExternsBuilder().addConsole().build())),
            inputs,
            options);

    assertWithMessage(Joiner.on(",").join(result.errors)).that(result.success).isTrue();
    assertThat(compiler.toSource()).isEqualTo(expected);
  }

  @Test
  public void testIdenticalLicenseAndImportantComment() {
    String js1 =
        """
        /** @license Identical license here */
        console.log(0);
        """;
    String js2 =
        """
        /*! Identical license here */
        console.log(1);
        """;
    String expected = "/*\n Identical license here */\nconsole.log(0);console.log(1);";

    Compiler compiler = new Compiler();
    CompilerOptions options = createNewFlagBasedOptions();
    ImmutableList<SourceFile> inputs =
        ImmutableList.of(
            SourceFile.fromCode("testcode1", js1), SourceFile.fromCode("testcode2", js2));
    Result result =
        compiler.compile(
            ImmutableList.of(
                SourceFile.fromCode("externs", new TestExternsBuilder().addConsole().build())),
            inputs,
            options);

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
    ImmutableList<String> defines =
        ImmutableList.of(
            "COMPILED", "DEF_TRUE=true", "DEF_FALSE=false", "DEF_NUMBER=5.5", "DEF_STRING='bye'");
    ImmutableMap<String, Node> expected =
        ImmutableMap.of(
            "COMPILED", new Node(Token.TRUE),
            "DEF_TRUE", new Node(Token.TRUE),
            "DEF_FALSE", new Node(Token.FALSE),
            "DEF_NUMBER", Node.newNumber(5.5),
            "DEF_STRING", Node.newString("bye"));
    assertDefineOverrides(expected, defines);
  }

  @Test
  public void testDefineOverriding2() {
    ImmutableList<String> defines = ImmutableList.of("DEF_STRING='='");
    ImmutableMap<String, Node> expected = ImmutableMap.of("DEF_STRING", Node.newString("="));
    assertDefineOverrides(expected, defines);
  }

  @Test
  public void testDefineOverriding3() {
    ImmutableList<String> defines = ImmutableList.of("a.DEBUG");
    ImmutableMap<String, Node> expected = ImmutableMap.of("a.DEBUG", new Node(Token.TRUE));
    assertDefineOverrides(expected, defines);
  }

  @Test
  public void testBadDefineOverriding1() {
    ImmutableList<String> defines = ImmutableList.of("DEF_STRING=");
    CompilerOptions options = new CompilerOptions();

    assertThrows(
        RuntimeException.class,
        () -> AbstractCommandLineRunner.createDefineReplacements(defines, options));
  }

  @Test
  public void testBadDefineOverriding2() {
    ImmutableList<String> defines = ImmutableList.of("=true");
    CompilerOptions options = new CompilerOptions();

    assertThrows(
        RuntimeException.class,
        () -> AbstractCommandLineRunner.createDefineReplacements(defines, options));
  }

  @Test
  public void testBadDefineOverriding3() {
    ImmutableList<String> defines = ImmutableList.of("DEF_STRING='''");
    CompilerOptions options = new CompilerOptions();

    assertThrows(
        RuntimeException.class,
        () -> AbstractCommandLineRunner.createDefineReplacements(defines, options));
  }

  static void assertDefineOverrides(Map<String, Node> expected, List<String> defines) {
    CompilerOptions options = new CompilerOptions();
    AbstractCommandLineRunner.createDefineReplacements(defines, options);
    ImmutableMap<String, Node> actual = options.getDefineReplacements();

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

  static Result test(String js, String expected, @Nullable DiagnosticType error) {
    Compiler compiler = new Compiler();
    CompilerOptions options = createNewFlagBasedOptions();
    ImmutableList<SourceFile> inputs =
        ImmutableList.of(
            SourceFile.fromCode("testcode", js),
            SourceFile.fromCode("stdexterns", new TestExternsBuilder().addConsole().build()));
    Result result = compiler.compile(EMPTY_EXTERNS, inputs, options);

    if (error == null) {
      assertWithMessage(Joiner.on(",").join(result.errors)).that(result.success).isTrue();
      String outputSource = compiler.toSource();
      assertThat(outputSource).isEqualTo(expected.trim());
    } else {
      assertThat(result.errors).hasSize(1);
      assertThat(result.errors.get(0).type()).isEqualTo(error);
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
    ScriptNodeLicensesOnlyTracker lt = new ScriptNodeLicensesOnlyTracker(compiler);
    compiler.toSource(cb, lt, 0, n);
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
    ImmutableList<SourceFile> inputs = ImmutableList.of(SourceFile.fromCode("testcode", js));
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
    ImmutableList<SourceFile> inputs = ImmutableList.of(SourceFile.fromCode("testcode", js));
    Result result = compiler.compile(EMPTY_EXTERNS, inputs, options);

    assertThat(result.success).isTrue();
    assertThat(compiler.toSource()).isEqualTo("var b;var c={};b.exportSymbol(\"a\",c);");
  }

  private static final DiagnosticType TEST_ERROR = DiagnosticType.error("TEST_ERROR", "Test error");

  /** Simple error manager that tracks whether anything was reported/output. */
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
    @Override
    public void generateReport() {}

    @Override
    public int getErrorCount() {
      return errorCount;
    }

    @Override
    public int getWarningCount() {
      return warningCount;
    }

    @Override
    public ImmutableList<JSError> getErrors() {
      return ImmutableList.of();
    }

    @Override
    public ImmutableList<JSError> getWarnings() {
      return ImmutableList.of();
    }

    @Override
    public void setTypedPercent(double typedPercent) {}

    @Override
    public double getTypedPercent() {
      return 0.0;
    }
  }

  private boolean hasOutput(@Nullable String showWarningsOnlyFor, String path, CheckLevel level) {
    TestErrorManager errorManager = new TestErrorManager();
    Compiler compiler = new Compiler(errorManager);
    CompilerOptions options = createNewFlagBasedOptions();
    if (showWarningsOnlyFor != null) {
      options.addWarningsGuard(new ShowByPathWarningsGuard(showWarningsOnlyFor));
    }
    compiler.init(ImmutableList.<SourceFile>of(), ImmutableList.<SourceFile>of(), options);

    compiler.report(
        JSError.builder(TEST_ERROR).setSourceLocation(path, 1, 1).setLevel(level).build());

    return errorManager.output;
  }

  @Test
  public void testChecksOnlyModeSkipsOptimizations() {
    Compiler compiler = new Compiler();
    CompilerOptions options = createNewFlagBasedOptions();
    options.setChecksOnly(true);

    final boolean[] before = new boolean[1];
    final boolean[] after = new boolean[1];

    options.addCustomPass(
        CustomPassExecutionTime.BEFORE_OPTIMIZATIONS,
        new CompilerPass() {
          @Override
          public void process(Node externs, Node root) {
            before[0] = true;
          }
        });

    options.addCustomPass(
        CustomPassExecutionTime.BEFORE_OPTIMIZATION_LOOP,
        new CompilerPass() {
          @Override
          public void process(Node externs, Node root) {
            after[0] = true;
          }
        });

    String js = "var x = 1;";
    ImmutableList<SourceFile> inputs = ImmutableList.of(SourceFile.fromCode("testcode", js));
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
  public void testExternsDependencySorting() {
    ImmutableList<SourceFile> inputs =
        ImmutableList.of(
            SourceFile.fromCode("leaf", "/** @fileoverview @typeSummary */ goog.require('beer');"),
            SourceFile.fromCode(
                "beer",
                "/** @fileoverview @typeSummary */ goog.provide('beer');\ngoog.require('hops');"),
            SourceFile.fromCode("hops", "/** @fileoverview @typeSummary */ goog.provide('hops');"));

    CompilerOptions options = createNewFlagBasedOptions();
    options.setDependencyOptions(DependencyOptions.sortOnly());

    ImmutableList<SourceFile> externs = ImmutableList.of();
    Compiler compiler = new Compiler();
    compiler.compile(externs, inputs, options);

    assertThat(compiler.getExternsRoot().getChildCount()).isEqualTo(4);
    assertExternIndex(compiler, 0, " [synthetic:externs] "); // added by VarCheck
    assertExternIndex(compiler, 1, "hops");
    assertExternIndex(compiler, 2, "beer");
    assertExternIndex(compiler, 3, "leaf");
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
    options.setEmitUseStrict(false);
    options.setPreserveDetailedSourceInfo(true);

    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    List<SourceFile> externs =
        Collections.singletonList(
            SourceFile.fromCode(
                "externs.js",
                """
                var console = {};
                 console.log = function() {};
                """));
    List<SourceFile> code =
        Collections.singletonList(
            SourceFile.fromCode(
                "input.js",
                """
                function f() { return 2; }
                console.log(f());
                """));
    compiler.init(externs, code, options);

    compiler.parse();
    compiler.check();

    final byte[] stateAfterChecks = getSavedCompilerState(compiler);

    compiler = new Compiler(new TestErrorManager());
    compiler.init(externs, code, options);
    restoreCompilerState(compiler, stateAfterChecks);

    compiler.performTranspilationAndOptimizations(SegmentOfCompilationToRun.OPTIMIZATIONS);
    String source = compiler.toSource();
    assertThat(source).isEqualTo("console.log(2);");
  }

  @Test
  public void testCheckSaveRestore3Stages() throws Exception {
    Compiler compiler = new Compiler(new TestErrorManager());

    CompilerOptions options = new CompilerOptions();
    options.setAssumeForwardDeclaredForMissingTypes(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_NEXT);
    options.setCheckTypes(true);
    options.setStrictModeInput(true);
    options.setEmitUseStrict(false);
    options.setPreserveDetailedSourceInfo(true);
    // Late localization happens in stage 3, so this forces stage 3 to actually have some
    // effect on the AST.
    options.setDoLateLocalization(true);
    // Supply a message bundle to trigger the replaceStrings pass.
    // We don't need to actually translate anything, though.
    options.setMessageBundle(new EmptyMessageBundle());
    // Enable the ReplaceStrings pass, so we can confirm that the stringMap it creates survives
    // serialization and deserialization.
    options.setReplaceStringsFunctionDescriptions(ImmutableList.of("Error(*)"));

    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    List<SourceFile> externs =
        Collections.singletonList(
            SourceFile.fromCode(
                "externs.js",
                """
                var console = {};
                 console.log = function() {};
                """));
    List<SourceFile> srcs =
        Collections.singletonList(
            SourceFile.fromCode(
                "input.js",
                """
                /** @desc greeting */
                const MSG_HELLO = goog.getMsg('hello');
                function f() { return MSG_HELLO; }
                // Use `Error()` in order to make sure we generate a non-empty
                // compiler.stringMap, so we can confirm it is saved and restored.
                console.log(Error('string to replace'), f());
                """));
    compiler.init(externs, srcs, options);

    compiler.parse();
    compiler.check();

    final byte[] stateAfterChecks = getSavedCompilerState(compiler);

    compiler = new Compiler(new TestErrorManager());
    compiler.init(externs, srcs, options);
    restoreCompilerState(compiler, stateAfterChecks);

    compiler.performTranspilationAndOptimizations(SegmentOfCompilationToRun.OPTIMIZATIONS);
    String source = compiler.toSource();
    assertThat(source)
        .isEqualTo(
            concatStrings(
                "console.log(",
                "Error(\"a\"),", // replaceStrings obfuscated this
                "__jscomp_define_msg__(",
                "{\"key\":\"MSG_HELLO\",\"msg_text\":\"hello\"}",
                "));"));

    final byte[] stateAfterOptimizations = getSavedCompilerState(compiler);

    compiler = new Compiler(new TestErrorManager());
    compiler.init(externs, srcs, options);
    restoreCompilerState(compiler, stateAfterOptimizations);

    compiler.performFinalizations();

    final Result result = compiler.getResult();
    // confirm that the string map was built with a mapping from an obfuscated string to
    // the string used in the Error() call above.
    assertThat(result.stringMap.toMap()).containsExactly("a", "string to replace");

    source = compiler.toSource();
    assertThat(source).isEqualTo("console.log(Error(\"a\"),\"hello\");");
  }

  private static final String RESULT_SOURCE_MAP_WITH_CONTENT =
"""
{
"version":3,
"file":"output.js",
"lineCount":1,
"mappings":"AAQAA,OAAQC,CAAAA,GAAR,CAAY,IALVC,QAAA,EAAyB,EAKf,CAAM,CAAN,CAAZ;",
"sources":["../test/foo.ts"],
"sourcesContent":["var A = (function () {\\n    function A(input) {\\n        this.a = input;\\n    }\\n    return A;\\n}());\\nconsole.log(new A(1));"],
"names":["console","log","X"]
}
""";

  @Test
  public void testCheckSaveRestore3StagesSourceMaps() throws Exception {
    Compiler compiler = new Compiler(new TestErrorManager());

    CompilerOptions options = new CompilerOptions();
    options.setAssumeForwardDeclaredForMissingTypes(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_NEXT);
    options.setCheckTypes(true);
    options.setStrictModeInput(true);
    options.setEmitUseStrict(false);
    options.setPreserveDetailedSourceInfo(true);
    // 3-stage builds require late localization
    options.setDoLateLocalization(true);
    // For stages 1 and 2 we generally expect no source map output path to be set,
    // since it won't actually be generated until compilation is completed in
    // stage 3.
    options.setSourceMapOutputPath(null);
    // The code that is running the compiler is expected to set this option
    // when executing a partial compilation and it expects to request source
    // maps when running the final stage later.
    options.setAlwaysGatherSourceMapInfo(true);
    options.applyInputSourceMaps = true;
    options.setSourceMapIncludeSourcesContent(true);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    List<SourceFile> externs =
        Collections.singletonList(
            SourceFile.fromCode(
                "externs.js",
                """
                var console = {};
                 console.log = function() {};
                """));
    List<SourceFile> srcs =
        Collections.singletonList(
            SourceFile.fromCode(
                "input.js",
                SOURCE_MAP_TEST_CODE
                    + "\n//# sourceMappingURL="
                    + BASE64_ENCODED_SOURCE_MAP_WITH_CONTENT));

    // Stage 1
    compiler.init(externs, srcs, options);
    compiler.parse();
    compiler.check();
    final byte[] stateAfterChecks = getSavedCompilerState(compiler);

    // Stage 2
    compiler = new Compiler(new TestErrorManager());
    compiler.init(externs, srcs, options);
    restoreCompilerState(compiler, stateAfterChecks);
    compiler.performTranspilationAndOptimizations(SegmentOfCompilationToRun.OPTIMIZATIONS);

    String source = compiler.toSource();
    assertThat(source).isEqualTo("console.log(new function(){}(1));");

    final byte[] stateAfterOptimizations = getSavedCompilerState(compiler);

    // Stage 3
    compiler = new Compiler(new TestErrorManager());
    // In general the options passed to the compiler should be the same for all
    // 3 stages. The source map output path is an exception.
    // It only makes sense to specify it for the final stage when the output
    // file will actually be generated.
    // The name passed here doesn't matter, because the compiler itself only stores it and enables
    // tracking of source map information when it is non-null.
    // In real usage AbstractCommandLineRunner is responsible for actually writing the file whose
    // path is stored in this field.
    options.setSourceMapOutputPath("dummy");
    // The code that is running the compiler is expected to set this option
    // to false when executing the final stage, so no time and space will
    // be wasted on generating source maps if the source map output path is null.
    options.setAlwaysGatherSourceMapInfo(false);
    compiler.init(externs, srcs, options);
    restoreCompilerState(compiler, stateAfterOptimizations);
    compiler.performFinalizations();
    final Result result = compiler.getResult();

    source = compiler.toSource();
    assertThat(source).isEqualTo("console.log(new function(){}(1));");

    final SourceMap sourceMap = result.sourceMap;
    assertThat(sourceMap).isNotNull();

    // Check sourcemap output
    final StringBuilder sourceMapStringBuilder = new StringBuilder();
    final String outputJSFile = "output.js";
    sourceMap.appendTo(sourceMapStringBuilder, outputJSFile);
    final String sourceMapString = sourceMapStringBuilder.toString();
    assertThat(sourceMapString).isEqualTo(RESULT_SOURCE_MAP_WITH_CONTENT);
  }

  @Test
  public void testSingleStageCompileSourceMaps() throws Exception {
    Compiler compiler = new Compiler(new TestErrorManager());

    CompilerOptions options = new CompilerOptions();
    options.setAssumeForwardDeclaredForMissingTypes(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_NEXT);
    options.setCheckTypes(true);
    options.setStrictModeInput(true);
    options.setEmitUseStrict(false);
    options.setPreserveDetailedSourceInfo(true);
    // The name passed here doesn't matter, because the compiler itself only stores it and enables
    // tracking of source map information when it is non-null.
    // In real usage AbstractCommandLineRunner is responsible for actually writing the file whose
    // path is stored in this field.
    options.setSourceMapOutputPath("dummy");
    options.applyInputSourceMaps = true;
    options.setSourceMapIncludeSourcesContent(true);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    List<SourceFile> externs =
        Collections.singletonList(
            SourceFile.fromCode(
                "externs.js",
                """
                var console = {};
                 console.log = function() {};
                """));
    List<SourceFile> srcs =
        Collections.singletonList(
            SourceFile.fromCode(
                "input.js",
                SOURCE_MAP_TEST_CODE
                    + "\n//# sourceMappingURL="
                    + BASE64_ENCODED_SOURCE_MAP_WITH_CONTENT));
    compiler.init(externs, srcs, options);

    compiler.parse();
    compiler.check();
    compiler.performTranspilationAndOptimizations(SegmentOfCompilationToRun.OPTIMIZATIONS);
    compiler.performFinalizations();

    final Result result = compiler.getResult();

    String source = compiler.toSource();
    assertThat(source).isEqualTo("console.log(new function(){}(1));");

    final SourceMap sourceMap = result.sourceMap;
    assertThat(sourceMap).isNotNull();

    // Check sourcemap output
    final StringBuilder sourceMapStringBuilder = new StringBuilder();
    final String outputJSFile = "output.js";
    sourceMap.appendTo(sourceMapStringBuilder, outputJSFile);
    final String sourceMapString = sourceMapStringBuilder.toString();
    assertThat(sourceMapString).isEqualTo(RESULT_SOURCE_MAP_WITH_CONTENT);
  }

  @Test
  public void testCheckSaveRestore3StagesNoInputFiles() throws Exception {
    // There's an edge case where a chunk may be empty.
    // The compiler covers this weird case by adding a special "fillFile" into empty chunks.
    // This makes the logic in passes like CrossChunkCodeMotion easier.
    // However, we also need to drop the phony "fillFiles" in several cases.
    // One of those cases is serialization.
    // This can lead to an odd situation where deserialization doesn't see a SourceFile
    // for one of these "fillFiles".
    // This led to a NullPointerException in the past.
    // This test case exists to test the fix for that.
    Compiler compiler = new Compiler(new TestErrorManager());

    CompilerOptions options = new CompilerOptions();
    // Late localization happens in stage 3, so this forces stage 3 to actually have some
    // effect on the AST.
    options.setDoLateLocalization(true);

    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    ImmutableList<SourceFile> externs = ImmutableList.of();
    ImmutableList<SourceFile> srcs = ImmutableList.of();
    compiler.init(externs, srcs, options);

    compiler.parse();
    compiler.check();
    final CompilerInput onlyInputBeforeSave =
        getOnlyElement(compiler.getChunkGraph().getAllInputs());
    // this is the special name used for a single fill file when there are no inputs
    assertThat(onlyInputBeforeSave.getName()).isEqualTo("$strong$$fillFile");

    final byte[] stateAfterChecks = getSavedCompilerState(compiler);

    compiler = new Compiler(new TestErrorManager());
    compiler.init(externs, srcs, options);
    restoreCompilerState(compiler, stateAfterChecks);

    // The fillFile is still listed as the only input
    final CompilerInput onlyInputAfterRestore =
        getOnlyElement(compiler.getChunkGraph().getAllInputs());
    assertThat(onlyInputAfterRestore.getName()).isEqualTo("$strong$$fillFile");
  }

  private String concatStrings(String... strings) {
    return stream(strings).collect(joining());
  }

  private void restoreCompilerState(Compiler compiler, byte[] stateAfterChecks)
      throws IOException, ClassNotFoundException {
    try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(stateAfterChecks)) {
      compiler.restoreState(byteArrayInputStream);
    } catch (Exception e) {
      throw new AssertionError("restoring compiler state failed", e);
    }
  }

  private byte[] getSavedCompilerState(Compiler compiler) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    compiler.saveState(outputStream);
    outputStream.close();
    return outputStream.toByteArray();
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
    compiler.performTranspilationAndOptimizations(SegmentOfCompilationToRun.OPTIMIZATIONS);

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
    compiler.performTranspilationAndOptimizations(SegmentOfCompilationToRun.OPTIMIZATIONS);

    String source = compiler.toSource();
    assertThat(source).isEqualTo("'use strict';console.log(0);");
  }

  @Test
  public void testExternsDependencyPruning() {
    ImmutableList<SourceFile> inputs =
        ImmutableList.of(
            SourceFile.fromCode(
                "unused", "/** @fileoverview @typeSummary */ goog.provide('unused');"),
            SourceFile.fromCode(
                "moocher", "/** @fileoverview @typeSummary */ goog.require('something');"),
            SourceFile.fromCode(
                "something", "/** @fileoverview @typeSummary */ goog.provide('something');"));

    CompilerOptions options = createNewFlagBasedOptions();
    options.setDependencyOptions(DependencyOptions.pruneLegacyForEntryPoints(ImmutableList.of()));

    ImmutableList<SourceFile> externs = ImmutableList.of();
    Compiler compiler = new Compiler();
    compiler.compile(externs, inputs, options);

    assertThat(compiler.getExternsRoot().getChildCount()).isEqualTo(3);
    assertExternIndex(compiler, 0, " [synthetic:externs] "); // added by VarCheck
    assertExternIndex(compiler, 1, "something");
    assertExternIndex(compiler, 2, "moocher");
  }

  private void assertExternIndex(Compiler compiler, int index, String name) {
    assertThat(compiler.getExternsRoot().getChildAtIndex(index))
        .isSameInstanceAs(compiler.getInput(new InputId(name)).getAstRoot(compiler));
  }

  // https://github.com/google/closure-compiler/issues/2692
  @Test
  public void testGoogNamespaceEntryPoint() throws Exception {
    ImmutableList<SourceFile> inputs =
        ImmutableList.of(
            SourceFile.fromCode(
                "/index.js",
                """
                var goog = {};
                goog.provide = function(ns) {}; // stub, compiled out.
                goog.provide('foobar');
                const foo = require('./foo.js').default;
                foo('hello');
                """),
            SourceFile.fromCode("/foo.js", "export default (foo) => { alert(foo); }"));

    ImmutableList<ModuleIdentifier> entryPoints =
        ImmutableList.of(ModuleIdentifier.forClosure("goog:foobar"));

    CompilerOptions options = createNewFlagBasedOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setDependencyOptions(DependencyOptions.pruneLegacyForEntryPoints(entryPoints));
    options.setProcessCommonJSModules(true);
    ImmutableList<SourceFile> externs =
        ImmutableList.of(
            new TestExternsBuilder().addAlert().buildExternsFile("default_externs.js"));

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
    ImmutableList<SourceFile> inputs =
        ImmutableList.of(
            SourceFile.fromCode("/index[0].js", "import foo from './foo.js'; foo('hello');"),
            SourceFile.fromCode("/foo.js", "export default (foo) => { alert(foo); }"));

    ImmutableList<ModuleIdentifier> entryPoints =
        ImmutableList.of(ModuleIdentifier.forFile("/index[0].js"));

    CompilerOptions options = createNewFlagBasedOptions();
    options.setDependencyOptions(DependencyOptions.pruneLegacyForEntryPoints(entryPoints));
    ImmutableList<SourceFile> externs =
        ImmutableList.of(
            new TestExternsBuilder().addAlert().buildExternsFile("default_externs.js"));

    Compiler compiler = new Compiler();
    compiler.compile(externs, inputs, options);

    Result result = compiler.getResult();
    assertThat(result.errors).isEmpty();
  }

  @Test
  public void testExternsFileAsEntryPoint() throws Exception {
    // Test that you can specify externs as entry points.
    // This allows all inputs to be passed to the compiler under the --js flag,
    // relying on dependency management to sort out which ones are externs or weak files
    ImmutableList<SourceFile> inputs =
        ImmutableList.of(
            SourceFile.fromCode(
                "/externs.js", "/** @fileoverview @externs */ /** @const {number} */ var bar = 1;"),
            SourceFile.fromCode("/foo.js", "console.log(0);"));

    ImmutableList<ModuleIdentifier> entryPoints =
        ImmutableList.of(ModuleIdentifier.forFile("/externs.js"));

    CompilerOptions options = createNewFlagBasedOptions();
    options.setDependencyOptions(DependencyOptions.pruneForEntryPoints(entryPoints));

    ImmutableList<SourceFile> externs =
        ImmutableList.of(
            new TestExternsBuilder().addConsole().buildExternsFile("default_externs.js"));

    Compiler compiler = new Compiler();
    compiler.compile(externs, inputs, options);

    Result result = compiler.getResult();
    assertThat(result.errors).isEmpty();
    assertThat(compiler.toSource()).isEmpty(); // Empty since srcs are pruned.
  }

  @Test
  public void testExternsFileAsEntryPoint2() throws Exception {
    // Test code reference to an extern that doesn't exist,
    // but the extern is still the sole entry point.
    ImmutableList<SourceFile> inputs =
        ImmutableList.of(
            SourceFile.fromCode(
                "/externs.js", "/** @fileoverview @externs */ /** @const {number} */ var bar = 1;"),
            SourceFile.fromCode("/foo.js", "console.log(nonexistentExtern);"));

    ImmutableList<ModuleIdentifier> entryPoints =
        ImmutableList.of(ModuleIdentifier.forFile("/externs.js"));

    CompilerOptions options = createNewFlagBasedOptions();
    options.setDependencyOptions(DependencyOptions.pruneForEntryPoints(entryPoints));

    ImmutableList<SourceFile> externs =
        ImmutableList.of(
            new TestExternsBuilder().addConsole().buildExternsFile("default_externs.js"));

    Compiler compiler = new Compiler();
    compiler.compile(externs, inputs, options);

    Result result = compiler.getResult();
    assertThat(result.errors).isEmpty();
    assertThat(compiler.toSource()).isEmpty();
  }

  @Test
  public void testExternsFileAsEntryPoint3() throws Exception {
    // Test code reference to an extern that doesn't exist,
    // but the extern and source files are both entry points
    ImmutableList<SourceFile> inputs =
        ImmutableList.of(
            SourceFile.fromCode(
                "/externs.js", "/** @fileoverview @externs */ /** @const {number} */ var bar = 1;"),
            SourceFile.fromCode("/foo.js", "console.log(nonexistentExtern);"));

    ImmutableList<ModuleIdentifier> entryPoints =
        ImmutableList.of(
            ModuleIdentifier.forFile("/externs.js"), ModuleIdentifier.forFile("/foo.js"));

    CompilerOptions options = createNewFlagBasedOptions();
    options.setDependencyOptions(DependencyOptions.pruneForEntryPoints(entryPoints));

    ImmutableList<SourceFile> externs =
        ImmutableList.of(
            new TestExternsBuilder().addConsole().buildExternsFile("default_externs.js"));

    Compiler compiler = new Compiler();
    compiler.compile(externs, inputs, options);

    Result result = compiler.getResult();
    assertThat(result.errors).hasSize(1);
    assertThat(result.errors.get(0).type()).isEqualTo(VarCheck.UNDEFINED_VAR_ERROR);
  }

  @Test
  public void testExternsFileAsEntryPoint4() throws Exception {
    // Test that has a code reference to an extern that does exist,
    // and the extern and source files are both entry points
    ImmutableList<SourceFile> inputs =
        ImmutableList.of(
            SourceFile.fromCode(
                "/externs.js", "/** @fileoverview @externs */ /** @const {number} */ var bar = 1;"),
            SourceFile.fromCode("/foo.js", "console.log(bar);"));

    ImmutableList<ModuleIdentifier> entryPoints =
        ImmutableList.of(
            ModuleIdentifier.forFile("/externs.js"), ModuleIdentifier.forFile("/foo.js"));

    CompilerOptions options = createNewFlagBasedOptions();
    options.setDependencyOptions(DependencyOptions.pruneForEntryPoints(entryPoints));

    ImmutableList<SourceFile> externs =
        ImmutableList.of(
            new TestExternsBuilder().addConsole().buildExternsFile("default_externs.js"));

    Compiler compiler = new Compiler();
    compiler.compile(externs, inputs, options);

    Result result = compiler.getResult();
    assertThat(result.errors).isEmpty();
    assertThat(compiler.toSource()).isEqualTo("console.log(bar);");
  }

  @Test
  public void testExternsFileAsEntryPoint5() throws Exception {
    // Test that has a code reference to an extern that does exist,
    // and only the source source file is an entry point
    ImmutableList<SourceFile> inputs =
        ImmutableList.of(
            SourceFile.fromCode(
                "/externs.js", "/** @fileoverview @externs */ /** @const {number} */ var bar = 1;"),
            SourceFile.fromCode("/foo.js", "console.log(bar);"));

    ImmutableList<ModuleIdentifier> entryPoints =
        ImmutableList.of(ModuleIdentifier.forFile("/foo.js"));

    CompilerOptions options = createNewFlagBasedOptions();
    options.setDependencyOptions(DependencyOptions.pruneForEntryPoints(entryPoints));

    ImmutableList<SourceFile> externs =
        ImmutableList.of(
            new TestExternsBuilder().addConsole().buildExternsFile("default_externs.js"));

    Compiler compiler = new Compiler();
    compiler.compile(externs, inputs, options);

    Result result = compiler.getResult();
    assertThat(result.errors).isEmpty();
    assertThat(compiler.toSource()).isEqualTo("console.log(bar);");
  }

  @Test
  public void testWeakExternsFileAsEntryPointNoError() throws Exception {
    // Test that if a weak extern file is passed in as entry point, there is no error thrown.
    ImmutableList<SourceFile> inputs =
        ImmutableList.of(
            SourceFile.fromCode(
                "/externs.js",
                "/** @fileoverview @externs */ /** @const {number} */ var bar = 1;",
                SourceKind.WEAK));

    ImmutableList<ModuleIdentifier> entryPoints =
        ImmutableList.of(ModuleIdentifier.forFile("/externs.js"));

    CompilerOptions options = createNewFlagBasedOptions();
    options.setDependencyOptions(DependencyOptions.pruneForEntryPoints(entryPoints));

    ImmutableList<SourceFile> externs = ImmutableList.of();

    Compiler compiler = new Compiler();
    compiler.compile(externs, inputs, options);

    Result result = compiler.getResult();
    assertThat(result.errors).isEmpty();
    assertThat(compiler.toSource()).isEmpty();
  }

  @Test
  public void testGetEmptyResult() {
    Result result = new Compiler().getResult();
    assertThat(result.errors).isEmpty();
  }

  @Test
  public void testAnnotation() {
    Compiler compiler = new Compiler();

    assertThat(compiler.runJ2clPasses()).isFalse();

    compiler.setRunJ2clPasses(true);
    assertThat(compiler.runJ2clPasses()).isTrue();
  }

  @Test
  public void testAddIndexProvider_ThenGetIndex() {
    Compiler compiler = new Compiler();

    compiler.addIndexProvider(
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
        });
    compiler.addIndexProvider(
        new IndexProvider<Double>() {
          @Override
          public Double get() {
            // Normally some shared index would be constructed/updated/returned here.
            return Double.MAX_VALUE;
          }

          @Override
          public Class<Double> getType() {
            return Double.class;
          }
        });

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
    options.setEmitUseStrict(false);
    return options;
  }

  @Test
  public void testProperEs6ModuleOrdering() throws Exception {
    List<SourceFile> sources = new ArrayList<>();
    sources.add(
        SourceFile.fromCode(
            "/entry.js",
            """
            import './b/b.js';
            import './b/a.js';
            import './important.js';
            import './a/b.js';
            import './a/a.js';
            """));
    sources.add(SourceFile.fromCode("/a/a.js", "window['D'] = true;"));
    sources.add(SourceFile.fromCode("/a/b.js", "window['C'] = true;"));
    sources.add(SourceFile.fromCode("/b/a.js", "window['B'] = true;"));
    sources.add(
        SourceFile.fromCode(
            "/b/b.js",
            """
            import foo from './c.js';
            if (foo.settings.inUse) {
              window['E'] = true;
            }
            window['A'] = true;
            """));
    sources.add(
        SourceFile.fromCode(
            "/b/c.js",
            """
            window['BEFOREA'] = true;

            export default {
              settings: {
                inUse: Boolean(document.documentElement['attachShadow'])
              }
            };
            """));
    sources.add(SourceFile.fromCode("/important.js", "window['E'] = false;"));

    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setDependencyOptions(
        DependencyOptions.pruneForEntryPoints(
            ImmutableList.of(ModuleIdentifier.forFile("/entry.js"))));
    ImmutableList<SourceFile> externs = ImmutableList.of();

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
            """
            import {A, B, C1} from './a.js';
            console.log(A)
            console.log(B)
            console.log(C1)
            """));
    sources.add(
        SourceFile.fromCode(
            "/a.js",
            """
            export {B} from './b.js';
            export {C as C1} from './c.js';
            export const A = 'a';
            """));
    sources.add(SourceFile.fromCode("/b.js", "export const B = 'b';"));
    sources.add(SourceFile.fromCode("/c.js", "export const C = 'c';"));

    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setDependencyOptions(
        DependencyOptions.pruneForEntryPoints(
            ImmutableList.of(ModuleIdentifier.forFile("/entry.js"))));
    ImmutableList<SourceFile> externs = ImmutableList.of();

    Compiler compiler = new Compiler();
    Result result = compiler.compile(externs, sources.build(), options);
    assertThat(result.success).isTrue();

    List<String> orderedInputs = new ArrayList<>();
    for (CompilerInput input : compiler.getInputsInOrder()) {
      orderedInputs.add(input.getName());
    }

    assertThat(orderedInputs).containsExactly("/b.js", "/c.js", "/a.js", "/entry.js").inOrder();
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
            """
            /** @fileoverview @provideGoog */
            /** @const */ var goog = goog || {};
            var COMPILED = false;
            """));
    sources.add(
        SourceFile.fromCode(
            "entry.js",
            """
            goog.require('a');
            goog.require('b');
            goog.require('c');
            goog.require('d');
            """));

    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setDependencyOptions(
        DependencyOptions.pruneLegacyForEntryPoints(
            ImmutableList.of(ModuleIdentifier.forFile("entry.js"))));
    ImmutableList<SourceFile> externs = ImmutableList.of();

    for (int iterationCount = 0; iterationCount < 10; iterationCount++) {
      Collections.shuffle(sources);
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
            """
            console.log(module.id);
            __webpack_require__.e(0).then(function() { return __webpack_require__(3); });
            """));
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
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setDependencyOptions(
        DependencyOptions.pruneForEntryPoints(
            ImmutableList.of(ModuleIdentifier.forFile("/entry.js"))));
    options.setProcessCommonJSModules(true);
    options.setModuleResolutionMode(ResolutionMode.WEBPACK);
    ImmutableList<SourceFile> externs =
        ImmutableList.of(
            new TestExternsBuilder().addConsole().buildExternsFile("default_externs.js"));
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
            """
            console.log(module.id);
            __webpack_require__.e(0).then(function() {
              const foo = __webpack_require__(3);
              console.log(foo);
            });
            """));
    sources.add(SourceFile.fromCode("/b.js", "console.log(module.id); module.exports = 'foo';"));

    HashMap<String, String> webpackModulesById = new HashMap<>();
    webpackModulesById.put("1", "/entry.js");
    webpackModulesById.put("2", "/a.js");
    webpackModulesById.put("3", "/b.js");

    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setDependencyOptions(
        DependencyOptions.pruneForEntryPoints(
            ImmutableList.of(ModuleIdentifier.forFile("/entry.js"))));
    options.setProcessCommonJSModules(true);
    options.setModuleResolutionMode(ResolutionMode.WEBPACK);
    ImmutableList<SourceFile> externs =
        ImmutableList.of(
            new TestExternsBuilder().addConsole().buildExternsFile("default_externs.js"));
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
  public void testDynamicImportOrdering3() throws Exception {
    List<SourceFile> sources = new ArrayList<>();
    sources.add(SourceFile.fromCode("/entry.js", "__webpack_require__(2);"));
    sources.add(
        SourceFile.fromCode(
            "/a.js",
            """
            console.log(module.id);
            Promise.all([__webpack_require__.e(0)]).then(function() {
              return __webpack_require__(3);
            });
            """));
    sources.add(SourceFile.fromCode("/b.js", "console.log(module.id); module.exports = 'foo';"));

    HashMap<String, String> webpackModulesById = new HashMap<>();
    webpackModulesById.put("1", "/entry.js");
    webpackModulesById.put("2", "/a.js");
    webpackModulesById.put("3", "/b.js");

    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setDependencyOptions(
        DependencyOptions.pruneForEntryPoints(
            ImmutableList.of(ModuleIdentifier.forFile("/entry.js"))));
    options.setProcessCommonJSModules(true);
    options.setModuleResolutionMode(ResolutionMode.WEBPACK);
    ImmutableList<SourceFile> externs =
        ImmutableList.of(
            new TestExternsBuilder().addConsole().buildExternsFile("default_externs.js"));
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
  public void testCodeReferenceToTypeImport() throws Exception {
    ImmutableList<SourceFile> externs =
        ImmutableList.of(SourceFile.fromCode("extern.js", "/** @externs */ function alert(x) {}"));
    ImmutableList<SourceFile> sources =
        ImmutableList.of(
            SourceFile.fromCode(
                "type.js",
                """
                goog.module('type');

                exports.Type = class {}
                """),
            SourceFile.fromCode(
                "main.js",
                """
                goog.module('main');

                const {Type} = goog.requireType('type');

                alert(new Type());
                """));

    CompilerOptions options = new CompilerOptions();
    options.setClosurePass(true);

    Compiler compiler = new Compiler();

    compiler.init(externs, sources, options);
    compiler.parse();
    compiler.check();

    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(compiler.getErrors())
        .comparingElementsUsing(DIAGNOSTIC_EQUALITY)
        .containsExactly(CheckTypeImportCodeReferences.TYPE_IMPORT_CODE_REFERENCE);
  }

  @Test
  public void testWeakSources() throws Exception {
    ImmutableList<SourceFile> sources =
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
    compiler.performTranspilationAndOptimizations(SegmentOfCompilationToRun.OPTIMIZATIONS);

    assertThat(compiler.getChunkGraph().getChunkCount()).isEqualTo(2);
    assertThat(Iterables.get(compiler.getChunkGraph().getAllChunks(), 0).getName())
        .isEqualTo(JSChunk.STRONG_CHUNK_NAME);
    assertThat(Iterables.get(compiler.getChunkGraph().getAllChunks(), 1).getName())
        .isEqualTo(JSChunk.WEAK_CHUNK_NAME);

    assertThat(compiler.toSource()).isEqualTo("var a={};a.b={};var d={};");
  }

  private void weakSourcesModulesHelper(boolean saveAndRestore) throws Exception {
    JSChunk m1 = new JSChunk("m1");
    m1.add(SourceFile.fromCode("weak1.js", "goog.provide('a');", SourceKind.WEAK));
    m1.add(SourceFile.fromCode("strong1.js", "goog.provide('a.b');", SourceKind.STRONG));
    JSChunk m2 = new JSChunk("m2");
    m2.add(SourceFile.fromCode("weak2.js", "goog.provide('c');", SourceKind.WEAK));
    m2.add(SourceFile.fromCode("strong2.js", "goog.provide('d');", SourceKind.STRONG));

    CompilerOptions options = new CompilerOptions();
    options.setEmitUseStrict(false);
    options.setClosurePass(true);

    Compiler compiler = new Compiler();

    compiler.initChunks(ImmutableList.of(), ImmutableList.of(m1, m2), options);

    compiler.parse();
    compiler.check();

    if (saveAndRestore) {
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      compiler.saveState(byteArrayOutputStream);
      byteArrayOutputStream.close();

      // NOTE: The AST is not expected to be used after serialization to the save file.
      assertThat(compiler.toSource(new ScriptNodeLicensesOnlyTracker(compiler), m1))
          .isEqualTo("goog.provide(\"a.b\");");

      restoreCompilerState(compiler, byteArrayOutputStream.toByteArray());

      // restoring state creates new JSModule objects. the old ones are stale.
      m1 = compiler.getChunkGraph().getChunkByName("m1");
      m2 = compiler.getChunkGraph().getChunkByName("m2");
    }

    compiler.performTranspilationAndOptimizations(SegmentOfCompilationToRun.OPTIMIZATIONS);

    assertThat(compiler.getChunkGraph().getChunkCount()).isEqualTo(3);

    JSChunk weakModule = compiler.getChunkGraph().getChunkByName("$weak$");
    ScriptNodeLicensesOnlyTracker lt = new ScriptNodeLicensesOnlyTracker(compiler);
    assertThat(weakModule).isNotNull();

    assertThat(compiler.toSource(lt, m1)).isEqualTo("var a={};a.b={};");

    assertThat(compiler.toSource(lt, m2)).isEqualTo("var d={};");
    assertThat(compiler.toSource(lt, weakModule)).isEmpty();
  }

  @Test
  public void testWeakSourcesModules() throws Exception {
    weakSourcesModulesHelper(/* saveAndRestore= */ false);
  }

  @Test
  public void testWeakSourcesSaveRestore() throws Exception {
    weakSourcesModulesHelper(/* saveAndRestore= */ true);
  }

  @Test
  public void testWeakSourcesEntryPoint() throws Exception {
    SourceFile extern = SourceFile.fromCode("extern.js", "/** @externs */ function alert(x) {}");
    SourceFile strong =
        SourceFile.fromCode(
            "strong.js",
            """
            goog.module('strong');
            const T = goog.requireType('weak');
            /** @param {!T} x */ function f(x) { alert(x); }
            """,
            SourceKind.STRONG);
    SourceFile weak =
        SourceFile.fromCode(
            "type.js",
            """
            goog.module('weak');
            /** @typedef {number|string} */ exports.T;
            sideeffect();
            """,
            SourceKind.WEAK);

    CompilerOptions options = new CompilerOptions();
    options.setEmitUseStrict(false);
    options.setClosurePass(true);
    options.setDependencyOptions(
        DependencyOptions.pruneForEntryPoints(
            ImmutableList.of(ModuleIdentifier.forClosure("strong"))));

    Compiler compiler = new Compiler();

    compiler.init(ImmutableList.of(extern), ImmutableList.of(strong, weak), options);

    compiler.parse();
    compiler.check();
    compiler.performTranspilationAndOptimizations(SegmentOfCompilationToRun.OPTIMIZATIONS);
    compiler.performFinalizations();

    assertThat(compiler.toSource())
        .isEqualTo("var module$exports$strong={};function module$contents$strong_f(x){alert(x)};");
  }

  @Test
  public void testPreexistingWeakModule() throws Exception {
    JSChunk strong = new JSChunk("m");
    strong.add(SourceFile.fromCode("strong.js", "goog.provide('a');", SourceKind.STRONG));
    JSChunk weak = new JSChunk(JSChunk.WEAK_CHUNK_NAME);
    weak.add(SourceFile.fromCode("weak.js", "goog.provide('b');", SourceKind.WEAK));
    weak.addDependency(strong);

    CompilerOptions options = new CompilerOptions();
    options.setEmitUseStrict(false);
    options.setClosurePass(true);

    Compiler compiler = new Compiler();

    compiler.initChunks(ImmutableList.of(), ImmutableList.of(strong, weak), options);

    compiler.parse();
    compiler.check();
    compiler.performTranspilationAndOptimizations(SegmentOfCompilationToRun.OPTIMIZATIONS);

    assertThat(compiler.getChunkGraph().getChunkCount()).isEqualTo(2);
    assertThat(Iterables.get(compiler.getChunkGraph().getAllChunks(), 0).getName()).isEqualTo("m");
    assertThat(Iterables.get(compiler.getChunkGraph().getAllChunks(), 1).getName())
        .isEqualTo(JSChunk.WEAK_CHUNK_NAME);

    assertThat(compiler.toSource()).isEqualTo("var a={};");
  }

  @Test
  public void testPreexistingWeakModuleWithAdditionalStrongSources() throws Exception {
    JSChunk strong = new JSChunk("m");
    strong.add(SourceFile.fromCode("strong.js", "goog.provide('a');", SourceKind.STRONG));
    JSChunk weak = new JSChunk(JSChunk.WEAK_CHUNK_NAME);
    weak.add(SourceFile.fromCode("weak.js", "goog.provide('b');", SourceKind.WEAK));
    weak.add(
        SourceFile.fromCode(
            "weak_but_actually_strong.js", "goog.provide('c');", SourceKind.STRONG));
    weak.addDependency(strong);

    CompilerOptions options = new CompilerOptions();
    Compiler compiler = new Compiler();

    RuntimeException e =
        assertThrows(
            RuntimeException.class,
            () -> compiler.initChunks(ImmutableList.of(), ImmutableList.of(strong, weak), options));
    assertThat(e)
        .hasMessageThat()
        .contains("Found these strong sources in the weak chunk:\n  weak_but_actually_strong.js");
  }

  @Test
  public void testPreexistingWeakModuleWithMissingWeakSources() throws Exception {
    JSChunk strong = new JSChunk("m");
    strong.add(SourceFile.fromCode("strong.js", "goog.provide('a');", SourceKind.STRONG));
    strong.add(
        SourceFile.fromCode("strong_but_actually_weak.js", "goog.provide('b');", SourceKind.WEAK));
    JSChunk weak = new JSChunk(JSChunk.WEAK_CHUNK_NAME);
    weak.add(SourceFile.fromCode("weak.js", "goog.provide('c');", SourceKind.WEAK));
    weak.addDependency(strong);

    CompilerOptions options = new CompilerOptions();
    Compiler compiler = new Compiler();

    RuntimeException e =
        assertThrows(
            RuntimeException.class,
            () -> compiler.initChunks(ImmutableList.of(), ImmutableList.of(strong, weak), options));
    assertThat(e)
        .hasMessageThat()
        .contains(
            """
            Found these weak sources in other chunks:
              strong_but_actually_weak.js (in chunk m)\
            """);
  }

  @Test
  public void testPreexistingWeakModuleWithIncorrectDependencies() throws Exception {
    JSChunk m1 = new JSChunk("m1");
    JSChunk m2 = new JSChunk("m2");
    JSChunk weak = new JSChunk(JSChunk.WEAK_CHUNK_NAME);
    weak.addDependency(m1);

    CompilerOptions options = new CompilerOptions();
    Compiler compiler = new Compiler();

    RuntimeException e =
        assertThrows(
            RuntimeException.class,
            () -> compiler.initChunks(ImmutableList.of(), ImmutableList.of(m1, m2, weak), options));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo("A weak chunk already exists but it does not depend on every other chunk.");
  }

  @Test
  public void testImplicitWeakSourcesWithEntryPoint() throws Exception {
    SourceFile extern = SourceFile.fromCode("extern.js", "/** @externs */ function alert(x) {}");
    SourceFile strong =
        SourceFile.fromCode(
            "strong.js",
            """
            goog.module('strong');
            const T = goog.requireType('weak');
            /** @param {!T} x */ function f(x) { alert(x); }
            """);
    SourceFile weak =
        SourceFile.fromCode(
            "type.js",
            """
            goog.module('weak');
            /** @typedef {number|string} */ exports.T;
            sideeffect();
            """);

    CompilerOptions options = new CompilerOptions();
    options.setEmitUseStrict(false);
    options.setClosurePass(true);
    options.setDependencyOptions(
        DependencyOptions.pruneForEntryPoints(
            ImmutableList.of(ModuleIdentifier.forClosure("strong"))));

    Compiler compiler = new Compiler();

    compiler.init(ImmutableList.of(extern), ImmutableList.of(strong, weak), options);

    compiler.parse();
    compiler.check();
    compiler.performTranspilationAndOptimizations(SegmentOfCompilationToRun.OPTIMIZATIONS);
    compiler.performFinalizations();

    assertThat(compiler.toSource())
        .isEqualTo("var module$exports$strong={};function module$contents$strong_f(x){alert(x)};");
  }

  @Test
  public void testImplicitWeakSourcesWithEntryPointLegacyPrune() throws Exception {
    SourceFile extern = SourceFile.fromCode("extern.js", "/** @externs */ function alert(x) {}");
    SourceFile strong =
        SourceFile.fromCode(
            "moocher.js",
            """
            goog.requireType('weak');
            /** @param {!weak.T} x */ function f(x) { alert(x); }
            """);
    SourceFile weak =
        SourceFile.fromCode(
            "type.js",
            """
            goog.module('weak');
            /** @typedef {number|string} */ exports.T;
            sideeffect();
            """);

    CompilerOptions options = new CompilerOptions();
    options.setEmitUseStrict(false);
    options.setClosurePass(true);
    options.setDependencyOptions(DependencyOptions.pruneLegacyForEntryPoints(ImmutableList.of()));

    Compiler compiler = new Compiler();

    compiler.init(ImmutableList.of(extern), ImmutableList.of(strong, weak), options);

    compiler.parse();
    compiler.check();
    compiler.performTranspilationAndOptimizations(SegmentOfCompilationToRun.OPTIMIZATIONS);
    compiler.performFinalizations();

    assertThat(compiler.toSource()).isEqualTo("function f(x){alert(x)};");
  }

  @Test
  public void testTransitiveImplicitWeakSourcesWithEntryPoint() throws Exception {
    SourceFile extern = SourceFile.fromCode("extern.js", "/** @externs */ function alert(x) {}");
    SourceFile strong =
        SourceFile.fromCode(
            "strong.js",
            """
            goog.module('strong');
            const T = goog.requireType('weakEntry');
            /** @param {!T} x */ function f(x) { alert(x); }
            """);
    SourceFile weakEntry =
        SourceFile.fromCode(
            "weakEntry.js",
            """
            goog.module('weakEntry');
            const w = goog.require('weakByAssociation');
            exports = w;
            """);
    SourceFile weakByAssociation =
        SourceFile.fromCode(
            "weakByAssociation.js",
            """
            goog.module('weakByAssociation');
            /** @typedef {number|string} */ exports.T;
            sideEffect();
            """);

    CompilerOptions options = new CompilerOptions();
    options.setEmitUseStrict(false);
    options.setClosurePass(true);
    options.setDependencyOptions(
        DependencyOptions.pruneForEntryPoints(
            ImmutableList.of(ModuleIdentifier.forClosure("strong"))));

    Compiler compiler = new Compiler();

    compiler.init(
        ImmutableList.of(extern), ImmutableList.of(strong, weakEntry, weakByAssociation), options);

    compiler.parse();
    compiler.check();
    compiler.performTranspilationAndOptimizations(SegmentOfCompilationToRun.OPTIMIZATIONS);
    compiler.performFinalizations();

    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(compiler.getErrors()).isEmpty();

    assertThat(compiler.toSource())
        .isEqualTo("var module$exports$strong={};function module$contents$strong_f(x){alert(x)};");
  }

  @Test
  public void testExplicitWeakEntryPointIsError() throws Exception {
    SourceFile extern = SourceFile.fromCode("extern.js", "");
    SourceFile weakEntry =
        SourceFile.fromCode(
            "weakEntry.js",
            """
            goog.module('weakEntry');
            /** @typedef {number|string} */ exports.T;
            sideEffect();
            """,
            SourceKind.WEAK);

    CompilerOptions options = new CompilerOptions();
    options.setDependencyOptions(
        DependencyOptions.pruneForEntryPoints(
            ImmutableList.of(ModuleIdentifier.forClosure("weakEntry"))));

    Compiler compiler = new Compiler();

    compiler.init(ImmutableList.of(extern), ImmutableList.of(weakEntry), options);
    compiler.parse();

    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(compiler.getErrors()).hasSize(1);
    assertError(getOnlyElement(compiler.getErrors()))
        .hasMessage("Explicit entry point input must not be weak: weakEntry.js");
  }

  @Test
  public void testImplicitWeakEntryPointIsWarning() throws Exception {
    SourceFile extern = SourceFile.fromCode("extern.js", "/** @externs */ function alert(x) {}");
    SourceFile weakMoocher =
        SourceFile.fromCode(
            "weakMoocher.js",
            """
            const {T} = goog.require('weakByAssociation');
            /** @param {!T} x */ function f(x) { alert(x); }
            """,
            SourceKind.WEAK);
    SourceFile weakByAssociation =
        SourceFile.fromCode(
            "weakByAssociation.js",
            """
            goog.module('weakByAssociation');
            /** @typedef {number|string} */ exports.T;
            sideeffect();
            """);

    CompilerOptions options = new CompilerOptions();
    options.setDependencyOptions(DependencyOptions.pruneLegacyForEntryPoints(ImmutableList.of()));

    Compiler compiler = new Compiler();

    compiler.init(
        ImmutableList.of(extern), ImmutableList.of(weakMoocher, weakByAssociation), options);
    compiler.parse();

    assertThat(compiler.getErrors()).isEmpty();
    assertThat(compiler.getWarnings()).hasSize(1);
    assertError(getOnlyElement(compiler.getWarnings()))
        .hasMessage("Implicit entry point input should not be weak: weakMoocher.js");
  }

  @Test
  public void testWeakStronglyReachableIsError() throws Exception {
    SourceFile extern = SourceFile.fromCode("extern.js", "/** @externs */ function alert(x) {}");
    SourceFile strong =
        SourceFile.fromCode(
            "strong.js",
            """
            goog.module('strong');
            const T = goog.require('weak');
            /** @param {!T} x */ function f(x) { alert(x); }
            """,
            SourceKind.STRONG);
    SourceFile weak =
        SourceFile.fromCode(
            "weak.js",
            """
            goog.module('weak');
            /** @typedef {number|string} */ exports.T;
            sideEffect();
            """,
            SourceKind.WEAK);

    CompilerOptions options = new CompilerOptions();
    options.setDependencyOptions(
        DependencyOptions.pruneForEntryPoints(
            ImmutableList.of(ModuleIdentifier.forClosure("strong"))));

    Compiler compiler = new Compiler();

    compiler.init(ImmutableList.of(extern), ImmutableList.of(strong, weak), options);
    compiler.parse();

    assertThat(compiler.getWarnings()).isEmpty();
    assertThat(compiler.getErrors()).hasSize(1);
    assertError(getOnlyElement(compiler.getErrors()))
        .hasMessage("File strongly reachable from an entry point must not be weak: weak.js");
  }

  @Test
  public void restoreState_doesNotCreateColorRegistryIfTypecheckingSkipped() throws Exception {
    CompilerOptions options = new CompilerOptions();
    options.setCheckTypes(false);
    Compiler compiler = new Compiler();

    ImmutableList<SourceFile> inputs = ImmutableList.of(SourceFile.fromCode("in1", ""));
    compiler.init(ImmutableList.of(), inputs, options);

    compiler.parse();
    compiler.check();

    assertThat(compiler.hasTypeCheckingRun()).isFalse();

    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    compiler.saveState(byteArrayOutputStream);
    byteArrayOutputStream.close();

    compiler = new Compiler();
    compiler.init(ImmutableList.of(), inputs, options);
    restoreCompilerState(compiler, byteArrayOutputStream.toByteArray());

    assertThat(compiler.hasTypeCheckingRun()).isFalse();
    assertThat(compiler.hasOptimizationColors()).isFalse();
  }

  @Test
  public void librariesInjectedInStage1_notReinjectedInStage2() throws Exception {
    CompilerOptions options = new CompilerOptions();
    options.setEmitUseStrict(false);

    Compiler compiler = new Compiler();

    ImmutableList<SourceFile> inputs = ImmutableList.of(SourceFile.fromCode("in1", ""));
    compiler.init(ImmutableList.of(), inputs, options);

    compiler.parse();
    compiler.check();
    compiler.ensureLibraryInjected("base", /* force= */ true);

    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    compiler.saveState(byteArrayOutputStream);
    byteArrayOutputStream.close();

    compiler = new Compiler();
    compiler.init(ImmutableList.of(), inputs, options);
    restoreCompilerState(compiler, byteArrayOutputStream.toByteArray());

    Node oldAst = compiler.getJsRoot().cloneTree();

    // should not change the AST as 'base' was already injected.
    compiler.ensureLibraryInjected("base", /* force= */ true);

    assertNode(compiler.getJsRoot()).isEqualTo(oldAst);
  }

  @Test
  public void injectLibrariesBeforeAndAfterStage1() throws Exception {
    CompilerOptions options = new CompilerOptions();
    options.setEmitUseStrict(false);

    Compiler compiler = new Compiler();

    ImmutableList<SourceFile> inputs = ImmutableList.of(SourceFile.fromCode("in1", ""));
    compiler.init(ImmutableList.of(), inputs, options);

    compiler.parse();
    compiler.check();
    compiler.ensureLibraryInjected("base", /* force= */ true);

    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    compiler.saveState(byteArrayOutputStream);
    byteArrayOutputStream.close();

    compiler = new Compiler();
    compiler.init(ImmutableList.of(), inputs, options);

    restoreCompilerState(compiler, byteArrayOutputStream.toByteArray());

    Node oldAst = compiler.getJsRoot().cloneTree();

    compiler.ensureLibraryInjected("es6/set", /* force= */ true);

    assertNode(compiler.getJsRoot()).isNotEqualTo(oldAst);

    String source = compiler.toSource();
    int jscompDefinition = source.indexOf("var $jscomp");
    int jscompPolyfillDefinition = source.indexOf("$jscomp.polyfill");

    // The definition of $jscomp.polyfill should be injected after the definition of 'var $jscomp',
    // not before.
    assertThat(jscompDefinition).isLessThan(jscompPolyfillDefinition);
  }

  @Test
  public void testTypesAreRemoved() {
    CompilerOptions options = new CompilerOptions();
    options.setCheckTypes(true);
    SourceFile input =
        SourceFile.fromCode(
            "input.js",
            """
            /** @license @type {!Foo} */
            class Foo {}
            class Bar {}
            /** @typedef {number} */ let Num;
            const n = /** @type {!Num} */ (5);
            var /** !Foo */ f = new Bar;
            """);
    Compiler compiler = new Compiler();
    WeakReference<JSTypeRegistry> registryWeakReference =
        new WeakReference<>(compiler.getTypeRegistry());
    compiler.compile(EMPTY_EXTERNS.get(0), input, options);

    // Just making sure that typechecking ran and didn't crash.  It would be reasonable
    // for there also to be other type errors in this code before the final null assignment.
    assertThat(compiler.getWarnings()).hasSize(1);
    assertError(compiler.getWarnings().get(0)).hasType(TYPE_MISMATCH_WARNING);

    System.gc();
    System.runFinalization();

    assertThat(registryWeakReference.get()).isNull();
  }

  @Test
  public void testTypedAstFilesystem_extraInputFilesAvailable() {
    // Given
    SourceFile file1 = SourceFile.fromCode("test1.js", "");
    SourceFile file2 = SourceFile.fromCode("test2.js", "");

    TypedAst.List typedAstList =
        TypedAst.List.newBuilder()
            .addTypedAsts(
                TypedAst.newBuilder()
                    .setStringPool(StringPool.empty().toProto())
                    .setSourceFilePool(
                        SourceFilePool.newBuilder()
                            .addSourceFile(file1.getProto())
                            .addSourceFile(file2.getProto()))
                    .addCodeAst(
                        LazyAst.newBuilder()
                            .setSourceFile(1)
                            .setScript(
                                AstNode.newBuilder()
                                    .setKind(NodeKind.SOURCE_FILE)
                                    .build()
                                    .toByteString()))
                    .addCodeAst(
                        LazyAst.newBuilder()
                            .setSourceFile(2)
                            .setScript(
                                AstNode.newBuilder()
                                    .setKind(NodeKind.SOURCE_FILE)
                                    .build()
                                    .toByteString())))
            .build();
    InputStream typedAstListStream = new ByteArrayInputStream(typedAstList.toByteArray());

    Compiler compiler = new Compiler();
    CompilerOptions compilerOptions = new CompilerOptions();
    compiler.initOptions(compilerOptions);

    // When
    compiler.initWithTypedAstFilesystem(
        ImmutableList.of(), ImmutableList.of(file1), compilerOptions, typedAstListStream);

    assertThrows(
        Exception.class,
        () -> {
          var unused = compiler.getTypedAstDeserializer(file2);
        });

    Node script = compiler.getRoot().getSecondChild().getFirstChild();
    assertThat(script.getStaticSourceFile()).isSameInstanceAs(file1);
  }

  @Test
  public void testTypedAstFilesystem_someInputFilesUnavailable_crashes() {
    // Given
    SourceFile file = SourceFile.fromCode("test.js", "");
    InputStream typedAstListStream = new ByteArrayInputStream(new byte[0]);
    Compiler compiler = new Compiler();
    CompilerOptions compilerOptions = new CompilerOptions();
    compiler.initOptions(compilerOptions);

    Exception e =
        assertThrows(
            Exception.class,
            () ->
                compiler.initWithTypedAstFilesystem(
                    ImmutableList.of(),
                    ImmutableList.of(file),
                    compilerOptions,
                    typedAstListStream));
    assertThat(e).hasMessageThat().containsMatch("missing .* test.js");
  }

  @Test
  public void testTypedAstFilesystem_someAvailableFilesDuplicated_usesFirstCopy() {
    // Given
    SourceFile file = SourceFile.fromCode("test.js", "");

    TypedAst typedAst0 =
        TypedAst.newBuilder()
            .setStringPool(StringPool.empty().toProto())
            .setSourceFilePool(SourceFilePool.newBuilder().addSourceFile(file.getProto()))
            .addCodeAst(
                LazyAst.newBuilder()
                    .setSourceFile(1)
                    .setScript(
                        AstNode.newBuilder().setKind(NodeKind.SOURCE_FILE).build().toByteString()))
            .build();
    TypedAst typedAst1 =
        TypedAst.newBuilder()
            .setStringPool(StringPool.empty().toProto())
            .setSourceFilePool(SourceFilePool.newBuilder().addSourceFile(file.getProto()))
            .addCodeAst(
                LazyAst.newBuilder()
                    .setSourceFile(1)
                    .setScript(
                        AstNode.newBuilder()
                            .setKind(NodeKind.SOURCE_FILE)
                            .addChild(AstNode.newBuilder().setKind(NodeKind.VAR_DECLARATION))
                            .build()
                            .toByteString()))
            .build();
    InputStream typedAstListStream =
        new ByteArrayInputStream(
            TypedAst.List.newBuilder()
                .addTypedAsts(typedAst0)
                .addTypedAsts(typedAst1)
                .build()
                .toByteArray());

    Compiler compiler = new Compiler();
    CompilerOptions compilerOptions = new CompilerOptions();
    compiler.initOptions(compilerOptions);

    // When
    compiler.initWithTypedAstFilesystem(
        ImmutableList.of(), ImmutableList.of(file), compilerOptions, typedAstListStream);

    // Then
    Node script = compiler.getRoot().getSecondChild().getFirstChild();
    assertThat(script.getStaticSourceFile()).isSameInstanceAs(file);
    assertThat(script.hasChildren()).isFalse();
  }

  @Test
  public void testTypedAstFilesystem_syntheticExternsFile_isCattedAcrossTypedAsts() {
    // Given
    Compiler compiler = new Compiler();
    CompilerOptions compilerOptions = new CompilerOptions();
    compiler.initOptions(compilerOptions);
    SourceFile syntheticFile = compiler.SYNTHETIC_EXTERNS_FILE;
    SourceFile fileOne = SourceFile.fromCode("one.js", "");
    SourceFile fileTwo = SourceFile.fromCode("two.js", "");

    TypedAst typedAst0 =
        TypedAst.newBuilder()
            .setStringPool(StringPool.empty().toProto())
            .setSourceFilePool(
                SourceFilePool.newBuilder()
                    .addSourceFile(syntheticFile.getProto())
                    .addSourceFile(fileOne.getProto()))
            .addCodeAst(
                LazyAst.newBuilder()
                    .setSourceFile(1)
                    .setScript(
                        AstNode.newBuilder()
                            .setKind(NodeKind.SOURCE_FILE)
                            .addChild(AstNode.newBuilder().setKind(NodeKind.CONST_DECLARATION))
                            .build()
                            .toByteString()))
            .addCodeAst(
                LazyAst.newBuilder()
                    .setSourceFile(2)
                    .setScript(
                        AstNode.newBuilder().setKind(NodeKind.SOURCE_FILE).build().toByteString()))
            .build();
    TypedAst typedAst1 =
        TypedAst.newBuilder()
            .setStringPool(StringPool.empty().toProto())
            .setSourceFilePool(
                SourceFilePool.newBuilder()
                    .addSourceFile(syntheticFile.getProto())
                    .addSourceFile(fileTwo.getProto()))
            .addCodeAst(
                LazyAst.newBuilder()
                    .setSourceFile(1)
                    .setScript(
                        AstNode.newBuilder()
                            .setKind(NodeKind.SOURCE_FILE)
                            .addChild(AstNode.newBuilder().setKind(NodeKind.VAR_DECLARATION))
                            .build()
                            .toByteString()))
            .addCodeAst(
                LazyAst.newBuilder()
                    .setSourceFile(2)
                    .setScript(
                        AstNode.newBuilder().setKind(NodeKind.SOURCE_FILE).build().toByteString()))
            .build();
    InputStream typedAstListStream =
        new ByteArrayInputStream(
            TypedAst.List.newBuilder()
                .addTypedAsts(typedAst0)
                .addTypedAsts(typedAst1)
                .build()
                .toByteArray());

    // When
    compiler.initWithTypedAstFilesystem(
        ImmutableList.of(),
        ImmutableList.of(fileOne, fileTwo),
        compilerOptions,
        typedAstListStream);

    // Then
    Node insertedExterns = compiler.getExternsRoot().getOnlyChild();
    Node lazyExterns = compiler.getSynthesizedExternsInput().getAstRoot(compiler);

    assertThat(insertedExterns).isEqualTo(lazyExterns);
    assertThat(lazyExterns.getStaticSourceFile()).isSameInstanceAs(compiler.SYNTHETIC_EXTERNS_FILE);
    assertThat(lazyExterns.getFirstChild().isConst()).isTrue();
    assertThat(lazyExterns.getSecondChild().isVar()).isTrue();
  }

  @Test
  public void testTypedAstFilesystem_doesNotParseWeakFileTypedAstContents() {
    // Given
    SourceFile weakFile = SourceFile.fromCode("weak.js", "0", SourceKind.WEAK);
    SourceFile strongFile = SourceFile.fromCode("strong.js", "1");
    Compiler compiler = new Compiler();
    CompilerOptions compilerOptions = new CompilerOptions();
    compiler.initOptions(compilerOptions);

    TypedAst typedAst =
        TypedAst.newBuilder()
            .setStringPool(StringPool.empty().toProto())
            .setSourceFilePool(
                SourceFilePool.newBuilder()
                    .addSourceFile(weakFile.getProto())
                    .addSourceFile(strongFile.getProto()))
            .addCodeAst(
                LazyAst.newBuilder()
                    .setSourceFile(1)
                    .setScript(
                        AstNode.newBuilder()
                            .setKind(NodeKind.SOURCE_FILE)
                            .addChild(
                                AstNode.newBuilder()
                                    .setKind(NodeKind.EXPRESSION_STATEMENT)
                                    .addChild(
                                        AstNode.newBuilder()
                                            .setKind(NodeKind.NUMBER_LITERAL)
                                            .setDoubleValue(0)))
                            .build()
                            .toByteString()))
            .addCodeAst(
                LazyAst.newBuilder()
                    .setSourceFile(2)
                    .setScript(
                        AstNode.newBuilder()
                            .setKind(NodeKind.SOURCE_FILE)
                            .addChild(
                                AstNode.newBuilder()
                                    .setKind(NodeKind.EXPRESSION_STATEMENT)
                                    .addChild(
                                        AstNode.newBuilder()
                                            .setKind(NodeKind.NUMBER_LITERAL)
                                            .setDoubleValue(1)))
                            .build()
                            .toByteString()))
            .build();

    InputStream typedAstListStream =
        new ByteArrayInputStream(
            TypedAst.List.newBuilder().addTypedAsts(typedAst).build().toByteArray());

    // When
    compiler.initWithTypedAstFilesystem(
        ImmutableList.of(),
        ImmutableList.of(weakFile, strongFile),
        compilerOptions,
        typedAstListStream);
    Node weakScript =
        compiler
            .getChunkGraph()
            .getChunkByName(JSChunk.WEAK_CHUNK_NAME)
            .getInputs()
            .get(0)
            .getAstRoot(compiler);
    Node strongScript =
        compiler
            .getChunkGraph()
            .getChunkByName(JSChunk.STRONG_CHUNK_NAME)
            .getInputs()
            .get(0)
            .getAstRoot(compiler);

    // Then
    assertNode(weakScript).hasNoChildren();
    assertNode(strongScript).hasOneChild();
    assertNode(strongScript.getFirstChild()).hasToken(Token.EXPR_RESULT);
    assertNode(strongScript.getFirstFirstChild()).isNumber(1);
  }

  @Test
  public void testTypedAstFilesystemWithModules_doesNotParseWeakFileTypedAstContents() {
    // Given
    SourceFile weakFile = SourceFile.fromCode("weak.js", "0", SourceKind.WEAK);
    SourceFile strongFile = SourceFile.fromCode("strong.js", "1");
    Compiler compiler = new Compiler();
    CompilerOptions compilerOptions = new CompilerOptions();
    compiler.initOptions(compilerOptions);
    JSChunk weakChunk = new JSChunk(JSChunk.WEAK_CHUNK_NAME);
    JSChunk strongChunk = new JSChunk("a");
    weakChunk.add(weakFile);
    weakChunk.addDependency(strongChunk);
    strongChunk.add(strongFile);

    TypedAst typedAst =
        TypedAst.newBuilder()
            .setStringPool(StringPool.empty().toProto())
            .setSourceFilePool(
                SourceFilePool.newBuilder()
                    .addSourceFile(weakFile.getProto())
                    .addSourceFile(strongFile.getProto()))
            .addCodeAst(
                LazyAst.newBuilder()
                    .setSourceFile(1)
                    .setScript(
                        AstNode.newBuilder()
                            .setKind(NodeKind.SOURCE_FILE)
                            .addChild(
                                AstNode.newBuilder()
                                    .setKind(NodeKind.EXPRESSION_STATEMENT)
                                    .addChild(
                                        AstNode.newBuilder()
                                            .setKind(NodeKind.NUMBER_LITERAL)
                                            .setDoubleValue(0)))
                            .build()
                            .toByteString()))
            .addCodeAst(
                LazyAst.newBuilder()
                    .setSourceFile(2)
                    .setScript(
                        AstNode.newBuilder()
                            .setKind(NodeKind.SOURCE_FILE)
                            .addChild(
                                AstNode.newBuilder()
                                    .setKind(NodeKind.EXPRESSION_STATEMENT)
                                    .addChild(
                                        AstNode.newBuilder()
                                            .setKind(NodeKind.NUMBER_LITERAL)
                                            .setDoubleValue(1)))
                            .build()
                            .toByteString()))
            .build();

    InputStream typedAstListStream =
        new ByteArrayInputStream(
            TypedAst.List.newBuilder().addTypedAsts(typedAst).build().toByteArray());

    // When
    compiler.initChunksWithTypedAstFilesystem(
        ImmutableList.of(),
        ImmutableList.of(strongChunk, weakChunk),
        compilerOptions,
        typedAstListStream);
    compiler.parse();
    Node weakScript = weakChunk.getInputs().get(0).getAstRoot(compiler);
    Node strongScript = strongChunk.getInputs().get(0).getAstRoot(compiler);

    // Then
    assertNode(weakScript).hasNoChildren();
    assertNode(strongScript).hasOneChild();
    assertNode(strongScript.getFirstChild()).hasToken(Token.EXPR_RESULT);
    assertNode(strongScript.getFirstFirstChild()).isNumber(1);
  }

  @Test
  public void testCreateSyntheticExternsInput_setsCorrectInputId() {
    CompilerOptions options = new CompilerOptions();
    Compiler compiler = new Compiler();
    compiler.init(ImmutableList.of(), ImmutableList.of(), options);

    CompilerInput syntheticExterns = compiler.getSynthesizedExternsInput();

    assertThat(compiler.getInputsById().get(syntheticExterns.getInputId()))
        .isSameInstanceAs(syntheticExterns);
  }

  @Test
  public void testStage2SplittingResultsInSameOutput() throws Exception {
    Compiler compiler = new Compiler(new TestErrorManager());
    CompilerOptions options = new CompilerOptions();

    options.setEmitUseStrict(false);

    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    List<SourceFile> externs =
        Collections.singletonList(
            SourceFile.fromCode(
                "externs.js",
                """
                var console = {};
                 console.log = function() {};
                """));
    List<SourceFile> srcs =
        Collections.singletonList(
            SourceFile.fromCode(
                "input.js",
                """
                goog.module('foo');
                const hello = 'hello';
                function f() { return hello; }
                console.log(f());
                """));
    compiler.init(externs, srcs, options);

    // This is what the output should look like after all optimizations.
    String finalOutputAfterOptimizations = "console.log(\"hello\");";

    // Stage 1
    compiler.parse();
    compiler.check();
    final byte[] stateAfterChecks = getSavedCompilerState(compiler);

    compiler = new Compiler(new TestErrorManager());
    compiler.init(externs, srcs, options);
    restoreCompilerState(compiler, stateAfterChecks);

    // Stage 2, all passes
    compiler.performTranspilationAndOptimizations(SegmentOfCompilationToRun.OPTIMIZATIONS);
    String source = compiler.toSource();
    assertThat(source).isEqualTo(finalOutputAfterOptimizations); // test output stage 2 code

    // Now reset the compiler and test splitting stage 2 into two halves. We want to test that the
    // output is the same as when we run all of stage 2 in one go.
    compiler = new Compiler(new TestErrorManager());
    compiler.init(externs, srcs, options);

    // Stage 1
    compiler.parse();
    compiler.check();
    final byte[] stateAfterChecks2 = getSavedCompilerState(compiler);

    compiler = new Compiler(new TestErrorManager());
    compiler.init(externs, srcs, options);
    restoreCompilerState(compiler, stateAfterChecks2);

    // Stage 2, first half
    compiler.performTranspilationAndOptimizations(
        SegmentOfCompilationToRun.OPTIMIZATIONS_FIRST_HALF);
    source = compiler.toSource();
    assertThat(source).isEqualTo("console.log(function(){return\"hello\"}());");

    final byte[] stateAfterFirstHalfOptimizations = getSavedCompilerState(compiler);
    compiler = new Compiler(new TestErrorManager());
    compiler.init(externs, srcs, options);
    restoreCompilerState(compiler, stateAfterFirstHalfOptimizations);

    // Stage 2, second half
    compiler.performTranspilationAndOptimizations(
        SegmentOfCompilationToRun.OPTIMIZATIONS_SECOND_HALF);
    source = compiler.toSource();
    assertThat(source).isEqualTo(finalOutputAfterOptimizations); // output is the same as before
  }
}
