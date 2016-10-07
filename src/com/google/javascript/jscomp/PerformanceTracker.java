/*
 * Copyright 2008 The Closure Compiler Authors.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.CompilerOptions.TracerMode;
import com.google.javascript.jscomp.parsing.parser.util.format.SimpleFormat;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

/**
 * A PerformanceTracker collects statistics about the runtime of each pass, and
 * how much a pass impacts the size of the compiled output, before and after
 * gzip.
 */
public final class PerformanceTracker {
  private static final int DEFAULT_WHEN_SIZE_UNTRACKED = -1;

  private final PrintStream output;

  private final Node jsRoot;
  private final Node externsRoot;

  private final TracerMode mode;

  // Keeps track of AST changes and computes code size estimation
  // if there is any.
  private final RecentChange codeChange = new RecentChange();

  private int initAstSize = DEFAULT_WHEN_SIZE_UNTRACKED;
  private int initCodeSize = DEFAULT_WHEN_SIZE_UNTRACKED;
  private int initGzCodeSize = DEFAULT_WHEN_SIZE_UNTRACKED;

  private final long startTime;
  private long endTime;
  private int passesRuntime = 0;
  private int maxMem = 0;
  private int runs = 0;
  private int changes = 0;
  private int loopRuns = 0;
  private int loopChanges = 0;

  private int jsLines = 0;
  private int jsSources = 0;
  private int externLines = 0;
  private int externSources = 0;

  // The following fields for tracking size changes are just estimates.
  // They do not take into account preserved license blocks, newline padding,
  // or pretty printing (if enabled), since they don't use CodePrinter.
  // To get exact sizes, call compiler.toSource() for the final generated code.
  private int astSize = DEFAULT_WHEN_SIZE_UNTRACKED;
  private int codeSize = DEFAULT_WHEN_SIZE_UNTRACKED;
  private int gzCodeSize = DEFAULT_WHEN_SIZE_UNTRACKED;
  private int astDiff = 0;
  private int diff = 0;
  private int gzDiff = 0;

  private final Deque<Stats> currentPass = new ArrayDeque<>();

  /** Cumulative stats for each compiler pass. */
  private ImmutableMap<String, Stats> summary;

  /** Stats a single run of a compiler pass. */
  private final List<Stats> log = new ArrayList<>();

  PerformanceTracker(Node externsRoot, Node jsRoot, TracerMode mode, PrintStream printStream) {
    Preconditions.checkArgument(mode != TracerMode.OFF,
        "PerformanceTracker can't work without tracer data.");
    this.startTime = System.currentTimeMillis();
    this.externsRoot = externsRoot;
    this.jsRoot = jsRoot;
    this.output = printStream == null ? System.out : printStream;
    this.mode = mode;
  }

  CodeChangeHandler getCodeChangeHandler() {
    return this.codeChange;
  }

  void recordPassStart(String passName, boolean isOneTime) {
    this.currentPass.push(new Stats(passName, isOneTime));
    // In Compiler, toSource may be called after every pass X. We don't want it
    // to reset the handler, because recordPassStop for pass X has not been
    // called, so we are falsely logging that pass X didn't make changes.
    if (!passName.equals("toSource")) {
      this.codeChange.reset();
    }
  }

  /**
   * Collects information about a pass P after P finishes running, eg, how much
   * time P took and what was its impact on code size.
   *
   * @param passName short name of the pass
   * @param runtime execution time in milliseconds
   */
  void recordPassStop(String passName, long runtime) {
    int allocMem = getAllocatedMegabytes();
    Stats logStats = this.currentPass.pop();
    Preconditions.checkState(passName.equals(logStats.pass));
    this.log.add(logStats);

    // Update fields that aren't related to code size
    logStats.runtime = runtime;
    logStats.allocMem = allocMem;
    logStats.runs = 1;
    if (this.codeChange.hasCodeChanged()) {
      logStats.changes = 1;
    }
    if (passName.equals(Compiler.PARSING_PASS_NAME)) {
      recordParsingStop(logStats);
    } else if (this.codeChange.hasCodeChanged() && tracksAstSize()) {
      recordOtherPassStop(logStats);
    }
  }

  private void recordParsingStop(Stats logStats) {
    recordInputCount();
    if (!tracksAstSize()) {
      return;
    }
    logStats.astSize = this.initAstSize = this.astSize = NodeUtil.countAstSize(jsRoot);
    if (!tracksSize()) {
      return;
    }
    PerformanceTrackerCodeSizeEstimator estimator =
        PerformanceTrackerCodeSizeEstimator.estimate(this.jsRoot, tracksGzSize());
    logStats.size = this.initCodeSize = this.codeSize = estimator.getCodeSize();
    if (tracksGzSize()) {
      logStats.gzSize = this.initGzCodeSize = this.gzCodeSize = estimator.getZippedCodeSize();
    }
  }

