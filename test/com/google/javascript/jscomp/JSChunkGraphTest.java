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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.base.JSCompStrings.lines;
import static java.util.Collections.shuffle;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.javascript.jscomp.deps.DependencyInfo.Require;
import com.google.javascript.rhino.StaticSourceFile.SourceKind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.nullness.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link JSChunkGraph} */
@RunWith(JUnit4.class)
public final class JSChunkGraphTest {

  private JSChunk chunkA;
  private JSChunk chunkB;
  private JSChunk chunkC;
  private JSChunk chunkD;
  private JSChunk chunkE;
  private JSChunk chunkF;
  private @Nullable JSChunkGraph graph = null;

  // For resolving dependencies only.
  private Compiler compiler;

  @Before
  public void setUp() throws Exception {
    compiler = new Compiler();
  }

  private void makeDeps() {
    chunkA = new JSChunk("chunkA");
    chunkB = new JSChunk("chunkB");
    chunkC = new JSChunk("chunkC");
    chunkD = new JSChunk("chunkD");
    chunkE = new JSChunk("chunkE");
    chunkF = new JSChunk("chunkF");
    chunkB.addDependency(chunkA); //     __A__
    chunkC.addDependency(chunkA); //    /  |  \
    chunkD.addDependency(chunkB); //   B   C  |
    chunkE.addDependency(chunkB); //  / \ /|  |
    chunkE.addDependency(chunkC); // D   E | /
    chunkF.addDependency(chunkA); //      \|/
    chunkF.addDependency(chunkC); //       F
    chunkF.addDependency(chunkE);
  }

  private void makeGraph() {
    graph = new JSChunkGraph(new JSChunk[] {chunkA, chunkB, chunkC, chunkD, chunkE, chunkF});
  }

  private JSChunk getWeakModule() {
    return graph.getChunkByName(JSChunk.WEAK_CHUNK_NAME);
  }

  @Test
  public void testMakesWeakModuleIfNotPassed() {
    makeDeps();
    makeGraph();
    assertThat(graph.getChunkCount()).isEqualTo(7);
    assertThat(graph.getChunksByName()).containsKey(JSChunk.WEAK_CHUNK_NAME);
    assertThat(getWeakModule().getAllDependencies())
        .containsExactly(chunkA, chunkB, chunkC, chunkD, chunkE, chunkF);
  }

  @Test
  public void testAcceptExistingWeakModule() {
    makeDeps();
    JSChunk weakChunk = new JSChunk(JSChunk.WEAK_CHUNK_NAME);

    weakChunk.addDependency(chunkA);
    weakChunk.addDependency(chunkB);
    weakChunk.addDependency(chunkC);
    weakChunk.addDependency(chunkD);
    weakChunk.addDependency(chunkE);
    weakChunk.addDependency(chunkF);

    weakChunk.add(SourceFile.fromCode("weak", "", SourceKind.WEAK));

    JSChunkGraph graph =
        new JSChunkGraph(new JSChunk[] {chunkA, chunkB, chunkC, chunkD, chunkE, chunkF, weakChunk});

    assertThat(graph.getChunkCount()).isEqualTo(7);
    assertThat(graph.getChunkByName(JSChunk.WEAK_CHUNK_NAME)).isSameInstanceAs(weakChunk);
  }

  @Test
  public void testExistingWeakModuleMustHaveDependenciesOnAllOtherModules() {
    makeDeps();
    JSChunk weakChunk = new JSChunk(JSChunk.WEAK_CHUNK_NAME);

    weakChunk.addDependency(chunkA);
    weakChunk.addDependency(chunkB);
    weakChunk.addDependency(chunkC);
    weakChunk.addDependency(chunkD);
    weakChunk.addDependency(chunkE);
    // Missing F

    IllegalStateException e =
        assertThrows(
            IllegalStateException.class,
            () ->
                new JSChunkGraph(
                    new JSChunk[] {chunkA, chunkB, chunkC, chunkD, chunkE, chunkF, weakChunk}));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo("A weak chunk already exists but it does not depend on every other chunk.");
  }

  @Test
  public void testWeakFileCannotExistOutsideWeakModule() {
    makeDeps();
    JSChunk weakChunk = new JSChunk(JSChunk.WEAK_CHUNK_NAME);

    weakChunk.addDependency(chunkA);
    weakChunk.addDependency(chunkB);
    weakChunk.addDependency(chunkC);
    weakChunk.addDependency(chunkD);
    weakChunk.addDependency(chunkE);
    weakChunk.addDependency(chunkF);

    chunkA.add(SourceFile.fromCode("a", "", SourceKind.WEAK));

    IllegalStateException e =
        assertThrows(
            IllegalStateException.class,
            () ->
                new JSChunkGraph(
                    new JSChunk[] {chunkA, chunkB, chunkC, chunkD, chunkE, chunkF, weakChunk}));

    assertThat(e)
        .hasMessageThat()
        .contains("Found these weak sources in other chunks:\n  a (in chunk chunkA)");
  }

