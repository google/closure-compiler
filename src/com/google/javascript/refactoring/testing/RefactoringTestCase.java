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
package com.google.javascript.refactoring.testing;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.refactoring.ApplySuggestedFixes;
import com.google.javascript.refactoring.RefactoringDriver;
import com.google.javascript.refactoring.Scanner;
import com.google.javascript.refactoring.SuggestedFix;
import java.util.List;

/** RefactoringTestCase */
public abstract class RefactoringTestCase {
  protected static final Joiner LINE_JOINER = Joiner.on('\n');

  protected abstract Scanner getScanner();

  protected abstract String getExterns();

  private List<SuggestedFix> runRefactoring(String originalCode) {
    return (new RefactoringDriver.Builder()
            .addExternsFromCode(getExterns())
            .addInputsFromCode(originalCode)
            .withCompilerOptions(getOptions())
            .build())
        .drive(getScanner());
  }

  protected CompilerOptions getOptions() {
    return RefactoringDriver.getCompilerOptions();
  }

  protected void assertChanges(String originalCode, String... expectedCodeChoices) {
    List<SuggestedFix> fixes = runRefactoring(originalCode);
    assertThat(fixes).isNotEmpty();
    ImmutableList<String> newCode =
        ApplySuggestedFixes.applyAllSuggestedFixChoicesToCode(
                fixes, ImmutableMap.of("input", originalCode))
            .stream()
            .map(m -> m.get("input"))
            .collect(toImmutableList());
    assertThat(newCode).containsExactlyElementsIn(ImmutableList.copyOf(expectedCodeChoices));
  }

  protected void assertNoChanges(String originalCode) {
    List<SuggestedFix> fixes = runRefactoring(originalCode);
    assertTrue(
        "No changes should be made to the code, but found: " + fixes,
        fixes.isEmpty()
            || (fixes.size() == 1
                && fixes
                    .get(0)
                    .getAlternatives()
                    .stream()
                    .map(SuggestedFix::getReplacements)
                    .allMatch(Multimap::isEmpty)));
  }
}
