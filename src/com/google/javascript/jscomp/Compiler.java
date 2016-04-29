/*
 * Copyright 2004 The Closure Compiler Authors.
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

import com.google.common.annotations.GwtIncompatible;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.debugging.sourcemap.proto.Mapping.OriginalMapping;
import com.google.javascript.jscomp.CompilerOptions.DevMode;
import com.google.javascript.jscomp.ReferenceCollectingCallback.ReferenceCollection;
import com.google.javascript.jscomp.TypeValidator.TypeMismatch;
import com.google.javascript.jscomp.deps.SortedDependencies.MissingProvideException;
import com.google.javascript.jscomp.parsing.Config;
import com.google.javascript.jscomp.parsing.ParserRunner;
import com.google.javascript.jscomp.parsing.parser.trees.Comment;
import com.google.javascript.jscomp.type.ChainableReverseAbstractInterpreter;
import com.google.javascript.jscomp.type.ClosureReverseAbstractInterpreter;
import com.google.javascript.jscomp.type.ReverseAbstractInterpreter;
import com.google.javascript.jscomp.type.SemanticReverseAbstractInterpreter;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TypeIRegistry;
import com.google.javascript.rhino.jstype.JSTypeRegistry;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;

/**
 * Compiler (and the other classes in this package) does the following:
 * <ul>
 * <li>parses JS code
 * <li>checks for undefined variables
 * <li>performs optimizations such as constant folding and constants inlining
 * <li>renames variables (to short names)
 * <li>outputs compact JavaScript code
 * </ul>
 *
 * External variables are declared in 'externs' files. For instance, the file
 * may include definitions for global javascript/browser objects such as
 * window, document.
 *
 */
public class Compiler extends AbstractCompiler {
  static final String SINGLETON_MODULE_NAME = "$singleton$";

  static final DiagnosticType MODULE_DEPENDENCY_ERROR =
      DiagnosticType.error("JSC_MODULE_DEPENDENCY_ERROR",
          "Bad dependency: {0} -> {1}. "
              + "Modules must be listed in dependency order.");

  static final DiagnosticType MISSING_ENTRY_ERROR = DiagnosticType.error(
      "JSC_MISSING_ENTRY_ERROR",
      "required entry point \"{0}\" never provided");

  static final DiagnosticType MISSING_MODULE_ERROR = DiagnosticType.error(
      "JSC_MISSING_ENTRY_ERROR",
      "unknown module \"{0}\" specified in entry point spec");

  // Used in PerformanceTracker
  static final String PARSING_PASS_NAME = "parseInputs";
  static final String CROSS_MODULE_CODE_MOTION_NAME = "crossModuleCodeMotion";
  static final String CROSS_MODULE_METHOD_MOTION_NAME =
      "crossModuleMethodMotion";

  private static final String CONFIG_RESOURCE =
      "com.google.javascript.jscomp.parsing.ParserConfig";

  CompilerOptions options = null;

  private PassConfig passes = null;

  // The externs inputs
  private List<CompilerInput> externs;

  // The JS source modules
  private List<JSModule> modules;

  // The graph of the JS source modules. Must be null if there are less than
  // 2 modules, because we use this as a signal for which passes to run.
  private JSModuleGraph moduleGraph;

  // The JS source inputs
  private List<CompilerInput> inputs;

  // error manager to which error management is delegated
  private ErrorManager errorManager;

  // Warnings guard for filtering warnings.
  private WarningsGuard warningsGuard;

  // Compile-time injected libraries. The node points to the last node of
  // the library, so code can be inserted after.
  private final Map<String, Node> injectedLibraries = new LinkedHashMap<>();

  // Node of the final injected library. Future libraries will be injected
  // after this node.
  private Node lastInjectedLibrary;

  // Parse tree root nodes
  Node externsRoot;
  Node jsRoot;
  Node externAndJsRoot;

  // Used for debugging; to see the compiled code between passes
  private String lastJsSource = null;

  /** @see #getLanguageMode() */
  private CompilerOptions.LanguageMode languageMode =
      CompilerOptions.LanguageMode.ECMASCRIPT3;

  private Map<InputId, CompilerInput> inputsById;

  // Function to load source files from disk or memory.
  private Function<String, SourceFile> originalSourcesLoader =
      new Function<String, SourceFile>() {
        @Override
        public SourceFile apply(String filename) {
          return SourceFile.fromFile(filename);
        }
      };

  // Original sources referenced by the source maps.
  private ConcurrentHashMap<String, SourceFile> sourceMapOriginalSources
      = new ConcurrentHashMap<>();

  // Map from filenames to lists of all the comments in each file.
  private Map<String, List<Comment>> commentsPerFile = new HashMap<>();

  /** The source code map */
  private SourceMap sourceMap;

  /** The externs created from the exports.  */
  private String externExports = null;

  /**
   * Ids for function inlining so that each declared name remains
   * unique.
   */
  private int uniqueNameId = 0;

  /**
   * Whether to assume there are references to the RegExp Global object
   * properties.
   */
  private boolean hasRegExpGlobalReferences = true;

  /** The function information map */
  private FunctionInformationMap functionInformationMap;

  /** Debugging information */
  private final StringBuilder debugLog = new StringBuilder();

  /** Detects Google-specific coding conventions. */
  CodingConvention defaultCodingConvention = new ClosureCodingConvention();

  private JSTypeRegistry typeRegistry;
  private Config parserConfig = null;
  private Config externsParserConfig = null;

  private ReverseAbstractInterpreter abstractInterpreter;
  private TypeValidator typeValidator;
  // The compiler can ask phaseOptimizer for things like which pass is currently
  // running, or which functions have been changed by optimizations
  private PhaseOptimizer phaseOptimizer = null;

  public PerformanceTracker tracker;

  // Used by optimize-returns, optimize-parameters and remove-unused-variables
  private SimpleDefinitionFinder defFinder = null;

  // For use by the new type inference
  private GlobalTypeInfo symbolTable;

  // The oldErrorReporter exists so we can get errors from the JSTypeRegistry.
  private final com.google.javascript.rhino.ErrorReporter oldErrorReporter =
      RhinoErrorReporter.forOldRhino(this);

  // This error reporter gets the messages from the current Rhino parser.
  private final ErrorReporter defaultErrorReporter =
      RhinoErrorReporter.forOldRhino(this);

  /** Error strings used for reporting JSErrors */
  public static final DiagnosticType OPTIMIZE_LOOP_ERROR = DiagnosticType.error(
      "JSC_OPTIMIZE_LOOP_ERROR",
      "Exceeded max number of optimization iterations: {0}");
  public static final DiagnosticType MOTION_ITERATIONS_ERROR =
      DiagnosticType.error("JSC_OPTIMIZE_LOOP_ERROR",
          "Exceeded max number of code motion iterations: {0}");

  private final CompilerExecutor compilerExecutor = new CompilerExecutor();

  /**
   * Logger for the whole com.google.javascript.jscomp domain -
   * setting configuration for this logger affects all loggers
   * in other classes within the compiler.
   */
  private static final Logger logger =
      Logger.getLogger("com.google.javascript.jscomp");

  private final PrintStream outStream;

  private GlobalVarReferenceMap globalRefMap = null;

  private volatile double progress = 0.0;
  private String lastPassName;

  private Set<String> externProperties = null;

  private static final Joiner pathJoiner = Joiner.on(File.separator);

  /**
   * Creates a Compiler that reports errors and warnings to its logger.
   */
  public Compiler() {
    this((PrintStream) null);
  }

  /**
   * Creates a Compiler that reports errors and warnings to an output stream.
   */
  public Compiler(PrintStream stream) {
    addChangeHandler(recentChange);
    outStream = stream;
  }

  /**
   * Creates a Compiler that uses a custom error manager.
   */
  public Compiler(ErrorManager errorManager) {
    this();
    setErrorManager(errorManager);
  }

  /**
   * Sets the error manager.
   *
   * @param errorManager the error manager, it cannot be {@code null}
   */
  public void setErrorManager(ErrorManager errorManager) {
    Preconditions.checkNotNull(
        errorManager, "the error manager cannot be null");
    this.errorManager = errorManager;
  }

  /**
   * Creates a message formatter instance corresponding to the value of
   * {@link CompilerOptions}.
   */
  private MessageFormatter createMessageFormatter() {
    boolean colorize = options.shouldColorizeErrorOutput();
    return options.errorFormat.toFormatter(this, colorize);
  }

  @VisibleForTesting
  void setOriginalSourcesLoader(
      Function<String, SourceFile> originalSourcesLoader) {
    this.originalSourcesLoader = originalSourcesLoader;
  }

  /**
   * Initializes the compiler options. It's called as part of a normal compile() job.
   * Public for the callers that are not doing a normal compile() job.
   */
  public void initOptions(CompilerOptions options) {
    this.options = options;
    this.languageMode = options.getLanguageIn();
    if (errorManager == null) {
      if (outStream == null) {
        setErrorManager(
            new LoggerErrorManager(createMessageFormatter(), logger));
      } else {
        PrintStreamErrorManager printer =
            new PrintStreamErrorManager(createMessageFormatter(), outStream);
        printer.setSummaryDetailLevel(options.summaryDetailLevel);
        setErrorManager(printer);
      }
    }

    reconcileOptionsWithGuards();

    if (options.legacyCodeCompile) {
      options.disambiguateProperties = false;
      options.ambiguateProperties = false;
      options.useNonStrictWarningsGuard();
    }

    initWarningsGuard(options.getWarningsGuard());
  }

  void initWarningsGuard(WarningsGuard warningsGuard) {
    this.warningsGuard = new ComposeWarningsGuard(
        new SuppressDocWarningsGuard(getDiagnosticGroups().getRegisteredGroups()), warningsGuard);
  }