  private void recordOtherPassStop(Stats logStats) {
    int newSize = NodeUtil.countAstSize(this.jsRoot);
    logStats.astDiff = this.astSize - newSize;
    this.astSize = logStats.astSize = newSize;
    if (!tracksSize()) {
      return;
    }
    PerformanceTrackerCodeSizeEstimator estimator =
        PerformanceTrackerCodeSizeEstimator.estimate(this.jsRoot, tracksGzSize());
    newSize = estimator.getCodeSize();
    logStats.diff = this.codeSize - newSize;
    this.codeSize = logStats.size = newSize;
    if (tracksGzSize()) {
      newSize = estimator.getZippedCodeSize();
      logStats.gzDiff = this.gzCodeSize - newSize;
      this.gzCodeSize = logStats.gzSize = newSize;
    }
  }

  private void recordInputCount() {
    for (Node n : this.externsRoot.children()) {
      this.externSources += 1;
      this.externLines += estimateLines(n);
    }

    for (Node n : this.jsRoot.children()) {
      this.jsSources += 1;
      this.jsLines += estimateLines(n);
    }
  }

  private int estimateLines(Node n) {
    Preconditions.checkState(n.isScript());
    StaticSourceFile ssf = n.getStaticSourceFile();
    if (ssf instanceof SourceFile) {
      return ((SourceFile) ssf).getNumLines();
    }
    return 0;
  }

  private int bytesToMB(long bytes) {
    return (int) (bytes / (1024 * 1024));
  }

  private int getAllocatedMegabytes() {
    Runtime javaRuntime = Runtime.getRuntime();
    return bytesToMB(javaRuntime.totalMemory() - javaRuntime.freeMemory());
  }

  public boolean tracksSize() {
    return this.mode == TracerMode.RAW_SIZE || this.mode == TracerMode.ALL;
  }

  public boolean tracksGzSize() {
    return this.mode == TracerMode.ALL;
  }

  public boolean tracksAstSize() {
    return this.mode != TracerMode.TIMING_ONLY;
  }

  public int getRuntime() {
    calcTotalStats();
    return this.passesRuntime;
  }

  public int getSize() {
    calcTotalStats();
    return this.codeSize;
  }

  public int getGzSize() {
    calcTotalStats();
    return this.gzCodeSize;
  }

  public int getAstSize() {
    calcTotalStats();
    return this.astSize;
  }

  @VisibleForTesting
  int getChanges() {
    calcTotalStats();
    return this.changes;
  }

  @VisibleForTesting
  int getLoopChanges() {
    calcTotalStats();
    return this.loopChanges;
  }

  @VisibleForTesting
  int getRuns() {
    calcTotalStats();
    return this.runs;
  }

  @VisibleForTesting
  int getLoopRuns() {
    calcTotalStats();
    return this.loopRuns;
  }

  public ImmutableMap<String, Stats> getStats() {
    calcTotalStats();
    return this.summary;
  }

  private void calcTotalStats() {
    // This method only does work the first time it is called
    if (this.summary != null) {
      return;
    }
    this.endTime = System.currentTimeMillis();

    populateSummary();

    for (Entry<String, Stats> entry : this.summary.entrySet()) {
      Stats stats = entry.getValue();
      this.passesRuntime += stats.runtime;
      this.maxMem = Math.max(this.maxMem, stats.allocMem);
      this.runs += stats.runs;
      this.changes += stats.changes;
      if (!stats.isOneTime) {
        this.loopRuns += stats.runs;
        this.loopChanges += stats.changes;
      }
      this.astDiff += stats.astDiff;
      this.diff += stats.diff;
      this.gzDiff += stats.gzDiff;
    }
    Preconditions.checkState(!tracksAstSize() || this.initAstSize == this.astDiff + this.astSize);
    Preconditions.checkState(!tracksSize() || this.initCodeSize == this.diff + this.codeSize);
    Preconditions.checkState(
        !tracksGzSize() || this.initGzCodeSize == this.gzDiff + this.gzCodeSize);
  }

