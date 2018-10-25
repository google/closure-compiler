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
import static java.util.Collections.shuffle;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.javascript.jscomp.deps.DependencyInfo.Require;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link JSModuleGraph}
 *
 */
@RunWith(JUnit4.class)
public final class JSModuleGraphTest {

  // NOTE: These are not static. It would probably be clearer to initialize them in setUp()
  private final JSModule A = new JSModule("A");
  private final JSModule B = new JSModule("B");
  private final JSModule C = new JSModule("C");
  private final JSModule D = new JSModule("D");
  private final JSModule E = new JSModule("E");
  private final JSModule F = new JSModule("F");
  private JSModuleGraph graph = null;

  // For resolving dependencies only.
  private Compiler compiler;

  @Before
  public void setUp() throws Exception {
    B.addDependency(A); //     __A__
    C.addDependency(A); //    /  |  \
    D.addDependency(B); //   B   C  |
    E.addDependency(B); //  / \ /|  |
    E.addDependency(C); // D   E | /
    F.addDependency(A); //      \|/
    F.addDependency(C); //       F
    F.addDependency(E);
    graph = new JSModuleGraph(new JSModule[] {A, B, C, D, E, F});
    compiler = new Compiler();
  }

  @Test
  public void testSmallerTreeBeatsDeeperTree() {
    final JSModule a = new JSModule("a");
    final JSModule b = new JSModule("b");
    final JSModule c = new JSModule("c");
    final JSModule d = new JSModule("d");
    final JSModule e = new JSModule("e");
    final JSModule f = new JSModule("f");
    final JSModule g = new JSModule("g");
    final JSModule h = new JSModule("h");
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
    JSModuleGraph graph = new JSModuleGraph(new JSModule[] {a, b, c, d, e, f, g, h});
    // d is deeper, but it also has an extra dependent node, so b is the better choice.
    assertSmallestCoveringSubtree(b, graph, a, e, f, g);
    // However, if the parent tree we're looking at is c, then b isn't an option
    assertSmallestCoveringSubtree(d, graph, c, e, f, g);
  }

  @Test
  public void testModuleDepth() {
    assertWithMessage("A should have depth 0").that(A.getDepth()).isEqualTo(0);
    assertWithMessage("B should have depth 1").that(B.getDepth()).isEqualTo(1);
    assertWithMessage("C should have depth 1").that(C.getDepth()).isEqualTo(1);
    assertWithMessage("D should have depth 2").that(D.getDepth()).isEqualTo(2);
    assertWithMessage("E should have depth 2").that(E.getDepth()).isEqualTo(2);
    assertWithMessage("F should have depth 3").that(F.getDepth()).isEqualTo(3);
  }

  @Test
  public void testDeepestCommonDep() {
    assertDeepestCommonDep(null, A, A);
    assertDeepestCommonDep(null, A, B);
    assertDeepestCommonDep(null, A, C);
    assertDeepestCommonDep(null, A, D);
    assertDeepestCommonDep(null, A, E);
    assertDeepestCommonDep(null, A, F);
    assertDeepestCommonDep(A, B, B);
    assertDeepestCommonDep(A, B, C);
    assertDeepestCommonDep(A, B, D);
    assertDeepestCommonDep(A, B, E);
    assertDeepestCommonDep(A, B, F);
    assertDeepestCommonDep(A, C, C);
    assertDeepestCommonDep(A, C, D);
    assertDeepestCommonDep(A, C, E);
    assertDeepestCommonDep(A, C, F);
    assertDeepestCommonDep(B, D, D);
    assertDeepestCommonDep(B, D, E);
    assertDeepestCommonDep(B, D, F);
    assertDeepestCommonDep(C, E, E);
    assertDeepestCommonDep(C, E, F);
    assertDeepestCommonDep(E, F, F);
  }

