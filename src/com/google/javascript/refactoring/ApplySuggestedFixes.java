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

package com.google.javascript.refactoring;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.common.collect.SetMultimap;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class that applies suggested fixes to code or files.
 */
public final class ApplySuggestedFixes {

  private static final Ordering<CodeReplacement> ORDER_CODE_REPLACEMENTS = Ordering.natural()
      .onResultOf(new Function<CodeReplacement, Integer>() {
        @Override public Integer apply(CodeReplacement replacement) {
          return replacement.getStartPosition();
        }
      })
      .compound(Ordering.natural().onResultOf(new Function<CodeReplacement, Integer>() {
        @Override public Integer apply(CodeReplacement replacement) {
          return replacement.getLength();
        }
      }));


  /**
   * Applies the provided set of suggested fixes to the files listed in the suggested fixes.
   * The fixes can be provided in any order, but they may not have any overlapping modifications
   * for the same file.
   */
  public static void applySuggestedFixesToFiles(Iterable<SuggestedFix> fixes)
      throws IOException {
    Set<String> filenames = new HashSet<>();
    for (SuggestedFix fix : fixes) {
      filenames.addAll(fix.getReplacements().keySet());
    }

    Map<String, String> filenameToCodeMap = new HashMap<>();
    for (String filename : filenames) {
      filenameToCodeMap.put(filename, Files.toString(new File(filename), UTF_8));
    }

    Map<String, String> newCode = applySuggestedFixesToCode(fixes, filenameToCodeMap);
    for (Map.Entry<String, String> entry : newCode.entrySet()) {
      Files.write(entry.getValue(), new File(entry.getKey()), UTF_8);
    }
  }

  /**
   * Applies the provided set of suggested fixes to the provided code and returns the new code.
   * The {@code filenameToCodeMap} must contain all the files that the provided fixes apply to.
   * The fixes can be provided in any order, but they may not have any overlapping modifications
   * for the same file.
   * This function will return new code only for the files that have been modified.
   */
  public static Map<String, String> applySuggestedFixesToCode(
      Iterable<SuggestedFix> fixes, Map<String, String> filenameToCodeMap) {
    ImmutableSetMultimap.Builder<String, CodeReplacement> builder = ImmutableSetMultimap.builder();
    for (SuggestedFix fix : fixes) {
      builder.putAll(fix.getReplacements());
    }
    SetMultimap<String, CodeReplacement> replacementsMap = builder.build();
    ImmutableMap.Builder<String, String> newCodeMap = ImmutableMap.builder();
    for (Map.Entry<String, Set<CodeReplacement>> entry
        : Multimaps.asMap(replacementsMap).entrySet()) {
      String filename = entry.getKey();
      if (!filenameToCodeMap.containsKey(filename)) {
        throw new IllegalArgumentException("filenameToCodeMap missing code for file: " + filename);
      }
      Set<CodeReplacement> replacements = entry.getValue();
      String newCode = applyCodeReplacements(replacements, filenameToCodeMap.get(filename));
      newCodeMap.put(filename, newCode);
    }
    return newCodeMap.build();
  }

  /**
   * Applies the provided set of code replacements to the code and returns the transformed code.
   * The code replacements may not have any overlap.
   */
  public static String applyCodeReplacements(Iterable<CodeReplacement> replacements, String code) {
    List<CodeReplacement> sortedReplacements = ORDER_CODE_REPLACEMENTS.sortedCopy(replacements);
    validateNoOverlaps(sortedReplacements);

    StringBuilder sb = new StringBuilder();
    int lastIndex = 0;
    for (CodeReplacement replacement : sortedReplacements) {
      sb.append(code, lastIndex, replacement.getStartPosition());
      sb.append(replacement.getNewContent());
      lastIndex = replacement.getStartPosition() + replacement.getLength();
    }
    if (lastIndex <= code.length()) {
      sb.append(code.substring(lastIndex));
    }
    return sb.toString();
  }

  /**
   * Validates that none of the CodeReplacements have any overlap, since applying
   * changes that have overlap will produce malformed results.
   * The replacements must be provided in order sorted by start position, as sorted
   * by ORDER_CODE_REPLACEMENTS.
   */
  private static void validateNoOverlaps(List<CodeReplacement> replacements) {
    int start = -1;
    for (CodeReplacement replacement : replacements) {
      if (replacement.getStartPosition() < start) {
        throw new IllegalArgumentException(
            "Found overlap between code replacements!\n" + replacements);
      }
      start = Math.max(start, replacement.getStartPosition() + replacement.getLength());
    }
  }
}
