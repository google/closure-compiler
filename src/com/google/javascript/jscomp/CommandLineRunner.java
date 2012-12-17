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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.common.io.LimitInputStream;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;
import org.kohsuke.args4j.spi.StringOptionHandler;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * CommandLineRunner translates flags into Java API calls on the Compiler.
 *
 * This class may be extended and used to create other Java classes
 * that behave the same as running the Compiler from the command line. If you
 * want to run the compiler in-process in Java, you should look at this class
 * for hints on what API calls to make, but you should not use this class
 * directly.
 *
 * Example:
 * <pre>
 * class MyCommandLineRunner extends CommandLineRunner {
 *   MyCommandLineRunner(String[] args) {
 *     super(args);
 *   }
 *
 *   {@code @Override} protected CompilerOptions createOptions() {
 *     CompilerOptions options = super.createOptions();
 *     addMyCrazyCompilerPassThatOutputsAnExtraFile(options);
 *     return options;
 *   }
 *
 *   public static void main(String[] args) {
 *     MyCommandLineRunner runner = new MyCommandLineRunner(args);
 *     if (runner.shouldRunCompiler()) {
 *       runner.run();
 *     } else {
 *       System.exit(-1);
 *     }
 *   }
 * }
 * </pre>
 *
 * This class is totally not thread-safe.
 *
 * @author bolinfest@google.com (Michael Bolin)
 */
