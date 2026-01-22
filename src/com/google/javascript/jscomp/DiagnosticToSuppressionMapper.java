/*
 * Copyright 2019 The Closure Compiler Authors.
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

import static com.google.common.collect.ImmutableSortedMap.toImmutableSortedMap;
import static java.util.Comparator.naturalOrder;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.gson.Gson;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Used to print a map from diagnostic id to suppression. */
public final class DiagnosticToSuppressionMapper {

  enum OutputFormat {
    MD,
    JSON;
  }

  private final Set<String> validSuppressions;
  private final ImmutableMap<String, DiagnosticGroup> diagnosticGroups;

  /**
   * @param validSuppressions the set of diagnostic group names valid to @suppress in code
   * @param diagnosticGroups a map from suppression to {@link DiagnosticGroup}
   */
  DiagnosticToSuppressionMapper(
      Set<String> validSuppressions, ImmutableMap<String, DiagnosticGroup> diagnosticGroups) {
    this.validSuppressions = validSuppressions;
    this.diagnosticGroups = diagnosticGroups;
  }

  /** Prints a sorted map of all valid suppressions to System.out */
  void printSuppressions(OutputFormat output) {
    switch (output) {
      case JSON -> printAsJson(createSuppressionMap());
      case MD -> printAsMarkdown(createSuppressionMap());
    }
  }

  @VisibleForTesting
  ImmutableSortedMap<String, String> createSuppressionMap() {
    Set<String> validSuppressions = new HashSet<>(this.validSuppressions);
    // deprecated in favor of "visibility"
    validSuppressions.remove("accessControls");
    validSuppressions.remove("missingSourcesWarnings");

    LinkedHashMap<DiagnosticType, String> diagnosticToSuppression = new LinkedHashMap<>();

    for (Map.Entry<String, DiagnosticGroup> entry : this.diagnosticGroups.entrySet()) {
      String suppression = entry.getKey();
      if (!validSuppressions.contains(suppression)) {
        continue;
      }

      DiagnosticGroup group = entry.getValue();
      for (DiagnosticType diagnosticType : group.getTypes()) {
        diagnosticToSuppression.merge(diagnosticType, suppression, this::preferMoreTargetedGroup);
      }
    }

    return diagnosticToSuppression.entrySet().stream()
        .collect(
            toImmutableSortedMap(
                naturalOrder(), (entrySet) -> entrySet.getKey().key, Map.Entry::getValue));
  }

  /** Use a more targeted suppression if one exists. */
  private String preferMoreTargetedGroup(String next, String current) {
    DiagnosticGroup nextGroup = this.diagnosticGroups.get(next);
    DiagnosticGroup currentGroup = this.diagnosticGroups.get(current);
    return nextGroup.getTypes().size() < currentGroup.getTypes().size() ? next : current;
  }

  private static void printAsJson(ImmutableSortedMap<String, String> diagnosticToSuppression) {
    System.out.println(new Gson().toJson(diagnosticToSuppression));
  }

  private static void printAsMarkdown(ImmutableSortedMap<String, String> diagnosticToSuppression) {
    System.out.println("| Error | Suppression tag |");
    System.out.println("|---|---|");

    diagnosticToSuppression.entrySet().stream()
        .map(e -> String.format("|%s|%s|", e.getKey(), e.getValue()))
        .forEachOrdered(System.out::println);
  }
}
