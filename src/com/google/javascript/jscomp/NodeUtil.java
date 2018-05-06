/*
 * Copyright 2004 The Closure Compiler Authors.
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

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TokenStream;
import com.google.javascript.rhino.TokenUtil;
import com.google.javascript.rhino.TypeI;
import com.google.javascript.rhino.dtoa.DToA;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.TernaryValue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/**
 * NodeUtil contains generally useful AST utilities.
 *
 * @author nicksantos@google.com (Nick Santos)
 * @author johnlenz@google.com (John Lenz)
 */
public final class NodeUtil {

  public static final String EXTERN_OBJECT_PROPERTY_STRING =
      "JSCompiler_ObjectPropertyString";
  static final long MAX_POSITIVE_INTEGER_NUMBER = 1L << 53;

  static final String JSC_PROPERTY_NAME_FN = "JSCompiler_renameProperty";

  static final char LARGEST_BASIC_LATIN = 0x7f;

  /** the set of builtin constructors that don't have side effects. */
  private static final ImmutableSet<String> CONSTRUCTORS_WITHOUT_SIDE_EFFECTS =
      ImmutableSet.of("Array", "Date", "Error", "Object", "RegExp", "XMLHttpRequest");

  // Utility class; do not instantiate.
  private NodeUtil() {}

  static boolean isImpureTrue(Node n) {
    return getImpureBooleanValue(n) == TernaryValue.TRUE;
  }

  /**
   * Gets the boolean value of a node that represents a expression. This method
   * effectively emulates the <code>Boolean()</code> JavaScript cast function.
   * Note: unlike getPureBooleanValue this function does not return UNKNOWN
   * for expressions with side-effects.
   */
  static TernaryValue getImpureBooleanValue(Node n) {
    switch (n.getToken()) {
      case ASSIGN:
      case COMMA:
        // For ASSIGN and COMMA the value is the value of the RHS.
        return getImpureBooleanValue(n.getLastChild());
      case NOT:
        TernaryValue value = getImpureBooleanValue(n.getLastChild());
        return value.not();
      case AND: {
        TernaryValue lhs = getImpureBooleanValue(n.getFirstChild());
        TernaryValue rhs = getImpureBooleanValue(n.getLastChild());
        return lhs.and(rhs);
      }
      case OR:  {
        TernaryValue lhs = getImpureBooleanValue(n.getFirstChild());
        TernaryValue rhs = getImpureBooleanValue(n.getLastChild());
        return lhs.or(rhs);
      }
      case HOOK:  {
        TernaryValue trueValue = getImpureBooleanValue(
            n.getSecondChild());
        TernaryValue falseValue = getImpureBooleanValue(n.getLastChild());
        if (trueValue.equals(falseValue)) {
          return trueValue;
        } else {
          return TernaryValue.UNKNOWN;
        }
      }
      case NEW:
      case ARRAYLIT:
      case OBJECTLIT:
        // ignoring side-effects
        return TernaryValue.TRUE;

      case VOID:
        return TernaryValue.FALSE;

      default:
        return getPureBooleanValue(n);
    }
  }

  /**
   * Gets the boolean value of a node that represents a literal. This method
   * effectively emulates the <code>Boolean()</code> JavaScript cast function
   * except it return UNKNOWN for known values with side-effects, use
   * getImpureBooleanValue if you don't care about side-effects.
   */
  static TernaryValue getPureBooleanValue(Node n) {
    switch (n.getToken()) {
      case TEMPLATELIT:
        if (n.hasOneChild()) {
          return TernaryValue.forBoolean(!n.getFirstChild().getString().isEmpty());
        }
        break;

      case STRING:
        return TernaryValue.forBoolean(n.getString().length() > 0);

      case NUMBER:
        return TernaryValue.forBoolean(n.getDouble() != 0);

      case NOT:
        return getPureBooleanValue(n.getLastChild()).not();

      case NULL:
      case FALSE:
        return TernaryValue.FALSE;

      case VOID:
        if (!mayHaveSideEffects(n.getFirstChild())) {
          return TernaryValue.FALSE;
        }
        break;

      case NAME:
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

      case TRUE:
      case REGEXP:
        return TernaryValue.TRUE;

      case FUNCTION:
      case CLASS:
      case NEW:
      case ARRAYLIT:
      case OBJECTLIT:
        if (!mayHaveSideEffects(n)) {
          return TernaryValue.TRUE;
        }
        break;
      default:
        break;
    }

    return TernaryValue.UNKNOWN;
  }

