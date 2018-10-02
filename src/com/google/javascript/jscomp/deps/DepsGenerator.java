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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Multiset;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.ErrorManager;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.JsAst;
import com.google.javascript.jscomp.LazyParsedDependencyInfo;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.deps.DependencyInfo.Require;
import com.google.javascript.jscomp.deps.ModuleLoader.ModulePath;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Generates deps.js files by scanning JavaScript files for
 * calls to goog.provide(), goog.require() and goog.addDependency().
 *
 * @author agrieve@google.com (Andrew Grieve)
 */
public class DepsGenerator {

  public static enum InclusionStrategy {
    ALWAYS,
    WHEN_IN_SRCS,
    DO_NOT_DUPLICATE
  }

  private static final Logger logger = Logger.getLogger(DepsGenerator.class.getName());

  // See the Flags in MakeJsDeps for descriptions of these.
  private final Collection<SourceFile> srcs;
  private final Collection<SourceFile> deps;
  private final String closurePathAbs;
  private final InclusionStrategy mergeStrategy;
  private final ModuleLoader loader;
  final ErrorManager errorManager;

  static final DiagnosticType ES6_IMPORT_FOR_NON_ES6_MODULE =
      DiagnosticType.warning(
          "DEPS_ES6_IMPORT_FOR_NON_ES6_MODULE",
          "Cannot import file \"{0}\" because it is not an ES6 module.");

  static final DiagnosticType UNKNOWN_PATH_IMPORT =
      DiagnosticType.warning("DEPS_UNKNOWN_PATH_IMPORT", "Could not find file \"{0}\".");

  static final DiagnosticType SAME_FILE_WARNING = DiagnosticType.warning(
      "DEPS_SAME_FILE",
      "Namespace \"{0}\" is both required and provided in the same file.");

  static final DiagnosticType NEVER_PROVIDED_ERROR = DiagnosticType.error(
      "DEPS_NEVER_PROVIDED",
      "Namespace \"{0}\" is required but never provided.");

  static final DiagnosticType DUPE_PROVIDES_WARNING = DiagnosticType.warning(
      "DEPS_DUPE_PROVIDES",
      "Multiple calls to goog.provide(\"{0}\")");

  static final DiagnosticType MULTIPLE_PROVIDES_ERROR = DiagnosticType.error(
      "DEPS_DUPE_PROVIDES",
      "Namespace \"{0}\" is already provided in other file {1}");

  static final DiagnosticType DUPE_REQUIRE_WARNING = DiagnosticType.warning(
      "DEPS_DUPE_REQUIRES",
      "Namespace \"{0}\" is required multiple times");

  static final DiagnosticType NO_DEPS_WARNING = DiagnosticType.warning(
      "DEPS_NO_DEPS",
      "No dependencies found in file");

  /**
   * Creates a new DepsGenerator.
   */
  public DepsGenerator(
      Collection<SourceFile> deps,
      Collection<SourceFile> srcs,
      InclusionStrategy mergeStrategy,
      String closurePathAbs,
      ErrorManager errorManager,
      ModuleLoader loader) {
    this.deps = deps;
    this.srcs = srcs;
    this.mergeStrategy = mergeStrategy;
    this.closurePathAbs = closurePathAbs;
    this.errorManager = errorManager;
    this.loader = loader;
  }

  /**
   * Performs the parsing inputs and writing of outputs.
   * @throws IOException Occurs upon an IO error.
   * @return Returns a String of goog.addDependency calls that will build
   *     the dependency graph. Returns null if there was an error.
   */
  public String computeDependencyCalls() throws IOException {
    // Build a map of closure-relative path -> DepInfo.
    Map<String, DependencyInfo> depsFiles = parseDepsFiles();
    if (logger.isLoggable(Level.FINE)) {
      logger.fine("preparsedFiles: " + depsFiles);
    }
    // Find all goog.provides & goog.requires in src files
    Map<String, DependencyInfo> jsFiles = parseSources(depsFiles.keySet());

    // Check if there were any parse errors.
    if (errorManager.getErrorCount() > 0) {
      return null;
    }

    cleanUpDuplicatedFiles(depsFiles, jsFiles);

    jsFiles = removeMungedSymbols(depsFiles, jsFiles);

    // Check for missing provides or other semantic inconsistencies.
    validateDependencies(depsFiles.values(), jsFiles.values());

    if (errorManager.getErrorCount() > 0) {
      return null;
    }

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    writeDepsContent(depsFiles, jsFiles, new PrintStream(output));
    return new String(output.toByteArray(), UTF_8);
  }

