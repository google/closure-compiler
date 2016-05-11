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

package com.google.javascript.jscomp.lint;

import static com.google.javascript.jscomp.lint.CheckUnusedLabels.UNUSED_LABEL;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.Es6CompilerTestCase;

/**
 * Test case for {@link CheckUnusedLabels}.
 */
public final class CheckUnusedLabelsTest extends Es6CompilerTestCase {
  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    return new CheckUnusedLabels(compiler);
  }

  @Override
  protected CompilerOptions getOptions(CompilerOptions options) {
    super.getOptions(options);
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    return options;
  }

  public void testCheckUnusedLabels_noWarning() {
    testSame("L: if (true) { break L; }");
    testSame("L: for (;;) { if (true) { break L; } }");
    testSame("L1: L2: if (true) { if (true) { break L1; } break L2; }");
  }

  public void testCheckUnusedLabels_warning() {
    testWarning("L: var x = 0;", UNUSED_LABEL);
    testWarning("L: { f(); }", UNUSED_LABEL);
    testWarning("L: for (;;) {}", UNUSED_LABEL);
    testWarning("L1: for (;;) { L2: if (true) { break L2; } }", UNUSED_LABEL);
    testWarningEs6("() => {a: 2}", UNUSED_LABEL);
  }
}
