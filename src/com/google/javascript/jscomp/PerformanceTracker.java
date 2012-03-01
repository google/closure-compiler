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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.javascript.jscomp.CodeChangeHandler.RecentChange;
import com.google.javascript.jscomp.CompilerOptions.TracerMode;
import com.google.javascript.rhino.Node;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 */
public class PerformanceTracker {

  private final Node jsRoot;
  private final boolean trackSize;
  private final boolean trackGzippedSize;

  // Keeps track of AST changes and computes code size estimation
  // if there is any.
  private final RecentChange codeChange = new RecentChange();

  private int curCodeSizeEstimate = -1;
  private int curZippedCodeSizeEstimate = -1;

  private Deque<String> currentRunningPass = new ArrayDeque<String>();

  /** Summary stats by pass name. */
  private final Map<String, Stats> summary = Maps.newHashMap();

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
        this.trackGzippedSize = false;
        break;

      case RAW_SIZE:
        this.trackSize = true;
        this.trackGzippedSize = false;
        break;

      case ALL:
        this.trackSize = true;
        this.trackGzippedSize = true;
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
    String currentPassName = currentRunningPass.pop();
    if (!passName.equals(currentPassName)) {
      throw new RuntimeException(passName + " is not running.");
    }

    CodeSizeEstimatePrinter printer = null;
    if (codeChange.hasCodeChanged() && (trackSize || trackGzippedSize)) {
      printer = estimateCodeSize(jsRoot);
    }

    Stats logStats = new Stats(currentPassName);
    log.add(logStats);
    updateStats(logStats, result, printer);

    Stats summaryStats = summary.get(passName);
    if (summaryStats == null) {
      summaryStats = new Stats(passName);
      summary.put(passName, summaryStats);
    }
    updateStats(summaryStats, result, printer);

    if (printer != null) {
      if (trackSize) {
        curCodeSizeEstimate = printer.calcSize();
      }
      if (trackGzippedSize) {
        curZippedCodeSizeEstimate = printer.calcZippedSize();
      }
    }
  }

  private void updateStats(Stats stats,
      long result, CodeSizeEstimatePrinter printer) {
    stats.runtime += result;
    stats.runs += 1;
    if (codeChange.hasCodeChanged()) {
      stats.changes += 1;
    }

    if (printer != null) {
      recordSizeChange(
          curCodeSizeEstimate, printer.calcSize(), stats);
      recordGzSizeChange(
          curZippedCodeSizeEstimate, printer.calcZippedSize(), stats);
    }
  }

  /**
   * Record the size change in the given record for that given pass.
   */
  private static void recordSizeChange(int oldSize, int newSize, Stats record) {
    if (oldSize != -1) {
      int delta = oldSize - newSize;
      if (delta > 0) {
        record.diff += delta;
      }
    }
    if (newSize != -1) {
      record.size = newSize;
    }
  }

  /**
   * Record the gzip size change in the given record for that given pass.
   */
  private static void recordGzSizeChange(
      int oldSize, int newSize, Stats record) {
    if (oldSize != -1) {
      int delta = oldSize - newSize;
      if (delta > 0) {
        record.gzDiff += delta;
      }
    }
    if (newSize != -1) {
      record.gzSize = newSize;
    }
  }

  public ImmutableMap<String, Long> getRuntimeRecord() {
    ImmutableMap.Builder<String, Long> builder =
        new ImmutableMap.Builder<String, Long>();
    for (Map.Entry<String, Stats> entry : summary.entrySet()) {
      builder.put(entry.getKey(), entry.getValue().runtime);
    }
    return builder.build();
  }

  public ImmutableMap<String, Stats> getStats() {
    return ImmutableMap.copyOf(summary);
  }

  public ImmutableList<Stats> getLog() {
    return ImmutableList.copyOf(log);
  }

  public ImmutableMap<String, Integer> getCodeSizeRecord() {
    ImmutableMap.Builder<String, Integer> builder =
      new ImmutableMap.Builder<String, Integer>();
    for (Map.Entry<String, Stats> entry : summary.entrySet()) {
      builder.put(entry.getKey(), entry.getValue().diff);
    }
    return builder.build();
  }

  public ImmutableMap<String, Integer> getZippedCodeSizeRecord() {
    ImmutableMap.Builder<String, Integer> builder =
      new ImmutableMap.Builder<String, Integer>();
    for (Map.Entry<String, Stats> entry : summary.entrySet()) {
      builder.put(entry.getKey(), entry.getValue().gzDiff);
    }
    return builder.build();
  }

  private final CodeSizeEstimatePrinter estimateCodeSize(Node root) {
    CodeSizeEstimatePrinter cp = new CodeSizeEstimatePrinter(trackGzippedSize);
    CodeGenerator cg = new CodeGenerator(cp);
    cg.add(root);
    return cp;
  }

  /**
   * Purely use to get a code size estimate and not generate any code at all.
   */
  private static final class CodeSizeEstimatePrinter extends CodeConsumer {
    private final boolean trackGzippedSize;
    private int size = 0;
    private char lastChar = '\0';
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final GZIPOutputStream stream;

    private CodeSizeEstimatePrinter(boolean trackGzippedSize) {
      this.trackGzippedSize = trackGzippedSize;

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
        if (trackGzippedSize) {
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

    private int calcZippedSize() {
      if (trackGzippedSize) {
        try {
          stream.finish();
          stream.flush();
          stream.close();
          return output.size();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      } else {
        return -1;
      }
    }
  }
}
