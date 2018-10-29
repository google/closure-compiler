/*
 * Copyright 2006 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.testing.JSErrorSubject.assertError;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.truth.Correspondence;
import com.google.errorprone.annotations.ForOverride;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.type.ReverseAbstractInterpreter;
import com.google.javascript.jscomp.type.SemanticReverseAbstractInterpreter;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile.SourceKind;
import com.google.javascript.rhino.testing.BaseJSTypeTestCase;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;

/**
 * Base class for testing JS compiler classes that change the node tree of a compiled JS input.
 *
 * <p>Pulls in shared functionality from different test cases. Also supports node tree comparison
 * for input and output (instead of string comparison), which makes it easier to write tests b/c you
 * don't have to get the syntax exactly correct to the spacing.
 *
 */
public abstract class CompilerTestCase {
  protected static final Joiner LINE_JOINER = Joiner.on('\n');

  /** Externs for the test */
  final List<SourceFile> externsInputs;

  /** Libraries to inject before typechecking */
  final Set<String> librariesToInject;

  /** Whether to include synthetic code when comparing actual to expected */
  private boolean compareSyntheticCode;

  /** Whether to compare input and output as trees instead of strings */
  private boolean compareAsTree;

  /** Whether to parse type info from JSDoc comments */
  private boolean parseTypeInfo;

  /** Whether to take JSDoc into account when comparing ASTs. */
  private boolean compareJsDoc;

  /** Whether we check warnings without source information. */
  private boolean allowSourcelessWarnings;

  /** True iff closure pass runs before pass being tested. */
  private boolean closurePassEnabled;

  /** Whether the closure pass is run on the expected JS. */
  private boolean closurePassEnabledForExpected;

  /** Whether to rewrite commons js modules before the test is run. */
  private boolean processCommonJsModules;

  /** Whether to rewrite Closure code before the test is run. */
  private boolean rewriteClosureCode;

  /**
   * If true, run type checking together with the pass being tested. A separate
   * flag controls whether type checking runs before or after the pass.
   */
  private boolean typeCheckEnabled;

  /**
   * If true performs the test using multistage compilation.
   */
  private boolean multistageCompilation;

  /** Whether to test the compiler pass before the type check. */
  private boolean runTypeCheckAfterProcessing;

  /** Whether to scan externs for property names. */
  private boolean gatherExternPropertiesEnabled;

  /**
   * Whether the Normalize pass runs before pass being tested and
   * whether the expected JS strings should be normalized.
   */
  private boolean normalizeEnabled;

  private boolean polymerPass;

  /** Whether the transpilation passes run before the pass being tested. */
  private boolean transpileEnabled;

  /** Whether we run InferConsts before checking. */
  private boolean inferConsts;

  /** Whether we run CheckAccessControls after the pass being tested. */
  private boolean checkAccessControls;

  /** Whether to check that all line number information is preserved. */
  private boolean checkLineNumbers;

  /** Whether to check that changed scopes are marked as changed */
  private boolean checkAstChangeMarking;

  /** Whether we expect parse warnings in the current test. */
  private boolean expectParseWarningsThisTest;

  /**
   * An expected symbol table error. Only useful for testing the
   * symbol table error-handling.
   */
  private DiagnosticType expectedSymbolTableError;

  /**
   * Whether the MarkNoSideEffectsCalls pass runs before the pass being tested
   */
  private boolean markNoSideEffects;

  /**
   * Whether the PureFunctionIdentifier pass runs before the pass being tested
   */
  private boolean computeSideEffects;

  /** The most recently used Compiler instance. */
  private Compiler lastCompiler;

  /**
   * Whether to accept ES6, ES5 or ES3 source.
   */
  private LanguageMode acceptedLanguage;

  private LanguageMode languageOut;

  /** How to interpret ES6 module imports */
  private ModuleLoader.ResolutionMode moduleResolutionMode;

  /**
   * Whether externs changes should be allowed for this pass.
   */
  private boolean allowExternsChanges;

  /**
   * Whether the AST should be validated.
   */
  private boolean astValidationEnabled;

  /**
   * Whether we should verify that type information is present for every AST node that should have
   * it, and not present for those that shouldn't have it.
   */
  private boolean typeInfoValidationEnabled;

  /**
   * Whether we should verify that for every SCRIPT node, all features present in its AST are also
   * present in its FeatureSet property.
   */
  private boolean scriptFeatureValidationEnabled;

  private final Set<DiagnosticType> ignoredWarnings = new HashSet<>();

  private final Map<String, String> webpackModulesById = new HashMap<>();

  /** Whether {@link #setUp} has run. */
  private boolean setUpRan = false;

  protected static final String ACTIVE_X_OBJECT_DEF =
      lines(
          "/**",
          " * @param {string} progId",
          " * @param {string=} opt_location",
          " * @constructor",
          " * @see http://msdn.microsoft.com/en-us/library/7sw4ddf8.aspx",
          " */",
          "function ActiveXObject(progId, opt_location) {}");

  /** A minimal set of externs, consisting of only those needed for NTI not to blow up. */
  protected static final String MINIMAL_EXTERNS =
      lines(
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
          " * @constructor",
          " * @implements {IArrayLike<T>} ",
          " * @implements {Iterable<T>}",
          " * @param {...*} var_args",
          " * @return {!Array<?>}",
          " */",
          "function Array(var_args) {}");

  /** A default set of externs for testing. */
  protected static final String DEFAULT_EXTERNS =
      lines(
          MINIMAL_EXTERNS,
          "/**",
          " * @type{number}",
          " */",
          "IArrayLike.prototype.length;",
          "/** @type {?Object} */ Object.prototype.__proto__;",
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
          "/**",
          " * @constructor",
          " * @extends {Array<string>}",
          " */",
          "function ITemplateArray() {}",
          ACTIVE_X_OBJECT_DEF);

  /**
   * Constructs a test.
   *
   * @param externs Externs JS as a string
   */
  protected CompilerTestCase(String externs) {
    this.externsInputs = ImmutableList.of(SourceFile.fromCode("externs", externs));
    librariesToInject = new HashSet<>();
  }

  /**
   * Constructs a test. Uses AST comparison and no externs.
   */
  protected CompilerTestCase() {
    this("");
  }

  @SuppressWarnings("MissingOverride")
  public String getName() {
    return this.getClass().getSimpleName();
  }

  // Overridden here so that we can easily find all classes that override.
  @Before
  public void setUp() throws Exception {

    // TODO(sdh): Initialize *all* the options here, but first we must ensure no subclass
    // is changing them in the constructor, rather than in their own setUp method.
    this.acceptedLanguage = LanguageMode.ECMASCRIPT_2017;
    this.moduleResolutionMode = ModuleLoader.ResolutionMode.BROWSER;
    this.allowExternsChanges = false;
    this.allowSourcelessWarnings = false;
    this.astValidationEnabled = true;
    this.typeInfoValidationEnabled = false;
    this.scriptFeatureValidationEnabled = true;
    this.checkAccessControls = false;
    this.checkAstChangeMarking = true;
    this.checkLineNumbers = true;
    this.closurePassEnabled = false;
    this.closurePassEnabledForExpected = false;
    this.compareAsTree = true;
    this.compareJsDoc = true;
    this.compareSyntheticCode = true;
    this.computeSideEffects = false;
    this.expectParseWarningsThisTest = false;
    this.expectedSymbolTableError = null;
    this.gatherExternPropertiesEnabled = false;
    this.inferConsts = false;
    this.languageOut = LanguageMode.ECMASCRIPT5;
    this.markNoSideEffects = false;
    this.multistageCompilation = true;
    this.normalizeEnabled = false;
    this.parseTypeInfo = false;
    this.polymerPass = false;
    this.processCommonJsModules = false;
    this.rewriteClosureCode = false;
    this.runTypeCheckAfterProcessing = false;
    this.transpileEnabled = false;
    this.typeCheckEnabled = false;

    this.setUpRan = true;
  }

  protected void tearDown() throws Exception {
    this.setUpRan = false;
  }

