/*
 * Copyright 2010 Google Inc.
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

import com.google.common.collect.Lists;
import com.google.javascript.jscomp.CommandLineRunner;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.JSSourceFile;
import com.google.javascript.jscomp.MessageFormatter;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.WarningLevel;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.List;
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
  private WarningLevel warningLevel;
  private boolean debugOptions;
  private String encoding = "UTF-8";
  private String outputEncoding = "UTF-8";
  private CompilationLevel compilationLevel;
  private boolean customExternsOnly;
  private boolean manageDependencies;
  private File outputFile;
  private final List<FileList> externFileLists;
  private final List<FileList> sourceFileLists;

  public CompileTask() {
    this.warningLevel = WarningLevel.DEFAULT;
    this.debugOptions = false;
    this.compilationLevel = CompilationLevel.SIMPLE_OPTIMIZATIONS;
    this.customExternsOnly = false;
    this.manageDependencies = false;
    this.externFileLists = Lists.newLinkedList();
    this.sourceFileLists = Lists.newLinkedList();
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
   * Sets the externs file.
   */
  public void addExterns(FileList list) {
    this.externFileLists.add(list);
  }

  /**
   * Sets the source files.
   */
  public void addSources(FileList list) {
    this.sourceFileLists.add(list);
  }

  public void execute() {
    if (this.outputFile == null) {
      throw new BuildException("outputFile attribute must be set");
    }

    Compiler.setLoggingLevel(Level.OFF);

    CompilerOptions options = createCompilerOptions();
    Compiler compiler = createCompiler(options);

    JSSourceFile[] externs = findExternFiles();
    JSSourceFile[] sources = findSourceFiles();

    log("Compiling " + sources.length + " file(s) with " +
        externs.length + " extern(s)");

    Result result = compiler.compile(externs, sources, options);
    if (result.success) {
      writeResult(compiler.toSource());
    } else {
      throw new BuildException("Compilation failed.");
    }
  }

  private CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();

    if (this.debugOptions) {
      this.compilationLevel.setDebugOptionsForCompilationLevel(options);
    } else {
      this.compilationLevel.setOptionsForCompilationLevel(options);
    }

    this.warningLevel.setOptionsForWarningLevel(options);
    options.setManageClosureDependencies(manageDependencies);
    return options;
  }

  private Compiler createCompiler(CompilerOptions options) {
    Compiler compiler = new Compiler();
    MessageFormatter formatter =
        options.errorFormat.toFormatter(compiler, false);
    AntErrorManager errorManager = new AntErrorManager(formatter, this);
    compiler.setErrorManager(errorManager);
    return compiler;
  }

  private JSSourceFile[] findExternFiles() {
    List<JSSourceFile> files = Lists.newLinkedList();
    if (!this.customExternsOnly) {
      files.addAll(getDefaultExterns());
    }

    for (FileList list : this.externFileLists) {
      files.addAll(findJavaScriptFiles(list));
    }

    return files.toArray(new JSSourceFile[files.size()]);
  }

  private JSSourceFile[] findSourceFiles() {
    List<JSSourceFile> files = Lists.newLinkedList();

    for (FileList list : this.sourceFileLists) {
      files.addAll(findJavaScriptFiles(list));
    }

    return files.toArray(new JSSourceFile[files.size()]);
  }

  /**
   * Translates an Ant file list into the file format that the compiler
   * expects.
   */
  private List<JSSourceFile> findJavaScriptFiles(FileList fileList) {
    List<JSSourceFile> files = Lists.newLinkedList();
    File baseDir = fileList.getDir(getProject());

    for (String included : fileList.getFiles(getProject())) {
      files.add(JSSourceFile.fromFile(new File(baseDir, included),
          Charset.forName(encoding)));
    }

    return files;
  }

  /**
   * Gets the default externs set.
   *
   * Adapted from {@link CommandLineRunner}.
   */
  private List<JSSourceFile> getDefaultExterns() {
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

    log("Compiled javascript written to " + this.outputFile.getAbsolutePath(),
        Project.MSG_DEBUG);
  }
}
