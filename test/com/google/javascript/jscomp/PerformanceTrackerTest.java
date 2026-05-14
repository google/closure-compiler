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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.CompilerOptions.TracerMode;
import com.google.javascript.jscomp.PerformanceTracker.Stats;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for PerformanceTracker. */
@RunWith(JUnit4.class)
public final class PerformanceTrackerTest {
  private final Node emptyExternRoot = new Node(Token.BLOCK);
  private final Node emptyJsRoot = new Node(Token.BLOCK);

  @Test
  public void testStatsCalculation() {
    PerformanceTracker tracker =
        new PerformanceTracker(emptyExternRoot, emptyJsRoot, TracerMode.ALL);
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
  public void testAstSummaryAndFormat() {
    // Given
    Node main =
        IR.root(
            IR.script(
                IR.block(
                    IR.block(
                        IR.exprResult( //
                            IR.hook(IR.string("a"), IR.string("b"), IR.string("c")))),
                    IR.function(IR.name("name"), IR.paramList(), IR.block()))));
    PerformanceTracker tracker =
        new PerformanceTracker(emptyExternRoot, main, TracerMode.TIMING_ONLY);

    // When
    tracker.recordPassStart(PassNames.PARSE_INPUTS, true);
    tracker.recordPassStop(PassNames.PARSE_INPUTS, 0); // This is what triggers the counting.
    String report = extractReport(tracker);

    // Then
    assertThat(report)
        .containsMatch(
            Pattern.compile(
                """
                Input AST Manifest:
                token,count
                BLOCK,3
                EXPR_RESULT,1
                FUNCTION,1
                HOOK,1
                NAME,1
                PARAM_LIST,1
                ROOT,1
                SCRIPT,1
                STRINGLIT,3
                """,
                Pattern.DOTALL));
  }

  @Test
  public void testOutputFormat() {
    PerformanceTracker tracker =
        new PerformanceTracker(emptyExternRoot, emptyJsRoot, TracerMode.ALL);
    String report = extractReport(tracker);
    Pattern p =
        Pattern.compile(
            """
            .*TOTAL:
            Start time\\(ms\\): [0-9]+
            End time\\(ms\\): [0-9]+
            Wall time\\(ms\\): [0-9]+
            Passes runtime\\(ms\\): [0-9]+
            Max mem usage \\(measured after each pass\\)\\(MB\\): -?[0-9]+
            #Runs: [0-9]+
            #Changing runs: [0-9]+
            #Loopable runs: [0-9]+
            #Changing loopable runs: [0-9]+
            Estimated AST reduction\\(#nodes\\): [0-9]+
            Estimated Reduction\\(bytes\\): [0-9]+
            Estimated GzReduction\\(bytes\\): [0-9]+
            Estimated AST size\\(#nodes\\): -?[0-9]+
            Estimated Size\\(bytes\\): -?[0-9]+
            Estimated GzSize\\(bytes\\): -?[0-9]+

            DisambiguateProperties: not executed
            AmbiguateProperties: not executed

            Inputs:
            JS lines:\\s*[0-9]+
            JS sources:\\s*[0-9]+
            Extern lines:\\s*[0-9]+
            Extern sources:\\s*[0-9]+
            Type summary lines \\(raw input\\):\\s*[0-9]+
            Type summary lines \\(post-pruning\\):\\s*[0-9]+
            Type summary sources \\(raw input\\):\\s*[0-9]+
            Type summary sources \\(post-pruning\\):\\s*[0-9]+

            Dependency pruning analysis: not executed

            Summary:
            pass,runtime,allocMem,runs,changingRuns,astReduction,reduction,gzReduction

            Log:
            pass,runtime,allocMem,codeChanged,astReduction,reduction,gzReduction,astSize,size,gzSize
            .*
            """,
            Pattern.DOTALL);

    assertThat(report).matches(p);
  }

  @Test
  public void testDistinguishBetweenSrcExternAndIjs() {
    // Add extern .i.js and .js nodes
    emptyExternRoot.addChildToFront(createExtern("externs1.js"));
    emptyExternRoot.addChildToFront(createExtern("externs2.js"));
    emptyExternRoot.addChildToFront(createExtern("externs3.js"));
    emptyExternRoot.addChildToFront(createTypeSummary("type_dep.js.i.js"));
    emptyJsRoot.addChildToFront(createScript("input1.js"));
    emptyJsRoot.addChildToFront(createScript("input2.js"));
    PerformanceTracker tracker =
        new PerformanceTracker(emptyExternRoot, emptyJsRoot, TracerMode.ALL);

    tracker.recordPassStart(PassNames.PARSE_INPUTS, true);
    tracker.recordPassStop(PassNames.PARSE_INPUTS, 0);
    String report = extractReport(tracker);

    Pattern p =
        Pattern.compile(
            """
            JS lines:\\s* 0
            JS sources:\\s* 2
            Extern lines:\\s* 0
            Extern sources:\\s* 3
            Type summary lines \\(raw input\\):\\s* 0
            Type summary lines \\(post-pruning\\):\\s* 0
            Type summary sources \\(raw input\\):\\s* 1
            Type summary sources \\(post-pruning\\):\\s* 1
            """,
            Pattern.DOTALL);

    assertThat(report).containsMatch(p);
  }

  @Test
  public void testRecordPrePruningInputCount_distinguishesPrunedAndNonPrunedIjs() {
    // Create three type summary nodes and inputs, one of which will be pruned.
    SourceFile typeSummary1SourceFile = SourceFile.fromCode("type1.js.i.js", "");
    SourceFile typeSummary2SourceFile = SourceFile.fromCode("type2.js.i.js", "");
    SourceFile typeSummaryPrunedSourceFile =
        SourceFile.fromCode("type_depPruned.js.i.js", "a \n b \n c");
    var typeSummary1Input = new CompilerInput(typeSummary1SourceFile, new InputId("type1.js.i.js"));
    var typeSummary2Input = new CompilerInput(typeSummary2SourceFile, new InputId("type2.js.i.js"));
    var typeSummaryPrunedInput =
        new CompilerInput(typeSummaryPrunedSourceFile, new InputId("pruned.js.i.js"));
    emptyExternRoot.addChildToFront(createTypeSummary("type1.js.i.js"));
    emptyExternRoot.addChildToFront(createTypeSummary("type2.js.i.js"));
    PerformanceTracker tracker =
        new PerformanceTracker(emptyExternRoot, emptyJsRoot, TracerMode.ALL);

    tracker.recordPassStart(PassNames.PARSE_INPUTS, true);
    tracker.recordPrePruningInputCount(
        ImmutableList.of(typeSummary1Input, typeSummaryPrunedInput),
        ImmutableList.of(typeSummary2Input));
    tracker.recordPassStop(PassNames.PARSE_INPUTS, 0);
    String report = extractReport(tracker);

    Pattern p =
        Pattern.compile(
            """
            Type summary lines \\(raw input\\):\\s* 5
            Type summary lines \\(post-pruning\\):\\s* 0
            Type summary sources \\(raw input\\):\\s* 3
            Type summary sources \\(post-pruning\\):\\s* 2
            """,
            Pattern.DOTALL);

    assertThat(report).containsMatch(p);
  }

  private static Node createScript(String sourceFileName) {
    SourceFile sourceFile = SourceFile.fromCode(sourceFileName, "");
    var script = IR.script();
    script.setStaticSourceFile(sourceFile);
    return script;
  }

  private static Node createTypeSummary(String sourceFileName) {
    var script = createScript(sourceFileName);
    JSDocInfo.Builder scriptInfo = JSDocInfo.builder();
    scriptInfo.recordTypeSummary();
    script.setJSDocInfo(scriptInfo.build());
    return script;
  }

  private static Node createExtern(String sourceFileName) {
    var script = createScript(sourceFileName);
    JSDocInfo.Builder scriptInfo = JSDocInfo.builder();
    scriptInfo.recordExterns();
    script.setJSDocInfo(scriptInfo.build());
    return script;
  }

  private static final String extractReport(PerformanceTracker tracker) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (PrintStream outstream = new PrintStream(output)) {
      tracker.outputTracerReport(outstream);
    }
    return output.toString(UTF_8);
  }
}
