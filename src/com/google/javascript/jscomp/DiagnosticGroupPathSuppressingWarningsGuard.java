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

import org.jspecify.nullness.Nullable;

/**
 * A warnings guard that suppresses warnings for a particular diagnostic group for a file that
 * contains the specified substring.
 */
public final class DiagnosticGroupPathSuppressingWarningsGuard extends WarningsGuard {
  private final DiagnosticGroup group;
  private final String part;

  public DiagnosticGroupPathSuppressingWarningsGuard(DiagnosticGroup group, String part) {
    this.group = group;
    this.part = part;
  }

  /** Does not touch warnings in other paths. */
  @Override
  public @Nullable CheckLevel level(JSError error) {
    if (error.getSourceName() == null || !error.getSourceName().contains(this.part)) {
      return null;
    }
    return this.group.matches(error) ? CheckLevel.OFF : null;
  }

  @Override
  public String toString() {
    return this.group + "(" + this.part + ")";
  }
}
