/*
 * Copyright 2008 The Closure Compiler Authors.
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

/**
 * Class that allows to flexibly manage what to do with a reported
 * warning/error.
 *
 * Guard has several choices:
 *   - return OFF - suppress the warning/error
 *   - return WARNING
 *   - return ERROR report it with high severity
 *   - return null. Does not know what to do with it. Lets the other guard
 *       decide what to do with it.
 *
 * Although the interface is very simple it allows you easyly customize what
 * warnings you are interested in.
 *
 * For example there are could be several implementations:
 *   StrictGuard - {return ERROR}. All warnings should be treat as errors.
 *   SilentGuard - {if (WARNING) return OFF}. Suppress all warnings but still
 *     fail if js has errors.
 *   WhitelistGuard (if !whitelistErrors.contains(error) return ERROR) return
 *     error if it does not present in the whitelist.
 *
 * @author anatol@google.com (Anatol Pomazau)
 */
public abstract class WarningsGuard {

  public static enum Priority {
    MAX(1),
    MIN(100),
    STRICT(100),
    DEFAULT(50),
    SUPPRESS_BY_WHITELIST(40),
    SUPPRESS_DOC(20),
    FILTER_BY_PATH(1);

    final int value;

    Priority(int value) {
      this.value = value;
    }

    public int getValue() {
      return value;
    }
  }

  /**
   * Returns a new check level for a given error. OFF - suppress it, ERROR -
   * report as error. null means that this guard does not know what to do
   * with the error. Null is extremely helpful when you have a chain of
   * guards. If current guard returns null, then the next in the chain should
   * process it.
   *
   * @param error a reported error.
   * @return what level given error should have.
   */
  public abstract CheckLevel level(JSError error);

  /**
   * The priority in which warnings guards are applied. Lower means the
   * guard will be applied sooner. Expressed on a scale of 1 to 100.
   */
  protected int getPriority() {
    return Priority.DEFAULT.value;
  }

  /**
   * Returns whether all warnings in the given diagnostic group will be
   * filtered out. Used to determine which passes to skip.
   *
   * @param group A group of DiagnosticTypes.
   * @return Whether all warnings of these types are disabled by this guard.
   */
  protected boolean disables(DiagnosticGroup group) {
    return false;
  }

  /**
   * Returns whether any of the warnings in the given diagnostic group will be
   * upgraded to a warning or error.
   *
   * @param group A group of DiagnosticTypes.
   * @return Whether any warnings of these types are enabled by this guard.
   */
  protected boolean enables(DiagnosticGroup group) {
    return false;
  }
}
