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
import static com.google.common.truth.Truth8.assertThat;
import static java.util.Collections.shuffle;
import static org.junit.Assert.fail;

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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link JSChunkGraph} */
@RunWith(JUnit4.class)
public final class JSModuleGraphTest {

  private JSChunk moduleA;
  private JSChunk moduleB;
  private JSChunk moduleC;
  private JSChunk moduleD;
  private JSChunk moduleE;
  private JSChunk moduleF;
  private JSChunkGraph graph = null;

  // For resolving dependencies only.
  private Compiler compiler;

  @Before
  public void setUp() throws Exception {
    compiler = new Compiler();
  }

  private void makeDeps() {
    moduleA = new JSChunk("moduleA");
    moduleB = new JSChunk("moduleB");
    moduleC = new JSChunk("moduleC");
    moduleD = new JSChunk("moduleD");
    moduleE = new JSChunk("moduleE");
    moduleF = new JSChunk("moduleF");
    moduleB.addDependency(moduleA); //     __A__
    moduleC.addDependency(moduleA); //    /  |  \
    moduleD.addDependency(moduleB); //   B   C  |
    moduleE.addDependency(moduleB); //  / \ /|  |
    moduleE.addDependency(moduleC); // D   E | /
    moduleF.addDependency(moduleA); //      \|/
    moduleF.addDependency(moduleC); //       F
    moduleF.addDependency(moduleE);
  }

  private void makeGraph() {
    graph = new JSChunkGraph(new JSChunk[] {moduleA, moduleB, moduleC, moduleD, moduleE, moduleF});
  }

  private JSChunk getWeakModule() {
    return graph.getModuleByName(JSChunk.WEAK_MODULE_NAME);
  }

  @Test
  public void testMakesWeakModuleIfNotPassed() {
    makeDeps();
    makeGraph();
    assertThat(graph.getModuleCount()).isEqualTo(7);
    assertThat(graph.getModulesByName()).containsKey(JSChunk.WEAK_MODULE_NAME);
    assertThat(getWeakModule().getAllDependencies())
        .containsExactly(moduleA, moduleB, moduleC, moduleD, moduleE, moduleF);
  }

  @Test
  public void testAcceptExistingWeakModule() {
    makeDeps();
    JSChunk weakModule = new JSChunk(JSChunk.WEAK_MODULE_NAME);

    weakModule.addDependency(moduleA);
    weakModule.addDependency(moduleB);
    weakModule.addDependency(moduleC);
    weakModule.addDependency(moduleD);
    weakModule.addDependency(moduleE);
    weakModule.addDependency(moduleF);

    weakModule.add(SourceFile.fromCode("weak", "", SourceKind.WEAK));

    JSChunkGraph graph =
        new JSChunkGraph(
            new JSChunk[] {moduleA, moduleB, moduleC, moduleD, moduleE, moduleF, weakModule});

    assertThat(graph.getModuleCount()).isEqualTo(7);
    assertThat(graph.getModuleByName(JSChunk.WEAK_MODULE_NAME)).isSameInstanceAs(weakModule);
  }

  @Test
  public void testExistingWeakModuleMustHaveDependenciesOnAllOtherModules() {
    makeDeps();
    JSChunk weakModule = new JSChunk(JSChunk.WEAK_MODULE_NAME);

    weakModule.addDependency(moduleA);
    weakModule.addDependency(moduleB);
    weakModule.addDependency(moduleC);
    weakModule.addDependency(moduleD);
    weakModule.addDependency(moduleE);
    // Missing F

    try {
      new JSChunkGraph(
          new JSChunk[] {moduleA, moduleB, moduleC, moduleD, moduleE, moduleF, weakModule});
      fail();
    } catch (IllegalStateException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo("A weak module already exists but it does not depend on every other module.");
    }
  }

