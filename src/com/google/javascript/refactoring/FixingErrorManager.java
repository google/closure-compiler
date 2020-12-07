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

import static com.google.javascript.jscomp.ClosureCheckModule.REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME;
import static com.google.javascript.jscomp.lint.CheckExtraRequires.EXTRA_REQUIRE_WARNING;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.BasicErrorManager;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.JSError;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/**
 * <p>An error manager that finds a SuggestedFix for all errors if possible.
 */
public class FixingErrorManager extends BasicErrorManager {
  private ErrorToFixMapper fixer;
  private final HashMap<JSError, SuggestedFix> sureFixes = new HashMap<>();
  private final ListMultimap<JSError, SuggestedFix> multiFixes = ArrayListMultimap.create();
  private final ImmutableSet<DiagnosticType> unfixableErrors;

  private enum FixTypes {
    ONE_FIX,
    ONE_FIX_AND_MULTI_FIXES,
  }

  public FixingErrorManager() {
    this(ImmutableSet.of());
  }

  public FixingErrorManager(ImmutableSet<DiagnosticType> unfixableErrors) {
    this.unfixableErrors = unfixableErrors;
  }

  public void setCompiler(AbstractCompiler compiler) {
    this.fixer = new ErrorToFixMapper(compiler);
  }

  @Override
  public void report(CheckLevel level, JSError error) {
    super.report(level, error);
    if (!unfixableErrors.contains(error.getType())) {
      ImmutableList<SuggestedFix> fixes = this.fixer.getFixesForJsError(error);
      if (fixes.size() == 1) {
        sureFixes.put(error, fixes.get(0));
      } else {
        multiFixes.putAll(error, fixes);
      }
    }
  }

  private boolean containsFixableShorthandModuleWarning() {
    for (JSError error : sureFixes.keySet()) {
      if (error.getType().equals(REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME)) {
        return true;
      }
    }
    for (JSError error : multiFixes.keySet()) {
      if (error.getType().equals(REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME)) {
        return true;
      }
    }
    return false;
  }

  public List<SuggestedFix> getFixesForJsError(JSError error) {
    if (sureFixes.containsKey(error)) {
      return ImmutableList.of(sureFixes.get(error));
    } else {
      return multiFixes.get(error);
    }
  }

  private Collection<SuggestedFix> getFixes(FixTypes fixTypes) {
    boolean containsFixableShorthandModuleWarning = containsFixableShorthandModuleWarning();
    Collection<SuggestedFix> fixes = new ArrayList<>();
    for (JSError error : getErrors()) {
      // Sometimes code will produce a spurious extra-require error,
      // as well as a warning about using a full namespace instead of a shorthand type. In this case
      // don't apply the extra require fix.
      if (containsFixableShorthandModuleWarning && error.getType().equals(EXTRA_REQUIRE_WARNING)) {
        // Don't apply this fix.
      } else {
        if (fixTypes == FixTypes.ONE_FIX && sureFixes.containsKey(error)) {
          fixes.add(sureFixes.get(error));
        } else if (fixTypes == FixTypes.ONE_FIX_AND_MULTI_FIXES) {
          fixes.addAll(getFixesForJsError(error));
        }
      }
    }
    for (JSError warning : getWarnings()) {
      if (warning.getType().equals(EXTRA_REQUIRE_WARNING)
          && containsFixableShorthandModuleWarning) {
        // As above, don't apply the extra-require fix.
      } else {
        if (fixTypes == FixTypes.ONE_FIX && sureFixes.containsKey(warning)) {
          fixes.add(sureFixes.get(warning));
        } else if (fixTypes == FixTypes.ONE_FIX_AND_MULTI_FIXES) {
          fixes.addAll(getFixesForJsError(warning));
        }
      }
    }
    return fixes;
  }

  /** Returns fixes for errors and warnings that only have one 'sure' guaranteed fix. */
  public Collection<SuggestedFix> getSureFixes() {
    return getFixes(FixTypes.ONE_FIX);
  }

  /**
   * Returns fixes for errors first, then fixes for warnings. This includes 'sure' fixes with only
   * one option, and 'multi' fixes which have multiple choices.
   */
  public Collection<SuggestedFix> getAllFixes() {
    return getFixes(FixTypes.ONE_FIX_AND_MULTI_FIXES);
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
