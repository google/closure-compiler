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

import com.google.common.annotations.GwtIncompatible;

import java.io.PrintStream;

/**
 * <p>An error manager that prints errors and warnings to the print stream
 * provided in addition to the functionality of the
 * {@link BasicErrorManager}.</p>
 *
 * <p>It collaborates with a {@link SourceExcerptProvider} via a
 * {@link MessageFormatter} to display error messages with source context.</p>
 *
 */
@GwtIncompatible("java.io.PrintStream")
public class PrintStreamErrorManager extends BasicErrorManager {
  private final MessageFormatter formatter;
  private final PrintStream stream;
  private int summaryDetailLevel = 1;

  /**
   * Creates an error manager.
   * @param formatter the message formatter used to format the messages
   * @param stream the stream on which the errors and warnings should be
   *     printed. This class does not close the stream
   */
  public PrintStreamErrorManager(MessageFormatter formatter,
                                 PrintStream stream) {
    this.formatter = formatter;
    this.stream = stream;
  }

  /**
   * Creates an instance with a source-less error formatter.
   */
  public PrintStreamErrorManager(PrintStream stream) {
    this(ErrorFormat.SOURCELESS.toFormatter(null, false), stream);
  }

  @Override
  public void println(CheckLevel level, JSError error) {
    stream.println(error.format(level, formatter));
  }

  public void setSummaryDetailLevel(int summaryDetailLevel) {
    this.summaryDetailLevel = summaryDetailLevel;
  }

  @Override
  public void printSummary() {
    if (summaryDetailLevel >= 3 ||
        (summaryDetailLevel >= 1 && getErrorCount() + getWarningCount() > 0) ||
        (summaryDetailLevel >= 2 && getTypedPercent() > 0.0)) {
      if (getTypedPercent() > 0.0) {
        stream.format("%d error(s), %d warning(s), %.1f%% typed%n",
            getErrorCount(), getWarningCount(), getTypedPercent());
      } else {
        stream.format("%d error(s), %d warning(s)%n", getErrorCount(),
            getWarningCount());
      }
    }
  }
}
