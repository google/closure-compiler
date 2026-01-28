/*
 * Copyright 2017 The Closure Compiler Authors.
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

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.ConformanceConfig.LibraryLevelNonAllowlistedConformanceViolationsBehavior;

/**
 * A simple delegating {@link ErrorManager} that provides a thread-safe wrapper
 * for the one being delegated.
 */
public class ThreadSafeDelegatingErrorManager implements ErrorManager {
  private final ErrorManager delegated;

  public ThreadSafeDelegatingErrorManager(ErrorManager delegated) {
    this.delegated = delegated;
  }

  @Override
  public synchronized void report(CheckLevel level, JSError error) {
    delegated.report(level, error);
  }

  @Override
  public synchronized void generateReport() {
    delegated.generateReport();
  }

  @Override
  public boolean hasHaltingErrors() {
    return delegated.hasHaltingErrors();
  }

  @Override
  public synchronized int getErrorCount() {
    return delegated.getErrorCount();
  }

  @Override
  public synchronized int getWarningCount() {
    return delegated.getWarningCount();
  }

  @Override
  public synchronized ImmutableList<JSError> getErrors() {
    return delegated.getErrors();
  }

  @Override
  public synchronized ImmutableList<JSError> getWarnings() {
    return delegated.getWarnings();
  }

  @Override
  public synchronized void setTypedPercent(double typedPercent) {
    delegated.setTypedPercent(typedPercent);
  }

  @Override
  public synchronized double getTypedPercent() {
    return delegated.getTypedPercent();
  }

  @Override
  public synchronized boolean shouldReportConformanceViolation(
      Requirement requirement,
      Optional<RequirementScopeEntry> allowlistEntry,
      JSError diagnostic,
      LibraryLevelNonAllowlistedConformanceViolationsBehavior behavior,
      boolean isAllowlisted) {
    return delegated.shouldReportConformanceViolation(
        requirement, allowlistEntry, diagnostic, behavior, isAllowlisted);
  }
}
