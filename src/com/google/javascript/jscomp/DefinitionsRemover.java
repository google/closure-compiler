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
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import javax.annotation.Nullable;

/**
 * Models an assignment that defines a variable and the removal of it.
 *
 */
class DefinitionsRemover {

  /**
   * This logic must match {@link #isDefinitionNode(Node n)}.
   *
   * @return an {@link Definition} object if the node contains a definition or {@code null}
   *     otherwise.
   */

  static Definition getDefinition(Node n, boolean isExtern) {
    Node parent = n.getParent();
    if (parent == null) {
      return null;
    }

    if (NodeUtil.isNameDeclaration(parent) && n.isName() && (isExtern || n.hasChildren())) {
      return new VarDefinition(n, isExtern);
    } else if (parent.isFunction() && parent.getFirstChild() == n) {
      if (NodeUtil.isFunctionDeclaration(parent)) {
        return new NamedFunctionDefinition(parent, isExtern);
      } else if (!n.getString().isEmpty()) {
        return new FunctionExpressionDefinition(parent, isExtern);
      }
    } else if (parent.isClass() && parent.getFirstChild() == n) {
      if (!NodeUtil.isClassExpression(parent)) {
        return new NamedClassDefinition(parent, isExtern);
      } else if (!n.isEmpty()) {
        return new ClassExpressionDefinition(parent, isExtern);
      }
    } else if (n.isMemberFunctionDef() && parent.isClassMembers()) {
      return new MemberFunctionDefinition(n, isExtern);
    } else if (parent.isAssign() && parent.getFirstChild() == n) {
      return new AssignmentDefinition(parent, isExtern);
    } else if (NodeUtil.mayBeObjectLitKey(n)) {
      return new ObjectLiteralPropertyDefinition(n, n.getFirstChild(), isExtern);
    } else if (NodeUtil.getEnclosingType(n, Token.PARAM_LIST) != null && n.isName()) {
      // TODO(b/128035138): This fails when there are arbitrary expressions inside the PARAM_LIST,
      // which may contain NAMEs.
      // Error case: `function f(a = b) { }`, `b` is not a param
      // Error case: `function f({[b]: a}) { }`, `b` is not a param

      Node paramList = NodeUtil.getEnclosingType(n, Token.PARAM_LIST);
      Node function = paramList.getParent();
      return new FunctionArgumentDefinition(function, n, isExtern);
    } else if (parent.getToken() == Token.COLON && parent.getFirstChild() == n && isExtern) {
      Node grandparent = parent.getParent();
      checkState(grandparent.getToken() == Token.LB);
      checkState(grandparent.getParent().getToken() == Token.LC);
      return new RecordTypePropertyDefinition(n);
    } else if (isExtern && n.isGetProp() && parent.isExprResult() && n.isQualifiedName()) {
      return new ExternalNameOnlyDefinition(n);
    }
    return null;
  }

  /**
   * This logic must match {@link #getDefinition(Node, boolean)}.
   *
   * @return Whether a definition object can be created.
   */
  static boolean isDefinitionNode(Node n) {
    Node parent = n.getParent();
    if (parent == null) {
      return false;
    }

    if (NodeUtil.isNameDeclaration(parent) && n.isName()
        && (n.isFromExterns() || n.hasChildren())) {
      return true;
    } else if (parent.isFunction() && parent.getFirstChild() == n) {
      if (!NodeUtil.isFunctionExpression(parent)) {
        return true;
      } else if (!n.getString().isEmpty()) {
        return true;
      }
    } else if (parent.isClass() && parent.getFirstChild() == n) {
      if (!NodeUtil.isClassExpression(parent)) {
        return true;
      } else if (!n.isEmpty()) {
        return true;
      }
    } else if (n.isMemberFunctionDef() && parent.isClassMembers()) {
      return true;
    } else if (parent.isAssign() && parent.getFirstChild() == n) {
      return true;
    } else if (NodeUtil.mayBeObjectLitKey(n)) {
      return true;
    } else if (parent.isParamList()) {
      return true;
    } else if (parent.getToken() == Token.COLON
        && parent.getFirstChild() == n
        && n.isFromExterns()) {
      Node grandparent = parent.getParent();
      checkState(grandparent.getToken() == Token.LB);
      checkState(grandparent.getParent().getToken() == Token.LC);
      return true;
    } else if (n.isFromExterns() && parent.isExprResult() && n.isGetProp() && n.isQualifiedName()) {
      return true;
    }
    return false;
  }


  abstract static class Definition {

    private final boolean isExtern;
    private final String simplifiedName;