  @Test
  public void testStrongFileCannotExistInWeakModule() {
    makeDeps();
    JSChunk weakChunk = new JSChunk(JSChunk.WEAK_CHUNK_NAME);

    weakChunk.addDependency(chunkA);
    weakChunk.addDependency(chunkB);
    weakChunk.addDependency(chunkC);
    weakChunk.addDependency(chunkD);
    weakChunk.addDependency(chunkE);
    weakChunk.addDependency(chunkF);

    weakChunk.add(SourceFile.fromCode("a", "", SourceKind.STRONG));

    IllegalStateException e =
        assertThrows(
            IllegalStateException.class,
            () ->
                new JSChunkGraph(
                    new JSChunk[] {chunkA, chunkB, chunkC, chunkD, chunkE, chunkF, weakChunk}));
    ;
    assertThat(e).hasMessageThat().contains("Found these strong sources in the weak chunk:\n  a");
  }

  @Test
  public void testSmallerTreeBeatsDeeperTree() {
    final JSChunk a = new JSChunk("a");
    final JSChunk b = new JSChunk("b");
    final JSChunk c = new JSChunk("c");
    final JSChunk d = new JSChunk("d");
    final JSChunk e = new JSChunk("e");
    final JSChunk f = new JSChunk("f");
    final JSChunk g = new JSChunk("g");
    final JSChunk h = new JSChunk("h");
    //   a
    //  / \
    // b   c
    b.addDependency(a);
    c.addDependency(a);
    //   b
    //  /|\
    // e f g
    e.addDependency(b);
    f.addDependency(b);
    g.addDependency(b);
    //     c
    //     |
    //     d
    //   // \\
    //  / | | \
    // e  f g  h
    d.addDependency(c);
    e.addDependency(d);
    f.addDependency(d);
    g.addDependency(d);
    h.addDependency(d);
    JSChunkGraph graph = new JSChunkGraph(new JSChunk[] {a, b, c, d, e, f, g, h});
    // d is deeper, but it also has an extra dependent node, so b is the better choice.
    assertSmallestCoveringSubtree(b, graph, a, e, f, g);
    // However, if the parent tree we're looking at is c, then b isn't an option
    assertSmallestCoveringSubtree(d, graph, c, e, f, g);
  }

  @Test
  public void testModuleDepth() {
    makeDeps();
    makeGraph();
    assertWithMessage("chunkA should have depth 0").that(chunkA.getDepth()).isEqualTo(0);
    assertWithMessage("chunkB should have depth 1").that(chunkB.getDepth()).isEqualTo(1);
    assertWithMessage("chunkC should have depth 1").that(chunkC.getDepth()).isEqualTo(1);
    assertWithMessage("chunkD should have depth 2").that(chunkD.getDepth()).isEqualTo(2);
    assertWithMessage("chunkE should have depth 2").that(chunkE.getDepth()).isEqualTo(2);
    assertWithMessage("chunkF should have depth 3").that(chunkF.getDepth()).isEqualTo(3);
  }

  @Test
  public void testDeepestCommonDep() {
    makeDeps();
    makeGraph();
    assertDeepestCommonDep(null, chunkA, chunkA);
    assertDeepestCommonDep(null, chunkA, chunkB);
    assertDeepestCommonDep(null, chunkA, chunkC);
    assertDeepestCommonDep(null, chunkA, chunkD);
    assertDeepestCommonDep(null, chunkA, chunkE);
    assertDeepestCommonDep(null, chunkA, chunkF);
    assertDeepestCommonDep(chunkA, chunkB, chunkB);
    assertDeepestCommonDep(chunkA, chunkB, chunkC);
    assertDeepestCommonDep(chunkA, chunkB, chunkD);
    assertDeepestCommonDep(chunkA, chunkB, chunkE);
    assertDeepestCommonDep(chunkA, chunkB, chunkF);
    assertDeepestCommonDep(chunkA, chunkC, chunkC);
    assertDeepestCommonDep(chunkA, chunkC, chunkD);
    assertDeepestCommonDep(chunkA, chunkC, chunkE);
    assertDeepestCommonDep(chunkA, chunkC, chunkF);
    assertDeepestCommonDep(chunkB, chunkD, chunkD);
    assertDeepestCommonDep(chunkB, chunkD, chunkE);
    assertDeepestCommonDep(chunkB, chunkD, chunkF);
    assertDeepestCommonDep(chunkC, chunkE, chunkE);
    assertDeepestCommonDep(chunkC, chunkE, chunkF);
    assertDeepestCommonDep(chunkE, chunkF, chunkF);
  }

