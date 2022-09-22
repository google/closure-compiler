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

import com.google.javascript.jscomp.base.Tri;
import java.io.Serializable;
import org.jspecify.nullness.Nullable;

/**
 * Class that allows to flexibly manage what to do with a reported warning/error.
 *
 * <p>Guard has several choices: - return OFF - suppress the warning/error - return WARNING - return
 * ERROR report it with high severity - return null. Does not know what to do with it. Lets the
 * other guard decide what to do with it.
 *
 * <p>Although the interface is very simple, it allows you easily customize what warnings you are
 * interested in.
 *
 * <p>For example there are could be several implementations: StrictGuard - {return ERROR}. All
 * warnings should be treat as errors. SilentGuard - {if (WARNING) return OFF}. Suppress all
 * warnings but still fail if JS has errors. AllowlistGuard (if !allowlistErrors.contains(error)
 * return ERROR) return error if it does not present in the allowlist.
 */
public abstract class WarningsGuard implements Serializable {

  /** Priority */
  public enum Priority {
    MAX(1),
    MIN(100),
    STRICT(100),
    DEFAULT(50),
    SUPPRESS_BY_ALLOWLIST(40),
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
   * Returns a new check level for a given error.
   *
   * <p>`null` means that this guard does not know what to do with the error. `null` can be used it
   * chain multiple guards; if current guard returns null, then the next in the chain should process
   * it.
   *
   * @param error a reported error.
   * @return what level given error should have.
   */
  public abstract @Nullable CheckLevel level(JSError error);

  /**
   * Do checks for `group` still need to be run if this guard is installed?
   *
   * <ol>
   *   <li>TRUE: Enables one or more types in the group, so it must be checked.
   *   <li>FALSE: Disables all types in the group, so it need not be checked.
   *   <li>UNKNOWN: Does not affect or only partially disables the group, so checking is undecided.
   * </ol>
   *
   * @param group a group to check.
   */
  public Tri mustRunChecks(DiagnosticGroup group) {
    return Tri.UNKNOWN;
  }

  /**
   * The priority in which warnings guards are applied. Lower means the guard will be applied
   * sooner. Expressed on a scale of 1 to 100.
   */
  protected int getPriority() {
    return Priority.DEFAULT.value;
  }
}
