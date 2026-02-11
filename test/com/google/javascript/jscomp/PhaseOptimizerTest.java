/*
 * Copyright 2009 The Closure Compiler Authors.
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
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.TracerMode;
import com.google.javascript.jscomp.PhaseOptimizer.Loop;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link PhaseOptimizer}. */
@RunWith(JUnit4.class)
public final class PhaseOptimizerTest {
  private final List<String> passesRun = new ArrayList<>();
  private Node dummyRoot;
  Node dummyScript;
  private PhaseOptimizer optimizer;
  private Compiler compiler;
  private PerformanceTracker tracker;

  @Before
  public void setUp() {
    passesRun.clear();
    Node dummyExternsRoot = new Node(Token.ROOT);
    dummyScript = IR.script();
    dummyRoot = IR.root(dummyScript);
    compiler = new Compiler();
    compiler.initCompilerOptionsIfTesting();
    tracker = new PerformanceTracker(dummyExternsRoot, dummyRoot, TracerMode.TIMING_ONLY);
    optimizer = new PhaseOptimizer(compiler, tracker);
  }

  @Test
  public void testOneRun() {
    addOneTimePass("x");
    assertPasses("x");
  }

  @Test
  public void testLoop1() {
    Loop loop = optimizer.addFixedPointLoop();
    addLoopedPass(loop, "x", 0);
    assertPasses("x");
  }

  @Test
  public void testLoop2() {
    Loop loop = optimizer.addFixedPointLoop();
    addLoopedPass(loop, "x", 3);
    assertPasses("x", "x", "x", "x");
  }

  @Test
  public void testSchedulingOfLoopablePasses() {
    Loop loop = optimizer.addFixedPointLoop();
    addLoopedPass(loop, "x", 3);
    addLoopedPass(loop, "y", 1);
    // The pass iterations can be grouped as: [x y] [x y] [x] [x] [y]
    assertPasses("x", "y", "x", "y", "x", "x", "y");
  }

  @Test
  public void testCapLoopIterations() {
    CompilerOptions options = compiler.getOptions();
    options.setMaxOptimizationLoopIterations(1);
    optimizer = new PhaseOptimizer(compiler, tracker);
    Loop loop = optimizer.addFixedPointLoop();
    addLoopedPass(loop, PassNames.PEEPHOLE_OPTIMIZATIONS, 2);
    assertPasses(PassNames.PEEPHOLE_OPTIMIZATIONS);
  }

  @Test
  public void testNotInfiniteLoop() {
    Loop loop = optimizer.addFixedPointLoop();
    addLoopedPass(loop, "x", PhaseOptimizer.MAX_LOOPS - 2);
    optimizer.process(null, dummyRoot);
    assertWithMessage("There should be no errors.").that(compiler.getErrorCount()).isEqualTo(0);
  }

  @Test
  public void testInfiniteLoop() {
    Loop loop = optimizer.addFixedPointLoop();
    addLoopedPass(loop, "x", PhaseOptimizer.MAX_LOOPS + 1);
    try {
      optimizer.process(null, dummyRoot);
      assertWithMessage("Expected RuntimeException").fail();
    } catch (RuntimeException e) {
      assertWithMessage(e.getMessage())
          .that(e.getMessage().contains(PhaseOptimizer.OPTIMIZE_LOOP_ERROR))
          .isTrue();
    }
  }

  @Test
  public void testSchedulingOfAnyKindOfPasses1() {
    addOneTimePass("a");
    Loop loop = optimizer.addFixedPointLoop();
    addLoopedPass(loop, "x", 3);
    addLoopedPass(loop, "y", 1);
    addOneTimePass("z");
    assertPasses("a", "x", "y", "x", "y", "x", "x", "y", "z");
  }