  @Test
  public void testDeepestCommonDepInclusive() {
    makeDeps();
    makeGraph();
    assertDeepestCommonDepInclusive(chunkA, chunkA, chunkA);
    assertDeepestCommonDepInclusive(chunkA, chunkA, chunkB);
    assertDeepestCommonDepInclusive(chunkA, chunkA, chunkC);
    assertDeepestCommonDepInclusive(chunkA, chunkA, chunkD);
    assertDeepestCommonDepInclusive(chunkA, chunkA, chunkE);
    assertDeepestCommonDepInclusive(chunkA, chunkA, chunkF);
    assertDeepestCommonDepInclusive(chunkB, chunkB, chunkB);
    assertDeepestCommonDepInclusive(chunkA, chunkB, chunkC);
    assertDeepestCommonDepInclusive(chunkB, chunkB, chunkD);
    assertDeepestCommonDepInclusive(chunkB, chunkB, chunkE);
    assertDeepestCommonDepInclusive(chunkB, chunkB, chunkF);
    assertDeepestCommonDepInclusive(chunkC, chunkC, chunkC);
    assertDeepestCommonDepInclusive(chunkA, chunkC, chunkD);
    assertDeepestCommonDepInclusive(chunkC, chunkC, chunkE);
    assertDeepestCommonDepInclusive(chunkC, chunkC, chunkF);
    assertDeepestCommonDepInclusive(chunkD, chunkD, chunkD);
    assertDeepestCommonDepInclusive(chunkB, chunkD, chunkE);
    assertDeepestCommonDepInclusive(chunkB, chunkD, chunkF);
    assertDeepestCommonDepInclusive(chunkE, chunkE, chunkE);
    assertDeepestCommonDepInclusive(chunkE, chunkE, chunkF);
    assertDeepestCommonDepInclusive(chunkF, chunkF, chunkF);
  }

  @Test
  public void testSmallestCoveringSubtree() {
    makeDeps();
    makeGraph();
    assertSmallestCoveringSubtree(chunkA, chunkA, chunkA, chunkA);
    assertSmallestCoveringSubtree(chunkA, chunkA, chunkA, chunkB);
    assertSmallestCoveringSubtree(chunkA, chunkA, chunkA, chunkC);
    assertSmallestCoveringSubtree(chunkA, chunkA, chunkA, chunkD);
    assertSmallestCoveringSubtree(chunkA, chunkA, chunkA, chunkE);
    assertSmallestCoveringSubtree(chunkA, chunkA, chunkA, chunkF);
    assertSmallestCoveringSubtree(chunkB, chunkA, chunkB, chunkB);
    assertSmallestCoveringSubtree(chunkA, chunkA, chunkB, chunkC);
    assertSmallestCoveringSubtree(chunkB, chunkA, chunkB, chunkD);
    assertSmallestCoveringSubtree(chunkB, chunkA, chunkB, chunkE);
    assertSmallestCoveringSubtree(chunkB, chunkA, chunkB, chunkF);
    assertSmallestCoveringSubtree(chunkC, chunkA, chunkC, chunkC);
    assertSmallestCoveringSubtree(chunkA, chunkA, chunkC, chunkD);
    assertSmallestCoveringSubtree(chunkC, chunkA, chunkC, chunkE);
    assertSmallestCoveringSubtree(chunkC, chunkA, chunkC, chunkF);
    assertSmallestCoveringSubtree(chunkD, chunkA, chunkD, chunkD);
    assertSmallestCoveringSubtree(chunkB, chunkA, chunkD, chunkE);
    assertSmallestCoveringSubtree(chunkB, chunkA, chunkD, chunkF);
    assertSmallestCoveringSubtree(chunkE, chunkA, chunkE, chunkE);
    assertSmallestCoveringSubtree(chunkE, chunkA, chunkE, chunkF);
    assertSmallestCoveringSubtree(chunkF, chunkA, chunkF, chunkF);
  }

  @Test
  public void testGetTransitiveDepsDeepestFirst() {
    makeDeps();
    makeGraph();
    assertTransitiveDepsDeepestFirst(chunkA);
    assertTransitiveDepsDeepestFirst(chunkB, chunkA);
    assertTransitiveDepsDeepestFirst(chunkC, chunkA);
    assertTransitiveDepsDeepestFirst(chunkD, chunkB, chunkA);
    assertTransitiveDepsDeepestFirst(chunkE, chunkC, chunkB, chunkA);
    assertTransitiveDepsDeepestFirst(chunkF, chunkE, chunkC, chunkB, chunkA);
  }

  @Test
  public void testManageDependenciesLooseWithoutEntryPoint() throws Exception {
    makeDeps();
    makeGraph();
    setUpManageDependenciesTest();
    DependencyOptions depOptions = DependencyOptions.pruneLegacyForEntryPoints(ImmutableList.of());
    ImmutableList<CompilerInput> results = graph.manageDependencies(compiler, depOptions);

    assertInputs(chunkA, "a1", "a3");
    assertInputs(chunkB, "a2", "b2");
    assertInputs(chunkC); // no inputs
    assertInputs(chunkE, "c1", "e1", "e2");

    assertThat(sourceNames(results))
        .isEqualTo(ImmutableList.of("a1", "a3", "a2", "b2", "c1", "e1", "e2"));
  }

  @Test
  public void testManageDependenciesLooseWithEntryPoint() throws Exception {
    makeDeps();
    makeGraph();
    setUpManageDependenciesTest();
    DependencyOptions depOptions =
        DependencyOptions.pruneLegacyForEntryPoints(
            ImmutableList.of(ModuleIdentifier.forClosure("c2")));
    ImmutableList<CompilerInput> results = graph.manageDependencies(compiler, depOptions);

    assertInputs(chunkA, "a1", "a3");
    assertInputs(chunkB, "a2", "b2");
    assertInputs(chunkC, "c1", "c2");
    assertInputs(chunkE, "e1", "e2");

    assertThat(sourceNames(results))
        .isEqualTo(ImmutableList.of("a1", "a3", "a2", "b2", "c1", "c2", "e1", "e2"));
  }

