/*
 * Copyright 2014 The Closure Compiler Authors.
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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.google.javascript.refactoring.ApplySuggestedFixes;
import com.google.javascript.refactoring.CodeReplacement;
import com.google.javascript.refactoring.SuggestedFix;

import java.util.List;
import java.util.Set;


/**
 * Utilities for testing SuggestedFix-related code.
 */
public final class SuggestedFixes {
  private SuggestedFixes() {}

  public static void assertReplacement(SuggestedFix fix, CodeReplacement expectedReplacement) {
    assertReplacements(fix, ImmutableSet.of(expectedReplacement));
  }

  private static void assertReplacements(
      SuggestedFix fix, Set<CodeReplacement> expectedReplacements) {
    SetMultimap<String, CodeReplacement> replacementMap = fix.getReplacements();
    assertEquals(1, replacementMap.size());
    Set<CodeReplacement> replacements = replacementMap.get("test");
    assertThat(replacements).hasSize(expectedReplacements.size());
    assertEquals(expectedReplacements, replacements);
  }

  public static void assertChanges(
      SuggestedFix fix, String externs, String originalCode, String expectedCode) {
    assertChanges(ImmutableList.of(fix), externs, originalCode, expectedCode);
  }

  public static void assertChanges(
      List<SuggestedFix> fixes, String externs, String originalCode, String expectedCode) {
    assertThat(fixes).isNotEmpty();
    String newCode = ApplySuggestedFixes.applySuggestedFixesToCode(
        fixes, ImmutableMap.of("test", originalCode)).get("test");
    assertEquals(expectedCode, newCode);
  }
}
