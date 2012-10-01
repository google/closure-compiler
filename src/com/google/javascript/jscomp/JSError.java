/*
 * Copyright 2004 The Closure Compiler Authors.
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

import com.google.javascript.rhino.Node;

import javax.annotation.Nullable;

/**
 * Compile error description
 *
 */
public class JSError {
  /** A type of the error */
  private final DiagnosticType type;

  /** Description of the error */
  public final String description;

  /** Name of the source */
  public final String sourceName;

  /** Node where the warning occurred. */
  final Node node;

  /** Line number of the source */
  public final int lineNumber;

  /** @deprecated Use #getDefaultLevel */
  @Deprecated
  public final CheckLevel level;

  private final CheckLevel defaultLevel;

  // character number
  private final int charno;

  //
  // JSError.make - static factory methods for creating JSError objects
  //
  //  The general form of the arguments is
  //
  //    [source location] [level] DiagnosticType [argument ...]
  //
  //  This order echos a typical command line diagnostic.  Source location
  //  arguments are arranged to be sources of information in the order
  //  file-line-column.
  //
  //  If the level is not given, it is taken from the level of the
  //  DiagnosticType.


  /**
   * Creates a JSError with no source information
   *
   * @param type The DiagnosticType
   * @param arguments Arguments to be incorporated into the message
   */
  public static JSError make(DiagnosticType type, String... arguments) {
    return new JSError(null, null, -1, -1, type, null, arguments);
  }

  /**
   * Creates a JSError at a given source location
   *
   * @param sourceName The source file name
   * @param lineno Line number with source file, or -1 if unknown
   * @param charno Column number within line, or -1 for whole line.
   * @param type The DiagnosticType
   * @param arguments Arguments to be incorporated into the message
   */
  public static JSError make(String sourceName, int lineno, int charno,
                             DiagnosticType type, String... arguments) {
    return new JSError(sourceName, null, lineno, charno, type, null, arguments);
  }

  /**
   * Creates a JSError at a given source location
   *
   * @param sourceName The source file name
   * @param lineno Line number with source file, or -1 if unknown
   * @param charno Column number within line, or -1 for whole line.
   * @param type The DiagnosticType
   * @param arguments Arguments to be incorporated into the message
   */
  public static JSError make(String sourceName, int lineno, int charno,
      CheckLevel level, DiagnosticType type, String... arguments) {
    return new JSError(
        sourceName, null, lineno, charno, type, level, arguments);
  }

  /**
   * Creates a JSError from a file and Node position.
   *
   * @param sourceName The source file name
   * @param n Determines the line and char position within the source file name
   * @param type The DiagnosticType
   * @param arguments Arguments to be incorporated into the message
   */
  public static JSError make(String sourceName, Node n,
                             DiagnosticType type, String... arguments) {
    return new JSError(sourceName, n, type, arguments);
  }

  /**
   * Creates a JSError from a file and Node position.
   *
   * @param n Determines the line and char position and source file name
   * @param type The DiagnosticType
   * @param arguments Arguments to be incorporated into the message
   */
  public static JSError make(Node n, DiagnosticType type, String... arguments) {
    return new JSError(n.getSourceFileName(), n, type, arguments);
  }

  /**
   * Creates a JSError from a file and Node position.
   *
   * @param sourceName The source file name
   * @param n Determines the line and char position within the source file name
   * @param type The DiagnosticType
   * @param arguments Arguments to be incorporated into the message
   */
  public static JSError make(String sourceName, Node n, CheckLevel level,
      DiagnosticType type, String... arguments) {

    return new JSError(sourceName, n, n.getLineno(), n.getCharno(), type, level,
        arguments);
  }

  //
  //  JSError constructors
  //

  /**
   * Creates a JSError at a CheckLevel for a source file location.
   * Private to avoid any entanglement with code outside of the compiler.
   */
  private JSError(
      String sourceName, @Nullable Node node, int lineno, int charno,
      DiagnosticType type, CheckLevel level, String... arguments) {
    this.type = type;
    this.node = node;
    this.description = type.format.format(arguments);
    this.lineNumber = lineno;
    this.charno = charno;
    this.sourceName = sourceName;
    this.defaultLevel = level == null ? type.level : level;
    this.level = level == null ? type.level : level;
  }

  /**
   * Creates a JSError for a source file location.  Private to avoid
   * any entanglement with code outside of the compiler.
   */
  private JSError(String sourceName, @Nullable Node node,
                  DiagnosticType type, String... arguments) {
    this(sourceName,
         node,
         (node != null) ? node.getLineno() : -1,
         (node != null) ? node.getCharno() : -1,
         type, null, arguments);
  }

  public DiagnosticType getType() {
    return type;
  }

  /**
   * Format a message at the given level.
   *
   * @return the formatted message or {@code null}
   */
  public String format(CheckLevel level, MessageFormatter formatter) {
    switch (level) {
      case ERROR:
        return formatter.formatError(this);

      case WARNING:
        return formatter.formatWarning(this);

      default:
        return null;
    }
  }

  @Override
  public String toString() {
    // TODO(user): remove custom toString.
    return type.key + ". " + description + " at " +
      (sourceName != null && sourceName.length() > 0 ?
       sourceName : "(unknown source)") + " line " +
      (lineNumber != -1 ? String.valueOf(lineNumber) : "(unknown line)") +
      " : " + (charno != -1 ? String.valueOf(charno) : "(unknown column)");
  }

  /**
   * Get the character number.
   */
  public int getCharno() {
    return charno;
  }

  /**
   * Get the line number. One-based.
   */
  public int getLineNumber() {
    return lineNumber;
  }

  /**
   * @return the offset of the region the Error applies to, or -1 if the offset
   *         is unknown.
   */
  public int getNodeSourceOffset() {
    return node != null ? node.getSourceOffset() : -1;
  }

  /**
   * @return the length of the region the Error applies to, or 0 if the length
   *         is unknown.
   */
  public int getNodeLength() {
    return node != null ? node.getLength() : 0;
  }

  /** The default level, before any of the WarningsGuards are applied. */
  public CheckLevel getDefaultLevel() {
    return defaultLevel;
  }

  @Override
  public boolean equals(Object o) {
    // Generated by Intellij IDEA
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    JSError jsError = (JSError) o;

    if (charno != jsError.charno) {
      return false;
    }
    if (lineNumber != jsError.lineNumber) {
      return false;
    }
    if (!description.equals(jsError.description)) {
      return false;
    }
    if (defaultLevel != jsError.defaultLevel) {
      return false;
    }
    if (sourceName != null ? !sourceName.equals(jsError.sourceName)
        : jsError.sourceName != null) {
      return false;
    }
    if (!type.equals(jsError.type)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    // Generated by Intellij IDEA
    int result = type.hashCode();
    result = 31 * result + description.hashCode();
    result = 31 * result + (sourceName != null ? sourceName.hashCode() : 0);
    result = 31 * result + lineNumber;
    result = 31 * result + defaultLevel.hashCode();
    result = 31 * result + charno;
    return result;
  }
}