  @Test
  public void testManageDependenciesStrictWithEntryPoint() throws Exception {
    makeDeps();
    makeGraph();
    setUpManageDependenciesTest();
    DependencyOptions depOptions =
        DependencyOptions.pruneForEntryPoints(ImmutableList.of(ModuleIdentifier.forClosure("c2")));
    ImmutableList<CompilerInput> results = graph.manageDependencies(compiler, depOptions);

    // Everything gets pushed up into module c, because that's
    // the only one that has entry points.
    assertInputs(chunkA);
    assertInputs(chunkB);
    assertInputs(chunkC, "a1", "c1", "c2");
    assertInputs(chunkE);

    assertThat(sourceNames(results)).containsExactly("a1", "c1", "c2").inOrder();
  }

  @Test
  public void testManageDependenciesStrictForGoogRequireDynamic() throws Exception {
    JSChunk chunkA = new JSChunk("chunk");
    graph = new JSChunkGraph(new JSChunk[] {chunkA});
    List<CompilerInput> inputs = new ArrayList<>();
    CompilerInput compilerInputA1 = new CompilerInput(code("a1", provides("a1"), requires()));
    compilerInputA1.addRequireDynamicImports("a2");
    chunkA.add(compilerInputA1);
    chunkA.add(code("a2", provides("a2"), requires()));
    inputs.addAll(chunkA.getInputs());
    for (CompilerInput input : inputs) {
      input.setCompiler(compiler);
    }
    DependencyOptions depOptions =
        DependencyOptions.pruneForEntryPoints(ImmutableList.of(ModuleIdentifier.forClosure("a1")));
    ImmutableList<CompilerInput> results = graph.manageDependencies(compiler, depOptions);

    assertInputs(chunkA, "a1", "a2");
    assertThat(sourceNames(results)).containsExactly("a1", "a2");
  }

  @Test
  public void testManageDependenciesStrictWithEntryPointWithDuplicates() throws Exception {
    final JSChunk a = new JSChunk("a");
    JSChunkGraph graph = new JSChunkGraph(new JSChunk[] {a});

    // Create all the input files.
    List<CompilerInput> inputs = new ArrayList<>();
    a.add(code("a1", provides("a1"), requires("a2")));
    a.add(code("a2", provides("a2"), requires()));
    a.add(code("a3", provides("a2"), requires()));
    inputs.addAll(a.getInputs());
    for (CompilerInput input : inputs) {
      input.setCompiler(compiler);
    }

    DependencyOptions depOptions =
        DependencyOptions.pruneForEntryPoints(ImmutableList.of(ModuleIdentifier.forClosure("a1")));
    ImmutableList<CompilerInput> results = graph.manageDependencies(compiler, depOptions);

    // Everything gets pushed up into module c, because that's
    // the only one that has entry points.
    assertInputs(a, "a2", "a3", "a1");

    assertThat(sourceNames(results)).containsExactly("a2", "a3", "a1").inOrder();
  }

  @Test
  public void testManageDependenciesSortOnly() throws Exception {
    makeDeps();
    makeGraph();
    setUpManageDependenciesTest();
    ImmutableList<CompilerInput> results =
        graph.manageDependencies(compiler, DependencyOptions.sortOnly());

    assertInputs(chunkA, "a1", "a2", "a3");
    assertInputs(chunkB, "b1", "b2");
    assertInputs(chunkC, "c1", "c2");
    assertInputs(chunkE, "e1", "e2");

    assertThat(sourceNames(results))
        .isEqualTo(ImmutableList.of("a1", "a2", "a3", "b1", "b2", "c1", "c2", "e1", "e2"));
  }

  // NOTE: The newline between the @provideGoog comment and the var statement is required.
  private static final String BASEJS =
      lines(
          "/** @fileoverview",
          " * @provideGoog */",
          "var COMPILED = false;",
          "var goog = goog || {}");

  @Test
  public void testManageDependenciesSortOnlyImpl() throws Exception {
    makeDeps();
    makeGraph();
    chunkA.add(code("a2", provides("a2"), requires("a1")));
    chunkA.add(code("a1", provides("a1"), requires()));
    chunkA.add(code("base.js", BASEJS, provides(), requires()));

    for (CompilerInput input : chunkA.getInputs()) {
      input.setCompiler(compiler);
    }

    ImmutableList<CompilerInput> results =
        graph.manageDependencies(compiler, DependencyOptions.sortOnly());

    assertInputs(chunkA, "base.js", "a1", "a2");

    assertThat(sourceNames(results)).containsExactly("base.js", "a1", "a2").inOrder();
  }

  @Test
  public void testNoFiles() throws Exception {
    makeDeps();
    makeGraph();
    ImmutableList<CompilerInput> results =
        graph.manageDependencies(compiler, DependencyOptions.sortOnly());
    assertThat(results).isEmpty();
  }

