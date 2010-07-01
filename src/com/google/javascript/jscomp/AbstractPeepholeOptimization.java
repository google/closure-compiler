/*
 * Copyright 2010 Google Inc.
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

import com.google.common.base.Preconditions;
import com.google.javascript.rhino.Node;

/**
 * An abstract class whose implementations run peephole optimizations:
 * optimizations that look at a small section of code and either remove
 * that code (if it is not needed) or replaces it with smaller code.
 *
*
 */
abstract class AbstractPeepholeOptimization {

  private NodeTraversal currentTraversal;

  /**
   * Given a node to optimize and a traversal, optimize the node. Subclasses
   * should override to provide their own peephole optimization.
   *
   * @param subtree The subtree that will be optimized.
   * @return The new version of the subtree (or null if the subtree or one of
   * its parents was removed from the AST). If the subtree has not changed,
   * this method must return {@code subtree}.
   */
  abstract Node optimizeSubtree(Node subtree);

  /**
   * Helper method for reporting an error to the compiler when applying a
   * peephole optimization.
   *
   * @param diagnostic The error type
   * @param n The node for which the error should be reported
   */
  protected void error(DiagnosticType diagnostic, Node n) {
    JSError error = currentTraversal.makeError(n, diagnostic, n.toString());
    currentTraversal.getCompiler().report(error);
  }

  /**
   * Helper method for telling the compiler that something has changed.
   * Subclasses must call these if they have changed the AST.
   */
  protected void reportCodeChange() {
    Preconditions.checkNotNull(currentTraversal);
    currentTraversal.getCompiler().reportCodeChange();
  }

  /**
   * Are the nodes equal for the purpose of inlining?
   * If type aware optimizations are on, type equality is checked.
   */
  protected boolean areNodesEqualForInlining(Node n1, Node n2) {
    /* Our implementation delegates to the compiler. We provide this
     * method because we don't want to expose Compiler to PeepholeOptimizations.
     */
    Preconditions.checkNotNull(currentTraversal);
    return currentTraversal.getCompiler().areNodesEqualForInlining(n1, n2);
  }

  /**
   *  Is the current AST normalized? (e.g. has the Normalize pass been run
   *  and has the Denormalize pass not yet been run?)
   */
  protected boolean isASTNormalized() {
    Preconditions.checkNotNull(currentTraversal);
    Preconditions.checkNotNull(currentTraversal.getCompiler());

    return currentTraversal.getCompiler().isNormalized();
  }

  /**
   * Informs the optimization that a traversal will begin.
   */
  void beginTraversal(NodeTraversal traversal) {
    currentTraversal = traversal;
  }

  /**
   * Informs the optimization that a traversal has completed.
   */
  void endTraversal(NodeTraversal traversal) {
    currentTraversal = null;
  }
}
