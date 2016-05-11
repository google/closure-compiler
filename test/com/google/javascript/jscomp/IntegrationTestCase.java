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

package com.google.javascript.jscomp;

import static com.google.javascript.jscomp.testing.JSErrorSubject.assertError;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.testing.BlackHoleErrorManager;
import com.google.javascript.rhino.Node;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * Framework for end-to-end test cases.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
abstract class IntegrationTestCase extends TestCase {
  protected static final Joiner LINE_JOINER = Joiner.on('\n');

  /** Externs for the test */
  protected static final List<SourceFile> DEFAULT_EXTERNS =
      ImmutableList.of(SourceFile.fromCode("externs", LINE_JOINER.join(
          "var arguments;",
          "var undefined;",
          "var Math;",
          "var isNaN;",
          "var Infinity;",
          "/** @interface */",
          "var Iterator;",
          "/** @interface */",
          "var Iterable;",
          "/** @interface @extends {Iterator} @extends {Iterable} */",
          "var IteratorIterable;",
          "/** @interface */",
          "function IArrayLike() {};",
          "/** @constructor */",
          "var Map;",
          "",
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
          "function alert(message) {}",
          "",
          "/**",
          " * @constructor",
          " * @implements {IArrayLike}",
          " * @return {!Array}",
          " * @param {...*} var_args",
          " */",
          "function Array(var_args) {}",
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
          " * @return {string}",
          " * @param {*=} opt_s",
          " */",
          "function String(opt_s) {}",
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
          "",
          "/**",
          " * @constructor",
          " * @param {*=} opt_value",
          " * @return {!Object}",
          " */",
          "function Object(opt_value) {}",
          "Object.seal;",
          "Object.defineProperties;",
          "/** @type {!Function} */",
          "Object.prototype.constructor;",
          "",
          "/** @typedef {?} */",
          "var symbol;",
          "",
          "/**",
          " * @param {string} s",
          " * @return {symbol}",
          " */",
          "function Symbol(s) {}",
          "",
          "/**",
          " * @param {...*} var_args",
          " * @constructor",
          " */",
          "function Function(var_args) {}",
          "/** @param {...*} var_args */",
          "Function.prototype.call = function (var_args) {};",
          "",
          "/** @constructor */",
          "function Arguments() {}")));

  protected List<SourceFile> externs = DEFAULT_EXTERNS;

  // The most recently used compiler.
  protected Compiler lastCompiler;

  protected boolean normalizeResults = false;

  @Override
  public void setUp() {
    externs = DEFAULT_EXTERNS;
    lastCompiler = null;
    normalizeResults = false;
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
    assertEquals("Expected no warnings or errors\n" +
        "Errors: \n" + Joiner.on("\n").join(compiler.getErrors()) + "\n" +
        "Warnings: \n" + Joiner.on("\n").join(compiler.getWarnings()),
        0, compiler.getErrors().length + compiler.getWarnings().length);

    Node root = compiler.getRoot().getLastChild();
    Node expectedRoot = parseExpectedCode(compiled, options, normalizeResults);
    String explanation = expectedRoot.checkTreeEquals(root);
    assertNull("\n"
        + "Expected: " + compiler.toSource(expectedRoot) + "\n"
        + "Result:   " + compiler.toSource(root) + "\n"
        + explanation,
        explanation);
  }

  /**
   * Asserts that when compiling with the given compiler options,
   * there is an error or warning.
   */
  protected void test(CompilerOptions options,
      String original, DiagnosticType warning) {
    test(options, new String[] { original }, warning);
  }

  protected void test(CompilerOptions options,
      String original, String compiled, DiagnosticType warning) {
    test(options, new String[] { original }, new String[] { compiled },
         warning);
  }

  protected void test(CompilerOptions options,
      String[] original, DiagnosticType warning) {
    test(options, original, null, warning);
  }

  /**
   * Asserts that when compiling with the given compiler options,
   * there is an error or warning.
   */
  protected void test(CompilerOptions options,
      String[] original, String[] compiled, DiagnosticType warning) {
    Compiler compiler = compile(options, original);
    checkUnexpectedErrorsOrWarnings(compiler, 1);
    assertEquals("Expected exactly one warning or error",
        1, compiler.getErrors().length + compiler.getWarnings().length);
    if (compiler.getErrors().length > 0) {
      assertError(compiler.getErrors()[0]).hasType(warning);
    } else {
      assertError(compiler.getWarnings()[0]).hasType(warning);
    }

    if (compiled != null) {
      Node root = compiler.getRoot().getLastChild();
      Node expectedRoot = parseExpectedCode(compiled, options, normalizeResults);
      String explanation = expectedRoot.checkTreeEquals(root);
      assertNull("\nExpected: " + compiler.toSource(expectedRoot) +
          "\nResult: " + compiler.toSource(root) +
          "\n" + explanation, explanation);
    }
  }

