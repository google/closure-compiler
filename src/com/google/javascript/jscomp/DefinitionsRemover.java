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
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Set;

/**
 * Models an assignment that defines a variable and the removal of it.
 *
 */
class DefinitionsRemover {

  /**
   * @return an {@link Definition} object if the node contains a definition or
   *     {@code null} otherwise.
   */
  static Definition getDefinition(Node n, boolean isExtern) {
    // TODO(user): Since we have parent pointers handy. A lot of constructors
    // can be simplified.

    // This logic must match #isDefinitionNode
    Node parent = n.getParent();
    if (parent == null) {
      return null;
    }

    if (NodeUtil.isVarDeclaration(n) && n.hasChildren()) {
      return new VarDefinition(n, isExtern);
    } else if (parent.isFunction() && parent.getFirstChild() == n) {
      if (!NodeUtil.isFunctionExpression(parent)) {
        return new NamedFunctionDefinition(parent, isExtern);
      } else if (!n.getString().equals("")) {
        return new FunctionExpressionDefinition(parent, isExtern);
      }
    } else if (parent.isAssign() && parent.getFirstChild() == n) {
      return new AssignmentDefinition(parent, isExtern);
    } else if (NodeUtil.isObjectLitKey(n, parent)) {
      return new ObjectLiteralPropertyDefinition(parent, n, n.getFirstChild(),
          isExtern);
    } else if (parent.isParamList()) {
      Node function = parent.getParent();
      return new FunctionArgumentDefinition(function, n, isExtern);
    }
    return null;
  }

  /**
   * @return Whether a definition object can be created.
   */
  static boolean isDefinitionNode(Node n) {
    // This logic must match #getDefinition
    Node parent = n.getParent();
    if (parent == null) {
      return false;
    }

    if (NodeUtil.isVarDeclaration(n) && n.hasChildren()) {
      return true;
    } else if (parent.isFunction() && parent.getFirstChild() == n) {
      if (!NodeUtil.isFunctionExpression(parent)) {
        return true;
      } else if (!n.getString().equals("")) {
        return true;
      }
    } else if (parent.isAssign() && parent.getFirstChild() == n) {
      return true;
    } else if (NodeUtil.isObjectLitKey(n, parent)) {
      return true;
    } else if (parent.isParamList()) {
      return true;
    }
    return false;
  }


  static abstract class Definition {

    private final boolean isExtern;

    Definition(boolean isExtern) {
      this.isExtern = isExtern;
    }

    /**
     * Removes this definition from the AST if it is not an extern.
     *
     * This method should not be called on a definition for which isExtern()
     * is true.
     */
    public void remove() {
      if (!isExtern) {
        performRemove();
      } else {
        throw new IllegalStateException("Attempt to remove() an extern" +
            " definition.");
      }
    }

    /**
     * Subclasses should override to remove the definition from the AST.
     */
    protected abstract void performRemove();

    /**
     * Variable or property name represented by this definition.
     * For example, in the case of assignments this method would
     * return the NAME, GETPROP or GETELEM expression that acts as the
     * assignment left hand side.
     *
     * @return the L-Value associated with this definition.
     *         The node's type is always NAME, GETPROP or GETELEM.
     */
    public abstract Node getLValue();

    /**
     * Value expression that acts as the right hand side of the
     * definition statement.
     */
    public abstract Node getRValue();

    /**
     * Returns true if the definition is an extern.
     */
    public boolean isExtern() {
      return isExtern;
    }
  }

  /**
   * Represents an name-only external definition.  The definition's
   * RHS is missing.
   */
  abstract static class IncompleteDefinition extends Definition {
    private static final Set<Integer> ALLOWED_TYPES =
        ImmutableSet.of(Token.NAME, Token.GETPROP, Token.GETELEM);
    private final Node lValue;

    IncompleteDefinition(Node lValue, boolean inExterns) {
      super(inExterns);
      Preconditions.checkNotNull(lValue);
      Preconditions.checkArgument(ALLOWED_TYPES.contains(lValue.getType()),
          "Unexpected lValue type %s", Token.name(lValue.getType()));
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
    UnknownDefinition(Node lValue, boolean inExterns) {
      super(lValue, inExterns);
    }

    @Override
    public void performRemove() {
      throw new IllegalArgumentException("Can't remove an UnknownDefinition");
    }
  }

