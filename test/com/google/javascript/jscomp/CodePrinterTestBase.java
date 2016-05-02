/*
 * Copyright 2015 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import junit.framework.TestCase;

/** Base class for tests that exercise {@link CodePrinter}. */
public abstract class CodePrinterTestBase extends TestCase {
  // If this is set, ignore parse warnings and only fail the test
  // for parse errors.
  protected boolean allowWarnings = false;
  protected boolean trustedStrings = true;
  protected boolean preserveTypeAnnotations = false;
  protected LanguageMode languageMode = LanguageMode.ECMASCRIPT5;
  protected Compiler lastCompiler = null;

  @Override public void setUp() {
    allowWarnings = false;
    preserveTypeAnnotations = false;
    trustedStrings = true;
    lastCompiler = null;
    languageMode = LanguageMode.ECMASCRIPT5;
  }

  Node parse(String js) {
    return parse(js, false);
  }

  Node parse(String js, boolean checkTypes) {
    Compiler compiler = new Compiler();
    lastCompiler = compiler;
    CompilerOptions options = new CompilerOptions();
    options.setTrustedStrings(trustedStrings);
    options.preserveTypeAnnotations = preserveTypeAnnotations;

    // Allow getters and setters.
    options.setLanguageIn(languageMode);
    compiler.initOptions(options);
    Node n = compiler.parseTestCode(js);

    if (checkTypes) {
      DefaultPassConfig passConfig = new DefaultPassConfig(null);
      CompilerPass typeResolver = passConfig.resolveTypes.create(compiler);
      Node externs = new Node(Token.SCRIPT);
      externs.setInputId(new InputId("externs"));
      Node externAndJsRoot = new Node(Token.BLOCK, externs, n);
      externAndJsRoot.setIsSyntheticBlock(true);
      typeResolver.process(externs, n);
      CompilerPass inferTypes = passConfig.inferTypes.create(compiler);
      inferTypes.process(externs, n);
    }

    checkUnexpectedErrorsOrWarnings(compiler, 0);
    return n;
  }

  private void checkUnexpectedErrorsOrWarnings(
      Compiler compiler, int expected) {
    int actual = compiler.getErrors().length;
    if (!allowWarnings) {
      actual += compiler.getWarnings().length;
    }

    if (actual != expected) {
      String msg = "";
      for (JSError err : compiler.getErrors()) {
        msg += "Error:" + err + "\n";
      }
      if (!allowWarnings) {
        for (JSError err : compiler.getWarnings()) {
          msg += "Warning:" + err + "\n";
        }
      }
      assertEquals("Unexpected warnings or errors.\n " + msg, expected, actual);
    }
  }

  String parsePrint(String js, CompilerOptions options) {
    return new CodePrinter.Builder(parse(js)).setCompilerOptions(options).build();
  }

  abstract static class CompilerOptionBuilder {
    abstract void setOptions(CompilerOptions options);
  }

  CompilerOptions newCompilerOptions(CompilerOptionBuilder builder) {
    CompilerOptions options = new CompilerOptions();
    options.setTrustedStrings(trustedStrings);
    options.preserveTypeAnnotations = preserveTypeAnnotations;
    options.setLanguageOut(languageMode);
    builder.setOptions(options);
    return options;
  }

  String printNode(Node n) {
    CompilerOptions options = new CompilerOptions();
    options.setLineLengthThreshold(CompilerOptions.DEFAULT_LINE_LENGTH_THRESHOLD);
    options.setLanguageOut(languageMode);
    return new CodePrinter.Builder(n).setCompilerOptions(options).build();
  }

  void assertPrintNode(String expectedJs, Node ast) {
    assertEquals(expectedJs, printNode(ast));
  }

  protected void assertPrint(String js, String expected) {
    parse(expected); // validate the expected string is valid JS
    assertEquals(expected,
        parsePrint(js, newCompilerOptions(new CompilerOptionBuilder() {
          @Override void setOptions(CompilerOptions options) {
            options.setPrettyPrint(false);
            options.setLineLengthThreshold(CompilerOptions.DEFAULT_LINE_LENGTH_THRESHOLD);
          }
        })));
  }

  protected void assertPrintSame(String js) {
    assertPrint(js, js);
  }
}