  /**
   * Gets the compiler pass instance to use for a test.
   *
   * @param compiler The compiler
   * @return The pass to test
   */
  protected abstract CompilerPass getProcessor(Compiler compiler);

  /**
   * Gets the compiler options to use for this test. Use getProcessor to
   * determine what passes should be run.
   */
  protected CompilerOptions getOptions() {
    return getOptions(new CompilerOptions());
  }

  /**
   * Gets the compiler options to use for this test. Use getProcessor to
   * determine what passes should be run.
   */
  protected CompilerOptions getOptions(CompilerOptions options) {
    options.setLanguageIn(acceptedLanguage);
    options.setEmitUseStrict(false);
    options.setLanguageOut(languageOut);
    options.setModuleResolutionMode(moduleResolutionMode);
    options.setPreserveTypeAnnotations(true);

    // This doesn't affect whether checkSymbols is run--it just affects
    // whether variable warnings are filtered.
    options.setCheckSymbols(true);

    options.setWarningLevel(DiagnosticGroups.INVALID_CASTS, CheckLevel.WARNING);
    options.setWarningLevel(DiagnosticGroups.MISPLACED_MSG_ANNOTATION, CheckLevel.WARNING);
    options.setWarningLevel(DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.WARNING);
    if (!ignoredWarnings.isEmpty()) {
      options.setWarningLevel(
          new DiagnosticGroup(ignoredWarnings.toArray(new DiagnosticType[0])), CheckLevel.OFF);
    }
    options.setCodingConvention(getCodingConvention());
    options.setPolymerVersion(1);
    return options;
  }

  @ForOverride
  protected CodingConvention getCodingConvention() {
    return new GoogleCodingConvention();
  }

