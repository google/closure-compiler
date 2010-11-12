/*
 * Copyright 2009 The Closure Compiler Authors.
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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.CodingConvention.SubclassRelationship;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.List;
import java.util.Set;

/**
 * Callback that gathers subexpressions that may have side effects
 * and appends copies of those subexpressions to the replacements
 * list.  In the case of branching subexpressions, it simplifies the
 * subexpression before adding it to the replacement list.
 *
 */
class GatherSideEffectSubexpressionsCallback implements Callback {

  /**
   * Used by GatherSideEffectSubexpressionsCallback to notify client
   * code about side effect expressions that should be kept.
   */
  interface SideEffectAccumulator {

    /**
     * Returns true if the "mixin" and "inherits" function calls
     * should be treated as if they had side effects.
     */
    boolean classDefiningCallsHaveSideEffects();

    /**
     * Adds subtree to the list of nodes that have side effects.
     *
     * @param original - root of the tree.
     */
    void keepSubTree(Node original);

    /**
     * Simplifies a subtree whose root node is an AND or OR expression
     * and adds the resulting subtree to the list of nodes that have
     * side effects.
     *
     * @param original - root of the and/or expression.
     */
    void keepSimplifiedShortCircuitExpression(Node original);

    /**
     * Simplifies a subtree whose root node is a HOOK expression
     * and adds the resulting subtree to the list of nodes that have
     * side effects.
     *
     * @param hook - root of the hook expression.
     * @param thenHasSideEffects - then branch has side effects
     * @param elseHasSideEffects - else branch has side effects
     */
    void keepSimplifiedHookExpression(Node hook,
                                      boolean thenHasSideEffects,
                                      boolean elseHasSideEffects);
  }

  /**
   * Populates the provided replacement list by appending copies of
   * subtrees that have side effects.
   */
  static final class CopySideEffectSubexpressions
      implements SideEffectAccumulator {
    private final AbstractCompiler compiler;
    private final List<Node> replacements;

    /**
     * Creates the accumulator.
     *
     * @param compiler - the AbstractCompiler
     * @param replacements - list to accumulate into
     */
    CopySideEffectSubexpressions(AbstractCompiler compiler,
                                 List<Node> replacements) {
      this.compiler = compiler;
      this.replacements = replacements;
    }

    @Override
    public boolean classDefiningCallsHaveSideEffects() {
      return true;
    }

    @Override
    public void keepSubTree(Node original) {
      replacements.add(original.cloneTree());
    }

    @Override
    public void keepSimplifiedShortCircuitExpression(Node original) {
      Preconditions.checkArgument(
          (original.getType() == Token.AND) || (original.getType() == Token.OR),
          "Expected: AND or OR, Got: " + Token.name(original.getType()));
      Node left = original.getFirstChild();
      Node right = left.getNext();
      Node simplifiedRight = simplifyShortCircuitBranch(right);

      Node simplified = original.cloneNode();
      simplified.addChildToBack(left.cloneTree());
      simplified.addChildToBack(simplifiedRight);
      replacements.add(simplified);
    }

    @Override
    public void keepSimplifiedHookExpression(Node hook,
                                             boolean thenHasSideEffects,
                                             boolean elseHasSideEffects) {
      Preconditions.checkArgument(
          hook.getType() == Token.HOOK,
          "Expected: HOOK, Got: " + Token.name(hook.getType()));
      Node condition = hook.getFirstChild();
      Node thenBranch = condition.getNext();
      Node elseBranch = thenBranch.getNext();
      if (thenHasSideEffects && elseHasSideEffects) {
        Node simplified = hook.cloneNode();
        simplified.addChildToBack(condition.cloneTree());
        simplified.addChildToBack(simplifyShortCircuitBranch(thenBranch));
        simplified.addChildToBack(simplifyShortCircuitBranch(elseBranch));
        replacements.add(simplified);
      } else if (thenHasSideEffects || elseHasSideEffects) {
        int type = thenHasSideEffects ? Token.AND : Token.OR;
        Node body = thenHasSideEffects ? thenBranch : elseBranch;
        Node simplified = new Node(
            type, condition.cloneTree(), simplifyShortCircuitBranch(body))
            .copyInformationFrom(hook);
        replacements.add(simplified);
      } else {
        throw new IllegalArgumentException(
            "keepSimplifiedHookExpression must keep at least 1 branch");
      }
    }

    private Node simplifyShortCircuitBranch(Node node) {
      List<Node> parts = Lists.newArrayList();
      NodeTraversal.traverse(
          compiler, node,
          new GatherSideEffectSubexpressionsCallback(
              compiler,
              new CopySideEffectSubexpressions(compiler, parts)));

      Node ret = null;
      for (Node part : parts) {
        if (ret != null) {
          ret = new Node(Token.COMMA, ret, part).copyInformationFrom(node);
        } else {
          ret = part;
        }
      }

      if (ret == null) {
        throw new IllegalArgumentException(
            "expected at least one side effect subexpression in short "
            + "circuit branch.");
      }

      return ret;
    }
  }

  private static final Set<Integer> FORBIDDEN_TYPES = ImmutableSet.of(
      Token.BLOCK, Token.SCRIPT, Token.VAR, Token.EXPR_RESULT, Token.RETURN);
  private final AbstractCompiler compiler;
  private final SideEffectAccumulator accumulator;

