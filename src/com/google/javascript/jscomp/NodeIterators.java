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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * A package for common iteration patterns.
 *
 * All iterators are forward, post-order traversals unless otherwise noted.
 */
class NodeIterators {

  private NodeIterators() {} /* all static */

  /**
   * Traverses the local scope, skipping all function nodes.
   */
  static class FunctionlessLocalScope implements Iterator<Node> {
    private final Deque<Node> ancestors = new ArrayDeque<>();

    /**
     * @param ancestors The ancestors of the point where iteration will start,
     *     beginning with the deepest ancestor. The start node will not be
     *     exposed in the iteration.
     */
    FunctionlessLocalScope(Node ... ancestors) {
      checkArgument(ancestors.length > 0);

      for (Node n : ancestors) {
        if (n.isFunction()) {
          break;
        }

        this.ancestors.addFirst(n);
      }
    }

    @Override
    public boolean hasNext() {
      // Check if the current node has any nodes after it.
      return !(ancestors.size() == 1 && ancestors.peekLast().getNext() == null);
    }

    @Override
    public Node next() {
      Node current = ancestors.removeLast();
      if (current.getNext() == null) {
        current = ancestors.peekLast();

        // If this is a function node, skip it.
        if (current.isFunction()) {
          return next();
        }
      } else {
        current = current.getNext();
        ancestors.addLast(current);

        // If this is a function node, skip it.
        if (current.isFunction()) {
          return next();
        }

        while (current.hasChildren()) {
          current = current.getFirstChild();
          ancestors.addLast(current);

          // If this is a function node, skip it.
          if (current.isFunction()) {
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
      return ancestors.peekLast();
    }

    /**
     * Gets the parent of the node most recently returned by next().
     */
    protected Node currentParent() {
      return ancestors.size() >= 2 ? current().getParent() : null;
    }

    /**
     * Gets the ancestors of the current node, with the deepest node first.
     * Only exposed for testing purposes.
     */
    List<Node> currentAncestors() {
      List<Node> list = new ArrayList<>(ancestors);
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
    private final AbstractCompiler compiler;
    private final boolean valueHasSideEffects;
    private final FunctionlessLocalScope iterator;
    private final String varName;
    private Node lookAhead;

    /**
     * The name is a bit of a misnomer; this works with let and const as well.
     *
     * @return Create a LocalVarMotion for use with moving a value assigned at a variable
     *     declaration.
     */
    static LocalVarMotion forVar(AbstractCompiler compiler, Node name, Node var, Node block) {
      checkArgument(NodeUtil.isNameDeclaration(var));
      checkArgument(NodeUtil.isStatement(var));
      // The FunctionlessLocalScope must start at "name" as this may be used
      // before the Normalize pass, and thus the VAR node may define multiple
      // names and the "name" node may have siblings.  The actual assigned
      // value is skipped as it is a child of name.
      return new LocalVarMotion(compiler, name, new FunctionlessLocalScope(name, var, block));
    }

    /**
     * @return Create a LocalVarMotion for use with moving a value assigned as part of a simple
     *     assignment expression ("a = b;").
     */
    static LocalVarMotion forAssign(
        AbstractCompiler compiler, Node name, Node assign, Node expr, Node block) {
      checkArgument(assign.isAssign());
      checkArgument(expr.isExprResult());
      // The FunctionlessLocalScope must start at "assign", to skip the value
      // assigned to "name" (which would be its sibling).
      return new LocalVarMotion(compiler, name, new FunctionlessLocalScope(assign, expr, block));
    }

    /**
     * @param compiler
     * @param iterator The iterator to use while inspecting the node
     */
    private LocalVarMotion(
        AbstractCompiler compiler, Node nameNode, FunctionlessLocalScope iterator) {
      checkArgument(nameNode.isName());
      Node valueNode = NodeUtil.getAssignedValue(nameNode);
      this.compiler = checkNotNull(compiler);
      this.varName = nameNode.getString();
      this.valueHasSideEffects =
          valueNode != null && compiler.getAstAnalyzer().mayHaveSideEffects(valueNode);
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

        // Don't advance past a reference to the variable that we're trying
        // to inline.
        Node curNode = iterator.current();
        if (curNode.isName() && varName.equals(curNode.getString())) {
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
      Token type = nextNode.getToken();

      if (valueHasSideEffects) {
        // Reject anything that might read state
        boolean readsState = false;

        if (// Any read of a different variable.
            (nextNode.isName() && !varName.equals(nextNode.getString()))
            // Any read of a property.
            || (nextNode.isGetProp() || nextNode.isGetElem())) {

          // If this is a simple assign, we'll be ok.
          if (nextParent == null || !NodeUtil.isNameDeclOrSimpleAssignLhs(nextNode, nextParent)) {
            readsState = true;
          }

        } else if (nextNode.isCall() || nextNode.isNew()) {
          // This isn't really an important case. In most cases when we use
          // CALL or NEW, we're invoking it on a NAME or a GETPROP. And in the
          // few cases where we're not, it's because we have an anonymous
          // function that escapes the variable we're worried about. But we
          // include this for completeness.
          readsState = true;
        }

        if (readsState) {
          lookAhead = null;
          return;
        }
      }

      // Reject anything that might modify relevant state. We assume that
      // nobody relies on variables being undeclared, which will break
      // constructions like:
      //   var a = b;
      //   var b = 3;
      //   alert(a);
      if ((compiler.getAstAnalyzer().nodeTypeMayHaveSideEffects(nextNode) && type != Token.NAME)
          || (type == Token.NAME && nextParent.isCatch())) {
        lookAhead = null;
        return;
      }

      lookAhead = nextNode;
    }
  }
}
