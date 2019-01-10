/*
 * Copyright 2008 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.deps;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.CharMatcher;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.ErrorManager;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.deps.DependencyInfo.Require;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parser that can extract dependency information from a .js file, including
 * goog.require, goog.provide, goog.module, import statements, and export statements.
 *
 * @author agrieve@google.com (Andrew Grieve)
 */
@GwtIncompatible("java.util.regex")
public final class JsFileParser extends JsFileLineParser {

  private static final Logger logger = Logger.getLogger(JsFileParser.class.getName());

  /** Pattern for matching goog.provide(*) and goog.require(*). */
  private static final Pattern GOOG_PROVIDE_REQUIRE_PATTERN =
      // TODO(sdh): this handles goog.loadModule(function(){"use strict";goog.module
      // but fails to match without "use strict"; since we look for semicolon, not open brace.
      Pattern.compile(
          "(?:^|;)(?:[a-zA-Z0-9$_,:{}\\s]+=)?\\s*"
              + "goog\\.(?<func>provide|module|require|requireType|addDependency|declareModuleId)"
              // TODO(johnplaisted): Remove declareNamespace.
              + "(?<subfunc>\\.declareNamespace)?\\s*\\((?<args>.*?)\\)");

  /**
   * Pattern for matching import ... from './path/to/file'.
   *
   * <p>Unlike the goog.require() pattern above, this pattern does not
   * allow multiple statements per line.  The import/export <b>must</b>
   * be at the beginning of the line to match.
   */
  private static final Pattern ES6_MODULE_PATTERN =
      Pattern.compile(
          // Require the import/export to be at the beginning of the line
          "^"
          // Either an import or export, but we don't care which, followed by at least one space
          + "(?:import|export)\\b\\s*"
          // Skip any identifier chars, as well as star, comma, braces, and spaces
          // This should match, e.g., "* as foo from ", or "Foo, {Bar as Baz} from ".
          // The 'from' keyword is required except in the case of "import '...';",
          // where there's nothing between 'import' and the module key string literal.
          + "(?:[a-zA-Z0-9$_*,{}\\s]+\\bfrom\\s*|)"
          // Imports require a string literal at the end; it's optional for exports
          // (e.g. "export * from './other';", which is effectively also an import).
          // This optionally captures group #1, which is the imported module name.
          + "(?:['\"]([^'\"]+)['\"])?"
          // Finally, this should be the entire statement, so ensure there's a semicolon.
          + "\\s*;");

  /**
   * Pattern for 'export' keyword, e.g. "export default class ..." or "export {blah}".
   * The '\b' ensures we don't also match "exports = ...", which is not an ES6 module.
   */
  private static final Pattern ES6_EXPORT_PATTERN = Pattern.compile("^export\\b");

  /** Line in comment indicating that the file is Closure's base.js. */
  private static final String PROVIDES_GOOG_COMMENT = "@provideGoog";

  /** The start of a bundled goog.module, i.e. one that is wrapped in a goog.loadModule call */
  private static final String BUNDLED_GOOG_MODULE_START = "goog.loadModule(function(";

  /** Matchers used in the parsing. */
  private final Matcher googMatcher = GOOG_PROVIDE_REQUIRE_PATTERN.matcher("");

  /** Matchers used in the parsing. */
  private final Matcher es6Matcher = ES6_MODULE_PATTERN.matcher("");

  /** The info for the file we are currently parsing. */
  private List<String> provides;
  private List<Require> requires;
  private List<String> typeRequires;
  private boolean fileHasProvidesOrRequires;
  private ModuleLoader loader = ModuleLoader.EMPTY;
  private ModuleLoader.ModulePath file;

  private enum ModuleType {
    NON_MODULE,
    GOOG_MODULE,
    GOOG_PROVIDE,
    ES6_MODULE,
  }

  private ModuleType moduleType;
  private boolean seenLoadModule = false;

