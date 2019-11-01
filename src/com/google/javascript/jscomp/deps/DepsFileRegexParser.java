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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.CharMatcher;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.javascript.jscomp.ErrorManager;
import com.google.javascript.jscomp.deps.DependencyInfo.Require;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parser that can extract dependency information from existing deps.js files.
 *
 * <p>See //javascript/closure/deps.js for an example file.
 */
@GwtIncompatible("java.util.regex")
public final class DepsFileRegexParser extends JsFileLineParser {
  private static final Logger logger = Logger.getLogger(DepsFileRegexParser.class.getName());

  /**
   * Pattern for matching JavaScript string literals. The group is:
   * goog.addDependency({1});
   */
  private final Matcher depMatcher =
      Pattern.compile("\\s*goog.addDependency\\((.*)\\);?\\s*").matcher("");

  /**
   * Pattern for matching the args of a goog.addDependency(). The group is:
   * goog.addDependency({1}, {2}, {3}, {4?});
   */
  private final Matcher depArgsMatch =
      Pattern.compile(
              "\\s*([^,]*), (\\[[^\\]]*\\]), (\\[[^\\]]*\\])"
                  + "(?:, (true|false|\\{[^{}]*\\}))?\\s*")
          .matcher("");

  /**
   * The dependency information extracted from the current file.
   */
  private List<DependencyInfo> depInfos;

  /** Translates paths in different build systems. */
  private final Function<String, String> pathTranslator;

  /**
   * Constructor
   *
   * @param errorManager Handles parse errors.
   */
  public DepsFileRegexParser(ErrorManager errorManager) {
    this(Functions.identity(), errorManager);
  }

  /**
   * @param pathTranslator Translates paths in different build systems.
   * @param errorManager Handles parse errors.
   */
  public DepsFileRegexParser(Function<String, String> pathTranslator, ErrorManager errorManager) {
    super(errorManager);
    this.pathTranslator = pathTranslator;
  }

  /**
   * Parses the given file and returns a list of dependency information that it
   * contained.
   *
   * @param filePath Path to the file to parse.
   * @return A list of DependencyInfo objects.
   * @throws IOException Thrown if the file could not be read.
   */
  public List<DependencyInfo> parseFile(String filePath) throws IOException {
    return parseFileReader(filePath, Files.newReader(new File(filePath), StandardCharsets.UTF_8));
  }

  /**
   * Parses the given file and returns a list of dependency information that it
   * contained.
   * It uses the passed in fileContents instead of reading the file.
   *
   * @param filePath Path to the file to parse.
   * @param fileContents The contents to parse.
   * @return A list of DependencyInfo objects.
   */
  public List<DependencyInfo> parseFile(String filePath, String fileContents) {
    return parseFileReader(filePath, new StringReader(fileContents));
  }


  /**
   * Parses the file from the given reader and returns a list of
   * dependency information that it contained.
   *
   * @param filePath Path to the file to parse.
   * @param reader A reader for the file.
   * @return A list of DependencyInfo objects.
   */
  public List<DependencyInfo> parseFileReader(String filePath, Reader reader) {
    depInfos = new ArrayList<>();
    if (logger.isLoggable(Level.FINE)) {
      logger.fine("Parsing Dep: " + filePath);
    }
    doParse(filePath, reader);
    return depInfos;
  }

  /**
   * Extracts dependency information from lines that look like
   *   goog.addDependency('pathRelativeToClosure', ['provides'], ['requires']);
   * Adds the dependencies to depInfos.
   *
   * @throws ParseException Thrown if the given line has a malformed
   *     goog.addDependency().
   */
  @Override
  protected boolean parseLine(String line) throws ParseException {
    boolean hasDependencies = false;

    // Quick check that will catch most cases. This is a performance win for teams with a lot of JS.
    if (line.contains("addDependency")) {
      depMatcher.reset(line);
      // See if the line looks like: goog.addDependency(...)
      if (depMatcher.matches()) {
        hasDependencies = true;
        String addDependencyParams = depMatcher.group(1);
        depArgsMatch.reset(addDependencyParams);
        // Extract the three parameters.
        if (!depArgsMatch.matches()) {
          // Although we could recover, we mark this as fatal since there should
          // not be problems with generated deps.js files.
          throw new ParseException("Invalid arguments to goog.addDependency(). Found: "
              + addDependencyParams, true);
        }
        // Parse the file path.
        String relativePath = parseJsString(depArgsMatch.group(1));
        String path = pathTranslator.apply(relativePath);

        List<String> provides = parseJsStringArray(depArgsMatch.group(2));
        Map<String, String> loadFlags = parseLoadFlags(depArgsMatch.group(4));

        // ES6 modules are require'd by path but do not provide them in the addDependency call.
        if ("es6".equals(loadFlags.get("module"))) {
          provides.add(relativePath);
        }

        DependencyInfo depInfo =
            SimpleDependencyInfo.builder(path, filePath)
                .setProvides(provides)
                .setRequires(
                    parseJsStringArray(depArgsMatch.group(3))
                        .stream()
                        .map(Require::parsedFromDeps)
                        .collect(toImmutableList()))
                .setLoadFlags(loadFlags)
                .build();

        if (logger.isLoggable(Level.FINE)) {
          logger.fine("Found dep: " + depInfo);
        }
        depInfos.add(depInfo);
      }
    }

    return !shortcutMode || hasDependencies ||
        CharMatcher.whitespace().matchesAllOf(line);
  }

  private Map<String, String> parseLoadFlags(String loadFlags) throws ParseException {
    if (loadFlags == null || loadFlags.equals("false")) {
      return ImmutableMap.of();
    } else if (loadFlags.equals("true")) {
      return ImmutableMap.of("module", "goog");
    } else {
      return parseJsStringMap(loadFlags);
    }
  }
}
