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
import static com.google.common.truth.Truth.assertWithMessage;
import static org.mockito.Mockito.RETURNS_SMART_NULLS;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.bundle.TranspilationException;
import com.google.javascript.jscomp.transpile.BaseTranspiler;
import com.google.javascript.jscomp.transpile.TranspileResult;
import com.google.javascript.jscomp.transpile.Transpiler;
import java.io.IOException;
import java.net.URI;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/** Tests for ClosureBundler */
@RunWith(JUnit4.class)
public final class ClosureBundlerTest {

  private static final DependencyInfo MODULE =
      SimpleDependencyInfo.builder("", "").setGoogModule(true).build();

  private static final DependencyInfo TRADITIONAL =
      SimpleDependencyInfo.builder("", "").build();

  @Test
  public void testGoogModule() throws IOException {
    StringBuilder sb = new StringBuilder();
    new ClosureBundler().appendTo(sb, MODULE, "\"a string\"");
    assertThat(sb.toString())
        .isEqualTo("goog.loadModule(function(exports) {'use strict';"
            + "\"a string\"\n"
            + ";return exports;});\n");
  }

  @Test
  public void testGoogModuleWithSourceURL() throws IOException {
    StringBuilder sb = new StringBuilder();
    new ClosureBundler()
        .useEval(true)
        .withSourceUrl("URL")
        .appendTo(sb, MODULE, "\"a string\"");
    assertThat(sb.toString())
        .isEqualTo("goog.loadModule(\"\\x22a string\\x22\\n//# sourceURL\\x3dURL\\n\");\n");
  }

  @Test
  public void testGoogModuleWithEval() throws IOException {
    StringBuilder sb = new StringBuilder();
    new ClosureBundler()
        .useEval(true)
        .appendTo(sb, MODULE, "\"a string\"");
    assertThat(sb.toString()).isEqualTo("goog.loadModule(\"\\x22a string\\x22\");\n");
  }

  @Test
  public void testGoogModuleWithEvalWithURL() throws IOException {
    StringBuilder sb = new StringBuilder();
    new ClosureBundler()
        .useEval(true)
        .withSourceUrl("URL")
        .appendTo(sb, MODULE, "\"a string\"");
    assertThat(sb.toString())
        .isEqualTo("goog.loadModule(\"\\x22a string\\x22\\n//# sourceURL\\x3dURL\\n\");\n");
  }

  @Test
  public void testTraditional() throws IOException {
    StringBuilder sb = new StringBuilder();
    new ClosureBundler().appendTo(sb, TRADITIONAL, "\"a string\"");
    assertThat(sb.toString()).isEqualTo("\"a string\"");
  }

  @Test
  public void testTraditionalWithSourceURL() throws IOException {
    StringBuilder sb = new StringBuilder();
    new ClosureBundler()
        .withSourceUrl("URL")
        .appendTo(sb, TRADITIONAL, "\"a string\"");
    assertThat(sb.toString())
        .isEqualTo("\"a string\"\n"
            + "//# sourceURL=URL\n");
  }

  @Test
  public void testTraditionalWithEval() throws IOException {
    StringBuilder sb = new StringBuilder();
    new ClosureBundler()
        .useEval(true)
        .appendTo(sb, TRADITIONAL, "\"a string\"");
    assertThat(sb.toString()).isEqualTo("eval(\"\\x22a string\\x22\");\n");
  }

  @Test
  public void testTraditionalWithEvalWithSourceUrl() throws IOException {
    StringBuilder sb = new StringBuilder();
    new ClosureBundler()
        .useEval(true)
        .withSourceUrl("URL")
        .appendTo(sb, TRADITIONAL, "\"a string\"");
    assertThat(sb.toString())
        .isEqualTo("eval(\"\\x22a string\\x22\\n//# sourceURL\\x3dURL\\n\");\n");
  }

  @Test
  public void testTranspilation() throws Exception {
    String input = "goog.module('Foo');\nclass Foo {}";
    URI uri = new URI("foo.js");

    Transpiler transpiler = Mockito.mock(Transpiler.class, RETURNS_SMART_NULLS);
    when(transpiler.runtime()).thenReturn("RUNTIME;");
    when(transpiler.transpile(uri, input))
        .thenReturn(new TranspileResult(uri, input, "TRANSPILED;", ""));

    ClosureBundler bundler = new ClosureBundler(transpiler).withPath("foo.js");
    StringBuilder sb = new StringBuilder();
    bundler.appendRuntimeTo(sb);
    bundler.appendTo(sb, MODULE, input);
    assertThat(sb.toString()).startsWith("RUNTIME;");
    // Call endsWith because the ES6 module runtime is also injected.
    assertThat(sb.toString())
        .endsWith("goog.loadModule(function(exports) {'use strict';TRANSPILED;\n"
            + ";return exports;});\n");

    // Without calling appendRuntimeTo(), the runtime is not included anymore.
    sb = new StringBuilder();
    bundler.appendTo(sb, MODULE, input);
    assertThat(sb.toString())
        .isEqualTo("goog.loadModule(function(exports) {'use strict';TRANSPILED;\n"
            + ";return exports;});\n");
  }

