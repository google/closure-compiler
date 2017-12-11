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

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.io.Files;
import com.google.javascript.jscomp.AbstractCommandLineRunner.CommandLineConfig.ErrorFormatOption;
import com.google.javascript.jscomp.CompilerOptions.IsolationMode;
import com.google.javascript.jscomp.SourceMap.LocationMapping;
import com.google.javascript.jscomp.deps.ClosureBundler;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.TokenStream;
import com.google.protobuf.TextFormat;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.NamedOptionDef;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.OptionHandlerFilter;
import org.kohsuke.args4j.spi.FieldSetter;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;
import org.kohsuke.args4j.spi.StringOptionHandler;

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
@GwtIncompatible("Unnecessary")
public class CommandLineRunner extends
    AbstractCommandLineRunner<Compiler, CompilerOptions> {

  public static final String OUTPUT_MARKER =
      AbstractCommandLineRunner.OUTPUT_MARKER;

  // UTF-8 BOM is 0xEF, 0xBB, 0xBF, of which character code is 65279.
  public static final int UTF8_BOM_CODE = 65279;

  // Allowable module name characters that aren't valid in a JS identifier
  private static final Pattern extraModuleNameChars = Pattern.compile("[-.]+");

  // I don't really care about unchecked warnings in this class.
  @SuppressWarnings("unchecked")
  private static class Flags {
    // Some clients run a few copies of the compiler through CommandLineRunner
    // on parallel threads (thankfully, with the same flags),
    // so the access to these lists should be synchronized.
    private static final List<FlagEntry<CheckLevel>> guardLevels =
        Collections.synchronizedList(new ArrayList<FlagEntry<CheckLevel>>());
    private static final List<FlagEntry<JsSourceType>> mixedJsSources =
        Collections.synchronizedList(new ArrayList<FlagEntry<JsSourceType>>());

    @Option(
      name = "--help",
      handler = BooleanOptionHandler.class,
      usage = "Displays this message on stdout and exit"
    )
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

    @Option(
      name = "--emit_use_strict",
      handler = BooleanOptionHandler.class,
      usage = "Start output with \"'use strict';\"."
    )
    private boolean emitUseStrict = true;

    @Option(
        name = "--strict_mode_input",
        handler = BooleanOptionHandler.class,
        usage = "Assume input sources are to run in strict mode.")
    private boolean strictModeInput = true;

    // Turn on (very slow) extra validity checks for use when modifying the compiler.
    @Option(
      name = "--jscomp_dev_mode",
      hidden = true,
      //no usage
      aliases = {"--dev_mode"}
    )
    private CompilerOptions.DevMode jscompDevMode = CompilerOptions.DevMode.OFF;

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
        handler = JsOptionHandler.class,
        usage = "The JavaScript filename. You may specify multiple. "
        + "The flag name is optional, because args are interpreted as files by default. "
        + "You may also use minimatch-style glob patterns. For example, use "
        + "--js='**.js' --js='!**_test.js' to recursively include all "
        + "js files that do not end in _test.js")
    private List<String> js = new ArrayList<>();

    @Option(name = "--jszip",
        hidden = true,
        handler = JsZipOptionHandler.class,
        usage = "The JavaScript zip filename. You may specify multiple.")
    private List<String> jszip = new ArrayList<>();

    @Option(name = "--js_output_file",
        usage = "Primary output filename. If not specified, output is "
        + "written to stdout")
    private String jsOutputFile = "";

    @Option(name = "--module",
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

    @Option(name = "--continue-saved-compilation",
        usage = "Filename where the intermediate compilation state was previously saved.",
        hidden = true)
    private String continueSavedCompilationFile = null;

    @Option(name = "--save-after-checks",
        usage = "Filename to save phase 1 intermediate state so that the compilation can be"
            + " resumed later.",
        hidden = true)
    private String saveAfterChecksFile = null;


    @Option(name = "--variable_renaming_report",
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

    @Option(name = "--source_map_include_content",
        handler = BooleanOptionHandler.class,
        usage = "Includes sources content into source map. Greatly increases "
        + "the size of source maps but offers greater portability")
    private boolean sourceMapIncludeSourcesContent = false;

    @Option(name = "--property_renaming_report",
        usage = "File where the serialized version of the property "
        + "renaming map produced should be saved")
    private String propertyMapOutputFile = "";

    @Option(name = "--third_party",
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

    @Option(name = "--isolation_mode",
        usage = "If set to IIFE the compiler output will follow the form:\n"
        + "  (function(){%output%)).call(this);\n"
        + "Options: NONE, IIFE")
    private IsolationMode isolationMode = IsolationMode.NONE;

    @Option(
      name = "--output_wrapper",
      usage =
          "Interpolate output into this string at the place denoted"
              + " by the marker token %output%. Use marker token %output|jsstring%"
              + " to do js string escaping on the output."
              + " Consider using the --isolation_mode flag instead."
    )
    private String outputWrapper = "";

    @Option(name = "--output_wrapper_file",
        usage = "Loads the specified file and passes the file contents to the --output_wrapper"
        + " flag, replacing the value if it exists. This is useful if you want special characters"
        + " like newline in the wrapper.")
    private String outputWrapperFile = "";

    @Option(name = "--module_wrapper",
        usage = "An output wrapper for a JavaScript module (optional). "
        + "The format is <name>:<wrapper>. The module name must correspond "
        + "with a module specified using --module. The wrapper must "
        + "contain %s as the code placeholder. "
        + "Alternately, %output% can be used in place of %s. "
        + "%n% can be used to represent a newline. "
        + "The %basename% placeholder can "
        + "also be used to substitute the base name of the module output file.")
    private List<String> moduleWrapper = new ArrayList<>();

    @Option(name = "--module_output_path_prefix",
        usage = "Prefix for filenames of compiled JS modules. "
        + "<module-name>.js will be appended to this prefix. Directories "
        + "will be created as needed. Use with --module")
    private String moduleOutputPathPrefix = "./";

    @Option(name = "--create_source_map",
        usage = "If specified, a source map file mapping the generated "
        + "source files back to the original source file will be "
        + "output to the specified path. The %outname% placeholder will "
        + "expand to the name of the output file that the source map "
        + "corresponds to.")
    private String createSourceMap = "";

    @Option(name = "--source_map_format",
        hidden = true,
        usage = "The source map format to produce. "
        + "Options are V3 and DEFAULT, which are equivalent.")
    private SourceMap.Format sourceMapFormat = SourceMap.Format.DEFAULT;

    @Option(name = "--source_map_location_mapping",
        usage = "Source map location mapping separated by a '|' "
        + "(i.e. filesystem-path|webserver-path)")
    private List<String> sourceMapLocationMapping = new ArrayList<>();

    @Option(
      name = "--source_map_input",
      hidden = false,
      usage =
          "Source map locations for input files, separated by a '|', "
              + "(i.e. input-file-path|input-source-map)"
    )
    private List<String> sourceMapInputs = new ArrayList<>();

    @Option(name = "--parse_inline_source_maps",
        handler = BooleanOptionHandler.class,
        hidden = true,
        usage = "Parse inline source maps (//# sourceMappingURL=data:...)")
    private Boolean parseInlineSourceMaps = true;

    @Option(
      name = "--apply_input_source_maps",
      handler = BooleanOptionHandler.class,
      hidden = true,
      usage =
          "Apply input source maps to the output source map, i.e. have the result map back to"
              + "original inputs.  Input sourcemaps can be located in 2 ways:\n 1) by the"
              + "//# sourceMappingURL=<url>. \n 2) using the--source_map_location_mapping flag.\n"
              + "sourceMappingURL=<url> can read both paths and inline Base64 encoded sourcemaps. "
              + "For inline Base64 encoded sourcemaps, see --parse_inline_source_maps."
    )
    private boolean applyInputSourceMaps = true;

    // Used to define the flag, values are stored by the handler.
    @SuppressWarnings("unused")
    @Option(
      name = "--jscomp_error",
      handler = WarningGuardErrorOptionHandler.class,
      usage =
          "Make the named class of warnings an error. Must be one "
              + "of the error group items. '*' adds all supported."
    )
    private List<String> jscompError = new ArrayList<>();

    // Used to define the flag, values are stored by the handler.
    @SuppressWarnings("unused")
    @Option(
      name = "--jscomp_warning",
      handler = WarningGuardWarningOptionHandler.class,
      usage =
          "Make the named class of warnings a normal warning. Must be one "
              + "of the error group items. '*' adds all supported."
    )
    private List<String> jscompWarning = new ArrayList<>();

    // Used to define the flag, values are stored by the handler.
    @SuppressWarnings("unused")
    @Option(
      name = "--jscomp_off",
      handler = WarningGuardOffOptionHandler.class,
      usage =
          "Turn off the named class of warnings. Must be one "
              + "of the error group items. '*' adds all supported."
    )
    private List<String> jscompOff = new ArrayList<>();

    @Option(name = "--define",
        aliases = {"--D", "-D"},
        usage = "Override the value of a variable annotated @define. "
        + "The format is <name>[=<val>], where <name> is the name of a @define "
        + "variable and <val> is a boolean, number, or a single-quoted string "
        + "that contains no single quotes. If [=<val>] is omitted, "
        + "the variable is marked true")
    private List<String> define = new ArrayList<>();

    @Option(name = "--charset",
        usage = "Input and output charset for all files. By default, we "
                + "accept UTF-8 as input and output US_ASCII")
    private String charset = "";

    @Option(
      name = "--compilation_level",
      aliases = {"-O"},
      usage =
          "Specifies the compilation level to use. Options: "
              + "BUNDLE, "
              + "WHITESPACE_ONLY, "
              + "SIMPLE (default), "
              + "ADVANCED"
    )
    private String compilationLevel = "SIMPLE";

    private CompilationLevel compilationLevelParsed = null;

    @Option(name = "--checks_only",
        aliases = {"--checks-only"},
        handler = BooleanOptionHandler.class,
        usage = "Don't generate output. Run checks, but no optimization passes.")
    private boolean checksOnly = false;

    @Option(
      name = "--incremental_check_mode",
      usage = "Generate or check externs-like .i.js files representing individual libraries."
    )
    private CompilerOptions.IncrementalCheckMode incrementalCheckMode =
        CompilerOptions.IncrementalCheckMode.OFF;

    @Option(name = "--continue_after_errors",
        handler = BooleanOptionHandler.class,
        usage = "Continue trying to compile after an error is encountered.")
    private boolean continueAfterErrors = false;

    @Option(name = "--use_types_for_optimization",
        handler = BooleanOptionHandler.class,
        usage = "Enable or disable the optimizations "
        + "based on available type information. Inaccurate type annotations "
        + "may result in incorrect results.")
    private boolean useTypesForOptimization = true;

    @Option(name = "--assume_function_wrapper",
        handler = BooleanOptionHandler.class,
        usage = "Enable additional optimizations based on the assumption that the output will be "
        + "wrapped with a function wrapper.  This flag is used to indicate that \"global\" "
        + "declarations will not actually be global but instead isolated to the compilation unit. "
        + "This enables additional optimizations.")
    private boolean assumeFunctionWrapper = false;

    @Option(name = "--warning_level",
        aliases = {"-W"},
        usage = "Specifies the warning level to use. Options: "
        + "QUIET, DEFAULT, VERBOSE")
    private WarningLevel warningLevel = WarningLevel.DEFAULT;

    @Option(name = "--debug",
        hidden = true,
        handler = BooleanOptionHandler.class,
        usage = "Enable debugging options")
    private boolean debug = false;

    @Option(name = "--generate_exports",
        handler = BooleanOptionHandler.class,
        usage = "Generates export code for those marked with @export")
    private boolean generateExports = false;

    @Option(name = "--export_local_property_definitions",
        handler = BooleanOptionHandler.class,
        usage = "Generates export code for local properties marked with @export")
    private boolean exportLocalPropertyDefinitions = false;

    @Option(name = "--formatting",
        usage = "Specifies which formatting options, if any, should be "
        + "applied to the output JS. Options: "
        + "PRETTY_PRINT, PRINT_INPUT_DELIMITER, SINGLE_QUOTES")
    private List<FormattingOption> formatting = new ArrayList<>();

    @Option(name = "--process_common_js_modules",
        handler = BooleanOptionHandler.class,
        usage = "Process CommonJS modules to a concatenable form.")
    private boolean processCommonJsModules = false;

    @Option(
      name = "--common_js_module_path_prefix",
      hidden = true,
      usage = "Deprecated: use --js_module_root."
    )
    private List<String> commonJsPathPrefix = new ArrayList<>();

    @Option(
      name = "--js_module_root",
      usage = "Path prefixes to be removed from ES6 & CommonJS modules."
    )
    private List<String> moduleRoot = new ArrayList<>();

    @Option(
      name = "--common_js_entry_module",
      hidden = true,
      usage = "Deprecated: use --entry_point."
    )
    private String commonJsEntryModule;

    @Option(name = "--transform_amd_modules",
        hidden = true,
        handler = BooleanOptionHandler.class,
        usage = "Deprecated: Transform AMD to CommonJS modules.")
    @Deprecated
    private boolean transformAmdModules = false;

    @Option(name = "--process_closure_primitives",
        handler = BooleanOptionHandler.class,
        usage = "Processes built-ins from the Closure library, such as "
        + "goog.require(), goog.provide(), and goog.exportSymbol(). "
        + "True by default.")
    private boolean processClosurePrimitives = true;

    @Option(
      name = "--manage_closure_dependencies",
      hidden = true,
      handler = BooleanOptionHandler.class,
      usage = "Deprecated: use --dependency_mode=LOOSE."
    )
    private boolean manageClosureDependencies = false;

    @Option(
      name = "--only_closure_dependencies",
      hidden = true,
      handler = BooleanOptionHandler.class,
      usage = "Deprecated: use --dependency_mode=STRICT."
    )
    private boolean onlyClosureDependencies = false;

    @Option(
      name = "--closure_entry_point",
      hidden = true,
      usage = "Deprecated: use --entry_point.")
    private List<String> closureEntryPoint = new ArrayList<>();

    @Option(name = "--angular_pass",
        handler = BooleanOptionHandler.class,
        usage = "Generate $inject properties for AngularJS for functions "
        + "annotated with @ngInject")
    private boolean angularPass = false;

    @Option(name = "--polymer_pass",
        handler = BooleanOptionHandler.class,
        usage = "Equivalent to --polymer_version=1")
    @Deprecated
    private boolean polymerPass = false;

    @Option(name = "--polymer_version",
        usage = "Which version of Polymer is being used (1 or 2).")
    private Integer polymerVersion = null;

    @Option(name = "--chrome_pass",
        handler = BooleanOptionHandler.class,
        usage = "Enable Chrome-specific options for handling cr.* functions.",
        hidden = true)
    private boolean chromePass = false;

    @Option(name = "--dart_pass",
        handler = BooleanOptionHandler.class,
        usage = "Rewrite Dart Dev Compiler output to be compiler-friendly.")
    private boolean dartPass = false;

    @Option(
      name = "--j2cl_pass",
      hidden = true,
      usage =
          "Rewrite J2CL output to be compiler-friendly if enabled (ON or AUTO). "
              + "Options:OFF, ON, AUTO(default)"
    )
    private String j2clPassMode = "AUTO";

    @Option(
      name = "--output_manifest",
      usage =
          "Prints out a list of all the files in the compilation. "
              + "If --dependency_mode=STRICT or LOOSE is specified, this will not include "
              + "files that got dropped because they were not required. "
              + "The %outname% placeholder expands to the JS output file. "
              + "If you're using modularization, using %outname% will create "
              + "a manifest for each module."
    )
    private String outputManifest = "";

    @Option(name = "--output_module_dependencies",
        usage = "Prints out a JSON file of dependencies between modules.")
    private String outputModuleDependencies = "";

    @Option(
      name = "--language_in",
      usage =
          "Sets the language spec to which input sources should conform. "
              + "Options: ECMASCRIPT3, ECMASCRIPT5, ECMASCRIPT5_STRICT, "
              + "ECMASCRIPT6_TYPED (experimental), ECMASCRIPT_2015, ECMASCRIPT_2016, "
              + "ECMASCRIPT_2017, ECMASCRIPT_NEXT"
    )
    private String languageIn = "ECMASCRIPT_2017";

    @Option(
      name = "--language_out",
      usage =
          "Sets the language spec to which output should conform. "
              + "Options: ECMASCRIPT3, ECMASCRIPT5, ECMASCRIPT5_STRICT, "
              + "ECMASCRIPT6_TYPED (experimental), ECMASCRIPT_2015, ECMASCRIPT_2016, "
              + "ECMASCRIPT_2017, ECMASCRIPT_NEXT, NO_TRANSPILE"
    )
    private String languageOut = "ECMASCRIPT5";

    @Option(name = "--version",
        handler = BooleanOptionHandler.class,
        usage = "Prints the compiler version to stdout and exit.")
    private boolean version = false;

    @Option(name = "--translations_file",
        hidden = true,
        usage = "Source of translated messages. Currently only supports XTB.")
    private String translationsFile = "";

    @Option(name = "--translations_project",
        hidden = true,
        usage = "Scopes all translations to the specified project."
        + "When specified, we will use different message ids so that messages "
        + "in different projects can have different translations.")
    private String translationsProject = null;

    @Option(name = "--flagfile",
        hidden = true,
        usage = "A file (or files) containing additional command-line options.")
    private List<String> flagFiles = new ArrayList<>();

    @Option(name = "--warnings_whitelist_file",
        usage = "A file containing warnings to suppress. Each line should be "
            + "of the form\n"
            + "<file-name>:<line-number>?  <warning-description>")
    private String warningsWhitelistFile = "";

    @Option(name = "--hide_warnings_for",
        usage = "If specified, files whose path contains this string will "
            + "have their warnings hidden. You may specify multiple.")
    private List<String> hideWarningsFor = new ArrayList<>();

    @Option(name = "--extra_annotation_name",
        usage = "A whitelist of tag names in JSDoc. You may specify multiple")
    private List<String> extraAnnotationName = new ArrayList<>();

    @Option(name = "--tracer_mode",
        hidden = true,
        usage = "Shows the duration of each compiler pass and the impact to "
        + "the compiled output size. "
        + "Options: ALL, AST_SIZE, RAW_SIZE, TIMING_ONLY, OFF")
    private CompilerOptions.TracerMode tracerMode =
        CompilerOptions.TracerMode.OFF;

    @Option(name = "--new_type_inf",
        handler = BooleanOptionHandler.class,
        usage = "Checks for type errors using the new type inference algorithm.")
    private boolean useNewTypeInference = false;

    @Option(name = "--rename_variable_prefix",
        usage = "Specifies a prefix that will be prepended to all variables.")
    private String renamePrefix = null;

    @Option(name = "--rename_prefix_namespace",
        usage = "Specifies the name of an object that will be used to store all "
        + "non-extern globals")
    private String renamePrefixNamespace = null;

    @Option(name = "--conformance_configs",
        usage = "A list of JS Conformance configurations in text protocol buffer format.")
    private List<String> conformanceConfigs = new ArrayList<>();

    @Option(name = "--env",
        usage = "Determines the set of builtin externs to load. "
            + "Options: BROWSER, CUSTOM. Defaults to BROWSER.")
    private CompilerOptions.Environment environment =
        CompilerOptions.Environment.BROWSER;


    @Option(name = "--instrumentation_template",
            hidden = true,
            usage = "A file containing an instrumentation template.")
    private String instrumentationFile = "";

    @Option(
      name = "--json_streams",
      hidden = true,
      usage =
          "Specifies whether standard input and output streams will be "
              + "a JSON array of sources. Each source will be an object of the "
              + "form {path: filename, src: file_contents, srcmap: srcmap_contents }. "
              + "Intended for use by stream-based build systems such as gulpjs. "
              + "Options: NONE, IN, OUT, BOTH. Defaults to NONE."
    )
    private CompilerOptions.JsonStreamMode jsonStreamMode = CompilerOptions.JsonStreamMode.NONE;

    @Option(name = "--preserve_type_annotations",
        hidden = true,
        handler = BooleanOptionHandler.class,
        usage = "Preserves type annotations.")
    private boolean preserveTypeAnnotations = false;

    @Option(name = "--inject_libraries",
        handler = BooleanOptionHandler.class,
        usage = "Allow injecting runtime libraries.")
    private boolean injectLibraries = true;

    @Option(name = "--force_inject_library",
        usage = "Force injection of named runtime libraries. "
        + "The format is <name> where <name> is the name of a runtime library. "
        + "Possible libraries include: base, es6_runtime, runtime_type_check")
    private List<String> forceInjectLibraries = new ArrayList<>();

    @Option(
      name = "--dependency_mode",
      usage = "Specifies how the compiler should determine the set and order "
      + "of files for a compilation. Options: NONE the compiler will include "
      + "all src files in the order listed, STRICT files will be included and "
      + "sorted by starting from namespaces or files listed by the "
      + "--entry_point flag - files will only be included if they are "
      + "referenced by a goog.require or CommonJS require or ES6 import, LOOSE "
      + "same as with STRICT but files which do not goog.provide a namespace "
      + "and are not modules will be automatically added as "
      + "--entry_point entries. Defaults to NONE."
    )
    private CompilerOptions.DependencyMode dependencyMode = CompilerOptions.DependencyMode.NONE;

    @Option(
      name = "--entry_point",
      usage = "A file or namespace to use as the starting point for determining "
      + "which src files to include in the compilation. ES6 and CommonJS "
      + "modules are specified as file paths (without the extension). "
      + "Closure-library namespaces are specified with a \"goog:\" prefix. "
      + "Example: --entry_point=goog:goog.Promise"
    )
    private List<String> entryPoints = new ArrayList<>();

    @Option(name = "--rewrite_polyfills",
        handler = BooleanOptionHandler.class,
        usage = "Rewrite ES6 library calls to use polyfills provided by the compiler's runtime.")
    private boolean rewritePolyfills = true;

    @Option(name = "--allow_method_call_decomposing",
        handler = BooleanOptionHandler.class,
        usage = "Allow decomposing x.y(); to: var tmp = x.y; tmp.call(x); Unsafe on IE 8 and 9")
    private boolean allowMethodCallDecomposing = false;

    @Option(
      name = "--print_source_after_each_pass",
      handler = BooleanOptionHandler.class,
      hidden = true,
      usage = "Whether to iteratively print resulting JS source per pass."
    )
    private boolean printSourceAfterEachPass = false;

    @Option(
      name = "--module_resolution",
      hidden = false,
      usage =
          "Specifies how the compiler locates modules. BROWSER requires all module imports "
              + "to begin with a '.' or '/' and have a file extension. NODE uses the node module "
              + "rules."
    )
    private ModuleLoader.ResolutionMode moduleResolutionMode = ModuleLoader.ResolutionMode.BROWSER;

    @Option(
      name = "--package_json_entry_names",
      usage =
          "Ordered list of entries to look for in package.json files when processing "
              + "modules with the NODE module resolution strategy (i.e. esnext:main,browser,main). "
              + "Defaults to a list with the following entries: \"browser\", \"module\", \"main\"."
    )
    private String packageJsonEntryNames = null;

    @Option(name = "--error_format", usage = "Specifies format for error messages.")
    private ErrorFormatOption errorFormat = ErrorFormatOption.STANDARD;

    @Option(name = "--renaming",
        handler = BooleanOptionHandler.class,
        usage = "Disables variable renaming. Cannot be used with ADVANCED optimizations.")
    private boolean renaming = true;

    @Argument
    private List<String> arguments = new ArrayList<>();
    private final CmdLineParser parser;

    Flags() {
      parser = new CmdLineParser(this);
    }

    /**
     * Parse the given args list.
     */
    private void parse(List<String> args) throws CmdLineException {
      parser.parseArgument(args.toArray(new String[] {}));

      compilationLevelParsed = CompilationLevel.fromString(Ascii.toUpperCase(compilationLevel));
      if (compilationLevelParsed == null) {
        throw new CmdLineException(
            parser, "Bad value for --compilation_level: " + compilationLevel);
      }
    }

    private static final Multimap<String, String> categories =
        new ImmutableMultimap.Builder<String, String>()
            .putAll(
                "Basic Usage",
                ImmutableList.of(
                    "compilation_level",
                    "env",
                    "externs",
                    "js",
                    "js_output_file",
                    "language_in",
                    "language_out",
                    "warning_level"))
            .putAll(
                "Warning and Error Management",
                ImmutableList.of(
                    "conformance_configs",
                    "error_format",
                    "extra_annotation_name",
                    "hide_warnings_for",
                    "jscomp_error",
                    "jscomp_off",
                    "jscomp_warning",
                    "new_type_inf",
                    "strict_mode_input",
                    "warnings_whitelist_file"))
            .putAll(
                "Output",
                ImmutableList.of(
                    "assume_function_wrapper",
                    "debug",
                    "export_local_property_definitions",
                    "formatting",
                    "generate_exports",
                    "isolation_mode",
                    "output_wrapper",
                    "output_wrapper_file"))
            .putAll("Dependency Management", ImmutableList.of("dependency_mode", "entry_point"))
            .putAll(
                "JS Modules",
                ImmutableList.of(
                    "js_module_root",
                    "module_resolution",
                    "process_common_js_modules",
                    "package_json_entry_names"))
            .putAll(
                "Library and Framework Specific",
                ImmutableList.of(
                    "angular_pass",
                    "dart_pass",
                    "force_inject_library",
                    "inject_libraries",
                    "polymer_pass",
                    "process_closure_primitives",
                    "rewrite_polyfills"))
            .putAll(
                "Code Splitting",
                ImmutableList.of("module", "module_output_path_prefix", "module_wrapper"))
            .putAll(
                "Reports",
                ImmutableList.of(
                    "create_source_map",
                    "output_manifest",
                    "output_module_dependencies",
                    "property_renaming_report",
                    "source_map_input",
                    "source_map_include_content",
                    "source_map_location_mapping",
                    "variable_renaming_report"))
            .putAll(
                "Miscellaneous",
                ImmutableList.of(
                    "charset",
                    "checks_only",
                    "define",
                    "flagfile",
                    "help",
                    "third_party",
                    "use_types_for_optimization",
                    "version"))
            .build();

    private void printUsage(PrintStream ps) {
      OutputStreamWriter outputStream = new OutputStreamWriter(ps, UTF_8);

      boolean isFirst = true;
      for (Map.Entry<String, Collection<String>> entry : categories.asMap().entrySet()) {
        String prefix = "\n\n";
        String suffix = "";
        if (isFirst) {
          isFirst = false;
          prefix = "";
        }

        if (entry.getKey().equals("Warning and Error Management")) {
          suffix =
              "\n"
                  + boldPrefix
                  + "Available Error Groups: "
                  + normalPrefix
                  + DiagnosticGroups.DIAGNOSTIC_GROUP_NAMES;
        }

        printCategoryUsage(entry.getKey(), entry.getValue(), outputStream, prefix, suffix);
      }

      ps.flush();
    }

    private final String boldPrefix = "\033[1m";
    private final String normalPrefix = "\033[0m";

    private void printCategoryUsage(
        String categoryName,
        final Collection<String> options,
        OutputStreamWriter outputStream,
        String prefix,
        String suffix) {

      try {
        if (prefix != null) {
          printStringLineWrapped(prefix, outputStream);
        }

        outputStream.write(boldPrefix + categoryName + ":\n" + normalPrefix);

        parser.printUsage(
            outputStream,
            null,
            new OptionHandlerFilter() {
              @Override
              public boolean select(OptionHandler optionHandler) {
                if (optionHandler.option instanceof NamedOptionDef) {
                  return !optionHandler.option.hidden()
                      && options.contains(
                          ((NamedOptionDef) optionHandler.option).name().replaceFirst("^--", ""));
                }
                return false;
              }
            });

        if (suffix != null) {
          printStringLineWrapped(suffix, outputStream);
        }
      } catch (IOException e) {
        // Ignore.
      }
    }

    private final int maxLineLength = 80;
    private final Pattern whitespacePattern = Pattern.compile("\\s");

    private void printStringLineWrapped(String input, OutputStreamWriter outputStream)
        throws IOException {
      if (input.length() < maxLineLength) {
        outputStream.write(input);
        return;
      }

      int endIndex = maxLineLength;
      String subString = input.substring(0, maxLineLength);
      Matcher whitespaceMatcher = whitespacePattern.matcher(subString);
      boolean foundMatch = false;
      while (whitespaceMatcher.find()) {
        endIndex = whitespaceMatcher.start();
        foundMatch = true;
      }
      outputStream.write(input.substring(0, endIndex) + "\n");
      printStringLineWrapped(
          "    " + input.substring(foundMatch ? endIndex + 1 : endIndex), outputStream);
    }

    private static void printShortUsageAfterErrors(PrintStream ps) {
      ps.print("Sample usage: ");
      ps.println("--compilation_level (-O) VAL --externs VAL --js VAL"
          + " --js_output_file VAL"
          + " --warning_level (-W) [QUIET | DEFAULT | VERBOSE]");
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
     *     | xargs java -jar compiler.jar --dependency_mode=LOOSE
     * </pre>
     * The {@code find} command will produce a list of '*.js' source files in
     * the {@code MY_JS_SRC_DIR} directory while {@code xargs} will convert them
     * to a single, space-delimited set of arguments that are appended to the
     * {@code java} command to run the Compiler.
     * <p>
     * Note that it is important to use the
     * {@code --dependency_mode=LOOSE or STRICT} option in this case because the
     * order produced by {@code find} is unlikely to be sorted correctly with
     * respect to {@code goog.provide()} and {@code goog.requires()}.
     */
    protected List<String> getJsFiles() throws CmdLineException, IOException {
      List<String> patterns = new ArrayList<>();
      patterns.addAll(js);
      patterns.addAll(arguments);
      List<String> allJsInputs = findJsFiles(patterns);
      if (!patterns.isEmpty() && allJsInputs.isEmpty()) {
        throw new CmdLineException(parser, "No inputs matched");
      }
      return allJsInputs;
    }

    protected List<FlagEntry<JsSourceType>> getMixedJsSources()
        throws CmdLineException, IOException {
      List<FlagEntry<JsSourceType>> mixedSources = new ArrayList<>();
      Set<String> excludes = new HashSet<>();
      for (FlagEntry<JsSourceType> source : Flags.mixedJsSources) {
        if (source.getValue().endsWith(".zip")) {
          mixedSources.add(source);
        } else if (source.getValue().startsWith("!")) {
          for (String filename : findJsFiles(
              Collections.singletonList(source.getValue().substring(1)))) {
            excludes.add(filename);
            mixedSources.remove(new FlagEntry<>(JsSourceType.JS, filename));
          }
        } else {
          for (String filename : findJsFiles(Collections.singletonList(source.getValue()), true)) {
            if (!excludes.contains(filename)) {
              mixedSources.add(new FlagEntry<>(JsSourceType.JS, filename));
            }
          }
        }
      }
      List<String> fromArguments = findJsFiles(arguments);
      for (String filename : fromArguments) {
        mixedSources.add(new FlagEntry<>(JsSourceType.JS, filename));
      }
      if (!Flags.mixedJsSources.isEmpty() && !arguments.isEmpty() && mixedSources.isEmpty()) {
        throw new CmdLineException(parser, "No inputs matched");
      }
      return mixedSources;
    }

    List<SourceMap.LocationMapping> getSourceMapLocationMappings() throws CmdLineException {
      ImmutableList.Builder<LocationMapping> locationMappings = ImmutableList.builder();

      ImmutableMap<String, String> split = splitPipeParts(
          sourceMapLocationMapping, "--source_map_location_mapping");
      for (Map.Entry<String, String> mapping : split.entrySet()) {
        locationMappings.add(new SourceMap.LocationMapping(mapping.getKey(),
            mapping.getValue()));
      }

      return locationMappings.build();
    }

    ImmutableMap<String, String> getSourceMapInputs() throws CmdLineException {
      return splitPipeParts(sourceMapInputs, "--source_map_input");
    }

    private ImmutableMap<String, String> splitPipeParts(Iterable<String> input,
        String flagName) throws CmdLineException {
      ImmutableMap.Builder<String, String> result = new ImmutableMap.Builder<>();

      Splitter splitter = Splitter.on('|').limit(2);
      for (String inputSourceMap : input) {
        List<String> parts = splitter.splitToList(inputSourceMap);
        if (parts.size() != 2) {
          throw new CmdLineException(parser, "Bad value for " + flagName +
              " (duplicate key): " + input);
        }
        result.put(parts.get(0), parts.get(1));
      }

      return result.build();
    }

    List<String> getPackageJsonEntryNames() throws CmdLineException {
      return Splitter.on(',').splitToList(packageJsonEntryNames);
    }

    // Our own option parser to be backwards-compatible.
    // It needs to be public because of the crazy reflection that args4j does.
    public static class BooleanOptionHandler extends OptionHandler<Boolean> {
      private static final ImmutableSet<String> TRUES = ImmutableSet.of("true", "on", "yes", "1");
      private static final ImmutableSet<String> FALSES = ImmutableSet.of("false", "off", "no", "0");

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
          String lowerParam = Ascii.toLowerCase(param);
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
        super(parser, option,
            new MultiFlagSetter<>(setter, CheckLevel.ERROR, guardLevels));
      }
    }

    public static class WarningGuardWarningOptionHandler
        extends StringOptionHandler {
      public WarningGuardWarningOptionHandler(
          CmdLineParser parser, OptionDef option,
          Setter<? super String> setter) {
        super(parser, option,
            new MultiFlagSetter<>(setter, CheckLevel.WARNING, guardLevels));
      }
    }

    public static class WarningGuardOffOptionHandler
        extends StringOptionHandler {
      public WarningGuardOffOptionHandler(
          CmdLineParser parser, OptionDef option,
          Setter<? super String> setter) {
        super(parser, option,
            new MultiFlagSetter<>(setter, CheckLevel.OFF, guardLevels));
      }
    }

    public static class JsOptionHandler extends StringOptionHandler {
      public JsOptionHandler(
          CmdLineParser parser, OptionDef option,
          Setter<? super String> setter) {
        super(parser, option,
            new MultiFlagSetter<>(setter, JsSourceType.JS, mixedJsSources));
      }
    }

    public static class JsZipOptionHandler extends StringOptionHandler {
      public JsZipOptionHandler(
          CmdLineParser parser, OptionDef option,
          Setter<? super String> setter) {
        super(parser, option,
            new MultiFlagSetter<>(setter, JsSourceType.JS_ZIP, mixedJsSources));
      }
    }

    private static class MultiFlagSetter<T> implements Setter<String> {
      private final Setter<? super String> proxy;
      private final T flag;
      private final List<FlagEntry<T>> entries;

      private MultiFlagSetter(
          Setter<? super String> proxy, T flag, List<FlagEntry<T>> entries) {
        this.proxy = proxy;
        this.flag = flag;
        this.entries = entries;
      }

      @Override public boolean isMultiValued() {
        return proxy.isMultiValued();
      }

      @Override public Class<String> getType() {
        return (Class<String>) proxy.getType();
      }

      @Override public void addValue(String value) throws CmdLineException {
        proxy.addValue(value);
        entries.add(new FlagEntry<>(flag, value));
      }

      @Override public FieldSetter asFieldSetter() {
        return proxy.asFieldSetter();
      }

      @Override public AnnotatedElement asAnnotatedElement() {
        return proxy.asAnnotatedElement();
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
          options.setPrettyPrint(true);
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

  protected CommandLineRunner(String[] args, InputStream in, PrintStream out, PrintStream err) {
    super(in, out, err);
    initConfigFromFlags(args, out, err);
  }

  private static List<String> processArgs(String[] args) {
    // Args4j has a different format that the old command-line parser.
    // So we use some voodoo to get the args into the format that args4j
    // expects.
    Pattern argPattern = Pattern.compile("(--?[a-zA-Z_]+)=(.*)", Pattern.DOTALL);
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

  private void processFlagFiles() throws CmdLineException {
    for (String flagFile : flags.flagFiles) {
      try {
        processFlagFile(flagFile);
      } catch (IOException ioErr) {
        reportError("ERROR - " + flagFile + " read error.");
      }
    }
  }

  private void processFlagFile(String flagFileString)
            throws CmdLineException, IOException {
    Path flagFile = Paths.get(flagFileString);

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
            builder.setCharAt(builder.length() - 1, (char) c);
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

    flags.flagFiles = new ArrayList<>();

    tokens = processArgs(tokens.toArray(new String[0]));

    // Command-line warning levels should override flag file settings,
    // which means they should go last.
    List<FlagEntry<CheckLevel>> previousGuardLevels = new ArrayList<>(Flags.guardLevels);
    List<FlagEntry<JsSourceType>> previousMixedJsSources = new ArrayList<>(Flags.mixedJsSources);
    Flags.guardLevels.clear();
    Flags.mixedJsSources.clear();
    flags.parse(tokens);
    Flags.guardLevels.addAll(previousGuardLevels);
    Flags.mixedJsSources.addAll(previousMixedJsSources);

    // Currently we are not supporting this (prevent direct/indirect loops)
    if (!flags.flagFiles.isEmpty()) {
      reportError("ERROR - Arguments in the file cannot contain "
          + "--flagfile option.");
    }
  }

  private void initConfigFromFlags(String[] args, PrintStream out, PrintStream err) {

    errorStream = err;
    List<String> processedArgs = processArgs(args);

    Flags.guardLevels.clear();
    Flags.mixedJsSources.clear();

    List<String> jsFiles = null;
    List<FlagEntry<JsSourceType>> mixedSources = null;
    List<LocationMapping> mappings = null;
    ImmutableMap<String, String> sourceMapInputs = null;
    boolean parseInlineSourceMaps = false;
    boolean applyInputSourceMaps = false;
    try {
      flags.parse(processedArgs);

      processFlagFiles();

      jsFiles = flags.getJsFiles();
      mixedSources = flags.getMixedJsSources();
      mappings = flags.getSourceMapLocationMappings();
      sourceMapInputs = flags.getSourceMapInputs();
      parseInlineSourceMaps = flags.parseInlineSourceMaps;
      applyInputSourceMaps = flags.applyInputSourceMaps;
    } catch (CmdLineException e) {
      reportError(e.getMessage());
    } catch (IOException ioErr) {
      reportError("ERROR - ioException: " + ioErr);
    }

    List<ModuleIdentifier> entryPoints = new ArrayList<>();

    if (flags.processCommonJsModules) {
      flags.processClosurePrimitives = true;
      if (flags.commonJsEntryModule != null) {
        if (flags.entryPoints.isEmpty()) {
          entryPoints.add(ModuleIdentifier.forFile(flags.commonJsEntryModule));
        } else {
          reportError("--common_js_entry_module cannot be used with --entry_point.");
        }
      }
    }

    if (flags.outputWrapper == null) {
      flags.outputWrapper = "";
    }

    if (flags.outputWrapperFile != null && !flags.outputWrapperFile.isEmpty()) {
      try {
        flags.outputWrapper = Files.asCharSource(new File(flags.outputWrapperFile), UTF_8).read();
      } catch (Exception e) {
        reportError("ERROR - invalid output_wrapper_file specified.");
      }
    }

    if (!flags.outputWrapper.isEmpty() &&
        !flags.outputWrapper.contains(CommandLineRunner.OUTPUT_MARKER)) {
      reportError("ERROR - invalid output_wrapper specified. Missing '" +
          CommandLineRunner.OUTPUT_MARKER + "'.");
    }

    if (!flags.outputWrapper.isEmpty() && flags.isolationMode != IsolationMode.NONE) {
      reportError("--output_wrapper and --isolation_mode may not be used together.");
    }

    if (flags.isolationMode == IsolationMode.IIFE) {
      flags.outputWrapper = "(function(){%output%}).call(this);";
    }

    // Handle --compilation_level=BUNDLE
    List<String> bundleFiles = ImmutableList.of();
    boolean skipNormalOutputs = false;
    if (flags.compilationLevelParsed == CompilationLevel.BUNDLE) {
      if (flags.jsOutputFile.isEmpty()) {
        reportError("--compilation_level=BUNDLE cannot be used without a --js_output_file.");
      } else {
        bundleFiles = ImmutableList.of(flags.jsOutputFile);
        flags.jsOutputFile = "";
        skipNormalOutputs = true;
      }
    }

    if (errors) {
      Flags.printShortUsageAfterErrors(errorStream);
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
      } else if (flags.chromePass) {
        conv = new ChromeCodingConvention();
      } else {
        conv = new ClosureCodingConvention();
      }

      // For backwards compatibility, allow both commonJsPathPrefix and jsModuleRoot.
      List<String> moduleRoots = new ArrayList<>();
      if (!flags.moduleRoot.isEmpty()) {
        moduleRoots.addAll(flags.moduleRoot);

        if (!flags.commonJsPathPrefix.isEmpty()) {
          reportError("--commonJsPathPrefix cannot be used with --js_module_root.");
        }
      } else if (flags.commonJsPathPrefix != null) {
        moduleRoots.addAll(flags.commonJsPathPrefix);
      } else {
        moduleRoots.add(ModuleLoader.DEFAULT_FILENAME_PREFIX);
      }

      for (String entryPoint : flags.entryPoints) {
        if (entryPoint.startsWith("goog:")) {
          entryPoints.add(ModuleIdentifier.forClosure(entryPoint));
        } else {
          entryPoints.add(ModuleIdentifier.forFile(entryPoint));
        }
      }

      if (flags.dependencyMode == CompilerOptions.DependencyMode.STRICT && entryPoints.isEmpty()) {
        reportError(
            "When --dependency_mode=STRICT, you must specify at least " + "one --entry_point.");
      }

      CompilerOptions.DependencyMode depMode = flags.dependencyMode;

      if (flags.onlyClosureDependencies || flags.manageClosureDependencies) {
        if (flags.dependencyMode != CompilerOptions.DependencyMode.NONE) {
          reportError(
              (flags.onlyClosureDependencies
                      ? "--only_closure_dependencies"
                      : "--manage_closure_dependencies")
                  + " cannot be used with --dependency_mode.");
        } else {
          if (flags.manageClosureDependencies) {
            depMode = CompilerOptions.DependencyMode.LOOSE;
          } else if (flags.onlyClosureDependencies) {
            depMode = CompilerOptions.DependencyMode.STRICT;
          }

          if (!flags.closureEntryPoint.isEmpty() && !flags.entryPoints.isEmpty()) {
            reportError("--closure_entry_point cannot be used with --entry_point.");
          } else {
            for (String entryPoint : flags.closureEntryPoint) {
              entryPoints.add(ModuleIdentifier.forClosure(entryPoint));
            }
          }
        }
      }

      if (!flags.renaming
          && flags.compilationLevelParsed == CompilationLevel.ADVANCED_OPTIMIZATIONS) {
        reportError("ERROR - renaming cannot be disabled when ADVANCED_OPTMIZATIONS is used.");
        runCompiler = false;
      }

      getCommandLineConfig()
          .setPrintTree(flags.printTree)
          .setPrintAst(flags.printAst)
          .setPrintPassGraph(flags.printPassGraph)
          .setJscompDevMode(flags.jscompDevMode)
          .setLoggingLevel(flags.loggingLevel)
          .setExterns(flags.externs)
          .setJs(jsFiles)
          .setJsZip(flags.jszip)
          .setMixedJsSources(mixedSources)
          .setJsOutputFile(flags.jsOutputFile)
          .setSaveAfterChecksFileName(flags.saveAfterChecksFile)
          .setContinueSavedCompilationFileName(flags.continueSavedCompilationFile)
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
          .setSourceMapInputFiles(sourceMapInputs)
          .setParseInlineSourceMaps(parseInlineSourceMaps)
          .setApplyInputSourceMaps(applyInputSourceMaps)
          .setWarningGuards(Flags.guardLevels)
          .setDefine(flags.define)
          .setCharset(flags.charset)
          .setDependencyMode(depMode)
          .setEntryPoints(entryPoints)
          .setOutputManifest(ImmutableList.of(flags.outputManifest))
          .setOutputBundle(bundleFiles)
          .setSkipNormalOutputs(skipNormalOutputs)
          .setOutputModuleDependencies(flags.outputModuleDependencies)
          .setProcessCommonJSModules(flags.processCommonJsModules)
          .setModuleRoots(moduleRoots)
          .setTransformAMDToCJSModules(flags.transformAmdModules)
          .setWarningsWhitelistFile(flags.warningsWhitelistFile)
          .setHideWarningsFor(flags.hideWarningsFor)
          .setAngularPass(flags.angularPass)
          .setInstrumentationTemplateFile(flags.instrumentationFile)
          .setNewTypeInference(flags.useNewTypeInference)
          .setJsonStreamMode(flags.jsonStreamMode)
          .setErrorFormat(flags.errorFormat);
    }
    errorStream = null;
  }

  @Override
  protected void addWhitelistWarningsGuard(
      CompilerOptions options, File whitelistFile) {
    options.addWarningsGuard(WhitelistWarningsGuard.fromFile(whitelistFile));
  }

  @Override
  protected void checkModuleName(String name) {
    if (!TokenStream.isJSIdentifier(
        extraModuleNameChars.matcher(name).replaceAll("_"))) {
      throw new FlagUsageException("Invalid module name: '" + name + "'");
    }
  }

  @Override
  protected CompilerOptions createOptions() {
    CompilerOptions options = new CompilerOptions();

    if (!flags.languageIn.isEmpty()) {
      CompilerOptions.LanguageMode languageMode =
          CompilerOptions.LanguageMode.fromString(flags.languageIn);
      if (languageMode != null) {
        options.setLanguageIn(languageMode);
      } else {
        throw new FlagUsageException("Unknown language `" + flags.languageIn + "' specified.");
      }
    }

    if (flags.languageOut.isEmpty()) {
      options.setLanguageOut(options.getLanguageIn());
    } else {
      CompilerOptions.LanguageMode languageMode =
          CompilerOptions.LanguageMode.fromString(flags.languageOut);
      if (languageMode != null) {
        options.setLanguageOut(languageMode);
      } else {
        throw new FlagUsageException("Unknown language `" + flags.languageOut + "' specified.");
      }
    }

    options.setCodingConvention(new ClosureCodingConvention());

    options.setExtraAnnotationNames(flags.extraAnnotationName);

    CompilationLevel level = flags.compilationLevelParsed;
    level.setOptionsForCompilationLevel(options);

    if (flags.debug) {
      level.setDebugOptionsForCompilationLevel(options);
    }

    options.setEnvironment(flags.environment);

    options.setChecksOnly(flags.checksOnly);
    if (flags.checksOnly) {
      options.setOutputJs(CompilerOptions.OutputJs.NONE);
    }

    options.setIncrementalChecks(flags.incrementalCheckMode);

    options.setContinueAfterErrors(flags.continueAfterErrors);

    if (flags.useTypesForOptimization) {
      level.setTypeBasedOptimizationOptions(options);
    }

    if (flags.assumeFunctionWrapper || flags.isolationMode == IsolationMode.IIFE) {
      level.setWrappedOutputOptimizations(options);
    }

    if (flags.generateExports) {
      options.setGenerateExports(flags.generateExports);
    }

    if (flags.exportLocalPropertyDefinitions) {
      options.setExportLocalPropertyDefinitions(true);
    }

    WarningLevel wLevel = flags.warningLevel;
    wLevel.setOptionsForWarningLevel(options);
    for (FormattingOption formattingOption : flags.formatting) {
      formattingOption.applyToOptions(options);
    }

    options.closurePass = flags.processClosurePrimitives;

    options.angularPass = flags.angularPass;

    if (flags.polymerPass) {
      options.polymerVersion = 1;
    } else {
      options.polymerVersion = flags.polymerVersion;
    }

    options.setChromePass(flags.chromePass);

    options.setDartPass(flags.dartPass);

    if (!flags.j2clPassMode.isEmpty()) {
      try {
        CompilerOptions.J2clPassMode j2clPassMode =
            CompilerOptions.J2clPassMode.valueOf(Ascii.toUpperCase(flags.j2clPassMode));
        options.setJ2clPass(j2clPassMode);
      } catch (IllegalArgumentException ex) {
        throw new FlagUsageException(
            "Unknown J2clPassMode `" + flags.j2clPassMode + "' specified.");
      }
    }

    options.renamePrefix = flags.renamePrefix;

    options.renamePrefixNamespace = flags.renamePrefixNamespace;

    options.setPreserveTypeAnnotations(flags.preserveTypeAnnotations);

    options.setPreventLibraryInjection(!flags.injectLibraries);

    if (!flags.forceInjectLibraries.isEmpty()) {
      options.setForceLibraryInjection(flags.forceInjectLibraries);
    }

    options.rewritePolyfills =
        flags.rewritePolyfills && options.getLanguageIn().toFeatureSet().contains(FeatureSet.ES6);

    options.setAllowMethodCallDecomposing(flags.allowMethodCallDecomposing);

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
      options.setWarningLevel(DiagnosticGroups.MSG_CONVENTIONS, CheckLevel.OFF);
    }

    options.setConformanceConfigs(loadConformanceConfigs(flags.conformanceConfigs));

    if (!flags.instrumentationFile.isEmpty()) {
      String instrumentationPb;
      Instrumentation.Builder builder = Instrumentation.newBuilder();
      try (BufferedReader br =
          new BufferedReader(Files.newReader(new File(flags.instrumentationFile), UTF_8))) {
        StringBuilder sb = new StringBuilder();
        String line = br.readLine();

        while (line != null) {
          sb.append(line);
          sb.append(System.lineSeparator());
          line = br.readLine();
        }
        instrumentationPb = sb.toString();
        TextFormat.merge(instrumentationPb, builder);

        // Setting instrumentation template
        options.instrumentationTemplate = builder.build();

      } catch (IOException e) {
        throw new RuntimeException("Error reading instrumentation template", e);
      }
    }

    options.setPrintSourceAfterEachPass(flags.printSourceAfterEachPass);
    options.setTracerMode(flags.tracerMode);
    options.setStrictModeInput(flags.strictModeInput);
    if (!flags.emitUseStrict) {
      options.setEmitUseStrict(false);
    }
    options.setSourceMapIncludeSourcesContent(flags.sourceMapIncludeSourcesContent);
    options.setModuleResolutionMode(flags.moduleResolutionMode);

    if (flags.packageJsonEntryNames != null) {
      try {
        List<String> packageJsonEntryNames = flags.getPackageJsonEntryNames();
        options.setPackageJsonEntryNames(packageJsonEntryNames);
      } catch (CmdLineException e) {
        reportError("ERROR - invalid package_json_entry_names format specified.");
      }
    }

    if (!flags.renaming) {
      options.setVariableRenaming(VariableRenamingPolicy.OFF);
      options.setPropertyRenaming(PropertyRenamingPolicy.OFF);
    }

    return options;
  }

  @Override
  protected Compiler createCompiler() {
    return new Compiler(getErrorPrintStream());
  }

  @Override
  protected void prepForBundleAndAppendTo(Appendable out, CompilerInput input, String content)
      throws IOException {
    ClosureBundler.appendInput(out, input, content);
  }

  @Override
  protected List<SourceFile> createExterns(CompilerOptions options) throws IOException {
    List<SourceFile> externs = super.createExterns(options);
    if (isInTestMode()) {
      return externs;
    } else {
      List<SourceFile> builtinExterns = getBuiltinExterns(options.getEnvironment());
      builtinExterns.addAll(externs);
      return builtinExterns;
    }
  }

  private static ImmutableList<ConformanceConfig> loadConformanceConfigs(List<String> configPaths) {
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
    String textProto = Files.asCharSource(new File(configFile), UTF_8).read();

    ConformanceConfig.Builder builder = ConformanceConfig.newBuilder();

    // Looking for BOM.
    if (!textProto.isEmpty() && textProto.charAt(0) == UTF8_BOM_CODE) {
      // Stripping the BOM.
      textProto = textProto.substring(1);
    }

    try {
      TextFormat.merge(textProto, builder);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return builder.build();
  }

  @Deprecated
  public static List<SourceFile> getDefaultExterns() throws IOException {
    return getBuiltinExterns(CompilerOptions.Environment.BROWSER);
  }

  /**
   * Returns all the JavaScript files from the set of patterns. The patterns support
   * globs, such as '*.js' for all JS files in a directory and '**.js' for all JS files
   * within the directory and sub-directories.
   */
  public static List<String> findJsFiles(Collection<String> patterns) throws IOException {
    return findJsFiles(patterns, false);
  }

  /**
   * Returns all the JavaScript files from the set of patterns.
   *
   * @param patterns A collection of filename patterns.
   * @param sortAlphabetically Whether the output filenames should be in alphabetical order.
   * @return The list of JS filenames found by expanding the patterns.
   */
  private static List<String> findJsFiles(Collection<String> patterns, boolean sortAlphabetically)
      throws IOException {
    // A map from normalized absolute paths to original paths. We need to return original paths to
    // support whitelist files that depend on them.
    Map<String, String> allJsInputs = sortAlphabetically
        ? new TreeMap<>() : new LinkedHashMap<>();
    Set<String> excludes = new HashSet<>();
    for (String pattern : patterns) {
      if (!pattern.contains("*") && !pattern.startsWith("!")) {
        File matchedFile = new File(pattern);
        if (matchedFile.isDirectory()) {
          matchPaths(new File(matchedFile, "**.js").toString(), allJsInputs, excludes);
        } else {
          Path original = Paths.get(pattern);
          String pathStringAbsolute = original.normalize().toAbsolutePath().toString();
          if (!excludes.contains(pathStringAbsolute)) {
            allJsInputs.put(pathStringAbsolute, original.toString());
          }
        }
      } else {
        matchPaths(pattern, allJsInputs, excludes);
      }
    }

    return new ArrayList<>(allJsInputs.values());
  }

  private static void matchPaths(String pattern, final Map<String, String> allJsInputs,
      final Set<String> excludes) throws IOException {
    FileSystem fs = FileSystems.getDefault();
    final boolean remove = pattern.indexOf('!') == 0;
    if (remove) {
      pattern = pattern.substring(1);
    }

    String separator = File.separator.equals("\\") ? "\\\\" : File.separator;

    // Split the pattern into two pieces: the globbing part
    // and the non-globbing prefix.
    List<String> patternParts = Splitter.on(File.separator).splitToList(pattern);
    String prefix = ".";
    for (int i = 0; i < patternParts.size(); i++) {
      if (patternParts.get(i).contains("*")) {
        if (i > 0) {
          prefix = Joiner.on(separator).join(patternParts.subList(0, i));
          pattern = Joiner.on(separator).join(patternParts.subList(i, patternParts.size()));
        }
        break;
      }
    }

    final PathMatcher matcher = fs.getPathMatcher("glob:" + prefix + separator + pattern);
    java.nio.file.Files.walkFileTree(
        fs.getPath(prefix), new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path p, BasicFileAttributes attrs) {
            if (matcher.matches(p) || matcher.matches(p.normalize())) {
              String pathStringAbsolute = p.normalize().toAbsolutePath().toString();
              if (remove) {
                excludes.add(pathStringAbsolute);
                allJsInputs.remove(pathStringAbsolute);
              } else if (!excludes.contains(pathStringAbsolute)) {
                allJsInputs.put(pathStringAbsolute, p.toString());
              }
            }
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFileFailed(Path file, IOException e) {
            return FileVisitResult.SKIP_SUBTREE;
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
