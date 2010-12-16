/*
 * Copyright 2006 The Closure Compiler Authors.
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
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.List;
import java.util.Set;

/**
 * Collapses multiple variable declarations into a single one. i.e the
 * following:
 *
 * <pre>
 * var a;
 * var b = 1;
 * var c = 2;
 * </pre>
 *
 * becomes:
 *
 * <pre>var a, b = 1, c = 2;</pre>
 *
 * This reduces the generated code size. More optimizations are possible:
 * <li>Group all variable declarations inside a function into one such variable.
 * declaration block.</li>
 * <li>Re-use variables instead of declaring a new one if they are used for
 * only part of a function.</li>
 *
 * Similarly, also collapses assigns like:
 *
 * <pre>
 * a = true;
 * b = true;
 * var c = true;
 * </pre>
 *
 * becomes:
 *
 * <pre>var c = b = a = true;</pre>
 *
 */
class CollapseVariableDeclarations implements CompilerPass {
  /** Reference to JS Compiler */
  private final AbstractCompiler compiler;

  /** Encapsulation of information about a variable declaration collapse */
  private static class Collapse {
    /**
     * Variable declaration that any following var nodes should be
     * collapsed into
     */
    final Node firstVarNode;

    /** Parent of the nodes to the collapse */
    final Node parent;

    Collapse(Node firstVarNode, Node parent) {
      this.firstVarNode = firstVarNode;
      this.parent = parent;
    }
  }

  /**
   * Collapses to do in this pass.
   */
  private final List<Collapse> collapses = Lists.newArrayList();

  /**
   * Nodes we've already looked at for collapsing, so that we don't look at them
   * again (we look ahead when examining what nodes can be collapsed, and the
   * node traversal may give them to us again)
   */
  private final Set<Node> nodesToCollapse = Sets.newHashSet();

  CollapseVariableDeclarations(AbstractCompiler compiler) {
    Preconditions.checkState(!compiler.getLifeCycleStage().isNormalized());
    this.compiler = compiler;
  }

  public void process(Node externs, Node root) {
    collapses.clear();
    nodesToCollapse.clear();

    NodeTraversal.traverse(compiler, root,
        new CombinedCompilerPass(compiler,
            new ExploitAssigns(), new GatherCollapses()));
    if (!collapses.isEmpty()) {
      applyCollapses();
      compiler.reportCodeChange();
    }
  }

  private class ExploitAssigns extends AbstractPostOrderCallback {

    public void visit(NodeTraversal t, Node expr, Node exprParent) {
      if (!NodeUtil.isExprAssign(expr)) {
        return;
      }

      collapseAssign(t, expr.getFirstChild(), expr, exprParent);
    }

    /**
     * Try to collapse the given assign into subsequent expressions.
     */
    private void collapseAssign(NodeTraversal t, Node assign, Node expr,
        Node exprParent) {
      Node leftValue = assign.getFirstChild();
      Node rightValue = leftValue.getNext();
      if (isCollapsibleValue(leftValue, true) &&
          collapseAssignEqualTo(expr, exprParent, leftValue)) {
        t.getCompiler().reportCodeChange();
      } else if (isCollapsibleValue(rightValue, false) &&
          collapseAssignEqualTo(expr, exprParent, rightValue)) {
        t.getCompiler().reportCodeChange();
      } else if (rightValue.getType() == Token.ASSIGN) {
        // Recursively deal with nested assigns.
        collapseAssign(t, rightValue, expr, exprParent);
      }
    }

    /**
     * Determines whether we know enough about the given value to be able
     * to collapse it into subsequent expressions.
     *
     * For example, we can collapse booleans and variable names:
     * <code>
     * x = 3; y = x; // y = x = 3;
     * a = true; b = true; // b = a = true;
     * <code>
     * But we won't try to collapse complex expressions.
     *
     * @param value The value node.
     * @param isLValue Whether it's on the left-hand side of an expr.
     */
    private boolean isCollapsibleValue(Node value, boolean isLValue) {
      switch (value.getType()) {
        case Token.GETPROP:
          // Do not collapse GETPROPs on arbitrary objects, because
          // they may be implemented  setter functions, and oftentimes
          // setter functions fail on native objects. This is ok for "THIS"
          // objects, because we assume that they are non-native.
          return !isLValue || value.getFirstChild().getType() == Token.THIS;

        case Token.NAME:
        case Token.NUMBER:
        case Token.TRUE:
        case Token.FALSE:
        case Token.NULL:
        case Token.STRING:
          return true;
      }

      return false;
    }

