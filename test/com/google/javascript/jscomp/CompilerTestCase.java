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
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.testing.JSErrorSubject.assertError;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.truth.Correspondence;
import com.google.errorprone.annotations.ForOverride;
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import com.google.javascript.jscomp.AccessorSummary.PropertyAccessKind;
import com.google.javascript.jscomp.AstValidator.TypeInfoValidation;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.jscomp.modules.ModuleMapCreator;
import com.google.javascript.jscomp.parsing.Config.JsDocParsing;
import com.google.javascript.jscomp.serialization.ConvertTypesToColors;
import com.google.javascript.jscomp.serialization.SerializationOptions;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.jscomp.type.ReverseAbstractInterpreter;
import com.google.javascript.jscomp.type.SemanticReverseAbstractInterpreter;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile.SourceKind;
import com.google.javascript.rhino.testing.BaseJSTypeTestCase;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.junit.Before;

/**
 * Base class for testing JS compiler classes that change the node tree of a compiled JS input.
 *
 * <p>Pulls in shared functionality from different test cases. Also supports node tree comparison
 * for input and output (instead of string comparison), which makes it easier to write tests b/c you
 * don't have to get the syntax exactly correct to the spacing.
 */
public abstract class CompilerTestCase {
  private static final Joiner LINE_JOINER = Joiner.on('\n');

  // The file name is included in the AST display string attached to each node.
  // Consistently using the same name for src or externs generated files avoids spurious differences
  // in the diffs displayed when a test case fails.
  // TODO(bradfordcsmith): "testcode" and "externs" are magical strings.
  // Other testing code assumes these names are used and expects them.
  public static final String GENERATED_SRC_NAME = "testcode";
  public static final String GENERATED_EXTERNS_NAME = "externs";

  /**
   * Default externs for the test. Individual calls to test() may provide their own externs instead.
   */
  private final ImmutableList<SourceFile> defaultExternsInputs;

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

  private boolean rewriteModulesAfterTypechecking = true;

  /** Whether to rewrite Closure code before the test is run. */
  private boolean rewriteClosureProvides;

  /**
   * If true, run type checking together with the pass being tested. A separate flag controls
   * whether type checking runs before or after the pass.
   */
  private boolean typeCheckEnabled;

  /**
   * If true, run the ConvertTypesToColors pass, which removes references to JSTypes and converts
   * JSDoc to its simplified optimization form.
   */
  private boolean replaceTypesWithColors;

  /** If true performs the test using multistage compilation. */
  private boolean multistageCompilation;

  /** Whether to test the compiler pass before the type check. */
  private boolean runTypeCheckAfterProcessing;

  /** Whether to scan externs for property names. */
  private boolean gatherExternPropertiesEnabled;

  /** Whether to run {@link ModuleMapCreator} after parsing. */
  private boolean createModuleMap = false;

  /**
   * Whether the Normalize pass runs before pass being tested and whether the expected JS strings
   * should be normalized.
   */
  private boolean normalizeEnabled;

  /** If Normalize is enabled, then run it on the expected output also before comparing. */
  private boolean normalizeExpectedOutputEnabled;

  private boolean polymerPass;

  /** Whether ES module rewriting runs before the pass being tested. */
  private boolean rewriteEsModulesEnabled;

  /** Whether the transpilation passes run before the pass being tested. */
  private boolean transpileEnabled;

  /** Whether we run InferConsts before checking. */
  private boolean inferConsts;

  /** Whether we run CheckAccessControls after the pass being tested. */
  private boolean checkAccessControls;

  /** Whether to check that changed scopes are marked as changed */
  private boolean checkAstChangeMarking;

  /** Whether we expect parse warnings in the current test. */
  private boolean expectParseWarningsInThisTest;

  /** An expected symbol table error. Only useful for testing the symbol table error-handling. */
  private @Nullable DiagnosticType expectedSymbolTableError;

  /** Whether the PureFunctionIdentifier pass runs before the pass being tested */
  private boolean computeSideEffects;

  /** Whether the SourceInformationAnnotator pass runs after parsing */
  private boolean annotateSourceInfo;

  /**
   * The set of accessors declared to exist "somewhere" in the test program.
   *
   * <p>When this field is populated, automatic getter and setter collection is disabled.
   */
  private final LinkedHashMap<String, PropertyAccessKind> declaredAccessors = new LinkedHashMap<>();

  /** The most recently used Compiler instance. */
  private Compiler lastCompiler;

  /** Whether to accept ES6, ES5 or ES3 source. */
  private LanguageMode acceptedLanguage;

  private LanguageMode languageOut;

  private @Nullable Integer browserFeaturesetYear;

  /** How to interpret ES6 module imports */
  private ResolutionMode moduleResolutionMode;

  /** How to parse JS Documentation. */
  private JsDocParsing parseJsDocDocumentation;

  /** Compiler option. */
  private boolean assumeStaticInheritanceIsNotUsed;

  /** Whether externs changes should be allowed for this pass. */
  private boolean allowExternsChanges;

  /** Whether the AST should be validated. */
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

  private final Set<DiagnosticType> ignoredWarnings = new LinkedHashSet<>();

  private final Map<String, String> webpackModulesById = new LinkedHashMap<>();

  private ImmutableMap<String, String> genericNameReplacements = ImmutableMap.of();

  /** Whether {@link #setUp} has run. */
  private boolean setUpRan = false;

  // NOTE: These externs are inserted by VarCheck and should not be input as externs for tests.
  // It is provided here for tests that assert on output externs in cases where pulling in the
  // extra externs is unavoidable.
  protected static final String VAR_CHECK_EXTERNS =
      Joiner.on("").join(Iterables.transform(VarCheck.REQUIRED_SYMBOLS, s -> "var " + s + ";\n"));

  /**
   * A minimal set of externs, consisting of only those needed for the typechecker not to blow up.
   */
  protected static final String MINIMAL_EXTERNS =
      new TestExternsBuilder()
          .addArray()
          .addIterable()
          .addObject()
          .addUndefined()
          .addFunction()
          .addString()
          .build();

  /** A default set of externs for testing. */
  protected static final String DEFAULT_EXTERNS =
      new TestExternsBuilder()
          .addArray()
          .addIterable()
          .addObject()
          .addUndefined()
          .addFunction()
          .addString()
          .addPromise()
          .addClosureExterns()
          .addITemplateArray()
          .build();

  /**
   * Constructs a test.
   *
   * @param externs Externs JS as a string
   */
  protected CompilerTestCase(String externs) {
    this.defaultExternsInputs =
        ImmutableList.of(SourceFile.fromCode(GENERATED_EXTERNS_NAME, externs));
    librariesToInject = new LinkedHashSet<>();
  }

  /** Constructs a test. Uses AST comparison and no externs. */
  protected CompilerTestCase() {
    this("");
  }

  public String getName() {
    return this.getClass().getSimpleName();
  }

