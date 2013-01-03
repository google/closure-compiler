/*
 * Copyright 2010 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.ant;

import com.google.javascript.jscomp.BasicErrorManager;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.MessageFormatter;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;

/**
 * An error manager that pipes warnings and errors properly into the Ant
 * task infrastructure.
 */
public final class AntErrorManager
    extends BasicErrorManager {
  private final MessageFormatter formatter;
  private final Task task;

  public AntErrorManager(MessageFormatter formatter, Task task) {
    this.formatter = formatter;
    this.task = task;
  }

  @Override
  public void println(CheckLevel level, JSError error) {
    switch (level) {
      case ERROR:
        this.task.log(error.format(level, this.formatter), Project.MSG_ERR);
        break;
      case WARNING:
        this.task.log(error.format(level, this.formatter), Project.MSG_WARN);
        break;
      case OFF:
        break;
    }
  }

  @Override
  protected void printSummary() {
    String message =
        getErrorCount() + " error(s), " + getWarningCount() + " warning(s)";

    if (getTypedPercent() > 0.0) {
      message += ", " + getTypedPercent() + " typed";
    }

    int level = (getErrorCount() + getWarningCount() == 0) ?
        Project.MSG_INFO : Project.MSG_WARN;
    this.task.log(message, level);
  }
}