  /**
   * When the CompilerOptions and its WarningsGuard overlap, reconcile
   * any discrepencies.
   */
  protected void reconcileOptionsWithGuards() {
    // DiagnosticGroups override the plain checkTypes option.
    if (options.enables(DiagnosticGroups.CHECK_TYPES)) {
      options.checkTypes = true;
    } else if (options.disables(DiagnosticGroups.CHECK_TYPES)) {
      options.checkTypes = false;
    } else if (!options.checkTypes) {
      // If DiagnosticGroups did not override the plain checkTypes
      // option, and checkTypes is enabled, then turn off the
      // parser type warnings.
      options.setWarningLevel(
          DiagnosticGroup.forType(
              RhinoErrorReporter.TYPE_PARSE_ERROR),
          CheckLevel.OFF);
    }
    // With NTI, we still need OTI to run because the later passes that use
    // types only understand OTI types at the moment.
    // But we do not want to see the warnings from OTI.
    if (options.getNewTypeInference()) {
      options.checkTypes = true;
      if (!options.reportOTIErrorsUnderNTI) {
        options.setWarningLevel(
            DiagnosticGroups.OLD_CHECK_TYPES,
            CheckLevel.OFF);
        options.setWarningLevel(
            DiagnosticGroups.OLD_REPORT_UNKNOWN_TYPES,
            CheckLevel.OFF);
        options.setWarningLevel(
            FunctionTypeBuilder.ALL_DIAGNOSTICS,
            CheckLevel.OFF);
      }
      options.setWarningLevel(
          DiagnosticGroup.forType(RhinoErrorReporter.TYPE_PARSE_ERROR),
          CheckLevel.WARNING);
    }

    if (options.checkGlobalThisLevel.isOn() &&
        !options.disables(DiagnosticGroups.GLOBAL_THIS)) {
      options.setWarningLevel(
          DiagnosticGroups.GLOBAL_THIS,
          options.checkGlobalThisLevel);
    }

    if (options.getLanguageIn().isStrict()) {
      options.setWarningLevel(
          DiagnosticGroups.ES5_STRICT,
          CheckLevel.ERROR);
    }

    // All passes must run the variable check. This synthesizes
    // variables later so that the compiler doesn't crash. It also
    // checks the externs file for validity. If you don't want to warn
    // about missing variable declarations, we shut that specific
    // error off.
    if (!options.checkSymbols &&
        !options.enables(DiagnosticGroups.CHECK_VARIABLES)) {
      options.setWarningLevel(
          DiagnosticGroups.CHECK_VARIABLES, CheckLevel.OFF);
    }
  }

  /**
   * Initializes the instance state needed for a compile job.
   */
  public <T1 extends SourceFile, T2 extends SourceFile> void init(
      List<T1> externs,
      List<T2> inputs,
      CompilerOptions options) {
    JSModule module = new JSModule(SINGLETON_MODULE_NAME);
    for (SourceFile input : inputs) {
      module.add(input);
    }

    List<JSModule> modules = new ArrayList<>(1);
    modules.add(module);
    initModules(externs, modules, options);
  }

  /**
   * Initializes the instance state needed for a compile job if the sources
   * are in modules.
   */
  public <T extends SourceFile> void initModules(
      List<T> externs, List<JSModule> modules, CompilerOptions options) {
    initOptions(options);

    checkFirstModule(modules);
    fillEmptyModules(modules);

    this.externs = makeCompilerInput(externs, true);

    // Generate the module graph, and report any errors in the module
    // specification as errors.
    this.modules = modules;
    if (modules.size() > 1) {
      try {
        this.moduleGraph = new JSModuleGraph(modules);
      } catch (JSModuleGraph.ModuleDependenceException e) {
        // problems with the module format.  Report as an error.  The
        // message gives all details.
        report(JSError.make(MODULE_DEPENDENCY_ERROR,
                e.getModule().getName(), e.getDependentModule().getName()));
        return;
      }
    } else {
      this.moduleGraph = null;
    }

    this.inputs = getAllInputsFromModules(modules);
    initBasedOnOptions();

    initInputsByIdMap();

    initAST();
  }

  /**
   * Do any initialization that is dependent on the compiler options.
   */
  private void initBasedOnOptions() {
    // Create the source map if necessary.
    if (options.sourceMapOutputPath != null) {
      sourceMap = options.sourceMapFormat.getInstance();
      sourceMap.setPrefixMappings(options.sourceMapLocationMappings);
    }
  }

  private <T extends SourceFile> List<CompilerInput> makeCompilerInput(
      List<T> files, boolean isExtern) {
    List<CompilerInput> inputs = new ArrayList<>(files.size());
    for (T file : files) {
      inputs.add(new CompilerInput(file, isExtern));
    }
    return inputs;
  }

  private static final DiagnosticType EMPTY_MODULE_LIST_ERROR =
      DiagnosticType.error("JSC_EMPTY_MODULE_LIST_ERROR",
          "At least one module must be provided");

  private static final DiagnosticType EMPTY_ROOT_MODULE_ERROR =
      DiagnosticType.error("JSC_EMPTY_ROOT_MODULE_ERROR",
          "Root module ''{0}'' must contain at least one source code input");

  /**
   * Verifies that at least one module has been provided and that the first one
   * has at least one source code input.
   */
  private void checkFirstModule(List<JSModule> modules) {
    if (modules.isEmpty()) {
      report(JSError.make(EMPTY_MODULE_LIST_ERROR));
    } else if (modules.get(0).getInputs().isEmpty() && modules.size() > 1) {
      // The root module may only be empty if there is exactly 1 module.
      report(JSError.make(EMPTY_ROOT_MODULE_ERROR,
          modules.get(0).getName()));
    }
  }

  /**
   * Empty modules get an empty "fill" file, so that we can move code into
   * an empty module.
   */
  static String createFillFileName(String moduleName) {
    return moduleName + "$fillFile";
  }

  /**
   * Creates an OS specific path string from parts
   */
  public static String joinPathParts(String... pathParts) {
    return pathJoiner.join(pathParts);
  }

  /**
   * Fill any empty modules with a place holder file. It makes any cross module
   * motion easier.
   */
  private static void fillEmptyModules(List<JSModule> modules) {
    for (JSModule module : modules) {
      if (module.getInputs().isEmpty()) {
        module.add(SourceFile.fromCode(
            createFillFileName(module.getName()), ""));
      }
    }
  }

  /**
   * Rebuilds the internal list of inputs by iterating over all modules.
   * This is necessary if inputs have been added to or removed from a module
   * after the {@link #init(List, List, CompilerOptions)} call.
   */
  public void rebuildInputsFromModules() {
    inputs = getAllInputsFromModules(modules);
    initInputsByIdMap();
  }

  /**
   * Builds a single list of all module inputs. Verifies that it contains no
   * duplicates.
   */
  private static List<CompilerInput> getAllInputsFromModules(
      List<JSModule> modules) {
    List<CompilerInput> inputs = new ArrayList<>();
    Map<String, JSModule> inputMap = new HashMap<>();
    for (JSModule module : modules) {
      for (CompilerInput input : module.getInputs()) {
        String inputName = input.getName();

        // NOTE(nicksantos): If an input is in more than one module,
        // it will show up twice in the inputs list, and then we
        // will get an error down the line.
        inputs.add(input);
        inputMap.put(inputName, module);
      }
    }
    return inputs;
  }

  static final DiagnosticType DUPLICATE_INPUT =
      DiagnosticType.error("JSC_DUPLICATE_INPUT", "Duplicate input: {0}");
  static final DiagnosticType DUPLICATE_EXTERN_INPUT =
      DiagnosticType.error("JSC_DUPLICATE_EXTERN_INPUT",
          "Duplicate extern input: {0}");

  /**
   * Returns the relative path, resolved relative to the base path, where the
   * base path is interpreted as a filename rather than a directory. E.g.:
   *   getRelativeTo("../foo/bar.js", "baz/bam/qux.js") --> "baz/foo/bar.js"
   */
  private static String getRelativeTo(String relative, String base) {
    return FileSystems.getDefault().getPath(base)
        .resolveSibling(relative)
        .normalize()
        .toString()
        .replace(File.separator, "/");
  }

  /**
   * Creates a map to make looking up an input by name fast. Also checks for
   * duplicate inputs.
   */
  void initInputsByIdMap() {
    inputsById = new HashMap<>();
    for (CompilerInput input : externs) {
      InputId id = input.getInputId();
      CompilerInput previous = putCompilerInput(id, input);
      if (previous != null) {
        report(JSError.make(DUPLICATE_EXTERN_INPUT, input.getName()));
      }
    }
    for (CompilerInput input : inputs) {
      InputId id = input.getInputId();
      CompilerInput previous = putCompilerInput(id, input);
      if (previous != null) {
        report(JSError.make(DUPLICATE_INPUT, input.getName()));
      }
    }
  }

  /**
   * Sets up the skeleton of the AST (the externs and root).
   */
  private void initAST() {
    jsRoot = IR.block();
    jsRoot.setIsSyntheticBlock(true);

    externsRoot = IR.block();
    externsRoot.setIsSyntheticBlock(true);

    externAndJsRoot = IR.block(externsRoot, jsRoot);
    externAndJsRoot.setIsSyntheticBlock(true);
  }

  public Result compile(
      SourceFile extern, SourceFile input, CompilerOptions options) {
    return compile(ImmutableList.of(extern), ImmutableList.of(input), options);
  }

  /**
   * Compiles a list of inputs.
   */
  public <T1 extends SourceFile, T2 extends SourceFile> Result compile(
      List<T1> externs, List<T2> inputs, CompilerOptions options) {
    // The compile method should only be called once.
    Preconditions.checkState(jsRoot == null);

    try {
      init(externs, inputs, options);
      if (hasErrors()) {
        return getResult();
      }
      return compile();
    } finally {
      Tracer t = newTracer("generateReport");
      errorManager.generateReport();
      stopTracer(t, "generateReport");
    }
  }

