/*
 * Copyright 2012 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.integration;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.BlackHoleErrorManager;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.DiagnosticGroup;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.testing.JSChunkGraphBuilder;
import com.google.javascript.jscomp.testing.JSCompCorrespondences;
import com.google.javascript.jscomp.testing.NoninjectingCompiler;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;

/** Framework for end-to-end test cases. */
abstract class IntegrationTestCase {
  protected static final Joiner LINE_JOINER = Joiner.on('\n');
  protected static final Joiner EMPTY_JOINER = Joiner.on("");

  protected static String lines(String line) {
    return line;
  }

  protected static String lines(String... lines) {
    return LINE_JOINER.join(lines);
  }

  /** Externs for the test */
  protected static final ImmutableList<SourceFile> DEFAULT_EXTERNS =
      ImmutableList.of(
          new TestExternsBuilder()
              .addArguments()
              .addString()
              .addObject()
              .addReflect()
              .addFunction()
              .addIterable()
              .addPromise()
              .addArray()
              .addAlert()
              .addExtra(
                  lines(
                      "/**",
                      " * @const",
                      " */",
                      "var Math = {};",
                      "/**",
                      " * @param {?} n1",
                      " * @param {?} n2",
                      " * @return {number}",
                      " * @nosideeffects",
                      " */",
                      "Math.pow = function(n1, n2) {};",
                      "var isNaN;",
                      "var Infinity;",
                      "/**",
                      " * @constructor",
                      " * @extends {Array<string>}",
                      " */",
                      "var ITemplateArray = function() {};",
                      "/** @constructor */",
                      "var Map;",
                      "/** @constructor */",
                      "var Set;",
                      "/** @constructor */ function Window() {}",
                      "/** @type {string} */ Window.prototype.name;",
                      "/** @type {string} */ Window.prototype.offsetWidth;",
                      "/** @type {Window} */ var window;",
                      "",
                      "/** @nosideeffects */ function noSideEffects() {}",
                      "",
                      "/**",
                      " * @constructor",
                      " * @nosideeffects",
                      " */",
                      "function Widget() {}",
                      "/** @modifies {this} */ Widget.prototype.go = function() {};",
                      "/** @return {string} */ var widgetToken = function() {};",
                      "",
                      "/**",
                      " * @constructor",
                      " * @return {number}",
                      " * @param {*=} opt_n",
                      " */",
                      "function Number(opt_n) {}",
                      "",
                      "/**",
                      " * @constructor",
                      " * @return {boolean}",
                      " * @param {*=} opt_b",
                      " */",
                      "function Boolean(opt_b) {}",
                      "",
                      "/**",
                      " * @constructor",
                      " * @return {!TypeError}",
                      " * @param {*=} opt_message",
                      " * @param {*=} opt_file",
                      " * @param {*=} opt_line",
                      " */",
                      "function TypeError(opt_message, opt_file, opt_line) {}",
                      "/**",
                      " * @constructor",
                      " * @param {*=} opt_message",
                      " * @param {*=} opt_file",
                      " * @param {*=} opt_line",
                      " * @return {!Error}",
                      " * @nosideeffects",
                      " */",
                      "function Error(opt_message, opt_file, opt_line) {}",
                      "",
                      "/** @constructor */",
                      "var HTMLElement = function() {};",
                      ""))
              .buildExternsFile("externs"));

  protected List<SourceFile> externs = DEFAULT_EXTERNS;

  // The most recently used compiler.
  protected Compiler lastCompiler;

  protected boolean useNoninjectingCompiler = false;

  protected String inputFileNamePrefix;
  protected String inputFileNameSuffix;

  @Before
  public void setUp() {
    externs = DEFAULT_EXTERNS;
    lastCompiler = null;
    useNoninjectingCompiler = false;
    inputFileNamePrefix = "i";
    inputFileNameSuffix = ".js";
  }

