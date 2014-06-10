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

import com.google.common.collect.Lists;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.ErrorManager;
import com.google.javascript.jscomp.JSError;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base class for classes that parse JavaScript sources on a line-by-line basis. Strips comments
 * from files and records all parsing errors.
 *
 * @author agrieve@google.com (Andrew Grieve)
 */
public abstract class JsFileLineParser {

  static final DiagnosticType PARSE_WARNING = DiagnosticType.warning(
      "DEPS_PARSE_WARNING", "{0}\n{1}");
  static final DiagnosticType PARSE_ERROR = DiagnosticType.error(
      "DEPS_PARSE_ERROR", "{0}\n{1}");

  boolean shortcutMode = false;

  /**
   * Thrown by base classes to signify a problem parsing a line.
   */
  static class ParseException extends Exception {
    public static final long serialVersionUID = 1L;
    private boolean fatal;

    /**
     * Constructor.
     *
     * @param message A description of what caused the exception.
     * @param fatal Whether the exception is recoverable.
     */
    public ParseException(String message, boolean fatal) {
      super(message);
      this.fatal = fatal;
    }

    public boolean isFatal() {
      return fatal;
    }
  }

  /** Pattern for matching JavaScript string literals. */
  private static final Pattern STRING_LITERAL_PATTERN = Pattern.compile(
      "\\s*(?:'((?:\\\\'|[^'])*?)'|\"((?:\\\\\"|[^\"])*?)\")\\s*");

  /** Matcher used in the parsing string literals. */
  private Matcher valueMatcher = STRING_LITERAL_PATTERN.matcher("");

  /** Path of the file currently being parsed. */
  String filePath;
  /** The line number of the line currently being parsed. */
  int lineNum;
  /** Handles error messages. */
  ErrorManager errorManager;
  /** Did our parse succeed. */
  boolean parseSucceeded;

  /**
   * Constructor.
   *
   * @param errorManager Parse error handler.
   */
  public JsFileLineParser(ErrorManager errorManager) {
    this.errorManager = errorManager;
  }

  /**
   * In shortcut mode, the file line parser can stop reading early if
   * it thinks it found enough information.
   *
   * For example, many parsers assume that dependency information never
   * shows up after "real" code.
   */
  public void setShortcutMode(boolean mode) {
    this.shortcutMode = mode;
  }

  public boolean didParseSucceed() {
    return parseSucceeded;
  }

  /**
   * Performs the line-by-line parsing of the given fileContents. This method
   * strips out JavaScript comments and then uses the abstract parseLine()
   * method to do the line parsing.
   *
   * @param filePath The path to the file being parsed. Used for reporting parse
   *     exceptions.
   * @param fileContents A reader for the contents of the file.
   */
  void doParse(String filePath, Reader fileContents) {
    this.filePath = filePath;
    parseSucceeded = true;

    BufferedReader lineBuffer = new BufferedReader(fileContents);

    // Parse all lines.
    String line = null;
    lineNum = 0;
    boolean inMultilineComment = false;

    try {
      while (null != (line = lineBuffer.readLine())) {
        ++lineNum;
        try {
          String revisedLine = line;
          if (inMultilineComment) {
            int endOfComment = revisedLine.indexOf("*/");
            if (endOfComment != -1) {
              revisedLine = revisedLine.substring(endOfComment + 2);
              inMultilineComment = false;
            } else {
              revisedLine = "";
            }
          }

          if (!inMultilineComment) {
            while (true) {
              int startOfLineComment = revisedLine.indexOf("//");
              int startOfMultilineComment = revisedLine.indexOf("/*");
              if (startOfLineComment != -1 &&
                  (startOfMultilineComment == -1 ||
                   startOfLineComment < startOfMultilineComment)) {
                revisedLine = revisedLine.substring(0, startOfLineComment);
                break;
              } else if (startOfMultilineComment != -1) {
                int endOfMultilineComment = revisedLine.indexOf("*/",
                    startOfMultilineComment + 2);
                if (endOfMultilineComment == -1) {
                  revisedLine = revisedLine.substring(
                      0, startOfMultilineComment);
                  inMultilineComment = true;
                  break;
                } else {
                  revisedLine =
                      revisedLine.substring(0, startOfMultilineComment) +
                      revisedLine.substring(endOfMultilineComment + 2);
                }
              } else {
                break;
              }
            }
          }

          if (!revisedLine.isEmpty()) {
            // This check for shortcut mode should be redundant, but
            // it's done for safety reasons.
            if (!parseLine(revisedLine) && shortcutMode) {
              break;
            }
          }
        } catch (ParseException e) {
          // Inform the error handler of the exception.
          errorManager.report(
              e.isFatal() ? CheckLevel.ERROR : CheckLevel.WARNING,
              JSError.make(filePath, lineNum, 0 /* char offset */,
                  e.isFatal() ? PARSE_ERROR : PARSE_WARNING,
                  e.getMessage(), line));
          parseSucceeded = parseSucceeded && !e.isFatal();
        }
      }
    } catch (IOException e) {
      errorManager.report(CheckLevel.ERROR,
          JSError.make(filePath, 0, 0 /* char offset */,
              PARSE_ERROR, "Error reading file: " + filePath));
      parseSucceeded = false;
    }
  }