  @Test
  public void testSchedulingOfAnyKindOfPasses2() {
    optimizer.consume(
        ImmutableList.of(
            createPassFactory("a", 0, true),
            createPassFactory("b", 1, false),
            createPassFactory("c", 2, false),
            createPassFactory("d", 1, false),
            createPassFactory("e", 1, true),
            createPassFactory("f", 0, true)));
    // The pass iterations can be grouped as:
    // 1. [a]  2. [b c d]  3. [b c d]  4. [c]  5. [b]  6. [e]  7. [f]
    // In loop #3, "b" is run and does not make changes, then "c" is run and makes changes, then "d"
    // is run and does not make changes.
    // In loop #4, "c" is run and does not make changes.
    // In loop #5, "b" is run but "d" is not run. This is because the AST changed after the last run
    // of "b" but has not changed after the last run of "d".
    assertPasses("a", "b", "c", "d", "b", "c", "d", "c", "b", "e", "f");
  }

  @Test
  public void testSchedulingOfAnyKindOfPasses3() {
    optimizer.consume(
        ImmutableList.of(
            createPassFactory("a", 2, false),
            createPassFactory("b", 1, true),
            createPassFactory("c", 1, false)));
    assertPasses("a", "a", "a", "b", "c", "c");
  }

  @Test
  public void testSchedulingOfAnyKindOfPasses4() {
    optimizer.consume(
        ImmutableList.of(
            createPassFactory("a", 2, true),
            createPassFactory("b", 0, false),
            createPassFactory("c", 0, false)));
    assertPasses("a", "b", "c");
  }

  @Test
  public void testDuplicateLoop() {
    Loop loop = optimizer.addFixedPointLoop();
    addLoopedPass(loop, "x", 1);
    try {
      addLoopedPass(loop, "x", 1);
      assertWithMessage("Expected exception").fail();
    } catch (IllegalArgumentException e) {
      return;
    }
  }

  @Test
  public void preconditionCheck_success() {
    PassFactory passFactory =
        PassFactory.builder()
            .setName("myPass")
            .setInternalFactory((compiler) -> createPass("myPass", 0))
            .setPreconditionCheck((options) -> new PassFactory.PreconditionResult(true, null))
            .build();
    optimizer.addOneTimePass(passFactory);
    assertPasses("myPass");
  }

  @Test
  public void preconditionCheck_failure() {
    PassFactory passFactory =
        PassFactory.builder()
            .setName("myPass")
            .setInternalFactory((compiler) -> createPass("myPass", 0))
            .setPreconditionCheck((options) -> new PassFactory.PreconditionResult(false, "message"))
            .build();
    optimizer.addOneTimePass(passFactory);

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> assertPasses("myPass"));

    assertThat(ex).hasMessageThat().isEqualTo("Precondition for pass myPass failed: message");
  }

  public void assertPasses(String... names) {
    optimizer.process(null, dummyRoot);
    assertThat(passesRun).isEqualTo(ImmutableList.copyOf(names));
  }

  private void addOneTimePass(String name) {
    optimizer.addOneTimePass(createPassFactory(name, 0, true));
  }

  private void addLoopedPass(Loop loop, String name, int numChanges) {
    loop.addLoopedPass(createPassFactory(name, numChanges, false));
  }

  private PassFactory createPassFactory(String name, int numChanges, boolean isOneTime) {
    return createPassFactory(name, createPass(name, numChanges), isOneTime);
  }

  private PassFactory createPassFactory(String name, final CompilerPass pass, boolean isOneTime) {
    return PassFactory.builder()
        .setName(name)
        .setRunInFixedPointLoop(!isOneTime)
        .setInternalFactory((compiler) -> pass)
        .build();
  }

  private CompilerPass createPass(final String name, int numChanges) {
    final PhaseOptimizerTest self = this;
    final int[] numChangesClosure = new int[] {numChanges};
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        passesRun.add(name);
        if (numChangesClosure[0] > 0) {
          numChangesClosure[0] = numChangesClosure[0] - 1;
          compiler.reportChangeToEnclosingScope(self.dummyScript);
        }
      }
    };
  }
}