  /**
   * Removes duplicated depsInfo from jsFiles if this info already present in
   * some of the parsed deps.js
   *
   * @param depsFiles DepsInfo from deps.js dependencies
   * @param jsFiles DepsInfo from some of jsSources
   */
  protected void cleanUpDuplicatedFiles(Map<String, DependencyInfo> depsFiles,
      Map<String, DependencyInfo> jsFiles) {
    Set<String> depsPathsCopy = new HashSet<>(depsFiles.keySet());
    for (String path : depsPathsCopy) {
      if (mergeStrategy != InclusionStrategy.WHEN_IN_SRCS) {
        jsFiles.remove(path);
      }
    }

    for (String path : jsFiles.keySet()) {
      // If a generated file appears in both the jsFiles and in depsFiles, then
      // remove it from depsFiles in order to get the full path the generated
      // file.
      depsFiles.remove(path);
    }
  }

  /**
   * Removes munged symbols in requires and provides. These munged symbols are from ES6 modules
   * and are generated by {@link ModulePath#toModuleName()}. We do not wish to write these munged
   * symbols to the dependency file.
   *
   * <ul>
   *   <li>Makes any require'd munged symbol the require'd file's relative path to Closure.</li>
   *   <li>Removes munged symbols from the provides list.</li>
   * </ul>
   */
  private Map<String, DependencyInfo> removeMungedSymbols(
      Map<String, DependencyInfo> depFiles, Map<String, DependencyInfo> jsFiles) {
    Map<String, DependencyInfo> newJsFiles = new LinkedHashMap<>();
    Map<String, DependencyInfo> providesMap = new LinkedHashMap<>();
    addToProvideMap(depFiles.values(), providesMap, true);
    addToProvideMap(jsFiles.values(), providesMap, false);

    for (DependencyInfo dependencyInfo : jsFiles.values()) {
      ArrayList<Require> newRequires = new ArrayList<>();
      for (Require require : dependencyInfo.getRequires()) {
        if (require.getType() == Require.Type.ES6_IMPORT) {
          // Symbols are unique per file and have nothing to do with paths so map lookups are safe
          // here.
          DependencyInfo provider = providesMap.get(require.getSymbol());
          if (provider == null) {
            reportMissingFile(dependencyInfo, require.getRawText());
          } else {
            // If this is an ES6 module then set the symbol to be its relative path to Closure.
            // ES6 modules in a dependency file do not "provide" anything. Requires can match
            // a provided symbol or a relative path to Closure.
            newRequires.add(require.withSymbol(provider.getPathRelativeToClosureBase()));
          }
        } else {
          // Require is by symbol already so no need to change it.
          newRequires.add(require);
        }
      }

      ImmutableList<String> provides = dependencyInfo.getProvides();

      if ("es6".equals(dependencyInfo.getLoadFlags().get("module"))) {
        String mungedProvide = loader.resolve(dependencyInfo.getName()).toModuleName();
        // Filter out the munged symbol.
        // Note that at the moment ES6 modules should not have any other provides! In the future
        // we may have additional mechanisms to add goog symbols. But for not nothing is officially
        // supported.
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        for (String provide : provides) {
          if (!provide.equals(mungedProvide)
              && !provide.equals(dependencyInfo.getPathRelativeToClosureBase())) {
            builder.add(provide);
          }
        }
        provides = builder.build();
      }

      newJsFiles.put(
          dependencyInfo.getPathRelativeToClosureBase(),
          SimpleDependencyInfo.builder(
                  dependencyInfo.getPathRelativeToClosureBase(), dependencyInfo.getName())
              .setProvides(provides)
              .setRequires(newRequires)
              .setLoadFlags(dependencyInfo.getLoadFlags())
              .build());
    }

    return newJsFiles;
  }