  @Test
  public void testWeakFileCannotExistOutsideWeakModule() {
    makeDeps();
    JSChunk weakModule = new JSChunk(JSChunk.WEAK_MODULE_NAME);

    weakModule.addDependency(moduleA);
    weakModule.addDependency(moduleB);
    weakModule.addDependency(moduleC);
    weakModule.addDependency(moduleD);
    weakModule.addDependency(moduleE);
    weakModule.addDependency(moduleF);

    moduleA.add(SourceFile.fromCode("a", "", SourceKind.WEAK));

    try {
      new JSChunkGraph(
          new JSChunk[] {moduleA, moduleB, moduleC, moduleD, moduleE, moduleF, weakModule});
      fail();
    } catch (IllegalStateException e) {
      assertThat(e)
          .hasMessageThat()
          .contains("Found these weak sources in other modules:\n  a (in module moduleA)");
    }
  }

  @Test
  public void testStrongFileCannotExistInWeakModule() {
    makeDeps();
    JSChunk weakModule = new JSChunk(JSChunk.WEAK_MODULE_NAME);

    weakModule.addDependency(moduleA);
    weakModule.addDependency(moduleB);
    weakModule.addDependency(moduleC);
    weakModule.addDependency(moduleD);
    weakModule.addDependency(moduleE);
    weakModule.addDependency(moduleF);

    weakModule.add(SourceFile.fromCode("a", "", SourceKind.STRONG));

    try {
      new JSChunkGraph(
          new JSChunk[] {moduleA, moduleB, moduleC, moduleD, moduleE, moduleF, weakModule});
      fail();
    } catch (IllegalStateException e) {
      assertThat(e)
          .hasMessageThat()
          .contains("Found these strong sources in the weak module:\n  a");
    }
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
    assertWithMessage("moduleA should have depth 0").that(moduleA.getDepth()).isEqualTo(0);
    assertWithMessage("moduleB should have depth 1").that(moduleB.getDepth()).isEqualTo(1);
    assertWithMessage("moduleC should have depth 1").that(moduleC.getDepth()).isEqualTo(1);
    assertWithMessage("moduleD should have depth 2").that(moduleD.getDepth()).isEqualTo(2);
    assertWithMessage("moduleE should have depth 2").that(moduleE.getDepth()).isEqualTo(2);
    assertWithMessage("moduleF should have depth 3").that(moduleF.getDepth()).isEqualTo(3);
  }

  @Test
  public void testDeepestCommonDep() {
    makeDeps();
    makeGraph();
    assertDeepestCommonDep(null, moduleA, moduleA);
    assertDeepestCommonDep(null, moduleA, moduleB);
    assertDeepestCommonDep(null, moduleA, moduleC);
    assertDeepestCommonDep(null, moduleA, moduleD);
    assertDeepestCommonDep(null, moduleA, moduleE);
    assertDeepestCommonDep(null, moduleA, moduleF);
    assertDeepestCommonDep(moduleA, moduleB, moduleB);
    assertDeepestCommonDep(moduleA, moduleB, moduleC);
    assertDeepestCommonDep(moduleA, moduleB, moduleD);
    assertDeepestCommonDep(moduleA, moduleB, moduleE);
    assertDeepestCommonDep(moduleA, moduleB, moduleF);
    assertDeepestCommonDep(moduleA, moduleC, moduleC);
    assertDeepestCommonDep(moduleA, moduleC, moduleD);
    assertDeepestCommonDep(moduleA, moduleC, moduleE);
    assertDeepestCommonDep(moduleA, moduleC, moduleF);
    assertDeepestCommonDep(moduleB, moduleD, moduleD);
    assertDeepestCommonDep(moduleB, moduleD, moduleE);
    assertDeepestCommonDep(moduleB, moduleD, moduleF);
    assertDeepestCommonDep(moduleC, moduleE, moduleE);
    assertDeepestCommonDep(moduleC, moduleE, moduleF);
    assertDeepestCommonDep(moduleE, moduleF, moduleF);
  }

