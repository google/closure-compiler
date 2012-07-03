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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.CharStreams;
import com.google.javascript.jscomp.CompilerOptions.DevMode;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.ReferenceCollectingCallback.ReferenceCollection;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.jscomp.deps.SortedDependencies.CircularDependencyException;
import com.google.javascript.jscomp.deps.SortedDependencies.MissingProvideException;
import com.google.javascript.jscomp.parsing.Config;
import com.google.javascript.jscomp.parsing.ParserRunner;
import com.google.javascript.jscomp.type.ChainableReverseAbstractInterpreter;
import com.google.javascript.jscomp.type.ClosureReverseAbstractInterpreter;
import com.google.javascript.jscomp.type.ReverseAbstractInterpreter;
import com.google.javascript.jscomp.type.SemanticReverseAbstractInterpreter;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.head.ErrorReporter;
import com.google.javascript.rhino.jstype.JSTypeRegistry;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.Callable;
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
  static final String SINGLETON_MODULE_NAME = "[singleton]";

  static final DiagnosticType MODULE_DEPENDENCY_ERROR =
      DiagnosticType.error("JSC_MODULE_DEPENDENCY_ERROR",
          "Bad dependency: {0} -> {1}. "
              + "Modules must be listed in dependency order.");

  static final DiagnosticType MISSING_ENTRY_ERROR = DiagnosticType.error(
      "JSC_MISSING_ENTRY_ERROR",
      "required entry point \"{0}\" never provided");

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
  private final Map<String, Node> injectedLibraries = Maps.newLinkedHashMap();

  // Parse tree root nodes
  Node externsRoot;
  Node jsRoot;
  Node externAndJsRoot;

  private Map<InputId, CompilerInput> inputsById;

  /** The source code map */
  private SourceMap sourceMap;

  /** The externs created from the exports.  */
  private String externExports = null;

  /**
   * Ids for function inlining so that each declared name remains
   * unique.
   */
  private int uniqueNameId = 0;

  /** Whether to use threads. */
  private boolean useThreads = true;

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

  private ReverseAbstractInterpreter abstractInterpreter;
  private TypeValidator typeValidator;

  public PerformanceTracker tracker;

  // The oldErrorReporter exists so we can get errors from the JSTypeRegistry.
  private final com.google.javascript.rhino.ErrorReporter oldErrorReporter =
      RhinoErrorReporter.forOldRhino(this);

  // This error reporter gets the messages from the current Rhino parser.
  private final ErrorReporter defaultErrorReporter =
      RhinoErrorReporter.forNewRhino(this);

  /** Error strings used for reporting JSErrors */
  public static final DiagnosticType OPTIMIZE_LOOP_ERROR = DiagnosticType.error(
      "JSC_OPTIMIZE_LOOP_ERROR",
      "Exceeded max number of optimization iterations: {0}");
  public static final DiagnosticType MOTION_ITERATIONS_ERROR =
      DiagnosticType.error("JSC_OPTIMIZE_LOOP_ERROR",
          "Exceeded max number of code motion iterations: {0}");

  private static final long COMPILER_STACK_SIZE = 1048576L;


  /**
   * Logger for the whole com.google.javascript.jscomp domain -
   * setting configuration for this logger affects all loggers
   *  in other classes within the compiler.
   */
  private static final Logger logger =
      Logger.getLogger("com.google.javascript.jscomp");

  private final PrintStream outStream;

  private GlobalVarReferenceMap globalRefMap = null;

  private volatile double progress = 0.0;

  /**
   * Creates a Compiler that reports errors and warnings to its logger.
   */
  public Compiler() {
    this((PrintStream) null);
  }

  /**
   * Creates n Compiler that reports errors and warnings to an output
   * stream.
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

  /**
   * Initialize the compiler options. Only necessary if you're not doing
   * a normal compile() job.
   */
  public void initOptions(CompilerOptions options) {
    this.options = options;
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

    if (options.checkGlobalThisLevel.isOn() &&
        !options.disables(DiagnosticGroups.GLOBAL_THIS)) {
      options.setWarningLevel(
          DiagnosticGroups.GLOBAL_THIS,
          options.checkGlobalThisLevel);
    }

    if (options.getLanguageIn() == LanguageMode.ECMASCRIPT5_STRICT) {
      options.setWarningLevel(
          DiagnosticGroups.ES5_STRICT,
          CheckLevel.ERROR);
    }

    // Initialize the warnings guard.
    List<WarningsGuard> guards = Lists.newArrayList();
    guards.add(
        new SuppressDocWarningsGuard(
            getDiagnosticGroups().getRegisteredGroups()));
    guards.add(options.getWarningsGuard());

    ComposeWarningsGuard composedGuards = new ComposeWarningsGuard(guards);

    // All passes must run the variable check. This synthesizes
    // variables later so that the compiler doesn't crash. It also
    // checks the externs file for validity. If you don't want to warn
    // about missing variable declarations, we shut that specific
    // error off.
    if (!options.checkSymbols &&
        !composedGuards.enables(DiagnosticGroups.CHECK_VARIABLES)) {
      composedGuards.addGuard(new DiagnosticGroupWarningsGuard(
          DiagnosticGroups.CHECK_VARIABLES, CheckLevel.OFF));
    }

    this.warningsGuard = composedGuards;
  }

  /**
   * Initializes the instance state needed for a compile job.
   */
  public void init(JSSourceFile[] externs, JSSourceFile[] inputs,
      CompilerOptions options) {
    init(Lists.<JSSourceFile>newArrayList(externs),
        Lists.<JSSourceFile>newArrayList(inputs), options);
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

    initModules(externs, Lists.newArrayList(module), options);
  }

  /**
   * Initializes the instance state needed for a compile job if the sources
   * are in modules.
   */
  public void init(JSSourceFile[] externs, JSModule[] modules,
      CompilerOptions options) {
    initModules(Lists.<SourceFile>newArrayList(externs),
         Lists.<JSModule>newArrayList(modules), options);
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
    List<CompilerInput> inputs = Lists.newArrayList();
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
          "Root module '{0}' must contain at least one source code input");

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
    return "[" + moduleName + "]";
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
   * after the {@link #init(JSSourceFile[], JSModule[], CompilerOptions)} call.
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
    List<CompilerInput> inputs = Lists.newArrayList();
    Map<String, JSModule> inputMap = Maps.newHashMap();
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
   * Creates a map to make looking up an input by name fast. Also checks for
   * duplicate inputs.
   */
  void initInputsByIdMap() {
    inputsById = new HashMap<InputId, CompilerInput>();
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

  public Result compile(
      SourceFile extern, SourceFile input, CompilerOptions options) {
     return compile(Lists.newArrayList(extern), Lists.newArrayList(input), options);
  }

  public Result compile(
      SourceFile extern, JSSourceFile[] input, CompilerOptions options) {
     return compile(Lists.newArrayList(extern), Lists.newArrayList(input), options);
  }

  public Result compile(
      JSSourceFile extern, JSModule[] modules, CompilerOptions options) {
     return compileModules(
         Lists.newArrayList(extern), Lists.newArrayList(modules), options);
  }

  /**
   * Compiles a list of inputs.
   */
  public Result compile(JSSourceFile[] externs,
                        JSSourceFile[] inputs,
                        CompilerOptions options) {
    return compile(Lists.<SourceFile>newArrayList(externs),
        Lists.<SourceFile>newArrayList(inputs),
        options);
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
  public Result compile(JSSourceFile[] externs,
                        JSModule[] modules,
                        CompilerOptions options) {
    return compileModules(Lists.<SourceFile>newArrayList(externs),
        Lists.<JSModule>newArrayList(modules),
        options);
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
    useThreads = false;
  }

  private <T> T runInCompilerThread(final Callable<T> callable) {
    return runCallable(callable, useThreads, options.tracer.isOn());
  }

  static <T> T runCallableWithLargeStack(final Callable<T> callable) {
    return runCallable(callable, true, false);
  }

  @SuppressWarnings("unchecked")
  static <T> T runCallable(
      final Callable<T> callable, boolean useLargeStackThread, boolean trace) {

    // Under JRE 1.6, the JS Compiler overflows the stack when running on some
    // large or complex JS code. Here we start a new thread with a larger
    // stack in order to let the compiler do its thing, without having to
    // increase the stack size for *every* thread (which is what -Xss does).
    // Might want to add thread pool support for clients that compile a lot.

    final boolean dumpTraceReport = trace;
    final Object[] result = new Object[1];
    final Throwable[] exception = new Throwable[1];
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        try {
          if (dumpTraceReport) {
            Tracer.initCurrentThreadTrace();
          }
          result[0] = callable.call();
        } catch (Throwable e) {
          exception[0] = e;
        } finally {
          if (dumpTraceReport) {
            Tracer.logAndClearCurrentThreadTrace();
          }
        }
      }
    };

    if (useLargeStackThread) {
      Thread th = new Thread(null, runnable, "jscompiler", COMPILER_STACK_SIZE);
      th.start();
      while (true) {
        try {
          th.join();
          break;
        } catch (InterruptedException ignore) {
          // ignore
        }
      }
    } else {
      runnable.run();
    }

    // Pass on any exception caught by the runnable object.
    if (exception[0] != null) {
      throw new RuntimeException(exception[0]);
    }

    return (T) result[0];
  }

  private void compileInternal() {
    setProgress(0.0);
    parse();
    // 15 percent of the work is assumed to be for parsing (based on some
    // minimal analysis on big JS projects, of course this depends on options)
    setProgress(0.15);
    if (hasErrors()) {
      return;
    }

    if (!precheck()) {
      return;
    }

    if (options.nameAnonymousFunctionsOnly) {
      // TODO(nicksantos): Move this into an instrument() phase maybe?
      check();
      return;
    }

    if (!options.skipAllPasses) {
      check();
      if (hasErrors()) {
        return;
      }

      if (options.isExternExportsEnabled()
          || options.externExportsPath != null) {
        externExports();
      }

      // IDE-mode is defined to stop here, before the heavy rewriting begins.
      if (!options.ideMode) {
        optimize();
      }
    }

    if (options.recordFunctionInformation) {
      recordFunctionInformation();
    }

    if (options.devMode == DevMode.START_AND_END) {
      runSanityCheck();
    }
    setProgress(1.0);
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
    // the client wanted since he or she probably meant to use their
    // own PassConfig object.
    Preconditions.checkNotNull(passes);

    if (this.passes != null) {
      throw new IllegalStateException("this.passes has already been assigned");
    }
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

  public void check() {
    runCustomPasses(CustomPassExecutionTime.BEFORE_CHECKS);

    // We are currently only interested in check-passes for progress reporting
    // as it is used for IDEs, that's why the maximum progress is set to 1.0.
    PhaseOptimizer phaseOptimizer = new PhaseOptimizer(this, tracker,
        new PhaseOptimizer.ProgressRange(getProgress(), 1.0));
    if (options.devMode == DevMode.EVERY_PASS) {
      phaseOptimizer.setSanityCheck(sanityCheck);
    }
    phaseOptimizer.consume(getPassConfig().getChecks());
    phaseOptimizer.process(externsRoot, jsRoot);
    if (hasErrors()) {
      return;
    }

    // TODO(nicksantos): clean this up. The flow here is too hard to follow.
    if (options.nameAnonymousFunctionsOnly) {
      return;
    }

    if (options.removeTryCatchFinally) {
      removeTryCatchFinally();
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
    protected CompilerPass createInternal(AbstractCompiler compiler) {
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
   * Removes try/catch/finally statements for easier debugging.
   */
  void removeTryCatchFinally() {
    logger.fine("Remove try/catch/finally");
    startPass("removeTryCatchFinally");
    RemoveTryCatch r = new RemoveTryCatch(this);
    process(r);
    endPass();
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
    String passToCheck = currentPassName;
    currentPassName = null;
    currentTracer = null;

    maybeSanityCheck();
  }

  /**
   * Returns a new tracer for the given pass name.
   */
  Tracer newTracer(String passName) {
    String comment = passName
        + (recentChange.hasCodeChanged() ? " on recently changed AST" : "");
    if (options.tracer.isOn()) {
      tracker.recordPassStart(passName);
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
   * Returns an array constructed from errors + temporary warnings.
   */
  public JSError[] getMessages() {
    return getErrors();
  }

  /**
   * Returns the array of errors (never null).
   */
  public JSError[] getErrors() {
    return errorManager.getErrors();
  }

  /**
   * Returns the array of warnings (never null).
   */
  public JSError[] getWarnings() {
    return errorManager.getWarnings();
  }

  @Override
  public Node getRoot() {
    return externAndJsRoot;
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

  @Override
  public CompilerInput newExternInput(String name) {
    SourceAst ast = new SyntheticAst(name);
    if (inputsById.containsKey(ast.getInputId())) {
      throw new IllegalArgumentException("Conflicting externs name: " + name);
    }
    CompilerInput input = new CompilerInput(ast, true);
    putCompilerInput(input.getInputId(), input);
    externsRoot.addChildToFront(ast.getAstRoot(this));
    externs.add(0, input);
    return input;
  }

  private CompilerInput putCompilerInput(InputId id, CompilerInput input) {
    input.setCompiler(this);
    return inputsById.put(id, input);
  }

  /** Add a source input dynamically. Intended for incremental compilation. */
  void addIncrementalSourceAst(JsAst ast) {
    InputId id = ast.getInputId();
    Preconditions.checkState(getInput(id) == null, "Duplicate input %s", id.getIdName());
    putCompilerInput(id, new CompilerInput(ast));
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
  public JSTypeRegistry getTypeRegistry() {
    if (typeRegistry == null) {
      typeRegistry = new JSTypeRegistry(oldErrorReporter, options.looseTypes);
    }
    return typeRegistry;
  }

  @Override
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

    return symbolTable;
  }

  @Override
  public Scope getTopScope() {
    return getPassConfig().getTopScope();
  }

  @Override
  public ReverseAbstractInterpreter getReverseAbstractInterpreter() {
    if (abstractInterpreter == null) {
      ChainableReverseAbstractInterpreter interpreter =
          new SemanticReverseAbstractInterpreter(
              getCodingConvention(), getTypeRegistry());
      if (options.closurePass) {
        interpreter = new ClosureReverseAbstractInterpreter(
            getCodingConvention(), getTypeRegistry())
            .append(interpreter).getFirst();
      }
      abstractInterpreter = interpreter;
    }
    return abstractInterpreter;
  }

  @Override
  TypeValidator getTypeValidator() {
    if (typeValidator == null) {
      typeValidator = new TypeValidator(this);
    }
    return typeValidator;
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
    if (externsRoot != null) {
      externsRoot.detachChildren();
    }
    if (jsRoot != null) {
      jsRoot.detachChildren();
    }

    // Parse main JS sources.
    jsRoot = IR.block();
    jsRoot.setIsSyntheticBlock(true);

    externsRoot = IR.block();
    externsRoot.setIsSyntheticBlock(true);

    externAndJsRoot = IR.block(externsRoot, jsRoot);
    externAndJsRoot.setIsSyntheticBlock(true);

    if (options.tracer.isOn()) {
      tracker = new PerformanceTracker(jsRoot, options.tracer);
      addChangeHandler(tracker.getCodeChangeHandler());
    }

    Tracer tracer = newTracer("parseInputs");

    try {
      // Parse externs sources.
      for (CompilerInput input : externs) {
        Node n = input.getAstRoot(this);
        if (hasErrors()) {
          return null;
        }
        externsRoot.addChildToBack(n);
      }

      // Modules inferred in ProcessCommonJS pass.
      if (options.transformAMDToCJSModules || options.processCommonJSModules) {
        processAMDAndCommonJSModules();
      }

      hoistExterns(externsRoot);

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
        } catch (CircularDependencyException e) {
          report(JSError.make(
              JSModule.CIRCULAR_DEPENDENCY_ERROR, e.getMessage()));

          // If in IDE mode, we ignore the error and keep going.
          if (hasErrors()) {
            return null;
          }
        } catch (MissingProvideException e) {
          report(JSError.make(
              MISSING_ENTRY_ERROR, e.getMessage()));

          // If in IDE mode, we ignore the error and keep going.
          if (hasErrors()) {
            return null;
          }
        }
      }

      hoistNoCompileFiles();

      if (staleInputs) {
        repartitionInputs();
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

        if (options.sourceMapOutputPath != null ||
            options.nameReferenceReportPath != null) {

          // Annotate the nodes in the tree with information from the
          // input file. This information is used to construct the SourceMap.
          SourceInformationAnnotator sia =
              new SourceInformationAnnotator(
                  input.getName(), options.devMode != DevMode.OFF);
          NodeTraversal.traverse(this, n, sia);
        }

        jsRoot.addChildToBack(n);
      }

      if (hasErrors()) {
        return null;
      }
      return externAndJsRoot;
    } finally {
      stopTracer(tracer, "parseInputs");
    }
  }

  /**
   * Hoists inputs with the @externs annotation into the externs list.
   */
  private void hoistExterns(Node externsRoot) {
    boolean staleInputs = false;
    for (CompilerInput input : inputs) {
      if (options.dependencyOptions.needsManagement() &&
          options.closurePass) {
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
   * Hoists inputs with the @nocompiler annotation out of the inputs.
   */
  private void hoistNoCompileFiles() {
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

  /**
   * Transforms AMD and CJS modules to something closure compiler can
   * process and creates JSModules and the corresponding dependency tree
   * on the way.
   */
  void processAMDAndCommonJSModules() {
    Map<String, JSModule> modulesByName = Maps.newLinkedHashMap();
    Map<CompilerInput, JSModule> modulesByInput = Maps.newLinkedHashMap();
    // TODO(nicksantos): Refactor module dependency resolution to work nicely
    // with multiple ways to express dependencies. Directly support JSModules
    // that are equivalent to a signal file and which express their deps
    // directly in the source.
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
        ProcessCommonJSModules cjs = new ProcessCommonJSModules(this,
            options.commonJSModulePathPrefix);
        cjs.process(null, root);
        JSModule m = cjs.getModule();
        if (m != null) {
          modulesByName.put(m.getName(), m);
          modulesByInput.put(input, m);
        }
      }
    }
    if (options.processCommonJSModules) {
      List<JSModule> modules = Lists.newArrayList(modulesByName.values());
      if (!modules.isEmpty()) {
        this.modules = modules;
        this.moduleGraph = new JSModuleGraph(this.modules);
      }
      for (JSModule module : modules) {
        for (CompilerInput input : module.getInputs()) {
          for (String require : input.getRequires()) {
            JSModule dependency = modulesByName.get(require);
            if (dependency == null) {
              report(JSError.make(MISSING_ENTRY_ERROR, require));
            } else {
              module.addDependency(dependency);
            }
          }
        }
      }
      try {
        modules = Lists.newArrayList();
        for (CompilerInput input : this.moduleGraph.manageDependencies(
            options.dependencyOptions, inputs)) {
          modules.add(modulesByInput.get(input));
        }
        this.modules = modules;
        this.moduleGraph = new JSModuleGraph(modules);
      } catch (Exception e) {
        Throwables.propagate(e);
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
      inputsById = Maps.newHashMap();
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
        if (root.getJSDocInfo() != null &&
            root.getJSDocInfo().getLicense() != null) {
          cb.append("/*\n")
            .append(root.getJSDocInfo().getLicense())
            .append("*/\n");
        }

        // If there is a valid source map, then indicate to it that the current
        // root node's mappings are offset by the given string builder buffer.
        if (options.sourceMapOutputPath != null) {
          sourceMap.setStartingPosition(
              cb.getLineIndex(), cb.getColumnIndex());
        }

        // if LanguageMode is ECMASCRIPT5_STRICT, only print 'use strict'
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
  String toSource(Node n) {
    initCompilerOptionsIfTesting();
    return toSource(n, null, true);
  }

  /**
   * Generates JavaScript source code for an AST.
   */
  private String toSource(Node n, SourceMap sourceMap, boolean firstOutput) {
    CodePrinter.Builder builder = new CodePrinter.Builder(n);
    builder.setPrettyPrint(options.prettyPrint);
    builder.setLineBreak(options.lineBreak);
    builder.setPreferLineBreakAtEndOfFile(options.preferLineBreakAtEndOfFile);
    builder.setSourceMap(sourceMap);
    builder.setSourceMapDetailLevel(options.sourceMapDetailLevel);
    builder.setTagAsStrict(firstOutput &&
        options.getLanguageOut() == LanguageMode.ECMASCRIPT5_STRICT);
    builder.setLineLengthThreshold(options.lineLengthThreshold);

    Charset charset = options.outputCharset != null ?
        Charset.forName(options.outputCharset) : null;
    builder.setOutputCharset(charset);

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
  }

  //------------------------------------------------------------------------
  // Optimizations
  //------------------------------------------------------------------------

  public void optimize() {
    // Ideally, this pass should be the first pass run, however:
    // 1) VariableReferenceCheck reports unexpected warnings if Normalize
    // is done first.
    // 2) ReplaceMessages, stripCode, and potentially custom passes rely on
    // unmodified local names.
    normalize();

    PhaseOptimizer phaseOptimizer = new PhaseOptimizer(this, tracker, null);
    if (options.devMode == DevMode.EVERY_PASS) {
      phaseOptimizer.setSanityCheck(sanityCheck);
    }
    phaseOptimizer.consume(getPassConfig().getOptimizations());
    phaseOptimizer.process(externsRoot, jsRoot);
    if (hasErrors()) {
      return;
    }
  }

  @Override
  void setCssRenamingMap(CssRenamingMap map) {
    options.cssRenamingMap = map;
  }

  @Override
  CssRenamingMap getCssRenamingMap() {
    return options.cssRenamingMap;
  }

  /**
   * Reprocesses the current defines over the AST.  This is used by GwtCompiler
   * to generate N outputs for different targets from the same (checked) AST.
   * For each target, we apply the target-specific defines by calling
   * {@code processDefines} and then {@code optimize} to optimize the AST
   * specifically for that target.
   */
  public void processDefines() {
    (new DefaultPassConfig(options)).processDefines.create(this)
        .process(externsRoot, jsRoot);
  }

  boolean isInliningForbidden() {
    return options.propertyRenaming == PropertyRenamingPolicy.HEURISTIC ||
        options.propertyRenaming ==
            PropertyRenamingPolicy.AGGRESSIVE_HEURISTIC;
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

  protected final CodeChangeHandler.RecentChange recentChange =
      new CodeChangeHandler.RecentChange();
  private final List<CodeChangeHandler> codeChangeHandlers =
      Lists.<CodeChangeHandler>newArrayList();

  /** Name of the synthetic input that holds synthesized externs. */
  static final String SYNTHETIC_EXTERNS = "{SyntheticVarsDeclar}";

  private CompilerInput synthesizedExternsInput = null;

  @Override
  void addChangeHandler(CodeChangeHandler handler) {
    codeChangeHandlers.add(handler);
  }

  @Override
  void removeChangeHandler(CodeChangeHandler handler) {
    codeChangeHandlers.remove(handler);
  }

  /**
   * All passes should call reportCodeChange() when they alter
   * the JS tree structure. This is verified by CompilerTestCase.
   * This allows us to optimize to a fixed point.
   */
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
  public boolean acceptEcmaScript5() {
    switch (options.getLanguageIn()) {
      case ECMASCRIPT5:
      case ECMASCRIPT5_STRICT:
        return true;
    }
    return false;
  }

  public LanguageMode languageMode() {
    return options.getLanguageIn();
  }

  @Override
  public boolean acceptConstKeyword() {
    return options.acceptConstKeyword;
  }

  @Override
  Config getParserConfig() {
    if (parserConfig == null) {
      Config.LanguageMode mode;
      switch (options.getLanguageIn()) {
        case ECMASCRIPT3:
          mode = Config.LanguageMode.ECMASCRIPT3;
          break;
        case ECMASCRIPT5:
          mode = Config.LanguageMode.ECMASCRIPT5;
          break;
        case ECMASCRIPT5_STRICT:
          mode = Config.LanguageMode.ECMASCRIPT5_STRICT;
          break;
        default:
          throw new IllegalStateException("unexpected language mode");
      }

      parserConfig = ParserRunner.createConfig(
        isIdeMode(),
        mode,
        acceptConstKeyword(),
        options.extraAnnotationNames);
    }
    return parserConfig;
  }

  @Override
  public boolean isTypeCheckingEnabled() {
    return options.checkTypes;
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
      "Please report this problem.\n" + message;

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
    debugLog.append(str);
    debugLog.append('\n');
    logger.fine(str);
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
    }
    return null;
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
    if (moduleInputs.size() > 0) {
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
    return Collections.<CompilerInput>unmodifiableList(inputs);
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
    return Collections.<CompilerInput>unmodifiableList(externs);
  }

  /**
   * Stores the internal compiler state just before optimization is performed.
   * This can be saved and restored in order to efficiently optimize multiple
   * different output targets without having to perform checking multiple times.
   *
   * NOTE: This does not include all parts of the compiler's internal state. In
   * particular, SourceFiles and CompilerOptions are not recorded. In
   * order to recreate a Compiler instance from scratch, you would need to
   * call {@code init} with the same arguments as in the initial creation before
   * restoring intermediate state.
   */
  public static class IntermediateState implements Serializable {
    private static final long serialVersionUID = 1L;

    Node externsRoot;
    private Node jsRoot;
    private List<CompilerInput> externs;
    private List<CompilerInput> inputs;
    private List<JSModule> modules;
    private PassConfig.State passConfigState;
    private JSTypeRegistry typeRegistry;
    private AbstractCompiler.LifeCycleStage lifeCycleStage;
    private Map<String, Node> injectedLibraries;

    private IntermediateState() {}
  }

  /**
   * Returns the current internal state, excluding the input files and modules.
   */
  public IntermediateState getState() {
    IntermediateState state = new IntermediateState();
    state.externsRoot = externsRoot;
    state.jsRoot = jsRoot;
    state.externs = externs;
    state.inputs = inputs;
    state.modules = modules;
    state.passConfigState = getPassConfig().getIntermediateState();
    state.typeRegistry = typeRegistry;
    state.lifeCycleStage = getLifeCycleStage();
    state.injectedLibraries = Maps.newLinkedHashMap(injectedLibraries);

    return state;
  }

  /**
   * Sets the internal state to the capture given.  Note that this assumes that
   * the input files are already set up.
   */
  public void setState(IntermediateState state) {
    externsRoot = state.externsRoot;
    jsRoot = state.jsRoot;
    externs = state.externs;
    inputs = state.inputs;
    modules = state.modules;
    passes = createPassConfigInternal();
    getPassConfig().setIntermediateState(state.passConfigState);
    typeRegistry = state.typeRegistry;
    setLifeCycleStage(state.lifeCycleStage);

    injectedLibraries.clear();
    injectedLibraries.putAll(state.injectedLibraries);
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
      synthesizedExternsInput = newExternInput(SYNTHETIC_EXTERNS);
    }
    return synthesizedExternsInput;
  }

  @Override
  public double getProgress() {
    return progress;
  }

  @Override
  void setProgress(double newProgress) {
    if (newProgress > 1.0) {
      progress = 1.0;
    } else if (newProgress < 0.0) {
      progress = 0.0;
    } else {
      progress = newProgress;
    }
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
  Node ensureLibraryInjected(String resourceName) {
    if (injectedLibraries.containsKey(resourceName)) {
      return null;
    }

    // All libraries depend on js/base.js
    boolean isBase = "base".equals(resourceName);
    if (!isBase) {
      ensureLibraryInjected("base");
    }

    Node firstChild = loadLibraryCode(resourceName).removeChildren();
    Node lastChild = firstChild.getLastSibling();

    Node parent = getNodeForCodeInsertion(null);
    if (isBase) {
      parent.addChildrenToFront(firstChild);
    } else {
      parent.addChildrenAfter(
          firstChild, injectedLibraries.get("base"));
    }
    reportCodeChange();

    injectedLibraries.put(resourceName, lastChild);
    return lastChild;
  }

  /** Load a library as a resource */
  @VisibleForTesting
  Node loadLibraryCode(String resourceName) {
    String originalCode;
    try {
      originalCode = CharStreams.toString(new InputStreamReader(
          Compiler.class.getResourceAsStream(
              String.format("js/%s.js", resourceName)),
          Charsets.UTF_8));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return Normalize.parseAndNormalizeSyntheticCode(
        this, originalCode,
        String.format("jscomp_%s_", resourceName));
  }

  /** Returns the compiler version baked into the jar. */
  public static String getReleaseVersion() {
    ResourceBundle config = ResourceBundle.getBundle(CONFIG_RESOURCE);
    return config.getString("compiler.version");
  }

  /** Returns the compiler date baked into the jar. */
  public static String getReleaseDate() {
    ResourceBundle config = ResourceBundle.getBundle(CONFIG_RESOURCE);
    return config.getString("compiler.date");
  }
}