  /**
   * Gets the value of a node as a String, or null if it cannot be converted.
   * When it returns a non-null String, this method effectively emulates the
   * <code>String()</code> JavaScript cast function.
   */
  public static String getStringValue(Node n) {
    // TODO(user): regex literals as well.
    switch (n.getToken()) {
      case STRING:
      case STRING_KEY:
        return n.getString();

      case TEMPLATELIT:
        // Only convert a template literal if all its expressions can be converted.
        String string = "";
        for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
          if (child.isString()) {
            string = string + child.getString();
          } else if (child.isTemplateLitSub()) {
            Node expression = child.getFirstChild();
            String expressionString = getStringValue(expression);
            if (expressionString == null) {
              // Cannot convert.
              return null;
            }
            string = string + expressionString;
          }
        }
        return string;

      case NAME:
        String name = n.getString();
        if ("undefined".equals(name)
            || "Infinity".equals(name)
            || "NaN".equals(name)) {
          return name;
        }
        break;

      case NUMBER:
        return DToA.numberToString(n.getDouble());

      case FALSE:
        return "false";

      case TRUE:
        return "true";

      case NULL:
        return "null";

      case VOID:
        return "undefined";

      case NOT:
        TernaryValue child = getPureBooleanValue(n.getFirstChild());
        if (child != TernaryValue.UNKNOWN) {
          return child.toBoolean(true) ? "false" : "true"; // reversed.
        }
        break;

      case ARRAYLIT:
        return arrayToString(n);

      case OBJECTLIT:
        return "[object Object]";
      default:
        break;
    }
    return null;
  }

  /**
   * When converting arrays to string using Array.prototype.toString or
   * Array.prototype.join, the rules for conversion to String are different
   * than converting each element individually.  Specifically, "null" and
   * "undefined" are converted to an empty string.
   * @param n A node that is a member of an Array.
   * @return The string representation.
   */
  static String getArrayElementStringValue(Node n) {
    return (NodeUtil.isNullOrUndefined(n) || n.isEmpty())
        ? "" : getStringValue(n);
  }

  static String arrayToString(Node literal) {
    Node first = literal.getFirstChild();
    StringBuilder result = new StringBuilder();
    for (Node n = first; n != null; n = n.getNext()) {
      String childValue = getArrayElementStringValue(n);
      if (childValue == null) {
        return null;
      }
      if (n != first) {
        result.append(',');
      }
      result.append(childValue);
    }
    return result.toString();
  }

  /**
   * Gets the value of a node as a Number, or null if it cannot be converted.
   * When it returns a non-null Double, this method effectively emulates the
   * <code>Number()</code> JavaScript cast function.
   *
   * @param n The node.
   * @return The value of a node as a Number, or null if it cannot be converted.
   */
  static Double getNumberValue(Node n) {
    switch (n.getToken()) {
      case TRUE:
        return 1.0;

      case FALSE:
      case NULL:
        return 0.0;

      case NUMBER:
        return n.getDouble();

      case VOID:
        if (mayHaveSideEffects(n.getFirstChild())) {
          return null;
        } else {
          return Double.NaN;
        }

      case NAME:
        // Check for known constants
        String name = n.getString();
        if (name.equals("undefined")) {
          return Double.NaN;
        }
        if (name.equals("NaN")) {
          return Double.NaN;
        }
        if (name.equals("Infinity")) {
          return Double.POSITIVE_INFINITY;
        }
        return null;

      case NEG:
        if (n.hasOneChild() && n.getFirstChild().isName()
            && n.getFirstChild().getString().equals("Infinity")) {
          return Double.NEGATIVE_INFINITY;
        }
        return null;

      case NOT:
        TernaryValue child = getPureBooleanValue(n.getFirstChild());
        if (child != TernaryValue.UNKNOWN) {
          return child.toBoolean(true) ? 0.0 : 1.0; // reversed.
        }
        break;

      case TEMPLATELIT:
        String string = getStringValue(n);
        if (string == null) {
          return null;
        }
        return getStringNumberValue(string);

      case STRING:
        return getStringNumberValue(n.getString());

      case ARRAYLIT:
      case OBJECTLIT:
        String value = getStringValue(n);
        return value != null ? getStringNumberValue(value) : null;
      default:
        break;
    }

    return null;
  }

  static Double getStringNumberValue(String rawJsString) {
    if (rawJsString.contains("\u000b")) {
      // vertical tab is not always whitespace
      return null;
    }

    String s = trimJsWhiteSpace(rawJsString);
    // return ScriptRuntime.toNumber(s);
    if (s.isEmpty()) {
      return 0.0;
    }

    if (s.length() > 2
        && s.charAt(0) == '0'
        && (s.charAt(1) == 'x' || s.charAt(1) == 'X')) {
      // Attempt to convert hex numbers.
      try {
        return Double.valueOf(Integer.parseInt(s.substring(2), 16));
      } catch (NumberFormatException e) {
        return Double.NaN;
      }
    }

    if (s.length() > 3
        && (s.charAt(0) == '-' || s.charAt(0) == '+')
        && s.charAt(1) == '0'
        && (s.charAt(2) == 'x' || s.charAt(2) == 'X')) {
      // hex numbers with explicit signs vary between browsers.
      return null;
    }

    // Firefox and IE treat the "Infinity" differently. Firefox is case
    // insensitive, but IE treats "infinity" as NaN.  So leave it alone.
    if (s.equals("infinity")
        || s.equals("-infinity")
        || s.equals("+infinity")) {
      return null;
    }

    try {
      return Double.parseDouble(s);
    } catch (NumberFormatException e) {
      return Double.NaN;
    }
  }

  static String trimJsWhiteSpace(String s) {
    int start = 0;
    int end = s.length();
    while (end > 0
        && TokenUtil.isStrWhiteSpaceChar(s.charAt(end - 1)) == TernaryValue.TRUE) {
      end--;
    }
    while (start < end
        && TokenUtil.isStrWhiteSpaceChar(s.charAt(start)) == TernaryValue.TRUE) {
      start++;
    }
    return s.substring(start, end);
  }

  /**
   * @param n A function or class node.
   * @return The name of the given function or class, if it has one.
   */
  public static String getName(Node n) {
    Node nameNode = getNameNode(n);
    return nameNode == null ? null : nameNode.getQualifiedName();
  }

  /**
   * Gets the node of a function or class's name. This method recognizes five forms:
   * <ul>
   * <li>{@code class name {...}}</li>
   * <li>{@code var name = class {...}}</li>
   * <li>{@code qualified.name = class {...}}</li>
   * <li>{@code var name2 = class name1 {...}}</li>
   * <li>{@code qualified.name2 = class name1 {...}}</li>
   * </ul>
   * In two last cases with named function expressions, the second name is
   * returned (the variable or qualified name).
   *
   * @param n A function or class node
   * @return the node best representing the class's name
   */
  public static Node getNameNode(Node n) {
    checkState(n.isFunction() || n.isClass(), n);
    Node parent = n.getParent();
    switch (parent.getToken()) {
      case NAME:
        // var name = function() ...
        // var name2 = function name1() ...
        return parent;

      case ASSIGN: {
        // qualified.name = function() ...
        // qualified.name2 = function name1() ...
        Node firstChild = parent.getFirstChild();
        return firstChild.isQualifiedName() ? firstChild : null;
      }

      default:
        // function name() ...
        //   or
        // class Name ...
        Node funNameNode = n.getFirstChild();
        // Don't return the name node for anonymous functions/classes.
        // TODO(tbreisacher): Currently we do two kinds of "empty" checks because
        // anonymous classes have an EMPTY name node while anonymous functions
        // have a STRING node with an empty string. Consider making these the same.
        return (funNameNode.isEmpty() || funNameNode.getString().isEmpty()) ? null : funNameNode;
    }
  }

  /** Set the given function/class node to an empty name */
  public static void removeName(Node n) {
    checkState(n.isFunction() || n.isClass());
    Node originalName = n.getFirstChild();
    Node emptyName = n.isFunction() ? IR.name("") : IR.empty();
    n.replaceChild(originalName, emptyName.useSourceInfoFrom(originalName));
  }

  /**
   * Gets the function's name. This method recognizes the forms:
   * <ul>
   * <li>{@code &#123;'name': function() ...&#125;}</li>
   * <li>{@code &#123;name: function() ...&#125;}</li>
   * <li>{@code function name() ...}</li>
   * <li>{@code var name = function() ...}</li>
   * <li>{@code var obj = {name() {} ...}}</li>
   * <li>{@code qualified.name = function() ...}</li>
   * <li>{@code var name2 = function name1() ...}</li>
   * <li>{@code qualified.name2 = function name1() ...}</li>
   * </ul>
   *
   * @param n a node whose type is {@link Token#FUNCTION}
   * @return the function's name, or {@code null} if it has no name
   */
  public static String getNearestFunctionName(Node n) {
    if (!n.isFunction()) {
      return null;
    }

    String name = getName(n);
    if (name != null) {
      return name;
    }

    // Check for the form { 'x' : function() { }} and {x() {}}
    Node parent = n.getParent();
    switch (parent.getToken()) {
      case MEMBER_FUNCTION_DEF:
      case SETTER_DEF:
      case GETTER_DEF:
      case STRING_KEY:
        // Return the name of the literal's key.
        return parent.getString();
      case NUMBER:
        return getStringValue(parent);
      default:
        break;
    }

    return null;
  }

  public static Node getClassMembers(Node n) {
    checkArgument(n.isClass());
    return n.getLastChild();
  }

  /**
   * Returns true if this is an immutable value.
   */
  static boolean isImmutableValue(Node n) {
    // TODO(johnlenz): rename this function.  It is currently being used
    // in two disjoint cases:
    // 1) We only care about the result of the expression
    //    (in which case NOT here should return true)
    // 2) We care that expression is a side-effect free and can't
    //    be side-effected by other expressions.
    // This should only be used to say the value is immutable and
    // hasSideEffects and canBeSideEffected should be used for the other case.

    switch (n.getToken()) {
      case STRING:
      case NUMBER:
      case NULL:
      case TRUE:
      case FALSE:
        return true;
      case CAST:
      case NOT:
      case VOID:
      case NEG:
        return isImmutableValue(n.getFirstChild());
      case NAME:
        String name = n.getString();
        // We assume here that programs don't change the value of the keyword
        // undefined to something other than the value undefined.
        return "undefined".equals(name) || "Infinity".equals(name) || "NaN".equals(name);
      case TEMPLATELIT:
        for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
          if (child.isTemplateLitSub()) {
            if (!isImmutableValue(child.getFirstChild())) {
              return false;
            }
          }
        }
        return true;
      default:
        break;
    }

    return false;
  }

  /**
   * Returns true if the operator on this node is symmetric
   */
  static boolean isSymmetricOperation(Node n) {
    switch (n.getToken()) {
      case EQ: // equal
      case NE: // not equal
      case SHEQ: // exactly equal
      case SHNE: // exactly not equal
      case MUL: // multiply, unlike add it only works on numbers
        // or results NaN if any of the operators is not a number
        return true;
      default:
        break;
    }
    return false;
  }

  /**
   * Returns true if the operator on this node is relational.
   * the returned set does not include the equalities.
   */
  static boolean isRelationalOperation(Node n) {
    switch (n.getToken()) {
      case GT: // equal
      case GE: // not equal
      case LT: // exactly equal
      case LE: // exactly not equal
        return true;
      default:
        break;
    }
    return false;
  }

  /**
   * Returns the inverse of an operator if it is invertible.
   * ex. '>' ==> '<'
   */
  static Token getInverseOperator(Token type) {
    switch (type) {
      case GT:
        return Token.LT;
      case LT:
        return Token.GT;
      case GE:
        return Token.LE;
      case LE:
        return Token.GE;
      default:
        throw new IllegalArgumentException("Unexpected token: " + type);
    }
  }

  /**
   * Returns true if this is a literal value. We define a literal value
   * as any node that evaluates to the same thing regardless of when or
   * where it is evaluated. So /xyz/ and [3, 5] are literals, but
   * the name a is not.
   *
   * <p>Function literals do not meet this definition, because they
   * lexically capture variables. For example, if you have
   * <code>
   * function() { return a; }
   * </code>
   * If it is evaluated in a different scope, then it
   * captures a different variable. Even if the function did not read
   * any captured variables directly, it would still fail this definition,
   * because it affects the lifecycle of variables in the enclosing scope.
   *
   * <p>However, a function literal with respect to a particular scope is
   * a literal.
   *
   * @param includeFunctions If true, all function expressions will be
   *     treated as literals.
   */
  public static boolean isLiteralValue(Node n, boolean includeFunctions) {
    switch (n.getToken()) {
      case CAST:
        return isLiteralValue(n.getFirstChild(), includeFunctions);

      case ARRAYLIT:
        for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
          if ((!child.isEmpty()) && !isLiteralValue(child, includeFunctions)) {
            return false;
          }
        }
        return true;

      case REGEXP:
        // Return true only if all descendants are const.
        for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
          if (!isLiteralValue(child, includeFunctions)) {
            return false;
          }
        }
        return true;

      case OBJECTLIT:
        for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
          if (child.isMemberFunctionDef() || NodeUtil.isGetOrSetKey(child)) {
            // { methodName() {...} }
            // { get propertyName() {...} }
            // { set propertyName(value) {...} }
            if (!includeFunctions) {
              return false;
            }
          } else if (child.isComputedProp()) {
            // { [key_expression]: value, ... }
            // { [key_expression](args) {...}, ... }
            if (!isLiteralValue(child.getFirstChild(), includeFunctions)
                || !isLiteralValue(child.getLastChild(), includeFunctions)) {
              return false;
            }
          } else if (child.isSpread()) {
            if (!isLiteralValue(child.getOnlyChild(), includeFunctions)) {
              return false;
            }
          } else {
            // { key: value, ... }
            // { "quoted_key": value, ... }
            checkState(child.isStringKey(), child);
            if (!isLiteralValue(child.getOnlyChild(), includeFunctions)) {
              return false;
            }
          }
        }
        return true;

      case FUNCTION:
        return includeFunctions && !NodeUtil.isFunctionDeclaration(n);

      case TEMPLATELIT:
        for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
          if (child.isTemplateLitSub()) {
            if (!isLiteralValue(child.getFirstChild(), includeFunctions)) {
              return false;
            }
          }
        }
        return true;

      default:
        return isImmutableValue(n);
    }
  }

  /**
   * Returns true iff the value associated with the node is a JS string literal, a concatenation
   * thereof or a ternary operator choosing between string literals.
   */
  static boolean isSomeCompileTimeConstStringValue(Node node) {
    // TODO(bangert): Support constants, using a Scope argument. See ConstParamCheck
    if (node.isString() || (node.isTemplateLit() && node.hasOneChild())) {
      return true;
    } else if (node.isAdd()) {
      checkState(node.hasTwoChildren(), node);
      Node left = node.getFirstChild();
      Node right = node.getLastChild();
      return isSomeCompileTimeConstStringValue(left) && isSomeCompileTimeConstStringValue(right);
    } else if (node.isHook()) {
      // Ternary operator a ? b : c
      Node left = node.getSecondChild();
      Node right = node.getLastChild();
      return isSomeCompileTimeConstStringValue(left) && isSomeCompileTimeConstStringValue(right);
    }
    return false;
  }

  /**
   * Determines whether the given value may be assigned to a define.
   *
   * @param val The value being assigned.
   * @param defines The list of names of existing defines.
   */
  static boolean isValidDefineValue(Node val, Set<String> defines) {
    switch (val.getToken()) {
      case STRING:
      case NUMBER:
      case TRUE:
      case FALSE:
        return true;

      // Binary operators are only valid if both children are valid.
      case AND:
      case OR:
      case ADD:
      case BITAND:
      case BITNOT:
      case BITOR:
      case BITXOR:
      case DIV:
      case EQ:
      case EXPONENT:
      case GE:
      case GT:
      case LE:
      case LSH:
      case LT:
      case MOD:
      case MUL:
      case NE:
      case RSH:
      case SHEQ:
      case SHNE:
      case SUB:
      case URSH:
        return isValidDefineValue(val.getFirstChild(), defines)
            && isValidDefineValue(val.getLastChild(), defines);

      case HOOK:
        return isValidDefineValue(val.getFirstChild(), defines)
            && isValidDefineValue(val.getSecondChild(), defines)
            && isValidDefineValue(val.getLastChild(), defines);

      // Unary operators are valid if the child is valid.
      case NOT:
      case NEG:
      case POS:
        return isValidDefineValue(val.getFirstChild(), defines);

      // Names are valid if and only if they are defines themselves.
      case NAME:
      case GETPROP:
        if (val.isQualifiedName()) {
          return defines.contains(val.getQualifiedName());
        }
        break;
      default:
        break;
    }
    return false;
  }

  /**
   * Returns whether this a BLOCK node with no children.
   *
   * @param block The node.
   */
  static boolean isEmptyBlock(Node block) {
    if (!block.isNormalBlock()) {
      return false;
    }

    for (Node n = block.getFirstChild(); n != null; n = n.getNext()) {
      if (!n.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  static boolean isBinaryOperator(Node n) {
    return isBinaryOperatorType(n.getToken());
  }

  /**
   * An operator with two operands that does not assign a value to either.
   * Once you cut through the layers of rules, these all parse similarly, taking
   * LeftHandSideExpression operands on either side.
   * Comma is not included, because it takes AssignmentExpression operands, making its syntax
   * different.
   */
  static boolean isBinaryOperatorType(Token type) {
    switch (type) {
      case OR:
      case AND:
      case BITOR:
      case BITXOR:
      case BITAND:
      case EQ:
      case NE:
      case SHEQ:
      case SHNE:
      case LT:
      case GT:
      case LE:
      case GE:
      case INSTANCEOF:
      case IN:
      case LSH:
      case RSH:
      case URSH:
      case ADD:
      case SUB:
      case MUL:
      case DIV:
      case MOD:
      case EXPONENT:
        return true;

      default:
        return false;
    }
  }

  static boolean isUnaryOperator(Node n) {
    return isUnaryOperatorType(n.getToken());
  }

  /**
   * An operator taking only one operand.
   * These all parse very similarly, taking LeftHandSideExpression operands.
   */
  static boolean isUnaryOperatorType(Token type) {
    switch (type) {
      case DELPROP:
      case VOID:
      case TYPEOF:
      case POS:
      case NEG:
      case BITNOT:
      case NOT:
        return true;

      default:
        return false;
    }
  }

  static boolean isUpdateOperator(Node n) {
    return isUpdateOperatorType(n.getToken());
  }

  static boolean isUpdateOperatorType(Token type) {
    switch (type) {
      case INC:
      case DEC:
        return true;

      default:
        return false;
    }
  }

  static boolean isSimpleOperator(Node n) {
    return isSimpleOperatorType(n.getToken());
  }

  /**
   * A "simple" operator is one whose children are expressions,
   * has no direct side-effects (unlike '+='), and has no
   * conditional aspects (unlike '||').
   */
  static boolean isSimpleOperatorType(Token type) {
    switch (type) {
      case ADD:
      case BITAND:
      case BITNOT:
      case BITOR:
      case BITXOR:
      case COMMA:
      case DIV:
      case EQ:
      case EXPONENT:
      case GE:
      case GETELEM:
      case GETPROP:
      case GT:
      case IN:
      case INSTANCEOF:
      case LE:
      case LSH:
      case LT:
      case MOD:
      case MUL:
      case NE:
      case NOT:
      case RSH:
      case SHEQ:
      case SHNE:
      case SUB:
      case TYPEOF:
      case VOID:
      case POS:
      case NEG:
      case URSH:
        return true;

      default:
        return false;
    }
  }

  /**
   * True for aliases defined with @const, not for aliases defined with @constructor/@interface.
   */
  static boolean isAliasedConstDefinition(Node lhs) {
    JSDocInfo jsdoc = getBestJSDocInfo(lhs);
    if (jsdoc == null && !lhs.isFromExterns()) {
      return false;
    }
    if (jsdoc != null && !jsdoc.hasConstAnnotation()) {
      return false;
    }
    Node rhs = getRValueOfLValue(lhs);
    if (rhs == null || !rhs.isQualifiedName()) {
      return false;
    }
    Node parent = lhs.getParent();
    return (lhs.isName() && parent.isVar())
        || (lhs.isGetProp() && lhs.isQualifiedName()
            && parent.isAssign() && parent.getParent().isExprResult());
  }

  static boolean isTypedefDecl(Node n) {
    if (n.isVar()
        || (n.isName() && n.getParent().isVar())
        || (n.isGetProp() && n.getParent().isExprResult())) {
      JSDocInfo jsdoc = getBestJSDocInfo(n);
      return jsdoc != null && jsdoc.hasTypedefType();
    }
    return false;
  }

  static boolean isEnumDecl(Node n) {
    if (NodeUtil.isNameDeclaration(n)
        || (n.isName() && NodeUtil.isNameDeclaration(n.getParent()))
        || (n.isGetProp() && n.getParent().isAssign() && n.getGrandparent().isExprResult())
        || (n.isAssign() && n.getParent().isExprResult())) {
      JSDocInfo jsdoc = getBestJSDocInfo(n);
      return jsdoc != null && jsdoc.hasEnumParameterType();
    }
    return false;
  }

  /**
   * Returns true iff this node defines a namespace, e.g.,
   *
   * /** @const * / var goog = {};
   * /** @const * / var goog = goog || {};
   * /** @const * / goog.math = goog.math || {};
   */
  public static boolean isNamespaceDecl(Node n) {
    JSDocInfo jsdoc = getBestJSDocInfo(n);
    if (jsdoc != null && !jsdoc.getTypeNodes().isEmpty()) {
      return false;
    }
    // In externs, we allow namespace definitions without @const.
    // This is a worse design than always requiring @const, but it helps with
    // namespaces that are defined in many places, such as gapi.
    // Also, omitting @const in externs is not as confusing as in source code,
    // because assigning an object literal in externs only makes sense when
    // defining a namespace or enum.
    boolean isMarkedConst = n.getParent().isConst() || (jsdoc != null && jsdoc.isConstant());
    if (!n.isFromExterns() && !isMarkedConst) {
      return false;
    }
    Node qnameNode;
    Node initializer;
    if (NodeUtil.isNameDeclaration(n.getParent())) {
      qnameNode = n;
      initializer = n.getFirstChild();
    } else if (n.isExprResult()) {
      Node expr = n.getFirstChild();
      if (!expr.isAssign() || !expr.getFirstChild().isGetProp()) {
        return false;
      }
      qnameNode = expr.getFirstChild();
      initializer = expr.getLastChild();
    } else if (n.isGetProp()) {
      Node parent = n.getParent();
      if (!parent.isAssign() || !parent.getParent().isExprResult()) {
        return false;
      }
      qnameNode = n;
      initializer = parent.getLastChild();
    } else {
      return false;
    }
    if (initializer == null || qnameNode == null) {
      return false;
    }
    if (initializer.isObjectLit()) {
      return true;
    }
    return initializer.isOr()
        && qnameNode.matchesQualifiedName(initializer.getFirstChild())
        && initializer.getLastChild().isObjectLit();
  }

  /** Determine if the given SCRIPT is a @typeSummary file, like an i.js file */
  public static boolean isFromTypeSummary(Node n) {
    checkArgument(n.isScript(), n);
    JSDocInfo info = n.getJSDocInfo();
    return info != null && info.isTypeSummary();
  }

  /**
   * Creates an EXPR_RESULT.
   *
   * @param child The expression itself.
   * @return Newly created EXPR node with the child as subexpression.
   */
  static Node newExpr(Node child) {
    return IR.exprResult(child).srcref(child);
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
   * This version default to the "safe" assumptions when the compiler object is not
   * provided (RegExp have side-effects, etc).
   */
  public static boolean mayHaveSideEffects(Node n) {
    return mayHaveSideEffects(n, null);
  }

  public static boolean mayHaveSideEffects(Node n, AbstractCompiler compiler) {
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
    switch (n.getToken()) {
      // Throws are by definition side effects, and yield and export are similar.
      case THROW:
      case YIELD:
      case EXPORT:
      case VAR:
      case LET:
      case CONST:
        return true;

      case OBJECTLIT:
        if (checkForNewObjects) {
          return true;
        }
        for (Node key = n.getFirstChild(); key != null; key = key.getNext()) {
          for (Node c = key.getFirstChild(); c != null; c = c.getNext()) {
            if (checkForStateChangeHelper(c, checkForNewObjects, compiler)) {
              return true;
            }
          }
        }
        return false;

      case ARRAYLIT:
      case REGEXP:
        if (checkForNewObjects) {
          return true;
        }
        break;

      case SPREAD:
        Node expr = n.getOnlyChild();
        if (!expr.isArrayLit()) {
          // Anything other than an array, in the absense of any other information,
          // we have to assume is going to invoke invoke a stateful generator or the like.
          return true;
        }
        break;

      case NAME:
        if (n.hasChildren()) {
          // This is the left side of a var/let/const
          return true;
        }
        break;

      case FUNCTION:
        // Function expressions don't have side-effects, but function
        // declarations change the namespace. Either way, we don't need to
        // check the children, since they aren't executed at declaration time.
        return checkForNewObjects || isFunctionDeclaration(n);

      case CLASS:
        return checkForNewObjects || isClassDeclaration(n)
            // Check the extends clause for side effects.
            || checkForStateChangeHelper(n.getSecondChild(), checkForNewObjects, compiler)
            // Check for class members that are computed properties with side effects.
            || checkForStateChangeHelper(n.getLastChild(), checkForNewObjects, compiler);

      case CLASS_MEMBERS:
        for (Node member = n.getFirstChild(); member != null; member = member.getNext()) {
          if (member.isComputedProp()
              && checkForStateChangeHelper(member.getFirstChild(), checkForNewObjects, compiler)) {
            return true;
          }
        }
        return false;

      case NEW:
        if (checkForNewObjects) {
          return true;
        }

        if (!constructorCallHasSideEffects(n)) {
          // loop below will see if the constructor parameters have
          // side-effects
          break;
        }
        return true;

      case CALL:
        // calls to functions that have no side effects have the no
        // side effect property set.
        if (!functionCallHasSideEffects(n, compiler)) {
          // loop below will see if the function parameters have
          // side-effects
          break;
        }
        return true;

      case TAGGED_TEMPLATELIT:
        return functionCallHasSideEffects(n, compiler);

      case CAST:
      case AND:
      case BLOCK:
      case ROOT:
      case EXPR_RESULT:
      case HOOK:
      case IF:
      case PARAM_LIST:
      case NUMBER:
      case OR:
      case THIS:
      case TRUE:
      case FALSE:
      case NULL:
      case STRING:
      case STRING_KEY:
      case SWITCH:
      case TEMPLATELIT_SUB:
      case TRY:
      case EMPTY:
      case TEMPLATELIT:
      case COMPUTED_PROP:
        break;

      default:
        if (isSimpleOperator(n)) {
          break;
        }

        if (isAssignmentOp(n)) {
          Node assignTarget = n.getFirstChild();
          if (assignTarget.isName()) {
            return true;
          }

          // Assignments will have side effects if
          // a) The RHS has side effects, or
          // b) The LHS has side effects, or
          // c) A name on the LHS will exist beyond the life of this statement.
          if (checkForStateChangeHelper(n.getFirstChild(), checkForNewObjects, compiler)
              || checkForStateChangeHelper(n.getLastChild(), checkForNewObjects, compiler)) {
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
   * @param callNode - constructor call node
   */
  static boolean constructorCallHasSideEffects(Node callNode) {
    checkArgument(callNode.isNew(), "Expected NEW node, got %s", callNode.getToken());

    if (callNode.isNoSideEffectsCall()) {
      return false;
    }

    if (callNode.isOnlyModifiesArgumentsCall() && allArgsUnescapedLocal(callNode)) {
      return false;
    }

    Node nameNode = callNode.getFirstChild();
    return !nameNode.isName() || !CONSTRUCTORS_WITHOUT_SIDE_EFFECTS.contains(nameNode.getString());
  }

  // A list of built-in object creation or primitive type cast functions that
  // can also be called as constructors but lack side-effects.
  // TODO(johnlenz): consider adding an extern annotation for this.
  private static final ImmutableSet<String> BUILTIN_FUNCTIONS_WITHOUT_SIDEEFFECTS =
      ImmutableSet.of("Object", "Array", "String", "Number", "Boolean", "RegExp", "Error");
  private static final ImmutableSet<String> OBJECT_METHODS_WITHOUT_SIDEEFFECTS =
      ImmutableSet.of("toString", "valueOf");
  private static final ImmutableSet<String> REGEXP_METHODS = ImmutableSet.of("test", "exec");
  private static final ImmutableSet<String> STRING_REGEXP_METHODS =
      ImmutableSet.of("match", "replace", "search", "split");

  /**
   * Returns true if calls to this function have side effects.
   *
   * @param callNode - function call node
   */
  static boolean functionCallHasSideEffects(Node callNode) {
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
    checkState(callNode.isCall() || callNode.isTaggedTemplateLit(), callNode);

    if (callNode.isNoSideEffectsCall()) {
      return false;
    }

    if (callNode.isOnlyModifiesArgumentsCall()
        && allArgsUnescapedLocal(callNode)) {
      return false;
    }

    Node callee = callNode.getFirstChild();

    // Built-in functions with no side effects.
    if (callee.isName()) {
      String name = callee.getString();
      if (BUILTIN_FUNCTIONS_WITHOUT_SIDEEFFECTS.contains(name)) {
        return false;
      }
    } else if (callee.isGetProp()) {
      if (callNode.hasOneChild()
          && OBJECT_METHODS_WITHOUT_SIDEEFFECTS.contains(callee.getLastChild().getString())) {
        return false;
      }

      if (callNode.isOnlyModifiesThisCall()
          && evaluatesToLocalValue(callee.getFirstChild())) {
        return false;
      }

      // Many common Math functions have no side-effects.
      // TODO(nicksantos): This is a terrible terrible hack, until
      // I create a definitionProvider that understands namespacing.
      if (callee.getFirstChild().isName() && callee.isQualifiedName()
          && callee.getFirstChild().getString().equals("Math")) {
        switch(callee.getLastChild().getString()) {
          case "abs":
          case "acos":
          case "acosh":
          case "asin":
          case "asinh":
          case "atan":
          case "atanh":
          case "atan2":
          case "cbrt":
          case "ceil":
          case "cos":
          case "cosh":
          case "exp":
          case "expm1":
          case "floor":
          case "hypot":
          case "log":
          case "log10":
          case "log1p":
          case "log2":
          case "max":
          case "min":
          case "pow":
          case "round":
          case "sign":
          case "sin":
          case "sinh":
          case "sqrt":
          case "tan":
          case "tanh":
          case "trunc":
            return false;
          case "random":
            return !callNode.hasOneChild(); // no parameters
          default:
            // Unknown Math.* function, so fall out of this switch statement.
        }
      }

      if (compiler != null && !compiler.hasRegExpGlobalReferences()) {
        if (callee.getFirstChild().isRegExp()
            && REGEXP_METHODS.contains(callee.getLastChild().getString())) {
          return false;
        } else if (isTypedAsString(callee.getFirstChild(), compiler)) {
          // Unlike regexs, string methods don't need to be hosted on a string literal
          // to avoid leaking mutating global state changes, it is just necessary that
          // the regex object can't be referenced.
          String method = callee.getLastChild().getString();
          Node param = callee.getNext();
          if (param != null) {
            if (param.isString()) {
              if (STRING_REGEXP_METHODS.contains(method)) {
                return false;
              }
            } else if (param.isRegExp()) {
              if ("replace".equals(method)) {
                // Assume anything but a string constant has side-effects
                return !param.getNext().isString();
              } else if (STRING_REGEXP_METHODS.contains(method)) {
                return false;
              }
            }
          }
        }
      }
    }

    return true;
  }

  private static boolean isTypedAsString(Node n, AbstractCompiler compiler) {
    if (n.isString()) {
      return true;
    }

    if (compiler.getOptions().useTypesForLocalOptimization) {
      TypeI type = n.getTypeI();
      if (type != null) {
        TypeI nativeStringType = compiler.getTypeIRegistry()
            .getNativeType(JSTypeNative.STRING_TYPE);
        if (type.isEquivalentTo(nativeStringType)) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * @return Whether the call has a local result.
   */
  static boolean callHasLocalResult(Node n) {
    checkState(n.isCall() || n.isTaggedTemplateLit(), n);
    return (n.getSideEffectFlags() & Node.FLAG_LOCAL_RESULTS) > 0;
  }

  /**
   * @return Whether the new has a local result.
   */
  static boolean newHasLocalResult(Node n) {
    checkState(n.isNew(), n);
    return n.isOnlyModifiesThisCall();
  }

  /**
   * Returns true if the current node's type implies side effects.
   *
   * <p>This is a non-recursive version of the may have side effects
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

    switch (n.getToken()) {
      case DELPROP:
      case DEC:
      case INC:
      case YIELD:
      case THROW:
      case AWAIT:
        return true;
      case CALL:
        return NodeUtil.functionCallHasSideEffects(n, compiler);
      case NEW:
        return NodeUtil.constructorCallHasSideEffects(n);
      case NAME:
        // A variable definition.
        return n.hasChildren();
      default:
        return false;
    }
  }

  static boolean allArgsUnescapedLocal(Node callOrNew) {
    for (Node arg = callOrNew.getSecondChild(); arg != null; arg = arg.getNext()) {
      if (!evaluatesToLocalValue(arg)) {
        return false;
      }
    }
    return true;
  }

  /**
   * @return Whether the tree can be affected by side-effects or
   * has side-effects.
   */
  static boolean canBeSideEffected(Node n) {
    Set<String> emptySet = Collections.emptySet();
    return canBeSideEffected(n, emptySet, null);
  }

  /**
   * @param knownConstants A set of names known to be constant value at
   * node 'n' (such as locals that are last written before n can execute).
   * @return Whether the tree can be affected by side-effects or has side-effects.
   */
  // TODO(nick): Get rid of the knownConstants argument in favor of using
  // scope with InferConsts.
  static boolean canBeSideEffected(Node n, Set<String> knownConstants, @Nullable Scope scope) {
    switch (n.getToken()) {
      case YIELD:
      case CALL:
      case NEW:
        // Function calls or constructor can reference changed values.
        // TODO(johnlenz): Add some mechanism for determining that functions
        // are unaffected by side effects.
        return true;
      case NAME:
        // Non-constant names values may have been changed.
        return !isConstantVar(n, scope) && !knownConstants.contains(n.getString());

      // Properties on constant NAMEs can still be side-effected.
      case GETPROP:
      case GETELEM:
        return true;

      case FUNCTION:
        // Function expression are not changed by side-effects,
        // and function declarations are not part of expressions.
        // TODO(bradfordcsmith): Do we need to add a case for CLASS here?
        //     This checkState currently does not exclude class methods.
        checkState(!isFunctionDeclaration(n), n);
        return false;
      default:
        break;
    }

    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      if (canBeSideEffected(c, knownConstants, scope)) {
        return true;
      }
    }

    return false;
  }

  /**
   * The comma operator has the lowest precedence, 0, followed by the assignment operators
   * ({@code =}, {@code &=}, {@code +=}, etc.) which have precedence of 1, and so on.
   *
   * See
   * https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Operators/Operator_Precedence
   */
  public static int precedence(Token type) {
    switch (type) {
      case COMMA:  return 0;
      case ASSIGN_BITOR:
      case ASSIGN_BITXOR:
      case ASSIGN_BITAND:
      case ASSIGN_LSH:
      case ASSIGN_RSH:
      case ASSIGN_URSH:
      case ASSIGN_ADD:
      case ASSIGN_SUB:
      case ASSIGN_MUL:
      case ASSIGN_EXPONENT:
      case ASSIGN_DIV:
      case ASSIGN_MOD:
      case ASSIGN: return 1;
      case YIELD:  return 2;
      case HOOK:   return 3;  // ?: operator
      case OR:     return 4;
      case AND:    return 5;
      case BITOR:  return 6;
      case BITXOR: return 7;
      case BITAND: return 8;
      case EQ:
      case NE:
      case SHEQ:
      case SHNE:   return 9;
      case LT:
      case GT:
      case LE:
      case GE:
      case INSTANCEOF:
      case IN:     return 10;
      case LSH:
      case RSH:
      case URSH:   return 11;
      case SUB:
      case ADD:    return 12;
      case MUL:
      case MOD:
      case DIV:
        return 13;

      case EXPONENT:
        return 14;

      case AWAIT:
      case NEW:
      case DELPROP:
      case TYPEOF:
      case VOID:
      case NOT:
      case BITNOT:
      case POS:
      case NEG:
        return 15; // Unary operators

      case INC:
      case DEC:
        return 16; // Update operators

      case CALL:
      case GETELEM:
      case GETPROP:
      case NEW_TARGET:
        // Data values
      case ARRAYLIT:
      case ARRAY_PATTERN:
      case DEFAULT_VALUE:
      case DESTRUCTURING_LHS:
      case EMPTY:  // TODO(johnlenz): remove this.
      case FALSE:
      case FUNCTION:
      case CLASS:
      case INTERFACE:
      case NAME:
      case NULL:
      case NUMBER:
      case OBJECTLIT:
      case OBJECT_PATTERN:
      case REGEXP:
      case REST:
      case SPREAD:
      case STRING:
      case STRING_KEY:
      case MEMBER_VARIABLE_DEF:
      case INDEX_SIGNATURE:
      case CALL_SIGNATURE:
      case OPTIONAL_PARAMETER:
      case THIS:
      case SUPER:
      case TRUE:
      case TAGGED_TEMPLATELIT:
      case TEMPLATELIT:
        // Tokens from the type declaration AST
      case UNION_TYPE:
        return 17;
      case FUNCTION_TYPE:
        return 18;
      case ARRAY_TYPE:
      case PARAMETERIZED_TYPE:
        return 19;
      case STRING_TYPE:
      case NUMBER_TYPE:
      case BOOLEAN_TYPE:
      case ANY_TYPE:
      case RECORD_TYPE:
      case NULLABLE_TYPE:
      case NAMED_TYPE:
      case UNDEFINED_TYPE:
      case GENERIC_TYPE:
        return 20;
      case CAST:
        return 21;

      default:
        throw new IllegalStateException("Unknown precedence for " + type);
    }
  }

  public static boolean isUndefined(Node n) {
    switch (n.getToken()) {
      case VOID:
        return true;
      case NAME:
        return n.getString().equals("undefined");
      default:
        break;
    }
    return false;
  }

  public static boolean isNullOrUndefined(Node n) {
    return n.isNull() || isUndefined(n);
  }

  static boolean isImmutableResult(Node n) {
    return allResultsMatch(n, NodeUtil::isImmutableValue);
  }

  /**
   * Apply the supplied predicate against
   * all possible result Nodes of the expression.
   */
  static boolean allResultsMatch(Node n, Predicate<Node> p) {
    switch (n.getToken()) {
      case CAST:
        return allResultsMatch(n.getFirstChild(), p);
      case ASSIGN:
      case COMMA:
        return allResultsMatch(n.getLastChild(), p);
      case AND:
      case OR:
        return allResultsMatch(n.getFirstChild(), p)
            && allResultsMatch(n.getLastChild(), p);
      case HOOK:
        return allResultsMatch(n.getSecondChild(), p)
            && allResultsMatch(n.getLastChild(), p);
      default:
        return p.apply(n);
    }
  }

  /** @see #getKnownValueType(Node) */
  public enum ValueType {
    UNDETERMINED,
    NULL,
    VOID,
    NUMBER,
    STRING,
    BOOLEAN,
    OBJECT
  }

  /**
   * Apply the supplied predicate against
   * all possible result Nodes of the expression.
   */
  public static ValueType getKnownValueType(Node n) {
    switch (n.getToken()) {
      case CAST:
        return getKnownValueType(n.getFirstChild());
      case ASSIGN:
      case COMMA:
        return getKnownValueType(n.getLastChild());
      case AND:
      case OR:
        return and(
            getKnownValueType(n.getFirstChild()),
            getKnownValueType(n.getLastChild()));
      case HOOK:
        return and(
            getKnownValueType(n.getSecondChild()),
            getKnownValueType(n.getLastChild()));

      case ADD: {
        ValueType last = getKnownValueType(n.getLastChild());
        if (last == ValueType.STRING) {
          return ValueType.STRING;
        }
        ValueType first = getKnownValueType(n.getFirstChild());
        if (first == ValueType.STRING) {
          return ValueType.STRING;
        }

        // There are some pretty weird cases for object types:
        //   {} + [] === "0"
        //   [] + {} ==== "[object Object]"
        if (first == ValueType.OBJECT || last == ValueType.OBJECT) {
          return ValueType.UNDETERMINED;
        }

        if (!mayBeString(first) && !mayBeString(last)) {
          // ADD used with compilations of null, undefined, boolean and number always result
          // in numbers.
          return ValueType.NUMBER;
        }

        // There are some pretty weird cases for object types:
        //   {} + [] === "0"
        //   [] + {} ==== "[object Object]"

        return ValueType.UNDETERMINED;
      }

      case ASSIGN_ADD: {
        ValueType last = getKnownValueType(n.getLastChild());
        if (last == ValueType.STRING) {
          return ValueType.STRING;
        }
        return ValueType.UNDETERMINED;
      }

      case NAME:
        String name = n.getString();
        if (name.equals("undefined")) {
          return ValueType.VOID;
        }
        if (name.equals("NaN")) {
          return ValueType.NUMBER;
        }
        if (name.equals("Infinity")) {
          return ValueType.NUMBER;
        }
        return ValueType.UNDETERMINED;

      case ASSIGN_BITOR:
      case ASSIGN_BITXOR:
      case ASSIGN_BITAND:
      case ASSIGN_LSH:
      case ASSIGN_RSH:
      case ASSIGN_URSH:
      case ASSIGN_SUB:
      case ASSIGN_MUL:
      case ASSIGN_EXPONENT:
      case ASSIGN_DIV:
      case ASSIGN_MOD:
      case BITNOT:
      case BITOR:
      case BITXOR:
      case BITAND:
      case LSH:
      case RSH:
      case URSH:
      case SUB:
      case MUL:
      case MOD:
      case DIV:
      case EXPONENT:
      case INC:
      case DEC:
      case POS:
      case NEG:
      case NUMBER:
        return ValueType.NUMBER;

      // Primitives
      case TRUE:
      case FALSE:
        // Comparisons
      case EQ:
      case NE:
      case SHEQ:
      case SHNE:
      case LT:
      case GT:
      case LE:
      case GE:
        // Queries
      case IN:
      case INSTANCEOF:
        // Inversion
      case NOT:
        // delete operator returns a boolean.
      case DELPROP:
        return ValueType.BOOLEAN;

      case TYPEOF:
      case STRING:
      case TEMPLATELIT:
        return ValueType.STRING;

      case NULL:
        return ValueType.NULL;

      case VOID:
        return ValueType.VOID;

      case FUNCTION:
      case NEW:
      case ARRAYLIT:
      case OBJECTLIT:
      case REGEXP:
        return ValueType.OBJECT;

      default:
        return ValueType.UNDETERMINED;
    }
  }

  static ValueType and(ValueType a, ValueType b) {
    return (a == b) ? a : ValueType.UNDETERMINED;
  }

  /**
   * Returns true if the result of node evaluation is always a number
   */
  public static boolean isNumericResult(Node n) {
    return getKnownValueType(n) == ValueType.NUMBER;
  }

  /**
   * @return Whether the result of node evaluation is always a boolean
   */
  public static boolean isBooleanResult(Node n) {
    return getKnownValueType(n) == ValueType.BOOLEAN;
  }

  /**
   * @return Whether the result of node evaluation is always a string
   */
  public static boolean isStringResult(Node n) {
    return getKnownValueType(n) == ValueType.STRING;
  }

  /**
   * @return Whether the result of node evaluation is always an object
   */
  public static boolean isObjectResult(Node n) {
    return getKnownValueType(n) == ValueType.OBJECT;
  }

  static boolean mayBeString(Node n) {
    return mayBeString(n, false);
  }

  /**
   * Return if the node is possibly a string.
   *
   * @param n The node.
   * @param useType If true and the node has a primitive type, return true if that type is string
   *     and false otherwise.
   * @return Whether the results is possibly a string.
   */
  static boolean mayBeString(Node n, boolean useType) {
    if (useType) {
      TypeI type = n.getTypeI();
      if (type != null) {
        if (type.isStringValueType()) {
          return true;
        } else if (type.isNumberValueType() || type.isBooleanValueType() || type.isNullType()
            || type.isVoidType()) {
          return false;
        }
      }
    }
    return mayBeString(getKnownValueType(n));
  }

  /**
   * @return Whether the results is possibly a string, this includes Objects which may implicitly
   * be converted to a string.
   */
  static boolean mayBeString(ValueType type) {
    switch (type) {
      case BOOLEAN:
      case NULL:
      case NUMBER:
      case VOID:
        return false;
      case OBJECT:
      case STRING:
      case UNDETERMINED:
        return true;
      default:
        throw new IllegalStateException("unexpected");
    }
  }

  static boolean mayBeObject(Node n) {
    return mayBeObject(getKnownValueType(n));
  }

  static boolean mayBeObject(ValueType type) {
    switch (type) {
      case BOOLEAN:
      case NULL:
      case NUMBER:
      case STRING:
      case VOID:
        return false;
      case OBJECT:
      case UNDETERMINED:
        return true;
      default:
        throw new IllegalStateException("unexpected");
    }
  }

  /**
   * Returns true if the operator is associative.
   * e.g. (a * b) * c = a * (b * c)
   * Note: "+" is not associative because it is also the concatenation
   * for strings. e.g. "a" + (1 + 2) is not "a" + 1 + 2
   */
  static boolean isAssociative(Token type) {
    switch (type) {
      case MUL:
      case AND:
      case OR:
      case BITOR:
      case BITXOR:
      case BITAND:
        return true;
      default:
        return false;
    }
  }

  /**
   * Returns true if the operator is commutative.
   * e.g. (a * b) * c = c * (b * a)
   * Note 1: "+" is not commutative because it is also the concatenation
   * for strings. e.g. "a" + (1 + 2) is not "a" + 1 + 2
   * Note 2: only operations on literals and pure functions are commutative.
   */
  static boolean isCommutative(Token type) {
    switch (type) {
      case MUL:
      case BITOR:
      case BITXOR:
      case BITAND:
        return true;
      default:
        return false;
    }
  }

  public static boolean isAssignmentOp(Node n) {
    switch (n.getToken()) {
      case ASSIGN:
      case ASSIGN_BITOR:
      case ASSIGN_BITXOR:
      case ASSIGN_BITAND:
      case ASSIGN_LSH:
      case ASSIGN_RSH:
      case ASSIGN_URSH:
      case ASSIGN_ADD:
      case ASSIGN_SUB:
      case ASSIGN_MUL:
      case ASSIGN_EXPONENT:
      case ASSIGN_DIV:
      case ASSIGN_MOD:
        return true;
      default:
        break;
    }
    return false;
  }

  public static boolean isCompoundAssignmentOp(Node n) {
    return isAssignmentOp(n) && !n.isAssign();
  }

  static Token getOpFromAssignmentOp(Node n) {
    switch (n.getToken()) {
      case ASSIGN_BITOR:
        return Token.BITOR;
      case ASSIGN_BITXOR:
        return Token.BITXOR;
      case ASSIGN_BITAND:
        return Token.BITAND;
      case ASSIGN_LSH:
        return Token.LSH;
      case ASSIGN_RSH:
        return Token.RSH;
      case ASSIGN_URSH:
        return Token.URSH;
      case ASSIGN_ADD:
        return Token.ADD;
      case ASSIGN_SUB:
        return Token.SUB;
      case ASSIGN_MUL:
        return Token.MUL;
      case ASSIGN_EXPONENT:
        return Token.EXPONENT;
      case ASSIGN_DIV:
        return Token.DIV;
      case ASSIGN_MOD:
        return Token.MOD;
      default:
        break;
    }
    throw new IllegalArgumentException("Not an assignment op:" + n);
  }

  static Token getAssignOpFromOp(Node n) {
    switch (n.getToken()) {
      case BITOR:
        return Token.ASSIGN_BITOR;
      case BITXOR:
        return Token.ASSIGN_BITXOR;
      case BITAND:
        return Token.ASSIGN_BITAND;
      case LSH:
        return Token.ASSIGN_LSH;
      case RSH:
        return Token.ASSIGN_RSH;
      case URSH:
        return Token.ASSIGN_URSH;
      case ADD:
        return Token.ASSIGN_ADD;
      case SUB:
        return Token.ASSIGN_SUB;
      case MUL:
        return Token.ASSIGN_MUL;
      case EXPONENT:
        return Token.ASSIGN_EXPONENT;
      case DIV:
        return Token.ASSIGN_DIV;
      case MOD:
        return Token.ASSIGN_MOD;
      default:
        throw new IllegalStateException("Unexpected operator: " + n);
    }
  }

  static boolean hasCorrespondingAssignmentOp(Node n) {
    switch (n.getToken()) {
      case BITOR:
      case BITXOR:
      case BITAND:
      case LSH:
      case RSH:
      case URSH:
      case ADD:
      case SUB:
      case MUL:
      case DIV:
      case MOD:
        return true;
      default:
        return false;
    }
  }

  /**
   * Determines if the given node contains a function statement or function
   * expression.
   */
  static boolean containsFunction(Node n) {
    return containsType(n, Token.FUNCTION);
  }

  /**
   * Gets the closest ancestor to the given node of the provided type.
   */
  @CheckReturnValue
  public static Node getEnclosingType(Node n, final Token type) {
    return getEnclosingNode(
        n,
        new Predicate<Node>() {
          @Override
          public boolean apply(Node n) {
            return n.getToken() == type;
          }
        });
  }

  /**
   * Finds the class member function containing the given node.
   */
  @CheckReturnValue
  static Node getEnclosingClassMemberFunction(Node n) {
    return getEnclosingType(n, Token.MEMBER_FUNCTION_DEF);
  }

  /**
   * Finds the class containing the given node.
   */
  @CheckReturnValue
  public static Node getEnclosingClass(Node n) {
    return getEnclosingType(n, Token.CLASS);
  }

  /**
   * Finds the function containing the given node.
   */
  @CheckReturnValue
  public static Node getEnclosingFunction(Node n) {
    return getEnclosingType(n, Token.FUNCTION);
  }

  /**
   * Finds the script containing the given node.
   */
  @CheckReturnValue
  public static Node getEnclosingScript(Node n) {
    return getEnclosingType(n, Token.SCRIPT);
  }

  /**
   * Finds the block containing the given node.
   */
  @CheckReturnValue
  public static Node getEnclosingBlock(Node n) {
    return getEnclosingType(n, Token.BLOCK);
  }

  @CheckReturnValue
  public static Node getEnclosingBlockScopeRoot(Node n) {
    return getEnclosingNode(n, NodeUtil::createsBlockScope);
  }

  @CheckReturnValue
  public static Node getEnclosingScopeRoot(Node n) {
    return getEnclosingNode(n, NodeUtil::createsScope);
  }

  public static boolean isInFunction(Node n) {
    return getEnclosingFunction(n) != null;
  }

  @CheckReturnValue
  public static Node getEnclosingStatement(Node n) {
    return getEnclosingNode(n, NodeUtil::isStatement);
  }

  @CheckReturnValue
  public static Node getEnclosingNode(Node n, Predicate<Node> pred) {
    Node curr = n;
    while (curr != null && !pred.apply(curr)) {
      curr = curr.getParent();
    }
    return curr;
  }

  /**
   * @return The first property in the objlit or class members, that matches the key.
   */
  @Nullable
  static Node getFirstPropMatchingKey(Node n, String keyName) {
    checkState(n.isObjectLit() || n.isClassMembers());
    for (Node keyNode : n.children()) {
      if ((keyNode.isStringKey() || keyNode.isMemberFunctionDef())
          && keyNode.getString().equals(keyName)) {
        return keyNode.getFirstChild();
      }
    }
    return null;
  }

  /** @return The first getter in the class members that matches the key. */
  @Nullable
  static Node getFirstGetterMatchingKey(Node n, String keyName) {
    checkState(n.isClassMembers() || n.isObjectLit(), n);
    for (Node keyNode : n.children()) {
      if (keyNode.isGetterDef() && keyNode.getString().equals(keyName)) {
        return keyNode;
      }
    }
    return null;
  }

  /** @return The first setter in the class members that matches the key. */
  @Nullable
  static Node getFirstSetterMatchingKey(Node n, String keyName) {
    checkState(n.isClassMembers() || n.isObjectLit(), n);
    for (Node keyNode : n.children()) {
      if (keyNode.isSetterDef() && keyNode.getString().equals(keyName)) {
        return keyNode;
      }
    }
    return null;
  }

  /** @return The first computed property in the objlit whose key matches {@code key}. */
  @Nullable
  static Node getFirstComputedPropMatchingKey(Node objlit, Node key) {
    checkState(objlit.isObjectLit());
    for (Node child : objlit.children()) {
      if (child.isComputedProp() && child.getFirstChild().isEquivalentTo(key)) {
        return child.getLastChild();
      }
    }
    return null;
  }

  /**
   * Returns true if the shallow scope contains references to 'this' keyword
   */
  static boolean referencesThis(Node n) {
    Node start = (n.isFunction()) ? n.getLastChild() : n;
    return containsType(start, Token.THIS, MATCH_NOT_THIS_BINDING);
  }

  /**
   * Returns true if the current scope contains references to the 'super' keyword.
   * Note that if there are classes declared inside the current class, super calls which
   * reference those classes are not reported.
   */
  static boolean referencesSuper(Node n) {
    Node curr = n.getFirstChild();
    while (curr != null) {
      if (containsType(curr, Token.SUPER, node -> !node.isClass())) {
        return true;
      }
      curr = curr.getNext();
    }
    return false;
  }

  /**
   * Is this a GETPROP or GETELEM node?
   */
  public static boolean isGet(Node n) {
    return n.isGetProp() || n.isGetElem();
  }


  /**
   * Is this node the name of a block-scoped declaration?
   * Checks for let, const, class, or block-scoped function declarations.
   *
   * @param n The node
   * @return True if {@code n} is the NAME of a block-scoped declaration.
   */
  static boolean isBlockScopedDeclaration(Node n) {
    if (n.isName()) {
      switch (n.getParent().getToken()) {
        case LET:
        case CONST:
        case CATCH:
          return true;
        case CLASS:
          return n.getParent().getFirstChild() == n;
        case FUNCTION:
          return isBlockScopedFunctionDeclaration(n.getParent());
        default:
          break;
      }
    }
    return false;
  }

  /**
   * Is this node a name declaration?
   *
   * @param n The node
   * @return True if {@code n} is VAR, LET or CONST
   */
  public static boolean isNameDeclaration(Node n) {
    return n != null && (n.isVar() || n.isLet() || n.isConst());
  }

  /**
   * @param n The node
   * @return True if {@code n} is a VAR, LET or CONST that contains a
   *     destructuring pattern.
   */
  static boolean isDestructuringDeclaration(Node n) {
    if (isNameDeclaration(n)) {
      for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
        if (c.isDestructuringLhs()) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * For an assignment or variable declaration get the assigned value.
   * @return The value node representing the new value.
   */
  public static Node getAssignedValue(Node n) {
    checkState(n.isName() || n.isGetProp(), n);
    Node parent = n.getParent();
    if (NodeUtil.isNameDeclaration(parent)) {
      return n.getFirstChild();
    } else if (parent.isAssign() && parent.getFirstChild() == n) {
      return n.getNext();
    } else {
      return null;
    }
  }

  /**
   * Is this node an assignment expression statement?
   *
   * @param n The node
   * @return True if {@code n} is EXPR_RESULT and {@code n}'s
   *     first child is ASSIGN
   */
  static boolean isExprAssign(Node n) {
    return n.isExprResult()
        && n.getFirstChild().isAssign();
  }

  /**
   * Is this node a call expression statement?
   *
   * @param n The node
   * @return True if {@code n} is EXPR_RESULT and {@code n}'s
   *     first child is CALL
   */
  public static boolean isExprCall(Node n) {
    return n.isExprResult()
        && n.getFirstChild().isCall();
  }

  static boolean isVanillaFunction(Node n) {
    return n.isFunction() && !n.isArrowFunction();
  }

  public static boolean isEnhancedFor(Node n) {
    return n.isForOf() || n.isForIn();
  }

  public static boolean isAnyFor(Node n) {
    return n.isVanillaFor() || n.isForIn() || n.isForOf();
  }

  /**
   * Determines whether the given node is a FOR, DO, or WHILE node.
   */
  static boolean isLoopStructure(Node n) {
    switch (n.getToken()) {
      case FOR:
      case FOR_IN:
      case FOR_OF:
      case DO:
      case WHILE:
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
    switch (n.getToken()) {
      case FOR:
      case FOR_IN:
      case FOR_OF:
      case WHILE:
        return n.getLastChild();
      case DO:
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

      if (parent.isFunction()) {
        break;
      }
    }
    return false;
  }

  /**
   * Determines whether the given node is a FOR, DO, WHILE, WITH, or IF node.
   */
  public static boolean isControlStructure(Node n) {
    switch (n.getToken()) {
      case FOR:
      case FOR_IN:
      case FOR_OF:
      case DO:
      case WHILE:
      case WITH:
      case IF:
      case LABEL:
      case TRY:
      case CATCH:
      case SWITCH:
      case CASE:
      case DEFAULT_CASE:
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
    switch (parent.getToken()) {
      case DO:
        return parent.getFirstChild() == n;
      case TRY:
        return parent.getFirstChild() == n || parent.getLastChild() == n;
      case FOR:
      case FOR_IN:
      case FOR_OF:
      case WHILE:
      case LABEL:
      case WITH:
      case CATCH:
        return parent.getLastChild() == n;
      case IF:
      case SWITCH:
      case CASE:
        return parent.getFirstChild() != n;
      case DEFAULT_CASE:
        return true;
      default:
        checkState(isControlStructure(parent), parent);
        return false;
    }
  }

  /**
   * Gets the condition of an ON_TRUE / ON_FALSE CFG edge.
   * @param n a node with an outgoing conditional CFG edge
   * @return the condition node or null if the condition is not obviously a node
   */
  static Node getConditionExpression(Node n) {
    switch (n.getToken()) {
      case IF:
      case WHILE:
        return n.getFirstChild();
      case DO:
        return n.getLastChild();
      case FOR:
        return n.getSecondChild();
      case FOR_IN:
      case FOR_OF:
      case CASE:
        return null;
      default:
        break;
    }
    throw new IllegalArgumentException(n + " does not have a condition.");
  }

  /**
   * @return Whether the node is of a type that contain other statements.
   */
  public static boolean isStatementBlock(Node n) {
    return n.isRoot() || n.isScript() || n.isNormalBlock() || n.isModuleBody();
  }

  /**
   * A block scope is created by a non-synthetic block node, a for loop node,
   * or a for-of loop node.
   *
   * <p>Note: for functions, we use two separate scopes for parameters and
   * declarations in the body. We need to make sure default parameters cannot
   * reference var / function declarations in the body.
   *
   * @return Whether the node creates a block scope.
   */
  static boolean createsBlockScope(Node n) {
    switch (n.getToken()) {
      case BLOCK:
        Node parent = n.getParent();
        // Don't create block scope for switch cases or catch blocks.
        return parent != null && !isSwitchCase(parent) && !parent.isCatch();
      case FOR:
      case FOR_IN:
      case FOR_OF:
      case SWITCH:
      case CLASS:
        return true;
      default:
        return false;
    }
  }

  static boolean createsScope(Node n) {
    return createsBlockScope(n) || n.isFunction() || n.isModuleBody()
        // The ROOT nodes that are the root of the externs tree or main JS tree do not
        // create scopes. The parent of those two, which is the root of the entire AST and
        // therefore has no parent, is the only ROOT node that creates a scope.
        || (n.isRoot() && n.getParent() == null);
  }

  private static final Set<Token> DEFINITE_CFG_ROOTS =
      EnumSet.of(Token.FUNCTION, Token.SCRIPT, Token.MODULE_BODY, Token.ROOT);

  static boolean isValidCfgRoot(Node n) {
    return DEFINITE_CFG_ROOTS.contains(n.getToken());
  }

  /**
   * @return Whether the node is used as a statement.
   */
  public static boolean isStatement(Node n) {
    return !n.isModuleBody() && isStatementParent(n.getParent());
  }

  private static final Set<Token> IS_STATEMENT_PARENT =
      EnumSet.of(
          Token.SCRIPT,
          Token.MODULE_BODY,
          Token.BLOCK,
          Token.LABEL,
          Token.NAMESPACE_ELEMENTS,
          Token.INTERFACE_MEMBERS);

  public static boolean isStatementParent(Node parent) {
    // It is not possible to determine definitely if a node is a statement
    // or not if it is not part of the AST.  A FUNCTION node can be
    // either part of an expression or a statement.
    return IS_STATEMENT_PARENT.contains(parent.getToken());
  }

  private static boolean isDeclarationParent(Node parent) {
    switch (parent.getToken()) {
      case DECLARE:
      case EXPORT:
        return true;
      default:
        return isStatementParent(parent);
    }
  }

  /** Whether the node is part of a switch statement. */
  static boolean isSwitchCase(Node n) {
    return n.isCase() || n.isDefaultCase();
  }

  /**
   * @return Whether the node is a reference to a variable, function, class or
   *     function parameter (not a label or an empty function expression name).
   */
  static boolean isReferenceName(Node n) {
    return n.isName() && !n.getString().isEmpty();
  }

  /**
   * @return Whether the name in an import or export spec is not defined within the module, but is
   *     an exported name from this or another module. e.g. nonlocal in "export {a as nonlocal}" or
   *     "import {nonlocal as a} from './foo.js'"
   */
  static boolean isNonlocalModuleExportName(Node n) {
    Node parent = n.getParent();
    return (parent != null
            && n.isName()
            && ((parent.isExportSpec() && n != parent.getFirstChild())
                || (parent.isImportSpec() && n != parent.getLastChild())));
  }

  /** Whether the child node is the FINALLY block of a try. */
  static boolean isTryFinallyNode(Node parent, Node child) {
    return parent.isTry() && parent.hasXChildren(3)
        && child == parent.getLastChild();
  }

  /** Whether the node is a CATCH container BLOCK. */
  static boolean isTryCatchNodeContainer(Node n) {
    Node parent = n.getParent();
    return parent.isTry()
        && parent.getSecondChild() == n;
  }

  // TODO(tbreisacher): Add a method for detecting nodes injected as runtime libraries.
  static boolean isInSyntheticScript(Node n) {
    return n.getSourceFileName() != null && n.getSourceFileName().startsWith(" [synthetic:");
  }

  /**
   * Permanently delete the given node from the AST, as well as report
   * the related AST changes/deletions to the given compiler.
   */
  public static void deleteNode(Node n, AbstractCompiler compiler) {
    Node parent = n.getParent();
    NodeUtil.markFunctionsDeleted(n, compiler);
    n.detach();
    compiler.reportChangeToEnclosingScope(parent);
  }

  /**
   * Permanently delete the given call from the AST while maintaining a valid node structure, as
   * well as report the related AST changes to the given compiler. In some cases, this is done by
   * deleting the parent from the AST and is come cases expression is replaced by {@code
   * undefined}.
   */
  public static void deleteFunctionCall(Node n, AbstractCompiler compiler) {
    checkState(n.isCall());

    Node parent = n.getParent();
    if (parent.isExprResult()) {
      Node grandParent = parent.getParent();
      grandParent.removeChild(parent);
      parent = grandParent;
    } else {
      // Seems like part of more complex expression, fallback to replacing with no-op.
      parent.replaceChild(n, newUndefinedNode(n));
    }

    NodeUtil.markFunctionsDeleted(n, compiler);
    compiler.reportChangeToEnclosingScope(parent);
  }

  /**
   * Permanently delete all the children of the given node, including reporting changes.
   */
  public static void deleteChildren(Node n, AbstractCompiler compiler) {
    while (n.hasChildren()) {
      deleteNode(n.getFirstChild(), compiler);
    }
  }

  /**
   * Safely remove children while maintaining a valid node structure.
   * In some cases, this is done by removing the parent from the AST as well.
   */
  public static void removeChild(Node parent, Node node) {
    if (isTryFinallyNode(parent, node)) {
      if (NodeUtil.hasCatchHandler(getCatchBlock(parent))) {
        // A finally can only be removed if there is a catch.
        parent.removeChild(node);
      } else {
        // Otherwise, only its children can be removed.
        node.detachChildren();
      }
    } else if (node.isCatch()) {
      // The CATCH can can only be removed if there is a finally clause.
      Node tryNode = node.getGrandparent();
      checkState(NodeUtil.hasFinally(tryNode));
      node.detach();
    } else if (isTryCatchNodeContainer(node)) {
      // The container node itself can't be removed, but the contained CATCH
      // can if there is a 'finally' clause
      Node tryNode = node.getParent();
      checkState(NodeUtil.hasFinally(tryNode));
      node.detachChildren();
    } else if (node.isNormalBlock()) {
      // Simply empty the block.  This maintains source location and
      // "synthetic"-ness.
      node.detachChildren();
    } else if (isStatementBlock(parent)
        || isSwitchCase(node)
        || node.isMemberFunctionDef()) {
      // A statement in a block or a member function can simply be removed
      parent.removeChild(node);
    } else if (isNameDeclaration(parent) || parent.isExprResult()) {
      if (parent.hasMoreThanOneChild()) {
        parent.removeChild(node);
      } else {
        // Remove the node from the parent, so it can be reused.
        parent.removeChild(node);
        // This would leave an empty VAR, remove the VAR itself.
        removeChild(parent.getParent(), parent);
      }
    } else if (parent.isLabel()
        && node == parent.getLastChild()) {
      // Remove the node from the parent, so it can be reused.
      parent.removeChild(node);
      // A LABEL without children can not be referred to, remove it.
      removeChild(parent.getParent(), parent);
    } else if (parent.isVanillaFor()) {
      // Only Token.FOR can have an Token.EMPTY other control structure
      // need something for the condition. Others need to be replaced
      // or the structure removed.
      parent.replaceChild(node, IR.empty());
    } else if (parent.isObjectPattern()) {
      // Remove the name from the object pattern
      parent.removeChild(node);
    } else if (parent.isArrayPattern()) {
      if (node == parent.getLastChild()) {
        parent.removeChild(node);
      } else {
        parent.replaceChild(node, IR.empty());
      }
    } else if (parent.isDestructuringLhs()) {
      // Destructuring is empty so we should remove the node
      parent.removeChild(node);
      if (parent.getParent().hasChildren()) {
        // removing the destructuring could leave an empty variable declaration node, so we would
        // want to remove it from the AST
        removeChild(parent.getParent(), parent);
      }
    } else if (parent.isRest()) {
      // Rest params can only ever have one child node
      parent.detach();
    } else if (parent.isParamList()) {
      parent.removeChild(node);
    } else if (parent.isImport()) {
      // An import node must always have three child nodes. Only the first can be safely removed.
      if (node == parent.getFirstChild()) {
        parent.replaceChild(node, IR.empty());
      } else {
        throw new IllegalStateException("Invalid attempt to remove: " + node + " from " + parent);
      }
    } else {
      throw new IllegalStateException("Invalid attempt to remove node: " + node + " of " + parent);
    }
  }

  /**
   * Replace the child of a var/let/const declaration (usually a name) with a new statement.
   * Preserves the order of side effects for all the other declaration children.
   *
   * @param declChild The name node to be replaced.
   * @param newStatement The statement to replace with.
   */
  public static void replaceDeclarationChild(Node declChild, Node newStatement) {
    checkArgument(isNameDeclaration(declChild.getParent()));
    checkArgument(null == newStatement.getParent());

    Node decl = declChild.getParent();
    Node declParent = decl.getParent();
    if (decl.hasOneChild()) {
      declParent.replaceChild(decl, newStatement);
    } else if (declChild.getNext() == null) {
      decl.removeChild(declChild);
      declParent.addChildAfter(newStatement, decl);
    } else if (declChild.getPrevious() == null) {
      decl.removeChild(declChild);
      declParent.addChildBefore(newStatement, decl);
    } else {
      checkState(decl.hasMoreThanOneChild());
      Node newDecl = new Node(decl.getToken()).srcref(decl);
      for (Node after = declChild.getNext(), next; after != null; after = next) {
        next = after.getNext();
        newDecl.addChildToBack(after.detach());
      }
      decl.removeChild(declChild);
      declParent.addChildAfter(newStatement, decl);
      declParent.addChildAfter(newDecl, newStatement);
    }
  }

  /**
   * Add a finally block if one does not exist.
   */
  static void maybeAddFinally(Node tryNode) {
    checkState(tryNode.isTry());
    if (!NodeUtil.hasFinally(tryNode)) {
      tryNode.addChildToBack(IR.block().srcref(tryNode));
    }
  }

  /**
   * Merge a block with its parent block.
   * @return Whether the block was removed.
   */
  public static boolean tryMergeBlock(Node block, boolean alwaysMerge) {
    checkState(block.isNormalBlock());
    Node parent = block.getParent();
    boolean canMerge = alwaysMerge || canMergeBlock(block);
    // Try to remove the block if its parent is a block/script or if its
    // parent is label and it has exactly one child.
    if (isStatementBlock(parent) && canMerge) {
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
   * A check inside a block to see if there are const, let, class, or function declarations
   * to be safe and not hoist them into the upper block.
   * @return Whether the block can be removed
   */
  public static boolean canMergeBlock(Node block) {
    for (Node c = block.getFirstChild(); c != null; c = c.getNext()) {
      switch (c.getToken()) {
        case LABEL:
          if (canMergeBlock(c)){
            continue;
          } else {
            return false;
          }

        case CONST:
        case LET:
        case CLASS:
        case FUNCTION:
          return false;

        default:
          continue;
      }
    }
    return true;
  }

  /**
   * @param node A node
   * @return Whether the call is a NEW or CALL node.
   */
  public static boolean isCallOrNew(Node node) {
    return node.isCall() || node.isNew();
  }

  /**
   * Return a BLOCK node for the given FUNCTION node.
   */
  public static Node getFunctionBody(Node fn) {
    checkArgument(fn.isFunction(), fn);
    return fn.getLastChild();
  }


  /**
   * Is the node a var, const, let, function, or class declaration?
   * See {@link #isFunctionDeclaration}, {@link #isClassDeclaration}, and {@link #isNameDeclaration}
   */
  static boolean isDeclaration(Node n) {
    return isNameDeclaration(n) || isFunctionDeclaration(n) || isClassDeclaration(n);
  }

  /**
   * Is this node a function declaration? A function declaration is a function
   * that has a name that is added to the current scope (i.e. a function that
   * is not part of a expression; see {@link #isFunctionExpression}).
   */
  public static boolean isFunctionDeclaration(Node n) {
    // Note: There is currently one case where an unnamed function has a declaration parent.
    // `export default function() {...}`
    // In this case we consider the function to be an expression.
    return n.isFunction() && isDeclarationParent(n.getParent()) && isNamedFunction(n);
  }

  /**
   * Is this node a class or object literal member function?
   *
   * <p>examples:
   *
   * <pre><code>
   *   class C {
   *     f() {}
   *     get x() { return this.x_; }
   *     set x(v) { this.x_ = v; }
   *     [someExpr]() {}
   *   }
   *   obj = {
   *     f() {}
   *     get x() { return this.x_; }
   *     set x(v) { this.x_ = v; }
   *     [someExpr]() {}
   *   }
   * </code></pre>
   */
  public static boolean isMethodDeclaration(Node n) {
    if (n.isFunction()) {
      Node parent = n.getParent();
      switch (parent.getToken()) {
        case GETTER_DEF:
        case SETTER_DEF:
        case MEMBER_FUNCTION_DEF:
          // `({ get x() {} })`
          // `({ set x(v) {} })`
          // `({ f() {} })`
          return true;
        case COMPUTED_PROP:
          // `({ [expression]() {} })`
          // The first child is the expression, and could possibly be a function.
          return parent.getLastChild() == n;
        default:
          return false;
      }
    } else {
      return false;
    }
  }

  /**
   * see {@link #isClassDeclaration}
   */
  public static boolean isClassDeclaration(Node n) {
    return n.isClass() && isDeclarationParent(n.getParent()) && isNamedClass(n);
  }

  /**
   * Is this node a hoisted function declaration? A function declaration in the
   * scope root is hoisted to the top of the scope.
   * See {@link #isFunctionDeclaration}).
   */
  public static boolean isHoistedFunctionDeclaration(Node n) {
    if (isFunctionDeclaration(n)) {
      Node parent = n.getParent();
      return parent.isScript()
          || parent.isModuleBody()
          || parent.getParent().isFunction()
          || parent.isExport();
    }
    return false;
  }

  static boolean isBlockScopedFunctionDeclaration(Node n) {
    if (!isFunctionDeclaration(n)) {
      return false;
    }
    Node current = n.getParent();
    while (current != null) {
      switch (current.getToken()) {
        case BLOCK:
          return !current.getParent().isFunction();
        case FUNCTION:
        case SCRIPT:
        case DECLARE:
        case EXPORT:
        case MODULE_BODY:
          return false;
        default:
          checkState(current.isLabel(), current);
          current = current.getParent();
      }
    }
    return false;
  }

  static boolean isFunctionBlock(Node n) {
    return n.isNormalBlock() && n.getParent() != null && n.getParent().isFunction();
  }

  /**
   * Is a FUNCTION node a function expression?
   *
   * <p>A function expression is a function that:
   * <ul>
   *   <li>has either no name or a name that is not added to the current scope
   *   <li>AND can be manipulated as an expression
   *       (assigned to variables, passed to functions, etc.)
   *       i.e. It is not a method declaration on a class or object literal.
   * </ul>
   *
   * <p>Some examples of function expressions:
   *
   * <pre>
   * (function () {})
   * (function f() {})()
   * [ function f() {} ]
   * var f = function f() {};
   * for (function f() {};;) {}
   * export default function() {}
   * () => 1
   * </pre>
   *
   * <p>Some examples of functions that are <em>not</em> expressions:
   *
   * <pre>
   * function f() {}
   * if (x); else function f() {}
   * for (;;) { function f() {} }
   * export default function f() {}
   * ({
   *   f() {},
   *   set x(v) {},
   *   get x() {},
   *   [expr]() {}
   * })
   * class {
   *   f() {}
   *   set x(v) {}
   *   get x() {}
   *   [expr]() {}
   * }
   * </pre>
   *
   * @param n A node
   * @return Whether n is a function used within an expression.
   */
  static boolean isFunctionExpression(Node n) {
    return n.isFunction()
        && !NodeUtil.isFunctionDeclaration(n)
        && !NodeUtil.isMethodDeclaration(n);
  }

  static boolean isUnannotatedCallback(Node n) {
    JSDocInfo jsdoc = getBestJSDocInfo(n);
    return n.isFunction() && n.getParent().isCall() && n != n.getParent().getFirstChild()
        && jsdoc == null && !functionHasInlineJsdocs(n);
  }

  /**
   * @return Whether the node is both a function expression and the function is named.
   */
  static boolean isNamedFunctionExpression(Node n) {
    return NodeUtil.isFunctionExpression(n) && !n.getFirstChild().getString().isEmpty();
  }

  /**
   * see {@link #isFunctionExpression}
   *
   * @param n A node
   * @return Whether n is a class used within an expression.
   */
  static boolean isClassExpression(Node n) {
    return n.isClass() && (!isNamedClass(n) || !isDeclarationParent(n.getParent()));
  }

  /**
   * Returns whether n is a function with a nonempty name.
   * Differs from {@link #isFunctionDeclaration} because the name might in a function expression
   * and not be added to the current scope.
   *
   * Some named functions include
   * <pre>
   *   (function f() {})();
   *   export default function f() {};
   *   function f() {};
   *   var f = function f() {};
   * </pre>
   */
  static boolean isNamedFunction(Node n) {
    return n.isFunction() && isReferenceName(n.getFirstChild());
  }

  /**
   * see {@link #isNamedFunction}
   *
   * @param n A node
   * @return Whether n is a named class
   */
  static boolean isNamedClass(Node n) {
    return n.isClass() && isReferenceName(n.getFirstChild());
  }

  /**
   * Returns whether this is a bleeding function (an anonymous named function
   * that bleeds into the inner scope).
   */
  static boolean isBleedingFunctionName(Node n) {
    if (!n.isName() || n.getString().isEmpty()) {
      return false;
    }
    Node parent = n.getParent();
    return isFunctionExpression(parent) && n == parent.getFirstChild();
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
   * @return Whether a function has a reference to its own "arguments" object.
   */
  static boolean doesFunctionReferenceOwnArgumentsObject(Node fn) {
    checkArgument(fn.isFunction());
    if (fn.isArrowFunction()) {
      return false;
    }
    return referencesArgumentsHelper(fn.getLastChild());
  }

  /** @return Whether any child is a reference to the "arguments" object of the root. Effectively,
   * this includes arrow method bodies (which don't have their own) and excludes other functions
   * which shadow the "arguments" value with their own.
   */
  private static boolean referencesArgumentsHelper(Node node) {
    if (node.isName() && node.getString().equals("arguments")) {
      return true;
    }

    if (NodeUtil.isVanillaFunction(node)) {
      return false;
    }

    for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
      if (referencesArgumentsHelper(c)) {
        return true;
      }
    }

    return false;
  }

  /**
   * @return Whether node is a call to methodName.
   *    a.f(...)
   *    a['f'](...)
   */
  static boolean isObjectCallMethod(Node callNode, String methodName) {
    if (callNode.isCall()) {
      Node functionIndentifyingExpression = callNode.getFirstChild();
      if (isGet(functionIndentifyingExpression)) {
        Node last = functionIndentifyingExpression.getLastChild();
        if (last != null && last.isString()) {
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

  static boolean isGoogBind(Node n) {
    return n.isGetProp() && n.matchesQualifiedName("goog.bind");
  }

  static boolean isGoogPartial(Node n) {
    return n.isGetProp() && n.matchesQualifiedName("goog.partial");
  }

  /**
   * Does not use type info. For example, it returns false for f.bind(...)
   * because it cannot know whether f is a function.
   */
  static boolean isFunctionBind(Node expr) {
    if (!expr.isGetProp()) {
      return false;
    }
    if (isGoogBind(expr) || isGoogPartial(expr)) {
      return true;
    }
    return expr.getFirstChild().isFunction()
        && expr.getLastChild().getString().equals("bind");
  }

  /**
   * Determines whether this node is strictly on the left hand side of an assign or var
   * initialization. Notably, this does not include all L-values, only statements where the node is
   * used only as an L-value.
   *
   * @param n The node
   * @param parent Parent of the node
   * @return True if n is the left hand of an assign
   */
  static boolean isNameDeclOrSimpleAssignLhs(Node n, Node parent) {
    return
        (parent.isAssign() && parent.getFirstChild() == n) || NodeUtil.isNameDeclaration(parent);
  }

  /**
   * Determines whether this node is used as an L-value. Notice that sometimes
   * names are used as both L-values and R-values.
   *
   * <p>We treat "var x;" and "let x;" as an L-value because it's syntactically similar to
   * "var x = undefined", even though it's technically not an L-value. But it kind of makes
   * sense if you treat it as "assignment to 'undefined' at the top of the scope".
   *
   * @param n The node
   * @return True if n is an L-value.
   */
  public static boolean isLValue(Node n) {
    switch (n.getToken()) {
      case NAME:
      case GETPROP:
      case GETELEM:
        break;
      default:
        return false;
    }

    Node parent = n.getParent();
    if (parent == null) {
      return false;
    }

    switch (parent.getToken()) {
      case IMPORT_SPEC:
        return parent.getLastChild() == n;
      case VAR:
      case LET:
      case CONST:
      case REST:
      case PARAM_LIST:
      case IMPORT:
      case INC:
      case DEC:
      case CATCH:
        return true;
      case CLASS:
      case FUNCTION:
      case DEFAULT_VALUE:
      case FOR:
      case FOR_IN:
      case FOR_OF:
        return parent.getFirstChild() == n;
      case ARRAY_PATTERN:
      case STRING_KEY:
      case COMPUTED_PROP:
        return isLhsByDestructuring(n);
      default:
        return NodeUtil.isAssignmentOp(parent) && parent.getFirstChild() == n;
    }
  }

  /**
   * Determines whether this node is used as an L-value that is a declaration.
   *
   * <p><code>x = 5;</code> is an L-value but does not declare a variable.
   */
  public static boolean isDeclarationLValue(Node n) {
    boolean isLValue = isLValue(n);

    if (!isLValue) {
      return false;
    }

    Node parent = n.getParent();

    switch (parent.getToken()) {
      case IMPORT_SPEC:
      case VAR:
      case LET:
      case CONST:
      case PARAM_LIST:
      case IMPORT:
      case CATCH:
      case CLASS:
      case FUNCTION:
        return true;
      case STRING_KEY:
        return isNameDeclaration(parent.getParent().getGrandparent());
      case OBJECT_PATTERN:
      case ARRAY_PATTERN:
        return isNameDeclaration(parent.getGrandparent());
      default:
        return false;
    }
  }

  static boolean isLhsOfAssign(Node n) {
    Node parent = n.getParent();
    return parent != null && parent.isAssign() && parent.getFirstChild() == n;
  }

  public static boolean isImportedName(Node n) {
    Node parent = n.getParent();
    return parent.isImport() || (parent.isImportSpec() && parent.getLastChild() == n);
  }

  /**
   * Returns true if the node is a lhs value of a destructuring assignment For example, x in {@code
   * var [x] = [1];}, {@code var [...x] = [1];}, and {@code var {a: x} = {a: 1}} or a.b in {@code
   * ([a.b] = [1]);} or {@code ({key: a.b} = {key: 1});}
   */
  public static boolean isLhsByDestructuring(Node n) {
    switch (n.getToken()) {
      case NAME:
      case GETPROP:
      case STRING_KEY:
      case GETELEM:
        return isLhsByDestructuringHelper(n);
      default:
        return false;
    }
  }

  /**
   * Returns true if the given node is either an LHS node in a destructuring pattern or if one of
   * its descendants contains an LHS node in a destructuring pattern. For example, in {@code var {a:
   * b = 3}}}, this returns true given the NAME b or the DEFAULT_VALUE node containing b.
   */
  private static boolean isLhsByDestructuringHelper(Node n) {
    Node parent = n.getParent();
    Node grandparent = n.getGrandparent();

    switch (parent.getToken()) {
      case ARRAY_PATTERN:
        return true; // "b" in var [b] = ...

      case STRING_KEY:
        return grandparent.isObjectPattern(); // the "b" in "var {a: b} = ..."

      case OBJECT_PATTERN:
        // STRING_KEY children of object patterns are not LHS nodes, since shorthand (e.g.
        // "var {a} = ...") is normalized at parse-time. If n is not a STRING_KEY, it is
        // an OBJECT_PATTERN or a COMPUTED_PROP and contains a LHS node.
        return !n.isStringKey();

      case COMPUTED_PROP:
        if (n == parent.getSecondChild()) {
          // The first child of a COMPUTED_PROP is the property expression, not a LHS.
          // The second is the value, which in an object pattern will contain the LHS.
          return isLhsByDestructuringHelper(parent);
        }
        return false;

      case REST:
        // The only child of a REST node is the LHS.
        return isLhsByDestructuringHelper(parent);

      case DEFAULT_VALUE:
        if (n == parent.getFirstChild()) {
          // The first child of a DEFAULT_VALUE is a NAME node and a potential LHS.
          // The second child is the value, so never a LHS node.
          return isLhsByDestructuringHelper(parent);
        }
        return false;

      default:
        return false;
    }
  }

  /**
   * Determines whether a node represents an object literal key
   * (e.g. key1 in {key1: value1, key2: value2}).
   *
   * @param node A node
   */
  static boolean isObjectLitKey(Node node) {
    switch (node.getToken()) {
      case STRING_KEY:
      case GETTER_DEF:
      case SETTER_DEF:
      case MEMBER_FUNCTION_DEF:
        return true;
      default:
        break;
    }
    return false;
  }

  /**
   * Get the name of an object literal key.
   *
   * @param key A node
   */
  static String getObjectLitKeyName(Node key) {
    Node keyNode = getObjectLitKeyNode(key);
    if (keyNode != null) {
      return keyNode.getString();
    }
    throw new IllegalStateException("Unexpected node type: " + key);
  }

  /**
   * Get the Node that defines the name of an object literal key.
   *
   * @param key A node
   */
  static Node getObjectLitKeyNode(Node key) {
    switch (key.getToken()) {
      case STRING_KEY:
      case GETTER_DEF:
      case SETTER_DEF:
      case MEMBER_FUNCTION_DEF:
        return key;
      case COMPUTED_PROP:
        return key.getFirstChild().isString() ? key.getFirstChild() : null;
      default:
        break;
    }
    throw new IllegalStateException("Unexpected node type: " + key);
  }

  /**
   * Determine whether the destructuring object pattern is nested
   *
   * @param n object pattern node
   */
  static boolean isNestedObjectPattern(Node n) {
    checkState(n.isObjectPattern());
    for (Node key = n.getFirstChild(); key != null; key = key.getNext()) {
      Node value = key.getFirstChild();
      if (value != null
          && (value.isObjectLit() || value.isArrayLit() || value.isDestructuringPattern())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Determine whether the destructuring array pattern is nested
   *
   * @param n array pattern node
   */
  static boolean isNestedArrayPattern(Node n) {
    checkState(n.isArrayPattern());
    for (Node key = n.getFirstChild(); key != null; key = key.getNext()) {
      if (key.hasChildren()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Determines whether a node represents an object literal get or set key
   * (e.g. key1 in {get key1() {}, set key2(a){}).
   *
   * @param node A node
   */
  static boolean isGetOrSetKey(Node node) {
    switch (node.getToken()) {
      case GETTER_DEF:
      case SETTER_DEF:
        return true;
      default:
        break;
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
  public static String opToStr(Token operator) {
    switch (operator) {
      case BITOR:
        return "|";
      case OR:
        return "||";
      case BITXOR:
        return "^";
      case AND:
        return "&&";
      case BITAND:
        return "&";
      case SHEQ:
        return "===";
      case EQ:
        return "==";
      case NOT:
        return "!";
      case NE:
        return "!=";
      case SHNE:
        return "!==";
      case LSH:
        return "<<";
      case IN:
        return "in";
      case LE:
        return "<=";
      case LT:
        return "<";
      case URSH:
        return ">>>";
      case RSH:
        return ">>";
      case GE:
        return ">=";
      case GT:
        return ">";
      case MUL:
        return "*";
      case DIV:
        return "/";
      case MOD:
        return "%";
      case EXPONENT:
        return "**";
      case BITNOT:
        return "~";
      case ADD:
      case POS:
        return "+";
      case SUB:
      case NEG:
        return "-";
      case ASSIGN:
        return "=";
      case ASSIGN_BITOR:
        return "|=";
      case ASSIGN_BITXOR:
        return "^=";
      case ASSIGN_BITAND:
        return "&=";
      case ASSIGN_LSH:
        return "<<=";
      case ASSIGN_RSH:
        return ">>=";
      case ASSIGN_URSH:
        return ">>>=";
      case ASSIGN_ADD:
        return "+=";
      case ASSIGN_SUB:
        return "-=";
      case ASSIGN_MUL:
        return "*=";
      case ASSIGN_EXPONENT:
        return "**=";
      case ASSIGN_DIV:
        return "/=";
      case ASSIGN_MOD:
        return "%=";
      case VOID:
        return "void";
      case TYPEOF:
        return "typeof";
      case INSTANCEOF:
        return "instanceof";
      default:
        return null;
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
  static String opToStrNoFail(Token operator) {
    String res = opToStr(operator);
    if (res == null) {
      throw new Error("Unknown op " + operator);
    }
    return res;
  }

  /** @return true if n or any of its descendants are of the specified type. */
  static boolean containsType(Node node, Token type, Predicate<Node> traverseChildrenPred) {
    return has(node, new MatchNodeType(type), traverseChildrenPred);
  }

  /**
   * @return true if n or any of its descendants are of the specified type.
   */
  public static boolean containsType(Node node, Token type) {
    return containsType(node, type, Predicates.alwaysTrue());
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
      Node var = IR.var(
          IR.name(nameNode.getString())
              .srcref(nameNode))
          .srcref(nameNode);
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
    crawl_ancestors: while (null != (ancestor = ancestor.getParent())) {
      switch (ancestor.getToken()) {
        case SCRIPT:
        case MODULE_BODY:
          addingRoot = ancestor;
          break crawl_ancestors;
        case FUNCTION:
          addingRoot = ancestor.getLastChild();
          break crawl_ancestors;
        default:
          continue crawl_ancestors;
      }
    }

    // make sure that the adding root looks ok
    checkState(addingRoot.isNormalBlock() || addingRoot.isModuleBody() || addingRoot.isScript());
    checkState(!addingRoot.hasChildren() || !addingRoot.getFirstChild().isScript());
    return addingRoot;
  }

  public static Node newDeclaration(Node lhs, @Nullable Node rhs, Token declarationType) {
    if (rhs == null) {
      return IR.declaration(lhs, declarationType);
    }
    return IR.declaration(lhs, rhs, declarationType);
  }

  /**
   * Creates a node representing a qualified name.
   *
   * @param name A qualified name (e.g. "foo" or "foo.bar.baz")
   * @return A NAME or GETPROP node
   */
  public static Node newQName(
      AbstractCompiler compiler, String name) {
    int endPos = name.indexOf('.');
    if (endPos == -1) {
      return newName(compiler, name);
    }
    Node node;
    String nodeName = name.substring(0, endPos);
    if ("this".equals(nodeName)) {
      node = IR.thisNode();
    } else if ("super".equals(nodeName)) {
      node = IR.superNode();
    } else {
      node = newName(compiler, nodeName);
    }
    int startPos;
    do {
      startPos = endPos + 1;
      endPos = name.indexOf('.', startPos);
      String part = (endPos == -1 ? name.substring(startPos) : name.substring(startPos, endPos));
      Node propNode = IR.string(part);
      propNode.setLength(part.length());
      if (compiler.getCodingConvention().isConstantKey(part)) {
        propNode.putBooleanProp(Node.IS_CONSTANT_NAME, true);
      }
      int length = node.getLength() + ".".length() + part.length();
      node = IR.getprop(node, propNode);
      node.setLength(length);
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
  static Node newQName(
      AbstractCompiler compiler, String name, Node basisNode,
      String originalName) {
    Node node = newQName(compiler, name);
    useSourceInfoForNewQName(node, basisNode);
    if (!originalName.equals(node.getOriginalName())) {
      // If basisNode already had the correct original name, then it will already be set correctly.
      // Setting it again will force the QName node to have a different property list from all of
      // its children, causing greater memory consumption.
      node.setOriginalName(originalName);
    }
    return node;
  }

  /**
   * Creates a property access on the {@code context} tree.
   */
  public static Node newPropertyAccess(AbstractCompiler compiler, Node context, String name) {
    Node propNode = IR.getprop(context, IR.string(name));
    if (compiler.getCodingConvention().isConstantKey(name)) {
      propNode.putBooleanProp(Node.IS_CONSTANT_NAME, true);
    }
    return propNode;
  }

  /**
   * Creates a node representing a qualified name.
   *
   * @param name A qualified name (e.g. "foo" or "foo.bar.baz")
   * @return A VAR node, or an EXPR_RESULT node containing an ASSIGN or NAME node.
   */
  public static Node newQNameDeclaration(
      AbstractCompiler compiler, String name, Node value, JSDocInfo info) {
    return newQNameDeclaration(compiler, name, value, info, Token.VAR);
  }

  /**
   * Creates a node representing a qualified name.
   *
   * @param name A qualified name (e.g. "foo" or "foo.bar.baz")
   * @param type Must be VAR, CONST, or LET. Ignored if {@code name} is dotted.
   * @return A VAR/CONST/LET node, or an EXPR_RESULT node containing an ASSIGN or NAME node.
   */
  public static Node newQNameDeclaration(
      AbstractCompiler compiler, String name, Node value, JSDocInfo info, Token type) {
    checkState(type == Token.VAR || type == Token.LET || type == Token.CONST, type);
    Node result;
    Node nameNode = newQName(compiler, name);
    if (nameNode.isName()) {
      result =
          value == null ? IR.declaration(nameNode, type) : IR.declaration(nameNode, value, type);
      result.setJSDocInfo(info);
    } else if (value != null) {
      result = IR.exprResult(IR.assign(nameNode, value));
      result.getFirstChild().setJSDocInfo(info);
    } else {
      result = IR.exprResult(nameNode);
      result.getFirstChild().setJSDocInfo(info);
    }
    return result;
  }

  /**
   * Custom update new QName node with source info from another node.
   *
   * <p>This is very similar to {@link Node#useSourceInfoIfMissingFromForTree(Node)}, but it avoids
   * overwriting the length field of the nodes.
   * TODO(bradfordcsmith): Eliminate the need for this custom method.
   */
  private static void useSourceInfoForNewQName(Node newQName, Node basisNode) {
    if (newQName.getStaticSourceFile() == null) {
      newQName.setStaticSourceFileFrom(basisNode);
      newQName.setSourceEncodedPosition(basisNode.getSourcePosition());
    }

    if (newQName.getOriginalName() == null) {
      newQName.putProp(Node.ORIGINALNAME_PROP, basisNode.getOriginalName());
    }

    for (Node child = newQName.getFirstChild(); child != null; child = child.getNext()) {
      useSourceInfoForNewQName(child, basisNode);
    }
  }

  /**
   * Gets the root node of a qualified name. Must be either NAME, THIS or SUPER.
   */
  static Node getRootOfQualifiedName(Node qName) {
    for (Node current = qName; true; current = current.getFirstChild()) {
      if (current.isName() || current.isThis() || current.isSuper()) {
        return current;
      }
      checkState(current.isGetProp(), "Not a getprop node: ", current);
    }
  }

  static int getLengthOfQname(Node qname) {
    int result = 1;
    while (qname.isGetProp() || qname.isGetElem()) {
      result++;
      qname = qname.getFirstChild();
    }
    checkState(qname.isName());
    return result;
  }

  private static Node newName(AbstractCompiler compiler, String name) {
    Node nameNode = IR.name(name);
    nameNode.setLength(name.length());
    if (compiler.getCodingConvention().isConstant(name)) {
      nameNode.putBooleanProp(Node.IS_CONSTANT_NAME, true);
    }
    return nameNode;
  }

  /**
   * Creates a new node representing an *existing* name, copying over the source
   * location information from the basis node.
   *
   * @param name The name for the new NAME node.
   * @param srcref The node that represents the name as currently found in
   *     the AST.
   *
   * @return The node created.
   */
  static Node newName(AbstractCompiler compiler, String name, Node srcref) {
    return newName(compiler, name).srcref(srcref);
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
  static Node newName(
      AbstractCompiler compiler, String name,
      Node basisNode, String originalName) {
    Node nameNode = newName(compiler, name, basisNode);
    nameNode.setOriginalName(originalName);
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
   * Determines whether the given name is a valid variable name.
   */
  static boolean isValidSimpleName(String name) {
    return TokenStream.isJSIdentifier(name)
        && !TokenStream.isKeyword(name)
        // no Unicode escaped characters - some browsers are less tolerant
        // of Unicode characters that might be valid according to the
        // language spec.
        // Note that by this point, Unicode escapes have been converted
        // to UTF-16 characters, so we're only searching for character
        // values, not escapes.
        && isLatin(name);
  }

  @Deprecated
  public static boolean isValidQualifiedName(LanguageMode mode, String name) {
    return isValidQualifiedName(mode.toFeatureSet(), name);
  }

  /** Determines whether the given name is a valid qualified name. */
  public static boolean isValidQualifiedName(FeatureSet mode, String name) {
    if (name.endsWith(".") || name.startsWith(".")) {
      return false;
    }

    List<String> parts = Splitter.on('.').splitToList(name);
    for (String part : parts) {
      if (!isValidPropertyName(mode, part)) {
        return false;
      }
    }
    return isValidSimpleName(parts.get(0));
  }

  /**
   * Determines whether the given name can appear on the right side of the dot operator. Many
   * properties (like reserved words) cannot, in ES3.
   */
  static boolean isValidPropertyName(FeatureSet mode, String name) {
    if (isValidSimpleName(name)) {
      return true;
    } else {
      return mode.has(Feature.KEYWORDS_AS_PROPERTIES) && TokenStream.isKeyword(name);
    }
  }

  private static class VarCollector implements Visitor {
    final Map<String, Node> vars = new LinkedHashMap<>();

    @Override
    public void visit(Node n) {
      if (n.isName()) {
        Node parent = n.getParent();
        if (parent != null && parent.isVar()) {
          String name = n.getString();
          vars.putIfAbsent(name, n);
        }
      }
    }
  }

  /**
   * Retrieves vars declared in the current node tree, excluding descent scopes.
   */
  static Collection<Node> getVarsDeclaredInBranch(Node root) {
    VarCollector collector = new VarCollector();
    visitPreOrder(
        root,
        collector,
        MATCH_NOT_FUNCTION);
    return collector.vars.values();
  }

  private static void getLhsNodesHelper(Node n, List<Node> lhsNodes) {
    switch (n.getToken()) {
      case IMPORT:
        getLhsNodesHelper(n.getFirstChild(), lhsNodes);
        getLhsNodesHelper(n.getSecondChild(), lhsNodes);
        return;
      case VAR:
      case CONST:
      case LET:
      case OBJECT_PATTERN:
      case ARRAY_PATTERN:
      case PARAM_LIST:
      case IMPORT_SPECS:
        for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
          getLhsNodesHelper(child, lhsNodes);
        }
        return;
      case DESTRUCTURING_LHS:
      case DEFAULT_VALUE:
      case CATCH:
      case REST:
        getLhsNodesHelper(n.getFirstChild(), lhsNodes);
        return;
      case IMPORT_SPEC:
      case COMPUTED_PROP:
      case STRING_KEY:
        getLhsNodesHelper(n.getLastChild(), lhsNodes);
        return;
      case NAME:
      case IMPORT_STAR:
        lhsNodes.add(n);
        return;
      case GETPROP:
      case GETELEM:
        // Not valid in declarations but may appear in assignments.
        lhsNodes.add(n);
        return;
      case EMPTY:
        return;
      default:
        if (isAssignmentOp(n)) {
          getLhsNodesHelper(n.getFirstChild(), lhsNodes);
        } else {
          throw new IllegalStateException("Invalid node in lhs: " + n);
        }
    }
  }

  /** Retrieves lhs nodes declared in the current declaration or ASSIGN statement. */
  public static List<Node> findLhsNodesInNode(Node declNode) {
    checkArgument(
        isNameDeclaration(declNode)
            || declNode.isParamList()
            || isAssignmentOp(declNode)
            || declNode.isCatch()
            || declNode.isDestructuringLhs()
            || declNode.isDefaultValue()
            || declNode.isImport(),
        declNode);
    ArrayList<Node> lhsNodes = new ArrayList<>();
    getLhsNodesHelper(declNode, lhsNodes);
    return lhsNodes;
  }

  /**
   * @return {@code true} if the node is a definition with Object.defineProperties
   */
  static boolean isObjectDefinePropertiesDefinition(Node n) {
    if (!n.isCall() || !n.hasXChildren(3)) {
      return false;
    }
    Node first = n.getFirstChild();
    if (!first.isGetProp()) {
      return false;
    }
    Node prop = first.getLastChild();
    return prop.getString().equals("defineProperties")
        && isKnownGlobalObjectReference(first.getFirstChild());
  }

  private static boolean isKnownGlobalObjectReference(Node n) {
    switch (n.getToken()) {
      case NAME:
        return n.getString().equals("Object");
      case GETPROP:
        return n.matchesQualifiedName("$jscomp.global.Object")
            || n.matchesQualifiedName("$jscomp$global.Object");
      default:
        return false;
    }
  }

  /**
   * @return {@code true} if the node is a definition with Object.defineProperty
   */
  static boolean isObjectDefinePropertyDefinition(Node n) {
    return n.isCall()
        && n.hasXChildren(4)
        && n.getFirstChild().matchesQualifiedName("Object.defineProperty");
  }

  /**
   * @return A list of STRING_KEY properties defined by a Object.defineProperties(o, {...}) call
   */
  static Iterable<Node> getObjectDefinedPropertiesKeys(Node definePropertiesCall) {
    checkArgument(NodeUtil.isObjectDefinePropertiesDefinition(definePropertiesCall));
    List<Node> properties = new ArrayList<>();
    Node objectLiteral = definePropertiesCall.getLastChild();
    for (Node key : objectLiteral.children()) {
      if (!key.isStringKey()) {
        continue;
      }
      properties.add(key);
    }
    return properties;
  }

  /**
   * @return {@code true} if the node an assignment to a prototype property of
   *     some constructor.
   */
  public static boolean isPrototypePropertyDeclaration(Node n) {
    return isExprAssign(n) && isPrototypeProperty(n.getFirstFirstChild());
  }

  /**
   * @return Whether the node represents a qualified prototype property.
   */
  static boolean isPrototypeProperty(Node n) {
    if (!n.isGetProp()) {
      return false;
    }
    Node recv = n.getFirstChild();
    return recv.isGetProp() && recv.getLastChild().getString().equals("prototype");
  }

  /**
   * @return Whether the node represents a prototype method.
   */
  static boolean isPrototypeMethod(Node n) {
    if (!n.isFunction()) {
      return false;
    }
    Node assignNode = n.getParent();
    if (!assignNode.isAssign()) {
      return false;
    }
    return isPrototypePropertyDeclaration(assignNode.getParent());
  }

  static boolean isPrototypeAssignment(Node getProp) {
    if (!getProp.isGetProp()) {
      return false;
    }
    Node parent = getProp.getParent();
    return parent.isAssign() && parent.getFirstChild() == getProp
        && parent.getFirstChild().getLastChild().getString().equals("prototype");
  }

  /**
   * Determines whether this node is testing for the existence of a property.
   * If true, we will not emit warnings about a missing property.
   *
   * @param propAccess The GETPROP or GETELEM being tested.
   */
  static boolean isPropertyTest(AbstractCompiler compiler, Node propAccess) {
    Node parent = propAccess.getParent();
    switch (parent.getToken()) {
      case CALL:
        return parent.getFirstChild() != propAccess
            && compiler.getCodingConvention().isPropertyTestFunction(parent);

      case IF:
      case WHILE:
      case DO:
      case FOR:
      case FOR_IN:
        return NodeUtil.getConditionExpression(parent) == propAccess;

      case INSTANCEOF:
      case TYPEOF:
      case AND:
      case OR:
        return true;

      case NE:
      case SHNE: {
        Node other = parent.getFirstChild() == propAccess
            ? parent.getSecondChild() : parent.getFirstChild();
        return isUndefined(other);
      }

      case HOOK:
        return parent.getFirstChild() == propAccess;

      case NOT:
        return parent.getParent().isOr()
            && parent.getParent().getFirstChild() == parent;

      case CAST:
        return isPropertyTest(compiler, parent);
      default:
        break;
    }
    return false;
  }

  static boolean isPropertyAbsenceTest(Node propAccess) {
    Node parent = propAccess.getParent();
    switch (parent.getToken()) {
      case EQ:
      case SHEQ: {
        Node other = parent.getFirstChild() == propAccess
            ? parent.getSecondChild() : parent.getFirstChild();
        return isUndefined(other);
      }
      default:
        return false;
    }
  }

  /**
   * @param qName A qualified name node representing a class prototype, or a property on that
   *     prototype, e.g. foo.Bar.prototype, or foo.Bar.prototype.toString.
   * @return The class name part of a qualified prototype name, e.g. foo.Bar.
   */
  static Node getPrototypeClassName(Node qName) {
    if (!qName.isGetProp()) {
      return null;
    }
    if (qName.getLastChild().getString().equals("prototype")) {
      return qName.getFirstChild();
    }
    Node recv = qName.getFirstChild();
    if (recv.isGetProp() && recv.getLastChild().getString().equals("prototype")) {
      return recv.getFirstChild();
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
    Node node = IR.voidNode(IR.number(0));
    if (srcReferenceNode != null) {
      node.useSourceInfoFromForTree(srcReferenceNode);
    }
    return node;
  }

  /**
   * Create a VAR node containing the given name and initial value expression.
   */
  static Node newVarNode(String name, Node value) {
    Node lhs = IR.name(name);
    if (value != null) {
      lhs.srcref(value);
    }
    return newVarNode(lhs, value);
  }

  /**
   * Create a VAR node containing the given lhs (name or destructuring pattern) and
   * initial value expression.
   */
  static Node newVarNode(Node lhs, Node value) {
    if (lhs.isDestructuringPattern()) {
      checkNotNull(value);
      return IR.var(new Node(Token.DESTRUCTURING_LHS, lhs, value).srcref(lhs)).srcref(lhs);
    } else {
      checkState(lhs.isName() && !lhs.hasChildren());
      if (value != null) {
        lhs.addChildToBack(value);
      }
      return IR.var(lhs).srcref(lhs);
    }
  }

  public static Node emptyFunction() {
    return IR.function(IR.name(""), IR.paramList(), IR.block());
  }

  /**
   * A predicate for matching name nodes with the specified node.
   */
  static class MatchNameNode implements Predicate<Node>{
    final String name;

    MatchNameNode(String name){
      this.name = name;
    }

    @Override
    public boolean apply(Node n) {
      return n.isName() && n.getString().equals(name);
    }
  }

  /**
   * A predicate for matching nodes with the specified type.
   */
  static class MatchNodeType implements Predicate<Node>{
    final Token type;

    MatchNodeType(Token type){
      this.type = type;
    }

    @Override
    public boolean apply(Node n) {
      return n.getToken() == type;
    }
  }


  /**
   * A predicate for matching var, let, const, class or function declarations.
   */
  static class MatchDeclaration implements Predicate<Node> {
    @Override
    public boolean apply(Node n) {
      return isDeclaration(n);
    }
  }

  static final Predicate<Node> MATCH_NOT_FUNCTION = n -> !n.isFunction();

  static final Predicate<Node> MATCH_NOT_THIS_BINDING = n -> !NodeUtil.isVanillaFunction(n);

  /**
   * A predicate for matching statements without exiting the current scope.
   */
  static class MatchShallowStatement implements Predicate<Node>{
    @Override
    public boolean apply(Node n) {
      Node parent = n.getParent();
      return n.isRoot()
          || n.isNormalBlock()
          || (!n.isFunction()
              && (parent == null || isControlStructure(parent) || isStatementBlock(parent)));
    }
  }

  /**
   * Finds the number of times a type is referenced within the node tree.
   */
  static int getNodeTypeReferenceCount(
      Node node, Token type, Predicate<Node> traverseChildrenPred) {
    return getCount(node, new MatchNodeType(type), traverseChildrenPred);
  }

  /** Whether a simple name is referenced within the node tree. */
  static boolean isNameReferenced(Node node, String name, Predicate<Node> traverseChildrenPred) {
    return has(node, new MatchNameNode(name), traverseChildrenPred);
  }

  /**
   * Whether a simple name is referenced within the node tree.
   */
  static boolean isNameReferenced(Node node, String name) {
    return isNameReferenced(node, name, Predicates.alwaysTrue());
  }

  /**
   * Finds the number of times a simple name is referenced within the node tree.
   */
  static int getNameReferenceCount(Node node, String name) {
    return getCount(node, new MatchNameNode(name), Predicates.alwaysTrue());
  }

  /** @return Whether the predicate is true for the node or any of its descendants. */
  public static boolean has(Node node, Predicate<Node> pred, Predicate<Node> traverseChildrenPred) {
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
   * @return The number of times the predicate is true for the node
   * or any of its descendants.
   */
  public static int getCount(
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
  public static interface Visitor {
    void visit(Node node);
  }

  /** A pre-order traversal, calling Visitor.visit for each decendent. */
  public static void visitPreOrder(Node node, Visitor visitor) {
    visitPreOrder(node, visitor, Predicates.alwaysTrue());
  }

  /** A pre-order traversal, calling Visitor.visit for each child matching the predicate. */
  public static void visitPreOrder(
      Node node, Visitor visitor, Predicate<Node> traverseChildrenPred) {
    visitor.visit(node);

    if (traverseChildrenPred.apply(node)) {
      for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
        visitPreOrder(c, visitor, traverseChildrenPred);
      }
    }
  }

  /** A post-order traversal, calling Visitor.visit for each decendent. */
  public static void visitPostOrder(Node node, Visitor visitor) {
    visitPostOrder(node, visitor, Predicates.alwaysTrue());
  }

  /** A post-order traversal, calling Visitor.visit for each descendant matching the predicate. */
  public static void visitPostOrder(
      Node node, Visitor visitor, Predicate<Node> traverseChildrenPred) {
    if (traverseChildrenPred.apply(node)) {
      for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
        visitPostOrder(c, visitor, traverseChildrenPred);
      }
    }

    visitor.visit(node);
  }

  /**
   * @return Whether an EXPORT node has a from clause.
   */
  static boolean isExportFrom(Node n) {
    checkArgument(n.isExport());
    return n.hasTwoChildren();
  }

  /**
   * @return Whether a TRY node has a finally block.
   */
  static boolean hasFinally(Node n) {
    checkArgument(n.isTry());
    return n.hasXChildren(3);
  }

  /**
   * @return The BLOCK node containing the CATCH node (if any)
   * of a TRY.
   */
  static Node getCatchBlock(Node n) {
    checkArgument(n.isTry());
    return n.getSecondChild();
  }

  /**
   * @return Whether BLOCK (from a TRY node) contains a CATCH.
   * @see NodeUtil#getCatchBlock
   */
  static boolean hasCatchHandler(Node n) {
    checkArgument(n.isNormalBlock());
    return n.hasChildren() && n.getFirstChild().isCatch();
  }

  /**
   * @param fnNode The function.
   * @return The Node containing the Function parameters.
   */
  public static Node getFunctionParameters(Node fnNode) {
    checkArgument(fnNode.isFunction());
    return fnNode.getSecondChild();
  }

  /**
   * Counts the parameters of a function that are not marked optional or varargs.
   * In ES5 functions, that's all parameters, in ES6 it's a prefix of the parameters.
   * The result is an overapproximation: if a parameter is not marked as optional, it may still
   * be optional, but it doesn't have a default value, and wasn't marked as optional
   * during transpilation.
   */
  public static int getApproxRequiredArity(Node fun) {
    checkArgument(fun.isFunction());
    checkArgument(getBestJSDocInfo(fun) == null, "Expected unannotated function, found: %s", fun);
    int result = 0;
    for (Node param : fun.getSecondChild().children()) {
      if (param.isOptionalArg() || param.isVarArgs()) {
        break;
      }
      result++;
    }
    return result;
  }

  static boolean isConstantVar(Node node, @Nullable Scope scope) {
    if (isConstantName(node)) {
      return true;
    }

    if (!node.isName() || scope == null) {
      return false;
    }

    Var var = scope.getVar(node.getString());
    return var != null && (var.isInferredConst() || var.isConst());
  }

  /**
   * <p>Determines whether a variable is constant:
   * <ol>
   * <li>In Normalize, any name that matches the
   *     {@link CodingConvention#isConstant(String)} is annotated with an
   *     IS_CONSTANT_NAME property.
   * </ol>
   *
   * @param node A NAME or STRING node
   * @return True if a name node represents a constant variable
   *
   * TODO(dimvar): this method and the next two do similar but not quite identical things. Clean up
   */
  static boolean isConstantName(Node node) {
    return node.getBooleanProp(Node.IS_CONSTANT_NAME);
  }

  /** Whether the given name is constant by coding convention. */
  static boolean isConstantByConvention(
      CodingConvention convention, Node node) {
    Node parent = node.getParent();
    if (parent.isGetProp() && node == parent.getLastChild()) {
      return convention.isConstantKey(node.getString());
    } else if (isObjectLitKey(node)) {
      return convention.isConstantKey(node.getString());
    } else if (node.isName()) {
      return convention.isConstant(node.getString());
    }
    return false;
  }

  /**
   * Temporary function to determine if a node is constant
   * in the old or new world. This does not check its inputs
   * carefully because it will go away once we switch to the new
   * world.
   */
  static boolean isConstantDeclaration(
      CodingConvention convention, JSDocInfo info, Node node) {
    // TODO(b/77597706): Update this method to handle destructured declarations.
    if (node.isName() && node.getParent().isConst()) {
      return true;
    }

    if (info != null && info.isConstant()) {
      return true;
    }

    if (node.getBooleanProp(Node.IS_CONSTANT_VAR)) {
      return true;
    }

    switch (node.getToken()) {
      case NAME:
        return NodeUtil.isConstantByConvention(convention, node);
      case GETPROP:
        return node.isQualifiedName()
            && NodeUtil.isConstantByConvention(convention, node.getLastChild());
      default:
        break;
    }
    return false;
  }

  static boolean functionHasInlineJsdocs(Node fn) {
    if (!fn.isFunction()) {
      return false;
    }
    // Check inline return annotation
    if (fn.getFirstChild().getJSDocInfo() != null) {
      return true;
    }
    // Check inline parameter annotations
    Node param = fn.getSecondChild().getFirstChild();
    while (param != null) {
      if (param.getJSDocInfo() != null) {
        return true;
      }
      param = param.getNext();
    }
    return false;
  }

  /**
   * @param n The node.
   * @return The source name property on the node or its ancestors.
   */
  public static String getSourceName(Node n) {
    String sourceName = null;
    while (sourceName == null && n != null) {
      sourceName = n.getSourceFileName();
      n = n.getParent();
    }
    return sourceName;
  }

  /**
   * @param n The node.
   * @return The source name property on the node or its ancestors.
   */
  public static StaticSourceFile getSourceFile(Node n) {
    StaticSourceFile sourceName = null;
    while (sourceName == null && n != null) {
      sourceName = n.getStaticSourceFile();
      n = n.getParent();
    }
    return sourceName;
  }

  /**
   * @param n The node.
   * @return The InputId property on the node or its ancestors.
   */
  public static InputId getInputId(Node n) {
    while (n != null && !n.isScript()) {
      n = n.getParent();
    }

    return (n != null && n.isScript()) ? n.getInputId() : null;
  }

  // NOTE(dimvar): This method is to support IDEs using the compiler. If we end up
  // needing many more methods, put them all in a separate file.
  // lineNo and columNo are 1-based.
  // Column number 1 represents a cursor at the start of the line.
  public static Node getNodeByLineCol(Node ancestor, int lineNo, int columNo) {
    checkArgument(ancestor.isScript());
    Node current = ancestor;
    Node result = null;
    while (current != null) {
      int currLineNo = current.getLineno();
      checkState(current.getLineno() <= lineNo);
      Node nextSibling = current.getNext();
      if (nextSibling != null) {
        int nextSiblingLineNo = nextSibling.getLineno();
        int nextSiblingColumNo = getColumnNoBase1(nextSibling);
        if (result != null
            && lineNo == nextSiblingLineNo && columNo == nextSiblingColumNo) {
          // The cursor is in-between two nodes.
          // If just one of them is a variable, a property, or a literal, return that one.
          // Otherwise, return the node to the left of the cursor.
          if (result.hasChildren() && !nextSibling.hasChildren()) {
            return nextSibling;
          }
          return result;
        }
        // Check if the desired location is past the end of the current node,
        // and if so, continue to the siblings.
        if (lineNo > nextSiblingLineNo
            || (lineNo > currLineNo && lineNo == nextSiblingLineNo)
            || (lineNo == nextSiblingLineNo && columNo > nextSiblingColumNo)) {
          current = nextSibling;
          continue;
        }
      }
      // The desired node is either current or one of its children.
      int currColumNo = getColumnNoBase1(current);
      if (currLineNo == lineNo) {
        if (currColumNo > columNo) {
          // current starts past the desired node, return.
          return result;
        }
        if (currColumNo + current.getLength() >= columNo) {
          result = current;
        }
      }
      current = current.getFirstChild();
    }
    return result;
  }

  // This is here instead of in Node, because it's error-prone to have two methods
  // in Node for getting the column. So, we implement the method only for the specific
  // use case of getNodeByLineCol.
  private static int getColumnNoBase1(Node n) {
    return n.getCharno() + 1;
  }

  /**
   * A new CALL node with the "FREE_CALL" set based on call target.
   */
  static Node newCallNode(Node callTarget, Node... parameters) {
    boolean isFreeCall = !isGet(callTarget);
    Node call = IR.call(callTarget);
    call.putBooleanProp(Node.FREE_CALL, isFreeCall);
    for (Node parameter : parameters) {
      call.addChildToBack(parameter);
    }
    return call;
  }

  /**
   * @return Whether the result of the expression node is known to be a primitive value
   * or an object that has not yet escaped.
   */
  static boolean evaluatesToLocalValue(Node value) {
    return evaluatesToLocalValue(value, Predicates.alwaysFalse());
  }

  /**
   * @param locals A predicate to apply to unknown local values.
   * @return Whether the result of the expression node is known to be a primitive value
   * or an object that has not yet escaped.  This guarantee is different
   * than that provided by isLiteralValue (where literal values are immune to side-effects
   * if unescaped) or isImmutableValue (which can be safely aliased).
   *
   * The concept of "local values" allow for the containment of side-effect operations. For
   * example, setting a property on a local value does not produce a global side-effect.
   *
   * Note that the concept of "local value" is not deep, it does not say anything
   * about the properties of the "local value" (all class instances have "constructor" properties
   * that are not local values for instance).
   *
   * Note that this method only provides the starting state of the expression result,
   * it does not guarantee that the value is forever a local value.  If the containing
   * method has any non-local side-effect, "local values" may escape.
   */
  static boolean evaluatesToLocalValue(Node value, Predicate<Node> locals) {
    switch (value.getToken()) {
      case ASSIGN:
        // A result that is aliased by a non-local name, is the effectively the
        // same as returning a non-local name, but this doesn't matter if the
        // value is immutable.
        return NodeUtil.isImmutableValue(value.getLastChild())
            || (locals.apply(value) && evaluatesToLocalValue(value.getLastChild(), locals));
      case COMMA:
        return evaluatesToLocalValue(value.getLastChild(), locals);
      case AND:
      case OR:
        return evaluatesToLocalValue(value.getFirstChild(), locals)
            && evaluatesToLocalValue(value.getLastChild(), locals);
      case HOOK:
        return evaluatesToLocalValue(value.getSecondChild(), locals)
            && evaluatesToLocalValue(value.getLastChild(), locals);
      case THIS:
      case SUPER:
        return locals.apply(value);
      case NAME:
        return isImmutableValue(value) || locals.apply(value);
      case GETELEM:
      case GETPROP:
        // There is no information about the locality of object properties.
        return locals.apply(value);
      case CALL:
        return callHasLocalResult(value)
            || isToStringMethodCall(value)
            || locals.apply(value);
      case TAGGED_TEMPLATELIT:
        return callHasLocalResult(value) || locals.apply(value);
      case NEW:
        return newHasLocalResult(value) || locals.apply(value);
      case DELPROP:
      case INC:
      case DEC:
      case CLASS:
      case FUNCTION:
      case REGEXP:
      case EMPTY:
      case ARRAYLIT:
      case OBJECTLIT:
      case TEMPLATELIT:
        return true;
      case CAST:
        return evaluatesToLocalValue(value.getFirstChild(), locals);
      case SPREAD:
      case YIELD:
      case AWAIT:
        // TODO(johnlenz): we can do better for await if we use type information.  That is,
        // if we know the promise being awaited on is a immutable value type (string, etc)
        // we could return true here.
        return false;
      default:
        // Other op force a local value:
        //  '' + g (a local string)
        //  x -= g (x is now an local number)
        if (isAssignmentOp(value)
            || isSimpleOperator(value)
            || isImmutableValue(value)) {
          return true;
        }

        throw new IllegalStateException(
            "Unexpected expression node: " + value + "\n parent:" + value.getParent());
    }
  }

  /**
   * @return Whether the provided expression is may evaluate to 'undefined'.
   */
  static boolean mayBeUndefined(Node n) {
    return !isDefinedValue(n);
  }

  /**
   * @return Whether the provided expression is known not to evaluate to 'undefined'.
   *
   * Similar to #getKnownValueType only for 'undefined'.  This is useful for simplifying
   * default value expressions.
   */
  static boolean isDefinedValue(Node value) {
    switch (value.getToken()) {
      case ASSIGN: // Only the assigned value matters here.
      case CAST:
      case COMMA:
        return isDefinedValue(value.getLastChild());
      case AND:
      case OR:
        return isDefinedValue(value.getFirstChild())
            && isDefinedValue(value.getLastChild());
      case HOOK:
        return isDefinedValue(value.getSecondChild())
            && isDefinedValue(value.getLastChild());
      // Assume undefined leaks in this and call results.
      case CALL:
      case NEW:
      case GETELEM:
      case GETPROP:
      case TAGGED_TEMPLATELIT:
      case THIS:
      case YIELD:
      case AWAIT:
      case VOID:
        return false;
      case DELPROP:
      case INC:
      case DEC:
      case CLASS:
      case FUNCTION:
      case REGEXP:
      case EMPTY:
      case ARRAYLIT:
      case OBJECTLIT:
      case TEMPLATELIT:
      case STRING:
      case NUMBER:
      case NULL:
      case TRUE:
      case FALSE:
        return true;
      case NAME:
        String name = value.getString();
        // We assume here that programs don't change the value of the keyword
        // undefined to something other than the value undefined.
        return "Infinity".equals(name) || "NaN".equals(name);
      default:
        // Other op force a local value:
        //  '' + g (a  string)
        //  x -= g (x is now an number)
        if (isAssignmentOp(value) || isSimpleOperator(value)) {
          return true;
        }

        throw new IllegalStateException(
            "Unexpected expression node: " + value + "\n parent:" + value.getParent());
    }
  }

  /**
   * Given the first sibling, this returns the nth
   * sibling or null if no such sibling exists.
   * This is like "getChildAtIndex" but returns null for non-existent indexes.
   */
  private static Node getNthSibling(Node first, int index) {
    Node sibling = first;
    while (index != 0 && sibling != null) {
      sibling = sibling.getNext();
      index--;
    }
    return sibling;
  }

  /**
   * Given the function, this returns the nth
   * argument or null if no such parameter exists.
   */
  static Node getArgumentForFunction(Node function, int index) {
    checkState(function.isFunction());
    return getNthSibling(
        function.getSecondChild().getFirstChild(), index);
  }

  /**
   * Given the new or call, this returns the nth
   * argument of the call or null if no such argument exists.
   */
  static Node getArgumentForCallOrNew(Node call, int index) {
    checkState(isCallOrNew(call));
    return getNthSibling(call.getSecondChild(), index);
  }

  /**
   * Returns whether this is a target of a call or new.
   */
  static boolean isInvocationTarget(Node n) {
    Node parent = n.getParent();
    return parent != null && (isCallOrNew(parent) || parent.isTaggedTemplateLit())
        && parent.getFirstChild() == n;
  }

  static boolean isCallOrNewArgument(Node n) {
    Node parent = n.getParent();
    return parent != null && isCallOrNew(parent) && parent.getFirstChild() != n;
  }

  private static boolean isToStringMethodCall(Node call) {
    Node getNode = call.getFirstChild();
    if (isGet(getNode)) {
      Node propNode = getNode.getLastChild();
      return propNode.isString() && "toString".equals(propNode.getString());
    }
    return false;
  }

  /** Return declared JSDoc type for the given name declaration, or null if none present. */
  @Nullable
  public static JSTypeExpression getDeclaredTypeExpression(Node declaration) {
    checkArgument(declaration.isName() || declaration.isStringKey());
    JSDocInfo nameJsdoc = getBestJSDocInfo(declaration);
    if (nameJsdoc != null) {
      return nameJsdoc.getType();
    }
    Node parent = declaration.getParent();
    if (parent.isRest() || parent.isDefaultValue()) {
      parent = parent.getParent();
    }
    if (parent.isParamList()) {
      JSDocInfo functionJsdoc = getBestJSDocInfo(parent.getParent());
      if (functionJsdoc != null) {
        return functionJsdoc.getParameterType(declaration.getString());
      }
    }
    return null;
  }

  /** Find the best JSDoc for the given node. */
  @Nullable
  public static JSDocInfo getBestJSDocInfo(Node n) {
    Node jsdocNode = getBestJSDocInfoNode(n);
    return jsdocNode == null ? null : jsdocNode.getJSDocInfo();
  }

  @Nullable
  public static Node getBestJSDocInfoNode(Node n) {
    if (n.isExprResult()) {
      return getBestJSDocInfoNode(n.getFirstChild());
    }
    JSDocInfo info = n.getJSDocInfo();
    if (info == null) {
      Node parent = n.getParent();
      if (parent == null || n.isExprResult()) {
        return null;
      }

      if (parent.isName()) {
        return getBestJSDocInfoNode(parent);
      } else if (parent.isAssign()) {
        return getBestJSDocInfoNode(parent);
      } else if (isObjectLitKey(parent) || parent.isComputedProp()) {
        return parent;
      } else if ((parent.isFunction() || parent.isClass()) && n == parent.getFirstChild()) {
        // n is the NAME node of the function/class.
        return getBestJSDocInfoNode(parent);
      } else if (NodeUtil.isNameDeclaration(parent) && parent.hasOneChild()) {
        return parent;
      } else if ((parent.isHook() && parent.getFirstChild() != n)
          || parent.isOr()
          || parent.isAnd()
          || (parent.isComma() && parent.getFirstChild() != n)) {
        return getBestJSDocInfoNode(parent);
      }
    }
    return n;
  }

  /** Find the l-value that the given r-value is being assigned to. */
  public static Node getBestLValue(Node n) {
    Node parent = n.getParent();
    boolean isFunctionDeclaration = isFunctionDeclaration(n);
    if (isFunctionDeclaration) {
      return n.getFirstChild();
    } else if (parent.isName()) {
      return parent;
    } else if (parent.isAssign()) {
      return parent.getFirstChild();
    } else if (isObjectLitKey(parent)) {
      return parent;
    } else if ((parent.isHook() && parent.getFirstChild() != n)
        || parent.isOr()
        || parent.isAnd()
        || (parent.isComma() && parent.getFirstChild() != n)) {
      return getBestLValue(parent);
    } else if (parent.isCast()) {
      return getBestLValue(parent);
    }
    return null;
  }

  /** Gets the r-value (or initializer) of a node returned by getBestLValue. */
  public static Node getRValueOfLValue(Node n) {
    Node parent = n.getParent();
    switch (parent.getToken()) {
      case ASSIGN:
      case ASSIGN_BITOR:
      case ASSIGN_BITXOR:
      case ASSIGN_BITAND:
      case ASSIGN_LSH:
      case ASSIGN_RSH:
      case ASSIGN_URSH:
      case ASSIGN_ADD:
      case ASSIGN_SUB:
      case ASSIGN_MUL:
      case ASSIGN_EXPONENT:
      case ASSIGN_DIV:
      case ASSIGN_MOD:
        return n.getNext();
      case VAR:
      case LET:
      case CONST:
        return n.getLastChild();
      case DESTRUCTURING_LHS:
        return parent.getLastChild();
      case OBJECTLIT:
        return n.getFirstChild();
      case FUNCTION:
      case CLASS:
        return parent;
      default:
        break;
    }
    return null;
  }

  /** Get the owner of the given l-value node. */
  static Node getBestLValueOwner(@Nullable Node lValue) {
    if (lValue == null || lValue.getParent() == null) {
      return null;
    }
    if (isObjectLitKey(lValue)) {
      return getBestLValue(lValue.getParent());
    } else if (isGet(lValue)) {
      return lValue.getFirstChild();
    }

    return null;
  }

  /** Get the name of the given l-value node. */
  static String getBestLValueName(@Nullable Node lValue) {
    if (lValue == null || lValue.getParent() == null) {
      return null;
    }
    if (lValue.isMemberFunctionDef() && lValue.getParent().isClassMembers()) {
      String className = NodeUtil.getName(lValue.getGrandparent());
      if (className == null) { // Anonymous class
        return null;
      }
      String methodName = lValue.getString();
      return className + ".prototype." + methodName;
    }
    if (isObjectLitKey(lValue)) {
      Node owner = getBestLValue(lValue.getParent());
      if (owner != null) {
        String ownerName = getBestLValueName(owner);
        if (ownerName != null) {
          return ownerName + "." + getObjectLitKeyName(lValue);
        }
      }
      return null;
    }
    return lValue.getQualifiedName();
  }

  /** Gets the root of a qualified name l-value. */
  static Node getBestLValueRoot(@Nullable Node lValue) {
    if (lValue == null) {
      return null;
    }
    switch (lValue.getToken()) {
      case STRING_KEY:
        // NOTE: beware of getBestLValue returning null (or be null-permissive?)
        return getBestLValueRoot(NodeUtil.getBestLValue(lValue.getParent()));
      case GETPROP:
      case GETELEM:
        return getBestLValueRoot(lValue.getFirstChild());
      case THIS:
      case SUPER:
      case NAME:
        return lValue;
      default:
        return null;
    }
  }

  /**
   * @return true iff the result of the expression is consumed.
   */
  static boolean isExpressionResultUsed(Node expr) {
    Node parent = expr.getParent();
    switch (parent.getToken()) {
      case BLOCK:
      case EXPR_RESULT:
        return false;
      case CAST:
        return isExpressionResultUsed(parent);
      case HOOK:
      case AND:
      case OR:
        return (expr == parent.getFirstChild()) || isExpressionResultUsed(parent);
      case COMMA:
        Node grandparent = parent.getParent();
        if (grandparent.isCall() && parent == grandparent.getFirstChild()) {
          // Semantically, a direct call to eval is different from an indirect
          // call to an eval. See ECMA-262 S15.1.2.1. So it's OK for the first
          // expression to a comma to be a no-op if it's used to indirect
          // an eval. This we pretend that this is "used".
          if (expr == parent.getFirstChild()
              && parent.hasTwoChildren()
              && expr.getNext().isName()
              && "eval".equals(expr.getNext().getString())) {
            return true;
          }
        }

        return (expr == parent.getFirstChild()) ? false : isExpressionResultUsed(parent);
      case FOR:
        // Only an expression whose result is in the condition part of the
        // expression is used.
        return (parent.getSecondChild() == expr);
      default:
        break;
    }
    return true;
  }

  /**
   * @param n The expression to check.
   * @return Whether the expression is unconditionally executed only once in the
   *     containing execution scope.
   */
  static boolean isExecutedExactlyOnce(Node n) {
    inspect: do {
      Node parent = n.getParent();
      switch (parent.getToken()) {
        case IF:
        case HOOK:
        case AND:
        case OR:
          if (parent.getFirstChild() != n) {
            return false;
          }
          // other ancestors may be conditional
          continue inspect;
        case FOR:
        case FOR_IN:
          if (parent.isForIn()) {
            if (parent.getSecondChild() != n) {
              return false;
            }
          } else {
            if (parent.getFirstChild() != n) {
              return false;
            }
          }
          // other ancestors may be conditional
          continue inspect;
        case WHILE:
        case DO:
          return false;
        case TRY:
          // Consider all code under a try/catch to be conditionally executed.
          if (!hasFinally(parent) || parent.getLastChild() != n) {
            return false;
          }
          continue inspect;
        case CASE:
        case DEFAULT_CASE:
          return false;
        case SCRIPT:
        case FUNCTION:
          // Done, we've reached the scope root.
          break inspect;
        default:
          break;
      }
    } while ((n = n.getParent()) != null);
    return true;
  }

  /**
   * @return An appropriate AST node for the boolean value.
   */
  static Node booleanNode(boolean value) {
    return value ? IR.trueNode() : IR.falseNode();
  }

  /**
   * @return An appropriate AST node for the double value.
   */
  static Node numberNode(double value, Node srcref) {
    Node result;
    if (Double.isNaN(value)) {
      result = IR.name("NaN");
    } else if (value == Double.POSITIVE_INFINITY) {
      result = IR.name("Infinity");
    } else if (value == Double.NEGATIVE_INFINITY) {
      result = IR.neg(IR.name("Infinity"));
    } else {
      result = IR.number(value);
    }
    if (srcref != null) {
      result.srcrefTree(srcref);
    }
    return result;
  }

  static boolean isNaN(Node n) {
    return (n.isName() && n.getString().equals("NaN"))
        || (n.getToken() == Token.DIV
            && n.getFirstChild().isNumber()
            && n.getFirstChild().getDouble() == 0
            && n.getLastChild().isNumber()
            && n.getLastChild().getDouble() == 0);
  }

  /**
   * A change scope does not directly correspond to a language scope but is an internal
   * grouping of changes.
   *
   * @return Whether the node represents a change scope root.
   */
  static boolean isChangeScopeRoot(Node n) {
    return (n.isScript() || n.isFunction());
  }

  /**
   * @return the change scope root
   */
  @CheckReturnValue
  static Node getEnclosingChangeScopeRoot(Node n) {
    while (n != null && !isChangeScopeRoot(n)) {
      n = n.getParent();
    }
    return n;
  }


  static int countAstSizeUpToLimit(Node n, final int limit) {
    // Java doesn't allow accessing mutable local variables from another class.
    final int[] wrappedSize = {0};
    visitPreOrder(
        n,
        new Visitor() {
          @Override
          public void visit(Node n) {
            wrappedSize[0]++;
          }
        },
        new Predicate<Node>() {
          @Override
          public boolean apply(Node n) {
            return wrappedSize[0] < limit;
          }
        });
    return wrappedSize[0];
  }

  static int countAstSize(Node n) {
    int count = 1;
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      count += countAstSize(c);
    }
    return count;
  }


  static JSDocInfo createConstantJsDoc() {
    JSDocInfoBuilder builder = new JSDocInfoBuilder(false);
    builder.recordConstancy();
    return builder.build();
  }

  static int toInt32(double d) {
    int id = (int) d;
    if (id == d) {
      // This covers -0.0 as well
      return id;
    }

    if (Double.isNaN(d)
        || d == Double.POSITIVE_INFINITY
        || d == Double.NEGATIVE_INFINITY) {
      return 0;
    }

    d = (d >= 0) ? Math.floor(d) : Math.ceil(d);

    double two32 = 4294967296.0;
    d = d % two32;
    // (double)(long)d == d should hold here

    long l = (long) d;
    // returning (int)d does not work as d can be outside int range
    // but the result must always be 32 lower bits of l
    return (int) l;
  }

  static int toUInt32(double d) {
    if (Double.isNaN(d) || Double.isInfinite(d) || d == 0) {
      return 0;
    }

    d = Math.signum(d) * Math.floor(Math.abs(d));

    double two32 = 4294967296.0;
    // this ensures that d is positive
    d = ((d % two32) + two32) % two32;
    // (double)(long)d == d should hold here

    long l = (long) d;
    // returning (int)d does not work as d can be outside int range
    // but the result must always be 32 lower bits of l
    return (int) l;
  }

  private static boolean isGoogModuleCall(Node n) {
    if (isExprCall(n)) {
      Node target = n.getFirstFirstChild();
      return (target.matchesQualifiedName("goog.module"));
    }
    return false;
  }

  static boolean isModuleScopeRoot(Node n) {
    return n.isModuleBody() || isBundledGoogModuleScopeRoot(n);
  }

  private static boolean isBundledGoogModuleScopeRoot(Node n) {
    if (!n.isNormalBlock() || !n.hasChildren() || !isGoogModuleCall(n.getFirstChild())) {
      return false;
    }
    Node function = n.getParent();
    if (function == null
        || !function.isFunction()
        || !getFunctionParameters(function).hasOneChild()
        || !getFunctionParameters(function).getFirstChild().matchesQualifiedName("exports")) {
      return false;
    }
    Node call = function.getParent();
    if (!call.isCall()
        || !call.hasTwoChildren()
        || !call.getFirstChild().matchesQualifiedName("goog.loadModule")) {
      return false;
    }
    return call.getParent().isExprResult() && call.getGrandparent().isScript();
  }

  static boolean isGoogModuleDeclareLegacyNamespaceCall(Node n) {
    if (isExprCall(n)) {
      Node target = n.getFirstFirstChild();
      return (target.matchesQualifiedName("goog.module.declareLegacyNamespace"));
    }
    return false;
  }

  public static boolean isTopLevel(Node n) {
    return n.isScript() || n.isModuleBody();
  }

  /**
   * @return Whether the node is a goog.module file's SCRIPT node.
   */
  static boolean isGoogModuleFile(Node n) {
    return n.isScript() && n.hasChildren() && n.getFirstChild().isModuleBody()
        && isGoogModuleCall(n.getFirstFirstChild());
  }

  /**
   * @return Whether the node is a SCRIPT node for a goog.module that has a
   *     declareLegacyNamespace call.
   */
  static boolean isLegacyGoogModuleFile(Node n) {
    return isGoogModuleFile(n)
        && isGoogModuleDeclareLegacyNamespaceCall(n.getFirstChild().getSecondChild());
  }

  static boolean isConstructor(Node fnNode) {
    if (fnNode == null || !fnNode.isFunction()) {
      return false;
    }

    TypeI type = fnNode.getTypeI();
    JSDocInfo jsDocInfo = getBestJSDocInfo(fnNode);

    return (type != null && type.isConstructor())
        || (jsDocInfo != null && jsDocInfo.isConstructor())
        || isEs6Constructor(fnNode);
  }

  private static boolean isEs6Constructor(Node fnNode) {
    return fnNode.isFunction()
        && fnNode.getGrandparent() != null
        && fnNode.getGrandparent().isClassMembers()
        && fnNode.getParent().matchesQualifiedName("constructor");
  }

  static boolean isGetterOrSetter(Node propNode) {
    if (isGetOrSetKey(propNode)) {
      return true;
    }
    if (!propNode.isStringKey() || !propNode.getFirstChild().isFunction()) {
      return false;
    }
    String keyName = propNode.getString();
    return keyName.equals("get") || keyName.equals("set");
  }

  public static boolean isCallTo(Node n, String qualifiedName) {
    return n.isCall() && n.getFirstChild().matchesQualifiedName(qualifiedName);
  }

  static ImmutableSet<String> collectExternVariableNames(AbstractCompiler compiler, Node externs) {
    ReferenceCollectingCallback externsRefs =
        new ReferenceCollectingCallback(
            compiler,
            ReferenceCollectingCallback.DO_NOTHING_BEHAVIOR,
            new Es6SyntacticScopeCreator(compiler));
    externsRefs.process(externs);
    ImmutableSet.Builder<String> externsNames = ImmutableSet.builder();
    for (Var v : externsRefs.getAllSymbols()) {
      if (!v.isParam()) {
        externsNames.add(v.getName());
      }
    }
    return externsNames.build();
  }

  /** Recurses through a tree, marking all function nodes as changed. */
  static void markNewScopesChanged(Node node, AbstractCompiler compiler) {
    if (node.isFunction()) {
      compiler.reportChangeToChangeScope(node);
    }
    for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
      markNewScopesChanged(child, compiler);
    }
  }

  /** Recurses through a tree, marking all function nodes deleted. */
  public static void markFunctionsDeleted(Node node, AbstractCompiler compiler) {
    if (node.isFunction()) {
      compiler.reportFunctionDeleted(node);
    }

    for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
      markFunctionsDeleted(child, compiler);
    }
  }

  /** Returns the list of scope nodes which are parents of the provided list of scope nodes. */
  public static List<Node> getParentChangeScopeNodes(List<Node> scopeNodes) {
    Set<Node> parentScopeNodes = new LinkedHashSet<>(scopeNodes);
    for (Node scopeNode : scopeNodes) {
      parentScopeNodes.add(getEnclosingChangeScopeRoot(scopeNode));
    }
    return new ArrayList<>(parentScopeNodes);
  }

  /**
   * Removes any scope nodes from the provided list that are nested within some other scope node
   * also in the list. Returns the modified list.
   */
  public static List<Node> removeNestedChangeScopeNodes(List<Node> scopeNodes) {
    Set<Node> uniqueScopeNodes = new LinkedHashSet<>(scopeNodes);
    for (Node scopeNode : scopeNodes) {
      for (Node ancestor = scopeNode.getParent();
          ancestor != null;
          ancestor = ancestor.getParent()) {
        if (isChangeScopeRoot(ancestor) && uniqueScopeNodes.contains(ancestor)) {
          uniqueScopeNodes.remove(scopeNode);
          break;
        }
      }
    }
    return new ArrayList<>(uniqueScopeNodes);
  }

  static Iterable<Node> getInvocationArgsAsIterable(Node invocation){
    if (invocation.isTaggedTemplateLit()) {
      return new TemplateArgsIterable(invocation.getLastChild());
    } else {
      checkState(isCallOrNew(invocation), invocation);
      return invocation.hasOneChild() ? ImmutableList.of() : invocation.getSecondChild().siblings();
    }
  }

  /**
   * Returns the number of arguments in this invocation. For template literals it takes into
   * account the implicit first argument of ITemplateArray
   */
  static int getInvocationArgsCount(Node invocation) {
    if (invocation.isTaggedTemplateLit()) {
      Iterable<Node> args = new TemplateArgsIterable(invocation.getLastChild());
      return Iterables.size(args) + 1;
    } else {
      return invocation.getChildCount() - 1;
    }
  }

  /**
   * Represents an iterable of the children of templatelit_sub nodes of a template lit node
   * This iterable will skip over the String children of the template lit node.
   */
  private static final class TemplateArgsIterable implements Iterable<Node>{
    private final Node templateLit;

    TemplateArgsIterable(Node templateLit) {
      checkState(templateLit.isTemplateLit());
      this.templateLit = templateLit;
    }

    @Override
    public Iterator<Node> iterator() {
      return new AbstractIterator<Node>() {
        @Nullable private Node nextChild = templateLit.getFirstChild();

        @Override
        protected Node computeNext() {
          while (nextChild != null && !nextChild.isTemplateLitSub()) {
            nextChild = nextChild.getNext();
          }
          if (nextChild == null) {
            return endOfData();
          } else {
            Node result = nextChild.getFirstChild();
            nextChild = nextChild.getNext();
            return result;
          }
        }
      };
    }
  }

  /**
   * Records a mapping of names to vars of everything reachable in a function. Should only be called
   * with a function scope. Does not enter new control flow areas aka embedded functions.
   *
   * @param nameVarMap an empty map that gets populated with the keys being variable names and
   *     values being variable objects
   * @param orderedVars an empty list that gets populated with variable objects in the order that
   *     they appear in the fn
   */
  static void getAllVarsDeclaredInFunction(
      final Map<String, Var> nameVarMap,
      final List<Var> orderedVars,
      AbstractCompiler compiler,
      ScopeCreator scopeCreator,
      final Scope scope) {

    checkState(nameVarMap.isEmpty());
    checkState(orderedVars.isEmpty());
    checkState(scope.isFunctionScope(), scope);

    ScopedCallback finder =
        new ScopedCallback() {
          @Override
          public void enterScope(NodeTraversal t) {
            Scope currentScope = t.getScope();
            for (Var v : currentScope.getVarIterable()) {
              nameVarMap.put(v.getName(), v);
              orderedVars.add(v);
            }
          }

          @Override
          public void exitScope(NodeTraversal t) {}

          @Override
          public final boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
            // Don't enter any new functions
            return !n.isFunction() || n == scope.getRootNode();
          }

          @Override
          public void visit(NodeTraversal t, Node n, Node parent) {}
        };

    NodeTraversal t = new NodeTraversal(compiler, finder, scopeCreator);
    t.traverseAtScope(scope);
  }

  /** Returns true if the node is a property of an object literal. */
  public static boolean isObjLitProperty(Node node) {
    return node.isStringKey()
        || node.isGetterDef()
        || node.isSetterDef()
        || node.isMemberFunctionDef()
        || node.isComputedProp();
  }
}