  @Test
  public void testToJson() {
    makeDeps();
    makeGraph();
    JsonArray modules = graph.toJson();
    assertThat(modules).hasSize(7);
    for (int i = 0; i < modules.size(); i++) {
      JsonObject m = modules.get(i).getAsJsonObject();
      assertThat(m.get("name")).isNotNull();
      assertThat(m.get("dependencies")).isNotNull();
      assertThat(m.get("transitive-dependencies")).isNotNull();
      assertThat(m.get("inputs")).isNotNull();
    }
    JsonObject m = modules.get(3).getAsJsonObject();
    assertThat(m.get("name").getAsString()).isEqualTo("chunkD");
    assertThat(m.get("dependencies").getAsJsonArray().toString()).isEqualTo("[\"chunkB\"]");
    assertThat(m.get("transitive-dependencies").getAsJsonArray()).hasSize(2);
    assertThat(m.get("inputs").getAsJsonArray().toString()).isEqualTo("[]");
  }

  private List<CompilerInput> setUpManageDependenciesTest() {
    List<CompilerInput> inputs = new ArrayList<>();

    chunkA.add(code("a1", provides("a1"), requires()));
    chunkA.add(code("a2", provides("a2"), requires("a1")));
    chunkA.add(code("a3", provides(), requires("a1")));

    chunkB.add(code("b1", provides("b1"), requires("a2")));
    chunkB.add(code("b2", provides(), requires("a1", "a2")));

    chunkC.add(code("c1", provides("c1"), requires("a1")));
    chunkC.add(code("c2", provides("c2"), requires("c1")));

    chunkE.add(code("e1", provides(), requires("c1")));
    chunkE.add(code("e2", provides(), requires("c1")));

    inputs.addAll(chunkA.getInputs());
    inputs.addAll(chunkB.getInputs());
    inputs.addAll(chunkC.getInputs());
    inputs.addAll(chunkE.getInputs());

    for (CompilerInput input : inputs) {
      input.setCompiler(compiler);
    }
    return inputs;
  }

  @Test
  public void testGoogBaseOrderedCorrectly() throws Exception {
    makeDeps();
    makeGraph();
    List<SourceFile> sourceFiles = new ArrayList<>();
    sourceFiles.add(code("a9", provides("a9"), requires()));
    sourceFiles.add(code("a8", provides("a8"), requires()));
    sourceFiles.add(code("a7", provides("a7"), requires()));
    sourceFiles.add(code("a6", provides("a6"), requires()));
    sourceFiles.add(code("a5", provides("a5"), requires()));
    sourceFiles.add(code("a4", provides("a4"), requires()));
    sourceFiles.add(code("a3", provides("a3"), requires()));
    sourceFiles.add(code("a2", provides("a2"), requires()));
    sourceFiles.add(
        code("a1", provides("a1"), requires("a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9")));
    sourceFiles.add(code("base.js", BASEJS, provides(), requires()));

    DependencyOptions depOptions =
        DependencyOptions.pruneForEntryPoints(ImmutableList.of(ModuleIdentifier.forClosure("a1")));
    for (int i = 0; i < 10; i++) {
      shuffle(sourceFiles);
      chunkA.removeAll();
      for (SourceFile sourceFile : sourceFiles) {
        chunkA.add(sourceFile);
      }

      for (CompilerInput input : chunkA.getInputs()) {
        input.setCompiler(compiler);
      }

      ImmutableList<CompilerInput> results = graph.manageDependencies(compiler, depOptions);

      assertInputs(chunkA, "base.js", "a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9", "a1");

      assertThat(sourceNames(results))
          .containsExactly("base.js", "a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9", "a1")
          .inOrder();
    }
  }

