/*
 * Copyright 2009 Google Inc.
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
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Set;

/**
 * Models an assignment that defines a variable and the removal of it.
 *
*
 */
class DefinitionsRemover {

  /**
   * @return an {@link Definition} object if the node contains a definition or
   *     {@code null} otherwise.
   */
  static Definition getDefinition(Node n, Node parent) {
    // TODO(user): Since we have parent pointers handy. A lot of constructors
    // can be simplied.
    if (parent == null) {
      return null;
    }

    if (NodeUtil.isVarDeclaration(n) && n.hasChildren()) {
      return new VarDefinition(n);
    } else if(NodeUtil.isFunction(parent) && parent.getFirstChild() == n) {
      if (!NodeUtil.isAnonymousFunction(parent)) {
        return new NamedFunctionDefinition(parent);
      } else if (!n.getString().equals("")) {
        return new AnonymousFunctionDefinition(parent);
      }
    } else if (NodeUtil.isAssign(parent) && parent.getFirstChild() == n) {
      return new AssignmentDefinition(parent);
    } else if (NodeUtil.isObjectLitKey(n, parent)) {
      return new ObjectLiteralPropertyDefinition(parent, n, n.getNext());
    } else if (parent.getType() == Token.LP) {
      Node function = parent.getParent();
      return new FunctionArgumentDefinition(function, n);
    }
    return null;
  }

  static interface Definition {
    void remove();

    /**
     * Variable or property name represented by this definition.
     * For example, in the case of assignments this method would
     * return the NAME, GETPROP or GETELEM expression that acts as the
     * assignment left hand side.
     *
     * @return the L-Value associated with this definition.
     *         The node's type is always NAME, GETPROP or GETELEM.
     */
    Node getLValue();

    /**
     * Value expression that acts as the right hand side of the
     * definition statement.
     */
    Node getRValue();
  }

  /**
   * Represents an name-only external definition.  The definition's
   * rhs is missing.
   */
  abstract static class IncompleteDefinition implements Definition {
    private static final Set<Integer> ALLOWED_TYPES =
        ImmutableSet.of(Token.NAME, Token.GETPROP, Token.GETELEM);
    private final Node lValue;

    IncompleteDefinition(Node lValue) {
      Preconditions.checkNotNull(lValue);
      Preconditions.checkArgument(
          ALLOWED_TYPES.contains(lValue.getType()),
          "Unexpected lValue type " + Token.name(lValue.getType()));
      this.lValue = lValue;
    }

    @Override
    public Node getLValue() {
      return lValue;
    }

    @Override
    public Node getRValue() {
      return null;
    }
  }


  /**
   * Represents an unknown definition.
   */
  static final class UnknownDefinition extends IncompleteDefinition {
    UnknownDefinition(Node lValue) {
      super(lValue);
    }

    @Override
    public void remove() {
      throw new IllegalArgumentException("Can't remove an UnknownDefinition");
    }
  }

  /**
   * Represents an name-only external definition.  The definition's
   * rhs is missing.
   */
  static final class ExternalNameOnlyDefinition extends IncompleteDefinition {

    ExternalNameOnlyDefinition(Node lValue) {
      super(lValue);
    }

    @Override
    public void remove() {
      throw new IllegalArgumentException(
          "Can't remove external name-only definition");
    }
  }

  /**
   * Represents an name-only external definition.  The definition's
   * rhs is missing.
   */
  static final class FunctionArgumentDefinition extends IncompleteDefinition {
    FunctionArgumentDefinition(Node function, Node argumentName) {
      super(argumentName);
      Preconditions.checkArgument(NodeUtil.isFunction(function));
      Preconditions.checkArgument(NodeUtil.isName(argumentName));
    }

    @Override
    public void remove() {
      throw new IllegalArgumentException(
          "Can't remove a FunctionArgumentDefinition");
    }
  }

  /**
   * Represents a function declaration or function expression.
   */
  static abstract class FunctionDefinition implements Definition {

    protected final Node function;

    FunctionDefinition(Node node) {
      Preconditions.checkArgument(NodeUtil.isFunction(node));
      function = node;
    }

    @Override
    public Node getLValue() {
      return function.getFirstChild();
    }

    @Override
    public Node getRValue() {
      return function;
    }
  }

  /**
   * Represents a function declaration without assignment node such as
   * {@code function foo()}.
   */
  static final class NamedFunctionDefinition extends FunctionDefinition {
    NamedFunctionDefinition(Node node) {
      super(node);
    }

    @Override
    public void remove() {
      function.detachFromParent();
    }
  }

  /**
   * Represents a function expression that acts as a rhs.  The defined
   * name is only reachable from within the function.
   */
  static final class AnonymousFunctionDefinition extends FunctionDefinition {
    AnonymousFunctionDefinition(Node node) {
      super(node);
      Preconditions.checkArgument(
          NodeUtil.isAnonymousFunction(node));
    }

    @Override
    public void remove() {
      // replace internal name with ""
      function.replaceChild(function.getFirstChild(),
                            Node.newString(Token.NAME, ""));
    }
  }

  /**
   * Represents a declaration within an assignment.
   */
  static final class AssignmentDefinition implements Definition {
    private final Node assignment;

    AssignmentDefinition(Node node) {
      Preconditions.checkArgument(NodeUtil.isAssign(node));
      assignment = node;
    }

    @Override
    public void remove() {
      // A simple assignment. foo = bar() -> bar();
      Node parent = assignment.getParent();
      Node last = assignment.getLastChild();
      assignment.removeChild(last);
      parent.replaceChild(assignment, last);
    }

    @Override
    public Node getLValue() {
      return assignment.getFirstChild();
    }

    @Override
    public Node getRValue() {
      return assignment.getLastChild();
    }
  }

  /**
   * Represents member declarations using a object literal.
   * Example: var x = { e : function() { } };
   */
  static final class ObjectLiteralPropertyDefinition implements Definition {

    private final Node literal;
    private final Node name;
    private final Node value;


    ObjectLiteralPropertyDefinition(Node lit, Node name, Node value) {
      this.literal = lit;
      this.name = name;
      this.value = value;
    }

    @Override
    public void remove() {
      literal.removeChild(name);
      literal.removeChild(value);
    }

    @Override
    public Node getLValue() {
      // TODO(user) revisit: object literal definitions are an example
      // of definitions whose lhs doesn't correspond to a node that
      // exists in the AST.  We will have to change the return type of
      // getLValue sooner or later in order to provide this added
      // flexibility.
      return new Node(Token.GETPROP,
                      new Node(Token.OBJECTLIT),
                      name.cloneNode());
    }

    @Override
    public Node getRValue() {
      return value;
    }
  }

  /**
   * Represents a VAR declaration with an assignment.
   */
  static final class VarDefinition implements Definition {
    private final Node name;
    VarDefinition(Node node) {
      Preconditions.checkArgument(NodeUtil.isVarDeclaration(node));
      Preconditions.checkArgument(node.hasChildren(),
          "VAR Declaration of " + node.getString() +
          "should be assigned a value.");
      name = node;
    }

    @Override
    public void remove() {
      Node var = name.getParent();
      Preconditions.checkState(var.getFirstChild() == var.getLastChild(),
          "AST should be normalized first");
      Node parent = var.getParent();
      Node rValue = name.removeFirstChild();
      Preconditions.checkState(parent.getType() != Token.FOR);
      parent.replaceChild(var, NodeUtil.newExpr(rValue));
    }

    @Override
    public Node getLValue() {
      return name;
    }

    @Override
    public Node getRValue() {
      return name.getFirstChild();
    }
  }
}
