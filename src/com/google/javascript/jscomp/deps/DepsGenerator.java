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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.ErrorManager;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.JsAst;
import com.google.javascript.jscomp.LazyParsedDependencyInfo;
import com.google.javascript.jscomp.SourceFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

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

  private static Logger logger =
      Logger.getLogger(DepsGenerator.class.getName());

  // See the Flags in MakeJsDeps for descriptions of these.
  private final Collection<SourceFile> srcs;
  private final Collection<SourceFile> deps;
  private final String closurePathAbs;
  private final InclusionStrategy mergeStrategy;
  final ErrorManager errorManager;

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
      ErrorManager errorManager) {
    this.deps = deps;
    this.srcs = srcs;
    this.mergeStrategy = mergeStrategy;
    this.closurePathAbs = closurePathAbs;
    this.errorManager = errorManager;
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
    logger.fine("preparsedFiles: " + depsFiles);

    // Find all goog.provides & goog.requires in src files
    Map<String, DependencyInfo> jsFiles = parseSources(depsFiles.keySet());

    // Check if there were any parse errors.
    if (errorManager.getErrorCount() > 0) {
      return null;
    }

    cleanUpDuplicatedFiles(depsFiles, jsFiles);

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
   * Reports if there are any dependency problems with the given dependency
   * information. Reported problems include:
   *     - A namespace being provided more than once
   *     - A namespace being required multiple times from within one file
   *     - A namespace being provided and required in the same file
   *     - A namespace being required that is never provided
   * @param preparsedFileDepedencies Dependency information from existing
   *     deps.js files.
   * @param parsedFileDependencies Dependency information from parsed .js files.
   */
  private void validateDependencies(Iterable<DependencyInfo> preparsedFileDepedencies,
      Iterable<DependencyInfo> parsedFileDependencies) {
    // Create a map of namespace -> file providing it.
    // Also report any duplicate provides.
    Map<String, DependencyInfo> providesMap = new HashMap<>();
    addToProvideMap(preparsedFileDepedencies, providesMap);
    addToProvideMap(parsedFileDependencies, providesMap);
    // For each require in the parsed sources:
    for (DependencyInfo depInfo : parsedFileDependencies) {
      List<String> requires = new ArrayList<>(depInfo.getRequires());
      for (int i = 0, l = requires.size(); i < l; ++i) {
        String namespace = requires.get(i);
        // Check for multiple requires.
        if (requires.subList(i + 1, l).contains(namespace)) {
          reportDuplicateRequire(namespace, depInfo);
        }
        // Check for missing provides.
        DependencyInfo provider = providesMap.get(namespace);
        if (provider == null) {
          reportUndefinedNamespace(namespace, depInfo);
        } else if (provider == depInfo) {
          reportSameFile(namespace, depInfo);
        }
      }
    }
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
      errorManager.report(CheckLevel.WARNING,
          JSError.make(firstDep.getName(), -1, -1,
              DUPE_PROVIDES_WARNING, namespace));
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
   * Adds the given DependencyInfos to the given providesMap. Also checks for
   * and reports duplicate provides.
   */
  private void addToProvideMap(Iterable<DependencyInfo> depInfos,
      Map<String, DependencyInfo> providesMap) {
    for (DependencyInfo depInfo : depInfos) {
      for (String provide : depInfo.getProvides()) {
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
    Map<String, DependencyInfo> depsFiles = new HashMap<>();
    for (SourceFile file : deps) {
      if (!shouldSkipDepsFile(file)) {
        List<DependencyInfo>
            depInfos = depsParser.parseFileReader(
                file.getName(), file.getCodeReader());
        if (depInfos.isEmpty()) {
          reportNoDepsInDepsFile(file.getName());
        } else {
          for (DependencyInfo info : depInfos) {
            depsFiles.put(info.getPathRelativeToClosureBase(), info);
          }
        }
      }
    }

    // If a deps file also appears in srcs, our build tools will move it
    // into srcs.  So we need to scan all the src files for addDependency
    // calls as well.
    for (SourceFile src : srcs) {
      if ((new File(src.getName())).exists() &&
          !shouldSkipDepsFile(src)) {
        List<DependencyInfo> srcInfos =
            depsParser.parseFileReader(src.getName(), src.getCodeReader());
        for (DependencyInfo info : srcInfos) {
          depsFiles.put(info.getPathRelativeToClosureBase(), info);
        }
      }
    }

    return depsFiles;
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
    Map<String, DependencyInfo> parsedFiles = new HashMap<>();
    JsFileParser jsParser = new JsFileParser(errorManager);
    Compiler compiler = new Compiler();
    compiler.init(
        ImmutableList.<SourceFile>of(), ImmutableList.<SourceFile>of(), new CompilerOptions());

    for (SourceFile file : srcs) {
      String closureRelativePath =
          PathUtil.makeRelative(
              closurePathAbs, PathUtil.makeAbsolute(file.getName()));
      logger.fine("Closure-relative path: " + closureRelativePath);

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
      Multimap<String, DependencyInfo> infosIndex = Multimaps.index(
          depsFiles.values(),
          new Function<DependencyInfo, String>() {
            @Override
            public String apply(DependencyInfo from) {
              return from.getName();
            }
          });

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

  /**
   * Writes goog.addDependency() lines for each DependencyInfo in depInfos.
   */
  private void writeDepInfos(PrintStream out, Collection<DependencyInfo> depInfos)
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

  static List<SourceFile> createSourceFilesFromZipPaths(
      Collection<String> paths) throws IOException {
    List<SourceFile> zipSourceFiles = new ArrayList<>();
    for (String path : paths) {
      zipSourceFiles.addAll(SourceFile.fromZipFile(path, UTF_8));
    }
    return zipSourceFiles;
  }

  static List<SourceFile> createSourceFilesFromPaths(
      String ... paths) {
    return createSourceFilesFromPaths(Arrays.asList(paths));
  }
}
