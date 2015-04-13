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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.testing.BlackHoleErrorManager;
import com.google.javascript.jscomp.type.ReverseAbstractInterpreter;
import com.google.javascript.jscomp.type.SemanticReverseAbstractInterpreter;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.testing.BaseJSTypeTestCase;

import junit.framework.TestCase;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
public abstract class CompilerTestCase extends TestCase  {

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

  /** True iff type checking pass runs before pass being tested. */
  private boolean typeCheckEnabled = false;

  /** Error level reported by type checker. */
  private CheckLevel typeCheckLevel;

  /** Whether to the test compiler pass before the type check. */
  protected boolean runTypeCheckAfterProcessing = false;

  /** Whether to scan externs for property names. */
  private boolean gatherExternPropertiesEnabled = false;

  /** Whether the Normalize pass runs before pass being tested. */
  private boolean normalizeEnabled = false;

  /** Whether the expected JS strings should be normalized. */
  private boolean normalizeExpected = false;

  /** Whether we run InferConsts before checking. */
  private boolean enableInferConsts = false;

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

  /**
   * Whether externs changes should be allowed for this pass.
   */
  private boolean allowExternsChanges = false;

  /**
   * Whether the AST should be validated.
   */
  private boolean astValidationEnabled = true;

  private String filename = "testcode";

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
    this.externsInputs = ImmutableList.of(
        SourceFile.fromCode("externs", externs));
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

  @Override protected void tearDown() throws Exception {
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

    // This doesn't affect whether checkSymbols is run--it just affects
    // whether variable warnings are filtered.
    options.setCheckSymbols(true);

    options.setWarningLevel(DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.WARNING);
    options.setWarningLevel(
        DiagnosticGroups.INVALID_CASTS, CheckLevel.WARNING);
    options.setCodingConvention(getCodingConvention());
    return options;
  }

  protected CodingConvention getCodingConvention() {
    return new GoogleCodingConvention();
  }