  @Test
  public void testDeepestCommonDepInclusive() {
    assertDeepestCommonDepInclusive(A, A, A);
    assertDeepestCommonDepInclusive(A, A, B);
    assertDeepestCommonDepInclusive(A, A, C);
    assertDeepestCommonDepInclusive(A, A, D);
    assertDeepestCommonDepInclusive(A, A, E);
    assertDeepestCommonDepInclusive(A, A, F);
    assertDeepestCommonDepInclusive(B, B, B);
    assertDeepestCommonDepInclusive(A, B, C);
    assertDeepestCommonDepInclusive(B, B, D);
    assertDeepestCommonDepInclusive(B, B, E);
    assertDeepestCommonDepInclusive(B, B, F);
    assertDeepestCommonDepInclusive(C, C, C);
    assertDeepestCommonDepInclusive(A, C, D);
    assertDeepestCommonDepInclusive(C, C, E);
    assertDeepestCommonDepInclusive(C, C, F);
    assertDeepestCommonDepInclusive(D, D, D);
    assertDeepestCommonDepInclusive(B, D, E);
    assertDeepestCommonDepInclusive(B, D, F);
    assertDeepestCommonDepInclusive(E, E, E);
    assertDeepestCommonDepInclusive(E, E, F);
    assertDeepestCommonDepInclusive(F, F, F);
  }

  @Test
  public void testSmallestCoveringSubtree() {
    assertSmallestCoveringSubtree(A, A, A, A);
    assertSmallestCoveringSubtree(A, A, A, B);
    assertSmallestCoveringSubtree(A, A, A, C);
    assertSmallestCoveringSubtree(A, A, A, D);
    assertSmallestCoveringSubtree(A, A, A, E);
    assertSmallestCoveringSubtree(A, A, A, F);
    assertSmallestCoveringSubtree(B, A, B, B);
    assertSmallestCoveringSubtree(A, A, B, C);
    assertSmallestCoveringSubtree(B, A, B, D);
    assertSmallestCoveringSubtree(B, A, B, E);
    assertSmallestCoveringSubtree(B, A, B, F);
    assertSmallestCoveringSubtree(C, A, C, C);
    assertSmallestCoveringSubtree(A, A, C, D);
    assertSmallestCoveringSubtree(C, A, C, E);
    assertSmallestCoveringSubtree(C, A, C, F);
    assertSmallestCoveringSubtree(D, A, D, D);
    assertSmallestCoveringSubtree(B, A, D, E);
    assertSmallestCoveringSubtree(B, A, D, F);
    assertSmallestCoveringSubtree(E, A, E, E);
    assertSmallestCoveringSubtree(E, A, E, F);
    assertSmallestCoveringSubtree(F, A, F, F);
  }

  @Test
  public void testGetTransitiveDepsDeepestFirst() {
    assertTransitiveDepsDeepestFirst(A);
    assertTransitiveDepsDeepestFirst(B, A);
    assertTransitiveDepsDeepestFirst(C, A);
    assertTransitiveDepsDeepestFirst(D, B, A);
    assertTransitiveDepsDeepestFirst(E, C, B, A);
    assertTransitiveDepsDeepestFirst(F, E, C, B, A);
  }

  @Test
  public void testManageDependencies1() throws Exception {
    setUpManageDependenciesTest();
    DependencyOptions depOptions = new DependencyOptions();
    depOptions.setDependencySorting(true);
    depOptions.setDependencyPruning(true);
    depOptions.setEntryPoints(ImmutableList.<ModuleIdentifier>of());
    List<CompilerInput> results = graph.manageDependencies(depOptions);

    assertInputs(A, "a1", "a3");
    assertInputs(B, "a2", "b2");
    assertInputs(C); // no inputs
    assertInputs(E, "c1", "e1", "e2");

    assertThat(sourceNames(results))
        .isEqualTo(ImmutableList.of("a1", "a3", "a2", "b2", "c1", "e1", "e2"));
  }

  @Test
  public void testManageDependencies2() throws Exception {
    setUpManageDependenciesTest();
    DependencyOptions depOptions = new DependencyOptions();
    depOptions.setDependencySorting(true);
    depOptions.setDependencyPruning(true);
    depOptions.setEntryPoints(ImmutableList.of(ModuleIdentifier.forClosure("c2")));
    List<CompilerInput> results = graph.manageDependencies(depOptions);

    assertInputs(A, "a1", "a3");
    assertInputs(B, "a2", "b2");
    assertInputs(C, "c1", "c2");
    assertInputs(E, "e1", "e2");

    assertThat(sourceNames(results))
        .isEqualTo(ImmutableList.of("a1", "a3", "a2", "b2", "c1", "c2", "e1", "e2"));
  }