  /**
   * Reports if there are any dependency problems with the given dependency
   * information. Reported problems include:
   *     - A namespace being provided more than once
   *     - A namespace being required multiple times from within one file
   *     - A namespace being provided and required in the same file
   *     - A namespace being required that is never provided
   * @param preparsedFileDependencies Dependency information from existing
   *     deps.js files.
   * @param parsedFileDependencies Dependency information from parsed .js files.
   */
  private void validateDependencies(Iterable<DependencyInfo> preparsedFileDependencies,
      Iterable<DependencyInfo> parsedFileDependencies) {
    // Create a map of namespace -> file providing it.
    // Also report any duplicate provides.
    Map<String, DependencyInfo> providesMap = new LinkedHashMap<>();
    addToProvideMap(preparsedFileDependencies, providesMap, true);
    addToProvideMap(parsedFileDependencies, providesMap, false);
    // For each require in the parsed sources:
    for (DependencyInfo depInfo : parsedFileDependencies) {
      Multiset<String> symbols = ImmutableMultiset.copyOf(depInfo.getRequiredSymbols());
      for (String symbol : symbols.elementSet()) {
        if (symbols.count(symbol) > 1) {
          reportDuplicateRequire(symbol, depInfo);
        }
      }

      for (Require require : depInfo.getRequires()) {
        String namespace = require.getSymbol();
        // Check for missing provides.
        DependencyInfo provider = providesMap.get(namespace);
        if (provider == null) {
          reportUndefinedNamespace(namespace, depInfo);
        } else if (provider == depInfo) {
          reportSameFile(namespace, depInfo);
        } else {
          depInfo.isModule();
          boolean providerIsEs6Module = "es6".equals(provider.getLoadFlags().get("module"));

          switch (require.getType()) {
            case ES6_IMPORT:
              if (!providerIsEs6Module) {
                reportEs6ImportForNonEs6Module(provider, depInfo);
              }
              break;
            case GOOG_REQUIRE_SYMBOL:
            case PARSED_FROM_DEPS:
              break;
            case COMMON_JS:
            case COMPILER_MODULE:
            default:
              throw new IllegalStateException("Unexpected import type: " + require.getType());
          }
        }
      }
    }
  }

  private void reportMissingFile(DependencyInfo depInfo, String path) {
    errorManager.report(
        CheckLevel.ERROR, JSError.make(depInfo.getName(), -1, -1, UNKNOWN_PATH_IMPORT, path));
  }

  private void reportEs6ImportForNonEs6Module(DependencyInfo provider, DependencyInfo depInfo) {
    errorManager.report(
        CheckLevel.ERROR,
        JSError.make(depInfo.getName(), -1, -1, ES6_IMPORT_FOR_NON_ES6_MODULE, provider.getName()));
  }

  private void reportSameFile(String namespace, DependencyInfo depInfo) {
    errorManager.report(CheckLevel.WARNING,
        JSError.make(depInfo.getName(), -1, -1,
            SAME_FILE_WARNING, namespace));
  }

  private void reportUndefinedNamespace(
      String namespace, DependencyInfo depInfo) {
    errorManager.report(CheckLevel.ERROR,
        JSError.make(depInfo.getName(), -1, -1,
            NEVER_PROVIDED_ERROR, namespace));
  }

  private void reportDuplicateProvide(String namespace, DependencyInfo firstDep,
      DependencyInfo secondDep) {
    if (firstDep == secondDep) {
      if (!firstDep.getPathRelativeToClosureBase().equals(namespace)) {
        errorManager.report(
            CheckLevel.WARNING,
            JSError.make(firstDep.getName(), -1, -1, DUPE_PROVIDES_WARNING, namespace));
      }
    } else {
      errorManager.report(CheckLevel.ERROR,
          JSError.make(secondDep.getName(), -1, -1,
              MULTIPLE_PROVIDES_ERROR, namespace, firstDep.getName()));
    }
  }

