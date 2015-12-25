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

import static com.google.javascript.jscomp.SourceExcerptProvider.SourceExcerpt.LINE;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.debugging.sourcemap.proto.Mapping.OriginalMapping;
import com.google.javascript.jscomp.SourceExcerptProvider.ExcerptFormatter;
import com.google.javascript.jscomp.SourceExcerptProvider.SourceExcerpt;
import com.google.javascript.rhino.TokenUtil;

/**
 * Lightweight message formatter. The format of messages this formatter
 * produces is very compact and to the point.
 *
 */
public final class LightweightMessageFormatter extends AbstractMessageFormatter {
  private SourceExcerpt excerpt;
  private static final ExcerptFormatter excerptFormatter =
      new LineNumberingFormatter();

  /**
   * A constructor for when the client doesn't care about source information.
   */
  private LightweightMessageFormatter() {
    super(null);
    this.excerpt = LINE;
  }

  public LightweightMessageFormatter(SourceExcerptProvider source) {
    this(source, LINE);
  }

  public LightweightMessageFormatter(SourceExcerptProvider source,
      SourceExcerpt excerpt) {
    super(source);
    Preconditions.checkNotNull(source);
    this.excerpt = excerpt;
  }

  static LightweightMessageFormatter withoutSource() {
    return new LightweightMessageFormatter();
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
    String sourceName = error.sourceName;
    int lineNumber = error.lineNumber;
    int charno = error.getCharno();

    // Format the non-reverse-mapped position.
    StringBuilder b = new StringBuilder();
    StringBuilder boldLine = new StringBuilder();
    String nonMappedPosition = formatPosition(sourceName, lineNumber);

    // Check if we can reverse-map the source.
    OriginalMapping mapping = source == null ? null : source.getSourceMapping(
        error.sourceName, error.lineNumber, error.getCharno());
    if (mapping == null) {
      boldLine.append(nonMappedPosition);
    } else {
      sourceName = mapping.getOriginalFile();
      lineNumber = mapping.getLineNumber();
      charno = mapping.getColumnPosition();

      b.append(nonMappedPosition);
      b.append("\nOriginally at:\n");
      boldLine.append(formatPosition(sourceName, lineNumber));
    }

    // extract source excerpt
    String sourceExcerpt = source == null ? null :
        excerpt.get(
            source, sourceName, lineNumber, excerptFormatter);

    boldLine.append(getLevelName(warning ? CheckLevel.WARNING : CheckLevel.ERROR));
    boldLine.append(" - ");

    boldLine.append(error.description);

    b.append(maybeEmbolden(boldLine.toString()));
    b.append('\n');
    if (sourceExcerpt != null) {
      b.append(sourceExcerpt);
      b.append('\n');

      // padding equal to the excerpt and arrow at the end
      // charno == sourceExpert.length() means something is missing
      // at the end of the line
      if (excerpt.equals(LINE)
          && 0 <= charno && charno <= sourceExcerpt.length()) {
        for (int i = 0; i < charno; i++) {
          char c = sourceExcerpt.charAt(i);
          if (TokenUtil.isWhitespace(c)) {
            b.append(c);
          } else {
            b.append(' ');
          }
        }
        b.append("^\n");
      }
    }
    return b.toString();
  }

  private static String formatPosition(String sourceName, int lineNumber) {
    StringBuilder b = new StringBuilder();
    if (sourceName != null) {
      b.append(sourceName);
      if (lineNumber > 0) {
        b.append(':');
        b.append(lineNumber);
      }
      b.append(": ");
    }
    return b.toString();
  }

  /**
   * Formats a region by appending line numbers in front, e.g.
   * <pre>   9| if (foo) {
   *   10|   alert('bar');
   *   11| }</pre>
   * and return line excerpt without any modification.
   */
  static class LineNumberingFormatter implements ExcerptFormatter {
    @Override
    public String formatLine(String line, int lineNumber) {
      return line;
    }

    @Override
    public String formatRegion(Region region) {
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