  @Test
  public void testEs6Module() throws IOException {
    String input =
        "import {x} from './other.js';\n"
            + "export {x as y};"
            + "let local;\n"
            + "export function foo() { return local; }\n";
    ClosureBundler bundler =
        new ClosureBundler(BaseTranspiler.LATEST_TRANSPILER).withPath("nested/path/foo.js");
    StringBuilder sb = new StringBuilder();
    bundler.appendRuntimeTo(sb);
    bundler.appendTo(
        sb,
        SimpleDependencyInfo.builder("", "").setLoadFlags(ImmutableMap.of("module", "es6")).build(),
        input);
    String result = sb.toString();
    // ES6 module runtime should be injected.
    assertThat(result).contains("$jscomp.require = createRequire();");
    assertThat(result).startsWith("var $jscomp");
    assertThat(result)
        .endsWith(
            "$jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {\n"
                + "  \"use strict\";\n"
                + "  Object.defineProperties($$exports, {foo:{enumerable:true, get:function() {\n"
                + "    return foo;\n"
                + "  }}, y:{enumerable:true, get:function() {\n"
                + "    return module$nested$path$other.x;\n"
                + "  }}});\n"
                + "  var module$nested$path$other = $$require(\"nested/path/other.js\");\n"
                + "  let local;\n"
                + "  function foo() {\n"
                + "    return local;\n"
                + "  }\n"
                + "}, \"nested/path/foo.js\", [\"nested/path/other.js\"]);\n");
  }

  @Test
  public void testPassThroughIfNoTranspilationNeeded() throws Exception {
    String input = "/** Hello Comments! */ const s = 0;\n  let intended;";
    ClosureBundler bundler = new ClosureBundler(BaseTranspiler.LATEST_TRANSPILER);
    StringBuilder sb = new StringBuilder();
    bundler.appendTo(
        sb,
        SimpleDependencyInfo.builder("", "").build(),
        input);
    assertThat(sb.toString()).isEqualTo(input);
  }

  @Test
  public void testCommentsAndFormattingRemovedWithTranspilation() throws Exception {
    String input = "/** Hello Comments! */ const s = 0;\n  let intended;";
    ClosureBundler bundler = new ClosureBundler(BaseTranspiler.ES5_TRANSPILER);
    StringBuilder sb = new StringBuilder();
    bundler.appendTo(
        sb,
        SimpleDependencyInfo.builder("", "").build(),
        input);
    assertThat(sb.toString()).isEqualTo("var s = 0;\nvar intended;\n");
  }

  @Test
  public void testFullWidthLowLineWithDefaultTranspilerIsOkay() throws Exception {
    // The last character is something the compiler doesn't handle correctly
    String input = "var ａｅｓｔｈｅｔｉｃ＿";
    ClosureBundler bundler = new ClosureBundler();
    StringBuilder sb = new StringBuilder();
    bundler.appendTo(
        sb,
        SimpleDependencyInfo.builder("", "").build(),
        input);
    assertThat(sb.toString()).isEqualTo(input);
  }

  // TODO(johnplaisted): If / when the compiler can parse full width low line in identifiers
  // this should be okay to be transpiled.
  @Test
  public void testFullWidthLowLineInTranspiledCodeIsError() throws Exception {
    // The last character is something the compiler doesn't handle correctly
    String input = "let ａｅｓｔｈｅｔｉｃ＿";
    ClosureBundler bundler = new ClosureBundler(BaseTranspiler.ES5_TRANSPILER);
    StringBuilder sb = new StringBuilder();
    try {
      bundler.appendTo(sb, SimpleDependencyInfo.builder("", "").build(), input);
      assertWithMessage("Expected an exception").fail();
    } catch (TranspilationException e) {
      assertThat(e)
          .hasMessageThat()
          .contains("Parse error. Character '＿' (U+FF3F) is not a valid identifier start char");
    }
  }
}
