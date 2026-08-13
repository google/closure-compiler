/*
 * Copyright 2026 The Closure Compiler Authors.
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

import com.google.javascript.rhino.Node;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Collects opt-in diagnostics attributing expensive optimization work to source functions. */
final class OptimizationWorkReporter {

  private record Entry(
      String pass,
      String source,
      int line,
      int column,
      String function,
      int variables,
      int cfgNodes,
      int candidates,
      int changes,
      long runtimeNanos) {}

  private static final class SourceSummary {
    long runtimeNanos;
    int calls;
    int variables;
    int cfgNodes;
    int candidates;
    int changes;

    void add(Entry entry) {
      runtimeNanos += entry.runtimeNanos;
      calls++;
      variables += entry.variables;
      cfgNodes += entry.cfgNodes;
      candidates += entry.candidates;
      changes += entry.changes;
    }
  }

  private final List<Entry> entries = new ArrayList<>();

  synchronized void recordFunction(
      String pass,
      Node function,
      int variables,
      int cfgNodes,
      int candidates,
      int changes,
      long runtimeNanos) {
    String source = function.getSourceFileName();
    @Nullable String functionName = NodeUtil.getNearestFunctionName(function);
    entries.add(
        new Entry(
            pass,
            source != null ? source : "<unknown>",
            function.getLineno(),
            function.getCharno(),
            functionName != null ? functionName : "<anonymous>",
            variables,
            cfgNodes,
            candidates,
            changes,
            runtimeNanos));
  }

  synchronized void write(Writer writer) throws IOException {
    Map<String, SourceSummary> sourceSummaries = new LinkedHashMap<>();
    for (Entry entry : entries) {
      sourceSummaries.computeIfAbsent(entry.source, unused -> new SourceSummary()).add(entry);
    }

    writer.append(
        "kind\truntime_us\tcalls\tvariables\tcfg_nodes\tcandidates\tchanges\tpass\tsource"
            + "\tline\tcolumn\tfunction\n");
    List<Map.Entry<String, SourceSummary>> sortedSourceSummaries =
        new ArrayList<>(sourceSummaries.entrySet());
    sortedSourceSummaries.sort(
        Comparator.<Map.Entry<String, SourceSummary>>comparingLong(
                entry -> entry.getValue().runtimeNanos)
            .reversed()
            .thenComparing(Map.Entry::getKey));
    for (Map.Entry<String, SourceSummary> entry : sortedSourceSummaries) {
      SourceSummary summary = entry.getValue();
      appendRow(
          writer,
          "source",
          summary.runtimeNanos,
          summary.calls,
          summary.variables,
          summary.cfgNodes,
          summary.candidates,
          summary.changes,
          "*",
          entry.getKey(),
          0,
          0,
          "*");
    }

    List<Entry> sortedEntries = new ArrayList<>(entries);
    sortedEntries.sort(
        Comparator.comparingLong(Entry::runtimeNanos)
            .reversed()
            .thenComparing(Entry::source)
            .thenComparingInt(Entry::line)
            .thenComparingInt(Entry::column));
    for (Entry entry : sortedEntries) {
      appendRow(
          writer,
          "function",
          entry.runtimeNanos,
          1,
          entry.variables,
          entry.cfgNodes,
          entry.candidates,
          entry.changes,
          entry.pass,
          entry.source,
          entry.line,
          entry.column,
          entry.function);
    }
  }

  private static void appendRow(
      Writer writer,
      String kind,
      long runtimeNanos,
      int calls,
      int variables,
      int cfgNodes,
      int candidates,
      int changes,
      String pass,
      String source,
      int line,
      int column,
      String function)
      throws IOException {
    writer
        .append(kind)
        .append('\t')
        .append(Long.toString(runtimeNanos / 1_000))
        .append('\t')
        .append(Integer.toString(calls))
        .append('\t')
        .append(Integer.toString(variables))
        .append('\t')
        .append(Integer.toString(cfgNodes))
        .append('\t')
        .append(Integer.toString(candidates))
        .append('\t')
        .append(Integer.toString(changes))
        .append('\t')
        .append(sanitize(pass))
        .append('\t')
        .append(sanitize(source))
        .append('\t')
        .append(Integer.toString(line))
        .append('\t')
        .append(Integer.toString(column))
        .append('\t')
        .append(sanitize(function))
        .append('\n');
  }

  private static String sanitize(String value) {
    return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
  }
}
