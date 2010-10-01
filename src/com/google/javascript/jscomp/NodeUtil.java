/*
 * Copyright 2004 Google Inc.
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
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TokenStream;
import com.google.javascript.rhino.jstype.TernaryValue;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * NodeUtil contains utilities that get properties from the Node object.
 *
 */
public final class NodeUtil {

  // TODO(user): Eliminate this class and make all of the static methods
  // instance methods of com.google.javascript.rhino.Node.

  /** the set of builtin constructors that don't have side effects. */
  private static final Set<String> CONSTRUCTORS_WITHOUT_SIDE_EFFECTS =
      new HashSet<String>(Arrays.asList(
        "Array",
        "Date",
        "Error",
        "Object",
        "RegExp",
        "XMLHttpRequest"));

  // Utility class; do not instantiate.
  private NodeUtil() {}

  /**
   * Gets the boolean value of a node that represents a expression. This method
   * effectively emulates the <code>Boolean()</code> JavaScript cast function.
   * Note: unlike getBooleanValue this function does not return UNKNOWN
   * for expressions with side-effects.
   */
  static TernaryValue getExpressionBooleanValue(Node n) {
    switch (n.getType()) {
      case Token.ASSIGN:
      case Token.COMMA:
        // For ASSIGN and COMMA the value is the value of the RHS.
        return getExpressionBooleanValue(n.getLastChild());
      case Token.NOT:
        TernaryValue value = getExpressionBooleanValue(n.getLastChild());
        return value.not();
      case Token.AND: {
        TernaryValue lhs = getExpressionBooleanValue(n.getFirstChild());
        TernaryValue rhs = getExpressionBooleanValue(n.getLastChild());
        return lhs.and(rhs);
      }
      case Token.OR:  {
        TernaryValue lhs = getExpressionBooleanValue(n.getFirstChild());
        TernaryValue rhs = getExpressionBooleanValue(n.getLastChild());
        return lhs.or(rhs);
      }
      case Token.HOOK:  {
        TernaryValue trueValue = getExpressionBooleanValue(
            n.getFirstChild().getNext());
        TernaryValue falseValue = getExpressionBooleanValue(n.getLastChild());
        if (trueValue.equals(falseValue)) {
          return trueValue;
        } else {
          return TernaryValue.UNKNOWN;
        }
      }
      default:
        return getBooleanValue(n);
    }
  }

  /**
   * Gets the boolean value of a node that represents a literal. This method
   * effectively emulates the <code>Boolean()</code> JavaScript cast function.
   */
  static TernaryValue getBooleanValue(Node n) {
    switch (n.getType()) {
      case Token.STRING:
        return TernaryValue.forBoolean(n.getString().length() > 0);

      case Token.NUMBER:
        return TernaryValue.forBoolean(n.getDouble() != 0);

      case Token.NULL:
      case Token.FALSE:
      case Token.VOID:
        return TernaryValue.FALSE;

      case Token.NAME:
        String name = n.getString();
        if ("undefined".equals(name)
            || "NaN".equals(name)) {
          // We assume here that programs don't change the value of the keyword
          // undefined to something other than the value undefined.
          return TernaryValue.FALSE;
        } else if ("Infinity".equals(name)) {
          return TernaryValue.TRUE;
        }
        break;

      case Token.TRUE:
      case Token.ARRAYLIT:
      case Token.OBJECTLIT:
      case Token.REGEXP:
        return TernaryValue.TRUE;
    }

    return TernaryValue.UNKNOWN;
  }


  /**
   * Gets the value of a node as a String, or null if it cannot be converted.
   * When it returns a non-null String, this method effectively emulates the
   * <code>String()</code> JavaScript cast function.
   */
  static String getStringValue(Node n) {
    // TODO(user): Convert constant array, object, and regex literals as well.
    switch (n.getType()) {
      case Token.NAME:
      case Token.STRING:
        return n.getString();

      case Token.NUMBER:
        double value = n.getDouble();
        long longValue = (long) value;

        // Return "1" instead of "1.0"
        if (longValue == value) {
          return Long.toString(longValue);
        } else {
          return Double.toString(n.getDouble());
        }

      case Token.FALSE:
      case Token.TRUE:
      case Token.NULL:
        return Node.tokenToName(n.getType());

      case Token.VOID:
        return "undefined";
    }
    return null;
  }

  /**
   * Gets the function's name. This method recognizes five forms:
   * <ul>
   * <li>{@code function name() ...}</li>
   * <li>{@code var name = function() ...}</li>
   * <li>{@code qualified.name = function() ...}</li>
   * <li>{@code var name2 = function name1() ...}</li>
   * <li>{@code qualified.name2 = function name1() ...}</li>
   * </ul>
   * In two last cases with named function expressions, the second name is
   * returned (the variable of qualified name).
   *
   * @param n a node whose type is {@link Token#FUNCTION}
   * @return the function's name, or {@code null} if it has no name
   */
  static String getFunctionName(Node n) {
    Node parent = n.getParent();
    String name = n.getFirstChild().getString();
    switch (parent.getType()) {
      case Token.NAME:
        // var name = function() ...
        // var name2 = function name1() ...
        return parent.getString();

      case Token.ASSIGN:
        // qualified.name = function() ...
        // qualified.name2 = function name1() ...
        return parent.getFirstChild().getQualifiedName();

      default:
        // function name() ...
        return name != null && name.length() != 0 ? name : null;
    }
  }

  /**
   * Gets the function's name. This method recognizes the forms:
   * <ul>
   * <li>{@code {'name': function() ...}}</li>
   * <li>{@code {name: function() ...}}</li>
   * <li>{@code function name() ...}</li>
   * <li>{@code var name = function() ...}</li>
   * <li>{@code qualified.name = function() ...}</li>
   * <li>{@code var name2 = function name1() ...}</li>
   * <li>{@code qualified.name2 = function name1() ...}</li>
   * </ul>
   *
   * @param n a node whose type is {@link Token#FUNCTION}
   * @return the function's name, or {@code null} if it has no name
   */
  static String getNearestFunctionName(Node n) {
    String name = getFunctionName(n);
    if (name != null) {
      return name;
    }

    // Check for the form { 'x' : function() { } }
    Node parent = n.getParent();
    switch (parent.getType()) {
      case Token.OBJECTLIT:
        // Return the name of the literal's key.
        return getStringValue(parent.getFirstChild());
    }

    return null;
  }


  /**
   * Returns true if this is an immutable value.
   */
  static boolean isImmutableValue(Node n) {
    switch (n.getType()) {
      case Token.STRING:
      case Token.NUMBER:
      case Token.NULL:
      case Token.TRUE:
      case Token.FALSE:
        return true;
      case Token.VOID:
      case Token.NEG:
        return isImmutableValue(n.getFirstChild());
      case Token.NAME:
        String name = n.getString();
        // We assume here that programs don't change the value of the keyword
        // undefined to something other than the value undefined.
        return "undefined".equals(name)
            || "Infinity".equals(name)
            || "NaN".equals(name);
    }

    return false;
  }

  /**
   * Returns true if this is a literal value. We define a literal value
   * as any node that evaluates to the same thing regardless of when or
   * where it is evaluated. So /xyz/ and [3, 5] are literals, but
   * the name a is not.
   *
   * Function literals do not meet this definition, because they
   * lexically capture variables. For example, if you have
   * <code>
   * function() { return a; }
   * </code>
   * If it is evaluated in a different scope, then it
   * captures a different variable. Even if the function did not read
   * any captured vairables directly, it would still fail this definition,
   * because it affects the lifecycle of variables in the enclosing scope.
   *
   * However, a function literal with respect to a particular scope is
   * a literal.
   *
   * @param includeFunctions If true, all function expressions will be
   *     treated as literals.
   */
  static boolean isLiteralValue(Node n, boolean includeFunctions) {
    switch (n.getType()) {
      case Token.ARRAYLIT:
      case Token.OBJECTLIT:
      case Token.REGEXP:
        // Return true only if all children are const.
        for (Node child = n.getFirstChild(); child != null;
             child = child.getNext()) {
          if (!isLiteralValue(child, includeFunctions)) {
            return false;
          }
        }
        return true;

      case Token.FUNCTION:
        return includeFunctions && !NodeUtil.isFunctionDeclaration(n);

      default:
        return isImmutableValue(n);
    }
  }