  @Test
  public void testDeepestCommonDepInclusive() {
    makeDeps();
    makeGraph();
    assertDeepestCommonDepInclusive(moduleA, moduleA, moduleA);
    assertDeepestCommonDepInclusive(moduleA, moduleA, moduleB);
    assertDeepestCommonDepInclusive(moduleA, moduleA, moduleC);
    assertDeepestCommonDepInclusive(moduleA, moduleA, moduleD);
    assertDeepestCommonDepInclusive(moduleA, moduleA, moduleE);
    assertDeepestCommonDepInclusive(moduleA, moduleA, moduleF);
    assertDeepestCommonDepInclusive(moduleB, moduleB, moduleB);
    assertDeepestCommonDepInclusive(moduleA, moduleB, moduleC);
    assertDeepestCommonDepInclusive(moduleB, moduleB, moduleD);
    assertDeepestCommonDepInclusive(moduleB, moduleB, moduleE);
    assertDeepestCommonDepInclusive(moduleB, moduleB, moduleF);
    assertDeepestCommonDepInclusive(moduleC, moduleC, moduleC);
    assertDeepestCommonDepInclusive(moduleA, moduleC, moduleD);
    assertDeepestCommonDepInclusive(moduleC, moduleC, moduleE);
    assertDeepestCommonDepInclusive(moduleC, moduleC, moduleF);
    assertDeepestCommonDepInclusive(moduleD, moduleD, moduleD);
    assertDeepestCommonDepInclusive(moduleB, moduleD, moduleE);
    assertDeepestCommonDepInclusive(moduleB, moduleD, moduleF);
    assertDeepestCommonDepInclusive(moduleE, moduleE, moduleE);
    assertDeepestCommonDepInclusive(moduleE, moduleE, moduleF);
    assertDeepestCommonDepInclusive(moduleF, moduleF, moduleF);
  }

  @Test
  public void testSmallestCoveringSubtree() {
    makeDeps();
    makeGraph();
    assertSmallestCoveringSubtree(moduleA, moduleA, moduleA, moduleA);
    assertSmallestCoveringSubtree(moduleA, moduleA, moduleA, moduleB);
    assertSmallestCoveringSubtree(moduleA, moduleA, moduleA, moduleC);
    assertSmallestCoveringSubtree(moduleA, moduleA, moduleA, moduleD);
    assertSmallestCoveringSubtree(moduleA, moduleA, moduleA, moduleE);
    assertSmallestCoveringSubtree(moduleA, moduleA, moduleA, moduleF);
    assertSmallestCoveringSubtree(moduleB, moduleA, moduleB, moduleB);
    assertSmallestCoveringSubtree(moduleA, moduleA, moduleB, moduleC);
    assertSmallestCoveringSubtree(moduleB, moduleA, moduleB, moduleD);
    assertSmallestCoveringSubtree(moduleB, moduleA, moduleB, moduleE);
    assertSmallestCoveringSubtree(moduleB, moduleA, moduleB, moduleF);
    assertSmallestCoveringSubtree(moduleC, moduleA, moduleC, moduleC);
    assertSmallestCoveringSubtree(moduleA, moduleA, moduleC, moduleD);
    assertSmallestCoveringSubtree(moduleC, moduleA, moduleC, moduleE);
    assertSmallestCoveringSubtree(moduleC, moduleA, moduleC, moduleF);
    assertSmallestCoveringSubtree(moduleD, moduleA, moduleD, moduleD);
    assertSmallestCoveringSubtree(moduleB, moduleA, moduleD, moduleE);
    assertSmallestCoveringSubtree(moduleB, moduleA, moduleD, moduleF);
    assertSmallestCoveringSubtree(moduleE, moduleA, moduleE, moduleE);
    assertSmallestCoveringSubtree(moduleE, moduleA, moduleE, moduleF);
    assertSmallestCoveringSubtree(moduleF, moduleA, moduleF, moduleF);
  }

  @Test
  public void testGetTransitiveDepsDeepestFirst() {
    makeDeps();
    makeGraph();
    assertTransitiveDepsDeepestFirst(moduleA);
    assertTransitiveDepsDeepestFirst(moduleB, moduleA);
    assertTransitiveDepsDeepestFirst(moduleC, moduleA);
    assertTransitiveDepsDeepestFirst(moduleD, moduleB, moduleA);
    assertTransitiveDepsDeepestFirst(moduleE, moduleC, moduleB, moduleA);
    assertTransitiveDepsDeepestFirst(moduleF, moduleE, moduleC, moduleB, moduleA);
  }

