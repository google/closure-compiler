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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.javascript.jscomp.CompilerOptions.TweakProcessing;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.TokenStream;
import com.google.protobuf.CodedOutputStream;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

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
abstract class AbstractCommandLineRunner<A extends Compiler,
    B extends CompilerOptions> {

  private final CommandLineConfig config;

  private Appendable out;
  private final PrintStream err;
  private A compiler;

  private Charset inputCharset;
  private String outputCharset;

  private boolean testMode = false;
  private Supplier<List<JSSourceFile>> externsSupplierForTesting = null;
  private Supplier<List<JSSourceFile>> inputsSupplierForTesting = null;
  private Supplier<List<JSModule>> modulesSupplierForTesting = null;
  private Function<Integer, Boolean> exitCodeReceiverForTesting = null;

  // Bookkeeping to measure optimal phase orderings.
  private static final int NUM_RUNS_TO_DETERMINE_OPTIMAL_ORDER = 100;

  private final RunTimeStats runTimeStats = new RunTimeStats();

  AbstractCommandLineRunner() {
    this(System.out, System.err);
  }

  AbstractCommandLineRunner(PrintStream out, PrintStream err) {
    this.config = new CommandLineConfig();
    this.out = out;
    this.err = err;
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
      Supplier<List<JSSourceFile>> externsSupplier,
      Supplier<List<JSSourceFile>> inputsSupplier,
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

  /** No longer does anything. */
  @Deprecated
  protected void initOptionsFromFlags(CompilerOptions options) {}

  /**
   * Sets options based on the configurations set flags API.
   * Called during the run() run() method.
   * If you want to ignore the flags API, or intepret flags your own way,
   * then you should override this method.
   */
  final protected void setRunOptions(CompilerOptions options)
      throws FlagUsageException, IOException {
    DiagnosticGroups diagnosticGroups = getDiagnosticGroups();

    diagnosticGroups.setWarningLevels(
        options, config.jscompError, CheckLevel.ERROR);
    diagnosticGroups.setWarningLevels(
        options, config.jscompWarning, CheckLevel.WARNING);
    diagnosticGroups.setWarningLevels(
        options, config.jscompOff, CheckLevel.OFF);

    createDefineOrTweakReplacements(config.define, options, false);

    options.setTweakProcessing(config.tweakProcessing);
    createDefineOrTweakReplacements(config.tweak, options, true);

    options.manageClosureDependencies = config.manageClosureDependencies;
    if (config.closureEntryPoints.size() > 0) {
      options.setManageClosureDependencies(config.closureEntryPoints);
    }
    options.devMode = config.jscompDevMode;
    options.setCodingConvention(config.codingConvention);
    options.setSummaryDetailLevel(config.summaryDetailLevel);

    outputCharset = options.outputCharset = getOutputCharset();
    inputCharset = getInputCharset();

    if (config.jsOutputFile.length() > 0) {
      options.jsOutputFile = config.jsOutputFile;
    }

    if (config.createSourceMap.length() > 0) {
      options.sourceMapOutputPath = config.createSourceMap;
    }
    options.sourceMapDetailLevel = config.sourceMapDetailLevel;
    options.sourceMapFormat = config.sourceMapFormat;

    if (!config.variableMapInputFile.equals("")) {
      options.inputVariableMapSerialized =
          VariableMap.load(config.variableMapInputFile).toBytes();
    }

    if (!config.propertyMapInputFile.equals("")) {
      options.inputPropertyMapSerialized =
          VariableMap.load(config.propertyMapInputFile).toBytes();
    }
  }

  final protected A getCompiler() {
    return compiler;
  }

  /**
   * Runs the Compiler and calls System.exit() with the exit status of the
   * compiler.
   */
  final public void run() {
    int result = 0;
    int runs = 1;
    if (config.computePhaseOrdering) {
      runs = NUM_RUNS_TO_DETERMINE_OPTIMAL_ORDER;
      PhaseOptimizer.randomizeLoops();
    }
    try {
      for (int i = 0; i < runs && result == 0; i++) {
        runTimeStats.recordStartRun();
        result = doRun();
        runTimeStats.recordEndRun();
      }
    } catch (AbstractCommandLineRunner.FlagUsageException e) {
      System.err.println(e.getMessage());
      result = -1;
    } catch (Throwable t) {
      t.printStackTrace();
      result = -2;
    }

    if (config.computePhaseOrdering) {
      runTimeStats.outputBestPhaseOrdering();
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
  protected static class FlagUsageException extends Exception {
    private static final long serialVersionUID = 1L;

    FlagUsageException(String message) {
      super(message);
    }
  }

  /**
   * Creates inputs from a list of files.
   *
   * @param files A list of filenames
   * @param allowStdIn Whether '-' is allowed appear as a filename to represent
   *        stdin. If true, '-' is only allowed to appear once.
   * @return An array of inputs
   */
  private List<JSSourceFile> createInputs(List<String> files,
      boolean allowStdIn) throws FlagUsageException, IOException {
    List<JSSourceFile> inputs = new ArrayList<JSSourceFile>(files.size());
    boolean usingStdin = false;
    for (String filename : files) {
      if (!"-".equals(filename)) {
        JSSourceFile newFile = JSSourceFile.fromFile(filename, inputCharset);
        inputs.add(newFile);
      } else {
        if (!allowStdIn) {
          throw new FlagUsageException("Can't specify stdin.");
        }
        if (usingStdin) {
          throw new FlagUsageException("Can't specify stdin twice.");
        }

        inputs.add(JSSourceFile.fromInputStream("stdin", System.in));
        usingStdin = true;
      }
    }
    return inputs;
  }

  /**
   * Creates js source code inputs from a list of files.
   */
  private List<JSSourceFile> createSourceInputs(List<String> files)
      throws FlagUsageException, IOException {
    if (isInTestMode()) {
      return inputsSupplierForTesting.get();
    }
    if (files.isEmpty()) {
      files = Collections.singletonList("-");
    }
    try {
      return createInputs(files, true);
    } catch (FlagUsageException e) {
      throw new FlagUsageException("Bad --js flag. " + e.getMessage());
    }
  }

  /**
   * Creates js extern inputs from a list of files.
   */
  private List<JSSourceFile> createExternInputs(List<String> files)
      throws FlagUsageException, IOException {
    if (files.isEmpty()) {
      return ImmutableList.of(JSSourceFile.fromCode("/dev/null", ""));
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
   * @param jsFiles A list of js file paths, not null
   * @return An array of module objects
   */
  List<JSModule> createJsModules(
      List<String> specs, List<String> jsFiles)
      throws FlagUsageException, IOException {
    if (isInTestMode()) {
      return modulesSupplierForTesting.get();
    }

    Preconditions.checkState(specs != null);
    Preconditions.checkState(!specs.isEmpty());
    Preconditions.checkState(jsFiles != null);

    final int totalNumJsFiles = jsFiles.size();
    int nextJsFileIndex = 0;

    Map<String, JSModule> modulesByName = Maps.newLinkedHashMap();
    for (String spec : specs) {

      // Format is "<name>:<num-js-files>[:[<dep>,...][:]]".
      String[] parts = spec.split(":");
      if (parts.length < 2 || parts.length > 4) {
        throw new FlagUsageException("Expected 2-4 colon-delimited parts in "
            + "module spec: " + spec);
      }

      // Parse module name.
      String name = parts[0];
      if (!TokenStream.isJSIdentifier(name)) {
        throw new FlagUsageException("Invalid module name: '" + name + "'");
      }
      if (modulesByName.containsKey(name)) {
        throw new FlagUsageException("Duplicate module name: " + name);
      }
      JSModule module = new JSModule(name);

      // Parse module inputs.
      int numJsFiles = -1;
      try {
        numJsFiles = Integer.parseInt(parts[1]);
      } catch (NumberFormatException ignored) {
        numJsFiles = -1;
      }

      // We will allow modules of zero input.
      if (numJsFiles < 0) {
        throw new FlagUsageException("Invalid js file count '" + parts[1]
            + "' for module: " + name);
      }
      if (nextJsFileIndex + numJsFiles > totalNumJsFiles) {
        throw new FlagUsageException("Not enough js files specified. Expected "
            + (nextJsFileIndex + numJsFiles - totalNumJsFiles)
            + " more in module:" + name);
      }
      List<String> moduleJsFiles =
          jsFiles.subList(nextJsFileIndex, nextJsFileIndex + numJsFiles);
      for (JSSourceFile input : createInputs(moduleJsFiles, false)) {
        module.add(input);
      }
      nextJsFileIndex += numJsFiles;

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

      modulesByName.put(name, module);
    }

    if (nextJsFileIndex < totalNumJsFiles) {
      throw new FlagUsageException("Too many js files specified. Expected "
          + nextJsFileIndex + " but found " + totalNumJsFiles);
    }

    return Lists.newArrayList(modulesByName.values());
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

    Map<String, String> wrappers =
        Maps.newHashMapWithExpectedSize(modules.size());

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

  /**
   * Writes code to an output stream, optionally wrapping it in an arbitrary
   * wrapper that contains a placeholder where the code should be inserted.
   */
  static void writeOutput(Appendable out, Compiler compiler, String code,
      String wrapper, String codePlaceholder) throws IOException {
    int pos = wrapper.indexOf(codePlaceholder);
    if (pos != -1) {
      String prefix = "";

      if (pos > 0) {
        prefix = wrapper.substring(0, pos);
        out.append(prefix);
      }

      out.append(code);

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
    if (pathPrefix.length() > 0) {
      String dirName =
          pathPrefix.charAt(pathPrefix.length() - 1) == File.separatorChar
              ? pathPrefix.substring(0, pathPrefix.length() - 1) : new File(
                  pathPrefix).getParent();
      if (dirName != null) {
        new File(dirName).mkdirs();
      }
    }
  }

  /**
   * Parses command-line arguments and runs the compiler.
   *
   * @return system exit status
   */
  protected int doRun() throws FlagUsageException, IOException {
    Compiler.setLoggingLevel(Level.parse(config.loggingLevel));

    List<JSSourceFile> externs = createExterns();

    compiler = createCompiler();
    B options = createOptions();

    List<JSModule> modules = null;
    Result result;

    setRunOptions(options);

    boolean writeOutputToFile = !options.jsOutputFile.isEmpty();
    if (writeOutputToFile) {
      out = fileNameToOutputWriter(options.jsOutputFile);
    } else if (out instanceof OutputStream) {
      out = streamToOutputWriter((OutputStream) out);
    }

    List<String> jsFiles = config.js;
    List<String> moduleSpecs = config.module;
    if (!moduleSpecs.isEmpty()) {
      modules = createJsModules(moduleSpecs, jsFiles);
      result = compiler.compileModules(externs, modules, options);
    } else {
      List<JSSourceFile> inputs = createSourceInputs(jsFiles);
      result = compiler.compile(externs, inputs, options);
    }

    int errCode = processResults(result, modules, options);
    // Close the output if we are writing to a file.
    if (out instanceof Closeable) {
      ((Closeable) out).close();
    }
    return errCode;
  }

  /**
   * Processes the results of the compile job, and returns an error code.
   */
  int processResults(Result result, List<JSModule> modules, B options)
       throws FlagUsageException, IOException {
    if (config.computePhaseOrdering) {
      return 0;
    }

    if (config.printPassGraph) {
      if (compiler.getRoot() == null) {
        return 1;
      } else {
        out.append(DotFormatter.toDot(compiler.getPassConfig().getPassGraph()));
        out.append('\n');
        return 0;
      }
    }

    if (config.printAst) {
      if (compiler.getRoot() == null) {
        return 1;
      } else {
        ControlFlowGraph<Node> cfg = compiler.computeCFG();
        DotFormatter.appendDot(compiler.getRoot().getLastChild(), cfg, out);
        out.append('\n');
        return 0;
      }
    }

    if (config.printTree) {
      if (compiler.getRoot() == null) {
        out.append("Code contains errors; no tree was generated.\n");
        return 1;
      } else {
        compiler.getRoot().appendStringTree(out);
        out.append("\n");
        return 0;
      }
    }

    if (result.success) {
      if (modules == null) {
        writeOutput(out, compiler, compiler.toSource(), config.outputWrapper,
            config.outputWrapperMarker);

        // Output the source map if requested.
        outputSourceMap(options);
      } else {
        String moduleFilePrefix = config.moduleOutputPathPrefix;
        maybeCreateDirsForPath(moduleFilePrefix);
        Map<String, String> moduleWrappers =
            parseModuleWrappers(config.moduleWrapper, modules);

        // If the source map path is in fact a pattern for each
        // module, create a stream per-module. Otherwise, create
        // a single source map.
        Writer mapOut = null;

        if (!shouldGenerateMapPerModule(options)) {
          mapOut = fileNameToOutputWriter(expandSourceMapPath(options, null));
        }

        for (JSModule m : modules) {
          if (shouldGenerateMapPerModule(options)) {
            mapOut = fileNameToOutputWriter(expandSourceMapPath(options, m));
          }

          Writer writer = fileNameToOutputWriter(
              moduleFilePrefix + m.getName() + ".js");

          if (options.sourceMapOutputPath != null) {
            compiler.getSourceMap().reset();
          }

          writeOutput(writer, compiler, compiler.toSource(m), moduleWrappers.get(
              m.getName()), "%s");

          if (options.sourceMapOutputPath != null) {
            compiler.getSourceMap().appendTo(mapOut, m.getName());
          }

          writer.close();

          if (shouldGenerateMapPerModule(options) && mapOut != null) {
            mapOut.close();
            mapOut = null;
          }
        }

        if (mapOut != null) {
          mapOut.close();
        }
      }

      // Output the externs if required.
      if (options.externExportsPath != null) {
        Writer eeOut =
            openExternExportsStream(options, options.jsOutputFile);
        eeOut.append(result.externExport);
        eeOut.close();
      }

      // Output the variable and property name maps if requested.
      outputNameMaps(options);

      // Output the manifest if requested.
      outputManifest();
    }

    // return 0 if no errors, the error count otherwise
    return Math.min(result.errors.length, 0x7f);
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
    return Charsets.UTF_8;
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
  private String getOutputCharset() throws FlagUsageException {
    if (!config.charset.isEmpty()) {
      if (!Charset.isSupported(config.charset)) {
        throw new FlagUsageException(config.charset +
            " is not a valid charset name.");
      }
      return config.charset;
    }
    return "US-ASCII";
  }

  protected List<JSSourceFile> createExterns() throws FlagUsageException,
      IOException {
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

    return fileNameToOutputWriter(exPath);
  }

  /**
   * Expand a file path specified on the command-line.
   *
   * Most file paths on the command-line allow an %outname% placeholder.
   * The placeholder will expand to a different value depending on
   * the current output mode. There are three scenarios:
   *
   * 1) Single js output, single extra output: sub in jsOutputPath.
   * 2) Multiple js output, single extra output: sub in the base module name.
   * 3) Multiple js output, multiple extra output: sub in the module output file.
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
    if (Strings.isEmpty(options.sourceMapOutputPath)) {
      return null;
    }
    return expandCommandLinePath(options.sourceMapOutputPath, forModule);
  }

  /** Expansion function for the manifest. */
  @VisibleForTesting
  String expandManifest(JSModule forModule) {
    if (Strings.isEmpty(config.outputManifest)) {
      return null;
    }
    return expandCommandLinePath(config.outputManifest, forModule);
  }

  /**
   * Converts a file name into a Writer.
   * Returns null if the file name is null.
   */
  private Writer fileNameToOutputWriter(String fileName) throws IOException {
    if (fileName == null) {
      return null;
    }
    if (testMode) {
      return new StringWriter();
    }
    return streamToOutputWriter(new FileOutputStream(fileName));
  }

  /**
   * Create a writer.
   */
  private Writer streamToOutputWriter(OutputStream stream)
      throws IOException {
    if (outputCharset == null) {
      return new BufferedWriter(
          new OutputStreamWriter(stream));
    } else {
      return new BufferedWriter(
          new OutputStreamWriter(stream, outputCharset));
    }
  }

  /**
   * Outputs the source map found in the compiler to the proper path if one
   * exists.
   *
   * @param options The options to the Compiler.
   */
  private void outputSourceMap(B options)
      throws IOException {
    if (Strings.isEmpty(options.sourceMapOutputPath)) {
      return;
    }

    String outName = expandSourceMapPath(options, null);
    Writer out = fileNameToOutputWriter(outName);
    compiler.getSourceMap().appendTo(out, outName);
    out.close();
  }

  /**
   * Returns the path at which to output map file(s) based on the path at which
   * the JS binary will be placed.
   *
   * @return The path in which to place the generated map file(s).
   */
  private String getMapPath(String outputFile) {
    String basePath = "";

    if (outputFile.equals("")) {
      // If we have a js_module_binary rule, output the maps
      // at modulename_props_map.out, etc.
      if (!config.moduleOutputPathPrefix.equals("")) {
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

      basePath = file.getParent() + File.separatorChar + outputFileName;
    }

    return basePath;
  }

  /**
   * Outputs the variable and property name maps for the specified compiler if
   * the proper FLAGS are set.
   */
  private void outputNameMaps(B options) throws FlagUsageException,
      IOException {

    String propertyMapOutputPath = null;
    String variableMapOutputPath = null;
    String functionInformationMapOutputPath = null;

    // Check the create_name_map_files FLAG.
    if (config.createNameMapFiles) {
      String basePath = getMapPath(options.jsOutputFile);

      propertyMapOutputPath = basePath + "_props_map.out";
      variableMapOutputPath = basePath + "_vars_map.out";
      functionInformationMapOutputPath = basePath + "_functions_map.out";
    }

    // Check the individual FLAGS.
    if (!config.variableMapOutputFile.equals("")) {
      if (variableMapOutputPath != null) {
        throw new FlagUsageException("The flags variable_map_output_file and "
            + "create_name_map_files cannot both be used simultaniously.");
      }

      variableMapOutputPath = config.variableMapOutputFile;
    }

    if (!config.propertyMapOutputFile.equals("")) {
      if (propertyMapOutputPath != null) {
        throw new FlagUsageException("The flags property_map_output_file and "
            + "create_name_map_files cannot both be used simultaniously.");
      }

      propertyMapOutputPath = config.propertyMapOutputFile;
    }

    // Output the maps.
    if (variableMapOutputPath != null) {
      if (compiler.getVariableMap() != null) {
        compiler.getVariableMap().save(variableMapOutputPath);
      }
    }

    if (propertyMapOutputPath != null) {
      if (compiler.getPropertyMap() != null) {
        compiler.getPropertyMap().save(propertyMapOutputPath);
      }
    }

    if (functionInformationMapOutputPath != null) {
      if (compiler.getFunctionalInformationMap() != null) {
        FileOutputStream file =
            new FileOutputStream(functionInformationMapOutputPath);
        CodedOutputStream outputStream = CodedOutputStream.newInstance(file);
        compiler.getFunctionalInformationMap().writeTo(outputStream);
        outputStream.flush();
        file.flush();
        file.close();
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
   * Returns true if and only if a manifest should be generated for each
   * module, as opposed to one unified manifest.
   */
  private boolean shouldGenerateManifestPerModule() {
    return !config.module.isEmpty()
        && config.outputManifest != null
        && config.outputManifest.contains("%outname%");
  }

  /**
   * Writes the manifest of all compiler input files that survived
   * manage_closure_dependencies, if requested.
   */
  private void outputManifest() throws IOException {
    String outputManifest = config.outputManifest;
    if (Strings.isEmpty(outputManifest)) {
      return;
    }

    JSModuleGraph graph = compiler.getModuleGraph();
    if (shouldGenerateManifestPerModule()) {
      // Generate per-module manifests.
      Iterable<JSModule> modules = graph.getAllModules();
      for (JSModule module : modules) {
        Writer out = fileNameToOutputWriter(expandManifest(module));
        printManifestTo(module.getInputs(), out);
        out.close();
      }
    } else {
      // Generate a single file manifest.
      Writer out = fileNameToOutputWriter(expandManifest(null));
      if (graph == null) {
        printManifestTo(compiler.getInputsInOrder(), out);
      } else {
        printModuleGraphManifestTo(graph, out);
      }
      out.close();
    }
  }

  /**
   * Prints a set of modules to the manifest file.
   */
  @VisibleForTesting
  void printModuleGraphManifestTo(
      JSModuleGraph graph, Appendable out) throws IOException {
    Joiner commas = Joiner.on(",");
    boolean requiresNewline = false;
    for (JSModule module : graph.getAllModulesInDependencyOrder()) {
      if (requiresNewline) {
        out.append("\n");
      }

      // See CommandLineRunnerTest to see what the format of this
      // manifest looks like.
      String dependencies = commas.join(module.getSortedDependencyNames());
      out.append(
          String.format("{%s%s}\n",
              module.getName(),
              dependencies.isEmpty() ? "" : ":" + dependencies));
      printManifestTo(module.getInputs(), out);
      requiresNewline = true;
    }
  }

  /**
   * Prints a list of input names, delimited by newlines, to the manifest file.
   */
  private void printManifestTo(Iterable<CompilerInput> inputs, Appendable out)
      throws IOException {
    List<String> names = Lists.newArrayList();
    for (CompilerInput input : inputs) {
      names.add(input.getName());
    }
    String result = Joiner.on("\n").join(names);
    out.append(result);
    out.append("\n");
  }

  private class RunTimeStats {
    private long bestRunTime = Long.MAX_VALUE;
    private long worstRunTime = Long.MIN_VALUE;
    private long lastStartTime = 0;
    private List<List<String>> loopedPassesInBestRun = null;

    /**
     * Record the start of a run.
     */
    private void recordStartRun() {
      lastStartTime = System.currentTimeMillis();
      PhaseOptimizer.clearLoopsRun();
    }

    /**
     * Record the end of a run.
     */
    private void recordEndRun() {
      long endTime = System.currentTimeMillis();
      long length = endTime - lastStartTime;
      worstRunTime = Math.max(length, worstRunTime);
      if (length < bestRunTime) {
        loopedPassesInBestRun = PhaseOptimizer.getLoopsRun();
        bestRunTime = length;
      }
    }

    /**
     * Print the best phase loop to stderr.
     */
    private void outputBestPhaseOrdering() {
      try {
        out.append("Best time: " + bestRunTime + "\n");
        out.append("Worst time: " + worstRunTime + "\n");

        int i = 1;
        for (List<String> loop : loopedPassesInBestRun) {
          out.append("\nLoop " + i + ":\n" + Joiner.on("\n").join(loop)+ "\n");
          i++;
        }
      } catch (IOException e) {
        throw new RuntimeException("unexpected exception", e);
      }
    }
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

    private boolean computePhaseOrdering = false;

    /**
     * Runs the compile job many times, then prints out the best phase
     * ordering from this run
     */
    CommandLineConfig setComputePhaseOrdering(boolean computePhaseOrdering) {
      this.computePhaseOrdering = computePhaseOrdering;
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

    private final List<String> externs = Lists.newArrayList();

    /**
     * The file containing javascript externs. You may specify multiple.
     */
    CommandLineConfig setExterns(List<String> externs) {
      this.externs.clear();
      this.externs.addAll(externs);
      return this;
    }

    private final List<String> js = Lists.newArrayList();

    /**
     * The javascript filename. You may specify multiple.
     */
    CommandLineConfig setJs(List<String> js) {
      this.js.clear();
      this.js.addAll(js);
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

    private final List<String> module = Lists.newArrayList();

    /**
     * A javascript module specification. The format is
     * <name>:<num-js-files>[:[<dep>,...][:]]]. Module names must be
     * unique. Each dep is the name of a module that this module
     * depends on. Modules must be listed in dependency order, and js
     * source files must be listed in the corresponding order. Where
     * --module flags occur in relation to --js flags is unimportant
     */
    CommandLineConfig setModule(List<String> module) {
      this.module.clear();
      this.module.addAll(module);
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

    private CodingConvention codingConvention = new DefaultCodingConvention();

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
     *  by the marker token %output%. See --output_wrapper_marker
     */
    CommandLineConfig setOutputWrapper(String outputWrapper) {
      this.outputWrapper = outputWrapper;
      return this;
    }

    private String outputWrapperMarker = "";

    /**
     * Use this token as output marker in the value of
     *  --output_wrapper
     */
    CommandLineConfig setOutputWrapperMarker(String outputWrapperMarker) {
      this.outputWrapperMarker = outputWrapperMarker;
      return this;
    }

    private final List<String> moduleWrapper = Lists.newArrayList();

    /**
     * An output wrapper for a javascript module (optional).
     * The format is <name>:<wrapper>. The module name must correspond
     * with a module specified using --module. The wrapper must
     * contain %s as the code placeholder
     */
    CommandLineConfig setModuleWrapper(List<String> moduleWrapper) {
      this.moduleWrapper.clear();
      this.moduleWrapper.addAll(moduleWrapper);
      return this;
    }

    private String moduleOutputPathPrefix = "";

    /**
     * Prefix for filenames of compiled js modules.
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
      SourceMap.Format.LEGACY;

    /**
     * The detail supplied in the source map file, if generated.
     */
    CommandLineConfig setSourceMapFormat(SourceMap.Format format) {
      this.sourceMapFormat = format;
      return this;
    }

    private final List<String> jscompError = Lists.newArrayList();

    /**
     * Make the named class of warnings an error.
     */
    CommandLineConfig setJscompError(List<String> jscompError) {
      this.jscompError.clear();
      this.jscompError.addAll(jscompError);
      return this;
    }

    private final List<String> jscompWarning = Lists.newArrayList();

    /**
     * Make the named class of warnings a normal warning.
     */
    CommandLineConfig setJscompWarning(List<String> jscompWarning) {
      this.jscompWarning.clear();
      this.jscompWarning.addAll(jscompWarning);
      return this;
    }

    private final List<String> jscompOff = Lists.newArrayList();

    /**
     * Turn off the named class of warnings.
     */
    CommandLineConfig setJscompOff(List<String> jscompOff) {
      this.jscompOff.clear();
      this.jscompOff.addAll(jscompOff);
      return this;
    }

    private final List<String> define = Lists.newArrayList();

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

    private final List<String> tweak = Lists.newArrayList();

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

    private String outputManifest = "";

    /**
     * Sets whether to print an output manifest file.
     */
    CommandLineConfig setOutputManifest(String outputManifest) {
      this.outputManifest = outputManifest;
      return this;
    }
  }
}