  // Overridden here so that we can easily find all classes that override.
  @Before
  public void setUp() throws Exception {

    // TODO(sdh): Initialize *all* the options here, but first we must ensure no subclass
    // is changing them in the constructor, rather than in their own setUp method.
    this.acceptedLanguage = LanguageMode.UNSUPPORTED;
    this.moduleResolutionMode = ResolutionMode.BROWSER;
    this.parseJsDocDocumentation = JsDocParsing.TYPES_ONLY;
    this.allowExternsChanges = false;
    this.allowSourcelessWarnings = false;
    this.astValidationEnabled = true;
    this.typeInfoValidationEnabled = false;
    this.scriptFeatureValidationEnabled = true;
    this.checkAccessControls = false;
    this.checkAstChangeMarking = true;
    this.closurePassEnabled = false;
    this.closurePassEnabledForExpected = false;
    this.compareAsTree = true;
    this.compareJsDoc = true;
    this.compareSyntheticCode = true;
    this.computeSideEffects = false;
    this.annotateSourceInfo = false;
    this.expectParseWarningsInThisTest = false;
    this.expectedSymbolTableError = null;
    this.gatherExternPropertiesEnabled = false;
    this.inferConsts = false;
    this.languageOut = LanguageMode.NO_TRANSPILE;
    this.browserFeaturesetYear = null;
    this.multistageCompilation = false;
    this.normalizeEnabled = false;
    this.normalizeExpectedOutputEnabled = false;
    this.parseTypeInfo = false;
    this.polymerPass = false;
    this.processCommonJsModules = false;
    this.rewriteClosureCode = false;
    // default to true: this doesn't have any effect unless rewriteClosureCode is enabled. The
    // default matches options.setBadRewriteModulesBeforeTypecheckingThatWeWantToGetRidOf.
    this.rewriteModulesAfterTypechecking = true;
    this.runTypeCheckAfterProcessing = false;
    this.rewriteEsModulesEnabled = false;
    this.transpileEnabled = false;
    this.typeCheckEnabled = false;
    this.assumeStaticInheritanceIsNotUsed = true;

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

  /** Enable debug logging for this test run */
  private boolean debugLoggingEnabled = false;

  public void enableDebugLogging(boolean debugLoggingEnabled) {
    this.debugLoggingEnabled = debugLoggingEnabled;
  }

  /**
   * Gets the compiler options to use for this test. Use getProcessor to determine what passes
   * should be run.
   */
  @OverridingMethodsMustInvokeSuper
  protected CompilerOptions getOptions() {
    CompilerOptions options = new CompilerOptions();

    options.setLanguageIn(acceptedLanguage);
    options.setEmitUseStrict(false);
    options.setLanguageOut(languageOut);
    if (browserFeaturesetYear != null) {
      // Must be set after languageOut, because it must override languageOut.
      options.setBrowserFeaturesetYear(browserFeaturesetYear);
    }
    options.setModuleResolutionMode(moduleResolutionMode);
    options.setParseJsDocDocumentation(parseJsDocDocumentation);
    options.setAssumeStaticInheritanceIsNotUsed(assumeStaticInheritanceIsNotUsed);
    options.setPreserveTypeAnnotations(true);
    options.setAssumeGettersArePure(false); // Default to the complex case.

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
    if (debugLoggingEnabled) {
      CompilerTestCaseUtils.setDebugLogDirectoryOn(options);
    }
    options.setRuntimeLibraryMode(CompilerOptions.RuntimeLibraryMode.RECORD_ONLY);

    return options;
  }

  @ForOverride
  protected CodingConvention getCodingConvention() {
    return new GoogleCodingConvention();
  }

  /**
   * Enables parsing type info from JSDoc comments. This sets the compiler option, but does not
   * actually run the type checking pass.
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

  /** Returns the number of times the pass should be run before results are verified. */
  @ForOverride
  protected int getNumRepetitions() {
    return 1;
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

  /** What language to allow in source parsing. Also sets the output language. */
  protected final void setAcceptedLanguage(LanguageMode lang) {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    setLanguage(lang, lang);
  }

  /** Sets the input and output language modes.. */
  protected final void setLanguage(LanguageMode langIn, LanguageMode langOut) {
    this.acceptedLanguage = langIn;
    setLanguageOut(langOut);
  }

  protected final void setLanguageOut(LanguageMode langOut) {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    this.languageOut = langOut;
  }

  protected final void setBrowserFeaturesetYear(@Nullable Integer year) {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    this.browserFeaturesetYear = year;
  }

  protected final void setModuleResolutionMode(ResolutionMode moduleResolutionMode) {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    this.moduleResolutionMode = moduleResolutionMode;
  }

  protected final void setJsDocumentation(JsDocParsing parseJsDocDocumentation) {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    this.parseJsDocDocumentation = parseJsDocDocumentation;
  }

  protected final void setAssumeStaticInheritanceIsNotUsed(
      boolean assumeStaticInheritanceIsNotUsed) {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    this.assumeStaticInheritanceIsNotUsed = assumeStaticInheritanceIsNotUsed;
  }

  /** Whether to run InferConsts before passes */
  protected final void enableInferConsts() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    this.inferConsts = true;
  }

  /** Enables running CheckAccessControls after the pass being tested (and checking types). */
  protected final void enableCheckAccessControls() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    this.checkAccessControls = true;
  }