  /**
   * Compiles a list of modules.
   */
  public <T extends SourceFile> Result compileModules(List<T> externs,
      List<JSModule> modules, CompilerOptions options) {
    // The compile method should only be called once.
    Preconditions.checkState(jsRoot == null);

    try {
      initModules(externs, modules, options);
      if (hasErrors()) {
        return getResult();
      }
      return compile();
    } finally {
      Tracer t = newTracer("generateReport");
      errorManager.generateReport();
      stopTracer(t, "generateReport");
    }
  }

  private Result compile() {
    return runInCompilerThread(new Callable<Result>() {
      @Override
      public Result call() throws Exception {
        compileInternal();
        return getResult();
      }
    });
  }

  /**
   * Disable threads. This is for clients that run on AppEngine and
   * don't have threads.
   */
  public void disableThreads() {
    compilerExecutor.disableThreads();
  }

  /**
   * Sets the timeout when Compiler is run in a thread
   * @param timeout seconds to wait before timeout
   */
  public void setTimeout(int timeout) {
    compilerExecutor.setTimeout(timeout);
  }

  /**
   * The primary purpose of this method is to run the provided code with a larger than standard
   * stack.
   */
  <T> T runInCompilerThread(Callable<T> callable) {
    return compilerExecutor.runInCompilerThread(callable, options != null && options.tracer.isOn());
  }

  private void compileInternal() {
    setProgress(0.0, null);
    CompilerOptionsPreprocessor.preprocess(options);
    parse();
    // 15 percent of the work is assumed to be for parsing (based on some
    // minimal analysis on big JS projects, of course this depends on options)
    setProgress(0.15, "parse");
    if (hasErrors()) {
      return;
    }

    if (!precheck()) {
      return;
    }

    if (options.skipNonTranspilationPasses) {
      // i.e. whitespace-only mode, which will not work with goog.module without:
      whitespaceOnlyPasses();
    }

    if (!options.skipNonTranspilationPasses || options.lowerFromEs6()) {
      check();
      if (hasErrors()) {
        return;
      }

      // IDE-mode is defined to stop here, before the heavy rewriting begins.
      if (!options.checksOnly && !options.ideMode) {
        optimize();
      }
    }

    if (options.recordFunctionInformation) {
      recordFunctionInformation();
    }

    if (options.devMode == DevMode.START_AND_END) {
      runSanityCheck();
    }
    setProgress(1.0, "recordFunctionInformation");

    if (tracker != null) {
      tracker.outputTracerReport(outStream == null ? System.out : outStream);
    }
  }

  public void parse() {
    parseInputs();
  }

  PassConfig getPassConfig() {
    if (passes == null) {
      passes = createPassConfigInternal();
    }
    return passes;
  }

  /**
   * Create the passes object. Clients should use setPassConfig instead of
   * overriding this.
   */
  PassConfig createPassConfigInternal() {
    return new DefaultPassConfig(options);
  }

  /**
   * @param passes The PassConfig to use with this Compiler.
   * @throws NullPointerException if passes is null
   * @throws IllegalStateException if this.passes has already been assigned
   */
  public void setPassConfig(PassConfig passes) {
    // Important to check for null because if setPassConfig(null) is
    // called before this.passes is set, getPassConfig() will create a
    // new PassConfig object and use that, which is probably not what
    // the client wanted since they probably meant to use their
    // own PassConfig object.
    Preconditions.checkNotNull(passes);
    Preconditions.checkState(this.passes == null, "setPassConfig was already called");
    this.passes = passes;
  }

  /**
   * Carry out any special checks or procedures that need to be done before
   * proceeding with rest of the compilation process.
   *
   * @return true, to continue with compilation
   */
  boolean precheck() {
    return true;
  }

  public void whitespaceOnlyPasses() {
    Tracer t = newTracer("runWhitespaceOnlyPasses");
    try {
      for (PassFactory pf : getPassConfig().getWhitespaceOnlyPasses()) {
        pf.create(this).process(externsRoot, jsRoot);
      }
    } finally {
      stopTracer(t, "runWhitespaceOnlyPasses");
    }
  }

  public void check() {
    runCustomPasses(CustomPassExecutionTime.BEFORE_CHECKS);

    // We are currently only interested in check-passes for progress reporting
    // as it is used for IDEs, that's why the maximum progress is set to 1.0.
    phaseOptimizer = new PhaseOptimizer(this, tracker,
        new PhaseOptimizer.ProgressRange(getProgress(), 1.0));
    if (options.devMode == DevMode.EVERY_PASS) {
      phaseOptimizer.setSanityCheck(sanityCheck);
    }
    if (options.getCheckDeterminism()) {
      phaseOptimizer.setPrintAstHashcodes(true);
    }
    phaseOptimizer.consume(getPassConfig().getChecks());
    phaseOptimizer.process(externsRoot, jsRoot);
    if (hasErrors()) {
      return;
    }

    if (options.getTweakProcessing().shouldStrip() ||
        !options.stripTypes.isEmpty() ||
        !options.stripNameSuffixes.isEmpty() ||
        !options.stripTypePrefixes.isEmpty() ||
        !options.stripNamePrefixes.isEmpty()) {
      stripCode(options.stripTypes, options.stripNameSuffixes,
          options.stripTypePrefixes, options.stripNamePrefixes);
    }

    runCustomPasses(CustomPassExecutionTime.BEFORE_OPTIMIZATIONS);
    phaseOptimizer = null;
  }

  private void externExports() {
    logger.fine("Creating extern file for exports");
    startPass("externExports");

    ExternExportsPass pass = new ExternExportsPass(this);
    process(pass);

    externExports = pass.getGeneratedExterns();

    endPass();
  }

  @Override
  void process(CompilerPass p) {
    p.process(externsRoot, jsRoot);
  }

