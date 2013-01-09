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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.javascript.jscomp.CodeChangeHandler.RecentChange;
import com.google.javascript.jscomp.CompilerOptions.TracerMode;
import com.google.javascript.rhino.Node;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.GZIPOutputStream;

/**
 */
public class PerformanceTracker {

  private final Node jsRoot;
  private final boolean trackSize;
  private final boolean trackGzSize;

  // Keeps track of AST changes and computes code size estimation
  // if there is any.
  private final RecentChange codeChange = new RecentChange();

  private int codeSize = 0;
  private int gzCodeSize = 0;
  private int initCodeSize = 0;
  private int initGzCodeSize = 0;

  private Deque<String> currentRunningPass = new ArrayDeque<String>();

  /** Summary stats by pass name. */
  private final Map<String, Stats> summary = Maps.newHashMap();

  // To share with the rest of the program
  private ImmutableMap<String, Stats> summaryCopy = null;

  /** Stats for each run of a compiler pass. */
  private final List<Stats> log = Lists.newArrayList();


  public static class Stats {
    public Stats(String pass) {
      this.pass = pass;
    }
    public final String pass;
    public long runtime = 0;
    public int runs = 0;
    public int changes = 0;
    public int diff = 0;
    public int gzDiff = 0;
    public int size = 0;
    public int gzSize = 0;
  }