    Definition(boolean isExtern, String simplifiedName) {
      this.isExtern = isExtern;
      this.simplifiedName = simplifiedName;
    }

    /**
     * Removes this definition from the AST if it is not an extern.
     *
     * This method should not be called on a definition for which isExtern()
     * is true.
     */
    public void remove(AbstractCompiler compiler) {
      if (!isExtern) {
        performRemove(compiler);
      } else {
        throw new IllegalStateException("Attempt to remove() an extern definition.");
      }
    }

    /**
     * Subclasses should override to remove the definition from the AST.
     */
    protected abstract void performRemove(AbstractCompiler compiler);

    /**
     * Extract a name from a node. In the case of GETPROP nodes, replace the namespace or object
     * expression with "this" for simplicity and correctness at the expense of inefficiencies due to
     * higher chances of name collisions.
     *
     * <p>TODO(user) revisit. it would be helpful to at least use fully qualified names in the case
     * of namespaces. Might not matter as much if this pass runs after {@link CollapseProperties}.
     */
    @Nullable
    public static String getSimplifiedName(Node node) {
      if (node.isName()) {
        String name = node.getString();
        if (name != null && !name.isEmpty()) {
          return name;
        } else {
          return null;
        }
      } else if (node.isGetProp()) {
        return "this." + node.getLastChild().getString();
      } else if (node.isMemberFunctionDef()) {
        return "this." + node.getString();
      }
      return null;
    }

    public String getSimplifiedName() {
      return simplifiedName;
    }

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

    @Override
    public String toString() {
      return getLValue().getQualifiedName() + " = " + getRValue();
    }
  }

  /**
   * Represents an name-only external definition.  The definition's
   * RHS is missing.
   */
  abstract static class IncompleteDefinition extends Definition {
    private static final ImmutableSet<Token> ALLOWED_TYPES =
        ImmutableSet.of(Token.NAME, Token.GETPROP, Token.GETELEM);
    private final Node lValue;