  @Test
  public void testProperEs6ModuleOrdering() throws Exception {
    makeDeps();
    makeGraph();
    List<SourceFile> sourceFiles = new ArrayList<>();
    sourceFiles.add(code("/entry.js", provides(), requires()));
    sourceFiles.add(code("/a/a.js", provides(), requires()));
    sourceFiles.add(code("/a/b.js", provides(), requires()));
    sourceFiles.add(code("/b/a.js", provides(), requires()));
    sourceFiles.add(code("/b/b.js", provides(), requires()));
    sourceFiles.add(code("/b/c.js", provides(), requires()));
    sourceFiles.add(code("/important.js", provides(), requires()));

    HashMap<String, List<String>> orderedRequires = new HashMap<>();
    orderedRequires.put(
        "/entry.js",
        ImmutableList.of(
            ModuleIdentifier.forFile("/b/b.js").toString(),
            ModuleIdentifier.forFile("/b/a.js").toString(),
            ModuleIdentifier.forFile("/important.js").toString(),
            ModuleIdentifier.forFile("/a/b.js").toString(),
            ModuleIdentifier.forFile("/a/a.js").toString()));
    orderedRequires.put("/a/a.js", ImmutableList.of());
    orderedRequires.put("/a/b.js", ImmutableList.of());
    orderedRequires.put("/b/a.js", ImmutableList.of());
    orderedRequires.put(
        "/b/b.js", ImmutableList.of(ModuleIdentifier.forFile("/b/c.js").toString()));
    orderedRequires.put("/b/c.js", ImmutableList.of());
    orderedRequires.put("/important.js", ImmutableList.of());

    DependencyOptions depOptions =
        DependencyOptions.pruneForEntryPoints(
            ImmutableList.of(ModuleIdentifier.forFile("/entry.js")));
    for (int iterationCount = 0; iterationCount < 10; iterationCount++) {
      shuffle(sourceFiles);
      chunkA.removeAll();
      for (SourceFile sourceFile : sourceFiles) {
        chunkA.add(sourceFile);
      }

      for (CompilerInput input : chunkA.getInputs()) {
        input.setCompiler(compiler);
        for (String require : orderedRequires.get(input.getSourceFile().getName())) {
          input.addOrderedRequire(Require.compilerModule(require));
        }
        input.setHasFullParseDependencyInfo(true);
      }

      ImmutableList<CompilerInput> results = graph.manageDependencies(compiler, depOptions);

      assertInputs(
          chunkA,
          "/b/c.js",
          "/b/b.js",
          "/b/a.js",
          "/important.js",
          "/a/b.js",
          "/a/a.js",
          "/entry.js");

      assertThat(sourceNames(results))
          .containsExactly(
              "/b/c.js", "/b/b.js", "/b/a.js", "/important.js", "/a/b.js", "/a/a.js", "/entry.js")
          .inOrder();
    }
  }

  @Test
  public void testMoveMarkedWeakSources() throws Exception {
    makeDeps();

    SourceFile weak1 = SourceFile.fromCode("weak1", "", SourceKind.WEAK);
    SourceFile weak2 = SourceFile.fromCode("weak2", "", SourceKind.WEAK);
    SourceFile strong1 = SourceFile.fromCode("strong1", "", SourceKind.STRONG);
    SourceFile strong2 = SourceFile.fromCode("strong2", "", SourceKind.STRONG);

    chunkA.add(weak1);
    chunkA.add(strong1);
    chunkA.add(weak2);
    chunkA.add(strong2);

    for (CompilerInput input : chunkA.getInputs()) {
      input.setCompiler(compiler);
    }

    makeGraph();

    assertThat(getWeakModule().getInputs().stream().map(CompilerInput::getSourceFile))
        .containsExactly(weak1, weak2);
    assertThat(chunkA.getInputs().stream().map(CompilerInput::getSourceFile))
        .containsExactly(strong1, strong2);
  }

  @Test
  public void testMoveMarkedWeakSourcesDuringManageDepsSortOnly() throws Exception {
    makeDeps();

    SourceFile weak1 = SourceFile.fromCode("weak1", "", SourceKind.WEAK);
    SourceFile weak2 = SourceFile.fromCode("weak2", "", SourceKind.WEAK);
    SourceFile strong1 = SourceFile.fromCode("strong1", "", SourceKind.STRONG);
    SourceFile strong2 = SourceFile.fromCode("strong2", "", SourceKind.STRONG);

    chunkA.add(weak1);
    chunkA.add(strong1);
    chunkA.add(weak2);
    chunkA.add(strong2);

    for (CompilerInput input : chunkA.getInputs()) {
      input.setCompiler(compiler);
    }

    makeGraph();
    graph.manageDependencies(compiler, DependencyOptions.sortOnly());

    assertThat(getWeakModule().getInputs().stream().map(CompilerInput::getSourceFile))
        .containsExactly(weak1, weak2);
    assertThat(chunkA.getInputs().stream().map(CompilerInput::getSourceFile))
        .containsExactly(strong1, strong2);
  }

  @Test
  public void testIgnoreMarkedWeakSourcesDuringManageDepsPrune() throws Exception {
    makeDeps();

    SourceFile weak1 = SourceFile.fromCode("weak1", "", SourceKind.WEAK);
    SourceFile weak2 = SourceFile.fromCode("weak2", "", SourceKind.WEAK);
    SourceFile strong1 = SourceFile.fromCode("strong1", "", SourceKind.STRONG);
    SourceFile strong2 = SourceFile.fromCode("strong2", "", SourceKind.STRONG);

    chunkA.add(weak1);
    chunkA.add(strong1);
    chunkA.add(weak2);
    chunkA.add(strong2);

    for (CompilerInput input : chunkA.getInputs()) {
      input.setCompiler(compiler);
    }

    makeGraph();
    graph.manageDependencies(
        compiler,
        DependencyOptions.pruneForEntryPoints(
            ImmutableList.of(
                ModuleIdentifier.forFile("strong1"), ModuleIdentifier.forFile("strong2"))));

    assertThat(
            getWeakModule().getInputs().stream()
                .map(CompilerInput::getSourceFile)
                .collect(Collectors.toList()))
        .isEmpty();
    assertThat(chunkA.getInputs().stream().map(CompilerInput::getSourceFile))
        .containsExactly(strong1, strong2);
  }

