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
import static java.lang.Math.min;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

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
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.google.javascript.jscomp.CodePrinter.LicenseTracker;
import com.google.javascript.jscomp.Compiler.ChunkGraphAwareLicenseTracker;
import com.google.javascript.jscomp.Compiler.ScriptNodeLicensesOnlyTracker;
import com.google.javascript.jscomp.Compiler.SingleBinaryLicenseTracker;
import com.google.javascript.jscomp.CompilerOptions.JsonStreamMode;
import com.google.javascript.jscomp.CompilerOptions.OutputJs;
import com.google.javascript.jscomp.CompilerOptions.SegmentOfCompilationToRun;
import com.google.javascript.jscomp.CompilerOptions.TweakProcessing;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.deps.SourceCodeEscapers;
import com.google.javascript.jscomp.ijs.IjsErrors;
import com.google.javascript.jscomp.js.RuntimeJsLibManager;
import com.google.javascript.jscomp.parsing.Config;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile.SourceKind;
import com.google.javascript.rhino.TokenStream;
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
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.jspecify.annotations.Nullable;

/**
 * Implementations of AbstractCommandLineRunner translate flags into Java API calls on the Compiler.
 * AbstractCompiler contains common flags and logic to make that happen.
 *
 * <p>This class may be extended and used to create other Java classes that behave the same as
 * running the Compiler from the command line. Example:
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
 */
public abstract class AbstractCommandLineRunner<A extends Compiler, B extends CompilerOptions> {

  static final DiagnosticType OUTPUT_SAME_AS_INPUT_ERROR =
      DiagnosticType.error(
          "JSC_OUTPUT_SAME_AS_INPUT_ERROR", "Bad output file (already listed as input file): {0}");

  static final DiagnosticType COULD_NOT_SERIALIZE_AST =
      DiagnosticType.error("JSC_COULD_NOT_SERIALIZE_AST", "Could not serialize ast to: {0}");

  static final DiagnosticType COULD_NOT_DESERIALIZE_AST =
      DiagnosticType.error("JSC_COULD_NOT_DESERIALIZE_AST", "Could not deserialize ast from: {0}");

  static final DiagnosticType NO_TREE_GENERATED_ERROR =
      DiagnosticType.error(
          "JSC_NO_TREE_GENERATED_ERROR", "Code contains errors. No tree was generated.");
  static final DiagnosticType INVALID_CHUNK_SOURCEMAP_PATTERN =
      DiagnosticType.error(
          "JSC_INVALID_CHUNK_SOURCEMAP_PATTERN",
          "When using --chunk or --module flags, the --create_source_map flag must contain "
              + "%outname% in the value.");

  static final String WAITING_FOR_INPUT_WARNING = "The compiler is waiting for input via stdin.";
  // Use an 8MiB buffer since the concatenated TypedAst file can be very large.
  private static final int GZIPPED_TYPEDAST_BUFFER_SIZE = 8 * 1024 * 1024;

  private final CommandLineConfig config;

  private final InputStream in;

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

  private Charset legacyOutputCharset;

  private boolean testMode = false;

  private @Nullable Supplier<List<SourceFile>> externsSupplierForTesting = null;

  private @Nullable Supplier<List<SourceFile>> inputsSupplierForTesting = null;

  private @Nullable Supplier<List<JSChunk>> chunksSupplierForTesting = null;

  private Function<Integer, Void> exitCodeReceiver = SystemExitCodeReceiver.INSTANCE;

  private @Nullable Map<String, String> rootRelativePathsMap = null;

  private @Nullable Map<String, String> parsedChunkWrappers = null;

  private @Nullable ImmutableMap<String, String> parsedChunkOutputFiles = null;

  private @Nullable ImmutableMap<String, String> parsedChunkConformanceFiles = null;

  private final Gson gson;

  static final String OUTPUT_MARKER = "%output%";
  private static final String OUTPUT_MARKER_JS_STRING = "%output|jsstring%";

  private final List<JsonFileSpec> filesToStreamOut = new ArrayList<>();

  AbstractCommandLineRunner() {
    this(System.in, System.out, System.err);
  }

  AbstractCommandLineRunner(PrintStream out, PrintStream err) {
    this(System.in, out, err);
  }

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
   * @param chunksSupplier A provider for chunks. Only one of inputsSupplier and chunksSupplier may
   *     be non-null.
   * @param exitCodeReceiver A receiver for the status code that would have been passed to
   *     System.exit in non-test mode.
   */
  @VisibleForTesting
  void enableTestMode(
      Supplier<List<SourceFile>> externsSupplier,
      Supplier<List<SourceFile>> inputsSupplier,
      Supplier<List<JSChunk>> chunksSupplier,
      Function<Integer, Void> exitCodeReceiver) {
    checkArgument(inputsSupplier == null != (chunksSupplier == null));
    testMode = true;
    this.externsSupplierForTesting = externsSupplier;
    this.inputsSupplierForTesting = inputsSupplier;
    this.chunksSupplierForTesting = chunksSupplier;
    this.exitCodeReceiver = exitCodeReceiver;
  }

  /**
   * @param newExitCodeReceiver receives a non-zero integer to indicate a problem during execution
   *     or 0i to indicate success.
   */
  public void setExitCodeReceiver(Function<Integer, Void> newExitCodeReceiver) {
    this.exitCodeReceiver = checkNotNull(newExitCodeReceiver);
  }

  /** Returns whether we're in test mode. */
  protected boolean isInTestMode() {
    return testMode;
  }

  /** Returns whether output should be a JSON stream. */
  private boolean isOutputInJson() {
    return config.jsonStreamMode == JsonStreamMode.OUT
        || config.jsonStreamMode == JsonStreamMode.BOTH;
  }

  /** Get the command line config, so that it can be initialized. */
  protected CommandLineConfig getCommandLineConfig() {
    return config;
  }

  /** Returns the instance of the Compiler to use when {@link #run()} is called. */
  protected abstract A createCompiler();

  /**
   * Performs any transformation needed on the given compiler input and appends it to the given
   * output bundle.
   */
  protected abstract void prepForBundleAndAppendTo(
      Appendable out, CompilerInput input, String content) throws IOException;

  /** Writes whatever runtime libraries are needed to bundle. */
  protected abstract void appendRuntimeTo(Appendable out) throws IOException;

  /**
   * Returns the instance of the Options to use when {@link #run()} is called. createCompiler() is
   * called before createOptions(), so getCompiler() will not return null when createOptions() is
   * called.
   */
  protected abstract B createOptions();

  /** The warning classes that are available from the command-line. */
  protected DiagnosticGroups getDiagnosticGroups() {
    if (compiler == null) {
      return new DiagnosticGroups();
    }
    return compiler.getDiagnosticGroups();
  }

  protected abstract void addAllowlistWarningsGuard(CompilerOptions options, File allowlistFile);

  protected static void setWarningGuardOptions(
      CompilerOptions options,
      ArrayList<FlagEntry<CheckLevel>> warningGuards,
      DiagnosticGroups diagnosticGroups) {
    if (warningGuards != null) {
      final ImmutableSet<String> groupNames = DiagnosticGroups.getRegisteredGroups().keySet();
      for (FlagEntry<CheckLevel> entry : warningGuards) {
        if ("*".equals(entry.value)) {
          for (String groupName : groupNames) {
            if (!DiagnosticGroups.wildcardExcludedGroups.contains(groupName)) {
              diagnosticGroups.setWarningLevel(options, groupName, entry.flag);
            }
          }
        } else if (groupNames.contains(entry.value)) {
          diagnosticGroups.setWarningLevel(options, entry.value, entry.flag);
        } else {
          throw new FlagUsageException("Unknown diagnostic group: '" + entry.value + "'");
        }
      }
    }
  }

  /**
   * Sets options based on the configurations set flags API. Called during the run() run() method.
   * If you want to ignore the flags API, or interpret flags your own way, then you should override
   * this method.
   */
  protected void setRunOptions(CompilerOptions options) throws IOException {
    DiagnosticGroups diagnosticGroups = getDiagnosticGroups();

    if (config.shouldSaveAfterStage1() || config.shouldContinueCompilation()) {
      if (options.isChecksOnly()) {
        throw new FlagUsageException(
            "checks_only mode is incompatible with multi-stage compilation");
      }
      if (options.getExternExportsPath() != null) {
        throw new FlagUsageException(
            "generating externs from exports is incompatible with multi-stage compilation");
      }
    }

    setWarningGuardOptions(options, config.warningGuards, diagnosticGroups);

    if (!config.warningsAllowFile.isEmpty()) {
      addAllowlistWarningsGuard(options, new File(config.warningsAllowFile));
    }

    if (!config.hideWarningsFor.isEmpty()) {
      options.addWarningsGuard(
          new ShowByPathWarningsGuard(
              config.hideWarningsFor.toArray(new String[] {}),
              ShowByPathWarningsGuard.ShowType.EXCLUDE));
    }

    List<String> define = new ArrayList<>(config.define);
    if (config.browserFeaturesetYear != 0) {
      try {
        options.setBrowserFeaturesetYear(config.browserFeaturesetYear);
      } catch (IllegalStateException e) {
        throw new FlagUsageException(e.getMessage());
      }
    }

    createDefineReplacements(define, options);

    options.setTweakProcessing(config.tweakProcessing);

    // TODO(tjgq): Unconditionally set the options.
    if (config.dependencyOptions != null) {
      options.setDependencyOptions(config.dependencyOptions);
    }

    options.setDevMode(config.jscompDevMode);
    options.setCodingConvention(config.codingConvention);
    options.setSummaryDetailLevel(config.summaryDetailLevel);
    options.setTrustedStrings(true);

    legacyOutputCharset = getLegacyOutputCharset();
    options.setOutputCharset(legacyOutputCharset);
    outputCharset2 = getOutputCharset2();
    inputCharset = getInputCharset();

    if (config.jsOutputFile.length() > 0) {
      if (config.skipNormalOutputs) {
        throw new FlagUsageException(
            "skip_normal_outputs and js_output_file cannot be used together.");
      }
    }

    if (config.skipNormalOutputs && config.printAst) {
      throw new FlagUsageException("skip_normal_outputs and print_ast cannot be used together.");
    }

    if (config.skipNormalOutputs && config.printTree) {
      throw new FlagUsageException("skip_normal_outputs and print_tree cannot be used together.");
    }

    if (config.skipNormalOutputs && config.printTreeJson) {
      throw new FlagUsageException(
          "skip_normal_outputs and print_tree_json_path cannot be used together.");
    }

    if (config.createSourceMap.length() > 0) {
      options.setSourceMapOutputPath(config.createSourceMap);
    } else if (isOutputInJson()) {
      options.setSourceMapOutputPath("%outname%");
    }
    options.setSourceMapDetailLevel(config.sourceMapDetailLevel);
    options.setSourceMapFormat(config.sourceMapFormat);
    options.setSourceMapLocationMappings(config.sourceMapLocationMappings);
    options.setParseInlineSourceMaps(config.parseInlineSourceMaps);
    options.setApplyInputSourceMaps(config.applyInputSourceMaps);

    ImmutableMap.Builder<String, SourceMapInput> inputSourceMaps = new ImmutableMap.Builder<>();
    for (Map.Entry<String, String> files : config.sourceMapInputFiles.entrySet()) {
      SourceFile sourceMap =
          SourceFile.builder().withKind(SourceKind.NON_CODE).withPath(files.getValue()).build();
      inputSourceMaps.put(files.getKey(), new SourceMapInput(sourceMap));
    }
    options.setInputSourceMaps(inputSourceMaps.buildOrThrow());

    if (!config.variableMapInputFile.isEmpty()) {
      options.setInputVariableMap(VariableMap.load(config.variableMapInputFile));
    }

    if (!config.propertyMapInputFile.isEmpty()) {
      options.setInputPropertyMap(VariableMap.load(config.propertyMapInputFile));
    }

    if (!config.outputManifests.isEmpty()) {
      Set<String> uniqueNames = new LinkedHashSet<>();
      for (String filename : config.outputManifests) {
        if (!uniqueNames.add(filename)) {
          throw new FlagUsageException(
              "output_manifest flags specify duplicate file names: " + filename);
        }
      }
    }

    if (!config.outputBundles.isEmpty()) {
      Set<String> uniqueNames = new LinkedHashSet<>();
      for (String filename : config.outputBundles) {
        if (!uniqueNames.add(filename)) {
          throw new FlagUsageException(
              "output_bundle flags specify duplicate file names: " + filename);
        }
      }
    }

    options.setProcessCommonJSModules(config.processCommonJSModules);
    options.setModuleRoots(config.moduleRoots);
    options.setAngularPass(config.angularPass);

    if (!config.jsonWarningsFile.isEmpty()) {
      options.addReportGenerator(
          new JsonErrorReportGenerator(new PrintStream(config.jsonWarningsFile), compiler));
    }

    if (config.errorFormat == CommandLineConfig.ErrorFormatOption.JSON) {
      JsonErrorReportGenerator errorGenerator =
          new JsonErrorReportGenerator(getErrorPrintStream(), compiler);
      compiler.setErrorManager(new SortingErrorManager(ImmutableSet.of(errorGenerator)));
    }
    if (config.printTree || config.printTreeJson) {
      options.setParseJsDocDocumentation(Config.JsDocParsing.INCLUDE_ALL_COMMENTS);
    }
    if (config.skipNormalOutputs) {
      // If skipping normal outputs, it's unnecessary to do a full AST parse of each input file.
      // The regex parser may still run if ordering/pruning inputs.
      compiler.setPreferRegexParser(true);
    }
  }

