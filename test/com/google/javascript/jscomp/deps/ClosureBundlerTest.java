/*
 * Copyright 2014 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.deps;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.RETURNS_SMART_NULLS;
import static org.mockito.Mockito.when;

import com.google.javascript.jscomp.transpile.TranspileResult;
import com.google.javascript.jscomp.transpile.Transpiler;
import java.io.IOException;
import java.nio.file.Paths;
import junit.framework.TestCase;
import org.mockito.Mockito;

/**
 * Tests for ClosureBundler
 */
public final class ClosureBundlerTest extends TestCase {

  private static final DependencyInfo MODULE =
      SimpleDependencyInfo.builder("", "").setGoogModule(true).build();

  private static final DependencyInfo TRADITIONAL =
      SimpleDependencyInfo.builder("", "").build();

  public void testGoogModule() throws IOException {
    StringBuilder sb = new StringBuilder();
    new ClosureBundler().appendTo(sb, MODULE, "\"a string\"");
    assertThat(sb.toString())
        .isEqualTo("goog.loadModule(function(exports) {'use strict';"
            + "\"a string\"\n"
            + ";return exports;});\n");
  }

  public void testGoogModuleWithSourceURL() throws IOException {
    StringBuilder sb = new StringBuilder();
    new ClosureBundler()
        .useEval(true)
        .withSourceUrl("URL")
        .appendTo(sb, MODULE, "\"a string\"");
    assertThat(sb.toString())
        .isEqualTo("goog.loadModule(\"\\x22a string\\x22\\n//# sourceURL\\x3dURL\\n\");\n");
  }

  public void testGoogModuleWithEval() throws IOException {
    StringBuilder sb = new StringBuilder();
    new ClosureBundler()
        .useEval(true)
        .appendTo(sb, MODULE, "\"a string\"");
    assertThat(sb.toString()).isEqualTo("goog.loadModule(\"\\x22a string\\x22\");\n");
  }

  public void testGoogModuleWithEvalWithURL() throws IOException {
    StringBuilder sb = new StringBuilder();
    new ClosureBundler()
        .useEval(true)
        .withSourceUrl("URL")
        .appendTo(sb, MODULE, "\"a string\"");
    assertThat(sb.toString())
        .isEqualTo("goog.loadModule(\"\\x22a string\\x22\\n//# sourceURL\\x3dURL\\n\");\n");
  }

  public void testTraditional() throws IOException {
    StringBuilder sb = new StringBuilder();
    new ClosureBundler().appendTo(sb, TRADITIONAL, "\"a string\"");
    assertThat(sb.toString()).isEqualTo("\"a string\"");
  }

  public void testTraditionalWithSourceURL() throws IOException {
    StringBuilder sb = new StringBuilder();
    new ClosureBundler()
        .withSourceUrl("URL")
        .appendTo(sb, TRADITIONAL, "\"a string\"");
    assertThat(sb.toString())
        .isEqualTo("\"a string\"\n"
            + "//# sourceURL=URL\n");
  }

  public void testTraditionalWithEval() throws IOException {
    StringBuilder sb = new StringBuilder();
    new ClosureBundler()
        .useEval(true)
        .appendTo(sb, TRADITIONAL, "\"a string\"");
    assertThat(sb.toString()).isEqualTo("(0,eval(\"\\x22a string\\x22\"));\n");
  }

  public void testTraditionalWithEvalWithSourceUrl() throws IOException {
    StringBuilder sb = new StringBuilder();
    new ClosureBundler()
        .useEval(true)
        .withSourceUrl("URL")
        .appendTo(sb, TRADITIONAL, "\"a string\"");
    assertThat(sb.toString())
        .isEqualTo("(0,eval(\"\\x22a string\\x22\\n//# sourceURL\\x3dURL\\n\"));\n");
  }

  public void testTranspilation() throws IOException {
    String input = "goog.module('Foo');\nclass Foo {}";

    Transpiler transpiler = Mockito.mock(Transpiler.class, RETURNS_SMART_NULLS);
    when(transpiler.runtime()).thenReturn("RUNTIME;");
    when(transpiler.transpile(Paths.get("foo.js"), input))
        .thenReturn(new TranspileResult(Paths.get("foo.js"), input, "TRANSPILED;", ""));

    ClosureBundler bundler = new ClosureBundler(transpiler).withPath("foo.js");
    StringBuilder sb = new StringBuilder();
    bundler.appendRuntimeTo(sb);
    bundler.appendTo(sb, MODULE, input);
    assertThat(sb.toString())
        .isEqualTo("RUNTIME;goog.loadModule(function(exports) {'use strict';TRANSPILED;\n"
            + ";return exports;});\n");

    // Without calling appendRuntimeTo(), the runtime is not included anymore.
    sb = new StringBuilder();
    bundler.appendTo(sb, MODULE, input);
    assertThat(sb.toString())
        .isEqualTo("goog.loadModule(function(exports) {'use strict';TRANSPILED;\n"
            + ";return exports;});\n");
  }
}