  @Test
  public void testIgnoreDepsOfMarkedWeakSourcesDuringManageDepsPrune() throws Exception {
    makeDeps();

    SourceFile weak1 =
        SourceFile.fromCode("weak1", "goog.requireType('weak1weak');", SourceKind.WEAK);
    SourceFile weak1weak =
        SourceFile.fromCode("weak1weak", "goog.provide('weak1weak');", SourceKind.WEAK);
    SourceFile weak2 =
        SourceFile.fromCode("weak2", "goog.require('weak2strong');", SourceKind.WEAK);
    SourceFile weak2strong =
        SourceFile.fromCode("weak2strong", "goog.provide('weak2strong');", SourceKind.WEAK);
    SourceFile strong1 = SourceFile.fromCode("strong1", "", SourceKind.STRONG);
    SourceFile strong2 = SourceFile.fromCode("strong2", "", SourceKind.STRONG);

    chunkA.add(weak1);
    chunkA.add(strong1);
    chunkA.add(weak2);
    chunkA.add(strong2);
    chunkA.add(weak1weak);
    chunkA.add(weak2strong);

    for (CompilerInput input : chunkA.getInputs()) {
      input.setCompiler(compiler);
    }

    makeGraph();
    graph.manageDependencies(
        compiler,
        DependencyOptions.pruneForEntryPoints(
            ImmutableList.of(
                ModuleIdentifier.forFile("strong1"), ModuleIdentifier.forFile("strong2"))));

    assertThat(
            getWeakModule().getInputs().stream()
                .map(CompilerInput::getSourceFile)
                .collect(Collectors.toList()))
        .isEmpty();
    assertThat(chunkA.getInputs().stream().map(CompilerInput::getSourceFile))
        .containsExactly(strong1, strong2);
  }

  @Test
  public void testMoveImplicitWeakSourcesFromMoocherDuringManageDepsLegacyPrune() throws Exception {
    makeDeps();

    SourceFile weak = SourceFile.fromCode("weak", "goog.provide('weak');");
    SourceFile strong = SourceFile.fromCode("strong", "");
    SourceFile moocher = SourceFile.fromCode("moocher", "goog.requireType('weak');");

    chunkA.add(weak);
    chunkA.add(strong);
    chunkA.add(moocher);

    for (CompilerInput input : chunkA.getInputs()) {
      input.setCompiler(compiler);
    }

    makeGraph();
    graph.manageDependencies(
        compiler,
        DependencyOptions.pruneLegacyForEntryPoints(
            ImmutableList.of(ModuleIdentifier.forFile("strong"))));

    assertThat(getWeakModule().getInputs().stream().map(CompilerInput::getSourceFile))
        .containsExactly(weak);
    assertThat(chunkA.getInputs().stream().map(CompilerInput::getSourceFile))
        .containsExactly(strong, moocher);
  }

  @Test
  public void testImplicitWeakSourcesNotMovedDuringManageDepsSortOnly() throws Exception {
    makeDeps();

    SourceFile weak1 = SourceFile.fromCode("weak1", "goog.provide('weak1');");
    SourceFile weak2 = SourceFile.fromCode("weak2", "goog.provide('weak2');");
    SourceFile strong1 = SourceFile.fromCode("strong1", "goog.requireType('weak1');");
    SourceFile strong2 = SourceFile.fromCode("strong2", "goog.requireType('weak2');");

    chunkA.add(weak1);
    chunkA.add(strong1);
    chunkA.add(weak2);
    chunkA.add(strong2);

    for (CompilerInput input : chunkA.getInputs()) {
      input.setCompiler(compiler);
    }

    makeGraph();
    graph.manageDependencies(compiler, DependencyOptions.sortOnly());

    assertThat(getWeakModule().getInputs()).isEmpty();
    assertThat(chunkA.getInputs().stream().map(CompilerInput::getSourceFile))
        .containsExactly(weak1, strong1, weak2, strong2);
  }

  @Test
  public void testImplicitWeakSourcesMovedDuringManageDepsPrune() throws Exception {
    makeDeps();

    SourceFile weak1 = SourceFile.fromCode("weak1", "goog.provide('weak1');");
    SourceFile weak2 = SourceFile.fromCode("weak2", "goog.provide('weak2');");
    SourceFile strong1 = SourceFile.fromCode("strong1", "goog.requireType('weak1');");
    SourceFile strong2 = SourceFile.fromCode("strong2", "goog.requireType('weak2');");

    chunkA.add(weak1);
    chunkA.add(strong1);
    chunkA.add(weak2);
    chunkA.add(strong2);

    for (CompilerInput input : chunkA.getInputs()) {
      input.setCompiler(compiler);
    }

    makeGraph();
    graph.manageDependencies(
        compiler,
        DependencyOptions.pruneForEntryPoints(
            ImmutableList.of(
                ModuleIdentifier.forFile("strong1"), ModuleIdentifier.forFile("strong2"))));

    assertThat(getWeakModule().getInputs().stream().map(CompilerInput::getSourceFile))
        .containsExactly(weak1, weak2);
    assertThat(chunkA.getInputs().stream().map(CompilerInput::getSourceFile))
        .containsExactly(strong1, strong2);
  }

