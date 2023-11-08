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
import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.javascript.rhino.Node;
import java.io.Serializable;
import java.util.Objects;
import org.jspecify.nullness.Nullable;

/** Compile error description. */
@AutoValue
public abstract class JSError implements Serializable {

  /** A type of the error. */
  public abstract DiagnosticType getType();

  /** Description of the error. */
  public abstract String getDescription();

  /** Name of the source */
  public abstract @Nullable String getSourceName();

  /** One-indexed line number of the error location. */
  public abstract int getLineno();

  /** Zero-indexed character number of the error location. */
  public abstract int getCharno();

  /** Node where the warning occurred. */
  public abstract @Nullable Node getNode();

  /** The default level, before any of the {@code WarningsGuard}s are applied. */
  public abstract CheckLevel getDefaultLevel();

  /** Requirement that fails in the case of conformance violations. */
  public abstract @Nullable Requirement getRequirement();

  private static final int DEFAULT_LINENO = -1;
  private static final int DEFAULT_CHARNO = -1;
  private static final @Nullable String DEFAULT_SOURCENAME = null;
  private static final @Nullable Node DEFAULT_NODE = null;
  private static final @Nullable Requirement DEFAULT_REQUIREMENT = null;

  /**
   * Creates a JSError with no source information
   *
   * @param type The DiagnosticType
   * @param arguments Arguments to be incorporated into the message
   */
  public static JSError make(DiagnosticType type, String... arguments) {
    return builder(type, arguments).build();
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
    return builder(type, arguments).setSourceLocation(sourceName, lineno, charno).build();
  }

  /**
   * Creates a JSError from a file and Node position.
   *
   * @param n Determines the line and char position and source file name
   * @param type The DiagnosticType
   * @param arguments Arguments to be incorporated into the message
   */
  public static JSError make(Node n, DiagnosticType type, String... arguments) {
    return builder(type, arguments).setNode(n).build();
  }

  /**
   * Creates a JSError from a requirement, file and Node position.
   *
   * @param requirement The conformance requirement that fails
   * @param n Determines the line and char position and source file name
   * @param type The DiagnosticType
   * @param arguments Arguments to be incorporated into the message
   */
  public static JSError make(
      Requirement requirement, Node n, DiagnosticType type, String... arguments) {
    return builder(type, arguments).setNode(n).setRequirement(requirement).build();
  }

  static final class Builder {
    private final DiagnosticType type;
    private final String[] args;

    private CheckLevel level;
    private Node n = DEFAULT_NODE;
    private String sourceName = DEFAULT_SOURCENAME;
    private int lineno = DEFAULT_LINENO;
    private int charno = DEFAULT_CHARNO;
    private Requirement requirement = DEFAULT_REQUIREMENT;

    private Builder(DiagnosticType type, String... args) {
      this.type = type;
      this.args = args;
      this.level = type.level; // may be overwritten later
    }

    /**
     * Sets the location where this error occurred.
     *
     * <p>Incompatible with {@link #setSourceLocation(String, int, int)}
     */
    @CanIgnoreReturnValue
    Builder setNode(Node n) {
      Preconditions.checkState(
          Objects.equals(DEFAULT_SOURCENAME, this.sourceName),
          "Cannot provide a Node when there's already a source name");
      this.n = n;
      this.sourceName = n.getSourceFileName();
      this.lineno = n.getLineno();
      this.charno = n.getCharno();
      return this;
    }

    /**
     * Sets the default level, before any WarningGuards, of this JSError. Overwrites the default
     * level of the DiagnosticType.
     *
     * <p>This method is rarely useful: prefer whenever possible to rely on the default level of the
     * DiagnosticType.
     */
    @CanIgnoreReturnValue
    Builder setLevel(CheckLevel level) {
      this.level = Preconditions.checkNotNull(level);
      return this;
    }

    /** Sets the requirement that fails in the case of conformance violations. */
    @CanIgnoreReturnValue
    Builder setRequirement(Requirement requirement) {
      this.requirement = Preconditions.checkNotNull(requirement);
      return this;
    }

    /**
     * Sets the location where this error occurred
     *
     * <p>Incompatible with {@link #setNode(Node)}
     *
     * @param sourceName The source file name
     * @param lineno Line number with source file, or -1 if unknown
     * @param charno Column number within line, or -1 for whole line.
     */
    @CanIgnoreReturnValue
    Builder setSourceLocation(String sourceName, int lineno, int charno) {
      Preconditions.checkState(
          this.n == DEFAULT_NODE, "Cannot provide a source location when there is already a Node");
      this.sourceName = sourceName;
      this.lineno = lineno;
      this.charno = charno;
      return this;
    }

    JSError build() {
      return new AutoValue_JSError(
          type, type.format(args), sourceName, lineno, charno, n, level, requirement);
    }
  }

  /**
   * Creates a new builder.
   *
   * @param type the associated DiagnosticType
   * @param arguments formatting arguments used to format the DiagnosticType's description
   */
  static Builder builder(DiagnosticType type, String... arguments) {
    return new Builder(type, arguments);
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
  public final @Nullable String format(CheckLevel level, MessageFormatter formatter) {
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
