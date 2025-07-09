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
import static java.util.Objects.requireNonNull;

import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.InlineMe;
import com.google.javascript.rhino.Node;
import java.io.Serializable;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Compile error description.
 *
 * @param type A type of the error.
 * @param description Description of the error.
 * @param sourceName Name of the source
 * @param lineno One-indexed line number of the error location.
 * @param charno Zero-indexed character number of the error location.
 * @param length Length of the error region.
 * @param node Node where the warning occurred.
 * @param defaultLevel The default level, before any of the {@code WarningsGuard}s are applied.
 * @param requirement Requirement that fails in the case of conformance violations.
 */
public record JSError(
    DiagnosticType type,
    String description,
    @Nullable String sourceName,
    int lineno,
    int charno,
    int length,
    @Nullable Node node,
    CheckLevel defaultLevel,
    @Nullable Requirement requirement)
    implements Serializable {
  public JSError {
    requireNonNull(type, "type");
    requireNonNull(description, "description");
    requireNonNull(defaultLevel, "defaultLevel");
  }

  @InlineMe(replacement = "this.type()")
  public DiagnosticType getType() {
    return type();
  }

  @InlineMe(replacement = "this.description()")
  public String getDescription() {
    return description();
  }

  @InlineMe(replacement = "this.sourceName()")
  public @Nullable String getSourceName() {
    return sourceName();
  }

  @InlineMe(replacement = "this.lineno()")
  public int getLineno() {
    return lineno();
  }

  @InlineMe(replacement = "this.charno()")
  public int getCharno() {
    return charno();
  }

  @InlineMe(replacement = "this.length()")
  public int getLength() {
    return length();
  }

  @InlineMe(replacement = "this.node()")
  public @Nullable Node getNode() {
    return node();
  }

  @InlineMe(replacement = "this.defaultLevel()")
  public CheckLevel getDefaultLevel() {
    return defaultLevel();
  }

  @InlineMe(replacement = "this.requirement()")
  public @Nullable Requirement getRequirement() {
    return requirement();
  }

  private static final int DEFAULT_LINENO = -1;
  private static final int DEFAULT_CHARNO = -1;
  private static final int DEFAULT_LENGTH = 0;
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
   * Creates a JSError from a file and Node position.
   *
   * @param start Determines the line and char position and source file name
   * @param end Determines the length of the error region
   * @param type The DiagnosticType
   * @param arguments Arguments to be incorporated into the message
   */
  public static JSError make(Node start, Node end, DiagnosticType type, String... arguments) {
    return builder(type, arguments).setNodeRange(start, end).build();
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
    private int length = DEFAULT_LENGTH;
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
      this.length = n.getLength();
      return this;
    }

    @CanIgnoreReturnValue
    Builder setNodeRange(Node start, Node end) {
      Preconditions.checkState(
          Objects.equals(DEFAULT_SOURCENAME, this.sourceName),
          "Cannot provide a Node when there's already a source name");
      this.n = start;
      this.sourceName = n.getSourceFileName();
      this.lineno = n.getLineno();
      this.charno = n.getCharno();
      int endOffset = end.getSourceOffset();
      int startOffset = start.getSourceOffset();
      if (endOffset != -1 && startOffset != -1) {
        this.length = (end.getSourceOffset() + end.getLength()) - start.getSourceOffset();
      }
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
      return new JSError(
          type, type.format(args), sourceName, lineno, charno, length, n, level, requirement);
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
        emptyToNull(this.sourceName()) != null ? this.sourceName() : "(unknown source)";
    String lineno =
        this.lineno() != DEFAULT_LINENO ? String.valueOf(this.lineno()) : "(unknown line)";
    String charno =
        this.charno() != DEFAULT_CHARNO ? String.valueOf(this.charno()) : "(unknown column)";

    return this.type().key
        + ". "
        + this.description()
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
    return switch (level) {
      case ERROR -> formatter.formatError(this);
      case WARNING -> formatter.formatWarning(this);
      default -> null;
    };
  }

  /** @return the offset of the region the Error applies to, or -1 if the offset is unknown. */
  public final int getNodeSourceOffset() {
    return this.node() != null ? this.node().getSourceOffset() : -1;
  }

  /** Alias for {@link #getLineno()}. */
  public final int getLineNumber() {
    return this.lineno();
  }

}