  /**
   * Asserts that there is at least one parse error.
   */
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
      if (!error.getType().equals(RhinoErrorReporter.PARSE_ERROR)) {
        fail("Found unexpected error type " + error.getType() + ":\n" + error);
      }
    }
    assertEquals("Unexpected warnings: " +
        Joiner.on("\n").join(compiler.getWarnings()),
        0, compiler.getWarnings().length);

    if (compiled != null) {
      Node root = compiler.getRoot().getLastChild();
      Node expectedRoot = parseExpectedCode(
          new String[] {compiled}, options, normalizeResults);
      String explanation = expectedRoot.checkTreeEquals(root);
      assertNull("\nExpected: " + compiler.toSource(expectedRoot) +
          "\nResult: " + compiler.toSource(root) +
          "\n" + explanation, explanation);
    }
  }

  /**
   * Asserts that when compiling with the given compiler options,
   * there is an error or warning.
   */
  protected void test(CompilerOptions options,
      String[] original, String[] compiled, DiagnosticType[] warnings) {
    Compiler compiler = compile(options, original);
    checkUnexpectedErrorsOrWarnings(compiler, warnings.length);

    if (compiled != null) {
      Node root = compiler.getRoot().getLastChild();
      Node expectedRoot = parseExpectedCode(compiled, options, normalizeResults);
      String explanation = expectedRoot.checkTreeEquals(root);
      assertNull("\nExpected: " + compiler.toSource(expectedRoot) +
          "\nResult: " + compiler.toSource(root) +
          "\n" + explanation, explanation);
    }
  }

  protected void checkUnexpectedErrorsOrWarnings(
      Compiler compiler, int expected) {
    int actual = compiler.getErrors().length + compiler.getWarnings().length;
    if (actual != expected) {
      String msg = "";
      for (JSError err : compiler.getErrors()) {
        msg += "Error:" + err + "\n";
      }
      for (JSError err : compiler.getWarnings()) {
        msg += "Warning:" + err + "\n";
      }
      assertEquals("Unexpected warnings or errors.\n " + msg,
        expected, actual);
    }
  }

  protected Compiler compile(CompilerOptions options, String original) {
    return compile(options, new String[] { original });
  }

  protected Compiler compile(CompilerOptions options, String[] original) {
    Compiler compiler = lastCompiler = new Compiler();
    BlackHoleErrorManager.silence(compiler);
    compiler.compileModules(
        externs, ImmutableList.copyOf(CompilerTestCase.createModuleChain(original)),
        options);
    return compiler;
  }

  /**
   * Parse the expected code to compare against.
   * We want to run this with similar parsing options, but don't
   * want to run the commonjs preprocessing passes (so that we can use this
   * to test the commonjs code).
   */
  protected Node parseExpectedCode(
      String[] original, CompilerOptions options, boolean normalize) {
    boolean oldProcessCommonJsModules = options.processCommonJSModules;
    options.processCommonJSModules = false;
    Node expectedRoot = parse(original, options, normalize);
    options.processCommonJSModules = oldProcessCommonJsModules;
    return expectedRoot;
  }

  protected Node parse(
      String[] original, CompilerOptions options, boolean normalize) {
    Compiler compiler = new Compiler();
    List<SourceFile> inputs = new ArrayList<>();
    for (int i = 0; i < original.length; i++) {
      inputs.add(SourceFile.fromCode("input" + i, original[i]));
    }
    compiler.init(externs, inputs, options);
    checkUnexpectedErrorsOrWarnings(compiler, 0);
    Node all = compiler.parseInputs();
    checkUnexpectedErrorsOrWarnings(compiler, 0);
    Node n = all.getLastChild();
    Node externs = all.getFirstChild();

    (new CreateSyntheticBlocks(
        compiler, "synStart", "synEnd")).process(externs, n);

    if (normalize) {
      compiler.normalize();
    }

    return n;
  }

  /** Creates a CompilerOptions object with google coding conventions. */
  abstract CompilerOptions createCompilerOptions();
}
