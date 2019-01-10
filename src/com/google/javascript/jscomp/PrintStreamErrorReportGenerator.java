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

import com.google.javascript.jscomp.parsing.parser.util.format.SimpleFormat;
import java.io.PrintStream;

/**
 * An error report generator that prints errors and warnings to the print stream provided.
 *
 * <p>It collaborates with a {@link SourceExcerptProvider} via a {@link MessageFormatter} to display
 * error messages with source context.
 */
public class PrintStreamErrorReportGenerator implements SortingErrorManager.ErrorReportGenerator {
  private final MessageFormatter formatter;
  private final PrintStream stream;
  private final int summaryDetailLevel;

  /**
   * Creates an error report generator.
   *
   * @param formatter the message formatter used to format the messages
   * @param stream the stream on which the errors and warnings should be printed. This class does
   *     not close the stream
   */
  public PrintStreamErrorReportGenerator(
      MessageFormatter formatter, PrintStream stream, int summaryDetailLevel) {
    this.formatter = formatter;
    this.stream = stream;
    this.summaryDetailLevel = summaryDetailLevel;
  }

  @Override
  public void generateReport(SortingErrorManager manager) {
    for (SortingErrorManager.ErrorWithLevel e : manager.getSortedDiagnostics()) {
      println(e.level, e.error);
    }
    printSummary(manager);
  }

  private void println(CheckLevel level, JSError error) {
    stream.println(error.format(level, formatter));
  }

  private void printSummary(ErrorManager manager) {
    if (summaryDetailLevel >= 3
        || (summaryDetailLevel >= 1 && manager.getErrorCount() + manager.getWarningCount() > 0)
        || (summaryDetailLevel >= 2 && manager.getTypedPercent() > 0.0)) {
      if (manager.getTypedPercent() > 0.0) {
        stream.print(
            SimpleFormat.format(
                "%d error(s), %d warning(s), %.1f%% typed%n",
                manager.getErrorCount(), manager.getWarningCount(), manager.getTypedPercent()));
      } else {
        stream.print(
            SimpleFormat.format(
                "%d error(s), %d warning(s)%n",
                manager.getErrorCount(), manager.getWarningCount()));
      }
    }
  }
}