  private final PassFactory sanityCheck =
      new PassFactory("sanityCheck", false) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new SanityCheck(compiler);
    }
  };

  private void maybeSanityCheck() {
    if (options.devMode == DevMode.EVERY_PASS) {
      runSanityCheck();
    }
  }

  private void runSanityCheck() {
    sanityCheck.create(this).process(externsRoot, jsRoot);
  }

  /**
   * Strips code for smaller compiled code. This is useful for removing debug
   * statements to prevent leaking them publicly.
   */
  void stripCode(Set<String> stripTypes, Set<String> stripNameSuffixes,
      Set<String> stripTypePrefixes, Set<String> stripNamePrefixes) {
    logger.fine("Strip code");
    startPass("stripCode");
    StripCode r = new StripCode(this, stripTypes, stripNameSuffixes,
        stripTypePrefixes, stripNamePrefixes);
    if (options.getTweakProcessing().shouldStrip()) {
      r.enableTweakStripping();
    }
    process(r);
    endPass();
  }

  /**
   * Runs custom passes that are designated to run at a particular time.
   */
  private void runCustomPasses(CustomPassExecutionTime executionTime) {
    if (options.customPasses != null) {
      Tracer t = newTracer("runCustomPasses");
      try {
        for (CompilerPass p : options.customPasses.get(executionTime)) {
          process(p);
        }
      } finally {
        stopTracer(t, "runCustomPasses");
      }
    }
  }

  private Tracer currentTracer = null;
  private String currentPassName = null;

  /**
   * Marks the beginning of a pass.
   */
  void startPass(String passName) {
    Preconditions.checkState(currentTracer == null);
    currentPassName = passName;
    currentTracer = newTracer(passName);
  }

  /**
   * Marks the end of a pass.
   */
  void endPass() {
    Preconditions.checkState(currentTracer != null,
        "Tracer should not be null at the end of a pass.");
    stopTracer(currentTracer, currentPassName);
    currentPassName = null;
    currentTracer = null;

    maybeSanityCheck();
  }

  @Override
  final void beforePass(String passName) {
    // does nothing for now
  }

  @Override
  final void afterPass(String passName) {
    if (options.printSourceAfterEachPass) {
      String currentJsSource = toSource();
      if (!currentJsSource.equals(this.lastJsSource)) {
        System.out.println();
        System.out.println("// " + passName + " yields:");
        System.out.println("// ************************************");
        System.out.println(currentJsSource);
        lastJsSource = currentJsSource;
      }
    }
  }

  /**
   * Returns a new tracer for the given pass name.
   */
  Tracer newTracer(String passName) {
    String comment = passName
        + (recentChange.hasCodeChanged() ? " on recently changed AST" : "");
    if (options.tracer.isOn()) {
      tracker.recordPassStart(passName, true);
    }
    return new Tracer("Compiler", comment);
  }

  void stopTracer(Tracer t, String passName) {
    long result = t.stop();
    if (options.tracer.isOn()) {
      tracker.recordPassStop(passName, result);
    }
  }

  /**
   * Returns the result of the compilation.
   */
  public Result getResult() {
    PassConfig.State state = getPassConfig().getIntermediateState();
    return new Result(getErrors(), getWarnings(), debugLog.toString(),
        state.variableMap, state.propertyMap,
        state.anonymousFunctionNameMap, state.stringMap, functionInformationMap,
        sourceMap, externExports, state.cssNames, state.idGeneratorMap);
  }

  /**
   * Returns the array of errors (never null).
   */
  public JSError[] getErrors() {
    if (errorManager == null) {
      return new JSError[] {};
    }
    return errorManager.getErrors();
  }

  /**
   * Returns the array of warnings (never null).
   */
  public JSError[] getWarnings() {
    if (errorManager == null) {
      return new JSError[] {};
    }
    return errorManager.getWarnings();
  }

  @Override
  public Node getRoot() {
    return externAndJsRoot;
  }

  @Override
  CompilerOptions.LanguageMode getLanguageMode() {
    return languageMode;
  }

  @Override
  void setLanguageMode(CompilerOptions.LanguageMode mode) {
    languageMode = mode;
  }

  /**
   * Creates a new id for making unique names.
   */
  private int nextUniqueNameId() {
    return uniqueNameId++;
  }

  /**
   * Resets the unique name id counter
   */
  @VisibleForTesting
  void resetUniqueNameId() {
    uniqueNameId = 0;
  }

  @Override
  Supplier<String> getUniqueNameIdSupplier() {
    final Compiler self = this;
    return new Supplier<String>() {
      @Override
      public String get() {
        return String.valueOf(self.nextUniqueNameId());
      }
    };
  }

  @Override
  boolean areNodesEqualForInlining(Node n1, Node n2) {
    if (options.ambiguateProperties ||
        options.disambiguateProperties) {
      // The type based optimizations require that type information is preserved
      // during other optimizations.
      return n1.isEquivalentToTyped(n2);
    } else {
      return n1.isEquivalentTo(n2);
    }
  }

  //------------------------------------------------------------------------
  // Inputs
  //------------------------------------------------------------------------

  // TODO(nicksantos): Decide which parts of these belong in an AbstractCompiler
  // interface, and which ones should always be injected.

  @Override
  public CompilerInput getInput(InputId id) {
    return inputsById.get(id);
  }

  /**
   * Removes an input file from AST.
   * @param id The id of the input to be removed.
   */
  protected void removeExternInput(InputId id) {
    CompilerInput input = getInput(id);
    if (input == null) {
      return;
    }
    Preconditions.checkState(input.isExtern(), "Not an extern input: %s", input.getName());
    inputsById.remove(id);
    externs.remove(input);
    Node root = input.getAstRoot(this);
    if (root != null) {
      root.detachFromParent();
    }
  }

  // Where to put a new synthetic externs file.
  private static enum SyntheticExternsPosition {
    START,
    END
  }

  CompilerInput newExternInput(String name, SyntheticExternsPosition pos) {
    SourceAst ast = new SyntheticAst(name);
    if (inputsById.containsKey(ast.getInputId())) {
      throw new IllegalArgumentException("Conflicting externs name: " + name);
    }
    CompilerInput input = new CompilerInput(ast, true);
    putCompilerInput(input.getInputId(), input);
    if (pos == SyntheticExternsPosition.START) {
      externsRoot.addChildToFront(ast.getAstRoot(this));
      externs.add(0, input);
    } else {
      externsRoot.addChildToBack(ast.getAstRoot(this));
      externs.add(input);
    }
    return input;
  }

  CompilerInput putCompilerInput(InputId id, CompilerInput input) {
    if (inputsById == null) {
      inputsById = new HashMap<>();
    }
    input.setCompiler(this);
    return inputsById.put(id, input);
  }

  /**
   * Replace a source input dynamically. Intended for incremental
   * re-compilation.
   *
   * If the new source input doesn't parse, then keep the old input
   * in the AST and return false.
   *
   * @return Whether the new AST was attached successfully.
   */
  boolean replaceIncrementalSourceAst(JsAst ast) {
    CompilerInput oldInput = getInput(ast.getInputId());
    Preconditions.checkNotNull(oldInput, "No input to replace: %s", ast.getInputId().getIdName());
    Node newRoot = ast.getAstRoot(this);
    if (newRoot == null) {
      return false;
    }

    Node oldRoot = oldInput.getAstRoot(this);
    if (oldRoot != null) {
      oldRoot.getParent().replaceChild(oldRoot, newRoot);
    } else {
      getRoot().getLastChild().addChildToBack(newRoot);
    }

    CompilerInput newInput = new CompilerInput(ast);
    putCompilerInput(ast.getInputId(), newInput);

    JSModule module = oldInput.getModule();
    if (module != null) {
      module.addAfter(newInput, oldInput);
      module.remove(oldInput);
    }

    // Verify the input id is set properly.
    Preconditions.checkState(
        newInput.getInputId().equals(oldInput.getInputId()));
    InputId inputIdOnAst = newInput.getAstRoot(this).getInputId();
    Preconditions.checkState(newInput.getInputId().equals(inputIdOnAst));

    inputs.remove(oldInput);
    return true;
  }

  /**
   * Add a new source input dynamically. Intended for incremental compilation.
   * <p>
   * If the new source input doesn't parse, it will not be added, and a false
   * will be returned.
   *
   * @param ast the JS Source to add.
   * @return true if the source was added successfully, false otherwise.
   * @throws IllegalStateException if an input for this ast already exists.
   */
  boolean addNewSourceAst(JsAst ast) {
    CompilerInput oldInput = getInput(ast.getInputId());
    if (oldInput != null) {
      throw new IllegalStateException(
          "Input already exists: " + ast.getInputId().getIdName());
    }
    Node newRoot = ast.getAstRoot(this);
    if (newRoot == null) {
      return false;
    }

    getRoot().getLastChild().addChildToBack(newRoot);

    CompilerInput newInput = new CompilerInput(ast);

    // TODO(tylerg): handle this for multiple modules at some point.
    if (moduleGraph == null && !modules.isEmpty()) {
      // singleton module
      modules.get(0).add(newInput);
    }

    putCompilerInput(ast.getInputId(), newInput);

    return true;
  }

  @Override
  JSModuleGraph getModuleGraph() {
    return moduleGraph;
  }

  /**
   * Gets a module graph. This will always return a module graph, even
   * in the degenerate case when there's only one module.
   */
  JSModuleGraph getDegenerateModuleGraph() {
    return moduleGraph == null ? new JSModuleGraph(modules) : moduleGraph;
  }

  @Override
  public TypeIRegistry getTypeIRegistry() {
    return getTypeRegistry();
  }

  @Override
  public JSTypeRegistry getTypeRegistry() {
    if (typeRegistry == null) {
      typeRegistry = new JSTypeRegistry(oldErrorReporter);
    }
    return typeRegistry;
  }

  @Override
  void forwardDeclareType(String typeName) {
    // Always add it to the old type registry, since OTI runs after NTI to
    // provide types for the remaining passes.
    // TODO(dimvar): change this when we stop running OTI after NTI.
    getTypeRegistry().forwardDeclareType(typeName);
    if (this.options.getNewTypeInference()) {
      getSymbolTable().addUnknownTypeName(typeName);
    }
  }

  @Override
  // Only used by jsdev
  public MemoizedScopeCreator getTypedScopeCreator() {
    return getPassConfig().getTypedScopeCreator();
  }

  @SuppressWarnings("unchecked")
  DefaultPassConfig ensureDefaultPassConfig() {
    PassConfig passes = getPassConfig().getBasePassConfig();
    Preconditions.checkState(passes instanceof DefaultPassConfig,
        "PassConfigs must eventually delegate to the DefaultPassConfig");
    return (DefaultPassConfig) passes;
  }

  public SymbolTable buildKnownSymbolTable() {
    SymbolTable symbolTable = new SymbolTable(getTypeRegistry());

    MemoizedScopeCreator typedScopeCreator = getTypedScopeCreator();
    if (typedScopeCreator != null) {
      symbolTable.addScopes(typedScopeCreator.getAllMemoizedScopes());
      symbolTable.addSymbolsFrom(typedScopeCreator);
    } else {
      symbolTable.findScopes(this, externsRoot, jsRoot);
    }

    GlobalNamespace globalNamespace =
        ensureDefaultPassConfig().getGlobalNamespace();
    if (globalNamespace != null) {
      symbolTable.addSymbolsFrom(globalNamespace);
    }

    ReferenceCollectingCallback refCollector =
        new ReferenceCollectingCallback(
            this, ReferenceCollectingCallback.DO_NOTHING_BEHAVIOR);
    NodeTraversal.traverse(this, getRoot(), refCollector);
    symbolTable.addSymbolsFrom(refCollector);

    PreprocessorSymbolTable preprocessorSymbolTable =
        ensureDefaultPassConfig().getPreprocessorSymbolTable();
    if (preprocessorSymbolTable != null) {
      symbolTable.addSymbolsFrom(preprocessorSymbolTable);
    }

    symbolTable.fillNamespaceReferences();
    symbolTable.fillPropertyScopes();
    symbolTable.fillThisReferences(this, externsRoot, jsRoot);
    symbolTable.fillPropertySymbols(this, externsRoot, jsRoot);
    symbolTable.fillJSDocInfo(this, externsRoot, jsRoot);
    symbolTable.fillSymbolVisibility(this, externsRoot, jsRoot);

    return symbolTable;
  }

  @Override
  public TypedScope getTopScope() {
    return getPassConfig().getTopScope();
  }

  @Override
  public ReverseAbstractInterpreter getReverseAbstractInterpreter() {
    if (abstractInterpreter == null) {
      ChainableReverseAbstractInterpreter interpreter =
          new SemanticReverseAbstractInterpreter(getTypeRegistry());
      if (options.closurePass) {
        interpreter = new ClosureReverseAbstractInterpreter(getTypeRegistry())
            .append(interpreter).getFirst();
      }
      abstractInterpreter = interpreter;
    }
    return abstractInterpreter;
  }

  @Override
  // Only used by passes in the old type checker.
  TypeValidator getTypeValidator() {
    if (typeValidator == null) {
      typeValidator = new TypeValidator(this);
    }
    return typeValidator;
  }

  @Override
  Iterable<TypeMismatch> getTypeMismatches() {
    return getTypeValidator().getMismatches();
  }

  @Override
  Iterable<TypeMismatch> getImplicitInterfaceUses() {
    return getTypeValidator().getImplicitStructuralInterfaceUses();
  }

  @Override
  GlobalTypeInfo getSymbolTable() {
    if (this.symbolTable == null) {
      this.symbolTable = new GlobalTypeInfo(this);
    }
    return this.symbolTable;
  }

  @Override
  void setSymbolTable(CompilerPass symbolTable) {
    Preconditions.checkArgument(
        symbolTable == null || symbolTable instanceof GlobalTypeInfo);
    this.symbolTable = (GlobalTypeInfo) symbolTable;
  }

  @Override
  SimpleDefinitionFinder getSimpleDefinitionFinder() {
    return this.defFinder;
  }

  @Override
  void setSimpleDefinitionFinder(SimpleDefinitionFinder defFinder) {
    this.defFinder = defFinder;
  }

  //------------------------------------------------------------------------
  // Parsing
  //------------------------------------------------------------------------

  /**
   * Parses the externs and main inputs.
   *
   * @return A synthetic root node whose two children are the externs root
   *     and the main root
   */
  Node parseInputs() {
    boolean devMode = options.devMode != DevMode.OFF;

    // If old roots exist (we are parsing a second time), detach each of the
    // individual file parse trees.
    externsRoot.detachChildren();
    jsRoot.detachChildren();

    if (options.tracer.isOn()) {
      tracker = new PerformanceTracker(jsRoot, options.tracer);
      addChangeHandler(tracker.getCodeChangeHandler());
    }

    Tracer tracer = newTracer(PARSING_PASS_NAME);
    beforePass(PARSING_PASS_NAME);

    try {
      // Parse externs sources.
      for (CompilerInput input : externs) {
        Node n = input.getAstRoot(this);
        if (hasErrors()) {
          return null;
        }
        externsRoot.addChildToBack(n);
      }

      if (options.lowerFromEs6()) {
        processEs6Modules();
      }

      // Modules inferred in ProcessCommonJS pass.
      if (options.transformAMDToCJSModules || options.processCommonJSModules) {
        processAMDAndCommonJSModules();
      }

      if (options.lowerFromEs6()
          || options.transformAMDToCJSModules
          || options.processCommonJSModules) {

        // Build a map of module identifiers for any input which provides no namespace.
        // These files could be imported modules which have no exports, but do have side effects.
        Map<String, CompilerInput> inputModuleIdentifiers = new HashMap<>();
        for (CompilerInput input : inputs) {
          if (input.getKnownProvides().isEmpty()) {
            ModuleIdentifier modInfo =
                ModuleIdentifier.forFile(input.getSourceFile().getOriginalPath());

            inputModuleIdentifiers.put(modInfo.getClosureNamespace(), input);
          }
        }

        // Find out if any input attempted to import a module that had no exports.
        // In this case we must force module rewriting to occur on the imported file
        Map<String, CompilerInput> inputsToRewrite = new HashMap<>();
        for (CompilerInput input : inputs) {
          for (String require : input.getKnownRequires()) {
            if (inputModuleIdentifiers.containsKey(require)
                && !inputsToRewrite.containsKey(require)) {
              inputsToRewrite.put(require, inputModuleIdentifiers.get(require));
            }
          }
        }

        if (!inputsToRewrite.isEmpty()) {
          processEs6Modules(new ArrayList<>(inputsToRewrite.values()), true);
        }
      }

      orderInputs();

      // If in IDE mode, we ignore the error and keep going.
      if (hasErrors()) {
        return null;
      }

      // Build the AST.
      for (CompilerInput input : inputs) {
        Node n = input.getAstRoot(this);
        if (n == null) {
          continue;
        }

        if (devMode) {
          runSanityCheck();
          if (hasErrors()) {
            return null;
          }
        }

        // TODO(johnlenz): we shouldn't need to check both isExternExportsEnabled and
        // externExportsPath.
        if (options.sourceMapOutputPath != null ||
            options.nameReferenceReportPath != null ||
            options.isExternExportsEnabled() ||
            options.externExportsPath != null ||
            !options.replaceStringsFunctionDescriptions.isEmpty()) {

          // Annotate the nodes in the tree with information from the
          // input file. This information is used to construct the SourceMap.
          SourceInformationAnnotator sia =
              new SourceInformationAnnotator(
                  input.getName(), options.devMode != DevMode.OFF);
          NodeTraversal.traverseEs6(this, n, sia);
        }

        jsRoot.addChildToBack(n);
      }

      if (hasErrors()) {
        return null;
      }
      return externAndJsRoot;
    } finally {
      afterPass(PARSING_PASS_NAME);
      stopTracer(tracer, PARSING_PASS_NAME);
    }
  }

  void orderInputsWithLargeStack() {
    runInCompilerThread(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        Tracer tracer = newTracer("orderInputsWithLargeStack");
        try {
          orderInputs();
        } finally {
          stopTracer(tracer, "orderInputsWithLargeStack");
        }
        return null;
      }
    });
  }

  void orderInputs() {
    hoistExterns();

    // Check if the sources need to be re-ordered.
    boolean staleInputs = false;
    if (options.dependencyOptions.needsManagement()) {
      for (CompilerInput input : inputs) {
        // Forward-declare all the provided types, so that they
        // are not flagged even if they are dropped from the process.
        for (String provide : input.getProvides()) {
          getTypeRegistry().forwardDeclareType(provide);
        }
      }

      try {
        inputs =
            (moduleGraph == null ? new JSModuleGraph(modules) : moduleGraph)
            .manageDependencies(options.dependencyOptions, inputs);
        staleInputs = true;
      } catch (MissingProvideException e) {
        report(JSError.make(
            MISSING_ENTRY_ERROR, e.getMessage()));
      } catch (JSModuleGraph.MissingModuleException e) {
        report(JSError.make(
            MISSING_MODULE_ERROR, e.getMessage()));
      }
    }

    hoistNoCompileFiles();

    if (staleInputs) {
      repartitionInputs();
    }
  }

  /**
   * Hoists inputs with the @externs annotation into the externs list.
   */
  void hoistExterns() {
    boolean staleInputs = false;
    for (CompilerInput input : inputs) {
      if (options.dependencyOptions.needsManagement()) {
        // If we're doing scanning dependency info anyway, use that
        // information to skip sources that obviously aren't externs.
        if (!input.getProvides().isEmpty() || !input.getRequires().isEmpty()) {
          continue;
        }
      }

      Node n = input.getAstRoot(this);

      // Inputs can have a null AST on a parse error.
      if (n == null) {
        continue;
      }

      JSDocInfo info = n.getJSDocInfo();
      if (info != null && info.isExterns()) {
        // If the input file is explicitly marked as an externs file, then
        // assume the programmer made a mistake and throw it into
        // the externs pile anyways.
        externsRoot.addChildToBack(n);
        input.setIsExtern(true);

        input.getModule().remove(input);

        externs.add(input);
        staleInputs = true;
      }
    }

    if (staleInputs) {
      repartitionInputs();
    }
  }

  /**
   * Hoists inputs with the @nocompile annotation out of the inputs.
   */
  void hoistNoCompileFiles() {
    boolean staleInputs = false;
    for (CompilerInput input : inputs) {
      Node n = input.getAstRoot(this);

      // Inputs can have a null AST on a parse error.
      if (n == null) {
        continue;
      }

      JSDocInfo info = n.getJSDocInfo();
      if (info != null && info.isNoCompile()) {
        input.getModule().remove(input);
        staleInputs = true;
      }
    }

    if (staleInputs) {
      repartitionInputs();
    }
  }

  private void repartitionInputs() {
    fillEmptyModules(modules);
    rebuildInputsFromModules();
  }

  void processEs6Modules() {
    processEs6Modules(inputs, false);
  }

  void processEs6Modules(List<CompilerInput> inputsToProcess, boolean forceRewrite) {
    ES6ModuleLoader loader = new ES6ModuleLoader(options.moduleRoots, inputs);
    for (CompilerInput input : inputsToProcess) {
      input.setCompiler(this);
      Node root = input.getAstRoot(this);
      if (root == null) {
        continue;
      }
      new ProcessEs6Modules(this, loader, true).processFile(root, forceRewrite);
    }
  }

  /**
   * Transforms AMD and CJS modules to something closure compiler can
   * process and creates JSModules and the corresponding dependency tree
   * on the way.
   */
  void processAMDAndCommonJSModules() {
    ES6ModuleLoader loader = new ES6ModuleLoader(options.moduleRoots, inputs);
    for (CompilerInput input : inputs) {
      input.setCompiler(this);
      Node root = input.getAstRoot(this);
      if (root == null) {
        continue;
      }
      if (options.transformAMDToCJSModules) {
        new TransformAMDToCJSModule(this).process(null, root);
      }
      if (options.processCommonJSModules) {
        ProcessCommonJSModules cjs = new ProcessCommonJSModules(this, loader, true);
        cjs.process(null, root);
      }
    }
  }

  public Node parse(SourceFile file) {
    initCompilerOptionsIfTesting();
    addToDebugLog("Parsing: " + file.getName());
    return new JsAst(file).getAstRoot(this);
  }

  private int syntheticCodeId = 0;

  @Override
  Node parseSyntheticCode(String js) {
    CompilerInput input = new CompilerInput(
        SourceFile.fromCode(" [synthetic:" + (++syntheticCodeId) + "] ", js));
    putCompilerInput(input.getInputId(), input);
    return input.getAstRoot(this);
  }

  /**
   * Allow subclasses to override the default CompileOptions object.
   */
  protected CompilerOptions newCompilerOptions() {
    return new CompilerOptions();
  }

  void initCompilerOptionsIfTesting() {
    if (options == null) {
      // initialization for tests that don't initialize the compiler
      // by the normal mechanisms.
      initOptions(newCompilerOptions());
    }
  }

  @Override
  Node parseSyntheticCode(String fileName, String js) {
    initCompilerOptionsIfTesting();
    return parse(SourceFile.fromCode(fileName, js));
  }

  @Override
  Node parseTestCode(String js) {
    initCompilerOptionsIfTesting();
    CompilerInput input = new CompilerInput(
        SourceFile.fromCode("[testcode]", js));
    if (inputsById == null) {
      inputsById = new HashMap<>();
    }
    putCompilerInput(input.getInputId(), input);
    return input.getAstRoot(this);
  }

  @Override
  ErrorReporter getDefaultErrorReporter() {
    return defaultErrorReporter;
  }

  //------------------------------------------------------------------------
  // Convert back to source code
  //------------------------------------------------------------------------

  /**
   * Converts the main parse tree back to JS code.
   */
  @Override
  public String toSource() {
    return runInCompilerThread(new Callable<String>() {
      @Override
      public String call() throws Exception {
        Tracer tracer = newTracer("toSource");
        try {
          CodeBuilder cb = new CodeBuilder();
          if (jsRoot != null) {
            int i = 0;
            for (Node scriptNode = jsRoot.getFirstChild();
                 scriptNode != null;
                 scriptNode = scriptNode.getNext()) {
              toSource(cb, i++, scriptNode);
            }
          }
          return cb.toString();
        } finally {
          stopTracer(tracer, "toSource");
        }
      }
    });
  }

  /**
   * Converts the parse tree for each input back to JS code.
   */
  public String[] toSourceArray() {
    return runInCompilerThread(new Callable<String[]>() {
      @Override
      public String[] call() throws Exception {
        Tracer tracer = newTracer("toSourceArray");
        try {
          int numInputs = inputs.size();
          String[] sources = new String[numInputs];
          CodeBuilder cb = new CodeBuilder();
          for (int i = 0; i < numInputs; i++) {
            Node scriptNode = inputs.get(i).getAstRoot(Compiler.this);
            cb.reset();
            toSource(cb, i, scriptNode);
            sources[i] = cb.toString();
          }
          return sources;
        } finally {
          stopTracer(tracer, "toSourceArray");
        }
      }
    });
  }

  /**
   * Converts the parse tree for a module back to JS code.
   */
  public String toSource(final JSModule module) {
    return runInCompilerThread(new Callable<String>() {
      @Override
      public String call() throws Exception {
        List<CompilerInput> inputs = module.getInputs();
        int numInputs = inputs.size();
        if (numInputs == 0) {
          return "";
        }
        CodeBuilder cb = new CodeBuilder();
        for (int i = 0; i < numInputs; i++) {
          Node scriptNode = inputs.get(i).getAstRoot(Compiler.this);
          if (scriptNode == null) {
            throw new IllegalArgumentException(
                "Bad module: " + module.getName());
          }
          toSource(cb, i, scriptNode);
        }
        return cb.toString();
      }
    });
  }


  /**
   * Converts the parse tree for each input in a module back to JS code.
   */
  public String[] toSourceArray(final JSModule module) {
    return runInCompilerThread(new Callable<String[]>() {
      @Override
      public String[] call() throws Exception {
        List<CompilerInput> inputs = module.getInputs();
        int numInputs = inputs.size();
        if (numInputs == 0) {
          return new String[0];
        }

        String[] sources = new String[numInputs];
        CodeBuilder cb = new CodeBuilder();
        for (int i = 0; i < numInputs; i++) {
          Node scriptNode = inputs.get(i).getAstRoot(Compiler.this);
          if (scriptNode == null) {
            throw new IllegalArgumentException(
                "Bad module input: " + inputs.get(i).getName());
          }

          cb.reset();
          toSource(cb, i, scriptNode);
          sources[i] = cb.toString();
        }
        return sources;
      }
    });
  }

  /**
   * Writes out JS code from a root node. If printing input delimiters, this
   * method will attach a comment to the start of the text indicating which
   * input the output derived from. If there were any preserve annotations
   * within the root's source, they will also be printed in a block comment
   * at the beginning of the output.
   */
  public void toSource(final CodeBuilder cb,
                       final int inputSeqNum,
                       final Node root) {
    runInCompilerThread(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        if (options.printInputDelimiter) {
          if ((cb.getLength() > 0) && !cb.endsWith("\n")) {
            cb.append("\n");  // Make sure that the label starts on a new line
          }
          Preconditions.checkState(root.isScript());

          String delimiter = options.inputDelimiter;

          String inputName = root.getInputId().getIdName();
          String sourceName = root.getSourceFileName();
          Preconditions.checkState(sourceName != null);
          Preconditions.checkState(!sourceName.isEmpty());

          delimiter = delimiter
              .replaceAll("%name%", Matcher.quoteReplacement(inputName))
              .replaceAll("%num%", String.valueOf(inputSeqNum));

          cb.append(delimiter)
            .append("\n");
        }
        if (root.getJSDocInfo() != null) {
          String license = root.getJSDocInfo().getLicense();
          if (license != null && cb.addLicense(license)) {
            cb.append("/*\n")
              .append(license)
              .append("*/\n");
          }
        }

        // If there is a valid source map, then indicate to it that the current
        // root node's mappings are offset by the given string builder buffer.
        if (options.sourceMapOutputPath != null) {
          sourceMap.setStartingPosition(
              cb.getLineIndex(), cb.getColumnIndex());
        }

        // if LanguageMode is strict, only print 'use strict'
        // for the first input file
        String code = toSource(root, sourceMap, inputSeqNum == 0);
        if (!code.isEmpty()) {
          cb.append(code);

          // In order to avoid parse ambiguity when files are concatenated
          // together, all files should end in a semi-colon. Do a quick
          // heuristic check if there's an obvious semi-colon already there.
          int length = code.length();
          char lastChar = code.charAt(length - 1);
          char secondLastChar = length >= 2 ?
              code.charAt(length - 2) : '\0';
          boolean hasSemiColon = lastChar == ';' ||
              (lastChar == '\n' && secondLastChar == ';');
          if (!hasSemiColon) {
            cb.append(";");
          }
        }
        return null;
      }
    });
  }

  /**
   * Generates JavaScript source code for an AST, doesn't generate source
   * map info.
   */
  @Override
  public String toSource(Node n) {
    initCompilerOptionsIfTesting();
    return toSource(n, null, true);
  }

  /**
   * Generates JavaScript source code for an AST.
   */
  private String toSource(Node n, SourceMap sourceMap, boolean firstOutput) {
    CodePrinter.Builder builder = new CodePrinter.Builder(n);
    builder.setTypeRegistry(this.typeRegistry);
    builder.setCompilerOptions(options);
    builder.setSourceMap(sourceMap);
    builder.setTagAsStrict(firstOutput && options.getLanguageOut().isStrict());
    return builder.build();
  }

  /**
   * Stores a buffer of text to which more can be appended.  This is just like a
   * StringBuilder except that we also track the number of lines.
   */
  public static class CodeBuilder {
    private final StringBuilder sb = new StringBuilder();
    private int lineCount = 0;
    private int colCount = 0;
    private final Set<String> uniqueLicenses = new HashSet<>();

    /** Removes all text, but leaves the line count unchanged. */
    void reset() {
      sb.setLength(0);
    }

    /** Appends the given string to the text buffer. */
    CodeBuilder append(String str) {
      sb.append(str);

      // Adjust the line and column information for the new text.
      int index = -1;
      int lastIndex = index;
      while ((index = str.indexOf('\n', index + 1)) >= 0) {
        ++lineCount;
        lastIndex = index;
      }

      if (lastIndex == -1) {
        // No new lines, append the new characters added.
        colCount += str.length();
      } else {
        colCount = str.length() - (lastIndex + 1);
      }

      return this;
    }

    /** Returns all text in the text buffer. */
    @Override
    public String toString() {
      return sb.toString();
    }

    /** Returns the length of the text buffer. */
    public int getLength() {
      return sb.length();
    }

    /** Returns the (zero-based) index of the last line in the text buffer. */
    int getLineIndex() {
      return lineCount;
    }

    /** Returns the (zero-based) index of the last column in the text buffer. */
    int getColumnIndex() {
      return colCount;
    }

    /** Determines whether the text ends with the given suffix. */
    boolean endsWith(String suffix) {
      return (sb.length() > suffix.length())
          && suffix.equals(sb.substring(sb.length() - suffix.length()));
    }

    /** Adds a license and returns whether it is unique (has yet to be encountered). */
    boolean addLicense(String license) {
      return uniqueLicenses.add(license);
    }
  }

  //------------------------------------------------------------------------
  // Optimizations
  //------------------------------------------------------------------------

  public void optimize() {
    List<PassFactory> optimizations = getPassConfig().getOptimizations();
    if (optimizations.isEmpty()) {
      return;
    }

    // Ideally, this pass should be the first pass run, however:
    // 1) VariableReferenceCheck reports unexpected warnings if Normalize
    // is done first.
    // 2) ReplaceMessages, stripCode, and potentially custom passes rely on
    // unmodified local names.
    normalize();

    // Create extern exports after the normalize because externExports depends on unique names.
    if (options.isExternExportsEnabled()
        || options.externExportsPath != null) {
      externExports();
    }

    phaseOptimizer = new PhaseOptimizer(this, tracker, null);
    if (options.devMode == DevMode.EVERY_PASS) {
      phaseOptimizer.setSanityCheck(sanityCheck);
    }
    if (options.getCheckDeterminism()) {
      phaseOptimizer.setPrintAstHashcodes(true);
    }
    phaseOptimizer.consume(optimizations);
    phaseOptimizer.process(externsRoot, jsRoot);
    phaseOptimizer = null;
  }

  @Override
  void setCssRenamingMap(CssRenamingMap map) {
    options.cssRenamingMap = map;
  }

  @Override
  CssRenamingMap getCssRenamingMap() {
    return options.cssRenamingMap;
  }

  /** Control Flow Analysis. */
  ControlFlowGraph<Node> computeCFG() {
    logger.fine("Computing Control Flow Graph");
    Tracer tracer = newTracer("computeCFG");
    ControlFlowAnalysis cfa = new ControlFlowAnalysis(this, true, false);
    process(cfa);
    stopTracer(tracer, "computeCFG");
    return cfa.getCfg();
  }

  public void normalize() {
    logger.fine("Normalizing");
    startPass("normalize");
    process(new Normalize(this, false));
    endPass();
  }

  @Override
  void prepareAst(Node root) {
    CompilerPass pass = new PrepareAst(this);
    pass.process(null, root);
  }

  void recordFunctionInformation() {
    logger.fine("Recording function information");
    startPass("recordFunctionInformation");
    RecordFunctionInformation recordFunctionInfoPass =
        new RecordFunctionInformation(
            this, getPassConfig().getIntermediateState().functionNames);
    process(recordFunctionInfoPass);
    functionInformationMap = recordFunctionInfoPass.getMap();
    endPass();
  }

  protected final RecentChange recentChange = new RecentChange();
  private final List<CodeChangeHandler> codeChangeHandlers = new ArrayList<>();

  /** Name of the synthetic input that holds synthesized externs. */
  static final String SYNTHETIC_EXTERNS = "{SyntheticVarsDeclar}";

  /**
   * Name of the synthetic input that holds synthesized externs which
   * must be at the end of the externs AST.
   */
  static final String SYNTHETIC_EXTERNS_AT_END = "{SyntheticVarsAtEnd}";

  private CompilerInput synthesizedExternsInput = null;
  private CompilerInput synthesizedExternsInputAtEnd = null;

  private ImmutableMap<String, Node> defaultDefineValues = ImmutableMap.of();

  @Override
  void addChangeHandler(CodeChangeHandler handler) {
    codeChangeHandlers.add(handler);
  }

  @Override
  void removeChangeHandler(CodeChangeHandler handler) {
    codeChangeHandlers.remove(handler);
  }

  @Override
  void setScope(Node n) {
    if (phaseOptimizer != null) {
      phaseOptimizer.setScope(n);
    }
  }

  @Override
  Node getJsRoot() {
    return jsRoot;
  }

  @Override
  boolean hasScopeChanged(Node n) {
    if (phaseOptimizer == null) {
      return true;
    }
    return phaseOptimizer.hasScopeChanged(n);
  }

  @Override
  void reportChangeToEnclosingScope(Node n) {
    if (phaseOptimizer != null) {
      phaseOptimizer.reportChangeToEnclosingScope(n);
      phaseOptimizer.startCrossScopeReporting();
      reportCodeChange();
      phaseOptimizer.endCrossScopeReporting();
    } else {
      reportCodeChange();
    }
  }

  /**
   * Some tests don't want to call the compiler "wholesale," they may not want
   * to call check and/or optimize. With this method, tests can execute custom
   * optimization loops.
   */
  @VisibleForTesting
  void setPhaseOptimizer(PhaseOptimizer po) {
    this.phaseOptimizer = po;
  }

  @Override
  public void reportCodeChange() {
    for (CodeChangeHandler handler : codeChangeHandlers) {
      handler.reportChange();
    }
  }

  @Override
  public CodingConvention getCodingConvention() {
    CodingConvention convention = options.getCodingConvention();
    convention = convention != null ? convention : defaultCodingConvention;
    return convention;
  }

  @Override
  public boolean isIdeMode() {
    return options.ideMode;
  }

  @Override
  Config getParserConfig(ConfigContext context) {
    if (parserConfig == null) {
      switch (options.getLanguageIn()) {
        case ECMASCRIPT3:
          parserConfig = createConfig(Config.LanguageMode.ECMASCRIPT3);
          externsParserConfig = createConfig(Config.LanguageMode.ECMASCRIPT5);
          break;
        case ECMASCRIPT5:
          parserConfig = createConfig(Config.LanguageMode.ECMASCRIPT5);
          externsParserConfig = parserConfig;
          break;
        case ECMASCRIPT5_STRICT:
          parserConfig = createConfig(Config.LanguageMode.ECMASCRIPT5_STRICT);
          externsParserConfig = parserConfig;
          break;
        case ECMASCRIPT6:
          parserConfig = createConfig(Config.LanguageMode.ECMASCRIPT6);
          externsParserConfig = parserConfig;
          break;
        case ECMASCRIPT6_STRICT:
          parserConfig = createConfig(Config.LanguageMode.ECMASCRIPT6_STRICT);
          externsParserConfig = parserConfig;
          break;
        case ECMASCRIPT6_TYPED:
          parserConfig = createConfig(Config.LanguageMode.ECMASCRIPT6_TYPED);
          externsParserConfig = parserConfig;
          break;
        default:
          throw new IllegalStateException("unexpected language mode: "
              + options.getLanguageIn());
      }
    }
    switch (context) {
      case EXTERNS:
        return externsParserConfig;
      default:
        return parserConfig;
    }
  }

  protected Config createConfig(Config.LanguageMode mode) {
    return ParserRunner.createConfig(
        isIdeMode(),
        options.isParseJsDocDocumentation(),
        options.isPreserveJsDocWhitespace(),
        mode,
        options.extraAnnotationNames);
  }

  //------------------------------------------------------------------------
  // Error reporting
  //------------------------------------------------------------------------

  /**
   * The warning classes that are available from the command-line, and
   * are suppressible by the {@code @suppress} annotation.
   */
  protected DiagnosticGroups getDiagnosticGroups() {
    return new DiagnosticGroups();
  }

  @Override
  public void report(JSError error) {
    CheckLevel level = error.getDefaultLevel();
    if (warningsGuard != null) {
      CheckLevel newLevel = warningsGuard.level(error);
      if (newLevel != null) {
        level = newLevel;
      }
    }

    if (level.isOn()) {
      initCompilerOptionsIfTesting();
      if (getOptions().errorHandler != null) {
        getOptions().errorHandler.report(level, error);
      }
      errorManager.report(level, error);
    }
  }

  @Override
  public CheckLevel getErrorLevel(JSError error) {
    Preconditions.checkNotNull(options);
    return warningsGuard.level(error);
  }

  /**
   * Report an internal error.
   */
  @Override
  void throwInternalError(String message, Exception cause) {
    String finalMessage =
      "INTERNAL COMPILER ERROR.\n" +
      "Please report this problem.\n\n" + message;

    RuntimeException e = new RuntimeException(finalMessage, cause);
    if (cause != null) {
      e.setStackTrace(cause.getStackTrace());
    }
    throw e;
  }


  /**
   * Gets the number of errors.
   */
  public int getErrorCount() {
    return errorManager.getErrorCount();
  }

  /**
   * Gets the number of warnings.
   */
  public int getWarningCount() {
    return errorManager.getWarningCount();
  }

  @Override
  boolean hasHaltingErrors() {
    return !isIdeMode() && getErrorCount() > 0;
  }

  /**
   * Consults the {@link ErrorManager} to see if we've encountered errors
   * that should halt compilation. <p>
   *
   * If {@link CompilerOptions#ideMode} is {@code true}, this function
   * always returns {@code false} without consulting the error manager. The
   * error manager will continue to be told about new errors and warnings, but
   * the compiler will complete compilation of all inputs.<p>
   */
  public boolean hasErrors() {
    return hasHaltingErrors();
  }

  /** Called from the compiler passes, adds debug info */
  @Override
  void addToDebugLog(String str) {
    if (options.useDebugLog) {
      debugLog.append(str);
      debugLog.append('\n');
      logger.fine(str);
    }
  }

  @Override
  SourceFile getSourceFileByName(String sourceName) {
    // Here we assume that the source name is the input name, this
    // is try of JavaScript parsed from source.
    if (sourceName != null) {
      CompilerInput input = inputsById.get(new InputId(sourceName));
      if (input != null) {
        return input.getSourceFile();
      }
      // Alternatively, the sourceName might have been reverse-mapped by
      // an input source-map, so let's look in our sourcemap original sources.
      return sourceMapOriginalSources.get(sourceName);
    }

    return null;
  }

  @Override
  public OriginalMapping getSourceMapping(String sourceName, int lineNumber,
      int columnNumber) {
    SourceMapInput sourceMap = options.inputSourceMaps.get(sourceName);
    if (sourceMap == null) {
      return null;
    }

    // JSCompiler uses 1-indexing for lineNumber and 0-indexing for
    // columnNumber.
    // SourceMap uses 1-indexing for both.
    OriginalMapping result = sourceMap.getSourceMap()
        .getMappingForLine(lineNumber, columnNumber + 1);
    if (result == null) {
      return null;
    }

    // The sourcemap will return a path relative to the sourcemap's file.
    // Translate it to one relative to our base directory.
    String path =
        getRelativeTo(result.getOriginalFile(), sourceMap.getOriginalPath());
    sourceMapOriginalSources.putIfAbsent(
        path, originalSourcesLoader.apply(path));
    return result.toBuilder()
        .setOriginalFile(path)
        .setColumnPosition(result.getColumnPosition() - 1)
        .build();
  }

  @Override
  public String getSourceLine(String sourceName, int lineNumber) {
    if (lineNumber < 1) {
      return null;
    }
    SourceFile input = getSourceFileByName(sourceName);
    if (input != null) {
      return input.getLine(lineNumber);
    }
    return null;
  }

  @Override
  public Region getSourceRegion(String sourceName, int lineNumber) {
    if (lineNumber < 1) {
      return null;
    }
    SourceFile input = getSourceFileByName(sourceName);
    if (input != null) {
      return input.getRegion(lineNumber);
    }
    return null;
  }

  //------------------------------------------------------------------------
  // Package-private helpers
  //------------------------------------------------------------------------

  @Override
  Node getNodeForCodeInsertion(JSModule module) {
    if (module == null) {
      if (inputs.isEmpty()) {
        throw new IllegalStateException("No inputs");
      }

      return inputs.get(0).getAstRoot(this);
    }

    List<CompilerInput> moduleInputs = module.getInputs();
    if (!moduleInputs.isEmpty()) {
      return moduleInputs.get(0).getAstRoot(this);
    }
    throw new IllegalStateException("Root module has no inputs");
  }

  public SourceMap getSourceMap() {
    return sourceMap;
  }

  VariableMap getVariableMap() {
    return getPassConfig().getIntermediateState().variableMap;
  }

  VariableMap getPropertyMap() {
    return getPassConfig().getIntermediateState().propertyMap;
  }

  @Override
  CompilerOptions getOptions() {
    return options;
  }

  FunctionInformationMap getFunctionalInformationMap() {
    return functionInformationMap;
  }

  /**
   * Sets the logging level for the com.google.javascript.jscomp package.
   */
  public static void setLoggingLevel(Level level) {
    logger.setLevel(level);
  }

  /** Gets the DOT graph of the AST generated at the end of compilation. */
  public String getAstDotGraph() throws IOException {
    if (jsRoot != null) {
      ControlFlowAnalysis cfa = new ControlFlowAnalysis(this, true, false);
      cfa.process(null, jsRoot);
      return DotFormatter.toDot(jsRoot, cfa.getCfg());
    } else {
      return "";
    }
  }

  @Override
  public ErrorManager getErrorManager() {
    if (options == null) {
      initOptions(newCompilerOptions());
    }
    return errorManager;
  }

  @Override
  List<CompilerInput> getInputsInOrder() {
    return Collections.unmodifiableList(inputs);
  }

  /**
   * Returns an unmodifiable view of the compiler inputs indexed by id.
   */
  public Map<InputId, CompilerInput> getInputsById() {
    return Collections.unmodifiableMap(inputsById);
  }

  /**
   * Gets the externs in the order in which they are being processed.
   */
  List<CompilerInput> getExternsInOrder() {
    return Collections.unmodifiableList(externs);
  }

  @VisibleForTesting
  List<CompilerInput> getInputsForTesting() {
    return inputs;
  }

  @VisibleForTesting
  List<CompilerInput> getExternsForTesting() {
    return externs;
  }

  @Override
  boolean hasRegExpGlobalReferences() {
    return hasRegExpGlobalReferences;
  }

  @Override
  void setHasRegExpGlobalReferences(boolean references) {
    hasRegExpGlobalReferences = references;
  }

  @Override
  void updateGlobalVarReferences(Map<Var, ReferenceCollection> refMapPatch,
      Node collectionRoot) {
    Preconditions.checkState(collectionRoot.isScript()
        || collectionRoot.isBlock());
    if (globalRefMap == null) {
      globalRefMap = new GlobalVarReferenceMap(getInputsInOrder(),
          getExternsInOrder());
    }
    globalRefMap.updateGlobalVarReferences(refMapPatch, collectionRoot);
  }

  @Override
  GlobalVarReferenceMap getGlobalVarReferences() {
    return globalRefMap;
  }

  @Override
  CompilerInput getSynthesizedExternsInput() {
    if (synthesizedExternsInput == null) {
      synthesizedExternsInput = newExternInput(SYNTHETIC_EXTERNS, SyntheticExternsPosition.START);
    }
    return synthesizedExternsInput;
  }

  @Override
  CompilerInput getSynthesizedExternsInputAtEnd() {
    if (synthesizedExternsInputAtEnd == null) {
      synthesizedExternsInputAtEnd = newExternInput(
          SYNTHETIC_EXTERNS_AT_END, SyntheticExternsPosition.END);
    }
    return synthesizedExternsInputAtEnd;
  }

  @Override
  public double getProgress() {
    return progress;
  }

  @Override
  String getLastPassName() {
    return lastPassName;
  }

  @Override
  void setProgress(double newProgress, String passName) {
    this.lastPassName = passName;
    if (newProgress > 1.0) {
      progress = 1.0;
    } else {
      progress = newProgress;
    }
  }

  @Override
  void setExternProperties(Set<String> externProperties) {
    this.externProperties = externProperties;
  }

  @Override
  Set<String> getExternProperties() {
    return externProperties;
  }

  /**
   * Replaces one file in a hot-swap mode. The given JsAst should be made
   * from a new version of a file that already was present in the last compile
   * call. If the file is new, this will silently ignored.
   *
   * @param ast the ast of the file that is being replaced
   */
  public void replaceScript(JsAst ast) {
    CompilerInput input = this.getInput(ast.getInputId());
    if (!replaceIncrementalSourceAst(ast)) {
      return;
    }
    Node originalRoot = input.getAstRoot(this);

    processNewScript(ast, originalRoot);
  }

  /**
   * Adds a new Script AST to the compile state. If a script for the same file
   * already exists the script will not be added, instead a call to
   * #replaceScript should be used.
   *
   * @param ast the ast of the new file
   */
  public void addNewScript(JsAst ast) {
    if (!addNewSourceAst(ast)) {
      return;
    }
    Node emptyScript = new Node(Token.SCRIPT);
    InputId inputId = ast.getInputId();
    emptyScript.setInputId(inputId);
    emptyScript.setStaticSourceFile(
        SourceFile.fromCode(inputId.getIdName(), ""));

    processNewScript(ast, emptyScript);
  }

  private void processNewScript(JsAst ast, Node originalRoot) {
    Node js = ast.getAstRoot(this);
    Preconditions.checkNotNull(js);

    runHotSwap(originalRoot, js, this.getCleanupPassConfig());
    // NOTE: If hot swap passes that use GlobalNamespace are added, we will need
    // to revisit this approach to clearing GlobalNamespaces
    runHotSwapPass(null, null, ensureDefaultPassConfig().garbageCollectChecks);

    this.getTypeRegistry().clearNamedTypes();
    this.removeSyntheticVarsInput();

    runHotSwap(originalRoot, js, this.ensureDefaultPassConfig());
  }

  /**
   * Execute the passes from a PassConfig instance over a single replaced file.
   */
  private void runHotSwap(
      Node originalRoot, Node js, PassConfig passConfig) {
    for (PassFactory passFactory : passConfig.getChecks()) {
      runHotSwapPass(originalRoot, js, passFactory);
    }
  }

  private void runHotSwapPass(
      Node originalRoot, Node js, PassFactory passFactory) {
    HotSwapCompilerPass pass = passFactory.getHotSwapPass(this);
    if (pass != null) {
      logger.info("Performing HotSwap for pass " + passFactory.getName());
      pass.hotSwapScript(js, originalRoot);
    }
  }

  private PassConfig getCleanupPassConfig() {
    return new CleanupPasses(getOptions());
  }

  private void removeSyntheticVarsInput() {
    String sourceName = Compiler.SYNTHETIC_EXTERNS;
    removeExternInput(new InputId(sourceName));
  }

  @Override
  Node ensureLibraryInjected(String resourceName, boolean force) {
    boolean doNotInject =
        !force && (options.skipNonTranspilationPasses || options.preventLibraryInjection);
    if (injectedLibraries.containsKey(resourceName) || doNotInject) {
      return lastInjectedLibrary;
    }

    // Load/parse the code.
    String originalCode = ResourceLoader.loadTextResource(
        Compiler.class, "js/" + resourceName + ".js");
    Node ast = parseSyntheticCode(originalCode);

    // Look for string literals of the form 'require foo bar' or 'externs baz' or 'normalize'.
    // As we process each one, remove it from its parent.
    for (Node node = ast.getFirstChild();
         node != null && node.isExprResult() && node.getFirstChild().isString();
         node = ast.getFirstChild()) {
      String directive = node.getFirstChild().getString();
      List<String> words = Splitter.on(' ').splitToList(directive);
      switch (words.get(0)) {
        case "use":
          // 'use strict' is ignored (and deleted).
          break;
        case "require":
          // 'require lib1 lib2'; pulls in the named libraries before this one.
          for (String dependency : words.subList(1, words.size())) {
            ensureLibraryInjected(dependency, force);
          }
          break;
        case "declare":
          // 'declare name1 name2'; adds the names to the externs (with no type information).
          // Note that we could simply add the entire externs library, but that leads to
          // potentially-surprising behavior when the externs that are present depend on
          // whether or not a polyfill is used.
          for (String extern : words.subList(1, words.size())) {
            getSynthesizedExternsInputAtEnd()
                .getAstRoot(this)
                .addChildToBack(IR.var(IR.name(extern)));
          }
          break;
        default:
          throw new RuntimeException("Bad directive: " + directive);
      }
      ast.removeChild(node);
    }

    // If we've already started optimizations, then we need to normalize this.
    if (getLifeCycleStage().isNormalized()) {
      Normalize.normalizeSyntheticCode(this, ast, "jscomp_" + resourceName + "_");
    }

    // Insert the code immediately after the last-inserted runtime library.
    Node firstChild = ast.removeChildren();
    Node lastChild = firstChild.getLastSibling();
    Node parent = getNodeForCodeInsertion(null);
    if (lastInjectedLibrary == null) {
      parent.addChildrenToFront(firstChild);
    } else {
      parent.addChildrenAfter(firstChild, lastInjectedLibrary);
    }
    lastInjectedLibrary = lastChild;
    injectedLibraries.put(resourceName, lastChild);

    reportCodeChange();
    return lastChild;
  }

  /** Returns the compiler version baked into the jar. */
  @GwtIncompatible("java.util.ResourceBundle")
  public static String getReleaseVersion() {
    ResourceBundle config = ResourceBundle.getBundle(CONFIG_RESOURCE);
    return config.getString("compiler.version");
  }

  /** Returns the compiler date baked into the jar. */
  @GwtIncompatible("java.util.ResourceBundle")
  public static String getReleaseDate() {
    ResourceBundle config = ResourceBundle.getBundle(CONFIG_RESOURCE);
    return config.getString("compiler.date");
  }

  @Override
  void addComments(String filename, List<Comment> comments) {
    if (!isIdeMode()) {
      throw new UnsupportedOperationException(
          "addComments may only be called in IDE mode.");
    }
    commentsPerFile.put(filename, comments);
  }

  @Override
  public List<Comment> getComments(String filename) {
    if (!isIdeMode()) {
      throw new UnsupportedOperationException(
          "getComments may only be called in IDE mode.");
    }
    return commentsPerFile.get(filename);
  }

  @Override
  void setDefaultDefineValues(ImmutableMap<String, Node> values) {
    this.defaultDefineValues = values;
  }

  @Override
  ImmutableMap<String, Node> getDefaultDefineValues() {
    return this.defaultDefineValues;
  }
}
