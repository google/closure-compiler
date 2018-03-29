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

import static com.google.javascript.jscomp.CheckMissingAndExtraRequires.EXTRA_REQUIRE_WARNING;
import static com.google.javascript.jscomp.CheckMissingAndExtraRequires.MISSING_REQUIRE_STRICT_WARNING;
import static com.google.javascript.jscomp.CheckMissingAndExtraRequires.MISSING_REQUIRE_WARNING;
import static com.google.javascript.jscomp.ClosureCheckModule.JSDOC_REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME;
import static com.google.javascript.jscomp.ClosureCheckModule.REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.BasicErrorManager;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.JSError;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * <p>An error manager that finds a SuggestedFix for all errors if possible.
 */
public class FixingErrorManager extends BasicErrorManager {
  private AbstractCompiler compiler;
  private final ListMultimap<JSError, SuggestedFix> fixes = ArrayListMultimap.create();
  private final ImmutableSet<DiagnosticType> unfixableErrors;

  public FixingErrorManager() {
    this(ImmutableSet.of());
  }

  public FixingErrorManager(ImmutableSet<DiagnosticType> unfixableErrors) {
    this.unfixableErrors = unfixableErrors;
  }

  public void setCompiler(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void report(CheckLevel level, JSError error) {
    super.report(level, error);
    if (!unfixableErrors.contains(error.getType())) {
      fixes.putAll(error, ErrorToFixMapper.getFixesForJsError(error, compiler));
    }
  }

  private boolean containsFixableShorthandModuleWarning() {
    for (JSError error : fixes.keySet()) {
      if (error.getType().equals(JSDOC_REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME)
          || error.getType().equals(REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME)) {
        return true;
      }
    }
    return false;
  }

  public List<SuggestedFix> getFixesForJsError(JSError error) {
    return fixes.get(error);
  }

  /** Returns fixes for errors first, then fixes for warnings. */
  public Collection<SuggestedFix> getAllFixes() {
    boolean containsFixableShorthandModuleWarning = containsFixableShorthandModuleWarning();
    Collection<SuggestedFix> fixes = new ArrayList<>();
    for (JSError error : getErrors()) {
      // Sometimes code will produce a spurious extra-require or missing-require error,
      // as well as a warning about using a full namespace instead of a shorthand type. In this case
      // don't apply the extra/missing require fix.
      if (containsFixableShorthandModuleWarning
          && (error.getType().equals(EXTRA_REQUIRE_WARNING)
              || error.getType().equals(MISSING_REQUIRE_STRICT_WARNING)
              || error.getType().equals(MISSING_REQUIRE_WARNING))) {
        // Don't apply this fix.
      } else {
        fixes.addAll(getFixesForJsError(error));
      }
    }
    for (JSError warning : getWarnings()) {
      if (warning.getType().equals(EXTRA_REQUIRE_WARNING)
          && containsFixableShorthandModuleWarning) {
        // As above, don't apply the extra-require fix.
      } else {
        fixes.addAll(getFixesForJsError(warning));
      }
    }
    return fixes;
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
