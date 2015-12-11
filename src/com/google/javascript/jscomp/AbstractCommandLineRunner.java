/*
 * Copyright 2009 The Closure Compiler Authors.
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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.google.javascript.jscomp.CompilerOptions.JsonStreamMode;
import com.google.javascript.jscomp.CompilerOptions.TweakProcessing;
import com.google.javascript.jscomp.deps.ClosureBundler;
import com.google.javascript.jscomp.deps.SourceCodeEscapers;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.TokenStream;
import com.google.protobuf.CodedOutputStream;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.Flushable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.annotation.Nullable;

/**
 * Implementations of AbstractCommandLineRunner translate flags into Java
 * API calls on the Compiler. AbstractCompiler contains common flags and logic
 * to make that happen.
 *
 * This class may be extended and used to create other Java classes
 * that behave the same as running the Compiler from the command line. Example:
 *
 * <pre>
 * class MyCommandLineRunner extends
 *     AbstractCommandLineRunner<MyCompiler, MyOptions> {
 *   MyCommandLineRunner(String[] args) {
 *     super(args);
 *   }
 *
 *   &#064;Override
 *   protected MyOptions createOptions() {
 *     MyOptions options = new MyOptions();
 *     CompilerFlagTranslator.setOptionsFromFlags(options);
 *     addMyCrazyCompilerPassThatOutputsAnExtraFile(options);
 *     return options;
 *   }
 *
 *   &#064;Override
 *   protected MyCompiler createCompiler() {
 *     return new MyCompiler();
 *   }
 *
 *   public static void main(String[] args) {
 *     (new MyCommandLineRunner(args)).run();
 *   }
 * }
 * </pre>
 *
 * @author bolinfest@google.com (Michael Bolin)
 */