    IncompleteDefinition(Node lValue, boolean inExterns) {
      super(inExterns, Definition.getSimplifiedName(lValue));
      checkNotNull(lValue);

      Preconditions.checkArgument(
          ALLOWED_TYPES.contains(lValue.getToken()),
          "Unexpected lValue type %s",
          lValue.getToken());
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
    public void performRemove(AbstractCompiler compiler) {
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
    public void performRemove(AbstractCompiler compiler) {
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
      checkArgument(function.isFunction());
      checkArgument(argumentName.isName());
    }

    @Override
    public void performRemove(AbstractCompiler compiler) {
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
      this(node, inExterns, Definition.getSimplifiedName(node.getFirstChild()));
    }

    FunctionDefinition(Node node, boolean inExterns, String name) {
      super(inExterns, name);
      checkArgument(node.isFunction(), node);
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
    public void performRemove(AbstractCompiler compiler) {
      compiler.reportChangeToEnclosingScope(function);
      function.detach();
      NodeUtil.markFunctionsDeleted(function, compiler);
    }
  }

  /**
   * Represents a function expression that acts as a RHS.  The defined
   * name is only reachable from within the function.
   */
  static final class FunctionExpressionDefinition extends FunctionDefinition {
    FunctionExpressionDefinition(Node node, boolean inExterns) {
      super(node, inExterns);
      checkArgument(NodeUtil.isFunctionExpression(node));
    }

    @Override
    public void performRemove(AbstractCompiler compiler) {
      // replace internal name with ""
      function.replaceChild(function.getFirstChild(), IR.name(""));
      compiler.reportChangeToEnclosingScope(function.getFirstChild());
    }
  }

  /**
   * Represents a class member function.
   */

  static final class MemberFunctionDefinition extends FunctionDefinition {

    protected final Node memberFunctionDef;

    MemberFunctionDefinition(Node node, boolean inExterns) {
      super(node.getFirstChild(), inExterns, Definition.getSimplifiedName(node));
      checkState(node.isMemberFunctionDef(), node);
      memberFunctionDef = node;
    }

    @Override
    public void performRemove(AbstractCompiler compiler) {
      NodeUtil.deleteNode(memberFunctionDef, compiler);
    }

    @Override
    public Node getLValue() {
      // As far as we know, only the property name matters so the target can be an object literal
      return IR.getprop(IR.objectlit(), memberFunctionDef.getString());
    }
  }

  /**
   * Represents a class declaration or function expression.
   */
  abstract static class ClassDefinition extends Definition {

    protected final Node c;

    ClassDefinition(Node node, boolean inExterns) {
      super(inExterns, Definition.getSimplifiedName(node.getFirstChild()));
      Preconditions.checkArgument(node.isClass());
      c = node;
    }

    @Override
    public Node getLValue() {
      return c.getFirstChild();
    }

    @Override
    public Node getRValue() {
      return c;
    }
  }

  /**
   * Represents a function declaration without assignment node such as
   * {@code function foo()}.
   */
  static final class NamedClassDefinition extends ClassDefinition {
    NamedClassDefinition(Node node, boolean inExterns) {
      super(node, inExterns);
    }

    @Override
    public void performRemove(AbstractCompiler compiler) {
      NodeUtil.deleteNode(c, compiler);
    }
  }

  /**
   * Represents a class expression that acts as a RHS.  The defined
   * name is only reachable from within the function.
   */
  static final class ClassExpressionDefinition extends ClassDefinition {
    ClassExpressionDefinition(Node node, boolean inExterns) {
      super(node, inExterns);
      Preconditions.checkArgument(
          NodeUtil.isClassExpression(node));
    }

    @Override
    public void performRemove(AbstractCompiler compiler) {
      // replace internal name with ""
      c.replaceChild(c.getFirstChild(), IR.empty());
      compiler.reportChangeToEnclosingScope(c.getFirstChild());
    }
  }

  /**
   * Represents a declaration within an assignment.
   */
  static final class AssignmentDefinition extends Definition {
    private final Node assignment;

    AssignmentDefinition(Node node, boolean inExterns) {
      super(inExterns, Definition.getSimplifiedName(node.getFirstChild()));
      checkArgument(node.isAssign());
      assignment = node;
    }

    @Override
    public void performRemove(AbstractCompiler compiler) {
      // A simple assignment. foo = bar() -> bar();
      Node parent = assignment.getParent();
      Node last = assignment.getLastChild();
      assignment.removeChild(last);
      parent.replaceChild(assignment, last);
      compiler.reportChangeToEnclosingScope(parent);
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
   * Represents member declarations using a record type from externs.
   * Example: /** @typedef {{prop: number}} *\/ var typdef;
   */
  static final class RecordTypePropertyDefinition extends IncompleteDefinition {
    RecordTypePropertyDefinition(Node name) {
      super(IR.getprop(IR.objectlit(), name.cloneNode()),
            /** isExtern */ true);
      checkArgument(name.isString());
    }

    @Override
    public void performRemove(AbstractCompiler compiler) {
      throw new UnsupportedOperationException("Can't remove RecordType def");
    }
  }


  /**
   * Represents member declarations using a object literal.
   * Example: var x = { e : function() { } };
   */
  static final class ObjectLiteralPropertyDefinition extends Definition {
    private final Node name;
    private final Node value;

    ObjectLiteralPropertyDefinition(Node name, Node value, boolean isExtern) {
      super(isExtern, Definition.getSimplifiedName(getLValue(name)));

      this.name = name;
      this.value = value;
    }

    @Override
    public void performRemove(AbstractCompiler compiler) {
      NodeUtil.deleteNode(name, compiler);
    }

    @Override
    public Node getLValue() {
      return getLValue(name);
    }

    private static Node getLValue(Node name) {
      // TODO(user) revisit: object literal definitions are an example
      // of definitions whose LHS doesn't correspond to a node that
      // exists in the AST.  We will have to change the return type of
      // getLValue sooner or later in order to provide this added
      // flexibility.

      switch (name.getToken()) {
        case SETTER_DEF:
        case GETTER_DEF:
        case STRING_KEY:
        case MEMBER_FUNCTION_DEF:
          // TODO(johnlenz): return a GETELEM for quoted strings.
          return IR.getprop(
              IR.objectlit(),
              IR.string(name.getString()));
        default:
          throw new IllegalStateException("Unexpected left Token: " + name.getToken());
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
      super(inExterns, Definition.getSimplifiedName(node));
      checkArgument(NodeUtil.isNameDeclaration(node.getParent()) && node.isName());
      Preconditions.checkArgument(inExterns || node.hasChildren(),
          "VAR Declaration of %s must be assigned a value.", node.getString());
      name = node;
    }

    @Override
    public void performRemove(AbstractCompiler compiler) {
      Node var = name.getParent();
      checkState(var.getFirstChild() == var.getLastChild(), "AST should be normalized first");
      Node parent = var.getParent();
      Node rValue = name.removeFirstChild();
      checkState(!NodeUtil.isLoopStructure(parent));
      parent.replaceChild(var, NodeUtil.newExpr(rValue));
      compiler.reportChangeToEnclosingScope(parent);
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
