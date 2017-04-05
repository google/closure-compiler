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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.testing.JSErrorSubject.assertError;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.AbstractCompiler.MostRecentTypechecker;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.testing.BlackHoleErrorManager;
import com.google.javascript.jscomp.type.ReverseAbstractInterpreter;
import com.google.javascript.jscomp.type.SemanticReverseAbstractInterpreter;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.testing.BaseJSTypeTestCase;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import junit.framework.TestCase;

/**
 * <p>Base class for testing JS compiler classes that change
 * the node tree of a compiled JS input.</p>
 *
 * <p>Pulls in shared functionality from different test cases. Also supports
 * node tree comparison for input and output (instead of string comparison),
 * which makes it easier to write tests b/c you don't have to get the syntax
 * exactly correct to the spacing.</p>
 *
 */
public abstract class CompilerTestCase extends TestCase {
  protected static final Joiner LINE_JOINER = Joiner.on('\n');

  /** Externs for the test */
  protected final List<SourceFile> externsInputs;

  /** Whether to compare input and output as trees instead of strings */
  private boolean compareAsTree;

  /** Whether to parse type info from JSDoc comments */
  protected boolean parseTypeInfo;

  /** Whether to take JSDoc into account when comparing ASTs. */
  protected boolean compareJsDoc;

  /** Whether we check warnings without source information. */
  private boolean allowSourcelessWarnings = false;

  /** True iff closure pass runs before pass being tested. */
  private boolean closurePassEnabled = false;

  /** Whether the closure pass is run on the expected JS. */
  private boolean closurePassEnabledForExpected = false;

  /** Whether to rewrite Closure code before the test is run. */
  private boolean rewriteClosureCode = false;

  /**
   * If true, run type checking together with the pass being tested. A separate
   * flag controls whether type checking runs before or after the pass.
   */
  private boolean typeCheckEnabled = false;

  /**
   * If true, run NTI together with the pass being tested. A separate
   * flag controls whether NTI runs before or after the pass.
   */
  private boolean newTypeInferenceEnabled = false;

  /** Whether to test the compiler pass before the type check. */
  protected boolean runTypeCheckAfterProcessing = false;

  /** Whether to test the compiler pass before NTI. */
  protected boolean runNTIAfterProcessing = false;

  /** Whether to scan externs for property names. */
  private boolean gatherExternPropertiesEnabled = false;

  /**
   * Whether the Normalize pass runs before pass being tested and
   * whether the expected JS strings should be normalized.
   */
  private boolean normalizeEnabled = false;

  private boolean polymerPass = false;

  /** Whether the tranpilation passes runs before pass being tested. */
  private boolean transpileEnabled = false;

  /** Whether the expected JS strings should be transpiled. */
  private boolean transpileExpected = false;

  /** Whether we run InferConsts before checking. */
  private boolean enableInferConsts = false;

  /** Whether we run CheckAccessControls after the pass being tested. */
  private boolean checkAccessControls = false;

  /** Whether to check that all line number information is preserved. */
  private boolean checkLineNumbers = true;

  /** Whether we expect parse warnings in the current test. */
  private boolean expectParseWarningsThisTest = false;

  /**
   * An expected symbol table error. Only useful for testing the
   * symbol table error-handling.
   */
  private DiagnosticType expectedSymbolTableError = null;

  /**
   * Whether the MarkNoSideEffectsCalls pass runs before the pass being tested
   */
  private boolean markNoSideEffects = false;

  /**
   * Whether the PureFunctionIdentifier pass runs before the pass being tested
   */
  private boolean computeSideEffects = false;

  /** The most recently used Compiler instance. */
  private Compiler lastCompiler;

  /**
   * Whether to accept ES6, ES5 or ES3 source.
   */
  private LanguageMode acceptedLanguage = LanguageMode.ECMASCRIPT5;

  private LanguageMode languageOut = LanguageMode.ECMASCRIPT5;
  
  private boolean emitUseStrict = false;

  /**
   * Whether externs changes should be allowed for this pass.
   */
  private boolean allowExternsChanges = false;

  /**
   * Whether the AST should be validated.
   */
  private boolean astValidationEnabled = true;

  private String filename = "testcode";

  static final String ACTIVE_X_OBJECT_DEF =
      LINE_JOINER.join(
          "/**",
          " * @param {string} progId",
          " * @param {string=} opt_location",
          " * @constructor",
          " * @see http://msdn.microsoft.com/en-us/library/7sw4ddf8.aspx",
          " */",
          "function ActiveXObject(progId, opt_location) {}");