  protected void testSame(CompilerOptions options, String original) {
    testSame(options, new String[] { original });
  }

  protected void testSame(CompilerOptions options, String[] original) {
    test(options, original, original);
  }

  /**
   * Asserts that when compiling with the given compiler options,
   * {@code original} is transformed into {@code compiled}.
   */
  protected void test(CompilerOptions options,
      String original, String compiled) {
    test(options, new String[] { original }, new String[] { compiled });
  }

  /**
   * Asserts that when compiling with the given compiler options,
   * {@code original} is transformed into {@code compiled}.
   */
  protected void test(CompilerOptions options,
      String[] original, String[] compiled) {
    Compiler compiler = compile(options, original);

    Node root = compiler.getRoot().getLastChild();

    // Verify that there are no unexpected errors before checking the compiled output
    assertWithMessage(
            "Expected no warnings or errors\n"
                + "Errors: \n"
                + Joiner.on("\n").join(compiler.getErrors())
                + "\n"
                + "Warnings: \n"
                + Joiner.on("\n").join(compiler.getWarnings()))
        .that(compiler.getErrors().size() + compiler.getWarnings().size())
        .isEqualTo(0);

    if (compiled != null) {
      Node expectedRoot = parseExpectedCode(compiled, options);
      assertNode(root).usingSerializer(compiler::toSource).isEqualTo(expectedRoot);
    }
  }

  /** Asserts that when compiling with the given compiler options, there is an error or warning. */
  protected void test(CompilerOptions options, String original, DiagnosticGroup warning) {
    test(options, new String[] {original}, null, warning);
  }

  /** Asserts that when compiling with the given compiler options, there is an error or warning. */
  protected void test(
      CompilerOptions options, String original, String compiled, DiagnosticGroup warning) {
    test(options, new String[] {original}, new String[] {compiled}, warning);
  }

  /** Asserts that when compiling with the given compiler options, there is an error or warning. */
  protected void test(CompilerOptions options, String[] original, DiagnosticGroup warning) {
    test(options, original, null, warning);
  }

  /** Asserts that when compiling with the given compiler options, there is an error or warning. */
  protected void test(
      CompilerOptions options, String[] original, String[] compiled, DiagnosticGroup[] warnings) {
    Compiler compiler = compile(options, original);
    checkUnexpectedErrorsOrWarnings(compiler, warnings.length);

    if (compiled != null) {
      Node root = compiler.getRoot().getLastChild();
      Node expectedRoot = parseExpectedCode(compiled, options);
      assertNode(root).usingSerializer(compiler::toSource).isEqualTo(expectedRoot);
    }

    assertThat(
            ImmutableList.builder()
                .addAll(compiler.getErrors())
                .addAll(compiler.getWarnings())
                .build())
        .comparingElementsUsing(JSCompCorrespondences.OWNING_DIAGNOSTIC_GROUP)
        .containsExactly(warnings);
  }

  /** Asserts that when compiling with the given compiler options, there is an error or warning. */
  protected void test(
      CompilerOptions options, String[] original, String[] compiled, DiagnosticGroup warnings) {
    Compiler compiler = compile(options, original);
    checkUnexpectedErrorsOrWarnings(compiler, 1);

    if (compiled != null) {
      Node root = compiler.getRoot().getLastChild();
      Node expectedRoot = parseExpectedCode(compiled, options);
      assertNode(root).usingSerializer(compiler::toSource).isEqualTo(expectedRoot);
    }

    DiagnosticType diagnostic;
    if (compiler.getErrorCount() == 1) {
      diagnostic = compiler.getErrors().get(0).getType();
    } else {
      diagnostic = compiler.getWarnings().get(0).getType();
    }
    assertWithMessage(
            "Error not in expected diagnostic group. Error: "
                + diagnostic.key
                + "\nExpected group: "
                + warnings)
        .that(warnings.matches(diagnostic))
        .isTrue();
  }