  /** Whether to provide/require the root namespace. */
  private boolean includeGoogBase = false;

  /**
   * Constructor
   *
   * @param errorManager Handles parse errors.
   */
  public JsFileParser(ErrorManager errorManager) {
    super(errorManager);
  }

  /**
   * Sets whether we should create implicit provides and requires of the
   * root namespace.
   *
   * When generating deps files, you do not want this behavior. Deps files
   * need base.js to run anyway, so they don't need information about it.
   *
   * When generating abstract build graphs, you probably do want this behavior.
   * It will create an implicit dependency of all files with provides/requires
   * on base.js.
   *
   * @return this for easy chaining.
   */
  public JsFileParser setIncludeGoogBase(boolean include) {
    checkState(JsFileParser.isSupported());
    includeGoogBase = include;
    return this;
  }

  /**
   * Sets a list of "module root" URIs, which allow relativizing filenames
   * for modules.
   *
   * @return this for easy chaining.
   */
  public JsFileParser setModuleLoader(ModuleLoader loader) {
    this.loader = loader;
    return this;
  }

  /**
   * Parses the given file and returns the dependency information that it
   * contained.
   *
   * @param filePath Path to the file to parse.
   * @param closureRelativePath Path of the file relative to closure.
   * @param fileContents The contents to parse.
   * @return A DependencyInfo containing all provides/requires found in the
   *     file.
   */
  public DependencyInfo parseFile(String filePath, String closureRelativePath,
      String fileContents) {
    return parseReader(filePath, closureRelativePath, new StringReader(fileContents));
  }

  private DependencyInfo parseReader(String filePath,
      String closureRelativePath, Reader fileContents) {
    provides = new ArrayList<>();
    requires = new ArrayList<>();
    typeRequires = new ArrayList<>();
    fileHasProvidesOrRequires = false;
    file = loader.resolve(filePath);
    moduleType = ModuleType.NON_MODULE;
    seenLoadModule = false;

    if (logger.isLoggable(Level.FINE)) {
      logger.fine("Parsing Source: " + filePath);
    }
    doParse(filePath, fileContents);

    if (moduleType == ModuleType.ES6_MODULE) {
      provides.add(file.toModuleName());
    }

    Map<String, String> loadFlags = new LinkedHashMap<>();
    switch (moduleType) {
      case GOOG_MODULE:
        loadFlags.put("module", "goog");
        break;
      case ES6_MODULE:
        loadFlags.put("module", "es6");
        break;
      default:
        // Nothing to do here.
    }

    DependencyInfo dependencyInfo =
        SimpleDependencyInfo.builder(closureRelativePath, filePath)
            .setProvides(provides)
            .setRequires(requires)
            .setTypeRequires(typeRequires)
            .setLoadFlags(loadFlags)
            .build();
    if (logger.isLoggable(Level.FINE)) {
      logger.fine("DepInfo: " + dependencyInfo);
    }
    return dependencyInfo;
  }

  private void setModuleType(ModuleType type) {
    boolean provide = type == ModuleType.GOOG_PROVIDE || moduleType == ModuleType.GOOG_PROVIDE;
    boolean es6Module = type == ModuleType.ES6_MODULE || moduleType == ModuleType.ES6_MODULE;
    boolean googModule = type == ModuleType.GOOG_MODULE || moduleType == ModuleType.GOOG_MODULE;

    if (googModule && provide && seenLoadModule) {
      // We have to assume this is a top level goog.provide and a wrapped goog.loadModule. We can't
      // correctly validate this with just regular expressions.
      moduleType = ModuleType.GOOG_PROVIDE;
      return;
    }

    boolean provideGoogModuleConflict = googModule && provide && !seenLoadModule;
    boolean provideEs6ModuleConflict = es6Module && provide;
    // Don't allow nested goog modules in ES6 modules.
    boolean googEs6ModuleConflict = (googModule || seenLoadModule) && es6Module;

    if (provideGoogModuleConflict || provideEs6ModuleConflict || googEs6ModuleConflict) {
      // TODO(sdh): should this be an error?
      errorManager.report(
          CheckLevel.WARNING, JSError.make(ModuleLoader.MODULE_CONFLICT, file.toString()));
    }

    moduleType = type;
  }