  private void populateSummary() {
    HashMap<String, Stats> tmpSummary = new HashMap<>();

    for (Stats logStat : this.log) {
      String passName = logStat.pass;
      Stats entry = tmpSummary.get(passName);
      if (entry == null) {
        entry = new Stats(passName, logStat.isOneTime);
        tmpSummary.put(passName, entry);
      }
      entry.runtime += logStat.runtime;
      entry.allocMem = Math.max(entry.allocMem, logStat.allocMem);
      entry.runs++;
      entry.changes += logStat.changes;
      entry.astDiff += logStat.astDiff;
      entry.diff += logStat.diff;
      entry.gzDiff += logStat.gzDiff;
      // We don't populate the size fields in the summary stats.
      // We used to put the size after the last time a pass was run, but that is
      // a pretty meaningless thing to measure.
    }

    this.summary = ImmutableMap.copyOf(tmpSummary);
  }

  /**
   * Prints a summary, which contains aggregate stats for all runs of each pass
   * and a log, which contains stats for each individual run.
   */
  public void outputTracerReport() {
    JvmMetrics.maybeWriteJvmMetrics(this.output, "verbose:pretty:all");
    calcTotalStats();

    ArrayList<Entry<String, Stats>> statEntries = new ArrayList<>();
    statEntries.addAll(this.summary.entrySet());
    Collections.sort(
        statEntries,
        new Comparator<Entry<String, Stats>>() {
          @Override
          public int compare(Entry<String, Stats> e1, Entry<String, Stats> e2) {
            return Long.compare(e1.getValue().runtime, e2.getValue().runtime);
          }
        });

    this.output.print("Summary:\n"
        + "pass,runtime,allocMem,runs,changingRuns,astReduction,reduction,gzReduction\n");
    for (Entry<String, Stats> entry : statEntries) {
      String key = entry.getKey();
      Stats stats = entry.getValue();
      this.output.print(SimpleFormat.format("%s,%d,%d,%d,%d,%d,%d,%d\n", key, stats.runtime,
            stats.allocMem, stats.runs, stats.changes, stats.astDiff, stats.diff, stats.gzDiff));
    }

    this.output.print(Joiner.on("\n").join(
        "\nTOTAL:",
        "Wall time(ms): " + (this.endTime - this.startTime),
        "Passes runtime(ms): " + this.passesRuntime,
        "Max mem usage (measured after each pass)(MB): " + this.maxMem,
        "#Runs: " + this.runs,
        "#Changing runs: " + this.changes,
        "#Loopable runs: " + this.loopRuns,
        "#Changing loopable runs: " + this.loopChanges,
        "Estimated AST reduction(#nodes): " + this.astDiff,
        "Estimated Reduction(bytes): " + this.diff,
        "Estimated GzReduction(bytes): " + this.gzDiff,
        "Estimated AST size(#nodes): " + this.astSize,
        "Estimated Size(bytes): " + this.codeSize,
        "Estimated GzSize(bytes): " + this.gzCodeSize));

    this.output.print(Joiner.on("\n").join(
        "\n\nInputs:",
        "JS lines:   " + this.jsLines,
        "JS sources: " + this.jsSources,
        "Extern lines:   " + this.externLines,
        "Extern sources: " + this.externSources + "\n\n"));

    this.output.print(Joiner.on("\n").join(
        "Log:",
        "pass,runtime,allocMem,codeChanged,astReduction,reduction,gzReduction,astSize,size,gzSize"));
    for (Stats stats : this.log) {
      this.output.print(SimpleFormat.format("%s,%d,%d,%b,%d,%d,%d,%d,%d,%d\n",
          stats.pass, stats.runtime, stats.allocMem, stats.changes == 1,
          stats.astDiff, stats.diff, stats.gzDiff, stats.astSize, stats.size, stats.gzSize));
    }
    this.output.print("\n");
    // this.output can be System.out, so don't close it to not lose subsequent
    // error messages. Flush to ensure that you will see the tracer report.
    try {
      // TODO(johnlenz): Remove this cast and try/catch.
      // This is here to workaround GWT http://b/30943295
      ((FilterOutputStream) this.output).flush();
    } catch (IOException e) {
      throw new RuntimeException("Unreachable.");
    }
  }

  /**
   * A Stats object contains statistics about a pass run, such as running time,
   * size changes, etc
   */
  public static class Stats {
    Stats(String pass, boolean iot) {
      this.pass = pass;
      this.isOneTime = iot;
    }
    public final String pass;
    public final boolean isOneTime;
    public long runtime = 0;
    public int allocMem = 0;
    public int runs = 0;
    public int changes = 0;
    public int diff = 0;
    public int gzDiff = 0;
    public int size = 0;
    public int gzSize = 0;
    public int astDiff = 0;
    public int astSize = 0;
  }
}