  protected final A getCompiler() {
    return compiler;
  }

  /**
   * @return a mutable list
   */
  public static List<SourceFile> getBuiltinExterns(CompilerOptions.Environment env)
      throws IOException {
    try (InputStream input = getExternsInput()) {
      ZipInputStream zip = new ZipInputStream(input);
      String envPrefix = Ascii.toLowerCase(env.toString()) + "/";
      Map<String, SourceFile> mapFromExternsZip = new LinkedHashMap<>();
      for (ZipEntry entry = null; (entry = zip.getNextEntry()) != null; ) {
        String filename = entry.getName();

        // Always load externs in the root folder.
        // If the non-core-JS externs are organized in subfolders, only load
        // the ones in a subfolder matching the specified environment. Strip the subfolder.
        if (filename.contains("/")) {
          if (!filename.startsWith(envPrefix)) {
            continue;
          }
          filename = filename.substring(envPrefix.length()); // remove envPrefix, including '/'
        }

        BufferedInputStream entryStream =
            new BufferedInputStream(ByteStreams.limit(zip, entry.getSize()));
        mapFromExternsZip.put(
            filename,
            // Give the files an odd prefix, so that they do not conflict
            // with the user's files.
            SourceFile.builder()
                .withPath("externs.zip//" + filename)
                .withContent(entryStream)
                .build());
      }
      return DefaultExterns.prepareExterns(env, mapFromExternsZip);
    }
  }

  /**
   * Some text identifying this binary and its version.
   *
   * <p>At minimum, this is what will be printed when `--version` is passed.
   */
  protected abstract String getVersionText();

  private static InputStream getExternsInput() {
    InputStream input = AbstractCommandLineRunner.class.getResourceAsStream("/externs.zip");
    if (input == null) {
      // In some environments, the externs.zip is relative to this class.
      input = AbstractCommandLineRunner.class.getResourceAsStream("externs.zip");
    }
    checkNotNull(input);
    return input;
  }

  /** Runs the Compiler and calls System.exit() with the exit status of the compiler. */
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
  protected final PrintStream getErrorPrintStream() {
    return err;
  }

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
   * @param files A list of flag entries indicates js and zip file names.
   * @param allowStdIn Whether '-' is allowed appear as a filename to represent stdin. If true, '-'
   *     is only allowed to appear once.
   * @param jsChunkSpecs A list chunk specs.
   * @return An array of inputs
   */
  private List<SourceFile> createInputs(
      List<FlagEntry<JsSourceType>> files, boolean allowStdIn, List<JsChunkSpec> jsChunkSpecs)
      throws IOException {
    return createInputs(files, /* jsonFiles= */ null, allowStdIn, jsChunkSpecs);
  }

