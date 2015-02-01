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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.javascript.jscomp.SourceMap.LocationMapping;
import com.google.protobuf.TextFormat;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.OptionHandlerFilter;
import org.kohsuke.args4j.spi.FieldSetter;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;
import org.kohsuke.args4j.spi.StringOptionHandler;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.lang.reflect.AnnotatedElement;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
 *     }
 *     if (runner.hasErrors()) {
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

  public static final String OUTPUT_MARKER =
      AbstractCommandLineRunner.OUTPUT_MARKER;

  // UTF-8 BOM is 0xEF, 0xBB, 0xBF, of which character code is 65279.
  public static final int UTF8_BOM_CODE = 65279;

  private static class GuardLevel {
    final String name;
    final CheckLevel level;
    GuardLevel(String name, CheckLevel level) {
      this.name = name;
      this.level = level;
    }
  }

  // I don't really care about unchecked warnings in this class.
  @SuppressWarnings("unchecked")
  private static class Flags {
    // Some clients run a few copies of the compiler through CommandLineRunner
    // on parallel threads (thankfully, with the same flags),
    // so the access to |guardLevels| should be at least synchronized.
    private static List<GuardLevel> guardLevels =
        Collections.synchronizedList(new ArrayList<CommandLineRunner.GuardLevel>());

    @Option(name = "--help",
        hidden = true,
        handler = BooleanOptionHandler.class,
        usage = "Displays this message on stdout and exit")
    private boolean displayHelp = false;

    @Option(name = "--print_tree",
        hidden = true,
        handler = BooleanOptionHandler.class,
        usage = "Prints out the parse tree and exits")
    private boolean printTree = false;

    @Option(name = "--print_ast",
        hidden = true,
        handler = BooleanOptionHandler.class,
        usage = "Prints a dot file describing the internal abstract syntax"
        + " tree and exits")
    private boolean printAst = false;

    @Option(name = "--print_pass_graph",
        hidden = true,
        handler = BooleanOptionHandler.class,
        usage = "Prints a dot file describing the passes that will get run"
        + " and exits")
    private boolean printPassGraph = false;

    // Turn on (very slow) extra sanity checks for use when modifying the
    // compiler.
    @Option(name = "--jscomp_dev_mode",
        // hidden, no usage
        aliases = {"--dev_mode"})
    private CompilerOptions.DevMode jscompDevMode =
        CompilerOptions.DevMode.OFF;

    @Option(name = "--logging_level",
        hidden = true,
        usage = "The logging level (standard java.util.logging.Level"
        + " values) for Compiler progress. Does not control errors or"
        + " warnings for the JavaScript code under compilation")
    private String loggingLevel = Level.WARNING.getName();

    @Option(name = "--externs",
        usage = "The file containing JavaScript externs. You may specify"
        + " multiple")
    private List<String> externs = new ArrayList<>();

    @Option(name = "--js",
        usage = "The JavaScript filename. You may specify multiple. " +
            "The flag name is optional, because args are interpreted as files by default. " +
            "You may also use minimatch-style glob patterns. For example, use " +
            "--js='**.js' --js='!**_test.js' to recursively include all " +
            "js files that do not end in _test.js")
    private List<String> js = new ArrayList<>();

    @Option(name = "--js_output_file",
        usage = "Primary output filename. If not specified, output is " +
        "written to stdout")
    private String jsOutputFile = "";

    @Option(name = "--module",
        hidden = true,
        usage = "A JavaScript module specification. The format is "
        + "<name>:<num-js-files>[:[<dep>,...][:]]]. Module names must be "
        + "unique. Each dep is the name of a module that this module "
        + "depends on. Modules must be listed in dependency order, and JS "
        + "source files must be listed in the corresponding order. Where "
        + "--module flags occur in relation to --js flags is unimportant. "
        + "<num-js-files> may be set to 'auto' for the first module if it "
        + "has no dependencies. "
        + "Provide the value 'auto' to trigger module creation from CommonJS"
        + "modules.")
    private List<String> module = new ArrayList<>();

    @Option(name = "--variable_renaming_report",
        hidden = true,
        usage = "File where the serialized version of the variable "
        + "renaming map produced should be saved")
    private String variableMapOutputFile = "";

    @Option(name = "--create_renaming_reports",
        hidden = true,
        handler = BooleanOptionHandler.class,
        usage = "If true, variable renaming and property renaming report "
        + "files will be produced as {binary name}_vars_renaming_report.out "
        + "and {binary name}_props_renaming_report.out. Note that this flag "
        + "cannot be used in conjunction with either variable_renaming_report "
        + "or property_renaming_report")
    private boolean createNameMapFiles = false;

    @Option(name = "--property_renaming_report",
        hidden = true,
        usage = "File where the serialized version of the property "
        + "renaming map produced should be saved")
    private String propertyMapOutputFile = "";

    @Option(name = "--third_party",
        hidden = true,
        handler = BooleanOptionHandler.class,
        usage = "Check source validity but do not enforce Closure style "
        + "rules and conventions")
    private boolean thirdParty = false;

    @Option(name = "--summary_detail_level",
        hidden = true,
        usage = "Controls how detailed the compilation summary is. Values:"
        + " 0 (never print summary), 1 (print summary only if there are "
        + "errors or warnings), 2 (print summary if the 'checkTypes' "
        + "diagnostic  group is enabled, see --jscomp_warning), "
        + "3 (always print summary). The default level is 1")
    private int summaryDetailLevel = 1;

    @Option(name = "--output_wrapper",
        hidden = true,
        usage = "Interpolate output into this string at the place denoted"
        + " by the marker token %output%. Use marker token %output|jsstring%"
        + " to do js string escaping on the output.")
    private String outputWrapper = "";

    @Option(name = "--output_wrapper_file",
        hidden = true,
        usage = "Loads the specified file and passes the file contents to the "
        + "--output_wrapper flag, replacing the value if it exists.")
    private String outputWrapperFile = "";

    @Option(name = "--module_wrapper",
        hidden = true,
        usage = "An output wrapper for a JavaScript module (optional). "
        + "The format is <name>:<wrapper>. The module name must correspond "
        + "with a module specified using --module. The wrapper must "
        + "contain %s as the code placeholder. The %basename% placeholder can "
        + "also be used to substitute the base name of the module output file.")
    private List<String> moduleWrapper = new ArrayList<>();

    @Option(name = "--module_output_path_prefix",
        hidden = true,
        usage = "Prefix for filenames of compiled JS modules. "
        + "<module-name>.js will be appended to this prefix. Directories "
        + "will be created as needed. Use with --module")
    private String moduleOutputPathPrefix = "./";

    @Option(name = "--create_source_map",
        hidden = true,
        usage = "If specified, a source map file mapping the generated " +
        "source files back to the original source file will be " +
        "output to the specified path. The %outname% placeholder will " +
        "expand to the name of the output file that the source map " +
        "corresponds to.")
    private String createSourceMap = "";

    @Option(name = "--source_map_format",
        hidden = true,
        usage = "The source map format to produce. " +
        "Options are V3 and DEFAULT, which are equivalent.")
    private SourceMap.Format sourceMapFormat = SourceMap.Format.DEFAULT;

    @Option(name = "--source_map_location_mapping",
        hidden = true,
        usage = "Source map location mapping separated by a '|' " +
        "(i.e. filesystem-path|webserver-path)")
    private List<String> sourceMapLocationMapping = Lists.newArrayList();

    // Used to define the flag, values are stored by the handler.
    @SuppressWarnings("unused")
    @Option(name = "--jscomp_error",
        hidden = true,
        handler = WarningGuardErrorOptionHandler.class,
        usage = "Make the named class of warnings an error. Options:" +
        DiagnosticGroups.DIAGNOSTIC_GROUP_NAMES)
    private List<String> jscompError = new ArrayList<>();

    // Used to define the flag, values are stored by the handler.
    @SuppressWarnings("unused")
    @Option(name = "--jscomp_warning",
        hidden = true,
        handler = WarningGuardWarningOptionHandler.class,
        usage = "Make the named class of warnings a normal warning. " +
        "Options:" + DiagnosticGroups.DIAGNOSTIC_GROUP_NAMES)
    private List<String> jscompWarning = new ArrayList<>();

    // Used to define the flag, values are stored by the handler.
    @SuppressWarnings("unused")
    @Option(name = "--jscomp_off",
        hidden = true,
        handler = WarningGuardOffOptionHandler.class,
        usage = "Turn off the named class of warnings. Options:" +
        DiagnosticGroups.DIAGNOSTIC_GROUP_NAMES)
    private List<String> jscompOff = new ArrayList<>();

    @Option(name = "--define",
        hidden = true,
        aliases = {"--D", "-D"},
        usage = "Override the value of a variable annotated @define. " +
        "The format is <name>[=<val>], where <name> is the name of a @define " +
        "variable and <val> is a boolean, number, or a single-quoted string " +
        "that contains no single quotes. If [=<val>] is omitted, " +
        "the variable is marked true")
    private List<String> define = new ArrayList<>();

    @Option(name = "--charset",
        hidden = true,
        usage = "Input and output charset for all files. By default, we " +
                "accept UTF-8 as input and output US_ASCII")
    private String charset = "";

    @Option(name = "--compilation_level",
        aliases = {"-O"},
        usage = "Specifies the compilation level to use. Options: " +
            "WHITESPACE_ONLY, " +
            "SIMPLE, " +
            "ADVANCED")
    private String compilationLevel = "SIMPLE";
    private CompilationLevel compilationLevelParsed = null;

    @Option(name = "--use_types_for_optimization",
        hidden = true,
        usage = "Experimental: perform additional optimizations " +
        "based on available information. Inaccurate type annotations " +
        "may result in incorrect results.")
    private boolean useTypesForOptimization = false;

    @Option(name = "--warning_level",
        aliases = {"-W"},
        usage = "Specifies the warning level to use. Options: " +
        "QUIET, DEFAULT, VERBOSE")
    private WarningLevel warningLevel = WarningLevel.DEFAULT;

    @Option(name = "--use_only_custom_externs",
        hidden = true,
        handler = BooleanOptionHandler.class,
        usage = "Specifies whether the default externs should be excluded")
    private boolean useOnlyCustomExterns = false;

    @Option(name = "--debug",
        hidden = true,
        handler = BooleanOptionHandler.class,
        usage = "Enable debugging options")
    private boolean debug = false;

    @Option(name = "--generate_exports",
        hidden = true,
        handler = BooleanOptionHandler.class,
        usage = "Generates export code for those marked with @export")
    private boolean generateExports = false;

    @Option(name = "--formatting",
        hidden = true,
        usage = "Specifies which formatting options, if any, should be "
        + "applied to the output JS. Options: "
        + "PRETTY_PRINT, PRINT_INPUT_DELIMITER, SINGLE_QUOTES")
    private List<FormattingOption> formatting = new ArrayList<>();

    @Option(name = "--process_common_js_modules",
        hidden = true,
        usage = "Process CommonJS modules to a concatenable form.")
    private boolean processCommonJsModules = false;

    @Option(name = "--rewrite_es6_modules",
        hidden = true,
        usage = "Rewrite ES6 modules to a concatenable form.")
    private boolean rewriteEs6Modules = false;

    @Option(name = "--transpile_only",
        hidden = true,
        usage = "Run ES6 to ES3 transpilation only, skip other passes.")
    private boolean transpileOnly = false;

    @Option(name = "--common_js_module_path_prefix",
        hidden = true,
        usage = "Path prefix to be removed from CommonJS module names.")
    private String commonJsPathPrefix =
        ProcessCommonJSModules.DEFAULT_FILENAME_PREFIX;

    @Option(name = "--common_js_entry_module",
        hidden = true,
        usage = "Root of your common JS dependency hierarchy. " +
            "Your main script.")
    private String commonJsEntryModule;

    @Option(name = "--transform_amd_modules",
        hidden = true,
        usage = "Transform AMD to CommonJS modules.")
    private boolean transformAmdModules = false;

    @Option(name = "--process_closure_primitives",
        hidden = true,
        handler = BooleanOptionHandler.class,
        usage = "Processes built-ins from the Closure library, such as "
        + "goog.require(), goog.provide(), and goog.exportSymbol(). "
        + "True by default.")
    private boolean processClosurePrimitives = true;

    @Option(name = "--manage_closure_dependencies",
        hidden = true,
        handler = BooleanOptionHandler.class,
        usage = "Automatically sort dependencies so that a file that "
        + "goog.provides symbol X will always come before a file that "
        + "goog.requires symbol X. If an input provides symbols, and "
        + "those symbols are never required, then that input will not "
        + "be included in the compilation.")
    private boolean manageClosureDependencies = false;

    @Option(name = "--only_closure_dependencies",
        hidden = true,
        handler = BooleanOptionHandler.class,
        usage = "Only include files in the transitive dependency of the "
        + "entry points (specified by closure_entry_point). Files that do "
        + "not provide dependencies will be removed. This supersedes "
        + "manage_closure_dependencies")
    private boolean onlyClosureDependencies = false;

    @Option(name = "--closure_entry_point",
        hidden = true,
        usage = "Entry points to the program. Must be goog.provide'd "
        + "symbols. Any goog.provide'd symbols that are not a transitive "
        + "dependency of the entry points will be removed. Files without "
        + "goog.provides, and their dependencies, will always be left in. "
        + "If any entry points are specified, then the "
        + "manage_closure_dependencies option will be set to true and "
        + "all files will be sorted in dependency order.")
    private List<String> closureEntryPoint = new ArrayList<>();

    @Option(name = "--process_jquery_primitives",
        hidden = true,
        handler = BooleanOptionHandler.class,
        usage = "Processes built-ins from the Jquery library, such as "
        + "jQuery.fn and jQuery.extend()")
    private boolean processJqueryPrimitives = false;

    @Option(name = "--angular_pass",
        hidden = true,
        handler = BooleanOptionHandler.class,
        usage = "Generate $inject properties for AngularJS for functions "
        + "annotated with @ngInject")
    private boolean angularPass = false;

    @Option(name = "--output_manifest",
        hidden = true,
        usage = "Prints out a list of all the files in the compilation. "
        + "If --manage_closure_dependencies is on, this will not include "
        + "files that got dropped because they were not required. "
        + "The %outname% placeholder expands to the JS output file. "
        + "If you're using modularization, using %outname% will create "
        + "a manifest for each module.")
    private String outputManifest = "";

    @Option(name = "--output_module_dependencies",
        hidden = true,
        usage = "Prints out a JSON file of dependencies between modules.")
    private String outputModuleDependencies = "";

    @Option(name = "--accept_const_keyword",
        hidden = true,
        usage = "Allows usage of const keyword.")
    private boolean acceptConstKeyword = false;

    // TODO(tbreisacher): Remove the "(experimental)" for ES6 when it's stable enough.
    @Option(name = "--language_in",
        hidden = true,
        usage = "Sets what language spec that input sources conform. "
        + "Options: ECMASCRIPT3 (default), ECMASCRIPT5, ECMASCRIPT5_STRICT, "
        + "ECMASCRIPT6 (experimental), ECMASCRIPT6_STRICT (experimental), "
        + "ECMASCRIPT6_TYPED (experimental)")
    private String languageIn = "ECMASCRIPT3";

    @Option(name = "--language_out",
        hidden = true,
        usage = "Sets what language spec the output should conform to. "
        + " If omitted, defaults to the value of language_in. "
        + "Options: ECMASCRIPT3, ECMASCRIPT5, ECMASCRIPT5_STRICT"
        + "ECMASCRIPT6_TYPED (experimental)")
    private String languageOut = "";

    @Option(name = "--allow_es6_out",
        hidden = true,
        usage = "Experimental: Allows ES6 language_out, for compiling "
        + "ES6 to ES6 as well as transpiling to ES6 from lower versions. "
        + "Enabling this flag may cause the compiler to crash.")
    private boolean allowEs6Out = false;

    @Option(name = "--version",
        hidden = true,
        handler = BooleanOptionHandler.class,
        usage = "Prints the compiler version to stdout and exit.")
    private boolean version = false;

    @Option(name = "--translations_file",
        hidden = true,
        usage = "Source of translated messages. Currently only supports XTB.")
    private String translationsFile = "";

    @Option(name = "--translations_project",
        hidden = true,
        usage = "Scopes all translations to the specified project." +
        "When specified, we will use different message ids so that messages " +
        "in different projects can have different translations.")
    private String translationsProject = null;

    @Option(name = "--flagfile",
        hidden = true,
        usage = "A file containing additional command-line options.")
    private String flagFile = "";

    @Option(name = "--warnings_whitelist_file",
        hidden = true,
        usage = "A file containing warnings to suppress. Each line should be " +
            "of the form\n" +
            "<file-name>:<line-number>?  <warning-description>")
    private String warningsWhitelistFile = "";

    @Option(name = "--extra_annotation_name",
        hidden = true,
        usage = "A whitelist of tag names in JSDoc. You may specify multiple")
    private List<String> extraAnnotationName = new ArrayList<>();

    @Option(name = "--tracer_mode",
        hidden = true,
        usage = "Shows the duration of each compiler pass and the impact to " +
        "the compiled output size. Options: ALL, RAW_SIZE, TIMING_ONLY, OFF")
    private CompilerOptions.TracerMode tracerMode =
        CompilerOptions.TracerMode.OFF;

    @Option(name = "--new_type_inf",
        hidden = true,
        usage = "In development new type inference pass. DO NOT USE!")
    private boolean useNewTypeInference = false;

    @Option(name = "--rename_prefix_namespace",
        usage = "Specifies the name of an object that will be used to store all "
        + "non-extern globals")
    private String renamePrefixNamespace = null;

    @Option(name = "--conformance_configs",
        hidden = true,
        usage = "A list of JS Conformance configurations in text protocol buffer format.")
    private List<String> conformanceConfigs = new ArrayList<>();

    @Argument
    private List<String> arguments = new ArrayList<>();

    private static final Map<String, CompilationLevel> COMPILATION_LEVEL_MAP =
        ImmutableMap.of(
            "WHITESPACE_ONLY",
            CompilationLevel.WHITESPACE_ONLY,
            "SIMPLE",
            CompilationLevel.SIMPLE_OPTIMIZATIONS,
            "SIMPLE_OPTIMIZATIONS",
            CompilationLevel.SIMPLE_OPTIMIZATIONS,
            "ADVANCED",
            CompilationLevel.ADVANCED_OPTIMIZATIONS,
            "ADVANCED_OPTIMIZATIONS",
            CompilationLevel.ADVANCED_OPTIMIZATIONS);

    /**
     * Parse the given args list.
     */
    private void parse(List<String> args) throws CmdLineException {
      CmdLineParser parser = new CmdLineParser(this);
      parser.parseArgument(args.toArray(new String[] {}));

      compilationLevelParsed =
          COMPILATION_LEVEL_MAP.get(compilationLevel.toUpperCase());
      if (compilationLevelParsed == null) {
        throw new CmdLineException(
            parser, "Bad value for --compilation_level: " + compilationLevel);
      }

    }

    private void printUsage(PrintStream ps) {
      CmdLineParser p = new CmdLineParser(this);
      p.printUsage(new OutputStreamWriter(ps, UTF_8), null, OptionHandlerFilter.ALL);
      ps.flush();
    }

    private void printShortUsageAfterErrors(PrintStream ps) {
      ps.print("Sample usage: ");
      ps.println((new CmdLineParser(this)).printExample(OptionHandlerFilter.PUBLIC, null));
      ps.println("Run with --help for all options and details");
      ps.flush();
    }

    /**
     * Users may specify JS inputs via the {@code --js} flag, as well
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
    protected List<String> getJsFiles() throws CmdLineException, IOException {
      List<String> patterns = new ArrayList<>();
      patterns.addAll(js);
      patterns.addAll(arguments);
      List<String> allJsInputs = findJsFiles(patterns);
      if (!patterns.isEmpty() && allJsInputs.isEmpty()) {
        throw new CmdLineException(new CmdLineParser(this), "No inputs matched");
      }
      return allJsInputs;
    }

    @SuppressWarnings("deprecation")
    List<SourceMap.LocationMapping> getSourceMapLocationMappings() throws CmdLineException {
      ImmutableList.Builder<LocationMapping> locationMappings = ImmutableList.builder();

      Splitter splitter = Splitter.on('|').limit(2);
      for (String locationMapping : sourceMapLocationMapping) {
        List<String> parts = splitter.splitToList(locationMapping);
        if (parts.size() != 2) {
          throw new CmdLineException(
            "Bad value for --source_map_location_mapping: " +
            ImmutableList.of(sourceMapLocationMapping));
        }
        locationMappings.add(new SourceMap.LocationMapping(parts.get(0), parts.get(1)));
      }

      return locationMappings.build();
    }

    // Our own option parser to be backwards-compatible.
    // It needs to be public because of the crazy reflection that args4j does.
    public static class BooleanOptionHandler extends OptionHandler<Boolean> {
      private static final Set<String> TRUES =
          ImmutableSet.of("true", "on", "yes", "1");
      private static final Set<String> FALSES =
          ImmutableSet.of("false", "off", "no", "0");

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
        } catch (CmdLineException e) {
          param = null; // to stop linter complaints
        }

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
        guardLevels.add(new GuardLevel(value, level));
      }

      @Override public FieldSetter asFieldSetter() {
        return proxy.asFieldSetter();
      }

      @Override public AnnotatedElement asAnnotatedElement() {
        return proxy.asAnnotatedElement();
      }
    }

    public static WarningGuardSpec getWarningGuardSpec() {
      WarningGuardSpec spec = new WarningGuardSpec();
      for (GuardLevel guardLevel : guardLevels) {
        spec.add(guardLevel.level, guardLevel.name);
      }
      return spec;
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

  private boolean errors = false;

  private boolean runCompiler = false;

  /**
   * Cached error stream to avoid passing it as a parameter to helper
   * functions.
   */
  private PrintStream errorStream;

  /**
   * Create a new command-line runner. You should only need to call
   * the constructor if you're extending this class. Otherwise, the main
   * method should instantiate it.
   */
  protected CommandLineRunner(String[] args) {
    super();
    initConfigFromFlags(args, System.out, System.err);
  }

  protected CommandLineRunner(String[] args, PrintStream out, PrintStream err) {
    super(out, err);
    initConfigFromFlags(args, out, err);
  }

  private static List<String> processArgs(String[] args) {
    // Args4j has a different format that the old command-line parser.
    // So we use some voodoo to get the args into the format that args4j
    // expects.
    Pattern argPattern = Pattern.compile("(--?[a-zA-Z_]+)=(.*)");
    Pattern quotesPattern = Pattern.compile("^['\"](.*)['\"]$");
    List<String> processedArgs = new ArrayList<>();

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

  private void reportError(String message) {
    errors = true;
    errorStream.println(message);
    errorStream.flush();
  }

  private void processFlagFile()
            throws CmdLineException, IOException {
    Path flagFile = Paths.get(flags.flagFile);

    BufferedReader buffer =
      java.nio.file.Files.newBufferedReader(flagFile, UTF_8);
    // Builds the tokens.
    StringBuilder builder = new StringBuilder();
    // Stores the built tokens.
    List<String> tokens = new ArrayList<>();
    // Indicates if we are in a "quoted" token.
    boolean quoted = false;
    // Indicates if the char being processed has been escaped.
    boolean escaped = false;
    // Indicates whether this is the beginning of the file.
    boolean isFirstCharacter = true;

    int c;

    while ((c = buffer.read()) != -1) {

      // Ignoring the BOM.
      if (isFirstCharacter) {
        isFirstCharacter = false;
        if (c == UTF8_BOM_CODE) {
          continue;
        }
      }

      if (c == 32 || c == 9 || c == 10 || c == 13) {
        if (quoted) {
          builder.append((char) c);
        } else if (builder.length() != 0) {
          tokens.add(builder.toString());
          builder.setLength(0);
        }
      } else if (c == 34) {
        if (escaped) {
          if (quoted) {
            builder.setCharAt(builder.length()-1, (char) c);
          } else {
            builder.append((char) c);
          }
        } else {
          quoted = !quoted;
        }
      } else {
        builder.append((char) c);
      }

      escaped = c == 92;
    }

    buffer.close();

    if (builder.length() != 0) {
      tokens.add(builder.toString());
    }

    flags.flagFile = "";

    tokens = processArgs(tokens.toArray(new String[tokens.size()]));

    // Command-line warning levels should override flag file settings,
    // which means they should go last.
    List<GuardLevel> previous = new ArrayList<>(Flags.guardLevels);
    Flags.guardLevels.clear();
    flags.parse(tokens);
    Flags.guardLevels.addAll(previous);

    // Currently we are not supporting this (prevent direct/indirect loops)
    if (!flags.flagFile.isEmpty()) {
      reportError("ERROR - Arguments in the file cannot contain "
          + "--flagfile option.");
    }
  }

  private void initConfigFromFlags(String[] args, PrintStream out, PrintStream err) {

    errorStream = err;
    List<String> processedArgs = processArgs(args);

    Flags.guardLevels.clear();

    List<String> jsFiles = null;
    List<LocationMapping> mappings = null;
    try {
      flags.parse(processedArgs);

      // For contains --flagfile flag
      if (!flags.flagFile.isEmpty()) {
        processFlagFile();
      }

      jsFiles = flags.getJsFiles();
      mappings = flags.getSourceMapLocationMappings();
    } catch (CmdLineException e) {
      reportError(e.getMessage());
    } catch (IOException ioErr) {
      reportError("ERROR - " + flags.flagFile + " read error.");
    }

    if (flags.processCommonJsModules) {
      flags.processClosurePrimitives = true;
      flags.manageClosureDependencies = true;
      if (flags.commonJsEntryModule == null) {
        reportError("Please specify --common_js_entry_module.");
      }
      flags.closureEntryPoint = Lists.newArrayList(
          ProcessCommonJSModules.toModuleName(flags.commonJsEntryModule));
    }

    if (flags.outputWrapperFile != null && !flags.outputWrapperFile.isEmpty()) {
      flags.outputWrapper = "";
      try {
        flags.outputWrapper = Files.toString(
            new File(flags.outputWrapperFile), UTF_8);
      } catch (Exception e) {
        reportError("ERROR - invalid output_wrapper_file specified.");
      }
    }

    if (flags.outputWrapper != null && !flags.outputWrapper.isEmpty() &&
        !flags.outputWrapper.contains(CommandLineRunner.OUTPUT_MARKER)) {
      reportError("ERROR - invalid output_wrapper specified. Missing '" +
          CommandLineRunner.OUTPUT_MARKER + "'.");
    }

    if (errors) {
      flags.printShortUsageAfterErrors(errorStream);
    } else if (flags.displayHelp) {
      flags.printUsage(out);
    } else if (flags.version) {
      out.println(
          "Closure Compiler (http://github.com/google/closure-compiler)\n" +
          "Version: " + Compiler.getReleaseVersion() + "\n" +
          "Built on: " + Compiler.getReleaseDate());
      out.flush();
    } else {
      runCompiler = true;

      CodingConvention conv;
      if (flags.thirdParty) {
        conv = CodingConventions.getDefault();
      } else if (flags.processJqueryPrimitives) {
        conv = new JqueryCodingConvention();
      } else {
        conv = new ClosureCodingConvention();
      }

      getCommandLineConfig()
          .setPrintTree(flags.printTree)
          .setPrintAst(flags.printAst)
          .setPrintPassGraph(flags.printPassGraph)
          .setJscompDevMode(flags.jscompDevMode)
          .setLoggingLevel(flags.loggingLevel)
          .setExterns(flags.externs)
          .setJs(jsFiles)
          .setJsOutputFile(flags.jsOutputFile)
          .setModule(flags.module)
          .setVariableMapOutputFile(flags.variableMapOutputFile)
          .setCreateNameMapFiles(flags.createNameMapFiles)
          .setPropertyMapOutputFile(flags.propertyMapOutputFile)
          .setCodingConvention(conv)
          .setSummaryDetailLevel(flags.summaryDetailLevel)
          .setOutputWrapper(flags.outputWrapper)
          .setModuleWrapper(flags.moduleWrapper)
          .setModuleOutputPathPrefix(flags.moduleOutputPathPrefix)
          .setCreateSourceMap(flags.createSourceMap)
          .setSourceMapFormat(flags.sourceMapFormat)
          .setSourceMapLocationMappings(mappings)
          .setWarningGuardSpec(Flags.getWarningGuardSpec())
          .setDefine(flags.define)
          .setCharset(flags.charset)
          .setManageClosureDependencies(flags.manageClosureDependencies)
          .setOnlyClosureDependencies(flags.onlyClosureDependencies)
          .setClosureEntryPoints(flags.closureEntryPoint)
          .setOutputManifest(ImmutableList.of(flags.outputManifest))
          .setOutputModuleDependencies(flags.outputModuleDependencies)
          .setAcceptConstKeyword(flags.acceptConstKeyword)
          .setLanguageIn(flags.languageIn)
          .setLanguageOut(flags.languageOut)
          .setProcessCommonJSModules(flags.processCommonJsModules)
          .setRewriteEs6Modules(flags.rewriteEs6Modules)
          .setTranspileOnly(flags.transpileOnly)
          .setCommonJSModulePathPrefix(flags.commonJsPathPrefix)
          .setTransformAMDToCJSModules(flags.transformAmdModules)
          .setWarningsWhitelistFile(flags.warningsWhitelistFile)
          .setAngularPass(flags.angularPass)
          .setTracerMode(flags.tracerMode)
          .setNewTypeInference(flags.useNewTypeInference);
    }
    errorStream = null;
  }

  @Override
  protected void addWhitelistWarningsGuard(
      CompilerOptions options, File whitelistFile) {
    options.addWarningsGuard(WhitelistWarningsGuard.fromFile(whitelistFile));
  }

  @Override
  protected CompilerOptions createOptions() {
    CompilerOptions options = new CompilerOptions();
    if (flags.processJqueryPrimitives) {
      options.setCodingConvention(new JqueryCodingConvention());
    } else {
      options.setCodingConvention(new ClosureCodingConvention());
    }

    options.setAllowEs6Out(flags.allowEs6Out);
    options.setExtraAnnotationNames(flags.extraAnnotationName);

    CompilationLevel level = flags.compilationLevelParsed;
    level.setOptionsForCompilationLevel(options);

    if (flags.debug) {
      level.setDebugOptionsForCompilationLevel(options);
    }

    if (flags.useTypesForOptimization) {
      level.setTypeBasedOptimizationOptions(options);
    }

    if (flags.generateExports) {
      options.setGenerateExports(flags.generateExports);
    }

    WarningLevel wLevel = flags.warningLevel;
    wLevel.setOptionsForWarningLevel(options);
    for (FormattingOption formattingOption : flags.formatting) {
      formattingOption.applyToOptions(options);
    }

    options.closurePass = flags.processClosurePrimitives;

    options.jqueryPass = CompilationLevel.ADVANCED_OPTIMIZATIONS == level &&
        flags.processJqueryPrimitives;

    options.angularPass = flags.angularPass;

    options.renamePrefixNamespace = flags.renamePrefixNamespace;

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
      // so we might as well inline it. But shut off the i18n warnings,
      // because the user didn't really ask for i18n.
      options.messageBundle = new EmptyMessageBundle();
      options.setWarningLevel(JsMessageVisitor.MSG_CONVENTIONS, CheckLevel.OFF);
    }

    options.setConformanceConfigs(loadConformanceConfigs(flags.conformanceConfigs));

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
    if (flags.useOnlyCustomExterns || isInTestMode()) {
      return externs;
    } else {
      List<SourceFile> defaultExterns = getDefaultExterns();
      defaultExterns.addAll(externs);
      return defaultExterns;
    }
  }

  private ImmutableList<ConformanceConfig> loadConformanceConfigs(List<String> configPaths) {
    ImmutableList.Builder<ConformanceConfig> configs =
        ImmutableList.builder();

    for (String configPath : configPaths) {
      try {
        configs.add(loadConformanceConfig(configPath));
      } catch (IOException e) {
        throw new RuntimeException("Error loading conformance config", e);
      }
    }

    return configs.build();
  }

  private static ConformanceConfig loadConformanceConfig(String configFile)
      throws IOException {
    String textProto = Files.toString(new File(configFile), UTF_8);

    ConformanceConfig.Builder builder = ConformanceConfig.newBuilder();

    // Looking for BOM.
    if ((int)textProto.charAt(0) == UTF8_BOM_CODE) {
      // Stripping the BOM.
      textProto = textProto.substring(1);
    }

    try {
      TextFormat.merge(textProto, builder);
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
    return builder.build();
  }

  // The externs expected in externs.zip, in sorted order.
  private static final List<String> DEFAULT_EXTERNS_NAMES = ImmutableList.of(
    // JS externs
    "es3.js",
    "es5.js",
    "es6.js",
    "es6_collections.js",
    "intl.js",

    // Event APIs
    "w3c_event.js",
    "w3c_event3.js",
    "gecko_event.js",
    "ie_event.js",
    "webkit_event.js",
    "w3c_device_sensor_event.js",

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

    "chrome.js",

    "deprecated.js",
    "fileapi.js",
    "flash.js",
    "gecko_xml.js",
    "html5.js",
    "ie_vml.js",
    "iphone.js",
    "mediasource.js",
    "v8.js",
    "webstorage.js",
    "w3c_anim_timing.js",
    "w3c_encoding.js",
    "w3c_css3d.js",
    "w3c_elementtraversal.js",
    "w3c_geolocation.js",
    "w3c_indexeddb.js",
    "w3c_navigation_timing.js",
    "w3c_range.js",
    "w3c_rtc.js",
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
    Map<String, SourceFile> externsMap = new HashMap<>();
    for (ZipEntry entry = null; (entry = zip.getNextEntry()) != null; ) {
      BufferedInputStream entryStream = new BufferedInputStream(
          ByteStreams.limit(zip, entry.getSize()));
      externsMap.put(entry.getName(),
          SourceFile.fromInputStream(
              // Give the files an odd prefix, so that they do not conflict
              // with the user's files.
              "externs.zip//" + entry.getName(),
              entryStream,
              UTF_8));
    }

    Preconditions.checkState(
        externsMap.keySet().equals(new HashSet<>(DEFAULT_EXTERNS_NAMES)),
        "Externs zip must match our hard-coded list of externs.");

    // Order matters, so the resources must be added to the result list
    // in the expected order.
    List<SourceFile> externs = new ArrayList<>();
    for (String key : DEFAULT_EXTERNS_NAMES) {
      externs.add(externsMap.get(key));
    }

    return externs;
  }

  /**
   * Returns all the JavaScript files from the set of patterns. The patterns support
   * globs, such as '*.js' for all JS files in a directory and '**.js' for all JS files
   * within the directory and sub-directories.
   */
  public static List<String> findJsFiles(Collection<String> patterns) throws IOException {
    Set<String> allJsInputs = new LinkedHashSet<>();
    for (String pattern : patterns) {
      if (!pattern.contains("*") && !pattern.startsWith("!")) {
        File matchedFile = new File(pattern);
        if (matchedFile.isDirectory()) {
          matchPaths(new File(matchedFile, "**.js").toString(), allJsInputs);
        } else {
          allJsInputs.add(pattern);
        }
      } else {
        matchPaths(pattern, allJsInputs);
      }
    }

    return new ArrayList<>(allJsInputs);
  }

  private static void matchPaths(String pattern, final Set<String> allJsInputs)
      throws IOException {
    FileSystem fs = FileSystems.getDefault();
    final boolean remove = pattern.indexOf('!') == 0;
    if (remove) pattern = pattern.substring(1);

    if (File.separator.equals("\\")) {
      pattern = pattern.replace('\\', '/');
    }

    // Split the pattern into two pieces: the globbing part
    // and the non-globbing prefix.
    List<String> patternParts = Splitter.on('/').splitToList(pattern);
    String prefix = ".";
    for (int i = 0; i < patternParts.size(); i++) {
      if (patternParts.get(i).contains("*")) {
        if (i == 0) {
          break;
        } else {
          prefix = Joiner.on("/").join(patternParts.subList(0, i));
          pattern = Joiner.on("/").join(patternParts.subList(i, patternParts.size()));
        }
      }
    }

    final PathMatcher matcher = fs.getPathMatcher("glob:" + pattern);
    java.nio.file.Files.walkFileTree(
        fs.getPath(prefix), new SimpleFileVisitor<Path>() {
          @Override public FileVisitResult visitFile(
              Path p, BasicFileAttributes attrs) {
            if (matcher.matches(p)) {
              if (remove) {
                allJsInputs.remove(p.toString());
              } else {
                allJsInputs.add(p.toString());
              }
            }
            return FileVisitResult.CONTINUE;
          }
        });
  }

  /**
   * @return Whether the configuration is valid and specifies to run the
   *         compiler.
   */
  public boolean shouldRunCompiler() {
    return this.runCompiler;
  }

  /**
   * @return Whether the configuration has errors.
   */
  public boolean hasErrors() {
    return this.errors;
  }

  /**
   * Runs the Compiler. Exits cleanly in the event of an error.
   */
  public static void main(String[] args) {
    CommandLineRunner runner = new CommandLineRunner(args);
    if (runner.shouldRunCompiler()) {
      runner.run();
    }
    if (runner.hasErrors()) {
      System.exit(-1);
    }
  }
}
