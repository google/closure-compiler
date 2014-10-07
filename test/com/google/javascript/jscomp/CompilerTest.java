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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.debugging.sourcemap.FilePosition;
import com.google.debugging.sourcemap.SourceMapGeneratorV3;
import com.google.debugging.sourcemap.proto.Mapping.OriginalMapping;
import com.google.javascript.rhino.Node;

import junit.framework.TestCase;

import java.io.File;
import java.util.List;

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

    assertTrue(cb.toString().isEmpty());
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
    options.ideMode = true;
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
    options.ideMode = true;
    options.setManageClosureDependencies(entryPoints);
    options.closurePass = true;
    options.processCommonJSModules = true;
    Compiler compiler = new Compiler();
    compiler.init(Lists.<SourceFile>newArrayList(), inputs, options);
    compiler.parseInputs();
    return compiler;
  }


}
