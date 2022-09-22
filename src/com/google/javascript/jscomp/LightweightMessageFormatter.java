/*
 * Copyright 2007 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.SourceExcerptProvider.SourceExcerpt.FULL;
import static com.google.javascript.jscomp.SourceExcerptProvider.SourceExcerpt.LINE;
import static java.lang.Math.max;
import static java.lang.Math.min;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.debugging.sourcemap.proto.Mapping.OriginalMapping;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.javascript.jscomp.SourceExcerptProvider.ExcerptFormatter;
import com.google.javascript.jscomp.SourceExcerptProvider.SourceExcerpt;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.TokenUtil;
import java.util.List;
import org.jspecify.nullness.Nullable;

/**
 * Lightweight message formatter. The format of messages this formatter
 * produces is very compact and to the point.
 */
public final class LightweightMessageFormatter extends AbstractMessageFormatter {
  private final SourceExcerpt defaultFormat;
  private static final ExcerptFormatter excerptFormatter =
      new LineNumberingFormatter();
  private boolean includeLocation = true;
  private boolean includeLevel = true;
  private static final int MAX_MULTILINE_ERROR_LENGTH = 4; // must be even.

  /**
   * A constructor for when the client doesn't care about source information.
   */
  private LightweightMessageFormatter() {
    super(null);
    this.defaultFormat = LINE;
  }

  public LightweightMessageFormatter(SourceExcerptProvider source) {
    this(source, LINE);
  }

  public LightweightMessageFormatter(SourceExcerptProvider source, SourceExcerpt defaultFormat) {
    super(source);
    checkNotNull(source);
    this.defaultFormat = defaultFormat;
  }

  public static LightweightMessageFormatter withoutSource() {
    return new LightweightMessageFormatter();
  }

  @CanIgnoreReturnValue
  public LightweightMessageFormatter setIncludeLocation(boolean includeLocation) {
    this.includeLocation = includeLocation;
    return this;
  }

  @CanIgnoreReturnValue
  public LightweightMessageFormatter setIncludeLevel(boolean includeLevel) {
    this.includeLevel = includeLevel;
    return this;
  }

  @Override
  public String formatError(JSError error) {
    return format(error, false);
  }

  @Override
  public String formatWarning(JSError warning) {
    return format(warning, true);
  }

  private String format(JSError error, boolean warning) {
    SourceExcerptProvider source = getSource();
    String sourceName = error.getSourceName();
    int lineNumber = error.getLineNumber();
    int charno = error.getCharno();

    // Format the non-reverse-mapped position.
    StringBuilder b = new StringBuilder();
    StringBuilder boldLine = new StringBuilder();

    OriginalMapping mapping =
        source == null
            ? null
            : source.getSourceMapping(
                error.getSourceName(), error.getLineNumber(), error.getCharno());

    // Check if we can reverse-map the source.
    if (includeLocation) {
      if (mapping != null) {
        appendPosition(b, sourceName, lineNumber, charno);

        sourceName = mapping.getOriginalFile();
        lineNumber = mapping.getLineNumber();
        charno = mapping.getColumnPosition();

        b.append("\nOriginally at:\n");
      }

      appendPosition(boldLine, sourceName, lineNumber, charno);
    }

    if (includeLevel) {
      boldLine.append(getLevelName(warning ? CheckLevel.WARNING : CheckLevel.ERROR));
      boldLine.append(" - [");
      boldLine.append(error.getType().key);
      boldLine.append("] ");
    }

    boldLine.append(error.getDescription());

    b.append(maybeEmbolden(boldLine.toString()));
    b.append('\n');

    // For reverse-mapped sources, fall back to a single line excerpt because the excerpt length
    // cannot be reliably mapped.
    String sourceExcerptWithPosition =
        getExcerptWithPosition(
            error, sourceName, lineNumber, charno, mapping != null ? LINE : defaultFormat);

    if (sourceExcerptWithPosition != null) {
      b.append(sourceExcerptWithPosition);
    }

    return b.toString();
  }

  String getExcerptWithPosition(JSError error) {
    return getExcerptWithPosition(
        error, error.getSourceName(), error.getLineNumber(), error.getCharno(), defaultFormat);
  }

  private String getExcerptWithPosition(
      JSError error, String sourceName, int lineNumber, int charno, SourceExcerpt format) {
    StringBuilder b = new StringBuilder();

    SourceExcerptProvider source = getSource();
    int nodeLength = error.getNodeLength();
    int length = charno >= 0 && nodeLength >= 0 ? charno + nodeLength : -1;

    String sourceExcerpt =
        source == null
            ? null
            : format.get(source, sourceName, lineNumber, length, excerptFormatter);

    if (sourceExcerpt != null) {
      if (format.equals(FULL)) {
        if (0 <= charno) {
          padMultipleLines(error, charno, sourceExcerpt, b, error.getNode());
        } else {
          b.append(sourceExcerpt);
          b.append('\n');
        }
      } else {
        b.append(sourceExcerpt);
        b.append('\n');

        // charno == sourceExcerpt.length() means something is missing
        // at the end of the line
        if (format.equals(LINE) && 0 <= charno && charno <= sourceExcerpt.length()) {
          padLine(charno, sourceExcerpt, b, error.getNodeLength(), error.getNode());
        }
      }
    }
    return b.toString();
  }

