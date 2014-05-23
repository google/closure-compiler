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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionDef;
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
import java.io.PrintStream;
import java.lang.reflect.AnnotatedElement;
import java.nio.charset.Charset;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.util.ArrayList;
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

  public static final String OUTPUT_MARKER =
      AbstractCommandLineRunner.OUTPUT_MARKER;

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
    private static List<GuardLevel> guardLevels = new ArrayList<>();

    @Option(name = "--help",
        handler = BooleanOptionHandler.class,
        usage = "Displays this message")
    private boolean displayHelp = false;

    @Option(name = "--print_tree",
        handler = BooleanOptionHandler.class,
        usage = "Prints out the parse tree and exits")
    private boolean printTree = false;

    @Option(name = "--print_ast",
        handler = BooleanOptionHandler.class,
        usage = "Prints a dot file describing the internal abstract syntax"
        + " tree and exits")
    private boolean printAst = false;

    @Option(name = "--print_pass_graph",
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
        usage = "A JavaScript module specification. The format is "
        + "<name>:<num-js-files>[:[<dep>,...][:]]]. Module names must be "
        + "unique. Each dep is the name of a module that this module "
        + "depends on. Modules must be listed in dependency order, and JS "
        + "source files must be listed in the corresponding order. Where "
        + "--module flags occur in relation to --js flags is unimportant. "
        + "Provide the value 'auto' to trigger module creation from CommonJS"
        + "modules.")
    private List<String> module = new ArrayList<>();

    @Option(name = "--third_party",
        handler = BooleanOptionHandler.class,
        usage = "Check source validity but do not enforce Closure style "
        + "rules and conventions")
    private boolean thirdParty = false;

    @Option(name = "--summary_detail_level",
        usage = "Controls how detailed the compilation summary is. Values:"
        + " 0 (never print summary), 1 (print summary only if there are "
        + "errors or warnings), 2 (print summary if the 'checkTypes' "
        + "diagnostic  group is enabled, see --jscomp_warning), "
        + "3 (always print summary). The default level is 1")
    private int summaryDetailLevel = 1;

    @Option(name = "--output_wrapper",
        usage = "Interpolate output into this string at the place denoted"
        + " by the marker token %output%. Use marker token %output|jsstring%"
        + " to do js string escaping on the output.")
    private String outputWrapper = "";

    @Option(name = "--module_wrapper",
        usage = "An output wrapper for a JavaScript module (optional). "
        + "The format is <name>:<wrapper>. The module name must correspond "
        + "with a module specified using --module. The wrapper must "
        + "contain %s as the code placeholder. The %basename% placeholder can "
        + "also be used to substitute the base name of the module output file.")
    private List<String> moduleWrapper = new ArrayList<>();

    @Option(name = "--module_output_path_prefix",
        usage = "Prefix for filenames of compiled JS modules. "
        + "<module-name>.js will be appended to this prefix. Directories "
        + "will be created as needed. Use with --module")
    private String moduleOutputPathPrefix = "./";

    @Option(name = "--create_source_map",
        usage = "If specified, a source map file mapping the generated " +
        "source files back to the original source file will be " +
        "output to the specified path. The %outname% placeholder will " +
        "expand to the name of the output file that the source map " +
        "corresponds to.")
    private String createSourceMap = "";

    @Option(name = "--source_map_format",
        usage = "The source map format to produce. " +
        "Options are V3 and DEFAULT, which are equivalent.")
    private SourceMap.Format sourceMapFormat = SourceMap.Format.DEFAULT;

    @Option(name = "--source_map_location_mapping",
        usage = "Source map location mapping separated by a '|' " +
        "(i.e. filesystem-path|webserver-path)")
    private List<String> sourceMapLocationMapping = Lists.newArrayList();

    // Used to define the flag, values are stored by the handler.
    @SuppressWarnings("unused")
    @Option(name = "--jscomp_error",
        handler = WarningGuardErrorOptionHandler.class,
        usage = "Make the named class of warnings an error. Options:" +
        DiagnosticGroups.DIAGNOSTIC_GROUP_NAMES)
    private List<String> jscompError = new ArrayList<>();

    // Used to define the flag, values are stored by the handler.
    @SuppressWarnings("unused")
    @Option(name = "--jscomp_warning",
        handler = WarningGuardWarningOptionHandler.class,
        usage = "Make the named class of warnings a normal warning. " +
        "Options:" + DiagnosticGroups.DIAGNOSTIC_GROUP_NAMES)
    private List<String> jscompWarning = new ArrayList<>();

    // Used to define the flag, values are stored by the handler.
    @SuppressWarnings("unused")
    @Option(name = "--jscomp_off",
        handler = WarningGuardOffOptionHandler.class,
        usage = "Turn off the named class of warnings. Options:" +
        DiagnosticGroups.DIAGNOSTIC_GROUP_NAMES)
    private List<String> jscompOff = new ArrayList<>();

    @Option(name = "--define",
        aliases = {"--D", "-D"},
        usage = "Override the value of a variable annotated @define. " +
        "The format is <name>[=<val>], where <name> is the name of a @define " +
        "variable and <val> is a boolean, number, or a single-quoted string " +
        "that contains no single quotes. If [=<val>] is omitted, " +
        "the variable is marked true")
    private List<String> define = new ArrayList<>();

    @Option(name = "--charset",
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
        usage = "Experimental: perform additional optimizations " +
        "based on available information.  Inaccurate type annotations " +
        "may result in incorrect results.")
    private boolean useTypesForOptimization = false;

    @Option(name = "--warning_level",
        aliases = {"-W"},
        usage = "Specifies the warning level to use. Options: " +
        "QUIET, DEFAULT, VERBOSE")
    private WarningLevel warningLevel = WarningLevel.DEFAULT;

    @Option(name = "--use_only_custom_externs",
        handler = BooleanOptionHandler.class,
        usage = "Specifies whether the default externs should be excluded")
    private boolean useOnlyCustomExterns = false;

    @Option(name = "--debug",
        handler = BooleanOptionHandler.class,
        usage = "Enable debugging options")
    private boolean debug = false;

    @Option(name = "--generate_exports",
        handler = BooleanOptionHandler.class,
        usage = "Generates export code for those marked with @export")
    private boolean generateExports = false;

    @Option(name = "--formatting",
        usage = "Specifies which formatting options, if any, should be "
        + "applied to the output JS. Options: "
        + "PRETTY_PRINT, PRINT_INPUT_DELIMITER, SINGLE_QUOTES")
    private List<FormattingOption> formatting = new ArrayList<>();

    @Option(name = "--process_common_js_modules",
        usage = "Process CommonJS modules to a concatenable form.")
    private boolean processCommonJsModules = false;

    @Option(name = "--common_js_module_path_prefix",
        usage = "Path prefix to be removed from CommonJS module names.")
    private String commonJsPathPrefix =
        ProcessCommonJSModules.DEFAULT_FILENAME_PREFIX;

    @Option(name = "--common_js_entry_module",
        usage = "Root of your common JS dependency hierarchy. " +
            "Your main script.")
    private String commonJsEntryModule;

    @Option(name = "--transform_amd_modules",
        usage = "Transform AMD to CommonJS modules.")
    private boolean transformAmdModules = false;

    @Option(name = "--process_closure_primitives",
        handler = BooleanOptionHandler.class,
        usage = "Processes built-ins from the Closure library, such as "
        + "goog.require(), goog.provide(), and goog.exportSymbol()")
    private boolean processClosurePrimitives = true;

    @Option(name = "--manage_closure_dependencies",
        handler = BooleanOptionHandler.class,
        usage = "Automatically sort dependencies so that a file that "
        + "goog.provides symbol X will always come before a file that "
        + "goog.requires symbol X. If an input provides symbols, and "
        + "those symbols are never required, then that input will not "
        + "be included in the compilation.")
    private boolean manageClosureDependencies = false;

    @Option(name = "--only_closure_dependencies",
        handler = BooleanOptionHandler.class,
        usage = "Only include files in the transitive dependency of the "
        + "entry points (specified by closure_entry_point). Files that do "
        + "not provide dependencies will be removed. This supersedes"
        + "manage_closure_dependencies")
    private boolean onlyClosureDependencies = false;

    @Option(name = "--closure_entry_point",
        usage = "Entry points to the program. Must be goog.provide'd "
        + "symbols. Any goog.provide'd symbols that are not a transitive "
        + "dependency of the entry points will be removed. Files without "
        + "goog.provides, and their dependencies, will always be left in. "
        + "If any entry points are specified, then the "
        + "manage_closure_dependencies option will be set to true and "
        + "all files will be sorted in dependency order.")
    private List<String> closureEntryPoint = new ArrayList<>();

    @Option(name = "--process_jquery_primitives",
        handler = BooleanOptionHandler.class,
        usage = "Processes built-ins from the Jquery library, such as "
        + "jQuery.fn and jQuery.extend()")
    private boolean processJqueryPrimitives = false;

    @Option(name = "--angular_pass",
        handler = BooleanOptionHandler.class,
        usage = "Generate $inject properties for AngularJS for functions "
        + "annotated with @ngInject")
    private boolean angularPass = false;

    @Option(name = "--output_manifest",
        usage = "Prints out a list of all the files in the compilation. "
        + "If --manage_closure_dependencies is on, this will not include "
        + "files that got dropped because they were not required. "
        + "The %outname% placeholder expands to the JS output file. "
        + "If you're using modularization, using %outname% will create "
        + "a manifest for each module.")
    private String outputManifest = "";

    @Option(name = "--output_module_dependencies",
        usage = "Prints out a JSON file of dependencies between modules.")
    private String outputModuleDependencies = "";

    @Option(name = "--accept_const_keyword",
        usage = "Allows usage of const keyword.")
    private boolean acceptConstKeyword = false;

    // TODO(tbreisacher): Add ES6 and ES6_STRICT to this usage string, once ES6
    // support is stable enough for people to start using it.
    @Option(name = "--language_in",
        usage = "Sets what language spec that input sources conform. "
        + "Options: ECMASCRIPT3 (default), ECMASCRIPT5, ECMASCRIPT5_STRICT")
    private String languageIn = "ECMASCRIPT3";

    @Option(name = "--language_out",
        usage = "Sets what language spec the output should conform to. "
        + " If omitted, defaults to the value of language_in. "
        + "Options: ECMASCRIPT3, ECMASCRIPT5, ECMASCRIPT5_STRICT")
    private String languageOut = "";

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
    private String flagFile = "";

    @Option(name = "--warnings_whitelist_file",
        usage = "A file containing warnings to suppress. Each line should be " +
            "of the form\n" +
            "<file-name>:<line-number>?  <warning-description>")
    private String warningsWhitelistFile = "";

    @Option(name = "--extra_annotation_name",
        usage = "A whitelist of tag names in JSDoc. You may specify multiple")
    private List<String> extraAnnotationName = new ArrayList<>();

    @Option(name = "--tracer_mode",
        usage = "Shows the duration of each compiler pass and the impact to " +
        "the compiled output size. Options: ALL, RAW_SIZE, TIMING_ONLY, OFF")
    private CompilerOptions.TracerMode tracerMode =
        CompilerOptions.TracerMode.OFF;

    @Option(name = "--new_type_inf",
        usage = "In development new type inference pass. DO NOT USE!")
    private boolean useNewTypeInference = false;

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
            "Bad value for --compilation_level: " + compilationLevel);
      }
    }

    private void printUsage(PrintStream err) {
      (new CmdLineParser(this)).printUsage(err);
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
    List<String> getJsFiles(PrintStream err) throws CmdLineException, IOException {
      final Set<String> allJsInputs = new LinkedHashSet<>();
      final List<String> matchedPaths = new ArrayList<>();
      List<String> patterns = new ArrayList<>();
      patterns.addAll(js);
      patterns.addAll(arguments);
      for (String pattern : patterns) {
        if (!pattern.contains("*") && !pattern.startsWith("!")) {
          allJsInputs.add(pattern);
        } else {
          FileSystem fs = FileSystems.getDefault();
          final boolean remove = pattern.indexOf("!") == 0;
          if (remove) pattern = pattern.substring(1);
          final PathMatcher matcher = fs.getPathMatcher("glob:" + pattern);
          java.nio.file.Files.walkFileTree(
              fs.getPath("."), new SimpleFileVisitor<Path>() {
              @Override public FileVisitResult visitFile(
                  Path p, BasicFileAttributes attrs) {
                if (matcher.matches(p)) {
                  if (remove) {
                    allJsInputs.remove(p.toString());
                  } else {
                    allJsInputs.add(p.toString());
                  }
                }
                matchedPaths.add(p.toString());
                return FileVisitResult.CONTINUE;
              }
          });
        }
      }

      if (!patterns.isEmpty() && allJsInputs.isEmpty()) {
        err.println("Paths attempted to match:");
        for (String path : matchedPaths) {
          err.println(path);
        }

        throw new CmdLineException("No inputs matched");
      }

      return new ArrayList<>(allJsInputs);
    }

    List<SourceMap.LocationMapping> getSourceMapLocationMappings() {
      List<SourceMap.LocationMapping> locationMappings =
          Lists.newArrayListWithCapacity(sourceMapLocationMapping.size());

      for (String locationMapping : sourceMapLocationMapping) {
        String[] pair = locationMapping.split("\\|", 2);
        locationMappings.add(new SourceMap.LocationMapping(pair[0], pair[1]));
      }

      return locationMappings;
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
  private static List<String> tokenizeKeepingQuotedStrings(List<String> lines) {
    List<String> tokens = new ArrayList<>();
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

  private void processFlagFile(PrintStream err)
            throws CmdLineException, IOException {
    Path flagFile = Paths.get(flags.flagFile);

    BufferedReader buffer =
      java.nio.file.Files.newBufferedReader(flagFile, UTF_8);
    // Builds the tokens.
    StringBuilder builder = new StringBuilder();
    // Stores the built tokens.
    List<String> tokens = new ArrayList<String>();
    // Indicates if we are in a "quoted" token.
    boolean quoted = false;
    // Indicates if the char being processed has been escaped.
    boolean escaped = false;

    int c;

    while ((c = buffer.read()) != -1) {
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
      err.println("ERROR - Arguments in the file cannot contain "
          + "--flagfile option.");
      isConfigValid = false;
    }
  }

  private void initConfigFromFlags(String[] args, PrintStream err) {

    List<String> processedArgs = processArgs(args);

    Flags.guardLevels.clear();
    isConfigValid = true;

    List<String> jsFiles = null;
    try {
      flags.parse(processedArgs);

      // For contains --flagfile flag
      if (!flags.flagFile.isEmpty()) {
        processFlagFile(err);
      }

      jsFiles = flags.getJsFiles(err);
    } catch (CmdLineException e) {
      err.println(e.getMessage());
      isConfigValid = false;
    } catch (IOException ioErr) {
      err.println("ERROR - " + flags.flagFile + " read error.");
      isConfigValid = false;
    }

    if (flags.version) {
      err.println(
          "Closure Compiler (http://code.google.com/closure/compiler)\n" +
          "Version: " + Compiler.getReleaseVersion() + "\n" +
          "Built on: " + Compiler.getReleaseDate());
      err.flush();
    }

    if (flags.processCommonJsModules) {
      flags.processClosurePrimitives = true;
      flags.manageClosureDependencies = true;
      if (flags.commonJsEntryModule == null) {
        err.println("Please specify --common_js_entry_module.");
        err.flush();
        isConfigValid = false;
      }
      flags.closureEntryPoint = Lists.newArrayList(
          ProcessCommonJSModules.toModuleName(flags.commonJsEntryModule));
    }

    if (flags.outputWrapper != null && !flags.outputWrapper.isEmpty() &&
        !flags.outputWrapper.contains(CommandLineRunner.OUTPUT_MARKER)) {
      err.println("ERROR - invalid output_wrapper specified. Missing '" +
          CommandLineRunner.OUTPUT_MARKER + "'.");
      isConfigValid = false;
    }

    if (!isConfigValid || flags.displayHelp) {
      isConfigValid = false;
      flags.printUsage(err);
    } else {
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
          .setCodingConvention(conv)
          .setSummaryDetailLevel(flags.summaryDetailLevel)
          .setOutputWrapper(flags.outputWrapper)
          .setModuleWrapper(flags.moduleWrapper)
          .setModuleOutputPathPrefix(flags.moduleOutputPathPrefix)
          .setCreateSourceMap(flags.createSourceMap)
          .setSourceMapFormat(flags.sourceMapFormat)
          .setSourceMapLocationMappings(flags.getSourceMapLocationMappings())
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
          .setCommonJSModulePathPrefix(flags.commonJsPathPrefix)
          .setTransformAMDToCJSModules(flags.transformAmdModules)
          .setWarningsWhitelistFile(flags.warningsWhitelistFile)
          .setAngularPass(flags.angularPass)
          .setTracerMode(flags.tracerMode)
          .setNewTypeInference(flags.useNewTypeInference);
    }
  }

  @Override
  protected CompilerOptions createOptions() {
    CompilerOptions options = new CompilerOptions();
    if (flags.processJqueryPrimitives) {
      options.setCodingConvention(new JqueryCodingConvention());
    } else {
      options.setCodingConvention(new ClosureCodingConvention());
    }

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

  // The externs expected in externs.zip, in sorted order.
  private static final List<String> DEFAULT_EXTERNS_NAMES = ImmutableList.of(
    // JS externs
    "es3.js",
    "es5.js",
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
    "v8.js",
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
    Map<String, SourceFile> externsMap = new HashMap<>();
    for (ZipEntry entry = null; (entry = zip.getNextEntry()) != null; ) {
      BufferedInputStream entryStream = new BufferedInputStream(
          ByteStreams.limit(zip, entry.getSize()));
      externsMap.put(entry.getName(),
          SourceFile.fromInputStream(
              // Give the files an odd prefix, so that they do not conflict
              // with the user's files.
              "externs.zip//" + entry.getName(),
              entryStream));
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
