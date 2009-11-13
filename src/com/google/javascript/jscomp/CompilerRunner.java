/*
 * Copyright 2009 Google Inc.
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

import com.google.common.collect.Lists;
import com.google.common.flags.Flag;
import com.google.common.flags.FlagSpec;
import com.google.common.io.LimitInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * CompilerRunner encapsulates the logic required to run the Compiler.
 * This class is designed to be extended and used to create other Java classes
 * that behave the same as running the Compiler from the command line. Example:
 * <pre>
 * class MyCompilerRunner extends CompilerRunner {
 *   MyCompilerRunner(String[] args) { super(args); }
 *
 *   {@code @Override} protected CompilerOptions createOptions() {
 *     CompilerOptions options = super.createOptions();
 *     addMyCrazyCompilerPassThatOutputsAnExtraFile(options);
 *     return options;
 *   }
 *
 *   public static void main(String[] args) {
 *     (new MyCompilerRunner(args)).run();
 *   }
 * }
 * </pre>
*
 */
public class CompilerRunner extends
    AbstractCompilerRunner<Compiler, CompilerOptions> {

  @FlagSpec(help = "Specifies the compilation level to use. Options: " +
            "WHITESPACE_ONLY, SIMPLE_OPTIMIZATIONS, ADVANCED_OPTIMIZATIONS")
  private static final Flag<CompilationLevel> FLAG_compilation_level
      = Flag.value(CompilationLevel.SIMPLE_OPTIMIZATIONS);

  @FlagSpec(help = "Specifies the warning level to use. Options: " +
            "QUIET, DEFAULT, VERBOSE")
  static final Flag<WarningLevel> FLAG_warning_level
      = Flag.value(WarningLevel.DEFAULT);

  @FlagSpec(help = "Specifies whether the default externs should be excluded.")
  private static final Flag<Boolean> FLAG_use_only_custom_externs
      = Flag.value(false);

  /**
   * Set of options that can be used with the --formatting flag.
   */
  private static enum FormattingOption {
    PRETTY_PRINT,
    PRINT_INPUT_DELIMITER,
    ;

    private void applyToOptions(CompilerOptions options) {
      switch (this) {
        case PRETTY_PRINT:
          options.prettyPrint = true;
          break;
        case PRINT_INPUT_DELIMITER:
          options.printInputDelimiter = true;
          break;
        default:
          throw new RuntimeException("Unknown formatting option: " + this);
      }
    }
  }

  @FlagSpec(help = "Specifies which formatting options, if any, should be "
      + "applied to the output JS")
  private static final Flag<List<FormattingOption>> FLAG_formatting
      = Flag.enumList(FormattingOption.class);

  @FlagSpec(help = "Processes built-ins from the Closure library, such as "
      + "goog.require(), goog.provide(), and goog.exportSymbol().")
  private static final Flag<Boolean> FLAG_process_closure_primitives
      = Flag.value(true);

  public CompilerRunner(String[] args) {
    super(args);
  }

  public CompilerRunner(String[] args, PrintStream out, PrintStream err) {
    super(args, out, err);
  }

  @Override
  protected CompilerOptions createOptions() {
    CompilerOptions options = new CompilerOptions();
    CompilationLevel level = FLAG_compilation_level.get();
    level.setOptionsForCompilationLevel(options);
    WarningLevel wLevel = FLAG_warning_level.get();
    wLevel.setOptionsForWarningLevel(options);
    for (FormattingOption formattingOption : FLAG_formatting.get()) {
      formattingOption.applyToOptions(options);
    }
    if (FLAG_process_closure_primitives.get()) {
      options.closurePass = true;
    }

    DiagnosticGroups.setWarningLevels(
        options, AbstractCompilerRunner.FLAG_jscomp_error.get(),
        CheckLevel.ERROR);
    DiagnosticGroups.setWarningLevels(
        options, AbstractCompilerRunner.FLAG_jscomp_warning.get(),
        CheckLevel.WARNING);
    DiagnosticGroups.setWarningLevels(
        options, AbstractCompilerRunner.FLAG_jscomp_off.get(),
        CheckLevel.OFF);

    return options;
  }

  @Override
  protected Compiler createCompiler() {
    return new Compiler(getErrorPrintStream());
  }

  @Override
  protected List<JSSourceFile> createExterns() throws FlagUsageException,
      IOException {
    List<JSSourceFile> externs = super.createExterns();
    if (!FLAG_use_only_custom_externs.get()) {
      List<JSSourceFile> defaultExterns = getDefaultExterns();
      defaultExterns.addAll(externs);
      return defaultExterns;
    } else {
      return externs;
    }
  }

  /**
   * @return a mutable list
   * @throws IOException
   */
  private List<JSSourceFile> getDefaultExterns() throws IOException {
    InputStream input = CompilerRunner.class.getResourceAsStream(
        "/externs.zip");
    ZipInputStream zip = new ZipInputStream(input);
    List<JSSourceFile> externs = Lists.newLinkedList();
    for (ZipEntry entry = null; (entry = zip.getNextEntry()) != null; ) {
      LimitInputStream entryStream = new LimitInputStream(zip, entry.getSize());
      externs.add(JSSourceFile.fromInputStream(entry.getName(), entryStream));
    }
    return externs;
  }

  /**
   * Runs the Compiler. Exits cleanly in the event of an error.
   */
  public static void main(String[] args) {
    (new CompilerRunner(args)).run();
  }
}
