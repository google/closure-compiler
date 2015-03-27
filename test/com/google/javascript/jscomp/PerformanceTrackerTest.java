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

import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.CompilerOptions.TracerMode;
import com.google.javascript.jscomp.PerformanceTracker.Stats;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import junit.framework.TestCase;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.regex.Pattern;

/**
 * Unit tests for PerformanceTracker.
 *
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public final class PerformanceTrackerTest extends TestCase {
  private Node emptyScript = new Node(Token.SCRIPT);

  public void testStatsCalculation() {
    PerformanceTracker tracker =
        new PerformanceTracker(emptyScript, TracerMode.ALL);
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

    assertEquals(numRuns, 7);
    assertEquals(tracker.getRuntime(), numRuns * passRuntime);
    assertEquals(tracker.getLoopRuns(), 3);
    assertEquals(tracker.getChanges(), 4); /* reportChange was called 4 times */
    assertEquals(tracker.getLoopChanges(), 1);

    ImmutableMap<String, Stats> stats = tracker.getStats();
    Stats st = stats.get("noloopA");
    assertEquals(st.runs, 1);
    assertEquals(st.runtime, passRuntime);
    assertEquals(st.changes, 1);

    st = stats.get("noloopB");
    assertEquals(st.runs, 3);
    assertEquals(st.runtime, 3 * passRuntime);
    assertEquals(st.changes, 2);

    st = stats.get("loopA");
    assertEquals(st.runs, 2);
    assertEquals(st.runtime, 2 * passRuntime);
    assertEquals(st.changes, 1);

    st = stats.get("loopB");
    assertEquals(st.runs, 1);
    assertEquals(st.runtime, passRuntime);
    assertEquals(st.changes, 0);
  }

  public void testOutputFormat() {
    PerformanceTracker tracker =
        new PerformanceTracker(emptyScript, TracerMode.ALL);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    PrintStream outstream = new PrintStream(output);
    tracker.outputTracerReport(outstream);
    outstream.close();
    Pattern p = Pattern.compile(
        ".*Summary:\npass,runtime,runs,changingRuns,reduction,gzReduction" +
        ".*TOTAL:" +
        "\nRuntime\\(ms\\): [0-9]+" +
        "\n#Runs: [0-9]+" +
        "\n#Changing runs: [0-9]+" +
        "\n#Loopable runs: [0-9]+" +
        "\n#Changing loopable runs: [0-9]+" +
        "\nEstimated Reduction\\(bytes\\): [0-9]+" +
        "\nEstimated GzReduction\\(bytes\\): [0-9]+" +
        "\nEstimated Size\\(bytes\\): -?[0-9]+" +
        "\nEstimated GzSize\\(bytes\\): -?[0-9]+" +
        "\n\nLog:\n" +
        "pass,runtime,runs,changingRuns,reduction,gzReduction,size,gzSize.*",
        Pattern.DOTALL);
    String outputString = output.toString();
    assertTrue("Unexpected output from PerformanceTracker:\n" + outputString,
        p.matcher(outputString).matches());
  }
}