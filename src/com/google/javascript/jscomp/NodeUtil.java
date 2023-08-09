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
import static com.google.common.collect.Sets.immutableEnumSet;
import static com.google.javascript.jscomp.base.JSCompDoubles.ecmascriptToInt32;
import static com.google.javascript.jscomp.base.JSCompDoubles.isAtLeastIntegerPrecision;
import static com.google.javascript.jscomp.base.JSCompDoubles.isEitherZero;
import static com.google.javascript.jscomp.base.JSCompDoubles.isExactInt64;
import static com.google.javascript.jscomp.base.JSCompDoubles.isNegative;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.errorprone.annotations.InlineMe;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.base.Tri;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.jscomp.parsing.ParsingUtil;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.QualifiedName;
import com.google.javascript.rhino.StaticSourceFile;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TokenStream;
import com.google.javascript.rhino.TokenUtil;
import com.google.javascript.rhino.dtoa.DToA;
import com.google.javascript.rhino.jstype.JSType;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.jspecify.nullness.Nullable;

/** NodeUtil contains generally useful AST utilities. */
public final class NodeUtil {

  // Value of JavaScript's Number.MAX_SAFE_INTEGER
  static final long MAX_POSITIVE_INTEGER_NUMBER = (1L << 53) - 1;

  static final String JSC_PROPERTY_NAME_FN = "JSCompiler_renameProperty";

  static final char LARGEST_BASIC_LATIN = 0x7f;

  private static final QualifiedName GOOG_MODULE_DECLARE_LEGACY_NAMESPACE =
      QualifiedName.of("goog.module.declareLegacyNamespace");

  private static final QualifiedName GOOG_SET_TEST_ONLY = QualifiedName.of("goog.setTestOnly");

  private static final QualifiedName GOOG_PROVIDE = QualifiedName.of("goog.provide");

  private static final QualifiedName GOOG_MODULE = QualifiedName.of("goog.module");

  private static final QualifiedName GOOG_MODULE_GET = QualifiedName.of("goog.module.get");

  private static final QualifiedName GOOG_REQUIRE = QualifiedName.of("goog.require");

  private static final QualifiedName GOOG_REQUIRE_TYPE = QualifiedName.of("goog.requireType");

  private static final QualifiedName GOOG_FORWARD_DECLARE = QualifiedName.of("goog.forwardDeclare");

  private static final QualifiedName GOOG_REQUIRE_DYNAMIC = QualifiedName.of("goog.requireDynamic");

  // Utility class; do not instantiate.
  private NodeUtil() {}

  /**
   * Gets the boolean value of a node that represents an expression, or {@code Tri.UNKNOWN} if no
   * such value can be determined by static analysis.
   *
   * <p>This method does not consider whether the node may have side-effects.
   */
  static Tri getBooleanValue(Node n) {
    // This switch consists of cases that are not supported by getLiteralBooleanValue(),
    // which we will call if none of these match.
    switch (n.getToken()) {
      case NULL:
      case FALSE:
      case VOID:
        return Tri.FALSE;

      case TRUE:
      case REGEXP:
      case FUNCTION:
      case CLASS:
      case NEW:
      case ARRAYLIT:
      case OBJECTLIT:
        return Tri.TRUE;

      case TEMPLATELIT:
        if (n.hasOneChild()) {
          Node templateLitString = n.getOnlyChild();
          checkState(templateLitString.isTemplateLitString(), templateLitString);
          String cookedString = templateLitString.getCookedString();
          return Tri.forBoolean(cookedString != null && !cookedString.isEmpty());
        } else {
          return Tri.UNKNOWN;
        }

      case STRINGLIT:
        return Tri.forBoolean(n.getString().length() > 0);

      case NUMBER:
        return Tri.forBoolean(n.getDouble() != 0);

      case BIGINT:
        return Tri.forBoolean(!n.getBigInt().equals(BigInteger.ZERO));

      case NOT:
        return getBooleanValue(n.getLastChild()).not();

      case NAME:
        // We assume here that programs don't change the value of these global variables.
        switch (n.getString()) {
          case "undefined":
          case "NaN":
            return Tri.FALSE;
          case "Infinity":
            return Tri.TRUE;
          default:
            return Tri.UNKNOWN;
        }

      case BITNOT:
      case POS:
      case NEG:
        {
          Double doubleVal = getNumberValue(n);
          if (doubleVal != null) {
            boolean isFalsey = doubleVal.isNaN() || isEitherZero(doubleVal);
            return Tri.forBoolean(!isFalsey);
          }

          BigInteger bigintVal = getBigIntValue(n);
          if (bigintVal != null) {
            boolean isFalsey = bigintVal.equals(BigInteger.ZERO);
            return Tri.forBoolean(!isFalsey);
          }

          return Tri.UNKNOWN;
        }

      case ASSIGN:
      case COMMA:
        // For ASSIGN and COMMA the value is the value of the RHS.
        return getBooleanValue(n.getLastChild());

      case AND:
      case ASSIGN_AND:
        {
          Tri lhs = getBooleanValue(n.getFirstChild());
          Tri rhs = getBooleanValue(n.getLastChild());
          return lhs.and(rhs);
        }
      case OR:
      case ASSIGN_OR:
        {
          Tri lhs = getBooleanValue(n.getFirstChild());
          Tri rhs = getBooleanValue(n.getLastChild());
          return lhs.or(rhs);
        }
      case HOOK:
        {
          Tri trueValue = getBooleanValue(n.getSecondChild());
          Tri falseValue = getBooleanValue(n.getLastChild());
          if (trueValue.equals(falseValue)) {
            return trueValue;
          } else {
            return Tri.UNKNOWN;
          }
        }
      case COALESCE:
      case ASSIGN_COALESCE:
        {
          Tri lhs = getBooleanValue(n.getFirstChild());
          Tri rhs = getBooleanValue(n.getLastChild());
          if (lhs.equals(Tri.TRUE) || lhs.equals(rhs)) {
            return lhs;
          } else {
            return Tri.UNKNOWN;
          }
        }
      default:
        return Tri.UNKNOWN;
    }
  }

