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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Use by the classes that test {@link NewTypeInference}.
 *
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public abstract class NewTypeInferenceTestBase extends CompilerTypeTestCase {

  protected CompilerOptions compilerOptions;

  protected static final String CLOSURE_BASE =
      LINE_JOINER.join(
          "/** @const */",
          "var goog = {};",
          "/** @return {void} */",
          "goog.nullFunction = function() {};",
          "/** @type {!Function} */",
          "goog.abstractMethod = function(){};",
          "goog.asserts;",
          "goog.asserts.assertInstanceOf;",
          "goog.getMsg;",
          "goog.addSingletonGetter;",
          "Object.prototype.superClass_;");

  protected static final String DEFAULT_EXTERNS =
      CompilerTypeTestCase.DEFAULT_EXTERNS + LINE_JOINER.join(
          "/** @const {undefined} */",
          "var undefined;",
          "/** @return {string} */",
          "Object.prototype.toString = function() {};",
          "/**",
          " * @param {*} propertyName",
          " * @return {boolean}",
          " */",
          "Object.prototype.hasOwnProperty = function(propertyName) {};",
          "/**",
          " * @this {!String|string}",
          " * @param {!RegExp} regexp",
          " * @return {!Array<string>}",
          " */",
          "String.prototype.match = function(regexp) {};",
          "/** @return {string} */",
          "String.prototype.toString = function() {};",
          "/** @return {string} */",
          "String.prototype.toLowerCase = function() {};",
          "String.prototype.startsWith = function(s) {};",
          "/**",
          " * @constructor",
          " * @param {*=} arg",
          " * @return {number}",
          " */",
          "function Number(arg) {}",
          "/**",
          " @param {number=} opt_radix",
          " @return {string}",
          "*/",
          "Number.prototype.toString = function(opt_radix) {};",
          "/**",
          " * @param {number=} opt_fractionDigits",
          " * @return {string}",
          " */",
          "Number.prototype.toExponential = function(opt_fractionDigits) {};",
          "/**",
          " * @constructor",
          " * @param {*=} arg",
          " * @return {boolean}",
          " */",
          "function Boolean(arg) {}",
          "/** @return {string} */",
          "Boolean.prototype.toString = function() {};",
          "/**",
          " * @param {?=} opt_begin",
          " * @param {?=} opt_end",
          " * @return {!Array.<T>}",
          " * @this {{length: number}|string}",
          " * @template T",
          " */",
          "Array.prototype.slice = function(opt_begin, opt_end) {};",
          "/**",
          " * @param {...?} var_args",
          " * @return {!Array.<?>}",
          " */",
          "Array.prototype.concat = function(var_args) {};",
          "/**",
          " * @this {{length: number}|Array<T>}",
          " * @return {T}",
          " * @template T",
          " */",
          "Array.prototype.shift = function() {};",
          "/** @interface */",
          "function IThenable () {}",
          "IThenable.prototype.then = function(onFulfilled) {};",
          "/**",
          " * @template T",
          " * @constructor",
          " * @implements {IThenable}",
          " */",
          "function Promise(resolver) {};",
          "/**",
          " * @param {VALUE} value",
          " * @return {!Promise<VALUE>}",
          " * @template VALUE",
          " */",
          "Promise.resolve = function(value) {};",
          "/**",
          " * @template RESULT",
          " * @param {function(): RESULT} onFulfilled",
          " * @return {RESULT}",
          " */",
          "Promise.prototype.then = function(onFulfilled) {};",
          "/**",
          " * @constructor",
          " * @param {*=} opt_message",
          " * @param {*=} opt_file",
          " * @param {*=} opt_line",
          " * @return {!Error}",
          " */",
          "function Error(opt_message, opt_file, opt_line) {}",
          "/** @type {string} */",
          "Error.prototype.stack;",
          "/**",
          " * @constructor",
          " * @param {?=} opt_pattern",
          " * @param {?=} opt_flags",
          " * @return {!RegExp}",
          " */",
          "function RegExp(opt_pattern, opt_flags) {}",
          "/** @constructor */",
          "function Window() {}",
          "/** @type {boolean} */",
          "Window.prototype.closed;",
          "/** @type {!Window} */",
          "var window;",
          "",
          "/**",
          " * @constructor",
          " * @extends {Array<string>}",
          " */",
          "var ITemplateArray = function() {};",
          "",
          "/** @type {!Array<string>} */",
          "ITemplateArray.prototype.raw;",
          "",
          "/**",
          " * @param {!ITemplateArray} template",
          " * @param {...*} var_args",
          " * @return {string}",
          " */",
          "String.raw = function(template, var_args) {};",
          "");

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    compilerOptions = getDefaultOptions();
  }

  @Override
  protected CompilerOptions getDefaultOptions() {
    CompilerOptions compilerOptions = super.getDefaultOptions();
    compilerOptions.setClosurePass(true);
    compilerOptions.setNewTypeInference(true);
    compilerOptions.setWarningLevel(
        DiagnosticGroups.NEW_CHECK_TYPES_ALL_CHECKS, CheckLevel.WARNING);
    // EC5 is the highest language level that type inference understands.
    compilerOptions.setLanguage(LanguageMode.ECMASCRIPT5);
    return compilerOptions;
  }

  protected final PassFactory makePassFactory(
      String name, final CompilerPass pass) {
    return new PassFactory(name, true/* one-time pass */) {
      @Override
      protected CompilerPass create(AbstractCompiler compiler) {
        return pass;
      }
    };
  }

  private final void parseAndTypeCheck(String externs, String js) {
    initializeNewCompiler(compilerOptions);
    compiler.init(
        ImmutableList.of(SourceFile.fromCode("[externs]", externs)),
        ImmutableList.of(SourceFile.fromCode("[testcode]", js)),
        compilerOptions);

    Node externsRoot = IR.block();
    externsRoot.setIsSyntheticBlock(true);
    externsRoot.addChildToFront(
        compiler.getInput(new InputId("[externs]")).getAstRoot(compiler));
    Node astRoot = IR.block();
    astRoot.setIsSyntheticBlock(true);
    astRoot.addChildToFront(
        compiler.getInput(new InputId("[testcode]")).getAstRoot(compiler));

    assertEquals("parsing error: " + Joiner.on(", ").join(compiler.getErrors()),
        0, compiler.getErrorCount());
    assertEquals(
        "parsing warning: " + Joiner.on(", ").join(compiler.getWarnings()), 0,
        compiler.getWarningCount());

    // Create common parent of externs and ast; needed by Es6RewriteBlockScopedDeclaration.
    Node block = IR.block(externsRoot, astRoot);
    block.setIsSyntheticBlock(true);

    // Run ASTValidator
    (new AstValidator(compiler)).validateRoot(block);

    DeclaredGlobalExternsOnWindow rewriteExterns =
        new DeclaredGlobalExternsOnWindow(compiler);
    List<PassFactory> passes = new ArrayList<>();
    passes.add(makePassFactory("globalExternsOnWindow", rewriteExterns));
    ProcessClosurePrimitives closurePass =
        new ProcessClosurePrimitives(compiler, null, CheckLevel.ERROR, false);
    passes.add(makePassFactory("ProcessClosurePrimitives", closurePass));
    if (compilerOptions.getLanguageIn() == LanguageMode.ECMASCRIPT6_TYPED) {
      passes.add(makePassFactory("convertEs6TypedToEs6",
              new Es6TypedToEs6Converter(compiler)));
    }
    if (compilerOptions.getLanguageIn().isEs6OrHigher()
        && !compilerOptions.getLanguageOut().isEs6OrHigher()) {
      TranspilationPasses.addEs6EarlyPasses(passes);
      TranspilationPasses.addEs6LatePasses(passes);
      TranspilationPasses.addRewritePolyfillPass(passes);
    }
    passes.add(makePassFactory("GlobalTypeInfo", compiler.getSymbolTable()));
    passes.add(makePassFactory("NewTypeInference", new NewTypeInference(compiler)));

    PhaseOptimizer phaseopt = new PhaseOptimizer(compiler, null, null);
    phaseopt.consume(passes);
    phaseopt.process(externsRoot, astRoot);
  }

  protected final void typeCheck(String js, DiagnosticType... warningKinds) {
    typeCheck(DEFAULT_EXTERNS, js, warningKinds);
  }

  protected final void typeCheckCustomExterns(
      String externs, String js, DiagnosticType... warningKinds) {
    typeCheck(externs, js, warningKinds);
  }

  private final void typeCheck(
      String externs, String js, DiagnosticType... warningKinds) {
    parseAndTypeCheck(externs, js);
    JSError[] warnings = compiler.getWarnings();
    JSError[] errors = compiler.getErrors();
    String errorMessage = LINE_JOINER.join(
        "Expected warning of type:",
        "================================================================",
        LINE_JOINER.join(warningKinds),
        "================================================================",
        "but found:",
        "----------------------------------------------------------------",
        LINE_JOINER.join(errors) + "\n" + LINE_JOINER.join(warnings),
        "----------------------------------------------------------------\n");
    assertEquals(
        errorMessage + "Warning count",
        warningKinds.length,
        warnings.length + errors.length);
    for (JSError warning : warnings) {
      assertTrue(
          "Wrong warning type\n" + errorMessage,
          Arrays.asList(warningKinds).contains(warning.getType()));
    }
    for (JSError error : errors) {
      assertTrue(
          "Wrong warning type\n" + errorMessage,
          Arrays.asList(warningKinds).contains(error.getType()));
    }
  }

  // Used only in the cases where we provide extra details in the error message.
  // Don't use in other cases.
  // It is deliberately less general; no custom externs and only a single
  // warning per test.
  protected final void typeCheckMessageContents(
      String js, DiagnosticType warningKind, String warningMsg) {
    parseAndTypeCheck(DEFAULT_EXTERNS, js);
    JSError[] warnings = compiler.getWarnings();
    JSError[] errors = compiler.getErrors();
    assertEquals(
        "Expected no errors, but found:\n" + Arrays.toString(errors),
        0, errors.length);
    assertEquals(
        "Expected one warning, but found:\n" + Arrays.toString(warnings),
        1, warnings.length);
    JSError warning = warnings[0];
    assertEquals(LINE_JOINER.join(
        "Wrong warning type",
        "Expected warning of type:",
        "================================================================",
        warningKind.toString(),
        "================================================================",
        "but found:",
        "----------------------------------------------------------------",
        warning.toString(),
        "----------------------------------------------------------------\n"),
        warningKind, warning.getType());
    assertEquals(LINE_JOINER.join(
        "Wrong warning message",
        "Expected:",
        "================================================================",
        warningMsg,
        "================================================================",
        "but found:",
        "----------------------------------------------------------------",
        warning.description,
        "----------------------------------------------------------------\n"),
        warningMsg, warning.description);
  }
}
