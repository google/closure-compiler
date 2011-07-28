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

import com.google.common.collect.Sets;
import com.google.javascript.jscomp.CheckLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;

/**
 * <p>A basic error manager that sorts all errors and warnings reported to it to
 * generate a sorted report when the {@link #generateReport()} method
 * is called.</p>
 *
 * <p>This error manager does not produce any output, but subclasses can
 * override the {@link #println(CheckLevel, JSError)} method to generate custom
 * output.</p>
 *
 */
public abstract class BasicErrorManager implements ErrorManager {
  private final SortedSet<ErrorWithLevel> messages =
      Sets.newTreeSet(new LeveledJSErrorComparator());
  private int errorCount = 0;
  private int warningCount = 0;
  private double typedPercent = 0.0;

  @Override
  public void report(CheckLevel level, JSError error) {
    if (messages.add(new ErrorWithLevel(error, level))) {
      if (level == CheckLevel.ERROR) {
        errorCount++;
      } else if (level == CheckLevel.WARNING) {
        warningCount++;
      }
    }
  }

  @Override
  public void generateReport() {
    for (ErrorWithLevel message : messages) {
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

  @Override
  public int getErrorCount() {
    return errorCount;
  }

  @Override
  public int getWarningCount() {
    return warningCount;
  }

  @Override
  public JSError[] getErrors() {
    return toArray(CheckLevel.ERROR);
  }

  @Override
  public JSError[] getWarnings() {
    return toArray(CheckLevel.WARNING);
  }

  @Override
  public void setTypedPercent(double typedPercent) {
    this.typedPercent = typedPercent;
  }

  @Override
  public double getTypedPercent() {
    return typedPercent;
  }

  private JSError[] toArray(CheckLevel level) {
    List<JSError> errors = new ArrayList<JSError>(messages.size());
    for (ErrorWithLevel p : messages) {
      if (p.level == level) {
        errors.add(p.error);
      }
    }
    return errors.toArray(new JSError[errors.size()]);
  }

  /**
   * <p>Comparator of {@link JSError} with an associated {@link CheckLevel}.
   * The ordering is the standard lexical ordering on the quintuple
   * (file name, line number, {@link CheckLevel},
   * character number, description).</p>
   *
   * <p>Note: this comparator imposes orderings that are inconsistent with
   * {@link JSError#equals(Object)}.</p>
   */
  static final class LeveledJSErrorComparator
      implements Comparator<ErrorWithLevel> {
    private static final int P1_LT_P2 = -1;
    private static final int P1_GT_P2 = 1;

    @Override
    public int compare(ErrorWithLevel p1, ErrorWithLevel p2) {
      // null is the smallest value
      if (p2 == null) {
        if (p1 == null) {
          return 0;
        } else {
          return P1_GT_P2;
        }
      }

      // check level
      if (p1.level != p2.level) {
        return p2.level.compareTo(p1.level);
      }

      // sourceName comparison
      String source1 = p1.error.sourceName;
      String source2 = p2.error.sourceName;
      if (source1 != null && source2 != null) {
        int sourceCompare = source1.compareTo(source2);
        if (sourceCompare != 0) {
          return sourceCompare;
        }
      } else if (source1 == null && source2 != null) {
        return P1_LT_P2;
      } else if (source1 != null && source2 == null) {
        return P1_GT_P2;
      }
      // lineno comparison
      int lineno1 = p1.error.lineNumber;
      int lineno2 = p2.error.lineNumber;
      if (lineno1 != lineno2) {
        return lineno1 - lineno2;
      } else if (lineno1 < 0 && 0 <= lineno2) {
        return P1_LT_P2;
      } else if (0 <= lineno1 && lineno2 < 0) {
        return P1_GT_P2;
      }
      // charno comparison
      int charno1 = p1.error.getCharno();
      int charno2 = p2.error.getCharno();
      if (charno1 != charno2) {
        return charno1 - charno2;
      } else if (charno1 < 0 && 0 <= charno2) {
        return P1_LT_P2;
      } else if (0 <= charno1 && charno2 < 0) {
        return P1_GT_P2;
      }
      // description
      return p1.error.description.compareTo(p2.error.description);
    }
  }

  static class ErrorWithLevel {
    final JSError error;
    final CheckLevel level;

    ErrorWithLevel(JSError error, CheckLevel level) {
      this.error = error;
      this.level = level;
    }
  }
}
