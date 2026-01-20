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


import com.google.common.annotations.VisibleForTesting;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.Node;
import java.util.Arrays;
import java.util.List;

/**
 * A compiler pass to run various peephole optimizations (e.g. constant folding,
 * some useless code removal, some minimizations).
 */
class PeepholeOptimizationsPass implements CompilerPass {

  private final AbstractCompiler compiler;
  private final String passName;
  // NOTE: Use a native array rather than a List to avoid creating iterators for every node in the
  // AST.
  private final AbstractPeepholeOptimization[] peepholeOptimizations;
  private boolean retraverseOnChange;

  /** Creates a peephole optimization pass that runs the given optimizations. */
  PeepholeOptimizationsPass(
      AbstractCompiler compiler, String passName, AbstractPeepholeOptimization... optimizations) {
    this(compiler, passName, Arrays.asList(optimizations));
  }

  PeepholeOptimizationsPass(
      AbstractCompiler compiler,
      String passName,
      List<AbstractPeepholeOptimization> optimizations) {
    this.compiler = compiler;
    this.passName = passName;
    this.peepholeOptimizations = optimizations.toArray(new AbstractPeepholeOptimization[0]);
    this.retraverseOnChange = true;
  }

  @VisibleForTesting
  void setRetraverseOnChange(boolean retraverse) {
    this.retraverseOnChange = retraverse;
  }

  @Override
  public void process(Node externs, Node root) {
    beginTraversal();

    // Repeat to an internal fixed point.
    for (List<Node> changedScopeNodes =
            compiler.getChangeTracker().getChangedScopeNodesForPass(passName);
        changedScopeNodes == null || !changedScopeNodes.isEmpty();
        changedScopeNodes = compiler.getChangeTracker().getChangedScopeNodesForPass(passName)) {

      if (changedScopeNodes == null) {
        // changedScopeNodes is null if this is the first run of peepholeOptimizationsPass.
        NodeTraversal.traverse(compiler, root, new PeepCallback());
      } else {
        NodeTraversal.traverseScopeRoots(
            compiler, changedScopeNodes, new PeepCallback(), /* traverseNested= */ false);
      }

      // Cancel the fixed point if requested.
      if (!retraverseOnChange) {
        break;
      }
    }

    endTraversal();
  }

  private class PeepCallback extends NodeTraversal.AbstractScopedCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      Node currentNode = n;
      for (AbstractPeepholeOptimization optim : peepholeOptimizations) {
        currentNode = optim.optimizeSubtree(currentNode);
        if (currentNode == null) {
          return;
        }
      }
    }

    @Override
    public void enterScope(NodeTraversal t) {}

    @Override
    public void exitScope(NodeTraversal t) {
      // Call updateFeatures() here, instead of immediately after
      // `optim.optimizeSubtree(currentNode)` in visit, to avoid repeating work for
      // every AST node x every peephole optimization. In practice, profiling shows a small
      // but measurable improvement for large projects. See comments on cl/856752797.
      updateFeatures(t);
    }

    private void updateFeatures(NodeTraversal t) {
      for (AbstractPeepholeOptimization optim : peepholeOptimizations) {
        var newFeatures = optim.getNewFeatures();
        if (!newFeatures.isEmpty()) {
          NodeUtil.addFeaturesToScript(
              t.getCurrentScript(), FeatureSet.BARE_MINIMUM.with(newFeatures), compiler);
        }
        optim.clearNewFeatures();
      }
    }
  }

  /** Make sure that all the optimizations have the current compiler so they can report errors. */
  private void beginTraversal() {
    for (AbstractPeepholeOptimization optimization : peepholeOptimizations) {
      optimization.beginTraversal(compiler);
    }
  }

  /** End the traversal. */
  private void endTraversal() {
    for (AbstractPeepholeOptimization optimization : peepholeOptimizations) {
      optimization.endTraversal();
    }
  }
}