  /**
   * Determines whether the given value may be assigned to a define.
   *
   * @param val The value being assigned.
   * @param defines The list of names of existing defines.
   */
  static boolean isValidDefineValue(Node val, Set<String> defines) {
    switch (val.getType()) {
      case Token.STRING:
      case Token.NUMBER:
      case Token.TRUE:
      case Token.FALSE:
        return true;

      // Binary operators are only valid if both children are valid.
      case Token.ADD:
      case Token.BITAND:
      case Token.BITNOT:
      case Token.BITOR:
      case Token.BITXOR:
      case Token.DIV:
      case Token.EQ:
      case Token.GE:
      case Token.GT:
      case Token.LE:
      case Token.LSH:
      case Token.LT:
      case Token.MOD:
      case Token.MUL:
      case Token.NE:
      case Token.RSH:
      case Token.SHEQ:
      case Token.SHNE:
      case Token.SUB:
      case Token.URSH:
        return isValidDefineValue(val.getFirstChild(), defines)
            && isValidDefineValue(val.getLastChild(), defines);

      // Uniary operators are valid if the child is valid.
      case Token.NOT:
      case Token.NEG:
      case Token.POS:
        return isValidDefineValue(val.getFirstChild(), defines);

      // Names are valid if and only if they are defines themselves.
      case Token.NAME:
      case Token.GETPROP:
        if (val.isQualifiedName()) {
          return defines.contains(val.getQualifiedName());
        }
    }
    return false;
  }

  /**
   * Returns whether this a BLOCK node with no children.
   *
   * @param block The node.
   */
  static boolean isEmptyBlock(Node block) {
    if (block.getType() != Token.BLOCK) {
      return false;
    }

    for (Node n = block.getFirstChild(); n != null; n = n.getNext()) {
      if (n.getType() != Token.EMPTY) {
        return false;
      }
    }
    return true;
  }

  static boolean isSimpleOperator(Node n) {
    return isSimpleOperatorType(n.getType());
  }

  /**
   * A "simple" operator is one whose children are expressions,
   * has no direct side-effects (unlike '+='), and has no
   * conditional aspects (unlike '||').
   */
  static boolean isSimpleOperatorType(int type) {
    switch (type) {
      case Token.ADD:
      case Token.BITAND:
      case Token.BITNOT:
      case Token.BITOR:
      case Token.BITXOR:
      case Token.COMMA:
      case Token.DIV:
      case Token.EQ:
      case Token.GE:
      case Token.GETELEM:
      case Token.GETPROP:
      case Token.GT:
      case Token.INSTANCEOF:
      case Token.LE:
      case Token.LSH:
      case Token.LT:
      case Token.MOD:
      case Token.MUL:
      case Token.NE:
      case Token.NOT:
      case Token.RSH:
      case Token.SHEQ:
      case Token.SHNE:
      case Token.SUB:
      case Token.TYPEOF:
      case Token.VOID:
      case Token.POS:
      case Token.NEG:
      case Token.URSH:
        return true;

      default:
        return false;
    }
  }

  /**
   * Creates an EXPR_RESULT.
   *
   * @param child The expression itself.
   * @return Newly created EXPR node with the child as subexpression.
   */
  public static Node newExpr(Node child) {
    Node expr = new Node(Token.EXPR_RESULT, child)
        .copyInformationFrom(child);
    return expr;
  }

  /**
   * Returns true if the node may create new mutable state, or change existing
   * state.
   *
   * @see <a href="http://www.xkcd.org/326/">XKCD Cartoon</a>
   */
  static boolean mayEffectMutableState(Node n) {
    return mayEffectMutableState(n, null);
  }

  static boolean mayEffectMutableState(Node n, AbstractCompiler compiler) {
    return checkForStateChangeHelper(n, true, compiler);
  }

  /**
   * Returns true if the node which may have side effects when executed.
   */
  static boolean mayHaveSideEffects(Node n) {
    return mayHaveSideEffects(n, null);
  }

  static boolean mayHaveSideEffects(Node n, AbstractCompiler compiler) {
    return checkForStateChangeHelper(n, false, compiler);
  }