  public void setFilename(String filename) {
    this.filename = filename;
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
   * What language to allow in source parsing.
   */
  protected void setAcceptedLanguage(LanguageMode acceptedLanguage) {
    this.acceptedLanguage = acceptedLanguage;
  }

  /**
   * Whether to run InferConsts before passes
   */
  protected void enableInferConsts(boolean enable) {
    this.enableInferConsts = enable;
  }

  /**
   * Whether to allow externs changes.
   */
  protected void allowExternsChanges(boolean allowExternsChanges) {
    this.allowExternsChanges = allowExternsChanges;
  }

  /**
   * Perform type checking before running the test pass. This will check
   * for type errors and annotate nodes with type information.
   *
   * @param level the level of severity to report for type errors
   *
   * @see TypeCheck
   */
  public void enableTypeCheck(CheckLevel level) {
    typeCheckEnabled  = true;
    typeCheckLevel = level;
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
  void disableTypeCheck() {
    typeCheckEnabled  = false;
  }

  /**
   * Process closure library primitives.
   */
  // TODO(nicksantos): Fix other passes to use this when appropriate.
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
   * Perform AST normalization before running the test pass, and anti-normalize
   * after running it.
   *
   * @see Normalize
   */
  protected void enableNormalize() {
    enableNormalize(true);
  }

  /**
   * Perform AST normalization before running the test pass, and anti-normalize
   * after running it.
   *
   * @param normalizeExpected Whether to perform normalization on the
   * expected JS result.
   * @see Normalize
   */
  protected void enableNormalize(boolean normalizeExpected) {
    normalizeEnabled = true;
    this.normalizeExpected = normalizeExpected;
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
  // TODO(nicksantos): This pass doesn't get run anymore. It should be removed.
  void enableMarkNoSideEffects() {
    markNoSideEffects  = true;
  }

  /**
   * Run the PureFunctionIdentifier pass before running the test pass.
   *
   * @see MarkNoSideEffectCalls
   */
  void enableComputeSideEffects() {
    computeSideEffects  = true;
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
  private static TypeCheck createTypeCheck(Compiler compiler,
      CheckLevel level) {
    ReverseAbstractInterpreter rai =
        new SemanticReverseAbstractInterpreter(compiler.getTypeRegistry());

    return new TypeCheck(compiler, rai, compiler.getTypeRegistry(), level);
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
    assertNotNull("Must assert an error", error);
    test(js, null, error, null);
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
  public void test(String js, String expected, DiagnosticType error,
                   DiagnosticType warning, String description) {
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
  public void test(String js, String expected,
                   DiagnosticType error, DiagnosticType warning) {
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
  public void test(String externs, String js, String expected,
                   DiagnosticType error, DiagnosticType warning) {
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
  public void test(String externs, String js, String expected,
                   DiagnosticType error, DiagnosticType warning,
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
  public void test(List<SourceFile> externs, String js, String expected,
                   DiagnosticType error,
                   DiagnosticType warning, String description) {
    test(externs, ImmutableList.of(SourceFile.fromCode(filename, js)),
        expected, error, warning, description);
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
  private void test(List<SourceFile> externs, List<SourceFile> js, String expected,
                    DiagnosticType error,
                    DiagnosticType warning, String description) {
    Compiler compiler = createCompiler();
    lastCompiler = compiler;

    CompilerOptions options = getOptions();

    options.setLanguageIn(acceptedLanguage);
    // Note that in this context, turning on the checkTypes option won't
    // actually cause the type check to run.
    options.setCheckTypes(parseTypeInfo);
    compiler.init(externs, js, options);

    BaseJSTypeTestCase.addNativeProperties(compiler.getTypeRegistry());

    test(compiler, maybeCreateArray(expected), error, warning, description);
  }

  private String[] maybeCreateArray(String expected) {
    if (expected != null) {
      return new String[] { expected };
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
   * Verifies that the compiler pass's JS output matches the expected output,
   * or that an expected error is encountered.
   *
   * @param js Inputs
   * @param expected Expected JS output
   * @param error Expected error, or null if no error is expected
   */
  public void test(String[] js, String[] expected, DiagnosticType error) {
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
  public void test(String[] js, String[] expected, DiagnosticType error,
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
   */
  public void test(List<SourceFile> js, List<SourceFile> expected,
                   DiagnosticType error, DiagnosticType warning) {
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
  public void test(String[] js, String[] expected, DiagnosticType error,
                   DiagnosticType warning, String description) {
    List<SourceFile> inputs = Lists.newArrayList();
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
  public void test(List<SourceFile> js, List<SourceFile> expected,
                  DiagnosticType error, DiagnosticType warning, String description) {
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
  public void test(List<SourceFile> inputs, String[] expected, DiagnosticType error,
                   DiagnosticType warning, String description) {
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
  public void test(JSModule[] modules, String[] expected,
      DiagnosticType error) {
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
  public void test(JSModule[] modules, String[] expected,
                   DiagnosticType error, DiagnosticType warning) {
    Compiler compiler = createCompiler();
    lastCompiler = compiler;

    compiler.initModules(
        externsInputs, Lists.newArrayList(modules), getOptions());
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
   * @param js Input and output
   * @param diag Expected error or warning, or null if none is expected
   * @param error true if diag is an error, false if it is a warning
   */
  public void testSame(String js, DiagnosticType diag, boolean error) {
    if (error) {
      test(js, null, diag, null);
    } else {
      test(js, js, null, diag);
    }
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
  public void testSame(
      String externs, String js, DiagnosticType diag, boolean error) {
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
  public void testSame(String externs, String js, DiagnosticType warning,
                       String description) {
    testSame(externs, js, warning, description, false);
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
  public void testSame(String externs, String js, DiagnosticType type,
                       String description, boolean error) {
    List<SourceFile> externsInputs = ImmutableList.of(
        SourceFile.fromCode("externs", externs));
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
  protected void test(Compiler compiler, String[] expected,
                      DiagnosticType error, DiagnosticType warning) {
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
  private void test(Compiler compiler, String[] expected,
                    DiagnosticType error, DiagnosticType warning,
                    String description) {
    if (expected == null) {
      test(compiler, (List<SourceFile>) null, error, warning, description);
    } else {
      List<SourceFile> inputs = Lists.newArrayList();
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
  private void test(Compiler compiler, List<SourceFile> expected,
                    DiagnosticType error, DiagnosticType warning,
                    String description) {
    RecentChange recentChange = new RecentChange();
    compiler.addChangeHandler(recentChange);

    Node root = compiler.parseInputs();
    assertNotNull("Unexpected parse error(s): " + Joiner.on("\n").join(compiler.getErrors()), root);
    if (!expectParseWarningsThisTest) {
      assertEquals("Unexpected parse warnings(s): " + Joiner.on("\n").join(compiler.getWarnings()),
          0, compiler.getWarnings().length);
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
    List<JSError> aggregateWarnings = Lists.newArrayList();
    boolean hasCodeChanged = false;

    assertFalse("Code should not change before processing",
        recentChange.hasCodeChanged());

    for (int i = 0; i < numRepetitions; ++i) {
      if (compiler.getErrorCount() == 0) {
        errorManagers[i] = new BlackHoleErrorManager();
        compiler.setErrorManager(errorManagers[i]);

        // Only run process closure primitives once, if asked.
        if (closurePassEnabled && i == 0) {
          recentChange.reset();
          new ProcessClosurePrimitives(compiler, null, CheckLevel.ERROR, false)
              .process(null, mainRoot);
          hasCodeChanged = hasCodeChanged || recentChange.hasCodeChanged();
        }

        if (rewriteClosureCode && i == 0) {
          new ClosureRewriteClass(compiler).process(null, mainRoot);
          new ClosureRewriteModule(compiler).process(null, mainRoot);
          new ScopedAliases(compiler, null, CompilerOptions.NULL_ALIAS_TRANSFORMATION_HANDLER)
              .process(null, mainRoot);
          hasCodeChanged = hasCodeChanged || recentChange.hasCodeChanged();
        }

        // Only run the type checking pass once, if asked.
        // Running it twice can cause unpredictable behavior because duplicate
        // objects for the same type are created, and the type system
        // uses reference equality to compare many types.
        if (!runTypeCheckAfterProcessing && typeCheckEnabled && i == 0) {
          TypeCheck check = createTypeCheck(compiler, typeCheckLevel);
          check.processForTesting(externsRoot, mainRoot);
        }

        // Only run the normalize pass once, if asked.
        if (normalizeEnabled && i == 0) {
          normalizeActualCode(compiler, externsRoot, mainRoot);
        }

        if (enableInferConsts && i == 0) {
          new InferConsts(compiler).process(externsRoot, mainRoot);
        }

        if (computeSideEffects && i == 0) {
          PureFunctionIdentifier.Driver mark =
              new PureFunctionIdentifier.Driver(compiler, null, false);
          mark.process(externsRoot, mainRoot);
        }

        if (markNoSideEffects && i == 0) {
          MarkNoSideEffectCalls mark = new MarkNoSideEffectCalls(compiler);
          mark.process(externsRoot, mainRoot);
        }

        if (gatherExternPropertiesEnabled && i == 0) {
          (new GatherExternProperties(compiler))
              .process(externsRoot, mainRoot);
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
          TypeCheck check = createTypeCheck(compiler, typeCheckLevel);
          check.processForTesting(externsRoot, mainRoot);
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
      assertEquals(
          "Unexpected error(s): " + Joiner.on("\n").join(compiler.getErrors()),
          0, compiler.getErrorCount());

      // Verify the symbol table.
      ErrorManager symbolTableErrorManager = new BlackHoleErrorManager();
      compiler.setErrorManager(symbolTableErrorManager);
      Node expectedRoot = null;
      if (expected != null) {
        expectedRoot = parseExpectedJs(expected);
        expectedRoot.detachFromParent();
      }

      JSError[] stErrors = symbolTableErrorManager.getErrors();
      if (expectedSymbolTableError != null) {
        assertEquals("There should be one error.", 1, stErrors.length);
        assertThat(stErrors[0].getType()).isEqualTo(expectedSymbolTableError);
      } else {
        assertEquals("Unexpected symbol table error(s): " +
            Joiner.on("\n").join(stErrors),
            0, stErrors.length);
      }

      if (warning == null) {
        assertEquals(
            "Unexpected warning(s): " + Joiner.on("\n").join(aggregateWarnings),
            0, aggregateWarningCount);
      } else {
        assertEquals("There should be one warning, repeated " + numRepetitions
            + " time(s). Warnings: " + aggregateWarnings, numRepetitions, aggregateWarningCount);
        for (int i = 0; i < numRepetitions; ++i) {
          JSError[] warnings = errorManagers[i].getWarnings();
          JSError actual = warnings[0];
          assertThat(actual.getType()).isEqualTo(warning);

          // Make sure that source information is always provided.
          if (!allowSourcelessWarnings) {
            assertTrue("Missing source file name in warning",
                actual.sourceName != null && !actual.sourceName.isEmpty());
            assertTrue("Missing line number in warning",
                -1 != actual.lineNumber);
            assertTrue("Missing char number in warning",
                -1 != actual.getCharno());
          }

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

      boolean codeChange = !mainRootClone.isEquivalentTo(mainRoot);
      boolean externsChange = !externsRootClone.isEquivalentTo(externsRoot);

      // Generally, externs should not be changed by the compiler passes.
      if (externsChange && !allowExternsChanges) {
        String explanation = externsRootClone.checkTreeEquals(externsRoot);
        fail("Unexpected changes to externs" +
            "\nExpected: " + compiler.toSource(externsRootClone) +
            "\nResult:   " + compiler.toSource(externsRoot) +
            "\n" + explanation);
      }

      if (!codeChange && !externsChange) {
        assertFalse(
            "compiler.reportCodeChange() was called " +
            "even though nothing changed",
            hasCodeChanged);
      } else {
        assertTrue("compiler.reportCodeChange() should have been called."
            + "\nOriginal: " + mainRootClone.toStringTree()
            + "\nNew: " + mainRoot.toStringTree(), hasCodeChanged);
      }

      // Check correctness of the changed-scopes-only traversal
      NodeUtil.verifyScopeChanges(mtoc, mainRoot, false, compiler);

      if (expected != null) {
        if (compareAsTree) {
          String explanation;
          if (compareJsDoc) {
            explanation = expectedRoot.checkTreeEqualsIncludingJsDoc(mainRoot);
          } else {
            explanation = expectedRoot.checkTreeEquals(mainRoot);
          }
          assertNull(
              "\nExpected: " + compiler.toSource(expectedRoot) +
              "\nResult:   " + compiler.toSource(mainRoot) +
              "\n" + explanation, explanation);
        } else if (expected != null) {
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
      Node normalizeCheckExternsRootClone =
          normalizeCheckRootClone.getFirstChild();
      Node normalizeCheckMainRootClone = normalizeCheckRootClone.getLastChild();
      new PrepareAst(compiler).process(
          normalizeCheckExternsRootClone, normalizeCheckMainRootClone);
      String explanation =
          normalizeCheckMainRootClone.checkTreeEquals(mainRoot);
      assertNull("Node structure normalization invalidated." +
          "\nExpected: " +
          compiler.toSource(normalizeCheckMainRootClone) +
          "\nResult:   " + compiler.toSource(mainRoot) +
          "\n" + explanation, explanation);

      // TODO(johnlenz): enable this for most test cases.
      // Currently, this invalidates test for while-loops, for-loop
      // initializers, and other naming.  However, a set of code
      // (Closure primitive rewrites, etc) runs before the Normalize pass,
      // so this can't be force on everywhere.
      if (normalizeEnabled) {
        new Normalize(compiler, true).process(
            normalizeCheckExternsRootClone, normalizeCheckMainRootClone);
        explanation =  normalizeCheckMainRootClone.checkTreeEquals(mainRoot);
        assertNull("Normalization invalidated." +
            "\nExpected: " +
            compiler.toSource(normalizeCheckMainRootClone) +
            "\nResult:   " + compiler.toSource(mainRoot) +
            "\n" + explanation, explanation);
      }
    } else {
      assertNull("expected must be null if error != null", expected);
      String errors = "";
      for (JSError actualError : compiler.getErrors()) {
        errors += actualError.description + "\n";
      }
      assertEquals("There should be one error. " + errors,
          1, compiler.getErrorCount());
      assertEquals(errors, error, compiler.getErrors()[0].getType());

      if (warning != null) {
        String warnings = "";
        for (JSError actualError : compiler.getWarnings()) {
          warnings += actualError.description + "\n";
        }
        assertEquals("There should be one warning. " + warnings,
            1, compiler.getWarningCount());
        assertEquals(warnings, warning, compiler.getWarnings()[0].getType());
      }
    }
  }

  private void normalizeActualCode(
      Compiler compiler, Node externsRoot, Node mainRoot) {
    Normalize normalize = new Normalize(compiler, false);
    normalize.process(externsRoot, mainRoot);
  }

  /**
   * Parses expected JS inputs and returns the root of the parse tree.
   */
  protected Node parseExpectedJs(String[] expected) {
    List<SourceFile> inputs = Lists.newArrayList();
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
    assertNotNull("Unexpected parse error(s): " + Joiner.on("\n").join(compiler.getErrors()), root);
    Node externsRoot = root.getFirstChild();
    Node mainRoot = externsRoot.getNext();
    // Only run the normalize pass, if asked.
    if (normalizeEnabled && normalizeExpected && !compiler.hasErrors()) {
      Normalize normalize = new Normalize(compiler, false);
      normalize.process(externsRoot, mainRoot);
    }

    if (closurePassEnabled && closurePassEnabledForExpected && !compiler.hasErrors()) {
      new ProcessClosurePrimitives(compiler, null, CheckLevel.ERROR, false)
          .process(null, mainRoot);
    }
    return mainRoot;
  }

  protected void testExternChanges(
      String input, String expectedExtern) {
    testExternChanges("", input, expectedExtern);
  }

  protected void testExternChanges(
      String extern, String input, String expectedExtern) {
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
    assertThat(compiler.hasErrors()).isFalse();

    (getProcessor(compiler)).process(externs, root);

    if (compareAsTree) {
      // Expected output parsed without implied block.
      Preconditions.checkState(externs.isBlock());
      Preconditions.checkState(compareJsDoc);
      Preconditions.checkState(externs.hasOneChild(),
          "Compare as tree only works when output has a single script.");
      externs = externs.getFirstChild();
      String explanation = expected.checkTreeEqualsIncludingJsDoc(externs);
      assertNull(
          "\nExpected: " + compiler.toSource(expected) +
          "\nResult:   " + compiler.toSource(externs) +
          "\n" + explanation, explanation);
    } else {
      String externsCode = compiler.toSource(externs);
      String expectedCode = compiler.toSource(expected);
      assertThat(externsCode).isEqualTo(expectedCode);
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
    JSModule[] modules = createModules(inputs);
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
  static JSModule[] createModuleBush(String ... inputs) {
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
  static JSModule[] createModuleTree(String ... inputs) {
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
    JSModule[] modules = new JSModule[inputs.length];
    for (int i = 0; i < inputs.length; i++) {
      JSModule module = modules[i] = new JSModule("m" + i);
      module.add(SourceFile.fromCode("i" + i, inputs[i]));
    }
    return modules;
  }

  Compiler createCompiler() {
    Compiler compiler = new Compiler();
    return compiler;
  }

  protected void setExpectedSymbolTableError(DiagnosticType type) {
    this.expectedSymbolTableError = type;
  }

  /** Finds the first matching qualified name node in post-traversal order. */
  protected final Node findQualifiedNameNode(final String name, Node root) {
    final List<Node> matches = Lists.newArrayList();
    NodeUtil.visitPostOrder(root,
        new NodeUtil.Visitor() {
          @Override public void visit(Node n) {
            if (name.equals(n.getQualifiedName())) {
              matches.add(n);
            }
          }
        },
        Predicates.<Node>alwaysTrue());
    return matches.get(0);
  }
}
