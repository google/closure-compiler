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
import com.google.common.collect.Lists;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

/**
 * A package for common iteration patterns.
 *
 * All iterators are forward, post-order traversals unless otherwise noted.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
class NodeIterators {

  private NodeIterators() {} /* all static */

  /**
   * Traverses the local scope, skipping all function nodes.
   */
  static class FunctionlessLocalScope implements Iterator<Node> {
    private final Stack<Node> ancestors = new Stack<Node>();

    /**
     * @param ancestors The ancestors of the point where iteration will start,
     *     beginning with the deepest ancestor. The start node will not be
     *     exposed in the iteration.
     */
    FunctionlessLocalScope(Node ... ancestors) {
      Preconditions.checkArgument(ancestors.length > 0);

      for (Node n : ancestors) {
        if (n.getType() == Token.FUNCTION) {
          break;
        }

        this.ancestors.add(0, n);
      }
    }

    @Override
    public boolean hasNext() {
      // Check if the current node has any nodes after it.
      return !(ancestors.size() == 1 && ancestors.peek().getNext() == null);
    }

    @Override
    public Node next() {
      Node current = ancestors.pop();
      if (current.getNext() == null) {
        current = ancestors.peek();

        // If this is a function node, skip it.
        if (current.getType() == Token.FUNCTION) {
          return next();
        }
      } else {
        current = current.getNext();
        ancestors.push(current);

        // If this is a function node, skip it.
        if (current.getType() == Token.FUNCTION) {
          return next();
        }

        while (current.hasChildren()) {
          current = current.getFirstChild();
          ancestors.push(current);

          // If this is a function node, skip it.
          if (current.getType() == Token.FUNCTION) {
            return next();
          }
        }
      }

      return current;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Gets the node most recently returned by next().
     */
    protected Node current() {
      return ancestors.peek();
    }

    /**
     * Gets the parent of the node most recently returned by next().
     */
    protected Node currentParent() {
      return ancestors.size() >= 2 ?
          ancestors.get(ancestors.size() - 2) : null;
    }

    /**
     * Gets the ancestors of the current node, with the deepest node first.
     * Only exposed for testing purposes.
     */
    List<Node> currentAncestors() {
      List<Node> list = Lists.newArrayList(ancestors);
      Collections.reverse(list);
      return list;
    }
  }

  /**
   * An iterator to help with variable inlining. Given a variable declaration,
   * find all the nodes in post-order where the variable is guaranteed to
   * retain its original value.
   *
   * Consider:
   * <pre>
   * var X = 1;
   * var Y = 3; // X is still 1
   * if (Y) {
   *   // X is still 1
   * } else {
   *   X = 5;
   * }
   * // X may not be 1
   * </pre>
   * In the above example, the iterator will iterate past the declaration of
   * Y and into the first block of the IF branch, and will stop at the
   * assignment {@code X = 5}.
   */
  static class LocalVarMotion implements Iterator<Node> {
    private final boolean valueHasSideEffects;
    private final FunctionlessLocalScope iterator;
    private final String varName;
    private Node lookAhead;

    /**
     * @return Create a LocalVarMotion for use with moving a value assigned
     * at a variable declaration.
     */
    static LocalVarMotion forVar(
        Node name, Node var, Node block) {
      Preconditions.checkArgument(var.getType() == Token.VAR);
      Preconditions.checkArgument(NodeUtil.isStatement(var));
      // The FunctionlessLocalScope must start at "name" as this may be used
      // before the Normalize pass, and thus the VAR node may define multiple
      // names and the "name" node may have siblings.  The actual assigned
      // value is skipped as it is a child of name.
      return new LocalVarMotion(
          name, new FunctionlessLocalScope(name, var, block));
    }

    /**
     * @return Create a LocalVarMotion for use with moving a value assigned
     * as part of a simple assignment expression ("a = b;").
     */
    static LocalVarMotion forAssign(
        Node name, Node assign, Node expr, Node block) {
      Preconditions.checkArgument(assign.getType() == Token.ASSIGN);
      Preconditions.checkArgument(expr.getType() == Token.EXPR_RESULT);
      // The FunctionlessLocalScope must start at "assign", to skip the value
      // assigned to "name" (which would be its sibling).
      return new LocalVarMotion(
          name, new FunctionlessLocalScope(assign, expr, block));
    }

    /**
     * @param iterator The to use while inspecting the node
     *     beginning with the deepest ancestor.
     */
    private LocalVarMotion(Node nameNode, FunctionlessLocalScope iterator) {
      Preconditions.checkArgument(nameNode.getType() == Token.NAME);
      Node valueNode = NodeUtil.getAssignedValue(nameNode);
      this.varName = nameNode.getString();
      this.valueHasSideEffects = valueNode != null &&
          NodeUtil.mayHaveSideEffects(valueNode);
      this.iterator = iterator;
      advanceLookAhead(true);
    }

    @Override
    public boolean hasNext() {
      return lookAhead != null;
    }

    @Override
    public Node next() {
      Node next = lookAhead;
      advanceLookAhead(false);
      return next;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("Not implemented");
    }

    private void advanceLookAhead(boolean atStart) {
      if (!atStart) {
        if (lookAhead == null) {
          return;
        }

        // Don't advance past a refrence to the variable that we're trying
        // to inline.
        Node curNode = iterator.current();
        if (curNode.getType() == Token.NAME &&
            varName.equals(curNode.getString())) {
          lookAhead = null;
          return;
        }
      }

      if (!iterator.hasNext()) {
        lookAhead = null;
        return;
      }

      Node nextNode = iterator.next();
      Node nextParent = iterator.currentParent();
      int type = nextNode.getType();

      if (valueHasSideEffects) {
        // Reject anything that might read state, i.e. any NAME that is not
        // newly redeclared or an assignment to a simple name.

        if (type == Token.NAME && !varName.equals(nextNode.getString())) {
          boolean blocked = false;
          if (nextParent == null) {
            blocked = true;
          } else {
            boolean assignsName = (nextParent.getType() == Token.ASSIGN
                    && nextNode == nextParent.getFirstChild());
            boolean isVarDeclaration = (nextParent.getType() == Token.VAR);

            if (!assignsName && !isVarDeclaration) {
              blocked = true;
            }
          }

          if (blocked) {
            lookAhead = null;
            return;
          }
        }
      }

      // Reject anything that might modify relevant state. We assume that
      // nobody relies on variables being undeclared, which will break
      // constructions like:
      //   var a = b;
      //   var b = 3;
      //   alert(a);
      if (NodeUtil.nodeTypeMayHaveSideEffects(nextNode) && type != Token.NAME
          || type == Token.NAME && nextParent.getType() == Token.CATCH) {
        lookAhead = null;
        return;
      }

      lookAhead = nextNode;
    }
  }
}