  /** A default set of externs for testing. */
  public static final String DEFAULT_EXTERNS =
      LINE_JOINER.join(
          "/**",
          " * @interface",
          " * @template VALUE",
          " */",
          "function Iterable() {}",
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
          " * @type{number}",
          " */",
          "IArrayLike.prototype.length;",
          "/**",
          " * @constructor",
          " * @param {*=} opt_value",
          " * @return {!Object}",
          " */",
          "function Object(opt_value) {}",
          "/** @return {string} */",
          "Object.prototype.toString = function() {};",
          "/**",
          " * @param {*} propertyName",
          " * @return {boolean}",
          " */",
          "Object.prototype.hasOwnProperty = function(propertyName) {};",
          "/** @type {?Function} */ Object.prototype.constructor;",
          "Object.defineProperties = function(obj, descriptors) {};",
          "/** @constructor",
          " * @param {...*} var_args */ ",
          "function Function(var_args) {}",
          "/** @type {!Function} */ Function.prototype.apply;",
          "/** @type {!Function} */ Function.prototype.bind;",
          "/** @type {!Function} */ Function.prototype.call;",
          "/** @type {number} */",
          "Function.prototype.length;",
          "/** @type {string} */",
          "Function.prototype.name;",
          "/** @constructor",
          " * @param {*=} arg",
          " * @return {string} */",
          "function String(arg) {}",
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
          " * @template T",
          " * @constructor ",
          " * @implements {IArrayLike<T>} ",
          " * @implements {Iterable<T>}",
          " * @param {...*} var_args",
          " * @return {!Array.<?>}",
          " */",
          "function Array(var_args) {}",
          "/** @type {number} */ Array.prototype.length;",
          "/**",
          " * @param {...T} var_args",
          " * @return {number} The new length of the array.",
          " * @this {{length: number}|!Array.<T>}",
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
          // TODO(bradfordcsmith): Copy fields for this from es5.js this when we have test cases
          //     that depend on them.
          "function ObjectPropertyDescriptor() {}",
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
          "/** @type {?} */ var unknown;", // For producing unknowns in tests.
          "/** @typedef {?} */ var symbol;", // TODO(sdh): remove once primitive 'symbol' supported
          "/** @constructor */ function Symbol() {}",
          "/** @const {!symbol} */ Symbol.iterator;",
          ACTIVE_X_OBJECT_DEF);

  /**
   * Constructs a test.
   *
   * @param externs Externs JS as a string
   * @param compareAsTree True to compare output & expected as a node tree.
   *     99% of the time you want to compare as a tree. There are a few
   *     special cases where you don't, like if you want to test the code
   *     printing of "unnatural" syntax trees. For example,
   *
   * <pre>
   * IF
   *   IF
   *     STATEMENT
   * ELSE
   *   STATEMENT
   * </pre>
   */
  protected CompilerTestCase(String externs, boolean compareAsTree) {
    this.externsInputs = ImmutableList.of(SourceFile.fromCode("externs", externs));
    this.compareAsTree = compareAsTree;
    this.parseTypeInfo = false;
    this.compareJsDoc = true;
  }

  /**
   * Constructs a test. Uses AST comparison.
   * @param externs Externs JS as a string
   */
  protected CompilerTestCase(String externs) {
    this(externs, true);
  }

  /**
   * Constructs a test. Uses AST comparison and no externs.
   */
  protected CompilerTestCase() {
    this("", true);
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    expectParseWarningsThisTest = false;
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
    options.setEmitUseStrict(emitUseStrict);
    options.setLanguageOut(languageOut);

    // This doesn't affect whether checkSymbols is run--it just affects
    // whether variable warnings are filtered.
    options.setCheckSymbols(true);

    options.setWarningLevel(DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.WARNING);
    options.setWarningLevel(DiagnosticGroups.INVALID_CASTS, CheckLevel.WARNING);
    options.setCodingConvention(getCodingConvention());
    options.setPolymerVersion(1);
    return options;
  }

  protected CodingConvention getCodingConvention() {
    return new GoogleCodingConvention();
  }

  public void setFilename(String filename) {
    this.filename = filename;
  }

  public String getFilename() {
    return filename;
  }

  /**
   * Returns the number of times the pass should be run before results are
   * verified.
   */
  protected int getNumRepetitions() {
    // Since most compiler passes should be idempotent, we run each pass twice
    // by default.
    return 2;
  }

  /** Expect warnings without source information. */
  void allowSourcelessWarnings() {
    allowSourcelessWarnings = true;
  }

  /** The most recently used JSComp instance. */
  Compiler getLastCompiler() {
    return lastCompiler;
  }

  /**
   * What language to allow in source parsing. Also sets the output language.
   */
  protected void setAcceptedLanguage(LanguageMode lang) {
    setLanguage(lang, lang);
  }

  /**
   * Sets the input and output language modes..
   */
  protected void setLanguage(LanguageMode langIn, LanguageMode langOut) {
    this.acceptedLanguage = langIn;
    setLanguageOut(langOut);
  }

  protected void setLanguageOut(LanguageMode acceptedLanguage) {
    this.languageOut = acceptedLanguage;
  }

  protected void setEmitUseStrict(boolean emitUseStrict) {
	  this.emitUseStrict = emitUseStrict;
  }

  /**
   * Whether to run InferConsts before passes
   */
  protected void enableInferConsts(boolean enable) {
    this.enableInferConsts = enable;
  }

  /**
   * Whether to run CheckAccessControls after the pass being tested (and checking types).
   */
  protected void enableCheckAccessControls(boolean enable) {
    this.checkAccessControls = enable;
  }

  /**
   * Whether to allow externs changes.
   */
  protected void allowExternsChanges(boolean allowExternsChanges) {
    this.allowExternsChanges = allowExternsChanges;
  }

  public void enablePolymerPass() {
    polymerPass = true;
  }

  /**
   * Perform type checking before running the test pass. This will check
   * for type errors and annotate nodes with type information.
   *
   * @see TypeCheck
   */
  public void enableTypeCheck() {
    typeCheckEnabled = true;
  }

  // Run the new type inference after the test pass. Useful for testing passes
  // that rewrite the AST prior to typechecking, eg, AngularPass or PolymerPass.
  public void enableNewTypeInference() {
    this.newTypeInferenceEnabled = true;
  }

  /**
   * Check to make sure that line numbers were preserved.
   */
  public void enableLineNumberCheck(boolean newVal) {
    checkLineNumbers = newVal;
  }

  /**
   * Do not run type checking before running the test pass.
   *
   * @see TypeCheck
   */
  public void disableTypeCheck() {
    typeCheckEnabled = false;
  }

  public void disableNewTypeInference() {
    this.newTypeInferenceEnabled = false;
  }

  /**
   * Process closure library primitives.
   */
  protected void enableClosurePass() {
    closurePassEnabled = true;
  }