  private static void appendPosition(
      StringBuilder b, String sourceName, int lineNumber, int charno) {
    if (sourceName != null) {
      b.append(sourceName);
      if (lineNumber > 0) {
        b.append(':').append(lineNumber);
        if (charno >= 0) {
          b.append(':').append(charno);
        }
      }
      b.append(": ");
    }
  }

  private void padLine(
      int charno, String sourceExcerpt, StringBuilder b, int errLength, Node errorNode) {
    // Append leading whitespace
    for (int i = 0; i < charno; i++) {
      char c = sourceExcerpt.charAt(i);
      if (TokenUtil.isWhitespace(c)) {
        b.append(c);
      } else {
        b.append(' ');
      }
    }
    if (errorNode == null) {
      b.append("^");
    } else {
      int length = max(1, min(errLength, sourceExcerpt.length() - charno));
      for (int i = 0; i < length; i++) {
        b.append("^");
      }
    }
    b.append("\n");
  }

  /**
   * Appends lines from the given excerpt, attempting to add "^^^^" highlighting to the parts of the
   * excerpt covered by the given error node.
   *
   * @param startCharno the (positive) charno representing the index into the first line.
   * @param sourceExcerpt the original source, possibly multiple lines separated by '\n'.
   */
  private void padMultipleLines(
      JSError error, int startCharno, String sourceExcerpt, StringBuilder b, Node errorNode) {
    if (errorNode == null) {
      b.append(sourceExcerpt);
      b.append("\n");
      int charWithLineNumberOffset = startCharno + sourceExcerpt.indexOf('|') + 2;
      checkState(
          charWithLineNumberOffset <= sourceExcerpt.length(),
          "Cannot format source excerpt; unexpected start character for error:\n %s",
          error);
      padLine(charWithLineNumberOffset, sourceExcerpt, b, -1, errorNode);
      return;
    }
    List<String> lines = Splitter.on('\n').splitToList(sourceExcerpt);
    boolean requiresTruncation = lines.size() > MAX_MULTILINE_ERROR_LENGTH;
    int truncationStart = MAX_MULTILINE_ERROR_LENGTH / 2;
    int truncationEnd = lines.size() - MAX_MULTILINE_ERROR_LENGTH / 2;

    int remainingLength = errorNode.getLength();
    int charno = startCharno;
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      if (requiresTruncation && i == truncationStart) {
        b.append("...\n");
      }
      boolean shouldPrintLine = !requiresTruncation || i < truncationStart || i >= truncationEnd;
      // The LineNumberingFormatter below will append "  5| " to the start of each line, so subtract
      // that from the source line length in order to match the actual error length.
      int charWithLineNumberOffset = charno + line.indexOf('|') + 2;

      if (shouldPrintLine) {
        b.append(line);
        b.append("\n");
        checkState(
            charWithLineNumberOffset <= sourceExcerpt.length(),
            "Cannot format source excerpt; unexpected start character for error\n%s",
            error);
        padLine(charWithLineNumberOffset, line, b, remainingLength, errorNode);
      }

      // add 1 to represent the newline; subtract the offset of the "  5| ".
      remainingLength -= (line.length() + 1 - charWithLineNumberOffset);
      charno = 0;
    }
  }

  /**
   * Formats a region by appending line numbers in front, e.g.
   *
   * <pre>
   *    9| if (foo) {
   *   10|   alert('bar');
   *   11| }
   * </pre>
   *
   * and return line excerpt without any modification.
   */
  static class LineNumberingFormatter implements ExcerptFormatter {
    @Override
    public String formatLine(String line, int lineNumber) {
      return line;
    }

    @Override
    public @Nullable String formatRegion(@Nullable Region region) {
      if (region == null) {
        return null;
      }
      String code = region.getSourceExcerpt();
      if (code.isEmpty()) {
        return null;
      }

      // max length of the number display
      int numberLength = Integer.toString(region.getEndingLineNumber())
          .length();

      // formatting
      StringBuilder builder = new StringBuilder(code.length() * 2);
      int start = 0;
      int end = code.indexOf('\n', start);
      int lineNumber = region.getBeginningLineNumber();
      while (start >= 0) {
        // line extraction
        String line;
        if (end < 0) {
          line = code.substring(start);
          if (line.isEmpty()) {
            return builder.substring(0, builder.length() - 1);
          }
        } else {
          line = code.substring(start, end);
        }
        builder.append("  ");

        // nice spaces for the line number
        int spaces = numberLength - Integer.toString(lineNumber).length();
        builder.append(Strings.repeat(" ", spaces));
        builder.append(lineNumber);
        builder.append("| ");

        // end & update
        if (end < 0) {
          builder.append(line);
          start = -1;
        } else {
          builder.append(line);
          builder.append('\n');
          start = end + 1;
          end = code.indexOf('\n', start);
          lineNumber++;
        }
      }
      return builder.toString();
    }
  }
}
