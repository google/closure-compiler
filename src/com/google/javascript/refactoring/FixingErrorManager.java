/*
 * Copyright 2015 The Closure Compiler Authors.
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

package com.google.javascript.refactoring;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.BasicErrorManager;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.JSError;

import java.util.Collection;
import java.util.List;

/**
 * <p>An error manager that finds a SuggestedFix for all errors if possible.
 */
public class FixingErrorManager extends BasicErrorManager {
  private AbstractCompiler compiler;
  private final ListMultimap<JSError, SuggestedFix> fixes = ArrayListMultimap.create();

  public FixingErrorManager() {}

  public void setCompiler(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void report(CheckLevel level, JSError error) {
    super.report(level, error);
    fixes.putAll(error, ErrorToFixMapper.getFixesForJsError(error, compiler));
  }

  public List<SuggestedFix> getFixesForJsError(JSError error) {
    return fixes.get(error);
  }

  public Collection<SuggestedFix> getAllFixes() {
    return fixes.values();
  }

  @Override
  public void printSummary() {
    // No-op
  }

  @Override
  public void println(CheckLevel level, JSError error) {
    // No-op
  }
}
