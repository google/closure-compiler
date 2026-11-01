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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.deps.SortedDependencies;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class PruningAnalysisTest {

  private Compiler compiler;

  @Before
  public void setUp() {
    compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
  }

  @Test
  public void testNoBloat() {
    CompilerInput root1 = createInput("root1.js", "goog.provide('root1'); goog.require('dep1');");
    CompilerInput dep1 = createInput("dep1.js", "goog.provide('dep1');");
    CompilerInput root2 = createInput("root2.js", "goog.provide('root2'); goog.require('dep2');");
    CompilerInput dep2 = createInput("dep2.js", "goog.provide('dep2');");

    PruningAnalysis.Result result =
        analyze(ImmutableList.of(root1, root2), root1, dep1, root2, dep2);

    assertThat(result.entryPointDependencyCount()).containsExactly("root1.js", 2, "root2.js", 2);
  }

  @Test
  public void testSharedDependencies() {
    CompilerInput root1 =
        createInput("root1.js", "goog.provide('root1'); goog.require('s1'); goog.require('s2');");
    CompilerInput root2 =
        createInput("root2.js", "goog.provide('root2'); goog.require('s1'); goog.require('s3');");
    CompilerInput root3 =
        createInput(
            "root3.js",
            "goog.provide('root3'); goog.require('s1'); goog.require('s2'); goog.require('s3');");
    CompilerInput s1 = createInput("s1.js", "goog.provide('s1');");
    CompilerInput s2 = createInput("s2.js", "goog.provide('s2');");
    CompilerInput s3 = createInput("s3.js", "goog.provide('s3');");

    PruningAnalysis.Result result =
        analyze(ImmutableList.of(root1, root2, root3), root1, root2, root3, s1, s2, s3);

    assertThat(result.entryPointDependencyCount())
        .containsAtLeast("root1.js", 3, "root2.js", 3, "root3.js", 4);
  }

  @Test
  public void testWeakDependencies_included() {
    CompilerInput root1 =
        createInput("root1.js", "goog.provide('root1'); goog.requireType('weak1');");
    CompilerInput weak1 = createInput("weak1.js", "goog.provide('weak1');");

    PruningAnalysis.Result result = analyze(ImmutableList.of(root1), root1, weak1);

    assertThat(result.entryPointDependencyCount()).containsAtLeast("root1.js", 2);
  }

  @Test
  public void testForwardDeclare_included() {
    CompilerInput root1 =
        createInput(
            "root1.js",
            "goog.provide('root1'); goog.forwardDeclare('fwdDeclared');"
                + " goog.forwardDeclare('missing');");
    CompilerInput fwdDeclared = createInput("fwdDeclared.js", "goog.provide('fwdDeclared');");

    PruningAnalysis.Result result = analyze(ImmutableList.of(root1), root1, fwdDeclared);

    assertThat(result.entryPointDependencyCount()).containsAtLeast("root1.js", 2);
  }

  @Test
  public void testOverlappingEntryPoints() {
    CompilerInput entry1 = createInput("entry1.js", "goog.require('dep1');");
    CompilerInput entry2 = createInput("entry2.js", "goog.require('dep2');");
    CompilerInput dep1 =
        createInput(
            "dep1.js", "goog.provide('dep1'); goog.require('dep2'); goog.require('excl1');");
    CompilerInput dep2 = createInput("dep2.js", "goog.provide('dep2'); goog.require('excl2');");
    CompilerInput excl1 = createInput("excl1.js", "goog.provide('excl1');");
    CompilerInput excl2 = createInput("excl2.js", "goog.provide('excl2');");

    PruningAnalysis.Result result =
        analyze(ImmutableList.of(entry1, entry2), entry1, entry2, dep1, dep2, excl1, excl2);

    // Symbols are entry1.js and entry2.js.
    // entry1.js closure: {entry1.js, dep1.js, dep2.js excl1.js, excl2.js}
    // entry2.js closure: {entry2.js, dep2.js, excl2.js}
    assertThat(result.entryPointDependencyCount()).containsAtLeast("entry1.js", 5, "entry2.js", 3);
  }

  @Test
  public void testMultipleEntryPoints() {
    CompilerInput root1 = createInput("root1.js", "goog.require('dep1');");
    CompilerInput root2 = createInput("root2.js", "goog.require('dep2');");
    CompilerInput dep1 = createInput("dep1.js", "goog.provide('dep1');");
    CompilerInput dep2 = createInput("dep2.js", "goog.provide('dep2');");

    PruningAnalysis.Result result =
        analyze(ImmutableList.of(root1, root2), root1, root2, dep1, dep2);

    assertThat(result.entryPointDependencyCount()).containsAtLeast("root1.js", 2, "root2.js", 2);
  }

  @Test
  public void testWeakCycle() {
    CompilerInput dep1 = createInput("dep1.js", "goog.provide('dep1'); goog.require('dep2');");
    CompilerInput dep2 = createInput("dep2.js", "goog.provide('dep2'); goog.requireType('root1');");
    CompilerInput root = createInput("root.js", "goog.require('dep1');");

    PruningAnalysis.Result result = analyze(ImmutableList.of(root), root, dep1, dep2);

    assertThat(result.entryPointDependencyCount()).containsAtLeast("root.js", 3);
  }

  @Test
  public void testBottlenecks() {
    // entry -> A -> B -> {C, D, E}
    CompilerInput a = createInput("a.js", "goog.provide('a'); goog.require('b');");
    CompilerInput b =
        createInput(
            "b.js", "goog.provide('b'); goog.require('c'); goog.require('d'); goog.require('e');");
    CompilerInput c = createInput("c.js", "goog.provide('c');");
    CompilerInput d = createInput("d.js", "goog.provide('d');");
    CompilerInput e = createInput("e.js", "goog.provide('e');");
    CompilerInput entry = createInput("entry.js", "goog.require('a');");

    PruningAnalysis.Result result = analyze(ImmutableList.of(entry), a, b, c, d, e, entry);

    // Bottleneck scores (dominated subtree size):
    // a.js: dominates {a, b, c, d, e}. Size = 5.
    // b.js: dominates {b, c, d, e}. Size = 4.
    // c.js, d.js, e.js: Size = 1.
    assertThat(result.bottleneckBlame()).containsEntry("a.js", 5);
    assertThat(result.bottleneckBlame()).containsEntry("b.js", 4);
    assertThat(result.bottleneckBlame()).doesNotContainEntry("c.js", 1);
  }

  @Test
  public void testSharedBottleneckInAnalysis() {
    // entry1 -> B, entry2 -> B, B -> {C, D}
    CompilerInput b =
        createInput("b.js", "goog.provide('b'); goog.require('c'); goog.require('d');");
    CompilerInput c = createInput("c.js", "goog.provide('c');");
    CompilerInput d = createInput("d.js", "goog.provide('d');");
    CompilerInput entry1 = createInput("entry1.js", "goog.require('b');");
    CompilerInput entry2 = createInput("entry2.js", "goog.require('b');");

    PruningAnalysis.Result result =
        analyze(ImmutableList.of(entry1, entry2), b, c, d, entry1, entry2);

    // entry1.js and entry2.js are roots.
    // Virtual root -> {entry1.js, entry2.js}
    // entry1.js -> b.js
    // entry2.js -> b.js
    // b.js -> {c.js, d.js}
    // IDom of b.js is Virtual Root.
    // b.js dominates {b.js, c.js, d.js}. Size = 3.
    assertThat(result.bottleneckBlame()).containsEntry("b.js", 3);
  }

  private CompilerInput createInput(String name, String code) {
    CompilerInput input = new CompilerInput(SourceFile.fromCode(name, code));
    input.setCompiler(compiler);
    return input;
  }

  private PruningAnalysis.Result analyze(
      ImmutableList<CompilerInput> entryPoints, CompilerInput... allInputs) {
    ImmutableList<CompilerInput> inputList = ImmutableList.copyOf(allInputs);
    SortedDependencies<CompilerInput> sorter = new SortedDependencies<>(inputList);
    return PruningAnalysis.create(sorter, entryPoints).analyze();
  }
}