  /**
   * Called for each line of the file being parsed.
   *
   * @param line The line to parse.
   * @return true to keep going, false otherwise.
   * @throws ParseException Should be thrown to signify a problem with the line.
   */
  abstract boolean parseLine(String line) throws ParseException;

  /**
   * Parses a JS string literal.
   *
   * @param jsStringLiteral The literal. Must look like "asdf" or 'asdf'
   * @throws ParseException Thrown if there is a string literal that cannot be
   *     parsed.
   */
  String parseJsString(String jsStringLiteral) throws ParseException {
    valueMatcher.reset(jsStringLiteral);
    if (!valueMatcher.matches()) {
      throw new ParseException("Syntax error in JS String literal", true /* fatal */);
    }
    return valueMatcher.group(1) != null ? valueMatcher.group(1) : valueMatcher.group(2);
  }

  /**
   * Parses a JavaScript array of string literals. (eg: ['a', 'b', "c"]).
   * @param input A string containing a JavaScript array of string literals.
   * @return A list of parsed string literals.
   * @throws ParseException Thrown if there is a syntax error with the input.
   */
  List<String> parseJsStringArray(String input)
      throws ParseException {
    List<String> results = Lists.newArrayList();
    int indexStart = input.indexOf('[');
    int indexEnd = input.lastIndexOf(']');
    if ((indexStart == -1) || (indexEnd == -1)) {
      throw new ParseException("Syntax error when parsing JS array", true /* fatal */);
    }
    String innerValues = input.substring(indexStart + 1, indexEnd);

    if (!innerValues.trim().isEmpty()) {
      valueMatcher.reset(innerValues);
      for (;;) {
        // Parse the current string literal.
        if (!valueMatcher.lookingAt()) {
          throw new ParseException("Syntax error in JS String literal", true /* fatal */);
        }
        // Add it to the results.
        results.add(valueMatcher.group(1) != null ?
            valueMatcher.group(1) : valueMatcher.group(2));
        if (valueMatcher.hitEnd()) {
          break;
        }
        // Ensure there is a comma after the value.
        if (innerValues.charAt(valueMatcher.end()) != ',') {
          throw new ParseException("Missing comma in string array", true /* fatal */);
        }
        // Move to the next value.
        valueMatcher.region(valueMatcher.end() + 1, valueMatcher.regionEnd());
      }
    }
    return results;
  }

  boolean parseJsBoolean(String jsBoolean) throws ParseException {
    if (jsBoolean.equals("true")) {
      return true;
    } else if (jsBoolean.equals("false")) {
      return false;
    }
    throw new ParseException("Syntax error in JS String literal", true /* fatal */);
  }
}