  /** Asserts that there is at least one parse error. */
  protected void testParseError(CompilerOptions options, String original) {
    testParseError(options, original, null);
  }

  /**
   * Asserts that there is at least one parse error.
   */
  protected void testParseError(CompilerOptions options,
      String original, String compiled) {
    Compiler compiler = compile(options, original);
    for (JSError error : compiler.getErrors()) {
      if (!DiagnosticGroups.PARSING.matches(error)) {
        assertWithMessage("Found unexpected error type " + error.getType() + ":\n" + error).fail();
      }
    }
    assertWithMessage("Unexpected warnings: " + Joiner.on("\n").join(compiler.getWarnings()))
        .that(compiler.getWarnings().size())
        .isEqualTo(0);

    if (compiled != null) {
      Node root = compiler.getRoot().getLastChild();
      Node expectedRoot = parseExpectedCode(new String[] {compiled}, options);
      assertNode(root).usingSerializer(compiler::toSource).isEqualTo(expectedRoot);
    }
  }

  protected void checkUnexpectedErrorsOrWarnings(
      Compiler compiler, int expected) {
    int actual = compiler.getErrors().size() + compiler.getWarnings().size();
    if (actual != expected) {
      String msg = "";
      for (JSError err : compiler.getErrors()) {
        msg += "Error:" + err + "\n";
      }
      for (JSError err : compiler.getWarnings()) {
        msg += "Warning:" + err + "\n";
      }
      assertWithMessage("Unexpected warnings or errors.\n " + msg).that(actual).isEqualTo(expected);
    }
  }

  protected Compiler compile(CompilerOptions options, String original) {
    return compile(options, new String[] { original });
  }

  protected Compiler compile(CompilerOptions options, String[] original) {
    Compiler compiler =
        useNoninjectingCompiler
            ? new NoninjectingCompiler(new BlackHoleErrorManager())
            : new Compiler(new BlackHoleErrorManager());

    lastCompiler = compiler;
    compiler.compileModules(
        externs,
        ImmutableList.copyOf(
            JSChunkGraphBuilder.forChain()
                .addChunks(ImmutableList.copyOf(original))
                .setFilenameFormat(inputFileNamePrefix + "%s" + inputFileNameSuffix)
                .build()),
        options);
    return compiler;
  }

  protected void testNoWarnings(CompilerOptions options, String code) {
    testNoWarnings(options, new String[] { code });
  }

  protected void testNoWarnings(CompilerOptions options, String[] sources) {
    Compiler compiler = compile(options, sources);
    assertThat(compiler.getErrors()).isEmpty();
    assertThat(compiler.getWarnings()).isEmpty();
  }

  /**
   * Parse the expected code to compare against. We want to run this with similar parsing options,
   * but don't want to run the commonjs preprocessing passes (so that we can use this to test the
   * commonjs code).
   */
  protected Node parseExpectedCode(String[] original, CompilerOptions options) {
    boolean oldProcessCommonJsModules = options.getProcessCommonJSModules();
    options.setProcessCommonJSModules(false);
    Node expectedRoot = parse(original, options);
    options.setProcessCommonJSModules(oldProcessCommonJsModules);
    return expectedRoot;
  }

  protected Node parse(String[] original, CompilerOptions options) {
    Compiler compiler = new Compiler();
    List<SourceFile> inputs = new ArrayList<>();
    for (int i = 0; i < original.length; i++) {
      inputs.add(SourceFile.fromCode(inputFileNamePrefix + i + inputFileNameSuffix, original[i]));
    }
    compiler.init(externs, inputs, options);
    checkUnexpectedErrorsOrWarnings(compiler, 0);
    compiler.parse();
    checkUnexpectedErrorsOrWarnings(compiler, 0);

    return compiler.getRoot().getLastChild();
  }

  /** Creates a CompilerOptions object with google coding conventions. */
  protected abstract CompilerOptions createCompilerOptions();
}
