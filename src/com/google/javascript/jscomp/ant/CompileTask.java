/*
 * Copyright 2010 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.ant;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Strings;
import com.google.common.io.Files;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.CommandLineRunner;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.DiagnosticGroup;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.MessageFormatter;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.SourceMap;
import com.google.javascript.jscomp.SourceMap.Format;
import com.google.javascript.jscomp.SourceMap.LocationMapping;
import com.google.javascript.jscomp.WarningLevel;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileList;
import org.apache.tools.ant.types.Parameter;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Resource;
import org.apache.tools.ant.types.ResourceCollection;
import org.apache.tools.ant.types.resources.FileResource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * This class implements a simple Ant task to do almost the same as
 * CommandLineRunner.
 *
 * Most of the public methods of this class are entry points for the
 * Ant code to hook into.
 *
 */
public final class CompileTask
    extends Task {
  private CompilerOptions.LanguageMode languageIn;
  private CompilerOptions.LanguageMode languageOut;
  private WarningLevel warningLevel;
  private boolean debugOptions;
  private String encoding = "UTF-8";
  private Charset outputEncoding = UTF_8;
  private CompilationLevel compilationLevel;
  private CompilerOptions.Environment environment;
  private boolean manageDependencies;
  private boolean prettyPrint;
  private boolean printInputDelimiter;
  private boolean preferSingleQuotes;
  private boolean generateExports;
  private boolean replaceProperties;
  private boolean forceRecompile;
  private boolean angularPass;
  private String replacePropertiesPrefix;
  private File outputFile;
  private String outputWrapper;
  private File outputWrapperFile;
  private final List<Parameter> defineParams;
  private final List<Parameter> entryPointParams;
  private final List<FileList> externFileLists;
  private final List<FileList> sourceFileLists;
  private final List<Path> sourcePaths;
  private final List<Warning> warnings;
  private String sourceMapFormat;
  private File sourceMapOutputFile;
  private String sourceMapLocationMapping;

  public CompileTask() {
    this.languageIn = CompilerOptions.LanguageMode.ECMASCRIPT6;
    this.languageOut = CompilerOptions.LanguageMode.ECMASCRIPT3;
    this.warningLevel = WarningLevel.DEFAULT;
    this.debugOptions = false;
    this.compilationLevel = CompilationLevel.SIMPLE_OPTIMIZATIONS;
    this.environment = CompilerOptions.Environment.BROWSER;
    this.manageDependencies = false;
    this.prettyPrint = false;
    this.printInputDelimiter = false;
    this.preferSingleQuotes = false;
    this.generateExports = false;
    this.replaceProperties = false;
    this.forceRecompile = false;
    this.angularPass = false;
    this.replacePropertiesPrefix = "closure.define.";
    this.defineParams = new LinkedList<>();
    this.entryPointParams = new LinkedList<>();
    this.externFileLists = new LinkedList<>();
    this.sourceFileLists = new LinkedList<>();
    this.sourcePaths = new LinkedList<>();
    this.warnings = new LinkedList<>();
  }

  private static CompilerOptions.LanguageMode parseLanguageMode(String value) {
    switch (value) {
      case "ECMASCRIPT6_STRICT":
      case "ES6_STRICT":
        return CompilerOptions.LanguageMode.ECMASCRIPT6_STRICT;
      case "ECMASCRIPT6":
      case "ES6":
        return CompilerOptions.LanguageMode.ECMASCRIPT6;
      case "ECMASCRIPT5_STRICT":
      case "ES5_STRICT":
        return CompilerOptions.LanguageMode.ECMASCRIPT5_STRICT;
      case "ECMASCRIPT5":
      case "ES5":
        return CompilerOptions.LanguageMode.ECMASCRIPT5;
      case "ECMASCRIPT3":
      case "ES3":
        return CompilerOptions.LanguageMode.ECMASCRIPT3;
      default:
        throw new BuildException(
            "Unrecognized 'languageIn' option value (" + value + ")");
    }
  }

  /**
   * Set the language to which input sources conform.
   * @param value The name of the language.
   *     (ECMASCRIPT3, ECMASCRIPT5, ECMASCRIPT5_STRICT).
   */
  public void setLanguageIn(String value) {
    this.languageIn = parseLanguageMode(value);
  }

  /**
   * Set the language to which output sources conform.
   * @param value The name of the language.
   *     (ECMASCRIPT3, ECMASCRIPT5, ECMASCRIPT5_STRICT).
   */
  public void setLanguageOut(String value) {
    this.languageOut = parseLanguageMode(value);
  }

  /**
   * Set the warning level.
   * @param value The warning level by string name. (default, quiet, verbose).
   */
  public void setWarning(String value) {
    if ("default".equalsIgnoreCase(value)) {
      this.warningLevel = WarningLevel.DEFAULT;
    } else if ("quiet".equalsIgnoreCase(value)) {
      this.warningLevel = WarningLevel.QUIET;
    } else if ("verbose".equalsIgnoreCase(value)) {
      this.warningLevel = WarningLevel.VERBOSE;
    } else {
      throw new BuildException(
          "Unrecognized 'warning' option value (" + value + ")");
    }
  }

  /**
   * Set the environment which determines the builtin extern set.
   * @param value The name of the environment.
   *     (BROWSER, CUSTOM).
   */
  public void setEnvironment(String value) {
    switch (value) {
      case "BROWSER":
        this.environment = CompilerOptions.Environment.BROWSER;
        break;
      case "CUSTOM":
        this.environment = CompilerOptions.Environment.CUSTOM;
        break;
      default:
        throw new BuildException(
            "Unrecognized 'environment' option value (" + value + ")");
    }
  }

  /**
   * Enable debugging options.
   * @param value True if debug mode is enabled.
   */
  public void setDebug(boolean value) {
    this.debugOptions = value;
  }

  /**
   * Set the compilation level.
   * @param value The optimization level by string name.
   *     (whitespace, simple, advanced).
   */
  public void setCompilationLevel(String value) {
    if ("simple".equalsIgnoreCase(value)) {
      this.compilationLevel = CompilationLevel.SIMPLE_OPTIMIZATIONS;
    } else if ("advanced".equalsIgnoreCase(value)) {
      this.compilationLevel = CompilationLevel.ADVANCED_OPTIMIZATIONS;
    } else if ("whitespace".equalsIgnoreCase(value)) {
      this.compilationLevel = CompilationLevel.WHITESPACE_ONLY;
    } else {
      throw new BuildException(
          "Unrecognized 'compilation' option value (" + value + ")");
    }
  }

  public void setManageDependencies(boolean value) {
    this.manageDependencies = value;
  }

  /**
   * Set output file.
   */
  public void setOutput(File value) {
    this.outputFile = value;
  }

  /**
   * Set output wrapper.
   */
  public void setOutputWrapper(String value) {
    this.outputWrapper = value;
  }

  /**
   * Set output wrapper file.
   */
  public void setOutputWrapperFile(File value) {
    this.outputWrapperFile = value;
  }

  /**
   * Set the replacement property prefix.
   */
  public void setReplacePropertiesPrefix(String value) {
    this.replacePropertiesPrefix = value;
  }

  /**
   * Whether to replace {@code @define} lines with properties
   */
  public void setReplaceProperties(boolean value) {
    this.replaceProperties = value;
  }

  /**
   * Set input file encoding
   */
  public void setEncoding(String encoding) {
    this.encoding = encoding;
  }

  /**
   * Set output file encoding
   */
  public void setOutputEncoding(String outputEncoding) {
    this.outputEncoding = Charset.forName(outputEncoding);
  }

  /**
   * Set pretty print formatting option
   */
  public void setPrettyPrint(boolean pretty) {
    this.prettyPrint = pretty;
  }

  /**
   * Set print input delimiter formatting option
   */
  public void setPrintInputDelimiter(boolean print) {
    this.printInputDelimiter = print;
  }

  /**
   * Normally, when there are an equal number of single and double quotes
   * in a string, the compiler will use double quotes. Set this to true
   * to prefer single quotes.
   */
  public void setPreferSingleQuotes(boolean singlequotes) {
    this.preferSingleQuotes = singlequotes;
  }

  /**
   * Set force recompile option
   */
  public void setForceRecompile(boolean forceRecompile) {
    this.forceRecompile = forceRecompile;
  }

  public void setAngularPass(boolean angularPass) {
    this.angularPass = angularPass;
  }

  /**
   * Set generateExports option
   */
  public void setGenerateExports(boolean generateExports) {
   this.generateExports = generateExports;
  }

  /**
   * Sets the externs file.
   */
  public void addExterns(FileList list) {
    this.externFileLists.add(list);
  }

  /**
   * Adds a {@code <warning/>} entry
   *
   * Each warning entry must have two attributes, group and level. Group must
   * contain one of the constants from DiagnosticGroups (e.g.,
   * "ACCESS_CONTROLS"), while level must contain one of the CheckLevel
   * constants ("ERROR", "WARNING" or "OFF").
   */
  public void addWarning(Warning warning) {
    this.warnings.add(warning);
  }

  /**
   * Adds a {@code <entrypoint/>} entry
   *
   * Each entrypoint entry must have one attribute, name.
   */
  public void addEntryPoint(Parameter entrypoint) {
    this.entryPointParams.add(entrypoint);
  }

  /**
   * Sets the source files.
   */
  public void addSources(FileList list) {
    this.sourceFileLists.add(list);
  }

  /**
   * Adds a <path/> entry.
   */
  public void addPath(Path list) {
    this.sourcePaths.add(list);
  }

  @Override
  public void execute() {
    if (this.outputFile == null) {
      throw new BuildException("outputFile attribute must be set");
    }

    Compiler.setLoggingLevel(Level.OFF);

    CompilerOptions options = createCompilerOptions();
    Compiler compiler = createCompiler(options);

    List<SourceFile> externs = findExternFiles(options);
    List<SourceFile> sources = findSourceFiles();

    if (isStale() || forceRecompile) {
      log("Compiling " + sources.size() + " file(s) with " +
          externs.size() + " extern(s)");

      Result result = compiler.compile(externs, sources, options);

      if (result.success) {
        StringBuilder source = new StringBuilder(compiler.toSource());

        if (this.outputWrapperFile != null) {
          try {
            this.outputWrapper = Files.toString(this.outputWrapperFile, UTF_8);
          } catch (Exception e) {
            throw new BuildException("Invalid output_wrapper_file specified.");
          }
        }

        if (this.outputWrapper != null) {
          int pos = -1;
          pos = this.outputWrapper.indexOf(CommandLineRunner.OUTPUT_MARKER);
          if (pos > -1) {
            String prefix = this.outputWrapper.substring(0, pos);
            source.insert(0, prefix);

            // end of outputWrapper
            int suffixStart = pos + CommandLineRunner.OUTPUT_MARKER.length();
            String suffix = this.outputWrapper.substring(suffixStart);
            source.append(suffix);
          } else {
            throw new BuildException("Invalid output_wrapper specified. " +
                "Missing '" + CommandLineRunner.OUTPUT_MARKER + "'.");
          }
        }

        if (result.sourceMap != null) {
          flushSourceMap(result.sourceMap);
          source.append(System.getProperty("line.separator"));
          source.append("//# sourceMappingURL=" + sourceMapOutputFile.getName());
        }
        writeResult(source.toString());
      } else {
        throw new BuildException("Compilation failed.");
      }
    } else {
      log("None of the files changed. Compilation skipped.");
    }
  }

  private void flushSourceMap(SourceMap sourceMap) {
    try {
      FileWriter out = new FileWriter(sourceMapOutputFile);
      sourceMap.appendTo(out, sourceMapOutputFile.getName());
      out.close();
    } catch (IOException e) {
      throw new BuildException("Cannot write sourcemap to file.", e);
    }
  }

  private CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();

    this.compilationLevel.setOptionsForCompilationLevel(options);
    if (this.debugOptions) {
      this.compilationLevel.setDebugOptionsForCompilationLevel(options);
    }

    options.setEnvironment(this.environment);

    options.setPrettyPrint(this.prettyPrint);
    options.setPrintInputDelimiter(this.printInputDelimiter);
    options.setPreferSingleQuotes(this.preferSingleQuotes);
    options.setGenerateExports(this.generateExports);

    options.setLanguageIn(this.languageIn);
    options.setLanguageOut(this.languageOut);
    options.setOutputCharset(this.outputEncoding);

    this.warningLevel.setOptionsForWarningLevel(options);
    options.setManageClosureDependencies(manageDependencies);
    convertEntryPointParameters(options);
    options.setTrustedStrings(true);
    options.setAngularPass(angularPass);

    if (replaceProperties) {
      convertPropertiesMap(options);
    }

    convertDefineParameters(options);

    for (Warning warning : warnings) {
      CheckLevel level = warning.getLevel();
      String groupName = warning.getGroup();
      DiagnosticGroup group = new DiagnosticGroups().forName(groupName);
      if (group == null) {
        throw new BuildException(
            "Unrecognized 'warning' option value (" + groupName + ")");
      }
      options.setWarningLevel(group, level);
    }

    if (!Strings.isNullOrEmpty(sourceMapFormat)) {
      options.setSourceMapFormat(Format.valueOf(sourceMapFormat));
    }

    if (!Strings.isNullOrEmpty(sourceMapLocationMapping)) {
      String[] tokens = sourceMapLocationMapping.split("\\|", -1);
      LocationMapping lm = new LocationMapping(tokens[0], tokens[1]);
      options.setSourceMapLocationMappings(Arrays.asList(lm));
    }

    if (sourceMapOutputFile != null) {
      File parentFile = sourceMapOutputFile.getParentFile();
      if (parentFile.mkdirs()) {
        log("Created missing parent directory " + parentFile, Project.MSG_DEBUG);
      }
      options.setSourceMapOutputPath(parentFile.getAbsolutePath());
    }
    return options;
  }

  /**
   * Creates a new {@code <define/>} nested element. Supports name and value
   * attributes.
   */
  public Parameter createDefine() {
    Parameter param = new Parameter();
    defineParams.add(param);
    return param;
  }

  /**
   * Creates a new {@code <entrypoint/>} nested element. Supports name
   * attribute.
   */
  public Parameter createEntryPoint() {
    Parameter param = new Parameter();
    entryPointParams.add(param);
    return param;
  }

  /**
   * Converts {@code <define/>} nested elements into Compiler {@code @define}
   * replacements. Note: unlike project properties, {@code <define/>} elements
   * do not need to be named starting with the replacement prefix.
   */
  private void convertDefineParameters(CompilerOptions options) {
    for (Parameter p : defineParams) {
      String key = p.getName();
      Object value = p.getValue();

      if (!setDefine(options, key, value)) {
        log("Unexpected @define value for name=" + key + "; value=" + value);
      }
    }
  }

  /**
   * Converts {@code <entrypoint/>} nested elements into Compiler entrypoint
   * replacements.
   */
  private void convertEntryPointParameters(CompilerOptions options) {
    List<String> entryPoints = new LinkedList<>();
    for (Parameter p : entryPointParams) {
      String key = p.getName();
      entryPoints.add(key);
    }
    if (this.manageDependencies) {
      options.setManageClosureDependencies(entryPoints);
    }
  }

  /**
   * Converts project properties beginning with the replacement prefix
   * into Compiler {@code @define} replacements.
   *
   * @param options
   */
  private void convertPropertiesMap(CompilerOptions options) {
    @SuppressWarnings("unchecked")
    Map<String, Object> props = getProject().getProperties();
    for (Map.Entry<String, Object> entry : props.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      if (key.startsWith(replacePropertiesPrefix)) {
        key = key.substring(replacePropertiesPrefix.length());

        if (!setDefine(options, key, value)) {
          log("Unexpected property value for key=" + key + "; value=" + value);
        }
      }
    }
  }

  /**
   * Maps Ant-style values (e.g., from Properties) into expected
   * Closure {@code @define} literals
   *
   * @return True if the {@code @define} replacement succeeded, false if
   *         the variable's value could not be mapped properly.
   */
  private boolean setDefine(CompilerOptions options,
      String key, Object value) {
    boolean success = false;

    if (value instanceof String) {
      final boolean isTrue = "true".equals(value);
      final boolean isFalse = "false".equals(value);

      if (isTrue || isFalse) {
        options.setDefineToBooleanLiteral(key, isTrue);
      } else {
        try {
          double dblTemp = Double.parseDouble((String) value);
          options.setDefineToDoubleLiteral(key, dblTemp);
        } catch (NumberFormatException nfe) {
          // Not a number, assume string
          options.setDefineToStringLiteral(key, (String) value);
        }
      }

      success = true;
    } else if (value instanceof Boolean) {
      options.setDefineToBooleanLiteral(key, (Boolean) value);
      success = true;
    } else if (value instanceof Integer) {
      options.setDefineToNumberLiteral(key, (Integer) value);
      success = true;
    } else if (value instanceof Double) {
      options.setDefineToDoubleLiteral(key, (Double) value);
      success = true;
    }

    return success;
  }

  private Compiler createCompiler(CompilerOptions options) {
    Compiler compiler = new Compiler();
    MessageFormatter formatter =
        options.getErrorFormat().toFormatter(compiler, false);
    AntErrorManager errorManager = new AntErrorManager(formatter, this);
    compiler.setErrorManager(errorManager);
    return compiler;
  }

  private List<SourceFile> findExternFiles(CompilerOptions options) {
    List<SourceFile> files = new LinkedList<>();
    files.addAll(getBuiltinExterns(options));

    for (FileList list : this.externFileLists) {
      files.addAll(findJavaScriptFiles(list));
    }

    return files;
  }

  private List<SourceFile> findSourceFiles() {
    List<SourceFile> files = new LinkedList<>();

    for (FileList list : this.sourceFileLists) {
      files.addAll(findJavaScriptFiles(list));
    }

    for (Path list : this.sourcePaths) {
      files.addAll(findJavaScriptFiles(list));
    }

    return files;
  }

  /**
   * Translates an Ant resource collection into the file list format that
   * the compiler expects.
   */
  private List<SourceFile> findJavaScriptFiles(ResourceCollection rc) {
    List<SourceFile> files = new LinkedList<>();
    Iterator<Resource> iter = rc.iterator();
    while (iter.hasNext()) {
      FileResource fr = (FileResource) iter.next();
      // Construct path to file, relative to current working directory.
      File file = Paths.get("")
          .toAbsolutePath()
          .relativize(fr.getFile().toPath())
          .toFile();
      files.add(SourceFile.fromFile(file, Charset.forName(encoding)));
    }
    return files;
  }

  /**
   * Gets the default externs set.
   *
   * Adapted from {@link CommandLineRunner}.
   */
  private List<SourceFile> getBuiltinExterns(CompilerOptions options) {
    try {
      return CommandLineRunner.getBuiltinExterns(options.getEnvironment());
    } catch (IOException e) {
      throw new BuildException(e);
    }
  }

  private void writeResult(String source) {
    if (this.outputFile.getParentFile().mkdirs()) {
      log("Created missing parent directory " +
          this.outputFile.getParentFile(), Project.MSG_DEBUG);
    }

    try {
      OutputStreamWriter out = new OutputStreamWriter(
          new FileOutputStream(this.outputFile), outputEncoding);
      out.append(source);
      out.flush();
      out.close();
    } catch (IOException e) {
      throw new BuildException(e);
    }

    log("Compiled JavaScript written to " + this.outputFile.getAbsolutePath(),
        Project.MSG_DEBUG);
  }

  /**
   * Determine if compilation must actually happen, i.e. if any input file
   * (extern or source) has changed after the outputFile was last modified.
   *
   * @return true if compilation should happen
   */
  private boolean isStale() {
    long lastRun = outputFile.lastModified();
    long sourcesLastModified = Math.max(
        getLastModifiedTime(this.sourceFileLists),
        getLastModifiedTime(this.sourcePaths));
    long externsLastModified = getLastModifiedTime(this.externFileLists);

    return lastRun <= sourcesLastModified || lastRun <= externsLastModified;
  }

  /**
   * Returns the most recent modified timestamp of the file collection.
   *
   * Note: this must be combined into one method to account for both
   * Path and FileList erasure types.
   *
   * @param fileLists Collection of FileList or Path
   * @return Most recent modified timestamp
   */
  private long getLastModifiedTime(List<?> fileLists) {
    long lastModified = 0;

    for (Object entry : fileLists) {
      if (entry instanceof FileList) {
        FileList list = (FileList) entry;

        for (String fileName : list.getFiles(this.getProject())) {
          File path = list.getDir(this.getProject());
          File file = new File(path, fileName);
          lastModified = Math.max(getLastModifiedTime(file), lastModified);
        }
      } else if (entry instanceof Path) {
        Path path = (Path) entry;
        for (String src : path.list()) {
          File file = new File(src);
          lastModified = Math.max(getLastModifiedTime(file), lastModified);
        }
      }
    }

    return lastModified;
  }

  /**
   * Returns the last modified timestamp of the given File.
   */
  private static long getLastModifiedTime(File file) {
    long fileLastModified = file.lastModified();
    // If the file is absent, we don't know if it changed (maybe was deleted),
    // so assume it has just changed.
    if (fileLastModified == 0) {
      fileLastModified = new Date().getTime();
    }
    return fileLastModified;
  }

  public void setSourceMapFormat(String format) {
    this.sourceMapFormat = format;
  }

  public void setSourceMapOutputFile(File sourceMapOutputFile) {
    this.sourceMapOutputFile = sourceMapOutputFile;
  }

  public void setSourceMapLocationMapping(String mapping) {
    this.sourceMapLocationMapping = mapping;
  }
}
