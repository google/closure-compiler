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

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.javascript.rhino.Node;

import junit.framework.TestCase;

/**
 * Tests for error message filtering.
 * @author nicksantos@google.com (Nick Santos)
 */
public class RhinoErrorReporterTest extends TestCase {

  public CompilerPass getProcessor(Compiler compiler) {
    return new CompilerPass() {
      public void process(Node externs, Node root) {}
    };
  }

  public void testTrailingComma() throws Exception {
    String message =
        "Parse error. Internet Explorer has a non-standard " +
        "intepretation of trailing commas. Arrays will have the wrong " +
        "length and objects will not parse at all.";
    assertError(
        "var x = [1,];",
        RhinoErrorReporter.TRAILING_COMMA,
        message);
    assertError(
        "var x = {1: 2,};",
        RhinoErrorReporter.TRAILING_COMMA,
        message);
  }


  /**
   * Verifies that the compiler emits an error for the given code.
   */
  private void assertError(
      String code, DiagnosticType type, String description) {
    Compiler compiler = new Compiler();
    JSSourceFile[] externs = new JSSourceFile[] {};
    JSSourceFile[] inputs =  new JSSourceFile[] {
      JSSourceFile.fromCode("input", code)
    };
    compiler.init(externs, inputs, new CompilerOptions());
    compiler.parseInputs();
    assertEquals("Expected error", 1, compiler.getErrorCount());

    JSError error =
        Iterables.getOnlyElement(Lists.newArrayList(compiler.getErrors()));
    assertEquals(type, error.getType());
    assertEquals(description, error.description);
  }
}
