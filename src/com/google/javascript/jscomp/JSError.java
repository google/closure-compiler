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

import static com.google.common.base.Strings.emptyToNull;

import com.google.auto.value.AutoValue;
import com.google.javascript.rhino.Node;
import java.io.Serializable;
import javax.annotation.Nullable;

/** Compile error description. */
@AutoValue
public abstract class JSError implements Serializable {

  /** A type of the error. */
  public abstract DiagnosticType getType();

  /** Description of the error. */
  public abstract String getDescription();

  /** Name of the source */
  @Nullable
  public abstract String getSourceName();

  /** One-indexed line number of the error location. */
  public abstract int getLineno();

  /** Zero-indexed character number of the error location. */
  public abstract int getCharno();

  /** Node where the warning occurred. */
  @Nullable
  public abstract Node getNode();

  /** The default level, before any of the {@code WarningsGuard}s are applied. */
  public abstract CheckLevel getDefaultLevel();

  private static final int DEFAULT_LINENO = -1;
  private static final int DEFAULT_CHARNO = -1;
  private static final String DEFAULT_SOURCENAME = null;
  private static final Node DEFAULT_NODE = null;

  /**
   * Creates a JSError with no source information
   *
   * @param type The DiagnosticType
   * @param arguments Arguments to be incorporated into the message
   */
  public static JSError make(DiagnosticType type, String... arguments) {
    return new AutoValue_JSError(
        type,
        type.format(arguments),
        DEFAULT_SOURCENAME,
        DEFAULT_LINENO,
        DEFAULT_CHARNO,
        DEFAULT_NODE,
        type.level);
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
  public static JSError make(
      String sourceName, int lineno, int charno, DiagnosticType type, String... arguments) {
    return new AutoValue_JSError(
        type, type.format(arguments), sourceName, lineno, charno, DEFAULT_NODE, type.level);
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
  public static JSError make(
      String sourceName,
      int lineno,
      int charno,
      CheckLevel level,
      DiagnosticType type,
      String... arguments) {
    return new AutoValue_JSError(
        type, type.format(arguments), sourceName, lineno, charno, DEFAULT_NODE, level);
  }

  /**
   * Creates a JSError from a file and Node position.
   *
   * @param n Determines the line and char position and source file name
   * @param type The DiagnosticType
   * @param arguments Arguments to be incorporated into the message
   */
  public static JSError make(Node n, DiagnosticType type, String... arguments) {
    return new AutoValue_JSError(
        type,
        type.format(arguments),
        n.getSourceFileName(),
        n.getLineno(),
        n.getCharno(),
        n,
        type.level);
  }

  /** Creates a JSError from a file and Node position. */
  public static JSError make(Node n, CheckLevel level, DiagnosticType type, String... arguments) {
    return new AutoValue_JSError(
        type,
        type.format(arguments),
        n.getSourceFileName(),
        n.getLineno(),
        n.getCharno(),
        n,
        level);
  }

  /** @return the default rendering of an error as text. */
  @Override
  public final String toString() {
    String sourceName =
        emptyToNull(this.getSourceName()) != null ? this.getSourceName() : "(unknown source)";
    String lineno =
        this.getLineno() != DEFAULT_LINENO ? String.valueOf(this.getLineno()) : "(unknown line)";
    String charno =
        this.getCharno() != DEFAULT_CHARNO ? String.valueOf(this.getCharno()) : "(unknown column)";

    return this.getType().key
        + ". "
        + this.getDescription()
        + " at "
        + sourceName
        + " line "
        + lineno
        + " : "
        + charno;
  }

  /**
   * Format a message at the given level.
   *
   * @return the formatted message or {@code null}
   */
  public final String format(CheckLevel level, MessageFormatter formatter) {
    switch (level) {
      case ERROR:
        return formatter.formatError(this);

      case WARNING:
        return formatter.formatWarning(this);

      default:
        return null;
    }
  }

  /** @return the offset of the region the Error applies to, or -1 if the offset is unknown. */
  public final int getNodeSourceOffset() {
    return this.getNode() != null ? this.getNode().getSourceOffset() : -1;
  }

  /** @return the length of the region the Error applies to, or 0 if the length is unknown. */
  public final int getNodeLength() {
    return this.getNode() != null ? this.getNode().getLength() : 0;
  }

  /** Alias for {@link #getLineno()}. */
  public final int getLineNumber() {
    return this.getLineno();
  }

  JSError() {
    // Package private.
  }
}