  @Test
  public void testManageDependenciesLooseWithoutEntryPoint() throws Exception {
    makeDeps();
    makeGraph();
    setUpManageDependenciesTest();
    DependencyOptions depOptions = DependencyOptions.pruneLegacyForEntryPoints(ImmutableList.of());
    List<CompilerInput> results = graph.manageDependencies(compiler, depOptions);

    assertInputs(moduleA, "a1", "a3");
    assertInputs(moduleB, "a2", "b2");
    assertInputs(moduleC); // no inputs
    assertInputs(moduleE, "c1", "e1", "e2");

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
    List<CompilerInput> results = graph.manageDependencies(compiler, depOptions);

    assertInputs(moduleA, "a1", "a3");
    assertInputs(moduleB, "a2", "b2");
    assertInputs(moduleC, "c1", "c2");
    assertInputs(moduleE, "e1", "e2");

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
    List<CompilerInput> results = graph.manageDependencies(compiler, depOptions);

    // Everything gets pushed up into module c, because that's
    // the only one that has entry points.
    assertInputs(moduleA);
    assertInputs(moduleB);
    assertInputs(moduleC, "a1", "c1", "c2");
    assertInputs(moduleE);

    assertThat(sourceNames(results)).containsExactly("a1", "c1", "c2").inOrder();
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
    List<CompilerInput> results = graph.manageDependencies(compiler, depOptions);

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
    List<CompilerInput> results = graph.manageDependencies(compiler, DependencyOptions.sortOnly());

    assertInputs(moduleA, "a1", "a2", "a3");
    assertInputs(moduleB, "b1", "b2");
    assertInputs(moduleC, "c1", "c2");
    assertInputs(moduleE, "e1", "e2");

    assertThat(sourceNames(results))
        .isEqualTo(ImmutableList.of("a1", "a2", "a3", "b1", "b2", "c1", "c2", "e1", "e2"));
  }

  // NOTE: The newline between the @provideGoog comment and the var statement is required.
  private static final String BASEJS =
      "/** @provideGoog */\nvar COMPILED = false; var goog = goog || {}";

  @Test
  public void testManageDependenciesSortOnlyImpl() throws Exception {
    makeDeps();
    makeGraph();
    moduleA.add(code("a2", provides("a2"), requires("a1")));
    moduleA.add(code("a1", provides("a1"), requires()));
    moduleA.add(code("base.js", BASEJS, provides(), requires()));

    for (CompilerInput input : moduleA.getInputs()) {
      input.setCompiler(compiler);
    }

    List<CompilerInput> results = graph.manageDependencies(compiler, DependencyOptions.sortOnly());

    assertInputs(moduleA, "base.js", "a1", "a2");

    assertThat(sourceNames(results)).containsExactly("base.js", "a1", "a2").inOrder();
  }

  @Test
  public void testNoFiles() throws Exception {
    makeDeps();
    makeGraph();
    List<CompilerInput> results = graph.manageDependencies(compiler, DependencyOptions.sortOnly());
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
    assertThat(m.get("name").getAsString()).isEqualTo("moduleD");
    assertThat(m.get("dependencies").getAsJsonArray().toString()).isEqualTo("[\"moduleB\"]");
    assertThat(m.get("transitive-dependencies").getAsJsonArray()).hasSize(2);
    assertThat(m.get("inputs").getAsJsonArray().toString()).isEqualTo("[]");
  }