@GwtIncompatible("Unnecessary")
public abstract class AbstractCommandLineRunner<A extends Compiler,
    B extends CompilerOptions> {
  static final DiagnosticType OUTPUT_SAME_AS_INPUT_ERROR = DiagnosticType.error(
      "JSC_OUTPUT_SAME_AS_INPUT_ERROR",
      "Bad output file (already listed as input file): {0}");
  static final DiagnosticType NO_TREE_GENERATED_ERROR = DiagnosticType.error(
      "JSC_NO_TREE_GENERATED_ERROR",
      "Code contains errors. No tree was generated.");

  static final String WAITING_FOR_INPUT_WARNING =
      "The compiler is waiting for input via stdin.";

  // The core language externs expected in externs.zip, in sorted order.
  private static final List<String> BUILTIN_LANG_EXTERNS = ImmutableList.of(
      "es3.js",
      "es5.js",
      "es6.js",
      "es6_collections.js");

  // Externs expected in externs.zip, in sorted order.
  // Externs not included in this list will be added last
  private static final List<String> BUILTIN_EXTERN_DEP_ORDER = ImmutableList.of(
    //-- browser externs --
    "browser/intl.js",
    "browser/w3c_event.js",
    "browser/w3c_event3.js",
    "browser/gecko_event.js",
    "browser/ie_event.js",
    "browser/webkit_event.js",
    "browser/w3c_device_sensor_event.js",
    "browser/w3c_dom1.js",
    "browser/w3c_dom2.js",
    "browser/w3c_dom3.js",
    "browser/gecko_dom.js",
    "browser/ie_dom.js",
    "browser/webkit_dom.js",
    "browser/w3c_css.js",
    "browser/gecko_css.js",
    "browser/ie_css.js",
    "browser/webkit_css.js",
    "browser/w3c_touch_event.js"
  );

  private final CommandLineConfig config;

  private final PrintStream defaultJsOutput;
  private final PrintStream err;
  private A compiler;

  private Charset inputCharset;

  // NOTE(nicksantos): JSCompiler has always used ASCII as the default
  // output charset. This was done to solve legacy problems with
  // bad proxies, etc. We are not sure if these issues are still problems,
  // and changing the encoding would require a big obnoxious migration plan.
  //
  // New outputs should use outputCharset2, which is how I would have
  // designed this if I had a time machine.
  private Charset outputCharset2;
  private String legacyOutputCharset;

  private boolean testMode = false;
  private Supplier<List<SourceFile>> externsSupplierForTesting = null;
  private Supplier<List<SourceFile>> inputsSupplierForTesting = null;
  private Supplier<List<JSModule>> modulesSupplierForTesting = null;
  private Function<Integer, Boolean> exitCodeReceiverForTesting = null;
  private Map<String, String> rootRelativePathsMap = null;

  private Map<String, String> parsedModuleWrappers = null;

  private final Gson gson;

  static final String OUTPUT_MARKER = "%output%";
  private static final String OUTPUT_MARKER_JS_STRING = "%output|jsstring%";

  private final List<JsonFileSpec> filesToStreamOut = new ArrayList<>();

  AbstractCommandLineRunner() {
    this(System.out, System.err);
  }

  AbstractCommandLineRunner(PrintStream out, PrintStream err) {
    this.config = new CommandLineConfig();
    this.defaultJsOutput = Preconditions.checkNotNull(out);
    this.err = Preconditions.checkNotNull(err);
    this.gson = new Gson();
  }

  /**
   * Put the command line runner into test mode. In test mode,
   * all outputs will be blackholed.
   * @param externsSupplier A provider for externs.
   * @param inputsSupplier A provider for source inputs.
   * @param modulesSupplier A provider for modules. Only one of inputsSupplier
   *     and modulesSupplier may be non-null.
   * @param exitCodeReceiver A receiver for the status code that would
   *     have been passed to System.exit in non-test mode.
   */
  @VisibleForTesting
  void enableTestMode(
      Supplier<List<SourceFile>> externsSupplier,
      Supplier<List<SourceFile>> inputsSupplier,
      Supplier<List<JSModule>> modulesSupplier,
      Function<Integer, Boolean> exitCodeReceiver) {
    Preconditions.checkArgument(
        inputsSupplier == null ^ modulesSupplier == null);
    testMode = true;
    this.externsSupplierForTesting = externsSupplier;
    this.inputsSupplierForTesting = inputsSupplier;
    this.modulesSupplierForTesting = modulesSupplier;
    this.exitCodeReceiverForTesting = exitCodeReceiver;
  }

  /**
   * Returns whether we're in test mode.
   */
  protected boolean isInTestMode() {
    return testMode;
  }

  /**
   * Returns whether output should be a JSON stream.
   */
  private boolean isOutputInJson() {
    return config.jsonStreamMode == JsonStreamMode.OUT
        || config.jsonStreamMode == JsonStreamMode.BOTH;
  }


  /**
   * Get the command line config, so that it can be initialized.
   */
  protected CommandLineConfig getCommandLineConfig() {
    return config;
  }

  /**
   * Returns the instance of the Compiler to use when {@link #run()} is
   * called.
   */
  protected abstract A createCompiler();

  /**
   * Returns the instance of the Options to use when {@link #run()} is called.
   * createCompiler() is called before createOptions(), so getCompiler()
   * will not return null when createOptions() is called.
   */
  protected abstract B createOptions();

  /**
   * The warning classes that are available from the command-line.
   */
  protected DiagnosticGroups getDiagnosticGroups() {
    if (compiler == null) {
      return new DiagnosticGroups();
    }
    return compiler.getDiagnosticGroups();
  }

  /**
   * A helper function for creating the dependency options object.
   */
  static DependencyOptions createDependencyOptions(
      boolean manageClosureDependencies,
      boolean onlyClosureDependencies,
      boolean processCommonJSModules,
      List<String> closureEntryPoints)
      throws FlagUsageException {
    if (onlyClosureDependencies) {
      if (closureEntryPoints.isEmpty()) {
        throw new FlagUsageException("When only_closure_dependencies is "
          + "on, you must specify at least one closure_entry_point");
      }

      return new DependencyOptions()
          .setDependencyPruning(true)
          .setDependencySorting(true)
          .setMoocherDropping(true)
          .setEntryPoints(closureEntryPoints);
    } else if (processCommonJSModules) {
      return new DependencyOptions()
        .setDependencyPruning(false)
        .setDependencySorting(true)
        .setMoocherDropping(false)
        .setEntryPoints(closureEntryPoints);
    } else if (manageClosureDependencies || !closureEntryPoints.isEmpty()) {
      return new DependencyOptions()
          .setDependencyPruning(true)
          .setDependencySorting(true)
          .setMoocherDropping(false)
          .setEntryPoints(closureEntryPoints);
    }
    return null;
  }

  protected abstract void addWhitelistWarningsGuard(
      CompilerOptions options, File whitelistFile);

  /**
   * Sets options based on the configurations set flags API.
   * Called during the run() run() method.
   * If you want to ignore the flags API, or interpret flags your own way,
   * then you should override this method.
   */
  protected void setRunOptions(CompilerOptions options)
      throws FlagUsageException, IOException {
    DiagnosticGroups diagnosticGroups = getDiagnosticGroups();

    if (config.warningGuards != null) {
      for (WarningGuardSpec.Entry entry : config.warningGuards.entries) {
        if ("*".equals(entry.groupName)) {
          Set<String> groupNames =
              diagnosticGroups.getRegisteredGroups().keySet();
          for (String groupName : groupNames) {
            if (!DiagnosticGroups.wildcardExcludedGroups.contains(groupName)) {
              diagnosticGroups.setWarningLevel(options, groupName, entry.level);
            }
          }
        } else {
          diagnosticGroups.setWarningLevel(options, entry.groupName,
              entry.level);
        }
      }
    }

    if (!config.warningsWhitelistFile.isEmpty()) {
      addWhitelistWarningsGuard(options, new File(config.warningsWhitelistFile));
    }

    if (!config.hideWarningsFor.isEmpty()) {
      options.addWarningsGuard(new ShowByPathWarningsGuard(
          config.hideWarningsFor.toArray(new String[] {}),
          ShowByPathWarningsGuard.ShowType.EXCLUDE));
    }

    createDefineOrTweakReplacements(config.define, options, false);

    options.setTweakProcessing(config.tweakProcessing);
    createDefineOrTweakReplacements(config.tweak, options, true);

    DependencyOptions depOptions = createDependencyOptions(
        config.manageClosureDependencies,
        config.onlyClosureDependencies,
        config.processCommonJSModules,
        config.closureEntryPoints);
    if (depOptions != null) {
      options.setDependencyOptions(depOptions);
    }

    options.devMode = config.jscompDevMode;
    options.setCodingConvention(config.codingConvention);
    options.setSummaryDetailLevel(config.summaryDetailLevel);
    options.setTrustedStrings(true);

    legacyOutputCharset = options.outputCharset = getLegacyOutputCharset();
    outputCharset2 = getOutputCharset2();
    inputCharset = getInputCharset();

    if (config.jsOutputFile.length() > 0) {
      if (config.skipNormalOutputs) {
        throw new FlagUsageException("skip_normal_outputs and js_output_file"
            + " cannot be used together.");
      }
    }

    if (config.skipNormalOutputs && config.printAst) {
      throw new FlagUsageException("skip_normal_outputs and print_ast cannot"
          + " be used together.");
    }

    if (config.skipNormalOutputs && config.printTree) {
      throw new FlagUsageException("skip_normal_outputs and print_tree cannot"
          + " be used together.");
    }

    if (config.createSourceMap.length() > 0) {
      options.sourceMapOutputPath = config.createSourceMap;
    } else if (isOutputInJson()) {
      options.sourceMapOutputPath = "%outname%";
    }
    options.sourceMapDetailLevel = config.sourceMapDetailLevel;
    options.sourceMapFormat = config.sourceMapFormat;
    options.sourceMapLocationMappings = config.sourceMapLocationMappings;

    ImmutableMap.Builder<String, SourceMapInput> inputSourceMaps
        = new ImmutableMap.Builder<>();
    for (Map.Entry<String, String> files :
             config.sourceMapInputFiles.entrySet()) {
      SourceFile sourceMap = SourceFile.fromFile(files.getValue());
      inputSourceMaps.put(
          files.getKey(), new SourceMapInput(sourceMap));
    }
    options.inputSourceMaps = inputSourceMaps.build();

    if (!config.variableMapInputFile.isEmpty()) {
      options.inputVariableMap =
          VariableMap.load(config.variableMapInputFile);
    }

    if (!config.propertyMapInputFile.isEmpty()) {
      options.inputPropertyMap =
          VariableMap.load(config.propertyMapInputFile);
    }

    if (config.languageIn.length() > 0) {
      CompilerOptions.LanguageMode languageMode =
          CompilerOptions.LanguageMode.fromString(config.languageIn);
      if (languageMode != null) {
        options.setLanguageIn(languageMode);
      } else {
        throw new FlagUsageException("Unknown language `" + config.languageIn
                                     + "' specified.");
      }
    }

    if (config.languageOut.isEmpty()) {
      options.setLanguageOut(options.getLanguageIn());
    } else {
      CompilerOptions.LanguageMode languageMode =
          CompilerOptions.LanguageMode.fromString(config.languageOut);
      if (languageMode != null) {
        options.setLanguageOut(languageMode);
      } else {
        throw new FlagUsageException("Unknown language `" + config.languageOut +
                                     "' specified.");
      }
    }

    if (!config.outputManifests.isEmpty()) {
      Set<String> uniqueNames = new HashSet<>();
      for (String filename : config.outputManifests) {
        if (!uniqueNames.add(filename)) {
          throw new FlagUsageException("output_manifest flags specify " +
              "duplicate file names: " + filename);
        }
      }
    }

    if (!config.outputBundles.isEmpty()) {
      Set<String> uniqueNames = new HashSet<>();
      for (String filename : config.outputBundles) {
        if (!uniqueNames.add(filename)) {
          throw new FlagUsageException("output_bundle flags specify " +
              "duplicate file names: " + filename);
        }
      }
    }

    options.transformAMDToCJSModules = config.transformAMDToCJSModules;
    options.processCommonJSModules = config.processCommonJSModules;
    options.moduleRoots = config.moduleRoots;
    options.angularPass = config.angularPass;
    options.tracer = config.tracerMode;
    options.setNewTypeInference(config.useNewTypeInference);
    options.instrumentationTemplateFile = config.instrumentationTemplateFile;
  }

  protected final A getCompiler() {
    return compiler;
  }

  /**
   * @return a mutable list
   * @throws IOException
   */
  public static List<SourceFile> getBuiltinExterns(CompilerOptions options)
      throws IOException {
    InputStream input = AbstractCommandLineRunner.class.getResourceAsStream(
        "/externs.zip");
    if (input == null) {
      // In some environments, the externs.zip is relative to this class.
      input = AbstractCommandLineRunner.class.getResourceAsStream("externs.zip");
    }
    Preconditions.checkNotNull(input);

    ZipInputStream zip = new ZipInputStream(input);
    CompilerOptions.Environment env = options.getEnvironment();
    String envPrefix = env.toString().toLowerCase() + "/";
    String browserEnv = CompilerOptions.Environment.BROWSER.toString().toLowerCase();
    boolean flatExternStructure = true;
    Map<String, SourceFile> mapFromExternsZip = new HashMap<>();
    for (ZipEntry entry = null; (entry = zip.getNextEntry()) != null; ) {
      String filename = entry.getName();
      if (filename.contains(browserEnv)) {
        flatExternStructure = false;
      }
      // Always load externs in the root folder.
      // If the non-core-JS externs are organized in subfolders, only load
      // the ones in a subfolder matching the specified environment.
      if (!filename.contains("/")
          || (filename.indexOf(envPrefix) == 0
              && filename.length() > envPrefix.length())) {
        BufferedInputStream entryStream = new BufferedInputStream(
            ByteStreams.limit(zip, entry.getSize()));
        mapFromExternsZip.put(filename,
            SourceFile.fromInputStream(
                // Give the files an odd prefix, so that they do not conflict
                // with the user's files.
                "externs.zip//" + filename,
                entryStream,
                UTF_8));
      }
    }

    List<SourceFile> externs = new ArrayList<>();
    // The externs for core JS objects are loaded in all environments.
    for (String key : BUILTIN_LANG_EXTERNS) {
      Preconditions.checkState(
          mapFromExternsZip.containsKey(key),
          "Externs zip must contain %s.", key);
      externs.add(mapFromExternsZip.remove(key));
    }
    // Order matters, so extern resources which have dependencies must be added
    // to the result list in the expected order.
    for (String key : BUILTIN_EXTERN_DEP_ORDER) {
      if (!flatExternStructure && !key.contains(envPrefix)) {
        continue;
      }
      if (flatExternStructure) {
        key = key.substring(key.indexOf('/') + 1);
      }
      Preconditions.checkState(
          mapFromExternsZip.containsKey(key),
          "Externs zip must contain %s when environment is %s.", key, env);
      externs.add(mapFromExternsZip.remove(key));
    }
    externs.addAll(mapFromExternsZip.values());
    return externs;
  }

  /**
   * Runs the Compiler and calls System.exit() with the exit status of the
   * compiler.
   */
  public final void run() {
    int result = 0;
    int runs = 1;
    try {
      for (int i = 0; i < runs && result == 0; i++) {
        result = doRun();
      }
    } catch (AbstractCommandLineRunner.FlagUsageException e) {
      System.err.println(e.getMessage());
      result = -1;
    } catch (Throwable t) {
      t.printStackTrace();
      result = -2;
    }

    if (testMode) {
      exitCodeReceiverForTesting.apply(result);
    } else {
      System.exit(result);
    }
  }

  /**
   * Returns the PrintStream for writing errors associated with this
   * AbstractCommandLineRunner.
   */
  protected PrintStream getErrorPrintStream() {
    return err;
  }

  /**
   * An exception thrown when command-line flags are used incorrectly.
   */
  public static class FlagUsageException extends Exception {
    private static final long serialVersionUID = 1L;

    public FlagUsageException(String message) {
      super(message);
    }
  }

  public List<JsonFileSpec> parseJsonFilesFromInputStream() throws IOException {
    List<JsonFileSpec> jsonFiles = new ArrayList<>();
    try (JsonReader reader = new JsonReader(new InputStreamReader(System.in, inputCharset))) {
      reader.beginArray();
      while (reader.hasNext()) {
        JsonFileSpec jsonFile = gson.fromJson(reader, JsonFileSpec.class);
        jsonFiles.add(jsonFile);
      }
      reader.endArray();
    }
    return jsonFiles;
  }

  /**
   * Creates inputs from a list of files.
   *
   * Can be overridden by subclasses who want to pull files from different
   * places.
   *
   * @param files A list of filenames
   * @param allowStdIn Whether '-' is allowed appear as a filename to represent
   *        stdin. If true, '-' is only allowed to appear once.
   * @return An array of inputs
   */
  protected List<SourceFile> createInputs(List<String> files,
      boolean allowStdIn) throws FlagUsageException, IOException {
    return createInputs(files, new ArrayList<String>() /* zips */, allowStdIn);
  }

  /**
   * Creates inputs from a list of source files and json files.
   *
   * Can be overridden by subclasses who want to pull files from different
   * places.
   *
   * @param files A list of filenames.
   * @param jsonFiles A list of json encoded files.
   * @return An array of inputs
   */
  protected List<SourceFile> createInputs(List<String> files,
      List<JsonFileSpec> jsonFiles) throws FlagUsageException, IOException {
    return createInputs(files, new ArrayList<String>() /* zips */, jsonFiles, false);
  }

  /**
   * Creates inputs from a list of source files and zips.
   *
   * Can be overridden by subclasses who want to pull files from different
   * places.
   *
   * @param files A list of filenames.
   * @param zips A list of zip filenames.
   * @param allowStdIn Whether '-' is allowed appear as a filename to represent
   *        stdin. If true, '-' is only allowed to appear once.
   * @return An array of inputs
   */
  protected List<SourceFile> createInputs(List<String> files,
      List<String> zips, boolean allowStdIn) throws FlagUsageException, IOException {
    return createInputs(files, zips, null, allowStdIn);
  }

  /**
   * Creates inputs from a list of source files, zips and json files.
   *
   * Can be overridden by subclasses who want to pull files from different
   * places.
   *
   * @param files A list of filenames.
   * @param zips A list of zip filenames.
   * @param jsonFiles A list of json encoded files.
   * @param allowStdIn Whether '-' is allowed appear as a filename to represent
   *        stdin. If true, '-' is only allowed to appear once.
   * @return An array of inputs
   */
  protected List<SourceFile> createInputs(List<String> files,
      List<String> zips, List<JsonFileSpec> jsonFiles, boolean allowStdIn)
      throws FlagUsageException, IOException {
    List<SourceFile> inputs = new ArrayList<>(files.size());
    boolean usingStdin = false;
    for (String filename : files) {
      if (!"-".equals(filename)) {
        SourceFile newFile = SourceFile.fromFile(filename, inputCharset);
        inputs.add(newFile);
      } else {
        if (!allowStdIn) {
          throw new FlagUsageException("Can't specify stdin.");
        }
        if (usingStdin) {
          throw new FlagUsageException("Can't specify stdin twice.");
        }

        if (!config.outputManifests.isEmpty()) {
          throw new FlagUsageException("Manifest files cannot be generated " +
              "when the input is from stdin.");
        }
        if (!config.outputBundles.isEmpty()) {
          throw new FlagUsageException("Bundle files cannot be generated " +
              "when the input is from stdin.");
        }

        this.err.println(WAITING_FOR_INPUT_WARNING);
        inputs.add(SourceFile.fromInputStream("stdin", System.in, inputCharset));
        usingStdin = true;
      }
    }
    for (String zipName : zips) {
      if (!"-".equals(zipName)) {
        List<SourceFile> newFiles = SourceFile.fromZipFile(zipName, inputCharset);
        inputs.addAll(newFiles);
      }
    }
    if (jsonFiles != null) {
      for (JsonFileSpec jsonFile : jsonFiles) {
        inputs.add(SourceFile.fromCode(jsonFile.getPath(), jsonFile.getSrc()));
      }
    }
    return inputs;
  }

  /**
   * Creates JS source code inputs from a list of files.
   */
  private List<SourceFile> createSourceInputs(List<String> files, List<String> zips,
      List<JsonFileSpec> jsonFiles)
      throws FlagUsageException, IOException {
    if (isInTestMode()) {
      return inputsSupplierForTesting != null ? inputsSupplierForTesting.get()
          : null;
    }
    if (files.isEmpty() && zips.isEmpty() && jsonFiles == null) {
      // Request to read from stdin.
      files = Collections.singletonList("-");
    }
    try {
      if (jsonFiles != null) {
        return createInputs(files, jsonFiles);
      } else {
        return createInputs(files, zips, true);
      }
    } catch (FlagUsageException e) {
      throw new FlagUsageException("Bad --js flag. " + e.getMessage());
    }
  }

  /**
   * Creates JS extern inputs from a list of files.
   */
  private List<SourceFile> createExternInputs(List<String> files)
      throws FlagUsageException, IOException {
    if (files.isEmpty()) {
      return ImmutableList.of(SourceFile.fromCode("/dev/null", ""));
    }
    try {
      return createInputs(files, false);
    } catch (FlagUsageException e) {
      throw new FlagUsageException("Bad --externs flag. " + e.getMessage());
    }
  }

  /**
   * Creates module objects from a list of module specifications.
   *
   * @param specs A list of module specifications, not null or empty. The spec
   *        format is: <code>name:num-js-files[:[dep,...][:]]</code>. Module
   *        names must not contain the ':' character.
   * @param jsFiles A list of JS file paths, not null
   * @return An array of module objects
   */
  List<JSModule> createJsModules(
      List<String> specs, List<SourceFile> inputs)
      throws FlagUsageException, IOException {
    if (isInTestMode()) {
      return modulesSupplierForTesting.get();
    }

    Preconditions.checkState(specs != null);
    Preconditions.checkState(!specs.isEmpty());
    Preconditions.checkState(inputs != null);

    List<String> moduleNames = new ArrayList<>(specs.size());
    Map<String, JSModule> modulesByName = new LinkedHashMap<>();
    Map<String, Integer> modulesFileCountMap = new LinkedHashMap<>();
    int numJsFilesExpected = 0, minJsFilesRequired = 0;
    boolean isFirstModule = true;
    for (String spec : specs) {
      // Format is "<name>:<num-js-files>[:[<dep>,...][:]]".
      String[] parts = spec.split(":");
      if (parts.length < 2 || parts.length > 4) {
        throw new FlagUsageException("Expected 2-4 colon-delimited parts in "
            + "module spec: " + spec);
      }

      // Parse module name.
      String name = parts[0];
      checkModuleName(name);
      if (modulesByName.containsKey(name)) {
        throw new FlagUsageException("Duplicate module name: " + name);
      }
      JSModule module = new JSModule(name);

      if (parts.length > 2) {
        // Parse module dependencies.
        String depList = parts[2];
        if (depList.length() > 0) {
          String[] deps = depList.split(",");
          for (String dep : deps) {
            JSModule other = modulesByName.get(dep);
            if (other == null) {
              throw new FlagUsageException("Module '" + name
                  + "' depends on unknown module '" + dep
                  + "'. Be sure to list modules in dependency order.");
            }
            module.addDependency(other);
          }
        }
      }

      // Parse module inputs.
      int numJsFiles = -1;
      try {
        numJsFiles = Integer.parseInt(parts[1]);
      } catch (NumberFormatException ignored) {
        numJsFiles = -1;
      }

      // We will allow modules of zero input.
      if (numJsFiles < 0) {
        // A size of 'auto' is only allowed on the base module if
        // and it must also be the first module
        if (parts.length == 2 && "auto".equals(parts[1])) {
          if (isFirstModule) {
            numJsFilesExpected = -1;
          } else {
            throw new FlagUsageException("Invalid JS file count '" + parts[1]
                + "' for module: " + name + ". Only the first module may specify " +
                "a size of 'auto' and it must have no dependencies.");
          }
        } else {
          throw new FlagUsageException("Invalid JS file count '" + parts[1]
              + "' for module: " + name);
        }
      } else {
        minJsFilesRequired += numJsFiles;
      }


      if (numJsFilesExpected >= 0) {
        numJsFilesExpected += numJsFiles;
      }

      // Add modules in reverse order so that source files are allocated to
      // modules in reverse order. This allows the first module
      // (presumably the base module) to have a size of 'auto'
      moduleNames.add(0, name);
      modulesFileCountMap.put(name, numJsFiles);
      modulesByName.put(name, module);
    }

    final int totalNumJsFiles = inputs.size();

    if (numJsFilesExpected >= 0 || minJsFilesRequired > totalNumJsFiles) {
      if (minJsFilesRequired > totalNumJsFiles) {
        numJsFilesExpected = minJsFilesRequired;
      }

      if (numJsFilesExpected > totalNumJsFiles) {
        throw new FlagUsageException("Not enough JS files specified. Expected "
            + numJsFilesExpected + " but found " + totalNumJsFiles);
      } else if (numJsFilesExpected < totalNumJsFiles) {
        throw new FlagUsageException("Too many JS files specified. Expected "
                + numJsFilesExpected + " but found " + totalNumJsFiles);
      }
    }

    int numJsFilesLeft = totalNumJsFiles, moduleIndex = 0;
    for (String moduleName : moduleNames) {
      // Parse module inputs.
      int numJsFiles = modulesFileCountMap.get(moduleName);
      JSModule module = modulesByName.get(moduleName);

      // Check if the first module specified 'auto' for the number of files
      if (moduleIndex == moduleNames.size() - 1 && numJsFiles == -1) {
        numJsFiles = numJsFilesLeft;
      }

      List<SourceFile> moduleFiles =
          inputs.subList(numJsFilesLeft - numJsFiles, numJsFilesLeft);
      for (SourceFile input : moduleFiles) {
        module.add(input);
      }
      numJsFilesLeft -= numJsFiles;
      moduleIndex++;
    }

    return new ArrayList<>(modulesByName.values());
  }

  /**
   * Validates the module name. Can be overridden by subclasses.
   * @param name The module name
   * @throws FlagUsageException if the validation fails
   */
  protected void checkModuleName(String name)
      throws FlagUsageException {
    if (!TokenStream.isJSIdentifier(name)) {
      throw new FlagUsageException("Invalid module name: '" + name + "'");
    }
  }

  /**
   * Parses module wrapper specifications.
   *
   * @param specs A list of module wrapper specifications, not null. The spec
   *        format is: <code>name:wrapper</code>. Wrappers.
   * @param modules The JS modules whose wrappers are specified
   * @return A map from module name to module wrapper. Modules with no wrapper
   *         will have the empty string as their value in this map.
   */
  static Map<String, String> parseModuleWrappers(List<String> specs,
      List<JSModule> modules) throws FlagUsageException {
    Preconditions.checkState(specs != null);

    Map<String, String> wrappers = Maps.newHashMapWithExpectedSize(modules.size());

    // Prepopulate the map with module names.
    for (JSModule m : modules) {
      wrappers.put(m.getName(), "");
    }

    for (String spec : specs) {

      // Format is "<name>:<wrapper>".
      int pos = spec.indexOf(':');
      if (pos == -1) {
        throw new FlagUsageException("Expected module wrapper to have "
            + "<name>:<wrapper> format: " + spec);
      }

      // Parse module name.
      String name = spec.substring(0, pos);
      if (!wrappers.containsKey(name)) {
        throw new FlagUsageException("Unknown module: '" + name + "'");
      }
      String wrapper = spec.substring(pos + 1);
      if (!wrapper.contains("%s")) {
        throw new FlagUsageException("No %s placeholder in module wrapper: '"
            + wrapper + "'");
      }
      wrappers.put(name, wrapper);
    }
    return wrappers;
  }

  private String getModuleOutputFileName(JSModule m) {
    return config.moduleOutputPathPrefix + m.getName() + ".js";
  }

  @VisibleForTesting
  void writeModuleOutput(Appendable out, JSModule m)
      throws FlagUsageException, IOException {
    if (parsedModuleWrappers == null) {
      parsedModuleWrappers = parseModuleWrappers(
          config.moduleWrapper,
          ImmutableList.copyOf(
              compiler.getDegenerateModuleGraph().getAllModules()));
    }

    String fileName = getModuleOutputFileName(m);
    String baseName = new File(fileName).getName();
    writeOutput(out, compiler, compiler.toSource(m),
        parsedModuleWrappers.get(m.getName()).replace("%basename%", baseName),
        "%s", null);
  }

  /**
   * Writes code to an output stream, optionally wrapping it in an arbitrary
   * wrapper that contains a placeholder where the code should be inserted.
   */
  static void writeOutput(Appendable out, Compiler compiler, String code,
      String wrapper, String codePlaceholder,
      @Nullable Function<String, String> escaper)
      throws IOException {
    int pos = wrapper.indexOf(codePlaceholder);
    if (pos != -1) {
      String prefix = "";

      if (pos > 0) {
        prefix = wrapper.substring(0, pos);
        out.append(prefix);
      }

      out.append(escaper == null ? code : escaper.apply(code));

      int suffixStart = pos + codePlaceholder.length();
      if (suffixStart != wrapper.length()) {
        // Something after placeholder?
        out.append(wrapper.substring(suffixStart));
      }
      // Make sure we always end output with a line feed.
      out.append('\n');

      // If we have a source map, adjust its offsets to match
      // the code WITHIN the wrapper.
      if (compiler != null && compiler.getSourceMap() != null) {
        compiler.getSourceMap().setWrapperPrefix(prefix);
      }

    } else {
      out.append(code);
      out.append('\n');
    }
  }

  /**
   * Creates any directories necessary to write a file that will have a given
   * path prefix.
   */
  private static void maybeCreateDirsForPath(String pathPrefix) {
    if (!Strings.isNullOrEmpty(pathPrefix)) {
      String dirName =
          pathPrefix.charAt(pathPrefix.length() - 1) == File.separatorChar
              ? pathPrefix.substring(0, pathPrefix.length() - 1)
              : new File(pathPrefix).getParent();
      if (dirName != null) {
        new File(dirName).mkdirs();
      }
    }
  }

  private Appendable createDefaultOutput() throws IOException {
    boolean writeOutputToFile = !config.jsOutputFile.isEmpty();
    if (writeOutputToFile) {
      return fileNameToLegacyOutputWriter(config.jsOutputFile);
    } else {
      return streamToLegacyOutputWriter(defaultJsOutput);
    }
  }

  private static void closeAppendable(Appendable output) throws IOException {
    if (output instanceof Flushable) {
      ((Flushable) output).flush();
    }
    if (output instanceof Closeable) {
      ((Closeable) output).close();
    }
  }

  /**
   * Parses command-line arguments and runs the compiler.
   *
   * @return system exit status
   */
  protected int doRun() throws FlagUsageException, IOException {
    Compiler.setLoggingLevel(Level.parse(config.loggingLevel));

    compiler = createCompiler();
    B options = createOptions();

    List<SourceFile> externs = createExterns(options);

    List<JSModule> modules = null;
    Result result = null;

    setRunOptions(options);

    boolean writeOutputToFile = !config.jsOutputFile.isEmpty();
    List<String> outputFileNames = new ArrayList<>();
    if (writeOutputToFile) {
      outputFileNames.add(config.jsOutputFile);
    }

    List<String> jsFiles = config.js;
    List<String> moduleSpecs = config.module;
    List<JsonFileSpec> jsonFiles = null;

    if (config.jsonStreamMode == JsonStreamMode.IN ||
        config.jsonStreamMode == JsonStreamMode.BOTH) {
      jsonFiles = parseJsonFilesFromInputStream();

      ImmutableMap.Builder<String, SourceMapInput> inputSourceMaps
          = new ImmutableMap.Builder<>();

      boolean foundJsonInputSourceMap = false;
      for (JsonFileSpec jsonFile : jsonFiles) {
        if (jsonFile.getSourceMap() != null && jsonFile.getSourceMap().length() > 0) {
          String sourceMapPath = jsonFile.getPath() + ".map";
          SourceFile sourceMap = SourceFile.fromCode(sourceMapPath,
              jsonFile.getSourceMap());
          inputSourceMaps.put(sourceMapPath, new SourceMapInput(sourceMap));
          foundJsonInputSourceMap = true;
        }
      }

      if (foundJsonInputSourceMap) {
        inputSourceMaps.putAll(options.inputSourceMaps);
        options.inputSourceMaps = inputSourceMaps.build();
      }
    }

    boolean createCommonJsModules = false;
    if (options.processCommonJSModules
        && (moduleSpecs.size() == 1 && "auto".equals(moduleSpecs.get(0)))) {
      createCommonJsModules = true;
      moduleSpecs.remove(0);
    }
    List<SourceFile> inputs = createSourceInputs(jsFiles, config.jsZip, jsonFiles);
    if (!moduleSpecs.isEmpty()) {
      modules = createJsModules(moduleSpecs, inputs);
      for (JSModule m : modules) {
        outputFileNames.add(getModuleOutputFileName(m));
      }

      if (config.skipNormalOutputs) {
        compiler.initModules(externs, modules, options);
      } else {
        result = compiler.compileModules(externs, modules, options);
      }
    } else {
      if (config.skipNormalOutputs) {
        compiler.init(externs, inputs, options);
        compiler.hoistExterns();
      } else {
        result = compiler.compile(externs, inputs, options);
      }
    }
    if (createCommonJsModules) {
      // For CommonJS modules construct modules from actual inputs.
      modules = ImmutableList.copyOf(compiler.getDegenerateModuleGraph()
          .getAllModules());
      for (JSModule m : modules) {
        outputFileNames.add(getModuleOutputFileName(m));
      }
    }

    for (String outputFileName : outputFileNames) {
      if (compiler.getSourceFileByName(outputFileName) != null) {
        compiler.report(
            JSError.make(OUTPUT_SAME_AS_INPUT_ERROR, outputFileName));
        return 1;
      }
    }

    return processResults(result, modules, options);
  }

  /**
   * Processes the results of the compile job, and returns an error code.
   */
  int processResults(Result result, List<JSModule> modules, B options)
       throws FlagUsageException, IOException {
    if (config.printPassGraph) {
      if (compiler.getRoot() == null) {
        return 1;
      } else {
        Appendable jsOutput = createDefaultOutput();
        jsOutput.append(
            DotFormatter.toDot(compiler.getPassConfig().getPassGraph()));
        jsOutput.append('\n');
        closeAppendable(jsOutput);
        return 0;
      }
    }

    if (config.printAst) {
      if (compiler.getRoot() == null) {
        return 1;
      } else {
        Appendable jsOutput = createDefaultOutput();
        ControlFlowGraph<Node> cfg = compiler.computeCFG();
        DotFormatter.appendDot(
            compiler.getRoot().getLastChild(), cfg, jsOutput);
        jsOutput.append('\n');
        closeAppendable(jsOutput);
        return 0;
      }
    }

    if (config.printTree) {
      if (compiler.getRoot() == null) {
        compiler.report(JSError.make(NO_TREE_GENERATED_ERROR));
        return 1;
      } else {
        Appendable jsOutput = createDefaultOutput();
        compiler.getRoot().appendStringTree(jsOutput);
        jsOutput.append("\n");
        closeAppendable(jsOutput);
        return 0;
      }
    }

    rootRelativePathsMap = constructRootRelativePathsMap();

    if (config.skipNormalOutputs) {
      // Output the manifest and bundle files if requested.
      outputManifest();
      outputBundle();
      outputModuleGraphJson();
      return 0;
    } else if (!options.checksOnly && result.success) {
      outputModuleGraphJson();
      if (modules == null) {
        outputSingleBinary(options);

        // Output the source map if requested.
        // If output files are being written to stdout as a JSON string,
        // outputSingleBinary will have added the sourcemap to the output file
        if (!isOutputInJson()) {
          outputSourceMap(options, config.jsOutputFile);
        }
      } else {
        outputModuleBinaryAndSourceMaps(modules, options);
      }

      // Output the externs if required.
      if (options.externExportsPath != null) {
        try (Writer eeOut = openExternExportsStream(options, config.jsOutputFile)) {
          eeOut.append(result.externExport);
        }
      }

      // Output the variable and property name maps if requested.
      outputNameMaps();

      // Output the manifest and bundle files if requested.
      outputManifest();
      outputBundle();

      if (isOutputInJson()) {
        outputJsonStream();
      }
    }

    // return 0 if no errors, the error count otherwise
    return Math.min(result.errors.length, 0x7f);
  }

  Function<String, String> getJavascriptEscaper() {
    return SourceCodeEscapers.javascriptEscaper().asFunction();
  }

  void outputSingleBinary(B options) throws IOException {
    Function<String, String> escaper = null;
    String marker = OUTPUT_MARKER;
    if (config.outputWrapper.contains(OUTPUT_MARKER_JS_STRING)) {
      marker = OUTPUT_MARKER_JS_STRING;
      escaper = getJavascriptEscaper();
    }

    if (isOutputInJson()) {
      this.filesToStreamOut.add(createJsonFile(options, marker, escaper));
    } else {
      if(!config.jsOutputFile.isEmpty()) {
        maybeCreateDirsForPath(config.jsOutputFile);
      }

      Appendable jsOutput = createDefaultOutput();
      writeOutput(
          jsOutput, compiler, compiler.toSource(), config.outputWrapper,
          marker, escaper);
      closeAppendable(jsOutput);
    }
  }

  /**
   * Save the compiler output to a JsonFileSpec to be later written to
   * stdout
   */
  JsonFileSpec createJsonFile(B options, String outputMarker,
      Function<String, String> escaper) throws IOException {
    Appendable jsOutput = new StringBuilder();
    writeOutput(
        jsOutput, compiler, compiler.toSource(), config.outputWrapper,
        outputMarker, escaper);

    JsonFileSpec jsonOutput = new JsonFileSpec(jsOutput.toString(),
        Strings.isNullOrEmpty(config.jsOutputFile) ?
            "compiled.js" : config.jsOutputFile);

    if (!Strings.isNullOrEmpty(options.sourceMapOutputPath)) {
      StringBuilder sourcemap = new StringBuilder();
      compiler.getSourceMap().appendTo(sourcemap, jsonOutput.getPath());
      jsonOutput.setSourceMap(sourcemap.toString());
    }

    return jsonOutput;
  }

  void outputJsonStream() throws IOException {
    try (JsonWriter jsonWriter =
            new JsonWriter(new BufferedWriter(new OutputStreamWriter(defaultJsOutput, "UTF-8")))) {
      jsonWriter.beginArray();
      for (JsonFileSpec jsonFile : this.filesToStreamOut) {
        jsonWriter.beginObject();
        jsonWriter.name("src").value(jsonFile.getSrc());
        jsonWriter.name("path").value(jsonFile.getPath());
        if (!Strings.isNullOrEmpty(jsonFile.getSourceMap())) {
          jsonWriter.name("source_map").value(jsonFile.getSourceMap());
        }
        jsonWriter.endObject();
      }
      jsonWriter.endArray();
    }
  }

  private void outputModuleBinaryAndSourceMaps(
      List<JSModule> modules, B options)
      throws FlagUsageException, IOException {
    parsedModuleWrappers = parseModuleWrappers(
        config.moduleWrapper, modules);
    maybeCreateDirsForPath(config.moduleOutputPathPrefix);

    // If the source map path is in fact a pattern for each
    // module, create a stream per-module. Otherwise, create
    // a single source map.
    Writer mapFileOut = null;

    // When the json_streams flag is specified, sourcemaps are always generated
    // per module
    if (!(shouldGenerateMapPerModule(options) || isOutputInJson())) {
      mapFileOut = fileNameToOutputWriter2(
          expandSourceMapPath(options, null));
    }

    for (JSModule m : modules) {
      if (isOutputInJson()) {
        this.filesToStreamOut.add(createJsonFileFromModule(m));
      } else {
        if (shouldGenerateMapPerModule(options)) {
          mapFileOut = fileNameToOutputWriter2(expandSourceMapPath(options, m));
        }

        try (Writer writer = fileNameToLegacyOutputWriter(
            getModuleOutputFileName(m))) {
          if (options.sourceMapOutputPath != null) {
            compiler.getSourceMap().reset();
          }
          writeModuleOutput(writer, m);
          if (options.sourceMapOutputPath != null) {
            compiler.getSourceMap().appendTo(mapFileOut, m.getName());
          }
        }

        if (shouldGenerateMapPerModule(options) && mapFileOut != null) {
          mapFileOut.close();
          mapFileOut = null;
        }
      }
    }

    if (mapFileOut != null) {
      mapFileOut.close();
    }
  }

  /**
   * Given an output module, convert it to a JSONFileSpec with associated
   * sourcemap
   */
  private JsonFileSpec createJsonFileFromModule(JSModule module) throws
      FlagUsageException, IOException{
    compiler.getSourceMap().reset();

    StringBuilder output = new StringBuilder();
    writeModuleOutput(output, module);

    JsonFileSpec jsonFile = new JsonFileSpec(output.toString(),
        getModuleOutputFileName(module));

    StringBuilder moduleSourceMap = new StringBuilder();

    compiler.getSourceMap().appendTo(moduleSourceMap,
        getModuleOutputFileName(module));

    jsonFile.setSourceMap(moduleSourceMap.toString());

    return jsonFile;
  }

  /**
   * Query the flag for the input charset, and return a Charset object
   * representing the selection.
   *
   * @return Charset to use when reading inputs
   * @throws FlagUsageException if flag is not a valid Charset name.
   */
  private Charset getInputCharset() throws FlagUsageException {
    if (!config.charset.isEmpty()) {
      if (!Charset.isSupported(config.charset)) {
        throw new FlagUsageException(config.charset +
            " is not a valid charset name.");
      }
      return Charset.forName(config.charset);
    }
    return UTF_8;
  }

  /**
   * Query the flag for the output charset.
   *
   * Let the outputCharset be the same as the input charset... except if
   * we're reading in UTF-8 by default.  By tradition, we've always
   * output ASCII to avoid various hiccups with different browsers,
   * proxies and firewalls.
   *
   * @return Name of the charset to use when writing outputs. Guaranteed to
   *    be a supported charset.
   * @throws FlagUsageException if flag is not a valid Charset name.
   */
  private String getLegacyOutputCharset() throws FlagUsageException {
    if (!config.charset.isEmpty()) {
      if (!Charset.isSupported(config.charset)) {
        throw new FlagUsageException(config.charset +
            " is not a valid charset name.");
      }
      return config.charset;
    }
    return "US-ASCII";
  }

  /**
   * Query the flag for the output charset. Defaults to UTF-8.
   * @throws FlagUsageException if flag is not a valid Charset name.
   */
  private Charset getOutputCharset2() throws FlagUsageException {
    if (!config.charset.isEmpty()) {
      if (!Charset.isSupported(config.charset)) {
        throw new FlagUsageException(config.charset +
            " is not a valid charset name.");
      }
      return Charset.forName(config.charset);
    }
    return UTF_8;
  }

  protected List<SourceFile> createExterns(CompilerOptions options)
      throws FlagUsageException, IOException {
    return isInTestMode() ? externsSupplierForTesting.get() :
        createExternInputs(config.externs);
  }

  /**
   * Returns true if and only if a source map file should be generated for each
   * module, as opposed to one unified map. This is specified by having the
   * source map pattern include the %outname% variable.
   */
  private boolean shouldGenerateMapPerModule(B options) {
    return options.sourceMapOutputPath != null
        && options.sourceMapOutputPath.contains("%outname%");
  }

  /**
   * Returns a stream for outputting the generated externs file.
   *
   * @param options The options to the Compiler.
   * @param path The path of the generated JS source file.
   *
   * @return The stream or null if no extern-ed exports are being generated.
   */
  private Writer openExternExportsStream(B options,
      String path) throws IOException {
    if (options.externExportsPath == null) {
      return null;
    }

    String exPath = options.externExportsPath;

    if (!exPath.contains(File.separator)) {
      File outputFile = new File(path);
      exPath = outputFile.getParent() + File.separatorChar + exPath;
    }

    return fileNameToOutputWriter2(exPath);
  }

  /**
   * Expand a file path specified on the command-line.
   *
   * Most file paths on the command-line allow an %outname% placeholder.
   * The placeholder will expand to a different value depending on
   * the current output mode. There are three scenarios:
   *
   * 1) Single JS output, single extra output: sub in jsOutputPath.
   * 2) Multiple JS output, single extra output: sub in the base module name.
   * 3) Multiple JS output, multiple extra output: sub in the module output
   *    file.
   *
   * Passing a JSModule to this function automatically triggers case #3.
   * Otherwise, we'll use strategy #1 or #2 based on the current output mode.
   */
  private String expandCommandLinePath(
      String path, JSModule forModule) {
    String sub;
    if (forModule != null) {
      sub = config.moduleOutputPathPrefix + forModule.getName() + ".js";
    } else if (!config.module.isEmpty()) {
      sub = config.moduleOutputPathPrefix;
    } else {
      sub = config.jsOutputFile;
    }
    return path.replace("%outname%", sub);
  }

  /** Expansion function for source map. */
  @VisibleForTesting
  String expandSourceMapPath(B options, JSModule forModule) {
    if (Strings.isNullOrEmpty(options.sourceMapOutputPath)) {
      return null;
    }
    return expandCommandLinePath(options.sourceMapOutputPath, forModule);
  }

  /**
   * Converts a file name into a Writer taking in account the output charset.
   * Returns null if the file name is null.
   */
  private Writer fileNameToLegacyOutputWriter(String fileName)
      throws IOException {
    if (fileName == null) {
      return null;
    }
    if (testMode) {
      return new StringWriter();
    }

    return streamToLegacyOutputWriter(filenameToOutputStream(fileName));
  }

  /**
   * Converts a file name into a Writer taking in account the output charset.
   * Returns null if the file name is null.
   */
  private Writer fileNameToOutputWriter2(String fileName) throws IOException {
    if (fileName == null) {
      return null;
    }
    if (testMode) {
      return new StringWriter();
    }

    return streamToOutputWriter2(filenameToOutputStream(fileName));
  }

  /**
   * Converts a file name into a Outputstream.
   * Returns null if the file name is null.
   */
  protected OutputStream filenameToOutputStream(String fileName)
      throws IOException {
    if (fileName == null){
      return null;
    }
    return new FileOutputStream(fileName);
  }

  /**
   * Create a writer with the legacy output charset.
   */
  private Writer streamToLegacyOutputWriter(OutputStream stream)
      throws IOException {
    if (legacyOutputCharset == null) {
      return new BufferedWriter(new OutputStreamWriter(stream, UTF_8));
    } else {
      return new BufferedWriter(
          new OutputStreamWriter(stream, legacyOutputCharset));
    }
  }

  /**
   * Create a writer with the newer output charset.
   */
  private Writer streamToOutputWriter2(OutputStream stream) {
    if (outputCharset2 == null) {
      return new BufferedWriter(new OutputStreamWriter(stream, UTF_8));
    } else {
      return new BufferedWriter(
          new OutputStreamWriter(stream, outputCharset2));
    }
  }

  /**
   * Outputs the source map found in the compiler to the proper path if one
   * exists.
   *
   * @param options The options to the Compiler.
   */
  private void outputSourceMap(B options, String associatedName)
      throws IOException {
    if (Strings.isNullOrEmpty(options.sourceMapOutputPath)) {
      return;
    }

    String outName = expandSourceMapPath(options, null);
    maybeCreateDirsForPath(outName);
    try (Writer out = fileNameToOutputWriter2(outName)) {
      compiler.getSourceMap().appendTo(out, associatedName);
    }
  }

  /**
   * Returns the path at which to output map file(s) based on the path at which
   * the JS binary will be placed.
   *
   * @return The path in which to place the generated map file(s).
   */
  private String getMapPath(String outputFile) {
    String basePath = "";

    if (outputFile.isEmpty()) {
      // If we have a js_module_binary rule, output the maps
      // at modulename_props_map.out, etc.
      if (!config.moduleOutputPathPrefix.isEmpty()) {
        basePath = config.moduleOutputPathPrefix;
      } else {
        basePath = "jscompiler";
      }
    } else {
      // Get the path of the output file.
      File file = new File(outputFile);

      String outputFileName = file.getName();

      // Strip the .js from the name.
      if (outputFileName.endsWith(".js")) {
        outputFileName =
            outputFileName.substring(0, outputFileName.length() - 3);
      }

      String fileParent = file.getParent();
      if (fileParent == null) {
        basePath = outputFileName;
      } else {
        basePath = file.getParent() + File.separatorChar + outputFileName;
      }
    }

    return basePath;
  }

  /**
   * Outputs the variable and property name maps for the specified compiler if
   * the proper FLAGS are set.
   */
  private void outputNameMaps() throws FlagUsageException,
      IOException {

    String propertyMapOutputPath = null;
    String variableMapOutputPath = null;
    String functionInformationMapOutputPath = null;

    // Check the create_name_map_files FLAG.
    if (config.createNameMapFiles) {
      String basePath = getMapPath(config.jsOutputFile);

      propertyMapOutputPath = basePath + "_props_map.out";
      variableMapOutputPath = basePath + "_vars_map.out";
      functionInformationMapOutputPath = basePath + "_functions_map.out";
    }

    // Check the individual FLAGS.
    if (!config.variableMapOutputFile.isEmpty()) {
      if (variableMapOutputPath != null) {
        throw new FlagUsageException("The flags variable_map_output_file and "
            + "create_name_map_files cannot both be used simultaniously.");
      }

      variableMapOutputPath = config.variableMapOutputFile;
    }

    if (!config.propertyMapOutputFile.isEmpty()) {
      if (propertyMapOutputPath != null) {
        throw new FlagUsageException("The flags property_map_output_file and "
            + "create_name_map_files cannot both be used simultaniously.");
      }

      propertyMapOutputPath = config.propertyMapOutputFile;
    }

    // Output the maps.
    if (variableMapOutputPath != null && compiler.getVariableMap() != null) {
      compiler.getVariableMap().save(variableMapOutputPath);
    }

    if (propertyMapOutputPath != null && compiler.getPropertyMap() != null) {
      compiler.getPropertyMap().save(propertyMapOutputPath);
    }

    if (functionInformationMapOutputPath != null
        && compiler.getFunctionalInformationMap() != null) {
      try (final OutputStream file = filenameToOutputStream(functionInformationMapOutputPath)) {
        CodedOutputStream outputStream = CodedOutputStream.newInstance(file);
        compiler.getFunctionalInformationMap().writeTo(outputStream);
        outputStream.flush();
      }
    }
  }

  /**
   * Create a map of constant names to constant values from a textual
   * description of the map.
   *
   * @param definitions A list of overriding definitions for defines in
   *     the form <name>[=<val>], where <val> is a number, boolean, or
   *     single-quoted string without single quotes.
   */
  @VisibleForTesting
  static void createDefineOrTweakReplacements(List<String> definitions,
      CompilerOptions options, boolean tweaks) {
    // Parse the definitions
    for (String override : definitions) {
      String[] assignment = override.split("=", 2);
      String defName = assignment[0];

      if (defName.length() > 0) {
        String defValue = assignment.length == 1 ? "true" : assignment[1];

        boolean isTrue = defValue.equals("true");
        boolean isFalse = defValue.equals("false");
        if (isTrue || isFalse) {
          if (tweaks) {
            options.setTweakToBooleanLiteral(defName, isTrue);
          } else {
            options.setDefineToBooleanLiteral(defName, isTrue);
          }
          continue;
        } else if (defValue.length() > 1
            && ((defValue.charAt(0) == '\'' &&
                defValue.charAt(defValue.length() - 1) == '\'')
                || (defValue.charAt(0) == '\"' &&
                    defValue.charAt(defValue.length() - 1) == '\"'))) {
          // If the value starts and ends with a single quote,
          // we assume that it's a string.
          String maybeStringVal =
              defValue.substring(1, defValue.length() - 1);
          if (maybeStringVal.indexOf(defValue.charAt(0)) == -1) {
            if (tweaks) {
              options.setTweakToStringLiteral(defName, maybeStringVal);
            } else {
              options.setDefineToStringLiteral(defName, maybeStringVal);
            }
            continue;
          }
        } else {
          try {
            double value = Double.parseDouble(defValue);
            if (tweaks) {
              options.setTweakToDoubleLiteral(defName, value);
            } else {
              options.setDefineToDoubleLiteral(defName, value);
            }
            continue;
          } catch (NumberFormatException e) {
            // do nothing, it will be caught at the end
          }
        }
      }

      if (tweaks) {
        throw new RuntimeException(
            "--tweak flag syntax invalid: " + override);
      }
      throw new RuntimeException(
          "--define flag syntax invalid: " + override);
    }
  }

  /**
   * Returns true if and only if a manifest or bundle should be generated
   * for each module, as opposed to one unified manifest.
   */
  private boolean shouldGenerateOutputPerModule(String output) {
    return !config.module.isEmpty()
        && output != null && output.contains("%outname%");
  }

  private void outputManifest() throws IOException {
    outputManifestOrBundle(config.outputManifests, true);
  }

  private void outputBundle() throws IOException {
    outputManifestOrBundle(config.outputBundles, false);
  }

  /**
   * Writes the manifest or bundle of all compiler input files that survived
   * manage_closure_dependencies, if requested.
   */
  private void outputManifestOrBundle(List<String> outputFiles,
      boolean isManifest) throws IOException {
    if (outputFiles.isEmpty()) {
      return;
    }

    for (String output : outputFiles) {
      if (output.isEmpty()) {
        continue;
      }

      if (shouldGenerateOutputPerModule(output)) {
        // Generate per-module manifests or bundles
        JSModuleGraph graph = compiler.getDegenerateModuleGraph();
        Iterable<JSModule> modules = graph.getAllModules();
        for (JSModule module : modules) {
          try (Writer out = fileNameToOutputWriter2(expandCommandLinePath(output, module))) {
            if (isManifest) {
              printManifestTo(module.getInputs(), out);
            } else {
              printBundleTo(module.getInputs(), out);
            }
          }
        }
      } else {
        // Generate a single file manifest or bundle.
        try (Writer out = fileNameToOutputWriter2(expandCommandLinePath(output, null))) {
          if (config.module.isEmpty()) {
            if (isManifest) {
              printManifestTo(compiler.getInputsInOrder(), out);
            } else {
              printBundleTo(compiler.getInputsInOrder(), out);
            }
          } else {
            printModuleGraphManifestOrBundleTo(
                compiler.getDegenerateModuleGraph(), out, isManifest);
          }
        }
      }
    }
  }

  /**
   * Creates a file containing the current module graph in JSON serialization.
   */
  private void outputModuleGraphJson() throws IOException {
    if (config.outputModuleDependencies != null &&
        config.outputModuleDependencies.length() != 0) {
      try (Writer out = fileNameToOutputWriter2(config.outputModuleDependencies)) {
        printModuleGraphJsonTo(out);
      }
    }
  }

  /**
   * Prints the current module graph as JSON.
   */
  @VisibleForTesting
  void printModuleGraphJsonTo(Appendable out) throws IOException {
    out.append(compiler.getDegenerateModuleGraph().toJson().toString());
  }

  /**
   * Prints a set of modules to the manifest or bundle file.
   */
  @VisibleForTesting
  void printModuleGraphManifestOrBundleTo(JSModuleGraph graph,
      Appendable out, boolean isManifest) throws IOException {
    Joiner commas = Joiner.on(",");
    boolean requiresNewline = false;
    for (JSModule module : graph.getAllModules()) {
      if (requiresNewline) {
        out.append("\n");
      }

      if (isManifest) {
        // See CommandLineRunnerTest to see what the format of this
        // manifest looks like.
        String dependencies = commas.join(module.getSortedDependencyNames());
        out.append(
            String.format("{%s%s}\n",
                module.getName(),
                dependencies.isEmpty() ? "" : ":" + dependencies));
        printManifestTo(module.getInputs(), out);
      } else {
        printBundleTo(module.getInputs(), out);
      }
      requiresNewline = true;
    }
  }

  /**
   * Prints a list of input names (using root-relative paths), delimited by
   * newlines, to the manifest file.
   */
  private void printManifestTo(Iterable<CompilerInput> inputs, Appendable out)
      throws IOException {
    for (CompilerInput input : inputs) {
      String rootRelativePath = rootRelativePathsMap.get(input.getName());
      String displayName = rootRelativePath != null
          ? rootRelativePath
          : input.getName();
      out.append(displayName);
      out.append("\n");
    }
  }

  /**
   * Prints all the input contents, starting with a comment that specifies
   * the input file name (using root-relative paths) before each file.
   */
  @VisibleForTesting
  void printBundleTo(Iterable<CompilerInput> inputs, Appendable out)
      throws IOException {
    ClosureBundler bundler = new ClosureBundler();

    for (CompilerInput input : inputs) {
      // Every module has an empty file in it. This makes it easier to implement
      // cross-module code motion.
      //
      // But it also leads to a weird edge case because
      // a) If we don't have a module spec, we create a singleton module, and
      // b) If we print a bundle file, we copy the original input files.
      //
      // This means that in the (rare) case where we have no inputs, and no
      // module spec, and we're printing a bundle file, we'll have a fake
      // input file that shouldn't be copied. So we special-case this, to
      // make all the other cases simpler.
      if (input.getName().equals(
              Compiler.createFillFileName(Compiler.SINGLETON_MODULE_NAME))) {
        Preconditions.checkState(1 == Iterables.size(inputs));
        return;
      }

      String rootRelativePath = rootRelativePathsMap.get(input.getName());
      String displayName = rootRelativePath != null
          ? rootRelativePath
          : input.getName();
      out.append("//");
      out.append(displayName);
      out.append("\n");

      bundler.appendTo(out, input, input.getSourceFile().getCodeCharSource());

      out.append("\n");
    }
  }

  /**
   * Construct and return the input root path map. The key is the exec path of
   * each input file, and the value is the corresponding root relative path.
   */
  private Map<String, String> constructRootRelativePathsMap() {
    Map<String, String> rootRelativePathsMap = new LinkedHashMap<>();
    for (String mapString : config.manifestMaps) {
      int colonIndex = mapString.indexOf(':');
      Preconditions.checkState(colonIndex > 0);
      String execPath = mapString.substring(0, colonIndex);
      String rootRelativePath = mapString.substring(colonIndex + 1);
      Preconditions.checkState(rootRelativePath.indexOf(':') == -1);
      rootRelativePathsMap.put(execPath, rootRelativePath);
    }
    return rootRelativePathsMap;
  }

  /**
   * Configurations for the command line configs. Designed for easy
   * building, so that we can decouple the flags-parsing library from
   * the actual configuration options.
   *
   * By design, these configurations must match one-to-one with
   * command-line flags.
   */
  static class CommandLineConfig {
    private boolean printTree = false;

    /** Prints out the parse tree and exits */
    CommandLineConfig setPrintTree(boolean printTree) {
      this.printTree = printTree;
      return this;
    }

    private boolean printAst = false;

    /**
     * Prints a dot file describing the internal abstract syntax tree
     * and exits
     */
    CommandLineConfig setPrintAst(boolean printAst) {
      this.printAst = printAst;
      return this;
    }

    private boolean printPassGraph = false;

    /** Prints a dot file describing the passes that will get run and exits */
    CommandLineConfig setPrintPassGraph(boolean printPassGraph) {
      this.printPassGraph = printPassGraph;
      return this;
    }

    private CompilerOptions.DevMode jscompDevMode = CompilerOptions.DevMode.OFF;

    /** Turns on extra sanity checks */
    CommandLineConfig setJscompDevMode(CompilerOptions.DevMode jscompDevMode) {
      this.jscompDevMode = jscompDevMode;
      return this;
    }

    private String loggingLevel = Level.WARNING.getName();

    /**
     * The logging level (standard java.util.logging.Level
     * values) for Compiler progress. Does not control errors or
     * warnings for the JavaScript code under compilation
     */
    CommandLineConfig setLoggingLevel(String loggingLevel) {
      this.loggingLevel = loggingLevel;
      return this;
    }

    private final List<String> externs = new ArrayList<>();

    /**
     * The file containing JavaScript externs. You may specify multiple.
     */
    CommandLineConfig setExterns(List<String> externs) {
      this.externs.clear();
      this.externs.addAll(externs);
      return this;
    }

    private final List<String> js = new ArrayList<>();

    /**
     * The JavaScript filename. You may specify multiple.
     */
    CommandLineConfig setJs(List<String> js) {
      this.js.clear();
      this.js.addAll(js);
      return this;
    }

    private final List<String> jsZip = new ArrayList<>();

    /**
     * The JavaScript zip filename. You may specify multiple.
     */
    CommandLineConfig setJsZip(List<String> zip) {
      this.jsZip.clear();
      this.jsZip.addAll(zip);
      return this;
    }

    private String jsOutputFile = "";

    /**
     * Primary output filename. If not specified, output is written to stdout
     */
    CommandLineConfig setJsOutputFile(String jsOutputFile) {
      this.jsOutputFile = jsOutputFile;
      return this;
    }

    private final List<String> module = new ArrayList<>();

    /**
     * A JavaScript module specification. The format is
     * <name>:<num-js-files>[:[<dep>,...][:]]]. Module names must be
     * unique. Each dep is the name of a module that this module
     * depends on. Modules must be listed in dependency order, and JS
     * source files must be listed in the corresponding order. Where
     * --module flags occur in relation to --js flags is unimportant
     */
    CommandLineConfig setModule(List<String> module) {
      this.module.clear();
      this.module.addAll(module);
      return this;
    }

    private Map<String, String> sourceMapInputFiles = new HashMap<>();

    CommandLineConfig setSourceMapInputFiles(
        Map<String, String> sourceMapInputFiles) {
      this.sourceMapInputFiles = sourceMapInputFiles;
      return this;
    }

    private String variableMapInputFile = "";

    /**
     * File containing the serialized version of the variable renaming
     * map produced by a previous compilation
     */
    CommandLineConfig setVariableMapInputFile(String variableMapInputFile) {
      this.variableMapInputFile = variableMapInputFile;
      return this;
    }

    private String propertyMapInputFile = "";

    /**
     * File containing the serialized version of the property renaming
     * map produced by a previous compilation
     */
    CommandLineConfig setPropertyMapInputFile(String propertyMapInputFile) {
      this.propertyMapInputFile = propertyMapInputFile;
      return this;
    }

    private String variableMapOutputFile = "";

    /**
     * File where the serialized version of the variable renaming map
     * produced should be saved
     */
    CommandLineConfig setVariableMapOutputFile(String variableMapOutputFile) {
      this.variableMapOutputFile = variableMapOutputFile;
      return this;
    }

    private boolean createNameMapFiles = false;

    /**
     * If true, variable renaming and property renaming map
     * files will be produced as {binary name}_vars_map.out and
     * {binary name}_props_map.out. Note that this flag cannot be used
     * in conjunction with either variable_map_output_file or
     * property_map_output_file
     */
    CommandLineConfig setCreateNameMapFiles(boolean createNameMapFiles) {
      this.createNameMapFiles = createNameMapFiles;
      return this;
    }

    private String propertyMapOutputFile = "";

    /**
     * File where the serialized version of the property renaming map
     * produced should be saved
     */
    CommandLineConfig setPropertyMapOutputFile(String propertyMapOutputFile) {
      this.propertyMapOutputFile = propertyMapOutputFile;
      return this;
    }

    private CodingConvention codingConvention = CodingConventions.getDefault();

    /**
     * Sets rules and conventions to enforce.
     */
    CommandLineConfig setCodingConvention(CodingConvention codingConvention) {
      this.codingConvention = codingConvention;
      return this;
    }

    private int summaryDetailLevel = 1;

    /**
     * Controls how detailed the compilation summary is. Values:
     *  0 (never print summary), 1 (print summary only if there are
     * errors or warnings), 2 (print summary if type checking is on,
     * see --check_types), 3 (always print summary). The default level
     * is 1
     */
    CommandLineConfig setSummaryDetailLevel(int summaryDetailLevel) {
      this.summaryDetailLevel = summaryDetailLevel;
      return this;
    }

    private String outputWrapper = "";

    /**
     * Interpolate output into this string at the place denoted
     * by the marker token %output%, or %output|jsstring%
     */
    CommandLineConfig setOutputWrapper(String outputWrapper) {
      this.outputWrapper = outputWrapper;
      return this;
    }

    private final List<String> moduleWrapper = new ArrayList<>();

    /**
     * An output wrapper for a JavaScript module (optional). See the flag
     * description for formatting requirements.
     */
    CommandLineConfig setModuleWrapper(List<String> moduleWrapper) {
      this.moduleWrapper.clear();
      this.moduleWrapper.addAll(moduleWrapper);
      return this;
    }

    private String moduleOutputPathPrefix = "";

    /**
     * Prefix for filenames of compiled JS modules.
     * <module-name>.js will be appended to this prefix. Directories
     * will be created as needed. Use with --module
     */
    CommandLineConfig setModuleOutputPathPrefix(String moduleOutputPathPrefix) {
      this.moduleOutputPathPrefix = moduleOutputPathPrefix;
      return this;
    }

    private String createSourceMap = "";

    /**
     * If specified, a source map file mapping the generated
     * source files back to the original source file will be
     * output to the specified path. The %outname% placeholder will
     * expand to the name of the output file that the source map
     * corresponds to.
     */
    CommandLineConfig setCreateSourceMap(String createSourceMap) {
      this.createSourceMap = createSourceMap;
      return this;
    }

    private SourceMap.DetailLevel sourceMapDetailLevel =
        SourceMap.DetailLevel.ALL;

    /**
     * The detail supplied in the source map file, if generated.
     */
    CommandLineConfig setSourceMapDetailLevel(SourceMap.DetailLevel level) {
      this.sourceMapDetailLevel = level;
      return this;
    }

    private SourceMap.Format sourceMapFormat =
      SourceMap.Format.DEFAULT;

    /**
     * The source map format to use, if generated.
     */
    CommandLineConfig setSourceMapFormat(SourceMap.Format format) {
      this.sourceMapFormat = format;
      return this;
    }

    private ImmutableList<SourceMap.LocationMapping> sourceMapLocationMappings =
      ImmutableList.of();

    /**
     * The source map location mappings to use, if generated.
     */
    CommandLineConfig setSourceMapLocationMappings(
        List<SourceMap.LocationMapping> locationMappings) {

      this.sourceMapLocationMappings = ImmutableList.copyOf(locationMappings);
      return this;
    }

    private WarningGuardSpec warningGuards = null;

    /**
     * Add warning guards.
     */
    CommandLineConfig setWarningGuardSpec(WarningGuardSpec spec) {
      this.warningGuards = spec;
      return this;
    }

    private final List<String> define = new ArrayList<>();

    /**
     * Override the value of a variable annotated @define.
     * The format is <name>[=<val>], where <name> is the name of a @define
     * variable and <val> is a boolean, number, or a single-quoted string
     * that contains no single quotes. If [=<val>] is omitted,
     * the variable is marked true
     */
    CommandLineConfig setDefine(List<String> define) {
      this.define.clear();
      this.define.addAll(define);
      return this;
    }

    private final List<String> tweak = new ArrayList<>();

    /**
     * Override the default value of a registered tweak. The format is
     * <name>[=<val>], where <name> is the ID of a tweak and <val> is a boolean,
     * number, or a single-quoted string that contains no single quotes. If
     * [=<val>] is omitted, then true is assumed.
     */
    CommandLineConfig setTweak(List<String> tweak) {
      this.tweak.clear();
      this.tweak.addAll(tweak);
      return this;
    }

    private TweakProcessing tweakProcessing = TweakProcessing.OFF;

    /**
     * Sets the kind of processing to do for goog.tweak functions.
     */
    CommandLineConfig setTweakProcessing(TweakProcessing tweakProcessing) {
      this.tweakProcessing = tweakProcessing;
      return this;
    }

    private String charset = "";

    /**
     * Input charset for all files.
     */
    CommandLineConfig setCharset(String charset) {
      this.charset = charset;
      return this;
    }

    private boolean manageClosureDependencies = false;

    /**
     * Sets whether to sort files by their goog.provide/require deps,
     * and prune inputs that are not required.
     */
    CommandLineConfig setManageClosureDependencies(boolean newVal) {
      this.manageClosureDependencies = newVal;
      return this;
    }

    private boolean onlyClosureDependencies = false;

    /**
     * Sets whether to sort files by their goog.provide/require deps,
     * and prune inputs that are not required, and drop all non-closure
     * files.
     */
    CommandLineConfig setOnlyClosureDependencies(boolean newVal) {
      this.onlyClosureDependencies = newVal;
      return this;
    }

    private List<String> closureEntryPoints = ImmutableList.of();

    /**
     * Set closure entry points, which makes the compiler only include
     * those files and sort them in dependency order.
     */
    CommandLineConfig setClosureEntryPoints(List<String> entryPoints) {
      Preconditions.checkNotNull(entryPoints);
      this.closureEntryPoints = entryPoints;
      return this;
    }

    private List<String> outputManifests = ImmutableList.of();

    /**
     * Sets whether to print output manifest files.
     * Filter out empty file names.
     */
    CommandLineConfig setOutputManifest(List<String> outputManifests) {
      this.outputManifests = new ArrayList<>();
      for (String manifestName : outputManifests) {
        if (!manifestName.isEmpty()) {
          this.outputManifests.add(manifestName);
        }
      }
      this.outputManifests = ImmutableList.copyOf(this.outputManifests);
      return this;
    }

    private String outputModuleDependencies = null;

    /**
     * Sets whether a JSON file representing the dependencies between modules
     * should be created.
     */
    CommandLineConfig setOutputModuleDependencies(String
        outputModuleDependencies) {
      this.outputModuleDependencies = outputModuleDependencies;
      return this;
    }

    private List<String> outputBundles = ImmutableList.of();

    /**
     * Sets whether to print output bundle files.
     */
    CommandLineConfig setOutputBundle(List<String> outputBundles) {
      this.outputBundles = outputBundles;
      return this;
    }

    private String languageIn = "";
    private String languageOut = "";

    CommandLineConfig setLanguageIn(String languageIn) {
      this.languageIn = languageIn;
      return this;
    }

    CommandLineConfig setLanguageOut(String languageOut) {
      this.languageOut = languageOut;
      return this;
    }

    private boolean skipNormalOutputs = false;

    /**
     * Sets whether the normal outputs of compilation should be skipped.
     */
    CommandLineConfig setSkipNormalOutputs(boolean skipNormalOutputs) {
      this.skipNormalOutputs = skipNormalOutputs;
      return this;
    }

    private List<String> manifestMaps = ImmutableList.of();

    /**
     * Sets the execPath:rootRelativePath mappings
     */
    CommandLineConfig setManifestMaps(List<String> manifestMaps) {
      this.manifestMaps = manifestMaps;
      return this;
    }


    private boolean transformAMDToCJSModules = false;

    /**
     * Set whether to transform AMD to CommonJS modules.
     */
    CommandLineConfig setTransformAMDToCJSModules(
        boolean transformAMDToCJSModules) {
      this.transformAMDToCJSModules = transformAMDToCJSModules;
      return this;
    }

    private boolean processCommonJSModules = false;

    /**
     * Sets whether to process CommonJS modules.
     */
    CommandLineConfig setProcessCommonJSModules(
        boolean processCommonJSModules) {
      this.processCommonJSModules = processCommonJSModules;
      return this;
    }

    private List<String> moduleRoots = ImmutableList.of(ES6ModuleLoader.DEFAULT_FILENAME_PREFIX);

    /**
     * Sets the module roots.
     */
    CommandLineConfig setModuleRoots(List<String> jsModuleRoots) {
      this.moduleRoots = jsModuleRoots;
      return this;
    }

    private String warningsWhitelistFile = "";

    /**
     * Sets a whitelist file that suppresses warnings.
     */
    CommandLineConfig setWarningsWhitelistFile(String fileName) {
      this.warningsWhitelistFile = fileName;
      return this;
    }

    private List<String> hideWarningsFor = ImmutableList.of();

    /**
     * Sets the paths for which warnings will be hidden.
     */
    CommandLineConfig setHideWarningsFor(List<String> hideWarningsFor) {
      this.hideWarningsFor = hideWarningsFor;
      return this;
    }

    private boolean angularPass = false;

    /**
     * Sets whether to process AngularJS-specific annotations.
     */
    CommandLineConfig setAngularPass(boolean angularPass) {
      this.angularPass = angularPass;
      return this;
    }

    private CompilerOptions.TracerMode tracerMode =
        CompilerOptions.TracerMode.OFF;

    CommandLineConfig setTracerMode(CompilerOptions.TracerMode tracerMode) {
      this.tracerMode = tracerMode;
      return this;
    }

    private boolean useNewTypeInference = false;

    CommandLineConfig setNewTypeInference(boolean useNewTypeInference) {
      this.useNewTypeInference = useNewTypeInference;
      return this;
    }

    private String instrumentationTemplateFile = "";

    CommandLineConfig setInstrumentationTemplateFile(String fileName) {
        this.instrumentationTemplateFile = fileName;
        return this;
    }

    private JsonStreamMode jsonStreamMode = JsonStreamMode.NONE;

    CommandLineConfig setJsonStreamMode(JsonStreamMode mode) {
      this.jsonStreamMode = mode;
      return this;
    }

  }

  /**
   * A little helper class to make it easier to collect warning types
   * from --jscomp_error, --jscomp_warning, and --jscomp_off.
   */
  protected static class WarningGuardSpec {
    private static class Entry {
      private final CheckLevel level;
      private final String groupName;

      private Entry(CheckLevel level, String groupName) {
        this.level = level;
        this.groupName = groupName;
      }
    }

    // The entries, in the order that they were added.
    private final List<Entry> entries = new ArrayList<>();

    protected void add(CheckLevel level, String groupName) {
      entries.add(new Entry(level, groupName));
    }

    protected void clear() {
      entries.clear();
    }
  }

  /**
   * Representation of a source file from an encoded json stream input
   */
  private class JsonFileSpec {
    private final String src;
    private final String path;
    private String source_map;

    public JsonFileSpec(String src, String path) {
      this(src, path, null);
    }

    public JsonFileSpec(String src, String path, String source_map) {
      this.src = src;
      this.path = path;
      this.source_map = source_map;
    }

    public String getSrc() {
      return this.src;
    }

    public String getPath() {
      return this.path;
    }

    public String getSourceMap() { return this.source_map; }

    public void setSourceMap(String map) {
      this.source_map = map;
    }
  }
}