  private void reportDuplicateRequire(
      String namespace, DependencyInfo depInfo) {
    errorManager.report(CheckLevel.WARNING,
        JSError.make(depInfo.getName(), -1, -1,
            DUPE_REQUIRE_WARNING, namespace));
  }

  private void reportNoDepsInDepsFile(String filePath) {
    errorManager.report(CheckLevel.WARNING,
        JSError.make(filePath, -1, -1, NO_DEPS_WARNING));
  }

  /**
   * Adds the given DependencyInfos to the given providesMap. Also checks for and reports duplicate
   * provides.
   */
  private void addToProvideMap(
      Iterable<DependencyInfo> depInfos,
      Map<String, DependencyInfo> providesMap,
      boolean isFromDepsFile) {
    for (DependencyInfo depInfo : depInfos) {
      List<String> provides = new ArrayList<>(depInfo.getProvides());

      // Add a munged symbol to the provides map so that lookups by path requires work as intended.
      if (isFromDepsFile) {
        // Don't add the dependency file itself but every file it says exists instead.
        provides.add(
            loader
                .resolve(
                    PathUtil.makeAbsolute(depInfo.getPathRelativeToClosureBase(), closurePathAbs))
                .toModuleName());
      } else {
        // ES6 modules already provide these munged symbols.
        if (!"es6".equals(depInfo.getLoadFlags().get("module"))) {
          provides.add(loader.resolve(depInfo.getName()).toModuleName());
        }
      }

      // Also add the relative closure path as a provide. At some point we'll swap out the munged
      // symbols for these relative paths. So looks ups by either need to work.
      provides.add(depInfo.getPathRelativeToClosureBase());

      for (String provide : provides) {
        DependencyInfo prevValue = providesMap.put(provide, depInfo);
        // Check for duplicate provides.
        if (prevValue != null) {
          reportDuplicateProvide(provide, prevValue, depInfo);
        }
      }
    }
  }

  protected DepsFileParser createDepsFileParser() {
    DepsFileParser depsParser = new DepsFileParser(errorManager);
    depsParser.setShortcutMode(true);
    return depsParser;
  }

  /**
   * Returns whether we should ignore dependency info in the given deps file.
   */
  protected boolean shouldSkipDepsFile(SourceFile file) {
    return false;
  }

  /**
   * Parses all deps.js files in the deps list and creates a map of
   * closure-relative path -> DependencyInfo.
   */
  private Map<String, DependencyInfo> parseDepsFiles() throws IOException {
    DepsFileParser depsParser = createDepsFileParser();
    Map<String, DependencyInfo> depsFiles = new LinkedHashMap<>();
    for (SourceFile file : deps) {
      if (!shouldSkipDepsFile(file)) {
        List<DependencyInfo>
            depInfos = depsParser.parseFileReader(
                file.getName(), file.getCodeReader());
        if (depInfos.isEmpty()) {
          reportNoDepsInDepsFile(file.getName());
        } else {
          for (DependencyInfo info : depInfos) {
            depsFiles.put(info.getPathRelativeToClosureBase(), removeRelativePathProvide(info));
          }
        }
      }
    }

    // If a deps file also appears in srcs, our build tools will move it
    // into srcs.  So we need to scan all the src files for addDependency
    // calls as well.
    for (SourceFile src : srcs) {
      if (!shouldSkipDepsFile(src)) {
        List<DependencyInfo> srcInfos =
            depsParser.parseFileReader(src.getName(), src.getCodeReader());
        for (DependencyInfo info : srcInfos) {
          depsFiles.put(info.getPathRelativeToClosureBase(), removeRelativePathProvide(info));
        }
      }
    }

    return depsFiles;
  }

  private DependencyInfo removeRelativePathProvide(DependencyInfo info) {
    // DepsFileParser adds an ES6 module's relative path to closure as a provide so that
    // the resulting depgraph is valid. But we don't want to write this "fake" provide
    // back out, so remove it here.
    return SimpleDependencyInfo.Builder.from(info)
        .setProvides(
            info.getProvides().stream()
                .filter(p -> !p.equals(info.getPathRelativeToClosureBase()))
                .collect(Collectors.toList()))
        .build();
  }

