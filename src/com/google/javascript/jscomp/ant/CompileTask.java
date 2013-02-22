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

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
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
import com.google.javascript.jscomp.WarningLevel;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileList;
import org.apache.tools.ant.types.Parameter;
import org.apache.tools.ant.types.Path;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.Date;
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
  private WarningLevel warningLevel;
  private boolean debugOptions;
  private String encoding = "UTF-8";
  private String outputEncoding = "UTF-8";
  private CompilationLevel compilationLevel;
  private boolean customExternsOnly;
  private boolean manageDependencies;
  private boolean prettyPrint;
  private boolean printInputDelimiter;
  private boolean generateExports;
  private boolean replaceProperties;
  private boolean forceRecompile;
  private String replacePropertiesPrefix;
  private File outputFile;
  private final List<Parameter> defineParams;
  private final List<FileList> externFileLists;
  private final List<FileList> sourceFileLists;
  private final List<Path> sourcePaths;
  private final List<Warning> warnings;
  private String sourceMapFormat;
  private File sourceMapOutputFile;

  public CompileTask() {
    this.languageIn = CompilerOptions.LanguageMode.ECMASCRIPT3;
    this.warningLevel = WarningLevel.DEFAULT;
    this.debugOptions = false;
    this.compilationLevel = CompilationLevel.SIMPLE_OPTIMIZATIONS;
    this.customExternsOnly = false;
    this.manageDependencies = false;
    this.prettyPrint = false;
    this.printInputDelimiter = false;
    this.generateExports = false;
    this.replaceProperties = false;
    this.forceRecompile = false;
    this.replacePropertiesPrefix = "closure.define.";
    this.defineParams = Lists.newLinkedList();
    this.externFileLists = Lists.newLinkedList();
    this.sourceFileLists = Lists.newLinkedList();
    this.sourcePaths = Lists.newLinkedList();
    this.warnings = Lists.newLinkedList();
  }

  /**
   * Set the language to which input sources conform.
   * @param value The name of the language.
   *     (ECMASCRIPT3, ECMASCRIPT5, ECMASCRIPT5_STRICT).
   */
  public void setLanguageIn(String value) {
    if (value.equals("ECMASCRIPT5_STRICT") || value.equals("ES5_STRICT")) {
      this.languageIn = CompilerOptions.LanguageMode.ECMASCRIPT5_STRICT;
    } else if (value.equals("ECMASCRIPT5") || value.equals("ES5")) {
      this.languageIn = CompilerOptions.LanguageMode.ECMASCRIPT5;
    } else if (value.equals("ECMASCRIPT3") || value.equals("ES3")) {
      this.languageIn = CompilerOptions.LanguageMode.ECMASCRIPT3;
    } else {
      throw new BuildException(
          "Unrecognized 'languageIn' option value (" + value + ")");
    }
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
   * Use only custom externs.
   */
  public void setCustomExternsOnly(boolean value) {
    this.customExternsOnly = value;
  }

  /**
   * Set output file.
   */
  public void setOutput(File value) {
    this.outputFile = value;
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
    this.outputEncoding = outputEncoding;
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
   * Set force recompile option
   */
  public void setForceRecompile(boolean forceRecompile) {
    this.forceRecompile = forceRecompile;
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
   * Adds a <warning/> entry
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

    List<SourceFile> externs = findExternFiles();
    List<SourceFile> sources = findSourceFiles();

    if (isStale() || forceRecompile) {
      log("Compiling " + sources.size() + " file(s) with " +
          externs.size() + " extern(s)");

      Result result = compiler.compile(externs, sources, options);
      if (result.success) {
        StringBuilder source = new StringBuilder(compiler.toSource());
        if (result.sourceMap != null) {
          flushSourceMap(result.sourceMap);
          source.append(System.getProperty("line.separator"));
          source.append("//@ sourceMappingURL=" + sourceMapOutputFile.getName());
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

    options.prettyPrint = this.prettyPrint;
    options.printInputDelimiter = this.printInputDelimiter;
    options.generateExports = this.generateExports;

    options.setLanguageIn(this.languageIn);

    this.warningLevel.setOptionsForWarningLevel(options);
    options.setManageClosureDependencies(manageDependencies);

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
      options.sourceMapFormat = Format.valueOf(sourceMapFormat);
    }

    if (sourceMapOutputFile != null) {
      File parentFile = sourceMapOutputFile.getParentFile();
      if (parentFile.mkdirs()) {
        log("Created missing parent directory " + parentFile, Project.MSG_DEBUG);
      }
      options.sourceMapOutputPath = parentFile.getAbsolutePath();
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
        options.errorFormat.toFormatter(compiler, false);
    AntErrorManager errorManager = new AntErrorManager(formatter, this);
    compiler.setErrorManager(errorManager);
    return compiler;
  }

  private List<SourceFile> findExternFiles() {
    List<SourceFile> files = Lists.newLinkedList();
    if (!this.customExternsOnly) {
      files.addAll(getDefaultExterns());
    }

    for (FileList list : this.externFileLists) {
      files.addAll(findJavaScriptFiles(list));
    }

    return files;
  }

  private List<SourceFile> findSourceFiles() {
    List<SourceFile> files = Lists.newLinkedList();

    for (FileList list : this.sourceFileLists) {
      files.addAll(findJavaScriptFiles(list));
    }

    for (Path list : this.sourcePaths) {
      files.addAll(findJavaScriptFiles(list));
    }

    return files;
  }

  /**
   * Translates an Ant file list into the file format that the compiler
   * expects.
   */
  private List<SourceFile> findJavaScriptFiles(FileList fileList) {
    List<SourceFile> files = Lists.newLinkedList();
    File baseDir = fileList.getDir(getProject());

    for (String included : fileList.getFiles(getProject())) {
      files.add(SourceFile.fromFile(new File(baseDir, included),
          Charset.forName(encoding)));
    }

    return files;
  }

  /**
   * Translates an Ant Path into the file list format that the compiler
   * expects.
   */
  private List<SourceFile> findJavaScriptFiles(Path path) {
    List<SourceFile> files = Lists.newArrayList();

    for (String included : path.list()) {
      files.add(SourceFile.fromFile(new File(included),
          Charset.forName(encoding)));
    }

    return files;
  }

  /**
   * Gets the default externs set.
   *
   * Adapted from {@link CommandLineRunner}.
   */
  private List<SourceFile> getDefaultExterns() {
    try {
      return CommandLineRunner.getDefaultExterns();
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
  private long getLastModifiedTime(File file) {
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
}