  @Test
  public void testTransitiveWeakSources() throws Exception {
    makeDeps();

    SourceFile weak1 =
        SourceFile.fromCode(
            "weak1",
            "goog.provide('weak1'); goog.requireType('weak2'); goog.require('strongFromWeak');");
    SourceFile strongFromWeak =
        SourceFile.fromCode("strongFromWeak", "goog.provide('strongFromWeak');");
    SourceFile weak2 =
        SourceFile.fromCode("weak2", "goog.provide('weak2'); goog.requireType('weak3');");
    SourceFile weak3 = SourceFile.fromCode("weak3", "goog.provide('weak3');");
    SourceFile strong1 = SourceFile.fromCode("strong1", "goog.requireType('weak1');");

    chunkA.add(weak1);
    chunkA.add(strong1);
    chunkA.add(weak2);
    chunkA.add(weak3);
    chunkA.add(strongFromWeak);

    for (CompilerInput input : chunkA.getInputs()) {
      input.setCompiler(compiler);
    }

    makeGraph();
    graph.manageDependencies(
        compiler,
        DependencyOptions.pruneForEntryPoints(
            ImmutableList.of(ModuleIdentifier.forFile("strong1"))));

    assertThat(getWeakModule().getInputs().stream().map(CompilerInput::getSourceFile))
        .containsExactly(weak1, weak2, weak3, strongFromWeak);
    assertThat(chunkA.getInputs().stream().map(CompilerInput::getSourceFile))
        .containsExactly(strong1);
  }

  private void assertInputs(JSChunk chunk, String... sourceNames) {
    assertThat(sourceNames(chunk.getInputs())).isEqualTo(ImmutableList.copyOf(sourceNames));
  }

  private List<String> sourceNames(List<CompilerInput> inputs) {
    List<String> inputNames = new ArrayList<>();
    for (CompilerInput input : inputs) {
      inputNames.add(input.getName());
    }
    return inputNames;
  }

  private SourceFile code(String sourceName, List<String> provides, List<String> requires) {
    return code(sourceName, "", provides, requires);
  }

  private SourceFile code(
      String sourceName, String source, List<String> provides, List<String> requires) {
    String text = "";
    for (String p : provides) {
      text += "goog.provide('" + p + "');\n";
    }
    for (String r : requires) {
      text += "goog.require('" + r + "');\n";
    }
    return SourceFile.fromCode(sourceName, text + source);
  }

  private ImmutableList<String> provides(String... strings) {
    return ImmutableList.copyOf(strings);
  }

  private ImmutableList<String> requires(String... strings) {
    return ImmutableList.copyOf(strings);
  }

  private void assertSmallestCoveringSubtree(
      JSChunk expected, JSChunk parentTree, JSChunk... modules) {
    assertSmallestCoveringSubtree(expected, graph, parentTree, modules);
  }

  private void assertSmallestCoveringSubtree(
      JSChunk expected, JSChunkGraph graph, JSChunk parentTree, JSChunk... modules) {
    BitSet modulesBitSet = new BitSet();
    for (JSChunk m : modules) {
      modulesBitSet.set(m.getIndex());
    }
    assertSmallestCoveringSubtree(expected, graph, parentTree, modulesBitSet);
  }

  private void assertSmallestCoveringSubtree(
      JSChunk expected, JSChunkGraph graph, JSChunk parentTree, BitSet modules) {
    JSChunk actual = graph.getSmallestCoveringSubtree(parentTree, modules);
    assertWithMessage(
            "Smallest covering subtree of %s in %s should be %s but was %s",
            parentTree, modules, expected, actual)
        .that(actual)
        .isEqualTo(expected);
  }

  private void assertDeepestCommonDepInclusive(JSChunk expected, JSChunk m1, JSChunk m2) {
    assertDeepestCommonDepOneWay(expected, m1, m2, true);
    assertDeepestCommonDepOneWay(expected, m2, m1, true);
  }

  private void assertDeepestCommonDep(@Nullable JSChunk expected, JSChunk m1, JSChunk m2) {
    assertDeepestCommonDepOneWay(expected, m1, m2, false);
    assertDeepestCommonDepOneWay(expected, m2, m1, false);
  }

  private void assertDeepestCommonDepOneWay(
      JSChunk expected, JSChunk m1, JSChunk m2, boolean inclusive) {
    JSChunk actual =
        inclusive
            ? graph.getDeepestCommonDependencyInclusive(m1, m2)
            : graph.getDeepestCommonDependency(m1, m2);
    if (actual != expected) {
      assertWithMessage(
              String.format(
                  "Deepest common dep of %s and %s should be %s but was %s",
                  m1.getName(),
                  m2.getName(),
                  expected == null ? "null" : expected.getName(),
                  actual == null ? "null" : actual.getName()))
          .fail();
    }
  }

  private void assertTransitiveDepsDeepestFirst(JSChunk m, JSChunk... deps) {
    Iterable<JSChunk> actual = graph.getTransitiveDepsDeepestFirst(m);
    assertThat(Arrays.toString(Iterables.toArray(actual, JSChunk.class)))
        .isEqualTo(Arrays.toString(deps));
  }
}