  /**
   * Returns true if some node in n's subtree changes application state.
   * If {@code checkForNewObjects} is true, we assume that newly created
   * mutable objects (like object literals) change state. Otherwise, we assume
   * that they have no side effects.
   */
  private static boolean checkForStateChangeHelper(
      Node n, boolean checkForNewObjects, AbstractCompiler compiler) {
    // Rather than id which ops may have side effects, id the ones
    // that we know to be safe
    switch (n.getType()) {
      // other side-effect free statements and expressions
      case Token.AND:
      case Token.BLOCK:
      case Token.EXPR_RESULT:
      case Token.HOOK:
      case Token.IF:
      case Token.IN:
      case Token.LP:
      case Token.NUMBER:
      case Token.OR:
      case Token.THIS:
      case Token.TRUE:
      case Token.FALSE:
      case Token.NULL:
      case Token.STRING:
      case Token.SWITCH:
      case Token.TRY:
      case Token.EMPTY:
        break;

      // Throws are by definition side effects
      case Token.THROW:
        return true;

      case Token.OBJECTLIT:
      case Token.ARRAYLIT:
      case Token.REGEXP:
        if (checkForNewObjects) {
          return true;
        }
        break;

      case Token.VAR:    // empty var statement (no declaration)
      case Token.NAME:   // variable by itself
        if (n.getFirstChild() != null) {
          return true;
        }
        break;

      case Token.FUNCTION:
        // Function expressions don't have side-effects, but function
        // declarations change the namespace. Either way, we don't need to
        // check the children, since they aren't executed at declaration time.
        return checkForNewObjects || !isFunctionExpression(n);

      case Token.NEW:
        if (checkForNewObjects) {
          return true;
        }

        if (!constructorCallHasSideEffects(n)) {
          // loop below will see if the constructor parameters have
          // side-effects
          break;
        }
        return true;

      case Token.CALL:
        // calls to functions that have no side effects have the no
        // side effect property set.
        if (!functionCallHasSideEffects(n, compiler)) {
          // loop below will see if the function parameters have
          // side-effects
          break;
        }
        return true;

      default:
        if (isSimpleOperatorType(n.getType())) {
          break;
        }

        if (isAssignmentOp(n)) {
          Node assignTarget = n.getFirstChild();
          if (isName(assignTarget)) {
            return true;
          }

          // Assignments will have side effects if
          // a) The RHS has side effects, or
          // b) The LHS has side effects, or
          // c) A name on the LHS will exist beyond the life of this statement.
          if (checkForStateChangeHelper(
                  n.getFirstChild(), checkForNewObjects, compiler) ||
              checkForStateChangeHelper(
                  n.getLastChild(), checkForNewObjects, compiler)) {
            return true;
          }

          if (isGet(assignTarget)) {
            // If the object being assigned to is a local object, don't
            // consider this a side-effect as it can't be referenced
            // elsewhere.  Don't do this recursively as the property might
            // be an alias of another object, unlike a literal below.
            Node current = assignTarget.getFirstChild();
            if (evaluatesToLocalValue(current)) {
              return false;
            }

            // A literal value as defined by "isLiteralValue" is guaranteed
            // not to be an alias, or any components which are aliases of
            // other objects.
            // If the root object is a literal don't consider this a
            // side-effect.
            while (isGet(current)) {
              current = current.getFirstChild();
            }

            return !isLiteralValue(current, true);
          } else {
            // TODO(johnlenz): remove this code and make this an exception. This
            // is here only for legacy reasons, the AST is not valid but
            // preserve existing behavior.
            return !isLiteralValue(assignTarget, true);
          }
        }

        return true;
    }

    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      if (checkForStateChangeHelper(c, checkForNewObjects, compiler)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Do calls to this constructor have side effects?
   *
   * @param callNode - construtor call node
   */
  static boolean constructorCallHasSideEffects(Node callNode) {
    return constructorCallHasSideEffects(callNode, null);
  }

  static boolean constructorCallHasSideEffects(
      Node callNode, AbstractCompiler compiler) {
    Preconditions.checkArgument(
        callNode.getType() == Token.NEW,
        "Expected NEW node, got " + Token.name(callNode.getType()));

    if (callNode.isNoSideEffectsCall()) {
      return false;
    }

    Node nameNode = callNode.getFirstChild();
    if (nameNode.getType() == Token.NAME &&
        CONSTRUCTORS_WITHOUT_SIDE_EFFECTS.contains(nameNode.getString())) {
      return false;
    }

    return true;
  }

  // A list of built-in object creation or primitive type cast functions that
  // can also be called as constructors but lack side-effects.
  // TODO(johnlenz): consider adding an extern annotation for this.
  private static final Set<String> BUILTIN_FUNCTIONS_WITHOUT_SIDEEFFECTS =
      ImmutableSet.of(
          "Object", "Array", "String", "Number", "Boolean", "RegExp", "Error");
  private static final Set<String> REGEXP_METHODS =
      ImmutableSet.of("test", "exec");
  private static final Set<String> STRING_REGEXP_METHODS =
      ImmutableSet.of("match", "replace", "search", "split");

  /**
   * Returns true if calls to this function have side effects.
   *
   * @param callNode - function call node
   */
  static boolean functionCallHasSideEffects(
      Node callNode) {
    return functionCallHasSideEffects(callNode, null);
  }

  /**
   * Returns true if calls to this function have side effects.
   *
   * @param callNode The call node to inspected.
   * @param compiler A compiler object to provide program state changing
   *     context information. Can be null.
   */
  static boolean functionCallHasSideEffects(
      Node callNode, @Nullable AbstractCompiler compiler) {
    Preconditions.checkArgument(
        callNode.getType() == Token.CALL,
        "Expected CALL node, got " + Token.name(callNode.getType()));

    if (callNode.isNoSideEffectsCall()) {
      return false;
    }

    Node nameNode = callNode.getFirstChild();

    // Built-in functions with no side effects.
    if (nameNode.getType() == Token.NAME) {
      String name = nameNode.getString();
      if (BUILTIN_FUNCTIONS_WITHOUT_SIDEEFFECTS.contains(name)) {
        return false;
      }
    } else if (nameNode.getType() == Token.GETPROP) {
      if (callNode.isOnlyModifiesThisCall()
          && evaluatesToLocalValue(nameNode.getFirstChild())) {
        return false;
      }

      // Functions in the "Math" namespace have no side effects.
      if (nameNode.getFirstChild().getType() == Token.NAME) {
        String namespaceName = nameNode.getFirstChild().getString();
        if (namespaceName.equals("Math")) {
          return false;
        }
      }

      if (compiler != null && !compiler.hasRegExpGlobalReferences()) {
        if (nameNode.getFirstChild().getType() == Token.REGEXP
            && REGEXP_METHODS.contains(nameNode.getLastChild().getString())) {
          return false;
        } else if (nameNode.getFirstChild().getType() == Token.STRING
            && STRING_REGEXP_METHODS.contains(
                nameNode.getLastChild().getString())) {
          Node param = nameNode.getNext();
          if (param != null &&
              (param.getType() == Token.STRING
                  || param.getType() == Token.REGEXP))
          return false;
        }
      }
    }

    return true;
  }

  /**
   * @return Whether the call has a local result.
   */
  static boolean callHasLocalResult(Node n) {
    Preconditions.checkState(n.getType() == Token.CALL);
    return (n.getSideEffectFlags() & Node.FLAG_LOCAL_RESULTS) > 0;
  }

  /**
   * Returns true if the current node's type implies side effects.
   *
   * This is a non-recursive version of the may have side effects
   * check; used to check wherever the current node's type is one of
   * the reason's why a subtree has side effects.
   */
  static boolean nodeTypeMayHaveSideEffects(Node n) {
    return nodeTypeMayHaveSideEffects(n, null);
  }

  static boolean nodeTypeMayHaveSideEffects(Node n, AbstractCompiler compiler) {
    if (isAssignmentOp(n)) {
      return true;
    }

    switch(n.getType()) {
      case Token.DELPROP:
      case Token.DEC:
      case Token.INC:
      case Token.THROW:
        return true;
      case Token.CALL:
        return NodeUtil.functionCallHasSideEffects(n, compiler);
      case Token.NEW:
        return NodeUtil.constructorCallHasSideEffects(n, compiler);
      case Token.NAME:
        // A variable definition.
        return n.hasChildren();
      default:
        return false;
    }
  }

  /**
   * @return Whether the tree can be affected by side-effects or
   * has side-effects.
   */
  static boolean canBeSideEffected(Node n) {
    Set<String> emptySet = Collections.emptySet();
    return canBeSideEffected(n, emptySet);
  }

  /**
   * @param knownConstants A set of names known to be constant value at
   * node 'n' (such as locals that are last written before n can execute).
   * @return Whether the tree can be affected by side-effects or
   * has side-effects.
   */
  static boolean canBeSideEffected(Node n, Set<String> knownConstants) {
    switch (n.getType()) {
      case Token.CALL:
      case Token.NEW:
        // Function calls or constructor can reference changed values.
        // TODO(johnlenz): Add some mechanism for determining that functions
        // are unaffected by side effects.
        return true;
      case Token.NAME:
        // Non-constant names values may have been changed.
        return !isConstantName(n)
            && !knownConstants.contains(n.getString());

      // Properties on constant NAMEs can still be side-effected.
      case Token.GETPROP:
      case Token.GETELEM:
        return true;

      case Token.FUNCTION:
        // Function expression are not changed by side-effects,
        // and function declarations are not part of expressions.
        Preconditions.checkState(isFunctionExpression(n));
        return false;
    }

    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      if (canBeSideEffected(c, knownConstants)) {
        return true;
      }
    }

    return false;
  }

  /*
   *  0 comma ,
   *  1 assignment = += -= *= /= %= <<= >>= >>>= &= ^= |=
   *  2 conditional ?:
   *  3 logical-or ||
   *  4 logical-and &&
   *  5 bitwise-or |
   *  6 bitwise-xor ^
   *  7 bitwise-and &
   *  8 equality == !=
   *  9 relational < <= > >=
   * 10 bitwise shift << >> >>>
   * 11 addition/subtraction + -
   * 12 multiply/divide * / %
   * 13 negation/increment ! ~ - ++ --
   * 14 call, member () [] .
   */
  static int precedence(int type) {
    switch (type) {
      case Token.COMMA:  return 0;
      case Token.ASSIGN_BITOR:
      case Token.ASSIGN_BITXOR:
      case Token.ASSIGN_BITAND:
      case Token.ASSIGN_LSH:
      case Token.ASSIGN_RSH:
      case Token.ASSIGN_URSH:
      case Token.ASSIGN_ADD:
      case Token.ASSIGN_SUB:
      case Token.ASSIGN_MUL:
      case Token.ASSIGN_DIV:
      case Token.ASSIGN_MOD:
      case Token.ASSIGN: return 1;
      case Token.HOOK:   return 2;  // ?: operator
      case Token.OR:     return 3;
      case Token.AND:    return 4;
      case Token.BITOR:  return 5;
      case Token.BITXOR: return 6;
      case Token.BITAND: return 7;
      case Token.EQ:
      case Token.NE:
      case Token.SHEQ:
      case Token.SHNE:   return 8;
      case Token.LT:
      case Token.GT:
      case Token.LE:
      case Token.GE:
      case Token.INSTANCEOF:
      case Token.IN:     return 9;
      case Token.LSH:
      case Token.RSH:
      case Token.URSH:   return 10;
      case Token.SUB:
      case Token.ADD:    return 11;
      case Token.MUL:
      case Token.MOD:
      case Token.DIV:    return 12;
      case Token.INC:
      case Token.DEC:
      case Token.NEW:
      case Token.DELPROP:
      case Token.TYPEOF:
      case Token.VOID:
      case Token.NOT:
      case Token.BITNOT:
      case Token.POS:
      case Token.NEG:    return 13;

      case Token.ARRAYLIT:
      case Token.CALL:
      case Token.EMPTY:
      case Token.FALSE:
      case Token.FUNCTION:
      case Token.GETELEM:
      case Token.GETPROP:
      case Token.GET_REF:
      case Token.IF:
      case Token.LP:
      case Token.NAME:
      case Token.NULL:
      case Token.NUMBER:
      case Token.OBJECTLIT:
      case Token.REGEXP:
      case Token.RETURN:
      case Token.STRING:
      case Token.THIS:
      case Token.TRUE:
        return 15;

      default: throw new Error("Unknown precedence for " +
                               Node.tokenToName(type) +
                               " (type " + type + ")");
    }
  }

  /**
   * Returns true if the operator is associative.
   * e.g. (a * b) * c = a * (b * c)
   * Note: "+" is not associative because it is also the concatentation
   * for strings. e.g. "a" + (1 + 2) is not "a" + 1 + 2
   */
  static boolean isAssociative(int type) {
    switch (type) {
      case Token.MUL:
      case Token.AND:
      case Token.OR:
      case Token.BITOR:
      case Token.BITAND:
        return true;
      default:
        return false;
    }
  }

  static boolean isAssignmentOp(Node n) {
    switch (n.getType()){
      case Token.ASSIGN:
      case Token.ASSIGN_BITOR:
      case Token.ASSIGN_BITXOR:
      case Token.ASSIGN_BITAND:
      case Token.ASSIGN_LSH:
      case Token.ASSIGN_RSH:
      case Token.ASSIGN_URSH:
      case Token.ASSIGN_ADD:
      case Token.ASSIGN_SUB:
      case Token.ASSIGN_MUL:
      case Token.ASSIGN_DIV:
      case Token.ASSIGN_MOD:
        return true;
    }
    return false;
  }

  static int getOpFromAssignmentOp(Node n) {
    switch (n.getType()){
      case Token.ASSIGN_BITOR:
        return Token.BITOR;
      case Token.ASSIGN_BITXOR:
        return Token.BITXOR;
      case Token.ASSIGN_BITAND:
        return Token.BITAND;
      case Token.ASSIGN_LSH:
        return Token.LSH;
      case Token.ASSIGN_RSH:
        return Token.RSH;
      case Token.ASSIGN_URSH:
        return Token.URSH;
      case Token.ASSIGN_ADD:
        return Token.ADD;
      case Token.ASSIGN_SUB:
        return Token.SUB;
      case Token.ASSIGN_MUL:
        return Token.MUL;
      case Token.ASSIGN_DIV:
        return Token.DIV;
      case Token.ASSIGN_MOD:
        return Token.MOD;
    }
    throw new IllegalArgumentException("Not an assiment op");
  }

  static boolean isExpressionNode(Node n) {
    return n.getType() == Token.EXPR_RESULT;
  }

  /**
   * Determines if the given node contains a function statement or function
   * expression.
   */
  static boolean containsFunction(Node n) {
    return containsType(n, Token.FUNCTION);
  }

  /**
   * Returns true if the shallow scope contains references to 'this' keyword
   */
  static boolean referencesThis(Node n) {
    return containsType(n, Token.THIS, new MatchNotFunction());
  }

  /**
   * Is this a GETPROP or GETELEM node?
   */
  static boolean isGet(Node n) {
    return n.getType() == Token.GETPROP
        || n.getType() == Token.GETELEM;
  }

  /**
   * Is this a GETPROP node?
   */
  static boolean isGetProp(Node n) {
    return n.getType() == Token.GETPROP;
  }

  /**
   * Is this a NAME node?
   */
  static boolean isName(Node n) {
    return n.getType() == Token.NAME;
  }

  /**
   * Is this a NEW node?
   */
  static boolean isNew(Node n) {
    return n.getType() == Token.NEW;
  }

  /**
   * Is this a VAR node?
   */
  static boolean isVar(Node n) {
    return n.getType() == Token.VAR;
  }

  /**
   * Is this node the name of a variable being declared?
   *
   * @param n The node
   * @return True if {@code n} is NAME and {@code parent} is VAR
   */
  static boolean isVarDeclaration(Node n) {
    // There is no need to verify that parent != null because a NAME node
    // always has a parent in a valid parse tree.
    return n.getType() == Token.NAME && n.getParent().getType() == Token.VAR;
  }

  /**
   * For an assignment or variable declaration get the assigned value.
   * @return The value node representing the new value.
   */
  static Node getAssignedValue(Node n) {
    Preconditions.checkState(isName(n));
    Node parent = n.getParent();
    if (isVar(parent)) {
      return n.getFirstChild();
    } else if (isAssign(parent) && parent.getFirstChild() == n) {
      return n.getNext();
    } else {
      return null;
    }
  }

  /**
   * Is this a STRING node?
   */
  static boolean isString(Node n) {
    return n.getType() == Token.STRING;
  }

  /**
   * Is this node an assignment expression statement?
   *
   * @param n The node
   * @return True if {@code n} is EXPR_RESULT and {@code n}'s
   *     first child is ASSIGN
   */
  static boolean isExprAssign(Node n) {
    return n.getType() == Token.EXPR_RESULT
        && n.getFirstChild().getType() == Token.ASSIGN;
  }

  /**
   * Is this an ASSIGN node?
   */
  static boolean isAssign(Node n) {
    return n.getType() == Token.ASSIGN;
  }

  /**
   * Is this node a call expression statement?
   *
   * @param n The node
   * @return True if {@code n} is EXPR_RESULT and {@code n}'s
   *     first child is CALL
   */
  static boolean isExprCall(Node n) {
    return n.getType() == Token.EXPR_RESULT
        && n.getFirstChild().getType() == Token.CALL;
  }

  /**
   * @return Whether the node represents a FOR-IN loop.
   */
  static boolean isForIn(Node n) {
    return n.getType() == Token.FOR
        && n.getChildCount() == 3;
  }

  /**
   * Determines whether the given node is a FOR, DO, or WHILE node.
   */
  static boolean isLoopStructure(Node n) {
    switch (n.getType()) {
      case Token.FOR:
      case Token.DO:
      case Token.WHILE:
        return true;
      default:
        return false;
    }
  }

  /**
   * @param n The node to inspect.
   * @return If the node, is a FOR, WHILE, or DO, it returns the node for
   * the code BLOCK, null otherwise.
   */
  static Node getLoopCodeBlock(Node n) {
    switch (n.getType()) {
      case Token.FOR:
      case Token.WHILE:
        return n.getLastChild();
      case Token.DO:
        return n.getFirstChild();
      default:
        return null;
    }
  }

  /**
   * @return Whether the specified node has a loop parent that
   * is within the current scope.
   */
  static boolean isWithinLoop(Node n) {
    for (Node parent : n.getAncestors()) {
      if (NodeUtil.isLoopStructure(parent)) {
        return true;
      }

      if (NodeUtil.isFunction(parent)) {
        break;
      }
    }
    return false;
  }

  /**
   * Determines whether the given node is a FOR, DO, WHILE, WITH, or IF node.
   */
  static boolean isControlStructure(Node n) {
    switch (n.getType()) {
      case Token.FOR:
      case Token.DO:
      case Token.WHILE:
      case Token.WITH:
      case Token.IF:
      case Token.LABEL:
      case Token.TRY:
      case Token.CATCH:
      case Token.SWITCH:
      case Token.CASE:
      case Token.DEFAULT:
        return true;
      default:
        return false;
    }
  }

  /**
   * Determines whether the given node is code node for FOR, DO,
   * WHILE, WITH, or IF node.
   */
  static boolean isControlStructureCodeBlock(Node parent, Node n) {
    switch (parent.getType()) {
      case Token.FOR:
      case Token.WHILE:
      case Token.LABEL:
      case Token.WITH:
        return parent.getLastChild() == n;
      case Token.DO:
        return parent.getFirstChild() == n;
      case Token.IF:
        return parent.getFirstChild() != n;
      case Token.TRY:
        return parent.getFirstChild() == n || parent.getLastChild() == n;
      case Token.CATCH:
        return parent.getLastChild() == n;
      case Token.SWITCH:
      case Token.CASE:
        return parent.getFirstChild() != n;
      case Token.DEFAULT:
        return true;
      default:
        Preconditions.checkState(isControlStructure(parent));
        return false;
    }
  }

  /**
   * Gets the condition of an ON_TRUE / ON_FALSE CFG edge.
   * @param n a node with an outgoing conditional CFG edge
   * @return the condition node or null if the condition is not obviously a node
   */
  static Node getConditionExpression(Node n) {
    switch (n.getType()) {
      case Token.IF:
      case Token.WHILE:
        return n.getFirstChild();
      case Token.DO:
        return n.getLastChild();
      case Token.FOR:
        switch (n.getChildCount()) {
          case 3:
            return null;
          case 4:
            return n.getFirstChild().getNext();
        }
        throw new IllegalArgumentException("malformed 'for' statement " + n);
      case Token.CASE:
        return null;
    }
    throw new IllegalArgumentException(n + " does not have a condition.");
  }

  /**
   * @return Whether the node is of a type that contain other statements.
   */
  static boolean isStatementBlock(Node n) {
    return n.getType() == Token.SCRIPT || n.getType() == Token.BLOCK;
  }

  /**
   * @return Whether the node is used as a statement.
   */
  static boolean isStatement(Node n) {
    Node parent = n.getParent();
    // It is not possible to determine definitely if a node is a statement
    // or not if it is not part of the AST.  A FUNCTION node can be
    // either part of an expression or a statement.
    Preconditions.checkState(parent != null);
    switch (parent.getType()) {
      case Token.SCRIPT:
      case Token.BLOCK:
      case Token.LABEL:
        return true;
      default:
        return false;
    }
  }

  /** Whether the node is part of a switch statement. */
  static boolean isSwitchCase(Node n) {
    return n.getType() == Token.CASE || n.getType() == Token.DEFAULT;
  }

  /**
   * @return Whether the name is a reference to a variable, function or
   *       function parameter (not a label or a empty function expression name).
   */
  static boolean isReferenceName(Node n) {
    return isName(n) && !n.getString().isEmpty();
  }

  /** @return Whether the node is a label name. */
  static boolean isLabelName(Node n) {
    return (n != null && n.getType() == Token.LABEL_NAME);
  }

  /** Whether the child node is the FINALLY block of a try. */
  static boolean isTryFinallyNode(Node parent, Node child) {
    return parent.getType() == Token.TRY && parent.getChildCount() == 3
        && child == parent.getLastChild();
  }

  /** Safely remove children while maintaining a valid node structure. */
  static void removeChild(Node parent, Node node) {
    // Node parent = node.getParent();
    if (isStatementBlock(parent)
        || isSwitchCase(node)
        || isTryFinallyNode(parent, node)) {
      // A statement in a block can simply be removed.
      parent.removeChild(node);
    } else if (parent.getType() == Token.VAR) {
      if (parent.hasMoreThanOneChild()) {
        parent.removeChild(node);
      } else {
        // Remove the node from the parent, so it can be reused.
        parent.removeChild(node);
        // This would leave an empty VAR, remove the VAR itself.
        removeChild(parent.getParent(), parent);
      }
    } else if (node.getType() == Token.BLOCK) {
      // Simply empty the block.  This maintains source location and
      // "synthetic"-ness.
      node.detachChildren();
    } else if (parent.getType() == Token.LABEL
        && node == parent.getLastChild()) {
      // Remove the node from the parent, so it can be reused.
      parent.removeChild(node);
      // A LABEL without children can not be referred to, remove it.
      removeChild(parent.getParent(), parent);
    } else if (parent.getType() == Token.FOR
        && parent.getChildCount() == 4) {
      // Only Token.FOR can have an Token.EMPTY other control structure
      // need something for the condition. Others need to be replaced
      // or the structure removed.
      parent.replaceChild(node, new Node(Token.EMPTY));
    } else {
      throw new IllegalStateException("Invalid attempt to remove node: " +
          node.toString() + " of "+ parent.toString());
    }
  }

  /**
   * Merge a block with its parent block.
   * @return Whether the block was removed.
   */
  static boolean tryMergeBlock(Node block) {
    Preconditions.checkState(block.getType() == Token.BLOCK);
    Node parent = block.getParent();
    // Try to remove the block if its parent is a block/script or if its
    // parent is label and it has exactly one child.
    if (isStatementBlock(parent)) {
      Node previous = block;
      while (block.hasChildren()) {
        Node child = block.removeFirstChild();
        parent.addChildAfter(child, previous);
        previous = child;
      }
      parent.removeChild(block);
      return true;
    } else {
      return false;
    }
  }

  /**
   * Is this a CALL node?
   */
  static boolean isCall(Node n) {
    return n.getType() == Token.CALL;
  }

  /**
   * Is this a FUNCTION node?
   */
  static boolean isFunction(Node n) {
    return n.getType() == Token.FUNCTION;
  }

  /**
   * Return a BLOCK node for the given FUNCTION node.
   */
  static Node getFunctionBody(Node fn) {
    Preconditions.checkArgument(isFunction(fn));
    return fn.getLastChild();
  }

  /**
   * Is this a THIS node?
   */
  static boolean isThis(Node node) {
    return node.getType() == Token.THIS;
  }

  /**
   * Is this node or any of its children a CALL?
   */
  static boolean containsCall(Node n) {
    return containsType(n, Token.CALL);
  }

  /**
   * Is this node a function declaration? A function declaration is a function
   * that has a name that is added to the current scope (i.e. a function that
   * is not part of a expression; see {@link #isFunctionExpression}).
   */
  static boolean isFunctionDeclaration(Node n) {
    return n.getType() == Token.FUNCTION && isStatement(n);
  }

  /**
   * Is this node a hoisted function declaration? A function declaration in the
   * scope root is hoisted to the top of the scope.
   * See {@link #isFunctionDeclaration}).
   */
  static boolean isHoistedFunctionDeclaration(Node n) {
    return isFunctionDeclaration(n)
        && (n.getParent().getType() == Token.SCRIPT
            || n.getParent().getParent().getType() == Token.FUNCTION);
  }

  /**
   * Is a FUNCTION node an function expression? An function expression is one
   * that has either no name or a name that is not added to the current scope.
   *
   * <p>Some examples of function expressions:
   * <pre>
   * (function () {})
   * (function f() {})()
   * [ function f() {} ]
   * var f = function f() {};
   * for (function f() {};;) {}
   * </pre>
   *
   * <p>Some examples of functions that are <em>not</em> expressions:
   * <pre>
   * function f() {}
   * if (x); else function f() {}
   * for (;;) { function f() {} }
   * </pre>
   *
   * @param n A node
   * @return Whether n is an function used within an expression.
   */
  static boolean isFunctionExpression(Node n) {
    return n.getType() == Token.FUNCTION && !isStatement(n);
  }

  /**
   * Determines if a node is a function expression that has an empty body.
   *
   * @param node a node
   * @return whether the given node is a function expression that is empty
   */
  static boolean isEmptyFunctionExpression(Node node) {
    return isFunctionExpression(node) && isEmptyBlock(node.getLastChild());
  }

  /**
   * Determines if a function takes a variable number of arguments by
   * looking for references to the "arguments" var_args object.
   */
  static boolean isVarArgsFunction(Node function) {
    Preconditions.checkArgument(isFunction(function));
    return isNameReferenced(
        function.getLastChild(),
        "arguments",
        new MatchNotFunction());
  }

  /**
   * @return Whether node is a call to methodName.
   *    a.f(...)
   *    a['f'](...)
   */
  static boolean isObjectCallMethod(Node callNode, String methodName) {
    if (callNode.getType() == Token.CALL) {
      Node functionIndentifyingExpression = callNode.getFirstChild();
      if (isGet(functionIndentifyingExpression)) {
        Node last = functionIndentifyingExpression.getLastChild();
        if (last != null && last.getType() == Token.STRING) {
          String propName = last.getString();
          return (propName.equals(methodName));
        }
      }
    }
    return false;
  }


  /**
   * @return Whether the callNode represents an expression in the form of:
   *    x.call(...)
   *    x['call'](...)
   */
  static boolean isFunctionObjectCall(Node callNode) {
    return isObjectCallMethod(callNode, "call");
  }

  /**
   * @return Whether the callNode represents an expression in the form of:
   *    x.apply(...)
   *    x['apply'](...)
   */
  static boolean isFunctionObjectApply(Node callNode) {
    return isObjectCallMethod(callNode, "apply");
  }

  /**
   * @return Whether the callNode represents an expression in the form of:
   *    x.call(...)
   *    x['call'](...)
   * where x is a NAME node.
   */
  static boolean isSimpleFunctionObjectCall(Node callNode) {
    if (isFunctionObjectCall(callNode)) {
      if (callNode.getFirstChild().getFirstChild().getType() == Token.NAME) {
        return true;
      }
    }

    return false;
  }

  /**
   * Determines whether this node is strictly on the left hand side of an assign
   * or var initialization. Notably, this does not include all L-values, only
   * statements where the node is used only as an L-value.
   *
   * @param n The node
   * @param parent Parent of the node
   * @return True if n is the left hand of an assign
   */
  static boolean isLhs(Node n, Node parent) {
    return (parent.getType() == Token.ASSIGN && parent.getFirstChild() == n) ||
           parent.getType() == Token.VAR;
  }

  /**
   * Determines whether a node represents an object literal key
   * (e.g. key1 in {key1: value1, key2: value2}).
   *
   * @param node A node
   * @param parent The node's parent
   */
  static boolean isObjectLitKey(Node node, Node parent) {
    if (node.getType() == Token.STRING && parent.getType() == Token.OBJECTLIT) {
      int index = 0;
      for (Node current = parent.getFirstChild();
           current != null;
           current = current.getNext()) {
        if (current == node) {
          return index % 2 == 0;
        }
        index++;
      }
    }
    return false;
  }

  /**
   * Converts an operator's token value (see {@link Token}) to a string
   * representation.
   *
   * @param operator the operator's token value to convert
   * @return the string representation or {@code null} if the token value is
   * not an operator
   */
  static String opToStr(int operator) {
    switch (operator) {
      case Token.BITOR: return "|";
      case Token.OR: return "||";
      case Token.BITXOR: return "^";
      case Token.AND: return "&&";
      case Token.BITAND: return "&";
      case Token.SHEQ: return "===";
      case Token.EQ: return "==";
      case Token.NOT: return "!";
      case Token.NE: return "!=";
      case Token.SHNE: return "!==";
      case Token.LSH: return "<<";
      case Token.IN: return "in";
      case Token.LE: return "<=";
      case Token.LT: return "<";
      case Token.URSH: return ">>>";
      case Token.RSH: return ">>";
      case Token.GE: return ">=";
      case Token.GT: return ">";
      case Token.MUL: return "*";
      case Token.DIV: return "/";
      case Token.MOD: return "%";
      case Token.BITNOT: return "~";
      case Token.ADD: return "+";
      case Token.SUB: return "-";
      case Token.POS: return "+";
      case Token.NEG: return "-";
      case Token.ASSIGN: return "=";
      case Token.ASSIGN_BITOR: return "|=";
      case Token.ASSIGN_BITXOR: return "^=";
      case Token.ASSIGN_BITAND: return "&=";
      case Token.ASSIGN_LSH: return "<<=";
      case Token.ASSIGN_RSH: return ">>=";
      case Token.ASSIGN_URSH: return ">>>=";
      case Token.ASSIGN_ADD: return "+=";
      case Token.ASSIGN_SUB: return "-=";
      case Token.ASSIGN_MUL: return "*=";
      case Token.ASSIGN_DIV: return "/=";
      case Token.ASSIGN_MOD: return "%=";
      case Token.VOID: return "void";
      case Token.TYPEOF: return "typeof";
      case Token.INSTANCEOF: return "instanceof";
      default: return null;
    }
  }

  /**
   * Converts an operator's token value (see {@link Token}) to a string
   * representation or fails.
   *
   * @param operator the operator's token value to convert
   * @return the string representation
   * @throws Error if the token value is not an operator
   */
  static String opToStrNoFail(int operator) {
    String res = opToStr(operator);
    if (res == null) {
      throw new Error("Unknown op " + operator + ": " +
                      Token.name(operator));
    }
    return res;
  }

  /**
   * @return true if n or any of its children are of the specified type
   */
  static boolean containsType(Node node,
                              int type,
                              Predicate<Node> traverseChildrenPred) {
    return has(node, new MatchNodeType(type), traverseChildrenPred);
  }

  /**
   * @return true if n or any of its children are of the specified type
   */
  static boolean containsType(Node node, int type) {
    return containsType(node, type, Predicates.<Node>alwaysTrue());
  }


  /**
   * Given a node tree, finds all the VAR declarations in that tree that are
   * not in an inner scope. Then adds a new VAR node at the top of the current
   * scope that redeclares them, if necessary.
   */
  static void redeclareVarsInsideBranch(Node branch) {
    Collection<Node> vars = getVarsDeclaredInBranch(branch);
    if (vars.isEmpty()) {
      return;
    }

    Node parent = getAddingRoot(branch);
    for (Node nameNode : vars) {
      Node var = new Node(
          Token.VAR,
          Node.newString(Token.NAME, nameNode.getString())
              .copyInformationFrom(nameNode))
          .copyInformationFrom(nameNode);
      copyNameAnnotations(nameNode, var.getFirstChild());
      parent.addChildToFront(var);
    }
  }

  /**
   * Copy any annotations that follow a named value.
   * @param source
   * @param destination
   */
  static void copyNameAnnotations(Node source, Node destination) {
    if (source.getBooleanProp(Node.IS_CONSTANT_NAME)) {
      destination.putBooleanProp(Node.IS_CONSTANT_NAME, true);
    }
  }

  /**
   * Gets a Node at the top of the current scope where we can add new var
   * declarations as children.
   */
  private static Node getAddingRoot(Node n) {
    Node addingRoot = null;
    Node ancestor = n;
    while (null != (ancestor = ancestor.getParent())) {
      int type = ancestor.getType();
      if (type == Token.SCRIPT) {
        addingRoot = ancestor;
        break;
      } else if (type == Token.FUNCTION) {
        addingRoot = ancestor.getLastChild();
        break;
      }
    }

    // make sure that the adding root looks ok
    Preconditions.checkState(addingRoot.getType() == Token.BLOCK ||
        addingRoot.getType() == Token.SCRIPT);
    Preconditions.checkState(addingRoot.getFirstChild() == null ||
        addingRoot.getFirstChild().getType() != Token.SCRIPT);
    return addingRoot;
  }

  /** Creates function name(params_0, ..., params_n) { body }. */
  public static Node newFunctionNode(String name, List<Node> params,
      Node body, int lineno, int charno) {
    Node parameterParen = new Node(Token.LP, lineno, charno);
    for (Node param : params) {
      parameterParen.addChildToBack(param);
    }
    Node function = new Node(Token.FUNCTION, lineno, charno);
    function.addChildrenToBack(
        Node.newString(Token.NAME, name, lineno, charno));
    function.addChildToBack(parameterParen);
    function.addChildToBack(body);
    return function;
  }

  /**
   * Creates a node representing a qualified name.
   *
   * @param name A qualified name (e.g. "foo" or "foo.bar.baz")
   * @param lineno The source line offset.
   * @param charno The source character offset from start of the line.
   * @return A NAME or GETPROP node
   */
  public static Node newQualifiedNameNode(String name, int lineno, int charno) {
    int endPos = name.indexOf('.');
    if (endPos == -1) {
      return Node.newString(Token.NAME, name, lineno, charno);
    }
    Node node = Node.newString(Token.NAME, name.substring(0, endPos),
                               lineno, charno);
    int startPos;
    do {
      startPos = endPos + 1;
      endPos = name.indexOf('.', startPos);
      String part = (endPos == -1
                     ? name.substring(startPos)
                     : name.substring(startPos, endPos));
      node = new Node(Token.GETPROP, node,
                      Node.newString(Token.STRING, part, lineno, charno),
                      lineno, charno);
    } while (endPos != -1);

    return node;
  }

  /**
   * Creates a node representing a qualified name, copying over the source
   * location information from the basis node and assigning the given original
   * name to the node.
   *
   * @param name A qualified name (e.g. "foo" or "foo.bar.baz")
   * @param basisNode The node that represents the name as currently found in
   *     the AST.
   * @param originalName The original name of the item being represented by the
   *     NAME node. Used for debugging information.
   *
   * @return A NAME or GETPROP node
   */
  static Node newQualifiedNameNode(String name, Node basisNode,
      String originalName) {
    Node node = newQualifiedNameNode(name, -1, -1);
    setDebugInformation(node, basisNode, originalName);
    return node;
  }

  /**
   * Gets the root node of a qualified name. Must be either NAME or THIS.
   */
  static Node getRootOfQualifiedName(Node qName) {
    for (Node current = qName; true;
         current = current.getFirstChild()) {
      int type = current.getType();
      if (type == Token.NAME || type == Token.THIS) {
        return current;
      }
      Preconditions.checkState(type == Token.GETPROP);
    }
  }

  /**
   * Sets the debug information (source file info and orignal name)
   * on the given node.
   *
   * @param node The node on which to set the debug information.
   * @param basisNode The basis node from which to copy the source file info.
   * @param originalName The original name of the node.
   */
  static void setDebugInformation(Node node, Node basisNode,
                                  String originalName) {
    node.copyInformationFromForTree(basisNode);
    node.putProp(Node.ORIGINALNAME_PROP, originalName);
  }

  /**
   * Creates a new node representing an *existing* name, copying over the source
   * location information from the basis node.
   *
   * @param name The name for the new NAME node.
   * @param basisNode The node that represents the name as currently found in
   *     the AST.
   *
   * @return The node created.
   */
  static Node newName(String name, Node basisNode) {
    Node nameNode = Node.newString(Token.NAME, name);
    nameNode.copyInformationFrom(basisNode);
    return nameNode;
  }

  /**
   * Creates a new node representing an *existing* name, copying over the source
   * location information from the basis node and assigning the given original
   * name to the node.
   *
   * @param name The name for the new NAME node.
   * @param basisNode The node that represents the name as currently found in
   *     the AST.
   * @param originalName The original name of the item being represented by the
   *     NAME node. Used for debugging information.
   *
   * @return The node created.
   */
  static Node newName(String name, Node basisNode, String originalName) {
    Node nameNode = newName(name, basisNode);
    nameNode.putProp(Node.ORIGINALNAME_PROP, originalName);
    return nameNode;
  }

  /** Test if all characters in the string are in the Basic Latin (aka ASCII)
   * character set - that they have UTF-16 values equal to or below 0x7f.
   * This check can find which identifiers with Unicode characters need to be
   * escaped in order to allow resulting files to be processed by non-Unicode
   * aware UNIX tools and editors.
   * *
   * See http://en.wikipedia.org/wiki/Latin_characters_in_Unicode
   * for more on Basic Latin.
   *
   * @param s The string to be checked for ASCII-goodness.
   *
   * @return True if all characters in the string are in Basic Latin set.
   */

  static boolean isLatin(String s) {
    char LARGEST_BASIC_LATIN = 0x7f;
    int len = s.length();
    for (int index = 0; index < len; index++) {
      char c = s.charAt(index);
      if (c > LARGEST_BASIC_LATIN) {
        return false;
      }
    }
    return true;
  }

  /**
   * Determines whether the given name can appear on the right side of
   * the dot operator. Many properties (like reserved words) cannot.
   */
  static boolean isValidPropertyName(String name) {
    return TokenStream.isJSIdentifier(name) &&
        !TokenStream.isKeyword(name) &&
        // no Unicode escaped characters - some browsers are less tolerant
        // of Unicode characters that might be valid according to the
        // language spec.
        // Note that by this point, unicode escapes have been converted
        // to UTF-16 characters, so we're only searching for character
        // values, not escapes.
        isLatin(name);
  }

  private static class VarCollector implements Visitor {
    final Map<String, Node> vars = Maps.newLinkedHashMap();

    public void visit(Node n) {
      if (n.getType() == Token.NAME) {
        Node parent = n.getParent();
        if (parent != null && parent.getType() == Token.VAR) {
          String name = n.getString();
          if (!vars.containsKey(name)) {
            vars.put(name, n);
          }
        }
      }
    }
  }

  /**
   * Retrieves vars declared in the current node tree, excluding descent scopes.
   */
  public static Collection<Node> getVarsDeclaredInBranch(Node root) {
    VarCollector collector = new VarCollector();
    visitPreOrder(
        root,
        collector,
        new MatchNotFunction());
    return collector.vars.values();
  }

  /**
   * @return {@code true} if the node an assignment to a prototype property of
   *     some constructor.
   */
  static boolean isPrototypePropertyDeclaration(Node n) {
    if (!isExprAssign(n)) {
      return false;
    }
    return isPrototypeProperty(n.getFirstChild().getFirstChild());
  }

  static boolean isPrototypeProperty(Node n) {
    String lhsString = n.getQualifiedName();
    if (lhsString == null) {
      return false;
    }
    int prototypeIdx = lhsString.indexOf(".prototype.");
    return prototypeIdx != -1;
  }

  /**
   * @return The class name part of a qualified prototype name.
   */
  static Node getPrototypeClassName(Node qName) {
    Node cur = qName;
    while (isGetProp(cur)) {
      if (cur.getLastChild().getString().equals("prototype")) {
        return cur.getFirstChild();
      } else {
        cur = cur.getFirstChild();
      }
    }
    return null;
  }

  /**
   * @return The string property name part of a qualified prototype name.
   */
  static String getPrototypePropertyName(Node qName) {
    String qNameStr = qName.getQualifiedName();
    int prototypeIdx = qNameStr.lastIndexOf(".prototype.");
    int memberIndex = prototypeIdx + ".prototype".length() + 1;
    return qNameStr.substring(memberIndex);
  }

  /**
   * Create a node for an empty result expression:
   *   "void 0"
   */
  static Node newUndefinedNode(Node srcReferenceNode) {
    // TODO(johnlenz): Why this instead of the more common "undefined"?
    Node node = new Node(Token.VOID, Node.newNumber(0));
    if (srcReferenceNode != null) {
        node.copyInformationFromForTree(srcReferenceNode);
    }
    return node;
  }

  /**
   * Create a VAR node containing the given name and initial value expression.
   */
  static Node newVarNode(String name, Node value) {
    Node nodeName = Node.newString(Token.NAME, name);
    if (value != null) {
      Preconditions.checkState(value.getNext() == null);
      nodeName.addChildToBack(value);
      nodeName.copyInformationFrom(value);
    }
    Node var = new Node(Token.VAR, nodeName)
        .copyInformationFrom(nodeName);

    return var;
  }

  /**
   * A predicate for matching name nodes with the specified node.
   */
  private static class MatchNameNode implements Predicate<Node>{
    final String name;

    MatchNameNode(String name){
      this.name = name;
    }

    public boolean apply(Node n) {
      return n.getType() == Token.NAME
          && n.getString().equals(name);
    }
  }

  /**
   * A predicate for matching nodes with the specified type.
   */
  static class MatchNodeType implements Predicate<Node>{
    final int type;

    MatchNodeType(int type){
      this.type = type;
    }

    public boolean apply(Node n) {
      return n.getType() == type;
    }
  }


  /**
   * A predicate for matching var or function declarations.
   */
  static class MatchDeclaration implements Predicate<Node> {
    public boolean apply(Node n) {
      return isFunctionDeclaration(n) || n.getType() == Token.VAR;
    }
  }

  /**
   * A predicate for matching anything except function nodes.
   */
  static class MatchNotFunction implements Predicate<Node>{
    public boolean apply(Node n) {
      return !isFunction(n);
    }
  }

  /**
   * A predicate for matching statements without exiting the current scope.
   */
  static class MatchShallowStatement implements Predicate<Node>{
    public boolean apply(Node n) {
      Node parent = n.getParent();
      return n.getType() == Token.BLOCK
          || (!isFunction(n) && (parent == null
              || isControlStructure(parent)
              || isStatementBlock(parent)));
    }
  }

  /**
   * Finds the number of times a type is referenced within the node tree.
   */
  static int getNodeTypeReferenceCount(
      Node node, int type, Predicate<Node> traverseChildrenPred) {
    return getCount(node, new MatchNodeType(type), traverseChildrenPred);
  }

  /**
   * Whether a simple name is referenced within the node tree.
   */
  static boolean isNameReferenced(Node node,
                                  String name,
                                  Predicate<Node> traverseChildrenPred) {
    return has(node, new MatchNameNode(name), traverseChildrenPred);
  }

  /**
   * Whether a simple name is referenced within the node tree.
   */
  static boolean isNameReferenced(Node node, String name) {
    return isNameReferenced(node, name, Predicates.<Node>alwaysTrue());
  }

  /**
   * Finds the number of times a simple name is referenced within the node tree.
   */
  static int getNameReferenceCount(Node node, String name) {
    return getCount(
        node, new MatchNameNode(name), Predicates.<Node>alwaysTrue());
  }

  /**
   * @return Whether the predicate is true for the node or any of its children.
   */
  static boolean has(Node node,
                     Predicate<Node> pred,
                     Predicate<Node> traverseChildrenPred) {
    if (pred.apply(node)) {
      return true;
    }

    if (!traverseChildrenPred.apply(node)) {
      return false;
    }

    for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
      if (has(c, pred, traverseChildrenPred)) {
        return true;
      }
    }

    return false;
  }

