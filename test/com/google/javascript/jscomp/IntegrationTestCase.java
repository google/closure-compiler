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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.testing.JSErrorSubject.assertError;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerTestCase.NoninjectingCompiler;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;

/**
 * Framework for end-to-end test cases.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
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
          SourceFile.fromCode(
              "externs",
              lines(
                  "var arguments;",
                  "var undefined;",
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
                  " * @interface",
                  " * @template VALUE",
                  " */",
                  "function Iterable() {}",
                  "/**",
                  " * @interface",
                  " * @template VALUE",
                  " */",
                  "function Iterator() {}",
                  "/**",
                  " * @param {VALUE=} value",
                  " * @return {!IIterableResult<VALUE>}",
                  " */",
                  "Iterator.prototype.next;",
                  "/** @interface @extends {Iterator<T>} @extends {Iterable<T>} @template T*/",
                  "var IteratorIterable = function() {};",
                  "/** @interface */",
                  "function IArrayLike() {};",
                  // TODO(sdh): See if we can remove IIterableResult and Set once polyfills are
                  // split
                  "/**",
                  " * @record",
                  " * @template VALUE",
                  " */",
                  "function IIterableResult() {};",
                  "/** @type {boolean} */",
                  "IIterableResult.prototype.done;",
                  "/** @type {VALUE} */",
                  "IIterableResult.prototype.value;",
                  "/**",
                  " * @constructor",
                  " * @extends {Array<string>}",
                  " */",
                  "var ITemplateArray = function() {};",
                  "/** @constructor */",
                  "var Map;",
                  "/** @constructor */",
                  "var Set;",
                  "/** @constructor */",
                  "function ObjectPropertyDescriptor() {};",
                  "",
                  "ObjectPropertyDescriptor.prototype.value;",
                  "",
                  "/** @type {(function():?)|undefined} */",
                  "ObjectPropertyDescriptor.prototype.get;",
                  "",
                  "/** @type {(function(?):void)|undefined} */",
                  "ObjectPropertyDescriptor.prototype.set;",
                  "",
                  "/** @type {boolean|undefined} */",
                  "ObjectPropertyDescriptor.prototype.enumerable;",
                  "",
                  "/** @type {boolean|undefined} */",
                  "ObjectPropertyDescriptor.prototype.configurable;",
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
                  " * @implements {Iterable<T>}",
                  " * @return {!Array<?>}",
                  " * @param {...*} var_args",
                  " * @template T",
                  " */",
                  "function Array(var_args) {}",
                  "",
                  "/** @type {number} */",
                  "Array.prototype.length;",
                  "",
                  "/** @return {!IteratorIterable<T>} */",
                  "Array.prototype.values;",
                  "",
                  "Array.prototype.splice;",
                  "Array.prototype.push;",
                  "Array.prototype.reverse;",
                  "Array.prototype.pop;",
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
                  " * @implements {Iterable<string>}",
                  " * @param {*=} opt_str",
                  " * @return {string}",
                  " */",
                  "function String(opt_str) {}",
                  "",
                  "String.prototype.split = function(delimiter) {};",
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
                  "/**",
                  " * @constructor",
                  " * @param {*=} opt_value",
                  " * @return {!Object}",
                  " */",
                  "function Object(opt_value) {}",
                  "Object.seal;",
                  "Object.defineProperties;",
                  "Object.defineProperty;",
                  "Object.getOwnPropertyDescriptor;",
                  "",
                  "Object.prototype;",
                  "",
                  "/** @type {!Function} */",
                  "Object.prototype.constructor;",
                  "Object.create = function(obj) {};",
                  "/** @type {function(!Object, ?Object)} */",
                  "Object.setPrototypeOf;",
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
                  "/**",
                  " * @param {*} context",
                  " * @param {!IArrayLike} args",
                  " */",
                  "Function.prototype.apply = function (context, args) {};",
                  "/** @param {...*} var_args */",
                  "Function.prototype.call = function (var_args) {};",
                  "",
                  "/**",
                  " * @constructor",
                  " * @template T",
                  " */",
                  "function Arguments() {}",
                  "",
                  "/**",
                  " * @interface",
                  " * @extends {IteratorIterable<VALUE>}",
                  " * @template VALUE",
                  " */",
                  "function Generator() {}",
                  "/**",
                  " * @param {?=} opt_value",
                  " * @return {!IIterableResult<VALUE>}",
                  " * @override",
                  " */",
                  "Generator.prototype.next = function(opt_value) {};",
                  "/**",
                  " * @param {VALUE} value",
                  " * @return {!IIterableResult<VALUE>}",
                  " */",
                  "Generator.prototype.return = function(value) {};",
                  "/**",
                  " * @param {?} exception",
                  " * @return {!IIterableResult<VALUE>}",
                  " */",
                  "Generator.prototype.throw = function(exception) {};",
                  "",
                  "/** @interface */",
                  "function IThenable() {}",
                  "",
                  "IThenable.prototype.then = function(callback) {};",
                  "",
                  "/**",
                  " * @param {function(",
                  " *             function((TYPE|IThenable<TYPE>|Thenable|null)=),",
                  " *             function(*=))} resolver",
                  " * @constructor",
                  " * @implements {IThenable<TYPE>}",
                  " * @template TYPE",
                  " */",
                  "function Promise(resolver) {}",
                  "",
                  "Promise.resolve = function(value) {};",
                  "",
                  "/**",
                  " * @param {?(function(this:void, TYPE):VALUE)=} opt_onFulfilled",
                  " * @param {?(function(this:void, *): *)=} opt_onRejected",
                  " * @template VALUE",
                  " * @override",
                  " */",
                  "Promise.prototype.then = function(opt_onFulfilled, opt_onRejected) {};",
                  "",
                  "/**",
                  " * @param {function(*): RESULT} opt_errorCallback",
                  " * @return {!Promise<RESULT>}",
                  " * @template RESULT",
                  " */",
                  "Promise.prototype.catch = function(opt_errorCallback) {};",
                  "",
                  "/**",
                  " * @typedef {{then: ?}}",
                  " */",
                  "var Thenable;",
                  "",
                  "/** @constructor */",
                  "var HTMLElement = function() {};",
                  "")));

  protected List<SourceFile> externs = DEFAULT_EXTERNS;

  // The most recently used compiler.
  protected Compiler lastCompiler;

  protected boolean normalizeResults = false;
  protected boolean useNoninjectingCompiler = false;

  protected String inputFileNamePrefix;
  protected String inputFileNameSuffix;

  @Before
  public void setUp() {
    externs = DEFAULT_EXTERNS;
    lastCompiler = null;
    normalizeResults = false;
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
    assertWithMessage(
            "Expected no warnings or errors\n"
                + "Errors: \n"
                + Joiner.on("\n").join(compiler.getErrors())
                + "\n"
                + "Warnings: \n"
                + Joiner.on("\n").join(compiler.getWarnings()))
        .that(compiler.getErrors().length + compiler.getWarnings().length)
        .isEqualTo(0);

    Node root = compiler.getJsRoot();
    if (compiled != null) {
      Node expectedRoot = parseExpectedCode(compiled, options, normalizeResults);
      String explanation = expectedRoot.checkTreeEquals(root);
      assertWithMessage(
              "\n"
                  + "Expected: "
                  + compiler.toSource(expectedRoot)
                  + "\n"
                  + "Result:   "
                  + compiler.toSource(root)
                  + "\n"
                  + explanation)
          .that(explanation)
          .isNull();
    }
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
    assertWithMessage("Expected exactly one warning or error")
        .that(compiler.getErrors().length + compiler.getWarnings().length)
        .isEqualTo(1);
    if (compiler.getErrors().length > 0) {
      assertError(compiler.getErrors()[0]).hasType(warning);
    } else {
      assertError(compiler.getWarnings()[0]).hasType(warning);
    }

    if (compiled != null) {
      Node root = compiler.getRoot().getLastChild();
      Node expectedRoot = parseExpectedCode(compiled, options, normalizeResults);
      String explanation = expectedRoot.checkTreeEquals(root);
      assertWithMessage(
              "\nExpected: "
                  + compiler.toSource(expectedRoot)
                  + "\nResult: "
                  + compiler.toSource(root)
                  + "\n"
                  + explanation)
          .that(explanation)
          .isNull();
    }
  }

  /** Asserts that when compiling with the given compiler options, there is an error or warning. */
  protected void test(
      CompilerOptions options, String[] original, String[] compiled, DiagnosticType[] warnings) {
    Compiler compiler = compile(options, original);
    checkUnexpectedErrorsOrWarnings(compiler, warnings.length);

    if (compiled != null) {
      Node root = compiler.getRoot().getLastChild();
      Node expectedRoot = parseExpectedCode(compiled, options, normalizeResults);
      String explanation = expectedRoot.checkTreeEquals(root);
      assertWithMessage(
              "\nExpected: "
                  + compiler.toSource(expectedRoot)
                  + "\nResult:   "
                  + compiler.toSource(root)
                  + "\n"
                  + explanation)
          .that(explanation)
          .isNull();
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
        assertWithMessage("Found unexpected error type " + error.getType() + ":\n" + error).fail();
      }
    }
    assertWithMessage("Unexpected warnings: " + Joiner.on("\n").join(compiler.getWarnings()))
        .that(compiler.getWarnings().length)
        .isEqualTo(0);

    if (compiled != null) {
      Node root = compiler.getRoot().getLastChild();
      Node expectedRoot = parseExpectedCode(
          new String[] {compiled}, options, normalizeResults);
      String explanation = expectedRoot.checkTreeEquals(root);
      assertWithMessage(
              "\nExpected: "
                  + compiler.toSource(expectedRoot)
                  + "\nResult: "
                  + compiler.toSource(root)
                  + "\n"
                  + explanation)
          .that(explanation)
          .isNull();
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
            CompilerTestCase.createModuleChain(
                ImmutableList.copyOf(original), inputFileNamePrefix, inputFileNameSuffix)),
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
      inputs.add(SourceFile.fromCode(inputFileNamePrefix + i + inputFileNameSuffix, original[i]));
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
      new Normalize(compiler, false)
          .process(compiler.getExternsRoot(), compiler.getJsRoot());
    }

    return n;
  }

  /** Creates a CompilerOptions object with google coding conventions. */
  abstract CompilerOptions createCompilerOptions();
}