  /**
   * Creates inputs from a list of source files and json files.
   *
   * @param files A list of flag entries indicates js and zip file names.
   * @param jsonFiles A list of json encoded files.
   * @param jsChunkSpecs A list chunk specs.
   * @return An array of inputs
   */
  private List<SourceFile> createInputs(
      List<FlagEntry<JsSourceType>> files,
      List<JsonFileSpec> jsonFiles,
      List<JsChunkSpec> jsChunkSpecs)
      throws IOException {
    return createInputs(files, jsonFiles, /* allowStdIn= */ false, jsChunkSpecs);
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
   * @param jsChunkSpecs A list chunk specs.
   * @return An array of inputs
   */
  protected List<SourceFile> createInputs(
      List<FlagEntry<JsSourceType>> files,
      @Nullable List<JsonFileSpec> jsonFiles,
      boolean allowStdIn,
      List<JsChunkSpec> jsChunkSpecs)
      throws IOException {
    List<SourceFile> inputs = new ArrayList<>(files.size());
    boolean usingStdin = false;
    int jsChunkIndex = 0;
    JsChunkSpec jsChunkSpec = Iterables.getFirst(jsChunkSpecs, null);
    int cumulatedInputFilesExpected =
        jsChunkSpec == null ? Integer.MAX_VALUE : jsChunkSpec.getNumInputs();
    for (int i = 0; i < files.size(); i++) {
      FlagEntry<JsSourceType> file = files.get(i);
      String filename = file.value;
      if (file.flag == JsSourceType.JS_ZIP) {
        if (this.config.typedAstListInputFilename != null) {
          throw new FlagUsageException("Can't use TypedASTs with --zip.");
        }

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
          if (jsChunkSpec != null) {
            jsChunkSpec.numJsFiles += newFiles.size() - 1;
          }
        }
      } else if (!"-".equals(filename)) {
        SourceKind kind = file.flag == JsSourceType.WEAKDEP ? SourceKind.WEAK : SourceKind.STRONG;
        SourceFile newFile =
            SourceFile.builder()
                .withPath(filename)
                .withCharset(inputCharset)
                .withKind(kind)
                .build();
        inputs.add(newFile);
      } else {
        if (this.config.typedAstListInputFilename != null) {
          throw new FlagUsageException("Can't use TypedASTs with stdin.");
        }
        if (!config.defaultToStdin) {
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
        inputs.add(
            SourceFile.builder()
                .withPath("stdin")
                .withContent(this.in)
                .withCharset(inputCharset)
                .build());
        usingStdin = true;
      }
      if (i >= cumulatedInputFilesExpected - 1) {
        jsChunkIndex++;
        if (jsChunkIndex < jsChunkSpecs.size()) {
          jsChunkSpec = jsChunkSpecs.get(jsChunkIndex);
          cumulatedInputFilesExpected += jsChunkSpec.getNumInputs();
        }
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
   * Removes input --ijs files whose basename matches an input --js or --weakdep file.
   *
   * @throws FlagUsageException If there are both input --ijs files and chunk specs.
   */
  private ImmutableList<JSError> deduplicateIjsFiles(
      List<FlagEntry<JsSourceType>> files, List<String> moduleRoots, boolean hasChunkSpecs) {
    ImmutableList.Builder<JSError> errors = ImmutableList.builder();

    // First pass: collect the (module root relative) names of --js and --weakdep files.
    Map<String, String> relativeToAbsoluteName = new LinkedHashMap<>();
    for (FlagEntry<JsSourceType> file : files) {
      // TODO(tjgq): Handle zip files.
      if (file.flag == JsSourceType.JS || file.flag == JsSourceType.WEAKDEP) {
        String absoluteName = file.value;
        String relativeName = getModuleRootRelativeName(absoluteName, moduleRoots);
        relativeToAbsoluteName.put(relativeName, absoluteName);
      }
    }

    // Second pass: drop --ijs files whose (module root relative) name matches a --js or --weakdep
    // file.
    Iterator<FlagEntry<JsSourceType>> iterator = files.iterator();
    while (iterator.hasNext()) {
      FlagEntry<JsSourceType> file = iterator.next();
      if (file.flag == JsSourceType.IJS) {
        if (hasChunkSpecs) {
          throw new FlagUsageException("--ijs is incompatible with --chunk or --module.");
        }
        String absoluteName = file.value;
        if (!absoluteName.endsWith(".i.js")) {
          errors.add(JSError.make(IjsErrors.BAD_IJS_FILE_NAME, absoluteName));
          continue;
        }
        String relativeName = getModuleRootRelativeName(absoluteName, moduleRoots);
        String relativeNonIjsName =
            relativeName.substring(0, relativeName.length() - ".i.js".length());
        if (relativeToAbsoluteName.containsKey(relativeNonIjsName)) {
          errors.add(
              JSError.make(
                  IjsErrors.CONFLICTING_IJS_FILE,
                  relativeToAbsoluteName.get(relativeNonIjsName),
                  absoluteName));
          iterator.remove();
        }
      }
    }

    return errors.build();
  }

  private String getModuleRootRelativeName(String filename, List<String> moduleRoots) {
    for (String moduleRoot : moduleRoots) {
      if (filename.startsWith(moduleRoot + "/")) {
        return filename.substring(moduleRoot.length() + 1);
      }
    }
    return filename;
  }

  /** Creates JS source code inputs from a list of files. */
  private @Nullable List<SourceFile> createSourceInputs(
      List<JsChunkSpec> jsChunkSpecs,
      List<FlagEntry<JsSourceType>> files,
      List<JsonFileSpec> jsonFiles,
      List<String> moduleRoots)
      throws IOException {
    if (isInTestMode()) {
      return inputsSupplierForTesting != null ? inputsSupplierForTesting.get() : null;
    }
    if (files.isEmpty() && jsonFiles == null && config.defaultToStdin) {
      // Request to read from stdin.
      files = ImmutableList.of(new FlagEntry<JsSourceType>(JsSourceType.JS, "-"));
    }

    for (JSError error : deduplicateIjsFiles(files, moduleRoots, !jsChunkSpecs.isEmpty())) {
      compiler.report(error);
    }

    try {
      if (jsonFiles != null) {
        return createInputs(files, jsonFiles, jsChunkSpecs);
      } else {
        return createInputs(files, true, jsChunkSpecs);
      }
    } catch (FlagUsageException e) {
      throw new FlagUsageException("Bad --js flag. " + e.getMessage());
    }
  }

  /** Creates JS extern inputs from a list of files. */
  private List<SourceFile> createExternInputs(List<String> files) throws IOException {
    List<FlagEntry<JsSourceType>> externFiles = new ArrayList<>();
    for (String file : files) {
      externFiles.add(new FlagEntry<JsSourceType>(JsSourceType.EXTERN, file));
    }
    try {
      return createInputs(externFiles, false, new ArrayList<JsChunkSpec>());
    } catch (FlagUsageException e) {
      throw new FlagUsageException("Bad --externs flag. " + e.getMessage());
    }
  }

  /**
   * Creates chunk objects from a list of chunk specifications.
   *
   * @param specs A list of chunk specifications, not null or empty.
   * @param inputs A list of JS file paths, not null
   * @return An array of chunk objects
   */
  public static List<JSChunk> createJsChunks(List<JsChunkSpec> specs, List<CompilerInput> inputs) {
    checkState(specs != null);
    checkState(!specs.isEmpty());
    checkState(inputs != null);

    List<String> chunkNames = new ArrayList<>(specs.size());
    Map<String, JSChunk> chunksByName = new LinkedHashMap<>();
    Map<String, Integer> chunksFileCountMap = new LinkedHashMap<>();
    int numJsFilesExpected = 0;
    int minJsFilesRequired = 0;
    for (JsChunkSpec spec : specs) {
      if (chunksByName.containsKey(spec.name)) {
        throw new FlagUsageException("Duplicate chunk name: " + spec.name);
      }
      JSChunk chunk = new JSChunk(spec.name);

      for (String dep : spec.deps) {
        JSChunk other = chunksByName.get(dep);
        if (other == null) {
          throw new FlagUsageException(
              "Chunk '"
                  + spec.name
                  + "' depends on unknown chunk '"
                  + dep
                  + "'. Be sure to list chunks in dependency order.");
        }
        chunk.addDependency(other);
      }

      // We will allow chunks of zero input.
      if (spec.numJsFiles < 0) {
        numJsFilesExpected = -1;
      } else {
        minJsFilesRequired += spec.numJsFiles;
      }

      if (numJsFilesExpected >= 0) {
        numJsFilesExpected += spec.numJsFiles;
      }

      // Add chunks in reverse order so that source files are allocated to
      // chunks in reverse order. This allows the first chunk
      // (presumably the base chunk) to have a size of 'auto'
      chunkNames.add(0, spec.name);
      chunksFileCountMap.put(spec.name, spec.numJsFiles);
      chunksByName.put(spec.name, chunk);
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
    int chunkIndex = 0;
    for (String chunkName : chunkNames) {
      // Parse chunk inputs.
      int numJsFiles = chunksFileCountMap.get(chunkName);
      JSChunk chunk = chunksByName.get(chunkName);

      // Check if the first chunk specified 'auto' for the number of files
      if (chunkIndex == chunkNames.size() - 1 && numJsFiles == -1) {
        numJsFiles = numJsFilesLeft;
      }

      List<CompilerInput> chunkFiles = inputs.subList(numJsFilesLeft - numJsFiles, numJsFilesLeft);
      for (CompilerInput input : chunkFiles) {
        chunk.add(input);
      }
      numJsFilesLeft -= numJsFiles;
      chunkIndex++;
    }

    return new ArrayList<>(chunksByName.values());
  }

  /**
   * Validates the chunk name. Can be overridden by subclasses.
   *
   * @param name The chunk name
   */
  protected void checkChunkName(String name) {
    if (!TokenStream.isJSIdentifier(name)) {
      throw new FlagUsageException("Invalid chunk name: '" + name + "'");
    }
  }

  /**
   * Parses chunk wrapper specifications.
   *
   * @param specs A list of chunk wrapper specifications, not null. The spec format is: <code>
   *     name:wrapper</code>. Wrappers.
   * @param chunks The JS chunks whose wrappers are specified
   * @return A map from chunk name to chunk wrapper. Chunks with no wrapper will have the empty
   *     string as their value in this map.
   */
  public static Map<String, String> parseChunkWrappers(
      List<String> specs, Iterable<JSChunk> chunks) {
    checkState(specs != null);

    Map<String, String> wrappers = new LinkedHashMap<>();

    // Prepopulate the map with chunk names.
    for (JSChunk c : chunks) {
      wrappers.put(c.getName(), "");
    }

    for (String spec : specs) {
      // Format is "<name>:<wrapper>".
      int pos = spec.indexOf(':');
      if (pos == -1) {
        throw new FlagUsageException(
            "Expected chunk wrapper to have " + "<name>:<wrapper> format: " + spec);
      }

      // Parse chunk name.
      String name = spec.substring(0, pos);
      if (!wrappers.containsKey(name)) {
        throw new FlagUsageException("Unknown chunk: '" + name + "'");
      }
      String wrapper = spec.substring(pos + 1);
      // Support for %n% and %output%
      wrapper = wrapper.replace("%output%", "%s").replace("%n%", "\n");
      if (!wrapper.contains("%s")) {
        throw new FlagUsageException("No %s placeholder in chunk wrapper: '" + wrapper + "'");
      }

      wrappers.put(name, wrapper);
    }
    return wrappers;
  }

  /**
   * Parses chunk output name specifications.
   *
   * @param specs A list of chunk output name specifications, not null. The spec format is: {@code
   *     name:output_file}.
   * @return A map from chunk name to chunk output file name, if declared. Chunks with no
   *     predeclared output file name will have no entry in this map.
   */
  private static ImmutableMap<String, String> parseChunkOutputFiles(List<String> specs) {
    checkArgument(specs != null);

    ImmutableMap.Builder<String, String> outputFilesBuilder = ImmutableMap.builder();

    for (String spec : specs) {
      // Format is "<name>:<output_file>".
      int pos = spec.indexOf(':');
      if (pos == -1) {
        throw new FlagUsageException(
            "Expected chunk_output_file to have " + "<name>:<output_file> format: " + spec);
      }

      String name = spec.substring(0, pos);
      String filename = spec.substring(pos + 1);
      outputFilesBuilder.put(name, filename);
    }
    return outputFilesBuilder.buildOrThrow();
  }

  /**
   * Returns the output file name for a chunk.
   *
   * <p>For chunks with predeclared output file names specified using {@code --chunk_output_file},
   * the output file name is {@code <outputPathPrefix>/<output_file>}
   *
   * <p>Otherwise, the output file name is {@code <outputPathPrefix>/<chunkName>.js}
   */
  @VisibleForTesting
  String getChunkOutputFileName(JSChunk m) {
    if (parsedChunkOutputFiles == null) {
      parsedChunkOutputFiles = parseChunkOutputFiles(config.chunkOutputFiles);
    }
    String outputFile = parsedChunkOutputFiles.get(m.getName());
    if (outputFile != null) {
      return config.chunkOutputPathPrefix + outputFile;
    }
    return config.chunkOutputPathPrefix + m.getName() + ".js";
  }

  /** Returns the conformance file name for a chunk. */
  @VisibleForTesting
  String getChunkConformanceFileName(JSChunk m) {
    if (parsedChunkConformanceFiles == null) {
      parsedChunkConformanceFiles = parseChunkOutputFiles(config.chunkConformanceFiles);
    }
    return parsedChunkConformanceFiles.get(m.getName());
  }

  @VisibleForTesting
  void writeChunkOutput(String fileName, Appendable out, LicenseTracker lt, JSChunk m)
      throws IOException {
    if (parsedChunkWrappers == null) {
      parsedChunkWrappers =
          parseChunkWrappers(
              config.chunkWrapper, ImmutableList.copyOf(compiler.getChunkGraph().getAllChunks()));
    }

    if (!isOutputInJson()) {
      maybeCreateDirsForPath(fileName);
    }
    String baseName = new File(fileName).getName();
    writeOutput(
        out,
        compiler,
        lt,
        m,
        parsedChunkWrappers.get(m.getName()).replace("%basename%", baseName),
        "%s",
        null,
        fileName);
  }

  /**
   * Writes code to an output stream, optionally wrapping it in an arbitrary wrapper that contains a
   * placeholder where the code should be inserted.
   *
   * @param chunk Which chunk to write. If this is null, write the entire AST.
   */
  void writeOutput(
      Appendable out,
      Compiler compiler,
      LicenseTracker licenseTracker,
      @Nullable JSChunk chunk,
      String wrapper,
      String codePlaceholder,
      @Nullable Function<String, String> escaper,
      String filename)
      throws IOException {
    if (compiler.getOptions().getOutputJs() == OutputJs.SENTINEL) {
      out.append("// No JS output because the compiler was run in checks-only mode.\n");
      return;
    }
    checkState(compiler.getOptions().getOutputJs() == OutputJs.NORMAL);

    String code = chunk == null ? compiler.toSource() : compiler.toSource(licenseTracker, chunk);
    writeOutput(out, compiler, code, wrapper, codePlaceholder, escaper, filename);
  }

  /**
   * Writes code to an output stream, optionally wrapping it in an arbitrary wrapper that contains a
   * placeholder where the code should be inserted.
   */
  @VisibleForTesting
  void writeOutput(
      Appendable out,
      Compiler compiler,
      String code,
      String wrapper,
      String codePlaceholder,
      @Nullable Function<String, String> escaper,
      String filename)
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
      if (getCommandLineConfig().includeTrailingNewline) {
        out.append('\n');
      }

      // If we have a source map, adjust its offsets to match
      // the code WITHIN the wrapper.
      if (compiler != null && compiler.getSourceMap() != null) {
        compiler.getSourceMap().setWrapperPrefix(prefix);
      }
    } else {
      out.append(code);
      if (getCommandLineConfig().includeTrailingNewline) {
        out.append('\n');
      }
    }
  }

  /** Creates any directories necessary to write a file that will have a given path prefix. */
  private static void maybeCreateDirsForPath(String pathPrefix) {
    if (!Strings.isNullOrEmpty(pathPrefix)) {
      File parent = new File(pathPrefix).getParentFile();
      if (parent != null) {
        parent.mkdirs();
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
    if (output instanceof Flushable flushable) {
      flushable.flush();
    }
    if (output instanceof Closeable closeable) {
      closeable.close();
    }
  }

  /**
   * Parses command-line arguments and runs the compiler.
   *
   * @return system exit status
   */
  protected int doRun() throws IOException {
    CompileMetricsRecorderInterface metricsRecorder = getCompileMetricsRecorder();
    metricsRecorder.recordActionStart();
    if (this.config.printVersion) {
      this.defaultJsOutput.println(this.getVersionText());
      this.defaultJsOutput.flush();
      return 0;
    }

    Compiler.setLoggingLevel(Level.parse(config.loggingLevel));

    compiler = createCompiler();
    B options = createOptions();
    setRunOptions(options);

    @Nullable String typedAstListInputFilename = config.typedAstListInputFilename;

    List<SourceFile> externs = createExterns(options);
    List<JSChunk> chunks = null;

    rootRelativePathsMap = constructRootRelativePathsMap();

    boolean writeOutputToFile = !config.jsOutputFile.isEmpty();
    List<String> outputFileNames = new ArrayList<>();
    if (writeOutputToFile) {
      outputFileNames.add(config.jsOutputFile);
    }

    boolean createCommonJsModules = false;
    if (options.getProcessCommonJSModules()
        && (config.chunk.size() == 1 && Objects.equals(config.chunk.get(0), "auto"))) {
      createCommonJsModules = true;
      config.chunk.remove(0);
    }

    List<JsChunkSpec> jsChunkSpecs = new ArrayList<>();
    for (int i = 0; i < config.chunk.size(); i++) {
      jsChunkSpecs.add(JsChunkSpec.create(config.chunk.get(i), i == 0));
    }
    List<JsonFileSpec> jsonFiles = null;

    if (config.jsonStreamMode == JsonStreamMode.IN
        || config.jsonStreamMode == JsonStreamMode.BOTH) {
      jsonFiles = parseJsonFilesFromInputStream();

      ImmutableMap.Builder<String, SourceMapInput> inputSourceMaps = new ImmutableMap.Builder<>();
      ImmutableMap.Builder<String, String> inputPathByWebpackId = new ImmutableMap.Builder<>();

      boolean foundJsonInputSourceMap = false;
      for (JsonFileSpec jsonFile : jsonFiles) {
        if (jsonFile.getSourceMap() != null && jsonFile.getSourceMap().length() > 0) {
          String sourceMapPath = jsonFile.getPath() + ".map";
          SourceFile sourceMap = SourceFile.fromCode(sourceMapPath, jsonFile.getSourceMap());
          inputSourceMaps.put(jsonFile.getPath(), new SourceMapInput(sourceMap));
          foundJsonInputSourceMap = true;
        }
        if (jsonFile.getWebpackId() != null) {
          inputPathByWebpackId.put(jsonFile.getWebpackId(), jsonFile.getPath());
        }
      }

      if (foundJsonInputSourceMap) {
        inputSourceMaps.putAll(options.getInputSourceMaps());
        options.setInputSourceMaps(inputSourceMaps.buildOrThrow());
      }

      compiler.initWebpackMap(inputPathByWebpackId.buildOrThrow());
    } else {
      ImmutableMap<String, String> emptyMap = ImmutableMap.of();
      compiler.initWebpackMap(emptyMap);
    }

    options.setDoLateLocalization(config.shouldDoLateLocalization());
    options.setAlwaysGatherSourceMapInfo(config.shouldAlwaysGatherSourceMapInfo());

    compiler.initOptions(options);

    List<SourceFile> sources =
        createSourceInputs(jsChunkSpecs, config.mixedJsSources, jsonFiles, config.moduleRoots);

    if (!jsChunkSpecs.isEmpty()) {
      if (isInTestMode()) {
        chunks = chunksSupplierForTesting.get();
      } else {
        if (JsonStreamMode.IN.equals(config.jsonStreamMode)
            || JsonStreamMode.NONE.equals(config.jsonStreamMode)) {
          for (JsChunkSpec m : jsChunkSpecs) {
            checkChunkName(m.getName());
          }
        }
        ImmutableList.Builder<CompilerInput> inputs = ImmutableList.builder();
        for (SourceFile source : sources) {
          inputs.add(new CompilerInput(source, /* isExtern= */ false));
        }
        chunks = createJsChunks(jsChunkSpecs, inputs.build());
      }
      for (JSChunk m : chunks) {
        outputFileNames.add(getChunkOutputFileName(m));
      }

      if (typedAstListInputFilename != null) {
        this.initChunksWithTypedAstFilesystem(externs, chunks, options, typedAstListInputFilename);
      } else {
        compiler.initChunks(externs, chunks, options);
      }
    } else {
      if (typedAstListInputFilename != null) {
        this.initWithTypedAstFilesystem(externs, sources, options, typedAstListInputFilename);
      } else {
        compiler.init(externs, sources, options);
      }
    }

    // Release temporary data structures now that the compiler has
    // been initialized
    jsChunkSpecs = null;
    jsonFiles = null;
    externs = null;
    sources = null;

    final Result result;
    if (config.skipNormalOutputs) {
      metricsRecorder.recordActionName("skip normal outputs");
      // TODO(bradfordcsmith): Should we be ignoring possible init/initChunks() errors here?
      compiler.orderInputsWithLargeStack();
      result = null;
    } else if (compiler.hasErrors()) {
      metricsRecorder.recordActionName("initialization errors occurred");
      // init() or initChunks() encountered an error.
      compiler.generateReport();
      result = compiler.getResult();
    } else {
      try {
        // This is the common case - we're actually compiling something.
        performCompilation(metricsRecorder);
        result = compiler.getResult();
        // If we're finished with compilation (i.e. we're not saving state), /and/ the compiler was
        // restored from a previous state, then we need to re-initialize the set of chunks.
        // TODO(lharker): figure out if this is still needed.
        boolean refresh =
            chunks != null
                && config.restoredCompilationStage != -1
                && config.saveAfterCompilationStage == -1;
        if (refresh) {
          chunks = ImmutableList.copyOf(compiler.getChunks());
        }
      } finally {
        // Make sure we generate a report of errors and warnings even if the compiler throws an
        // exception somewhere.
        compiler.generateReport();
      }
    }

    if (createCommonJsModules) {
      // For CommonJS modules, construct chunks from actual inputs.
      chunks = ImmutableList.copyOf(compiler.getChunks());
      for (JSChunk c : chunks) {
        outputFileNames.add(getChunkOutputFileName(c));
      }
    }

    for (String outputFileName : outputFileNames) {
      if (compiler.getSourceFileByName(outputFileName) != null) {
        compiler.report(JSError.make(OUTPUT_SAME_AS_INPUT_ERROR, outputFileName));
        return 1;
      }
    }

    // We won't want to process results for cases where compilation is only partially done.
    boolean shouldProcessResults = config.getSaveCompilationStateToFilename() == null;
    int exitStatus =
        shouldProcessResults
            ? processResults(result, chunks, options)
            : getExitStatusForResult(result);
    metricsRecorder.recordResultMetrics(compiler, result);
    return exitStatus;
  }

  private void performCompilation(CompileMetricsRecorderInterface metricsRecorder) {
    // Parse, restore from a save file, or initialize from a TypedAST list.
    initializeStateBeforeCompilation();

    // Do the interesting work - checks, optimizations, etc.
    runCompilerPasses(metricsRecorder);

    // Save state unless there was an exception or error - we won't go on to the next stage if there
    // was an error, and saving state itself might throw an exception if the compiler is in a bad
    // state.
    if (!compiler.hasErrors()) {
      saveState();
    }
    // Perform post-compilation tasks even if compiler.hasErrors()
    compiler.performPostCompilationTasks();
  }

  private void runCompilerPasses(CompileMetricsRecorderInterface metricsRecorder) {
    boolean runStage1 = false;
    boolean runStage2 = false;
    boolean runStage3 = false;
    boolean instrumentForCoverage = false;
    final String actionMetricsName;
    if (compiler.getOptions().getInstrumentForCoverageOnly()) {
      actionMetricsName = "instrument for coverage";
      instrumentForCoverage = true;
    } else if (config.shouldSaveAfterStage1()) {
      actionMetricsName = "stage 1";
      runStage1 = true;
    } else if (config.shouldRestoreTypedAstsPerformStage2AndSave()) {
      actionMetricsName = "parse & optimize";
      runStage2 = true;
    } else if (config.shouldRestoreTypedAstsPerformStages2And3()) {
      actionMetricsName = "skip-checks compile";
      runStage2 = true;
      runStage3 = true;
    } else if (config.shouldRestoreAndPerformStage2AndSave()) {
      actionMetricsName = "stage 2/3";
      runStage2 = true;
    } else if (config.shouldRestoreAndPerformStages2And3()) {
      // From the outside this looks like the second stage of a 2-stage compile.
      actionMetricsName = "stage 2/2";
      runStage2 = true;
      runStage3 = true;
    } else if (config.shouldRestoreAndPerformStage3()) {
      actionMetricsName = "stage 3/3";
      runStage3 = true;
    } else {
      // This is the code path taken when "building" a library by just checking it for errors
      // and generating an .ijs file and also when doing a full compilation.
      actionMetricsName = compiler.getOptions().isChecksOnly() ? "checks-only" : "full compile";
      runStage1 = true;
      runStage2 = true;
      runStage3 = true;
    }

    metricsRecorder.recordActionName(actionMetricsName);
    if (compiler.hasErrors()) {
      return;
    }
    metricsRecorder.recordStartState(compiler);
    if (runStage1) {
      compiler.stage1Passes();
      if (compiler.hasErrors()) {
        return;
      }
    }
    if (runStage2) {
      compiler.stage2Passes(SegmentOfCompilationToRun.OPTIMIZATIONS);
      if (compiler.hasErrors()) {
        return;
      }
    }
    if (runStage3) {
      compiler.stage3Passes();
      if (compiler.hasErrors()) {
        return;
      }
    }
    if (instrumentForCoverage) {
      compiler.instrumentForCoverage();
    }
  }

  /**
   * Child classes should override this if they want to actually record metrics about the
   * compilation.
   */
  protected CompileMetricsRecorderInterface getCompileMetricsRecorder() {
    return new DummyCompileMetricsRecorder();
  }

  private void initWithTypedAstFilesystem(
      List<SourceFile> externs,
      List<SourceFile> sources,
      CompilerOptions options,
      String filename) {
    try (GZIPInputStream typedAstListStream =
        new GZIPInputStream(new FileInputStream(filename), GZIPPED_TYPEDAST_BUFFER_SIZE)) {
      compiler.initWithTypedAstFilesystem(externs, sources, options, typedAstListStream);
    } catch (IOException e) {
      compiler.report(JSError.make(COULD_NOT_DESERIALIZE_AST, filename));
    }
  }

  private void initChunksWithTypedAstFilesystem(
      List<SourceFile> externs, List<JSChunk> chunks, CompilerOptions options, String filename) {
    try (GZIPInputStream typedAstListStream =
        new GZIPInputStream(new FileInputStream(filename), GZIPPED_TYPEDAST_BUFFER_SIZE)) {
      compiler.initChunksWithTypedAstFilesystem(externs, chunks, options, typedAstListStream);
    } catch (IOException e) {
      compiler.report(JSError.make(COULD_NOT_DESERIALIZE_AST, filename));
    }
  }

  /**
   * Call at the beginning of compilation to initialize the compiler state.
   *
   * <p>Compiler state should be initialized no matter what the compilation stage is, but this
   * method handles the different ways it might be initialized - whether from parsing actual JS
   * files, from reading library-level TypedASTs, or from restoring a previous compilation state.
   */
  private void initializeStateBeforeCompilation() {
    if (config.restoredCompilationStage != -1) {
      restoreState(config.getContinueSavedCompilationFileName());
    } else if (config.typedAstListInputFilename != null) {
      // we did this elsewhere
    } else {
      // parsing!
      compiler.parseForCompilation();
  }
  }

  private void restoreState(String filename) {
    try (BufferedInputStream serializedInputStream =
        new BufferedInputStream(new FileInputStream(filename))) {
      compiler.restoreState(serializedInputStream);
    } catch (IOException | ClassNotFoundException e) {
      compiler.report(JSError.make(COULD_NOT_DESERIALIZE_AST, filename));
    }
  }

  /** Call at the end of compilation to save the compiler state if applicable. */
  private void saveState() {
    if (config.getSaveCompilationStateToFilename() == null) {
      // nothing to save to.
      return;
    }
    String filename = config.getSaveCompilationStateToFilename();
    try (BufferedOutputStream serializedOutputStream =
        new BufferedOutputStream(new FileOutputStream(filename))) {
      compiler.saveState(serializedOutputStream);
    } catch (IOException e) {
      compiler.report(JSError.make(COULD_NOT_SERIALIZE_AST, filename));
    }
  }

  /** Processes the results of the compile job, and returns an error code. */
  int processResults(Result result, List<JSChunk> chunks, B options) throws IOException {
    if (config.printAst) {
      if (compiler.getRoot() == null) {
        return 1;
      } else {
        Appendable jsOutput = createDefaultOutput();
        ControlFlowGraph<Node> cfg = compiler.computeCFG();
        DotFormatter.appendDot(compiler.getRoot().getLastChild(), cfg, jsOutput);
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
        compiler.getJsRoot().appendStringTree(jsOutput);
        jsOutput.append("\n");
        closeAppendable(jsOutput);
        return 0;
      }
    }

    if (config.printTreeJson) {
      if (compiler.getRoot() == null) {
        compiler.report(JSError.make(NO_TREE_GENERATED_ERROR));
        return 1;
      } else {
        Appendable jsOutput = createDefaultOutput();
        compiler.getJsRoot().appendJsonTree(jsOutput);
        closeAppendable(jsOutput);
        return 0;
      }
    }

    if (config.skipNormalOutputs) {
      // Output the manifest and bundle files if requested.
      outputManifest();
      outputBundle();
      outputChunkGraphJson();
      return 0;
    } else if (options.getOutputJs() != OutputJs.NONE && result.success) {
      outputChunkGraphJson();
      if (chunks == null) {
        outputSingleBinary(options);

        // Output the source map if requested.
        // If output files are being written to stdout as a JSON string,
        // outputSingleBinary will have added the sourcemap to the output file
        if (!isOutputInJson()) {
          outputSourceMap(options, config.jsOutputFile);
        }
      } else {
        DiagnosticType error = outputChunkBinaryAndSourceMaps(compiler.getChunkGraph(), options);
        if (error != null) {
          compiler.report(JSError.make(error));
          return 1;
        }
      }

      // Output the externs if required.
      if (options.getExternExportsPath() != null) {
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

      // Output the production instrumentation param mapping if requested.
      outputInstrumentationMapping();

      if (isOutputInJson()) {
        outputJsonStream();
      }
    }

    return getExitStatusForResult(result);
  }

  private static int getExitStatusForResult(Result result) {
    // return 0 if no errors, the error count otherwise
    return min(result.errors.size(), 0x7f);
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
      if (!config.jsOutputFile.isEmpty()) {
        maybeCreateDirsForPath(config.jsOutputFile);
      }

      Appendable jsOutput = createDefaultOutput();
      writeOutput(
          jsOutput,
          compiler,
          // So long as the JSChunk arg is null the compiler will write all sources to jsOutput
          // Use single-binary license tracking to dedupe licenses among all the inputs
          new SingleBinaryLicenseTracker(compiler),
          (JSChunk) null,
          config.outputWrapper,
          marker,
          escaper,
          config.jsOutputFile);
      closeAppendable(jsOutput);
    }
  }

  /** Save the compiler output to a JsonFileSpec to be later written to stdout */
  JsonFileSpec createJsonFile(B options, String outputMarker, Function<String, String> escaper)
      throws IOException {
    Appendable jsOutput = new StringBuilder();
    writeOutput(
        jsOutput,
        compiler,
        // So long as the JSChunk arg is null the compiler will write all sources to jsOutput
        // Use single-binary license tracking to dedupe licenses among all the inputs
        new SingleBinaryLicenseTracker(compiler),
        (JSChunk) null,
        config.outputWrapper,
        outputMarker,
        escaper,
        config.jsOutputFile);

    JsonFileSpec jsonOutput =
        new JsonFileSpec(
            jsOutput.toString(),
            Strings.isNullOrEmpty(config.jsOutputFile) ? "compiled.js" : config.jsOutputFile);

    if (options.shouldGatherSourceMapInfo()) {
      StringBuilder sourcemap = new StringBuilder();
      compiler.getSourceMap().appendTo(sourcemap, jsonOutput.getPath());
      jsonOutput.setSourceMap(sourcemap.toString());
    }

    return jsonOutput;
  }

  void outputJsonStream() throws IOException {
    try (JsonWriter jsonWriter =
        new JsonWriter(new BufferedWriter(new OutputStreamWriter(defaultJsOutput, UTF_8)))) {
      Gson gsonOut = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
      Type filesCollectionType = new TypeToken<List<JsChunkSpec>>() {}.getType();
      gsonOut.toJson(this.filesToStreamOut, filesCollectionType, jsonWriter);
    }
  }

  private @Nullable DiagnosticType outputChunkBinaryAndSourceMaps(
      JSChunkGraph chunkGraph, B options) throws IOException {
    Iterable<JSChunk> chunks = chunkGraph.getAllChunks();
    parsedChunkWrappers = parseChunkWrappers(config.chunkWrapper, chunks);
    if (!isOutputInJson()) {
      // make sure the method generates all dirs up to the latest /
      maybeCreateDirsForPath(config.chunkOutputPathPrefix + "dummy");
    }

    // If the source map path is in fact a pattern for each
    // chunk, create a stream per-chunk. Otherwise, create
    // a single source map.
    Writer mapFileOut = null;

    // When the json_streams flag is specified, sourcemaps are always generated
    // per chunk
    if (!(shouldGenerateMapPerChunk(options)
        || !options.shouldGatherSourceMapInfo()
        || config.jsonStreamMode == JsonStreamMode.OUT
        || config.jsonStreamMode == JsonStreamMode.BOTH)) {
      // warn that this is not supported
      return INVALID_CHUNK_SOURCEMAP_PATTERN;
    }

    ChunkGraphAwareLicenseTracker mlicenseTracker = new ChunkGraphAwareLicenseTracker(compiler);
    for (JSChunk m : chunks) {
      if (m.getName().equals(JSChunk.WEAK_CHUNK_NAME)) {
        // Skip the weak chunk, which is always empty.
        continue;
      }
      if (isOutputInJson()) {
        this.filesToStreamOut.add(createJsonFileFromChunk(m));
      } else {
        if (shouldGenerateMapPerChunk(options)) {
          mapFileOut = fileNameToOutputWriter2(expandSourceMapPath(options, m));
        }

        String chunkFilename = getChunkOutputFileName(m);
        maybeCreateDirsForPath(chunkFilename);
        try (Writer writer = fileNameToLegacyOutputWriter(chunkFilename)) {
          if (options.shouldGatherSourceMapInfo()) {
            compiler.resetAndIntitializeSourceMap();
          }
          mlicenseTracker.setCurrentChunkContext(m);
          writeChunkOutput(chunkFilename, writer, mlicenseTracker, m);
          if (options.shouldGatherSourceMapInfo()) {
            compiler.getSourceMap().appendTo(mapFileOut, chunkFilename);
          }
        }

        if (shouldGenerateMapPerChunk(options) && mapFileOut != null) {
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

  /** Given an output chunk, convert it to a JSONFileSpec with associated sourcemap */
  private JsonFileSpec createJsonFileFromChunk(JSChunk chunk) throws IOException {
    compiler.resetAndIntitializeSourceMap();

    String filename = getChunkOutputFileName(chunk);
    StringBuilder output = new StringBuilder();
    writeChunkOutput(filename, output, new ScriptNodeLicensesOnlyTracker(compiler), chunk);

    JsonFileSpec jsonFile = new JsonFileSpec(output.toString(), filename);

    StringBuilder chunkSourceMap = new StringBuilder();

    compiler.getSourceMap().appendTo(chunkSourceMap, getChunkOutputFileName(chunk));

    jsonFile.setSourceMap(chunkSourceMap.toString());

    return jsonFile;
  }

  /**
   * Query the flag for the input charset, and return a Charset object representing the selection.
   *
   * @return Charset to use when reading inputs
   * @throws FlagUsageException if flag is not a valid Charset name.
   */
  private Charset getInputCharset() {
    if (!config.charset.isEmpty()) {
      if (!Charset.isSupported(config.charset)) {
        throw new FlagUsageException(config.charset + " is not a valid charset name.");
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
  private Charset getOutputCharset2() {
    if (!config.charset.isEmpty()) {
      if (!Charset.isSupported(config.charset)) {
        throw new FlagUsageException(config.charset + " is not a valid charset name.");
      }
      return Charset.forName(config.charset);
    }
    return UTF_8;
  }

  protected List<SourceFile> createExterns(CompilerOptions options) throws IOException {
    return isInTestMode() ? externsSupplierForTesting.get() : createExternInputs(config.externs);
  }

  /**
   * Returns true if and only if a source map file should be generated for each chunk, as opposed to
   * one unified map. This is specified by having the source map pattern include the %outname%
   * variable.
   */
  protected boolean shouldGenerateMapPerChunk(B options) {
    return options.shouldGatherSourceMapInfo()
        && options.getSourceMapOutputPath().contains("%outname%");
  }

  /**
   * Returns a stream for outputting the generated externs file.
   *
   * @param options The options to the Compiler.
   * @param path The path of the generated JS source file.
   * @return The stream or null if no extern-ed exports are being generated.
   */
  private @Nullable Writer openExternExportsStream(B options, String path) throws IOException {
    final String externExportsPath = options.getExternExportsPath();
    if (externExportsPath == null) {
      return null;
    }

    String exPath = externExportsPath;

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
   * extra output: sub in the base chunk name. 3) Multiple JS output, multiple extra output: sub in
   * the module output file.
   *
   * <p>Passing a JSChunk to this function automatically triggers case #3. Otherwise, we'll use
   * strategy #1 or #2 based on the current output mode.
   */
  private String expandCommandLinePath(String path, @Nullable JSChunk forChunk) {
    String sub;
    if (forChunk != null) {
      sub = getChunkOutputFileName(forChunk);
    } else if (!config.chunk.isEmpty()) {
      sub = config.chunkOutputPathPrefix;
    } else {
      sub = config.jsOutputFile;
    }
    return path.replace("%outname%", sub);
  }

  /** Expansion function for source map. */
  @VisibleForTesting
  @Nullable String expandSourceMapPath(B options, @Nullable JSChunk forChunk) {
    if (!options.shouldGatherSourceMapInfo()) {
      return null;
    }
    return expandCommandLinePath(options.getSourceMapOutputPath(), forChunk);
  }

  /**
   * Converts a file name into a Writer taking in account the output charset. Returns null if the
   * file name is null.
   */
  private @Nullable Writer fileNameToLegacyOutputWriter(String fileName) throws IOException {
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
  private @Nullable Writer fileNameToOutputWriter2(String fileName) throws IOException {
    if (fileName == null) {
      return null;
    }
    if (isInTestMode()) {
      return new StringWriter();
    }

    return streamToOutputWriter2(filenameToOutputStream(fileName));
  }

  /** Converts a file name into a Outputstream. Returns null if the file name is null. */
  protected @Nullable OutputStream filenameToOutputStream(String fileName) throws IOException {
    if (fileName == null) {
      return null;
    }
    return new FileOutputStream(fileName);
  }

  /** Create a writer with the legacy output charset. */
  private Writer streamToLegacyOutputWriter(OutputStream stream) {
    if (legacyOutputCharset == null) {
      return createWriter(stream, UTF_8);
    } else {
      return createWriter(stream, legacyOutputCharset);
    }
  }

  /** Create a writer with the newer output charset. */
  private Writer streamToOutputWriter2(OutputStream stream) {
    if (outputCharset2 == null) {
      return createWriter(stream, UTF_8);
    } else {
      return createWriter(stream, outputCharset2);
    }
  }

  /** Creates a buffered Writer that writes to the given stream using the given encoding. */
  Writer createWriter(OutputStream stream, Charset charset) {
    return new BufferedWriter(new OutputStreamWriter(stream, charset));
  }

  /**
   * Outputs the source map found in the compiler to the proper path if one exists.
   *
   * @param options The options to the Compiler.
   */
  private void outputSourceMap(B options, String associatedName) throws IOException {
    if (!options.shouldGatherSourceMapInfo()
        || options.getSourceMapOutputPath().equals("/dev/null")) {
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
  private String getMapPath(String outputFile) {
    String basePath = "";

    if (outputFile.isEmpty()) {
      // If we have a js_module_binary rule, output the maps
      // at chunkname_props_map.out, etc.
      if (!config.chunkOutputPathPrefix.isEmpty()) {
        basePath = config.chunkOutputPathPrefix;
      } else {
        basePath = "jscompiler";
      }
    } else {
      // Get the path of the output file.
      File file = new File(outputFile);

      String outputFileName = file.getName();

      // Strip the .js from the name.
      if (outputFileName.endsWith(".js")) {
        outputFileName = outputFileName.substring(0, outputFileName.length() - 3);
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
  private void outputNameMaps() throws IOException {

    String propertyMapOutputPath = null;
    String variableMapOutputPath = null;

    // Check the create_name_map_files FLAG.
    if (config.createNameMapFiles) {
      String basePath = getMapPath(config.jsOutputFile);

      propertyMapOutputPath = basePath + "_props_map.out";
      variableMapOutputPath = basePath + "_vars_map.out";
    }

    // Check the individual FLAGS.
    if (!config.variableMapOutputFile.isEmpty()) {
      if (variableMapOutputPath != null) {
        throw new FlagUsageException(
            "The flags variable_map_output_file and "
                + "create_name_map_files cannot both be used simultaneously.");
      }

      variableMapOutputPath = config.variableMapOutputFile;
    }

    if (!config.propertyMapOutputFile.isEmpty()) {
      if (propertyMapOutputPath != null) {
        throw new FlagUsageException(
            "The flags property_map_output_file and "
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
  }

  /**
   * Outputs the string map generated by the {@link ReplaceStrings} pass if an output path exists.
   */
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
  public static void createDefineReplacements(List<String> definitions, CompilerOptions options) {
    // Parse the definitions
    for (String override : definitions) {
      String[] assignment = override.split("=", 2);
      String defName = assignment[0];

      if (defName.length() > 0) {
        String defValue = assignment.length == 1 ? "true" : assignment[1];

        boolean isTrue = defValue.equals("true");
        boolean isFalse = defValue.equals("false");
        if (isTrue || isFalse) {
          options.setDefineToBooleanLiteral(defName, isTrue);
          continue;
        } else if (defValue.length() > 1
            && ((defValue.charAt(0) == '\'' && defValue.charAt(defValue.length() - 1) == '\'')
                || (defValue.charAt(0) == '\"'
                    && defValue.charAt(defValue.length() - 1) == '\"'))) {
          // If the value starts and ends with a single quote,
          // we assume that it's a string.
          String maybeStringVal = defValue.substring(1, defValue.length() - 1);
          if (maybeStringVal.indexOf(defValue.charAt(0)) == -1) {
            options.setDefineToStringLiteral(defName, maybeStringVal);
            continue;
          }
        } else {
          try {
            double value = Double.parseDouble(defValue);
            options.setDefineToDoubleLiteral(defName, value);
            continue;
          } catch (NumberFormatException e) {
            // do nothing, it will be caught at the end
          }

          if (defValue.length() > 0) {
            options.setDefineToStringLiteral(defName, defValue);
            continue;
          }
        }
      }

      throw new RuntimeException("--define flag syntax invalid: " + override);
    }
  }

  /**
   * Returns true if and only if a manifest or bundle should be generated for each chunk, as opposed
   * to one unified manifest.
   */
  private boolean shouldGenerateOutputPerChunk(String output) {
    return !config.chunk.isEmpty() && output != null && output.contains("%outname%");
  }

  private void outputManifest() throws IOException {
    outputManifestOrBundle(config.outputManifests, true);
  }

  private void outputBundle() throws IOException {
    outputManifestOrBundle(config.outputBundles, false);
  }

  private void outputInstrumentationMapping() throws IOException {
    if (!Strings.isNullOrEmpty(config.instrumentationMappingFile)) {
      String path = expandCommandLinePath(config.instrumentationMappingFile, /* forChunk= */ null);
      compiler.getInstrumentationMapping().save(path);
    }
  }

  /**
   * Writes the manifest or bundle of all compiler input files that were included as controlled by
   * --dependency_mode, if requested.
   */
  private void outputManifestOrBundle(List<String> outputFiles, boolean isManifest)
      throws IOException {
    if (outputFiles.isEmpty()) {
      return;
    }

    for (String output : outputFiles) {
      if (output.isEmpty()) {
        continue;
      }

      if (shouldGenerateOutputPerChunk(output)) {
        // Generate per-chunk manifests or bundles.
        Iterable<JSChunk> chunks = compiler.getChunkGraph().getAllChunks();
        for (JSChunk chunk : chunks) {
          try (Writer out = fileNameToOutputWriter2(expandCommandLinePath(output, chunk))) {
            if (isManifest) {
              printManifestTo(chunk, out);
            } else {
              printBundleTo(chunk, out);
            }
          }
        }
      } else {
        // Generate a single file manifest or bundle.
        try (Writer out = fileNameToOutputWriter2(expandCommandLinePath(output, null))) {
          if (config.chunk.isEmpty()) {
            // For a single-chunk compilation, generate a single headerless manifest or bundle
            // containing only the strong files.
            JSChunk chunk = compiler.getChunkGraph().getChunkByName(JSChunk.STRONG_CHUNK_NAME);
            if (isManifest) {
              printManifestTo(chunk, out);
            } else {
              printBundleTo(chunk, out);
            }
          } else {
            // For a multi-chunk compilation, generate a single manifest file with chunk headers.
            printChunkGraphManifestOrBundleTo(compiler.getChunkGraph(), out, isManifest);
          }
        }
      }
    }
  }

  /** Creates a file containing the current chunk graph in JSON serialization. */
  private void outputChunkGraphJson() throws IOException {
    if (config.outputChunkDependencies != null && config.outputChunkDependencies.length() != 0) {
      try (Writer out = fileNameToOutputWriter2(config.outputChunkDependencies)) {
        printChunkGraphJsonTo(out);
      }
    }
  }

  /** Prints the current chunk graph as JSON. */
  @VisibleForTesting
  void printChunkGraphJsonTo(Appendable out) throws IOException {
    out.append(compiler.getChunkGraph().toJson().toString());
  }

  /** Prints a set of chunks to the manifest or bundle file. */
  @VisibleForTesting
  void printChunkGraphManifestOrBundleTo(JSChunkGraph graph, Appendable out, boolean isManifest)
      throws IOException {
    Joiner commas = Joiner.on(",");
    boolean requiresNewline = false;
    for (JSChunk chunk : graph.getAllChunks()) {
      if (!isManifest && chunk.isWeak()) {
        // Skip the weak chunk on a multi-chunk bundle, but not a multi-chunk manifest.
        continue;
      }

      if (requiresNewline) {
        out.append("\n");
      }

      if (isManifest) {
        // See CommandLineRunnerTest to see what the format of this
        // manifest looks like.
        String dependencies = commas.join(chunk.getSortedDependencyNames());
        out.append(
            String.format(
                "{%s%s}\n", chunk.getName(), dependencies.isEmpty() ? "" : ":" + dependencies));
        printManifestTo(chunk, out);
      } else {
        printBundleTo(chunk, out);
      }
      requiresNewline = true;
    }
  }

  /**
   * Prints a list of input names (using root-relative paths), delimited by newlines, to the
   * manifest file.
   */
  @VisibleForTesting
  void printManifestTo(JSChunk chunk, Appendable out) throws IOException {
    for (CompilerInput input : chunk.getInputs()) {
      String name = input.getName();

      // Ignore fill files created by the compiler to facilitate cross-chunk code motion.
      if (Compiler.isFillFileName(name)) {
        continue;
      }

      String rootRelativePath = rootRelativePathsMap.get(name);
      String displayName = rootRelativePath != null ? rootRelativePath : name;
      out.append(displayName);
      out.append("\n");
    }
  }

  /**
   * Prints all the input contents, starting with a comment that specifies the input file name
   * (using root-relative paths) before each file.
   */
  @VisibleForTesting
  void printBundleTo(JSChunk chunk, Appendable out) throws IOException {
    ImmutableList<CompilerInput> inputs = chunk.getInputs();
    CompilerOptions options = compiler.getOptions();
    // Prebuild ASTs before they're needed in getLoadFlags, for performance and because
    // StackOverflowErrors can be hit if not prebuilt.
    if (options.getNumParallelThreads() > 1) {
      new PrebuildAst(compiler, compiler.getOptions().getNumParallelThreads()).prebuild(inputs);
    }
    if (options.getRuntimeLibraryMode() == RuntimeJsLibManager.RuntimeLibraryMode.INJECT) {
      // ES6 modules will need a runtime in a bundle. Skip appending this runtime if there are no
      // ES6 modules to cut down on size.
      for (CompilerInput input : inputs) {
        if (input.isEs6Module()) {
          appendRuntimeTo(out);
          break;
        }
      }
    }

    for (CompilerInput input : inputs) {
      String name = input.getName();
      String code = input.getSourceFile().getCode();

      // Ignore empty fill files created by the compiler to facilitate cross-chunk code motion.
      // Note that non-empty fill files (ones whose code has actually been moved into) are still
      // emitted. In particular, this ensures that if there are no (real) inputs the bundle will be
      // empty.
      if (Compiler.isFillFileName(name) && code.isEmpty()) {
        continue;
      }

      String rootRelativePath = rootRelativePathsMap.get(name);
      String displayName = rootRelativePath != null ? rootRelativePath : input.getName();
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
  protected static class CommandLineConfig {

    private boolean printVersion;

    @CanIgnoreReturnValue
    CommandLineConfig setPrintVersion(boolean x) {
      this.printVersion = x;
      return this;
    }

    private boolean printTree = false;
    private boolean printTreeJson = false;

    /** Prints out the parse tree and exits */
    @CanIgnoreReturnValue
    CommandLineConfig setPrintTree(boolean printTree) {
      this.printTree = printTree;
      return this;
    }

    /** Prints out the parse tree and exits */
    @CanIgnoreReturnValue
    CommandLineConfig setPrintTreeJson(boolean printTreeJson) {
      this.printTreeJson = printTreeJson;
      return this;
    }

    private boolean printAst = false;

    /** Prints a dot file describing the internal abstract syntax tree and exits */
    @CanIgnoreReturnValue
    public CommandLineConfig setPrintAst(boolean printAst) {
      this.printAst = printAst;
      return this;
    }

    private CompilerOptions.DevMode jscompDevMode = CompilerOptions.DevMode.OFF;

    /** Turns on extra validity checks */
    @CanIgnoreReturnValue
    public CommandLineConfig setJscompDevMode(CompilerOptions.DevMode jscompDevMode) {
      this.jscompDevMode = jscompDevMode;
      return this;
    }

    private String loggingLevel = Level.WARNING.getName();

    /**
     * The logging level (standard java.util.logging.Level values) for Compiler progress. Does not
     * control errors or warnings for the JavaScript code under compilation
     */
    @CanIgnoreReturnValue
    public CommandLineConfig setLoggingLevel(String loggingLevel) {
      this.loggingLevel = loggingLevel;
      return this;
    }

    private final List<String> externs = new ArrayList<>();

    /** The file containing JavaScript externs. You may specify multiple. */
    @CanIgnoreReturnValue
    public CommandLineConfig setExterns(List<String> externs) {
      this.externs.clear();
      this.externs.addAll(externs);
      return this;
    }

    private final List<FlagEntry<JsSourceType>> mixedJsSources = new ArrayList<>();

    /** The JavaScript source file names, including .js and .zip files. You may specify multiple. */
    @CanIgnoreReturnValue
    public CommandLineConfig setMixedJsSources(List<FlagEntry<JsSourceType>> mixedJsSources) {
      this.mixedJsSources.clear();
      this.mixedJsSources.addAll(mixedJsSources);
      return this;
    }

    private boolean defaultToStdin = false;

    /**
     * Whether to read a single source file from standard input if no input files are explicitly
     * specified.
     */
    @CanIgnoreReturnValue
    public CommandLineConfig setDefaultToStdin() {
      this.defaultToStdin = true;
      return this;
    }

    private String jsOutputFile = "";

    /** Primary output filename. If not specified, output is written to stdout */
    @CanIgnoreReturnValue
    public CommandLineConfig setJsOutputFile(String jsOutputFile) {
      this.jsOutputFile = jsOutputFile;
      return this;
    }

    /**
     * When non-null specifies a file containing saved compiler state to restore and continue
     * compiling.
     */
    private @Nullable String continueSavedCompilationFileName = null;

    /**
     * When > 0 indicates the stage at which compilation stopped for the compilation state that is
     * being restored from a save file.
     */
    private int restoredCompilationStage = -1;

    /**
     * When > 0 indicates the stage at which compilation should stop and the compilation state be
     * stored into a save file.
     */
    private int saveAfterCompilationStage = -1;

    /** Set the compiler to resume a saved compilation state from a file. */
    @CanIgnoreReturnValue
    public CommandLineConfig setContinueSavedCompilationFileName(
        String fileName, int restoredCompilationStage) {
      if (fileName != null) {
        checkArgument(
            restoredCompilationStage > 0 && restoredCompilationStage < 3,
            "invalid compilation stage: %s",
            restoredCompilationStage);
        continueSavedCompilationFileName = fileName;
        this.restoredCompilationStage = restoredCompilationStage;
      }
      return this;
    }

    boolean shouldSaveAfterStage1() {
      if (saveCompilationStateToFilename != null && saveAfterCompilationStage == 1) {
        checkState(
            restoredCompilationStage < 0,
            "cannot perform stage 1 after restoring from stage %s",
            restoredCompilationStage);
        checkState(
            continueSavedCompilationFileName == null,
            "cannot restore a saved compilation and also save after stage 1");
        return true;
      } else {
        return false;
      }
    }

    String getContinueSavedCompilationFileName() {
      return continueSavedCompilationFileName;
    }

    boolean shouldRestoreAndPerformStages2And3() {
      // We have a saved compilations state to restore
      return shouldContinueCompilation()
          // the restored compilation stopped at stage 1
          && restoredCompilationStage == 1
          // we want to complete the rest of the compilation stages
          && saveAfterCompilationStage < 0;
    }

    private boolean shouldContinueCompilation() {
      if (continueSavedCompilationFileName != null) {
        checkState(
            restoredCompilationStage > 0 && restoredCompilationStage < 3,
            "invalid restored compilation stage: %s",
            restoredCompilationStage);
        return true;
      } else {
        return false;
      }
    }

    boolean shouldRestoreTypedAstsPerformStage2AndSave() {
      // We have a typed ast input list to parse
      return typedAstListInputFilename != null
          // we want to stop and save after optimizations
          && saveAfterCompilationStage == 2;
    }

    boolean shouldRestoreTypedAstsPerformStages2And3() {
      // We have a typed ast input list to parse
      return typedAstListInputFilename != null
          // we do not want to stop and save after optimizations
          && saveAfterCompilationStage == -1;
    }

    boolean shouldRestoreAndPerformStage2AndSave() {
      // We have a saved compilations state to restore
      return shouldContinueCompilation()
          // the restored compilation stopped at stage 1
          && restoredCompilationStage == 1
          // we want to stop and save after stage 2
          && saveAfterCompilationStage == 2;
    }

    boolean shouldRestoreAndPerformStage3() {
      if (shouldContinueCompilation() && restoredCompilationStage == 2) {
        // We have a saved compilations state from stage 2 to restore and continue
        checkState(
            saveAfterCompilationStage < 0,
            "request to save after stage %s is invalid when restoring from stage 2",
            saveAfterCompilationStage);
        return true;
      } else {
        return false;
      }
    }

    boolean shouldDoLateLocalization() {
      // The point of dividing checks, optimizations, and finalizations into different stages is to
      // localization work for last, so we avoid doing checks and optimizations separately for every
      // locale.
      // If we aren't doing finalizations as a separate final stage, then late localization is just
      // doing more work for a possibly-bigger compiled output (because code gets added after
      // optimizations have already executed).
      return shouldRestoreAndPerformStage2AndSave()
          || shouldRestoreAndPerformStage3()
          || shouldRestoreTypedAstsPerformStage2AndSave();
    }

    boolean shouldAlwaysGatherSourceMapInfo() {
      // If we're doing a partial compilation that isn't the final stage, we need to always gather
      // source map info in case the final stage requires it.
      return shouldSaveAfterStage1()
          || shouldRestoreAndPerformStage2AndSave()
          || shouldRestoreTypedAstsPerformStage2AndSave();
    }

    private @Nullable String typedAstListInputFilename;

    @CanIgnoreReturnValue
    public CommandLineConfig setTypedAstListInputFilename(@Nullable String fileName) {
      this.typedAstListInputFilename = fileName;
      return this;
    }

    private @Nullable String saveCompilationStateToFilename = null;

    /** Set the compiler to perform the first phase and save the intermediate result to a file. */
    @CanIgnoreReturnValue
    public CommandLineConfig setSaveCompilationStateToFilename(
        String fileName, int saveAfterCompilationStage) {
      saveCompilationStateToFilename = fileName;
      this.saveAfterCompilationStage = saveAfterCompilationStage;
      return this;
    }

    public String getSaveCompilationStateToFilename() {
      return saveCompilationStateToFilename;
    }

    private final List<String> chunk = new ArrayList<>();

    /**
     * A JavaScript chunk specification. The format is {@code
     * <name>:<num-js-files>[:[<dep>,...][:]]]}. Chunk names must be unique. Each dep is the name of
     * a chunk that this chunk depends on. Chunks must be listed in dependency order, and JS source
     * files must be listed in the corresponding order. Where --chunk flags occur in relation to
     * --js flags is unimportant
     */
    @CanIgnoreReturnValue
    public CommandLineConfig setChunk(List<String> chunk) {
      this.chunk.clear();
      this.chunk.addAll(chunk);
      return this;
    }

    private Map<String, String> sourceMapInputFiles = new LinkedHashMap<>();

    @CanIgnoreReturnValue
    public CommandLineConfig setSourceMapInputFiles(Map<String, String> sourceMapInputFiles) {
      this.sourceMapInputFiles = sourceMapInputFiles;
      return this;
    }

    private boolean parseInlineSourceMaps = false;

    @CanIgnoreReturnValue
    public CommandLineConfig setParseInlineSourceMaps(boolean parseInlineSourceMaps) {
      this.parseInlineSourceMaps = parseInlineSourceMaps;
      return this;
    }

    private String variableMapInputFile = "";

    /**
     * File containing the serialized version of the variable renaming map produced by a previous
     * compilation
     */
    @CanIgnoreReturnValue
    public CommandLineConfig setVariableMapInputFile(String variableMapInputFile) {
      this.variableMapInputFile = variableMapInputFile;
      return this;
    }

    private String propertyMapInputFile = "";

    /**
     * File containing the serialized version of the property renaming map produced by a previous
     * compilation
     */
    @CanIgnoreReturnValue
    public CommandLineConfig setPropertyMapInputFile(String propertyMapInputFile) {
      this.propertyMapInputFile = propertyMapInputFile;
      return this;
    }

    private String variableMapOutputFile = "";

    /** File where the serialized version of the variable renaming map produced should be saved */
    @CanIgnoreReturnValue
    public CommandLineConfig setVariableMapOutputFile(String variableMapOutputFile) {
      this.variableMapOutputFile = variableMapOutputFile;
      return this;
    }

    private boolean createNameMapFiles = false;

    /**
     * If true, variable renaming and property renaming map files will be produced as {binary
     * name}_vars_map.out and {binary name}_props_map.out. Note that this flag cannot be used in
     * conjunction with either variable_map_output_file or property_map_output_file
     */
    @CanIgnoreReturnValue
    public CommandLineConfig setCreateNameMapFiles(boolean createNameMapFiles) {
      this.createNameMapFiles = createNameMapFiles;
      return this;
    }

    private String propertyMapOutputFile = "";

    /** File where the serialized version of the property renaming map produced should be saved */
    @CanIgnoreReturnValue
    public CommandLineConfig setPropertyMapOutputFile(String propertyMapOutputFile) {
      this.propertyMapOutputFile = propertyMapOutputFile;
      return this;
    }

    private String stringMapOutputPath = "";

    /**
     * File where the serialized version of the string map produced by the ReplaceStrings pass
     * should be saved.
     */
    @CanIgnoreReturnValue
    public CommandLineConfig setStringMapOutputFile(String stringMapOutputPath) {
      this.stringMapOutputPath = stringMapOutputPath;
      return this;
    }

    private String instrumentationMappingFile = "";

    @CanIgnoreReturnValue
    public CommandLineConfig setInstrumentationMappingFile(String instrumentationMappingFile) {
      this.instrumentationMappingFile = instrumentationMappingFile;
      return this;
    }

    private CodingConvention codingConvention = CodingConventions.getDefault();

    /** Sets rules and conventions to enforce. */
    @CanIgnoreReturnValue
    public CommandLineConfig setCodingConvention(CodingConvention codingConvention) {
      this.codingConvention = codingConvention;
      return this;
    }

    private int summaryDetailLevel = 1;

    /**
     * Controls how detailed the compilation summary is. Values: 0 (never print summary), 1 (print
     * summary only if there are errors or warnings), 2 (print summary if type checking is on), 3
     * (always print summary). The default level is 1
     */
    @CanIgnoreReturnValue
    public CommandLineConfig setSummaryDetailLevel(int summaryDetailLevel) {
      this.summaryDetailLevel = summaryDetailLevel;
      return this;
    }

    private String outputWrapper = "";

    /**
     * Interpolate output into this string at the place denoted by the marker token %output%, or
     * %output|jsstring%
     */
    @CanIgnoreReturnValue
    public CommandLineConfig setOutputWrapper(String outputWrapper) {
      this.outputWrapper = outputWrapper;
      return this;
    }

    private final List<String> chunkWrapper = new ArrayList<>();

    /**
     * An output wrapper for a JavaScript module (optional). See the flag description for formatting
     * requirements.
     */
    @CanIgnoreReturnValue
    public CommandLineConfig setChunkWrapper(List<String> chunkWrapper) {
      this.chunkWrapper.clear();
      this.chunkWrapper.addAll(chunkWrapper);
      return this;
    }

    private String chunkOutputPathPrefix = "";

    /**
     * Prefix for filenames of compiled chunks. {@code <chunk-name>.js} will be appended to this
     * prefix. Directories will be created as needed. Use with --chunk
     */
    @CanIgnoreReturnValue
    public CommandLineConfig setChunkOutputPathPrefix(String chunkOutputPathPrefix) {
      this.chunkOutputPathPrefix = chunkOutputPathPrefix;
      return this;
    }

    private final List<String> chunkOutputFiles = new ArrayList<>();

    /**
     * The output file name for a JavaScript chunk (optional). See the flag description for
     * formatting requirements.
     */
    @CanIgnoreReturnValue
    public CommandLineConfig setChunkOutputFiles(List<String> chunkOutputFiles) {
      this.chunkOutputFiles.clear();
      this.chunkOutputFiles.addAll(chunkOutputFiles);
      return this;
    }

    private final List<String> chunkConformanceFiles = new ArrayList<>();

    /**
     * The conformance report file name for a JavaScript chunk (optional). See the flag description
     * for formatting requirements.
     */
    @CanIgnoreReturnValue
    public CommandLineConfig setChunkConformanceFiles(List<String> chunkConformanceFiles) {
      this.chunkConformanceFiles.clear();
      this.chunkConformanceFiles.addAll(chunkConformanceFiles);
      return this;
    }

    private String createSourceMap = "";

    /**
     * If specified, a source map file mapping the generated source files back to the original
     * source file will be output to the specified path. The %outname% placeholder will expand to
     * the name of the output file that the source map corresponds to.
     */
    @CanIgnoreReturnValue
    public CommandLineConfig setCreateSourceMap(String createSourceMap) {
      this.createSourceMap = createSourceMap;
      return this;
    }

    private SourceMap.DetailLevel sourceMapDetailLevel = SourceMap.DetailLevel.ALL;

    /** The detail supplied in the source map file, if generated. */
    @CanIgnoreReturnValue
    public CommandLineConfig setSourceMapDetailLevel(SourceMap.DetailLevel level) {
      this.sourceMapDetailLevel = level;
      return this;
    }

    private SourceMap.Format sourceMapFormat = SourceMap.Format.DEFAULT;

    /** The source map format to use, if generated. */
    @CanIgnoreReturnValue
    public CommandLineConfig setSourceMapFormat(SourceMap.Format format) {
      this.sourceMapFormat = format;
      return this;
    }

    private ImmutableList<SourceMap.LocationMapping> sourceMapLocationMappings = ImmutableList.of();

    /** The source map location mappings to use, if generated. */
    @CanIgnoreReturnValue
    public CommandLineConfig setSourceMapLocationMappings(
        List<SourceMap.LocationMapping> locationMappings) {

      this.sourceMapLocationMappings = ImmutableList.copyOf(locationMappings);
      return this;
    }

    private boolean applyInputSourceMaps = false;

    /**
     * Whether to apply input source maps to the output, i.e. map back to original inputs from input
     * files that have source maps applied to them.
     */
    @CanIgnoreReturnValue
    public CommandLineConfig setApplyInputSourceMaps(boolean applyInputSourceMaps) {
      this.applyInputSourceMaps = applyInputSourceMaps;
      return this;
    }

    private final ArrayList<FlagEntry<CheckLevel>> warningGuards = new ArrayList<>();

    /** Add warning guards. */
    @CanIgnoreReturnValue
    public CommandLineConfig setWarningGuards(List<FlagEntry<CheckLevel>> warningGuards) {
      this.warningGuards.clear();
      this.warningGuards.addAll(warningGuards);
      return this;
    }

    private final List<String> define = new ArrayList<>();

    /**
     * Override the value of a variable annotated @define. The format is {@code <name>[=<val>]},
     * where {@code <name>} is the name of a @define variable and {@code <val>} is a boolean,
     * number, or a single-quoted string that contains no single quotes. If {@code [=<val>]} is
     * omitted, the variable is marked true
     */
    @CanIgnoreReturnValue
    public CommandLineConfig setDefine(List<String> define) {
      this.define.clear();
      this.define.addAll(define);
      return this;
    }

    private int browserFeaturesetYear = 0;

    /** Indicates target browser's year */
    @CanIgnoreReturnValue
    public CommandLineConfig setBrowserFeaturesetYear(Integer browserFeaturesetYear) {
      this.browserFeaturesetYear = browserFeaturesetYear;
      return this;
    }

    private TweakProcessing tweakProcessing = TweakProcessing.OFF;

    /** Sets the kind of processing to do for goog.tweak functions. */
    @CanIgnoreReturnValue
    public CommandLineConfig setTweakProcessing(TweakProcessing tweakProcessing) {
      this.tweakProcessing = tweakProcessing;
      return this;
    }

    private String charset = "";

    /** Input charset for all files. */
    @CanIgnoreReturnValue
    public CommandLineConfig setCharset(String charset) {
      this.charset = charset;
      return this;
    }

    private @Nullable DependencyOptions dependencyOptions = null;

    /** Sets the dependency management options. */
    @CanIgnoreReturnValue
    public CommandLineConfig setDependencyOptions(@Nullable DependencyOptions dependencyOptions) {
      this.dependencyOptions = dependencyOptions;
      return this;
    }

    private List<String> outputManifests = ImmutableList.of();

    /** Sets whether to print output manifest files. Filter out empty file names. */
    @CanIgnoreReturnValue
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

    private @Nullable String outputChunkDependencies = null;

    /** Sets whether a JSON file representing the dependencies between modules should be created. */
    @CanIgnoreReturnValue
    public CommandLineConfig setOutputChunkDependencies(String outputChunkDependencies) {
      this.outputChunkDependencies = outputChunkDependencies;
      return this;
    }

    private List<String> outputBundles = ImmutableList.of();

    /** Sets whether to print output bundle files. */
    @CanIgnoreReturnValue
    public CommandLineConfig setOutputBundle(List<String> outputBundles) {
      this.outputBundles = outputBundles;
      return this;
    }

    private boolean skipNormalOutputs = false;

    /** Sets whether the normal outputs of compilation should be skipped. */
    @CanIgnoreReturnValue
    public CommandLineConfig setSkipNormalOutputs(boolean skipNormalOutputs) {
      this.skipNormalOutputs = skipNormalOutputs;
      return this;
    }

    private List<String> manifestMaps = ImmutableList.of();

    /** Sets the execPath:rootRelativePath mappings */
    @CanIgnoreReturnValue
    public CommandLineConfig setManifestMaps(List<String> manifestMaps) {
      this.manifestMaps = manifestMaps;
      return this;
    }

    private boolean processCommonJSModules = false;

    /** Sets whether to process CommonJS modules,. */
    @CanIgnoreReturnValue
    public CommandLineConfig setProcessCommonJSModules(boolean processCommonJSModules) {
      this.processCommonJSModules = processCommonJSModules;
      return this;
    }

    private List<String> moduleRoots = ImmutableList.of(ModuleLoader.DEFAULT_FILENAME_PREFIX);

    /** Sets the module roots. */
    @CanIgnoreReturnValue
    public CommandLineConfig setModuleRoots(List<String> jsModuleRoots) {
      this.moduleRoots = jsModuleRoots;
      return this;
    }

    private String warningsAllowFile = "";

    /** Sets a allowlist file that suppresses warnings. */
    @CanIgnoreReturnValue
    public CommandLineConfig setWarningsAllowlistFile(String fileName) {
      this.warningsAllowFile = fileName;
      return this;
    }

    private List<String> hideWarningsFor = ImmutableList.of();

    /** Sets the paths for which warnings will be hidden. */
    @CanIgnoreReturnValue
    public CommandLineConfig setHideWarningsFor(List<String> hideWarningsFor) {
      this.hideWarningsFor = hideWarningsFor;
      return this;
    }

    private boolean angularPass = false;

    /** Sets whether to process AngularJS-specific annotations. */
    @CanIgnoreReturnValue
    public CommandLineConfig setAngularPass(boolean angularPass) {
      this.angularPass = angularPass;
      return this;
    }

    private JsonStreamMode jsonStreamMode = JsonStreamMode.NONE;

    @CanIgnoreReturnValue
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

    @CanIgnoreReturnValue
    public CommandLineConfig setErrorFormat(ErrorFormatOption errorFormat) {
      this.errorFormat = errorFormat;
      return this;
    }

    private String jsonWarningsFile = "";

    @CanIgnoreReturnValue
    public CommandLineConfig setJsonWarningsFile(String jsonWarningsFile) {
      this.jsonWarningsFile = jsonWarningsFile;
      return this;
    }

    private boolean includeTrailingNewline = true;

    @CanIgnoreReturnValue
    public CommandLineConfig setIncludeTrailingNewline(boolean includeTrailingNewline) {
      this.includeTrailingNewline = includeTrailingNewline;
      return this;
    }
  }

  /** Representation of a source file from an encoded json stream input */
  public static class JsonFileSpec {
    private final @Nullable String src;
    private final @Nullable String path;

    @SerializedName(
        value = "source_map",
        alternate = {"sourceMap"})
    private @Nullable String sourceMap;

    @SerializedName(
        value = "webpack_id",
        alternate = {"webpackId"})
    private final @Nullable String webpackId;

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

    public JsonFileSpec(
        @Nullable String src,
        @Nullable String path,
        @Nullable String sourceMap,
        @Nullable String webpackId) {
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

  /** Flag types for JavaScript source files. */
  protected enum JsSourceType {
    EXTERN("extern"),
    JS("js"),
    JS_ZIP("jszip"),
    WEAKDEP("weakdep"),
    IJS("ijs");

    @VisibleForTesting final String flagName;

    private JsSourceType(String flagName) {
      this.flagName = flagName;
    }
  }

  /** A pair from flag to its value. */
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
        return that.flag.equals(this.flag) && that.value.equals(this.value);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return flag.hashCode() + value.hashCode();
    }

    @Override
    public String toString() {
      return this.flag + "=" + this.value;
    }

    public T getFlag() {
      return flag;
    }

    public String getValue() {
      return value;
    }
  }

  /** Represents a specification for a serving chunk. */
  public static class JsChunkSpec {
    private final String name;
    // Number of input files, including js and zip files.
    private final int numInputs;
    private final ImmutableList<String> deps;
    // Number of input js files. All zip files should be expanded.
    private int numJsFiles;

    private JsChunkSpec(String name, int numInputs, ImmutableList<String> deps) {
      this.name = name;
      this.numInputs = numInputs;
      this.deps = deps;
      this.numJsFiles = numInputs;
    }

    /**
     * Returns a parsed chunk spec.
     *
     * @param specString The spec format is: <code>name:num-js-files[:[dep,...][:]]</code>. Chunk
     *     names must not contain the ':' character.
     * @param isFirstChunk Whether the spec is for the first chunk.
     */
    public static JsChunkSpec create(String specString, boolean isFirstChunk) {
      // Format is "<name>:<num-js-files>[:[<dep>,...][:]]".
      String[] parts = specString.split(":");
      if (parts.length < 2 || parts.length > 4) {
        throw new FlagUsageException(
            "Expected 2-4 colon-delimited parts in " + "chunk spec: " + specString);
      }

      // Parse chunk name.
      String name = parts[0];

      // Parse chunk dependencies.
      String[] deps =
          parts.length > 2 && parts[2].length() > 0 ? parts[2].split(",") : new String[0];

      // Parse chunk inputs.
      int numInputs = -1;
      try {
        numInputs = Integer.parseInt(parts[1]);
      } catch (NumberFormatException ignored) {
        numInputs = -1;
      }

      // We will allow chunks of zero input.
      if (numInputs < 0) {
        // A size of 'auto' is only allowed on the base chunk if
        // and it must also be the first chunk
        if (parts.length == 2 && "auto".equals(parts[1])) {
          if (!isFirstChunk) {
            throw new FlagUsageException(
                "Invalid JS file count '"
                    + parts[1]
                    + "' for chunk: "
                    + name
                    + ". Only the first chunk may specify "
                    + "a size of 'auto' and it must have no dependencies.");
          }
        } else {
          throw new FlagUsageException(
              "Invalid JS file count '" + parts[1] + "' for chunk: " + name);
        }
      }

      return new JsChunkSpec(name, numInputs, ImmutableList.copyOf(deps));
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