    /**
     * Collapse the given assign expression into the expression directly
     * following it, if possible.
     *
     * @param expr The expression that may be moved.
     * @param exprParent The parent of {@code expr}.
     * @param value The value of this expression, expressed as a node. Each
     *     expression may have multiple values, so this function may be called
     *     multiple times for the same expression. For example,
     *     <code>
     *     a = true;
     *     </code>
     *     is equal to the name "a" and the boolean "true".
     * @return Whether the expression was collapsed succesfully.
     */
    private boolean collapseAssignEqualTo(Node expr, Node exprParent,
        Node value) {
      Node assign = expr.getFirstChild();
      Node parent = exprParent;
      Node next = expr.getNext();
      while (next != null) {
        switch (next.getType()) {
          case Token.AND:
          case Token.OR:
          case Token.HOOK:
          case Token.IF:
          case Token.RETURN:
          case Token.EXPR_RESULT:
            // Dive down the left side
            parent = next;
            next = next.getFirstChild();
            break;

          case Token.VAR:
            if (next.getFirstChild().hasChildren()) {
              parent = next.getFirstChild();
              next = parent.getFirstChild();
              break;
            }
            return false;

          case Token.GETPROP:
          case Token.NAME:
            if (next.isQualifiedName()) {
              String nextName = next.getQualifiedName();
              if (value.isQualifiedName() &&
                  nextName.equals(value.getQualifiedName())) {
                // If the previous expression evaluates to value of a
                // qualified name, and that qualified name is used again
                // shortly, then we can exploit the assign here.

                // Verify the assignment doesn't change its own value.
                if (!isSafeReplacement(next, assign)) {
                  return false;
                }

                exprParent.removeChild(expr);
                expr.removeChild(assign);
                parent.replaceChild(next, assign);
                return true;
              }
            }
            return false;

          case Token.NUMBER:
          case Token.TRUE:
          case Token.FALSE:
          case Token.NULL:
          case Token.STRING:
            if (value.getType() == next.getType()) {
              if ((next.getType() == Token.STRING ||
                      next.getType() == Token.NUMBER) &&
                  !next.isEquivalentTo(value)) {
                return false;
              }

              // If the r-value of the expr assign is an immutable value,
              // and the value is used again shortly, then we can exploit
              // the assign here.
              exprParent.removeChild(expr);
              expr.removeChild(assign);
              parent.replaceChild(next, assign);
              return true;
            }
            return false;

          case Token.ASSIGN:
            // Assigns are really tricky. In lots of cases, we want to inline
            // into the right side of the assign. But the left side of the
            // assign is evaluated first, and it may have convoluted logic:
            //   a = null;
            //   (a = b).c = null;
            // We don't want to exploit the first assign. Similarly:
            //   a.b = null;
            //   a.b.c = null;
            // We don't want to exploit the first assign either.
            //
            // To protect against this, we simply only inline when the left side
            // is guaranteed to evaluate to the same L-value no matter what.
            Node leftSide = next.getFirstChild();
            if (leftSide.getType() == Token.NAME ||
                leftSide.getType() == Token.GETPROP &&
                leftSide.getFirstChild().getType() == Token.THIS) {
              // Dive down the right side of the assign.
              parent = next;
              next = leftSide.getNext();
              break;
            } else {
              return false;
            }

          default:
            // Return without inlining a thing
            return false;
        }
      }

      return false;
    }
  }

  /**
   * Checks name referenced in node to determine if it might have
   * changed.
   * @return Whether the replacement can be made.
   */
  private boolean isSafeReplacement(Node node, Node replacement) {
    // No checks are needed for simple names.
    if (node.getType() == Token.NAME) {
      return true;
    }
    Preconditions.checkArgument(node.getType() == Token.GETPROP);

    Node name = node.getFirstChild();
    if (name.getType() == Token.NAME
        && isNameAssignedTo(name.getString(), replacement)) {
      return false;
    }

    return true;
  }

  /**
   * @return Whether name is assigned in the expression rooted at node.
   */
  private boolean isNameAssignedTo(String name, Node node) {
    for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
      if (isNameAssignedTo(name, c)) {
        return true;
      }
    }

    if (node.getType() == Token.NAME) {
      Node parent = node.getParent();
      if (parent.getType() == Token.ASSIGN && parent.getFirstChild() == node) {
        if (name.equals(node.getString())) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Gathers all of the variable declarations that should be collapsed into one.
   * We do not do the collapsing as we go since node traversal would be affected
   * by the changes we are making to the parse tree.
   */
  private class GatherCollapses extends AbstractPostOrderCallback {

    public void visit(NodeTraversal t, Node n, Node parent) {
      // Only care about var nodes
      if (n.getType() != Token.VAR) return;

      // If we've already looked at this node, skip it
      if (nodesToCollapse.contains(n)) return;

      // Adjacent VAR children of an IF node are the if and else parts and can't
      // be collapsed
      if (parent.getType() == Token.IF) return;

      Node varNode = n;

      // Find variable declarations that follow this one (if any)
      n = n.getNext();

      boolean hasNodesToCollapse = false;
      while (n != null && n.getType() == Token.VAR) {
        nodesToCollapse.add(n);
        hasNodesToCollapse = true;

        n = n.getNext();
      }

      if (hasNodesToCollapse) {
        nodesToCollapse.add(varNode);
        collapses.add(new Collapse(varNode, parent));
      }

    }
  }

  private void applyCollapses() {
    for (Collapse collapse : collapses) {
      Node first = collapse.firstVarNode;
      while (first.getNext() != null &&
          first.getNext().getType() == Token.VAR) {
        Node next = collapse.parent.removeChildAfter(first);

        // Move all children of the next var node into the first one.
        first.addChildrenToBack(next.removeChildren());
      }
    }
  }

}
