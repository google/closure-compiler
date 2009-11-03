/*
 * Copyright 2009 Google Inc.
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


import junit.framework.TestCase;


/**
 * Tests for {@link ExternExportsPass}.
 *
*
 */
public class ExternExportsPassTest extends TestCase {
  private static final JSSourceFile[] EXTERNS = {
      JSSourceFile.fromCode("externs", "")
  };

  public void testExportSymbol() throws Exception {
    compileAndCheck("var a = {}; a.b = {}; a.b.c = function(d, e, f) {};" +
                    "goog.exportSymbol('foobar', a.b.c)",
                    "var foobar = function(d, e, f) {};\n");
  }

  public void testExportProperty() throws Exception {
    compileAndCheck("var a = {}; a.b = {}; a.b.c = function(d, e, f) {};" +
                    "goog.exportProperty(a.b, 'cprop', a.b.c)",
                    "var a;\na.b;\na.b.cprop = function(d, e, f) {};\n");
  }

  public void testExportMultiple() throws Exception {
    compileAndCheck("var a = {}; a.b = function(p1) {}; " +
                    "a.b.c = function(d, e, f) {};" +
                    "a.b.prototype.c = function(g, h, i) {};" +
                    "goog.exportSymbol('a.b', a.b);" +
                    "goog.exportProperty(a.b, 'c', a.b.c);" +
                    "goog.exportProperty(a.b.prototype, 'c', a.b.prototype.c);",

                    "var a;\n" +
                    "a.b = function(p1) {};\n" +
                    "a.b.c = function(d, e, f) {};\n" +
                    "a.b.prototype;\n" +
                    "a.b.prototype.c = function(g, h, i) {};\n");
  }

  public void testExportMultiple2() throws Exception {
    compileAndCheck("var a = {}; a.b = function(p1) {}; " +
                    "a.b.c = function(d, e, f) {};" +
                    "a.b.prototype.c = function(g, h, i) {};" +
                    "goog.exportSymbol('hello', a);" +
                    "goog.exportProperty(a.b, 'c', a.b.c);" +
                    "goog.exportProperty(a.b.prototype, 'c', a.b.prototype.c);",

                    "var hello;\n" +
                    "hello.b;\n" +
                    "hello.b.c = function(d, e, f) {};\n" +
                    "hello.b.prototype;\n" +
                    "hello.b.prototype.c = function(g, h, i) {};\n");
  }


  public void testExportMultiple3() throws Exception {
    compileAndCheck("var a = {}; a.b = function(p1) {}; " +
                    "a.b.c = function(d, e, f) {};" +
                    "a.b.prototype.c = function(g, h, i) {};" +
                    "goog.exportSymbol('prefix', a.b);" +
                    "goog.exportProperty(a.b, 'c', a.b.c);",

                    "var prefix = function(p1) {};\n" +
                    "prefix.c = function(d, e, f) {};\n");
  }


  public void testExportNonStaticSymbol() throws Exception {
    compileAndCheck("var a = {}; a.b = {}; var d = {}; a.b.c = d;" +
                    "goog.exportSymbol('foobar', a.b.c)",
                    "var foobar;\n");
  }

  public void testExportNonStaticSymbol2() throws Exception {
    compileAndCheck("var a = {}; a.b = {}; var d = null; a.b.c = d;" +
                    "goog.exportSymbol('foobar', a.b.c())",
                    "var foobar;\n");
  }

  public void testExportNonexistantProperty() throws Exception {
    compileAndCheck("var a = {}; a.b = {}; a.b.c = function(d, e, f) {};" +
                    "goog.exportProperty(a.b, 'none', a.b.none)",
                    "var a;\n" +
                    "a.b;\n" +
                    "a.b.none;\n");
  }


  private void compileAndCheck(String js, String expected) {
    System.err.println(compile(js));
    assertEquals(expected, compile(js));
  }

  private String compile(String js) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.externExportsPath = "externs.js";

    // Turn on IDE mode to get rid of optimizations.
    options.ideMode = true;

    JSSourceFile[] inputs = {
      JSSourceFile.fromCode("testcode",
                            "var goog = {};" +
                            "goog.exportSymbol = function(a, b) {}; " +
                            "goog.exportProperty = function(a, b, c) {}; " +
                            js)
    };

    Result result = compiler.compile(EXTERNS, inputs, options);

    assertTrue(result.success);

    return result.externExport;
  }
}