  /**
   * @param compiler - AbstractCompiler object
   * @param accumulator - object that will accumulate roots of
   *                      subtrees that have side effects.
   */
  GatherSideEffectSubexpressionsCallback(AbstractCompiler compiler,
                                         SideEffectAccumulator accumulator) {
    this.compiler = compiler;
    this.accumulator = accumulator;
  }

  /**
   * Determines if a call defines a class inheritance or mixing
   * relation, according to the current coding convention.
   */
  private boolean isClassDefiningCall(Node callNode) {
    SubclassRelationship classes =
        compiler.getCodingConvention().getClassesDefinedByCall(callNode);
    return classes != null;
  }

  /**
   * Computes the list of subtrees whose root nodes have side effects.
   *
   * <p>If the current subtree's root has side effects this method should
   * call accumulator.keepSubTree and return 'false' to add the
   * subtree to the result list and avoid avoid traversing the nodes children.
   *
   * <p>Branching nodes whose then or else branch contain side effects
   * must be simplified by doing a recursive traversal; this method
   * should call the appropriate accumulator 'keepSimplified' method
   * and return 'false' to stop the regular traversal.
   */
  @Override
  public boolean shouldTraverse(
      NodeTraversal traversal, Node node, Node parent) {
    if (FORBIDDEN_TYPES.contains(node.getType()) ||
        NodeUtil.isControlStructure(node)) {
      throw new IllegalArgumentException(
          Token.name(node.getType()) + " nodes are not supported.");
    }

    // Do not recurse into nested functions.
    if (node.getType() == Token.FUNCTION) {
      return false;
    }

    // simplify and maybe keep hook expression.
    if (node.getType() == Token.HOOK) {
      return processHook(node);
    }

    // simplify and maybe keep AND/OR expression.
    if ((node.getType() == Token.AND) || (node.getType() == Token.OR)) {
      return processShortCircuitExpression(node);
    }

    if (!NodeUtil.nodeTypeMayHaveSideEffects(node)) {
      return true;
    } else {

      // Node type suggests that the expression has side effects.

      if (node.getType() == Token.CALL) {
        return processFunctionCall(node);
      } else if (node.getType() == Token.NEW) {
        return processConstructorCall(node);
      } else {
        accumulator.keepSubTree(node);
        return false;
      }
    }
  }

  /**
   * Processes an AND or OR expression.
   *
   * @return true to continue traversal, false otherwise
   */
  boolean processShortCircuitExpression(Node node) {
    Preconditions.checkArgument(
        (node.getType() == Token.AND) || (node.getType() == Token.OR),
        "Expected: AND or OR, Got: " + Token.name(node.getType()));

    // keep whole expression if rhs of the branching expression
    // contains a call.
    Node left = node.getFirstChild();
    Node right = left.getNext();
    if (NodeUtil.mayHaveSideEffects(right)) {
      accumulator.keepSimplifiedShortCircuitExpression(node);
      return false;
    } else {
      return true;
    }
  }

  /**
   * Processes a HOOK expression.
   *
   * @return true to continue traversal, false otherwise
   */
  boolean processHook(Node node) {
    Preconditions.checkArgument(
        node.getType() == Token.HOOK,
        "Expected: HOOK, Got: " + Token.name(node.getType()));

    Node condition = node.getFirstChild();
    Node ifBranch = condition.getNext();
    Node elseBranch = ifBranch.getNext();
    boolean thenHasSideEffects = NodeUtil.mayHaveSideEffects(ifBranch);
    boolean elseHasSideEffects = NodeUtil.mayHaveSideEffects(elseBranch);
    if (thenHasSideEffects || elseHasSideEffects) {
      accumulator.keepSimplifiedHookExpression(
          node, thenHasSideEffects, elseHasSideEffects);
      return false;
    } else {
      return true;
    }
  }

  /**
   * Processes a CALL expression.
   *
   * @return true to continue traversal, false otherwise
   */
  boolean processFunctionCall(Node node) {
    Preconditions.checkArgument(
        node.getType() == Token.CALL,
        "Expected: CALL, Got: " + Token.name(node.getType()));

    // Calls to functions that are known to be "pure" have no side
    // effects.
    Node functionName = node.getFirstChild();
    if (NodeUtil.isName(functionName) || NodeUtil.isGetProp(functionName)) {
      if (!accumulator.classDefiningCallsHaveSideEffects() &&
          isClassDefiningCall(node)) {
        return true;
      }
    }

    if (!NodeUtil.functionCallHasSideEffects(node)) {
      return true;
    }

    accumulator.keepSubTree(node);
    return false;
  }

  /**
   * Processes a NEW expression.
   *
   * @return true to continue traversal, false otherwise
   */
  boolean processConstructorCall(Node node) {
    Preconditions.checkArgument(
        node.getType() == Token.NEW,
        "Expected: NEW, Got: " + Token.name(node.getType()));

    // Calls to constructors that are known to be "pure" have no
    // side effects.
    if (!NodeUtil.constructorCallHasSideEffects(node)) {
      return true;
    }

    accumulator.keepSubTree(node);
    return false;
  }

  @Override
  public void visit(NodeTraversal traversal, Node node, Node parent) {}
}