  protected void enableClosurePassForExpected() {
    closurePassEnabledForExpected = true;
  }

  /**
   * Rewrite Closure code before the test is run.
   */
  void enableRewriteClosureCode() {
    rewriteClosureCode = true;
  }

  /**
   * Don't rewrite Closure code before the test is run.
   */
  void disableRewriteClosureCode() {
    rewriteClosureCode = false;
  }

  /**
   * Perform AST normalization before running the test pass, and anti-normalize
   * after running it.
   *
   * @see Normalize
   */
  protected void enableNormalize() {
    this.normalizeEnabled = true;
  }

  /**
   * Perform AST transpilation before running the test pass.
   */
  protected void enableTranspile() {
    enableTranspile(true);
  }

  /**
   * Perform AST transpilation before running the test pass.
   *
   * @param transpileExpected Whether to perform transpilation on the
   * expected JS result.
   */
  protected void enableTranspile(boolean transpileExpected) {
    transpileEnabled = true;
    this.transpileExpected = transpileExpected;
  }

  /**
   * Don't perform AST normalization before running the test pass.
   * @see Normalize
   */
  protected void disableNormalize() {
    normalizeEnabled = false;
  }

  /**
   * Run the MarkSideEffectCalls pass before running the test pass.
   *
   * @see MarkNoSideEffectCalls
   */
  void enableMarkNoSideEffects() {
    markNoSideEffects = true;
  }

  /**
   * Run the PureFunctionIdentifier pass before running the test pass.
   *
   * @see MarkNoSideEffectCalls
   */
  void enableComputeSideEffects() {
    computeSideEffects = true;
  }

  /**
   * Scan externs for properties that should not be renamed.
   */
  void enableGatherExternProperties() {
    gatherExternPropertiesEnabled = true;
  }

  /**
   * Whether to allow Validate the AST after each run of the pass.
   */
  protected void enableAstValidation(boolean validate) {
    astValidationEnabled = validate;
  }

  /**
   * Whether to compare the expected output as a tree or string.
   */
  protected void enableCompareAsTree(boolean compareAsTree) {
    this.compareAsTree = compareAsTree;
  }

  /** Whether we should ignore parse warnings for the current test method. */
  protected void setExpectParseWarningsThisTest() {
    expectParseWarningsThisTest = true;
  }

  /** Returns a newly created TypeCheck. */
  private static TypeCheck createTypeCheck(Compiler compiler) {
    ReverseAbstractInterpreter rai =
        new SemanticReverseAbstractInterpreter(compiler.getTypeRegistry());
    compiler.setMostRecentTypechecker(MostRecentTypechecker.OTI);
    return new TypeCheck(compiler, rai, compiler.getTypeRegistry());
  }

