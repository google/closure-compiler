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

import com.google.javascript.jscomp.NodeTraversal.AbstractShallowCallback;
import com.google.javascript.jscomp.NodeTraversal.FunctionCallback;
import com.google.javascript.rhino.Node;

/**
 * A compiler pass to run various peephole optimizations (e.g. constant folding,
 * some useless code removal, some minimizations).
 *
 * @author dcc@google.com (Devin Coughlin)
 */
class PeepholeOptimizationsPass implements CompilerPass {
  private AbstractCompiler compiler;

  // Use an array here for faster iteration compared to ImmutableSet
  private final AbstractPeepholeOptimization[] peepholeOptimizations;

  private boolean retraverseOnChange;
  private RecentChange handler;

  /**
   * Creates a peephole optimization pass that runs the given
   * optimizations.
   */
  PeepholeOptimizationsPass(AbstractCompiler compiler,
      AbstractPeepholeOptimization... optimizations) {
    this.compiler = compiler;
    this.peepholeOptimizations = optimizations;
    this.retraverseOnChange = true;
    this.handler = new RecentChange();
  }

  void setRetraverseOnChange(boolean retraverse) {
    this.retraverseOnChange = retraverse;
  }

  @Override
  public void process(Node externs, Node root) {
    compiler.addChangeHandler(handler);
    beginTraversal();
    NodeTraversal.traverseChangedFunctions(compiler, new FunctionCallback() {
        @Override
        public void visit(AbstractCompiler compiler, Node root) {
          if (root.isFunction()) {
            root = root.getLastChild();
          }
          do {
            handler.reset();
            NodeTraversal.traverse(compiler, root, new PeepCallback());
          } while (retraverseOnChange && handler.hasCodeChanged());
        }
      });
    endTraversal();
    compiler.removeChangeHandler(handler);
  }

  private class PeepCallback extends AbstractShallowCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      Node currentNode = n, newNode;
      boolean codeChanged = false;
      do {
        codeChanged = false;
        for (AbstractPeepholeOptimization optim : peepholeOptimizations) {
          newNode = optim.optimizeSubtree(currentNode);
          if (newNode != currentNode) {
            codeChanged = true;
            currentNode = newNode;
          }
          if (currentNode == null) {
            return;
          }
        }
      } while(codeChanged);
    }
  }

  /**
   * Make sure that all the optimizations have the current traversal so they
   * can report errors.
   */
  private void beginTraversal() {
    for (AbstractPeepholeOptimization optimization : peepholeOptimizations) {
      optimization.beginTraversal(compiler);
    }
  }

  private void endTraversal() {
    for (AbstractPeepholeOptimization optimization : peepholeOptimizations) {
      optimization.endTraversal(compiler);
    }
  }
}
