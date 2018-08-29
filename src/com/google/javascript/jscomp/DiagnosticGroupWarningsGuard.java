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

/**
 * Sets the level for a particular DiagnosticGroup.
 * @author nicksantos@google.com (Nick Santos)
 */
public class DiagnosticGroupWarningsGuard extends WarningsGuard {
  private static final long serialVersionUID = 1L;

  private final DiagnosticGroup group;
  private final CheckLevel level;

  public DiagnosticGroupWarningsGuard(
      DiagnosticGroup group, CheckLevel level) {
    this.group = group;
    this.level = level;
  }

  @Override
  public CheckLevel level(JSError error) {
    return group.matches(error) ? level : null;
  }

  @Override
  public boolean disables(DiagnosticGroup otherGroup) {
    return !level.isOn() && group.isSubGroup(otherGroup);
  }

  @Override
  public boolean enables(DiagnosticGroup otherGroup) {
    if (level.isOn()) {
      for (DiagnosticType type : otherGroup.getTypes()) {
        if (group.matches(type)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  protected WarningsGuard makeNonStrict() {
    return new DiagnosticGroupWarningsGuard(
        this.group,
        this.level.isOn() ? CheckLevel.WARNING : CheckLevel.OFF);
  }

  @Override
  public String toString() {
    return group + "(" + level + ")";
  }
}
