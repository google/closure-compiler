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

import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Streams;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/**
 * Class that applies suggested fixes to code or files.
 */
public final class ApplySuggestedFixes {

  private static final Ordering<CodeReplacement> ORDER_CODE_REPLACEMENTS =
      Ordering.natural()
          .onResultOf(CodeReplacement::getStartPosition)
          .compound(Ordering.natural().onResultOf(CodeReplacement::getLength))
          .compound(Ordering.natural().onResultOf(CodeReplacement::getSortKey));

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
      filenameToCodeMap.put(filename, Files.asCharSource(new File(filename), UTF_8).read());
    }

    Map<String, String> newCode = applySuggestedFixesToCode(fixes, filenameToCodeMap);
    for (Map.Entry<String, String> entry : newCode.entrySet()) {
      Files.asCharSink(new File(entry.getKey()), UTF_8).write(entry.getValue());
    }
  }

  /**
   * Applies all possible options from each {@code SuggestedFixAlternative} to the provided code and
   * returns the new code. This only makes sense if all the SuggestedFixAlternatives come from the
   * same checker, i.e. they offer the same number of choices and the same index corresponds to
   * similar fixes. The {@code filenameToCodeMap} must contain all the files that the provided fixes
   * apply to. The fixes can be provided in any order, but they may not have any overlapping
   * modifications for the same file. This function will return new code only for the files that
   * have been modified.
   */
  public static ImmutableList<ImmutableMap<String, String>> applyAllSuggestedFixChoicesToCode(
      Iterable<SuggestedFix> fixChoices, Map<String, String> fileNameToCodeMap) {
    if (Iterables.isEmpty(fixChoices)) {
      return ImmutableList.of(ImmutableMap.of());
    }
    int alternativeCount = Iterables.getFirst(fixChoices, null).getAlternatives().size();
    Preconditions.checkArgument(
        Streams.stream(fixChoices)
            .map(f -> f.getAlternatives().size())
            .allMatch(Predicate.isEqual(alternativeCount)),
        "All SuggestedFixAlternatives must offer an equal number of choices for this "
            + "utility to make sense");
    return IntStream.range(0, alternativeCount)
        .mapToObj(i -> applySuggestedFixChoicesToCode(fixChoices, i, fileNameToCodeMap))
        .collect(ImmutableList.toImmutableList());
  }

  private static ImmutableMap<String, String> applySuggestedFixChoicesToCode(
      Iterable<SuggestedFix> fixChoices,
      final int choiceIndex,
      Map<String, String> fileNameToCodeMap) {
    ImmutableList<SuggestedFix> chosenFixes =
        Streams.stream(fixChoices)
            .map(choices -> choices.getAlternatives().get(choiceIndex))
            .collect(ImmutableList.toImmutableList());
    return applySuggestedFixesToCode(chosenFixes, fileNameToCodeMap);
  }

  /**
   * Applies the provided set of suggested fixes to the provided code and returns the new code,
   * ignoring alternative fixes. The {@code filenameToCodeMap} must contain all the files that the
   * provided fixes apply to. The fixes can be provided in any order, but they may not have any
   * overlapping modifications for the same file. This function will return new code only for the
   * files that have been modified.
   */
  public static ImmutableMap<String, String> applySuggestedFixesToCode(
      Iterable<SuggestedFix> fixes, Map<String, String> filenameToCodeMap) {
    ReplacementMap map = new ReplacementMap();
    for (SuggestedFix fix : fixes) {
      map.putIfNoOverlap(fix);
    }
    ImmutableMap.Builder<String, String> newCodeMap = ImmutableMap.builder();
    for (Map.Entry<String, Set<CodeReplacement>> entry : map.entrySet()) {
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
      lastIndex = replacement.getEndPosition();
    }
    if (lastIndex <= code.length()) {
      sb.append(code, lastIndex, code.length());
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
    checkState(ORDER_CODE_REPLACEMENTS.isOrdered(replacements));
    if (containsOverlaps(replacements)) {
      throw new IllegalArgumentException(
          "Found overlap between code replacements!\n" + Joiner.on("\n\n").join(replacements));
    }
  }

  /**
   * Checks whether the CodeReplacements have any overlap. The replacements must be provided in
   * order sorted by start position, as sorted by ORDER_CODE_REPLACEMENTS.
   */
  private static boolean containsOverlaps(List<CodeReplacement> replacements) {
    checkState(ORDER_CODE_REPLACEMENTS.isOrdered(replacements));
    int start = -1;
    for (CodeReplacement replacement : replacements) {
      if (replacement.getStartPosition() < start) {
        return true;
      }
      start = Math.max(start, replacement.getEndPosition());
    }
    return false;
  }

  private static class ReplacementMap {
    private final SetMultimap<String, CodeReplacement> map;

    ReplacementMap() {
      this.map = HashMultimap.create();
    }

    void putIfNoOverlap(SuggestedFix fix) {
      if (canPut(fix)) {
        map.putAll(fix.getReplacements());
      }
    }

    private boolean canPut(SuggestedFix fix) {
      for (String filename : fix.getReplacements().keySet()) {
        List<CodeReplacement> replacements = new ArrayList<>(map.get(filename));
        replacements.addAll(fix.getReplacements().get(filename));
        replacements = ORDER_CODE_REPLACEMENTS.sortedCopy(replacements);
        if (containsOverlaps(replacements)) {
          return false;
        }
      }
      return true;
    }

    Set<Entry<String, Set<CodeReplacement>>> entrySet() {
      return Multimaps.asMap(map).entrySet();
    }
  }
  private ApplySuggestedFixes() {}
}
