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
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.ErrorManager;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parser that can extract goog.require() and goog.provide() dependency
 * information from a .js file.
 *
 * @author agrieve@google.com (Andrew Grieve)
 */
public class JsFileParser extends JsFileLineParser {

  private static Logger logger = Logger.getLogger(JsFileParser.class.getName());

  /** Pattern for matching goog.provide(*) and goog.require(*). */
  private static final Pattern GOOG_PROVIDE_REQUIRE_PATTERN = Pattern.compile(
      "(?:^|;)\\s*goog\\.(provide|require)\\s*\\((.*?)\\)");

  /** Matchers used in the parsing. */
  private Matcher googMatcher = GOOG_PROVIDE_REQUIRE_PATTERN.matcher("");

  /** The info for the file we are currently parsing. */
  private List<String> provides;
  private List<String> requires;

  /**
   * Constructor
   *
   * @param errorManager Handles parse errors.
   */
  public JsFileParser(ErrorManager errorManager) {
    super(errorManager);
  }

  /**
   * Parses the given file and returns the dependency information that it
   * contained.
   *
   * @param filePath Path to the file to parse.
   * @param closureRelativePath Path of the file relative to closure.
   * @return A DependencyInfo containing all provides/requires found in the
   *     file.
   * @throws IOException Thrown if there was an problem reading the given file.
   */
  public DependencyInfo parseFile(String filePath, String closureRelativePath)
      throws IOException {
    return parseReader(filePath, closureRelativePath, new FileReader(filePath));
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
    return parseReader(filePath, closureRelativePath,
        new StringReader(fileContents));
  }

  private DependencyInfo parseReader(String filePath,
      String closureRelativePath, Reader fileContents) {
    provides = Lists.newArrayList();
    requires = Lists.newArrayList();

    logger.fine("Parsing Source: " + filePath);
    doParse(filePath, fileContents);

    DependencyInfo dependencyInfo = new SimpleDependencyInfo(
        closureRelativePath, filePath, provides, requires);
    logger.fine("DepInfo: " + dependencyInfo);
    return dependencyInfo;
  }

  /**
   * Parses a line of javascript, extracting goog.provide and goog.require
   * information.
   */
  @Override
  protected boolean parseLine(String line) throws ParseException {
    boolean hasProvidesOrRequires = false;

    // Quick sanity check that will catch most cases. This is a performance
    // win for people with a lot of JS.
    if (line.indexOf("provide") != -1 ||
        line.indexOf("require") != -1) {
      // Iterate over the provides/requires.
      googMatcher.reset(line);
      while (googMatcher.find()) {
        hasProvidesOrRequires = true;

        // See if it's a require or provide.
        boolean isRequire = googMatcher.group(1).charAt(0) == 'r';
        // Parse the param.
        String arg = parseJsString(googMatcher.group(2));

        // Add the dependency.
        if (isRequire) {
          // goog is always implicit.
          // TODO(nicksantos): I'm pretty sure we don't need this anymore.
          // Remove this later.
          if (!"goog".equals(arg)) {
            requires.add(arg);
          }
        } else {
          provides.add(arg);
        }
      }
    }

    return !shortcutMode || hasProvidesOrRequires ||
        CharMatcher.WHITESPACE.matchesAllOf(line);
  }
}
