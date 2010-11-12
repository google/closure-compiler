/*
 * Copyright 2010 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.List;
import java.util.Set;

/**
 * Unit tests for PeepholeOptimizationsPass.
 *
 */
public class PeepholeOptimizationsPassTest extends CompilerTestCase {

  private ImmutableSet<AbstractPeepholeOptimization> currentPeepholePasses;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    super.enableLineNumberCheck(true);
  }

  @Override
  public CompilerPass getProcessor(final Compiler compiler) {
    return new PeepholeOptimizationsPass(compiler, currentPeepholePasses);
  }

  @Override
  protected int getNumRepetitions() {
    // Our tests do not require multiple passes to reach a fixed-point.
    return 1;
  }

  /**
   * PeepholeOptimizationsPass should handle the case when no peephole
   * optimizations are turned on.
   */
  public void testEmptyPass() {
    currentPeepholePasses = ImmutableSet.<AbstractPeepholeOptimization>of();

    testSame("var x; var y;");
  }

  public void testOptimizationOrder() {
    /*
     * We need to make sure that: 1) We are only traversing the AST once 2) For
     * each node, we visit the optimizations in the client-supplied order
     *
     * To test this, we create two fake optimizations that each make an entry in
     * the visitationLog when they are passed a name node to optimize.
     *
     * Each entry is of the form nameX where 'name' is the name of the name node
     * visited and X is the identity of the optimization (1 or 2 in this case).
     * After the pass is run, we verify the correct ordering by querying the
     * log.
     *
     * Using a log, rather than, say, transforming nodes, allows us to ensure
     * not only that we are visiting each node but that our visits occur in the
     * right order (i.e. we need to make sure we're not traversing the entire
     * AST for the first optimization and then a second time for the second).
     */

    final List<String> visitationLog = Lists.newArrayList();

    AbstractPeepholeOptimization note1Applied =
        new AbstractPeepholeOptimization() {
      @Override
      public Node optimizeSubtree(Node node) {
        if (node.getType() == Token.NAME) {
          visitationLog.add(node.getString() + "1");
        }

        return node;
      }
    };

    AbstractPeepholeOptimization note2Applied =
        new AbstractPeepholeOptimization() {
      @Override
      public Node optimizeSubtree(Node node) {
        if (node.getType() == Token.NAME) {
          visitationLog.add(node.getString() + "2");
        }

        return node;
      }
    };

    currentPeepholePasses =
      ImmutableSet.<AbstractPeepholeOptimization>of(note1Applied, note2Applied);

    test("var x; var y", "var x; var y");

    /*
     * We expect the optimization order to be: "x" visited by optimization1 "x"
     * visited by optimization2 "y" visited by optimization1 "y" visited by
     * optimization2
     */

    assertEquals(4, visitationLog.size());
    assertEquals("x1", visitationLog.get(0));
    assertEquals("x2", visitationLog.get(1));
    assertEquals("y1", visitationLog.get(2));
    assertEquals("y2", visitationLog.get(3));
  }

  /**
   * A peephole optimization that, given a subtree consisting of a VAR node,
   * removes children of that node named "x".
   */
  private static class RemoveNodesNamedXUnderVarOptimization
      extends AbstractPeepholeOptimization {
    @Override
    public Node optimizeSubtree(Node node) {
      if (node.getType() == Token.VAR) {
        Set<Node> nodesToRemove = Sets.newHashSet();

        for (Node child : node.children()) {
          if ("x".equals(child.getString())) {
            nodesToRemove.add(child);
          }
        }

        for (Node childToRemove : nodesToRemove) {
          node.removeChild(childToRemove);
          reportCodeChange();
        }
      }

      return node;
    }
  }

  /**
   * A peephole optimization that, given a subtree consisting of a name node
   * named "x" removes that node.
   */
  private static class RemoveNodesNamedXOptimization
      extends AbstractPeepholeOptimization {
    @Override
    public Node optimizeSubtree(Node node) {
      if (node.getType() == Token.NAME && "x".equals(node.getString())) {
        node.getParent().removeChild(node);
        reportCodeChange();

        return null;
      }

      return node;
    }
  }

  /**
   * A peephole optimization that, given a subtree consisting of a name node
   * named "x" whose parent is a VAR node, removes the parent VAR node.
   */
  private static class RemoveParentVarsForNodesNamedX
      extends AbstractPeepholeOptimization {
    @Override
    public Node optimizeSubtree(Node node) {
      if (node.getType() == Token.NAME && "x".equals(node.getString())) {
        Node parent = node.getParent();
        if (parent.getType() == Token.VAR) {
          parent.getParent().removeChild(parent);
          reportCodeChange();
          return null;
        }
      }
      return node;
    }
  }

  /**
   * A peephole optimization that, given a subtree consisting of a name node
   * named "y", replaces it with a name node named "x";
   */
  private static class RenameYToX extends AbstractPeepholeOptimization {
    @Override
    public Node optimizeSubtree(Node node) {
      if (node.getType() == Token.NAME && "y".equals(node.getString())) {
        Node replacement = Node.newString(Token.NAME, "x");

        node.getParent().replaceChild(node, replacement);
        reportCodeChange();

        return replacement;
      }
      return node;
    }
  }

  public void testOptimizationRemovingSubtreeChild() {
    currentPeepholePasses = ImmutableSet.<AbstractPeepholeOptimization>of(new
          RemoveNodesNamedXUnderVarOptimization());

    test("var x,y;", "var y;");
    test("var y,x;", "var y;");
    test("var x,y,x;", "var y;");
  }

  public void testOptimizationRemovingSubtree() {
    currentPeepholePasses = ImmutableSet.<AbstractPeepholeOptimization>of(new
          RemoveNodesNamedXOptimization());

    test("var x,y;", "var y;");
    test("var y,x;", "var y;");
    test("var x,y,x;", "var y;");
  }

  public void testOptimizationRemovingSubtreeParent() {
    currentPeepholePasses = ImmutableSet.<AbstractPeepholeOptimization>of(new
          RemoveParentVarsForNodesNamedX());

    test("var x; var y", "var y");
  }

  /**
   * Test the case where the first peephole optimization removes a node and the
   * second wants to remove (the now nonexistent) parent of that node.
   */
  public void testOptimizationsRemoveParentAfterRemoveChild() {
    currentPeepholePasses = ImmutableSet.<AbstractPeepholeOptimization>of(
          new RemoveNodesNamedXOptimization(),
          new RemoveParentVarsForNodesNamedX());

    test("var x,y; var z;", "var y; var z;");
  }

  public void testOptimizationReplacingNode() {
    currentPeepholePasses = ImmutableSet.<AbstractPeepholeOptimization>of(
          new RenameYToX(),
          new RemoveParentVarsForNodesNamedX());

    test("var y; var z;", "var z;");
  }
}
