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

import junit.framework.TestCase;

import java.io.IOException;

/**
 * Tests for ClosureBundler
 */
public class ClosureBundlerTest extends TestCase {

  private static final DependencyInfo MODULE = new SimpleDependencyInfo(
      null, null, null, null, true);

  private static final DependencyInfo TRADITIONAL = new SimpleDependencyInfo(
      null, null, null, null, false);

  public void testGoogModule() throws IOException {
    StringBuilder sb = new StringBuilder();
    new ClosureBundler().appendTo(sb, MODULE, "\"a string\"");
    assertEquals(
        "goog.loadModule(function(exports) {'use strict';"
        + "\"a string\"\n"
        + ";return exports;});\n",
        sb.toString());
  }

  public void testGoogModuleWithSourceURL() throws IOException {
    StringBuilder sb = new StringBuilder();
    new ClosureBundler()
        .useEval(true)
        .withSourceUrl("URL")
        .appendTo(sb, MODULE, "\"a string\"");
    assertEquals(
        "goog.loadModule(\"\\x22a string\\x22\\n//# sourceURL\\x3dURL\\n\");",
        sb.toString());
  }

  public void testGoogModuleWithEval() throws IOException {
    StringBuilder sb = new StringBuilder();
    new ClosureBundler()
        .useEval(true)
        .appendTo(sb, MODULE, "\"a string\"");
    assertEquals("goog.loadModule(\"\\x22a string\\x22\");", sb.toString());
  }

  public void testGoogModuleWithEvalWithURL() throws IOException {
    StringBuilder sb = new StringBuilder();
    new ClosureBundler()
        .useEval(true)
        .withSourceUrl("URL")
        .appendTo(sb, MODULE, "\"a string\"");
    assertEquals(
        "goog.loadModule(\"\\x22a string\\x22\\n//# sourceURL\\x3dURL\\n\");",
        sb.toString());
  }

  public void testTraditional() throws IOException {
    StringBuilder sb = new StringBuilder();
    new ClosureBundler().appendTo(sb, TRADITIONAL, "\"a string\"");
    assertEquals("\"a string\"", sb.toString());
  }

  public void testTraditionalWithSourceURL() throws IOException {
    StringBuilder sb = new StringBuilder();
    new ClosureBundler()
        .withSourceUrl("URL")
        .appendTo(sb, TRADITIONAL, "\"a string\"");
    assertEquals(
        "\"a string\"\n" +
        "//# sourceURL=URL\n",
        sb.toString());
  }

  public void testTraditionalWithEval() throws IOException {
    StringBuilder sb = new StringBuilder();
    new ClosureBundler()
        .useEval(true)
        .appendTo(sb, TRADITIONAL, "\"a string\"");
    assertEquals(
        "(0,eval(\"\\x22a string\\x22\"));",
        sb.toString());
  }

  public void testTraditionalWithEvalWithSourceUrl() throws IOException {
    StringBuilder sb = new StringBuilder();
    new ClosureBundler()
        .useEval(true)
        .withSourceUrl("URL")
        .appendTo(sb, TRADITIONAL, "\"a string\"");
    assertEquals(
        "(0,eval(\"\\x22a string\\x22\\n//# sourceURL\\x3dURL\\n\"));",
        sb.toString());
  }
}
