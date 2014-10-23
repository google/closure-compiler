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

import com.google.common.base.Preconditions;
import com.google.javascript.rhino.Node;

/**
 * An abstract class whose implementations run peephole optimizations:
 * optimizations that look at a small section of code and either remove
 * that code (if it is not needed) or replaces it with smaller code.
 *
 */
abstract class AbstractPeepholeOptimization {

  private AbstractCompiler compiler;

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
  protected void report(DiagnosticType diagnostic, Node n) {
    JSError error = JSError.make(n, diagnostic, n.toString());
    compiler.report(error);
  }

  /**
   * Helper method for telling the compiler that something has changed.
   * Subclasses must call these if they have changed the AST.
   */
  protected void reportCodeChange() {
    Preconditions.checkNotNull(compiler);
    compiler.reportCodeChange();
  }

  /**
   * Are the nodes equal for the purpose of inlining?
   * If type aware optimizations are on, type equality is checked.
   */
  protected boolean areNodesEqualForInlining(Node n1, Node n2) {
    /* Our implementation delegates to the compiler. We provide this
     * method because we don't want to expose Compiler to PeepholeOptimizations.
     */
    Preconditions.checkNotNull(compiler);
    return compiler.areNodesEqualForInlining(n1, n2);
  }

  /**
   *  Is the current AST normalized? (e.g. has the Normalize pass been run
   *  and has the Denormalize pass not yet been run?)
   */
  protected boolean isASTNormalized() {
    Preconditions.checkNotNull(compiler);

    return compiler.getLifeCycleStage().isNormalized();
  }

  /**
   * Informs the optimization that a traversal will begin.
   */
  void beginTraversal(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  /**
   * Informs the optimization that a traversal has completed.
   * @param compiler The current compiler.
   */
  void endTraversal(AbstractCompiler compiler) {
    this.compiler = null;
  }

  // NodeUtil's mayEffectMutableState and mayHaveSideEffects need access to the
  // compiler object, route them through here to give them access.

  /**
   * @return Whether the node may create new mutable state, or change existing
   * state.
   */
  boolean mayEffectMutableState(Node n) {
    return NodeUtil.mayEffectMutableState(n, compiler);
  }

  /**
   * @return Whether the node may have side effects when executed.
   */
  boolean mayHaveSideEffects(Node n) {
    return NodeUtil.mayHaveSideEffects(n, compiler);
  }

  /**
   * Returns true if the current node's type implies side effects.
   *
   * This is a non-recursive version of the may have side effects
   * check; used to check wherever the current node's type is one of
   * the reason's why a subtree has side effects.
   */
  boolean nodeTypeMayHaveSideEffects(Node n) {
    return NodeUtil.nodeTypeMayHaveSideEffects(n, compiler);
  }

  /**
   * @return Whether the source code version is ECMAScript 5 or later.
   *     Workarounds for quirks in browsers that do not support ES5 can be
   *     ignored when this is true.
   */
  boolean isEcmaScript5OrGreater() {
    return compiler != null
        && compiler.acceptEcmaScript5();
  }

  /**
   * @return the current coding convention.
   */
  CodingConvention getCodingConvention() {
    // Note: this assumes a thread safe coding convention object.
    return compiler.getCodingConvention();
  }

}