  /** Allow externs to change. */
  protected final void allowExternsChanges() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    this.allowExternsChanges = true;
  }

  protected final void enablePolymerPass() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    polymerPass = true;
  }

  /**
   * Perform type checking before running the test pass. This will check for type errors and
   * annotate nodes with type information.
   *
   * @see TypeCheck
   */
  protected final void enableTypeCheck() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    typeCheckEnabled = true;
    enableCreateModuleMap();
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
   * Converts any inferred JSTypes attached to Nodes to Colors, also deleting any JSTypes or
   * JSDocInfo references.
   */
  protected final void replaceTypesWithColors() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    replaceTypesWithColors = true;
  }

  protected final void enableCreateModuleMap() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    this.createModuleMap = true;
  }

  /**
   * When comparing expected to actual, ignore nodes created through compiler.ensureLibraryInjected
   *
   * <p>This differs from using {@link CompilerOptions.RuntimeLibraryMode.RECORD_ONLY} in that the
   * compiler still injects the polyfills when requested.
   */
  protected final void disableCompareSyntheticCode() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    compareSyntheticCode = false;
  }

  /** Run using multistage compilation. */
  protected final void enableMultistageCompilation() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    multistageCompilation = true;
  }

  /** Run using singlestage compilation. */
  protected final void disableMultistageCompilation() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    multistageCompilation = false;
  }

  /** Disable validating AST change marking. */
  protected final void disableValidateAstChangeMarking() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    checkAstChangeMarking = false;
  }

  /** Process closure library primitives. */
  protected final void enableClosurePass() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    closurePassEnabled = true;
    enableCreateModuleMap(); // ProcessClosurePrimitives looks at the ModuleMetadataMap
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

  /** Rewrite Closure code before the test is run. */
  protected final void enableRewriteClosureCode() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    rewriteClosureCode = true;
    enableCreateModuleMap();
  }

  /** Don't rewrite Closure code before the test is run. */
  protected final void disableRewriteClosureCode() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    rewriteClosureCode = false;
  }

  protected final void enableRewriteModulesAfterTypechecking() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    enableRewriteClosureCode();
    this.rewriteModulesAfterTypechecking = true;
  }

  protected final void disableRewriteModulesAfterTypechecking() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    this.rewriteModulesAfterTypechecking = false;
  }

  /** Rewrite goog.provides */
  protected final void enableRewriteClosureProvides() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    this.rewriteClosureProvides = true;
    enableCreateModuleMap();
  }

  /**
   * Perform AST normalization before running the test pass
   *
   * <p>This option also enables multistage compilation to increase coverage for multistage builds
   * (Normalize is stage 2, so normalized tests must cover stage 2 passes). Disable via
   * disableMultistageCompilation().
   *
   * @see Normalize
   */
  protected final void enableNormalize() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    this.normalizeEnabled = true;
    this.enableMultistageCompilation();
  }

  /**
   * If normalization is enabled for test inputs, then also run normalization on the expected
   * outputs.
   *
   * <p>Enabling this can cause the results of test failures to be confusing, since the expected
   * output written in the test case is modified before it is compared with the test results. So,
   * you should avoid using this when you can. However, there are some ways that Normalization
   * modifies the AST (e.g. adding properties to nodes) that cannot be represented in input JS
   * source text but will affect whether the AST comparison succeeds. This option is intended to
   * allow Normalization to make those changes on the expected output code.
   *
   * @see #enableNormalize
   */
  protected final void enableNormalizeExpectedOutput() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    checkState(this.normalizeEnabled, "Enabled normalize on output, but not input.");
    this.normalizeExpectedOutputEnabled = true;
  }

  /**
   * Perform AST transpilation before running the test pass and also on the expected output.
   *
   * <p>This also has the side effect of forcing normalization of the expected output if you also
   * enable normalization.
   *
   * <p>Although this method is not deprecated, it is only used in a few very special cases. The
   * test behavior this creates can be confusing, especially since it modifies the expected output,
   * so that diffs will not necessarily reflect the expected output text written directly into the
   * test cases. Adding new usages of this is probably a bad idea.
   */
  protected final void enableTranspile() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    transpileEnabled = true;
  }

  /**
   * Don't perform AST normalization before running the test pass.
   *
   * @see Normalize
   */
  protected final void disableNormalize() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    normalizeEnabled = false;
  }

  /** Perform ES module transpilation before running the test pass. */
  protected final void enableRewriteEsModules() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    rewriteEsModulesEnabled = true;
  }

  /**
   * Run the PureFunctionIdentifier pass before running the test pass.
   *
   * @see PureFunctionIdentifier
   */
  protected final void enableComputeSideEffects() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    computeSideEffects = true;
  }

  /**
   * Disables runng the PureFunctionIdentifier pass before running the test pass.
   *
   * @see PureFunctionIdentifier
   */
  protected final void disableComputeSideEffects() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    computeSideEffects = false;
  }

  protected final void enableSourceInformationAnnotator() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    this.annotateSourceInfo = true;
  }

  /**
   * Declare an accessor as being present "somewhere" in a the test program.
   *
   * <p>Calling this method also disabled automatic getter / setter collection.
   *
   * @see GatherGetterAndSetterProperties
   */
  protected final void declareAccessor(String name, PropertyAccessKind kind) {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    checkState(kind.hasGetterOrSetter(), "Kind should be a getter and/or setter.");
    declaredAccessors.put(name, kind);
  }

  /** Scan externs for properties that should not be renamed. */
  protected final void enableGatherExternProperties() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    gatherExternPropertiesEnabled = true;
  }

  protected final ImmutableSet<String> getGatheredExternProperties() {
    checkState(this.gatherExternPropertiesEnabled, "Must enable gatherExternProperties");
    return lastCompiler.getExternProperties();
  }

  /** Disable validating the AST after each run of the pass. */
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
  protected final void setExpectParseWarningsInThisTest() {
    checkState(this.setUpRan, "Attempted to configure before running setUp().");
    expectParseWarningsInThisTest = true;
  }

  protected final void setWebpackModulesById(Map<String, String> webpackModulesById) {
    this.webpackModulesById.clear();
    this.webpackModulesById.putAll(webpackModulesById);
  }

  /**
   * A map of replacement generic names to the prefix of the original generated one.
   *
   * <p>If set, used in conjunction with {@link UnitTestUtils#updateGenericVarNamesInExpectedFiles}
   * on expected output.
   *
   * <p>Note: Requires that sources are a {@link FlatSources}.
   */
  protected final void setGenericNameReplacements(Map<String, String> genericNameReplacements) {
    this.genericNameReplacements = ImmutableMap.copyOf(genericNameReplacements);
  }

  protected final void disableGenericNameReplacements() {
    genericNameReplacements = ImmutableMap.of();
  }

  /** Returns a newly created TypeCheck. */
  private static TypeCheck createTypeCheck(Compiler compiler) {
    ReverseAbstractInterpreter rai =
        new SemanticReverseAbstractInterpreter(compiler.getTypeRegistry());
    TypeCheck typeChecker = new TypeCheck(compiler, rai, compiler.getTypeRegistry());
    compiler.setTypeCheckingHasRun(true);
    return typeChecker;
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

  /** Verifies that the compiler generates the given error and description for the given input. */
  protected void testError(String js, DiagnosticType error, String description) {
    assertThat(error).isNotNull();
    test(srcs(js), error(error).withMessage(description));
  }

  /** Verifies that the compiler generates the given error and description for the given input. */
  protected void testError(Sources srcs, Diagnostic error) {
    assertThat(error.level).isEqualTo(CheckLevel.ERROR);
    test(srcs, error);
  }

  /** Verifies that the compiler generates the given error and description for the given input. */
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
  protected void testError(Sources js, DiagnosticType error) {
    assertThat(error).isNotNull();
    test(js, error(error));
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

  /** Verifies that the compiler generates the given warning for the given input. */
  protected void testWarning(Sources srcs, DiagnosticType warning) {
    assertThat(warning).isNotNull();
    test(srcs, warning(warning));
  }

  /**
   * Verifies that the compiler generates the given warning and description for the given input.
   *
   * @param js Input
   * @param warning Expected warning
   */
  protected void testWarning(String js, DiagnosticType warning, String description) {
    assertThat(warning).isNotNull();
    test(srcs(js), warning(warning).withMessage(description.trim()));
  }

  /** Verifies that the compiler generates the given warning for the given input. */
  protected void testWarning(
      Externs externs, Sources srcs, DiagnosticType warning, String description) {
    assertThat(warning).isNotNull();
    test(externs, srcs, warning(warning).withMessage(description.trim()));
  }

  /**
   * Verifies that the compiler generates no warnings for the given input.
   *
   * @param js Input
   */
  protected void testNoWarning(String js) {
    test(srcs(js));
  }

  /** Verifies that the compiler generates no warnings for the given input. */
  protected void testNoWarning(Sources srcs) {
    test(srcs);
  }

  /** Verifies that the compiler generates no warnings for the given input. */
  public void testNoWarning(Externs externs, Sources srcs) {
    test(externs, srcs);
  }

  /**
   * Verifies that the compiler pass's JS output matches the expected output and (optionally) that
   * an expected warning is issued. Or, if an error is expected, this method just verifies that the
   * error is encountered.
   *
   * @param js Input
   * @param expected Expected output, or null if an error is expected
   * @param diagnostic Expected warning or error
   */
  protected void test(String js, String expected, Diagnostic diagnostic) {
    test(externs(defaultExternsInputs), srcs(js), expected(expected), diagnostic);
  }

  protected void testInternal(
      Externs externs,
      Sources inputs,
      Expected expected,
      List<Diagnostic> diagnostics,
      List<Postcondition> postconditions) {

    Compiler compiler = createAndInitializeCompiler(externs, inputs);
    lastCompiler = compiler;

    if (this.typeCheckEnabled) {
      BaseJSTypeTestCase.addNativeProperties(compiler.getTypeRegistry());
    }

    testInternal(compiler, externs, inputs, expected, diagnostics, postconditions);
  }

  /**
   * Create a fresh Compiler object initialized with the given externs and inputs.
   *
   * <p>Important: This method does not affect the return value of `getLastCompiler()`.
   */
  protected Compiler createAndInitializeCompiler(Externs externs, Sources inputs) {
    Compiler compiler = createCompiler();

    CompilerOptions options = getOptions();

    if (inputs instanceof FlatSources flatSources) {
      // TODO(bradfordcsmith): Why do we set this only for the non-module case?
      //     I extracted this method from testInternal().
      options.setCheckTypes(parseTypeInfo || this.typeCheckEnabled);
      compiler.init(externs.externs, flatSources.sources, options);
    } else {
      compiler.initChunks(externs.externs, ((ChunkSources) inputs).chunks, getOptions());
    }
    return compiler;
  }

  private static @Nullable ImmutableList<SourceFile> maybeCreateSources(
      String name, String srcText) {
    if (srcText != null) {
      return ImmutableList.of(SourceFile.fromCode(name, srcText));
    }
    return null;
  }

  protected static @Nullable List<SourceFile> createSources(String name, String... sources) {
    if (sources == null) {
      return null;
    }
    return createSources(name, ImmutableList.copyOf(sources));
  }

  private static @Nullable List<SourceFile> createSources(String name, List<String> sources) {
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
   * Verifies that the compiler pass's JS output is the same as its input.
   *
   * @param js Input and output
   */
  protected void testSame(String js) {
    test(srcs(js), expected(js));
  }

  /**
   * Verifies that the compiler pass's JS output is the same as its input and (optionally) that an
   * expected warning is issued.
   *
   * @param js Input and output
   * @param warning Expected warning, or null if no warning is expected
   */
  protected void testSame(String js, DiagnosticType warning) {
    test(srcs(js), expected(js), warning(warning));
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
      Externs externs,
      Sources inputsObj, // TODO remove this parameter
      Expected expectedObj,
      List<Diagnostic> diagnostics,
      List<Postcondition> postconditions) {
    var genericNameMapping = new LinkedHashMap<String, String>();
    if (!genericNameReplacements.isEmpty()
        && expectedObj != null
        && expectedObj.expected != null
        && inputsObj instanceof FlatSources flatSources) {
      expectedObj =
          expected(
              UnitTestUtils.updateGenericVarNamesInExpectedFiles(
                  flatSources, expectedObj, genericNameReplacements, genericNameMapping));
    }

    List<SourceFile> inputs =
        (inputsObj instanceof FlatSources flatSources) ? flatSources.sources : null;
    List<SourceFile> expected = expectedObj != null ? expectedObj.expected : null;
    ImmutableList<Diagnostic> expectedErrors =
        diagnostics.stream().filter(d -> d.level == CheckLevel.ERROR).collect(toImmutableList());
    ImmutableList<Diagnostic> expectedWarnings =
        diagnostics.stream().filter(d -> d.level == CheckLevel.WARNING).collect(toImmutableList());
    checkState(
        expectedErrors.isEmpty() || expectedWarnings.isEmpty(),
        "Cannot expect both errors and warnings.");
    checkState(
        expectedErrors.isEmpty() || expected == null,
        "Cannot expect both errors and compiled output.");
    checkState(this.setUpRan, "CompilerTestCase.setUp not run: call super.setUp() from overrides.");
    RecentChange recentChange = new RecentChange();
    compiler.getChangeTracker().addChangeHandler(recentChange);

    Node root = compiler.parseInputs();

    if (root == null) {
      // Might be an expected parse error.
      assertWithMessage("parse errors")
          .that(compiler.getErrors())
          .comparingElementsUsing(DIAGNOSTIC_CORRESPONDENCE)
          .containsExactlyElementsIn(expectedErrors);
      for (Postcondition postcondition : postconditions) {
        postcondition.verify(compiler);
      }
      return;
    }
    if (!expectParseWarningsInThisTest) {
      assertWithMessage("Unexpected parser warning(s)").that(compiler.getWarnings()).isEmpty();
    } else {
      assertThat(compiler.getWarningCount()).isGreaterThan(0);
    }
    Node externsRoot = root.getFirstChild();
    Node mainRoot = root.getLastChild();

    if (annotateSourceInfo) {
      NodeTraversal.traverse(compiler, mainRoot, SourceInformationAnnotator.create());
    }

    if (createModuleMap) {
      new GatherModuleMetadata(
              compiler, /* processCommonJsModules= */ false, ResolutionMode.BROWSER)
          .process(externsRoot, mainRoot);
      new ModuleMapCreator(compiler, compiler.getModuleMetadataMap())
          .process(externsRoot, mainRoot);
    }

    if (astValidationEnabled) {
      // NOTE: We do not enable type validation here, because type information never exists
      // immediately after parsing.
      (new AstValidator(compiler, scriptFeatureValidationEnabled)).validateRoot(root);
    }

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
          new PolymerPass(compiler).process(externsRoot, mainRoot);
          hasCodeChanged = hasCodeChanged || recentChange.hasCodeChanged();
        }

        if (rewriteClosureCode && i == 0) {
          new CheckClosureImports(compiler, compiler.getModuleMetadataMap())
              .process(externsRoot, mainRoot);
          ScopedAliases.builder(compiler).build().process(externsRoot, mainRoot);
          hasCodeChanged = hasCodeChanged || recentChange.hasCodeChanged();
        }

        if (rewriteClosureCode && !rewriteModulesAfterTypechecking && i == 0) {
          new ClosureRewriteModule(compiler, null, null).process(externsRoot, mainRoot);
          hasCodeChanged = hasCodeChanged || recentChange.hasCodeChanged();
        }

        // Only run process closure primitives once, if asked.
        if (closurePassEnabled && i == 0) {
          recentChange.reset();
          new ProcessClosurePrimitives(compiler).process(externsRoot, mainRoot);
          hasCodeChanged = hasCodeChanged || recentChange.hasCodeChanged();
        }

        // Only run process closure primitives once, if asked.
        if (processCommonJsModules && i == 0) {
          recentChange.reset();
          new ProcessCommonJSModules(compiler).process(externsRoot, mainRoot);
          hasCodeChanged = hasCodeChanged || recentChange.hasCodeChanged();
        }

        if ((rewriteEsModulesEnabled || transpileEnabled) && i == 0) {
          // If we're transpiling down to ES5, then that includes ES module transpilation,
          // but that pass isn't part of transpileToEs5(), because it happens earlier in
          // the compilation process than most transpilation passes.
          recentChange.reset();
          rewriteEsModules(compiler, externsRoot, mainRoot);
          hasCodeChanged = hasCodeChanged || recentChange.hasCodeChanged();
        }

        final boolean injectLibrariesFromTypedAsts =
            multistageCompilation || replaceTypesWithColors;
        if (!librariesToInject.isEmpty() && i == 0 && !injectLibrariesFromTypedAsts) {
          recentChange.reset();
          for (String resourceName : librariesToInject) {
            compiler.ensureLibraryInjected(resourceName, true);
          }
          hasCodeChanged = hasCodeChanged || recentChange.hasCodeChanged();
        }

        // Only run the type checking pass once, if asked.
        // Running it twice can cause unpredictable behavior because duplicate
        // objects for the same type are created, and the type system
        // uses reference equality to compare many types.
        if (!runTypeCheckAfterProcessing && typeCheckEnabled && i == 0) {
          TypeCheck check = createTypeCheck(compiler);
          check.processForTesting(externsRoot, mainRoot);
        }

        if (rewriteClosureCode && rewriteModulesAfterTypechecking && i == 0) {
          new ClosureRewriteModule(compiler, null, compiler.getTopScope())
              .process(externsRoot, mainRoot);
          hasCodeChanged = hasCodeChanged || recentChange.hasCodeChanged();
        }

        if (inferConsts && i == 0) {
          new InferConsts(compiler).process(externsRoot, mainRoot);
        }

        if (gatherExternPropertiesEnabled && i == 0) {
          new GatherExternProperties(compiler, GatherExternProperties.Mode.CHECK_AND_OPTIMIZE)
              .process(externsRoot, mainRoot);
        }

        if (i == 0) {
          if (multistageCompilation) {
            if (inputs != null) {
              recentChange.reset();
              compiler =
                  CompilerTestCaseUtils.multistageSerializeAndDeserialize(
                      this, compiler, externs.externs, inputs, recentChange);
              root = compiler.getRoot();
              externsRoot = compiler.getExternsRoot();
              mainRoot = compiler.getJsRoot();
              lastCompiler = compiler;
              hasCodeChanged = hasCodeChanged || recentChange.hasCodeChanged();
            }
          } else if (replaceTypesWithColors) {
            recentChange.reset();
            new RemoveCastNodes(compiler).process(externsRoot, mainRoot);
            new ConvertTypesToColors(
                    compiler, SerializationOptions.builder().setIncludeDebugInfo(true).build())
                .process(externsRoot, mainRoot);

            compiler.setLifeCycleStage(AbstractCompiler.LifeCycleStage.COLORS_AND_SIMPLIFIED_JSDOC);
            hasCodeChanged = hasCodeChanged || recentChange.hasCodeChanged();
          }

          if (!librariesToInject.isEmpty() && injectLibrariesFromTypedAsts) {
            recentChange.reset();
            for (String resourceName : librariesToInject) {
              compiler.ensureLibraryInjected(resourceName, true);
            }
            hasCodeChanged = hasCodeChanged || recentChange.hasCodeChanged();
          }
        }

        // Only rewrite provides once, if asked.
        if (rewriteClosureProvides && i == 0) {
          recentChange.reset();
          new ProcessClosureProvidesAndRequires(compiler, false).process(externsRoot, mainRoot);
          hasCodeChanged = hasCodeChanged || recentChange.hasCodeChanged();
        }

        if (transpileEnabled && i == 0) {
          recentChange.reset();
          transpileToEs5(compiler, externsRoot, mainRoot);
          hasCodeChanged = hasCodeChanged || recentChange.hasCodeChanged();
        } else if (normalizeEnabled && i == 0) {
          // An explicit normalize pass is necessary since it wasn't done as part of transpilation.
          normalizeActualCode(compiler, externsRoot, mainRoot);
        }

        updateAccessorSummary(compiler, mainRoot);

        if (computeSideEffects && i == 0) {
          recentChange.reset();
          PureFunctionIdentifier.Driver mark = new PureFunctionIdentifier.Driver(compiler);
          mark.process(externsRoot, mainRoot);
          hasCodeChanged = hasCodeChanged || recentChange.hasCodeChanged();
        }

        recentChange.reset();

        ChangeVerifier changeVerifier = null;
        if (checkAstChangeMarking) {
          changeVerifier = new ChangeVerifier(compiler);
          changeVerifier.snapshot(mainRoot);
        }

        // Call "beforePass" as some passes ask for the index of the current pass being run and
        // expect it to be >= 0.
        compiler.beforePass(this.getName());
        getProcessor(compiler).process(externsRoot, mainRoot);

        if (checkAstChangeMarking) {
          // TODO(johnlenz): add support for multiple passes in getProcessor so that we can
          // check the AST marking after each pass runs.
          // Verify that changes to the AST are properly marked on the AST.
          changeVerifier.checkRecordedChanges(mainRoot);
        }

        verifyAccessorSummary(compiler, mainRoot);

        if (runTypeCheckAfterProcessing && typeCheckEnabled && i == 0) {
          TypeCheck check = createTypeCheck(compiler);
          check.processForTesting(externsRoot, mainRoot);
        }

        // Transpilation passes are allowed to leave the AST in a bad state when there is a halting
        // error.
        if (astValidationEnabled && !compiler.hasHaltingErrors()) {
          TypeInfoValidation typeValidationMode =
              typeInfoValidationEnabled
                  ? compiler.hasOptimizationColors()
                      ? TypeInfoValidation.COLOR
                      : TypeInfoValidation.JSTYPE
                  : TypeInfoValidation.NONE;
          new AstValidator(compiler, scriptFeatureValidationEnabled)
              .setTypeValidationMode(typeValidationMode)
              .validateRoot(root);
        }
        new SourceInfoCheck(compiler).process(externsRoot, mainRoot);

        if (checkAccessControls) {
          (new CheckAccessControls(compiler)).process(externsRoot, mainRoot);
        }

        hasCodeChanged = hasCodeChanged || recentChange.hasCodeChanged();
        aggregateWarningCount += errorManagers[i].getWarningCount();
        aggregateWarnings.addAll(compiler.getWarnings());

        if (normalizeEnabled) {
          boolean verifyDeclaredConstants = true;
          new ValidityCheck.VerifyConstants(compiler, verifyDeclaredConstants)
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

      ImmutableList<JSError> stErrors = symbolTableErrorManager.getErrors();
      if (expectedSymbolTableError != null) {
        assertError(getOnlyElement(stErrors)).hasType(expectedSymbolTableError);
      } else {
        assertWithMessage("symbol table errors").that(stErrors).isEmpty();
      }
      LightweightMessageFormatter formatter = new LightweightMessageFormatter(compiler);
      if (expectedWarnings.isEmpty()) {
        assertWithMessage(
                "aggregate warnings: %s",
                aggregateWarnings.stream().map(formatter::formatWarning).collect(joining("\n")))
            .that(aggregateWarnings)
            .isEmpty();
      } else {
        assertWithMessage(
                "There should be %s warnings, repeated %s time(s). Warnings: \n%s",
                expectedWarnings.size(), numRepetitions, LINE_JOINER.join(aggregateWarnings))
            .that(aggregateWarningCount)
            .isEqualTo(numRepetitions * expectedWarnings.size());
        for (int i = 0; i < numRepetitions; i++) {
          assertWithMessage("compile warnings from repetition %s", (i + 1))
              .that(errorManagers[i].getWarnings())
              .comparingElementsUsing(DIAGNOSTIC_CORRESPONDENCE)
              .containsExactlyElementsIn(expectedWarnings);
          for (JSError warning : errorManagers[i].getWarnings()) {
            validateSourceLocation(warning);
          }
        }
      }

      // If we ran normalize on the AST, we must also run normalize on th clone before checking for
      // changes.
      if (normalizeEnabled) {
        boolean hasTypecheckingRun = compiler.hasTypeCheckingRun();
        // we don't run type inference over the clone of the AST, so need to mark that in the
        // compiler or Normalize will crash due to lack of inferred types on the clone AST nodes.
        compiler.setTypeCheckingHasRun(false);
        normalizeActualCode(compiler, externsRootClone, mainRootClone);
        compiler.setTypeCheckingHasRun(hasTypecheckingRun);
      }

      boolean codeChange = !mainRootClone.isEquivalentWithSideEffectsTo(mainRoot);
      boolean externsChange = !externsRootClone.isEquivalentWithSideEffectsTo(externsRoot);

      // Generally, externs should not be changed by the compiler passes.
      if (externsChange && !allowExternsChanges) {
        assertNode(externsRoot)
            .usingSerializer(createPrettyPrinter(compiler))
            .isEqualTo(externsRootClone);
      }

      if (checkAstChangeMarking) {
        if (!codeChange && !externsChange) {
          assertWithMessage("compiler.reportCodeChange() was called even though nothing changed")
              .that(hasCodeChanged)
              .isFalse();
        } else {
          assertWithMessage(
                  "compiler.reportCodeChange() should have been called."
                      + "\nOriginal: %s\nNew: %s",
                  mainRootClone.toStringTree(), mainRoot.toStringTree())
              .that(hasCodeChanged)
              .isTrue();
        }
      }

      if (expected != null) {
        if (!compareSyntheticCode) {
          Node scriptRoot = mainRoot.getFirstChild();
          for (Node child = scriptRoot.getFirstChild(); child != null; ) {
            Node nextChild = child.getNext();
            if (NodeUtil.isInSyntheticScript(child)) {
              child.detach();
            }
            child = nextChild;
          }
        }
        if (compareAsTree) {
          if (compareJsDoc) {
            assertNode(mainRoot)
                .usingSerializer(createPrettyPrinter(compiler))
                .withGenericNameReplacements(ImmutableMap.copyOf(genericNameMapping))
                .isEqualIncludingJsDocTo(expectedRoot);
          } else {
            assertNode(mainRoot)
                .usingSerializer(createPrettyPrinter(compiler))
                .withGenericNameReplacements(ImmutableMap.copyOf(genericNameMapping))
                .isEqualTo(expectedRoot);
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
          assertThat(compiler.toSource(mainRoot).replaceAll(" +\n", "\n"))
              .isEqualTo(Joiner.on("").join(expectedSources));
        }
      }

      // Verify normalization is not invalidated.
      Node normalizeCheckRootClone = root.cloneTree();
      Node normalizeCheckExternsRootClone = normalizeCheckRootClone.getFirstChild();
      Node normalizeCheckMainRootClone = normalizeCheckRootClone.getLastChild();

      assertNode(normalizeCheckMainRootClone)
          .usingSerializer(createPrettyPrinter(compiler))
          .isEqualTo(mainRoot);

      // TODO(johnlenz): enable this for most test cases.
      // Currently, this invalidates test for while-loops, for-loop
      // initializers, and other naming.  However, a set of code
      // (Closure primitive rewrites, etc) runs before the Normalize pass,
      // so this can't be force on everywhere.
      if (normalizeEnabled) {
        Normalize.builder(compiler)
            .assertOnChange(true)
            .build()
            .process(normalizeCheckExternsRootClone, normalizeCheckMainRootClone);

        assertNode(normalizeCheckMainRootClone)
            .usingSerializer(createPrettyPrinter(compiler))
            .isEqualTo(mainRoot);
      }
    } else {
      assertWithMessage("compile errors")
          .that(compiler.getErrors())
          .comparingElementsUsing(DIAGNOSTIC_CORRESPONDENCE)
          .containsExactlyElementsIn(expectedErrors);
      for (JSError error : compiler.getErrors()) {
        validateSourceLocation(error);
        assertWithMessage("Some placeholders in the error message were not replaced")
            .that(error.description())
            .doesNotContainMatch("\\{\\d\\}");
      }
    }
    for (Postcondition postcondition : postconditions) {
      postcondition.verify(compiler);
    }
  }

  private static void rewriteEsModules(AbstractCompiler compiler, Node externsRoot, Node codeRoot) {
    CompilerOptions options = compiler.getOptions();
    PassListBuilder factories = new PassListBuilder(options);

    GatherModuleMetadata gatherModuleMetadata =
        new GatherModuleMetadata(
            compiler, options.getProcessCommonJSModules(), options.moduleResolutionMode);
    factories.maybeAdd(
        PassFactory.builder()
            .setName(PassNames.GATHER_MODULE_METADATA)
            .setRunInFixedPointLoop(true)
            .setInternalFactory((x1) -> gatherModuleMetadata)
            .build());
    factories.maybeAdd(
        PassFactory.builder()
            .setName(PassNames.CREATE_MODULE_MAP)
            .setRunInFixedPointLoop(true)
            .setInternalFactory(
                (x) -> new ModuleMapCreator(compiler, compiler.getModuleMetadataMap()))
            .build());
    TranspilationPasses.addEs6ModulePass(
        factories, new PreprocessorSymbolTable.CachedInstanceFactory());
    for (PassFactory factory : factories.build()) {
      factory.create(compiler).process(externsRoot, codeRoot);
    }
  }

  private void transpileToEs5(AbstractCompiler compiler, Node externsRoot, Node codeRoot) {
    CompilerOptions options = compiler.getOptions();
    PassListBuilder factories = new PassListBuilder(options);

    options.setLanguageIn(LanguageMode.UNSUPPORTED);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    TranspilationPasses.addTranspilationRuntimeLibraries(factories);
    TranspilationPasses.addRewritePolyfillPass(factories);
    // Transpilation requires normalization.
    factories.maybeAdd(
        PassFactory.builder()
            .setName(PassNames.NORMALIZE)
            .setInternalFactory(abstractCompiler -> Normalize.builder(abstractCompiler).build())
            .build());
    TranspilationPasses.addTranspilationPasses(factories, options);
    // We need to put back the original variable names where possible once transpilation is
    // complete. This matches the behavior in DefaultPassConfig. See comments there for further
    // explanation.
    factories.maybeAdd(
        PassFactory.builder()
            .setName("invertContextualRenaming")
            .setInternalFactory(MakeDeclaredNamesUnique::getContextualRenameInverter)
            .build());
    for (PassFactory factory : factories.build()) {
      factory.create(compiler).process(externsRoot, codeRoot);
    }
  }

  private void validateSourceLocation(JSError jserror) {
    // Make sure that source information is always provided.
    if (!allowSourcelessWarnings) {
      final String sourceName = jserror.sourceName();
      assertWithMessage("Missing source file name in warning: %s", jserror)
          .that(sourceName != null && !sourceName.isEmpty())
          .isTrue();
      Node scriptNode = lastCompiler.getScriptNode(sourceName);
      assertWithMessage("No SCRIPT node found for warning: %s", jserror)
          .that(scriptNode)
          .isNotNull();
      assertWithMessage("Missing line number in warning: %s", jserror)
          .that(jserror.lineno() != -1)
          .isTrue();
      assertWithMessage("Missing char number in warning: %s", jserror)
          .that(jserror.charno() != -1)
          .isTrue();
    }
  }

  private static void normalizeActualCode(Compiler compiler, Node externsRoot, Node mainRoot) {
    Normalize normalize = Normalize.createNormalizeForOptimizations(compiler);
    normalize.process(externsRoot, mainRoot);
  }

  private void updateAccessorSummary(Compiler compiler, Node mainRoot) {
    LinkedHashMap<String, PropertyAccessKind> union =
        new LinkedHashMap<>(GatherGetterAndSetterProperties.gather(compiler, mainRoot.getParent()));
    union.putAll(this.declaredAccessors);
    compiler.setAccessorSummary(AccessorSummary.create(union));
  }

  private void verifyAccessorSummary(Compiler compiler, Node mainRoot) {
    if (compiler.getAccessorSummary() == null) {
      return;
    }

    assertWithMessage(
            "Pass created new getters/setters without calling"
                + " GatherGetterAndSetterProperties.update")
        .that(compiler.getAccessorSummary().getAccessors())
        .containsAtLeastEntriesIn(
            GatherGetterAndSetterProperties.gather(compiler, mainRoot.getParent()));
  }

  protected Node parseExpectedJs(String expected) {
    return parseExpectedJs(ImmutableList.of(SourceFile.fromCode(GENERATED_SRC_NAME, expected)));
  }

  protected Node parseExpectedJs(Expected expected) {
    return parseExpectedJs(expected.expected);
  }

  /** Parses expected JS inputs and returns the root of the parse tree. */
  private Node parseExpectedJs(List<SourceFile> inputs) {
    Compiler compiler = createCompiler();

    compiler.init(defaultExternsInputs, inputs, getOptions());
    Node root = compiler.parseInputs();
    assertWithMessage("Unexpected parse error(s): %s", LINE_JOINER.join(compiler.getErrors()))
        .that(root)
        .isNotNull();
    Node externsRoot = root.getFirstChild();
    Node mainRoot = externsRoot.getNext();

    // Normalize CAST nodes from the expected AST if asked.
    if (replaceTypesWithColors || multistageCompilation) {
      new RemoveCastNodes(compiler).process(externsRoot, mainRoot);
    }

    if (closurePassEnabled && closurePassEnabledForExpected && !compiler.hasErrors()) {
      new GatherModuleMetadata(compiler, false, ResolutionMode.BROWSER)
          .process(externsRoot, mainRoot);
      new ProcessClosurePrimitives(compiler).process(externsRoot, mainRoot);
    }

    if (rewriteClosureCode) {
      new ClosureRewriteModule(compiler, null, null).process(externsRoot, mainRoot);
      ScopedAliases.builder(compiler).build().process(externsRoot, mainRoot);
    }

    if (rewriteClosureProvides && closurePassEnabledForExpected && !compiler.hasErrors()) {
      new ProcessClosureProvidesAndRequires(compiler, false).process(externsRoot, mainRoot);
    }

    if (transpileEnabled && !compiler.hasErrors()) {
      rewriteEsModules(compiler, externsRoot, mainRoot);
      // TODO(bradfordcsmith): Notice that transpileToEs5() will perform normalization, if that
      // is enabled, without checking normalizeExpectedOutputEnabled.
      // This behavior is documented in enableTranspile(), which is used only in a very few
      // special cases.
      transpileToEs5(compiler, externsRoot, mainRoot);
    } else if (normalizeEnabled && normalizeExpectedOutputEnabled && !compiler.hasErrors()) {
      // An explicit normalize pass is necessary since it wasn't done as part of transpilation.
      normalizeActualCode(compiler, externsRoot, mainRoot);
    }
    return mainRoot;
  }

  protected void testExternChanges(Sources input, Expected expectedExtern, Diagnostic... warnings) {
    testExternChanges(externs(""), input, expectedExtern, warnings);
  }

  protected void testExternChanges(
      Externs externs, Sources inputs, Expected expectedExtern, Diagnostic... warnings) {
    Compiler compiler = createCompiler();
    CompilerOptions options = getOptions();
    if (inputs instanceof FlatSources flatSources) {
      compiler.init(externs.externs, flatSources.sources, options);
    } else {
      compiler.initChunks(externs.externs, ((ChunkSources) inputs).chunks, getOptions());
    }
    testExternChangesInternal(compiler, expectedExtern, warnings);
  }

  private void testExternChangesInternal(
      Compiler compiler, Expected expectedExterns, Diagnostic... warnings) {
    compiler.parseInputs();

    if (createModuleMap) {
      new GatherModuleMetadata(
              compiler, /* processCommonJsModules= */ false, ResolutionMode.BROWSER)
          .process(compiler.getExternsRoot(), compiler.getJsRoot());
      new ModuleMapCreator(compiler, compiler.getModuleMetadataMap())
          .process(compiler.getExternsRoot(), compiler.getJsRoot());
    }
    assertThat(compiler.getErrors()).isEmpty();

    Node externsAndJs = compiler.getRoot();
    Node root = externsAndJs.getLastChild();

    Node externs = externsAndJs.getFirstChild();

    Node expectedRoot = parseExpectedJs(expectedExterns);
    assertThat(compiler.getErrors()).isEmpty();

    // Call "beforePass" as some passes ask for the index of the current pass being run and expect
    // it to be >= 0.
    compiler.beforePass(this.getName());
    (getProcessor(compiler)).process(externs, root);

    if (compareAsTree) {
      // Ignore and remove empty externs, so that if we start with an empty extern and only add
      // to the synthetic externs, we can still enable compareAsTree.
      if (externs.hasMoreThanOneChild()) {
        for (Node c = externs.getFirstChild(); c != null; c = c.getNext()) {
          if (!c.hasChildren()) {
            c.detach();
          }
        }
      }

      // Expected output parsed without implied block.
      checkState(externs.isRoot());

      expectedRoot.detach();

      if (compareJsDoc) {
        assertNode(externs)
            .usingSerializer(createPrettyPrinter(compiler))
            .isEqualIncludingJsDocTo(expectedRoot);
      } else {
        assertNode(externs).usingSerializer(createPrettyPrinter(compiler)).isEqualTo(expectedRoot);
      }
    } else {
      String externsCode = compiler.toSource(externs);
      String expectedCode = compiler.toSource(expectedRoot);
      assertThat(externsCode).isEqualTo(expectedCode);
    }

    if (warnings != null) {
      StringBuilder warningBuilder = new StringBuilder();
      for (JSError actualWarning : compiler.getWarnings()) {
        warningBuilder.append(actualWarning.description()).append("\n");
      }
      String warningMessage = warningBuilder.toString();
      assertWithMessage("There should be %s warnings. %s", warnings.length, warningMessage)
          .that(compiler.getWarningCount())
          .isEqualTo(warnings.length);
      for (int i = 0; i < warnings.length; i++) {
        DiagnosticType warning = warnings[i].diagnostic;
        assertWithMessage(warningMessage)
            .that(compiler.getWarnings().get(i).type())
            .isEqualTo(warning);
      }
    }
  }

  protected Compiler createCompiler() {
    Compiler compiler = new Compiler();
    compiler.setAllowableFeatures(acceptedLanguage.toFeatureSet());
    if (!webpackModulesById.isEmpty()) {
      compiler.initWebpackMap(ImmutableMap.copyOf(webpackModulesById));
    }
    compiler.setPreferRegexParser(false);
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
        n -> {
          if (n.matchesQualifiedName(name)) {
            matches.add(n);
          }
        });
    return matches;
  }

  /**
   * Returns a node that defines the given qualified name. The returned node should have a valid
   * type defined on it, if type checking occurred. Will return null if no definition is found. Only
   * nodes in the top-level script are traversed.
   */
  protected static Node findDefinition(Compiler compiler, String name) {
    for (Node node = compiler.getJsRoot().getFirstFirstChild();
        node != null;
        node = node.getNext()) {
      switch (node.getToken()) {
        case FUNCTION -> {
          if (name.equals(node.getFirstChild().getString())) {
            return node;
          }
        }
        case VAR, CONST, LET -> {
          if (name.equals(node.getFirstChild().getString())) {
            return node.getFirstChild();
          }
        }
        case EXPR_RESULT -> {
          if (node.getFirstChild().isAssign()
              && node.getFirstFirstChild().matchesQualifiedName(name)) {
            return node.getFirstFirstChild();
          }
        }
        default -> {}
      }
    }
    return null;
  }

  protected static Sources srcs(String srcText) {
    return new FlatSources(maybeCreateSources(GENERATED_SRC_NAME, srcText));
  }

  protected static Sources srcs(String... srcTexts) {
    return new FlatSources(createSources(GENERATED_SRC_NAME, srcTexts));
  }

  protected static Sources srcs(List<SourceFile> files) {
    return new FlatSources(files);
  }

  protected Sources srcs(SourceFile... files) {
    return new FlatSources(Arrays.asList(files));
  }

  protected static Sources srcs(JSChunk... modules) {
    return new ChunkSources(modules);
  }

  protected static Expected expected(String srcText) {
    return new Expected(maybeCreateSources(GENERATED_SRC_NAME, srcText));
  }

  protected static Expected expected(String... srcTexts) {
    return new Expected(createSources(GENERATED_SRC_NAME, srcTexts));
  }

  protected static Expected expected(List<SourceFile> files) {
    return new Expected(files);
  }

  protected Expected expected(SourceFile... files) {
    return new Expected(Arrays.asList(files));
  }

  protected static Expected expected(JSChunk[] chunks) {
    List<String> expectedSrcs = new ArrayList<>();
    for (JSChunk chunk : chunks) {
      for (CompilerInput input : chunk.getInputs()) {
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
    return new Externs(maybeCreateSources(GENERATED_EXTERNS_NAME, externSrc));
  }

  protected static Externs externs(String... srcTexts) {
    return new Externs(createSources(GENERATED_EXTERNS_NAME, srcTexts));
  }

  protected static Externs externs(SourceFile... externs) {
    // Copy SourceFile objects to prevent the externs bit from polluting tests.
    return new Externs(
        stream(externs)
            .map(
                f -> {
                  try {
                    return SourceFile.fromCode(f.getName(), f.getCode());
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                })
            .collect(toImmutableList()));
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
      if (part instanceof Externs ext) {
        checkState(externs == null);
        externs = ext;
      } else if (part instanceof Sources sources) {
        checkState(srcs == null);
        srcs = sources;
      } else if (part instanceof Expected exp) {
        checkState(expected == null);
        expected = exp;
      } else if (part instanceof Diagnostic diagnostic) {
        diagnostics.add(diagnostic);
      } else if (part instanceof Postcondition postcondition) {
        postconditions.add(postcondition);
      } else {
        throw new IllegalStateException("unexpected " + part.getClass().getName());
      }
    }
    if (EXPECTED_SAME.equals(expected)) {
      expected = fromSources(srcs);
    }
    if (externs == null) {
      externs = externs(defaultExternsInputs);
    }
    testInternal(externs, srcs, expected, diagnostics, postconditions);
  }

  private static Expected fromSources(Sources srcs) {
    if (srcs instanceof FlatSources flatSources) {
      return expected(flatSources.sources);
    } else if (srcs instanceof ChunkSources chunkSources) {
      ChunkSources modules = chunkSources;
      return expected(modules.chunks.toArray(new JSChunk[0]));
    } else {
      throw new IllegalStateException("unexpected");
    }
  }

  // TODO(johnlenz): make this a abstract class with a private constructor
  /**
   * Marker interface for configuration parameters of a test invocation. This is essentially a
   * closed union type consisting of four subtypes: (1) Externs, (2) Expected, (3) Sources, and (4)
   * Diagnostic. Sharing a common marker interface, allows specifying any combination of these types
   * to the 'test' method, and makes it very clear what function each parameter serves (since the
   * first three can all be represented as simple strings). Moreover, it reduces the combinatorial
   * explosion of different ways to express the parts (a single string, a list of SourceFiles, a
   * list of modules, a DiagnosticType, a DiagnosticType with an expected message, etc).
   *
   * <p>Note that this API is intended to be a medium-term temporary API while developing and
   * migrating to a more fluent assertion style.
   */
  protected interface TestPart {}

  protected static final class Expected implements TestPart {
    final List<SourceFile> expected;

    Expected(@Nullable List<SourceFile> files) {
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

  protected static final class ChunkSources extends Sources {
    final ImmutableList<JSChunk> chunks;

    ChunkSources(JSChunk[] chunks) {
      this.chunks = ImmutableList.copyOf(chunks);
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
    final int line;
    final int charno;
    final int length;

    Diagnostic(
        CheckLevel level,
        DiagnosticType diagnostic,
        @Nullable NamedPredicate<String> messagePredicate) {
      this(level, diagnostic, messagePredicate, -1, -1, -1);
    }

    Diagnostic(
        CheckLevel level,
        DiagnosticType diagnostic,
        @Nullable NamedPredicate<String> messagePredicate,
        int line,
        int charno,
        int length) {
      this.level = level;
      this.diagnostic = diagnostic;
      this.messagePredicate = messagePredicate;
      this.line = line;
      this.charno = charno;
      this.length = length;
    }

    private Optional<String> formatDiff(JSError error) {
      if (!Objects.equals(diagnostic, error.type())) {
        return Optional.of(
            String.format(
                "diagnostic type <%s> did not match <%s>", error.type().key, diagnostic.key));
      }
      if (messagePredicate != null && !messagePredicate.apply(error.description())) {
        return Optional.of(
            String.format("message <%s> was not <%s>", error.description(), messagePredicate));
      }
      if (line != -1 && error.lineno() != line) {
        return Optional.of(String.format("line <%d> did not match <%d>", error.lineno(), line));
      }
      if (charno != -1 && error.charno() != charno) {
        return Optional.of(String.format("charno <%d> did not match <%d>", error.charno(), charno));
      }
      if (length != -1 && error.length() != length) {
        return Optional.of(String.format("length <%d> did not match <%d>", error.length(), length));
      }
      return Optional.empty();
    }

    public Diagnostic withMessage(final String expectedRaw) {
      String expected = expectedRaw.trim();
      checkState(messagePredicate == null);
      return new Diagnostic(
          level,
          diagnostic,
          NamedPredicate.of(actual -> actual.trim().equals(expected), "\"" + expected + "\""));
    }

    public Diagnostic withMessageContaining(final String substring) {
      checkState(messagePredicate == null);
      return new Diagnostic(
          level,
          diagnostic,
          NamedPredicate.of(
              message -> message.contains(substring), "containing \"" + substring + "\""));
    }

    public Diagnostic withLocation(int line, int charno, int length) {
      return new Diagnostic(level, diagnostic, messagePredicate, line, charno, length);
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

  private static final Correspondence<JSError, Diagnostic> DIAGNOSTIC_CORRESPONDENCE =
      Correspondence.from(
              (JSError actual, Diagnostic expected) -> expected.formatDiff(actual).isEmpty(),
              "is a JSError matching")
          .formattingDiffsUsing((actual, expected) -> expected.formatDiff(actual).get());

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

  private static Function<Node, String> createPrettyPrinter(Compiler compiler) {
    return (n) ->
        new CodePrinter.Builder(n)
            .setCompilerOptions(compiler.getOptions())
            .setPrettyPrint(true)
            .build();
  }
}
