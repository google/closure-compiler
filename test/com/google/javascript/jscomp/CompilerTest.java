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

import java.util.List;

import com.google.common.collect.Lists;
import com.google.javascript.jscomp.deps.SortedDependencies.CircularDependencyException;
import com.google.javascript.jscomp.deps.SortedDependencies.MissingProvideException;
import com.google.javascript.rhino.Node;

import junit.framework.TestCase;

/**
 * @author johnlenz@google.com (John Lenz)
 */
public class CompilerTest extends TestCase {

  public void testCodeBuilderColumnAfterResetDummy() {
    Compiler compiler = new Compiler();
    Node n = compiler.parseTestCode("");
    Compiler.CodeBuilder cb = new Compiler.CodeBuilder();
  }

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
    JSSourceFile[] inputs = {
        JSSourceFile.fromCode(
            "gin", "goog.provide('gin'); goog.require('tonic'); var gin = {};"),
        JSSourceFile.fromCode("tonic",
            "goog.provide('tonic'); goog.require('gin'); var tonic = {};"),
        JSSourceFile.fromCode(
            "mix", "goog.require('gin'); goog.require('tonic');")};
    CompilerOptions options = new CompilerOptions();
    options.ideMode = true;
    options.setManageClosureDependencies(true);
    Compiler compiler = new Compiler();
    compiler.init(new JSSourceFile[0], inputs, options);
    compiler.parseInputs();
    assertEquals(compiler.externAndJsRoot, compiler.jsRoot.getParent());
    assertEquals(compiler.externAndJsRoot, compiler.externsRoot.getParent());
    assertNotNull(compiler.externAndJsRoot);

    Node jsRoot = compiler.jsRoot;
    assertEquals(3, jsRoot.getChildCount());
  }

  public void testLocalUndefined() throws Exception {
    // Some javascript libraries like to create a local instance of "undefined",
    // to ensure that other libraries don't try to overwrite it.
    //
    // Most of the time, this is ok, because normalization will rename
    // that variable to undefined$$1. But this won't happen if they don't
    // include the default externs.
    //
    // This test is just to make sure that the compiler doesn't crash.
    CompilerOptions options = new CompilerOptions();
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(
        options);
    Compiler compiler = new Compiler();
    JSSourceFile externs = JSSourceFile.fromCode("externs.js", "");
    JSSourceFile input = JSSourceFile.fromCode("input.js",
        "(function (undefined) { alert(undefined); })();");
    compiler.compile(externs, input, options);
  }

  public void testCommonJSProvidesAndRequire() throws
      CircularDependencyException, MissingProvideException {
    JSSourceFile[] inputs = {
        JSSourceFile.fromCode("gin.js", "require('tonic')"),
        JSSourceFile.fromCode("tonic.js", ""),
        JSSourceFile.fromCode("mix.js", "require('gin'); require('tonic');")};
    CompilerOptions options = new CompilerOptions();
    options.ideMode = true;
    List<String> entryPoints = Lists.newArrayList("module$mix");
    options.setManageClosureDependencies(entryPoints);
    options.closurePass = true;
    options.processCommonJSModules = true;
    Compiler compiler = new Compiler();
    compiler.init(new JSSourceFile[0], inputs, options);
    compiler.parseInputs();
    JSModuleGraph graph = compiler.getModuleGraph();
    assertEquals(graph.getModuleCount(), 3);
    List<CompilerInput> result = graph.manageDependencies(entryPoints,
        compiler.getInputsForTesting());
    assertEquals("tonic.js", result.get(0).getName());
    assertEquals("gin.js", result.get(1).getName());
    assertEquals("mix.js", result.get(2).getName());
  }
}
