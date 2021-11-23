/*
 * Copyright 2021 The Closure Compiler Authors.
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
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class RuntimeLibraryTypedAstInjectionTest extends CompilerTestCase {

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
    enableTypeCheck();
    replaceTypesWithColors();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return (externs, root) -> {};
  }

  @Test
  public void testInjection_carriesReconciledTypeInformation() {
    ensureLibraryInjected("util/owns");

    test(
        srcs("Object;"),
        expected(
            lines(
                "/** @const */ var $jscomp = $jscomp || {};", //
                "/** @const */ $jscomp.scope = {};",
                "$jscomp.owns = function(obj, prop) {",
                "  return Object.prototype.hasOwnProperty.call(obj, prop);",
                "};",
                "Object;")));

    ImmutableList<Node> objectNameNodes =
        findNodesNamed(this.getLastCompiler().getRoot(), "Object");
    assertThat(objectNameNodes).hasSize(2);

    Node injectedObjectNode = objectNameNodes.get(0);
    Node inferredObjectNode = objectNameNodes.get(1);
    assertThat(injectedObjectNode.getSourceFileName()).contains("util/owns");
    assertThat(inferredObjectNode.getSourceFileName()).contains("testcode");

    Color injectedObjectCtor = injectedObjectNode.getColor();
    Color inferredObjectCtor = inferredObjectNode.getColor();
    assertThat(injectedObjectCtor).isNotNull();
    assertThat(injectedObjectCtor).isSameInstanceAs(inferredObjectCtor);
  }

  @Test
  public void testInjection_excludesTypeInformation_ifTypecheckingHasNotRun() {
    ensureLibraryInjected("util/owns");
    disableTypeCheck();

    test(
        srcs("Object;"),
        expected(
            lines(
                "/** @const */ var $jscomp = $jscomp || {};", //
                "/** @const */ $jscomp.scope = {};",
                "$jscomp.owns = function(obj, prop) {",
                "  return Object.prototype.hasOwnProperty.call(obj, prop);",
                "};",
                "Object;")));

    ImmutableList<Node> objectNameNodes =
        findNodesNamed(this.getLastCompiler().getRoot(), "Object");
    assertThat(objectNameNodes).hasSize(2);

    Node injectedObjectNode = objectNameNodes.get(0);
    Node inferredObjectNode = objectNameNodes.get(1);
    assertThat(injectedObjectNode.getSourceFileName()).contains("util/owns");
    assertThat(inferredObjectNode.getSourceFileName()).contains("testcode");

    assertNode(injectedObjectNode).hasColorThat().isNull();
    assertNode(inferredObjectNode).hasColorThat().isNull();
  }

  @Test
  public void testInjection_doesntCrashOnLibrariesWithCasts_ifTypecheckingHasNotRun() {
    // note: we're testing "util/global" because (at the time lharker@) is writing this test it has
    // a cast, which triggers some edge cases in deserialization.
    ensureLibraryInjected("util/global");
    disableTypeCheck();

    testNoWarning(srcs("globalThis"));

    ImmutableList<Node> objectNameNodes =
        findNodesNamed(this.getLastCompiler().getRoot(), "globalThis");
    assertThat(objectNameNodes).hasSize(3);

    Node injectedGlobalThisNode = objectNameNodes.get(0);
    Node sourceGlobalThisNode = objectNameNodes.get(2);

    assertThat(injectedGlobalThisNode.getSourceFileName()).contains("util/global");
    assertThat(sourceGlobalThisNode.getSourceFileName()).contains("testcode");

    assertNode(injectedGlobalThisNode).hasColorThat().isNull();
    assertNode(sourceGlobalThisNode).hasColorThat().isNull();

    ImmutableList.Builder<Node> casts = ImmutableList.builder();
    findAllCastNodes(this.getLastCompiler().getRoot(), casts);
    // ensure that we don't inject CAST nodes into AST during optimizations
    assertThat(casts.build()).isEmpty();
  }

  private static ImmutableList<Node> findNodesNamed(Node root, String name) {
    ImmutableList.Builder<Node> results = ImmutableList.builder();
    findNodesNamed(root, name, results);
    return results.build();
  }

  private static void findNodesNamed(Node root, String name, ImmutableList.Builder<Node> results) {
    if (root.isName() && root.getString().equals(name)) {
      results.add(root);
    }

    for (Node child = root.getFirstChild(); child != null; child = child.getNext()) {
      findNodesNamed(child, name, results);
    }
  }

  private static void findAllCastNodes(Node root, ImmutableList.Builder<Node> results) {
    if (root.isCast()) {
      results.add(root);
    }

    for (Node child = root.getFirstChild(); child != null; child = child.getNext()) {
      findAllCastNodes(child, results);
    }
  }
}