  @Override
  protected boolean parseBlockCommentLine(String line) {
    if (includeGoogBase && line.contains(PROVIDES_GOOG_COMMENT)) {
      provides.add("goog");
      return false;
    }
    return true;
  }

  /** Parses a line of JavaScript, extracting goog.provide and goog.require information. */
  @Override
  protected boolean parseLine(String line) throws ParseException {
    boolean lineHasProvidesOrRequires = false;

    if (line.startsWith(BUNDLED_GOOG_MODULE_START)) {
      seenLoadModule = true;
    }

    // Quick check that will catch most cases. This is a performance win for teams with a lot of JS.
    if (line.contains("provide")
        || line.contains("require")
        || line.contains("module")
        || line.contains("addDependency")
        || line.contains("declareModuleId")) {
      // Iterate over the provides/requires.
      googMatcher.reset(line);
      while (googMatcher.find()) {
        lineHasProvidesOrRequires = true;

        if (includeGoogBase && !fileHasProvidesOrRequires) {
          fileHasProvidesOrRequires = true;
          requires.add(Require.BASE);
        }

        // See if it's a require or provide.
        String methodName = googMatcher.group("func");
        char firstChar = methodName.charAt(0);
        boolean isDeclareModuleNamespace =
            firstChar == 'd' || (firstChar == 'm' && googMatcher.group("subfunc") != null);
        boolean isModule = !isDeclareModuleNamespace && firstChar == 'm';
        boolean isProvide = firstChar == 'p';
        boolean providesNamespace = isProvide || isModule || isDeclareModuleNamespace;
        boolean isRequire = firstChar == 'r';

        if (isModule && !seenLoadModule) {
          setModuleType(ModuleType.GOOG_MODULE);
        }

        if (isProvide) {
          setModuleType(ModuleType.GOOG_PROVIDE);
        }

        if (providesNamespace || isRequire) {
          // Parse the param.
          String arg = parseJsString(googMatcher.group("args"));
          // Add the dependency.
          if (isRequire) {
            if ("requireType".equals(methodName)) {
              typeRequires.add(arg);
            } else if (!"goog".equals(arg)) {
              // goog is always implicit.
              Require require = Require.googRequireSymbol(arg);
              requires.add(require);
            }
          } else {
            provides.add(arg);
          }
        }
      }
    }

    if (line.startsWith("import") || line.startsWith("export")) {
      es6Matcher.reset(line);
      while (es6Matcher.find()) {
        setModuleType(ModuleType.ES6_MODULE);
        lineHasProvidesOrRequires = true;

        String arg = es6Matcher.group(1);
        if (arg != null) {
          if (arg.startsWith("goog:")) {
            // cut off the "goog:" prefix
            requires.add(Require.googRequireSymbol(arg.substring(5)));
          } else {
            ModuleLoader.ModulePath path = file.resolveJsModule(arg);
            if (path == null) {
              path = file.resolveModuleAsPath(arg);
            }
            requires.add(Require.es6Import(path.toModuleName(), arg));
          }
        }
      }

      // This check is only relevant for modules that don't import anything.
      if (moduleType != ModuleType.ES6_MODULE && ES6_EXPORT_PATTERN.matcher(line).lookingAt()) {
        setModuleType(ModuleType.ES6_MODULE);
      }
    }

    return !shortcutMode || lineHasProvidesOrRequires
        || CharMatcher.whitespace().matchesAllOf(line)
        || !line.contains(";")
        || line.contains("goog.setTestOnly")
        || line.contains("goog.module.declareLegacyNamespace");
  }

  public static boolean isSupported() {
    return true;
  }
}
