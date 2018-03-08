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
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

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
          "goog.inherits = function(child, parent){};",
          "/** @return {void} */",
          "goog.nullFunction = function() {};",
          "/** @type {!Function} */",
          "goog.abstractMethod = function(){};",
          "goog.asserts;",
          "goog.asserts.assert;",
          "goog.asserts.assertArray;",
          "goog.asserts.assertInstanceof;",
          "goog.asserts.assertNumber;",
          "goog.asserts.assertObject;",
          "goog.asserts.assertString;",
          "goog.partial;",
          "/**",
          " * @param {?function(this:T, ...)} fn",
          " * @param {T} selfObj",
          " * @param {...*} var_args",
          " * @return {!Function}",
          " * @template T",
          " */",
          "goog.bind = function(fn, selfObj, var_args) {",
          "  return function() {};",
          "};",
          "goog.isNull;",
          "goog.isDef;",
          "goog.isDefAndNotNull;",
          "goog.isArray;",
          "goog.isArrayLike;",
          "goog.isFunction;",
          "goog.isObject;",
          "goog.isString;",
          "goog.isNumber;",
          "goog.isBoolean;",
          "goog.typeOf;",
          "goog.addDependency = function(file, provides, requires){};",
          "goog.forwardDeclare = function(name){};",
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
      CompilerTestCase.lines(
              "/** @type {undefined} */",
              "var undefined;",
              "/**",
              " * @constructor",
              " * @param {*=} opt_value",
              " * @return {!Object}",
              " */",
              "function Object(opt_value) {}",
              "/**",
              " * @constructor",
              " * @param {...*} var_args",
              " */",
              "function Function(var_args) {}",
              "/**",
              " * @constructor",
              " * @implements {Iterable<string>}",
              " * @param {*=} arg",
              " * @return {string}",
              " */",
              "function String(arg) {}",
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
              "/**",
              " * @interface",
              " * @extends {Iterator<VALUE>}",
              " * @extends {Iterable<VALUE>}",
              " * @template VALUE",
              " */",
              "function IteratorIterable() {}",
              "/**",
              " * @interface",
              " * @template KEY1, VALUE1",
              " */",
              "function IObject() {};",
              "/**",
              " * @record",
              " * @extends IObject<number, VALUE2>",
              " * @template VALUE2",
              " */",
              "function IArrayLike() {};",
              "/**",
              " * @template T",
              " * @constructor ",
              " * @implements {IArrayLike<T>} ",
              " * @implements {Iterable<T>}",
              " * @param {...*} var_args",
              " * @return {!Array.<?>}",
              " */",
              "function Array(var_args) {}",
          "/**",
          " * @type{number}",
          " */",
          "IArrayLike.prototype.length;",
          "/** @return {string} */",
          "Object.prototype.toString = function() {};",
          "/**",
          " * @param {*} propertyName",
          " * @return {boolean}",
          " */",
          "Object.prototype.hasOwnProperty = function(propertyName) {};",
          "/** @type {?Function} */ Object.prototype.constructor;",
          "Object.defineProperties = function(obj, descriptors) {};",
          "/** @type {!Function} */ Function.prototype.apply;",
          "/** @type {!Function} */ Function.prototype.bind;",
          "/** @type {!Function} */ Function.prototype.call;",
          "/** @type {number} */",
          "Function.prototype.length;",
          "/** @type {string} */",
          "Function.prototype.name;",
          "/** @param {number} sliceArg */",
          "String.prototype.slice = function(sliceArg) {};",
          "/**",
          " * @this {?String|string}",
          " * @param {?} regex",
          " * @param {?} str",
          " * @param {string=} opt_flags",
          " * @return {string}",
          " */",
          "String.prototype.replace = function(regex, str, opt_flags) {};",
          "/** @type {number} */ String.prototype.length;",
          "/**",
          " * @constructor",
          " * @param {*=} arg",
          " * @return {number}",
          " */",
          "function Number(arg) {}",
          "/**",
          " * @constructor",
          " * @param {*=} arg",
          " * @return {boolean}",
          " */",
          "function Boolean(arg) {}",
          "/** @type {number} */ Array.prototype.length;",
          "/**",
          " * @param {*} arr",
          " * @return {boolean}",
          " */",
          "Array.isArray = function(arr) {};",
          "/**",
          " * @param {...T} var_args",
          " * @return {number} The new length of the array.",
          " * @this {IArrayLike<T>}",
          " * @template T",
          " * @modifies {this}",
          " */",
          "Array.prototype.push = function(var_args) {};",
          "/**",
          " * @this {IArrayLike<T>}",
          " * @return {T}",
          " * @template T",
          " */",
          "Array.prototype.shift = function() {};",
          "/**",
          " * @param {?function(this:S, T, number, !Array<T>): ?} callback",
          " * @param {S=} opt_thisobj",
          " * @this {?IArrayLike<T>|string}",
          " * @template T,S",
          " * @return {undefined}",
          " */",
          "Array.prototype.forEach = function(callback, opt_thisobj) {};",
          "/**",
          " * @param {?function(this:S, T, number, !Array<T>): ?} callback",
          " * @param {S=} opt_thisobj",
          " * @return {!Array<T>}",
          " * @this {?IArrayLike<T>|string}",
          " * @template T,S",
          " */",
          "Array.prototype.filter = function(callback, opt_thisobj) {};",
          "/**",
          " * @constructor",
          " * @template T",
          " * @implements {IArrayLike<T>}",
          " */",
          "function Arguments() {}",
          "/** @type {number} */",
          "Arguments.prototype.length;",
          "/**",
          " * @constructor",
          " * @param {*=} opt_pattern",
          " * @param {*=} opt_flags",
          " * @return {!RegExp}",
          " * @nosideeffects",
          " */",
          "function RegExp(opt_pattern, opt_flags) {}",
          "/**",
          " * @param {*} str The string to search.",
          " * @return {?Array<string>}",
          " */",
          "RegExp.prototype.exec = function(str) {};",
          "/**",
          " * @constructor",
          " */",
          "function ObjectPropertyDescriptor() {}",
          "/** @type {*} */",
          "ObjectPropertyDescriptor.prototype.value;",
          "/**",
          " * @param {!Object} obj",
          " * @param {string} prop",
          " * @return {!ObjectPropertyDescriptor|undefined}",
          " * @nosideeffects",
          " */",
          "Object.getOwnPropertyDescriptor = function(obj, prop) {};",
          "/**",
          " * @param {!Object} obj",
          " * @param {string} prop",
          " * @param {!Object} descriptor",
          " * @return {!Object}",
          " */",
          "Object.defineProperty = function(obj, prop, descriptor) {};",
          "/**",
          " * @param {?Object} proto",
          " * @param {?Object=} opt_properties",
          " * @return {!Object}",
          " */",
          "Object.create = function(proto, opt_properties) {};",
          "/**",
          " * @param {!Object} obj",
          " * @param {?} proto",
          " * @return {!Object}",
          " */",
          "Object.setPrototypeOf = function(obj, proto) {};",
          "/** @type {?} */ var unknown;", // For producing unknowns in tests.
          "/** @typedef {?} */ var symbol;", // TODO(sdh): remove once primitive 'symbol' supported
          "/** ",
          " * @constructor",
          " * @param {*=} opt_description",
          " * @return {symbol}",
          " */",
          "function Symbol(opt_description) {}",
          "/** @const {!symbol} */ Symbol.iterator;",
          "/**",
          " * @return {!Iterator<VALUE>}",
          " * @suppress {externsValidation}",
          " */",
          "Iterable.prototype[Symbol.iterator] = function() {};",
          "/** @type {number} */ var NaN;",
          "/**",
          " * @constructor",
          " * @implements {IteratorIterable<VALUE>}",
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
          " * @typedef {{then: ?}}",
          " */",
          "var Thenable;",
          "/**",
          " * @interface",
          " * @template TYPE",
          " */",
          "function IThenable() {}",
          "/**",
          " * @param {?(function(TYPE):VALUE)=} opt_onFulfilled",
          " * @param {?(function(*): *)=} opt_onRejected",
          " * @return {RESULT}",
          " * @template VALUE",
          " * @template RESULT := type('IThenable',",
          " *     cond(isUnknown(VALUE), unknown(),",
          " *       mapunion(VALUE, (V) =>",
          " *         cond(isTemplatized(V) && sub(rawTypeOf(V), 'IThenable'),",
          " *           templateTypeOf(V, 0),",
          " *           cond(sub(V, 'Thenable'),",
          " *              unknown(),",
          " *              V)))))",
          " * =:",
          " */",
          "IThenable.prototype.then = function(opt_onFulfilled, opt_onRejected) {};",
          "/**",
          " * @param {function(",
          " *             function((TYPE|IThenable<TYPE>|Thenable|null)=),",
          " *             function(*=))} resolver",
          " * @constructor",
          " * @implements {IThenable<TYPE>}",
          " * @template TYPE",
          " */",
          "function Promise(resolver) {}",
          "/**",
          " * @param {VALUE=} opt_value",
          " * @return {RESULT}",
          " * @template VALUE",
          " * @template RESULT := type('Promise',",
          " *     cond(isUnknown(VALUE), unknown(),",
          " *       mapunion(VALUE, (V) =>",
          " *         cond(isTemplatized(V) && sub(rawTypeOf(V), 'IThenable'),",
          " *           templateTypeOf(V, 0),",
          " *           cond(sub(V, 'Thenable'),",
          " *              unknown(),",
          " *              V)))))",
          " * =:",
          " */",
          "Promise.resolve = function(opt_value) {};",
          "/**",
          " * @param {*=} opt_error",
          " * @return {!Promise<?>}",
          " */",
          "Promise.reject = function(opt_error) {};",
          "/**",
          " * @param {!Iterable<VALUE>} iterable",
          " * @return {!Promise<!Array<RESULT>>}",
          " * @template VALUE",
          " * @template RESULT := mapunion(VALUE, (V) =>",
          " *     cond(isUnknown(V),",
          " *         unknown(),",
          " *         cond(isTemplatized(V) && sub(rawTypeOf(V), 'IThenable'),",
          " *             templateTypeOf(V, 0),",
          " *             cond(sub(V, 'Thenable'), unknown(), V))))",
          " * =:",
          " */",
          "Promise.all = function(iterable) {};",
          "/**",
          " * @param {!Iterable<VALUE>} iterable",
          " * @return {!Promise<RESULT>}",
          " * @template VALUE",
          " * @template RESULT := mapunion(VALUE, (V) =>",
          " *     cond(isUnknown(V),",
          " *         unknown(),",
          " *         cond(isTemplatized(V) && sub(rawTypeOf(V), 'IThenable'),",
          " *             templateTypeOf(V, 0),",
          " *             cond(sub(V, 'Thenable'), unknown(), V))))",
          " * =:",
          " */",
          "Promise.race = function(iterable) {};",
          "/**",
          " * @param {?(function(this:void, TYPE):VALUE)=} opt_onFulfilled",
          " * @param {?(function(this:void, *): *)=} opt_onRejected",
          " * @return {RESULT}",
          " * @template VALUE",
          " * @template RESULT := type('Promise',",
          " *     cond(isUnknown(VALUE), unknown(),",
          " *       mapunion(VALUE, (V) =>",
          " *         cond(isTemplatized(V) && sub(rawTypeOf(V), 'IThenable'),",
          " *           templateTypeOf(V, 0),",
          " *           cond(sub(V, 'Thenable'),",
          " *              unknown(),",
          " *              V)))))",
          " * =:",
          " * @override",
          " */",
          "Promise.prototype.then = function(opt_onFulfilled, opt_onRejected) {};",
          "/**",
          " * @param {function(*): RESULT} onRejected",
          " * @return {!Promise<RESULT>}",
          " * @template RESULT",
          " */",
          "Promise.prototype.catch = function(onRejected) {};",
          CompilerTestCase.ACTIVE_X_OBJECT_DEF,
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
          "/**",
          " * @constructor",
          " * @implements {Iterable<!Array<KEY|VALUE>>}",
          " * @param {Iterable<!Array<KEY|VALUE>>=} opt_arg",
          " * @template KEY, VALUE",
          " */",
          "function Map(opt_arg) {}",
          "/**",
          " * @constructor",
          " * @implements {Iterable<VALUE>}",
          " * @param {Iterable<VALUE>=} opt_arg",
          " * @template VALUE",
          " */",
          "function Set(opt_arg) {}",
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

  private final PassFactory setFeatureSet(final FeatureSet featureSet) {
    return new PassFactory("setFeatureSet:" + featureSet.version(), true) {
      @Override
      protected CompilerPass create(final AbstractCompiler compiler) {
        return new CompilerPass() {
          @Override
          public void process(Node externs, Node root) {
            compiler.setFeatureSet(featureSet);
          }
        };
      }

      @Override
      public FeatureSet featureSet() {
        return FeatureSet.latest();
      }
    };
  }

  private void parseAndTypeCheck(String externs, String js) {
    initializeNewCompiler(compilerOptions);
    compiler.init(
        ImmutableList.of(SourceFile.fromCode("[externs]", externs)),
        ImmutableList.of(SourceFile.fromCode("[testcode]", js)),
        compilerOptions);
    compiler.setFeatureSet(compiler.getFeatureSet().without(Feature.MODULES));

    compiler.parseInputs();
    assertWithMessage("parsing errors").that(compiler.getErrors()).isEmpty();
    assertWithMessage("parsing warnings").that(compiler.getWarnings()).isEmpty();

    // Run ASTValidator
    (new AstValidator(compiler)).validateRoot(compiler.getRoot());

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
    if (compilerOptions.needsTranspilationFrom(FeatureSet.ES_NEXT)) {
      // placeholder for a transpilation pass from ES_NEXT to ES8.
      passes.add(setFeatureSet(FeatureSet.ES8));
    }
    if (compilerOptions.needsTranspilationFrom(FeatureSet.ES6)) {
      TranspilationPasses.addEs2017Passes(passes);
      TranspilationPasses.addEs2016Passes(passes);
      TranspilationPasses.addEs6PassesBeforeNTI(passes);
      if (!compilerOptions.getTypeCheckEs6Natively()) {
        TranspilationPasses.addEs6PassesAfterNTI(passes);
        TranspilationPasses.addRewritePolyfillPass(passes);
      }
    }
    passes.add(makePassFactory("GlobalTypeInfo", new GlobalTypeInfoCollector(compiler)));
    passes.add(makePassFactory("NewTypeInference", new NewTypeInference(compiler)));
    if (compilerOptions.needsTranspilationFrom(FeatureSet.ES6)
        && compilerOptions.getTypeCheckEs6Natively()) {
      TranspilationPasses.addEs6PassesAfterNTI(passes);
      TranspilationPasses.addRewritePolyfillPass(passes);
    }

    PhaseOptimizer phaseopt = new PhaseOptimizer(compiler, null);
    phaseopt.consume(passes);
    phaseopt.process(compiler.getExternsRoot(), compiler.getJsRoot());
  }

  // Note: firstWarningKind is necessary to disambiguate the zero-diagnostic case.
  protected final void typeCheck(
      String js, DiagnosticType firstWarningKind, DiagnosticType... warningKinds) {
    typeCheck(js, Diagnostic.wrap(firstWarningKind, warningKinds));
  }

  protected final void typeCheck(String js, Diagnostic... diagnostics) {
    if (this.mode.checkNative()) {
      compilerOptions.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
      typeCheck(DEFAULT_EXTERNS, js, Diagnostic.unwrap(diagnostics, compilerOptions));
    }
    if (this.mode.checkTranspiled()) {
      compilerOptions.setLanguageOut(LanguageMode.ECMASCRIPT5);
      // TODO(sdh): stop allowing this option to be false, then we can eliminate this extra check.
      compilerOptions.setTypeCheckEs6Natively(false);
      typeCheck(DEFAULT_EXTERNS, js, Diagnostic.unwrap(diagnostics, compilerOptions));
      compilerOptions.setTypeCheckEs6Natively(true);
      typeCheck(DEFAULT_EXTERNS, js, Diagnostic.unwrap(diagnostics, compilerOptions));
    }
  }

  private void typeCheck(String externs, String js, DiagnosticType... warningKinds) {
    parseAndTypeCheck(externs, js);
    JSError[] warnings = compiler.getWarnings();
    JSError[] errors = compiler.getErrors();
    String errorMessage =
        LINE_JOINER.join(
            "Expected warning of type:",
            "================================================================",
            LINE_JOINER.join(warningKinds),
            "================================================================",
            "but found:",
            "----------------------------------------------------------------",
            LINE_JOINER.join(errors) + "\n" + LINE_JOINER.join(warnings),
            "----------------------------------------------------------------\n");
    assertEquals(
        errorMessage + "Warning count", warningKinds.length, warnings.length + errors.length);
    for (JSError warning : warnings) {
      assertWithMessage("Wrong warning type\n" + errorMessage)
          .that(warningKinds)
          .asList()
          .contains(warning.getType());
    }
    for (JSError error : errors) {
      assertWithMessage("Wrong warning type\n" + errorMessage)
          .that(warningKinds)
          .asList()
          .contains(error.getType());
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
      // TODO(sdh): stop allowing this option to be false, then we can eliminate this extra check.
      compilerOptions.setTypeCheckEs6Natively(false);
      typeCheck(externs, js, warningKinds);
      compilerOptions.setTypeCheckEs6Natively(true);
      typeCheck(externs, js, warningKinds);
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
      // TODO(sdh): stop allowing this option to be false, then we can eliminate this extra check.
      compilerOptions.setTypeCheckEs6Natively(false);
      typeCheckMessageContentsHelper(js, warningKind, warningMsg);
      compilerOptions.setTypeCheckEs6Natively(true);
      typeCheckMessageContentsHelper(js, warningKind, warningMsg);
    }
  }

  private void typeCheckMessageContentsHelper(
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

  /** Abstraction over DiagnosticType that allows warnings to be expected conditionally. */
  protected abstract static class Diagnostic implements Function<CompilerOptions, DiagnosticType> {

    protected static Diagnostic of(final DiagnosticType type) {
      return new Diagnostic() {
        @Override
        public DiagnosticType apply(CompilerOptions unused) {
          return type;
        }
      };
    }

    protected static Diagnostic[] wrap(DiagnosticType firstType, DiagnosticType[] types) {
      Diagnostic[] out = new Diagnostic[types.length + 1];
      out[0] = of(firstType);
      for (int i = 0; i < types.length; i++) {
        out[i + 1] = of(types[i]);
      }
      return out;
    }

    protected static DiagnosticType[] unwrap(Diagnostic[] diagnostics, CompilerOptions options) {
      List<DiagnosticType> out = new ArrayList<>();
      for (Diagnostic diagnostic : diagnostics) {
        DiagnosticType unwrapped = diagnostic.apply(options);
        if (unwrapped != null) {
          out.add(unwrapped);
        }
      }
      return out.toArray(new DiagnosticType[0]);
    }
  }

  /** A warning that is only expected when type checking ES6 natively. */
  protected static Diagnostic nativeEs6Only(final DiagnosticType type) {
    return new Diagnostic() {
      @Override
      public DiagnosticType apply(CompilerOptions options) {
        return options.needsTranspilationFrom(FeatureSet.ES6) && options.getTypeCheckEs6Natively()
            ? type : null;
      }
    };
  }

  /** A warning that is only expected when not type checking ES6 natively. */
  protected static Diagnostic fullyTranspiledOnly(final DiagnosticType type) {
    return new Diagnostic() {
      @Override
      public DiagnosticType apply(CompilerOptions options) {
        return options.needsTranspilationFrom(FeatureSet.ES6) && !options.getTypeCheckEs6Natively()
            ? type : null;
      }
    };
  }
}
