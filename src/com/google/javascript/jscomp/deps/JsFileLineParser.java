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
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.ErrorManager;
import com.google.javascript.jscomp.JSError;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base class for classes that parse JavaScript sources on a line-by-line basis. Strips comments
 * from files and records all parsing errors.
 */
public abstract class JsFileLineParser {

  static final DiagnosticType PARSE_WARNING = DiagnosticType.warning(
      "DEPS_PARSE_WARNING", "{0}\n{1}");
  public static final DiagnosticType PARSE_ERROR = DiagnosticType.error(
      "DEPS_PARSE_ERROR", "{0}\n{1}");

  boolean shortcutMode = false;

  /**
   * Thrown by base classes to signify a problem parsing a line.
   */
  static class ParseException extends Exception {
    public static final long serialVersionUID = 1L;
    private final boolean fatal;

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
  private final Matcher valueMatcher = STRING_LITERAL_PATTERN.matcher("");

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
    boolean inJsDocComment = false;

    try {
      while (null != (line = lineBuffer.readLine())) {
        ++lineNum;
        try {
          String revisedLine = line;
          String revisedJsDocCommentLine = "";
          if (inMultilineComment) {
            int endOfComment = revisedLine.indexOf("*/");
            if (endOfComment != -1) {
              if (inJsDocComment) {
                revisedJsDocCommentLine = revisedLine.substring(0, endOfComment + 2);
                inJsDocComment = false;
              }
              revisedLine = revisedLine.substring(endOfComment + 2);
              inMultilineComment = false;
            } else {
              if (inJsDocComment) {
                revisedJsDocCommentLine = line;
              }
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
                // If comment is in a string (single or double quoted), don't parse as a comment.
                if (isCommentQuoted(revisedLine, startOfMultilineComment, '\'')) {
                  break;
                }
                if (isCommentQuoted(revisedLine, startOfMultilineComment, '"')) {
                  break;
                }
                if (startOfMultilineComment == revisedLine.indexOf("/**")) {
                  inJsDocComment = true;
                }
                int endOfMultilineComment = revisedLine.indexOf("*/", startOfMultilineComment + 2);
                if (endOfMultilineComment == -1) {
                  if (inJsDocComment) {
                    revisedJsDocCommentLine = revisedLine.substring(startOfMultilineComment);
                  }
                  revisedLine = revisedLine.substring(0, startOfMultilineComment);
                  inMultilineComment = true;
                  break;
                } else {
                  if (inJsDocComment) {
                    String jsDocComment =
                        revisedLine.substring(startOfMultilineComment, endOfMultilineComment + 2);
                    if (!parseJsDocCommentLine(jsDocComment) && shortcutMode) {
                      break;
                    }
                    inJsDocComment = false;
                  }
                  revisedLine =
                      revisedLine.substring(0, startOfMultilineComment) +
                      revisedLine.substring(endOfMultilineComment + 2);
                }
              } else {
                break;
              }
            }
          }

          if (!revisedJsDocCommentLine.isEmpty()) {
            if (!parseJsDocCommentLine(revisedJsDocCommentLine) && shortcutMode) {
              break;
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

  private boolean isCommentQuoted(String line, int startOfMultilineComment, char quoteChar) {
    int startQuoteIndex = line.indexOf(quoteChar);
    // Loop in case there are multiple strings between start of line and start of the comment.
    while (startQuoteIndex != -1 && startQuoteIndex < startOfMultilineComment) {
      int closingQuoteIndex = line.indexOf(quoteChar, startQuoteIndex + 1);
      if (closingQuoteIndex == -1) {
        return false;
      }
      ;
      // Skip escaped quotes
      while (isEscaped(line, closingQuoteIndex)) {
        closingQuoteIndex = line.indexOf(quoteChar, closingQuoteIndex + 1);
        if (closingQuoteIndex == -1) {
          return false;
        }
      }
      if (closingQuoteIndex > startOfMultilineComment) {
        return true;
      }
      startQuoteIndex = line.indexOf(quoteChar, closingQuoteIndex + 1);
    }
    return false;
  }

  private boolean isEscaped(String line, int closingQuoteIndex) {
    int escapeCharCount = 0;
    int index = closingQuoteIndex - 1;
    while (line.charAt(index) == '\\') {
      index--;
      escapeCharCount++;
    }
    // Odd number of backslashes means one is actually escaping the quote. Otherwise they are
    // literal backslashes which are being escaped by another backslash.
    return escapeCharCount % 2 == 1;
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
   * Called for each JSDoc line of the file being parsed.
   *
   * @param line The JSDoc comment line to parse.
   * @return true to keep going, false otherwise.
   */
  boolean parseJsDocCommentLine(String line) {
    return true;
  }

  /**
   * Parses a JS string literal.
   *
   * @param jsStringLiteral The literal. Must look like "asdf" or 'asdf'
   * @throws ParseException Thrown if there is a string literal that cannot be parsed.
   */
  String parseJsString(String jsStringLiteral) throws ParseException {
    valueMatcher.reset(jsStringLiteral);
    if (!valueMatcher.matches()) {
      throw new ParseException("Syntax error in JS String literal", /* fatal= */ true);
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
    List<String> results = new ArrayList<>();
    int indexStart = input.indexOf('[');
    int indexEnd = input.lastIndexOf(']');
    if ((indexStart == -1) || (indexEnd == -1)) {
      throw new ParseException("Syntax error when parsing JS array", /* fatal= */ true);
    }
    String innerValues = input.substring(indexStart + 1, indexEnd);

    if (!innerValues.trim().isEmpty()) {
      valueMatcher.reset(innerValues);
      for (;;) {
        // Parse the current string literal.
        if (!valueMatcher.lookingAt()) {
          throw new ParseException("Syntax error in JS String literal", /* fatal= */ true);
        }
        // Add it to the results.
        results.add(valueMatcher.group(1) != null ?
            valueMatcher.group(1) : valueMatcher.group(2));
        if (valueMatcher.hitEnd()) {
          break;
        }
        // Ensure there is a comma after the value.
        if (innerValues.charAt(valueMatcher.end()) != ',') {
          throw new ParseException("Missing comma in string array", /* fatal= */ true);
        }
        // Move to the next value.
        valueMatcher.region(valueMatcher.end() + 1, valueMatcher.regionEnd());
      }
    }
    return results;
  }

  // TODO(sdh): Consider simplifying this by reusing the parser or a separate JSON library.
  /**
   * Parses a JavaScript map of string literals. (eg: {'a': 'b', "c": "d"}).
   * @param input A string containing a JavaScript map of string literals.
   * @return A map of parsed string literals.
   * @throws ParseException Thrown if there is a syntax error with the input.
   */
  Map<String, String> parseJsStringMap(String input) throws ParseException {
    input = CharMatcher.whitespace().trimFrom(input);
    check(
        !input.isEmpty() && input.charAt(0) == '{' && input.charAt(input.length() - 1) == '}',
        "Syntax error when parsing JS object");
    input = input.substring(1, input.length() - 1).trim();

    Map<String, String> results = new LinkedHashMap<>();
    boolean done = input.isEmpty();
    valueMatcher.reset(input);
    while (!done) {
      // Parse the next key (TODO(sdh): need to support non-quoted keys?).
      check(valueMatcher.lookingAt(), "Bad key in JS object literal");
      String key = valueMatcher.group(1) != null ? valueMatcher.group(1) : valueMatcher.group(2);
      check(!valueMatcher.hitEnd(), "Missing value in JS object literal");
      check(input.charAt(valueMatcher.end()) == ':', "Missing colon in JS object literal");
      valueMatcher.region(valueMatcher.end() + 1, valueMatcher.regionEnd());

      // Parse the corresponding value.
      check(valueMatcher.lookingAt(), "Bad value in JS object literal");
      String val = valueMatcher.group(1) != null ? valueMatcher.group(1) : valueMatcher.group(2);
      results.put(key, val);
      if (!valueMatcher.hitEnd()) {
        check(input.charAt(valueMatcher.end()) == ',', "Missing comma in JS object literal");
        valueMatcher.region(valueMatcher.end() + 1, valueMatcher.regionEnd());
      } else {
        done = true;
      }
    }
    return results;
  }

  private static void check(boolean condition, String message) throws ParseException {
    if (!condition) {
      throw new ParseException(message, /* fatal= */ true);
    }
  }
}