  /**
   * Enables parsing type info from JSDoc comments. This sets the compiler option,
   * but does not actually run the type checking pass.
   */
  protected final void enableParseTypeInfo() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    this.parseTypeInfo = true;
  }

  /** Turns off taking JSDoc into account when comparing ASTs. */
  protected final void disableCompareJsDoc() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    this.compareJsDoc = false;
  }

  /** Moves type checking to occur after the processor, instead of before. */
  protected final void enableRunTypeCheckAfterProcessing() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    this.runTypeCheckAfterProcessing = true;
  }

  /**
   * Returns the number of times the pass should be run before results are
   * verified.
   */
  @ForOverride
  protected int getNumRepetitions() {
    // Since most compiler passes should be idempotent, we run each pass twice
    // by default.
    return 2;
  }

  /** Adds the given DiagnosticTypes to the set of warnings to ignore. */
  protected final void ignoreWarnings(DiagnosticType... warnings) {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    Collections.addAll(ignoredWarnings, warnings);
  }

  /** Adds the given DiagnosticGroups to the set of warnings to ignore. */
  protected final void ignoreWarnings(DiagnosticGroup... warnings) {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    for (DiagnosticGroup group : warnings) {
      ignoredWarnings.addAll(group.getTypes());
    }
  }

  /** Expect warnings without source information. */
  protected final void allowSourcelessWarnings() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    allowSourcelessWarnings = true;
  }

  /** The most recently used JSComp instance. */
  protected Compiler getLastCompiler() {
    return lastCompiler;
  }

  /**
   * What language to allow in source parsing. Also sets the output language.
   */
  protected final void setAcceptedLanguage(LanguageMode lang) {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    setLanguage(lang, lang);
  }

  /**
   * Sets the input and output language modes..
   */
  protected final void setLanguage(LanguageMode langIn, LanguageMode langOut) {
    this.acceptedLanguage = langIn;
    setLanguageOut(langOut);
  }

  protected final void setLanguageOut(LanguageMode acceptedLanguage) {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    this.languageOut = acceptedLanguage;
  }

  protected final void setModuleResolutionMode(ModuleLoader.ResolutionMode moduleResolutionMode) {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    this.moduleResolutionMode = moduleResolutionMode;
  }

  /**
   * Whether to run InferConsts before passes
   */
  protected final void enableInferConsts() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    this.inferConsts = true;
  }

  /**
   * Enables running CheckAccessControls after the pass being tested (and checking types).
   */
  protected final void enableCheckAccessControls() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    this.checkAccessControls = true;
  }

  /**
   * Allow externs to change.
   */
  protected final void allowExternsChanges() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    this.allowExternsChanges = true;
  }

  protected final void enablePolymerPass() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    polymerPass = true;
  }

  /**
   * Perform type checking before running the test pass. This will check
   * for type errors and annotate nodes with type information.
   *
   * @see TypeCheck
   */
  protected final void enableTypeCheck() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    typeCheckEnabled = true;
  }

  protected final boolean isTypeCheckEnabled() {
    return typeCheckEnabled;
  }

  /**
   * Do not run type checking before running the test pass.
   *
   * @see TypeCheck
   */
  protected final void disableTypeCheck() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    typeCheckEnabled = false;
  }

  /**
   * When comparing expected to actual, ignore nodes created through compiler.ensureLibraryInjected
   *
   * <p>This differs from using a NonInjecting compiler in that the compiler still injects the
   * polyfills when requested.
   */
  protected final void disableCompareSyntheticCode() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    compareSyntheticCode = false;
  }

  /**
   * Run using multistage compilation.
   */
  protected final void enableMultistageCompilation() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    multistageCompilation = true;
  }

  /**
   * Run using singlestage compilation.
   */
  protected final void disableMultistageCompilation() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    multistageCompilation = false;
  }

  /**
   * Disable checking to make sure that line numbers were preserved.
   */
  protected final void disableLineNumberCheck() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    checkLineNumbers = false;
  }

  /** Disable validating AST change marking. */
  protected final void disableValidateAstChangeMarking() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    checkAstChangeMarking = false;
  }

  /**
   * Process closure library primitives.
   */
  protected final void enableClosurePass() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    closurePassEnabled = true;
  }

  protected final void enableClosurePassForExpected() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    closurePassEnabledForExpected = true;
  }

  /** Rewrite CommonJS modules before the test run. */
  protected final void enableProcessCommonJsModules() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    processCommonJsModules = true;
  }

  /**
   * Rewrite Closure code before the test is run.
   */
  protected final void enableRewriteClosureCode() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    rewriteClosureCode = true;
  }

  /**
   * Don't rewrite Closure code before the test is run.
   */
  protected final void disableRewriteClosureCode() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    rewriteClosureCode = false;
  }

  /**
   * Perform AST normalization before running the test pass, and anti-normalize
   * after running it.
   *
   * @see Normalize
   */
  protected final void enableNormalize() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    this.normalizeEnabled = true;
  }

  /**
   * Perform AST transpilation before running the test pass.
   */
  protected final void enableTranspile() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    transpileEnabled = true;
  }

  /**
   * Don't perform AST normalization before running the test pass.
   * @see Normalize
   */
  protected final void disableNormalize() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    normalizeEnabled = false;
  }

  /**
   * Run the MarkSideEffectCalls pass before running the test pass.
   *
   * @see MarkNoSideEffectCalls
   */
  protected final void enableMarkNoSideEffects() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    markNoSideEffects = true;
  }

  /**
   * Run the PureFunctionIdentifier pass before running the test pass.
   *
   * @see MarkNoSideEffectCalls
   */
  protected final void enableComputeSideEffects() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    computeSideEffects = true;
  }

  /**
   * Scan externs for properties that should not be renamed.
   */
  protected final void enableGatherExternProperties() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    gatherExternPropertiesEnabled = true;
  }

  /**
   * Disable validating the AST after each run of the pass.
   */
  protected final void disableAstValidation() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    astValidationEnabled = false;
  }

  /** Enable validating type information in the AST after each run of the pass. */
  protected final void enableTypeInfoValidation() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    typeInfoValidationEnabled = true;
  }

  /** Disable validating type information in the AST after each run of the pass. */
  protected final void disableTypeInfoValidation() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    typeInfoValidationEnabled = false;
  }

  /** Enable validating script featuresets after each run of the pass. */
  protected final void enableScriptFeatureValidation() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    scriptFeatureValidationEnabled = true;
  }

  /** Disable validating script featuresets after each run of the pass. */
  protected final void disableScriptFeatureValidation() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    scriptFeatureValidationEnabled = false;
  }

  /**
   * Disable comparing the expected output as a tree or string. 99% of the time you want to compare
   * as a tree. There are a few special cases where you don't, like if you want to test the code
   * printing of "unnatural" syntax trees. For example,
   *
   * <pre>
   * IF
   *   IF
   *     STATEMENT
   * ELSE
   *   STATEMENT
   * </pre>
   */
  protected final void disableCompareAsTree() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    this.compareAsTree = false;
  }

  /** Whether we should ignore parse warnings for the current test method. */
  protected final void setExpectParseWarningsThisTest() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    expectParseWarningsThisTest = true;
  }

  protected final void setWebpackModulesById(Map<String, String> webpackModulesById) {
    this.webpackModulesById.clear();
    this.webpackModulesById.putAll(webpackModulesById);
  }

  /** Returns a newly created TypeCheck. */
  private static TypeCheck createTypeCheck(Compiler compiler) {
    ReverseAbstractInterpreter rai =
        new SemanticReverseAbstractInterpreter(compiler.getTypeRegistry());
    compiler.setTypeCheckingHasRun(true);
    return new TypeCheck(compiler, rai, compiler.getTypeRegistry());
  }

  /** Ensures the given library is injected before typechecking */
  protected final void ensureLibraryInjected(String resourceName) {
    librariesToInject.add(resourceName);
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output.
   *
   * @param js Input
   * @param expected Expected JS output
   */
  protected void test(String js, String expected) {
    test(srcs(js), expected(expected));
  }

  /**
   * Verifies that the compiler generates the given error for the given input.
   *
   * @param js Input
   * @param error Expected error
   */
  protected void testError(String js, DiagnosticType error) {
    test(srcs(js), error(error));
  }

  /**
   * Verifies that the compiler generates the given error for the given input.
   *
   * @param js Input
   * @param error Expected error
   */
  protected void testError(String externs, String js, DiagnosticType error) {
    test(externs(externs), srcs(js), error(error));
  }

  /**
   * Verifies that the compiler generates the given error and description for the given input.
   */
  protected void testError(String js, DiagnosticType error, String description) {
    assertThat(error).isNotNull();
    test(srcs(js), error(error).withMessage(description));
  }

  /**
   * Verifies that the compiler generates the given error and description for the given input.
   */
  protected void testError(Sources srcs, Diagnostic error) {
    assertThat(error.level).isEqualTo(CheckLevel.ERROR);
    test(srcs, error);
  }

  /**
   * Verifies that the compiler generates the given error and description for the given input.
   */
  protected void testError(Externs externs, Sources srcs, Diagnostic error) {
    assertThat(error.level).isEqualTo(CheckLevel.ERROR);
    test(externs, srcs, error);
  }

  /**
   * Verifies that the compiler generates the given error for the given input.
   *
   * @param js Input
   * @param error Expected error
   */
  protected void testError(String[] js, DiagnosticType error) {
    assertThat(error).isNotNull();
    test(srcs(js), error(error));
  }

  /**
   * Verifies that the compiler generates the given warning for the given input.
   */
  protected void testError(List<SourceFile> inputs, DiagnosticType error) {
    assertThat(error).isNotNull();
    test(srcs(inputs), error(error));
  }

  /**
   * Verifies that the compiler generates the given warning for the given input.
   */
  protected void testError(List<SourceFile> inputs, DiagnosticType error, String description) {
    assertThat(error).isNotNull();
    test(srcs(inputs), error(error).withMessage(description));
  }

  /**
   * Verifies that the compiler generates the given warning for the given input.
   *
   * @param js Input
   * @param warning Expected warning
   */
  protected void testWarning(String js, DiagnosticType warning) {
    assertThat(warning).isNotNull();
    test(srcs(js), warning(warning));
  }

  /**
   * Verifies that the compiler generates the given warning for the given input.
   *
   * @param srcs Inputs
   * @param warning Expected warning
   */
  protected void testWarning(Sources srcs, Diagnostic warning) {
    assertThat(warning.level).isEqualTo(CheckLevel.WARNING);
    test(srcs, warning);
  }

  /**
   * Verifies that the compiler generates the given warning for the given input.
   *
   * @param externs The externs
   * @param srcs The input
   * @param warning Expected warning
   */
  protected void testWarning(Externs externs, Sources srcs, Diagnostic warning) {
    assertThat(warning.level).isEqualTo(CheckLevel.WARNING);
    test(externs, srcs, warning);
  }

  /**
   * Verifies that the compiler generates the given warning for the given input.
   *
   * @param js Input
   * @param warning Expected warning
   */
  protected void testWarning(String externs, String js, DiagnosticType warning) {
    assertThat(warning).isNotNull();
    test(externs(externs), srcs(js), warning(warning));
  }

  /**
   * Verifies that the compiler generates the given warning for the given input.
   */
  protected void testWarning(String[] js, DiagnosticType warning) {
    assertThat(warning).isNotNull();
    test(srcs(js), warning(warning));
  }

  /**
   * Verifies that the compiler generates the given warning for the given input.
   */
  protected void testWarning(List<SourceFile> inputs, DiagnosticType warning) {
    assertThat(warning).isNotNull();
    test(srcs(inputs), warning(warning));
  }

  /**
   * Verifies that the compiler generates the given warning and description for the given input.
   *
   * @param js Input
   * @param warning Expected warning
   */
  protected void testWarning(String js, DiagnosticType warning, String description) {
    assertThat(warning).isNotNull();
    test(srcs(js), warning(warning).withMessage(description));
  }

  /**
   * Verifies that the compiler generates the given warning for the given input.
   */
  protected void testWarning(List<SourceFile> inputs, DiagnosticType warning, String description) {
    assertThat(warning).isNotNull();
    test(srcs(inputs), warning(warning).withMessage(description));
  }

  /**
   * Verifies that the compiler generates the given warning for the given input.
   */
  protected void testWarning(
      String externs, String js, DiagnosticType warning, String description) {
    assertThat(warning).isNotNull();
    test(externs(externs), srcs(js), warning(warning).withMessage(description));
  }

  /**
   * Verifies that the compiler generates no warnings for the given input.
   *
   * @param js Input
   */
  protected void testNoWarning(String js) {
    test(srcs(js));
  }

  /**
   * Verifies that the compiler generates no warnings for the given input.
   */
  protected void testNoWarning(Sources srcs) {
    test(srcs);
  }

  /**
   * Verifies that the compiler generates no warnings for the given input.
   */
  public void testNoWarning(String externs, String js) {
    test(externs(externs), srcs(js));
  }

  /**
   * Verifies that the compiler generates no warnings for the given input.
   */
  public void testNoWarning(String[] js) {
    test(srcs(js));
  }

  /**
   * Verifies that the compiler generates no warnings for the given input.
   */
  public void testNoWarning(List<SourceFile> inputs) {
    test(srcs(inputs));
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output
   * and (optionally) that an expected warning is issued. Or, if an error is
   * expected, this method just verifies that the error is encountered.
   *
   * @param js Input
   * @param expected Expected output, or null if an error is expected
   * @param diagnostic Expected warning or error
   */
  protected void test(String js, String expected, Diagnostic diagnostic) {
    test(externs(externsInputs), srcs(js), expected(expected), diagnostic);
  }

  protected void testInternal(
      Externs externs,
      Sources inputs,
      Expected expected,
      List<Diagnostic> diagnostics,
      List<Postcondition> postconditions) {

    Compiler compiler = createCompiler();
    lastCompiler = compiler;

    CompilerOptions options = getOptions();

    if (inputs instanceof FlatSources) {
      options.setCheckTypes(parseTypeInfo || this.typeCheckEnabled);
      compiler.init(externs.externs, ((FlatSources) inputs).sources, options);
    } else {
      compiler.initModules(externsInputs, ((ModuleSources) inputs).modules, getOptions());
    }

    if (this.typeCheckEnabled) {
      BaseJSTypeTestCase.addNativeProperties(compiler.getTypeRegistry());
    }

    testInternal(compiler, inputs, expected, diagnostics, postconditions);
  }

  private static ImmutableList<SourceFile> maybeCreateSources(String name, String srcText) {
    if (srcText != null) {
      return ImmutableList.of(SourceFile.fromCode(name, srcText));
    }
    return null;
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output.
   *
   * @param js Inputs
   * @param expected Expected JS output
   */
  protected void test(String[] js, String[] expected) {
    test(srcs(js), expected(expected));
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output.
   *
   * @param js Inputs
   * @param expected Expected JS output
   */
  protected void test(List<SourceFile> js, List<SourceFile> expected) {
    test(srcs(js), expected(expected));
  }

  private static List<SourceFile> createSources(String name, String... sources) {
    if (sources == null) {
      return null;
    }
    return createSources(name, ImmutableList.copyOf(sources));
  }

  private static List<SourceFile> createSources(String name, List<String> sources) {
    if (sources == null) {
      return null;
    }
    List<SourceFile> expectedSources = new ArrayList<>();
    for (int i = 0; i < sources.size(); i++) {
      expectedSources.add(SourceFile.fromCode(name + i, sources.get(i)));
    }
    return expectedSources;
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output.
   *
   * @param modules Module inputs
   * @param expected Expected JS outputs (one per module)
   */
  protected void test(JSModule[] modules, String[] expected) {
    test(srcs(modules), expected(expected));
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output
   * and (optionally) that an expected warning is issued. Or, if an error is
   * expected, this method just verifies that the error is encountered.
   *
   * @param modules Module inputs
   * @param expected Expected JS outputs (one per module)
   * @param diagnostic the warning or error expected
   */
  protected void test(
      JSModule[] modules, String[] expected, Diagnostic diagnostic) {
    test(srcs(modules), expected(expected), diagnostic);
  }

  /**
   * Verifies that the compiler pass's JS output is the same as its input.
   *
   * @param js Input and output
   */
  protected void testSame(String js) {
    test(srcs(js), expected(js));
  }

  /**
   * Verifies that the compiler pass's JS output is the same as its input
   * and (optionally) that an expected warning is issued.
   *
   * @param js Input and output
   * @param warning Expected warning, or null if no warning is expected
   */
  protected void testSame(String js, DiagnosticType warning) {
    test(srcs(js), expected(js), warning(warning));
  }

  /**
   * Verifies that the compiler pass's JS output is the same as its input.
   *
   * @param js Inputs and outputs
   */
  protected void testSame(String[] js) {
    test(srcs(js), expected(js));
  }

  /**
   * Verifies that the compiler pass's JS output is the same as its input.
   *
   * @param js Inputs and outputs
   */
  protected void testSame(List<SourceFile> js) {
    test(srcs(js), expected(js));
  }

  /**
   * Verifies that the compiler pass's JS output is the same as its input,
   * and emits the given warning.
   *
   * @param js Inputs and outputs
   * @param warning Expected warning, or null if no warning is expected
   */
  protected void testSameWarning(String[] js, DiagnosticType warning) {
    test(srcs(js), expected(js), warning(warning));
  }

  /**
   * Verifies that the compiler pass's JS output is the same as the input.
   *
   * @param modules Module inputs
   */
  protected void testSame(JSModule[] modules) {
    test(srcs(modules), expected(modules));
  }

  /**
   * Verifies that the compiler pass's JS output is the same as the input.
   *
   * @param modules Module inputs
   * @param warning A warning, or null for no expected warning.
   */
  protected void testSame(JSModule[] modules, DiagnosticType warning) {
    test(srcs(modules), expected(modules), warning(warning));
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output and (optionally) that
   * an expected warning is issued. Or, if an error is expected, this method just verifies that the
   * error is encountered.
   *
   * @param compiler A compiler that has been initialized via {@link Compiler#init}
   * @param inputsObj Input source files
   * @param expectedObj Expected outputs
   * @param diagnostics Expected warning/error diagnostics
   */
  private void testInternal(
      Compiler compiler,
      Sources inputsObj, // TODO remove this parameter
      Expected expectedObj,
      List<Diagnostic> diagnostics,
      List<Postcondition> postconditions) {
    List<SourceFile> inputs =
        (inputsObj instanceof FlatSources)
            ? ((FlatSources) inputsObj).sources
            : null;
    List<SourceFile> expected = expectedObj != null ? expectedObj.expected : null;
    List<Diagnostic> expectedErrors =
        diagnostics.stream().filter(d -> d.level == CheckLevel.ERROR).collect(toImmutableList());
    List<Diagnostic> expectedWarnings =
        diagnostics.stream().filter(d -> d.level == CheckLevel.WARNING).collect(toImmutableList());
    checkState(
        expectedErrors.isEmpty() || expectedWarnings.isEmpty(),
        "Cannot expect both errors and warnings.");
    checkState(
        expectedErrors.isEmpty() || expected == null,
        "Cannot expect both errors and compiled output.");
    checkState(this.setUpRan, "CompilerTestCase.setUp not run: call super.setUp() from overrides.");
    RecentChange recentChange = new RecentChange();
    compiler.addChangeHandler(recentChange);

    Node root = compiler.parseInputs();

    if (root == null) {
      // Might be an expected parse error.
      assertThat(compiler.getErrors())
          .named("parse errors")
          .asList()
          .comparingElementsUsing(new DiagnosticCorrespondence())
          .containsExactlyElementsIn(expectedErrors);
      for (Postcondition postcondition : postconditions) {
        postcondition.verify(compiler);
      }
      return;
    }
    if (!expectParseWarningsThisTest) {
      assertWithMessage("Unexpected parser warning(s)").that(compiler.getWarnings()).isEmpty();
    } else {
      assertThat(compiler.getWarningCount()).isGreaterThan(0);
    }

    if (astValidationEnabled) {
      // NOTE: We do not enable type validation here, because type information never exists
      // immediately after parsing.
      (new AstValidator(compiler, scriptFeatureValidationEnabled)).validateRoot(root);
    }
    Node externsRoot = root.getFirstChild();
    Node mainRoot = root.getLastChild();

    // Save the tree for later comparison.
    Node rootClone = root.cloneTree();
    Node externsRootClone = rootClone.getFirstChild();
    Node mainRootClone = rootClone.getLastChild();

    int numRepetitions = getNumRepetitions();
    ErrorManager[] errorManagers = new ErrorManager[numRepetitions];
    int aggregateWarningCount = 0;
    List<JSError> aggregateWarnings = new ArrayList<>();
    boolean hasCodeChanged = false;

    for (int i = 0; i < numRepetitions; ++i) {
      if (compiler.getErrorCount() == 0) {
        errorManagers[i] = new BlackHoleErrorManager();
        compiler.setErrorManager(errorManagers[i]);

        if (polymerPass && i == 0) {
          recentChange.reset();
          new PolymerPass(compiler, 1, PolymerExportPolicy.LEGACY, false)
              .process(externsRoot, mainRoot);
          hasCodeChanged = hasCodeChanged || recentChange.hasCodeChanged();
        }

        if (rewriteClosureCode && i == 0) {
          new ClosureRewriteClass(compiler).process(externsRoot, mainRoot);
          new ClosureRewriteModule(compiler, null, null).process(externsRoot, mainRoot);
          new ScopedAliases(compiler, null, CompilerOptions.NULL_ALIAS_TRANSFORMATION_HANDLER)
              .process(externsRoot, mainRoot);
          hasCodeChanged = hasCodeChanged || recentChange.hasCodeChanged();
        }

        // Only run process closure primitives once, if asked.
        if (closurePassEnabled && i == 0) {
          recentChange.reset();
          new ProcessClosurePrimitives(compiler, null, CheckLevel.ERROR, false)
              .process(externsRoot, mainRoot);
          hasCodeChanged = hasCodeChanged || recentChange.hasCodeChanged();
        }

        // Only run process closure primitives once, if asked.
        if (processCommonJsModules && i == 0) {
          recentChange.reset();
          new ProcessCommonJSModules(compiler).process(externsRoot, mainRoot);
          hasCodeChanged = hasCodeChanged || recentChange.hasCodeChanged();
        }

        if (transpileEnabled && i == 0) {
          recentChange.reset();
          transpileToEs5(compiler, externsRoot, mainRoot);
          hasCodeChanged = hasCodeChanged || recentChange.hasCodeChanged();
        }

        if (!librariesToInject.isEmpty() && i == 0) {
          for (String resourceName : librariesToInject) {
            compiler.ensureLibraryInjected(resourceName, true);
          }
        }

        // Only run the type checking pass once, if asked.
        // Running it twice can cause unpredictable behavior because duplicate
        // objects for the same type are created, and the type system
        // uses reference equality to compare many types.
        if (!runTypeCheckAfterProcessing && typeCheckEnabled && i == 0) {
          TypeCheck check = createTypeCheck(compiler);
          check.processForTesting(externsRoot, mainRoot);
        }

        boolean runNormalization = normalizeEnabled && i == 0;

        if (multistageCompilation && runNormalization) {
          // Only run multistage compilation when normalizing.

          // TODO(rluble): enable multistage compilation when invoking with modules.
          if (inputs != null
              && compiler.getModuleGraph() == null
              && compiler.getModuleGraph().getModuleCount() > 1) {
            compiler =
                CompilerTestCaseUtils.multistageSerializeAndDeserialize(
                    this, compiler, inputs, recentChange);
            root = compiler.getRoot();
            externsRoot = compiler.getExternsRoot();
            mainRoot = compiler.getJsRoot();
            lastCompiler = compiler;
          }
        }

        // Only run the normalize pass once, if asked.
        if (runNormalization) {
          normalizeActualCode(compiler, externsRoot, mainRoot);
        }

        if (inferConsts && i == 0) {
          new InferConsts(compiler).process(externsRoot, mainRoot);
        }

        if (computeSideEffects && i == 0) {
          recentChange.reset();
          PureFunctionIdentifier.Driver mark =
              new PureFunctionIdentifier.Driver(compiler, null);
          mark.process(externsRoot, mainRoot);
          hasCodeChanged = hasCodeChanged || recentChange.hasCodeChanged();
        }

        if (markNoSideEffects && i == 0) {
          MarkNoSideEffectCalls mark = new MarkNoSideEffectCalls(compiler);
          mark.process(externsRoot, mainRoot);
        }

        if (gatherExternPropertiesEnabled && i == 0) {
          (new GatherExternProperties(compiler)).process(externsRoot, mainRoot);
        }

        recentChange.reset();

        ChangeVerifier changeVerifier = null;
        if (checkAstChangeMarking) {
          changeVerifier = new ChangeVerifier(compiler);
          changeVerifier.snapshot(mainRoot);
        }

        getProcessor(compiler).process(externsRoot, mainRoot);

        if (checkAstChangeMarking) {
          // TODO(johnlenz): add support for multiple passes in getProcessor so that we can
          // check the AST marking after each pass runs.
          // Verify that changes to the AST are properly marked on the AST.
          changeVerifier.checkRecordedChanges(mainRoot);
        }

        if (astValidationEnabled) {
          new AstValidator(compiler, scriptFeatureValidationEnabled)
              .setTypeValidationEnabled(typeInfoValidationEnabled)
              .validateRoot(root);
        }
        if (checkLineNumbers) {
          (new LineNumberCheck(compiler)).process(externsRoot, mainRoot);
        }

        if (runTypeCheckAfterProcessing && typeCheckEnabled && i == 0) {
          TypeCheck check = createTypeCheck(compiler);
          check.processForTesting(externsRoot, mainRoot);
        }

        if (checkAccessControls) {
          (new CheckAccessControls(compiler, false)).process(externsRoot, mainRoot);
        }

        hasCodeChanged = hasCodeChanged || recentChange.hasCodeChanged();
        aggregateWarningCount += errorManagers[i].getWarningCount();
        Collections.addAll(aggregateWarnings, compiler.getWarnings());

        if (normalizeEnabled) {
          boolean verifyDeclaredConstants = true;
          new Normalize.VerifyConstants(compiler, verifyDeclaredConstants)
              .process(externsRoot, mainRoot);
        }
      }
    }

    if (expectedErrors.isEmpty()) {
      assertThat(compiler.getErrors()).isEmpty();

      // Verify the symbol table.
      ErrorManager symbolTableErrorManager = new BlackHoleErrorManager();
      compiler.setErrorManager(symbolTableErrorManager);
      Node expectedRoot = null;
      if (expected != null) {
        expectedRoot = parseExpectedJs(expected);
        expectedRoot.detach();
      }

      JSError[] stErrors = symbolTableErrorManager.getErrors();
      if (expectedSymbolTableError != null) {
        assertWithMessage("There should be one error.").that(stErrors.length).isEqualTo(1);
        assertError(stErrors[0]).hasType(expectedSymbolTableError);
      } else {
        assertThat(stErrors).named("symbol table errors").isEmpty();
      }
      if (expectedWarnings.isEmpty()) {
        assertThat(aggregateWarnings).named("aggregate warnings").isEmpty();
      } else {
        assertWithMessage(
                "There should be "
                    + expectedWarnings.size()
                    + " warnings, repeated "
                    + numRepetitions
                    + " time(s). Warnings: \n"
                    + LINE_JOINER.join(aggregateWarnings))
            .that(aggregateWarningCount)
            .isEqualTo(numRepetitions);
        for (int i = 0; i < numRepetitions; i++) {
          assertThat(errorManagers[i].getWarnings())
              .named("compile warnings from repetition " + (i + 1))
              .asList()
              .comparingElementsUsing(new DiagnosticCorrespondence())
              .containsExactlyElementsIn(expectedWarnings);
          for (JSError warning : errorManagers[i].getWarnings()) {
            validateSourceLocation(warning);
          }
        }
      }

      // If we ran normalize on the AST, we must also run normalize on the
      // clone before checking for changes.
      if (normalizeEnabled) {
        normalizeActualCode(compiler, externsRootClone, mainRootClone);
      }

      boolean codeChange = !mainRootClone.isEquivalentWithSideEffectsTo(mainRoot);
      boolean externsChange = !externsRootClone.isEquivalentWithSideEffectsTo(externsRoot);

      // Generally, externs should not be changed by the compiler passes.
      if (externsChange && !allowExternsChanges) {
        String explanation = externsRootClone.checkTreeEquals(externsRoot);
        assertWithMessage(
                "Unexpected changes to externs"
                    + "\nExpected: "
                    + compiler.toSource(externsRootClone)
                    + "\nResult:   "
                    + compiler.toSource(externsRoot)
                    + "\n"
                    + explanation)
            .fail();
      }

      if (!codeChange && !externsChange) {
        assertWithMessage("compiler.reportCodeChange() was called " + "even though nothing changed")
            .that(hasCodeChanged)
            .isFalse();
      } else {
        assertWithMessage(
                "compiler.reportCodeChange() should have been called."
                    + "\nOriginal: "
                    + mainRootClone.toStringTree()
                    + "\nNew: "
                    + mainRoot.toStringTree())
            .that(hasCodeChanged)
            .isTrue();
      }

      if (expected != null) {
        if (!compareSyntheticCode) {
          // remove code in files starting with [synthetic:
          Node scriptRoot = mainRoot.getFirstChild();
          Node child = scriptRoot.getFirstChild();
          while (child != null) {
            Node nextChild = child.getNext();
            String sourceFile = child.getSourceFileName();
            if (sourceFile != null && sourceFile.startsWith(Compiler.SYNTHETIC_CODE_PREFIX)) {
              scriptRoot.removeChild(child);
            }
            child = nextChild;
          }
        }
        if (compareAsTree) {
          String explanation;
          if (compareJsDoc) {
            explanation = expectedRoot.checkTreeEqualsIncludingJsDoc(mainRoot);
          } else {
            explanation = expectedRoot.checkTreeEquals(mainRoot);
          }
          if (explanation != null) {
            String expectedAsSource = compiler.toSource(expectedRoot);
            String mainAsSource = compiler.toSource(mainRoot);
            if (expectedAsSource.equals(mainAsSource)) {
              assertWithMessage("In: " + expectedAsSource + "\n" + explanation).fail();
            } else {
              assertWithMessage(
                      "\nExpected: "
                          + expectedAsSource
                          + "\nResult:   "
                          + mainAsSource
                          + "\n"
                          + explanation)
                  .fail();
            }
          }
        } else {
          String[] expectedSources = new String[expected.size()];
          for (int i = 0; i < expected.size(); ++i) {
            try {
              expectedSources[i] = expected.get(i).getCode();
            } catch (IOException e) {
              throw new RuntimeException("failed to get source code", e);
            }
          }
          assertThat(compiler.toSource(mainRoot)).isEqualTo(Joiner.on("").join(expectedSources));
        }
      }

      // Verify normalization is not invalidated.
      Node normalizeCheckRootClone = root.cloneTree();
      Node normalizeCheckExternsRootClone = normalizeCheckRootClone.getFirstChild();
      Node normalizeCheckMainRootClone = normalizeCheckRootClone.getLastChild();
      new PrepareAst(compiler).process(normalizeCheckExternsRootClone, normalizeCheckMainRootClone);
      String explanation = normalizeCheckMainRootClone.checkTreeEquals(mainRoot);
      assertWithMessage(
              "Node structure normalization invalidated."
                  + "\nExpected: "
                  + compiler.toSource(normalizeCheckMainRootClone)
                  + "\nResult:   "
                  + compiler.toSource(mainRoot)
                  + "\n"
                  + explanation)
          .that(explanation)
          .isNull();

      // TODO(johnlenz): enable this for most test cases.
      // Currently, this invalidates test for while-loops, for-loop
      // initializers, and other naming.  However, a set of code
      // (Closure primitive rewrites, etc) runs before the Normalize pass,
      // so this can't be force on everywhere.
      if (normalizeEnabled) {
        new Normalize(compiler, true)
            .process(normalizeCheckExternsRootClone, normalizeCheckMainRootClone);
        explanation = normalizeCheckMainRootClone.checkTreeEquals(mainRoot);
        assertWithMessage(
                "Normalization invalidated."
                    + "\nExpected: "
                    + compiler.toSource(normalizeCheckMainRootClone)
                    + "\nResult:   "
                    + compiler.toSource(mainRoot)
                    + "\n"
                    + explanation)
            .that(explanation)
            .isNull();
      }
    } else {
      assertThat(compiler.getErrors())
          .named("compile errors")
          .asList()
          .comparingElementsUsing(new DiagnosticCorrespondence())
          .containsExactlyElementsIn(expectedErrors);
      for (JSError error : compiler.getErrors()) {
        validateSourceLocation(error);
        assertWithMessage("Some placeholders in the error message were not replaced")
            .that(error.description)
            .doesNotContainMatch("\\{\\d\\}");
      }
    }
    for (Postcondition postcondition : postconditions) {
      postcondition.verify(compiler);
    }
  }

  private static void transpileToEs5(AbstractCompiler compiler, Node externsRoot, Node codeRoot) {
    List<PassFactory> factories = new ArrayList<>();
    CompilerOptions options = compiler.getOptions();
    GatherModuleMetadata gatherModuleMetadata =
        new GatherModuleMetadata(
            compiler, options.processCommonJSModules, options.moduleResolutionMode);
    factories.add(new PassFactory(PassNames.GATHER_MODULE_METADATA, /* isOneTimePass= */ true) {
      @Override
      protected CompilerPass create(AbstractCompiler compiler) {
        return gatherModuleMetadata;
      }

      @Override
      protected FeatureSet featureSet() {
        return FeatureSet.ES_NEXT;
      }
    });
    TranspilationPasses.addEs6ModulePass(
        factories, new PreprocessorSymbolTable.CachedInstanceFactory());
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    TranspilationPasses.addPreTypecheckTranspilationPasses(factories, options, false);
    TranspilationPasses.addPostCheckTranspilationPasses(factories, options);
    TranspilationPasses.addRewritePolyfillPass(factories);
    for (PassFactory factory : factories) {
      factory.create(compiler).process(externsRoot, codeRoot);
    }
  }

  private void validateSourceLocation(JSError jserror) {
    // Make sure that source information is always provided.
    if (!allowSourcelessWarnings) {
      assertWithMessage("Missing source file name in warning: " + jserror)
          .that(jserror.sourceName != null && !jserror.sourceName.isEmpty())
          .isTrue();
      assertWithMessage("Missing line number in warning: " + jserror)
          .that(-1 != jserror.lineNumber)
          .isTrue();
      assertWithMessage("Missing char number in warning: " + jserror)
          .that(-1 != jserror.getCharno())
          .isTrue();
    }
  }

  private static void normalizeActualCode(Compiler compiler, Node externsRoot, Node mainRoot) {
    Normalize normalize = new Normalize(compiler, false);
    normalize.process(externsRoot, mainRoot);
  }

  protected Node parseExpectedJs(String expected) {
    return parseExpectedJs(
        ImmutableList.of(
            SourceFile.fromCode("expected", expected)));
  }

  /**
   * Parses expected JS inputs and returns the root of the parse tree.
   */
  protected Node parseExpectedJs(List<SourceFile> inputs) {
    Compiler compiler = createCompiler();

    compiler.init(externsInputs, inputs, getOptions());
    Node root = compiler.parseInputs();
    assertWithMessage("Unexpected parse error(s): " + LINE_JOINER.join(compiler.getErrors()))
        .that(root)
        .isNotNull();
    Node externsRoot = root.getFirstChild();
    Node mainRoot = externsRoot.getNext();
    // Only run the normalize pass, if asked.
    if (normalizeEnabled && !compiler.hasErrors()) {
      Normalize normalize = new Normalize(compiler, false);
      normalize.process(externsRoot, mainRoot);
    }

    if (closurePassEnabled && closurePassEnabledForExpected && !compiler.hasErrors()) {
      new ProcessClosurePrimitives(compiler, null, CheckLevel.ERROR, false)
          .process(externsRoot, mainRoot);
    }

    if (rewriteClosureCode) {
      new ClosureRewriteClass(compiler).process(externsRoot, mainRoot);
      new ClosureRewriteModule(compiler, null, null).process(externsRoot, mainRoot);
      new ScopedAliases(compiler, null, CompilerOptions.NULL_ALIAS_TRANSFORMATION_HANDLER)
          .process(externsRoot, mainRoot);
    }

    if (transpileEnabled && !compiler.hasErrors()) {
      transpileToEs5(compiler, externsRoot, mainRoot);
    }
    return mainRoot;
  }

  protected void testExternChanges(String input, String expectedExtern) {
    testExternChanges("", input, expectedExtern);
  }

  protected void testExternChanges(String extern, String input, String expectedExtern) {
    testExternChanges(extern, input, expectedExtern, (DiagnosticType[]) null);
  }

  protected void testExternChanges(String input, String expectedExtern,
      DiagnosticType... warnings) {
    testExternChanges("", input, expectedExtern, warnings);
  }

  protected void testExternChanges(String extern, String input, String expectedExtern,
      DiagnosticType... warnings) {
    Compiler compiler = createCompiler();
    CompilerOptions options = getOptions();
    compiler.init(
        maybeCreateSources("extern", extern),
        maybeCreateSources("input", input),
        options);
    compiler.parseInputs();
    assertThat(compiler.getErrors()).isEmpty();

    Node externsAndJs = compiler.getRoot();
    Node root = externsAndJs.getLastChild();

    Node externs = externsAndJs.getFirstChild();

    Node expected = compiler.parseTestCode(expectedExtern);
    assertThat(compiler.getErrors()).isEmpty();

    (getProcessor(compiler)).process(externs, root);

    if (compareAsTree) {
      // Ignore and remove empty externs, so that if we start with an empty extern and only add
      // to the synthetic externs, we can still enable compareAsTree.
      if (externs.hasMoreThanOneChild()) {
        for (Node c : externs.children()) {
          if (!c.hasChildren()) {
            c.detach();
          }
        }
      }

      // Expected output parsed without implied block.
      checkState(externs.isRoot());
      checkState(compareJsDoc);
      checkState(
          externs.hasOneChild(), "Compare as tree only works when output has a single script.");
      externs = externs.getFirstChild();
      String explanation = expected.checkTreeEqualsIncludingJsDoc(externs);
      assertWithMessage(
              ""
                  + "\nExpected: "
                  + compiler.toSource(expected)
                  + "\nResult:   "
                  + compiler.toSource(externs)
                  + "\n"
                  + explanation)
          .that(explanation)
          .isNull();
    } else {
      String externsCode = compiler.toSource(externs);
      String expectedCode = compiler.toSource(expected);
      assertThat(externsCode).isEqualTo(expectedCode);
    }

    if (warnings != null) {
      String warningMessage = "";
      for (JSError actualWarning : compiler.getWarnings()) {
        warningMessage += actualWarning.description + "\n";
      }
      assertWithMessage("There should be " + warnings.length + " warnings. " + warningMessage)
          .that(compiler.getWarningCount())
          .isEqualTo(warnings.length);
      for (int i = 0; i < warnings.length; i++) {
        DiagnosticType warning = warnings[i];
        assertWithMessage(warningMessage)
            .that(compiler.getWarnings()[i].getType())
            .isEqualTo(warning);
      }
    }
  }

  /**
   * Generates a list of modules from a list of inputs, such that each module
   * depends on the module before it.
   */
  protected static JSModule[] createModuleChain(String... inputs) {
    return createModuleChain(Arrays.asList(inputs), "i", ".js");
  }

  protected static JSModule[] createModuleChain(
      List<String> inputs, String fileNamePrefix, String fileNameSuffix) {
    JSModule[] modules = createModules(inputs, fileNamePrefix, fileNameSuffix);
    for (int i = 1; i < modules.length; i++) {
      modules[i].addDependency(modules[i - 1]);
    }
    return modules;
  }

  /**
   * Generates a list of modules from a list of inputs, such that each module
   * depends on the first module.
   */
  protected static JSModule[] createModuleStar(String... inputs) {
    JSModule[] modules = createModules(inputs);
    for (int i = 1; i < modules.length; i++) {
      modules[i].addDependency(modules[0]);
    }
    return modules;
  }

  /**
   * Generates a list of modules from a list of inputs, such that modules
   * form a bush formation. In a bush formation, module 2 depends
   * on module 1, and all other modules depend on module 2.
   */
  protected static JSModule[] createModuleBush(String... inputs) {
    checkState(inputs.length > 2);
    JSModule[] modules = createModules(inputs);
    for (int i = 1; i < modules.length; i++) {
      modules[i].addDependency(modules[i == 1 ? 0 : 1]);
    }
    return modules;
  }

  /**
   * Generates a list of modules from a list of inputs, such that modules
   * form a tree formation. In a tree formation, module N depends on
   * module `floor(N/2)`, So the modules form a balanced binary tree.
   */
  protected static JSModule[] createModuleTree(String... inputs) {
    JSModule[] modules = createModules(inputs);
    for (int i = 1; i < modules.length; i++) {
      modules[i].addDependency(modules[(i - 1) / 2]);
    }
    return modules;
  }

  /**
   * Generates a list of modules from a list of inputs. Does not generate any
   * dependencies between the modules.
   */
  protected static JSModule[] createModules(String... inputs) {
    return createModules(Arrays.asList(inputs), "i", ".js");
  }

  protected static JSModule[] createModules(
      List<String> inputs, String fileNamePrefix, String fileNameSuffix) {
    JSModule[] modules = new JSModule[inputs.size()];
    for (int i = 0; i < inputs.size(); i++) {
      JSModule module = modules[i] = new JSModule("m" + i);
      module.add(SourceFile.fromCode(fileNamePrefix + i + fileNameSuffix, inputs.get(i)));
    }
    return modules;
  }

  protected Compiler createCompiler() {
    Compiler compiler = new Compiler();
    compiler.setFeatureSet(acceptedLanguage.toFeatureSet());
    if (!webpackModulesById.isEmpty()) {
      compiler.initWebpackMap(ImmutableMap.copyOf(webpackModulesById));
    }
    return compiler;
  }

  protected void setExpectedSymbolTableError(DiagnosticType type) {
    this.expectedSymbolTableError = type;
  }

  /** Finds the first matching qualified name node in post-traversal order. */
  protected final Node findQualifiedNameNode(final String name, Node root) {
    return findQualifiedNameNodes(name, root).get(0);
  }

  /** Finds all the matching qualified name nodes in post-traversal order. */
  protected final List<Node> findQualifiedNameNodes(final String name, Node root) {
    final List<Node> matches = new ArrayList<>();
    NodeUtil.visitPostOrder(
        root,
        new NodeUtil.Visitor() {
          @Override
          public void visit(Node n) {
            if (n.matchesQualifiedName(name)) {
              matches.add(n);
            }
          }
        });
    return matches;
  }

  /**
   * Returns a node that defines the given qualified name. The returned node
   * should have a valid type defined on it, if type checking occurred.  Will
   * return null if no definition is found.  Only nodes in the top-level script
   * are traversed.
   */
  protected static Node findDefinition(Compiler compiler, String name) {
    for (Node node = compiler.getJsRoot().getFirstFirstChild();
         node != null; node = node.getNext()) {
      switch (node.getToken()) {
        case FUNCTION:
          if (name.equals(node.getFirstChild().getString())) {
            return node;
          }
          break;
        case VAR:
        case CONST:
        case LET:
          if (name.equals(node.getFirstChild().getString())) {
            return node.getFirstChild();
          }
          break;
        case EXPR_RESULT:
          if (node.getFirstChild().isAssign()
              && node.getFirstFirstChild().matchesQualifiedName(name)) {
            return node.getFirstFirstChild();
          }
          break;
        default:
          break;
      }
    }
    return null;
  }

  /** A Compiler that records requested runtime libraries, rather than injecting. */
  protected static class NoninjectingCompiler extends Compiler {

    NoninjectingCompiler(ErrorManager em) {
      super(em);
    }

    NoninjectingCompiler() {
      super();
    }

    protected final Set<String> injected = new HashSet<>();

    @Override
    Node ensureLibraryInjected(String library, boolean force) {
      injected.add(library);
      return null;
    }

    @Override
    @GwtIncompatible
    public void saveState(OutputStream outputStream) throws IOException {
      super.saveState(outputStream);
      ObjectOutputStream out = new ObjectOutputStream(outputStream);
      out.writeObject(injected);
    }

    @SuppressWarnings("unchecked")
    @Override
    @GwtIncompatible
    public void restoreState(InputStream inputStream) throws IOException, ClassNotFoundException {
      super.restoreState(inputStream);
      ObjectInputStream in = new ObjectInputStream(inputStream);
      injected.clear();
      injected.addAll((Set<String>) in.readObject());
    }
  }

  public static String lines(String line) {
    return line;
  }

  public static String lines(String... lines) {
    return LINE_JOINER.join(lines);
  }

  protected static Sources srcs(String srcText) {
    return new FlatSources(maybeCreateSources("testcode",  srcText));
  }

  protected static Sources srcs(String... srcTexts) {
    return new FlatSources(createSources("input", srcTexts));
  }

  protected static Sources srcs(List<SourceFile> files) {
    return new FlatSources(files);
  }

  protected Sources srcs(SourceFile... files) {
    return new FlatSources(Arrays.asList(files));
  }

  protected static Sources srcs(JSModule[] modules) {
    return new ModuleSources(modules);
  }

  protected static Expected expected(String srcText) {
    return new Expected(maybeCreateSources("expected",  srcText));
  }

  protected static Expected expected(String... srcTexts) {
    return new Expected(createSources("expected", srcTexts));
  }

  protected static Expected expected(List<SourceFile> files) {
    return new Expected(files);
  }

  protected Expected expected(SourceFile... files) {
    return new Expected(Arrays.asList(files));
  }

  protected static Expected expected(JSModule[] modules) {
    List<String> expectedSrcs = new ArrayList<>();
    for (JSModule module : modules) {
      for (CompilerInput input : module.getInputs()) {
        try {
          expectedSrcs.add(input.getSourceFile().getCode());
        } catch (IOException e) {
          throw new RuntimeException("ouch", e);
        }
      }
    }
    return expected(expectedSrcs.toArray(new String[0]));
  }

  protected static Externs externs(String externSrc) {
    return new Externs(maybeCreateSources("externs",  externSrc));
  }

  protected static Externs externs(String... srcTexts) {
    return new Externs(createSources("externs", srcTexts));
  }

  protected static Externs externs(SourceFile... externs) {
    // Copy SourceFile objects to prevent the externs bit from polluting tests.
    return new Externs(
        Arrays.stream(externs)
            .map(
                f -> {
                  try {
                    return SourceFile.fromCode(f.getName(), f.getCode());
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                })
            .collect(ImmutableList.toImmutableList()));
  }

  protected static Externs externs(List<SourceFile> files) {
    return new Externs(files);
  }

  protected static Diagnostic warning(DiagnosticType type) {
    return new WarningDiagnostic(type);
  }

  protected static Diagnostic error(DiagnosticType type) {
    return new ErrorDiagnostic(type);
  }

  protected static Postcondition postcondition(Postcondition postcondition) {
    return postcondition;
  }

  protected void testSame(TestPart... parts) {
    testInternal(Iterables.concat(Arrays.asList(parts), ImmutableList.of(EXPECTED_SAME)));
  }

  protected void test(TestPart... parts) {
    testInternal(Arrays.asList(parts));
  }

  private void testInternal(Iterable<TestPart> parts) {
    // TODO(johnlenz): make "ignore" and "nothing" explicit.
    Externs externs = null;
    Sources srcs = null;
    Expected expected = null;
    List<Diagnostic> diagnostics = new ArrayList<>();
    List<Postcondition> postconditions = new ArrayList<>();
    for (TestPart part : parts) {
      if (part instanceof Externs) {
        checkState(externs == null);
        externs = (Externs) part;
      } else if (part instanceof Sources) {
        checkState(srcs == null);
        srcs = (Sources) part;
      } else if (part instanceof Expected) {
        checkState(expected == null);
        expected = (Expected) part;
      } else if (part instanceof Diagnostic) {
        diagnostics.add((Diagnostic) part);
      } else if (part instanceof Postcondition) {
        postconditions.add((Postcondition) part);
      } else {
        throw new IllegalStateException("unexepected " + part.getClass().getName());
      }
    }
    if (EXPECTED_SAME.equals(expected)) {
      expected = fromSources(srcs);
    }
    if (externs == null) {
      externs = externs(externsInputs);
    }
    testInternal(externs, srcs, expected, diagnostics, postconditions);
  }

  private static Expected fromSources(Sources srcs) {
    if (srcs instanceof FlatSources) {
      return expected(((FlatSources) srcs).sources);
    } else if (srcs instanceof ModuleSources) {
      ModuleSources modules = ((ModuleSources) srcs);
      return expected(modules.modules.toArray(new JSModule[0]));
    } else {
      throw new IllegalStateException("unexpected");
    }
  }

  // TODO(johnlenz): make this a abstract class with a private constructor
  /**
   * Marker interface for configuration parameters of a test invocation.
   * This is essentially a closed union type consisting of four subtypes:
   * (1) Externs, (2) Expected, (3) Sources, and (4) Diagnostic.  Sharing
   * a common marker interface, allows specifying any combination of these
   * types to the 'test' method, and makes it very clear what function
   * each parameter serves (since the first three can all be represented
   * as simple strings).  Moreover, it reduces the combinatorial explosion
   * of different ways to express the parts (a single string, a list of
   * SourceFiles, a list of modules, a DiagnosticType, a DiagnosticType with
   * an expected message, etc).
   *
   * Note that this API is intended to be a medium-term temporary API while
   * developing and migrating to a more fluent assertion style.
   */
  protected interface TestPart {}

  protected static final class Expected implements TestPart {
    final List<SourceFile> expected;

    Expected(List<SourceFile> files) {
      this.expected = files;
    }
  }
  // TODO(sdh): make a shorter function to get this - e.g. same(), expectSame(), sameOutput() ?
  // TODO(sdh): also make an ignoreOutput() and noOutput() ?
  private static final Expected EXPECTED_SAME = new Expected(null);

  protected abstract static class Sources implements TestPart {}

  protected static class FlatSources extends Sources {
    final ImmutableList<SourceFile> sources;

    FlatSources(List<SourceFile> files) {
      sources = ImmutableList.copyOf(files);
    }
  }

  protected static final class ModuleSources extends Sources {
    final ImmutableList<JSModule> modules;

    ModuleSources(JSModule[] modules) {
      this.modules = ImmutableList.copyOf(modules);
    }
  }

  protected static final class Externs implements TestPart {
    final List<SourceFile> externs;

    Externs(List<SourceFile> files) {
      externs = files;
      if (files != null) {
        for (SourceFile s : files) {
          s.setKind(SourceKind.EXTERN);
        }
      }
    }
  }

  protected static class Diagnostic implements TestPart {
    final CheckLevel level;
    final DiagnosticType diagnostic;
    final NamedPredicate<String> messagePredicate;

    Diagnostic(
        CheckLevel level, DiagnosticType diagnostic, NamedPredicate<String> messagePredicate) {
      this.level = level;
      this.diagnostic = diagnostic;
      this.messagePredicate = messagePredicate;
    }

    private boolean matches(JSError error) {
      return diagnostic == error.getType()
          && (messagePredicate == null || messagePredicate.apply(error.description));
    }

    private String formatDiff(JSError error) {
      if (diagnostic != error.getType()) {
        return "diagnostic type " + error.getType().key + " did not match";
      }
      return "message \"" + error.description + "\" was not " + messagePredicate;
    }

    protected Diagnostic withMessage(final String expected) {
      checkState(messagePredicate == null);
      return new Diagnostic(
          level, diagnostic, NamedPredicate.of(expected::equals, "\"" + expected + "\""));
    }

    public Diagnostic withMessageContaining(final String substring) {
      checkState(messagePredicate == null);
      return new Diagnostic(
          level,
          diagnostic,
          NamedPredicate.of(
              message -> message.contains(substring), "containing \"" + substring + "\""));
    }

    @Override
    public String toString() {
      return diagnostic.key + (messagePredicate != null ? " with message " + messagePredicate : "");
    }
  }

  private static class ErrorDiagnostic extends Diagnostic {
    ErrorDiagnostic(DiagnosticType diagnostic) {
      super(CheckLevel.ERROR, diagnostic, null);
    }
  }

  private static class WarningDiagnostic extends Diagnostic {
    WarningDiagnostic(DiagnosticType diagnostic) {
      super(CheckLevel.WARNING, diagnostic, null);
    }
  }

  private static class DiagnosticCorrespondence extends Correspondence<JSError, Diagnostic> {
    @Override
    public boolean compare(JSError actual, Diagnostic expected) {
      return expected.matches(actual);
    }

    @Override
    public String formatDiff(JSError actual, Diagnostic expected) {
      return expected.formatDiff(actual);
    }

    @Override
    public String toString() {
      return "is a JSError matching";
    }
  }

  private static class NamedPredicate<T> implements Predicate<T> {
    final Predicate<T> delegate;
    final String name;

    static <T> NamedPredicate<T> of(Predicate<T> delegate, String name) {
      return new NamedPredicate<>(delegate, name);
    }

    NamedPredicate(Predicate<T> delegate, String name) {
      this.delegate = delegate;
      this.name = name;
    }

    @Override
    public boolean apply(T arg) {
      return delegate.apply(arg);
    }

    @Override
    public String toString() {
      return name;
    }
  }

  protected interface Postcondition extends TestPart {
    void verify(Compiler compiler);
  }

  protected static Postcondition expectRuntimeLibraries(String... expected) {
    return (compiler) -> {
      assertThat(((NoninjectingCompiler) compiler).injected).containsExactlyElementsIn(expected);
    };
  }
}
