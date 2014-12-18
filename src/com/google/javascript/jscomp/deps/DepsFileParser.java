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

import com.google.common.base.CharMatcher;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.javascript.jscomp.ErrorManager;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parser that can extract dependency information from existing deps.js files.
 *
 * <p>See //javascript/closure/deps.js for an example file.</p>
 *
 * @author agrieve@google.com (Andrew Grieve)
 */
public class DepsFileParser extends JsFileLineParser {

  private static Logger logger = Logger.getLogger(DepsFileParser.class.getName());

  /**
   * Pattern for matching JavaScript string literals. The group is:
   * goog.addDependency({1});
   */
  private final Matcher depMatcher =
      Pattern.compile("\\s*goog.addDependency\\((.*)\\);?\\s*").matcher("");

  /**
   * Pattern for matching the args of a goog.addDependency(). The group is:
   * goog.addDependency({1}, {2}, {3});
   */
  private final Matcher depArgsMatch =
      Pattern.compile(
          "\\s*([^,]*), (\\[[^\\]]*\\]), (\\[[^\\]]*\\])(?:, (true|false))?\\s*").matcher("");

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
  public DepsFileParser(ErrorManager errorManager) {
    this(Functions.<String>identity(), errorManager);
  }

  /**
   * @param pathTranslator Translates paths in different build systems.
   * @param errorManager Handles parse errors.
   */
  public DepsFileParser(Function<String, String> pathTranslator,
      ErrorManager errorManager) {
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
    depInfos = Lists.newArrayList();
    logger.fine("Parsing Dep: " + filePath);
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

    // Quick sanity check that will catch most cases. This is a performance
    // win for people with a lot of JS.
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
        String path = pathTranslator.apply(parseJsString(depArgsMatch.group(1)));
        String moduleMatch = depArgsMatch.group(4);
        // Legacy deps files may not have a "isModule" parameter, those files are not modules.
        boolean isModule = moduleMatch == null ? false : parseJsBoolean(moduleMatch);

        DependencyInfo depInfo = new SimpleDependencyInfo(path, filePath,
            // Parse the provides.
            parseJsStringArray(depArgsMatch.group(2)),
            // Parse the requires.
            parseJsStringArray(depArgsMatch.group(3)),
            // "isModule"
            isModule
            );

        if (logger.isLoggable(Level.FINE)) {
          logger.fine("Found dep: " + depInfo);
        }
        depInfos.add(depInfo);
      }
    }

    return !shortcutMode || hasDependencies ||
        CharMatcher.WHITESPACE.matchesAllOf(line);
  }
}
