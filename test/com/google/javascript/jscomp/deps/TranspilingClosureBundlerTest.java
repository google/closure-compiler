/*
 * Copyright 2016 The Closure Compiler Authors.
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

import com.google.common.cache.CacheBuilder;
import com.google.javascript.jscomp.testing.BlackHoleErrorManager;

import junit.framework.TestCase;
import org.junit.Assert;

import java.io.IOException;

/** Tests for {@link TranspilingClosureBundler}. */
public final class TranspilingClosureBundlerTest extends TestCase {

  private static final JsFileParser PARSER = new JsFileParser(new BlackHoleErrorManager());
  private TranspilingClosureBundler bundler;

  @Override
  public void setUp() {
    bundler = new TranspilingClosureBundler("RUNTIME;\n");
  }

  public void testCodeWithES6FeaturesIsTranspiled() throws IOException {
    String input = "class Foo {}";
    DependencyInfo info = PARSER.parseFile("foo.js", "foo.js", input);
    StringBuilder sb = new StringBuilder();
    bundler.appendTo(sb, info, input);
    assertThat(removeSourceMap(sb.toString()))
        .isEqualTo("RUNTIME;\nvar Foo = function() {\n};\n");
  }

  public void testRuntimeInjectedOutsideBundle() throws IOException {
    String input = "goog.module('Foo');\nclass Foo {}";
    DependencyInfo info = PARSER.parseFile("foo.js", "foo.js", input);
    StringBuilder sb = new StringBuilder();
    bundler.appendTo(sb, info, input);
    assertThat(removeSourceMap(sb.toString()))
        .isEqualTo("RUNTIME;\n"
            + "goog.loadModule(function(exports) {'use strict';"
            + "goog.module(\"Foo\");\nvar Foo = function() {\n};\n\n"
            + ";return exports;});\n");
  }

  public void testCacheHit() throws IOException {
    String input = "class CacheHit {}";
    DependencyInfo info = PARSER.parseFile("foo.js", "foo.js", input);
    assertThat(bundler.cachedTranspilations.asMap()).isEmpty();

    StringBuilder sb = new StringBuilder();
    bundler.appendTo(sb, info, input);
    assertThat(removeSourceMap(sb.toString()))
        .isEqualTo("RUNTIME;\nvar CacheHit = function() {\n};\n");
    assertThat(bundler.cachedTranspilations.asMap()).hasSize(2);

    sb = new StringBuilder();
    bundler.appendTo(sb, info, input);
    // The ES6 runtime isn't bundled a second time.
    assertThat(removeSourceMap(sb.toString()))
        .isEqualTo("var CacheHit = function() {\n};\n");
    assertThat(bundler.cachedTranspilations.asMap()).hasSize(2);
  }

  public void testCacheMiss() throws IOException {
    String input = "class Foo {}";
    DependencyInfo info = PARSER.parseFile("foo.js", "foo.js", input);

    StringBuilder sb = new StringBuilder();
    bundler.appendTo(sb, info, input);
    assertThat(removeSourceMap(sb.toString()))
        .isEqualTo("RUNTIME;\nvar Foo = function() {\n};\n");
    assertThat(bundler.cachedTranspilations.asMap()).hasSize(2);

    input = "class Bar {}";
    sb = new StringBuilder();
    bundler.appendTo(sb, info, input);
    // The ES6 runtime isn't bundled a second time.
    assertThat(removeSourceMap(sb.toString()))
        .isEqualTo("var Bar = function() {\n};\n");
    assertThat(bundler.cachedTranspilations.asMap()).hasSize(3);
  }

  public void testError() throws IOException {
    String input = "const foo;";
    DependencyInfo info = PARSER.parseFile("foo.js", "foo.js", input);
    StringBuilder sb = new StringBuilder();
    try {
      bundler.appendTo(sb, info, input);
    } catch (IllegalStateException e) {
      assertThat(e.getMessage()).contains("Parse error. const variables must have an initializer");
      return;
    }
    Assert.fail();
  }
  
  public void testDisableSourceMap() throws IOException {
    bundler =
        new TranspilingClosureBundler(
            CacheBuilder.newBuilder().maximumSize(1).<Long, String>build(), false);
    String input = "class Foo {}";
    DependencyInfo info = PARSER.parseFile("foo.js", "foo.js", input);
    StringBuilder sb = new StringBuilder();
    bundler.appendTo(sb, info, input);
    assertThat(sb.toString()).doesNotContain("//# sourceMappingURL=");
  }
  
  public void testGetSourceMapReturnsAfterTranspile() throws IOException {
    bundler =
        new TranspilingClosureBundler(
            CacheBuilder.newBuilder().maximumSize(1).<Long, String>build(), false);
    bundler.withPath("foo.js");
    assertThat(bundler.getSourceMap("foo.js")).isNull();
    String input = "class Foo {}";
    DependencyInfo info = PARSER.parseFile("foo.js", "foo.js", input);
    StringBuilder sb = new StringBuilder();
    bundler.appendTo(sb, info, input);
    String sourceMap = bundler.getSourceMap("foo.js");
    assertThat(sourceMap).isNotNull();
    String input2 = "class Bar {}";
    info = PARSER.parseFile("foo.js", "foo.js", input2);
    bundler.appendTo(sb, info, input2);
    assertThat(bundler.getSourceMap("foo.js")).isNotEqualTo(sourceMap);
  }

  private static String removeSourceMap(String input) {
    return input.replaceAll("\n//# sourceMappingURL[^\n]+\n", "");
  }
}
