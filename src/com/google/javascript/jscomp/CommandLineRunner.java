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
import com.google.common.flags.DocLevel;
import com.google.common.flags.Flag;
import com.google.common.flags.FlagSpec;
import com.google.common.flags.Flags;
import com.google.common.io.LimitInputStream;
import com.google.javascript.jscomp.AbstractCommandLineRunner.CommandLineConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * CommandLineRunner translates flags into Java API calls on the Compiler.
 *
 * This class may be extended and used to create other Java classes
 * that behave the same as running the Compiler from the command line. If you
 * want to run the compiler in-process in Java, you should look at this class
 * for hints on what API calls to make, but you should not use this class directly.
 *
 * Example:
 * <pre>
 * class MyCommandLineRunner extends CommandLineRunner {
 *   MyCommandLineRunner(String[] args) { super(args); }
 *
 *   {@code @Override} protected CompilerOptions createOptions() {
 *     CompilerOptions options = super.createOptions();
 *     addMyCrazyCompilerPassThatOutputsAnExtraFile(options);
 *     return options;
 *   }
 *
 *   public static void main(String[] args) {
 *     (new MyCommandLineRunner(args)).run();
 *   }
 * }
 * </pre>
*
 */
public class CommandLineRunner extends
    AbstractCommandLineRunner<Compiler, CompilerOptions> {

  @FlagSpec(help = "Prints out the parse tree and exits",
      docLevel = DocLevel.SECRET)
  static final Flag<Boolean> FLAG_print_tree = Flag.value(false);

  @FlagSpec(help = "Runs the compile job many times, then prints out the " +
      "best phase ordering from this run",
      docLevel = DocLevel.SECRET)
  static final Flag<Boolean> FLAG_compute_phase_ordering =
      Flag.value(false);

  @FlagSpec(help = "Prints a dot file describing the internal abstract syntax"
      + " tree and exits",
      docLevel = DocLevel.SECRET)
  static final Flag<Boolean> FLAG_print_ast = Flag.value(false);

  @FlagSpec(help = "Prints a dot file describing the passes that will get run"
      + " and exits",
      docLevel = DocLevel.SECRET)
  static final Flag<Boolean> FLAG_print_pass_graph = Flag.value(false);

  @FlagSpec(help = "Turns on extra sanity checks", altName = "dev_mode",
      docLevel = DocLevel.SECRET)
  static final Flag<CompilerOptions.DevMode> FLAG_jscomp_dev_mode =
      Flag.value(CompilerOptions.DevMode.OFF);

  // TODO(nicksantos): Make the next 2 flags package-private.
  @FlagSpec(help = "The logging level (standard java.util.logging.Level"
      + " values) for Compiler progress. Does not control errors or"
      + " warnings for the JavaScript code under compilation",
      docLevel = DocLevel.SECRET)
  public static final Flag<String> FLAG_logging_level =
      Flag.value(Level.WARNING.getName());

  @FlagSpec(help = "The file containing javascript externs. You may specify"
      + " multiple")
  public static final Flag<List<String>> FLAG_externs = Flag.stringCollector();

  @FlagSpec(help = "The javascript filename. You may specify multiple")
  static final Flag<List<String>> FLAG_js = Flag.stringCollector();

  @FlagSpec(help = "Primary output filename. If not specified, output is " +
            "written to stdout")
  static final Flag<String> FLAG_js_output_file = Flag.value("");

  @FlagSpec(help = "A javascript module specification. The format is "
      + "<name>:<num-js-files>[:[<dep>,...][:]]]. Module names must be "
      + "unique. Each dep is the name of a module that this module "
      + "depends on. Modules must be listed in dependency order, and js "
      + "source files must be listed in the corresponding order. Where "
      + "--module flags occur in relation to --js flags is unimportant")
  static final Flag<List<String>> FLAG_module = Flag.stringCollector();

  @FlagSpec(help = "File containing the serialized version of the variable "
      + "renaming map produced by a previous compilation")
  static final Flag<String> FLAG_variable_map_input_file =
      Flag.value("");

  @FlagSpec(help = "File containing the serialized version of the property "
      + "renaming map produced by a previous compilation",
      docLevel = DocLevel.SECRET)
  static final Flag<String> FLAG_property_map_input_file =
      Flag.value("");

  @FlagSpec(help = "File where the serialized version of the variable "
      + "renaming map produced should be saved",
      docLevel = DocLevel.SECRET)
  static final Flag<String> FLAG_variable_map_output_file =
      Flag.value("");

  @FlagSpec(help = "If true, variable renaming and property renaming map "
      + "files will be produced as {binary name}_vars_map.out and "
      + "{binary name}_props_map.out. Note that this flag cannot be used "
      + "in conjunction with either variable_map_output_file or "
      + "property_map_output_file",
      docLevel = DocLevel.SECRET)
  static final Flag<Boolean> FLAG_create_name_map_files =
      Flag.value(false);

  @FlagSpec(help = "File where the serialized version of the property "
      + "renaming map produced should be saved")
  static final Flag<String> FLAG_property_map_output_file =
      Flag.value("");

  @FlagSpec(help = "Check source validity but do not enforce Closure style "
      + "rules and conventions")
  static final Flag<Boolean> FLAG_third_party = Flag.value(false);


  @FlagSpec(help = "Controls how detailed the compilation summary is. Values:"
      + " 0 (never print summary), 1 (print summary only if there are "
      + "errors or warnings), 2 (print summary if type checking is on, "
      + "see --check_types), 3 (always print summary). The default level "
      + "is 1")
  static final Flag<Integer> FLAG_summary_detail_level = Flag.value(1);

  @FlagSpec(help = "Interpolate output into this string at the place denoted"
      + " by the marker token %output%. See --output_wrapper_marker")
  static final Flag<String> FLAG_output_wrapper = Flag.value("");

  @FlagSpec(help = "Use this token as output marker in the value of"
      + " --output_wrapper")
  static final Flag<String> FLAG_output_wrapper_marker =
      Flag.value("%output%");

  @FlagSpec(help = "An output wrapper for a javascript module (optional). "
      + "The format is <name>:<wrapper>. The module name must correspond "
      + "with a module specified using --module. The wrapper must "
      + "contain %s as the code placeholder")
  static final Flag<List<String>> FLAG_module_wrapper =
      Flag.stringCollector();

  @FlagSpec(help = "Prefix for filenames of compiled js modules. "
      + "<module-name>.js will be appended to this prefix. Directories "
      + "will be created as needed. Use with --module")
  static final Flag<String> FLAG_module_output_path_prefix =
      Flag.value("./");

  @FlagSpec(help = "If specified, a source map file mapping the generated " +
            "source files back to the original source file will be " +
            "output to the specified path. The %outname% placeholder will " +
            "expand to the name of the output file that the source map " +
            "corresponds to.")
  static final Flag<String> FLAG_create_source_map =
      Flag.value("");

  @FlagSpec(help = "Make the named class of warnings an error. Options:" +
      DiagnosticGroups.DIAGNOSTIC_GROUP_NAMES)
  static final Flag<List<String>> FLAG_jscomp_error =
      Flag.stringCollector();

  @FlagSpec(help = "Make the named class of warnings a normal warning. " +
                "Options:" + DiagnosticGroups.DIAGNOSTIC_GROUP_NAMES)
  static final Flag<List<String>> FLAG_jscomp_warning =
      Flag.stringCollector();

  @FlagSpec(help = "Turn off the named class of warnings. Options:" +
      DiagnosticGroups.DIAGNOSTIC_GROUP_NAMES)
  static final Flag<List<String>> FLAG_jscomp_off =
      Flag.stringCollector();

  @FlagSpec(altName = "D",
      help = "Override the value of a variable annotated @define. " +
      "The format is <name>[=<val>], where <name> is the name of a @define " +
      "variable and <val> is a boolean, number, or a single-quoted string " +
      "that contains no single quotes. If [=<val>] is omitted, " +
      "the variable is marked true")
  static final Flag<List<String>> FLAG_define = Flag.stringCollector();

  @FlagSpec(help = "Input charset for all files.")
  static final Flag<String> FLAG_charset = Flag.value("");

  @FlagSpec(help = "Specifies the compilation level to use. Options: " +
            "WHITESPACE_ONLY, SIMPLE_OPTIMIZATIONS, ADVANCED_OPTIMIZATIONS")
  static final Flag<CompilationLevel> FLAG_compilation_level
      = Flag.value(CompilationLevel.SIMPLE_OPTIMIZATIONS);

  @FlagSpec(help = "Specifies the warning level to use. Options: " +
            "QUIET, DEFAULT, VERBOSE")
  static final Flag<WarningLevel> FLAG_warning_level
      = Flag.value(WarningLevel.DEFAULT);

  @FlagSpec(help = "Specifies whether the default externs should be excluded")
  static final Flag<Boolean> FLAG_use_only_custom_externs
      = Flag.value(false);

  @FlagSpec(help = "Enable debugging options")
  static final Flag<Boolean> FLAG_debug = Flag.value(false);

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
      + "applied to the output JS. Options: "
      + "PRETTY_PRINT, PRINT_INPUT_DELIMITER")
  static final Flag<List<FormattingOption>> FLAG_formatting
      = Flag.enumList(FormattingOption.class);

  @FlagSpec(help = "Processes built-ins from the Closure library, such as "
      + "goog.require(), goog.provide(), and goog.exportSymbol()")
  static final Flag<Boolean> FLAG_process_closure_primitives
      = Flag.value(true);

  /**
   * Create a new command-line runner. You should only need to call
   * the constructor if you're extending this class. Otherwise, the main
   * method should instantiate it.
   */
  protected CommandLineRunner(String[] args) {
    super(readConfigFromFlags(args));
  }

  protected CommandLineRunner(String[] args, PrintStream out, PrintStream err) {
    super(readConfigFromFlags(args), out, err);
  }

  private static CommandLineConfig readConfigFromFlags(String[] args) {
    Flags.parse(args);
    return new CommandLineConfig()
        .setPrintTree(FLAG_print_tree.get())
        .setComputePhaseOrdering(FLAG_compute_phase_ordering.get())
        .setPrintAst(FLAG_print_ast.get())
        .setPrintPassGraph(FLAG_print_pass_graph.get())
        .setJscompDevMode(FLAG_jscomp_dev_mode.get())
        .setLoggingLevel(FLAG_logging_level.get())
        .setExterns(FLAG_externs.get())
        .setJs(FLAG_js.get())
        .setJsOutputFile(FLAG_js_output_file.get())
        .setModule(FLAG_module.get())
        .setVariableMapInputFile(FLAG_variable_map_input_file.get())
        .setPropertyMapInputFile(FLAG_property_map_input_file.get())
        .setVariableMapOutputFile(FLAG_variable_map_output_file.get())
        .setCreateNameMapFiles(FLAG_create_name_map_files.get())
        .setPropertyMapOutputFile(FLAG_property_map_output_file.get())
        .setThirdParty(FLAG_third_party.get())
        .setSummaryDetailLevel(FLAG_summary_detail_level.get())
        .setOutputWrapper(FLAG_output_wrapper.get())
        .setOutputWrapperMarker(FLAG_output_wrapper_marker.get())
        .setModuleWrapper(FLAG_module_wrapper.get())
        .setModuleOutputPathPrefix(FLAG_module_output_path_prefix.get())
        .setCreateSourceMap(FLAG_create_source_map.get())
        .setJscompError(FLAG_jscomp_error.get())
        .setJscompWarning(FLAG_jscomp_warning.get())
        .setJscompOff(FLAG_jscomp_off.get())
        .setDefine(FLAG_define.get())
        .setCharset(FLAG_charset.get());
  }

  @Override
  protected CompilerOptions createOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setCodingConvention(new ClosureCodingConvention());
    CompilationLevel level = FLAG_compilation_level.get();
    level.setOptionsForCompilationLevel(options);
    if (FLAG_debug.get()) {
      level.setDebugOptionsForCompilationLevel(options);
    }

    WarningLevel wLevel = FLAG_warning_level.get();
    wLevel.setOptionsForWarningLevel(options);
    for (FormattingOption formattingOption : FLAG_formatting.get()) {
      formattingOption.applyToOptions(options);
    }
    if (FLAG_process_closure_primitives.get()) {
      options.closurePass = true;
    }

    initOptionsFromFlags(options);
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
    InputStream input = CommandLineRunner.class.getResourceAsStream(
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
    (new CommandLineRunner(args)).run();
  }
}