  private static void runNewTypeInference(Compiler compiler, Node externs, Node js) {
    GlobalTypeInfo gti = compiler.getSymbolTable();
    gti.process(externs, js);
    NewTypeInference nti = new NewTypeInference(compiler);
    nti.process(externs, js);
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output.
   *
   * @param js Input
   * @param expected Expected JS output
   */
  public void test(String js, String expected) {
    test(js, expected, null, null);
  }

  /**
   * Verifies that the compiler generates the given error for the given input.
   *
   * @param js Input
   * @param error Expected error
   */
  public void testError(String js, DiagnosticType error) {
    assertNotNull(error);
    test(js, null, error, null);
  }

  /**
   * Verifies that the compiler generates the given error and description for the given input.
   */
  public void testError(String js, DiagnosticType error, String description) {
    assertNotNull(error);
    test(js, null, error, null, description);
  }

  /**
   * Verifies that the compiler generates the given error for the given input.
   *
   * @param js Input
   * @param error Expected error
   */
  public void testError(String[] js, DiagnosticType error) {
    assertNotNull(error);
    test(js, null, error, null);
  }

  /**
   * Verifies that the compiler generates the given warning for the given input.
   *
   * @param js Input
   * @param warning Expected warning
   */
  public void testWarning(String js, DiagnosticType warning) {
    assertNotNull(warning);
    test(js, null, null, warning);
  }

  /**
   * Verifies that the compiler generates the given warning for the given input.
   */
  public void testWarning(String[] js, DiagnosticType warning) {
    assertNotNull(warning);
    test(js, null, null, warning);
  }

  /**
   * Verifies that the compiler generates the given warning and description for the given input.
   *
   * @param js Input
   * @param warning Expected warning
   */
  public void testWarning(String js, DiagnosticType warning, String description) {
    assertNotNull(warning);
    test(js, null, null, warning, description);
  }

  /**
   * Verifies that the compiler generates no warnings for the given input.
   *
   * @param js Input
   */
  public void testNoWarning(String js) {
    test(js, null, null, null);
  }

  /**
   * Verifies that the compiler generates no warnings for the given input.
   */
  public void testNoWarning(String[] js) {
    test(js, null, null, null);
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output,
   * or that an expected error is encountered.
   *
   * @param js Input
   * @param expected Expected output, or null if an error is expected
   * @param error Expected error, or null if no error is expected
   * @param warning Expected warning, or null if no warning is expected
   * @param description The content of the error expected
   */
  public void test(
      String js,
      String expected,
      DiagnosticType error,
      DiagnosticType warning,
      String description) {
    test(externsInputs, js, expected, error, warning, description);
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output
   * and (optionally) that an expected warning is issued. Or, if an error is
   * expected, this method just verifies that the error is encountered.
   *
   * @param js Input
   * @param expected Expected output, or null if an error is expected
   * @param error Expected error, or null if no error is expected
   * @param warning Expected warning, or null if no warning is expected
   */
  public void test(String js, String expected, DiagnosticType error, DiagnosticType warning) {
    test(externsInputs, js, expected, error, warning, null);
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output
   * and (optionally) that an expected warning is issued. Or, if an error is
   * expected, this method just verifies that the error is encountered.
   *
   * @param externs Externs input
   * @param js Input
   * @param expected Expected output, or null if an error is expected
   * @param error Expected error, or null if no error is expected
   * @param warning Expected warning, or null if no warning is expected
   */
  public void test(
      String externs, String js, String expected, DiagnosticType error, DiagnosticType warning) {
    test(externs, js, expected, error, warning, null);
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output
   * and (optionally) that an expected warning is issued. Or, if an error is
   * expected, this method just verifies that the error is encountered.
   *
   * @param externs Externs input
   * @param js Input
   * @param expected Expected output, or null if an error is expected
   * @param error Expected error, or null if no error is expected
   * @param warning Expected warning, or null if no warning is expected
   * @param description The description of the expected warning,
   *      or null if no warning is expected or if the warning's description
   *      should not be examined
   */
  public void test(
      String externs,
      String js,
      String expected,
      DiagnosticType error,
      DiagnosticType warning,
      String description) {
    SourceFile externsFile = SourceFile.fromCode("externs", externs);
    externsFile.setIsExtern(true);
    List<SourceFile> externsInputs = ImmutableList.of(externsFile);
    test(externsInputs, js, expected, error, warning, description);
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output
   * and (optionally) that an expected warning is issued. Or, if an error is
   * expected, this method just verifies that the error is encountered.
   *
   * @param externs Externs inputs
   * @param js Input
   * @param expected Expected output, or null if an error is expected
   * @param error Expected error, or null if no error is expected
   * @param warning Expected warning, or null if no warning is expected
   * @param description The description of the expected warning,
   *      or null if no warning is expected or if the warning's description
   *      should not be examined
   */
  public void test(
      List<SourceFile> externs,
      String js,
      String expected,
      DiagnosticType error,
      DiagnosticType warning,
      String description) {
    test(
        externs,
        ImmutableList.of(SourceFile.fromCode(filename, js)),
        expected,
        error,
        warning,
        description);
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output
   * and (optionally) that an expected warning is issued. Or, if an error is
   * expected, this method just verifies that the error is encountered.
   *
   * @param externs Externs inputs
   * @param js Inputs
   * @param expected Expected output, or null if an error is expected
   * @param error Expected error, or null if no error is expected
   * @param warning Expected warning, or null if no warning is expected
   * @param description The description of the expected warning,
   *     or null if no warning is expected or if the warning's description
   *     should not be examined
   */
  private void test(
      List<SourceFile> externs,
      List<SourceFile> js,
      String expected,
      DiagnosticType error,
      DiagnosticType warning,
      String description) {
    Compiler compiler = createCompiler();
    lastCompiler = compiler;

    CompilerOptions options = getOptions();

    options.setCheckTypes(parseTypeInfo || this.typeCheckEnabled);
    compiler.init(externs, js, options);

    if (this.typeCheckEnabled) {
      BaseJSTypeTestCase.addNativeProperties(compiler.getTypeRegistry());
    }

    test(compiler, maybeCreateArray(expected), error, warning, description);
  }

  private static String[] maybeCreateArray(String expected) {
    if (expected != null) {
      return new String[] {expected};
    }
    return null;
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output.
   *
   * @param js Inputs
   * @param expected Expected JS output
   */
  public void test(String[] js, String[] expected) {
    test(js, expected, null);
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output.
   *
   * @param js Inputs
   * @param expected Expected JS output
   */
  public void test(List<SourceFile> js, List<SourceFile> expected) {
    test(js, expected, null);
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output, or that an expected
   * error is encountered.
   *
   * @param js Inputs
   * @param expected Expected JS output
   * @param error Expected error, or null if no error is expected
   */
  private void test(String[] js, String[] expected, DiagnosticType error) {
    test(js, expected, error, null);
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output,
   * or that an expected error is encountered.
   *
   * @param js Inputs
   * @param expected Expected JS output
   * @param error Expected error, or null if no error is expected
   */
  public void test(List<SourceFile> js, List<SourceFile> expected, DiagnosticType error) {
    test(js, expected, error, null);
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output
   * and (optionally) that an expected warning is issued. Or, if an error is
   * expected, this method just verifies that the error is encountered.
   *
   * @param js Inputs
   * @param expected Expected JS output
   * @param error Expected error, or null if no error is expected
   * @param warning Expected warning, or null if no warning is expected
   */
  public void test(String[] js, String[] expected, DiagnosticType error, DiagnosticType warning) {
    test(js, expected, error, warning, null);
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output
   * and (optionally) that an expected warning is issued. Or, if an error is
   * expected, this method just verifies that the error is encountered.
   *
   * @param js Inputs
   * @param expected Expected JS output
   * @param error Expected error, or null if no error is expected
   * @param warning Expected warning, or null if no warning is expected
   */
  public void test(
      List<SourceFile> js,
      List<SourceFile> expected,
      DiagnosticType error,
      DiagnosticType warning) {
    test(js, expected, error, warning, null);
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output
   * and (optionally) that an expected warning is issued. Or, if an error is
   * expected, this method just verifies that the error is encountered.
   *
   * @param js Inputs
   * @param expected Expected JS output
   * @param error Expected error, or null if no error is expected
   * @param warning Expected warning, or null if no warning is expected
   * @param description The description of the expected warning,
   *      or null if no warning is expected or if the warning's description
   *      should not be examined
   */
  public void test(
      String[] js,
      String[] expected,
      DiagnosticType error,
      DiagnosticType warning,
      String description) {
    List<SourceFile> inputs = new ArrayList<>();
    for (int i = 0; i < js.length; i++) {
      inputs.add(SourceFile.fromCode("input" + i, js[i]));
    }
    test(inputs, expected, error, warning, description);
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output
   * and (optionally) that an expected warning is issued. Or, if an error is
   * expected, this method just verifies that the error is encountered.
   *
   * @param js Inputs
   * @param expected Expected JS output
   * @param error Expected error, or null if no error is expected
   * @param description The description of the expected warning,
   *     or null if no warning is expected or if the warning's description
   *     should not be examined
   */
  public void test(
      List<SourceFile> js,
      List<SourceFile> expected,
      DiagnosticType error,
      DiagnosticType warning,
      String description) {
    Compiler compiler = createCompiler();
    lastCompiler = compiler;

    compiler.init(externsInputs, js, getOptions());
    test(compiler, expected, error, warning, description);
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output
   * and (optionall) that an expected warning is issued. Or, if an error is
   * expected, this method just verifies that the error is encountered.
   *
   * @param inputs Inputs
   * @param expected Expected JS output
   * @param error Expected error, or null if no error is expected
   * @param description The description of the expected warning,
   *     or null if no warning is expected or if the warning's description
   *     should no be examined
   */
  public void test(
      List<SourceFile> inputs,
      String[] expected,
      DiagnosticType error,
      DiagnosticType warning,
      String description) {
    Compiler compiler = createCompiler();
    lastCompiler = compiler;

    compiler.init(externsInputs, inputs, getOptions());
    test(compiler, expected, error, warning, description);
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output.
   *
   * @param modules Module inputs
   * @param expected Expected JS outputs (one per module)
   */
  public void test(JSModule[] modules, String[] expected) {
    test(modules, expected, null);
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output,
   * or that an expected error is encountered.
   *
   * @param modules Module inputs
   * @param expected Expected JS outputs (one per module)
   * @param error Expected error, or null if no error is expected
   */
  public void test(JSModule[] modules, String[] expected, DiagnosticType error) {
    test(modules, expected, error, null);
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output
   * and (optionally) that an expected warning is issued. Or, if an error is
   * expected, this method just verifies that the error is encountered.
   *
   * @param modules Module inputs
   * @param expected Expected JS outputs (one per module)
   * @param error Expected error, or null if no error is expected
   * @param warning Expected warning, or null if no warning is expected
   */
  public void test(
      JSModule[] modules, String[] expected, DiagnosticType error, DiagnosticType warning) {
    Compiler compiler = createCompiler();
    lastCompiler = compiler;

    compiler.initModules(externsInputs, ImmutableList.copyOf(modules), getOptions());
    test(compiler, expected, error, warning);
  }

  /**
   * Verifies that the compiler pass's JS output is the same as its input.
   *
   * @param js Input and output
   */
  public void testSame(String js) {
    test(js, js);
  }

  /**
   * Verifies that the compiler pass's JS output is the same as its input
   * and (optionally) that an expected warning is issued.
   *
   * @param js Input and output
   * @param warning Expected warning, or null if no warning is expected
   */
  public void testSame(String js, DiagnosticType warning) {
    test(js, js, null, warning);
  }

  /**
   * Verifies that the compiler pass's JS output is the same as its input
   * and (optionally) that an expected warning is issued.
   *
   * @param externs Externs input
   * @param js Input and output
   * @param warning Expected warning, or null if no warning is expected
   */
  public void testSame(String externs, String js, DiagnosticType warning) {
    testSame(externs, js, warning, null);
  }

  /**
   * Verifies that the compiler pass's JS output is the same as its input
   * and (optionally) that an expected warning is issued.
   *
   * @param externs Externs input
   * @param js Input and output
   * @param diag Expected error or warning, or null if none is expected
   * @param error true if diag is an error, false if it is a warning
   */
  public void testSame(String externs, String js, DiagnosticType diag, boolean error) {
    if (error) {
      test(externs, js, (String) null, diag, null);
    } else {
      test(externs, js, js, null, diag);
    }
  }

  /**
   * Verifies that the compiler pass's JS output is the same as its input
   * and (optionally) that an expected warning and description is issued.
   *
   * @param externs Externs input
   * @param js Input and output
   * @param warning Expected warning, or null if no warning is expected
   * @param description The description of the expected warning,
   *      or null if no warning is expected or if the warning's description
   *      should not be examined
   */
  public void testSame(String externs, String js, DiagnosticType warning, String description) {
    testSame(externs, js, warning, description, false);
  }

  /**
   * Verifies that the compiler pass's JS output is the same as its input
   * and (optionally) that an expected warning and description is issued.
   *
   * @param externs Externs input
   * @param js Input and output
   * @param type Expected warning or error, or null if no warning is expected
   * @param description The description of the expected warning,
   *      or null if no warning is expected or if the warning's description
   *      should not be examined
   * @param error Whether the "type" parameter represents an error.
   *   (false indicated the type is a warning). Ignored if type is null.
   */
  private void testSame(
      String externs, String js, DiagnosticType type, String description, boolean error) {
    List<SourceFile> externsInputs = ImmutableList.of(SourceFile.fromCode("externs", externs));
    if (error) {
      test(externsInputs, js, null, type, null, description);
    } else {
      test(externsInputs, js, js, null, type, description);
    }
  }

  /**
   * Verifies that the compiler pass's JS output is the same as its input.
   *
   * @param js Inputs and outputs
   */
  public void testSame(String[] js) {
    test(js, js);
  }

  /**
   * Verifies that the compiler pass's JS output is the same as its input.
   *
   * @param js Inputs and outputs
   */
  public void testSame(List<SourceFile> js) {
    test(js, js);
  }

  /**
   * Verifies that the compiler pass's JS output is the same as its input,
   * and emits the given warning.
   *
   * @param js Inputs and outputs
   * @param warning Expected warning, or null if no warning is expected
   */
  public void testSameWarning(String[] js, DiagnosticType warning) {
    test(js, js, null, warning);
  }

  /**
   * Verifies that the compiler pass's JS output is the same as the input.
   *
   * @param modules Module inputs
   */
  public void testSame(JSModule[] modules) {
    testSame(modules, null);
  }

  /**
   * Verifies that the compiler pass's JS output is the same as the input.
   *
   * @param modules Module inputs
   * @param warning A warning, or null for no expected warning.
   */
  public void testSame(JSModule[] modules, DiagnosticType warning) {
    try {
      String[] expected = new String[modules.length];
      for (int i = 0; i < modules.length; i++) {
        expected[i] = "";
        for (CompilerInput input : modules[i].getInputs()) {
          expected[i] += input.getSourceFile().getCode();
        }
      }
      test(modules, expected, null, warning);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output
   * and (optionally) that an expected warning is issued. Or, if an error is
   * expected, this method just verifies that the error is encountered.
   *
   * @param compiler A compiler that has been initialized via
   *     {@link Compiler#init}
   * @param expected Expected output, or null if an error is expected
   * @param error Expected error, or null if no error is expected
   * @param warning Expected warning, or null if no warning is expected
   */
  protected void test(
      Compiler compiler, String[] expected, DiagnosticType error, DiagnosticType warning) {
    test(compiler, expected, error, warning, null);
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output
   * and (optionally) that an expected warning is issued. Or, if an error is
   * expected, this method just verified that the error is encountered.
   *
   * @param compiler A compiler that has been initialized via
   *     {@link Compiler#init}
   * @param expected Expected output, or null if an error is expected
   * @param error Expected error, or null if no error is expected
   * @param warning Expected warning, or null if no warning is expected
   */
  private void test(
      Compiler compiler,
      String[] expected,
      DiagnosticType error,
      DiagnosticType warning,
      String description) {
    if (expected == null) {
      test(compiler, (List<SourceFile>) null, error, warning, description);
    } else {
      List<SourceFile> inputs = new ArrayList<>();
      for (int i = 0; i < expected.length; i++) {
        inputs.add(SourceFile.fromCode("expected" + i, expected[i]));
      }
      test(compiler, inputs, error, warning, description);
    }
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output
   * and (optionally) that an expected warning is issued. Or, if an error is
   * expected, this method just verifies that the error is encountered.
   *
   * @param compiler A compiler that has been initialized via
   *     {@link Compiler#init}
   * @param expected Expected output, or null if an error is expected
   * @param error Expected error, or null if no error is expected
   * @param warning Expected warning, or null if no warning is expected
   * @param description The description of the expected warning,
   *      or null if no warning is expected or if the warning's description
   *      should not be examined
   */
  private void test(
      Compiler compiler,
      List<SourceFile> expected,
      DiagnosticType error,
      DiagnosticType warning,
      String description) {
    Preconditions.checkState(!this.typeCheckEnabled || !this.newTypeInferenceEnabled);
    RecentChange recentChange = new RecentChange();
    compiler.addChangeHandler(recentChange);

    compiler.getOptions().setNewTypeInference(this.newTypeInferenceEnabled);

    Node root = compiler.parseInputs();

    String errorMsg = LINE_JOINER.join(compiler.getErrors());
    if (root == null && expected == null && error != null) {
      // Might be an expected parse error.
      assertWithMessage("Expected one parse error, but got " + errorMsg)
          .that(compiler.getErrorCount())
          .isEqualTo(1);
      JSError actualError = compiler.getErrors()[0];
      assertWithMessage("Unexpected parse error(s): " + errorMsg)
          .that(actualError.getType())
          .isEqualTo(error);
      if (description != null) {
        assertThat(actualError.description).isEqualTo(description);
      }
      return;
    }
    assertWithMessage("Unexpected parse error(s): " + errorMsg).that(root).isNotNull();
    if (!expectParseWarningsThisTest) {
      assertThat(compiler.getWarnings()).named("parser warnings").isEmpty();
    } else {
      assertThat(compiler.getWarningCount()).isGreaterThan(0);
    }

    if (astValidationEnabled) {
      (new AstValidator(compiler)).validateRoot(root);
    }
    Node externsRoot = root.getFirstChild();
    Node mainRoot = root.getLastChild();

    // Save the tree for later comparison.
    Node rootClone = root.cloneTree();
    Node externsRootClone = rootClone.getFirstChild();
    Node mainRootClone = rootClone.getLastChild();
    Map<Node, Node> mtoc = NodeUtil.mapMainToClone(mainRoot, mainRootClone);

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
          new PolymerPass(compiler).process(externsRoot, mainRoot);
          hasCodeChanged = hasCodeChanged || recentChange.hasCodeChanged();
        }

        if (rewriteClosureCode && i == 0) {
          new ClosureRewriteClass(compiler).process(null, mainRoot);
          new ClosureRewriteModule(compiler, null, null).process(null, mainRoot);
          new ScopedAliases(compiler, null, CompilerOptions.NULL_ALIAS_TRANSFORMATION_HANDLER)
              .process(null, mainRoot);
          hasCodeChanged = hasCodeChanged || recentChange.hasCodeChanged();
        }

        // Only run process closure primitives once, if asked.
        if (closurePassEnabled && i == 0) {
          recentChange.reset();
          new ProcessClosurePrimitives(compiler, null, CheckLevel.ERROR, false)
              .process(externsRoot, mainRoot);
          hasCodeChanged = hasCodeChanged || recentChange.hasCodeChanged();
        }

        if (transpileEnabled && i == 0) {
          recentChange.reset();
          transpileToEs5(compiler, externsRoot, mainRoot);
          hasCodeChanged = hasCodeChanged || recentChange.hasCodeChanged();
        }

        // Only run the type checking pass once, if asked.
        // Running it twice can cause unpredictable behavior because duplicate
        // objects for the same type are created, and the type system
        // uses reference equality to compare many types.
        if (!runTypeCheckAfterProcessing && typeCheckEnabled && i == 0) {
          TypeCheck check = createTypeCheck(compiler);
          check.processForTesting(externsRoot, mainRoot);
        } else if (!this.runNTIAfterProcessing
            && this.newTypeInferenceEnabled
            && i == 0) {
          runNewTypeInference(compiler, externsRoot, mainRoot);
        }

        // Only run the normalize pass once, if asked.
        if (normalizeEnabled && i == 0) {
          normalizeActualCode(compiler, externsRoot, mainRoot);
        }

        if (enableInferConsts && i == 0) {
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

        getProcessor(compiler).process(externsRoot, mainRoot);
        if (astValidationEnabled) {
          (new AstValidator(compiler)).validateRoot(root);
        }
        if (checkLineNumbers) {
          (new LineNumberCheck(compiler)).process(externsRoot, mainRoot);
        }

        if (runTypeCheckAfterProcessing && typeCheckEnabled && i == 0) {
          TypeCheck check = createTypeCheck(compiler);
          check.processForTesting(externsRoot, mainRoot);
        } else if (this.runNTIAfterProcessing
            && this.newTypeInferenceEnabled
            && i == 0) {
          runNewTypeInference(compiler, externsRoot, mainRoot);
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

    if (error == null) {
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
        assertEquals("There should be one error.", 1, stErrors.length);
        assertError(stErrors[0]).hasType(expectedSymbolTableError);
      } else {
        assertThat(stErrors).named("symbol table errors").isEmpty();
      }

      if (warning == null) {
        assertThat(aggregateWarnings).isEmpty();
      } else {
        assertEquals(
            "There should be one warning, repeated "
                + numRepetitions
                + " time(s). Warnings: \n"
                + LINE_JOINER.join(aggregateWarnings),
            numRepetitions,
            aggregateWarningCount);
        for (int i = 0; i < numRepetitions; ++i) {
          JSError[] warnings = errorManagers[i].getWarnings();
          JSError actual = warnings[0];
          assertError(actual).hasType(warning);
          validateSourceLocation(actual);

          if (description != null) {
            assertThat(actual.description).isEqualTo(description);
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
        fail(
            "Unexpected changes to externs"
                + "\nExpected: "
                + compiler.toSource(externsRootClone)
                + "\nResult:   "
                + compiler.toSource(externsRoot)
                + "\n"
                + explanation);
      }

      if (!codeChange && !externsChange) {
        assertFalse(
            "compiler.reportCodeChange() was called " + "even though nothing changed",
            hasCodeChanged);
      } else {
        assertTrue(
            "compiler.reportCodeChange() should have been called."
                + "\nOriginal: "
                + mainRootClone.toStringTree()
                + "\nNew: "
                + mainRoot.toStringTree(),
            hasCodeChanged);
      }

      // Check correctness of the changed-scopes-only traversal
      NodeUtil.verifyScopeChanges(mtoc, mainRoot, false);

      if (expected != null) {
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
              fail("In: " + expectedAsSource + "\n" + explanation);
            } else {
              fail("\nExpected: "
                  + expectedAsSource
                  + "\nResult:   "
                  + mainAsSource
                  + "\n" + explanation);
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
      assertNull(
          "Node structure normalization invalidated."
              + "\nExpected: "
              + compiler.toSource(normalizeCheckMainRootClone)
              + "\nResult:   "
              + compiler.toSource(mainRoot)
              + "\n"
              + explanation,
          explanation);

      // TODO(johnlenz): enable this for most test cases.
      // Currently, this invalidates test for while-loops, for-loop
      // initializers, and other naming.  However, a set of code
      // (Closure primitive rewrites, etc) runs before the Normalize pass,
      // so this can't be force on everywhere.
      if (normalizeEnabled) {
        new Normalize(compiler, true)
            .process(normalizeCheckExternsRootClone, normalizeCheckMainRootClone);
        explanation = normalizeCheckMainRootClone.checkTreeEquals(mainRoot);
        assertNull(
            "Normalization invalidated."
                + "\nExpected: "
                + compiler.toSource(normalizeCheckMainRootClone)
                + "\nResult:   "
                + compiler.toSource(mainRoot)
                + "\n"
                + explanation,
            explanation);
      }
    } else {
      assertNull("expected must be null if error != null", expected);
      assertEquals(
          "There should be one error of type '" + error.key + "' but there were: "
          + Arrays.toString(compiler.getErrors()),
          1, compiler.getErrorCount());
      JSError actualError = compiler.getErrors()[0];
      assertEquals(errorMsg, error, actualError.getType());
      validateSourceLocation(actualError);
      if (description != null) {
        assertThat(actualError.description).isEqualTo(description);
      }
      assertWithMessage("Some placeholders in the error message were not replaced")
          .that(actualError.description)
          .doesNotContainMatch("\\{\\d\\}");

      if (warning != null) {
        String warnings = "";
        for (JSError actualWarning : compiler.getWarnings()) {
          warnings += actualWarning.description + "\n";
          assertWithMessage("Some placeholders in the warning message were not replaced")
              .that(actualWarning.description)
              .doesNotContainMatch("\\{\\d\\}");
        }
        assertEquals("There should be one warning. " + warnings, 1, compiler.getWarningCount());
        assertEquals(warnings, warning, compiler.getWarnings()[0].getType());
      }
    }
  }

  private static void transpileToEs5(AbstractCompiler compiler, Node externsRoot, Node codeRoot) {
    List<PassFactory> factories = new ArrayList<>();
    TranspilationPasses.addEs6EarlyPasses(factories);
    TranspilationPasses.addEs6LatePasses(factories);
    TranspilationPasses.addRewritePolyfillPass(factories);
    for (PassFactory factory : factories) {
      factory.create(compiler).process(externsRoot, codeRoot);
    }
  }

  private void validateSourceLocation(JSError jserror) {
    // Make sure that source information is always provided.
    if (!allowSourcelessWarnings) {
      assertTrue(
          "Missing source file name in warning: " + jserror,
          jserror.sourceName != null && !jserror.sourceName.isEmpty());
      assertTrue("Missing line number in warning: " + jserror, -1 != jserror.lineNumber);
      assertTrue("Missing char number in warning: " + jserror, -1 != jserror.getCharno());
    }
  }

  private static void normalizeActualCode(Compiler compiler, Node externsRoot, Node mainRoot) {
    Normalize normalize = new Normalize(compiler, false);
    normalize.process(externsRoot, mainRoot);
  }

  /**
   * Parses expected JS inputs and returns the root of the parse tree.
   */
  protected Node parseExpectedJs(String[] expected) {
    List<SourceFile> inputs = new ArrayList<>();
    for (int i = 0; i < expected.length; i++) {
      inputs.add(SourceFile.fromCode("expected" + i, expected[i]));
    }
    return parseExpectedJs(inputs);
  }

  /**
   * Parses expected JS inputs and returns the root of the parse tree.
   */
  protected Node parseExpectedJs(List<SourceFile> inputs) {
    Compiler compiler = createCompiler();

    compiler.init(externsInputs, inputs, getOptions());
    Node root = compiler.parseInputs();
    assertNotNull("Unexpected parse error(s): " + LINE_JOINER.join(compiler.getErrors()), root);
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

    if (transpileEnabled && transpileExpected && !compiler.hasErrors()) {
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
        ImmutableList.of(SourceFile.fromCode("extern", extern)),
        ImmutableList.of(SourceFile.fromCode("input", input)),
        options);
    compiler.parseInputs();
    assertThat(compiler.hasErrors()).isFalse();

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
      Preconditions.checkState(externs.isRoot());
      Preconditions.checkState(compareJsDoc);
      Preconditions.checkState(
          externs.hasOneChild(), "Compare as tree only works when output has a single script.");
      externs = externs.getFirstChild();
      String explanation = expected.checkTreeEqualsIncludingJsDoc(externs);
      assertNull(""
          + "\nExpected: " + compiler.toSource(expected)
          + "\nResult:   " + compiler.toSource(externs)
          + "\n" + explanation,
          explanation);
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
      assertEquals("There should be " + warnings.length + " warnings. " + warningMessage,
          warnings.length, compiler.getWarningCount());
      for (int i = 0; i < warnings.length; i++) {
        DiagnosticType warning = warnings[i];
        assertEquals(warningMessage, warning, compiler.getWarnings()[i].getType());
      }
    }
  }

  protected Node parseExpectedJs(String expected) {
    return parseExpectedJs(new String[] {expected});
  }

  /**
   * Generates a list of modules from a list of inputs, such that each module
   * depends on the module before it.
   */
  static JSModule[] createModuleChain(String... inputs) {
    return createModuleChain(Arrays.asList(inputs), "i", ".js");
  }

  static JSModule[] createModuleChain(
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
  static JSModule[] createModuleStar(String... inputs) {
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
  static JSModule[] createModuleBush(String... inputs) {
    Preconditions.checkState(inputs.length > 2);
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
  static JSModule[] createModuleTree(String... inputs) {
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
  static JSModule[] createModules(String... inputs) {
    return createModules(Arrays.asList(inputs), "i", ".js");
  }

  static JSModule[] createModules(
      List<String> inputs, String fileNamePrefix, String fileNameSuffix) {
    JSModule[] modules = new JSModule[inputs.size()];
    for (int i = 0; i < inputs.size(); i++) {
      JSModule module = modules[i] = new JSModule("m" + i);
      module.add(SourceFile.fromCode(fileNamePrefix + i + fileNameSuffix, inputs.get(i)));
    }
    return modules;
  }

  Compiler createCompiler() {
    Compiler compiler = new Compiler();
    compiler.setLanguageMode(acceptedLanguage);
    return compiler;
  }

  protected void setExpectedSymbolTableError(DiagnosticType type) {
    this.expectedSymbolTableError = type;
  }

  /** Finds the first matching qualified name node in post-traversal order. */
  public final Node findQualifiedNameNode(final String name, Node root) {
    return findQualifiedNameNodes(name, root).get(0);
  }

  /** Finds all the matching qualified name nodes in post-traversal order. */
  public final List<Node> findQualifiedNameNodes(final String name, Node root) {
    final List<Node> matches = new ArrayList<>();
    NodeUtil.visitPostOrder(
        root,
        new NodeUtil.Visitor() {
          @Override
          public void visit(Node n) {
            if (name.equals(n.getQualifiedName())) {
              matches.add(n);
            }
          }
        },
        Predicates.<Node>alwaysTrue());
    return matches;
  }

  /** A Compiler that records requested runtime libraries, rather than injecting. */
  protected static class NoninjectingCompiler extends Compiler {
    protected final Set<String> injected = new HashSet<>();
    @Override Node ensureLibraryInjected(String library, boolean force) {
      injected.add(library);
      return null;
    }
  }
}