  @Test
  public void testManageDependencies3Impl() throws Exception {
    setUpManageDependenciesTest();
    DependencyOptions depOptions = new DependencyOptions();
    depOptions.setDependencySorting(true);
    depOptions.setDependencyPruning(true);
    depOptions.setMoocherDropping(true);
    depOptions.setEntryPoints(ImmutableList.of(ModuleIdentifier.forClosure("c2")));
    List<CompilerInput> results = graph.manageDependencies(depOptions);

    // Everything gets pushed up into module c, because that's
    // the only one that has entry points.
    assertInputs(A);
    assertInputs(B);
    assertInputs(C, "a1", "c1", "c2");
    assertInputs(E);

    assertThat(sourceNames(results)).containsExactly("a1", "c1", "c2").inOrder();
  }

  @Test
  public void testManageDependencies4() throws Exception {
    setUpManageDependenciesTest();
    DependencyOptions depOptions = new DependencyOptions();
    depOptions.setDependencySorting(true);

    List<CompilerInput> results = graph.manageDependencies(depOptions);

    assertInputs(A, "a1", "a2", "a3");
    assertInputs(B, "b1", "b2");
    assertInputs(C, "c1", "c2");
    assertInputs(E, "e1", "e2");

    assertThat(sourceNames(results))
        .isEqualTo(ImmutableList.of("a1", "a2", "a3", "b1", "b2", "c1", "c2", "e1", "e2"));
  }

  // NOTE: The newline between the @provideGoog comment and the var statement is required.
  private static final String BASEJS =
      "/** @provideGoog */\nvar COMPILED = false; var goog = goog || {}";

  @Test
  public void testManageDependencies5Impl() throws Exception {
    A.add(code("a2", provides("a2"), requires("a1")));
    A.add(code("a1", provides("a1"), requires()));
    A.add(code("base.js", BASEJS, provides(), requires()));

    for (CompilerInput input : A.getInputs()) {
      input.setCompiler(compiler);
    }

    DependencyOptions depOptions = new DependencyOptions();
    depOptions.setDependencySorting(true);

    List<CompilerInput> results = graph.manageDependencies(depOptions);

    assertInputs(A, "base.js", "a1", "a2");

    assertThat(sourceNames(results)).containsExactly("base.js", "a1", "a2").inOrder();
  }

  @Test
  public void testNoFiles() throws Exception {
    DependencyOptions depOptions = new DependencyOptions();
    depOptions.setDependencySorting(true);

    List<CompilerInput> results = graph.manageDependencies(depOptions);
    assertThat(results).isEmpty();
  }

  @Test
  public void testToJson() {
    JsonArray modules = graph.toJson();
    assertThat(modules.size()).isEqualTo(6);
    for (int i = 0; i < modules.size(); i++) {
      JsonObject m = modules.get(i).getAsJsonObject();
      assertThat(m.get("name")).isNotNull();
      assertThat(m.get("dependencies")).isNotNull();
      assertThat(m.get("transitive-dependencies")).isNotNull();
      assertThat(m.get("inputs")).isNotNull();
    }
    JsonObject m = modules.get(3).getAsJsonObject();
    assertThat(m.get("name").getAsString()).isEqualTo("D");
    assertThat(m.get("dependencies").getAsJsonArray().toString()).isEqualTo("[\"B\"]");
    assertThat(m.get("transitive-dependencies").getAsJsonArray().size()).isEqualTo(2);
    assertThat(m.get("inputs").getAsJsonArray().toString()).isEqualTo("[]");
  }

  private List<CompilerInput> setUpManageDependenciesTest() {
    List<CompilerInput> inputs = new ArrayList<>();

    A.add(code("a1", provides("a1"), requires()));
    A.add(code("a2", provides("a2"), requires("a1")));
    A.add(code("a3", provides(), requires("a1")));

    B.add(code("b1", provides("b1"), requires("a2")));
    B.add(code("b2", provides(), requires("a1", "a2")));

    C.add(code("c1", provides("c1"), requires("a1")));
    C.add(code("c2", provides("c2"), requires("c1")));

    E.add(code("e1", provides(), requires("c1")));
    E.add(code("e2", provides(), requires("c1")));

    inputs.addAll(A.getInputs());
    inputs.addAll(B.getInputs());
    inputs.addAll(C.getInputs());
    inputs.addAll(E.getInputs());

    for (CompilerInput input : inputs) {
      input.setCompiler(compiler);
    }
    return inputs;
  }