  /**
   * @return The number of times the the predicate is true for the node
   * or any of its children.
   */
  static int getCount(
      Node n, Predicate<Node> pred, Predicate<Node> traverseChildrenPred) {
    int total = 0;

    if (pred.apply(n)) {
      total++;
    }

    if (traverseChildrenPred.apply(n)) {
      for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
        total += getCount(c, pred, traverseChildrenPred);
      }
    }

    return total;
  }

  /**
   * Interface for use with the visit method.
   * @see #visit
   */
  static interface Visitor {
    void visit(Node node);
  }

  /**
   * A pre-order traversal, calling Vistor.visit for each child matching
   * the predicate.
   */
  static void visitPreOrder(Node node,
                     Visitor vistor,
                     Predicate<Node> traverseChildrenPred) {
    vistor.visit(node);

    if (traverseChildrenPred.apply(node)) {
      for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
        visitPreOrder(c, vistor, traverseChildrenPred);
      }
    }
  }

  /**
   * A post-order traversal, calling Vistor.visit for each child matching
   * the predicate.
   */
  static void visitPostOrder(Node node,
                     Visitor vistor,
                     Predicate<Node> traverseChildrenPred) {
    if (traverseChildrenPred.apply(node)) {
      for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
        visitPostOrder(c, vistor, traverseChildrenPred);
      }
    }

    vistor.visit(node);
  }

  /**
   * @return Whether a TRY node has a finally block.
   */
  static boolean hasFinally(Node n) {
    Preconditions.checkArgument(n.getType() == Token.TRY);
    return n.getChildCount() == 3;
  }

  /**
   * @return The BLOCK node containing the CATCH node (if any)
   * of a TRY.
   */
  static Node getCatchBlock(Node n) {
    Preconditions.checkArgument(n.getType() == Token.TRY);
    return n.getFirstChild().getNext();
  }

  /**
   * @return Whether BLOCK (from a TRY node) contains a CATCH.
   * @see NodeUtil#getCatchBlock
   */
  static boolean hasCatchHandler(Node n) {
    Preconditions.checkArgument(n.getType() == Token.BLOCK);
    return n.hasChildren() && n.getFirstChild().getType() == Token.CATCH;
  }

  /**
    * @param fnNode The function.
    * @return The Node containing the Function parameters.
    */
  static Node getFnParameters(Node fnNode) {
   // Function NODE: [ FUNCTION -> NAME, LP -> ARG1, ARG2, ... ]
   Preconditions.checkArgument(fnNode.getType() == Token.FUNCTION);
   return fnNode.getFirstChild().getNext();
  }

  /**
   * Returns true if a name node represents a constant variable.
   *
   * <p>Determining whether a variable is constant has three steps:
   * <ol>
   * <li>In CodingConventionAnnotator, any name that matches the
   *     {@link CodingConvention#isConstant(String)} is annotated with an
   *     IS_CONSTANT_NAME property.
   * <li>The normalize pass renames any variable with the IS_CONSTANT_NAME
   *     annotation and that is initialized to a constant value with
   *     a variable name inlucding $$constant.
   * <li>Return true here if the variable includes $$constant in its name.
   * </ol>
   *
   * @param node A NAME or STRING node
   * @return True if the variable is constant
   */
  static boolean isConstantName(Node node) {
    return node.getBooleanProp(Node.IS_CONSTANT_NAME);
  }

  /** Whether the given name is constant by coding convention. */
  static boolean isConstantByConvention(
      CodingConvention convention, Node node, Node parent) {
    String name = node.getString();
    if (parent.getType() == Token.GETPROP &&
        node == parent.getLastChild()) {
      return convention.isConstantKey(name);
    } else if (isObjectLitKey(node, parent)) {
      return convention.isConstantKey(name);
    } else {
      return convention.isConstant(name);
    }
  }

  /**
   * @param nameNode A name node
   * @return The JSDocInfo for the name node
   */
  static JSDocInfo getInfoForNameNode(Node nameNode) {
    JSDocInfo info = null;
    Node parent = null;
    if (nameNode != null) {
      info = nameNode.getJSDocInfo();
      parent = nameNode.getParent();
    }

    if (info == null && parent != null &&
        ((parent.getType() == Token.VAR && parent.hasOneChild()) ||
          parent.getType() == Token.FUNCTION)) {
      info = parent.getJSDocInfo();
    }
    return info;
  }

  /**
   * Get the JSDocInfo for a function.
   */
  static JSDocInfo getFunctionInfo(Node n) {
    Preconditions.checkState(n.getType() == Token.FUNCTION);
    JSDocInfo fnInfo = n.getJSDocInfo();
    if (fnInfo == null && NodeUtil.isFunctionExpression(n)) {
      // Look for the info on other nodes.
      Node parent = n.getParent();
      if (parent.getType() == Token.ASSIGN) {
        // on ASSIGNs
        fnInfo = parent.getJSDocInfo();
      } else if (parent.getType() == Token.NAME) {
        // on var NAME = function() { ... };
        fnInfo = parent.getParent().getJSDocInfo();
      }
    }
    return fnInfo;
  }

  /**
   * @param n The node.
   * @return The source name property on the node or its ancestors.
   */
  static String getSourceName(Node n) {
    String sourceName = null;
    while (sourceName == null && n != null) {
      sourceName = (String) n.getProp(Node.SOURCENAME_PROP);
      n = n.getParent();
    }
    return sourceName;
  }

  /**
   * A new CALL node with the "FREE_CALL" set based on call target.
   */
  static Node newCallNode(Node callTarget, Node... parameters) {
    boolean isFreeCall = isName(callTarget);
    Node call = new Node(Token.CALL, callTarget);
    call.putBooleanProp(Node.FREE_CALL, isFreeCall);
    for (Node parameter : parameters) {
      call.addChildToBack(parameter);
    }
    return call;
  }

  /**
   * @return Whether the node is known to be a value that is not referenced
   * elsewhere.
   */
  static boolean evaluatesToLocalValue(Node value) {
    return evaluatesToLocalValue(value, Predicates.<Node>alwaysFalse());
  }

  /**
   * @param locals A predicate to apply to unknown local values.
   * @return Whether the node is known to be a value that is not a reference
   *     outside the expression scope.
   */
  static boolean evaluatesToLocalValue(Node value, Predicate<Node> locals) {
    switch (value.getType()) {
      case Token.ASSIGN:
        // A result that is aliased by a non-local name, is the effectively the
        // same as returning a non-local name, but this doesn't matter if the
        // value is immutable.
        return NodeUtil.isImmutableValue(value.getLastChild())
            || (locals.apply(value)
                && evaluatesToLocalValue(value.getLastChild(), locals));
      case Token.COMMA:
        return evaluatesToLocalValue(value.getLastChild(), locals);
      case Token.AND:
      case Token.OR:
        return evaluatesToLocalValue(value.getFirstChild(), locals)
           && evaluatesToLocalValue(value.getLastChild(), locals);
      case Token.HOOK:
        return evaluatesToLocalValue(value.getFirstChild().getNext(), locals)
           && evaluatesToLocalValue(value.getLastChild(), locals);
      case Token.INC:
      case Token.DEC:
        if (value.getBooleanProp(Node.INCRDECR_PROP)) {
          return evaluatesToLocalValue(value.getFirstChild(), locals);
        } else {
          return true;
        }
      case Token.THIS:
        return locals.apply(value);
      case Token.NAME:
        return isImmutableValue(value) || locals.apply(value);
      case Token.GETELEM:
      case Token.GETPROP:
        // There is no information about the locality of object properties.
        return locals.apply(value);
      case Token.CALL:
        return callHasLocalResult(value) || locals.apply(value);
      case Token.NEW:
        return true;
      case Token.FUNCTION:
      case Token.REGEXP:
      case Token.ARRAYLIT:
      case Token.OBJECTLIT:
        // Literals objects with non-literal children are allowed.
        return true;
      case Token.IN:
        // TODO(johnlenz): should IN operator be included in #isSimpleOperator?
        return true;
      default:
        // Other op force a local value:
        //  x = '' + g (x is now an local string)
        //  x -= g (x is now an local number)
        if (isAssignmentOp(value)
            || isSimpleOperator(value)
            || isImmutableValue(value)) {
          return true;
        }

        throw new IllegalStateException(
            "Unexpected expression node" + value +
            "\n parent:" + value.getParent());
    }
  }
}
