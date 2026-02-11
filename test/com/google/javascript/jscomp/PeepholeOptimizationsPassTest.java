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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for PeepholeOptimizationsPass. */
@RunWith(JUnit4.class)
public final class PeepholeOptimizationsPassTest extends CompilerTestCase {

  private ImmutableList<AbstractPeepholeOptimization> currentPeepholePasses;

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new PeepholeOptimizationsPass(
        compiler, getName(), currentPeepholePasses.toArray(new AbstractPeepholeOptimization[0]));
  }

  /**
   * PeepholeOptimizationsPass should handle the case when no peephole optimizations are turned on.
   */
  @Test
  public void testEmptyPass() {
    currentPeepholePasses = ImmutableList.of();

    testSame("var x; var y;");
  }

  @Test
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

    final List<String> visitationLog = new ArrayList<>();

    AbstractPeepholeOptimization note1Applied =
        new AbstractPeepholeOptimization() {
          @Override
          public Node optimizeSubtree(Node node) {
            if (node.isName()) {
              visitationLog.add(node.getString() + "1");
            }

            return node;
          }
        };

    AbstractPeepholeOptimization note2Applied =
        new AbstractPeepholeOptimization() {
          @Override
          public Node optimizeSubtree(Node node) {
            if (node.isName()) {
              visitationLog.add(node.getString() + "2");
            }

            return node;
          }
        };

    currentPeepholePasses = ImmutableList.of(note1Applied, note2Applied);

    testSame("var x; var y");

    /*
     * We expect the optimization order to be: "x" visited by optimization1 "x"
     * visited by optimization2 "y" visited by optimization1 "y" visited by
     * optimization2
     */
    assertThat(visitationLog).containsExactly("x1", "x2", "y1", "y2").inOrder();
  }

  /**
   * A peephole optimization that, given a subtree consisting of a VAR node, removes children of
   * that node named "x".
   */
  private static class RemoveNodesNamedXUnderVarOptimization extends AbstractPeepholeOptimization {
    @Override
    public Node optimizeSubtree(Node node) {
      if (node.isVar()) {
        Set<Node> nodesToRemove = new HashSet<>();

        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
          if ("x".equals(child.getString())) {
            nodesToRemove.add(child);
          }
        }

        for (Node childToRemove : nodesToRemove) {
          reportChangeToEnclosingScope(node);
          childToRemove.detach();
        }
      }

      return node;
    }
  }

  /**
   * A peephole optimization that, given a subtree consisting of a name node named "x" removes that
   * node.
   */
  private static class RemoveNodesNamedXOptimization extends AbstractPeepholeOptimization {
    @Override
    public Node optimizeSubtree(Node node) {
      if (node.isName() && "x".equals(node.getString())) {
        reportChangeToEnclosingScope(node);
        node.detach();

        return null;
      }

      return node;
    }
  }

  /**
   * A peephole optimization that, given a subtree consisting of a name node named "x" whose parent
   * is a VAR node, removes the parent VAR node.
   */
  private static class RemoveParentVarsForNodesNamedX extends AbstractPeepholeOptimization {
    @Override
    public Node optimizeSubtree(Node node) {
      if (node.isName() && "x".equals(node.getString())) {
        Node parent = node.getParent();
        if (parent.isVar()) {
          reportChangeToEnclosingScope(parent);
          parent.detach();
          return null;
        }
      }
      return node;
    }
  }

  /**
   * A peephole optimization that, given a subtree consisting of a name node named "y", replaces it
   * with a name node named "x";
   */
  private static class RenameYToX extends AbstractPeepholeOptimization {
    @Override
    public Node optimizeSubtree(Node node) {
      if (node.isName() && "y".equals(node.getString())) {
        Node replacement = Node.newString(Token.NAME, "x");

        node.replaceWith(replacement);
        reportChangeToEnclosingScope(replacement);

        return replacement;
      }
      return node;
    }
  }

  @Test
  public void testOptimizationRemovingSubtreeChild() {
    currentPeepholePasses =
        ImmutableList.<AbstractPeepholeOptimization>of(new RemoveNodesNamedXUnderVarOptimization());

    test("var x,y;", "var y;");
    test("var y,x;", "var y;");
    test("var x,y,x;", "var y;");
  }

  @Test
  public void testOptimizationRemovingSubtree() {
    currentPeepholePasses =
        ImmutableList.<AbstractPeepholeOptimization>of(new RemoveNodesNamedXOptimization());

    test("var x,y;", "var y;");
    test("var y,x;", "var y;");
    test("var x,y,x;", "var y;");
  }

  @Test
  public void testOptimizationRemovingSubtreeParent() {
    currentPeepholePasses =
        ImmutableList.<AbstractPeepholeOptimization>of(new RemoveParentVarsForNodesNamedX());

    test("var x; var y", "var y");
  }

  /**
   * Test the case where the first peephole optimization removes a node and the second wants to
   * remove (the now nonexistent) parent of that node.
   */
  @Test
  public void testOptimizationsRemoveParentAfterRemoveChild() {
    currentPeepholePasses =
        ImmutableList.of(new RemoveNodesNamedXOptimization(), new RemoveParentVarsForNodesNamedX());

    test("var x,y; var z;", "var y; var z;");
  }

  @Test
  public void testOptimizationReplacingNode() {
    currentPeepholePasses =
        ImmutableList.of(new RenameYToX(), new RemoveParentVarsForNodesNamedX());

    test("var y; var z;", "var z;");
  }

  @Test
  public void testAddFeatureToEnclosingScript() {
    currentPeepholePasses =
        ImmutableList.of(
            new AbstractPeepholeOptimization() {
              @Override
              public Node optimizeSubtree(Node node) {
                if (node.isAdd()) {
                  this.addFeatureToEnclosingScript(Feature.LET_DECLARATIONS);
                  this.addFeatureToEnclosingScript(Feature.LET_DECLARATIONS);
                  this.addFeatureToEnclosingScript(Feature.CLASSES);
                }
                return node;
              }
            },
            new AbstractPeepholeOptimization() {
              @Override
              public Node optimizeSubtree(Node node) {
                if (node.isSub()) {
                  this.addFeatureToEnclosingScript(Feature.CONST_DECLARATIONS);
                }
                return node;
              }
            });
    testSame("(3 + 4); function sub() { return 3 - 4; }");
    Compiler compiler = getLastCompiler();
    Node script = checkNotNull(compiler.getScriptNode("testcode"));
    assertThat(script.getProp(Node.FEATURE_SET))
        .isEqualTo(
            FeatureSet.BARE_MINIMUM.with(
                Feature.LET_DECLARATIONS, Feature.CLASSES, Feature.CONST_DECLARATIONS));
  }
}
