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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.testing.JSErrorSubject.assertError;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.List;

/**
 * Use by the classes that test {@link NewTypeInference}.
 *
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public abstract class NewTypeInferenceTestBase extends CompilerTypeTestCase {

  protected CompilerOptions compilerOptions;

  protected static enum InputLanguageMode {
    TRANSPILATION,
    NO_TRANSPILATION,
    BOTH;

    boolean checkNative() {
      return this == NO_TRANSPILATION || this == BOTH;
    }

    boolean checkTranspiled() {
      return this == TRANSPILATION || this == BOTH;
    }
  }

  protected InputLanguageMode mode = InputLanguageMode.NO_TRANSPILATION;

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
          "/**",
          " * @param {string} str",
          " * @param {Object<string, string>=} opt_values",
          " * @return {string}",
          " */",
          "goog.getMsg;",
          "goog.addSingletonGetter;",
          "goog.reflect;",
          "goog.reflect.object;",
          "Object.prototype.superClass_;");

  @SuppressWarnings("hiding")
  protected static final String DEFAULT_EXTERNS =
      CompilerTypeTestCase.DEFAULT_EXTERNS + LINE_JOINER.join(
          "/** @const {undefined} */",
          "var undefined;",
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
          " @param {number=} opt_radix",
          " @return {string}",
          "*/",
          "Number.prototype.toString = function(opt_radix) {};",
          "/**",
          " * @param {number=} opt_fractionDigits",
          " * @return {string}",
          " */",
          "Number.prototype.toExponential = function(opt_fractionDigits) {};",
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
          "/** @constructor */",
          "function Window() {}",
          "/** @type {boolean} */",
          "Window.prototype.closed;",
          "/** @type {!Window} */",
          "var window;",
          "/**",
          " * @param {Function|string} callback",
          " * @param {number=} opt_delay",
          " * @param {...*} var_args",
          " * @return {number}",
          " */",
          "function setTimeout(callback, opt_delay, var_args) {}",
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
          "/** @return {?} */",
          "function any() {}");

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
    compilerOptions.setUseTTLinNTI(true);
    compilerOptions.setWarningLevel(
        DiagnosticGroups.NEW_CHECK_TYPES_ALL_CHECKS, CheckLevel.WARNING);
    compilerOptions.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    // ES5 is the highest language level that type inference understands.
    compilerOptions.setLanguageOut(LanguageMode.ECMASCRIPT5);
    return compilerOptions;
  }

  protected PassFactory makePassFactory(String name, final CompilerPass pass) {
    return new PassFactory(name, true/* one-time pass */) {
      @Override
      protected CompilerPass create(AbstractCompiler compiler) {
        return pass;
      }

      @Override
      protected FeatureSet featureSet() {
        return FeatureSet.latest().withoutTypes();
      }
    };
  }

  private final void parseAndTypeCheck(String externs, String js) {
    initializeNewCompiler(compilerOptions);
    compiler.init(
        ImmutableList.of(SourceFile.fromCode("[externs]", externs)),
        ImmutableList.of(SourceFile.fromCode("[testcode]", js)),
        compilerOptions);
    compiler.setFeatureSet(compiler.getFeatureSet().without(Feature.MODULES));

    Node externsRoot = IR.root();
    externsRoot.addChildToFront(
        compiler.getInput(new InputId("[externs]")).getAstRoot(compiler));
    Node astRoot = IR.root();
    astRoot.addChildToFront(
        compiler.getInput(new InputId("[testcode]")).getAstRoot(compiler));

    assertThat(compiler.getErrors()).named("parsing errors").isEmpty();
    assertThat(compiler.getWarnings()).named("parsing warnings").isEmpty();

    // Create common parent of externs and ast; needed by Es6RewriteBlockScopedDeclaration.
    Node block = IR.root(externsRoot, astRoot);

    // Run ASTValidator
    (new AstValidator(compiler)).validateRoot(block);

    DeclaredGlobalExternsOnWindow rewriteExterns =
        new DeclaredGlobalExternsOnWindow(compiler);
    List<PassFactory> passes = new ArrayList<>();
    passes.add(makePassFactory("globalExternsOnWindow", rewriteExterns));
    ProcessClosurePrimitives closurePass =
        new ProcessClosurePrimitives(compiler, null, CheckLevel.ERROR, false);
    passes.add(makePassFactory("ProcessClosurePrimitives", closurePass));
    if (compilerOptions.needsTranspilationFrom(FeatureSet.TYPESCRIPT)) {
      passes.add(makePassFactory("convertEs6TypedToEs6",
              new Es6TypedToEs6Converter(compiler)));
    }
    if (compilerOptions.needsTranspilationFrom(FeatureSet.ES6)) {
      TranspilationPasses.addEs2017Passes(passes);
      TranspilationPasses.addEs2016Passes(passes);
      TranspilationPasses.addEs6EarlyPasses(passes);
      TranspilationPasses.addEs6LatePasses(passes);
      TranspilationPasses.addRewritePolyfillPass(passes);
    }
    passes.add(makePassFactory("GlobalTypeInfo", new GlobalTypeInfoCollector(compiler)));
    passes.add(makePassFactory("NewTypeInference", new NewTypeInference(compiler)));

    PhaseOptimizer phaseopt = new PhaseOptimizer(compiler, null);
    phaseopt.consume(passes);
    phaseopt.process(externsRoot, astRoot);
  }

  protected final void typeCheck(String js, DiagnosticType... warningKinds) {
    if (this.mode.checkNative()) {
      compilerOptions.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
      typeCheck(DEFAULT_EXTERNS, js, warningKinds);
    }
    if (this.mode.checkTranspiled()) {
      compilerOptions.setLanguageOut(LanguageMode.ECMASCRIPT5);
      typeCheck(DEFAULT_EXTERNS, js, warningKinds);
    }
  }

  protected final void typeCheckCustomExterns(
      String externs, String js, DiagnosticType... warningKinds) {
    if (this.mode.checkNative()) {
      compilerOptions.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
      typeCheck(externs, js, warningKinds);
    }
    if (this.mode.checkTranspiled()) {
      compilerOptions.setLanguageOut(LanguageMode.ECMASCRIPT5);
      typeCheck(externs, js, warningKinds);
    }
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
      assertWithMessage("Wrong warning type\n" + errorMessage)
          .that(warningKinds).asList().contains(warning.getType());
    }
    for (JSError error : errors) {
      assertWithMessage("Wrong warning type\n" + errorMessage)
          .that(warningKinds).asList().contains(error.getType());
    }
  }

  // Used only in the cases where we provide extra details in the error message.
  // Don't use in other cases.
  // It is deliberately less general; no custom externs and only a single
  // warning per test.
  protected final void typeCheckMessageContents(
      String js, DiagnosticType warningKind, String warningMsg) {
    if (this.mode.checkNative()) {
      compilerOptions.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
      typeCheckMessageContentsHelper(js, warningKind, warningMsg);
    }
    if (this.mode.checkTranspiled()) {
      compilerOptions.setLanguageOut(LanguageMode.ECMASCRIPT5);
      typeCheckMessageContentsHelper(js, warningKind, warningMsg);
    }
  }

  private final void typeCheckMessageContentsHelper(
      String js, DiagnosticType warningKind, String warningMsg) {
    parseAndTypeCheck(DEFAULT_EXTERNS, js);
    JSError[] warnings = compiler.getWarnings();
    JSError[] errors = compiler.getErrors();
    assertThat(errors).isEmpty();
    assertThat(warnings).hasLength(1);
    JSError warning = warnings[0];
    assertError(warning).hasType(warningKind);
    assertError(warning).hasMessage(warningMsg);
  }
}
