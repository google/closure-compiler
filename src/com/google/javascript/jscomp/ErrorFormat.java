/*
 * Copyright 2009 Google Inc.
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

import com.google.javascript.jscomp.SourceExcerptProvider.SourceExcerpt;

/**
 * Error formats available.
 */
public enum ErrorFormat {
  LEGACY {
    @Override
    public MessageFormatter toFormatter(
        SourceExcerptProvider source, boolean colorize) {
      VerboseMessageFormatter formatter = new VerboseMessageFormatter(source);
      formatter.setColorize(colorize);
      return formatter;
    }
  },
  SINGLELINE {
    @Override
    public MessageFormatter toFormatter(
        SourceExcerptProvider source, boolean colorize) {
      LightweightMessageFormatter formatter = new LightweightMessageFormatter(
          source);
      formatter.setColorize(colorize);
      return formatter;
    }
  },
  MULTILINE {
    @Override
    public MessageFormatter toFormatter(
        SourceExcerptProvider source, boolean colorize) {
      LightweightMessageFormatter formatter = new LightweightMessageFormatter(
          source, SourceExcerpt.REGION);
      formatter.setColorize(colorize);
      return formatter;
    }
  },
  SOURCELESS {
    @Override
    public MessageFormatter toFormatter(
        SourceExcerptProvider source, boolean colorize) {
      return new SourcelessMessageFormatter();
    }
  };

  /**
   * Convert to a concrete formatter.
   */
  public abstract MessageFormatter toFormatter(
      SourceExcerptProvider source, boolean colorize);

  // A message formatter that does not know how to get source information.
  private static class SourcelessMessageFormatter
      extends AbstractMessageFormatter {

    private SourcelessMessageFormatter() {
      super(null);
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
      // formatting the message
      StringBuilder b = new StringBuilder();
      if (error.sourceName != null) {
        b.append(error.sourceName);
        if (error.lineNumber > 0) {
          b.append(':');
          b.append(error.lineNumber);
        }
        b.append(": ");
      }

      b.append(getLevelName(warning ? CheckLevel.WARNING : CheckLevel.ERROR));
      b.append(" - ");

      b.append(error.description);
      b.append('\n');
      return b.toString();
    }
  }
}