  private List<CompilerInput> setUpManageDependenciesTest() {
    List<CompilerInput> inputs = new ArrayList<>();

    moduleA.add(code("a1", provides("a1"), requires()));
    moduleA.add(code("a2", provides("a2"), requires("a1")));
    moduleA.add(code("a3", provides(), requires("a1")));

    moduleB.add(code("b1", provides("b1"), requires("a2")));
    moduleB.add(code("b2", provides(), requires("a1", "a2")));

    moduleC.add(code("c1", provides("c1"), requires("a1")));
    moduleC.add(code("c2", provides("c2"), requires("c1")));

    moduleE.add(code("e1", provides(), requires("c1")));
    moduleE.add(code("e2", provides(), requires("c1")));

    inputs.addAll(moduleA.getInputs());
    inputs.addAll(moduleB.getInputs());
    inputs.addAll(moduleC.getInputs());
    inputs.addAll(moduleE.getInputs());

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
      moduleA.removeAll();
      for (SourceFile sourceFile : sourceFiles) {
        moduleA.add(sourceFile);
      }

      for (CompilerInput input : moduleA.getInputs()) {
        input.setCompiler(compiler);
      }

      List<CompilerInput> results = graph.manageDependencies(compiler, depOptions);

      assertInputs(moduleA, "base.js", "a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9", "a1");

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
      moduleA.removeAll();
      for (SourceFile sourceFile : sourceFiles) {
        moduleA.add(sourceFile);
      }

      for (CompilerInput input : moduleA.getInputs()) {
        input.setCompiler(compiler);
        for (String require : orderedRequires.get(input.getSourceFile().getName())) {
          input.addOrderedRequire(Require.compilerModule(require));
        }
        input.setHasFullParseDependencyInfo(true);
      }

      List<CompilerInput> results = graph.manageDependencies(compiler, depOptions);

      assertInputs(
          moduleA,
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

    moduleA.add(weak1);
    moduleA.add(strong1);
    moduleA.add(weak2);
    moduleA.add(strong2);

    for (CompilerInput input : moduleA.getInputs()) {
      input.setCompiler(compiler);
    }

    makeGraph();

    assertThat(getWeakModule().getInputs().stream().map(CompilerInput::getSourceFile))
        .containsExactly(weak1, weak2);
    assertThat(moduleA.getInputs().stream().map(CompilerInput::getSourceFile))
        .containsExactly(strong1, strong2);
  }

  @Test
  public void testMoveMarkedWeakSourcesDuringManageDepsSortOnly() throws Exception {
    makeDeps();

    SourceFile weak1 = SourceFile.fromCode("weak1", "", SourceKind.WEAK);
    SourceFile weak2 = SourceFile.fromCode("weak2", "", SourceKind.WEAK);
    SourceFile strong1 = SourceFile.fromCode("strong1", "", SourceKind.STRONG);
    SourceFile strong2 = SourceFile.fromCode("strong2", "", SourceKind.STRONG);

    moduleA.add(weak1);
    moduleA.add(strong1);
    moduleA.add(weak2);
    moduleA.add(strong2);

    for (CompilerInput input : moduleA.getInputs()) {
      input.setCompiler(compiler);
    }

    makeGraph();
    graph.manageDependencies(compiler, DependencyOptions.sortOnly());

    assertThat(getWeakModule().getInputs().stream().map(CompilerInput::getSourceFile))
        .containsExactly(weak1, weak2);
    assertThat(moduleA.getInputs().stream().map(CompilerInput::getSourceFile))
        .containsExactly(strong1, strong2);
  }

  @Test
  public void testIgnoreMarkedWeakSourcesDuringManageDepsPrune() throws Exception {
    makeDeps();

    SourceFile weak1 = SourceFile.fromCode("weak1", "", SourceKind.WEAK);
    SourceFile weak2 = SourceFile.fromCode("weak2", "", SourceKind.WEAK);
    SourceFile strong1 = SourceFile.fromCode("strong1", "", SourceKind.STRONG);
    SourceFile strong2 = SourceFile.fromCode("strong2", "", SourceKind.STRONG);

    moduleA.add(weak1);
    moduleA.add(strong1);
    moduleA.add(weak2);
    moduleA.add(strong2);

    for (CompilerInput input : moduleA.getInputs()) {
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
    assertThat(moduleA.getInputs().stream().map(CompilerInput::getSourceFile))
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

    moduleA.add(weak1);
    moduleA.add(strong1);
    moduleA.add(weak2);
    moduleA.add(strong2);
    moduleA.add(weak1weak);
    moduleA.add(weak2strong);

    for (CompilerInput input : moduleA.getInputs()) {
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
    assertThat(moduleA.getInputs().stream().map(CompilerInput::getSourceFile))
        .containsExactly(strong1, strong2);
  }

  @Test
  public void testMoveImplicitWeakSourcesFromMoocherDuringManageDepsLegacyPrune() throws Exception {
    makeDeps();

    SourceFile weak = SourceFile.fromCode("weak", "goog.provide('weak');");
    SourceFile strong = SourceFile.fromCode("strong", "");
    SourceFile moocher = SourceFile.fromCode("moocher", "goog.requireType('weak');");

    moduleA.add(weak);
    moduleA.add(strong);
    moduleA.add(moocher);

    for (CompilerInput input : moduleA.getInputs()) {
      input.setCompiler(compiler);
    }

    makeGraph();
    graph.manageDependencies(
        compiler,
        DependencyOptions.pruneLegacyForEntryPoints(
            ImmutableList.of(ModuleIdentifier.forFile("strong"))));

    assertThat(getWeakModule().getInputs().stream().map(CompilerInput::getSourceFile))
        .containsExactly(weak);
    assertThat(moduleA.getInputs().stream().map(CompilerInput::getSourceFile))
        .containsExactly(strong, moocher);
  }

  @Test
  public void testImplicitWeakSourcesNotMovedDuringManageDepsSortOnly() throws Exception {
    makeDeps();

    SourceFile weak1 = SourceFile.fromCode("weak1", "goog.provide('weak1');");
    SourceFile weak2 = SourceFile.fromCode("weak2", "goog.provide('weak2');");
    SourceFile strong1 = SourceFile.fromCode("strong1", "goog.requireType('weak1');");
    SourceFile strong2 = SourceFile.fromCode("strong2", "goog.requireType('weak2');");

    moduleA.add(weak1);
    moduleA.add(strong1);
    moduleA.add(weak2);
    moduleA.add(strong2);

    for (CompilerInput input : moduleA.getInputs()) {
      input.setCompiler(compiler);
    }

    makeGraph();
    graph.manageDependencies(compiler, DependencyOptions.sortOnly());

    assertThat(getWeakModule().getInputs()).isEmpty();
    assertThat(moduleA.getInputs().stream().map(CompilerInput::getSourceFile))
        .containsExactly(weak1, strong1, weak2, strong2);
  }

  @Test
  public void testImplicitWeakSourcesMovedDuringManageDepsPrune() throws Exception {
    makeDeps();

    SourceFile weak1 = SourceFile.fromCode("weak1", "goog.provide('weak1');");
    SourceFile weak2 = SourceFile.fromCode("weak2", "goog.provide('weak2');");
    SourceFile strong1 = SourceFile.fromCode("strong1", "goog.requireType('weak1');");
    SourceFile strong2 = SourceFile.fromCode("strong2", "goog.requireType('weak2');");

    moduleA.add(weak1);
    moduleA.add(strong1);
    moduleA.add(weak2);
    moduleA.add(strong2);

    for (CompilerInput input : moduleA.getInputs()) {
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
    assertThat(moduleA.getInputs().stream().map(CompilerInput::getSourceFile))
        .containsExactly(strong1, strong2);
  }

  @Test
  public void testTransitiveWeakSources() throws Exception {
    makeDeps();

    SourceFile weak1 =
        SourceFile.fromCode(
            "weak1",
            "goog.provide('weak1'); goog.requireType('weak2'); goog.require('strongFromWeak');");
    SourceFile strongFromWeak = SourceFile.fromCode("weak1", "goog.provide('strongFromWeak');");
    SourceFile weak2 =
        SourceFile.fromCode("weak2", "goog.provide('weak2'); goog.requireType('weak3');");
    SourceFile weak3 = SourceFile.fromCode("weak3", "goog.provide('weak3');");
    SourceFile strong1 = SourceFile.fromCode("strong1", "goog.requireType('weak1');");

    moduleA.add(weak1);
    moduleA.add(strong1);
    moduleA.add(weak2);
    moduleA.add(weak3);
    moduleA.add(strongFromWeak);

    for (CompilerInput input : moduleA.getInputs()) {
      input.setCompiler(compiler);
    }

    makeGraph();
    graph.manageDependencies(
        compiler,
        DependencyOptions.pruneForEntryPoints(
            ImmutableList.of(ModuleIdentifier.forFile("strong1"))));

    assertThat(getWeakModule().getInputs().stream().map(CompilerInput::getSourceFile))
        .containsExactly(weak1, weak2, weak3, strongFromWeak);
    assertThat(moduleA.getInputs().stream().map(CompilerInput::getSourceFile))
        .containsExactly(strong1);
  }

  private void assertInputs(JSChunk module, String... sourceNames) {
    assertThat(sourceNames(module.getInputs())).isEqualTo(ImmutableList.copyOf(sourceNames));
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

  private void assertDeepestCommonDep(JSChunk expected, JSChunk m1, JSChunk m2) {
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
