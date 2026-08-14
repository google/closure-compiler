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
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.base.Tri;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import org.jspecify.annotations.Nullable;

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
   * New parser features added in some {@link #optimizeSubtree} call.
   *
   * <p>{@link PeepholeOptimizationsPass} uses this to call {@link NodeUtil#addFeaturesToScript} in
   * closer to O(1) time, as this class doesn't know what script node it's currently in, and would
   * need to walk the AST.
   */
  private final LinkedHashSet<Feature> newFeatures = new LinkedHashSet<>();

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

  /**
   * Informs the optimization that a traversal has ended.
   *
   * <p>This class cannot be used after this method is called, unless {@link #beginTraversal} is
   * called again.
   */
  void endTraversal() {
    checkState(
        this.newFeatures.isEmpty(),
        "Expected getNewFeatures() to be empty in endTraversal() but found %s for %s",
        this.newFeatures,
        this.getClass().getName());
    this.compiler = null;
    astAnalyzer = null;
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

  protected Double getSideEffectFreeNumberValueNoConversion(Node n) {
    Double value = NodeUtil.getNumberValueNoConversions(n);
    if (value != null && astAnalyzer.mayHaveSideEffects(n)) {
      value = null;
    }
    return value;
  }

  /**
   * Returns the bigint value of the node if it has one and it cannot have side effects.
   *
   * <p>Returns {@code null} otherwise.
   */
  protected BigInteger getSideEffectFreeBigIntValue(Node n) {
    BigInteger value = NodeUtil.getBigIntValue(n);
    // Calculating the bigint value, if any, is likely to be faster than calculating side effects,
    // and there are only a very few cases where we can compute a bigint value, but there could
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
   * <p>Returns {@link Tri#UNKNOWN} if the node has side effects or its value cannot be statically
   * determined.
   */
  protected Tri getSideEffectFreeBooleanValue(Node n) {
    Tri value = NodeUtil.getBooleanValue(n);
    // Calculating the boolean value, if any, is likely to be faster than calculating side effects,
    // and there are only a very few cases where we can compute a boolean value, but there could
    // also be side effects. e.g. `void doSomething()` has value `false`, regardless of the
    // behavior of `doSomething()`
    if (value != Tri.UNKNOWN && astAnalyzer.mayHaveSideEffects(n)) {
      value = Tri.UNKNOWN;
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
   * Replaces an {@code expression} with a provided {@code value}, which must have been obtained by
   * evaluating the expression. The present function ensures that the side effects of the {@code
   * expression} is preserved after the replacement.
   *
   * <p>This is achieved by substituting the original expression with the comma node (expr, value)
   * and then simplify the first child using {@link trySimplifyUnusedResult()}.
   *
   * <p>If the provided expression is a call such as `fn(innerExpr)`, we require that fn has no side
   * effects. This is because if fn had side effects, no simplifications can occur and one ends up
   * with (fn(innerExpr), value), which is less optimized than the initial expression.
   *
   * @return The replacement expression, which can be of the form `(..., value)` or just `value`.
   */
  protected final Node replaceExpressionWithEvalResult(Node expr, Node value) {
    checkState(expr.hasParent(), expr);
    checkState(!value.hasParent(), value);
    if (expr.isCall() || expr.isOptChainCall()) {
      checkState(expr.isNoSideEffectsCall(), expr);
    }
    Node comma = new Node(Token.COMMA, value.srcref(expr));
    expr.replaceWith(comma);
    comma.addChildToFront(expr); // comma = (expr, value)
    expr = trySimplifyUnusedResult(expr);
    if (expr == null || !mayHaveSideEffects(expr)) {
      value.detach();
      comma.replaceWith(value);
      if (expr != null) {
        markFunctionsDeleted(expr);
      }
      expr = value; // expr = value
    } else {
      expr = comma; // expr = (..., value)
    }
    reportChangeToEnclosingScope(expr);
    return expr;
  }

  /**
   * Replaces {@code expression} with an expression that contains only side-effects of the original.
   *
   * <p>This replacement is made under the assumption that the result of {@code expression} is
   * unused and therefore it is correct to eliminate non-side-effectful nodes.
   *
   * @return The replacement expression, or {@code null} if there were no side-effects to preserve.
   */
  protected final @Nullable Node trySimplifyUnusedResult(Node expression) {
    ArrayDeque<Node> sideEffectRoots = new ArrayDeque<>();
    boolean atFixedPoint = trySimplifyUnusedResultInternal(expression, sideEffectRoots);

    if (atFixedPoint) {
      // `expression` is in a form that cannot be further optimized.
      return expression;
    } else if (sideEffectRoots.isEmpty()) {
      deleteNode(expression);
      return null;
    } else if (sideEffectRoots.peekFirst() == expression) {
      // Expression was a conditional that was transformed. There can't be any other side-effects,
      // but we also can't detach the transformed root.
      checkState(sideEffectRoots.size() == 1, sideEffectRoots);
      reportChangeToEnclosingScope(expression);
      return expression;
    } else {
      Node sideEffects = asDetachedExpression(sideEffectRoots.pollFirst());

      // Assemble a tree of comma expressions for all the side-effects. The tree must execute the
      // side-effects in FIFO order with respect to the queue. It must also be left leaning to match
      // the parser's preferred structure.
      while (!sideEffectRoots.isEmpty()) {
        Node next = asDetachedExpression(sideEffectRoots.pollFirst());
        sideEffects = IR.comma(sideEffects, next).srcref(next);
      }

      sideEffects.insertBefore(expression);
      deleteNode(expression);
      return sideEffects;
    }
  }

  /**
   * Collects any potentially side-effectful subtrees within {@code tree} into {@code
   * sideEffectRoots}.
   *
   * <p>When a node is determined to have side-effects its descendants are not explored. This method
   * assumes the entire subtree of such a node must be preserved. As a corollary, the contents of
   * {@code sideEffectRoots} are a forest.
   *
   * <p>This operation generally does not mutate {@code tree}; however, exceptions are made for
   * expressions that alter control-flow. Such expression will be pruned of their side-effectless
   * branches. Even in this case, {@code tree} is never detached.
   *
   * @param sideEffectRoots The roots of subtrees determined to have side-effects, in execution
   *     order.
   * @return {@code true} iff there is no code to be removed from within {@code tree}; it is already
   *     at a fixed point for code removal.
   */
  private boolean trySimplifyUnusedResultInternal(Node tree, ArrayDeque<Node> sideEffectRoots) {
    // Special cases for conditional expressions that may be using results.
    switch (tree.getToken()) {
      case HOOK -> {
        // Try to remove one or more of the conditional children and transform the HOOK to an
        // equivalent operation. Remember that if either value branch still exists, the result of
        // the predicate expression is being used, and so cannot be removed.
        //    x() ? foo() : 1 --> x() && foo()
        //    x() ? 1 : foo() --> x() || foo()
        //    x() ? 1 : 1 --> x()
        //    x ? 1 : 1 --> null
        Node trueNode = trySimplifyUnusedResult(tree.getSecondChild());
        Node falseNode = trySimplifyUnusedResult(tree.getLastChild());
        if (trueNode == null && falseNode != null) {
          checkState(tree.hasTwoChildren(), tree);

          tree.setToken(Token.OR);
          sideEffectRoots.addLast(tree);
          return false; // The node type was changed.
        } else if (trueNode != null && falseNode == null) {
          checkState(tree.hasTwoChildren(), tree);

          tree.setToken(Token.AND);
          sideEffectRoots.addLast(tree);
          return false; // The node type was changed.
        } else if (trueNode == null && falseNode == null) {
          // Don't bother adding true and false branch children to make the AST valid; this HOOK is
          // going to be deleted. We just need to collect any side-effects from the predicate
          // expression.
          trySimplifyUnusedResultInternal(tree.getOnlyChild(), sideEffectRoots);
          return false; // This HOOK must be cleaned up.
        } else {
          sideEffectRoots.addLast(tree);
          return hasFixedPointParent(tree);
        }
      }
      case AND, OR, COALESCE -> {
        // Try to remove the second operand from a AND, OR, and COALESCE operations. Remember that
        // if the second
        // child still exists, the result of the first expression is being used, and so cannot be
        // removed.
        //    x() ?? f --> x()
        //    x() || f --> x()
        //    x() && f --> x()
        Node conditionalResultNode = trySimplifyUnusedResult(tree.getLastChild());
        if (conditionalResultNode == null) {
          // Don't bother adding a second child to make the AST valid; this op is going to be
          // deleted. We just need to collect any side-effects from the predicate first child.
          trySimplifyUnusedResultInternal(tree.getOnlyChild(), sideEffectRoots);
          return false; // This op must be cleaned up.
        } else {
          sideEffectRoots.addLast(tree);
          return hasFixedPointParent(tree);
        }
      }
      case FUNCTION -> {
        // Functions that aren't being invoked are dead. If they were invoked we'd see the CALL
        // before arriving here. We don't want to look at any children since they'll never execute.
        return false;
      }
      default -> {
        // This is the meat of this function. It covers the general case of nodes which are unused
        if (nodeTypeMayHaveSideEffects(tree)) {
          sideEffectRoots.addLast(tree);
          return hasFixedPointParent(tree);
        } else if (!tree.hasChildren()) {
          return false; // A node must have children or side-effects to be at fixed-point.
        }

        boolean atFixedPoint = hasFixedPointParent(tree);
        for (Node child = tree.getFirstChild(); child != null; child = child.getNext()) {
          atFixedPoint &= trySimplifyUnusedResultInternal(child, sideEffectRoots);
        }
        return atFixedPoint;
      }
    }
  }

  /**
   * Returns an expression executing {@code expr} which is legal in any expression context.
   *
   * @param expr An attached expression
   * @return A detached expression
   */
  protected static Node asDetachedExpression(Node expr) {
    switch (expr.getToken()) {
      case ITER_SPREAD, OBJECT_SPREAD -> {
        switch (expr.getParent().getToken()) {
          case ARRAYLIT:
          case NEW:
          case CALL: // `Math.sin(...c)`
          case OPTCHAIN_CALL: // `Math?.sin(...c)`
            expr = IR.arraylit(expr.detach()).srcref(expr);
            break;
          case OBJECTLIT:
            expr = IR.objectlit(expr.detach()).srcref(expr);
            break;
          default:
            throw new IllegalStateException(expr.toStringTree());
        }
      }
      default -> {}
    }

    if (expr.hasParent()) {
      expr.detach();
    }

    checkState(IR.mayBeExpression(expr), expr);
    return expr;
  }

  /**
   * Returns {@code true} iff {@code expr} is parented such that it is valid in a fixed-point
   * representation of an unused expression tree.
   *
   * <p>A fixed-point representation is one in which no further nodes should be changed or removed
   * when removing unused code. This method assumes that the expression tree in question is unused,
   * so only side-effects are relevant.
   */
  private static boolean hasFixedPointParent(Node expr) {
    // Most kinds of nodes shouldn't be branches in the fixed-point tree of an unused
    // expression. Those listed below are the only valid kinds.
    return switch (expr.getParent().getToken()) {
      case AND, COMMA, HOOK, OR, COALESCE -> true;
      case ARRAYLIT, OBJECTLIT ->
          // Make a special allowance for SPREADs so they remain in a legal context. Parent types
          // other than ARRAYLIT and OBJECTLIT are not fixed-point because they are the tersest
          // legal
          // parents and are known to be side-effect free.
          expr.isSpread();
      default ->
          // Statments are always fixed-point parents. All other expressions are not.
          NodeUtil.isStatement(expr.getParent());
    };
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

  /** Calls {@link NodeUtil#addFeatureToScript} with the script currently being visited. */
  protected final void addFeatureToEnclosingScript(Feature feature) {
    // NOTE: we could implement this as
    //  protected final void addFeatureToEnclosingScript(Node node, Feature feature) {
    //     NodeUtil.addFeatureToScript(NodeUtil.getEnclosingScript(node), ...
    // This is expected to be behaviorally equivalent.
    // However, walking the AST to get the enclosing script is O(depth of the AST) per call. With
    // the  current implementation, we let PeepholeOptimizationsPass to find the script in what's
    // usually O(1) time, or worst case it only calls getEnclosingScript() once per NodeTraversal
    // and then caches the result.
    newFeatures.add(feature);
  }

  final ImmutableSet<Feature> getNewFeatures() {
    return ImmutableSet.copyOf(newFeatures);
  }

  final void clearNewFeatures() {
    newFeatures.clear();
  }
}
