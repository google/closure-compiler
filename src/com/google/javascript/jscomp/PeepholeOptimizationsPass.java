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
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;

/**
 * A compiler pass to run various peephole optimizations (e.g. constant folding,
 * some useless code removal, some minimizations).
 *
 * @author dcc@google.com (Devin Coughlin)
 */
class PeepholeOptimizationsPass extends AbstractPostOrderCallback
    implements CompilerPass {
  private AbstractCompiler compiler;

  private ImmutableSet<AbstractPeepholeOptimization> peepholeOptimizations;

  PeepholeOptimizationsPass(AbstractCompiler compiler,
      ImmutableSet<AbstractPeepholeOptimization> optimizations) {
    this.compiler = compiler;
    this.peepholeOptimizations = optimizations;
  }

  /**
   * Creates a peephole optimization pass that runs the given
   * optimizations.
   */
  PeepholeOptimizationsPass(AbstractCompiler compiler,
      AbstractPeepholeOptimization... optimizations) {
    this(compiler, ImmutableSet.copyOf(optimizations));
  }

  public AbstractCompiler getCompiler() {
    return compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal t = new NodeTraversal(compiler, this);

    beginTraversal(t);
    t.traverse(root);
    endTraversal(t);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    Node currentVersionOfNode = n;
    boolean somethingChanged = false;

    do {
      somethingChanged = false;
      for (AbstractPeepholeOptimization optimization : peepholeOptimizations) {
        Node newVersionOfNode =
            optimization.optimizeSubtree(currentVersionOfNode);

        if (newVersionOfNode != currentVersionOfNode) {
          somethingChanged = true;

          currentVersionOfNode = newVersionOfNode;
        }

        if (currentVersionOfNode == null) {
          return;
        }
      }
    } while(somethingChanged);
  }

  /**
   * Make sure that all the optimizations have the current traversal so they
   * can report errors.
   */
  private void beginTraversal(NodeTraversal t) {
    for (AbstractPeepholeOptimization optimization : peepholeOptimizations) {
      optimization.beginTraversal(t);
    }
  }

  private void endTraversal(NodeTraversal t) {
    for (AbstractPeepholeOptimization optimization : peepholeOptimizations) {
      optimization.endTraversal(t);
    }
  }
}
