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

import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 */
@RunWith(JUnit4.class)
public final class CombinedCompilerPassTest {

  private Compiler compiler;

  /**
   * Returns a Node tree with the post-order traversal a b c d e f g h i j k l m and the in-order
   * traversal m d a b c h e f g l i j k:
   *
   * <p>m ,---------|---------. d h l ,--|--. ,--|--. ,--|--. a b c e f g i j k
   */
  private static Node createPostOrderAlphabet() {
    Node a = Node.newString("a");
    Node b = Node.newString("b");
    Node c = Node.newString("c");
    Node d = Node.newString("d");
    Node e = Node.newString("e");
    Node f = Node.newString("f");
    Node g = Node.newString("g");
    Node h = Node.newString("h");
    Node i = Node.newString("i");
    Node j = Node.newString("j");
    Node k = Node.newString("k");
    Node l = Node.newString("l");
    Node m = Node.newString("m");

    d.addChildToBack(a);
    d.addChildToBack(b);
    d.addChildToBack(c);

    h.addChildToBack(e);
    h.addChildToBack(f);
    h.addChildToBack(g);

    l.addChildToBack(i);
    l.addChildToBack(j);
    l.addChildToBack(k);

    m.addChildToBack(d);
    m.addChildToBack(h);
    m.addChildToBack(l);

    return m;
  }

  @Before
  public void setUp() throws Exception {
    compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
  }

  /**
   * Concatenates contents of string nodes encountered in pre-order and post-order traversals.
   * Abbreviates traversals by ignoring subtrees rooted with specified strings.
   */
  private static class ConcatTraversal implements NodeTraversal.Callback {
    private final StringBuilder visited = new StringBuilder();
    private final StringBuilder shouldTraversed = new StringBuilder();
    private final Set<String> ignoring = new HashSet<>();

    ConcatTraversal ignore(String s) {
      ignoring.add(s);
      return this;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      assertThat(n.getToken()).isEqualTo(Token.STRINGLIT);
      visited.append(n.getString());
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      assertThat(n.getToken()).isEqualTo(Token.STRINGLIT);
      shouldTraversed.append(n.getString());
      return !ignoring.contains(n.getString());
    }

    /** Returns strings concatenated during post-order traversal. */
    String getVisited() {
      return visited.toString();
    }

    /** Returns strings concatenated during pre-order traversal. */
    String getShouldTraversed() {
      return shouldTraversed.toString();
    }

    Collection<String> getIgnoring() {
      return ignoring;
    }
  }

  /**
   * Collection of data for a traversal test. Contains the traversal callback and the expected pre-
   * and post-order traversal results.
   */
  private static class TestHelper {
    private final ConcatTraversal traversal;
    private final String expectedVisited;
    private final String shouldTraverseExpected;

    TestHelper(ConcatTraversal traversal, String expectedVisited, String shouldTraverseExpected) {
      this.traversal = traversal;
      this.expectedVisited = expectedVisited;
      this.shouldTraverseExpected = shouldTraverseExpected;
    }

    ConcatTraversal getTraversal() {
      return traversal;
    }

    void checkResults() {
      assertWithMessage(
              "ConcatTraversal ignoring %s has unexpected visiting order", traversal.getIgnoring())
          .that(traversal.getVisited())
          .isEqualTo(expectedVisited);

      assertWithMessage(
              "ConcatTraversal ignoring %s has unexpected traversal order", traversal.getIgnoring())
          .that(traversal.getShouldTraversed())
          .isEqualTo(shouldTraverseExpected);
    }
  }

  private static List<TestHelper> createStringTests() {
    List<TestHelper> tests = new ArrayList<>();

    tests.add(new TestHelper(new ConcatTraversal(), "abcdefghijklm", "mdabchefglijk"));

    tests.add(new TestHelper(new ConcatTraversal().ignore("d"), "efghijklm", "mdhefglijk"));

    tests.add(new TestHelper(new ConcatTraversal().ignore("f"), "abcdeghijklm", "mdabchefglijk"));

    tests.add(new TestHelper(new ConcatTraversal().ignore("m"), "", "m"));

    return tests;
  }

  @Test
  public void testIndividualPasses() {
    for (TestHelper test : createStringTests()) {
      CombinedCompilerPass pass = new CombinedCompilerPass(compiler, test.getTraversal());
      pass.process(null, createPostOrderAlphabet());
      test.checkResults();
    }
  }

  @Test
  public void testCombinedPasses() {
    List<TestHelper> tests = createStringTests();
    NodeTraversal.Callback[] callbacks = new NodeTraversal.Callback[tests.size()];
    int i = 0;
    for (TestHelper test : tests) {
      callbacks[i++] = test.getTraversal();
    }
    CombinedCompilerPass pass = new CombinedCompilerPass(compiler, callbacks);
    pass.process(null, createPostOrderAlphabet());
    for (TestHelper test : tests) {
      test.checkResults();
    }
  }

  /**
   * Records the scopes visited during an AST traversal. Abbreviates traversals by ignoring subtrees
   * rooted with specified NAME nodes.
   */
  private static class ScopeRecordingCallback implements ScopedCallback {

    final Set<Node> visitedScopes = new HashSet<>();
    final Set<String> ignoring = new HashSet<>();

    void ignore(String name) {
      ignoring.add(name);
    }

    @Override
    public void enterScope(NodeTraversal t) {
      visitedScopes.add(t.getScopeRoot());
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      return !n.isName() || !ignoring.contains(n.getString());
    }

    Set<Node> getVisitedScopes() {
      return visitedScopes;
    }

    @Override
    public void exitScope(NodeTraversal t) {}

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {}
  }

  @Test
  public void testScopes() {
    Node root = compiler.parseTestCode("var y = function() { var x = function() { };}");

    ScopeRecordingCallback c1 = new ScopeRecordingCallback();
    c1.ignore("y");
    ScopeRecordingCallback c2 = new ScopeRecordingCallback();
    c2.ignore("x");
    ScopeRecordingCallback c3 = new ScopeRecordingCallback();

    CombinedCompilerPass pass = new CombinedCompilerPass(compiler, c1, c2, c3);
    pass.process(null, root);

    assertThat(c1.getVisitedScopes()).hasSize(1);
    assertThat(c2.getVisitedScopes()).hasSize(3);
    assertThat(c3.getVisitedScopes()).hasSize(5);
  }
}
