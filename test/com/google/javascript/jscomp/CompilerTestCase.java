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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.CodeChangeHandler.RecentChange;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.type.ReverseAbstractInterpreter;
import com.google.javascript.jscomp.type.SemanticReverseAbstractInterpreter;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.testing.BaseJSTypeTestCase;

import junit.framework.TestCase;

import java.io.IOException;
import java.util.List;

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
  private final List<SourceFile> externsInputs;

  /** Whether to compare input and output as trees instead of strings */
  private final boolean compareAsTree;

  /** Whether to parse type info from JSDoc comments */
  protected boolean parseTypeInfo;

  /** Whether we check warnings without source information. */
  private boolean allowSourcelessWarnings = false;

  /** True iff closure pass runs before pass being tested. */
  private boolean closurePassEnabled = false;

  /** True iff type checking pass runs before pass being tested. */
  private boolean typeCheckEnabled = false;

  /** Error level reported by type checker. */
  private CheckLevel typeCheckLevel;

  /** Whether the Normalize pass runs before pass being tested. */
  private boolean normalizeEnabled = false;

  /** Whether the expected JS strings should be normalized. */
  private boolean normalizeExpected = false;

  /** Whether to check that all line number information is preserved. */
  private boolean checkLineNumbers = true;

  /**
   * An expected symbol table error. Only useful for testing the
   * symbol table error-handling.
   */
  private DiagnosticType expectedSymbolTableError = null;

  /**
   * Whether the MarkNoSideEffectsCalls pass runs before the pass being tested
   */
  private boolean markNoSideEffects = false;

  /** The most recently used Compiler instance. */
  private Compiler lastCompiler;

  /**
   * Whether to acceptES5 source.
   */
  private boolean acceptES5 = true;

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
    if (this.acceptES5) {
      options.setLanguageIn(LanguageMode.ECMASCRIPT5);
    }

    // This doesn't affect whether checkSymbols is run--it just affects
    // whether variable warnings are filtered.
    options.checkSymbols = true;

    options.setWarningLevel(
        DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.WARNING);
    options.setWarningLevel(
        DiagnosticGroups.CAST, CheckLevel.WARNING);
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
   * Whether to allow ECMASCRIPT5 source parsing.
   */
  protected void enableEcmaScript5(boolean acceptES5) {
    this.acceptES5 = acceptES5;
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
  void enableClosurePass() {
    closurePassEnabled = true;
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
  void enableMarkNoSideEffects() {
    markNoSideEffects  = true;
  }

  /**
   * Whether to allow Validate the AST after each run of the pass.
   */
  protected void enableAstValidation(boolean validate) {
    astValidationEnabled = validate;
  }

  /** Returns a newly created TypeCheck. */
  private static TypeCheck createTypeCheck(Compiler compiler,
      CheckLevel level) {
    ReverseAbstractInterpreter rai =
        new SemanticReverseAbstractInterpreter(compiler.getCodingConvention(),
            compiler.getTypeRegistry());

    return new TypeCheck(compiler, rai, compiler.getTypeRegistry(),
        level, CheckLevel.OFF);
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output.
   *
   * @param js Input
   * @param expected Expected JS output
   */
  public void test(String js, String expected) {
    test(js, expected, (DiagnosticType) null);
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output,
   * or that an expected error is encountered.
   *
   * @param js Input
   * @param expected Expected output, or null if an error is expected
   * @param error Expected error, or null if no error is expected
   */
  public void test(String js, String expected, DiagnosticType error) {
    test(js, expected, error, null);
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
    List<SourceFile> externsInputs = ImmutableList.of(
        SourceFile.fromCode("externs", externs));
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
    Compiler compiler = createCompiler();
    lastCompiler = compiler;

    CompilerOptions options = getOptions();

    if (this.acceptES5) {
      options.setLanguageIn(LanguageMode.ECMASCRIPT5);
    }
    // Note that in this context, turning on the checkTypes option won't
    // actually cause the type check to run.
    options.checkTypes = parseTypeInfo;
    compiler.init(externs, ImmutableList.of(
        SourceFile.fromCode(filename, js)), options);

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
   * @param description The description of the expected warning,
   *      or null if no warning is expected or if the warning's description
   *      should not be examined
   */
  public void test(String[] js, String[] expected, DiagnosticType error,
                   DiagnosticType warning, String description) {
    Compiler compiler = createCompiler();
    lastCompiler = compiler;

    List<SourceFile> inputs = Lists.newArrayList();
    for (int i = 0; i < js.length; i++) {
      inputs.add(SourceFile.fromCode("input" + i, js[i]));
    }
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
      test(js, js, diag);
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
      test(externs, js, js, diag, null);
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
    List<SourceFile> externsInputs = ImmutableList.of(
        SourceFile.fromCode("externs", externs));
    test(externsInputs, js, js, null, warning, description);
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
   * Verifies that the compiler pass's JS output is the same as its input,
   * and emits the given error.
   *
   * @param js Inputs and outputs
   * @param error Expected error, or null if no error is expected
   */
  public void testSame(String[] js, DiagnosticType error) {
    test(js, js, error);
  }

  /**
   * Verifies that the compiler pass's JS output is the same as its input,
   * and emits the given error and warning.
   *
   * @param js Inputs and outputs
   * @param error Expected error, or null if no error is expected
   * @param warning Expected warning, or null if no warning is expected
   */
  public void testSame(
      String[] js, DiagnosticType error, DiagnosticType warning) {
    test(js, js, error, warning);
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
  private void test(Compiler compiler, String[] expected,
                    DiagnosticType error, DiagnosticType warning,
                    String description) {
    RecentChange recentChange = new RecentChange();
    compiler.addChangeHandler(recentChange);

    Node root = compiler.parseInputs();
    assertTrue("Unexpected parse error(s): " +
        Joiner.on("\n").join(compiler.getErrors()), root != null);

    if (astValidationEnabled) {
      (new AstValidator()).validateRoot(root);
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
    List<JSError> aggregateWarnings = Lists.newArrayList();
    boolean hasCodeChanged = false;

    assertFalse("Code should not change before processing",
        recentChange.hasCodeChanged());

    for (int i = 0; i < numRepetitions; ++i) {
      if (compiler.getErrorCount() == 0) {
        errorManagers[i] = new BlackHoleErrorManager(compiler);

        // Only run process closure primitives once, if asked.
        if (closurePassEnabled && i == 0) {
          recentChange.reset();
          new ProcessClosurePrimitives(compiler, null, CheckLevel.ERROR)
              .process(null, mainRoot);
          hasCodeChanged = hasCodeChanged || recentChange.hasCodeChanged();
        }

        // Only run the type checking pass once, if asked.
        // Running it twice can cause unpredictable behavior because duplicate
        // objects for the same type are created, and the type system
        // uses reference equality to compare many types.
        if (typeCheckEnabled && i == 0) {
          TypeCheck check = createTypeCheck(compiler, typeCheckLevel);
          check.processForTesting(externsRoot, mainRoot);
        }

        // Only run the normalize pass once, if asked.
        if (normalizeEnabled && i == 0) {
          normalizeActualCode(compiler, externsRoot, mainRoot);
        }

        if (markNoSideEffects && i == 0) {
          MarkNoSideEffectCalls mark = new MarkNoSideEffectCalls(compiler);
          mark.process(externsRoot, mainRoot);
        }

        recentChange.reset();

        getProcessor(compiler).process(externsRoot, mainRoot);
        if (astValidationEnabled) {
          (new AstValidator()).validateRoot(root);
        }
        if (checkLineNumbers) {
          (new LineNumberCheck(compiler)).process(externsRoot, mainRoot);
        }

        hasCodeChanged = hasCodeChanged || recentChange.hasCodeChanged();
        aggregateWarningCount += errorManagers[i].getWarningCount();
        aggregateWarnings.addAll(Lists.newArrayList(compiler.getWarnings()));

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
      ErrorManager symbolTableErrorManager =
          new BlackHoleErrorManager(compiler);
      Node expectedRoot = null;
      if (expected != null) {
        expectedRoot = parseExpectedJs(expected);
        expectedRoot.detachFromParent();
      }

      JSError[] stErrors = symbolTableErrorManager.getErrors();
      if (expectedSymbolTableError != null) {
        assertEquals("There should be one error.", 1, stErrors.length);
        assertEquals(expectedSymbolTableError, stErrors[0].getType());
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
        assertEquals("There should be one warning, repeated " + numRepetitions +
            " time(s).", numRepetitions, aggregateWarningCount);
        for (int i = 0; i < numRepetitions; ++i) {
          JSError[] warnings = errorManagers[i].getWarnings();
          JSError actual = warnings[0];
          assertEquals(warning, actual.getType());

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
            assertEquals(description, actual.description);
          }
        }
      }

      if (normalizeEnabled) {
        normalizeActualCode(compiler, externsRootClone, mainRootClone);
      }

      boolean codeChange = !mainRootClone.isEquivalentTo(mainRoot);
      boolean externsChange = !externsRootClone.isEquivalentTo(externsRoot);

      // Generally, externs should not be change by the compiler passes.
      if (externsChange && !allowExternsChanges) {
        String explanation = externsRootClone.checkTreeEquals(externsRoot);
        fail("Unexpected changes to externs" +
            "\nExpected: " + compiler.toSource(externsRootClone) +
            "\nResult: " + compiler.toSource(externsRoot) +
            "\n" + explanation);
      }

      if (!codeChange && !externsChange) {
        assertFalse(
            "compiler.reportCodeChange() was called " +
            "even though nothing changed",
            hasCodeChanged);
      } else {
        assertTrue("compiler.reportCodeChange() should have been called",
            hasCodeChanged);
      }

      if (expected != null) {
        if (compareAsTree) {
          String explanation = expectedRoot.checkTreeEquals(mainRoot);
          assertNull("\nExpected: " + compiler.toSource(expectedRoot) +
              "\nResult: " + compiler.toSource(mainRoot) +
              "\n" + explanation, explanation);
        } else if (expected != null) {
          assertEquals(
              Joiner.on("").join(expected), compiler.toSource(mainRoot));
        }
      }

      // Verify normalization is not invalidated.
      Node normalizeCheckRootClone = root.cloneTree();
      Node normalizeCheckExternsRootClone = root.getFirstChild();
      Node normalizeCheckMainRootClone = root.getLastChild();
      new PrepareAst(compiler).process(
          normalizeCheckExternsRootClone, normalizeCheckMainRootClone);
      String explanation =
          normalizeCheckMainRootClone.checkTreeEquals(mainRoot);
      assertNull("Node structure normalization invalidated.\nExpected: " +
          compiler.toSource(normalizeCheckMainRootClone) +
          "\nResult: " + compiler.toSource(mainRoot) +
          "\n" + explanation, explanation);

      // TODO(johnlenz): enable this for most test cases.
      // Currently, this invalidates test for while-loops, for-loop
      // initializers, and other naming.  However, a set of code
      // (FoldConstants, etc) runs before the Normalize pass, so this can't be
      // force on everywhere.
      if (normalizeEnabled) {
        new Normalize(compiler, true).process(
            normalizeCheckExternsRootClone, normalizeCheckMainRootClone);
        explanation =  normalizeCheckMainRootClone.checkTreeEquals(mainRoot);
        assertNull("Normalization invalidated.\nExpected: " +
            compiler.toSource(normalizeCheckMainRootClone) +
            "\nResult: " + compiler.toSource(mainRoot) +
            "\n" + explanation, explanation);
      }
    } else {
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
    Compiler compiler = createCompiler();
    List<SourceFile> inputs = Lists.newArrayList();
    for (int i = 0; i < expected.length; i++) {
      inputs.add(SourceFile.fromCode("expected" + i, expected[i]));
    }
    compiler.init(externsInputs, inputs, getOptions());
    Node root = compiler.parseInputs();
    assertTrue("Unexpected parse error(s): " +
        Joiner.on("\n").join(compiler.getErrors()), root != null);
    Node externsRoot = root.getFirstChild();
    Node mainRoot = externsRoot.getNext();
    // Only run the normalize pass, if asked.
    if (normalizeEnabled && normalizeExpected && !compiler.hasErrors()) {
      Normalize normalize = new Normalize(compiler, false);
      normalize.process(externsRoot, mainRoot);
    }
    return mainRoot;
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

  private static class BlackHoleErrorManager extends BasicErrorManager {
    private BlackHoleErrorManager(Compiler compiler) {
      compiler.setErrorManager(this);
    }

    @Override
    public void println(CheckLevel level, JSError error) {}

    @Override
    public void printSummary() {}
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
