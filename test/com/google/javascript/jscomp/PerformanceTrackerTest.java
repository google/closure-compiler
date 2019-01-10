/*
 * Copyright 2013 The Closure Compiler Authors.
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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.CompilerOptions.TracerMode;
import com.google.javascript.jscomp.PerformanceTracker.Stats;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for PerformanceTracker.
 *
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
@RunWith(JUnit4.class)
public final class PerformanceTrackerTest {
  private final Node emptyExternRoot = new Node(Token.BLOCK);
  private final Node emptyJsRoot = new Node(Token.BLOCK);

  @Test
  public void testStatsCalculation() {
    PerformanceTracker tracker =
        new PerformanceTracker(emptyExternRoot, emptyJsRoot, TracerMode.ALL, null);
    CodeChangeHandler handler = tracker.getCodeChangeHandler();

    // It's sufficient for this test to assume that a single run of any pass
    // takes some fixed amount of time, say 5ms.
    int passRuntime = 5;

    tracker.recordPassStart("noloopA", true);
    handler.reportChange();
    tracker.recordPassStop("noloopA", passRuntime);

    tracker.recordPassStart("noloopB", true);
    handler.reportChange();
    tracker.recordPassStop("noloopB", passRuntime);

    tracker.recordPassStart("loopA", false);
    handler.reportChange();
    tracker.recordPassStop("loopA", passRuntime);

    tracker.recordPassStart("loopA", false);
    tracker.recordPassStop("loopA", passRuntime);

    tracker.recordPassStart("noloopB", true);
    handler.reportChange();
    tracker.recordPassStop("noloopB", passRuntime);

    tracker.recordPassStart("loopB", false);
    tracker.recordPassStop("loopB", passRuntime);

    tracker.recordPassStart("noloopB", true);
    tracker.recordPassStop("noloopB", passRuntime);

    int numRuns = tracker.getRuns();

    assertThat(numRuns).isEqualTo(7);
    assertThat(numRuns * passRuntime).isEqualTo(tracker.getRuntime());
    assertThat(tracker.getLoopRuns()).isEqualTo(3);
    assertThat(tracker.getChanges()).isEqualTo(4); /* reportChange was called 4 times */
    assertThat(tracker.getLoopChanges()).isEqualTo(1);

    ImmutableMap<String, Stats> stats = tracker.getStats();
    Stats st = stats.get("noloopA");
    assertThat(st.runs).isEqualTo(1);
    assertThat(passRuntime).isEqualTo(st.runtime);
    assertThat(st.changes).isEqualTo(1);

    st = stats.get("noloopB");
    assertThat(st.runs).isEqualTo(3);
    assertThat(3 * passRuntime).isEqualTo(st.runtime);
    assertThat(st.changes).isEqualTo(2);

    st = stats.get("loopA");
    assertThat(st.runs).isEqualTo(2);
    assertThat(2 * passRuntime).isEqualTo(st.runtime);
    assertThat(st.changes).isEqualTo(1);

    st = stats.get("loopB");
    assertThat(st.runs).isEqualTo(1);
    assertThat(passRuntime).isEqualTo(st.runtime);
    assertThat(st.changes).isEqualTo(0);
  }

  @Test
  public void testOutputFormat() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (PrintStream outstream = new PrintStream(output)) {
      PerformanceTracker tracker =
          new PerformanceTracker(emptyExternRoot, emptyJsRoot, TracerMode.ALL, outstream);
      tracker.outputTracerReport();
    }
    Pattern p = Pattern.compile(Joiner.on("\n").join(
        ".*TOTAL:",
        "Start time\\(ms\\): [0-9]+",
        "End time\\(ms\\): [0-9]+",
        "Wall time\\(ms\\): [0-9]+",
        "Passes runtime\\(ms\\): [0-9]+",
        "Max mem usage \\(measured after each pass\\)\\(MB\\): -?[0-9]+",
        "#Runs: [0-9]+",
        "#Changing runs: [0-9]+",
        "#Loopable runs: [0-9]+",
        "#Changing loopable runs: [0-9]+",
        "Estimated AST reduction\\(#nodes\\): [0-9]+",
        "Estimated Reduction\\(bytes\\): [0-9]+",
        "Estimated GzReduction\\(bytes\\): [0-9]+",
        "Estimated AST size\\(#nodes\\): -?[0-9]+",
        "Estimated Size\\(bytes\\): -?[0-9]+",
        "Estimated GzSize\\(bytes\\): -?[0-9]+",
        "",
        "Inputs:",
        "JS lines:   [0-9]+",
        "JS sources: [0-9]+",
        "Extern lines:   [0-9]+",
        "Extern sources: [0-9]+",
        "",
        "Summary:",
        "pass,runtime,allocMem,runs,changingRuns,astReduction,reduction,gzReduction",
        "",
        "Log:",
        "pass,runtime,allocMem,codeChanged,astReduction,reduction,gzReduction,astSize,size,gzSize",
        "",
        ".*"),
        Pattern.DOTALL);
    String outputString = output.toString();
    assertThat(outputString).matches(p);
  }
}
