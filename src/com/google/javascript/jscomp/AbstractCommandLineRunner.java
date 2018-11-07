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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.google.javascript.jscomp.CompilerOptions.JsonStreamMode;
import com.google.javascript.jscomp.CompilerOptions.OutputJs;
import com.google.javascript.jscomp.CompilerOptions.TweakProcessing;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.deps.SourceCodeEscapers;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile.SourceKind;
import com.google.javascript.rhino.TokenStream;
import com.google.protobuf.CodedOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
 *     AbstractCommandLineRunner&lt;MyCompiler, MyOptions&gt; {
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
public abstract class AbstractCommandLineRunner<A extends Compiler,
    B extends CompilerOptions> {

  static final DiagnosticType OUTPUT_SAME_AS_INPUT_ERROR = DiagnosticType.error(
      "JSC_OUTPUT_SAME_AS_INPUT_ERROR",
      "Bad output file (already listed as input file): {0}");

  static final DiagnosticType COULD_NOT_SERIALIZE_AST = DiagnosticType.error(
      "JSC_COULD_NOT_SERIALIZE_AST",
      "Could not serialize ast to: {0}");

  static final DiagnosticType COULD_NOT_DESERIALIZE_AST = DiagnosticType.error(
      "JSC_COULD_NOT_DESERIALIZE_AST",
      "Could not deserialize ast from: {0}");

  static final DiagnosticType NO_TREE_GENERATED_ERROR = DiagnosticType.error(
      "JSC_NO_TREE_GENERATED_ERROR",
      "Code contains errors. No tree was generated.");
  static final DiagnosticType INVALID_MODULE_SOURCEMAP_PATTERN =
      DiagnosticType.error(
          "JSC_INVALID_MODULE_SOURCEMAP_PATTERN",
          "When using --module flags, the --create_source_map flag must contain "
              + "%outname% in the value.");

  static final DiagnosticType CONFLICTING_DUPLICATE_ZIP_CONTENTS = DiagnosticType.error(
      "JSC_CONFLICTING_DUPLICATE_ZIP_CONTENTS",
      "Two zip entries containing conflicting contents with the same relative path.\n"
      + "Entry 1: {0}\n"
      + "Entry 2: {1}");

  static final String WAITING_FOR_INPUT_WARNING =
      "The compiler is waiting for input via stdin.";

  @GwtIncompatible("Unnecessary")
  private final CommandLineConfig config;

  @GwtIncompatible("Unnecessary")
  private final InputStream in;

  @GwtIncompatible("Unnecessary")
  private final PrintStream defaultJsOutput;

  @GwtIncompatible("Unnecessary")
  private final PrintStream err;

  @GwtIncompatible("Unnecessary")
  private A compiler;

  @GwtIncompatible("Unnecessary")
  private Charset inputCharset;

  // NOTE(nicksantos): JSCompiler has always used ASCII as the default
  // output charset. This was done to solve legacy problems with
  // bad proxies, etc. We are not sure if these issues are still problems,
  // and changing the encoding would require a big obnoxious migration plan.
  //
  // New outputs should use outputCharset2, which is how I would have
  // designed this if I had a time machine.
  @GwtIncompatible("Unnecessary")
  private Charset outputCharset2;

  @GwtIncompatible("Unnecessary")
  private Charset legacyOutputCharset;

  @GwtIncompatible("Unnecessary")
  private boolean testMode = false;

  @GwtIncompatible("Unnecessary")
  private Supplier<List<SourceFile>> externsSupplierForTesting = null;

  @GwtIncompatible("Unnecessary")
  private Supplier<List<SourceFile>> inputsSupplierForTesting = null;

  @GwtIncompatible("Unnecessary")
  private Supplier<List<JSModule>> modulesSupplierForTesting = null;

  @GwtIncompatible("Unnecessary")
  private Function<Integer, Void> exitCodeReceiver = SystemExitCodeReceiver.INSTANCE;

  @GwtIncompatible("Unnecessary")
  private Map<String, String> rootRelativePathsMap = null;

  @GwtIncompatible("Unnecessary")
  private Map<String, String> parsedModuleWrappers = null;

  @GwtIncompatible("Unnecessary")
  private final Gson gson;

  static final String OUTPUT_MARKER = "%output%";
  private static final String OUTPUT_MARKER_JS_STRING = "%output|jsstring%";

  @GwtIncompatible("Unnecessary")
  private final List<JsonFileSpec> filesToStreamOut = new ArrayList<>();

  @GwtIncompatible("Unnecessary")
  AbstractCommandLineRunner() {
    this(System.in, System.out, System.err);
  }

  @GwtIncompatible("Unnecessary")
  AbstractCommandLineRunner(PrintStream out, PrintStream err) {
    this(System.in, out, err);
  }

  @GwtIncompatible("Unnecessary")
  AbstractCommandLineRunner(InputStream in, PrintStream out, PrintStream err) {
    this.config = new CommandLineConfig();
    this.in = checkNotNull(in);
    this.defaultJsOutput = checkNotNull(out);
    this.err = checkNotNull(err);
    this.gson = new Gson();
  }

  /**
   * Put the command line runner into test mode. In test mode, all outputs will be blackholed.
   *
   * @param externsSupplier A provider for externs.
   * @param inputsSupplier A provider for source inputs.
   * @param modulesSupplier A provider for modules. Only one of inputsSupplier and modulesSupplier
   *     may be non-null.
   * @param exitCodeReceiver A receiver for the status code that would have been passed to
   *     System.exit in non-test mode.
   */
  @VisibleForTesting
  @GwtIncompatible("Unnecessary")
  void enableTestMode(
      Supplier<List<SourceFile>> externsSupplier,
      Supplier<List<SourceFile>> inputsSupplier,
      Supplier<List<JSModule>> modulesSupplier,
      Function<Integer, Void> exitCodeReceiver) {
    checkArgument(inputsSupplier == null ^ modulesSupplier == null);
    testMode = true;
    this.externsSupplierForTesting = externsSupplier;
    this.inputsSupplierForTesting = inputsSupplier;
    this.modulesSupplierForTesting = modulesSupplier;
    this.exitCodeReceiver = exitCodeReceiver;
  }

  /**
   * @param newExitCodeReceiver receives a non-zero integer to indicate a problem during execution
   *     or 0i to indicate success.
   */
  @GwtIncompatible("Unnecessary")
  public void setExitCodeReceiver(Function<Integer, Void> newExitCodeReceiver) {
    this.exitCodeReceiver = checkNotNull(newExitCodeReceiver);
  }

  /** Returns whether we're in test mode. */
  @GwtIncompatible("Unnecessary")
  protected boolean isInTestMode() {
    return testMode;
  }

  /** Returns whether output should be a JSON stream. */
  @GwtIncompatible("Unnecessary")
  private boolean isOutputInJson() {
    return config.jsonStreamMode == JsonStreamMode.OUT
        || config.jsonStreamMode == JsonStreamMode.BOTH;
  }

  /** Get the command line config, so that it can be initialized. */
  @GwtIncompatible("Unnecessary")
  protected CommandLineConfig getCommandLineConfig() {
    return config;
  }

  /** Returns the instance of the Compiler to use when {@link #run()} is called. */
  @GwtIncompatible("Unnecessary")
  protected abstract A createCompiler();

  /**
   * Performs any transformation needed on the given compiler input and appends it to the given
   * output bundle.
   */
  @GwtIncompatible("Unnecessary")
  protected abstract void prepForBundleAndAppendTo(
      Appendable out, CompilerInput input, String content) throws IOException;

  /** Writes whatever runtime libraries are needed to bundle. */
  @GwtIncompatible("Unnecessary")
  protected abstract void appendRuntimeTo(Appendable out) throws IOException;

  /**
   * Returns the instance of the Options to use when {@link #run()} is called. createCompiler() is
   * called before createOptions(), so getCompiler() will not return null when createOptions() is
   * called.
   */
  @GwtIncompatible("Unnecessary")
  protected abstract B createOptions();

  /** The warning classes that are available from the command-line. */
  @GwtIncompatible("Unnecessary")
  protected DiagnosticGroups getDiagnosticGroups() {
    if (compiler == null) {
      return new DiagnosticGroups();
    }
    return compiler.getDiagnosticGroups();
  }

  @GwtIncompatible("Unnecessary")
  protected abstract void addWhitelistWarningsGuard(CompilerOptions options, File whitelistFile);

  @GwtIncompatible("Unnecessary")
  protected static void setWarningGuardOptions(
      CompilerOptions options,
      ArrayList<FlagEntry<CheckLevel>> warningGuards,
      DiagnosticGroups diagnosticGroups) {
    if (warningGuards != null) {
      for (FlagEntry<CheckLevel> entry : warningGuards) {
        if ("*".equals(entry.value)) {
          Set<String> groupNames = diagnosticGroups.getRegisteredGroups().keySet();
          for (String groupName : groupNames) {
            if (!DiagnosticGroups.wildcardExcludedGroups.contains(groupName)) {
              diagnosticGroups.setWarningLevel(options, groupName, entry.flag);
            }
          }
        } else {
          diagnosticGroups.setWarningLevel(options, entry.value, entry.flag);
        }
      }
    }
  }

  /**
   * Sets options based on the configurations set flags API. Called during the run() run() method.
   * If you want to ignore the flags API, or interpret flags your own way, then you should override
   * this method.
   */
  @GwtIncompatible("Unnecessary")
  protected void setRunOptions(CompilerOptions options) throws IOException {
    DiagnosticGroups diagnosticGroups = getDiagnosticGroups();

    setWarningGuardOptions(options, config.warningGuards, diagnosticGroups);

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

    // TODO(tjgq): Unconditionally set the options.
    if (config.dependencyOptions != null) {
      options.setDependencyOptions(config.dependencyOptions);
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
    options.parseInlineSourceMaps = config.parseInlineSourceMaps;
    options.applyInputSourceMaps = config.applyInputSourceMaps;

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
    options.instrumentationTemplateFile = config.instrumentationTemplateFile;

    if (!config.jsonWarningsFile.isEmpty()) {
      options.addReportGenerator(
          new JsonErrorReportGenerator(new PrintStream(config.jsonWarningsFile), compiler));
    }

    if (config.errorFormat == CommandLineConfig.ErrorFormatOption.JSON) {
      JsonErrorReportGenerator errorGenerator =
          new JsonErrorReportGenerator(getErrorPrintStream(), compiler);
      compiler.setErrorManager(new SortingErrorManager(ImmutableSet.of(errorGenerator)));
    }
  }

  @GwtIncompatible("Unnecessary")
  protected final A getCompiler() {
    return compiler;
  }

  /**
   * @return a mutable list
   * @throws IOException
   */
  @GwtIncompatible("Unnecessary")
  public static List<SourceFile> getBuiltinExterns(CompilerOptions.Environment env)
      throws IOException {
    InputStream input = AbstractCommandLineRunner.class.getResourceAsStream(
        "/externs.zip");
    if (input == null) {
      // In some environments, the externs.zip is relative to this class.
      input = AbstractCommandLineRunner.class.getResourceAsStream("externs.zip");
    }
    checkNotNull(input);

    ZipInputStream zip = new ZipInputStream(input);
    String envPrefix = Ascii.toLowerCase(env.toString()) + "/";
    Map<String, SourceFile> mapFromExternsZip = new HashMap<>();
    for (ZipEntry entry = null; (entry = zip.getNextEntry()) != null; ) {
      String filename = entry.getName();

      // Always load externs in the root folder.
      // If the non-core-JS externs are organized in subfolders, only load
      // the ones in a subfolder matching the specified environment. Strip the subfolder.
      if (filename.contains("/")) {
        if (!filename.startsWith(envPrefix)) {
          continue;
        }
        filename = filename.substring(envPrefix.length());  // remove envPrefix, including '/'
      }

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

    return DefaultExterns.prepareExterns(env, mapFromExternsZip);
 }

  /** Runs the Compiler and calls System.exit() with the exit status of the compiler. */
  @GwtIncompatible("Unnecessary")
  public final void run() {
    int result;
    try {
      result = doRun();
    } catch (FlagUsageException e) {
      err.println(e.getMessage());
      result = -1;
    } catch (Throwable t) {
      t.printStackTrace(err);
      result = -2;
    }

    exitCodeReceiver.apply(result);
  }

  /** Returns the PrintStream for writing errors associated with this AbstractCommandLineRunner. */
  @GwtIncompatible("Unnecessary")
  protected final PrintStream getErrorPrintStream() {
    return err;
  }

  @GwtIncompatible("Unnecessary")
  public List<JsonFileSpec> parseJsonFilesFromInputStream() throws IOException {
    List<JsonFileSpec> jsonFiles = new ArrayList<>();
    try (JsonReader reader = new JsonReader(new InputStreamReader(this.in, inputCharset))) {
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
   * <p>Can be overridden by subclasses who want to pull files from different places.
   *
   * @param files A list of flag entries indicates js and zip file names.
   * @param allowStdIn Whether '-' is allowed appear as a filename to represent stdin. If true, '-'
   *     is only allowed to appear once.
   * @param jsModuleSpecs A list js module specs.
   * @return An array of inputs
   */
  @GwtIncompatible("Unnecessary")
  protected List<SourceFile> createInputs(
      List<FlagEntry<JsSourceType>> files, boolean allowStdIn, List<JsModuleSpec> jsModuleSpecs)
      throws IOException {
    return createInputs(files, null /* jsonFiles */, allowStdIn, jsModuleSpecs);
  }

  /**
   * Creates inputs from a list of source files and json files.
   *
   * <p>Can be overridden by subclasses who want to pull files from different places.
   *
   * @param files A list of flag entries indicates js and zip file names.
   * @param jsonFiles A list of json encoded files.
   * @param jsModuleSpecs A list js module specs.
   * @return An array of inputs
   */
  @GwtIncompatible("Unnecessary")
  protected List<SourceFile> createInputs(
      List<FlagEntry<JsSourceType>> files,
      List<JsonFileSpec> jsonFiles,
      List<JsModuleSpec> jsModuleSpecs)
      throws IOException {
    return createInputs(files, jsonFiles, false, jsModuleSpecs);
  }

  /**
   * Creates inputs from a list of source files, zips and json files.
   *
   * <p>Can be overridden by subclasses who want to pull files from different places.
   *
   * @param files A list of flag entries indicates js and zip file names
   * @param jsonFiles A list of json encoded files.
   * @param allowStdIn Whether '-' is allowed appear as a filename to represent stdin. If true, '-'
   *     is only allowed to appear once.
   * @param jsModuleSpecs A list js module specs.
   * @return An array of inputs
   */
  @GwtIncompatible("Unnecessary")
  protected List<SourceFile> createInputs(
      List<FlagEntry<JsSourceType>> files,
      List<JsonFileSpec> jsonFiles,
      boolean allowStdIn,
      List<JsModuleSpec> jsModuleSpecs)
      throws IOException {
    List<SourceFile> inputs = new ArrayList<>(files.size());
    boolean usingStdin = false;
    int jsModuleIndex = 0;
    JsModuleSpec jsModuleSpec = Iterables.getFirst(jsModuleSpecs, null);
    int cumulatedInputFilesExpected =
        jsModuleSpec == null ? Integer.MAX_VALUE : jsModuleSpec.getNumInputs();
    for (int i = 0; i < files.size(); i++) {
      FlagEntry<JsSourceType> file = files.get(i);
      String filename = file.value;
      if (file.flag == JsSourceType.JS_ZIP) {
        if (!"-".equals(filename)) {
          List<SourceFile> newFiles = SourceFile.fromZipFile(filename, inputCharset);
          // Update the manifest maps for new zip entries.
          if (rootRelativePathsMap.containsKey(filename)) {
            String rootFilename = rootRelativePathsMap.get(filename);
            for (SourceFile zipEntry : newFiles) {
              String zipEntryName = zipEntry.getName();
              checkState(zipEntryName.contains(filename));
              String zipmap = zipEntryName.replace(filename, rootFilename);
              rootRelativePathsMap.put(zipEntryName, zipmap);
            }
          }

          inputs.addAll(newFiles);
          if (jsModuleSpec != null) {
            jsModuleSpec.numJsFiles += newFiles.size() - 1;
          }
        }
      } else if (!"-".equals(filename)) {
        SourceKind kind = file.flag == JsSourceType.WEAKDEP ? SourceKind.WEAK : SourceKind.STRONG;
        SourceFile newFile = SourceFile.fromFile(filename, inputCharset, kind);
        inputs.add(newFile);
      } else {
        if (!allowStdIn) {
          throw new FlagUsageException("Can't specify stdin.");
        }
        if (usingStdin) {
          throw new FlagUsageException("Can't specify stdin twice.");
        }

        if (!config.outputManifests.isEmpty()) {
          throw new FlagUsageException(
              "Manifest files cannot be generated when the input is from stdin.");
        }
        if (!config.outputBundles.isEmpty()) {
          throw new FlagUsageException(
              "Bundle files cannot be generated when the input is from stdin.");
        }

        this.err.println(WAITING_FOR_INPUT_WARNING);
        inputs.add(SourceFile.fromInputStream("stdin", this.in, inputCharset));
        usingStdin = true;
      }
      if (i >= cumulatedInputFilesExpected - 1) {
        jsModuleIndex++;
        if (jsModuleIndex < jsModuleSpecs.size()) {
          jsModuleSpec = jsModuleSpecs.get(jsModuleIndex);
          cumulatedInputFilesExpected += jsModuleSpec.getNumInputs();
        }
      }
    }
    if (jsonFiles != null) {
      for (JsonFileSpec jsonFile : jsonFiles) {
        inputs.add(SourceFile.fromCode(jsonFile.getPath(), jsonFile.getSrc()));
      }
    }
    for (JSError error : removeDuplicateZipEntries(inputs, jsModuleSpecs)) {
      compiler.report(error);
    }
    return inputs;
  }

  /**
   * Check that relative paths inside zip files are unique, since multiple files with the same path
   * inside different zips are considered duplicate inputs. Parameter {@code sourceFiles} may be
   * modified if duplicates are removed.
   */
  @GwtIncompatible("Unnecessary")
  public static ImmutableList<JSError> removeDuplicateZipEntries(
      List<SourceFile> sourceFiles, List<JsModuleSpec> jsModuleSpecs) throws IOException {
    ImmutableList.Builder<JSError> errors = ImmutableList.builder();
    Map<String, SourceFile> sourceFilesByName = new HashMap<>();
    Iterator<SourceFile> fileIterator = sourceFiles.iterator();
    int currentFileIndex = 0;
    Iterator<JsModuleSpec> moduleIterator = jsModuleSpecs.iterator();
    // Tracks the total number of js files for current module and all the previous modules.
    int cumulatedJsFileNum = 0;
    JsModuleSpec currentModule = null;
    while (fileIterator.hasNext()) {
      SourceFile sourceFile = fileIterator.next();
      currentFileIndex++;
      // Check whether we reached the next module.
      if (moduleIterator.hasNext() && currentFileIndex > cumulatedJsFileNum) {
        currentModule = moduleIterator.next();
        cumulatedJsFileNum += currentModule.numJsFiles;
      }
      String fullPath = sourceFile.getName();
      if (!fullPath.contains("!/")) {
        // Not a zip file
        continue;
      }
      String relativePath = fullPath.split("!")[1];
      if (!sourceFilesByName.containsKey(relativePath)) {
        sourceFilesByName.put(relativePath, sourceFile);
      } else {
        SourceFile firstSourceFile = sourceFilesByName.get(relativePath);
        if (firstSourceFile.getCode().equals(sourceFile.getCode())) {
          fileIterator.remove();
          if (currentModule != null) {
            currentModule.numJsFiles--;
          }
        } else {
          errors.add(
              JSError.make(
                  CONFLICTING_DUPLICATE_ZIP_CONTENTS,
                  firstSourceFile.getName(),
                  sourceFile.getName()));
        }
      }
    }
    return errors.build();
  }

  /** Creates JS source code inputs from a list of files. */
  @GwtIncompatible("Unnecessary")
  private List<SourceFile> createSourceInputs(
      List<JsModuleSpec> jsModuleSpecs,
      List<FlagEntry<JsSourceType>> files,
      List<JsonFileSpec> jsonFiles)
      throws IOException {
    if (isInTestMode()) {
      return inputsSupplierForTesting != null ? inputsSupplierForTesting.get()
          : null;
    }
    if (files.isEmpty() && jsonFiles == null) {
      // Request to read from stdin.
      files = ImmutableList.of(new FlagEntry<JsSourceType>(JsSourceType.JS, "-"));
    }
    try {
      if (jsonFiles != null) {
        return createInputs(files, jsonFiles, jsModuleSpecs);
      } else {
        return createInputs(files, true, jsModuleSpecs);
      }
    } catch (FlagUsageException e) {
      throw new FlagUsageException("Bad --js flag. " + e.getMessage());
    }
  }

  /** Creates JS extern inputs from a list of files. */
  @GwtIncompatible("Unnecessary")
  private List<SourceFile> createExternInputs(List<String> files) throws IOException {
    List<FlagEntry<JsSourceType>> externFiles = new ArrayList<>();
    for (String file : files) {
      externFiles.add(new FlagEntry<JsSourceType>(JsSourceType.EXTERN, file));
    }
    try {
      return createInputs(externFiles, false, new ArrayList<JsModuleSpec>());
    } catch (FlagUsageException e) {
      throw new FlagUsageException("Bad --externs flag. " + e.getMessage());
    }
  }

  /**
   * Creates module objects from a list of js module specifications.
   *
   * @param specs A list of js module specifications, not null or empty.
   * @param inputs A list of JS file paths, not null
   * @return An array of module objects
   */
  public static List<JSModule> createJsModules(List<JsModuleSpec> specs, List<SourceFile> inputs)
      throws IOException {
    checkState(specs != null);
    checkState(!specs.isEmpty());
    checkState(inputs != null);

    List<String> moduleNames = new ArrayList<>(specs.size());
    Map<String, JSModule> modulesByName = new LinkedHashMap<>();
    Map<String, Integer> modulesFileCountMap = new LinkedHashMap<>();
    int numJsFilesExpected = 0;
    int minJsFilesRequired = 0;
    for (JsModuleSpec spec : specs) {
      if (modulesByName.containsKey(spec.name)) {
        throw new FlagUsageException("Duplicate module name: " + spec.name);
      }
      JSModule module = new JSModule(spec.name);

      for (String dep : spec.deps) {
        JSModule other = modulesByName.get(dep);
        if (other == null) {
          throw new FlagUsageException(
              "Module '"
                  + spec.name
                  + "' depends on unknown module '"
                  + dep
                  + "'. Be sure to list modules in dependency order.");
        }
        module.addDependency(other);
      }

      // We will allow modules of zero input.
      if (spec.numJsFiles < 0) {
        numJsFilesExpected = -1;
      } else {
        minJsFilesRequired += spec.numJsFiles;
      }

      if (numJsFilesExpected >= 0) {
        numJsFilesExpected += spec.numJsFiles;
      }

      // Add modules in reverse order so that source files are allocated to
      // modules in reverse order. This allows the first module
      // (presumably the base module) to have a size of 'auto'
      moduleNames.add(0, spec.name);
      modulesFileCountMap.put(spec.name, spec.numJsFiles);
      modulesByName.put(spec.name, module);
    }

    final int totalNumJsFiles = inputs.size();

    if (numJsFilesExpected >= 0 || minJsFilesRequired > totalNumJsFiles) {
      if (minJsFilesRequired > totalNumJsFiles) {
        numJsFilesExpected = minJsFilesRequired;
      }

      if (numJsFilesExpected > totalNumJsFiles) {
        throw new FlagUsageException(
            "Not enough JS files specified. Expected "
                + numJsFilesExpected
                + " but found "
                + totalNumJsFiles);
      } else if (numJsFilesExpected < totalNumJsFiles) {
        throw new FlagUsageException(
            "Too many JS files specified. Expected "
                + numJsFilesExpected
                + " but found "
                + totalNumJsFiles);
      }
    }

    int numJsFilesLeft = totalNumJsFiles;
    int moduleIndex = 0;
    for (String moduleName : moduleNames) {
      // Parse module inputs.
      int numJsFiles = modulesFileCountMap.get(moduleName);
      JSModule module = modulesByName.get(moduleName);

      // Check if the first js module specified 'auto' for the number of files
      if (moduleIndex == moduleNames.size() - 1 && numJsFiles == -1) {
        numJsFiles = numJsFilesLeft;
      }

      List<SourceFile> moduleFiles = inputs.subList(numJsFilesLeft - numJsFiles, numJsFilesLeft);
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
   *
   * @param name The module name
   */
  @GwtIncompatible("Unnecessary")
  protected void checkModuleName(String name) {
    if (!TokenStream.isJSIdentifier(name)) {
      throw new FlagUsageException("Invalid module name: '" + name + "'");
    }
  }

  /**
   * Parses module wrapper specifications.
   *
   * @param specs A list of module wrapper specifications, not null. The spec format is: <code>
   *     name:wrapper</code>. Wrappers.
   * @param chunks The JS chunks whose wrappers are specified
   * @return A map from module name to module wrapper. Modules with no wrapper will have the empty
   *     string as their value in this map.
   */
  public static Map<String, String> parseModuleWrappers(
      List<String> specs, Iterable<JSModule> chunks) {
    checkState(specs != null);

    Map<String, String> wrappers = new HashMap<>();

    // Prepopulate the map with module names.
    for (JSModule c : chunks) {
      wrappers.put(c.getName(), "");
    }

    for (String spec : specs) {
      // Format is "<name>:<wrapper>".
      int pos = spec.indexOf(':');
      if (pos == -1) {
        throw new FlagUsageException(
            "Expected module wrapper to have " + "<name>:<wrapper> format: " + spec);
      }

      // Parse module name.
      String name = spec.substring(0, pos);
      if (!wrappers.containsKey(name)) {
        throw new FlagUsageException("Unknown module: '" + name + "'");
      }
      String wrapper = spec.substring(pos + 1);
      // Support for %n% and %output%
      wrapper = wrapper.replace("%output%", "%s").replace("%n%", "\n");
      if (!wrapper.contains("%s")) {
        throw new FlagUsageException("No %s placeholder in module wrapper: '" + wrapper + "'");
      }

      wrappers.put(name, wrapper);
    }
    return wrappers;
  }

  @GwtIncompatible("Unnecessary")
  private String getModuleOutputFileName(JSModule m) {
    return config.moduleOutputPathPrefix + m.getName() + ".js";
  }

  @VisibleForTesting
  @GwtIncompatible("Unnecessary")
  void writeModuleOutput(Appendable out, JSModule m) throws IOException {
    if (parsedModuleWrappers == null) {
      parsedModuleWrappers =
          parseModuleWrappers(
              config.moduleWrapper,
              ImmutableList.copyOf(compiler.getModuleGraph().getAllModules()));
    }

    String fileName = getModuleOutputFileName(m);
    String baseName = new File(fileName).getName();
    writeOutput(out, compiler, m,
        parsedModuleWrappers.get(m.getName()).replace("%basename%", baseName),
        "%s", null);
  }

  /**
   * Writes code to an output stream, optionally wrapping it in an arbitrary wrapper that contains a
   * placeholder where the code should be inserted.
   *
   * @param module Which module to write. If this is null, write the entire AST.
   */
  @GwtIncompatible("Unnecessary")
  void writeOutput(
      Appendable out,
      Compiler compiler,
      @Nullable JSModule module,
      String wrapper,
      String codePlaceholder,
      @Nullable Function<String, String> escaper)
      throws IOException {
    if (compiler.getOptions().outputJs == OutputJs.SENTINEL) {
      out.append("// No JS output because the compiler was run in checks-only mode.\n");
      return;
    }
    checkState(compiler.getOptions().outputJs == OutputJs.NORMAL);

    String code = module == null ? compiler.toSource() : compiler.toSource(module);
    writeOutput(out, compiler, code, wrapper, codePlaceholder, escaper);
  }

  /**
   * Writes code to an output stream, optionally wrapping it in an arbitrary wrapper that contains a
   * placeholder where the code should be inserted.
   */
  @GwtIncompatible("Unnecessary")
  void writeOutput(
      Appendable out,
      Compiler compiler,
      String code,
      String wrapper,
      String codePlaceholder,
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

  /** Creates any directories necessary to write a file that will have a given path prefix. */
  @GwtIncompatible("Unnecessary")
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

  @GwtIncompatible("Unnecessary")
  private Appendable createDefaultOutput() throws IOException {
    boolean writeOutputToFile = !config.jsOutputFile.isEmpty();
    if (writeOutputToFile) {
      return fileNameToLegacyOutputWriter(config.jsOutputFile);
    } else {
      return streamToLegacyOutputWriter(defaultJsOutput);
    }
  }

  @GwtIncompatible("Unnecessary")
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
  @GwtIncompatible("Unnecessary")
  protected int doRun() throws IOException {
    Compiler.setLoggingLevel(Level.parse(config.loggingLevel));

    compiler = createCompiler();
    B options = createOptions();

    List<SourceFile> externs = createExterns(options);

    List<JSModule> modules = null;
    Result result = null;

    setRunOptions(options);

    rootRelativePathsMap = constructRootRelativePathsMap();

    boolean writeOutputToFile = !config.jsOutputFile.isEmpty();
    List<String> outputFileNames = new ArrayList<>();
    if (writeOutputToFile) {
      outputFileNames.add(config.jsOutputFile);
    }

    boolean createCommonJsModules = false;
    if (options.processCommonJSModules
        && (config.module.size() == 1 && "auto".equals(config.module.get(0)))) {
      createCommonJsModules = true;
      config.module.remove(0);
    }

    List<JsModuleSpec> jsModuleSpecs = new ArrayList<>();
    for (int i = 0; i < config.module.size(); i++) {
      jsModuleSpecs.add(JsModuleSpec.create(config.module.get(i), i == 0));
    }
    List<JsonFileSpec> jsonFiles = null;

    if (config.jsonStreamMode == JsonStreamMode.IN ||
        config.jsonStreamMode == JsonStreamMode.BOTH) {
      jsonFiles = parseJsonFilesFromInputStream();

      ImmutableMap.Builder<String, SourceMapInput> inputSourceMaps
          = new ImmutableMap.Builder<>();
      ImmutableMap.Builder<String, String> inputPathByWebpackId = new ImmutableMap.Builder<>();

      boolean foundJsonInputSourceMap = false;
      for (JsonFileSpec jsonFile : jsonFiles) {
        if (jsonFile.getSourceMap() != null && jsonFile.getSourceMap().length() > 0) {
          String sourceMapPath = jsonFile.getPath() + ".map";
          SourceFile sourceMap = SourceFile.fromCode(sourceMapPath,
              jsonFile.getSourceMap());
          inputSourceMaps.put(jsonFile.getPath(), new SourceMapInput(sourceMap));
          foundJsonInputSourceMap = true;
        }
        if (jsonFile.getWebpackId() != null) {
          inputPathByWebpackId.put(jsonFile.getWebpackId(), jsonFile.getPath());
        }
      }

      if (foundJsonInputSourceMap) {
        inputSourceMaps.putAll(options.inputSourceMaps);
        options.inputSourceMaps = inputSourceMaps.build();
      }

      compiler.initWebpackMap(inputPathByWebpackId.build());
    } else {
      ImmutableMap<String, String> emptyMap = ImmutableMap.of();
      compiler.initWebpackMap(emptyMap);
    }

    compiler.initWarningsGuard(options.getWarningsGuard());
    List<SourceFile> inputs =
        createSourceInputs(jsModuleSpecs, config.mixedJsSources, jsonFiles);
    if (!jsModuleSpecs.isEmpty()) {
      if (isInTestMode()) {
        modules = modulesSupplierForTesting.get();
      } else {
        if (JsonStreamMode.IN.equals(config.jsonStreamMode)
            || JsonStreamMode.NONE.equals(config.jsonStreamMode)) {
          for (JsModuleSpec m : jsModuleSpecs) {
            checkModuleName(m.getName());
          }
        }
        modules = createJsModules(jsModuleSpecs, inputs);
      }
      for (JSModule m : modules) {
        outputFileNames.add(getModuleOutputFileName(m));
      }

      compiler.initModules(externs, modules, options);
    } else {
      compiler.init(externs, inputs, options);
    }

    if (options.printConfig) {
      compiler.printConfig(System.err);
    }


    String saveAfterChecksFilename = config.getSaveAfterChecksFileName();
    String continueSavedCompilationFilename = config.getContinueSavedCompilationFileName();
    if (config.skipNormalOutputs) {
      // TODO(bradfordcsmith): Should we be ignoring possible init/initModules() errors here?
      compiler.orderInputsWithLargeStack();
    } else if (compiler.hasErrors()) {
      // init() or initModules() encountered an error.
      compiler.generateReport();
      result = compiler.getResult();
    } else if (options.getInstrumentForCoverageOnly()) {
      result = instrumentForCoverage();
    } else if (saveAfterChecksFilename != null) {
      result = performStage1andSave(saveAfterChecksFilename);
    } else if (continueSavedCompilationFilename != null) {
      result = restoreAndPerformStage2(continueSavedCompilationFilename);
      if (modules != null) {
        modules = ImmutableList.copyOf(compiler.getModules());
      }
    } else {
      result = performFullCompilation();
    }

    if (createCommonJsModules) {
      // For CommonJS modules construct modules from actual inputs.
      modules = ImmutableList.copyOf(compiler.getModules());
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

  @GwtIncompatible("Unnecessary")
  private Result performStage1andSave(String filename) {
    Result result;
    try (BufferedOutputStream serializedOutputStream =
        new BufferedOutputStream(new FileOutputStream(filename))) {
      compiler.parseForCompilation();
      if (!compiler.hasErrors()) {
        compiler.stage1Passes();
      }
      if (!compiler.hasErrors()) {
        compiler.saveState(serializedOutputStream);
      }
      compiler.performPostCompilationTasks();
    } catch (IOException e) {
      compiler.report(JSError.make(COULD_NOT_SERIALIZE_AST, filename));
    } finally {
      // Make sure we generate a report of errors and warnings even if the compiler throws an
      // exception somewhere.
      compiler.generateReport();
    }
    result = compiler.getResult();
    return result;
  }

  @GwtIncompatible("Unnecessary")
  private Result restoreAndPerformStage2(String filename) {
    Result result;
    try (BufferedInputStream serializedInputStream =
        new BufferedInputStream(new FileInputStream(filename))) {
      compiler.restoreState(serializedInputStream);
      if (!compiler.hasErrors()) {
          compiler.stage2Passes();
      }
      compiler.performPostCompilationTasks();
    } catch (IOException | ClassNotFoundException e) {
      compiler.report(JSError.make(COULD_NOT_DESERIALIZE_AST, filename));
    } finally {
      // Make sure we generate a report of errors and warnings even if the compiler throws an
      // exception somewhere.
      compiler.generateReport();
    }
    result = compiler.getResult();
    return result;
  }

  @GwtIncompatible("Unnecessary")
  private Result performFullCompilation() {
    Result result;
    try {
      compiler.parseForCompilation();
      if (!compiler.hasErrors()) {
        compiler.stage1Passes();
        if (!compiler.hasErrors()) {
          compiler.stage2Passes();
        }
        compiler.performPostCompilationTasks();
      }
    } finally {
      // Make sure we generate a report of errors and warnings even if the compiler throws an
      // exception somewhere.
      compiler.generateReport();
    }
    result = compiler.getResult();
    return result;
  }

  @GwtIncompatible("Unnecessary")
  private Result instrumentForCoverage() {
    Result result;
    try {
      compiler.parseForCompilation();
      if (!compiler.hasErrors()) {
        compiler.instrumentForCoverage();
      }
    } finally {
      compiler.generateReport();
    }
    result = compiler.getResult();
    return result;
  }

  /** Processes the results of the compile job, and returns an error code. */
  @GwtIncompatible("Unnecessary")
  int processResults(Result result, List<JSModule> modules, B options) throws IOException {
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

    if (config.skipNormalOutputs) {
      // Output the manifest and bundle files if requested.
      outputManifest();
      outputBundle();
      outputModuleGraphJson();
      return 0;
    } else if (options.outputJs != OutputJs.NONE && result.success) {
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
        DiagnosticType error = outputModuleBinaryAndSourceMaps(compiler.getModules(), options);
        if (error != null) {
          compiler.report(JSError.make(error));
          return 1;
        }
      }

      // Output the externs if required.
      if (options.externExportsPath != null) {
        try (Writer eeOut = openExternExportsStream(options, config.jsOutputFile)) {
          eeOut.append(result.externExport);
        }
      }

      // Output the variable and property name maps if requested.
      outputNameMaps();

      // Output the ReplaceStrings map if requested
      outputStringMap();

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

  @GwtIncompatible("Unnecessary")
  Function<String, String> getJavascriptEscaper() {
    return SourceCodeEscapers.javascriptEscaper().asFunction();
  }

  @GwtIncompatible("Unnecessary")
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
      if (!config.jsOutputFile.isEmpty()) {
        maybeCreateDirsForPath(config.jsOutputFile);
      }

      Appendable jsOutput = createDefaultOutput();
      writeOutput(
          jsOutput, compiler, (JSModule) null, config.outputWrapper,
          marker, escaper);
      closeAppendable(jsOutput);
    }
  }

  /** Save the compiler output to a JsonFileSpec to be later written to stdout */
  @GwtIncompatible("Unnecessary")
  JsonFileSpec createJsonFile(B options, String outputMarker, Function<String, String> escaper)
      throws IOException {
    Appendable jsOutput = new StringBuilder();
    writeOutput(
        jsOutput, compiler, (JSModule) null, config.outputWrapper,
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

  @GwtIncompatible("Unnecessary")
  void outputJsonStream() throws IOException {
    try (JsonWriter jsonWriter =
        new JsonWriter(new BufferedWriter(new OutputStreamWriter(defaultJsOutput, UTF_8)))) {
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

  @GwtIncompatible("Unnecessary")
  private DiagnosticType outputModuleBinaryAndSourceMaps(Iterable<JSModule> modules, B options)
      throws IOException {
    parsedModuleWrappers = parseModuleWrappers(
        config.moduleWrapper, modules);
    maybeCreateDirsForPath(config.moduleOutputPathPrefix);

    // If the source map path is in fact a pattern for each
    // module, create a stream per-module. Otherwise, create
    // a single source map.
    Writer mapFileOut = null;

    // When the json_streams flag is specified, sourcemaps are always generated
    // per module
    if (!(shouldGenerateMapPerModule(options)
        || options.sourceMapOutputPath == null
        || config.jsonStreamMode == JsonStreamMode.OUT
        || config.jsonStreamMode == JsonStreamMode.BOTH)) {
      // warn that this is not supported
      return INVALID_MODULE_SOURCEMAP_PATTERN;
    }

    for (JSModule m : modules) {
      if (isOutputInJson()) {
        this.filesToStreamOut.add(createJsonFileFromModule(m));
      } else {
        if (shouldGenerateMapPerModule(options)) {
          mapFileOut = fileNameToOutputWriter2(expandSourceMapPath(options, m));
        }

        String moduleFilename = getModuleOutputFileName(m);
        try (Writer writer = fileNameToLegacyOutputWriter(moduleFilename)) {
          if (options.sourceMapOutputPath != null) {
            compiler.getSourceMap().reset();
          }
          writeModuleOutput(writer, m);
          if (options.sourceMapOutputPath != null) {
            compiler.getSourceMap().appendTo(mapFileOut, moduleFilename);
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
    return null;
  }

  /** Given an output module, convert it to a JSONFileSpec with associated sourcemap */
  @GwtIncompatible("Unnecessary")
  private JsonFileSpec createJsonFileFromModule(JSModule module) throws IOException {
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
   * Query the flag for the input charset, and return a Charset object representing the selection.
   *
   * @return Charset to use when reading inputs
   * @throws FlagUsageException if flag is not a valid Charset name.
   */
  @GwtIncompatible("Unnecessary")
  private Charset getInputCharset() {
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
   * <p>Let the outputCharset be the same as the input charset... except if we're reading in UTF-8
   * by default. By tradition, we've always output ASCII to avoid various hiccups with different
   * browsers, proxies and firewalls.
   *
   * @return Name of the charset to use when writing outputs. Guaranteed to be a supported charset.
   * @throws FlagUsageException if flag is not a valid Charset name.
   */
  @GwtIncompatible("Unnecessary")
  private Charset getLegacyOutputCharset() {
    if (!config.charset.isEmpty()) {
      if (!Charset.isSupported(config.charset)) {
        throw new FlagUsageException(config.charset + " is not a valid charset name.");
      }
      return Charset.forName(config.charset);
    }
    return US_ASCII;
  }

  /**
   * Query the flag for the output charset. Defaults to UTF-8.
   *
   * @throws FlagUsageException if flag is not a valid Charset name.
   */
  @GwtIncompatible("Unnecessary")
  private Charset getOutputCharset2() {
    if (!config.charset.isEmpty()) {
      if (!Charset.isSupported(config.charset)) {
        throw new FlagUsageException(config.charset +
            " is not a valid charset name.");
      }
      return Charset.forName(config.charset);
    }
    return UTF_8;
  }

  @GwtIncompatible("Unnecessary")
  protected List<SourceFile> createExterns(CompilerOptions options) throws IOException {
    return isInTestMode() ? externsSupplierForTesting.get() : createExternInputs(config.externs);
  }

  /**
   * Returns true if and only if a source map file should be generated for each module, as opposed
   * to one unified map. This is specified by having the source map pattern include the %outname%
   * variable.
   */
  @GwtIncompatible("Unnecessary")
  private boolean shouldGenerateMapPerModule(B options) {
    return options.sourceMapOutputPath != null
        && options.sourceMapOutputPath.contains("%outname%");
  }

  /**
   * Returns a stream for outputting the generated externs file.
   *
   * @param options The options to the Compiler.
   * @param path The path of the generated JS source file.
   * @return The stream or null if no extern-ed exports are being generated.
   */
  @GwtIncompatible("Unnecessary")
  private Writer openExternExportsStream(B options, String path) throws IOException {
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
   * <p>Most file paths on the command-line allow an %outname% placeholder. The placeholder will
   * expand to a different value depending on the current output mode. There are three scenarios:
   *
   * <p>1) Single JS output, single extra output: sub in jsOutputPath. 2) Multiple JS output, single
   * extra output: sub in the base module name. 3) Multiple JS output, multiple extra output: sub in
   * the module output file.
   *
   * <p>Passing a JSModule to this function automatically triggers case #3. Otherwise, we'll use
   * strategy #1 or #2 based on the current output mode.
   */
  @GwtIncompatible("Unnecessary")
  private String expandCommandLinePath(String path, JSModule forModule) {
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
  @GwtIncompatible("Unnecessary")
  String expandSourceMapPath(B options, JSModule forModule) {
    if (Strings.isNullOrEmpty(options.sourceMapOutputPath)) {
      return null;
    }
    return expandCommandLinePath(options.sourceMapOutputPath, forModule);
  }

  /**
   * Converts a file name into a Writer taking in account the output charset. Returns null if the
   * file name is null.
   */
  @GwtIncompatible("Unnecessary")
  private Writer fileNameToLegacyOutputWriter(String fileName) throws IOException {
    if (fileName == null) {
      return null;
    }
    if (isInTestMode()) {
      return new StringWriter();
    }

    return streamToLegacyOutputWriter(filenameToOutputStream(fileName));
  }

  /**
   * Converts a file name into a Writer taking in account the output charset. Returns null if the
   * file name is null.
   */
  @GwtIncompatible("Unnecessary")
  private Writer fileNameToOutputWriter2(String fileName) throws IOException {
    if (fileName == null) {
      return null;
    }
    if (isInTestMode()) {
      return new StringWriter();
    }

    return streamToOutputWriter2(filenameToOutputStream(fileName));
  }

  /** Converts a file name into a Outputstream. Returns null if the file name is null. */
  @GwtIncompatible("Unnecessary")
  protected OutputStream filenameToOutputStream(String fileName) throws IOException {
    if (fileName == null){
      return null;
    }
    return new FileOutputStream(fileName);
  }

  /** Create a writer with the legacy output charset. */
  @GwtIncompatible("Unnecessary")
  private Writer streamToLegacyOutputWriter(OutputStream stream) throws IOException {
    if (legacyOutputCharset == null) {
      return new BufferedWriter(new OutputStreamWriter(stream, UTF_8));
    } else {
      return new BufferedWriter(
          new OutputStreamWriter(stream, legacyOutputCharset));
    }
  }

  /** Create a writer with the newer output charset. */
  @GwtIncompatible("Unnecessary")
  private Writer streamToOutputWriter2(OutputStream stream) {
    if (outputCharset2 == null) {
      return new BufferedWriter(new OutputStreamWriter(stream, UTF_8));
    } else {
      return new BufferedWriter(
          new OutputStreamWriter(stream, outputCharset2));
    }
  }

  /**
   * Outputs the source map found in the compiler to the proper path if one exists.
   *
   * @param options The options to the Compiler.
   */
  @GwtIncompatible("Unnecessary")
  private void outputSourceMap(B options, String associatedName) throws IOException {
    if (Strings.isNullOrEmpty(options.sourceMapOutputPath)
        || options.sourceMapOutputPath.equals("/dev/null")) {
      return;
    }

    String outName = expandSourceMapPath(options, null);
    maybeCreateDirsForPath(outName);
    try (Writer out = fileNameToOutputWriter2(outName)) {
      compiler.getSourceMap().appendTo(out, associatedName);
    }
  }

  /**
   * Returns the path at which to output map file(s) based on the path at which the JS binary will
   * be placed.
   *
   * @return The path in which to place the generated map file(s).
   */
  @GwtIncompatible("Unnecessary")
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
   * Outputs the variable and property name maps for the specified compiler if the proper FLAGS are
   * set.
   */
  @GwtIncompatible("Unnecessary")
  private void outputNameMaps() throws IOException {

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
            + "create_name_map_files cannot both be used simultaneously.");
      }

      variableMapOutputPath = config.variableMapOutputFile;
    }

    if (!config.propertyMapOutputFile.isEmpty()) {
      if (propertyMapOutputPath != null) {
        throw new FlagUsageException("The flags property_map_output_file and "
            + "create_name_map_files cannot both be used simultaneously.");
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
   * Outputs the string map generated by the {@link ReplaceStrings} pass if an output path exists.
   */
  @GwtIncompatible("Unnecessary")
  private void outputStringMap() throws IOException {
    if (!config.stringMapOutputPath.isEmpty()) {
      if (compiler.getStringMap() == null) {
        // Ensure an empty file is created if there is no string map.
        // This avoids confusing some build tools that expect to see the file, even if it is empty.
        if (!(new File(config.stringMapOutputPath).createNewFile())) {
          throw new IOException("Could not create file: " + config.stringMapOutputPath);
        }
      } else {
        compiler.getStringMap().save(config.stringMapOutputPath);
      }
    }
  }

  /**
   * Create a map of constant names to constant values from a textual description of the map.
   *
   * @param definitions A list of overriding definitions for defines in the form {@code
   *     <name>[=<val>]}, where {@code <val>} is a number, boolean, or single-quoted string without
   *     single quotes.
   */
  @VisibleForTesting
  public static void createDefineOrTweakReplacements(
      List<String> definitions, CompilerOptions options, boolean tweaks) {
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

          if (defValue.length() > 0) {
            if (tweaks) {
              options.setTweakToStringLiteral(defName, defValue);
            } else {
              options.setDefineToStringLiteral(defName, defValue);
            }
            continue;
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
   * Returns true if and only if a manifest or bundle should be generated for each module, as
   * opposed to one unified manifest.
   */
  @GwtIncompatible("Unnecessary")
  private boolean shouldGenerateOutputPerModule(String output) {
    return !config.module.isEmpty()
        && output != null && output.contains("%outname%");
  }

  @GwtIncompatible("Unnecessary")
  private void outputManifest() throws IOException {
    outputManifestOrBundle(config.outputManifests, true);
  }

  @GwtIncompatible("Unnecessary")
  private void outputBundle() throws IOException {
    outputManifestOrBundle(config.outputBundles, false);
  }

  /**
   * Writes the manifest or bundle of all compiler input files that were included as controlled by
   * --dependency_mode, if requested.
   */
  @GwtIncompatible("Unnecessary")
  private void outputManifestOrBundle(List<String> outputFiles, boolean isManifest)
      throws IOException {
    if (outputFiles.isEmpty()) {
      return;
    }

    for (String output : outputFiles) {
      if (output.isEmpty()) {
        continue;
      }

      if (shouldGenerateOutputPerModule(output)) {
        // Generate per-module manifests or bundles
        Iterable<JSModule> modules = compiler.getModuleGraph().getAllModules();
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
            printModuleGraphManifestOrBundleTo(compiler.getModuleGraph(), out, isManifest);
          }
        }
      }
    }
  }

  /** Creates a file containing the current module graph in JSON serialization. */
  @GwtIncompatible("Unnecessary")
  private void outputModuleGraphJson() throws IOException {
    if (config.outputModuleDependencies != null &&
        config.outputModuleDependencies.length() != 0) {
      try (Writer out = fileNameToOutputWriter2(config.outputModuleDependencies)) {
        printModuleGraphJsonTo(out);
      }
    }
  }

  /** Prints the current module graph as JSON. */
  @VisibleForTesting
  @GwtIncompatible("Unnecessary")
  void printModuleGraphJsonTo(Appendable out) throws IOException {
    out.append(compiler.getModuleGraph().toJson().toString());
  }

  /** Prints a set of modules to the manifest or bundle file. */
  @VisibleForTesting
  @GwtIncompatible("Unnecessary")
  void printModuleGraphManifestOrBundleTo(JSModuleGraph graph, Appendable out, boolean isManifest)
      throws IOException {
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
   * Prints a list of input names (using root-relative paths), delimited by newlines, to the
   * manifest file.
   */
  @VisibleForTesting
  @GwtIncompatible("Unnecessary")
  void printManifestTo(Iterable<CompilerInput> inputs, Appendable out) throws IOException {
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
   * Prints all the input contents, starting with a comment that specifies the input file name
   * (using root-relative paths) before each file.
   */
  @VisibleForTesting
  @GwtIncompatible("Unnecessary")
  void printBundleTo(Iterable<CompilerInput> inputs, Appendable out) throws IOException {
    if (!compiler.getOptions().preventLibraryInjection) {
      // ES6 modules will need a runtime in a bundle. Skip appending this runtime if there are no
      // ES6 modules to cut down on size.
      for (CompilerInput input : inputs) {
        if ("es6".equals(input.getLoadFlags().get("module"))) {
          appendRuntimeTo(out);
          break;
        }
      }
    }

    for (CompilerInput input : inputs) {
      String name = input.getName();
      String code = input.getSourceFile().getCode();

      // Ignore empty fill files created by the compiler to facilitate cross-module code motion.
      // Note that non-empty fill files (ones whose code has actually been moved into) are still
      // emitted. In particular, this ensures that if there are no (real) inputs the bundle will be
      // empty.
      if (Compiler.isFillFileName(name) && code.isEmpty()) {
        continue;
      }

      String rootRelativePath = rootRelativePathsMap.get(name);
      String displayName = rootRelativePath != null
          ? rootRelativePath
          : input.getName();
      out.append("//");
      out.append(displayName);
      out.append("\n");

      prepForBundleAndAppendTo(out, input, code);

      out.append("\n");
    }
  }

  /**
   * Construct and return the input root path map. The key is the exec path of each input file, and
   * the value is the corresponding root relative path.
   */
  @GwtIncompatible("Unnecessary")
  private Map<String, String> constructRootRelativePathsMap() {
    Map<String, String> rootRelativePathsMap = new LinkedHashMap<>();
    for (String mapString : config.manifestMaps) {
      int colonIndex = mapString.indexOf(':');
      checkState(colonIndex > 0);
      String execPath = mapString.substring(0, colonIndex);
      String rootRelativePath = mapString.substring(colonIndex + 1);
      checkState(rootRelativePath.indexOf(':') == -1);
      rootRelativePathsMap.put(execPath, rootRelativePath);
    }
    return rootRelativePathsMap;
  }

  /**
   * Configurations for the command line configs. Designed for easy building, so that we can
   * decouple the flags-parsing library from the actual configuration options.
   *
   * <p>TODO(tjgq): Investigate whether this class is really needed to mediate between the
   * CompilerOptions and runner implementations. An alternative would be for the runners to fill in
   * the CompilerOptions directly, but that conflicts with the latter's mutability and the desire to
   * reuse the same options across multiple compilations.
   */
  @GwtIncompatible("Unnecessary")
  protected static class CommandLineConfig {
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
    public CommandLineConfig setPrintAst(boolean printAst) {
      this.printAst = printAst;
      return this;
    }

    private boolean printPassGraph = false;

    /** Prints a dot file describing the passes that will get run and exits */
    public CommandLineConfig setPrintPassGraph(boolean printPassGraph) {
      this.printPassGraph = printPassGraph;
      return this;
    }

    private CompilerOptions.DevMode jscompDevMode = CompilerOptions.DevMode.OFF;

    /** Turns on extra validity checks */
    public CommandLineConfig setJscompDevMode(CompilerOptions.DevMode jscompDevMode) {
      this.jscompDevMode = jscompDevMode;
      return this;
    }

    private String loggingLevel = Level.WARNING.getName();

    /**
     * The logging level (standard java.util.logging.Level
     * values) for Compiler progress. Does not control errors or
     * warnings for the JavaScript code under compilation
     */
    public CommandLineConfig setLoggingLevel(String loggingLevel) {
      this.loggingLevel = loggingLevel;
      return this;
    }

    private final List<String> externs = new ArrayList<>();

    /**
     * The file containing JavaScript externs. You may specify multiple.
     */
    public CommandLineConfig setExterns(List<String> externs) {
      this.externs.clear();
      this.externs.addAll(externs);
      return this;
    }

    private final List<FlagEntry<JsSourceType>> mixedJsSources =
        new ArrayList<>();

    /**
     * The JavaScript source file names, including .js and .zip files. You may
     * specify multiple.
     */
    public CommandLineConfig setMixedJsSources(
        List<FlagEntry<JsSourceType>> mixedJsSources) {
      this.mixedJsSources.clear();
      this.mixedJsSources.addAll(mixedJsSources);
      return this;
    }

    private String jsOutputFile = "";

    /**
     * Primary output filename. If not specified, output is written to stdout
     */
    public CommandLineConfig setJsOutputFile(String jsOutputFile) {
      this.jsOutputFile = jsOutputFile;
      return this;
    }

    private String continueSavedCompilationFileName = null;

    /**
     * Set the compiler to resume a saved compilation state from a file.
     */
    public CommandLineConfig setContinueSavedCompilationFileName(String fileName) {
      continueSavedCompilationFileName = fileName;
      return this;
    }

    String getContinueSavedCompilationFileName() {
      return continueSavedCompilationFileName;
    }

    private String saveAfterChecksFileName = null;

    /**
     * Set the compiler to perform the first phase and save the intermediate result to a file.
     */
    public CommandLineConfig setSaveAfterChecksFileName(String fileName) {
      saveAfterChecksFileName = fileName;
      return this;
    }

    public String getSaveAfterChecksFileName() {
      return saveAfterChecksFileName;
    }

    private final List<String> module = new ArrayList<>();

    /**
     * A JavaScript module specification. The format is
     * {@code <name>:<num-js-files>[:[<dep>,...][:]]]}. Module names must be
     * unique. Each dep is the name of a module that this module
     * depends on. Modules must be listed in dependency order, and JS
     * source files must be listed in the corresponding order. Where
     * --module flags occur in relation to --js flags is unimportant
     */
    public CommandLineConfig setModule(List<String> module) {
      this.module.clear();
      this.module.addAll(module);
      return this;
    }

    private Map<String, String> sourceMapInputFiles = new HashMap<>();

    public CommandLineConfig setSourceMapInputFiles(
        Map<String, String> sourceMapInputFiles) {
      this.sourceMapInputFiles = sourceMapInputFiles;
      return this;
    }

    private boolean parseInlineSourceMaps = false;

    public CommandLineConfig setParseInlineSourceMaps(boolean parseInlineSourceMaps) {
      this.parseInlineSourceMaps = parseInlineSourceMaps;
      return this;
    }

    private String variableMapInputFile = "";

    /**
     * File containing the serialized version of the variable renaming
     * map produced by a previous compilation
     */
    public CommandLineConfig setVariableMapInputFile(String variableMapInputFile) {
      this.variableMapInputFile = variableMapInputFile;
      return this;
    }

    private String propertyMapInputFile = "";

    /**
     * File containing the serialized version of the property renaming
     * map produced by a previous compilation
     */
    public CommandLineConfig setPropertyMapInputFile(String propertyMapInputFile) {
      this.propertyMapInputFile = propertyMapInputFile;
      return this;
    }

    private String variableMapOutputFile = "";

    /**
     * File where the serialized version of the variable renaming map
     * produced should be saved
     */
    public CommandLineConfig setVariableMapOutputFile(String variableMapOutputFile) {
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
    public CommandLineConfig setCreateNameMapFiles(boolean createNameMapFiles) {
      this.createNameMapFiles = createNameMapFiles;
      return this;
    }

    private String propertyMapOutputFile = "";

    /**
     * File where the serialized version of the property renaming map
     * produced should be saved
     */
    public CommandLineConfig setPropertyMapOutputFile(String propertyMapOutputFile) {
      this.propertyMapOutputFile = propertyMapOutputFile;
      return this;
    }


    private String stringMapOutputPath = "";

    /**
     * File where the serialized version of the string map produced by the ReplaceStrings pass
     * should be saved.
     */
    public CommandLineConfig setStringMapOutputFile(String stringMapOutputPath) {
      this.stringMapOutputPath = stringMapOutputPath;
      return this;
    }

    private CodingConvention codingConvention = CodingConventions.getDefault();

    /**
     * Sets rules and conventions to enforce.
     */
    public CommandLineConfig setCodingConvention(CodingConvention codingConvention) {
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
    public CommandLineConfig setSummaryDetailLevel(int summaryDetailLevel) {
      this.summaryDetailLevel = summaryDetailLevel;
      return this;
    }

    private String outputWrapper = "";

    /**
     * Interpolate output into this string at the place denoted
     * by the marker token %output%, or %output|jsstring%
     */
    public CommandLineConfig setOutputWrapper(String outputWrapper) {
      this.outputWrapper = outputWrapper;
      return this;
    }

    private final List<String> moduleWrapper = new ArrayList<>();

    /**
     * An output wrapper for a JavaScript module (optional). See the flag
     * description for formatting requirements.
     */
    public CommandLineConfig setModuleWrapper(List<String> moduleWrapper) {
      this.moduleWrapper.clear();
      this.moduleWrapper.addAll(moduleWrapper);
      return this;
    }

    private String moduleOutputPathPrefix = "";

    /**
     * Prefix for filenames of compiled JS modules.
     * {@code <module-name>.js} will be appended to this prefix. Directories
     * will be created as needed. Use with --module
     */
    public CommandLineConfig setModuleOutputPathPrefix(String moduleOutputPathPrefix) {
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
    public CommandLineConfig setCreateSourceMap(String createSourceMap) {
      this.createSourceMap = createSourceMap;
      return this;
    }

    private SourceMap.DetailLevel sourceMapDetailLevel =
        SourceMap.DetailLevel.ALL;

    /**
     * The detail supplied in the source map file, if generated.
     */
    public CommandLineConfig setSourceMapDetailLevel(SourceMap.DetailLevel level) {
      this.sourceMapDetailLevel = level;
      return this;
    }

    private SourceMap.Format sourceMapFormat =
      SourceMap.Format.DEFAULT;

    /**
     * The source map format to use, if generated.
     */
    public CommandLineConfig setSourceMapFormat(SourceMap.Format format) {
      this.sourceMapFormat = format;
      return this;
    }

    private ImmutableList<SourceMap.LocationMapping> sourceMapLocationMappings =
      ImmutableList.of();

    /**
     * The source map location mappings to use, if generated.
     */
    public CommandLineConfig setSourceMapLocationMappings(
        List<SourceMap.LocationMapping> locationMappings) {

      this.sourceMapLocationMappings = ImmutableList.copyOf(locationMappings);
      return this;
    }

    private boolean applyInputSourceMaps = false;

    /**
     * Whether to apply input source maps to the output, i.e. map back to original inputs from
     * input files that have source maps applied to them.
     */
    public CommandLineConfig setApplyInputSourceMaps(boolean applyInputSourceMaps) {
      this.applyInputSourceMaps = applyInputSourceMaps;
      return this;
    }

    private final ArrayList<FlagEntry<CheckLevel>> warningGuards = new ArrayList<>();

    /**
     * Add warning guards.
     */
    public CommandLineConfig setWarningGuards(List<FlagEntry<CheckLevel>> warningGuards) {
      this.warningGuards.clear();
      this.warningGuards.addAll(warningGuards);
      return this;
    }

    private final List<String> define = new ArrayList<>();

    /**
     * Override the value of a variable annotated @define.  The format
     * is {@code <name>[=<val>]}, where {@code <name>} is the name of
     * a @define variable and {@code <val>} is a boolean, number, or a
     * single-quoted string that contains no single quotes. If
     * {@code [=<val>]} is omitted, the variable is marked true
     */
    public CommandLineConfig setDefine(List<String> define) {
      this.define.clear();
      this.define.addAll(define);
      return this;
    }

    private final List<String> tweak = new ArrayList<>();

    /**
     * Override the default value of a registered tweak. The format is
     * {@code <name>[=<val>]}, where {@code <name>} is the ID of a
     * tweak and {@code <val>} is a boolean, number, or a
     * single-quoted string that contains no single quotes. If
     * {@code [=<val>]} is omitted, then true is assumed.
     */
    public CommandLineConfig setTweak(List<String> tweak) {
      this.tweak.clear();
      this.tweak.addAll(tweak);
      return this;
    }

    private TweakProcessing tweakProcessing = TweakProcessing.OFF;

    /**
     * Sets the kind of processing to do for goog.tweak functions.
     */
    public CommandLineConfig setTweakProcessing(TweakProcessing tweakProcessing) {
      this.tweakProcessing = tweakProcessing;
      return this;
    }

    private String charset = "";

    /**
     * Input charset for all files.
     */
    public CommandLineConfig setCharset(String charset) {
      this.charset = charset;
      return this;
    }

    private DependencyOptions dependencyOptions = null;

    /** Sets the dependency management options. */
    public CommandLineConfig setDependencyOptions(@Nullable DependencyOptions dependencyOptions) {
      this.dependencyOptions = dependencyOptions;
      return this;
    }

    private List<String> outputManifests = ImmutableList.of();

    /**
     * Sets whether to print output manifest files.
     * Filter out empty file names.
     */
    public CommandLineConfig setOutputManifest(List<String> outputManifests) {
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
    public CommandLineConfig setOutputModuleDependencies(String
        outputModuleDependencies) {
      this.outputModuleDependencies = outputModuleDependencies;
      return this;
    }

    private List<String> outputBundles = ImmutableList.of();

    /**
     * Sets whether to print output bundle files.
     */
    public CommandLineConfig setOutputBundle(List<String> outputBundles) {
      this.outputBundles = outputBundles;
      return this;
    }

    private boolean skipNormalOutputs = false;

    /**
     * Sets whether the normal outputs of compilation should be skipped.
     */
    public CommandLineConfig setSkipNormalOutputs(boolean skipNormalOutputs) {
      this.skipNormalOutputs = skipNormalOutputs;
      return this;
    }

    private List<String> manifestMaps = ImmutableList.of();

    /**
     * Sets the execPath:rootRelativePath mappings
     */
    public CommandLineConfig setManifestMaps(List<String> manifestMaps) {
      this.manifestMaps = manifestMaps;
      return this;
    }


    private boolean transformAMDToCJSModules = false;

    /**
     * Set whether to transform AMD to CommonJS modules.
     */
    public CommandLineConfig setTransformAMDToCJSModules(
        boolean transformAMDToCJSModules) {
      this.transformAMDToCJSModules = transformAMDToCJSModules;
      return this;
    }

    private boolean processCommonJSModules = false;

    /**
     * Sets whether to process CommonJS modules.
     */
    public CommandLineConfig setProcessCommonJSModules(
        boolean processCommonJSModules) {
      this.processCommonJSModules = processCommonJSModules;
      return this;
    }

    private List<String> moduleRoots = ImmutableList.of(ModuleLoader.DEFAULT_FILENAME_PREFIX);

    /**
     * Sets the module roots.
     */
    public CommandLineConfig setModuleRoots(List<String> jsModuleRoots) {
      this.moduleRoots = jsModuleRoots;
      return this;
    }

    private String warningsWhitelistFile = "";

    /**
     * Sets a whitelist file that suppresses warnings.
     */
    public CommandLineConfig setWarningsWhitelistFile(String fileName) {
      this.warningsWhitelistFile = fileName;
      return this;
    }

    private List<String> hideWarningsFor = ImmutableList.of();

    /**
     * Sets the paths for which warnings will be hidden.
     */
    public CommandLineConfig setHideWarningsFor(List<String> hideWarningsFor) {
      this.hideWarningsFor = hideWarningsFor;
      return this;
    }

    private boolean angularPass = false;

    /**
     * Sets whether to process AngularJS-specific annotations.
     */
    public CommandLineConfig setAngularPass(boolean angularPass) {
      this.angularPass = angularPass;
      return this;
    }

    private String instrumentationTemplateFile = "";

    public CommandLineConfig setInstrumentationTemplateFile(String fileName) {
        this.instrumentationTemplateFile = fileName;
        return this;
    }

    private JsonStreamMode jsonStreamMode = JsonStreamMode.NONE;

    public CommandLineConfig setJsonStreamMode(JsonStreamMode mode) {
      this.jsonStreamMode = mode;
      return this;
    }

    /** Set of options that can be used with the --formatting flag. */
    protected enum ErrorFormatOption {
      STANDARD,
      JSON
    }

    private ErrorFormatOption errorFormat = ErrorFormatOption.STANDARD;

    public CommandLineConfig setErrorFormat(ErrorFormatOption errorFormat) {
      this.errorFormat = errorFormat;
      return this;
    }

    private String jsonWarningsFile = "";

    public CommandLineConfig setJsonWarningsFile(String jsonWarningsFile) {
      this.jsonWarningsFile = jsonWarningsFile;
      return this;
    }
  }

  /** Representation of a source file from an encoded json stream input */
  @GwtIncompatible("Unnecessary")
  public static class JsonFileSpec {
    private final String src;
    private final String path;
    private String sourceMap;
    @Nullable
    private final String webpackId;

    // Graal requires a non-arg constructor for use with GSON
    // See https://github.com/oracle/graal/issues/680
    private JsonFileSpec() {
      this(null, null, null, null);
    }

    public JsonFileSpec(String src, String path) {
      this(src, path, null, null);
    }

    public JsonFileSpec(String src, String path, String sourceMap) {
      this(src, path, sourceMap, null);
    }

    public JsonFileSpec(String src, String path, String sourceMap, @Nullable String webpackId) {
      this.src = src;
      this.path = path;
      this.sourceMap = sourceMap;
      this.webpackId = webpackId;
    }

    public String getSrc() {
      return this.src;
    }

    public String getPath() {
      return this.path;
    }

    public String getSourceMap() {
      return this.sourceMap;
    }

    public String getWebpackId() {
      return this.webpackId;
    }

    public void setSourceMap(String map) {
      this.sourceMap = map;
    }
  }

  /** Flag types for js source files. */
  @GwtIncompatible("Unnecessary")
  protected enum JsSourceType {
    EXTERN("extern"),
    JS("js"),
    JS_ZIP("jszip"),
    WEAKDEP("weakdep");

    @VisibleForTesting
    final String flagName;

    private JsSourceType(String flagName) {
      this.flagName = flagName;
    }
  }

  /** A pair from flag to its value. */
  @GwtIncompatible("Unnecessary")
  protected static class FlagEntry<T> {
    private final T flag;
    private final String value;

    protected FlagEntry(T flag, String value) {
      this.flag = flag;
      this.value = value;
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof FlagEntry) {
        FlagEntry<?> that = (FlagEntry<?>) o;
        return that.flag.equals(this.flag)
            && that.value.equals(this.value);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return flag.hashCode() + value.hashCode();
    }

    public T getFlag() {
      return flag;
    }

    public String getValue() {
      return value;
    }
  }

  /**
   * Represents a specification for a js module.
   */
  public static class JsModuleSpec {
    private final String name;
    // Number of input files, including js and zip files.
    private final int numInputs;
    private final ImmutableList<String> deps;
    // Number of input js files. All zip files should be expanded.
    private int numJsFiles;

    private JsModuleSpec(String name, int numInputs, ImmutableList<String> deps) {
      this.name = name;
      this.numInputs = numInputs;
      this.deps = deps;
      this.numJsFiles = numInputs;
    }

    /**
     * @param specString The spec format is: <code>name:num-js-files[:[dep,...][:]]</code>. Module
     *     names must not contain the ':' character.
     * @param isFirstModule Whether the spec is for the first module.
     * @return A parsed js module spec.
     */
    public static JsModuleSpec create(String specString, boolean isFirstModule) {
      // Format is "<name>:<num-js-files>[:[<dep>,...][:]]".
      String[] parts = specString.split(":");
      if (parts.length < 2 || parts.length > 4) {
        throw new FlagUsageException("Expected 2-4 colon-delimited parts in "
            + "js module spec: " + specString);
      }

      // Parse module name.
      String name = parts[0];

      // Parse module dependencies.
      String[] deps = parts.length > 2 && parts[2].length() > 0
          ? parts[2].split(",")
          : new String[0];

      // Parse module inputs.
      int numInputs = -1;
      try {
        numInputs = Integer.parseInt(parts[1]);
      } catch (NumberFormatException ignored) {
        numInputs = -1;
      }

      // We will allow modules of zero input.
      if (numInputs < 0) {
        // A size of 'auto' is only allowed on the base module if
        // and it must also be the first module
        if (parts.length == 2 && "auto".equals(parts[1])) {
          if (!isFirstModule) {
            throw new FlagUsageException("Invalid JS file count '" + parts[1]
                + "' for module: " + name + ". Only the first module may specify "
                + "a size of 'auto' and it must have no dependencies.");
          }
        } else {
          throw new FlagUsageException("Invalid JS file count '" + parts[1]
              + "' for module: " + name);
        }
      }

      return new JsModuleSpec(name, numInputs, ImmutableList.copyOf(deps));
    }

    public String getName() {
      return name;
    }

    public int getNumInputs() {
      return numInputs;
    }

    public ImmutableList<String> getDeps() {
      return deps;
    }

    public int getNumJsFiles() {
      return numJsFiles;
    }
  }

  @GwtIncompatible("Unnecessary")
  static final class SystemExitCodeReceiver implements Function<Integer, Void> {
    static final SystemExitCodeReceiver INSTANCE = new SystemExitCodeReceiver();

    private SystemExitCodeReceiver() {
      // singleton
    }

    @Override
    public Void apply(Integer exitCode) {
      int exitCodeValue = checkNotNull(exitCode);
      // Don't spuriously report success.
      // Posix conventions only guarantee that 8b are significant.
      byte exitCodeByte = (byte) exitCodeValue;
      if (exitCodeByte == 0 && exitCodeValue != 0) {
        exitCodeByte = (byte) -1;
      }
      System.exit(exitCodeByte);
      return null;
    }
  }
}
