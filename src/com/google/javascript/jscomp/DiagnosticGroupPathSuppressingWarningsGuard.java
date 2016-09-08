/*
 * Copyright 2016 The Closure Compiler Authors.
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
 * A warnings guard that suppresses warnings for a particular diagnostic group for a file that
 * contains the specified substring.
 * @author nicksantos@google.com (Nick Santos)
 */
public class DiagnosticGroupPathSuppressingWarningsGuard extends DiagnosticGroupWarningsGuard {
  private final String part;

  public DiagnosticGroupPathSuppressingWarningsGuard(DiagnosticGroup type, String part) {
    super(type, CheckLevel.OFF);
    this.part = part;
  }

  /** Does not suppress all warnings of any type. */
  @Override public boolean disables(DiagnosticGroup type) {
    return false;
  }

  /** Does not touch warnings in other paths. */
  @Override public CheckLevel level(JSError error) {
    return error.sourceName != null && error.sourceName.contains(part)
        ? super.level(error) /** suppress */
        : null /** proceed */;
  }

  @Override public String toString() {
    return super.toString() + "(" + part + ")";
  }
}