  /**
   * Gets the value of a node as a String, or null if it cannot be converted. When it returns a
   * non-null String, this method effectively emulates the <code>String()</code> JavaScript cast
   * function.
   *
   * <p>IMPORTANT: This method does not consider whether {@code n} may have side effects.
   */
  public static @Nullable String getStringValue(Node n) {
    // TODO(user): regex literals as well.
    switch (n.getToken()) {
      case STRINGLIT:
      case STRING_KEY:
        return n.getString();

      case TEMPLATELIT:
        // Only convert a template literal if all its expressions can be converted.
        StringBuilder string = new StringBuilder();
        for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
          Node expression = child;
          if (child.isTemplateLitSub()) {
            expression = child.getFirstChild();
          }
          String expressionString = getStringValue(expression);
          if (expressionString == null) {
            // Cannot convert.
            return null;
          }
          string.append(expressionString);
        }
        return string.toString();

      case TEMPLATELIT_STRING:
        return n.getCookedString();

      case NAME:
        String name = n.getString();
        if ("undefined".equals(name) || "Infinity".equals(name) || "NaN".equals(name)) {
          return name;
        }
        break;

      case NEG:
      case NUMBER:
        {
          Double value = getNumberValue(n);
          if (value == null) {
            break;
          }

          return DToA.numberToString(value.doubleValue());
        }

      case BIGINT:
        return n.getBigInt() + "n";

      case FALSE:
        return "false";

      case TRUE:
        return "true";

      case NULL:
        return "null";

      case VOID:
        return "undefined";

      case NOT:
        Tri child = getBooleanValue(n.getFirstChild());
        if (child != Tri.UNKNOWN) {
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
   * When converting arrays to string using Array.prototype.toString or Array.prototype.join, the
   * rules for conversion to String are different than converting each element individually.
   * Specifically, "null" and "undefined" are converted to an empty string.
   *
   * @param n A node that is a member of an Array.
   * @return The string representation.
   */
  static String getArrayElementStringValue(Node n) {
    return (NodeUtil.isNullOrUndefined(n) || n.isEmpty()) ? "" : getStringValue(n);
  }

  static @Nullable String arrayToString(Node literal) {
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
   * Gets the value of a node as a Number, or null if it cannot be converted. When it returns a
   * non-null Double, this method effectively emulates the <code>Number()</code> JavaScript cast
   * function.
   *
   * <p>IMPORTANT: This method does not consider whether {@code n} may have side effects.
   *
   * @param n The node.
   * @return The value of a node as a Number, or null if it cannot be converted.
   */
  static @Nullable Double getNumberValue(Node n) {
    switch (n.getToken()) {
      case NUMBER:
        return n.getDouble();

      case BIGINT:
        // When this call returns non-null, it is an assertion that JavaScript automatic conversion
        // to Number (e.g. during arithmetic operations) would produce the value returned here.
        // The spec does not allow automatic conversion from BigInt to Number, since that would
        // likely result in incorrect computation results.
        return null;

      case VOID:
        return Double.NaN;

      case NAME:
        switch (n.getString()) {
          case "undefined":
          case "NaN":
            return Double.NaN;
          case "Infinity":
            return Double.POSITIVE_INFINITY;
          default:
            return null;
        }

      case POS:
        return getNumberValue(n.getOnlyChild());

      case NEG:
        {
          Double val = getNumberValue(n.getOnlyChild());
          return (val == null) ? null : -val;
        }

      case BITNOT:
        {
          Double val = getNumberValue(n.getOnlyChild());
          return (val == null) ? null : (double) ~ecmascriptToInt32(val);
        }

      case FALSE:
      case NOT:
      case NULL:
      case TRUE:
        switch (getBooleanValue(n)) {
          case TRUE:
            return 1.0;
          case FALSE:
            return 0.0;
          case UNKNOWN:
            return null;
        }
        throw new AssertionError();

      case TEMPLATELIT:
        String string = getStringValue(n);
        if (string == null) {
          return null;
        }
        return getStringNumberValue(string);

      case STRINGLIT:
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

  static @Nullable Double getStringNumberValue(String rawJsString) {
    if (rawJsString.contains("\u000b")) {
      // vertical tab is not always whitespace
      return null;
    }

    String s = trimJsWhiteSpace(rawJsString);
    // return ScriptRuntime.toNumber(s);
    if (s.isEmpty()) {
      return 0.0;
    }

    if (s.length() > 2 && s.charAt(0) == '0' && (s.charAt(1) == 'x' || s.charAt(1) == 'X')) {
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
    if (s.equals("infinity") || s.equals("-infinity") || s.equals("+infinity")) {
      return null;
    }

    try {
      return Double.parseDouble(s);
    } catch (NumberFormatException e) {
      return Double.NaN;
    }
  }

  /**
   * Gets the value of a node as a BigInt, or null if it cannot be converted. When it returns a
   * non-null BigInteger, this method effectively emulates the <code>BigInt()</code> JavaScript cast
   * function.
   *
   * <p>IMPORTANT: This method does not consider whether {@code n} may have side effects.
   *
   * @param n The node.
   * @return The value of a node as a BigInt, or null if it cannot be converted.
   */
  static @Nullable BigInteger getBigIntValue(Node n) {
    switch (n.getToken()) {
      case NUMBER:
        {
          double val = n.getDouble();
          return isAtLeastIntegerPrecision(val) && isExactInt64(val)
              ? BigInteger.valueOf((long) val)
              : null;
        }

      case BIGINT:
        return n.getBigInt();

      case FALSE:
      case NOT:
      case TRUE:
        switch (getBooleanValue(n)) {
          case TRUE:
            return BigInteger.ONE;
          case FALSE:
            return BigInteger.ZERO;
          case UNKNOWN:
            return null;
        }
        throw new AssertionError();

      case TEMPLATELIT:
        {
          String string = getStringValue(n);
          if (string == null) {
            return null;
          }
          return getStringBigIntValue(string);
        }

      case STRINGLIT:
        return getStringBigIntValue(n.getString());

      case NEG:
        {
          BigInteger result = getBigIntValue(n.getOnlyChild());
          return (result == null) ? null : result.negate();
        }

      case BITNOT:
        {
          BigInteger result = getBigIntValue(n.getOnlyChild());
          return (result == null) ? null : result.not();
        }

      case ARRAYLIT:
      case OBJECTLIT:
        String value = getStringValue(n);
        return value != null ? getStringBigIntValue(value) : null;
      case VOID:
      case NAME:
      case NULL:
      default:
        return null;
    }
  }

  static @Nullable BigInteger getStringBigIntValue(String rawJsString) {
    if (rawJsString.contains("\u000b")) {
      // vertical tab is not always whitespace
      return null;
    }

    String s = trimJsWhiteSpace(rawJsString);
    if (s.isEmpty()) {
      return BigInteger.ZERO;
    }

    if (s.length() > 2 && s.charAt(0) == '0') {
      // Attempt to convert hex, octal, and binary formats.
      int radix;
      switch (s.charAt(1)) {
        case 'x':
        case 'X':
          radix = 16;
          break;
        case 'o':
        case 'O':
          radix = 8;
          break;
        case 'b':
        case 'B':
          radix = 2;
          break;
        default:
          radix = 0;
      }
      if (radix != 0) {
        try {
          return new BigInteger(s.substring(2), radix);
        } catch (NumberFormatException e) {
          return null;
        }
      }
    }

    try {
      return new BigInteger(s);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  static String trimJsWhiteSpace(String s) {
    int start = 0;
    int end = s.length();
    while (end > 0 && TokenUtil.isStrWhiteSpaceChar(s.charAt(end - 1)) == Tri.TRUE) {
      end--;
    }
    while (start < end && TokenUtil.isStrWhiteSpaceChar(s.charAt(start)) == Tri.TRUE) {
      start++;
    }
    return s.substring(start, end);
  }

  /**
   * @param n A function or class node.
   * @return The name of the given function or class, if it has one.
   */
  public static @Nullable String getName(Node n) {
    Node nameNode = getNameNode(n);
    return nameNode == null ? null : nameNode.getQualifiedName();
  }

  /**
   * Gets the node of a function or class's name. This method recognizes five forms:
   *
   * <ul>
   *   <li>{@code class name {...}}
   *   <li>{@code var name = class {...}}
   *   <li>{@code qualified.name = class {...}}
   *   <li>{@code var name2 = class name1 {...}}
   *   <li>{@code qualified.name2 = class name1 {...}}
   * </ul>
   *
   * In two last cases with named function expressions, the second name is returned (the variable or
   * qualified name).
   *
   * @param n A function or class node
   * @return the node best representing the class's name
   */
  public static @Nullable Node getNameNode(Node n) {
    checkState(n.isFunction() || n.isClass(), n);
    Node parent = n.getParent();
    switch (parent.getToken()) {
      case NAME:
        // var name = function() ...
        // var name2 = function name1() ...
        return parent;

      case ASSIGN:
        {
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
    originalName.replaceWith(emptyName.srcref(originalName));
  }

  /**
   * Gets the function's name. This method recognizes the forms:
   *
   * <ul>
   *   <li>{@code &#123;'name': function() ...&#125;}
   *   <li>{@code &#123;name: function() ...&#125;}
   *   <li>{@code function name() ...}
   *   <li>{@code var name = function() ...}
   *   <li>{@code var obj = {name() {} ...}}
   *   <li>{@code qualified.name = function() ...}
   *   <li>{@code var name2 = function name1() ...}
   *   <li>{@code qualified.name2 = function name1() ...}
   * </ul>
   *
   * @param n a node whose type is {@link Token#FUNCTION}
   * @return the function's name, or {@code null} if it has no name
   */
  public static @Nullable String getNearestFunctionName(Node n) {
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

  public static @Nullable Node getEs6ClassConstructorMemberFunctionDef(Node classNode) {
    checkArgument(classNode.isClass(), classNode);
    Node classMembers = checkNotNull(classNode.getLastChild(), classNode);
    for (Node memberFunctionDef = classMembers.getFirstChild();
        memberFunctionDef != null;
        memberFunctionDef = memberFunctionDef.getNext()) {
      if (isEs6ConstructorMemberFunctionDef(memberFunctionDef)) {
        return memberFunctionDef;
      }
    }

    return null;
  }

  /** Returns true if this is an immutable value. */
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
      case STRINGLIT:
      case NUMBER:
      case BIGINT:
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
        // TODO(yitingwang) There are probably other tokens that shouldn't get to the default branch
        checkArgument(!n.isTemplateLitString());
        break;
    }

    return false;
  }

  /** Returns true if the operator on this node is symmetric */
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
   * Returns true if the operator on this node is relational. the returned set does not include the
   * equalities.
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

  /** Returns the inverse of an operator if it is invertible. ex. '>' ==> '<' */
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
   * Returns true if this is a literal value. We define a literal value as any node that evaluates
   * to the same thing regardless of when or where it is evaluated. So /xyz/ and [3, 5] are
   * literals, but the name a is not.
   *
   * <p>Function literals do not meet this definition, because they lexically capture variables. For
   * example, if you have <code>
   * function() { return a; }
   * </code> If it is evaluated in a different scope, then it captures a different variable. Even if
   * the function did not read any captured variables directly, it would still fail this definition,
   * because it affects the lifecycle of variables in the enclosing scope.
   *
   * <p>However, a function literal with respect to a particular scope is a literal.
   *
   * @param includeFunctions If true, all function expressions will be treated as literals.
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
          switch (child.getToken()) {
            case MEMBER_FUNCTION_DEF:
            case GETTER_DEF:
            case SETTER_DEF:
              // { methodName() {...} }
              // { get propertyName() {...} }
              // { set propertyName(value) {...} }
              if (!includeFunctions) {
                return false;
              }
              break;

            case COMPUTED_PROP:
              // { [key_expression]: value, ... }
              // { [key_expression](args) {...}, ... }
              // { get [key_expression]() {...}, ... }
              // { set [key_expression](args) {...}, ... }
              if (!isLiteralValue(child.getFirstChild(), includeFunctions)
                  || !isLiteralValue(child.getLastChild(), includeFunctions)) {
                return false;
              }
              break;

            case OBJECT_SPREAD:
              if (!isLiteralValue(child.getOnlyChild(), includeFunctions)) {
                return false;
              }
              break;

            case STRING_KEY:
              // { key: value, ... }
              // { "quoted_key": value, ... }
              if (!isLiteralValue(child.getOnlyChild(), includeFunctions)) {
                return false;
              }
              break;

            default:
              throw new IllegalArgumentException(
                  "Unexpected child of OBJECTLIT: " + child.toStringTree());
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
    if (node.isStringLit() || (node.isTemplateLit() && node.hasOneChild())) {
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
   * Returns whether this a BLOCK node with no children.
   *
   * @param block The node.
   */
  public static boolean isEmptyBlock(Node block) {
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

  static boolean isBinaryOperator(Node n) {
    return isBinaryOperatorType(n.getToken());
  }

  /**
   * An operator with two operands that does not assign a value to either. Once you cut through the
   * layers of rules, these all parse similarly, taking LeftHandSideExpression operands on either
   * side. Comma is not included, because it takes AssignmentExpression operands, making its syntax
   * different.
   */
  static boolean isBinaryOperatorType(Token type) {
    switch (type) {
      case OR:
      case AND:
      case COALESCE:
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
   * An operator taking only one operand. These all parse very similarly, taking
   * LeftHandSideExpression operands.
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
   * A "simple" operator is one whose children are expressions, has no direct side-effects (unlike
   * '+='), and has no conditional aspects (unlike '||').
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
   * Returns true iff this node defines a namespace, e.g.,
   *
   * <p>/** @const * / var goog = {}; /** @const * / var goog = goog || {}; /** @const * / goog.math
   * = goog.math || {};
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
   * Returns {@code true} if {@code node} <em>might</em> execute an `Iterable` iteration that has
   * side-effects, {@code false} if there are <em>definitely<em> no such side-effects.
   *
   * <p>This function only considers purity of the iteration. Other expressions within the {@code
   * node} subtree may still have side-effects.
   *
   * @throws IllegalStateException if {@code node} is of a kind that does not trigger iteration. An
   *     explicit goal of this function is to record all the kinds of nodes that do.
   */
  static boolean iteratesImpureIterable(Node node) {
    Node parent = node.getParent();

    final Node iterable;
    switch (node.getToken()) {
      case ITER_SPREAD:
        iterable = node.getOnlyChild();
        break;

      case YIELD:
        if (!node.isYieldAll()) {
          return false; // Regular `yield` does not iterate, only `yield*`.
        }
        iterable = node.getOnlyChild();
        break;

      case FOR_OF:
      case FOR_AWAIT_OF:
        iterable = node.getSecondChild();
        break;

      case ITER_REST:
        switch (parent.getToken()) {
          case PARAM_LIST: // Rest arguments are flat at the call-site.
            return false;
          case ARRAY_PATTERN:
            return true; // TODO(b/127862986): We assume the r-value to be an impure iterable.
          default:
            throw new IllegalStateException(
                "Unexpected parent of ITRE_REST: " + parent.toStringTree());
        }

      default:
        throw new IllegalStateException(
            "Expected a kind of node that may trigger iteration: " + node.toStringTree());
    }

    return !isPureIterable(iterable);
  }

  /**
   * Returns {@code true} if {@code node} is guaranteed to be an `Iterable` that causes no
   * side-effects during iteration, {@code false} otherwise.
   */
  private static boolean isPureIterable(Node node) {
    // TODO(b/127862986): The type of the iterable should also allow us to say it's pure.
    switch (node.getToken()) {
      case ARRAYLIT:
      case STRINGLIT:
      case TEMPLATELIT:
        return true; // These iterables are known to be pure.
      default:
        return false; // Anything else, including a non-iterable (e.g. `null`), would be impure.
    }
  }

  /**
   * @return Whether the new has a local result.
   */
  static boolean newHasLocalResult(Node n) {
    checkState(n.isNew(), n);
    return n.isOnlyModifiesThisCall();
  }

  static boolean allArgsUnescapedLocal(Node callOrNew) {
    for (Node arg = callOrNew.getSecondChild(); arg != null; arg = arg.getNext()) {
      if (!evaluatesToLocalValue(arg)) {
        return false;
      }
    }
    return true;
  }

  /** We will assume that side effects never change the values of these global variables. */
  static final ImmutableSet<String> KNOWN_CONSTANTS =
      ImmutableSet.of("undefined", "Infinity", "NaN");

  /**
   * @return Whether the tree can be affected by side-effects or has side-effects.
   */
  static boolean canBeSideEffected(Node n) {
    return canBeSideEffected(n, KNOWN_CONSTANTS, null);
  }

  /**
   * @param knownConstants A set of names known to be constant value at node 'n' (such as locals
   *     that are last written before n can execute).
   * @return Whether the tree can be affected by side-effects or has side-effects.
   */
  // TODO(nick): Get rid of the knownConstants argument in favor of using
  // scope with InferConsts.
  static boolean canBeSideEffected(Node n, Set<String> knownConstants, @Nullable Scope scope) {
    switch (n.getToken()) {
      case YIELD:
      case CALL:
      case OPTCHAIN_CALL:
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
      case OPTCHAIN_GETPROP:
      case OPTCHAIN_GETELEM:
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
   * The comma operator has the lowest precedence, 0, followed by the assignment operators ({@code
   * =}, {@code &=}, {@code +=}, etc.) which have precedence of 1, and so on.
   *
   * <p>See
   * https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Operators/Operator_Precedence
   */
  public static int precedence(Token type) {
    switch (type) {
      case COMMA:
        return 0;
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
      case ASSIGN_OR:
      case ASSIGN_AND:
      case ASSIGN_COALESCE:
      case ASSIGN:
        return 1;
      case YIELD:
        return 2;
      case HOOK:
        return 3; // ?: operator
      case OR:
        return 4;
      case AND:
        return 5;
      case COALESCE:
        return 6;
      case BITOR:
        return 7;
      case BITXOR:
        return 8;
      case BITAND:
        return 9;
      case EQ:
      case NE:
      case SHEQ:
      case SHNE:
        return 10;
      case LT:
      case GT:
      case LE:
      case GE:
      case INSTANCEOF:
      case IN:
        return 11;
      case LSH:
      case RSH:
      case URSH:
        return 12;
      case SUB:
      case ADD:
        return 13;
      case MUL:
      case MOD:
      case DIV:
        return 14;

      case EXPONENT:
        return 15;

      case AWAIT:
      case NEW:
      case DELPROP:
      case TYPEOF:
      case VOID:
      case NOT:
      case BITNOT:
      case POS:
      case NEG:
        return 16; // Unary operators

      case INC:
      case DEC:
        return 17; // Update operators

      case CALL:
      case GETELEM:
      case GETPROP:
      case OPTCHAIN_CALL:
      case OPTCHAIN_GETELEM:
      case OPTCHAIN_GETPROP:
      case NEW_TARGET:
      case IMPORT_META:
        // Data values
      case ARRAYLIT:
      case ARRAY_PATTERN:
      case DEFAULT_VALUE:
      case DESTRUCTURING_LHS:
      case EMPTY: // TODO(johnlenz): remove this.
      case FALSE:
      case FUNCTION:
      case CLASS:
      case INTERFACE:
      case NAME:
      case NULL:
      case NUMBER:
      case BIGINT:
      case OBJECTLIT:
      case OBJECT_PATTERN:
      case REGEXP:
      case ITER_REST:
      case OBJECT_REST:
      case ITER_SPREAD:
      case OBJECT_SPREAD:
      case STRINGLIT:
      case STRING_KEY:
      case MEMBER_VARIABLE_DEF:
      case INDEX_SIGNATURE:
      case CALL_SIGNATURE:
      case THIS:
      case SUPER:
      case TRUE:
      case TAGGED_TEMPLATELIT:
      case TEMPLATELIT:
      case DYNAMIC_IMPORT:
        // Tokens from the type declaration AST
      case UNION_TYPE:
        return 18;
      case FUNCTION_TYPE:
        return 19;
      case ARRAY_TYPE:
      case PARAMETERIZED_TYPE:
        return 20;
      case STRING_TYPE:
      case NUMBER_TYPE:
      case BOOLEAN_TYPE:
      case ANY_TYPE:
      case RECORD_TYPE:
      case NULLABLE_TYPE:
      case NAMED_TYPE:
      case UNDEFINED_TYPE:
      case VOID_TYPE:
      case GENERIC_TYPE:
        return 21;
      case CAST:
        return 22;

      default:
        checkArgument(type != Token.TEMPLATELIT_STRING);
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

  /**
   * @see #getKnownValueType(Node)
   */
  public enum ValueType {
    UNDETERMINED,
    NULL,
    VOID,
    NUMBER,
    BIGINT,
    STRING,
    BOOLEAN,
    OBJECT
  }

  /**
   * Evaluate a node's token and attempt to determine which primitive value type it could resolve to
   * Without proper type information some assumptions had to be made for operations that could
   * result in a BigInt or a Number. If there is not enough information available to determine one
   * or the other then we assume Number in order to maintain historical behavior of the compiler and
   * avoid breaking projects that relied on this behavior.
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
      case COALESCE:
      case ASSIGN_OR:
      case ASSIGN_AND:
      case ASSIGN_COALESCE:
        return and(getKnownValueType(n.getFirstChild()), getKnownValueType(n.getLastChild()));
      case HOOK:
        return and(getKnownValueType(n.getSecondChild()), getKnownValueType(n.getLastChild()));

      case ADD:
        {
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
          //   [] + {} === "[object Object]"
          if (first == ValueType.OBJECT || last == ValueType.OBJECT) {
            return ValueType.UNDETERMINED;
          }

          if (!mayBeString(first) && !mayBeString(last)) {
            if (first == ValueType.BIGINT || last == ValueType.BIGINT) {
              // If one operand is a BigInt, then the result is a BigInt or there's a type error
              return ValueType.BIGINT;
            } else {
              // ADD used with compilations of null, undefined, boolean and number always result
              // in numbers.
              return ValueType.NUMBER;
            }
          }

          return ValueType.UNDETERMINED;
        }

      case ASSIGN_ADD:
        {
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
        // assign operators could be using BIGINT or NUMBER
        if (getKnownValueType(n.getLastChild()) == ValueType.BIGINT) {
          return ValueType.BIGINT;
        } else {
          return ValueType.NUMBER;
        }

      case BIGINT:
        return ValueType.BIGINT;

      case BITOR:
      case BITXOR:
      case BITAND:
      case LSH:
      case RSH:
      case SUB:
      case MUL:
      case MOD:
      case DIV:
      case EXPONENT:
        {
          // binary arithmetic operators could result in BIGINT or NUMBER
          ValueType first = getKnownValueType(n.getFirstChild());
          ValueType last = getKnownValueType(n.getLastChild());
          if (first == ValueType.BIGINT || last == ValueType.BIGINT) {
            return ValueType.BIGINT;
          } else {
            return ValueType.NUMBER;
          }
        }
      case BITNOT:
      case NEG:
        // unary negation (bitwise or arithmetic) could be using BIGINT or NUMBER
        if (getKnownValueType(n.getOnlyChild()) == ValueType.BIGINT) {
          return ValueType.BIGINT;
        } else {
          return ValueType.NUMBER;
        }
      case INC:
      case DEC:
        // increment and decrement can only be used on variables, so we assume they're numbers
        return ValueType.NUMBER;
      case URSH:
      case POS:
      case NUMBER:
        // unary + and unsigned right shift don't apply to bigint
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
      case STRINGLIT:
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
        checkArgument(!n.isTemplateLitString());
        return ValueType.UNDETERMINED;
    }
  }

  static ValueType and(ValueType a, ValueType b) {
    return (a == b) ? a : ValueType.UNDETERMINED;
  }

  /** Returns true if the result of node evaluation is always a number */
  public static boolean isNumericResult(Node n) {
    return getKnownValueType(n) == ValueType.NUMBER;
  }

  /** Returns true if the result of node evaluation is always a bigint */
  public static boolean isBigIntResult(Node n) {
    return getKnownValueType(n) == ValueType.BIGINT;
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
      Color color = n.getColor();
      if (color != null) {
        if (color.equals(StandardColors.STRING)) {
          return true;
        } else if (color.equals(StandardColors.NUMBER)
            || color.equals(StandardColors.BIGINT)
            || color.equals(StandardColors.BOOLEAN)
            || color.equals(StandardColors.NULL_OR_VOID)) {
          return false;
        }
      }
      JSType type = n.getJSType();
      if (type != null) {
        if (type.isStringValueType()) {
          return true;
        } else if (type.isNumberValueType()
            || type.isBigIntValueType()
            || type.isBooleanValueType()
            || type.isNullType()
            || type.isVoidType()) {
          return false;
        }
      }
    }
    return mayBeString(getKnownValueType(n));
  }

  /**
   * @return Whether the results is possibly a string, this includes Objects which may implicitly be
   *     converted to a string.
   */
  static boolean mayBeString(ValueType type) {
    switch (type) {
      case BOOLEAN:
      case NULL:
      case NUMBER:
      case BIGINT:
      case VOID:
        return false;
      case OBJECT:
      case STRING:
      case UNDETERMINED:
        return true;
    }
    throw new IllegalStateException("unexpected");
  }

  static boolean mayBeObject(Node n) {
    return mayBeObject(getKnownValueType(n));
  }

  static boolean mayBeObject(ValueType type) {
    switch (type) {
      case BOOLEAN:
      case NULL:
      case NUMBER:
      case BIGINT:
      case STRING:
      case VOID:
        return false;
      case OBJECT:
      case UNDETERMINED:
        return true;
    }
    throw new IllegalStateException("unexpected");
  }

  /**
   * Returns true if the operator is associative. e.g. (a * b) * c = a * (b * c) Note: "+" is not
   * associative because it is also the concatenation for strings. e.g. "a" + (1 + 2) is not "a" + 1
   * + 2
   */
  static boolean isAssociative(Token type) {
    switch (type) {
      case MUL:
      case AND:
      case OR:
      case COALESCE:
      case BITOR:
      case BITXOR:
      case BITAND:
        return true;
      default:
        return false;
    }
  }

  /**
   * Returns true if the operator is commutative. e.g. (a * b) * c = c * (b * a) Note 1: "+" is not
   * commutative because it is also the concatenation for strings. e.g. "a" + (1 + 2) is not "a" + 1
   * + 2 Note 2: only operations on literals and pure functions are commutative.
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

  /**
   * Returns true if the operator is an assignment type operator. Note: The logical assignments
   * (i.e. ASSIGN_OR, ASSIGN_AND, ASSIGN_COALESCE) follow short-circuiting behavior, and the RHS may
   * not always be evaluated. They are still considered AssignmentOps (may be optimized).
   */
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
      case ASSIGN_OR:
      case ASSIGN_AND:
      case ASSIGN_COALESCE:
        return true;
      default:
        break;
    }
    return false;
  }

  /** Returns true if the operator is a logical assignment type operator. */
  public static boolean isLogicalAssignmentOp(Node n) {
    switch (n.getToken()) {
      case ASSIGN_OR:
      case ASSIGN_AND:
      case ASSIGN_COALESCE:
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
      case ASSIGN_OR:
        return Token.OR;
      case ASSIGN_AND:
        return Token.AND;
      case ASSIGN_COALESCE:
        return Token.COALESCE;
      default:
        break;
    }
    throw new IllegalArgumentException("Not an assignment op:" + n);
  }

  /** Gets the closest ancestor to the given node of the provided type. */
  public static Node getEnclosingType(Node n, final Token type) {
    return getEnclosingNode(n, n1 -> n1.getToken() == type);
  }

  static Node getEnclosingNonArrowFunction(Node n) {
    return getEnclosingNode(n, NodeUtil::isNonArrowFunction);
  }

  /** Finds the class containing the given node. */
  public static Node getEnclosingClass(Node n) {
    return getEnclosingNode(n, Node::isClass);
  }

  public static Node getEnclosingModuleIfPresent(Node n) {
    return getEnclosingNode(n, Node::isModuleBody);
  }

  /** Finds the function containing the given node. */
  public static Node getEnclosingFunction(Node n) {
    return getEnclosingNode(n, Node::isFunction);
  }

  /** Finds the script containing the given node. */
  public static Node getEnclosingScript(Node n) {
    return getEnclosingNode(n, Node::isScript);
  }

  /** Finds the block containing the given node. */
  public static Node getEnclosingBlock(Node n) {
    return getEnclosingNode(n, Node::isBlock);
  }

  public static Node getEnclosingBlockScopeRoot(Node n) {
    return getEnclosingNode(n, NodeUtil::createsBlockScope);
  }

  public static Node getEnclosingScopeRoot(Node n) {
    return getEnclosingNode(n, NodeUtil::createsScope);
  }

  /**
   * Return the nearest enclosing hoist scope root node, null for the global scope. There are
   * currently 4 such roots to consider: the global, module, function, and class static blocks.
   */
  public static Node getEnclosingHoistScopeRoot(Node n) {
    return getEnclosingNode(n, NodeUtil::isHoistScopeRoot);
  }

  public static boolean isHoistScopeRoot(Node n) {
    // The "global" hoist scope has multiple meaning so
    return n.isFunction() || n.isModuleBody() || isClassStaticBlock(n);
  }

  public static boolean isInFunction(Node n) {
    return getEnclosingFunction(n) != null;
  }

  public static Node getEnclosingStatement(Node n) {
    return getEnclosingNode(n, NodeUtil::isStatement);
  }

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
  static @Nullable Node getFirstPropMatchingKey(Node n, String keyName) {
    checkState(n.isObjectLit() || n.isClassMembers());
    for (Node keyNode = n.getFirstChild(); keyNode != null; keyNode = keyNode.getNext()) {
      if ((keyNode.isStringKey() || keyNode.isMemberFunctionDef())
          && keyNode.getString().equals(keyName)) {
        return keyNode.getFirstChild();
      }
    }
    return null;
  }

  /**
   * @return The first getter in the class members that matches the key.
   */
  static @Nullable Node getFirstGetterMatchingKey(Node n, String keyName) {
    checkState(n.isClassMembers() || n.isObjectLit(), n);
    for (Node keyNode = n.getFirstChild(); keyNode != null; keyNode = keyNode.getNext()) {
      if (keyNode.isGetterDef() && keyNode.getString().equals(keyName)) {
        return keyNode;
      }
    }
    return null;
  }

  /**
   * Returns {@code true} if this function references its receiver object.
   *
   * <p>We define this function in terms of "receiver" rather than specific syntax, however
   * internally we search for `this` and `super`.
   *
   * <p>Arrow functions return {@code false}. They do not have their own receiver, but rather
   * capture the receiver of the enclosing scope.
   */
  static boolean referencesOwnReceiver(Node fn) {
    checkState(fn.isFunction());
    if (fn.isArrowFunction()) {
      return false;
    }

    return referencesEnclosingReceiver(NodeUtil.getFunctionParameters(fn))
        || referencesEnclosingReceiver(NodeUtil.getFunctionBody(fn));
  }

  /**
   * Returns {@code true} if this subtree references the receiver object from its enclosing function
   * scope.
   *
   * <p>We define this function in terms of "receiver" rather than specific syntax, however
   * internally we search for `this` and `super`.
   *
   * <p>Arrow functions may return {@code true}. They capture the receiver of the enclosing scope,
   * rather than having their own.
   */
  static boolean referencesEnclosingReceiver(Node n) {
    return has(n, (c) -> c.isThis() || c.isSuper(), MATCH_ANYTHING_BUT_NON_ARROW_FUNCTION);
  }

  /**
   * Returns true if the current scope contains references to the 'super' keyword. Note that if
   * there are classes declared inside the current class, super calls which reference those classes
   * are not reported.
   */
  static boolean referencesSuper(Node n) {
    Node curr = n.getFirstChild();
    while (curr != null) {
      if (has(curr, Node::isSuper, node -> !node.isClass())) {
        return true;
      }
      curr = curr.getNext();
    }
    return false;
  }

  /** Is this a GETPROP, OPTCHAIN_GETPROP, GETELEM, or OPTCHAIN_GETELEM? */
  public static boolean isNormalOrOptChainGet(Node n) {
    return isNormalGet(n) || isOptChainGet(n);
  }

  /** Is this a GETPROP or OPTCHAIN_GETPROP? */
  public static boolean isNormalOrOptChainGetProp(Node n) {
    return n.isGetProp() || n.isOptChainGetProp();
  }

  /** Is this a CALL or OPTCHAIN_CALL? */
  public static boolean isNormalOrOptChainCall(Node n) {
    return n.isCall() || n.isOptChainCall();
  }

  /** Is this a GETPROP or GETELEM node? */
  public static boolean isNormalGet(Node n) {
    return n.isGetProp() || n.isGetElem();
  }

  /** Is this an OPTCHAIN_GETPROP or OPTCHAIN_GETELEM node? */
  public static boolean isOptChainGet(Node n) {
    return n.isOptChainGetProp() || n.isOptChainGetElem();
  }

  /** Is this a OPTCHAIN_GETPROP, OPTCHAIN_GETELEM, OPTCHAIN_CALL node? */
  public static boolean isOptChainNode(Node n) {
    return n.isOptChainGetProp() || n.isOptChainGetElem() || n.isOptChainCall();
  }

  /**
   * Find the start of the optional chain. E.g Find the `a?. ...` node in `a?.b.c.d` given any other
   * node
   *
   * @param n A node in an optional chain
   * @return The start of the optional chain that `n` is part of.
   */
  static Node getStartOfOptChainSegment(Node n) {
    checkState(NodeUtil.isOptChainNode(n), n);
    if (n.isOptionalChainStart()) {
      return n;
    }
    return getStartOfOptChainSegment(n.getFirstChild());
  }

  /**
   * Find the end of an optional chain segment.
   *
   * <p>Each `?.` ends one segment and starts another.
   *
   * <p>Examples
   *
   * <pre>
   *   a?.b.c.d   // end is the node with children `a?.b.c` and `d`
   *   a?.b.c?.d  // given a?.b end is the node with children `a?.b` and `c`
   *   a?.b.c?.d  // given a?.b.c end is the node with children `a?.b.c` and `d`
   *   (a?.b.c).d // given a?.b end is the node with children `a?.b` and `c`
   * </pre>
   *
   * @param n A node in an optional chain
   * @return The end of the optional chain that `n` is part of.
   */
  static Node getEndOfOptChainSegment(Node n) {
    checkState(NodeUtil.isOptChainNode(n), n);
    if (isEndOfOptChainSegment(n)) {
      return n;
    } else {
      return getEndOfOptChainSegment(n.getParent());
    }
  }

  /**
   * Is this node the final node of a full optional chain?
   *
   * <p>e.g. for `a?.b.c?.d` this method returns true only for the Node with children `a?.b.c` and
   * `d`. That node is the end of the whole chain, and also represents the whole chain in the AST.
   */
  static boolean isEndOfFullOptChain(Node n) {
    if (NodeUtil.isOptChainNode(n)) {
      Node parent = n.getParent();
      // the chain continues if this is the first child of another optional chain node
      return !(NodeUtil.isOptChainNode(parent) && n.isFirstChildOf(parent));
    } else {
      // not even an optional chain node
      return false;
    }
  }

  /**
   * Is this node the end of an optional chain segment?
   *
   * <p>Each `?.` begins a new segment and ends the previous one, if any. The end of the whole chain
   * is also the end of its final segment.
   */
  static boolean isEndOfOptChainSegment(Node n) {
    if (!NodeUtil.isOptChainNode(n)) {
      return false;
    } else {
      Node parent = n.getParent();
      // Check for null so this method will work for a disconnected Node.
      if (parent != null && n.isFirstChildOf(parent) && NodeUtil.isOptChainNode(parent)) {
        // The parent is a continuation of this optional chain.
        // If it starts a new segment, then this node is the end of a segment
        return parent.isOptionalChainStart();
      } else {
        // The parent doesn't continue this node's optional chain, though it might be part of a
        // different one.
        // e.g. in `a?.(x?.y.z)` the parent of `x?.y.z` is part of a different optional chain.
        return true;
      }
    }
  }

  /**
   * Given the end of an optional chain segment changes all nodes from the end down to the start
   * into non-optional nodes. e.g `({a})?.a.b.c.d()?.x.y.z` gets converted to
   * `({a}).a.b.c.d()?.x.y.z` when the passed in endOfOptChainSegment is `({a})?.a.b.c.d()`.
   */
  static void convertToNonOptionalChainSegment(Node endOfOptChainSegment) {
    checkArgument(isEndOfOptChainSegment(endOfOptChainSegment), endOfOptChainSegment);
    // Since part of changing the nodes removes the isOptionalChainStart() marker we look for to
    // know we're done, this logic is easier to read if we just find all the nodes first, then
    // change them.
    ArrayDeque<Node> segmentNodes = new ArrayDeque<>();
    Node segmentNode = endOfOptChainSegment;
    while (true) {
      checkState(NodeUtil.isOptChainNode(segmentNode), segmentNode);
      segmentNodes.add(segmentNode);
      if (segmentNode.isOptionalChainStart()) {
        break;
      }
      segmentNode = segmentNode.getFirstChild();
    }
    for (Node n : segmentNodes) {
      n.setIsOptionalChainStart(false);
      n.setToken(getNonOptChainToken(n.getToken()));
    }
  }

  private static Token getNonOptChainToken(Token optChainToken) {
    switch (optChainToken) {
      case OPTCHAIN_CALL:
        return Token.CALL;
      case OPTCHAIN_GETELEM:
        return Token.GETELEM;
      case OPTCHAIN_GETPROP:
        return Token.GETPROP;
      default:
        throw new IllegalStateException("Should be an OPTCHAIN token: " + optChainToken);
    }
  }

  /**
   * Is this node the name of a block-scoped declaration? Checks for let, const, class, or
   * block-scoped function declarations.
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
   * @return True if {@code n} is a VAR, LET or CONST that contains a destructuring pattern.
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
   *
   * @return The value node representing the new value.
   */
  public static @Nullable Node getAssignedValue(Node n) {
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
   * @return True if {@code n} is EXPR_RESULT and {@code n}'s first child is ASSIGN
   */
  static boolean isExprAssign(Node n) {
    return n.isExprResult() && n.getFirstChild().isAssign();
  }

  /**
   * Is this node a call expression statement?
   *
   * @param n The node
   * @return True if {@code n} is EXPR_RESULT and {@code n}'s first child is CALL
   */
  public static boolean isExprCall(Node n) {
    return n.isExprResult() && n.getFirstChild().isCall();
  }

  static boolean isNonArrowFunction(Node n) {
    return n.isFunction() && !n.isArrowFunction();
  }

  public static boolean isEnhancedFor(Node n) {
    return n.isForOf() || n.isForAwaitOf() || n.isForIn();
  }

  public static boolean isAnyFor(Node n) {
    return n.isVanillaFor() || n.isForIn() || n.isForOf() || n.isForAwaitOf();
  }

  /** Determines whether the given node is a FOR, DO, or WHILE node. */
  public static boolean isLoopStructure(Node n) {
    switch (n.getToken()) {
      case FOR:
      case FOR_IN:
      case FOR_OF:
      case FOR_AWAIT_OF:
      case DO:
      case WHILE:
        return true;
      default:
        return false;
    }
  }

  /**
   * @param n The node to inspect.
   * @return If the node, is a FOR, WHILE, or DO, it returns the node for the code BLOCK, null
   *     otherwise.
   */
  public static @Nullable Node getLoopCodeBlock(Node n) {
    switch (n.getToken()) {
      case FOR:
      case FOR_IN:
      case FOR_OF:
      case FOR_AWAIT_OF:
      case WHILE:
        return n.getLastChild();
      case DO:
        return n.getFirstChild();
      default:
        return null;
    }
  }

  /**
   * @return Whether the specified node has a loop parent that is within the current scope.
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

  /** Determines whether the given node is a FOR, DO, WHILE, WITH, or IF node. */
  public static boolean isControlStructure(Node n) {
    switch (n.getToken()) {
      case FOR:
      case FOR_IN:
      case FOR_OF:
      case FOR_AWAIT_OF:
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

  /** Determines whether the given node is code node for FOR, DO, WHILE, WITH, or IF node. */
  static boolean isControlStructureCodeBlock(Node parent, Node n) {
    switch (parent.getToken()) {
      case DO:
        return parent.getFirstChild() == n;
      case TRY:
        return parent.getFirstChild() == n || parent.getLastChild() == n;
      case FOR:
      case FOR_IN:
      case FOR_OF:
      case FOR_AWAIT_OF:
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
   *
   * @param n a node with an outgoing conditional CFG edge
   * @return the condition node or null if the condition is not obviously a node
   */
  static @Nullable Node getConditionExpression(Node n) {
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
      case FOR_AWAIT_OF:
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
    return n.isRoot() || n.isScript() || n.isBlock() || n.isModuleBody();
  }

  /**
   * A block scope is created by a non-synthetic block node, a for loop node, or a for-of loop node.
   *
   * <p>Note: for functions, we use two separate scopes for parameters and declarations in the body.
   * We need to make sure default parameters cannot reference var / function declarations in the
   * body.
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
      case FOR_AWAIT_OF:
      case SWITCH:
      case CLASS:
        return true;
      default:
        return false;
    }
  }

  static boolean createsScope(Node n) {
    return createsBlockScope(n)
        || n.isFunction()
        || n.isModuleBody()
        || n.isMemberFieldDef()
        || n.isComputedFieldDef()
        // The ROOT nodes that are the root of the externs tree or main JS tree do not
        // create scopes. The parent of those two, which is the root of the entire AST and
        // therefore has no parent, is the only ROOT node that creates a scope.
        || (n.isRoot() && n.getParent() == null);
  }

  private static final ImmutableSet<Token> DEFINITE_CFG_ROOTS =
      immutableEnumSet(Token.FUNCTION, Token.SCRIPT, Token.MODULE_BODY, Token.ROOT);

  static boolean isValidCfgRoot(Node n) {
    return DEFINITE_CFG_ROOTS.contains(n.getToken()) || isClassStaticBlock(n);
  }

  /**
   * @return Whether the node is used as a statement.
   */
  public static boolean isStatement(Node n) {
    return !n.isModuleBody() && !n.isScript() && !n.isRoot() && isStatementParent(n.getParent());
  }

  private static final ImmutableSet<Token> IS_STATEMENT_PARENT =
      immutableEnumSet(
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
   * @return Whether the node is a reference to a variable, function, class or function parameter
   *     (not a label or an empty function expression name).
   */
  static boolean isReferenceName(Node n) {
    return n.isName() && !n.getString().isEmpty();
  }

  /**
   * Returns whether the given name in an import or export spec is not defined within the module,
   * but is an exported name from this or another module.
   *
   * <p>Examples include `nonlocal` in:
   *
   * <ul>
   *   <li>export {a as nonlocal};
   *   <li>import {nonlocal} from './foo.js';
   *   <li>import {nonlocal as a} from './foo.js';
   *   <li>export {nonlocal as a} from './foo.js';
   *   <li>export {a as nonlocal} from './foo.js';
   * </ul>
   *
   * @param n a NAME node.
   */
  static boolean isNonlocalModuleExportName(Node n) {
    checkArgument(n.isName(), n);
    Node parent = n.getParent();
    if (parent.isImportSpec() && n.isFirstChildOf(parent)) {
      // import {nonlocal as x} from './foo.js'
      return true;
    } else if (parent.isExportSpec()) {
      if (n.isFirstChildOf(parent)) {
        // export {nonlocal as b} from './foo.js';
        return isExportFrom(parent.getGrandparent());
      } else {
        // export {local as nonlocal};
        return true;
      }
    }
    return false;
  }

  /** Whether the child node is the FINALLY block of a try. */
  static boolean isTryFinallyNode(Node parent, Node child) {
    return parent.isTry() && parent.hasXChildren(3) && child == parent.getLastChild();
  }

  /** Whether the node is a CATCH container BLOCK. */
  static boolean isTryCatchNodeContainer(Node n) {
    Node parent = n.getParent();
    return parent.isTry() && parent.getSecondChild() == n;
  }

  // TODO(tbreisacher): Add a method for detecting nodes injected as runtime libraries.
  static boolean isInSyntheticScript(Node n) {
    String sourceFileName = n.getSourceFileName();
    return sourceFileName != null
        && (sourceFileName.startsWith(" [synthetic:")
            || sourceFileName.startsWith(AbstractCompiler.RUNTIME_LIB_DIR));
  }

  /**
   * Permanently delete the given node from the AST, as well as report the related AST
   * changes/deletions to the given compiler.
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
   * deleting the parent from the AST and is come cases expression is replaced by {@code undefined}.
   */
  public static void deleteFunctionCall(Node n, AbstractCompiler compiler) {
    checkState(n.isCall());

    Node parent = n.getParent();
    if (parent.isExprResult()) {
      Node grandParent = parent.getParent();
      parent.detach();
      parent = grandParent;
    } else {
      // Seems like part of more complex expression, fallback to replacing with no-op.
      n.replaceWith(newUndefinedNode(n));
    }

    NodeUtil.markFunctionsDeleted(n, compiler);
    compiler.reportChangeToEnclosingScope(parent);
  }

  /** Permanently delete all the children of the given node, including reporting changes. */
  public static void deleteChildren(Node n, AbstractCompiler compiler) {
    while (n.hasChildren()) {
      deleteNode(n.getFirstChild(), compiler);
    }
  }

  /**
   * Safely remove children while maintaining a valid node structure. In some cases, this is done by
   * removing the parent from the AST as well.
   */
  public static void removeChild(Node parent, Node node) {
    if (isTryFinallyNode(parent, node)) {
      if (NodeUtil.hasCatchHandler(getCatchBlock(parent))) {
        // A finally can only be removed if there is a catch.
        node.detach();
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
    } else if (node.isBlock()) {
      // Simply empty the block.  This maintains source location and
      // "synthetic"-ness.
      node.detachChildren();
    } else if (isStatementBlock(parent) || isSwitchCase(node) || node.isMemberFunctionDef()) {
      // A statement in a block or a member function can simply be removed
      node.detach();
    } else if (isNameDeclaration(parent) || parent.isExprResult()) {
      if (parent.hasMoreThanOneChild()) {
        node.detach();
      } else {
        // Remove the node from the parent, so it can be reused.
        node.detach();
        // This would leave an empty VAR, remove the VAR itself.
        removeChild(parent.getParent(), parent);
      }
    } else if (parent.isLabel() && node == parent.getLastChild()) {
      // Remove the node from the parent, so it can be reused.
      node.detach();
      // A LABEL without children can not be referred to, remove it.
      removeChild(parent.getParent(), parent);
    } else if (parent.isVanillaFor()) {
      // Only Token.FOR can have an Token.EMPTY other control structure
      // need something for the condition. Others need to be replaced
      // or the structure removed.
      node.replaceWith(IR.empty());
    } else if (parent.isObjectPattern()) {
      // Remove the name from the object pattern
      node.detach();
    } else if (parent.isArrayPattern()) {
      if (node == parent.getLastChild()) {
        node.detach();
      } else {
        node.replaceWith(IR.empty());
      }
    } else if (parent.isDestructuringLhs()) {
      // Destructuring is empty so we should remove the node
      node.detach();
      if (parent.getParent().hasChildren()) {
        // removing the destructuring could leave an empty variable declaration node, so we would
        // want to remove it from the AST
        removeChild(parent.getParent(), parent);
      }
    } else if (parent.isRest()) {
      // Rest params can only ever have one child node
      parent.detach();
    } else if (parent.isParamList()) {
      node.detach();
    } else if (parent.isImport()) {
      // An import node must always have three child nodes. Only the first can be safely removed.
      if (node == parent.getFirstChild()) {
        node.replaceWith(IR.empty());
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
    if (decl.hasOneChild()) {
      decl.replaceWith(newStatement);
    } else if (declChild.getNext() == null) {
      declChild.detach();
      newStatement.insertAfter(decl);
    } else if (declChild.getPrevious() == null) {
      declChild.detach();
      newStatement.insertBefore(decl);
    } else {
      checkState(decl.hasMoreThanOneChild());
      Node newDecl = new Node(decl.getToken()).srcref(decl);
      for (Node after = declChild.getNext(), next; after != null; after = next) {
        next = after.getNext();
        newDecl.addChildToBack(after.detach());
      }
      declChild.detach();
      newStatement.insertAfter(decl);
      newDecl.insertAfter(newStatement);
    }
  }

  /** Add a finally block if one does not exist. */
  static void maybeAddFinally(Node tryNode) {
    checkState(tryNode.isTry());
    if (!NodeUtil.hasFinally(tryNode)) {
      tryNode.addChildToBack(IR.block().srcref(tryNode));
    }
  }

  /**
   * Merge a block with its parent block.
   *
   * @param ignoreBlockScopedDeclarations merge the block regardless of any inner block-scoped
   *     declarations that may cause name collisions. use if e.g. the AST is normalized
   * @return Whether the block was removed.
   */
  public static boolean tryMergeBlock(Node block, boolean ignoreBlockScopedDeclarations) {
    checkState(block.isBlock());
    Node parent = block.getParent();
    boolean canMerge = ignoreBlockScopedDeclarations || canMergeBlock(block);
    // Try to remove the block if its parent is a block/script or if its
    // parent is label and it has exactly one child.
    if (isStatementBlock(parent) && canMerge) {
      Node previous = block;
      while (block.hasChildren()) {
        Node child = block.removeFirstChild();
        child.insertAfter(previous);
        previous = child;
      }
      block.detach();
      return true;
    } else {
      return false;
    }
  }

  /**
   * A check inside a block to see if there are const, let, class, or function declarations to be
   * safe and not hoist them into the upper block.
   *
   * @return Whether the block can be removed
   */
  public static boolean canMergeBlock(Node block) {
    for (Node c = block.getFirstChild(); c != null; c = c.getNext()) {
      switch (c.getToken()) {
        case LABEL:
          if (canMergeBlock(c)) {
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
    return node.isCall() || node.isNew() || node.isOptChainCall();
  }

  /** Return a BLOCK node for the given FUNCTION node. */
  public static Node getFunctionBody(Node fn) {
    checkArgument(fn.isFunction(), fn);
    return fn.getLastChild();
  }

  /**
   * Returns the call target for a call expression, resolving an indirected call (`(0, foo)()`) if
   * present.
   */
  public static Node getCallTargetResolvingIndirectCalls(Node call) {
    checkArgument(call.isCall() || call.isNew(), "must be call or new expression, got %s", call);
    Node target = call.getFirstChild();
    if (target.isComma() && target.getSecondChild().isQualifiedName()) {
      return target.getSecondChild();
    }
    return target;
  }

  /**
   * Is the node a var, const, let, function, or class declaration? See {@link
   * #isFunctionDeclaration}, {@link #isClassDeclaration}, and {@link #isNameDeclaration}
   */
  static boolean isDeclaration(Node n) {
    return isNameDeclaration(n) || isFunctionDeclaration(n) || isClassDeclaration(n);
  }

  /**
   * Whether this is an assignment to 'exports' that creates named exports.
   *
   * <ul>
   *   <li>exports = {a, b}; // named export, returns true.
   *   <li>exports = 0; // namespace export, returns false.
   *   <li>exports = {a: 0, b}; // namespace export, returns false.
   * </ul>
   */
  public static boolean isNamedExportsLiteral(Node objectLiteral) {
    if (!objectLiteral.isObjectLit() || !objectLiteral.hasChildren()) {
      return false;
    }
    for (Node key = objectLiteral.getFirstChild(); key != null; key = key.getNext()) {
      if (!key.isStringKey() || key.isQuotedStringKey()) {
        return false;
      }
      if (!key.getFirstChild().isName()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Is this node a function declaration? A function declaration is a function that has a name that
   * is added to the current scope (i.e. a function that is not part of a expression; see {@link
   * #isFunctionExpression}).
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
          // `({ get [expression]() {} })`
          // `({ set [expression](x) {} })`
          // (but not `({ [expression]: function() {} })`
          // The first child is the expression, and could possibly be a function.
          return parent.getLastChild() == n
              && (parent.getBooleanProp(Node.COMPUTED_PROP_METHOD)
                  || parent.getBooleanProp(Node.COMPUTED_PROP_GETTER)
                  || parent.getBooleanProp(Node.COMPUTED_PROP_SETTER));
        default:
          return false;
      }
    } else {
      return false;
    }
  }

  /** Is this a class declaration. */
  public static boolean isClassDeclaration(Node n) {
    return n.isClass() && isDeclarationParent(n.getParent()) && isNamedClass(n);
  }

  /**
   * Is this node a hoisted function declaration? A function declaration in the scope root is
   * hoisted to the top of the scope. See {@link #isFunctionDeclaration}).
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
    return n.isBlock() && n.hasParent() && n.getParent().isFunction();
  }

  static boolean isClassStaticBlock(Node n) {
    return n.isBlock() && n.hasParent() && n.getParent().isClassMembers();
  }

  /**
   * Is a FUNCTION node a function expression?
   *
   * <p>A function expression is a function that:
   *
   * <ul>
   *   <li>has either no name or a name that is not added to the current scope
   *   <li>AND can be manipulated as an expression (assigned to variables, passed to functions,
   *       etc.) i.e. It is not a method declaration on a class or object literal.
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
    return n.isFunction() && !NodeUtil.isFunctionDeclaration(n) && !NodeUtil.isMethodDeclaration(n);
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
   * @return Whether the node is both a function expression and the function is named.
   */
  static boolean isNamedClassExpression(Node n) {
    return NodeUtil.isClassExpression(n) && n.getFirstChild().isName();
  }

  /**
   * Returns whether n is a function with a nonempty name. Differs from {@link
   * #isFunctionDeclaration} because the name might in a function expression and not be added to the
   * current scope.
   *
   * <p>Some named functions include
   *
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
   * Returns whether this is a bleeding function (an anonymous named function that bleeds into the
   * inner scope).
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
    return referencesArgumentsHelper(fn.getSecondChild())
        || referencesArgumentsHelper(fn.getLastChild());
  }

  /**
   * @return Whether any child is a reference to the "arguments" object of the root. Effectively,
   *     this includes arrow method bodies (which don't have their own) and excludes other functions
   *     which shadow the "arguments" value with their own.
   */
  private static boolean referencesArgumentsHelper(Node node) {
    if (node.isName() && node.getString().equals("arguments")) {
      return true;
    }

    if (NodeUtil.isNonArrowFunction(node)) {
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
   * <code>
   * a.f(...)
   * a?.f(...)
   * a['f'](...)
   * </code>
   *
   * @return Whether node is a call to methodName.
   */
  static boolean isObjectCallMethod(Node callNode, String methodName) {
    if (callNode.isCall() || callNode.isOptChainCall()) {
      Node callee = callNode.getFirstChild();
      if (isNormalOrOptChainGetProp(callee)) {
        return callee.getString().equals(methodName);
      } else if (isNormalOrOptChainGet(callee)) {
        Node last = callee.getLastChild();
        if (last != null && last.isStringLit()) {
          String propName = last.getString();
          return (propName.equals(methodName));
        }
      }
    }
    return false;
  }

  /**
   * @return Whether the callNode represents an expression in the form of: x.call(...)
   *     x['call'](...)
   */
  static boolean isFunctionObjectCall(Node callNode) {
    return isObjectCallMethod(callNode, "call");
  }

  /**
   * @return Whether the callNode represents an expression in the form of: x.apply(...)
   *     x['apply'](...)
   */
  static boolean isFunctionObjectApply(Node callNode) {
    return isObjectCallMethod(callNode, "apply");
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
  public static boolean isNameDeclOrSimpleAssignLhs(Node n, Node parent) {
    return (parent.isAssign() && parent.getFirstChild() == n) || NodeUtil.isNameDeclaration(parent);
  }

  /**
   * Determines whether this node is used as an L-value. Notice that sometimes names are used as
   * both L-values and R-values.
   *
   * <p>We treat "var x;" and "let x;" as an L-value because it's syntactically similar to "var x =
   * undefined", even though it's technically not an L-value. But it kind of makes sense if you
   * treat it as "assignment to 'undefined' at the top of the scope".
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
      case ITER_REST:
      case OBJECT_REST:
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
      case FOR_AWAIT_OF:
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

  public static boolean isLhsOfAssign(Node n) {
    Node parent = n.getParent();
    return parent != null && parent.isAssign() && parent.getFirstChild() == n;
  }

  public static boolean isImportedName(Node n) {
    Node parent = n.getParent();
    return parent.isImport() || (parent.isImportSpec() && parent.getLastChild() == n);
  }

  /**
   * Returns the node that is effectively declaring the given target.
   *
   * <p>Examples:
   *
   * <pre><code>
   *   const a = 1; // getDeclaringParent(a) returns CONST
   *   let {[expression]: [x = 3]} = obj; // getDeclaringParent(x) returns LET
   *   function foo({a, b}) {}; // getDeclaringParent(a) returns PARAM_LIST
   *   function foo(a = 1) {}; // getDeclaringParent(a) returns PARAM_LIST
   *   function foo({a, b} = obj) {}; // getDeclaringParent(a) returns PARAM_LIST
   *   function foo(...a) {}; // getDeclaringParent(a) returns PARAM_LIST
   *   function foo() {}; // gotRootTarget(foo) returns FUNCTION
   *   class foo {}; // gotRootTarget(foo) returns CLASS
   *   import foo from './foo'; // getDeclaringParent(foo) returns IMPORT
   *   import {foo} from './foo'; // getDeclaringParent(foo) returns IMPORT
   *   import {foo as bar} from './foo'; // getDeclaringParent(bar) returns IMPORT
   *   } catch (err) { // getDeclaringParent(err) returns CATCH
   * </code></pre>
   *
   * @param targetNode a NAME, OBJECT_PATTERN, or ARRAY_PATTERN
   * @return node of type LET, CONST, VAR, FUNCTION, CLASS, PARAM_LIST, CATCH, or IMPORT
   * @throws IllegalStateException if targetNode is not actually used as a declaration target
   */
  public static Node getDeclaringParent(Node targetNode) {
    Node rootTarget = getRootTarget(targetNode);
    Node parent = rootTarget.getParent();
    if (parent.isRest() || parent.isDefaultValue()) {
      // e.g. `function foo(targetNode1 = default, ...targetNode2) {}`
      parent = parent.getParent();
      checkState(parent.isParamList(), parent);
    } else if (parent.isDestructuringLhs()) {
      // e.g. `let [a, b] = something;` targetNode is `[a, b]`
      parent = parent.getParent();
      checkState(isNameDeclaration(parent), parent);
    } else if (parent.isClass() || parent.isFunction()) {
      // e.g. `function targetNode() {}`
      // e.g. `class targetNode {}`
      checkState(targetNode == parent.getFirstChild(), targetNode);
    } else if (parent.isImportSpec()) {
      // e.g. `import {foo as targetNode} from './foo';
      checkState(targetNode == parent.getSecondChild(), targetNode);
      // import -> import_specs -> import_spec
      // we want import
      parent = parent.getGrandparent();
      checkState(parent.isImport(), parent);
    } else {
      // e.g. `function foo(targetNode) {};`
      // e.g. `let targetNode = something;`
      // e.g. `import targetNode from './foo';
      // e.g. `} catch (foo) {`
      checkState(
          parent.isParamList()
              || isNameDeclaration(parent)
              || parent.isImport()
              || parent.isCatch(),
          parent);
    }
    return parent;
  }

  /**
   * Returns the outermost target enclosing the given assignment target.
   *
   * <p>Returns targetNode itself if there is no enclosing target.
   *
   * <p>Examples:
   *
   * <pre><code>
   *   const a = 1; // getRootTarget(a) returns a
   *   let {[expression]: [x = 3]} = obj; // getRootTarget(x) returns {[expression]: [x = 3]}
   *   {a = 1} = obj; // getRootTarget(a) returns {a = 1}
   *   {[expression]: [x = 3]} = obj; // getRootTarget(x) returns {[expression]: [x = 3]}
   *   function foo({a, b}) {}; // getRootTarget(a) returns {a, b}
   *   function foo(a = 1) {}; // getRootTarget(a) returns a
   *   function foo({a, b} = obj) {}; // getRootTarget(a) returns a
   *   function foo(...a) {}; // getRootTarget(a) returns a
   *   function foo() {}; // gotRootTarget(foo) returns foo
   *   class foo {}; // gotRootTarget(foo) returns foo
   *   import foo from './foo'; // getRootTarget(foo) returns foo
   *   import {foo} from './foo'; // getRootTarget(foo) returns foo
   *   import {foo as bar} from './foo'; // getRootTarget(bar) returns bar
   * </code></pre>
   *
   * @throws IllegalStateException if targetNode is not actually used as a target
   */
  public static Node getRootTarget(Node targetNode) {
    Node enclosingTarget = targetNode;
    for (Node nextTarget = getEnclosingTarget(enclosingTarget);
        nextTarget != null;
        nextTarget = getEnclosingTarget(enclosingTarget)) {
      enclosingTarget = nextTarget;
    }
    return enclosingTarget;
  }

  /**
   * Returns the immediately enclosing target node for a given target node, or null if none found.
   *
   * @see #getRootTarget(Node) for examples
   */
  private static @Nullable Node getEnclosingTarget(Node targetNode) {
    checkState(checkNotNull(targetNode).isValidAssignmentTarget(), targetNode);
    Node parent = checkNotNull(targetNode.getParent(), targetNode);
    boolean targetIsFirstChild = parent.getFirstChild() == targetNode;
    if (parent.isDefaultValue() || parent.isRest()) {
      // in `([something = targetNode] = x)` targetNode isn't actually acting
      // as a target.
      checkState(targetIsFirstChild, parent);
      // The DEFAULT_VALUE or REST occupies the place where the assignment target it contains would
      // otherwise be in the AST, so pretend it is the target for the logic below.
      targetNode = parent;
      parent = checkNotNull(targetNode.getParent());
      targetIsFirstChild = targetNode == parent.getFirstChild();
    }
    switch (parent.getToken()) {
      case ARRAY_PATTERN:
        // e.g. ([targetNode] = something)
        return parent;

      case OBJECT_PATTERN:
        // e.g. ({...rest} = something);
        return parent;

      case COMPUTED_PROP:
        // e.g. ({[expression]: targetNode} = something)
        // e.g. ({[expression]: targetNode = default} = something)
        // make sure the effective target (targetNode or DEFAULT_VALUE containing it)
        // isn't the expression part
        checkState(!targetIsFirstChild, parent);
        // otherwise the same as STRING_KEY so fall through
      case STRING_KEY:
        // e.g. ({parent: targetNode} = something)
        Node grandparent = checkNotNull(parent.getParent(), parent);
        checkState(grandparent.isObjectPattern(), grandparent);
        return grandparent;

      case PARAM_LIST:
        // e.g. `function foo(targetNode) {}`
      case LET:
      case CONST:
      case VAR:
        // non-destructured declarations
        // e.g. `let targetNode = 3;`
        return null;

      case FUNCTION:
      case CLASS:
        // e.g. `function targetNode() {}`
        // e.g. `class targetNode {}`
        checkState(targetIsFirstChild, targetNode);
        return null;

      case FOR_IN:
      case FOR_OF:
      case FOR_AWAIT_OF:
        // e.g. `for ({length} in obj) {}` // targetNode is `{length}`
        // e.g. `for ({length} of obj) {}` // targetNode is `{length}`
        checkState(targetIsFirstChild, targetNode);
        return null;

      case DESTRUCTURING_LHS:
        // destructured declarations
        // e.g. `let [a] = 3`; // targetNode is `[a]`
        checkState(targetIsFirstChild, targetNode);
        return null;

      case IMPORT:
        // e.g. `import targetNode from './foo/bar';`
        return null;

      case IMPORT_SPEC:
        // e.g. `import {bar as targetNode} from './foo/bar';`
        // e.g. `import {targetNode} from './foo/bar';` // AST will have {targetNode as targetNode}
        checkState(!targetIsFirstChild, parent);
        return null;

      case CATCH:
        // e.g. `try {} catch (foo) {}`
        return null;

      default:
        // e.g. targetNode = something
        checkState(isAssignmentOp(parent) && targetIsFirstChild, parent);
        return null;
    }
  }

  /**
   * Returns true if the node is a lhs value of a destructuring assignment.
   *
   * <p>For example, x in {@code var [x] = [1];}, {@code var [...x] = [1];}, and {@code var {a: x} =
   * {a: 1}} or a.b in {@code ([a.b] = [1]);} or {@code ({key: a.b} = {key: 1});}
   */
  public static boolean isLhsByDestructuring(Node n) {
    switch (n.getToken()) {
      case NAME:
      case GETPROP:
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
      case ARRAY_PATTERN: // `b` in `var [b] = ...`
      case ITER_REST:
      case OBJECT_REST: // `b` in `var [...b] = ...`
        return true;

      case COMPUTED_PROP:
        if (n.isFirstChildOf(parent)) {
          return false;
        }
        // Fall through.
      case STRING_KEY:
        return grandparent.isObjectPattern(); // the "b" in "var {a: b} = ..."

      case DEFAULT_VALUE:
        if (n.isFirstChildOf(parent)) {
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
   * Determines whether a node represents a possible object literal key (e.g. key1 in {key1: value1,
   * key2: value2}). Computed properties are excluded here (see b/111621528). This method does not
   * check whether the node is actually in an object literal! it also returns true for object
   * pattern keys, and member functions/getters in ES6 classes.
   *
   * @param node A node
   */
  // TODO(b/189993301): should fields be added to this method?
  static boolean mayBeObjectLitKey(Node node) {
    switch (node.getToken()) {
      case STRING_KEY:
      case GETTER_DEF:
      case SETTER_DEF:
      case MEMBER_FUNCTION_DEF:
        return true;
      default:
        return false;
    }
  }

  /**
   * Determines whether a node represents an object literal key (e.g. key1 in {key1: value1, key2:
   * value2}) and is in an object literal. Computed properties are excluded here (see b/111621528).
   *
   * @param node A node
   */
  static boolean isObjectLitKey(Node node) {
    return node.getParent().isObjectLit() && mayBeObjectLitKey(node);
  }

  /**
   * Get the name of an object literal key.
   *
   * @param key A node
   */
  static String getObjectOrClassLitKeyName(Node key) {
    Node keyNode = getObjectOrClassLitKeyNode(key);
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
  static @Nullable Node getObjectOrClassLitKeyNode(Node key) {
    switch (key.getToken()) {
      case STRING_KEY:
      case GETTER_DEF:
      case SETTER_DEF:
      case MEMBER_FUNCTION_DEF:
      case MEMBER_FIELD_DEF:
        return key;
      case COMPUTED_PROP:
      case COMPUTED_FIELD_DEF:
        return key.getFirstChild()
                .isStringLit() // TODO(b/189993301): may be an issue with non string lits
            ? key.getFirstChild()
            : null;
      default:
        break;
    }
    throw new IllegalStateException("Unexpected node type: " + key);
  }

  /**
   * Determines whether a node represents an object literal get or set key (e.g. key1 in {get key1()
   * {}, set key2(a){}).
   *
   * @param node A node
   */
  static boolean isGetOrSetKey(Node node) {
    switch (node.getToken()) {
      case GETTER_DEF:
      case SETTER_DEF:
        return true;
      case COMPUTED_PROP:
        return node.getBooleanProp(Node.COMPUTED_PROP_GETTER)
            || node.getBooleanProp(Node.COMPUTED_PROP_SETTER);
      default:
        break;
    }
    return false;
  }

  /**
   * Converts an operator's token value (see {@link Token}) to a string representation.
   *
   * @param operator the operator's token value to convert
   * @return the string representation or {@code null} if the token value is not an operator
   */
  public static @Nullable String opToStr(Token operator) {
    switch (operator) {
      case COALESCE:
        return "??";
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
      case ASSIGN_OR:
        return "||=";
      case ASSIGN_AND:
        return "&&=";
      case ASSIGN_COALESCE:
        return "??=";
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
   * Converts an operator's token value (see {@link Token}) to a string representation or fails.
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

  /**
   * Given a node tree, finds all the VAR declarations in that tree that are not in an inner scope.
   * Then adds a new VAR node at the top of the current scope that redeclares them, if necessary.
   */
  static void redeclareVarsInsideBranch(Node branch) {
    Collection<Node> vars = getVarsDeclaredInBranch(branch);
    if (vars.isEmpty()) {
      return;
    }

    Node parent = getAddingRoot(branch);
    for (Node nameNode : vars) {
      Node var = IR.var(IR.name(nameNode.getString()).srcref(nameNode)).srcref(nameNode);
      copyNameAnnotations(nameNode, var.getFirstChild());
      parent.addChildToFront(var);
    }
  }

  /** Copy any annotations that follow a named value. */
  static void copyNameAnnotations(Node source, Node destination) {
    if (source.getBooleanProp(Node.IS_CONSTANT_NAME)) {
      destination.putBooleanProp(Node.IS_CONSTANT_NAME, true);
    }
  }

  /**
   * Gets a Node at the top of the current scope where we can add new var declarations as children.
   */
  private static Node getAddingRoot(Node n) {
    Node addingRoot = null;
    Node ancestor = n;
    crawl_ancestors:
    while (null != (ancestor = ancestor.getParent())) {
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
    checkState(addingRoot.isBlock() || addingRoot.isModuleBody() || addingRoot.isScript());
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
  public static Node newQName(AbstractCompiler compiler, String name) {
    int endPos = name.indexOf('.');
    if (endPos == -1) {
      endPos = name.length();
    }

    Node qname;
    String nodeName = name.substring(0, endPos);
    if ("this".equals(nodeName)) {
      qname = IR.thisNode();
    } else if ("super".equals(nodeName)) {
      qname = IR.superNode();
    } else {
      qname = newName(compiler, nodeName);
    }
    qname.setLength(endPos);

    for (int startPos = endPos + 1; endPos < name.length(); startPos = endPos + 1) {
      endPos = name.indexOf('.', startPos);
      if (endPos == -1) {
        endPos = name.length();
      }

      String part = name.substring(startPos, endPos);
      qname = IR.getprop(qname, part);
      if (compiler.getCodingConvention().isConstantKey(part)) {
        qname.putBooleanProp(Node.IS_CONSTANT_NAME, true);
      }
      qname.setLength(part.length());
    }

    return qname;
  }

  /**
   * Creates a node representing a qualified name, copying over the source location information from
   * the basis node and assigning the given original name to the node.
   *
   * @param name A qualified name (e.g. "foo" or "foo.bar.baz")
   * @param basisNode The node that represents the name as currently found in the AST.
   * @param originalName The original name of the item being represented by the NAME node. Used for
   *     debugging information.
   * @return A NAME or GETPROP node
   */
  static Node newQName(
      AbstractCompiler compiler, String name, Node basisNode, String originalName) {
    Node node = newQName(compiler, name);
    node.srcrefTreeIfMissing(basisNode);
    if (!originalName.equals(node.getOriginalName())) {
      // If basisNode already had the correct original name, then it will already be set correctly.
      // Setting it again will force the QName node to have a different property list from all of
      // its children, causing greater memory consumption.
      node.setOriginalName(originalName);
    }
    return node;
  }

  /**
   * Attaches nameNode to a new qualified name declaration and returns the new qualified declaration
   *
   * @return a new qualified name declaration
   */
  static Node getDeclarationFromName(Node nameNode, Node value, Token type, JSDocInfo info) {
    Node result;
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

  /** Creates a property access on the {@code context} tree. */
  public static Node newPropertyAccess(AbstractCompiler compiler, Node context, String name) {
    Node propNode = IR.getprop(context, name);
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
    Node nameNode = newQName(compiler, name);
    return getDeclarationFromName(nameNode, value, type, info);
  }

  /** Gets the root node of a qualified name. Must be either NAME, THIS or SUPER. */
  public static Node getRootOfQualifiedName(Node qName) {
    for (Node current = qName; true; current = current.getFirstChild()) {
      if (current.isName() || current.isThis() || current.isSuper()) {
        return current;
      }
      checkState(current.isGetProp(), "Not a getprop node:  (%s)", current);
    }
  }

  /** Gets the root node of a string representing a qualified name. */
  static String getRootOfQualifiedName(String qName) {
    int dot = qName.indexOf('.');
    if (dot == -1) {
      return qName;
    }
    return qName.substring(0, dot);
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
   * Creates a new node representing an *existing* name, copying over the source location
   * information from the basis node.
   *
   * @param name The name for the new NAME node.
   * @param srcref The node that represents the name as currently found in the AST.
   * @return The node created.
   */
  static Node newName(AbstractCompiler compiler, String name, Node srcref) {
    return newName(compiler, name).srcref(srcref);
  }

  /**
   * Creates a new node representing an *existing* name, copying over the source location
   * information from the basis node and assigning the given original name to the node.
   *
   * @param name The name for the new NAME node.
   * @param basisNode The node that represents the name as currently found in the AST.
   * @param originalName The original name of the item being represented by the NAME node. Used for
   *     debugging information.
   * @return The node created.
   */
  static Node newName(AbstractCompiler compiler, String name, Node basisNode, String originalName) {
    Node nameNode = newName(compiler, name, basisNode);
    nameNode.setOriginalName(originalName);
    return nameNode;
  }

  /**
   * Test if all characters in the string are in the Basic Latin (aka ASCII) character set - that
   * they have UTF-16 values equal to or below 0x7f. This check can find which identifiers with
   * Unicode characters need to be escaped in order to allow resulting files to be processed by
   * non-Unicode aware UNIX tools and editors. * See
   * http://en.wikipedia.org/wiki/Latin_characters_in_Unicode for more on Basic Latin.
   *
   * @param s The string to be checked for ASCII-goodness.
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

  /** Determines whether the given name is a valid variable name. */
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

  @InlineMe(
      replacement = "NodeUtil.isValidQualifiedName(mode.toFeatureSet(), name)",
      imports = "com.google.javascript.jscomp.NodeUtil")
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

  /** Retrieves vars declared in the current node tree, excluding descent scopes. */
  static Collection<Node> getVarsDeclaredInBranch(Node root) {
    VarCollector collector = new VarCollector();
    visitPreOrder(root, collector, MATCH_NOT_FUNCTION);
    return collector.vars.values();
  }

  private static void getLhsNodesHelper(Node n, Consumer<Node> consumer) {
    switch (n.getToken()) {
      case IMPORT:
        getLhsNodesHelper(n.getFirstChild(), consumer);
        getLhsNodesHelper(n.getSecondChild(), consumer);
        return;
      case VAR:
      case CONST:
      case LET:
      case OBJECT_PATTERN:
      case ARRAY_PATTERN:
      case PARAM_LIST:
      case IMPORT_SPECS:
        for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
          getLhsNodesHelper(child, consumer);
        }
        return;
      case DESTRUCTURING_LHS:
      case DEFAULT_VALUE:
      case CATCH:
      case ITER_REST:
      case OBJECT_REST:
      case CAST:
        getLhsNodesHelper(n.getFirstChild(), consumer);
        return;
      case IMPORT_SPEC:
      case COMPUTED_PROP:
      case STRING_KEY:
        getLhsNodesHelper(n.getLastChild(), consumer);
        return;
      case NAME:
      case IMPORT_STAR:
        consumer.accept(n);
        return;
      case GETPROP:
      case GETELEM:
        // Not valid in declarations but may appear in assignments.
        consumer.accept(n);
        return;
      case EMPTY:
        return;
      case FOR_IN:
      case FOR_OF:
      case FOR_AWAIT_OF:
        // Enhanced for loops assign to variables in their first child
        // e.g.
        // for (some.prop in someObj) {...
        // for ({a, b} of someIterable) {...
        getLhsNodesHelper(n.getFirstChild(), consumer);
        return;
      default:
        if (isAssignmentOp(n)) {
          getLhsNodesHelper(n.getFirstChild(), consumer);
        } else {
          throw new IllegalStateException("Invalid node in lhs: " + n);
        }
    }
  }

  /**
   * Retrieves lhs nodes declared or assigned in a given assigning parent node.
   *
   * <p>An assigning parent node is one that assigns a value to one or more LHS nodes.
   */
  public static void visitLhsNodesInNode(Node assigningParent, Consumer<Node> consumer) {
    checkArgument(
        isNameDeclaration(assigningParent)
            || assigningParent.isParamList()
            || isAssignmentOp(assigningParent)
            || assigningParent.isCatch()
            || assigningParent.isDestructuringLhs()
            || assigningParent.isDefaultValue()
            || assigningParent.isImport()
            // enhanced for loops assign to loop variables
            || isEnhancedFor(assigningParent),
        assigningParent);
    getLhsNodesHelper(assigningParent, consumer);
  }

  /** Returns {@code true} if the node is a definition with Object.defineProperties */
  public static boolean isObjectDefinePropertiesDefinition(Node n) {
    // We intentionally don't check optional Object.defineProperties?.() because we expect it to be
    // defined or polyfill-ed, and to avoid changing the current invariant that this method returns
    // true only for non-optional `Object.defineProperties()`.
    if (!n.isCall() || !n.hasXChildren(3)) {
      return false;
    }
    Node getprop = n.getFirstChild();
    if (!getprop.isGetProp()) {
      return false;
    }
    return getprop.getString().equals("defineProperties")
        && isKnownGlobalObjectReference(getprop.getFirstChild());
  }

  private static final QualifiedName GLOBAL_OBJECT = QualifiedName.of("$jscomp.global.Object");
  private static final QualifiedName GLOBAL_OBJECT_MANGLED =
      QualifiedName.of("$jscomp$global.Object");

  private static boolean isKnownGlobalObjectReference(Node n) {
    switch (n.getToken()) {
      case NAME:
        return n.getString().equals("Object");
      case GETPROP:
        return GLOBAL_OBJECT.matches(n) || GLOBAL_OBJECT_MANGLED.matches(n);
      default:
        return false;
    }
  }

  /** Returns {@code true} if the node is a definition with Object.defineProperty. */
  static boolean isObjectDefinePropertyDefinition(Node n) {
    if (!n.isCall() || !n.hasXChildren(4)) {
      return false;
    }
    Node getprop = n.getFirstChild();
    if (!getprop.isGetProp()) {
      return false;
    }
    return getprop.getString().equals("defineProperty")
        && isKnownGlobalObjectReference(getprop.getFirstChild());
  }

  /**
   * @return A list of STRING_KEY properties defined by a Object.defineProperties(o, {...}) call
   */
  static Iterable<Node> getObjectDefinedPropertiesKeys(Node definePropertiesCall) {
    checkArgument(NodeUtil.isObjectDefinePropertiesDefinition(definePropertiesCall));
    List<Node> properties = new ArrayList<>();
    Node objectLiteral = definePropertiesCall.getLastChild();
    for (Node key = objectLiteral.getFirstChild(); key != null; key = key.getNext()) {
      if (!key.isStringKey()) {
        continue;
      }
      properties.add(key);
    }
    return properties;
  }

  /**
   * @return {@code true} if the node an assignment to a prototype property of some constructor.
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
    return recv.isGetProp() && recv.getString().equals("prototype");
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
    return parent.isAssign()
        && getProp.isFirstChildOf(parent)
        && getProp.getString().equals("prototype");
  }

  /**
   * Determines whether this node is testing for the existence of a property. If true, we will not
   * emit warnings about a missing property.
   *
   * @param propAccess The GETPROP or GETELEM being tested.
   */
  static boolean isPropertyTest(AbstractCompiler compiler, Node propAccess) {
    Node parent = propAccess.getParent();
    switch (parent.getToken()) {
      case CALL:
        return parent.getFirstChild() != propAccess
            && compiler.getCodingConvention().isPropertyTestFunction(parent);

      case OPTCHAIN_CALL:
      case OPTCHAIN_GETELEM:
        return parent.getFirstChild() == propAccess;

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
      case COALESCE:
      case OPTCHAIN_GETPROP:
        return true;

      case NE:
      case SHNE:
        {
          Node other =
              parent.getFirstChild() == propAccess
                  ? parent.getSecondChild()
                  : parent.getFirstChild();
          return isUndefined(other) || (parent.isNE() && other.isNull());
        }

      case HOOK:
        return parent.getFirstChild() == propAccess;

      case NOT:
        return parent.getParent().isOr() && parent.getParent().getFirstChild() == parent;

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
      case SHEQ:
        {
          Node other =
              parent.getFirstChild() == propAccess
                  ? parent.getSecondChild()
                  : parent.getFirstChild();
          return isUndefined(other) || (parent.isEQ() && other.isNull());
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
  static @Nullable Node getPrototypeClassName(Node qName) {
    if (!qName.isGetProp()) {
      return null;
    }
    if (qName.getString().equals("prototype")) {
      return qName.getFirstChild();
    }
    Node recv = qName.getFirstChild();
    if (recv.isGetProp() && recv.getString().equals("prototype")) {
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

  /** Create a node for an empty result expression: "void 0" */
  public static Node newUndefinedNode(Node srcReferenceNode) {
    Node node = IR.voidNode(IR.number(0));
    if (srcReferenceNode != null) {
      node.srcrefTree(srcReferenceNode);
    }
    return node;
  }

  /** Create a VAR node containing the given name and initial value expression. */
  static Node newVarNode(String name, Node value) {
    Node lhs = IR.name(name);
    if (value != null) {
      lhs.srcref(value);
    }
    return newVarNode(lhs, value);
  }

  /**
   * Create a VAR node containing the given lhs (name or destructuring pattern) and initial value
   * expression.
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

  /** A predicate for matching name nodes with the specified node. */
  static class MatchNameNode implements Predicate<Node> {
    final String name;

    MatchNameNode(String name) {
      this.name = name;
    }

    @Override
    public boolean apply(Node n) {
      return n.isName() && n.getString().equals(name);
    }
  }

  /** A predicate for matching nodes with the specified type. */
  static class MatchNodeType implements Predicate<Node> {
    final Token type;

    MatchNodeType(Token type) {
      this.type = type;
    }

    @Override
    public boolean apply(Node n) {
      return n.getToken() == type;
    }
  }

  /** A predicate for matching var, let, const, class or function declarations. */
  static class MatchDeclaration implements Predicate<Node> {
    @Override
    public boolean apply(Node n) {
      return isDeclaration(n);
    }
  }

  static final Predicate<Node> MATCH_NOT_FUNCTION = n -> !n.isFunction();

  /**
   * A predicate for matching anything except for a non-arrow function.
   *
   * <p>Useful to avoid traversing into scopes that don't share the same values for {@code this},
   * {@code super}, or {@code arguments}.
   */
  static final Predicate<Node> MATCH_ANYTHING_BUT_NON_ARROW_FUNCTION =
      n -> !NodeUtil.isNonArrowFunction(n);

  /** A predicate for matching statements without exiting the current scope. */
  static class MatchShallowStatement implements Predicate<Node> {
    @Override
    public boolean apply(Node n) {
      Node parent = n.getParent();
      return n.isRoot()
          || n.isBlock()
          || (!n.isFunction()
              && (parent == null || isControlStructure(parent) || isStatementBlock(parent)));
    }
  }

  /** Finds the number of times a type is referenced within the node tree. */
  static int getNodeTypeReferenceCount(
      Node node, Token type, Predicate<Node> traverseChildrenPred) {
    return getCount(node, new MatchNodeType(type), traverseChildrenPred);
  }

  /** Whether a simple name is referenced within the node tree. */
  static boolean isNameReferenced(Node node, String name, Predicate<Node> traverseChildrenPred) {
    return has(node, new MatchNameNode(name), traverseChildrenPred);
  }

  /** Whether a simple name is referenced within the node tree. */
  static boolean isNameReferenced(Node node, String name) {
    return isNameReferenced(node, name, Predicates.alwaysTrue());
  }

  /** Finds the number of times a simple name is referenced within the node tree. */
  static int getNameReferenceCount(Node node, String name) {
    return getCount(node, new MatchNameNode(name), Predicates.alwaysTrue());
  }

  /**
   * @return Whether the predicate is true for the node or any of its descendants.
   */
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

  /** Returns the first Node matching the given pred via a pre-order traversal. */
  public static @Nullable Node findPreorder(
      Node node, Predicate<Node> pred, Predicate<Node> traverseChildrenPred) {
    if (pred.apply(node)) {
      return node;
    }

    if (!traverseChildrenPred.apply(node)) {
      return null;
    }

    for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
      Node result = findPreorder(c, pred, traverseChildrenPred);
      if (result != null) {
        return result;
      }
    }

    return null;
  }

  /**
   * @return The number of times the predicate is true for the node or any of its descendants.
   */
  public static int getCount(Node n, Predicate<Node> pred, Predicate<Node> traverseChildrenPred) {
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
   *
   * @see #visit
   */
  public static interface Visitor {
    void visit(Node node);
  }

  /** A pre-order traversal, calling Visitor.visit for each decendent. */
  public static void visitPreOrder(Node node, Visitor visitor) {
    visitPreOrder(node, visitor, Predicates.alwaysTrue());
  }

  /**
   * A pre-order traversal, calling Visitor.visit for each node in the tree. Children of nodes that
   * do not match the predicate will not be visited.
   */
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

  /**
   * A post-order traversal, calling Visitor.visit for each node in the tree. Children of nodes that
   * do not match the predicate will not be visited.
   */
  public static void visitPostOrder(
      Node node, Visitor visitor, Predicate<Node> traverseChildrenPred) {
    if (traverseChildrenPred.apply(node)) {
      for (Node c = node.getFirstChild(); c != null; ) {
        Node next = c.getNext();
        visitPostOrder(c, visitor, traverseChildrenPred);
        c = next;
      }
    }

    visitor.visit(node);
  }

  /**
   * Create an {@link Iterable} over the given node and all descendents. Nodes are given in
   * depth-first pre-order (<a
   * href="https://www.w3.org/TR/DOM-Level-2-Traversal-Range/glossary.html#dt-documentorder">
   * document source order</a>).
   *
   * <p>This has the benefit over the visitor patten that it is iterative and can be used with Java
   * 8 Stream API for searching, filtering, transforms, and other functional processing.
   *
   * <p>The given predicate determines whether a node's children will be iterated over. If a node
   * does not match the predicate, none of its children will be visited.
   *
   * @see java.util.stream.Stream
   * @see Streams#stream(Iterable)
   * @param root Root of the tree.
   * @param travserseNodePredicate Matches nodes in the tree whose children should be traversed.
   */
  public static Iterable<Node> preOrderIterable(Node root, Predicate<Node> travserseNodePredicate) {
    return () -> new PreOrderIterator(root, travserseNodePredicate);
  }

  /**
   * Same as {@link #preOrderIterable(Node, Predicate)} but iterates over all nodes in the tree
   * without exception.
   */
  public static Iterable<Node> preOrderIterable(Node root) {
    return preOrderIterable(
        root,
        // Traverse all nodes.
        Predicates.alwaysTrue());
  }

  /**
   * Utility class for {@see #preOrderIterable}. Iterates over nodes in tree in depth-first
   * pre-order.
   */
  private static final class PreOrderIterator extends AbstractIterator<Node> {

    private final Predicate<Node> traverseNodePredicate;

    private @Nullable Node current;

    public PreOrderIterator(Node root, Predicate<Node> traverseNodePredicate) {
      Preconditions.checkNotNull(root);
      this.traverseNodePredicate = traverseNodePredicate;
      this.current = root;
    }

    @Override
    protected Node computeNext() {
      if (current == null) {
        return endOfData();
      }

      Node returnValue = current;
      current = calculateNextNode(returnValue);
      return returnValue;
    }

    private @Nullable Node calculateNextNode(Node currentNode) {
      Preconditions.checkNotNull(currentNode);

      // If node does not match the predicate, do not descend into it.
      if (traverseNodePredicate.apply(currentNode)) {
        // In prefix order, the next node is the leftmost child.
        if (currentNode.hasChildren()) {
          return currentNode.getFirstChild();
        }
      }

      // If currentNode doesn't have children, it is a leaf node.

      // To find the next node, walk up the ancestry chain (including current node) and return the
      // first sibling we see.
      // If we don't find one, we're done.

      while (currentNode != null) {
        Node next = currentNode.getNext();
        if (next != null) {
          return next;
        }

        currentNode = currentNode.getParent();
      }

      return null;
    }
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
   * @return The BLOCK node containing the CATCH node (if any) of a TRY.
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
    checkArgument(n.isBlock());
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

  static boolean isConstantVar(Node node, @Nullable Scope scope) {
    if (isConstantName(node)) {
      return true;
    }

    if (!node.isName() || scope == null) {
      return false;
    }

    Var var = scope.getVar(node.getString());
    return var != null && (var.isDeclaredOrInferredConst() || var.isConst());
  }

  /**
   * Determines whether a variable is constant:
   *
   * <ol>
   *   <li>In Normalize, any name that matches the {@link CodingConvention#isConstant(String)} is
   *       annotated with an IS_CONSTANT_NAME property.
   * </ol>
   *
   * @param node A NAME or STRING node
   * @return True if a name node represents a constant variable
   *     <p>TODO(dimvar): this method and the next two do similar but not quite identical things.
   *     Clean up
   */
  static boolean isConstantName(Node node) {
    return node.getBooleanProp(Node.IS_CONSTANT_NAME);
  }

  /**
   * Returns whether the given name is constant by coding convention.
   *
   * @deprecated we want to delete the constant by convention logic - see http://b/135755127
   */
  @Deprecated
  static boolean isConstantByConvention(CodingConvention convention, Node node) {
    if (isNormalOrOptChainGetProp(node)) {
      return convention.isConstantKey(node.getString());
    } else if (mayBeObjectLitKey(node)) {
      return convention.isConstantKey(node.getString());
    } else if (node.isName()) {
      return convention.isConstant(node.getString());
    }
    return false;
  }

  /**
   * Determines whether the given lvalue is declared constant or is a name assigned exactly once.
   *
   * <p>Note that this intentionally excludes variables that are constant according to the coding
   * convention.
   *
   * @param node some lvalue node.
   * @throws IllegalStateException if the given node is not an lvalue
   */
  static boolean isConstantDeclaration(JSDocInfo info, Node node) {
    if (isObjectLitKey(node)
        || (node.getParent().isAssign() && node.isFirstChildOf(node.getParent()))
        || (node.getParent().isExprResult() && isNormalGet(node))
        || node.isMemberFieldDef()
        || node.isComputedFieldDef()) {
      return info != null && info.isConstant();
    }
    checkArgument(node.isName(), node);
    Node declaringParent = getDeclaringParent(node); // throws an error if `node` is not an lvalue
    if (declaringParent.isConst()) {
      return true;
    } else if (info != null && info.isConstant()) {
      return true;
    }
    return node.isInferredConstantVar();
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
  public static @Nullable InputId getInputId(Node n) {
    while (n != null && !n.isScript()) {
      n = n.getParent();
    }

    return (n != null && n.isScript()) ? n.getInputId() : null;
  }

  /** A new CALL node with the "FREE_CALL" set based on call target. */
  static Node newCallNode(Node callTarget, Node... parameters) {
    boolean isFreeCall = !isNormalGet(callTarget);
    Node call = IR.call(callTarget);
    call.putBooleanProp(Node.FREE_CALL, isFreeCall);
    for (Node parameter : parameters) {
      call.addChildToBack(parameter);
    }
    return call;
  }

  /**
   * Whether the result of the expression node is known to be a primitive value or an object that
   * has not yet escaped.
   *
   * <p>This guarantee is different than that provided by isLiteralValue (where literal values are
   * immune to side-effects if unescaped) or isImmutableValue (which can be safely aliased).
   *
   * <p>The concept of "local values" allow for the containment of side-effect operations. For
   * example, setting a property on a local value does not produce a global side-effect.
   *
   * <p>Note that the concept of "local value" is not deep, it does not say anything about the
   * properties of the "local value" (all class instances have "constructor" properties that are not
   * local values for instance).
   *
   * <p>Note that this method only provides the starting state of the expression result, it does not
   * guarantee that the value is forever a local value. If the containing method has any non-local
   * side-effect, "local values" may escape.
   */
  static boolean evaluatesToLocalValue(Node value) {
    switch (value.getToken()) {
      case ASSIGN:
        // A result that is aliased by a non-local name, is the effectively the
        // same as returning a non-local name, but this doesn't matter if the
        // value is immutable.
        return NodeUtil.isImmutableValue(value.getLastChild());
      case COMMA:
        return evaluatesToLocalValue(value.getLastChild());
      case AND:
      case OR:
      case COALESCE:
        return evaluatesToLocalValue(value.getFirstChild())
            && evaluatesToLocalValue(value.getLastChild());
      case HOOK:
        return evaluatesToLocalValue(value.getSecondChild())
            && evaluatesToLocalValue(value.getLastChild());
      case DYNAMIC_IMPORT:
        // Dynamic import always returns a newly created Promise.
        return true;
      case THIS:
      case SUPER:
        return false;
      case NAME:
        return isImmutableValue(value);
      case GETELEM:
      case GETPROP:
      case OPTCHAIN_GETELEM:
      case OPTCHAIN_GETPROP:
        // There is no information about the locality of object properties.
        return false;
      case CALL:
      case OPTCHAIN_CALL:
        return isToStringMethodCall(value);
      case TAGGED_TEMPLATELIT:
        // No information about local values for tagged template literals
        return false;
      case NEW:
        return newHasLocalResult(value);
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
        return evaluatesToLocalValue(value.getFirstChild());
      case ITER_SPREAD:
      case OBJECT_SPREAD:
        // TODO(johnlenz): remove this case.
      case NEW_TARGET:
        // Returns an alias of a constructor (current or subclass).
        return false;
      case YIELD:
      case AWAIT:
        // TODO(johnlenz): we can do better for await if we use type information.  That is,
        // if we know the promise being awaited on is a immutable value type (string, etc)
        // we could return true here.
        return false;
      default:
        // A logical assignment could evaluate to either the assignment target or the
        // right-hand side, and we generally have no information about the locality of the lhs.
        if (isLogicalAssignmentOp(value)) {
          return false;
        }
        // Other non-logical assignment ops force a local value:
        //  '' + g (a local string)
        //  x -= g (x is now an local number)
        if (isAssignmentOp(value) || isSimpleOperator(value) || isImmutableValue(value)) {
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
   *     <p>Similar to #getKnownValueType only for 'undefined'. This is useful for simplifying
   *     default value expressions.
   */
  static boolean isDefinedValue(Node value) {
    switch (value.getToken()) {
      case ASSIGN: // Only the assigned value matters here.
      case CAST:
      case COMMA:
        return isDefinedValue(value.getLastChild());
      case COALESCE:
        // 'null' is a "defined" value so we can only trust the RHS.
        // NOTE: consider creating and using a "isDefinedAndNotNull" that would allow us to
        // trust the tested value.
        return isDefinedValue(value.getSecondChild());
      case AND:
      case OR:
        return isDefinedValue(value.getFirstChild()) && isDefinedValue(value.getLastChild());
      case HOOK:
        return isDefinedValue(value.getSecondChild()) && isDefinedValue(value.getLastChild());
        // Assume undefined leaks in this and call results.
      case CALL:
      case OPTCHAIN_CALL:
      case NEW:
      case GETELEM:
      case GETPROP:
      case OPTCHAIN_GETELEM:
      case OPTCHAIN_GETPROP:
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
      case STRINGLIT:
      case NUMBER:
      case BIGINT:
      case NULL:
      case TRUE:
      case FALSE:
        return true;
      case TEMPLATELIT_STRING:
        return value.getCookedString() != null;
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
   * Given the first sibling, this returns the nth sibling or null if no such sibling exists. This
   * is like "getChildAtIndex" but returns null for non-existent indexes.
   */
  private static Node getNthSibling(Node first, int index) {
    Node sibling = first;
    while (index != 0 && sibling != null) {
      sibling = sibling.getNext();
      index--;
    }
    return sibling;
  }

  /** Given the function, this returns the nth argument or null if no such parameter exists. */
  static Node getArgumentForFunction(Node function, int index) {
    checkState(function.isFunction());
    return getNthSibling(function.getSecondChild().getFirstChild(), index);
  }

  /**
   * Given the new or call, this returns the nth argument of the call or null if no such argument
   * exists.
   */
  static Node getArgumentForCallOrNew(Node call, int index) {
    checkState(isCallOrNew(call));
    return getNthSibling(call.getSecondChild(), index);
  }

  /** Returns whether this is a target of a call or new. */
  static boolean isInvocationTarget(Node n) {
    Node parent = n.getParent();
    return parent != null
        && (isCallOrNew(parent) || parent.isTaggedTemplateLit())
        && parent.getFirstChild() == n;
  }

  /** Returns whether this is a call (including tagged template lits) or new. */
  static boolean isInvocation(Node n) {
    return isCallOrNew(n) || n.isTaggedTemplateLit();
  }

  static boolean isCallOrNewArgument(Node n) {
    Node parent = n.getParent();
    return parent != null && isCallOrNew(parent) && parent.getFirstChild() != n;
  }

  private static boolean isToStringMethodCall(Node call) {
    Node getNode = call.getFirstChild();
    final String name;
    if (isNormalOrOptChainGetProp(getNode)) {
      name = getNode.getString();
    } else if (isNormalOrOptChainGet(getNode)) {
      Node propNode = getNode.getLastChild();
      if (!propNode.isStringLit()) {
        return false;
      }
      name = propNode.getString();
    } else {
      return false;
    }

    return "toString".equals(name);
  }

  /** Return declared JSDoc type for the given name declaration, or null if none present. */
  public static @Nullable JSTypeExpression getDeclaredTypeExpression(Node declaration) {
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
  public static @Nullable JSDocInfo getBestJSDocInfo(Node n) {
    Node jsdocNode = getBestJSDocInfoNode(n);
    return jsdocNode == null ? null : jsdocNode.getJSDocInfo();
  }

  public static @Nullable Node getBestJSDocInfoNode(Node n) {
    if (n.isExprResult()) {
      return getBestJSDocInfoNode(n.getFirstChild());
    }
    JSDocInfo info = n.getJSDocInfo();
    if (info == null) {
      Node parent = n.getParent();
      if (parent == null || n.isExprResult()) {
        return null;
      }

      if (parent.isName()
          || parent.isExport()
          || parent.isVar()
          || parent.isLet()
          || parent.isConst()
          || parent.isDeclare()) {
        return getBestJSDocInfoNode(parent);
      } else if (parent.isAssign()) {
        return getBestJSDocInfoNode(parent);
      } else if (mayBeObjectLitKey(parent) || parent.isComputedProp()) {
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
  public static @Nullable Node getBestLValue(Node n) {
    Node parent = n.getParent();
    if (isFunctionDeclaration(n) || isClassDeclaration(n)) {
      return n.getFirstChild();
    } else if (n.isClassMembers()) {
      return getBestLValue(parent);
    } else if (parent.isName()) {
      return parent;
    } else if (parent.isAssign()) {
      return parent.getFirstChild();
    } else if (mayBeObjectLitKey(parent) || parent.isComputedProp()) {
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
  public static @Nullable Node getRValueOfLValue(Node n) {
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
      case ASSIGN_OR:
      case ASSIGN_AND:
      case ASSIGN_COALESCE:
      case DESTRUCTURING_LHS:
        return n.getNext();
      case VAR:
      case LET:
      case CONST:
        return n.getLastChild();
      case OBJECTLIT:
        checkState(
            n.isStringKey()
                || n.isComputedProp()
                || n.isMemberFunctionDef()
                || n.isGetterDef()
                || n.isSetterDef(),
            n);
        return n.getLastChild();
      case CLASS_MEMBERS:
        checkState(
            n.isMemberFunctionDef()
                || n.isMemberFieldDef()
                || n.isComputedFieldDef()
                || n.isGetterDef()
                || n.isSetterDef(),
            n);
        return n.getLastChild();
      case FUNCTION:
      case CLASS:
        return parent;
      default:
        break;
    }
    return null;
  }

  // TODO(b/189993301): do I have to add logic for class fields in these LValue functions?
  /** Get the owner of the given l-value node. */
  static @Nullable Node getBestLValueOwner(@Nullable Node lValue) {
    if (lValue == null || lValue.getParent() == null) {
      return null;
    }
    if (mayBeObjectLitKey(lValue) || lValue.isComputedProp()) {
      return getBestLValue(lValue.getParent());
    } else if (isNormalGet(lValue)) {
      return lValue.getFirstChild();
    }

    return null;
  }

  /** Get the name of the given l-value node. */
  public static @Nullable String getBestLValueName(@Nullable Node lValue) {
    if (lValue == null || lValue.getParent() == null) {
      return null;
    }
    if (lValue.getParent().isClassMembers() && !lValue.isComputedProp()) {
      String className = NodeUtil.getName(lValue.getGrandparent());
      if (className == null) { // Anonymous class
        return null;
      }
      String methodName = lValue.getString();
      String maybePrototype = lValue.isStaticMember() ? "." : ".prototype.";
      return className + maybePrototype + methodName;
    }
    // TODO(sdh): Tighten this to simply require !lValue.isQuotedString()
    // Could get rid of the isJSIdentifier check, but may need to fix depot.
    if (mayBeObjectLitKey(lValue)) {
      Node owner = getBestLValue(lValue.getParent());
      if (owner != null) {
        String ownerName = getBestLValueName(owner);
        if (ownerName != null) {
          String key = getObjectOrClassLitKeyName(lValue);
          return TokenStream.isJSIdentifier(key) ? ownerName + "." + key : null;
        }
      }
      return null;
    }
    return lValue.getQualifiedName();
  }

  /** Gets the root of a qualified name l-value. */
  static @Nullable Node getBestLValueRoot(@Nullable Node lValue) {
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
  public static boolean isExpressionResultUsed(Node expr) {
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
      case COALESCE:
        return (expr == parent.getFirstChild()) || isExpressionResultUsed(parent);
      case COMMA:
        Node grandparent = parent.getParent();
        if ((grandparent.isCall() || grandparent.isTaggedTemplateLit())
            && parent == grandparent.getFirstChild()
            && expr == parent.getFirstChild()
            && parent.hasTwoChildren()) {
          // Special case the indirect function call pattern, e.g. (0, myFn)(arg1, arg2).
          // The indirect call pattern has two use cases:
          Node calledFn = parent.getLastChild();
          if (calledFn.isName() && calledFn.matchesName("eval")) {
            // 1) eval
            //    Semantically, a direct call to eval is different from an indirect
            //    call to an eval. See ECMA-262 S15.1.2.1.
            return true;
          }
          if (NodeUtil.isNormalOrOptChainGet(calledFn)) {
            // 2) Removing "this" context
            //    When calling prefix.myFn(), myFn receives "prefix" as its this context.
            //    Calling "(0, prefix.myFn)()" removes the this context, so this is useful code.
            return true;
          }
        }
        return expr != parent.getFirstChild() && isExpressionResultUsed(parent);
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
   * @return Whether the expression is unconditionally executed only once in the containing
   *     execution scope.
   */
  static boolean isExecutedExactlyOnce(Node n) {
    inspect:
    do {
      Node parent = n.getParent();
      switch (parent.getToken()) {
        case IF:
        case HOOK:
        case AND:
        case OR:
        case COALESCE:
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
    } else {
      if (Double.isInfinite(value)) {
        result = IR.name("Infinity");
      } else {
        result = IR.number(Math.abs(value));
      }
      if (isNegative(value)) {
        result = IR.neg(result);
      }
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
            && n.getLastChild().getDouble() == 0)
        || n.matchesQualifiedName(NUMBER_NAN);
  }

  private static final Node NUMBER_NAN = IR.getprop(IR.name("Number"), "NaN");

  /**
   * A change scope does not directly correspond to a language scope but is an internal grouping of
   * changes.
   *
   * @return Whether the node represents a change scope root.
   */
  static boolean isChangeScopeRoot(Node n) {
    return (n.isScript() || n.isFunction());
  }

  /**
   * @return the change scope root
   */
  static Node getEnclosingChangeScopeRoot(Node n) {
    while (n != null && !isChangeScopeRoot(n)) {
      n = n.getParent();
    }
    return n;
  }

  static int countAstSizeUpToLimit(Node n, final int limit) {
    // Java doesn't allow accessing mutable local variables from another class.
    final int[] wrappedSize = {0};
    visitPreOrder(n, n12 -> wrappedSize[0]++, n1 -> wrappedSize[0] < limit);
    return wrappedSize[0];
  }

  public static int countAstSize(Node n) {
    int count = 1;
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      count += countAstSize(c);
    }
    return count;
  }

  static JSDocInfo createConstantJsDoc() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordConstancy();
    return builder.build();
  }

  public static boolean isGoogProvideCall(Node n) {
    if (isExprCall(n)) {
      Node target = n.getFirstFirstChild();
      return GOOG_PROVIDE.matches(target);
    }
    return false;
  }

  public static boolean isGoogModuleCall(Node n) {
    if (isExprCall(n)) {
      Node target = n.getFirstFirstChild();
      return GOOG_MODULE.matches(target);
    }
    return false;
  }

  static boolean isGoogModuleGetCall(Node callNode) {
    if (!callNode.isCall()) {
      return false;
    }
    return GOOG_MODULE_GET.matches(callNode.getFirstChild())
        && callNode.hasTwoChildren()
        && callNode.getSecondChild().isStringLit();
  }

  static boolean isGoogRequireCall(Node call) {
    if (call.isCall()) {
      Node target = call.getFirstChild();
      return GOOG_REQUIRE.matches(target);
    }
    return false;
  }

  static boolean isGoogRequireTypeCall(Node call) {
    if (call.isCall()) {
      Node target = call.getFirstChild();
      return GOOG_REQUIRE_TYPE.matches(target);
    }
    return false;
  }

  static boolean isGoogForwardDeclareCall(Node call) {
    if (call.isCall()) {
      Node target = call.getFirstChild();
      return GOOG_FORWARD_DECLARE.matches(target);
    }
    return false;
  }

  static boolean isGoogRequireDynamicCall(Node call) {
    if (call.isCall()) {
      Node target = call.getFirstChild();
      return GOOG_REQUIRE_DYNAMIC.matches(target);
    }
    return false;
  }

  static boolean isModuleScopeRoot(Node n) {
    return n.isModuleBody() || isBundledGoogModuleScopeRoot(n);
  }

  /** Whether the given node is referencing the `exports` object in some goog.module */
  static boolean isGoogModuleExportsReference(Scope scope, Node possibleName) {
    if (!possibleName.isName() || !possibleName.getString().equals("exports")) {
      return false;
    }
    Var var = scope.getVar(possibleName.getString());
    Node scopeRoot = var != null ? var.getScopeRoot() : null;
    return scopeRoot != null
        && (scopeRoot.isModuleBody()
            || (scopeRoot.isFunction()
                && isBundledGoogModuleScopeRoot(getFunctionBody(scopeRoot))));
  }

  private static final QualifiedName GOOG_LOADMODULE = QualifiedName.of("goog.loadModule");

  static boolean isBundledGoogModuleCall(Node n) {
    // TODO(lharker): take an EXPR_RESULT to align with NodeUtil.isGoogModuleCall and
    // NodeUtil.isGoogProvideCall.
    if (!(n.isCall() && n.hasTwoChildren() && GOOG_LOADMODULE.matches(n.getFirstChild()))) {
      return false;
    }
    return n.hasParent()
        && n.getParent().isExprResult()
        && n.getGrandparent() != null
        && n.getGrandparent().isScript();
  }

  static boolean isBundledGoogModuleScopeRoot(Node n) {
    if (!n.isBlock() || !n.hasChildren() || !isGoogModuleCall(n.getFirstChild())) {
      return false;
    }
    Node function = n.getParent();
    if (function == null
        || !function.isFunction()
        || !getFunctionParameters(function).hasOneChild()
        || !getFunctionParameters(function).getFirstChild().matchesName("exports")) {
      return false;
    }
    Node call = function.getParent();
    if (!call.isCall()
        || !call.hasTwoChildren()
        || !GOOG_LOADMODULE.matches(call.getFirstChild())) {
      return false;
    }
    return call.getParent().isExprResult() && call.getGrandparent().isScript();
  }

  static boolean isGoogModuleDeclareLegacyNamespaceCall(Node n) {
    if (isExprCall(n)) {
      Node target = n.getFirstFirstChild();
      return GOOG_MODULE_DECLARE_LEGACY_NAMESPACE.matches(target);
    }
    return false;
  }

  static boolean isGoogSetTestOnlyCall(Node n) {
    if (isExprCall(n)) {
      Node target = n.getFirstFirstChild();
      return GOOG_SET_TEST_ONLY.matches(target);
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
    return n.isScript()
        && n.hasChildren()
        && n.getFirstChild().isModuleBody()
        && isGoogModuleCall(n.getFirstFirstChild());
  }

  /**
   * @return Whether the node is a SCRIPT node for a goog.module that has a declareLegacyNamespace
   *     call.
   */
  static boolean isLegacyGoogModuleFile(Node n) {
    return isGoogModuleFile(n)
        && isGoogModuleDeclareLegacyNamespaceCall(n.getFirstChild().getSecondChild());
  }

  static boolean isConstructor(Node fnNode) {
    if (fnNode == null || !fnNode.isFunction()) {
      return false;
    }

    JSType type = fnNode.getJSType();
    JSDocInfo jsDocInfo = getBestJSDocInfo(fnNode);
    Color color = fnNode.getColor();

    return (type != null && type.isConstructor())
        || (jsDocInfo != null && jsDocInfo.isConstructor())
        || (color != null && color.isConstructor())
        || isEs6Constructor(fnNode);
  }

  public static boolean isEs6ConstructorMemberFunctionDef(Node memberFunctionDef) {
    if (!memberFunctionDef.isMemberFunctionDef()) {
      return false; // not a member function at all
    }
    return memberFunctionDef.getParent().isClassMembers() // is in a class
        && !memberFunctionDef.isStaticMember() // constructors aren't static
        && memberFunctionDef.getString().equals("constructor");
  }

  public static boolean isEs6Constructor(Node fnNode) {
    if (!fnNode.isFunction()) {
      return false;
    }
    Node memberFunctionDef = fnNode.getParent();
    return memberFunctionDef != null && isEs6ConstructorMemberFunctionDef(memberFunctionDef);
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

  public static boolean isCallTo(Node n, QualifiedName qualifiedName) {
    return n.isCall() && qualifiedName.matches(n.getFirstChild());
  }

  /**
   * A faster version of {@link #isCallTo(Node, String)}.
   *
   * @param n node to check if a call
   * @param targetMethod the prebuilt AST getprop node that represents the method to check
   */
  public static boolean isCallTo(Node n, Node targetMethod) {
    if (!n.isCall()) {
      return false;
    }

    return n.getFirstChild().matchesQualifiedName(targetMethod);
  }

  public static ImmutableSet<String> collectExternVariableNames(
      AbstractCompiler compiler, Node externs) {
    ReferenceCollector externsRefs =
        new ReferenceCollector(
            compiler, ReferenceCollector.DO_NOTHING_BEHAVIOR, new SyntacticScopeCreator(compiler));
    externsRefs.process(externs);
    ImmutableSet.Builder<String> externsNames = ImmutableSet.builder();
    for (Var v : externsRefs.getAllSymbols()) {
      if (!v.isParam()) {
        externsNames.add(v.getName());
      }
    }
    return externsNames.build();
  }

  static void createSynthesizedExternsSymbol(AbstractCompiler compiler, String nameToAdd) {
    Node name = IR.name(nameToAdd);
    name.putBooleanProp(Node.IS_CONSTANT_NAME, true);
    Node var = IR.var(name);
    CompilerInput input = compiler.getSynthesizedExternsInput();
    Node root = input.getAstRoot(compiler);
    name.setStaticSourceFileFrom(root);
    var.setStaticSourceFileFrom(root);
    root.addChildToBack(var);
    compiler.reportChangeToEnclosingScope(var);
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

  static Iterable<Node> getInvocationArgsAsIterable(Node invocation) {
    if (invocation.isTaggedTemplateLit()) {
      return new TemplateArgsIterable(invocation.getLastChild());
    }

    checkState(isCallOrNew(invocation), invocation);
    if (invocation.hasOneChild()) {
      return ImmutableList.of();
    }

    ImmutableList.Builder<Node> list = ImmutableList.builder();
    for (Node arg = invocation.getSecondChild(); arg != null; arg = arg.getNext()) {
      list.add(arg);
    }
    return list.build();
  }

  /**
   * Returns the number of arguments in this invocation. For template literals it takes into account
   * the implicit first argument of ITemplateArray
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
   * Represents an iterable of the children of templatelit_sub nodes of a template lit node This
   * iterable will skip over the String children of the template lit node.
   */
  private static final class TemplateArgsIterable implements Iterable<Node> {
    private final Node templateLit;

    TemplateArgsIterable(Node templateLit) {
      checkState(templateLit.isTemplateLit());
      this.templateLit = templateLit;
    }

    @Override
    public Iterator<Node> iterator() {
      return new AbstractIterator<Node>() {
        private @Nullable Node nextChild = templateLit.getFirstChild();

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
   * Records a mapping of names to vars of everything reachable in a module. Should only be called
   * with a module scope.
   *
   * @param nameVarMap an empty map that gets populated with the keys being variable names and
   *     values being variable objects
   * @param orderedVars an empty list that gets populated with variable objects in the order that
   *     they appear in the module
   */
  static void getAllVarsDeclaredInModule(
      final Node moduleNode,
      final Map<String, Var> nameVarMap,
      final List<Var> orderedVars,
      AbstractCompiler compiler,
      ScopeCreator scopeCreator,
      final Scope globalScope) {

    checkState(moduleNode.isModuleBody(), "getAllVarsDeclaredInModule expects a module body node");
    checkState(nameVarMap.isEmpty());
    checkState(orderedVars.isEmpty());
    checkState(globalScope.isGlobal(), globalScope);

    ScopedCallback finder =
        new ScopedCallback() {
          @Override
          public void enterScope(NodeTraversal t) {
            Scope currentScope = t.getScope();
            if (currentScope.isModuleScope()) {
              for (Var v : currentScope.getVarIterable()) {
                nameVarMap.put(v.getName(), v);
                orderedVars.add(v);
              }
            }
          }

          @Override
          public void exitScope(NodeTraversal t) {}

          @Override
          public final boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
            return n.isModuleBody();
          }

          @Override
          public void visit(NodeTraversal t, Node n, Node parent) {}
        };
    NodeTraversal.builder()
        .setCompiler(compiler)
        .setCallback(finder)
        .setScopeCreator(scopeCreator)
        .traverseWithScope(moduleNode, globalScope);
  }

  /**
   * Returns a mapping of names to vars of everything reachable in a function. Should only be called
   * with a function scope. Does not enter new control flow areas aka embedded functions.
   */
  static AllVarsDeclaredInFunction getAllVarsDeclaredInFunction(
      AbstractCompiler compiler, ScopeCreator scopeCreator, final Scope scope) {
    checkState(scope.isFunctionScope(), scope);

    final Map<String, Var> nameVarMap = new LinkedHashMap<>();
    final List<Var> orderedVars = new ArrayList<>();

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

    NodeTraversal.builder()
        .setCompiler(compiler)
        .setCallback(finder)
        .setScopeCreator(scopeCreator)
        .traverseAtScope(scope);

    return new AllVarsDeclaredInFunction(nameVarMap, orderedVars);
  }

  /** Represents a mapping of names to vars of everything reachable in a function. */
  static final class AllVarsDeclaredInFunction {
    private final Map<String, Var> allVarsInFn;
    private final List<Var> orderedVars;

    private AllVarsDeclaredInFunction(Map<String, Var> allVarsInFn, List<Var> orderedVars) {
      checkState(allVarsInFn.isEmpty() == orderedVars.isEmpty());
      this.allVarsInFn = allVarsInFn;
      this.orderedVars = orderedVars;
    }

    public Map<String, Var> getAllVariables() {
      return allVarsInFn;
    }

    public List<Var> getAllVariablesInOrder() {
      return orderedVars;
    }
  }

  /** Returns true if the node is a property of an object literal. */
  public static boolean isObjLitProperty(Node node) {
    return node.isStringKey()
        || node.isGetterDef()
        || node.isSetterDef()
        || node.isMemberFunctionDef()
        || node.isComputedProp();
  }

  /**
   * @return Whether the node represents the return value of a blockless Arrow function
   */
  public static boolean isBlocklessArrowFunctionResult(Node n) {
    Node parent = n.getParent();
    return parent != null && parent.isFunction() && n == parent.getLastChild() && !n.isBlock();
  }

  /**
   * Returns a script node's FeatureSet, which is set at parse-time. This may not be up-to-date as
   * passes can add/remove features from a script node's descendants.
   *
   * <p>The feature set will be null if the script node was created artificially or if the parser
   * didn't detect any interesting features.
   */
  static @Nullable FeatureSet getFeatureSetOfScript(Node scriptNode) {
    checkState(scriptNode.isScript(), scriptNode);
    return (FeatureSet) scriptNode.getProp(Node.FEATURE_SET);
  }

  /**
   * Adds the given features to a SCRIPT node's FeatureSet property.
   *
   * <p>Also updates the compiler's FeatureSet.
   */
  static void addFeatureToScript(Node scriptNode, Feature feature, AbstractCompiler compiler) {
    checkState(scriptNode.isScript(), scriptNode);
    FeatureSet currentFeatures = getFeatureSetOfScript(scriptNode);
    FeatureSet newFeatures =
        currentFeatures != null
            ? currentFeatures.with(feature)
            : FeatureSet.BARE_MINIMUM.with(feature);
    scriptNode.putProp(Node.FEATURE_SET, newFeatures);
    if (feature != Feature.MODULES) {
      // Except MODULES (which can get reintroduced later in ConvertChunksToEsModules pass),
      // prohibit passes from reintroducing any feature that already got transpiled.
      checkState(compiler.getAllowableFeatures().contains(feature));
    } else {
      compiler.setAllowableFeatures(compiler.getAllowableFeatures().with(feature));
    }
  }

  /**
   * Removes the given feature from the SCRIPT node's FeatureSet property.
   *
   * <p>Removing a feature from a single script does not mean that it's removed from all scripts.
   * Hence this method does not remove it from the compiler's featureSet. The caller must remove it
   * from the compiler's featureSet if it's calling this method for each script.
   */
  private static void removeFeatureFromScript(Node scriptNode, Feature feature) {
    FeatureSet currentFeatures = getFeatureSetOfScript(scriptNode);
    if (currentFeatures != null) {
      currentFeatures = currentFeatures.without(feature);
    }
    scriptNode.putProp(Node.FEATURE_SET, currentFeatures);
  }

  /**
   * Removes the given feature from the FEATURE_SET prop of all SCRIPT nodes under root.
   *
   * <p>Also updates the compiler's FeatureSet.
   */
  static void removeFeatureFromAllScripts(Node root, Feature feature, AbstractCompiler compiler) {
    checkArgument(root.isRoot(), root);
    for (Node childNode = root.getFirstChild();
        childNode != null;
        childNode = childNode.getNext()) {
      checkState(childNode.isScript());
      NodeUtil.removeFeatureFromScript(childNode, feature);
    }
    compiler.markFeatureNotAllowed(feature);
  }

  /** Adds the given feature to the FEATURE_SET prop of all scripts under externs and root. */
  static void addFeatureToAllScripts(Node root, Feature feature, AbstractCompiler compiler) {
    checkArgument(root.isRoot(), root);
    for (Node childNode = root.getFirstChild();
        childNode != null;
        childNode = childNode.getNext()) {
      checkState(childNode.isScript());
      NodeUtil.addFeatureToScript(childNode, feature, compiler);
    }
  }

  /**
   * Adds the given features to a SCRIPT node's FeatureSet property.
   *
   * <p>Also updates the compiler's FeatureSet.
   */
  static void addFeaturesToScript(Node scriptNode, FeatureSet features, AbstractCompiler compiler) {
    checkState(scriptNode.isScript(), scriptNode);
    for (Feature feature : features.getFeatures()) {
      addFeatureToScript(scriptNode, feature, compiler);
    }
  }

  /** Calls {@code cb} with all NAMEs declared in a PARAM_LIST or destructuring pattern. */
  public static void getParamOrPatternNames(Node n, Consumer<Node> cb) {
    ParsingUtil.getParamOrPatternNames(n, cb);
  }

  /** Represents a goog.require'd namespace and property inside a module. */
  @AutoValue
  public abstract static class GoogRequire {
    public abstract String namespace(); // The Closure namespace inside the require call

    public abstract @Nullable String property(); // Non-null for destructuring requires.

    static GoogRequire fromNamespace(String namespace) {
      return new AutoValue_NodeUtil_GoogRequire(namespace, /* property= */ null);
    }

    static GoogRequire fromNamespaceAndProperty(String namespace, String property) {
      return new AutoValue_NodeUtil_GoogRequire(namespace, property);
    }
  }

  public static @Nullable GoogRequire getGoogRequireInfo(String name, Scope scope) {
    Var var = scope.getVar(name);
    if (var == null || !var.getScopeRoot().isModuleBody() || var.getNameNode() == null) {
      return null;
    }
    Node nameNode = var.getNameNode();

    if (NodeUtil.isNameDeclaration(nameNode.getParent())) {
      Node requireCall = nameNode.getFirstChild();
      if (requireCall == null
          || !(isGoogRequireCall(requireCall) || isGoogRequireTypeCall(requireCall))) {
        return null;
      }
      String namespace = requireCall.getSecondChild().getString();
      return GoogRequire.fromNamespace(namespace);
    } else if (nameNode.getParent().isStringKey() && nameNode.getGrandparent().isObjectPattern()) {
      Node requireCall = nameNode.getGrandparent().getNext();
      if (requireCall == null
          || !(isGoogRequireCall(requireCall) || isGoogRequireTypeCall(requireCall))) {
        return null;
      }
      String property = nameNode.getParent().getString();
      String namespace = requireCall.getSecondChild().getString();
      return GoogRequire.fromNamespaceAndProperty(namespace, property);
    }
    return null;
  }

  /**
   * Estimates the number of lines in the file of this script node.
   *
   * <p>This method returns the line number of the last node in the script +1. This is not strictly
   * the number of lines in the file (consider trailing comments or whitespace), but should be
   * strongly correlated with it. If perfect accuracy is desired the original source file will have
   * to be read.
   */
  public static int estimateNumLines(Node scriptNode) {
    checkArgument(scriptNode.isScript());
    Node current = scriptNode;
    while (current.hasChildren()) {
      current = current.getLastChild();
    }

    // +1 since this is the start line of the last AST node and we can expect at least one
    // trailing newline
    return current.getLineno() + 1;
  }
}
