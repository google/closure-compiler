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

import com.google.common.collect.ImmutableSet;

/**
 * An error manager that generates a sorted report when the {@link #generateReport()} method is
 * called.
 *
 * <p>This error manager does not produce any output, but subclasses can override the {@link
 * #println(CheckLevel, JSError)} method to generate custom output. Consider using the
 * SortingErrorManager with a custom {@link SortingErrorManager.ErrorReportGenerator} instead.
 */
@Deprecated
public abstract class BasicErrorManager extends SortingErrorManager {

  public BasicErrorManager() {
    super(ImmutableSet.of());
  }

  @Override
  public void generateReport() {
    for (ErrorWithLevel message : super.getSortedDiagnostics()) {
      println(message.level, message.error);
    }
    printSummary();
  }

  /**
   * Print a message with a trailing new line. This method is called by the
   * {@link #generateReport()} method when generating messages.
   */
  public abstract void println(CheckLevel level, JSError error);

  /**
   * Print the summary of the compilation - number of errors and warnings.
   */
  protected abstract void printSummary();
}
