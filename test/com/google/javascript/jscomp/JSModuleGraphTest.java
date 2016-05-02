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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tests for {@link JSModuleGraph}
 *
 */
public final class JSModuleGraphTest extends TestCase {

  private final JSModule A = new JSModule("A");
  private final JSModule B = new JSModule("B");
  private final JSModule C = new JSModule("C");
  private final JSModule D = new JSModule("D");
  private final JSModule E = new JSModule("E");
  private final JSModule F = new JSModule("F");
  private JSModuleGraph graph = null;

  // For resolving dependencies only.
  private Compiler compiler;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    B.addDependency(A);  //     __A__
    C.addDependency(A);  //    /  |  \
    D.addDependency(B);  //   B   C  |
    E.addDependency(B);  //  / \ /|  |
    E.addDependency(C);  // D   E | /
    F.addDependency(A);  //      \|/
    F.addDependency(C);  //       F
    F.addDependency(E);
    graph = new JSModuleGraph(new JSModule[] {A, B, C, D, E, F});
    compiler = new Compiler();
  }

  public void testModuleDepth() {
    assertEquals("A should have depth 0", 0, A.getDepth());
    assertEquals("B should have depth 1", 1, B.getDepth());
    assertEquals("C should have depth 1", 1, C.getDepth());
    assertEquals("D should have depth 2", 2, D.getDepth());
    assertEquals("E should have depth 2", 2, E.getDepth());
    assertEquals("F should have depth 3", 3, F.getDepth());
  }

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

  public void testGetTransitiveDepsDeepestFirst() {
    assertTransitiveDepsDeepestFirst(A);
    assertTransitiveDepsDeepestFirst(B, A);
    assertTransitiveDepsDeepestFirst(C, A);
    assertTransitiveDepsDeepestFirst(D, B, A);
    assertTransitiveDepsDeepestFirst(E, C, B, A);
    assertTransitiveDepsDeepestFirst(F, E, C, B, A);
  }

  public void testCoalesceDuplicateFiles() {
    A.add(SourceFile.fromCode("a.js", ""));

    B.add(SourceFile.fromCode("a.js", ""));
    B.add(SourceFile.fromCode("b.js", ""));

    C.add(SourceFile.fromCode("b.js", ""));
    C.add(SourceFile.fromCode("c.js", ""));

    E.add(SourceFile.fromCode("c.js", ""));
    E.add(SourceFile.fromCode("d.js", ""));

    graph.coalesceDuplicateFiles();

    assertThat(A.getInputs()).hasSize(2);
    assertEquals("a.js", A.getInputs().get(0).getName());
    assertEquals("b.js", A.getInputs().get(1).getName());
    assertThat(B.getInputs()).isEmpty();
    assertThat(C.getInputs()).hasSize(1);
    assertEquals("c.js", C.getInputs().get(0).getName());
    assertThat(E.getInputs()).hasSize(1);
    assertEquals("d.js", E.getInputs().get(0).getName());
  }

  public void testManageDependencies1() throws Exception {
    List<CompilerInput> inputs = setUpManageDependenciesTest();
    DependencyOptions depOptions = new DependencyOptions();
    depOptions.setDependencySorting(true);
    depOptions.setDependencyPruning(true);
    depOptions.setEntryPoints(ImmutableList.<ModuleIdentifier>of());
    List<CompilerInput> results = graph.manageDependencies(depOptions, inputs);

    assertInputs(A, "a1", "a3");
    assertInputs(B, "a2", "b2");
    assertInputs(C); // no inputs
    assertInputs(E, "c1", "e1", "e2");

    assertEquals(
        ImmutableList.of("a1", "a3", "a2", "b2", "c1", "e1", "e2"),
        sourceNames(results));
  }

  public void testManageDependencies2() throws Exception {
    List<CompilerInput> inputs = setUpManageDependenciesTest();
    DependencyOptions depOptions = new DependencyOptions();
    depOptions.setDependencySorting(true);
    depOptions.setDependencyPruning(true);
    depOptions.setEntryPoints(
        ImmutableList.of(ModuleIdentifier.forClosure("c2")));
    List<CompilerInput> results = graph.manageDependencies(depOptions, inputs);

    assertInputs(A, "a1", "a3");
    assertInputs(B, "a2", "b2");
    assertInputs(C, "c1", "c2");
    assertInputs(E, "e1", "e2");

    assertEquals(
        ImmutableList.of("a1", "a3", "a2", "b2", "c1", "c2", "e1", "e2"),
        sourceNames(results));
  }

  public void testManageDependencies3Impl() throws Exception {
    List<CompilerInput> inputs = setUpManageDependenciesTest();
    DependencyOptions depOptions = new DependencyOptions();
    depOptions.setDependencySorting(true);
    depOptions.setDependencyPruning(true);
    depOptions.setMoocherDropping(true);
    depOptions.setEntryPoints(
        ImmutableList.of(ModuleIdentifier.forClosure("c2")));
    List<CompilerInput> results = graph.manageDependencies(depOptions, inputs);

    // Everything gets pushed up into module c, because that's
    // the only one that has entry points.
    assertInputs(A);
    assertInputs(B);
    assertInputs(C, "a1", "c1", "c2");
    assertInputs(E);

    assertEquals(ImmutableList.of("a1", "c1", "c2"), sourceNames(results));
  }

  public void testManageDependencies4() throws Exception {
    setUpManageDependenciesTest();
    DependencyOptions depOptions = new DependencyOptions();
    depOptions.setDependencySorting(true);

    List<CompilerInput> inputs = new ArrayList<>();

    // Add the inputs in a random order.
    inputs.addAll(E.getInputs());
    inputs.addAll(B.getInputs());
    inputs.addAll(A.getInputs());
    inputs.addAll(C.getInputs());

    List<CompilerInput> results = graph.manageDependencies(depOptions, inputs);

    assertInputs(A, "a1", "a2", "a3");
    assertInputs(B, "b1", "b2");
    assertInputs(C, "c1", "c2");
    assertInputs(E, "e1", "e2");

    assertEquals(
        ImmutableList.of(
            "a1", "a2", "a3", "b1", "b2", "c1", "c2", "e1", "e2"),
        sourceNames(results));
  }

  private static final String BASEJS = Joiner.on("\n").join(
      "/** @provideGoog */",
      "var COMPILED = false;",
      "var goog = goog || {}");

  public void testManageDependencies5Impl() throws Exception {
    A.add(code("a2", provides("a2"), requires("a1")));
    A.add(code("a1", provides("a1"), requires()));
    A.add(code("base.js", BASEJS, provides(), requires()));

    for (CompilerInput input : A.getInputs()) {
      input.setCompiler(compiler);
    }

    DependencyOptions depOptions = new DependencyOptions();
    depOptions.setDependencySorting(true);

    List<CompilerInput> inputs = new ArrayList<>();
    inputs.addAll(A.getInputs());
    List<CompilerInput> results = graph.manageDependencies(
        depOptions, inputs);

    assertInputs(A, "base.js", "a1", "a2");

    assertThat(sourceNames(results)).containsExactly("base.js", "a1", "a2").inOrder();
  }

  public void testNoFiles() throws Exception {
    DependencyOptions depOptions = new DependencyOptions();
    depOptions.setDependencySorting(true);

    List<CompilerInput> inputs = new ArrayList<>();
    List<CompilerInput> results = graph.manageDependencies(
        depOptions, inputs);
    assertThat(results).isEmpty();
  }

  public void testToJson() throws JsonParseException {
    JsonArray modules = graph.toJson();
    assertEquals(6, modules.size());
    for (int i = 0; i < modules.size(); i++) {
      JsonObject m = modules.get(i).getAsJsonObject();
      assertNotNull(m.get("name"));
      assertNotNull(m.get("dependencies"));
      assertNotNull(m.get("transitive-dependencies"));
      assertNotNull(m.get("inputs"));
    }
    JsonObject m = modules.get(3).getAsJsonObject();
    assertEquals("D", m.get("name").getAsString());
    assertEquals("[\"B\"]", m.get("dependencies").getAsJsonArray().toString());
    assertEquals(2,
        m.get("transitive-dependencies").getAsJsonArray().size());
    assertEquals("[]", m.get("inputs").getAsJsonArray().toString());
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

  private void assertInputs(JSModule module, String ... sourceNames) {
    assertEquals(
        ImmutableList.copyOf(sourceNames),
        sourceNames(module.getInputs()));
  }

  private List<String> sourceNames(List<CompilerInput> inputs) {
    List<String> inputNames = new ArrayList<>();
    for (CompilerInput input : inputs) {
      inputNames.add(input.getName());
    }
    return inputNames;
  }

  private SourceFile code(
      String sourceName, List<String> provides, List<String> requires) {
    return code(sourceName, "", provides, requires);
  }

  private SourceFile code(
      String sourceName, String source,
      List<String> provides, List<String> requires) {
    String text = "";
    for (String p : provides) {
      text += "goog.provide('" + p + "');\n";
    }
    for (String r : requires) {
      text += "goog.require('" + r + "');\n";
    }
    return SourceFile.fromCode(sourceName, text + source);
  }

  private List<String> provides(String ... strings) {
    return ImmutableList.copyOf(strings);
  }

  private List<String> requires(String ... strings) {
    return ImmutableList.copyOf(strings);
  }

  private void assertDeepestCommonDepInclusive(
      JSModule expected, JSModule m1, JSModule m2) {
    assertDeepestCommonDepOneWay(expected, m1, m2, true);
    assertDeepestCommonDepOneWay(expected, m2, m1, true);
  }

  private void assertDeepestCommonDep(
      JSModule expected, JSModule m1, JSModule m2) {
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
      fail(String.format(
          "Deepest common dep of %s and %s should be %s but was %s",
          m1.getName(), m2.getName(),
          expected == null ? "null" : expected.getName(),
          actual ==  null ? "null" : actual.getName()));
    }
  }

  private void assertTransitiveDepsDeepestFirst(JSModule m, JSModule... deps) {
    Iterable<JSModule> actual = graph.getTransitiveDepsDeepestFirst(m);
    assertEquals(Arrays.toString(deps),
                 Arrays.toString(Iterables.toArray(actual, JSModule.class)));
  }
}
