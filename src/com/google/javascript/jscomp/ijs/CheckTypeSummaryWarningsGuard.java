/*
 * Copyright 2018 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.ijs;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.ClosurePrimitiveErrors;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.WarningsGuard;
import org.jspecify.nullness.Nullable;

/**
 * A warnings guard that demotes the errors found in type summary files to be less severe, leaving
 * only the errors found in the original source.
 */
public class CheckTypeSummaryWarningsGuard extends WarningsGuard {

  private final CheckLevel level;

  public CheckTypeSummaryWarningsGuard(CheckLevel level) {
    this.level = level;
  }

  @Override
  public @Nullable CheckLevel level(JSError error) {
    checkNotNull(error);
    if (!shouldDemoteIfTypeSummary(error)) {
      return null;
    }
    if (inTypeSummary(error)) {
      return this.level;
    }
    return null;
  }

  @Override
  protected int getPriority() {
    // Treat warnings in .i.js files as though they are allowlisted.
    return Priority.SUPPRESS_BY_ALLOWLIST.getValue();
  }

  /** Return whether the given error was produced inside a type summary file */
  private boolean inTypeSummary(JSError error) {
    return error.getSourceName() != null && error.getSourceName().endsWith(".i.js");
  }

  /** Return whether the given error should be demoted even if in a type summary file */
  private boolean shouldDemoteIfTypeSummary(JSError error) {
    // TODO(b/215776774): also skip demoting DUPLICATE_MODULE and DUPLICATE_NAMESPACE errors.
    // DUPLICATE_NAMESPACE_AND_MODULE is only special-cased because it was easier to land.
    return !error.getType().equals(ClosurePrimitiveErrors.DUPLICATE_NAMESPACE_AND_MODULE);
  }
}
