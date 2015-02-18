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

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TokenStream;
import com.google.javascript.rhino.jstype.StaticSourceFile;
import com.google.javascript.rhino.jstype.TernaryValue;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * NodeUtil contains generally useful AST utilities.
 *
 * @author nicksantos@google.com (Nick Santos)
 * @author johnlenz@google.com (John Lenz)
 */
public final class NodeUtil {
  static final long MAX_POSITIVE_INTEGER_NUMBER = 1L << 53;

  static final String JSC_PROPERTY_NAME_FN = "JSCompiler_renameProperty";

  static final char LARGEST_BASIC_LATIN = 0x7f;

  /** the set of builtin constructors that don't have side effects. */
  private static final Set<String> CONSTRUCTORS_WITHOUT_SIDE_EFFECTS =
      ImmutableSet.of(
        "Array",
        "Date",
        "Error",
        "Object",
        "RegExp",
        "XMLHttpRequest");

  // Utility class; do not instantiate.
  private NodeUtil() {}

  /**
   * Gets the boolean value of a node that represents a expression. This method
   * effectively emulates the <code>Boolean()</code> JavaScript cast function.
   * Note: unlike getPureBooleanValue this function does not return UNKNOWN
   * for expressions with side-effects.
   */
  static TernaryValue getImpureBooleanValue(Node n) {
    switch (n.getType()) {
      case Token.ASSIGN:
      case Token.COMMA:
        // For ASSIGN and COMMA the value is the value of the RHS.
        return getImpureBooleanValue(n.getLastChild());
      case Token.NOT:
        TernaryValue value = getImpureBooleanValue(n.getLastChild());
        return value.not();
      case Token.AND: {
        TernaryValue lhs = getImpureBooleanValue(n.getFirstChild());
        TernaryValue rhs = getImpureBooleanValue(n.getLastChild());
        return lhs.and(rhs);
      }
      case Token.OR:  {
        TernaryValue lhs = getImpureBooleanValue(n.getFirstChild());
        TernaryValue rhs = getImpureBooleanValue(n.getLastChild());
        return lhs.or(rhs);
      }
      case Token.HOOK:  {
        TernaryValue trueValue = getImpureBooleanValue(
            n.getFirstChild().getNext());
        TernaryValue falseValue = getImpureBooleanValue(n.getLastChild());
        if (trueValue.equals(falseValue)) {
          return trueValue;
        } else {
          return TernaryValue.UNKNOWN;
        }
      }
      case Token.ARRAYLIT:
      case Token.OBJECTLIT:
        // ignoring side-effects
        return TernaryValue.TRUE;

      case Token.VOID:
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
    switch (n.getType()) {
      case Token.STRING:
        return TernaryValue.forBoolean(n.getString().length() > 0);

      case Token.NUMBER:
        return TernaryValue.forBoolean(n.getDouble() != 0);

      case Token.NOT:
        return getPureBooleanValue(n.getLastChild()).not();

      case Token.NULL:
      case Token.FALSE:
        return TernaryValue.FALSE;

      case Token.VOID:
        if (!mayHaveSideEffects(n.getFirstChild())) {
          return TernaryValue.FALSE;
        }
        break;

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
      case Token.REGEXP:
        return TernaryValue.TRUE;

      case Token.ARRAYLIT:
      case Token.OBJECTLIT:
        if (!mayHaveSideEffects(n)) {
          return TernaryValue.TRUE;
        }
        break;
    }

    return TernaryValue.UNKNOWN;
  }

  /**
   * Gets the value of a node as a String, or null if it cannot be converted.
   * When it returns a non-null String, this method effectively emulates the
   * <code>String()</code> JavaScript cast function.
   */
  static String getStringValue(Node n) {
    // TODO(user): regex literals as well.
    switch (n.getType()) {
      case Token.STRING:
      case Token.STRING_KEY:
        return n.getString();

      case Token.NAME:
        String name = n.getString();
        if ("undefined".equals(name)
            || "Infinity".equals(name)
            || "NaN".equals(name)) {
          return name;
        }
        break;

      case Token.NUMBER:
        return getStringValue(n.getDouble());

      case Token.FALSE:
        return "false";

      case Token.TRUE:
        return "true";

      case Token.NULL:
        return "null";

      case Token.VOID:
        return "undefined";

      case Token.NOT:
        TernaryValue child = getPureBooleanValue(n.getFirstChild());
        if (child != TernaryValue.UNKNOWN) {
          return child.toBoolean(true) ? "false" : "true"; // reversed.
        }
        break;

      case Token.ARRAYLIT:
        return arrayToString(n);

      case Token.OBJECTLIT:
        return "[object Object]";
    }
    return null;
  }

  static String getStringValue(double value) {
    long longValue = (long) value;

    // Return "1" instead of "1.0"
    if (longValue == value) {
      return Long.toString(longValue);
    } else {
      return Double.toString(value);
    }
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
   */
  static Double getNumberValue(Node n) {
    switch (n.getType()) {
      case Token.TRUE:
        return 1.0;

      case Token.FALSE:
      case Token.NULL:
        return 0.0;

      case Token.NUMBER:
        return n.getDouble();

      case Token.VOID:
        if (mayHaveSideEffects(n.getFirstChild())) {
          return null;
        } else {
          return Double.NaN;
        }

      case Token.NAME:
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

      case Token.NEG:
        if (n.getChildCount() == 1 && n.getFirstChild().isName()
            && n.getFirstChild().getString().equals("Infinity")) {
          return Double.NEGATIVE_INFINITY;
        }
        return null;

      case Token.NOT:
        TernaryValue child = getPureBooleanValue(n.getFirstChild());
        if (child != TernaryValue.UNKNOWN) {
          return child.toBoolean(true) ? 0.0 : 1.0; // reversed.
        }
        break;

      case Token.STRING:
        return getStringNumberValue(n.getString());

      case Token.ARRAYLIT:
      case Token.OBJECTLIT:
        String value = getStringValue(n);
        return value != null ? getStringNumberValue(value) : null;
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
        && isStrWhiteSpaceChar(s.charAt(end - 1)) == TernaryValue.TRUE) {
      end--;
    }
    while (start < end
        && isStrWhiteSpaceChar(s.charAt(start)) == TernaryValue.TRUE) {
      start++;
    }
    return s.substring(start, end);
  }

  /**
   * Copied from Rhino's ScriptRuntime
   */
  public static TernaryValue isStrWhiteSpaceChar(int c) {
    switch (c) {
      case '\u000B': // <VT>
        return TernaryValue.UNKNOWN;  // IE says "no", ECMAScript says "yes"
      case ' ': // <SP>
      case '\n': // <LF>
      case '\r': // <CR>
      case '\t': // <TAB>
      case '\u00A0': // <NBSP>
      case '\u000C': // <FF>
      case '\u2028': // <LS>
      case '\u2029': // <PS>
      case '\uFEFF': // <BOM>
        return TernaryValue.TRUE;
      default:
        return (Character.getType(c) == Character.SPACE_SEPARATOR)
            ? TernaryValue.TRUE : TernaryValue.FALSE;
    }
  }

  /**
   * Gets the node of a class's name. This method recognizes five forms:
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
   * @param n A class node
   * @return the node best representing the class's name
   */
  static Node getClassNameNode(Node clazz) {
    Preconditions.checkState(clazz.isClass());
    return getNameNode(clazz);
  }

  static String getClassName(Node n) {
    Node nameNode = getClassNameNode(n);
    return nameNode == null ? null : nameNode.getQualifiedName();
  }

  /**
   * Gets the node of a function's name. This method recognizes five forms:
   * <ul>
   * <li>{@code function name() ...}</li>
   * <li>{@code var name = function() ...}</li>
   * <li>{@code qualified.name = function() ...}</li>
   * <li>{@code var name2 = function name1() ...}</li>
   * <li>{@code qualified.name2 = function name1() ...}</li>
   * </ul>
   * In two last cases with named function expressions, the second name is
   * returned (the variable or qualified name).
   *
   * @param n A function node
   * @return the node best representing the function's name
   */
  static Node getFunctionNameNode(Node n) {
    Preconditions.checkState(n.isFunction());
    return getNameNode(n);
  }

  static String getFunctionName(Node n) {
    Node nameNode = getFunctionNameNode(n);
    return nameNode == null ? null : nameNode.getQualifiedName();
  }

  /** Implementation for getFunctionNameNode and getClassNameNode. */
  private static Node getNameNode(Node n) {
    Node parent = n.getParent();
    switch (parent.getType()) {
      case Token.NAME:
        // var name = function() ...
        // var name2 = function name1() ...
        return parent;

      case Token.ASSIGN:
        // qualified.name = function() ...
        // qualified.name2 = function name1() ...
        return parent.getFirstChild();

      default:
        // function name() ...
        Node funNameNode = n.getFirstChild();
        // Don't return the name node for anonymous functions/classes.
        // TODO(tbreisacher): Currently we do two kinds of "empty" checks because
        // anonymous classes have an EMPTY name node while anonymous functions
        // have a STRING node with an empty string. Consider making these the same.
        return (funNameNode.isEmpty() || funNameNode.getString().isEmpty())
            ? null : funNameNode;
    }
  }

  /**
   * Gets the function's name. This method recognizes the forms:
   * <ul>
   * <li>{@code &#123;'name': function() ...&#125;}</li>
   * <li>{@code &#123;name: function() ...&#125;}</li>
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
  public static String getNearestFunctionName(Node n) {
    if (!n.isFunction()) {
      return null;
    }

    String name = getFunctionName(n);
    if (name != null) {
      return name;
    }

    // Check for the form { 'x' : function() { } }
    Node parent = n.getParent();
    switch (parent.getType()) {
      case Token.SETTER_DEF:
      case Token.GETTER_DEF:
      case Token.STRING_KEY:
        // Return the name of the literal's key.
        return parent.getString();
      case Token.NUMBER:
        return getStringValue(parent);
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
      case Token.CAST:
      case Token.NOT:
        return isImmutableValue(n.getFirstChild());
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
   * Returns true if the operator on this node is symmetric
   */
  static boolean isSymmetricOperation(Node n) {
    switch (n.getType()) {
      case Token.EQ: // equal
      case Token.NE: // not equal
      case Token.SHEQ: // exactly equal
      case Token.SHNE: // exactly not equal
      case Token.MUL: // multiply, unlike add it only works on numbers
                      // or results NaN if any of the operators is not a number
        return true;
    }
    return false;
  }

  /**
   * Returns true if the operator on this node is relational.
   * the returned set does not include the equalities.
   */
  static boolean isRelationalOperation(Node n) {
    switch (n.getType()) {
      case Token.GT: // equal
      case Token.GE: // not equal
      case Token.LT: // exactly equal
      case Token.LE: // exactly not equal
        return true;
    }
    return false;
  }

  static boolean isAssignmentTarget(Node n) {
    Node parent = n.getParent();
    if ((isAssignmentOp(parent) && parent.getFirstChild() == n)
        || parent.isInc()
        || parent.isDec()
        || (isForIn(parent) && parent.getFirstChild() == n)) {
      // If GETPROP/GETELEM is used as assignment target the object literal is
      // acting as a temporary we can't fold it here:
      //    "{a:x}.a += 1" is not "x += 1"
      return true;
    }
    return false;
  }

  /**
   * Returns the inverse of an operator if it is invertible.
   * ex. '>' ==> '<'
   */
  static int getInverseOperator(int type) {
    switch (type) {
      case Token.GT:
        return Token.LT;
      case Token.LT:
        return Token.GT;
      case Token.GE:
        return Token.LE;
      case Token.LE:
        return Token.GE;
    }
    return Token.ERROR;
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
  static boolean isLiteralValue(Node n, boolean includeFunctions) {
    switch (n.getType()) {
      case Token.CAST:
        return isLiteralValue(n.getFirstChild(), includeFunctions);

      case Token.ARRAYLIT:
        for (Node child = n.getFirstChild(); child != null;
             child = child.getNext()) {
          if ((!child.isEmpty()) && !isLiteralValue(child, includeFunctions)) {
            return false;
          }
        }
        return true;

      case Token.REGEXP:
        // Return true only if all children are const.
        for (Node child = n.getFirstChild(); child != null;
             child = child.getNext()) {
          if (!isLiteralValue(child, includeFunctions)) {
            return false;
          }
        }
        return true;

      case Token.OBJECTLIT:
        // Return true only if all values are const.
        for (Node child = n.getFirstChild(); child != null;
             child = child.getNext()) {
          if (!isLiteralValue(child.getFirstChild(), includeFunctions)) {
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
      case Token.AND:
      case Token.OR:
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

      // Unary operators are valid if the child is valid.
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
    if (!block.isBlock()) {
      return false;
    }

    for (Node n = block.getFirstChild(); n != null; n = n.getNext()) {
      if (!n.isEmpty()) {
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

  static boolean isTypedefDecl(Node n) {
    if (n.isVar()
        || n.isName() && n.getParent().isVar()
        || n.isGetProp() && n.getParent().isExprResult()) {
      JSDocInfo jsdoc = getBestJSDocInfo(n);
      return jsdoc != null && jsdoc.hasTypedefType();
    }
    return false;
  }

  static boolean isEnumDecl(Node n) {
    if (n.isVar()
        || n.isName() && n.getParent().isVar()
        || (n.isGetProp() && n.getParent().isAssign()
            && n.getParent().getParent().isExprResult())
        || (n.isAssign() && n.getParent().isExprResult())) {
      JSDocInfo jsdoc = getBestJSDocInfo(n);
      return jsdoc != null && jsdoc.hasEnumParameterType();
    }
    return false;
  }

  static boolean isAliasedNominalTypeDecl(Node n) {
    if (n.isName()) {
      n = n.getParent();
    }
    if (n.isVar() && n.getChildCount() == 1) {
      Node name = n.getFirstChild();
      Node init = name.getFirstChild();
      JSDocInfo jsdoc = getBestJSDocInfo(n);
      return jsdoc != null
          && (jsdoc.isConstructor() || jsdoc.isInterface())
          && init != null
          && init.isQualifiedName();
    }
    Node parent = n.getParent();
    if (n.isGetProp() && n.isQualifiedName()
        && parent.isAssign() && parent.getParent().isExprResult()) {
      JSDocInfo jsdoc = getBestJSDocInfo(n);
      return jsdoc != null
          && (jsdoc.isConstructor() || jsdoc.isInterface())
          && parent.getLastChild().isQualifiedName();
    }
    return false;
  }

  static Node getInitializer(Node n) {
    Preconditions.checkArgument(n.isQualifiedName() || n.isStringKey());
    switch (n.getParent().getType()) {
      case Token.ASSIGN:
        return n.getNext();
      case Token.VAR:
      case Token.OBJECTLIT:
        return n.getFirstChild();
      default:
        return null;
    }
  }

  /**
   * Returns true iff this node defines a namespace, such as goog or goog.math.
   */
  static boolean isNamespaceDecl(Node n) {
    JSDocInfo jsdoc = getBestJSDocInfo(n);
    if (jsdoc == null || !jsdoc.isConstant()
        || !jsdoc.getTypeNodes().isEmpty()) {
      return false;
    }
    Node qnameNode;
    Node initializer;
    if (n.getParent().isVar()) {
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
    switch (n.getType()) {
      // other side-effect free statements and expressions
      case Token.CAST:
      case Token.AND:
      case Token.BLOCK:
      case Token.EXPR_RESULT:
      case Token.HOOK:
      case Token.IF:
      case Token.IN:
      case Token.PARAM_LIST:
      case Token.NUMBER:
      case Token.OR:
      case Token.THIS:
      case Token.TRUE:
      case Token.FALSE:
      case Token.NULL:
      case Token.STRING:
      case Token.STRING_KEY:
      case Token.SWITCH:
      case Token.TRY:
      case Token.EMPTY:
        break;

      // Throws are by definition side effects, and yields are similar.
      case Token.THROW:
      case Token.YIELD:
        return true;

      case Token.OBJECTLIT:
        if (checkForNewObjects) {
          return true;
        }
        for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
          if (checkForStateChangeHelper(
                  c.getFirstChild(), checkForNewObjects, compiler)) {
            return true;
          }
        }
        return false;

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
   * @param callNode - constructor call node
   */
  static boolean constructorCallHasSideEffects(Node callNode) {
    return constructorCallHasSideEffects(callNode, null);
  }

  static boolean constructorCallHasSideEffects(
      Node callNode, AbstractCompiler compiler) {
    if (!callNode.isNew()) {
      throw new IllegalStateException(
          "Expected NEW node, got " + Token.name(callNode.getType()));
    }

    if (callNode.isNoSideEffectsCall()) {
      return false;
    }

    if (callNode.isOnlyModifiesArgumentsCall() &&
        allArgsUnescapedLocal(callNode)) {
      return false;
    }

    Node nameNode = callNode.getFirstChild();
    return !nameNode.isName() || !CONSTRUCTORS_WITHOUT_SIDE_EFFECTS.contains(nameNode.getString());
  }

  // A list of built-in object creation or primitive type cast functions that
  // can also be called as constructors but lack side-effects.
  // TODO(johnlenz): consider adding an extern annotation for this.
  private static final Set<String> BUILTIN_FUNCTIONS_WITHOUT_SIDEEFFECTS =
      ImmutableSet.of(
          "Object", "Array", "String", "Number", "Boolean", "RegExp", "Error");
  private static final Set<String> OBJECT_METHODS_WITHOUT_SIDEEFFECTS =
      ImmutableSet.of("toString", "valueOf");
  private static final Set<String> REGEXP_METHODS =
      ImmutableSet.of("test", "exec");
  private static final Set<String> STRING_REGEXP_METHODS =
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
    if (!callNode.isCall()) {
      throw new IllegalStateException(
          "Expected CALL node, got " + Token.name(callNode.getType()));
    }

    if (callNode.isNoSideEffectsCall()) {
      return false;
    }

    if (callNode.isOnlyModifiesArgumentsCall() &&
        allArgsUnescapedLocal(callNode)) {
      return false;
    }

    Node nameNode = callNode.getFirstChild();

    // Built-in functions with no side effects.
    if (nameNode.isName()) {
      String name = nameNode.getString();
      if (BUILTIN_FUNCTIONS_WITHOUT_SIDEEFFECTS.contains(name)) {
        return false;
      }
    } else if (nameNode.isGetProp()) {
      if (callNode.hasOneChild()
          && OBJECT_METHODS_WITHOUT_SIDEEFFECTS.contains(
                nameNode.getLastChild().getString())) {
        return false;
      }

      if (callNode.isOnlyModifiesThisCall()
          && evaluatesToLocalValue(nameNode.getFirstChild())) {
        return false;
      }

      // Math.floor has no side-effects.
      // TODO(nicksantos): This is a terrible terrible hack, until
      // I create a definitionProvider that understands namespacing.
      if (nameNode.getFirstChild().isName()) {
        if ("Math.floor".equals(nameNode.getQualifiedName())) {
          return false;
        }
      }

      if (compiler != null && !compiler.hasRegExpGlobalReferences()) {
        if (nameNode.getFirstChild().isRegExp()
            && REGEXP_METHODS.contains(nameNode.getLastChild().getString())) {
          return false;
        } else if (nameNode.getFirstChild().isString()
            && STRING_REGEXP_METHODS.contains(
                nameNode.getLastChild().getString())) {
          Node param = nameNode.getNext();
          if (param != null &&
              (param.isString() || param.isRegExp())) {
            return false;
          }
        }
      }
    }

    return true;
  }

  /**
   * @return Whether the call has a local result.
   */
  static boolean callHasLocalResult(Node n) {
    Preconditions.checkState(n.isCall());
    return (n.getSideEffectFlags() & Node.FLAG_LOCAL_RESULTS) > 0;
  }

  /**
   * @return Whether the new has a local result.
   */
  static boolean newHasLocalResult(Node n) {
    Preconditions.checkState(n.isNew());
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

    switch(n.getType()) {
      case Token.DELPROP:
      case Token.DEC:
      case Token.INC:
      case Token.YIELD:
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

  static boolean allArgsUnescapedLocal(Node callOrNew) {
    for (Node arg = callOrNew.getFirstChild().getNext();
         arg != null; arg = arg.getNext()) {
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
   * @return Whether the tree can be affected by side-effects or
   * has side-effects.
   */
  // TODO(nick): Get rid of the knownConstants argument in favor of using
  // scope with InferConsts.
  static boolean canBeSideEffected(
      Node n, Set<String> knownConstants, Scope scope) {
    switch (n.getType()) {
      case Token.YIELD:
      case Token.CALL:
      case Token.NEW:
        // Function calls or constructor can reference changed values.
        // TODO(johnlenz): Add some mechanism for determining that functions
        // are unaffected by side effects.
        return true;
      case Token.NAME:
        // Non-constant names values may have been changed.
        return !isConstantVar(n, scope)
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
      if (canBeSideEffected(c, knownConstants, scope)) {
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
      case Token.YIELD:  return 2;
      case Token.HOOK:   return 3;  // ?: operator
      case Token.OR:     return 4;
      case Token.AND:    return 5;
      case Token.BITOR:  return 6;
      case Token.BITXOR: return 7;
      case Token.BITAND: return 8;
      case Token.EQ:
      case Token.NE:
      case Token.SHEQ:
      case Token.SHNE:   return 9;
      case Token.LT:
      case Token.GT:
      case Token.LE:
      case Token.GE:
      case Token.INSTANCEOF:
      case Token.IN:     return 10;
      case Token.LSH:
      case Token.RSH:
      case Token.URSH:   return 11;
      case Token.SUB:
      case Token.ADD:    return 12;
      case Token.MUL:
      case Token.MOD:
      case Token.DIV:    return 13;
      case Token.INC:
      case Token.DEC:
      case Token.NEW:
      case Token.DELPROP:
      case Token.TYPEOF:
      case Token.VOID:
      case Token.NOT:
      case Token.BITNOT:
      case Token.POS:
      case Token.NEG:    return 14;

      case Token.CALL:
      case Token.GETELEM:
      case Token.GETPROP:
      // Data values
      case Token.ARRAYLIT:
      case Token.ARRAY_PATTERN:
      case Token.DEFAULT_VALUE:
      case Token.EMPTY:  // TODO(johnlenz): remove this.
      case Token.FALSE:
      case Token.FUNCTION:
      case Token.CLASS:
      case Token.NAME:
      case Token.NULL:
      case Token.NUMBER:
      case Token.OBJECTLIT:
      case Token.OBJECT_PATTERN:
      case Token.REGEXP:
      case Token.REST:
      case Token.SPREAD:
      case Token.STRING:
      case Token.STRING_KEY:
      case Token.THIS:
      case Token.SUPER:
      case Token.TRUE:
      case Token.TEMPLATELIT:
      // Tokens from the type declaration AST
      case Token.STRING_TYPE:
      case Token.NUMBER_TYPE:
      case Token.BOOLEAN_TYPE:
      case Token.ANY_TYPE:
      case Token.RECORD_TYPE:
      case Token.NULLABLE_TYPE:
      case Token.NAMED_TYPE:
      case Token.UNDEFINED_TYPE:
      case Token.FUNCTION_TYPE:
      case Token.REST_PARAMETER_TYPE:
        return 15;
      case Token.CAST:
        return 16;

      default:
        throw new IllegalStateException("Unknown precedence for "
            + Token.name(type) + " (type " + type + ")");
    }
  }

  static boolean isUndefined(Node n) {
    switch (n.getType()) {
      case Token.VOID:
        return true;
      case Token.NAME:
        return n.getString().equals("undefined");
    }
    return false;
  }

  static boolean isNullOrUndefined(Node n) {
    return n.isNull() || isUndefined(n);
  }

  static final Predicate<Node> IMMUTABLE_PREDICATE = new Predicate<Node>() {
    @Override
    public boolean apply(Node n) {
      return isImmutableValue(n);
    }
  };

  static boolean isImmutableResult(Node n) {
    return allResultsMatch(n, IMMUTABLE_PREDICATE);
  }

  /**
   * Apply the supplied predicate against
   * all possible result Nodes of the expression.
   */
  static boolean allResultsMatch(Node n, Predicate<Node> p) {
    switch (n.getType()) {
      case Token.CAST:
        return allResultsMatch(n.getFirstChild(), p);
      case Token.ASSIGN:
      case Token.COMMA:
        return allResultsMatch(n.getLastChild(), p);
      case Token.AND:
      case Token.OR:
        return allResultsMatch(n.getFirstChild(), p)
            && allResultsMatch(n.getLastChild(), p);
      case Token.HOOK:
        return allResultsMatch(n.getFirstChild().getNext(), p)
            && allResultsMatch(n.getLastChild(), p);
      default:
        return p.apply(n);
    }
  }

  enum ValueType {
    UNDETERMINED,
    NULL,
    VOID,
    NUMBER,
    STRING,
    BOOLEAN
    // TODO(johnlenz): Array,Object,RegExp
  }

  /**
   * Apply the supplied predicate against
   * all possible result Nodes of the expression.
   */
  static ValueType getKnownValueType(Node n) {
    switch (n.getType()) {
      case Token.CAST:
        return getKnownValueType(n.getFirstChild());
      case Token.ASSIGN:
      case Token.COMMA:
        return getKnownValueType(n.getLastChild());
      case Token.AND:
      case Token.OR:
        return and(
            getKnownValueType(n.getFirstChild()),
            getKnownValueType(n.getLastChild()));
      case Token.HOOK:
        return and(
            getKnownValueType(n.getFirstChild().getNext()),
            getKnownValueType(n.getLastChild()));

      case Token.ADD: {
        ValueType last = getKnownValueType(n.getLastChild());
        if (last == ValueType.STRING) {
          return ValueType.STRING;
        }
        ValueType first = getKnownValueType(n.getFirstChild());
        if (first == ValueType.STRING) {
          return ValueType.STRING;
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

      case Token.NAME:
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

      case Token.BITNOT:
      case Token.BITOR:
      case Token.BITXOR:
      case Token.BITAND:
      case Token.LSH:
      case Token.RSH:
      case Token.URSH:
      case Token.SUB:
      case Token.MUL:
      case Token.MOD:
      case Token.DIV:
      case Token.INC:
      case Token.DEC:
      case Token.POS:
      case Token.NEG:
      case Token.NUMBER:
        return ValueType.NUMBER;

      // Primitives
      case Token.TRUE:
      case Token.FALSE:
      // Comparisons
      case Token.EQ:
      case Token.NE:
      case Token.SHEQ:
      case Token.SHNE:
      case Token.LT:
      case Token.GT:
      case Token.LE:
      case Token.GE:
      // Queries
      case Token.IN:
      case Token.INSTANCEOF:
      // Inversion
      case Token.NOT:
      // delete operator returns a boolean.
      case Token.DELPROP:
        return ValueType.BOOLEAN;

      case Token.TYPEOF:
      case Token.STRING:
        return ValueType.STRING;

      case Token.NULL:
        return ValueType.NULL;

      case Token.VOID:
        return ValueType.VOID;

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
  static boolean isNumericResult(Node n) {
    return getKnownValueType(n) == ValueType.NUMBER;
  }

  /**
   * @return Whether the result of node evaluation is always a boolean
   */
  static boolean isBooleanResult(Node n) {
    return getKnownValueType(n) == ValueType.BOOLEAN;
  }

  /**
   * @return Whether the result of node evaluation is always a string
   */
  static boolean isStringResult(Node n) {
    return getKnownValueType(n) == ValueType.STRING;
  }

  /**
   * @return Whether the results is possibly a string.
   */
  static boolean mayBeString(Node n) {
    return mayBeString(getKnownValueType(n));
  }

  /**
   * @return Whether the results is possibly a string.
   */
  static boolean mayBeString(ValueType type) {
    switch (type) {
      case BOOLEAN:
      case NULL:
      case NUMBER:
      case VOID:
        return false;
      case UNDETERMINED:
      case STRING:
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
  static boolean isAssociative(int type) {
    switch (type) {
      case Token.MUL:
      case Token.AND:
      case Token.OR:
      case Token.BITOR:
      case Token.BITXOR:
      case Token.BITAND:
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
  static boolean isCommutative(int type) {
    switch (type) {
      case Token.MUL:
      case Token.BITOR:
      case Token.BITXOR:
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
    throw new IllegalArgumentException("Not an assignment op:" + n);
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
  static Node getEnclosingType(Node n, int type) {
    Node curr = n;
    while (curr.getType() != type) {
      curr = curr.getParent();
      if (curr == null) {
        return curr;
      }
    }
    return curr;
  }

  /**
   * Finds the class member function containing the given node.
   */
  static Node getEnclosingClassMemberFunction(Node n) {
    return getEnclosingType(n, Token.MEMBER_FUNCTION_DEF);
  }

  /**
   * Finds the class containing the given node.
   */
  static Node getEnclosingClass(Node n) {
    return getEnclosingType(n, Token.CLASS);
  }

  /**
   * Finds the function containing the given node.
   */
  static Node getEnclosingFunction(Node n) {
    return getEnclosingType(n, Token.FUNCTION);
  }

  static boolean isInFunction(Node n) {
    return getEnclosingFunction(n) != null;
  }

  static Node getEnclosingStatement(Node n) {
    Node curr = n;
    while (curr != null && !isStatement(curr)) {
      curr = curr.getParent();
    }
    return curr;
  }

  /**
   * Returns true if the shallow scope contains references to 'this' keyword
   */
  static boolean referencesThis(Node n) {
    Node start = (n.isFunction()) ? n.getLastChild() : n;
    return containsType(start, Token.THIS, MATCH_NOT_FUNCTION);
  }

  /**
   * Returns true if the current scope contains references to the 'super' keyword.
   * Note that if there are classes declared inside the current class, super calls which
   * reference those classes are not reported.
   */
  static boolean referencesSuper(Node n) {
    Node curr = n.getFirstChild();
    while (curr != null) {
      if (containsType(curr, Token.SUPER, MATCH_NOT_CLASS)) {
        return true;
      }
      curr = curr.getNext();
    }
    return false;
  }

  /**
   * Is this a GETPROP or GETELEM node?
   */
  static boolean isGet(Node n) {
    return n.isGetProp() || n.isGetElem();
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
    return n.isName() && n.getParent().isVar();
  }

  /**
   * Is this node a name declaration?
   *
   * @param n The node
   * @return True if {@code n} is VAR, LET or CONST
   */
  public static boolean isNameDeclaration(Node n) {
    return n.isVar() || n.isLet() || n.isConst();
  }

  /**
   * @param n The node
   * @return True if {@code n} is a VAR, LET or CONST that contains a
   *     destructuring pattern.
   */
  static boolean isDestructuringDeclaration(Node n) {
    if (n.isVar() || n.isLet() || n.isConst()) {
      for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
        if (c.isArrayPattern() || c.isObjectPattern()) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Is this node declaring a block-scoped variable?
   */
  static boolean isLexicalDeclaration(Node n) {
    return n.isLet() || n.isConst()
        || isFunctionDeclaration(n) || isClassDeclaration(n);
  }

  /**
   * For an assignment or variable declaration get the assigned value.
   * @return The value node representing the new value.
   */
  static Node getAssignedValue(Node n) {
    Preconditions.checkState(n.isName());
    Node parent = n.getParent();
    if (parent.isVar()) {
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
  static boolean isExprCall(Node n) {
    return n.isExprResult()
        && n.getFirstChild().isCall();
  }

  static boolean isVanillaFor(Node n) {
    return n.isFor() && n.getChildCount() == 4;
  }

  static boolean isEnhancedFor(Node n) {
    return n.isForOf() || isForIn(n);
  }

  /**
   * @return Whether the node represents a FOR-IN loop.
   */
  static boolean isForIn(Node n) {
    return n.isFor() && n.getChildCount() == 3;
  }

  /**
   * Determines whether the given node is a FOR, DO, or WHILE node.
   */
  static boolean isLoopStructure(Node n) {
    switch (n.getType()) {
      case Token.FOR:
      case Token.FOR_OF:
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
      case Token.FOR_OF:
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

      if (parent.isFunction()) {
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
      case Token.FOR_OF:
      case Token.DO:
      case Token.WHILE:
      case Token.WITH:
      case Token.IF:
      case Token.LABEL:
      case Token.TRY:
      case Token.CATCH:
      case Token.SWITCH:
      case Token.CASE:
      case Token.DEFAULT_CASE:
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
      case Token.FOR_OF:
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
      case Token.DEFAULT_CASE:
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
    return n.isScript() || n.isBlock();
  }

  /**
   * A block scope is created by a non-synthetic block node, a for loop node,
   * or a for-of loop node.
   *
   * Note: for functions, we use two separate scopes for parameters and
   * declarations in the body. We need to make sure default parameters cannot
   * reference var / function declarations in the body.
   *
   * @return Whether the node creates a block scope.
   */
  static boolean createsBlockScope(Node n) {
    switch (n.getType()) {
      case Token.BLOCK:
        Node parent = n.getParent();
        if (parent == null || parent.isCatch()) {
          return false;
        }
        Node child = n.getFirstChild();
        return child != null && !child.isScript();
      case Token.FOR:
      case Token.FOR_OF:
        return true;
    }
    return false;
  }

  /**
   * @return Whether the node is used as a statement.
   */
  static boolean isStatement(Node n) {
    return isStatementParent(n.getParent());
  }

  static boolean isStatementParent(Node parent) {
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
    return n.isCase() || n.isDefaultCase();
  }

  /**
   * @return Whether the name is a reference to a variable, function or
   *       function parameter (not a label or a empty function expression name).
   */
  static boolean isReferenceName(Node n) {
    return n.isName() && !n.getString().isEmpty();
  }

  /** Whether the child node is the FINALLY block of a try. */
  static boolean isTryFinallyNode(Node parent, Node child) {
    return parent.isTry() && parent.getChildCount() == 3
        && child == parent.getLastChild();
  }

  /** Whether the node is a CATCH container BLOCK. */
  static boolean isTryCatchNodeContainer(Node n) {
    Node parent = n.getParent();
    return parent.isTry()
        && parent.getFirstChild().getNext() == n;
  }

  /** Safely remove children while maintaining a valid node structure. */
  static void removeChild(Node parent, Node node) {
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
      Node tryNode = node.getParent().getParent();
      Preconditions.checkState(NodeUtil.hasFinally(tryNode));
      node.detachFromParent();
    } else if (isTryCatchNodeContainer(node)) {
      // The container node itself can't be removed, but the contained CATCH
      // can if there is a 'finally' clause
      Node tryNode = node.getParent();
      Preconditions.checkState(NodeUtil.hasFinally(tryNode));
      node.detachChildren();
    } else if (node.isBlock()) {
      // Simply empty the block.  This maintains source location and
      // "synthetic"-ness.
      node.detachChildren();
    } else if (isStatementBlock(parent)
        || isSwitchCase(node)) {
      // A statement in a block can simply be removed.
      parent.removeChild(node);
    } else if (parent.isVar()) {
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
    } else if (parent.isFor()
        && parent.getChildCount() == 4) {
      // Only Token.FOR can have an Token.EMPTY other control structure
      // need something for the condition. Others need to be replaced
      // or the structure removed.
      parent.replaceChild(node, IR.empty());
    } else {
      throw new IllegalStateException("Invalid attempt to remove node: " + node + " of " + parent);
    }
  }

  /**
   * Add a finally block if one does not exist.
   */
  static void maybeAddFinally(Node tryNode) {
    Preconditions.checkState(tryNode.isTry());
    if (!NodeUtil.hasFinally(tryNode)) {
      tryNode.addChildrenToBack(IR.block().srcref(tryNode));
    }
  }

  /**
   * Merge a block with its parent block.
   * @return Whether the block was removed.
   */
  static boolean tryMergeBlock(Node block) {
    Preconditions.checkState(block.isBlock());
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
   * @param node A node
   * @return Whether the call is a NEW or CALL node.
   */
  static boolean isCallOrNew(Node node) {
    return node.isCall() || node.isNew();
  }

  /**
   * Return a BLOCK node for the given FUNCTION node.
   */
  static Node getFunctionBody(Node fn) {
    Preconditions.checkArgument(fn.isFunction());
    return fn.getLastChild();
  }

  /**
   * Is this node a function declaration? A function declaration is a function
   * that has a name that is added to the current scope (i.e. a function that
   * is not part of a expression; see {@link #isFunctionExpression}).
   */
  static boolean isFunctionDeclaration(Node n) {
    return n.isFunction() && isStatement(n);
  }

  /**
   * see {@link #isClassDeclaration}
   */
  static boolean isClassDeclaration(Node n) {
    return n.isClass() && isStatement(n);
  }

  /**
   * Is this node a hoisted function declaration? A function declaration in the
   * scope root is hoisted to the top of the scope.
   * See {@link #isFunctionDeclaration}).
   */
  static boolean isHoistedFunctionDeclaration(Node n) {
    return isFunctionDeclaration(n)
        && (n.getParent().isScript()
            || n.getParent().getParent().isFunction());
  }

  static boolean isBlockScopedFunctionDeclaration(Node n) {
    if (!isFunctionDeclaration(n)) {
      return false;
    }
    Node current = n.getParent();
    while (current != null) {
      if (current.isBlock()) {
        return !current.getParent().isFunction();
      } else if (current.isFunction() || current.isScript()) {
        return false;
      } else {
        Preconditions.checkArgument(current.isLabel());
        current = current.getParent();
      }
    }
    return false;
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
   * @return Whether n is a function used within an expression.
   */
  static boolean isFunctionExpression(Node n) {
    return n.isFunction() && !isStatement(n);
  }

  /**
   * see {@link #isFunctionExpression}
   *
   * @param n A node
   * @return Whether n is a class used within an expression.
   */
  static boolean isClassExpression(Node n) {
    return n.isClass() && !isStatement(n);
  }

  /**
   * Returns whether this is a bleeding function (an anonymous named function
   * that bleeds into the inner scope).
   */
  static boolean isBleedingFunctionName(Node n) {
    return n.isName() && !n.getString().isEmpty() &&
        isFunctionExpression(n.getParent());
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
    // TODO(johnlenz): rename this function
    Preconditions.checkArgument(function.isFunction());
    return isNameReferenced(
        function.getLastChild(),
        "arguments",
        MATCH_NOT_FUNCTION);
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

  /**
   * Determines whether this node is strictly on the left hand side of an assign
   * or var initialization. Notably, this does not include all L-values, only
   * statements where the node is used only as an L-value.
   *
   * @param n The node
   * @param parent Parent of the node
   * @return True if n is the left hand of an assign
   */
  static boolean isVarOrSimpleAssignLhs(Node n, Node parent) {
    return (parent.isAssign() && parent.getFirstChild() == n) ||
           parent.isVar();
  }

  /**
   * Determines whether this node is used as an L-value. Notice that sometimes
   * names are used as both L-values and R-values.
   *
   * <p>We treat "var x;" as a pseudo-L-value, which kind of makes sense if you
   * treat it as "assignment to 'undefined' at the top of the scope". But if
   * we're honest with ourselves, it doesn't make sense, and we only do this
   * because it makes sense to treat this as syntactically similar to
   * "var x = 0;".
   *
   * @param n The node
   * @return True if n is an L-value.
   */
  public static boolean isLValue(Node n) {
    Preconditions.checkArgument(n.isName() || n.isGetProp() ||
        n.isGetElem());
    Node parent = n.getParent();
    if (parent == null) {
      return false;
    }
    return (NodeUtil.isAssignmentOp(parent) && parent.getFirstChild() == n)
        || (NodeUtil.isForIn(parent) && parent.getFirstChild() == n)
        || parent.isVar()
        || (parent.isFunction() && parent.getFirstChild() == n)
        || parent.isDec()
        || parent.isInc()
        || parent.isParamList()
        || parent.isCatch();
  }

  /**
   * Determines whether a node represents an object literal key
   * (e.g. key1 in {key1: value1, key2: value2}).
   *
   * @param node A node
   */
  static boolean isObjectLitKey(Node node) {
    switch (node.getType()) {
      case Token.STRING_KEY:
      case Token.GETTER_DEF:
      case Token.SETTER_DEF:
        return true;
    }
    return false;
  }

  /**
   * Get the name of an object literal key.
   *
   * @param key A node
   */
  static String getObjectLitKeyName(Node key) {
    switch (key.getType()) {
      case Token.STRING_KEY:
      case Token.GETTER_DEF:
      case Token.SETTER_DEF:
        return key.getString();
    }
    throw new IllegalStateException("Unexpected node type: " + key);
  }

  /**
   * Determines whether a node represents an object literal get or set key
   * (e.g. key1 in {get key1() {}, set key2(a){}).
   *
   * @param node A node
   */
  static boolean isGetOrSetKey(Node node) {
    switch (node.getType()) {
      case Token.GETTER_DEF:
      case Token.SETTER_DEF:
        return true;
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
    Preconditions.checkState(addingRoot.isBlock() ||
        addingRoot.isScript());
    Preconditions.checkState(addingRoot.getFirstChild() == null ||
        !addingRoot.getFirstChild().isScript());
    return addingRoot;
  }

  /**
   * Creates a node representing a qualified name.
   *
   * @param name A qualified name (e.g. "foo" or "foo.bar.baz")
   * @return A NAME or GETPROP node
   */
  public static Node newQName(AbstractCompiler compiler, String name) {
    int endPos = name.indexOf('.');
    if (endPos == -1) {
      return newName(compiler, name);
    }
    Node node;
    String nodeName = name.substring(0, endPos);
    if ("this".equals(nodeName)) {
      node = IR.thisNode();
    } else {
      node = newName(compiler, nodeName);
    }
    int startPos;
    do {
      startPos = endPos + 1;
      endPos = name.indexOf('.', startPos);
      String part = (endPos == -1
                     ? name.substring(startPos)
                     : name.substring(startPos, endPos));
      Node propNode = IR.string(part);
      if (compiler.getCodingConvention().isConstantKey(part)) {
        propNode.putBooleanProp(Node.IS_CONSTANT_NAME, true);
      }
      node = IR.getprop(node, propNode);
    } while (endPos != -1);

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
   * @return A NAME or GETPROP node
   */
  public static Node newQNameDeclaration(
      AbstractCompiler compiler, String name, Node value, JSDocInfo info) {
    Node result;
    Node nameNode = newQName(compiler, name);
    if (nameNode.isName()) {
      result = IR.var(nameNode, value);
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
    setDebugInformation(node, basisNode, originalName);
    return node;
  }

  /**
   * Gets the root node of a qualified name. Must be either NAME or THIS.
   */
  static Node getRootOfQualifiedName(Node qName) {
    for (Node current = qName; true;
         current = current.getFirstChild()) {
      if (current.isName() || current.isThis()) {
        return current;
      }
      Preconditions.checkState(current.isGetProp());
    }
  }

  /**
   * Sets the debug information (source file info and original name)
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

  private static Node newName(AbstractCompiler compiler, String name) {
    Node nameNode = IR.name(name);
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
    return TokenStream.isJSIdentifier(name) &&
        !TokenStream.isKeyword(name) &&
        // no Unicode escaped characters - some browsers are less tolerant
        // of Unicode characters that might be valid according to the
        // language spec.
        // Note that by this point, Unicode escapes have been converted
        // to UTF-16 characters, so we're only searching for character
        // values, not escapes.
        isLatin(name);
  }

  @Deprecated
  public static boolean isValidQualifiedName(String name) {
    return isValidQualifiedName(LanguageMode.ECMASCRIPT3, name);
  }


  /**
   * Determines whether the given name is a valid qualified name.
   */
  public static boolean isValidQualifiedName(LanguageMode mode, String name) {
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

  @Deprecated
  static boolean isValidPropertyName(String name) {
    return isValidSimpleName(name);
  }

  /**
   * Determines whether the given name can appear on the right side of
   * the dot operator. Many properties (like reserved words) cannot, in ES3.
   */
  static boolean isValidPropertyName(LanguageMode mode, String name) {
    if (isValidSimpleName(name)) {
      return true;
    } else {
      return mode.isEs5OrHigher() && TokenStream.isKeyword(name);
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
  static Collection<Node> getVarsDeclaredInBranch(Node root) {
    VarCollector collector = new VarCollector();
    visitPreOrder(
        root,
        collector,
        MATCH_NOT_FUNCTION);
    return collector.vars.values();
  }

  /**
   * @return {@code true} if the node an assignment to a prototype property of
   *     some constructor.
   */
  public static boolean isPrototypePropertyDeclaration(Node n) {
    return isExprAssign(n) &&
        isPrototypeProperty(n.getFirstChild().getFirstChild());
  }

  /**
   * @return Whether the node represents a qualified prototype property.
   */
  static boolean isPrototypeProperty(Node n) {
    if (!n.isGetProp()) {
      return false;
    }
    n = n.getFirstChild();
    while (n.isGetProp()) {
      if (n.getLastChild().getString().equals("prototype")) {
        return n.isQualifiedName();
      }
      n = n.getFirstChild();
    }
    return false;
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

  /**
   * Determines whether this node is testing for the existence of a property.
   * If true, we will not emit warnings about a missing property.
   *
   * @param propAccess The GETPROP or GETELEM being tested.
   */
  static boolean isPropertyTest(AbstractCompiler compiler, Node propAccess) {
    Node parent = propAccess.getParent();
    switch (parent.getType()) {
      case Token.CALL:
        return parent.getFirstChild() != propAccess
            && compiler.getCodingConvention().isPropertyTestFunction(parent);

      case Token.IF:
      case Token.WHILE:
      case Token.DO:
      case Token.FOR:
        return NodeUtil.getConditionExpression(parent) == propAccess;

      case Token.INSTANCEOF:
      case Token.TYPEOF:
        return true;

      case Token.AND:
      case Token.HOOK:
        return parent.getFirstChild() == propAccess;

      case Token.NOT:
        return parent.getParent().isOr()
            && parent.getParent().getFirstChild() == parent;

      case Token.CAST:
        return isPropertyTest(compiler, parent);
    }
    return false;
  }

  /**
   * @return The class name part of a qualified prototype name.
   */
  static Node getPrototypeClassName(Node qName) {
    Node cur = qName;
    while (cur.isGetProp()) {
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
    Node node = IR.voidNode(IR.number(0));
    if (srcReferenceNode != null) {
        node.copyInformationFromForTree(srcReferenceNode);
    }
    return node;
  }

  /**
   * Create a VAR node containing the given name and initial value expression.
   */
  static Node newVarNode(String name, Node value) {
    Node nodeName = IR.name(name);
    if (value != null) {
      Preconditions.checkState(value.getNext() == null);
      nodeName.addChildToBack(value);
      nodeName.srcref(value);
    }
    Node var = IR.var(nodeName).srcref(nodeName);

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

    @Override
    public boolean apply(Node n) {
      return n.isName() && n.getString().equals(name);
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

    @Override
    public boolean apply(Node n) {
      return n.getType() == type;
    }
  }


  /**
   * A predicate for matching var or function declarations.
   */
  static class MatchDeclaration implements Predicate<Node> {
    @Override
    public boolean apply(Node n) {
      return isFunctionDeclaration(n) || n.isVar();
    }
  }

  /**
   * A predicate for matching anything except function nodes.
   */
  private static class MatchNotFunction implements Predicate<Node>{
    @Override
    public boolean apply(Node n) {
      return !n.isFunction();
    }
  }

  /**
   * A predicate for matching anything except class nodes.
   */
  private static class MatchNotClass implements Predicate<Node> {
    @Override
    public boolean apply(Node n) {
      return !n.isClass();
    }
  }

  static final Predicate<Node> MATCH_NOT_FUNCTION = new MatchNotFunction();

  static final Predicate<Node> MATCH_NOT_CLASS = new MatchNotClass();

  /**
   * A predicate for matching statements without exiting the current scope.
   */
  static class MatchShallowStatement implements Predicate<Node>{
    @Override
    public boolean apply(Node n) {
      Node parent = n.getParent();
      return n.isBlock()
          || (!n.isFunction() && (parent == null
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
  public static interface Visitor {
    void visit(Node node);
  }

  /**
   * A pre-order traversal, calling Visitor.visit for each child matching
   * the predicate.
   */
  public static void visitPreOrder(Node node,
                     Visitor visitor,
                     Predicate<Node> traverseChildrenPred) {
    visitor.visit(node);

    if (traverseChildrenPred.apply(node)) {
      for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
        visitPreOrder(c, visitor, traverseChildrenPred);
      }
    }
  }

  /**
   * A post-order traversal, calling Visitor.visit for each child matching
   * the predicate.
   */
  static void visitPostOrder(Node node,
                     Visitor visitor,
                     Predicate<Node> traverseChildrenPred) {
    if (traverseChildrenPred.apply(node)) {
      for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
        visitPostOrder(c, visitor, traverseChildrenPred);
      }
    }

    visitor.visit(node);
  }

  /**
   * @return Whether a TRY node has a finally block.
   */
  static boolean hasFinally(Node n) {
    Preconditions.checkArgument(n.isTry());
    return n.getChildCount() == 3;
  }

  /**
   * @return The BLOCK node containing the CATCH node (if any)
   * of a TRY.
   */
  static Node getCatchBlock(Node n) {
    Preconditions.checkArgument(n.isTry());
    return n.getFirstChild().getNext();
  }

  /**
   * @return Whether BLOCK (from a TRY node) contains a CATCH.
   * @see NodeUtil#getCatchBlock
   */
  static boolean hasCatchHandler(Node n) {
    Preconditions.checkArgument(n.isBlock());
    return n.hasChildren() && n.getFirstChild().isCatch();
  }

  /**
    * @param fnNode The function.
    * @return The Node containing the Function parameters.
    */
  public static Node getFunctionParameters(Node fnNode) {
    // Function NODE: [ FUNCTION -> NAME, LP -> ARG1, ARG2, ... ]
    Preconditions.checkArgument(fnNode.isFunction());
    return fnNode.getFirstChild().getNext();
  }

  static boolean hasConstAnnotation(Node node) {
    JSDocInfo jsdoc = getBestJSDocInfo(node);
    return jsdoc != null && jsdoc.isConstant() && !jsdoc.isDefine();
  }

  static boolean isConstantVar(Node node, Scope scope) {
    if (isConstantName(node)) {
      return true;
    }

    if (!node.isName() || scope == null) {
      return false;
    }

    Scope.Var var = scope.getVar(node.getString());
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
    if (info != null && info.isConstant()) {
      return true;
    }

    if (node.getBooleanProp(Node.IS_CONSTANT_VAR)) {
      return true;
    }

    switch (node.getType()) {
      case Token.NAME:
        return NodeUtil.isConstantByConvention(convention, node);
      case Token.GETPROP:
        return node.isQualifiedName()
            && NodeUtil.isConstantByConvention(convention, node.getLastChild());
    }
    return false;
  }

  /**
   * Get the JSDocInfo for a function.
   */
  public static JSDocInfo getFunctionJSDocInfo(Node n) {
    Preconditions.checkState(n.isFunction());
    JSDocInfo fnInfo = n.getJSDocInfo();
    if (fnInfo == null && NodeUtil.isFunctionExpression(n)) {
      // Look for the info on other nodes.
      Node parent = n.getParent();
      if (parent.isAssign()) {
        // on ASSIGNs
        fnInfo = parent.getJSDocInfo();
      } else if (parent.isName()) {
        // on var NAME = function() { ... };
        fnInfo = parent.getParent().getJSDocInfo();
      }
    }
    return fnInfo;
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
    Node param = fn.getChildAtIndex(1).getFirstChild();
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
      case Token.CAST:
        return evaluatesToLocalValue(value.getFirstChild(), locals);
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
        return callHasLocalResult(value)
            || isToStringMethodCall(value)
            || locals.apply(value);
      case Token.NEW:
        return newHasLocalResult(value)
               || locals.apply(value);
      case Token.FUNCTION:
      case Token.REGEXP:
      case Token.ARRAYLIT:
      case Token.OBJECTLIT:
        // Literals objects with non-literal children are allowed.
        return true;
      case Token.DELPROP:
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
    Preconditions.checkState(function.isFunction());
    return getNthSibling(
        function.getFirstChild().getNext().getFirstChild(), index);
  }

  /**
   * Given the new or call, this returns the nth
   * argument of the call or null if no such argument exists.
   */
  static Node getArgumentForCallOrNew(Node call, int index) {
    Preconditions.checkState(isCallOrNew(call));
    return getNthSibling(
      call.getFirstChild().getNext(), index);
  }

  /**
   * Returns whether this is a target of a call or new.
   */
  static boolean isCallOrNewTarget(Node target) {
    Node parent = target.getParent();
    return parent != null
        && NodeUtil.isCallOrNew(parent)
        && parent.getFirstChild() == target;
  }

  private static boolean isToStringMethodCall(Node call) {
    Node getNode = call.getFirstChild();
    if (isGet(getNode)) {
      Node propNode = getNode.getLastChild();
      return propNode.isString() && "toString".equals(propNode.getString());
    }
    return false;
  }

  /** Find the best JSDoc for the given node. */
  @Nullable
  public static JSDocInfo getBestJSDocInfo(Node n) {
    JSDocInfo info = n.getJSDocInfo();
    if (info == null) {
      Node parent = n.getParent();
      if (parent == null) {
        return null;
      }

      if (parent.isName()) {
        return getBestJSDocInfo(parent);
      } else if (parent.isAssign()) {
        return getBestJSDocInfo(parent);
      } else if (isObjectLitKey(parent)) {
        return parent.getJSDocInfo();
      } else if (parent.isFunction()) {
        // FUNCTION may be inside ASSIGN
        return getBestJSDocInfo(parent);
      } else if (parent.isVar() && parent.hasOneChild()) {
        return parent.getJSDocInfo();
      } else if ((parent.isHook() && parent.getFirstChild() != n) ||
                 parent.isOr() ||
                 parent.isAnd() ||
                 (parent.isComma() && parent.getFirstChild() != n)) {
        return getBestJSDocInfo(parent);
      } else if (parent.isCast()) {
        return parent.getJSDocInfo();
      }
    }
    return info;
  }

  /** Find the l-value that the given r-value is being assigned to. */
  static Node getBestLValue(Node n) {
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
    } else if (
        (parent.isHook() && parent.getFirstChild() != n) ||
        parent.isOr() ||
        parent.isAnd() ||
        (parent.isComma() && parent.getFirstChild() != n)) {
      return getBestLValue(parent);
    } else if (parent.isCast()) {
      return getBestLValue(parent);
    }
    return null;
  }

  /** Gets the r-value of a node returned by getBestLValue. */
  static Node getRValueOfLValue(Node n) {
    Node parent = n.getParent();
    switch (parent.getType()) {
      case Token.ASSIGN:
        return n.getNext();
      case Token.VAR:
        return n.getFirstChild();
      case Token.FUNCTION:
        return parent;
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

  /**
   * @return false iff the result of the expression is not consumed.
   */
  static boolean isExpressionResultUsed(Node expr) {
    // TODO(johnlenz): consider sharing some code with trySimpleUnusedResult.
    Node parent = expr.getParent();
    switch (parent.getType()) {
      case Token.BLOCK:
      case Token.EXPR_RESULT:
        return false;
      case Token.CAST:
        return isExpressionResultUsed(parent);
      case Token.HOOK:
      case Token.AND:
      case Token.OR:
        return (expr == parent.getFirstChild()) || isExpressionResultUsed(parent);
      case Token.COMMA:
        Node gramps = parent.getParent();
        if (gramps.isCall() &&
            parent == gramps.getFirstChild()) {
          // Semantically, a direct call to eval is different from an indirect
          // call to an eval. See ECMA-262 S15.1.2.1. So it's OK for the first
          // expression to a comma to be a no-op if it's used to indirect
          // an eval. This we pretend that this is "used".
          if (expr == parent.getFirstChild() &&
              parent.getChildCount() == 2 &&
              expr.getNext().isName() &&
              "eval".equals(expr.getNext().getString())) {
            return true;
          }
        }

        return (expr == parent.getFirstChild())
            ? false : isExpressionResultUsed(parent);
      case Token.FOR:
        if (!NodeUtil.isForIn(parent)) {
          // Only an expression whose result is in the condition part of the
          // expression is used.
          return (parent.getChildAtIndex(1) == expr);
        }
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
      switch (parent.getType()) {
        case Token.IF:
        case Token.HOOK:
        case Token.AND:
        case Token.OR:
          if (parent.getFirstChild() != n) {
            return false;
          }
          // other ancestors may be conditional
          continue inspect;
        case Token.FOR:
          if (NodeUtil.isForIn(parent)) {
            if (parent.getChildAtIndex(1) != n) {
              return false;
            }
          } else {
            if (parent.getFirstChild() != n) {
              return false;
            }
          }
          // other ancestors may be conditional
          continue inspect;
        case Token.WHILE:
        case Token.DO:
          return false;
        case Token.TRY:
          // Consider all code under a try/catch to be conditionally executed.
          if (!hasFinally(parent) || parent.getLastChild() != n) {
            return false;
          }
          continue inspect;
        case Token.CASE:
        case Token.DEFAULT_CASE:
          return false;
        case Token.SCRIPT:
        case Token.FUNCTION:
          // Done, we've reached the scope root.
          break inspect;
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
    return (n.isName() && n.getString().equals("NaN")) || (n.getType() == Token.DIV
               && n.getFirstChild().isNumber() && n.getFirstChild().getDouble() == 0
               && n.getLastChild().isNumber() && n.getLastChild().getDouble() == 0);
  }

  /**
   * Given an AST and its copy, map the root node of each scope of main to the
   * corresponding root node of clone
   */
  public static Map<Node, Node> mapMainToClone(Node main, Node clone) {
    Preconditions.checkState(main.isEquivalentTo(clone));
    Map<Node, Node> mtoc = new HashMap<>();
    mtoc.put(main, clone);
    mtocHelper(mtoc, main, clone);
    return mtoc;
  }

  private static void mtocHelper(Map<Node, Node> map, Node main, Node clone) {
    if (main.isFunction()) {
      map.put(main, clone);
    }
    Node mchild = main.getFirstChild(), cchild = clone.getFirstChild();
    while (mchild != null) {
      mtocHelper(map, mchild, cchild);
      mchild = mchild.getNext();
      cchild = cchild.getNext();
    }
  }

  /** Checks that the scope roots marked as changed have indeed changed */
  public static void verifyScopeChanges(Map<Node, Node> map,
      Node main, boolean verifyUnchangedNodes,
      AbstractCompiler compiler) {
    // compiler is passed only to call compiler.toSource during debugging to see
    // mismatches in scopes

    // If verifyUnchangedNodes is false, we are comparing the initial AST to the
    // final AST. Don't check unmarked nodes b/c they may have been changed by
    // non-loopable passes.
    // If verifyUnchangedNodes is true, we are comparing the ASTs before & after
    // a pass. Check all scope roots.
    final Map<Node, Node> mtoc = map;
    final boolean checkUnchanged = verifyUnchangedNodes;
    Node clone = mtoc.get(main);
    if (main.getChangeTime() > clone.getChangeTime()) {
      Preconditions.checkState(!isEquivalentToExcludingFunctions(main, clone));
    } else if (checkUnchanged) {
      Preconditions.checkState(isEquivalentToExcludingFunctions(main, clone));
    }
    visitPreOrder(main,
        new Visitor() {
          @Override
          public void visit(Node n) {
            if (n.isFunction() && mtoc.containsKey(n)) {
              Node clone = mtoc.get(n);
              if (n.getChangeTime() > clone.getChangeTime()) {
                Preconditions.checkState(
                    !isEquivalentToExcludingFunctions(n, clone));
              } else if (checkUnchanged) {
                Preconditions.checkState(
                    isEquivalentToExcludingFunctions(n, clone));
              }
            }
          }
        },
        Predicates.<Node>alwaysTrue());
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

  /**
   * @return Whether the two node are equivalent while ignoring
   * differences any descendant functions differences.
   */
  private static boolean isEquivalentToExcludingFunctions(
      Node thisNode, Node thatNode) {
    if (thisNode == null || thatNode == null) {
      return thisNode == null && thatNode == null;
    }
    if (!thisNode.isEquivalentToShallow(thatNode)) {
      return false;
    }
    if (thisNode.getChildCount() != thatNode.getChildCount()) {
      return false;
    }
    Node thisChild = thisNode.getFirstChild();
    Node thatChild = thatNode.getFirstChild();
    while (thisChild != null && thatChild != null) {
      if (thisChild.isFunction()) {
        //  don't compare function name, parameters or bodies.
        return thatChild.isFunction();
      }
      if (!isEquivalentToExcludingFunctions(thisChild, thatChild)) {
        return false;
      }
      thisChild = thisChild.getNext();
      thatChild = thatChild.getNext();
    }
    return true;
  }

  static JSDocInfo createConstantJsDoc() {
    JSDocInfoBuilder builder = new JSDocInfoBuilder(false);
    builder.recordConstancy();
    return builder.build(null);
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
    d = Math.IEEEremainder(d, two32);
    // (double)(long)d == d should hold here

    long l = (long) d;
    // returning (int)d does not work as d can be outside int range
    // but the result must always be 32 lower bits of l
    return (int) l;
  }
}