  PerformanceTracker(Node jsRoot, TracerMode mode) {
    this.jsRoot = jsRoot;
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
        throw new UnsupportedOperationException();
    }
  }

  CodeChangeHandler getCodeChangeHandler() {
    return codeChange;
  }

  void recordPassStart(String passName) {
    currentRunningPass.push(passName);
    codeChange.reset();
  }

  /**
   * Record that a pass has stopped.
   *
   * @param passName Short name of the pass.
   * @param result Execution time.
   */
  void recordPassStop(String passName, long result) {
    if (!passName.equals(currentRunningPass.pop())) {
      throw new RuntimeException(passName + " is not running.");
    }

    // After parsing, initialize codeSize and gzCodeSize
    if (passName.equals(Compiler.PARSING_PASS_NAME) && trackSize) {
      CodeSizeEstimatePrinter printer = new CodeSizeEstimatePrinter();
      CodeGenerator.forCostEstimation(printer).add(jsRoot);
      initCodeSize = codeSize = printer.calcSize();
      if (this.trackGzSize) {
        initGzCodeSize = gzCodeSize = printer.calcZippedSize();
      }
    }

    // Initialize logStats and summaryStats
    Stats logStats = new Stats(passName);
    log.add(logStats);
    Stats summaryStats = summary.get(passName);
    if (summaryStats == null) {
      summaryStats = new Stats(passName);
      summary.put(passName, summaryStats);
    }

    // Update fields that aren't related to code size
    logStats.runtime = result;
    logStats.runs = 1;
    summaryStats.runtime += result;
    summaryStats.runs += 1;
    if (codeChange.hasCodeChanged()) {
      logStats.changes = 1;
      summaryStats.changes += 1;
    }

    // Update fields related to code size
    if (codeChange.hasCodeChanged() && trackSize) {
      int newSize = 0;
      CodeSizeEstimatePrinter printer = new CodeSizeEstimatePrinter();
      CodeGenerator.forCostEstimation(printer).add(jsRoot);
      if (trackSize) {
        newSize = printer.calcSize();
        logStats.diff = codeSize - newSize;
        summaryStats.diff += logStats.diff;
        codeSize = summaryStats.size = logStats.size = newSize;
      }
      if (trackGzSize) {
        newSize = printer.calcZippedSize();
        logStats.gzDiff = gzCodeSize - newSize;
        summaryStats.gzDiff += logStats.gzDiff;
        gzCodeSize = summaryStats.gzSize = logStats.gzSize = newSize;
      }
    }
  }

  public ImmutableMap<String, Stats> getStats() {
    if (summaryCopy == null) {
      summaryCopy = ImmutableMap.copyOf(summary);
    }
    return summaryCopy;
  }

  class CmpEntries implements Comparator<Entry<String, Stats>> {
    @Override
    public int compare(Entry<String, Stats> e1, Entry<String, Stats> e2) {
      return (int) (e1.getValue().runtime - e2.getValue().runtime);
    }
  }

  public void outputTracerReport(PrintStream pstr) {
    JvmMetrics.maybeWriteJvmMetrics(pstr, "verbose:pretty:all");
    OutputStreamWriter output = new OutputStreamWriter(pstr);
    try {
      int runtime = 0;
      int runs = 0;
      int changes = 0;
      int diff = 0;
      int gzDiff = 0;

      // header
      output.write("Summary:\n");
      output.write("pass,runtime,runs,changingRuns,reduction,gzReduction\n");

      ArrayList<Entry<String, Stats>> a = new ArrayList<Entry<String, Stats>>();
      for (Entry<String, Stats> entry : summary.entrySet()) {
        a.add(entry);
      }
      Collections.sort(a, new CmpEntries());

      for (Entry<String, Stats> entry : a) {
        String key = entry.getKey();
        Stats stats = entry.getValue();

        output.write(key);
        output.write(",");
        output.write(String.valueOf(stats.runtime));
        runtime += stats.runtime;
        output.write(",");
        output.write(String.valueOf(stats.runs));
        runs += stats.runs;
        output.write(",");
        output.write(String.valueOf(stats.changes));
        changes += stats.changes;
        output.write(",");
        output.write(String.valueOf(stats.diff));
        diff += stats.diff;
        output.write(",");
        output.write(String.valueOf(stats.gzDiff));
        gzDiff += stats.gzDiff;
        output.write("\n");
      }
      Preconditions.checkState(!trackSize || initCodeSize == diff + codeSize);
      Preconditions.checkState(!trackGzSize ||
          initGzCodeSize == gzDiff + gzCodeSize);

      output.write("TOTAL");
      output.write(",");
      output.write(String.valueOf(runtime));
      output.write(",");
      output.write(String.valueOf(runs));
      output.write(",");
      output.write(String.valueOf(changes));
      output.write(",");
      output.write(String.valueOf(diff));
      output.write(",");
      output.write(String.valueOf(gzDiff));
      output.write("\n");
      output.write("\n");

      output.write("Log:\n");
      output.write(
          "pass,runtime,runs,changingRuns,reduction,gzReduction,size,gzSize\n");
      for (Stats stats : log) {
        output.write(stats.pass);
        output.write(",");
        output.write(String.valueOf(stats.runtime));
        output.write(",");
        output.write(String.valueOf(stats.runs));
        output.write(",");
        output.write(String.valueOf(stats.changes));
        output.write(",");
        output.write(String.valueOf(stats.diff));
        output.write(",");
        output.write(String.valueOf(stats.gzDiff));
        output.write(",");
        output.write(String.valueOf(stats.size));
        output.write(",");
        output.write(String.valueOf(stats.gzSize));
        output.write("\n");
      }
      output.write("\n");
      output.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Purely use to get a code size estimate and not generate any code at all.
   */
  private final class CodeSizeEstimatePrinter extends CodeConsumer {
    private int size = 0;
    private char lastChar = '\0';
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final GZIPOutputStream stream;

    private CodeSizeEstimatePrinter() {
      try {
        stream = new GZIPOutputStream(output);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    void append(String str) {
      int len = str.length();
      if (len > 0) {
        size += len;
        lastChar = str.charAt(len - 1);
        if (trackGzSize) {
          try {
            stream.write(str.getBytes());
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      }
    }

    @Override
    char getLastChar() {
      return lastChar;
    }

    private int calcSize() {
      return size;
    }

    // Called iff trackGzSize is true
    private int calcZippedSize() {
      try {
        stream.finish();
        stream.flush();
        stream.close();
        return output.size();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