  /**
   * Represents an name-only external definition.  The definition's
   * RHS is missing.
   */
  static final class ExternalNameOnlyDefinition extends IncompleteDefinition {

    ExternalNameOnlyDefinition(Node lValue) {
      super(lValue, true);
    }

    @Override
    public void performRemove() {
      throw new IllegalArgumentException(
          "Can't remove external name-only definition");
    }
  }

  /**
   * Represents a function formal parameter. The definition's RHS is missing.
   */
  static final class FunctionArgumentDefinition extends IncompleteDefinition {
    FunctionArgumentDefinition(Node function,
        Node argumentName,
        boolean inExterns) {
      super(argumentName, inExterns);
      Preconditions.checkArgument(function.isFunction());
      Preconditions.checkArgument(argumentName.isName());
    }

    @Override
    public void performRemove() {
      throw new IllegalArgumentException(
          "Can't remove a FunctionArgumentDefinition");
    }
  }

  /**
   * Represents a function declaration or function expression.
   */
  abstract static class FunctionDefinition extends Definition {

    protected final Node function;

    FunctionDefinition(Node node, boolean inExterns) {
      super(inExterns);
      Preconditions.checkArgument(node.isFunction());
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
    NamedFunctionDefinition(Node node, boolean inExterns) {
      super(node, inExterns);
    }

    @Override
    public void performRemove() {
      function.detachFromParent();
    }
  }

  /**
   * Represents a function expression that acts as a RHS.  The defined
   * name is only reachable from within the function.
   */
  static final class FunctionExpressionDefinition extends FunctionDefinition {
    FunctionExpressionDefinition(Node node, boolean inExterns) {
      super(node, inExterns);
      Preconditions.checkArgument(
          NodeUtil.isFunctionExpression(node));
    }

    @Override
    public void performRemove() {
      // replace internal name with ""
      function.replaceChild(function.getFirstChild(), IR.name(""));
    }
  }

  /**
   * Represents a declaration within an assignment.
   */
  static final class AssignmentDefinition extends Definition {
    private final Node assignment;

    AssignmentDefinition(Node node, boolean inExterns) {
      super(inExterns);
      Preconditions.checkArgument(node.isAssign());
      assignment = node;
    }

    @Override
    public void performRemove() {
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
  static final class ObjectLiteralPropertyDefinition extends Definition {

    private final Node literal;
    private final Node name;
    private final Node value;

    ObjectLiteralPropertyDefinition(Node lit, Node name, Node value,
          boolean isExtern) {
      super(isExtern);

      this.literal = lit;
      this.name = name;
      this.value = value;
    }

    @Override
    public void performRemove() {
      literal.removeChild(name);
    }

    @Override
    public Node getLValue() {
      // TODO(user) revisit: object literal definitions are an example
      // of definitions whose LHS doesn't correspond to a node that
      // exists in the AST.  We will have to change the return type of
      // getLValue sooner or later in order to provide this added
      // flexibility.

      switch (name.getType()) {
        case Token.SETTER_DEF:
        case Token.GETTER_DEF:
        case Token.STRING_KEY:
          // TODO(johnlenz): return a GETELEM for quoted strings.
          return IR.getprop(
              IR.objectlit(),
              IR.string(name.getString()));
        default:
          throw new IllegalStateException("unexpected");
      }
    }

    @Override
    public Node getRValue() {
      return value;
    }
  }

  /**
   * Represents a VAR declaration with an assignment.
   */
  static final class VarDefinition extends Definition {
    private final Node name;
    VarDefinition(Node node, boolean inExterns) {
      super(inExterns);
      Preconditions.checkArgument(NodeUtil.isVarDeclaration(node));
      Preconditions.checkArgument(node.hasChildren(),
          "VAR Declaration of %sshould be assigned a value.", node.getString());
      name = node;
    }

    @Override
    public void performRemove() {
      Node var = name.getParent();
      Preconditions.checkState(var.getFirstChild() == var.getLastChild(),
          "AST should be normalized first");
      Node parent = var.getParent();
      Node rValue = name.removeFirstChild();
      Preconditions.checkState(!parent.isFor());
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
