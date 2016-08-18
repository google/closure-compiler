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
import java.util.Map;
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
  private final boolean trackSize;
  private final boolean trackGzSize;

  // Keeps track of AST changes and computes code size estimation
  // if there is any.
  private final RecentChange codeChange = new RecentChange();

  private int initCodeSize = DEFAULT_WHEN_SIZE_UNTRACKED;
  private int initGzCodeSize = DEFAULT_WHEN_SIZE_UNTRACKED;

  private int runtime = 0;
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
  private int codeSize = DEFAULT_WHEN_SIZE_UNTRACKED;
  private int gzCodeSize = DEFAULT_WHEN_SIZE_UNTRACKED;
  private int diff = 0;
  private int gzDiff = 0;

  private final Deque<Stats> currentPass = new ArrayDeque<>();

  /** Summary stats by pass name. */
  private final Map<String, Stats> summary = new HashMap<>();

  // To share with the rest of the program
  private ImmutableMap<String, Stats> summaryCopy;

  /** Stats for each run of a compiler pass. */
  private final List<Stats> log = new ArrayList<>();

  PerformanceTracker(Node externsRoot, Node jsRoot, TracerMode mode, PrintStream printStream) {
    this.externsRoot = externsRoot;
    this.jsRoot = jsRoot;
    this.output = printStream == null ? System.out : printStream;
    switch (mode) {
      case TIMING_ONLY:
        this.trackSize = false;
        this.trackGzSize = false;
        break;

      case RAW_SIZE:
        this.trackSize = true;
        this.trackGzSize = false;
        break;

      case ALL:
        this.trackSize = true;
        this.trackGzSize = true;
        break;

      case OFF:
      default:
        throw new IllegalArgumentException(
            "PerformanceTracker can't work without tracer data.");
    }
  }

  CodeChangeHandler getCodeChangeHandler() {
    return codeChange;
  }

  void recordPassStart(String passName, boolean isOneTime) {
    currentPass.push(new Stats(passName, isOneTime));
    // In Compiler, toSource may be called after every pass X. We don't want it
    // to reset the handler, because recordPassStop for pass X has not been
    // called, so we are falsely logging that pass X didn't make changes.
    if (!passName.equals("toSource")) {
      codeChange.reset();
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

    Stats logStats = currentPass.pop();
    Preconditions.checkState(passName.equals(logStats.pass));

    // Populate log and summary
    log.add(logStats);
    Stats summaryStats = summary.get(passName);
    if (summaryStats == null) {
      summaryStats = new Stats(passName, logStats.isOneTime);
      summary.put(passName, summaryStats);
    }

    // After parsing, initialize codeSize and gzCodeSize
    if (passName.equals(Compiler.PARSING_PASS_NAME)) {
      recordInputCount();
      if (trackSize) {
        PerformanceTrackerCodeSizeEstimator estimator =
            PerformanceTrackerCodeSizeEstimator.estimate(jsRoot, trackGzSize);
        initCodeSize = codeSize = estimator.getCodeSize();
        logStats.size = summaryStats.size = initCodeSize;
        if (this.trackGzSize) {
          initGzCodeSize = gzCodeSize = estimator.getZippedCodeSize();
          logStats.gzSize = summaryStats.gzSize = initGzCodeSize;
        }
      }
    }

    // Update fields that aren't related to code size
    logStats.runtime = runtime;
    logStats.allocMem = allocMem;
    logStats.runs = 1;
    summaryStats.runtime += runtime;
    summaryStats.allocMem = Math.max(allocMem, summaryStats.allocMem);
    summaryStats.runs += 1;
    if (codeChange.hasCodeChanged()) {
      logStats.changes = 1;
      summaryStats.changes += 1;
    }

    // Update fields related to code size
    if (codeChange.hasCodeChanged() && trackSize) {
      int newSize = 0;
      PerformanceTrackerCodeSizeEstimator estimator = PerformanceTrackerCodeSizeEstimator.estimate(
          jsRoot, trackGzSize);
      newSize = estimator.getCodeSize();
      logStats.diff = codeSize - newSize;
      summaryStats.diff += logStats.diff;
      codeSize = summaryStats.size = logStats.size = newSize;
      if (trackGzSize) {
        newSize = estimator.getZippedCodeSize();
        logStats.gzDiff = gzCodeSize - newSize;
        summaryStats.gzDiff += logStats.gzDiff;
        gzCodeSize = summaryStats.gzSize = logStats.gzSize = newSize;
      }
    }
  }

  private void recordInputCount() {
    for (Node n : externsRoot.children()) {
      this.externSources += 1;
      this.externLines += estimateLines(n);
    }

    for (Node n : jsRoot.children()) {
      this.jsSources += 1;
      this.jsLines += estimateLines(n);
    }
  }

  private int estimateLines(Node n) {
    Preconditions.checkState(n.isScript());
    StaticSourceFile ssf = n.getStaticSourceFile();
    if (ssf != null && ssf instanceof SourceFile) {
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
    return trackSize;
  }

  public boolean tracksGzSize() {
    return trackGzSize;
  }

  public int getRuntime() {
    calcTotalStats();
    return runtime;
  }

  public int getSize() {
    calcTotalStats();
    return codeSize;
  }

  public int getGzSize() {
    calcTotalStats();
    return gzCodeSize;
  }

  @VisibleForTesting
  int getChanges() {
    calcTotalStats();
    return changes;
  }

  @VisibleForTesting
  int getLoopChanges() {
    calcTotalStats();
    return loopChanges;
  }

  @VisibleForTesting
  int getRuns() {
    calcTotalStats();
    return runs;
  }

  @VisibleForTesting
  int getLoopRuns() {
    calcTotalStats();
    return loopRuns;
  }

  public ImmutableMap<String, Stats> getStats() {
    calcTotalStats();
    return summaryCopy;
  }

  private void calcTotalStats() {
    // This method only does work the first time it's called
    if (summaryCopy != null) {
      return;
    }
    summaryCopy = ImmutableMap.copyOf(summary);
    for (Entry<String, Stats> entry : summary.entrySet()) {
      Stats stats = entry.getValue();
      runtime += stats.runtime;
      maxMem = Math.max(maxMem, stats.allocMem);
      runs += stats.runs;
      changes += stats.changes;
      if (!stats.isOneTime) {
        loopRuns += stats.runs;
        loopChanges += stats.changes;
      }
      diff += stats.diff;
      gzDiff += stats.gzDiff;
    }
    Preconditions.checkState(!trackSize || initCodeSize == diff + codeSize);
    Preconditions.checkState(!trackGzSize
        || initGzCodeSize == gzDiff + gzCodeSize);
  }

  /**
   * Prints a summary, which contains aggregate stats for all runs of each pass
   * and a log, which contains stats for each individual run.
   */
  public void outputTracerReport() {
    JvmMetrics.maybeWriteJvmMetrics(this.output, "verbose:pretty:all");
    calcTotalStats();

    ArrayList<Entry<String, Stats>> statEntries = new ArrayList<>();
    statEntries.addAll(summary.entrySet());
    Collections.sort(
        statEntries,
        new Comparator<Entry<String, Stats>>() {
          @Override
          public int compare(Entry<String, Stats> e1, Entry<String, Stats> e2) {
            return Long.compare(e1.getValue().runtime, e2.getValue().runtime);
          }
        });

    this.output.print("Summary:\n"
        + "pass,runtime,allocMem,runs,changingRuns,reduction,gzReduction\n");
    for (Entry<String, Stats> entry : statEntries) {
      String key = entry.getKey();
      Stats stats = entry.getValue();
      this.output.print(SimpleFormat.format("%s,%d,%d,%d,%d,%d,%d\n", key, stats.runtime,
            stats.allocMem, stats.runs, stats.changes, stats.diff, stats.gzDiff));
    }
    this.output.print("\nTOTAL:"
        + "\nRuntime(ms): " + runtime
        + "\nMax mem usage (measured after each pass)(MB): " + maxMem
        + "\n#Runs: " + runs
        + "\n#Changing runs: " + changes + "\n#Loopable runs: " + loopRuns
        + "\n#Changing loopable runs: " + loopChanges + "\nEstimated Reduction(bytes): " + diff
        + "\nEstimated GzReduction(bytes): " + gzDiff + "\nEstimated Size(bytes): " + codeSize
        + "\nEstimated GzSize(bytes): " + gzCodeSize + "\n");

    this.output.print("\nInputs:"
        + "\nJS lines:   " + jsLines
        + "\nJS sources: " + jsSources
        + "\nExtern lines:   " + externLines
        + "\nExtern sources: " + externSources + "\n\n");

    this.output.print("Log:\n"
        + "pass,runtime,allocMem,codeChanged,reduction,gzReduction,size,gzSize\n");
    for (Stats stats : log) {
      this.output.print(SimpleFormat.format("%s,%d,%d,%b,%d,%d,%d,%d\n",
          stats.pass, stats.runtime, stats.allocMem, stats.changes == 1,
          stats.diff, stats.gzDiff, stats.size, stats.gzSize));
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
    public int size;
    public int gzSize;
  }
}