  /**
   * Parses all source files for dependency information.
   * @param preparsedFiles A set of closure-relative paths.
   *     Files in this set are not parsed if they are encountered in srcs.
   * @return Returns a map of closure-relative paths -> DependencyInfo for the
   *     newly parsed files.
   * @throws IOException Occurs upon an IO error.
   */
  private Map<String, DependencyInfo> parseSources(
      Set<String> preparsedFiles) throws IOException {
    Map<String, DependencyInfo> parsedFiles = new LinkedHashMap<>();
    JsFileParser jsParser = new JsFileParser(errorManager).setModuleLoader(loader);
    Compiler compiler = new Compiler();
    compiler.init(ImmutableList.of(), ImmutableList.of(), new CompilerOptions());

    for (SourceFile file : srcs) {
      String closureRelativePath =
          PathUtil.makeRelative(
              closurePathAbs, PathUtil.makeAbsolute(file.getName()));
      if (logger.isLoggable(Level.FINE)) {
        logger.fine("Closure-relative path: " + closureRelativePath);
      }
      if (InclusionStrategy.WHEN_IN_SRCS == mergeStrategy ||
          !preparsedFiles.contains(closureRelativePath)) {
        DependencyInfo depInfo =
            jsParser.parseFile(
                file.getName(), closureRelativePath,
                file.getCode());
        depInfo = new LazyParsedDependencyInfo(depInfo, new JsAst(file), compiler);

        // Kick the source out of memory.
        file.clearCachedSource();
        parsedFiles.put(closureRelativePath, depInfo);
      }
    }

    return parsedFiles;
  }

  /**
   * Creates the content to put into the output deps.js file. If mergeDeps is
   * true, then all of the dependency information in the providedDeps will be
   * included in the output.
   * @throws IOException Occurs upon an IO error.
   */
  private void writeDepsContent(Map<String, DependencyInfo> depsFiles,
      Map<String, DependencyInfo> jsFiles, PrintStream out)
      throws IOException {
    // Print all dependencies extracted from srcs.
    writeDepInfos(out, jsFiles.values());

    // Print all dependencies extracted from deps.
    if (mergeStrategy == InclusionStrategy.ALWAYS) {
      // This multimap is just for splitting DepsInfo objects by
      // it's definition deps.js file
      Multimap<String, DependencyInfo> infosIndex =
          Multimaps.index(depsFiles.values(), DependencyInfo::getName);

      for (String depsPath : infosIndex.keySet()) {
        String path = formatPathToDepsFile(depsPath);
        out.println("\n// Included from: " + path);
        writeDepInfos(out, infosIndex.get(depsPath));
      }
    }
  }

  /**
   * Format the deps file path so that it can be included in the output file.
   */
  protected String formatPathToDepsFile(String path) {
    return path;
  }

  /** Writes goog.addDependency() lines for each DependencyInfo in depInfos. */
  private static void writeDepInfos(PrintStream out, Collection<DependencyInfo> depInfos)
      throws IOException {
    // Print dependencies.
    // Lines look like this:
    // goog.addDependency('../../path/to/file.js', ['goog.Delay'],
    //     ['goog.Disposable', 'goog.Timer']);
    for (DependencyInfo depInfo : depInfos) {
      DependencyInfo.Util.writeAddDependency(out, depInfo);
    }
  }

  static List<SourceFile> createSourceFilesFromPaths(
      Collection<String> paths) {
    List<SourceFile> files = new ArrayList<>();
    for (String path : paths) {
      files.add(SourceFile.fromFile(path));
    }
    return files;
  }

  static List<SourceFile> createSourceFilesFromPaths(String... paths) {
    return createSourceFilesFromPaths(Arrays.asList(paths));
  }

  static List<SourceFile> createSourceFilesFromZipPaths(
      Collection<String> paths) throws IOException {
    List<SourceFile> zipSourceFiles = new ArrayList<>();
    for (String path : paths) {
      zipSourceFiles.addAll(SourceFile.fromZipFile(path, UTF_8));
    }
    return zipSourceFiles;
  }
}
