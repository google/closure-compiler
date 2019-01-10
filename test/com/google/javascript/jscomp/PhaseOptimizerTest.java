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

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.TracerMode;
import com.google.javascript.jscomp.PhaseOptimizer.Loop;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link PhaseOptimizer}.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
@RunWith(JUnit4.class)
public final class PhaseOptimizerTest {
  private final List<String> passesRun = new ArrayList<>();
  private Node dummyExternsRoot;
  private Node dummyRoot;
  Node dummyScript;
  private PhaseOptimizer optimizer;
  private Compiler compiler;
  private PerformanceTracker tracker;

  @Before
  public void setUp() {
    passesRun.clear();
    dummyExternsRoot = new Node(Token.ROOT);
    dummyScript = IR.script();
    dummyRoot = IR.root(dummyScript);
    compiler = new Compiler();
    compiler.initCompilerOptionsIfTesting();
    tracker = new PerformanceTracker(dummyExternsRoot, dummyRoot, TracerMode.TIMING_ONLY, null);
    optimizer = new PhaseOptimizer(compiler, tracker);
    compiler.setPhaseOptimizer(optimizer);
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
    options.optimizationLoopMaxIterations = 1;
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
    // [a] [b c d] [b c d] [c] [b d] [e] [f]
    assertPasses("a", "b", "c", "d", "b", "c", "d", "c", "b", "d", "e", "f");
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
  public void testPassOrdering() {
    Loop loop = optimizer.addFixedPointLoop();
    List<String> optimalOrder = new ArrayList<>(
        PhaseOptimizer.OPTIMAL_ORDER);
    Random random = new Random();
    while (!optimalOrder.isEmpty()) {
      addLoopedPass(
          loop, optimalOrder.remove(random.nextInt(optimalOrder.size())), 0);
    }
    optimizer.process(null, dummyRoot);
    assertThat(passesRun).isEqualTo(PhaseOptimizer.OPTIMAL_ORDER);
  }

  @Test
  public void testProgress() {
    final List<Double> progressList = new ArrayList<>();
    compiler = new Compiler() {
      @Override void setProgress(double p, String name) {
        progressList.add(p);
      }
    };
    compiler.initCompilerOptionsIfTesting();
    optimizer = new PhaseOptimizer(compiler, null).withProgress(
        new PhaseOptimizer.ProgressRange(0, 100));
    addOneTimePass("x1");
    addOneTimePass("x2");
    addOneTimePass("x3");
    addOneTimePass("x4");
    optimizer.process(null, dummyRoot);
    assertThat(progressList).hasSize(4);
    assertThat(Math.round(progressList.get(0))).isEqualTo(25);
    assertThat(Math.round(progressList.get(1))).isEqualTo(50);
    assertThat(Math.round(progressList.get(2))).isEqualTo(75);
    assertThat(Math.round(progressList.get(3))).isEqualTo(100);
  }

  @Test
  public void testSetSkipUnsupportedPasses() {
    compiler.getOptions().setSkipUnsupportedPasses(true);
    addUnsupportedPass("testPassFactory");
    assertPasses();
  }

  @Test
  public void testSetDontSkipUnsupportedPasses() {
    compiler.getOptions().setSkipUnsupportedPasses(false);
    addUnsupportedPass("testPassFactory");
    assertPasses("testPassFactory");
  }

  public void assertPasses(String ... names) {
    optimizer.process(null, dummyRoot);
    assertThat(passesRun).isEqualTo(ImmutableList.copyOf(names));
  }

  private void addOneTimePass(String name) {
    optimizer.addOneTimePass(
        createPassFactory(name, 0, true));
  }

  private void addLoopedPass(Loop loop, String name, int numChanges) {
    loop.addLoopedPass(
        createPassFactory(name, numChanges, false));
  }

  /** Adds a pass with the given name that does not support some of the features used in the AST. */
  private void addUnsupportedPass(String name) {
    compiler.setFeatureSet(FeatureSet.latest());
    optimizer.addOneTimePass(
        createPassFactory(name, createPass(name, 0), true, FeatureSet.BARE_MINIMUM));
  }

  private PassFactory createPassFactory(
      String name, int numChanges, boolean isOneTime) {
    return createPassFactory(name, createPass(name, numChanges), isOneTime);
  }

  private PassFactory createPassFactory(
      String name, final CompilerPass pass, boolean isOneTime) {
    return createPassFactory(name, pass, isOneTime, FeatureSet.latest());
  }

  private PassFactory createPassFactory(
      String name, final CompilerPass pass, boolean isOneTime, FeatureSet featureSet) {
    return new PassFactory(name, isOneTime) {
      @Override
      protected CompilerPass create(AbstractCompiler compiler) {
        return pass;
      }

      @Override
      public FeatureSet featureSet() {
        return featureSet;
      }
    };
  }

  private CompilerPass createPass(final String name, int numChanges) {
    final PhaseOptimizerTest self = this;
    final int[] numChangesClosure = new int[] {numChanges};
    return new CompilerPass() {
      @Override public void process(Node externs, Node root) {
        passesRun.add(name);
        if (numChangesClosure[0] > 0) {
          numChangesClosure[0] = numChangesClosure[0] - 1;
          compiler.reportChangeToEnclosingScope(self.dummyScript);
        }
      }
    };
  }
}