public class CommandLineRunner extends
    AbstractCommandLineRunner<Compiler, CompilerOptions> {
  // I don't really care about unchecked warnings in this class.
  @SuppressWarnings("unchecked")
  private static class Flags {
    private static final WarningGuardSpec warningGuardSpec =
        new WarningGuardSpec();

    @Option(name = "--help",
        handler = BooleanOptionHandler.class,
        usage = "Displays this message")
    private boolean display_help = false;

    @Option(name = "--print_tree",
        handler = BooleanOptionHandler.class,
        usage = "Prints out the parse tree and exits")
    private boolean print_tree = false;

    @Option(name = "--print_ast",
        handler = BooleanOptionHandler.class,
        usage = "Prints a dot file describing the internal abstract syntax"
        + " tree and exits")
    private boolean print_ast = false;

    @Option(name = "--print_pass_graph",
        handler = BooleanOptionHandler.class,
        usage = "Prints a dot file describing the passes that will get run"
        + " and exits")
    private boolean print_pass_graph = false;

    // Turn on (very slow) extra sanity checks for use when modifying the
    // compiler.
    @Option(name = "--jscomp_dev_mode",
        // hidden, no usage
        aliases = {"--dev_mode"})
    private CompilerOptions.DevMode jscomp_dev_mode =
        CompilerOptions.DevMode.OFF;

    @Option(name = "--logging_level",
        usage = "The logging level (standard java.util.logging.Level"
        + " values) for Compiler progress. Does not control errors or"
        + " warnings for the JavaScript code under compilation")
    private String logging_level = Level.WARNING.getName();

    @Option(name = "--externs",
        usage = "The file containing JavaScript externs. You may specify"
        + " multiple")
    private List<String> externs = Lists.newArrayList();

    @Option(name = "--js",
        usage = "The JavaScript filename. You may specify multiple")
    private List<String> js = Lists.newArrayList();

    @Option(name = "--js_output_file",
        usage = "Primary output filename. If not specified, output is " +
        "written to stdout")
    private String js_output_file = "";

    @Option(name = "--module",
        usage = "A JavaScript module specification. The format is "
        + "<name>:<num-js-files>[:[<dep>,...][:]]]. Module names must be "
        + "unique. Each dep is the name of a module that this module "
        + "depends on. Modules must be listed in dependency order, and JS "
        + "source files must be listed in the corresponding order. Where "
        + "--module flags occur in relation to --js flags is unimportant. "
        + "Provide the value 'auto' to trigger module creation from CommonJS"
        + "modules.")
    private List<String> module = Lists.newArrayList();

    @Option(name = "--variable_map_input_file",
        usage = "File containing the serialized version of the variable "
        + "renaming map produced by a previous compilation")
    private String variable_map_input_file = "";

    @Option(name = "--property_map_input_file",
        usage = "File containing the serialized version of the property "
        + "renaming map produced by a previous compilation")
    private String property_map_input_file = "";

    @Option(name = "--variable_map_output_file",
        usage = "File where the serialized version of the variable "
        + "renaming map produced should be saved")
    private String variable_map_output_file = "";

    @Option(name = "--create_name_map_files",
        handler = BooleanOptionHandler.class,
        usage = "If true, variable renaming and property renaming map "
        + "files will be produced as {binary name}_vars_map.out and "
        + "{binary name}_props_map.out. Note that this flag cannot be used "
        + "in conjunction with either variable_map_output_file or "
        + "property_map_output_file")
    private boolean create_name_map_files = false;

    @Option(name = "--property_map_output_file",
        usage = "File where the serialized version of the property "
        + "renaming map produced should be saved")
    private String property_map_output_file = "";

    @Option(name = "--third_party",
        handler = BooleanOptionHandler.class,
        usage = "Check source validity but do not enforce Closure style "
        + "rules and conventions")
    private boolean third_party = false;


    @Option(name = "--summary_detail_level",
        usage = "Controls how detailed the compilation summary is. Values:"
        + " 0 (never print summary), 1 (print summary only if there are "
        + "errors or warnings), 2 (print summary if the 'checkTypes' "
        + "diagnostic  group is enabled, see --jscomp_warning), "
        + "3 (always print summary). The default level is 1")
    private int summary_detail_level = 1;

    @Option(name = "--output_wrapper",
        usage = "Interpolate output into this string at the place denoted"
        + " by the marker token %output%. Use marker token %output|jsstring%"
        + " to do js string escaping on the output.")
    private String output_wrapper = "";

    @Option(name = "--module_wrapper",
        usage = "An output wrapper for a JavaScript module (optional). "
        + "The format is <name>:<wrapper>. The module name must correspond "
        + "with a module specified using --module. The wrapper must "
        + "contain %s as the code placeholder. The %basename% placeholder can "
        + "also be used to substitute the base name of the module output file.")
    private List<String> module_wrapper = Lists.newArrayList();

    @Option(name = "--module_output_path_prefix",
        usage = "Prefix for filenames of compiled JS modules. "
        + "<module-name>.js will be appended to this prefix. Directories "
        + "will be created as needed. Use with --module")
    private String module_output_path_prefix = "./";

    @Option(name = "--create_source_map",
        usage = "If specified, a source map file mapping the generated " +
        "source files back to the original source file will be " +
        "output to the specified path. The %outname% placeholder will " +
        "expand to the name of the output file that the source map " +
        "corresponds to.")
    private String create_source_map = "";

    @Option(name = "--source_map_format",
        usage = "The source map format to produce. " +
        "Options: V1, V2, V3, DEFAULT. DEFAULT produces V2.")
    private SourceMap.Format source_map_format = SourceMap.Format.DEFAULT;

    // Used to define the flag, values are stored by the handler.
    @SuppressWarnings("unused")
    @Option(name = "--jscomp_error",
        handler = WarningGuardErrorOptionHandler.class,
        usage = "Make the named class of warnings an error. Options:" +
        DiagnosticGroups.DIAGNOSTIC_GROUP_NAMES)
    private List<String> jscomp_error = Lists.newArrayList();

    // Used to define the flag, values are stored by the handler.
    @SuppressWarnings("unused")
    @Option(name = "--jscomp_warning",
        handler = WarningGuardWarningOptionHandler.class,
        usage = "Make the named class of warnings a normal warning. " +
        "Options:" + DiagnosticGroups.DIAGNOSTIC_GROUP_NAMES)
    private List<String> jscomp_warning = Lists.newArrayList();

    // Used to define the flag, values are stored by the handler.
    @SuppressWarnings("unused")
    @Option(name = "--jscomp_off",
        handler = WarningGuardOffOptionHandler.class,
        usage = "Turn off the named class of warnings. Options:" +
        DiagnosticGroups.DIAGNOSTIC_GROUP_NAMES)
    private List<String> jscomp_off = Lists.newArrayList();

    @Option(name = "--define",
        aliases = {"--D", "-D"},
        usage = "Override the value of a variable annotated @define. " +
        "The format is <name>[=<val>], where <name> is the name of a @define " +
        "variable and <val> is a boolean, number, or a single-quoted string " +
        "that contains no single quotes. If [=<val>] is omitted, " +
        "the variable is marked true")
    private List<String> define = Lists.newArrayList();

    @Option(name = "--charset",
        usage = "Input and output charset for all files. By default, we " +
                "accept UTF-8 as input and output US_ASCII")
    private String charset = "";

    @Option(name = "--compilation_level",
        usage = "Specifies the compilation level to use. Options: " +
        "WHITESPACE_ONLY, SIMPLE_OPTIMIZATIONS, ADVANCED_OPTIMIZATIONS")
    private CompilationLevel compilation_level =
        CompilationLevel.SIMPLE_OPTIMIZATIONS;

    @Option(name = "--use_types_for_optimization",
        usage = "Experimental: perform additional optimizations " +
        "based on available information.  Inaccurate type annotations " +
        "may result in incorrect results.")
    private boolean use_types_for_optimization = false;

    @Option(name = "--warning_level",
        usage = "Specifies the warning level to use. Options: " +
        "QUIET, DEFAULT, VERBOSE")
    private WarningLevel warning_level = WarningLevel.DEFAULT;

    @Option(name = "--use_only_custom_externs",
        handler = BooleanOptionHandler.class,
        usage = "Specifies whether the default externs should be excluded")
    private boolean use_only_custom_externs = false;

    @Option(name = "--debug",
        handler = BooleanOptionHandler.class,
        usage = "Enable debugging options")
    private boolean debug = false;

    @Option(name = "--generate_exports",
        handler = BooleanOptionHandler.class,
        usage = "Generates export code for those marked with @export")
    private boolean generate_exports = false;

    @Option(name = "--formatting",
        usage = "Specifies which formatting options, if any, should be "
        + "applied to the output JS. Options: "
        + "PRETTY_PRINT, PRINT_INPUT_DELIMITER, SINGLE_QUOTES")
    private List<FormattingOption> formatting = Lists.newArrayList();

    @Option(name = "--process_common_js_modules",
        usage = "Process CommonJS modules to a concatenable form.")
    private boolean process_common_js_modules = false;

    @Option(name = "--common_js_module_path_prefix",
        usage = "Path prefix to be removed from CommonJS module names.")
    private String common_js_path_prefix =
        ProcessCommonJSModules.DEFAULT_FILENAME_PREFIX;

    @Option(name = "--common_js_entry_module",
        usage = "Root of your common JS dependency hierarchy. "+
            "Your main script.")
    private String common_js_entry_module;

    @Option(name = "--transform_amd_modules",
        usage = "Transform AMD to CommonJS modules.")
    private boolean transform_amd_modules = false;

    @Option(name = "--process_closure_primitives",
        handler = BooleanOptionHandler.class,
        usage = "Processes built-ins from the Closure library, such as "
        + "goog.require(), goog.provide(), and goog.exportSymbol()")
    private boolean process_closure_primitives = true;

    @Option(name = "--manage_closure_dependencies",
        handler = BooleanOptionHandler.class,
        usage = "Automatically sort dependencies so that a file that "
        + "goog.provides symbol X will always come before a file that "
        + "goog.requires symbol X. If an input provides symbols, and "
        + "those symbols are never required, then that input will not "
        + "be included in the compilation.")
    private boolean manage_closure_dependencies = false;

    @Option(name = "--only_closure_dependencies",
        handler = BooleanOptionHandler.class,
        usage = "Only include files in the transitive dependency of the "
        + "entry points (specified by closure_entry_point). Files that do "
        + "not provide dependencies will be removed. This supersedes"
        + "manage_closure_dependencies")
    private boolean only_closure_dependencies = false;

    @Option(name = "--closure_entry_point",
        usage = "Entry points to the program. Must be goog.provide'd "
        + "symbols. Any goog.provide'd symbols that are not a transitive "
        + "dependency of the entry points will be removed. Files without "
        + "goog.provides, and their dependencies, will always be left in. "
        + "If any entry points are specified, then the "
        + "manage_closure_dependencies option will be set to true and "
        + "all files will be sorted in dependency order.")
    private List<String> closure_entry_point = Lists.newArrayList();

    @Option(name = "--process_jquery_primitives",
        handler = BooleanOptionHandler.class,
        usage = "Processes built-ins from the Jquery library, such as "
        + "jQuery.fn and jQuery.extend()")
    private boolean process_jquery_primitives = false;

    @Option(name = "--output_manifest",
        usage = "Prints out a list of all the files in the compilation. "
        + "If --manage_closure_dependencies is on, this will not include "
        + "files that got dropped because they were not required. "
        + "The %outname% placeholder expands to the JS output file. "
        + "If you're using modularization, using %outname% will create "
        + "a manifest for each module.")
    private String output_manifest = "";

    @Option(name = "--output_module_dependencies",
        usage = "Prints out a JSON file of dependencies between modules.")
    private String output_module_dependencies = "";

    @Option(name = "--accept_const_keyword",
        usage = "Allows usage of const keyword.")
    private boolean accept_const_keyword = false;

    @Option(name = "--language_in",
        usage = "Sets what language spec that input sources conform. "
        + "Options: ECMASCRIPT3 (default), ECMASCRIPT5, ECMASCRIPT5_STRICT")
    private String language_in = "ECMASCRIPT3";

    @Option(name = "--version",
        handler = BooleanOptionHandler.class,
        usage = "Prints the compiler version to stderr.")
    private boolean version = false;

    @Option(name = "--translations_file",
        usage = "Source of translated messages. Currently only supports XTB.")
    private String translationsFile = "";

    @Option(name = "--translations_project",
        usage = "Scopes all translations to the specified project." +
        "When specified, we will use different message ids so that messages " +
        "in different projects can have different translations.")
    private String translationsProject = null;

    @Option(name = "--flagfile",
        usage = "A file containing additional command-line options.")
    private String flag_file = "";

    @Option(name = "--warnings_whitelist_file",
        usage = "A file containing warnings to suppress. Each line should be " +
            "of the form\n" +
            "<file-name>:<line-number>?  <warning-description>")
    private String warnings_whitelist_file = "";

    @Argument
    private List<String> arguments = Lists.newArrayList();

    /**
     * Users may specify JS inputs via the legacy {@code --js} option, as well
     * as via additional arguments to the Closure Compiler. For example, it is
     * convenient to leverage the additional arguments feature when using the
     * Closure Compiler in combination with {@code find} and {@code xargs}:
     * <pre>
     * find MY_JS_SRC_DIR -name '*.js' \
     *     | xargs java -jar compiler.jar --manage_closure_dependencies
     * </pre>
     * The {@code find} command will produce a list of '*.js' source files in
     * the {@code MY_JS_SRC_DIR} directory while {@code xargs} will convert them
     * to a single, space-delimited set of arguments that are appended to the
     * {@code java} command to run the Compiler.
     * <p>
     * Note that it is important to use the
     * {@code --manage_closure_dependencies} option in this case because the
     * order produced by {@code find} is unlikely to be sorted correctly with
     * respect to {@code goog.provide()} and {@code goog.requires()}.
     */
    List<String> getJsFiles() {
      List<String> allJsInputs = Lists.newArrayListWithCapacity(
          js.size() + arguments.size());
      allJsInputs.addAll(js);
      allJsInputs.addAll(arguments);
      return allJsInputs;
    }

    // Our own option parser to be backwards-compatible.
    // It needs to be public because of the crazy reflection that args4j does.
    public static class BooleanOptionHandler extends OptionHandler<Boolean> {
      private static final Set<String> TRUES =
          Sets.newHashSet("true", "on", "yes", "1");
      private static final Set<String> FALSES =
          Sets.newHashSet("false", "off", "no", "0");

      public BooleanOptionHandler(
          CmdLineParser parser, OptionDef option,
          Setter<? super Boolean> setter) {
        super(parser, option, setter);
      }

      @Override
      public int parseArguments(Parameters params) throws CmdLineException {
        String param = null;
        try {
          param = params.getParameter(0);
        } catch (CmdLineException e) {}

        if (param == null) {
          setter.addValue(true);
          return 0;
        } else {
          String lowerParam = param.toLowerCase();
          if (TRUES.contains(lowerParam)) {
            setter.addValue(true);
          } else if (FALSES.contains(lowerParam)) {
            setter.addValue(false);
          } else {
            setter.addValue(true);
            return 0;
          }
          return 1;
        }
      }

      @Override
      public String getDefaultMetaVariable() {
        return null;
      }
    }

    // Our own parser for warning guards that preserves the original order
    // of the flags.
    public static class WarningGuardErrorOptionHandler
        extends StringOptionHandler {
      public WarningGuardErrorOptionHandler(
          CmdLineParser parser, OptionDef option,
          Setter<? super String> setter) {
        super(parser, option, new WarningGuardSetter(setter, CheckLevel.ERROR));
      }
    }

    public static class WarningGuardWarningOptionHandler
        extends StringOptionHandler {
      public WarningGuardWarningOptionHandler(
          CmdLineParser parser, OptionDef option,
          Setter<? super String> setter) {
        super(parser, option,
            new WarningGuardSetter(setter, CheckLevel.WARNING));
      }
    }

    public static class WarningGuardOffOptionHandler
        extends StringOptionHandler {
      public WarningGuardOffOptionHandler(
          CmdLineParser parser, OptionDef option,
          Setter<? super String> setter) {
        super(parser, option, new WarningGuardSetter(setter, CheckLevel.OFF));
      }
    }

    private static class WarningGuardSetter implements Setter<String> {
      private final Setter<? super String> proxy;
      private final CheckLevel level;

      private WarningGuardSetter(
          Setter<? super String> proxy, CheckLevel level) {
        this.proxy = proxy;
        this.level = level;
      }

      @Override public boolean isMultiValued() {
        return proxy.isMultiValued();
      }

      @Override public Class<String> getType() {
        return (Class<String>) proxy.getType();
      }

      @Override public void addValue(String value) throws CmdLineException {
        proxy.addValue(value);
        warningGuardSpec.add(level, value);
      }
    }
  }

  /**
   * Set of options that can be used with the --formatting flag.
   */
  private static enum FormattingOption {
    PRETTY_PRINT,
    PRINT_INPUT_DELIMITER,
    SINGLE_QUOTES
    ;

    private void applyToOptions(CompilerOptions options) {
      switch (this) {
        case PRETTY_PRINT:
          options.prettyPrint = true;
          break;
        case PRINT_INPUT_DELIMITER:
          options.printInputDelimiter = true;
          break;
        case SINGLE_QUOTES:
          options.setPreferSingleQuotes(true);
          break;
        default:
          throw new RuntimeException("Unknown formatting option: " + this);
      }
    }
  }

  private final Flags flags = new Flags();

  private boolean isConfigValid = false;

  /**
   * Create a new command-line runner. You should only need to call
   * the constructor if you're extending this class. Otherwise, the main
   * method should instantiate it.
   */
  protected CommandLineRunner(String[] args) {
    super();
    initConfigFromFlags(args, System.err);
  }

  protected CommandLineRunner(String[] args, PrintStream out, PrintStream err) {
    super(out, err);
    initConfigFromFlags(args, err);
  }

  /**
   * Split strings into tokens delimited by whitespace, but treat quoted
   * strings as single tokens. Non-whitespace characters adjacent to quoted
   * strings will be returned as part of the token. For example, the string
   * {@code "--js='/home/my project/app.js'"} would be returned as a single
   * token.
   *
   * @param lines strings to tokenize
   * @return a list of tokens
   */
  private List<String> tokenizeKeepingQuotedStrings(List<String> lines) {
    List<String> tokens = Lists.newArrayList();
    Pattern tokenPattern =
        Pattern.compile("(?:[^ \t\f\\x0B'\"]|(?:'[^']*'|\"[^\"]*\"))+");

    for (String line : lines) {
      Matcher matcher = tokenPattern.matcher(line);
      while (matcher.find()) {
        tokens.add(matcher.group(0));
      }
    }
    return tokens;
  }

  private List<String> processArgs(String[] args) {
    // Args4j has a different format that the old command-line parser.
    // So we use some voodoo to get the args into the format that args4j
    // expects.
    Pattern argPattern = Pattern.compile("(--[a-zA-Z_]+)=(.*)");
    Pattern quotesPattern = Pattern.compile("^['\"](.*)['\"]$");
    List<String> processedArgs = Lists.newArrayList();

    for (String arg : args) {
      Matcher matcher = argPattern.matcher(arg);
      if (matcher.matches()) {
        processedArgs.add(matcher.group(1));

        String value = matcher.group(2);
        Matcher quotesMatcher = quotesPattern.matcher(value);
        if (quotesMatcher.matches()) {
          processedArgs.add(quotesMatcher.group(1));
        } else {
          processedArgs.add(value);
        }
      } else {
        processedArgs.add(arg);
      }
    }

    return processedArgs;
  }

  private void processFlagFile(PrintStream err)
            throws CmdLineException, IOException {
    File flagFileInput = new File(flags.flag_file);
    List<String> argsInFile = tokenizeKeepingQuotedStrings(
        Files.readLines(flagFileInput, Charset.defaultCharset()));

    flags.flag_file = "";
    List<String> processedFileArgs
        = processArgs(argsInFile.toArray(new String[] {}));
    CmdLineParser parserFileArgs = new CmdLineParser(flags);
    Flags.warningGuardSpec.clear();
    parserFileArgs.parseArgument(processedFileArgs.toArray(new String[] {}));

    // Currently we are not supporting this (prevent direct/indirect loops)
    if (!flags.flag_file.equals("")) {
      err.println("ERROR - Arguments in the file cannot contain "
          + "--flagfile option.");
      isConfigValid = false;
    }
  }

  private void initConfigFromFlags(String[] args, PrintStream err) {

    List<String> processedArgs = processArgs(args);

    CmdLineParser parser = new CmdLineParser(flags);
    Flags.warningGuardSpec.clear();
    isConfigValid = true;
    try {
      parser.parseArgument(processedArgs.toArray(new String[] {}));
      // For contains --flagfile flag
      if (!flags.flag_file.equals("")) {
        processFlagFile(err);
      }
    } catch (CmdLineException e) {
      err.println(e.getMessage());
      isConfigValid = false;
    } catch (IOException ioErr) {
      err.println("ERROR - " + flags.flag_file + " read error.");
      isConfigValid = false;
    }

    if (flags.version) {
      err.println(
          "Closure Compiler (http://code.google.com/closure/compiler)\n" +
          "Version: " + Compiler.getReleaseVersion() + "\n" +
          "Built on: " + Compiler.getReleaseDate());
      err.flush();
    }

    if (flags.process_common_js_modules) {
      flags.process_closure_primitives = true;
      flags.manage_closure_dependencies = true;
      if (flags.common_js_entry_module == null) {
        err.println("Please specify --common_js_entry_module.");
        err.flush();
        isConfigValid = false;
      }
      flags.closure_entry_point = Lists.newArrayList(
          ProcessCommonJSModules.toModuleName(flags.common_js_entry_module));
    }

    if (!isConfigValid || flags.display_help) {
      isConfigValid = false;
      parser.printUsage(err);
    } else {
      CodingConvention conv;
      if (flags.third_party) {
        conv = CodingConventions.getDefault();
      } else if (flags.process_jquery_primitives) {
        conv = new JqueryCodingConvention();
      } else {
        conv = new ClosureCodingConvention();
      }

      getCommandLineConfig()
          .setPrintTree(flags.print_tree)
          .setPrintAst(flags.print_ast)
          .setPrintPassGraph(flags.print_pass_graph)
          .setJscompDevMode(flags.jscomp_dev_mode)
          .setLoggingLevel(flags.logging_level)
          .setExterns(flags.externs)
          .setJs(flags.getJsFiles())
          .setJsOutputFile(flags.js_output_file)
          .setModule(flags.module)
          .setVariableMapInputFile(flags.variable_map_input_file)
          .setPropertyMapInputFile(flags.property_map_input_file)
          .setVariableMapOutputFile(flags.variable_map_output_file)
          .setCreateNameMapFiles(flags.create_name_map_files)
          .setPropertyMapOutputFile(flags.property_map_output_file)
          .setCodingConvention(conv)
          .setSummaryDetailLevel(flags.summary_detail_level)
          .setOutputWrapper(flags.output_wrapper)
          .setModuleWrapper(flags.module_wrapper)
          .setModuleOutputPathPrefix(flags.module_output_path_prefix)
          .setCreateSourceMap(flags.create_source_map)
          .setSourceMapFormat(flags.source_map_format)
          .setWarningGuardSpec(Flags.warningGuardSpec)
          .setDefine(flags.define)
          .setCharset(flags.charset)
          .setManageClosureDependencies(flags.manage_closure_dependencies)
          .setOnlyClosureDependencies(flags.only_closure_dependencies)
          .setClosureEntryPoints(flags.closure_entry_point)
          .setOutputManifest(ImmutableList.of(flags.output_manifest))
          .setOutputModuleDependencies(flags.output_module_dependencies)
          .setAcceptConstKeyword(flags.accept_const_keyword)
          .setLanguageIn(flags.language_in)
          .setProcessCommonJSModules(flags.process_common_js_modules)
          .setCommonJSModulePathPrefix(flags.common_js_path_prefix)
          .setTransformAMDToCJSModules(flags.transform_amd_modules)
          .setWarningsWhitelistFile(flags.warnings_whitelist_file);
    }
  }

  @Override
  protected CompilerOptions createOptions() {
    CompilerOptions options = new CompilerOptions();
    if (flags.process_jquery_primitives) {
      options.setCodingConvention(new JqueryCodingConvention());
    } else {
      options.setCodingConvention(new ClosureCodingConvention());
    }

    CompilationLevel level = flags.compilation_level;
    level.setOptionsForCompilationLevel(options);

    if (flags.debug) {
      level.setDebugOptionsForCompilationLevel(options);
    }

    if (flags.use_types_for_optimization) {
      level.setTypeBasedOptimizationOptions(options);
    }

    if (flags.generate_exports) {
      options.setGenerateExports(flags.generate_exports);
    }

    WarningLevel wLevel = flags.warning_level;
    wLevel.setOptionsForWarningLevel(options);
    for (FormattingOption formattingOption : flags.formatting) {
      formattingOption.applyToOptions(options);
    }

    options.closurePass = flags.process_closure_primitives;

    options.jqueryPass = flags.process_jquery_primitives &&
        CompilationLevel.ADVANCED_OPTIMIZATIONS == level;

    if (!flags.translationsFile.isEmpty()) {
      try {
        options.messageBundle = new XtbMessageBundle(
            new FileInputStream(flags.translationsFile),
            flags.translationsProject);
      } catch (IOException e) {
        throw new RuntimeException("Reading XTB file", e);
      }
    } else if (CompilationLevel.ADVANCED_OPTIMIZATIONS == level) {
      // In SIMPLE or WHITESPACE mode, if the user hasn't specified a
      // translations file, they might reasonably try to write their own
      // implementation of goog.getMsg that makes the substitution at
      // run-time.
      //
      // In ADVANCED mode, goog.getMsg is going to be renamed anyway,
      // so we might as well inline it.
      options.messageBundle = new EmptyMessageBundle();
    }

    return options;
  }

  @Override
  protected Compiler createCompiler() {
    return new Compiler(getErrorPrintStream());
  }

  @Override
  protected List<SourceFile> createExterns() throws FlagUsageException,
      IOException {
    List<SourceFile> externs = super.createExterns();
    if (flags.use_only_custom_externs || isInTestMode()) {
      return externs;
    } else {
      List<SourceFile> defaultExterns = getDefaultExterns();
      defaultExterns.addAll(externs);
      return defaultExterns;
    }
  }

  // The externs expected in externs.zip, in sorted order.
  private static final List<String> DEFAULT_EXTERNS_NAMES = ImmutableList.of(
    // JS externs
    "es3.js",
    "es5.js",

    // Event APIs
    "w3c_event.js",
    "w3c_event3.js",
    "gecko_event.js",
    "ie_event.js",
    "webkit_event.js",

    // DOM apis
    "w3c_dom1.js",
    "w3c_dom2.js",
    "w3c_dom3.js",
    "gecko_dom.js",
    "ie_dom.js",
    "webkit_dom.js",

    // CSS apis
    "w3c_css.js",
    "gecko_css.js",
    "ie_css.js",
    "webkit_css.js",

    // Top-level namespaces
    "google.js",

    "deprecated.js",
    "fileapi.js",
    "flash.js",
    "gears_symbols.js",
    "gears_types.js",
    "gecko_xml.js",
    "html5.js",
    "ie_vml.js",
    "iphone.js",
    "webstorage.js",
    "w3c_anim_timing.js",
    "w3c_css3d.js",
    "w3c_elementtraversal.js",
    "w3c_geolocation.js",
    "w3c_indexeddb.js",
    "w3c_navigation_timing.js",
    "w3c_range.js",
    "w3c_selectors.js",
    "w3c_xml.js",
    "window.js",
    "webkit_notifications.js",
    "webgl.js");

  /**
   * @return a mutable list
   * @throws IOException
   */
  public static List<SourceFile> getDefaultExterns() throws IOException {
    InputStream input = CommandLineRunner.class.getResourceAsStream(
        "/externs.zip");
    if (input == null) {
      // In some environments, the externs.zip is relative to this class.
      input = CommandLineRunner.class.getResourceAsStream("externs.zip");
    }
    Preconditions.checkNotNull(input);

    ZipInputStream zip = new ZipInputStream(input);
    Map<String, SourceFile> externsMap = Maps.newHashMap();
    for (ZipEntry entry = null; (entry = zip.getNextEntry()) != null; ) {
      BufferedInputStream entryStream = new BufferedInputStream(
          new LimitInputStream(zip, entry.getSize()));
      externsMap.put(entry.getName(),
          SourceFile.fromInputStream(
              // Give the files an odd prefix, so that they do not conflict
              // with the user's files.
              "externs.zip//" + entry.getName(),
              entryStream));
    }

    Preconditions.checkState(
        externsMap.keySet().equals(Sets.newHashSet(DEFAULT_EXTERNS_NAMES)),
        "Externs zip must match our hard-coded list of externs.");

    // Order matters, so the resources must be added to the result list
    // in the expected order.
    List<SourceFile> externs = Lists.newArrayList();
    for (String key : DEFAULT_EXTERNS_NAMES) {
      externs.add(externsMap.get(key));
    }

    return externs;
  }

  /**
   * @return Whether the configuration is valid.
   */
  public boolean shouldRunCompiler() {
    return this.isConfigValid;
  }

  /**
   * Runs the Compiler. Exits cleanly in the event of an error.
   */
  public static void main(String[] args) {
    CommandLineRunner runner = new CommandLineRunner(args);
    if (runner.shouldRunCompiler()) {
      runner.run();
    } else {
      System.exit(-1);
    }
  }
}
