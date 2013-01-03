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

import com.google.javascript.jscomp.CheckLevel;

import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * An error manager that logs errors and warnings using a logger in addition to
 * collecting them in memory. Errors are logged at the SEVERE level and warnings
 * are logged at the WARNING level.
 *
 */
public class LoggerErrorManager extends BasicErrorManager {
  private final MessageFormatter formatter;
  private final Logger logger;

  /**
   * Creates an instance.
   */
  public LoggerErrorManager(MessageFormatter formatter, Logger logger) {
    this.formatter = formatter;
    this.logger = logger;
  }

  /**
   * Creates an instance with a source-less error formatter.
   */
  public LoggerErrorManager(Logger logger) {
    this(ErrorFormat.SOURCELESS.toFormatter(null, false), logger);
  }

  @Override
  public void println(CheckLevel level, JSError error) {
    switch (level) {
      case ERROR:
        logger.severe(error.format(level, formatter));
        break;
      case WARNING:
        logger.warning(error.format(level, formatter));
        break;
      case OFF:
        break;
    }
  }

  @Override
  protected void printSummary() {
    Level level = (getErrorCount() + getWarningCount() == 0) ?
        Level.INFO : Level.WARNING;
    if (getTypedPercent() > 0.0) {
      logger.log(level, "{0} error(s), {1} warning(s), {2,number,#.#}% typed",
          new Object[] {getErrorCount(), getWarningCount(), getTypedPercent()});
    } else {
      if (getErrorCount() + getWarningCount() > 0) {
        logger.log(level, "{0} error(s), {1} warning(s)",
            new Object[] {getErrorCount(), getWarningCount()});
      }
    }
  }
}