  @Test
  public void testGoogBaseOrderedCorrectly() throws Exception {
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

    DependencyOptions depOptions = new DependencyOptions();
    depOptions.setDependencySorting(true);
    depOptions.setDependencyPruning(true);
    depOptions.setMoocherDropping(true);
    depOptions.setEntryPoints(ImmutableList.of(ModuleIdentifier.forClosure("a1")));
    for (int i = 0; i < 10; i++) {
      shuffle(sourceFiles);
      A.removeAll();
      for (SourceFile sourceFile : sourceFiles) {
        A.add(sourceFile);
      }

      for (CompilerInput input : A.getInputs()) {
        input.setCompiler(compiler);
      }

      List<CompilerInput> results = graph.manageDependencies(depOptions);

      assertInputs(A, "base.js", "a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9", "a1");

      assertThat(sourceNames(results))
          .containsExactly("base.js", "a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9", "a1")
          .inOrder();
    }
  }

  @Test
  public void testProperEs6ModuleOrdering() throws Exception {
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

    DependencyOptions depOptions = new DependencyOptions();
    depOptions.setDependencySorting(true);
    depOptions.setDependencyPruning(true);
    depOptions.setMoocherDropping(true);
    depOptions.setEntryPoints(ImmutableList.of(ModuleIdentifier.forFile("/entry.js")));
    for (int iterationCount = 0; iterationCount < 10; iterationCount++) {
      shuffle(sourceFiles);
      A.removeAll();
      for (SourceFile sourceFile : sourceFiles) {
        A.add(sourceFile);
      }

      for (CompilerInput input : A.getInputs()) {
        input.setCompiler(compiler);
        for (String require : orderedRequires.get(input.getSourceFile().getName())) {
          input.addOrderedRequire(Require.compilerModule(require));
        }
        input.setHasFullParseDependencyInfo(true);
      }

      List<CompilerInput> results = graph.manageDependencies(depOptions);

      assertInputs(
          A, "/b/c.js", "/b/b.js", "/b/a.js", "/important.js", "/a/b.js", "/a/a.js", "/entry.js");

      assertThat(sourceNames(results))
          .containsExactly(
              "/b/c.js", "/b/b.js", "/b/a.js", "/important.js", "/a/b.js", "/a/a.js", "/entry.js")
          .inOrder();
    }
  }

  private void assertInputs(JSModule module, String... sourceNames) {
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
      JSModule expected, JSModule parentTree, JSModule... modules) {
    assertSmallestCoveringSubtree(expected, graph, parentTree, modules);
  }

  private void assertSmallestCoveringSubtree(
      JSModule expected, JSModuleGraph graph, JSModule parentTree, JSModule... modules) {
    BitSet modulesBitSet = new BitSet();
    for (JSModule m : modules) {
      modulesBitSet.set(m.getIndex());
    }
    assertSmallestCoveringSubtree(expected, graph, parentTree, modulesBitSet);
  }

  private void assertSmallestCoveringSubtree(
      JSModule expected, JSModuleGraph graph, JSModule parentTree, BitSet modules) {
    JSModule actual = graph.getSmallestCoveringSubtree(parentTree, modules);
    assertWithMessage(
            "Smallest covering subtree of %s in %s should be %s but was %s",
            parentTree, modules, expected, actual)
        .that(actual)
        .isEqualTo(expected);
  }

  private void assertDeepestCommonDepInclusive(JSModule expected, JSModule m1, JSModule m2) {
    assertDeepestCommonDepOneWay(expected, m1, m2, true);
    assertDeepestCommonDepOneWay(expected, m2, m1, true);
  }

  private void assertDeepestCommonDep(JSModule expected, JSModule m1, JSModule m2) {
    assertDeepestCommonDepOneWay(expected, m1, m2, false);
    assertDeepestCommonDepOneWay(expected, m2, m1, false);
  }

  private void assertDeepestCommonDepOneWay(
      JSModule expected, JSModule m1, JSModule m2, boolean inclusive) {
    JSModule actual =
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

  private void assertTransitiveDepsDeepestFirst(JSModule m, JSModule... deps) {
    Iterable<JSModule> actual = graph.getTransitiveDepsDeepestFirst(m);
    assertThat(Arrays.toString(Iterables.toArray(actual, JSModule.class)))
        .isEqualTo(Arrays.toString(deps));
  }
}
