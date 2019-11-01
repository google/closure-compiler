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

import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.TernaryValue;

/**
 * An abstract class whose implementations run peephole optimizations:
 * optimizations that look at a small section of code and either remove
 * that code (if it is not needed) or replaces it with smaller code.
 */
abstract class AbstractPeepholeOptimization {

  /** Intentionally not exposed to subclasses */
  private AbstractCompiler compiler;
  /** Intentionally not exposed to subclasses */
  private AstAnalyzer astAnalyzer;

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
   * Are the nodes equal for the purpose of inlining?
   * If type aware optimizations are on, type equality is checked.
   */
  protected boolean areNodesEqualForInlining(Node n1, Node n2) {
    /* Our implementation delegates to the compiler. We provide this
     * method because we don't want to expose Compiler to PeepholeOptimizations.
     */
    checkNotNull(compiler);
    return compiler.areNodesEqualForInlining(n1, n2);
  }

  /**
   *  Is the current AST normalized? (e.g. has the Normalize pass been run
   *  and has the Denormalize pass not yet been run?)
   */
  protected boolean isASTNormalized() {
    checkNotNull(compiler);

    return compiler.getLifeCycleStage().isNormalized();
  }

  /** Informs the optimization that a traversal will begin. */
  void beginTraversal(AbstractCompiler compiler) {
    this.compiler = checkNotNull(compiler);
    astAnalyzer = compiler.getAstAnalyzer();
  }

  /** Returns whether the node may create new mutable state, or change existing state. */
  protected boolean mayEffectMutableState(Node n) {
    return astAnalyzer.mayEffectMutableState(n);
  }

  /** Returns whether the node may have side effects when executed. */
  protected boolean mayHaveSideEffects(Node n) {
    return astAnalyzer.mayHaveSideEffects(n);
  }

  /**
   * Returns the number value of the node if it has one and it cannot have side effects.
   *
   * <p>Returns {@code null} otherwise.
   */
  protected Double getSideEffectFreeNumberValue(Node n) {
    Double value = NodeUtil.getNumberValue(n);
    // Calculating the number value, if any, is likely to be faster than calculating side effects,
    // and there are only a very few cases where we can compute a number value, but there could
    // also be side effects. e.g. `void doSomething()` has value NaN, regardless of the behavior
    // of `doSomething()`
    if (value != null && astAnalyzer.mayHaveSideEffects(n)) {
      value = null;
    }
    return value;
  }

  /**
   * Gets the value of a node as a String, or {@code null} if it cannot be converted.
   *
   * <p>This method effectively emulates the <code>String()</code> JavaScript cast function when
   * possible and the node has no side effects. Otherwise, it returns {@code null}.
   */
  protected String getSideEffectFreeStringValue(Node n) {
    String value = NodeUtil.getStringValue(n);
    // Calculating the string value, if any, is likely to be faster than calculating side effects,
    // and there are only a very few cases where we can compute a string value, but there could
    // also be side effects. e.g. `void doSomething()` has value 'undefined', regardless of the
    // behavior of `doSomething()`
    if (value != null && astAnalyzer.mayHaveSideEffects(n)) {
      value = null;
    }
    return value;
  }

  /**
   * Calculate the known boolean value for a node if possible and if it has no side effects.
   *
   * <p>Returns {@link TernaryValue#UNKNOWN} if the node has side effects or its value cannot be
   * statically determined.
   */
  protected TernaryValue getSideEffectFreeBooleanValue(Node n) {
    TernaryValue value = NodeUtil.getBooleanValue(n);
    // Calculating the boolean value, if any, is likely to be faster than calculating side effects,
    // and there are only a very few cases where we can compute a boolean value, but there could
    // also be side effects. e.g. `void doSomething()` has value `false`, regardless of the
    // behavior of `doSomething()`
    if (value != TernaryValue.UNKNOWN && astAnalyzer.mayHaveSideEffects(n)) {
      value = TernaryValue.UNKNOWN;
    }
    return value;
  }

  /**
   * Returns true if the current node's type implies side effects.
   *
   * <p>This is a non-recursive version of the may have side effects check; used to check wherever
   * the current node's type is one of the reason's why a subtree has side effects.
   */
  protected boolean nodeTypeMayHaveSideEffects(Node n) {
    return astAnalyzer.nodeTypeMayHaveSideEffects(n);
  }

  /**
   * Returns whether the output language is ECMAScript 5 or later. Workarounds for quirks in
   * browsers that do not support ES5 can be ignored when this is true.
   */
  protected boolean isEcmaScript5OrGreater() {
    return compiler != null
        && compiler.getOptions().getOutputFeatureSet().contains(FeatureSet.ES5);
  }

  /** Returns the current coding convention. */
  protected CodingConvention getCodingConvention() {
    // Note: this assumes a thread safe coding convention object.
    return compiler.getCodingConvention();
  }

  protected final boolean areDeclaredGlobalExternsOnWindow() {
    checkNotNull(compiler);
    return compiler.getOptions().declaredGlobalExternsOnWindow;
  }

  protected final void reportChangeToEnclosingScope(Node n) {
    compiler.reportChangeToEnclosingScope(n);
  }

  /** Calls {@link NodeUtil#deleteNode(Node, AbstractCompiler)} */
  protected final void deleteNode(Node property) {
    checkNotNull(compiler);
    NodeUtil.deleteNode(property, compiler);
  }

  /** Calls {@link NodeUtil#markFunctionsDeleted(Node, AbstractCompiler)} */
  protected final void markFunctionsDeleted(Node function) {
    checkNotNull(compiler);
    NodeUtil.markFunctionsDeleted(function, compiler);
  }

  /** Calls {@link NodeUtil#markNewScopesChanged(Node, AbstractCompiler)} */
  protected final void markNewScopesChanged(Node n) {
    checkNotNull(compiler);
    NodeUtil.markNewScopesChanged(n, compiler);
  }
}
